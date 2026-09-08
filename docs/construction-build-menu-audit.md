# Construction build-menu audit (OpenRune revision 240)

Audit date: 2026-08-25

## Scope and result

This audit covers every visible `dbtable.poh_room` / `dbtable.furniture` build-menu occurrence in the project's pinned revision-240 cache. A menu occurrence is one furniture row at one room/hotspot/menu position; the same canonical furniture can occur in multiple physical hotspots or room variants.

| Classification | Menu occurrences | Unique local definitions |
| --- | ---: | ---: |
| `VERIFIED_EXISTING` | 628 | 404 |
| `EXISTING_REQUIREMENTS_CORRECTED` | 49 | 39 |
| `EXISTING_LEVEL_CORRECTED` | 3 | 3 |
| `NEWLY_ADDED` | 89 | 56 |
| `UNRESOLVED` | 1 | 0 |
| **Total** | **770** | **502 resolved + 1 unresolved** |

Final state:

- 769 of 770 visible revision-240 POH menu occurrences resolve to a local build definition.
- All 769 resolved occurrences have an exact FurnitureRow ID, icon item ID, cache display name, `Build: <name>` hover target, Construction level, cache material list, and separately validated world-object mapping.
- Menu-order mismatches: 0. The initial comparison found 12 legacy ordering mismatches; the final comparison has none.
- Existing icon/name/hover binding corrected: 680 legacy menu occurrences. The 89 new occurrences use the corrected binding from their first registration.
- Existing material records corrected: 49 occurrences / 39 unique definitions.
- Existing level records corrected: 3 occurrences: Marble cape rack 90, Magical cape rack 99, Redwood fence 75.
- Existing XP records corrected: 0. XP remains the locally verified OSRS value because revision 240's FurnitureRow does not carry an XP column.
- Existing built-object mappings corrected: 0. Existing object mappings were retained after cache validation; newly added options introduced 89 occurrence mappings / 56 unique definitions.
- Entries unavailable in the pinned cache revision: 0 among the revision-240 source table. Content released after this cache is not invented or assigned guessed IDs.

The complete row-by-row result is [construction-buildables.csv](construction-buildables.csv).

## Revision and evidence

The configured cache revision is **240**.

Evidence used:

- `game.example.yml` declares `revision: 240`.
- `.data/cache/SERVER` is the authoritative server definition cache used by tests and runtime.
- `.data/cache/LIVE` is the matching live/client cache.
- `.data/gamevals-binary/gamevals.dat` is loaded by `GameValProvider`.
- Generated revision-specific table decoders are in `api/generated`, including `FurnitureRow` and `PohRoomRow`.
- The audit enumerates the cache's visible furniture rows directly; it does not maintain a runtime dependency on a website.

External cross-checks:

- [OSRS Construction](https://oldschool.runescape.wiki/w/Construction)
- [OSRS Constructed items](https://oldschool.runescape.wiki/w/Constructed_items)
- [OpenRune cache/config tooling](https://openrune.dev/)

The current Wiki was used for content and XP cross-checks. Numeric icon, object, DB-row, and menu values come from the local revision-240 cache. Where the current Wiki and the pinned cache differ, the pinned cache wins. One example is Beehive style 2: the current Wiki associates item 28677, while revision-240 FurnitureRow 6025 uses icon item 28676; the runtime follows row 6025 and the discrepancy is recorded here instead of silently replacing the cache value.

## Architecture discovered

1. A player clicks a hotspot object handled by `ConstructionWorld` and `ConstructionHouseService.openFurniture`.
2. The active house instance resolves the clicked coordinates, layer, and object to a `DonorHotspot`.
3. `DonorConstructionData` provides ordered internal build keys, levels, XP, materials, upgrade relationships, icon items, built-object variants, and room/hotspot object variants.
4. `ConstructionFurnitureMenuCatalog` resolves each build key to the exact revision-240 `FurnitureRow`.
5. `ConstructionHouseService.openFurniture` invokes clientscript 1404 per ordered slot and stores the exact same `ConstructionFurnitureMenuEntry` list in `PendingBuild` for the click handler.
6. The selection handler validates build mode, level/boost, tools, materials, and upgrade source; consumes materials; performs the build animation; replaces the hotspot; awards XP; and writes the selected internal key to the player's `HouseBlueprint`.
7. `replace` and `restoreHotspot` atomically remove/add dynamic objects through the existing location repository. This preserves tile, layer, shape, angle, clipping updates, nearby-player synchronization, rollback on a partial multi-piece failure, persistence, and exact hotspot restoration.
8. Saved furniture is reapplied when the house instance is reconstructed.

No separate Construction UI or object-spawn framework was added.

## Original icon and hover defect

Clientscript 1404 expects a **FurnitureRow DB-row ID** as its furniture argument. The old implementation supplied the icon item ID in that position. The client then interpreted an item ID as an unrelated DB row, so icon, name, material columns, and hover target could come from unrelated data.

The corrected binding keeps the identifier types separate:

- `furnitureRowId`: clientscript/database selection payload.
- `iconItemId`: `FurnitureRow.modelObj`, used by the row's item-model presentation.
- `displayName`: trimmed revision-240 `FurnitureRow.name`.
- `hoverText`: exactly `Build: <displayName>`.
- `builtLocIds`: world-object IDs used only for house object replacement.
- `hotspot.locIds`: hotspot world-object IDs used only for click/placement/removal matching.

The pending click payload is built from the same ordered entry list sent to the interface. This removes icon/name/click slot desynchronization. Script 1406 is called with the current option count on every open, and deterministic large-then-small payload tests verify that the second list contains no trailing selection bindings from the first.

## Data changes

Verified revision-240 rows were added for the missing room/hotspot options, including the missing kitchen sink/spice rack, mounted display bases, portal nexus furniture, League Hall furniture and all three physical pedestal variants, Quest list, Deadman/Raging Echoes rugs, Raging Echoes curtains, Gnome Child and Grid Master icons, Marble lectern, pumpkins, both beehive styles, Greenman furniture, and the Annihilation exit portal.

The Annihilation portal enforces its cache materials, including blueprint item 33122. The current house state has no separate theme-unlock variable, so no additional guessed theme gate was added.

The 39 corrected requirement definitions are:

`ANCIENT_ALTAR`, `BASIC_JEWELLERY_BOX`, `CAT_BLANKET`, `COMBAT_RING`, `CW_ARMOUR_1`, `CW_ARMOUR_2`, `CW_ARMOUR_3`, `DAGANNOTH`, `DARK_ALTAR`, `DEMON`, `DESECRATED_HOUSE`, `FLAME_PIT`, `GILDED_ALTAR`, `GILDED_CLOCK`, `GREATER_MAGICAL_BALANCE`, `KALPHITE_SOLDIER`, `LESSER_MAGICAL_BALANCE`, `MAHOGANY_DEMON_LECTERN`, `MAHOGANY_EAGLE_LECTERN`, `MAHOGANY_FEEDER`, `MEDIUM_MAGICAL_BALANCE`, `MOUNTED_EMBLEM`, `OBSIDIAN_BENCH`, `OPULENT_DINING_TABLE`, `ORNATE_REJUVENATION_POOL`, `POLAR_HABITAT`, `POND`, `RESTORATION_POOL`, `RUNE_CASE_1`, `RUNE_CASE_2`, `RUNE_CASE_3`, `RUNE_DRAGON`, `SCRYING_POOL`, `STEEL_DRAGON`, `TEAK_DRESSER`, `TEAK_FEEDER`, `TOK_XIL`, `VOLCANIC_HABITAT`, and `VOLCANIC_THEME`.

The cache uses item 32923 as its generic-nails material token. The existing runtime's generic nail placeholder is item 4819. Validation normalizes that one intentional representation difference; no object ID is used as an inventory icon or material.

## Explicitly classified non-menu definitions and exclusions

Eighteen imported definitions intentionally remain outside the active base build menus:

- 11 contextual mounted trophy payloads: eight mounted heads and three mounted fish. Revision 240 exposes the display furniture in the base menu; the mounted payload is contextual state, not a second base display option.
- 4 occult-altar internal/upgrade paths. Their source-relative/source-absolute upgrade state is not a separate base hotspot row.
- 3 Chambers of Xeric storage-unit definitions (`SMALL_STORAGE_UNIT`, `MEDIUM_STORAGE_UNIT`, `LARGE_STORAGE_UNIT`), which are not POH hotspot furniture.

Six related Construction systems were inspected and excluded as separate scopes rather than folded into this manifest: room creation, flatpack manufacture/installation, outdoor STASH units, Mahogany Homes repairs, quest/temporary construction, and minigame/non-POH construction.

## Unresolved mapping

### Trailblazer rug

- Room/hotspot: League Hall rug, room type 29, hotspot row 6146, menu order 3.
- FurnitureRow: 6012.
- Canonical name: Trailblazer rug.
- Icon item: 25364.
- Materials: 4 cloth (8790) and Trailblazer rug item 25096.
- Hotspot objects: 41031, 41032, 41033.
- Candidate built world objects: 41132 through 41154 (23 cache/Wiki variants; the cache also contains adjacent historical object 41131, which current Wiki data does not classify as a current variant).
- Evidence inspected: local FurnitureRow/PohRoomRow, local item and object definitions/models/transforms, generated gamevals, the League Hall room/hotspot table, and current Wiki constructed-item/object data.
- Reason unresolved: the current OpenRune house blueprint does not store the Trailblazer style/unlock variant needed to choose one of 23 visual world objects. Selecting one numeric object by proximity would violate the no-guessing requirement.
- Required to resolve: the exact revision-240 varbit/enum/client preference that selects the player's Trailblazer rug variant, plus a persisted house-style field and deterministic variant-to-object mapping.

This one row is not registered in the live menu, so players cannot build an incorrectly mapped object.

## Automated validation

The tests validate:

- duplicate buildable keys, hotspot keys, hotspot options, and selection payloads;
- every active option's exact FurnitureRow, icon item, canonical name, hover string, Construction level, and material multiset;
- every referenced icon/material item definition and every positive built/hotspot object definition against revision 240;
- positive material quantities, valid upgrade sources, non-negative XP, and at least one concrete built object;
- representative low/mid/high, nail, special-material, mounted, garden, dungeon, portal, storage, stairs, modern-room, and special-prerequisite options;
- modern room/hotspot order, all three League pedestal world-object variants, menus with many options, large-then-small payload clearing, icon/name/click alignment, and exact hover generation;
- exact 770/769/0/1 audit totals and zero menu-order mismatches;
- the fixed set of 18 intentionally non-menu definitions, so a build definition cannot silently disappear from classification.

Dynamic placement/removal continues through the previously working `LocRepository` transaction path. The cache tests validate every object variant that path may place or restore. This repository does not provide a headless revision-240 client renderer, so final visual icon framing and in-client cursor rendering still require the owner's normal RSProx gameplay pass.

## Verification command ledger

Final verification commands:

```text
.\gradlew.bat :content:skills:construction:test --tests org.rsmod.content.skills.construction.ConstructionCatalogValidationTest --console=plain
.\gradlew.bat :content:skills:construction:test --tests org.rsmod.content.skills.construction.ConstructionCatalogAuditOutputTest --console=plain
.\gradlew.bat :content:skills:construction:test --rerun-tasks --console=plain
.\gradlew.bat :content:skills:construction:build --console=plain
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\openrune-doctor.ps1
.\gradlew.bat :server:app:run --no-daemon
```

Audit generation also used the local `generate_construction_csv.ps1` helper against the JUnit XML output. Read-only investigation used `rg`, `Get-Content`, `Select-String`, `Import-Csv`, cache-definition probes, targeted process/CIM inspection, and the repository doctor. The final report and manifest are static repository artifacts; runtime menu opening has no network dependency.

## Changed implementation areas

- `ConstructionFurnitureMenuCatalog.kt`: revision-240 FurnitureRow resolution and typed row/icon/name/hover binding.
- `ConstructionHouseService.kt`: exact row payload, ordered pending selection binding, and existing atomic placement/removal path.
- `DonorConstructionData.kt`: missing definitions/menu registration, exact built variants, 39 material corrections, and 3 level corrections.
- `ConstructionState.kt`, `ConstructionWorld.kt`, and module build configuration: restored/retained house persistence and plugin integration required by the catalog path.
- Construction catalog tests: exhaustive cache audit, unresolved cache probe, representative and invariant validation.
- `docs/construction-buildables.csv`: complete 770-row manifest.

No unrelated Construction interface redesign, house-layout change, or visual cleanup was performed.
