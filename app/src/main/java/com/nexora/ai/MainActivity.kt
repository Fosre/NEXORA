package com.nexora.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class ChatMessage(val text: String, val fromUser: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("nexora", MODE_PRIVATE)
        setContent {
            var endpoint by remember { mutableStateOf(prefs.getString("endpoint", "") ?: "") }
            var showSettings by remember { mutableStateOf(false) }
            var input by remember { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            val messages = remember { mutableStateListOf(ChatMessage("Привет! Я NEXORA. Напиши, чем помочь.", false)) }
            val scope = rememberCoroutineScope()
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF9A8BFF),
                background = Color(0xFF101116),
                surface = Color(0xFF191B24)
            )) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF101116)) {
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text("NEXORA", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                                Text("Твой ИИ-помощник", color = Color.LightGray, fontSize = 12.sp)
                            }
                            TextButton(onClick = { showSettings = true }) { Text("Настройки") }
                        }
                        Spacer(Modifier.height(14.dp))
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(messages) { msg ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start) {
                                    Box(
                                        Modifier.widthIn(max = 300.dp)
                                            .background(if (msg.fromUser) Color(0xFF5145A8) else Color(0xFF242631), RoundedCornerShape(18.dp))
                                            .padding(13.dp)
                                    ) { Text(msg.text, color = Color.White) }
                                }
                            }
                            if (busy) item { Text("NEXORA печатает…", color = Color.Gray) }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = input, onValueChange = { input = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Сообщение…") },
                                shape = RoundedCornerShape(18.dp),
                                maxLines = 4
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(
                                enabled = input.isNotBlank() && !busy,
                                onClick = {
                                    val prompt = input.trim()
                                    input = ""
                                    messages.add(ChatMessage(prompt, true))
                                    if (endpoint.isBlank()) {
                                        messages.add(ChatMessage("Чтобы подключить настоящий ИИ, открой «Настройки» и укажи адрес своего backend-сервера. API-ключ нельзя помещать в APK.", false))
                                    } else {
                                        busy = true
                                        scope.launch {
                                            val answer = withContext(Dispatchers.IO) { askServer(endpoint, prompt) }
                                            messages.add(ChatMessage(answer, false))
                                            busy = false
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
                            ) { Text("➤") }
                        }
                    }
                }
                if (showSettings) {
                    AlertDialog(
                        onDismissRequest = { showSettings = false },
                        title = { Text("Подключение ИИ") },
                        text = {
                            Column {
                                Text("URL backend-сервера (например, https://example.com)")
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, singleLine = true, placeholder = { Text("https://…") })
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                prefs.edit().putString("endpoint", endpoint.trim()).apply()
                                showSettings = false
                            }) { Text("Сохранить") }
                        },
                        dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Отмена") } }
                    )
                }
            }
        }
    }
}

private fun askServer(base: String, prompt: String): String {
    var connection: HttpURLConnection? = null
    return try {
        val url = URL(base.trimEnd('/') + "/chat")
        connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        val escaped = prompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        connection.outputStream.use { it.write("{\"message\":\"$escaped\"}".toByteArray()) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) "Ошибка сервера: $body" else {
            Regex("\"reply\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
                .find(body)?.groupValues?.get(1)?.replace("\\n", "\n") ?: body
        }
    } catch (e: Exception) {
        "Не удалось подключиться: ${e.localizedMessage ?: "проверь URL и интернет"}"
    } finally {
        connection?.disconnect()
    }
}
