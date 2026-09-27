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
import kotlinx.coroutines.flow.Flow
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
     * 从 Genius (及 AI 容灾策展引擎) 检索、解析并本地持久化当前曲目的歌词典故与背景故事
     */
    suspend fun fetchAndCacheAnnotations(
        track: TrackEntity,
        artist: String,
        alignedLines: List<String> = emptyList(),
        forceRefresh: Boolean = false
    ): Result<Pair<SongStoryEntity?, List<LyricAnnotationEntity>>> = withContext(Dispatchers.IO) {
        val trackId = track.id

        // 提取有效歌词文本行
        val effectiveLines = if (alignedLines.isNotEmpty()) {
            alignedLines
        } else {
            track.originalLyrics?.lines()
                ?.map { it.replace(Regex("""^\[\d+:\d+(?:\.\d+)?\]"""), "").trim() }
                ?.filter { it.isNotBlank() && !it.startsWith("[") }
                ?: emptyList()
        }

        // 1. 若非强制刷新，先检查本地 Room 缓存
        if (!forceRefresh) {
            val cachedAnnotations = lyricAnnotationDao.getAnnotations(trackId)
            val cachedStory = lyricAnnotationDao.getSongStory(trackId)

            // 只有当故事与逐句典故均已缓存完整时，才直接返回本地缓存
            // 如果仅有故事而典故为空（或相反），绝不能提早退出，必须继续向下执行 Genius 抓取或 AI 深度策展
            if (cachedStory != null && cachedAnnotations.isNotEmpty()) {
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

        val cleanTitle = AiAnnotationCurator.cleanSongTitle(track.title)
        val customToken = aiPreferences.geniusToken.takeIf { it.isNotBlank() }

        var storyEntity: SongStoryEntity? = null
        val annotationEntities = mutableListOf<LyricAnnotationEntity>()

        // 2. 尝试从 Genius 检索原生条目
        try {
            val searchHit = GeniusService.searchSong(
                title = cleanTitle,
                artist = artist,
                customToken = customToken
            )

            if (searchHit != null) {
                val songId = searchHit.id

                // 2.1 获取歌曲概览与时代背景
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
                        songUrl = detail.songUrl ?: searchHit.url
                    )
                }

                // 2.2 获取歌词典故注释列表 (Referents)
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
            // 网络受限或 Genius 403/超时，平滑进入 AI 策展容灾模式
        }

        // 3. 容灾与深度策展保障：若未能从 Genius 获得有效注释或背景故事，唤起 AI 唱片学者深度策展引擎
        if (storyEntity == null || storyEntity.descriptionPlain.isBlank() || annotationEntities.isEmpty()) {
            val (curatedStory, curatedAnnotations) = AiAnnotationCurator.curateTrack(
                track = track,
                artist = artist,
                alignedLines = effectiveLines
            )

            if (storyEntity == null || storyEntity.descriptionPlain.isBlank()) {
                storyEntity = curatedStory
            }
            if (annotationEntities.isEmpty() && curatedAnnotations.isNotEmpty()) {
                annotationEntities.addAll(curatedAnnotations)
            }
        }

        // 4. 持久化存储至本地 Room 数据库
        if (storyEntity != null) {
            // 清除旧的可能被误翻译成英文的 descriptionTranslation
            if (AiAnnotationCurator.isAlreadyChinese(storyEntity.descriptionPlain)) {
                storyEntity = storyEntity.copy(descriptionTranslation = null)
            }
            lyricAnnotationDao.insertSongStory(storyEntity)
        }

        if (annotationEntities.isNotEmpty()) {
            lyricAnnotationDao.deleteAnnotationsForTrack(trackId)
            lyricAnnotationDao.insertAnnotations(annotationEntities)
        }

        return@withContext Result.success(Pair(storyEntity, annotationEntities))
    }

    /**
     * 针对外文注释进行本地中文/目标语言机器翻译并持久化
     */
    suspend fun translateAnnotation(annotation: LyricAnnotationEntity): LyricAnnotationEntity = withContext(Dispatchers.IO) {
        if (!annotation.explanationTranslation.isNullOrBlank()) {
            return@withContext annotation
        }

        // 如果已经是中文，无需发起机器翻译（避免将中文误翻为英文）
        if (AiAnnotationCurator.isAlreadyChinese(annotation.explanationText)) {
            return@withContext annotation
        }

        val targetIso = TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage).fallbackIso
        val translated = translationService.translateLyricsWithTimestamps(annotation.explanationText, targetIso)
        val finalTranslation = if (translated.isNotBlank()) translated else annotation.explanationText

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

        // 如果已经是中文，无需发起机器翻译（避免将中文误翻为英文）
        if (AiAnnotationCurator.isAlreadyChinese(story.descriptionPlain)) {
            return@withContext story
        }

        val targetIso = TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage).fallbackIso
        val translated = translationService.translateLyricsWithTimestamps(story.descriptionPlain, targetIso)
        val finalTranslation = if (translated.isNotBlank()) translated else story.descriptionPlain

        val updated = story.copy(descriptionTranslation = finalTranslation)
        lyricAnnotationDao.updateSongStory(updated)
        updated
    }
}
