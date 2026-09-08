# Wilderness boss combat audit

Date: 2026-08-13
Canonical project: `<repo-root>`
Active cache: `.data/cache/SERVER` with `.data/cache/LIVE` as the JS5 source

## Completion statement

This pass replaces generic fallback combat for all eleven requested bosses with one boss-owned scheduler, encounter generation checks, shared impact effects, owned adds, deterministic specification tests, active-cache asset validation, and administrator diagnostics. The automated modules compile and their focused tests pass.

This document intentionally does **not** claim the entire attachment's definition of done. A real logged-in client and a second player were not available to perform the required visual, multiplayer, logout, full-kill, loot, and 20-attack manual traces. The active revision also lacks current production entrances/spawns for several reworked lairs, and several complex ground mechanics remain approximations as listed under **Known limitations**. Those items require an in-game validation pass before production sign-off.

## Root causes and timing

### Chaos Fanatic

There was no tracked Chaos Fanatic combat module before this pass (`git ls-tree -r HEAD` contains only its drop table). The active cache defines NPC 6619 with attack-rate param 2, but the boss was left to generic NPC combat and had no boss-owned cycle, special scheduling, delayed-impact invalidation, or measurable trace. The observed problem was therefore missing authoritative boss scheduling rather than an evidence-backed alternate constant.

- Original configured interval: 2 ticks from active-cache `param_14=2`; no dedicated trace existed.
- Corrected authoritative interval: 2 game ticks from attack commitment.
- Cooldown source: `BossCombat.runCombatTick` only. Projectile and special impact delays do not extend it.
- Regression: `ChaosFanaticSpecTest` locks the 2-tick rate, magic style, max hit, prayer mode, special set, projectile counts, and quotes. `::measurebossattacks` validates real consecutive commit intervals (20 by default).

The final cycle is normal magic (weight 6), disarm (weight 1, eight-tick reuse guard), and six-projectile ground explosion (weight 1, eight-tick reuse guard). Normal magic max is 31 and uses Protect from Magic. Delayed disarm/explosion callbacks validate the original encounter generation, player session, life, plane, and range.

### Scorpia

There was no tracked Scorpia combat module before this pass. NPC 6615 has no explicit attack-rate param, so OpenRune inherited the global generic default of 4 ticks. That numeric default happens to match current evidence, but it did not isolate attack timing from poison and guardian logic and offered no deterministic boss trace. The repair makes 4 ticks explicit and boss-owned; poison cadence and guardian healing never alter `lastAbilityTick`.

- Original configured interval: generic default 4 ticks; no dedicated trace existed.
- Corrected authoritative interval: explicit 4 game ticks from attack commitment.
- Poison root cause: no Scorpia-specific impact rule existed; any prior ad-hoc/raw poison behavior was outside an authoritative encounter and could not distinguish parent and offspring.
- Corrected parent poison: on an accurate melee impact, 1/4 roll, initial poison severity 20, through `CombatEffects.poison` and the shared toxin service.
- Corrected offspring poison: on its always-accurate ranged impact, 1/4 roll, initial poison severity 6, through the same shared service.

The shared poison service owns cadence, one status per player, immunity, reapplication strength, decay, cure, death, and logout cleanup. Scorpia drains two Prayer points when the target uses Protect from Melee. At 99 HP or below, exactly one owned pair of guardians is created. Each living guardian heals only its owner by 1 per tick within three tiles, walks back when separated, despawns after 25 out-of-range ticks, and is deleted on owner death/reset. Healing does not touch the attack scheduler. Offspring use NPC 6616, ranged max 2, six-tick attacks, poison 6 at 1/4; guardians use NPC 6617.

## Active-cache NPC manifest

| Encounter | Cache key | NPC ID | Form / role |
|---|---|---:|---|
| King Black Dragon | `npc.king_dragon` | 239 | boss |
| Chaos Elemental | `npc.chaoselemental` | 2054 | boss |
| Callisto | `npc.callisto` | 6609 | multi boss |
| Venenatis | `npc.venenatis` | 6610 | multi boss |
| Vet'ion | `npc.vetion` | 6611 | first form |
| Vet'ion | `npc.vetion_2` | 6612 | enraged form |
| Vet'ion hound | `npc.vetion_hellhound_jnr` | 6613 | first-form add |
| Vet'ion hound | `npc.vetion_hellhound_snr` | 6614 | enraged add |
| Scorpia | `npc.scorpia` | 6615 | boss |
| Scorpia's offspring | `npc.scorpia_minion` | 6616 | add / ambient child |
| Scorpia's guardian | `npc.scorpia_guardian` | 6617 | healer add |
| Chaos Fanatic | `npc.chaos_fanatic` | 6619 | boss |
| Revenant Maledictus | `npc.wild_cave_superior` | 11246 | event boss |
| Artio | `npc.callisto_singles` | 11992 | singles-plus boss |
| Calvar'ion | `npc.vetion_single` | 11993 | first form |
| Calvar'ion | `npc.vetion_2_single` | 11994 | enraged form |
| Spindel | `npc.venenatis_singles` | 11998 | singles-plus boss |
| Venenatis spiderling | `npc.venenatis_spiderling` | 12000 | owned add |
| Spindel spiderling | `npc.spindel_spiderling` | 12001 | owned add |
| Calvar'ion hound | `npc.vetion_hellhound_jnr_singles` | 12107 | first-form add |
| Calvar'ion hound | `npc.vetion_hellhound_snr_singles` | 12108 | enraged add |

These IDs were resolved from this project's active revision dump, not pasted from current RuneLite IDs.

## Implemented combat profiles

| Boss | Rate | Implemented mechanics | Automated proof |
|---|---:|---|---|
| Chaos Fanatic | 2 | magic max 31, prayer interaction, disarm, six-projectile dodgeable explosion, delayed cleanup | `ChaosFanaticSpecTest` |
| Scorpia | 4 | melee max 16, poison 20 at 1/4 on impact, prayer drain 2, single guardian pair | `ScorpiaSpecTest` |
| Scorpia offspring | 6 | accurate ranged max 2, poison 6 at 1/4 | `ScorpiaSpecTest` |
| King Black Dragon | 4 | melee, ordinary fire, poison/freeze/shock breaths, distance-aware melee selection | `KingBlackDragonSpecTest` |
| Chaos Elemental | 4 | melee/ranged/magic max 28, safe nearby teleport, up to four safe unequips | `WildernessBossSpecTest` |
| Revenant Maledictus | 5 | ranged/magic standard cycle, ice/blood/air specials, multi-player tile snapshots, 50% prayer reduction | `WildernessBossSpecTest`, `MaledictusEventTest` |
| Callisto | 4 | melee 35..55 with 50% prayer reduction, ranged max 31 fully protected, knockback, trap thresholds | `WildernessBossSpecTest` |
| Artio | 4 | melee 15..35, ranged max 17, single-target knockback, independent encounter | `WildernessBossSpecTest` |
| Venenatis | 4 | adjacency melee override; exact eight ranged/eight magic rotation; first ranged summons two; third magic webs | `WildernessBossSpecTest` |
| Spindel | 4 | independent singles variant, same rotation, separate spiderling type and encounter | `WildernessBossSpecTest` |
| Vet'ion | 6 / 5 | normal/enraged rates, one preserved encounter through transform, two hounds in each form, lightning and bash | `WildernessBossSpecTest` |
| Calvar'ion | 6 / 5 | separate forms/adds/encounter, fixed two-hound count, max-seven lightning specification | `WildernessBossSpecTest` |

### King Black Dragon protection matrix

All four breaths use dragonfire-specific hit types. Ordinary fire rolls against a base cap of 65; each special breath rolls against 50. The current matrix encoded by `DragonfireProtection.resolveMaxHit` is:

| Protection | Ordinary / special max |
|---|---:|
| none | 65 / 50 |
| shield | 15 / 10 |
| Protect from Magic | 20 / 15 |
| shield + prayer | 15 / 10 |
| antifire potion | 50 / 50 |
| antifire + shield | 0 / 50 |
| antifire + prayer | 0 / 10 |
| antifire + shield + prayer | 0 / 10 |
| super antifire | 5 / 15 |
| super antifire + shield | 0 / 15 |
| super antifire + prayer | 0 / 10 |
| super antifire + shield + prayer | 0 / 10 |

Poison breath applies poison 8, freezing breath binds for 10 ticks, and shocking breath drains Attack, Strength, Defence, Ranged, Magic, and Prayer by 2. Each special secondary effect is normally 1/2; shield or Protect from Magic changes that roll to 1/8. Poison, freeze, and drains execute only at a valid projectile impact through shared services.

### Maledictus lifecycle

Eligible Revenant Cave revenant deaths feed one world accumulator by the slain revenant's combat level. Invalid kills, Maledictus itself, and NPCs outside `area.revenant_caves` do not count. State starts at a randomized progress after restart, permits only one active event, has a 45-minute recent-spawn lockout, a custom 60-minute failsafe, and a 10-minute idle event lifetime. An engaged boss is not removed mid-fight.

The boss selects one of three revision-valid cave spawn tiles and announces to cave players. Normal ranged/magic damage is reduced 50% by the matching prayer. Ice targets snapshotted tiles and freezes; blood heals half of damage dealt; air is a 5x5 typeless burst with edge falloff. Top damage receives a guaranteed owned ancient emblem or ancient totem. Other resolved contributors receive one blighted super restore and two blighted foods beneath them. The existing `ForinthrySurgeKillHook` remains the authoritative Avarice/Surge implementation.

Accumulator tests cover cumulative progress, invalid kills, overflow, a single active event, lockout, inactive transition, and randomized restart seeding.

## Shared runtime changes

- `BossCombat` is the single attack scheduler. A phase can override the base rate; cooldown begins on commit.
- `BossEncounter` owns phase, forced ability, selectors, commit/impact traces, generation, fired-once markers, and child NPCs.
- `MeleeOverrideRotation` permits spider melee without consuming the ranged/magic rotation.
- Delayed hit, projectile, debris, teleport, unequip, guardian, hound, and special callbacks validate the current encounter generation.
- Impact-bound poison/freeze/stat drain run after accuracy resolution and use existing shared services.
- Test bosses are player-owned and `suppressRewards`; `NpcDeath` exits before drops, kill count, pet, Slayer, or other economic hooks.
- Cache assets are validated once during diagnostics, not decoded on every attack.

## Animations and visual assets

No block, death, idle, walk, rotation, size, model, recolour, or skeleton override was introduced. Those remain sourced from each NPC's active cache definition to avoid cross-revision deformation.

Explicit attack sequences used by the new specs are `seq.human_casting`, `seq.dragon_attack`, `seq.dragon_firebreath_all_attack`, `seq.chaoselemental_attack`, `seq.npc_revenant_superior_attack_melee`, `seq.npc_revenant_superior_attack_stun`, `seq.npc_revenant_superior_attack_heal`, `seq.npc_revenant_superior_attack_dot`, `seq.npc_callisto_attack_melee01`, `seq.npc_callisto_attack_ranged01`, `seq.npc_callisto_attack_magic01`, `seq.npc_callisto_enraged`, `seq.venenatis_attack_melee`, `seq.venenatis_attack_ranged`, `seq.venenatis_attack_magic`, `seq.vetion_attack_magic`, `seq.vetion_attack_melee`, and `seq.scorpia_talk`. Parent/offspring normal attacks may use the active cache's `param.attack_anim` via `npcAttackAnim()`.

Explicit projectile/graphic families validated by key include dragon fire/toxic/ice/lightning, Chaos Elemental confusion/madness/discord, Revenant superior standard and special impacts, Callisto ranged/trap, Venenatis ranged/magic/web, Vet'ion lightning, and Scorpia guardian telegrab visuals. Diagnostics print the exact resolved asset list and missing keys for the live cache.

## Administrator diagnostics

All commands require administrator rights:

- `::wildernessbossaudit` — resolves live NPC IDs, owner registration, phase rates, abilities, cache assets, validation errors, and arena classification.
- `::testwildernessboss <name-or-id>` — creates a nearby owner-only, reward-free encounter; never changes the permanent spawn.
- `::measurebossattacks <name-or-id> [20-50]` — prints attack key/style, commit, expected and actual impact, next commit, interval, expected interval, and PASS/FAIL.
- `::forcebossattack <name-or-id> <attack-key>` — safely rejects unknown keys and forces the next real attack.
- `::resetwildernessboss <name-or-id>` — invalidates pending work, deletes owned adds, resets form/phase/health/control state.

## Files added for this pass

- `content/bosses/chaos-fanatic/` — production script and tests
- `content/bosses/scorpia/` — combat, access links, and tests
- `content/bosses/wilderness/` — grouped Wilderness combat, Maledictus event, and tests
- `content/bosses/kbd/src/test/.../KingBlackDragonSpecTest.kt`
- `content/bosses/diagnostics/.../WildernessBossAuditCommands.kt`
- `api/bosses/.../validation/BossAssetValidator.kt`
- this report

Key modified files are the boss DSL/runtime (`BossDsl.kt`, `EffectBuilders.kt`, `BossCombat.kt`, `BossEncounter.kt`, `EffectInterpreter.kt`, `EncounterRegistry.kt`, `BossSpec.kt`, `Effect.kt`, `Selector.kt`, `SpecValidator.kt`), `DragonfireProtection.kt`, KBD combat, `Npc.kt`, and `NpcDeath.kt`. The repository already contained many unrelated uncommitted changes; they were preserved.

## Research evidence

- OSRS Wiki, *King Black Dragon* and *King Black Dragon/Strategies*: four-tick rate, melee proximity, four breaths, max hits and secondary statuses.
- OSRS Wiki, *Dragonfire*: current protection stacking matrix.
- OSRS Wiki, *Chaos Fanatic*: attack profile, two-tick rate, disarm and green explosion behavior.
- OSRS Wiki, *Scorpia/Strategies*: four-tick parent rate, poison/guardian/offspring behavior.
- OSRS Wiki, *Chaos Elemental*: styles, teleport and equipment-removal specials.
- OSRS Wiki, *Revenant maledictus* plus Jagex's *Revenant Maledictus & Wilderness Changes*: accumulator, lockout, three specials, contribution and rewards.
- OSRS Wiki, *Callisto/Strategies* and *Artio/Strategies*: minimum melee damage, prayer behavior, knockback and trap thresholds.
- OSRS Wiki, *Venenatis* and *Venenatis/Strategies*: four-tick rate, 8/8 rotation, movement cadence, spiderling/web positions and drain values.
- OSRS Wiki, *Vet'ion/Strategies*: 6/5-tick forms, lightning, bash, two hounds per form and transformation.
- OpenRune active cache dump and raw server overrides: IDs, forms, models, size, animations, params and available assets.

## Verification record

Focused verification command:

```text
.\gradlew.bat :api:bosses:compileKotlin :content:bosses:kbd:test :content:bosses:chaos-fanatic:test :content:bosses:scorpia:test :content:bosses:wilderness:test :content:bosses:diagnostics:compileKotlin --rerun-tasks
```

- Focused boss regression result: **BUILD SUCCESSFUL**, 160 tasks executed, including all four boss test modules and diagnostics compilation.
- Standard `:server:app:build`: application compilation reached the final check, then failed only at `:server:app:spotlessKotlinCheck` because of pre-existing formatting violations in `GameBootstrap.kt`, `GameServer.kt`, and `GameService.kt`. Those unrelated user changes were preserved.
- Full `:server:app:build -x :server:app:spotlessKotlinCheck`: **BUILD SUCCESSFUL**, 364 tasks, including server compilation, application packaging, shadow JAR/distributions, and available server tests.
- Controlled start 1: **PASS**. Revision 240; game port 43594; OpenRune Central 8080/9091; active `.data/cache/SERVER`; `OPENRUNE CUSTOM BUILD ACTIVE`; 288 scripts loaded; `Server ready` in 13.456 seconds.
- Controlled start 2: **PASS**. Same canonical working directory/cache/revision/ports; custom-build marker; 288 scripts; `Server ready` in 13.545 seconds. The second verified server was left running.
- Second-start JVM working directory: `<repo-root>`.
- Startup logs: `C:\tmp\openrune-wilderness-start1.out.log` and `C:\tmp\openrune-wilderness-start2.out.log`.

Manual status:

- No player client was controlled by this task, so rendered animation checks, actual 20-attack traces, multi-player behavior, prayer/status timelines, complete kills, loot, respawn, death, and logout scenarios are **NOT RUN**.
- Use the administrator diagnostics above for the next in-game validation session. `::testwildernessboss` is reward-free and owner-only; `::measurebossattacks` defaults to the required 20 intervals.

## Known limitations requiring follow-up

1. No logged-in player/two-player harness was available. Visual deformation, 20 live intervals per phase, real prayer switching, movement, collision, death/logout, complete kill, loot, respawn, and simultaneous pair isolation remain **not manually tested**.
2. The active map is from an older Wilderness layout: it has surface spawns for Callisto (3291,3849), Artio (3116,3677), and Venenatis (3319,3798), but no permanent Spindel, Vet'ion, or Calvar'ion spawn and no verified current lair entrance set. Accurate production access cannot be invented safely; administrator test encounters are available.
3. Callisto/Artio traps are delayed dodgeable tile hazards, but are not persistent bind-producing encounter ground objects for their full OSRS lifetime.
4. Venenatis/Spindel encode the exact 8-ranged/8-magic order and correct special positions, but scripted arena repositioning every fourth attack is not yet implemented.
5. The web uses a 7x5 activation check and one application of damage/prayer/run drain. It does not yet persist for repeated ticks, restrict movement, overlap as owned ground state, or implement Aranea-boots behavior.
6. Spiderlings are capped at ten and drain Prayer, but their live count does not yet alter boss max hit/prayer effectiveness. The current through-prayer scaling formula is therefore not complete.
7. Vet'ion/Calvar'ion lightning now creates four random strikes plus one player-targeted strike per participant (up to three), caps the combined set at seven, warns first, and checks each strike as a 3x3 area at activation. Exact arena-edge random-tile filtering still needs live-map validation.
8. Shield bash now warns a boss-centred area, resolves after two ticks, and applies an eight-tick attack-only lock without freezing movement. Its exact directional safe-tile shape and the enraged-form transformation lightning ring remain to be implemented and visually validated.
9. The repository contains no Revenant Dragon drop table registered for runtime reuse. Maledictus currently gives the guaranteed artefact and contributor supplies but not the two official ordinary Revenant Dragon table rolls. The official accumulator denominator and failsafe are intentionally undisclosed by Jagex; 10,000 and 60 minutes are documented custom values.
10. Global singles-plus/PJ/fee/death-item behavior and recoil/vengeance capability flags were inspected but not comprehensively changed in this pass; changing global PvP rules without an arena-valid integration test would be unsafe.
11. Automated tests prove deterministic spec/state invariants, not rendered animation compatibility or every runtime death/drop hook. The admin diagnostics and controlled test encounter are the required next validation layer.

Because of these limitations, this pass is a substantial combat-system repair and testable implementation baseline, not a truthful claim of complete current-OSRS parity.
