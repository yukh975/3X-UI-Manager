# 3X-UI Manager — Android app

Native Android client for managing a [3x-ui](https://github.com/MHSanaei/3x-ui) panel over its REST API — dashboard, inbounds, clients (with QR sharing), nodes, and the Xray config, on the go.

🇷🇺 [Версия на русском](README.ru.md) · 📝 [Changelog](CHANGELOG.md)

---

## Features

### Connect
- Two auth modes (pick on the connect screen): **API token** (Bearer) or **login + password** (with optional 2FA).
- **Self-signed TLS** toggle for panels with a self-signed certificate.
- **Subscription base URL** field (see [Subscriptions](#subscriptions)).
- Credentials are stored encrypted (`EncryptedSharedPreferences`, AES-256, key in the Android Keystore).

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
Structured editors over the panel's Xray config — each round-trips the **whole** config, preserving sibling and unknown keys. Field sets mirror the panel's own forms. (Needs API-token access on panel **v3.3.0+**, otherwise a login session.)
- **Outbounds** — list (add / edit / delete / reorder) + per-protocol forms (vless, vmess, trojan, shadowsocks, socks, http, freedom, blackhole, wireguard) with transport + TLS/REALITY; **import from a `vless://` link**.
- **Routing** — rules (source / dest / inbound → outbound or balancer, reorderable) + balancers (strategy / selector / fallback) + routing strategy.
- **DNS** — enable, DNS-level options, servers (bare string or full object), FakeDNS pools.
- **General / Logs** — log levels, routing strategy, outbound test URL, traffic-stats toggles.
- **Xray config (raw)** — the full config as JSON, a fallback for anything the forms don't cover (Observatory, advanced xHTTP, hysteria, reverse, …).

### Backup / restore (⋮ menu)
- **Back up** the panel's whole database (settings, inbounds, clients **and** the Xray config) to a file via the system file picker, and **restore** the panel from one. Engine-agnostic — the panel saves SQLite as `x-ui.db` and PostgreSQL as `x-ui.dump`, and imports either back. Restore confirms first and restarts Xray (a brief connection drop). Works with an API token.

### Other
- **Language**: English (default) or Russian, switchable in **Settings** (⋮ menu) — no restart needed.
- Confirmation dialog on every **save** and **delete** so nothing changes by accident.

---

## Authentication

| Mode | What works |
|------|-----------|
| **API token** (Bearer) | On panel **v3.3.0+**, everything the app does — dashboard, inbounds, clients, nodes, the **Xray config editor**, **settings**, **subscription links** and **backup / restore**. The token doesn't time out, so it never drops you mid-session. |
| **Login + password** (session, optional 2FA) | The same surface. Needed only for panels **older than v3.3.0**, where settings and the Xray config are exposed to a logged-in session but not to a token. |

On panel **v3.3.0** the whole management API moved under `/panel/api/*`, which a Bearer token authenticates (as the panel's first admin). So a token now also covers **settings**, the **Xray-config editor** and **subscription-base auto-discovery** — things that used to need a session. A 3x-ui token is **full admin** (there are no read-only or scoped tokens), so guard it like the password.

Create a token in the panel under **Settings → Security → API Token**.

**Session length (login mode):** the panel session lasts `sessionMaxAge` (default **360 min / 6 h**, configurable under Settings → Security) — after which login mode re-authenticates and re-prompts 2FA. Token mode has no such timeout.

---

## Subscriptions

A **subscription link** is one URL that hands a client app *all* of a user's configs and keeps them updated. In 3x-ui it isn't stored — it's built as:

```
<subscription server base>/<client subId>
```

The `subId` is unique per client (the app already has it), so each client gets its own link. The only shared part is the **subscription server base**, which is a panel setting.

- **Auto (panel v3.3.0+):** the app reads the base from the panel automatically — with **either** an API token or a login session — so the subscription QR + link appear per client with no setup.
- **Manual override:** set the base **once** in the connect screen's **Subscription base URL** field when the auto-read doesn't fit — a panel **older than v3.3.0** (its settings aren't token-readable), or a **reverse proxy** in front of the subscription whose public URI differs from what the panel stores. Enter either:
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

Open the `android/` folder in Android Studio — it syncs and downloads everything, including the Gradle wrapper.

```bash
cd android
gradle wrapper --gradle-version 8.10.2   # one-time
./gradlew assembleDebug                   # debug APK
./gradlew assembleRelease                 # signed release APK (needs keystore)
./gradlew testDebugUnitTest               # unit tests
```

## CI / releases

[`.gitlab-ci.yml`](../.gitlab-ci.yml) builds **only** on a version tag (`vX.Y.Z`) or a manual trigger — branch pushes don't build. A tag pipeline builds the **signed release APK**, runs unit tests, uploads the APK to the project's Generic Package Registry, and auto-creates a GitLab Release. `versionName`/`versionCode` derive from the tag.

To cut a release: push a `vX.Y.Z` tag.

---

© 2026 Yuriy Khachaturian ([yukh.net](https://yukh.net))
