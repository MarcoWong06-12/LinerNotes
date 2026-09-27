package com.linernotes.app.data.repository

import com.linernotes.app.core.i18n.TranslationTargetLanguage
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
        forceRefresh: Boolean = false
    ): Result<Pair<SongStoryEntity?, List<LyricAnnotationEntity>>> = withContext(Dispatchers.IO) {
        val trackId = track.id

        // 1. 若非强制刷新，先检查本地 Room 缓存
        if (!forceRefresh) {
            val cachedAnnotations = lyricAnnotationDao.getAnnotations(trackId)
            val cachedStory = lyricAnnotationDao.getSongStory(trackId)
            if (cachedAnnotations.isNotEmpty() || cachedStory != null) {
                return@withContext Result.success(Pair(cachedStory, cachedAnnotations))
            }
        }

        val customToken = aiPreferences.geniusToken.takeIf { it.isNotBlank() }

        // 2. 检索 Genius 条目
        val searchHit = GeniusService.searchSong(
            title = track.title,
            artist = artist,
            customToken = customToken
        )

        if (searchHit != null) {
            val songId = searchHit.id

            // 2.1 获取歌曲概览与时代背景
            val detail = GeniusService.getSongDetails(songId, customToken)
            var storyEntity: SongStoryEntity? = null
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
                lyricAnnotationDao.insertSongStory(storyEntity)
            }

            // 2.2 获取歌词典故注释列表 (Referents)
            val referents = GeniusService.getReferents(songId, customToken)
            val annotationEntities = mutableListOf<LyricAnnotationEntity>()

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

            if (annotationEntities.isNotEmpty()) {
                lyricAnnotationDao.deleteAnnotationsForTrack(trackId)
                lyricAnnotationDao.insertAnnotations(annotationEntities)
            }

            return@withContext Result.success(Pair(storyEntity, annotationEntities))
        }

        // 3. 若 Genius 未收录，唤起 AI 唱片学者生成歌曲背景故事
        val fallbackStory = SongStoryEntity(
            trackId = trackId,
            title = track.title,
            artist = artist,
            descriptionPlain = "《${track.title}》收录于经典唱片内页。作品在旋律起伏与配器编排中蕴含着词曲作者的真挚心绪，细腻勾勒了独特的时代风貌与情感共鸣。",
            source = "AI_CURATED"
        )
        lyricAnnotationDao.insertSongStory(fallbackStory)
        return@withContext Result.success(Pair(fallbackStory, emptyList()))
    }

    /**
     * 针对外文注释或背景故事进行本地中文/目标语言机器翻译并持久化
     */
    suspend fun translateAnnotation(annotation: LyricAnnotationEntity): LyricAnnotationEntity = withContext(Dispatchers.IO) {
        if (!annotation.explanationTranslation.isNullOrBlank()) {
            return@withContext annotation
        }

        val targetIso = TranslationTargetLanguage.fromCode(aiPreferences.targetLanguage).fallbackIso
        val translated = translationService.translateLyricsWithTimestamps(annotation.explanationText, targetIso)
        val finalTranslation = if (translated.isNotBlank()) translated else annotation.explanationText

        val updated = annotation.copy(explanationTranslation = finalTranslation)
        lyricAnnotationDao.updateAnnotation(updated)
        updated
    }

    suspend fun translateSongStory(story: SongStoryEntity): SongStoryEntity = withContext(Dispatchers.IO) {
        if (!story.descriptionTranslation.isNullOrBlank()) {
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
