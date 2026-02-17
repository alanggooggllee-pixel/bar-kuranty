# Система контроля качества звонков — Кафе-бар Куранты

Автоматическая система записи, транскрибирования и анализа телефонных звонков hostess.

## Архитектура

```
Android-приложение → Google Drive → Cloud Function → Telegram
(запись + overlay)   (хранилище)    (STT + AI)       (отчёт)
```

### Компоненты

1. **Android-приложение** (`android-app/`) — мониторит звонки, показывает overlay-напоминание о записи, загружает записи MIUI на Google Drive
2. **Backend** (`backend/`) — Python Cloud Function: скачивает записи с Drive, транскрибирует (Whisper), анализирует (Claude), отправляет отчёт в Telegram
3. **Стандарты** (`standards/`) — критерии оценки звонков hostess

## Настройка

### Необходимые аккаунты и ключи

| Сервис | Что нужно |
|--------|-----------|
| Google Cloud | Project с Drive API, Cloud Functions, Cloud Scheduler |
| OpenAI | API key (для Whisper STT) |
| Anthropic | API key (для Claude — анализ) |
| Telegram | Bot token (@BotFather) + Chat ID |

### Backend

```bash
cd backend

# Переменные окружения
export GOOGLE_DRIVE_FOLDER_ID="..."
export OPENAI_API_KEY="..."
export ANTHROPIC_API_KEY="..."
export TELEGRAM_BOT_TOKEN="..."
export TELEGRAM_CHAT_ID="..."

# Локальный тест
pip install -r requirements.txt
python main.py test_recording.mp3

# Деплой в Google Cloud
export GCP_PROJECT_ID="your-project"
./deploy.sh
```

### Android-приложение

1. Создайте service account в Google Cloud Console с доступом к Drive
2. Скачайте JSON-ключ и поместите в `android-app/app/src/main/assets/service_account.json`
3. Откройте `android-app/` в Android Studio
4. Build → Generate Signed APK
5. Установите APK на Redmi Note 13 Pro
6. В настройках приложения укажите Google Drive Folder ID
7. В настройках MIUI включите автозапись звонков

### Настройка телефона (Redmi Note 13 Pro)

1. **Авто-запись MIUI**: Настройки → Приложения → Записи звонков → Включить автозапись
2. **Разрешения приложению**: выдать все запрошенные разрешения (телефон, хранилище, наложение поверх окон)
3. **Автозапуск**: Настройки → Приложения → Управление → Куранты Мониторинг → Автозапуск: вкл

## Проверка работоспособности

1. Позвонить на рабочий телефон
2. Overlay с напоминанием должен появиться
3. После звонка MIUI сохраняет запись в `/MIUI/sound_recorder/call_rec/`
4. Приложение автоматически загружает файл на Google Drive
5. Cloud Function (каждые 5 мин) обрабатывает запись
6. Отчёт приходит в Telegram
