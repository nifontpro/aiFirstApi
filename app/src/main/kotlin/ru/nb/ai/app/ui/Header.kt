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

    fun printHelp(terminal: Terminal) {
        val w = terminal.writer()
        w.println()
        w.println("$G╔$SEP╗$R")
        w.println(row("${Y}commands$R"))
        w.println("$G╠$SEP╣$R")
        w.println(row("${C}/models$R         switch model"))
        w.println(row("${C}/system$R <text>  set system prompt"))
        w.println(row("${C}/system$R clear   clear system prompt"))
        w.println(row("${C}/t$R <0.0–1.0>      set temperature"))
        w.println(row("${C}/t$R reset           reset temperature to default"))
        w.println(row("${C}/request$R on|off    show request JSON"))
        w.println(row("${C}/think$R on|off      show thinking tokens"))
        w.println(row("${C}/exit$R              quit"))
        w.println("$G╚$SEP╝$R")
        w.println()
        w.flush()
    }

    fun printRequestMode(terminal: Terminal, enabled: Boolean) {
        val w = terminal.writer()
        w.println()
        w.println("$G╔$SEP╗$R")
        if (enabled) {
            w.println(row("${D}request JSON output$R  $C${BG}on$R"))
        } else {
            w.println(row("${D}request JSON output$R  off"))
        }
        w.println("$G╚$SEP╝$R")
        w.println()
        w.flush()
    }

    fun printThinkMode(terminal: Terminal, enabled: Boolean) {
        val w = terminal.writer()
        w.println()
        w.println("$G╔$SEP╗$R")
        if (enabled) {
            w.println(row("${D}thinking tokens$R      $C${BG}on$R"))
        } else {
            w.println(row("${D}thinking tokens$R      off"))
        }
        w.println("$G╚$SEP╝$R")
        w.println()
        w.flush()
    }

    fun printTemperature(terminal: Terminal, temperature: Double?) {
        val w = terminal.writer()
        w.println()
        w.println("$G╔$SEP╗$R")
        if (temperature == null) {
            w.println(row("${D}temperature reset to default$R"))
        } else {
            w.println(row("${D}temperature set to$R"))
            w.println(row("$C$BG  $temperature  $R"))
        }
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