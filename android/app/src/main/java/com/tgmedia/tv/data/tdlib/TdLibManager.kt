package com.tgmedia.tv.data.tdlib

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Реактивный менеджер TDLib для Android TV.
 * Поддерживает авторизацию исключительно по номеру телефона (SMS / код Telegram / 2FA пароль)
 * и полноценную систему управления прокси (MTProto / SOCKS5 / HTTP / WEB)
 * в точности как в официальном клиенте Telegram.
 */
class TdLibManager private constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Состояние авторизации
    private val _authorizationState = MutableStateFlow<TdApi.AuthorizationState>(
        TdApi.AuthorizationStateWaitTdlibParameters()
    )
    val authorizationState: StateFlow<TdApi.AuthorizationState> = _authorizationState.asStateFlow()

    // Состояние сетевого подключения Telegram
    private val _connectionState = MutableStateFlow<TdApi.ConnectionState?>(null)
    val connectionState: StateFlow<TdApi.ConnectionState?> = _connectionState.asStateFlow()

    // Последнее сообщение об ошибке
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // Конфигурация прокси (в точности по официальному клиенту Telegram)
    private val _telegramProxyConfig = MutableStateFlow(TelegramProxyConfig.DEFAULT)
    val telegramProxyConfig: StateFlow<TelegramProxyConfig> = _telegramProxyConfig.asStateFlow()

    // Поток входящих обновлений данных
    private val _updates = MutableSharedFlow<TdApi.Update>(extraBufferCapacity = 128)
    val updates: SharedFlow<TdApi.Update> = _updates.asSharedFlow()

    private var client: Client? = null
    private var config: TdLibConfig? = null
    private var appContext: Context? = null

    // ID активного прокси в TDLib
    private var activeTdlibProxyId: Int? = null

    // Флаги перезапуска сессии
    @Volatile
    private var isRestartingSession: Boolean = false
    @Volatile
    private var pendingClearDatabase: Boolean = false

    // Задача автопереключения прокси
    private var autoSwitchJob: Job? = null

    companion object {
        private const val TAG = "TdLibManager"
        private const val PREFS_NAME = "tgmedia_tv_prefs"
        private const val PREF_PROXY_CONFIG_JSON = "telegram_proxy_config_json"

        @Volatile
        private var instance: TdLibManager? = null

        private var isNativeLibraryLoaded = false

        fun getInstance(): TdLibManager {
            return instance ?: synchronized(this) {
                instance ?: TdLibManager().also { instance = it }
            }
        }

        init {
            try {
                System.loadLibrary("tdjni")
                isNativeLibraryLoaded = true
                Log.i(TAG, "Native C++ library libtdjni.so successfully loaded.")

                Client.setLogMessageHandler(3) { verbosityLevel, message ->
                    Log.d("TDLibNative", "[$verbosityLevel] $message")
                }
                try {
                    Client.execute(TdApi.SetLogVerbosityLevel(3))
                } catch (t: Throwable) {
                    Log.w(TAG, "SetLogVerbosityLevel warning", t)
                }
            } catch (e: Throwable) {
                isNativeLibraryLoaded = false
                Log.w(TAG, "Native library libtdjni.so is not available. Running in Safe mode.", e)
            }
        }
    }

    val isNativeLoaded: Boolean get() = isNativeLibraryLoaded

    /**
     * Инициализация менеджера TDLib с контекстом приложения.
     */
    fun initialize(context: Context, config: TdLibConfig) {
        this.appContext = context.applicationContext
        _telegramProxyConfig.value = loadSavedProxyConfig()
        initialize(config)
        startAutoSwitchMonitor()
    }

    /**
     * Инициализация или запуск клиента TDLib.
     */
    fun initialize(config: TdLibConfig) {
        this.config = config

        if (!isNativeLibraryLoaded) {
            Log.w(TAG, "Native library not loaded. Running in Safe mode.")
            return
        }

        if (client != null) {
            Log.w(TAG, "TDLib client already running.")
            return
        }

        try {
            client = Client.create(
                { event -> handleIncomingEvent(event) },
                { error -> Log.e(TAG, "TDLib update exception", error) },
                { error -> Log.e(TAG, "TDLib default exception", error) }
            )

            updateNetworkType()

            // Применяем сохраненный режим прокси
            applyCurrentProxyConfigToTdLib()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to create Client due to native error: ${e.message}", e)
            _lastError.value = "Ошибка запуска TDLib: ${e.message}"
        }
    }

    /**
     * Уведомление TDLib об активном сетевом соединении (Wi-Fi/Ethernet).
     */
    fun updateNetworkType() {
        val networkType: TdApi.NetworkType = appContext?.let { ctx ->
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNet = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(activeNet)
            when {
                caps == null -> TdApi.NetworkTypeOther()
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> TdApi.NetworkTypeWiFi()
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> TdApi.NetworkTypeOther()
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> TdApi.NetworkTypeMobile()
                else -> TdApi.NetworkTypeOther()
            }
        } ?: TdApi.NetworkTypeOther()

        send(TdApi.SetNetworkType(networkType)) { result ->
            if (result is TdApi.Error) {
                Log.w(TAG, "SetNetworkType error: ${result.message}")
            } else {
                Log.d(TAG, "SetNetworkType confirmed ($networkType).")
            }
        }
    }

    /**
     * Обработка событий от нативного C++ ядра.
     */
    private fun handleIncomingEvent(event: TdApi.Object) {
        when (event) {
            is TdApi.UpdateConnectionState -> {
                val newState = event.state
                Log.i(TAG, "ConnectionState: ${newState.javaClass.simpleName}")
                _connectionState.value = newState
            }
            is TdApi.UpdateAuthorizationState -> {
                val newState = event.authorizationState
                Log.i(TAG, "AuthorizationState: ${newState.javaClass.simpleName}")
                _authorizationState.value = newState

                when (newState) {
                    is TdApi.AuthorizationStateWaitTdlibParameters -> {
                        sendTdlibParameters()
                    }
                    is TdApi.AuthorizationStateWaitPhoneNumber -> {
                        // Ожидаем ввод номера пользователем (без QR-кода!)
                        Log.i(TAG, "TDLib is ready and waiting for phone number input.")
                        _lastError.value = null
                    }
                    is TdApi.AuthorizationStateWaitCode -> {
                        Log.i(TAG, "AuthorizationStateWaitCode: code sent to Telegram or SMS.")
                        _lastError.value = null
                    }
                    is TdApi.AuthorizationStateWaitPassword -> {
                        Log.i(TAG, "AuthorizationStateWaitPassword: 2FA cloud password required.")
                        _lastError.value = null
                    }
                    is TdApi.AuthorizationStateReady -> {
                        Log.i(TAG, "AuthorizationStateReady: User logged in successfully!")
                        _lastError.value = null
                    }
                    is TdApi.AuthorizationStateClosed -> {
                        Log.i(TAG, "TDLib Client session closed.")
                        client = null
                        if (isRestartingSession) {
                            scope.launch(Dispatchers.IO) {
                                delay(300)
                                if (pendingClearDatabase) {
                                    clearDatabaseFiles()
                                    pendingClearDatabase = false
                                }
                                withContext(Dispatchers.Main) {
                                    isRestartingSession = false
                                    val currentConfig = config
                                    if (currentConfig != null) {
                                        initialize(currentConfig)
                                    }
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            }
            is TdApi.Update -> {
                scope.launch {
                    _updates.emit(event)
                }
            }
            else -> Unit
        }
    }

    /**
     * Отправка системных параметров TDLib.
     */
    fun sendTdlibParameters() {
        val currentConfig = config ?: run {
            Log.e(TAG, "Cannot set TdlibParameters: config is null")
            _lastError.value = "Конфигурация приложения не задана"
            return
        }

        updateNetworkType()

        val request = TdApi.SetTdlibParameters(
            currentConfig.useTestDc,
            currentConfig.databaseDirectory,
            currentConfig.filesDirectory,
            currentConfig.encryptionKey,
            true, // useFileDatabase
            true, // useChatInfoDatabase
            true, // useMessageDatabase
            false, // useSecretChats
            currentConfig.apiId,
            currentConfig.apiHash,
            currentConfig.systemLanguageCode,
            currentConfig.deviceModel,
            currentConfig.systemVersion,
            currentConfig.applicationVersion
        )

        send(request) { result ->
            if (result is TdApi.Error) {
                Log.e(TAG, "SetTdlibParameters failed: ${result.message} (code: ${result.code})")
                _lastError.value = "Ошибка параметров TDLib: [${result.code}] ${result.message}"
            } else {
                Log.i(TAG, "SetTdlibParameters accepted by TDLib.")
                _lastError.value = null
                updateNetworkType()
            }
        }
    }

    // ============================================================================
    // Авторизация по номеру телефона (Phone Number Auth)
    // ============================================================================

    /**
     * Отправка номера телефона в Telegram.
     */
    fun sendAuthenticationPhoneNumber(phoneNumber: String, onResult: (Boolean, String?) -> Unit) {
        val trimmed = phoneNumber.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
        val formatted = if (trimmed.startsWith("+")) trimmed else "+$trimmed"

        Log.i(TAG, "Sending phone number: $formatted")
        _lastError.value = null
        updateNetworkType()

        val activeClient = client
        if (activeClient == null) {
            val msg = "Клиент TDLib не запущен. Пожалуйста, перезапустите сессию."
            _lastError.value = msg
            onResult(false, msg)
            return
        }

        if (_authorizationState.value is TdApi.AuthorizationStateWaitTdlibParameters) {
            sendTdlibParameters()
        }

        send(TdApi.SetAuthenticationPhoneNumber(formatted, null)) { result ->
            if (result is TdApi.Error) {
                Log.e(TAG, "SetAuthenticationPhoneNumber error: ${result.message} (${result.code})")
                val friendly = when (result.code) {
                    400 -> when {
                        result.message.contains("PHONE_NUMBER_INVALID", ignoreCase = true) ->
                            "Неверный номер телефона. Укажите в международном формате с плюсом (например, +79120517638)."
                        result.message.contains("PHONE_NUMBER_BANNED", ignoreCase = true) ->
                            "Этот номер телефона заблокирован в Telegram."
                        result.message.contains("Another authorization", ignoreCase = true) ->
                            "Запрос авторизации уже выполняется. Ожидайте..."
                        else -> "Ошибка [${result.code}]: ${result.message}"
                    }
                    429 -> "Слишком много попыток (FLOOD_WAIT). Telegram временно ограничил запросы с этого IP/номера. Попробуйте через 5 минут или смените Proxy."
                    else -> "Ошибка отправки номера: [${result.code}] ${result.message}"
                }
                _lastError.value = friendly
                onResult(false, friendly)
            } else {
                Log.i(TAG, "Phone number accepted. Telegram code was sent.")
                _lastError.value = null
                onResult(true, null)
            }
        }
    }

    /**
     * Проверка 5-значного кода подтверждения.
     */
    fun checkAuthenticationCode(code: String, onResult: (Boolean, String?) -> Unit) {
        val trimmed = code.trim().replace(" ", "")
        Log.i(TAG, "Checking auth code: $trimmed")
        _lastError.value = null

        val activeClient = client
        if (activeClient == null) {
            onResult(false, "Клиент TDLib не запущен")
            return
        }

        send(TdApi.CheckAuthenticationCode(trimmed)) { result ->
            if (result is TdApi.Error) {
                Log.e(TAG, "CheckAuthenticationCode error: ${result.message} (${result.code})")
                val friendly = when (result.code) {
                    400 -> when {
                        result.message.contains("PHONE_CODE_INVALID", ignoreCase = true) -> "Неверный код подтверждения. Проверьте цифры."
                        result.message.contains("PHONE_CODE_EXPIRED", ignoreCase = true) -> "Срок действия кода истек. Запросите код заново."
                        else -> "Ошибка [${result.code}]: ${result.message}"
                    }
                    else -> "Ошибка проверки кода: [${result.code}] ${result.message}"
                }
                _lastError.value = friendly
                onResult(false, friendly)
            } else {
                Log.i(TAG, "Auth code accepted successfully.")
                _lastError.value = null
                onResult(true, null)
            }
        }
    }

    /**
     * Проверка пароля двухфакторной аутентификации (2FA).
     */
    fun checkAuthenticationPassword(password: String, onResult: (Boolean, String?) -> Unit) {
        Log.i(TAG, "Checking 2FA password...")
        _lastError.value = null

        val activeClient = client
        if (activeClient == null) {
            onResult(false, "Клиент TDLib не запущен")
            return
        }

        send(TdApi.CheckAuthenticationPassword(password.trim())) { result ->
            if (result is TdApi.Error) {
                Log.e(TAG, "CheckAuthenticationPassword error: ${result.message} (${result.code})")
                val friendly = when {
                    result.message.contains("PASSWORD_HASH_INVALID", ignoreCase = true) -> "Неверный облачный пароль 2FA."
                    else -> "Ошибка 2FA: [${result.code}] ${result.message}"
                }
                _lastError.value = friendly
                onResult(false, friendly)
            } else {
                Log.i(TAG, "2FA password accepted successfully.")
                _lastError.value = null
                onResult(true, null)
            }
        }
    }

    /**
     * Повторный запрос кода подтверждения.
     */
    fun resendAuthenticationCode(onResult: (Boolean, String?) -> Unit) {
        send(TdApi.ResendAuthenticationCode()) { result ->
            if (result is TdApi.Error) {
                onResult(false, "Ошибка повторной отправки: [${result.code}] ${result.message}")
            } else {
                onResult(true, null)
            }
        }
    }

    // ============================================================================
    // Управление Proxy по стандарту официального клиента Telegram
    // ============================================================================

    /**
     * Смена режима прокси (ОТКЛЮЧИТЬ / СИСТЕМНЫЕ / СОБСТВЕННЫЙ).
     */
    fun setProxyConnectionMode(mode: ProxyConnectionMode) {
        val current = _telegramProxyConfig.value
        val updated = current.copy(mode = mode)
        updateAndSaveProxyConfig(updated)
        applyCurrentProxyConfigToTdLib()
    }

    /**
     * Переключение опции IPv6.
     */
    fun setUseIPv6(useIPv6: Boolean) {
        val current = _telegramProxyConfig.value
        val updated = current.copy(useIPv6 = useIPv6)
        updateAndSaveProxyConfig(updated)
    }

    /**
     * Настройка автопереключения прокси и интервала ожидания.
     */
    fun setAutoSwitch(autoSwitch: Boolean, delaySec: Int) {
        val current = _telegramProxyConfig.value
        val updated = current.copy(
            autoSwitch = autoSwitch,
            autoSwitchDelaySec = delaySec
        )
        updateAndSaveProxyConfig(updated)
    }

    /**
     * Выбор активного собственного прокси из списка.
     */
    fun selectProxy(proxyId: String) {
        val current = _telegramProxyConfig.value
        val updated = current.copy(
            mode = ProxyConnectionMode.CUSTOM,
            selectedProxyId = proxyId
        )
        updateAndSaveProxyConfig(updated)
        applyCurrentProxyConfigToTdLib()
    }

    /**
     * Добавление или обновление прокси в списке с опциональной предварительной проверкой.
     */
    fun addOrUpdateProxy(
        item: TelegramProxyItem,
        testFirst: Boolean = true,
        onComplete: ((Boolean, TelegramProxyItem) -> Unit)? = null
    ) {
        scope.launch {
            var testedItem = item
            if (testFirst) {
                val pingRes = MtprotoScraper.verifyProxySocket(item.host, item.port)
                testedItem = if (pingRes.first) {
                    item.copy(status = ProxyStatus.AVAILABLE, pingMs = pingRes.second)
                } else {
                    item.copy(status = ProxyStatus.UNAVAILABLE, pingMs = null)
                }
            }

            val current = _telegramProxyConfig.value
            val existingIndex = current.proxies.indexOfFirst { it.id == item.id }
            val newProxies = current.proxies.toMutableList()

            if (existingIndex >= 0) {
                newProxies[existingIndex] = testedItem
            } else {
                newProxies.add(testedItem)
            }

            // Если список был пуст или выбран этот прокси, выбираем его
            val selectedId = current.selectedProxyId ?: testedItem.id
            val updatedConfig = current.copy(
                proxies = newProxies,
                selectedProxyId = selectedId
            )

            updateAndSaveProxyConfig(updatedConfig)

            if (updatedConfig.mode == ProxyConnectionMode.CUSTOM && updatedConfig.selectedProxyId == testedItem.id) {
                applyCurrentProxyConfigToTdLib()
            }

            withContext(Dispatchers.Main) {
                onComplete?.invoke(testedItem.status == ProxyStatus.AVAILABLE, testedItem)
            }
        }
    }

    /**
     * Удаление прокси из списка.
     */
    fun removeProxy(proxyId: String) {
        val current = _telegramProxyConfig.value
        val itemToRemove = current.proxies.find { it.id == proxyId }
        val newProxies = current.proxies.filter { it.id != proxyId }

        itemToRemove?.tdlibProxyId?.let { id ->
            send(TdApi.RemoveProxy(id), null)
        }

        val newSelectedId = if (current.selectedProxyId == proxyId) {
            newProxies.firstOrNull()?.id
        } else {
            current.selectedProxyId
        }

        val updated = current.copy(
            proxies = newProxies,
            selectedProxyId = newSelectedId
        )
        updateAndSaveProxyConfig(updated)

        if (current.selectedProxyId == proxyId) {
            applyCurrentProxyConfigToTdLib()
        }
    }

    /**
     * Удаление всех прокси из списка.
     */
    fun removeAllProxies() {
        val current = _telegramProxyConfig.value
        for (proxy in current.proxies) {
            proxy.tdlibProxyId?.let { id ->
                send(TdApi.RemoveProxy(id), null)
            }
        }

        val updated = current.copy(
            selectedProxyId = null,
            proxies = emptyList()
        )
        updateAndSaveProxyConfig(updated)
        applyCurrentProxyConfigToTdLib()
    }

    /**
     * Проверка работоспособности конкретного прокси (сокет + пинг в мс).
     */
    fun testProxy(proxyId: String, onResult: (Boolean, Long?, String?) -> Unit) {
        val current = _telegramProxyConfig.value
        val item = current.proxies.find { it.id == proxyId }
        if (item == null) {
            onResult(false, null, "Прокси не найден в списке")
            return
        }

        // Ставим статус проверки
        updateProxyStatus(proxyId, ProxyStatus.CHECKING, null)

        scope.launch {
            val checkResult = MtprotoScraper.verifyProxySocket(item.host, item.port, timeoutMs = 3000)
            val isSuccess = checkResult.first
            val latency = if (isSuccess) checkResult.second else null
            val newStatus = if (isSuccess) {
                if (current.mode == ProxyConnectionMode.CUSTOM && current.selectedProxyId == proxyId) {
                    ProxyStatus.CONNECTED
                } else {
                    ProxyStatus.AVAILABLE
                }
            } else {
                ProxyStatus.UNAVAILABLE
            }

            updateProxyStatus(proxyId, newStatus, latency)

            withContext(Dispatchers.Main) {
                onResult(isSuccess, latency, if (isSuccess) null else "Таймаут соединения")
            }
        }
    }

    /**
     * Загрузка свежих MTProto прокси с сайта https://mtproto.ru/main.php.
     */
    fun loadProxiesFromMtprotoRu(onResult: (Int, String?) -> Unit) {
        scope.launch {
            try {
                val scraped = MtprotoScraper.fetchAndVerifyProxies()
                if (scraped.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onResult(0, "Не удалось найти прокси на mtproto.ru")
                    }
                    return@launch
                }

                val current = _telegramProxyConfig.value
                val existingHosts = current.proxies.map { "${it.host.lowercase()}:${it.port}" }.toSet()
                val added = mutableListOf<TelegramProxyItem>()

                for (p in scraped) {
                    if (!existingHosts.contains("${p.host.lowercase()}:${p.port}")) {
                        added.add(p)
                    }
                }

                val allProxies = (current.proxies + added).toMutableList()
                val activeId = current.selectedProxyId ?: allProxies.firstOrNull { it.status == ProxyStatus.AVAILABLE }?.id

                val updatedConfig = current.copy(
                    proxies = allProxies,
                    selectedProxyId = activeId
                )
                updateAndSaveProxyConfig(updatedConfig)

                // Если прокси не были включены, но найден рабочий прокси, переключаем на него
                if (updatedConfig.mode != ProxyConnectionMode.CUSTOM && activeId != null) {
                    setProxyConnectionMode(ProxyConnectionMode.CUSTOM)
                }

                withContext(Dispatchers.Main) {
                    onResult(added.size, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading proxies from mtproto.ru", e)
                withContext(Dispatchers.Main) {
                    onResult(0, "Ошибка загрузки: ${e.message}")
                }
            }
        }
    }

    /**
     * Импорт прокси из буфера обмена.
     */
    fun importProxyFromClipboard(text: String, onResult: (Boolean, String?) -> Unit) {
        val parsed = MtprotoScraper.parseFromText(text)
        if (parsed == null) {
            onResult(false, "В буфере обмена не найдена ссылка tg://proxy или tg://socks")
            return
        }

        addOrUpdateProxy(parsed, testFirst = true) { ok, _ ->
            if (ok) {
                selectProxy(parsed.id)
                onResult(true, "Прокси ${parsed.displayAddress} успешно добавлен и проверен!")
            } else {
                onResult(false, "Прокси добавлен, но не отвечает на запрос проверки.")
            }
        }
    }

    private fun updateProxyStatus(proxyId: String, status: ProxyStatus, pingMs: Long?) {
        val current = _telegramProxyConfig.value
        val updatedList = current.proxies.map {
            if (it.id == proxyId) it.copy(status = status, pingMs = pingMs ?: it.pingMs) else it
        }
        updateAndSaveProxyConfig(current.copy(proxies = updatedList))
    }

    /**
     * Применение текущей конфигурации прокси в ядро TDLib.
     */
    private fun applyCurrentProxyConfigToTdLib() {
        val config = _telegramProxyConfig.value
        val activeClient = client ?: return

        when (config.mode) {
            ProxyConnectionMode.DISABLED, ProxyConnectionMode.SYSTEM -> {
                Log.i(TAG, "Disabling TDLib proxy (mode: ${config.mode})")
                send(TdApi.DisableProxy()) {
                    activeTdlibProxyId = null
                    updateNetworkType()
                }
            }
            ProxyConnectionMode.CUSTOM -> {
                val activeProxy = config.activeProxy
                if (activeProxy == null || !activeProxy.isValid) {
                    Log.w(TAG, "No valid active proxy selected for CUSTOM mode.")
                    send(TdApi.DisableProxy())
                    return
                }

                Log.i(TAG, "Enabling custom proxy in TDLib: ${activeProxy.type} ${activeProxy.displayAddress}")
                val tdProxy = activeProxy.toTdProxy()

                send(TdApi.AddProxy(tdProxy, true, "TV Custom Proxy")) { result ->
                    if (result is TdApi.AddedProxy) {
                        Log.i(TAG, "Proxy added & enabled in TDLib. TDLib ID: ${result.id}")
                        activeTdlibProxyId = result.id
                        updateProxyStatus(activeProxy.id, ProxyStatus.CONNECTED, activeProxy.pingMs)
                        updateNetworkType()
                    } else if (result is TdApi.Error) {
                        Log.e(TAG, "Failed to apply proxy in TDLib: ${result.message} (${result.code})")
                        updateProxyStatus(activeProxy.id, ProxyStatus.UNAVAILABLE, null)
                    }
                }
            }
        }
    }

    /**
     * Фоновый монитор для автопереключения прокси при отсутствии соединения.
     */
    private fun startAutoSwitchMonitor() {
        autoSwitchJob?.cancel()
        autoSwitchJob = scope.launch {
            var disconnectedSeconds = 0

            while (isActive) {
                delay(1000)
                val config = _telegramProxyConfig.value

                if (config.mode != ProxyConnectionMode.CUSTOM || !config.autoSwitch || config.proxies.size <= 1) {
                    disconnectedSeconds = 0
                    continue
                }

                val conn = _connectionState.value
                val isConnected = conn is TdApi.ConnectionStateReady

                if (isConnected) {
                    disconnectedSeconds = 0
                } else {
                    disconnectedSeconds++
                    val threshold = config.autoSwitchDelaySec.coerceAtLeast(5)

                    if (disconnectedSeconds >= threshold) {
                        Log.i(TAG, "AutoSwitch triggered after $disconnectedSeconds sec disconnected. Switching proxy...")
                        disconnectedSeconds = 0
                        switchNextWorkingProxy()
                    }
                }
            }
        }
    }

    private suspend fun switchNextWorkingProxy() {
        val config = _telegramProxyConfig.value
        val currentIndex = config.proxies.indexOfFirst { it.id == config.selectedProxyId }
        val proxies = config.proxies
        if (proxies.isEmpty()) return

        for (i in 1 until proxies.size) {
            val candidateIndex = (currentIndex + i) % proxies.size
            val candidate = proxies[candidateIndex]

            // Проверяем кандидата
            val ping = MtprotoScraper.verifyProxySocket(candidate.host, candidate.port, timeoutMs = 1500)
            if (ping.first) {
                Log.i(TAG, "AutoSwitch found alive proxy: ${candidate.displayAddress} (ping ${ping.second} ms)")
                withContext(Dispatchers.Main) {
                    selectProxy(candidate.id)
                }
                break
            }
        }
    }

    private fun updateAndSaveProxyConfig(config: TelegramProxyConfig) {
        _telegramProxyConfig.value = config
        saveProxyConfig(config)
    }

    private fun saveProxyConfig(config: TelegramProxyConfig) {
        appContext?.let { ctx ->
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(PREF_PROXY_CONFIG_JSON, config.toJson())
                .apply()
        }
    }

    private fun loadSavedProxyConfig(): TelegramProxyConfig {
        val ctx = appContext ?: return TelegramProxyConfig.DEFAULT
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(PREF_PROXY_CONFIG_JSON, null)
        return if (jsonStr.isNullOrBlank()) {
            TelegramProxyConfig.DEFAULT
        } else {
            TelegramProxyConfig.fromJson(jsonStr)
        }
    }

    // ============================================================================
    // Управление сессией и сброс базы
    // ============================================================================

    /**
     * Перезапуск сессии TDLib (мягкий или с полной очисткой базы данных).
     */
    fun restartSession(clearDatabase: Boolean = false) {
        Log.i(TAG, "Restarting TDLib session (clearDatabase=$clearDatabase)...")
        _lastError.value = if (clearDatabase) "Очистка кэша и перезапуск TDLib..." else "Перезапуск сетевой сессии Telegram..."

        val currentClient = client
        if (currentClient != null) {
            isRestartingSession = true
            pendingClearDatabase = clearDatabase
            try {
                currentClient.send(TdApi.Close(), null)
            } catch (t: Throwable) {
                Log.w(TAG, "Error closing client", t)
                isRestartingSession = false
                client = null
                if (clearDatabase) clearDatabaseFiles()
                config?.let { initialize(it) }
            }
        } else {
            if (clearDatabase) clearDatabaseFiles()
            config?.let { initialize(it) }
        }
    }

    private fun clearDatabaseFiles() {
        try {
            config?.databaseDirectory?.let { path ->
                val dir = File(path)
                if (dir.exists()) {
                    dir.listFiles()?.forEach { file ->
                        file.deleteRecursively()
                    }
                    Log.i(TAG, "Database directory cleared: $path")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear database directory", e)
        }
    }

    // ============================================================================
    // Базовые утилиты и сетевые запросы
    // ============================================================================

    suspend fun <T : TdApi.Object> execute(query: TdApi.Function<T>): Result<T> =
        suspendCancellableCoroutine { continuation ->
            val activeClient = client
            if (activeClient == null) {
                continuation.resume(Result.failure(IllegalStateException("TDLib client is not initialized")))
                return@suspendCancellableCoroutine
            }

            activeClient.send(query) { result ->
                if (result is TdApi.Error) {
                    continuation.resume(
                        Result.failure(Exception("TDLib Error [${result.code}]: ${result.message}"))
                    )
                } else {
                    @Suppress("UNCHECKED_CAST")
                    continuation.resume(Result.success(result as T))
                }
            }
        }

    fun send(query: TdApi.Function<*>, callback: (TdApi.Object) -> Unit) {
        val activeClient = client
        if (activeClient == null) {
            callback(TdApi.Error(500, "TDLib client not running"))
            return
        }
        activeClient.send(query) { result ->
            callback(result)
        }
    }

    suspend fun loadChats(limit: Int = 30): Result<List<Long>> {
        if (!isNativeLibraryLoaded || client == null) {
            return Result.success(listOf(1001L, 1002L, 1003L, 1004L, 1005L))
        }
        val loadResult = execute(TdApi.LoadChats(null, limit))
        if (loadResult.isFailure) {
            return Result.failure(loadResult.exceptionOrNull() ?: Exception("Failed to load chats"))
        }
        val chatsResult = execute(TdApi.GetChats(null, limit))
        return chatsResult.map { it.chatIds.toList() }
    }

    suspend fun getChatHistory(
        chatId: Long,
        fromMessageId: Long = 0,
        limit: Int = 50
    ): Result<List<TdApi.Message>> {
        val result = execute(TdApi.GetChatHistory(chatId, fromMessageId, 0, limit, false))
        return result.map { it.messages.toList() }
    }

    suspend fun sendMessage(chatId: Long, text: String): Result<TdApi.Message> {
        val content = TdApi.InputMessageText(TdApi.FormattedText(text, null), null, true)
        val request = TdApi.SendMessage().apply {
            this.chatId = chatId
            this.inputMessageContent = content
        }
        return execute(request)
    }

    fun logOut(onComplete: () -> Unit = {}) {
        send(TdApi.LogOut()) {
            onComplete()
        }
    }
}
