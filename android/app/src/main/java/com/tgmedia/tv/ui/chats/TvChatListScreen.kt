package com.tgmedia.tv.ui.chats

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.window.DialogProperties
import com.tgmedia.tv.data.tdlib.TdLibManager
import com.tgmedia.tv.ui.proxy.TgAccentCyan
import com.tgmedia.tv.ui.proxy.TgCardBg
import com.tgmedia.tv.ui.proxy.TgDarkBg
import com.tgmedia.tv.ui.proxy.TgPrimary
import com.tgmedia.tv.ui.proxy.TgSurface
import com.tgmedia.tv.ui.proxy.TgTextSecondary
import com.tgmedia.tv.ui.proxy.TgTextWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
 * Отображает реальные чаты аккаунта из TDLib в режиме непрерывной реактивной синхронизации,
 * поддерживает глобальный поиск (по своим чатам, публичным каналам/группам и сообщениям)
 * и сохраняет позицию скролла при возврате.
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
    val chatFolders by tdLibManager.chatFolders.collectAsState()
    val archiveCount by tdLibManager.archiveChatsCount.collectAsState()
    val currentChatListType by tdLibManager.currentChatListType.collectAsState()
    val currentUser by tdLibManager.currentUser.collectAsState()
    val downloadedFiles by tdLibManager.fileDownloadedState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var showGlobalSearchDialog by remember { mutableStateOf(false) }

    fun loadInitialChats() {
        isLoading = true
        coroutineScope.launch {
            tdLibManager.loadCurrentUser()
            when (val cur = currentChatListType) {
                is com.tgmedia.tv.data.tdlib.ChatListType.Main -> tdLibManager.loadDetailedChats(TdApi.ChatListMain(), 50)
                is com.tgmedia.tv.data.tdlib.ChatListType.Archive -> tdLibManager.loadDetailedChats(TdApi.ChatListArchive(), 50)
                is com.tgmedia.tv.data.tdlib.ChatListType.Folder -> tdLibManager.loadDetailedChats(TdApi.ChatListFolder(cur.folderId), 50)
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        if (chatsFromTdLib.isEmpty()) {
            loadInitialChats()
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
            .padding(horizontal = 48.dp, vertical = 20.dp)
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
                    text = "Навигация стрелками пульта ДУ (Вверх/Вниз/Влево/Вправо), выбор — кнопка ОК",
                    fontSize = 12.sp,
                    color = TgTextSecondary
                )
            }

            // Правый блок действий в шапке (Глобальный поиск + Профиль)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Кнопка глобального поиска
                var isSearchFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = { showGlobalSearchDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSearchFocused) TgAccentCyan else TgSurface
                    ),
                    modifier = Modifier
                        .onFocusChanged { isSearchFocused = it.isFocused }
                        .border(
                            width = if (isSearchFocused) 2.5.dp else 1.dp,
                            color = if (isSearchFocused) Color.White else Color(0xFF222B3D),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .height(40.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Глобальный поиск",
                        tint = if (isSearchFocused) Color.Black else TgAccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Глобальный поиск",
                        color = if (isSearchFocused) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
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
                        .height(40.dp)
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
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Панель вкладок папок и архива (Пункт 3 и 4: Синхронизация с аккаунтом)
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Вкладка "Все чаты"
            item {
                val isSelected = currentChatListType is com.tgmedia.tv.data.tdlib.ChatListType.Main
                var isTabFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isTabFocused -> Color(0xFF38BDF8)
                                isSelected -> Color(0xFF0284C7)
                                else -> Color(0xFF131722)
                            }
                        )
                        .border(
                            width = if (isTabFocused) 3.dp else if (isSelected) 2.dp else 1.dp,
                            color = if (isTabFocused) Color.White else if (isSelected) Color(0xFF38BDF8) else Color(0xFF222B3D),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .onFocusChanged { isTabFocused = it.isFocused }
                        .clickable {
                            tdLibManager.selectChatList(com.tgmedia.tv.data.tdlib.ChatListType.Main)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Все чаты",
                        color = if (isTabFocused) Color.Black else Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected || isTabFocused) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }

            // Пользовательские папки из Telegram (автоматически синхронизированы)
            items(chatFolders) { folder ->
                val folderName = folder.name?.text?.text?.ifBlank { "Папка" } ?: "Папка"
                val isSelected = (currentChatListType as? com.tgmedia.tv.data.tdlib.ChatListType.Folder)?.folderId == folder.id
                var isTabFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isTabFocused -> Color(0xFF38BDF8)
                                isSelected -> Color(0xFF0284C7)
                                else -> Color(0xFF131722)
                            }
                        )
                        .border(
                            width = if (isTabFocused) 3.dp else if (isSelected) 2.dp else 1.dp,
                            color = if (isTabFocused) Color.White else if (isSelected) Color(0xFF38BDF8) else Color(0xFF222B3D),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .onFocusChanged { isTabFocused = it.isFocused }
                        .clickable {
                            tdLibManager.selectChatList(com.tgmedia.tv.data.tdlib.ChatListType.Folder(folder.id, folderName))
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "📁 $folderName",
                        color = if (isTabFocused) Color.Black else Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected || isTabFocused) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }

            // Вкладка "Архив" со счетчиком чатов (Пункт 3)
            item {
                val isSelected = currentChatListType is com.tgmedia.tv.data.tdlib.ChatListType.Archive
                var isTabFocused by remember { mutableStateOf(false) }
                val label = if (archiveCount > 0) "📦 Архив ($archiveCount)" else "📦 Архив"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isTabFocused -> Color(0xFF38BDF8)
                                isSelected -> Color(0xFF0284C7)
                                else -> Color(0xFF131722)
                            }
                        )
                        .border(
                            width = if (isTabFocused) 3.dp else if (isSelected) 2.dp else 1.dp,
                            color = if (isTabFocused) Color.White else if (isSelected) Color(0xFF38BDF8) else Color(0xFF222B3D),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .onFocusChanged { isTabFocused = it.isFocused }
                        .clickable {
                            tdLibManager.selectChatList(com.tgmedia.tv.data.tdlib.ChatListType.Archive)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isTabFocused) Color.Black else Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected || isTabFocused) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isLoading && displayList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = TgAccentCyan)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Синхронизация чатов с вашим Telegram аккаунтом...", color = TgTextWhite, fontSize = 16.sp)
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
                        val emptyTitle = when (currentChatListType) {
                            is com.tgmedia.tv.data.tdlib.ChatListType.Archive -> "Архив пуст"
                            is com.tgmedia.tv.data.tdlib.ChatListType.Folder -> "Папка пуста"
                            else -> "Чаты не найдены"
                        }
                        val emptyDesc = when (currentChatListType) {
                            is com.tgmedia.tv.data.tdlib.ChatListType.Archive -> "Все диалоги, которые вы архивируете в официальном Telegram на других устройствах, отобразятся здесь."
                            is com.tgmedia.tv.data.tdlib.ChatListType.Folder -> "В этой папке пока нет добавленных или синхронизированных диалогов."
                            else -> "В вашем аккаунте пока нет синхронизированных диалогов или TDLib выполняет начальную загрузку каналов."
                        }
                        Text(
                            text = emptyTitle,
                            color = TgTextWhite,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = emptyDesc,
                            color = TgTextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { showGlobalSearchDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = TgPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Открыть глобальный поиск", color = Color.White)
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

    if (showGlobalSearchDialog) {
        GlobalSearchDialog(
            tdLibManager = tdLibManager,
            downloadedFiles = downloadedFiles,
            onDismiss = { showGlobalSearchDialog = false },
            onSelectChat = { chatId, title ->
                showGlobalSearchDialog = false
                onSelectChat(chatId, title)
            }
        )
    }
}

private enum class SearchTabFilter {
    ALL,
    LOCAL_CHATS,
    PUBLIC_GLOBAL,
    MESSAGES
}

@Composable
private fun GlobalSearchDialog(
    tdLibManager: TdLibManager,
    downloadedFiles: Map<Int, String>,
    onDismiss: () -> Unit,
    onSelectChat: (chatId: Long, title: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val inputFocusRequester = remember { FocusRequester() }

    var searchQuery by remember { mutableStateOf("") }
    var isEditingQuery by remember { mutableStateOf(false) }
    var isInputBoxFocused by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(SearchTabFilter.ALL) }
    var searchResult by remember { mutableStateOf(TdLibManager.GlobalSearchResult()) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onDismiss)

    // Автоматический реактивный поиск при изменении строки запроса (с debounce 350 мс)
    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            searchResult = TdLibManager.GlobalSearchResult()
            isSearching = false
            statusBanner = null
            return@LaunchedEffect
        }
        isSearching = true
        delay(350)
        try {
            searchResult = tdLibManager.performGlobalSearch(q)
        } catch (_: Exception) {}
        isSearching = false
    }

    fun executeManualSearch() {
        val q = searchQuery.trim()
        if (q.isEmpty()) return
        isEditingQuery = false
        keyboardController?.hide()
        isSearching = true
        scope.launch {
            try {
                searchResult = tdLibManager.performGlobalSearch(q)
            } catch (_: Exception) {}
            isSearching = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E131F)),
            modifier = Modifier
                .width(920.dp)
                .fillMaxHeight(0.88f)
                .border(2.dp, TgAccentCyan, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(26.dp)
            ) {
                // Шапка окна глобального поиска
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "🔍 Глобальный поиск Telegram",
                            color = TgTextWhite,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Поиск по вашим диалогам, публичным каналам (@username / t.me) и тексту сообщений",
                            color = TgTextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    var isCloseFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCloseFocused) TgAccentCyan else Color(0xFF1E293B)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .onFocusChanged { isCloseFocused = it.isFocused }
                            .border(
                                width = if (isCloseFocused) 2.5.dp else 1.dp,
                                color = if (isCloseFocused) Color.White else Color(0xFF334155),
                                shape = RoundedCornerShape(10.dp)
                            )
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = if (isCloseFocused) Color.Black else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Закрыть (Назад)",
                            color = if (isCloseFocused) Color.Black else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Строка ввода поискового запроса (клавиатура строго по кнопке ОК)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF151C2C))
                            .border(
                                width = if (isInputBoxFocused) 3.5.dp else 1.dp,
                                color = if (isInputBoxFocused) TgAccentCyan else Color(0xFF28354E),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .onFocusChanged { isInputBoxFocused = it.isFocused }
                            .clickable {
                                isEditingQuery = true
                                scope.launch {
                                    delay(80)
                                    try {
                                        inputFocusRequester.requestFocus()
                                        keyboardController?.show()
                                    } catch (_: Exception) {}
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = TgAccentCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (isEditingQuery) {
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                                        cursorBrush = SolidColor(TgAccentCyan),
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(inputFocusRequester)
                                    )
                                } else {
                                    Text(
                                        text = if (searchQuery.isNotBlank()) searchQuery else "Нажмите ОК для ввода названия канала, @username, ссылки t.me или текста...",
                                        color = if (searchQuery.isNotBlank()) Color.White else Color(0xFF94A3B8),
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    if (searchQuery.isNotBlank()) {
                        var isClearFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = {
                                searchQuery = ""
                                isEditingQuery = false
                                keyboardController?.hide()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isClearFocused) TgAccentCyan else Color(0xFF1E293B)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(46.dp)
                                .onFocusChanged { isClearFocused = it.isFocused }
                        ) {
                            Text(
                                "Сброс",
                                color = if (isClearFocused) Color.Black else Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    var isFindBtnFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = { executeManualSearch() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFindBtnFocused) TgAccentCyan else TgPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(46.dp)
                            .onFocusChanged { isFindBtnFocused = it.isFocused }
                            .border(
                                width = if (isFindBtnFocused) 2.5.dp else 1.dp,
                                color = if (isFindBtnFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Text(
                            "Найти (ОК)",
                            color = if (isFindBtnFocused) Color.Black else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Вкладки фильтрации результатов поиска
                val totalCount = searchResult.localChats.size + searchResult.publicChats.size + searchResult.messages.size
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val tabs = listOf(
                        SearchTabFilter.ALL to "Все результаты ($totalCount)",
                        SearchTabFilter.LOCAL_CHATS to "Мои чаты (${searchResult.localChats.size})",
                        SearchTabFilter.PUBLIC_GLOBAL to "Глобальные каналы (${searchResult.publicChats.size})",
                        SearchTabFilter.MESSAGES to "Сообщения (${searchResult.messages.size})"
                    )
                    items(tabs) { (tabType, label) ->
                        val isSelected = activeTab == tabType
                        var isTabFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        isTabFocused -> TgAccentCyan
                                        isSelected -> TgPrimary
                                        else -> Color(0xFF151C2C)
                                    }
                                )
                                .border(
                                    width = if (isTabFocused) 2.5.dp else 1.dp,
                                    color = if (isTabFocused) Color.White else if (isSelected) TgAccentCyan else Color(0xFF25324B),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .onFocusChanged { isTabFocused = it.isFocused }
                                .clickable { activeTab = tabType }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isTabFocused) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected || isTabFocused) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                statusBanner?.let { banner ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = banner,
                        color = TgAccentCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Кнопка прямого перехода по введенной ссылке или @username
                val trimmedQuery = searchQuery.trim()
                if (trimmedQuery.startsWith("@") || trimmedQuery.contains("t.me/") || trimmedQuery.startsWith("tg://")) {
                    var isDirectLinkFocused by remember { mutableStateOf(false) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDirectLinkFocused) TgAccentCyan else Color(0xFF122B40))
                            .border(
                                width = if (isDirectLinkFocused) 3.dp else 1.dp,
                                color = if (isDirectLinkFocused) Color.White else TgAccentCyan,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .onFocusChanged { isDirectLinkFocused = it.isFocused }
                            .clickable {
                                scope.launch {
                                    statusBanner = "Переход по ссылке $trimmedQuery..."
                                    when (val act = tdLibManager.resolveAndHandleLink(trimmedQuery)) {
                                        is TdLibManager.ResolvedLinkAction.OpenChat -> {
                                            onSelectChat(act.chatId, act.chatTitle)
                                        }
                                        is TdLibManager.ResolvedLinkAction.ProxyImported -> {
                                            statusBanner = act.message
                                        }
                                        is TdLibManager.ResolvedLinkAction.Error -> {
                                            statusBanner = act.message
                                        }
                                        is TdLibManager.ResolvedLinkAction.OpenExternalWeb -> {
                                            statusBanner = "Внешняя ссылка: ${act.url}"
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "✈️ Открыть напрямую ссылку или @юзернейм: $trimmedQuery",
                            color = if (isDirectLinkFocused) Color.Black else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Перейти (ОК) →",
                            color = if (isDirectLinkFocused) Color.Black else TgAccentCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Список результатов
                if (isSearching && totalCount == 0) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = TgAccentCyan)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Глобальный поиск в Telegram...", color = TgTextWhite, fontSize = 14.sp)
                        }
                    }
                } else if (trimmedQuery.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Введите текст, @username или ссылку t.me для поиска каналов, групп, ботов и сообщений",
                            color = TgTextSecondary,
                            fontSize = 14.sp
                        )
                    }
                } else if (totalCount == 0 && !isSearching) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "По запросу «$trimmedQuery» ничего не найдено",
                            color = TgTextSecondary,
                            fontSize = 15.sp
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // 1. Секция: Мои чаты
                        if ((activeTab == SearchTabFilter.ALL || activeTab == SearchTabFilter.LOCAL_CHATS) &&
                            searchResult.localChats.isNotEmpty()
                        ) {
                            item(key = "header_local") {
                                Text(
                                    text = "МОИ ЧАТЫ И КАНАЛЫ (${searchResult.localChats.size})",
                                    color = TgAccentCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                )
                            }
                            items(searchResult.localChats, key = { "loc_${it.id}" }) { chat ->
                                val uiItem = remember(chat, downloadedFiles) {
                                    chat.toUiChatItem(tdLibManager.getChatPhotoPath(chat))
                                }
                                ChatRowItem(
                                    chat = uiItem,
                                    onClick = { onSelectChat(uiItem.id, uiItem.title) }
                                )
                            }
                        }

                        // 2. Секция: Глобальный поиск публичных каналов/групп
                        if ((activeTab == SearchTabFilter.ALL || activeTab == SearchTabFilter.PUBLIC_GLOBAL) &&
                            searchResult.publicChats.isNotEmpty()
                        ) {
                            item(key = "header_public") {
                                Text(
                                    text = "ГЛОБАЛЬНЫЙ ПОИСК TELEGRAM (${searchResult.publicChats.size})",
                                    color = Color(0xFF34D399),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                )
                            }
                            items(searchResult.publicChats, key = { "pub_${it.id}" }) { chat ->
                                val uiItem = remember(chat, downloadedFiles) {
                                    chat.toUiChatItem(tdLibManager.getChatPhotoPath(chat))
                                }
                                ChatRowItem(
                                    chat = uiItem.copy(
                                        lastMessage = "🌐 Публичный канал / чат Telegram • Нажмите ОК для открытия"
                                    ),
                                    onClick = { onSelectChat(uiItem.id, uiItem.title) }
                                )
                            }
                        }

                        // 3. Секция: Найденные сообщения во всех чатах
                        if ((activeTab == SearchTabFilter.ALL || activeTab == SearchTabFilter.MESSAGES) &&
                            searchResult.messages.isNotEmpty()
                        ) {
                            item(key = "header_messages") {
                                Text(
                                    text = "НАЙДЕННЫЕ СООБЩЕНИЯ (${searchResult.messages.size})",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                )
                            }
                            items(
                                searchResult.messages,
                                key = { "msg_${it.chatId}_${it.message.id}" }
                            ) { foundItem ->
                                var isMsgRowFocused by remember { mutableStateOf(false) }
                                val msgUi = remember(foundItem) {
                                    foundItem.message.toUiMessage(foundItem.senderName)
                                }
                                val dateStr = remember(foundItem.message.date) {
                                    try {
                                        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                                            .format(Date(foundItem.message.date * 1000L))
                                    } catch (_: Exception) {
                                        msgUi.time
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isMsgRowFocused) Color(0xFF1E293B) else TgCardBg)
                                        .border(
                                            width = if (isMsgRowFocused) 3.5.dp else 1.dp,
                                            color = if (isMsgRowFocused) TgAccentCyan else Color(0xFF222B3D),
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .onFocusChanged { isMsgRowFocused = it.isFocused }
                                        .clickable {
                                            onSelectChat(foundItem.chatId, foundItem.chatTitle)
                                        }
                                        .padding(horizontal = 18.dp, vertical = 12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = foundItem.chatTitle,
                                                color = TgAccentCyan,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "• ${foundItem.senderName}",
                                                color = TgTextSecondary,
                                                fontSize = 12.sp,
                                                maxLines = 1
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = msgUi.text,
                                            color = TgTextWhite,
                                            fontSize = 14.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = dateStr,
                                            color = TgTextSecondary,
                                            fontSize = 11.sp
                                        )
                                        if (isMsgRowFocused) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Открыть чат (ОК) →",
                                                color = TgAccentCyan,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
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
            .focusRequester(focusRequester)
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
