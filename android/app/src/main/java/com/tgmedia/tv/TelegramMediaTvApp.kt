package com.tgmedia.tv

import android.app.Application
import android.util.Log
import com.tgmedia.tv.data.tdlib.TdLibConfig
import com.tgmedia.tv.data.tdlib.TdLibManager
import java.io.File

class TelegramMediaTvApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i("TelegramMediaTvApp", "Initializing Telegram Media TV Application")

        // Защита от аварийных вылетов при сбоях сети/парсинга медиа
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("TelegramMediaTvApp", "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            // Логируем и предотвращаем внезапное падение UI если это не критическая ошибка VM
            if (throwable is OutOfMemoryError) {
                System.gc()
            }
            previousHandler?.uncaughtException(thread, throwable)
        }

        try {
            // Подготовка путей локальной базы SQLite и кэша видеофайлов
            val tdlibDbDir = File(filesDir, "tdlib_db").apply { mkdirs() }
            val tdlibFilesDir = File(cacheDir, "tdlib_media").apply { mkdirs() }

            // Инициализация менеджера TDLib с параметрами Android TV
            val config = TdLibConfig(
                apiId = 279933, // Из App configuration на my.telegram.org
                apiHash = "1c32273df61c79fc3568bceb0bfb73a9",
                databaseDirectory = tdlibDbDir.absolutePath,
                filesDirectory = tdlibFilesDir.absolutePath,
                deviceModel = "Android TV 10-Foot",
                systemVersion = android.os.Build.VERSION.RELEASE ?: "14",
                applicationVersion = BuildConfig.VERSION_NAME
            )

            TdLibManager.getInstance().initialize(this, config)
        } catch (t: Throwable) {
            Log.e("TelegramMediaTvApp", "Safe mode fallback: Error during TdLib init", t)
        }
    }
}
