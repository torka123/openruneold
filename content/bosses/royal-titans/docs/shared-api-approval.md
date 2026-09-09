# Royal Titans: approved shared-system scope

The user explicitly approved this scoped proposal on 2026-09-08 ("approve"). Implementation is now
authorized; completion and verification must still be recorded separately. New project APIs are
not automatically official upstream APIs.
Comparison reference: `upstream/main-audit-latest` at `1c60a15d54a06b960d8c51da960988e48a4c5de0`.
The root `AGENTS.md` requires separate approval before new shared-engine integration.
That approval requirement is satisfied for the changes below. No new approval is needed merely
to implement or test this scope; materially broader systems still require a separate design.

## Implementation status

### Additional bounded approval: NPC body models (2026-09-09)

The user's `ye` approves the separately proposed native NPC body-model customization API.
Mapping before edits: content calls shared `Npc.setBodyModels` / `resetBodyModels`; `NpcAvatar`
retains an immutable model snapshot; `NpcInfoProtocol` forwards through `RspNpcInfo` to the
installed rsprot240 `setBodyCustomisation(models, emptyList(), emptyList())` and
`resetBodyCustomisations()`. Its existing persistent extended-info path handles later observers.
This is an approved project API extension, not a claim that an upstream OpenRune API already exists.

Comparable native presentation path: `Npc.transmog` -> `NpcInfoProtocol` -> `RspNpcInfo`, already
used by DagannothKingsCombat and AlchemicalHydraEncounterManager. A transmog alone is insufficient:
2283 T1558 L24086-24087 retains Branda's NPC identity but sets model55952; T1562 L24168-24169 sets
Eldric model55940. Corpse transforms explicitly reset the override at T1651 L26840 / T1806 L30751.
LIVE revision240 supplies these models; no cache edits, new NPC types or new packet encoder needed.

Scope: model lists only (no recolouring, retexturing, player mirroring or combat changes), replay
on protocol attachment, explicit corpse/deletion reset, native respawn reset, and deterministic
engine/packet/encounter/cache tests. rsprot owns flags, serialization, observer replay and pool reset.
No timing, scheduler, reward or Combat Achievement uncertainty is resolved by this visual API.

See [approved-integration-report.md](approved-integration-report.md) for source/tests/runtime results.

| Approved scope | Current state |
|---|---|
| 1 — hit provenance and cancellation | Implemented through native queues/processors and existing producers; tested. Unpublished loot policy and unimplemented ring-recoil producer are separate remaining gaps. |
| 2 — CA awards and precise observations | Synchronous shared stat observations implemented/tested. Shared persistent awards and the eleven encounter predicates remain unfinished. Cache mapping is in `shared-ca-cache-audit.md`. |
| 3 — durable rewards | Unfinished; approval remains valid. No boss-local claimed flag is presented as durable delivery. |
| 4 — unsafe death location and ownership | Existing native pile-location resolver integrated/tested; native instance deletion ownership cleanup implemented/tested. Full gravestones were not included. |
| Smaller reviewed needs | 100% freeze bug and canonical NPC params fixed; native protected name-dialog wrapper implemented/tested. Per-Titan accuracy/readiness integration remains unfinished. |

## Work that does not need a new API

Wall casting uses target interactions, the existing spell registry, rune manager and hit queues.
Final elemental refunds use `onNpcHit`. PostgreSQL completion/statistics use `GameDatabase` and
an ordinary migration. Scoreboard uses named native interface APIs. Existing `MapClock` timestamps
can express the last-defeated Titan's timing: the shared instance timer is not equivalent.
Drop-table composition already supports content-owned unique/two-standard/page/tertiary selection.
Do not request new combat callbacks or duplicate these established paths.

## 1. Typed hit provenance and cancellation

Scope: `engine/game` Hit/HitBuilder/DamageContributions and native NPC/player queue/processor APIs;
recoil, poison, venom, burn and thrall producers.

Proposed optional `HitContext` carries `HitAttribution(origin, ownerUuid, mechanic)` and typed
generation tokens. Origins are Direct/Recoil/Poison/Venom/Burn/Thrall/Environment. Mechanic labels
belong to content, not a Royal Titans enum in the engine. `HitScope.invalidate()` makes prior tokens
invalid; composed encounter and membership scopes reject hits after reset or departure/rejoin.

Processors validate before *all* impact effects, including damage, retaliation, recoil, hitmarks,
death and events. Existing unscoped behavior remains unchanged. Contribution preserves aggregate
queries and adds origin queries. Do not introduce formula overrides or per-hit callback lambdas.

Needed for original defects8–9, exact CA attribution and departure safety33–35. Recoil counts;
elementals do not. August numeric saturation remains unpublished: this API cannot prove it.

Tests: origin conservation/overkill, recoil and thrall ownership, DoT damage preserved, slot reuse,
logout/teleport/death/reset/leave-rejoin cancellation, no side effects from cancelled hits.

## 2. Shared Combat Achievement awards and precise observations

Scope: a shared CA service plus native stat-change dispatch. Reuse `onNpcHit` and existing player
HP-change events; do not replace them with boss-local polling. Emit immutable previous/current
stat levels at every mutation so a prayer drain followed by same-tick restoration is observable.

Proposed service: `award(player, taskIds, completionId)`, `hasCompleted(player, taskId)` and
`hasRewardTier(player, tier)`. It owns persistent/idempotent awards, packed task bits, totals,
points, reward status and notifications. Content owns the eleven predicates and shared duo failures.
No equivalent public service was found; Cerberus's private helper is not a shared service.

Tests: all eleven predicates, duplicate/concurrent/restarted awards, preservation of unrelated
packed bits, aggregate counts/points, claimed versus merely qualified reward tiers, same-tick
prayer loss/restoration, abandoned generations and duo attribution. Zero-hit Perfect semantics
must be proved before introducing an extra impact event solely for that purpose.

## 3. Durable reward claims through one native delivery path

Scope: `content/drops` reward service/hook, account save coordination, native ObjRepository/pickup,
ground-item reward-line identity, and a versioned PostgreSQL migration.

Proposed `allocate(request)`, `deliver(player, claimId)`, `recover(player)` APIs. Allocation freezes
one choice per encounter/account and fully resolved items/quantities/tertiaries. Recovery never
rerolls. Reuse/refactor the existing private native delivery implementation, not a boss-local copy.
The native loot-tracker event allocator is currently private and must not be replaced with a
colliding boss-local counter. Native personal ownership/lifetime, Collection Log, provenance,
tracker and broadcasts remain in the single shared path.

The account transaction must serialize with autosaves and apply committed memory state before a
later save. Existing local exclusive-save helpers require review; they are not upstream APIs.
Ground merging, pickup and recovery must retain durable reward-line identity. A SQL claimed flag
around a transient ground spawn alone cannot prevent both crash loss and duplication.

Tests: both corpses/double-clicks/duo, rollback, concurrent allocation, restart without reroll,
crash injection at allocation/spawn/pickup/account-save boundaries, stack merging, expiration,
inventory capacity, and exactly-once log/broadcast/tracker effects where supported.

## 4. Unsafe-death destination and instance ownership cleanup

Scope: review the existing local `PlayerDeathDropLocationHook`, native death delivery, and the
instance manager's NPC deletion listener. Preserve all unrelated PlayerDeath/Vorkath work.

Standardize the existing location resolver rather than adding another hook. Resolve outside-entry
coordinates while original death/instance context exists; preserve native item-loss decisions.
No native gravestone service was found: moving a death pile is not gravestone parity and must not
be described as such. A complete grave service would require its own bounded design.

Prefer an internal instance fix listening to native NPC deletion over a new `detachNpc` API:
remove owned NPC object identities and saved registration UIDs on deletion. Content continues
using `NpcRepository.del`.

Tests: outside destination before instance destruction, unchanged unsafe penalties, duplicate
cleanup, transformed corpses, repeated kills, timers/hitboxes/minions and reused slots.

## Smaller reviewed API/bugfix needs

- Per-Titan ranged accuracy needs a legitimate shared-formula integration. The inherited
  `incomingRangedAccuracyMultiplier` field is fork-only; no new use is classified as official.
- Shared 100% freeze resistance currently clamps duration to one tick. The shared calculation
  must return an immune result for100%, with existing partial-resistance tests retained.
- `ProtectedAccess` has public string input but no equivalent protected name-dialog wrapper.
  Capture3402 T1165 sends script109 and resumes a name input. A native `nameDialog` wrapper must
  preserve protection/modal validation and support verified remembered-name behavior. Do not
  access private coroutine resumption internals from the boss.

Approval permits designing/testing these shared changes; it does not establish unknown OSRS
patrol/action-budget rules, safe-tile distributions, exact full-loot saturation, unique topology,
late-join equality or grave behavior. Those remain separate evidence requirements.
