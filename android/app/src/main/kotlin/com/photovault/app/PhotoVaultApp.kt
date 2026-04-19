package com.photovault.app

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PhotoVaultApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.install()
        installGlobalExceptionBoundary()
    }

    private fun installGlobalExceptionBoundary() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogger.e(
                tag = "Uncaught",
                message = "thread=${thread.name} ${throwable.javaClass.simpleName}",
                throwable = throwable,
            )
            previous?.uncaughtException(thread, throwable)
        }
    }
}
