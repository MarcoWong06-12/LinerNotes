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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnnotationRepository @Inject constructor(
    private val lyricAnnotationDao: LyricAnnotationDao,
    private val aiPreferences: AiPreferences,
    private val translationService: TranslationService
) {

    companion object {
        private const val NEGATIVE_CACHE_DURATION_MS = 15 * 60 * 1000L // 15 分钟短效负向缓存，防止无典故歌曲频繁重复查询，同时避免网络波动造成长久死锁
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activePrefetchAlbums = ConcurrentHashMap.newKeySet<String>()
    private val negativeCache = ConcurrentHashMap<Long, Long>()

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
     * 将专辑加入常驻后台预取队列（多轨受控并发下载，应用级生命周期保障）
     */
    fun enqueueAlbumPrefetch(albumId: String, artist: String, tracks: List<TrackEntity>) {
        if (tracks.isEmpty()) return
        if (!activePrefetchAlbums.add(albumId)) return

        repositoryScope.launch {
            try {
                prefetchAlbumAnnotationsInternal(artist, tracks)
            } finally {
                activePrefetchAlbums.remove(albumId)
            }
        }
    }

    /**
     * 后台静默预拉取全专辑曲目的 Genius 典故与背景故事（无感且极速）
     */
    suspend fun prefetchAlbumAnnotations(artist: String, tracks: List<TrackEntity>) = withContext(Dispatchers.IO) {
        prefetchAlbumAnnotationsInternal(artist, tracks)
    }

    private suspend fun prefetchAlbumAnnotationsInternal(artist: String, tracks: List<TrackEntity>) = coroutineScope {
        val semaphore = Semaphore(3)
        tracks.map { track ->
            async {
                semaphore.withPermit {
                    try {
                        val cachedStory = lyricAnnotationDao.getSongStory(track.id)
                        val cachedAnnotations = lyricAnnotationDao.getAnnotations(track.id)
                        val isGeniusData = cachedStory?.source == "GENIUS" || cachedAnnotations.any { it.source == "GENIUS" }
                        val checkedTime = negativeCache[track.id]
                        val isNegCached = checkedTime != null && (System.currentTimeMillis() - checkedTime < NEGATIVE_CACHE_DURATION_MS)

                        if (isGeniusData || isNegCached) {
                            return@withPermit
                        }

                        fetchAndCacheAnnotations(track = track, artist = artist)
                    } catch (e: Exception) {
                        // 静默处理，避免干扰前台正常交互
                    }
                }
            }
        }.awaitAll()
    }

    /**
     * 从 Genius 检索、解析并本地持久化当前曲目的歌词典故与背景故事。
     * 原生 Genius 数据解析完成后即刻入库并返回界面渲染（秒级展示），
     * 中文对照翻译在后台异步平滑执行，绝不阻塞界面，不套用虚假默认模板。
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

        // 1. 本地 Room 缓存与负向缓存检查：
        if (forceRefresh) {
            negativeCache.remove(trackId)
        } else {
            val checkedTime = negativeCache[trackId]
            if (checkedTime != null && System.currentTimeMillis() - checkedTime < NEGATIVE_CACHE_DURATION_MS) {
                return@withContext Result.success(Pair(null, emptyList()))
            }

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
        val cleanTitle = GeniusService.sanitizeTitle(track.title)
        val customToken = aiPreferences.geniusToken.takeIf { it.isNotBlank() }

        var geniusRequestSucceeded = false
        var storyEntity: SongStoryEntity? = null
        val annotationEntities = mutableListOf<LyricAnnotationEntity>()

        try {
            val searchHit = GeniusService.searchSong(
                title = cleanTitle,
                artist = artist,
                customToken = customToken
            )
            geniusRequestSucceeded = true

            if (searchHit != null) {
                val songId = searchHit.id

                // 2.1 获取歌曲背景总览 (About this Song)
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

                // 2.2 获取该曲目的全部逐句歌词典故列表 (Referents)
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
            // 网络受限或 Genius 超时，不标记请求成功
        }

        // 3. 立即本地持久化原生 Genius 数据（不等翻译！让界面毫秒级秒开展示！）
        if (storyEntity != null) {
            if (AiAnnotationCurator.isAlreadyChinese(storyEntity.descriptionPlain)) {
                storyEntity = storyEntity.copy(descriptionTranslation = null)
            }
            lyricAnnotationDao.insertSongStory(storyEntity)
        }

        lyricAnnotationDao.deleteAnnotationsForTrack(trackId)
        if (annotationEntities.isNotEmpty()) {
            lyricAnnotationDao.insertAnnotations(annotationEntities)
        }

        // 4. 在后台异步协程中温和执行中文对照翻译，逐步更新数据库，绝不阻塞前台渲染
        if (annotationEntities.isNotEmpty() || (storyEntity != null && !storyEntity.descriptionPlain.isBlank())) {
            CoroutineScope(Dispatchers.IO).launch {
                // 4.1 异步翻译背景故事
                if (storyEntity != null && !storyEntity.descriptionPlain.isBlank() &&
                    !AiAnnotationCurator.isAlreadyChinese(storyEntity.descriptionPlain) &&
                    storyEntity.descriptionTranslation.isNullOrBlank()
                ) {
                    try {
                        val transStory = translationService.translateText(storyEntity.descriptionPlain, "zh")
                        if (!transStory.isNullOrBlank()) {
                            val finalStoryTrans = if (isTraditionalTarget) ChineseConverter.toTraditional(transStory) else transStory
                            val updatedStory = storyEntity.copy(descriptionTranslation = finalStoryTrans)
                            lyricAnnotationDao.updateSongStory(updatedStory)
                        }
                    } catch (e: Exception) { /* ignore */ }
                }

                // 4.2 顺序/温和翻译各条歌词注释与片段 (带延时防限流)
                for (annot in annotationEntities) {
                    try {
                        var updated = annot
                        if (!AiAnnotationCurator.isAlreadyChinese(annot.lyricFragment) && annot.lyricTranslation.isNullOrBlank()) {
                            val lyricTrans = translationService.translateText(annot.lyricFragment, "zh")
                            if (!lyricTrans.isNullOrBlank()) {
                                val finalLyricTrans = if (isTraditionalTarget) ChineseConverter.toTraditional(lyricTrans) else lyricTrans
                                updated = updated.copy(lyricTranslation = finalLyricTrans)
                            }
                        }
                        if (!AiAnnotationCurator.isAlreadyChinese(annot.explanationText) && annot.explanationTranslation.isNullOrBlank()) {
                            val trans = translationService.translateText(annot.explanationText, "zh")
                            if (!trans.isNullOrBlank()) {
                                val finalTrans = if (isTraditionalTarget) ChineseConverter.toTraditional(trans) else trans
                                updated = updated.copy(explanationTranslation = finalTrans)
                            }
                        }
                        if (updated != annot) {
                            lyricAnnotationDao.updateAnnotation(updated)
                        }
                        delay(100)
                    } catch (e: Exception) { /* ignore */ }
                }
            }
        }

        if (storyEntity == null && annotationEntities.isEmpty()) {
            if (geniusRequestSucceeded) {
                // 仅当网络请求顺利完成但曲目确实没有典故时，缓存 15 分钟
                negativeCache[trackId] = System.currentTimeMillis()
            }
        } else {
            negativeCache.remove(trackId)
        }

        if (!geniusRequestSucceeded && storyEntity == null && annotationEntities.isEmpty()) {
            return@withContext Result.failure(Exception("Genius request failed due to network exception or timeout"))
        }

        return@withContext Result.success(Pair(storyEntity, annotationEntities))
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
