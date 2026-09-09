# Collection Log X-button follow-up — 2026-09-09

## Cause and correction

The X is correctly mapped to revision-240 component **621:1**. Real client clicks appear
in `logs/cerberus-audit-20260907/restart-2026090910.stdout.log:1506–1509` and `1514–1516`
as **overlay** `If3Button` events, operation 1, sub -1. The packet is not gated or missing.

`SummarySideScript.clickCollectionLog` opens the interface with native `ifOpenOverlay`,
using the gameframe's floating slot. The Collection Log's overlay X handler instead called
`ifClose`, which closes modals, not overlays. The log remained open and its close event
never cleared the session. The previous close/reopen test covered only the book/modal path.

`CollectionLogScript` now calls the existing
`player.ifCloseOverlay(CollectionLogUi.INTERFACE, eventBus)` for the overlay X.
The modal X retains its working `ifClose` path. This matches established skill-guide and
settings overlay handlers. No cache change, engine API, duplicate registration or manual
packet workaround was introduced.

## Regression evidence

Before the correction, the expanded test failed with:

```text
Close must remove session for modal=false
expected: null
but was: CollectionLogUiSession(categoryIndex=4, sectionIndex=30)
```

Evidence: `logs/collection-log-close-regression-before.log` and the preserved failure XML
`logs/collection-log-close-failing-regression.xml`.

The fixed executable test covers book/modal and account-summary/overlay opening paths on
fixed (548) and resizable (161) gameframes, using cache-backed mainmodal/floater translations.
It runs **20 close/reopen cycles**, checking:

- The actual incoming X-button handler removes the collection from modal/overlay state.
- An `IfCloseSub` packet addresses the correct translated client slot.
- The session and same-tick search-refresh attribute are removed.
- Reset script 2158 runs once and inventory 620 is emptied.
- Duplicate close clicks and stale section clicks do nothing after closure.
- Reopening returns to the first collection with valid encoded display packets.

The other exhaustive tests still cover all 124 sections, normal/search selection, all search
keyboard controls, empty/partial/complete logs, and native outgoing packet encoding.

## Checks and deployment

Focused module tests and scoped formatting passed in 46 seconds:

```powershell
.\gradlew.bat -I scripts/format-selected.init.gradle '-PopenruneFormatManifest=docs/collection-log/close-button-format-files.txt' :content:interfaces:collection-log-interface:spotlessKotlinApply :content:interfaces:collection-log-interface:test --no-daemon --console=plain
```

Log: `logs/collection-log-close-tests.log`. All 21 Collection Log interface tests passed.

Broader check/build command:

```powershell
.\gradlew.bat -I scripts/format-selected.init.gradle '-PopenruneFormatManifest=docs/collection-log/close-button-format-files.txt' :content:interfaces:collection-log-interface:check :api:collection-log:test :api:net:test :or-cache:test --tests 'dev.openrune.CollectionLog*' :server:app:assemble --no-daemon --console=plain
```

Broader build/check **passed in 1m 15s** (462 tasks), with **125 tests**, no failures or
errors: interface 21, Collection Log API 10, network API 86, Collection Log cache audits 8.
Cache tests are filtered to Collection Log; formatting is restricted to the two Kotlin files
in the manifest. Log: `logs/collection-log-close-check-build.log`.

Deployment completed, followed by two controlled restarts:

| Run | PID | Local ready time | Startup duration |
| --- | ---: | --- | --- |
| Rebuilt startup | 41772 | 03:23:15 | 19.235 seconds |
| Restart 2026090911 | 31908 | 03:24:24 | 19.521 seconds |
| Restart 2026090912 | 10068 | 03:25:38 | 18.953 seconds |

All three runs emitted `OPENRUNE CUSTOM BUILD ACTIVE`, loaded 432 scripts and reached
`Server ready`. Each doctor check confirmed the canonical JVM working directory, revision
240 and unchanged SERVER/LIVE fingerprints. Final PID **10068** owns listeners 43594,
8080 and 9091. Logs are `logs/collection-log-close-startup.stdout.log`,
`logs/cerberus-audit-20260907/restart-2026090911.stdout.log` and
`restart-2026090912.stdout.log`. Doctor logs use the prefix
`logs/collection-log-close-doctor-` (before, startup, restart-1, restart-2).

All 128 recorded dirty/untracked source files and both deployment JARs retained identical
hashes through rebuild/startup/restarts. Evidence is in the source/artifact before/after
JSON snapshots prefixed `logs/collection-log-close-`. Final module JAR SHA-256:
`8D80F96D5B2A310AF88DCD11F5FAC3190FFF3B86D3B14B4669930E6CBAE79FDD`.
Server JAR: `F4BA10EC0EF342B29EC18AECB9AF34C5BE5B5727D8C4B36191C43E4E117210DC`.

`git diff --check` passed. The existing PostgreSQL shutdown-order warning recurred
(`Game database service failed to shut down` / `Connection reset by peer`); normal process
termination and subsequent readiness succeeded. This follow-up does not claim to fix that
separate shutdown warning or to have visually clicked X in a desktop client after deployment.

Before editing, `scripts/openrune-doctor.ps1` confirmed PID 39324 was the canonical project's
JVM using revision 240. The server was stopped through its normal shutdown hooks for rebuild.
No source was reset/restored; unrelated work, cache paths/revision and PostgreSQL authority
remain unchanged. Only the handler, its existing packet test, and audit documents are changed
in this follow-up. No fresh desktop-client visual result is claimed.
