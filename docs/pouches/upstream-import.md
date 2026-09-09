# Rune pouch and looting bag import

## Source and scope

Imported the pouch content from Blade9766's commit
[9033a54ac66722f5abc5820bb84e17b0d87fa881](https://github.com/OpenRune/OpenRune-Server/commit/9033a54ac66722f5abc5820bb84e17b0d87fa881)
in PR 216, not the entire multi-feature PR. Original commit title:
`feat(pouches): looting bag and rune pouch with pickup redirect and death hooks`.
The original Git commit records Blade9766 as author and Claude Fable 5.1 as co-author.

Also imported the empty-inventory-transaction correction from
[8fa395bf7105116cc30eec5e7e2368bf54c5499c](https://github.com/OpenRune/OpenRune-Server/commit/8fa395bf7105116cc30eec5e7e2368bf54c5499c):
`fix: cast spells with runes held only in a rune pouch`.
Its separate change forcing every account to be a member is **not included**; existing account
membership remains authoritative. Consequently existing client-side membership restrictions still
apply. The user was asked separately about that broader gameplay change.

This is a source integration, not a clean cherry-pick or an import of the entire PR history. The
existing dirty tree required preserving newer local combat, bank, unsafe-death and persistence code.
No Git commit, reset, checkout or source replacement was performed.

## Mechanic-to-native-system mapping

| Feature | Authoritative integration |
| --- | --- |
| Regular/divine/locked rune pouches | Native held and interface events; revision-240 gameval identities |
| Rune contents, quantities and settings | Existing rune-pouch varbits and account varp persistence |
| Casting and rune consumption | Existing `MagicRuneManager` and rune repositories; no separate spell calculator |
| Looting bag inventory | Native permanent `inv.looting_bag`, saved by `CharacterInventoryPipeline` |
| Deposits, withdrawals and bank tabs | Native inventory transactions and `BankInvScript.invDeposit` |
| Open-bag pickups | Shared pickup validation, prepared inventory transaction, repository removal, commit |
| Death | Imported shared `PlayerDeathItemHook`, existing handling/ownership/location policies |
| Quantity dialogs | Native protected-access suspension and post-dialog revalidation |

## Adaptations and corrections

1. Bank events now carry Op1/Op9 and run as native protected events. Configure reuses the current
   protected action instead of launching a nested action that is rejected while banking.
2. Pouch Empty uses native bank deposits, preserving capacity, selected tabs, placeholders and partial
   stack handling. Failed deposits leave the remaining runes in their original varbits.
3. Pickup redirection uses prepared transactions. No ground item is removed unless a destination
   transaction succeeds. After successful repository removal, exactly that transaction commits.
   Competing pickups cannot duplicate the item. State and validation are checked again after delay.
4. Looting-bag bank buttons reuse the unsafe-dungeon bank-session guard; stale hidden panels cannot
   move items. Quantity dialogs revalidate item identity, bag/pouch presence and pouch capacity.
5. Divine reversion rechecks the fourth slot after confirmation. Full-inventory withdrawals and
   invalid/stale slots cannot consume stored runes.
6. The active cache defines bag inventory 516 as **permanent**, contrary to the contributor's
   temporary-inventory assumption. Native inventory persistence takes precedence; a stale legacy
   attribute cannot overwrite it. The legacy codec remains backward compatible and preserves item
   vars when used with temporary inventories. No parallel persistent bag authority is introduced.
7. The native bag definition had `protect=true`, which rejects modal inventory transactions, even
   from the bag's registered protected handlers. Its tracked TOML now sets `protect=false`, matching
   inventory/bank. Trade-offer protection is unchanged. Bag access remains gated by native handlers.
8. The scoped, explicit `:or-cache:installLootingBagConfig` task changes only inventory 516's protect
   flag in SERVER. It is not a build/startup dependency and never overwrites source. Repacking from
   tracked TOML retains the correction. The original encoded definition is backed up in
   `logs/pouches-cache-backups/`.
9. The imported death hook keeps existing item-retrieval, death-notice and external-grave location
   hooks. PvM bag contents follow the existing external drop-location policy for disposable arenas.
10. `MagicRuneManager` skips inventory deletion when every consumed rune comes from pouch varbits;
    existing mixed inventory/pouch and combination-rune accounting remains native.
11. The first real startup exposed four older generic bag registrations embedded in
    `LiveWildernessAgilityCourse.kt`. These registrations and their three superseded helper methods
    were removed, retaining the course's bag dispenser, native paid-loot deposits and streak logic.
    Every pouch integration fixture now loads both scripts into the same event bus; an additional
    test opens the bag, claims paid course rewards, prevents duplicate lap claims, checks the bag UI
    and closes it without losing the deposited rewards. Duplicate registrations remain fatal.

## Files

New authoritative content module: `content/other/pouches/` (two scripts, shared module/death hook,
rune contents/quantity rules, bag codec, pickup hook, and executable tests).

Shared integrations:

- `api/core/.../module/PlayerModule.kt`
- `api/death-plugin/.../DeathDropHooksModule.kt`
- `api/death/.../PlayerDeath.kt`, new `PlayerDeathItemHook.kt`
- `api/obj-plugin/.../ObjTakePlugin.kt`
- `api/player/.../events/interact/HeldEvents.kt`, `hook/GroundItemHooks.kt`
- `api/script-advanced/.../HeldScriptEventExtensions.kt`
- `api/combat/combat-manager/.../MagicRuneManager.kt`
- `content/interfaces/bank/.../BankAccess.kt`, `scripts/BankInvScript.kt`
- `content/areas/wilderness/.../interactions/LiveWildernessAgilityCourse.kt` (only superseded bag operations)
- `.data/raw-cache/server/inv.toml`
- `or-cache/build.gradle.kts`, new `LootingBagConfigInstaller.kt`

Exact Kotlin paths for this change are listed in `upstream-import-format-files.txt`.
Formatting was restricted to that manifest, not the entire dirty tree.

## Verification

The cache-backed integration suite dispatches real registered held events, bank events, incoming
`If3Button` packets and native pickup events. It covers all four pouch variants, 16,000 limits,
three/four slots, insufficient space, pouch-only/mixed/combination-rune spell consumption, failed
requirements, bank Configure/Empty/capacity, wilderness deposits, open-bag pickup/fallback, competing
pickups, both containers full, delayed revalidation, post-dialog pouch removal, death spill/retention,
native inventory reload and stale-attribute rejection. The original pure tests were retained.
The Wilderness Agility coexistence test dispatches the real coordinate-scoped dispenser event.

Commands/logs:

- `git fetch --no-tags upstream 9033a54ac66722f5abc5820bb84e17b0d87fa881`.
- Initial compile and original tests: `logs/pouches-import-compile.log`, successful.
- Integration iterations: `logs/pouches-import-integration*.log`; uncovered the modal-protection
  definition mismatch. Test-fixture corrections included native processed-clock advancement,
  closing modals before held operations, and realistic per-item-vars stacking rules.
- Scoped installer and integration tests: `logs/pouches-import-cache-and-test.log`, successful.
- Scoped formatting: `logs/pouches-import-format.log`, successful.
- Full affected tests and server assembly: `logs/pouches-import-regression-build-2.log`, successful.
- Wilderness Agility coexistence/build regression: `logs/pouches-import-wilderness-regression-2.log`,
  successful after supplying the test fixture's native protected-access RNG.
- Revision-240 Royal Titans/Collection Log cache audits and idempotent installer rerun:
  `logs/pouches-import-cache-regression.log`, successful. Installer reported SERVER unchanged.

The broad regression command was:

```powershell
.\gradlew.bat :content:other:pouches:test :api:core:test :api:death-plugin:test :api:death:test :api:obj-plugin:test :api:player:test :api:script-advanced:test :api:combat:combat-manager:test :content:interfaces:bank:test :api:invtx:test :api:account:test :content:skills:magic:spell-attacks:test :content:skills:magic:spell-teleports:test :content:bosses:royal-titans:test :content:interfaces:collection-log-interface:test :server:app:assemble --no-daemon --console=plain
```

After consolidating the overlapping Wilderness Agility handlers, its tests, pouch tests and server
assembly were run again (with manifest-scoped formatting). Final results across these commands and
the two cache audit classes: **345 tests, zero failures, zero skips**. Of these, 27 are pouch/bag
tests (15 native/cache-backed integration tests and 12 content/codec tests), 83 Wilderness tests,
97 Royal Titans tests, 21 Collection Log tests, 87 magic-content tests, 25 shared-system tests and
5 cache tests. Some requested API modules contain no test sources; these were compiled by the build.
Exact per-module counts are in `logs/pouches-import-test-summary-final.json`.

126 pre-existing unrelated source files were compared against the previous task's final hashes:
zero mismatches (`logs/pouches-import-unrelated-source-verification-final.json`). Existing source changes
overlapping the shared death integration were preserved explicitly.

## Runtime verification status

Verified on 2026-09-09: successful real startup and two controlled server restarts. Every successful
boot reported `OPENRUNE CUSTOM BUILD ACTIVE`, loaded **434 scripts**, and reached `Server ready`.
Doctor confirmed the canonical project working directory for every JVM.

| Boot | JVM PID | Ready time (server log) | Evidence |
| --- | --- | --- | --- |
| Corrected initial startup | 33112 | 04:22:50 | `logs/pouches-import-startup-2.stdout.log`, `logs/pouches-import-doctor-startup-2.log` |
| Controlled restart 1 | 9208 | 04:24:02 | `logs/cerberus-audit-20260907/restart-2026090913.stdout.log`, `logs/pouches-import-doctor-restart-1.log` |
| Controlled restart 2, left running | 40904 | 04:25:21 | `logs/cerberus-audit-20260907/restart-2026090914.stdout.log`, `logs/pouches-import-doctor-restart-2.log` |

The earlier failed boot (PID 39668) is preserved in `logs/pouches-import-startup.stderr.log`:
duplicate held Op1 for looting bag 11941. It was stopped normally before consolidation/redeployment;
it is not counted as a successful startup or restart. The shutdown helper verifies the target JVM
and requests normal JVM shutdown; no server was force-killed.

Existing shutdown-order warnings (`Game database service failed to shut down` / PostgreSQL
connection reset), Hunter-option warnings, and occasional startup cycle-overrun messages were also
observed. This import does not fix those unrelated diagnostics or claim warning-free shutdowns.
The final startup has no pouch/bag registration exception and an empty stderr log at verification.

All 177 dirty/untracked source/report files in the final-build snapshot were byte-identical after
the restarts (`logs/pouches-import-restarts-source-verification.json`); only this report was updated
afterward to record results. The assembled server, pouch and Wilderness jars, SERVER and LIVE
fingerprints also matched across restarts (`logs/pouches-import-restart-artifact-verification.json`).
`git diff --check` passed. Nothing was committed or staged by this task.

Revision remains **240**; SERVER and LIVE paths are unchanged. Only the intended SERVER inventory
definition was updated; LIVE is untouched. `.data/postgres` remains the native persistence authority;
no player inventory, bank or account rows were manually edited.

- Assembled server SHA-256: `60A93AF19B1C587EFCFD372F7DCD980F9C1DA6DEB736E095E87530693E8D14EE`.
- SERVER dat2 SHA-256: `8530AB985DAAC92001C601355A67C8EAE715F5D7F49C2CFD2E67233640DE340B`.
- LIVE dat2 SHA-256: `FD0FE5EF66039A791A900EDDDF1CDBCFE57E09E8C32EA3496C69E96217A41A7B`.

The automated client tests exercise packet dispatch and outbound interface updates, not a desktop
visual inspection or a live player's save/reconnect round-trip. Native persistence serialization/
reload is covered in tests; two successful server restarts establish deployment durability, not a
live-account persistence demonstration. No claim of an exhaustive OSRS pouch-behaviour audit is made.
Existing membership policy was deliberately not broadened while importing these two requested
features.
