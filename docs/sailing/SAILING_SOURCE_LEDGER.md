# Sailing source ledger

Source precedence is target-revision cache/packet evidence, official shipped-update evidence, current Wiki inventory, RuneLite/client behavior, dated gameplay footage, then donor/PR design reference. Cache presence is not proof that a feature was live.

| Source | Frozen reference | Used for | Limits |
|---|---|---|---|
| OpenRune `LIVE` cache | revision 240; dat2 SHA-256 `37B6679BA2310276FD8D26A3611D8626251D436005359149D4139AFC2D5E8D02` | exact definitions, DB schema/rows, interfaces, vars, transforms, sequences, maps | local metadata does not prove subrevision 5; staged assets may exist |
| OpenRune `SERVER` cache | revision 240; dat2 SHA-256 `DA5692E7017805D5B8289DBD402EC7DE79FE35A9E138B1FB1EE09E9336478268` | customized runtime cache and executable var/DB/interface contracts | full index audit remains separately blocked by malformed injected enum 65475 |
| Local merged gamevals | 2026-08-28 tree | symbolic IDs and generated DB wrappers | ignored generated wrappers are not gameplay implementation |
| OpenRune PR #182 | head `3026daead`; commits `910a6a5e6`, `cda27866e`, `4c61b7172` | world-entity and helm foundation/provenance | draft, dirty, incomplete, no committed parity suite |
| Current RSProx source | head `a440d3fde8f648fb8e67907297300309201b87bc`; r240 decoders | incoming/outgoing packet layouts and independent client-like world-entity state | protocol transport does not prove gameplay constants, collision, or dock geometry |
| Current RSProt source | `1.0.0-ALPHA-20260825`; head `0a0fab9eea7abed14665aced167f1500222e6c65` | OpenRune r240 codec authority and corrected high-resolution extended-info flag | still requires OpenRune integration and live-client validation |
| Targeted public RSProx captures | 381/r231, 3132/r232, 672/r229, 1016+r4306/r235 | historical tick choreography, UI state, helm/sail/salvaging behavior | older captures are behavioral evidence only; values are crosswalked by r240 gameval/cache identity |
| Port Sarim capture 1009 | r235.1; SHA-256 `4DE6153EA211131504802720695430C9B12F6142D1DB3211E7B1E2A2A5DF89D9` | exact observed gangplank, raft spawn, helm entry, heading, movement, and positive centerline replay evidence | no return, docking-buoy action, negative collision, or r240 session; production geometry remains blocked |
| Official launch update | 2025-11-19 | shipped launch boundary | visible rules still require cache/capture |
| Official Boat Combat Fixes | 2026-03-18 | current combat/facility deltas | exact formulas/cadence require cache/capture |
| Official Sailing changes | 2026-05-13 | facility boosts, salvage/pet and lifecycle deltas | proposal posts do not count unless shipped |
| Official Red Reef update | 2026-05-20 | Red Reef, Bosun's Bench, ammo, auto-retaliate, colors | scripted quest boats do not prove generic NPC boats |
| Official Wyrmscraig update | 2026-07-29 | Wyrmscraig and Fallen From Grace | exact quest/activity state requires mapping |
| Official 5 August changes | 2026-08-05 | offshore Fishing, Max Cape/portal parity | exact spots and UI require mapping |
| Official 26 August update | 2026-08-26 | Courier Loot Sacks and final cutoff fixes | reproduce intended Courier-only rule, not transient Bounty bug |
| OSRS Wiki Sailing inventory | read 2026-08-28 | taxonomy, visible requirements, cross-links | older crawls omit later 2026 content; official updates override |
| RuneLite/client helpers | target revision where available | visible UI state, route/target identity, overlay behavior | does not prove server authority or hidden formulas |
| Dated live footage/capture | not yet complete | choreography, timing, audio/camera, packet state | must match target cutoff and be archived |

## Primary URLs

- PR #182: https://github.com/OpenRune/OpenRune-Server/pull/182
- Launch: https://secure.runescape.com/m=news/sailing-is-out-today?oldschool=1
- Boat Combat Fixes: https://secure.runescape.com/m=news/boat-combat-fixes?oldschool=1
- Sailing changes: https://secure.runescape.com/m=news/sailing-changes-and-gem-bag-expansion?oldschool=1
- Red Reef: https://secure.runescape.com/m=news/the-red-reef-is-out-today?oldschool=1
- Wyrmscraig: https://secure.runescape.com/m=news/wyrmscraig-is-out-today?oldschool=1
- 5 August changes: https://secure.runescape.com/m=news/wyrmscraig--sailing-changes?oldschool=1
- 19 August changes: https://secure.runescape.com/m=news/summer-sweep-up---hunter--skilling?oldschool=1
- 26 August cutoff update: https://secure.runescape.com/m=news/mad-angel-combat-achievements?oldschool=1
- Wiki Sailing: https://oldschool.runescape.wiki/w/Sailing
- Shipbuilding: https://oldschool.runescape.wiki/w/Shipbuilding
- Mooring: https://oldschool.runescape.wiki/w/Mooring_point
- Crew: https://oldschool.runescape.wiki/w/Crew_Management
- Salvaging: https://oldschool.runescape.wiki/w/Shipwreck_salvaging
- Charting: https://oldschool.runescape.wiki/w/Sea_charting
- Trials: https://oldschool.runescape.wiki/w/Barracuda_Trials
- Trawling: https://oldschool.runescape.wiki/w/Deep_sea_trawling
- Courier tasks: https://oldschool.runescape.wiki/w/Courier_tasks
- Bounty tasks: https://oldschool.runescape.wiki/w/Bounty_tasks
- Ship combat: https://oldschool.runescape.wiki/w/Ship_combat
- Collection log: https://oldschool.runescape.wiki/w/Sailing_Collection_Log

## Local audit artifacts

The working research copies are outside the canonical repository and are not runtime dependencies:

- `work/sailing-research/repository-audit.md`
- `work/sailing-research/cache-gamevals-audit.md`
- `work/sailing-research/live-content-source-audit.md`
- `work/sailing-research/foundation-qa-audit.md`

## Evidence rule

Every parity row links a source and exact revision/cache mapping. Conflicts are recorded in `SAILING_CAPTURE_GAPS.md`. No numeric ID, formula, timing, probability, coordinate, operation, or persistence rule is selected by similarity. Missing proof means `BLOCKED_BY_CAPTURE`, not an approximate implementation.
