# Sailing baseline

Audit date: 2026-08-28 (America/New_York)

This document freezes the starting point for the OSRS Sailing parity project. It is an evidence record, not a completion claim.

## Repository

- Canonical tree: `<repo-root>`
- Working branch: `codex/sailing-parity-r240`
- Starting commit: `d3f10edd1a754b045d9fd9f7710775161b1c047b`
- Upstream observed at audit: `1c60a15d54a06b960d8c51da960988e48a4c5de0`; the starting commit was 40 commits behind.
- Pre-existing dirty tree: 1,189 entries (900 modified, 3 deleted, 286 untracked). All unrelated work is user-owned and must be preserved.
- Repository authority: this tree, `.data/postgres`, `.data/cache/SERVER`, and `.data/cache/LIVE` as defined by root `AGENTS.md`.

## Runtime and cache

- Configured cache revision: 240.
- Revision 240 is independently verified from `game.yml`, generated manifests, the rsprot dependency, and successful `LIVE` decoding.
- Requested target label: revision 240 subrevision 5, dated 2026-08-26.
- Subrevision 5 is not independently encoded in inspected local metadata and remains `BLOCKED_BY_CAPTURE` for provenance.
- Active server cache: `.data/cache/SERVER`.
- Client/JS5 and exact stock-definition source: `.data/cache/LIVE`.
- `LIVE` dat2 SHA-256: `1DB537DDC1266CD81D41D17C26BC041B4D149A4C1155DF9539B7E7F29AA31A3B`.
- `SERVER` dat2 SHA-256: `76025F9CF927277E76CB648F9C2F91159755DFAEBA8266A5172876D6ACAED3E5`.
- Whole-cache `LIVE` decoding passes. Whole-cache `SERVER` indexing fails on injected enum 65475 and must not be used as proof that Sailing data is absent.
- Gradle/runtime toolchain: Java 21. The interactive shell may expose Java 25, but it is not the target runtime.

## Source foundation

OpenRune PR #182 (`MouldyToast/feature/sailing-helm`) was audited at head `3026daead89aba4a41463de781d14a6973e8ed53`. It is an open, draft, dirty PR and was not merged or cherry-picked wholesale.

Relevant commits used as provenance:

1. `910a6a5e624397bd45d05d8e091fae155c6857a6` - world-entity engine support.
2. `cda27866eae8acd4b0dca85bcde66d1d1bbdff88` - Sailing module, boat lifecycle, docks, gangplanks.
3. `4c61b71729718e920aa97635eb2bd3012c109ece` - kinematics, helm input, deck furnishing.

The unrelated bank/admin commit and five merge commits were excluded. New source files were imported selectively; nine shared dirty files were reconciled manually against local systems.

## Integrated foundation at this checkpoint

- Engine `WorldEntity`, list, info protocol, state events, and registry.
- Network avatar lifecycle, rebuild/update plumbing, deck-zone replay, aboard root-view handling, and loc click level resolution.
- Aboard build-area and loc interaction handling.
- `SetHeading` packet registration hardened to accept only headings 0 through 15.
- Sailing module with in-memory raft/skiff/sloop definitions, dock table, boat manager, gangplank, helm, deck locs, and debug commands.
- Movement kernel preserving imported ordering and quantization.
- Discovered Sailing unit tests covering 2,048 headings x 8 reachable speeds, zero speed, acceleration, deceleration, wraparound, the half-turn tie, 10,000-tick travel, and a 20,000-tick seeded control stream.

This is only an engine/helm scaffold. It does not implement the native skill, ownership, persistence, side panel, full collision, quests, tasks, charting, trials, crew, combat, fishing, islands, rewards, or 1-99 progression.

## Baseline build evidence

- Repository `test` before Sailing integration: pass, 531 actionable tasks.
- Aggregate `build` before integration: blocked by seven pre-existing Spotless failures in unrelated modules (`invtx`, `music-plugin`, `music`, `obj-plugin`, `realm-config`, `obj-charges`, `player-output`).
- Integrated compile gate: `:server:app:compileKotlin :api:game-process:test :content:skills:sailing:compileKotlin` passed.
- Focused Sailing movement suite: passed, 6 tests.
- The pre-existing `api:game-process/src/integration` directory is not wired to a Gradle integration-test task and is not counted as passing coverage.

## Frozen movement behavior

The imported algorithm accelerates/decelerates by 64 fine units, turns by at most 128 of 2,048 angle units per tick, turns before moving, and rounds each tick to a 32-fine-unit vector. It currently discards sub-32 residuals.

- Exhaustive vector SHA-256: `a1f5aa3b0a662f48a477687be9e0facda58249bbab425bb8c231a845f9bcbf60`.
- Seeded 20,000-tick state SHA-256: `52b9bbaf91f79bda84263a0659b69c98d9c74dffb7d17f6141785e675bd4af37`.
- These values prove deterministic regression behavior only. They do not prove OSRS parity.
- Exact fractional accumulation, speed constants, and turn rules remain `BLOCKED_BY_CAPTURE` until target-revision evidence exists.

## Live deployment gate

Do not call this foundation verified live until:

1. lifecycle and collision invariants pass automated tests;
2. teleport, logout, disconnect, death, passenger, and despawn cleanup cannot duplicate or strand state;
3. a controlled rebuild succeeds for the affected modules;
4. the server displays `OPENRUNE CUSTOM BUILD ACTIVE`;
5. `scripts/openrune-doctor.ps1` passes after each of two controlled restarts; and
6. an in-game smoke test proves create, board, helm, observe, stop, disembark, and cleanup.

## Status

- PR integration: `IMPLEMENTING`.
- World-entity engine: `IMPLEMENTING`.
- Movement regression lock: `PASS` for imported behavior, `BLOCKED_BY_CAPTURE` for OSRS parity.
- Full Sailing parity: `NOT_STARTED`/`IMPLEMENTING` by row in `SAILING_PARITY_MATRIX.md`.
- Production/live enablement: `DISABLED` until the deployment gate passes.
