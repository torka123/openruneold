# Wilderness Agility parity evidence

## Runtime authority

The canonical server is the OpenRune-Server tree containing this document. `scripts/openrune-doctor.ps1`, the server startup marker, `.data/cache/SERVER`, and `.data/cache/LIVE` all identify revision **240** as the active server/client cache revision. Numeric identifiers below are revision-240 cache values; implementation code uses gameval names wherever the project API permits it.

Sources cross-checked on 2026-08-15: revision-240 local cache definitions and map squares, local generated `.rscm` gamevals, current Old School RuneScape Wiki pages for the course/dispenser/ticket/dungeon, Jagex's 2024 Wilderness Agility update notes, and current RuneLite `Courses.java`/`Obstacles.java`. Cache sequence duration is supporting evidence only and is not treated as a server tick timeline by itself.

## Cache and behavior table

| Feature | Gameval | ID | Revision | Coordinates | Animation/Sequence | Tick Timeline | Sound | OSRS Evidence | Implementation File | Test |
|---|---|---:|---:|---|---|---|---|---|---|---|
| Northern exit left gate | `loc.balancegate52b_left` | 23552 | 240 | (2998,3931,0), type WallStraight, rotation 3, 1x1, `Open`, clip -1 | Balance walk 4178; ready 4179 | Unrestricted exit crosses the full causeway south and clears course state | No verified obstacle-specific sound in cache | Local map/loc config; current Wiki level 52 is entry-only | `LiveWildernessAgilityCourse.kt` | access/exit tests |
| Northern exit right gate | `loc.balancegate52b_right` | 23554 | 240 | (2997,3931,0), type WallCorner, rotation 3, 1x1, `Open`, clip -1 | Balance walk 4178; ready 4179 | Same exit route as the other gate half | No verified obstacle-specific sound in cache | Local map/loc config; current Wiki | `LiveWildernessAgilityCourse.kt` | both-gate tests |
| Southern entrance gate | `loc.balancegate52a` | 23555 | 240 | (2998,3917,0), type WallStraight, rotation 3, 1x1, `Open`, clip -1 | Balance walk 4178; ready 4179 | Checks effective Agility 52, then crosses the full causeway north into the course | No verified obstacle-specific sound in cache | Local map/loc config; current Wiki | `LiveWildernessAgilityCourse.kt` | entry/access tests |
| Obstacle pipe, south end | `loc.obstical_pipe2` | 23137 | 240 | (3004,3938,0), type CentrepieceStraight, rotation 3, size 2x1, `Squeeze-through`, clip -1 | `seq.human_doublepipesqueeze` 749; frame delays 4,4,4,4,4,8,4,4,4,4,4,4,8,4,4,4,4,4 (84 client cycles) | Start validates effective Agility 49 and clears an untagged prior lap; crawl is locked and position advances through the pipe; XP/progress only on successful arrival | No verified sound mapping | Local cache; RuneLite obstacle 23137; Wiki 12.5 XP/no failure | `WildernessAgilityCourse.kt` | pipe level/order/timeline tests |
| Obstacle pipe, north end | `loc.obstical_pipe2` | 23137 | 240 | (3004,3948,0), type CentrepieceStraight, rotation 1, size 2x1 | Sequence 749 | Reverse traversal returns through the pipe without forward-lap credit | No verified sound mapping | Local map object | `WildernessAgilityCourse.kt` | reverse traversal test |
| Rope swing | `loc.obstical_ropeswing2` | 23132 | 240 | (3005,3952,0), type CentrepieceStraight, rotation 2, size 1x6, `Swing-on`, clip -1 | success `seq.human_ropeswing_long` 751 (64 client cycles); short/grab 750 (70); miss 1105 (46) | Validate ordered pipe progress; success moves to north landing then grants 20 XP/progress; failure applies `floor(current HP*0.15)+1` and moves at the failure moment to (3005,10352,0) | No verified sound mapping | Local cache; current Wiki damage/failure dungeon; surface/dungeon geometry offset +6400 | `WildernessAgilityCourse.kt` | retry/damage/concurrency tests |
| Stepping stones | `loc.steppingstone1` | 23556 | 240 | anchor (3001,3960,0), GroundDecor, rotation 0, 1x1, `Cross`, clip 0 | `seq.human_steppingstonejump` 769; delays 4,8,16,2,2,2,2,2,2,16,8,4 (68 client cycles) | Move west one stone at a time; only the third-stone checkpoint rolls failure; success grants 20 XP/progress; failure applies `floor(current HP*0.20)+1` and recovers on surface | No verified sound mapping | Local cache; current Wiki third-stone behavior | `WildernessAgilityCourse.kt` | individual movement/failure tests |
| Log balance | `loc.wilderness_log_balance1` | 23542 | 240 | (3001,3945,0), GroundDecor, rotation 3, 1x1, `Walk-across`, clip -1 | ready 763 (52 client cycles), walk 762/7134 (48), stumble 764 (76) | Validate ordered stones; early checkpoint can fail; once passed it cannot fail; success reaches west end then grants 20 XP/progress; failure applies `floor(current HP*0.15)+1` and moves to (3001,10345,0) | No verified sound mapping | Local cache; current Wiki damage/dungeon; matching surface/dungeon geometry +6400 | `WildernessAgilityCourse.kt` | checkpoint/retry tests |
| Climbing rocks | `loc.wildclimbingrock` | 23640 | 240 | (2993,3936,0) rot3, (2994,3936,0) rot2, (2995,3936,0) rot1; GroundDecor, `Climb`, clip 0 | climb 737 (44 client cycles), ready 738, fall 739, down 740 | Move south to matching completion tile at z=3933; only a full ordered state grants 498.9 XP and creates one unclaimed lap | No verified sound mapping | Local map; RuneLite completion points (2993-2995,3933) | `WildernessAgilityCourse.kt` | full/invalid lap tests |
| Agility dispenser | `loc.wildy_agility_pillar` | 53224 | 240 | (3005,3936,0), CentrepieceStraight, rotation 0, 1x1; `Tag`, `Pay`, `Redeem` | No player/loc sequence encoded | Tag always restores run; atomically claims at most one completed lap; Pay removes 150,000 inventory coins once; Redeem removes tickets and awards banded XP | No verified sound mapping | Local cache options; current dispenser Wiki/Jagex notes | `WildernessAgilityCourse.kt` | dispenser/payment/redeem tests |
| Surface ladder | `loc.wild_laddertop_dungeon` | 14758 | 240 | (3005,3963,0), CentrepieceStraight, rotation 0, `Climb-down` | Existing ladder climb sequence API | Tick-driven to corresponding safe dungeon landing; paid state preserved | Existing generic ladder audio only if supplied by shared ladder API | Local map and dungeon Wiki | `WildernessAgilityCourse.kt` | ladder direction/state test |
| Dungeon ladder | `loc.ladder_from_cellar` | 17385 | 240 | (3005,10363,0), CentrepieceStraight, west angle, `Climb-up` | Existing ladder climb sequence API | Tick-driven to corresponding surface landing; paid state preserved | Existing generic ladder audio only if supplied by shared ladder API | Local map and dungeon Wiki | `WildernessAgilityCourse.kt` | ladder direction/state test |
| Wilderness agility ticket | `obj.wildy_agility_token` | 29460 | 240 | Inventory/ground item; stackable; untradeable; bankable | n/a | One per valid tagged lap; redemption uses 200/210/220/230 XP each at 1-10/11-50/51-100/101+ | n/a | Local obj config; current ticket Wiki | `WildernessAgilityCourse.kt` | redemption boundaries |
| Looting bag | `obj.looting_bag`, `obj.looting_bag_open`; `inv.looting_bag` | 11941, 22586; inv 516 | 240 | Inventory/bank and 28-slot persistent bag inventory | n/a | Payment grants a closed bag only if none owned; paid reward deposit requires the open bag, matching the September 2025 live change | n/a | Local obj/inv config; current course/dispenser Wiki | `WildernessAgilityCourse.kt`, server inv config | ownership/full tests |
| Surface Skeletons | `npc.wildy_agility_skeleton_1` through `_5`, plus longhunt variants | 13495-13501 | 240 | 16 exact map coordinates documented below | cache-defined stand/walk/attack/defend/death sequences | Native revision-240 NPC definition: respawn rate 100, hunt range 5, wander range 5, max range 7 | cache-defined combat sounds only | Current Skeleton spawn map; local NPC definitions | `wilderness_agility_course.toml` | count/definition test |
| Dungeon Skeletons | `npc.skeleton_armed` through `npc.skeleton_armed5` | 77-81 | 240 | 8 exact map coordinates documented below | cache-defined combat sequences | Native revision-240 NPC definition: respawn rate 50 | cache-defined combat sounds only | Current Skeleton spawn map; local NPC definitions | `wilderness_agility_course.toml` | count/definition test |

There are no transformed/alternate obstacle object IDs in the revision-240 definitions above. The course locs are static and concurrent; course state and movement locks are per player. No obstacle-specific sound can be proven from the local loc/sequence configs, so the implementation does not invent one. `synth.picketgate_open` 67 and `synth.picketgate_close` 66 exist but are not evidence that these balance gates play them.

## Exact coordinates and boundaries

Surface course is map region 11837. The protected course state area covers the gate interior, obstacle floor and the failure dungeon; the boundary handler treats transitions between the surface and dungeon as internal. Completion tiles are (2993,3933,0), (2994,3933,0), and (2995,3933,0).

Surface level-18 Skeleton coordinates:

`(2998,3952) (2993,3955) (2996,3958) (2995,3959) (2995,3961) (3004,3960) (3006,3959) (3003,3951) (3006,3951) (2994,3946) (2994,3944) (2990,3940) (2997,3939) (2993,3932) (2995,3933) (2996,3932)`.

Dungeon level-25 Skeleton coordinates:

`(2994,10346) (2998,10342) (2998,10350) (3002,10356) (3003,10347) (3004,10362) (3006,10352) (3006,10357)`.

## Reward evidence

Every paid lap makes one noted resource roll and one noted armour roll into an **open** looting bag. If normal inventory capacity remains, a separate unnoted blighted-supply roll is made. A medium clue is an independent 1/40 roll, or 1/20 only while an imbued Ring of wealth is worn; existing clue ownership suppresses the roll. Current tier boundaries are laps 1-15, 16-30, 31-60, and 61+.

Resource weights in every tier are 8/31 anglerfish, 8/31 manta ray, 8/31 karambwan, and 7/31 super restore. Quantities by tier are respectively 3-6/3-6/3-6/1-2, 6-11/6-11/6-11/2-4, 10-16/10-16/10-16/3-6, and 14-20/14-20/14-20/4-8.

Armour weights:

- Tier 1, total 10: adamant full helm 1, platebody 2, platelegs 1, mithril chainbody 1, platelegs 1, plateskirt 1, rune med helm 2, steel platebody 1.
- Tier 2, total 9: adamant full helm/platebody/platelegs, mithril chainbody/platelegs/plateskirt, rune chainbody/kiteshield/med helm each 1.
- Tier 3, total 10: adamant full helm/platebody/platelegs, mithril platelegs/plateskirt each 1; rune chainbody 2, kiteshield 2, med helm 1.
- Tier 4, total 20: adamant full helm 1, platebody 2, platelegs 1, mithril platelegs 1, plateskirt 1; rune chainbody 6, kiteshield 6, med helm 2.

Optional unnoted supply weights are 5/16 each for blighted anglerfish, manta ray, and karambwan, and 1/16 for a four-dose blighted super restore.

## Persistence rules

Active obstacle progress and an unclaimed-lap marker are temporary. Paid status and paid lap streak are persistent. Logging out or hopping while still inside preserves the fee and subtracts ten laps exactly once; leaving the true combined course area, teleporting away, or dying clears paid status, streak, active progress, and an unclaimed lap. Falling into the dungeon or returning by its ladder does not clear paid state. Starting another lap before tagging the dispenser resets the reward streak but does not delete already-held tickets.

## Success/failure formula evidence

Failure damage is `floor(current Hitpoints * 0.15) + 1` for rope/log and `floor(current Hitpoints * 0.20) + 1` for stepping stones, and can kill at 1 Hitpoint. Current live behavior retains a non-zero obstacle failure chance at level 99. The local client/cache cannot prove the server-side low/high success thresholds, and the public sources reviewed do not publish them. The current 0-255 threshold curve in the implementation is therefore provisional and must not be represented as exact OSRS parity until verified against a packet capture, authoritative server trace, or repeatable live sample.

## Version note

Live behavior after the 2024 rework is used. The September 2025 open-looting-bag reward-routing change is included. The July 2026 Bounty Hunter world payment-before-entry restriction does not apply to this project's configured world 255.
