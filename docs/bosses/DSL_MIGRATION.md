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

No new boss-specific constants, timing, hit rules or reward rates are authorized by this table.
Existing local source-attribution, trace, ownership, recoil and impact-resolution additions are
preserved. Deferred concerns such as ignored noRepeatBias must not silently rebalance existing specs.

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

Implementation and test results will be recorded per focused commit. No gameplay migration or
runtime verification is claimed by the initial mapping. No deployment is permitted without new
explicit authorization.
