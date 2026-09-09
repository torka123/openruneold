# Corporeal Beast behaviour sheet (revision 240)

## Assets and room

- Corporeal Beast: NPC 319 (`npc.corp_beast`), 2,000 hitpoints, 5x5 footprint.
- Dark energy core: NPC 320.
- Public lairs are the existing map spawns at `(2993,4254,2)` and `(2993,4382,2)`.
- Existing Wilderness cave routes remain authoritative for entry and exit.

## Combat

- Four-tick normal cadence with the occasional three-tick follow-up.
- In melee reach: 40% melee and 60% magic. Outside melee reach: magic only.
- Magic variants are regular, stat-draining, and area/split attacks.
- Melee max is 33 and Protect from Melee blocks it. Magic max is 65 and Protect from Magic
  reduces it by one third.
- The drain projectile removes 1-2 Magic or Prayer on a damaging impact and heals Corp for half
  the damage actually dealt.
- Players beneath Corp's 5x5 footprint are checked every seven ticks for the 30-51 stomp.

## Dark core and lifecycle

- At most one core belongs to each Corp spawn. A 32+ hit can spawn it, and attacks below half
  health can also trigger the spawn roll.
- The core jumps when it has no adjacent victim. Adjacent siphons occur every two ticks, deal
  5-13 damage, and heal Corp by damage actually dealt.
- Core state is removed on core death, Corp death, Corp respawn, deletion, and the three-minute
  no-combat reset. Core deaths have no drops.
- With nobody in the room, Corp heals 25 every seven ticks. After 300 ticks without combat it
  returns home with full hitpoints and restored combat stats.
- Corp death uses the normal OpenRune NPC death/drop pipeline, which is the canonical Collection
  Log unlock path.

## Architecture migration (9 September 2026)

Ordinary combat now registers through `BossCombat` and `corporealBeastSpec`: weighted selection,
both melee animations, native magic projectiles, impact-time protection and the existing variable
cooldown. The short-roll distribution preserves even the baseline modulo bias; this is mechanical
preservation, not new evidence of that probability in OSRS. The old selector/footprint helper is a
test-only comparison oracle, not a production engine.

Public map spawns/routes remain unchanged. `BossEncounter` owns room state, core ownership and
cancellable world work. Three focused handlers retain split-projectile geometry, core jump/contact,
underfoot hazards and room recovery at the existing native LateCycle stage. Native NPC death/drop
processing remains authoritative. There are no per-player runs or new private instances.

The module's native fixture executes registered combat, deferred prayer modifiers, NPC registry,
world queues, lifecycle events and two-lair isolation. It does not claim live-client or packet parity.
See [the migration report](../../../docs/bosses/DSL_MIGRATION.md) for exact commands/results.

## Historical evidence provenance (unchanged)

- Mechanics and rates: current OSRS Wiki.
- Animation/projectile/spot-effect IDs and projectile profiles: the user-authorized Near Reality
  revision-240 reference, translated into native OpenRune APIs.
- Room spawns/routes and NPC IDs: active OpenRune revision-240 map/cache data.
- No usable Corporeal Beast RSProx capture was present locally; packet-exact sound and core poison
  stun timing remain capture-validation items.
