package net.yukh.xui.i18n

/**
 * Russian translations. Key = the English source string passed to tr().
 * Missing keys fall back to English, so this can grow incrementally.
 */
val ruStrings: Map<String, String> = mapOf(
    // Tabs & top-level menu
    "Dashboard" to "Дашборд",
    "Inbounds" to "Входящие",
    "Clients" to "Клиенты",
    "Nodes" to "Узлы",
    "Menu" to "Меню",
    "Xray config" to "Конфиг Xray",
    "Disconnect" to "Отключиться",
    "Settings" to "Настройки",

    // Common actions
    "Save" to "Сохранить",
    "Cancel" to "Отмена",
    "Delete" to "Удалить",
    "Close" to "Закрыть",
    "Create" to "Создать",
    "Edit" to "Изменить",
    "Add" to "Добавить",
    "OK" to "OK",
    "Update" to "Обновить",
    "Restart" to "Перезапустить",
    "Never" to "Никогда",
    "Pick date" to "Выбрать дату",

    // Connect screen
    "Connect to panel" to "Подключение к панели",
    "Panel URL" to "URL панели",
    "Include the webBasePath if your admin set one" to "Укажите webBasePath, если он задан админом",
    "API token" to "API-токен",
    "Login & password" to "Логин и пароль",
    "Username" to "Имя пользователя",
    "Password" to "Пароль",
    "2FA code" to "Код 2FA",
    "Leave empty if 2FA is disabled" to "Оставьте пустым, если 2FA выключена",
    "Only required if 2FA is enabled on your account." to "Нужен только если на аккаунте включена 2FA.",
    "Allow self-signed TLS" to "Разрешить самоподписанный TLS",
    "Disable certificate verification — only enable for your own panel." to
        "Отключает проверку сертификата — включайте только для своей панели.",
    "Subscription base URL (optional)" to "Базовый URL подписки (опционально)",
    "Connect" to "Подключиться",
    "Sign in" to "Войти",
    "Signing in…" to "Вход…",
    "Create one in Settings → Security → API Token on the panel." to
        "Создайте его в панели: Settings → Security → API Token.",
    "For subscription links/QR with an API token, enter your reverse-proxy URI " +
        "(if you use one) or the panel's Subscription URL, e.g. https://host:2096/sub/. " +
        "Leave empty with login/password." to
        "Для ссылок/QR подписки в режиме API-токена укажите URI обратного прокси " +
            "(если используется) или Subscription URL панели, напр. https://host:2096/sub/. " +
            "С логином/паролем оставьте пустым.",

    // Dashboard
    "Xray" to "Xray",
    "Running" to "Работает",
    "Stopped" to "Остановлен",
    "Status unknown" to "Статус неизвестен",
    "CPU" to "CPU",
    "Memory" to "Память",
    "Disk" to "Диск",
    "Online (tap)" to "Онлайн (нажми)",
    "Net ↑ / ↓ per s" to "Сеть ↑ / ↓ в сек",
    "Connections" to "Соединения",
    "Refreshing…" to "Обновление…",
    "Waiting for first response…" to "Ожидание первого ответа…",
    "Restart Xray?" to "Перезапустить Xray?",
    "This briefly drops every active client connection." to
        "Это кратко разорвёт все активные подключения клиентов.",
    "Nobody connected right now." to "Сейчас никто не подключён.",
    "3x-ui panel" to "Панель 3x-ui",
    "Up to date" to "Актуальная версия",
    "Update 3x-ui?" to "Обновить 3x-ui?",
    "version unknown" to "версия неизвестна",
    "Updating…" to "Обновление…",
    "Restarting…" to "Перезапуск…",

    // Clients
    "Search by email" to "Поиск по email",
    "No clients yet." to "Пока нет клиентов.",
    "Subscription" to "Подписка",
    "Auto-updating link for all configs" to "Авто-обновляемая ссылка на все конфиги",
    "Individual server links" to "Отдельные ссылки на серверы",
    "New client" to "Новый клиент",
    "Edit client" to "Изменить клиента",
    "Delete client?" to "Удалить клиента?",

    // Editors — common
    "Remark" to "Примечание",
    "Enabled" to "Включён",
    "Port" to "Порт",
    "Protocol" to "Протокол",
    "Traffic limit (GB, 0 = unlimited)" to "Лимит трафика (ГБ, 0 = безлимит)",
    "Traffic reset" to "Сброс трафика",
    "Expiry" to "Срок действия",
    "Transport" to "Транспорт",
    "Network" to "Сеть",
    "Security" to "Безопасность",
    "Sniffing" to "Sniffing",
    "New inbound" to "Новый входящий",
    "Edit inbound" to "Изменить входящий",
    "Delete inbound" to "Удалить входящий",
    "Delete inbound?" to "Удалить входящий?",
    "Add node" to "Добавить узел",
    "Edit node" to "Изменить узел",
    "Delete node" to "Удалить узел",
    "Delete node?" to "Удалить узел?",
    "Name" to "Имя",
    "Address (IP or domain)" to "Адрес (IP или домен)",
    "Base path" to "Базовый путь",
    "Save changes?" to "Сохранить изменения?",

    // Settings / About
    "Language" to "Язык",
    "English" to "English",
    "Русский" to "Русский",
    "About" to "О приложении",
    "Version" to "Версия",
    "Application" to "Приложение",
)
