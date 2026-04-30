package com.xpx.vault.ui.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateOf
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * 导出到系统相册的运行时共享状态，用于跨 Composable 传递选择集合与结果。
 *
 * 简单模式：调用 [enqueue] 设置待导出路径集合，进入 ExportProgressScreen
 * 后由该页面异步执行并更新 [progress]。完成后将 [lastResult] 写入，供 ExportResultScreen 读取。
 */
object ExportRuntimeState {
    /** 下次进入进度页要处理的源文件路径。 */
    var pendingPaths: List<String> = emptyList()
        private set

    /** 已完成进度（用于进度页 UI）。 */
    val progressDone = mutableStateOf(0)
    val progressTotal = mutableStateOf(0)
    val progressCurrentName = mutableStateOf<String?>(null)

    /** 最近一次导出结果，用于 ExportResultScreen 展示。 */
    var lastResult: ExportBatchResult? = null
        private set

    fun enqueue(paths: List<String>) {
        pendingPaths = paths.distinct()
        progressDone.value = 0
        progressTotal.value = pendingPaths.size
        progressCurrentName.value = null
        lastResult = null
    }

    fun clearPending() {
        pendingPaths = emptyList()
        progressDone.value = 0
        progressTotal.value = 0
        progressCurrentName.value = null
    }

    fun publishResult(result: ExportBatchResult) {
        lastResult = result
    }

    /**
     * 特征参数：
     * - 并发度 [MAX_CONCURRENCY]=3，在大量小图场景下将 MediaStore Binder 开销并行化，大视频场景下也不会塑能造成额外负担（IO 带宽将先成为瓶颈）。
     * - 进度回调以 [PROGRESS_THROTTLE_MS] 节流，避免频繁触发 Compose 重组；最后一项必定会触发一次全量更新。
     */
    private const val MAX_CONCURRENCY = 3
    private const val PROGRESS_THROTTLE_MS = 100L

    /**
     * 真正执行导出：限并发处理，每完成一项更新计数器，节流推送进度回调。
     */
    suspend fun runExport(
        context: Context,
        paths: List<String>,
        onEachDone: (index: Int, outcome: MediaExporter.ExportOutcome) -> Unit = { _, _ -> },
    ): ExportBatchResult = withContext(Dispatchers.IO) {
        // ① 空间预检：欲写入总大小 × 1.1 < 可用空间，避免写到一半磁盘满。
        val totalBytes = paths.sumOf { runCatching { File(it).length() }.getOrDefault(0L) }
        val available = runCatching {
            val path = Environment.getExternalStorageDirectory()?.path
                ?: context.filesDir.path
            StatFs(path).availableBytes
        }.getOrDefault(Long.MAX_VALUE)
        if (totalBytes > 0 && available < (totalBytes + totalBytes / 10)) {
            val failed = paths.map { p ->
                ExportItem(
                    sourcePath = p,
                    displayName = p.substringAfterLast('/'),
                    uri = null,
                    reason = "insufficient_space",
                )
            }
            return@withContext ExportBatchResult(success = emptyList(), failed = failed)
        }

        val success = java.util.Collections.synchronizedList(mutableListOf<ExportItem>())
        val failed = java.util.Collections.synchronizedList(mutableListOf<ExportItem>())
        val doneCount = AtomicInteger(0)
        val total = paths.size
        var lastEmitAt = 0L
        val semaphore = Semaphore(permits = MAX_CONCURRENCY)
        coroutineScope {
            paths.mapIndexed { index, path ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        // ③ 支持取消：协程被 cancel 时下一项不再开始。
                        ensureActive()
                        val outcome: MediaExporter.ExportOutcome = try {
                            MediaExporter.exportFile(context, path)
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (t: Throwable) {
                            MediaExporter.ExportOutcome.Failure(t.message ?: "error")
                        }
                        when (outcome) {
                            is MediaExporter.ExportOutcome.Success -> success.add(
                                ExportItem(
                                    sourcePath = path,
                                    displayName = outcome.displayName,
                                    uri = outcome.uri,
                                    reason = null,
                                ),
                            )
                            is MediaExporter.ExportOutcome.Failure -> failed.add(
                                ExportItem(
                                    sourcePath = path,
                                    displayName = path.substringAfterLast('/'),
                                    uri = null,
                                    reason = outcome.reason,
                                ),
                            )
                        }
                        val finished = doneCount.incrementAndGet()
                        // 节流：最后一项或上次推送超过门限时更新 UI
                        val now = System.currentTimeMillis()
                        val shouldEmit = finished == total || now - lastEmitAt >= PROGRESS_THROTTLE_MS
                        if (shouldEmit) {
                            lastEmitAt = now
                            progressDone.value = finished
                            progressCurrentName.value = when (outcome) {
                                is MediaExporter.ExportOutcome.Success -> outcome.displayName
                                is MediaExporter.ExportOutcome.Failure -> null
                            }
                        }
                        onEachDone(index, outcome)
                    }
                }
            }.awaitAll()
        }
        // 关闭时强制保证 UI 读到终态
        progressDone.value = doneCount.get()
        ExportBatchResult(success = success.toList(), failed = failed.toList())
    }
}

data class ExportItem(
    val sourcePath: String,
    val displayName: String,
    val uri: Uri?,
    val reason: String?,
)

data class ExportBatchResult(
    val success: List<ExportItem>,
    val failed: List<ExportItem>,
) {
    val total: Int get() = success.size + failed.size
    val hasFailure: Boolean get() = failed.isNotEmpty()
}

/**
 * 构造一个打开系统相册的 Intent（优先跳到 Pictures/PhotoVault 所在位置）。
 */
fun buildOpenGalleryIntent(): Intent {
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
