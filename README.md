# 3X-UI Manager

Native mobile clients for managing a [3x-ui](https://github.com/MHSanaei/3x-ui) panel over its REST API — dashboard, inbounds, clients (with QR sharing), nodes, and the Xray config, on the go.

This branch (`main`) holds the **Android** app (Kotlin + Jetpack Compose), built straight from the repository root.

🇷🇺 [Версия на русском](README.ru.md) · 📝 [Changelog](CHANGELOG.md)

---

## Repository layout

| Branch | What's there |
| --- | --- |
| **`main`** | The **Android** app (this branch). Gradle project at the repo root (`app/`, `build.gradle.kts`, …). |
| **`apple`** | The **Apple / iOS** app — a Kotlin Multiplatform + Compose-MP foundation. |

The 3X-UI panel **user manual** (RU canonical + EN) lives in its own repository, **3X-UI-Manual** — it's unrelated to the manager apps.

The upstream project lives at [MHSanaei/3x-ui](https://github.com/MHSanaei/3x-ui); a private **pure mirror** of it is kept separately as a read-only reference for diffing what changed on a panel upgrade. This repository is the management app only — it carries none of the panel's Go source.

---

## Features

### Connect
- **API token** (Bearer) only — paste a token from the panel. Requires panel **v3.3.0 or newer**.
- **Self-signed TLS** toggle for panels with a self-signed certificate.
- **Subscription base URL** field (see [Subscriptions](#subscriptions)).
- The token is stored encrypted (`EncryptedSharedPreferences`, AES-256, key in the Android Keystore).
- **Multiple panels (profiles).** Save several panels and switch between them from the **⇄** button in the top bar — switch the active panel, add another, or remove one.

### Dashboard
- Live server status, polled every 3 s: **Xray** running/stopped + version, one-tap **Restart Xray** (confirmed).
- **CPU**, **Memory**, **Disk** with used/total; **Online** clients, **Net ↑/↓** per second, **TCP/UDP** connection counts, **load average**, **uptime**, public IP.
- Tap any metric card (CPU, Memory, Disk, Load, Net, Connections) → a **history chart** with an interval dropdown (real-time up to 5 h).
- **Traffic this month** — proxied (VPN) traffic for the current month, for the main panel's own inbounds (with the period start date).
- Tap **Online** → list of currently-connected clients, each with the inbound(s) they belong to (refreshes every 3 s).
- **3x-ui panel version** card at the bottom; if an update is available, an **Update** button (confirmed) triggers the panel self-update.
- **Geo databases** — re-download the panel's built-in geo rule files (`geoip.dat`, `geosite.dat`, and the RU/IR variants) individually; the panel restarts Xray after each update (confirmed first).

### Inbounds
- List with per-row **enable/disable** toggle, traffic usage vs quota, client counts.
- **Create / edit / delete** with a structured editor (no raw JSON needed for the common cases):
  - Basics: remark, port, listen IP, protocol, enabled.
  - Limits: traffic limit (GB), traffic reset schedule, expiry (date picker).
  - **Transport**: network (tcp/ws/grpc/httpupgrade/xhttp/kcp) with the relevant fields (ws/httpupgrade path+host, grpc service name).
  - **Security**: none / TLS (SNI) / Reality (dest, server names, short IDs, fingerprint, public/private key).
  - **Sniffing**: enable + destOverride checkboxes.
  - **Advanced (JSON)**: the protocol `settings` (decryption, fallbacks, …). The client list is **not** shown here — clients are managed on the Clients tab and are never touched when you save an inbound.

### Clients
- List with online presence dot, traffic, expiry, last-seen.
- **Search by email** — filter the list by typing part of a name/address.
- **Create / edit / delete** (email/name editable; rename supported): inbound membership (multi-select; attach/detach on save), traffic limit, IP limit, reset period, expiry, Telegram ID, group, comment.
- **Share sheet** (tap a client): a **Subscription** section (QR + link) and an expandable **Connections** section where each server link has its own QR + copy/share.

### Nodes
- Manage remote panels (multi-panel): online status, CPU/RAM/latency, inbound/client counts, **traffic this month** per node.
- **Add / edit / delete**: name, address, port, scheme, base path, API token, TLS verify mode, allow-private-address.

### Xray config (⋮ menu)
Structured editors over the panel's Xray config — each round-trips the **whole** config, preserving sibling and unknown keys. Field sets mirror the panel's own forms. (Needs panel **v3.3.0+**.)
- **Outbounds** — list (add / edit / delete / reorder) + per-protocol forms (vless, vmess, trojan, shadowsocks, socks, http, freedom, blackhole, wireguard) with transport + TLS/REALITY; **import from a `vless://` link**.
- **Routing** — rules (source / dest / inbound → outbound or balancer, reorderable) + balancers (strategy / selector / fallback) + routing strategy.
- **DNS** — enable, DNS-level options, servers (bare string or full object), FakeDNS pools.
- **General / Logs** — log levels, routing strategy, outbound test URL, traffic-stats toggles.
- **Xray config (raw)** — the full config as JSON, a fallback for anything the forms don't cover (Observatory, advanced xHTTP, hysteria, reverse, …).

### Backup / restore (⋮ menu)
- **Back up** the panel's whole database (settings, inbounds, clients **and** the Xray config) to a file via the system file picker, and **restore** the panel from one. Engine-agnostic — the panel saves SQLite as `x-ui.db` and PostgreSQL as `x-ui.dump`, and imports either back. Restore confirms first and restarts Xray (a brief connection drop). Works with an API token.

### Panel admin (⋮ menu)
- **Admin account** — change the panel login username + password (current credentials required to confirm).
- **API tokens** — list, create (the plaintext is shown once to copy), enable/disable, delete.
- **Restart panel** — restart the panel service (confirmed; the app reconnects after a few seconds).

### Other
- **App lock**: an optional 4–8 digit passcode (+ biometric unlock) in **Settings** (⋮ menu). It guards the **signed-in panel UI only** — re-locking when the app is backgrounded while connected and on launch when a saved session is restored. It is **not** asked on the Connect screen (signed out) or right after a fresh manual sign-in.
- **Language**: English (default) or Russian, switchable in **Settings** (⋮ menu) — no restart needed.
- Confirmation dialog on every **save** and **delete** so nothing changes by accident.

---

## Authentication

The app authenticates with an **API token** (Bearer) only, and is designed for panel **v3.4.x**. There is no login/password mode.

> **Panel v3.3.x users:** the current app version is optimised for **v3.4.x**. For panels on **v3.3.x**, use the last compatible release — **v0.3.23**.

On panel **v3.3.0** the whole management API moved under `/panel/api/*`, which a Bearer token authenticates (as the panel's first admin). So a token covers **everything the app does** — dashboard, inbounds, clients, nodes, the **Xray config editor**, **settings**, **subscription links** and **backup / restore**. A 3x-ui token is **full admin** (there are no read-only or scoped tokens), so guard it like the password.

Create a token in the panel under **Settings → Security → API Token**.

**Why a token (and not login):** a token **doesn't expire on a timer**, so it never drops you mid-session and never re-prompts 2FA — unlike a login session, which lasts `sessionMaxAge` (default **360 min / 6 h**) and then drops. Pre-v3.3.0 panels exposed settings and the Xray config only to a logged-in session, which is why login used to be supported; on v3.3.0+ a token reaches all of it, so the app is now token-only.

The one edge case: if a token is later **disabled or recreated** in the panel, its requests fail with `401`. The app then **returns you to the Connect screen** with a clear "your API token is no longer valid" message and your details pre-filled — re-enter a working token and reconnect. Rare in practice, since tokens don't time out.

---

## Subscriptions

A **subscription link** is one URL that hands a client app *all* of a user's configs and keeps them updated. In 3x-ui it isn't stored — it's built as:

```
<subscription server base>/<client subId>
```

The `subId` is unique per client (the app already has it), so each client gets its own link. The only shared part is the **subscription server base**, which is a panel setting.

- **Auto (panel v3.3.0+):** the app reads the base from the panel automatically over the token — so the subscription QR + link appear per client with no setup.
- **Manual override:** set the base **once** in the connect screen's **Subscription base URL** field when the auto-read doesn't fit — a **reverse proxy** in front of the subscription whose public URI differs from what the panel stores. Enter either:
  - your **reverse-proxy URI** (e.g. `https://sub.example.com/`), or
  - the panel's **Subscription URL** as shown in the panel.

  Then the app builds each client's link as `base + subId`.

**Where to find it in the panel:** *Settings → Subscription* — the enabled state, port, path and domain. The full base typically looks like `https://your-host:2096/sub/` (port/path are whatever you configured). If you use a reverse proxy in front of it, use that public URI instead.

---

## Tech stack

- Kotlin 2.1 + Jetpack Compose (Material 3)
- Min SDK 24 (Android 7.0), target/compile SDK 35 (Android 15)
- Hilt (DI), Retrofit + OkHttp + kotlinx.serialization (networking), zxing (QR codes)
- In-app i18n via a lightweight `tr()` lookup (English source + Russian dictionary)

## Building

Open this repository in Android Studio — it syncs and downloads everything, including the Gradle wrapper. From the command line:

```bash
gradle wrapper --gradle-version 8.10.2   # one-time (the wrapper jar is git-ignored)
./gradlew assembleDebug                   # debug APK
./gradlew assembleRelease                 # signed release APK (needs keystore)
```

## CI / releases

[`.gitlab-ci.yml`](.gitlab-ci.yml) builds **only** on a version tag (`vX.Y.Z`) or a manual trigger — branch pushes don't build. A tag pipeline builds the **signed release APK**, uploads it to the project's Generic Package Registry, and auto-creates a GitLab Release. `versionName`/`versionCode` derive from the tag.

To cut a release: push a `vX.Y.Z` tag.

---

© 2026 Yuriy Khachaturian ([yukh.net](https://yukh.net))
