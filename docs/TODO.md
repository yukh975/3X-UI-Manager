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
The central `GET /panel/api/inbounds/list` **already includes node inbounds**,
each tagged with a `nodeId` (absent/0 = the main node's own inbounds; `N` = node
with that id). So **no per-node queries are needed** — group the one list by
`nodeId`:
- **Main node** = inbounds with no/zero `nodeId` (e.g. NETADM, FAM).
- **Node N** = inbounds with `nodeId == N`; map id→name via `/nodes/list`.

Per group, traffic = Σ(up + down). Each inbound also carries
`lastTrafficResetTime` (unix) — use it to label the figure ("since 01.06").

### ⚠️ Hard limit: one counter per inbound (total XOR monthly)
An inbound stores a **single** up/down counter; its `trafficReset` mode decides
what that counter means — you cannot read both all-time and this-month from it:
- `trafficReset == "monthly"` → counter = **this month** (panel's `@monthly`
  cron zeroes it on the 1st); all-time is **discarded**, not stored.
- `trafficReset == "never"` → counter = **all-time total**; there is **no**
  monthly breakdown.

Live-panel reality (user's setup):
- **Main node** NETADM + FAM are `monthly` → "this month" is exact; "total
  all-time" is NOT kept (equals the monthly number).
- **Nodes** WS-* are `never` → "total all-time" is exact; "this month" is NOT
  available unless those inbounds switch to monthly reset.

So "Total **and** This-month for everything" is not possible from the API alone.
Options to decide with the user:
1. Show whichever is available per group, labelled honestly (main → monthly;
   nodes → total) + the reset date. Simplest, no surprises.
2. Ask the user to set all inbounds to `monthly` → then everything shows
   this-month (but loses all-time).
3. App-side history: persist a per-inbound counter snapshot at month start and
   compute the delta → gives both, but is fragile (storage, counter resets,
   inbound add/remove, first-month has no baseline).

### Other caveats
- Do **NOT** use `netTraffic` from `/server/status` — NIC counter since OS boot.
- Node metric history (`/nodes/history`) is **cpu/mem only** — no traffic series.

### Files to touch
- `data/api/dto` — add `nodeId` + `lastTrafficResetTime` to the inbound DTO.
- `data/repo/PanelRepository.kt` — group inbounds by `nodeId`, sum up+down.
- `ui/screen/dashboard/DashboardViewModel.kt` + `DashboardScreen.kt` — new card.
- `ui/screen/nodes/NodesScreen.kt` / `NodesViewModel.kt` — per-node line.
- i18n: `RuStrings.kt` ("Traffic" / "This month" / "Total" → RU).

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
