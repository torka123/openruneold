# Sailing persistence model

`.data/postgres` and the existing account/autosave pipeline are authoritative. Client varps/varbits mirror state but never own it.

## Durable account state

- Sailing XP, level-derived unlocks, settings, tutorial/quest stages, unlock bits.
- Owned boat slot count, boat identities, selected boat, last-used boat, and recovery/last-dock state.
- Captain's Log charting/task/filter data, trial unlocks/PBs, schematic/reward/collection states.
- Active Courier/Bounty assignments and their immutable generation seed/source metadata.
- Crew roster, stats, assignments, and command state that is defined to survive logout.

## Durable boat aggregate

- Stable boat ID, owner account ID, class, name, creation/version timestamps.
- Location state: moored dock/mooring, last safe gangplank, world, recovery status. Raw live world-entity slot is never durable identity.
- Core components, facilities, slot order/masks, customization, sail/trim/flag.
- Hitpoints/damage and other live-persistent stats.
- Cargo inventory and capacity version.
- Crew assignments and owner-defined permissions.
- Aggregate version and last completed operation token.

## Runtime-only state

- Protocol slot/avatar, deck-region allocation, observer set, current fine coordinate/angle, interpolation output.
- Input queue, active helmsman lock, temporary facility lock, transient animation/audio state.
- Reconstructable encounter/combat targets unless a specific activity requires a durable checkpoint.

## Transaction boundaries

The following are single database transactions with an idempotency token:

- buy/create/recover/destroy boat;
- build/replace/remove/bottle/reorder component or facility;
- cargo deposit/withdraw/loss/transfer;
- accept/cancel/complete task and grant its reward;
- hire/dismiss/assign crew;
- quest stage/reward transitions;
- charting/trial/reward/collection unlocks; and
- capsize/death/quick-transport recovery decisions.

Inventory, bank, cargo, XP, collection log, and boat rows commit together when one action affects them. Output occurs only after commit.

## Login and recovery

1. Load and validate durable aggregates before exposing Sailing actions.
2. Resolve any incomplete operation token idempotently.
3. Reconcile duplicate/stale live leases; never spawn a second runtime boat.
4. If a valid aboard checkpoint can be reconstructed exactly, restore boat then passengers and rebuild observers.
5. Otherwise place the player/boat at the recorded safe dock using the reason-specific cargo policy.
6. Re-derive all client vars/interfaces from server state.

Exact moving-boat logout/passenger semantics remain `BLOCKED_BY_CAPTURE`; until resolved, unsafe restoration paths remain disabled and recover safely to shore without duplication.

## Exit policies

- Normal dock/disembark: retain boat/cargo and update safe location.
- Quick transport: destroy only the live-defined task cargo, bounty items, salvage, and stored fish; do not wipe unrelated state.
- Capsize: retain player inventory, lose cargo, return to last gangplank, remain HCIM-safe, do not implicitly clear poison.
- Player death aboard: normal death rules, gravestone linked to last gangplank, cargo lost.
- Logout/disconnect/hop/server stop: checkpoint or safe-recover according to captured rule; always release runtime locks/slots once.

## Concurrency and anti-duplication

- Optimistic aggregate version on every mutation.
- Unique live lease per boat ID and unique active-boat reference per player ID.
- Inventory/cargo operations use existing transactional containers, never copy-then-delete.
- Rewards and task completion use immutable operation tokens.
- Cleanup/recovery can be retried any number of times with the same result.
- Passenger/captain changes serialize on the boat aggregate.

## Migrations and tests

Schema changes are versioned and default old accounts safely. Never infer owned boats from transient vars. Test forward migration, partially populated rows, unknown cache definition, rollback, commit/output crash, restart recovery, and duplicate tokens. Preserve backups before destructive schema work.

## Implemented ownership foundation (2026-08-28)

The first durable slice is intentionally smaller than the final aggregate above:

- `V30__character_sailing_boats.sql` stores at most five rows per character, keyed by slot `0..4`.
- `boat_type` stores exact symbolic revision-240 DB-row keys (`dbrow.sailing_boat_raft`, `dbrow.sailing_boat_skiff`, or `dbrow.sailing_boat_sloop`). Numeric cache IDs, world-entity protocol IDs, and enum order are not persisted.
- `format_version` is currently exactly 1 in application validation. Unknown versions and unknown symbolic types fail account load/save closed.
- The account pipeline loads ownership before player exposure and saves it inside the existing account transaction.
- Runtime mutations replace an immutable snapshot through compare-and-set and notify the existing autosave orchestrator exactly once per successful material change.
- Temporary aboard and helm-lock varbits are sanitized independently on login/logout and never delete durable ownership.

Verified tests cover the migration and a PostgreSQL save/load/apply round trip, sorted immutable snapshots, duplicate/invalid/future rejection, mutation/no-op notification behavior, and reconnect sanitization. Durable component, cargo, crew, task, location/recovery, and operation-token tables are not implemented yet.
