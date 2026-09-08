# Duke Sucellus repair — 7 September 2026

## Result and limits

Duke now has one dedicated, deterministic encounter runtime, covering preparation, normal and Awakened combat, pending-hit cancellation, death and room reset. The instance copies every source plane from region 12132, including the links over both side corridors. The generic DT2 combat scheduler no longer drives Duke.

175 selected checks passed: 132 DT2 tests, 1 real revision-240 Duke cache audit, 20 instance tests, 16 player tests and 6 reward/context tests. The server and Duke JAR rebuilt successfully. Deployment verification is recorded below. These tests are not a visual client playthrough. Exact equivalence to every OSRS mechanic is not claimed; the remaining evidence gaps are listed explicitly.

The accidentally posted Collection Log/spell-lock request was excluded from this work.

## Source and persistence authority

- Canonical tree: `<repo-root>`.
- Pre-change doctor verified GameServer PID 36844 with this JVM working directory.
- Revision remains 240. Server definitions use `.data/cache/SERVER`; JS5 uses `.data/cache/LIVE`.
- SERVER dat2 SHA-256: `899D421EE88ADE6CE7956EC2A13A09ADCC4882264D27B1CF2BC1CED42AC821E3`.
- LIVE dat2 SHA-256: `5B2CA5B369D2A137C0387DE407F2499BCA18929B6D0BCDAB40AB89D21287691A`.
- `.data/postgres` remains the existing player/realm persistence authority. No save/database migration or replacement was made.
- The tree already contained extensive unrelated edits; the DT2 directory itself was untracked. An empty Git diff for that directory is not evidence of no changes. Existing work was retained; no generated source was edited or restored from Git.

## Runtime behavior

| Mechanic | Normal | Awakened |
| --- | --- | --- |
| HP | 485 | 1697 |
| Attack / Strength / Defence / Magic | 300 / 345 / 275 / 310 | 435 / 500 / 316 / 465 |
| Poison doses | 2 | 3 |
| Base attack interval | 5 ticks | 5 ticks |
| Enrage | 121 HP; 4 ticks | 440 HP; 4 ticks |
| Gas count | 1, then 2 at 242 HP | 1, then 4 below 1290 HP |
| Slam / magic maximum before prayer | 56 / 48 | 81 / 71 |
| Additional mechanics | — | Bile and moving darkness orbs |

Stats are set on each spawned encounter NPC. They do not overwrite the cache. The requested Awakened visual is 12195, while the underlying NPC type stays 12191 for the existing combat/drop-table identity. NPC option/item-use dispatch resolves the visible type, so asleep Feed remains routed through 12167. Cache 12195 has quest-like stats and must not be treated as the authoritative Awakened stat source.

Preparation uses the real inventory transaction API, Mining pickaxe selection/animation/delay, repeated mushroom gathering, eight mushrooms per patch, Herblore powder yields and level-dependent salt yields. Vats consume complete batches atomically, ferment for six ticks, retain doses when inventory is full, support Check/Empty and preserve spare poison/vat contents between kills.

The room installs tools, salt, mushroom patches, vats, eyes and gates at translated source coordinates. Preparation hazards use the captured middle/south/north eye order, vent grid, ice graphics and corridor bounds.

The standard rotation has five attacks before gaze and eight before the first gas attack. Subsequent gas spacing is latched at the preceding gas attack and gas cannot immediately follow gaze. Magic impacts four ticks after launch; slam checks position after two; gaze checks after four and resumes after six. Multi-spit gas uses offsets 0/3 or 0/3/5/7.

Duke runs at `GameLifecycle.StartCycle`, before player movement. Hits enter the real player hit queue with delay 1, which is consumed by the player processor in that cycle. Delay 0 is illegal in this engine. Prayer/position checks occur at the mechanic's impact phase. The hit validity callback also checks instance membership, generation and timeline epoch, so resetting or leaving invalidates an already queued hit.

Bile checks existing pools before placing new drips. Running includes the intermediate movement tile. Existing pools are not repeatedly refreshed, and expire nine ticks after creation. Death/exit removes them immediately. Darkness orbs move across the front row and drain eight levels from released skills other than HP, whose damage follows the hit system.

Ward of Arceuus is checked through its actual timed state for Duke's regular damage. Protected melee chip retains its normal range; peripheral hazards do not receive Ward reduction. The shared modifier gained an explicit opt-out so Duke can apply Ward selectively; default behavior for other callers is unchanged.

Death uses animation 10181, dead eyes after two ticks, disabled resources/gate after four, and a corpse until reset. Reset is 23 ticks from death, as repeatedly observed. Reward placement is source tile (3039, 6451). The existing reward pipeline receives difficulty, eligibility, event identity and Duke's perfect flag. Helper NPC references are detached from the instance manager when removed, preventing accumulation over repeated kills.

## Capture evidence

Decoded capture files are under `%USERPROFILE%\.rsprox\binary\duke-sucellus-audit\rsprox-<id>.txt`; binary originals remain beside them. References below are file line numbers; tick numbers are decoded capture ticks, not wall time.

| Evidence | Capture / lines | Observed ticks or values |
| --- | --- | --- |
| Sleeping form / preparation objects | 3285:303536–303547 | Asleep 12167, tools/resources |
| Middle, south, north extremities | 3285:303571–303646 | 19789 / 19791 / 19793; repeat middle at 19804 |
| Ice shadow to impact | 3285:303603–303701 | 1447 at 19791; 538 at 19795 |
| Preparation vent anchors | 3285:303852–303856 | (3041,6441), (3038,6445), (3035,6449) |
| Mushroom depletion | 3285:305240–305457 | Picks at 19864 / 19867 / 19870 / 19873; eighth depletes patch |
| Iron pickaxe salt gathering | 3285:308409–308571 | Animation 626 at 20009; first yield at 20015 |
| Wake to first standard | 3285:312555–312731 | Wake 20197; first magic 20202 |
| First rotation | 3285:312727–314061 | Standards 20202/207/212/217/222; gaze 227; resume 233; gas 248 |
| Magic projectile | 3285:312731 | 2434, delay 50, end 120, angle 5, progress 32, heights 350→100 |
| Melee and icicles | 3285:313164–313227 | 10176 + 2439 at 20217; seven 2440–2443 row graphics; impact 20219 |
| Gaze check/resume | 3285:313470–313572; 1703:182180–183077 | Check +4, resume +6; safe pillar then movement on check tick |
| Gas sequence | 3285:314053–314327 | Spits 20248/251/253/255; 2436 projectile; vent 2431/2432/2433 |
| Bile visual | 3285:314548–314654 | 10178 and 1354 at 20259; 30032 pool at 20260 |
| Running bile trail | 555:305940–306135 | Pools on both running tiles, including (3042,6439) and (3042,6440) |
| Pool expiry | 555:317662–318363 | 15450→15459, 15455→15464, 15457→15466, 15458→15467: nine ticks |
| Darkness orb | 3285:314327; 555:317657–318050 | 11708; one tile per tick across the front row; warning 382/spawn 379 |
| Death sequence | 3285:314795–314907 | 10181/12192; dead eyes +2; disabled objects +4 |
| Death, reward, reset | 1827:31096–31304 | Death 1673; KC/drop +4; asleep/reset 1696 = +23 |
| Repeated reset timing | 1827; 91:244068–244353 | 1731→1754, 1780→1803, 1834→1857, 1934→1957; 11010→11033 |

Reproduce a bounded excerpt with:

```powershell
.\scripts\duke-capture-excerpt.ps1 -Capture 555 -From 317590 -To 318370 -Pattern '30032|loc_del' -Limit 60
```

Capture labels/IDs disagree across revisions: 1703 shows 12195 with an older normal-like 440 HP fight; 3285 shows 12191 with 1697 HP, tolerance 370, four gas spits, bile and darkness orbs. Therefore NPC ID alone is not a mode classifier. Capture 555 is an older Awakened recording (1540 HP); using its nine-tick pool lifetime in revision 240 is an inference, although its individual pool additions/removals are directly observed.

Current reference consulted: [Duke Sucellus strategies](https://oldschool.runescape.wiki/w/Duke_Sucellus/Strategies). Stats, preparation quantities and current mode thresholds were cross-checked there. Packet timing takes precedence where the prose timing is approximate.

## Tests and build

```powershell
.\gradlew.bat :content:bosses:desert-treasure-ii:test --console=plain
.\gradlew.bat :or-cache:test --tests dev.openrune.DukeR240CacheAuditTest :api:instances:test :api:player:test :content:drops:test --tests org.rsmod.content.drops.Dt2AwakenedCompletionTest --tests org.rsmod.content.drops.NpcDeathKillContextTest :server:app:classes --console=plain
.\gradlew.bat :content:bosses:desert-treasure-ii:jar :server:app:classes --console=plain
```

- Final DT2 run: 132 passed, zero skipped/failures/errors; `logs/duke-tests-20260907.log`.
- Integration run: 43 passed, zero skipped/failures/errors; `logs/duke-integration-20260907.log`.
- `DukeRuntimeTest`: actual revision-240 cache, NPC registry/repository, inventory transactions, emitted hit queues and modifiers. Session/scene IO and accuracy are mocked; it does not run the complete player movement/HP/death processor or render a client.
- Runtime checks include both wake modes, atomic vat handling, full inventory, translated hazards, impact timing/prayer, pillar avoidance, cancellation, death scene, bile attack reservation, safe movement, running trail/expiry, Ward expiry and orb skill drains.
- `DukeRulesTest`: rotation, mode thresholds, latched gas intervals, geometry, preparation quantities, timeline generation/epoch cancellation and deterministic repeated sequences.
- Cache audit reads actual LIVE map group 12132, asserts upper-plane links in both corridors, validates NPC forms, animations, item definitions, visual models and sounds, including death animation's four-tick duration.

## Deployment verification

Controlled rebuild succeeded. The final source, including the separate Awakened magic-defence correction, survived controlled restarts 14 and 15. Restart 13 was an earlier verification before that last correction.

| Run | JVM PID | Build marker (server local time) | Doctor |
| --- | --- | --- | --- |
| Preliminary restart 13 | 11712 | 15:27:14 | `logs/duke-doctor-restart13.log` |
| Final build, restart 14 | 29068 | 15:42:27 | `logs/duke-doctor-restart14.log` |
| Final build, restart 15 | 36108 | 15:48:19 | `logs/duke-doctor-restart15.log` |

Startup stdout/stderr are `logs/cerberus-audit-20260907/restart-<number>.stdout.log` and `.stderr.log`, using the existing controlled-restart helper. Both final runs bound port 43594, reported revision 240 and `OPENRUNE CUSTOM BUILD ACTIVE`, and completed doctor with the canonical JVM `user.dir`. Both cache fingerprints exactly matched the pre-change values above. Final gameplay source was re-read after restart 15; the selective Ward handling, nine-tick pools, running trail and separate magic-defence assignment remained present. Selected Git whitespace checks passed and the final working-tree status/diff were inspected.

The startup marker reports the unchanged commands JAR, so its old modification timestamp is not a Duke build timestamp. The Duke module was rebuilt through Gradle before deployment and again by the normal run dependency graph where required. Startup logs could not resolve the Git branch/commit. Each startup logged one cycle overrun (832 ms and 773 ms on the final two runs); no encounter exception or duplicate-registration error was observed. This is startup/runtime verification, not an observed Duke kill.

## Changed source inventory

Paths below are relative to the canonical root and list this Duke repair's work, not all pre-existing uncommitted changes.

- `content/bosses/desert-treasure-ii/src/main/kotlin/org/rsmod/content/bosses/dt2/DukeEncounter.kt` — dedicated runtime and preparation.
- `content/bosses/desert-treasure-ii/src/main/kotlin/org/rsmod/content/bosses/dt2/DukeRules.kt` — source geometry, timings, deterministic rotation and timeline.
- `content/bosses/desert-treasure-ii/src/main/kotlin/org/rsmod/content/bosses/dt2/Dt2Config.kt` — all-plane room copy, Duke entry and asset registration.
- `content/bosses/desert-treasure-ii/src/main/kotlin/org/rsmod/content/bosses/dt2/Dt2EncounterManager.kt` — Duke dispatch, stats, reward position, death/reset and cleanup integration; removed obsolete Duke handlers.
- `content/bosses/desert-treasure-ii/src/main/kotlin/org/rsmod/content/bosses/dt2/Dt2World.kt` — authoritative preparation/Feed/vat/exit option routing.
- `content/bosses/desert-treasure-ii/src/main/kotlin/org/rsmod/content/bosses/dt2/Dt2Lifecycle.kt` — Duke tick phase and leave/teleport hooks.
- `content/bosses/desert-treasure-ii/src/main/kotlin/org/rsmod/content/bosses/dt2/Dt2Combat.kt` — Duke death animation and perfect reward context.
- `content/bosses/desert-treasure-ii/src/main/kotlin/org/rsmod/content/bosses/dt2/Dt2Rules.kt` — removed superseded Duke timing rules; dose requirement delegates to DukeRules.
- `content/bosses/desert-treasure-ii/build.gradle.kts` — existing Mining implementation dependency; real-cache test setup and Mockito.
- `content/bosses/desert-treasure-ii/src/test/kotlin/org/rsmod/content/bosses/dt2/DukeRuntimeTest.kt`, `DukeRulesTest.kt`, `Dt2CaptureReplayTest.kt` — runtime/rule tests and room-copy expectation.
- `api/instances/src/main/kotlin/org/rsmod/api/instances/InstanceManager.kt` — encounter-owned NPC detachment only; unrelated existing edits retained.
- `api/player/src/main/kotlin/org/rsmod/api/player/hit/modifier/StandardPlayerHitModifier.kt` and `NpcBypassProtectionPrayerPlayerHitModifier.kt` — optional selective Ward handling.
- `or-cache/src/test/kotlin/dev/openrune/DukeR240CacheAuditTest.kt` — real-cache audit.
- `scripts/duke-capture-excerpt.ps1` and this report — reproducible evidence extraction and ledger.

## Remaining evidence and client checks

These are limitations, not confirmed parity:

- No normal or Awakened client playthrough was observed during this repair. Upper-plane copying is tested structurally; camera appearance, click routing, gathering interruption, actual kills/drops and post-restart login require client observation.
- Exact cone tile masks and all four pillar-safe tiles need collision/position capture confirmation. Two pillar tiles are directly observed; neighboring tiles are inferred.
- Preparation ice cadence, exact gas damage/reduction rounding and gaze freeze behavior are not fully established by the captures.
- Awakened post-gas bile selection is currently an HP-gated, non-overlapping schedule; the precise OSRS selection rule remains unproven. Leak duration follows the current reference; pool expiry follows older capture 555.
- Orb cadence currently follows the current approximately-ten-second reference (17 ticks). Older capture 555 has a 12-tick cadence, so it must not be presented as matching that older recording. Random initial orb side and all impact boundaries need further observation.
- The existing Duke drop table does not consume `perfectKill` for a quantity bonus. The runtime now supplies the flag, but this repair preserves the existing reward table rather than claiming the reward bonus has been implemented.

Client verification: enter each difficulty through the existing Duke entry, inspect both raised corridors, gather/grind/fill/collect and Feed; test melee step-out, late prayer changes, pillar gaze avoidance, gas, Awakened running bile/orbs; kill and wait for the 23-tick reset; then test exit, teleport and logout while hazards are pending. Check spare poison retention between kills and removal on leaving.

## Mushroom picking crash follow-up

The user reported a black screen and crash while picking mushrooms after the initial deployment. Restart 15's server log records the same failure at 15:57:31 and 16:30:58: `IllegalStateException: Invalid sequence ID: -1`, originating at `DukeEncounter.gather` line 357. The eighth mushroom depleted the patch and then called `access.anim(-1)` to stop picking. Animation priority lookup rejects that ID before the reset can happen.

The authoritative gathering handler now calls the existing `access.resetAnim()` API. No cache or engine validation changes were needed. `DukeRuntimeTest` now executes the real protected-access coroutine, player animation methods and inventory transactions for all eight mushrooms from both patches, in both difficulties and translated instance coordinates. It checks the three-tick harvest timing, normal completion, released access, animation reset, depleted scene and harmless stale clicks.

The new test failed before the fix with the exact production exception and passed after it. The complete DT2 suite then passed 133 tests with no failures, errors or skips, and the Duke JAR/server classes build succeeded. Evidence: `logs/duke-mushroom-regression-before.log`, `logs/duke-mushroom-tests-after.log`, and `logs/duke-mushroom-build.log`. Scene/session IO is mocked in this test; a client retest is still needed to confirm the reported black screen is resolved in play.

The fix survived controlled restarts 16 and 17. Their JVM PIDs were 20280 and 26160, and their `OPENRUNE CUSTOM BUILD ACTIVE` markers were logged at 17:46:11 and 17:47:37 respectively. Both bound port 43594 at revision 240. Doctors confirmed the canonical working directory and unchanged SERVER/LIVE cache fingerprints; see `logs/duke-mushroom-doctor16.log` and `logs/duke-mushroom-doctor17.log`. Each startup logged one cycle overrun (720 ms / 873 ms); no encounter exception was observed during startup checks. The corrected gathering source remained present after both restarts. Git status and the existing scoped diff were reviewed; this follow-up changes only the gathering reset, its regression test and audit evidence.
