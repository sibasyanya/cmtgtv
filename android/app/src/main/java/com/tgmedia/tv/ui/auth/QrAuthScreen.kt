package com.tgmedia.tv.ui.auth

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.tgmedia.tv.data.tdlib.ProxySettings
import com.tgmedia.tv.data.tdlib.TdLibManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi

enum class AuthMode {
    QR_CODE,
    PHONE_NUMBER,
    PROXY_SETTINGS
}

/**
 * 10-Foot UI экран авторизации в Telegram для Android TV.
 * Поддерживает:
 * 1. Авторизацию по QR-коду с защитой от двойных запросов.
 * 2. Встроенные настройки MTProto / SOCKS5 Proxy для обхода блокировок серверов Telegram в РФ.
 * 3. Альтернативный вход по номеру телефона с поддержкой 2FA.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun QrAuthScreen(
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tdLibManager = remember { TdLibManager.getInstance() }
    val authState by tdLibManager.authorizationState.collectAsState()
    val connectionState by tdLibManager.connectionState.collectAsState()
    val lastError by tdLibManager.lastError.collectAsState()
    val qrLink by tdLibManager.qrCodeLink.collectAsState()
    val isQrInProgress by tdLibManager.isQrRequestInProgress.collectAsState()
    val proxySettings by tdLibManager.proxySettings.collectAsState()

    var activeMode by remember { mutableStateOf(AuthMode.QR_CODE) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Состояния для входа по телефону
    var phoneNumberInput by remember { mutableStateOf("+7") }
    var authCodeInput by remember { mutableStateOf("") }
    var password2FAInput by remember { mutableStateOf("") }
    var phoneAuthStage by remember { mutableStateOf(0) } // 0: ввод телефона, 1: ввод кода, 2: ввод 2FA
    var phoneStatusMsg by remember { mutableStateOf<String?>(null) }

    // Автоматический переход при успешной авторизации
    LaunchedEffect(authState) {
        if (authState is TdApi.AuthorizationStateReady) {
            onAuthenticated()
        }
    }

    // Генерация Bitmap QR-кода при получении ссылки tg://login?token=...
    LaunchedEffect(qrLink) {
        val link = qrLink
        if (link != null) {
            withContext(Dispatchers.Default) {
                qrBitmap = generateQrCodeBitmap(link, 500)
            }
        } else {
            qrBitmap = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xFF090A0F)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(960.dp)
                .background(
                    androidx.compose.ui.graphics.Color(0xFF13141E),
                    RoundedCornerShape(24.dp)
                )
                .border(
                    1.dp,
                    androidx.compose.ui.graphics.Color(0xFF26283A),
                    RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 36.dp, vertical = 24.dp)
        ) {
            // Верхняя панель переключения режимов
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Telegram Media TV",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                    Text(
                        text = "Авторизация медиаклиента Android TV",
                        fontSize = 12.sp,
                        color = androidx.compose.ui.graphics.Color(0xFF8E8EA0)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModeSwitchButton(
                        text = "QR-код",
                        isSelected = activeMode == AuthMode.QR_CODE,
                        onClick = { activeMode = AuthMode.QR_CODE }
                    )
                    ModeSwitchButton(
                        text = "По номеру телефона",
                        isSelected = activeMode == AuthMode.PHONE_NUMBER,
                        onClick = { activeMode = AuthMode.PHONE_NUMBER }
                    )
                    ModeSwitchButton(
                        text = if (proxySettings.enabled) "Proxy (Включен)" else "Proxy (РФ)",
                        isSelected = activeMode == AuthMode.PROXY_SETTINGS,
                        onClick = { activeMode = AuthMode.PROXY_SETTINGS }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            when (activeMode) {
                AuthMode.QR_CODE -> {
                    QrCodeAuthView(
                        tdLibManager = tdLibManager,
                        qrBitmap = qrBitmap,
                        isQrInProgress = isQrInProgress,
                        authState = authState,
                        connectionState = connectionState,
                        proxySettings = proxySettings,
                        lastError = lastError,
                        onOpenProxy = { activeMode = AuthMode.PROXY_SETTINGS },
                        onOpenPhone = { activeMode = AuthMode.PHONE_NUMBER },
                        onDemoLogin = onAuthenticated
                    )
                }

                AuthMode.PHONE_NUMBER -> {
                    PhoneNumberAuthView(
                        tdLibManager = tdLibManager,
                        phoneNumber = phoneNumberInput,
                        onPhoneNumberChange = { phoneNumberInput = it },
                        authCode = authCodeInput,
                        onAuthCodeChange = { authCodeInput = it },
                        password2FA = password2FAInput,
                        onPassword2FAChange = { password2FAInput = it },
                        stage = phoneAuthStage,
                        onStageChange = { phoneAuthStage = it },
                        statusMsg = phoneStatusMsg,
                        onStatusMsgChange = { phoneStatusMsg = it },
                        onBackToQr = { activeMode = AuthMode.QR_CODE }
                    )
                }

                AuthMode.PROXY_SETTINGS -> {
                    ProxySettingsView(
                        tdLibManager = tdLibManager,
                        currentSettings = proxySettings,
                        onClose = { activeMode = AuthMode.QR_CODE }
                    )
                }
            }
        }
    }
}

/**
 * Режим авторизации через QR-код.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QrCodeAuthView(
    tdLibManager: TdLibManager,
    qrBitmap: Bitmap?,
    isQrInProgress: Boolean,
    authState: TdApi.AuthorizationState,
    connectionState: TdApi.ConnectionState?,
    proxySettings: ProxySettings,
    lastError: String?,
    onOpenProxy: () -> Unit,
    onOpenPhone: () -> Unit,
    onDemoLogin: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Контейнер QR-кода слева
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(230.dp)
                .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR-код авторизации Telegram",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (isQrInProgress) "Запрос QR с сервера..." else "Ожидание QR-кода...",
                        color = androidx.compose.ui.graphics.Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val connHint = when (connectionState) {
                        is TdApi.ConnectionStateWaitingForNetwork -> "Ожидание сети..."
                        is TdApi.ConnectionStateConnecting -> "Подключение..."
                        is TdApi.ConnectionStateUpdating -> "Синхронизация..."
                        is TdApi.ConnectionStateReady -> "Сеть готова"
                        else -> "Связь с Telegram..."
                    }
                    Text(
                        text = connHint,
                        color = androidx.compose.ui.graphics.Color(0xFF4B5563),
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(32.dp))

        // Правая колонка с инструкцией, статусами и кнопками
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "1. Откройте Telegram на телефоне\n2. Настройки → Устройства → Подключить\n3. Наведите камеру на QR-код слева",
                fontSize = 14.sp,
                color = androidx.compose.ui.graphics.Color(0xFFA5A5B8),
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Карточка статусов
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Color(0xFF1B1C28), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                val connStatusText = when (connectionState) {
                    is TdApi.ConnectionStateReady -> "Подключено к серверам Telegram"
                    is TdApi.ConnectionStateConnecting -> "Соединение с Telegram (в РФ может блокироваться)..."
                    is TdApi.ConnectionStateWaitingForNetwork -> "Ожидание сети Wi-Fi/Ethernet..."
                    is TdApi.ConnectionStateUpdating -> "Синхронизация данных..."
                    else -> "Инициализация сети..."
                }
                val connColor = when (connectionState) {
                    is TdApi.ConnectionStateReady -> androidx.compose.ui.graphics.Color(0xFF34D399)
                    is TdApi.ConnectionStateWaitingForNetwork -> androidx.compose.ui.graphics.Color(0xFFFBBF24)
                    else -> androidx.compose.ui.graphics.Color(0xFF38BDF8)
                }

                Text(
                    text = "• Сеть: $connStatusText",
                    color = connColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                if (proxySettings.enabled) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "• Proxy: активен (${proxySettings.server}:${proxySettings.port})",
                        color = androidx.compose.ui.graphics.Color(0xFFA7F3D0),
                        fontSize = 11.sp
                    )
                }

                if (lastError != null) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "• $lastError",
                        color = androidx.compose.ui.graphics.Color(0xFFF87171),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Кнопки действий
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvActionButton(
                    text = if (isQrInProgress) "Запрос отправлен..." else "Обновить QR",
                    onClick = { tdLibManager.refreshQr() }
                )

                TvActionButton(
                    text = "Перезапустить сессию",
                    onClick = { tdLibManager.restartSession() }
                )

                TvActionButton(
                    text = "Вход по телефону",
                    onClick = onOpenPhone
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvActionButton(
                    text = "Настройки Proxy (РФ)",
                    onClick = onOpenProxy
                )

                TvActionButton(
                    text = "Тестовый вход",
                    onClick = onDemoLogin
                )
            }

            // Подсказка при блокировках
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "💡 Если QR-код долго не появляется: сервера Telegram блокируются провайдером в РФ. Включите Proxy или войдите по телефону.",
                color = androidx.compose.ui.graphics.Color(0xFF717188),
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
    }
}

/**
 * Альтернативный экран входа по номеру телефона с поддержкой кода и 2FA.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PhoneNumberAuthView(
    tdLibManager: TdLibManager,
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    authCode: String,
    onAuthCodeChange: (String) -> Unit,
    password2FA: String,
    onPassword2FAChange: (String) -> Unit,
    stage: Int,
    onStageChange: (Int) -> Unit,
    statusMsg: String?,
    onStatusMsgChange: (String?) -> Unit,
    onBackToQr: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Левая панель формы
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            when (stage) {
                0 -> {
                    Text(
                        text = "Вход по номеру телефона",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Введите номер в международном формате (например, +79991234567):",
                        fontSize = 13.sp,
                        color = androidx.compose.ui.graphics.Color(0xFF9E9EAF)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .background(androidx.compose.ui.graphics.Color(0xFF1B1C28), RoundedCornerShape(10.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = phoneNumber.ifEmpty { "+7..." },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TvActionButton(text = "Запросить код") {
                            onStatusMsgChange("Отправка запроса на сервер...")
                            tdLibManager.sendAuthenticationPhoneNumber(phoneNumber) { success, err ->
                                if (success) {
                                    onStageChange(1)
                                    onStatusMsgChange("Код отправлен в ваш Telegram на телефоне!")
                                } else {
                                    onStatusMsgChange(err ?: "Ошибка отправки номера")
                                }
                            }
                        }
                        TvActionButton(text = "Назад к QR", onClick = onBackToQr)
                    }
                }

                1 -> {
                    Text(
                        text = "Введите 5-значный код",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Telegram отправил код в приложение на телефоне:",
                        fontSize = 13.sp,
                        color = androidx.compose.ui.graphics.Color(0xFF9E9EAF)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .background(androidx.compose.ui.graphics.Color(0xFF1B1C28), RoundedCornerShape(10.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = authCode.ifEmpty { "Код..." },
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color(0xFF38BDF8),
                            letterSpacing = 4.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TvActionButton(text = "Подтвердить код") {
                            onStatusMsgChange("Проверка кода...")
                            tdLibManager.checkAuthenticationCode(authCode) { success, err ->
                                if (success) {
                                    onStatusMsgChange("Вход выполнен успешно!")
                                } else {
                                    if (err?.contains("PASSWORD") == true || err?.contains("2FA") == true) {
                                        onStageChange(2)
                                        onStatusMsgChange("Требуется облачный пароль (2FA)")
                                    } else {
                                        onStatusMsgChange(err ?: "Неверный код")
                                    }
                                }
                            }
                        }
                        TvActionButton(text = "Назад к номеру") { onStageChange(0) }
                    }
                }

                2 -> {
                    Text(
                        text = "Облачный пароль (2FA)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "На вашем аккаунте включена двухфакторная аутентификация:",
                        fontSize = 13.sp,
                        color = androidx.compose.ui.graphics.Color(0xFF9E9EAF)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .background(androidx.compose.ui.graphics.Color(0xFF1B1C28), RoundedCornerShape(10.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "*".repeat(password2FA.length).ifEmpty { "Пароль 2FA..." },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TvActionButton(text = "Войти") {
                            onStatusMsgChange("Проверка облачного пароля...")
                            tdLibManager.checkAuthenticationPassword(password2FA) { success, err ->
                                if (success) {
                                    onStatusMsgChange("Авторизация успешна!")
                                } else {
                                    onStatusMsgChange(err ?: "Неверный пароль 2FA")
                                }
                            }
                        }
                        TvActionButton(text = "Назад к коду") { onStageChange(1) }
                    }
                }
            }

            if (statusMsg != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• $statusMsg",
                    color = androidx.compose.ui.graphics.Color(0xFF38BDF8),
                    fontSize = 12.sp
                )
            }
        }

        // Правая панель: Виртуальная цифровая клавиатура Android TV (удобна для пульта)
        Column(
            modifier = Modifier
                .width(280.dp)
                .background(androidx.compose.ui.graphics.Color(0xFF1B1C28), RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val targetInput = when (stage) {
                0 -> phoneNumber
                1 -> authCode
                else -> password2FA
            }
            val onTargetChange: (String) -> Unit = when (stage) {
                0 -> onPhoneNumberChange
                1 -> onAuthCodeChange
                else -> onPassword2FAChange
            }

            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("+", "0", "⌫")
            )

            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { char ->
                        NumpadButton(
                            text = char,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                when (char) {
                                    "⌫" -> if (targetInput.isNotEmpty()) onTargetChange(targetInput.dropLast(1))
                                    else -> onTargetChange(targetInput + char)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Экран управления настройками Proxy (MTProto / SOCKS5).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProxySettingsView(
    tdLibManager: TdLibManager,
    currentSettings: ProxySettings,
    onClose: () -> Unit
) {
    var statusText by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Настройки Proxy (Обход блокировок серверов Telegram в РФ)",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = androidx.compose.ui.graphics.Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "В РФ многие интернет-провайдеры блокируют IP-адреса серверов Telegram. Включение прокси позволяет мгновенно подключиться и получить QR-код.",
            fontSize = 12.sp,
            color = androidx.compose.ui.graphics.Color(0xFFA0A0B2),
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Готовые пресеты
        Text(
            text = "Быстрое подключение проверенных пресетов:",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = androidx.compose.ui.graphics.Color(0xFF38BDF8)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            PresetProxyCard(
                title = "MTProto Пресет 1",
                subtitle = "Digital Resistance (443)",
                isActive = currentSettings.enabled && currentSettings.server == ProxySettings.PRESET_MTPROTO_1.server,
                modifier = Modifier.weight(1f),
                onClick = {
                    statusText = "Применение MTProto Proxy 1..."
                    tdLibManager.applyProxy(ProxySettings.PRESET_MTPROTO_1) { ok, err ->
                        statusText = if (ok) "MTProto Proxy 1 успешно подключен!" else (err ?: "Ошибка прокси")
                    }
                }
            )

            PresetProxyCard(
                title = "MTProto Пресет 2",
                subtitle = "Резервный MTProxy (443)",
                isActive = currentSettings.enabled && currentSettings.server == ProxySettings.PRESET_MTPROTO_2.server,
                modifier = Modifier.weight(1f),
                onClick = {
                    statusText = "Применение MTProto Proxy 2..."
                    tdLibManager.applyProxy(ProxySettings.PRESET_MTPROTO_2) { ok, err ->
                        statusText = if (ok) "MTProto Proxy 2 успешно подключен!" else (err ?: "Ошибка прокси")
                    }
                }
            )

            PresetProxyCard(
                title = "Локальный SOCKS5",
                subtitle = "127.0.0.1:1080 (VPN/Туннель)",
                isActive = currentSettings.enabled && currentSettings.server == ProxySettings.PRESET_LOCAL_SOCKS5.server,
                modifier = Modifier.weight(1f),
                onClick = {
                    statusText = "Применение локального SOCKS5..."
                    tdLibManager.applyProxy(ProxySettings.PRESET_LOCAL_SOCKS5) { ok, err ->
                        statusText = if (ok) "Локальный SOCKS5 подключен!" else (err ?: "Ошибка прокси")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentSettings.enabled) {
                TvActionButton(text = "Отключить Proxy (Прямое соединение)") {
                    statusText = "Отключение прокси..."
                    tdLibManager.disableProxy { ok, _ ->
                        statusText = if (ok) "Proxy отключен. Переход на прямое соединение." else "Ошибка отключения"
                    }
                }
            }

            TvActionButton(text = "Перезапустить сессию") {
                tdLibManager.restartSession()
                statusText = "Сессия перезапущена с новыми параметрами сети."
            }

            TvActionButton(text = "Назад к QR-коду", onClick = onClose)
        }

        if (statusText != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• $statusText",
                color = androidx.compose.ui.graphics.Color(0xFF34D399),
                fontSize = 12.sp
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PresetProxyCard(
    title: String,
    subtitle: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Button(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else if (isActive) 1.5.dp else 1.dp,
                color = when {
                    isFocused -> androidx.compose.ui.graphics.Color(0xFF38BDF8)
                    isActive -> androidx.compose.ui.graphics.Color(0xFF34D399)
                    else -> androidx.compose.ui.graphics.Color(0xFF26283A)
                },
                shape = RoundedCornerShape(12.dp)
            )
            .focusable(),
        colors = ButtonDefaults.colors(
            containerColor = if (isActive) androidx.compose.ui.graphics.Color(0xFF162524) else androidx.compose.ui.graphics.Color(0xFF181924)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = androidx.compose.ui.graphics.Color.White
                )
                if (isActive) {
                    Text(
                        text = "✓ Вкл",
                        fontSize = 11.sp,
                        color = androidx.compose.ui.graphics.Color(0xFF34D399),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = androidx.compose.ui.graphics.Color(0xFF8E8EA0)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ModeSwitchButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Button(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) androidx.compose.ui.graphics.Color(0xFF38BDF8)
                else if (isSelected) androidx.compose.ui.graphics.Color(0xFF2DD4BF)
                else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .focusable(),
        colors = ButtonDefaults.colors(
            containerColor = if (isSelected) androidx.compose.ui.graphics.Color(0xFF1F2937) else androidx.compose.ui.graphics.Color(0xFF181924)
        )
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFFB0B0C0),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvActionButton(
    text: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Button(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) androidx.compose.ui.graphics.Color(0xFF38BDF8) else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .focusable(),
        colors = ButtonDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color(0xFF252738)
        )
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NumpadButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Button(
        onClick = onClick,
        modifier = modifier
            .height(44.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) androidx.compose.ui.graphics.Color(0xFF38BDF8) else androidx.compose.ui.graphics.Color(0xFF26283A),
                shape = RoundedCornerShape(8.dp)
            )
            .focusable(),
        colors = ButtonDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color(0xFF141520)
        )
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color.White
            )
        }
    }
}

/**
 * Оптимизированная ZXing генерация Bitmap QR-кода через прямой массив пикселей.
 */
private fun generateQrCodeBitmap(contents: String, size: Int): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val hints = mapOf(com.google.zxing.EncodeHintType.MARGIN to 1)
        val bitMatrix = writer.encode(contents, BarcodeFormat.QR_CODE, size, size, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        bitmap
    } catch (e: Throwable) {
        android.util.Log.e("QrAuthScreen", "Failed to generate QR bitmap", e)
        null
    }
}
