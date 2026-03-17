package ru.nb.ai.utils

import java.io.File
import java.util.Properties

object ConfigLoader {
    fun load(path: String = "litellm.properties"): Properties {
        val file = File(path)
        if (!file.exists()) error(
            "Config file '$path' not found. Create it with:\n" +
            "  litellm.baseUrl=http://...\n" +
            "  litellm.apiKey=..."
        )
        val props = Properties()
        file.inputStream().use { props.load(it) }
        return props
    }
}
