# Zulrah combat mechanics: capture validation

## Recording identity and limitations

Primary recording: `rsprox-820-rev234.bin`

- SHA-256: `40A571CD18DBB50965FCBF3363FC5D4050A45ABD0AC9036753808F9B659915EB`
- Header: revision `234.1`, world `679`
- Environment: Grid Master: Forestry League world
- Successful-kill delimiters: kill counts 2 through 48, or 47 kills

The recording was decoded in full, not sampled from only one entry. It is heavily accelerated by a League guardian, so most kills end during the opening phase. It is authoritative for the events below, but it cannot prove unrecorded branches.

Aggregate combat coverage:

| observed element | capture count |
|---|---:|
| initial green spawn, NPC 2042 + sequence 5071 | 47 |
| red transform, NPC 2043 | 6 |
| blue transform, NPC 2044 | 5 |
| later green transform, NPC 2042 | 1 |
| ranged projectile 1044 | 1 |
| magic projectile 1046 | 0 |
| snakeling egg projectile 1047 | 7 |
| visible snakeling NPC 2045/2046 | 0 |
| cloud-projectile packet lines containing 1045 | 182, usually two projectiles per line |
| cloud-location packet lines containing 11700 | 304 |
| dive sequence 5072 | 12 |
| emerge sequence 5073 | 12 |
| tail sequence 5806 | 1 |
| tail sequence 5807 | 4 |

## Tick matrix

Dynamic capture coordinates are normalized to the canonical shrine template where the mapping is known. “Unknown” means the recording did not reveal the branch; no value was invented.

| run/time | game tick | rotation/phase | Zulrah form + position | sequence/animation | graphic/projectile + delay/path | player tile/prayer/state | damage/venom/result | target code | match status |
|---|---:|---|---|---|---|---|---|---|---|
| representative KC2 run/start | 10362 | opening phase | green 2042, middle | 5071 spawn | none | capture `(10726,15261)` moving toward local entry | encounter active | `start`, `ZULRAH_INITIAL_EMERGE_TICKS` | capture matched |
| KC2 opening volley 1 | 10373 | opening action 1 | green, middle | 5069 | two 1045 projectiles: primary `370→20,40→90,0°`; secondary `370→20,45→120,10°`; local origins `(2268,3068)`, `(2271,3068)` | local `(2268,3068)`, overhead capture value 1 | sound 790 | `launchCloudVolley` | capture matched |
| KC2 cloud 1 primary | 10376 | opening action 1 impact | green | 5069 for next volley | loc 11700 appears at first origin, sound 1930 | outside/near cloud | no toxin state change from the cloud | `spawnCloud` | capture matched (+3 ticks) |
| KC2 opening volley 2 | 10376 | opening action 2 | green, middle | 5069 | primary/secondary 1045 to local origins `(2265,3068)`, `(2262,3069)` | same instance | sound 790 | `launchCloudVolley` | capture matched (3-tick cadence) |
| KC2 cloud 1 secondary | 10377 | opening action 1 impact | green | — | second loc 11700 | — | cloud active | pending hazard queue | capture matched (+4 ticks) |
| KC2 opening volley 3 | 10379 | opening action 3 | green, middle | 5069 | 1045 pair to local origins `(2272,3071)`, `(2272,3074)` | attacking guardian; prayer value not relevant to cloud | sound 790 | `launchCloudVolley` | capture matched |
| KC2 cloud 2 impacts | 10379–10380 | opening action 2 impact | green | — | 11700 primary then secondary | — | clouds active | `spawnCloud` | capture matched (+3/+4) |
| KC2 opening volley 4 | 10382 | opening action 4 | green, middle | 5069 | 1045 pair to local origins `(2262,3072)`, `(2262,3075)` | local shrine | sound 790 | `launchCloudVolley` | capture matched |
| KC2 cloud 3 impacts | 10382–10383 | opening action 3 impact | green | — | 11700 primary then secondary | — | clouds active | `spawnCloud` | capture matched (+3/+4) |
| KC2 cloud 4 impacts | 10385–10386 | opening action 4 impact | green | — | 11700 primary then secondary | — | clouds active | `spawnCloud` | capture matched (+3/+4) |
| KC2 transition | 10386 | end opening | green, middle | 5072 dive | no new combat projectile | player remains in shrine | Zulrah untargetable | `beginDive` | capture matched |
| KC2 transform | 10389 | next phase | red 2043, middle | 5073 emerge | transformation + option re-enable | player local shrine | magma phase active after lock | `finishDive`, `finishEmerge` | capture matched (+3 ticks) |
| KC2 magma telegraph 1 | 10392 | red phase | red, middle | 5807 | no projectile | player attacks boss | no captured tail damage branch | `launchTail` | animation/order matched; hit branch unrecorded |
| KC2 magma telegraph 2 | 10399 | red phase | red, middle | 5806 | no projectile | player attacks boss | no captured tail damage branch | `launchTail` | 7-tick animation separation matched |
| KC2 death | 10404 | red phase interrupted | red 2043 | 5804 death | clouds removed during completion | player alive | boss dead, combat state closed | `beginBossDeath`, `clearHazards` | capture matched |
| KC2 post-death | 10410 | complete | boss removed | — | exit 11701 added | player local entry | kill-count delimiter 2 | `finishBossDeath` / existing drops | lifecycle matched; loot out of scope |
| later run/ranged launch | 11578 | opening interrupted | green 2042 | 5069 | 1044: `370→50,40→90,0°`, target player, sound 213 | overhead `-1`; local player tile not restated in packet | attack launched; simultaneous cloud damage 4 | `directAttack` | capture matched |
| same run/boss lethal hit | 11579 | opening interrupted | green | boss receives lethal hit | ranged projectile remains outstanding | overhead unchanged; cloud exposure | cloud damage 2; boss HP reaches 0 | `beginBossDeath` | capture matched |
| same run/delayed impact | 11581 | boss already dead | removed/dying | — | previously launched 1044 resolves | player moves; overhead state not re-evaluated | hit type 12, value 0 | queued combat hit from `directAttack` | capture matched; proves fired hits survive death |
| all captured egg launches | multiple | early phases only | mixed captured form | 5069 | 1047: `370→20,40→120,0°`, sound 788 | target is a tile | seven launches; resulting NPCs not visible before fast kills | `launchSnakelingVolley` | packet matched; spawn/combat branch unrecorded |
| captured cloud exposure | multiple | opening/early phases | mixed | — | active 11700 footprint | player within 3x3 | hit type 5, damage 1–4 each tick; no cloud envenom observed | `processCloudDamage` | capture matched |
| captured cloud cleanup | multiple deaths/transitions | completion | mixed | — | 11700 removal; sounds 795/796 | player remains in instance | no stale cloud persists into next entry | `clearHazards`, expiry | capture matched |

## Rotation program and non-capture validation

The capture does not contain a complete natural four-rotation playthrough. The implementation therefore uses the pinned donor’s cohesive rotation programs, corrected where capture/current Wiki evidence is stronger:

- Four explicit families with 11/11/12/13 phases.
- Exact surface positions and fixed facing points.
- Exact action order and explicit attack counts per phase; no unseeded action/form/tile substitution.
- Final green phase performs five ranged attacks plus the captured opening cloud pattern.
- A completed family selects another official family. The same family may repeat.
- Donor coordinate typo `2075` was corrected to `3075`.
- The first opening secondary cloud centre was corrected to captured `(2272,3069)`.
- The final Serp snakeling layout is two per side, following current Wiki evidence.
- `attack_acidx3` is 5068 in revision 240; captured launches use `attack_acidx1` 5069.

These programs are asserted structurally by `ZulrahRotationTest`: all phase counts, every phase form/position/facing, every ordered action signature, opening targets, Jad counts/order, and explicit hazard targets.

## Implemented branches not demonstrated by this capture

| mechanic | implementation source | deterministic coverage | capture status |
|---|---|---|---|
| blue 2:1 magic/ranged mix | current Wiki + donor | `ZulrahRulesTest` | magic projectile absent |
| Jad alternating style/count | current Wiki + donor rotations | rules + full rotation signatures | absent |
| launch-tick prayer snapshot | current Wiki; sole capture launch timing | source review/compile | protected branch absent |
| venom on accurate standard attack even if prayer blocks damage | current Wiki | source review/compile | toxin outcome absent |
| >50 damage cap reroll to 45–50 | current Wiki | unit test | League guardian makes player-hit evidence unusable |
| tail 20–30, prayer bypass, stun, venom, target escape and four pillar safe tiles | current Wiki + donor | rule/safe-tile/timing tests | no player tail impact |
| snakeling 1/3 magic, 2/3 melee, max 15, four-tick cadence, 67-tick expiry | donor + Wiki | timing constants/source validation | spawned NPC/attacks absent |
| visible snakeling magic projectile | donor/r240 semantic asset | cache/source validation | absent; generic OpenRune magic controller remains |
| private-instance isolation | OpenRune instance ownership system | ownership guards/source review | capture entries are sequential, not concurrent |
| death, logout, teleport, instance-exit cleanup | OpenRune lifecycle hooks | source review and module compile | boss-death cloud cleanup observed; other exits absent |
| post-first-family unpredictable deviations | Wiki mentions deviations but gives no exact probability/program | not invented | absent and not implemented |

## Verification results

- `:content:bosses:zulrah:test` — passed.
- `:or-cache:test --tests dev.openrune.ZulrahR240CacheAuditTest` — passed.
- Focused total — 22 tests in 4 suites, 0 failures, 0 errors, 0 skipped.
- `:server:app:shadowJar` — passed; full live distribution compiled and packaged.
- Two clean revision-240 deployment restarts — passed; port 43594, 399 scripts, empty final stderr log.
- Repository-wide `build` — stopped only at pre-existing `api/music` Spotless violations (`Music.kt` unused import and `MusicRepository.kt` spacing). No Zulrah compile/test failure occurred.
- Revision-240 semantic NPC, location, sequence, graphic, projectile-controller, and synth mappings — passed.
- Captured opening timing, cloud origin/footprint, projectile timing, dive/emerge cadence, and delayed-hit behavior — represented in code and deterministic tests where separable.

## Proof boundary

The capture-matched subset is verified. The entire fight is **not yet proven 1:1 live** because the supplied capture does not expose complete official rotations, the standard magic branch, a protected standard attack, visible snakeling combat, a tail hit/avoid result, concurrent instances, or all termination paths. A revision-240 client smoke run through `::zulrahdebug 1` through `::zulrahdebug 4`, including intentional hit/avoid cases and two concurrent accounts, is still required for full 1:1 proof.
