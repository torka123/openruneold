# Path of Crondis obstacle evidence ledger

Date: 2026-08-28
Runtime cache: OpenRune LIVE revision 240
Map region: 15698, plane 0, source base `(3904, 5248)`

This ledger distinguishes cache-proven geometry, current OSRS public behavior, donor-derived timing,
and details that still require a live OSRS packet capture. The donor is reference material only; no
donor Java, donor IDs, or donor cache data is used at runtime.

| Mechanic | Source and confidence | Revision-240 assets / coordinates | Timing used | Implementation | Automated status | Live status |
| --- | --- | --- | --- | --- | --- | --- |
| Four waterfall paths | Revision-240 map, high | Wet waterfall `45398` at locals `(22,2)`, `(36,2)`, `(22,58)`, `(36,58)`; dry state `45399` | Existing protected 8-tick recovery | All four paths represented; fill/check/empty/destroy/palm flow preserved | Covered | Pending |
| Alternating spike emitters | Revision-240 map, high | 40 statues `45415` and 40 spears `45416`; SW/NW walls `x=21/28`, SE/NE walls `x=35/42`; spear footprints `x=23..25`, `25..27`, `37..39`, `39..41` at staggered `z=10..19` and `45..54` | Per-path 10-tick matrices from donor, medium | Four independent path definitions; exact occupied three-tile footprints | Covered | Pending |
| Spike warning/extension/retraction | Cache plus donor, medium | Statue/spear idle `9562`, statue warning `9563`, spear extension `9565` | Warning `d`; extend `d+3`; dangerous `d+4..d+6`; retract `d+7` | Native static locs animated; server danger is synchronized to the active window | Covered | Pending visual comparison |
| Poison trail geometry | Donor coordinates cross-checked against revision-240 room, medium | Gfx `2129` / sequence `9559`; bases `(23,9)`, `(37,9)`, `(23,55)`, `(37,55)`; five lanes and ten travel tiles per path | Seeded batch every 5 ticks, begins after 2 ticks, advances 3 tiles/tick, each dark tile dangerous 2 ticks | Warning visuals and dark-phase server collision are separate; only dark phase damages | Covered | Pending visual comparison |
| Run/tile skipping | Current OSRS Wiki strategy and RuneLite marker parity, high | Destination tiles only; no asset | Evaluated every game tick | Intermediate tile of a two-tile run is not treated as occupied; no interval history or delayed hit | Covered | Pending live route check |
| Obstacle stat drains | Current OSRS Wiki, high | None | On successful occupied-tile hit | Defence `-6`, Agility `-3`, Prayer `-2` | Runtime constants exercised through implementation review; engine-side live assertion pending | Pending |
| Normal water loss | Current OSRS Wiki and item page, high | Container `27295` | On successful hit | Retains floor 50%, minimum 1 when positive: `100â†’50â†’25â†’12â†’6`; empty remains 0 | Covered | Pending odd-value chat check |
| Rare water loss | OSRS item page proves a 16% reduction but not its chance; probability trace-required | Container `27295` | Existing 1-in-6 compatibility probability retained explicitly until a live capture establishes the real chance | Retains floor 84%, minimum 1 | Covered for outcome/rounding; probability marked unresolved | Blocked on live OSRS sample |
| Obstacle damage | Donor gives spike `6..14`, cloud `5..13`; live OSRS scaling trace-required | Typeless hit through OpenRune combat | One roll per accepted hit; normal raid/path scaling wrapper | Random roll replaces fixed 12; hit cooldown is 3 ticks for spikes and 2 for clouds | Range/state covered; exact OSRS distribution unresolved | Blocked on live OSRS capture |
| Perfect Crondis | Current OSRS task wording, high | None | At most 100 server ticks (60 seconds), no water lost, no crocodile palm drain | Eligibility invalidated only when water is actually lost; completion time exposed deterministically | Covered | Pending task-award integration/live check |
| Lifecycle | OpenRune room lifecycle, high | All above | Barrier initializes once; completion/reset cancels immediately | Pre-barrier inactive; seeded controller belongs to raid generation; clouds/hit cooldowns/visuals clear on completion and reset | Covered | Pending death/logout/rejoin/wipe exercise |

## Evidence limitations

The seven local Crondis RsProx sessions are revision-240 localhost/OpenRune recordings. They prove
the old server emitted rotating gfx `2129` anchors, but they are not live OSRS traces. Consequently,
the exact rare-spill probability, exact obstacle damage/scaling, exact cloud visual-to-hit frame,
and any missing sounds remain trace-required. The implementation deliberately labels those items
instead of claiming a completed 1:1 match.

## Deterministic coverage

`ToaCrondisEncounterTest` covers revision-240 asset IDs, all four waterfall coordinates, all 40
emitters, four distinct stagger matrices, the full spike phase cycle, cloud warning/movement/danger
and expiry, north/south direction, seeded unique lane selection, destination-tile run skipping,
normal and rare spill rounding, empty-container behavior, 1-8 water/crocodile scaling, pre-barrier
inactivity, completion stop, Perfect timing, and reset cleanup.
