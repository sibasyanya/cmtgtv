package com.tgmedia.tv.ui.chats

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tgmedia.tv.data.tdlib.TdLibManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UiMessage(
    val id: Long,
    val senderName: String,
    val text: String,
    val time: String,
    val isOutgoing: Boolean
)

/**
 * Преобразование реального сообщения TDLib в отображаемую модель UI.
 */
fun TdApi.Message.toUiMessage(senderTitle: String): UiMessage {
    val timeFormatted = try {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(this.date * 1000L))
    } catch (_: Exception) {
        ""
    }

    val contentText = when (val c = this.content) {
        is TdApi.MessageText -> c.text.text
        is TdApi.MessagePhoto -> if (c.caption.text.isNotBlank()) "🖼️ ${c.caption.text}" else "🖼️ Фотография"
        is TdApi.MessageVideo -> if (c.caption.text.isNotBlank()) "📹 ${c.caption.text}" else "📹 Видеозапись"
        is TdApi.MessageDocument -> if (c.caption.text.isNotBlank()) "📄 ${c.caption.text}" else "📄 Документ"
        is TdApi.MessageVoiceNote -> "🎤 Голосовое сообщение"
        is TdApi.MessageAudio -> if (c.caption.text.isNotBlank()) "🎵 ${c.caption.text}" else "🎵 Аудиозапись"
        is TdApi.MessageSticker -> "Стикер ${c.sticker.emoji}"
        is TdApi.MessageAnimation -> if (c.caption.text.isNotBlank()) "GIF: ${c.caption.text}" else "GIF анимация"
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
        isOutgoing = this.isOutgoing
    )
}

/**
 * 10-Foot UI экран чтения канала/чата с ТВ-пульта:
 * 1. Проверка прав отправки сообщений (в каналах и чужих группах поле ввода и шаблоны скрываются).
 * 2. Клавиатура открывается ТОЛЬКО при нажатии кнопки ОК на пульте ДУ.
 * 3. Двунаправленная навигация D-Pad по каждому сообщению вверх и вниз с фокус-рамкой.
 * 4. Удобный переход к полю ввода (кнопка в шапке "Написать сообщение" и переход из нижней части списка).
 * 5. Просмотр длинных сообщений целиком по нажатию ОК в полноэкранном модальном окне со скроллом.
 * 6. Окно полного профиля чата (описание, ссылки, юзернейм, количество участников).
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
                    }
                }
            } catch (e: Exception) {
                Log.w("TvChatDetail", "Failed to load older messages: ${e.message}")
            } finally {
                isLoadingMoreOlder = false
            }
        }
    }

    // Первичная загрузка реальной истории сообщений чата и открытие чата в TDLib
    LaunchedEffect(chatId) {
        isLoadingHistory = true
        hasMoreOlderMessages = true
        tdLibManager.openChat(chatId)

        try {
            val historyResult = tdLibManager.getChatHistory(
                chatId = chatId,
                fromMessageId = 0,
                offset = 0,
                limit = 30
            )
            val rawMessages = historyResult.getOrNull().orEmpty()

            if (rawMessages.isEmpty()) {
                hasMoreOlderMessages = false
            }

            val converted = rawMessages.reversed().map { msg ->
                val senderName = tdLibManager.getSenderName(msg.senderId)
                msg.toUiMessage(senderName)
            }
            messagesList = converted

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
                            messagesList = messagesList.map { oldMsg ->
                                if (oldMsg.id == update.messageId) {
                                    val dummyMsg = TdApi.Message().apply {
                                        id = update.messageId
                                        content = update.newContent
                                        date = (System.currentTimeMillis() / 1000).toInt()
                                        isOutgoing = oldMsg.isOutgoing
                                    }
                                    val updated = dummyMsg.toUiMessage(oldMsg.senderName)
                                    oldMsg.copy(text = updated.text)
                                } else oldMsg
                            }
                        }
                    }
                    is TdApi.UpdateDeleteMessages -> {
                        if (update.chatId == chatId) {
                            val deletedIds = update.messageIds.toSet()
                            messagesList = messagesList.filterNot { it.id in deletedIds }
                        }
                    }
                    is TdApi.UpdateChatPermissions -> {
                        if (update.chatId == chatId) {
                            canSendMessages = tdLibManager.canSendMessagesInChat(chatObj)
                        }
                    }
                    else -> Unit
                }
            } catch (e: Exception) {
                Log.w("TvChatDetail", "Error in updates collector: ${e.message}")
            }
        }
    }

    // При выходе закрываем чат в TDLib
    DisposableEffect(chatId) {
        onDispose {
            try {
                tdLibManager.closeChat(chatId)
            } catch (_: Exception) {}
        }
    }

    val quickPhrases = listOf(
        "Да, отлично!",
        "Смотрю на ТВ",
        "Позже отвечу",
        "Спасибо за инфу"
    )

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val currentText = text.trim()
        inputText = ""
        isInputEditing = false
        keyboardController?.hide()

        scope.launch {
            try {
                tdLibManager.sendMessage(chatId, currentText)
            } catch (e: Exception) {
                Log.e("TvChatDetail", "Failed to send message: ${e.message}")
            }
        }
    }

    fun openProfile() {
        showProfileDialog = true
        isLoadingProfile = true
        scope.launch {
            profileDetails = tdLibManager.fetchChatProfileDetails(chatId)
            isLoadingProfile = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090A0F))
            .padding(horizontal = 48.dp, vertical = 20.dp)
    ) {
        // Шапка чата с кликабельным профилем и кнопками навигации
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                var isBackFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = { onBack() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBackFocused) Color(0xFF38BDF8) else Color(0xFF1E2433)
                    ),
                    modifier = Modifier
                        .onFocusChanged { isBackFocused = it.isFocused }
                        .focusable()
                ) {
                    Text(
                        "← Назад",
                        fontSize = 13.sp,
                        color = if (isBackFocused) Color.Black else Color.White
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Кликабельная карточка профиля чата (аватар + название)
                var isProfileBtnFocused by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isProfileBtnFocused) Color(0xFF1E293B) else Color.Transparent)
                        .border(
                            width = if (isProfileBtnFocused) 2.dp else 1.dp,
                            color = if (isProfileBtnFocused) Color(0xFF38BDF8) else Color(0xFF222B3D),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { openProfile() }
                        .focusable()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .onFocusChanged { isProfileBtnFocused = it.isFocused }
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
                                .size(34.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        val firstLetter = chatTitle.trim().take(1).uppercase().ifBlank { "#" }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0284C7))
                        ) {
                            Text(
                                text = firstLetter,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = chatTitle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Нажмите ОК для информации о канале / чате",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8)
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
                            .focusable()
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
                            fontSize = 12.sp,
                            color = if (isWriteFocused) Color.Black else Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Область сообщений с двунаправленной навигацией по элементам D-Pad
        if (isLoadingHistory && messagesList.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.height(12.dp))
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Индикатор подгрузки более старых сообщений сверху
                if (hasMoreOlderMessages) {
                    item(key = "older_loader") {
                        var isLoaderFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoadingMoreOlder) {
                                CircularProgressIndicator(
                                    color = Color(0xFF38BDF8),
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Button(
                                    onClick = { loadOlderMessages() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isLoaderFocused) Color(0xFF38BDF8) else Color(0xFF1E2433)
                                    ),
                                    modifier = Modifier
                                        .onFocusChanged { isLoaderFocused = it.isFocused }
                                        .focusable()
                                ) {
                                    Text(
                                        "↑ Загрузить предыдущие сообщения...",
                                        color = if (isLoaderFocused) Color.Black else Color(0xFF38BDF8),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                items(messagesList, key = { it.id }) { msg ->
                    var isMsgFocused by remember { mutableStateOf(false) }
                    val alignment = if (msg.isOutgoing) Alignment.End else Alignment.Start
                    val bgBubbleColor = if (isMsgFocused) {
                        if (msg.isOutgoing) Color(0xFF0284C7) else Color(0xFF28324E)
                    } else {
                        if (msg.isOutgoing) Color(0xFF0369A1) else Color(0xFF181C2A)
                    }
                    val isLongMessage = msg.text.length > 250

                    Column(
                        horizontalAlignment = alignment,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 680.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(bgBubbleColor)
                                .border(
                                    width = if (isMsgFocused) 3.dp else 1.dp,
                                    color = if (isMsgFocused) Color(0xFF38BDF8) else Color(0xFF232A3D),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    // Открытие полного текста сообщения по кнопке ОК
                                    selectedFullMessage = msg
                                }
                                .focusable()
                                .onFocusChanged { isMsgFocused = it.isFocused }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Column {
                                if (!msg.isOutgoing) {
                                    Text(
                                        text = msg.senderName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                Text(
                                    text = msg.text,
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    lineHeight = 20.sp,
                                    maxLines = 8,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isLongMessage) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "[Нажмите ОК для чтения сообщения целиком]",
                                        fontSize = 11.sp,
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (msg.time.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = msg.time,
                                        fontSize = 10.sp,
                                        color = Color(0xFFA1A1AA),
                                        modifier = Modifier.align(Alignment.End)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Блок ввода сообщений и быстрых шаблонов отображается ТОЛЬКО если есть техническое право отправки
        if (canSendMessages) {
            Spacer(modifier = Modifier.height(10.dp))

            // Быстрые шаблоны ответов
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(quickPhrases) { phrase ->
                    var isPhraseFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .onFocusChanged { isPhraseFocused = it.isFocused }
                            .background(
                                if (isPhraseFocused) Color(0xFF0284C7) else Color(0xFF191C29),
                                RoundedCornerShape(20.dp)
                            )
                            .border(
                                width = if (isPhraseFocused) 2.dp else 1.dp,
                                color = if (isPhraseFocused) Color(0xFF38BDF8) else Color(0xFF2C3147),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { sendMessage(phrase) }
                            .focusable()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = phrase,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Поле ввода текста: клавиатура открывается ИСКЛЮЧИТЕЛЬНО по клику (кнопка ОК)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { isInputBoxFocused = it.isFocused }
                        .background(Color(0xFF131520), RoundedCornerShape(12.dp))
                        .border(
                            width = if (isInputBoxFocused) 3.dp else 1.dp,
                            color = if (isInputBoxFocused) Color(0xFF38BDF8) else Color(0xFF262A3C),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            isInputEditing = true
                            scope.launch {
                                delay(100)
                                textFieldFocusRequester.requestFocus()
                                keyboardController?.show()
                            }
                        }
                        .focusable()
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
                        .focusable()
                ) {
                    Text(
                        text = "Отправить",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    // Модальное окно просмотра длинного сообщения целиком с вертикальным скроллом
    selectedFullMessage?.let { fullMsg ->
        Dialog(onDismissRequest = { selectedFullMessage = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131826)),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF38BDF8)),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .fillMaxHeight(0.8f)
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
                        Column {
                            Text(
                                text = fullMsg.senderName,
                                color = Color(0xFF38BDF8),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Отправлено: ${fullMsg.time}",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }

                        var isCloseFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { selectedFullMessage = null },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCloseFocused) Color(0xFF38BDF8) else Color(0xFF26334D)
                            ),
                            modifier = Modifier
                                .onFocusChanged { isCloseFocused = it.isFocused }
                                .focusable()
                        ) {
                            Text(
                                "Закрыть (ОК)",
                                color = if (isCloseFocused) Color.Black else Color.White,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color(0xFF232D42))
                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFF0C101A), RoundedCornerShape(10.dp))
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = fullMsg.text,
                            color = Color.White,
                            fontSize = 16.sp,
                            lineHeight = 24.sp
                        )
                    }
                }
            }
        }
    }

    // Модальное окно информации и профиля чата (официальный стиль Telegram)
    if (showProfileDialog) {
        Dialog(onDismissRequest = { showProfileDialog = false }) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131826)),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF38BDF8)),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .fillMaxHeight(0.85f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    // Заголовок карточки
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Информация о профиле",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        var isCloseDialogFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { showProfileDialog = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCloseDialogFocused) Color(0xFF38BDF8) else Color(0xFF26334D)
                            ),
                            modifier = Modifier
                                .onFocusChanged { isCloseDialogFocused = it.isFocused }
                                .focusable()
                        ) {
                            Text(
                                "Закрыть",
                                color = if (isCloseDialogFocused) Color.Black else Color.White,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isLoadingProfile) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFF38BDF8))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Загрузка данных профиля...", color = Color.Gray, fontSize = 14.sp)
                            }
                        }
                    } else {
                        val details = profileDetails
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            // Блок аватара и названия
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val avatarBitmap = remember(details?.avatarPath) {
                                    val path = details?.avatarPath
                                    if (!path.isNullOrBlank()) {
                                        try {
                                            val f = File(path)
                                            if (f.exists() && f.length() > 0) {
                                                BitmapFactory.decodeFile(path)?.asImageBitmap()
                                            } else null
                                        } catch (_: Throwable) {
                                            null
                                        }
                                    } else null
                                }

                                if (avatarBitmap != null) {
                                    Image(
                                        bitmap = avatarBitmap,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    val firstLetter = chatTitle.trim().take(1).uppercase().ifBlank { "#" }
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF0284C7))
                                    ) {
                                        Text(
                                            text = firstLetter,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 28.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(18.dp))

                                Column {
                                    Text(
                                        text = details?.title ?: chatTitle,
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = details?.chatTypeDescription ?: "Чат Telegram",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (details?.memberCount != null && details.memberCount > 0) {
                                        Text(
                                            text = "Участников / подписчиков: ${details.memberCount}",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                            Divider(color = Color(0xFF232D42))
                            Spacer(modifier = Modifier.height(16.dp))

                            // Username / Ссылка
                            if (!details?.username.isNullOrBlank()) {
                                Text(
                                    text = "Имя пользователя / Ссылка:",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "@${details?.username} • https://t.me/${details?.username}",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                            }

                            if (!details?.inviteLink.isNullOrBlank()) {
                                Text(
                                    text = "Ссылка-приглашение:",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = details?.inviteLink.orEmpty(),
                                    color = Color(0xFF38BDF8),
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                            }

                            // Описание (Bio / Description)
                            Text(
                                text = "Описание:",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = details?.description ?: "Описание отсутствует",
                                color = if (details?.description != null) Color.White else Color.Gray,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // ID чата в TDLib
                            Text(
                                text = "Идентификатор (Telegram ID):",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$chatId",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
