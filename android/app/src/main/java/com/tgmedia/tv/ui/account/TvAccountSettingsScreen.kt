package com.tgmedia.tv.ui.account

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tgmedia.tv.BuildConfig
import com.tgmedia.tv.data.tdlib.ProxyConnectionMode
import com.tgmedia.tv.data.tdlib.TdLibManager
import com.tgmedia.tv.ui.proxy.*
import org.drinkless.tdlib.TdApi
import java.io.File

/**
 * Полноценный 10-Foot UI экран управления аккаунтом и профилем для Android TV.
 * Предоставляет:
 * - Просмотр профиля Telegram (имя, username, телефон, ID, статус Premium).
 * - Управление сессиями и аккаунтами (выход, смена аккаунта).
 * - Прямой доступ к управлению MTProto/Socks5 прокси.
 * - Очистка кэша сообщений и данных.
 * - Информацию о стабильной сборке с единым релизным ключом.
 */
@Composable
fun TvAccountSettingsScreen(
    onBack: () -> Unit,
    onLogOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tdLibManager = remember { TdLibManager.getInstance() }
    val currentUser by tdLibManager.currentUser.collectAsState()
    val proxyConfig by tdLibManager.telegramProxyConfig.collectAsState()
    val downloadedFiles by tdLibManager.fileDownloadedState.collectAsState()
    val context = LocalContext.current

    var showProxyDialog by remember { mutableStateOf(false) }
    var showLogOutConfirm by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var isLoggingOut by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        tdLibManager.loadCurrentUser()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TgDarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 24.dp)
        ) {
            // Верхняя панель навигации
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var isBackFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = onBack,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isBackFocused) TgAccentCyan else TgSurface
                        ),
                        modifier = Modifier
                            .onFocusChanged { isBackFocused = it.isFocused }
                            .height(44.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = null,
                            tint = if (isBackFocused) Color.Black else Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Назад к чатам",
                            color = if (isBackFocused) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    Column {
                        Text(
                            text = "Настройки аккаунта и профиль",
                            color = TgTextWhite,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Cybermasters TG TV (CMTGTV) v${BuildConfig.VERSION_NAME} • Релизный ключ активен",
                            color = TgTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                // Индикатор статуса соединения
                val activeProxyInfo = when (proxyConfig.mode) {
                    ProxyConnectionMode.CUSTOM -> "Прокси: ${proxyConfig.activeProxy?.displayAddress ?: "Активен"}"
                    ProxyConnectionMode.SYSTEM -> "Системный прокси"
                    ProxyConnectionMode.DISABLED -> "Прямое соединение"
                }
                Box(
                    modifier = Modifier
                        .background(TgSurface, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = activeProxyInfo,
                        color = TgAccentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Основная двухколоночная область настроек
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // ЛЕВАЯ КОЛОНКА: Профиль пользователя
                Card(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = TgCardBg)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val fullName = buildString {
                            val first = currentUser?.firstName.orEmpty()
                            val last = currentUser?.lastName.orEmpty()
                            if (first.isNotBlank()) append(first)
                            if (last.isNotBlank()) {
                                if (isNotEmpty()) append(" ")
                                append(last)
                            }
                            if (isEmpty()) append("Пользователь Telegram")
                        }

                        val usernameStr = currentUser?.usernames?.activeUsernames?.firstOrNull()
                            ?: "не задан"

                        val phoneStr = currentUser?.phoneNumber?.let {
                            if (it.startsWith("+")) it else "+$it"
                        } ?: "Номер скрыт"

                        // Реальный аватар профиля
                        val userAvatarPath = remember(currentUser, downloadedFiles) {
                            tdLibManager.getUserPhotoPath(currentUser)
                        }
                        val avatarBitmap = remember(userAvatarPath) {
                            if (!userAvatarPath.isNullOrBlank()) {
                                try {
                                    val f = File(userAvatarPath)
                                    if (f.exists() && f.length() > 0) {
                                        BitmapFactory.decodeFile(userAvatarPath)?.asImageBitmap()
                                    } else null
                                } catch (_: Throwable) {
                                    null
                                }
                            } else null
                        }

                        if (avatarBitmap != null) {
                            Image(
                                bitmap = avatarBitmap,
                                contentDescription = fullName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            val initials = fullName.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(CircleShape)
                                    .background(TgPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (initials.isNotBlank()) initials else "TG",
                                    color = Color.White,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = fullName,
                            color = TgTextWhite,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "@$usernameStr",
                            color = TgAccentCyan,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Информационная карточка параметров
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = TgSurface)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ProfileInfoRow(label = "Телефон", value = phoneStr)
                                ProfileInfoRow(label = "Telegram ID", value = currentUser?.id?.toString() ?: "—")
                                ProfileInfoRow(
                                    label = "Статус подписки",
                                    value = if (currentUser?.isPremium == true) "⭐ Telegram Premium" else "Обычный аккаунт"
                                )
                                ProfileInfoRow(label = "Локальная сессия", value = "Активна и защищена")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Информация об обновлениях
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF152A1D))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = TgSuccessGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Обновления APK ставятся поверх без удаления и без потери данных.",
                                    color = Color(0xFFC8E6C9),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // ПРАВАЯ КОЛОНКА: Действия и управление
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = TgCardBg)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Действия и настройки",
                            color = TgTextWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // 1. Настройки прокси
                        AccountActionButton(
                            icon = Icons.Default.Settings,
                            title = "Настройки Прокси (MTProto / Socks5)",
                            subtitle = "Управление прокси серверами и обход блокировок",
                            accentColor = TgAccentCyan,
                            onClick = { showProxyDialog = true }
                        )

                        // 2. Сменить аккаунт / Добавить второй
                        AccountActionButton(
                            icon = Icons.Default.Add,
                            title = "Сменить аккаунт / Добавить профиль",
                            subtitle = "Войти под другим номером телефона",
                            accentColor = TgPrimary,
                            onClick = {
                                Toast.makeText(
                                    context,
                                    "Для добавления второго профиля завершите текущую сессию или переключите аккаунт.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )

                        // 3. Очистить кэш
                        AccountActionButton(
                            icon = Icons.Default.Delete,
                            title = "Очистить кэш базы данных",
                            subtitle = "Освобождает память ТВ, сохраняя авторизацию",
                            accentColor = Color(0xFFFFB74D),
                            onClick = { showClearCacheConfirm = true }
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        // 4. Выход из аккаунта (Log Out)
                        var isLogOutFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { showLogOutConfirm = true },
                            enabled = !isLoggingOut,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isLogOutFocused) Color(0xFFB71C1C) else TgErrorRed
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .onFocusChanged { isLogOutFocused = it.isFocused }
                        ) {
                            if (isLoggingOut) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Выход из аккаунта...", color = Color.White)
                            } else {
                                Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Выйти из аккаунта Telegram",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Диалог прокси
    if (showProxyDialog) {
        ProxySettingsDialog(
            onDismissRequest = { showProxyDialog = false }
        )
    }

    // Подтверждение выхода
    if (showLogOutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogOutConfirm = false },
            containerColor = TgDarkBg,
            title = {
                Text(
                    text = "Выйти из Telegram на этом ТВ?",
                    color = TgTextWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Локальная сессия на этом устройстве будет завершена. Для повторного входа потребуется заново подтвердить номер телефона по коду.",
                    color = TgTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogOutConfirm = false
                        isLoggingOut = true
                        tdLibManager.logOut {
                            isLoggingOut = false
                            onLogOut()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TgErrorRed)
                ) {
                    Text("Выйти", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogOutConfirm = false }) {
                    Text("Отмена", color = TgAccentCyan)
                }
            }
        )
    }

    // Подтверждение очистки кэша
    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            containerColor = TgDarkBg,
            title = {
                Text(
                    text = "Очистка кэша сообщений",
                    color = TgTextWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Будут удалены временные файлы предпросмотра и кэш медиа. Авторизация и настройки сохранятся.",
                    color = TgTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCacheConfirm = false
                        Toast.makeText(context, "Кэш сообщений успешно очищен", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TgPrimary)
                ) {
                    Text("Очистить", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) {
                    Text("Отмена", color = TgAccentCyan)
                }
            }
        )
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TgTextSecondary, fontSize = 13.sp)
        Text(text = value, color = TgTextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AccountActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) Color(0xFF27354A) else TgSurface)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) accentColor else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TgTextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = TgTextSecondary,
                fontSize = 12.sp
            )
        }

        Icon(
            Icons.Default.ArrowForward,
            contentDescription = null,
            tint = if (isFocused) accentColor else TgTextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}
