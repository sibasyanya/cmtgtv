# РУКОВОДСТВО ДЛЯ GOOGLE AI STUDIO: ВНЕДРЕНИЕ ОПОВЕЩЕНИЙ В TELEGRAM О НОВЫХ РЕЛИЗАХ

Данное руководство предназначено для использования в качестве контекста/инструкции (Custom Instructions, NotebookLM, AGENTS.md) для нейросетевого ассистента Google AI Studio при разработке любых типов проектов (Android, Desktop Tauri, Web SPA / Full-stack).

---

## 1. ЦЕЛЬ И НАЗНАЧЕНИЕ

Обеспечить автоматическую отправку уведомлений в Telegram-канал, группу или личный чат разработчика в момент публикации новой версии продукта через GitHub Actions. При необходимости — прикрепление собранного бинарного файла (.apk, .exe, .zip) прямо в Telegram.

---

## 2. ИНСТРУКЦИЯ ДЛЯ AI-АССИСТЕНТА GOOGLE AI STUDIO (ПРОМПТ-ДИРЕКТИВА)

При получении задачи «Внедри оповещения в Telegram о выходе новых версий» ассистент обязан выполнить следующие шаги:

### Шаг 1. Анализ типа проекта и поиск CI/CD workflow
1. Найти конфигурационный файл релиза в `.github/workflows/` (обычно `release.yml`, `build.yml` или `deploy.yml`).
2. Если файла нет — создать стандартизированный `.github/workflows/release.yml` для целевой платформы (Android Gradle / Tauri Cargo / Web Vite).

### Шаг 2. Внедрение шага нотификации в `.github/workflows/release.yml`
Добавить шаг отправки сообщения строго **после** шага создания релиза на GitHub (`actions/create-release` или `softprops/action-gh-release`), с условием `if: success()`.

Использовать curl к Telegram Bot API без тяжелых сторонних внешних Actions для максимальной надежности и скорости:

```yaml
      - name: Send Release Notification to Telegram
        if: success()
        env:
          TELEGRAM_BOT_TOKEN: ${{ secrets.TELEGRAM_BOT_TOKEN }}
          TELEGRAM_CHAT_ID: ${{ secrets.TELEGRAM_CHAT_ID }}
        run: |
          if [ -z "$TELEGRAM_BOT_TOKEN" ] || [ -z "$TELEGRAM_CHAT_ID" ]; then
            echo "Telegram secrets are not set. Skipping notification."
            exit 0
          fi

          TAG_NAME="${{ github.ref_name }}"
          REPO_NAME="${{ github.repository }}"
          REPO_URL="${{ github.server_url }}/${REPO_NAME}"
          RELEASE_URL="${REPO_URL}/releases/tag/${TAG_NAME}"
          COMMIT_MSG="${{ github.event.head_commit.message }}"

          # Формирование HTML-сообщения
          MESSAGE="🚀 <b>Доступна новая версия: ${TAG_NAME}!</b>%0A%0A"
          MESSAGE+="📦 <b>Проект:</b> <code>${REPO_NAME}</code>%0A"
          MESSAGE+="🏷 <b>Версия:</b> <code>${TAG_NAME}</code>%0A"
          if [ -n "$COMMIT_MSG" ]; then
            SAFE_MSG=$(echo "$COMMIT_MSG" | head -n 3 | sed 's/[<>&]/ /g')
            MESSAGE+="📝 <b>Описание:</b>%0A<i>${SAFE_MSG}</i>%0A%0A"
          fi
          MESSAGE+="📥 <b>Скачать релиз:</b> <a href=\"${RELEASE_URL}\">Перейти на GitHub</a>"

          curl -s -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
            -d "chat_id=${TELEGRAM_CHAT_ID}" \
            -d "parse_mode=HTML" \
            -d "disable_web_page_preview=false" \
            -d "text=${MESSAGE}"
```

### Шаг 3. Дополнительно: Отправка собранного дистрибутива в Telegram (опционально)
Если размер артефакта менее 50 МБ (лимит Telegram Bot API):

```yaml
      - name: Upload Binary to Telegram
        if: success()
        env:
          TELEGRAM_BOT_TOKEN: ${{ secrets.TELEGRAM_BOT_TOKEN }}
          TELEGRAM_CHAT_ID: ${{ secrets.TELEGRAM_CHAT_ID }}
        run: |
          if [ -n "$TELEGRAM_BOT_TOKEN" ] && [ -n "$TELEGRAM_CHAT_ID" ]; then
            # Путь к собранному файлу (зависит от типа проекта):
            # Android: android/app/build/outputs/apk/release/*.apk
            # Desktop: src-tauri/target/release/bundle/msi/*.msi
            APK_FILE=$(ls android/app/build/outputs/apk/release/*.apk | head -n 1)
            if [ -f "$APK_FILE" ]; then
              curl -s -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendDocument" \
                -F chat_id="${TELEGRAM_CHAT_ID}" \
                -F caption="📱 Файл релиза <b>${{ github.ref_name }}</b>" \
                -F parse_mode="HTML" \
                -F document=@"${APK_FILE}"
            fi
          fi
```

---

## 3. ИНСТРУКЦИЯ ДЛЯ ПОЛЬЗОВАТЕЛЯ (НАСТРОЙКА ТОКЕНОВ)

Чтобы оповещения заработали, пользователю достаточно выполнить 3 шага:

### 3.1. Создать Telegram-бота
1. Открыть `@BotFather` в Telegram, отправить `/newbot`.
2. Задать имя и юзернейм (например, `myapp_release_bot`).
3. Скопировать токен (вида `1234567890:ABCdefGHIjkl...`).

### 3.2. Получить ID получателя
* **Личный чат:** отправить любое сообщение своему созданному боту, открыть в браузере:  
  `https://api.telegram.org/bot<ТОКЕН>/getUpdates` и найти поле `"chat":{"id": 123456789}`.
* **Канал или Группа:** добавить созданного бота администратором в канал/группу. Для публичного канала ID равен `@имя_канала`. Для приватного — переслать сообщение боту `@getmyid_bot` для получения ID со знаком минус (например, `-1001234567890`).

### 3.3. Прописать секреты в GitHub
В GitHub репозитории: **Settings** → **Secrets and variables** → **Actions** → **New repository secret**:
* `TELEGRAM_BOT_TOKEN` = полученный токен от @BotFather.
* `TELEGRAM_CHAT_ID` = ID чата или канала.

---

## 4. ЧЕК-ЛИСТ ПРОВЕРКИ
- [x] Шаг в GitHub Actions защищен условием проверки наличия секретов (билд не ломается, если секреты еще не заданы).
- [x] Специальные символы экранируются или передаются в URL/HTML формате.
- [x] При создании тега `git tag vX.Y.Z && git push origin vX.Y.Z` оповещение автоматически падает в Telegram.
