package com.tgmedia.tv.ui.auth

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tgmedia.tv.data.tdlib.*
import com.tgmedia.tv.ui.proxy.ProxySettingsDialog
import com.tgmedia.tv.ui.proxy.TgAccentCyan
import com.tgmedia.tv.ui.proxy.TgCardBg
import com.tgmedia.tv.ui.proxy.TgCardHover
import com.tgmedia.tv.ui.proxy.TgDarkBg
import com.tgmedia.tv.ui.proxy.TgErrorRed
import com.tgmedia.tv.ui.proxy.TgPrimary
import com.tgmedia.tv.ui.proxy.TgSuccessGreen
import com.tgmedia.tv.ui.proxy.TgSurface
import com.tgmedia.tv.ui.proxy.TgTextMuted
import com.tgmedia.tv.ui.proxy.TgTextSecondary
import com.tgmedia.tv.ui.proxy.TgTextWhite
import org.drinkless.tdlib.TdApi

/**
 * Основной экран авторизации в Telegram для Android TV (10-foot UI).
 * Фокусируется исключительно на входе по номеру телефона, коду и 2FA паролю,
 * со встроенными настройками прокси (как в официальном клиенте Telegram).
 */
@Composable
fun QrAuthScreen(
    onAuthenticated: () -> Unit
) {
    val tdLibManager = remember { TdLibManager.getInstance() }
    val authState by tdLibManager.authorizationState.collectAsState()
    val connState by tdLibManager.connectionState.collectAsState()
    val lastError by tdLibManager.lastError.collectAsState()
    val proxyConfig by tdLibManager.telegramProxyConfig.collectAsState()
    val context = LocalContext.current

    var phoneNumber by remember { mutableStateOf("+7") }
    var authCode by remember { mutableStateOf("") }
    var cloudPassword by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var showProxyDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    // Реакция на успешную авторизацию
    LaunchedEffect(authState) {
        if (authState is TdApi.AuthorizationStateReady) {
            onAuthenticated()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TgDarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 24.dp)
        ) {
            // 1. Верхняя панель: Название, сетевой статус и кнопки управления
            TopStatusBar(
                connState = connState,
                proxyConfig = proxyConfig,
                onOpenProxySettings = { showProxyDialog = true },
                onResetDatabase = { showResetConfirm = true }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Баннер ошибки (если возникла)
            if (lastError != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = TgErrorRed.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = TgErrorRed)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = lastError ?: "",
                            color = Color(0xFFFFB4AB),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // 3. Основная карточка авторизации по стадиям
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (authState) {
                    is TdApi.AuthorizationStateWaitCode -> {
                        // СТАДИЯ 2: Ввод кода подтверждения
                        AuthCodeStageView(
                            phoneNumber = phoneNumber,
                            code = authCode,
                            isSubmitting = isSubmitting,
                            onCodeChange = { authCode = it },
                            onSubmit = {
                                if (authCode.length < 5) {
                                    Toast.makeText(context, "Введите 5 цифр кода", Toast.LENGTH_SHORT).show()
                                    return@AuthCodeStageView
                                }
                                isSubmitting = true
                                tdLibManager.checkAuthenticationCode(authCode) { ok, err ->
                                    isSubmitting = false
                                    if (err != null) {
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onBackToPhone = {
                                authCode = ""
                                tdLibManager.restartSession(clearDatabase = false)
                            },
                            onResendCode = {
                                tdLibManager.resendAuthenticationCode { ok, err ->
                                    val msg = if (ok) "Код отправлен повторно" else (err ?: "Ошибка отправки")
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    is TdApi.AuthorizationStateWaitPassword -> {
                        // СТАДИЯ 3: Ввод облачного пароля 2FA
                        AuthPasswordStageView(
                            password = cloudPassword,
                            isSubmitting = isSubmitting,
                            onPasswordChange = { cloudPassword = it },
                            onSubmit = {
                                if (cloudPassword.isBlank()) {
                                    Toast.makeText(context, "Введите пароль 2FA", Toast.LENGTH_SHORT).show()
                                    return@AuthPasswordStageView
                                }
                                isSubmitting = true
                                tdLibManager.checkAuthenticationPassword(cloudPassword) { ok, err ->
                                    isSubmitting = false
                                    if (err != null) {
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onBack = {
                                cloudPassword = ""
                                tdLibManager.restartSession(clearDatabase = false)
                            }
                        )
                    }

                    else -> {
                        // СТАДИЯ 1: Ввод номера телефона (по умолчанию)
                        PhoneNumberStageView(
                            phoneNumber = phoneNumber,
                            isSubmitting = isSubmitting,
                            connState = connState,
                            onPhoneChange = { phoneNumber = it },
                            onSubmit = {
                                val clean = phoneNumber.trim().replace(" ", "").replace("-", "")
                                if (clean.length < 7) {
                                    Toast.makeText(context, "Укажите номер телефона в международном формате", Toast.LENGTH_LONG).show()
                                    return@PhoneNumberStageView
                                }
                                isSubmitting = true
                                tdLibManager.sendAuthenticationPhoneNumber(phoneNumber) { ok, err ->
                                    isSubmitting = false
                                    if (err != null) {
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Диалог настроек прокси
    if (showProxyDialog) {
        ProxySettingsDialog(
            onDismissRequest = { showProxyDialog = false }
        )
    }

    // Диалог подтверждения сброса базы и кэша
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor = TgDarkBg,
            title = { Text("Сброс базы данных TDLib", color = TgTextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Это удалит локальные файлы сессии td.binlog и сбросит зависшие сетевые запросы. Настройки прокси сохранятся. Продолжить?",
                    color = TgTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        phoneNumber = "+7"
                        authCode = ""
                        cloudPassword = ""
                        tdLibManager.restartSession(clearDatabase = true)
                        Toast.makeText(context, "База сброшена. Перезапуск сессии...", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TgErrorRed)
                ) {
                    Text("Сбросить", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Отмена", color = TgAccentCyan)
                }
            }
        )
    }
}

// ============================================================================
// Верхняя панель состояния сети и быстрых действий
// ============================================================================

@Composable
private fun TopStatusBar(
    connState: TdApi.ConnectionState?,
    proxyConfig: TelegramProxyConfig,
    onOpenProxySettings: () -> Unit,
    onResetDatabase: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TgSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Левая часть: логотип и статус соединения
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(TgPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "Cybermasters TG TV (CMTGTV)",
                    color = TgTextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                val (statusColor, statusText) = when (connState) {
                    is TdApi.ConnectionStateReady -> Pair(TgSuccessGreen, "Подключено к Telegram")
                    is TdApi.ConnectionStateConnecting -> Pair(Color(0xFFFFB74D), "Подключение к Telegram...")
                    is TdApi.ConnectionStateUpdating -> Pair(TgAccentCyan, "Обновление данных...")
                    is TdApi.ConnectionStateWaitingForNetwork -> Pair(TgErrorRed, "Ожидание сети (включите Proxy)")
                    else -> Pair(TgTextMuted, "Инициализация TDLib...")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = statusText, color = statusColor, fontSize = 12.sp)
                }
            }
        }

        // Правая часть: кнопка настроек прокси и кнопка сброса
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val proxyBadge = when (proxyConfig.mode) {
                ProxyConnectionMode.DISABLED -> "Прокси: Отключен"
                ProxyConnectionMode.SYSTEM -> "Прокси: Системный"
                ProxyConnectionMode.CUSTOM -> {
                    val active = proxyConfig.activeProxy
                    if (active != null) "Прокси: ${active.type.name} (${active.displayAddress})" else "Прокси: Собственный"
                }
            }

            TvHeaderButton(
                icon = Icons.Default.Settings,
                text = proxyBadge,
                accentColor = if (proxyConfig.mode == ProxyConnectionMode.CUSTOM) TgAccentCyan else TgTextSecondary,
                onClick = onOpenProxySettings
            )

            TvHeaderButton(
                icon = Icons.Default.Refresh,
                text = "Сброс базы",
                accentColor = TgErrorRed,
                onClick = onResetDatabase
            )
        }
    }
}

// ============================================================================
// СТАДИЯ 1: Ввод номера телефона
// ============================================================================

@Composable
private fun PhoneNumberStageView(
    phoneNumber: String,
    isSubmitting: Boolean,
    connState: TdApi.ConnectionState?,
    onPhoneChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(680.dp)
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TgCardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Вход по номеру телефона",
                color = TgTextWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Введите номер телефона вашего аккаунта Telegram",
                color = TgTextSecondary,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Быстрые пресеты кодов стран
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                val presets = listOf("+7", "+375", "+380", "+998", "+1")
                presets.forEach { code ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (phoneNumber.startsWith(code)) TgPrimary else TgSurface)
                            .clickable {
                                onPhoneChange(code)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = code,
                            color = if (phoneNumber.startsWith(code)) Color.White else TgTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Поле ввода номера
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { onPhoneChange(it) },
                label = { Text("Номер телефона (+...)", color = TgTextSecondary) },
                modifier = Modifier
                    .fillMaxWidth(0.85f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TgTextWhite,
                    unfocusedTextColor = TgTextWhite,
                    focusedContainerColor = TgSurface,
                    unfocusedContainerColor = TgSurface,
                    focusedBorderColor = TgAccentCyan,
                    unfocusedBorderColor = Color.Transparent
                ),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Экранная цифровая клавиатура для пульта ТВ
            TvNumericKeypad(
                onDigit = { d -> onPhoneChange(phoneNumber + d) },
                onBackspace = {
                    if (phoneNumber.isNotEmpty()) {
                        onPhoneChange(phoneNumber.dropLast(1))
                    }
                },
                onClear = { onPhoneChange("+") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Большая кнопка «Отправить код»
            Button(
                onClick = onSubmit,
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TgPrimary)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Отправка запроса в Telegram...", color = Color.White, fontSize = 15.sp)
                } else {
                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Получить код подтверждения", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ============================================================================
// СТАДИЯ 2: Ввод кода подтверждения
// ============================================================================

@Composable
private fun AuthCodeStageView(
    phoneNumber: String,
    code: String,
    isSubmitting: Boolean,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBackToPhone: () -> Unit,
    onResendCode: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(680.dp)
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TgCardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Введите код подтверждения",
                color = TgTextWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Код отправлен в ваше приложение Telegram (или по SMS) на $phoneNumber",
                color = TgTextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Визуальные ячейки 5 цифр
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 5) {
                    val char = code.getOrNull(i)?.toString() ?: ""
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(TgSurface)
                            .border(
                                width = if (i == code.length) 2.dp else 1.dp,
                                color = if (i == code.length) TgAccentCyan else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = char,
                            color = TgTextWhite,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Экранный нумпад для пульта ТВ
            TvNumericKeypad(
                onDigit = { d ->
                    if (code.length < 5) onCodeChange(code + d)
                },
                onBackspace = {
                    if (code.isNotEmpty()) onCodeChange(code.dropLast(1))
                },
                onClear = { onCodeChange("") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Кнопка подтверждения входа
            Button(
                onClick = onSubmit,
                enabled = !isSubmitting && code.length >= 5,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TgPrimary)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Проверка кода...", color = Color.White)
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Войти в Telegram", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Кнопки возврата и повторной отправки
            Row(
                modifier = Modifier.fillMaxWidth(0.85f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onBackToPhone) {
                    Text("Изменить номер", color = TgAccentCyan, fontSize = 13.sp)
                }
                TextButton(onClick = onResendCode) {
                    Text("Отправить код повторно", color = TgAccentCyan, fontSize = 13.sp)
                }
            }
        }
    }
}

// ============================================================================
// СТАДИЯ 3: Ввод пароля 2FA
// ============================================================================

@Composable
private fun AuthPasswordStageView(
    password: String,
    isSubmitting: Boolean,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(580.dp)
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TgCardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = TgAccentCyan, modifier = Modifier.size(44.dp))

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Облачный пароль (2FA)",
                color = TgTextWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Ваш аккаунт защищен двухфакторной аутентификацией. Введите пароль.",
                color = TgTextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Пароль", color = TgTextSecondary) },
                modifier = Modifier.fillMaxWidth(0.9f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TgTextWhite,
                    unfocusedTextColor = TgTextWhite,
                    focusedContainerColor = TgSurface,
                    unfocusedContainerColor = TgSurface,
                    focusedBorderColor = TgAccentCyan
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onSubmit,
                enabled = !isSubmitting && password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TgPrimary)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Проверка пароля...", color = Color.White)
                } else {
                    Text("Подтвердить пароль", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = onBack) {
                Text("Назад к началу", color = TgAccentCyan, fontSize = 13.sp)
            }
        }
    }
}

// ============================================================================
// Виртуальная клавиатура для пульта ТВ
// ============================================================================

@Composable
private fun TvNumericKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit
) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "⌫")

    Column(
        modifier = Modifier.width(280.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val rows = keys.chunked(3)
        rows.forEach { rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowKeys.forEach { key ->
                    KeypadButton(
                        label = key,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (key) {
                                "⌫" -> onBackspace()
                                "C" -> onClear()
                                else -> onDigit(key)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isFocused) TgAccentCyan else TgSurface)
            .border(
                width = 1.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .onFocusChanged { isFocused = it.isFocused },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isFocused) Color.White else TgTextWhite,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TvHeaderButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) TgCardHover else TgDarkBg)
            .border(
                width = 1.dp,
                color = if (isFocused) accentColor else TgDivider,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = text, color = TgTextWhite, fontSize = 13.sp)
    }
}
