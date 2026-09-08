# Sailing test plan

## Test principles

- Deterministic inputs, clocks, seeds, and persistent state.
- Exact target-revision fixtures rather than expectations recomputed with production formulas.
- Unit, protocol, integration, persistence, multiplayer, performance, and live acceptance gates.
- Every test task must be discovered by Gradle; an unwired source directory is not coverage.
- Future/capture-blocked features have negative reachability tests.

## Phase 0 foundation gate

### Movement

Current discovered suite:

- every internal angle `0..2047` at speeds `0,64,128,192,256,320,384,448`;
- SHA-256 vector golden `a1f5aa3b...bcbf60`;
- zero speed;
- acceleration/deceleration by 64;
- shortest wrap and exact 1024 negative tie;
- 10,000-tick constant trajectory; and
- 20,000-tick seeded control stream, SHA-256 `52b9bbaf...d4af37`.

Still required: cardinal/intercardinal/boundary diagnostic rows, non-multiple target clamps, all boat types, move/update call ordering, exact protocol calls, residual accumulation after capture, 2,000 x 40 seeded trajectories, and captured OSRS golden fixtures.

### World entity

Navigation safety now has eight automated tests covering zero-mask land/water ambiguity, unknown/blocked tiles, a 448-fine-unit no-tunnelling sweep, diagonal corner clipping, unloaded zones, world bounds, revision/checksum mismatch, no state or mooring mutation on rejection, and known-clear fixture acceptance. The production surface remains empty/unknown until a complete capture fixture exists, so these tests prove fail-closed architecture rather than OSRS hull parity.

The revision-240 dock contract now verifies table 194 exposes exactly 61 typed rows with IDs 0 through 60, stable row/dock indexes, names, requirements, and sprites. The decoded SERVER map contract additionally proves 64 exact parent placements and 62 exact buoy placements, including shape, angle, layer, and coordinate indexes. Operational land/sea travel geometry remains a separate capture gate.

Verified automated foundation coverage:

- 8 engine tests for angle/coordinate behavior, update versus teleport calls, containment, slot reservation/capacity/reuse, and lookup;
- 5 registry tests for add/delete success and failure lifecycle; and
- 2 heading-handler tests covering all legal headings `0..15` and rejecting every tested out-of-range value.

Still required:

- angle masking and tile-to-fine boundaries;
- coordinate update versus teleport protocol flags;
- southwest inclusive/northeast exclusive deck containment on every level;
- slot 0 reserved, capacity exhaustion, delete/reuse, overlapping-footprint invariant;
- registry create/delete exactly once and all failure modes;
- avatar allocate/associate/release;
- rebuild ordering and persistent/transient/private deck-zone replay;
- root-view recenter while real instance coordinate remains unchanged;
- malformed heading and loc-level inputs rejected.

### Lifecycle/security

Verified automated foundation coverage: stale mooring is cleared on departure and first movement; only the owning helmsman can release helm state. Runtime hooks release a separated helmsman after coordinate changes and release helm ownership before logout persistence without writing to the closing client. The native teleport hook now releases authority before all external teleports, including exempt reserved-region movement, while retaining same-deck movement.

The runtime registry now also verifies one live lease per checked character/ownership-slot boat ID, owner, and world-entity slot; one boat membership per player; player-capacity enforcement; owner-and-member-only central helm claims; automatic helm release on leave; idempotent leave/despawn; rollback when owned spawn cannot acquire its lease; and OSRS five-slot level thresholds.

The current Sailing module suite contains 46 passing tests and zero failures. It includes durable-character identity, checked owned/debug boat identity, central helm claim/release, movement goldens, swept navigation safety, the typed 61-row dock registry, the full 64-parent/62-buoy SERVER-map contract, exact interaction-coordinate indexes, transactional despawn, revision-240 cache contracts, ownership mutation, reconnect sanitization, and the embedded-PostgreSQL V30 round trip.
The API-player unit suite now contains 4 passing tests and zero failures. Its cache-backed native Sailing contract proves stat ID 23, min/max/unreleased metadata, level 30 at 13,363 XP, 200m cap, pending stat-update bit 23, exact `UpdateStatV2` fields, typed current/base accessors, visible boost/drain/heal behavior, and released-skill mastery eligibility. The API-player and Sailing cache suites run separately to avoid cross-process singleton cache initialization.


Durable owned-boat coverage now includes immutable/sorted snapshots, add/replace/remove/swap/clear mutation semantics, one autosave hint per material change, strict malformed/future-row rejection, and a real embedded-PostgreSQL round trip through migration V30 and the account pipeline. The round trip proves exact symbolic keys, ordered load, new-player apply, and fail-closed unknown-key handling.

The revision-240 cache contract verifies base varp 5056 exists in the SERVER cache with temporary lifetime and always-transmit behavior before exercising reconnect state cleanup.

These checks do not prove full passenger recovery or exact OSRS moving-boat logout semantics. Regular-player gameplay remains disabled.

Still required:

- one helmsman across every interaction/lifecycle path;
- captain/passenger capacity and permissions;
- board/disembark/dock/despawn/teleport/logout/disconnect/hop/death/capsize/server restart;
- owner disconnect with passengers and passenger disconnect during motion;
- idempotent cleanup/recovery and no cargo/facility/crew duplication;
- packet rate limits, stale aggregate version, invalid interface/op/sub-index, off-deck target.

## Feature suites

| Suite | Required coverage |
|---|---|
| Native skill | XP/levels/boosts/unlocks/guide/cape/max cape/pet eligibility |
| Ownership/shipyard | slot unlocks, buy/build/replace/remove/repair/recover, recipes, boosts, rollback |
| Components/facilities | every definition, requirements, transforms, active/support rule, reorder, bottle |
| UI | every component/op/script/var, invalid state, reopen/rejoin restoration, fixed/resizable layouts |
| Ports | every live dock/mooring, gates, approach/rotation, congestion, summon/recover |
| Quests | every stage, branch, item, cutscene, failure, death/capsize, logout/rejoin, reward idempotency |
| Courier/Bounty | seeded generation, cancel, cargo/body parts, contribution, turn-in, XP/reward/sacks |
| Charting | every point and completion award, filter/log, one-time persistence, radius/orientation |
| Salvaging | pools, success, depletion/respawn, sorting, cargo/full inventory, rolls and pet |
| Trials | every rank/checkpoint/objective/time/PB/reward/reset/failure, spectators and disconnect |
| Crew | all roster/stats/capacity/orders/facilities/pathing/XP/combat/repair/reconnect |
| Combat | all creatures/cannons/ammo/effects/prayers/gear/formulas/loot/contribution/retaliation |
| Fishing | trawling tiers/bait/timeout and every offshore method/boat/tool/quest/cargo case |
| Islands | every cross-skill node/activity/transport/shop/drop/quest gate |
| Rewards | every unique, schematic, paint, pearl, sack, pet, cape, collection-log entry |

## Scaling and multiplayer

Test 1, 2, 5, and maximum-capacity passengers for each class; two nearby boats; observer join/leave mid-motion; owner and non-owner facilities; same-tick competing actions; full world-entity slot pressure; crowded ports; and independent per-player bait/task/UI state.

## Persistence/rollback

Owned-boat V30 migration and account-pipeline save/load now have a PostgreSQL round-trip test, including old-account empty defaults and unknown symbolic-key rejection. Still inject failure before transaction, after database commit/before output, during output, during autosave, and during restart recovery. Replaying the same operation token must produce one result. Verify forward compatibility for later aggregate formats and rollback for all inventory/cargo/boat cross-aggregate operations.

## Performance

Use synthetic fleets at 1, 32, 256, 1,024, and protocol capacity. Measure p50/p95/p99 tick time, allocations, spatial queries, observer fanout, rebuild size, and persistence backlog. Correctness fixtures must remain identical under load.

## Build and acceptance sequence

1. Focused module tests.
2. Engine/registry/net/game-process tests with nonzero discovery counts.
3. Server compile and broader repository `test`.
4. Record unrelated baseline failures separately; no Sailing regression may be waived.
5. Controlled build/install using canonical Java/cache paths.
6. Restart 1, verify custom marker and doctor, run smoke route.
7. Restart 2, verify persistence/recovery, custom marker and doctor, repeat route.
8. Live multiplayer acceptance from ownership through rewards for each completed feature group.

No row is `PASS` until its automated and live columns both pass.
