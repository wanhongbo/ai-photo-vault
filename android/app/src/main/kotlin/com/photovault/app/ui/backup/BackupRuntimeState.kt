package com.photovault.app.ui.backup

object BackupRuntimeState {
    @Volatile
    var lastBackupResult: BackupExecutionResult? = null

    @Volatile
    var lastRestoreResult: RestoreExecutionResult? = null
}
