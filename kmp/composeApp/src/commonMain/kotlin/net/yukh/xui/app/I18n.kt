package net.yukh.xui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

/**
 * Tiny i18n for the shared Compose-MP UI, mirroring the Android app's approach:
 * `tr("English source")` looks the string up in [ruStrings] when the current
 * language is Russian, else returns the English source. The English text IS the
 * key, so partial coverage degrades gracefully. The words `inbound`/`outbound`
 * are intentionally NOT translated in Russian.
 */
const val LANG_EN = "en"
const val LANG_RU = "ru"

val LocalAppLanguage = compositionLocalOf { LANG_EN }

@Composable
@ReadOnlyComposable
fun tr(en: String): String =
    if (LocalAppLanguage.current == LANG_RU) ruStrings[en] ?: en else en

/** Non-composable variant for the rare call site outside composition. */
fun tr(lang: String, en: String): String =
    if (lang == LANG_RU) ruStrings[en] ?: en else en

val ruStrings: Map<String, String> = mapOf(
    // Tabs (Inbounds stays untranslated by project rule)
    "Dashboard" to "Дашборд",
    "Clients" to "Клиенты",
    "Nodes" to "Узлы",
    "More" to "Ещё",

    // Connect screen
    "3X-UI Manager" to "3X-UI Manager",
    "Connect to your panel" to "Подключение к панели",
    "Panel URL" to "URL панели",
    "API token" to "API-токен",
    "Connect" to "Подключиться",
    "Connecting…" to "Подключение…",
    "Allow self-signed TLS" to "Разрешить самоподписанный TLS",
    "Login failed — check URL / token" to "Не удалось войти — проверьте URL / токен",
    "Your API token is no longer valid. Reconnect with a working one." to
        "API-токен больше не действителен. Переподключитесь с рабочим.",

    // Dashboard
    "Refresh" to "Обновить",
    "Disconnect" to "Отключиться",
    "Running" to "Работает",
    "Stopped" to "Остановлен",
    "Status unknown" to "Статус неизвестен",
    "Loading…" to "Загрузка…",
    "Xray" to "Xray",
    "CPU" to "CPU",
    "Memory" to "Память",
    "Disk" to "Диск",
    "Load 1·5·15m" to "Нагрузка 1·5·15м",
    "Net ↑ / ↓ per s" to "Сеть ↑ / ↓ в сек",
    "Connections" to "Соединения",
    "Uptime" to "Аптайм",
    "Panel" to "Панель",
    "cores" to "ядер",
    "Online" to "Онлайн",
    "Online by server" to "Онлайн по серверам",
    "Main server" to "Основной сервер",
    "Online clients" to "Онлайн-клиенты",
    "Nobody online right now." to "Сейчас никто не в сети.",
    "Traffic this month" to "Трафик за месяц",
    "since" to "с",
    "not all inbounds reset monthly" to "не все inbounds сбрасываются ежемесячно",
    "Geo databases" to "Гео-базы",
    "Update" to "Обновить",
    "Update geo database?" to "Обновить гео-базу?",
    "Downloads the latest database and restarts Xray, briefly dropping every active connection." to
        "Скачает свежую базу и перезапустит Xray, кратко разорвав все активные подключения.",

    // Lists
    "No inbounds yet." to "Пока нет inbound'ов.",
    "No clients yet." to "Пока нет клиентов.",
    "No nodes yet." to "Пока нет узлов.",
    "online" to "онлайн",
    "offline" to "офлайн",
    "clients" to "клиентов",
    "inbounds" to "inbounds",

    // Settings / About
    "Settings" to "Настройки",
    "Language" to "Язык",
    "English" to "English",
    "Русский" to "Русский",
    "About" to "О приложении",
    "Application" to "Приложение",
    "Version" to "Версия",
    "Panel URL:" to "URL панели:",

    // Common actions + editors
    "Cancel" to "Отмена",
    "Save" to "Сохранить",
    "Add" to "Добавить",
    "Add node" to "Добавить узел",
    "Edit node" to "Изменить узел",
    "Delete node" to "Удалить узел",
    "Name" to "Имя",
    "Remark (optional)" to "Примечание (необязательно)",
    "Scheme" to "Схема",
    "Address (IP or domain)" to "Адрес (IP или домен)",
    "Port" to "Порт",
    "Base path" to "Базовый путь",
    "Enabled" to "Включён",
    "Verify TLS certificate" to "Проверять TLS-сертификат",
    "Allow private address" to "Разрешить приватные адреса",
    "Edit inbound" to "Изменить inbound",
    "New inbound" to "Новый inbound",
    "Delete inbound" to "Удалить inbound",
    "Protocol" to "Протокол",
    "Traffic reset" to "Сброс трафика",
    "Protocol settings (JSON)" to "Настройки протокола (JSON)",
    "Transport / security (JSON)" to "Транспорт / безопасность (JSON)",
    "Sniffing (JSON)" to "Sniffing (JSON)",
    "Transport" to "Транспорт",
    "Network" to "Сеть",
    "Path" to "Путь",
    "Host" to "Host",
    "Service name" to "Имя сервиса",
    "Security" to "Безопасность",
    "SNI (server name)" to "SNI (имя сервера)",
    "Dest (target)" to "Dest (цель)",
    "Server names (comma-separated)" to "Имена серверов (через запятую)",
    "Short IDs (comma-separated)" to "Short ID (через запятую)",
    "Fingerprint" to "Отпечаток (fingerprint)",
    "Public key" to "Публичный ключ",
    "Private key" to "Приватный ключ",
    "Sniffing" to "Sniffing",
    "One of the JSON blocks is invalid." to "Один из JSON-блоков некорректен.",
    "Remark" to "Примечание",
    "Listen IP (blank = all)" to "Listen IP (пусто = все)",
    "Traffic limit (GB, 0 = unlimited)" to "Лимит трафика (ГБ, 0 = безлимит)",
    "Expiry is set — edit it in the full editor (coming soon)." to
        "Срок задан — изменить можно будет в полном редакторе (скоро).",
    "Protocol, transport and security are edited in the full editor (coming soon)." to
        "Протокол, транспорт и безопасность редактируются в полном редакторе (скоро).",
    "Couldn't load inbound" to "Не удалось загрузить inbound",

    // App lock
    "App lock" to "Блокировка приложения",
    "Set passcode" to "Задать код-пароль",
    "Change passcode" to "Сменить код-пароль",
    "Remove passcode" to "Убрать код-пароль",
    "Unlock with biometrics" to "Разблокировка по биометрии",
    "Set a 4–8 digit passcode" to "Задайте код-пароль из 4–8 цифр",
    "Passcode" to "Код-пароль",
    "Confirm passcode" to "Повторите код-пароль",
    "Enter passcode" to "Введите код-пароль",
    "Wrong passcode" to "Неверный код-пароль",
    "Unlock" to "Разблокировать",
    "Use biometrics" to "Использовать биометрию",

    // Client editor
    "Edit client" to "Изменить клиента",
    "New client" to "Новый клиент",
    "Attach to inbounds" to "Привязать к inbound'ам",
    "Delete client" to "Удалить клиента",
    "Email / name" to "Email / имя",
    "IP limit (0 = unlimited)" to "Лимит IP (0 = безлимит)",
    "Traffic reset period (days, 0 = off)" to "Период сброса трафика (дней, 0 = выкл)",
    "Telegram ID (optional)" to "Telegram ID (необязательно)",
    "Group (optional)" to "Группа (необязательно)",
    "Comment (optional)" to "Комментарий (необязательно)",
    "Show connection links" to "Показать ссылки-подключения",

    // Xray config
    "Xray config" to "Конфиг Xray",
    "Outbound test URL" to "URL для теста outbound",
    "xray config (JSON)" to "конфиг xray (JSON)",
    "Invalid JSON." to "Некорректный JSON.",
    "Save, then restart Xray to apply. A broken config can take Xray down." to
        "Сохраните и перезапустите Xray, чтобы применить. Сломанный конфиг может уронить Xray.",

    // Panel admin
    "Back" to "Назад",
    "Panel admin" to "Администрирование панели",
    "Admin account" to "Учётная запись админа",
    "Change the panel login. Enter the current credentials to confirm." to
        "Сменить логин панели. Введите текущие данные для подтверждения.",
    "Current username" to "Текущий логин",
    "Current password" to "Текущий пароль",
    "New username" to "Новый логин",
    "New password" to "Новый пароль",
    "Change credentials" to "Сменить учётные данные",
    "Change credentials?" to "Сменить учётные данные?",
    "The panel login changes immediately. Your API token keeps working." to
        "Логин панели сменится сразу. Ваш API-токен продолжит работать.",
    "Change" to "Сменить",
    "API tokens" to "API-токены",
    "No API tokens yet." to "Пока нет API-токенов.",
    "Create token" to "Создать токен",
    "Token name" to "Имя токена",
    "Token created" to "Токен создан",
    "Copy it now — it's shown only once." to "Скопируйте сейчас — показывается один раз.",
    "Copy & close" to "Скопировать и закрыть",
    "Delete token?" to "Удалить токен?",
    "Apps using this token will stop working. This can't be undone." to
        "Приложения с этим токеном перестанут работать. Отменить нельзя.",
    "Delete" to "Удалить",
    "Create" to "Создать",
    "Panel" to "Панель",
    "Restart panel" to "Перезапустить панель",
    "Restart panel?" to "Перезапустить панель?",
    "The panel restarts and the app reconnects in a few seconds." to
        "Панель перезапустится, приложение переподключится через несколько секунд.",
    "Restart" to "Перезапустить",
    "Credentials updated" to "Учётные данные обновлены",
    "Couldn't change credentials" to "Не удалось сменить учётные данные",
    "Couldn't create token" to "Не удалось создать токен",
    "Panel is restarting…" to "Панель перезапускается…",
    "Couldn't restart the panel" to "Не удалось перезапустить панель",

    // Xray structured sections (General / DNS / Routing)
    "General / Logs" to "Общее / Логи",
    "General" to "Общее",
    "Routing strategy" to "Стратегия маршрутизации",
    "Logs" to "Логи",
    "Log level" to "Уровень логов",
    "Access log (path, empty = off)" to "Access-лог (путь, пусто = выкл)",
    "Error log (path, empty = off)" to "Error-лог (путь, пусто = выкл)",
    "Mask address" to "Маскировать адрес",
    "DNS log" to "DNS-лог",
    "Statistics" to "Статистика",
    "Inbound uplink stats" to "Статистика inbound ↑",
    "Inbound downlink stats" to "Статистика inbound ↓",
    "Outbound uplink stats" to "Статистика outbound ↑",
    "Outbound downlink stats" to "Статистика outbound ↓",

    // DNS editor
    "DNS" to "DNS",
    "Enable DNS" to "Включить DNS",
    "Tag" to "Тег",
    "Client IP" to "Client IP",
    "Query strategy" to "Стратегия запросов",
    "Disable cache" to "Отключить кэш",
    "Disable fallback" to "Отключить fallback",
    "Disable fallback if match" to "Отключить fallback при совпадении",
    "Use system hosts" to "Системные hosts",
    "DNS servers" to "DNS-серверы",
    "Add DNS server" to "Добавить DNS-сервер",
    "DNS server" to "DNS-сервер",
    "Address" to "Адрес",
    "Port (blank = 53)" to "Порт (пусто = 53)",
    "Domains (comma-separated)" to "Домены (через запятую)",
    "Expected IPs (comma-separated)" to "Ожидаемые IP (через запятую)",
    "Skip fallback" to "Пропускать fallback",
    "Edit" to "Изменить",

    // Routing editor
    "Routing" to "Маршрутизация",
    "Routing rules" to "Правила маршрутизации",
    "Add rule" to "Добавить правило",
    "Routing rule" to "Правило маршрутизации",
    "Balancers" to "Балансировщики",
    "Add balancer" to "Добавить балансировщик",
    "Balancer" to "Балансировщик",
    "Target (one of)" to "Цель (одно из)",
    "Outbound tag" to "Тег outbound",
    "Balancer tag" to "Тег балансировщика",
    "Match" to "Условия",
    "Inbound tags" to "Теги inbound",
    "Domains" to "Домены",
    "IPs" to "IP",
    "Source IPs" to "Source IP",
    "Users" to "Пользователи",
    "Protocols" to "Протоколы",
    "(comma-separated)" to "(через запятую)",
    "Port" to "Порт",
    "Source port" to "Source-порт",
    "Network" to "Сеть",
    "Strategy" to "Стратегия",
    "Selector (comma-separated)" to "Selector (через запятую)",
    "Fallback tag" to "Fallback-тег",

    // Outbounds editor
    "Outbounds" to "Outbounds",
    "Add outbound" to "Добавить outbound",
    "Import from vless:// link" to "Импорт из vless://-ссылки",
    "Import" to "Импортировать",
    "Outbound" to "Outbound",
    "Protocol" to "Протокол",
    "Edit raw JSON" to "Править сырой JSON",
    "Outbound (JSON)" to "Outbound (JSON)",
    "ID (UUID)" to "ID (UUID)",
    "Flow" to "Flow",
    "Password" to "Пароль",
    "Method" to "Метод",
    "Domain strategy" to "Стратегия доменов",
    "Response type" to "Тип ответа",
    "Transport / security" to "Транспорт / безопасность",
    "Service name" to "Имя сервиса",
    "SNI (server name)" to "SNI (имя сервера)",
    "Public key" to "Публичный ключ",
    "Short ID" to "Short ID",
    "Host" to "Host",
    "Path" to "Путь",

    // Metric history chart
    "history" to "история",
    "No history yet" to "Истории пока нет",
    "Real-time" to "Реальное время",
    "30 min" to "30 мин",
    "1 hour" to "1 час",
    "2 hours" to "2 часа",
    "3 hours" to "3 часа",
    "5 hours" to "5 часов",
    "Load" to "Нагрузка",
    "Network" to "Сеть",
)
