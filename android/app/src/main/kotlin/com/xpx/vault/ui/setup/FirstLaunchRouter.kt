package com.xpx.vault.ui.setup

import android.content.Context
import com.xpx.vault.data.db.PhotoVaultDatabase
import com.xpx.vault.ui.backup.ExternalBackupLocation

/**
 * 首启分叉路由：根据本机 SecuritySetting 与外部 backup.dat 判断应走哪条路径。
 *
 * - [Branch.Fresh]：从未设置 PIN 且外部无备份 → 走原有 SETUP_ENTER 新建流程。
 * - [Branch.RestoreLogin]：从未设置 PIN 但外部有 backup.dat → 走「输入密码 = 恢复凭证 + 本机新密码」分支。
 * - [Branch.Unlock]：已有 SecuritySetting → 走常规 UNLOCK 流程。
 */
object FirstLaunchRouter {
    sealed class Branch {
        object Fresh : Branch()
        object RestoreLogin : Branch()
        object Unlock : Branch()
    }

    suspend fun detect(context: Context, db: PhotoVaultDatabase): Branch {
        val setting = db.securitySettingDao().getById()
        if (setting != null) return Branch.Unlock
        val hasAutoPackage = ExternalBackupLocation.findAuto(context) != null
        return if (hasAutoPackage) Branch.RestoreLogin else Branch.Fresh
    }
}
