package com.photovault.data.di

import android.content.Context
import androidx.room.Room
import com.photovault.data.crypto.AesCbcEngine
import com.photovault.data.crypto.KeystoreSecretKeyProvider
import com.photovault.data.db.PhotoVaultDatabase
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
        ).build()

    @Provides
    @Singleton
    fun provideKeystoreSecretKeyProvider(): KeystoreSecretKeyProvider =
        KeystoreSecretKeyProvider()

    @Provides
    @Singleton
    fun provideAesCbcEngine(
        keystoreSecretKeyProvider: KeystoreSecretKeyProvider,
    ): AesCbcEngine = AesCbcEngine(keystoreSecretKeyProvider.getOrCreateAesSecretKey())
}
