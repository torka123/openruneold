# Chambers of Xeric implementation and audit report

Generated 2026-08-16 for the canonical OpenRune revision-240 workspace.

## Status

The Chambers module is integrated, compiles, passes its focused tests, passes the cache audit, and survives two controlled live server restarts. It provides an isolated end-to-end raid framework, normal and Challenge layouts, room progression, party management, points/deaths, raid supplies, a stock points overlay, explicit Great Olm phases and damaging specials, reward generation/claiming, and administrator test commands.

This report does **not** claim complete 1:1 OSRS gameplay parity. The exact remaining discrepancies are listed below. In particular, most pre-Olm bosses still use generic combat profiles and several puzzle/skilling rooms use reduced interactions instead of their complete OSRS mechanics. A live in-client raid completion was not possible from the available automation surface; startup and asset validation are live-tested, while gameplay needs an in-client test pass.

## Cache source and revision evidence

- User archive: `%USERPROFILE%\OneDrive\Desktop\osrs server.rar`
- Read-only extracted cache: `C:\tmp\reason231-cox-donor-20260816\osrs server\server reasonps\data\cache`
- Read-only donor source: `C:\tmp\reason231-cox-donor-20260816\osrs server\server reasonps\kronos-server\src\main\java\io\ruin\model\activities\raids\xeric`
- Clean comparison cache: `C:\tmp\openrs2-cache-2048\cache`
- Destination LIVE cache: `<repo-root>\.data\cache\LIVE`
- Active server cache: `<repo-root>\.data\cache\SERVER`
- Donor evidence: its server declares build `229.1` and OpenRS2 cache `2048`; cache 2048 decodes as OSRS revision 229. The supplied “231” label is not supported by the local metadata.
- Archive SHA-256 before and after: `823B1C8C37D08D2395850D9312A8FF4D865BDF16800F9CF240519088EDCC28CE`
- Donor cache manifest SHA-256: `329A181D20769BE47E2A95B7081853D53E23F5EF74AB01F7B455240EEA16350E`
- Clean stock-229 manifest SHA-256: `350439322D2C9A601BEFAD09A7471733EBD91BFB55CAA840D1A7689B48C18364`
- Revision-240 LIVE manifest SHA-256: `AE11D3BADFBFC1F717E5C2B1FB299C15EF08E5C2389689835A12971C79B9D5FC`

The source archive and extracted donor were read-only. No Reason cache content was mutated.

## Audit result and cache policy

- 271 Chambers-related entries mapped.
- 209 `NATIVE_240_SAME_ID`.
- 62 `NATIVE_240_REPLACEMENT`.
- 0 unresolved decoded dependencies.
- 0 cache archives changed for this implementation.
- 0 custom Reason assets imported.
- The cache-change allowlist is intentionally empty.

Every mapping is recorded in `reason231-to-openrune240-cox-id-map.json`; the recursive references are recorded in `reason231-cox-dependency-graph.json`. These machine-readable files are the authoritative complete ID list and avoid scattering 271 numeric mappings through gameplay source.

The donor contains two divergent CoX implementations: a 48-file legacy implementation with disabled/incomplete rooms and shared static layout state, plus a separate five-file fixed-layout rework. Neither was copied directly. OpenRune already contained the native revision-240 CoX maps, definitions, interface, varbits, NPCs, objects, items, sequences, and graphics, but no authoritative end-to-end CoX gameplay module.

## Gameplay implemented

### Parties and instances

- Create, invite, join, leave, kick, leader transfer, rejoin, active-session protection, and maximum party size.
- Party-private dynamic instances using the existing instance manager.
- Deterministic seeded normal layouts and a fixed Challenge Mode layout.
- Connected-layout validation, final Olm placement, two-floor chunk placement, cleanup, logout tracking, death respawn, abandon handling, and cross-party NPC isolation.

### Registered rooms

`START`, `SCAVENGERS`, `SHAMANS`, `VASA`, `VANGUARDS`, `ICE_DEMON`, `SKELETAL_MYSTICS`, `TEKTON`, `MUTTADILES`, `TIGHTROPE`, `GUARDIANS`, `VESPULA`, `JEWELLED_CRABS`, `THIEVING`, `RESOURCE_FISHING`, `RESOURCE_HUNTER`, and `OLM`.

Combat rooms spawn raid-owned native NPCs with party/difficulty-scaled hitpoints and defence and become complete only after their owned NPC set is dead. Puzzle and resource rooms are bound to native room objects. The Ice Demon room now correctly changes from the brazier/shield stage to attackable NPC 7585; it no longer completes merely from the fourth brazier interaction.

### Great Olm

- Dedicated state machine: dormant, phases one through three, final head, defeated.
- Left/right hand vulnerability gates before the head.
- Phase advancement and phase-specific respawning.
- Explicit special cycle with crystal, lightning, acid, burn/spread, paired portal, and final falling-crystal damage.
- Raid completion and reward transition after the final head.

### Points, deaths, UI, supplies, and rewards

- Personal/team point accounting and death deductions.
- Stock revision-240 interface 513 overlay with team points, personal points, raid icon, and timer varbits.
- Raid-only gourd/water, seeds/herbs, worms/fishing, shared per-instance storage, and exit cleanup.
- Atomic per-player reward preparation/claim protection and inventory-or-ground delivery.
- Two distinct common rolls; up to three unique rolls from excess point chunks; point-weighted unique ownership.
- Dexterous/arcane scrolls, dragon hunter crossbow, twisted buckler, Dinh's bulwark, ancestral set, dragon claws, elder maul, Kodai insignia, twisted bow, Olmlet, elite clue, timed-CM ancestral kit, and metamorphic dust.
- Challenge Mode target times by party size, 5,000-point timed bonus, completion counters, and personal bests.

## Commands

- `::cox`
- `::coxparty create`
- `::coxparty invite <name>`
- `::coxparty join`
- `::coxparty leave`
- `::coxparty kick <name>`
- `::coxparty start normal`
- `::coxparty start challenge`
- `::coxparty rejoin`
- `::coxparty status`
- `::coxdev list`
- `::coxdev start normal [seed]`
- `::coxdev start challenge [seed]`
- `::coxdev room <room-name>`
- `::coxdev olm <phase>`
- `::coxdev points <amount>`
- `::coxdev complete`
- `::coxdev reset`
- `::coxdev status`

`::cox` and all `::coxdev` operations require administrator rights. Party management remains available to players.

## Source and configuration changed

### New Chambers module

- `content/minigames/chambers-of-xeric/build.gradle.kts`
- `CoxCombat.kt`
- `CoxConfig.kt`
- `CoxDomain.kt`
- `CoxInstanceLayout.kt`
- `CoxLayoutGenerator.kt`
- `CoxLifecycle.kt`
- `CoxModule.kt`
- `CoxRaidManager.kt`
- `CoxRewardService.kt`
- `CoxRules.kt`
- `CoxSupplies.kt`
- `CoxUi.kt`
- `CoxWorld.kt`
- `CoxRulesTest.kt`

### Existing shared files extended

- `settings.gradle.kts`: includes the Chambers module.
- `api/instances/src/main/kotlin/org/rsmod/api/instances/InstanceManager.kt`: deterministic explicit session termination used by raid cleanup.
- `api/player/src/main/kotlin/org/rsmod/api/player/ui/PlayerInterfaceExtensions.kt`: numeric stock-interface overlay open/close overloads; required because interface 513 is native and must not be remapped as a custom interface.
- `or-cache/build.gradle.kts`: repeatable `auditCoxCaches` verification task.
- `or-cache/src/main/kotlin/dev/openrune/audit/CoxCacheAuditMain.kt`: read-only three-cache audit.

### Audit/report artifacts

- `docs/raids/cox/reason231-cox-audit.md`
- `docs/raids/cox/reason231-cox-manifest.json`
- `docs/raids/cox/reason231-to-openrune240-cox-id-map.json`
- `docs/raids/cox/reason231-cox-dependency-graph.json`
- `docs/raids/cox/reason231-cox-cache-change-allowlist.json`
- `docs/raids/cox/reason231-cox-unresolved.json`
- `tools/reason231-raid-import/cox/README.md`
- `docs/raids/cox/cox-implementation-report.md`

No unrelated pre-existing user edits were reset, cleaned, checked out, or reverted.

## Verification completed

- `:content:minigames:chambers-of-xeric:test` — passed (7 tests).
  - 1,000 deterministic connected normal layouts.
  - Fixed/complete connected Challenge layout.
  - Party/Challenge scaling boundaries.
  - Unique chance cap and 69-weight unique table.
  - Excess-point multi-roll cap.
  - Challenge target-time boundaries.
  - Full Great Olm phase transition state machine.
- `:or-cache:auditCoxCaches` — passed; 271 mappings, zero unresolved.
- `:server:app:classes` — passed; 372 tasks, no compilation failure.
- Live restart 1 — revision 240, canonical working directory, custom-build marker, 329 scripts, ready on port 43594.
- Live restart 2 — same result; final live game-server PID 5856 owns port 43594.
- Final project doctor — confirms PID 5856 `user.dir` is the canonical project; SERVER and LIVE paths are correct; configured revision is 240.

Final cache fingerprints from the project doctor:

- SERVER: 25 files, 35,288,058 bytes, `main_file_cache.dat2` SHA-256 `D3C753BCD77487B175AF59F11ED2C19C2015CC3C35DFFA8996C36A3078282513`.
- LIVE: 25 files, 237,731,500 bytes, `main_file_cache.dat2` SHA-256 `7E1911549A9D4B8127018AE42D7B67102579F0441B4EC29F4D2262E557D8F9B3`.

## Remaining OSRS parity discrepancies

These are implementation gaps, not cache-resolution failures:

1. The production lobby currently uses commands/messages rather than the complete OSRS party/scouting/start interface, team list, and confirmation flow.
2. Normal layouts are deterministic connected room sequences assembled from verified native chunks, but do not reproduce every OSRS topology/scouting restriction or native transition animation.
3. Tekton, Vasa, Vespula, Vanguards, Muttadiles, Shamans, Skeletal Mystics, and Guardians still use shared generic melee/ranged/magic combat execution. Their full mechanics, exact animations, graphics, projectiles, attack ticks, immunities, weaknesses, environmental objects, Vanguard health-balance rule, and Vespula portal/lux mechanics are not yet 1:1.
4. Ice Demon now has correct shield-to-combat gating, but still lacks the full kindling/tinderbox loop, lit/unlit brazier transformations, frozen NPC presentation, icefiends, and shield drain model.
5. Jewelled Crabs, Tightrope, and Thieving are reduced progress interactions. They do not yet implement coloured-crystal beams, keystone guardian combat/balance movement/barrier transformation, or chest/grub/trough mechanics.
6. Resource support provides basic native raid-item gathering and shared storage, but does not yet implement the complete farming growth cycle, secondary gathering, potion recipes/doses/effects, cooking/burning, woodcutting/kindling, skill requirements, XP, or the stock storage interface container protocol.
7. Scaling and room points are centralized and deterministic but simplified; full OSRS per-NPC/per-room formulas, damage contribution, caps, and special contribution rules remain to be implemented and verified.
8. Great Olm has explicit phases and real damaging special schedules, but not exact head turning, target-side logic, cripple/regeneration, healing-hand behavior, native projectiles/graphics/objects/sounds, or tick-perfect OSRS special formulas.
9. Rewards implement the core unique/tertiary structure, but the common table is incomplete and collection-log/broadcast integration is absent. Exact current OSRS common quantities and every edge-case eligibility rule still need verification.
10. Automated tests do not yet exercise two simultaneous live parties, disconnect/rejoin reward races, full inventories, repeated instance leak cycles, or every room controller because those controllers are not complete.
11. Live verification covers asset validation, module loading, two restarts, canonical-runtime identity, and port binding. It does not cover the requested in-client sequence from `::cox` through a second full raid; that must be performed manually or with a game-client automation harness.

Because of these items, the implementation is a working integrated CoX foundation and end-to-end server flow, but it is not accurate to call it a production-complete 1:1 OSRS Chambers of Xeric implementation yet.

## References used for reward and Challenge Mode checks

- https://oldschool.runescape.wiki/w/Chambers_of_Xeric/Challenge_Mode
- https://oldschool.runescape.wiki/w/Ancient_chest
- https://oldschool.runescape.wiki/w/Olmlet
