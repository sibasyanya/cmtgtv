package com.tgmedia.tv.ui.chats

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.util.Log
import android.widget.VideoView
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

data class UiMessage(
    val id: Long,
    val senderName: String,
    val text: String,
    val time: String,
    val isOutgoing: Boolean,
    val mediaType: MessageMediaType = MessageMediaType.NONE,
    val fileId: Int? = null,
    val fullFileId: Int? = null,
    val mediaInfo: String = ""
)

/**
 * Преобразование сообщения TDLib в отображаемую модель UI с поддержкой медиа.
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
    val tdManager = try { TdLibManager.getInstance() } catch (_: Throwable) { null }

    val contentText = when (val c = this.content) {
        is TdApi.MessageText -> c.text.text
        is TdApi.MessagePhoto -> {
            mType = MessageMediaType.PHOTO
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
            targetFileId = c.animation.thumbnail?.file?.id ?: c.animation.animation.id
            targetFullFileId = c.animation.animation.id
            if (c.caption.text.isNotBlank()) "GIF: ${c.caption.text}" else "GIF анимация"
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

    return UiMessage(
        id = this.id,
        senderName = if (this.isOutgoing) "Вы" else senderTitle,
        text = contentText.ifBlank { "Сообщение" },
        time = timeFormatted,
        isOutgoing = this.isOutgoing,
        mediaType = mType,
        fileId = targetFileId,
        fullFileId = targetFullFileId,
        mediaInfo = mInfo
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
 */
@Composable
fun TvChatDetailScreen(
    chatId: Long,
    chatTitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tdLibManager = remember { TdLibManager.getInstance() }
    val scope = rememberCoroutineScope()
    val downloadedFiles by tdLibManager.fileDownloadedState.collectAsState()
    val isMediaAutoDownloadEnabled by tdLibManager.isMediaAutoDownloadEnabled.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    var messagesList by remember { mutableStateOf<List<UiMessage>>(emptyList()) }
    var isLoadingHistory by remember { mutableStateOf(true) }
    var isLoadingMoreOlder by remember { mutableStateOf(false) }
    var hasMoreOlderMessages by remember { mutableStateOf(true) }

    // Проверка прав на отправку сообщений в чат
    var canSendMessages by remember { mutableStateOf(false) }

    // Профиль чата
    var showProfileDialog by remember { mutableStateOf(false) }
    var profileDetails by remember { mutableStateOf<TdLibManager.ChatProfileDetails?>(null) }
    var isLoadingProfile by remember { mutableStateOf(false) }

    // Просмотр длинного сообщения
    var selectedFullMessage by remember { mutableStateOf<UiMessage?>(null) }

    // Просмотр фото на весь экран ТВ
    var selectedPhotoPath by remember { mutableStateOf<String?>(null) }

    // Просмотр/воспроизведение видео
    var selectedVideoPath by remember { mutableStateOf<String?>(null) }

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
    BackHandler(enabled = selectedPhotoPath != null || selectedVideoPath != null || selectedFullMessage != null || showProfileDialog) {
        if (selectedPhotoPath != null) {
            selectedPhotoPath = null
        } else if (selectedVideoPath != null) {
            selectedVideoPath = null
        } else if (selectedFullMessage != null) {
            selectedFullMessage = null
        } else if (showProfileDialog) {
            showProfileDialog = false
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

    // Загрузка прав на отправку сообщений
    LaunchedEffect(chatId, chatObj) {
        canSendMessages = tdLibManager.canSendMessagesInChat(chatObj)
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
                                        is TdApi.MessageText -> existing.copy(text = c.text.text)
                                        is TdApi.MessagePhoto -> existing.copy(
                                            text = if (c.caption.text.isNotBlank()) c.caption.text else "🖼️ Фотография"
                                        )
                                        is TdApi.MessageVideo -> existing.copy(
                                            text = if (c.caption.text.isNotBlank()) c.caption.text else "📹 Видео"
                                        )
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

    // Отправка сообщений
    fun sendMessage(textToSend: String) {
        val trimmed = textToSend.trim()
        if (trimmed.isEmpty()) return

        scope.launch {
            val result = tdLibManager.sendTextMessage(chatId, trimmed)
            if (result.isSuccess) {
                inputText = ""
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

            // Правый блок шапки: кнопка быстрого перехода к вводу сообщения (если разрешено)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                    when (msg.mediaType) {
                                        MessageMediaType.PHOTO -> {
                                            val pathToView = fullMediaLocalPath ?: mediaLocalPath
                                            if (!pathToView.isNullOrBlank()) {
                                                selectedPhotoPath = pathToView
                                            } else {
                                                msg.fullFileId?.let { fid -> tdLibManager.downloadMediaFile(fid, force = true) }
                                                msg.fileId?.let { fid -> tdLibManager.downloadMediaFile(fid, force = true) }
                                                selectedFullMessage = msg
                                            }
                                        }
                                        MessageMediaType.VIDEO -> {
                                            if (!fullMediaLocalPath.isNullOrBlank()) {
                                                selectedVideoPath = fullMediaLocalPath
                                            } else {
                                                msg.fullFileId?.let { fid -> tdLibManager.downloadMediaFile(fid, force = true) }
                                                selectedFullMessage = msg
                                            }
                                        }
                                        MessageMediaType.AUDIO, MessageMediaType.VOICE -> {
                                            if (!mediaLocalPath.isNullOrBlank()) {
                                                togglePlayAudio(msg.id, mediaLocalPath)
                                            } else {
                                                msg.fileId?.let { fid -> tdLibManager.downloadMediaFile(fid, force = true) }
                                            }
                                        }
                                        else -> {
                                            selectedFullMessage = msg
                                        }
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
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF38BDF8), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "▶ ВЫБРАНО (ОК)",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.Black
                                            )
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
                                                            text = if (msg.mediaType == MessageMediaType.PHOTO) "🔍 На весь экран (ОК)" else "▶ Воспроизвести (ОК)",
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

                                Text(
                                    text = msg.text,
                                    fontSize = 15.sp,
                                    color = Color.White,
                                    lineHeight = 22.sp,
                                    maxLines = 8,
                                    overflow = TextOverflow.Ellipsis
                                )

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
                        Text(
                            text = fullMsg.text,
                            color = Color.White,
                            fontSize = 17.sp,
                            lineHeight = 26.sp
                        )
                    }
                }
            }
        }
    }

    // Полноэкранный просмотр фото на экране ТВ (Пункт 6)
    selectedPhotoPath?.let { photoPath ->
        Dialog(
            onDismissRequest = { selectedPhotoPath = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val photoBitmap = remember(photoPath) {
                    try {
                        BitmapFactory.decodeFile(photoPath)?.asImageBitmap()
                    } catch (_: Throwable) {
                        null
                    }
                }

                if (photoBitmap != null) {
                    Image(
                        bitmap = photoBitmap,
                        contentDescription = "Фотография",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Плавающая кнопка закрытия в правом верхнем углу экрана ТВ
                var isClosePhotoFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(32.dp)
                ) {
                    Button(
                        onClick = { selectedPhotoPath = null },
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

    // Полноэкранный проигрыватель видеозаписи (Пункт 6)
    selectedVideoPath?.let { videoPath ->
        Dialog(
            onDismissRequest = { selectedVideoPath = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoPath(videoPath)
                            val mediaController = android.widget.MediaController(ctx)
                            mediaController.setAnchorView(this)
                            setMediaController(mediaController)
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                start()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Плавающая кнопка закрытия видео в правом верхнем углу экрана ТВ
                var isCloseVideoFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(32.dp)
                ) {
                    Button(
                        onClick = { selectedVideoPath = null },
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

                            // Блоки с деталями профиля
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                if (!details?.username.isNullOrBlank()) {
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF181F30)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Text(
                                                text = "Имя пользователя / Ссылка:",
                                                color = Color(0xFF38BDF8),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "@${details?.username} (https://t.me/${details?.username})",
                                                color = Color.White,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }

                                if (!details?.inviteLink.isNullOrBlank()) {
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF181F30)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Text(
                                                text = "Ссылка-приглашение:",
                                                color = Color(0xFF38BDF8),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = details?.inviteLink.orEmpty(),
                                                color = Color.White,
                                                fontSize = 14.sp
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
                                        Text(
                                            text = details?.description?.ifBlank { "Описание не указано" } ?: "Описание не указано",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            lineHeight = 22.sp
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
