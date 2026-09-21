package com.tgmedia.tv.data.tdlib

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.UUID

/**
 * Парсер и модуль проверки MTProto прокси с сайта https://mtproto.ru/main.php.
 * Бесплатные прокси с периодическим обновлением.
 */
object MtprotoScraper {
    private const val TAG = "MtprotoScraper"
    private const val SOURCE_URL = "https://mtproto.ru/main.php"

    /**
     * Загрузка списка прокси с сайта mtproto.ru с автоматической проверкой доступности.
     */
    suspend fun fetchAndVerifyProxies(): List<TelegramProxyItem> = withContext(Dispatchers.IO) {
        val rawProxies = fetchRawProxies()
        Log.i(TAG, "Fetched ${rawProxies.size} raw proxies from mtproto.ru. Verifying availability...")

        // Параллельно проверяем доступность каждого прокси
        val deferredChecks = rawProxies.map { proxy ->
            async {
                val checkResult = verifyProxySocket(proxy.host, proxy.port)
                if (checkResult.first) {
                    proxy.copy(
                        status = ProxyStatus.AVAILABLE,
                        pingMs = checkResult.second
                    )
                } else {
                    proxy.copy(
                        status = ProxyStatus.UNAVAILABLE,
                        pingMs = null
                    )
                }
            }
        }

        val checkedProxies = deferredChecks.awaitAll()
        // Сортируем: сначала рабочие по возрастанию пинга, затем недоступные
        checkedProxies.sortedWith(
            compareBy(
                { it.status != ProxyStatus.AVAILABLE },
                { it.pingMs ?: Long.MAX_VALUE }
            )
        )
    }

    /**
     * Парсинг ссылок tg://proxy и tg://webproxy со страницы mtproto.ru.
     */
    private fun fetchRawProxies(): List<TelegramProxyItem> {
        val proxies = mutableListOf<TelegramProxyItem>()
        try {
            val url = URL(SOURCE_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            }

            val html = connection.inputStream.bufferedReader().use { it.readText() }

            // 1. Поиск tg://proxy?server=...&port=...&secret=...
            val tgProxyRegex = Regex("""tg://proxy\?server=([^&"'><\s]+)&port=(\d+)&secret=([a-zA-Z0-9]+)""", RegexOption.IGNORE_CASE)
            for (match in tgProxyRegex.findAll(html)) {
                val host = match.groupValues[1].trim()
                val port = match.groupValues[2].toIntOrNull() ?: 443
                val secret = match.groupValues[3].trim()
                if (host.isNotBlank() && secret.isNotBlank()) {
                    proxies.add(
                        TelegramProxyItem(
                            id = UUID.randomUUID().toString(),
                            type = ProxyItemType.MTPROTO,
                            host = host,
                            port = port,
                            secret = secret,
                            status = ProxyStatus.CHECKING
                        )
                    )
                }
            }

            // 2. Поиск tg://webproxy?server=...&secret=...
            val tgWebProxyRegex = Regex("""tg://webproxy\?server=([^&"'><\s]+)&secret=([a-zA-Z0-9]+)""", RegexOption.IGNORE_CASE)
            for (match in tgWebProxyRegex.findAll(html)) {
                val host = match.groupValues[1].trim()
                val secret = match.groupValues[2].trim()
                if (host.isNotBlank() && secret.isNotBlank()) {
                    proxies.add(
                        TelegramProxyItem(
                            id = UUID.randomUUID().toString(),
                            type = ProxyItemType.MTPROTO,
                            host = host,
                            port = 443,
                            secret = secret,
                            status = ProxyStatus.CHECKING
                        )
                    )
                }
            }

            // 3. Поиск https://t.me/proxy?server=...&port=...&secret=...
            val tmeProxyRegex = Regex("""https://t\.me/proxy\?server=([^&"'><\s]+)&port=(\d+)&secret=([a-zA-Z0-9]+)""", RegexOption.IGNORE_CASE)
            for (match in tmeProxyRegex.findAll(html)) {
                val host = match.groupValues[1].trim()
                val port = match.groupValues[2].toIntOrNull() ?: 443
                val secret = match.groupValues[3].trim()
                if (host.isNotBlank() && secret.isNotBlank()) {
                    proxies.add(
                        TelegramProxyItem(
                            id = UUID.randomUUID().toString(),
                            type = ProxyItemType.MTPROTO,
                            host = host,
                            port = port,
                            secret = secret,
                            status = ProxyStatus.CHECKING
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load proxies from $SOURCE_URL: ${e.message}", e)
        }

        return proxies.distinctBy { "${it.host.lowercase()}:${it.port}" }
    }

    /**
     * Проверка прямого TCP соединения с хостом и измерение времени отклика (пинг).
     */
    suspend fun verifyProxySocket(host: String, port: Int, timeoutMs: Int = 2500): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val latency = System.currentTimeMillis() - startTime
                Pair(true, latency)
            }
        } catch (e: Exception) {
            Pair(false, -1L)
        }
    }

    /**
     * Парсинг прокси из буфера обмена (tg://proxy, tg://socks?..., tg://webproxy?... или json/text).
     */
    fun parseFromText(text: String): TelegramProxyItem? {
        val trimmed = text.trim()

        // Формат: tg://proxy?server=...&port=...&secret=...
        val mtprotoMatch = Regex("""(?:tg://|https://t\.me/)proxy\?server=([^&]+)&port=(\d+)&secret=([a-zA-Z0-9]+)""", RegexOption.IGNORE_CASE).find(trimmed)
        if (mtprotoMatch != null) {
            return TelegramProxyItem(
                id = UUID.randomUUID().toString(),
                type = ProxyItemType.MTPROTO,
                host = mtprotoMatch.groupValues[1].trim(),
                port = mtprotoMatch.groupValues[2].toIntOrNull() ?: 443,
                secret = mtprotoMatch.groupValues[3].trim(),
                status = ProxyStatus.CHECKING
            )
        }

        // Формат: tg://socks?server=...&port=...&user=...&pass=...
        val socksMatch = Regex("""(?:tg://|https://t\.me/)socks\?server=([^&]+)&port=(\d+)(?:&user=([^&]*))?(?:&pass=([^&]*))?""", RegexOption.IGNORE_CASE).find(trimmed)
        if (socksMatch != null) {
            return TelegramProxyItem(
                id = UUID.randomUUID().toString(),
                type = ProxyItemType.SOCKS5,
                host = socksMatch.groupValues[1].trim(),
                port = socksMatch.groupValues[2].toIntOrNull() ?: 1080,
                username = socksMatch.groupValues[3].trim(),
                password = socksMatch.groupValues[4].trim(),
                status = ProxyStatus.CHECKING
            )
        }

        // Простой формат host:port:secret
        val colonParts = trimmed.split(":")
        if (colonParts.size >= 3) {
            val host = colonParts[0].trim()
            val port = colonParts[1].toIntOrNull()
            val secret = colonParts[2].trim()
            if (port != null && host.isNotBlank() && secret.isNotBlank()) {
                return TelegramProxyItem(
                    id = UUID.randomUUID().toString(),
                    type = ProxyItemType.MTPROTO,
                    host = host,
                    port = port,
                    secret = secret,
                    status = ProxyStatus.CHECKING
                )
            }
        }

        return null
    }
}
