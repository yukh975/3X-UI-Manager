# F-Droid submission runbook

Operational notes for shepherding **3X-UI Manager** (`net.yukh.xui`) into the
F-Droid catalog. Self-contained so it can be picked up from any machine (this
file lives in the repo — clone it anywhere). No secrets are stored here.

## Status

- **MR:** <https://gitlab.com/fdroid/fdroiddata/-/merge_requests/42307> — awaiting F-Droid maintainer review.
- **Target project** `fdroid/fdroiddata` id **36528**.
- **Fork** `yukh975/fdroiddata` id **84185961**, branch **`add-net-yukh-xui`**.
- **App source mirror:** <https://github.com/yukh975/3X-UI-Manager> (default branch `main`, tag `v0.8.5`).
- **App id / version:** `net.yukh.xui`, 0.8.5 / versionCode 80500.
- gitlab.com account **yukh975** (validated → CI allowed).

## Why F-Droid

Google Play is blocked (identity verification fails: the Play account country is
Serbia and can't be changed, but only RF documents are available; plus Google's
Russia sanctions). RuStore was ruled out (VK-owned). The app was open-sourced
under MIT with a dedicated `fdroid` build flavor (no in-app updater, no
`REQUEST_INSTALL_PACKAGES`) so F-Droid can build it from source.

## Prerequisite on any machine

A gitlab.com personal access token with scope `api`, saved to `~/.gl-com-token`
(chmod 600). Create one at
<https://gitlab.com/-/user_settings/personal_access_tokens>.

## Check the MR status

```bash
TOK=$(tr -d '[:space:]' < ~/.gl-com-token) python3 - <<'PY'
import os, json, urllib.request
TOK = os.environ['TOK']; GL = "https://gitlab.com/api/v4"
def api(p):
    return json.load(urllib.request.urlopen(
        urllib.request.Request(GL + p, headers={'PRIVATE-TOKEN': TOK})))
mr = api("/projects/36528/merge_requests/42307")
print("state:", mr["state"], "| merge:", mr.get("detailed_merge_status"), "| labels:", mr.get("labels"))
notes = [n for n in api("/projects/36528/merge_requests/42307/notes?per_page=100") if not n.get("system")]
print("human comments:", len(notes))
for n in notes[-5:]:
    print(" -", n["author"]["username"] + ":", n["body"][:200].replace("\n", " "))
PY
```

## Apply a maintainer-requested metadata change

Edit `metadata/net.yukh.xui.yml` (content below), validate with `fdroid lint`,
then PUT it to the fork branch — the open MR updates automatically:

```bash
TOK=$(tr -d '[:space:]' < ~/.gl-com-token) python3 - <<'PY'
import os, json, urllib.request
TOK = os.environ['TOK']; GL = "https://gitlab.com/api/v4"; FORK = 84185961; BR = "add-net-yukh-xui"
content = open("net.yukh.xui.yml").read()  # the edited metadata file
req = urllib.request.Request(
    f"{GL}/projects/{FORK}/repository/files/metadata%2Fnet.yukh.xui.yml",
    method="PUT", headers={'PRIVATE-TOKEN': TOK, 'Content-Type': 'application/json'},
    data=json.dumps({"branch": BR, "content": content,
                     "commit_message": "net.yukh.xui: address review"}).encode())
print(urllib.request.urlopen(req).status)
PY
```

To validate locally: `pip install fdroidserver` in a venv, seed a `config.yml`
whose `categories:` list comes from fdroiddata's `config/categories.yml`, then
`fdroid lint net.yukh.xui`.

## Re-trigger the fork CI

Reopening the MR does **not** start a pipeline — only a **push to the branch**
does (a metadata edit is such a push). fdroiddata's verification jobs
(`app_verification_rules`: lint/build) run on fork branches and MRs; they are not
gated to the canonical project. Inspect:
`/projects/84185961/pipelines?ref=add-net-yukh-xui` → `/pipelines/<id>/jobs`.

## Future releases

New `v*` tags on the mirror are auto-picked up by F-Droid once the app is in the
catalog (`UpdateCheckMode: Tags`, `AutoUpdateMode: Version v%v`). Keep the GitHub
mirror in sync: `git push github main --tags`.

## Current metadata (`metadata/net.yukh.xui.yml`)

```yaml
Categories:
  - Remote Access
  - VPN & Proxy
License: MIT
AuthorName: Yuriy Khachaturian
SourceCode: https://github.com/yukh975/3X-UI-Manager
IssueTracker: https://github.com/yukh975/3X-UI-Manager/issues

AutoName: 3X-UI Manager
Summary: Manage your 3x-ui / Xray proxy panels from your phone
Description: |-
    A native client for the 3x-ui panel. Connect to one or more panels with an
    API token and manage them on the go:

    * Dashboard: Xray status, system stats, client counts
    * Inbounds and clients: create, edit, enable/disable, traffic and expiry
    * Config sharing: subscription and per-client QR codes
    * Nodes: status, mTLS, updates
    * Optional local notifications when a panel port stops answering or a client is about to expire or run out of traffic

    The app talks only to the panels you point it at; it bundles no trackers and
    no proprietary libraries.

RepoType: git
Repo: https://github.com/yukh975/3X-UI-Manager.git

Builds:
  - versionName: 0.8.5
    versionCode: 80500
    commit: v0.8.5
    subdir: app
    gradle:
      - fdroid

AutoUpdateMode: Version v%v
UpdateCheckMode: Tags ^v[0-9]
CurrentVersion: 0.8.5
CurrentVersionCode: 80500
```
