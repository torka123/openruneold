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

Verified sequences: direct attack 5069, summon attack 5068, spawn 5071, sink 5072, emerge 5073,
death 5804, tail left 5806, tail right 5807, defend 5808, snakeling attack 1741, snakeling defend
1742, and snakeling death 2408.

Verified spotanims: ranged 1044, cloud orb 1045, magic 1046, snakeling egg 1047, snakeling magic
1230. Verified locs: poison cloud 11700, shrine exit 11701, and boat variants 10068, 46241, 46242.

The shrine copies regions 9007 and 9008. Every boss and hazard source coordinate is stored in the
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
- Regicide enforcement is isolated behind `ZULRAH_REQUIRE_REGICIDE` and remains disabled until the
  server has a persisted Regicide state.

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
