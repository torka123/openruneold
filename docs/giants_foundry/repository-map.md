# Giants' Foundry repository map (revision 240)

This is a read-only implementation map for the canonical OpenRune tree. No Giants' Foundry production source was changed while producing it.

## Repository authority and current state

- Canonical checkout: `<repo-root>`.
- Runtime configuration: `game.yml` selects revision `240` and environment `LIVE`.
- Runtime cache: `.data/cache/LIVE`; `.data/cache/SERVER` is another JS5 tree and must not be treated as the live definition authority without explicitly changing the environment.
- Game-value aliases and map source inputs live under `.data/raw-cache`; generated output must not be edited by hand.
- Persistent character state is stored by the PostgreSQL account repository in `.data/postgres`.
- The working tree was already heavily modified. This audit did not clean, reset, or overwrite unrelated work.
- A targeted Kotlin search found no registered Giants' Foundry or Sleeping Giants activity module. Existing references are reward-item support, raw-cache aliases/spawns, and cache data—not a complete minigame.

## How content is discovered and started

| Concern | Existing seam | Giants' Foundry use |
|---|---|---|
| Module discovery | Root `settings.gradle.kts` recursively includes directories under `api`, `content`, `engine`, and `server` that contain `build.gradle.kts`. | Add one self-contained module such as `content/minigames/giants-foundry`; do not add manual source-set hacks. |
| Script startup | `server/app/src/main/kotlin/org/rsmod/server/app/GameServer.kt` loads `PluginScript` implementations. | Register interaction/UI/queue scripts through normal plugin discovery. |
| Script contract | `engine/plugin/src/main/kotlin/org/rsmod/plugin/scripts/PluginScript.kt`. | Each activity script installs its listeners in `startup`. |
| Hook registration | `engine/plugin/src/main/kotlin/org/rsmod/plugin/module/PluginModule.kt` and module-specific Guice modules. | Bind lifecycle hooks only if scripts alone are insufficient. Model after `PestControlModule` or `BarrowsModule`. |

Recommended module boundary:

```text
content/minigames/giants-foundry/
  build.gradle.kts
  src/main/kotlin/org/rsmod/content/minigames/giantsfoundry/
    GiantsFoundryModule.kt
    GiantsFoundryScript.kt
    GiantsFoundryService.kt
    GiantsFoundryState.kt
    GiantsFoundryData.kt
    GiantsFoundryMoulds.kt
    GiantsFoundryInterface.kt
    GiantsFoundryRewards.kt
    GiantsFoundryPersistence.kt
    GiantsFoundryLifecycle.kt
  src/test/kotlin/...
```

Keep one authoritative transition service. Interaction scripts should translate clicks into service commands; they should not independently calculate temperature, quality, completion, XP, reputation, or rewards.

## Interaction and interface routing

| Area | Canonical code | Observed behavior / integration rule |
|---|---|---|
| Location clicks | `api/player/src/main/kotlin/org/rsmod/api/player/interact/LocInteractions.kt` | Resolves player-specific multilocs through `VarPlayerIntMap`, then routes coordinate, type, content-group, category, unimplemented, and default handlers. Register Foundry station ops with `onOpLoc`; register item-on-loc with `onOpLocU`. |
| NPC clicks | `api/player/src/main/kotlin/org/rsmod/api/player/interact/NpcInteractions.kt` | Resolves multi-NPCs using player vars before routing. This is required for Kovac's quest/non-quest forms. Use `onOpNpc`. |
| Item clicks | `api/player/src/main/kotlin/org/rsmod/api/player/interact/ObjInteractions.kt` | Routes object operations by type/content/default. Use standard object handlers for preform/reward actions. |
| Script APIs | `api/script/src/main/kotlin/org/rsmod/api/script/*ScriptEventExtensions.kt` | `onOpLoc`, `onOpLocU`, `onOpNpc`, `onIfModalButton`, `onIfClose`, `onPlayerQueueWithArgs`, login/logout hooks, and lifecycle events are already supported. |
| UI open/close | `api/player/src/main/kotlin/org/rsmod/api/player/ui/PlayerInterfaceExtensions.kt` | Open the native modal/overlay through supported UI APIs; close it through the matching normal close path. |
| Client scripts | `api/player-output/src/main/kotlin/org/rsmod/api/player/output/ClientScripts.kt` | `Player.runClientScript(id, vararg args)` drives the cache-native Foundry interfaces. |
| Player vars | `api/player/src/main/kotlin/org/rsmod/api/player/vars/VarPlayerIntMapSetter.kt` | Set named revision-240 varps/varbits through the generated-name map so the client receives the correct values. |

Do not add coordinate-only fallbacks for every station. Register the verified content locs and retain the normal pathing/facing behavior. Coordinate-specific routing is appropriate only where two identical objects genuinely have different semantics.

## Object state, clipping, and multiplayer safety

`api/repo/src/main/kotlin/org/rsmod/api/repo/loc/LocRepository.kt` adds, removes, or changes dynamic locations, updates clipping, and broadcasts region updates. It is global/shared state.

That API is suitable for genuinely shared world changes. It is unsafe for per-player Foundry state: two players can have different crucible contents, mould layouts, preforms, temperatures, and progress while standing in the same public workshop. A global replacement would let one player's action alter another player's visuals/collision.

Implementation rule:

1. Prefer cache multilocs driven by the verified player varps/varbits (`mould_state`, `crucible_state`, `preform_stored`, and related fields).
2. Confirm each affected loc definition's transform before implementation.
3. If a required visual is not a multloc, first prove whether the activity area is instanced in this server. If it is public, a supported player-local loc update extension is required; do not silently use `LocRepository`.
4. Keep physical clipping unchanged for visual-only state locs unless the cache definition proves a collision transition.

The static map contains the hammer, grindstone, polishing wheel, lava pool, waterfall, exit, bank, and scenery. The active crucible/jig/storage IDs are not statically placed in the decoded map, which strongly suggests server-driven or transform state and must be verified before coding.

## Tick loop and action cancellation

- `engine/game/src/main/kotlin/org/rsmod/game/entity/Player.kt` exposes `weakQueue(queueName, ticks, args)`.
- `api/script/src/main/kotlin/org/rsmod/api/script/PlayerScriptEventExtensions.kt` exposes typed `onPlayerQueueWithArgs` processing.
- Smithing's smelting, cannonball, anvil, dragon-forge, and crystal-singing scripts are proven examples of queued skilling actions.
- `PestControlLifecycle` and `BarrowsLifecycle` show player post-tick/death cleanup patterns.

Recommended Foundry scheduling:

- Represent each interaction as a command against a single state machine.
- Queue station actions with a run/session generation token. Reject stale tasks after movement, death, logout, preform storage, collection, abandonment, or a new commission.
- Evaluate temperature/progress/quality once per authoritative tick path. Do not run the same math in both an interaction queue and a post-tick hook.
- Stop on invalid station, missing preform, out-of-range movement, incompatible state, logout, death, or interface closure as appropriate.
- Make collection/reward settlement idempotent so repeat clicks and reconnects cannot duplicate items, XP, or reputation.

## RNG and deterministic testing

Use injected `api/random/src/main/kotlin/org/rsmod/api/random/GameRandom.kt` and `DefaultGameRandom.kt`. Tests can provide fixed/sequence random implementations. Do not use `kotlin.random.Random` in Foundry rules.

One existing Smithing helper (`content/skills/smithing/.../util/SmithingUtils.kt`) currently uses Kotlin random for a partial-uniform speed proc. That is an existing deterministic-testability issue, not a pattern to copy.

## Persistence

| Component | Location | Relevance |
|---|---|---|
| Persistent key | `api/attr/src/main/kotlin/org/rsmod/api/attr/AttributeKey.kt` | An attribute with a `persistenceKey` is eligible for character persistence. |
| Serialization filter | `api/attr/src/main/kotlin/org/rsmod/api/attr/AttributeMap.kt` | Only non-temporary keyed values are emitted; persistence identity is the key string. |
| Account storage | `api/account/src/main/kotlin/org/rsmod/api/account/character/main/CharacterAccountRepository.kt` | Writes persistent attribute payloads with the character account. |
| Autosave | `api/account/src/main/kotlin/org/rsmod/api/account/autosave/PlayerAutosaveOrchestrator.kt` | Persistent mutations enter normal queued/periodic saves. |

Recommended saved payload: one versioned `giants_foundry_v1` map containing only integral/string/list/map values for reputation, mould unlocks, completions, active commission, crucible alloy, preform stage, quality, temperature, progress, and a settlement token. Keep runtime service objects, timers, entity references, and floating-point values out of persistence.

On login, validate and normalize the saved state before showing visuals. If an exact resume cannot be proven safe, preserve permanent unlocks/reputation but reset only the in-progress sword transaction through one explicit recovery rule. Never grant raw inputs or rewards twice.

## Existing Smithing and reward integration

| Feature | Existing implementation | Audit note |
|---|---|---|
| Colossal blade | `api/combat/combat-formulas/.../CombatMeleeAttributeCollector.kt` and `MeleeMaxHitOperations.kt` | Recognized as `obj.giants_foundry_colossal_blade`; max-hit bonus scales +2/+4/+6/+8/+10 for target sizes 1/2/3/4/5+. |
| Double ammo mould | `content/skills/smithing/src/main/kotlin/org/rsmod/content/skills/smithing/CannonBallScript.kt` | Uses two bars to produce eight cannonballs per action; normal mould remains one bar/four balls. |
| Kovac's grog | `content/other/consumables/.../FoodEffectService.kt` | Gives +4 Smithing and drains Attack/Ranged/Magic by 2. |
| Smithing catalyst | `content/skills/smithing/.../util/SmithingBonuses.kt` and smelting integration | Halves coal at ordinary furnaces; consumption is bypassed by Smith's gloves (i). Verify the intended OSRS XP bonus before relying on current behavior. |
| Smiths' uniform | `SmithingUniformXpModifiers.kt` and `SmithingUtils.kt` | Current code gives 2.5% XP per piece plus a 20% full-set bonus (30% total) and action-delay behavior. This must be reconciled against the task's OSRS source of truth; do not compound bonuses in Foundry code. |

The normal shop API in `api/shops` is built for shop-main/shop-side. Giants' Foundry has a dedicated native interface (`giants_foundry_reward_shop`), so implement a bespoke presenter/handler while reusing atomic inventory/currency transaction primitives. `PestControlRewardService.kt` is the closest existing confirm-and-purchase example.

## Reference minigames

- `content/minigames/pest-control`: manager + match state, lifecycle hooks, bespoke reward service, and pure rules tests.
- `content/minigames/barrows`: persistent per-player run fields, world interaction service, death cleanup, loot preview, and topology tests.
- Existing raids: examples of isolated instance lifecycle only. Do not instance Giants' Foundry merely because raids do; first verify the live activity map's intended sharing model.

## Test layout and required coverage

Place pure rule tests in the Foundry module's `src/test/kotlin`; use integration tests where player/loc/interface/persistence behavior is required.

Minimum suite:

1. Commission word selection and mould scoring for every mould combination/unlock gate.
2. Alloy acceptance, bar consumption, difficulty, and no partial/duplicate consumption under repeat clicks.
3. Every legal and illegal state-machine transition.
4. Temperature thresholds, station effectiveness, quality loss, progress, completion, and uniform effects using deterministic RNG.
5. XP/reputation/reward formulas and transaction idempotency.
6. Native mould UI setup/reset/set and reward-shop purchase/insufficient-space/insufficient-points paths.
7. Relog, disconnect, death, abandonment, preform store/take, and corrupted-save normalization.
8. Two players using different station states simultaneously without loc/var leakage.
9. Build, server boot, end-to-end commission, completion, collection, spending, relog persistence, and cleanup.

## Implementation gate checklist

| Gate | Status after this audit |
|---|---|
| Canonical module and registration path identified | mapped |
| Interaction/UI/var APIs identified | mapped |
| Tick/queue/RNG approach identified | mapped |
| Persistence path identified | mapped |
| Existing reward equipment integrations identified | mapped |
| Full Giants' Foundry activity module | missing |
| Player-private station visual mechanism proven | missing; must inspect loc transforms or add supported player-local update path |
| OSRS formulas/timings/mould data implemented | missing |
| End-to-end and multiplayer tests | missing |

## Commands and evidence

Read-only commands used included targeted `rg --files`, `rg -n` searches across `api`, `content`, `engine`, `server`, `.data/raw-cache`, and `osrs-dumps`; Gradle definition/client-script queries against `.data/cache/LIVE`; and one temporary read-only cache test that decoded map square `(52,179)`. The temporary source was not production code and is removed after the audit. The definition query and cache test both completed successfully on revision 240.
