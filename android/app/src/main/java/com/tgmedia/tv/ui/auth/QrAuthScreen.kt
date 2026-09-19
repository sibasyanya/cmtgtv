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
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .width(760.dp)
                .background(
                    androidx.compose.ui.graphics.Color(0xFF14151E),
                    RoundedCornerShape(24.dp)
                )
                .border(
                    1.dp,
                    androidx.compose.ui.graphics.Color(0xFF262838),
                    RoundedCornerShape(24.dp)
                )
                .padding(48.dp)
        ) {
            Text(
                text = "Вход в Telegram на Android TV",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "1. Откройте Telegram на телефоне\n2. Перейдите в Настройки → Устройства → Подключить устройство\n3. Наведите камеру на QR-код ниже",
                fontSize = 15.sp,
                color = androidx.compose.ui.graphics.Color(0xFF9E9EAF),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Контейнер QR-кода
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(260.dp)
                    .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(16.dp))
                    .padding(16.dp)
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
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Кнопка обновления QR с поддержкой D-Pad фокуса
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
                    text = "Обновить QR-код",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                )
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
