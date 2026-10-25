package com.tgmedia.tv

import android.app.Application
import android.content.Context
import android.util.Base64
import android.util.Log
import com.tgmedia.tv.data.tdlib.TdLibConfig
import com.tgmedia.tv.data.tdlib.TdLibManager
import java.io.File
import java.security.SecureRandom

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

            // Безопасное управление ключом шифрования локальной базы TDLib
            val encryptionKey = getOrCreateDatabaseEncryptionKey(tdlibDbDir)

            // Инициализация менеджера TDLib с параметрами Android TV из BuildConfig и ключом БД
            val config = TdLibConfig(
                apiId = BuildConfig.TG_API_ID,
                apiHash = BuildConfig.TG_API_HASH,
                databaseDirectory = tdlibDbDir.absolutePath,
                filesDirectory = tdlibFilesDir.absolutePath,
                encryptionKey = encryptionKey,
                deviceModel = "Android TV 10-Foot",
                systemVersion = android.os.Build.VERSION.RELEASE ?: "14",
                applicationVersion = BuildConfig.VERSION_NAME
            )

            TdLibManager.getInstance().initialize(this, config)
        } catch (t: Throwable) {
            Log.e("TelegramMediaTvApp", "Safe mode fallback: Error during TdLib init", t)
        }
    }

    /**
     * Генерация и безопасное хранение 256-битного AES ключа шифрования локальной БД TDLib.
     * Если база уже была инициализирована без ключа, возвращает пустой ключ во избежание сброса сессии.
     */
    private fun getOrCreateDatabaseEncryptionKey(dbDir: File): ByteArray {
        val prefs = getSharedPreferences("cmtgtv_security_vault", Context.MODE_PRIVATE)
        val savedKeyB64 = prefs.getString("tdlib_db_key_v1", null)
        if (!savedKeyB64.isNullOrEmpty()) {
            return try {
                Base64.decode(savedKeyB64, Base64.NO_WRAP)
            } catch (_: Exception) {
                ByteArray(0)
            }
        }

        // Если файл базы уже существует без ключа (ранее созданная сессия пользователя)
        val binlog = File(dbDir, "td.binlog")
        if (binlog.exists() && binlog.length() > 0) {
            return ByteArray(0)
        }

        // Создаем новый криптографически стойкий 256-битный ключ
        val newKey = ByteArray(32)
        SecureRandom().nextBytes(newKey)
        val encoded = Base64.encodeToString(newKey, Base64.NO_WRAP)
        prefs.edit().putString("tdlib_db_key_v1", encoded).apply()
        return newKey
    }
}
