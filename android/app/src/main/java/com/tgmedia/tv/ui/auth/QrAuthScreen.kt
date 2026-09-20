package com.tgmedia.tv.ui.auth

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.tgmedia.tv.data.tdlib.TdLibManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi

/**
 * 10-Foot UI экран авторизации в Telegram через QR-код для Android TV.
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

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isButtonFocused by remember { mutableStateOf(false) }

    // Автоматический переход при успешной авторизации
    LaunchedEffect(authState) {
        if (authState is TdApi.AuthorizationStateReady) {
            onAuthenticated()
        }
    }

    // Быстрая генерация Bitmap QR-кода при получении ссылки tg://login?token=...
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
                .width(920.dp)
                .background(
                    androidx.compose.ui.graphics.Color(0xFF14151E),
                    RoundedCornerShape(24.dp)
                )
                .border(
                    1.dp,
                    androidx.compose.ui.graphics.Color(0xFF262838),
                    RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 40.dp, vertical = 28.dp)
        ) {
            Text(
                text = "Вход в Telegram на Android TV",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Контейнер QR-кода слева
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(220.dp)
                        .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    val currentBitmap = qrBitmap
                    if (currentBitmap != null) {
                        Image(
                            bitmap = currentBitmap.asImageBitmap(),
                            contentDescription = "QR-код авторизации Telegram",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Генерация QR-кода...",
                                color = androidx.compose.ui.graphics.Color.Black,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val connHint = when (connectionState) {
                                is TdApi.ConnectionStateWaitingForNetwork -> "Ожидание сети..."
                                is TdApi.ConnectionStateConnecting -> "Подключение..."
                                is TdApi.ConnectionStateUpdating -> "Синхронизация..."
                                is TdApi.ConnectionStateReady -> "Сеть готова"
                                else -> "Подключение к Telegram..."
                            }
                            Text(
                                text = connHint,
                                color = androidx.compose.ui.graphics.Color.DarkGray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(36.dp))

                // Правая колонка с инструкцией и кнопками
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "1. Откройте Telegram на смартфоне\n2. Настройки → Устройства → Подключить\n3. Наведите камеру на QR-код слева",
                        fontSize = 15.sp,
                        color = androidx.compose.ui.graphics.Color(0xFF9E9EAF),
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Подробный статус соединения и авторизации
                    val statusText = when {
                        !tdLibManager.isNativeLoaded -> "Безопасный демо-режим"
                        lastError != null -> lastError!!
                        authState is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> "QR-код активен (готов к сканированию)"
                        authState is TdApi.AuthorizationStateWaitPhoneNumber -> "Запрос QR-кода с сервера Telegram..."
                        authState is TdApi.AuthorizationStateWaitTdlibParameters -> "Инициализация ядра TDLib..."
                        authState is TdApi.AuthorizationStateReady -> "Авторизован! Загрузка медиаканалов..."
                        connectionState is TdApi.ConnectionStateWaitingForNetwork -> "Ожидание сети на телевизоре..."
                        connectionState is TdApi.ConnectionStateConnecting -> "Подключение к серверам Telegram..."
                        connectionState is TdApi.ConnectionStateUpdating -> "Обновление данных Telegram..."
                        else -> "Инициализация ядра Telegram..."
                    }
                    val statusColor = when {
                        lastError != null -> androidx.compose.ui.graphics.Color(0xFFEF4444)
                        !tdLibManager.isNativeLoaded -> androidx.compose.ui.graphics.Color(0xFFFBBF24)
                        authState is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> androidx.compose.ui.graphics.Color(0xFF34D399)
                        connectionState is TdApi.ConnectionStateWaitingForNetwork -> androidx.compose.ui.graphics.Color(0xFFFBBF24)
                        else -> androidx.compose.ui.graphics.Color(0xFF38BDF8)
                    }

                    Text(
                        text = "• $statusText",
                        color = statusColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Кнопка обновления QR
                        Button(
                            onClick = { tdLibManager.refreshQr() },
                            modifier = Modifier
                                .onFocusChanged { isButtonFocused = it.isFocused }
                                .border(
                                    width = if (isButtonFocused) 3.dp else 1.dp,
                                    color = if (isButtonFocused) androidx.compose.ui.graphics.Color(0xFF38BDF8) else androidx.compose.ui.graphics.Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .focusable()
                        ) {
                            Text(
                                text = "Обновить QR",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }

                        // Кнопка входа в тестовый режим пульта ТВ
                        var isDemoFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { onAuthenticated() },
                            modifier = Modifier
                                .onFocusChanged { isDemoFocused = it.isFocused }
                                .border(
                                    width = if (isDemoFocused) 3.dp else 1.dp,
                                    color = if (isDemoFocused) androidx.compose.ui.graphics.Color(0xFF38BDF8) else androidx.compose.ui.graphics.Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .focusable()
                        ) {
                            Text(
                                text = "Тестовый вход (Пульт)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }

                    if (!tdLibManager.isNativeLoaded) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Демо-режим Android TV UI: проверяйте навигацию пультом ДУ.",
                            color = androidx.compose.ui.graphics.Color(0xFFFBBF24),
                            fontSize = 11.sp
                        )
                    }
                }
            }
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
