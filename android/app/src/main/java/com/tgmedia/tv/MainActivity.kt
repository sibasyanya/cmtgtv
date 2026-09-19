package com.tgmedia.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
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
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var currentScreen by remember { mutableStateOf<TvScreen>(TvScreen.Auth) }

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
