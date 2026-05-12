package com.xpx.vault.billing

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 为非 @Inject 可达的 Composable 提供 [PaywallGatekeeper] 访问。
 * 通过 Hilt EntryPoint 从 ApplicationContext 获取 Singleton 实例。
 */
object PaywallGatekeeperProvider {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface GatekeeperEntryPoint {
        fun paywallGatekeeper(): PaywallGatekeeper
    }

    fun get(context: Context): PaywallGatekeeper? =
        runCatching {
            val appContext = context.applicationContext
            val ep = EntryPointAccessors.fromApplication(appContext, GatekeeperEntryPoint::class.java)
            ep.paywallGatekeeper()
        }.getOrNull()
}
