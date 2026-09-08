# Corrupted Gauntlet behaviour and evidence sheet

## Scope and sources

This module implements only the Corrupted Gauntlet. It is native OpenRune/RSMod Kotlin and uses
the active revision-240 cache. The permitted Near Reality `plugins/excluded/gauntlet` module was
used as a behaviour reference for room chunks, floor pattern families, interaction flow, recipes,
and client state. No Near Reality class, package, cache archive, map, model, sprite, or other asset
was copied into OpenRune.

The OSRS Wiki pages for The Gauntlet, Corrupted Hunllef, and the corrupted tornado were used for
the public mechanics, thresholds, damage ranges, preparation duration, and reward structure.
RuneLite's Gauntlet plugin was used as a client-state cross-check. Searches of the available local
RSProx transcripts and the public described-dump index did not find a usable Corrupted Gauntlet
capture. Projectile and CS2 values below are therefore revision-240 cache/donor-reference values,
not falsely labelled as packet-capture-confirmed values.

## Revision-240 dependency sheet

| Area | Native dependency |
| --- | --- |
| Map | source region `7768`, plane `1`; four 2-by-2 room chunk shapes, start and boss chunks |
| NPCs | Corrupted Hunllef forms `9035`-`9038`, tornado `9039`, monsters/demi-bosses `9040`-`9048` |
| Objects | preparation objects `35964`-`35989`, corrupted light nodes `35998`-`36001`, floor states `36046`-`36048`, lobby/portal objects through `36084` |
| Run items | corrupted tools, resources, armour and weapons in the native `23820`-`23885` group; combo/escape items `25958` and `25959` |
| Hunllef visuals | sequences `8418`-`8422`, switches `8754`/`8755`; projectiles `1708`, `1712`, `1714` |
| Client state | overlay/map/recipes interfaces `637`-`640`; CS2 `2908`, `2914`, `2916`, `2917`; varbits `9177`-`9179` and `9240`-`9292` |
| Completion state | normal completion varp `2353`; corrupted completion varp `2354` |

## Run lifecycle

1. The portal checks normal-Gauntlet eligibility, death/protected-action state, pending rewards,
   and conflicting item-sandbox or instance membership.
2. A one-player private instance is created from a seeded 7-by-7 room layout. The player's
   inventory and equipment are transactionally snapshotted before temporary items are issued.
3. The start and boss rooms are fixed. Six outer-edge rooms are assigned two bears, two dragons,
   and two dark beasts. One boss-adjacent room receives three guaranteed fishing nodes.
4. Preparation lasts exactly 750 server ticks (7:30). Crossing the boss barrier starts Hunllef;
   expiration pulls the player into the boss room. CS2 `2916` changes the native overlay to the
   boss timer.
5. Success or failure creates one durable pending chest result. Temporary NPCs, objects, hazards,
   inventory, equipment, interface state, and instance membership are cleared. The original
   inventory/equipment snapshot is restored transactionally.
6. Logout, death, external teleport, explicit escape, and logout-tab Leave Instance all use the
   same cleanup route. An unresolved snapshot is retained for safe login recovery instead of
   discarding the player's items.

## Preparation behaviour

- Rooms are reproducible from a supplied seed (`::cg <seed>`) while ordinary entries use a random
  seed. Resource and monster placement is collision checked and instance owned.
- Unlit corridor nodes require the corrupted sceptre. Lighting either half opens both matching
  nodes, reveals the adjacent room on interface `638`, rejects out-of-grid links, and keeps the
  boss room restricted to its barrier. Revealing a room no longer moves the map's current-room
  marker until the player actually enters it.
- Ore, bark and linum nodes yield three; grym yields one; fishing nodes yield four fish.
- Weak, strong, and demi-boss tiers use their native NPC forms and combat styles. Drops route to
  the participant only; guaranteed weapon-frame and duplicate demi-component fallback rules are
  centralized.
- The player begins with a corrupted sceptre, axe, pickaxe, harpoon, pestle and mortar, and
  teleport crystal. Tool storage restores only missing tools.
- The singing bowl supports all three armour tiers, halberd/staff/bow progression, vials,
  teleport/escape crystals, and crystal paddlefish with explicit material/shard recipes.
- Vials fill at the pump and fishing spots. Grym/dust mixing creates Egniol potions. Ordinary
  paddlefish use the native food delay and heal 20; Egniol restores `floor(base Prayer / 4) + 7`,
  40% run energy, stamina, and follows the 4-to-empty-vial dose chain.
- The map orb opens native interface `638` and initializes it with CS2 `2908` only while the
  Gauntlet map varbit is active. Outside the activity, normal world-map behavior is unchanged.

## Corrupted Hunllef

- 1,000 HP, five-tick attack interval, starts with Ranged outgoing attacks, and changes between
  Ranged and Magic after four attack turns. A tornado cast consumes one of those turns.
- The initial protection prayer is seed-stable and may be Melee, Ranged, or Magic. Protected-style
  player hits deal zero. The sixth valid unprotected-style attack changes protection to the style
  of that sixth attack.
- Ranged uses projectile `1712`; Magic uses `1708`. Magic has a 15% chance to use prayer-disable
  projectile `1714`; all active prayers are disabled when that projectile impacts, not when cast.
- Correct overhead prayer damage is capped by worn armour tier sum: 16/13/10/8 at sums 0/3/6/9.
- Tornadoes spawn three ticks after animation `8418`, last 20 ticks, chase the player, and deal
  tier-scaled damage every tick on contact. Counts are 2/3/4 above 666, from 334-666, and at 333
  HP or below.
- Floor tiles use the native safe/warning/damaging objects and translated 12-by-12 easy, medium,
  and hard pattern families. Warning lasts five ticks and damage lasts six ticks. Patterns cannot
  overlap; later health phases schedule them more frequently.
- Standing under Hunllef triggers animation `8420` and typeless trample damage. Death clears all
  floors/tornadoes before the durable reward is prepared.

## Rewards and Collection Log

A success stores 7-12 crystal shards, a cape when not already owned, three main-table rolls, and
independent weapon-seed, armour-seed, enhanced-seed, Youngllef, and elite-clue rolls. A failed run
stores the appropriate partial/minimal failure roll. The pending reward carries both run and
transaction UUIDs; it is not removed until item delivery, Collection Log recording, and account
save commit together. Repeated chest clicks cannot claim it twice.

Tracked Gauntlet uniques are routed through `CollectionLogService` with stable event IDs. The
corrupted completion counter is synchronized to varp `2354` for the Collection Log interface.
The native lobby scoreboard object opens revision-240 interface `639`; its Corrupted Gauntlet
column is populated from durable personal completions, actual encounter deaths, and the fastest
complete run time. Standard-Gauntlet and global aggregate fields are clearly unavailable rather
than displaying invented records, because those systems are outside this milestone.

## Cache compatibility manifest

### Already present and ready

- Region `7768`, all listed NPC/object/item/sequence/projectile/interface/CS2/varbit definitions.
- All nine corrupted halberd/staff/bow tiers are wieldable and expose native attack categories,
  attack rates, and attack animations; all bows expose projectile parameters.
- Reward items and the existing Collection Log slots for Gauntlet uniques.

### Present; server configuration supplied by this module

- Room construction, dynamic node/monster spawning, interactions, combat, UI state, instance
  ownership, persistence, reward routing, and cleanup.

### Genuinely missing

- None found in the active revision-240 cache.

### Near Reality-only/custom assets

- None required or imported. There is no live-cache mutation and therefore no rollback manifest.

## Evidence limitation and manual validation

No usable RSProx Corrupted Gauntlet capture was available. Exact client-visible floor cadence and
projectile travel should therefore be compared against a fresh live capture during manual QA.
The IDs, definitions, map chunks, interaction state, and implemented rules are deterministic and
automated-testable, but final pixel/tick parity for those two visual details must not be claimed
until that capture is performed.

Manual QA:

1. Enter normally after one standard-Gauntlet completion; confirm ineligible and pending-reward
   messages on the negative paths.
2. Run `::cg 12345` twice and confirm the same layout; open the map orb, light corridor nodes with
   the sceptre, and confirm the adjacent room is revealed before moving while the current-room
   marker remains on the player.
3. Gather each node, recover missing tools, cook fish, fill/mix/drink a potion, and craft every
   armour/weapon tier plus both crystals.
4. Kill every monster and demi-boss type; confirm frame/component routing and no cross-room aggro.
5. Enter Hunllef normally and by preparation timeout. Verify four-turn style switches, 15% magic
   prayer-disable impacts, six valid-hit protection switches, all floor families, 2/3/4 tornadoes,
   tiered damage, movement, and trample.
6. Test death, Escape crystal, logout-tab Leave Instance, logout, disconnect, and a normal external
   teleport; re-enter and confirm no leaked run state or temporary item.
7. Complete a kill, reconnect before claiming, claim once, retry the chest, and verify inventory,
   completion varp, and Collection Log unique state.

## Deferred standard Gauntlet work

Standard Gauntlet remains intentionally unavailable in this module. A later milestone can reuse
the layout/rules abstractions while supplying crystalline NPC/item/object IDs, a ten-minute prep
timer, standard damage/reward values, separate completion tracking, and its own regression suite.
No command, portal route, or completion claim here represents standard Gauntlet as implemented.
