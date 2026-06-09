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
)
