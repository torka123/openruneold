# Cerberus revision-240 evidence ledger

September 7 audit status: **not verified 1:1**. See
[the implementation and verification report](cerberus-20260907-audit-report.md).
Existing timings and donor geometry below describe the implementation, not a captured OSRS fight.
The expanded real-cache audit passes; that proves asset availability and definitions, not their
packet ordering. No usable full fight was found in the public database searches.

## Sources and authority

| Source | Use | Limitation |
|---|---|---|
| OSRS Wiki Cerberus and strategy pages | Current health, cadence, thresholds, attack order, damage rules, and player-facing mechanics | Does not expose packet profiles or every audiovisual cycle |
| OpenRune revision-240 cache and raw map dump | NPC/object/animation/spot-animation definitions, room region, sitting spawn, size, stats, and actions | Cache presence does not prove runtime timing |
| Local RSProx submissions 1702 and 3250 | Account state, Key Master dialogue, kill-count and Combat Achievement variables | Neither capture contains NPC 5862/5863 or a complete fight |
| Near-Reality donor at commit `f0744c78168eefcd28ac75b6a0f64234767e1852` | Historical reference for room geometry, firewall placement, Soul lanes, and effect profiles | Does not prove current OSRS coordinates, timing, sound use, or choreography |
| Before-state OpenRune recording | Proves the prior broken runtime: already-active boss, no wake, firewall, Souls, or visible lava | Not evidence of live OSRS timing |
| RuneLite generated constants | Cross-check for named Cerberus animation/effect IDs | Historical mechanics helper was removed at Jagex request |

## Confirmed revision-240 crosswalk

| Feature | OpenRune mapping | Status |
|---|---|---|
| Western room | Source region `4883` | Cache-confirmed |
| Safe entry | Source `(1240,1226,0)`, local `(24,10)` | Coordinate conversion tested; 24 tiles from boss anchor |
| Sitting boss | NPC `5863` at source `(1238,1250,0)`, local `(22,34)` | Raw map and cache-confirmed |
| Active/reset forms | NPC `5862` / `5866` | Cache-confirmed |
| Summoned Souls | NPCs `5867`, `5868`, `5869` | Cache-confirmed |
| Fire wall | Object `23105`, local `x=23..25,z=26` | Donor/cache cross-check; lifecycle is server-controlled and private |
| Boss footprint | 5x5, 600 HP, combat 318 | Cache-confirmed |
| Boss animations | `4484..4489`, attacks `4490..4495`, lava `4501` | Cache-confirmed; 4490 remains the correct ranged rear-up animation |
| Soul animations | `8526..8531` | Cache-confirmed |
| Graphics | Semantic family `1241..1248` | Cache-confirmed |
| Drops | Existing `CerberusDropTable` | Reused; no duplicate table |

## Runtime mechanics implemented

- Private one-player region-4883 instance, safe entry, public exit, generic Leave Instance tab,
  `::leavecerberus`, teleport-out cleanup, logout cleanup, and destroy-when-empty behavior.
- Sitting NPC `5863` remains inactive in the corridor. Chamber entry begins animation `4486`, then
  transforms to active NPC `5862` and only then launches the opening triple.
- One-based six-tick attack clock with triple > Souls > lava > ordinary priority. The special skip
  roll is isolated to Souls/lava and has deterministic force/suppress test branches.
- Normal melee is selected only while touching the actual 5x5 footprint, uses Stab accuracy, and
  ranged ordinary selection contains only magic/ranged. Triple melee remains distance-independent.
- Separate magic/ranged projectile profiles and head-aware 5x5-footprint launch coordinates. The
  normal projectiles are resolved through the revision-240 RSCM map and attached to Cerberus's NPC
  slot while retaining the selected head tile. The claimed client rendering repair still needs
  an observed fight comparison. Prayer is snapshotted independently as each
  triple/Soul component launches. Attack animations have a priority window so defend reactions
  cannot cancel them.
- Three Souls spawn west-to-east at local `(23..25,49)`, walk nine tiles south, attack at two-tick
  lane gaps, retreat north, and despawn. Protected launches drain prayer without a false zero hit.
- The opening triple uses its dedicated `4493` spray animation while its magic, ranged, and melee
  components launch two ticks apart. Lava uses animation `4501`, creates three legal single-tile
  targets outside Cerberus's footprint, and activates each `1246` pool on that tile's actual `1247`
  projectile-arrival cycle rather than a shared guessed delay.
- The private three-object firewall is raised on activation, deals five damage when crossed, stays
  through the walk-back reset, then is removed when Cerberus sits.
- On disengage Cerberus uses reset NPC `5866`, walks back through players, plays the sitting
  transition, heals, and becomes sitting NPC `5863`; a bounded teleport fallback prevents a stuck
  instance. Death, leave, logout, teleport-out, and respawn clear Souls, lava, firewall, and queued
  encounter-scoped actions.
- `::cerbdebug` can show phase/counter/phase-start tick, set HP/counter, force normal styles/Souls/lava,
  and force or suppress the special skip. Any mutating debug action permanently disables rewards
  for that kill.

## Verification coverage

September10 architecture migration: ordinary selection/volleys and timed phases now execute
through BossSpec/BossCombat/BossEncounter. Native InstanceScript owns room membership and the
same configured actor's death/reveal. Soul/lava/firewall code is a focused arena handler; pending
Soul callbacks are cancelled through the encounter.75 module tests and2 revision-240 audit tests
pass. Full mappings, exact commands, backups and deployment status are recorded in
[DSL_MIGRATION.md](bosses/DSL_MIGRATION.md). This preserves the draft mechanics below; it does
not turn donor-derived geometry or missing packet evidence into verified OSRS facts.

- Exact source region, entry, boss anchor, and safe distance.
- HP, six-tick cadence, attack thresholds, one-based special timeline, priority collisions, and
  forced skip/use branches.
- 5x5 footprint distance, ordinary style pool, Stab selection path, and centre launch coordinate.
- Chamber boundary (`z=26` inactive, `z=27` active), three-tile firewall crossing geometry, lava
  legality, Soul lane positions/timing, animation-lock rule, and stale-session cleanup.
- Revision-240 NPCs, forms, actions, object `23105`, animations, graphics, and reused drop items.

## Evidence still required before claiming pixel/tick-perfect 1:1

- A complete current OSRS RSProx fight for exact magic/ranged source heights, angles, speed,
  impact cycles, and exact lava audio/lifetime. Separate projectile configurations are now wired,
  but the two profiles intentionally preserve the prior values until real packet evidence exists.
- Exact Soul launch, prayer-drain, retreat and despawn tick ordering, including simultaneous death.
  The previous claim that shared Ward state was absent was incorrect: the audit located
  `ArceuusSpellState.hasWard`, and Cerberus now uses it. Its 20/10 Prayer drain cases are covered
  by rule tests; unprotected Souls and lava bypass generic Ward hitpoint reduction.
- Global Elite Combat Achievement reward-tier state for the 1/95 clue rate, elite clue-step state,
  and a project-wide multicannon placement system.
- A replacement live OpenRune recording showing wake, opening triple, each ordinary attack,
  complete Souls, lava, firewall crossing, walk-back reset, death cleanup, and sitting respawn.

## September 7 public-capture search

Queried `https://rsprox.net/api/search` using NPC IDs 5862/5863/5866/5867/5868/5869,
mapsquares 4883/5139/5395, all required boss and Soul sequence IDs, object 23105, coordinate
text variants, both overhead messages, and spot animations
1241 through 1248. Mapsquares returned only the existing 1702 and 3250 submissions.
NPC, boss sequences, Soul sequences, coordinate text and wall searches returned no submissions.
Graphic searches returned 3323, 3329 and 416. All three originals were saved and decoded:

- `logs/cerberus-audit-20260907/captures/rsprox-3323.bin` and `rsprox-3323.txt`
- `logs/cerberus-audit-20260907/captures/rsprox-3329.bin` and `rsprox-3329.txt`
- `logs/cerberus-audit-20260907/captures/rsprox-416.bin` and `rsprox-416.txt`

None contains a Cerberus fight. In 3323 and 3329 another NPC (7286) uses graphics 1242/1243
around coordinates 226x/565x. This is shared asset use, not Cerberus evidence. Revision-235
decoder NPC type-cache lookup failed, so those transcripts retain numeric NPC identifiers;
the relevant update packets are still visible. Search responses are saved alongside the captures.
1702/3250 remain account, map and dialogue evidence only. Their tick numbers must not be
mistaken for NPC IDs when searching transcript text.

## Current source links

- [Cerberus](https://oldschool.runescape.wiki/w/Cerberus)
- [Cerberus strategies](https://oldschool.runescape.wiki/w/Cerberus/Strategies)
- [Key master teleport](https://oldschool.runescape.wiki/w/Key_master_teleport)
- [Ward of Arceuus](https://oldschool.runescape.wiki/w/Ward_of_Arceuus)
- [RuneLite AnimationID](https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/gameval/AnimationID.java)
- [RuneLite SpotanimID](https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/gameval/SpotanimID.java)
- [RSProx public database](https://rsprox.net/database)

## Local evidence paths

- Before video: `%USERPROFILE%\Documents\Codex\2026-08-31\https-oldschool-runescape-wiki-w-royal\outputs\cerberus-video-audit\cerberus-current-state-medal.mp4`
- RSProx transcripts: `%USERPROFILE%\Documents\Codex\2026-08-31\https-oldschool-runescape-wiki-w-royal\outputs\cerberus-captures\transcripts\cerberus-1702.txt` and `cerberus-3250.txt`
- Donor source: `C:\tmp\cerb-nearreality-862c4697209e406986edd28aedb0d639\near-reality-server-main`
