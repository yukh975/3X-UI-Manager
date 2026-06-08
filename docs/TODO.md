# TODO

Agreed-but-not-yet-done tasks, to pick up next. (Done items live in the
changelogs; this file is only the backlog.)

---

## Dashboard: "Traffic this month" block — main panel + per node
**Status:** analysed ✅, feasible without backend changes — implement next.

Add a separate full-width metric card (styled like CPU/Memory) showing **proxied
(VPN) traffic for the current calendar month** — for the **main panel alone**
(NOT aggregating sub-nodes), and the same figure **per node**. Must be "since the
1st of the current month", not all-time.

### How (client-side only, no panel changes)
Monthly traffic = **Σ(up + down) over inbounds whose `trafficReset == "monthly"`.**
The panel's `@monthly` cron job resets those counters at 00:00 on the 1st, so for
a monthly-reset inbound `up+down` *is* exactly the traffic since the 1st.

- **Main panel:** `GET /panel/api/inbounds/list` → sum up+down of monthly-reset
  inbounds only.
- **Per node:** query each node's own `/panel/api/inbounds/list` directly (reuse
  the per-node query path already built for "online by server" —
  `PanelRepository.listNodeOnlines`), sum its monthly-reset inbounds.
- UI: a "Трафик за месяц" card on the Dashboard + a per-node line in the Nodes
  screen (next to CPU/RAM/uptime/version).

### Caveats (verified against the live panel)
- Counts **only** inbounds with `trafficReset = "monthly"`. Inbounds set to
  `never`/`daily`/etc. can't be attributed to "this month" from the API, so they
  are excluded. For a complete per-node figure, all of that node's inbounds must
  use monthly reset. Show a small hint when some inbounds on a node aren't
  monthly (so the number isn't silently partial).
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
