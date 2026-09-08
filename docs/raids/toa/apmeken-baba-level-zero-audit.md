# Path of Apmeken and Ba-Ba: level-zero implementation audit

Status: implemented and automated-regression clean on 2026-08-29. This is **not a 1:1 certification**. The items in “Remaining capture gaps” need a modern revision-240 live trace before they can be called exact.

## Evidence hierarchy

1. Current official OSRS update notes and current OSRS Wiki mechanics.
2. RsProx public capture 14 (`https://rsprox.net/download/14`) and the decoded local transcript `%USERPROFILE%/.rsprox/binary/Old School RuneScape/zebak-audit-rsprox-14.txt` for tick order, coordinates and visible assets.
3. OpenRune revision-240 cache (`.data/cache/SERVER`, cross-checked against `.data/cache/LIVE`) for every runtime NPC, object, sequence, projectile and spotanim ID.
4. Existing OpenRune ToA state/lifecycle systems.
5. `TOA.zip` only as secondary behavioral context. No donor Java or donor IDs were copied.

Capture 14 is revision 223 from 2024-07-23. Where it conflicts with later official changes, the current OSRS rule wins and the old trace is used only for unchanged packet order or geometry.

Current sources used:

- `https://oldschool.runescape.wiki/w/Tombs_of_Amascut/Strategies#Path_of_Apmeken`
- `https://oldschool.runescape.wiki/w/Ba-Ba`
- `https://oldschool.runescape.wiki/w/Boulder_(Tombs_of_Amascut)`
- `https://oldschool.runescape.wiki/w/Update:Summer_Sweep-Up:_Combat_%26_Loot`
- `https://oldschool.runescape.wiki/w/Update:Project_Rebalance:_Combat_Changes`
- `https://oldschool.runescape.wiki/w/Update:Poll_80_ToA_Changes_%26_DMM_Tweaks`
- `https://oldschool.runescape.wiki/w/Update:Tombs_of_Amascut:_Balancing_%26_Bugfixes`

## Level-zero mechanics ledger

| Feature | Evidence | Runtime implementation | Automated coverage | Status |
|---|---|---|---|---|
| Challenge activation | Current strategy: first entrant begins Sight; existing ToA lifecycle: red barrier authoritative | Pre-barrier supplies remain usable; NPCs/timer spawn only after the barrier lifecycle reaches ACTIVE | Challenge lifecycle suite plus full ToA regression | Implemented |
| Current wave count/rosters | 2025 official update removes two waves; current strategy lists 8 | Eight explicit wave definitions | Live-runtime wave tests | Implemented |
| Wave placement | RsProx ticks 2683–3137 | Six packet-observed anchors: `(24,40)`, `(32,40)`, `(40,40)`, `(40,24)`, `(32,24)`, `(24,24)` | Runtime layout tests | Packet-backed |
| First wave delay | RsProx: encounter start to first wave = 15 ticks | `FIRST_WAVE_DELAY = 15` | Live-runtime timing test | Packet-backed |
| Brawler/Thrower/Mage | Current NPC/strategy pages: 5/7 max, 4-tick, opposing-style guaranteed max | Central combat definitions; Magic/Ranged/Melee weaknesses respectively | Combat profile and weakness tests | Implemented |
| Shaman | Current strategy: 40 HP, max 7 magic, 8-tick, Ranged weakness | Scripted 0–7 damage and 8-tick action interval; no generic 15-max fallback | Combat/runtime tests | Implemented; Thrall cap unresolved |
| Cursed baboon | Current update: 20 HP; current strategy: non-aggressive, 20-second pools, Ranged weakness | Scripted deterministic wandering and 33-tick pool lifetime; excluded from generic combat | Combat/runtime tests | Implemented |
| Volatile baboon | Current strategy: chooses target, chases, 3x3 detonation | One-tile-per-tick chase and scripted detonation path; excluded from generic combat | Runtime tests | Implemented; current damage formula unresolved |
| Challenge completion | Current strategy: delete hammers and clear toxins | Party cleanup removes regular hammers and poison/venom | Full ToA regression | Implemented |
| Boss-room completion | Current strategy: restore HP, Prayer, run, special; clear toxins | Shared boss-complete restoration applied to every member | Full ToA regression | Implemented |
| Ba-Ba basics | Current Ba-Ba page: 380 solo HP, 24 max, 6-tick melee, Protect Melee fully blocks | Level-zero max 24 and attack speed 6, through central rules | Ba-Ba rule tests | Implemented |
| Special selection | Existing trace and OSRS visible mechanics | Deterministic seeded scheduler across Rockfall, Shockwave and Baboon Discharge; avoids immediate repeats; Rockfall cooldown does not stall all specials | Deterministic scheduler tests | Implemented; exact modern weighting unresolved |
| Rockfall/rubble | Current Ba-Ba page: one shadow/player, two rubble, rubble HP `10 * party`; breaking cover forces Rockfall | Party-scaled rubble; destroyed Rock Throw cover schedules forced Rockfall after two ticks | Scaling/scheduler tests | Implemented; exact impact damage unresolved |
| Rock Throw cover | Current update: valid cover fully nulls player damage; cover takes type-specific damage | Cover validation/damage preserved; rubble destruction feeds forced Rockfall | Full ToA regression | Implemented; multiplayer cover sharing unresolved |
| Shockwave footprint | Current invocation description proves baseline 13-tile mask | Baseline mask remains 3x3 plus four cardinal tips; enhanced mask belongs only to invocation rules | Ba-Ba footprint tests | Implemented; exact damage/growth ticks unresolved |
| Boss baboons | 2025 update: 1–4, 10 HP, 4-tick, guaranteed player max | 10 HP, 4-tick and guaranteed-max weakness behavior | Ba-Ba/ToA combat tests | Implemented; exact party-count mapping unresolved |
| Rolling thresholds | Current strategy: 2/3 and 1/3 HP | Two threshold state transitions | Ba-Ba phase tests | Implemented |
| Rolling entry/exit | RsProx: boss local `(39,30)`, rolling source x=37; current strategy: melee hit above boulders ends phase | Packet-backed start; only valid melee contact in the exit zone ends rolling | Exit-rule and state tests | Implemented |
| Boulder row visuals | RsProx: projectile 2245 precedes NPC spawn by 2 ticks; sequence 9518; 5 lanes | Cast/projectile, two-tick delayed five-NPC spawn, overlapping rows | Cadence/layout tests | Packet-backed |
| Boulder cadence | RsProx casts at 8-tick intervals; Boulderdash is 4 | Central cadence rule 8/4 | Cadence tests | Implemented |
| Boulder lane RNG | Current strategy: first weak lane among middle 3; later within 2 lanes | Seeded lane selection with adjacency constraint | Lane tests | Implemented |
| Boulder HP | Current Boulder page level-0 path-0 table | Party table: 25, 36, 47, 55, 62, 70, 77, 85 | Party-size scaling tests | Implemented |
| Cracked boulder weakness | 2025 current rule: Ranged receives player maximum hit | Player-max input capped by boulder current HP | Damage-rule tests | Implemented |
| Maximum rows | Current strategy and old capture allow up to 10 | Ten-row safety cap, or early melee exit | Row-cap tests | Implemented; modern level-0 count conflict remains |
| Cleanup/reset | Existing raid lifecycle plus current room restoration rules | Pending projectiles/rows/forced Rockfall, rubble, baboons and timers are reset at phase/room cleanup | Full ToA regression | Implemented |
| Level-zero exclusions | Invocation definitions | No Mind the Gap, Gotta Have Faith, Jungle Japes, Shaking Things Up or Boulderdash effects unless snapshot enables them | Invocation/rules regression | Protected |

## Packet tick comparison

Ticks below are absolute ticks from RsProx capture 14. Server logic stores equivalent relative delays so a seeded encounter is reproducible regardless of world-cycle start.

| Event | Capture tick(s) | OpenRune rule |
|---|---:|---|
| Apmeken active to first wave | `+15` | `FIRST_WAVE_DELAY = 15` |
| Ba-Ba room activation | 3221 | encounter initializes |
| First basic melee | 3228 | six-tick basic cadence after initialization |
| Rockfall cast/resolution window | 3233 â†’ 3239 | special state owns warning/resolution instead of generic combat |
| Rock Throw cast/resolution window | 3250 â†’ 3257 | cover check occurs at scheduled resolution |
| First rolling threshold | 3316 | threshold latch begins transition |
| Jump / scream / first cast / first row | 3318 / 3320 / 3322 / 3324 | rolling transition then projectile-to-spawn delay of two ticks |
| Later row casts | 3330, 3338, … | baseline interval eight ticks |
| Ba-Ba death presentation / cleanup | 3497–3502 | encounter completion is authoritative; exact delayed death presentation remains a gap |

## Deterministic validation

Automated commands:

```text
.\gradlew.bat :content:minigames:tombs-of-amascut:test --tests "org.rsmod.content.minigames.toa.ToaApmekenCombatTest" --tests "org.rsmod.content.minigames.toa.ToaApmekenLiveRuntimeTest" --tests "org.rsmod.content.minigames.toa.ToaApmekenRuntimeLayoutTest" --tests "org.rsmod.content.minigames.toa.ToaBabaEncounterTest" --no-daemon
.\gradlew.bat :content:minigames:tombs-of-amascut:test --no-daemon
```

Result: 30 suites, 343 tests, 0 failures, 0 errors, 0 skipped.

Reproducible in-game solo route:

1. `::toa`
2. Clear the invocation board and create a level-zero party.
3. `::toadev start 1 14`
4. `::toadev room apmeken`
5. Confirm supplies work before the red barrier; cross it and complete all eight waves.
6. `::toadev room baba`
7. `::babatrace on`
8. Exercise Rockfall, Rock Throw cover/destruction, Shockwave, Discharge and both rolling thresholds.
9. `::babatrace dump` and compare relative ticks with the table above.
10. Kill Ba-Ba, confirm restore/cleanup, leave/re-enter/reset, then run the normal lobby-to-reward route.

Group scaling scenario: form the intended party normally, then use `::toadev start <party-size> 14`. The command refuses to start if the actual and requested party sizes differ. Repeat Ba-Ba at sizes 2–8 and verify the HP/rubble/boulder table and party-wide cleanup.

A human live run was not performed by this automated audit. Passing tests establishes deterministic server behavior, not visual 1:1 parity.

## Remaining capture gaps

These are intentionally not guessed:

- exact current Apmeken Sight cadence, warning window, failure tick and party damage scaling;
- exact current Shaman Thrall cap/summon cadence and Thrall Prayer-drain formula;
- Volatile charge duration and remaining-HP player-damage formula;
- current group-size mapping for Ba-Ba’s 1–4 summoned baboons;
- sarcophagus energy cone timing, footprint and damage;
- modern shockwave growth/resolution tick and damage-by-distance;
- multiplayer Rock Throw cover sharing/splitting geometry;
- rolling-boulder collision damage and knockback resolution order;
- whether modern level-zero always ends at seven rows or merely permits up to ten;
- exact delayed Ba-Ba death sequence and five-tick cleanup presentation under OpenRune’s generic NPC-death pipeline.

A modern revision-240 level-zero RsProx capture covering solo and at least one group is required to close these gaps. Until then, “implemented and regression-clean” is accurate; “1:1 complete” is not.

## Live movement and boss-cadence correction (2026-08-29)

Evidence reviewed:

- RsProx capture 14 at `%USERPROFILE%\.rsprox\binary\Old School RuneScape\zebak-audit-rsprox-14.txt`.
- Current OSRS Wiki ToA strategy data: eight Apmeken waves, style weaknesses, Cursed/Volatile behavior, Ba-Ba special rules, rolling thresholds, five-lane rows and current invocation effects.
- Kaoz OSRS, *In-depth Ba-ba Guide For Raids 3 - Puzzle, Fight, Invocations*, especially the puzzle-room and Ba-Ba sections.
- The local legacy Easy ToA Apmeken helper. Its hard-coded ten-wave panel predates the current eight-wave room and is not authoritative for current OSRS.

Live defects found and corrected:

| Area | Defect | Correction | Validation |
|---|---|---|---|
| Apmeken combat baboons | AP combat was assigned only once; stale interactions left actors idle | Set style-correct attack ranges and room chase range, refresh facing, and rearm AP combat when target/mode/range is stale | Focused and full ToA tests |
| Shaman | Scripted attacks existed but the NPC never pursued its target | Added room-safe pursuit until projectile range; out-of-range scripted hits no longer land | Movement-policy regression |
| Volatile | Direct diagonal step failed whenever that one tile was blocked | Added deterministic diagonal/cardinal fallback candidates | Movement-policy regression |
| Cursed | Direction changed every tick in east/north/west/south order, producing four-tile circles | Hold a deterministic eight-direction heading for six ticks with walkable venom-tile fallback | Movement-policy regression |
| RuneLite wave helper | Located legacy helper uses ten hard-coded waves | Server remains on the current OSRS eight-wave roster; the legacy panel must not be used as encounter truth | Source audit |
| Rock Throw | Surviving rubble caused Rock Throw to repeat indefinitely | Resolve one throw per player, then resume combat; destroyed cover alone schedules the separate Rockfall follow-up | Focused and full ToA tests |
| Rolling debris | First threshold did not clear prior rubble | Clear debris on both the 66.6% and 33.3% threshold screams | Full ToA tests |
| Rolling boulders | Server moved boulders as walking actors with default facing | Spawn and hold west facing; advance one tile per tick with the teleport update shown in the packet trace | Rolling path/cadence tests |
| Discharge baboons | Every spawned baboon immediately routed to a sarcophagus | All baboons first acquire players and use ranged combat; only one periodically peels off as the sarcophagus runner | Discharge-policy regression and full ToA tests |

Automated result after the correction: focused Apmeken/Ba-Ba tests passed and the complete Tombs of Amascut module test task passed. The four initially changed Kotlin files and the later Ba-Ba state file were formatted individually. The module-wide format check still reports pre-existing violations in unrelated Akkha/Het/lifecycle files; the files changed by this correction are clean.

A fresh human live pass is still required for visual acceptance of the eight Apmeken waves, one discharge cycle, both rolling thresholds, and the second normal-combat section after each threshold.
