# Changelog

All notable changes to the 3X-UI Manager Android app are documented here.
Format based on [Keep a Changelog](https://keepachangelog.com/); the project
uses [Semantic Versioning](https://semver.org/).

🇷🇺 [Версия на русском](CHANGELOG.ru.md)

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
