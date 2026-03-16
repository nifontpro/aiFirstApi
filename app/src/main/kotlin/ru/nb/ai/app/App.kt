package ru.nb.ai.app

import kotlinx.coroutines.runBlocking
import java.util.logging.Level
import java.util.logging.Logger
import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.terminal.TerminalBuilder
import org.jline.utils.AttributedString
import org.jline.utils.AttributedStyle
import ru.nb.ai.app.model.ChatMessage
import ru.nb.ai.app.ui.Header
import ru.nb.ai.app.ui.ModelSelector
import ru.nb.ai.utils.ConfigLoader

val AVAILABLE_MODELS = listOf(
    "claude-sonnet-4-5",
    "claude-haiku-4-5",
    "claude-opus-4-5",
    "claude-opus-4-6",
    "gpt-4o",
    "gpt-4.1",
    "gpt-4.1-mini",
    "gpt-4.1-nano",
    "gpt-4-turbo",
    "gpt-3.5-turbo",
    "gpt-5",
    "gpt-5-mini",
    "gemini-2.5-pro",
    "gemini-2.5-flash",
    "gemini-2.0-flash",
    "deepseek-chat",
    "deepseek-reasoner",
    "grok-4",
    "grok-3",
    "qwen3-max",
)

const val DEFAULT_MODEL = "claude-sonnet-4-5"

fun main() {
    Logger.getLogger("org.jline").level = Level.OFF

    val props = ConfigLoader.load()
    val baseUrl = props.getProperty("litellm.baseUrl")
    val apiKey = props.getProperty("litellm.apiKey")
    var model = DEFAULT_MODEL

    val terminal = TerminalBuilder.builder().system(true).build()

    Header.print(terminal, model, baseUrl)

    val timeoutSeconds = props.getProperty("litellm.timeoutSeconds", "300").toLongOrNull() ?: 300
    var client = LiteLLMClient(baseUrl, apiKey, model, timeoutSeconds)
    val history = mutableListOf<ChatMessage>()
    var systemPrompt: String? = null

    val completer = Completer { _, line, candidates ->
        val word = line.word()
        if (word.startsWith("/")) {
            listOf(
                Candidate("/models", "/models", null, "Switch model", null, null, true),
                Candidate("/system", "/system", null, "Set system prompt (/system clear to remove)", null, null, false),
                Candidate("/exit", "/exit", null, "Exit application", null, null, true),
            ).filter { it.value().startsWith(word) }.forEach(candidates::add)
        }
    }

    val prompt = AttributedString(
        "> ",
        AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN)
    ).toAnsi(terminal)

    val reader = LineReaderBuilder.builder()
        .terminal(terminal)
        .completer(completer)
        .option(org.jline.reader.LineReader.Option.DISABLE_EVENT_EXPANSION, true)
        .build()

    while (true) {
        val input = try {
            reader.readLine(prompt).trim()
        } catch (_: UserInterruptException) {
            break
        } catch (_: EndOfFileException) {
            break
        }

        when {
            input.equals("/exit", ignoreCase = true) -> break

            input.startsWith("/system", ignoreCase = true) -> {
                val arg = input.removePrefix("/system").removePrefix("/SYSTEM").trim()
                systemPrompt = if (arg.isEmpty() || arg.equals("clear", ignoreCase = true)) null else arg
                Header.printSystemPrompt(terminal, systemPrompt)
            }

            input.equals("/models", ignoreCase = true) -> {
                val newModel = ModelSelector.select(AVAILABLE_MODELS, model)
                if (newModel != model) {
                    model = newModel
                    client.close()
                    client = LiteLLMClient(baseUrl, apiKey, model, timeoutSeconds)
                    history.clear()
                    Header.printModelChanged(terminal, model)
                }
            }

            input.isNotEmpty() -> {
                history.add(ChatMessage(role = "user", content = input))
                try {
                    val messages = buildList {
                        systemPrompt?.let { add(ChatMessage(role = "system", content = it)) }
                        addAll(history)
                    }
                    val reply = runBlocking { client.chat(messages) }
                    history.add(ChatMessage(role = "assistant", content = reply))
                    terminal.writer().println("\u001B[36m<\u001B[0m $reply\n")
                } catch (e: Exception) {
                    history.removeLast()
                    terminal.writer().println("\u001B[31mError: ${e.message}\u001B[0m\n")
                }
                terminal.writer().flush()
            }
        }
    }

    client.close()
    terminal.close()
}