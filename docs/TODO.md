# TODO

Agreed-but-not-yet-done tasks, to pick up next. (Done items live in the
changelogs; this file is only the backlog.)

_Backlog is currently empty — the dashboard traffic-this-month card and the geo
database updater both shipped in v0.3.14._

---

## Deferred / optional (analysed, not requested yet)

These came up while building the geo updater; left out by choice, recorded so the
analysis isn't lost:

- **Geo last-update date.** No panel API exposes the built-in `geoip.dat` /
  `geosite.dat` file mtime. User chose to show **no date** (just the Update
  button). To add it later: persist the app-triggered update time locally per
  server, or add a small panel endpoint returning the geofile mtime.
- **Custom geo resources.** `POST /panel/api/custom-geo/update-all` /
  `/custom-geo/download/:id`, and `GET /panel/api/custom-geo/list` returns
  `lastUpdatedAt` per resource — could be surfaced if the user starts using
  custom geo.
- **Per-node geo update.** Each node is its own panel, so `updateGeofile` could be
  called against a node directly (reuse the per-node API path) from the Nodes tab.
