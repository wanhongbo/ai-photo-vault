package com.xpx.vault.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库迁移集合。每次 schema 变更请：
 *  1) 在 [PhotoVaultDatabase.VERSION] 递增；
 *  2) 在此定义迁移常量；
 *  3) 在 DataModule `Room.databaseBuilder().addMigrations(...)` 注册。
 *
 * v1 → v2：新增 AI 四张表（ai_tag / ai_phash / ai_quality / ai_sensitive），带 FK。
 * v2 → v3：AI 四张表去 FK（photoId 改为 path hash 生成，不再依赖 photo_assets）；
 *          因仅 v2 线上没有真实扫描数据写入，直接 DROP + CREATE 最稳妥。
 */
internal object PhotoVaultMigrations {
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_tag` (
                  `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                  `photo_id` INTEGER NOT NULL,
                  `label` TEXT NOT NULL,
                  `category` TEXT NOT NULL,
                  `confidence` REAL NOT NULL,
                  `source` TEXT NOT NULL,
                  `created_at_ms` INTEGER NOT NULL,
                  FOREIGN KEY(`photo_id`) REFERENCES `photo_assets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_tag_photo_id` ON `ai_tag` (`photo_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_tag_category` ON `ai_tag` (`category`)")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_tag_photo_id_label` ON `ai_tag` (`photo_id`, `label`)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_phash` (
                  `photo_id` INTEGER NOT NULL PRIMARY KEY,
                  `phash` INTEGER NOT NULL,
                  `dhash` INTEGER NOT NULL,
                  FOREIGN KEY(`photo_id`) REFERENCES `photo_assets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_quality` (
                  `photo_id` INTEGER NOT NULL PRIMARY KEY,
                  `sharpness` REAL NOT NULL,
                  `brightness` REAL NOT NULL,
                  `is_blurry` INTEGER NOT NULL,
                  `is_over_exposed` INTEGER NOT NULL,
                  `is_duplicate` INTEGER NOT NULL,
                  `duplicate_group_id` INTEGER,
                  FOREIGN KEY(`photo_id`) REFERENCES `photo_assets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_quality_is_blurry` ON `ai_quality` (`is_blurry`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_quality_is_duplicate` ON `ai_quality` (`is_duplicate`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ai_quality_duplicate_group_id` ON `ai_quality` (`duplicate_group_id`)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_sensitive` (
                  `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                  `photo_id` INTEGER NOT NULL,
                  `kind` TEXT NOT NULL,
                  `confidence` REAL NOT NULL,
                  `regions_json` TEXT,
                  `status` TEXT NOT NULL,
                  `created_at_ms` INTEGER NOT NULL,
                  FOREIGN KEY(`photo_id`) REFERENCES `photo_assets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_sensitive_photo_id_kind` ON `ai_sensitive` (`photo_id`, `kind`)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_sensitive_status` ON `ai_sensitive` (`status`)")
        }
    }

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 去掉 FK：直接 DROP 再 CREATE（v2 无真实扫描产出，不会丢失用户数据）。
            db.execSQL("DROP TABLE IF EXISTS `ai_tag`")
            db.execSQL("DROP TABLE IF EXISTS `ai_phash`")
            db.execSQL("DROP TABLE IF EXISTS `ai_quality`")
            db.execSQL("DROP TABLE IF EXISTS `ai_sensitive`")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_tag` (
                  `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                  `photo_id` INTEGER NOT NULL,
                  `label` TEXT NOT NULL,
                  `category` TEXT NOT NULL,
                  `confidence` REAL NOT NULL,
                  `source` TEXT NOT NULL,
                  `created_at_ms` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_tag_photo_id` ON `ai_tag` (`photo_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_tag_category` ON `ai_tag` (`category`)")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_tag_photo_id_label` ON `ai_tag` (`photo_id`, `label`)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_phash` (
                  `photo_id` INTEGER NOT NULL PRIMARY KEY,
                  `phash` INTEGER NOT NULL,
                  `dhash` INTEGER NOT NULL
                )
                """.trimIndent(),
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_quality` (
                  `photo_id` INTEGER NOT NULL PRIMARY KEY,
                  `sharpness` REAL NOT NULL,
                  `brightness` REAL NOT NULL,
                  `is_blurry` INTEGER NOT NULL,
                  `is_over_exposed` INTEGER NOT NULL,
                  `is_duplicate` INTEGER NOT NULL,
                  `duplicate_group_id` INTEGER
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_quality_is_blurry` ON `ai_quality` (`is_blurry`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_quality_is_duplicate` ON `ai_quality` (`is_duplicate`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ai_quality_duplicate_group_id` ON `ai_quality` (`duplicate_group_id`)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_sensitive` (
                  `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                  `photo_id` INTEGER NOT NULL,
                  `kind` TEXT NOT NULL,
                  `confidence` REAL NOT NULL,
                  `regions_json` TEXT,
                  `status` TEXT NOT NULL,
                  `created_at_ms` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_sensitive_photo_id_kind` ON `ai_sensitive` (`photo_id`, `kind`)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_sensitive_status` ON `ai_sensitive` (`status`)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
