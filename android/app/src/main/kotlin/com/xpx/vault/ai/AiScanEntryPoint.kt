package com.xpx.vault.ai

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 非 Compose / 非 Hilt 作用域下访问 [AiLocalScanUseCase] 的 Hilt EntryPoint。
 *
 * 典型用途：图片导入、拍照入库等回调场景里，业务代码位于 Composable / Activity 之外，
 * 又不便把 UseCase 向下注入到每个调用点，使用此 EntryPoint 可以直接从 Application Context
 * 取到 Singleton。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AiScanEntryPoint {
    fun aiLocalScanUseCase(): AiLocalScanUseCase

    companion object {
        fun from(context: Context): AiLocalScanUseCase =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                AiScanEntryPoint::class.java,
            ).aiLocalScanUseCase()
    }
}
