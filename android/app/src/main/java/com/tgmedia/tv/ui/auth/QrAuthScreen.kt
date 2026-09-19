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
    val qrLink by tdLibManager.qrCodeLink.collectAsState()

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isButtonFocused by remember { mutableStateOf(false) }

    // Автоматический переход при успешной авторизации
    LaunchedEffect(authState) {
        if (authState is TdApi.AuthorizationStateReady) {
            onAuthenticated()
        }
    }

    // Генерация Bitmap QR-кода при получении ссылки tg://login?token=...
    LaunchedEffect(qrLink) {
        qrLink?.let { link ->
            withContext(Dispatchers.Default) {
                qrBitmap = generateQrCodeBitmap(link, 600)
            }
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
                .width(880.dp)
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
                        .size(210.dp)
                        .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = "QR-код авторизации Telegram",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = "Ожидание сессии TDLib...",
                            color = androidx.compose.ui.graphics.Color.Black,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(36.dp))

                // Правая колонка с инструкцией и кнопками
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "1. Откройте Telegram на смартфоне\n2. Настройки → Устройства → Подключить\n3. Отсканируйте камерой QR-код слева",
                        fontSize = 15.sp,
                        color = androidx.compose.ui.graphics.Color(0xFF9E9EAF),
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Кнопка обновления QR
                        Button(
                            onClick = { tdLibManager.requestQrCodeAuthentication() },
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
 * ZXing утилита для генерации монохромного Bitmap QR-кода.
 */
private fun generateQrCodeBitmap(contents: String, size: Int): Bitmap {
    val writer = QRCodeWriter()
    val bitMatrix = writer.encode(contents, BarcodeFormat.QR_CODE, size, size)
    val width = bitMatrix.width
    val height = bitMatrix.height
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    for (x in 0 until width) {
        for (y in 0 until height) {
            bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
        }
    }
    return bitmap
}
