# The Whisperer private-instance evidence worksheet

## Original failure

The private command spawned active form `12204` in `PREPARING`. Revision-240 defines
`12204` with Attack only, while the required Disturb option belongs to Odd Figure
`12203`. Attacking `12204` therefore reached `Dt2AttackHook` and returned the exact
message `Finish the encounter preparation first.`; no valid client action could move
the run out of `PREPARING`.

The repaired flow spawns `12203`, handles its real option 1, plays player sequence
`9799` and NPC sequence `10227`, then transmogs to `12204` four ticks later and starts
combat. The administrator route supplies item `28356` when absent so the capture-backed
Shadow Realm prerequisite cannot deadlock a test instance.

## Evidence crosswalk

| Mechanic | Primary capture evidence | Implemented contract |
| --- | --- | --- |
| Room/start | `rsprox-3299-rev234.bin`, ticks around text ledger 15103-15122 | Region 10595; entry (2656,6382,0); Odd Figure (2655,6368,0); Disturb/transmog flow |
| Standard volley | capture animations 10237/10238 and 10243/10244; projectiles 2444/2445 and impact 2446 | Three-shot ten-tick turns; prayer-correct styles; maximum hits cross-checked with Wiki |
| Corrupted Seeds | capture section around ledger 15230-15235 | 28 exact local tiles, 14 healthy; 47575 regular and 47573/47574 Shadow Realm forms |
| Screech | capture sections around ledger 15266-15293 | Eight exact pillar tiles; 12210/12209 forms; pulses at +16/+22/+28; captured 6/1/1 despawn order |
| Soul Siphon | capture sequence 10256 and NPCs 12211/12212 | Twelve souls grouped 2 Vita, 3 Oratio, 3 Sanitas, 4 Mors; one complete chant resolves |
| Tentacles | capture sequences 10263/10266 and graphics 2452/2454 | Telegraph then tile impact; enabled after the second special and throughout enrage |
| Enrage/death | capture ranged/magic pairs and death sequence 10260 | 140 HP normal final stand; R,R,M,M attacks every two ticks; cleanup before death/drop |
| UI/lifecycle | sanity varbit 15064 and overlay 834 in capture | Sanity overlay, Shadow Realm drain, leave-tab replacement, teleport/logout cleanup |

## Secondary evidence

The OSRS Wiki pages for The Whisperer, its strategy, Sanity, and tentacles establish
phase thresholds, maximum hits, prayer behavior, special outcomes, and enrage rules.
RuneLite Plugin Hub's DT2 Mistake Tracker cross-checks region `10595`, boss forms
`12204..12207`, projectiles `2444/2445`, and client-recognized phase boundaries.

## Explicit limitations

The available captures are revision 234 while this server is revision 240. Every used
NPC, object, animation, graphic, projectile, interface and varbit is validated against
the active revision-240 cache during plugin startup. The captures expose visible packet
ordering but not server accuracy rolls; follow-up volley hits therefore use the existing
server hit pipeline where possible and deterministic bounded rolls for scheduled shots.
The requested ten seconds is not divisible by OpenRune's authoritative 600 ms tick;
17 ticks (10.2 seconds) is the closest representable server-tick interval.
