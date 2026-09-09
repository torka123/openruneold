# Rune pouch: empty contents panel, 9 September 2026

## Cause and correction

The stored rune type/quantity varbits were present, but the revision-240 client also requires a
temporary slot-capacity value. Native gameval `varp.if1` (261) was left at zero, causing the client
to hide every stored-rune slot. This was a presentation bug, not missing stored runes.

The existing `RunePouchScript.openPouch` now sets that var through the established native `intVarp`
delegate before opening interface190. Regular/locked pouches send3; divine/locked pouches send4.
Inventory Open and bankside Configure share this handler. Click permissions use the same capacity.
The value is refreshed on every open because it is temporary/shared UI state, not pouch storage.
No cache, membership, rune storage, casting, inventory or persistence policy was changed.

## Evidence

- Active SERVER definition261: `scope=Temp`, `transmit=OnSetAlways`, `bitProtect=false`.
- Active LIVE client script667 (draw slot) invokes7869 and hides the rune model when it returns0.
- Script7869 checks `slot <= varp261` for regular/divine slots1..4.
- Script4891 reads the native type/count values through4892 and resolves rune objects using enum982.
- Script453 installs the var-transmit drawing listener, including261.
- Retained read-only queries: `logs/rune-pouch-ui-definition-query.log`,
  `logs/rune-pouch-ui-draw-scripts.log`, `logs/rune-pouch-ui-eligibility-scripts.log`.
  The existing generic diagnostic decoder mislabels opcode9 and misaligns portions of453; those
  portions are not treated as proof. The new test decodes7869's footer/switches and executes its
  actual regular/divine branch, rejecting unsupported instructions and invalid instruction counts.

## Executable checks

Three new native/cache-backed regression tests in `PouchesIntegrationTest` exercise all four pouch
variants, real held/bankside handlers, capacity packet ordering before `IfOpenSub`, unchanged stored
runes, client-script slot visibility, reopening after temporary-state reset, and divine withdrawal.
`RunePouchClientSlots` is a test-only interpreter for that one cache predicate, not a UI renderer or
another pouch implementation.

Before the fix:18 integration tests ran, the three new tests failed because the capacity packet was
absent; the previous15 passed (`logs/rune-pouch-ui-red-test.log`).

After the fix: **99 tests passed, zero failures/errors/skips**:30 pouch/bag tests,3 combat-manager
tests,66 spell-attack tests. Bank's test task has no sources. Pouch check and server assembly passed
in1m31s (`logs/rune-pouch-ui-regression-build-2.log`, `logs/rune-pouch-ui-test-summary.json`).

```powershell
.\gradlew.bat -I scripts/format-selected.init.gradle '-PopenruneFormatManifest=docs/pouches/ui-capacity-format-files.txt' :content:other:pouches:spotlessApply :content:other:pouches:test :content:interfaces:bank:test :api:combat:combat-manager:test :content:skills:magic:spell-attacks:test :content:other:pouches:check :server:app:assemble --no-daemon --console=plain
```

The first build invocation failed before task execution because PowerShell split the unquoted
Gradle property at`.txt`; quoting the property fixed the invocation. No failing build was deployed.
Only the three Kotlin files in the scoped formatting manifest were formatted.

## Deployment and preservation

Two controlled normal shutdown/restart cycles completed using the established restart script.
Both boots reached `Server ready`, loaded435 scripts, reported `OPENRUNE CUSTOM BUILD ACTIVE`,
and passed `openrune-doctor.ps1 -SearchRoots @((Get-Location).Path) -SkipGradleVersion`.

| Restart | JVM | Ready time | Evidence |
|---|---|---|---|
| First |28624|05:54:21|`logs/cerberus-audit-20260907/restart-2026090919.stdout.log`, `logs/rune-pouch-ui-doctor-restart-1.log`|
| Second, left running |28712|05:55:48|`logs/cerberus-audit-20260907/restart-2026090920.stdout.log`, `logs/rune-pouch-ui-doctor-restart-2.log`|

All209 dirty/untracked source/report files were byte-identical across the restarts, as were the
server jar, pouch jar and SERVER/LIVE dat2 files (`logs/rune-pouch-ui-restart-verification.json`).
This report was updated afterward to record results. The baseline comparison identifies only the
intended pouch edits and the Nex approval-note update; unrelated user changes remain unchanged.
`git diff --check` passed (`logs/rune-pouch-ui-diff-check.log`). Nothing staged or committed.

Cache revision240, SERVER/LIVE paths and PostgreSQL authority are unchanged. No account rows were
manually edited. Existing database shutdown-order warnings and occasional startup cycle overruns
remain outside this fix; no forced process termination occurred.

Server artifact SHA-256:`BF30952E7D7E3D81FEE81F46CB9C8D5BBD73F946F393563A4114D75A69C388D1`.
The first artifact manifest used the wrong unversioned pouch jar filename; it still recorded the
server/cache hashes. The actual`pouches-0.0.1.jar` fingerprint is retained separately and was included
in the four-artifact comparison. No missing artifact was counted as verified.

**Verification boundary:** real handler/outbound packet tests plus execution of the cache's slot
predicate, successful build and two real server boots. No desktop visual inspection, live player
save/reconnect demonstration or screenshot comparison is claimed. Open the pouch again to receive
the corrected capacity; normal client drawing reads the existing stored runes.
