package com.tgmedia.tv.data.tdlib

import android.util.Base64
import org.drinkless.tdlib.TdApi
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Нормализация MTProto секрета в шестнадцатеричный (HEX) формат,
 * требуемый TDLib (TdApi.ProxyTypeMtproto). Поддерживает Base64/Base64URL и FakeTLS.
 */
fun normalizeMtprotoSecret(rawSecret: String): String {
    val clean = rawSecret.trim().removePrefix("secret=").trim()
    if (clean.isEmpty()) return clean

    // 1. Проверяем, является ли строка чистым HEX
    // Стандартные секреты MTProto в hex: 32 символа (MD5/AES), 34 символа (префикс dd для randomization),
    // или >= 34 символов (префикс ee для FakeTLS + SNI домен в hex).
    val isHexOnly = clean.all { c ->
        (c in '0'..'9') || (c in 'a'..'f') || (c in 'A'..'F')
    }
    if (isHexOnly && clean.length % 2 == 0 && clean.length >= 32) {
        return clean.lowercase()
    }

    // 2. Если это Base64 или Base64URL (часто встречается в ссылках tg://proxy, напр. 7gAAAAAAAAAAAAAAAAAAAAB3d3cuZ29vZ2xlLmNvbQ)
    try {
        val sanitizedB64 = clean.replace('-', '+').replace('_', '/')
        val padLength = (4 - (sanitizedB64.length % 4)) % 4
        val padded = sanitizedB64 + "=".repeat(padLength)
        val decodedBytes = Base64.decode(padded, Base64.DEFAULT)
        if (decodedBytes != null && decodedBytes.size >= 16) {
            val sb = StringBuilder(decodedBytes.size * 2)
            for (b in decodedBytes) {
                sb.append(String.format("%02x", b.toInt() and 0xFF))
            }
            val hexCandidate = sb.toString()
            // Проверяем, что декодированный результат имеет валидный формат MTProto
            if (hexCandidate.length >= 32) {
                return hexCandidate.lowercase()
            }
        }
    } catch (_: Exception) {
    }

    return clean.lowercase()
}

/**
 * Режим использования Proxy в соответствии с официальным клиентом Telegram:
 * 1. Отключить прокси (прямое соединение)
 * 2. Использовать системные настройки прокси
 * 3. Использовать собственный прокси
 */
enum class ProxyConnectionMode {
    DISABLED,
    SYSTEM,
    CUSTOM
}

/**
 * Поддерживаемые типы прокси-серверов в Telegram:
 * MTPROTO, SOCKS5, HTTP, WEB
 */
enum class ProxyItemType {
    MTPROTO,
    SOCKS5,
    HTTP,
    WEB;

    val displayName: String
        get() = name

    fun toTdLibProxyType(secret: String, username: String, password: String): TdApi.ProxyType {
        return when (this) {
            MTPROTO, WEB -> {
                val hexSecret = normalizeMtprotoSecret(secret)
                TdApi.ProxyTypeMtproto(hexSecret)
            }
            SOCKS5 -> TdApi.ProxyTypeSocks5(username, password)
            HTTP -> TdApi.ProxyTypeHttp(username, password, false)
        }
    }
}

/**
 * Статус проверки и доступности прокси
 */
enum class ProxyStatus {
    CHECKING,
    CONNECTED,
    AVAILABLE,
    UNAVAILABLE
}

/**
 * Модель отдельного прокси-сервера (из списка прокси Telegram)
 */
data class TelegramProxyItem(
    val id: String = UUID.randomUUID().toString(),
    val type: ProxyItemType = ProxyItemType.MTPROTO,
    val host: String = "",
    val port: Int = 443,
    val secret: String = "",
    val username: String = "",
    val password: String = "",
    val status: ProxyStatus = ProxyStatus.CHECKING,
    val pingMs: Long? = null,
    val tdlibProxyId: Int? = null,
    val comment: String = ""
) {
    val isValid: Boolean
        get() = host.isNotBlank() && port in 1..65535

    val displayAddress: String
        get() = "$host:$port"

    val statusText: String
        get() = when (status) {
            ProxyStatus.CONNECTED -> if (pingMs != null) "подключён ($pingMs мс)" else "подключён"
            ProxyStatus.AVAILABLE -> if (pingMs != null) "доступен ($pingMs мс)" else "доступен"
            ProxyStatus.UNAVAILABLE -> "недоступен"
            ProxyStatus.CHECKING -> "проверка..."
        }

    fun toTdProxy(): TdApi.Proxy {
        return TdApi.Proxy(host, port, type.toTdLibProxyType(secret, username, password))
    }

    fun toShareUrl(): String {
        return when (type) {
            ProxyItemType.MTPROTO, ProxyItemType.WEB -> "tg://proxy?server=$host&port=$port&secret=$secret"
            ProxyItemType.SOCKS5 -> "tg://socks?server=$host&port=$port" + (if (username.isNotEmpty()) "&user=$username" else "") + (if (password.isNotEmpty()) "&pass=$password" else "")
            ProxyItemType.HTTP -> "http://$host:$port"
        }
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("type", type.name)
            put("host", host)
            put("port", port)
            put("secret", secret)
            put("username", username)
            put("password", obfuscatePassword(password))
            put("status", status.name)
            if (pingMs != null) put("pingMs", pingMs)
            if (tdlibProxyId != null) put("tdlibProxyId", tdlibProxyId)
            put("comment", comment)
        }
    }

    companion object {
        private const val OBFUSCATION_PREFIX = "enc:"

        private fun obfuscatePassword(pwd: String): String {
            if (pwd.isEmpty()) return ""
            val salt = 0x5A.toByte()
            val bytes = pwd.toByteArray(Charsets.UTF_8).map { (it.toInt() xor salt.toInt()).toByte() }.toByteArray()
            return OBFUSCATION_PREFIX + Base64.encodeToString(bytes, Base64.NO_WRAP)
        }

        private fun deobfuscatePassword(raw: String): String {
            if (raw.isEmpty()) return ""
            if (!raw.startsWith(OBFUSCATION_PREFIX)) return raw
            return try {
                val b64 = raw.removePrefix(OBFUSCATION_PREFIX)
                val decoded = Base64.decode(b64, Base64.NO_WRAP)
                val salt = 0x5A.toByte()
                val plain = decoded.map { (it.toInt() xor salt.toInt()).toByte() }.toByteArray()
                String(plain, Charsets.UTF_8)
            } catch (_: Exception) {
                raw
            }
        }

        fun fromJson(json: JSONObject): TelegramProxyItem {
            val typeStr = json.optString("type", ProxyItemType.MTPROTO.name)
            val type = try {
                ProxyItemType.valueOf(typeStr)
            } catch (e: Exception) {
                ProxyItemType.MTPROTO
            }

            val statusStr = json.optString("status", ProxyStatus.CHECKING.name)
            val status = try {
                ProxyStatus.valueOf(statusStr)
            } catch (e: Exception) {
                ProxyStatus.CHECKING
            }

            val rawPassword = json.optString("password", "")
            val parsedPassword = deobfuscatePassword(rawPassword)

            return TelegramProxyItem(
                id = json.optString("id", UUID.randomUUID().toString()),
                type = type,
                host = json.optString("host", ""),
                port = json.optInt("port", 443),
                secret = json.optString("secret", ""),
                username = json.optString("username", ""),
                password = parsedPassword,
                status = status,
                pingMs = if (json.has("pingMs")) json.optLong("pingMs") else null,
                tdlibProxyId = if (json.has("tdlibProxyId")) json.optInt("tdlibProxyId") else null,
                comment = json.optString("comment", "")
            )
        }
    }
}

/**
 * Полная конфигурация настроек прокси согласно официальному клиенту Telegram.
 */
data class TelegramProxyConfig(
    val mode: ProxyConnectionMode = ProxyConnectionMode.DISABLED,
    val useIPv6: Boolean = false,
    val autoSwitch: Boolean = true,
    val autoSwitchDelaySec: Int = 10, // 5, 10, 15, 30, 60
    val selectedProxyId: String? = null,
    val proxies: List<TelegramProxyItem> = emptyList()
) {
    val activeProxy: TelegramProxyItem?
        get() = if (mode == ProxyConnectionMode.CUSTOM) {
            proxies.find { it.id == selectedProxyId } ?: proxies.firstOrNull()
        } else null

    fun toJson(): String {
        val root = JSONObject().apply {
            put("mode", mode.name)
            put("useIPv6", useIPv6)
            put("autoSwitch", autoSwitch)
            put("autoSwitchDelaySec", autoSwitchDelaySec)
            if (selectedProxyId != null) put("selectedProxyId", selectedProxyId)

            val array = JSONArray()
            for (proxy in proxies) {
                array.put(proxy.toJson())
            }
            put("proxies", array)
        }
        return root.toString()
    }

    companion object {
        val DEFAULT = TelegramProxyConfig(
            mode = ProxyConnectionMode.DISABLED,
            useIPv6 = false,
            autoSwitch = true,
            autoSwitchDelaySec = 10,
            selectedProxyId = null,
            proxies = emptyList()
        )

        fun fromJson(jsonStr: String): TelegramProxyConfig {
            return try {
                val root = JSONObject(jsonStr)
                val mode = try {
                    ProxyConnectionMode.valueOf(root.optString("mode", ProxyConnectionMode.DISABLED.name))
                } catch (e: Exception) {
                    ProxyConnectionMode.DISABLED
                }

                val useIPv6 = root.optBoolean("useIPv6", false)
                val autoSwitch = root.optBoolean("autoSwitch", true)
                val autoSwitchDelaySec = root.optInt("autoSwitchDelaySec", 10)
                val selectedProxyId = if (root.has("selectedProxyId")) root.optString("selectedProxyId") else null

                val proxyList = mutableListOf<TelegramProxyItem>()
                val array = root.optJSONArray("proxies")
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val itemObj = array.getJSONObject(i)
                        proxyList.add(TelegramProxyItem.fromJson(itemObj))
                    }
                }

                TelegramProxyConfig(
                    mode = mode,
                    useIPv6 = useIPv6,
                    autoSwitch = autoSwitch,
                    autoSwitchDelaySec = autoSwitchDelaySec,
                    selectedProxyId = selectedProxyId,
                    proxies = proxyList
                )
            } catch (e: Exception) {
                DEFAULT
            }
        }
    }
}

/**
 * Старый класс для обратной совместимости вызовов.
 */
data class ProxySettings(
    val enabled: Boolean = false,
    val server: String = "",
    val port: Int = 443,
    val type: ProxyType = ProxyType.MTPROTO,
    val secret: String = "",
    val username: String = "",
    val password: String = ""
) {
    enum class ProxyType {
        MTPROTO,
        SOCKS5,
        HTTP
    }

    val isValid: Boolean
        get() = server.isNotBlank() && port in 1..65535

    companion object {
        val DIRECT = ProxySettings(
            enabled = false,
            server = "",
            port = 443,
            type = ProxyType.MTPROTO
        )
    }
}
