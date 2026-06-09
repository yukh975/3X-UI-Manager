# TODO

Agreed-but-not-yet-done tasks, to pick up next. (Done items live in the
changelogs; this file is only the backlog.)

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
