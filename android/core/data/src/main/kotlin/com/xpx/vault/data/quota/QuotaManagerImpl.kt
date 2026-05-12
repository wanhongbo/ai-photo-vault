package com.xpx.vault.data.quota

import android.content.Context
import com.xpx.vault.data.db.dao.BackupRecordDao
import com.xpx.vault.data.db.dao.QuotaUsageDao
import com.xpx.vault.data.db.entity.QuotaUsageEntity
import com.xpx.vault.domain.quota.FreeQuota
import com.xpx.vault.domain.quota.QuotaManager
import com.xpx.vault.domain.repo.SubscriptionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [QuotaManager] 的 Data 层实现。
 *
 * - Vault 条目数：通过 VaultStore 的 snapshot totalCount 读取（file-based）。
 * - 备份次数：从 backup_records 表行数获取。
 * - AI 月度使用：从 quota_usage 表当月行获取。
 *
 * 所有 `isXxxExhausted()` 在 isPremium == true 时返回 false。
 */
@Singleton
class QuotaManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val quotaUsageDao: QuotaUsageDao,
    private val backupRecordDao: BackupRecordDao,
    private val subscriptionRepository: SubscriptionRepository,
) : QuotaManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _vaultCount = MutableStateFlow(0)
    private val _backupCount = MutableStateFlow(0)
    private val _aiMonthlyCount = MutableStateFlow(0)

    private val yearMonthFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

    init {
        scope.launch { refreshBackupCount() }
        scope.launch { refreshAiCount() }
    }

    // ---- Public API ----

    override fun observeVaultUsage(): Flow<Int> = _vaultCount

    override fun observeBackupUsage(): Flow<Int> = _backupCount

    override fun observeAiMonthlyUsage(): Flow<Int> = _aiMonthlyCount

    override fun isVaultFull(): Boolean {
        if (subscriptionRepository.isPremium.value) return false
        return _vaultCount.value >= FreeQuota.MAX_VAULT_ITEMS
    }

    override fun isBackupExhausted(): Boolean {
        if (subscriptionRepository.isPremium.value) return false
        return _backupCount.value >= FreeQuota.MAX_BACKUP_COUNT
    }

    override fun isAiExhausted(): Boolean {
        if (subscriptionRepository.isPremium.value) return false
        return _aiMonthlyCount.value >= FreeQuota.MAX_AI_MONTHLY
    }

    override suspend fun incrementAiUsage() {
        val ym = currentYearMonth()
        quotaUsageDao.insertIfAbsent(QuotaUsageEntity(yearMonth = ym))
        quotaUsageDao.incrementAiCount(ym)
        refreshAiCount()
    }

    // ---- 由外部调用刷新 vault count ----

    /**
     * 当 VaultStore snapshot 变化后调用此方法更新 vault 计数。
     * VaultStore 是文件系统级的，不走 Room，因此由调用方主动推送。
     */
    override fun updateVaultCount(count: Int) {
        _vaultCount.value = count
    }

    fun refreshBackupCountAsync() {
        scope.launch { refreshBackupCount() }
    }

    // ---- Internal ----

    private suspend fun refreshBackupCount() {
        _backupCount.value = backupRecordDao.count()
    }

    private suspend fun refreshAiCount() {
        val ym = currentYearMonth()
        val entity = quotaUsageDao.findByMonth(ym)
        _aiMonthlyCount.value = entity?.aiCount ?: 0
    }

    private fun currentYearMonth(): String =
        LocalDate.now().format(yearMonthFormatter)
}
