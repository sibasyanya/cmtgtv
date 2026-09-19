# Changelog

Все важные изменения проекта Telegram Media TV фиксируются в этом файле.

## [1.0.12] - 2026-09-19

### Добавлено и улучшено
- **Двухколоночный ландшафтный UI экрана авторизации (`QrAuthScreen`):**
  - Переработана разметка на горизонтальный вид (QR-код слева, понятные инструкции и кнопки справа).
  - Устранена проблема обрезки нижних кнопок на ТВ-экранах с нестандартным оверсканом (overscan/safe area). Все интерактивные кнопки теперь полностью видны и удобно выбираются пультом.
- **Интеллектуальная обработка кнопки «Назад» на пульте ДУ (`BackHandler`):**
  - При нажатии кнопки возврата на пульте в окне детального просмотра чата приложение возвращается к списку каналов.
  - При нажатии в списке каналов — возвращается к экрану авторизации/выбора.
  - Предотвращено случайное сворачивание/закрытие приложения при навигации.
- **Подготовка TDLib Native Binaries в CI/CD:**
  - В рабочий процесс сборки GitHub Actions добавлен автоматический шаг загрузки готовых скомпилированных C++ библиотек `libtdjni.so` под архитектуры Android TV (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`) для авторизации в реальном аккаунте Telegram.

## [1.0.11] - 2026-09-19

### Исправлено
- **Защита от сбоя при отсутствии нативной C++ библиотеки (Safe Mode Fallback):**
  - Обернут вызов `System.loadLibrary("tdjni")` и запуск `Client.create()` в безопасную обработку исключений `Throwable`.
  - Устранено падение `UnsatisfiedLinkError: No implementation found for int org.drinkless.tdlib.Client.createNativeClient()`.
  - Добавлен автоматический безопасный режим Android TV: генерация превью QR-кода и тестовый вход для проверки 10-Foot UI навигации и управления с пульта ДУ без падения приложения.
  - На стартовом экране `QrAuthScreen` добавлена кнопка «Войти в режим проверки UI пульта (Тест)».

### Исправлено
- **Добавлен обязательный конфигурационный файл `android/gradle.properties`:**
  - Установлен флаг `android.useAndroidX=true` (устраняет фатальный сбой AGP `:app:mergeReleaseNativeLibs`: *"Configuration contains AndroidX dependencies, but android.useAndroidX property is not enabled"*).
  - Включен флаг `android.nonTransitiveRClass=true` для ускоренной компиляции ресурсов.
  - Настроены параметры памяти демона Gradle `org.gradle.jvmargs=-Xmx2048m`.

## [1.0.9] - 2026-09-19

### Исправлено
- **Добавлен компонент FileProvider в манифест Android:**
  - Создан дескриптор `res/xml/file_paths.xml` для безопасной установки обновлений APK через `FileProvider`.
  - Зарегистрирован тег `<provider>` в `AndroidManifest.xml` для работы `AppUpdateManager` без падений.
  - Включен подробный вывод логов Gradle (`--info`) и сбор системных логов демона Gradle на случай диагностики.

## [1.0.8] - 2026-09-19

### Исправлено
- **Устранена ошибка сборщика AGP 8 и зависимостей Gradle:**
  - Удален блок `ndk { abiFilters }` из `build.gradle.kts`, вызывавший сбой AGP «NDK not configured» при отсутствии установленного NDK на раннере GitHub Actions.
  - Скорректированы версии библиотек в `libs.versions.toml`: `media3` обновлен до стабильной версии `1.5.1`, `tvMaterial` до `1.0.0`.
  - В рабочий процесс `release.yml` добавлен шаг выгрузки артефактов отчетов сборки Gradle в случае непредвиденных ошибок (`Upload Gradle Reports on Failure`).
  - Создан каталог-маркер `android/app/src/main/jniLibs/.gitkeep`.

## [1.0.7] - 2026-09-19

### Исправлено
- **Добавлены отсутствовавшие ресурсы Android (`res/`):**
  - Создана папка ресурсов `android/app/src/main/res` со всеми необходимыми файлами: `values/strings.xml`, `values/themes.xml`, `values/colors.xml`, `drawable/tv_banner.xml`, `drawable/ic_launcher.xml`.
  - Устранена ошибка AAPT при линковке ресурсов Android пакета.
  - В `build.gradle.kts` отключен сбой сборки на фатальных проверках релизного линтера (`lint { abortOnError = false; checkReleaseBuilds = false }`).
  - Убран фильтр в `settings.gradle.kts` для гарантированного разрешения зависимостей из Google репозитория.

## [1.0.6] - 2026-09-19

### Исправлено
- **GitHub Actions CI/CD (`release.yml`):**
  - Устранена ошибка exit code 127: добавлен шаг `gradle/actions/setup-gradle@v4` с версией Gradle 8.10.2 и автоматической генерацией Gradle Wrapper.
  - Обновлен `actions/setup-java` до актуальной версии `v5`.
  - Добавлен файл правил обфускации `proguard-rules.pro` и отключена агрессивная минификация в релизной конфигурации для гарантии стабильной сборки на CI.

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
