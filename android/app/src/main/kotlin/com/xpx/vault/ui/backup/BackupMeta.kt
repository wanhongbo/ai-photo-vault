package com.xpx.vault.ui.backup

import android.content.Context
import com.xpx.vault.AppLogger
import com.xpx.vault.data.crypto.BackupKeyManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 私有目录中的 `backup_meta.json` 读写器。
 *
 * 结构参考 spec 8.1：
 * ```
 * {
 *   "version": 1,
 *   "auto": { "lastBackupId", "lastBackupAtMs", "keyFingerprintHex", "kdfParams",
 *             "externalUri", "assetIndex": [ { "relativePath", "sha256Hex", "sizeBytes" } ] } | null,
 *   "manualHistory": [ { "createdAtMs", "uri", "sizeBytes", "note" } ]
 * }
 * ```
 *
 * - `auto.*` 由 AUTO 备份写入；手动备份**绝不触碰** `auto`。
 * - `manualHistory` 仅存元信息，不存 fingerprint/kdf。
 */
object BackupMeta {
    private const val FILE_NAME = "backup_meta.json"
    private const val META_VERSION = 1
    private const val TAG = "BackupMeta"

    data class AutoMeta(
        val lastBackupId: String,
        val lastBackupAtMs: Long,
        val keyFingerprintHex: String,
        val kdfParams: BackupKeyManager.KdfParams,
        val externalUri: String?,
        val assetIndex: List<AssetIndexEntry>,
    )

    data class AssetIndexEntry(
        val relativePath: String,
        val sha256Hex: String,
        val sizeBytes: Long,
    )

    data class ManualEntry(
        val createdAtMs: Long,
        val uri: String,
        val sizeBytes: Long,
        val note: String?,
    )

    data class Snapshot(
        val auto: AutoMeta?,
        val manualHistory: List<ManualEntry>,
    )

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun load(context: Context): Snapshot {
        val f = file(context)
        if (!f.exists()) return Snapshot(auto = null, manualHistory = emptyList())
        return runCatching {
            val root = JSONObject(f.readText(Charsets.UTF_8))
            parseSnapshot(root)
        }.getOrElse {
            AppLogger.w(TAG, "load failed, reset: ${it.message}")
            Snapshot(auto = null, manualHistory = emptyList())
        }
    }

    fun save(context: Context, snapshot: Snapshot) {
        val root = JSONObject().put("version", META_VERSION)
        if (snapshot.auto != null) root.put("auto", serializeAuto(snapshot.auto)) else root.put("auto", JSONObject.NULL)
        val manual = JSONArray()
        snapshot.manualHistory.forEach { m ->
            manual.put(
                JSONObject()
                    .put("createdAtMs", m.createdAtMs)
                    .put("uri", m.uri)
                    .put("sizeBytes", m.sizeBytes)
                    .put("note", m.note ?: JSONObject.NULL),
            )
        }
        root.put("manualHistory", manual)
        val out = file(context)
        out.parentFile?.mkdirs()
        out.writeText(root.toString(), Charsets.UTF_8)
    }

    /** 更新 auto 元信息，manualHistory 保持不变。 */
    fun updateAuto(context: Context, auto: AutoMeta) {
        val cur = load(context)
        save(context, cur.copy(auto = auto))
    }

    /** 追加一条 manual 历史，auto 保持不变。 */
    fun appendManual(context: Context, entry: ManualEntry, keepLatest: Int = 64) {
        val cur = load(context)
        val next = (listOf(entry) + cur.manualHistory).take(keepLatest)
        save(context, cur.copy(manualHistory = next))
    }

    /** 清空 auto 块（首启放弃备份、或测试重置使用）。 */
    fun clearAuto(context: Context) {
        val cur = load(context)
        save(context, cur.copy(auto = null))
    }

    /** 删除整个 meta 文件。 */
    fun deleteAll(context: Context) {
        file(context).delete()
    }

    // ---------- 序列化 ----------

    private fun serializeAuto(auto: AutoMeta): JSONObject {
        val kdf = JSONObject()
            .put("algorithm", auto.kdfParams.algorithm)
            .put("saltHex", auto.kdfParams.saltHex)
            .put("iterations", auto.kdfParams.iterations)
            .put("memoryKb", auto.kdfParams.memoryKb)
            .put("parallelism", auto.kdfParams.parallelism)
        val arr = JSONArray()
        auto.assetIndex.forEach { e ->
            arr.put(
                JSONObject()
                    .put("relativePath", e.relativePath)
                    .put("sha256Hex", e.sha256Hex)
                    .put("sizeBytes", e.sizeBytes),
            )
        }
        return JSONObject()
            .put("lastBackupId", auto.lastBackupId)
            .put("lastBackupAtMs", auto.lastBackupAtMs)
            .put("keyFingerprintHex", auto.keyFingerprintHex)
            .put("kdfParams", kdf)
            .put("externalUri", auto.externalUri ?: JSONObject.NULL)
            .put("assetIndex", arr)
    }

    private fun parseSnapshot(root: JSONObject): Snapshot {
        val auto = root.opt("auto")?.let {
            if (it is JSONObject) parseAuto(it) else null
        }
        val manualArr = root.optJSONArray("manualHistory") ?: JSONArray()
        val manualList = buildList {
            for (i in 0 until manualArr.length()) {
                val m = manualArr.getJSONObject(i)
                add(
                    ManualEntry(
                        createdAtMs = m.getLong("createdAtMs"),
                        uri = m.getString("uri"),
                        sizeBytes = m.getLong("sizeBytes"),
                        note = m.optString("note", "").takeIf { s -> s.isNotBlank() },
                    ),
                )
            }
        }
        return Snapshot(auto = auto, manualHistory = manualList)
    }

    private fun parseAuto(json: JSONObject): AutoMeta {
        val kdf = json.getJSONObject("kdfParams")
        val indexArr = json.optJSONArray("assetIndex") ?: JSONArray()
        val assetIndex = buildList {
            for (i in 0 until indexArr.length()) {
                val e = indexArr.getJSONObject(i)
                add(
                    AssetIndexEntry(
                        relativePath = e.getString("relativePath"),
                        sha256Hex = e.getString("sha256Hex"),
                        sizeBytes = e.getLong("sizeBytes"),
                    ),
                )
            }
        }
        return AutoMeta(
            lastBackupId = json.getString("lastBackupId"),
            lastBackupAtMs = json.getLong("lastBackupAtMs"),
            keyFingerprintHex = json.getString("keyFingerprintHex"),
            kdfParams = BackupKeyManager.KdfParams(
                algorithm = kdf.getString("algorithm"),
                saltHex = kdf.getString("saltHex"),
                iterations = kdf.getInt("iterations"),
                memoryKb = kdf.getInt("memoryKb"),
                parallelism = kdf.getInt("parallelism"),
            ),
            externalUri = json.optString("externalUri", "").takeIf { it.isNotBlank() },
            assetIndex = assetIndex,
        )
    }
}
