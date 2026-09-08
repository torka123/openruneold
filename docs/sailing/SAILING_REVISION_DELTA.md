# Sailing revision delta

## Freeze

- Behavioral cutoff: 2026-08-28.
- Last shipped update in scope: 2026-08-26.
- Protocol/cache family: revision 240.
- Requested label: 240.5.
- Local proof establishes revision 240 but not the subrevision suffix. Record 240.5 as expected, not verified, until provenance is captured.

## Shipped baseline and deltas

| Date | Shipped delta that must be folded into baseline |
|---|---|
| 2025-11-19 | Sailing launch; Pandemonium, Prying Times, Current Affairs, Troubled Tortugans, native skill/boats/training/islands |
| 2025-11-26 | week-one fixes and lifecycle/interface corrections |
| 2025-12-03 | XP review and further fixes |
| 2025-12-10 | GIM facilities, quick-transport losses, cargo confiscation, crew helm, chart/cannon boosts, filters/warnings |
| 2025-12-17 | additional interface and lifecycle corrections |
| 2026-01-07 | current-level checks for active facilities; captain helm/sail exception |
| 2026-01-14 | crew sail speed-boost duration correction |
| 2026-03-18 | cannon rebalance, combat formulas, Ballistic Attractor, sloop slots, Veiled Kraken, Fire at Will, loot changes |
| 2026-04-01 | manned-cannon buff, creature HP, Bounty XP, Ranged gear/prayer/Void integration |
| 2026-05-13 | high-level spawns, clue boost, facility ordering/boost categories, Wind Mote, Soup salvage roll, Coffer support |
| 2026-05-20 | Red Reef, Bosun's Bench, special ammo, Rubium, auto-retaliate, sail colors, Medallion, trawling changes |
| 2026-05-27 | Rubium/tick changes, splinters, special-ammo XP/cycle, Red Reef hotfixes |
| 2026-06-03 | Courier XP +10%, Bounty updates/rates, Last Boat integrations |
| 2026-06-17 | contribution rewards, one progress roll, Veiled rate fix, cannon-loot resume, item values |
| 2026-07-15 | trawling five-minute timeout, per-player bait, no cannon resume on respawn |
| 2026-07-29 | Wyrmscraig, Fallen From Grace, island activities/bosses, Sailing cape boat teleport |
| 2026-08-05 | offshore Fishing, Max Cape teleport, Portal Nexus Last Boat, UI/guide fixes |
| 2026-08-19 | coral nursery/chest/yield changes |
| 2026-08-26 | Courier Loot Sacks, port-themed supplies, offshore Fishing hitbox fix, Mad Angel Combat Achievements |

## Intended-state corrections

The 2026-08-26 update briefly let Bounty Tasks award Courier Loot Sacks. Official wording identified this as unintended. The frozen server implements Courier-only sacks and does not preserve the transient bug.

The Red Reef scripted quest boat battle is live; it does not enable the broader proposed generic pirate/NPC boat system.

## Cache versus live

The cache contains 7,450 Sailing-named mappings and may include staged/future assets. Every row must be classified. In particular:

- only raft, skiff, and sloop are live;
- only three Barracuda Trials are live;
- Courier and Bounty Tasks are live, Protection Tasks are not;
- six Sailing-linked quests are live;
- Red Reef, Wyrmscraig, offshore Fishing, and Courier Loot Sacks are mandatory;
- roadmap-only ocean/island expansion remains disabled.

## OpenRune delta

At the initial checkpoint OpenRune had no local Sailing/world-entity implementation beyond a protocol supplier. The selective PR #182 port now supplies an engine/helm scaffold. Relative to live OSRS, almost every skill/content/persistence/presentation domain remains unimplemented or capture-blocked as recorded in the parity matrix.

## Update procedure

For every later correction, add a dated row with source URL, target cache fingerprint, affected parity rows, migration requirements, automated tests, and live acceptance result. Do not silently reinterpret the frozen cutoff.
