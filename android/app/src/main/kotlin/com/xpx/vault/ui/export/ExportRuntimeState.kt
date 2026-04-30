package com.xpx.vault.ui.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
     * 真正执行导出，顺序处理，每完成一项更新进度回调。
     */
    suspend fun runExport(
        context: Context,
        paths: List<String>,
        onEachDone: (index: Int, outcome: MediaExporter.ExportOutcome) -> Unit = { _, _ -> },
    ): ExportBatchResult = withContext(Dispatchers.IO) {
        val success = mutableListOf<ExportItem>()
        val failed = mutableListOf<ExportItem>()
        paths.forEachIndexed { index, path ->
            try {
                val outcome = MediaExporter.exportFile(context, path)
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
                onEachDone(index, outcome)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                failed.add(
                    ExportItem(
                        sourcePath = path,
                        displayName = path.substringAfterLast('/'),
                        uri = null,
                        reason = t.message ?: "error",
                    ),
                )
                onEachDone(index, MediaExporter.ExportOutcome.Failure(t.message ?: "error"))
            }
        }
        ExportBatchResult(success = success, failed = failed)
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
