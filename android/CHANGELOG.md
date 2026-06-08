# Changelog

All notable changes to the 3X-UI Manager Android app are documented here.
Format based on [Keep a Changelog](https://keepachangelog.com/); the project
uses [Semantic Versioning](https://semver.org/).

🇷🇺 [Версия на русском](CHANGELOG.ru.md)

## [0.3.1] — 2026-06-08

### Added
- Client email/name is now editable when editing a client (rename).
- Client inbound membership is editable on edit (attach/detach the delta).
- Optional "Subscription base URL" on the connect screen — enables per-client
  subscription links + QR when using an API token (the token can't read the
  panel's subscription settings).
- Confirmation dialog on every save and on the inbound enable/disable toggle,
  so nothing is changed by accident. Deletes were already confirmed.

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
