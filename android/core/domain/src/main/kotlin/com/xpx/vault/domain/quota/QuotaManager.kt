package com.xpx.vault.domain.quota

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 配额管理器接口。跟踪免费版各项额度的使用量并判定是否超限。
 *
 * Premium 用户调用 [isXxxExhausted] 始终返回 false（由实现层结合 isPremium 判断）。
 */
interface QuotaManager {

    /** 当前保险箱内条目总数（实时 Flow）。 */
    fun observeVaultUsage(): Flow<Int>

    /** 累计备份次数（历史记录行数）。 */
    fun observeBackupUsage(): Flow<Int>

    /** 当月 AI 使用次数。 */
    fun observeAiMonthlyUsage(): Flow<Int>

    /** 保险箱存储是否已满（免费额度耗尽且非 Premium）。 */
    fun isVaultFull(): Boolean

    /** 备份次数是否用尽。 */
    fun isBackupExhausted(): Boolean

    /** 当月 AI 次数是否用尽。 */
    fun isAiExhausted(): Boolean

    /** 递增当月 AI 使用计数（执行一次 AI 操作后调用）。 */
    suspend fun incrementAiUsage()

    /** 更新 vault 条目总数（VaultStore 是文件系统级的，由调用方主动推送）。 */
    fun updateVaultCount(count: Int)
}
