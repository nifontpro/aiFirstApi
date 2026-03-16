package ru.nb.ai.app.ui

object ModelSelector {

    fun select(models: List<String>, current: String): String {
        println("\u001B[33m  Available models:\u001B[0m")
        models.forEachIndexed { i, model ->
            val mark = if (model == current) " \u001B[32m◀\u001B[0m" else ""
            println("  \u001B[2m${(i + 1).toString().padStart(2)}.\u001B[0m  $model$mark")
        }
        print("\n\u001B[33m  Select [1-${models.size}] or Enter to keep current:\u001B[0m ")
        System.out.flush()

        val input = readlnOrNull()?.trim() ?: return current
        if (input.isEmpty()) return current

        val idx = input.toIntOrNull()
        if (idx != null && idx in 1..models.size) return models[idx - 1]

        println("\u001B[31m  Invalid input, keeping current model.\u001B[0m")
        return current
    }
}