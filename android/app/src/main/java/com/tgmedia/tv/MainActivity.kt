package com.tgmedia.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.tgmedia.tv.ui.auth.QrAuthScreen
import com.tgmedia.tv.ui.chats.TvChatDetailScreen
import com.tgmedia.tv.ui.chats.TvChatListScreen

sealed class TvScreen {
    object Auth : TvScreen()
    object ChatList : TvScreen()
    data class ChatDetail(val chatId: Long, val chatTitle: String) : TvScreen()
}

/**
 * Главная Activity для Android TV (Single Activity Architecture).
 * Обрабатывает кнопку «Назад» с пульта ДУ телевизора без случайного закрытия приложения.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var currentScreen by remember { mutableStateOf<TvScreen>(TvScreen.Auth) }

            // Обработка кнопки «Назад» на пульте ТВ:
            // Если находимся в чате -> возвращаемся в список чатов
            // Если в списке чатов -> возвращаемся на экран авторизации/главный экран
            // Если уже на Auth -> стандартный выход из приложения
            BackHandler(enabled = currentScreen !is TvScreen.Auth) {
                when (currentScreen) {
                    is TvScreen.ChatDetail -> {
                        currentScreen = TvScreen.ChatList
                    }
                    is TvScreen.ChatList -> {
                        currentScreen = TvScreen.Auth
                    }
                    is TvScreen.Auth -> {
                        // Позволяем системе свернуть приложение
                    }
                }
            }

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
                            currentScreen = TvScreen.ChatDetail(chatId, title)
                        }
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
            }
        }
    }
}
