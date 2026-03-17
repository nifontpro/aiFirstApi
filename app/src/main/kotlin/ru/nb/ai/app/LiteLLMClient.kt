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
import ru.nb.ai.app.model.StreamToken
import ru.nb.ai.app.model.ThinkingConfig

class LiteLLMClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val temperature: Double? = null,
    private val timeoutSeconds: Long = 300,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

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

    fun chatStream(messages: List<ChatMessage>, thinkingBudget: Int? = null): Flow<StreamToken> = flow {
        val thinking = thinkingBudget?.let { ThinkingConfig(type = "enabled", budgetTokens = it) }
        // Extended thinking требует temperature = 1
        val effectiveTemperature = if (thinking != null) 1.0 else temperature
        client.preparePost("$baseUrl/chat/completions") {
            setBody(ChatRequest(model = model, messages = messages, temperature = effectiveTemperature, stream = true, thinking = thinking))
        }.execute { response ->
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
                        val delta = chunk.choices.firstOrNull()?.delta ?: return@onSuccess
                        delta.reasoningContent?.let { emit(StreamToken(it, isThinking = true)) }
                        delta.content?.let { emit(StreamToken(it, isThinking = false)) }
                    }
            }
        }
    }

    fun close() = client.close()
}
