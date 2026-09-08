# Zulrah combat mechanics: revision-240 ID map

## Scope and evidence

This map covers combat-facing definitions used by the OpenRune Zulrah encounter. It does not map access, loot, collection-log, achievement, or unrelated teleport definitions.

- Primary capture: `rsprox-820-rev234.bin`
- Capture SHA-256: `40A571CD18DBB50965FCBF3363FC5D4050A45ABD0AC9036753808F9B659915EB`
- Capture header: revision `234.1`, world `679`
- Target: OpenRune revision `240`
- Donor: `MouldyToast/Offline_Scape` commit `1bb62850e9a1d315c9ce01f36ff936fae9c140c6`
- Cache verification: `ZulrahR240CacheAuditTest`

Numeric equality between revisions is not treated as proof. Every live definition is addressed through an OpenRune semantic key. The audit resolves those keys against the revision-240 cache and asserts the expected revision-240 value and relevant definition metadata.

## NPCs and temporary entities

| semantic name | captured rev-234 ID | target rev-240 ID | type | mapping evidence | code location |
|---|---:|---:|---|---|---|
| `npc.snakeboss_boss_ranged` | 2042 | 2042 | NPC | Capture spawn/transform; r240 name, model/size, Attack option, 500 HP and defence params | `ZulrahConfig.kt`, `ZulrahEncounterManager.kt` |
| `npc.snakeboss_boss_melee` | 2043 | 2043 | NPC | Capture transform; r240 semantic cache definition and magma defence params | same |
| `npc.snakeboss_boss_magic` | 2044 | 2044 | NPC | Capture transform; r240 semantic cache definition and tanzanite defence params | same |
| `npc.snakeboss_minion_melee` | not visible | 2045 | NPC | Donor/Wiki branch; r240 semantic cache name, 1 HP, attack stats/options | `ZulrahConfig.kt`, `spawnSnakeling` |
| `npc.snakeboss_minion_magic` | not visible | 2046 | NPC | Donor/Wiki branch; r240 semantic cache name, 1 HP, magic stats/options | same |

Revision-240 form params preserve the form-specific defence state: green magic defence `-45`/ranged `+50`, magma magic `0`/ranged `+300`, and tanzanite magic `+300`/ranged `0`. The implementation transmogrifies to the semantic NPC definition instead of substituting numeric IDs.

## Locations and arena

| semantic name | captured rev-234 ID | target rev-240 ID | type | mapping evidence | code location |
|---|---:|---:|---|---|---|
| `loc.snakeboss_poisoncloud` | 11700 | 11700 | location, 3x3 | Capture add/remove packets; r240 semantic name, 3x3 dimensions and collision metadata | `ZulrahConfig.kt`, `spawnCloud` |
| `loc.snakeboss_exit` | 11701 | 11701 | location | Capture post-kill exit; r240 semantic definition | `ZulrahConfig.kt`, `ZulrahWorld.kt` |
| shrine template | dynamic instance | regions 9007 + 9008 | map regions | Capture U-shaped geometry cross-checked against r240 map regions | `ZULRAH_AREA` in `ZulrahConfig.kt` |
| shrine entry | dynamic | canonical `(2268,3068,0)` | coordinate | Capture local-coordinate normalization and donor shrine entry | `ZULRAH_AREA` |
| middle surface/facing | dynamic | `(2266,3073)` / `(2268,3065)` | coordinate | Capture transforms/facing plus donor/Wiki | `ZulrahPosition.MIDDLE` |
| south surface/facing | dynamic | `(2266,3062)` / `(2268,3068)` | coordinate | Donor/Wiki, normalized to r240 template | `ZulrahPosition.SOUTH` |
| west surface/facing | dynamic | `(2257,3071)` / `(2268,3073)` | coordinate | Donor/Wiki, normalized to r240 template | `ZulrahPosition.WEST` |
| east surface/facing | dynamic | `(2276,3071)` / `(2270,3074)` | coordinate | Donor/Wiki, normalized to r240 template | `ZulrahPosition.EAST` |
| magma pillar safe tiles | not exercised | `(2263,3072)`, `(2264,3072)`, `(2272,3072)`, `(2273,3072)` | coordinates | Donor and current Wiki strategy diagram | `ZULRAH_TAIL_SAFESPOTS` |

Cloud action coordinates represent the 3x3 centre. The projectile and location packet target is the south-west object origin `(centre.x - 1, centre.z - 1)`, matching the capture.

## Sequences

| semantic name | captured rev-234 ID | target rev-240 ID | type | mapping evidence | code location |
|---|---:|---:|---|---|---|
| `seq.snakeboss_attack_acidx1` | 5069 | 5069 | sequence | Capture standard/cloud launch animation; r240 semantic sequence, 2 ticks | `ZulrahAssets.ATTACK` and `SUMMON` |
| `seq.snakeboss_spawn` | 5071 | 5071 | sequence | 47 captured initial spawns; r240 cache | `start` |
| `seq.snakeboss_sinkfast` | 5072 | 5072 | sequence | 12 captured dives; r240 cache | `beginDive` |
| `seq.snakeboss_emergefast` | 5073 | 5073 | sequence | 12 captured emerges/transforms; r240 cache | `finishDive` |
| `seq.snakeboss_death` | 5804 | 5804 | sequence | Captured boss deaths; r240 cache | NPC death queue/cache definition |
| `seq.snakeboss_attack_tail_left` | 5806 | 5806 | sequence | One captured tail animation; r240 cache | `launchTail` |
| `seq.snakeboss_attack_tail_right` | 5807 | 5807 | sequence | Four captured tail animations; r240 cache | `launchTail` |
| `seq.snakeboss_defend` | not observed | 5808 | sequence | r240 semantic cache definition | validation list |
| `seq.snakeboss_pet_attack` | not observed | 1741 | sequence | r240 snakeling default/semantic definition | `spawnSnakeling`, `attackSnakeling` |
| `seq.snakeboss_pet_death` | not observed | 2408 | sequence | r240 snakeling death semantic definition | snakeling death queue |

`seq.snakeboss_attack_acidx3` resolves to `5068` in revision 240 and is the NPC default sequence, but it is deliberately not used for the captured launch event. The capture uses `5069`. This distinction is regression-tested.

## Graphics and projectile controllers

| semantic name | captured rev-234 ID | target rev-240 ID | type | mapping evidence | code location |
|---|---:|---:|---|---|---|
| `spotanim.snakeboss_orb` | 1044 | 1044 | graphic | Captured ranged projectile; r240 gameval | `directAttack` |
| `spotanim.snakeboss_double_orb` | 1045 | 1045 | graphic | Captured cloud volleys; r240 gameval | `launchCloudVolley` |
| `spotanim.snakeboss_fireball` | not observed | 1046 | graphic | Wiki/donor magic branch; r240 gameval/model | `directAttack` |
| `spotanim.snakeboss_egg` | 1047 | 1047 | graphic | Seven captured egg projectiles; r240 gameval | `launchSnakelingVolley` |
| `spotanim.snakeboss_minion_spell` | not observed | 1230 | graphic | Donor/Wiki snakeling branch; r240 gameval | `attackSnakeling` |
| `projanim.zulrah_attack` | packet fields | 18 | server projectile controller | Capture: `370→50`, start 40, end 90, angle 0, progress 0 | `projectiles.toml`, `directAttack` |
| `projanim.zulrah_cloud_primary` | packet fields | 19 | server projectile controller | Capture: `370→20`, start 40, end 90, angle 0 | `projectiles.toml`, `launchCloudVolley` |
| `projanim.zulrah_cloud_secondary` | packet fields | 20 | server projectile controller | Capture: `370→20`, start 45, end 120, angle 10 | same |
| `projanim.zulrah_snakeling_egg` | packet fields | 21 | server projectile controller | Capture: `370→20`, start 40, end 120, angle 0 | `projectiles.toml`, `launchSnakelingVolley` |
| `projanim.magic_spell` | not observed | semantic existing controller | server projectile controller | Existing OpenRune magic controller; placeholder for unrecorded visible snakeling magic projectile | `attackSnakeling` |

Controllers 18–21 are server-only definitions created from captured packet timing. They do not reuse a revision-234 controller number.

## Sounds

| semantic name | captured rev-234 ID | target rev-240 ID | type | mapping evidence | code location |
|---|---:|---:|---|---|---|
| `synth.waterwave_cast_and_fire` | 213 | 213 | synth | Captured ranged launch; existing unique r240 semantic key | `ZULRAH_RANGED_SEND_SOUND` |
| `synth.zulrah_ranged_impact` | 224 | 224 | synth | Capture/donor; newly named previously-unmapped r240 synth | `ZULRAH_RANGED_IMPACT_SOUND` |
| `synth.firewave_cast_and_fire` | not observed | 162 | synth | Donor magic launch; existing unique r240 semantic key | `ZULRAH_MAGIC_SEND_SOUND` |
| `synth.firewave_hit` | not observed | 163 | synth | Donor magic impact; existing unique r240 semantic key | `ZULRAH_MAGIC_IMPACT_SOUND` |
| `synth.zulrah_snakeling_send` | 788 | 788 | synth | Captured egg launch; named and asserted in r240 map | `ZULRAH_SNAKELING_SEND_SOUND` |
| `synth.zulrah_cloud_send` | 790 | 790 | synth | Captured cloud launch; named and asserted in r240 map | `ZULRAH_CLOUD_SEND_SOUND` |
| `synth.zulrah_snakeling_impact` | not observed | 794 | synth | Donor snakeling attack; named and asserted in r240 map | `ZULRAH_SNAKELING_IMPACT_SOUND` |
| `synth.zulrah_cloud_dissipate_a` | 795 | 795 | synth | Captured cleanup; named and asserted in r240 map | cloud expiry/cleanup |
| `synth.zulrah_cloud_dissipate_b` | 796 | 796 | synth | Captured cleanup; named and asserted in r240 map | cloud expiry/cleanup |
| `synth.zulrah_hazard_spawn` | 1930 | 1930 | synth | Captured cloud/egg impact; named and asserted in r240 map | hazard spawn |

The first three IDs that already had unique semantic names were not aliased: OpenRune rejects duplicate game-value mappings. This is intentional and tested.

## Hits, prayer, venom, and instance ownership

| semantic state | captured rev-234 value | target rev-240 representation | evidence | code location |
|---|---|---|---|---|
| cloud damage hitmark | hit type 5, damage 1–4 | `hitmark_groups.venom`, typeless hit, no toxin application | Repeated capture hits; Wiki says clouds do not envenom | `processCloudDamage` |
| standard miss/block | hit type 12, value 0 | normal combat hit + launch-time prayer snapshot | Captured delayed zero hit; Wiki launch-tick rule | `directAttack` |
| standard venom | toxin state not exposed by the sole normal launch | `PlayerVenom.tryVenom` when accurate at launch, even if prayer reduces damage | Wiki-backed unrecorded branch | `directAttack` |
| tail hit | not observed | typeless 20–30, prayer bypass, stun and venom | Wiki/donor-backed | `processPendingHazards` |
| damage cap | not observable due League guardian | reroll 45–50 when an incoming player hit exceeds 50 | Wiki-backed and deterministic unit test | `modifyNpcHit`, `ZulrahRules` |
| per-player isolation | independent dynamic instances in capture | private `InstanceSession`, spawn owner, player-UID run registry | OpenRune instance/ownership API | `Run`, `spawnNpc`, validation hooks |

No combat varp/varbit other than the existing semantic protection-prayer varbits is introduced. Kill-count and reward varps are outside the mechanics mapping scope.
