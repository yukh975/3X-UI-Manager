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
- Изменения: `android/CHANGELOG.md` / `android/CHANGELOG.ru.md`.
- Описание функционала: `android/README.md` / `android/README.ru.md`.
- CI: `.gitlab-ci.yml` (собирает только по тегу `vX.Y.Z`).
- Контекст/правила: `CLAUDE.md`, `docs/ANDROID-HANDOFF.md`.
