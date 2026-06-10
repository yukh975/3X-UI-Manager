package net.yukh.xui.i18n

/**
 * Russian translations. Key = the English source string passed to tr().
 * Missing keys fall back to English, so this can grow incrementally.
 */
val ruStrings: Map<String, String> = mapOf(
    // Tabs & top-level menu
    "Dashboard" to "Дашборд",
    "Inbounds" to "Inbounds",
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
    "Start" to "Запустить",
    "Stop" to "Остановить",
    "Never" to "Никогда",
    "Pick date" to "Выбрать дату",

    // Connect screen
    "Connect to panel" to "Подключение к панели",
    "Panel URL" to "URL панели",
    "Include the webBasePath if your admin set one" to "Укажите webBasePath, если он задан админом",
    "API token" to "API-токен",
    "API Token" to "API-токен",
    "Login" to "Логин",
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
    "Online" to "Онлайн",
    "Net ↑ / ↓ per s" to "Сеть ↑ / ↓ в сек",
    "Connections" to "Соединения",
    "Refreshing…" to "Обновление…",
    "Waiting for first response…" to "Ожидание первого ответа…",
    "Restart Xray?" to "Перезапустить Xray?",
    "This briefly drops every active client connection." to
        "Это кратко разорвёт все активные подключения клиентов.",
    "Stop Xray?" to "Остановить Xray?",
    "This disconnects every active client until you start Xray again." to
        "Это отключит всех активных клиентов, пока вы снова не запустите Xray.",
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
    "New inbound" to "Новый inbound",
    "Edit inbound" to "Изменить inbound",
    "Delete inbound" to "Удалить inbound",
    "Delete inbound?" to "Удалить inbound?",
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

    // App lock
    "App lock" to "Блокировка приложения",
    "Set passcode" to "Задать код-пароль",
    "Change passcode" to "Сменить код-пароль",
    "Remove passcode" to "Убрать код-пароль",
    "Unlock with fingerprint" to "Разблокировка по отпечатку",
    "Set a 4–8 digit passcode" to "Задайте код-пароль из 4–8 цифр",
    "Passcode" to "Код-пароль",
    "Confirm passcode" to "Повторите код-пароль",
    "Enter passcode" to "Введите код-пароль",
    "Wrong passcode" to "Неверный код-пароль",
    "Unlock" to "Разблокировать",
    "Use passcode" to "Ввести код-пароль",
    "Use fingerprint" to "Использовать отпечаток",

    // Dashboard (additional)
    "Load 1·5·15m" to "Нагрузка 1·5·15м",
    "cores" to "ядер",
    "Uptime" to "Аптайм",
    "IP" to "IP",
    "Online clients" to "Онлайн-клиенты",
    "Online by server" to "Онлайн по серверам",
    "Main server" to "Основной сервер",
    // Metric history chart
    "Load" to "Нагрузка",
    "Network" to "Сеть",
    "history" to "история",
    "Interval" to "Период",
    "No history yet" to "Данных истории пока нет",
    "Real-time" to "Реальное время",
    "30 min" to "30 мин",
    "1 hour" to "1 час",
    "2 hours" to "2 часа",
    "3 hours" to "3 часа",
    "5 hours" to "5 часов",
    "Xray uptime" to "Аптайм Xray",
    "Update available:" to "Доступно обновление:",
    "Traffic this month" to "Трафик за месяц",
    "since" to "с",
    "not all inbounds reset monthly" to "не все inbounds сбрасываются ежемесячно",
    "Geo databases" to "Гео-базы",
    "Update all" to "Обновить все",
    // Outbounds editor (the word "outbound" is left untranslated in RU)
    "Add outbound" to "Добавить outbound",
    "New outbound" to "Новый outbound",
    "Edit outbound" to "Изменить outbound",
    "Delete outbound?" to "Удалить outbound?",
    "This outbound" to "Этот outbound",
    "Done" to "Готово",
    "Tag" to "Тег",
    "Address" to "Адрес",
    "Server" to "Сервер",
    "Domain strategy" to "Стратегия доменов",
    "Response type" to "Тип ответа",
    "Username (optional)" to "Имя пользователя (необязательно)",
    "Password (optional)" to "Пароль (необязательно)",
    "default route" to "маршрут по умолчанию",
    "Move up" to "Вверх",
    "Move down" to "Вниз",
    "Settings (raw JSON)" to "Настройки (сырой JSON)",
    "A structured form for this protocol is coming; edit the raw JSON for now." to
        "Структурная форма для этого протокола скоро появится; пока редактируйте сырой JSON.",
    "Invalid JSON" to "Некорректный JSON",
    // Outbound protocol forms + stream settings + link import
    "Transport" to "Транспорт",
    "Security" to "Безопасность",
    "Path" to "Путь",
    "Mode" to "Режим",
    "Service name" to "Имя сервиса",
    "Header type" to "Тип заголовка",
    "Fingerprint" to "Отпечаток",
    "ALPN (comma-separated)" to "ALPN (через запятую)",
    "Allow insecure" to "Разрешить небезопасный TLS",
    "Public key" to "Публичный ключ",
    "Encryption" to "Шифрование",
    "Password" to "Пароль",
    "Method" to "Метод",
    "UDP over TCP (uot)" to "UDP поверх TCP (uot)",
    "Interface" to "Интерфейс",
    "Secret key" to "Секретный ключ",
    "Address (CIDR, comma-separated)" to "Адрес (CIDR, через запятую)",
    "Peer" to "Пир",
    "Allowed IPs (comma-separated)" to "Разрешённые IP (через запятую)",
    "Pre-shared key (optional)" to "Pre-shared key (необязательно)",
    "Import from link" to "Импорт из ссылки",
    "Import from vless:// link" to "Импорт из vless:// ссылки",
    "Paste vless:// link" to "Вставьте vless:// ссылку",
    "Import" to "Импортировать",
    "Not a valid vless:// link" to "Это не похоже на vless:// ссылку",
    // Outbound web-parity fields
    "Send through" to "Отправлять через",
    "Strategy" to "Стратегия",
    "Redirect" to "Перенаправление",
    "Fragment" to "Фрагментация",
    "Packets" to "Пакеты",
    "Length" to "Длина",
    "Max Split" to "Макс. сплит",
    "Transmission" to "Передача",
    "HTTP camouflage" to "HTTP-маскировка",
    "Multi mode" to "Мульти-режим",
    "Verify peer name" to "Проверка имени пира",
    "Concurrency" to "Параллелизм",
    "Workers" to "Воркеры",
    "Reserved" to "Зарезервировано",
    "UoT version" to "Версия UoT",
    "Keep alive" to "Keep-alive",
    "Update geo database?" to "Обновить гео-базу?",
    "Update all geo databases?" to "Обновить все гео-базы?",
    "Downloads the latest database and restarts Xray, " +
        "briefly dropping every active connection." to
        "Скачает свежую базу и перезапустит Xray, кратко разорвав все активные подключения.",
    "Downloads the latest of every built-in geo database and restarts " +
        "Xray, briefly dropping every active connection." to
        "Скачает свежие версии всех встроенных гео-баз и перезапустит Xray, " +
        "кратко разорвав все активные подключения.",

    // Clients list & rows
    "Add client" to "Добавить клиента",
    "Clear" to "Очистить",
    "No clients match" to "Нет клиентов по запросу",
    "(no email)" to "(без email)",
    "disabled" to "выключен",
    "of" to "из",
    "Expires" to "Истекает",
    "Last seen" to "Был онлайн",

    // Client editor (additional)
    "Email / name" to "Email / имя",
    "IP limit (0 = unlimited)" to "Лимит IP (0 = безлимит)",
    "Traffic reset period (days, 0 = off)" to "Период сброса трафика (дней, 0 = выкл)",
    "Telegram ID (optional)" to "Telegram ID (необязательно)",
    "Group (optional)" to "Группа (необязательно)",
    "Comment (optional)" to "Комментарий (необязательно)",
    "Create client?" to "Создать клиента?",
    "Create client" to "Создать клиента",
    "Apply changes to" to "Применить изменения к",

    // Inbounds list & rows
    "No inbounds yet." to "Пока нет inbound'ов.",
    "Add inbound" to "Добавить inbound",
    "of " to "из ",
    "Expires " to "Истекает ",

    // Inbound editor (additional)
    "Listen IP (blank = all)" to "Listen IP (пусто = все)",
    "Path" to "Путь",
    "Host" to "Host",
    "Service name" to "Service name",
    "SNI (server name)" to "SNI (имя сервера)",
    "Dest (target)" to "Dest (назначение)",
    "Server names (comma-separated)" to "Server names (через запятую)",
    "Short IDs (comma-separated)" to "Short IDs (через запятую)",
    "Fingerprint" to "Отпечаток (fingerprint)",
    "Public key" to "Публичный ключ",
    "Private key" to "Приватный ключ",
    "Advanced: protocol settings (JSON)" to "Расширенно: настройки протокола (JSON)",
    "Hide" to "Скрыть",
    "Show" to "Показать",
    "Clients are managed on the Clients tab and are kept as-is." to
        "Клиентами управляет вкладка Clients, здесь они не затрагиваются.",
    "Create inbound?" to "Создать inbound?",
    "Apply changes to this inbound? Xray will restart." to
        "Применить изменения к этому inbound? Xray перезапустится.",
    "This removes the inbound and all its clients. This can't be undone." to
        "Это удалит inbound и всех его клиентов. Отменить нельзя.",

    // Nodes list & rows
    "No nodes. Tap + to add a remote panel." to
        "Нет узлов. Нажмите +, чтобы добавить удалённую панель.",
    "Add node?" to "Добавить узел?",
    "online" to "онлайн",
    "offline" to "офлайн",
    "Ping" to "Пинг",
    "CPU " to "CPU ",
    "RAM " to "RAM ",
    "up " to "аптайм ",

    // Node editor (additional)
    "Scheme" to "Схема",
    "Remark (optional)" to "Примечание (необязательно)",
    "From the node panel: Settings → Security → API Token" to
        "В панели узла: Settings → Security → API Token",
    "TLS verify" to "Проверка TLS",
    "Allow private address" to "Разрешить приватные адреса",
    "Permit RFC1918 / LAN addresses" to "Разрешить адреса RFC1918 / LAN",
    "The remote panel itself is untouched; it's just removed from this list." to
        "Сама удалённая панель не трогается — она лишь убирается из этого списка.",

    // Xray config screen (paragraphs are split across tr() calls; the Russian
    // fragments concatenate into a coherent sentence, keep the trailing spaces)
    "Xray configuration isn't available with an API token." to
        "Конфигурация Xray недоступна с API-токеном.",
    "The panel only exposes the Xray config (outbounds, routing, " to
        "Панель отдаёт конфиг Xray (outbounds, routing, ",
    "DNS) to a logged-in session. Reconnect with login/password " to
        "DNS) только авторизованной сессии. Переподключитесь по логину/паролю, ",
    "to edit it." to "чтобы редактировать его.",
    "Full Xray config — outbounds, routing, DNS, etc. Same as the " to
        "Полный конфиг Xray — outbounds, routing, DNS и т.д. То же, что ",
    "panel's Xray Configuration page. Save, then restart Xray to apply." to
        "страница Xray Configuration в панели. Сохраните и перезапустите Xray.",
    "Outbound test URL" to "URL для теста outbound",
    "xray config (JSON)" to "конфиг xray (JSON)",
    "Save Xray config?" to "Сохранить конфиг Xray?",

    // Client share sheet
    "No connection links for this client." to "Нет ссылок-соединений у этого клиента.",
    "will be removed from every attached inbound." to
        "будет удалён из всех привязанных inbound'ов.",
    "No subscription URL. With an API token, set the " +
        "\"Subscription base URL\" on the connect screen " +
        "(e.g. https://host:2096/sub/) — or connect with " +
        "login/password so the app can read it automatically." to
        "Нет ссылки подписки. С API-токеном задайте «Базовый URL подписки» на " +
            "экране подключения (напр. https://host:2096/sub/) — или войдите по " +
            "логину/паролю, чтобы приложение прочитало её автоматически.",
    "Collapse" to "Свернуть",
    "Expand" to "Развернуть",
    "Copy" to "Копировать",
    "Share" to "Поделиться",
    "QR code" to "QR-код",

    // Connect screen (icon descriptions)
    "Hide token" to "Скрыть токен",
    "Show token" to "Показать токен",
    "Hide password" to "Скрыть пароль",
    "Show password" to "Показать пароль",
    "This replaces the panel's Xray configuration. Restart Xray " to
        "Это заменит конфигурацию Xray в панели. Перезапустите Xray ",
    "afterwards to apply. A broken config can take Xray down." to
        "после этого, чтобы применить. Сломанный конфиг может уронить Xray.",

    // Count suffixes (lowercase, after a number)
    "clients" to "клиентов",
    "inbounds" to "inbounds",

    // Relative time & expiry words (from Formatters)
    "Expired" to "Истёк",
    "now" to "сейчас",
    "s ago" to "с назад",
    "m ago" to "м назад",
    "h ago" to "ч назад",
    "d ago" to "д назад",
)
