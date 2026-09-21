package com.tgmedia.tv.data.tdlib

import org.drinkless.tdlib.TdApi
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

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
            MTPROTO, WEB -> TdApi.ProxyTypeMtproto(secret)
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
            put("password", password)
            put("status", status.name)
            if (pingMs != null) put("pingMs", pingMs)
            if (tdlibProxyId != null) put("tdlibProxyId", tdlibProxyId)
            put("comment", comment)
        }
    }

    companion object {
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

            return TelegramProxyItem(
                id = json.optString("id", UUID.randomUUID().toString()),
                type = type,
                host = json.optString("host", ""),
                port = json.optInt("port", 443),
                secret = json.optString("secret", ""),
                username = json.optString("username", ""),
                password = json.optString("password", ""),
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
