# OSRS Nex implementation audit (revision 240)

This audit records the production values used by `content/bosses/nex`. Cache-backed values were
resolved from the active revision-240 LIVE cache and merged gamevals on 2026-08-14. Combat and
reward rules were checked against the current OSRS Wiki Nex and strategy pages and Jagex's
25 June 2025 *Summer Sweep Up: Combat* update. Where the live Wiki and that official update
disagree, the task's explicit requirement is recorded below.

## Existing project state

- No Nex encounter, access script, shared encounter state, combat script, or reward service existed.
- Generated drop files existed for Nex, the four bodyguards, and Blood Reavers. The Nex file was a
  partial placeholder and did not implement contribution-weighted personal loot.
- Reusable systems found: normal NPC combat/accuracy, delayed hits, world queues, NPC transforms,
  public damage contributions, owned ground objects, player kill-count varps, death hooks, collision,
  exact-coordinate object handlers, and the generic banker content group.
- The active map already contains the real Ancient Prison bank room, arena, barrier, Zaros altar,
  reclaim chest, Ancient Forge, and bank NPC. The map is reused unchanged.
- OpenRune has no complete follower/pet framework or collection-log award service. Nex records the
  independent Nexling roll and persistent unlock, but follower recall and collection-log display
  remain an engine/content-system limitation.

## Map and access

| Purpose | Revision-240 value | Evidence | Implementation | Test |
|---|---:|---|---|---|
| Safe bank-room teleport | `2900,5203,0` | decoded collision: walkable; inside walls; beside bank NPC/chest area | `NexConfig.kt` / `NexWorld.kt` | startup collision validation |
| Barrier controller | loc `42967` at `2909,5202,0` | `loc.nex_fight_barrier`, decoded map | `NexWorld.kt` | startup cache/map validation |
| Busy barrier controller | loc `42968` | `loc.nex_fight_barrier_busy` | `NexWorld.kt` | cache validation |
| Public barrier transform | loc `42937`, option 1 `Pass`, option 3 `Peek` | LIVE object definition | `NexWorld.kt` | handler-registration assertion at startup |
| Public/private transform | loc `42938`, options 1/2/3 | LIVE object definition; private rooms intentionally unsupported | only option 1 and 3 registered | cache validation |
| Busy transforms | locs `42939`, `42940`, `42941` | LIVE object definitions | state varbit blocks entry while active | startup cache validation |
| Barrier state | varbit `13184` / `varbit.nex_barrier` | merged gamevals | `NexEncounterManager.kt` | runtime state handling |
| Bank-side return | `2908,5203,0` | decoded collision | `NexWorld.kt` | startup collision validation |
| Arena entry | `2910,5203,0` | decoded collision; east side of barrier | `NexWorld.kt` | startup collision validation |
| Arena center/Nex spawn | `2924,5202,0` | decoded map and OSRS arena cross | `NexConfig.kt` | coordinate assertion |
| Zaros altar/exit | loc `42965` at `2939,5202,0`; option 2 `Teleport` | `loc.nex_zaros_altar`, decoded map/LIVE definition | `NexWorld.kt` | startup validation |
| Reclaim chest | loc `42854` at `2901,5200,0`; option 1 `Claim` | decoded map | map asset audited; Nex currently uses standard PvM death drops | startup audit |
| Bank | NPC `npc.nex_story_npc_3op`, option 3 through `content.banker` | `.data/raw-cache/server/banks.toml` | existing generic banker module | startup validation/manual client check |

The bank room is treated as safe. Encounter targeting is bounded to the arena and never searches
through the barrier. Public entry is allowed while the chamber is idle/spawning and denied after
combat begins. Leaving never resets a fight that still has living participants.

## NPCs and cache forms

| NPC | ID | Level | HP | Attack/Strength/Defence/Magic/Ranged | Size | Spawn |
|---|---:|---:|---:|---|---:|---|
| Nex (normal) | 11278 | 1001 | 3400 | 315/200/260/230/350 | 3 | 2924,5202 |
| Nex (spawning) | 11279 | 1001 | 3400 | same | 3 | transform |
| Nex (Soul Split) | 11280 | 1001 | 3400 | same | 3 | transform |
| Nex (Deflect Melee) | 11281 | 1001 | 3400 | same | 3 | transform |
| Nex (dying/Wrath) | 11282 | 1001 | 3400 | same | 3 | transform |
| Fumus | 11283 | 285 | 500 | 1/1/200/200/1 | 1 | 2912,5216 |
| Umbra | 11284 | 285 | 500 | 1/1/200/200/1 | 1 | 2937,5216 |
| Cruor | 11285 | 285 | 500 | 1/1/200/200/1 | 1 | 2937,5190 |
| Glacies | 11286 | 285 | 500 | 1/1/200/200/1 | 1 | 2912,5190 |
| Prison Blood Reaver | 11293 | 174 | 125 | not used for boss summons | 2 | outside prison |
| Boss Blood Reaver | 11294 | 161 | 150 | defence 100, magic 190 | 2 | safe arena tiles around Nex |
| Nexling | 11277 | pet | cache-defined | non-combat | 1 | persistent roll/unlock |

Nex aggressive bonuses are melee `+200`, ranged `+100`, magic `+150`, melee strength `+20`,
ranged strength `+22`, magic damage `+5`. Defences are stab `+40`, slash `+140`, crush `+60`,
magic `+300`, light ranged `+190`, standard ranged `+190`, heavy ranged `+150`. Fumus defence is
25/100/100/150; Umbra 100/100/100/150; Cruor 100/25/100/150; Glacies 100/100/25/150.

All five Nex forms use stand `9177`, walk/run `9175`. Bodyguards use stand `808`, walk `819`,
turn-back `820`, turn-left `821`, turn-right `822`. Nex/bodyguards/Reavers are poison and venom
immune for this encounter; Nex Defence may be reduced by at most 10 levels. Encounter-owned
bodyguards and Reavers suppress independent normal loot.

## Animations and graphics

| Purpose | ID/key |
|---|---|
| Nex dash | seq 9178 / `seq.nex_dash_attack` |
| Nex Turmoil | seq 9179 / `seq.nex_turmoil` |
| Nex basic/claw attack | seq 9180 / `seq.nex_attack` |
| Nex blast/drag | seq 9182 / `seq.nex_blast_away` |
| Blood Siphon | seq 9183 / `seq.nex_blood_siphon` |
| Death | seq 9184 / `seq.nex_death` |
| Defence | seq 9185 / `seq.nex_defend` |
| Smash | seq 9186 / `seq.nex_smash_attack` |
| Spin/darkness | seq 9187 / `seq.nex_spin_out` |
| Alternate cast | seq 9188 / `seq.nex_alternate_cast_attack` |
| Cast | seq 9189 / `seq.nex_cast_attack` |
| Summon | seq 9202 / `seq.nex_summon` |
| Barrier | seq 9203 / `seq.nex_prison_barrier` |
| Smoke projectile/impact | spotanim 1997 / 1998 |
| Shadow projectile | spotanim 1999 |
| Blood projectile/impact | spotanim 2000 / 2001 |
| Blood Siphon projectile/body | spotanim 2002 / 2015 |
| Ice projectile/impact | spotanim 2004 / 2005 |
| Ice Prison projectile | spotanim 2006 |
| Zaros projectile/impact | spotanim 2007 / 2008 |
| Soul Split projectile | spotanim 2009 |
| Defence leech projectile/impact | spotanim 2010 / 2011 |
| Mushroom/smoke cloud projectile/map/small | spotanim 2012 / 2013 / 2014 |
| Turmoil | spotanim 2016 |
| Summon | spotanim 2017 |
| Shadow Smash map loc | loc 42942 / `loc.nex_shadow_smash` |
| Ice prison icicles | locs 42943, 42944 |

The cache has no named Nex synth/gameval entries and the active project has no dialogue-to-sound
mapping. No unrelated or guessed sound is substituted. Dialogue, animation, projectile, impact and
map graphics are implemented; verified numeric Nex sound IDs remain a manual cache-audio blocker.

## State machine and combat values

States are `IDLE`, `SPAWNING`, `SMOKE`, `FUMUS`, `SHADOW`, `UMBRA`, `BLOOD`, `CRUOR`, `ICE`,
`GLACIES`, `ZAROS`, `DYING`, and `RESETTING`. Each transition is generation-guarded and one-shot.

| Call | Threshold | post-2025 lower floor | Called bodyguard |
|---|---:|---:|---|
| Smoke end | 2720 | 2550 | Fumus |
| Shadow end | 2040 | 1870 | Umbra |
| Blood end | 1360 | 1190 | Cruor |
| Ice end | 680 | 510 | Glacies |

Damage crossing a call is reduced to 10% beyond the threshold and accepted only to the 170-HP
lower floor. Further delayed/simultaneous damage is rejected until the bodyguard dies. The official
2720-to-2550 example is a regression test. Nex uses a 50 soft cap: the part above 50 is reduced to
one third; bodyguards use the same cap; Reavers have no soft cap. Nex cannot die outside Zaros.

Normal attack cadence is 4 ticks. Phase special cycles are deterministic:

- Smoke: five normal attacks, then Choke, five normal attacks, then Smoke Dash with its pull; repeat.
- Shadow: five normal attacks, Shadow Smash, five normal attacks, Embrace Darkness; repeat.
- Blood: five normal attacks, Blood Siphon, five normal attacks, Blood Sacrifice; repeat.
- Ice: five normal attacks, Containment, five normal attacks, Ice Prison; repeat.
- Zaros: four attacks under Soul Split, four attacks under Deflect Melee, four with no overhead;
  repeat. Selecting melee forces that attack plus two more melee attempts while the target remains
  in melee range.

Normal prayer reductions are phase-specific: magic 50%, shadow ranged 50%, blood magic 66%, and
melee 80%. Final-phase melee protection is 66%. Final magic uses 50% when protected at launch and
25% when Protect from Magic is activated only during projectile travel (the June 2025 late-prayer
change). Smoke Dash maxes 50; Shadow Smash 50; Blood Sacrifice 80; Containment 60; Ice Prison 75.
Wrath resolves once after the death animation, within five tiles, and never reaches the bank room.

The task explicitly requires Blood Reaver damage to heal its attacker by 75%, matching Jagex's
25 June 2025 update text. The current Wiki strategy prose now describes 50%; this implementation
follows the user's explicit 75% requirement and records the source conflict rather than hiding it.

## Contribution and rewards

Accepted damage to Nex, each called bodyguard, and encounter-owned Blood Reavers is accumulated per
player and per encounter generation. Cancelled immune damage, rejected floor damage, outsiders,
spectators, prior-generation damage, and post-exit damage do not count. Eligibility requires at
least 5% of the encounter's accepted player damage or 100 accepted damage, whichever is lower.
Ties for MVP are deterministic: highest contribution, then earliest first contribution, then UID.

Every eligible player receives two personal common rolls whose quantities are scaled by their
contribution share of the encounter's generated loot lump. The MVP gets 10% more common quantity
and guaranteed Big bones. Each eligible player's unique chance is their contribution share divided
by 43; the MVP's chance is multiplied by 1.10, allowing multiple uniques in one kill. Unique weights
are Ancient hilt 1/12, Nihil horn 2/12, each damaged Torva piece 2/12, and Zaryte vambraces 3/12.
Nexling is an independent 1/500 roll. Rewarded player UIDs are retained in the encounter until reset,
making kill count, drops, unique broadcasts, and pet rolls idempotent.

The two contribution-scaled common rolls draw from blood/death/soul/air/fire/water runes,
unfinished dragon bolts, enchanted onyx bolts, steel cannonballs, air orbs, coal, runite ore,
uncut rubies, uncut diamonds, wine of Zamorak, sharks, Saradomin brews, coins, and ecumenical key
shards. Shark and brew rolls add their paired prayer-potion or super-restore supply. Blood essence,
the two Nihil-shard rolls, rune sword, and elite clue scroll are separate independent tertiary
rolls. The generated placeholder TOML is not used for personal Nex loot. The main-category
selection is uniform in the current implementation and is listed as a fidelity blocker below.

Kill count uses `varp.total_nex_kills` (3269) and increments once for each eligible rewarded player.
Combat-achievement-ready flags record Smoke Dash, Shadow Smash, Blood Siphon healing, Ice-special
damage, player death, starting team size, and cough-at-kill.

## Reset and validation

Every delayed effect captures the encounter generation and revalidates NPC identity, participant
membership, life state, arena location, and state before resolving. Death/logout/teleport/exit clear
only that player's temporary state. Empty active rooms enter reset without affecting the bank room.
Reset removes Reavers and temporary locs, deletes the old boss/bodyguards, clears rewards and
contribution, increments generation, then spawns exactly one fresh set after the respawn delay.

Automated pure-rule coverage is in `NexRulesTest.kt`, `NexRewardRulesTest.kt`, and
`NexPhaseRulesMatrixTest.kt`; runtime startup validates cache assets and critical walkable tiles.

## Verification boundary and remaining blockers

The module is a playable full-phase foundation, but it must not yet be represented as fully verified
1:1 Nex. The remaining gaps are explicit:

- Three-client, two-consecutive-kill timing and disconnect validation has not been performed.
- Sound IDs were not verifiable in the revision-240 RSCM data, so no guessed sounds were added.
- OpenRune has no complete follower or collection-log award framework; Nexling is an idempotent
  persistent unlock only.
- The project has no complete Combat Achievement award framework; encounter flags are recorded only.
- Nex deaths use the established standard PvM death-drop handling, not the OSRS 100,000-coin reclaim chest.
- The altar lacks the ten-minute cooldown and Zaros-item Hitpoints/run/special restoration branch.
- The exact OSRS team-lump/common-category weighting is not reproduced; category selection is uniform.
- Full integration tests for commands, live combat timing, prayer timing, cleanup, rewards, and reset
  remain absent; current tests cover deterministic formulas and phase matrices only.
