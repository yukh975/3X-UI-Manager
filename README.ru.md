# 3X-UI Manager — Apple

Нативные приложения Apple для управления панелью [3x-ui](https://github.com/MHSanaei/3x-ui) через REST API — дашборд, входящие, клиенты (с QR), узлы и конфиг Xray.

Эта ветка (`apple`) — проект на **Kotlin Multiplatform + Compose Multiplatform**, собирается прямо из корня репозитория. Даёт приложение для **iOS** (через Xcode) и для **macOS** (desktop), разделяя бизнес-логику с Android-приложением.

🇬🇧 [English version](README.md)

---

## Структура репозитория

| Ветка | Что внутри |
| --- | --- |
| **`main`** | Приложение для **Android** (Kotlin + Jetpack Compose). |
| **`apple`** | Приложение для **Apple** (эта ветка) — Kotlin Multiplatform / Compose-MP: **iOS** + **macOS desktop**. |
| **`manual`** | **Руководство** пользователя панели 3X-UI (RU — каноничное, + EN), под панель v3.3.0. |

Апстрим живёт в [MHSanaei/3x-ui](https://github.com/MHSanaei/3x-ui); рядом отдельно держится приватное **полное зеркало** — как read-only ссылка для сравнения, что изменилось при обновлении панели. Этот репозиторий — только приложение-менеджер, без Go-исходников самой панели.

**Источник истины** по функциям и версиям — Android; эта ветка следует за ним.

---

## Модули

| Модуль | Назначение |
| --- | --- |
| **`:shared`** | Кроссплатформенная бизнес-логика (API-клиент, модели, репозиторий) — таргеты iOS + JVM desktop. |
| **`:composeApp`** | UI на Compose Multiplatform и оболочка приложения — таргеты iOS + `jvm("desktop")`. |
| **`iosApp/`** | Xcode-проект, который оборачивает `:composeApp` в iOS-приложение (Swift-точка входа + asset catalog). |

## Возможности

Паритет функций с [Android-приложением](https://github.com/MHSanaei/3x-ui) (полный список — в README ветки `main`): подключение по **API-токену**, живой **дашборд**, **входящие** (вкл/правка), **клиенты** (поиск, правка, QR/подписка), **узлы**, структурный редактор **конфига Xray**, **бэкап/восстановление**, **администрирование панели** и опциональная **блокировка приложения** (код-пароль + биометрия), защищающая только залогиненный интерфейс.

## Аутентификация

Только **API-токен** (Bearer) — нужна панель **v3.3.0 или новее**. Токен 3x-ui — это полный админ (read-only и scoped-токенов нет), берегите его как пароль. Создаётся в **Settings → Security → API Token**.

## Сборка

Jar Gradle-**wrapper'а закоммичен**, поэтому `./gradlew` работает «из коробки». Нужна JDK 17+.

### iOS

Откройте `iosApp/iosApp.xcodeproj` в **Xcode** и запустите на симуляторе или устройстве. Xcode вызывает Gradle, чтобы собрать и встроить общий Kotlin-фреймворк; на маке с Apple Silicon это самый быстрый цикл. (Сборка iOS требует macOS + Xcode — Linux-CI её собрать не может, поэтому на этой ветке нет пайплайна GitLab.)

### macOS desktop

```bash
# Apple Silicon:
JAVA_HOME=<arm64 jdk> ./scripts/package-macos.sh "Apple Silicon"
# Intel:
JAVA_HOME=<x64 jdk> arch -x86_64 ./scripts/package-macos.sh "Intel"
```

`scripts/package-macos.sh` собирает дистрибутив, прописывает реальную версию в `CFBundleShortVersionString` (jpackage не принимает мажор `0.x`, поэтому `packageVersion` Gradle зафиксирован на `1.0.0`), подписывает ad-hoc, проверяет совпадение архитектуры лаунчера и JVM и упаковывает DMG через `create-dmg` (`brew install create-dmg`). Архитектура результата = архитектура JDK, которым запускается Gradle. Версия читается из `composeApp/.../Platform.desktop.kt`.

Быстрый запуск без упаковки:

```bash
./gradlew :composeApp:run            # desktop-приложение
```

## Релизы

Артефакты Apple (iOS `.ipa` и macOS `.dmg`) собираются **локально** на macOS — на этой ветке нет CI GitLab (Linux-раннер не может собирать Apple-таргеты). Релизный пайплайн Android-приложения — на ветке `main`.

**Синхронизация версии:** при подъёме версии Android на `main` также обновите `composeApp/.../Platform.desktop.kt` (`appVersionName`) и `iosApp/iosApp/Info.plist` (`CFBundleShortVersionString` / `CFBundleVersion`).

---

© 2026 Yuriy Khachaturian ([yukh.net](https://yukh.net))
