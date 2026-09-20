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

    companion object {
        // Популярный публичный MTProto прокси
        val PRESET_MTPROTO_1 = ProxySettings(
            enabled = true,
            server = "proxy.digitalresistance.dog",
            port = 443,
            type = ProxyType.MTPROTO,
            secret = "d41d8cd98f00b204e9800998ecf8427e"
        )

        // Альтернативный MTProto прокси
        val PRESET_MTPROTO_2 = ProxySettings(
            enabled = true,
            server = "149.154.175.50",
            port = 443,
            type = ProxyType.MTPROTO,
            secret = "ee000000000000000000000000000000007777772e676f6f676c652e636f6d"
        )

        // Локальный SOCKS5 (роутер, домашний VPN/V2Ray/Shadowsocks на 1080)
        val PRESET_LOCAL_SOCKS5 = ProxySettings(
            enabled = true,
            server = "127.0.0.1",
            port = 1080,
            type = ProxyType.SOCKS5,
            username = "",
            password = ""
        )
    }
}
