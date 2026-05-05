package com.xpx.vault.data.di

import android.content.Context
import androidx.room.Room
import com.xpx.vault.data.ai.AiAnalysisRepositoryImpl
import com.xpx.vault.data.crypto.KeystoreSecretKeyProvider
import com.xpx.vault.data.crypto.VaultCipher
import com.xpx.vault.data.db.PhotoVaultDatabase
import com.xpx.vault.data.db.PhotoVaultMigrations
import com.xpx.vault.data.db.dao.AiPhashDao
import com.xpx.vault.data.db.dao.AiQualityDao
import com.xpx.vault.data.db.dao.AiSensitiveDao
import com.xpx.vault.data.db.dao.AiTagDao
import com.xpx.vault.domain.repo.AiAnalysisRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun providePhotoVaultDatabase(
        @ApplicationContext context: Context,
    ): PhotoVaultDatabase =
        Room.databaseBuilder(
            context,
            PhotoVaultDatabase::class.java,
            PhotoVaultDatabase.NAME,
        )
            .addMigrations(*PhotoVaultMigrations.ALL)
            .build()

    @Provides
    @Singleton
    fun provideKeystoreSecretKeyProvider(): KeystoreSecretKeyProvider =
        KeystoreSecretKeyProvider()

    @Provides
    @Singleton
    fun provideVaultCipher(
        @ApplicationContext context: Context,
    ): VaultCipher = VaultCipher.get(context)

    // ---- AI DAO providers ----
    @Provides @Singleton
    fun provideAiTagDao(db: PhotoVaultDatabase): AiTagDao = db.aiTagDao()

    @Provides @Singleton
    fun provideAiPhashDao(db: PhotoVaultDatabase): AiPhashDao = db.aiPhashDao()

    @Provides @Singleton
    fun provideAiQualityDao(db: PhotoVaultDatabase): AiQualityDao = db.aiQualityDao()

    @Provides @Singleton
    fun provideAiSensitiveDao(db: PhotoVaultDatabase): AiSensitiveDao = db.aiSensitiveDao()
}

/** 接口与实现绑定：@Binds 比 @Provides 更高效、代码量更少。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindsModule {
    @Binds
    @Singleton
    abstract fun bindAiAnalysisRepository(
        impl: AiAnalysisRepositoryImpl,
    ): AiAnalysisRepository
}
