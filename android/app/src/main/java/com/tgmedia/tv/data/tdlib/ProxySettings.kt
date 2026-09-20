package com.tgmedia.tv.data.tdlib

/**
 * Модель настроек Proxy для обхода сетевых ограничений и блокировок в РФ.
 * Поддерживает MTProto (нативный протокол Telegram) и SOCKS5.
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

