package com.xpx.vault.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.xpx.vault.data.db.dao.AlbumDao
import com.xpx.vault.data.db.dao.BackupRecordDao
import com.xpx.vault.data.db.dao.SecuritySettingDao
import com.xpx.vault.data.db.entity.AlbumEntity
import com.xpx.vault.data.db.entity.BackupRecordEntity
import com.xpx.vault.data.db.entity.PhotoAssetEntity
import com.xpx.vault.data.db.entity.SecuritySettingEntity
import com.xpx.vault.data.db.entity.SubscriptionStateEntity
import com.xpx.vault.data.db.entity.TrashItemEntity

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
    abstract fun backupRecordDao(): BackupRecordDao
    abstract fun securitySettingDao(): SecuritySettingDao

    companion object {
        /** 升级时在此注册 [androidx.room.migration.Migration]，并递增版本号。 */
        const val VERSION = 1
        const val NAME = "photo_vault.db"
    }
}
