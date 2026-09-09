# Vorkath repair validation — 8 September 2026

## Scope and status

This is the historical 8 September repair/deployment record. The subsequent
[boss DSL migration](../DSL_MIGRATION.md) replaces its private timeline and instance wiring;
that migration has **not** been deployed or live-verified. The runtime claims below apply only
to the earlier build, not the current source. Its capture evidence and mechanical findings remain.

Canonical source: `<repo-root>`. Native revision **240**, SERVER and LIVE cache paths retained. The initial doctor confirmed JVM **19780** in this tree. Existing uncommitted source, player persistence and reward tables were preserved.

**Implemented, deployed and live engine audit passed:** 99 Vorkath, 60 spell, 25 shared combat/player and one native-cache test passed. Restart35 completed the full live engine/protocol audit with75 assertions. Two final controlled restarts passed doctor/startup checks, and all24 source/test hashes survived both unchanged. The active server is JVM38808 from the canonical tree. Rendered-client and complete 1:1 verification are not claimed.

## Evidence and implementation

- [Capture evidence](live-capture-evidence-20260908.md): five supplied decoded RSProx files; four contain Vorkath. Their source revisions differ from the target cache; all selected native assets were independently verified in revision 240.
- [Wiki and official RuneLite evidence](wiki-runelite-evidence-20260908.md): mechanics outside packet visibility and native names. Where indexed Wiki prose conflicts with captures (spawn distance), the captures prevail.
- A single encounter timeline drives wake, six normal attacks, alternating specials and recovery. Pending projectiles, hits, pools, healing, spawn arrival/death and cleanup belong to that encounter.
- Scene effects now run before scene update buffers are sent. The previous post-tick hook could enqueue effects after those buffers had already been dispatched.
- Exact projectile geometry is supplied through the shared NPC combat override; unrelated attacks retain their existing defaults.
- Dragonfire now rolls the pre-potion damage and then subtracts protection, rather than rolling uniformly up to a reduced maximum.
- Acid uses the captured 48 jittered grid cells, eight edge segments and optional distinct player tile: 56/57 targets, preserving the exit lane.
- Existing Crumble Undead, death/drop tables, killcount and PB authorities remain in use. No duplicate spell or reward implementation was added.
- The first live audit exposed an arena-membership error: `InstanceSession.regionIds` stores allocation IDs, not packed map-square IDs. Membership now uses the authoritative mapped Ungael origin, plane and 64×64 bounds together with session identity. The test fixture was updated to use real allocation-ID semantics.
- The live fight also revealed that generic defend animation7954 could replace a cast. Activated Vorkath now uses the existing per-NPC defend-animation suppression flag; spawn defend behavior is retained. A regression exercises the actual combat defend helper during an acid animation.
- Restart30 exposed a spawn landing behind Vorkath and becoming stuck against his body. Spawn candidates now require a reachable direct-step path; only the final step may overlap the frozen player. Boss and map collision remain enforced. The captured north-edge path is a regression witness (`rsprox-3279.txt:271773`, ticks13746–13749). All78 inspected launch/player pairs fit direct-step reachability; the hidden landing-selection rule is still inferred.

## Attack reference

All tick offsets below are game ticks. Projectile start/end/delay values are client cycles (30 per game tick). For normal/ice projectiles, **E = 70 + 5 × Chebyshev(mouth tile, launch target tile)**. All 2,547 captured normal/ice packets match this rule. The supplied ranged95/magic80 values are individual distance samples.

| Attack | Animation | Projectile | Impact | Timing and evidence |
|---|---|---|---|---|
| Wake | 7950; sleeping8059 → awakening8058 | — | Active8061, HP750 | Active +7, first normal +8; RSProx3279 |
| Melee | 7951 | — | Melee hit | Same tick; five-tick normal cadence; captures |
| Ranged | 7952 | 1477 | 1478, height124, delayE | start30/endE, angle14, progress128, heights142→124; hit +floor(E/30); captures |
| Magic | 7952 | 1479 | 1480, height124, delayE | Same distance formula/geometry; captures |
| Dragonfire | 7952 | 393 | 1466, height124, delayE | Same distance formula/geometry; captures; damage/protection Wiki-derived |
| Venom dragonfire | 7952 | 1470 | 1472, height124, delayE | Same distance formula/geometry; venom at impact; captures/Wiki |
| Prayer-disable dragonfire | 7952 | 1471 | 1473, height124, delayE | Same distance formula/geometry; disables prayers at impact even at zero damage; captures/Wiki |
| Fireball | 7960, animation delay2 | 1481 | Map1466, height38; sound163 | Projectile cast+1, start0/end120, angle46, progress128, heights340→38; fixed launch tile, blast cast+5; captures |
| Acid cast | 7957 | 1483 | Pool32000 | start32/end90, angle46, progress128, heights340→0; pools cast+3; captures |
| Acid rapid fire | Native ongoing acid pose/facing | 1482 | Map131, height30; sound158 | start0/end30, angle22, progress128, heights138→30; 25 launches cast+4…+28, each impact +1; pools removed +28; next normal +33; captures |
| Freeze breath | 7952 | 395 | Player369, height0, delayE; no damage/hitsplat | Normal geometry; freeze at +floor(E/30), boss immunity starts at launch; captures |
| Spawn launch | 7960 | 1484 | NPC8063, HP38 | Launch on freeze impact; start32/end120, angle46, progress128, heights340→0; lands launch+4 at Chebyshev8; captures |
| Spawn explosion | 7890 | — | Map1460, height30, delay22 | Contact on player tile; unfreeze immediately; damage and removal +1; captures. Full-health damage60 Wiki-derived |
| Spawn killed | 7891 | Existing Crumble Undead path | Native death | Lethal hit+3 death/unfreeze; removal hit+5; capture witness; recovery commonly unfreeze+1 |
| Vorkath death | 7949 | — | Existing loot and sleeping8059 | Native animation with captured six-tick death wait; capture animation-to-loot/sleep +6 (cache duration alone rounds to seven) |

Six normal attacks precede each special; every within-block launch is five ticks apart. First special is randomized; subsequent specials alternate.

## Verification results

- Native cache audit: `:or-cache:test --tests dev.openrune.VorkathR240CacheAuditTest` **PASS**, one test. Verified four NPCs, ten sequence aliases/definitions, 21 spot definitions with model data and linked sequences, acid object32000. No native asset missing; no cache edits.
- Shared NPC override test: `:api:combat:combat-scripts:test --tests org.rsmod.api.combat.NpcAttackOverrideTest :api:combat:combat-scripts:compileKotlin` **PASS**, five targeted cases.
- Full affected suites: `:api:combat:combat-scripts:test :api:player:test` **PASS**, 8 combat +17 player tests, zero failures/errors/skips.
- Vorkath module: `:content:bosses:vorkath:test` **PASS**, 99 tests, zero failures/errors/skips. Suites: Rules43, Timeline7, Projectiles6, Dragonfire5, AcidLayout5, SafeLane3, EncounterManager24, existing StorageCodec6. Final manager XML timestamp: 2026-09-08T09:36:27.979Z.
- Full spell suite: `:content:skills:magic:spell-attacks:test` **PASS**, 60 tests, including seven Crumble Undead cases and the existing ancient/Arceuus/Twinflame regressions. Initial combined execution exhausted the test worker heap during the unrelated Twinflame cache contract. The rerun used `-I logs/vorkath-spell-test-memory.gradle` (2GB heap, one class per worker), passed all60 with no skips, and did not change gameplay or global build settings. Final suite timestamps: 09:31:29–09:32:33Z.
- Build: `:content:bosses:vorkath:assemble :content:skills:magic:spell-attacks:assemble :server:app:classes` **PASS**, combined with both full test tasks and the above temporary validation init (`--no-daemon --console=plain`, Gradle exit0, 1m38s). The final harness-only rebuild and full Vorkath suite passed in36seconds. Source persistence across both final restarts: **PASS**.
- Live engine/protocol audit: restart28 correctly loaded the revision240 build, but its first audit failed during wake and uncovered the membership bug above. It is **not** counted as a passed live audit; the corrected build will be exercised again.
- Restart29 passed native wake, six normals at five-tick cadence, pool appearance+3 and all25 shots+4…+28. Its walking assertion failed because the synthetic player retained an automatic retaliation interaction; the harness now mirrors the normal click handler, including cancellation and native route finding. That attempt also exposed the generic defend animation replacement fixed above. No failed attempt is counted as a complete live pass.
- Restart30 reproduced the spawn collision stall; the collision and reachable landing selection were corrected and regression tested. Restart31 then passed both natural specials, safe-lane walking and spawn failure resolution. Its later assertion exposed a harness tracking error: an incidental second attack overwrote the forced attack's recorded projectile. Observation now tracks the first matching requested attack and its own impact deadline.
- Restart32 passed both specials, all seven standard attacks, exact prayer-disable impact, fireball movement/landing, and resets during all three pending hazard types. The Crumble Undead stage then found a harness lookup using a raw cache name; it now resolves the existing spell through the native RSCM-aware `isType` API.
- Restart33 reached the registered Crumble Undead handler but it rejected the spawn. A real revision240 cache test proved NPC8063 has internal name `npc.vorkath_spawn` and generic undead parameter0. The existing spell now recognizes the prefixed native name and permits this specific spawn before applying the generic undead restriction. Its accuracy threshold and lethal-hit rules use the same identity. No cache mutation or second spell implementation was needed.
- Restart34 emitted Crumble Undead travel146. The audit had incorrectly assumed player projectiles always have a negative source attachment; this generic spell uses a fixed source. The audit now distinguishes the native Crumble travel ID and owned spawn target before checking boss projectile geometry. Final Vorkath tests, assemble and server classes were rerun after this harness change: **PASS**, 36seconds.
- Restart35 **LIVE PASS**, 75 assertions: [complete live log](../../../logs/vorkath-live-audit-latest.log). Canonical JVM27844; startup marker09:37:41Z, ready09:37:44Z. Both special orders are covered by deterministic tests; this natural live run selected spawn then acid. Six-normal blocks were ticks30/35/40/45/50/55 and76/81/86/91/96/101. Acid cast106, pools109, rapid shots110…134, normal combat resumed139. Registered Crumble Undead succeeded twice, including lethal-hit+3 unfreeze. All ten boss projectile specifications, all seven standard attacks, prayer disable at224, fixed fireball landing, pending-hazard resets and every listed lifecycle stage passed.
- Doctor35 **PASS**, native240 and original SERVER/LIVE fingerprints: [runtime/cache excerpt](../../../logs/vorkath-doctor-restart-35-excerpt.log). All24 source/test hashes matched before and after restart35: [hash verification](../../../logs/vorkath-source-restart-35-verified.json).
- Restart36 **PASS**, controlled normal shutdown and ordinary server startup with the audit disabled. Canonical JVM38808; startup marker09:45:24Z, ready09:45:26Z. Doctor exit0 confirms revision240, canonical working directory and the same SERVER/LIVE fingerprints: [runtime/cache excerpt](../../../logs/vorkath-doctor-restart-36-excerpt.log). [Final persistence verification](../../../logs/vorkath-source-final-persistence.json) compares all24 source/test hashes before restart35, before restart36 and after restart36; every hash matches. Startup logs contain a first-cycle duration warning; no encounter exception or failed startup occurred.
- Targeted tracked diff whitespace check **PASS**. The dirty-tree status still contains1361 entries. Vorkath and several existing shared additions are untracked, so their source was reviewed directly; the full unrelated dirty diff was not treated as this repair's changes.

Intermediate compile/fixture failures were corrected and superseded by the successful final build and full test execution above. The last fixture correction disabled developer rewards in the lethal-boss cleanup test, matching the other lifecycle-only tests; its isolated fixture does not load custom killcount mappings.

## Live-test checklist

The opt-in audit uses the active server's real game loop, cache, collision, NPC AI, player hit queues, spell registry and scene packet buffers. Its disposable player has accountId=0 and characterId=0, receives no rewards and is removed without persistence. It does **not** run a graphical client.

- [x] Native arena entry, wake and 750HP boss
- [x] Six normals at five-tick intervals and alternating specials
- [x] Every normal attack and distinct projectile geometry
- [x] Prayer disabling at impact
- [x] Acid delayed pools, 25 shots, cleanup and safe lane
- [x] Spawn launch/arrival, freeze, real Crumble Undead success and failure explosion
- [x] Fixed fireball target and delayed blast
- [x] Native death and fresh sleeping boss
- [x] Reset, leave, external teleport, disconnect flag, logout event, player death and re-entry
- [x] Two controlled restarts and doctor/startup-marker checks, with unchanged source hashes
- [ ] Rendered client visual review (separate from engine/protocol audit)
- [ ] Real-account loot, killcount/PB award and physical socket disconnect: reward authority was preserved, but the disposable audit deliberately disables rewards and invokes the disconnect flag/logout event directly.

## Remaining inference boundaries

Do not read the verified packet geometry as a claim that every hidden live-server rule is proven:

- Exact fireball minimum/base distribution is unresolved. The existing uniform0–121 policy remains; the 121 maximum and halved adjacent damage are Wiki-derived.
- Complete attack weights are unresolved. Magic:ranged3:4 is verified Wiki guidance; unit weights for other attacks remain inferred.
- Uniform sampling inside the captured acid cells, acid minimum1, random spawn direction and partial-health explosion rounding are inferred.
- Filtering the captured distance-eight spawn ring for reachable direct-step paths is inferred, consistent with all78 inspected pairs. Captures show boss-edge clipping and do not support walking through Vorkath or a need for BFS. The final player-overlap step is required by captured contact/explosion behavior.
- Exact mouth interpolation during turning and source attachment encoding are not exposed by decoded packets. Geometry uses a two-tile mouth offset and fixed source attachment; named as inferred.
- Precise damage-roll/prayer-sampling and acid reduction boundary rules are not fully observable. Launch-time damage modification follows the existing engine; immunity is also checked at impact.
- Successful spawn recovery has capture variation; the common one-tick recovery is implemented.
- Already transmitted projectiles/map effects cannot be recalled by the available protocol. Reset cancels all future encounter-owned work and removes instance hazards/NPCs.
- Graphical rendering and exact hidden random distributions require further evidence; they must not be labelled tested merely because the live engine audit passes.

## Exact files changed during this repair

Paths are relative to the canonical project root. Existing earlier dirty changes in shared files were preserved.

- `content/bosses/vorkath/build.gradle.kts`
- `content/bosses/vorkath/src/main/kotlin/org/rsmod/content/bosses/vorkath/VorkathConfig.kt`
- `content/bosses/vorkath/src/main/kotlin/org/rsmod/content/bosses/vorkath/VorkathRules.kt`
- `content/bosses/vorkath/src/main/kotlin/org/rsmod/content/bosses/vorkath/VorkathEncounterManager.kt`
- `content/bosses/vorkath/src/main/kotlin/org/rsmod/content/bosses/vorkath/VorkathCombat.kt`
- `content/bosses/vorkath/src/main/kotlin/org/rsmod/content/bosses/vorkath/VorkathLifecycle.kt`
- `content/bosses/vorkath/src/main/kotlin/org/rsmod/content/bosses/vorkath/VorkathTimeline.kt`
- `content/bosses/vorkath/src/main/kotlin/org/rsmod/content/bosses/vorkath/VorkathProjectiles.kt`
- `content/bosses/vorkath/src/main/kotlin/org/rsmod/content/bosses/vorkath/VorkathDragonfire.kt`
- `content/bosses/vorkath/src/main/kotlin/org/rsmod/content/bosses/vorkath/VorkathAcidLayout.kt`
- `content/bosses/vorkath/src/main/kotlin/org/rsmod/content/bosses/vorkath/VorkathLiveAudit.kt`
- `content/bosses/vorkath/src/test/kotlin/org/rsmod/content/bosses/vorkath/VorkathRulesTest.kt`
- `content/bosses/vorkath/src/test/kotlin/org/rsmod/content/bosses/vorkath/VorkathTimelineTest.kt`
- `content/bosses/vorkath/src/test/kotlin/org/rsmod/content/bosses/vorkath/VorkathProjectilesTest.kt`
- `content/bosses/vorkath/src/test/kotlin/org/rsmod/content/bosses/vorkath/VorkathDragonfireTest.kt`
- `content/bosses/vorkath/src/test/kotlin/org/rsmod/content/bosses/vorkath/VorkathEncounterManagerTest.kt`
- `content/bosses/vorkath/src/test/kotlin/org/rsmod/content/bosses/vorkath/VorkathAcidLayoutTest.kt`
- `content/bosses/vorkath/src/test/kotlin/org/rsmod/content/bosses/vorkath/VorkathSafeLaneTest.kt`
- `api/combat/combat-scripts/src/main/kotlin/org/rsmod/api/combat/NvPCombat.kt`
- `api/player/src/main/kotlin/org/rsmod/api/player/hit/PlayerExtensions.kt`
- `api/combat/combat-scripts/src/test/kotlin/org/rsmod/api/combat/NpcAttackOverrideTest.kt`
- `or-cache/src/test/kotlin/dev/openrune/VorkathR240CacheAuditTest.kt`
- `content/skills/magic/spell-attacks/src/main/kotlin/org/rsmod/content/skills/magic/spell/attacks/standard/CrumbleUndeadSpells.kt`
- `content/skills/magic/spell-attacks/src/test/kotlin/org/rsmod/content/skills/magic/spell/attacks/standard/CrumbleUndeadRulesTest.kt`
- `scripts/vorkath-capture-excerpt.ps1`
- `scripts/vorkath-capture-timeline.ps1`
- `scripts/vorkath-controlled-restart.ps1`
- `docs/bosses/vorkath/live-capture-evidence-20260908.md`
- `docs/bosses/vorkath/wiki-runelite-evidence-20260908.md`
- `docs/bosses/vorkath/repair-validation-20260908.md`
