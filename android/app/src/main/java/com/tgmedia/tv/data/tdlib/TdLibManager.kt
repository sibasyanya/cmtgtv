package com.tgmedia.tv.data.tdlib

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import kotlin.coroutines.resume

/**
 * Реактивный менеджер TDLib (Staff Engineer level).
 * Инкапсулирует синглтон C++ ядра libtdjni через Java/Kotlin JNI обертку.
 * Обеспечивает потокобезопасные StateFlow и Coroutines для Android TV 10-foot UI.
 */
class TdLibManager private constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Текущее состояние авторизации сессии TDLib
    private val _authorizationState = MutableStateFlow<TdApi.AuthorizationState>(
        TdApi.AuthorizationStateWaitTdlibParameters()
    )
    val authorizationState: StateFlow<TdApi.AuthorizationState> = _authorizationState.asStateFlow()

    // Ссылка авторизации по QR коду для ТВ экрана: "tg://login?token=..."
    private val _qrCodeLink = MutableStateFlow<String?>(null)
    val qrCodeLink: StateFlow<String?> = _qrCodeLink.asStateFlow()

    // Флаг активного запроса QR кода (для предотвращения ошибки [400] Another authorization query has started)
    private val _isQrRequestInProgress = MutableStateFlow(false)
    val isQrRequestInProgress: StateFlow<Boolean> = _isQrRequestInProgress.asStateFlow()

    // Настройки и статус прокси-сервера (для обхода блокировок в РФ)
    private val _proxySettings = MutableStateFlow(ProxySettings())
    val proxySettings: StateFlow<ProxySettings> = _proxySettings.asStateFlow()

    // Режим входа: false = QR-код (по умолчанию для ТВ), true = ввод номера телефона
    @Volatile
    var isPhoneAuthMode: Boolean = false

    // Флаги управления жизненным циклом и перезапуском без блокировок файла td.binlog
    @Volatile
    private var isRestartingSession: Boolean = false
    @Volatile
    private var pendingClearDatabase: Boolean = false

    // Поток входящих обновлений данных (новые медиа, статус скачивания)
    private val _updates = MutableSharedFlow<TdApi.Update>(extraBufferCapacity = 128)
    val updates: SharedFlow<TdApi.Update> = _updates.asSharedFlow()

    private var client: Client? = null
    private var config: TdLibConfig? = null
    private var appContext: Context? = null
    private var activeProxyId: Int? = null

    companion object {
        private const val TAG = "TdLibManager"
        private const val PREFS_NAME = "tgmedia_tv_prefs"
        private const val PREF_PROXY_ENABLED = "proxy_enabled"
        private const val PREF_PROXY_SERVER = "proxy_server"
        private const val PREF_PROXY_PORT = "proxy_port"
        private const val PREF_PROXY_TYPE = "proxy_type"
        private const val PREF_PROXY_SECRET = "proxy_secret"
        private const val PREF_PROXY_USERNAME = "proxy_username"
        private const val PREF_PROXY_PASSWORD = "proxy_password"

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

                // Перенаправление внутренних логов ядра TDLib в Android Logcat
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
                Log.w(TAG, "Native library libtdjni.so is not available. Running in Safe/Demo mode.", e)
            }
        }
    }

    // Флаг доступности нативной TDLib
    val isNativeLoaded: Boolean get() = isNativeLibraryLoaded

    // Текущее состояние сетевого соединения с серверами Telegram
    private val _connectionState = MutableStateFlow<TdApi.ConnectionState?>(null)
    val connectionState: StateFlow<TdApi.ConnectionState?> = _connectionState.asStateFlow()

    // Текст последней ошибки TDLib (для вывода на экран ТВ)
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /**
     * Инициализация клиента TDLib с Context и настройками.
     */
    fun initialize(context: Context, config: TdLibConfig) {
        this.appContext = context.applicationContext
        val savedProxy = loadSavedProxySettings()
        _proxySettings.value = savedProxy
        initialize(config)
    }

    /**
     * Инициализация клиента TDLib с заданными параметрами.
     * Защищена от UnsatisfiedLinkError. При отсутствии libtdjni переходит в безопасный режим.
     */
    fun initialize(config: TdLibConfig) {
        this.config = config

        if (!isNativeLibraryLoaded) {
            Log.w(TAG, "Native library not loaded. Providing demo QR code for Android TV UI preview.")
            _qrCodeLink.value = "tg://login?token=DemoSafeModeTvPreviewToken"
            return
        }

        if (client != null) {
            Log.w(TAG, "TdLib client already initialized.")
            return
        }

        try {
            client = Client.create(
                { event -> handleIncomingEvent(event) },
                { error -> Log.e(TAG, "TDLib update exception", error) },
                { error -> Log.e(TAG, "TDLib default exception", error) }
            )
            // Оповещаем TDLib о доступности сети сразу при создании клиента
            updateNetworkType()

            // Если был сохранен включенный прокси, активируем его
            val currentProxy = _proxySettings.value
            if (currentProxy.enabled && currentProxy.server.isNotBlank()) {
                applyProxy(currentProxy)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to create Client due to native link error: ${e.message}", e)
            _lastError.value = "Ошибка запуска TDLib: ${e.message}"
            _qrCodeLink.value = "tg://login?token=DemoSafeModeTvPreviewToken"
        }
    }

    /**
     * Уведомление ядра TDLib об активном сетевом соединении (Wi-Fi/Ethernet).
     * Без вызова setNetworkType TDLib на Android может бесконечно ожидать сеть.
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
                Log.d(TAG, "SetNetworkType active confirmed ($networkType).")
            }
        }
    }

    /**
     * Обработка событий и обновлений от нативного C++ ядра.
     */
    private fun handleIncomingEvent(event: TdApi.Object) {
        when (event) {
            is TdApi.UpdateConnectionState -> {
                val newState = event.state
                Log.i(TAG, "ConnectionState updated: ${newState.javaClass.simpleName}")
                _connectionState.value = newState
            }
            is TdApi.UpdateAuthorizationState -> {
                val newState = event.authorizationState
                Log.i(TAG, "AuthorizationState updated: ${newState.javaClass.simpleName}")
                _authorizationState.value = newState

                when (newState) {
                    is TdApi.AuthorizationStateWaitTdlibParameters -> {
                        sendTdlibParameters()
                    }
                    is TdApi.AuthorizationStateWaitPhoneNumber -> {
                        // Для Android TV по умолчанию запускаем QR-код, если пользователь не выбрал ввод телефона
                        if (!isPhoneAuthMode) {
                            requestQrCodeAuthentication()
                        }
                    }
                    is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> {
                        // Получен токен для генерации QR-кода на экране ТВ
                        _isQrRequestInProgress.value = false
                        _qrCodeLink.value = newState.link
                        _lastError.value = null
                        Log.i(TAG, "Received QR Code Token link: ${newState.link}")
                    }
                    is TdApi.AuthorizationStateWaitCode -> {
                        Log.i(TAG, "AuthorizationStateWaitCode: code sent to phone/SMS")
                        _lastError.value = null
                    }
                    is TdApi.AuthorizationStateWaitPassword -> {
                        Log.i(TAG, "AuthorizationStateWaitPassword: 2FA cloud password required")
                        _lastError.value = null
                    }
                    is TdApi.AuthorizationStateReady -> {
                        // Авторизация успешно пройдена
                        _isQrRequestInProgress.value = false
                        _qrCodeLink.value = null
                        _lastError.value = null
                        Log.i(TAG, "Telegram Client AuthorizationStateReady! User is logged in.")
                    }
                    is TdApi.AuthorizationStateClosed -> {
                        Log.i(TAG, "TDLib Client session closed.")
                        client = null
                        _isQrRequestInProgress.value = false
                        if (isRestartingSession) {
                            scope.launch(Dispatchers.IO) {
                                delay(300) // Даем файловой системе безопасно освободить файловый дескриптор td.binlog
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
     * Отправка параметров приложения в ядро TDLib.
     */
    fun sendTdlibParameters() {
        val currentConfig = config ?: run {
            Log.e(TAG, "Cannot set TdlibParameters: config is null")
            _lastError.value = "Конфигурация приложения не задана"
            return
        }

        // Проверяем сетевое подключение
        updateNetworkType()

        val request = TdApi.SetTdlibParameters(
            currentConfig.useTestDc,
            currentConfig.databaseDirectory,
            currentConfig.filesDirectory,
            currentConfig.encryptionKey,
            true, // useFileDatabase
            true, // useChatInfoDatabase
            true, // useMessageDatabase
            false, // useSecretChats (не нужны в медиаклиенте ТВ)
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

    /**
     * Запрос авторизации по QR коду для ТВ (без ввода логина/пароля с пульта ДУ).
     * Защищен от повторного вызова во время выполнения (исключает ошибку [400] Another authorization query has started).
     */
    fun requestQrCodeAuthentication() {
        if (_isQrRequestInProgress.value) {
            Log.i(TAG, "RequestQrCodeAuthentication is already in progress, skipping duplicate call.")
            return
        }

        Log.i(TAG, "Requesting QR Code authentication from TDLib...")
        _isQrRequestInProgress.value = true
        updateNetworkType()

        send(TdApi.RequestQrCodeAuthentication(longArrayOf())) { result ->
            if (result is TdApi.Error) {
                Log.e(TAG, "RequestQrCodeAuthentication error: ${result.message} (code: ${result.code})")
                _isQrRequestInProgress.value = false
                if (result.code == 400 && result.message.contains("Another authorization query", ignoreCase = true)) {
                    _lastError.value = "Запрос уже отправлен. Ожидание ответа серверов Telegram..."
                } else {
                    _lastError.value = "Ошибка запроса QR: [${result.code}] ${result.message}"
                }
            } else {
                Log.i(TAG, "RequestQrCodeAuthentication query sent successfully, awaiting link...")
                _lastError.value = null
            }
        }
    }

    /**
     * Принудительное обновление QR-кода по нажатию кнопки на пульте ТВ.
     */
    fun refreshQr() {
        Log.i(TAG, "User triggered QR refresh.")
        if (_isQrRequestInProgress.value) {
            _lastError.value = "Запрос уже ожидает ответа сервера Telegram. Если соединение зависло, нажмите «Перезапустить»."
            return
        }

        _lastError.value = null
        updateNetworkType()
        when (val state = _authorizationState.value) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                sendTdlibParameters()
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                isPhoneAuthMode = false
                requestQrCodeAuthentication()
            }
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> {
                // Токен уже активен и обновляется Telegram автоматически
                Log.i(TAG, "QR token link already active: ${state.link}")
                _lastError.value = null
            }
            else -> {
                if (client == null) {
                    restartSession(clearDatabase = false)
                } else {
                    sendTdlibParameters()
                }
            }
        }
    }

    /**
     * Безопасный перезапуск сессии TDLib.
     * При clearDatabase=true полностью удаляет локальную базу td.binlog для устранения блокировок файлов.
     */
    fun restartSession(clearDatabase: Boolean = false) {
        Log.i(TAG, "Restarting TDLib session (clearDatabase=$clearDatabase)...")
        _isQrRequestInProgress.value = false
        _qrCodeLink.value = null
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

    /**
     * Отправка номера телефона для альтернативного входа на Android TV.
     */
    fun sendAuthenticationPhoneNumber(phoneNumber: String, onResult: (Boolean, String?) -> Unit) {
        val trimmed = phoneNumber.trim()
        Log.i(TAG, "Sending phone number: $trimmed")
        _lastError.value = null
        isPhoneAuthMode = true
        updateNetworkType()

        val activeClient = client
        if (activeClient == null) {
            val msg = "Клиент TDLib не запущен. Выполните перезапуск сессии."
            _lastError.value = msg
            onResult(false, msg)
            return
        }

        // Если TDLib ожидает параметры, отправляем их перед отправкой номера
        if (_authorizationState.value is TdApi.AuthorizationStateWaitTdlibParameters) {
            sendTdlibParameters()
        }

        send(TdApi.SetAuthenticationPhoneNumber(trimmed, null)) { result ->
            if (result is TdApi.Error) {
                Log.e(TAG, "SetAuthenticationPhoneNumber error: ${result.message} (${result.code})")
                val friendly = when (result.code) {
                    400 -> when {
                        result.message.contains("PHONE_NUMBER_INVALID", ignoreCase = true) ->
                            "Неверный номер телефона. Укажите в международном формате с плюсом (например, +79120517638)."
                        result.message.contains("Initialization parameters are needed", ignoreCase = true) ->
                            "Инициализация ядра TDLib... Нажмите кнопку ещё раз через секунду."
                        else -> "Ошибка [${result.code}]: ${result.message}"
                    }
                    429 -> "Слишком много запросов. Telegram временно ограничил отправку кодов на этот номер. Подождите несколько минут."
                    else -> "Ошибка отправки номера: [${result.code}] ${result.message}"
                }
                _lastError.value = friendly
                onResult(false, friendly)
            } else {
                Log.i(TAG, "Phone number accepted, waiting for code...")
                _lastError.value = null
                onResult(true, null)
            }
        }
    }

    /**
     * Проверка кода подтверждения Telegram (отправленного в приложение или SMS).
     */
    fun checkAuthenticationCode(code: String, onResult: (Boolean, String?) -> Unit) {
        val trimmed = code.trim()
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
     * Проверка облачного пароля двухфакторной аутентификации (2FA).
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
                    result.message.contains("PASSWORD_HASH_INVALID", ignoreCase = true) -> "Неверный пароль 2FA."
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
     * Включение и применение настроек Proxy (MTProto / SOCKS5).
     */
    fun applyProxy(settings: ProxySettings, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        saveProxySettings(settings)
        _proxySettings.value = settings

        if (!settings.enabled || settings.server.isBlank()) {
            disableProxy(onResult)
            return
        }

        val activeClient = client
        if (activeClient == null) {
            onResult(false, "Клиент TDLib не запущен. Перезапустите сессию.")
            return
        }

        val proxyType: TdApi.ProxyType = when (settings.type) {
            ProxySettings.ProxyType.SOCKS5 -> TdApi.ProxyTypeSocks5(settings.username, settings.password)
            ProxySettings.ProxyType.HTTP -> TdApi.ProxyTypeHttp(settings.username, settings.password, false)
            ProxySettings.ProxyType.MTPROTO -> TdApi.ProxyTypeMtproto(settings.secret)
        }

        val proxy = TdApi.Proxy(settings.server.trim(), settings.port, proxyType)
        send(TdApi.AddProxy(proxy, true, "Android TV Proxy")) { result ->
            if (result is TdApi.AddedProxy) {
                Log.i(TAG, "Proxy enabled successfully: ${settings.server}:${settings.port} (ID: ${result.id})")
                activeProxyId = result.id
                _lastError.value = null
                updateNetworkType()
                onResult(true, null)
            } else if (result is TdApi.Error) {
                Log.e(TAG, "Failed to enable proxy: ${result.message} (${result.code})")
                _lastError.value = "Ошибка Proxy: [${result.code}] ${result.message}"
                onResult(false, "[${result.code}] ${result.message}")
            }
        }
    }

    /**
     * Отключение активного Proxy и переход на прямое подключение.
     */
    fun disableProxy(onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        val current = ProxySettings.DIRECT
        saveProxySettings(current)
        _proxySettings.value = current

        val activeClient = client
        if (activeClient == null) {
            activeProxyId = null
            onResult(true, null)
            return
        }

        send(TdApi.DisableProxy()) { result ->
            activeProxyId = null
            updateNetworkType()
            if (result is TdApi.Error) {
                onResult(false, result.message)
            } else {
                onResult(true, null)
            }
        }
    }

    private fun saveProxySettings(settings: ProxySettings) {
        appContext?.let { ctx ->
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(PREF_PROXY_ENABLED, settings.enabled)
                .putString(PREF_PROXY_SERVER, settings.server)
                .putInt(PREF_PROXY_PORT, settings.port)
                .putString(PREF_PROXY_TYPE, settings.type.name)
                .putString(PREF_PROXY_SECRET, settings.secret)
                .putString(PREF_PROXY_USERNAME, settings.username)
                .putString(PREF_PROXY_PASSWORD, settings.password)
                .apply()
        }
    }

    private fun loadSavedProxySettings(): ProxySettings {
        val ctx = appContext ?: return ProxySettings.DIRECT
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(PREF_PROXY_ENABLED, false)
        val server = prefs.getString(PREF_PROXY_SERVER, "") ?: ""
        val port = prefs.getInt(PREF_PROXY_PORT, 443)
        val typeStr = prefs.getString(PREF_PROXY_TYPE, ProxySettings.ProxyType.MTPROTO.name) ?: ProxySettings.ProxyType.MTPROTO.name
        val type = try {
            ProxySettings.ProxyType.valueOf(typeStr)
        } catch (_: Throwable) {
            ProxySettings.ProxyType.MTPROTO
        }
        val secret = prefs.getString(PREF_PROXY_SECRET, "") ?: ""
        val username = prefs.getString(PREF_PROXY_USERNAME, "") ?: ""
        val password = prefs.getString(PREF_PROXY_PASSWORD, "") ?: ""

        // Очищаем недоступные локальные или устаревшие публичные адреса
        if (server == "127.0.0.1" || server == "localhost" || server == "149.154.175.50" || server.contains("digitalresistance")) {
            Log.w(TAG, "Clearing non-working default proxy: $server")
            prefs.edit().putBoolean(PREF_PROXY_ENABLED, false).putString(PREF_PROXY_SERVER, "").apply()
            return ProxySettings.DIRECT
        }

        return ProxySettings(
            enabled = enabled,
            server = server,
            port = port,
            type = type,
            secret = secret,
            username = username,
            password = password
        )
    }

    /**
     * Асинхронная отправка запроса в TDLib с Coroutines Result<T>.
     */
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

    /**
     * Callback-обертка над нативным send.
     */
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

    /**
     * Загрузка списка чатов (каналов, групп, личных переписок).
     */
    suspend fun loadChats(limit: Int = 30): Result<List<Long>> {
        if (!isNativeLibraryLoaded || client == null) {
            // Демонстрационный список каналов для проверки UI на ТВ пульте
            return Result.success(listOf(1001L, 1002L, 1003L, 1004L, 1005L))
        }
        val loadResult = execute(TdApi.LoadChats(null, limit))
        if (loadResult.isFailure) {
            return Result.failure(loadResult.exceptionOrNull() ?: Exception("Failed to load chats"))
        }
        val chatsResult = execute(TdApi.GetChats(null, limit))
        return chatsResult.map { it.chatIds.toList() }
    }

    /**
     * Получение истории сообщений выбранного чата или канала.
     */
    suspend fun getChatHistory(
        chatId: Long,
        fromMessageId: Long = 0,
        limit: Int = 50
    ): Result<List<TdApi.Message>> {
        val result = execute(TdApi.GetChatHistory(chatId, fromMessageId, 0, limit, false))
        return result.map { it.messages.toList() }
    }

    /**
     * Отправка текстового сообщения в чат с Android TV.
     */
    suspend fun sendMessage(chatId: Long, text: String): Result<TdApi.Message> {
        val content = TdApi.InputMessageText(TdApi.FormattedText(text, null), null, true)
        val request = TdApi.SendMessage().apply {
            this.chatId = chatId
            this.inputMessageContent = content
        }
        return execute(request)
    }

    /**
     * Выход из аккаунта и очистка кэша сессии.
     */
    fun logOut(onComplete: () -> Unit = {}) {
        send(TdApi.LogOut()) {
            _qrCodeLink.value = null
            onComplete()
        }
    }
}
