package com.tgmedia.tv.ui.proxy

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tgmedia.tv.data.tdlib.*
import kotlinx.coroutines.launch

// Цветовая палитра официального Telegram Desktop (Dark Theme)
val TgDarkBg = Color(0xFF17212B)
val TgSurface = Color(0xFF1E2C3A)
val TgCardBg = Color(0xFF242F3D)
val TgCardHover = Color(0xFF2B3A4C)
val TgPrimary = Color(0xFF5288C1)
val TgAccentCyan = Color(0xFF40A7E3)
val TgTextWhite = Color(0xFFF5F5F5)
val TgTextSecondary = Color(0xFF7E8C9B)
val TgTextMuted = Color(0xFF6C7883)
val TgDivider = Color(0xFF101921)
val TgErrorRed = Color(0xFFE53935)
val TgSuccessGreen = Color(0xFF4CAF50)

/**
 * Диалог «Настройки прокси», полностью повторяющий официальный клиент Telegram (скриншоты пользователя).
 */
@Composable
fun ProxySettingsDialog(
    onDismissRequest: () -> Unit
) {
    val tdLibManager = remember { TdLibManager.getInstance() }
    val proxyConfig by tdLibManager.telegramProxyConfig.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var showMenu by remember { mutableStateOf(false) }
    var editingProxy by remember { mutableStateOf<TelegramProxyItem?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var itemMenuProxyId by remember { mutableStateOf<String?>(null) }
    var isScrapingMtproto by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .width(620.dp)
                .heightIn(max = 720.dp)
                .padding(8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = TgDarkBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // 1. Шапка: Заголовок и меню трёх точек
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Настройки прокси",
                        color = TgTextWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Box {
                        IconButton(
                            onClick = { showMenu = !showMenu },
                            colors = IconButtonDefaults.iconButtonColors(contentColor = TgTextSecondary)
                        ) {
                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Меню")
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(TgCardBg)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Загрузить с mtproto.ru", color = TgTextWhite) },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = TgAccentCyan) },
                                onClick = {
                                    showMenu = false
                                    isScrapingMtproto = true
                                    tdLibManager.loadProxiesFromMtprotoRu { count, err ->
                                        isScrapingMtproto = false
                                        val msg = if (err != null) err else "Загружено и проверено новых прокси: $count"
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Добавить прокси из буфера обмена", color = TgTextWhite) },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = TgTextWhite) },
                                onClick = {
                                    showMenu = false
                                    val clipText = clipboardManager.getText()?.text
                                    if (clipText.isNullOrBlank()) {
                                        Toast.makeText(context, "Буфер обмена пуст", Toast.LENGTH_SHORT).show()
                                    } else {
                                        tdLibManager.importProxyFromClipboard(clipText) { ok, msg ->
                                            Toast.makeText(context, msg ?: "", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Удалить все", color = TgErrorRed) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = TgErrorRed) },
                                onClick = {
                                    showMenu = false
                                    tdLibManager.removeAllProxies()
                                    Toast.makeText(context, "Все прокси удалены", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Контент со скроллом
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // 2. Чекбокс: Через IPv6 (если возможно)
                    item {
                        TgCheckboxRow(
                            title = "Через IPv6 (если возможно)",
                            checked = proxyConfig.useIPv6,
                            onCheckedChange = { tdLibManager.setUseIPv6(it) }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // 3. Радиокнопки режимов соединения
                    item {
                        TgRadioRow(
                            title = "Отключить прокси",
                            selected = proxyConfig.mode == ProxyConnectionMode.DISABLED,
                            onClick = { tdLibManager.setProxyConnectionMode(ProxyConnectionMode.DISABLED) }
                        )

                        TgRadioRow(
                            title = "Использовать системные настройки прокси",
                            selected = proxyConfig.mode == ProxyConnectionMode.SYSTEM,
                            onClick = { tdLibManager.setProxyConnectionMode(ProxyConnectionMode.SYSTEM) }
                        )

                        TgRadioRow(
                            title = "Использовать собственный прокси",
                            selected = proxyConfig.mode == ProxyConnectionMode.CUSTOM,
                            onClick = { tdLibManager.setProxyConnectionMode(ProxyConnectionMode.CUSTOM) }
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // 4. Секция Автопереключения прокси (отображается при CUSTOM)
                    if (proxyConfig.mode == ProxyConnectionMode.CUSTOM) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(TgSurface)
                                    .padding(12.dp)
                            ) {
                                TgCheckboxRow(
                                    title = "Автопереключение прокси",
                                    checked = proxyConfig.autoSwitch,
                                    onCheckedChange = { tdLibManager.setAutoSwitch(it, proxyConfig.autoSwitchDelaySec) }
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Переключатель интервала: 5 с, 10 с, 15 с, 30 с, 60 с
                                val delayOptions = listOf(5, 10, 15, 30, 60)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    delayOptions.forEach { sec ->
                                        val isSelected = proxyConfig.autoSwitchDelaySec == sec
                                        TvChipButton(
                                            text = "$sec с",
                                            isSelected = isSelected,
                                            onClick = { tdLibManager.setAutoSwitch(proxyConfig.autoSwitch, sec) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Вы можете установить время ожидания до подключения к ближайшему активному прокси, если текущий перестанет работать.",
                                    color = TgTextMuted,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    // 5. Пояснительный текст
                    item {
                        Text(
                            text = "Использование прокси-сервера может помочь, если Telegram не удаётся установить соединение в Вашем регионе.",
                            color = TgTextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // 6. Список добавленных прокси
                    if (proxyConfig.proxies.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Список прокси пуст.\nНажмите «Загрузить с mtproto.ru» или «Добавить прокси».",
                                    color = TgTextMuted,
                                    fontSize = 13.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(proxyConfig.proxies, key = { it.id }) { item ->
                            val isSelected = proxyConfig.selectedProxyId == item.id && proxyConfig.mode == ProxyConnectionMode.CUSTOM

                            ProxyListItemRow(
                                item = item,
                                isSelected = isSelected,
                                onSelect = {
                                    tdLibManager.selectProxy(item.id)
                                },
                                onTest = {
                                    tdLibManager.testProxy(item.id) { success, latency, err ->
                                        val msg = if (success) "Доступен: ${latency} мс" else (err ?: "Недоступен")
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onEdit = {
                                    editingProxy = item
                                    showEditDialog = true
                                },
                                onDelete = {
                                    tdLibManager.removeProxy(item.id)
                                    Toast.makeText(context, "Прокси удален", Toast.LENGTH_SHORT).show()
                                }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 7. Нижний ряд кнопок: "Закрыть", "Загрузить с mtproto.ru", "Добавить прокси"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        colors = ButtonDefaults.textButtonColors(contentColor = TgAccentCyan)
                    ) {
                        Text("Закрыть", fontSize = 14.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                isScrapingMtproto = true
                                tdLibManager.loadProxiesFromMtprotoRu { count, err ->
                                    isScrapingMtproto = false
                                    val msg = if (err != null) err else "Получено и проверено прокси с mtproto.ru: $count"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TgSurface),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isScrapingMtproto) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = TgAccentCyan,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null, tint = TgAccentCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("С mtproto.ru", color = TgAccentCyan, fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                editingProxy = null
                                showEditDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TgPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Добавить прокси", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    // Диалог редактирования / добавления прокси (Скриншоты 6, 7, 8, 9)
    if (showEditDialog) {
        ProxyEditDialog(
            initialItem = editingProxy,
            onDismiss = { showEditDialog = false },
            onSave = { savedItem ->
                showEditDialog = false
                tdLibManager.addOrUpdateProxy(savedItem, testFirst = true) { isAlive, _ ->
                    val statusStr = if (isAlive) "Прокси проверен и работает!" else "Внимание: Прокси добавлен, но не отвечает."
                    Toast.makeText(context, statusStr, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

/**
 * Строка отдельного прокси в списке.
 */
@Composable
private fun ProxyListItemRow(
    item: TelegramProxyItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) TgCardHover else TgCardBg)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) TgAccentCyan else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onSelect() }
            .onFocusChanged { isFocused = it.isFocused }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Радиокнопка выбора
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(2.dp, if (isSelected) TgAccentCyan else TgTextSecondary, CircleShape)
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(TgAccentCyan)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Название и статус
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.type.name,
                    color = TgTextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.displayAddress,
                    color = TgTextSecondary,
                    fontSize = 14.sp
                )
            }

            // Статус (подключён / доступен / недоступен / проверка...)
            val (statusColor, statusLabel) = when (item.status) {
                ProxyStatus.CONNECTED -> Pair(TgAccentCyan, if (item.pingMs != null) "подключён (${item.pingMs} мс)" else "подключён")
                ProxyStatus.AVAILABLE -> Pair(TgSuccessGreen, if (item.pingMs != null) "доступен (${item.pingMs} мс)" else "доступен")
                ProxyStatus.UNAVAILABLE -> Pair(TgErrorRed, "недоступен")
                ProxyStatus.CHECKING -> Pair(TgTextMuted, "проверка...")
            }

            Text(
                text = statusLabel,
                color = statusColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Меню действий (3 точки)
        Box {
            IconButton(
                onClick = { menuExpanded = !menuExpanded },
                colors = IconButtonDefaults.iconButtonColors(contentColor = TgTextSecondary)
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "Опции")
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(TgCardBg)
            ) {
                DropdownMenuItem(
                    text = { Text("Проверить", color = TgTextWhite) },
                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = TgAccentCyan) },
                    onClick = {
                        menuExpanded = false
                        onTest()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Изменить", color = TgTextWhite) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = TgTextWhite) },
                    onClick = {
                        menuExpanded = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Удалить", color = TgErrorRed) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = TgErrorRed) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

/**
 * Диалог редактирования / создания прокси (Скриншоты 6, 7, 8, 9).
 */
@Composable
fun ProxyEditDialog(
    initialItem: TelegramProxyItem?,
    onDismiss: () -> Unit,
    onSave: (TelegramProxyItem) -> Unit
) {
    val isEdit = initialItem != null
    var selectedType by remember { mutableStateOf(initialItem?.type ?: ProxyItemType.MTPROTO) }
    var host by remember { mutableStateOf(initialItem?.host ?: "") }
    var portText by remember { mutableStateOf(initialItem?.port?.toString() ?: "443") }
    var secret by remember { mutableStateOf(initialItem?.secret ?: "") }
    var username by remember { mutableStateOf(initialItem?.username ?: "") }
    var password by remember { mutableStateOf(initialItem?.password ?: "") }

    var isCheckingLive by remember { mutableStateOf(false) }
    var checkResultText by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(520.dp)
                .padding(8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = TgDarkBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = if (isEdit) "Редактирование прокси" else "Добавление прокси",
                    color = TgTextWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Радиокнопки выбора типа: MTPROTO, SOCKS5, HTTP, WEB
                Column {
                    TgRadioRow("MTPROTO", selected = selectedType == ProxyItemType.MTPROTO) { selectedType = ProxyItemType.MTPROTO }
                    TgRadioRow("SOCKS5", selected = selectedType == ProxyItemType.SOCKS5) { selectedType = ProxyItemType.SOCKS5 }
                    TgRadioRow("HTTP", selected = selectedType == ProxyItemType.HTTP) { selectedType = ProxyItemType.HTTP }
                    TgRadioRow("WEB", selected = selectedType == ProxyItemType.WEB) { selectedType = ProxyItemType.WEB }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Пояснение для MTPROTO / WEB
                if (selectedType == ProxyItemType.MTPROTO || selectedType == ProxyItemType.WEB) {
                    Text(
                        text = "Этот прокси-сервер может показывать канал спонсора в списке Ваших чатов. Это не раскрывает Ваш трафик.",
                        color = TgTextMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Секция «Адрес сокета»
                Text(
                    text = "Адрес сокета",
                    color = TgTextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Хост", color = TgTextSecondary) },
                        modifier = Modifier.weight(0.7f),
                        colors = tgTextFieldColors(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Порт", color = TgTextSecondary) },
                        modifier = Modifier.weight(0.3f),
                        colors = tgTextFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Секция «Учётные данные»
                Text(
                    text = if (selectedType == ProxyItemType.MTPROTO || selectedType == ProxyItemType.WEB) "Учётные данные" else "Учётные данные (необязательно)",
                    color = TgTextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (selectedType == ProxyItemType.MTPROTO || selectedType == ProxyItemType.WEB) {
                    OutlinedTextField(
                        value = secret,
                        onValueChange = { secret = it },
                        label = { Text("Ключ (Secret)", color = TgTextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = tgTextFieldColors(),
                        singleLine = true
                    )
                } else {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Логин", color = TgTextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = tgTextFieldColors(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Пароль", color = TgTextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = tgTextFieldColors(),
                        singleLine = true
                    )
                }

                // Индикатор результата проверки
                if (checkResultText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = checkResultText ?: "",
                        color = if (checkResultText?.contains("Доступен") == true) TgSuccessGreen else TgErrorRed,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Кнопки «Отмена», «Проверить», «Сохранить»
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Кнопка быстрой проверки
                    TextButton(
                        onClick = {
                            val port = portText.toIntOrNull() ?: 443
                            if (host.isBlank()) {
                                checkResultText = "Введите хост"
                                return@TextButton
                            }
                            isCheckingLive = true
                            checkResultText = "Проверка соединения..."
                            coroutineScope.launch {
                                val pingRes = MtprotoScraper.verifyProxySocket(host.trim(), port)
                                isCheckingLive = false
                                checkResultText = if (pingRes.first) {
                                    "Доступен! Пинг: ${pingRes.second} мс"
                                } else {
                                    "Недоступен (таймаут соединения)"
                                }
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = TgAccentCyan)
                    ) {
                        Text(if (isCheckingLive) "Проверка..." else "Проверить", fontSize = 13.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(contentColor = TgAccentCyan)
                        ) {
                            Text("Отмена", fontSize = 14.sp)
                        }

                        Button(
                            onClick = {
                                val port = portText.toIntOrNull() ?: 443
                                if (host.isBlank()) {
                                    checkResultText = "Укажите адрес хоста"
                                    return@Button
                                }
                                val item = TelegramProxyItem(
                                    id = initialItem?.id ?: java.util.UUID.randomUUID().toString(),
                                    type = selectedType,
                                    host = host.trim(),
                                    port = port,
                                    secret = secret.trim(),
                                    username = username.trim(),
                                    password = password.trim()
                                )
                                onSave(item)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TgPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Сохранить", color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// UI Компоненты для стилизации под Telegram
// ============================================================================

@Composable
private fun TgCheckboxRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onCheckedChange(!checked) }
            .onFocusChanged { isFocused = it.isFocused }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(2.dp, if (checked) TgAccentCyan else TgTextSecondary, RoundedCornerShape(4.dp))
                .background(if (checked) TgAccentCyan else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = title, color = TgTextWhite, fontSize = 14.sp)
    }
}

@Composable
private fun TgRadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(2.dp, if (selected) TgAccentCyan else TgTextSecondary, CircleShape)
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(TgAccentCyan)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = title, color = TgTextWhite, fontSize = 14.sp)
    }
}

@Composable
private fun TvChipButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) TgAccentCyan else if (isFocused) TgCardHover else TgCardBg)
            .border(
                1.dp,
                if (isFocused) Color.White else Color.Transparent,
                RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.White else TgTextSecondary,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun tgTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TgTextWhite,
    unfocusedTextColor = TgTextWhite,
    focusedContainerColor = TgSurface,
    unfocusedContainerColor = TgSurface,
    focusedBorderColor = TgAccentCyan,
    unfocusedBorderColor = TgDivider,
    cursorColor = TgAccentCyan
)
