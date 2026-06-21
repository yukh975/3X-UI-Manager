# Как продолжить завтра на другом компьютере

Пошаговый порядок действий, чтобы поднять контекст и продолжить работу над
Android-приложением (`3X-UI Manager`) без потерь.

> Полный технический контекст — в [ANDROID-HANDOFF.md](ANDROID-HANDOFF.md).
> Правила для ассистента — в [../CLAUDE.md](../CLAUDE.md) (подхватывается сам).

---

## Текущее состояние (snapshot — 2026-06-11)

**Ветки:** `android-app` (основная) и `ios-app` (KMP: iOS + **macOS desktop** в
`kmp/`). Обе запушены, дерево чистое.

**Релизы:** последний — **v0.3.23** («первый стабильный» был v0.3.11). С v0.3.17:
- **v0.3.18** — **бэкап/восстановление** базы панели (меню ⋮): engine-agnostic
  (`GET /panel/api/server/getDb` → `x-ui.db`/`x-ui.dump` по Content-Disposition;
  `POST /panel/api/server/importDB`, multipart-поле `db`).
- **v0.3.19** — **Panel admin** (меню ⋮): смена логина/пароля админа
  (`setting/updateUser`), API-токены (список/создать/вкл-выкл/удалить),
  перезапуск панели (`setting/restartPanel`).
- **v0.3.20** — потеря авторизации (401) → возврат на экран входа с сообщением;
  запросы шлют `X-Requested-With: XMLHttpRequest`, чтобы панель отвечала 401,
  а не 404.
- **v0.3.21** — **только токен**: вход по логину/паролю/2FA полностью вырезан
  (удалены CsrfInterceptor/CsrfState/InMemoryCookieJar/LoginRequest; требуется
  панель **v3.3.0+**). Сноска на экране входа, где взять токен.
- **v0.3.22** — блокировка (код-пароль/отпечаток) не спрашивается, когда не
  залогинен (гейт `locked && connected` + арм на ON_STOP только при connected).
- **v0.3.23** — код-пароль не спрашивается сразу после ручного входа (замок
  включается только при авто-восстановлении сохранённой сессии на старте;
  Android: `LockState` смотрит и на наличие профиля в `ConnectionStore`).
- **Правило версий:** Android — источник истины; при бампе синхронизировать
  `kmp/composeApp/.../Platform.desktop.kt` (appVersionName) и
  `kmp/iosApp/iosApp/Info.plist`. Сейчас везде **0.3.23**.

**Десктоп (macOS), ветка `ios-app`:** у `kmp/composeApp` есть таргет
`jvm("desktop")` (окно 1290×880, вход `net.yukh.xui.app.MainKt`). Упаковка —
`kmp/scripts/package-macos.sh "<Apple Silicon|Intel>"`: собирает дистрибутив,
патчит реальную версию в `CFBundleShortVersionString` (jpackage не пускает
MAJOR=0, поэтому Gradle `packageVersion` навсегда 1.0.0 — это версия
инсталлятора), подписывает ad-hoc и заворачивает в DMG через **create-dmg**
(`brew install create-dmg`) с per-arch `README.txt`
(шаблон `kmp/composeApp/packaging/README.ru.template.txt`). Архитектуру выбирает
JDK: arm64 JDK → Apple Silicon; `arch -x86_64` + x64 JDK → Intel. Скрипт сам
чистит `build/compose` (там кэшируется jlink-рантайм — без чистки в
x86_64-лаунчер молча попадает arm64-JVM) и проверяет совпадение архитектур.
Иконка 3X — `kmp/composeApp/icons/` (рендер из Android adaptive-иконки),
подключена через `macOS.iconFile`. KMP-приложение полностью token-only, паритет
с Android по v0.3.23 (включая правки членства клиента в inbound'ах при edit и
обе починки блокировки).

**Рабочий цикл (ВАЖНО, актуально):** код → локальная сборка
(`./gradlew :app:assembleDebug`) → **поставить на локальный
эмулятор и проверить, что функциональность реально работает** (AVD `xui_pixel7`,
headless; adb в `/opt/homebrew/share/android-commandlinetools/platform-tools/`)
→ только после отладки тег `vX.Y.Z` по команде пользователя («собирай»). Машина
— Apple Silicon (см. memory `local-build-toolchain`); прежняя заметка про
тормозящий эмулятор относилась к старому Intel-Mac. APK:
`app/build/outputs/apk/debug/3x-ui-manager-debug.apk` (applicationId
`net.yukh.xui.debug`, ставится рядом с релизной).

**Редактор Xray (v0.3.17) — как устроено (для продолжения):**
- Все разделы правят ОДИН общий конфиг-`JsonObject` и round-trip'ят его целиком
  (соседние/незнакомые ключи сохраняются) через
  `ui/screen/xrayedit/XrayConfigIO.kt` (`repo.loadXrayConfig`/`saveXrayConfig`).
  Outbounds — отдельно в `ui/screen/outbounds/`. Иммутабельные правки —
  `data/json/JsonEdit.kt` (`put`/`child`/`string`/`array`/`putArray`…; `put(k,null)`
  удаляет ключ).
- **ГЛАВНЫЙ ГОТЧА:** 3x-ui хранит outbound-настройки ПЛОСКО
  (`settings.address/port/id/flow/encryption`), а НЕ как raw-Xray
  `vnext[]`/`servers[]`. `streamSettings` — сосед `settings`. Формы/парсер/дефолты
  — по плоской модели (иначе все поля пустые!).
- **Источник истины полей — Vue-исходник самой панели.** Фронтенд НЕ в рабочем
  дереве; достаётся из апстрим-объектов:
  `git show bc00d37a:frontend/src/pages/xray/{OutboundFormModal,RoutingTab,RuleFormModal,BalancersTab,BalancerFormModal,DnsTab,DnsServerModal,BasicsTab}.vue`
  и `frontend/src/models/outbound.js`. Спеки снимались многоагентными воркфлоу.
- **НЕ оформлено формами** (правится сырым JSON, как и местами в самой панели) —
  это TODO для полного парити, если попросят: Observatory/burstObservatory,
  базовые block/direct-хелперы вкладки Basics, продвинутый xHTTP
  (padding/session/xmux), hysteria, reverse-sniffing у vless, sockopt, правила
  DNS-сервера сверх базовых.
- Конфиг Xray по API-ТОКЕНУ на v3.3.0 **работает** (проверено:
  `POST /panel/api/xray/` с токеном → 200). Гейт «только сессия» в экранах — это
  фолбэк при ошибке (старые панели), не жёсткое ограничение на v3.3.0.

**Раньше (v0.3.9–v0.3.14):** дашборд (метрики построчно, pull-to-refresh, Xray
Старт/Стоп/Перезапуск с «закреплением» ~6 с), узлы (+кнопка «Обновить»
`POST /panel/api/nodes/updatePanel`), трафик за месяц, фикс «онлайн по серверам»
(по `nodeId` инбаунда), совместимость с панелью v3.3.0.

**iOS (ветка `ios-app`, папка `kmp/`) — полный паритет с Android v0.3.23,
работает на симуляторе:**
- `shared` — Ktor `PanelApi` token-only (XHR-заголовок, AuthExpiredException,
  метрики-история, updateUser/restartPanel/apiTokens, getDb/importDb,
  гео-апдейты, Xray start/stop/restart) + все DTO + `json/JsonEdit.kt`.
- `composeApp` — все экраны Android-версии: дашборд (Xray-кнопки, гео-аккордеон,
  графики метрик), inbounds/клиенты (членство в inbound'ах редактируется и при
  edit)/узлы, Xray-редакторы (Outbounds/Routing/DNS/General), Panel admin,
  бэкап/восстановление, блокировка (обе починки), RU/EN.
- Запуск: `cd kmp && xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator'
  -derivedDataPath /tmp/xui-ios-dd CODE_SIGNING_ALLOWED=NO build` (нужны
  JAVA_HOME+ANDROID_HOME), затем `xcrun simctl install/launch` (bundle id
  `net.yukh.xui.ios`; авто-логин через `defaults write net.yukh.xui.ios
  xui.baseUrl/xui.token`). На реальный iPhone — через Xcode с командой
  разработчика (signing — на стороне пользователя).
- Версия в `Info.plist` — 0.3.23/32300, иконка 3X в Assets.xcassets.

**Бэклог:** [TODO.md](TODO.md) — пуст: «трафик за месяц» и обновление гео-баз
сделаны в v0.3.14. Остались только отложенные/опциональные идеи (дата обновления
гео, кастомные гео, гео по узлам).

**Прочее:** заведён баг в апстрим — [MHSanaei/3x-ui#5100](https://github.com/MHSanaei/3x-ui/issues/5100)
(сброс трафика инбаунда на мастере не доходит до слейв-узлов). `gh` CLI залогинен
как `yukh975`. Тестовая панель (URL+токен) в репозитории НЕ хранится — дать
заново в начале сессии.

**Документация панели 3X-UI (RU + EN):** на отдельной ветке **`docs/manual`**
(НЕ в `android-app`!), MR **!2** → `main`. Файлы: `docs/3X-UI-MANUAL.ru.md`
(русский — эталон) и `docs/3X-UI-MANUAL.md` (английский). 16 разделов, 142
подпункта, многоуровневое оглавление; актуально для **3X-UI 3.3.0** (MTProto,
WARP-ротация, outbound из подписки, шаблоны страницы подписки, трафик по группам,
перенос API под `/panel/api`, `x-ui migrateDB`). Правило правок: сначала в RU,
затем синхронно переносим в EN. Собрано многоагентными воркфлоу по исходникам
панели + файлам переводов (`web/translation/*.json`). Баг #5100 в 3.3.0 уже
исправлен апстримом (#5103).

---

## 1. Доступ к GitLab (один раз на новой машине)
Репозиторий: `yukh/3x-ui` на `git.home.yukh.net` (SSH-порт **20222**).

1. Добавь SSH-доступ. В `~/.ssh/config`:
   ```
   Host git.home.yukh.net
     HostName git.home.yukh.net
     Port 20222
     User git
     IdentityFile ~/.ssh/gitlab.key
   ```
   и положи приватный ключ в `~/.ssh/gitlab.key` (`chmod 600`). Если ключа нет —
   создай новый и добавь его публичную часть в GitLab → Profile → SSH Keys.
2. Положи **API-токен GitLab** в файл `~/.gl-token` (без переноса строки).
   Токен берётся в GitLab → Profile → Access Tokens (scopes: `api`, `read_repository`,
   `write_repository`). Он нужен ассистенту для пайплайнов/релизов.

## 2. Клонировать и переключиться на рабочую ветку
```bash
git clone ssh://git@git.home.yukh.net:20222/yukh/3x-ui.git
cd 3x-ui
git checkout android-app
git pull
```

## 3. Поднять контекст в Claude
1. Открой папку `3x-ui` в Claude Code — файл `CLAUDE.md` подхватится автоматически.
2. Напиши ассистенту примерно так:
   > Прочитай `docs/ANDROID-HANDOFF.md` и продолжаем работу над Android-приложением.
3. Дай тестовые данные панели, если понадобится (URL + API-токен) — в репозитории
   их нет (это секрет).

## 4. Выпустить новый релиз (с любого компьютера)
Локально собирать НЕ обязательно — ключ подписи лежит в CI-переменных, CI всё
подпишет сам:
```bash
git tag -a vX.Y.Z -m "vX.Y.Z"
git push origin vX.Y.Z
```
Пайплайн соберёт подписанный `3x-ui-manager-release.apk`, зальёт в Package
Registry и создаст GitLab Release с русским описанием из `CHANGELOG.ru.md`.
Скачать: `https://git.home.yukh.net/yukh/3x-ui/-/releases`.
> Перед тегом ассистент по правилу прогоняет локальную компиляцию — для этого
> нужен п.5. Если локального окружения нет, можно тегать и собирать сразу в CI.

## 5. (Опционально) Локальная сборка/проверка UI
Нужно только если хочешь собирать/смотреть приложение локально (не через CI).
1. Установить: JDK 17 (Temurin), Android SDK (platform 35, build-tools 35.0.0,
   platform-tools), задать `JAVA_HOME`, `ANDROID_HOME`.
2. Gradle-wrapper в репозитории НЕ хранится (в `.gitignore`). Пересоздать версию
   8.10.2: в пустой временной папке с пустым `settings.gradle.kts` выполнить
   `gradle wrapper --gradle-version 8.10.2`, затем скопировать `gradlew` и
   `gradle/wrapper/gradle-wrapper.jar` в `android/`.
3. Сборка: `./gradlew :app:assembleDebug`.
4. Для визуальной проверки — эмулятор (AVD) + `adb install` + скриншоты.
   Подробности и подводные камни — в ANDROID-HANDOFF.md.

## 6. (Опционально) Локальная подпись release
Файл keystore (`~/.config/3x-ui-android-keystore/release.p12` + `release.properties`)
машинно-локальный, в репозитории его нет. Он продублирован в CI-переменной
`RELEASE_KEYSTORE_B64` (base64). Чтобы подписывать локально — восстанови его из
этой переменной или из своего бэкапа. Для релизов через CI это не требуется.

---

## 7. Секреты — что откуда скопировать (вручную, НЕ через git)

Эти файлы есть на текущем Mac, в репозитории их НЕТ. Перенеси их по защищённому
каналу (AirDrop / USB / scp / менеджер паролей).

| Что | Источник (текущий Mac) | Куда (новая машина) | Права |
|-----|------------------------|---------------------|-------|
| Токен GitLab | `~/.gl-token` | `~/.gl-token` | 600 |
| SSH-ключ | `~/.ssh/gitlab.key` | `~/.ssh/gitlab.key` | 600 |
| SSH-конфиг (блок `Host git.home.yukh.net`) | `~/.ssh/config` | `~/.ssh/config` | 644 |
| Keystore подписи (вся папка: `release.p12`, `release.properties`, `release.key`, `release.crt`) | `~/.config/3x-ui-android-keystore/` | `~/.config/3x-ui-android-keystore/` | dir 700, файлы 600 |

**Перенос одним архивом.** На ТЕКУЩЕМ Mac:
```bash
tar czf ~/3xui-secrets.tgz -C ~ \
  .gl-token \
  .ssh/gitlab.key \
  .config/3x-ui-android-keystore
```
Передай `~/3xui-secrets.tgz` на новую машину (AirDrop/USB/scp). На НОВОЙ машине:
```bash
cd ~ && tar xzf ~/3xui-secrets.tgz
chmod 600 ~/.gl-token ~/.ssh/gitlab.key
chmod 700 ~/.config/3x-ui-android-keystore && chmod 600 ~/.config/3x-ui-android-keystore/*
rm ~/3xui-secrets.tgz
```
Затем допиши в `~/.ssh/config` (если файла/блока нет):
```
Host git.home.yukh.net
  HostName git.home.yukh.net
  Port 20222
  User git
  IdentityFile ~/.ssh/gitlab.key
```
Проверка: `ssh -T git@git.home.yukh.net` (должно поздороваться от GitLab),
`cat ~/.gl-token` (должен быть токен, ~52 символа).

**Если переносить нечем — пересоздать:**
- Токен: GitLab → Profile → Access Tokens (scopes `api`, `read_repository`,
  `write_repository`) → положить в `~/.gl-token`.
- SSH-ключ: `ssh-keygen -t ed25519 -f ~/.ssh/gitlab.key`, публичную часть
  (`~/.ssh/gitlab.key.pub`) добавить в GitLab → Profile → SSH Keys.
- Keystore: НЕ генерировать новый (сломает обновления приложения на устройствах).
  Восстановить `release.p12` из CI-переменной `RELEASE_KEYSTORE_B64`:
  ```bash
  mkdir -p ~/.config/3x-ui-android-keystore
  # значение переменной взять в GitLab → Settings → CI/CD → Variables
  echo "<RELEASE_KEYSTORE_B64>" | base64 -d > ~/.config/3x-ui-android-keystore/release.p12
  ```
  пароль/алиас — из переменных `RELEASE_KEYSTORE_PASSWORD` / `RELEASE_KEY_ALIAS`
  (`xui-release`) / `RELEASE_KEY_PASSWORD`. Для релизов через CI это не нужно —
  ключ там уже есть.

## Где что лежит
- Приложение: `android/`. iOS-заготовка: ветка `ios-app`, `ios/README.md`.
- Изменения: `CHANGELOG.md` / `CHANGELOG.ru.md`.
- Описание функционала: `README.md` / `README.ru.md`.
- CI: `.gitlab-ci.yml` (собирает только по тегу `vX.Y.Z`).
- Контекст/правила: `CLAUDE.md`, `docs/ANDROID-HANDOFF.md`.
