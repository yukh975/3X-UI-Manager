# Outbounds editor — implementation plan (Android)

Structured Outbounds editor for the Xray config, replacing the raw-JSON textarea
(`ui/screen/xray/XrayConfigScreen.kt`) with tabbed forms like the web panel, while
keeping a raw-JSON fallback. Derived from a multi-agent research pass (2026-06-10).

Paths below are under `android/app/src/main/java/net/yukh/xui`.

## Core architecture
- The full Xray config round-trips as a `JsonElement` (`XrayConfigViewModel`,
  fetch `POST /panel/api/xray/`, save `POST /panel/api/xray/update`). The outbounds
  editor is a **structured view over the `outbounds` array inside that config** —
  no new endpoint.
- **Round-trip safety:** keep the raw `JsonObject` as the source of truth; forms
  are a *lens* (read/write individual keys via `data/json/JsonEdit.kt`'s immutable
  `put`/`child`/`string`/… which preserve unknown keys). Only `put("outbounds", …)`
  on save → every sibling (`routing`, `balancers`, `observatory`, `dns`, `reverse`,
  `outboundSub`, `policy`, `inbounds`) survives verbatim.
- **Session-gated** like the current Xray editor (the `!state.available` guard).
  Confirm token-vs-session on v3.3.0 during build.

## Phases (each independently compilable; emulator-verify when available)
- **Phase 1 — list + simple protocols + round-trip.** `OutboundTemplates.kt`
  (protocol list, `defaultOutbound()`, enum value lists, `PROXY_PROTOCOLS`),
  `JsonEdit` `array()`/`putArray()`/`int()`/`putInt()`, `OutboundsViewModel`,
  `OutboundsScreen` (list: add/edit/delete/reorder — **order = priority, index 0 =
  default route**), `OutboundEditorScreen` (common tag+protocol → freedom / blackhole
  / socks / http forms; dns / loopback via raw-JSON fallback; always-present raw
  escape hatch). Verify the save is non-destructive (siblings untouched) first.
- **Phase 2 — proxy protocols + stream settings + link import.** vmess / vless /
  trojan / shadowsocks forms + freshly-built transport/security sections (network:
  tcp/ws/grpc/httpupgrade/xhttp/kcp; security none/tls/reality) on the JsonObject
  lens; Mux + `dialerProxy`/`sendThrough` common group. **Import from `vless://`
  link** — local parser → outbound (vnext+users[flow/encryption] + streamSettings
  from query: type/security/sni/fp/pbk/sid/path/host/serviceName/alpn). Designed to
  extend to `vmess://`/`trojan://`/`ss://` later.
- **Phase 3 — wireguard + reverse + subscription outbounds.** wireguard full form
  (no stream settings); reverse bridge/portal over top-level `reverse.{bridges,
  portals}`; `outboundSub` (read-only imported outbounds; own endpoints).

## Hard rules / gotchas
- `freedom`/`blackhole`/`dns`/`loopback`/`wireguard` **never** carry `streamSettings`;
  proxy protocols may. On protocol change away from a proxy, strip `streamSettings`.
- Tags are referenced by `routing.rules[].outboundTag`, `balancers[].selector`,
  observatory subject lists, `dialerProxy`. v1: validate tag uniqueness on save;
  on delete/rename, **warn** about dangling refs (don't auto-rewrite).
- `vless` `encryption` stays `none`, no stream-level cipher; vmess has no `flow`.
- Multi-entry `vnext[]`/`servers[]`/`peers[]`/`users[]`: v1 edits `[0]`, preserves
  the rest; hint when size > 1.
- New outbounds **append** (don't change the existing default route at index 0).

## Reuse (no inbound-editor refactor yet — avoids unverifiable regression)
- New shared composables in `ui/components/FormFields.kt` (`Field`, `SwitchRow`,
  `LabeledDropdown`, `SectionTitle`), used by the new outbound screens only. The
  inbound editor keeps its private copies; dedupe later under emulator verification.
- Reuse `ui/components/ConfirmDialog.kt` as-is.
