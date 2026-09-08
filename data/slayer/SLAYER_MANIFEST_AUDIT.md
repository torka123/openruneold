# OSRS Slayer manifest audit notes

Source data was retrieved from the official Old School RuneScape Wiki on **2026-08-12**. The configured OpenRune cache revision is **240**; the active paths are `.data/cache/SERVER` and `.data/cache/LIVE`.

## Counts

- Master roles: 9
- Master NPC variants/replacements: 13
- Raw master task rows: 330
- Unique non-boss task categories: 115
- Expanded directly assignable Like a Boss identities: 33
- Concrete canonical task keys: 148
- Manifest entries including repeated per-master rows: 363
- Assignment-ready entries: 0
- BLOCKED entries: 363

The 330 master rows include four boss-pool meta rows (Konar, Nieve/Steve, Duradel/Kuradal, and Krystilia). The 33 directly assignable boss identities are expanded separately.

The Vannaka rows sum to weight 333, while the live Wiki displays 325. Its Cockatrice row has weight 8 but is not included in the page's running-total template. The manifest preserves the row's stated weight and records both totals in `masterTableWeightAudit`.

## Schema

`masterTaskRows` preserves every Wiki row independently, including master role, canonical task key, weight, base and extended amounts, raw and partially structured requirements, alternatives, Konar locations, Wilderness rules, source revision, cache evidence, and support disposition.

`directBossTasks` expands the Like a Boss pool into concrete boss identities. `parentPoolWeight` is the weight of the master’s boss meta-row; it is not an independent per-boss weight. Per-boss prerequisites remain unverified until the individual boss pages and encounters are audited.

The requirement `raw` field is authoritative. The derived skill list is convenience data only and must not replace AND/OR quest-state or partial-completion logic.

## Support and blockers

Every entry is deliberately `BLOCKED`. The current cache-backed Slayer system contains task tables, but this audit has not yet supplied an explicit, verified NPC-ID membership list for every category, proven at least one reachable spawn for every assignment, or validated every required combat profile and kill-credit path. Enabling random assignment from display-name matches would violate the no-guessing requirement.

Cache evidence uses exact normalized NPC display-name matches (plus a few explicit group aliases) against `osrs-dumps/dump.npc`. 310 entries have candidate definitions; 53 have no exact category-level display-name match. A no-match result does not prove absence—for grouped tasks such as birds or Barrows it often means the category name is not an NPC name.

Notable revision-240 evidence found: aquanites, Slayer gryphons, frost dragons, and juvenile/mature/elder custodian stalkers all have definitions. No exact Maggot King display-name definition was found. None of those findings prove map, spawn, quest, encounter, or combat support.

## Source revisions

- [Turael/Slayer assignments](https://oldschool.runescape.wiki/w/Turael/Slayer_assignments) — revision 15275442, 2026-07-25T22:53:52Z; accessed 2026-08-12
- [Spria](https://oldschool.runescape.wiki/w/Spria) — revision 15279957, 2026-07-29T12:47:49Z; accessed 2026-08-12
- [Mazchna/Slayer assignments](https://oldschool.runescape.wiki/w/Mazchna/Slayer_assignments) — revision 15248953, 2026-07-02T22:40:26Z; accessed 2026-08-12
- [Vannaka](https://oldschool.runescape.wiki/w/Vannaka) — revision 15279900, 2026-07-29T12:39:55Z; accessed 2026-08-12
- [Chaeldar](https://oldschool.runescape.wiki/w/Chaeldar) — revision 15279910, 2026-07-29T12:41:53Z; accessed 2026-08-12
- [Konar quo Maten](https://oldschool.runescape.wiki/w/Konar_quo_Maten) — revision 15291798, 2026-08-10T07:26:15Z; accessed 2026-08-12
- [Nieve/Slayer assignments](https://oldschool.runescape.wiki/w/Nieve/Slayer_assignments) — revision 15287287, 2026-08-04T17:04:24Z; accessed 2026-08-12
- [Duradel/Slayer assignments](https://oldschool.runescape.wiki/w/Duradel/Slayer_assignments) — revision 15287288, 2026-08-04T17:04:26Z; accessed 2026-08-12
- [Krystilia](https://oldschool.runescape.wiki/w/Krystilia) — revision 15279886, 2026-07-29T12:36:26Z; accessed 2026-08-12
- [Slayer](https://oldschool.runescape.wiki/w/Slayer) — revision 15285807, 2026-08-02T17:56:07Z; accessed 2026-08-12
- [Slayer Master](https://oldschool.runescape.wiki/w/Slayer_Master) — revision 15284083, 2026-07-31T09:10:48Z; accessed 2026-08-12
- [Slayer Rewards](https://oldschool.runescape.wiki/w/Slayer_Rewards) — Like a Boss unlock and exclusions; accessed 2026-08-12

## Known source caveats

- Current Wiki data is Sailing-era and newer than many traditional revision-240 assumptions; cache presence must be checked, not inferred from release chronology.
- Konar alternatives are location-sensitive. The location list does not by itself prove that every alternative counts in every listed location.
- Krystilia ignores combat-level filtering, but Slayer, quest, reward-unlock, Wilderness-location, and I Wildy More Slayer requirements still apply where documented.
- The live Vannaka and Slayer Master pages report a 60-point block cost; one historical summary on the broader Slayer page still says 70. This manifest records 60 and retains this discrepancy here.
- The Slayer overview identifies which bosses are directly assignable but does not enumerate every boss-specific access prerequisite. Those remain blocked pending individual boss-page verification.
