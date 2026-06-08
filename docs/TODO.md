# TODO

Agreed-but-not-yet-done tasks, to pick up next. (Done items live in the
changelogs; this file is only the backlog.)

---

## Dashboard: traffic block — total + this month, main panel + per node
**Status:** analysed ✅, feasible without backend changes — implement next.

Add a separate full-width metric card (styled like CPU/Memory) showing proxied
(VPN) traffic **both ways**:
- **Total** (all-time) and
- **This month** (since the 1st of the current calendar month),

for the **main panel alone** (NOT aggregating sub-nodes), and the same pair
**per node**.

### How (client-side only, no panel changes)
- **Total** = Σ(up + down) over **all** inbounds.
- **This month** = Σ(up + down) over inbounds whose `trafficReset == "monthly"`.
  The panel's `@monthly` cron resets those counters at 00:00 on the 1st, so for a
  monthly-reset inbound `up+down` *is* exactly the traffic since the 1st.

- **Main panel:** `GET /panel/api/inbounds/list` → sum up+down of monthly-reset
  inbounds only.
- **Per node:** query each node's own `/panel/api/inbounds/list` directly (reuse
  the per-node query path already built for "online by server" —
  `PanelRepository.listNodeOnlines`), sum its monthly-reset inbounds.
- UI: a "Трафик за месяц" card on the Dashboard + a per-node line in the Nodes
  screen (next to CPU/RAM/uptime/version).

### Caveats (verified against the live panel)
- **This-month** figure counts **only** inbounds with `trafficReset = "monthly"`.
  Inbounds set to `never`/`daily`/etc. can't be attributed to "this month" from
  the API, so they're excluded from the monthly number (they still count toward
  **Total**). For a complete monthly figure, all of that node's inbounds must use
  monthly reset — show a small hint when some aren't, so it isn't silently partial.
- Do **NOT** use `netTraffic` from `/server/status` — that's the NIC counter
  since OS boot, not monthly.
- Node metric history (`/nodes/history`) is **cpu/mem only** — there is no
  per-node traffic history to integrate.

### Files to touch
- `data/repo/PanelRepository.kt` — add `monthlyTraffic()` for main + a per-node
  inbound-sum helper (mirror `listNodeOnlines`).
- `ui/screen/dashboard/DashboardViewModel.kt` + `DashboardScreen.kt` — fetch +
  new card.
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
