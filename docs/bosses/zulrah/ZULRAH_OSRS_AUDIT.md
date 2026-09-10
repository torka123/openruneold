# Zulrah implementation audit

## Canonical source ownership

The authoritative encounter is `content/bosses/zulrah`. No pre-existing Zulrah combat, command,
instance, or world spawn was present. The only earlier implementation was a resource drop table at
`content/drops/src/main/resources/drops/tables/monsters/zulrah.toml`; it is replaced by the Kotlin
two-roll table so only one drop definition is registered.

## Active cache verification

`server/app/src/main/resources/game.yml` configures revision 240. The active binary game-value index
and `osrs-dumps/dump.npc` were queried from that workspace, rather than inferring the revision from a
directory name.

Verified NPC mappings: green 2042, magma 2043, tanzanite 2044, melee snakeling 2045, magic
snakeling 2046, High Priestess 1616, Priestess variants 1617 and 2033. Zulrah is level 725, size
5, and has 500 hitpoints and level 300 Defence/Magic/Ranged in the configured dump. Snakelings are
level 90 with one hitpoint.

Cache identities include attack 5069 and idle 5068. The capture-backed current summon uses 5069,
not the old ledger's incorrectly labelled 5068. Spawn 5071, sink 5072, emerge 5073,
death 5804, tail left 5806, tail right 5807, defend 5808, snakeling attack 1741, snakeling defend
1742, and snakeling death 2408.

Verified spotanims: ranged 1044, cloud orb 1045, magic 1046, snakeling egg 1047, snakeling magic
1230. Verified locs: poison cloud 11700, shrine exit 11701, and boat variants 10068, 46241, 46242.

The current source template preserves the playable 3x3 zone core at source zones (282,382), with
the existing 9x8 rotated swamp surround and upper-level chunks; it is not a simple two-region copy.
Every boss and hazard source coordinate is stored in the
original shrine coordinate space and transformed through `InstanceManager.localCoord` for the
allocated private region.

## Mechanics and deliberate integration choices

- The same owned NPC is transmogrified between forms without assigning a new UID. This preserves
  queued player hits and damage attribution through dives.
- Direct attacks use the engine projectile calculation and capture protection prayer at generation.
  Accuracy is also captured so an accurate zero-damage protected hit can still invoke global venom
  at impact.
- Damage above 50 is rerolled independently to 45-50 in `onModifyNpcHit`.
- Elemental weakness remains in the shared spell weakness system and active cache NPC parameters.
- Normal melee is rejected naturally by island collision and reach; the generic weapon range allows
  halberds to reach without an item-name exception.
- Cloud footprints are deduplicated before damage, so overlapping 3x3 hazards produce one cloud hit
  per tick. Cloud damage does not call the global venom-status applicator.
- Persistent death storage uses triples of item id, count, and vars. Inventory addition and any fee
  deletion occur in one inventory transaction.
- No Regicide enforcement was added by the architecture migration.

## September 2026 architecture migration

See [the shared migration report](../DSL_MIGRATION.md#zulrah-mapping-before-migration) for exact
upstream/local API classification, pre-migration characterization, fixes and command logs.
Ordinary boss/snakeling combat and all counted phase programs now use BossCombat/BossEncounter;
the same original rotation data generates the declarative specifications. Native InstanceScript
owns entry and exit; keyed instance sessions own the surface/resource state. There is no private
player-to-run map, ordinary attack loop, transition timer or respawn replacement engine.

Clouds, eggs and tails remain spatial handlers using native projectiles, scoped queued hits and
BossEncounter-owned deadlines. Snakeling expiry belongs to its own native encounter and cancels
on deletion. Native deathWithDrops owns completion drops and seventeen-tick same-actor respawn.
The source-only NPC overlay adds native KC metadata to spawn identity 2042; it must be packed before
deployment. The live SERVER/LIVE cache files have not been changed during this migration.

Executable regression coverage includes all four complete declared action programs; attack/prayer
snapshots; native boat/camera entry; cloud/tail timing; both normal and debug death; repeated kills;
all departure hooks; two-session isolation; source KC metadata/native kill hook; resource cancellation.
These tests characterize the implementation and integration, not independent normal-game proof.

Evidence limits remain: capture820 is Grid Master revision234.1, not normal-game timing authority;
full normal rotations and every visual branch have not been captured here. The earlier first-tail
ordering claim conflicts with the retained 5806-then-5807 implementation. Non-player source exclusion
is inherited and unresolved. No new client comparison or two-restart verification has been performed.
Do not label this migration 1:1 OSRS or live-verified.

## Verification boundary

The active cache proves the asset identifiers, NPC definitions, shrine regions, and interaction
options listed above. The public sources confirm the encounter's forms, rotations, broad mechanics,
and drop odds. They do not expose an authoritative packet/tick trace for every venom-cloud orb and
snakeling landing tile, every projectile release/impact delay, exact hazard lifetime, every sound,
or the probability of deviating after the first completed rotation. Those values are deliberately
centralized in the Zulrah module, but still require an authoritative capture and live client
comparison before this implementation can be certified as frame-for-frame 1:1.

No Zulrah-only recoil shortcut was added: the server's generic player-hit processor still has an
existing recoil-system TODO, so ring of recoil/suffering behavior remains incomplete. Encounter
sounds are also not implemented. Automated unit, drop-table, build, and two clean boot checks passed;
the full two-player, death-storage, fee, prayer-switch, halberd, fire-weakness, and collection-log
matrix has not yet been exercised manually in a connected client.

## References

- Current OSRS Wiki: `https://oldschool.runescape.wiki/w/Zulrah`
- Current phase chart: `https://oldschool.runescape.wiki/w/Zulrah/Strategies`
- Official 25 June 2025 loot update: `https://oldschool.runescape.wiki/w/Update:Summer_Sweep-Up:_Combat_%26_Loot`
- RuneLite plugin-hub rotation cross-check: `https://github.com/while-loop/runelite-plugins/tree/zulrah-helper`
