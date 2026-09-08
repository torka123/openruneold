# Vorkath Wiki and official RuneLite verification (2026-09-08)

This is research evidence, not a claim that gameplay or a live encounter passed verification. The five supplied RSProx captures govern packet geometry, animations, server-visible state, and observed ticks. Wiki facts below fill mechanical gaps and must be labelled **Wiki-derived/inferred**, not capture-confirmed. Current RuneLite gamevals corroborate names; the existing native revision-240 audit remains the cache authority. No cache or gameplay source was changed for this document.

Direct Wiki page retrieval was refused by the browsing provider's robots policy. Its search index supplied the primary Wiki page contents (indexed revisions noted below); therefore these are not assertions that every subsequent live Wiki revision was fetched. RuneLite raw official source was fetched successfully.

## Fight and lifecycle

[Vorkath](https://oldschool.runescape.wiki/w/Vorkath): post-quest HP 750; normal speed five ticks; maxima melee 32, ranged 32, magic 30, ordinary dragonfire 80, bomb 121, rapid fire 41. Melee includes diagonal adjacency. Six ordinary attacks precede each special. The first special is randomized; subsequent specials alternate. Acid reduces incoming damage by 50%; spawn makes Vorkath immune until resolution. Full-health spawn failure deals 60. Death returns Vorkath to sleep, allowing immediate reawakening. Sleeping NPC 8059 has Poke. Access uses Torfinn/Ungael and the crater ice obstacle. Existing rewards must remain authoritative: two standard drop rolls, tertiary once, one guaranteed head at kill 50, loot position based on player direction after death animation. Instance ground loot expires after three hours or leaving; Torfinn recovery costs 100,000.

## Specials and selection

[Strategies](https://oldschool.runescape.wiki/w/Vorkath/Strategies) (indexed revision 15147382):

- Magic:ranged relative weights are 3:4 (Mod Ash, 20 February 2018); complete melee/dragonfire weights are not established.
- Acid includes the player's sampled tile. Pools deal up to 10 and heal Vorkath equivalently. Rapid fire deals 25–41 regardless of protection, targeting the current tile each tick. The exit-side lane avoids random pools, but player-directed acid can contaminate it. **Capture resolution:** all 200 supplied acid phases fit 48 grid cells plus eight edge cells and the player tile, producing 56 or 57 distinct pools. The exit-side centre tiles x2269..2275 at z4054 remain clear of random pools; player-directed acid can contaminate them. See [capture evidence](live-capture-evidence-20260908.md) for the native coordinate ranges and collision audit.
- Icy breath freezes the player and starts immunity. **Capture resolution:** 89 fully observed freeze ticks contain no zero-damage hitsplat; the implementation must not add one. Spawn HP 38; Wiki reports ten tiles and walking toward the player. **Capture override:** all 184 supplied launch targets are Chebyshev eight, so revision-240 implementation uses eight; see [capture evidence](live-capture-evidence-20260908.md). Freeze/immunity end when it dies/explodes. Crumble Undead kills on a successful hit; magic bonus above -64 guarantees accuracy. A lethal spell arriving as it reaches the player must prevent explosion damage.
- Prayer dragonfire disables all prayers at impact, including Preserve. Venom and prayer effects remain possible when protection absorbs damage.
- Bomb snapshots the player's launch tile; adjacent damage is halved; moving two tiles avoids it. Dragonfire protection does not reduce bomb damage.

## Dragonfire distribution and complete protection matrix

[Dragonfire](https://oldschool.runescape.wiki/w/Dragonfire) (indexed revision 15145619), including cited Mod Ash explanations dated 17 October 2020, 27 December 2020 and 29 January 2021:

Roll damage **before** potion subtraction. Shield caps the roll at 20; Protect from Magic without shield caps it at 30. Without either, Magic accuracy determines an 80 or 50 cap (failed/successful resistance). Subtract regular antifire 10 or super antifire 20, then subtract 5 for venom/corrupting breath, clamping at zero. Extended/mix variants preserve potency. Shield and prayer do not stack. Rolling uniformly up to the reduced maximum gives an incorrect distribution.

| Equipment/prayer | None | Regular/extended antifire | Super/extended super |
|---|---:|---:|---:|
| Neither, resistance failed | 80 | 70 | 60 |
| Neither, resistance succeeded | 50 | 40 | 30 |
| Shield | 20 | 10 | 0 |
| Protect from Magic | 30 | 20 | 10 |
| Shield + Protect from Magic | 20 | 10 | 0 |

Values are ordinary-breath maxima, not independent uniform roll ranges. Green/purple maxima are five lower, minimum zero. Anti-dragon shield, dragonfire shield, dragonfire ward and ancient wyvern shield supply shield protection; elemental/mind shields do not.

## Native names corroborated by official RuneLite

[RuneLite SpotanimID](https://raw.githubusercontent.com/runelite/runelite/master/runelite-api/src/main/java/net/runelite/api/gameval/SpotanimID.java), declarations near lines 398 and 1475–1489:

| Asset | ID | Official name |
|---|---:|---|
| Ordinary breath | 393 | DRAGON_RANGED_FIRE_ATTACK |
| Venom travel / impact | 1470 / 1472 | DRAGON_RANGED_VENOM_ATTACK / DRAGON_VENOM_IMPACT |
| Prayer travel / impact | 1471 / 1473 | DRAGON_RANGED_CORRUPTING_ATTACK / DRAGON_CORRUPTING_IMPACT |
| Ranged travel / impact | 1477 / 1478 | VORKATH_RANGED_TRAVEL / VORKATH_RANGED_IMPACT |
| Magic travel / impact | 1479 / 1480 | VORKATH_MAGIC_TRAVEL / VORKATH_MAGIC_IMPACT |
| Bomb | 1481 | VORKATH_AREA_TRAVEL |
| Rapid fire | 1482 | VORKATH_AREA_SMALL_TRAVEL |
| Acid | 1483 | VORKATH_ACID_TRAVEL |
| Spawn | 1484 | VORKATH_SPAWN_TRAVEL |

[RuneLite AnimationID](https://raw.githubusercontent.com/runelite/runelite/master/runelite-api/src/main/java/net/runelite/api/gameval/AnimationID.java), declarations near lines 7890–7963: DS2_SPAWN_ATTACK 7890, DS2_SPAWN_DEATH 7891; DS2_VORKATH_SLEEPING 7946, WALK 7947, IDLE 7948, DEATH 7949, SPAWN 7950, ATTACK_MELEE 7951, RANGED 7952, DEFEND_UP 7954, DEFEND_IDLE 7955, DEFEND_DOWN 7956, ACID 7957, RANGED_UP 7960.

[RuneLite NpcID](https://raw.githubusercontent.com/runelite/runelite/master/runelite-api/src/main/java/net/runelite/api/gameval/NpcID.java), declarations near lines 35343–35367: VORKATH_SLEEPING 8059, VORKATH 8061, VORKATH_SPAWN 8063. Quest variants 8060/8062 are separate.

Names alone do not prove an effect is transmitted for an attack. Captures must determine whether 1472/1473 are used, and whether bomb/rapid impacts require separate map effects. No substitute impact, sound or freeze asset should be added from name similarity.

## Remaining inference boundaries

- Complete attack selection probabilities, especially melee chance, are unresolved. The previous 2:1 ranged/magic weighting conflicted with the cited 4:3 ratio outside melee range. The repair uses 4:3 in both pools with optional melee; the full proximity-dependent distribution remains inferred.
- No fixed freeze duration is established: bind it to the spawn phase. Numeric emergency timeout is cleanup policy, not OSRS evidence.
- The spawn damage relationship is health-scaled with full-health 60; exact rounding for partial health is unproven here. A uniform random roll from zero to the scaled value is unsupported.
- Bomb maximum 121 and adjacent half damage are established; exact minimum/base-damage formula is unresolved. A uniform 0–121 bomb would permit harmless direct hits and is not established by these sources.
- Captures now resolve acid selection cells/count, the exit-side lane, wake/death sequencing, special transitions, projectile launch/landing timing, spawn launch distance, and explosion versus lethal-hit ordering; see [capture evidence](live-capture-evidence-20260908.md). Selection uniformity inside the observed acid cells, acid minimum damage/pulse scheduling, spawn direction/path tie-breaking, and hit-roll/prayer sampling still have inference boundaries. The implemented acid 1..10 roll is not established as an exact distribution by this Wiki source.
- No official RuneLite encounter state machine was used as a server authority. Current official gamevals corroborate assets only; third-party OpenOSRS boss helpers were excluded.
