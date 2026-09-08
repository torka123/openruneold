# Sailing architecture

## Design rule

Sailing is a persistent native skill built on moving world entities. Content scripts may request actions, but they must not own protocol state, duplicate boat definitions, or bypass the central authority layers.

## Layering

1. **Cache definitions**
   - Revision-240 gamevals, DB rows/tables, interfaces, transforms, sequences, and map placements.
   - Static data is decoded once into typed registries. Kotlin constants are permitted only for verified protocol invariants or documented capture outputs.
2. **World-entity engine**
   - Entity slot/lifecycle, fine coordinates, angle, footprint, deck region, observer protocol, rebuilds, collision surface, and spatial index.
   - This layer is content-neutral and must support more than boats.
3. **Boat aggregate**
   - Persistent boat identity plus class, owner, components, facilities, appearance, cargo, HP, location, mooring, passengers, crew, and current orders.
   - One aggregate mutation is committed atomically before client output is sent.
4. **Navigation service**
   - Pure motion intent, revision/hash-bound sailable-tile authority, full swept static collision, validated captain input, sail/speed state, acceleration, turning, residual movement, hull/boat collision, currents, hazards, mooring, and camera/output state.
   - Kinematics is deterministic from a frozen input stream.
5. **Skill/activity services**
   - Shipbuilding, tasks, charting, salvaging, trials, trawling/fishing, encounters, combat, crew, quests, island content, rewards, collection log, XP, and mastery.
   - Services query central registries and the boat aggregate instead of creating activity-specific boats.
6. **Presentation controllers**
   - Native interface 934/935/937/938/939/943/944 controllers, component validation, client scripts, var state, dialog/camera/audio, and reconnect restoration.
   - Every click is checked against open interface, component, sub-index, player/boat authority, and current state.

## Core authorities

- `WorldEntityRegistry`: assigns and releases protocol slots; emits create/delete once.
- `BoatRepository`: durable source for owned boats and transactional aggregate updates.
- `BoatRuntimeRegistry`: maps a durable boat identity to at most one live world entity.
- `BoatAccessPolicy`: captain/passenger/friend/group permissions and capacity.
- `BoatNavigationService`: single writer for heading, velocity, collision, and mooring state.
- `SailingDefinitionRegistry`: typed revision-240 DB/cache definitions.
- `ReleasedSkillMastery`: cache-backed released-stat enumeration and shared mastery checks for skillcape/Max cape content; unreleased future stats never affect live eligibility.
- `SailingSessionService`: login/rejoin/logout/world-hop/death/teleport/capsize recovery.
- `SailingTaskService`: persistent Courier/Bounty assignment and turn-in state.
- `SailingRewardService`: atomic rewards, uniques, pets, collection-log unlocks, and XP.

## Runtime invariants

- A durable boat identity has zero or one live world entity.
- A player is on zero or one boat, and a boat contains no duplicate passenger identity.
- One captain/helmsman controls a boat at a time.
- Only the owner or an explicitly authorized actor can mutate components, facilities, cargo, crew, mooring, or navigation.
- Passenger count never exceeds class capacity.
- World-entity slot 0 is reserved; slots 1 through 4095 are unique.
- The player's real coordinate remains on deck instance land while the root view follows the boat.
- Output events follow committed state and are safe to rebuild for a late observer.
- Despawn cannot occur while passengers remain; cleanup evacuates/reconciles them first.
- Cargo/task-item loss uses a reason-specific policy. It never applies a broad inventory wipe.
- All random outcomes use a recorded server seed/context suitable for deterministic tests.

## Tick order

1. Drain and validate input.
2. Resolve helm/sail/facility/crew orders.
3. Calculate intended speed, turn, and fine movement.
4. Apply world bounds, water collision, boat footprint, entity collisions, currents, hazards, and mooring constraints.
5. Commit boat/world-entity state.
6. Update passenger root views and build areas.
7. Publish protocol updates, facility animations, side-panel vars, and sounds.
8. Persist dirty aggregates through the normal autosave/checkpoint path.

Exact ordering that is not exposed by cache metadata remains `BLOCKED_BY_CAPTURE`; the imported PR order is regression-locked but not treated as OSRS proof.

## Static versus runtime data

Revision-240 runtime collision is not a water classifier: a zero flag can be walkable land or open water, while `CollisionStrategy.Blocked` rejects sampled valid water. Sailable tiles and rotated hull masks therefore require immutable, checksummed capture fixtures that deny unknown coordinates. DB boat-size labels and deck rebuild-zone dimensions are never substituted for root-world hull geometry.

Dock table 194 is authoritative for 61 identities and their name/level/quest/sprite metadata. `SailingDockRegistry` joins those rows to the authoritative SERVER-map placements and exposes exact row, loc, and coordinate indexes for 64 parents and 62 buoys. Static placements establish interaction identity, not operational return/spawn/anchor/rotation geometry; uncaptured dock travel fails closed.

Use DB tables for boats (166), docks (194), facilities (176), combat facilities (162), charting (154), shipwrecks (203), trials (143), and seas (201). The manual `Docks` catalog has been removed. The remaining hard-coded `BoatTypes` and `DeckLocs` are migration scaffolding and must be replaced or cross-validated row by row.

Dynamic data includes:

- active world-entity slot and protocol avatar;
- root fine coordinate, angle, speed, target speed/angle, residuals, and collision state;
- active captain/passengers and facility locks;
- encounter/task/trial session state; and
- pending transactional operations and recovery token.

## Interaction security

Every packet/action is rejected unless all checks pass:

- input range and opcode/component validity;
- expected interface or loc/NPC/item target is active;
- coordinate/plane/deck relationship is valid;
- player owns or is authorized for the boat/action;
- current aggregate version and activity state allow the transition;
- rate and sequence number are acceptable; and
- requested items/resources exist and can be consumed atomically.

Malformed heading packets must not publish an event. Repeated packets cannot move a boat more than one authoritative tick. Client var state never grants authority.

## Failure and recovery

All lifecycle exits route through one session service with a reason enum: normal disembark, dock, teleport, quick transport, logout, disconnect, world hop, player death, capsize, server shutdown, or administrative recovery.

Recovery records the durable boat identity, last safe dock/gangplank, cargo-loss decision, passenger disposition, and operation token. Replaying recovery is idempotent. Startup scans detect duplicate live leases and keep the boat disabled until reconciled.

## Feature gating

A feature may be enabled only when its parity row has linked evidence, exact cache/capture mappings, automated tests, and live acceptance evidence. `BLOCKED_BY_CAPTURE` features remain unreachable even when assets are present in cache. Future/cancelled content stays `FUTURE_DISABLED`.

## Module direction

- Generic world-entity code stays under `engine`/`api` and cannot import Sailing content.
- Typed Sailing definitions, domain, persistence, and content live under Sailing-owned modules.
- Presentation scripts depend on domain services, never the reverse.
- Cross-skill hooks are narrow events/services for XP, collection log, quests, Slayer, Construction, Fishing, Hunter, Farming, Mining, Crafting, and Runecraft.
- Admin diagnostics are permission-gated and cannot bypass ownership/persistence in production mode.
