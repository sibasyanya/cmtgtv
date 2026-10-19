package com.tgmedia.tv.ui.chats

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tgmedia.tv.data.tdlib.TdLibManager
import com.tgmedia.tv.ui.proxy.TgAccentCyan
import com.tgmedia.tv.ui.proxy.TgCardBg
import com.tgmedia.tv.ui.proxy.TgDarkBg
import com.tgmedia.tv.ui.proxy.TgPrimary
import com.tgmedia.tv.ui.proxy.TgSurface
import com.tgmedia.tv.ui.proxy.TgTextSecondary
import com.tgmedia.tv.ui.proxy.TgTextWhite
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.io.File

data class UiChatItem(
    val id: Long,
    val title: String,
    val lastMessage: String,
    val unreadCount: Int,
    val isChannel: Boolean,
    val avatarPath: String?
)

/**
 * Преобразование реального TDLib объекта Chat в отображаемый элемент интерфейса ТВ.
 */
fun TdApi.Chat.toUiChatItem(avatarPath: String?): UiChatItem {
    val isChan = when (val t = this.type) {
        is TdApi.ChatTypeSupergroup -> t.isChannel
        else -> false
    }
    val preview = when (val msg = this.lastMessage?.content) {
        is TdApi.MessageText -> msg.text.text
        is TdApi.MessagePhoto -> if (msg.caption.text.isNotBlank()) "🖼️ ${msg.caption.text}" else "🖼️ Фото"
        is TdApi.MessageVideo -> if (msg.caption.text.isNotBlank()) "📹 ${msg.caption.text}" else "📹 Видео"
        is TdApi.MessageDocument -> if (msg.caption.text.isNotBlank()) "📄 ${msg.caption.text}" else "📄 Документ"
        is TdApi.MessageVoiceNote -> "🎤 Голосовое сообщение"
        is TdApi.MessageAudio -> "🎵 Аудиозапись"
        is TdApi.MessageSticker -> "Стикер ${msg.sticker.emoji}"
        is TdApi.MessageAnimation -> "GIF анимация"
        is TdApi.MessageContact -> "Контакт: ${msg.contact.firstName}"
        is TdApi.MessageLocation -> "Геолокация"
        null -> "Нет сообщений"
        else -> "Сообщение"
    }
    return UiChatItem(
        id = this.id,
        title = this.title.ifBlank { "Чат ${this.id}" },
        lastMessage = preview,
        unreadCount = this.unreadCount,
        isChannel = isChan,
        avatarPath = avatarPath
    )
}

/**
 * 10-Foot UI экран списка каналов и чатов для Android TV с D-Pad навигацией.
 * Отображает реальные чаты аккаунта из TDLib, сохраняет позицию скролла при возврате
 * и загружает реальные аватарки каналов и чатов.
 */
@Composable
fun TvChatListScreen(
    onSelectChat: (chatId: Long, title: String) -> Unit,
    onOpenAccountSettings: () -> Unit,
    listState: LazyListState,
    lastSelectedChatId: Long? = null,
    modifier: Modifier = Modifier
) {
    val tdLibManager = remember { TdLibManager.getInstance() }
    val chatsFromTdLib by tdLibManager.chatsList.collectAsState()
    val currentUser by tdLibManager.currentUser.collectAsState()
    val downloadedFiles by tdLibManager.fileDownloadedState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }

    fun refreshChats() {
        isLoading = true
        coroutineScope.launch {
            tdLibManager.loadCurrentUser()
            tdLibManager.loadDetailedChats(50)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        if (chatsFromTdLib.isEmpty()) {
            refreshChats()
        } else {
            tdLibManager.loadCurrentUser()
        }
    }

    // Реактивное обновление элементов чатов с путями к скачанным аватаркам
    val displayList = remember(chatsFromTdLib, downloadedFiles) {
        chatsFromTdLib.map { chat ->
            val photoPath = tdLibManager.getChatPhotoPath(chat)
            chat.toUiChatItem(photoPath)
        }
    }

    // Отслеживаем активный ID чата для мгновенного восстановления фокуса и рамки выделения
    var currentFocusedChatId by remember { mutableStateOf(lastSelectedChatId) }

    // При возврате в ленту чатов автоматически прокручиваем к выбранному чату
    LaunchedEffect(displayList, lastSelectedChatId) {
        if (displayList.isNotEmpty() && lastSelectedChatId != null) {
            val targetIndex = displayList.indexOfFirst { it.id == lastSelectedChatId }
            if (targetIndex >= 0) {
                try {
                    listState.scrollToItem(targetIndex)
                } catch (_: Exception) {}
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TgDarkBg)
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        // Шапка экрана с навигацией
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "Чаты и Каналы Telegram",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TgTextWhite
                )
                Text(
                    text = "Навигация стрелками пульта ДУ (Вверх/Вниз), выбор — кнопка ОК",
                    fontSize = 13.sp,
                    color = TgTextSecondary
                )
            }

            // Правый блок действий в шапке
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Кнопка обновления
                var isRefreshFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = { refreshChats() },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRefreshFocused) TgAccentCyan else TgSurface
                    ),
                    modifier = Modifier
                        .onFocusChanged { isRefreshFocused = it.isFocused }
                        .height(42.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = if (isRefreshFocused) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Обновить",
                        color = if (isRefreshFocused) Color.Black else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Кнопка настроек аккаунта и профиля
                val profileName = currentUser?.firstName?.ifBlank { null }
                    ?: currentUser?.usernames?.activeUsernames?.firstOrNull()
                    ?: "Аккаунт"
                val userAvatarPath = remember(currentUser, downloadedFiles) {
                    tdLibManager.getUserPhotoPath(currentUser)
                }

                var isProfileFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = onOpenAccountSettings,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isProfileFocused) TgPrimary else TgSurface
                    ),
                    modifier = Modifier
                        .onFocusChanged { isProfileFocused = it.isFocused }
                        .height(42.dp)
                ) {
                    if (!userAvatarPath.isNullOrBlank()) {
                        val bitmap = remember(userAvatarPath) {
                            try {
                                val f = File(userAvatarPath)
                                if (f.exists() && f.length() > 0) {
                                    BitmapFactory.decodeFile(userAvatarPath)?.asImageBitmap()
                                } else null
                            } catch (_: Throwable) {
                                null
                            }
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Профиль ($profileName)",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (isLoading && displayList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = TgAccentCyan)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Загрузка чатов из вашего Telegram аккаунта...", color = TgTextWhite, fontSize = 16.sp)
                }
            }
        } else if (displayList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = TgCardBg),
                    modifier = Modifier.width(540.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Чаты не найдены",
                            color = TgTextWhite,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "В вашем аккаунте пока нет синхронизированных диалогов или TDLib выполняет начальную загрузку каналов.",
                            color = TgTextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { refreshChats() },
                            colors = ButtonDefaults.buttonColors(containerColor = TgPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Повторить загрузку чатов", color = Color.White)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayList, key = { it.id }) { chat ->
                    val shouldAutoFocus = (chat.id == currentFocusedChatId || (currentFocusedChatId == null && chat.id == displayList.firstOrNull()?.id))
                    ChatRowItem(
                        chat = chat,
                        isTargetFocused = shouldAutoFocus,
                        onFocused = { currentFocusedChatId = chat.id },
                        onClick = { onSelectChat(chat.id, chat.title) }
                    )
                }
            }
        }
    }
}

@Composable
fun ChatRowItem(
    chat: UiChatItem,
    isTargetFocused: Boolean = false,
    onFocused: () -> Unit = {},
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    // Активно восстанавливаем фокус при монтировании элемента
    LaunchedEffect(isTargetFocused) {
        if (isTargetFocused) {
            for (attempt in 1..6) {
                kotlinx.coroutines.delay(50L * attempt)
                try {
                    focusRequester.requestFocus()
                    break
                } catch (_: Exception) {}
            }
        }
    }

    // Рамка выделения гарантированно присутствует и не пропадает при возврате
    val isVisuallyFocused = isFocused || isTargetFocused

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isVisuallyFocused) Color(0xFF1E293B) else TgCardBg)
            .border(
                width = if (isVisuallyFocused) 4.dp else 1.dp,
                color = if (isVisuallyFocused) Color(0xFF38BDF8) else Color(0xFF222B3D),
                shape = RoundedCornerShape(14.dp)
            )
            .androidx.compose.ui.focus.focusRequester(focusRequester)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) {
                    onFocused()
                }
            }
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            // Реальный аватар чата/канала или заглушка с инициалом при отсутствии/скачивании
            val avatarPath = chat.avatarPath
            val avatarBitmap = remember(avatarPath) {
                if (!avatarPath.isNullOrBlank()) {
                    try {
                        val f = File(avatarPath)
                        if (f.exists() && f.length() > 0) {
                            BitmapFactory.decodeFile(avatarPath)?.asImageBitmap()
                        } else null
                    } catch (_: Throwable) {
                        null
                    }
                } else null
            }

            if (avatarBitmap != null) {
                Image(
                    bitmap = avatarBitmap,
                    contentDescription = chat.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                )
            } else {
                val firstLetter = chat.title.trim().take(1).uppercase().ifBlank { "#" }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (chat.isChannel) TgPrimary else Color(0xFF334155))
                ) {
                    Text(
                        text = firstLetter,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = chat.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TgTextWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (chat.isChannel) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(TgPrimary, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Канал", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = chat.lastMessage,
                    fontSize = 13.sp,
                    color = if (isFocused) Color(0xFFE2E8F0) else TgTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (chat.unreadCount > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(26.dp)
                    .background(TgPrimary, CircleShape)
            ) {
                Text(
                    text = "${chat.unreadCount}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
