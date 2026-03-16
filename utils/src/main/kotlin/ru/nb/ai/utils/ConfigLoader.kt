package ru.nb.ai.utils

import java.io.File
import java.util.Properties

object ConfigLoader {
    fun load(path: String = "litellm.properties"): Properties {
        val props = Properties()
        File(path).inputStream().use { props.load(it) }
        return props
    }
}