package com.tgmedia.tv.data.tdlib

/**
 * Конфигурация параметров для инициализации ядра TDLib на Android TV.
 */
data class TdLibConfig(
    val apiId: Int,
    val apiHash: String,
    val databaseDirectory: String,
    val filesDirectory: String,
    val encryptionKey: ByteArray = ByteArray(0),
    val useTestDc: Boolean = false,
    val systemLanguageCode: String = "ru",
    val deviceModel: String = "Android TV",
    val systemVersion: String = "Android 14 (API 34)",
    val applicationVersion: String = "1.0.0"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TdLibConfig
        if (apiId != other.apiId) return false
        if (apiHash != other.apiHash) return false
        if (databaseDirectory != other.databaseDirectory) return false
        if (filesDirectory != other.filesDirectory) return false
        if (!encryptionKey.contentEquals(other.encryptionKey)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = apiId
        result = 31 * result + apiHash.hashCode()
        result = 31 * result + databaseDirectory.hashCode()
        result = 31 * result + filesDirectory.hashCode()
        result = 31 * result + encryptionKey.contentHashCode()
        return result
    }
}
