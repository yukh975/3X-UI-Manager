# TODO

Agreed-but-not-yet-done tasks, to pick up next. (Done items live in the
changelogs; this file is only the backlog.)

---

## Dashboard: traffic this month — main node + per node
**Status:** analysed ✅, feasible without backend changes — implement next.
**Decision:** user sets **all** inbounds to `trafficReset = "monthly"` and only
cares about the **current** month (past months are billed/closed). So we show a
single figure per group — traffic this month — no all-time, no app-side history.

Add a full-width metric card (styled like CPU/Memory) showing proxied (VPN)
**traffic for the current month**, for the **main node alone** (its own inbounds,
not aggregating sub-nodes) and **per node**.

### How (client-side only, no panel changes)
`GET /panel/api/inbounds/list` already includes node inbounds, each tagged with
`nodeId` (absent/0 = main node's own inbounds; `N` = node id). Group the one list
by `nodeId` — **no per-node queries needed**:
- **Main node** = inbounds with no/zero `nodeId` (NETADM, FAM).
- **Node N** = inbounds with `nodeId == N`; map id→name via `/nodes/list`.

Per group: traffic this month = **Σ(up + down)** (every inbound is monthly, so
the counter == this month). Label with `lastTrafficResetTime` ("since 01.06").

### Caveats
- Assumes every inbound is `trafficReset = "monthly"`. If any isn't, its number
  is its all-time counter, not this month — optionally flag such inbounds.
- Switching an inbound `never → monthly` does **not** zero it immediately; the
  counter keeps its accumulated value until the next 1st-of-month cron (or a
  manual reset via `POST /panel/api/inbounds/:id/resetTraffic`). The 3 WS-*
  inbounds were reset manually once to start clean.
- Do **NOT** use `netTraffic` from `/server/status` — NIC counter since OS boot.
- Node metric history (`/nodes/history`) is cpu/mem only — no traffic series.

### Files to touch
- `data/api/dto` — add `nodeId` (+ optional `lastTrafficResetTime`) to inbound DTO.
- `data/repo/PanelRepository.kt` — group inbounds by `nodeId`, sum up+down.
- `ui/screen/dashboard/DashboardViewModel.kt` + `DashboardScreen.kt` — new card.
- `ui/screen/nodes/NodesScreen.kt` / `NodesViewModel.kt` — per-node line.
- i18n: `RuStrings.kt` ("Traffic this month" → "Трафик за месяц", etc.).

---

## Geo databases: update from the app + last-update date
**Status:** analysed ✅ — update is fully feasible; the last-update date is only
partly available (see caveat).

Let the user update the panel's geo databases from the app, and show when they
were last updated.

### How (endpoints verified in the panel source)
- **Update built-in `geoip.dat` / `geosite.dat`:**
  `POST /panel/api/server/updateGeofile/:fileName` (one) or
  `POST /panel/api/server/updateGeofile` (all). Token-authed (same group as
  `/server/status`, `/server/updatePanel`, which the app already uses).
- **Update custom geo resources** (if any are configured):
  `POST /panel/api/custom-geo/update-all` or `/custom-geo/download/:id`.
- **Per node:** each node is its own panel, so the same `updateGeofile` can be
  called against a node directly (reuse the per-node API path) — optional.

### Last-update date
- **Custom geo:** ✅ `GET /panel/api/custom-geo/list` returns `lastUpdatedAt`
  (unix) per resource — show it directly.
- **Built-in `geoip.dat`/`geosite.dat`:** ❌ **no API exposes the file mtime.**
  Options (decide with user):
  1. show the date of the last update **triggered from the app** (persist it
     locally per server) — won't reflect cron/web-panel updates;
  2. show "date unavailable";
  3. add a small panel-side endpoint returning the geofile mtime (backend change).

### UI
- A "Geo databases" card/section (Dashboard or Settings) with an **Update** button
  (spinner while running, toast on success) + the last-update date where available.

### Files to touch
- `data/api/XuiApi.kt` — `updateGeofile(name)` (+ optional custom-geo endpoints).
- `data/repo/PanelRepository.kt` — `updateGeofiles()` wrapper.
- `ui/screen/dashboard` or `settings` — the card + Update action + date.
- i18n: `RuStrings.kt` ("Update geo databases" → "Обновить гео-базы", etc.).
