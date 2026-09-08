# Sailing implementation changelog

## 2026-08-28 - Phase 0 audit and selective foundation import

### Repository/evidence

- Created branch `codex/sailing-parity-r240` from `d3f10edd1a754b045d9fd9f7710775161b1c047b` without resetting the dirty tree.
- Ran the required repository doctor and recorded canonical runtime/cache/JVM details.
- Verified revision 240; left subrevision 5 as unproven.
- Decoded stock `LIVE` cache and inventoried 7,450 Sailing-named mappings, including 63 DB tables and 15 interfaces.
- Identified customized `SERVER` whole-cache decoding blocker at enum 65475.
- Audited PR #182 at `3026daead`; selected three relevant commits and excluded unrelated/merge history.
- Recorded live scope through the 2026-08-26 update and explicit future-disabled content.

### Engine/network

- Added the world-entity model/list/info protocol/events and registry.
- Integrated avatar create/delete, rebuild, zone replay, observer filtering, aboard view/build-area handling, and loc click-level resolution.
- Preserved local friend/clan network systems, custom animation handling, and private-location visibility while reconciling dirty shared files.
- Added root-view state to `Player` and bound `WorldEntityList` in the server module.
- Registered `SetHeading`; rejected headings outside 0 through 15.

### Sailing scaffold

- Added the Sailing module with boat manager/types, raft/skiff/sloop templates, dock/deck tables, gangplank, helm, kinematics, tick script, vars/modes, and diagnostic commands.
- Kept the scaffold classified as incomplete and not production-enabled.
- Identified hard-coded boat/dock/deck data for migration to revision-240 DB definitions.

### Tests

- Extracted the imported kinematics math behind an internal deterministic kernel without changing output.
- Added a discovered six-test module suite covering 16,384 angle/speed vectors, zero speed, acceleration/deceleration, turn wrap/tie, 10,000-tick travel, and a 20,000-tick seeded stream.
- Locked vector SHA-256 `a1f5aa3b0a662f48a477687be9e0facda58249bbab425bb8c231a845f9bcbf60`.
- Locked seeded trajectory SHA-256 `52b9bbaf91f79bda84263a0659b69c98d9c74dffb7d17f6141785e675bd4af37`.
- Confirmed the prior game-process `src/integration` directory is not wired and does not count as test coverage.

### Verification

- Baseline repository `test` passed before integration.
- Baseline aggregate `build` was blocked only by seven documented unrelated Spotless failures.
- Integrated server/game-process/Sailing compile gate passed.
- Focused Sailing test suite passed.
- Live server was deliberately not restarted at this checkpoint because collision and lifecycle safety gates are not yet satisfied.

### Open blockers

- Fractional movement, exact speed/turn constants, collision/hazards, world-entity type mapping.
- All ownership/persistence/recovery, full boarding/passenger authority, native UIs, ports, shipyard, facilities.
- Native skill/XP, six quests, tasks, charting, salvaging, trials, crew, combat, fishing, islands, rewards, and post-launch deltas.
- World-entity/protocol/lifecycle test matrix, multiplayer tests, performance baselines, two-restart live verification.

### Documentation

Created the required baseline, architecture, parity, source, gameval, content, revision, capture, test, performance, persistence, and changelog ledgers under `docs/sailing/`.

## 2026-08-28 - Foundation hardening and first live restart

### Release safety

- Added a fail-closed release gate with regular-player Sailing gameplay disabled.
- Left administrator-only boat diagnostics available for controlled engine testing.
- Kept collision, ownership, persistence, capacity, and capture-dependent content unreachable.

### Lifecycle

- Cleared dock occupancy as soon as a boat departs or begins a movement tick.
- Made helm release follow the owning player rather than depend on the player's current tile.
- Added coordinate-change cleanup for external teleports and packet-free cleanup before logout persistence.
- Moving-boat restore, passenger recovery, capsize, and crash recovery remain `BLOCKED_BY_CAPTURE` and disabled.

### Automated verification

- Sailing: 10 tests passed, including three new departure and helm-ownership tests.
- Engine world entities: 8 tests passed.
- World-entity registry: 5 tests passed.
- Heading packet handler: 2 tests passed.

### Live verification

- Restart 1 bound game port 43594, HTTP 8080, and world-link 9091.
- Verified the custom-build marker and 389 loaded scripts.
- The repository doctor completed successfully after restart.
- This restart proves startup health only; regular-player Sailing remains gated and no OSRS parity row is promoted by it.

## 2026-08-28 - Lifecycle verification and revision-240 cache contract

### Controlled restart 2

- Rebuilt the focused Sailing, engine, registry, and network suites plus the server compile gate.
- Restarted the canonical game process a second time after lifecycle hardening.
- Verified game port 43594, HTTP 8080, world-link 9091, the custom-build marker, 390 loaded scripts, an empty error log, and a successful repository doctor run.
- Observed one 714 ms startup-only cycle warning; no sustained runtime warning was observed.
- This is startup/lifecycle deployment evidence, not player-facing Sailing acceptance. The fail-closed release gate remains active.

### Revision-240 contracts

- Added an executable contract for stat 23, stats component 320:24, native Sailing interfaces 934/935/936/937/938/939/943/944, core varbits 19104/19105, and boat rows 8110/8111/8112.
- Bound raft, skiff, and sloop to explicit cache type IDs 0/1/2 and symbolic DB rows rather than enum order.
- Confirmed OSRS/LIVE crew slots are 0/2/5. Raw column 6 is `boat_size` (1/2/3); generated `crew_capacity` correctly resolves column 36 (0/2/5).
- Kept the customized SERVER whole-cache enum 65475 issue recorded separately; it is not bypassed or normalized.

### Automated verification

- Sailing suite: 11 tests after adding the cache contract.
- Engine world entities: 8 tests.
- World-entity registry: 5 tests.
- Heading packet handler: 2 tests.

## 2026-08-28 - Cache-backed boat definitions and ownership persistence

### Runtime definitions and leases

- Added a fail-fast revision-240 `SailingBoatDefinitionRegistry` for raft, skiff, and sloop. Runtime type, cache type, symbolic DB row, and numeric row ID are explicit crosswalks; enum order is not an identifier.
- Routed owned-boat spawn through the verified cache definition and its player capacity.
- Added stable runtime boat IDs and indexes enforcing one live lease per boat, owner, and world-entity slot, plus one boat membership per player.
- Added OSRS owned-slot thresholds at Sailing levels 1, 15, 50, 78, and 91.
- Made despawn transactional: local passengers, protection, and runtime leases are released only after the authoritative world-entity deletion succeeds.

### Durable ownership

- Added migration `V30__character_sailing_boats.sql` with five normalized character slots and account-character cascade cleanup.
- Persisted exact symbolic DB-row keys such as `dbrow.sailing_boat_raft`; numeric cache IDs and enum ordinals are never durable identity.
- Added strict load/save validation for slot bounds, duplicate slots, known boat types, and format version 1. Unknown or future rows fail closed instead of being rewritten silently.
- Added immutable, atomically replaced in-memory ownership snapshots and mutation services for add, replace, remove, swap, and clear.
- Integrated the ownership pipeline with the existing account transaction and autosave hint systems.
- Separated durable ownership from temporary aboard/helm state. Login/logout cleanup clears only the temporary state.

### Revision-240 runtime varp repair

- Added client base varp 5056 to the SERVER cache as `Temp` and `OnSetAlways`.
- This makes native varbits 19104 (`sailing_player_is_on_player_boat`) and 19105 (`sailing_boat_facility_lockedin`) safe to mutate at runtime; before this repair, setting either varbit threw because its base varp was missing from the server definition cache.
- Rebuilt the revision-240 LIVE and SERVER caches and added an executable cache contract for the varp lifetime/transmit policy.

### Automated verification

- Sailing: 30 tests passed, including runtime leases/capacity, transactional despawn, cache definitions, ownership mutations, reconnect sanitization, and an embedded-PostgreSQL V30 save/load round trip.
- Engine: 10 tests passed.
- Registry: 5 tests passed.
- Network: 23 tests passed.
- Full server Kotlin compilation passed.
- Total focused gate: 68 tests, zero failures.

### Remaining release state

- Regular-player Sailing remains fail-closed.
- Ownership UI, shipyard purchase/build operations, full passenger authority/reconciliation, recovery, collision, quests, training, and live content remain incomplete.

### Controlled post-change restarts

- Restart 1 applied Flyway migration V30 exactly once, bound ports 43594/8080/9091 on PID 17688, reported revision 240 and the canonical SERVER cache, printed `OPENRUNE CUSTOM BUILD ACTIVE`, loaded 390 scripts, and reached ready in 22.379 seconds. The doctor passed.
- Restart 2 found zero pending migrations, bound the same three ports on PID 23904, reported the same revision/cache/custom marker, loaded 390 scripts, and reached ready in 21.300 seconds. The final doctor passed and PID 23904 was left running.
- Both stderr files remained empty. Each boot logged one startup-only long-cycle warning (738 ms and 766 ms respectively); neither showed a continuing runtime failure.
- This proves build, migration, cache, dependency-injection, and startup health for the ownership slice. It is not player-facing gameplay acceptance, and the regular-player release gate remains closed.

## 2026-08-28 - Captain authority and teleport-safe membership

### Runtime authority

- Replaced global debug-boat state with a temporary per-character attribute. Two administrators can no longer move, board, or delete each other's diagnostic boat through shared plugin state.
- Made all diagnostic boats acquire the same owner/world-entity/runtime lease used by production deployment; direct varbit-only boarding was removed.
- Centralized helm claims in `BoatRuntimeRegistry`. A character must be the leased boat owner and an active occupant before a helm claim succeeds; leave, logout, external teleport, or despawn releases the claim.
- Helm steering now requires the active central claim on every heading event. Stale local/lease disagreement fails closed and has an idempotent recovery path.
- Added a checked `(character_id, ownership_slot)` boat-ID encoder. Production dock deployment now accepts a character and persisted ownership slot, resolves the symbolic boat row internally, and enforces slot and boat-type Sailing levels before spawning.

### Boarding and lifecycle

- Bound a native `PlayerTeleportHook`, covering exempt and reserved-region teleports that the normal coordinate-change event does not publish.
- Teleports within the same leased deck preserve membership; all other teleports release helm and membership before movement commits. The coordinate hook remains an idempotent fallback.
- Owner boarding reserves runtime capacity before movement and rolls back if movement output throws. Friend/previous/passenger policy remains disabled pending capture.
- Disembarking now resolves the current leased boat's recorded dock instead of choosing a nearby root-world gangplank from reserved-region coordinates.
- Rejected a second boat at an occupied dock, eliminating nondeterministic boarding selection.

### Revision-240 cache audit

- Verified generic embark loc 59832 operations `Board`, `Board-previous`, and `Board-friend`; disembark loc 59831; dock-parent transforms 59835 through 59866; Wyrmscraig parents 62397/62398; and Shipyard parent 59719 with children 59721/59720.
- Verified boat-selection interface 934 has 34 components and dynamic CS2-generated cards. Cache alone does not prove its server button payloads or open sequence, so the UI remains `BLOCKED_BY_CAPTURE`.
- Verified selection/aboard/dock var layouts, including varbits 18553, 18554, 19104, 19108, 19109, 19122, 19136, 19137, 19145, 19146, and 19147.
- Verified client boat DB type IDs are raft/skiff/sloop 0/1/2 and remain explicitly separate from imported world-entity wire types 1/2/3.

### Automated verification

- Sailing suite: 36 tests, zero failures, including central helm claims, automatic release on leave, durable character identity, checked owned-boat identity, ownership persistence, cache contracts, and movement goldens.
- Focused server Kotlin compilation passed after the authority integration.
- Regular-player Sailing remains fail-closed; passenger permissions and dynamic interface operations are not guessed.

### Controlled deployment verification

- The cross-module gate passed 74 tests: Sailing 36, engine 10, registry 5, and network 23, plus Sailing Spotless and full server Kotlin compilation.
- Restart 1 bound ports 43594/8080/9091 on PID 61004, applied zero pending migrations, reported revision 240 and the canonical SERVER cache, printed `OPENRUNE CUSTOM BUILD ACTIVE`, loaded 390 scripts, reached ready in 22.108 seconds, and passed the repository doctor.
- Restart 2 bound the same ports on PID 50352, again applied zero pending migrations, reported the same revision/cache/custom marker, loaded 390 scripts, reached ready in 21.764 seconds, and passed the final repository doctor.
- Both stderr logs are empty. Each restart recorded one startup-only long-cycle warning (800 ms and 855 ms); no sustained runtime error was observed.
- PID 50352 is the final live server process. This verifies deployment/startup health for the captain-authority slice, not live player parity; the regular-player release gate remains closed.

## 2026-08-28 - Fail-closed navigation and typed dock definitions

### Root-world navigation audit

- Probed the production revision-240 map/collision data at Port Sarim, Hosidius, Port Piscarilius, and the Pandemonium.
- Proved that `CollisionStrategy.Blocked` is not a Sailing-water classifier: known open water has no `BLOCK_WALK`, and at Port Sarim known land and known open water both have a zero collision mask.
- Kept exact water classification and rotated raft/skiff/sloop hull footprints `BLOCKED_BY_CAPTURE`; no underlay, broad rectangle, nominal boat-size, or deck-zone guess was introduced.
- Added an immutable revision/hash-labelled `SailableTileMap` seam. Its production revision-240 implementation classifies uncaptured tiles as unknown and therefore denies movement.

### Movement safety

- Split movement into a pure intent followed by validation and a single commit. Rejected movement no longer changes speed, angle, coordinates, or mooring state.
- Added complete centre-path sampling at 32 fine-unit intervals, normal static collision checks for every crossed tile transition, diagonal corner-clipping rejection, unloaded-zone rejection, and pre-packing world-bound checks.
- A maximum-speed 448-fine-unit tick can no longer tunnel through one-, two-, or three-tile blockers.
- Existing clear-path vector and long-trajectory goldens remain unchanged. Exact hull contact and native collision response remain capture-blocked, so the player release gate stays closed.

### Revision-240 docks and native stat

- Added `SailingDockDefinitionRegistry` directly over the generated `SailingDockRow`; no duplicate DB accessor or enum-order bridge was created.
- The registry fails fast unless table 194 contains exactly 61 definitions with dock IDs 0 through 60, unique dock/row identity, valid names, level requirements, optional quest rows, and sprite IDs.
- Kept operational land return, sea spawn, anchor, approach, rotation, and in-zone geometry separate and capture-blocked. Static parent-loc placement is not treated as a navigation destination.
- Added shared `sailingLvl` and `baseSailingLvl` player stat properties and routed owned-boat level checks through the native stat-23 accessor.
- The cache audit identified 32 regular/Wyrmscraig gangplank definitions, 27 dock moorings, 64 parent placements, and 62 buoy placements. Exact interaction indexing remains the next registry migration slice.

### Automated verification

- Sailing suite: 45 tests, zero failures, including eight navigation safety tests and the 61-row dock registry contract.
- Engine: 10 tests, registry: 5 tests, network: 23 tests.
- Cross-module gate: 83 tests, zero failures, plus Sailing Spotless and full server Kotlin compilation.
- The two cache-backed Sailing tests share a resource lock so the global cache manager is never initialized concurrently.
- Regular-player Sailing remains fail-closed; no collision, dock travel, UI, quest, training, or reward parity claim is made by this foundation slice.

### Controlled deployment verification

- Restart 1 bound ports 43594/8080/9091 on PID 11996, applied zero pending migrations, reported revision 240 and the canonical SERVER cache, printed `OPENRUNE CUSTOM BUILD ACTIVE`, loaded 390 scripts, reached ready in 17.264 seconds, and passed the repository doctor.
- Restart 2 bound the same ports on PID 58232, again applied zero pending migrations, reported the same revision/cache/custom marker, loaded 390 scripts, reached ready in 17.902 seconds, and passed the final repository doctor.
- Restart logs are `logs/sailing-navigation-restart1-20260828-164951.out.log` and `logs/sailing-navigation-restart2-20260828-165356.out.log`; both matching stderr logs are empty.
- Each boot recorded one startup-only long-cycle warning (733 ms and 752 ms); no sustained runtime error was observed.
- PID 58232 is the final live server process. This verifies deployment/startup health for the navigation/dock-definition slice, not player-facing Sailing parity; the regular-player release gate remains closed.

## 2026-08-28 - Authoritative dock placements and exact interaction authority

### Registry and migration

- Extended all 61 revision-240 dock definitions with explicit symbolic row, parent-loc, buoy-loc, and gangplank/mooring interaction identity. Parent transforms, dimensions, varbit 19104, and child families are validated without relying on ranges or enum order.
- Added immutable `SailingDockRegistry` startup authority over the decoded SERVER map. It validates and indexes 64 exact parent placements and 62 exact buoy placements, preserving coordinate, level, shape, and angle; ambiguous exact coordinates fail fast.
- Registered both gangplank and mooring embark/disembark child families. Interaction resolves the exact clicked static coordinate and never chooses a nearest dock.
- Removed the old 32-entry manual `Docks` catalog and nearest-within-eight lookup. Boats now retain stable dock IDs, and deployment/boarding rejects unknown IDs, locked levels, unavailable operational geometry, and duplicate occupancy with typed outcomes.
- Added `SailingDockOperationalGeometry`: every current row is deliberately `UNCERTIFIED` and names the missing land return, sea spawn, sea anchor, boat rotation, and in-zone fields. Static map placement is never promoted into guessed travel geometry.

### Verification

- Sailing suite: 46 tests, zero failures, including the full revision-240 map-placement and exact-index contract.
- Engine: 10 tests, registry: 5 tests, network: 23 tests; cross-module gate: 84 tests, zero failures, plus Sailing Spotless and full server Kotlin compilation.
- Restart 1 bound ports 43594/8080/9091 on PID 48448, reported revision 240 and the canonical SERVER cache, printed the custom-build marker, loaded 390 scripts, applied zero pending migrations, reached ready in 18.177 seconds, and passed the repository doctor.
- Restart 2 bound the same ports on PID 18952 with the same revision/cache/marker/script/migration checks, reached ready in 18.093 seconds, and passed the final repository doctor. Logs are `logs/sailing-docks-restart1-20260828-171641.out.log` and `logs/sailing-docks-restart2-20260828-171811.out.log`; both matching stderr logs are empty.
- Each boot recorded one startup-only long-cycle warning (798 ms and 770 ms); no sustained runtime error was observed. PID 18952 remains the live server process.
- This proves startup and deployment health for the static dock-authority slice only. Operational dock travel and normal-player Sailing remain fail-closed behind `SailingReleaseGate.playerGameplayEnabled = false`.

## 2026-08-28 - Native stat and released-skill mastery foundation

### Native skill integration

- Added cache-backed `ReleasedSkillMastery` as the single released-stat eligibility authority for future skillcape and Max cape content. It derives the active skill set from SERVER metadata, excludes unreleased Sailing today, and will include Sailing automatically when its cache release flag is deliberately enabled.
- Extended the revision-240 Sailing cache contract through generated `StatComponentsRow`: `dbrow.sailing_stat` resolves native stat 23, packed component 320:24, and guide bit 24.
- Added runnable API-player coverage for level 30 at the standard 13,363 XP threshold, the 200m cap, pending update bit 23, exact `UpdateStatV2` fields, typed current/base Sailing accessors, native visible boost/drain/heal operations, and released-skill mastery success/failure.
- Made cache-backed API-player tests deterministic by serializing the shared cache resource and changing the existing blowpipe test from the ambiguous default loader to the canonical revision-240 SERVER cache.
- The repository's dormant `src/integration` convention references a nonexistent `:api:testing` project, so no result from that source set is claimed. The new Sailing contract lives in the active `src/test` suite and is counted from its XML result.

### Verification

- API-player: 4 tests, zero failures; Sailing: 46; engine: 10; registry: 5; network: 23. The combined scoped gate is 88 tests with zero failures.
- API-player and Sailing cache suites passed sequentially; API-player/Sailing Spotless, the engine/registry/network suites, and full server Kotlin compilation all passed.
- The regular-player and cache release gates remain closed. This slice does not claim Sailing activities, XP producers, skill guide visuals, cape/hood items, emote assets, or player-facing Sailing are complete.

### Controlled deployment verification

- Restart 1 bound ports 43594/8080/9091 on PID 48924, applied zero pending migrations, reported revision 240 and the canonical SERVER cache, printed the custom-build marker, loaded 390 scripts, reached ready in 18.222 seconds, and passed the repository doctor.
- Restart 2 bound the same ports on PID 50012 with the same revision/cache/marker/script/migration checks, reached ready in 18.722 seconds, and passed the final repository doctor.
- Logs are `logs/sailing-native-restart1-20260828-174634.out.log` and `logs/sailing-native-restart2-20260828-174753.out.log`; both matching stderr logs are empty.
- Each boot recorded one startup-only long-cycle warning (771 ms and 807 ms); no sustained runtime error was observed. PID 50012 remains the live server process.

## 2026-08-28 - RSProx protocol crosswalk and native helm/side-panel state

### Packet and capture evidence

- Downloaded and fingerprinted the requested RSProx captures 381/r231, 3132/r232, 672/r229, and 1016+4306/r235, plus Port Sarim capture 1009/r235. All remain research artifacts outside the runtime repository.
- Transcribed Port Sarim board/helm/movement timelines and live salvaging state without decoding captures under the wrong revision.
- Verified r240 `SET_HEADING`, `SET_ACTIVE_WORLD_V2`, `WORLDENTITY_INFO_V7`, and `REBUILD_WORLDENTITY_V4` layouts against current RSProx and RSProt source.
- Upgraded OpenRune's r240 RSProt dependency from `20260804` to `20260825`, which fixes the high-resolution world-entity extended-info flag while preserving the low-to-high addition transform.
- Confirmed the public RSProx database topped out at revision 239 during this audit. No public revision-240 Port Sarim launch-return/docking capture exists.

### Runtime hardening

- Added OpenRune-side world-entity allocation bounds for IDs, dimensions, zones, levels, fine X/Z coordinates, and slots before RSProt avatar allocation. Invalid values now fail closed.
- Replaced the silent world-entity exception sink with structured logging.
- Corrected helm input so the first accepted heading is applied instead of being discarded.
- Added explicit native start-heading policies for first-only, always, and never, with deterministic tests.
- Mapped revision-240 native Sailing side-panel, helm, movement, sail-toggle, dirty-pulse, boat-type, and settings vars by symbolic gameval name.
- Added the captured state-before-open side-panel lifecycle on the symbolic `side0` overlay, exact switch-button event mask, combat-tab restore, and transient-state cleanup across board/disembark/login/logout.
- Added revision-240 raft/skiff/sloop helm and primary-sail sequence mappings and synchronized the captured helm-grab/full-sail/release transitions to the observed tick order.
- Rebuilt the revision-240 caches. LIVE dat2 SHA-256 is `37B6679BA2310276FD8D26A3611D8626251D436005359149D4139AFC2D5E8D02`; SERVER is `DA5692E7017805D5B8289DBD402EC7DE79FE35A9E138B1FB1EE09E9336478268`.
- Verified server varps 5056, 5111, 5117, 5118, 5119, and 5121 are present as `Temp` and `OnSetAlways` after the rebuild.

### Safe boundary

- Kept `Revision240SailableTileMap` fail-closed and kept `SailingReleaseGate.playerGameplayEnabled = false`.
- Capture 1009/r235 proves the same r240 Port Sarim gangplank identity, a historical raft spawn, helm entry, and a positive outbound centerline. It does not prove r240 spawn, return, docking radius/snap, shoreline rejection, or rotated hull footprints.
- Boat selection IF 934 remains gated because exact r240 dynamic-card button payloads/open ordering are missing.
- Side-panel dynamic facility/crew click subindices remain gated; only the cache-proven switch-button control is advertised.
- Salvaging and all later activities remain behind the Port Sarim acceptance gate as required.

### Automated verification

- Sailing: 51 tests, zero failures.
- World-entity registry: 7 tests, zero failures.
- Network: 23 tests, zero failures.
- Focused cross-module gate: 81 tests, zero failures, plus Sailing Spotless and full server Kotlin compilation.

### Controlled deployment verification

- Restart 1 bound ports 43594/8080/9091 on PID 38892, applied zero pending migrations, reported revision 240 and the canonical SERVER cache, printed the custom-build marker, loaded 391 scripts, reached ready in 18.307 seconds, and passed the scoped repository doctor.
- Restart 2 bound the same ports on PID 23040 with the same revision/cache/marker/script/migration checks, reached ready in 17.898 seconds, and passed the final scoped repository doctor.
- Logs are `logs/sailing-rsprox-restart1-20260828-1955.out.log` and `logs/sailing-rsprox-restart2-20260828-2000.out.log`. Restart 1's stderr contains only Gradle's expected non-zero wrapper after its game JVM was intentionally stopped for restart 2; restart 2's stderr is empty.
- Each boot recorded one startup-only long-cycle warning (808 ms and 778 ms); no sustained runtime error was observed.
- PID 23040 is the final live server process. This proves deployment/startup health for the packet/UI/helm infrastructure slice, not the command-free Port Sarim acceptance loop.
