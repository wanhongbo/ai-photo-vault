package com.photovault.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.photovault.data.db.dao.AlbumDao
import com.photovault.data.db.entity.AlbumEntity
import com.photovault.data.db.entity.BackupRecordEntity
import com.photovault.data.db.entity.PhotoAssetEntity
import com.photovault.data.db.entity.SecuritySettingEntity
import com.photovault.data.db.entity.SubscriptionStateEntity
import com.photovault.data.db.entity.TrashItemEntity

@Database(
    entities = [
        AlbumEntity::class,
        PhotoAssetEntity::class,
        TrashItemEntity::class,
        SecuritySettingEntity::class,
        SubscriptionStateEntity::class,
        BackupRecordEntity::class,
    ],
    version = PhotoVaultDatabase.VERSION,
    exportSchema = false,
)
abstract class PhotoVaultDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao

    companion object {
        /** 升级时在此注册 [androidx.room.migration.Migration]，并递增版本号。 */
        const val VERSION = 1
        const val NAME = "photo_vault.db"
    }
}
