# Verzik Vitur normal-mode parity audit

This audit records the sources, verified values, implementation ownership, and remaining visual
checks for OpenRune's Verzik Vitur encounter. Normal mode is the baseline. Entry and Hard mode
forms remain isolated by `TobMode`; Hard-mode-only poison pools and enhanced specials are not
mixed into the normal encounter.

## Sources

- Current OSRS Wiki: `Theatre of Blood/Strategies`, `Verzik Vitur`, `Theatre of Blood/Entry Mode`,
  `Theatre of Blood/Hard Mode`, and `Web (Verzik Vitur)`.
- User-provided full-fight reference: <https://www.youtube.com/watch?v=uw-XQXM7OSw>.
- Revision-240 cache object, NPC, sequence, and spot-animation definitions.
- Donor source under `Desktop/osrs server` was used only to cross-check cache IDs and room tiles.
  Its combat timings were not copied because its lightning and enrage rules are not current OSRS.

## Source audit

| Concern | Owner |
| --- | --- |
| NPC, object, item, sequence, and spot-animation IDs | `TobConfig.kt` |
| Tick constants, scaling, special cycle, lifecycle stages | `TobRules.kt` |
| Room ownership, targets, attacks, adds, transitions, cleanup | `TobRaidManager.kt` |
| Dawnbringer energy, accuracy bypass, projectile, damage | `DawnbringerSpecialAttack.kt` |
| Combat validation and player-hit interception | `TobCombat.kt`, `TobRaidManager.kt` |
| Deterministic rule and regression tests | `TobRulesTest.kt` |

No second Verzik scheduler or coordinate-specific combat handler was found. All encounter-owned NPCs
are spawned with respawning disabled and are removed through the raid instance lifecycle.

## Mechanics matrix

| Mechanic | Normal-mode implementation |
| --- | --- |
| Start | Seated host is stationary; all connected entrants must be in Verzik's room; talk starts P1. |
| Lifecycle | Dormant -> P1 -> P1/P2 transition -> P2 -> P2/P3 transition -> P3 -> enrage -> dying -> complete. |
| P1 volley | Every 14 ticks, 2-tick travel, max 137; Protect from Magic is snapshotted at launch and halves damage. |
| Pillars | Every occupied pillar absorbs one volley per attack; multiple players behind one pillar do not multiply pillar damage. |
| Dawnbringer | 35% energy, 75-150 damage, accuracy bypass, P1-only. |
| P1 caps | Non-Dawnbringer melee caps at 10; ranged/magic cap at 3. |
| P2 urns | Snapshot each player's tile, max 44, half with Protect from Missiles, dodge by moving before impact. |
| P2 blood | Max 45; Protect from Magic blocks player damage; Verzik heals half the pre-prayer roll. |
| P2 melee | Under-foot stomp and adjacent body slam are separate; all players in the selected melee band are scanned on the attack tick, then exact-moved four tiles and stunned for five ticks. |
| P2 lightning | Every fifth attack; up to four player hops; failed discharge max 50 or 25 with insulated boots. |
| P2 Nylocas | Combat Nylocas path to players and detonate; Athanatos paths to Verzik and heals 9-10. |
| Athanatos poison | Poison/venom converts the heal add into delayed 65-75 damage against Verzik. |
| P2.5 | At 35%, two wandering Matomenos spawn; the summon lasts 12 ticks and its first eight ticks absorb player damage; after seven Verzik attacks, remaining HP is absorbed and the cycle repeats after four ticks. |
| P3 targeting | Tank is retained while alive and reachable by encounter membership; Verzik faces and follows that player. |
| P3 autos | Four autos at seven-tick cadence, max 33 ranged/magic (50% prayer reduction), or max 63 adjacent melee. |
| P3 order | Nylocas -> webs -> yellow pools -> green ball, then repeats. |
| Webs | Six timed volleys of three; web NPCs have 10 HP; bind is released on web death, expiry, transition, wipe, or completion. |
| Yellow pools | Spawn per active player; projectile tick 7, occupancy snapshot tick 11, release tick 15; exactly one player per pool. |
| Green ball | Tick-driven; requires three passes to a different adjacent player; failure deals 75% of base Hitpoints. |
| Enrage | At 20%; attack interval becomes five ticks and one owned tornado is spawned per surviving raider. |
| Tornado | Contact removes 50% current HP and heals Verzik for triple damage; orphan tornadoes are removed. |
| Final death | Uses death form/animation for eight ticks, then opens the treasure passage and completes the room. |
| Normal HP scale | 1-3 players 75%, four players 87.5%, five players 100%. |

## Animation correction

The previous constants had phase-one attack/defend/death rotated and used phase-two's melee
sequence as the phase-two spawn sequence. The corrected sequence block is:

- P1 attack `8109`, defend `8110`, death `8111`.
- P2 spawn `8112`, idle `8113`, magic/urn `8114`, lightning `8115`, melee `8116`, heal/summon `8117`, death `8118`.
- P3 spawn `8119`, idle `8120`, walk `8121`, melee `8123`, magic `8124`, ranged `8125`, power blast `8126`, webs `8127`, death `8128`.

## Safety and cleanup

- Pending hits, tile hits, pillar hits, adds, webs, green ball, yellow pools, and tornadoes are
  instance-owned and reset between phases.
- Web binds are explicitly broken during every cleanup path.
- Dead/disconnected tornado targets cause their tornado to despawn.
- A phase-two pathing deadline telejumps only the transition NPC to the center, preventing a stuck
  or invisible transition without moving any player.
- Final completion is delayed until the death sequence finishes; the passage is not exposed while
  the phase-three form is still active.

## Verification

Automated tests cover sequence mapping, P1 prayer snapshot math, lightning cadence, phase-three
cadence, yellow timing, green-ball damage, enrage threshold, Matomenos count, normal HP scaling,
phase-two melee bands and slam probability, phase order, and lifecycle-stage progression. Full in-client verification must still cover exact
projectile heights, sounds, web visual density, pillar safe-tile feel, and multiplayer timing under
latency. The six-volley web duration is calibrated from the supplied full-fight video because the
public reference does not expose a stable numeric volley-count field.
