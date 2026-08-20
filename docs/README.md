# Documentation

The 3X-UI Manager user manual, written from the app's source code.

| File | Language | Format |
|---|---|---|
| **[3X-UI-MANAGER.en.md](3X-UI-MANAGER.en.md)** · [PDF](pdf/3X-UI-MANAGER.en.pdf) | 🇬🇧 English | Markdown + PDF |
| **[3X-UI-MANAGER.ru.md](3X-UI-MANAGER.ru.md)** · [PDF](pdf/3X-UI-MANAGER.ru.pdf) | 🇷🇺 Русский | Markdown + PDF |

Russian is the source language; the English version is translated from it.

Rebuild the PDFs with `./scripts/build-pdf.sh` (needs pandoc and WeasyPrint), and
check that every in-page anchor resolves with `python3 scripts/checklinks.py`.

Licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
