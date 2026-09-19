package com.tgmedia.tv.data.tdlib

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
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

    // Поток входящих обновлений данных (новые медиа, статус скачивания)
    private val _updates = MutableSharedFlow<TdApi.Update>(extraBufferCapacity = 128)
    val updates: SharedFlow<TdApi.Update> = _updates.asSharedFlow()

    private var client: Client? = null
    private var config: TdLibConfig? = null

    companion object {
        private const val TAG = "TdLibManager"

        @Volatile
        private var instance: TdLibManager? = null

        fun getInstance(): TdLibManager {
            return instance ?: synchronized(this) {
                instance ?: TdLibManager().also { instance = it }
            }
        }

        init {
            try {
                System.loadLibrary("tdjni")
                Log.i(TAG, "Native C++ library libtdjni.so successfully loaded.")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load libtdjni.so. Ensure native libraries exist in jniLibs", e)
            }
        }
    }

    /**
     * Инициализация клиента TDLib с заданными параметрами.
     * Запускает фоновый поток обработки сообщений C++ ядра.
     */
    fun initialize(config: TdLibConfig) {
        this.config = config

        if (client != null) {
            Log.w(TAG, "TdLib client already initialized.")
            return
        }

        client = Client.create(
            { event -> handleIncomingEvent(event) },
            { error -> Log.e(TAG, "TDLib update exception", error) },
            { error -> Log.e(TAG, "TDLib default exception", error) }
        )
    }

    /**
     * Обработка событий и обновлений от нативного C++ ядра.
     */
    private fun handleIncomingEvent(event: TdApi.Object) {
        when (event) {
            is TdApi.UpdateAuthorizationState -> {
                val newState = event.authorizationState
                Log.d(TAG, "AuthorizationState updated: ${newState.javaClass.simpleName}")
                _authorizationState.value = newState

                when (newState) {
                    is TdApi.AuthorizationStateWaitTdlibParameters -> {
                        sendTdlibParameters()
                    }
                    is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> {
                        // Получен токен для генерации QR-кода на экране ТВ
                        _qrCodeLink.value = newState.link
                        Log.i(TAG, "Received QR Code Token link: ${newState.link}")
                    }
                    is TdApi.AuthorizationStateReady -> {
                        // Авторизация успешно пройдена с телефона
                        _qrCodeLink.value = null
                        Log.i(TAG, "Telegram Client AuthorizationStateReady! User is logged in.")
                    }
                    is TdApi.AuthorizationStateClosed -> {
                        Log.i(TAG, "TDLib Client session closed.")
                        client = null
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
    private fun sendTdlibParameters() {
        val currentConfig = config ?: run {
            Log.e(TAG, "Cannot set TdlibParameters: config is null")
            return
        }

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
            } else {
                Log.i(TAG, "SetTdlibParameters accepted by TDLib.")
                // После установки параметров проверяем ключ базы
                checkDatabaseKey(currentConfig.encryptionKey)
            }
        }
    }

    private fun checkDatabaseKey(key: ByteArray) {
        send(TdApi.CheckDatabaseEncryptionKey(key)) { result ->
            if (result is TdApi.Error) {
                Log.e(TAG, "CheckDatabaseEncryptionKey error: ${result.message}")
            } else {
                Log.d(TAG, "Database encryption key confirmed.")
                // После подтверждения ключа запрашиваем авторизацию по QR-коду
                requestQrCodeAuthentication()
            }
        }
    }

    /**
     * Запрос авторизации по QR коду для ТВ (без ввода логина/пароля с пульта ДУ).
     */
    fun requestQrCodeAuthentication() {
        Log.i(TAG, "Requesting QR Code authentication from TDLib...")
        send(TdApi.RequestQrCodeAuthentication(emptyArray())) { result ->
            if (result is TdApi.Error) {
                Log.e(TAG, "RequestQrCodeAuthentication error: ${result.message} (code: ${result.code})")
            } else {
                Log.d(TAG, "RequestQrCodeAuthentication query sent successfully.")
            }
        }
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
        val loadResult = execute(TdApi.LoadChats(limit))
        if (loadResult.isFailure) {
            return Result.failure(loadResult.exceptionOrNull() ?: Exception("Failed to load chats"))
        }
        val chatsResult = execute(TdApi.GetChats(limit))
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
        val content = TdApi.InputMessageText(TdApi.FormattedText(text), false, true)
        val request = TdApi.SendMessage(chatId, 0, content)
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
