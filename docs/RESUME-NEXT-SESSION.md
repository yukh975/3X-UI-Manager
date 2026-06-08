# Как продолжить завтра на другом компьютере

Пошаговый порядок действий, чтобы поднять контекст и продолжить работу над
Android-приложением (`3X-UI Manager`) без потерь.

> Полный технический контекст — в [ANDROID-HANDOFF.md](ANDROID-HANDOFF.md).
> Правила для ассистента — в [../CLAUDE.md](../CLAUDE.md) (подхватывается сам).

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
Registry и создаст GitLab Release с русским описанием из `android/CHANGELOG.ru.md`.
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
3. Сборка: `cd android && ./gradlew :app:assembleDebug`.
4. Для визуальной проверки — эмулятор (AVD) + `adb install` + скриншоты.
   Подробности и подводные камни — в ANDROID-HANDOFF.md.

## 6. (Опционально) Локальная подпись release
Файл keystore (`~/.config/3x-ui-android-keystore/release.p12` + `release.properties`)
машинно-локальный, в репозитории его нет. Он продублирован в CI-переменной
`RELEASE_KEYSTORE_B64` (base64). Чтобы подписывать локально — восстанови его из
этой переменной или из своего бэкапа. Для релизов через CI это не требуется.

---

## Что НЕ хранится в репозитории (секреты — перенести вручную)
- `~/.gl-token` — токен GitLab.
- `~/.ssh/gitlab.key` — SSH-ключ.
- keystore подписи (есть в CI-переменных, для CI-релизов не нужен локально).

## Где что лежит
- Приложение: `android/`. iOS-заготовка: ветка `ios-app`, `ios/README.md`.
- Изменения: `android/CHANGELOG.md` / `android/CHANGELOG.ru.md`.
- Описание функционала: `android/README.md` / `android/README.ru.md`.
- CI: `.gitlab-ci.yml` (собирает только по тегу `vX.Y.Z`).
- Контекст/правила: `CLAUDE.md`, `docs/ANDROID-HANDOFF.md`.
