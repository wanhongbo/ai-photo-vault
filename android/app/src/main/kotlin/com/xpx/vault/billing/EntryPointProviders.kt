package com.xpx.vault.billing

import android.content.Context
import com.xpx.vault.domain.quota.QuotaManager
import com.xpx.vault.domain.repo.AiAnalysisRepository
import com.xpx.vault.domain.repo.SubscriptionRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 为 Composable 层提供 [SubscriptionRepository] 访问（非 ViewModel 场景）。
 */
object SubscriptionRepoProvider {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RepoEntryPoint {
        fun subscriptionRepository(): SubscriptionRepository
    }

    fun get(context: Context): SubscriptionRepository? =
        runCatching {
            val ep = EntryPointAccessors.fromApplication(
                context.applicationContext,
                RepoEntryPoint::class.java,
            )
            ep.subscriptionRepository()
        }.getOrNull()
}

/**
 * 为 Composable 层提供 [QuotaManager] 访问（非 ViewModel 场景）。
 */
object QuotaManagerProvider {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface QuotaEntryPoint {
        fun quotaManager(): QuotaManager
    }

    fun get(context: Context): QuotaManager? =
        runCatching {
            val ep = EntryPointAccessors.fromApplication(
                context.applicationContext,
                QuotaEntryPoint::class.java,
            )
            ep.quotaManager()
        }.getOrNull()
}

/**
 * 为 Composable 层提供 [PaywallAnalytics] 访问。
 */
object PaywallAnalyticsProvider {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AnalyticsEntryPoint {
        fun paywallAnalytics(): PaywallAnalytics
    }

    fun get(context: Context): PaywallAnalytics? =
        runCatching {
            val ep = EntryPointAccessors.fromApplication(
                context.applicationContext,
                AnalyticsEntryPoint::class.java,
            )
            ep.paywallAnalytics()
        }.getOrNull()
}

/**
 * 为非-ViewModel / object 层（如 VaultStore）提供 [AiAnalysisRepository] 访问。
 * 主要用于在删除 vault 照片同时同步清理 AI 分析表（phash/quality/tag/sensitive），
 * 避免 AI tab 卡片统计还挂着已删照片的孤儿记录。
 */
object AiAnalysisRepoProvider {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AiRepoEntryPoint {
        fun aiAnalysisRepository(): AiAnalysisRepository
    }

    fun get(context: Context): AiAnalysisRepository? =
        runCatching {
            val ep = EntryPointAccessors.fromApplication(
                context.applicationContext,
                AiRepoEntryPoint::class.java,
            )
            ep.aiAnalysisRepository()
        }.getOrNull()
}
