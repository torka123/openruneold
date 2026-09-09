# Collection Log selection crash audit — revision 240

Date: 2026-09-09. Scope: the existing Collection Log interface and every selectable collection.
Status: implementation, automated checks, rebuild and two controlled restarts complete.
This is not a claim of fresh desktop-client visual verification.

Follow-up: the original close/reopen test below was modal-only and missed the journal overlay
X-button path. That subsequently reported dead click is fixed and independently regression
tested in [the close-button audit](close-button-audit.md).

## Reproduced defects and corrections

1. **Vorkath page selection throws on an unmapped counter.**
   The all-section baseline reproduced `IllegalStateException: Missing mapping for key:
   'varp.kc_vorkath'` for Vorkath with empty, partial and complete ownership.
   The other 123 sections selected without that exception.
   `CollectionLogUi.SPECIAL_COUNTER_VARPS` now reads the actual cache-backed
   `varp.total_vorkath_kills`, ID **1691**, permanent scope.
   The Vorkath completion writer and both head-drop conditions used the same obsolete name;
   those references now use that same native counter. No new varp, persistence store or alias
   was introduced. Vorkath combat mechanics and drop rates were not changed.
   Historical runtime evidence independently records the same mapping error in the kill writer:
   `logs/cerberus-audit-20260907/restart-36.stdout.log:1414` and stack frame at line 1419.
   That historical stack is a boss-death error, not a recorded Collection Log client crash.

2. **Delayed section clicks can be applied to the wrong tab.**
   A real `If3Button` test selected Bosses, switched to Raids, then delivered an old Bosses
   section click. The old code interpreted its section index inside Raids.
   Both modal and overlay handlers now pass the source category; the UI rejects mismatches.
   A section selection also requires an existing UI session. Invalid indexes and clicks after
   native interface closure do not redraw or create a session.

3. **Invalid catalog references were only discovered on individual page selection.**
   Startup validation now checks all category and section structs, enumeration order,
   draw-widget references, display items and exceptional counters against the active cache.
   Incompatible assets fail with a diagnostic during script startup, before users can select
   a broken section. This is validation of the authoritative catalog, not a second catalog.

## Existing native paths used

- Selection: `CollectionLogScript` → native `If3ButtonHandler` /
  `ProtectedAccessLauncher` → `CollectionLogUi`.
- Open/close: existing `ifOpenMainModal`, `ifOpenOverlay`, `ifClose` and interface events.
- Ownership and persistence: existing `CollectionLogService` / `CollectionLogState`, unchanged.
- Counts: existing `VarPlayerIntMapSetter` and permanent cache-backed varps.
- Output: existing `UpdateInventory.updateInvFull` and `runClientScript`, encoded by the
  revision-240 RSProt desktop codecs. No engine API or combat hook was added.

## Coverage and evidence

The checked-in authoritative `api/collection-log/src/main/resources/collection-log/catalog-r240.json`
contains **124 sections**, **1,921 display slots**, and **1,712 unique canonical entries**.

| Tab | Sections | Display slots |
| --- | ---: | ---: |
| Bosses | 57 | 381 |
| Raids | 3 | 67 |
| Clues | 11 | 674 |
| Minigames | 22 | 261 |
| Other | 31 | 538 |

`CollectionLogClickTest` drives the actual registered incoming packet handlers, rather than
asserting that source contains particular strings. Every section is selected normally and
through search, in modal and overlay modes, for empty, partially filled and completed logs:
**1,488 section/search selections**. It checks:

- The selected player session and absence of client disconnect/coroutine leakage.
- Correct inventory-before-draw ordering and every inventory item ID/quantity.
- Real encoded inventory payloads, including large sections, empty slots and 255+ quantities.
- Exact seven-integer draw arguments and encoded argument order.
- All **29** search keyboard controls in both interface modes; **111,418** encoded search-item
  payloads checked (29 × 2 × 1,921), plus same-tick refresh throttling.
- Invalid indexes, previous-tab clicks, and five close/reopen cycles.

Additional executable tests validate Vorkath counter ID/scope, log counter projection,
a rewarded kill advancing 49 → 50 exactly once despite duplicate death callbacks,
and guaranteed/tertiary head conditions at KC **0, 1, 49, 50, 51 and 100**.

`CollectionLogR240SelectionAuditTest` reads **the served LIVE JS5 cache**, not only server
definitions. All section structs/item enums match the catalog. It follows note, placeholder,
linked-item and stack-count variants, successfully decoding **4,380 item definitions** and
checking the presence of **1,280 referenced nonzero inventory models**.
It also decodes script argument headers/instruction boundaries/branch destinations for
draw scripts **7797 → 7798 → 2731 → 2732**, search **4100**, and close reset **2158**.
The emitted inventory is **620**, collection interface **621**, close **621:1**.
Script argument counts are respectively **7, 7, 8, 3, 4, 0**.
These are cache/encoder checks; models were not rendered in a desktop client.

The pre-existing six Collection Log cache audit classes also pass (catalog, counters,
overview, protocol, ranks and Wiki-mismatch allowlist).
No current external Wiki claim is needed to correct this internal mapping/selection bug.

## Commands and results

Baseline:
```powershell
.\gradlew.bat :content:interfaces:collection-log-interface:test --tests '*CollectionLogUiProtocolTest' --no-daemon --console=plain
```

Failed as intended on the new exhaustive selection test before the fix;
`logs/collection-log-baseline-sections.log`. The real stale-tab regression was reproduced
before its correction in `logs/collection-log-clicks-2.log`.
Intermediate test compilation/format-command errors were corrected before the final run.

Scoped formatting (only the ten Kotlin files listed in `click-crash-format-files.txt`):
```powershell
.\gradlew.bat -I scripts/format-selected.init.gradle '-PopenruneFormatManifest=docs/collection-log/click-crash-format-files.txt' :content:interfaces:collection-log-interface:spotlessKotlinApply :content:bosses:vorkath:spotlessKotlinApply :content:drops:spotlessKotlinApply :or-cache:spotlessKotlinApply --no-daemon --console=plain
```

Passed in 13 seconds; `logs/collection-log-format-2.log`.

Final checks and complete server assembly:
```powershell
.\gradlew.bat -I scripts/format-selected.init.gradle '-PopenruneFormatManifest=docs/collection-log/click-crash-format-files.txt' :content:interfaces:collection-log-interface:check :api:collection-log:check :content:bosses:vorkath:check :content:drops:check :api:net:test :or-cache:test --tests 'dev.openrune.CollectionLog*' :or-cache:check :server:app:assemble --no-daemon --console=plain
```

**BUILD SUCCESSFUL in 1m 33s**, 474 tasks.
Formatting checks in this invocation are manifest-scoped.
Cache tests are explicitly Collection Log-filtered, not the entire cache test suite.
Log: `logs/collection-log-final-check-build.log`.

| Module | Tests | Failures/errors/skips |
| --- | ---: | --- |
| Collection Log interface | 21 | 0 / 0 / 0 |
| Collection Log API | 10 | 0 / 0 / 0 |
| Vorkath | 103 | 0 / 0 / 0 |
| Drops | 47 | 0 / 0 / 0 |
| Network API | 86 | 0 / 0 / 0 |
| Collection Log cache audits | 8 | 0 / 0 / 0 |
| **Total** | **275** | **0 / 0 / 0** |

Per-section packet and LIVE-cache results are recorded in the standard JUnit XML outputs for
`CollectionLogClickTest` and `CollectionLogR240SelectionAuditTest`.

## Deployment and source preservation

Pre-edit doctor: `logs/collection-log-doctor-before.log`, canonical JVM working directory
verified (old server PID 27660). That process was stopped normally before production edits.
The rebuilt server is launched using `:server:app:run --no-daemon --console=plain`.

| Run | JVM PID | Local ready time | Startup duration | Result |
| --- | ---: | --- | --- | --- |
| Rebuilt startup | 39524 | 03:04:41 | 19.617 seconds | Custom marker, 432 scripts, ready |
| Controlled restart 2026090909 | 17964 | 03:06:57 | 18.651 seconds | Custom marker, 432 scripts, ready |
| Controlled restart 2026090910 | 39324 | 03:08:08 | 18.754 seconds | Custom marker, 432 scripts, ready |

Both restarts used `scripts/cerberus-controlled-restart.ps1` with the verified running PID;
no forced process kill was used. Doctor reports `collection-log-doctor-startup.log`,
`collection-log-doctor-restart-1.log` and `collection-log-doctor-restart-2.log` under `logs/`
confirm the canonical JVM working directory and revision. Startup marker is at stdout line
1324 and script-load/ready messages at 1345–1346 in all three logs. Startup stdout is
`logs/collection-log-startup.stdout.log`; restart stdout is under
`logs/cerberus-audit-20260907/restart-2026090909.stdout.log` and
`restart-2026090910.stdout.log`.
Final PID **39324** owns listeners **43594**, **8080** and **9091**.

All **11** selected source/build files, **4** deployment JARs, and **146** existing
dirty/untracked files retained their recorded hashes across build/startup and restarts.
Before snapshots are `logs/collection-log-{source,artifact,worktree}-hashes-before-restarts.json`;
verified after snapshots end in `-after-restarts-verified.json`.
The first source-snapshot reader encountered PowerShell's serialized string metadata;
the verified comparison normalizes the recorded path value without changing any source.
Only the verified after snapshots are the successful comparison evidence.

Deployment JAR SHA-256 values:

| Artifact | SHA-256 |
| --- | --- |
| server.jar | `D4AF3C59F47C63A4F3586999B54D10B76F7E8A8ACC7AE83D68DF997E322D6CDC` |
| collection-log-interface | `E2EE91AB9F5078F270F4723106C9D204AE97A50202C2308B4DF229A03F1EC5B5` |
| vorkath | `EADA42B9117240BA37B4C1576EDE0AD7450AE6ADB664BD8FD693DC129319C5F3` |
| drops | `E4C136F1BDDA9B726D4C6E842BBB0375ECE3D56E9A3E3945E3F1724D57D5132D` |

The existing shutdown-order warning recurred: `Game database service failed to shut down`
with `Connection reset by peer` (first two stdout logs, line 1404 onward). The normal process
shutdown and subsequent server readiness succeeded; this is not claimed as a clean database
shutdown audit. No Collection Log mapping/startup exception appeared in these launches.

Cache revision **240**, SERVER `.data/cache/SERVER`, LIVE `.data/cache/LIVE`, and PostgreSQL
authority `.data/postgres` remain the same. No account inventory/bank migration was performed.
No Royal Titans, wall-casting or Home Teleport source was changed in this Collection Log pass.
SERVER dat2 SHA-256 remains `A77ED0C2DFD48B093050913041B098FB4A700D6A1C32DAECABFCD2A6DD81696C`;
LIVE remains `FD0FE5EF66039A791A900EDDDF1CDBCFE57E09E8C32EA3496C69E96217A41A7B`.

`git diff --check` passed after the edits. Nothing was staged, committed, reset or restored
from Git. The formatter also normalized layout/imports within the selected existing files;
those mechanical edits do not change Vorkath combat behavior.

## Files changed in this pass

- `content/interfaces/collection-log-interface/build.gradle.kts`: native packet test dependencies,
  serial cache-backed tests.
- That module's `CollectionLogUi.kt`, `CollectionLogScript.kt`,
  `CollectionLogUiProtocolTest.kt`, `CollectionLogUiProjectionTest.kt`,
  and new `CollectionLogClickTest.kt`.
- `content/bosses/vorkath/.../VorkathEncounterManager.kt` and its existing test.
- `content/drops/.../VorkathDropTable.kt` and its existing test.
- New `or-cache/src/test/kotlin/dev/openrune/CollectionLogR240SelectionAuditTest.kt`.
- This report and `click-crash-format-files.txt`.

Exact Kotlin source paths are in the formatting manifest. Other dirty/untracked work belongs
to the existing worktree and is not claimed as part of this fix.

## Remaining verification limit

No fresh desktop-client crash/visual reproduction was completed. The all-page handler and
wire/cache audit identifies and fixes the reproducible server failures, but does not prove
that every possible client rendering fault is absent. If the reported symptom is a whole
client process exit on another page, its collection name and client error log are still
needed to tie that distinct symptom to a specific cause.

## Every audited section

Each row passed the regular/search packet selections in all six mode/ownership combinations
and its LIVE item-enumeration audit.

| Tab | Section | Display slots |
| --- | --- | ---: |
| Bosses | Abyssal Sire | 9 |
| Bosses | Alchemical Hydra | 11 |
| Bosses | Amoxliatl | 4 |
| Bosses | Araxxor | 10 |
| Bosses | Barrows Chests | 25 |
| Bosses | Brutus | 4 |
| Bosses | Bryophyta | 1 |
| Bosses | Callisto and Artio | 6 |
| Bosses | Cerberus | 7 |
| Bosses | Chaos Elemental | 3 |
| Bosses | Chaos Fanatic | 3 |
| Bosses | Commander Zilyana | 8 |
| Bosses | Corporeal Beast | 7 |
| Bosses | Crazy Archaeologist | 3 |
| Bosses | Dagannoth Kings | 10 |
| Bosses | Deranged Archaeologist | 1 |
| Bosses | Doom of Mokhaiotl | 6 |
| Bosses | Duke Sucellus | 10 |
| Bosses | The Fight Caves | 2 |
| Bosses | Fortis Colosseum | 9 |
| Bosses | The Gauntlet | 5 |
| Bosses | General Graardor | 8 |
| Bosses | Giant Mole | 4 |
| Bosses | Grotesque Guardians | 7 |
| Bosses | Hespori | 4 |
| Bosses | The Hueycoatl | 6 |
| Bosses | The Inferno | 2 |
| Bosses | Kalphite Queen | 6 |
| Bosses | King Black Dragon | 4 |
| Bosses | Kraken | 4 |
| Bosses | Kree'arra | 8 |
| Bosses | K'ril Tsutsaroth | 8 |
| Bosses | The Leviathan | 10 |
| Bosses | The Mad Angel | 5 |
| Bosses | Maggot King | 3 |
| Bosses | Moons of Peril | 13 |
| Bosses | Nex | 8 |
| Bosses | The Nightmare | 12 |
| Bosses | Obor | 1 |
| Bosses | Phantom Muspah | 6 |
| Bosses | Royal Titans | 7 |
| Bosses | Sarachnis | 5 |
| Bosses | Scorpia | 4 |
| Bosses | Scurrius | 2 |
| Bosses | Shellbane Gryphon | 4 |
| Bosses | Skotizo | 6 |
| Bosses | Tempoross | 12 |
| Bosses | Thermonuclear Smoke Devil | 5 |
| Bosses | Vardorvis | 10 |
| Bosses | Venenatis and Spindel | 6 |
| Bosses | Vet'ion and Calvar'ion | 6 |
| Bosses | Vorkath | 6 |
| Bosses | The Whisperer | 10 |
| Bosses | Wintertodt | 10 |
| Bosses | Yama | 11 |
| Bosses | Zalcano | 4 |
| Bosses | Zulrah | 10 |
| Raids | Chambers of Xeric | 23 |
| Raids | Theatre of Blood | 17 |
| Raids | Tombs of Amascut | 27 |
| Clues | Beginner Treasure Trails | 16 |
| Clues | Easy Treasure Trails | 131 |
| Clues | Medium Treasure Trails | 115 |
| Clues | Hard Treasure Trails | 134 |
| Clues | Elite Treasure Trails | 59 |
| Clues | Master Treasure Trails | 49 |
| Clues | Hard Treasure Trails (Rare) | 24 |
| Clues | Elite Treasure Trails (Rare) | 39 |
| Clues | Master Treasure Trails (Rare) | 45 |
| Clues | Shared Treasure Trail Rewards | 49 |
| Clues | Scroll Cases | 13 |
| Minigames | Barbarian Assault | 11 |
| Minigames | Barracuda Trials | 9 |
| Minigames | Brimhaven Agility Arena | 9 |
| Minigames | Castle Wars | 39 |
| Minigames | Fishing Trawler | 4 |
| Minigames | Giants' Foundry | 9 |
| Minigames | Gnome Restaurant | 4 |
| Minigames | Guardians of the Rift | 17 |
| Minigames | Hallowed Sepulchre | 16 |
| Minigames | Last Man Standing | 32 |
| Minigames | Magic Training Arena | 11 |
| Minigames | Mahogany Homes | 8 |
| Minigames | Mastering Mixology | 7 |
| Minigames | Pest Control | 10 |
| Minigames | Rogues' Den | 5 |
| Minigames | Shades of Mort'ton | 14 |
| Minigames | Soul Wars | 3 |
| Minigames | Temple Trekking | 4 |
| Minigames | Tithe Farm | 7 |
| Minigames | Trouble Brewing | 30 |
| Minigames | Vale Totems | 4 |
| Minigames | Volcanic Mine | 8 |
| Other | Aerial Fishing | 9 |
| Other | All Pets | 71 |
| Other | Boat Paints | 11 |
| Other | Camdozaal | 10 |
| Other | Champion's Challenge | 11 |
| Other | Chompy Bird Hunting | 19 |
| Other | Colossal Wyrm Agility | 8 |
| Other | Creature Creation | 7 |
| Other | Cyclopes | 8 |
| Other | Elder Chaos Druids | 3 |
| Other | Forestry | 23 |
| Other | Fossil Island Notes | 10 |
| Other | Glough's Experiments | 6 |
| Other | Hunter Guild | 6 |
| Other | Lost Schematics | 12 |
| Other | Monkey Backpacks | 6 |
| Other | Motherlode Mine | 6 |
| Other | My Notes | 26 |
| Other | Ocean Encounters | 11 |
| Other | Random Events | 23 |
| Other | Revenants | 14 |
| Other | Rooftop Agility | 7 |
| Other | Sailing Miscellaneous | 12 |
| Other | Sea Treasures | 17 |
| Other | Shayzien Armour | 25 |
| Other | Shooting Stars | 2 |
| Other | Skilling Pets | 9 |
| Other | Slayer | 90 |
| Other | Tormented Demons | 3 |
| Other | TzHaar | 10 |
| Other | Miscellaneous | 63 |
