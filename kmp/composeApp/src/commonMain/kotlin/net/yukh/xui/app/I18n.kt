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
)
