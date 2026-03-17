package ru.nb.ai.app

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jline.reader.*
import org.jline.reader.impl.history.DefaultHistory
import org.jline.terminal.TerminalBuilder
import org.jline.utils.AttributedString
import org.jline.utils.AttributedStyle
import ru.nb.ai.app.model.ChatMessage
import ru.nb.ai.app.model.ChatRequest
import ru.nb.ai.app.ui.Header
import ru.nb.ai.app.ui.ModelSelector
import ru.nb.ai.utils.ConfigLoader
import java.util.logging.Level
import java.util.logging.Logger

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
    var temperature: Double? = null
    var showRequest = false
    val prettyJson = Json { prettyPrint = true; encodeDefaults = false }

    val terminal = TerminalBuilder.builder().system(true).build()

    Header.print(terminal, model, baseUrl)

    val timeoutSeconds = props.getProperty("litellm.timeoutSeconds", "300").toLongOrNull() ?: 300
    var client = LiteLLMClient(baseUrl, apiKey, model, temperature, timeoutSeconds)
    val history = mutableListOf<ChatMessage>()
    var systemPrompt: String? = null

    val completer = Completer { _, line, candidates ->
        val word = line.word()
        listOf(
            Candidate("/?", "/?", null, "Show help", null, null, true),
            Candidate("/models", "/models", null, "Switch model", null, null, true),
            Candidate("/system", "/system", null, "Set system prompt", null, null, false),
            Candidate("/t", "/t", null, "Set temperature 0.0–1.0", null, null, false),
            Candidate("/request", "/request", null, "Show request JSON (on/off)", null, null, false),
            Candidate("/exit", "/exit", null, "Exit application", null, null, true),
        ).filter { it.value().startsWith(word) }.forEach(candidates::add)
    }

    val prompt = AttributedString(
        "> ",
        AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN)
    ).toAnsi(terminal)

    val reader = LineReaderBuilder.builder()
        .terminal(terminal)
        .completer(completer)
        .history(DefaultHistory())
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

            input.equals("/?", ignoreCase = true) -> Header.printHelp(terminal)

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
                    client = LiteLLMClient(baseUrl, apiKey, model, temperature, timeoutSeconds)
                    history.clear()
                    Header.printModelChanged(terminal, model)
                }
            }

            input.startsWith("/t", ignoreCase = true) -> {
                val arg = input.drop(2).trim()
                if (arg.isEmpty() || arg.equals("reset", ignoreCase = true)) {
                    temperature = null
                } else {
                    val parsed = arg.toDoubleOrNull()
                    if (parsed == null || parsed !in 0.0..1.0) {
                        terminal.writer().println("\u001B[31mInvalid temperature. Use a value between 0.0 and 1.0 or 'reset'.\u001B[0m\n")
                        terminal.writer().flush()
                        continue
                    }
                    temperature = parsed
                }
                client.close()
                client = LiteLLMClient(baseUrl, apiKey, model, temperature, timeoutSeconds)
                Header.printTemperature(terminal, temperature)
            }

            input.startsWith("/request", ignoreCase = true) -> {
                val arg = input.drop(8).trim()
                showRequest = when {
                    arg.equals("on", ignoreCase = true) -> true
                    arg.equals("off", ignoreCase = true) -> false
                    else -> {
                        terminal.writer().println("\u001B[31mUsage: /request on|off\u001B[0m\n")
                        terminal.writer().flush()
                        continue
                    }
                }
                Header.printRequestMode(terminal, showRequest)
            }

            input.isNotEmpty() -> {
                history.add(ChatMessage(role = "user", content = input))
                try {
                    val messages = buildList {
                        systemPrompt?.let { add(ChatMessage(role = "system", content = it)) }
                        addAll(history)
                    }
                    if (showRequest) {
                        val requestBody = ChatRequest(model = model, messages = messages, temperature = temperature)
                        terminal.writer().println("\u001B[2m${prettyJson.encodeToString(requestBody)}\u001B[0m\n")
                        terminal.writer().flush()
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