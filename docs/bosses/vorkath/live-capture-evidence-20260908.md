# Vorkath decoded-capture evidence — 2026-09-08

This audit reads only the five supplied decoded RSProx captures. Source files are under
`%USERPROFILE%\.rsprox\binary\vorkath-audit\`. References below use original file line
numbers; bracketed capture numbers are game ticks, not milliseconds. Projectile start/end,
animation delays and spot delays are client cycles (30 per game tick).

The active target remains OpenRune revision 240. These captures themselves have headers
234.1 (3279), 227.1 (1851, 1857, 550), and 237.1 (1158), each at line 3.
The older `map_projanim_v1` dump reports quantized quarter-height values (35/31 normal,
85/9 fireball, 34/7 rapid). The revision-234 `map_projanim_v2` dump supplies the required
full heights 142/124, 340/38, 138/30. Never copy the older raw heights into the revision-240 packet.

## Reproducible checks

Read-only scripts:

```powershell
.\scripts\vorkath-capture-excerpt.ps1 -Geometry
.\scripts\vorkath-capture-excerpt.ps1 -SpawnLocations
.\scripts\vorkath-capture-timeline.ps1
.\scripts\vorkath-capture-excerpt.ps1 -Capture 3279 -FromTick 5123 -ToTick 5139 -AllLines
```

| Capture | Normal/ice projectile geometries checked | Specials with exactly six preceding normals | Within-block normal intervals, all exactly 5 ticks | Observed spawn arrivals, all launch +4 |
|---|---:|---:|---:|---:|
| 3279 | 1,037 | 173 | 972 | 88 |
| 1851 | 675 | 89 | 574 | 38 |
| 1857 | 671 | 93 | 542 | 43 |
| 550 | 164 | 29 | 152 | 10 |
| 1158 | 0 | 0 | 0 | 0 |

The supplied 1158 capture contains no matching Vorkath attack sequence or projectile evidence.
The scripts intentionally count incomplete/death-interrupted phases separately; shorter
barrages in those phases do not establish a different uninterrupted special duration.
No repeated special type within a single wake-to-death fight was found.

**All 2,547 normal/ice projectiles satisfy**
`endTime = 70 + 5 * Chebyshev(projectileSourceTile, launchTargetTile)`, with zero mismatches.
Ranged 95 and magic 80 are particular witnesses, not fixed per-style flight times.
The complete observed union is 80, 85, 90, 95, 100, 105, 110, 115 client cycles.
All other normal/ice geometry fields remain the same.
This is a formula derived from exhaustive supplied packet observations, not a recovered server script.

## Animation, flight and impact witnesses

Tuple notation is `start/end/angle/progress/startHeight/endHeight`.
`E` means the distance-derived end time above; damage/freeze timing is `floor(E/30)`,
independently of the shared OpenRune projectile helper's extra server-cycle convention.

| Attack | Animation | Projectile and tuple | Impact | Game timing | Original capture evidence |
|---|---|---|---|---|---|
| Melee | 7951 | None | Player hitsplat | Same tick as attack animation | 3279:117776,117780 (t5103:22); 120580,120584 (t5246:0) |
| Ranged | 7952 | 1477; 30/E/14/128/142/124 | 1478, height124, pre-sent delay E | t5093 launch, t5096 hit; E95 gives +3 | 3279:117602–117609,117654 |
| Magic | 7952 | 1479; 30/E/14/128/142/124 | 1480, height124, pre-sent delay E | t5139 launch, E80 => +2 | 3279:118406–118412 |
| Normal dragonfire | 7952 | 393; 30/E/14/128/142/124 | 1466 firesurge impact, height124, delay E | t5108 launch, t5110 hit7/shield message | 3279:117850–117856,117887–117891 |
| Venom dragonfire | 7952 | 1470; 30/E/14/128/142/124 | 1472 venom impact, height124, delay E | E80/90/95/100/105 observed; t6270 E90 impact/protection messages t6273 | 3279:143231,143237;139095–139099;139182–139189 |
| Prayer disable | 7952 | 1471; 30/E/14/128/142/124 | 1473 corrupting impact, height124, delay E | t5098 launch, t5100 prayers cleared and hit9 | 3279:117679,117684–117688,117708–117720 |
| Fireball | 7960, animation delay2 | 1481; 0/120/46/128/340/38 | Map animation1466, height38, fixed launch target; sound163 | Cast t5227, packet t5228 (+1), blast t5232 (+5 cast/+4 packet) | 3279:120281,120307–120309,120364–120365 |
| Acid cast | 7957 | 1483; 32/90/46/128/340/0 | Loc32000, shape10, varying rotation | Cast t5169, pools t5172 (+3) | 3279:118920–119092,119146–119258 |
| Acid rapid fire | Initial acid7957; no per-shot new sequence witnessed | 1482; 0/30/22/128/138/30 | Map animation131 fireblast impact, height30, fixed tile; sound158 | Shots t5173…5197 (+4…+28), each blast +1 | 3279:119275–119277,119297–119301,119320–119324,119699 |
| Spawn freezing breath | 7952 | 395; 30/E/14/128/142/124 | 369 ice barrage, height0, delay E | t5123 launch, frozen t5125 (+2 for E80) | 3279:118139–118145,118171 |
| Spawn launch | 7960, no delay2 | 1484; 32/120/46/128/340/0 | NPC8063 appears on fixed target | t5125 launch, t5129 add (+4) | 3279:118180–118183,118235 |
| Spawn successful death | 7891 | Player Crumble Undead uses own146 projectile/147 impact | Death sound230 | Lethal hit t5133, death/unfreeze t5136, NPC delete t5138 | 3279:118246–118256,118298,118339–118353,118396 |
| Spawn explosion | 7890 | None | Map animation1460 watersurge, delay22, height30; player hit following tick | Explosion/unfreeze t15414, hit50/delete t15415 | 3279:310213–310220,310240–310246 |

A native spot's presence is not proof it is always emitted: some fully protected attacks in
3279 omit a player impact block. Damage maxima/protection selection are not determined by
these packet IDs alone.

## Wake, arena, normal and special transitions

3279:117544–117547 records sleeping NPC8059 at SW tile (2269,4062,0), sequence7950
and immediate transformation8058 at t5085. At t5092 (+7), it becomes8061 with 750 HP
and boss HUD (117579–117587). The first normal attack is t5093 (+8).
The older1851:151152/151234 independently gives wake7344, first7352 (+8).

The six normals in the first3279 cycle are t5093,5098,5103,5108,5113,5118
(lines117606,117684,117780,117853,117963,118055), followed by freezing breath t5123.
After spawn resolution, normals t5139,5144,5149,5154,5159,5164 precede acid t5169
(lines118409,118494,118580,118659,118746,118817,118920).
After acid, normals t5202,5207,5212,5217,5222,5227 precede freezing breath t5232.
Melee therefore participates in the normal random attack sequence and does not replace it.

First-special examples differ:1851 starts with ice at7382 after six normals7352…7377
(151234–151946), while550 starts with acid31443 after normals31413…31438
(666279–667175). First-special choice is not forced to one type. Later specials alternate.

The boss SW tile remains (2269,4062) in the inspected circle-walking examples.
Projectile sources rotate around its centre (2272,4065):
south (2272,4063), north (2272,4067), east (2274,4065);
rapid southwest examples (2271,4063), (2270,4064), (2270,4065).
References:3279:118144,120308,139098,119276,119300,119353.
**The exact rounding/interpolation rule for mapping current NPC facing to these mouth tiles
is not proven by this audit.** Player launch-target position and current movement position can
differ in the same update. It is not valid to emit every attack from the boss SW tile or aim
each acid projectile's source towards its individual landing pool.
Decoded source blocks contain only coordinates; the dump does not establish sourceIndex
encoding independently.

## Acid state

All80 complete3279 acid phases have pools+3, 25 shots at+4…+28, and next normal+33.
Older captures corroborate the same complete-phase timeline (e.g.1851 acid7471,
1857 acid4803,550 acid31616).

The first3279 phase emits57 projectiles and57 loc additions.
Pools are **removed at t5197, cast+28**, the same tick as the final rapid shot
(119674–119733). Last-shot impact remains active through t5198. Loc existence is25ticks.
An exhaustive second pass reconstructed the placement pattern across **all200 acid casts**:
3279 has64 casts with57 destinations and20 with56;1851 has45/5;1857 has48/0;550 has13/5.
There were zero grid-cell or edge-range mismatches. This includes projectile launches whose
later pool creation was interrupted by death; counts describe launches, not only surviving locs.

The first48 destinations are sampled in row-major order from a7x7 grid of3x3 cells whose
origin is(2262,4055), skipping its centre cell(2271..2273,4064..4066). Tiles occupied by
Vorkath's7x7 footprint(2269..2275,4062..4068) are excluded within the remaining cells.
Then eight edge destinations follow in this exact order:

| Edge sample | X range | Z range |
|---|---|---|
| South west |2266..2268|4054|
| South east |2276..2278|4054|
| North west |2266..2268|4076|
| North east |2276..2278|4076|
| West south |2261|4059..4061|
| West north |2261|4069..4071|
| East south |2283|4059..4061|
| East north |2283|4069..4071|

The player's tile is added when not already selected, producing56 or57 unique destinations.
The full first57-destination witness at3279:118924–119092 is replayed by
`VorkathAcidLayoutTest.firstCapturedLayoutReplaysEveryProjectileDestinationInOrder`.
The seven centre tiles(2269..2275,4054) are outside every random candidate range and receive
only a forced player pool. Row4053 also has no captured pools; whether every tile on that row
is walkable requires native collision data. The implementation's uniform choice among a cell's
walkable candidates is inferred; packet distributions alone cannot recover OSRS's RNG routine.

Acid damage begins as pools appear: player hit6 at5172 (119143–119144), next hit6 at5173.
Vorkath's HP increases483→489 at5173, with green healing hitsplat6, then489→496 at5174
(119266–119295). This proves healing and hazard tick effects; it does not by itself prove
a universal heal fraction because the selected fight has Grid Master modifiers.

Rapid projectiles target a snapshot of the player's tile before that update's movement:
t5173 target(2269,4061), while player moves to(2268,4062);
t5174 target(2268,4062), while player moves to(2268,4063).
The delayed map impacts remain on the old tiles. No player target index is printed for
acid, rapid, fireball or spawn; unlike standard/ice projectiles they are tile-targeted.
Rapid damage and the exact damage-reduction interval require the Wiki plus runtime checks;
the idle7948 sequence stream alone does not expose a server damage multiplier.
Idle7948 packets often reflect a player's attack/defend response with a delayed sequence,
so they must not be used as an unconditional phase-end timer.

## Spawn state

Immunity is already active during ice flight:3279 t5124 message at118156, and1851
t7382 immunity message151934 is sent on the same tick as freezing-breath launch.
Freeze occurs at ice impact and the upward spawn launch occurs that same tick.
The spawn arrives four ticks later, then moves one tile per tick beginning the next tick.

**All184 launch targets are exactly Chebyshev8 from the frozen player's tile**, with zero
exceptions:3279=89,1851=39,1857=45,550=11. The SpawnLocations script mode follows the local
player index and movement updates before measuring. Examples:3279:118181–118183 player
(2271,4061) to(2279,4054);1851:152013 player(2274,4054) to(2266,4060);
1857:108112 player(2274,4054) to(2267,4062);550:670653 player(2277,4065) to(2279,4057).
A nominal ten-tile description is contradicted by these server-visible launch destinations.
The184 observed destinations span x2261..2283,z4054..4076, with all four extremes present.
For example3279:118183=(2279,4054),130863=(2280,4076),152963=(2261,4074).
The resulting full candidate rectangle remains inferred; these samples prove its required
minimum extent and the exact eight-tile ring, not every cell's selection probability.

Freezing breath deals zero HP damage **without a blue zero hitsplat**: of89 freeze-impact
ticks in3279,87 contain no player hit block. The other two contain unrelated periodic
six-damage hitsplats (8358:177714 id65;18584:382013 id5), not zero hitsplats.
The apparent zero at5125:118178 belongs to Vorkath's NPC update, not the player.

3279 successful example:
arrival5129 at(2279,4054); walks5130…5133 towards player(2271,4061);
Crumble Undead cast5130 (seq1166, cast145, travel146 with end81, impact147/defend7889 delay81);
lethal38 hitsplat5133; stopped spawn5134; death7891/unfreeze5136; delete5138.
Damage against Vorkath succeeds again at5137 (118376–118382).
Another successful example has lethal38 at5242, death/unfreeze5245, nextnormal5246
(120510,120556–120584).

Restart is **not universally three ticks after unfreeze**:
5136→5139 is+3,5245→5246 is+1, older1851 7397→7398 is+1.
The first example's extra delay may involve engagement/retaliation state; the dump does
not prove the underlying restart-selection rule. Do not extend immunity merely to
cover a cosmetic death animation.

Failure example:
spawn walks onto player tile(2266,4064) at15414, sequence7890/map1460, unfreezes player
immediately; player takes50 at15415, spawn disappears, and Vorkath launches its nextnormal
on15415 (310213–310246). A Crumble Undead cast15412 was too late to prevent that explosion.
This is a useful race-order regression witness. The isolated hit50 is not proof of a
universal50 damage maximum.

## Fireball, death, rewards and gaps

Fireball5227 targets(2273,4069) when its packet is emitted5228. Player moves two tiles
away5231. Blast1466 occurs5232 at the saved tile without a player hit.
Vorkath can begin its next freezing breath on5232, the same tick the previous normal
fireball lands; an encounter must retain legitimate in-flight normal work across the
special transition.

3279 death sequence7949 occurs5356 (122794). At5362 (+6), kill count and duration are
reported, ground loot appears, the active8061 is removed and sleeping8059 is added at
the same SW tile (122863–122878). HUD clear is observed5370 (122941–122942).
Those loot contents include Grid Master effects and must not replace the native drop table.

Not proven by these captures: exact live-main-game damage distribution/protection matrix,
probability weights, acid damage-reduction boundaries, exact cell-selection RNG,
mouth interpolation and source attachment index, all spawn landing candidate rules,
maximum freeze timeout independent of spawn resolution, every logout/teleport/reset
cleanup path, and live revision-240 client rendering after deployment. Use independent
Wiki/cache/runtime evidence for those items and label implementation inferences.

## Revision-240 asset verification

After capture analysis, the separate native-cache regression
`or-cache/src/test/kotlin/dev/openrune/VorkathR240CacheAuditTest.kt` was added and run:

```powershell
.\gradlew.bat :or-cache:test --tests dev.openrune.VorkathR240CacheAuditTest --console=plain
```

Result: **BUILD SUCCESSFUL, 18 seconds**, one test with zero failures/errors. It reads
revision-240 server definitions and native LIVE models without packing or modifying a cache.
Verified: NPC8058/8059/8061/8063; active750HP/size7/Attack; all10 named boss/spawn
sequences in the table; loc32000; all21 travel/impact/Crumble spots in the test, their native
models, and linked sequences. Thus none of these assets needs a substitute or cache edit.
The existing public-map spawn is also present in `.data/raw-cache/map/npcs/ungael.toml:8`
as `npc.vorkath_sleeping_noop`, coordinates `0_35_63_29_30` (2269,4062, region9023).

A second successful audit run (13 seconds) also recorded native `tickDuration` values:
7948=3,7949=7,7950=7,7951=3,7952=2,7957=30,7960=3,7889=1,7890=2,7891=1.
Native7949's rounded7-tick duration differs from the captured six-tick death-to-removal
interval; native sequence length alone must not override encounter-owned capture deadlines.
No7954/7955/7956 sequence packet was found anywhere in the five captures. No unconditional
null/idle animation reset is emitted at the first complete acid phase's final rapid shot.

## Spawn clipping and reachable landing tiles

The spawn respects Vorkath's occupied tiles. In3279, the boss remains at SW(2269,4062)
and the frozen player is at(2264,4068) (271702–271705). The spawn appears at(2272,4069)
on13745 (271759), then walks west along the boss's north edge:

| Tick | Spawn destination | Capture line |
|---|---|---|
| 13746 | (2271,4069) | 3279:271773 |
| 13747 | (2270,4069) | 3279:271795 |
| 13748 | (2269,4069) | 3279:271813 |
| 13749 | (2268,4069) | 3279:271831 |

Its direct southwest step would enter(2271,4068), inside the boss's7x7 footprint;
the observed westward step avoids that tile. Creature-wide pass-through movement would
contradict this witness. Reproduce the excerpt with
`.\scripts\vorkath-capture-excerpt.ps1 -Capture 3279 -FromTick 13739 -ToTick 13754 -Pattern '8063|player=|8061'`.

A bounded follow-up extracted78 complete launch/player coordinate pairs from3279.
All78 satisfy distance8 and can reach the player in a simulation that tries a diagonal
step, then an available horizontal or vertical step, preserving boss occupancy and
diagonal corner checks. None requires movement away from the target or a full path search.
One simulated route takes nine steps around the west edge: launch147504/t6687 from
(2263,4069) to frozen player(2269,4061). This is a reachability check, not a claim that
the capture observes every simulated step after the player kills a spawn.

**Inference boundary:** rejecting ring8 candidates that cannot reach the target with those
direct steps is consistent with these78 pairs; the exact OSRS landing-selection predicate
and random distribution remain unproven. The existing contact witness at3279:310213–310246
does prove the spawn can take its final step onto the player's tile, so player occupancy
must not prevent contact while Vorkath and map collision remain effective.
