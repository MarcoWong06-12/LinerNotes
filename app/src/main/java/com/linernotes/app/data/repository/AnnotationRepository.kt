package com.linernotes.app.data.repository

import com.linernotes.app.core.i18n.TranslationTargetLanguage
import com.linernotes.app.core.lyric.AiAnnotationCurator
import com.linernotes.app.core.preference.AiPreferences
import com.linernotes.app.core.util.ChineseConverter
import com.linernotes.app.data.local.dao.LyricAnnotationDao
import com.linernotes.app.data.local.entity.LyricAnnotationEntity
import com.linernotes.app.data.local.entity.SongStoryEntity
import com.linernotes.app.data.local.entity.TrackEntity
import com.linernotes.app.data.remote.GeniusService
import com.linernotes.app.data.remote.TranslationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
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

    suspend fun updateSongStory(story: SongStoryEntity) = withContext(Dispatchers.IO) {
        lyricAnnotationDao.updateSongStory(story)
    }

    suspend fun updateAnnotation(annotation: LyricAnnotationEntity) = withContext(Dispatchers.IO) {
        lyricAnnotationDao.updateAnnotation(annotation)
    }

    suspend fun convertAllAnnotationsForTracks(trackIds: List<Long>, toTraditional: Boolean) = withContext(Dispatchers.IO) {
        if (trackIds.isEmpty()) return@withContext
        val stories = lyricAnnotationDao.getSongStoriesForTracks(trackIds)
        for (story in stories) {
            val newStory = story.copy(
                descriptionTranslation = if (toTraditional) ChineseConverter.toTraditional(story.descriptionTranslation) else ChineseConverter.toSimplified(story.descriptionTranslation),
                descriptionPlain = if (AiAnnotationCurator.isAlreadyChinese(story.descriptionPlain)) {
                    if (toTraditional) ChineseConverter.toTraditional(story.descriptionPlain) else ChineseConverter.toSimplified(story.descriptionPlain)
                } else story.descriptionPlain
            )
            lyricAnnotationDao.updateSongStory(newStory)
        }
        val annotations = lyricAnnotationDao.getAnnotationsForTracks(trackIds)
        for (annot in annotations) {
            val newAnnot = annot.copy(
                explanationTranslation = if (toTraditional) ChineseConverter.toTraditional(annot.explanationTranslation) else ChineseConverter.toSimplified(annot.explanationTranslation),
                lyricTranslation = if (toTraditional) ChineseConverter.toTraditional(annot.lyricTranslation) else ChineseConverter.toSimplified(annot.lyricTranslation),
                explanationText = if (AiAnnotationCurator.isAlreadyChinese(annot.explanationText)) {
                    if (toTraditional) ChineseConverter.toTraditional(annot.explanationText) else ChineseConverter.toSimplified(annot.explanationText)
                } else annot.explanationText
            )
            lyricAnnotationDao.updateAnnotation(newAnnot)
        }
    }

    /**
     * 后台静默预拉取全专辑曲目的 Genius 典故与背景故事（无感且极速）
     */
    suspend fun prefetchAlbumAnnotations(artist: String, tracks: List<TrackEntity>) = withContext(Dispatchers.IO) {
        for (track in tracks) {
            try {
                val cachedStory = lyricAnnotationDao.getSongStory(track.id)
                val cachedAnnotations = lyricAnnotationDao.getAnnotations(track.id)
                // 仅当已有真实 Genius 原生缓存时跳过
                if (cachedStory?.source == "GENIUS" && cachedAnnotations.any { it.source == "GENIUS" }) {
                    continue
                }
                fetchAndCacheAnnotations(track = track, artist = artist)
            } catch (e: Exception) {
                // 静默处理，避免干扰前台正常交互
            }
        }
    }

    /**
     * 从 Genius 检索、解析并本地持久化当前曲目的歌词典故与背景故事。
     * 若获取到外文注释与故事，会自动预先并发翻译为中文对照并一并缓存入库。
     * 不套用虚假默认模板，保持真实性与优雅感。
     */
    suspend fun fetchAndCacheAnnotations(
        track: TrackEntity,
        artist: String,
        alignedLines: List<String> = emptyList(),
        forceRefresh: Boolean = false
    ): Result<Pair<SongStoryEntity?, List<LyricAnnotationEntity>>> = withContext(Dispatchers.IO) {
        val trackId = track.id
        val isTraditionalTarget = aiPreferences.targetLanguage == "zh-TW" ||
            TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage) == TranslationTargetLanguage.ZH_TW

        // 1. 本地 Room 缓存检查与清理：
        if (!forceRefresh) {
            val cachedAnnotations = lyricAnnotationDao.getAnnotations(trackId)
            val cachedStory = lyricAnnotationDao.getSongStory(trackId)

            // 清理历史残留的假模板数据
            if (cachedStory?.source == "AI_CURATED") {
                lyricAnnotationDao.deleteSongStoryForTrack(trackId)
            }
            if (cachedAnnotations.any { it.source == "AI_CURATED" }) {
                lyricAnnotationDao.deleteAnnotationsForTrack(trackId)
            }

            val isGeniusData = cachedStory?.source == "GENIUS" || cachedAnnotations.any { it.source == "GENIUS" }
            if (isGeniusData && (cachedAnnotations.isNotEmpty() || cachedStory != null)) {
                val fixedStory = if (cachedStory != null && AiAnnotationCurator.isAlreadyChinese(cachedStory.descriptionPlain) &&
                    !cachedStory.descriptionTranslation.isNullOrBlank()
                ) {
                    val s = cachedStory.copy(descriptionTranslation = null)
                    lyricAnnotationDao.updateSongStory(s)
                    s
                } else cachedStory

                return@withContext Result.success(Pair(fixedStory, cachedAnnotations.filter { it.source == "GENIUS" }))
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

        // 3. 自动中英翻译处理：如果获取到了 Genius 英文内容，立即并发自动翻译为中文对照（含歌词引用与背景故事）
        val finalAnnotations = mutableListOf<LyricAnnotationEntity>()
        if (annotationEntities.isNotEmpty()) {
            supervisorScope {
                val translatedList = annotationEntities.map { annot ->
                    async {
                        var updated = annot
                        // 3.1 翻译歌词片段 (lyricFragment)
                        if (!AiAnnotationCurator.isAlreadyChinese(annot.lyricFragment) && annot.lyricTranslation.isNullOrBlank()) {
                            val lyricTrans = translationService.translateText(annot.lyricFragment, "zh")
                            if (!lyricTrans.isNullOrBlank()) {
                                val finalLyricTrans = if (isTraditionalTarget) ChineseConverter.toTraditional(lyricTrans) else lyricTrans
                                updated = updated.copy(lyricTranslation = finalLyricTrans)
                            }
                        }
                        // 3.2 翻译典故解说 (explanationText)
                        if (!AiAnnotationCurator.isAlreadyChinese(annot.explanationText) && annot.explanationTranslation.isNullOrBlank()) {
                            val trans = translationService.translateText(annot.explanationText, "zh")
                            if (!trans.isNullOrBlank()) {
                                val finalTrans = if (isTraditionalTarget) ChineseConverter.toTraditional(trans) else trans
                                updated = updated.copy(explanationTranslation = finalTrans)
                            }
                        }
                        updated
                    }
                }.awaitAll()
                finalAnnotations.addAll(translatedList)
            }
        }

        if (storyEntity != null && !storyEntity.descriptionPlain.isBlank()) {
            if (!AiAnnotationCurator.isAlreadyChinese(storyEntity.descriptionPlain) && storyEntity.descriptionTranslation.isNullOrBlank()) {
                val transStory = translationService.translateText(storyEntity.descriptionPlain, "zh")
                if (!transStory.isNullOrBlank()) {
                    val finalStoryTrans = if (isTraditionalTarget) ChineseConverter.toTraditional(transStory) else transStory
                    storyEntity = storyEntity.copy(descriptionTranslation = finalStoryTrans)
                }
            }
        }

        // 4. 本地 Room 持久化（不使用伪造模板，如无 Genius 数据则干净利落，不污染页面）
        if (storyEntity != null) {
            if (AiAnnotationCurator.isAlreadyChinese(storyEntity.descriptionPlain)) {
                storyEntity = storyEntity.copy(descriptionTranslation = null)
            }
            lyricAnnotationDao.insertSongStory(storyEntity)
        }

        // 清理旧数据，保存真实 Genius 典故
        lyricAnnotationDao.deleteAnnotationsForTrack(trackId)
        if (finalAnnotations.isNotEmpty()) {
            lyricAnnotationDao.insertAnnotations(finalAnnotations)
        }

        return@withContext Result.success(Pair(storyEntity, finalAnnotations))
    }

    /**
     * 针对外文注释进行本地中文/目标语言机器翻译并持久化
     */
    suspend fun translateAnnotation(annotation: LyricAnnotationEntity): LyricAnnotationEntity = withContext(Dispatchers.IO) {
        val isTraditionalTarget = aiPreferences.targetLanguage == "zh-TW" ||
            TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage) == TranslationTargetLanguage.ZH_TW

        var updated = annotation

        // 翻译歌词片段
        if (updated.lyricTranslation.isNullOrBlank() && !AiAnnotationCurator.isAlreadyChinese(updated.lyricFragment)) {
            val transLyric = translationService.translateText(updated.lyricFragment, "zh")
            if (!transLyric.isNullOrBlank()) {
                val finalLyric = if (isTraditionalTarget) ChineseConverter.toTraditional(transLyric) else transLyric
                updated = updated.copy(lyricTranslation = finalLyric)
            }
        }

        // 翻译典故解说
        if (updated.explanationTranslation.isNullOrBlank() && !AiAnnotationCurator.isAlreadyChinese(updated.explanationText)) {
            val targetIso = TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage).fallbackIso
            val translated = translationService.translateText(updated.explanationText, targetIso)
            val baseTrans = if (!translated.isNullOrBlank()) translated else updated.explanationText
            val finalTrans = if (isTraditionalTarget) ChineseConverter.toTraditional(baseTrans) else baseTrans
            updated = updated.copy(explanationTranslation = finalTrans)
        }

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

        val isTraditionalTarget = aiPreferences.targetLanguage == "zh-TW" ||
            TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage) == TranslationTargetLanguage.ZH_TW
        val targetIso = TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage).fallbackIso
        val translated = translationService.translateText(story.descriptionPlain, targetIso)
        val baseTrans = if (!translated.isNullOrBlank()) translated else story.descriptionPlain
        val finalTrans = if (isTraditionalTarget) ChineseConverter.toTraditional(baseTrans) else baseTrans

        val updated = story.copy(descriptionTranslation = finalTrans)
        lyricAnnotationDao.updateSongStory(updated)
        updated
    }
}
