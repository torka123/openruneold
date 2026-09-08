# Vorkath implementation and donor audit

Date: 2026-08-16
Target: OpenRune revision 240
Supplied donor label: Reason 231 (`osrs server.rar`)
Decoded donor build: 229.1

## Outcome

The post-quest Vorkath encounter is implemented as a private, single-player state machine on a copy of native Ungael region 9023. The encounter uses only native revision-240 NPCs, objects, animations, graphics, models and map data. No Reason cache file and no OpenRune cache archive was changed.

The donor implementation was used only as secondary evidence for legacy IDs and broad flow. Current OSRS behavior/rates and the target engine's revision-240 definitions take priority. The donor has several known inaccuracies (old damage/protection behavior, only 39 acid pools, incomplete lifecycle and no explicit sounds), so its combat code was not copied wholesale.

## Implemented flow

1. `::vorkath` takes an administrator to the public Ungael ice-wall approach.
2. Native Climb-over objects 31990, 31992 and 31994 create a private region-9023 instance and preserve the selected wall crossing X coordinate.
3. Vorkath starts as native sleeping/Poke NPC 8059, stationary on the ice bed.
4. Poke runs the native wake sequence and replaces the sleeping form with post-quest NPC 8061 at 750 HP.
5. Six standard attacks are followed by a randomly selected first special; acid and zombified-spawn specials then alternate.
6. Death clears all pending projectiles, acid objects, binds and spawns, awards two main-table rolls plus guaranteed/tertiary rolls, records killcount/PB, and returns to a sleeping form in the same instance.
7. Crossing back over the ice chunks, logout, disconnect and player death tear down the instance-owned objects and NPCs.
8. Torfinn's native travel and collect options work at both Ungael and Rellekka. Unsafe-death items held from Vorkath cost 100,000 coins to reclaim.

Required lifecycle states are explicit: `ENTERING`, `SLEEPING`, `AWAKENING`, `ACTIVE`, `ZOMBIFIED_SPAWN_SPECIAL`, `ACID_SPECIAL`, `DYING`, `LOOTABLE`, `RESETTING`, and `ENDED`.

## Combat behavior

- Post-quest Vorkath: 750 HP, 5-tick attack rate, melee/ranged/magic/dragonfire pools and 6-standard-attack cadence.
- Dragonfire: uses the shared Vorkath-specific antifire/shield/Protect from Magic matrix. The high fireball is tile-targeted, deals up to 121 on its exact tile, up to 60 one tile away, and zero two or more tiles away.
- Acid phase: native acid loc 32000 is spawned into the instance only after projectile impact, synchronized through `LocRepository`, and removed at special/death/reset/exit. Selection reserves a collision-valid, contiguous five-tile lane before filling up to 64 pools. Standing on acid deals 1–10 typeless damage and heals Vorkath. Rapid fire targets the sampled tile every game tick for 25 shots and deals 25–41 only if the player remains there. Player damage to Vorkath is reduced by 50 percent during the phase.
- Zombified spawn: native post-quest NPC 8063 appears when its projectile lands, has 38 HP, follows collision-aware NPC movement, freezes the player during the special, and explodes for a maximum scaled from its remaining HP up to 60. The shared Crumble Undead integration guarantees a hit when magic attack bonus is -63 or better.
- Ownership: instance-spawn ownership and the global Vorkath attack validator prevent cross-instance and non-owner attacks.
- Debug-forced mechanics permanently disable loot, killcount and PB credit for that encounter.

## Drops and progression

The post-quest table is registered only to `npc.vorkath` and uses two main rolls. It guarantees two superior dragon bones and two blue dragonhides, implements the exact kill-50 head guarantee without an additional random head on that kill, preserves tertiary head/pet/visage/necklace/jar/clue rolls, and constrains shark-lure quantities to even values from 70 through 110. `VORKATH_DROP_RATE_MULTIPLIER` is intentionally `1.0`.

Slayer, Salve/undead and elemental fire-weakness bonuses are consumed from the target revision's NPC params by the shared combat formulas. NPC poison/venom uses the same native immunity params. The engine does not currently process player recoil against NPCs at all, so no Vorkath-specific recoil exception was added.

`VorkathAccessPolicy` is a deliberately narrow replacement point for Dragon Slayer II progression. The current quest framework does not provide complete Dragon Slayer II state, so the default policy permits access instead of inventing a false quest variable.

## Native definition evidence

A read-only decode of the active revision-240 cache confirmed:

- NPC 8059: size 7, Poke option, sleeping animation 7946.
- NPC 8061: size 7, Attack option, idle 7948 and walk 7947.
- Torfinn 10403/10404: Ungael/Rellekka travel options.
- Torfinn 10405/10406: travel plus Collect options.
- Loc 32000: native Acid pool.
- Locs 31990/31992/31994: native Climb-over ice chunks.

The complete mapping is in `reason231-to-openrune240-vorkath-id-map.json`.

## Cache safety

- Donor archive SHA-256: `823B1C8C37D08D2395850D9312A8FF4D865BDF16800F9CF240519088EDCC28CE`.
- Donor Vorkath source SHA-256: `472D573CDA89C0D8CBD82DB82F5D57F443D0FA5A8D911D443D01C2C665B13F12`.
- Donor cache manifest: 100 before / 100 after, 0 changed files, aggregate SHA-256 `39EBBFA89D365C07BA1EA3E122340440010DB9BB1C851A12C75E2ADB0E837516`.
- Active LIVE dat2 SHA-256: `7E1911549A9D4B8127018AE42D7B67102579F0441B4EC29F4D2262E557D8F9B3`.
- Active SERVER dat2 SHA-256: `D3C753BCD77487B175AF59F11ED2C19C2015CC3C35DFFA8996C36A3078282513`.
- Cache allowlist: empty; actual cache changes: empty.
- Active cache comparison across both restarts: 50 files before / 50 after, 0 changed files.

## Developer controls

- `::vorkath` — public Ungael wall approach.
- `::vorkathdebug` — current generation, state, HP, attack counter, next special, acid/spawn status and reward eligibility.
- `::vorkathdev wake`
- `::vorkathdev special acid`
- `::vorkathdev special spawn`
- `::vorkathdev attack melee|ranged|magic|dragonfire|venom|prayer|fireball`
- `::vorkathdev reset`
- `::vorkathdev kill`

Every forced mechanic is explicitly no-reward.

## Verification

Completed automated checks:

- `:content:bosses:vorkath:test`
- `:content:drops:test`
- `:content:skills:magic:spell-attacks:test`
- `:api:combat:combat-commons:test`
- scoped Spotless formatting checks
- read-only revision-240 definition decode
- donor cache before/after SHA-256 comparison

Full GameServer application compile: passed.

Controlled persistence/startup validation:

- Restart 1: canonical runtime marker, revision 240, all plugin scripts and Vorkath asset validation loaded; `Server ready` at 12:27:20; ports 43594/8080/9091 bound by GameServer PID 38796.
- Restart 2: canonical runtime marker, revision 240, all plugin scripts and Vorkath asset validation loaded; `Server ready` at 12:28:35; ports 43594/8080/9091 bound by GameServer PID 30776.
- RSProx and RuneLite processes were not stopped or replaced.
- No startup exception, missing Vorkath definition, bind error or cache error appeared in either launch log.

This environment provides server-process and log access but no safe game-client input automation. Therefore the full combat loop was covered by deterministic tests and live asset/plugin startup validation; an end-to-end human click/kill/death/reclaim pass remains a client-side acceptance check and is not falsely reported as observed.

## Deliberately unresolved

The supplied donor does not contain explicit Vorkath sound calls or sound IDs. Native sequence-embedded sounds are preserved, but standalone OSRS sound IDs/ticks were not guessed. See `reason231-vorkath-unresolved.json`.

## Files changed for Vorkath

- `content/bosses/vorkath/src/main/kotlin/org/rsmod/content/bosses/vorkath/` — encounter, interactions, combat hooks, lifecycle, storage, access policy and pure rules.
- `content/bosses/vorkath/src/test/kotlin/org/rsmod/content/bosses/vorkath/` — mechanics, wall, storage and safe-lane tests.
- `content/drops/src/main/kotlin/org/rsmod/content/drops/tables/monsters/VorkathDropTable.kt` — current post-quest drop structure and exact shark-lure parity.
- `content/drops/src/test/kotlin/org/rsmod/content/drops/tables/monsters/VorkathDropTableTest.kt` — drop registration/multiplier/parity tests.
- `docs/bosses/vorkath/` and `tools/reason231-boss-import/vorkath/` — evidence, mappings and cache-safety tooling.

No unrelated file was reverted, reset or cleaned.
