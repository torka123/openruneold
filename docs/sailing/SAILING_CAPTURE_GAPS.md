# Sailing capture gaps

`BLOCKED_BY_CAPTURE` means the feature remains disabled or at its audited scaffold behavior. It must not be approximated.

| Gap | Needed evidence | Acceptance artifact |
|---|---|---|
| revision 240.5 provenance | authoritative build/subrevision/date or matching cache manifest | signed note plus full cache fingerprint |
| world-entity type 0/1/2 versus 1/2/3 | client script/protocol packet trace for all boats | decoded create/rebuild/update fixture |
| movement constants/residuals | target live heading/speed traces across angles and long travel | versioned tick CSV plus digest |
| collision and footprint rotation | capture-backed per-tile water authority plus land/water/boat/hazard contacts for raft/skiff/sloop at all angles; r240 runtime collision is proven ambiguous | revision/hash-labelled surface fixture, hull-offset fixture, tick trace, and video |
| helm/sail/reverse/wind UI | inputs, var state, scripts, speed buttons, camera | packet/interface trace |
| dynamic Sailing UIs | component/sub-index/op/script/var mapping for IF 72, 931-939, 943-944 | click matrix with before/after state |
| boat-selection IF 934 callbacks | exact open/invocation sequence for CS2 8621, generated card payloads from 8623-8630, availability 8997, and every boat/friend/previous/cargo/recovery click | client-script trace plus accepted/rejected IF_BUTTON replay matrix |
| boat selection vars | exact domains/sentinels for varbits 18553/18554 and lifecycle for 19108/19109/19122/19136/19137 | fresh/owned/deployed/recovered/logout before-and-after var capture |
| boarding permissions | exact owner, previous-boat, friend, passenger-capacity, refusal, and owner-offline policy behind loc 59832 operations | two-account operation/packet/tick capture at full capacity and reconnect boundaries |
| shipyard hotspot counts | exact skiff 4-vs-7 and sloop 8-vs-11 generic/restricted masks | decoded DB/script rows and live UI capture |
| docks/mooring | operational land return, sea spawn/anchor, approach, rotation, and in-zone encoding for all 61 rows; identity/metadata and static parent/buoy placements are cache-proven | per-dock click/var/coordinate/boat-base trace linked to the typed registry |
| full map | XTEA-aware locations/bounds for routes, hazards, activities, creatures | coordinate ledger with cache sources |
| audio/music | unnamed synth and MIDI/region triggers | sequence/script/region trace; no guessed names |
| boat lifecycle | logout/rejoin, captain/passenger disconnect, hop, teleport, capsize, death, recovery | ordered packet/database/live scenarios |
| component recipes/actions | requirements, amounts, XP, five-tick actions, paid prices, boosts, transforms | cache/script rows plus action captures |
| crew | stat matrix, eligibility, cycles, orders, pathing, XP, repair/combat/reconnect | deterministic command/activity fixtures |
| Port Tasks | generation weights, cargo/counts, cancellation, XP, guaranteed tasks, sacks | seeded assignment/reward fixtures |
| charting | post-Wyrmscraig count, coordinates/radii, tools, XP, completion, cadence/camera | complete chart-point ledger |
| salvaging | pools, depletion, respawn, hook rolls, yields, sorting, pet, full inventory | seeded pool/reward fixtures |
| Barracuda Trials | tick thresholds, objectives, checkpoints, PB/reset, rewards, HUD, observer | per-rank golden runs |
| trawling/offshore Fishing | spots, bounds, bait ownership, timeout signal, animation, routing | multiplayer capture matrix |
| ship combat | March/April formulas, cadence, ammo, Attractor, retaliation, contribution/loot/corpse pull | deterministic combat replays |
| six quests | stage vars, dialogue, cutscenes, temporary items, bespoke boats/combat/recovery | complete stage/capture ledger |
| rewards/pet | every roll, pity/eligibility, full inventory, collection log ordering | seeded reward fixtures |

## Known conflicts

1. Cache boat DB rows report raft/skiff/sloop types 0/1/2; the PR scaffold sends world-entity types 1/2/3.
2. Cache facility amount reports raft 1, skiff 7, sloop 13; current Wiki descriptions and later combat-restricted slots use differing terms/counts.
3. The former manual `Docks` coordinate scaffold mixed semantics and has been removed; static parent/buoy coordinates still do not establish travel geometry.
4. PR movement claims were not backed by committed tests; local tests now lock its output but not OSRS truth.
5. Whole-cache `SERVER` audit fails at enum 65475; `LIVE` must remain the definition authority until repaired.
6. Revision-240 production collision stores a zero mask for both known land and known open water at Port Sarim; `CollisionStrategy.Blocked` rejects sampled open water and must not be used for boats.
7. Boat DB size labels and world-entity deck rebuild zones do not prove the rotated root-world hull footprint.
8. Dock parent/buoy coordinates prove static object placement only; they do not prove disembark, spawn, anchor, approach, or rotation geometry.

## Capture protocol

For each capture:

- record UTC/local date, world/build, client/RuneLite versions, account state, boat/loadout, starting vars and coordinates;
- retain raw packets/client logs when permitted, video, screenshots, and a human-readable event timeline;
- repeat boundary cases and multiplayer observer cases;
- cross-check IDs against the frozen cache, never by visual/name similarity;
- store fixture checksum and the production/test revision that consumes it; and
- update parity, source, gameval, test, and changelog ledgers together.

## Enablement rule

A blocked row can move to `IMPLEMENTING` only after evidence identifies the exact transition. It reaches `PASS` only after implementation, automated regression, and live acceptance all agree.
