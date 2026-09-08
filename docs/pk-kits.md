# PK kit system

The PK kit plugin lives in `content/other/pk-kits`. It provides validated server starter kits and
versioned, player-owned custom kits through OpenRune's normal plugin, command, protected-access,
inventory, bank, combat, prayer, spellbook, and character-attribute systems.

## Player commands

- `::kits` opens the dialogue-based kit manager.
- `::loadkit <kit-id>` loads a premade kit.
- `::loadkit <slot>` loads a one-based custom slot.
- `::loadkit` loads the player's configured default kit.
- `::savekit <slot> <name>` saves the current inventory, equipment, stats, and supported settings.
- `::deletekit <slot>` opens a confirmation dialogue before deletion.

## Administrator commands

- `::reloadkits` rebuilds and validates the starter registry.
- `::validatekits` reports every invalid starter definition.
- `::kitinfo <kit-id-or-slot>` reports item policy, risk, stats, and resolved contents.
- `::forcekit <player-name> <kit-id>` bypasses location/state restrictions, but keeps ownership,
  bank-capacity, forbidden-item, snapshot, and rollback protections.
- `::kitslottier <0-4> [player-name]` selects capacities 5, 7, 10, 15, or 20.

Only `NONE`, `MODERATOR`, and `ADMINISTRATOR` account rights exist in this project. There is no
donator-rank model to bind safely. The persistent `slotTier` field and `::kitslottier` command are
the integration point for a future rank provider. Reducing the tier locks higher slots without
renaming or deleting them.

## Starter kits

The registry contains these stable IDs:

- `pure_melee`, `pure_range`, `pure_tribrid`
- `zerker_vengeance`, `zerker_tribrid`
- `med_vengeance`, `med_tribrid`
- `main_vengeance`, `main_tribrid`
- `max_melee`, `max_tribrid`
- `dharok`, `range_tank`
- `lms_pure`, `lms_zerker`, `lms_med`, `lms_main`

All item keys are revision-240 game values. Startup and `::reloadkits` validate cache resolution,
quantities, unique inventory/equipment slots, wear slots, stats, settings, and item policy before a
definition becomes selectable.

## Item policy and transactions

The supported policies are `FREE_SPAWN`, `REQUIRE_OWNERSHIP`, and `MIXED`. Item overrides are
`INHERIT`, `FREE_SPAWN`, `REQUIRE_OWNERSHIP`, and `DISALLOWED`. Free spawning uses a conservative
explicit allowlist because this project has no pre-existing economy-wide spawnability service.
Dummy, certificate, placeholder, transformation, non-bankable, container, and explicitly unsafe
items are rejected.

Each load is synchronous and guarded by both protected access and a per-player transaction lock.
Before mutation, the planner:

1. validates player state, definition data, quick prayers, items, and exact item variants;
2. reserves reusable held free items without consuming equivalent bank stock;
3. resolves owned quantities across inventory, equipment, and bank without double counting;
4. computes the exact final inventory, equipment, and bank arrays;
5. rejects missing ownership, quantity overflow, forbidden items, or insufficient bank capacity.

The service then snapshots all three item containers, player varps/varbits, seven combat stats, run
energy, and combat level. Any exception restores that snapshot. Failure logs include the player,
kit ID, mutation stage, exception, and whether rollback succeeded. No floor-drop or temporary
container fallback exists.

## Persistence

Custom data uses the existing persistent character-attribute pipeline under
`pk_kit_data_v1`. The schema stores:

- schema version, slot tier, and default kit reference;
- stable custom ID, slot, sanitized name, category, policy, and timestamps;
- exact inventory/equipment slots;
- stable item game-value key, cache ID, count, packed `InvObj.vars`, and item policy override;
- seven combat stats;
- spellbook, prayer-book marker, quick-prayer mask, stance/style, autocast selection, special
  energy, run energy, and run toggle.

Unknown newer schemas are left untouched and locked. Malformed kits or missing cache items are
ignored with warnings instead of crashing login or deleting the rest of the save.

## Deliberate version-one limits

OpenRune currently exposes only the standard OSRS prayer book, so the schema includes a versioned
prayer-book field but accepts only `STANDARD`. Rune pouch, looting bag, bolt pouch, quiver, pet
storage, charged virtual containers, and other nested storage are excluded because the current
project does not expose a safe transactional serializer for them. Their container items are
forbidden rather than risk duplication or metadata loss. Supported `InvObj.vars` metadata is
preserved exactly.

The interface is implemented with existing dialogue and string-input components, so no new client
interface or button IDs are consumed. It includes premade/custom pages, exact item requirement
previews, stats, spellbook/prayer book, estimated risk, live readiness/missing-item reasons, load,
save/overwrite, rename, confirmed delete, default selection, and help.

## Runtime smoke test

1. Log in outside the Wilderness and combat; run `::kits`.
2. Preview and load `pure_melee`, `pure_tribrid`, and `dharok`.
3. Save with `::savekit 1 My test`, move items, then use `::loadkit 1`.
4. Remove an ownership-required item and confirm load cancels without changing held or bank items.
5. Fill all usable bank slots and confirm a load needing a new slot cancels unchanged.
6. Double-click Load and confirm only one transaction runs.
7. Set slot 20 at tier 4, downgrade with `::kitslottier 0`, and confirm slot 20 remains stored but locked.
8. Relog and repeat `::loadkit 1`; restart the server and repeat it again.
9. In the Wilderness and during combat, confirm normal load is blocked.
10. As an administrator, run `::validatekits`, `::kitinfo dharok`, and
   `::forcekit <player-name> pure_melee`.
