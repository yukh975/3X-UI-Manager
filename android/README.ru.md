# 3X-UI Manager — приложение для Android

Нативный Android-клиент для управления панелью [3x-ui](https://github.com/MHSanaei/3x-ui) через её REST API.

🇬🇧 [English version](README.md) · 📝 [Список изменений](CHANGELOG.ru.md)

## Возможности

- **Dashboard (Панель)** — состояние сервера в реальном времени, опрос каждые 3 с: статус Xray (работает/остановлен, перезапуск в один тап), CPU, RAM, диск, онлайн-клиенты, сетевой трафик, число соединений, средняя нагрузка, аптайм, версии панели и Xray.
- **Inbounds (Входящие)** — список с переключателем вкл/выкл по строке (оптимистично), трафик относительно лимита, число клиентов. Полный редактор: имя, порт, listen, протокол, лимит трафика, расписание сброса, срок действия, плюс сырой JSON `settings` / `streamSettings` / `sniffing` для любого транспорта/TLS/Reality. Создание и удаление.
- **Clients (Клиенты)** — список с индикатором онлайна, трафиком, сроком, «последний онлайн». Шит-меню по клиенту с QR и ссылкой **подписки**, а также QR и ссылкой каждого отдельного **соединения** (копировать / поделиться). Полный редактор: email, привязка к inbound'ам, лимит трафика, лимит IP, сброс, срок, Telegram ID, группа, комментарий. Создание и удаление.
- **Nodes (Узлы)** — управление удалёнными панелями (мульти-панель): статус онлайн, CPU/RAM/задержка, число inbound'ов и клиентов. Добавление / изменение / удаление: имя, адрес, порт, схема, базовый путь, API-токен, режим проверки TLS.
- **Xray config** — редактирование полного конфига Xray (outbounds, routing, DNS) как JSON, аналогично странице Xray Configuration в панели.

## Аутентификация

Два режима, выбираются на экране подключения:

| Режим | Что работает |
|------|-----------|
| **API-токен** (Bearer) | Dashboard, Inbounds, Clients, Nodes — всё под `/panel/api/*` |
| **Логин + пароль** (сессия, опц. 2FA) | Всё перечисленное **плюс** ссылки-подписки и редактор Xray-конфига |

3x-ui отдаёт настройки панели и конфиг Xray только авторизованной сессии, но не API-токену — поэтому ссылка-подписка и редактирование outbounds/routing требуют логина с паролем. API-токен создаётся в панели: *Settings → Security → API Token*.

Самоподписанный TLS поддерживается отдельным переключателем для подключения. Данные хранятся в `EncryptedSharedPreferences` (AES-256, ключ в Android Keystore).

## Стек

- Kotlin 2.1 + Jetpack Compose (Material 3)
- Min SDK 24 (Android 7.0), target/compile SDK 35 (Android 15)
- Hilt (DI), Retrofit + OkHttp + kotlinx.serialization (сеть), zxing (QR-коды)

## Структура проекта

```
android/
├── app/src/main/java/net/yukh/xui/
│   ├── data/
│   │   ├── api/        # Retrofit-интерфейс, обёртка ответа, DTO
│   │   ├── auth/       # Bearer / cookie / CSRF интерсепторы, TLS
│   │   ├── prefs/      # хранилище подключения (EncryptedSharedPreferences)
│   │   └── repo/       # PanelRepository — единый источник правды
│   ├── di/             # Hilt-модули
│   ├── ui/
│   │   ├── screen/     # connect, dashboard, inbounds, clients, nodes, xray, main
│   │   ├── format/     # форматтеры байт/дат/аптайма
│   │   ├── qr/         # генератор QR
│   │   └── navigation/ # AppNav + маршруты нижней навигации
│   ├── MainActivity.kt
│   └── XuiApp.kt
├── build.gradle.kts · settings.gradle.kts · gradle/libs.versions.toml
```

## Сборка

Откройте папку `android/` в Android Studio (Hedgehog или новее) — она сама синхронизирует и скачает всё необходимое, включая Gradle wrapper.

CLI:

```bash
cd android
gradle wrapper --gradle-version 8.10.2   # один раз, создаёт ./gradlew
./gradlew assembleDebug                   # debug APK
./gradlew assembleRelease                 # подписанный release APK (нужен keystore)
./gradlew testDebugUnitTest               # юнит-тесты
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## CI / релизы

[`.gitlab-ci.yml`](../.gitlab-ci.yml) (корень репозитория) собирает **только** по тегу версии (`vX.Y.Z`) или ручному запуску — пуши в ветку сборку не запускают. Пайплайн по тегу собирает debug + подписанный release APK, прогоняет тесты, загружает оба APK в Generic Package Registry проекта и автоматически создаёт GitLab Release со ссылками. `versionName`/`versionCode` берутся из тега, поэтому версия меняется только в момент релиза.

Чтобы выпустить релиз: запушьте тег `vX.Y.Z`.
