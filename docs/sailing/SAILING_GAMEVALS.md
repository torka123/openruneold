# Sailing revision-240 gamevals

All rows below were resolved from local gamevals plus decoded `.data/cache/LIVE` definitions at revision 240. Presence is not proof of live reachability. Subrevision 5 remains unproven by local metadata.

## Core

| Symbol | Type | ID | Verified linkage | Implementation use |
|---|---|---:|---|---|
| `stat.sailing` | stat | 23 | native skill index | skill/XP registration |
| `dbrow.sailing_stat` | dbrow | 65415 | Sailing stats row | level guide/stat table |
| `component.stats:sailing` | component | 20971544 | interface stats child 24 | skill tab |
| `dbtable.sailing_boat` | dbtable | 166 | 46 named columns | boat definitions |
| `dbrow.sailing_boat_raft` | dbrow | 8110 | level 1, players 2, crew 0, 1x3, recovery 250 | raft |
| `dbrow.sailing_boat_skiff` | dbrow | 8111 | level 15, players 5, crew 2, 2x5, recovery 3,750 | skiff |
| `dbrow.sailing_boat_sloop` | dbrow | 8112 | level 50, players 10, crew 5, 3x10, recovery 50,000 | sloop |
| `dbcol.sailing_boat:entity_data` | dbcol | 10878993 | deck/hotspot tuples | decode templates |
| `dbcol.sailing_boat:direction16_acceleration` | dbcol | 10878998 | schema exists; inspected rows omit value | `BLOCKED_BY_CAPTURE` |
| `dbcol.sailing_boat:direction16_turnspeed` | dbcol | 10878999 | schema exists; inspected rows omit value | `BLOCKED_BY_CAPTURE` |
| `dbtable.sailing_dock` | dbtable | 194 | 61 dock rows | central port registry |
| `dbrow.sailing_dock_port_sarim` | dbrow | 8587 | dock id 0, level 1, sprite 7115 | Port Sarim |
| `dbtable.sailing_boat_facility` | dbtable | 176 | requirements, recipes, variants, stats | facilities |
| `dbtable.sailing_combat_facility` | dbtable | 162 | ammo, damage, cadence, animation/sound | ship combat |
| `dbtable.sailing_charting_core` | dbtable | 154 | chart behavior and links | sea charting |
| `dbtable.sailing_shipwreck` | dbtable | 203 | durability, XP, drops, transforms | salvaging |
| `dbtable.sailing_bt_trial_core` | dbtable | 143 | trial bounds, ranks, rewards | trials |
| `dbtable.sailing_sea` | dbtable | 201 | sea/ocean/levels/centres/SFX | navigation regions |

## Native interfaces

| Symbol | ID | Components | Notes |
|---|---:|---:|---|
| `interface.sailing_intro_hud` | 345 | 8 | introduction/quest HUD |
| `interface.fossilquest_sailing_overlay` | 603 | unresolved here | quest overlay |
| `interface.fossilquest_sailing` | 604 | unresolved here | quest UI |
| `interface.sailing_menu` | 72 | 4 | init script 2073 |
| `interface.sailing_bt_hud` | 931 | 27 | trial HUD |
| `interface.sailing_bt_selection` | 932 | 8 | trial selection |
| `interface.sailing_bt_statistics` | 933 | 29 | trial results |
| `interface.sailing_boat_selection` | 934 | 34 | dynamic boat selection |
| `interface.sailing_log` | 935 | 51 | Captain's Log |
| `interface.sailing_spyglass` | 936 | 5 | charting overlay |
| `interface.sailing_sidepanel` | 937 | 40 | facilities/stats/crew |
| `interface.sailing_crew` | 938 | 17 | crew management |
| `interface.sailing_customisation` | 939 | 42 | shipyard |
| `interface.sailing_boat_cargohold` | 943 | 25 | cargo main |
| `interface.sailing_boat_cargohold_side` | 944 | 9 | cargo side |

Dynamic `*` operations require script/packet tracing. Static cache ops are not enough to bind every action.

## Core state vars

| Symbol | ID | Base varp / bits |
|---|---:|---|
| `settings_sailing_side_panel_onlyonboat` | 18131 | 4849 / 11 |
| `sailing_intro` | 18314 | 4959 / 0..5 |
| `sailing_combat_preferred_ammunition` | 19101 | 5040 / 0..4 |
| `sailing_combat_last_fired_ammunition_player` | 19102 | 5041 / 0..4 |
| `sailing_boat_selection_type` | 18553 | 5004 / 0..4 |
| `sailing_boat_last_personal` | 18554 | 5003 / 0..4 |
| `sailing_player_is_on_player_boat` | 19104 | 5056 / 0 |
| `sailing_boat_facility_lockedin` | 19105 | 5056 / 1..8 |
| `sailing_boarded_boat_type_stored` | 19108 | 5056 / 11..15 |
| `sailing_boarded_boat_world_stored` | 19109 | 5056 / 16..27 |
| `sailing_boat_speedboost_duration` | 19112 | 5057 / 12..17 |
| `sailing_boarded_boat_world` | 19122 | 5059 / 8..19 |
| `sailing_boat_cargohold_warning_dismissed` | 19123 | 5059 / 20 |
| `sailing_boarded_boat` | 19136 | 5060 / 21 |
| `sailing_boarded_boat_type` | 19137 | 5060 / 22..26 |
| `sailing_boarded_boat_last_dock` | 19145 | 5062 / 0..9 |
| `sailing_boarded_boat_last_standard_dock` | 19146 | 5062 / 10..19 |
| `sailing_boarded_boat_last_mooring_point` | 19147 | 5062 / 20..29 |
| `sailing_sidepanel_tabs` | 19152 | 5111 / 1..2 |
| `sailing_shipyard_boat_angle` | 19519 | 5187 / 0..10 |
| current Sailing dock | varp 5005 | full varp; encoding/sentinel blocked by capture |
| teleport Sailing dock | varp 5006 | full varp; encoding/sentinel blocked by capture |

Runtime note: client base varp `5056` is explicitly overlaid into the revision-240 SERVER cache as temporary, always-transmitted state. Without that server definition, native varbits 19104/19105 cannot be set through OpenRune's typed varbit delegate.

## Representative assets

| Symbol | ID | Exact cache evidence |
|---|---:|---|
| `obj.sailing_log` | 31986 | View-charts, View-tasks, Destroy |
| `obj.sailing_charting_spyglass` | 31803 | Look-through, Inspect, Destroy |
| `obj.skillcape_sailing` | 31288 | Wear, Teleport, Rename boat, Drop |
| `obj.skillpetsailing` | 31283 | Soup |
| `npc.sailing_shipwright_port_sarim` | 15363 | Talk-to, Buy-boat, Customise-boat, Recover-boat |
| `loc.sailing_docking_buoy_port_sarim` | 59769 | Dock; sequence 13563 |
| Sailing dock gangplank parents | 59835..59866 | varbit 19104 transforms `[59832, 59831, 59832]`; sequence 13562 |
| Sailing dock mooring parents | 59867..59893 | dock IDs 32..58; 3x3 transform family using 59834/59833 |
| Wyrmscraig dock parents | 62397 / 62398 | same generic transform family; root/cave variants |
| Shipyard gangplank parent | 59719 | children 59721 Board and 59720 Disembark |
| `loc.sailing_gangplank_embark` | 59832 | Board, Board-previous, Board-friend |
| `loc.sailing_gangplank_disembark` | 59831 | Disembark |
| raft helm idle/in-use | 59556 / 59555 | Navigate / Stop-navigating, Escape |
| skiff helm idle/in-use | 59578 / 59577 | same native operations |
| sloop helm idle/in-use | 59600 / 59599 | same native operations |
| player helm sequences | 13340 / 13351 / 13362 | raft / skiff / sloop |
| raft world-entity idle/sink | 13424 / 13425 | exact r240 sequences |
| bronze cannonball travel | 3487 | spotanim |
| Sailing journey / advancement | 1094 / 1055 | named jingles |

## Reconciliation blockers

- Boat DB type IDs are 0/1/2 while the imported protocol values are 1/2/3. Do not equate them without packet/client proof.
- In table 166, `boat_size` is column 6 with values 1/2/3; `crew_capacity` is column 36 with values 0/2/5. The generated `SailingBoatRow` accessor and current OSRS crew-slot table agree. Do not reinterpret boat size as crew capacity.
- Imported synths 10792, 10793, 10807, and 10808 have no symbolic reverse mapping.
- Imported movement constants are not proven by inspected DB rows.
- `SailingDockDefinitionRegistry` decodes and validates all 61 table-194 metadata rows and resolves each symbolic parent and buoy loc through revision-240 gamevals.
- `SailingDockRegistry` scans the authoritative SERVER map and proves 61 unique dock parent types, 64 parent placements, 61 buoy types, and 62 buoy placements; 27 parents are dock moorings, while three other non-child moorings belong to separate activities.
- The former 32-entry manual coordinate scaffold was removed because it mixed static interaction placement with operational travel geometry. Land return, sea spawn/anchor, approach, boat rotation, and in-zone encodings remain explicitly uncaptured and fail closed.
