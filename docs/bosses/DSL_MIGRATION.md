# Boss architecture migration — 9 September 2026

## Authority and preservation

Requested scope: architecture migration, not a combat rebalance. No deployment, server restart,
remote push, merge or PR action is authorized in this pass. The current user instruction authorizes
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

Initial line-count definition: main Kotlin files ending in `Manager.kt` or `Timeline.kt`, excluding
generated output. Includes specialized manager files (therefore not all counted lines are duplication).
Diagnostic-only modules are excluded. Zero manager lines is not proof of architecture compliance.

| Boss | Original architecture / manager lines | DSL conversion | Instance conversion | External handlers retained | Tests | Remaining uncertainty |
|---|---|---|---|---|---|---|
| Kraken | private per-player run, native combat wrapper; 363 | Pending golden migration | Pending | Whirlpool activation/drop tile | Baseline rules; native characterization pending | Preserve current behavior; no new OSRS parity claim |
| Vorkath | managers/timing; 1,254 | Pending | Pending | Acid/spawn/death storage | Existing tests preserved | PR remains unsubmitted |
| Corporeal Beast | manager; 502 | Pending | Pending | Core behavior | Pending | Detailed mapping pending |
| Alchemical Hydra | manager; 858 | Pending | Pending | Vents/hazards | Pending | Detailed mapping pending |
| Grotesque Guardians | manager; 945 | Pending | Pending | Paired mechanics | Pending | Detailed mapping pending |
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
