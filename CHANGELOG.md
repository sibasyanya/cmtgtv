# Changelog

Все важные изменения проекта Telegram Media TV фиксируются в этом файле.

## [1.0.5] - 2026-09-19

### Добавлено
- **Автоматический запуск сборки APK при синхронизации из AI Studio (`release.yml`):**
  - Добавлен триггер на пуш в ветку `main`.
  - Динамическое извлечение версии из `package.json`.
  - Автоматическое создание/обновление GitHub Release и отправка анонса в Telegram-канал при каждом коммите из студии.

## [1.0.4] - 2026-09-19

### Изменено
- Зафиксирован целевой репозиторий `sibasyanya/cmtgtv` в модуле `AppUpdateManager.kt` для прямого получения обновлений с GitHub Releases на Android TV.

## [1.0.3] - 2026-09-19

### Добавлено
- **Модуль автоматического обновления на ТВ (`AppUpdateManager.kt`):**
  - Запрос к GitHub Releases API для проверки новых версий.
  - Фоновое скачивание APK через `DownloadManager`.
  - Запуск системного PackageInstaller через безопасный `FileProvider`.
- **CI/CD Автоматизация (`.github/workflows/release.yml`):**
  - Сборка релизного APK при пуше тега `v*`.
  - Автоматическое создание GitHub Release с прикреплением собранного `.apk`.
  - Автоматическая публикация новости о релизе в официальный Telegram-канал проекта через Telegram Bot API.

## [1.0.2] - 2026-09-19

### Добавлено
- **Модуль чтения чатов и каналов (`TvChatListScreen`):**
  - Поддержка списков каналов, групп и личных диалогов пользователя на Jetpack Compose for TV.
  - Реализация D-Pad фокуса и выбора чата с пульта ДУ.
- **Модуль просмотра сообщений и отправки текста (`TvChatDetailScreen`, `TvChatView`):**
  - Лента сообщений с автопрокруткой, временными метками и статусом доставки.
  - Быстрые фразы для мгновенного ответа с ТВ-пульта без набора букв.
  - Поле ввода текста с поддержкой системной Android TV экранной клавиатуры и микрофона/голосового ввода.
  - Методы `loadChats`, `getChatHistory` и `sendMessage` в TDLib Manager.
- **Навигация Single-Activity (`MainActivity.kt`):**
  - Маршрутизация `Auth` → `ChatList` → `ChatDetail`.

## [1.0.1] - 2026-09-19

### Изменено
- Привязаны боевые ключи `api_id: 279933` и `api_hash: 1c32273df61c79fc3568bceb0bfb73a9` (Cybermasters TGTV / cmtgtv) в конфигурацию `TelegramMediaTvApp.kt`.
- Обновлена версия приложения до 1.0.1 (versionCode: 2).

## [1.0.0] - 2026-09-18

### Добавлено
- **Модуль 1 (Конфигурация и Манифест Android TV):**
  - Подготовлен `AndroidManifest.xml` с флагами `android.software.leanback` (обязателен), `android.hardware.touchscreen` (false) и `category.LEANBACK_LAUNCHER`.
  - Оформлен `android/gradle/libs.versions.toml` (Gradle Version Catalog) с версиями Kotlin 2.0+, Jetpack Compose for TV (`androidx.tv:tv-material:1.1.0`, `tv-foundation:1.0.0-alpha11`), Media3 ExoPlayer 1.11.0, Coil, ZXing и Coroutines.
  - Настроен `android/app/build.gradle.kts` с Target SDK 35, Min SDK 26, поддержкой ABI `arm64-v8a` и `armeabi-v7a`, и подключением директории `jniLibs` для `libtdjni.so`.
- **Модуль 2 (Слой TDLib Manager):**
  - Создана реактивная обертка `TdLibManager` над синглтоном `org.drinkless.tdlib.Client`.
  - Потокобезопасный `StateFlow<TdApi.AuthorizationState>` для отслеживания сессии.
  - Метод `sendTdlibParameters()` с передачей `api_id`, `api_hash`, путей к SQLite БД и кэшу видеофайлов.
  - Интеграция `TdApi.RequestQrCodeAuthentication` со стримингом `tg://login?token=...` через `StateFlow<String?>`.
  - Модели и функции `TdApi.java` и JNI-мост `Client.java`.
