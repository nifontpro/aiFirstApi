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
import kotlinx.serialization.json.Json
import ru.nb.ai.app.model.ChatMessage
import ru.nb.ai.app.model.ChatRequest
import ru.nb.ai.app.model.ChatResponse

class LiteLLMClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
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

    suspend fun chat(messages: List<ChatMessage>): String {
        val response = client.post("$baseUrl/chat/completions") {
            setBody(ChatRequest(model = model, messages = messages))
        }
        val rawBody = response.bodyAsText()

        if (!response.status.isSuccess()) {
            throw IllegalStateException("HTTP ${response.status.value}: $rawBody")
        }

        val parsed = runCatching { json.decodeFromString<ChatResponse>(rawBody) }
            .getOrElse { throw IllegalStateException("Unexpected response format:\n$rawBody", it) }

        return parsed.choices.first().message.content
    }

    fun close() = client.close()
}