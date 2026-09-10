# Boss architecture migration — 9 September 2026

## Authority and preservation

Requested scope: architecture migration, not a combat rebalance. The user's later instruction
conditionally authorizes local deployment/restarts at about 5% remaining Codex plan allowance,
after finishing and testing the current migration and preparing a change/testing/continuation
handoff. The later checkpoint instruction also authorizes a backed-up, tested local deployment
before moving to the next boss; its results are recorded at the end of this report. Account
allowance is not the model's context/token counter. No remote push, merge or PR is authorized.
The current user instruction authorizes
the listed shared repairs and routine migrations; the older AGENTS combat checkpoint is satisfied
by recording the mapping before implementation. New behavior conflicts must still be reported.

Source safety baseline: `798b8c320`, branch `backup/boss-dsl-source-baseline-20260909`, parent
`f9dd030fc`. The commit preserves 235 previously modified/untracked source, test and documentation
files. Credential-pattern and private-key/token/webhook scans of the included files returned no
candidates; this is not a guarantee that historical Git objects contain no secrets. The index was
empty beforehand. No `.data` changes, credentials, caches, logs or generated files were staged.
Existing tracked `.data` files remain in the parent history; their working changes remain unstaged.
The original `custom/openrune-r240` branch is unchanged. Captures/logs are retained in place.

Doctor completed before changes: canonical JVM working directory, revision 240, SERVER/LIVE cache
paths and `.data/postgres` authority unchanged. Evidence: `logs/boss-dsl-migration-doctor-before.log`.
Source byte hashes: `logs/boss-dsl-migration-source-baseline-hashes.json`.

Architecture reference: fetched `upstream/main` at
`61139e76a216592d66c2b20366ef26d48609875d`.
[Boss API](https://github.com/OpenRune/OpenRune-Server/tree/61139e76a216592d66c2b20366ef26d48609875d/api/bosses),
[Scurrius](https://github.com/OpenRune/OpenRune-Server/tree/61139e76a216592d66c2b20366ef26d48609875d/content/bosses/scurrius),
[instances](https://github.com/OpenRune/OpenRune-Server/tree/61139e76a216592d66c2b20366ef26d48609875d/api/instances).
The local contribution file describes older Alter module names; use current Gradle module paths.

## Pre-edit shared mechanic-to-API mapping

| Requirement / evidence | Existing workflow and classification | Repair and executable verification |
|---|---|---|
| Anim.delay declared but ignored in interpreter | Upstream Effect.Anim and StandardNpcAccess.anim(seq, delay) | Forward native client delay; assert actual NPC animation output. Do not convert this field into server-tick suspension. |
| Projectile launch/impact presentation | Upstream Effect.Projectile, ProjAnim, native player queueHit | `launch`/`impact` are spotanim names, NOT tick delays. Launch is ignored; local impact works only when a hit exists. Exercise packet travel and native hit queue timing without changing controller values. |
| Repeat.gap ignored | Upstream Effect.Repeat and StandardNpcAccess.delay | Delay between repetitions, never after the last; deterministic native coroutine ticks. |
| Parallel is sequential | Upstream Effect.Parallel, GameCoroutine, StandardNpcAccess | Add minimal structured parallel access to the existing NPC scope: child waits must not overwrite the shared NPC delay. No world attack-loop/timeline framework. Test branches including suspending external handlers, joining and cancellation. This helper is a proposed shared addition, not already upstream. |
| Weighted/forced selection uses Kotlin Random | Upstream GameRandom, BossEncounter, EncounterRegistry | Inject GameRandom through registry; retain weights and cooldowns. Restore upstream elapsed-time forced cadence instead of local exact-modulo polling. Test deterministic boundaries and missed deadlines. |
| Phase starts anchored at world tick zero locally | Upstream initializes phaseEnteredTick on first combat; BossEncounter transitions | Restore encounter-relative initialization; test transition cursor/cadence reset. Preserve existing phase thresholds. |
| Queued hits outlive encounter | Native queued hit processor plus existing local HitContext/HitScope and HitValidity | Attach existing encounter validity before damage, not only an after-damage visual callback. Test teardown and replacement before impact. These scope APIs are local, not mislabeled upstream. |
| External handler safety | Upstream BossExtensionRegistry and existing local encounter state/cleanup | Reject duplicate handler ownership; retain named handlers, clear encounter-owned state on removal. Tests for duplicate registration and cleanup idempotency. |
| TargetExpr stubs return current target | Upstream TargetExpr, native PlayerList and DamageContributions | Use eligible same-level/live/owned candidates, GameRandom and native contribution/prayer state. Test distinct targets and ownership isolation. |
| Instance lifecycle | Upstream InstanceScript, InstanceManager, keyed events; local teleport/disconnect and detach fixes | Reuse shared membership and removal. Session-owned mechanic state may require a small reusable InstanceSession state facility; no boss-local player/run registry. Verify native lifecycle integration before migrating content. |

Additional verified generic ownership gap: native `WorldQueueList.add` returns Unit and exposes no
cancellation handle; repository searches found only iterator-based removal in WorldQueueListProcess.
Add a cancellable enqueue overload to that same list (not another scheduler), with safe removal
during iteration. BossEncounter owns the cancellation handles through existing cleanup actions.
Tests must cover cancel-before-due, cancelling the iterator's next node, double cancellation and
encounter teardown. Existing `add` callers keep their signature and execution order.

No new boss-specific constants, timing, hit rules or reward rates are authorized by this table.
Existing local source-attribution, trace, ownership, recoil and impact-resolution additions are
preserved. Deferred concerns such as ignored noRepeatBias must not silently rebalance existing specs.

### Source-defined instance integration (mapped before editing)

Kraken's existing `krakenSpec` is an `InstanceSettings.withArea` specification with no DB settings
row (`settingsRowId = 0`). Local and upstream `InstanceScript` require an abstract settings row and
derive their key from it. The cache's instance gamevals contain no Kraken settings row. Requiring
a new cache row merely to adopt keyed lifecycle hooks would unnecessarily change the cache data.
Add a source-key/spec constructor to **the existing InstanceScript**; keep the database constructor
and all default flows unchanged. Source-defined content explicitly registers its loc interactions
and calls the same protected `completeInstanceEntry` / `defaultLeaveFlow`. Test registry startup,
keyed events, preservation of the supplied area/settings and the existing database path.

`InstanceSession` already owns identity, membership, contribution and timing, but repository and
upstream searches found no typed content-state attachment there. Add a typed identity key and
get/create accessor to that class. Native reset clears this state; native destruction marks it
ended and clears it. Do not store another player list or username/run index in content. Regression
tests cover two sessions, reset, destruction, stale references and native manager teardown. These
are proposed reusable local extensions of upstream APIs, not claims that upstream already has them.

### Native ordinary attacks (mapped before editing)

Kraken currently calls `NvPCombat.attack`, whose upstream implementation owns native attack gating,
animation/sound, accuracy-before-damage RNG order, combat metadata, retaliation, defender animation,
projectile travel and queued hits. Existing DSL `Effect.Projectile`/`Hit` do not perform all of these
steps. Simply translating Kraken to those effects would silently remove combat metadata/presentation
and change random draw order. Exact upstream reference:
[NvPCombat](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/combat/combat-scripts/src/main/kotlin/org/rsmod/api/combat/NvPCombat.kt).

Add a declarative ordinary-NPC-attack effect to the existing DSL, interpreted by delegating to that
same `NvPCombat` pipeline. It has native attack type, optional maximum/animation/projectile/impact
delay, impact graphic and prayer policy; no content callbacks or private loop. BossEncounter remains
the ability selector/cadence owner. The existing local public `NvPCombat`, `NpcProjectile` and
`NpcAttackOverride` are extensions of upstream (the upstream class is internal), not official public
upstream API. Add a HitContext field to the existing override so the DSL can attach the same native
encounter cancellation scope used by other effects. Test native-vs-interpreted attack output,
accuracy draw ordering, prayer bypass, combat state, travel ticks and teardown. Existing DSL specs
retain their current effect semantics. No new boss-specific combat pipeline is introduced.

## Inventory and progress

### Entry/exit safety found during the golden migration

Pre-edit mapping: `InstanceScript.completeInstanceEntry` calls the existing protected telejump
but finalizes membership unconditionally; `defaultLeaveFlow` removes membership before knowing
whether that telejump succeeds. ProtectedAccess already validates teleport rules and destination
zones. Search: all `finalizeEntry` callers, InstanceCreateScript, InstanceManager pending-entry
cleanup, ProtectedAccess telejump/validator/hooks, and upstream/main InstanceScript. This is a
native instance-flow defect, not a Kraken mechanic. Keep the established APIs: guard the entry
callback against a destroyed session, commit membership only at the intended destination, cancel
a failed newly-created pending instance, and detach a normal exit only after successful movement.
Do not bypass teleport rules. Tests execute the real protected access and InstanceScript with a
refusing validator, successful movement, cancelled/destroyed sessions and repeated callbacks.
No new boss-specific API is needed. Runtime/client validation remains outstanding.

### Kraken characterization and pre-edit migration mapping

Reference behavior is the recoverable `798b8c320` implementation, not a fresh claim of OSRS parity.
`KrakenEncounterCharacterizationTest` executes that implementation's native instance entry,
five dormant spawns, individual/boss awakening, same-entity transformations, stats/action locks,
one-shot completion and tentacle removal. Existing rules tests and all revision-240 IDs remain.

| Existing Kraken responsibility | Destination | Parity check / qualification |
|---|---|---|
| `Run` map keyed by PlayerUid, membership polling and custom logout/teleport handlers | Native InstanceManager indexes + typed InstanceSession state; InstanceLifecycleScript | Shared lifecycle integration tests cover removal, death, disconnect, stale references and two instances. No replacement membership map. |
| Manual five-NPC creation and attachment | InstanceArea.npcSpawns, existing InstanceManager spawn workflow | Characterization checks exact coordinates/order, dormant forms, ownership and respawn flags. Join hook applies dormant presentation only. |
| Ordinary boss magic and tentacle ranged attacks | BossSpec + declarative NpcAttack effects through NvPCombat | Keep 4 ticks, 28/2 maxima, prayer bypass, fire-wave/water-strike graphics and magic_spell controller; native adapter equality test. |
| Repeated `keepAggressive` polling | Native apPlayer2 interaction established at awakening | Upstream NvPCombat explicitly documents that NPC interactions repeat until cancelled. No private tick loop; verify approach retains the target. |
| Whirlpool activation and paired wake-up | Small named KrakenWhirlpools handler, state on InstanceSession | These are pre-combat encounter-specific interactions, not a second combat phase engine. |
| Create/leave/admin convenience commands | InstanceScript completeInstanceEntry/defaultLeaveFlow; ProtectedAccessLauncher | Retain free private one-player policy, messages, loc options and commands; native protected-access guard replaces the old contradictory guard inside protected loc access. |
| Boss/tentacle deaths and owner-position loot | Existing NpcDeath workflow; snapshot owner/drop coordinate before NPC deletion | No replacement drops, KC or rewards service. Existing no-natural-respawn behavior is retained, not silently rebalanced. |

API classification: BossSpec/BossCombat/InstanceScript/InstanceManager/NpcDeath/ProtectedAccess are
upstream workflows. Source-instance constructor, typed session state and declarative native attack
effect are the new shared local extensions documented above. Attack validation and source-aware hit
scopes are existing local shared integrations. No cache paths/data or mechanic constants change.

### Vorkath pre-edit mapping

Baseline: `798b8c320`, with 103 existing tests passing again in
`kraken-golden-broad-regression.log`. Preserve the five-capture projectile model, all capture tests,
safe-lane/collision logic, reclaim codec/storage, access policy, developer no-reward restrictions,
live audit script and research documents. No change to its documented inferred probabilities.

| Responsibility | Destination | Characterization / evidence |
|---|---|---|
| Six ordinary attacks at five ticks, alternating specials | BossEncounter forced-ability cadence and BossSpec phases/selectors | VorkathTimelineTest witnesses 8/13/18/23/28/33 then special 38; migrate these assertions to actual DSL dispatch before deleting the timeline |
| Ordinary magic/ranged/melee and conditional weighted selection | NpcAttack effects and WeightedRef.requires | Preserve VorkathRules.standardWeights, including its explicitly inferred weights; native attack tests compare metadata and RNG |
| Mouth-position projectile geometry and equipment-snapshotted dragonfire, venom/prayer impact | Small named attack-parameter resolver through BossExtensionRegistry; native NvPCombat still owns launch/accuracy/hit/prayer queues | VorkathProjectiles tests; exact fields remain in the existing researched model. This is not an external attack loop. |
| Sleeping/wake/death presentation | Native NPC lifecycle plus declarative delays/phase transitions; preserve actual delete/add sequence | Wake +7, first normal +1, death +6; existing native entity tests |
| Acid tiles and spawn movement/contact | Focused external handlers using native collision, movement, hit and scoped scheduling | Existing acid layout, contact, Crumble Undead, freeze and queued-hit tests stay executable |
| Membership, enter/exit/teleport/logout | InstanceScript + InstanceLifecycleScript/InstanceManager | Preserve selected ice-wall X, two-tick crossing, free solo room; remove username/PlayerUid run index and duplicated lifecycle |
| Session resources, freeze/drop-duration restoration | Typed InstanceSession state and native teardown callbacks | Must clean on forced termination as well as ordinary leave; no global lookup map in ended-event callback |
| KC/PB/reclaim/drop lifetime and diagnostic controls | Preserve existing native vars/attributes, NpcDeath and death storage hooks during architecture migration | Existing storage/50th-kill/idempotent death tests; no unrelated persistence or balance redesign |

Additional shared gaps verified by local/upstream searches: Effect.NpcAttack currently only accepts
static controller geometry/max hit; ProjectileConfig cannot express Vorkath's facing-dependent mouth
and equipment snapshot. Existing fork NvPCombat already supports the exact override, but calling it
from every boss's external attack function would reintroduce duplicated scheduling. Add a named,
non-suspending **parameter resolver** to the existing BossExtensionRegistry, referenced by NpcAttack.
It returns the existing NpcAttackOverride data, with cadence and encounter hit cancellation enforced
by the interpreter. Test override geometry/maxima/impact callbacks and composed cancellation; reject
duplicate/missing names. This is a proposed new local extension of upstream's registry, not an
official upstream API. No second hit/accuracy processor is proposed.

InstanceSession state currently discards references without releasing resources; InstanceEndedEvent
is published after removal and carries no session state. Native instance APIs have no content-owned
resource cleanup registration (`attachNpc` is the entity workflow, not freeze/visual restoration).
Add a small session-owned cleanup registration, exercised on reset/end and all teardown paths, with
tests proving idempotence and cleanup continuation after one handler fails. It must not own combat
cadence or replace InstanceManager. Search references: upstream/main api/instances and api/bosses,
local InstanceManager.destroy/resetServerOwned, InstanceSession, InstanceLifecycleScript, BossEncounter
cleanup and GameCycle/WorldQueueListProcess. Shared world work runs before NPC/player movement;
preserve that snapshot boundary when replacing Vorkath's StartCycle deadline lists.

One existing condition also needs repair: `Condition.PlayerEnterRange` is declared but always
returns false. Use native footprint distance (and matching plane), which lets Vorkath preserve its
existing "adjacent but not under the boss" melee eligibility without a custom selector. Test inside,
edge, beyond-range and wrong-plane positions before wiring its weights. This repairs an existing
generic condition rather than introducing a Vorkath-specific predicate.

Vorkath lifecycle integration (in progress, not a completed combat migration): native InstanceScript
entry, selected-X exit, keyed join, declarative sleeping spawn and typed session-owned state replace
the private membership map, generation counter and travel/leave/logout/death-cleanup implementations.
Session cleanup owns arena resources, while InstanceManager/InstanceLifecycleScript owns NPC/index
retirement. Existing diagnostic entry/exit now calls the same protected flows (not enabled live).
The characterization fixture now executes the real InstanceManager, InstanceScript and shared
lifecycle plugin. `vorkath-native-instance-test-5.log`: 105 tests pass, 36s/162 tasks.

The new native entry fixture found an actual collision defect, separate from architecture debt:
activation added the active boss over the sleeping boss, then deleting the sleeping footprint erased
the active footprint. Delete the sleeping actor before adding the active actor on that same tile;
`spawnRejectsStuckLandingTilesAndClipsAlongTheCapturedBossEdge` now passes from real entry/wake.
Both remain delete/add at the same tick; client/index ordering still requires runtime comparison.
The intermediate fixture run exhausted the test heap because inline mocks retained collision maps;
the fixture releases those recorded mocks after each test. No production memory limits changed.

Scoped timing gap for external mechanics: searched local `api`, `engine`, upstream/main
WorldQueueList/WorldQueueListProcess, NPC access, BossEncounter.schedule and instance tick hooks.
World queues use relative countdowns and may process an appended callback in the same cycle;
InstanceTimeTick is only for time-limited sessions and runs after player movement. Neither provides
an exact pre-input absolute deadline. Moving Vorkath's tile snapshots to NPC coroutines would move
them past player input. Add an optional readiness guard to the existing cancellable world queue
and `BossEncounter.scheduleAt` using MapClock; leave existing relative queues unchanged. This is a
small local extension of the native world queue, not another scheduler. Test nested scheduling at
different queue positions, exact deadlines, legacy countdown semantics, cancellation and encounter
end. Use it for unique arena effects; ordinary attacks remain declarative DSL effects.

`boss-absolute-world-queue-red-2.log`: two of three regression tests fail on early execution and
ignored readiness, while the unchanged relative-countdown test passes. After the processor honors
readiness, `boss-absolute-world-check.log` passes engine/game, game-process and boss API checks plus
105 Vorkath tests (39s, 178 tasks). Encounter removal also physically unlinks absolute work.
`vorkath-native-instance-check.log` passes selected-file formatting, Vorkath check and instance tests
(40s, 168 tasks); this still does not certify Vorkath's remaining custom combat timeline.

Vorkath combat wiring now registers only BossCombat for active/sleeping forms. BossEncounter owns
weighted selection, the six-normal cadence, action locks and all combat phase identity. The old
VorkathTimeline is moved to **test sources only**, as a frozen comparison oracle, not shipped or
registered. Exact wake, fireball, pool, splash and spawn deadlines delegate to scoped native world
work. Pending collections are audit metadata, no longer polled to execute hits/projectiles.
`vorkath-dsl-production-test.log`: 105 tests pass. `vorkath-scoped-special-effects-test-2.log`:
107 tests pass (34s/162 tasks), including native six-normal/acid/six-normal/spawn rotation against
the old cadence, all 25 rapid shots at +4..+28, zero world tasks after exit during each special or
wake, and collision/queued-hit races. The subsequent handler-extraction commit separates arena
effects, spawn collision/contact, session data and no-reward diagnostics. Live packet/visual
comparisons remain unperformed.

Initial line-count definition: main Kotlin files ending in `Manager.kt` or `Timeline.kt`, excluding
generated output. Includes specialized manager files (therefore not all counted lines are duplication).
Diagnostic-only modules are excluded. Zero manager lines is not proof of architecture compliance.

| Boss | Original architecture / manager lines | DSL conversion | Instance conversion | External handlers retained | Tests | Remaining uncertainty |
|---|---|---|---|---|---|---|
| Kraken | private per-player run, native combat wrapper; 363 → 0 manager lines | Boss/tentacle specifications; ordinary attacks delegated to native NvPCombat by DSL | Source InstanceScript; native spawns, keyed joins, session state and lifecycle | KrakenWhirlpools (171 lines): dormant activation, one-shot completion/drop tile; native attack-validation hook | 12 passing tests, including 8 native integration/characterization tests | No live deployment/client verification; no new OSRS parity claim |
| Vorkath | managers/timing; 1,254 -> 426 coordinator lines | BossSpec/BossCombat normal attacks, weights, forced cadence, phases; old timeline test-only | InstanceScript source arena, keyed join/leave, native ownership and cleanup | Arena effects (344 lines), spawn collision/contact (228), session data (71), no-reward diagnostics (105), existing reclaim storage | 110 passing module tests; all boss/shared regressions and server classes pass; revision-240 cache audit passes | Not deployed; PR unsubmitted. Inferred selection/path rules and original non-player damage exclusion retained, not certified as OSRS-correct |
| Corporeal Beast | manager; 502 -> 0 | BossSpec normal attacks, weighted styles/animations/cadence, native NvPCombat; old selector test-only | Public lairs retained, so no InstanceScript required; native NPC/encounter lifecycle | Room recovery/stomp (161 lines), core (114), split area (95), drain impact parameters (29), state (49) | 24 tests pass (17 native fixture + 7 prior rules/oracle); all boss/shared tests and server classes pass | Existing third-party projectile provenance, core poison stun and live visuals remain unverified |
| Alchemical Hydra | manager; 858 | Ordinary native volleys, rotation, forced cadence and phase identity | Native InstanceScript, typed session state and native same-actor respawn | 260-line observation/form coordinator; 317-line arena hazards; 71-line transform presentation; parameter/hit-policy adapters | 22 deterministic tests pass, including native lifecycle and two kills | No live verification; baseline-only hazard/pathing constants, non-player damage exclusion and natural exterior entrance remain uncertified |
| Grotesque Guardians | manager; 945 -> 327 coordinator lines | Two BossSpecs: ordinary native volleys, phase identity, ordinal style selection and forced cadence | Native InstanceScript, typed session state, configured actors and validated native pair reset | Arena geometry/effects (343 lines), paired form coordination, native parameter/damage adapters | 21 deterministic tests (15 native), 48 drop tests pass | No live verification or cache repack; inherited lightning vulnerability, non-player damage exclusion and draft-only spatial values remain uncertified |
| Zulrah | manager; 993 -> 300 coordinator lines | Four unchanged declarative rotation programs, native attacks and separate snakeling specs | Native InstanceScript, configured actor, typed session state and same-actor respawn | Spatial clouds/eggs/tail handler (307 lines), surface positioning, existing reclaim storage and native parameter adapters | 43 tests (21 native, 22 retained) and revision-240 cache audit pass; all boss/shared regressions and app classes pass | No deployment/cache repack/client verification; Grid Master capture context, tail-order conflict and inherited non-player damage exclusion remain uncertified |
| Muspah | manager; 1,088 -> 552 | Weighted normal attacks, final cadence and all timed phases on BossSpec/BossEncounter | InstanceScript, configured actor, typed session, native death/respawn and KC | Threshold/shield policy, spatial cloud/spike/LOS handlers, corruption presentation | 67 tests (27 native, 40 retained) pass | Draft timings, projectile geometry, source exclusions and shield-skip policy not OSRS-certified; client verification pending |
| Cerberus | manager; 1,290 -> 598 | Native ordinary attacks, triple components, weighted/conditional rotation, forced diagnostics and timed phases | Protected InstanceScript, typed session state and same-actor native death/reveal | Arena effects426 lines (Souls/lava/wall); phase-ready presentation, debug and CA policy | 75 module tests,2 revision-240 audit tests; all boss/shared regressions and app classes pass | Command-only access retained; donor/capture geometry and timeout fallbacks not OSRS-certified; not yet deployed |
| Royal Titans | manager; 2,017 -> 830 | Elemental ordinary attacks, per-Titan platform/arena/teleport phases migrated | Protected InstanceScript/session ownership; native paired reset and elemental corpse deadlines; no private membership indexes | Focused routes, landings, walls, elementals, paired tile attacks and statistics/reward policy | 129 passing module tests,3 revision-240 audit cases; broader boss/shared checks and two controlled boots pass | Source1279d1676 deployed; legacy rotation/reward/CA and visual evidence gaps are not resolved by architecture migration |
| Nex | manager; 1,321 | Pending | Pending | Group specials/reward coordination | Existing tests preserved | Full encounter work remains incomplete |
| Yama | managers; 2,908 | Pending | Pending | Contracts/judges/group mechanics | Pending | Detailed mapping pending |
| Doom of Mokhaiotl | managers; 4,432 | Pending | Pending | Delves/larvae/scoreboard | Pending | Detailed mapping pending |
| DT2 | manager; 3,706 | Pending | Pending | Four environmental encounters | Pending | Detailed mapping pending |
| Dagannoth Kings | no *Manager.kt | Review separately | Review separately | Pending | Pending | Not classified by filename alone |

Existing DSL consumers (Bryophyta, Chaos Fanatic, Crazy Archaeologist, Mole, Graardor, KBD,
Kree'arra, K'ril, Obor, Scorpia, Scurrius, Wilderness, Zilyana) must be regression-tested, not replaced.

## Verification log

- `3f1bddb26`: regression-first commit. `boss-dsl-regression-red-2.log`: 12 tests, 11 failures
  against the pre-repair interpreter/cadence (the native coroutine fixture advances both map clocks).
- Shared repairs: animation client delay, launch/impact graphics, repeat gaps, parallel native
  coroutine branches, injected random selection, encounter-relative forced cadence, live target
  selection, pre-impact hit invalidation, cancellable native world work, idempotent cleanup and
  atomic duplicate registration rejection. No boss timing constants or cache values changed.
- `boss-dsl-repairs-test-7.log`: `:api:bosses:test :api:npc:test :engine:game:test`, success;
  20 boss API, 12 NPC API and 35 game tests, no failures/skips.
- `boss-dsl-broad-regression.log`: unscoped API/game `check` plus all boss tests stopped at
  **pre-existing formatting violations in untouched sources**. Those files were not formatted.
- `boss-dsl-broad-regression-2.log`: same checks with `scripts/format-selected.init.gradle` and
  explicit changed-file manifest, plus every boss module's `test`, `--continue`; success in 1m31s,
  319 tasks. Boss module test totals: Hydra 7, Bryophyta 4, Cerberus 52, Chaos Fanatic 3, Corp 7,
  Crazy Archaeologist 3, DKS 10, DT2 133, Doom 115, Mole 3, GG 6, KBD 2, Kraken 4, Muspah 40,
  Nex 18, Obor 3, RT 97, Scorpia 3, Vorkath 103, Wilderness 63, Yama 112, Zulrah 22.
  Other boss modules compile but have no test sources; this is not runtime coverage for them.
- `scripts/check-boss-architecture.ps1 -SelfTest` and default budget check pass. The detector
  flags new debt, while explicit per-rule waivers preserve specialized content and identify
  existing migration debt. It is a review aid, not mechanical parity evidence.
- Selected-file formatting and `git diff --check` pass. No deployment, live restart, client
  gameplay, packet comparison or visual parity is claimed.
- `boss-instance-source-check.log`: source-defined and database-defined InstanceScript startup,
  keyed join dispatch, typed session state isolation/reset/retirement, and native InstanceManager
  exit/teleport/death/logout/disconnect/NPC ownership/re-entry tests pass. Instance module now has
  26 passing tests; boss API and Kraken regression tests also pass. Scurrius compiles (no tests).
  Physical region allocation is a test recording repository, not a running client or server.
- `boss-native-attack-check-2.log`: DSL/native attack adapter checks pass, including direct-vs-DSL
  packet projectile value equality, native animation, damage/prayer result, combat vars, travel
  delay, no damage RNG on a miss, action-lock cadence and transformed-NPC deletion. Boss API now
  has 24 passing tests. Native NvPCombat change only threads HitContext through its existing hit
  and retaliation queues; all previous override fields are preserved.
- `boss-native-broad-regression.log`: every boss module test passes after adding the ordinary
  attack effect (1m21s, 306 tasks). No boss migration is implied by this adapter test pass.
- `boss-instance-entry-red-2.log`: all three entry safety regression tests fail against the old
  shared flow. `boss-instance-entry-green.log`: 29 instance tests pass after guarding failed entry,
  destroyed-session callbacks and refused exits. This does not bypass native teleport validation.
- Kraken baseline characterization commit `b11c13366`: 7 tests passed before migration
  (`kraken-characterization-before.log`). The same entry/awakening/completion assertions passed
  after migration (`kraken-characterization-after.log`).
- Native combat regression exposed a real baseline registration defect, separate from architecture
  debt: AiPlayerEvents and NpcQueueEvents dispatch by original `npc.id`, but the old handlers only
  registered active forms while instances created whirlpools. Both original and active forms now
  register through the single specification/native death workflow. No IDs or transformations changed.
- `kraken-native-lifecycle-test-2.log`: 12 tests pass. Runtime objects and native coroutine/event
  dispatch verify attacks at 101/105/109, 28/2 maxima, magic/ranged types, prayer bypass, projectile
  travel queue delays, death/loot hooks at the owner's tile, one-shot completion, two private rooms,
  fresh re-entry, normal exit and external teleport/logout/disconnect/death cancellation.
  The test fixture records repositories and advances native coroutines; it is not a live client.
- `kraken-golden-check.log`: scoped formatting, Kraken check and shared boss/instance/combat tests
  pass (34s, 170 tasks). All Kraken architecture waivers are removed; detector and self-tests pass.
- Golden-pattern review: no private combat scheduler, membership map, manual instance spawning,
  polling cleanup or replacement reward pipeline remains. Only dormant presentation/awakening and
  completion coordination are session-owned specialized state. The native no-natural-respawn
  baseline is intentionally unchanged; consecutive test kills use exit and fresh instance entry.
  Existing research remains authoritative for future mechanical corrections, not proof of parity.
- `kraken-golden-broad-regression.log`: all boss module tests plus boss/instance/NPC/combat/game API
  tests pass (1m10s, 316 tasks). `git diff --check` passes. `.data` edits remain deliberately
  uncommitted and untouched. No live deployment/restart was performed.
- `boss-attack-parameters-red.log`: the new resolver regression fails before interpreter wiring
  (it sees the static maximum instead of the resolved snapshot). `boss-attack-parameters-green.log`
  passes after wiring; supplied attribution/scopes and impact callbacks are preserved while the
  interpreter always enforces the declared cadence and encounter cancellation.
- `boss-instance-cleanup-test.log`: 31 instance tests pass, including resource release across every
  teardown route, reset/unregistration, and continued native cleanup when one resource callback fails.
- `boss-specialized-adapters-check.log`: selected-file API formatting/checks plus Kraken and
  existing Vorkath tests pass (45s, 177 tasks). Boss API: 25 tests; instances: 31; Kraken: 12;
  Vorkath: 103. These are supporting APIs; Vorkath's migration is not yet complete.
- `boss-footprint-condition-red.log` fails against the stubbed range condition. After repair,
  `vorkath-dsl-characterization-2.log` passes: 26 boss API tests and 105 Vorkath tests. The first
  combined run exposed concurrent initialization of the shared revision-240 test cache; Vorkath's
  tests now run serially like Kraken and the shared API fixtures. No gameplay timing changed.
- At the characterization checkpoint, Vorkath's new specification was test-only, not registered beside
  the old combat in production. It reproduces six native attacks and the following special deadline,
  exact ranged projectile fields and queue delays, all selection weight boundaries including
  underfoot exclusion, and retirement of queued hits. Existing special/capture tests still run
  against the old wiring until each specialized/lifecycle path was migrated and compared.
- Final Vorkath handler extraction: `vorkath-final-handler-check.log` passes scoped formatting,
  Vorkath check (110 tests), boss API, instance and Kraken tests (40s, 174 tasks). Native death
  queue, two consecutive completions, multi-instance isolation and logout resource restoration
  before deferred map destruction are executed in headless native fixtures.
- `vorkath-migration-broad-regression.log`: all boss module test tasks, boss/instance/NPC/game-process/
  combat/game tests and `:server:app:classes` pass (1m37s, 503 tasks). Existing modules without tests
  are compilation-only coverage. `vorkath-migration-cache-audit.log`: one executable revision-240
  Vorkath cache audit passes (21s, 18 tasks). No cache path, definition or live runtime changed.
- Vorkath no longer needs dependency, private-timeline, global-run or InstanceScript waivers.
  Remaining reviewed exceptions are specialized area/contact effects and the Torfinn reclaim
  reminder hook. The latter does not manage encounter membership or cleanup. The manager retains
  presentation, attack eligibility, native KC/PB and reclaim coordination, not ordinary scheduling.

## Corporeal Beast mapping (before migration)

Architecture reference: upstream/main `61139e76a216592d66c2b20366ef26d48609875d`.
All coordinates, attack formulas and timing below characterize existing `BEHAVIOUR.md`, Assets,
Rules and the 502-line manager at the source baseline; they are not new OSRS verification.

| Behavior preserved | Existing path | Established workflow / implementation | Executable coverage |
|---|---|---|---|
| 40% adjacent melee; otherwise three equally weighted magic variants | Manager attack selector | Official BossSpec/WeightedRandom/Whenever/Choose; normal NpcAttack adapter (documented local extension) | Registered Ap2/Op2, all weight boundaries, both melee animations |
| Four ticks, occasional three ticks | Per-attack actionDelay assignment | Official upstream BossEncounter.attackRateOverride and BossCombat gating; declarative effect setting that existing concept | Injected RNG and exact native launch ticks |
| Melee 33, magic 65, impact-time 100%/33% prayer reduction | NvPCombat override | Existing native NvPCombat through DSL; named parameters only for drain impact callback | Native hits, both prayer timings, drain/heal, projectile equality |
| Tile-targeted main/six split projectiles | Manager queues with private generation | Named area handler; scoped native world queue and source-aware hit validity | Tile snapshot, split coordinates, queued cancellation |
| Two public lairs, fixed radius/plane restriction | Manager room check | Existing map spawns/routes remain; launch eligibility predicate in shared BossCombat; no player/run maps or new private room | Other lair/plane exclusion, simultaneous lair isolation |
| Core jump/siphon, underfoot stomp, empty-room heal/stat recovery | Global states/coreOwners maps and LateCycle handler | State and owned NPCs on BossEncounter; same native LateCycle stage enumerates native NPC list for specialized hazards | Native NPC/core ownership, timing, death/reset/delete, no stale hit/task |
| Native death/drops/respawn | NpcDeath plus duplicate cleanup events | Keep NpcDeath; BossCombat lifecycle owns cancellation, cleanup resource callback removes core | Native death queue and fresh encounter identity |

Searches: full local boss spec/interpreter/extension registry, NPC timer/queue APIs, NvPCombat
and attack validation, native lifecycle and instance APIs, upstream boss spec/Effect/BossCombat/
BossEncounter plus upstream content usages. Exact upstream references are files under
`https://github.com/OpenRune/OpenRune-Server/tree/61139e76a216592d66c2b20366ef26d48609875d/api/bosses/src/main/kotlin/org/rsmod/api/bosses`.
Upstream has `BossEncounter.attackRateOverride` and BossCombat precedence, absent locally.
Bring that capability across without replacing local edits. Its declarative setter is a small
local DSL extension; no Corp-specific concept or independent scheduler. Existing Choose can select
weighted branches, but currently consumes the parent phase forced cadence/cursor, a generic defect
requiring a regression and isolated branch selector state before using it for variants/cooldowns.
A shared optional launch eligibility callback is needed for public room bounds: native PvN validation
is the opposite attack direction, while hit validity alone acts after selection and cannot guard an
external tile launch. The callback is a local BossCombat extension, not a private combat hook.
NPC timers run at a different stage than the characterized LateCycle hazard snapshot; retain the
native LateCycle hook for specialized effects only, with native NPC/encounter ownership instead of
a plugin-owned states map. These scoped API adjustments fall under the requested framework repairs;
no cache, access, balancing, contribution or reward changes are proposed.

Ownership adjuncts: existing `BossEncounter.own/owned/drainOwnedNpcs` and `state` lacked a way to
detach one retired summon or read state without creating it. Add `disown` and `stateOrNull` to those
same collections, with removal/isolation tests; do not add another registry. These are local shared
extensions to the already adopted encounter identity.

`boss-nested-choice-red.log`: both new choice tests fail (28 tests, 2 failures). After isolating
branch selection state and validating branch keys, `boss-nested-choice-green-3.log` passes 30 boss
API, 12 Kraken and 110 Vorkath tests (39s, 168 tasks). `boss-choice-check-2.log` passes scoped
formatting and API check (43s, 159 tasks). The first check command used an unavailable formatter task;
the corrected invocation uses the project's existing selected-file Spotless workflow.
`corp-characterization-before-3.log`: 13 tests pass against the old manager (31s, 155 tasks).
`corp-dsl-characterization-after.log`: the same 13 assertions pass via production BossCombat wiring
(39s, 157 tasks). Two earlier baseline fixture iterations were corrected to read the native deferred
impact-hit queue rather than mistakenly looking only for already-built hits.

Corp migration completion (source/headless only): `corp-native-lifecycle-tests-2.log` passed the
native registry/world-queue tests (32s, 157 tasks). `corp-final-native-check.log` passed 24 Corp,
30 boss API, 12 Kraken and 110 Vorkath tests plus scoped formatting/check (44s, 176 tasks).
After removing the unregistered old manager and moving its selector to a test-only oracle,
`corp-final-native-check-2.log` passes (40s, 161 tasks). `corp-broad-regression.log` passes every
boss module test, affected shared tests and server app classes (58s, 503 tasks).
Native fixtures cover both melee animations, all weighted style boundaries, tracking/tile projectile
fields, deferred prayer changes, resolved-damage drain/healing, core ownership/jump/siphon/removal,
six-way splitting and callback unlinking, seven-tick stomp, empty-room recovery and reset,
teleport/logout/death hit cancellation, native respawn events and independent public lairs.
Impact callbacks are exercised with explicit resolved damage; no new drop or live-client claim.
The native death/drop registration remains unchanged apart from retiring the shared encounter.
24 test passes do not verify disputed OSRS rules or new public/private instance behavior.
The 502-line manager is recoverable from baseline commit `798b8c320`; no evidence or unrelated
source was removed. Dependency waiver removed; seven reviewed specialized-combat matches remain.

## Alchemical Hydra mapping (before migration)

Reference remains official upstream `61139e76a216592d66c2b20366ef26d48609875d`:
`api/bosses` (BossSpec, BossEncounter, EffectInterpreter), `api/combat/combat-scripts/.../NvPCombat.kt`,
`api/instances/.../InstanceScript.kt`, and `content/bosses/scurrius/.../ScurriusInstance.kt`.
The 858-line Hydra manager is the characterization baseline, not independent OSRS evidence.
The existing seven rules tests and revision-240 mappings are preserved. No balancing changes are
authorized by this migration; uncertain threshold, hazard and targeting rules remain uncertain.

| Preserved behavior | Current ownership | Planned established pipeline | Tests / confidence |
|---|---|---|---|
| Observation entry, exact movement, chamber door, private solo region | PlayerUid runs map; direct create/teleport/finalize | Official InstanceScript keyed entry/leave; existing local source-settings constructor and native NPC ownership | Native entry, coordinates, door gating, two rooms; baseline characterization |
| Random opening style, three attacks per style, final alternation | Manager counters and selector | Declarative rotation/forced abilities on BossEncounter; named parameter snapshot for vent-dependent maxima/count | Native registered attacks, style and special launch ticks; existing code, not new OSRS proof |
| Simultaneous two-hit normal attacks | Manager repeats projectile/accuracy/hit calls | Native NvPCombat volley under one attack gate, exposed through existing Effect.NpcAttack parameters | Independent accuracy/damage rolls, two projectiles, one cadence, launch prayer, teardown cancellation |
| First special after three basics, later after nine | Manager total-basic/special counters | Small generic first-use threshold on existing ForcedAbility, phase-owned reset | Injected RNG, first/repeated/phase-reset cadence |
| Four HP phases, capped crossing hits, four-tick transforms | Manager phase/state deadlines and hit mutation | BossEncounter phases plus native modify/impact callbacks; named transition presentation | All threshold crossings, queued races, stats/HP preservation, exact transform ticks |
| Poison pools, vents, lightning currents and flame trails/burns | Manager LateCycle hazard lists | Focused external handlers; same native LateCycle timing with session/encounter-owned resources | Tile/impact snapshots, expiry, vent footprint, native queued-hit invalidation |
| Final native death/drops, KC, 43-tick reform | Manager manually replaces NPC and tracks deadline | Native death/drop and instance-owned spawn/respawn workflow, no duplicate respawn authority | Death queue, one KC, two kills, actor detach and fresh encounter state |
| Logout, death, teleport and leave | Multiple private hooks and membership checks | InstanceLifecycleScript and keyed resource cleanup | Native lifecycle events, no stale player/run map or effects |

Shared gaps searched locally and in the pinned official upstream combat/boss sources (including
`git grep` for volley, hitCount, multiHit, forced attack thresholds, and comparable boss usages):
NvPCombat presently admits only one projectile/hit per attack gate. Repeating Effect.NpcAttack in
the same tick fails the gate on the second hit; using a boss-local normal attack handler would
retain the duplication this task removes. Add a positive hit count to that existing native attack
configuration, default one, with independent rolls/projectiles and the existing hit scope on each
hit. This is a **proposed local shared extension**, not an official upstream multi-hit API. It owns
no new scheduler. The existing NpcAttackOverride adapter itself is a documented local extension.
ForcedAbility has only a repeating threshold, not a distinct first-use threshold; extend that same
phase-owned selector minimally rather than keep Hydra's private counter engine. Both changes are
within the user's requested generic framework repairs and receive shared regression tests first.
Other APIs remain as classified above; specialized handlers must not select ordinary attacks.

Read-only doctor: `logs/boss-dsl-migration-doctor-hydra.log`, JVM PID 41396 reports the canonical
working directory, cache revision 240, SERVER/LIVE paths and project PostgreSQL unchanged. No restart.

Hydra characterization: `hydra-characterization-before-2.log` passes 13 tests (six native + seven
rules), after correcting a fixture reference to the native pending sequence. A seventh native test
then exercised StandardNpcHitProcessor across all three threshold transitions and the final-phase
style change, bringing Hydra to 14 passing tests while the old manager is still authoritative.
`boss-volley-first-cadence-red-4.log`: exactly three shared regressions fail among 33 tests; Hydra
passes. Earlier red runs exposed fixture compilation mistakes and retained inline Mockito answers
holding full collision maps; after-test mock cleanup fixes the latter, with a 1 GiB test heap.
`boss-volley-first-cadence-green.log` passes 33 boss API, 14 Hydra, 12 Kraken, 110 Vorkath, 24 Corp
tests, combat-scripts tests and scoped checks/formatting (58s, 187 tasks). Native volley defaults
remain one hit; supplied counts take precedence. Animation, retaliation and cadence are armed
once; accuracy, damage, projectile and impact callbacks are per hit. Forced first-use thresholds
reset with the native phase identity. No live runtime verification is implied.

`hydra-dsl-characterization-after.log`: all 14 Hydra tests pass after replacing production ordinary
attack selection/scheduling with BossCombat/BossSpec (42s, 163 tasks). The old normal attack loop,
style counters and special selector are removed, not left registered beside the new one. Native
BossEncounter owns phase names/rotation; vent-dependent maxima and launch prayer are parameter
snapshots. Membership and specialized effect extraction are not yet complete.

Respawn integration gap found before modifying shared death: the executable cache check in
`hydra-native-respawn-audit.log` finds native NPC 8615 respawnRate **50**, versus the characterized
manager's **43** ticks after death completion. Native NpcRepository.add(Int.MAX_VALUE) also sets
respawns=true after the old manager set false, leaving both native respawn and manager replacement
active. Searches of local/upstream NpcDeath, NpcRepository, Npc and instance reset/respawn usages
find no per-death delay input. Add an optional delay to the existing NpcDeath/StandardNpcAccess.death
workflow; default stays the cache value, with unchanged rewards and native despawn/respawn ownership.
This is a minimal **local shared extension**, not a replacement death pipeline or a cache change.
Shared tests cover default/custom delay, ordinary deletion and cancellation; Hydra must additionally
exercise two native death/respawn cycles without actor growth. Use the existing 43-tick rule, not a
new OSRS claim. Do not retain the private reform timer or a second spawning authority.

`native-death-delay-check-5.log`: four shared death tests and 15 Hydra tests pass with scoped
checks (29s, 171 tasks). Tests execute NpcDeathScript, the native death coroutine, registry and
NpcRevealProcessor; default/custom timing, same-actor respawn, temporary deletion and cancellation
are covered. Earlier fixture iterations needed direct test dependencies, the real death plugin,
and the native lifecycle sentinel/deadline semantics. One unscoped check encountered existing
formatting violations; unrelated files were not reformatted. This is headless evidence only.

Hydra lifecycle/extraction: `hydra-native-lifecycle-tests.log` passes 18 tests (38s, 166 tasks).
The production instance now creates the single authoritative actor from InstanceArea, uses
InstanceScript entry/leave and typed session state, and relies on InstanceLifecycleScript for
teleport/logout/disconnect/death detachment. The native 43-tick respawn reuses that actor and creates
a fresh BossEncounter. The old player-to-run map, duplicate spawn/reform timer and private teardown
hooks are removed. A narrow native Respawn listener defers presentation to LateCycle so native
stat reset and BossCombat cleanup complete regardless of listener registration order.

Confirmed defects tested separately from balancing:

- Observation detection formerly masked coordinates with 63, incorrectly treating the actual
  nine-zone template's observation tile as inside the chamber. Native localCoord mapping now
  supplies the template origin; the safety test holds the player there for 12 ticks.
- `hydra-native-killcount-audit.log` fails the two-kill test with the real generic kill-count hook:
  the manager increments KC twice. Only native NpcDeath/KillcountNpcKillHook now increments it.
  The content messages read the committed native varp. No shared kill-count code was changed.
- `hydra-queued-threshold-red.log` fails on two precomputed crossing hits (825 becomes 810).
  Existing native Resolve/Impact events through BossCombat now enforce the current phase floor
  at impact and observe accepted damage; no boss-local HitImpactHandler mutation remains. The
  original threshold values and calculation-time weakness behavior are unchanged. Incoming and
  outgoing queued hits also carry the existing session/encounter validity, excluding later runs.

`hydra-extracted-handlers-check.log` passes 18 tests after extraction and native KC correction
(39s, 168 tasks). `hydra-final-native-check-2.log` passes 22 Hydra tests plus boss API, instance,
death and combat-scripts regressions/scoped check (38s, 176 tasks). The preceding iteration had one
fixture expectation at the clamped arena edge; the final test moves its flame scenario to an
unclamped interior tile and keeps the production geometry unchanged. Tests cover poison projectile
impact, pool expiry, lightning movement/lifetime, flame tracking and five two-tick burns, scoped
hazard cancellation, independent simultaneous rooms, re-entry, phase-hit races, native kill credit,
playertile death context and two respawns without NPC growth. Drops are intercepted by a native
test hook; this is not a full reward-table or live-client verification.

Retained specialized responsibilities: poison/vent/lightning/flame LateCycle geometry and hazard
snapshots; four-tick transform presentation and HP/stat preservation; observation/door presentation.
BossEncounter alone owns phase names/ordinary rotation/forced cadence; no second attack loop exists.
The transform completion intentionally remains LateCycle: moving it to a pre-input world callback
would change which same-tick queued player hits encounter invulnerability. Remaining uncertainties
are not architecture excuses or new OSRS claims: inherited non-player-damage suppression, hazard
movement geometry and timing, and the pre-existing lack of a natural exterior rock entrance must
receive separate evidence verification. This migration has not invented an entrance loc or access
requirement. The admin command and existing interior doors remain functional through native flows.

`hydra-broad-regression.log`: **BUILD SUCCESSFUL**, every non-diagnostic boss module test,
affected shared tests and server app classes (1m36s, 505 tasks). Hydra XML: 15 native tests and
seven rules tests, zero failures/errors. This is a compile/headless integration result, not live
deployment or persistence through two restarts. The live JVM, PostgreSQL and cache paths were not
changed. The command's PowerShell wrapper reported exit 1 after native JVM warning output despite
Gradle's successful result; subsequent verification uses explicit Gradle exit-code propagation.
`hydra-final-check-explicit-exit.log` confirms scoped formatting/check success with process exit 0
(24s, 168 tasks). Architecture check and `git diff --check` pass. No new secret-pattern candidates
were found in the focused source/doc changes. Doctor before the next migration again identifies
the unchanged canonical PID 41396, cache fingerprints and PostgreSQL authority.

## Grotesque Guardians mapping (before migration)

Baseline manager: 945 lines, two actors and one private PlayerUid map. The six existing rules
tests are characterization, not independent OSRS verification. Official architecture reference
remains upstream `61139e76a216592d66c2b20366ef26d48609875d` BossSpec/BossCombat/BossEncounter,
NvPCombat, InstanceScript and ScurriusInstance (links above). All existing cache IDs, research
and thresholds are preserved; no new OSRS parity claim or balance change is implied.

| Characterized responsibility | Existing ownership | Migration mapping / API classification | Required executable tests |
|---|---|---|---|
| Roof entry, bell, solo ownership, exits and replacement actors | Direct create/finalize, PlayerUid runs, private logout/death/teleport hooks | Official InstanceScript/InstanceManager plus previously tested local source-settings and session state | Natural entry registration, bell, two rooms, teardown/re-entry, two kills |
| Dawn twin ranged hits, alternating nearby melee, fourth stone ball | Private attack count, direct accuracy/projectile/hit loop | Official DSL rotation/conditions/forced abilities; existing local native NvPCombat adapter | Exact 6-tick launch rotation, footprint selection, independent rolls, one-tick component impact separation |
| Dusk phase-two alternating melee/explosion; final prison and melee/range | Private counters/deadlines and selector | BossEncounter selector and declarative phase abilities; named arena handlers only for specials | Phase-specific cadence, delayed readiness, prison timing, animation ordering |
| Dusk final fixed first ranged component and nested-uniform second component | Boss-local damage rolls | Native damage calculation configured through a minimal generic per-strike parameter extension if needed | Accurate/missed and launch-prayer combinations, exact RNG calls and staggered impact |
| Alternating vulnerable guardian, HP floors, hammer finish and transforms | Manager phase engine and pre-resolution transitions | BossEncounter phase identity; native Resolve/Impact callbacks; focused paired-form presentation | Threshold races, each form, 12-tick lightning and five-tick death presentation |
| Stone ball/debris, lightning tiles, energy spheres and prison | World queues with local generation plus LateCycle lists | Named external handlers; native scoped world queue, BossEncounter/session-owned resources, existing loc APIs | Tile snapshots, expiry, sphere heal/absorb, prison heal, queued-hit and callback cancellation |
| Dusk native drops and both-actor reform after 17 ticks | Native death plus manual delete/recreate/reset deadline | Existing NpcDeath cadence and instance-owned actor reset/respawn; no private spawn authority | Native death/drop context, KC once, fresh encounter identities and no entity-list growth |

Pre-edit searches: full local native attack override/roll implementation, boss Effect and
BossEncounter selectors, instance actor spawn/reset APIs and comparable Scurrius/Corp/Hydra wiring;
`git grep` on upstream for hitCount, damageTransform, inaccurateMaxHit and projectileFactory finds
no equivalent per-strike adapter. Local shared volley support permits homogeneous simultaneous
hits only. Repeating ordinary NpcAttack effects loses the second attack to the native cadence gate.
If characterization confirms the draft's staggered heterogeneous components, extend that same
native volley with a non-empty per-strike parameter list (impact offset, modifier, pure damage-roll
policy). Keep animation/cadence/retaliation once, accuracy/projectile/hit per component and native
cancellation between components. This is a proposed **local shared extension**, not an upstream API
or a replacement scheduler. Add shared regressions before implementation. Do not silently change
the draft's RNG draw order or give the content a second normal attack loop.

Paired phase coordination and arena geometry remain legitimate specialized handlers, not permission
to preserve a general manager. The initial mapping may be refined by executable characterization;
any additional shared capability must be listed here before implementation. Inherited damage-source
exclusions and uncertain collision/OSRS values remain explicit evidence gaps, not approved rebalances.

`guardians-characterization-before-2.log`: 12 tests pass (six native + six rules), scoped check
32s/152 tasks. The first fixture iteration needed named ProjAnimType fields and client-frame sequence
cleanup between ticks; native animation priority must not retain an already-emitted spawn sequence
in every later test frame. No production animation or projectile constants were changed. The tests
confirm native registration, two-tick bell readiness, six-tick attacks, two projectiles with hit
offsets 0/1, 80% launch-prayer reduction, fourth Dawn stone ball, third Dusk explosion, all paired
forms/HP gates, 12-tick lightning, five-tick hammer death, and the final ranged components' exact
RNG usage (no roll for the fixed accurate first component; nested two rolls for the second; one
zero-range draw for the missed second component). This is preserved draft behavior, not packet proof.

Additional selector mapping before shared changes: Dusk's final style alternation counts committed
abilities including prison, while the native rotation cursor intentionally excludes forced abilities.
Local/upstream selector/condition searches find no phase-local committed-ability ordinal. Expose that
count on BossEncounter, reset it on transition, and add a generic modulo condition for declarative
effect selection. This avoids retaining Dusk's private normal attack counter. Tests cover forced and
ordinary commits, phase reset and invalid modulo inputs. Also verify that explicitly forced abilities
reset their existing periodic cadence when committed: currently only selection updates the periodic
timestamp, so forceNextAbility may be followed immediately by the same time-based ability after a
late start. Correct that generic bug in recordAbilityCommit, with regression tests. First prison can
then use the existing one-shot forceNextAbility API; no separate prison scheduler is needed.

`guardians-shared-components-red.log`: 38 API tests, exactly the five new regressions fail
(42s, 155 tasks). `guardians-shared-components-green.log`: shared API/scoped checks and the
Guardians/Hydra/Kraken/Vorkath/Corp regression tests pass (55s, 193 tasks). Native hit components
now support pure damage distributions, independent modifiers and impact offsets without a second
attack gate or scheduler. Lists are snapshotted per volley; native validity is rechecked between
components, including after a synchronous first-hit callback retires the encounter. Explicit forced
commits rearm existing periodic cadence; phase-local committed ability counts include those commits
and reset on phase changes. All are local extensions/repairs as mapped, not new upstream claims.

Native action-lock adjunct identified before Guardians wiring: BossCombat gates its own selector
after a special but does not synchronize the native NPC actionDelay unless the ability happens
to invoke NvPCombat. The old Guardians manager sets that lock after every special. Use the existing
BossCombat commit path to extend (never shorten) the native action lock to the declarative cadence
after interpreting the ability, while the encounter is still current. Do not arm it before
NvPCombat, which would reject its own attack. A registered no-op/special ability regression and
existing normal-attack tests must prove this reusable correction; no boss-specific action timer.

`boss-native-special-lock-red.log` fails the new registered-ability test as expected.
`boss-native-special-lock-green.log` passes 39 API tests and Hydra/Kraken/Vorkath/Corp regressions
with scoped API check (47s, 184 tasks). The shared commit extends the native lock only after
interpretation and only while that encounter is current; it does not introduce a second timer.

`guardians-dsl-characterization-after.log`: the same 12 Guardians tests pass through production
BossCombat/BossSpec registration (44s, 166 tasks). Separate Dawn/Dusk specs own ordinary attacks,
paired phase identities, ordinal-based styles and forced stone/explosion/prison cadence. Parameter
handlers only snapshot prayer and supply the already-characterized final damage distributions.
The old normal attack functions/counters and prison period counter are removed. Private membership,
special effect extraction and paired respawn integration remain in progress; this is not the final
Guardians architecture acceptance or a live verification claim.

Paired reset mapping before the next shared change: the draft leaves Dawn's corpse visible until
17 ticks after Dusk's native death completes, then replaces both actors. A hidden NPC cannot resume
its coroutine (`NpcMainProcess` uses `Npc.canProcess`, which requires visibility), so placing a
17-tick coroutine delay after Dusk's despawn would stall. Individual native respawn would also reveal
an extra actor before the pair replacement. Local and upstream searches for `resetNpcs`,
`resetServerOwned` and respawn APIs show an existing private InstanceManager actor-reset pipeline
(upstream 61139e7, InstanceManager.kt:318), but no callable reset for an occupied private session.
Expose a validated overload of that same pipeline, not new spawning logic. It must reject stale,
ended, expiring or invalid-region sessions, delete/detach all old actors, restore configured actors,
and preserve occupants and session-owned content. Test repeated reset, helper cleanup, identity
validation and two-instance isolation. This is a local shared adapter approved by this migration
task, not an official upstream API. Guardians' specialized pair reset will use Dawn's still-live
BossEncounter-scoped native world queue; instance teardown cancels it. Dusk's individual respawn
will be suppressed through the existing native death delay parameter. No new lifecycle scheduler.

`guardians-instance-reset-red.log`: 34 instance tests, two expected failures from the new actor
reset scenarios. `guardians-instance-reset-green.log`: all 34 pass and scoped check succeeds
(39s, 148 tasks, process exit 0). The overload delegates to the pre-existing reset implementation;
no new allocation, NPC construction, ownership map or session lifetime was introduced.

Guardians native-instance wiring now executes all original 12 characterization cases. The added
13th test fails as expected (`guardians-threshold-race-red.log`, 36s): calculating the first queued
threshold-crossing hit immediately transforms Dawn before accepted damage. Correct this through
the already-mapped native Resolve/Impact callbacks, preserving 247/9 HP floors and all form delays.
This is a demonstrated pipeline timing defect, not an invented OSRS rebalance. Initial instance
test compilation required correcting fixture imports; no production mechanic changed for that.

The first impact-time test overconstrained Dusk: the inherited `canDamage` explicitly allows damage
during LIGHTNING_TRANSITION. Keep that behavior, rather than introducing unresearched immunity.
The phase-two crossing stops at 247; a second precomputed 203 hit may then land during lightning,
leaving 44. Dawn's defensive phase rejects the second hit. The regression now distinguishes those
two existing policies. Lightning vulnerability remains an OSRS evidence gap, not a parity claim.

Further native death coverage exposes a **pre-existing reward integration defect**: both
DropTableRegistry and KillcountNpcKillHook use the immutable spawn definition. Dusk is spawned as
7851 then visually transformed; the reward table registers only 7888 and the active cache's KC
parameter is supplied on death definitions instead of 7851. The native kill hook executes once per
completion, but the KC remains zero. The characterization explicitly records zero as a known defect,
not an intended rule or passing-KC certification. Correct the table's base-identity registration
through the existing table system. The KC definition correction needs a source-definition and
non-live cache validation pass; do not mutate the running cache or install an ad hoc boss counter.
Guardians is not fully accepted until that integration is resolved. No source/cache paths changed.

Reward correction mapping: register the existing unchanged table for Dusk's native spawn identity
as well as the supported final-form spawn. Add only the missing `killcount_varp` source entry for
7851 to the existing intentional `.data/raw-cache/server/npcs.toml` configuration. Its unrelated
Royal Titans/GE hunks were inspected and must remain unstaged. Verify the overlay through the
existing PackServerConfig TOML parser and native kill hook with an in-memory parameter merge,
restoring the cached test definition afterward. This does not repack or deploy SERVER/LIVE.
The already-running JVM will require an explicitly authorized cache rebuild/restart later. No new
shared reward API, boss-local counter, table probabilities, quantities or prayer behavior changes.

### Guardians source migration result

`guardians-native-rewards-green.log`: 20 Guardians tests and four focused drop-table tests pass
(53s, 199 tasks). The source metadata test first failed without the 7851 entry, then passed by
executing the native kill hook with the parsed source overlay in memory. The registry regression
resolves the unchanged reward table from a 7851 actor visually transformed to 7888; Dawn receives
no table. No production cache was repacked. Only the seven-line intentional source configuration
hunk was staged; all pre-existing Royal Titans/GE data changes remain unstaged and preserved.

`guardians-final-module-check-2.log`: 21 Guardians tests (15 native, six rules) and the complete
48-test drops module pass; both scoped module checks succeed (37s, 199 tasks, process exit 0).
Added actor-deletion coverage proves native encounter teardown cancels scheduled spatial work,
clears temporary locations and prevents either actor attacking with a stale paired encounter.
Other new cases execute roof entry/exit, two isolated rooms, external teleport, logout/disconnect,
native death membership handling, queued-hit invalidation, sphere absorption/return, prison escape
and two paired resets. Reset occurs once, 17 ticks after native Dusk death completion; duplicate
completion callbacks cannot enqueue another reset. The two-kill test accommodates the unchanged
active cache and a future authorized cache rebuild; the separate source-overlay test always
requires exactly one native KC increment.

The manager no longer creates/deletes actors, tracks player-to-run membership, selects ordinary
attacks or owns respawn timers. Retained paired coordination and arena effects use native instance
and BossEncounter identities. Four obsolete architecture waivers were removed; seven reviewed
spatial-hit/native-parameter sites remain. Architecture detector and `git diff --check` pass.
Doctor after the changes still identifies the canonical live workspace and unchanged revision-240
cache authority. Runtime deployment, cache packing, two restarts, client visuals and the complete
grave pipeline remain **not performed**, not implied by these headless tests.

`guardians-broad-regression.log`: all applicable boss-module tests, drops, bosses/instances/NPC/death/
game-process/combat-scripts/engine-game tests and server app classes pass (1m44s, 507 tasks,
process exit 0). Modules reporting NO-SOURCE do not provide encounter gameplay coverage.

## Architecture check build integration

The existing `scripts/check-boss-architecture.ps1` already owns the detector and reviewed waiver
budgets; local root/build-logic searches find no Gradle invocation. Wire its existing self-test and
repository check into root `check` using Gradle Exec tasks. This is build integration, not a new
combat API/detector. Use Windows PowerShell on Windows and `pwsh` elsewhere; absence of that explicit
prerequisite must fail visibly, not silently skip enforcement. No task writes source, cache or data.

`boss-architecture-gradle-check.log`: both detector self-tests and repository enforcement pass
through Gradle (24s, 11 tasks, exit 0). Root `check` now depends on that same task.

## Zulrah mapping (before migration)

Baseline: 993-line manager, PlayerUid run map, private action/transition deadlines, polled pending
projectile/tail lists and manual NPC replacement; 22 existing module tests are rules/rotation/storage
tests, not native combat execution. Full source, world/combat/lifecycle/storage, configuration and
rotation programs inspected. Repository searches find only this encounter, its existing Kotlin
drop table, Collection Log KC mapping and item charging references; no second boss implementation.

Architecture reference remains upstream `61139e76a216592d66c2b20366ef26d48609875d`:
Scurrius.kt:104 registers BossCombat and ScurriusInstance.kt:23 extends InstanceScript. Upstream
api/bosses runtime/spec/DSL and api/instances are the native owners. Prior Kraken/Hydra/Guardians
migrations are local adapter examples, not official provenance.

| Mechanic | Evidence / confidence | Existing implementation | Native mapping and planned implementation | Executable coverage |
|---|---|---|---|---|
| Four rotation programs, surface locations/facing and action counts | Existing ZulrahRotation and three preserved ledgers; opening subset from rev234.1 capture 820, otherwise donor/Wiki claims, not full normal-game proof | Private actionIndex/actionProgress/tickPhase | Official Selector.Rotation and BossEncounter phase identity; derive specs from unchanged rotation data, flatten counted attacks, named spatial handlers | Registered combat through complete programs, exact opening/dive/emerge timeline and Jad ordering |
| Standard and Jad attacks, 2:1 tanzanite styles, max41, cadence3, launch prayer and venom | Existing code/rules; lone captured ranged launch, unobserved magic/protected branches | Manager NvPCombat override and manually launched ordinary projectile | Official native NvPCombat via local Effect.NpcAttack adapter; DSL weighted choice and rotation; pure launch parameters for prayer/venom/sounds and captured controller | Native projectile fields, hits, cadence, prayer snapshots, accurate zero venom, misses |
| Initial11/emerge3/dive3/phase-end4, tail4+3 | Existing capture ledger 10362..10399 and configuration; League context retained | Manager transitionCycle/nextActionCycle | Existing DSL delays/cadence and scoped external form positioning; no private general scheduler | Exact relative tick traces and teardown during delayed transitions |
| Cloud pairs, origin/centre, 3x3 damage1..4,30tick life; egg landing | Capture820 opening projectile/loc subset and retained configuration | Private pendingHazards and cloud lists | Named spatial handler, native projectile calculations and BossEncounter-owned world queues; content state only for active footprints | Two delayed impacts, overlap, expiry, cancellation, second-room isolation |
| Tail target snapshot, pillar safe tiles, damage20..30/stun/venom | Donor/Wiki claims; no captured hit branch | Private pendingTails | Named spatial handler with scoped native queue/hit validity, unchanged geometry | Dodge/safe/hit, delayed cancellation |
| Snakeling stats/type selection, lock3/rate4/lifetime67 | Existing cache/config; not seen in capture820 | Manual construction plus ordinary attack function | Native attached summon ownership, own BossSpec/NvPCombat; scoped lifetime handling | Both styles, one HP, launch lock, lifetime/death/instance cleanup |
| Damage reroll >50 to45..50; transforms preserve UID | Existing rules/cache, no authoritative player-hit trace | Modify callback; native transmog/teleport | Existing BossCombat hit callbacks and native movement/transmog; preserve current source exclusion explicitly as unresolved | Native hit modification and queued damage across dives |
| Solo boat entry/exit/camera/debug, rotated template and no Regicide enforcement | Existing interactions/template; some entry packets in820 | Manager create/finalize/telejump plus duplicated cleanup | Official InstanceScript/keyed native lifecycle with existing local source/typed-state/protected-entry adapters; unchanged template/camera | Actual object interaction, failed entry, exit/logout/teleport/death, two rooms |
| Death,10second/17tick repeat, drops,KC,PB and debug no-reward | Existing native death/table/storage; completion claims need characterization | deathNoDrops then spawnDrops; localKC; private replacement | Existing native death/drop/KC pipeline and native respawn, same source data; preserve debug reward suppression | Native completion, duplicate callbacks, two kills and no stale actors |
| Reclaim triples/fee/reminder/persistence | Existing ZulrahDeathStorage/codec and persistent AttributeKey; no new persistence research | Native death storage hook and invTransaction; bad null-active check suspected | Preserve native storage/inventory workflow; remove duplicated lifecycle cleanup, fix proven outside-room activation | Outside encounter, native death membership and preserved storage tests |

All new native-fixture evidence is characterization, not independent OSRS proof. Existing capture
820 is Grid Master (rev234.1, SHA256 40A571CD18DBB50965FCBF3363FC5D4050A45ABD0AC9036753808F9B659915EB),
not normal combat timing authority. Preserve its provenance and previously unobserved branches.
The old audit's no-sounds claim is stale; current code sends mapped sounds. First-tail naming/order
also conflicts between code and capture ledger and must stay documented, not silently rebalanced.

Native source search will precede any additional generic capability. In particular deterministic
debug rotation seeding must not introduce a second selector or change production GameRandom behavior.
The already available scoped queues, native respawn, paired reset and attack parameter adapters are
**local shared extensions**, not official upstream APIs. No new shared change is proposed here yet.

`zulrah-native-characterization-before-4.log`: 29 tests pass (seven new native cases, 22 retained
rules/rotation/storage cases), scoped check 31s/164 tasks. Fixture corrections were a missing
constructor argument and an over-specific projectile expectation: the south-phase native controller
ends at client cycle65 (hit delay3), not the captured middle-position cycle90. Production geometry
and controller values were unchanged. Native tests preserve opening eleven-tick delay, cloud launches
at111/114/117/120 and impacts +3/+4, dive124, emerge127, tail sequence5806 then5807 seven ticks apart,
ordinary prayer/accuracy/native cadence, seeded blue selection, Jad order, capped damage and UID.
The first-tail order is baseline behavior with a ledger conflict, not a newly verified OSRS rule.

Two generic integration gaps are now demonstrated before implementation:

1. The native draft's already-fired projectile remains valid after source death. Capture820's
   launch11578/death11579/impact11581 supports that lifetime (without claiming its altered damage
   is normal). The DSL currently invalidates every attack when source HP reaches zero. Local and
   upstream searches across BossEncounter/EncounterRegistry/Effect/NvPCombat/Hit find native scoped
   validity but no declarative source-death policy. Add an opt-in `survivesSourceDeath` parameter
   on Effect.NpcAttack; keep default cancellation, live-only launch, player/owner/instance validity
   and hard cancellation on deletion/respawn/encounter end. Reuse the existing hit scope; no new queue.
2. Existing no-reward debug seeding uses Kotlin Random(seed) for rotation/style/spatial choices.
   Official DefaultGameRandom already wraps the same seeded generator; use it instead. Local and
   upstream registry searches find no per-encounter selection RNG input. Allow the existing registry
   creation path to accept an injected selection GameRandom, used only on first creation. Existing
   encounters must not be reseeded by later lookup; production defaults remain unchanged. This avoids
   retaining a private debug selector beside the DSL. Tests cover isolation, reset and identity.

These are the smallest local shared extensions under the user's framework-repair authorization,
not official upstream APIs. Add failing API regressions before enabling either capability.

`zulrah-shared-lifetime-seed-red.log`: exactly the two new API regressions fail (41 tests).
`zulrah-outside-death-red.log`: exactly the new outside-shrine activation test fails (30 tests).
`zulrah-shared-lifetime-seed-green-2.log`: all 41 API and 30 Zulrah tests pass, scoped API check
38s/168 tasks, exit0. The initial green run exposed a fixture RNG exhaustion (one scripted draw
was supplied for two selections); adding the second explicit draw corrected the test, not behavior.
The opt-in lifetime still refuses dead-source launches and invalidates on departure and native
teardown; subsequent registry lookup does not reseed the existing encounter. Outside-shrine death
storage activation is now false. Earlier five migrated boss regression tasks also passed in the
first green run; that invocation overall failed only on the RNG fixture and was not deployed.

The user-requested resumable handoff is `MIGRATION_HANDOFF.md`; `scripts/resume-boss-migration.ps1`
prints it with current Git identity. Its default read-only execution passed
(`logs/boss-migration-resume-script-check.log`). Optional verification uses existing doctor/check/test
workflows; it never deploys, restarts, switches branches or commits. The account allowance cutoff is
not observable through the tools available in this session; the user must report it. Official
[usage guidance](https://learn.chatgpt.com/docs/pricing) points to the usage display and `/status`,
not an agent-side subscription balance tool. No account credentials were read.

The complete-program native characterization found two draft scheduler defects
(`zulrah-all-rotations-before-2.log`, 31 tests / two failures). Cooldown-blocked blue attacks consume
selection RNG before NvPCombat rejects the launch. Rotation 4 also launches its first standard shot
on the final egg-volley tick, overwriting that frame's presentation and advancing phase 2 and phase 4
three ticks early each. The existing declared three-tick action cadence is bypassed because `attack`
does not check `nextActionCycle`. Correct both readiness gates before replacing the manager's selector;
the DSL already owns these gates. This is a demonstrated implementation-vs-declared-program defect,
not new normal-OSRS capture proof. Preserve counts, weights, IDs and all declared rotation timings.

`zulrah-all-rotations-gates-green.log`: 31 tests pass, 38s/166 tasks after readiness correction.
`zulrah-native-dsl-first.log`: the same 31 tests pass, 40s/166 tasks through BossCombat registration.
ZulrahSpec derives all four action programs from the unchanged rotation data; Selector.Rotation,
weighted blue choice and exitAfter/nextPhase now own ordinary sequencing. Pure ZulrahAttackParameters
preserve native projectile geometry, launch prayer, venom and sounds. Snakeling attacks use their own
registered BossSpecs. Removed the manager's ordinary attack functions and action/phase tick engine;
this is an intermediate strangler checkpoint, not a completed instance/hazard migration. Spatial
pending work and private membership/respawn remain for the next focused change. Nothing deployed.

### Zulrah source migration result

The instance/hazard migration now replaces that intermediate checkpoint. ZulrahWorld extends the
existing InstanceScript; native configured spawns, keyed joins, typed session state and native
departure/death cleanup replace the private PlayerUid map. Native deathWithDrops and 17-tick respawn
reuse the same actor. BossEncounter owns delayed spatial effects; snakelings have their own
encounter-owned expiry and detach callbacks. Boss death cancels spatial work using the existing
runCleanupActions API without invalidating an already-fired ordinary projectile; destruction,
respawn and instance departure still invalidate it. No additional shared API was needed in this step.
The remaining 300-line coordinator handles surface presentation and completion data; the 307-line
arena handler contains only cloud, egg and tail geometry/effects. Ordinary sequencing is not duplicated.

Native KC inspection found the same spawn-definition issue as Guardians: Zulrah's immutable 2042
definition lacks killcount_varp in the active cache. Add only the eight-line intentional source
overlay in `.data/raw-cache/server/npcs.toml`, and remove the boss-local counter. The regression
parses that source overlay and executes the native kill hook in memory, restoring the test
definition afterward. SERVER/LIVE are unchanged; an authorized deployment must pack that metadata.
All pre-existing Royal Titans/GE data hunks remain unstaged. No reward rates or quantities changed.

`zulrah-final-native-check.log`: 43 tests pass (21 native and 22 retained); the revision-240 cache
audit also passes. Scoped Zulrah check succeeds (42s, 170 tasks, exit0). Native cases cover all four
complete programs, actual protected boat interaction/camera completion, two isolated rooms,
teleport/logout/disconnect/death/exit, two same-actor kills, one native KC with source metadata,
debug reward suppression, queued-hit lifetime, tail hit/dodge/pillars, cloud overlap and
source-death cancellation, snakeling attack lock and deletion/expiry cancellation. The suspected
protected-boat rejection was not reproduced; it is not recorded as a corrected defect.

`zulrah-broad-regression.log`: all applicable boss tests, drops, bosses/instances/NPC/death/
game-process/combat-scripts/engine-game tests, server app classes and bossArchitectureCheck pass
(49s, 509 tasks). The boss-module XML reports contain 893 tests, zero failures/errors/skips;
NO-SOURCE modules do not constitute gameplay coverage. Architecture waivers now retain only the
reviewed spatial-hit/parameter sites and persistent reclaim reminder, not private combat/membership.
Doctor still identifies canonical PID41396 and revision240 authority. No live deployment,
cache packing, controlled restart, client scenario or new packet comparison was performed.

## Muspah mapping (before migration)

Branch starts at Zulrah checkpoint `2aecefe5f`. Current Muspah is a 1,088-line manager with
PlayerUid membership, phase/respawn deadlines, ordinary attack selectors and manual actor creation.
Its 40 rules tests do not execute native combat. All module files, NPC source overlays, drop-table
registration, Collection Log KC reference and Git history inspected. Repository/upstream searches
find one local encounter; upstream contains a Muspah drop table but no encounter implementation.
No Muspah capture ledger was found under docs. Preserve current numeric behavior as characterization,
not normal-game evidence. No quest requirement, reward rebalance or new scoreboard is added here.

Official architecture references at `61139e76a216592d66c2b20366ef26d48609875d`:
[ScurriusInstance](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/content/bosses/scurrius/src/main/kotlin/org/rsmod/content/bosses/scurrius/ScurriusInstance.kt),
[BossCombat](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/bosses/src/main/kotlin/org/rsmod/api/bosses/runtime/BossCombat.kt),
[InstanceManager](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/instances/src/main/kotlin/org/rsmod/api/instances/InstanceManager.kt).
BossSpec/BossEncounter/InstanceScript/native repositories and death pipeline are upstream workflows.
Effect.NpcAttack, parameter adapters, scoped hit/task lifetimes, source instance settings and typed
session state are existing **local shared extensions** documented above, not official provenance.

| Mechanic / retained behavior | Evidence confidence | Existing pathway | Planned established workflow / tests |
|---|---|---|---|
| Random ranged/melee opening, 850HP, four-tick spawn, fixed room/template/facing | Draft/config and rev240 definitions; no packet proof | Manager creates NPC and private ready timer | Configured instance actor, native spawn/respawn and DSL phase entry; native both-form entry and readiness tests |
| Ranged 1/5 magic, reinforced 1/3; maxima61/72/79; rates6/5; magic impact3 | Draft only | Private RNG before NvPCombat readiness gate | DSL WeightedRandom/phase cadence plus native attack parameters; launch/deadline/prayer/blocked-selection tests |
| Melee step-back and stationary maxima, 50% prayer reduction | Draft/rules; unverified OSRS geometry | Private pre-launch counter | Native melee attack with pure launch snapshot and committed counter; movement/prayer/cooldown tests |
| First swaps100/80damage; later also4positive ticks; four-tick transform | Draft/rules; crossing currently evaluated during Modify | Manager counters/deadlines | BossEncounter phase identity and named form policy; native resolve/impact characterization before changing crossing semantics; multi-hit and queued-race tests |
| Specials637/425, order from initial form | Draft only | Manager flags and state switch | DSL phase identity/transition policy, unchanged priority; executable both orders and boundary tests |
| Lightning12teleports/+2 then3ticks, moving clouds; homing34ticks and orthogonal pursuit | Draft-only spatial values | Post-tick private phase loop and hazard lists | Focused arena handler, existing movement/collision/loc APIs, encounter-owned scheduling; route/expiry/cleanup tests |
| Below127 shockwave+4/shield+5,75shield; prayer/bolt/mace drain, passive2, soul-split half | Draft/rules; lethal skip and damage attribution unresolved | Pre-resolution hit mutation and callbacks | Existing BossCombat damage callbacks/native NvP hit handlers, phase-owned counters; threshold/shield/late-hit tests; do not invent new drain pipeline |
| Final spike every4attacks, two-tick crack and75%spike healing | Draft only | Ordinary onAttack manually counts and loc onDespawn callback | DSL forced cadence plus focused geometry using scoped native effects; exact crack/cadence/teardown tests |
| Solo natural crevice, exits/teleport/logout/death | Existing module and cache locs46597..46599 | Direct telejump/create/finalize and custom cleanup | InstanceScript/native keyed lifecycle and session state; natural entry, isolation and departure tests |
| Native drops, KC/PB,50tick repeat and charged-ice timer attribute | Existing table/persistence keys; no new rates research | Native death plus local KC and manually replaced actor | Native kill hook/source metadata and same-actor respawn; verify source identities, exactly one completion, preserved reward attribute and two kills |

Before removing any manager engine, add native characterization through existing registration,
hit and instance APIs. Suspected issues (cooldown-consuming RNG/counters, unscoped delayed hits,
stale crack callbacks and duplicate respawn ownership) need failing tests, not assumptions. Preserve
the current non-player damage exclusion as an explicit mechanical gap, not as OSRS-certified behavior.
No additional shared capability is proposed in this mapping; explain any newly proven gap first.

The native baseline found an actual launch exception: `projanim.magic` is unmapped. After fixing
fixture sequence-reset/prayer assertions, `muspah-native-characterization-before-3.log` has 48 tests
and exactly four failures, all that missing controller. Use the existing `projanim.magic_spell`
controller, defined in upstream `.data/raw-cache/server/projectiles.toml:69` and the current mapping
as ID6. This repairs the invalid generic controller reference; it does not certify its heights/travel
as Muspah-specific OSRS values. Validate the selected controller during asset validation. Existing
lightning/homing/form/exit native characterizations pass; no new spatial behavior is inferred.

`muspah-native-controller-green.log`: all 48 tests pass (eight native, 40 retained), scoped check
36s/164 tasks. The native spell controller resolves and ordinary attacks now launch. No engine or
cache edits were required. This is a corrected native baseline, not the completed DSL migration.

`muspah-native-readiness-red.log`: exactly two new regressions fail (50 tests total). Five blocked
ranged attempts consume five unnecessary style draws. Blocked melee attempts increase the
stationary counter, making the second committed stationary hit max34 instead of the declared20.
Gate rejected attempts before private selection/counter mutation, then replace that gate with
BossCombat's established readiness/selection pipeline. The intended per-attack formula is retained;
this fixes execution-vs-declared-rule behavior, not a newly researched OSRS maximum.

`muspah-native-readiness-green.log`: all 50 tests pass, scoped check37s/164tasks. Readiness is now
checked before legacy selector/counter mutation; the next step transfers that responsibility to
BossCombat. Existing phases, source data and rewards are unchanged.

`muspah-native-projectile-scope-red.log`: the new departure test fails (51 tests, one failure).
An ordinary queued shot remains valid after escape/deletion. Use the existing BossEncounter hit
scope and native player/session validity through Effect.NpcAttack; no new timer or shared hook.
The first strangler step replaces ordinary selectors/launch functions only. Remaining manager
phase, membership, hazard and respawn ownership is still migration debt, not an accepted end state.

`muspah-native-dsl-first.log`: all 51 tests pass through BossCombat/Effect.NpcAttack (44s/166tasks).
MuspahSpec owns ordinary form-dependent weights, maxima, animations and cadence. Parameter handlers
only snapshot native prayer/projectile/melee policies and invoke existing impact effects; they do
not launch or schedule normal attacks. Native Op2 and Ap2 now share the same cooldown and encounter.
Phase/spatial/membership migration still remains; no second ordinary attack selector is retained.

Additional review found an integration-order defect before accepting this partial checkpoint:
creating the default dormant BossEncounter on the first incoming hit re-locks active melee movement
before Muspah reaches the player. `muspah-native-initial-ownership-red.log` proves this (54tests,
one failure). Establish the native encounter when its actor is spawned, before activation unlocks
movement. No shared override is needed. `muspah-native-dsl-final.log` passes all54tests (14native,
40retained), scoped check37s/166tasks. The added tests also execute every1/5 and1/3 style boundary
and both native interaction registrations. The earlier combined API/instance/drops/app-class and
architecture invocation passed (`muspah-native-dsl-check.log`,40s/460tasks).

Remaining Muspah work: replace the private membership/phase/respawn engine with InstanceScript,
typed session state and native BossEncounter transitions; migrate final forced cadence and spatial
queues; characterize threshold races, native rewards/KC, two kills, real collision and all native
departure paths. Existing unscoped hazard callbacks and inherited non-player exclusion are not
fixed by ordinary projectile scoping. This partial checkpoint must not be reported as a completed
boss migration or OSRS parity. No server/cache restart or client verification has occurred.

`muspah-broad-regression.log`: all applicable boss/shared/drop tests, app classes and architecture
enforcement pass (24s/509tasks; unchanged tests reused Gradle's passing up-to-date results).
The resume helper's default read-only mode passes with Muspah as its current default
(`boss-migration-resume-muspah-check.log`). Doctor identifies canonical PID41396, revision240 and
unchanged SERVER/LIVE/PostgreSQL authority. `git diff --check` passes; unrelated source-data edits
remain outside the focused commit. Continue with the remaining Muspah mapping above.

### Muspah native lifecycle checkpoint

`muspah-native-lifecycle-red.log`: 56 tests, three failures. Native exit/logout/disconnect/death
left private membership behind; native death plus the private respawn timer created two actors.
Exercising the native location duration processor also exposed a concurrent-modification exception
when the old warning-expiry callback added its replacement during duration-list iteration.

InstanceScript now owns natural crevice entry and both exits, keyed join/leave requests and protected
teleporting. A configured native actor and typed session state replace the private PlayerUid map,
generation counter, manual actor creation/deletion and respawn deadline. Native death/50-tick reveal
reuses the same actor; visual starting-form selection copies that form's native stats each round.
Native lifecycle callbacks retire the session; its cleanup cancels hazards and encounter queues.
No new shared API or engine edit was required.

Warning replacement now uses the existing encounter-owned native queue at +2; it removes its warning
record on formation. Cloud/shockwave/spike hits carry native environment provenance and a hazard
scope invalidated at death/reset/exit. This avoids Muspah's re-entrant location callback; it does not
claim the shared LocRepository duration-list re-entrancy defect is generally repaired.

`muspah-native-lifecycle-first.log`: all56 tests pass,42s/165tasks.
`muspah-native-lifecycle-regression.log`: all58 tests pass,35s/165tasks, including exact crack tick,
queued-hit retirement, warning cancellation on death, same-actor repeat, round-field reset and
fresh-session re-entry. These are native headless tests, not client/packet verification.
Phase timers and final forced cadence remain the next migration step; KC metadata still needs its
native integration test. Doctor log `muspah-lifecycle-doctor.log` confirms PID41396/canonical root,
revision240 and unchanged cache/database authority. No deployment or restart has occurred.

`muspah-native-kc-red.log` has59 tests/one failure: packing native KC metadata while retaining the
local increment produces two kills for one completion. The configured base actor now declares
`param.killcount_varp = varp.total_muspah_kills` and suppresses the generic notification so the
existing duration/PB message remains. Removed the local KC increment; native death/drop/kill hooks
own it. `muspah-native-kc-green.log`: all59 pass,41s/167tasks, including two consecutive completions
and repeated completion notification without another increment. The three-line source overlay was
selectively staged; unrelated Royal Titans/GE changes in the same file were excluded. SERVER/LIVE
are not repacked; the overlay must accompany the authorized deployment.

`muspah-native-final-cadence-before.log` and `muspah-native-final-cadence-dsl.log` pass60 tests.
Final shots now declare `AbilityCountModulo(4,0)` after their native attack effect, invoking a
named spike handler. Removed the private attack counter without changing the eight-shot regression's
shots, deadlines or blocked-attempt behavior. The draft's passive-shield-break expansion is retained,
not certified as OSRS. `muspah-native-spatial-split.log` passes60 tests,41s/167tasks after separating
spike/cloud/LOS resource operations into MuspahArenaEffects. Phase ownership is not yet finished.

### Additional shared phase-clock mapping

Inspection of upstream/local BossCombat and native NpcInteractionProcessor shows timed
`exitAfter/nextPhase` progression is only checked on an attack interaction. A moving melee target
out of reach, or a locked special outside approach range, can prevent a declarative timed phase
from ending. Increasing the attack radius to drive timers would alter attack reach and movement;
retaining the private per-player phase timer would duplicate the framework.

Within the approved generic DSL timing/phase repairs, add the smallest opt-in binding to the
existing BossEncounter and WorldQueueList: arm the declared phase exit on native transition,
cancel/rearm on another transition, and execute its existing entry effect through
StandardNpcAccessLauncher/EffectInterpreter when the NPC is available. Ordinary selection, reach,
movement and combat readiness remain in BossCombat. Source/target lifetime must invalidate this
binding. This is a **new local shared capability**, not an official upstream API or a second
framework. Existing bosses are unchanged unless they opt in.

Required API regressions: timed entry without attack events, exact deadline, no duplicate entry
alongside native attack processing, phase replacement cancels old deadline, busy-NPC safety,
source/target retirement, and native queue cleanup. Muspah must then remove its corresponding
private deadlines and use this declarative clock before its phase migration is considered complete.

`boss-phase-clock-red-2.log`: 42 tests, one failure; without an attack callback the declared
four-tick phase never exits. (`boss-phase-clock-red.log` was only a missing format-manifest-file
setup failure.) `boss-phase-clock-regression.log`: all46 API tests pass,37s/158tasks. The opt-in
clock now advances declared phases through the native queue/access interpreter, cancels replaced
deadlines, waits for native busy access, and retires source/target-invalid work. Ordinary attack
callbacks cannot execute the same timed entry twice. Positive durations are required to avoid a
zero-tick phase cycle. Existing non-opted-in bosses retain their current transition behavior.

### Muspah completed source phase migration

`muspah-native-phase-dsl-first.log`: the existing60 tests pass through declared spawn/swap,
38-tick lightning,34-tick homing and4+1-tick shockwave/shield phases (42s/167tasks).
MuspahArenaState derives phase identity from BossEncounter; removed its private phase deadlines,
ordinary-attack counter, membership map, respawn timer and generation. InstanceScript owns room
entry/exit, native lifecycle owns departures, and native death/reveal owns the same actor across kills.
Named external entry handlers retain presentation and specialized shield/form policy; the spatial
handler owns only arena geometry/resource effects using native repositories and encounter queues.

`muspah-native-phase-dsl-edge-red.log`:65 tests, one failure. Queue-before-post-tick ordering allowed
a new cloud to damage the player on its birth tick. The preserved draft only processed that cloud
the following tick. A next-movement gate repairs this migration regression without changing its
declared damage. `muspah-native-phase-dsl-regression.log`:all67 tests pass,39s/167tasks. Added exact
cloud movement/hit timing, out-of-reach timed entry without post-tick phase polling, shield
interruption of lightning, same-tick lethal skip races, passive-shield-break expansion, natural
crevice/both exit interactions and two independent native rooms with isolated teardown.

Architecture waivers now remove private-membership/InstanceScript debt and explicitly review only
the spatial/corruption post-tick handoff, two native attack parameter objects and three scoped
environmental hit sites. These tests preserve the existing researched/draft behavior; they do not
certify the unresolved non-player damage exclusion, generic projectile geometry, source contribution,
exact OSRS specials or live visuals. No packet comparison is claimed.

`muspah-final-broad-check.log`: all applicable boss tests, Muspah scoped check, boss/instance,
combat-manager/combat-scripts/game/drop tests, app classes and architecture enforcement pass,
1m10s/508tasks (63 executed,445up-to-date; modules with no tests report NO-SOURCE).
The initial `muspah-final-native-check.log` command used a nonexistent `:content:other:drops`
path and failed task resolution; the corrected broad command uses `:content:drops:test`.

Deployment mapping: reuse existing `NpcParameterConfigInstaller`, which patches only selected
NPC parameters/explicit occupancy and backs up original encoded definitions. A generic explicit
Gradle entry point exposes that existing installer; no new packing engine or startup hook.
Select only `npc.gargboss_dusk_phase1_defensive`, `npc.snakeboss_boss_ranged`, `npc.muspah`.
`BossMigrationKcCacheAuditTest` verifies the installed SERVER definitions separately from native
gameplay tests. Before packing, `boss-migration-kc-install-red.log` correctly reports missing
Dusk spawn KC metadata (one test/one failure), not a Kotlin or gameplay-test failure. Cache paths,
revision240 and PostgreSQL authority must remain unchanged; LIVE is not part of this installation.

### Backed-up local deployment checkpoint

Source checkpoint `c48e36cadf1c29db550f9d7da6557405311c45db` is preserved by
`backup/boss-dsl-predeploy-20260909-muspah`. The source-only ZIP
`logs/boss-dsl-predeploy-20260909-muspah-source.zip` contains7,274 entries; SHA256
`C1B93AE0C0B631FB7DB2938792A66C88DB5D1B1C8629B31B036F16E4F4603AAB`.
It excludes runtime/cache/PostgreSQL/log/generated/config-secret paths. Limited credential-pattern
scans found no candidates in the new selected source. Historical Git objects were not certified.
The cold SERVER copy is `logs/boss-dsl-predeploy-20260909-server-cache/`; original encoded selected
NPCs also remain under `logs/npc-parameter-backups/`. No remote push or merge occurred.

The existing selected-NPC installer completed in16s/16tasks. Installed SERVER KC tests plus
Vorkath/Zulrah cache audits pass all5 tests,45s/18tasks (`boss-migration-installed-cache-check.log`).
Only selected NPC metadata changed. SERVER dat2 SHA256 is now
`D01985ED94C9814DBF99509B335E964C69F1FF91C2464A5165D42A78E1915D75`;
LIVE remains `FD0FE5EF66039A791A900EDDDF1CDBCFE57E09E8C32EA3496C69E96217A41A7B`.
Revision240 and paths are unchanged.143 source/unrelated-dirty-data fingerprints matched after
packing; later intentional audit-harness/documentation edits are recorded separately.

Canonical JVM41396 stopped through the normal shutdown helper. Its old, pre-migration runtime
reported closed database connections while saving pending account requests. Stored inventory,
bank and realm-setting fingerprints remained identical, but this does NOT prove any previously
unsaved in-memory progress was retained. This shutdown defect is recorded, not silently fixed as
boss-migration work. New JVM38976 started at23:19 on September9 and shut down normally; JVM13012
started at23:21. Both emitted the custom-build marker and Server ready, and passed the canonical
doctor (`boss-migration-doctor-restart-1.log`, `boss-migration-doctor-restart-2.log`). The marker's
Git fields said unavailable in the background launcher; doctor resolved the actual checkpoint,
and jcmd confirmed the new BossPhaseClock class loaded. Startup alone is not gameplay verification.

The read-only repeatable-read `BossMigrationPersistenceAudit.java` queries the native embedded
PostgreSQL authority and emits only ordered-table counts/hashes. Before and after both restarts:
22 inventory containers,1,147 inventory rows (including banks),1 realm spawn/respawn row; every
SHA256 matches. Logs: `boss-migration-persistence-before.log`, `-restart-1.log`, `-restart-2.log`.
No account data was changed by this audit.

The opt-in real-game-loop Vorkath audit passed normal/special attacks, reset, native boss/player
death, external teleport and normal exit, then timed out at tick801 on its stale disconnect step.
It signalled pendingLogout for the removed private loop instead of clientDisconnected for native
InstanceLifecycleScript.onPostTick. Mapping: adapt the diagnostic signal to the existing native
hook, then clear the synthetic player's force-disconnect flag before the account logout stage.
No gameplay/shared API change. A native regression executes the same audit helper and lifecycle
hook and verifies room/effect cleanup without a save request. All111 Vorkath tests, instance tests,
app classes and architecture check pass,41s/452tasks (`boss-migration-live-audit-disconnect-regression.log`).
The corrected live audit must be rerun; the timeout must not be reported as a successful fight audit.

The next audit passed disconnect but caught its immediate-logout assertion: native membership is
removed immediately and NPC/map reclaim is intentionally deferred one tick. Publishing an account
logout event for the synthetic account also attempted an activity-log insert, rejected by the
account foreign key (no account row was created). The harness now invokes only InstanceManager's
native logout workflow and waits for deferred reclaim; it does not emit account-level events.
The added regression executes that path and verifies immediate hit cancellation, deferred NPC
retirement and no account-save flags. `boss-migration-live-audit-reclaim-regression.log`: all112
Vorkath tests plus instance tests/app classes/architecture pass,41s/452tasks. This is a diagnostic
adaptation to the already-migrated native lifecycle, not another combat change.

Final live audit at source `7784e155e`: **SUCCESS75 assertions**, saved as
`logs/boss-migration-vorkath-live-audit-success.log` (started2026-09-10T03:40 UTC). Natural AI,
all ten native projectile profiles, both specials, launch/impact timings, collision-based acid
walking, Crumble Undead, resets, boss/player death, exit, external teleport, native disconnect and
deferred instance logout cleanup passed. This uses the real server game loop/cache/packets, not a
rendered client or an OSRS capture diff. It does not certify full account logout/save integration.
Current JVM23164 is canonical; restart log `cerberus-audit-20260907/restart-2026090942.stdout.log`
records custom marker23:40:08 and ready23:40:10. The earlier gameplay checkpoint already survived
two controlled restarts. Subsequent changes were the diagnostic harness only. Do not restart the
server again merely to repeat that checkpoint while the user is testing. Database fingerprint log
`boss-migration-persistence-harness-rerun.log` still matched all three predeployment fingerprints
before user testing began. Later legitimate player changes must not be mistaken for corruption.

### Cerberus pre-edit strangler mapping

Existing baseline:1,290-line CerberusEncounterManager;52 executable module tests. Evidence remains
`docs/cerberus-rev240-evidence.md` and the September7 audit/dispatch/instance-guard follow-ups.
Those documents explicitly distinguish Wiki/cache facts from donor-derived profiles and incomplete
captures1702/3250. No complete normal OSRS fight or new audiovisual evidence is asserted here.
The migration preserves the draft values; it must not turn its uncertainties into parity claims.

Architecture reference: official upstream `61139e76a216592d66c2b20366ef26d48609875d`,
BossCombat/BossEncounter/EffectInterpreter/Selector/Condition under `api/bosses`,
`api/combat/combat-scripts/.../NvPCombat.kt`, `api/instances/.../InstanceScript.kt` and
`.../InstanceManager.kt`, and `content/bosses/scurrius/.../Scurrius.kt` / `ScurriusInstance.kt`.
The source-defined InstanceScript constructor, typed session state, native attack overrides,
NpcAttack effect, ability-count condition, phase clock and scoped queues are already-tested **local
extensions of those native workflows**, not claims of official upstream APIs. No new shared
capability is planned for this step.

| Existing behavior / confidence | Existing owner | Planned established workflow | Executable checks |
|---|---|---|---|
| Forms5863/5862/5866,5x5,600HP; cache-verified | Manager construction/transmog | Same immutable actor identity; BossCombat registers all forms; native definition stats retained | Real-cache dispatch, base/visible combinations and no generic fallback |
| Six-tick normal, triple>Souls>lava priority; current draft characterized | Private selector/counter/actionDelay | BossSpec conditional modulo/HP selection, nested injected-RNG choices and SetAttackRate |35-turn rotation, HP boundaries400/200, skip RNG, blocked attempts and6/10/8 recovery |
| Normal magic/ranged plus adjacent Stab; maximum23, launch-prayer snapshot | Manual formula/projectile/hit code | Effect.NpcAttack delegates native NvPCombat; named parameter resolver retains head geometry and equipment-reduced maximum distribution | Actual zone packets, native queued hits, max distribution and prayer switching |
| Triple components0/2/4; draft timing, no complete OSRS capture | Three private launches/world queues | Declarative Sequence/Delay with native attacks; per-component launch snapshots, single ability commitment | Exact component ticks, opposite outer heads, melee impact, interruption and no duplicate ordinary shot |
| Animation priority/defend lock and cast/impact presentation; draft values | Manager presentation helpers | Thin named presentation handlers using normal anim/spotanim/sound APIs; no independent attack scheduler | Pending sequences, spell/impact effects and defend suppression |
| Souls below400, every7; permutation, fixed lanes,30/15/20/10 prayer drain,22 Elysian damage | Manager spawned NPCs/world timers | Focused Soul handler, native NPC/repository/path APIs, encounter-owned queues/hit scopes | Three different ghosts, west-east order, fixed facing, Ward/Spectral, retirement at every stage |
| Lava below200, every5; three unique destinations, projectile-arrival activation, fixed damage policy | Manager lava lists/tick | Focused spatial handler; native projectile/collision/queues scoped to encounter | Actual destinations/arrival, no cross-instance hits, lifetime and cleanup |
| Solo copied4883, entry(24,10), sitting(22,34), public exit; source-normalization tested | Private player/run map and create/leave/logout handlers | Source-defined InstanceScript, native keyed membership, typed InstanceSession state and configured actor | Concurrent clones, protected entry refusal, exit/teleport/logout/disconnect/death and fresh re-entry |
| Standing4, walk-back/sit reset,15-tick respawn; draft lifecycle | Private state deadlines and respawn construction | BossEncounter phases/opt-in clock; focused arrival-sensitive route handler; native death/reveal same-actor respawn | Exact state/arrival ticks, repeated kills, no duplicate actor or stale hazards/tasks |
| Native drops/KC, six achievement flags, debug reward suppression; existing behavior | Native death/drop plus local task policy | Keep native death/drop/Collection Log/vars; leave task eligibility as narrow event policy | Once-only completion, debug no rewards, persisted var definitions and native death destination |
| Debug next/counter/skip controls, no gameplay rewards | Manager forced fields | Native diagnostic ability forcing/counter state; diagnostic-only overrides must not become production selection | Forced attacks/skip and clean next round |

Migrate in independently tested internal steps: characterization, declarative combat, native
instance/actor lifecycle, specialized-resource split, final removal/waiver review. Do not deploy
an incomplete Cerberus wiring step. The current deployed checkpoint must remain usable while
the source migration proceeds. Preserve existing scripts, captures, maps, drop tables and evidence.

Cerberus characterization checkpoint: four added real-instance/queued-hit tests lock strict400/200
thresholds,6/10/8 recovery, blocked-attempt override retention, skip/no-reward behavior, triple
component prayers and defend suppression. Initial56-test run had one fixture failure because
emitted NPC animation packets were never cleared in the small harness; modeling the native
post-tick packet clear corrected the test, not gameplay. `cerberus-migration-characterization-green.log`
passes all56 tests,32s/146tasks. Scoped formatting plus tests pass again in31s/149tasks
(`cerberus-migration-characterization-final.log`). The earlier formatting invocation failed task
resolution because the PowerShell -P path was unquoted; no code/test failure in that invocation.
The1,290-line manager is still the authoritative implementation until these characterizations
pass through its replacement wiring. Do not count Cerberus as migrated or deployed.

Implementation order refinement: establish native protected entry/session ownership before combat
conversion so retained projectile/Soul/lava tests execute through InstanceScript and its keyed
callbacks. Replace the private membership map and duplicate logout/teleport/death cleanup first;
retain the existing actor/phase/combat behavior for this bounded step. Native instance lifecycle
retires effects on leave and owns entity teardown. Subsequent steps remove private actor respawn
and attack/phase scheduling. No new shared API or public entrance is introduced by this refinement.

Cerberus native-instance checkpoint (September10): InstanceScript now owns protected entry/exit,
keyed join/leave and the configured sitting actor. Typed InstanceSession state replaces the private
player/run map. Native InstanceLifecycleScript handles teleport/logout/disconnect/death retirement;
the redundant boss death-cleanup hook and logout subscriptions are removed. Native NpcDeath retains
the same actor through the existing death animation and fifteen-tick hidden respawn. This actor
change was included with instance ownership because repository deletion now correctly detaches an
NPC; a private delete/recreate cycle could no longer resolve its completion through native ownership.
The logout-panel result also now reports Failed when protected teleport validation refuses exit.

Six native lifecycle regressions add refused entry/exit, panel result, four departure paths with
two-room isolation, two native death/reveal cycles, and departure during the death coroutine.
Existing56 characterizations still pass. `cerberus-native-instance-final.log`:62 tests, zero
failures/skips; scoped formatting and instance tests pass,39s/151tasks. Architecture enforcement
and git diff --check pass. Removed the three obsolete instance/membership/lifecycle waivers.
Initial fixture runs exhausted heap because inline Mockito answers retained allocated collision
maps; serial execution plus per-test mock cleanup (the existing Muspah/Zulrah fixture pattern)
resolved this. A test queue generic compile error and a formatter manifest containing a .kts
instead of only .kt sources were corrected; neither changed live gameplay.
This is an internal checkpoint, NOT a completed or deployed Cerberus migration. Ordinary combat,
phase deadlines and specialized handler extraction remain. The live seven-boss checkpoint is
unchanged, and no cache/PostgreSQL data or unrelated work was modified.

Combat conversion detail: native BossEncounter commit count drives the production conditional
selector. Admin counter overrides use a reward-ineligible diagnostic offset plus the existing
forceNextAbility API; no new shared counter mutator or production scheduler is needed. Basic
style/head choices and special skips use declarative injected-RNG branches. Triple components
use a native suspended NPC coroutine with two-tick component locks, then the original ten-tick
ability recovery from its commit tick. Tests must advance the native coroutine before reading
the final recovery lock, rather than assume all component work was already placed on private
world timers. Normal attack parameters preserve the existing equipment-reduced maximum before
damage RNG, head-attached projectile geometry and per-component launch-prayer snapshots.

Additional generic presentation mapping (within the requested shared-framework repairs): the
retained triple emits one4493 sequence at launch, with hits at0/2/4. Native post-tick clears
pendingSequence each cycle. Both local NvPCombat.playAttackFx and official upstream
61139e76a216592d66c2b20366ef26d48609875d always emit the configured/default sequence and sound
on each attack call. Local searches across combat/boss APIs for skipAnimation/playAnimation/
suppressAnimation/suppressAttackAnimation found no opt-out; an explicit attackAnimation only
substitutes another sequence. Writing pending packets directly or replacing the hit pipeline
would violate the migration architecture. Smallest reusable capability: an opt-in
NpcAttackOverride.playAttackFx=false parameter, default true, skips only the native default
animation/sound when a DSL ability owns that presentation. It does not bypass accuracy, source
attribution, modifiers, projectile sending, action locks, queued hits or cancellation. This is a
new LOCAL extension of native NvPCombat, not an official upstream API. Shared interpreter/native
combat regression must test both default and suppressed presentation with identical hits and
locks; Cerberus regression flushes the first packet then executes subsequent components.

Native combat checkpoint: `logs/cerberus-native-presentation-checkpoint.log` passes (17s,
171 tasks; applicable tasks up-to-date after the45s green run). XML results: Cerberus69,
boss runtime47, combat scripts8 tests, all zero failures/skips. The packet-flush regression
first failed in `cerberus-native-triple-presentation-red.log`; it now passes with the retained
0/2/4 volley and no default animation replacing4493. No deployment or visual parity is claimed.

Next mapped source step: reuse existing local BossPhaseClock and BossEncounter.schedule (the
shared-framework migration additions, not upstream APIs) for standing/reset phase deadlines
and cancellable Soul work. Keep the native LateCycle spatial/presentation stage using declared
ready phases; do not activate one tick earlier. Replace the private generation with the native
BossEncounter identity. Preserve characterized four-tick wake, four-tick reset minimum plus
four-tick sitting and existing unverified24-tick reset path fallback. Tests cover both exact
deadlines and immediate queue cancellation on departure/reset/death; no new shared API is needed.

Phase/scope checkpoint: all75 Cerberus tests plus shared boss/instance suites pass in
`logs/cerberus-native-phase-checkpoint.log` (39s/167tasks). Six new native tests cover immediate
standing/reset deadline removal, isolated two-room Soul queues, reset/death cancellation,
NPC-deletion ownership cleanup, the characterized24-tick route fallback, and protected Soul
drain cancellation after teleport. BossEncounter now owns phase identity and cancellable tasks;
private generation/stateCycle/resetDeadline/resetSitting fields are removed. Souls are attached
to both the native instance and encounter ownership and detached on deletion. Remaining arena
geometry, hazard snapshots and presentation are being extracted, not rebalanced or OSRS-certified.

Handler extraction complete: CerberusArenaEffects retains only the existing Soul choreography,
lava geometry/arrival snapshots and firewall presentation/damage. CerberusRun lives in typed
InstanceSession state and derives its phase from BossEncounter; CerberusArena owns coordinate
conversion; CerberusAssetValidation retains the original cache crosswalk checks. The coordinator
is598 lines versus1,290 initially. No old combat engine remains active beside BossCombat.
The six reviewed native-parameter/spatial-combat sites remain explicitly waived; all ordinary
projectile/hit scheduling is native DSL combat. Debug reward suppression and existing CA/drop
behavior are unchanged. No source, captures or research was discarded to obtain the reduction.

Final source verification:
- `logs/cerberus-handler-extraction.log`:75 module tests pass,39s/160tasks.
- `logs/cerberus-extraction-check-cache.log`: scoped Cerberus check and75 module tests,
  plus2 CerberusR240CacheAuditTest cases pass,38s/166tasks.
- `logs/cerberus-final-broad-check.log`: every applicable boss test task, api:bosses,
  api:instances, combat-scripts, combat-manager, engine:game, content:drops, server:app:classes
  and bossArchitectureCheck pass,1m38s/505tasks (78 executed,427up-to-date).
  NO-SOURCE modules do not constitute executed gameplay tests.
- `git diff --check` passes. Unrelated dirty .data hunks remain unstaged and untouched.
No rendered Cerberus client scenario or OSRS event diff has been completed by this migration.

Predeployment frame-order correction: review of native GameCycle.tick found that worldMain
deadlines run before MapClock.tick and LateCycle. The fixture previously processed world queues
after the increment, masking a one-tick delay for LateCycle-authored wake/reset presentation.
The corrected fixture produces3 failures in `cerberus-native-frame-order-red.log` (test commit
`ecca4372a`). Standing/reset/sit ready markers now use the existing PhaseSpec.exitAfter with
the named lateCycleDeadline conversion (presentation interval minus the native stage advance);
the pre-input-to-pre-input reset-travel interval remains20. No new shared API or private timer
was added. `cerberus-native-frame-order-green.log` passes all75 tests,32s/160tasks.
The earlier `backup/cerberus-source-verified-20260910` at `c96d06528` and ZIP hash
`7F74E783B5491A1318E30A70632448CF07A82E8CA68AEFB5594B0AE5A767DB1D` preserve the prior
checkpoint but are superseded for deployment by this correction. Neither was deployed.

Corrected final broad verification: `cerberus-corrected-final-broad-check.log` passes,
28s/509tasks (7 executed,502up-to-date), including scoped Cerberus check, every applicable boss
test, the affected shared suites, app compilation and architecture detector. The full75-case
Cerberus execution immediately preceding it is in the32s green log above. No failures or skips
in Cerberus,47 boss-runtime tests or8 combat-script tests. The corrected source will be backed
up as `backup/cerberus-native-frame-verified-20260910` before deployment.

Live checkpoint pending: doctor still identifies canonical JVM23164 running the earlier
seven-boss build; SERVER/LIVE hashes and revision240 are unchanged. A game-port43594 client
remains connected. Deployment was not attempted; the user was asked to log out briefly before
the controlled restart because of the previously recorded pending-save shutdown risk.
The read-only predeployment PostgreSQL snapshot (`cerberus-predeploy-persistence.log`) has22
containers,1,152 inventory rows and1 realm row; its inventory hash differs from the earlier1,147
rows while the user is playing. This is live player progress, not evidence of migration data loss.

Final corrected backup created: branch `backup/cerberus-native-frame-verified-20260910` at
`c421aca33`; source-only ZIP `logs/cerberus-native-frame-verified-20260910.zip`, SHA256
`173612586CDAD4B5C39F4BAB304B6A63FF33E0FD10A7D1194B5CC20E74E4257D`.
The ZIP contains7,439 entries and no excluded .data/log/build/cache/config-secret paths under
the checked exclusion policy. No remote push/merge or runtime restart was performed.

Cerberus local deployment completed September10 after the user logged out. Game-port connection
count was0 before each normal shutdown. Controlled rebuild/restart23164 ->32456 ->36776 used
`cerberus-controlled-restart.ps1` with restart numbers202609101 and202609102. Both starts report
OPENRUNE CUSTOM BUILD ACTIVE, canonical directory, revision240 and Server ready (01:08:18,
01:15:07 EDT). The marker's Git fields remain unavailable; deployment source was `c421aca33`
plus documentation checkpoint `4417447e3`, rebuilt by the canonical Gradle run workflow.
Both read-only doctors pass. SERVER dat2 SHA256 remains
`D01985ED94C9814DBF99509B335E964C69F1FF91C2464A5165D42A78E1915D75`;
LIVE remains `FD0FE5EF66039A791A900EDDDF1CDBCFE57E09E8C32EA3496C69E96217A41A7B`.

Read-only PostgreSQL fingerprints match the logged-out snapshot before and after both restarts:

- 22 inventory containers: `85989c0b4aa1079086bfb308ab78c6161120f950dcd3ea76c152b298635d4991`.
- 1,152 inventory/bank rows: `dae3cf5f00d31c4df803067408c4af97164659777d18fc5d5a041103974fd70c`.
- 1 realm spawn/respawn row: `c971b1f6d8e431e9f25d64c6dc154d7c60ff22ce7c9e20274a73f671e911ef50`.

Evidence logs under logs/: `cerberus-deploy-logged-out-persistence-before.log`,
`cerberus-deploy-doctor-restart{1,2}-20260910.log`,
`cerberus-deploy-persistence-restart{1,2}-20260910.log`, and
`cerberus-audit-20260907/restart-20260910{1,2}.{stdout,stderr}.log`.
Normal old-server shutdown emitted PostgreSQL listener/pool warnings while services closed;
no connected player or pending-player-save failure was observed in this checkpoint. Initial
long-cycle startup warnings do not constitute a gameplay test. No Cerberus rendered-client or
OSRS packet diff was performed. Eight boss groups are now source-migrated and locally deployed,
not certified1:1. Royal Titans is the next separate branch.

User now authorizes native forced logout/save for future tested, backed-up local deployments.
Do not pause merely for their connected client. Confirm saves before normal shutdown, retain
the persistence safeguards and do not hard-kill the JVM. Recorded in AGENTS.md and the handoff.

## Royal Titans migration mapping (September10)

Starting branch: `refactor/royal-titans-boss-dsl-20260910`. Canonical doctor was rerun after
the Cerberus deployment; no Royal Titans changes were included in that live build. Additional
Cerberus live checkpoint backup: `backup/cerberus-live-verified-20260910` at `d86baed87`, ZIP
`logs/cerberus-live-verified-20260910.zip`, SHA256
`9345A54A1B02464636E141AF45FEBF44E3485D5AC9C2AA5BAEB2B5D021E59EDB` (7,439 entries,0 excluded-path
violations). No unrelated `.data` changes were staged or replaced.

Current manager measurement:2,017 lines. Search across content/api/engine/or-cache finds51
Royal Titans-related source/test/evidence files. The existing module/World/Combat registration
is authoritative; rtenter/rtjoin/rtstatus remain testing conveniences. Baseline focused command
passes in25s/196tasks, including the revision-240 cache audit; existing module XML records97
tests,0 failures. These are characterizations, not proof that inherited uncertainties are resolved.
Retain the existing capture ledger, approved API mapping, tests and source data.

Architecture reference: official `upstream/main` commit
`61139e76a216592d66c2b20366ef26d48609875d`. Exact comparable paths inspected:
`content/bosses/scurrius/src/main/kotlin/org/rsmod/content/bosses/scurrius/ScurriusInstance.kt`,
`api/instances/src/main/kotlin/org/rsmod/api/instances/{InstanceScript,InstanceManager}.kt`,
`api/bosses/src/main/kotlin/org/rsmod/api/bosses/{runtime/BossCombat,runtime/EffectInterpreter,spec/Effect}.kt`,
and `api/combat/combat-scripts/src/main/kotlin/org/rsmod/api/combat/NvPCombat.kt`.
The corresponding GitHub tree is https://github.com/OpenRune/OpenRune-Server/tree/61139e76a216592d66c2b20366ef26d48609875d .
Local Cerberus/Guardians are migration examples, not upstream provenance.

| Mechanic / existing ownership | Planned native integration / classification | Characterization and evidence constraints |
|---|---|---|
| Elemental four-tick ordinary melee in manager.attackElemental | BossSpec/BossCombat/EffectInterpreter (official family); existing local Effect.NpcAttack adapter to native NvPCombat; Rotation selector to avoid extra RNG | Execute real attack events for11947 slash/11950 stab, max4, impact+1, launch prayer, cadence, duo ownership and queued-hit lifetime; retain movement/stacking tests |
| Free solo/duo start/join/exit, warning, name input, private membership maps | Official InstanceScript protected completeInstanceEntry/defaultLeaveFlow, keyed join/leave and InstanceLifecycleScript; existing local source-spec constructor and typed InstanceSession state | Preserve all entrance options,450 comparison, two-player maximum; refusal/teleport/logout/disconnect/death/rejoin/two-room tests before removing manual flows |
| Titan patrol/arrival and independent readiness, summons/walls/stomps | Per-actor BossEncounter phase/cadence plus existing local BossPhaseClock; small external route/geometry handlers using official RouteFactory/Npc.walk and native collision | Preserve existing golden anchors, platform restriction, facing, phase-one damage; do not certify unproved patrol distribution/close-action budget |
| Paired transition, Blast, survivor rotation and collapse/reset | Declarative phases/deadlines with paired-session coordinator; native scoped scheduling, instance actor ownership and death queue transforms | Preserve teleport3/4/7, opening8, charge6/pulses7/9/11, first36 then29 inherited cadence, corpse transform and first-claim21/26/29 reset; rotation selection/sub100 policy remains uncertain |
| Walls, landing floors, packs/explosions, custom death presentation | Focused external handlers using native loc/NPC repositories, routefinder, map/sound APIs; existing local encounter hit/task scopes | Keep four-tick walls/three-piece spell removal, vertical packs, custom death-before-delete and pending-hit cancellation; candidate distributions/landing fallback/lifetime remain unverified |
| Spell accuracy/maxhit, wall manual/autocast, splash/echo/refunds | Existing authoritative spell handlers and native onNpcHit; preserve approved local HitContext and source attribution | Existing wall-cast/lethal-race/refund tests remain; no new private spell callbacks or alternate damage pipeline |
| Boss HUD/body models/ranged vulnerability | Native target HUD/presentation; existing approved local body model and accuracy fields remain explicitly local | Keep target switching and model reset tests; migration alone does not establish ranged-grace OSRS parity |
| KC/PB/global/deaths/scoreboard | Existing PostgreSQL GameDatabase transactions and named interface909 workflow retained; last-defeated spawn clock remains boss-specific | Preserve completion retries, duplicate callbacks, concurrency tests and varp4648; do not replace the specialized committed scoreboard with generic first-boss timing |
| Rewards, claims, CA observations | Preserve existing authoritative drop definitions/source ledger while reviewing shared delivery and award paths separately | Durable claims and eleven CA predicates were already unfinished, not a migration regression. Do not represent the current local claim flag/200-tick drops as native durable delivery |

No new shared API is proposed for the first bounded step. Effect.NpcAttack, its lifecycle checks,
BossCombat.canTarget/onImpactHit and typed session state are existing LOCAL migration extensions,
not upstream APIs. The requested framework/migration authorization covers their reuse. Each helper
will have one DSL owner only; remove its old direct attack registration in the same source step.
Use survivesSourceDeath=true for the already-launched elemental melee hit to retain its existing
lifetime through the custom death animation, while native deletion/instance/member scopes still
cancel it. Do not turn an architecture change into an undocumented damage rebalance.

Elemental characterization added before production edits: five real-registration/NvPCombat cases
cover both styles, native accuracy invocation, max4/impact+1, launch prayer, missed hits, four-tick
recovery, duo ownership/departure and launched-hit survival during custom death presentation.
The test helper now enters native ApPlayer2 mode before dispatching its event; omitting that mode
initially caused four fixture failures because NvPCombat correctly refused idle NPC attacks.
No production change was needed for the characterization. All102 module tests pass in
`royal-titans-elemental-characterization-final-20260910.log` (48s/194tasks).

First bounded production step: fire/ice elementals now have distinct registered BossSpecs using
Effect.NpcAttack, native slash/stab accuracy, max4, animations11947/11950, impact+1 and four-tick
recovery. A one-entry Rotation avoids introducing a new RNG draw. BossCombat receives the retained
incoming spell/refund callbacks and ownership predicate; the direct onAiApPlayer2 registration
and manager.attackElemental are removed. No ordinary elemental engine runs alongside the DSL.
Deletion retires its native encounter and effect scope; custom dying presentation retains its
already-launched melee hit until deletion, as characterized. No new shared API was added.

`royal-titans-elemental-dsl-green-20260910.log`: all102 existing/characterization tests pass,
1m16s/196tasks. Two additional native deletion/multi-room isolation tests raise the total to104.
The first isolation fixture assumed six simultaneous helpers after allowing the second room's
patrols to diverge; observed three. Synchronizing both rooms' test-only unlock frame corrected
the fixture. `royal-titans-elemental-dsl-verified-20260910.log`:104 tests, zero failures/skips,
scoped module check, api:bosses tests and3 cache audit cases pass,53s/204tasks (cache/API up-to-date).
The earlier test-only compile errors/mode-setup and isolation failures are retained in logs;
none were addressed by changing OSRS timing or shared combat formulas.

Manager now1,997 lines versus2,017; its remaining paired lifecycle/rotation is still migration debt.
Removed the obsolete boss-api-dependency waiver and reduced the direct-combat review budget3 ->2.
Remaining two sites are elemental spell splash and arena tile damage, not ordinary helper melee.
Patrols, wall casts/autocasts, Blast, body models, corpses, reward choice policy, PostgreSQL statistics
and the cache remain unchanged. This is a source checkpoint, NOT a completed/deployed Royal Titans
migration or a new OSRS parity claim. Native InstanceScript/session ownership and paired DSL phases
are the next mapped steps; inherited reward/CA/evidence uncertainties remain in the existing ledger.

Broader source verification passes: `royal-titans-elemental-broad-check-20260910.log`,30s/505tasks
(4 executed,501 up-to-date): every applicable boss test task, api:bosses, api:instances,
combat-scripts, combat-manager, engine:game, content:drops, server:app:classes and architecture
enforcement. NO-SOURCE/up-to-date tasks are not represented as newly executed gameplay tests.
git diff --check passes. Save this passing bounded stage on
`backup/royal-titans-elemental-dsl-20260910` before proceeding; live remains the Cerberus checkpoint.

### Royal Titans native session/lifecycle step

Use the already approved instance mapping above; no shared engine change is proposed. Rechecked
official Scurrius/InstanceScript against the pinned upstream revision. Source-defined specs and
typed session state remain explicitly existing local extensions. The next source change moves
start/join/exit into RoyalTitansWorld : InstanceScript, uses native member-name lookup and keyed
join/leave events, and resolves actor ownership from InstanceManager. Delete the manager's three
parallel run/member/NPC indexes. Session-owned participant data holds only combat scopes and
encounter statistics, not authority to join or remain in a room. Native InstanceLifecycleScript
owns logout, disconnect, movement and repository deletion; the session cleanup callback retires
specialized hazards. SQL retry records intentionally outlive the room and must not be removed.

Characterize native leave/death/logout/teleport, duo continuity, separate rooms and direct native
termination before production edits. Add protected-entry/exit refusal tests when the actual
InstanceScript entry boundary is wired into the fixture. Death recording must happen before
native membership disappears (including death's respawn teleport), remain idempotent, and retry
after destruction. The death cleanup adapter may observe a death but must not own detachment.
Preserve existing spawn ticks, warning/name dialogs, join threshold, corpse/reset and rewards.

Implemented native session ownership: RoyalTitansWorld now extends InstanceScript, registers its
source spec, and uses protected completeInstanceEntry/defaultLeaveFlow. Native keyed join/leave
owns participant scope setup/retirement. All three private manager indexes and its create/join/
leave/logout/teleport methods are gone. NPC ownership resolves through InstanceManager; session
occupants are authoritative. Participant records now contain only hit scope/death observation.
Run generations are session-local. Specialized hazard cleanup uses session.onCleanup; native
instance teardown deletes remaining Titans/corpses. Pending SQL death/completion retries survive
the room. Three obsolete architecture waivers were removed. No shared source was changed.

Before edits, new native leave/death/logout tests failed because the old manager retained stale
membership; external teleport and separate-room termination passed. Log:
`royal-titans-native-lifecycle-characterization-20260910.log`, five tests, three failures.
An initial fixture compile error used a read-only HP accessor; corrected to native statMap setter.
After migration, the first full run passed113/114; the remaining reach-probe test tried registering
corpse operations on a bus now occupied by the real World script. Its isolated native reach probe
now uses a separate event bus, not duplicate gameplay handlers. The fixture executes real protected
InstanceScript entry/exit and native lifecycle registration throughout the retained combat tests.

Final focused log `royal-titans-native-lifecycle-verified-20260910.log`:116 tests, zero failures,
errors or skips; scoped module check, api:instances, api:bosses and three revision-240 cache tests
pass (unchanged shared/cache tasks up-to-date),1m33s/206tasks. New coverage includes denied create/
join/exit, logout-panel result, native death/logout/disconnect/teleport, deferred reclamation,
multi-room termination, death retry after destruction, duplicate joins and cross-room claim denial.
The latter correction prevents a contributor in another room claiming a foreign corpse; personal
claims within the correct duo room remain unchanged. Death cleanup is observation only with
priority ahead of native detachment; zero-HP keyed departures cover the earlier respawn teleport.
One participant flag deduplicates both observations. It is reset for a new completion.

Broader log `royal-titans-native-lifecycle-broad-20260910.log`: successful,46s/505tasks, four executed
and501 up-to-date. All applicable boss/shared suites, app compilation and architecture enforcement
pass. Manager1,932 lines (was1,997 after elemental step; original2,017). Frame timings/mechanics
remain characterized, not newly OSRS-certified. No deployment in this step; live remains Cerberus.
Paired Titan DSL phases/cadence and specialized handler extraction are still next.

### Shared phase-clock target continuity regression

The approved shared-framework scope includes target selection, multiplayer ownership and lifetime
cleanup. Before wiring paired Titan deadlines, complete local searches of BossPhaseClock,
BossEncounter.bindPhaseClock, all bind callers and runtime tests show a captured initial Player
and an early return on subsequent bind calls. Native session departure can therefore cancel the
clock even while a valid duo partner remains. Official upstream has no BossPhaseClock at the
pinned reference; this is a defect in the already approved LOCAL migration extension, not an
official API claim or a request for a second scheduler. No suitable retarget operation exists.

Repair the existing internal bind operation to cancel only its old owned deadline, replace the
target/lifetime closure and rearm from unchanged phaseEnteredTick. Keep encounter identity,
unrelated scheduled work, phase counters and timing. No new public API or boss-specific DSL
concept is required. Tests must fail on current code for target replacement, then prove same-tick
multiple binds execute one entry, old-target departure does not cancel the new target, and current
target/actor teardown still cancels. Re-run shared boss, Cerberus and all other migrated suites.

The three new regressions reproduced two failures before the fix (50 shared runtime tests total,
`boss-phase-clock-target-red-20260910.log`). Replacing the captured closure and canceling only its
owned deadline passes all50 (`boss-phase-clock-target-green-20260910.log`,45s/158tasks). Repeated
same-frame binds retain one pending phase entry; unrelated scheduled hazards are not canceled.

### Royal Titans paired arena DSL step

Before changing production, `RoyalTitansArenaCadenceTest` executes the existing encounter through
109 ticks after transition and records both actors' charge/execute/summon/stomp sequences. Its
second case retains cadence when the initial duo player leaves. Both pass against the old arena
scheduler (`royal-titans-arena-cadence-baseline-20260910.log`,39s/196tasks). This characterizes the
draft's first36/subsequent29-tick policy; it does NOT resolve the OSRS rotation-selection gap.

Use existing BossSpec/PhaseSpec, BossCombat registration and the explicitly local BossPhaseClock
extension to own each Titan's arena deadlines. Remove the shared tickArena modulo selector.
The existing shared external-handler registry records a ready presentation marker; LateCycle
consumes it in the same order as the old code. This preserves native pre-input versus LateCycle
frame timing and paired Blast geometry without a second timer or selector. Unique wall, stomp,
summon and Blast geometry still uses the existing handlers pending focused extraction. Rebind
the native phase clock to the surviving participant on departure without resetting phase time.
Retire its deadline on Titan defeat or room cleanup. Register incoming hit processing through
BossCombat callbacks; remove the redundant manually registered Titan approach callback. No new
shared API or gameplay policy is proposed in this step. Add native-world-queue frame processing
to the fixture and prove cadence, both kill orders, duo continuation, cleanup and room isolation.

Implemented per-Titan arena specifications and native registration. RoyalTitansArenaProgram holds
only an encounter-owned ready marker/target identity and adapts native phase entries to LateCycle;
it has no private timer, selector, queue or membership map. The former tickArena modulo program
and no-op Titan approach handler are removed. Both incoming hit callbacks use BossCombat. Native
defeat retires the actor's deadline before the corpse transform restores its noncombat cache HP.
An initial118-test run passes (`royal-titans-arena-dsl-first-20260910.log`,1m3s/196tasks).

The added exact-departure-frame test then reproduced a one-tick delay: rebinding in LateCycle was
too late if the old target lifetime expired at the due pre-input deadline. Rebinding inside the
native keyed leave callback fixes it without altering phase time. Red log:
`royal-titans-arena-dsl-departure-red-20260910.log`,five tests/one failure. Final focused log:
`royal-titans-arena-dsl-verified-20260910.log`,all121 module tests pass, zero failures/errors/skips,
scoped check, shared boss/instance and three cache audit cases pass (unchanged tasks up-to-date),
1m5s/206tasks. Both kill orders, retained survivor cadence and room deadline destruction pass.
`boss-phase-clock-target-regressions-20260910.log`: Cerberus/Muspah pass,43s/170tasks.
Broader `royal-titans-arena-dsl-broad-20260910.log`:1m20s/505tasks,59 executed446up-to-date;
app classes, all applicable boss/shared suites and architecture enforcement pass. No deployment.
Manager is1,911 lines. Platform phase clocks, stage/reset choreography and handler extraction
remain migration work; the unresolved OSRS selection rules are unchanged.

### Royal Titans platform phase-clock mapping

Continue the approved phase/rotation mapping without new shared code. The existing movement
handler uses native routes and occupied footprints correctly for its characterized cases, but
RoyalTitanMovement still duplicates phase identity, readyCycle and the three-close-action counter.
Move those waits, identity and close-action sequence into the same per-Titan BossSpec/BossEncounter.
Keep only route destination, verified arrival, after-arrival action and paired elemental unlock
state in the movement adapter. Native route completion, not elapsed time, advances walking.
The external handler still chooses collision-aware patrol/return routes and the paired unlock
replacement action; it must not retain an independent timer or phase/attack-count engine.

Use pre-input phase origins (the preceding frame for a LateCycle transition) so even one-tick
turn/facing intervals remain positive native deadlines. Existing native golden routes/animations,
platform damage tests, blocked-footprint and canceled-route tests characterize this wiring. Add
a second-retreat/three-stomp characterization before production edits. Preserve the explicitly
unverified patrol weighting, return-row selection and three-action policy; no new OSRS claim.

Platform waits and the three-close-action program now live in RoyalTitansTitanSpec. Movement
state is stored on BossEncounter and exposes its phase, not another phase field; readyCycle,
closeActions and the run's movement index are removed. The renamed RoyalTitansPhaseProgram
uses the same native clock for platform and arena stages. Target rebinding works in either stage.
Paired teleport preparation cancels platform deadlines before the existing landing choreography.

The added61-tick second-retreat characterization passes against the old platform code (seven
movement tests, `royal-titans-platform-cadence-baseline-green-20260910.log`,35s/196tasks). The
earlier baseline invocation failed to compile a mutable/immutable List assertion; converting the
actual list in that test corrected only its type. The first platform migration reproduced25
failures across122 tests: starting the arrival wait from LateCycle lost one frame after native
post-tick route completion. Moving that native phase transition into the verified-arrival callback
restores all122 cases (`royal-titans-platform-dsl-arrival-green-20260910.log`,52s/196tasks).

A further native NPC deletion regression demonstrated a detached Titan reference recreating an
encounter on the next frame (`royal-titans-platform-native-delete-red-20260910.log`,eight tests,
one failure). BossEncounter.onCleanup now clears only that pair reference/spawn-timer entry.
Final `royal-titans-platform-dsl-verified-20260910.log`:123 tests, zero failures/errors/skips,
scoped module check, shared boss/instance and3 cache cases pass,1m2s/206tasks. Broader log
`royal-titans-platform-dsl-broad-20260910.log`:28s/505tasks,4 executed501up-to-date; applicable
boss/shared suites, app classes and architecture enforcement pass. Manager1,913 lines: phase
authority changed, not a claim of major handler extraction yet. Source-only, no deployment.

### Royal Titans focused handler extraction mapping

Continue the approved specialized-handler separation without adding shared APIs. Move typed
session Run data to RoyalTitansRun; keep SQL retry ownership in the completion coordinator.
RoyalTitansDefinitions retains the same revision-240 lookup/validation functions. A small
RoyalTitansArena adapter composes existing InstanceManager coordinate/member queries,
NpcRepository add/del plus native instance attachment, and scoped native hazard hits. It has
no scheduler, combat selector, private membership, persistence or teardown authority.
RoyalTitansWalls owns only wall geometry/presentation/removal and its existing run-owned pending
hazards. Ordinary phase scheduling remains in BossSpec/BossPhaseClock. Subsequent elemental
and Blast geometry handlers reuse that same arena adapter, not copied native workflows.

Preserve exact existing body-model, animation, loc/hitbox ordering, sound delays, target selection,
scope and packet behavior. The123 executable encounter tests (including wall casts/autocasts,
movement/collision, duo, hit lifetimes and cleanup) characterize this extraction. Do not change
drop policy, PostgreSQL schemas, cache authority or unresolved mechanics during file separation.

Platform route selection/arrival is the next focused handler: RoyalTitansPlatforms consumes the
existing native phase identity and calls RouteFactory/Npc.walk; it owns no cooldown or phase clock.
Its arrival guard queries native instance ownership through RoyalTitansArena, retaining the same
run-generation/stage guard. Landing telegraphs, floor locations and footprint blockers move to
RoyalTitansLandings with the same native loc repository and telejump calls. The existing landing
damage/knockback policy is preserved, not newly certified. Paired transition ordering remains with
the pair coordinator until its deadline ownership is reviewed. No new shared API is needed.

Extraction results: coordinator1,913 ->864 lines; Arena129, Definitions28, session Run86,
Walls177, Elementals272, TileAttacks272, Platforms194 and Landings108. This is responsibility
separation, not a claim that moving lines alone removes architecture debt. All123 existing
encounter tests pass after each step: arena1m6s, walls1m11s, elementals1m4s, tile attacks1m6s,
positioning1m1s (196tasks each). Initial arena extraction compile failures exposed incorrectly
qualified calls and a shadowing local `arena` flag; corrected source before any deployment.
`royal-titans-handlers-verified-20260910.log`: scoped formatter/check,123 module tests, shared
boss/instance suites and3 cache audit cases pass,1m1s/206tasks (6executed200up-to-date).
No reward/CA policy changes, no cache edits, no deployment and no new shared capability.
Broader `royal-titans-handlers-broad-20260910.log` passes in28s/505tasks (4executed501up-to-date):
all applicable boss/shared suites, server app classes and architecture enforcement. `git diff
--check` passes. Unrelated dirty `.data` changes remain outside this source checkpoint.

### Royal Titans paired teleport deadline mapping

Replace transitionStartCycle polling with declared per-actor BossSpec phases on the existing
BossPhaseClock. Branda lands at+3; Eldric's wait preserves his already-issued route until cast+4,
then lands at+7. Phase entry only marks the presentation action; LateCycle retains the exact
native teleport/facing/model/loc/damage ordering. Existing native target rebinding must preserve
both clocks when the selected duo player leaves at a due frame. Instance destruction must remove
both deadlines. No new shared API or changed landing geometry/damage is proposed. Add duo
departure-at-each-transition-boundary and empty-room characterization tests before production edits.
The two added native fixture cases pass against the prior implementation: departure offsets2/3/6
retain landing+3, cast+4, landing+7 and charge+15; room destruction at0/3/4/6 cannot retain actors
or clocks. `royal-titans-teleport-characterization-20260910.log`:33s/196tasks,2tests,0failures.

The two native timed phase programs now own that choreography; transitionStartCycle is removed.
LateCycle consumes phase-entry markers, retaining route completion until Eldric's cast and the
existing floor/model/hit ordering. No private transition timer remains. All125 tests pass on the
first production run (1m5s/196tasks) and scoped verification (`royal-titans-teleport-dsl-verified-
20260910.log`,57s/206tasks): module check, shared boss/instance and3 cache audit cases pass.
This is still source-only; no live/visual acceptance is inferred from these tests.

### Paired corpse reset mapping

The+29 respawn must outlive both corpse removals at+26, so neither removed NPC's BossEncounter
can own it. Native InstanceSession.onCleanup already owns resources across actor forms, and the
existing cancellable WorldQueueList supports exact deadlines. Reuse those APIs directly for the
three one-shot reinvigoration events (+21 collapse,+26 remove,+29 respawn); this is a focused
noncombat pair handler, not a general-purpose timer or attack framework. Session-owned state holds
the native cancellation handles and one presentation marker. Preserve LateCycle application,
first-claim-only start, unclaimed behavior and reward policy. Native session end/run retirement
physically unlinks all remaining work. No new shared API, altered respawn delay or gameplay policy.

Local searches: InstanceSession.onCleanup/resetState, InstanceManager.resetNpcs, BossEncounter
scheduleAt and WorldQueueList.addCancellable. Upstream reference remains pinned61139e76a;
InstanceManager is official family, cancellation/readiness and resource callbacks are existing
local migration extensions. Unlike Guardians, no actor remains visible throughout the reset gap.
Characterize duo claimer departure, teardown before collapse/after removal and repeated kills
before replacing firstClaimCycle polling. Normal combat continues through the registered DSL.
The two new reset characterizations pass before production edits (`royal-titans-reset-
characterization-20260910.log`,33s/196tasks). They cover claimer departure at+20 and empty-room
teardown at+0/+20/+25/+26/+28 without any subsequent resurrection.

RoyalTitansReinvigoration now submits only those three fixed native world deadlines, registers
cancellation with InstanceSession.onCleanup, and yields presentation markers for LateCycle. Its
state is on the session-owned run; no global membership/timer index was added. firstClaimCycle
polling is removed. Both normal reset and early room retirement unlink native work and release
the cleanup registration.127 tests pass first run (1m3s/196tasks) and scoped/cache/shared check
(`royal-titans-reset-native-verified-20260910.log`,1m1s/206tasks). No deployment yet.
Broader paired lifecycle check passes: `royal-titans-paired-lifecycle-broad-20260910.log`,
26s/505tasks,4executed501up-to-date, including app classes and architecture enforcement.

### Elemental corpse cleanup mapping

The remaining pendingRemovals map duplicates native deadline ownership for custom elemental
death visuals. Keep those visuals and their cache-duration timing; use the existing actor's
BossEncounter.scheduleAt to mark removal readiness, then remove through NpcRepository in the
same LateCycle position. Session-owned sets are presentation/cleanup observations, not a timer.
Native encounter end unregisters both actor references; reset deletes retained dying actors
through the native repository. No new shared API, changed attack impact stage or animation.
Add exact fire/ice deletion-frame tests and a red ownership test proving early native deletion
must unlink its deadline. Retain the launched-hit-during-death regression. Also remove obsolete
pack/area/same-tick counters and duplicate corpse references after confirming they are write-only.
The first test invocation also caught a fixture attempting death in the same frame as the ice
spawn's higher-priority animation. Waiting for that presentation in the fixture (no production
animation change) isolates the intended death timing. Corrected red run:2tests,1expected failure
for missing native deadline ownership; exact fire/ice deletion timing passes. Log
`royal-titans-elemental-death-ownership-red-corrected-20260910.log`,34s/196tasks.

Native actor-owned removal now passes both regressions. Early repository deletion cancels the
deadline and releases the dying/removable references; normal fire/ice visuals retain their exact
deletion frame. Removed the private deadline map and confirmed write-only pack/area/same-tick
counters and duplicate corpse fields. The pair coordinator is now830 lines versus2,017 originally.
`royal-titans-elemental-death-native-first-20260910.log`:129 tests,0failures/errors/skips,
1m1s/196tasks. Scoped formatter/check, shared boss/instance and3 cache cases pass in
`royal-titans-elemental-death-native-verified-20260910.log`,50s/206tasks. Broader all-boss/shared
tests, app classes and architecture enforcement pass in `royal-titans-elemental-death-broad-
20260910.log`,23s/505tasks (4executed501up-to-date). No new API, cache, reward or balance change.
This passing source checkpoint is ready for its backed-up controlled local deployment; it does
not certify rendered gameplay, packet parity, unresolved reward integration or all Combat Achievements.

### Royal Titans backed-up local deployment, September10

Source `1279d1676` is on `backup/royal-titans-native-checkpoint-20260910`. Source-only ZIP
`logs/royal-titans-native-checkpoint-20260910.zip`, SHA256
`46FF15C79D008FE4407D930EDA0226E24C3A1739BCCA940A66145BA1901EE1AC`:7,457 entries and zero
excluded paths. Limited credential-pattern review found no candidates in the changed source/docs.
No unrelated `.data` changes were staged. No remote push/merge or new cache packing occurred.

Reused the established controlled restart helper twice:36776 ->25860 ->39612. Both fresh builds
printed OPENRUNE CUSTOM BUILD ACTIVE and Server ready (03:40:27/03:40:30 and03:42:31/03:42:33 EDT).
Canonical doctor and read-only PostgreSQL fingerprints match after both boots against the fresh
predeployment snapshot. SERVER/LIVE hashes and revision240 authority remain unchanged. Exact logs,
backup and persistence values are in MIGRATION_HANDOFF.md. Account-saving shutdown succeeded;
the pre-existing service-order/closed-connection shutdown errors remain explicitly unverified,
not silently treated as a clean shutdown. No connected game clients were observed at either stop.
No rendered fight or packet diff is claimed. Continue Nex without redoing this routine checkpoint.
