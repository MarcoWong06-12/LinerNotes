package com.linernotes.app.data.repository

import com.linernotes.app.core.i18n.TranslationTargetLanguage
import com.linernotes.app.core.lyric.AiAnnotationCurator
import com.linernotes.app.core.preference.AiPreferences
import com.linernotes.app.data.local.dao.LyricAnnotationDao
import com.linernotes.app.data.local.entity.LyricAnnotationEntity
import com.linernotes.app.data.local.entity.SongStoryEntity
import com.linernotes.app.data.local.entity.TrackEntity
import com.linernotes.app.data.remote.GeniusService
import com.linernotes.app.data.remote.TranslationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnnotationRepository @Inject constructor(
    private val lyricAnnotationDao: LyricAnnotationDao,
    private val aiPreferences: AiPreferences,
    private val translationService: TranslationService
) {

    fun getAnnotationsFlow(trackId: Long): Flow<List<LyricAnnotationEntity>> =
        lyricAnnotationDao.getAnnotationsFlow(trackId)

    suspend fun getAnnotations(trackId: Long): List<LyricAnnotationEntity> =
        lyricAnnotationDao.getAnnotations(trackId)

    fun getSongStoryFlow(trackId: Long): Flow<SongStoryEntity?> =
        lyricAnnotationDao.getSongStoryFlow(trackId)

    suspend fun getSongStory(trackId: Long): SongStoryEntity? =
        lyricAnnotationDao.getSongStory(trackId)

    /**
     * 从 Genius (及 AI 容灾策展引擎) 检索、解析并本地持久化当前曲目的歌词典故与背景故事。
     * 若获取到外文注释与故事，会自动预先并发翻译为中文对照并一并缓存入库。
     */
    suspend fun fetchAndCacheAnnotations(
        track: TrackEntity,
        artist: String,
        alignedLines: List<String> = emptyList(),
        forceRefresh: Boolean = false
    ): Result<Pair<SongStoryEntity?, List<LyricAnnotationEntity>>> = withContext(Dispatchers.IO) {
        val trackId = track.id

        // 1. 本地 Room 缓存检查：
        // 关键逻辑：只有当缓存来自 GENIUS 原生数据（非早期网络超时写入的模板兜底）且非空时，才视为完全命中缓存
        if (!forceRefresh) {
            val cachedAnnotations = lyricAnnotationDao.getAnnotations(trackId)
            val cachedStory = lyricAnnotationDao.getSongStory(trackId)

            val isGeniusData = cachedStory?.source == "GENIUS" && cachedAnnotations.any { it.source == "GENIUS" }
            if (isGeniusData && cachedAnnotations.isNotEmpty()) {
                val fixedStory = if (AiAnnotationCurator.isAlreadyChinese(cachedStory.descriptionPlain) &&
                    !cachedStory.descriptionTranslation.isNullOrBlank()
                ) {
                    val s = cachedStory.copy(descriptionTranslation = null)
                    lyricAnnotationDao.updateSongStory(s)
                    s
                } else cachedStory

                return@withContext Result.success(Pair(fixedStory, cachedAnnotations))
            }
        }

        // 2. 尝试向 Genius 检索原生数据 (优先执行)
        val cleanTitle = AiAnnotationCurator.cleanSongTitle(track.title)
        val customToken = aiPreferences.geniusToken.takeIf { it.isNotBlank() }

        var storyEntity: SongStoryEntity? = null
        val annotationEntities = mutableListOf<LyricAnnotationEntity>()

        try {
            // 2.1 优先使用清洗后的规范曲名搜索
            var searchHit = GeniusService.searchSong(
                title = cleanTitle,
                artist = artist,
                customToken = customToken
            )

            // 若未命中且原标题与清洗标题不同，尝试原标题
            if (searchHit == null && !cleanTitle.equals(track.title, ignoreCase = true)) {
                searchHit = GeniusService.searchSong(
                    title = track.title,
                    artist = artist,
                    customToken = customToken
                )
            }

            if (searchHit != null) {
                val songId = searchHit.id

                // 2.2 获取歌曲背景总览 (About this Song)
                val detail = GeniusService.getSongDetails(songId, customToken)
                if (detail != null && (detail.descriptionPlain.isNotBlank() || !detail.headerImageUrl.isNullOrBlank())) {
                    storyEntity = SongStoryEntity(
                        trackId = trackId,
                        geniusSongId = songId,
                        title = detail.title,
                        artist = detail.artist,
                        descriptionPlain = detail.descriptionPlain,
                        releaseDate = detail.releaseDate,
                        headerImageUrl = detail.headerImageUrl ?: searchHit.coverUrl,
                        songArtImageUrl = detail.songArtImageUrl ?: searchHit.coverUrl,
                        producerCredits = detail.producerCredits,
                        songUrl = detail.songUrl ?: searchHit.url,
                        source = "GENIUS"
                    )
                }

                // 2.3 获取该曲目的全部逐句歌词典故列表 (Referents)
                val referents = GeniusService.getReferents(songId, customToken)
                for (ref in referents) {
                    val primaryAnnot = ref.annotations.firstOrNull() ?: continue
                    val imagesJson = if (primaryAnnot.imageUrls.isNotEmpty()) {
                        JSONArray(primaryAnnot.imageUrls).toString()
                    } else null

                    annotationEntities.add(
                        LyricAnnotationEntity(
                            trackId = trackId,
                            lyricFragment = ref.fragment,
                            explanationText = primaryAnnot.bodyPlain,
                            authorName = primaryAnnot.authorName,
                            authorAvatarUrl = primaryAnnot.authorAvatarUrl,
                            isVerified = primaryAnnot.verified,
                            votesTotal = primaryAnnot.votesTotal,
                            imageUrlsJson = imagesJson,
                            source = "GENIUS",
                            geniusSongId = songId,
                            geniusUrl = primaryAnnot.url ?: searchHit.url
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // 网络受限或 Genius 超时
        }

        // 3. 自动中英翻译处理：如果获取到了 Genius 英文内容，立即并发自动翻译为中文对照
        val finalAnnotations = mutableListOf<LyricAnnotationEntity>()
        if (annotationEntities.isNotEmpty()) {
            supervisorScope {
                val translatedList = annotationEntities.map { annot ->
                    async {
                        if (!AiAnnotationCurator.isAlreadyChinese(annot.explanationText) && annot.explanationTranslation.isNullOrBlank()) {
                            val trans = translationService.translateText(annot.explanationText, "zh")
                            if (!trans.isNullOrBlank()) annot.copy(explanationTranslation = trans) else annot
                        } else annot
                    }
                }.awaitAll()
                finalAnnotations.addAll(translatedList)
            }
        }

        if (storyEntity != null && !storyEntity.descriptionPlain.isBlank()) {
            if (!AiAnnotationCurator.isAlreadyChinese(storyEntity.descriptionPlain) && storyEntity.descriptionTranslation.isNullOrBlank()) {
                val transStory = translationService.translateText(storyEntity.descriptionPlain, "zh")
                if (!transStory.isNullOrBlank()) {
                    storyEntity = storyEntity.copy(descriptionTranslation = transStory)
                }
            }
        }

        // 4. 容灾保障：若 Genius 检索完全失败或该曲目在 Genius 无社区注释，唤起 AI 深度考据引擎
        if (storyEntity == null || storyEntity.descriptionPlain.isBlank() || finalAnnotations.isEmpty()) {
            val effectiveLines = if (alignedLines.isNotEmpty()) {
                alignedLines
            } else {
                track.originalLyrics?.lines()
                    ?.map { it.replace(Regex("""^\[\d+:\d+(?:\.\d+)?\]"""), "").trim() }
                    ?.filter { it.isNotBlank() && !it.startsWith("[") }
                    ?: emptyList()
            }

            val (curatedStory, curatedAnnotations) = AiAnnotationCurator.curateTrack(
                track = track,
                artist = artist,
                alignedLines = effectiveLines
            )

            if (storyEntity == null || storyEntity.descriptionPlain.isBlank()) {
                storyEntity = curatedStory
            }
            if (finalAnnotations.isEmpty() && curatedAnnotations.isNotEmpty()) {
                finalAnnotations.addAll(curatedAnnotations)
            }
        }

        // 5. 本地 Room 持久化（覆盖旧的模板数据）
        if (storyEntity != null) {
            if (AiAnnotationCurator.isAlreadyChinese(storyEntity.descriptionPlain)) {
                storyEntity = storyEntity.copy(descriptionTranslation = null)
            }
            lyricAnnotationDao.insertSongStory(storyEntity)
        }

        if (finalAnnotations.isNotEmpty()) {
            lyricAnnotationDao.deleteAnnotationsForTrack(trackId)
            lyricAnnotationDao.insertAnnotations(finalAnnotations)
        }

        return@withContext Result.success(Pair(storyEntity, finalAnnotations))
    }

    /**
     * 针对外文注释进行本地中文/目标语言机器翻译并持久化
     */
    suspend fun translateAnnotation(annotation: LyricAnnotationEntity): LyricAnnotationEntity = withContext(Dispatchers.IO) {
        if (!annotation.explanationTranslation.isNullOrBlank()) {
            return@withContext annotation
        }

        if (AiAnnotationCurator.isAlreadyChinese(annotation.explanationText)) {
            return@withContext annotation
        }

        val targetIso = TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage).fallbackIso
        val translated = translationService.translateText(annotation.explanationText, targetIso)
        val finalTranslation = if (!translated.isNullOrBlank()) translated else annotation.explanationText

        val updated = annotation.copy(explanationTranslation = finalTranslation)
        lyricAnnotationDao.updateAnnotation(updated)
        updated
    }

    /**
     * 针对外文歌曲背景故事进行本地中文/目标语言机器翻译并持久化
     */
    suspend fun translateSongStory(story: SongStoryEntity): SongStoryEntity = withContext(Dispatchers.IO) {
        if (!story.descriptionTranslation.isNullOrBlank()) {
            return@withContext story
        }

        if (AiAnnotationCurator.isAlreadyChinese(story.descriptionPlain)) {
            return@withContext story
        }

        val targetIso = TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage).fallbackIso
        val translated = translationService.translateText(story.descriptionPlain, targetIso)
        val finalTranslation = if (!translated.isNullOrBlank()) translated else story.descriptionPlain

        val updated = story.copy(descriptionTranslation = finalTranslation)
        lyricAnnotationDao.updateSongStory(updated)
        updated
    }
}
