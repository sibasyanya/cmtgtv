package com.tgmedia.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.tgmedia.tv.data.tdlib.TdLibManager
import com.tgmedia.tv.ui.account.TvAccountSettingsScreen
import com.tgmedia.tv.ui.auth.QrAuthScreen
import com.tgmedia.tv.ui.chats.TvChatDetailScreen
import com.tgmedia.tv.ui.chats.TvChatListScreen
import org.drinkless.tdlib.TdApi

sealed class TvScreen {
    object Auth : TvScreen()
    object ChatList : TvScreen()
    data class ChatDetail(val chatId: Long, val chatTitle: String) : TvScreen()
    object AccountSettings : TvScreen()
}

/**
 * Главная Activity для Android TV (Single Activity Architecture).
 * Обрабатывает кнопку «Назад» с пульта ДУ телевизора без случайного закрытия приложения.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val tdLibManager = remember { TdLibManager.getInstance() }
            val authState by tdLibManager.authorizationState.collectAsState()

            var currentScreen by remember { mutableStateOf<TvScreen>(TvScreen.Auth) }

            // Если сессия уже авторизована в TDLib, сразу переключаемся в список чатов
            LaunchedEffect(authState) {
                if (authState is TdApi.AuthorizationStateReady && currentScreen is TvScreen.Auth) {
                    currentScreen = TvScreen.ChatList
                } else if (authState !is TdApi.AuthorizationStateReady && currentScreen !is TvScreen.Auth) {
                    currentScreen = TvScreen.Auth
                }
            }

            // Обработка кнопки «Назад» на пульте ТВ:
            // Если находимся в чате -> возвращаемся в список чатов
            // Если в настройках аккаунта -> возвращаемся в список чатов
            // Если в списке чатов -> система безопасно сворачивает приложение (не выбрасывает на ввод номера)
            BackHandler(enabled = currentScreen !is TvScreen.ChatList && currentScreen !is TvScreen.Auth) {
                when (currentScreen) {
                    is TvScreen.ChatDetail -> {
                        currentScreen = TvScreen.ChatList
                    }
                    is TvScreen.AccountSettings -> {
                        currentScreen = TvScreen.ChatList
                    }
                    else -> Unit
                }
            }

            val chatListState = androidx.compose.foundation.lazy.rememberLazyListState()
            var lastSelectedChatId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }

            when (val screen = currentScreen) {
                is TvScreen.Auth -> {
                    QrAuthScreen(
                        onAuthenticated = {
                            currentScreen = TvScreen.ChatList
                        }
                    )
                }
                is TvScreen.ChatList -> {
                    TvChatListScreen(
                        onSelectChat = { chatId, title ->
                            lastSelectedChatId = chatId
                            currentScreen = TvScreen.ChatDetail(chatId, title)
                        },
                        onOpenAccountSettings = {
                            currentScreen = TvScreen.AccountSettings
                        },
                        listState = chatListState,
                        lastSelectedChatId = lastSelectedChatId
                    )
                }
                is TvScreen.ChatDetail -> {
                    TvChatDetailScreen(
                        chatId = screen.chatId,
                        chatTitle = screen.chatTitle,
                        onBack = {
                            currentScreen = TvScreen.ChatList
                        }
                    )
                }
                is TvScreen.AccountSettings -> {
                    TvAccountSettingsScreen(
                        onBack = {
                            currentScreen = TvScreen.ChatList
                        },
                        onLogOut = {
                            currentScreen = TvScreen.Auth
                        }
                    )
                }
            }
        }
    }
}
