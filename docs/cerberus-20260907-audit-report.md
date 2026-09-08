# Cerberus audit — 2026-09-07

**Status: implemented corrections are built and running; the full requested OSRS parity acceptance is incomplete. Do not call this 1:1.**

**Latest regression:** an instance allocation UID was compared with a map-square number, rejecting custom attacks even after combat dispatch was corrected. See [the instance guard fix](cerberus-instance-guard-fix-20260907.md) for the reproduced failure, 50-test result, real projectile/Soul/lava integration coverage and updated runtime status. Earlier test counts and process IDs below are historical.

**Follow-up correction:** the reported missing Souls/invisible projectiles revealed that the sitting-base NPC dispatched to generic combat despite its active visible form. The earlier tests below did not exercise this wiring. See [the combat dispatch follow-up](cerberus-visual-fix-20260907.md) for the reproduced defect, correction, 40-test result and current runtime verification. Historical process IDs and test totals below describe the earlier audit stage.

The canonical working tree is `<repo-root>`.
Doctor confirmed the original JVM (28180) used this directory before gameplay changes.
The initial rebuilt server passed two normal shutdown/start cycles: 28180 → 21172 → 4868.
After final barrier-guard review, the final build passed another two: 4868 → 35728 → **35992**.
All four new JVMs emitted `OPENRUNE CUSTOM BUILD ACTIVE` and reached `Server ready`.
Revision remains **240**; SERVER, LIVE and embedded PostgreSQL paths were retained.
No player inventory, bank, quest or Slayer assignment was edited.

## Confirmed corrections

1. **Cache statistics and shared attributes:** the loaded Cerberus definitions lacked the required combat parameter overlay, including the correct magic attack bonus. All three forms now have the specified attack/defence bonuses, Stab type, six-tick rate, demon classification, poison/venom/normal-burn immunity, water weakness 40%, Slayer XP 690 and combat XP multiplier 1150. NPC cannon immunity is false, distinct from the unsupported placement restriction. HP 600, size 5, combat 318 and base levels were checked against the real cache.
2. **False-positive graphic validation:** removed ID-allowlist acceptance. Runtime validation decodes the real LIVE spot-animation catalog; the test also checks linked models and sequences. Merely knowing an ID no longer makes it valid.
3. **Random sequence drift:** special skip RNG is consumed only for a due, unforced Souls/lava decision. Triple has priority and consumes no skip roll. Attack 21/35 collisions and all attack numbers through 35 are covered exactly.
4. **Lava tile selection:** replaced retry/fallback selection with a complete prevalidated candidate list, choosing two distinct random tiles without replacement plus the snapshotted player tile. An insufficient catalog fails explicitly rather than emitting fewer pools. Exact OSRS chamber geometry remains unresolved.
5. **Ward integration:** located and used the existing shared `ArceuusSpellState.hasWard`. Protected Soul drains resolve to 30/15/20/10 for none/Spectral/Ward/both. The previous evidence document incorrectly said shared Ward state was absent.
6. **Fixed mechanic damage:** unprotected Souls remain exactly 30, or 22 following the normal Elysian proc collector. Lava and Soul fixed damage no longer accidentally pass through generic Ward/absorption reductions. Normal and triple attacks use the shared damage-reduction formula; their prayer protection remains sampled at launch.
7. **Delayed hit ownership:** added a generic optional validity predicate to normal queued hits, checked before hit-processing side effects. Cerberus supplies run, generation, actual region, membership, living-player and living-boss validation. Old hits cannot become valid merely because the player starts another encounter.
8. **Reset/debug leakage:** reset/respawn clear forced attacks, forced styles, skip overrides and animation locks; reset clears per-attempt achievement state. A debug-tainted kill does not taint the next naturally respawned boss.
9. **Achievement event tracking:** a Soul wave is recorded after actual NPC creation; protected Souls count at launch; damaging red-Soul melee counts toward Anti-Bite. Boss-death completion is phase guarded to avoid repeating that completion path.
10. **Outward barrier damage cancellation:** wall-crossing damage is now bound after the phase update, so the outward reset cannot immediately invalidate its own five-damage hit. Final review also routed its validity through the same actual-region, level, living-entity and generation checks as other hits, allowing reset-phase crossings. Wall geometry and OSRS persistence are still unproven.
11. **Drop registration:** `@RegisterDropTable` was attached to a constant rather than the table field; it now registers the actual table.
12. **Unique/main roll structure:** a separate unique roll could accompany an ordinary main-table result. The main table now has 129 common weight plus one weight selecting four equal uniques: each unique is exactly 1/520 and replaces the common outcome. The inspected starting file already had the 1/130 constant; the older 1/520 outer-denominator issue described in the request was not its only remaining defect.
13. **Duplicate ashes:** removed table-guaranteed ashes because generic `NpcDeath.dropped_remains` already supplies infernal ashes once, including the normal ash-sanctifier hook.
14. **Death-pile location:** added a generic optional death-drop location hook and a Cerberus binding. Normal recoverable death items from a Cerberus instance go to the existing public exit (1310,1249,0), before disposable-instance teardown. Item selection remains in the standard death pipeline. Location rules are tested across clones and stale external membership; a complete live death/grave test is pending.
15. **Cache-test concurrency:** the new real-cache player test exposed concurrent initialization of shared cache singletons. Player tests now run sequentially and pass.

Existing sitting/standing/resetting/Soul/lava code was extended, not duplicated. The three requested commands remain authoritative. There is no new public entrance, Nexus integration, quest gate, fabricated task, Ironman branch, fragment, coffer or scoreboard.

## Mechanic evidence

| Mechanic | Evidence and practical limit |
|---|---|
| Stats, demon/toxin/burn attributes, water weakness, XP | [Cerberus Wiki](https://oldschool.runescape.wiki/w/Cerberus), actual SERVER NPC definitions and canonical TOML overlay. Shared formulas consume the attributes; every supported weapon/spell was not tested in a live fight. |
| Attack schedule, thresholds, priority, protection, Souls and lava rules | [Strategies](https://oldschool.runescape.wiki/w/Cerberus/Strategies) and user acceptance specification. Deterministic rule tests prove the implementation decisions, not client packet timing. |
| Ward/Spectral interaction | [Ward of Arceuus](https://oldschool.runescape.wiki/w/Ward_of_Arceuus), Strategies and existing shared spell/equipment state. No invented item-name matching or Cerberus-only Ward timer. |
| Forms, sequences 4484–4495/4501 and 8526–8531, graphics 1241–1248 | Loaded revision-240 definitions, model data, [RuneLite AnimationID](https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/gameval/AnimationID.java), [SpotanimID](https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/gameval/SpotanimID.java). Existence does not prove use, heights, travel or ordering. |
| Western anchor | Raw map spawn `0_19_19_22_34` → (1238,1250,0), region 4883. Full chamber mask and path reach not established by this anchor test. |
| Barrier/sounds | Cache object 23105, linked animation 494 and retained synth assets 162/163/911 exist. Donor placement/timing is historical reference only. |
| Drops and Key master scroll | [Cerberus drops](https://oldschool.runescape.wiki/w/Cerberus), [Key master teleport](https://oldschool.runescape.wiki/w/Key_master_teleport). Main item/amount/noted mappings inspected; key scroll seven at weight two retained. Real cache confirms `obj.blessedsnake` is Unholy symbol. |
| KC, achievements, Collection Log | Native variables in cache; 1702/3250 account-state packets; generic kill and collection services. Cache tests prove KC and all six task/base-counter varps have permanent lifetime. Native Collection Log catalog struct 480/enum 2113 contains Hellpuppy, all three crystals, Jar of Souls, Smouldering stone and Key master teleport. Account saver filters permanent varps and loader restores them. No new account was killed/relogged to prove the full persistence transaction. |
| Lifecycle visual timings | Existing donor-derived values retained. No complete OSRS capture proves standing/reset timing, Soul paths, wall ordering or the death-to-sitting interval. |

## Capture searches and saved artifacts

[RSProx](https://rsprox.net/database) searches covered all six NPC IDs, all requested boss sequences, Soul sequences, graphics 1241–1248, object 23105, mapsquares 4883/5139/5395, coordinate text forms and both overhead messages. NPCs/sequences/wall/coordinate-text returned zero. Mapsquares returned only 1702/3250. Graphic matches produced the following **non-fight candidates**, all saved and decoded:

| Original | Transcript | Finding |
|---|---|---|
| `logs/cerberus-audit-20260907/captures/rsprox-3323.bin` | `logs/cerberus-audit-20260907/captures/rsprox-3323.txt` | Other NPC 7286 reuses graphics 1242/1243 near 226x,565x. |
| `logs/cerberus-audit-20260907/captures/rsprox-3329.bin` | `logs/cerberus-audit-20260907/captures/rsprox-3329.txt` | Same unrelated graphic family usage. |
| `logs/cerberus-audit-20260907/captures/rsprox-416.bin` | `logs/cerberus-audit-20260907/captures/rsprox-416.txt` | No Cerberus fight. |

All paths above are relative to the canonical root. Search JSON files are in `logs/cerberus-audit-20260907/rsprox-*.json`. The reproducible supplementary query script is `scripts/cerberus-search-captures.ps1`; decoding is `scripts/cerberus-transcribe.ps1`. The two revision-235 candidates could not resolve the decoder's NPC type cache; their numeric update packets were still inspected. An initial search with thirteen conditions returned HTTP 400; splitting it into smaller batches succeeded.

The supplied originals/transcripts under
`%USERPROFILE%\Documents\Codex\2026-08-31\https-oldschool-runescape-wiki-w-royal\outputs\cerberus-captures\`
were inspected without modification. Neither 1702 nor 3250 is fight evidence. In particular, a transcript tick numbered 5862 is not an NPC update.

## Exact verification results

All commands ran from the canonical root with `.\gradlew.bat`.

| Command arguments | Result |
|---|---|
| `:content:bosses:cerberus:test --console=plain` | Passed during targeted verification; final XML: **36 tests, 0 failures, 0 errors, 0 skipped** (27 existing rules, 7 decision, 2 death-location). |
| `:or-cache:test --tests dev.openrune.CerberusR240CacheAuditTest --rerun --console=plain` | Fresh baseline passed: 15s, 18 tasks, 1 executed/17 up-to-date. The reported GameValProvider class-loading failure did not reproduce. No classloader workaround was added. |
| Expanded `:or-cache:test --tests dev.openrune.CerberusR240CacheAuditTest --console=plain` | Passed: 2 tests, 0 failures/errors/skips; first expanded pass 13s, 18 tasks, 3 executed/15 up-to-date. Final additional persistence assertions also passed. |
| `:content:bosses:cerberus:test :content:drops:test --console=plain` | SUCCESS, 16s, 177 tasks, 8 executed/169 up-to-date. |
| `:content:drops:test --tests '*CerberusDropTableTest' --console=plain` | SUCCESS, 6s, 173 tasks, 4 executed/169 up-to-date; 5 Cerberus drop tests pass. |
| `:engine:game:test :api:player:test :api:death:test :api:death-plugin:test :api:combat:combat-formulas:test --console=plain` | Engine and combat formula tests passed; initial player run exposed cache initialization concurrency (1/16 failed). Corrected by serializing cache-backed player tests; rerun below passed. Death modules have NO-SOURCE unit tests. |
| `:api:player:test :content:skills:slayer:test :api:instances:test :api:mechanics:toxins:test :api:account:test :api:collection-log:test --console=plain` | SUCCESS, 42s, 160 tasks, 9 executed/151 up-to-date. Account unit tests: NO-SOURCE. |
| `:or-cache:installCerberusConfig --console=plain` | SUCCESS, 8s, 16 tasks, 2 executed/14 up-to-date. Intentionally updates only the three NPC parameter records, with backups. |
| `:server:app:installDist --console=plain` | SUCCESS, 34s, 443 tasks, 147 executed/296 up-to-date. |
| `test --continue --console=plain` | **FAILED**, 6m12s, 612 tasks, 150 executed/462 up-to-date. Two out-of-scope failures detailed below. |
| `:content:bosses:cerberus:test :or-cache:test --tests dev.openrune.CerberusR240CacheAuditTest :content:other:consumables:compileTestKotlin :server:app:installDist --continue --console=plain` | Overall FAILED only on consumables, 19s, 448 tasks, 11 executed/437 up-to-date. Cerberus 36/36, cache 2/2 and updated installDist all passed. |
| Final `:content:bosses:cerberus:test :or-cache:test --tests dev.openrune.CerberusR240CacheAuditTest --console=plain` | SUCCESS, 5s, 148 tasks all up-to-date. Confirms final targeted dependency graph and results. |
| After final barrier guard `:content:bosses:cerberus:test :server:app:installDist --console=plain` | SUCCESS, 9s, 445 tasks, 8 executed/437 up-to-date; 36 Cerberus tests pass. |

Final relevant XML totals: Cerberus 36; targeted cache audit 2; drops module 45; engine game 18; player 16; combat formulas 10; instances 20; Slayer 61; Collection Log 10; toxins 1. All have zero failures/errors/skips: **219 tests across these results**. This is not 219 Cerberus integration tests. XML reports live in each module's `build/test-results/test`; generated reports were read, never hand-edited.

The broad failures were:
- `:content:other:consumables:compileTestKotlin`: `ConsumableActivityPolicyTest.kt` has unresolved Kotlin test symbols and a syntax error at line 78.
- `:content:skills:agility:test`: `AgilityMapPlacementTest.kt:71`, expected object IDs [36221,36229], actual empty set. Agility ran 13 tests with one failure.

Those sources/maps were not changed. An attempted `:api:player:integration` verification also failed because that Gradle task does not exist; the old integration source tree is not runnable through the current build. No dormant integration tests are counted as passing. Installer development encountered read-only-cache API errors before switching to the standard writable CacheLibrary; the final install and audit succeeded.

Restart logs:
- `logs/cerberus-audit-20260907/restart-1.stdout.log`: marker 04:12:00 EDT, ready 04:12:03, JVM 21172; normal account-saving shutdown logged before restart two.
- `logs/cerberus-audit-20260907/restart-2.stdout.log`: marker 04:13:48 EDT, ready 04:13:51, JVM 4868.
- `logs/cerberus-audit-20260907/restart-3.stdout.log`: final code, marker 04:24:42 EDT, ready 04:24:45, JVM 35728.
- `logs/cerberus-audit-20260907/restart-4.stdout.log`: final code, marker 04:26:26 EDT, ready 04:26:29, live JVM 35992.
- Matching `.stderr.log` files are retained.
- Doctor completed after each startup and verified canonical `user.dir`, SERVER cache and embedded PostgreSQL authority. Filtered results are saved as `doctor-restart-1-summary.txt` through `doctor-restart-4-summary.txt` in the audit log directory. Startup logged one slow-cycle message on each run (951ms/795ms/798ms/763ms), with no Cerberus asset/startup exception.

## Coverage and unresolved acceptance requirements

| Requested test area | What is actually proven / what remains |
|---|---|
| 1–3 schedule, overlaps, RNG | Exact rules through attack 35 at HP 600/400/399/200/199/1; collisions and bounded skip outcomes; seeded sequence stability. |
| 4–5 prayer and triple timing | Runnable shared launch-snapshot test across all three styles; triple sequence/gap rule tests. No full emitted-packet timeline comparison. |
| 6–8 Souls/permutations/equipment | All six order permutations, fixed damage and Prayer-drain rules tested. Live west-to-east movement, actual equipment procs and sound/despawn timing still need observation. |
| 9 boss death during Souls | Generation predicates tested, not every real coroutine stage. Already-launched Soul hits on the boss-death tick are currently canceled by the living-boss guard; OSRS tick ordering requires resolution. |
| 10–12 lava | Exact three-item selection and exhaustive protection matrix tested. Runtime guards cancel stale damage. Full pool cleanup/cross-instance coroutine tests and June-2025 close/distant reaction delays remain. |
| 13–14 mask/reach | All 4096 local tile distances checked against a 5×5 footprint oracle. Exact chamber mask, collision, every edge/diagonal LOS, under-boss walking and red-X behavior remain unverified. |
| 15–17 lifecycle/teardown | Existing rules and new hit predicate/location tests pass. No complete deterministic world simulation of every transform, logout, teleport, simultaneous death or reused instance. |
| 18 unique structure | Actual registered table structure, 130 outer weight and four equal inner outcomes tested without statistical sampling. |
| 19 conditional rewards | Unconditional clue/casket grants absent. Normal elite 1/100 retained. Native elite reward-tier meaning/service and real clue-step completion integration are incomplete; **1/95 and step-completion casket are not implemented**. Genuine Konar checks remain shared; final-task hook ordering needs an integration test. |
| 20 idempotency/persistence | Achievement completion phase guard, actual-event flags, permanent native varps and generic Collection Log state tests. Duplicate global death callbacks, actual KC/Slayer/drop transaction and relog/restart after a kill still require testing. |
| 21 interactions | Cache attributes and shared immunity tests pass; bones response retained. Cannon placement system is absent, so the requested melt-message restriction is **not implemented**. Thralls/demonbane/burn/prayer interactions need live encounter tests. |
| 22 assets | NPCs, required animations/graphics, linked models/sequences, retained object/sounds, item definitions and source anchor pass real-cache audit. Full collision mask and provenance of sound usage remain open. |

Additional limitations:
- Existing Soul spawn/route constants, firewall x23..25/z26, chamber bounding rectangle, projectile starts/heights/curves and lava lifetimes are donor/implementation values. They were not replaced with guesses. The current rectangle plus collision filtering is not a proven exact source-local chamber mask.
- The 15-tick respawn currently starts after generic death animation completion, not necessarily the OSRS clock anchor. Visible death-to-sitting timing requires capture.
- Firewall private objects do not yet have proven Pass/Quick-pass interaction and collision behavior. Persistence sources conflict.
- Simultaneous boss/player death still needs a world-level test; immediate player-death cleanup can remove the boss while its death sequence is pending. No claim of correct reward ordering is made.
- Standard recoverable deaths here use ground piles, not a complete OSRS grave service. Relocation prevents trapping items in a destroyed instance but does not make ground piles persist over server restart. Ordinary administrator death protection also remains in effect.
- Already-sent client projectiles cannot be withdrawn by the server; generation checks prevent their stale damage. An observed client fight is needed to distinguish harmless lingering graphics from hazards.
- Music/ambient behavior, all individual sound usage, Soul defend/ready/flight ordering, the lava sound added to OSRS, exact facing and visual order remain capture gaps.
- No global Combat Achievement tier progression or cannon/clue subsystem was fabricated just for Cerberus.

## Explicit full-fight capture checklist — still required

Record current OSRS in the western chamber with RSProx and a synchronized video. Save the original binary and decoded transcript; record game revision, tick reference, equipment, prayers and active effects.

- Start outside the chamber before wake. Capture sitting, crossing each barrier direction, standing, active form, wall creation/collision/interaction and initial facing.
- Complete two kills, allowing at least 35 attacks below the relevant HP thresholds; retain skipped specials and triple/Souls/lava overlap cases. Include ordinary melee/magic/ranged and every triple component.
- For each attack log server tick, NPC form/sequence/facing/overhead, projectile source and target, start/end cycle, heights, slope/angle, spot animation, sound, Prayer and HP change.
- Capture all six Soul style orders over enough waves: exact spawn tiles, ready, flight, paths, lane launches, prayer-drain tick, hits, retreat and despawn.
- Record protected Souls with none/Spectral/Ward/both, low Prayer reaching zero, and unprotected Elysian proc/non-proc cases.
- Trigger lava while adjacent and distant; record three targets, launch/arrival/first-damage cycles, every two-tick continuation, removal and audio. Include all shield/prayer/regular/super-antifire combinations.
- Exit/reset while attacks are in flight; record walk-back, healing, resetting form, sitting transition and wall persistence.
- Capture lethal boss damage, visible death, loot and sitting respawn with an unambiguous clock origin. Include a Soul already launched on the death tick, a Soul about to launch and simultaneous player death.
- Retain corridor/edge/under-boss movement and attack attempts, sounds/music, bones and cannon attempts.

A non-Cerberus NPC using graphic 1242 is not an acceptable substitute.

## Manual OpenRune verification checklist — not yet observed

- [ ] Kill one via `::cerberus` without debug mutation: observe sitting/wake, barrier, normal/triple projectiles, Souls below 400, lava below 200, death and sitting respawn. Record attack 21 and 35 overlap outcomes.
- [ ] Kill two in the same instance: confirm HP600 reset, opening triple, no old overrides/hazards/Souls and one ashes/main outcome per eligible death.
- [ ] Switch each prayer immediately before and after normal, triple and Soul launches; confirm post-launch switching cannot change the launched protection decision.
- [ ] Test Soul drains 30/15/20/10, low Prayer clamp, zero HP damage when drained to zero, Elysian proc 22/non-proc30, and Ward exclusion from unprotected Soul/lava damage.
- [ ] Test every lava protection combination: initial10 always; continued15/12/11 as specified; adjacent tiles take zero; exactly three unique pools.
- [ ] Test all chamber edges, diagonals, standing underneath, flinching/red-X support, normal melee adjacency, distant triple melee and no corridor attacks.
- [ ] Cross fire both ways; reset while Souls/projectiles/lava exist; return and confirm HP600/sitting, correct wall cleanup and no stale hit.
- [ ] Leave by command, external teleport and logout/disconnect during every special. Verify one participant maximum and empty-instance destruction; enter another clone and check isolation.
- [ ] Use a normal recoverable-death test account through the existing authorized test ingress: die with expendable items, retrieve the normal pile at (1310,1249,0), confirm no late damage after respawn. Test simultaneous boss death separately.
- [ ] Test bones message, poison, venom, ordinary burn, thralls, and supported Arclight/Emberlight/demonbane/Scorching bow through normal combat.
- [ ] Confirm no task bonuses/Slayer XP/task decrement/Brimstone on no task; verify genuine hellhound/Cerberus and Konar assignments including the final task kill.
- [ ] Verify KC message/+1, actual collection drops, all six native achievements and no duplicate awards. Mutating `::cerbdebug` kills must award none; subsequent ordinary kills must regain eligibility.
- [ ] Log out and back in after real rewards, then perform two controlled restarts and verify KC, collection, achievements, inventory and bank. The completed operational restarts did not replace this kill-and-relog persistence test.
- [ ] Compare synchronized OpenRune packets/video to the full OSRS capture and resolve every evidence gap above before marking parity accepted.

## Files modified or added in this audit

Paths below are relative to the canonical root. Existing unrelated dirty work was preserved; this list does not claim authorship of the repository's other changes.

```text
.data/raw-cache/server/npcs.toml
content/bosses/cerberus/build.gradle.kts
content/bosses/cerberus/src/main/kotlin/org/rsmod/content/bosses/cerberus/CerberusRules.kt
content/bosses/cerberus/src/main/kotlin/org/rsmod/content/bosses/cerberus/CerberusEncounterManager.kt
content/bosses/cerberus/src/main/kotlin/org/rsmod/content/bosses/cerberus/CerberusHitModifiers.kt
content/bosses/cerberus/src/main/kotlin/org/rsmod/content/bosses/cerberus/CerberusSpotanimValidation.kt
content/bosses/cerberus/src/main/kotlin/org/rsmod/content/bosses/cerberus/CerberusDeathDropLocation.kt
content/bosses/cerberus/src/main/kotlin/org/rsmod/content/bosses/cerberus/CerberusModule.kt
content/bosses/cerberus/src/test/kotlin/org/rsmod/content/bosses/cerberus/CerberusDecisionTest.kt
content/bosses/cerberus/src/test/kotlin/org/rsmod/content/bosses/cerberus/CerberusDeathDropLocationTest.kt
content/drops/src/main/kotlin/org/rsmod/content/drops/tables/monsters/CerberusDropTable.kt
content/drops/src/test/kotlin/org/rsmod/content/drops/tables/monsters/CerberusDropTableTest.kt
engine/game/src/main/kotlin/org/rsmod/game/hit/Hit.kt
engine/game/src/main/kotlin/org/rsmod/game/hit/HitBuilder.kt
engine/game/src/test/kotlin/org/rsmod/game/hit/HitValidityTest.kt
api/player/build.gradle.kts
api/player/src/main/kotlin/org/rsmod/api/player/hit/PlayerExtensions.kt
api/player/src/main/kotlin/org/rsmod/api/player/hit/processor/StandardPlayerHitProcessor.kt
api/player/src/test/kotlin/org/rsmod/api/player/hit/modifier/LaunchPrayerSnapshotTest.kt
api/combat/combat-formulas/src/main/kotlin/org/rsmod/api/combat/formulas/maxhit/MaxHitOperations.kt
api/death/src/main/kotlin/org/rsmod/api/death/PlayerDeathDropLocationHook.kt
api/death/src/main/kotlin/org/rsmod/api/death/PlayerDeath.kt
api/death-plugin/src/main/kotlin/org/rsmod/api/death/plugin/DeathDropHooksModule.kt
or-cache/build.gradle.kts
or-cache/src/main/kotlin/dev/openrune/CerberusConfigInstaller.kt
or-cache/src/test/kotlin/dev/openrune/CerberusR240CacheAuditTest.kt
scripts/cerberus-transcribe.ps1
scripts/cerberus-search-captures.ps1
scripts/CerberusAuditShutdown.java
scripts/cerberus-controlled-restart.ps1
docs/cerberus-rev240-evidence.md
docs/cerberus-20260907-audit-report.md
```

The inactive `api/player/src/integration/.../StandardPlayerHitProcessorTest.kt` was temporarily edited during investigation; added tests were removed when its absent task was discovered. It contains no retained Cerberus test change.

Intentional cache installation updated only SERVER configuration archive 58 NPC records 5862/5863/5866 (and the cache library's container/index metadata). Original record backups:
`logs/cerberus-audit-20260907/cache-before/npc-5862.bin`, `npc-5863.bin`, `npc-5866.bin`.
Capture binaries/transcripts, search JSON, restart logs and shutdown-helper compilation artifacts are additional audit outputs in that same log directory. No generated source/build output was hand-edited.
