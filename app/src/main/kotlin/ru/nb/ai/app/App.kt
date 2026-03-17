package ru.nb.ai.app

import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.jline.reader.*
import org.jline.reader.impl.history.DefaultHistory
import org.jline.terminal.Attributes
import org.jline.terminal.TerminalBuilder
import org.jline.utils.AttributedString
import org.jline.utils.AttributedStyle
import ru.nb.ai.app.model.ChatMessage
import ru.nb.ai.app.model.ChatRequest
import ru.nb.ai.app.model.StreamToken
import ru.nb.ai.app.model.ThinkingConfig
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
    var showThinking = false
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
            Candidate("/think", "/think", null, "Show thinking tokens (on/off)", null, null, false),
            Candidate("/exit", "/exit", null, "Exit application", null, null, true),
        ).filter { it.value().startsWith(word) }.forEach(candidates::add)
    }

    val prompt = AttributedString(
        "> ",
        AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN)
    ).toAnsi(terminal)

    val historyFile = java.nio.file.Paths.get(System.getProperty("user.home"), ".chat_history")

    val reader = LineReaderBuilder.builder()
        .terminal(terminal)
        .completer(completer)
        .history(DefaultHistory())
        .variable(LineReader.HISTORY_FILE, historyFile)
        .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
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

            input.startsWith("/think", ignoreCase = true) -> {
                val arg = input.drop(6).trim()
                showThinking = when {
                    arg.equals("on", ignoreCase = true) -> true
                    arg.equals("off", ignoreCase = true) -> false
                    else -> {
                        terminal.writer().println("\u001B[31mUsage: /think on|off\u001B[0m\n")
                        terminal.writer().flush()
                        continue
                    }
                }
                Header.printThinkMode(terminal, showThinking)
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
                val messages = buildList {
                    systemPrompt?.let { add(ChatMessage(role = "system", content = it)) }
                    addAll(history)
                }
                val thinkingBudget = if (showThinking && model.startsWith("claude", ignoreCase = true)) 10_000 else null

                if (showRequest) {
                    val requestBody = ChatRequest(model = model, messages = messages, temperature = temperature, stream = true,
                        thinking = thinkingBudget?.let { ThinkingConfig(type = "enabled", budgetTokens = it) })
                    terminal.writer().println("\u001B[2m${prettyJson.encodeToString(requestBody)}\u001B[0m\n")
                    terminal.writer().flush()
                }

                val accumulated = StringBuilder()
                var apiException: Exception? = null

                val apiJob = CoroutineScope(Dispatchers.IO).launch {
                    try {
                        var thinkingStarted = false
                        var contentStarted = false
                        client.chatStream(messages, thinkingBudget).collect { token ->
                            when {
                                token.isThinking && showThinking -> {
                                    if (!thinkingStarted) {
                                        terminal.writer().println("\u001B[2m[думает]\u001B[0m")
                                        thinkingStarted = true
                                    }
                                    terminal.writer().print("\u001B[2m${token.text}\u001B[0m")
                                    terminal.writer().flush()
                                }
                                !token.isThinking -> {
                                    if (!contentStarted) {
                                        if (thinkingStarted) terminal.writer().println()
                                        terminal.writer().print("\u001B[36m<\u001B[0m ")
                                        contentStarted = true
                                    }
                                    accumulated.append(token.text)
                                    terminal.writer().print(token.text)
                                    terminal.writer().flush()
                                }
                            }
                        }
                        terminal.writer().println("\n")
                        terminal.writer().flush()
                    } catch (_: CancellationException) {
                    } catch (e: Exception) {
                        apiException = e
                    }
                }

                val stopThread = Thread {
                    val savedAttrs = terminal.attributes
                    try {
                        val rawAttrs = terminal.attributes
                        rawAttrs.setLocalFlag(Attributes.LocalFlag.ICANON, false)
                        rawAttrs.setLocalFlag(Attributes.LocalFlag.ECHO, false)
                        terminal.setAttributes(rawAttrs)
                        while (!Thread.currentThread().isInterrupted) {
                            val ch = terminal.reader().read(100L)
                            if (ch == 's'.code || ch == 'S'.code) {
                                apiJob.cancel()
                                break
                            }
                        }
                    } catch (_: InterruptedException) {
                    } catch (_: java.io.InterruptedIOException) {
                    } finally {
                        terminal.setAttributes(savedAttrs)
                    }
                }.also { it.isDaemon = true; it.start() }

                runBlocking { apiJob.join() }
                stopThread.interrupt()

                when {
                    apiJob.isCancelled -> {
                        val partial = accumulated.toString()
                        if (partial.isNotEmpty()) {
                            terminal.writer().println("\n\u001B[33m[отменено]\u001B[0m\n")
                            history.add(ChatMessage(role = "assistant", content = partial))
                        } else {
                            terminal.writer().println("\u001B[33mЗапрос отменён.\u001B[0m\n")
                            history.removeLast()
                        }
                    }
                    apiException != null -> {
                        if (accumulated.isEmpty()) history.removeLast()
                        terminal.writer().println("\n\u001B[31mError: ${apiException.message}\u001B[0m\n")
                    }
                    else -> {
                        history.add(ChatMessage(role = "assistant", content = accumulated.toString()))
                    }
                }
                terminal.writer().flush()
            }
        }
    }

    client.close()
    terminal.close()
}