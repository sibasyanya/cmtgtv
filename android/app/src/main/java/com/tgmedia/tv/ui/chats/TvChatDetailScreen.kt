package com.tgmedia.tv.ui.chats

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.*
import com.tgmedia.tv.data.tdlib.TdLibManager
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
 * 10-Foot UI экран чтения канала/чата с пультом ТВ.
 * Синхронизирует сообщения напрямую с Telegram аккаунтом в реальном времени (Local-First + TDLib events).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
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

    var messagesList by remember { mutableStateOf<List<UiMessage>>(emptyList()) }
    var isLoadingHistory by remember { mutableStateOf(true) }

    var inputText by remember { mutableStateOf("") }
    var isInputFocused by remember { mutableStateOf(false) }
    var isSendFocused by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Получаем реальный объект чата для аватара
    val chatObj = remember(chatId, tdLibManager.chatsList) {
        tdLibManager.chatsMap[chatId]
    }
    val chatAvatarPath = remember(chatObj, downloadedFiles) {
        tdLibManager.getChatPhotoPath(chatObj)
    }

    // Первичная загрузка реальной истории сообщений чата и открытие чата в TDLib
    LaunchedEffect(chatId) {
        isLoadingHistory = true
        tdLibManager.openChat(chatId)

        val historyResult = tdLibManager.getChatHistory(chatId = chatId, fromMessageId = 0, limit = 50)
        val rawMessages = historyResult.getOrNull().orEmpty()

        // TDLib возвращает сообщения от новых к старым. Для нормального чтения сверху-вниз реверсируем.
        val converted = rawMessages.reversed().map { msg ->
            val senderName = tdLibManager.getSenderName(msg.senderId)
            msg.toUiMessage(senderName)
        }
        messagesList = converted
        isLoadingHistory = false

        // Отмечаем сообщения прочитанными
        if (rawMessages.isNotEmpty()) {
            val ids = rawMessages.map { it.id }.toLongArray()
            tdLibManager.viewMessages(chatId, ids)
        }

        // Прокручиваем к последнему сообщению
        if (converted.isNotEmpty()) {
            listState.scrollToItem(converted.size - 1)
        }
    }

    // Мгновенная синхронизация входящих и исходящих сообщений в реальном времени через TDLib updates
    LaunchedEffect(chatId) {
        tdLibManager.updates.collect { update ->
            when (update) {
                is TdApi.UpdateNewMessage -> {
                    if (update.message.chatId == chatId) {
                        val senderName = tdLibManager.getSenderName(update.message.senderId)
                        val uiMsg = update.message.toUiMessage(senderName)

                        // Исключаем дубликаты (например, если отправляли локально)
                        val exists = messagesList.any { it.id == uiMsg.id }
                        if (!exists) {
                            messagesList = messagesList + uiMsg
                            scope.launch {
                                if (messagesList.isNotEmpty()) {
                                    listState.animateScrollToItem(messagesList.size - 1)
                                }
                            }
                        }

                        // Отмечаем новое сообщение прочитанным
                        tdLibManager.viewMessages(chatId, longArrayOf(update.message.id))
                    }
                }
                is TdApi.UpdateMessageContent -> {
                    if (update.chatId == chatId) {
                        // Обновляем текст/содержимое отредактированного сообщения
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
                else -> Unit
            }
        }
    }

    // При выходе закрываем чат в TDLib
    DisposableEffect(chatId) {
        onDispose {
            tdLibManager.closeChat(chatId)
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

        scope.launch {
            tdLibManager.sendMessage(chatId, currentText)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090A0F))
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        // Шапка чата с аватаром
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { onBack() },
                    modifier = Modifier.focusable()
                ) {
                    Text("← Назад к чатам", fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))

                // Аватарка чата
                val avatarBitmap = remember(chatAvatarPath) {
                    if (!chatAvatarPath.isNullOrBlank() && File(chatAvatarPath).exists()) {
                        try {
                            BitmapFactory.decodeFile(chatAvatarPath)?.asImageBitmap()
                        } catch (_: Exception) {
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
                            .size(36.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Text(
                    text = chatTitle,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Text(
                text = "ID: $chatId",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Область сообщений
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(messagesList, key = { it.id }) { msg ->
                    val alignment = if (msg.isOutgoing) Alignment.End else Alignment.Start
                    val bgBubbleColor = if (msg.isOutgoing) Color(0xFF0284C7) else Color(0xFF1B1E2B)

                    Column(
                        horizontalAlignment = alignment,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 600.dp)
                                .background(bgBubbleColor, RoundedCornerShape(14.dp))
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
                                    lineHeight = 19.sp
                                )
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

        Spacer(modifier = Modifier.height(12.dp))

        // Быстрые ответы для ТВ-пульта
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

        Spacer(modifier = Modifier.height(12.dp))

        // Строка ввода и кнопка отправки
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isInputFocused = it.isFocused }
                    .background(Color(0xFF131520), RoundedCornerShape(12.dp))
                    .border(
                        width = if (isInputFocused) 3.dp else 1.dp,
                        color = if (isInputFocused) Color(0xFF38BDF8) else Color(0xFF262A3C),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (inputText.isEmpty() && !isInputFocused) {
                    Text(
                        text = "Нажмите ОК для ввода текста клавиатурой или голосом...",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(Color(0xFF38BDF8)),
                    modifier = Modifier.fillMaxWidth()
                )
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
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}
