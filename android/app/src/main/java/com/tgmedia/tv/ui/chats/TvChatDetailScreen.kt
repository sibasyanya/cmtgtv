package com.tgmedia.tv.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.tgmedia.tv.data.tdlib.TdLibManager
import kotlinx.coroutines.launch

data class UiMessage(
    val id: Long,
    val senderName: String,
    val text: String,
    val time: String,
    val isOutgoing: Boolean
)

/**
 * 10-Foot UI экран чтения канала/чата и отправки сообщений с пульта ТВ.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvChatDetailScreen(
    chatId: Long,
    chatTitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tdLibManager = remember { TdLibManager.getInstance() }
    val scope = rememberCoroutineScope()

    var messages by remember {
        mutableStateOf(
            listOf(
                UiMessage(1, chatTitle, "Добро пожаловать в просмотр сообщений на Android TV!", "12:00", false),
                UiMessage(2, "Вы", "Отлично, сообщения с пульта телевизора отправляются без задержек.", "12:02", true)
            )
        )
    }

    var inputText by remember { mutableStateOf("") }
    var isInputFocused by remember { mutableStateOf(false) }
    var isSendFocused by remember { mutableStateOf(false) }

    val quickPhrases = listOf(
        "Да, отлично!",
        "Смотрю на ТВ",
        "Позже отвечу",
        "Спасибо за инфу"
    )

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val newMsg = UiMessage(
            id = System.currentTimeMillis(),
            senderName = "Вы (ТВ)",
            text = text,
            time = "Сейчас",
            isOutgoing = true
        )
        messages = messages + newMsg
        inputText = ""

        scope.launch {
            tdLibManager.sendMessage(chatId, text)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090A0F))
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        // Заголовок чата
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { onBack() },
                    modifier = Modifier.focusable()
                ) {
                    Text("← Назад к чатам", fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = chatTitle,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Text(
                text = "ID чата: $chatId",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Список сообщений
        LazyColumn(
            reverseLayout = false,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(messages) { msg ->
                val alignment = if (msg.isOutgoing) Alignment.End else Alignment.Start
                val bgBubbleColor = if (msg.isOutgoing) Color(0xFF0284C7) else Color(0xFF1B1E2B)

                Column(
                    horizontalAlignment = alignment,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .background(bgBubbleColor, RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Column {
                            if (!msg.isOutgoing) {
                                Text(
                                    text = msg.senderName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(
                                text = msg.text,
                                fontSize = 14.sp,
                                color = Color.White,
                                lineHeight = 19.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = msg.time,
                                fontSize = 10.sp,
                                color = Color(0xFFA1A1AA),
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Быстрые ответы для ТВ-пульта
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(quickPhrases) { phrase ->
                var isPhraseFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .onFocusChanged { isPhraseFocused = it.isFocused }
                        .background(
                            if (isPhraseFocused) Color(0xFF0284C7) else Color(0xFF191C29),
                            RoundedCornerShape(20.dp)
                        )
                        .border(
                            width = if (isPhraseFocused) 2.dp else 1.dp,
                            color = if (isPhraseFocused) Color(0xFF38BDF8) else Color(0xFF2C3147),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { sendMessage(phrase) }
                        .focusable()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = phrase,
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Строка ввода и кнопка отправки
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isInputFocused = it.isFocused }
                    .background(Color(0xFF131520), RoundedCornerShape(12.dp))
                    .border(
                        width = if (isInputFocused) 3.dp else 1.dp,
                        color = if (isInputFocused) Color(0xFF38BDF8) else Color(0xFF262A3C),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (inputText.isEmpty() && !isInputFocused) {
                    Text(
                        text = "Нажмите ОК для ввода текста клавиатурой или голосом...",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(Color(0xFF38BDF8)),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = { sendMessage(inputText) },
                modifier = Modifier
                    .onFocusChanged { isSendFocused = it.isFocused }
                    .border(
                        width = if (isSendFocused) 3.dp else 1.dp,
                        color = if (isSendFocused) Color(0xFF38BDF8) else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .focusable()
            ) {
                Text(
                    text = "Отправить",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}
