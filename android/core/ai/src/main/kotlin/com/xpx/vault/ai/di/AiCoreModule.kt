package com.xpx.vault.ai.di

import com.xpx.vault.ai.core.AiFeatureRegistry
import com.xpx.vault.ai.core.ImageAnalyzer
import com.xpx.vault.ai.engine.local.LocalAlgoAnalyzer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * AI 模块的 Hilt 绑定。
 *
 * 各引擎通过 `@IntoSet` 贡献一个 [ImageAnalyzer]：
 *  - :core:ai 贡献 [LocalAlgoAnalyzer]（始终可用）
 *  - :core:ai-mlkit 在 app 被依赖时贡献 `MlKitAnalyzer`（GMS 不可用时 isReady=false，由上层过滤）
 *
 * [AiFeatureRegistry] 统一聚合 Set<ImageAnalyzer>，UI / Worker 只依赖 Registry，
 * 不感知具体引擎，也不感知模块装配顺序。
 */
@Module
@InstallIn(SingletonComponent::class)
object AiCoreModule {

    @Provides
    @Singleton
    fun provideAiFeatureRegistry(
        analyzers: Set<@JvmSuppressWildcards ImageAnalyzer>,
    ): AiFeatureRegistry = AiFeatureRegistry(analyzers.toList())

    @Provides
    @IntoSet
    @Singleton
    fun provideLocalAlgoAnalyzer(): ImageAnalyzer = LocalAlgoAnalyzer()
}
