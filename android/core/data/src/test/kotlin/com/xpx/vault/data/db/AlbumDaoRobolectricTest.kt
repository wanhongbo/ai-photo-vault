package com.xpx.vault.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.xpx.vault.data.db.dao.AlbumDao
import com.xpx.vault.data.db.entity.AlbumEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlbumDaoRobolectricTest {
    private lateinit var db: PhotoVaultDatabase
    private lateinit var albumDao: AlbumDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PhotoVaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        albumDao = db.albumDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAlbum_returnsRowId() = runBlocking {
        val id = albumDao.insert(
            AlbumEntity(
                name = "Default",
                coverPhotoId = null,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
            ),
        )
        assertThat(id).isGreaterThan(0L)
    }
}
