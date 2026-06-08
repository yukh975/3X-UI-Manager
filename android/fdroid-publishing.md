# Публикация Android-приложения в F-Droid

F-Droid не принимает готовый APK. Сборка идёт **на серверах F-Droid из исходников**
по твоим метаданным. Поэтому обязательны открытый репозиторий и FOSS-зависимости.

---

## 1. Предварительные требования (Inclusion Policy)

- Публичный репозиторий с реальным исходным кодом.
- Файл FOSS-лицензии в репозитории (без него не примут).
- Только FOSS-зависимости. **Firebase / GMS не принимаются.**
  Проверить, что нет Google Play Services, Firebase Analytics/Crashlytics.
  Если есть — сделать flavor/версию без них.
- Только FOSS-инструменты сборки (проприетарная IDE недопустима).
- Релизы помечены git-тегами.
- Желательно: описание / скриншоты / changelog в репозитории в структуре Fastlane:
  `fastlane/metadata/android/<locale>/` (F-Droid подтягивает их оттуда).

---

## 2. Выбор пути подачи

| Путь | Скорость | Кому |
|------|----------|------|
| Submission Queue (issue на GitLab) | медленно (~месяц), сборка вручную | новичкам |
| **Merge request в fdroiddata** | быстро, рекомендуется | тем, кто работает с git/CI |

Дальше — рекомендуемый путь через merge request.

---

## 3. Подготовка окружения

```bash
# Установить fdroidserver (Debian/Ubuntu)
sudo apt install fdroidserver
# либо через pip
pip install fdroidserver

# Shallow-clone fdroiddata
git clone --depth=1 https://gitlab.com/fdroid/fdroiddata.git
cd fdroiddata
```

---

## 4. Генерация метаданных

```bash
# Автогенерация заготовки из репозитория
fdroid import --url https://github.com/yukh975/<repo>
```

Создаст `metadata/<applicationId>.yml`.

Альтернатива — вручную из шаблона:

```bash
cp templates/build-gradle.yml metadata/<applicationId>.yml
```

---

## 5. Ключевые поля метаданных

```yaml
Categories:
  - <категория из существующих в fdroiddata>
License: <SPDX-идентификатор, напр. GPL-3.0-only>
SourceCode: https://github.com/yukh975/<repo>
IssueTracker: https://github.com/yukh975/<repo>/issues

Builds:
  - versionName: <напр. 1.0.0>
    versionCode: <напр. 1>
    commit: <git-тег релиза>
    subdir: app
    gradle:
      - yes
    # если нужен flavor без GMS:
    # gradle:
    #   - foss

AutoUpdateMode: Version
UpdateCheckMode: Tags        # рекомендуется
CurrentVersion: <напр. 1.0.0>
CurrentVersionCode: <напр. 1>
```

---

## 6. Локальная проверка перед подачей

```bash
# Линт метаданных
fdroid lint <applicationId>

# Тестовая сборка (как на сервере F-Droid)
fdroid build <applicationId>:<versionCode>
```

Править метаданные, пока сборка не пройдёт чисто.

---

## 7. Подача merge request

```bash
git checkout -b add-<applicationId>
git add metadata/<applicationId>.yml
git commit -m "New app: <applicationId>"
git push <твой-форк> add-<applicationId>
```

Создать merge request на GitLab в основной fdroiddata.
Push в ветку запускает CI pipeline (Build → Pipelines) — смотреть результаты,
править до зелёного прогона.

Чек-лист MR (отметить в шаблоне):
- приложение соответствует inclusion criteria;
- сборка проходит `fdroid build`;
- релизы помечены тегами;
- метаданные (summary/description/images/changelog) в Fastlane/Triple-T структуре upstream.

---

## 8. После принятия MR

- Файл попадает в официальный fdroiddata.
- На следующем прогоне build-сервер F-Droid скачивает исходники, собирает и публикует.
- Статус включения — по revision history fdroiddata на GitLab.

---

## Обновление версий в будущем

Процесс тот же: правка метаданных в fdroiddata (новый `versionName`/`versionCode`/
`commit`-тег) → merge request. При `UpdateCheckMode: Tags` F-Droid отслеживает
новые теги релизов автоматически.
