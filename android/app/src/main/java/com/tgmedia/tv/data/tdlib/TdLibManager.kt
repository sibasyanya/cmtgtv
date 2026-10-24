package com.tgmedia.tv.data.tdlib

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Исключение с кодом ошибки TDLib (например, 404 для конца списка чатов)
 */
class TdLibResponseException(val code: Int, message: String) : Exception("TDLib Error [$code]: $message")

/**
 * Тип списка чатов (Основной, Архив или Папка)
 */
sealed class ChatListType {
    object Main : ChatListType()
    object Archive : ChatListType()
    data class Folder(val folderId: Int, val title: String) : ChatListType()
}

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

    // Хранилище реальных чатов Telegram
    val chatsMap = ConcurrentHashMap<Long, TdApi.Chat>()
    private val _chatsList = MutableStateFlow<List<TdApi.Chat>>(emptyList())
    val chatsList: StateFlow<List<TdApi.Chat>> = _chatsList.asStateFlow()

    // Папки чатов Telegram (синхронизация с другими устройствами)
    private val _chatFolders = MutableStateFlow<List<TdApi.ChatFolderInfo>>(emptyList())
    val chatFolders: StateFlow<List<TdApi.ChatFolderInfo>> = _chatFolders.asStateFlow()

    // Количество чатов в архиве
    private val _archiveChatsCount = MutableStateFlow<Int>(0)
    val archiveChatsCount: StateFlow<Int> = _archiveChatsCount.asStateFlow()

    // Текущий выбранный список/папка чатов
    private val _currentChatListType = MutableStateFlow<ChatListType>(ChatListType.Main)
    val currentChatListType: StateFlow<ChatListType> = _currentChatListType.asStateFlow()

    // Кэш пользователей для отображения отправителей
    val usersMap = ConcurrentHashMap<Long, TdApi.User>()

    // Кэш локальных путей скачанных файлов (аватарки, фото и т.д.) fileId -> localPath
    val filePathsMap = ConcurrentHashMap<Int, String>()
    // Реактивное оповещение о загрузке файлов (fileId, localPath) для моментального обновления UI аватаров
    private val _fileDownloadedState = MutableStateFlow<Map<Int, String>>(emptyMap())
    val fileDownloadedState: StateFlow<Map<Int, String>> = _fileDownloadedState.asStateFlow()

    // Текущий авторизованный профиль пользователя Telegram
    private val _currentUser = MutableStateFlow<TdApi.User?>(null)
    val currentUser: StateFlow<TdApi.User?> = _currentUser.asStateFlow()

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
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isMediaAutoDownloadEnabled.value = prefs.getBoolean("auto_download_media", true)
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
                        scope.launch {
                            loadCurrentUser()
                            loadDetailedChats(TdApi.ChatListMain(), 50)
                            loadDetailedChats(TdApi.ChatListArchive(), 30)
                        }
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
                handleTdLibUpdate(event)
                scope.launch {
                    _updates.emit(event)
                }
            }
            else -> Unit
        }
    }

    private fun handleTdLibUpdate(update: TdApi.Update) {
        when (update) {
            is TdApi.UpdateNewChat -> {
                chatsMap[update.chat.id] = update.chat
                refreshChatsList()
            }
            is TdApi.UpdateChatTitle -> {
                chatsMap[update.chatId]?.let {
                    it.title = update.title
                    refreshChatsList()
                }
            }
            is TdApi.UpdateChatPhoto -> {
                chatsMap[update.chatId]?.let {
                    it.photo = update.photo
                    downloadChatPhoto(it)
                    refreshChatsList()
                }
            }
            is TdApi.UpdateChatLastMessage -> {
                chatsMap[update.chatId]?.let {
                    it.lastMessage = update.lastMessage
                    it.positions = update.positions
                    refreshChatsList()
                }
            }
            is TdApi.UpdateChatPosition -> {
                chatsMap[update.chatId]?.let { chat ->
                    val existing = chat.positions?.filter { !isSameChatList(it.list, update.position?.list) }?.toMutableList() ?: mutableListOf()
                    if (update.position != null && update.position.order != 0L) {
                        existing.add(update.position)
                    }
                    chat.positions = existing.toTypedArray()
                    refreshChatsList()
                }
            }
            is TdApi.UpdateChatFolders -> {
                val folders = update.chatFolders?.toList() ?: emptyList()
                _chatFolders.value = folders
                scope.launch {
                    for (folder in folders) {
                        loadDetailedChats(TdApi.ChatListFolder(folder.id), 30)
                    }
                }
            }
            is TdApi.UpdateChatReadInbox -> {
                chatsMap[update.chatId]?.let {
                    it.unreadCount = update.unreadCount
                    it.lastReadInboxMessageId = update.lastReadInboxMessageId
                    refreshChatsList()
                }
            }
            is TdApi.UpdateUser -> {
                usersMap[update.user.id] = update.user
                if (_currentUser.value?.id == update.user.id) {
                    _currentUser.value = update.user
                    downloadUserPhoto(update.user)
                }
            }
            is TdApi.UpdateUserStatus -> {
                usersMap[update.userId]?.status = update.status
                if (_currentUser.value?.id == update.userId) {
                    _currentUser.value?.status = update.status
                }
            }
            is TdApi.UpdateFile -> {
                val updatedFile = update.file
                val localPath = updatedFile.local?.path
                if (!localPath.isNullOrBlank() && updatedFile.local?.isDownloadingCompleted == true) {
                    registerDownloadedFile(updatedFile.id, localPath)
                }
            }
            else -> Unit
        }
    }

    private fun isSameChatList(a: TdApi.ChatList?, b: TdApi.ChatList?): Boolean {
        if (a == null || b == null) return a == b
        if (a.constructor != b.constructor) return false
        if (a is TdApi.ChatListFolder && b is TdApi.ChatListFolder) {
            return a.chatFolderId == b.chatFolderId
        }
        return true
    }

    /**
     * Получить отсортированный список чатов для конкретного типа (Основной, Архив или Папка)
     * в точности по правилам официального клиента Telegram:
     * строгая сортировка по position.order (по убыванию), закрепленные чаты первыми.
     */
    fun getChatsForList(listType: ChatListType): List<TdApi.Chat> {
        return chatsMap.values.mapNotNull { chat ->
            val positions = chat.positions
            if (positions == null || positions.isEmpty()) {
                return@mapNotNull if (listType is ChatListType.Main) {
                    Pair(chat, chat.lastMessage?.date?.toLong() ?: 0L)
                } else null
            }

            val matchingPos = positions.firstOrNull { pos ->
                when (listType) {
                    is ChatListType.Main -> pos.list is TdApi.ChatListMain && pos.order != 0L
                    is ChatListType.Archive -> pos.list is TdApi.ChatListArchive && pos.order != 0L
                    is ChatListType.Folder -> (pos.list as? TdApi.ChatListFolder)?.chatFolderId == listType.folderId && pos.order != 0L
                }
            }

            if (matchingPos != null) {
                Pair(chat, matchingPos.order)
            } else if (listType is ChatListType.Main) {
                val isArchived = positions.any { it.list is TdApi.ChatListArchive && it.order != 0L }
                val isExplicitlyRemovedFromMain = positions.any { it.list is TdApi.ChatListMain && it.order == 0L }
                if (!isArchived && !isExplicitlyRemovedFromMain && positions.none { it.list is TdApi.ChatListMain }) {
                    Pair(chat, chat.lastMessage?.date?.toLong() ?: 0L)
                } else null
            } else null
        }
        .sortedByDescending { it.second }
        .map { it.first }
    }

    /**
     * Переключение активного списка/папки чатов
     */
    fun selectChatList(type: ChatListType) {
        _currentChatListType.value = type
        refreshChatsList()
        scope.launch {
            when (type) {
                is ChatListType.Main -> loadDetailedChats(TdApi.ChatListMain(), 50)
                is ChatListType.Archive -> loadDetailedChats(TdApi.ChatListArchive(), 50)
                is ChatListType.Folder -> loadDetailedChats(TdApi.ChatListFolder(type.folderId), 50)
            }
        }
    }

    private fun refreshChatsList() {
        val archCount = chatsMap.values.count { chat ->
            chat.positions?.any { it.list is TdApi.ChatListArchive && it.order != 0L } == true
        }
        _archiveChatsCount.value = archCount
        _chatsList.value = getChatsForList(_currentChatListType.value)
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
            send(TdApi.RemoveProxy(id))
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
                send(TdApi.RemoveProxy(id))
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
            if (client != null) {
                pingProxyWithTdLib(item) { tdOk, tdMs, tdErr ->
                    if (tdOk && tdMs != null) {
                        val status = if (current.mode == ProxyConnectionMode.CUSTOM && current.selectedProxyId == proxyId) {
                            ProxyStatus.CONNECTED
                        } else {
                            ProxyStatus.AVAILABLE
                        }
                        updateProxyStatus(proxyId, status, tdMs)
                        onResult(true, tdMs, null)
                    } else {
                        // Если TDLib PingProxy вернул ошибку, проверяем сокет для диагностики
                        scope.launch {
                            val socketCheck = MtprotoScraper.verifyProxySocket(item.host, item.port, timeoutMs = 3000)
                            val isSocketOpen = socketCheck.first
                            updateProxyStatus(proxyId, ProxyStatus.UNAVAILABLE, null)
                            withContext(Dispatchers.Main) {
                                val diagMsg = if (isSocketOpen) {
                                    "TCP-порт открыт, но Telegram сервер не ответил через данный прокси (${tdErr ?: "сбой рукопожатия / ключ заблокирован"})"
                                } else {
                                    "Прокси-сервер недоступен (таймаут TCP порта)"
                                }
                                onResult(false, null, diagMsg)
                            }
                        }
                    }
                }
            } else {
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
    }

    /**
     * Загрузка свежих MTProto прокси с сайта mtproto.ru и резервных зеркал.
     */
    fun loadProxiesFromMtprotoRu(onResult: (Int, String?) -> Unit) {
        scope.launch {
            try {
                val scraped = MtprotoScraper.fetchAndVerifyProxies()
                if (scraped.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onResult(0, "Не удалось получить список прокси. Проверьте соединение с интернетом.")
                    }
                    return@launch
                }

                val current = _telegramProxyConfig.value
                val scrapedMap = scraped.associateBy { "${it.host.lowercase()}:${it.port}" }

                // Обновляем статусы существующих и добавляем новые
                val updatedExisting = current.proxies.map { existing ->
                    val key = "${existing.host.lowercase()}:${existing.port}"
                    val fresh = scrapedMap[key]
                    if (fresh != null) {
                        existing.copy(status = fresh.status, pingMs = fresh.pingMs)
                    } else existing
                }

                val existingKeys = current.proxies.map { "${it.host.lowercase()}:${it.port}" }.toSet()
                val added = scraped.filter { !existingKeys.contains("${it.host.lowercase()}:${it.port}") }

                val allProxies = (updatedExisting + added).toMutableList()
                // Сохраняем выбор пользователя: если уже был выбран прокси (например, Socks5), не переключаем принудительно на MTProto
                val activeId = current.selectedProxyId ?: allProxies.firstOrNull { it.status == ProxyStatus.AVAILABLE }?.id

                val updatedConfig = current.copy(
                    proxies = allProxies,
                    selectedProxyId = activeId
                )
                updateAndSaveProxyConfig(updatedConfig)

                // Если пользователь находился в режиме CUSTOM с активным прокси, применяем обновление
                if (updatedConfig.mode == ProxyConnectionMode.CUSTOM) {
                    applyCurrentProxyConfigToTdLib()
                }

                val availableCount = allProxies.count { it.status == ProxyStatus.AVAILABLE }
                withContext(Dispatchers.Main) {
                    val statusMessage = if (added.isNotEmpty()) {
                        "Добавлено ${added.size} новых прокси (рабочих: $availableCount)"
                    } else {
                        "Прокси обновлены (доступно рабочих: $availableCount)"
                    }
                    onResult(added.size, statusMessage)
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
                    send(TdApi.DisableProxy()) {
                        activeTdlibProxyId = null
                        updateNetworkType()
                    }
                    return
                }

                Log.i(TAG, "Enabling custom proxy in TDLib: ${activeProxy.type} ${activeProxy.displayAddress}")
                val tdProxy = activeProxy.toTdProxy()

                // Очищаем предыдущие прокси в TDLib перед добавлением нового, чтобы избежать конфликтов шлюзов
                send(TdApi.GetProxies()) { proxiesRes ->
                    if (proxiesRes is TdApi.AddedProxies) {
                        for (p in proxiesRes.proxies) {
                            send(TdApi.RemoveProxy(p.id))
                        }
                    }

                    // Добавляем и немедленно активируем выбранный пользователем прокси (Socks5/MTProto/Http)
                    send(TdApi.AddProxy(tdProxy, true, "TV ${activeProxy.type} Proxy")) { result ->
                        if (result is TdApi.AddedProxy) {
                            Log.i(TAG, "Proxy added & enabled in TDLib. TDLib ID: ${result.id}")
                            activeTdlibProxyId = result.id
                            send(TdApi.EnableProxy(result.id)) { enableRes ->
                                Log.i(TAG, "EnableProxy ID ${result.id} result: $enableRes")
                            }
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
        if (proxies.size <= 1) return

        for (i in 1 until proxies.size) {
            val candidateIndex = (currentIndex + i) % proxies.size
            val candidate = proxies[candidateIndex]

            // Проверяем доступность кандидата через TDLib
            val tdPingDeferred = CompletableDeferred<Boolean>()
            pingProxyWithTdLib(candidate) { ok, _, _ ->
                tdPingDeferred.complete(ok)
            }
            val responds = withTimeoutOrNull(2500) { tdPingDeferred.await() } ?: false

            if (responds) {
                Log.i(TAG, "AutoSwitch found alive proxy with Telegram response: ${candidate.displayAddress}")
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
                currentClient.send(TdApi.Close()) {}
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
                        Result.failure(TdLibResponseException(result.code, result.message))
                    )
                } else {
                    @Suppress("UNCHECKED_CAST")
                    continuation.resume(Result.success(result as T))
                }
            }
        }

    fun send(query: TdApi.Function<*>, callback: ((TdApi.Object) -> Unit)? = null) {
        val activeClient = client
        if (activeClient == null) {
            callback?.invoke(TdApi.Error(500, "TDLib client not running"))
            return
        }
        activeClient.send(query) { result ->
            callback?.invoke(result)
        }
    }

    /**
     * Загрузка чатов в TDLib. TDLib возвращает ошибку 404, когда все чаты уже подгружены.
     */
    suspend fun loadChats(limit: Int = 40): Result<List<Long>> {
        if (!isNativeLibraryLoaded || client == null) {
            return Result.success(emptyList())
        }
        val loadResult = execute(TdApi.LoadChats(null, limit))
        if (loadResult.isFailure) {
            val err = loadResult.exceptionOrNull()
            if (err !is TdLibResponseException || err.code != 404) {
                Log.w(TAG, "LoadChats notice: ${err?.message}")
            }
        }
        val chatsResult = execute(TdApi.GetChats(null, limit))
        return chatsResult.map { it.chatIds.toList() }
    }

    /**
     * Загрузка реальных чатов с полными названиями, сообщениями и аватарками.
     */
    suspend fun loadDetailedChats(chatList: TdApi.ChatList? = null, limit: Int = 50): Result<List<TdApi.Chat>> {
        if (!isNativeLibraryLoaded || client == null) {
            return Result.success(emptyList())
        }
        try {
            execute(TdApi.LoadChats(chatList, limit))
        } catch (_: Exception) {}

        val chatsResult = execute(TdApi.GetChats(chatList, limit))
        val chatIds = chatsResult.getOrNull()?.chatIds ?: longArrayOf()

        for (id in chatIds) {
            try {
                if (!chatsMap.containsKey(id)) {
                    val chatRes = execute(TdApi.GetChat(id))
                    chatRes.getOrNull()?.let { chat ->
                        chatsMap[chat.id] = chat
                        downloadChatPhoto(chat)
                    }
                } else {
                    chatsMap[id]?.let { downloadChatPhoto(it) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to getChat for $id: ${e.message}")
            }
        }
        refreshChatsList()
        return Result.success(_chatsList.value)
    }

    suspend fun loadDetailedChats(limit: Int): Result<List<TdApi.Chat>> {
        return loadDetailedChats(null, limit)
    }

    /**
     * Загрузка профиля текущего пользователя Telegram (GetMe).
     */
    suspend fun loadCurrentUser(): Result<TdApi.User> {
        val res = execute(TdApi.GetMe())
        res.getOrNull()?.let { user ->
            _currentUser.value = user
            usersMap[user.id] = user
            downloadUserPhoto(user)
        }
        return res
    }

    /**
     * Проверка отклика Telegram через TDLib PingProxy.
     */
    fun pingProxyWithTdLib(proxyItem: TelegramProxyItem, onResult: (Boolean, Long?, String?) -> Unit) {
        val tdProxy = proxyItem.toTdProxy()
        send(TdApi.PingProxy(tdProxy)) { result ->
            if (result is TdApi.Seconds) {
                val ms = (result.seconds * 1000).toLong()
                updateProxyStatus(proxyItem.id, ProxyStatus.AVAILABLE, ms)
                onResult(true, ms, null)
            } else if (result is TdApi.Error) {
                updateProxyStatus(proxyItem.id, ProxyStatus.UNAVAILABLE, null)
                onResult(false, null, result.message)
            }
        }
    }

    suspend fun getChatHistory(
        chatId: Long,
        fromMessageId: Long = 0,
        offset: Int = 0,
        limit: Int = 30
    ): Result<List<TdApi.Message>> {
        val result = execute(TdApi.GetChatHistory(chatId, fromMessageId, offset, limit, false))
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

    suspend fun sendTextMessage(chatId: Long, text: String): Result<TdApi.Message> {
        return sendMessage(chatId, text)
    }

    fun openChat(chatId: Long) {
        send(TdApi.OpenChat(chatId)) {
            Log.d(TAG, "Chat $chatId opened in TDLib.")
        }
    }

    fun closeChat(chatId: Long) {
        send(TdApi.CloseChat(chatId)) {
            Log.d(TAG, "Chat $chatId closed in TDLib.")
        }
    }

    /**
     * Загрузка аватара чата через TDLib (загружаем и миниатюру, и полноразмерное фото).
     */
    fun downloadChatPhoto(chat: TdApi.Chat) {
        val small = chat.photo?.small
        if (small != null) {
            downloadFile(small.id, priority = 16)
        }
        val big = chat.photo?.big
        if (big != null) {
            downloadFile(big.id, priority = 10)
        }
    }

    /**
     * Загрузка аватара пользователя через TDLib.
     */
    fun downloadUserPhoto(user: TdApi.User) {
        val small = user.profilePhoto?.small
        if (small != null) {
            downloadFile(small.id, priority = 16)
        }
        val big = user.profilePhoto?.big
        if (big != null) {
            downloadFile(big.id, priority = 10)
        }
    }

    /**
     * Получить локальный файл аватара чата (если уже скачан).
     * Приоритетно возвращает полноразмерный аватар высокого разрешения.
     */
    fun getChatPhotoPath(chat: TdApi.Chat?): String? {
        if (chat == null) return null
        val big = chat.photo?.big
        if (big != null) {
            val cached = filePathsMap[big.id]
            if (!cached.isNullOrBlank() && File(cached).exists()) return cached
            val local = big.local?.path
            if (!local.isNullOrBlank() && File(local).exists()) {
                filePathsMap[big.id] = local
                return local
            }
            downloadFile(big.id, priority = 20)
        }
        val small = chat.photo?.small
        if (small != null) {
            val cached = filePathsMap[small.id]
            if (!cached.isNullOrBlank() && File(cached).exists()) return cached
            val local = small.local?.path
            if (!local.isNullOrBlank() && File(local).exists()) {
                filePathsMap[small.id] = local
                return local
            }
            downloadFile(small.id, priority = 16)
        }
        return null
    }

    /**
     * Получить локальный файл аватара пользователя (если уже скачан).
     */
    fun getUserPhotoPath(user: TdApi.User?): String? {
        if (user == null) return null
        val file = user.profilePhoto?.small ?: user.profilePhoto?.big ?: return null
        val cached = filePathsMap[file.id]
        if (!cached.isNullOrBlank()) return cached
        val local = file.local?.path
        if (!local.isNullOrBlank()) {
            filePathsMap[file.id] = local
            return local
        }
        downloadFile(file.id, priority = 16)
        return null
    }

    /**
     * Зарегистрировать скачанный файл в локальном кэше и оповестить подписчиков StateFlow.
     */
    fun registerDownloadedFile(fileId: Int, path: String) {
        if (fileId <= 0 || path.isBlank()) return
        val f = File(path)
        if (f.exists() && f.length() > 0) {
            filePathsMap[fileId] = path
            if (_fileDownloadedState.value[fileId] != path) {
                _fileDownloadedState.value = _fileDownloadedState.value + (fileId to path)
            }
        }
    }

    /**
     * Получить локальный путь к файлу, если он уже загружен.
     */
    fun getFilePath(fileId: Int?): String? {
        if (fileId == null || fileId <= 0) return null
        val cached = filePathsMap[fileId]
        if (!cached.isNullOrBlank()) {
            val f = File(cached)
            if (f.exists() && f.length() > 0) return cached
        }
        return null
    }

    /**
     * Скачивание медиафайла через TDLib с учетом пользовательской настройки автозагрузки.
     */
    fun downloadMediaFile(fileId: Int, priority: Int = 16, force: Boolean = false) {
        if (fileId <= 0) return
        val cachedPath = filePathsMap[fileId]
        if (!cachedPath.isNullOrBlank()) {
            val f = File(cachedPath)
            if (f.exists() && f.length() > 0) {
                return
            }
        }

        if (!force && !_isMediaAutoDownloadEnabled.value) {
            // Если автозагрузка выключена, проверяем локальный кэш TDLib без инициирования сетевой загрузки
            send(TdApi.GetFile(fileId)) { res ->
                if (res is TdApi.File) {
                    val path = res.local?.path
                    if (!path.isNullOrBlank() && res.local?.isDownloadingCompleted == true) {
                        registerDownloadedFile(res.id, path)
                    }
                }
            }
            return
        }

        downloadFile(fileId, priority)
    }

    /**
     * Скачивание файла через TDLib (аватары, миниатюры фото, видео, стикеры).
     */
    fun downloadFile(fileId: Int, priority: Int = 16) {
        if (fileId <= 0) return
        send(TdApi.DownloadFile(fileId, priority, 0, 0, false)) { res ->
            if (res is TdApi.File) {
                val path = res.local?.path
                if (!path.isNullOrBlank() && res.local?.isDownloadingCompleted == true) {
                    registerDownloadedFile(res.id, path)
                }
            }
        }
    }

    /**
     * Получить имя отправителя сообщения по senderId (пользователь или канал).
     */
    suspend fun getSenderName(senderId: TdApi.MessageSender?): String {
        return when (senderId) {
            is TdApi.MessageSenderUser -> {
                val cached = usersMap[senderId.userId]
                if (cached != null) {
                    val full = "${cached.firstName} ${cached.lastName}".trim()
                    if (full.isNotBlank()) full else (cached.usernames?.activeUsernames?.firstOrNull() ?: "Пользователь")
                } else {
                    val userRes = execute(TdApi.GetUser(senderId.userId))
                    val user = userRes.getOrNull()
                    if (user != null) {
                        usersMap[user.id] = user
                        val full = "${user.firstName} ${user.lastName}".trim()
                        if (full.isNotBlank()) full else (user.usernames?.activeUsernames?.firstOrNull() ?: "Пользователь")
                    } else "Пользователь"
                }
            }
            is TdApi.MessageSenderChat -> {
                val cached = chatsMap[senderId.chatId]
                if (cached != null) {
                    cached.title.ifBlank { "Чат" }
                } else {
                    val chatRes = execute(TdApi.GetChat(senderId.chatId))
                    chatRes.getOrNull()?.title ?: "Чат"
                }
            }
            else -> "Telegram"
        }
    }

    /**
     * Проверка разрешения на отправку сообщений в чат (как в официальном клиенте Telegram).
     * В каналах писать могут только администраторы/создатели с правом canPostMessages.
     * В группах учитываются chat.permissions.canSendBasicMessages и статус ограничений.
     * В приватных диалогах отправка разрешена всегда.
     */
    suspend fun canSendMessagesInChat(chat: TdApi.Chat?): Boolean {
        if (chat == null) return false
        return when (val type = chat.type) {
            is TdApi.ChatTypePrivate -> true
            is TdApi.ChatTypeSecret -> true
            is TdApi.ChatTypeSupergroup -> {
                val supergroupRes = execute(TdApi.GetSupergroup(type.supergroupId))
                val supergroup = supergroupRes.getOrNull()
                if (supergroup != null) {
                    when (val status = supergroup.status) {
                        is TdApi.ChatMemberStatusCreator -> true
                        is TdApi.ChatMemberStatusAdministrator -> {
                            if (supergroup.isChannel) {
                                status.rights?.canPostMessages == true
                            } else {
                                true
                            }
                        }
                        is TdApi.ChatMemberStatusMember -> {
                            if (supergroup.isChannel) {
                                false
                            } else {
                                chat.permissions?.canSendBasicMessages == true
                            }
                        }
                        is TdApi.ChatMemberStatusRestricted -> {
                            status.isMember && status.permissions?.canSendBasicMessages == true
                        }
                        else -> false
                    }
                } else {
                    if (type.isChannel) false else (chat.permissions?.canSendBasicMessages == true)
                }
            }
            is TdApi.ChatTypeBasicGroup -> {
                chat.permissions?.canSendBasicMessages == true
            }
            else -> false
        }
    }

    /**
     * Модель подробных данных профиля чата (описание, ссылка, юзернейм, количество участников).
     */
    data class ChatProfileDetails(
        val title: String,
        val username: String? = null,
        val inviteLink: String? = null,
        val description: String? = null,
        val memberCount: Int? = null,
        val chatTypeDescription: String,
        val avatarPath: String? = null,
        val isVerified: Boolean = false,
        val isScam: Boolean = false,
        val isFake: Boolean = false
    ) {
        val typeDescription: String get() = chatTypeDescription
    }

    suspend fun getChatProfileDetails(chat: TdApi.Chat?): ChatProfileDetails? {
        if (chat == null) return null
        return fetchChatProfileDetails(chat.id)
    }

    /**
     * Загрузка полной информации о профиле чата (канала, группы, пользователя или бота).
     */
    suspend fun fetchChatProfileDetails(chatId: Long): ChatProfileDetails? {
        val chat = chatsMap[chatId] ?: execute(TdApi.GetChat(chatId)).getOrNull() ?: return null
        val avatarPath = getChatPhotoPath(chat)

        return when (val type = chat.type) {
            is TdApi.ChatTypeSupergroup -> {
                val sg = execute(TdApi.GetSupergroup(type.supergroupId)).getOrNull()
                val fullInfo = execute(TdApi.GetSupergroupFullInfo(type.supergroupId)).getOrNull()
                val primaryUsername = sg?.usernames?.activeUsernames?.firstOrNull()
                    ?: sg?.usernames?.editableUsername
                val typeDesc = if (type.isChannel) "Канал" else "Супергруппа"
                ChatProfileDetails(
                    title = chat.title,
                    username = primaryUsername,
                    inviteLink = fullInfo?.inviteLink?.inviteLink,
                    description = fullInfo?.description?.ifBlank { null },
                    memberCount = fullInfo?.memberCount?.takeIf { it > 0 } ?: sg?.memberCount?.takeIf { it > 0 },
                    chatTypeDescription = typeDesc,
                    avatarPath = avatarPath,
                    isVerified = sg?.verificationStatus?.isVerified == true,
                    isScam = sg?.verificationStatus?.isScam == true,
                    isFake = sg?.verificationStatus?.isFake == true
                )
            }
            is TdApi.ChatTypeBasicGroup -> {
                val fullInfo = execute(TdApi.GetBasicGroupFullInfo(type.basicGroupId)).getOrNull()
                ChatProfileDetails(
                    title = chat.title,
                    username = null,
                    inviteLink = fullInfo?.inviteLink?.inviteLink,
                    description = fullInfo?.description?.ifBlank { null },
                    memberCount = fullInfo?.members?.size,
                    chatTypeDescription = "Группа",
                    avatarPath = avatarPath
                )
            }
            is TdApi.ChatTypePrivate -> {
                val user = usersMap[type.userId] ?: execute(TdApi.GetUser(type.userId)).getOrNull()
                val fullInfo = execute(TdApi.GetUserFullInfo(type.userId)).getOrNull()
                val uname = user?.usernames?.activeUsernames?.firstOrNull()
                    ?: user?.usernames?.editableUsername
                val typeDesc = if (user?.type is TdApi.UserTypeBot) "Бот" else "Личный чат"
                ChatProfileDetails(
                    title = chat.title,
                    username = uname,
                    inviteLink = null,
                    description = fullInfo?.bio?.text?.ifBlank { null }
                        ?: fullInfo?.botInfo?.description?.ifBlank { null },
                    memberCount = null,
                    chatTypeDescription = typeDesc,
                    avatarPath = avatarPath,
                    isVerified = user?.verificationStatus?.isVerified == true,
                    isScam = user?.verificationStatus?.isScam == true,
                    isFake = user?.verificationStatus?.isFake == true
                )
            }
            is TdApi.ChatTypeSecret -> {
                ChatProfileDetails(
                    title = chat.title,
                    chatTypeDescription = "Секретный чат",
                    avatarPath = avatarPath
                )
            }
            else -> {
                ChatProfileDetails(
                    title = chat.title,
                    chatTypeDescription = "Чат",
                    avatarPath = avatarPath
                )
            }
        }
    }

    /**
     * Отметить сообщения прочитанными в чате (синхронизация со всеми устройствами аккаунта).
     */
    fun viewMessages(chatId: Long, messageIds: LongArray) {
        if (messageIds.isEmpty()) return
        send(TdApi.ViewMessages(chatId, messageIds, null, true)) {
            Log.d(TAG, "viewMessages sent for chat $chatId, ${messageIds.size} messages.")
        }
    }

    fun logOut(onComplete: () -> Unit = {}) {
        send(TdApi.LogOut()) {
            _currentUser.value = null
            chatsMap.clear()
            _chatsList.value = emptyList()
            usersMap.clear()
            filePathsMap.clear()
            _fileDownloadedState.value = emptyMap()
            onComplete()
        }
    }

    // Настройка автоматической загрузки медиа (фото, видео, стикеры, аудио)
    private val _isMediaAutoDownloadEnabled = MutableStateFlow<Boolean>(true)
    val isMediaAutoDownloadEnabled: StateFlow<Boolean> = _isMediaAutoDownloadEnabled.asStateFlow()

    fun setMediaAutoDownloadEnabled(enabled: Boolean) {
        _isMediaAutoDownloadEnabled.value = enabled
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)?.edit()
            ?.putBoolean("auto_download_media", enabled)
            ?.apply()
    }
}
