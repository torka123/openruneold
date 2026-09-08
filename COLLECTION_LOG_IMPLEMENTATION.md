# Collection Log implementation

## Scope and authority

This implementation drives the native revision-240 Collection Log rather than recreating it in a
server-only interface. The revision-240 cache is authoritative for category, section, slot, client
script, struct, enum, component, inventory, varp, varbit, item-option, and NPC-option mappings. A
checked-in OSRS Wiki snapshot is used as a second-source validation layer.

The server targets Java 21 and persists player attributes through OpenRune's existing account
pipeline. No donor bytecode is shipped and no custom per-section completion rewards were ported.

## Donor compatibility matrix

| Donor observation | OpenRune decision |
| --- | --- |
| Loose `CollectionLog.java` is only a menu entry | Not ported as an implementation |
| Generated `CollectionlogProjectDependency.java` | Ignored |
| Legacy `Map<Integer, Integer>` quantities | Accepted by the idempotent legacy migration |
| Old widget children 0, 3, 17, 19, and 36 | Treated only as hints; revision-240 cache mappings are used |
| JAR persistence and temporary UI state | Rewritten with OpenRune attributes and Kotlin services |
| JAR drop/reward hooks | Rewritten at authoritative OpenRune reward commit points |
| JAR client scripts, structs, enums, and catalog examples | Re-resolved and asserted against revision 240 |
| Custom section-completion rewards | Deliberately excluded; they are not OSRS Collection Log ranks |

## Data model and catalog

`api/collection-log` owns the runtime model:

- five ordered categories;
- 124 ordered sections;
- 1,921 display slots;
- 1,712 unique canonical items;
- display and canonical IDs, quantity cap, display rule, and optional activity identifier per slot;
- one shared canonical quantity for duplicated slots;
- completion totals counted by display slots, matching the client.

The normalized catalog is
`api/collection-log/src/main/resources/collection-log/catalog-r240.json`. It is generated from the
revision-240 category/section/item structs and enums, then cross-checked against the checked-in Wiki
evidence under `docs/collection-log/source`.

Source metadata:

| Source | Revision | Timestamp | SHA-1 |
| --- | ---: | --- | --- |
| Cache | 240 | catalog generated 2026-08-22 03:16:35 UTC | n/a |
| Collection log | 15315992 | 2026-08-22 03:14:54 UTC | `998a5df85f5b8b3f23cf6d3f1673f3d812963b70` |
| Collection log/Table | 14936656 | 2025-07-12 14:13:28 UTC | `8817eed6f7627ebf469cbfc36bed646c7f801bd0` |
| Module:Collection log/completion.json | 15240204 | 2026-06-26 17:33:38 UTC | `1d9920c3588ac46f683754baee7fc3bc6bfe93ac` |
| Module:Collection log/data.json | 15315995 | 2026-08-22 03:16:35 UTC | `dfdecdb061d3736dcc42974fafb5cc680042b465` |

The generator is version 1.0.0. Regenerate with:

```text
gradlew :or-cache:generateCollectionLogCatalog
```

Review `docs/collection-log/source/mismatch-report.json` after every regeneration. Every mismatch
must either be corrected or receive an exact, reasoned entry in `mismatch-allowlist.json`; the tests
reject an unreviewed difference.

## Persistence and migration

The persistent key is `collection_log_state_v1`. Its current payload schema is version 2 and stores:

- canonical item quantities;
- the legacy-migration marker;
- a bounded set of recent award-event IDs for idempotency;
- the twelve most recent first-time unlocks for the native overview.

Quantities are monotonic, invalid/non-positive awards are ignored, and values are clamped to their
catalog cap (250 normally, 65,535 for stackable entries). Award-event keys include award kind,
source, and source event ID so independently generated rewards cannot collide.

On first load, compatible direct or nested legacy item-ID maps are merged, canonicalized, clamped,
and marked migrated. The old attributes are retained and never erased. Schema 1 payloads upgrade to
schema 2 without changing quantities. Unsupported/corrupt future data is not overwritten.

## Authoritative award paths

All logging goes through `CollectionLogService.record` or `recordBatch`. Inventory movement,
banking, equipping, trading, deleting, and transforming items do not call this API.

| Kind | Live OpenRune commit points |
| --- | --- |
| NPC/boss drop | Central NPC drop hook, after the ground item is successfully spawned |
| Boss/chest | Barrows, Perilous Moons, Doom of Mokhaiotl, Nex, Brimstone, Grubby, Moon, Larran's, Muddy, Crystal, and Elven Crystal rewards |
| Raid | Chambers of Xeric, Theatre of Blood, and Tombs of Amascut reward commits |
| Minigame/activity | Fight Caves, Pest Control, and Giants' Foundry reward purchases |
| Skilling | Successful Dusuri purchases of the Celestial ring and Star fragment |
| Pet grant | Nexling and the Wilderness Breach pet grant |
| Server activities | Vote Boss and Wilderness Breach reward grants |

The target currently contains clue-scroll acquisition and transformation, but no clue completion or
reward-casket pipeline. It also contains no skilling-pet grant pipeline. Those catalog slots and UI
sections are present and migrated quantities display correctly, but there is no legitimate live
award event to hook for those two missing game systems. Adding a fake item-give hook would violate
the authoritative-event rule.

When either game system is implemented, call the service only after its reward transaction commits,
using `ClueReward` or `PetGrant` and a stable completion/grant event ID.

## Native interface

The interface module uses revision-240 protocol values verified by cache tests:

- Collection Log interface 621 and inventory 620;
- overview interface 908 and its current-day/recent-unlock varps and varbit;
- cache category structs, section enums, item enums, and native draw scripts 2389/2730;
- all five tabs, all section lists, global search-result indices, item-grid access, category/global
  progress, linked activity counters, and close/reopen reset;
- base and ranked Collection log held/worn options;
- Collector NPC 8491 `Get-log` and `Claim-log-rewards` options;
- all nine revision-240 ranks and staffs, including the dynamic Gilded threshold of 1,725 slots for
  this 1,921-slot catalog.

The implementation reads exact known persisted counters for Doom, Nex, Phantom Muspah, Vorkath,
Zulrah, and Grotesque Guardians. Other linked sections use only an exact normalized NPC-name match
to a cache-defined kill-count varp; an unrelated or unavailable activity reports zero rather than a
guessed count.

## Automated verification

Focused tests cover:

- catalog revision, category/section/slot order, totals, item-definition resolution, caps, and
  duplicate canonical slots;
- Wiki mismatch allowlisting;
- cache protocol, rank structs, overview fields, activity identifiers, components, item/NPC options,
  and exact varp/varbit mappings;
- new/repeated/capped/untracked awards, duplicate event IDs, all award-kind namespaces, recent
  unlocks, schema round trips, schema upgrade, and idempotent legacy migration;
- global/category progress, section inventory ordering, search mapping, and activity-counter sources.

Primary commands:

```text
gradlew :api:collection-log:test
gradlew :content:interfaces:collection-log-interface:test
gradlew :or-cache:test --tests *CollectionLogR240*
gradlew :server:app:build
```

The cache tests require `.data` to be visible from `or-cache`; local verification may use a temporary
directory junction and must remove it afterward.

## Live verification checklist

1. Use Collector's `Get-log`, then test Read and Overview from the held and worn book options.
2. Open every category and section, search a section, close, reopen, and confirm the first tab resets.
3. Earn a tracked NPC drop and confirm one quantity increment and the new-item notification setting.
4. Complete one hooked chest/minigame/raid reward and confirm only committed items increment.
5. Buy a Celestial ring or Star fragment from Dusuri and confirm failed purchases do not increment.
6. Relog and restart twice; verify quantities, recent unlocks, duplicate slots, and progress persist.
7. Claim the highest earned staff from Collector and create its matching ranked book.

## Known differences

There are four reviewed cache/Wiki differences. Revision 240 places Venator tooth (33663) and
Venator fang (33661) in `Other > Slayer`; the newer Wiki snapshot places them in a `Venators`
section which does not exist in this cache. The implementation retains the native revision-240
layout. The exact justifications are committed in the mismatch allowlist.

The only content-level limitations are the target server's absent clue-completion/reward-casket and
skilling-pet-grant systems described above. They are not represented as working award paths.
