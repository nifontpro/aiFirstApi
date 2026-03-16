plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")

    // Apply the Application plugin to add support for building an executable JVM application.
    application

    // Kotlinx Serialization for JSON parsing of LiteLLM responses.
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    // Project "app" depends on project "utils". (Project paths are separated with ":", so ":utils" refers to the top-level "utils" project.)
    implementation(project(":utils"))
    implementation(libs.bundles.ktor)
    implementation(libs.jlineReader)
    implementation(libs.jlineTerminalJni)
    implementation(libs.slf4jNop)
}

application {
    // Define the Fully Qualified Name for the application main class
    // (Note that Kotlin compiles `App.kt` to a class with FQN `com.example.app.AppKt`.)
    mainClass = "ru.nb.ai.app.AppKt"
}

tasks.named<JavaExec>("run") {
    // Run from root project directory so litellm.properties is found.
    workingDir = rootProject.projectDir
    // Pass stdin through so JLine3 can access the real terminal.
    standardInput = System.`in`
}
