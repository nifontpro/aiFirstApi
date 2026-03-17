package ru.nb.ai.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double? = null,
    val stream: Boolean = false,
)

@Serializable
data class ChatChunk(
    val choices: List<ChunkChoice>,
)

@Serializable
data class ChunkChoice(
    val delta: Delta,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class Delta(
    val content: String? = null,
)

@Serializable
data class ChatResponse(
    val choices: List<Choice>,
)

@Serializable
data class Choice(
    val message: ChatMessage,
    @SerialName("finish_reason") val finishReason: String? = null,
)