# Theatre of Blood implementation report

## Outcome

The current OpenRune revision-240 tree now contains an isolated Theatre of Blood module, a reproducible donor/native cache audit, focused mechanic tests, and administrator testing controls. The module compiles, its 10 focused tests pass, the complete server application classes build, startup asset validation passes, and the final code survived two consecutive controlled server restarts.

This is an integrated end-to-end Theatre framework, but it is **not yet a production-complete 1:1 OSRS Theatre of Blood**. The exact remaining mechanic and presentation gaps are listed below.

## Source and cache evidence

- User-supplied archive: `%USERPROFILE%\OneDrive\Desktop\osrs server.rar`
- Extracted read-only donor cache: `C:\tmp\reason231-cox-donor-20260816\osrs server\server reasonps\data\cache`
- Donor ToB source: `C:\tmp\reason231-cox-donor-20260816\osrs server\server reasonps\kronos-server\src\main\java\io\ruin\model\activities\raids\tob`
- Clean comparison cache: `C:\tmp\openrs2-cache-2048\cache`
- Active OpenRune cache: `.data/cache/SERVER`
- Active JS5 cache: `.data/cache/LIVE`
- Donor revision evidence: both the donor and clean comparison cache decode as revision **229**, not 231. The donor folder/source naming is therefore not accepted as revision evidence.
- Archive SHA-256 before and after: `823B1C8C37D08D2395850D9312A8FF4D865BDF16800F9CF240519088EDCC28CE`
- Extracted donor `main_file_cache.dat2` SHA-256 after: `AA284D9D0942B4AEAA394A653EFDFEF1362443E9DC2D5883EDC5E0308EAEC19B`
- Active SERVER `main_file_cache.dat2` SHA-256: `D3C753BCD77487B175AF59F11ED2C19C2015CC3C35DFFA8996C36A3078282513`
- Active LIVE `main_file_cache.dat2` SHA-256: `7E1911549A9D4B8127018AE42D7B67102579F0441B4EC29F4D2262E557D8F9B3`

No destination cache archive was changed. No donor byte was imported. No cache backup was needed because the destination caches remained read-only throughout this task.

## Cache manifest

The final audit resolves 279 Theatre dependencies:

- `NATIVE_240_SAME_ID`: 230
- `NATIVE_240_REPLACEMENT`: 49
- `UNRESOLVED`: 0
- Reason assets imported: 0

Important remaps include donor Verzik/NPC forms that cannot be decoded safely by the rev-240 decoder. The module uses verified native rev-240 forms instead, including combat-ready Verzik 8370 for the donor initial form, 10848 for the donor hard-mode form, and native tornado 8386 instead of the donor private placeholder 20015.

The full machine-readable map is `reason231-to-openrune240-tob-id-map.json`; the dependency graph, manifest, allowlist, unresolved report, and human audit are beside it in this directory.

## Existing implementation before edits

No authoritative OpenRune Theatre of Blood server module existed. The project did contain a proven Chambers of Xeric instance/session pattern and reusable instance, death-hook, NPC-combat, reward, object-interaction, and overlay APIs. Those systems were reused rather than duplicated or replaced.

## Files created or modified

Created module:

- `content/minigames/theatre-of-blood/build.gradle.kts`
- `TobCombat.kt`
- `TobConfig.kt`
- `TobDomain.kt`
- `TobInstanceLayout.kt`
- `TobLifecycle.kt`
- `TobModule.kt`
- `TobRaidManager.kt`
- `TobRewardService.kt`
- `TobRules.kt`
- `TobSupplies.kt`
- `TobUi.kt`
- `TobWorld.kt`
- `TobRulesTest.kt`

Audit/tooling:

- `or-cache/src/main/kotlin/dev/openrune/audit/TobCacheAuditMain.kt`
- `or-cache/build.gradle.kts`
- `tools/reason231-raid-import/tob/README.md`
- the six required JSON/Markdown audit artifacts in `docs/raids/tob/`

No unrelated source was reset, reverted, cleaned, or overwritten.

## Implemented server flow

- Ver Sinhaza lobby teleport and native entrance/notice-board interaction.
- One-to-five-player parties with create, invite, join, leave, kick, leadership transfer, mode selection, conflicting-session protection, disconnect tracking, rejoin, abandonment, and cleanup.
- Private large dynamic instances assembled from the verified native Maiden, Bloat, Nylocas, Sotetseg, Xarpus, Verzik, and treasure regions.
- Strict linear room order and completion gating.
- Entry, Normal, and Hard mode identifiers, scaling, death policy, reward restrictions, and Hard completion requirement.
- Player/NPC ownership isolation between simultaneous instances.
- Persistent Entry/Normal/Hard completion counters, personal-best ticks, and Lil' Zik unlock state.
- Overlay status for mode, room, elapsed time, score, members, deaths, and spectator state.
- Raid-only supply points, purchases, inventory-full rejection, and exit cleanup.
- Atomic per-player reward claims with full-inventory ground fallback and duplicate-claim protection.
- Reward-room exit crystal cleanup.

## Encounter mechanics present

- **Maiden:** scaled boss, blood projectile/hit schedule, blood-spawn chance, 70/50/30 Matomenos thresholds, mode-specific native forms, death/reset transition.
- **Bloat:** walking/down state machine, vulnerability/down timing, stomp timing, falling-shadow hit schedule, mode scaling.
- **Nylocas:** 24 Entry or 31 Normal/Hard wave state, melee/ranged/magic native forms, alive cap, pillar-health failure state, Hard pillar pressure, Vasilias spawn and completion.
- **Sotetseg:** standard combat, two health thresholds, deterministic connected maze-path generation, shadow phase timer, party path communication, return to combat.
- **Xarpus:** exhumed, combat, and screech phases, poison schedule, persistent phase state, mode timing/scaling.
- **Verzik:** three separate native NPC forms, phase transitions, pillars, electric/blood/Nylocas/web/yellow-pool/green-ball/tornado schedules, Hard extra Nylocas cycle, completion and reward transition.
- **Deaths:** score penalty, room spectating, teammate continuation, Entry room reset on full wipe, Normal/Hard termination on full wipe, reconnect tracking, raid-item cleanup.

## Rewards

- Entry Mode has no unique roll and reduced common quantities.
- Normal uses a maximum deathless team unique chance of `1/9.1`.
- Hard uses `1/7.7`, 15% common bonus, or 30% in-time bonus.
- Team score is based on 18 participation points per player plus 14 MVP points; deaths remove four points.
- Normal unique weights: Avernic 8/19, rapier 2/19, Sanguinesti staff 2/19, each Justiciar piece 2/19, scythe 1/19.
- Hard adjusts the Avernic weighting and supports holy ornament kit, sanguine ornament kit, and sanguine dust tertiary rolls.
- Lil' Zik unlock persistence is wired.

## Commands

Player commands:

- `::tob`
- `::tobparty create`
- `::tobparty invite <name>`
- `::tobparty join`
- `::tobparty leave`
- `::tobparty kick <name>`
- `::tobparty start entry|normal|hard`
- `::tobparty rejoin`
- `::tobparty status`
- `::tobsupply stamina|prayer|brew|restore|potato|shark|turtle|manta`

Administrator commands:

- `::tobdev list`
- `::tobdev start entry|normal|hard`
- `::tobdev maiden`
- `::tobdev bloat`
- `::tobdev nylocas <wave>`
- `::tobdev nylocasboss`
- `::tobdev sotetseg <phase>`
- `::tobdev xarpus <phase>`
- `::tobdev verzik <phase>`
- `::tobdev room <name>`
- `::tobdev wave <n>`
- `::tobdev score <n>`
- `::tobdev complete`
- `::tobdev reward`
- `::tobdev wipe`
- `::tobdev reset`
- `::tobdev status`

## Verification

- `:content:minigames:theatre-of-blood:test`: passed, 10 tests.
- Tests cover canonical room order, mode reward rates, unique weights, Hard timers/multipliers, scaling, Maiden thresholds, Bloat timing, Nylocas wave/pillar state, deterministic connected Sotetseg paths, and Xarpus/Verzik phase machines.
- `:server:app:classes`: passed with the Theatre module included.
- `:or-cache:auditTobCaches`: passed, 279 mappings and zero unresolved.
- The aggregate `build` task reaches project-wide checks but is blocked by pre-existing formatting violations in unrelated dirty modules such as boss HP bar, database, Wilderness area checker, attributes, core, and death. Those files were deliberately not reformatted.
- Final controlled restart 1: passed; revision 240, custom-build marker, ports 43594/8080/9091, ready in 13.884 seconds.
- Final controlled restart 2: passed; revision 240, custom-build marker, ports 43594/8080/9091, ready in 14.129 seconds.
- Final live GameServer: PID 24896, canonical `user.dir`, ports 43594/8080/9091.
- Startup decoded all required ToB NPC, object, item, and reward definitions without an exception and loaded 332 scripts.

## Exact remaining OSRS discrepancies

These gaps prevent a truthful “complete 1:1” claim:

1. Lobby/party/mode selection currently uses commands and chat plus a stock overlay, not the complete native OSRS party board, confirmation, spectator, and reward interfaces/CS2 behavior.
2. Maiden Matomenos are native distinct NPCs at the correct thresholds, but their exact per-wave spawn table, path-to-Maiden healing, freezes/binds, blood-pool tile objects, projectile graphics, animations, sounds, and Hard variations are not complete.
3. Bloat has explicit walking/down/stomp states, but does not follow the exact room perimeter route or pillar line-of-sight model; falling-body tiles, flies, exact animations/sounds, and mode-specific timings remain incomplete.
4. Nylocas uses the correct native combat forms and mode wave counts, but the full OSRS per-team declarative spawn table, exact spawn tiles/ticks, pillar pathing/damage, explosions, style immunity enforcement, splits, Prinkipas behavior, and Vasilias visual prayer/style transitions remain incomplete.
5. Sotetseg generates a valid connected safe path and runs a shadow phase, but the path is communicated in chat rather than rendered as visible instanced red tiles. Shadow-realm separation, team reveal rules, red/death-ball chaining, projectile timing, safe-tile validation, and failure damage remain incomplete.
6. Xarpus has three states and poison scheduling, but exhumed objects/placement/healing, persistent acid-tile collision, exact facing quadrants, watched-side punishment, animations/projectiles/sounds, and mode differences remain incomplete.
7. Verzik has three native forms and all major special categories scheduled, but Dawnbringer pickup/special, exact pillar protection/destruction, bounce timing, red-crab interactions, web movement constraints, real yellow-pool objects, green/death-ball chain rules, tornado movement/collision, enrage, exact animations/projectiles/sounds, and the full Hard-only mechanic set remain incomplete.
8. Spectators are attack-blocked and tracked, but do not yet receive the exact native spectator orb/location/visibility restrictions. Logout/reconnect is server-managed but has not been exercised with two live clients.
9. Theatre supplies use a command purchase surface and ordinary item definitions. The native chest/shop interface, exact OSRS supply allocation formula, Dawnbringer/raid-only item family, and every anti-smuggling edge case remain incomplete.
10. The unique-rate/weight model and core named rewards are present, but the common table is abbreviated, collection-log and world-broadcast integration are absent, pet/cosmetic eligibility needs a current-source audit, and exact all-death/MVP edge cases remain incomplete.
11. No automated client harness exists in this workspace. The requested in-client completion of every room, animations/projectiles, two simultaneous parties, repeated raids, disconnect/rejoin, full inventory, and live reward race was not performed. Server startup and asset/plugin integration were verified; visual and interactive parity still needs manual RSProx testing.

## References

- https://oldschool.runescape.wiki/w/Theatre_of_Blood
- https://oldschool.runescape.wiki/w/Theatre_of_Blood/Entry_Mode
- https://oldschool.runescape.wiki/w/Theatre_of_Blood/Hard_Mode
- https://oldschool.runescape.wiki/w/Theatre_of_Blood/Strategies
- https://oldschool.runescape.wiki/w/Monumental_chest
