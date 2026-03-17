package ru.nb.ai.app

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import ru.nb.ai.app.model.ChatChunk
import ru.nb.ai.app.model.ChatMessage
import ru.nb.ai.app.model.ChatRequest

class LiteLLMClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val temperature: Double? = null,
    private val timeoutSeconds: Long = 300,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis  = timeoutSeconds * 1_000
            connectTimeoutMillis  = 30_000
            socketTimeoutMillis   = timeoutSeconds * 1_000
        }
        install(DefaultRequest) {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
        }
        expectSuccess = false
    }

    fun chatStream(messages: List<ChatMessage>): Flow<String> = flow {
        val response = client.post("$baseUrl/chat/completions") {
            setBody(ChatRequest(model = model, messages = messages, temperature = temperature, stream = true))
        }

        if (!response.status.isSuccess()) {
            throw IllegalStateException("HTTP ${response.status.value}: ${response.bodyAsText()}")
        }

        val channel = response.bodyAsChannel()
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            if (!line.startsWith("data: ")) continue
            val data = line.removePrefix("data: ").trim()
            if (data == "[DONE]") break
            runCatching { json.decodeFromString<ChatChunk>(data) }
                .onSuccess { chunk ->
                    chunk.choices.firstOrNull()?.delta?.content?.let { emit(it) }
                }
        }
    }

    fun close() = client.close()
}
