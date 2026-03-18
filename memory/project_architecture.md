---
name: Архитектура firstApi
description: Полная архитектура проекта: API-клиент, стриминг, команды, структура файлов
type: project
---

Мульти-модульный Gradle/Kotlin проект — терминальный чат-клиент через LiteLLM.

## Структура файлов

- `app/src/main/kotlin/ru/nb/ai/app/App.kt` — точка входа, главный цикл, обработка команд
- `app/src/main/kotlin/ru/nb/ai/app/LiteLLMClient.kt` — HTTP-клиент (Ktor CIO)
- `app/src/main/kotlin/ru/nb/ai/app/model/ChatModels.kt` — data classes
- `app/src/main/kotlin/ru/nb/ai/app/ui/Header.kt` — вывод UI (JLine terminal)
- `app/src/main/kotlin/ru/nb/ai/app/ui/ModelSelector.kt` — интерактивный выбор модели
- `utils/src/main/kotlin/ru/nb/ai/utils/ConfigLoader.kt` — загрузка litellm.properties

## Запрос к API — стриминг

`LiteLLMClient.chatStream(messages, thinkingBudget?)` возвращает `Flow<StreamToken>`:
- **ВАЖНО**: используется `preparePost().execute { }`, а НЕ `post()` — иначе Ktor буферизует весь ответ
- Читает SSE построчно через `bodyAsChannel().readUTF8Line()`
- Парсит `data: {...}` чанки в `ChatStreamChunk`
- Эмитит `StreamToken(text, isThinking)` — отдельно `reasoningContent` и `content`
- `explicitNulls = false` в Json — чтобы null-поля не шли в запрос
- Extended thinking для Claude: `ThinkingConfig(type="enabled", budgetTokens=10000)` + `temperature=1.0`

## Data classes (ChatModels.kt)

- `ChatMessage(role, content)` — сообщение
- `ChatRequest(model, messages, temperature?, stream, thinking?)` — тело запроса
- `ThinkingConfig(type, budgetTokens)` — **type без дефолта**, иначе не сериализуется (encodeDefaults=false по умолчанию)
- `ChatStreamChunk → StreamChoice → StreamDelta(content?, reasoningContent?)` — SSE-чанк
- `StreamToken(text, isThinking)` — токен для вывода

## App.kt — архитектура

- `appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())` — единый scope, отменяется в `finally`
- `rebuildClient()` — локальная функция, вызывается при смене модели или температуры
- API-запрос: `appScope.launch { client.chatStream(...).collect { ... } }`
- Отмена по клавише `S`: daemon-поток в raw mode читает символы через `terminal.reader().read(100L)`
  - Ловит `InterruptedException` И `java.io.InterruptedIOException`
  - Восстанавливает terminal attributes в `finally`
- При отмене: если текст уже накоплен — сохраняется в историю с пометкой `[отменено]`

## Команды

| Команда | Действие |
|---------|----------|
| `/models` | выбор модели (очищает историю) |
| `/system <text>` / `/system clear` | системный промпт |
| `/t <0.0-1.0>` / `/t reset` | температура |
| `/request on\|off` | показывать JSON запроса |
| `/think on\|off` | показывать reasoning_content; для Claude добавляет ThinkingConfig |
| `/exit` | выход |
| `S` во время стриминга | остановить генерацию |

## Модели (AVAILABLE_MODELS)

Claude, GPT, Gemini, DeepSeek, Grok, Qwen — через LiteLLM-прокси.
DEFAULT_MODEL = "claude-sonnet-4-5"

## Ключевые решения и почему

- **preparePost vs post**: `post()` буферизует ответ → стриминг не работает
- **ThinkingConfig.type без дефолта**: kotlinx.serialization encodeDefaults=false → поля с дефолтами не сериализуются
- **Thread.interrupt() не использовать для пробуждения JLine**: JLine интерпретирует как UserInterruptException → ложная отмена
- **explicitNulls=false**: без этого `"thinking": null` идёт во все модели
- **terminal.reader().read(100L) в raw mode**: единственный способ читать одиночные клавиши без Enter

## Конфиг

`litellm.properties` в корне проекта:
```
litellm.baseUrl=http://...
litellm.apiKey=...
litellm.timeoutSeconds=300  # опционально
```
