# AI-assistant memory — repo copy

Snapshot of the Claude Code per-project memory from the primary dev Mac
(`~/.claude/projects/-Users-y-khachaturian-git-3x-ui/memory/`), kept in the
repo so the context is readable from any machine.

**The machine-local memory is canonical** — this copy is synced manually on
request, so it can lag. On a new machine: read these, then recreate/adjust the
machine-specific parts (JDK/SDK paths, emulator, worktree) for that machine.

| File | What it holds |
|------|---------------|
| [local-build-toolchain.md](local-build-toolchain.md) | Android/iOS/macOS-desktop build + packaging setup on the primary Apple-Silicon Mac (JDKs arm64+x64, emulator AVD, iOS simulator, create-dmg, /tmp worktree, gotchas) |
| [upstream-sync-workflow.md](upstream-sync-workflow.md) | How to update the fork to a new upstream 3x-ui release across main/android-app/ios-app |

Snapshot date: **2026-06-11** (v0.3.23 era). Current project state lives in
[../RESUME-NEXT-SESSION.md](../RESUME-NEXT-SESSION.md) and [../../CLAUDE.md](../../CLAUDE.md).
