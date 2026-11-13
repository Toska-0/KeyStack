# KeyStack 🔑

Простой Android-лаунчер для чата с нейросетями: выбери провайдера, вставь API-ключ — и общайся с любой моделью.

## Возможности

- Провайдеры из коробки: OpenAI, Anthropic (Claude), Google Gemini, Kilo Code (Gateway), Mistral, Groq, DeepSeek, OpenRouter
- Поддержка любого OpenAI-совместимого endpoint (кастомный Base URL)
- **Автозагрузка моделей**: после вставки ключа приложение само запрашивает список моделей, доступных с этим ключом (через /models)
- **История чатов**: список чатов в боковой панели — переименование, закрепление, удаление; чаты сохраняются на устройстве
- Стриминг ответов в реальном времени (SSE) и анимация «печатает…»
- API-ключ хранится только на устройстве (DataStore), запрос идёт напрямую провайдеру
- Material You, тёмная и светлая тема

## Сборка APK через GitHub Actions

1. Открой вкладку **Actions** в репозитории.
2. Запусти workflow **Build APK** (он также срабатывает на каждый пуш в main).
3. Скачай артефакт **KeyStack-debug-apk** — готовый app-debug.apk.

Для релиза: запушь тег вида v1.1 — APK автоматически приложится к GitHub Release.

## Локальная сборка

Открой проект в Android Studio (JDK 17) или в терминале:

    gradle assembleDebug

Готовый APK: app/build/outputs/apk/debug/app-debug.apk

## Windows-версия (ветка `pc-version`)

На этой ветке добавлен модуль `desktop` — порт приложения на Windows через Compose Desktop (Kotlin, тот же UI и та же логика провайдеров, что и в Android-версии).

Особенности порта:

- Настройки и история чатов хранятся в папке `~/.keystack` (`settings.json` и `chats.json`)
- Вложения прикрепляются через системный диалог выбора файлов Windows
- Динамическая тема Material You заменена на системную/светлую/тёмную/AMOLED

Сборка и запуск локально (JDK 17):

    gradle :desktop:run

Сборка MSI-установщика:

    gradle :desktop:packageDistributionForCurrentOS

Готовый MSI: desktop/build/compose/binaries/main/msi/KeyStack-1.4.0.msi

Сборка через GitHub Actions: workflow **Build Windows Desktop** собирает портативную zip-версию и MSI-установщик на каждый пуш в `pc-version`. При пуше тега вида `v1.4` оба файла автоматически приложатся к GitHub Release.

## Безопасность

Ключ хранится локально на устройстве и отправляется только напрямую выбранному провайдеру. Никаких промежуточных серверов и телеметрии.
