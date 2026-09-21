package com.linernotes.app.core.translation

import android.content.Context
import android.content.Intent
import android.os.Build
import com.linernotes.app.core.debug.AiDebugLogger
import com.linernotes.app.core.preference.AiPreferences
import com.linernotes.app.data.remote.AiTranslationService
import com.linernotes.app.domain.repository.AlbumRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 专辑全量批量翻译调度管理器
 * 负责全局单例维护批量翻译生命周期、限制速率并发、持久化写入数据库并与前台通知服务协同。
 */
@Singleton
class BatchTranslationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AlbumRepository,
    private val translationService: AiTranslationService,
    private val aiPreferences: AiPreferences
) {
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var batchJob: Job? = null

    private val _state = MutableStateFlow(BatchTranslationState())
    val state: StateFlow<BatchTranslationState> = _state.asStateFlow()

    fun startBatchTranslation(albumId: String, albumTitle: String) {
        if (_state.value.isTranslating) {
            AiDebugLogger.log(false, "批量翻译", "当前已有一个正在进行的翻译任务，请等待完成或取消")
            return
        }

        batchJob?.cancel()
        batchJob = managerScope.launch {
            try {
                AiDebugLogger.log(true, "批量翻译", "启动整张专辑批量翻译: 《$albumTitle》")

                // 1. 获取专辑所有曲目并筛选有歌词的曲目
                val albumWithTracks = repository.getAlbumBookletStream(albumId).first()
                val candidateTracks = albumWithTracks?.tracks?.filter { !it.originalLyrics.isNullOrBlank() } ?: emptyList()

                if (candidateTracks.isEmpty()) {
                    AiDebugLogger.log(false, "批量翻译", "专辑中未找到包含歌词的曲目")
                    _state.update {
                        it.copy(
                            isTranslating = false,
                            userMessage = "该专辑中暂无包含歌词的曲目"
                        )
                    }
                    return@launch
                }

                val total = candidateTracks.size
                _state.update {
                    it.copy(
                        isTranslating = true,
                        albumId = albumId,
                        albumTitle = albumTitle,
                        currentTrackTitle = "准备中...",
                        currentTrackIndex = 0,
                        totalTracks = total,
                        userMessage = "正在后台并发推敲翻译中..."
                    )
                }

                // 2. 启动前台保活服务
                startForegroundService(albumId, albumTitle)

                // 3. 并发限流控制（信号量限制为 2，兼顾两倍吞吐量且杜绝中转 API 频繁 429 报错）
                val semaphore = Semaphore(2)
                val completedCount = AtomicInteger(0)

                val trackJobs = candidateTracks.map { track ->
                    launch {
                        semaphore.withPermit {
                            if (!isActive) return@withPermit
                            _state.update { it.copy(currentTrackTitle = track.title) }

                            try {
                                val result = translationService.translateTrack(
                                    trackTitle = track.title,
                                    originalLyrics = track.originalLyrics!!
                                )
                                // 翻译成功立即落库，即使中途退出也保留已翻译的成果
                                repository.updateTrackTranslation(
                                    trackId = track.id,
                                    translatedTitle = result.translatedTitle ?: track.translatedTitle,
                                    originalLyrics = track.originalLyrics,
                                    translatedLyrics = result.translatedLyrics
                                )
                                AiDebugLogger.log(true, "批量翻译单曲成功", "《${track.title}》译文已存盘")
                            } catch (e: Exception) {
                                AiDebugLogger.log(false, "批量翻译单曲跳过", "《${track.title}》翻译出错: ${e.message}")
                            } finally {
                                val done = completedCount.incrementAndGet()
                                _state.update { it.copy(currentTrackIndex = done) }
                            }
                        }
                    }
                }

                trackJobs.forEach { it.join() }

                AiDebugLogger.log(true, "批量翻译", "整张专辑《$albumTitle》翻译全部完毕 ($total 首)")
                _state.update {
                    it.copy(
                        isTranslating = false,
                        userMessage = "整张专辑翻译完成！"
                    )
                }
            } catch (e: Exception) {
                AiDebugLogger.log(false, "批量翻译异常", "${e.message}")
                _state.update {
                    it.copy(
                        isTranslating = false,
                        userMessage = "批量翻译遇到错误: ${e.message}"
                    )
                }
            } finally {
                stopForegroundService()
            }
        }
    }

    fun cancelBatchTranslation() {
        if (_state.value.isTranslating) {
            batchJob?.cancel()
            batchJob = null
            _state.update {
                it.copy(
                    isTranslating = false,
                    userMessage = "已取消批量翻译"
                )
            }
            stopForegroundService()
            AiDebugLogger.log(false, "批量翻译", "用户取消了专辑批量翻译")
        }
    }

    private fun startForegroundService(albumId: String, albumTitle: String) {
        try {
            val intent = Intent(context, BatchTranslationService::class.java).apply {
                action = BatchTranslationService.ACTION_START
                putExtra(BatchTranslationService.EXTRA_ALBUM_ID, albumId)
                putExtra(BatchTranslationService.EXTRA_ALBUM_TITLE, albumTitle)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            AiDebugLogger.log(false, "前台服务启动异常", "${e.message}")
        }
    }

    private fun stopForegroundService() {
        try {
            val intent = Intent(context, BatchTranslationService::class.java).apply {
                action = BatchTranslationService.ACTION_STOP
            }
            context.startService(intent)
        } catch (e: Exception) {
            AiDebugLogger.log(false, "前台服务关闭异常", "${e.message}")
        }
    }
}
