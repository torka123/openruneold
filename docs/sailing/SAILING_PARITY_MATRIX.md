# Sailing parity matrix

Allowed states: `PASS`, `IMPLEMENTING`, `NOT_STARTED`, `BLOCKED_BY_CAPTURE`, `FUTURE_DISABLED`, `FAIL`.

`PASS` requires exact target evidence, cache mapping, automated coverage, and live acceptance. A compile or asset mapping alone is never `PASS`.

| Domain | Feature | Evidence/mapping | Implementation | Automated | Live | Status |
|---|---|---|---|---|---|---|
| Foundation | selective PR #182 integration | audited commits/files | compiled | partial | no | IMPLEMENTING |
| Foundation | world-entity lifecycle/slots | PR + current r240 RSProt/RSProx | integrated; invalid wire bounds reject before avatar allocation and provider failures are logged | 7 registry tests pass; byte/order matrix partial | startup only | IMPLEMENTING |
| Foundation | observer rebuild/zone replay | PR + local APIs | integrated | missing | no | IMPLEMENTING |
| Foundation | deck/root view split | PR + local APIs | integrated | unwired old integration test | no | IMPLEMENTING |
| Navigation | imported turn/move output | PR source hash | frozen kernel | 6 tests pass | no | IMPLEMENTING |
| Navigation | 2048 headings x 8 speeds | local golden | implemented | PASS | n/a | PASS |
| Navigation | fractional residuals | exact rule unknown | absent | absent | no | BLOCKED_BY_CAPTURE |
| Navigation | client heading validation | r240 opcode 109, `g1Alt1`, headings 0..15 | integrated; every valid first heading now applies `heading * 128`; first-only/always/never start settings are explicit | 2 packet + 5 helm-control tests pass | no | IMPLEMENTING |
| Navigation | collision/land/footprint | r240 collision probes prove land/water ambiguity; hull capture absent | pure intent, fail-closed revision/hash surface, full centre sweep and static guard; real surface/hull absent | 8 navigation tests pass | no | BLOCKED_BY_CAPTURE |
| Navigation | currents/rapids/kelp/crystal/ice | live sources + cache | absent | absent | no | NOT_STARTED |
| Navigation | sail states/reverse/wind boosts | r240 sequences/gamevals + r235 helm timeline | captured helm entry and full-sail transition/state are integrated; half/reverse/wind/anchor controls remain absent | cache contract + helm tests pass | no | BLOCKED_BY_CAPTURE |
| Boats | raft/skiff/sloop cache definitions | DB 166 rows 8110..8112 | cache-backed registry and explicit row/type crosswalk; deck/movement scaffold still hard-coded | r240 contract passes | no | IMPLEMENTING |
| Boats | ownership and five-slot unlocks | live evidence | immutable five-slot snapshot, level thresholds, runtime lease indexes, V30 persistence | unit + PostgreSQL round trip pass | no | IMPLEMENTING |
| Boats | selection/recovery/summon/last boat | IF 934 + live evidence | absent | absent | no | NOT_STARTED |
| Boats | persistent components/appearance/HP | cache vars/DB | absent | absent | no | NOT_STARTED |
| Boarding | board/disembark scaffold | loc 59832/59831 and 59834/59833 transform families | exact clicked static parent coordinate resolves a dock; owner reserves lease/capacity before board; recorded-dock exit; uncaptured travel geometry fails closed | registry/map/identity tests pass | no | IMPLEMENTING |
| Boarding | previous/friend/passenger capacity | loc 59832 cache ops; live policy capture missing | owner-only fail-closed path; friend/previous disabled | negative authority tests pass | no | BLOCKED_BY_CAPTURE |
| Boarding | captain/passenger authority | stable character identity + runtime lease | captain owner/member helm claim implemented; passenger authorization still blocked | registry authority tests pass | no | IMPLEMENTING |
| Lifecycle | teleport/logout/disconnect/world-hop | native teleport hook + r240 transient vars + live capture gaps | same-deck teleport retained; external teleport, coords fallback, logout, and disembark release authority and clear native HUD/helm state | lifecycle/lease/reconnect/cache sanitization tests pass | startup only | IMPLEMENTING |
| Lifecycle | death/capsize/cargo loss | live rules | absent | absent | no | BLOCKED_BY_CAPTURE |
| Ports | typed dock/mooring registry | DB 194 proves 61 IDs/metadata; SERVER map proves 64 parent and 62 buoy placements | immutable 61-row registry with row, parent, buoy, and exact-coordinate indexes | cache/map contract passes | no | IMPLEMENTING |
| Ports | all live docks including Wyrmscraig | 34 gangplank rows, 27 dock mooring rows; operational geometry absent | exact gangplank/mooring transform families registered; duplicate occupancy rejected; manual 32-entry scaffold removed | all static placements pass; travel remains blocked | no | BLOCKED_BY_CAPTURE |
| UI | boat selection IF 934 | cache + capture | absent | absent | no | BLOCKED_BY_CAPTURE |
| UI | Captain's Log IF 935 | cache + capture | absent | absent | no | BLOCKED_BY_CAPTURE |
| UI | Sailing side panel IF 937 | r240 cache/client scripts + r231/r235 ordering | state-before-open lifecycle, side0 overlay, onLoad, exact switch-button mask, helm/move/sail/boat-type state, and combat-tab restore integrated; dynamic facility subindices remain gated | r240 cache/sanitization contract passes | startup only | BLOCKED_BY_CAPTURE |
| UI | crew/customisation IF 938/939 | cache + capture | absent | absent | no | BLOCKED_BY_CAPTURE |
| UI | cargo IF 943/944 | cache + capture | absent | absent | no | BLOCKED_BY_CAPTURE |
| Skill | stat 23, XP, levels, boosts, guide | cache/live | native stat 23 XP/level/update-packet and visible boost operations verified; typed accessors and generic persistence/login/guide plumbing present; gameplay XP producers remain gated | r240 cache plus level-30/200m/packet/boost tests pass | no | IMPLEMENTING |
| Shipyard | build/replace/remove core parts | DB/cache/live | absent | absent | no | NOT_STARTED |
| Facilities | all active/support facilities | DB/cache/live | deck visual scaffold only | absent | no | NOT_STARTED |
| Facilities | slot masks/reorder/bottles | conflicting docs + scripts | absent | absent | no | BLOCKED_BY_CAPTURE |
| Quest | Pandemonium | live | absent | absent | no | NOT_STARTED |
| Quest | Prying Times | live | absent | absent | no | NOT_STARTED |
| Quest | Current Affairs | live | absent | absent | no | NOT_STARTED |
| Quest | Troubled Tortugans | live | absent | absent | no | NOT_STARTED |
| Quest | The Red Reef | live | absent | absent | no | NOT_STARTED |
| Quest | Fallen From Grace | live | absent | absent | no | NOT_STARTED |
| Tasks | Courier assignment/cargo/turn-in | live/cache | absent | absent | no | NOT_STARTED |
| Tasks | Courier Loot Sacks | 2026-08-26 live | absent | absent | no | NOT_STARTED |
| Tasks | Bounty generation/combat/turn-in | live/cache | absent | absent | no | NOT_STARTED |
| Tasks | Protection Tasks | not live | disabled | n/a | n/a | FUTURE_DISABLED |
| Charting | complete points/progression/log | cache/live | absent | absent | no | BLOCKED_BY_CAPTURE |
| Salvaging | wreck pools/hooks/sorting/rewards | DB 203/live | absent | absent | no | NOT_STARTED |
| Trials | Tempor Tantrum | DB 143/live | absent | absent | no | BLOCKED_BY_CAPTURE |
| Trials | Jubbly Jive | DB 143/live | absent | absent | no | BLOCKED_BY_CAPTURE |
| Trials | Gwenith Glide | DB 143/live | absent | absent | no | BLOCKED_BY_CAPTURE |
| Trials | fourth trial | not live | disabled | n/a | n/a | FUTURE_DISABLED |
| Crew | roster/hire/assign/order/persist | DB/cache/live | absent | absent | no | NOT_STARTED |
| Combat | cannons/ammo/targeting/formulas | DB 162 + updates | absent | absent | no | BLOCKED_BY_CAPTURE |
| Combat | Ballistic Attractor/Fire at Will | 2026 updates | absent | absent | no | NOT_STARTED |
| Combat | sea creatures and rewards | cache/live | Mogre-adjacent only | absent | no | NOT_STARTED |
| Combat | Veiled Kraken | 2026-03 live | absent | absent | no | NOT_STARTED |
| Combat | generic pirate NPC boats | not live | disabled | n/a | n/a | FUTURE_DISABLED |
| Fishing | deep-sea trawling | live/cache | absent | absent | no | NOT_STARTED |
| Fishing | offshore standard Fishing | 2026-08-05 live | absent | absent | no | NOT_STARTED |
| Encounters | rescue/lost goods/ambient | cache/live | absent | absent | no | NOT_STARTED |
| Islands | launch island cross-skill content | live/cache | absent | absent | no | NOT_STARTED |
| Islands | Red Reef/Sacred Grove | 2026-05 live | absent | absent | no | NOT_STARTED |
| Islands | Wyrmscraig/Mad Angel | 2026-07/08 live | absent | absent | no | NOT_STARTED |
| Rewards | Captain's Log/collection log | cache/live | absent | absent | no | NOT_STARTED |
| Rewards | Soup pet and all rolls | cache/live | absent | absent | no | BLOCKED_BY_CAPTURE |
| Rewards | Sailing cape/max cape actions | cache/live | cache-backed released-skill mastery authority implemented; cape items, sellers, operations, and emote assets still unresolved/absent | mastery exclusion/eligibility tests pass | no | IMPLEMENTING |
| Rewards | schematics/paints/pearls/medallion | cache/live | absent | absent | no | NOT_STARTED |
| Updates | launch through 2026-08-26 deltas | official ledger | inventory only | absent | no | IMPLEMENTING |
| Persistence | owned boats/cargo/crew/tasks/PBs | vars/live | owned boat slots implemented with symbolic keys and V30; other aggregates absent | unit + PostgreSQL migration/save/load pass | no | IMPLEMENTING |
| Security | ownership, packet, atomicity, dupe | architecture | heading range, unique runtime lease/membership indexes, atomic ownership snapshots, transactional despawn | focused tests pass; matrix incomplete | no | IMPLEMENTING |
| Performance | spatial indexing and tick budgets | architecture | linear scans | absent | no | NOT_STARTED |
| Acceptance | full 1-99/live-content regression | target spec | absent | absent | no | NOT_STARTED |

## Exit rule

Full Sailing is complete only when every live row is `PASS`, every blocked row has been resolved by target evidence, future rows remain unreachable, all relevant automated suites pass, and two controlled live restarts plus multiplayer acceptance are recorded.
