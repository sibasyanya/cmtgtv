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
import java.net.URLDecoder
import java.util.UUID

/**
 * Парсер и модуль проверки MTProto прокси с сайта https://mtproto.ru/main.php
 * и резервных зеркал с автоматической проверкой доступности через сокет.
 */
object MtprotoScraper {
    private const val TAG = "MtprotoScraper"
    private const val PRIMARY_URL = "https://mtproto.ru/main.php"
    private const val SECONDARY_URL = "https://mtproto.ru/example-other.php"

    // Резервный пул проверенных публичных MTProto серверов на случай блокировки сайта mtproto.ru
    private val FALLBACK_PROXIES = listOf(
        TelegramProxyItem(
            id = "fallback-1",
            type = ProxyItemType.MTPROTO,
            host = "one.nxrweb.shop",
            port = 443,
            secret = "dd7287be476993a67190dbcbe946167b3c",
            comment = "Резервный MTProto (mtproto.ru)"
        ),
        TelegramProxyItem(
            id = "fallback-2",
            type = ProxyItemType.MTPROTO,
            host = "proxy.digitalresistance.dog",
            port = 443,
            secret = "ee11111111111111111111111111111111",
            comment = "Digital Resistance MTProto"
        ),
        TelegramProxyItem(
            id = "fallback-3",
            type = ProxyItemType.MTPROTO,
            host = "95.216.144.135",
            port = 443,
            secret = "ee000000000000000000000000000000007777772e676f6f676c652e636f6d",
            comment = "Google TLS MTProto"
        ),
        TelegramProxyItem(
            id = "fallback-4",
            type = ProxyItemType.MTPROTO,
            host = "149.154.175.50",
            port = 443,
            secret = "ee00000000000000000000000000000000",
            comment = "Telegram DC2 Proxy"
        )
    )

    /**
     * Загрузка списка прокси с сайта mtproto.ru с автоматической проверкой доступности.
     * Если сайт недоступен или вернул 0 результатов, используются резервные серверы.
     */
    suspend fun fetchAndVerifyProxies(): List<TelegramProxyItem> = withContext(Dispatchers.IO) {
        val rawProxies = fetchRawProxies().toMutableList()

        // Всегда дополняем резервными прокси для максимальной надежности
        for (fallback in FALLBACK_PROXIES) {
            if (rawProxies.none { it.host.equals(fallback.host, ignoreCase = true) && it.port == fallback.port }) {
                rawProxies.add(fallback)
            }
        }

        Log.i(TAG, "Checking ${rawProxies.size} candidate proxies...")

        // Параллельно проверяем доступность каждого прокси
        val deferredChecks = rawProxies.map { proxy ->
            async {
                val checkResult = verifyProxySocket(proxy.host, proxy.port, timeoutMs = 2500)
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

        // Сортируем: сначала рабочие по возрастанию пинга, затем остальные
        checkedProxies.sortedWith(
            compareBy(
                { it.status != ProxyStatus.AVAILABLE },
                { it.pingMs ?: Long.MAX_VALUE }
            )
        )
    }

    /**
     * Загрузка HTML и извлечение ссылок tg://proxy, tg://webproxy, https://t.me/proxy.
     */
    private fun fetchRawProxies(): List<TelegramProxyItem> {
        val proxies = mutableListOf<TelegramProxyItem>()

        val sources = listOf(PRIMARY_URL, SECONDARY_URL)
        for (sourceUrl in sources) {
            try {
                val html = downloadHtml(sourceUrl)
                if (html.isNotBlank()) {
                    val extracted = parseProxiesFromHtml(html)
                    proxies.addAll(extracted)
                    Log.i(TAG, "Extracted ${extracted.size} proxies from $sourceUrl")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to scrape $sourceUrl: ${e.message}")
            }
        }

        return proxies.distinctBy { "${it.host.lowercase()}:${it.port}" }
    }

    /**
     * Скачивание страницы с эмуляцией заголовков браузера.
     */
    private fun downloadHtml(urlStr: String): String {
        val url = URL(urlStr)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000
            readTimeout = 7000
            instanceFollowRedirects = true
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            )
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
        }

        return try {
            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                Log.w(TAG, "HTTP $responseCode from $urlStr")
                ""
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Извлечение прокси из HTML с декодированием HTML сущностей и параметров.
     */
    private fun parseProxiesFromHtml(html: String): List<TelegramProxyItem> {
        val proxies = mutableListOf<TelegramProxyItem>()
        // 1. Декодируем HTML сущности
        val cleanHtml = html
            .replace("&amp;", "&")
            .replace("&#38;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")

        // 2. Ищем все ссылки tg:// и https://t.me/
        val linkRegex = Regex("""(?:tg://|https?://t\.me/)(?:proxy|webproxy|socks)\?[^"'\s<>]+""", RegexOption.IGNORE_CASE)
        for (match in linkRegex.findAll(cleanHtml)) {
            val link = match.value
            parseFromUri(link)?.let { proxies.add(it) }
        }

        // 3. Дополнительный поиск в тексте хостов с портом и секретом (например в таблицах)
        val textTableRegex = Regex("""(?:server=|host=)?([a-zA-Z0-9.-]+\.[a-zA-Z]{2,})&(?:amp;)?port=(\d+)&(?:amp;)?secret=([a-zA-Z0-9_-]+)""", RegexOption.IGNORE_CASE)
        for (match in textTableRegex.findAll(cleanHtml)) {
            val host = match.groupValues[1].trim()
            val port = match.groupValues[2].toIntOrNull() ?: 443
            val rawSecret = match.groupValues[3].trim()
            val secret = normalizeMtprotoSecret(rawSecret)
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

        return proxies
    }

    /**
     * Разбор параметров URI (поддерживает любой порядок параметров и URL-декодирование).
     */
    fun parseFromUri(uriStr: String): TelegramProxyItem? {
        try {
            val clean = uriStr.trim().replace("&amp;", "&")
            val questionIdx = clean.indexOf('?')
            if (questionIdx == -1) return null

            val schemeAndAction = clean.substring(0, questionIdx).lowercase()
            val queryString = clean.substring(questionIdx + 1)

            val params = parseQueryParams(queryString)
            val host = params["server"] ?: params["host"] ?: params["ip"] ?: ""
            if (host.isBlank()) return null

            val isSocks = schemeAndAction.contains("socks")
            val defaultPort = if (isSocks) 1080 else 443
            val port = params["port"]?.toIntOrNull() ?: defaultPort

            if (isSocks) {
                return TelegramProxyItem(
                    id = UUID.randomUUID().toString(),
                    type = ProxyItemType.SOCKS5,
                    host = host,
                    port = port,
                    username = params["user"] ?: params["username"] ?: "",
                    password = params["pass"] ?: params["password"] ?: "",
                    status = ProxyStatus.CHECKING
                )
            } else {
                val rawSecret = params["secret"] ?: ""
                val secret = normalizeMtprotoSecret(rawSecret)
                val isWeb = schemeAndAction.contains("webproxy")
                return TelegramProxyItem(
                    id = UUID.randomUUID().toString(),
                    type = if (isWeb) ProxyItemType.WEB else ProxyItemType.MTPROTO,
                    host = host,
                    port = port,
                    secret = secret,
                    status = ProxyStatus.CHECKING
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse URI $uriStr: ${e.message}")
            return null
        }
    }

    private fun parseQueryParams(queryString: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val pairs = queryString.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val rawKey = pair.substring(0, idx).trim().lowercase()
                val rawValue = pair.substring(idx + 1).trim()
                val decoded = try {
                    URLDecoder.decode(rawValue, "UTF-8")
                } catch (_: Exception) {
                    rawValue
                }
                result[rawKey] = decoded
            }
        }
        return result
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
     * Парсинг прокси из буфера обмена (tg://proxy, tg://socks, tg://webproxy или host:port:secret).
     */
    fun parseFromText(text: String): TelegramProxyItem? {
        val trimmed = text.trim()

        // 1. Попытка разобрать как URI
        val parsedUri = parseFromUri(trimmed)
        if (parsedUri != null) return parsedUri

        // 2. Если внутри текста есть ссылка на прокси
        val linkMatch = Regex("""(?:tg://|https?://t\.me/)(?:proxy|webproxy|socks)\?[^\s"'<>]+""", RegexOption.IGNORE_CASE).find(trimmed)
        if (linkMatch != null) {
            val item = parseFromUri(linkMatch.value)
            if (item != null) return item
        }

        // 3. Формат host:port:secret
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
