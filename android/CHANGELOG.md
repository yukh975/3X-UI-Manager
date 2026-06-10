# Changelog

All notable changes to the 3X-UI Manager Android app are documented here.
Format based on [Keep a Changelog](https://keepachangelog.com/); the project
uses [Semantic Versioning](https://semver.org/).

🇷🇺 [Версия на русском](CHANGELOG.ru.md)

## [0.3.17] — Unreleased

### Added
- **Outbounds editor:** a structured Outbounds list under the ⋮ menu (session
  login only, same as the Xray config) — add / edit / delete / reorder outbounds
  (order = priority; index 0 is the default route). Structured forms for vless,
  vmess, trojan, shadowsocks, socks, http, freedom, blackhole and wireguard, with
  transport + security (TCP / WS / gRPC / HTTPUpgrade / xHTTP / kcp, TLS / REALITY)
  for the proxy protocols. **Import from a `vless://` link** — paste a share link
  and it becomes an outbound. Niche types (dns, loopback, WARP, NordVPN, TUN,
  reverse, subscription outbounds) are edited via a raw-JSON box. Every edit
  round-trips through the full config, leaving routing / balancers / DNS /
  observatory intact.
- **Routing editor (⋮ menu):** routing rules (add / edit / delete / reorder —
  inbound / domain / IP / port / source / network / protocol / user → outbound or
  balancer) and balancers (tag / strategy / selector / fallback), plus the routing
  strategy.
- **DNS editor (⋮ menu):** enable toggle, DNS servers (bare address or full
  object — port / query strategy / domains / expected & unexpected IPs / skip-
  fallback / …), FakeDNS pools, and the DNS-level options.
- **General / Logs (⋮ menu):** routing strategy, log levels (access / error /
  mask / DNS log), outbound test URL, and traffic-statistics toggles.
- All four Xray-config sections round-trip through the same config (siblings and
  unknown keys preserved) and are session-login only.

## [0.3.16] — 2026-06-10

### Added
- **App icon:** a custom adaptive launcher icon (indigo “3X” monogram with a
  two-tone X) replaces the system-default icon — the app now shows one consistent
  icon across devices. Includes a monochrome layer for Android 13+ themed icons.
- **Dashboard → Geo databases:** an “Update all” button re-downloads every
  built-in geo database in a single panel call.

### Changed
- **Dashboard → Geo databases:** the card is collapsed by default (header only)
  and expands to the per-file list on tap (accordion).
- **Dashboard:** the online-clients card title no longer says “(tap)”.

## [0.3.15] — 2026-06-10

### Added
- **Dashboard:** tap any system-metric card (CPU, Memory, Disk, Load, Network,
  Connections) to open a **history chart** for that metric, with an interval
  **dropdown** at the top (Real-time / 30 min / 1 hour / 2 / 3 / 5 hours; default
  **Real-time**). Multi-value blocks chart every series — Load as 1m/5m/15m, Network
  as ↑/↓, Connections as TCP/UDP. Data comes from the panel's
  `GET /panel/api/server/history/{metric}/{bucket}` (Bearer-token), ~60 points per
  interval. Percent metrics use a fixed 0–100 % scale; the rest auto-scale.

## [0.3.14] — 2026-06-09

### Added
- **Dashboard:** a "Traffic this month" card showing proxied (VPN) traffic for
  the current month for the **main panel's own inbounds** (sub-nodes excluded),
  with the period start date ("since 01.06"). Computed client-side by grouping
  `/inbounds/list` by `nodeId` and summing up+down — no extra panel calls.
- **Nodes:** each node card now shows its **traffic this month** (that node's own
  inbounds). A trailing `*` flags a group where not every inbound resets monthly
  (its figure then counts all-time, not just this month).
- **Dashboard:** a "Geo databases" card to re-download the panel's built-in geo
  rule files — `geoip.dat`, `geosite.dat`, and the RU/IR variants — one at a time
  (`POST /panel/api/server/updateGeofile/:file`). Each update confirms first,
  since the panel restarts Xray afterwards (a brief connection drop).

### Changed
- **Panel v3.3.0 compatibility:** upstream v3.3.0 moved the Xray-config and
  panel-settings endpoints under `/panel/api/*`. `XuiApi` now targets the new
  paths — and because they accept a Bearer token there, the **Xray config
  editor** and the auto-derived **subscription URL** now work in **API-token**
  mode too (previously login/password only). ⚠️ Requires the panel on **v3.3.0+**.

### Fixed
- **Online by server:** the main-server group no longer lists node-only clients.
  The central onlines endpoint reports online emails across the whole node tree,
  so the main group could show a client that belongs only to a sub-node's inbound
  (e.g. an outbound credential the main server uses to chain to a node). The main
  group is now restricted to clients of the main panel's own inbounds
  (`nodeId` 0); node clients appear under their node. If a node isn't reachable
  directly from the device, its online clients now fall back to inbound-membership
  attribution instead of vanishing from the list.

## [0.3.12] — 2026-06-09

### Changed
- **Dashboard:** every metric is now its own full-width row, in order — CPU,
  Memory, Disk, Load, Net, Connections, Online (previously Load/Net/Connections/
  Online were cramped half-width tiles).
- **Nodes:** redesigned the node card — after the URL, each fact is on its own
  line: CPU · RAM, **Ping** (the ms value is now labelled), inbounds · clients,
  uptime, and the node's 3x-ui version.
- **Clients:** "Expires" now shows the number of **days remaining** (not a date)
  and gets a colon; "Last seen" moved to its own line, also with a colon, and
  reads "Never" for clients that have never connected (was a bare dash).
- **Inbounds:** colon after "Expires".

### Added
- **Nodes:** when a node runs an older 3x-ui than the latest, an **Update**
  button appears that triggers the node's self-update via the central panel
  (`POST /panel/api/nodes/updatePanel`).
- **Release:** the published APK filename now includes the version
  (`3x-ui-manager-<version>.apk`).

## [0.3.11] — 2026-06-08

### Fixed
- After **Start/Stop/Restart Xray** the status card no longer flickers:
  previously the button briefly showed the new state, snapped back to the old
  one, then took ~3 s (next poll) to settle. The panel keeps reporting the old
  Xray state for a moment, and the immediate refresh overwrote the optimistic
  value. Now the expected state is pinned for a few seconds (until Xray actually
  switches) while the dashboard refreshes immediately — no snap-back, no delay.

## [0.3.10] — 2026-06-08

### Changed
- **Restored the Xray Start / Stop buttons** on the dashboard (for all auth
  methods — token and login/password), alongside Restart. The connection loss on
  stop appears to happen only when the panel itself is reverse-proxied through
  Xray; on a direct connection, stopping is safe. The card optimistically shows
  the correct button after an action even if the poll doesn't return.

## [0.3.9] — 2026-06-08

### Added
- **Online clients grouped by server.** Tapping "Online" on the dashboard now
  shows the list grouped by server: the main panel first, then each node with
  its own connected clients. Each node is queried directly via its own API
  (address + token from the nodes list), so you see the real picture — including
  a client connected to several nodes at once (which the central API couldn't
  report). Nodes are queried in parallel.
- **Xray uptime** on the status card (time since the last Xray restart, separate
  from the overall server uptime).

### Changed
- The dashboard now offers only the **Restart Xray** button; Start and Stop are
  removed entirely (for all auth methods). Stopping Xray on panels proxied
  through Xray cuts off the panel itself — confirmed with both token and
  login/password — while Restart is safe.
- The connect screen's auth toggle is shortened to **"API Token" / "Login"** (was
  "Login & password", which overflowed and wrapped, breaking the layout).

## [0.3.8] — 2026-06-08

### Changed
- **Xray controls now depend on the auth method**: with an **API token** only
  **Restart** is available; with **login/password** you get the full
  **Start / Stop / Restart**. Reason: on panels reverse-proxied through Xray,
  stopping Xray cuts off the panel/API and a token session can't bring it back.
  Restart is safe — Xray comes back up and the connection is restored.
- The **online clients list** now shows only emails (no inbound): 3x-ui keys
  online/traffic by email under a single canonical inbound and copies that record
  into every inbound the email belongs to, so the API can't tell which inbounds a
  client is actually live on — showing one would be misleading.

## [0.3.7] — 2026-06-08

### Changed
- The words **inbound/outbound** are no longer translated in the Russian UI
  ("Изменить inbound", "Удалить inbound", …) — they're established terms.
- In the client share sheet, **Edit** and **Delete** are swapped (Edit left,
  Delete right), and **Close** is now a proper button instead of a text link.
- The Xray controls on the dashboard wrap to a centered second line when they
  don't fit one row (e.g. Russian "Перезапустить" + "Остановить").

### Fixed
- After **stopping Xray**, the dashboard immediately shows the "Start" button
  even if the panel connection drops (on setups where the panel is reverse-
  proxied through Xray, the poll after a stop may never return) — previously the
  stale "Restart/Stop" controls stuck around.
- On the **Xray config** screen in API-token mode, the raw "Network error" is no
  longer shown — the explanation that login/password is required is enough.

## [0.3.6] — 2026-06-08

> Every change in this release was verified on an emulator (editor/settings
> screenshots), not just by compiling.

### Fixed
- **Online clients list** now resolves the inbound via `clientStat.inboundId`
  (the one the panel actually tracks the client under) instead of whichever
  inbound's list the client appears in. One email can be a member of several
  inbounds (one user across multiple servers); 3x-ui stores its traffic/online
  under a single canonical inbound and replicates that record into the others,
  which is why all of them used to show. Now only the attributed one does.
- **Delete buttons (inbound/node) no longer run off-screen under the system
  navigation bar.** Editors are now rendered full-screen in the activity window
  (not in a `Dialog`, where Compose never received system-bar insets), and the
  delete button is pinned in a bottom bar that's always visible — no scrolling
  needed to reach it.
- **The Comment field in the client editor is no longer covered by the
  keyboard** — the form lifts above the keyboard so the field stays reachable.
- **Switches/buttons in Settings and the editors no longer overlap Russian
  text** — long labels get their own space (weight) with the control kept to
  the right.

## [0.3.5] — 2026-06-08

### Added
- **Pull-to-refresh on the dashboard** — swipe down from the top to manually
  refresh server status, the online list, and the panel-update check.
- **State-aware Xray controls** on the status card: when running, **Restart**
  and **Stop** (both confirmed); when stopped, only **Start**.

### Fixed
- **Online clients list** now shows only the inbound(s) a client is actually
  connected through right now (by `lastOnline` recency — a client can be on
  several at once), instead of every inbound they're a member of.
- **Delete buttons** in the inbound and node editors are no longer hidden under
  the Android navigation bar, and the **Comment** field in the client editor is
  no longer covered by the keyboard — editor dialogs now honor system insets
  and resize for the keyboard.

### Changed
- **Full Russian UI translation**: dashboard, inbounds, clients, nodes, all
  editors, the Xray config, the share sheet, and the connect screen — previously
  only the menu was translated.

## [0.3.4] — 2026-06-08

### Added
- **Client search by email** — filter the list by typing part of a name/address.
- **Dashboard → Online**: tapping the online count opens a list of currently
  connected clients, each with the inbound(s) they belong to (refreshes every 3 s).
- **Xray version** on the status card and a **3x-ui panel version** card at the
  bottom of the dashboard; when an update is available an "Update" button
  (confirmed) triggers the panel self-update.
- **In-app RU/EN language switch** (Settings, ⋮ menu) — no restart needed.
- **Settings screen** with an "About" section (version, copyright).
- **Session persistence**: auto-relogin on launch and on a 401 response — the
  app no longer kicks you out after a login/password sign-in.
- **App lock**: passcode on launch + fingerprint (biometric) unlock.

### Changed
- The release APK is now named `3x-ui-manager-release.apk` (instead of
  `app-release.apk`), matching the project name.
- CI no longer builds a debug APK — only the signed release is built.
- The releases page and release notes are now in Russian, generated from this
  changelog (the matching version's section).

### Fixed
- The lock screen no longer fails to compile: the biometric prompt labels are
  hoisted into composable scope (`tr()` can't be called from a plain function).

## [0.3.3] — 2026-06-08

### Fixed
- Editor dialogs (client, inbound, node, Xray) now fill the screen height.
  They were sizing to their content, so tall forms overflowed off-screen:
  the content wouldn't scroll and the bottom (Delete button / last field)
  was cut off. The earlier inset tweak didn't address this; the real cause
  was the dialog wrapping content height.

## [0.3.2] — 2026-06-08

### Changed
- Inbound editor is now structured instead of raw JSON: transport (network +
  ws/grpc/httpupgrade fields), security (TLS SNI; Reality dest/serverNames/
  shortIds/fingerprint/keys), and sniffing (switch + checkboxes) are edited
  with dropdowns, switches and fields. Unmodeled config keys are preserved.
  Protocol `settings` (decryption/fallbacks/…) remains under an Advanced
  JSON section.

### Fixed
- The inbound editor no longer shows the inbound's client list. Clients are
  managed on the Clients tab and are kept untouched when an inbound is saved.

## [0.3.1] — 2026-06-08

### Added
- Client email/name is now editable when editing a client (rename).
- Client inbound membership is editable on edit (attach/detach the delta).
- Optional "Subscription base URL" on the connect screen — enables per-client
  subscription links + QR when using an API token (the token can't read the
  panel's subscription settings).
- Confirmation dialog on every save, so nothing is changed by accident.
  Deletes were already confirmed. (The inbound enable/disable toggle stays
  one-tap — it's quick and reversible.)

### Fixed
- Editor dialogs no longer draw under the Android navigation bar — the
  "Delete" button and the last form field are fully visible again.
- The on-screen keyboard no longer covers the focused field in editors.

## [0.3.0] — 2026-06-08

### Added
- **Client editor** — create, edit and delete clients: email, inbound
  membership, traffic limit, IP limit, reset period, expiry (date picker),
  Telegram ID, group, comment.
- **Inbound editor** — create, edit and delete inbounds: remark, port, listen,
  protocol, traffic limit, reset schedule, expiry, and raw-JSON
  `settings` / `streamSettings` / `sniffing` for any transport/TLS/Reality.
- **Nodes tab** — manage remote panels (multi-panel): list with online status,
  CPU/RAM/latency/counts; add, edit, delete.
- **Xray config editor** — edit the full Xray config (outbounds, routing, DNS)
  as JSON. Requires login/password auth (the panel gates it behind a session).
- Subscription URL in the client share sheet, built from the panel's
  subscription settings (login/password auth).

### Changed
- Client share sheet reworked into an accordion: a **Subscription** section
  (QR + link) and a **Connections** section where each link expands to its own
  QR + copy/share.

### Fixed
- Share-sheet action buttons collapsing into an unusable thin strip when a
  client had several connection links.

## [0.2.2] — 2026-06-07

### Fixed
- DTOs now match the real 3x-ui v3.x API responses, fixing the "Unexpected
  response" error on connect: nested `mem`/`xray`/`netIO` objects in server
  status, numeric `tgId` and nested `traffic` on clients, array-shaped client
  links endpoint.
- Login/password flow treats `{success, obj:null}` ack responses as success
  instead of failure.

## [0.2.0] — 2026-06-07

### Added
- Dashboard with live server-status polling.
- Inbounds list with enable/disable toggle and traffic stats.
- Clients list with subscription link and QR code.
- Bottom navigation across Dashboard / Inbounds / Clients.

## [0.1.0] — 2026-06-07

### Added
- First installable build.
- Connect screen with two auth modes: API token, or login/password with
  optional 2FA. Self-signed TLS toggle.
- Project scaffold, GitLab CI, signed-release pipeline with versioning from
  git tags.
