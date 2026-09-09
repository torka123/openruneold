# Grand Exchange: unrestricted purchases and 4% seller tax

## Scope and source

The canonical working tree did **not** contain a Grand Exchange implementation when this task
started. The GE implementation from Blade9766's
[commit 83cd11a53f57a4e83b5be31f81226b95a6ea4464, PR #216](https://github.com/OpenRune/OpenRune-Server/commit/83cd11a53f57a4e83b5be31f81226b95a6ea4464)
was therefore integrated as the single authoritative base and adjusted to the user's requested
economy policy. No other PR #216 features were imported. There was no existing
`.data/grand-exchange.json` to modify. The upstream author/co-author attribution remains available
at the source commit; no new Git commit or index staging was performed.

The existing canonical JVM was verified with `scripts/openrune-doctor.ps1` before edits (PID 40904).
Unrelated dirty source was recorded in `logs/ge-no-limits-source-hashes-before.json`.

## Requested changes

- `GeConfig`: eight slots independent of membership; seller rate **40 per mille (4%)**;
  unchanged **5,000,000 per-item cap** and local `.data/grand-exchange.json` path.
- `GeExchange`: removed purchase-window state, accounting, reset calculations and the matching
  quantity clamp. Matching now depends only on the existing item/price/time/remaining-quantity rules.
  The injected clock still serves offer ordering and history timestamps, not purchase restrictions.
- `GeOffer`: removed the obsolete purchase-window data class.
- `GeItemData`: ignores the legacy TSV second column entirely; no gameplay accessor or field remains.
  Tax still uses `floor(price * rate / 1000)` separately per item, then the cap. Exemptions unchanged.
- `GeService`: removed waiting/reset messages and purchase-restriction text from pricing experts.
  Login transmits the same `TAX_PER_MILLE` constant through `ge_transmit_taxrate`.
- `GeSaveData`: contains only `nextId`, `offers`, and `history`. Jackson ignores the retired
  `buyWindows` property on input. New writes do not contain it, and loading it creates no state.

No global coordination, membership-based slot policy, item sets, or unrelated GE features were added.
Price/time priority, partial fills, buyer price-difference refunds, collection boxes, aborting,
history, Repeat Offer, item validation, ironman bond restrictions and completion notifications were
retained from the referenced implementation. Account membership itself was not changed.

## Integration with this checkout

The commit's GE music/staff configuration was missing too. Only the GE row/polygon and its staff
overlays were added. This checkout has no separate Varrock modern-music polygon, so the upstream
Varrock carve-out was not applied and no other location's music was introduced or replaced.

- Native `Music.musicModern()` now includes `music_modern_grand_exchange` -> `the_trade_parade`.
- Native polygon source retains the upstream plaza rectangle `(3143,3462)` to `(3187,3510)`, levels
  0–3, area gameval 32752. GE modern-music row gameval 63861 is unchanged from the source commit.
- The 16 upstream GE staff overlays specify `NoMove`, wander range zero.
- The existing player MIDI API needed its integer-group overload for completion jingle group 86.
  The string overload delegates to it; both preserve the native music-clock reset.
- `GeService` has an internal injectable save path for isolated startup fixtures. The injected
  production constructor still uses exactly `.data/grand-exchange.json`.
- Explicit `:or-cache:installGrandExchangeConfig` maintenance writes only the GE music row, that
  table's indexes, the merged plaza area and staff movement definitions. It preserves existing
  music rows and map areas and backs up changed encoded files under `logs/ge-config-backups/`.
  It is **not** a startup/build dependency, does not edit source, does not change revision 240 or
  cache paths, and never writes LIVE. The initial installation changed 22 encoded files in SERVER.

## Files added/changed

GE module (`content/other/grand-exchange/`):

- `build.gradle.kts`, `README.md`.
- `src/main/kotlin/org/rsmod/content/other/grandexchange/GeConfig.kt`.
- `GeExchange.kt`, `GeItemData.kt`, `GeOffer.kt`, `GeService.kt` in the same package.
- `GrandExchangeModule.kt`, `GrandExchangeScript.kt` in the same package (upstream wiring retained).
- `src/main/resources/ge-items.tsv` (data retained; header clarifies ignored legacy column).
- `src/test/kotlin/org/rsmod/content/other/grandexchange/GeExchangeTest.kt`, `GeClientContractTest.kt`.
- `src/test/resources/legacy-ge-book.json`.

Shared configuration/integration:

- `.data/gamevals/area.rscm`, `.data/gamevals/dbrow.rscm`.
- `.data/raw-cache/map/area/grand_exchange.toml`.
- `.data/raw-cache/server/npcs.toml` (GE append only; existing boss overlays retained).
- `api/player/src/main/kotlin/org/rsmod/api/player/PlayerMidiExtensions.kt`.
- `or-cache/src/main/kotlin/dev/openrune/impl/Music.kt`.
- `or-cache/src/main/kotlin/dev/openrune/GrandExchangeConfigInstaller.kt`.
- `or-cache/src/test/kotlin/dev/openrune/GrandExchangeConfigTest.kt`.
- `or-cache/build.gradle.kts` (one explicit maintenance task).
- This report and `docs/grand-exchange/no-limits-format-files.txt`.

## Tests and verification

The GE tests execute the order book and native client state, rather than source-string assertions.
They cover purchases above the former TSV amount in both placement orders, repeated same-clock
orders/partial fills, uncapped net proceeds, per-item rounding, cap boundaries and multi-item caps,
all existing named exemptions, eight slots, client tax varbit for both membership values, legacy
JSON load/re-save, and **actual GE script startup** with legacy data. Existing order priority,
refund, collection, abort, history and save/load tests remain. Jingle completion/abort behavior and
the native music-clock reset are also checked.

The cache audit checks the GE music row plus retained Lumbridge row, plaza bounds on all levels,
and stationary staff configuration. Existing Royal Titans and Collection Log cache audits are run
alongside it to guard unrelated changes.

Focused command:

```powershell
.\gradlew.bat :content:other:grand-exchange:test --no-daemon --console=plain
```

Logs: `logs/ge-no-limits-test-2.log` (initial 21 tests passed),
`logs/ge-no-limits-test-build-4.log` (full server assembly passed),
`logs/ge-no-limits-config-install.log` (targeted install passed),
`logs/ge-no-limits-final-regression-2.log` (final expanded tests/build), and
`logs/ge-no-limits-deployment-build.log` (final installer, GE/cache tests and server assembly).
Earlier iterations exposed the absent integer jingle overload and test-only import/type issues;
these were corrected rather than bypassed.

Results on 9 September 2026: **23 GE tests passed; 193 tests passed overall**, with zero failures,
errors or skips. Breakdown: GE 23, player API 17, pouches 27, Royal Titans 97, Collection Log 21,
cache 8. Bank, music, music-plugin and generic-NPC test tasks reported no test sources.
The broader regression/assembly completed successfully in 1m29s; the final deployment build
completed successfully in 1m31s. Counts are also recorded in `logs/ge-no-limits-test-summary.json`.

Broader test/build invocation (scoped formatting tasks preceded this in the same Gradle run):

```powershell
.\gradlew.bat :content:other:grand-exchange:test :api:player:test `
  :content:other:pouches:test :content:bosses:royal-titans:test `
  :content:interfaces:collection-log-interface:test :content:interfaces:bank:test `
  :api:music:test :api:music-plugin:test :content:generic:generic-npcs:test `
  :or-cache:test --tests dev.openrune.GrandExchangeConfigTest `
  --tests dev.openrune.RoyalTitansR240CacheAuditTest `
  --tests dev.openrune.CollectionLogR240SelectionAuditTest `
  :server:app:assemble --no-daemon --console=plain
```

The installer initially exposed order-dependent area re-encoding on a repeat run. It now compares
decoded map-area structure before encoding; a repeat install printed
`GE configuration already installed; SERVER unchanged.` in the final deployment log (line 495).
No source is generated or overwritten by startup, and installation remains an explicit task.

### Runtime and preservation

The server was started, then restarted twice using the existing normal-shutdown helper (not a
force kill). Every run printed `OPENRUNE CUSTOM BUILD ACTIVE`, loaded 435 scripts and reached
`Server ready`. Doctor verified the canonical JVM working directory and revision 240 each time.

| Run | JVM PID | Ready time (local) | Startup log |
| --- | ---: | --- | --- |
| Initial start | 20968 | 04:58:05 | `logs/ge-no-limits-startup.stdout.log:1358` |
| Restart 1 | 6468 | 04:59:43 | `logs/cerberus-audit-20260907/restart-2026090915.stdout.log:1358` |
| Restart 2 | 1420 | 05:01:12 | `logs/cerberus-audit-20260907/restart-2026090916.stdout.log:1358` |

Doctor logs: `logs/ge-no-limits-doctor-startup.log`,
`logs/ge-no-limits-doctor-restart-1.log`, `logs/ge-no-limits-doctor-restart-2.log`.
Controlled restart commands used `scripts/cerberus-controlled-restart.ps1` with the current
`-ServerProcessId` and `-RestartNumber 2026090915` / `2026090916`.
The final server remains running as PID 1420.

- All **199 recorded source files** were unchanged across both restarts.
- All **175 unrelated pre-existing changed/untracked files** remained byte-identical to the
  pre-task snapshot. The two intentional overlapping files retained their existing unrelated hunks.
- `git diff --check` passed. No files were staged or committed and no user work was reset.
- Server JAR, GE module JAR, SERVER data file and LIVE data file hashes remained unchanged across
  both restarts. Exact hashes and comparisons are in `logs/ge-no-limits-restart-verification.json`.
- Cache revision/path stayed 240 / `.data/cache/SERVER`; LIVE was not modified. Only the explicit
  targeted GE configuration installation changed SERVER before restart verification.
- No synthetic offers or player inventory changes were made in the live book/database. The local
  GE save did not yet exist because no live orders had been created; persistence and legacy
  startup were exercised using isolated fixtures through the real order-book/service/script code.

Existing startup cycle-overrun messages and PostgreSQL connection-close errors during shutdown
were observed. The same database shutdown error is present in the pre-GE restart logs
`restart-2026090913.stdout.log:1410` and `restart-2026090914.stdout.log:1539`.
Both GE restarts still completed normally and became ready; no GE startup error or duplicate
registration was observed. These checks are not a claim of a warning-free server.

## Verification boundaries

Automated client-contract tests and real server startup are distinct from desktop client playtesting.
No claim is made of visually testing every GE interaction, listening to the music, live-account
trading, or an exhaustive OSRS GE parity audit. The requested no-limit/4% policy is intentional
OpenRune behavior, not a claim about the current OSRS economy.
