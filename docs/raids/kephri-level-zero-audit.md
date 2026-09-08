# Kephri level-0 implementation audit

Date: 2026-08-29

Status: implementation and deterministic validation complete; live player smoke test remains required before calling the encounter 1:1.

## Evidence

- Primary packet trace: [RsProx submission 14](https://rsprox.net/download/14), a solo Entry Mode, invocation-off run. The party board, path HUD, 150 shield and 80 final HP identify the level-0/path-0 baseline. The trace is revision 223, so only timing/behavior is reused; every asset below was cross-checked against OpenRune revision 240.
- Current mechanics: [OSRS Wiki Kephri](https://oldschool.runescape.wiki/w/Kephri), [ToA strategies](https://oldschool.runescape.wiki/w/Tombs_of_Amascut/Strategies), [Scarab Swarm](https://oldschool.runescape.wiki/w/Scarab_Swarm_%28Tombs_of_Amascut%29), [Arcane Scarab](https://oldschool.runescape.wiki/w/Arcane_Scarab), [Soldier Scarab](https://oldschool.runescape.wiki/w/Soldier_Scarab), [Spitting Scarab](https://oldschool.runescape.wiki/w/Spitting_Scarab), and [Agile Scarab](https://oldschool.runescape.wiki/w/Agile_Scarab).
- Current change context: [Summer Sweep-Up: Combat & Loot](https://oldschool.runescape.wiki/w/Update%3ASummer_Sweep-Up%3A_Combat_%26_Loot) and [ToA balancing and bugfixes](https://oldschool.runescape.wiki/w/Update%3ATombs_of_Amascut%3A_Balancing_%26_Bugfixes).
- Donor TOA.zip was used only as a secondary state/geometry reference. No donor Java or donor IDs were copied.

## Audit table

| Mechanic | Before this audit | Expected OSRS level-0 behavior | Evidence/confidence | Implementation/files | Verification |
|---|---|---|---|---|---|
| Entry/activation | Boss mechanics started from room scheduling | NPC 11719 stays fixed and idle; first player attack activates combat and music | Packet trace, high | Activation flag, MIDI 730/737 and first-attack delay in ToaDomain.kt / ToaRaidManager.kt | Compile + deterministic state test; live smoke pending |
| Forms/HP | Recovery used dead form 11722; final reused active form | 11719 active, 11720 recovery, 11721 final, 11722 dead; shield 150 and final 80 before scaling | Packet + rev240 gamevals, high | ToaKephriEncounter.kt / ToaConfig.kt / ToaRaidManager.kt | Solo and 1-8 HP tests pass |
| Basic bomb | Projectile launched immediately, landed early, guaranteed flat damage, ignored prayer | Seq9577; upward 1481 at cast, tile locks at +3, downward 2266, fixed-tile impact +4 after lock; speed 6; max 24; prayer reduces about 66% | Packet + Wiki, high | Staged target/bomb runtimes and exact ProjAnim parameters in ToaRaidManager.kt | Timing/constants and movement-at-impact logic tested; live visual pending |
| Attack order | Modulo-4 Dung only with invocation; modulo-5 fake eggs | Baseline Dung is always present. Initial bombs then alternating Dung/Mass slots; five bombs between established specials; six live Agiles replace Mass with Dung | Packet + Wiki, high | KephriAttackCycle in ToaKephriEncounter.kt | Deterministic sequence/substitution tests pass |
| Dung target rotation | Cursor rotated only across currently connected list | Stable original party-orb order; one target baseline, two with Blowing Mud; third break targets all active players | Wiki + packet solo, medium/high | Stable roster scan and UID rebind/retire support in ToaDomain.kt / ToaRaidManager.kt | Solo/group cardinality rules pass; multiplayer live test pending |
| Dung shove/trail | Four-tick warning, wrong center, wrong chat color, immediate full wall | Warning +5, seq9578/player9799/stun245, exact move, correct ff289d message, trail visuals +2 and collision in two batches +4/+5 | Packet, high | KephriDungStrike geometry and staged placements | Collision/path tests pass; live exact-move visual pending |
| Mass Incubation | Repeated generic tile hits; no eggs or hatching | 12 physical eggs in solo: 10 light and 2 dark; spawn +4; resolve 15 visible ticks later; light 3x3 blasts; dark hatches Agile | Packet + Wiki, high for solo | Real NPC11728/11729 actors and batch/egg state in ToaKephriEncounter.kt / ToaRaidManager.kt | Lifecycle constants, layout and reset tests pass; live smoke pending |
| Agile Scarab | Did not exist as a real encounter actor | NPC11727, HP30, seq9594/projectile2152, ranged speed4/max5 and retreats when approached | Packet + Wiki, high | Spawn, combat profile and retreat logic in ToaCombat.kt / ToaRaidManager.kt | Profile tests pass; exact retreat visual live test pending |
| Recovery state | Wrong form/animation and generic short recovery | Two recovery intermissions; third break goes directly to final; recovery unattackable; break/reform effects and delayed final death | Packet, high | Correct forms, attack validation and transition state | Phase constants/reset tests pass; live shield-bar test pending |
| Recovery swarms | Six arbitrary lanes, fixed interval3, movement every2, active-party heal formula | 28 then 24; spawn groups at 4/3/2/1 intervals; +5/+6 first spawn; move one tile/tick after pause; 115% shield cap | Packet + Wiki, high except exact heal roll | Eight edge anchors, accelerated schedule, settle/absorb and locked-party heal cap | Schedule/cap tests pass; exact heal-roll remains uncertain |
| First bodyguard wave | Ranger + mager | Spitting only; More Overlords additionally adds Soldier | Packet + Wiki, high | Correct IDs/offsets and invocation projection | Rule tests pass |
| Spitting Scarab | Generic single-target ranged NPC, no poison | NPC11725 at boss anchor +7,+7; every3 ticks, all players within demonstrated 8 tiles, max10, poison starts6 | Packet + Wiki, high | Server-authoritative party-area attack/poison runtime | Profile tests pass; multiplayer live smoke pending |
| Second bodyguard wave | Melee + mager but wrong offsets/generic behavior | Soldier at +7,+1 and Arcane at +7,-5; More Overlords adds Spitting | Packet + Wiki, high | Correct spawn composition/offsets and persistent actors | Rule tests pass |
| Soldier Scarab | Generic melee only | Max17 melee/speed7 plus six-tick heal projectile2150; heal scales with Soldier health and can persist into final | Packet + Wiki, medium/high | Soldier runtime and heal loop | Pure heal/cadence tests pass; exact heal rounding remains uncertain |
| Arcane Scarab | Generic magic projectile | Eight-step three-tick charge ring, detonation, delayed magic hit and SE/NW/south/NE relocation order | Packet + Wiki, high for observed cycle | Arcane runtime, ring graphics, projectile and relocation | Cadence/asset tests pass; current post-2025 relocation-on-hit threshold remains uncertain |
| Death/completion | Immediate generic NPC removal | Final 11721 reaches zero, seq9582/transmog11722, completion three ticks later, then cleanup | Packet, high | Delayed death state in ToaDomain.kt / ToaRaidManager.kt | Compile/reset tests pass; live completion-spirit smoke pending |
| Wipe/logout/rejoin | Only generic pending-hit cleanup | Remove/rebind bombs, Dung targets, Arcane target, eggs, scarabs, swarms and collision; full reset returns inactive 11719 | Raid lifecycle + inferred cleanup, medium/high | Expanded reset/rebind/retire/cleanup | Deterministic reset test passes; network rejoin live test pending |

## Tick comparison

All offsets are server ticks relative to the mechanic start.

| Sequence | RsProx baseline | OpenRune implementation |
|---|---|---|
| Basic bomb | T seq9577/projectile1481; T+3 tile snapshot, shadow1447 and projectile2266; T+7 gfx2156-2159, sound6475 and damage | Same staged T/T+3/T+7 lifecycle; hit modifier checks the locked tile at impact |
| Dung | T target gfx2146; T+5 boss/player animations, shove and stun; T+7 delayed trail graphics; T+9/+10 collision batches | Same T/T+5/T+7 and two collision batches relative to resolve |
| Mass Incubation | T seq9578/projectiles2164/2165; T+4 NPC eggs; T+19 explosion/hatch | Same T/T+4/T+19 lifecycle |
| Recovery 1 | Break T; first swarm T+5; 28 at 4/3/2/1 cadence; reform one tick after last spawn | Same count, initial delay, cadence and reform rule |
| Recovery 2 | Break T; Soldier T, Arcane T+1; first swarm T+6; 24 at 4/3/2/1 cadence; reform one tick after last spawn | Same bodyguard offsets/count/timing and swarm schedule |
| Third break | T shield break, 11721, final HP80 and immediate all-player Dung; no recovery | Same direct final transition and immediate all-active-player Dung |
| Death | T final HP zero; T+1 seq9582/dead11722; T+4 completion | Engine kill callback starts seq9582/dead form and completes three ticks later |

## Deterministic and reproducible validation

- Focused tests: ToaKephriInvocationRuntimeTest, ToaPathBossInvocationRulesTest and ToaRulesTest.
- Forced clean test build command:
  gradlew.bat :content:minigames:tombs-of-amascut:test --tests org.rsmod.content.minigames.toa.ToaKephriInvocationRuntimeTest --tests org.rsmod.content.minigames.toa.ToaPathBossInvocationRulesTest --tests org.rsmod.content.minigames.toa.ToaRulesTest --no-daemon --rerun-tasks
- Reproducible live scenario:
  1. ::toadev start 1 14
  2. ::toadev room kephri
  3. Attack Kephri once to activate her.
  4. Observe three initial bombs, baseline Dung, Mass Incubation, two reform waves, direct third-break final phase and delayed death.
  5. Repeat with party sizes 2-8 using ::toadev start <party-size> 14.

## Remaining evidence gaps

These are not claimed 1:1:

1. Multiplayer captures are still needed to prove total egg layout/count, one-bomb-per-player behavior, Dung orb rotation visuals, Spitting multi-target projectile fan-out, and exact group swarm heal rounding.
2. The packet trace demonstrates variable 11-15 swarm transfers but does not expose the exact server formula. OpenRune preserves the 115% cap and documented party snapshot, but the individual heal roll is approximate.
3. Dung's no-walkable-tile light damage/rapid lethal sequence and exact server-side stun duration are not numerically established, so no invented damage loop was added.
4. Arcane's current post-2025 heavy-hit/attack-attempt relocation threshold needs a modern packet capture. Charge, detonation and location order are implemented from the observed baseline.
5. A live in-game visual pass is still required for projectile origin heights, map-animation persistence, exact movement facing, poison timing, bodyguard death visuals, full-party wipe and logout/rejoin.
6. Exact sound attenuation/positioning is engine-limited; the verified sound IDs are present, but map-local volume parity is not yet proven.
