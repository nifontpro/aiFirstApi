package ru.nb.ai.app.ui

import org.jline.terminal.Terminal

object Header {

    private const val G = "\u001B[32m"    // green
    private const val BG = "\u001B[1;32m" // bold green
    private const val C = "\u001B[36m"    // cyan
    private const val Y = "\u001B[33m"    // yellow
    private const val D = "\u001B[2m"     // dim
    private const val R = "\u001B[0m"     // reset

    private const val INNER = 52
    private val SEP = "═".repeat(INNER)

    private val LOGO = listOf(
        " ██████╗██╗  ██╗ █████╗ ████████╗",
        "██╔════╝██║  ██║██╔══██╗╚══██╔══╝",
        "██║     ███████║███████║   ██║   ",
        "██║     ██╔══██║██╔══██║   ██║   ",
        "╚██████╗██║  ██║██║  ██║   ██║   ",
        " ╚═════╝╚═╝  ╚═╝╚═╝  ╚═╝   ╚═╝   ",
    )

    fun print(terminal: Terminal, model: String, baseUrl: String) {
        val w = terminal.writer()
        val server = baseUrl
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
            .removeSuffix("/v1")

        w.println("$G╔$SEP╗$R")
        w.println(row(""))
        LOGO.forEach { w.println(row("$BG$it$R")) }
        w.println(row(""))
        w.println("$G╠$SEP╣$R")
        w.println(row("${C}model $R ›  $Y$model$R"))
        w.println(row("${C}server$R ›  $D$server$R"))
        w.println("$G╠$SEP╣$R")
        w.println(row("${D}/models$R  switch model   ${D}/system$R  set role   ${D}/exit$R  quit"))
        w.println("$G╚$SEP╝$R")
        w.println()
        w.flush()
    }

    fun printModelChanged(terminal: Terminal, model: String) {
        val w = terminal.writer()
        w.println()
        w.println("$G╔$SEP╗$R")
        w.println(row("${D}model switched to$R"))
        w.println(row("$Y$BG  $model  $R"))
        w.println("$G╚$SEP╝$R")
        w.println()
        w.flush()
    }

    fun printSystemPrompt(terminal: Terminal, prompt: String?) {
        val w = terminal.writer()
        w.println()
        w.println("$G╔$SEP╗$R")
        if (prompt == null) {
            w.println(row("${D}system prompt cleared$R"))
        } else {
            w.println(row("${D}system prompt set$R"))
            val preview = if (prompt.length > INNER - 4) prompt.take(INNER - 7) + "..." else prompt
            w.println(row("$C$preview$R"))
        }
        w.println("$G╚$SEP╝$R")
        w.println()
        w.flush()
    }

    // Strips ANSI codes to measure visible length, then pads to INNER width.
    private fun row(content: String): String {
        val visible = content.replace(Regex("""\u001B\[[^m]*m"""), "")
        val pad = " ".repeat((INNER - 2 - visible.length).coerceAtLeast(0))
        return "$G║$R  $content$pad$G║$R"
    }
}