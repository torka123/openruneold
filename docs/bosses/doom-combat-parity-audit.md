# Doom of Mokhaiotl parity ledger

Audit update: 2026-09-04
Target: the active OpenRune revision-240 server tree
Status: implemented and covered by deterministic tests; final live-client smoke remains required

## Evidence and confidence

The supplied implementation specification is the accepted mechanics contract. It was already researched by the user and is not reinterpreted in this pass.

The local RSProx transcripts were used as packet-level corroboration:

- `doom-3285.txt`: clean delves 1 through 8; primary timing and state-transition reference.
- `doom-1164.txt` and `doom-1168.txt`: Leagues captures; useful for cache identities and packet shapes, not authoritative for ordinary damage or timing.
- `doom-3340.txt`, `doom-3342.txt`, and `doom-3343.txt`: short encounter probes; useful for identities and ordering only.

Revision-240 cache definitions are authoritative for NPC, location, item, animation, graphic, projectile, headbar, and map identities. The OSRS Wiki is used for documented formulas and rates that are not visible in packets. Donor code is only a structural lead and is never an authority for IDs or mechanics.

Confirmed capture/cache facts include:

- Doom forms `14707`, `14708`, and `14709`; volatile earth `14714`; earthen shield `14715`.
- Per-delve combat levels `558, 608, 658, 708, 758, 808, 858, 908` and headbar `81`.
- Acid location `57283`; no second acid location is used.
- Larvae become active two ticks after spawning, crawl one tile every two ticks, and resolve against Doom's footprint rather than its centre tile.
- The falling-rock target is fixed when telegraphed.
- Delve 5 emits three shockwave pulses two ticks apart.

Server-side random-selection weights cannot be recovered from outbound packets. The isolated normal-attack weights (`rock 30`, `larva 20`, `charge 10`, otherwise a standard attack) remain explicitly inferred. Normal attack styles remain independent of rock impact styles; charge is also selected independently and is not forced to accompany a rock.

## Implemented encounter model

### Shared geometry and NPC presentation

- Doom uses one shared 5x5 footprint model for contact, larvae, car movement, fixed-point checks, shield positioning, and arena geometry.
- Spawned Doom forms receive the correct combat level for the active delve through the NPC-info protocol.
- The encounter uses the revision-240 identifiers and finite encounter-owned NPC lifetimes.

### Regular attacks, rocks, larvae, and acid

- Normal attacks use the configured delve timing and damage tables and check protection prayer when the hit lands.
- Rock shadows retain the originally telegraphed tile. Follow-up styles and hit timing are stored as run state rather than retargeted from the player's later position.
- Larvae use the 5x5 contact ring, collision-aware routing, periodic repathing, a stuck timeout, two-tick activation grace, and intersection-based boss/shield resolution.
- Small and giant larva hitpoints, demonbane caps, healing, explosive charge, shield damage, and player damage are separated by form and delve.
- Acid is created only with location `57283`, resolves at projectile impact time, uses cardinal collision-safe placement, persists across descents except the delve-6 reset, and deals Doom-specific typeless damage capped at 7. It does not use the global player-venom system.
- The moving shield clears acid only from its centre tile. Holy water retains its separate 3x3 clear.

### Shield, shockwave, and car phases

- The first shield transition is armed by the health threshold and gated by completed standard attacks so a large hit cannot skip it.
- Shield damage is isolated from normal hitpoints and protected by a phase epoch so delayed hits cannot damage the wrong pool.
- Shockwave counts are `1/1/2/2/3/3/4/5` for delves 1 through 8, spaced two ticks apart, with active expanding-ring damage. The recurrence gate resets after a phase so later valid phases are not permanently blocked.
- Charge, rocks, larvae, and standard attacks are selected without the former modulo rotation or artificial style synchronization.
- Car trample follows the real 5x5 footprint and samples the player's current tile as the segment arrives. Falling rocks and slam protection use their fixed telegraphed points.

### Rewards and persistent state

- Doom's chest service is the only reward authority; the inaccurate duplicate NPC drop table has been removed.
- Quantity scaling is `Q3 + trunc(Q3 * modifier / 100)` with delve modifiers `-50, -35, 0, 5, 10, 12, 14, 17, 20`.
- The ordinary reward table is a weighted table with total weight 104. Eye, Dom, elite-clue, guaranteed-tear, and chained-run handling are independent rolls/state.
- Pending rewards include stable run and result IDs. Claiming is transactional and idempotent, preventing duplicate delivery after repeated clicks or reconnects.
- Eye of Ayak charge count and mode are stored in item variables, with a 50,000 charge cap and the configured passive-charge award.
- The unique-risk hole uses location `50940`; the normal hole uses `57285`. The warning and Dom duplicate-glow state are driven by real pending reward state.
- Personal and global scoreboard data store all delve bands, best times, deepest delve, deaths, full-chain completions, deep delves, and display names. Global state is written through a temporary file and atomically replaced where supported. Malformed persistence is surfaced instead of silently discarded.

The current platform has no shared pet/follower-delivery service suitable for Dom. Dom item delivery and collection-log state are implemented; spawning/following remains an explicit platform limitation rather than a fake local pet system.

### Lifecycle

- Doom death clears all carried Doom rewards, ends the active run, and records the death once.
- Deaths outside an active Doom encounter do not emit Doom messages or mutate Doom state.
- Completion, leave, logout, teleport-out, reconnect, and cleanup paths remove encounter-owned NPCs, locations, delayed impacts, overlays, and transient run state.
- Scoreboard completion/death updates and reward creation use stable result IDs so retries are safe.

## Source ownership

| Area | Source files |
|---|---|
| Core rules and geometry | `DoomRules.kt`, `DoomGeometry.kt`, `DoomCarGeometry.kt`, `DoomCombatTimeline.kt`, `DoomAttackFlow.kt` |
| Runtime encounter | `DoomEncounterManager.kt`, `DoomCombat.kt`, `DoomWorld.kt`, `DoomLifecycle.kt` |
| Rewards and Eye state | `DoomRewardService.kt`, `DoomEyeState.kt` |
| Scoreboards | `DoomScoreboardService.kt`, `DoomGlobalStatsStore.kt` |
| NPC protocol | `engine/game/.../NpcInfoProtocol.kt`, `api/net/.../RspNpcInfo.kt` |
| Tests | `DoomRulesTest.kt`, `DoomAttackFlowTest.kt`, `DoomParityPersistenceTest.kt`, and the existing focused Doom test files |

## Automated acceptance coverage

The focused suite covers:

- all delve health, level, timing, damage, reward-scaling, and shockwave-count tables;
- independent attack selection and removal of deterministic modulo/style coupling;
- the 5x5 footprint, 24-tile contact ring, and footprint intersections;
- larva activation/routing contracts and shield/runtime gates;
- fixed rock/car geometry and impact-time resolution;
- reward table weight, pending-result codec, unique state, and idempotent claims;
- personal scoreboard codec/idempotency;
- durable global scoreboard reload, atomic temporary-file cleanup, and malformed-state rejection;
- exact and rounded scoreboard time formatting.

## Remaining live verification

Automated tests cannot verify final client camera, sound, projectile interpolation, or every graphic variant. Before calling the encounter visually 1:1, run a live delve 1-through-8 chain and verify:

1. combat levels/headbars and every state transition;
2. rock shadows remain fixed while moving and delve-8 double sequences remain distinct;
3. larvae crawl into the outer footprint ring without clipping or stalling;
4. acid placement, damage, persistence, delve-6 clear, moving-shield centre clear, and holy-water 3x3 clear;
5. recurring shield/shockwave phases, ring timing, visual order, and floor damage;
6. car Eye warning, path, trample, rock interception, and slam masking;
7. death forfeiture, chained descents, unique warning/glow, one-time reward claim, and reconnect behavior;
8. personal/general/delve scoreboard options across a server restart.

Do not describe the remaining visual packet presentation as verified until this matrix has been observed in the live client.


## Follow-up audit (2026-09-05)

- The supplied `Recording 2026-09-03 235905.mp4` was verified as Colosseum/Sol Heredit footage, not a Doom Delve 1-8 guide; it was excluded from mechanic decisions.
- The correct guide reference is `https://www.youtube.com/watch?v=DUL0hiFwEfk`. The video confirms slow magic/ranged basics (7:52–8:10), boulder style signals and fixed rubble targeting (8:15–8:47), larvae behavior (9:00–11:00), shield/coloured larvae (11:22–13:39), car phase (13:41–14:10), volatile-earth moving shield (14:52–16:24), D8 double boulders and six-hit prayer sequence (18:16–19:40), and the D1–D8 walkthrough (20:00–33:48).
- The guide confirms volatile-earth/slam can occur in any delve if the fight runs long enough; the slam count scales `1/1/2/2/3/3/4/5` for delves 1–8. It is no longer gated to D7+.
- The guide confirms larvae have 2 HP, non-demonbane attacks deal 1, demonbane weapons one-shot normal larvae, and delves 4+ show two protection prayers with the remaining colour/style as the correct attack. Delves 5–7 spawn two outside larvae; delve 8 uses one giant 4-HP larva outside the shield (the current OSRS post-hotfix behavior).
- The active manager now keeps each larva on its selected boss-contact route, keeps each wave on a single collision-aware route, displays all active prayer icons, and recognizes Silverlight/Darklight/Arclight/Emberlight/Burning Claws/Scorching Bow/Purging Staff demonbane weapons.
- D8 boulders remain two same-colour throws with separate rubble/prayer-disable events and the concatenated six-hit sequence; no new random attack rotation was introduced.
- RSProx entry evidence places the first-delves landing at region-local `(31,23)` and deeper-delve rebuilds at `(31,30)`; the generic `(31,28)` landing was removed.
- The scoreboard object now opens revision-240 interface 920 (`dom_scoreboard`) and populates the cache-probed personal/general/delve children instead of printing summaries to chat.
- Exact larva spawn coordinates and the initial public gap-entry animation are not established by the guide; those remain RSProx/cache-validation items rather than invented values.

## Medal and capture correction pass (2026-09-06)

- The configured delve-8 outside-larva behavior intentionally follows the supplied launch-video target: three ordinary larvae. This supersedes the later giant-larva note above for this project's chosen baseline.
- Rock Throw now snapshots the capture-derived split tile, emits exactly eight visible debris projectiles (`3388`–`3395`), creates the persistent rubble at detonation, disables protection prayer at the captured point, and preserves the distinct delve-8 double-throw/six-impact timeline.
- Volatile Earth now renders the full 613-tile strict radius-16 mask with per-tile `3405`–`3407` graphics and capture-relative travel delays. Car Slam uses the same authoritative arena geometry and surviving-rock cover instead of treating distant movement as safe.
- The shield transition is armed only by a completed hit that leaves Doom at or below 75% HP, waits for two completed standard attacks, keeps normal HP on the target HUD, and exposes the 500-point shield only through its overhead bar. An already-launched pre-shield hit retains the live grandfathered-impact rule; attacks committed after the shield forms require valid demonbane or larva damage.
- Larvae route to Doom's outer 5x5 contact ring while Doom remains fixed at the arena centre outside the car phase. Dead larvae can no longer resume pathing before their death event resolves, and the post-car reposition flag is cleared so later larvae do not despawn or wander.
- Shield-phase larva healing now refreshes the normal boss HUD. Instance descent, abandonment, logout-tab leave, teleport-out cleanup, dialogue cleanup, interaction-target cleanup, special-energy regeneration, and stale boss-bar teardown were hardened so state cannot leak into the next delve or outside the instance.
- Standard attack choice remains seeded random because the captures show genuine variation. No artificial modulo rotation was introduced; the per-delve state machine still owns legal mechanics and phase gating.
- Automated coverage now locks the delve 1–8 split schedule, the delve-8 paired debris/follow-up timeline, the eight-fragment contract, dead-larva non-progression, boss-HUD teardown, and leave-interface cleanup. Visual confirmation in the live client remains the final smoke-test gate.
