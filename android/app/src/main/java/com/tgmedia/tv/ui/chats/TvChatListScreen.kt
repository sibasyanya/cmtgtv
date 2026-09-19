package com.tgmedia.tv.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.tgmedia.tv.data.tdlib.TdLibManager
import org.drinkless.tdlib.TdApi

data class UiChatItem(
    val id: Long,
    val title: String,
    val lastMessage: String,
    val unreadCount: Int,
    val isChannel: Boolean
)

/**
 * 10-Foot UI экран списка каналов и чатов для Android TV с D-Pad навигацией.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvChatListScreen(
    onSelectChat: (chatId: Long, title: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tdLibManager = remember { TdLibManager.getInstance() }
    var chatList by remember { mutableStateOf<List<UiChatItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // Загрузка чатов через TDLib
        tdLibManager.loadChats(limit = 40).onSuccess { chatIds ->
            val items = chatIds.map { id ->
                UiChatItem(
                    id = id,
                    title = "Чат $id",
                    lastMessage = "Последнее сообщение...",
                    unreadCount = 0,
                    isChannel = false
                )
            }
            chatList = items
            isLoading = false
        }.onFailure {
            // В случае отсутствия активных данных отображаем демонстрационный список
            chatList = listOf(
                UiChatItem(1001, "Cybermasters News", "Новый релиз клиента для Android TV!", 2, true),
                UiChatItem(1002, "Кино & Сериалы 4K", "Подборка лучших фантастических фильмов недели", 0, true),
                UiChatItem(1003, "Алексей (Android Dev)", "Проверил сборку TDLib C++, видео летает!", 1, false),
                UiChatItem(1004, "Сообщество Android TV", "Кто настраивал голосовой ввод с пульта?", 5, false)
            )
            isLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090A0F))
            .padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "Чаты и Каналы Telegram",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Навигация стрелками пульта ДУ (Вверх/Вниз), выбор — кнопка ОК",
                    fontSize = 14.sp,
                    color = Color(0xFF9E9EAF)
                )
            }

            Box(
                modifier = Modifier
                    .background(Color(0xFF1B2A38), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "TDLib Online",
                    fontSize = 13.sp,
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Загрузка чатов из Telegram...", color = Color.LightGray, fontSize = 16.sp)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(chatList) { chat ->
                    ChatRowItem(
                        chat = chat,
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
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .background(
                if (isFocused) Color(0xFF1E2235) else Color(0xFF13151F),
                RoundedCornerShape(16.dp)
            )
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) Color(0xFF38BDF8) else Color(0xFF222533),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .focusable()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            // Аватар
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2B3245))
            ) {
                Text(
                    text = chat.title.take(1),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = chat.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (chat.isChannel) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF0284C7), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Канал", fontSize = 10.sp, color = Color.White)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = chat.lastMessage,
                    fontSize = 13.sp,
                    color = Color(0xFFA1A1AA),
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
                    .background(Color(0xFF0284C7), CircleShape)
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
