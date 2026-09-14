# Boss migration checkpoint and player test guide

This is a resumable handoff, not a project-completion or full OSRS-parity claim. The detailed authority,
mechanic mappings, uncertainties, commands and results remain in [DSL_MIGRATION.md](DSL_MIGRATION.md).
Update this checkpoint after each completed boss and immediately before any authorized deployment.

September14 current checkpoint: ToA Ba-Ba ordinary combat and native session ownership are verified.
Native BossSpec preserves six-tick cadence/gates/callbacks; typed session ownership preserves party
admission, reconnect, native death/spectator, reward-leave and cleanup in either logout order.
Kephri native cycle support is verified but awaits manager integration.18 focused/426 full ToA cases,
scoped checks, architecture/self-tests and application assemble pass;1,975 cases with retained results.
Archive/backup: logs/toa-native-session-baba-source-20260914.zip /
backup/toa-native-session-baba-20260914. Next: Kephri manager integration, Akkha/Zebak native ordinary
routes, remaining ToA specialized scheduling/Wardens, then ToB/CoX. Current DSL_MIGRATION.md has exact
scope, test-only corrections and next mappings. Source only; deployed c4fc9390f/JVM23812 and all holds
remain unchanged. Do not claim complete raid routing or full OSRS parity from this checkpoint.

Earlier September14 checkpoint: Whisperer native manager integration is verified. Special slots,
phases, completion, secondary shots and independent melee expiry now use native routes, preserving
RNG/prayer/snapshot and cleanup ordering.37 focused/207 full DT2 cases, scoped checks, architecture
self-tests and application assemble pass.1,957 valid cases with retained Doom/shared results.
Archive/backup: logs/whisperer-native-specials-source-20260914.zip /
backup/whisperer-native-specials-20260914. Identified DT2 routing debt is closed; mechanical evidence
gaps remain separately recorded. Next: mapped ToA Ba-Ba native ordinary combat, raid session ownership
and remaining forms, then ToB/CoX. Source only; runtime c4fc9390f/JVM23812 and all holds unchanged.
The current DSL_MIGRATION.md section contains the exact mapping and acceptance criteria.

Earlier September14 checkpoint: Doom's duplicate attack clock is removed; both writers now extend
native actionDelay.43 focused/158 full Doom cases, scoped checks and architecture/self-tests pass.
Its accepted budgets are legitimate native session-owned metadata; the documented presentation and
spatial boundaries remain. Archive/backup: `logs/doom-native-holds-source-20260914.zip` /
`backup/doom-native-holds-20260914`. Whisperer manager integration is active, then ToA/ToB/CoX.
Last application assemble is a48445587 below; final app integration follows Whisperer. Source only;
runtime c4fc9390f/JVM23812 and all holds unchanged. See current DSL_MIGRATION.md for exact scope.
Earlier September14 checkpoint: DT2 native start/respawn and Leviathan opportunity owners,
Whisperer support APIs, and Doom secondary volley/larva/style-hit queues are verified. Full
DT2 202/Doom156, scoped checks, architecture/self-tests and application assemble pass.1,950 valid
cases with retained shared/other-boss/ToA results. Archive/backup:
`logs/dt2-lifecycle-doom-queues-source-20260914.zip` / `backup/dt2-lifecycle-doom-queues-20260914`.
Next: integrate verified Whisperer helper/spec into its manager, finish any actual Doom budget/hold
routing gap, then ToA/ToB/CoX. Exact forced-roar fix, native timing mappings and remaining scope are
at the top of DSL_MIGRATION.md. Source only; runtime c4fc9390f/JVM23812 and all holds unchanged.
Earlier September14 checkpoint: Leviathan special phases/completion, Vardorvis native choices and
special/respawn queues, and Doom's complete car graph are verified. Full boss API72/DT2 186/Doom151,
scoped checks, architecture/self-tests and application assemble pass.1,929 valid cases including
retained shared/other-boss/ToA results. Archive/backup:
`logs/dt2-specials-doom-car-source-20260914.zip` / `backup/dt2-specials-doom-car-20260914`.
Next: DT2 start/respawn and Leviathan opportunity owners, Whisperer special/secondary routes;
Doom remaining volley/helper/style-hit scheduling and budgets; then ToA/ToB/CoX. Exact mappings,
test corrections and retained behavior/evidence limits are in the current DSL_MIGRATION.md section.
Source only; runtime c4fc9390f/JVM23812 and all holds unchanged.
Earlier September14 checkpoint: Duke's private timeline is replaced with the native owned queue,
and Doom's +2 charge start /13/17/20 beam readiness is native. Focused behavioral checks, full
DT2 171/Doom 146, architecture/self-tests and application assemble pass. With the unchanged
1,592 shared/other-boss/ToA cases below: **1,909 verified cases**. Archive/backup:
`logs/duke-owner-doom-charge-source-20260914.zip` / `backup/duke-owner-doom-charge-20260914`.
Next: Doom's complete car graph and remaining DT2 special/secondary-volley/start/respawn ownership,
then ToA/ToB/CoX. Detailed pre-edit car mapping and exact DT2 debt are at the top of
`DSL_MIGRATION.md`. Source only; runtime c4fc9390f/JVM23812 and all holds remain unchanged.

Earlier September14 shared checkpoint: the existing native world queue now supports owned dispatch
at an established lifecycle boundary. Seven new behavioral groups and full shared/25-boss/ToA
regression checks pass: **1,592 cases**. ToB/CoX compile/check pass with no test cases. Archive/backup:
`logs/native-owned-queue-source-20260914.zip` / `backup/native-owned-queue-20260914`.
Duke adoption and Doom charge readiness are currently being implemented; their focused checks and
final application/architecture integration are next. Source only, runtime/holds unchanged.
Read the current section and mapping in `DSL_MIGRATION.md` before historical checkpoints below.

September14 verified content checkpoint: DT2's four protected native instance owners and Doom's declared
shockwave graph/readiness pass full DT2 170/Doom 140, scoped checks, application assemble and
architecture/self-tests. The retained 1,023 shared/other-boss cases remain valid: **1,333 total**.
Archive/backup: `logs/dt2-entry-doom-shockwave-source-20260914.zip` /
`backup/dt2-entry-doom-shockwave-20260914`. Source only; runtime c4fc9390f/JVM23812 and all holds
unchanged. Next: native Doom charge readiness and the mapped shared owned world-queue dispatch
needed for Duke's exact StartCycle pool/callback/ordinary ordering, then car and ToA/ToB/CoX.
The top of `DSL_MIGRATION.md` records the pre-edit API gap, approval, mapping and acceptance.

Earlier September14 checkpoint: `DSL_MIGRATION.md` records DT2 native typed session
ownership/cleanup and Doom native phase identity/two-tick shield readiness, following the verified
seeded decisions, Leviathan cadence and Duke interval rotation. Full Doom134/DT2164 and still-valid
boss API72/instances54/25 other boss modules897 pass scoped checks: **1,321 cases**. Application
assemble and architecture detector/self-tests pass; source archives/backups are recorded there.
Next: four native DT2 InstanceScript entry/exit owners and Doom shockwave graph/readiness, preserving
StartCycle/LateCycle boundaries; then remaining specialized timing and the approved ToA/ToB/CoX order.
Runtime remains c4fc9390f/JVM23812, with deployment/evidence/replay holds unchanged. Read the
current section before the historical checkpoints below.

Earlier September14 checkpoint: the current section of `DSL_MIGRATION.md` records native Yama/Doom
session ownership (124/127 cases), Whisperer/Vardorvis (145 DT2 cases), and the three Dagannoth
Kings (14 cases). The integrated 511-case selection, scoped checks, architecture and application
assemble pass. Source archive/backup and exact retained adapter/selector boundaries are recorded
there. Next: Leviathan, Duke/DT2 lifecycle and Doom's missing declarative selector route, then
ToA/ToB/CoX. Runtime remains `c4fc9390f`; no deployment of these changes is claimed.
The current Nex ledger's top section records deferred native kill hooks,
generic personal KC (manual increment removed), and the boosted-Prayer altar guard.166 Nex cases
and installed KC metadata pass;16 death/68 drop results remain valid. SERVER NPC11278 metadata is
prepared with a verified backup; LIVE is unchanged. Runtime remains `c4fc9390f`, under existing
deployment holds. Read that section before the historical September13 checkpoints below.

## Current runtime checkpoint: September13, two verified restarts complete

JVM23812 is ONLINE from04:40 EDT on43594/8080/9091, canonical checkpoint `c4fc9390f`.
Nex protected access `444a39eb3`, Central dependency `07b00480e` and client/activity shutdown
`0f729d055` are deployed. Both ordinary restarts20364 ->25096 ->23812 passed native save/closure,
actual process exit, replacement startup, doctor and inventory/bank/realm persistence checks.
Revision240, SERVER/LIVE hashes and `.data/postgres` authority are unchanged. A fresh offline
1,243-file PostgreSQL backup and4,393-file source archive were verified before replacement boot.
Full evidence/receipts/hashes and build-marker limitation are in the current Nex ledger.

M21 clicked-prison ownership/cancellation706e11330, M26 completed-kill occupant handoff13719ba1c,
bounded M24 Wrath death-position snapshot30ef8129c and M14 Shadow marker ownership88d0abf0c are
complete in source. Fixed-public sessions49f73a317 and prepared owned spawning are now verified:
20 native groups,54 total instance cases,1,090 boss cases and322 shared/app cases pass;
scoped checks, architecture and app assemble pass. Both source archives are byte-verified. Each
increment has a verified source backup; see the Nex ledger for hashes/logs/native failure limits.
These increments are not deployed. Nex public-session adoption is now source-verified:12 new groups,
139 Nex cases,1,102 boss cases and322 shared/app cases pass, with scoped checks, architecture and
app assemble. One native Fixed session owns admission/actors and survives completed rounds; callback
and exact-identity cleanup are covered. Nine source files are byte-verified in the archive recorded
in the Nex ledger; commit219fa193a. Bounded S08 native defence minimum and Nex250 configuration now
pass142 Nex cases,1,105 boss cases and484 engine/shared/weapons/app cases, scoped checks, architecture
and app assemble; commit1ab48e36d. M18 marking and owned tint/late-queue cleanup now pass148 Nex,
1,111 boss,494 engine/shared/weapons/app and408 existing ToA cases, scoped checks/architecture/app
assemble; commite6bfba9f6. Bounded M06 opening animation9203->captured9182 also passes all148 Nex
cases, scoped checks, architecture/app assemble; both source archives are byte-verified. Source-only.
M06 captured facing512/sound5303/overhead speech also passes150 Nex cases, scoped checks/architecture/
app assemble; three-file archive byte-verified, commitbe131db42. Bounded M11 native bodyguard drops/
exact no-remains now pass153 Nex/1,116 boss,501 engine/shared/weapons/app,62 content/drops and408 ToA
cases (2,087 total), scoped checks/architecture/app assemble; nine-file archive verified. Source-only.
M06 captured guard anchors now pass155 Nex cases, scoped checks/architecture/app assemble;
native blocked terrain and ranged LOS are preserved through both rosters. Three-file archive
verified; commit769ac739e, source-only. S04 concrete reward preparation now passes68 content/drops,
155 Nex/1,118 boss and408 ToA cases, with501 unchanged shared cases valid (2,095 total), scoped
checks/architecture/app assemble. Three-file archive verified; commit6dc2a4dd0. S04 allocation
journal now passes native Flyway/PG rollback/concurrency/save-lock ordering/two isolated restarts,
9 account/503 shared,155 Nex/1,118 boss,68 drops and408 ToA (2,097 total), scoped checks/architecture/
app assemble. Thirteen-file archive verified; commit753b7b1ef, SOURCE ONLY. Explicit native commit-
outcome API now passes actual lost-ack/unsent-commit tests,8 DB/507 shared,155 Nex/1,118 boss,68 drops
and408 ToA (2,101 total), scoped checks/architecture/app assemble; four-file archive verified.
Native ground registration results now pass6 new groups/86 selected tests, with2,107 total valid
results including reused suites; scoped checks/architecture/app assemble and3-file archive pass.
M06 captured inward guard facing now passes157 Nex tests and2,109 total valid results; scoped
checks/architecture/app assemble and3-file archive pass. Captured1979/9194 helper attack
animations now pass157 Nex tests with native output/cooldown/delay checks, scoped checks/
architecture/app assemble and3-file archive. S04 native connection
recovery now passes13 DB/70 selected checks and2,114 total valid results; frozen startup authority,
native failure/monitor behavior, scoped checks/architecture/app assemble and3-file archive verified.
Typed claims/quantity settlement now pass11 account/72 scoped checks and2,116 valid results;
scoped checks/architecture/app assemble and13-file archive pass. Recovery commit8c858f737.
Claim commit26452a0cf is backed up. Typed balance reads now pass13 account/40 scoped checks,
2,118 valid results, scoped checks/architecture/app assemble and5-file archive.
Balance commitff86491af is backed up. Captured Fumus/Glacies projectiles now pass158 Nex tests,
2,119 valid results, scoped checks/architecture/app assemble and4-file archive.
Projectile commit195d6f60a is backed up. Captured guard cast sounds now pass160 Nex tests,
2,121 valid results, scoped checks/architecture/app assemble and2-file archive. Native accepted
hit/miss output, rejection and duplicate prevention are verified; no live audio acceptance claimed.
Sound commit6cc7ea092 is backed up. Durable non-pickup retirement now passes16 account/77 selected
cases,2,124 valid results, scoped checks/architecture/app assemble and15-file archive. Shared claim/
retirement quantity exclusion, exact settlement and two isolated database restarts are verified.
Retirement commit609991bf7 is backed up at backup/nex-s04-retirement-20260913. Independent native
claim/full-snapshot SQL composition now passes83 selected cases,2,176 valid results, scoped checks/
architecture/app assemble and2-file archive; backup/nex-s04-native-snapshot-20260913. Real writers,
fresh reload, late SQL rollback, lost ACK and both Existing outcomes are verified without a live
saver wrapper. The52 Sailing cases newly included in the rollup are one new test plus51 existing
regressions. September13 correction: reuse existing native drop, pickup and account-save systems.
World pause/isolation and restart-persistent unclaimed loot were not established prerequisites for
ordinary migration. The stronger approved S04 replay contract remains separately unfinished; do not
activate recovery alongside unrecorded pickup. Next: bounded RSProx M20 comparison and the existing
shared Nex item-delivery mapping. M31 is now source-verified:163 Nex/68 drops,2,179 valid cases,
scoped checks/app assemble/architecture and6-file archive; backup/nex-m31-shared-delivery-20260913.
No new API. Shared native delivery owns item logging/tracking/clan output; current global chat,
pet path, rolls and lifetime override are retained. Fresh RSProx search and48 retained Containment
casts disprove a fixed missing west edge. All768 current-map observations match native destination
blocking flags; directional step checks miss17. Both historical maps are byte-identical and all85
static loc collision definitions match current native inputs. Geometry mapping is supported; normal
timing/rotation evidence remains before M20 placement activation. M31 source commit64032d93f.
Full S04/reward accuracy remains open. See the Nex ledger for evidence and the corrected scope;
SOURCE ONLY, no live V43/V44/V45 migration. M04 cooldown/equipment and
M20 general placement/clipping remain evidence gaps. Boosted-floor evidence remains
separate. Full local/pinned searches verified the missing fixed
route before using the latest conditional approval. Existing timing/reward gaps remain separate.
Preserve unresolved E07 timing/damage/geometry evidence. Full Nex remains incomplete. Raid
bosses remain after Nex/Yama/Doom/DT2/separate Dagannoth Kings review, in ToA/ToB/CoX order.

## Prior source and crash-recovery checkpoint (historical)

Latest boss source checkpoint **444a39eb3** (`backup/nex-protected-access-20260913`): M01/M02
barrier, altar and `::nex` native protected movement is implemented and verified by98 Nex and155
shared-module cases. Scoped formatting, Nex check, app assemble and architecture checks pass.
This slice is source-only. See the Nex ledger for files, red/green history and evidence limitations.
Central dependency shutdown source `07b00480e` is committed/backed up:31 focused Central tests,
1,061 boss cases and285 affected shared/app cases pass, with app assemble and architecture checks.
Game-side client/activity-writer source `0f729d055` is also committed with12 new focused tests;
final all-boss/shared/app checks pass (1,061 boss and297 shared/app cases). The next deliverable
is the reviewed old-runtime handover and two verified restarts. The current Nex ledger records exact
files, hashes, test logs and remaining gates; none of this new Central source is deployed yet.

User priority remains server online through RSProx first, then Nex → Yama → Doom → DT2 →
separate Dagannoth Kings review → ToA → ToB → CoX. The earlier60–65% estimate covers the
original scope before adding raids, not the expanded total. Existing approvals and holds persist;
new shared routes are approved only after verifying no suitable route already exists.

The crash ended historical JVM42364. Cold recovery JVM10576 booted incoming-hit checkpoint
`a87f5fc47`, but its first controlled restart failed after native save preflight completed with
zero players/pending saves. Its gated recovery has now completed every native producer,
persistence and Central resource closure, followed by native PostgreSQL/lock closure and normal
exit. Replacement JVM1856 is online from03:32 EDT with43594/8080/9091 listening and matching
inventory/bank/realm fingerprints. An offline backup predates recovery. Exact evidence and steps
are in the latest Nex ledger and `logs/crash-recovery-20260913-025956/`.

The empty-world1024-cycle shutdown timeout is fixed in source `9e592b389` with backup branch
`backup/native-shutdown-empty-world-20260913`. Five focused regressions and the required
shared/app/Nex build pass. Native producer and persistence shutdown completed during gated
recovery, but Central2.0.1 cleanup resolves services after Koin4.0.2 has closed its registry.
The existing native Central owners were recovered and their social executor had zero submitted,
active or queued work; native stop/flush methods completed before datasource/PG closure.
The tested permanent Central dependency correction remains source-only: resolve owners before Koin
closure, stop/await producers before social drain, retain failed/pending writes and propagate
the final cleanup result to existing embedded stop. The exact installed2.0.1 source is pinned
at official9d718ca61aa6437007f883f27c35126fac5a0558 in the sibling backups checkout. No dependency
changes are loaded. Complete this correction and two controlled restarts before deployed
acceptance. Server recovery is successful; the failed ordinary restart remains failed. Further
approved Nex source work can proceed under this deployment hold. Older runtime claims are history.

## Previous source continuation: Nex native incoming hits (September13)

Branch `refactor/nex-native-impact-20260913` continues approved S02/S03 after the backed-up S10
source repair. Incoming guard thresholds, Siphon absorption, Reaver lifesteal, accepted contribution
and Deflect reflection now use native Resolve/Impact through BossCombat. Queue-time gameplay effects
are removed; validity remains tied to exact encounter/owner identity. Shared Impact input observations
are committed as63c5028ee. The existing Nex ledger holds mapping, red/green tests, remaining formula
conflicts and final verification/backup details. No deployment has occurred; see S10 gate below.

## Source checkpoint: approved S10 native save/shutdown repair (September13)

Working branch: `fix/native-shutdown-order-20260913`; pre-work recovery:
`backup/before-s10-shutdown-20260913` at `38fe43cc1`. S10 is approved, not a new approval request.
Source repair committed as `cde8b17a8`, recovery branch `backup/native-save-shutdown-20260913`,
focused28-file archive `logs/native-save-shutdown-source-20260913.zip` (hash in Nex ledger).
Ordered native service shutdown, completed-result waiters, retained interrupted account batches,
honest commit/failure accounting, safe logout removal, bootstrap-owned PostgreSQL closure and a
native save-first restart gate are implemented in source. Five core modules pass44 tests (33 new);
standalone operational gate tests pass6. The Nex ledger records scoped checks/build and limitations.
Real native logout/save/callback/player-removal integration now executes in tests, including failed
save recovery and fail-closed deletion hooks. Two isolated child-JVM tests prove failed cleanup keeps
the process alive and successful cleanup exits naturally. These fixtures do not use live PostgreSQL.

Live JVM42364 still runs source0372044fc. No S10 restart/attachment/deployment has occurred.
The updated helper deliberately refuses old JVMs without native prepareShutdown. The one-time
legacy save-first handover remains next; do not deploy by calling an old System.exit helper.
Complete two verified restarts before claiming deployed acceptance. Further approved source migration
can continue while that operational acceptance remains explicitly held.

Overall migration status estimate: approximately60-65%, not a measured completion percentage.
Nine boss architecture checkpoints have been migrated/backed up/deployed; Nex is partial; Yama,
Doom and the four DT2 encounters remain. That is seven individual encounters including Nex,
plus separate Dagannoth Kings review. Earlier bosses retain documented client/evidence acceptance
and Royal Titans integration gaps; they are not all certified full OSRS implementations.

## Previous continuation: Nex lifecycle and delayed effects (September13)

Source work now builds on `0372044fc` on `refactor/nex-main-native-combat-20260910`.
Current completed source slice: `55efb8fa3`, recovery branch
`backup/nex-native-lifecycle-20260913`, source ZIP `logs/nex-native-lifecycle-20260913.zip`
(4,401 files; SHA-256 in the Nex ledger). Nex 67 tests; affected-module run 223; all-boss
task selection 1,030 result cases; app assemble, architecture and unscoped Nex check pass.
Thirteen new tests were added. This source is backed up but NOT deployed.
Nex public-room maintenance uses the official GameLifecycle.EndCycle instead of the first
online player's post-tick. The former lastWorldTick dedup clock is removed. Deleted room actors
are retired without continuing their effects or stat repair. Eight delayed encounter callbacks
now use existing approved BossEncounter.schedule ownership; special/reflection hits reuse native
impact validity, including stable participant identity. Damage/rotation/geometry constants have
not been changed. This is still a bounded Nex migration, not full OSRS parity.

**Deployment hold: native account saving failed during the previous restart.** The live JVM
42364 started from `0372044fc` at 23:37:50 EDT September12. It is not running this September13
source slice. The preceding PostgreSQL shutdown occurred before the save queue finished.
Matching stored inventory/bank/realm fingerprints do not prove the failed final in-memory save
was preserved. Do not repeat the current System.exit restart helper as a save-drain workflow.
The required second successful restart has NOT been completed for this Nex main checkpoint.

S10 is a new, explicitly mapped shared shutdown/save-ordering prerequisite in
[NEX_OSRS_AUDIT.md](nex/NEX_OSRS_AUDIT.md). It was approved September13 and source implementation
is in progress on `fix/native-shutdown-order-20260913` (baseline recovery branch
`backup/before-s10-shutdown-20260913`). It covers one
database shutdown owner, completion-aware shutdown waiters, ordered native save/database closure,
honest save results and a verified pre-drain restart gate. The on-disk AGENTS checkpoint applies;
do not request already approved M01-M36/S01-S10 again or silently widen S10.

Existing source recovery: `backup/nex-main-native-verified-20260912` at `0372044fc` and
`logs/nex-main-native-verified-20260912.zip`; full hash and verification are in the Nex ledger.
The prior all-boss regression passed 1,017 tests across 27 task selections. Latest slice tests,
new recovery checkpoint and actual deployment status must be read from the ledger, not inferred
from that historical count. No new rendered-client fight or packet comparison was completed.

## Previous source continuation: Nex main combat DSL (September12)

Branch `refactor/nex-main-native-combat-20260910` contains approved S09 target-bound continuation
(`8b08bb84a`), optional S01 late prayer (`e2c28eb33`), and main Nex DSL (`8f0a6928c`).
Main and helper ordinary attacks now register through BossCombat/BossSpec/NvPCombat. BossEncounter
owns normal selection, phases, forced cadence and Zaros overhead rotation; the private ordinary
attack loop/counters are removed. This is NOT full Nex migration or mechanical parity.

The follow-up preserves outgoing elemental combat and native attack state across bodyguard calls,
fixes helper respawn ownership, and explicitly preserves main Nex's native death source for Wrath
without an automatic respawn competing with room reset. See the existing Nex ledger for red/green
tests, full command results, current backup and actual deployment. Do not infer deployment from
HEAD or copy the historical JVM below as current. Remaining room lifecycle, specialized effects,
damage/contribution, rewards/persistence/CAs and visual evidence are still mapped work.

M01-M36/S01-S09 are approved; do not ask again for the same scope. No new death engine was added.
Next safe migration work is the existing public-room/lifecycle mapping and scoped specialized
effects; exact Wrath presentation is still unresolved, not fixed by merely retaining its source.
The resume script and starter prompt now point to this main-combat checkpoint.

## Earlier continuation: Nex native minion combat (September10 history)

The latest user request resumes Nex. Branch `refactor/nex-native-minions-20260910` is based on
`30e94ae13`; pre-work backup `backup/nex-before-minion-dsl-20260910` preserves that source.
The previously characterized four guards and encounter Reaver now use BossCombat/BossSpec and
the existing approved Effect.NpcAttack adapter. Nine additional lifecycle/ownership tests pass;
Nex total35 (including five newer main-loop characterizations) and executable revision240 cache audit4
pass. Exact regression failures, successful
runs, upstream/local classification and packet contradictions are in
[NEX_OSRS_AUDIT.md](nex/NEX_OSRS_AUDIT.md). No new engine API was added.

This is a bounded helper migration, not full Nex completion. Main Nex ordinary/special selection,
phase coordination and fixed public-room lifecycle still require migration; rewards, healing
conflicts, CAs and rendered-client parity remain open. The existing wrong minion presentation is
retained as an explicit architecture characterization, not silently certified as OSRS-correct.
Production source `41fd77bff` is deployed: JVM21724 ->17376 ->27808, markers13:02:40/13:05:34 EDT,
two canonical revision240 doctors and unchanged fresh inventory/bank/realm/cache fingerprints.
Backup `backup/nex-native-minions-20260910`; source ZIP/hash and exact logs are in the Nex ledger.
Newer main-loop tests/documentation do not change that running production build. No client fight
or OSRS/OpenRune packet comparison was completed. The next genuinely missing selector capability
is mapped as proposed S09 in the ledger, with its own required approval checkpoint; do not request
M01-M36/S01-S08 approval again. The resume helper defaults to Nex, not the completed RT reward pass.

## Earlier continuation: Royal Titans native rewards (September10 history)

Latest user priority is Royal Titans remaining integrations before Nex. The reusable starter is
[RESUME_OPENRUNE_BOSS_API_DSL.txt](RESUME_OPENRUNE_BOSS_API_DSL.txt); the helper now defaults to
`royal-titans` and loads that file instead of embedding a stale next-boss prompt.
Pre-work source backup: `backup/royal-titans-before-rewards-20260910` at `9989c33f0`.
Current work branch: `fix/royal-titans-native-rewards-20260910`.

Deployed source is now `eb5d0f4a8`, preceded by shared delivery commit `ac1ca5e90`.
Backup `backup/royal-titans-native-rewards-20260910`; source-only ZIP and SHA are in
the reward continuation report. Nineteen new tests; Royal Titans133, drops60, death6,
drop-table5, Collection Log10 and cache3 all pass. Broader505-task checks/app assemble
and the other migrated boss/Nex regression suites pass. Two controlled restarts:
39612 ->42216 ->21724, markers05:18:30/05:19:58 EDT; doctors and fresh stored
inventory/bank/realm fingerprints match. Existing DB-close shutdown warnings persist.
No rendered-client reward scenario or OSRS fight-packet diff was performed in this pass.

The bounded reward extraction/table pass and its exact verification/deployment state are recorded
in [reward-workflow-20260910.md](../../content/bosses/royal-titans/docs/reward-workflow-20260910.md).
Do not mistake native delivery or stable notification IDs for durable reward allocation.
Contribution saturation, crash recovery, CA awards, pet unlocks, and documented fight-evidence
gaps remain open. No full Royal Titans completion or 1:1 claim is made.

## Earlier deployed architecture checkpoint (history)

Latest deployed update (September10): Royal Titans source `1279d1676` has129 passing module tests
and3 cache audit cases. Native platform/arena/teleport phases, paired reset and elemental
death-deadline ownership are implemented; focused handlers leave an830-line coordinator
(original2,017). Scoped and broad505-task checks pass. Two controlled restarts are complete:
36776 ->25860 ->39612, with canonical revision240 doctor/marker checks and matching fresh
persistence/cache fingerprints. See the detailed deployment record below for shutdown warnings.
Earlier incremental sections are history, not instructions to repeat completed work. Nex follows Royal Titans;
its first3 native minion characterization tests now pass (21 Nex tests total), with NO production
Nex migration edits yet. Existing mechanics/reward/CA
uncertainties and client acceptance remain explicitly open; this is not full OSRS parity.

Recoverable Royal Titans source backup: `backup/royal-titans-native-checkpoint-20260910`,
commit `1279d1676`; ZIP `logs/royal-titans-native-checkpoint-20260910.zip`, SHA256
`46FF15C79D008FE4407D930EDA0226E24C3A1739BCCA940A66145BA1901EE1AC`.
7,457 entries, zero excluded-path violations. No database/cache/runtime data is in this archive.
The user reported8% plan allowance at this checkpoint; do not infer a new remaining percentage
from context capacity. Finish the current bounded work and use this handoff at the requested cutoff.

Nex test-only continuation is committed as `4c6dc9f2b` and backed up on
`backup/nex-minion-characterization-20260910`. ZIP `logs/nex-minion-characterization-20260910.zip`,
SHA256 `EE25B9D73CB7A1D3BAE90E3FB621AAA120582B0B4EE0367593763BD844C234FB`.
7,458 entries, zero excluded paths. Current production Nex is unchanged from the live checkpoint.
Only the pre-existing four modified `.data` files and untracked GE area remain outside the commits;
preserve them. The resume helper prints actual Git branch/HEAD rather than assuming these IDs.

- Canonical workspace: `C:\Users\norma\Documents\Codex\2026-08-10\lets-work-on-the-openrune-server\work\OpenRune-Server`.
- Source safety baseline: `798b8c320`, branch `backup/boss-dsl-source-baseline-20260909`.
- Additional passing source checkpoint: `69f9deeba`,
  `backup/boss-dsl-passing-20260909-muspah-lifecycle`. Includes native Muspah membership/respawn,
  hazard retirement, native KC metadata, DSL final cadence and the spatial split (60 passing tests).
  This local Git backup includes no new runtime/cache/PostgreSQL copy and is not a deployment.
- Deployed source: Muspah checkpoint `f35675be2`, shared phase clock `f0f1d8d99` (46 API tests),
  deployment checkpoint `c48e36cad`, diagnostic-harness follow-ups `c40dca8ad` and `7784e155e`.
  Verified checkpoint branch: `chore/boss-migration-checkpoint-20260909`; next source work is on
  `refactor/cerberus-boss-dsl-20260909`. Re-read actual Git state.
- Recoverable predeployment source branch: `backup/boss-dsl-predeploy-20260909-muspah` (`c48e36cad`).
  Source-only ZIP: `logs/boss-dsl-predeploy-20260909-muspah-source.zip`, SHA256
  `C1B93AE0C0B631FB7DB2938792A66C88DB5D1B1C8629B31B036F16E4F4603AAB`.
  Diagnostic follow-ups are also backed up on `backup/boss-dsl-live-audit-checkpoint-20260909`
  and `backup/boss-dsl-native-logout-checkpoint-20260909`. No remote push/merge occurred.
- Completed live-checkpoint backup: `backup/boss-dsl-live-verified-20260909`, commit `18a8aad57`.
  ZIP `logs/boss-dsl-live-verified-20260909-source.zip`, SHA256
  `C4EECA806195DFEB29E7E8630290C58A46772FB11E3685C754EDAAB81BDE3B93`.
- Cerberus source migration now has75 passing module tests and2 revision-240 cache-audit tests.
  BossSpec owns normal attacks, triple/rotation and phase deadlines; native InstanceScript owns
  membership and same-actor death/reveal. The arena handler retains Souls/lava/firewall behavior.
  Cerberus is now deployed locally after two controlled restarts; visual acceptance remains open.
- Corrected Cerberus source backup: `backup/cerberus-native-frame-verified-20260910`,
  commit `c421aca33`. ZIP `logs/cerberus-native-frame-verified-20260910.zip`, SHA256
  `173612586CDAD4B5C39F4BAB304B6A63FF33E0FD10A7D1194B5CC20E74E4257D`.
  7,439 entries, no excluded-path violations. Use this instead of the pre-frame-fix backup.
- Latest live checkpoint: Cerberus source `c421aca33`, deployment documentation `4417447e3`.
  Normal restarts replaced JVM23164 with32456 and then36776. Custom markers at01:08:15 and
  01:15:05 EDT September10; ready at01:08:18 and01:15:07. Both doctor checks confirm the
  canonical directory and revision240. Marker Git metadata reports unavailable; the deployed
  source identity is recorded from Git and the controlled Gradle rebuild, not inferred from it.
  Current stored fingerprints match the logged-out predeployment snapshot:22 containers,
  1,152 inventory/bank rows and1 realm row. SERVER/LIVE fingerprints are unchanged.
  Logs: `cerberus-deploy-doctor-restart{1,2}-20260910.log`,
  `cerberus-deploy-persistence-restart{1,2}-20260910.log`, and
  `cerberus-audit-20260907/restart-20260910{1,2}.stdout.log` under `logs/`.
- Additional live checkpoint backup: `backup/cerberus-live-verified-20260910` at `d86baed87`,
  ZIP `logs/cerberus-live-verified-20260910.zip`, SHA256
  `9345A54A1B02464636E141AF45FEBF44E3485D5AC9C2AA5BAEB2B5D021E59EDB`.
- Current source branch: `refactor/royal-titans-boss-dsl-20260910`. First bounded step converts
  ordinary fire/ice elemental melee to BossSpec/NvPCombat and removes the manager's direct
  elemental attack method. Characterization commit `f28c0f225` precedes production edits.
  All104 module tests and3 cache tests pass; source-stage verification is recorded below.
  This Royal Titans step is NOT deployed and does not complete the paired-boss migration.
- Completed source migrations: Kraken, Vorkath, Corporeal Beast, Alchemical Hydra, Grotesque Guardians,
  Zulrah, Muspah and Cerberus. This means source/headless migration, not client or full OSRS parity certification.
- Muspah verification: `logs/muspah-native-phase-dsl-regression.log`, all67 tests pass
  (27 native,40 retained),39s/167tasks. Shared clock: `logs/boss-phase-clock-regression.log`.
  Broader verification passed: `logs/muspah-final-broad-check.log`,1m10s/508tasks, all applicable
  boss/shared tests, scoped Muspah check, app classes and architecture detector. NO-SOURCE modules
  are not counted as tested. Vorkath now has112 module tests including native audit-harness regressions.
- Muspah ordinary attacks, forced final cadence and phase deadlines use BossSpec/BossEncounter.
  InstanceScript owns native membership; configured actor/death/reveal and native KC replace local
  actor/respawn/KC ownership. Spatial cloud/spike/LOS handlers remain focused and session-scoped.
  Natural crevice/exits, all departure paths, two independent rooms and repeated kills are tested.
  No private phase clock or player/run map remains. Manager fell from1,088 to552 lines of
  specialized threshold, shield and presentation policy. OSRS-specific unresolved behavior is
  listed in DSL_MIGRATION.md; architecture completion is not full fight-parity certification.
- Live deployment: **performed locally** after the backup and passing broad check. Canonical
  JVM41396 was replaced by38976, then13012; both new starts passed doctor/marker/Server ready.
  Later diagnostic-only harness fixes were rebuilt and restarted as recorded in DSL_MIGRATION.md.
  Recheck the actual PID; do not restart an obsolete process number from this document.
- Final real-game-loop Vorkath audit: **75 assertions pass**, including both specials, all ten
  projectile profiles, native death, exits, disconnect and deferred instance logout cleanup.
  Saved report: `logs/boss-migration-vorkath-live-audit-success.log`. Current observed PID23164,
  source `7784e155e`, custom marker23:40:08/ready23:40:10. No client renderer/OSRS packet diff is
  claimed. The user has begun client testing; do not repeat the completed deployment checkpoint.
- Stored inventory/bank and realm-setting fingerprints match before and after both checkpoint
  restarts and the harness rerun.22 containers,1,147 inventory rows,1 realm row. The old JVM's
  shutdown reported closed-connection failures for pending saves; identical stored fingerprints
  do not prove any old unsaved in-memory progress was retained. See the detailed operational log.
- Cache: revision240, `.data/cache/SERVER` definitions and `.data/cache/LIVE` JS5; PostgreSQL
  authority `.data/postgres`. Do not reset, relocate or delete them.
- User authorization: when remaining Codex allowance reaches about 5%, finish the current bounded
  migration, run checks, then deploy the passing local checkpoint and produce a testing handoff.
  There is no reliable account-balance tool in this session; obtain that cutoff from the user or
  an actual supported usage display. Do not confuse context capacity with subscription allowance.
  No remote Git push, merge or PR is authorized. Do not auto-deploy merely by running the resume script.
- Updated user checkpoint: before moving to the next boss, make another recoverable source backup,
  run focused/broader verification, deploy the passing checkpoint locally and verify the controlled
  restart. Do not deploy a failing/in-progress shared phase-clock change. Record exact backup ref,
  deployed build/cache identity and two restart results here, then continue the migrations.

## Deployed changes ready for client testing

| Component | Source changes | Headless verification already completed | What to test in the client |
|---|---|---|---|
| Shared boss DSL | Delays, parallel effects, repeat gaps, injected selection RNG, cadence/forced counters, native volleys, hit scopes and cancellation | Native interpreter/combat regression suite; exact counts in detailed report | No double attacks, wrong delays or attacks after leaving; smoke-test existing DSL bosses such as Scurrius |
| Shared instances | Source-defined InstanceScript, typed session state, protected entry/exit, keyed lifecycle cleanup, native coordinated actor reset | Native lifecycle/reset/isolation tests | Entry, exit, teleport, logout/disconnect, death and re-entry; separate rooms must not affect each other |
| Kraken | Ordinary boss/tentacle combat moved to DSL; native room membership/spawns/cleanup | 12 module tests | Enter (`::kraken`/`::krak` is a convenience), awaken whirlpools, fight, loot, leave and re-enter |
| Vorkath | Normal/forced attacks and phases on DSL; native instance lifecycle; acid/spawn mechanics retained as focused handlers | 112 module tests plus cache audit; real-game-loop audit recorded separately | Natural boat entry, awakening, normal attacks, acid walk, zombified spawn, death/reclaim, loot, repeat and exit |
| Corporeal Beast | DSL ordinary attack selection; native public-room lifecycle, focused core and split-area handlers | 24 module tests | Both existing lairs, melee/magic attacks, core, stomp, group targeting, room reset and loot |
| Alchemical Hydra | DSL normal volleys/phase rotations; native instance and same-actor respawn; removed duplicate KC increment | 22 module tests | `::hydra`, observation-room doors, vents/forms/specials, prayer, loot/KC once, two kills, `::leavehydra` |
| Grotesque Guardians | Two DSL specs; native roof instance, paired forms and reset; base-Dusk drop/KC identity fixed | 21 module tests; 48 drop-module tests; all boss/shared regression tasks and app classes pass | Natural roof entrance, bell, both attacks, lightning/spheres, hammer, prison, loot/KC once, 17-tick paired reset, repeat and exit |
| Zulrah | Four DSL rotation programs and snake specs; native boat instance and same-actor respawn; scoped clouds/eggs/tail; native KC metadata | 43 module tests (21 native), revision-240 cache audit, broad boss/shared regressions | Natural boat entry/camera, all forms and rotations, clouds, eggs/snakes, tail dodge, prayer, death/reclaim, loot/KC once, two kills, exit/teleport/logout; debug mode must award nothing |
| Muspah | DSL normal attacks, timed phases and final cadence; native solo instance, same-actor repeat and KC; scoped cloud/spike effects | 67 module tests, including27 native gameplay tests | `::muspah` then natural crevice, both opening forms, form changes, lightning, homing, shield/Smite, final spikes, two kills/KC once, exits/teleport/logout/death; separate rooms isolated |
| Cerberus | DSL ordinary attacks/triple/rotation and native phase deadlines; InstanceScript membership and same-actor respawn; focused Souls/lava/firewall handler | 75 module tests,2 cache tests, broad boss/shared verification, two controlled boots and matching persistence/cache fingerprints | `::cerberus`, wake/opening triple, normal styles, Souls/prayers, lava, firewall/reset, loot/KC, repeated kills, exit/teleport/logout; forced debug kills are reward-ineligible |
| Royal Titans | Elemental melee and per-Titan platform/arena/teleport phases on BossSpec; native InstanceScript membership, paired reset and corpse deadlines; focused geometry handlers | 129 module tests,3 cache audit cases, broader505-task check, two controlled boots and matching fresh persistence/cache fingerprints | Natural solo entry, duo/late join, different phase-one positions, walls/autocast, elemental routes, both landings, Blast and either kill order; loot/pages/sacrifice, scoreboard, repeated kills, exit/teleport/logout/death; record any visual or timing change |
| Architecture enforcement | Existing detector/self-tests now run from root Gradle check; explicit reviewed waivers retained | `bossArchitectureCheck` passes | Developer check, not a visible game change |

Guardians, Zulrah and Muspah KC overlays are now packed into the selected SERVER NPC definitions
using the existing parameter installer. Installed-cache audits pass5 tests. The cold SERVER backup
is `logs/boss-dsl-predeploy-20260909-server-cache/`; original selected definitions also have encoded
backups. LIVE was unchanged. Preserve unrelated source-data hunks in `npcs.toml`.

For each fight record: account, solo/group, entry path, observed problem, approximate tick/time and
a clip/RSProx capture when possible. Test both normal completion and departure during a delayed
attack. Check inventory/bank/reclaim/KC after reconnect and two controlled restarts. Passing unit
tests and a boot marker do not certify visuals or full OSRS parity.

## Work still required

Current source step: Cerberus migration and handler extraction are implemented (manager1,290 ->
598 lines, arena handler426). All75 module tests and2 cache-audit tests pass. Broad boss/shared
tests, app classes and architecture enforcement pass in `cerberus-final-broad-check.log`
(1m38s/505tasks). Final predeployment review then found and corrected a fixture frame-order
mistake: world deadlines precede MapClock.tick/LateCycle. Three timing tests fail before the
correction; all75 pass afterward in `cerberus-native-frame-order-green.log`. See the detailed
mapping and subsequent final verification in DSL_MIGRATION.md.

Corrected final broad check: `logs/cerberus-corrected-final-broad-check.log`,28s/509tasks,
successful. This includes affected shared suites and all applicable boss tests (up-to-date
where unchanged), scoped Cerberus check, app compilation and architecture enforcement.

Cerberus backup and local deployment checkpoint are complete. Continue Royal Titans on its own
branch; the elemental DSL source step has begun, but no Royal Titans source change is part of this deployment.
The pre-correction backup `c96d06528` is recoverable but was NOT deployed. No cache or PostgreSQL
authority was changed. Native startup verification is not a rendered fight or packet comparison.

Standing deployment permission (September10): the user explicitly authorizes logging their account
out for tested, backed-up deployments. Use native logout/save and verify persistence before normal
shutdown; do not wait for a manual logout or repeatedly request it. Do not hard-kill the JVM or ignore
save failures. This permission is also recorded in AGENTS.md. Both restarts above had zero connected
game clients, following the user's voluntary logout.

Cerberus client test plan after deployment: `::cerberus`, approach/wake, opening triple and all
ordinary styles, Souls/prayer switching, lava movement, firewall crossing/reset, death/repeat,
normal exit, external teleport and logout. `::cerbdebug` can force mechanics but makes the kill
reward-ineligible; test actual loot/KC with a fresh natural kill. Natural public entrance remains
an inherited access gap, not a newly invented location. Capture and visual parity remain open.

Royal Titans source-stage result: `royal-titans-elemental-dsl-verified-20260910.log` passes all104
module tests, scoped check, shared boss tests and3 revision-240 cache tests (53s/204tasks; cache/API
tasks were up-to-date). Seven added elemental tests execute production registration, accuracy,
max hit, launch prayer, cadence, custom-death lifetime, native deletion and separate-room cleanup.
One multi-room fixture initially unlocked summons after its patrols had diverged; synchronizing
only test setup fixed the assumption without changing production timing. InstanceScript conversion,
private membership-map removal, paired Titan phases/cadence and specialized handler extraction
are NEXT, not completed. Preserve existing rewards/statistics and explicitly documented mechanical
uncertainties. Do not repeat or revert the characterized elemental conversion.
Broader verification: `royal-titans-elemental-broad-check-20260910.log` passes in30s/505tasks,
including applicable boss/shared suites, app compilation and architecture enforcement. Passing
source checkpoint branch: `backup/royal-titans-elemental-dsl-20260910`. It is not a live deployment.
Commit: `632ae1a929e7410a2cbb1c675af739f1771a02dc`. Source-only archive:
`logs/royal-titans-elemental-dsl-20260910.zip`, SHA256
`259545D6B09009AACB74EBF6D1AC3AF67A90147A80397B12AD29C03B77F1AE99`.
The archive contains 7,441 entries; excluded-path validation found zero violations. It excludes
runtime data, credentials, PostgreSQL, caches, logs and generated build output.

Next source checkpoint: Royal Titans native InstanceScript/session migration is implemented.
The three private manager indexes and manual enter/join/leave/logout/teleport flows are removed.
Source tests now enter through the actual protected World script. All116 module tests pass,
with native refusal, deferred logout, disconnect, duo/death/teleport, separate-room cleanup,
cross-room claim denial and SQL retry coverage. Focused check/cache/shared suites pass in
`royal-titans-native-lifecycle-verified-20260910.log`; broader boss/shared/app/architecture check
passes in `royal-titans-native-lifecycle-broad-20260910.log` (46s/505tasks).
This checkpoint is source-only, not live. Continue paired Titan combat phases/cadence and
specialized handler extraction; do not repeat the completed elemental/session work.
Backup: `backup/royal-titans-native-lifecycle-20260910` at `779fad05e`.
ZIP `logs/royal-titans-native-lifecycle-20260910.zip`, SHA256
`4C53022AEB9887F50BEAD7445BFA47F9CE8227CFB0B93F090880896FFCBB448E`.
Source-only archive validation: 7,442 entries, zero excluded-path violations.

Next source checkpoint: per-Titan arena deadlines are now declared in RoyalTitansTitanSpec and
owned by BossPhaseClock. No private modulo arena selector remains. The shared clock now supports
replacing its captured target without resetting the phase deadline;50 shared runtime tests pass,
including the two reproduced target-continuity failures. Royal Titans121 tests, scoped check,
cache/shared checks and broader505-task verification pass. The exact due-frame duo departure
regression required native keyed-leave rebinding, not a LateCycle-only fallback. Both corpse
orders retire their own native clocks. Live STILL remains the Cerberus checkpoint. Continue
platform-phase wiring and focused handler extraction; do not call Royal Titans complete yet.
Passing arena backup: `backup/royal-titans-arena-dsl-20260910` at `bf05c2def`, ZIP
`logs/royal-titans-arena-dsl-20260910.zip`, SHA256
`2707C222E490231E4FD35E9E4E1420B456722FB127D7BA7A33A0CA8D33E8DC38`.
Archive:7,445 entries, zero excluded-path violations; no runtime data or generated output.

Platform step also passes: native per-Titan phases own spawn/turn/facing, edge cooldowns and
the close-action sequence. Route arrival now advances that clock from the native completion
callback. No private movement readyCycle, phase field, action counter or run movement index.
RoyalTitansPhaseProgram replaces the arena-only adapter. Native actor deletion clears the pair
reference so it cannot recreate an orphan encounter.123 module tests, scoped/cache/shared
checks and broader505-task check pass (logs `royal-titans-platform-dsl-verified-20260910.log`
and `royal-titans-platform-dsl-broad-20260910.log`). Source-only; continue handler extraction
and remaining paired lifecycle timing. Do not restart or re-migrate Cerberus unnecessarily.
Platform backup: `backup/royal-titans-platform-dsl-20260910` at `e3c799e90`; ZIP
`logs/royal-titans-platform-dsl-20260910.zip`, SHA256
`67189BF867D4F648436EEE4DC1C571AA7C840CE51C26F362F7A616EE6C916243`.
Validated7,445 entries, zero excluded paths. Live unchanged.

Focused handler extraction now passes123 module tests and broader505-task checks; source-only.
Coordinator1,913 ->864 lines. Run/Definitions/Arena/Walls/Elementals/TileAttacks/Platforms/Landings
are the focused native-API components, not a replacement combat or membership engine. Timing and
reward policies remain unchanged. Logs `royal-titans-handlers-{verified,broad}-20260910.log`.
Continue paired transition/reset deadline ownership; no reason to pause at this checkpoint.
Handler backup: `backup/royal-titans-handlers-20260910` at `8b4ec3f55`, ZIP
`logs/royal-titans-handlers-20260910.zip`, SHA256
`DA091675D090295A71968BC0D2EFBAD87211777E5279FD9A09C2E169B42F2880`.
Validated7,453 entries and zero excluded-path violations; live still unchanged.
Paired teleport/reset work also passes:127 module tests,3 cache cases and broader505-task
check. Per-actor teleport phases and native session-owned reinvigoration replace both elapsed
timer polls. Source backup `backup/royal-titans-paired-lifecycle-20260910` at `6e96a1060`,
ZIP `logs/royal-titans-paired-lifecycle-20260910.zip`, SHA256
`EFDC34DE929CB7D56A683ADBEEDBB428B293E1549BD41AF9D554F1BC87C16718` (7,456 entries,0violations).
Native ownership of the remaining elemental corpse-removal deadlines is next. Live unchanged.

The elemental corpse-deadline step is now implemented:129 tests pass, with exact fire/ice death
frames and early native-deletion cancellation. Scoped verification50s/206tasks; broader check
23s/505tasks. Logs `royal-titans-elemental-death-{native-verified,broad}-20260910.log`.
Prepare the source-only backup, normal local rebuild/two restarts and persistence/doctor checks;
then continue Nex on a focused branch. Do not pause merely for a routine checkpoint or connected
user: use the authorized native logout/save workflow when needed, never an unsaved hard kill.

Royal Titans deployment is now complete for source `1279d1676`. Commands:
`cerberus-controlled-restart.ps1 -ServerProcessId 36776 -RestartNumber 202609103`, then the same
existing helper with `-ServerProcessId 25860 -RestartNumber 202609104`.
Both were normal shutdown requests, not force kills.
Markers03:40:27/03:42:31 EDT; ready03:40:30/03:42:33. JVM39612 is the last observed live process.
The marker still reports unavailable Git metadata; Git plus controlled Gradle rebuild records
source identity. No client was connected at either shutdown. First shutdown logged a ClanChat
database request failure and closed-connection shutdown errors; second logged a database-close
error. Account-saving service reported successful shutdown both times. This is NOT a clean
service-shutdown certification or a claim about unobserved in-memory data.

Fresh before/after fingerprints match across both restarts:22 inventory containers,
1,152 inventory/bank item rows,1 realm setting row. Item SHA256
`0ac5d6ef4bba6688c0fcf53ca1d923ef5f809cd20d67490c8b11815f88301280`;
container `85989c0b4aa1079086bfb308ab78c6161120f950dcd3ea76c152b298635d4991`;
realm `c971b1f6d8e431e9f25d64c6dc154d7c60ff22ce7c9e20274a73f671e911ef50`.
This fresh snapshot deliberately supersedes earlier inventory hashes after user activity.
Doctor logs `royal-titans-deploy-doctor-restart{1,2}-20260910.log`; persistence logs
`royal-titans-deploy-persistence-{before,restart1,restart2}-20260910.log`; boot logs
`cerberus-audit-20260907/restart-20260910{3,4}.stdout.log`, all under `logs/`.
SERVER/LIVE revision240 paths and hashes remain unchanged. No Royal Titans rendered-client
acceptance or OpenRune-versus-OSRS packet comparison was completed by this migration checkpoint.

1. Collect user client-test feedback. Backup, local deployment, two gameplay checkpoint restarts,
   persistent fingerprints and the corrected75-assertion live Vorkath audit are complete. Do not
   repeat them merely because an older section or message says they are pending. Visual/full OSRS
   acceptance remains separate.
2. Resume Nex after the native minion migration recorded at the top of this handoff.
   Do not repeat the completed guard/Reaver DSL conversion or its departure/deletion tests.
   Main Nex phase/rotation and public-room ownership are next; retain the evidence-backed
   mapping and separate mechanical corrections from architecture preservation. Legacy20/18
   damage and811 animation are still characterized draft values, not OSRS-correct presentation.
   Continue Nex, then Yama, Doom and DT2 in separate focused branches/commits. Review
   Dagannoth Kings separately. Retain Royal Titans client acceptance and documented outstanding
   mechanical/reward evidence as separate work; do not repeat its completed architecture steps.
3. Resolve or explicitly report remaining generic DSL concerns (unused condition/phase fields,
   ignored selection hints and any proven timing/lifecycle gap); do not silently rebalance content.
4. Run focused tests per change, all applicable boss/shared regressions and broader checks.
5. At the authorized cutoff, update this table with exact deployed commit/build marker, cache hashes,
   restart logs, test results, remaining work and player test instructions. Never deploy a failing
   or partially wired migration to meet a quota deadline.

## Resume instructions for the next agent

Continue the existing all-boss architecture migration; do not start a second implementation.
Read AGENTS.md, applicable nested instructions, this handoff and DSL_MIGRATION.md completely.
Inspect branch/remotes/status/untracked/ignored files and complete relevant diffs. Run the doctor.
Inspect recent commits and current test logs before changing anything; do not assume this snapshot
is newer than the worktree. Preserve baseline and focused branch history, all unrelated dirty
`.data` changes, caches, PostgreSQL, captures and evidence. Do not restore whole files or mass format.
Use upstream/main as the architecture reference, distinguish local adapters from official APIs,
and record mappings before any additional shared capability. Characterize behavior before replacing
its wiring. Continue implementation and verification rather than producing another audit-only pass.

The resume helper prints these instructions with current Git identity:

```powershell
.\scripts\resume-boss-migration.ps1
```

Optional `-CopyPrompt` copies the prompt to the clipboard. Default boss is now `nex`; optional
`-Verify -Boss nex` runs the
doctor, architecture detector and focused boss/API/instance tests first. It does not modify source,
switch branches, commit, deploy, restart or contact GitHub. Build/test outputs are the usual local
generated files. Paste its output into a new session in the canonical workspace after allowance resets.
