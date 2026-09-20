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
                applicationVersion = "1.0.17"
            )

            TdLibManager.getInstance().initialize(this, config)
        } catch (t: Throwable) {
            Log.e("TelegramMediaTvApp", "Safe mode fallback: Error during TdLib init", t)
        }
    }
}
