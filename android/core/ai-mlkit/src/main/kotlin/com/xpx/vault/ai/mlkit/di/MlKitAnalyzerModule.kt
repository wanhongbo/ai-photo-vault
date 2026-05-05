package com.xpx.vault.ai.mlkit.di

import com.xpx.vault.ai.core.ImageAnalyzer
import com.xpx.vault.ai.mlkit.MlKitAnalyzer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * ML Kit 引擎的 Hilt 绑定：把 [MlKitAnalyzer] 贡献到 `Set<ImageAnalyzer>`，
 * 由 core 模块的 `AiCoreModule` 聚合进 `AiFeatureRegistry`。
 *
 * 该模块仅在 app 依赖了 :core:ai-mlkit 时才生效，完全符合"降级路径"约束。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MlKitAnalyzerModule {

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindMlKitAnalyzer(impl: MlKitAnalyzer): ImageAnalyzer
}
