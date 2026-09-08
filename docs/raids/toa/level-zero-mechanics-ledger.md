# Tombs of Amascut — level-zero mechanics ledger

This ledger records the current level-zero foundation in OpenRune. “Implemented” means the behavior
is present in the Kotlin production source and has the automated coverage named below. It does not
mean that a complete raid has passed live, in-client acceptance.

The donor archive is behavioral reference only. The donor declares build 229.1, not revision 240,
and contains no implementations of the four challenge rooms. No donor Java, donor IDs, or donor
cache bytes are used at runtime. See `reason231-toa-audit.md` and the machine-readable
`reason231-to-openrune240-toa-id-map.json` for the 229-to-240 crosswalk.

## Evidence and verification boundary

- OSRS behavior references:
  [Tombs of Amascut strategies](https://oldschool.runescape.wiki/w/Tombs_of_Amascut/Strategies)
  and the relevant encounter pages linked from it.
- Revision-240 map evidence: `ScabarasRevision240RuntimeLayout`,
  `ApmekenRevision240RuntimeLayout`, and `HetRevision240RuntimeLayout` document decoded LIVE-cache
  regions and explicitly identify server-populated coordinates that are absent from map archives.
- Startup cache guards: `ToaRaidManager`, `ToaPartyBoard`, `ToaUi`, `ToaRewardService`,
  `ToaSupplies`, `ToaHetOpenRuneBridge`, and `ToaHetPickaxeStorage` require the referenced
  revision-240 NPC, loc, item, sequence, spotanim, interface, component, varp, and varbit definitions.
- Automated verification means the exact test classes listed in this document. A class containing
  `LiveRuntimeTest` is still an automated runtime-adapter test; it is not a live client session.
- Live full-run acceptance is **pending**. This document does not claim a successful lobby-to-reward
  raid on the deployed game server.

## Mechanics ledger

| Feature | Level-zero implementation | OpenRune revision-240 mapping and evidence | Automated regression coverage |
| --- | --- | --- | --- |
| Locked start state and instance route | **Implemented in source.** A successful start snapshots original party size, raid level 0, empty invocation selection, path levels 0, seed, and stable account hashes. The private instance contains the lobby, every challenge, every guardian, both Warden arenas, and the reward room. | `ToaRoom` sources: lobby `14160`; Scabaras/Kephri `14162`/`14164`; Het/Akkha `14674`/`14676`; Apmeken/Ba-Ba `15186`/`15188`; Crondis/Zebak `15698`/`15700`; Wardens `15184`/`15696`; rewards `14672`. `ToaInstanceLayout` copies all `TOA_ACTIVE_ROOMS`. | `ToaRulesTest`, `ToaLifecycleStateTest` |
| Native lobby, party, invocation-safe level-zero UI, and HUD | **Implemented in source.** Party create/view/apply/accept/remove/leave/start flow is bound explicitly; invocation controls refresh safely but cannot start a nonzero raid in this baseline. | Locs `46068` grouping obelisk, `46073` invocation board, `46089` start. Interfaces `772`, `774`, `776`, `777`; HUD `481`; on-load scripts `6594`, `6615`, `6609`; enums `4792`, `4793`, `4794`, `4664`; controller varp `3603`. Components are explicit packed IDs in `ToaPartyBoard`, never enum-order lookups. | `ToaRulesTest` |
| Path selection and completion gating | **Implemented in source.** Each path enters its challenge before its guardian, completed paths cannot be selected again, and all four guardians are required before Wardens. | Path locs `46155`/`46156`, `46158`/`46159`, `46161`/`46162`, `46164`/`46165`; Wardens locked/open locs `46167`/`46168`; guardian crystals `45505`, `45754`, `45506`, `45866`; Wardens crystal `45579`. | `ToaRulesTest` |
| Path of Scabaras challenge | **Implemented in source.** Sequence, lights, addition, obelisks, matching, branch barriers, ranged scarabs, seeded layouts, reset, and completion routing are present. | Region `14162`; sequence locs `45338`, `45340`–`45342`; addition locs `45339`, `45345`–`45353`; matching locs `45355`–`45395`; branch loc `45343`; ranged scarab `11697`; obelisks `11698`/`11699`. Cache-proven versus server-populated bindings are recorded in `ScabarasRevision240RuntimeLayout`. | `ToaScabarasEncounterTest`, `ToaScabarasRuntimeLayoutTest`, `ToaScabarasLiveRuntimeTest`, `ToaScabarasIntegrationTest` |
| Kephri | **Implemented in source.** Base shield phases, two recovery waves, swarms, eggs, bombs, overlords, prayer-aware attacks, death, points, and guardian completion routing are present for the zero-invocation baseline. | Region `14164`; Kephri `11719`, recovering form `11722`, swarms/adds `11723`–`11729`; sequences `9577`, `9578`, `9581`, `9587`, `9588`, `9605`; projectiles/graphics `2152`, `2168`, `2266`. | `ToaRulesTest` |
| Path of Apmeken challenge | **Implemented in source.** The eight-wave baseline, party scaling, sight holder, vents/roof-support/corruption quick events, supplies, shamans/thralls, cursed pools, volatile explosions, cleanup, and challenge-to-Ba-Ba transition are present. | Region `15186`; wave NPCs `11709`–`11718`; locs `45493`–`45500`; QTE spotanims `2132`–`2139`; combat effects include `2017`, `2242`, `2247`, `2249`. Static placements and unresolved server-selected spawn anchors are documented in `ApmekenRevision240RuntimeLayout`. | `ToaApmekenEncounterTest`, `ToaApmekenRuntimeLayoutTest`, `ToaApmekenLiveRuntimeTest` |
| Ba-Ba | **Implemented in source.** Melee cycle, rockfall/shockwave attacks, rolling-boulder phases, weak boulders, rubble, baboons, sarcophagus support, death, points, and guardian completion routing are present for the zero-invocation baseline. | Region `15188`; NPCs `11778`–`11788`; sequences `9737`–`9758`, `9796`, `9805`, `9806`; projectiles/graphics `2242`–`2252`, `2257`; locs `45752`–`45756`. | `ToaBabaEncounterTest`, `ToaRulesTest` |
| Path of Crondis challenge | **Implemented in source; live visual acceptance pending.** The protected water/palm/crocodile/barrier flow now has four cache-accurate waterfall paths, staggered animated spikes, seeded moving poison trails, destination-tile collision, spill rounding, Perfect eligibility, and generation-safe cleanup. | Region `15698`; palm/crocodile NPCs `11700`-`11705`; item `27295`; four waterfalls `45398` with dry state `45399`; 40 statues `45415`; 40 spears `45416`; sequences `9562`, `9563`, `9565`; trail gfx `2129`. Exact geometry and evidence boundaries are recorded in `crondis-obstacle-evidence.md`. Rare-spill probability and exact damage scaling remain trace-required. | `ToaCrondisEncounterTest` |
| Zebak | **Implemented in source.** Ranged/magic/melee cycles, roar thresholds, waves and safe gaps, jugs, rocks, acid, water clearing, enrage, death, points, and guardian completion routing are present for the zero-invocation baseline. | Region `15700`; NPCs `11730`–`11743`; sequences `9618`–`9637`; effects `2176`–`2195`; acid locs `45570`–`45576`. | `ToaZebakEncounterTest`, `ToaRulesTest` |
| Path of Het challenge | **Implemented in source.** Movable/fixed/dirty mirrors, rotation and pushing, beam tracing, barriers, shield/seal phases, falling orbs, pickaxe statue storage, reset, cleanup, and challenge-to-Akkha transition are present. | Region `14674`; mirror item `27296`; seal NPCs `11706`/`11707`, orb `11708`; mirrors `45455`–`45457`; barriers `45458`–`45466`; statue families beginning at `45468`; shield/caster locs `45485`/`45486`; pickaxe-storage varbit `14440`; beam effects `2064`, `2114`, `2116`–`2122`. Cache roles/actions are validated by `ToaHetOpenRuneBridge`. | `ToaHetEncounterTest`, `ToaHetRuntimeLayoutTest`, `ToaHetLiveRuntimeTest`, `ToaHetOpenRuneBridgeCacheTest`, `ToaHetPickaxeStorageTest` |
| Akkha | **Implemented in source.** Three-style form rotation, matching overhead protection, memory quadrants, detonation, shadow thresholds, trailing orbs, final stand, death, points, and guardian completion routing are present for the zero-invocation baseline. | Region `14676`; NPC/form range `11789`–`11805`; sequences `9760`–`9794`; effects `2253`–`2260`; native shadow headbar `42`. | `AkkhaEncounterTest`, `ToaRulesTest` |
| Wardens | **Implemented in source.** Obelisk charging and phase-one specials, phase-two forms/prayers/core exposures, obelisk attacks, Energy Siphons, phase-three phantom attacks, slam/lightning/floor removal, enrage, completion, and reward unlock are present at default cadence. | Regions `15184` and `15696`; Warden/core/siphon/phantom NPCs `11746`–`11777`; phase-one pyramid locs `45748`–`45751`; platform states `45605`–`45626`; principal projectiles/effects `2204`–`2241`; phase-two animations include `9659`–`9672`, `9727`, `9732`, `9733`. | `ToaWardensEncounterTest`, `ToaRulesTest` |
| Helpful Spirit supplies and temporary-item cleanup | **Implemented in source.** Life/Chaos/Power bundles unlock at the two-path and four-path checkpoints; issued raid supplies and challenge-only items are removed on detachment. | Helpful Spirit `11694`; supply items `27315`, `27317`, `27323`, `27327`, `27329`, `27335`, `27339`, `27343`, `27347`; challenge items `27295`, `27296`, and the Apmeken neutralising potion. Definitions are startup-validated through revision-240 item mappings. | `ToaSuppliesTest` |
| Death, wipe, disconnect, rejoin, leave, and deterministic developer routing | **Implemented in source.** Stable account identity is separated from transient player UID; disconnect pauses only when no eligible participant remains; rejoin rebinds runtime state; completion credit is idempotent. Administrator controls accept expected party size and seed and distinguish challenges from guardians. | `ToaRaidStartSnapshot`, `ToaRaidRoster`, `ToaRaidPauseRules`, `ToaCompletionCredit`, `ToaDevStartRules`, `ToaDevRoomRoutes`, and `ToaDevStatusSnapshot`; command surface is `::toadev start <party-size> <seed>|solo|status|reset|paths|complete <path>|room <name>|wardens|reward`. | `ToaLifecycleStateTest` |
| Completion and rewards | **Implemented in source.** All four paths unlock Wardens; final completion prepares one pending roll per eligible participant; rewards can be claimed to inventory/bank, discarded, or persisted to the lobby Rewards Niche. | Reward entry `45138`; reward sarcophagi `46220`, `46218`, `44547`, `44545`, `29994`, `46216`, `46215`, `46219`, `46217`; native reward interface `771`, components `4`, `6`, `8`, `10`; Rewards Niche varbit `14319`; revision-240 unique IDs and fallback IDs are defined in `ToaRewardRules`. | `ToaRewardRulesTest`, `ToaRulesTest`, `ToaLifecycleStateTest` |

## Exact automated test-class inventory

All classes below are under
`content/minigames/tombs-of-amascut/src/test/kotlin/org/rsmod/content/minigames/toa/`:

- Core route and lifecycle: `ToaRulesTest`, `ToaLifecycleStateTest`
- Scabaras: `ToaScabarasEncounterTest`, `ToaScabarasRuntimeLayoutTest`,
  `ToaScabarasLiveRuntimeTest`, `ToaScabarasIntegrationTest`
- Apmeken: `ToaApmekenEncounterTest`, `ToaApmekenRuntimeLayoutTest`,
  `ToaApmekenLiveRuntimeTest`
- Crondis and Zebak: `ToaCrondisEncounterTest`, `ToaZebakEncounterTest`
- Het: `ToaHetEncounterTest`, `ToaHetRuntimeLayoutTest`, `ToaHetLiveRuntimeTest`,
  `ToaHetOpenRuneBridgeCacheTest`, `ToaHetPickaxeStorageTest`
- Guardians and Wardens: `ToaBabaEncounterTest`, `AkkhaEncounterTest`,
  `ToaWardensEncounterTest`
- Supplies and rewards: `ToaSuppliesTest`, `ToaRewardRulesTest`

These tests cover deterministic rules, layouts, cache bindings, state machines, scaling helpers, and
runtime adapters. They are not a substitute for a deployed multiplayer client run.

## Pending live in-game acceptance

The level-zero milestone must remain **live-pending** until one recorded deployed-server session
completes all of the following without developer room skips:

1. Create and manage a party through every native lobby button, then start with raid level 0.
2. Complete Scabaras → Kephri, Apmeken → Ba-Ba, Crondis → Zebak, and Het → Akkha.
3. Complete every default Warden phase and enter the treasure room.
4. Claim or store rewards, leave safely, and verify the Rewards Niche state.
5. Repeat lifecycle smoke checks for death, team wipe, logout/rejoin, explicit leave, external
   teleport, and at least one 2–8-player scaling run.

Until that run is recorded, the accurate status is: **implemented with automated regression
coverage; live full-raid acceptance pending**.
