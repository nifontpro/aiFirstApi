# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
./gradlew run          # сборка и запуск приложения
./gradlew build        # только сборка
./gradlew check        # запуск всех проверок и тестов
./gradlew clean        # очистка артефактов сборки
./gradlew :utils:test  # тесты только модуля utils
./gradlew :app:test    # тесты только модуля app
```

## Архитектура

Мульти-модульный Gradle-проект на Kotlin:

- **`app`** — исполняемое JVM-приложение, точка входа: `ru.nb.ai.app.AppKt`. Зависит от `:utils`.
- **`utils`** — библиотечный модуль с общей логикой. Содержит зависимости kotlinx (datetime, serialization, coroutines). Здесь же живут тесты (JUnit Platform).
- **`buildSrc`** — convention plugin `buildsrc.convention.kotlin-jvm`, применяется в обоих модулях. Устанавливает JVM toolchain 17, настраивает тестовый output.

Зависимости версионируются через `gradle/libs.versions.toml` (version catalog). Kotlin 2.3.0, JVM 17.

Сборка использует Gradle build cache и configuration cache (`gradle.properties`).