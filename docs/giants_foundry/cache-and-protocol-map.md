# Giants' Foundry cache and protocol map (revision 240)

Status vocabulary used here:

- **verified**: decoded directly from the active revision-240 `LIVE` cache or its map archive.
- **corroborated**: supported by raw-cache aliases and an older spawn dump, but reverse game-value resolution must remain authoritative.
- **missing**: not yet proven from revision 240; implementation must not guess.

## Cache authority and audit evidence

- `game.yml`: revision `240`, environment `LIVE`.
- Queried cache: `.data/cache/LIVE`.
- Definition loader reported 16,347 NPCs (252 undecodable notices), 33,982 items, 19,108 varbits, 5,728 varps, 14,489 sequences, 5,874 enums, 85 health bars, 83 hit splats, 3,987 structs, 16,838 DB rows, and 248 DB tables.
- Relevant build/query tasks completed successfully:
  - `:or-cache:queryToaDefinitions` with Giants' Foundry terms.
  - `:or-cache:queryToaClientScripts` for scripts 6083, 6092, 6093, 6095, 6098, 6108, 6111, 6112, and 6113.
  - Temporary read-only map decoder for map square `(52,179)`; it was used only for this report and then removed.

Raw-cache files are useful for internal-name and spawn-source corroboration, but they are not a substitute for runtime numeric decoding.

## Coordinates and NPCs

Coordinate conversion used by the map/spawn source is `worldX = zoneX * 64 + localX`, `worldY = zoneY * 64 + localY`.

| Role | Internal/cache evidence | World coordinate | Status |
|---|---|---:|---|
| Kovac outside | `npc.giants_foundry_kovac_multi_outside` in `.data/raw-cache/map/npcs/kharid_desert_region.toml`, zone `0_52_49_33_11`; old spawn dump identifies ID 11494 with variants 11467/11468 | `(3361,3147,0)` | corroborated |
| Kovac workshop | `npc.giants_foundry_kovac_multi` in `.data/raw-cache/map/npcs/giants_foundry.toml`, zone `0_52_179_41_30`; old spawn dump identifies ID 11560 with variants 11472/11470 | `(3369,11486,0)` | corroborated |

Verified NPC definitions:

| ID | Name / form | Options |
|---:|---|---|
| 11466 | `<col=00ffff>Smithing catalyst</col>` | none |
| 11468 | Kovac (quest form) | Talk-to |
| 11469 | Kovac | none |
| 11470 | Kovac | Talk-to |
| 11472 | Kovac (activity form) | Talk-to, Hand-in, Commission, Shop |

`osrs-dumps/dump.npc` names the forms `giants_foundry_kovac_fake_attack`, `..._quest`, `..._quest_cutscene`, `..._1op`, `..._normal`, `..._multi_outside`, and `..._multi`. The outside multi transforms on `sleeping_giants`; the workshop multi transforms to the one-option/normal activity forms. The exact multi-NPC numeric IDs should be regenerated from the current game-value map rather than copied from the old CSV.

## Activity location definitions

### Verified interactive IDs

| ID | Definition | Options | Definition animation |
|---:|---|---|---:|
| 43860 | Broken grindstone | Repair | none decoded |
| 43861 | Repaired grindstone | Inspect | none decoded |
| 43862 | Broken polishing wheel | Repair | none decoded |
| 43863 | Repaired polishing wheel | Inspect | 9445 |
| 43864 | Crucible | Inspect | none decoded |
| 43865 | Mould jig | Inspect | none decoded |
| 44604 | Preform storage | Inspect | none decoded |
| 44608 | Broken grindstone | none | none decoded |
| 44609 | Broken polishing wheel | none | none decoded |
| 44611 | Crucible | none | none decoded |
| 44612 | Mould jig | none | none decoded |
| 44616 | Preform storage | none | none decoded |
| 44619 | Trip hammer | Use | 9449 |
| 44620 | Grindstone | Use | 9445 |
| 44621 | Polishing wheel | Use | 9445 |
| 44622 | Crucible (empty) | Fill, Check, Empty | none decoded |
| 44623 | Crucible (partially full) | Fill, Check, Empty | none decoded |
| 44624 | Crucible (full) | Pour, Check, Empty | none decoded |
| 44625 | Mould jig (Empty) | Setup | none decoded |
| 44626 | Mould jig (Setup) | Check | none decoded |
| 44627 | Mould jig (Poured metal) | Pick-up | none decoded |
| 44628 | Mould jig (Cooling metal) | Pick-up | none decoded |
| 44629 | Mould jig (Cooled metal) | Pick-up | none decoded |
| 44630 | Bank chest | Use, Collect | none decoded |
| 44631 | Lava pool | Heat-preform, Dunk-preform | 3743 |
| 44632 | Waterfall | Cool-preform, Quench-preform | 7024 |
| 44633 | Preform storage | Store-preform | none decoded |
| 44634 | Preform storage | Take-preform | none decoded |
| 44636 | Exit | Exit | none decoded |
| 44683 | Moulds | none | none decoded |

The animation column above is the location definition's own animation, not a verified player action sequence.

### Verified static placements in map square `(52,179)`

| ID | Name | Coordinate | Shape | Angle |
|---:|---|---:|---:|---:|
| 33309 | Pile of Buckets | `(3361,11483,0)` | 11 | 0 |
| 44619 | Trip hammer | `(3363,11496,0)` | 10 | 3 |
| 44620 | Grindstone | `(3361,11491,0)` | 10 | 0 |
| 44621 | Polishing wheel | `(3362,11484,0)` | 10 | 0 |
| 44630 | Bank chest | `(3375,11493,1)` | 10 | 1 |
| 44631 | Lava pool | `(3372,11497,0)` | 10 | 2 |
| 44632 | Waterfall | `(3357,11488,0)` | 10 | 3 |
| 44636 | Exit | `(3364,11482,0)` | 10 | 2 |
| 44683 | Moulds | `(3371,11486,0)` | 10 | 2 |
| 44726 | Chest | `(3372,11484,0)` | 11 | 0 |
| 44727 | Hammer | `(3362,11482,0)` | 10 | 3 |
| 44727 | Hammer | `(3374,11486,0)` | 10 | 1 |
| 44728 | Wheelbarrow | `(3370,11484,0)` | 11 | 1 |
| 44761 | Waterwheel | `(3354,11495,0)` | 10 | 3 |
| 44761 | Waterwheel | `(3356,11485,0)` | 10 | 3 |
| 44762 | Belt system | `(3361,11494,0)` | 10 | 0 |
| 44762 | Belt system | `(3362,11487,0)` | 10 | 0 |
| 44763 | Belt system | `(3362,11495,0)` | 10 | 3 |
| 44772 | unnamed wall definition | `(3365,11481,0)` through `(3367,11481,0)` | 0 | 1 |

IDs 44622-44629 and 44633-44634 were not statically present in this decoded map square. Their exact placement/transform relationship is therefore **missing**. Verify their multloc controlling vars or the server's intended player-local loc behavior before registering them. Do not spawn all states globally.

## Items

| ID | Item | Options / evidence | Status |
|---:|---|---|---|
| 27010 | Preform | raw-cache alias `obj.giants_foundry_preform` | verified |
| 27012 | Double ammo mould | existing cannonball integration | verified |
| 27014 | Kovac's grog | Drink; DB row reference `kovacs_grog=64462` | verified |
| 27017 | Smithing catalyst | existing Smithing integration | verified |
| 27019 | Ore pack | Open, Destroy; raw-cache alias `obj.giants_foundry_ore_pack` | verified |
| 27021 | Colossal blade | Wield; raw-cache alias `obj.giants_foundry_colossal_blade` | verified |
| 27693 | Ore pack | Open, Destroy; second cache definition | verified definition, unresolved reward use |

The reward shop's authoritative ore-pack variant is **missing** until the revision-240 shop enum/DB row is decoded. Do not award both IDs or choose one merely from the display name.

The Smiths' uniform item IDs and all mould item/enum mappings still require a focused reverse game-value query. Existing server code recognizes the uniform by internal names, but numeric IDs were not proven in this audit.

## Native interfaces and components

### Interface 718 — `giants_foundry_mould`

| Child | Internal name | Options / role |
|---:|---|---|
| 5 | `reset_button` | Reset |
| 6 | `set_button` | Set |
| 13 | `commission_layer` | commission section |
| 21 | `layout_part1` | View |
| 22 | `layout_part2` | View |
| 23 | `layout_part3` | View |
| 26 | `commission_title` | text `Commission` |
| 27 | `keyword1` | first commission word |
| 28 | `keyval1` | first score/value |
| 29 | `keyword2` | second commission word |
| 30 | `keyval2` | second score/value |
| 32 | `reset_text` | `Reset Mould` |
| 34 | `set_text` | `Set Mould` |

### Interface 753 — `giants_foundry_reward_shop`

| Child | Internal name | Options / role |
|---:|---|---|
| 11 | `points_border` | points frame |
| 12 | `points_title` | `Foundry Reputation` |
| 13 | `points_value` | initial text `0000` |
| 14 | `points_layer` | points layer |
| 15 | `buy_button` | Buy |
| 16 | `border` | selection border |
| 17 | `buy_button_text` | `Buy` |

### Interface 754 — `giants_foundry_hud`

| Child | Internal name | Role |
|---:|---|---|
| 1 | `quality_icon` | quality icon |
| 2 | `temperature_icon` | temperature icon |
| 5 | `quality_layer` | quality group |
| 6 | `quality_bar` | quality bar |
| 7 | `quality_pointer` | quality pointer |
| 19 | `polish_bar` | polish section |
| 20 | `grind_bar` | grind section |
| 21 | `hammer_bar` | hammer section |
| 31 | `hammer_bar_highlight` | hammer highlight |
| 32 | `temperature_layer` | temperature group |
| 45 | `quality_bar_lost` | lost-quality overlay |
| 46 | `quality_bar_fill` | quality fill |
| 47 | `fill` background | bar background |
| 61 | `grind_bar_highlight` | grind highlight |
| 71 | `polish_bar_highlight` | polish highlight |
| 73 | `temperature_bar` | temperature bar |
| 74 | `temperature_pointer` | temperature pointer |
| 75 | `completion_bar` | completion bar |
| 76 | `completion_bar_highlight` | completion highlight |
| 77 | `completion_bar_cover` | completion cover |
| 78 | `completion_pointer` | completion pointer |

Use named components once revision-240 game values are generated. If packed IDs are temporarily needed for diagnostics, calculate them as `(interfaceId << 16) | child`; do not scatter literal packed values through content code.

## Client scripts

| Script | Cache name / observed purpose | Arguments / evidence |
|---:|---|---|
| 6083 | reward shop setup | one integer; reads varp 3435 and varbit 13915, invokes scripts 6109/6086/6089/6091 |
| 6092 | mould interface init | no args; installs hook and calls 6093 |
| 6093 | mould content setup | no args |
| 6094 | mould content processing | signature not fully decoded |
| 6095 | mould tab select | one integer; writes varbit 13909; plays synth 2266 |
| 6096 | mould list processing | signature not fully decoded |
| 6097 | mould list item | signature not fully decoded |
| 6098 | mould selection | one integer; reads enums 4373/4374/4375 and writes varbits 13910/13911/13912; plays synth 2266 |
| 6099 | mould unlocked predicate | signature not fully decoded |
| 6100 | mould usable predicate | signature not fully decoded |
| 6101 | mould list item bar | signature not fully decoded |
| 6102 | commission processing | signature not fully decoded |
| 6106 | layout processing | signature not fully decoded |
| 6107 | button processing | signature not fully decoded |
| 6108 | mould reset selection | no args; clears selected mould varbits |
| 6111 | HUD init | no args |
| 6112 | HUD rescale | no args |
| 6113 | HUD update | no args |
| 6114 | HUD rescale processing | signature not fully decoded |
| 6115 | HUD pointer processing | signature not fully decoded |
| 6119 | HUD section count | signature not fully decoded |

The interface should be driven by these native scripts and player vars, not rebuilt as a custom widget. The exact argument schemas for scripts marked incomplete are **missing** and must be confirmed by opcode/stack tracing before calls are added.

## Varps and varbits

### Varps

| ID | Internal name |
|---:|---|
| 3428 | `varp.giants_foundry_main` |
| 3429 | `varp.giants_foundry_state_1` |
| 3430 | `varp.giants_foundry_state_2` |
| 3431 | `varp.giants_foundry_crucible` |
| 3432 | `varp.giants_foundry_preform_1` |
| 3433 | `varp.giants_foundry_preform_2` |
| 3435 | `varp.giants_foundry_reward_shop_selected_item` |
| 3436 | `varp.giants_foundry_reward_shop_points` |

Varp 3434 was not identified in the filtered name output. Its use, if any, is **missing**.

### Varbits

| ID | Internal name | Base / bits |
|---:|---|---|
| 13902 | `sleeping_giants` | 3428, 0..5 |
| 13903 | `sleeping_giants_tutorial` | 3428, 6..15 |
| 13904 | `repair_hammer` | 3428, 16..17 |
| 13905 | `repair_grind` | 3428, 18..19 |
| 13906 | `repair_polish` | 3428, 20..21 |
| 13907 | `commission_word_1` | 3429, 0..2 |
| 13908 | `commission_word_2` | 3429, 3..5 |
| 13909 | `mould_current_tab` | 3429, 6..7 |
| 13910 | `selected_ricasso` | 3429, 8..12 |
| 13911 | `selected_blade` | 3429, 13..17 |
| 13912 | `selected_tip` | 3429, 18..22 |
| 13913 | `mould_alloy_dificulty` | 3429, 23..30 |
| 13914 | `mould_state` | 3430, 0..1 |
| 13915 | `reward_shop_filter` | 3430, 2..4 |
| 13916..13920 | unlocked ricasso 7..11 | 3430, 5..9 |
| 13921..13925 | unlocked blade 7..11 | 3430, 10..14 |
| 13926..13930 | unlocked tip 7..11 | 3430, 15..19 |
| 13931 | `bronze_count` | 3431, 0..4 |
| 13932 | `iron_count` | 3431, 5..9 |
| 13933 | `steel_count` | 3431, 10..14 |
| 13934 | `mithril_count` | 3431, 15..19 |
| 13935 | `adamant_count` | 3431, 20..24 |
| 13936 | `rune_count` | 3431, 25..29 |
| 13937 | `crucible_state` | 3431, 30..31 |
| 13938 | `preform_dificulty` | 3432, 0..7 |
| 13939 | `preform_quality` | 3432, 8..15 |
| 13940..13945 | `seed_1` through `seed_6` | 3432, 16..27, two bits each |
| 13946 | `seed_0` | 3432, 28..29 |
| 13947 | `preform_stored` | 3432, 30..31 |
| 13948 | `temperature` | 3433, 0..9 |
| 13949 | `completion` | 3433, 10..19 |
| 13950 | `start_quality` | 3433, 20..27 |
| 13957 | `collection_minigames_giantsfoundry_completed` | 2948, bit 26 |

Names retain cache spelling (`dificulty`). Do not silently create corrected aliases unless the game-value generation convention requires it.

## Enums, DB rows, animations, graphics, projectiles, and sounds

Verified:

- Enums 4373, 4374, and 4375 are read by the mould-selection client script for ricasso/blade/tip data.
- Synth 2266 is played by native tab/selection scripts.
- Location definition animations 9449, 9445, 3743, 7024, 9450, and 9446 occur on relevant workshop objects/scenery.

Still missing and prohibited from guesswork:

- Enum row schemas and the complete mould names, keywords, unlock levels, score matrices, and display-item mappings.
- Reward-shop enum/DB row, prices, selected item mapping, and the authoritative ore-pack variant.
- Player hammer/grind/polish/heat/cool/pour/pick-up/store/take animations.
- Any activity graphics, projectiles, non-UI sounds, exact timings, and facing/forced-movement sequences.
- Health-bar/hit-splat usage, if any.
- Exact loc transform chains for crucible, mould jig, and preform storage state.

These must be decoded by internal name/reference from revision 240 before mechanics are implemented. A location's ambient animation is not evidence for the player's action animation.

## Protocol and synchronization rules

1. Use the generated `VarPlayerIntMap` names and ordinary var sync; do not emit raw custom packets for Foundry state.
2. Use the cache-native interfaces 718/753/754, their named components, and normal `onIfModalButton`/`onIfClose` routing.
3. Invoke only client scripts whose stack arguments have been proven. A wrong int/string stack is a client-disconnect risk.
4. Resolve Kovac through the normal player-var multi-NPC path.
5. Treat crucible/mould/preform state as player-owned. Do not broadcast private state with the global `LocRepository`.
6. Use standard region loc synchronization only for genuinely shared state; it already handles clipping and nearby-player broadcasts.
7. Update authoritative server state first, then the packed varps/varbits/HUD in one tick. Avoid two competing writers.
8. On login/re-entry, send a full state snapshot before opening the HUD. On activity exit/collection, close the overlay and clear only transient display vars after persistence settles.

## Mapping checklist

| Asset group | Status | Required next proof |
|---|---|---|
| Revision/environment/cache authority | verified | none |
| Workshop and outside Kovac locations | corroborated | reverse-resolve current multi NPC IDs |
| Core station loc IDs/options | verified | prove transforms/player-local state |
| Static workshop coordinates | verified | verify clickable reachability in live boot |
| Core reward item IDs | verified | resolve duplicate ore pack |
| Native interfaces/components | verified | generate/use named components |
| Core varps/varbits | verified | determine any use of varp 3434 |
| Client scripts | partially verified | decode incomplete argument schemas |
| Mould enums/data | missing | decode enums 4373/4374/4375 and linked data |
| Reward shop table/prices | missing | decode linked enum/DB row |
| Player animations/GFX/projectiles/sounds | missing | internal-name/reference audit in rev 240 |
| End-to-end protocol behavior | missing | live client tests with two players and relog |

No implementation should be called cache-accurate until every `missing` row needed by that behavior is resolved or explicitly documented as an unavailable revision-240 asset.
