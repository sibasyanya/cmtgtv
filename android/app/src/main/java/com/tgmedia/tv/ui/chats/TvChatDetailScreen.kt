package com.tgmedia.tv.ui.chats

import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tgmedia.tv.data.tdlib.TdLibManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MessageMediaType {
    NONE,
    PHOTO,
    VIDEO,
    STICKER,
    AUDIO,
    VOICE,
    DOCUMENT
}

/**
 * Извлеченная из сообщения или профиля ссылка (внутренняя Telegram или внешняя веб-ссылка).
 */
data class ExtractedLink(
    val url: String,
    val label: String,
    val isInternalTelegram: Boolean
)

/**
 * Парсер внутренних (@username, t.me/..., tg://...) и внешних (https://...) ссылок
 * с поддержкой TDLib TextEntity (включая скрытые ссылки TextEntityTypeTextUrl) и подсветкой в тексте.
 */
object LinkExtractor {
    private val rawLinkRegex = Regex(
        """(https?://[^\s<>"]+|tg://[^\s<>"]+|(?:t\.me|telegram\.me|telegram\.dog)/[a-zA-Z0-9_+/=?&-]+|@[a-zA-Z0-9_]{4,32})""",
        RegexOption.IGNORE_CASE
    )

    fun isInternalTelegramLink(rawUrl: String): Boolean {
        val u = rawUrl.trim().lowercase()
        return u.startsWith("@") ||
                u.startsWith("tg://") ||
                u.startsWith("t.me/") ||
                u.startsWith("https://t.me/") ||
                u.startsWith("http://t.me/") ||
                u.startsWith("https://telegram.me/") ||
                u.startsWith("https://telegram.dog/")
    }

    private fun cleanPunctuation(raw: String): String {
        return raw.trimEnd('.', ',', ';', '!', '?', ')', ']', '}', '"', '\'')
    }

    fun extractLinks(text: String, entities: Array<TdApi.TextEntity>? = null): List<ExtractedLink> {
        val result = LinkedHashMap<String, ExtractedLink>()

        // 1. Сначала извлекаем сущности TDLib (включая скрытые ссылки TextEntityTypeTextUrl)
        entities?.forEach { entity ->
            val start = entity.offset.coerceAtLeast(0)
            val end = (entity.offset + entity.length).coerceAtMost(text.length)
            val snippet = if (start < end) text.substring(start, end) else ""

            when (val t = entity.type) {
                is TdApi.TextEntityTypeTextUrl -> {
                    val target = cleanPunctuation(t.url)
                    if (target.isNotBlank()) {
                        val isInternal = isInternalTelegramLink(target)
                        val display = if (snippet.isNotBlank() && snippet != target) {
                            "${snippet.take(28)} (${target.take(34)})"
                        } else {
                            target.take(45)
                        }
                        result.putIfAbsent(target.lowercase(), ExtractedLink(target, display, isInternal))
                    }
                }
                is TdApi.TextEntityTypeUrl -> {
                    val target = cleanPunctuation(snippet)
                    if (target.isNotBlank()) {
                        val isInternal = isInternalTelegramLink(target)
                        result.putIfAbsent(target.lowercase(), ExtractedLink(target, target.take(45), isInternal))
                    }
                }
                is TdApi.TextEntityTypeMention -> {
                    val target = cleanPunctuation(snippet)
                    if (target.startsWith("@") && target.length > 1) {
                        result.putIfAbsent(target.lowercase(), ExtractedLink(target, target, true))
                    }
                }
                else -> Unit
            }
        }

        // 2. Дополнительно ищем ссылки и @упоминания регулярным выражением по тексту
        rawLinkRegex.findAll(text).forEach { match ->
            val target = cleanPunctuation(match.value)
            if (target.isNotBlank()) {
                val isInternal = isInternalTelegramLink(target)
                result.putIfAbsent(target.lowercase(), ExtractedLink(target, target.take(45), isInternal))
            }
        }

        return result.values.toList()
    }

    fun buildHighlightedText(text: String): AnnotatedString {
        return buildAnnotatedString {
            append(text)
            rawLinkRegex.findAll(text).forEach { match ->
                val cleaned = cleanPunctuation(match.value)
                val endIdx = match.range.first + cleaned.length
                if (endIdx <= text.length) {
                    addStyle(
                        style = SpanStyle(
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline
                        ),
                        start = match.range.first,
                        end = endIdx
                    )
                }
            }
        }
    }
}

data class UiMessage(
    val id: Long,
    val senderName: String,
    val text: String,
    val time: String,
    val isOutgoing: Boolean,
    val mediaType: MessageMediaType = MessageMediaType.NONE,
    val fileId: Int? = null,
    val fullFileId: Int? = null,
    val mediaInfo: String = "",
    val links: List<ExtractedLink> = emptyList()
)

/**
 * Преобразование сообщения TDLib в отображаемую модель UI с поддержкой медиа и извлечением ссылок.
 */
fun TdApi.Message.toUiMessage(senderTitle: String): UiMessage {
    val timeFormatted = try {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(this.date * 1000L))
    } catch (_: Exception) {
        ""
    }

    var mType = MessageMediaType.NONE
    var targetFileId: Int? = null
    var targetFullFileId: Int? = null
    var mInfo = ""
    var formattedEntities: Array<TdApi.TextEntity>? = null
    val tdManager = try { TdLibManager.getInstance() } catch (_: Throwable) { null }

    val contentText = when (val c = this.content) {
        is TdApi.MessageText -> {
            formattedEntities = c.text.entities
            c.text.text
        }
        is TdApi.MessagePhoto -> {
            mType = MessageMediaType.PHOTO
            formattedEntities = c.caption.entities
            val bestSize = c.photo.sizes.maxByOrNull { it.width * it.height }
            val thumbSize = c.photo.sizes.firstOrNull { it.type in listOf("m", "x", "s") } ?: bestSize
            targetFileId = thumbSize?.photo?.id ?: bestSize?.photo?.id
            targetFullFileId = bestSize?.photo?.id

            bestSize?.photo?.let { p ->
                val pPath = p.local?.path
                if (p.local?.isDownloadingCompleted == true && !pPath.isNullOrBlank()) {
                    tdManager?.registerDownloadedFile(p.id, pPath)
                }
            }
            thumbSize?.photo?.let { p ->
                val pPath = p.local?.path
                if (p.local?.isDownloadingCompleted == true && !pPath.isNullOrBlank()) {
                    tdManager?.registerDownloadedFile(p.id, pPath)
                }
            }

            if (c.caption.text.isNotBlank()) c.caption.text else "🖼️ Фотография"
        }
        is TdApi.MessageVideo -> {
            mType = MessageMediaType.VIDEO
            formattedEntities = c.caption.entities
            targetFileId = c.video.thumbnail?.file?.id ?: c.video.video.id
            targetFullFileId = c.video.video.id

            c.video.thumbnail?.file?.let { p ->
                val pPath = p.local?.path
                if (p.local?.isDownloadingCompleted == true && !pPath.isNullOrBlank()) {
                    tdManager?.registerDownloadedFile(p.id, pPath)
                }
            }
            c.video.video?.let { p ->
                val pPath = p.local?.path
                if (p.local?.isDownloadingCompleted == true && !pPath.isNullOrBlank()) {
                    tdManager?.registerDownloadedFile(p.id, pPath)
                }
            }

            val durationSec = c.video.duration
            val mins = durationSec / 60
            val secs = durationSec % 60
            mInfo = String.format("%02d:%02d", mins, secs)
            if (c.caption.text.isNotBlank()) c.caption.text else "📹 Видео ($mInfo)"
        }
        is TdApi.MessageSticker -> {
            mType = MessageMediaType.STICKER
            targetFileId = c.sticker.thumbnail?.file?.id ?: c.sticker.sticker.id
            targetFullFileId = c.sticker.sticker.id

            c.sticker.thumbnail?.file?.let { p ->
                val pPath = p.local?.path
                if (p.local?.isDownloadingCompleted == true && !pPath.isNullOrBlank()) {
                    tdManager?.registerDownloadedFile(p.id, pPath)
                }
            }
            c.sticker.sticker?.let { p ->
                val pPath = p.local?.path
                if (p.local?.isDownloadingCompleted == true && !pPath.isNullOrBlank()) {
                    tdManager?.registerDownloadedFile(p.id, pPath)
                }
            }

            mInfo = c.sticker.emoji
            "Стикер $mInfo"
        }
        is TdApi.MessageAudio -> {
            mType = MessageMediaType.AUDIO
            formattedEntities = c.caption.entities
            targetFileId = c.audio.audio.id
            targetFullFileId = c.audio.audio.id

            c.audio.audio?.let { p ->
                val pPath = p.local?.path
                if (p.local?.isDownloadingCompleted == true && !pPath.isNullOrBlank()) {
                    tdManager?.registerDownloadedFile(p.id, pPath)
                }
            }

            val title = if (c.audio.title.isNotBlank()) c.audio.title else c.audio.fileName
            val performer = if (c.audio.performer.isNotBlank()) "${c.audio.performer} - " else ""
            val durationSec = c.audio.duration
            mInfo = String.format("%02d:%02d", durationSec / 60, durationSec % 60)
            "🎵 $performer$title ($mInfo)"
        }
        is TdApi.MessageVoiceNote -> {
            mType = MessageMediaType.VOICE
            formattedEntities = c.caption.entities
            targetFileId = c.voiceNote.voice.id
            targetFullFileId = c.voiceNote.voice.id

            c.voiceNote.voice?.let { p ->
                val pPath = p.local?.path
                if (p.local?.isDownloadingCompleted == true && !pPath.isNullOrBlank()) {
                    tdManager?.registerDownloadedFile(p.id, pPath)
                }
            }

            val durationSec = c.voiceNote.duration
            mInfo = String.format("%02d:%02d", durationSec / 60, durationSec % 60)
            "🎤 Голосовое сообщение ($mInfo)"
        }
        is TdApi.MessageDocument -> {
            mType = MessageMediaType.DOCUMENT
            formattedEntities = c.caption.entities
            targetFileId = c.document.thumbnail?.file?.id ?: c.document.document.id
            targetFullFileId = c.document.document.id

            c.document.document?.let { p ->
                val pPath = p.local?.path
                if (p.local?.isDownloadingCompleted == true && !pPath.isNullOrBlank()) {
                    tdManager?.registerDownloadedFile(p.id, pPath)
                }
            }

            val name = c.document.fileName
            if (c.caption.text.isNotBlank()) "📄 $name • ${c.caption.text}" else "📄 Документ: $name"
        }
        is TdApi.MessageAnimation -> {
            mType = MessageMediaType.VIDEO
            formattedEntities = c.caption.entities
            targetFileId = c.animation.thumbnail?.file?.id ?: c.animation.animation.id
            targetFullFileId = c.animation.animation.id
            if (c.caption.text.isNotBlank()) "GIF: ${c.caption.text}" else "GIF анимация"
        }
        is TdApi.MessageVideoNote -> {
            mType = MessageMediaType.VIDEO
            targetFileId = c.videoNote.thumbnail?.file?.id ?: c.videoNote.video.id
            targetFullFileId = c.videoNote.video.id
            val durationSec = c.videoNote.duration
            val mins = durationSec / 60
            val secs = durationSec % 60
            mInfo = String.format("%02d:%02d", mins, secs)
            "📹 Видеосообщение ($mInfo)"
        }
        is TdApi.MessageContact -> "Контакт: ${c.contact.firstName} ${c.contact.lastName}"
        is TdApi.MessageLocation -> "Геолокация"
        is TdApi.MessagePoll -> "Опрос: ${c.poll.question}"
        is TdApi.MessagePinMessage -> "Закреплено сообщение"
        is TdApi.MessageChatJoinByLink -> "Пользователь присоединился по ссылке"
        is TdApi.MessageBasicGroupChatCreate -> "Создана группа"
        is TdApi.MessageSupergroupChatCreate -> "Создана супергруппа или канал"
        else -> "Сообщение"
    }

    val finalText = contentText.ifBlank { "Сообщение" }
    val extractedLinks = LinkExtractor.extractLinks(finalText, formattedEntities)

    return UiMessage(
        id = this.id,
        senderName = if (this.isOutgoing) "Вы" else senderTitle,
        text = finalText,
        time = timeFormatted,
        isOutgoing = this.isOutgoing,
        mediaType = mType,
        fileId = targetFileId,
        fullFileId = targetFullFileId,
        mediaInfo = mInfo,
        links = extractedLinks
    )
}

/**
 * 10-Foot UI экран чтения канала/чата для Android TV:
 * 1. Сверхконтрастное выделение активного сообщения (рамка 4.dp бирюзового цвета + яркая плашка [▶ ВЫБРАНО (ОК)] + акцентная заливка).
 * 2. Модальное окно длинного сообщения с возможностью плавного скролла D-Pad стрелками пульта (Вверх/Вниз) и кнопками управления.
 * 3. Поддержка сохранения и восстановления фокуса при возврате в список чатов.
 * 4. Загрузка и просмотр медиа (полноэкранные фото, видеопроигрыватель, аудиоплеер, стикеры) с учетом пользовательских настроек автозагрузки.
 * 5. Яркое, контрастное выделение профиля и названия чата в шапке при наведении курсора пульта.
 * 6. Открытие клавиатуры строго по нажатию кнопки ОК.
 * 7. Полная обработка внутренних (@username, t.me/..., приглашения, посты) и внешних (https://...) ссылок в сообщениях и профилях.
 */
@Composable
fun TvChatDetailScreen(
    chatId: Long,
    chatTitle: String,
    onBack: () -> Unit,
    onOpenChat: (chatId: Long, chatTitle: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val tdLibManager = remember { TdLibManager.getInstance() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val downloadedFiles by tdLibManager.fileDownloadedState.collectAsState()
    val isMediaAutoDownloadEnabled by tdLibManager.isMediaAutoDownloadEnabled.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    var messagesList by remember { mutableStateOf<List<UiMessage>>(emptyList()) }
    var isLoadingHistory by remember { mutableStateOf(true) }
    var isLoadingMoreOlder by remember { mutableStateOf(false) }
    var hasMoreOlderMessages by remember { mutableStateOf(true) }

    // Проверка прав на отправку сообщений и возможность подписки на публичный канал
    var canSendMessages by remember { mutableStateOf(false) }
    var canJoinCurrentChat by remember { mutableStateOf(false) }

    // Профиль чата
    var showProfileDialog by remember { mutableStateOf(false) }
    var profileDetails by remember { mutableStateOf<TdLibManager.ChatProfileDetails?>(null) }
    var isLoadingProfile by remember { mutableStateOf(false) }

    // Просмотр длинного сообщения
    var selectedFullMessage by remember { mutableStateOf<UiMessage?>(null) }

    // Просмотр фото на весь экран ТВ в оригинальном качестве
    var selectedPhotoMessage by remember { mutableStateOf<UiMessage?>(null) }

    // Просмотр/воспроизведение видео через ExoPlayer
    var selectedVideoMessage by remember { mutableStateOf<UiMessage?>(null) }

    // Встроенный ТВ-браузер для внешних веб-ссылок
    var activeExternalWebUrl by remember { mutableStateOf<String?>(null) }

    // Меню действий с сообщением (Ссылки, Ответить, Переслать, Удалить)
    var actionTargetMessage by remember { mutableStateOf<UiMessage?>(null) }
    var replyingToMessage by remember { mutableStateOf<UiMessage?>(null) }
    var forwardingMessage by remember { mutableStateOf<UiMessage?>(null) }
    var deletingMessage by remember { mutableStateOf<UiMessage?>(null) }
    var toastNotification by remember { mutableStateOf<String?>(null) }

    // Обработчик клика по любой внутренней или внешней ссылке
    fun handleLinkClick(rawUrl: String) {
        val target = rawUrl.trim()
        if (target.isEmpty()) return
        scope.launch {
            toastNotification = "Переход по ссылке: $target..."
            when (val res = tdLibManager.resolveAndHandleLink(target)) {
                is TdLibManager.ResolvedLinkAction.OpenChat -> {
                    actionTargetMessage = null
                    selectedFullMessage = null
                    showProfileDialog = false
                    toastNotification = null
                    onOpenChat(res.chatId, res.chatTitle)
                }
                is TdLibManager.ResolvedLinkAction.ProxyImported -> {
                    toastNotification = res.message
                }
                is TdLibManager.ResolvedLinkAction.OpenExternalWeb -> {
                    actionTargetMessage = null
                    toastNotification = null
                    activeExternalWebUrl = res.url
                }
                is TdLibManager.ResolvedLinkAction.Error -> {
                    toastNotification = res.message
                }
            }
        }
    }

    // Воспроизведение аудио/голосовых сообщений
    var playingAudioMsgId by remember { mutableStateOf<Long?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (_: Exception) {}
        }
    }

    // Перехват кнопки НАЗАД на пульте ДУ: закрывает активные диалоги перед выходом из экрана
    BackHandler(
        enabled = activeExternalWebUrl != null || selectedPhotoMessage != null ||
                selectedVideoMessage != null || selectedFullMessage != null ||
                showProfileDialog || actionTargetMessage != null ||
                forwardingMessage != null || deletingMessage != null ||
                replyingToMessage != null
    ) {
        when {
            activeExternalWebUrl != null -> activeExternalWebUrl = null
            selectedPhotoMessage != null -> selectedPhotoMessage = null
            selectedVideoMessage != null -> selectedVideoMessage = null
            selectedFullMessage != null -> selectedFullMessage = null
            actionTargetMessage != null -> actionTargetMessage = null
            forwardingMessage != null -> forwardingMessage = null
            deletingMessage != null -> deletingMessage = null
            showProfileDialog -> showProfileDialog = false
            replyingToMessage != null -> replyingToMessage = null
        }
    }

    // Ввод текста
    var inputText by remember { mutableStateOf("") }
    var isInputEditing by remember { mutableStateOf(false) }
    var isInputBoxFocused by remember { mutableStateOf(false) }
    var isSendFocused by remember { mutableStateOf(false) }
    val textFieldFocusRequester = remember { FocusRequester() }

    val listState = rememberLazyListState()

    // Получаем реальный объект чата для аватара и прав
    val chatObj = remember(chatId, tdLibManager.chatsList) {
        try {
            tdLibManager.chatsMap[chatId]
        } catch (_: Exception) {
            null
        }
    }
    val chatAvatarPath = remember(chatObj, downloadedFiles) {
        tdLibManager.getChatPhotoPath(chatObj)
    }

    // Загрузка прав на отправку сообщений и статуса подписки
    LaunchedEffect(chatId, chatObj) {
        canSendMessages = tdLibManager.canSendMessagesInChat(chatObj)
        canJoinCurrentChat = tdLibManager.canJoinChat(chatObj)
    }

    // Загрузка более старых сообщений (пагинация вверх с официальным лимитом 30 сообщений)
    fun loadOlderMessages() {
        if (isLoadingMoreOlder || !hasMoreOlderMessages || messagesList.isEmpty()) return
        val oldestMessageId = messagesList.firstOrNull()?.id ?: return

        isLoadingMoreOlder = true
        scope.launch {
            try {
                val olderResult = tdLibManager.getChatHistory(
                    chatId = chatId,
                    fromMessageId = oldestMessageId,
                    offset = 0,
                    limit = 30
                )
                val rawOlder = olderResult.getOrNull().orEmpty()

                if (rawOlder.isEmpty()) {
                    hasMoreOlderMessages = false
                } else {
                    val existingIds = messagesList.map { it.id }.toHashSet()
                    val newOldBatch = rawOlder
                        .filterNot { it.id in existingIds }
                        .reversed()
                        .map { msg ->
                            val senderName = tdLibManager.getSenderName(msg.senderId)
                            msg.toUiMessage(senderName)
                        }

                    if (newOldBatch.isEmpty()) {
                        hasMoreOlderMessages = false
                    } else {
                        val addedCount = newOldBatch.size
                        val currentFirstVisible = listState.firstVisibleItemIndex
                        val currentFirstOffset = listState.firstVisibleItemScrollOffset

                        messagesList = newOldBatch + messagesList
                        listState.scrollToItem(currentFirstVisible + addedCount, currentFirstOffset)

                        // Инициируем скачивание медиа если включена автозагрузка
                        if (isMediaAutoDownloadEnabled) {
                            newOldBatch.forEach { m ->
                                m.fileId?.let { fid -> tdLibManager.downloadMediaFile(fid) }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("TvChatDetail", "Failed to load older messages: ${e.message}")
            } finally {
                isLoadingMoreOlder = false
            }
        }
    }

    // Первоначальное открытие чата и загрузка истории сообщений
    LaunchedEffect(chatId) {
        isLoadingHistory = true
        tdLibManager.openChat(chatId)

        try {
            val historyResult = tdLibManager.getChatHistory(chatId = chatId, fromMessageId = 0, offset = 0, limit = 30)
            val rawMessages = historyResult.getOrNull().orEmpty()

            if (rawMessages.isEmpty()) {
                hasMoreOlderMessages = false
            }

            val converted = rawMessages.reversed().map { msg ->
                val senderName = tdLibManager.getSenderName(msg.senderId)
                msg.toUiMessage(senderName)
            }
            messagesList = converted

            // Инициируем автозагрузку медиафайлов
            if (isMediaAutoDownloadEnabled) {
                converted.forEach { m ->
                    m.fileId?.let { fid -> tdLibManager.downloadMediaFile(fid) }
                }
            }

            if (rawMessages.isNotEmpty()) {
                val ids = rawMessages.map { it.id }.toLongArray()
                tdLibManager.viewMessages(chatId, ids)
            }

            if (converted.isNotEmpty()) {
                listState.scrollToItem(converted.size - 1)
            }
        } catch (e: Exception) {
            Log.e("TvChatDetail", "Initial history load failed: ${e.message}")
        } finally {
            isLoadingHistory = false
        }
    }

    // Подгрузка при прокрутке вверх
    val shouldLoadMoreOlder by remember {
        derivedStateOf {
            val firstVisible = listState.firstVisibleItemIndex
            !isLoadingHistory && !isLoadingMoreOlder && hasMoreOlderMessages && firstVisible <= 2 && messagesList.isNotEmpty()
        }
    }

    LaunchedEffect(shouldLoadMoreOlder) {
        if (shouldLoadMoreOlder) {
            loadOlderMessages()
        }
    }

    // Мгновенная синхронизация входящих и исходящих сообщений в реальном времени
    LaunchedEffect(chatId) {
        tdLibManager.updates.collect { update ->
            try {
                when (update) {
                    is TdApi.UpdateNewMessage -> {
                        if (update.message.chatId == chatId) {
                            val senderName = tdLibManager.getSenderName(update.message.senderId)
                            val uiMsg = update.message.toUiMessage(senderName)

                            val exists = messagesList.any { it.id == uiMsg.id }
                            if (!exists) {
                                val isScrolledNearBottom = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 >= (messagesList.size - 2)
                                messagesList = messagesList + uiMsg

                                if (isMediaAutoDownloadEnabled && uiMsg.fileId != null) {
                                    tdLibManager.downloadMediaFile(uiMsg.fileId)
                                }

                                if (isScrolledNearBottom || uiMsg.isOutgoing) {
                                    scope.launch {
                                        if (messagesList.isNotEmpty()) {
                                            listState.animateScrollToItem(messagesList.size - 1)
                                        }
                                    }
                                }
                            }
                            tdLibManager.viewMessages(chatId, longArrayOf(update.message.id))
                        }
                    }
                    is TdApi.UpdateMessageContent -> {
                        if (update.chatId == chatId) {
                            messagesList = messagesList.map { existing ->
                                if (existing.id == update.messageId) {
                                    when (val c = update.newContent) {
                                        is TdApi.MessageText -> existing.copy(
                                            text = c.text.text,
                                            links = LinkExtractor.extractLinks(c.text.text, c.text.entities)
                                        )
                                        is TdApi.MessagePhoto -> {
                                            val newText = if (c.caption.text.isNotBlank()) c.caption.text else "🖼️ Фотография"
                                            existing.copy(
                                                text = newText,
                                                links = LinkExtractor.extractLinks(c.caption.text, c.caption.entities)
                                            )
                                        }
                                        is TdApi.MessageVideo -> {
                                            val newText = if (c.caption.text.isNotBlank()) c.caption.text else "📹 Видео"
                                            existing.copy(
                                                text = newText,
                                                links = LinkExtractor.extractLinks(c.caption.text, c.caption.entities)
                                            )
                                        }
                                        else -> existing
                                    }
                                } else existing
                            }
                        }
                    }
                    is TdApi.UpdateDeleteMessages -> {
                        if (update.chatId == chatId) {
                            val deletedIds = update.messageIds.toSet()
                            messagesList = messagesList.filterNot { it.id in deletedIds }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("TvChatDetail", "Update handle warning: ${e.message}")
            }
        }
    }

    DisposableEffect(chatId) {
        onDispose {
            tdLibManager.closeChat(chatId)
        }
    }

    // Отправка сообщений (с поддержкой ответа на выбранное сообщение)
    fun sendMessage(textToSend: String) {
        val trimmed = textToSend.trim()
        if (trimmed.isEmpty()) return

        val replyMsgId = replyingToMessage?.id
        scope.launch {
            val result = tdLibManager.sendTextMessage(chatId, trimmed, replyToMessageId = replyMsgId)
            if (result.isSuccess) {
                inputText = ""
                replyingToMessage = null
                isInputEditing = false
                keyboardController?.hide()
            }
        }
    }

    // Воспроизведение аудиофайлов
    fun togglePlayAudio(msgId: Long, filePath: String) {
        if (playingAudioMsgId == msgId) {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (_: Exception) {}
            mediaPlayer = null
            playingAudioMsgId = null
        } else {
            try {
                mediaPlayer?.release()
                val mp = MediaPlayer().apply {
                    setDataSource(filePath)
                    prepare()
                    setOnCompletionListener {
                        playingAudioMsgId = null
                    }
                    start()
                }
                mediaPlayer = mp
                playingAudioMsgId = msgId
            } catch (e: Exception) {
                Log.e("TvChatDetail", "Audio playback failed: ${e.message}")
                playingAudioMsgId = null
            }
        }
    }

    // Загрузка деталей профиля чата
    fun openProfile() {
        showProfileDialog = true
        isLoadingProfile = true
        scope.launch {
            val details = tdLibManager.getChatProfileDetails(chatObj)
            profileDetails = details
            isLoadingProfile = false
        }
    }

    // Быстрые шаблоны ответов
    val quickPhrases = remember {
        listOf(
            "👍 Понял, спасибо!",
            "👋 Привет! Смотрю на TV",
            "🎬 Отличное видео",
            "📺 Смотрю через Cybermasters TG TV",
            "👌 Буду позже",
            "🔥 Крутой контент!"
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090B10))
            .padding(horizontal = 40.dp, vertical = 20.dp)
    ) {
        // Шапка экрана чата
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                var isBackFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBackFocused) Color(0xFF38BDF8) else Color(0xFF131722)
                    ),
                    modifier = Modifier
                        .onFocusChanged { isBackFocused = it.isFocused }
                        .border(
                            width = if (isBackFocused) 3.5.dp else 1.dp,
                            color = if (isBackFocused) Color.White else Color(0xFF242A3A),
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Назад к списку чатов",
                        tint = if (isBackFocused) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Назад",
                        fontSize = 13.sp,
                        color = if (isBackFocused) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Кнопка профиля и названия чата с контрастным выделением фокуса (Пункт 5)
                var isProfileBtnFocused by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isProfileBtnFocused) Color(0xFF0284C7) else Color(0xFF131722))
                        .border(
                            width = if (isProfileBtnFocused) 4.dp else 1.dp,
                            color = if (isProfileBtnFocused) Color(0xFF38BDF8) else Color(0xFF253047),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .onFocusChanged { isProfileBtnFocused = it.isFocused }
                        .clickable { openProfile() }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    val avatarBitmap = remember(chatAvatarPath) {
                        if (!chatAvatarPath.isNullOrBlank()) {
                            try {
                                val f = File(chatAvatarPath)
                                if (f.exists() && f.length() > 0) {
                                    BitmapFactory.decodeFile(chatAvatarPath)?.asImageBitmap()
                                } else null
                            } catch (_: Throwable) {
                                null
                            }
                        } else null
                    }

                    if (avatarBitmap != null) {
                        Image(
                            bitmap = avatarBitmap,
                            contentDescription = chatTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .border(
                                    width = if (isProfileBtnFocused) 2.5.dp else 1.dp,
                                    color = if (isProfileBtnFocused) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                        )
                    } else {
                        val firstLetter = chatTitle.trim().take(1).uppercase().ifBlank { "#" }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isProfileBtnFocused) Color(0xFF38BDF8) else Color(0xFF0284C7))
                        ) {
                            Text(
                                text = firstLetter,
                                color = if (isProfileBtnFocused) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = chatTitle,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isProfileBtnFocused) {
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF38BDF8), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "▶ ПРОФИЛЬ (ОК)",
                                        fontSize = 11.sp,
                                        color = Color.Black,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (isProfileBtnFocused) "Нажмите ОК на пульте ДУ для открытия информации и ссылок" else "Нажмите ОК для просмотра описания и ссылок чата",
                            fontSize = 11.sp,
                            color = if (isProfileBtnFocused) Color(0xFFBAE6FD) else Color(0xFF94A3B8)
                        )
                    }
                }
            }

            // Правый блок шапки: кнопка подписки (для новых каналов) и быстрого перехода к вводу сообщения
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (canJoinCurrentChat) {
                    var isJoinFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = {
                            scope.launch {
                                val res = tdLibManager.joinChat(chatId)
                                if (res.isSuccess) {
                                    canJoinCurrentChat = false
                                    toastNotification = "✓ Вы подписались на $chatTitle"
                                } else {
                                    toastNotification = "Не удалось подписаться: ${res.exceptionOrNull()?.message ?: ""}"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isJoinFocused) Color(0xFF10B981) else Color(0xFF064E3B)
                        ),
                        modifier = Modifier
                            .onFocusChanged { isJoinFocused = it.isFocused }
                            .border(
                                width = if (isJoinFocused) 3.dp else 1.5.dp,
                                color = if (isJoinFocused) Color.White else Color(0xFF10B981),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = if (isJoinFocused) Color.Black else Color(0xFF34D399),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Подписаться",
                            fontSize = 13.sp,
                            color = if (isJoinFocused) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (canSendMessages) {
                    var isWriteFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = {
                            isInputEditing = true
                            scope.launch {
                                delay(100)
                                textFieldFocusRequester.requestFocus()
                                keyboardController?.show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isWriteFocused) Color(0xFF38BDF8) else Color(0xFF0F2338)
                        ),
                        modifier = Modifier
                            .onFocusChanged { isWriteFocused = it.isFocused }
                            .border(
                                width = if (isWriteFocused) 3.dp else 1.dp,
                                color = if (isWriteFocused) Color.White else Color(0xFF1E3A5F),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            tint = if (isWriteFocused) Color.Black else Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Написать",
                            fontSize = 13.sp,
                            color = if (isWriteFocused) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Область сообщений с двунаправленной навигацией по элементам D-Pad
        if (isLoadingHistory && messagesList.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Загрузка сообщений из Telegram...", color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else if (messagesList.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "В этом диалоге пока нет сообщений",
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                reverseLayout = false,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Индикатор подгрузки более старых сообщений сверху
                if (hasMoreOlderMessages) {
                    item(key = "older_loader") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoadingMoreOlder) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color(0xFF38BDF8),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Загрузка предыдущих сообщений...", color = Color.Gray, fontSize = 12.sp)
                                }
                            } else {
                                var isLoaderFocused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isLoaderFocused) Color(0xFF38BDF8) else Color(0xFF131826))
                                        .border(
                                            width = if (isLoaderFocused) 2.5.dp else 1.dp,
                                            color = if (isLoaderFocused) Color.White else Color(0xFF222B3D),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .onFocusChanged { isLoaderFocused = it.isFocused }
                                        .clickable { loadOlderMessages() }
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        "↑ Загрузить предыдущие сообщения...",
                                        color = if (isLoaderFocused) Color.Black else Color(0xFF38BDF8),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                items(messagesList, key = { it.id }) { msg ->
                    var isMsgFocused by remember { mutableStateOf(false) }
                    val alignment = if (msg.isOutgoing) Alignment.End else Alignment.Start

                    // Сверхконтрастное выделение активного сообщения (Пункт 1)
                    val bgBubbleColor = if (isMsgFocused) {
                        if (msg.isOutgoing) Color(0xFF0284C7) else Color(0xFF1E293B)
                    } else {
                        if (msg.isOutgoing) Color(0xFF0C2744) else Color(0xFF111420)
                    }

                    val isLongMessage = msg.text.length > 250

                    // Локальный путь к медиафайлу (превью)
                    val mediaLocalPath = remember(msg.fileId, downloadedFiles) {
                        tdLibManager.getFilePath(msg.fileId)
                    }

                    // Локальный путь к полному медиафайлу (для видео и полноэкранного фото)
                    val fullMediaLocalPath = remember(msg.fullFileId, downloadedFiles) {
                        tdLibManager.getFilePath(msg.fullFileId)
                    }

                    // Автозагрузка медиа при отображении сообщения
                    LaunchedEffect(msg.fileId, isMediaAutoDownloadEnabled) {
                        if (isMediaAutoDownloadEnabled && msg.fileId != null && mediaLocalPath.isNullOrBlank()) {
                            tdLibManager.downloadMediaFile(msg.fileId)
                        }
                    }

                    Column(
                        horizontalAlignment = alignment,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val maxBubbleWidth = when (msg.mediaType) {
                            MessageMediaType.PHOTO, MessageMediaType.VIDEO -> 420.dp
                            MessageMediaType.STICKER -> 260.dp
                            else -> 680.dp
                        }
                        Box(
                            modifier = Modifier
                                .widthIn(max = maxBubbleWidth)
                                .clip(RoundedCornerShape(16.dp))
                                .background(bgBubbleColor)
                                .border(
                                    width = if (isMsgFocused) 4.dp else 1.dp,
                                    color = if (isMsgFocused) Color(0xFF38BDF8) else Color(0xFF22293A),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .onFocusChanged { isMsgFocused = it.isFocused }
                                .clickable {
                                    if (msg.mediaType == MessageMediaType.AUDIO || msg.mediaType == MessageMediaType.VOICE) {
                                        if (!mediaLocalPath.isNullOrBlank()) {
                                            togglePlayAudio(msg.id, mediaLocalPath)
                                        } else {
                                            msg.fileId?.let { fid -> tdLibManager.downloadMediaFile(fid, force = true) }
                                        }
                                    } else {
                                        actionTargetMessage = msg
                                    }
                                }
                                .padding(horizontal = 18.dp, vertical = 14.dp)
                        ) {
                            Column {
                                // Заголовок пузыря сообщения: имя отправителя и метка фокуса
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!msg.isOutgoing) {
                                        Text(
                                            text = msg.senderName,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isMsgFocused) Color(0xFF7DD3FC) else Color(0xFF38BDF8)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.width(1.dp))
                                    }

                                    if (isMsgFocused) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFF38BDF8), RoundedCornerShape(6.dp))
                                                    .clickable { actionTargetMessage = msg }
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "⚡ ДЕЙСТВИЯ (ОК)",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color.Black
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Отображение загруженного медиа (Пункт 4)
                                if (msg.mediaType != MessageMediaType.NONE) {
                                    if (!mediaLocalPath.isNullOrBlank()) {
                                        val mediaBitmap = remember(mediaLocalPath) {
                                            try {
                                                val f = File(mediaLocalPath)
                                                if (f.exists() && f.length() > 0) {
                                                    BitmapFactory.decodeFile(mediaLocalPath)?.asImageBitmap()
                                                } else null
                                            } catch (_: Throwable) {
                                                null
                                            }
                                        }

                                        if (mediaBitmap != null) {
                                            val imageModifier = when (msg.mediaType) {
                                                MessageMediaType.STICKER -> Modifier
                                                    .size(140.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                else -> Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 160.dp, max = 240.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable {
                                                        if (msg.mediaType == MessageMediaType.PHOTO) {
                                                            msg.fullFileId?.let { fid -> tdLibManager.downloadMediaFile(fid, priority = 32, force = true) }
                                                            selectedPhotoMessage = msg
                                                        } else if (msg.mediaType == MessageMediaType.VIDEO) {
                                                            msg.fullFileId?.let { fid -> tdLibManager.downloadMediaFile(fid, priority = 32, force = true) }
                                                            selectedVideoMessage = msg
                                                        }
                                                    }
                                            }

                                            Box(modifier = imageModifier) {
                                                Image(
                                                    bitmap = mediaBitmap,
                                                    contentDescription = msg.text,
                                                    contentScale = if (msg.mediaType == MessageMediaType.STICKER) ContentScale.Fit else ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                if (msg.mediaType == MessageMediaType.PHOTO || msg.mediaType == MessageMediaType.VIDEO) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.BottomEnd)
                                                            .padding(8.dp)
                                                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = if (msg.mediaType == MessageMediaType.PHOTO) "🔍 HD Фото (ОК)" else "▶ Видео ExoPlayer (ОК)",
                                                            fontSize = 10.sp,
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                        } else if (msg.mediaType == MessageMediaType.STICKER) {
                                            // Fallback для анимированных/векторных стикеров (TGS/WebM)
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                                    .padding(12.dp)
                                            ) {
                                                Text(text = msg.mediaInfo.ifBlank { "🌟" }, fontSize = 32.sp)
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(text = "Стикер Telegram", color = Color.White, fontSize = 13.sp)
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    } else {
                                        // Медиа еще не скачано: информационный блок с кнопкой ручной загрузки
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                        ) {
                                            Icon(
                                                when (msg.mediaType) {
                                                    MessageMediaType.PHOTO -> Icons.Default.Face
                                                    MessageMediaType.VIDEO -> Icons.Default.PlayArrow
                                                    MessageMediaType.AUDIO -> Icons.Default.Notifications
                                                    MessageMediaType.VOICE -> Icons.Default.Call
                                                    else -> Icons.Default.Info
                                                },
                                                contentDescription = null,
                                                tint = Color(0xFF38BDF8),
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = when (msg.mediaType) {
                                                        MessageMediaType.PHOTO -> "Фотография"
                                                        MessageMediaType.VIDEO -> "Видеозапись (${msg.mediaInfo})"
                                                        MessageMediaType.STICKER -> "Стикер ${msg.mediaInfo}"
                                                        MessageMediaType.AUDIO -> "Аудиозапись (${msg.mediaInfo})"
                                                        MessageMediaType.VOICE -> "Голосовое (${msg.mediaInfo})"
                                                        else -> "Медиафайл"
                                                    },
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = if (isMediaAutoDownloadEnabled) "Загружается..." else "Автозагрузка выключена (нажмите ОК для скачивания)",
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                            if (msg.fileId != null) {
                                                Button(
                                                    onClick = { tdLibManager.downloadMediaFile(msg.fileId, force = true) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(30.dp)
                                                ) {
                                                    Text("Скачать (ОК)", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    // Панель аудио/голосового воспроизведения
                                    if ((msg.mediaType == MessageMediaType.AUDIO || msg.mediaType == MessageMediaType.VOICE) && !mediaLocalPath.isNullOrBlank()) {
                                        val isPlaying = (playingAudioMsgId == msg.id)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(if (isPlaying) Color(0xFF0369A1) else Color(0xFF0F172A), RoundedCornerShape(10.dp))
                                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = if (isPlaying) "Воспроизводится..." else "Готово к прослушиванию",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Нажмите ОК для ${if (isPlaying) "остановки" else "воспроизведения"}",
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }

                                val highlightedText = remember(msg.text) {
                                    LinkExtractor.buildHighlightedText(msg.text)
                                }

                                Text(
                                    text = highlightedText,
                                    fontSize = 15.sp,
                                    color = Color.White,
                                    lineHeight = 22.sp,
                                    maxLines = 8,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Интерактивные кнопки ссылок в сообщении (доступны для D-Pad навигации и в меню действий)
                                if (msg.links.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        msg.links.take(4).forEach { linkItem ->
                                            var isLinkChipFocused by remember { mutableStateOf(false) }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (isLinkChipFocused) Color(0xFF38BDF8)
                                                        else if (linkItem.isInternalTelegram) Color(0xFF0B2942)
                                                        else Color(0xFF162033)
                                                    )
                                                    .border(
                                                        width = if (isLinkChipFocused) 2.5.dp else 1.dp,
                                                        color = if (isLinkChipFocused) Color.White else Color(0xFF0284C7),
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                    .onFocusChanged { isLinkChipFocused = it.isFocused }
                                                    .clickable { handleLinkClick(linkItem.url) }
                                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                                            ) {
                                                Text(
                                                    text = if (linkItem.isInternalTelegram) "✈️" else "🌐",
                                                    fontSize = 13.sp
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = linkItem.displayLabel,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isLinkChipFocused) Color.Black else Color(0xFF7DD3FC),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (linkItem.displayLabel != linkItem.url) {
                                                        Text(
                                                            text = linkItem.url,
                                                            fontSize = 10.sp,
                                                            color = if (isLinkChipFocused) Color(0xFF0F172A) else Color(0xFF94A3B8),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (linkItem.isInternalTelegram) "Открыть в TG (ОК)" else "Открыть (ОК)",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (isLinkChipFocused) Color.Black else Color(0xFF38BDF8)
                                                )
                                            }
                                        }
                                        if (msg.links.size > 4) {
                                            Text(
                                                text = "+ ещё ссылок (${msg.links.size - 4}) — нажмите ОК на сообщении для полного списка",
                                                fontSize = 11.sp,
                                                color = Color(0xFF7DD3FC)
                                            )
                                        }
                                    }
                                }

                                if (isLongMessage || msg.mediaType == MessageMediaType.PHOTO || msg.mediaType == MessageMediaType.VIDEO) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = when {
                                            msg.mediaType == MessageMediaType.PHOTO -> "[Нажмите ОК для просмотра фото на весь экран]"
                                            msg.mediaType == MessageMediaType.VIDEO -> "[Нажмите ОК для воспроизведения видео]"
                                            else -> "[Нажмите ОК для чтения сообщения целиком]"
                                        },
                                        fontSize = 12.sp,
                                        color = if (isMsgFocused) Color(0xFFBAE6FD) else Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = msg.time,
                                        fontSize = 11.sp,
                                        color = if (isMsgFocused) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.5f)
                                    )
                                    if (msg.isOutgoing) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Отправлено",
                                            tint = if (isMsgFocused) Color(0xFF7DD3FC) else Color(0xFF38BDF8),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Блок отправки сообщений отображается ТОЛЬКО если у пользователя есть права на отправку
        if (canSendMessages) {
            // Баннер ответа на сообщение (если выбран режим ответа)
            replyingToMessage?.let { repMsg ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F1E36))
                        .border(1.5.dp, Color(0xFF38BDF8), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Reply,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ответ на сообщение: ${repMsg.senderName}",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = repMsg.text,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { replyingToMessage = null },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Отменить ответ",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Быстрые шаблоны ответов
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(quickPhrases) { phrase ->
                    var isPhraseFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isPhraseFocused) Color(0xFF0284C7) else Color(0xFF191C29))
                            .border(
                                width = if (isPhraseFocused) 2.5.dp else 1.dp,
                                color = if (isPhraseFocused) Color.White else Color(0xFF2C3147),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .onFocusChanged { isPhraseFocused = it.isFocused }
                            .clickable { sendMessage(phrase) }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = phrase,
                            color = if (isPhraseFocused) Color.White else Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = if (isPhraseFocused) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Поле ввода текста: виртуальная клавиатура открывается ИСКЛЮЧИТЕЛЬНО по клику ОК
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF131520))
                        .border(
                            width = if (isInputBoxFocused) 3.5.dp else 1.dp,
                            color = if (isInputBoxFocused) Color(0xFF38BDF8) else Color(0xFF262A3C),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .onFocusChanged { isInputBoxFocused = it.isFocused }
                        .clickable {
                            isInputEditing = true
                            scope.launch {
                                delay(100)
                                textFieldFocusRequester.requestFocus()
                                keyboardController?.show()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (isInputEditing) {
                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            cursorBrush = SolidColor(Color(0xFF38BDF8)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(textFieldFocusRequester)
                        )
                    } else {
                        Text(
                            text = if (inputText.isNotBlank()) inputText else "Нажмите ОК для ввода текста клавиатурой или голосом...",
                            color = if (inputText.isNotBlank()) Color.White else Color.Gray,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = { sendMessage(inputText) },
                    modifier = Modifier
                        .onFocusChanged { isSendFocused = it.isFocused }
                        .border(
                            width = if (isSendFocused) 3.dp else 1.dp,
                            color = if (isSendFocused) Color(0xFF38BDF8) else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Text(
                        text = "Отправить",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Модальное окно чтения длинного сообщения с поддержкой скролла пультом (Пункт 2)
    selectedFullMessage?.let { fullMsg ->
        val modalScrollState = rememberScrollState()
        val modalFocusRequester = remember { FocusRequester() }

        LaunchedEffect(fullMsg) {
            delay(150)
            try {
                modalFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }

        Dialog(
            onDismissRequest = { selectedFullMessage = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131722)),
                modifier = Modifier
                    .width(820.dp)
                    .fillMaxHeight(0.85f)
                    .border(2.dp, Color(0xFF38BDF8), RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    // Панель управления и скролла модального окна
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = fullMsg.senderName,
                                color = Color(0xFF38BDF8),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Отправлено: ${fullMsg.time} • Скролл D-Pad (Вверх/Вниз) или кнопками",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var isUpFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = {
                                    scope.launch { modalScrollState.scrollBy(-260f) }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isUpFocused) Color(0xFF38BDF8) else Color(0xFF1E293B)
                                ),
                                modifier = Modifier
                                    .onFocusChanged { isUpFocused = it.isFocused }
                                    .border(
                                        width = if (isUpFocused) 2.5.dp else 1.dp,
                                        color = if (isUpFocused) Color.White else Color(0xFF334155),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                            ) {
                                Text("▲ Вверх", color = if (isUpFocused) Color.Black else Color.White, fontSize = 12.sp)
                            }

                            var isDownFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = {
                                    scope.launch { modalScrollState.scrollBy(260f) }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDownFocused) Color(0xFF38BDF8) else Color(0xFF1E293B)
                                ),
                                modifier = Modifier
                                    .onFocusChanged { isDownFocused = it.isFocused }
                                    .border(
                                        width = if (isDownFocused) 2.5.dp else 1.dp,
                                        color = if (isDownFocused) Color.White else Color(0xFF334155),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                            ) {
                                Text("▼ Вниз", color = if (isDownFocused) Color.Black else Color.White, fontSize = 12.sp)
                            }

                            var isCloseFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = { selectedFullMessage = null },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isCloseFocused) Color(0xFF38BDF8) else Color(0xFF26334D)
                                ),
                                modifier = Modifier
                                    .onFocusChanged { isCloseFocused = it.isFocused }
                                    .border(
                                        width = if (isCloseFocused) 2.5.dp else 1.dp,
                                        color = if (isCloseFocused) Color.White else Color(0xFF3B4D73),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                            ) {
                                Text(
                                    "Закрыть (ОК)",
                                    color = if (isCloseFocused) Color.Black else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = Color(0xFF232D42))
                    Spacer(modifier = Modifier.height(14.dp))

                    // Контейнер с D-Pad фокусом и поддержкой прокрутки с пульта стрелками
                    var isTextBoxFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0C101A))
                            .border(
                                width = if (isTextBoxFocused) 3.5.dp else 1.dp,
                                color = if (isTextBoxFocused) Color(0xFF38BDF8) else Color(0xFF1E2638),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .focusRequester(modalFocusRequester)
                            .onFocusChanged { isTextBoxFocused = it.isFocused }
                            .focusable()
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionDown -> {
                                            scope.launch { modalScrollState.scrollBy(240f) }
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            if (modalScrollState.value > 0) {
                                                scope.launch { modalScrollState.scrollBy(-240f) }
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        Key.PageDown -> {
                                            scope.launch { modalScrollState.scrollBy(600f) }
                                            true
                                        }
                                        Key.PageUp -> {
                                            scope.launch { modalScrollState.scrollBy(-600f) }
                                            true
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                            .verticalScroll(modalScrollState)
                            .padding(22.dp)
                    ) {
                        Column {
                            Text(
                                text = remember(fullMsg.text) { LinkExtractor.buildHighlightedText(fullMsg.text) },
                                color = Color.White,
                                fontSize = 17.sp,
                                lineHeight = 26.sp
                            )

                            if (fullMsg.links.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(18.dp))
                                Divider(color = Color(0xFF1E293B))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Ссылки в сообщении:",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    fullMsg.links.forEach { linkItem ->
                                        var isModalLinkFocused by remember { mutableStateOf(false) }
                                        Button(
                                            onClick = {
                                                selectedFullMessage = null
                                                handleLinkClick(linkItem.url)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isModalLinkFocused) Color(0xFF38BDF8) else Color(0xFF162238)
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .onFocusChanged { isModalLinkFocused = it.isFocused }
                                                .border(
                                                    width = if (isModalLinkFocused) 2.5.dp else 1.dp,
                                                    color = if (isModalLinkFocused) Color.White else Color(0xFF0284C7),
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "${if (linkItem.isInternalTelegram) "✈️" else "🌐"} ${linkItem.displayLabel} (${linkItem.url})",
                                                    color = if (isModalLinkFocused) Color.Black else Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Перейти (ОК)",
                                                    color = if (isModalLinkFocused) Color.Black else Color(0xFF38BDF8),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 1. Полноэкранный просмотр фото на экране ТВ в оригинальном качестве (Пункт 1)
    selectedPhotoMessage?.let { photoMsg ->
        val fullPhotoPath = remember(photoMsg.fullFileId, downloadedFiles) {
            tdLibManager.getFilePath(photoMsg.fullFileId)
        }
        val thumbPhotoPath = remember(photoMsg.fileId, downloadedFiles) {
            tdLibManager.getFilePath(photoMsg.fileId)
        }

        // Запуск немедленной принудительной загрузки оригинала высокого разрешения
        LaunchedEffect(photoMsg.fullFileId) {
            photoMsg.fullFileId?.let { fid ->
                tdLibManager.downloadMediaFile(fid, priority = 32, force = true)
            }
        }

        val displayPhotoPath = fullPhotoPath ?: thumbPhotoPath
        val isOriginalQuality = (fullPhotoPath != null)

        Dialog(
            onDismissRequest = { selectedPhotoMessage = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val photoBitmap = remember(displayPhotoPath) {
                    if (!displayPhotoPath.isNullOrBlank()) {
                        try {
                            BitmapFactory.decodeFile(displayPhotoPath)?.asImageBitmap()
                        } catch (_: Throwable) {
                            null
                        }
                    } else null
                }

                if (photoBitmap != null) {
                    Image(
                        bitmap = photoBitmap,
                        contentDescription = "Фотография в высоком качестве",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(color = Color(0xFF38BDF8))
                }

                // Индикатор оригинального качества фото
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(32.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
                        .border(1.dp, if (isOriginalQuality) Color(0xFF10B981) else Color(0xFFF59E0B), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isOriginalQuality) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("✓ Исходное качество (HD)", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            CircularProgressIndicator(color = Color(0xFFF59E0B), strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Загрузка оригинала в полном разрешении...", color = Color(0xFFFCD34D), fontSize = 12.sp)
                        }
                    }
                }

                // Плавающая кнопка закрытия в правом верхнем углу экрана ТВ
                var isClosePhotoFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(32.dp)
                ) {
                    Button(
                        onClick = { selectedPhotoMessage = null },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isClosePhotoFocused) Color(0xFF38BDF8) else Color(0xCC111827)
                        ),
                        modifier = Modifier
                            .onFocusChanged { isClosePhotoFocused = it.isFocused }
                            .border(
                                width = if (isClosePhotoFocused) 3.dp else 1.dp,
                                color = if (isClosePhotoFocused) Color.White else Color(0x66FFFFFF),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = if (isClosePhotoFocused) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Закрыть (ОК / Назад)",
                            color = if (isClosePhotoFocused) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // 2. Полноэкранный проигрыватель видеозаписи через Media3 ExoPlayer (Пункт 2)
    selectedVideoMessage?.let { videoMsg ->
        val context = LocalContext.current
        val fullVideoPath = remember(videoMsg.fullFileId, downloadedFiles) {
            tdLibManager.getFilePath(videoMsg.fullFileId)
        }
        val thumbVideoPath = remember(videoMsg.fileId, downloadedFiles) {
            tdLibManager.getFilePath(videoMsg.fileId)
        }

        // Запуск принудительной фоновой загрузки видеофайла
        LaunchedEffect(videoMsg.fullFileId) {
            videoMsg.fullFileId?.let { fid ->
                tdLibManager.downloadMediaFile(fid, priority = 32, force = true)
            }
        }

        Dialog(
            onDismissRequest = { selectedVideoMessage = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (fullVideoPath != null && File(fullVideoPath).exists()) {
                    // ExoPlayer проигрыватель для всех видеоформатов Telegram (MP4, MKV, WebM, H.264, HEVC, VP9)
                    val exoPlayer = remember(fullVideoPath) {
                        ExoPlayer.Builder(context).build().apply {
                            val uri = android.net.Uri.fromFile(File(fullVideoPath))
                            val mediaItem = MediaItem.fromUri(uri)
                            setMediaItem(mediaItem)
                            repeatMode = Player.REPEAT_MODE_ALL
                            playWhenReady = true
                            prepare()
                        }
                    }

                    DisposableEffect(exoPlayer) {
                        onDispose {
                            try {
                                exoPlayer.stop()
                                exoPlayer.release()
                            } catch (_: Throwable) {}
                        }
                    }

                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Экран ожидания загрузки видеофайла с фоновым превью
                    val thumbBitmap = remember(thumbVideoPath) {
                        if (!thumbVideoPath.isNullOrBlank()) {
                            try {
                                BitmapFactory.decodeFile(thumbVideoPath)?.asImageBitmap()
                            } catch (_: Throwable) {
                                null
                            }
                        } else null
                    }

                    if (thumbBitmap != null) {
                        Image(
                            bitmap = thumbBitmap,
                            contentDescription = "Превью видео",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(0.35f)
                        )
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xEE111827)),
                        modifier = Modifier
                            .width(480.dp)
                            .border(2.dp, Color(0xFF38BDF8), RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF38BDF8),
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Загрузка видеозаписи...",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (videoMsg.mediaInfo.isNotBlank()) "Длительность: ${videoMsg.mediaInfo}" else "Формат Telegram Video",
                                color = Color(0xFF38BDF8),
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Воспроизведение через Media3 ExoPlayer начнется автоматически сразу после завершения загрузки",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            var isCancelLoadFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = { selectedVideoMessage = null },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isCancelLoadFocused) Color(0xFF38BDF8) else Color(0xFF26334D)
                                ),
                                modifier = Modifier
                                    .onFocusChanged { isCancelLoadFocused = it.isFocused }
                                    .border(
                                        width = if (isCancelLoadFocused) 2.5.dp else 1.dp,
                                        color = if (isCancelLoadFocused) Color.White else Color(0xFF3B4D73),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                            ) {
                                Text(
                                    "Отмена (Назад)",
                                    color = if (isCancelLoadFocused) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Плавающая кнопка закрытия видео в правом верхнем углу экрана ТВ
                var isCloseVideoFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(32.dp)
                ) {
                    Button(
                        onClick = { selectedVideoMessage = null },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCloseVideoFocused) Color(0xFF38BDF8) else Color(0xCC111827)
                        ),
                        modifier = Modifier
                            .onFocusChanged { isCloseVideoFocused = it.isFocused }
                            .border(
                                width = if (isCloseVideoFocused) 3.dp else 1.dp,
                                color = if (isCloseVideoFocused) Color.White else Color(0x66FFFFFF),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = if (isCloseVideoFocused) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Закрыть видео (ОК / Назад)",
                            color = if (isCloseVideoFocused) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // 3. Диалог действий с выбранным сообщением (Ответить, Переслать, Удалить) (Пункт 3)
    actionTargetMessage?.let { targetMsg ->
        Dialog(
            onDismissRequest = { actionTargetMessage = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111522)),
                modifier = Modifier
                    .width(580.dp)
                    .wrapContentHeight()
                    .border(2.dp, Color(0xFF38BDF8), RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Действия с сообщением",
                                color = Color(0xFF38BDF8),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${targetMsg.senderName} • ${targetMsg.time}",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(
                            onClick = { actionTargetMessage = null },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF090C14))
                            .border(1.dp, Color(0xFF1E2638), RoundedCornerShape(10.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = targetMsg.text,
                            color = Color.White,
                            fontSize = 13.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Ссылки из сообщения (внутренние Telegram и внешние веб-ссылки)
                        if (targetMsg.links.isNotEmpty()) {
                            Text(
                                text = "Ссылки в сообщении (${targetMsg.links.size}):",
                                color = Color(0xFF38BDF8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            targetMsg.links.forEach { linkItem ->
                                var isLinkActFocused by remember { mutableStateOf(false) }
                                Button(
                                    onClick = {
                                        val urlToOpen = linkItem.url
                                        actionTargetMessage = null
                                        handleLinkClick(urlToOpen)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isLinkActFocused) Color(0xFF38BDF8) else Color(0xFF102A45)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .onFocusChanged { isLinkActFocused = it.isFocused }
                                        .border(
                                            width = if (isLinkActFocused) 2.5.dp else 1.dp,
                                            color = if (isLinkActFocused) Color.White else Color(0xFF0284C7),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                ) {
                                    Text(
                                        text = if (linkItem.isInternalTelegram) {
                                            "✈️ Перейти в Telegram: ${linkItem.displayLabel}"
                                        } else {
                                            "🌐 Открыть ссылку: ${linkItem.displayLabel}"
                                        },
                                        color = if (isLinkActFocused) Color.Black else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Divider(color = Color(0xFF1E293B))
                        }

                        // Действие: Просмотр фото в исходном HD качестве
                        if (targetMsg.mediaType == MessageMediaType.PHOTO) {
                            var isPhotoActFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = {
                                    val msgToView = targetMsg
                                    actionTargetMessage = null
                                    msgToView.fullFileId?.let { fid -> tdLibManager.downloadMediaFile(fid, priority = 32, force = true) }
                                    selectedPhotoMessage = msgToView
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isPhotoActFocused) Color(0xFF38BDF8) else Color(0xFF1E2A42)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .onFocusChanged { isPhotoActFocused = it.isFocused }
                                    .border(
                                        width = if (isPhotoActFocused) 2.5.dp else 1.dp,
                                        color = if (isPhotoActFocused) Color.White else Color(0xFF2E3E5E),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Icon(Icons.Default.ZoomIn, contentDescription = null, tint = if (isPhotoActFocused) Color.Black else Color(0xFF38BDF8))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("🔍 Просмотреть фото в исходном качестве (HD)", color = if (isPhotoActFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        // Действие: Воспроизвести видео
                        if (targetMsg.mediaType == MessageMediaType.VIDEO) {
                            var isVideoActFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = {
                                    val msgToView = targetMsg
                                    actionTargetMessage = null
                                    msgToView.fullFileId?.let { fid -> tdLibManager.downloadMediaFile(fid, priority = 32, force = true) }
                                    selectedVideoMessage = msgToView
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isVideoActFocused) Color(0xFF38BDF8) else Color(0xFF1E2A42)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .onFocusChanged { isVideoActFocused = it.isFocused }
                                    .border(
                                        width = if (isVideoActFocused) 2.5.dp else 1.dp,
                                        color = if (isVideoActFocused) Color.White else Color(0xFF2E3E5E),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = if (isVideoActFocused) Color.Black else Color(0xFF38BDF8))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("▶ Воспроизвести видеозапись (${targetMsg.mediaInfo})", color = if (isVideoActFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        // Действие: Ответить
                        if (canSendMessages) {
                            var isReplyActFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = {
                                    replyingToMessage = targetMsg
                                    actionTargetMessage = null
                                    isInputEditing = true
                                    scope.launch {
                                        delay(100)
                                        textFieldFocusRequester.requestFocus()
                                        keyboardController?.show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isReplyActFocused) Color(0xFF38BDF8) else Color(0xFF1E2A42)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .onFocusChanged { isReplyActFocused = it.isFocused }
                                    .border(
                                        width = if (isReplyActFocused) 2.5.dp else 1.dp,
                                        color = if (isReplyActFocused) Color.White else Color(0xFF2E3E5E),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Icon(Icons.Default.Reply, contentDescription = null, tint = if (isReplyActFocused) Color.Black else Color(0xFF38BDF8))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("↩️ Ответить на сообщение", color = if (isReplyActFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        // Действие: Переслать
                        var isForwardActFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = {
                                val toFwd = targetMsg
                                actionTargetMessage = null
                                forwardingMessage = toFwd
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isForwardActFocused) Color(0xFF38BDF8) else Color(0xFF1E2A42)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .onFocusChanged { isForwardActFocused = it.isFocused }
                                .border(
                                    width = if (isForwardActFocused) 2.5.dp else 1.dp,
                                    color = if (isForwardActFocused) Color.White else Color(0xFF2E3E5E),
                                    shape = RoundedCornerShape(12.dp)
                                )
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = if (isForwardActFocused) Color.Black else Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("↗️ Переслать в другой чат", color = if (isForwardActFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        // Действие: Читать текст целиком (если текст длинный)
                        if (targetMsg.text.length > 200) {
                            var isReadActFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = {
                                    val toRead = targetMsg
                                    actionTargetMessage = null
                                    selectedFullMessage = toRead
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isReadActFocused) Color(0xFF38BDF8) else Color(0xFF1E2A42)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .onFocusChanged { isReadActFocused = it.isFocused }
                                    .border(
                                        width = if (isReadActFocused) 2.5.dp else 1.dp,
                                        color = if (isReadActFocused) Color.White else Color(0xFF2E3E5E),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Icon(Icons.Default.MenuBook, contentDescription = null, tint = if (isReadActFocused) Color.Black else Color(0xFF38BDF8))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("📖 Читать полностью со скроллом", color = if (isReadActFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        // Действие: Удалить
                        var isDeleteActFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = {
                                val toDel = targetMsg
                                actionTargetMessage = null
                                deletingMessage = toDel
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDeleteActFocused) Color(0xFFEF4444) else Color(0xFF2A151B)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .onFocusChanged { isDeleteActFocused = it.isFocused }
                                .border(
                                    width = if (isDeleteActFocused) 2.5.dp else 1.dp,
                                    color = if (isDeleteActFocused) Color.White else Color(0xFF4C1D26),
                                    shape = RoundedCornerShape(12.dp)
                                )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = if (isDeleteActFocused) Color.White else Color(0xFFEF4444))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("🗑️ Удалить сообщение", color = if (isDeleteActFocused) Color.White else Color(0xFFFCA5A5), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    // 4. Диалог выбора чата для пересылки сообщения
    forwardingMessage?.let { msgToForward ->
        val chatsToForward by tdLibManager.chatsList.collectAsState()
        Dialog(
            onDismissRequest = { forwardingMessage = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131722)),
                modifier = Modifier
                    .width(620.dp)
                    .fillMaxHeight(0.75f)
                    .border(2.dp, Color(0xFF38BDF8), RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Переслать сообщение",
                            color = Color(0xFF38BDF8),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { forwardingMessage = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26334D))
                        ) {
                            Text("Отмена", color = Color.White, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Выберите чат получателя из списка:",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(chatsToForward, key = { it.id }) { targetChat ->
                            var isTargetFocused by remember { mutableStateOf(false) }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isTargetFocused) Color(0xFF0284C7) else Color(0xFF1E2538))
                                    .border(
                                        width = if (isTargetFocused) 2.5.dp else 1.dp,
                                        color = if (isTargetFocused) Color.White else Color(0xFF2F3B55),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .onFocusChanged { isTargetFocused = it.isFocused }
                                    .clickable {
                                        scope.launch {
                                            val fwdRes = tdLibManager.forwardMessages(
                                                fromChatId = chatId,
                                                toChatId = targetChat.id,
                                                messageIds = longArrayOf(msgToForward.id)
                                            )
                                            if (fwdRes.isSuccess) {
                                                toastNotification = "✓ Сообщение переслано в ${targetChat.title}"
                                            } else {
                                                toastNotification = "Не удалось переслать сообщение"
                                            }
                                            forwardingMessage = null
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Send,
                                    contentDescription = null,
                                    tint = if (isTargetFocused) Color.Black else Color(0xFF38BDF8),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = targetChat.title,
                                    color = if (isTargetFocused) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 5. Диалог подтверждения удаления сообщения
    deletingMessage?.let { msgToDelete ->
        Dialog(
            onDismissRequest = { deletingMessage = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B26)),
                modifier = Modifier
                    .width(520.dp)
                    .wrapContentHeight()
                    .border(2.dp, Color(0xFFEF4444), RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(26.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Удалить сообщение?",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Вы действительно хотите удалить это сообщение из чата?",
                        color = Color(0xFFCBD5E1),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "\"${msgToDelete.text.take(80)}\"",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var isRevokeFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = {
                                scope.launch {
                                    tdLibManager.deleteMessages(chatId, longArrayOf(msgToDelete.id), revoke = true)
                                    messagesList = messagesList.filterNot { it.id == msgToDelete.id }
                                    toastNotification = "✓ Сообщение удалено для всех"
                                    deletingMessage = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRevokeFocused) Color(0xFFEF4444) else Color(0xFF7F1D1D)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { isRevokeFocused = it.isFocused }
                                .border(
                                    width = if (isRevokeFocused) 2.5.dp else 1.dp,
                                    color = if (isRevokeFocused) Color.White else Color(0xFF991B1B),
                                    shape = RoundedCornerShape(10.dp)
                                )
                        ) {
                            Text("Для всех", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        var isSelfFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = {
                                scope.launch {
                                    tdLibManager.deleteMessages(chatId, longArrayOf(msgToDelete.id), revoke = false)
                                    messagesList = messagesList.filterNot { it.id == msgToDelete.id }
                                    toastNotification = "✓ Сообщение удалено"
                                    deletingMessage = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelfFocused) Color(0xFF38BDF8) else Color(0xFF1E293B)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { isSelfFocused = it.isFocused }
                                .border(
                                    width = if (isSelfFocused) 2.5.dp else 1.dp,
                                    color = if (isSelfFocused) Color.White else Color(0xFF334155),
                                    shape = RoundedCornerShape(10.dp)
                                )
                        ) {
                            Text("У меня", color = if (isSelfFocused) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        var isCancelFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { deletingMessage = null },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCancelFocused) Color.White else Color(0xFF26334D)
                            ),
                            modifier = Modifier
                                .onFocusChanged { isCancelFocused = it.isFocused }
                                .border(
                                    width = if (isCancelFocused) 2.5.dp else 1.dp,
                                    color = if (isCancelFocused) Color.Black else Color(0xFF3B4D73),
                                    shape = RoundedCornerShape(10.dp)
                                )
                        ) {
                            Text("Отмена", color = if (isCancelFocused) Color.Black else Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // Всплывающее уведомление о действии
    toastNotification?.let { msg ->
        LaunchedEffect(msg) {
            delay(3000)
            toastNotification = null
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xEE1E293B)),
                modifier = Modifier.border(1.5.dp, Color(0xFF38BDF8), RoundedCornerShape(12.dp))
            ) {
                Text(
                    text = msg,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
    }

    // Диалог профиля чата / канала (Пункт 1: Исправление верстки и загрузка аватарок)
    if (showProfileDialog) {
        Dialog(
            onDismissRequest = { showProfileDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val resolvedAvatarPath = remember(profileDetails?.avatarPath, chatAvatarPath, downloadedFiles) {
                profileDetails?.avatarPath ?: chatAvatarPath ?: tdLibManager.getChatPhotoPath(chatObj)
            }
            val profileAvatarBitmap = remember(resolvedAvatarPath) {
                if (!resolvedAvatarPath.isNullOrBlank()) {
                    try {
                        val f = File(resolvedAvatarPath)
                        if (f.exists() && f.length() > 0) {
                            BitmapFactory.decodeFile(resolvedAvatarPath)?.asImageBitmap()
                        } else null
                    } catch (_: Throwable) {
                        null
                    }
                } else null
            }

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111420)),
                modifier = Modifier
                    .width(760.dp)
                    .fillMaxHeight(0.85f)
                    .border(2.dp, Color(0xFF38BDF8), RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp)
                ) {
                    // Шапка окна профиля
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Информация о профиле",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )

                        var isCloseDialogFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { showProfileDialog = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCloseDialogFocused) Color(0xFF38BDF8) else Color(0xFF26334D)
                            ),
                            modifier = Modifier
                                .onFocusChanged { isCloseDialogFocused = it.isFocused }
                                .border(
                                    width = if (isCloseDialogFocused) 2.5.dp else 1.dp,
                                    color = if (isCloseDialogFocused) Color.White else Color(0xFF3B4D73),
                                    shape = RoundedCornerShape(12.dp)
                                )
                        ) {
                            Text(
                                "Закрыть (ОК)",
                                color = if (isCloseDialogFocused) Color.Black else Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color(0xFF22283A))
                    Spacer(modifier = Modifier.height(16.dp))

                    if (isLoadingProfile) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF38BDF8))
                        }
                    } else {
                        val details = profileDetails
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Центрированный аватар высокого качества (96 dp)
                            if (profileAvatarBitmap != null) {
                                Image(
                                    bitmap = profileAvatarBitmap,
                                    contentDescription = chatTitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(CircleShape)
                                        .border(3.dp, Color(0xFF38BDF8), CircleShape)
                                )
                            } else {
                                val firstLetter = chatTitle.trim().take(1).uppercase().ifBlank { "#" }
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0284C7))
                                        .border(3.dp, Color(0xFF38BDF8), CircleShape)
                                ) {
                                    Text(
                                        text = firstLetter,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 38.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Название чата / канала
                            Text(
                                text = chatTitle,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Бейджи типа чата и верификации
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = details?.typeDescription ?: "Канал или чат",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (details?.isVerified == true) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF0369A1), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "✓ Подтвержден",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Кнопка подписки на канал/группу прямо из профиля (если ещё не подписаны)
                            if (canJoinCurrentChat) {
                                var isProfileJoinFocused by remember { mutableStateOf(false) }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val res = tdLibManager.joinChat(chatId)
                                            if (res.isSuccess) {
                                                canJoinCurrentChat = false
                                                toastNotification = "✓ Вы подписались на $chatTitle"
                                            } else {
                                                toastNotification = "Не удалось подписаться: ${res.exceptionOrNull()?.message ?: ""}"
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isProfileJoinFocused) Color(0xFF10B981) else Color(0xFF064E3B)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .onFocusChanged { isProfileJoinFocused = it.isFocused }
                                        .border(
                                            width = if (isProfileJoinFocused) 3.dp else 1.5.dp,
                                            color = if (isProfileJoinFocused) Color.White else Color(0xFF10B981),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        tint = if (isProfileJoinFocused) Color.Black else Color(0xFF34D399)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Подписаться / Вступить в чат (ОК)",
                                        color = if (isProfileJoinFocused) Color.Black else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                            }

                            // Блоки с деталями профиля и интерактивными ссылками
                            val descriptionText = details?.description?.ifBlank { "" }.orEmpty()
                            val profileDescriptionLinks = remember(descriptionText) {
                                LinkExtractor.extractLinks(descriptionText)
                            }

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                if (!details?.username.isNullOrBlank()) {
                                    val uname = details?.username.orEmpty().removePrefix("@")
                                    var isUsernameCardFocused by remember { mutableStateOf(false) }
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isUsernameCardFocused) Color(0xFF0284C7) else Color(0xFF181F30)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(
                                                width = if (isUsernameCardFocused) 2.5.dp else 1.dp,
                                                color = if (isUsernameCardFocused) Color.White else Color(0xFF26324D),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .onFocusChanged { isUsernameCardFocused = it.isFocused }
                                            .clickable {
                                                showProfileDialog = false
                                                handleLinkClick("https://t.me/$uname")
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Имя пользователя / Ссылка:",
                                                    color = if (isUsernameCardFocused) Color(0xFFE0F2FE) else Color(0xFF38BDF8),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "@$uname (https://t.me/$uname)",
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            Text(
                                                text = "✈️ Открыть (ОК)",
                                                color = if (isUsernameCardFocused) Color.White else Color(0xFF38BDF8),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                if (!details?.inviteLink.isNullOrBlank()) {
                                    val invLink = details?.inviteLink.orEmpty()
                                    var isInviteCardFocused by remember { mutableStateOf(false) }
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isInviteCardFocused) Color(0xFF0284C7) else Color(0xFF181F30)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(
                                                width = if (isInviteCardFocused) 2.5.dp else 1.dp,
                                                color = if (isInviteCardFocused) Color.White else Color(0xFF26324D),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .onFocusChanged { isInviteCardFocused = it.isFocused }
                                            .clickable {
                                                showProfileDialog = false
                                                handleLinkClick(invLink)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Ссылка-приглашение:",
                                                    color = if (isInviteCardFocused) Color(0xFFE0F2FE) else Color(0xFF38BDF8),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = invLink,
                                                    color = Color.White,
                                                    fontSize = 14.sp
                                                )
                                            }
                                            Text(
                                                text = "✈️ Перейти (ОК)",
                                                color = if (isInviteCardFocused) Color.White else Color(0xFF38BDF8),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                if (details?.memberCount != null && details.memberCount > 0) {
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF181F30)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Text(
                                                text = "Количество участников / подписчиков:",
                                                color = Color(0xFF38BDF8),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "${details.memberCount} участников",
                                                color = Color.White,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }

                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF181F30)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(
                                            text = "Описание / О канале:",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        if (descriptionText.isNotBlank()) {
                                            Text(
                                                text = remember(descriptionText) { LinkExtractor.buildHighlightedText(descriptionText) },
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                lineHeight = 22.sp
                                            )
                                        } else {
                                            Text(
                                                text = "Описание не указано",
                                                color = Color.Gray,
                                                fontSize = 14.sp
                                            )
                                        }

                                        if (profileDescriptionLinks.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Divider(color = Color(0xFF26324D))
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = "Ссылки из описания профиля (нажмите ОК для перехода):",
                                                color = Color(0xFF38BDF8),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                profileDescriptionLinks.forEach { linkItem ->
                                                    var isProfLinkFocused by remember { mutableStateOf(false) }
                                                    Button(
                                                        onClick = {
                                                            showProfileDialog = false
                                                            handleLinkClick(linkItem.url)
                                                        },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (isProfLinkFocused) Color(0xFF38BDF8) else Color(0xFF102A45)
                                                        ),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .onFocusChanged { isProfLinkFocused = it.isFocused }
                                                            .border(
                                                                width = if (isProfLinkFocused) 2.5.dp else 1.dp,
                                                                color = if (isProfLinkFocused) Color.White else Color(0xFF0284C7),
                                                                shape = RoundedCornerShape(10.dp)
                                                            )
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = "${if (linkItem.isInternalTelegram) "✈️" else "🌐"} ${linkItem.displayLabel}",
                                                                color = if (isProfLinkFocused) Color.Black else Color.White,
                                                                fontSize = 13.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text(
                                                                text = if (linkItem.isInternalTelegram) "Открыть в TG (ОК)" else "Открыть сайт (ОК)",
                                                                color = if (isProfLinkFocused) Color.Black else Color(0xFF38BDF8),
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.ExtraBold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 6. Встроенный ТВ-браузер для внешних веб-ссылок (с поддержкой пульта ДУ и открытия в системном браузере)
    activeExternalWebUrl?.let { webUrl ->
        val context = LocalContext.current
        var currentLoadedUrl by remember(webUrl) { mutableStateOf(webUrl) }
        var pageTitle by remember(webUrl) { mutableStateOf("Загрузка страницы...") }
        var webViewInstance by remember { mutableStateOf<WebView?>(null) }

        Dialog(
            onDismissRequest = { activeExternalWebUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0E131F)),
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .fillMaxHeight(0.92f)
                    .border(2.dp, Color(0xFF38BDF8), RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Верхняя панель ТВ-браузера
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF13192B))
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pageTitle,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentLoadedUrl,
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var isScrollUpFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = { webViewInstance?.scrollBy(0, -360) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isScrollUpFocused) Color(0xFF38BDF8) else Color(0xFF1E293B)
                                ),
                                modifier = Modifier
                                    .onFocusChanged { isScrollUpFocused = it.isFocused }
                                    .border(
                                        width = if (isScrollUpFocused) 2.5.dp else 1.dp,
                                        color = if (isScrollUpFocused) Color.White else Color(0xFF334155),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                            ) {
                                Text("▲ Вверх", color = if (isScrollUpFocused) Color.Black else Color.White, fontSize = 12.sp)
                            }

                            var isScrollDownFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = { webViewInstance?.scrollBy(0, 360) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isScrollDownFocused) Color(0xFF38BDF8) else Color(0xFF1E293B)
                                ),
                                modifier = Modifier
                                    .onFocusChanged { isScrollDownFocused = it.isFocused }
                                    .border(
                                        width = if (isScrollDownFocused) 2.5.dp else 1.dp,
                                        color = if (isScrollDownFocused) Color.White else Color(0xFF334155),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                            ) {
                                Text("▼ Вниз", color = if (isScrollDownFocused) Color.Black else Color.White, fontSize = 12.sp)
                            }

                            var isExtBrowserFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentLoadedUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        toastNotification = "Системный браузер не найден на ТВ"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isExtBrowserFocused) Color(0xFF38BDF8) else Color(0xFF1E293B)
                                ),
                                modifier = Modifier
                                    .onFocusChanged { isExtBrowserFocused = it.isFocused }
                                    .border(
                                        width = if (isExtBrowserFocused) 2.5.dp else 1.dp,
                                        color = if (isExtBrowserFocused) Color.White else Color(0xFF334155),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                            ) {
                                Text(
                                    "🌐 Браузер ТВ",
                                    color = if (isExtBrowserFocused) Color.Black else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            var isCloseWebFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = { activeExternalWebUrl = null },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isCloseWebFocused) Color(0xFFEF4444) else Color(0xFF26334D)
                                ),
                                modifier = Modifier
                                    .onFocusChanged { isCloseWebFocused = it.isFocused }
                                    .border(
                                        width = if (isCloseWebFocused) 2.5.dp else 1.dp,
                                        color = if (isCloseWebFocused) Color.White else Color(0xFF3B4D73),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                            ) {
                                Text(
                                    "Закрыть (ОК)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Область отображения веб-страницы с перехватом t.me / tg:// ссылок
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        if (!url.isNullOrBlank()) currentLoadedUrl = url
                                        val t = view?.title
                                        if (!t.isNullOrBlank()) pageTitle = t
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val clickedUrl = request?.url?.toString() ?: return false
                                        if (LinkExtractor.isTelegramInternalUrl(clickedUrl)) {
                                            activeExternalWebUrl = null
                                            handleLinkClick(clickedUrl)
                                            return true
                                        }
                                        return false
                                    }
                                }
                                loadUrl(webUrl)
                                webViewInstance = this
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}
