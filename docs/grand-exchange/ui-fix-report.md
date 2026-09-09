# Grand Exchange UI corrections — 9 September 2026

## Scope and implementation

User report: carried inventory is blank during buy setup; Modify offer and Abort offer are dead
clicks. Changes extend the single existing GE module. No order-book, 4% tax, unlimited-purchase,
eight-slot, persistence-path or unrelated gameplay policy was changed.

1. **Abort/Modify routing:** the active cache passes `details_status` (`465:23`) as argument 8
   of client script 803, then as argument 2 of script 819. Script 819 creates Abort at dynamic
   child 0 and Modify at child 1 on that parent. The original server installed permissions and
   listened on `details` (`465:15`), so the native input handler gated the real clicks. Both the
   event mask and handler now use `component.ge_offers:details_status`.
2. **Inventory refresh/lifecycle:** `onIfOpen(interface.ge_offers_side)` now requests the existing
   native full transmission of `player.inv` (93), including an already-subscribed inventory with
   no modified slots. Revision-240 side script 781/783 reads that inventory. Inventory click
   permissions are installed at the side panel's own open event, after replacement clears old
   permissions; previously they were installed prematurely by the main panel's open event.
   Closing GE leaves normal inventory transmission active. No copied inventory or custom item
   renderer was introduced.

The routing defect is reproduced by actual inbound packet tests. Inventory refresh and side
reopening are verified through native lifecycle, inventory processing and encoded packets; a
desktop screenshot comparison remains a separate verification boundary, not inferred from tests.

## Files changed in this pass

- `content/other/grand-exchange/src/main/kotlin/org/rsmod/content/other/grandexchange/GeConfig.kt`
- `content/other/grand-exchange/src/main/kotlin/org/rsmod/content/other/grandexchange/GeService.kt`
- `content/other/grand-exchange/src/main/kotlin/org/rsmod/content/other/grandexchange/GrandExchangeScript.kt`
- `content/other/grand-exchange/src/test/kotlin/org/rsmod/content/other/grandexchange/GeUiInteractionTest.kt`
- `content/other/grand-exchange/build.gradle.kts` — native processor/packet test dependencies only.
- This report and `docs/grand-exchange/ui-fix-format-files.txt`.

The native `If3ButtonHandler`, interface event gate, `onIfOpen`/`onIfModalButton`,
`startInvTransmit`, `PlayerInvUpdateProcessor`, inventory transactions and the existing GE
abort/modify/collection services are reused unchanged. No shared engine edits were needed.

## Evidence and tests

The user screenshots establish the visible report and expected inventory presentation. Active
SERVER cache definitions identify the exact main/side initializers; LIVE script 803 instructions
44–47 pass the status component to 819. Script 819 instructions 14–43 create Abort child 0;
44–73 create Modify child 1. See `logs/ge-ui-20260909-client-scripts.log`.
The diagnostic side-script output (`logs/ge-ui-20260909-side-scripts.log`) has an existing opcode-9
decoder limitation in loop bodies; those malformed regions are not used as proof of geometry or
complete client execution. The untouched onLoad arguments are asserted directly from the cache.

Eight new executable/cache-backed tests cover:

- Abort through real `If3ButtonHandler`, including double-click refund idempotency.
- Modify buy, native quantity adjustment and Confirm to submit the replacement offer.
- Native item-search resume into buy setup with coins, equipment, runes and empty slots.
- Real inventory processing and revision-240 wire encoding of all 28 slots, including max coins.
- Side panel replacement and the inventory Offer click remaining functional afterward.
- Partial buy abort in every one of the eight slots; unsupported operations remain gated.
- Partial sell modification retaining 96% proceeds and unfilled noted items.
- Full-inventory modification retaining escrow safely for a later retry.
- Cache initializer/parent identities (one of the eight tests; not gameplay proof by itself).

The initial fixture attempt used ordinary protected access with an already-open modal; this was
corrected to the real inbound slot click. `logs/ge-ui-20260909-red-2.log` then reproduced both
reported dead buttons at `465:23` before the routing fix. Additional fixture compilation and native
inventory-bootstrap issues were corrected without production workarounds.

Focused suite: `:content:other:grand-exchange:test` passed. Full verification command:

```powershell
.\gradlew.bat -I scripts/format-selected.init.gradle '-PopenruneFormatManifest=docs/grand-exchange/ui-fix-format-files.txt' :content:other:grand-exchange:spotlessApply :content:other:grand-exchange:test :content:other:grand-exchange:check :content:other:pouches:test :content:interfaces:collection-log-interface:test :server:app:assemble --no-daemon --console=plain
```

Test XML: **31 GE, 30 pouch/bag, 21 Collection Log = 82 tests, zero failures/errors/skips**.
Only the four Kotlin files in the explicit formatting manifest are formatted/format-checked.
Build output is retained in `logs/ge-ui-20260909-build.log`.
GE check and server assembly both passed (1m19s, 467 tasks).

## Runtime and preservation

Pre-change doctor completed successfully: canonical live JVM 28712, revision 240,
`.data/cache/SERVER`, `.data/cache/LIVE`, `.data/postgres` unchanged.
Evidence: `logs/ge-ui-20260909-doctor-before.log`.

Before deployment, the source-hash check covered 224 pre-existing/working source files and found
only the five expected GE source/test/build files changed. Existing Nex, Royal Titans, Vorkath,
collection-log, pouch, shared-engine and other user work was not edited in this pass.
Nothing staged, committed, pushed, restored wholesale or deleted.

Two controlled normal shutdown/restart cycles passed, without forced termination:

| Restart | Canonical JVM | Ready time | Startup log |
|---|---|---|---|
| First |25920|06:34:21|`logs/cerberus-audit-20260907/restart-2026090921.stdout.log`|
| Second, left running |30824|06:35:59|`logs/cerberus-audit-20260907/restart-2026090922.stdout.log`|

Both logs contain `OPENRUNE CUSTOM BUILD ACTIVE`, 435 loaded scripts and `Server ready`.
Both doctor runs completed with exit 0 and canonical revision-240 identity:
`logs/ge-ui-20260909-doctor-restart-1.log` and `logs/ge-ui-20260909-doctor-restart-2.log`.
All 226 pending/modified source/report files and five artifacts (server jar, GE jar, SERVER dat2,
LIVE dat2, GE JSON save) were byte-identical across deployment, before this report's final update.
See `logs/ge-ui-20260909-restart-verification.json`.

Server SHA-256: `C8830C8BA84F4D8E18657FC1106FF70E461651BC40E9828C07672DB89AFED2ED`.
GE JSON SHA-256: `52B7516668A0E49E929F2D65FAD2DF3FA5C015FF4A9300AB4CB549DE721520AD`.
`git diff --check` passed. No source restoration, cache revision change, manual account-data edit
or GE offer mutation was used for deployment verification. Startup cycle-overrun warnings are
not treated as gameplay failures or repaired as part of this UI task.

The full-worktree rebuild also includes the previously approved Nex foundation changes already
present before this task; no Nex source was edited here and this is not Nex gameplay acceptance.

**Verification boundary:** no after-fix desktop screenshot, live account trade, or RSProx GE
capture is claimed. Reopen GE in the client to verify visual presentation. Runtime boots alone
do not prove that presentation; native button, search-resume, transaction and packet tests provide
the executable verification described above.
