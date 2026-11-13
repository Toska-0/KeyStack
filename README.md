# KeyStack 🔑

Простой Android-лаунчер для чата с нейросетями: выбери провайдера, вставь API-ключ — и общайся с любой моделью.

## Возможности

- Провайдеры из коробки: OpenAI, Anthropic (Claude), Google Gemini, Mistral, Groq, DeepSeek, OpenRouter
- Поддержка любого OpenAI-совместимого endpoint (кастомный Base URL)
- Стриминг ответов в реальном времени (SSE)
- API-ключ хранится только на устройстве (DataStore),Request идёт напрямую провайдеру
- Material You, тёмная и светлая тема

## Сборка APK через GitHub Actions

1. Открой вкладку **Actions** в репозитории.
2. Запусти workflow **Build APK** (он также срабатывает на каждый пуш в main).
3. Скачай артефакт **KeyStack-debug-apk** — готовый app-debug.apk.

Для релиза: запушь тег вида v1.0 — APK автоматически приложится к GitHub Release.

## Локальная сборка

Открой проект в Android Studio (JDK 17) или в терминале:

    gradle assembleDebug

Готовый APK: app/build/outputs/apk/debug/app-debug.apk

## Безопасность

Ключ хранится локально на устройстве и отправляется только напрямую выбранному провайдеру. Никаких промежуточных серверов и телеметрии.
