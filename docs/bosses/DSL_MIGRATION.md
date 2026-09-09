# Boss architecture migration — 9 September 2026

## Authority and preservation

Requested scope: architecture migration, not a combat rebalance. The user's later instruction
conditionally authorizes local deployment/restarts at about 5% remaining Codex plan allowance,
after finishing and testing the current migration and preparing a change/testing/continuation
handoff. That cutoff has not been reported or established; no deployment has occurred. Account
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
| Zulrah | manager; 993 | Pending | Pending | Positions/clouds/snakes | Pending | Detailed mapping pending |
| Muspah | manager; 1,088 | Pending | Pending | Spikes/pathing/shield | Pending | Detailed mapping pending |
| Cerberus | manager; 1,290 | Pending | Pending | Ghosts/lava | Existing capture tests preserved | Detailed mapping pending |
| Royal Titans | manager; 2,017 | Pending | Pending | Paired coordination/walls/statistics | Existing tests preserved | Existing evidence gaps remain distinct from migration |
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
