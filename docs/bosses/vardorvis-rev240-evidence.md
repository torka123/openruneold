# Vardorvis revision-240 implementation ledger

## Sources

- OSRS Wiki: <https://oldschool.runescape.wiki/w/Vardorvis>
- OSRS Wiki strategy guide: <https://oldschool.runescape.wiki/w/Vardorvis/Strategies>
- RuneLite gamevals at commit `e1afd4502d3819700374bd045ab4dfcf6f9d82ec`.
- RSProx `%USERPROFILE%\.rsprox\binary\vardorvis-audit\rsprox-3344.txt`, SHA-256
  `1ED9CF9B0F61CA2D108BDDCED8A2727A6402394E818A413862829408DDEC0094`.
- RSProx `%USERPROFILE%\.rsprox\binary\vardorvis-audit\rsprox-3285.txt`, SHA-256
  `ADB7710D378068F5FB743B509F5E4D4DBDDB174905E2BFD30A3BA070452E4E50`.
- RSProx
  `%USERPROFILE%\.rsprox\binary\Old School RuneScape\rsprox-1703-rev226.txt`,
  SHA-256
  `31F88A69AD73C4778F0E1400DF299CB7A6179A3BC2A5795F9E852A6C85ACAC3E`.
- OpenRune revision-240 cache definitions and client scripts 1815, 1816, 1820, 1821, 1823.

## Confirmed crosswalk

| Feature | Evidence | Revision-240 implementation |
| --- | --- | --- |
| Arena | RSProx region `(17,53)`, region id 4405 | Clone region 4405 on plane 0 |
| Entry | RSProx player outside `(1117,3428,0)`, inside `(1119,3428,0)` | Safe local entry `(31,36)` and message `You enter the ritual site.` |
| Boss | Cache/RSProx | NPC 12223 at local `(40,25)`, world `(1128,3417,0)`, 700 HP, 2x2 |
| Exit | RSProx | Open 47603; sealed 47604 while active |
| Melee | Wiki plus cache param 14 | Sequence 10340, five ticks, max 32 scaling to 43, 75% melee-prayer mitigation, heals floor(actual damage / 2) |
| Axes | RSProx cycles 19472-19480 | Eight cardinal/diagonal lanes; 12225 wind-up using 10364/10365, 12227 moving with 10366, 12229 static with 10336; five visible movement ticks at 1/2/2/2/1 tiles; swept three-tile-wide collision; max 35 |
| Bleed | RSProx hitmark 67 and Wiki | 3 damage pulses for 14 ticks; running changes interval from three ticks to one |
| Darting Spikes | RSProx 2510/2512, Wiki geometry and cache 2513-2518 | Consecutive reposition dashes; complementary 3x5 half-X waves and a seeded four-tile third wave in the 5x5 area; inward single-crack edge fallback; max 25; heal half actual damage; each wave adds one tick of body delay |
| Head | RSProx | NPC 12226, cast 2550, projectile 2521 (15/90, 124 to 96, angle 8), impact 2552, 10 Prayer drain and three-tick overhead lock on failure |
| Strangle | RSProx interface 833 and Wiki | Sequences 10342/10343/10344; client script 1816; four to eight Destroy targets; five-tick window; 25 failure damage |
| Death/loot | RSProx and existing drop pipeline | Sequence 10346, existing owner-only drop table at boss tile, 18,000-tick private visibility |
| Respawn | Wiki | 17 server ticks (10.2 seconds, displayed as approximately ten seconds) with full encounter-state reset |

## Confirmed versus inferred

Confirmed from packets/cache: room coordinates and plane, normal NPC id and stats, axe entity transforms and movement cadence, head projectile parameters, prayer drain/lock messages, native Strangle interface/scripts, death sequence, and exit ids.

Wiki-confirmed: health-scaled strength/defence, melee max-hit scaling, prayer mitigation, lifesteal, health thresholds, bleed movement acceleration, five-tick Strangle window, and respawn duration.

Still inferred because the available captures do not expose it directly: normal-mode special selection weights, the bonus third axe chance between 231 and 690 HP, the exact four random tiles used by Darting Spikes' third wave, the exact inward edge crack, and integer rounding of scaled combat stats. The implementation keeps these values centralized and deterministic for replay tests, keeps the unconfirmed middle-health bonus axe disabled, and does not claim packet-confirmed precision for those gaps.
