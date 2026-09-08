# Giants' Foundry state-machine and transaction design

Research pin: **2026-08-27**. Design review: **2026-08-28**. This is a production
design, not evidence that the feature has been implemented. Source facts and confidence are in
[`wiki-research.md`](wiki-research.md), [`wiki-data.json`](wiki-data.json), and the companion
evidence/cache documents. Any value marked `UNRESOLVED` must remain data-gated and must not be
presented as OSRS parity until its focused parity test passes.

## Architectural decision

Giants' Foundry is per-player content in a shared, non-instanced area. One flat enum is not enough:
a player can process one preform while already loading the crucible for the next sword. Use one
authoritative per-player aggregate with four orthogonal state machines and derive client/UI state
from it:

1. `AccessProgress`: quest/tutorial eligibility and tutorial checkpoint.
2. `CommissionState`: assignment, descriptors, elapsed-time origin, and hand-in identity.
3. `CrucibleState`: independent metal contents, capacity revision, and confirmation token.
4. `PreformState`: no preform, pouring, ready/equipped, processing, stored, complete, or pending
   hand-in.

`RuntimeAction` and `OpenInterface` are transient projections. They are never the authority for
commission, mould, metal, heat, progress, quality, unlock, reputation, or reward state.

The implementation should live in a dedicated `content/minigames/giants-foundry` module and follow
the repository's existing `PluginScript`, `ProtectedAccess`, `PlayerPostTickHook`, persistent
`AttributeKey`, RSCM mapping, `GameRandom`, and `invTransaction` patterns. Do not introduce global
per-machine ownership: many players may use the same world object, while their state and tick
reducers remain isolated.

Concrete OpenRune seams verified by the repository/cache audit:

- route loc/NPC/item clicks through `LocInteractions`, `NpcInteractions`, and `ObjInteractions`,
  registering `onOpLoc`, `onOpLocU`, `onOpNpc`, and native interface button/close handlers;
- schedule exactly one authoritative tick path with player `weakQueue` plus a typed
  `onPlayerQueueWithArgs` command, or one lifecycle hook—not both;
- use cache-native interfaces 718 (mould), 753 (shop), and 754 (HUD), the named components, and
  generated var names; server state is committed before var/UI synchronization;
- project crucible/mould/preform visuals through verified player varp/multiloc transforms. The
  global `LocRepository` broadcasts and changes clipping, so it must not represent private Foundry
  progress. The exact transforms for locs 44622–44629 and 44633–44634 are still a cache gate;
- use injected `GameRandom`, never `kotlin.random.Random`, for commission, sweet-opportunity, shop
  pack, or uniform rolls.

## Authoritative aggregate

Persist one versioned, integer-only codec (`giants_foundry_state_v1`) rather than many independently
saved mutable attributes. OpenRune persistence normalizes JSON numbers and explicitly discourages
persistent floating point values. Suggested shape:

```text
FoundrySaveV1
  schemaVersion
  access: { questState, tutorialStage }
  economy: { reputation, unlockedMouldBits, collectionFlags }
  records: { swordsCompleted, personalBestQuality, personalBestElapsedTicks }
  commission: null | {
    commissionId, descriptorA, descriptorB, assignedAtEpochMillis,
    selectedForte, selectedBlade, selectedTip, selectionRevision
  }
  crucible: { bronze, iron, steel, mithril, adamant, rune, revision }
  preform: null | {
    commissionId, lifecycle, mouldScore, metalScore, maxQuality, quality,
    sectionPlan, sectionIndex, sectionProgressUnits, totalProgressUnits,
    heatUnits, locationState, sweetSeed, sweetPlan, sweetConsumedBits,
    lostQualityUnits, revision
  }
  pendingOperation: null | PendingOperation
```

Use fixed-point integer units for heat, progress, quality repair and any fractional machine delta.
Choose the scale only after the interface varbit ranges and evidence ledger are mapped; the scale
must exactly encode at least 2.5%, 1.7%, and 1.5%. Formula functions should use integer/rational
arithmetic and document every floor. Persist RSCM-stable domain ordinals or versioned logical IDs,
not unvalidated raw cache IDs.

Keep these transient and never restore them as running work after login/restart:

- open modal/HUD interface and client selection cursor;
- route, animation lock, face direction and current object instance;
- current machine/temperature action and next-cycle tick;
- sweet-spot visual window and one-tick packet dedupe set;
- warning dialogue cursor and pending input-X text entry.

Persist the commission, selected moulds, crucible, entire preform, sweet schedule/consumption,
reputation, mould unlocks, permanent purchases, personal bests, and a pending economy operation.

## Primary state machine

These are derived primary states; guards examine all four submachines.

| State | Durable condition | Legal next states |
|---|---|---|
| `NOT_INTRODUCED` | Sleeping Giants not started/completed | `TUTORIAL_IN_PROGRESS` only through verified quest entry |
| `TUTORIAL_IN_PROGRESS` | persisted tutorial checkpoint | next verified checkpoint, `ELIGIBLE_IDLE` at quest completion |
| `ELIGIBLE_IDLE` | eligible, no commission/preform | `RECEIVING_COMMISSION`, `LOADING_CRUCIBLE` |
| `RECEIVING_COMMISSION` | transient assignment lock | `COMMISSION_ACTIVE`; returns to prior state if interrupted before commit |
| `COMMISSION_ACTIVE` | valid commission, no preform | `CONFIGURING_MOULDS`, `LOADING_CRUCIBLE`, `READY_TO_POUR` |
| `CONFIGURING_MOULDS` | mould UI projection | `COMMISSION_ACTIVE` or `READY_TO_POUR`; closing UI does not clear selection |
| `LOADING_CRUCIBLE` | crucible interaction projection | prior durable state; may coexist with an existing preform |
| `READY_TO_POUR` | commission + exactly required contents + three valid selected moulds + no preform | `POURING` |
| `POURING` | transient action plus bound snapshot | `PREFORM_CREATED` on commit; `READY_TO_POUR` on pre-commit interruption |
| `PREFORM_CREATED` | durable preform, not yet picked up | `PROCESSING_ACTIVE` or `SUSPENDED_STORED` after verified pickup/storage rules |
| `PROCESSING_ACTIVE` | equipped preform, valid section not complete | `HEATING_OR_COOLING`, `USING_WORKSTATION`, `SUSPENDED_STORED`, `SWORD_COMPLETE` |
| `HEATING_OR_COOLING` | transient temperature action | `PROCESSING_ACTIVE`, `SUSPENDED_STORED` |
| `USING_WORKSTATION` | transient machine action | `TRANSITIONING_STAGE`, `PROCESSING_ACTIVE`, `SUSPENDED_STORED`, `SWORD_COMPLETE` |
| `TRANSITIONING_STAGE` | atomic reducer reached section threshold | next `PROCESSING_ACTIVE` section or `SWORD_COMPLETE` |
| `SUSPENDED_STORED` | preform persisted in storage | `PROCESSING_ACTIVE` on valid retrieval |
| `SWORD_COMPLETE` | final section complete or quality zero | `READY_FOR_HAND_IN`, `SUSPENDED_STORED` where supported |
| `READY_FOR_HAND_IN` | complete sword bound to active commission | `HAND_IN_PENDING` |
| `HAND_IN_PENDING` | computed, idempotent reward journal exists | `REWARDS_AWARDED` after atomic mutation |
| `REWARDS_AWARDED` | operation committed; old commission consumed | `ELIGIBLE_IDLE` or direct `RECEIVING_COMMISSION` |

`CANCELLED_OR_RESET` is an administrative/recovery outcome, not a normal player option. Current
evidence is high confidence that Kovac refuses commission cancellation. A player must finish, ruin
and hand in, or store the preform. Never expose a normal cancel transition.

## Transition contract

Every public handler calls one reducer under a per-player Foundry mutation guard. The reducer takes
`state + validated command + current game cycle` and returns `new state + effects`. Effects are
executed once, without suspension between inventory/state mutations. Each accepted transition
increments the aggregate/preform/crucible revision. A delayed action must carry the expected
revision and abort if it is stale.

Illegal commands are side-effect free. They close or resynchronize a forged/stale interface when
needed and send at most one normal message; they never partially mutate items, points, XP, or
state. Object handlers must validate the real revision-240 object at the expected coordinate/plane,
route reachability, region, option, player state, and action range.

### Access and commission

- Assignment requires completed Sleeping Giants, no active commission, no pending reward, and no
  assignment lock. Generate one of the 12 validated descriptor pairs with injectable `GameRandom`.
  Persist the commission and timer origin before showing dialogue/UI. A repeated click returns the
  same commission.
- The exact commission RNG algorithm is medium confidence. Equal choice among the 12 valid pairs is
  the best-supported current hypothesis (officially “no weights”), but must be labelled as such and
  statistically tested rather than claimed exact.
- The commission timer begins at assignment and storage does not pause it. Persist an absolute
  origin plus an overflow-safe elapsed calculation. Whether offline/server-down time counts is not
  explicitly published; best-supported safe behavior is to continue elapsed wall time, with a
  focused live parity test before claiming exactness.
- Mould selections are server-side logical IDs. Every button validates slot, default/unlock state,
  and selection revision. Current/boosted Smithing requirement is checked at pour, not purchase;
  an already-poured preform remains processable if the boost falls.

### Crucible and material conversion

- Crucible state is per player, persistent and independent of the current preform. Capacity is 28
  bar-equivalents. Bars add 1; validated smithable equipment adds `original bar cost - 1`.
- Validate exact slot, item ID, item vars, quantity, current Smithing level, accepted tier,
  conversion amount, crucible revision, and capacity before removal. Notes are rejected until the
  unresolved noted-input behavior is verified.
- Overflow must open a confirmation bound to `{slot,id,vars,count,conversion,crucibleRevision}`.
  Accepting a stale confirmation does nothing. On confirmed overflow, consume exactly the intended
  source item/quantity and add only up to capacity; the verified excess is deliberately destroyed.
- Emptying the crucible uses a similarly revision-bound warning and deliberately destroys all
  contents. There is no silent “refund” path.
- Use one `invTransaction` for every inventory removal. Do not update crucible state unless it
  succeeds. Do not suspend between successful inventory commit and aggregate update. Repeated
  item-on-object packets in one cycle are deduplicated; a changed slot snapshot invalidates the
  command.
- The third-metal rule is not rejection: all accepted material consumes capacity, but only the two
  largest quantities score, with higher tier winning ties. The other metal scores zero.

### Pouring and preform projection

Pour requires an active commission, complete/valid mould selection, exact required contents,
current Smithing levels for all selected level-gated moulds, no existing preform, and no pending
operation. Bind `POURING` to commission, mould-selection and crucible revisions. The safe
pre-evidence interruption rule is **commit at the verified completion tick**: movement or a new
action before that tick cancels the pour without consuming metal. Once committed, crucible
consumption and preform creation are one no-yield mutation; later packets only resynchronize.

The preform is the authority. The equipped preform item/model is a projection and must carry enough
server identity to reject an unrelated/forged item. Pickup requires a documented cooling item and
the ability to equip the two-handed preform. If unequipping the player's weapon/shield cannot be
completed atomically because inventory is full, block pickup before any change. Do not drop worn
items or the preform as a fallback.

### Processing scheduler

Only one `RuntimeAction` may exist per player:

```text
token, expectedPreformRevision, kind, stationLogicalId, boundObject,
startedCycle, nextCycle, cadence, accelerationStep, expectedSection
```

Each due cycle revalidates player/region/position, the bound object, equipped preform projection,
section, required station, and token. Invalidity cancels the action and animation immediately.
Machine cadence from current evidence is trip hammer 5 ticks, grindstone 2, polishing wheel 2.
Exact heat boundaries, temperature acceleration, passive cooling, and within-tick ordering remain
unresolved and must be supplied by the evidence ledger/test vectors; do not hide a guessed order in
the scheduler.

One machine click while already working has two mutually exclusive outcomes:

1. If a live sweet window for that station is claimable, consume it once and arm the next successful
   action bonus.
2. Otherwise set a Boolean `skipNextSuccessfulAction`. Repeated packets cannot stack multiple skips.

On the next due action, a skip consumes itself and grants no progress without quality damage.
Sweet opportunities are selected from an injectable deterministic seed at pour, persisted as a
plan, and consumed monotonically so logout/relogin cannot reroll them. On disconnect/storage, an
open visual window expires; it is not restored or refunded (safe anti-reroll choice, medium-low
parity confidence). The armed bonus should persist only if evidence shows it does; otherwise expire
on suspension and record this as a deliberate low-confidence choice.

Progress, heat and quality are clamped after each atomic reducer. `quality <= maxQuality`, progress
cannot regress, completed swords cannot return to processing, and quality-zero work is blocked.
The current sources conflict between 10 quality lost per damaging action and 5 per mistake. Keep the
penalty data-gated and fail its parity test until resolved; do not pick one silently. The 2025 sweet
spot quality repair amount and exact bonus progress are likewise unresolved.

### Hand-in and economy exactly-once contract

Forged hand-in is rejected unless the complete/ruined preform is bound to the active commission.
At acceptance, compute immutable rewards from server quality and store a `HAND_IN_PENDING` journal:

```text
transactionId, commissionId, expectedStateRevision, quality,
xp, coins, reputation, runEnergyDelta, stage
```

Then, in one game-thread, no-yield mutation:

1. Revalidate journal and commission/preform identity.
2. Apply Smithing XP, bank/inventory coins according to account mode, reputation, run energy,
   statistics and collection state.
3. Consume preform and commission, mark the transaction ID applied, and transition to awarded.
4. Queue the normal account save only after the complete snapshot exists.

OpenRune's account saver writes character fields, persistent attributes and permanent inventories
through one database transaction. Therefore inventory/economy/state must be mutated as one coherent
in-memory snapshot; no coroutine delay, dialogue, external callback, or second command may observe
the middle. On login, a pending journal is reconciled by transaction ID and state rather than
blindly paying again. The result screen is an effect after commit and reopening it never pays.

Use the verified formulas exactly:

```text
xp = (floor(q*q/73) + floor(3*q/2) + 1) * 30
coins = 2 * xp
reputation = q
run energy = min(cap, old + 50 percentage points)
```

Quality zero is the explicit 30 XP/60 coin outcome. Normal coins go to bank (including a valid
placeholder); Ultimate Ironman coins go to inventory. If the UIM inventory cannot accept the coin
stack, keep `HAND_IN_PENDING` and pay nothing until capacity exists; the exact OSRS full-inventory
branch remains unresolved, so this conservative choice prevents loss.

### Reward shop transactions

All button commands bind to the current shop interface nonce, server-side reward definition,
quantity, reputation balance and state revision. Bank PIN protection must be checked server-side.

- Item purchase: simulate inventory add and reputation debit; commit both or neither. Full
  inventory is not failure when an exact stack can accept the item. A repeat packet sees the new
  revision/balance and cannot replay the original debit/add.
- Mould purchase: validate cost and absence from unlock bitset, then atomically debit and set the
  permanent bit. It creates no inventory item. Buying below the Smithing requirement is allowed.
- Double ammo mould additionally requires Dwarf Cannon completion.
- Buyback: validate the exact eligible inventory item, exclude grog/Colossal blade and moulds, then
  atomically remove the item and credit 80% reputation. Never credit before removal succeeds.
- Exact duplicate rules for unique physical rewards are unresolved. Do not invent ownership gates;
  retain the server's data-defined policy and mark parity pending until observed.

## Lifecycle recovery policy

High-confidence OSRS behavior takes priority. For unresolved catastrophic events, use the safest
non-exploitable behavior and label it as a security/engine-limit decision:

- Area exit or teleport: cancel transient action, remove equipped projection, persist preform in
  storage with heat/progress/quality unchanged, keep timer running.
- Logout/network loss/world hop: same auto-store operation before save; on next login validate the
  codec, reconcile pending operations, send the documented storage reminder, and restore no action
  locks. World-hop specifics are unresolved, but treating it as logout prevents dupes/rerolls.
- Controlled server shutdown/restart: the same persisted stored state survives; never restore a
  scheduled action. Server-restart semantics are unresolved in public sources.
- Forced death: Foundry is safe/non-combat. If another system kills the player, auto-store the
  preform before death inventory handling and never place it in drops. This is a documented safe
  divergence until live behavior can be reproduced.
- Manual storage: preserve heat, progress, quality, section and commission; stop action; timer
  continues.
- Interface close: only discard interface nonce/cursor. It must not clear commission, crucible,
  selection or preform. Closing the HUD does not authorize progress changes.
- Movement/new activity: stop in-flight machine/temperature action at a cycle boundary with no
  partial tick. Starting an unrelated protected action cannot consume materials/rewards.
- Corrupt/future codec: quarantine the aggregate, deny economic mutations, and log one structured
  recovery error. Never default a malformed non-empty save to an empty state, which could duplicate
  an equipped/stored preform or repay a commission.

## Invariants

The reducer and tests must enforce:

1. At most one active commission and one preform per player.
2. A preform's `commissionId` always equals the active commission.
3. Crucible total is in `0..28`; every tier count is non-negative.
4. Exactly three valid mould slots are selected before pour.
5. `0 <= quality <= maxQuality <= validated cap`; heat/progress stay in validated ranges.
6. Section index and plan agree; final/ruined swords never regress.
7. One action token and one pending economy operation maximum.
8. Every item/XP/coin/reputation mutation has one operation identity and monotonic revision.
9. No client value influences formulas or state other than selecting a validated command.
10. No Foundry state is shared between players; shared machines carry no player progress.
11. A failed inventory transaction leaves aggregate and balance unchanged.
12. Login/reset cancels all stale jobs and cannot re-roll commission or sweet opportunities.

## Baseline repository status

Before Foundry production changes, the repository baseline was already not globally green:

- `./gradlew.bat build --no-daemon --console=plain` failed in pre-existing Spotless checks in
  `api:music-plugin`, `api:game-process`, `api:invtx`, and `api:music` (including
  `MusicAreaScript.kt`, `MusicTimerScript.kt`, `NpcPlayerHuntProcessor.kt`,
  `InvTransactionExtensions.kt`, `InvTransactionsScript.kt`, `VirtualInvTransactions.kt`,
  `Music.kt`, and `MusicRepository.kt`).
- `./gradlew.bat test --no-daemon --console=plain` failed in pre-existing tests:
  `api:player` `BlowpipeAmmoTest` (dragon-dart packed contents, `ArrayIndexOutOfBoundsException` at
  line 55), and `or-cache` `PohInterfaceAuditTest` / `PohViewerGamevalAuditTest`
  (`ArrayIndexOutOfBoundsException` at line 13).

Foundry verification must report these separately, run the Foundry-scoped suites directly, and
compare final global failures to this baseline. It must not silently modify unrelated files merely
to make the global task green.

## Open decisions that block a 1:1 claim

| Decision | Evidence/confidence | Safe behavior before resolution |
|---|---|---|
| Exact heat bands and scaling floors | unresolved/low | data-gate; no parity claim |
| Temperature acceleration/passive cooling/tick order | unresolved/low | deterministic reducer selected only after video vectors |
| Wrong-action quality loss: 10 vs 5 | current-source conflict | fail focused test; do not silently choose |
| Sweet RNG, ~5% bonus and repair amount | medium-low | precomputed seed/monotonic consumption; data-gate values |
| Commission RNG internals | medium | equal valid-pair choice; label hypothesis |
| Noted crucible input | unknown | reject without consuming |
| Offline/world-hop/restart timer semantics | unknown/medium | persist and continue wall-clock elapsed |
| Forced death | unknown | auto-store, never drop |
| UIM/full-inventory hand-in | partially unresolved | keep pending, grant nothing until atomic success |
| Duplicate unique shop purchases | unknown | data policy; no guessed gate |
| Ore-pack exact distribution | empirical/medium | seeded table from selected evidence; statistical test and disclosure |
