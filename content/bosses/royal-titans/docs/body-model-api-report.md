# Approved NPC body-model correction (2026-09-09)

Scope: the user's `ye` approves the bounded body-model API identified in the Medal review.
This report concerns that appearance correction only. It does not certify the entire encounter,
its unresolved scheduler, rewards, CAs, landing knockback, or visual/client parity.

## Mapping and changes

Approved mapping is recorded in `shared-api-approval.md` before implementation:
`Npc.setBodyModels/resetBodyModels` -> retained `NpcAvatar` snapshot -> `NpcInfoProtocol` ->
`RspNpcInfo` -> installed rsprot240 body customization. The established transmog pipeline is the
comparable native presentation API. This is a new approved project API, not an upstream API claim.

- Engine: body-only immutable model snapshots, validated count1..255/nonnegative32-bit IDs,
  duplicate-set/reset suppression, replay on protocol attachment, native respawn clear.
- Protocol: existing rsprot `setBodyCustomisation` / `resetBodyCustomisations`, with no recolours,
  retextures or player-mirroring flag. rsprot retains/replays the override to later observers.
- Registry: clear and disable before Delete listeners recycle the protocol avatar, then detach the protocol
  reference so later calls on a removed NPC cannot change the recycled appearance.
  Disabling after Delete could disable a replacement allocated by a later listener; the lifecycle
  test rejects any protocol call after release.
- Encounter: Branda55952 at transition+3; Eldric55940 at+7; retain active identity, UID, stats and
  footprint. Clear individually in the same update as each corpse transmog, and on removal.
- Rebuild exposed existing unmarked internal calls in NpcRegistry, RegionRegistry and
  LocRegistryRegion after the earlier `engine.annotations` dependency became visible. Class-local
  opt-ins match the existing PlayerRegistry precedent; no global opt-in/annotation weakening.

## Exact evidence / normalized event comparison

Raw2283 SHA256: `100EDE960B4ADBCE90E171B53C741AFB6055E01ED30D96848594EF70D682498B`.
Read-only parsing consumed444,169 bytes,36,137 packets,1,871 tick boundaries. Absolute offsets are
zero-based offsets in `raw/royal-titans-2283.bin`. Models in revision228 use its two-byte alternate
encoding; revision240 uses four-byte model IDs. Compare semantic events, not whole packets across
revisions. Existing native encoders own revision-specific field order.

| Normal2283 event | Transcript lines | Raw body bytes / offset | Production fixture expectation |
|---|---|---|---|
| B landing1558, index49582 still12596 |24082-24087|`02 01 10 DA` @333660|transition+3, set[55952], same UID/type, at(2910,9571), seq11966|
| E landing1562, index49605 still14147 |24165-24169|`02 01 04 DA` @334972|transition+7, set[55940], same UID/type, at(2910,9561), seq11963|
| B corpse1651, same index ->14148 |26835-26841|`01` @373383|reset on same tick as transform, survivor unchanged|
| E corpse1806, same index ->14149 |30746-30752|`01` @431669|reset on same tick as transform, survivor unchanged|

These are all four body-customization events in normal2283. Flag2 means replacement models,
count1; flag1 is explicit reset, not an empty model list. There are no recolour/retexture/mirror
fields in the set packets. Source decoder: revision228 `NpcInfoClient.kt`404-470, masks850-868.
Duo3402 corroborates the opposite kill order: E1351 L18042-18048 then B1352 L18101-18109, each reset.
Returning observer3402 T1188 L15366-15382 receives corpseBranda14148 with no override. No active
phase-two later-observer customization add was located in either duo capture; replay is tested
against the installed protocol behavior, not claimed as an observed duo packet.

Revision240 body-only payloads (excluding outer mask): B `7e 01 00 00 da 90`,
E `7e 01 00 00 da 84`, reset `7f`. Tests serialize the actual installed desktop writer, compare
the model payload, preserve it across postUpdate/new observer, and verify reset/pool cleanup.
This is deterministic serialized packet testing, not an outgoing live-client encounter capture.

## Active cache evidence

Stock LIVE definitions index2/group9:12596->[55951],14147->[55938],14148->[55952],14149->[55937].
Eldric's phase-two55940 is NOT his corpse55937. The active SERVER has definitions; LIVE supplies
JS5 models, so checking SERVER model archives would incorrectly report missing assets.

| LIVE index7 model | Bytes | SHA256 |
|---|---:|---|
|55952|34064|`d811121cb80c43cc6a53abcbde90198f1958a1580a829e45adc5859df61d342e`|
|55940|39475|`8ee22cc6bf4170f9d2b6fb936e4ffbd9fe3df77a34cefcc2f9a6ec5c4f898a9d`|

No cache paths, revision, definitions or model data changed in this pass.

## Verification status

The existing runtime was stopped through normal shutdown hooks before rebuilding loaded JARs;
no force-kill was used. Results follow below.

Changed sources (relative to the project root):

- `engine/game/.../entity/Npc.kt`, `NpcAvatar.kt`, `npc/NpcInfoProtocol.kt`.
- `api/net/.../rsprot/RspNpcInfo.kt`.
- `api/registry/.../npc/NpcRegistry.kt`, `loc/LocRegistryRegion.kt`, `region/RegionRegistry.kt`.
- Existing Royal Titans `RoyalTitansConfig.kt`, `RoyalTitansEncounterManager.kt`.
- Tests: `NpcBodyModelsTest`, `RspNpcBodyModelsTest`, `NpcBodyModelsLifecycleTest`,
  `RoyalTitansBodyModelsTest`, existing `RoyalTitansR240CacheAuditTest`.
- Docs: this report, `shared-api-approval.md`, `capture-validation.md`, `medal-flow-review.md`,
  and explicit formatter manifest `body-api-format-files.txt`.

Initial test attempts exposed the existing registry opt-ins and corrected test-only Huffman
provider construction (the installed provider is a sealed interface, not a SAM). Those failures
remain in `logs/royal-titans-body-api-tests-{initial,second,third,fourth}.log`. The first complete
pass is `tests-fifth.log` (52s); final lifecycle/repeated-completion changes are verified separately.

Final required command passed in44s (`logs/royal-titans-body-api-required.log`):

```powershell
.\gradlew.bat :content:bosses:royal-titans:test :or-cache:test --tests dev.openrune.RoyalTitansR240CacheAuditTest --no-daemon --console=plain
```

Royal Titans:92tests including four new body tests; focused cache:3tests including model assets.
No failures/errors. All four normalized captured body events match deterministic production
expectations: landing offsets+3/+7 and same-update independent resets. Four consecutive phase-two
completions return to two clean active NPCs without retained models; this does not certify rewards.

The unscoped broader `check` failed only formatting checks in registry, instances and Royal Titans,
including untouched ObjRegistry, InstanceEvents and pre-existing RoyalTitansMovementTest edits.
Those were preserved. The retry uses the explicit source formatter manifest, NOT global formatting
or disabled tests. This formatting boundary must be disclosed when reporting the broader check.

```powershell
.\gradlew.bat -I scripts/format-selected.init.gradle '-PopenruneFormatManifest=content/bosses/royal-titans/docs/body-api-format-files.txt' :engine:game:check :api:net:check :api:registry:check :api:game-process:check :api:instances:check :content:bosses:royal-titans:check :or-cache:check :server:app:assemble --no-daemon --console=plain
```

The scoped check and server assemble passed in5m54s:480tasks,134executed,2from cache,344up-to-date.
Log: `logs/royal-titans-body-api-scoped-check.log`. Test totals, all with zero failures/errors:

| Module | Tests |
|---|---:|
|engine/game|32|
|api/net|86|
|api/registry|8|
|api/game-process|8|
|api/instances|20|
|royal-titans|92|
|or-cache (full suite)|93|
|Total|339|

Runtime artifacts before launch (SHA256):

- Royal Titans `1F142690397BC79E7AE7C66FB815ECEE5E04567F25433AC8B59C1610CDE04998`
- engine/game `61D076C9D0CA732541C70FC459989DC95D7087409C007ADA1C14536E39587D35`
- api/net `96ED579C35F3B70C1FCF85E9FE92F0B32174066521F538D4AFDF2F73041E14A8`
- api/registry `5B0CE1A431B361C6A36697C8931CA242846FD368A9CEB4E67C7CA69FC7C2AD5C`

## Real server and two controlled restarts

All times are local 2026-09-09. Initial launch used the canonical `:server:app:run` task, hidden
background process and no source-rewriting startup task. Restarts used the existing
`scripts/cerberus-controlled-restart.ps1`, with IDs2026090903 and2026090904, normal shutdown hooks
and canonical working-directory validation. No gameplay JAR was rebuilt while a game JVM ran.

| Stage | Game JVM PID | Custom-build marker | Ready / scripts | Doctor log under logs/ |
|---|---:|---|---|---|
|Initial rebuilt start|28080|01:13:35|01:13:37 /432|royal-titans-body-api-doctor-start.log|
|Restart1|21040|01:14:39|01:14:41 /432|royal-titans-body-api-doctor-restart1.log|
|Restart2|41432|01:15:39|01:15:41 /432|royal-titans-body-api-doctor-restart2.log|

Each doctor confirms this root's `user.dir`, revision240, SERVER/LIVE split and `.data/postgres`.
Both cache fingerprints remain identical to the pre-edit check:

- SERVER `A77ED0C2DFD48B093050913041B098FB4A700D6A1C32DAECABFCD2A6DD81696C`
- LIVE `FD0FE5EF66039A791A900EDDDF1CDBCFE57E09E8C32EA3496C69E96217A41A7B`

Runtime logs: `logs/royal-titans-body-api-start.{stdout,stderr}.log` and
`logs/cerberus-audit-20260907/restart-202609090{3,4}.{stdout,stderr}.log`.
The existing `Game database service failed to shut down` / `Connection reset by peer` warning
reproduced during controlled shutdowns; startup succeeds afterward. This is NOT a clean
PostgreSQL data-flush certification. First-cycle over-budget logging also remains.

## Final boundaries

- The approved body-model API and its encounter integration are implemented and deployed.
- No fresh client-controlled solo/duo fight or live outgoing encounter capture was performed in
  this pass. The four-event comparison above is from deterministic native encounter fixtures and
  real serialized r240 extended-info payloads, NOT a live OpenRune-versus-RSProx fight capture.
- A fresh visual check of phase-two appearance and both corpse orders is still required.
- Previously documented fight-flow/elemental lifetime/scheduler, CA, reward and other parity gaps
  remain in `medal-flow-review.md` and `capture-validation.md`; no full1:1/completion claim is made.
- Unscoped formatting checks remain red on files outside this pass. Scoped checks, tests and
  server assemble pass; unrelated source formatting was preserved.
- No duplicate boss implementation, nexus changes, cache edits, source restoration, staging or
  commit. Unrelated AGENTS, PlayerDeath and Vorkath changes were not edited.
- Final `git diff --check` passes. All four runtime JAR hashes above are unchanged after both
  restarts. PID41432 is left running and owns listeners43594,8080,9091.
