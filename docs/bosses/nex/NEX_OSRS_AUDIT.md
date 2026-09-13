# Nex revision-240 evidence ledger and approval mapping

## Current deployed checkpoint: September13, 04:40 EDT

JVM23812 is ONLINE on game43594/HTTP8080/WorldLink9091, canonical source checkpoint `c4fc9390f`.
This includes Nex protected-access source `444a39eb3`, native save/empty-world fixes, permanent
Central dependency `07b00480e` and game-side client/activity shutdown `0f729d055`.
Both required ordinary controlled restarts completed: JVM20364 ->25096 ->23812, with fresh native
receipts `39ab6a15-7209-49fd-989c-1fab320f1042` and `823c8563-4eac-4a2e-b763-66f890d4d654`.
Each old process exited naturally and each replacement reached ready with all three listeners.
Native preflight reported committed0/players0/pending0 for these empty-world restart checks.
This does not claim a new client login/combat/visual acceptance test.

The preceding legacy JVM1856 handover completed natively at04:33:18, after committed5877,
players0/pending0. Fresh receipt `7f7aca82-7aad-453f-9577-3e314f048609` preceded normal exit.
No hard kill, failed-save discard, failure-state reset or System.exit was used. Before replacement
boot, `.data/postgres` was copied offline to `postgres-before-central-deploy` in the recovery dir;
all1,243 files were verified byte-for-byte. The original pre-crash-recovery backup remains intact.

Evidence directory: `logs/crash-recovery-20260913-025956/`. Both `doctor-central-s10-restart*.log`
complete with canonical user.dir, unchanged SERVER/LIVE dat2 hashes and revision240. Both
`persistence-central-s10-restart*.log` match the before-handover23 containers,1,153 inventory/bank
rows and1 realm fingerprint. Final classpath audit verifies one patched Central jar from the pinned
checkout, no old Maven Central jar, and canonical app/net/Nex paths. The custom build marker is
present; its legacy Git branch/commit fields say unavailable, so it is not used alone as artifact
identity proof. Startup/checkpoint/classpath evidence establishes the deployed source selection.
Startup logs for the two ordinary restarts are under `logs/cerberus-audit-20260907/` with numbers
202609131 and202609132. The original historical10576 failed restart remains recorded as failed.

Complete source snapshot `logs/central-s10-ready-source-20260913.zip`:4,393 verified source files,
zero excluded paths; SHA256 `0557EAEED5ADEA6E858BB8799E9D390DFDFEF7B1131B62BBC9EA4D047B3D3028`.
Backup branch `backup/central-s10-ready-20260913` points to `c4fc9390f`. This archive excludes
PostgreSQL/caches/credentials/generated output and is separate from the verified offline DB backup.
Build and behavioral verification passed before deployment:31 Central focused tests,12 new native
client/writer tests,1,061 boss cases and297 shared/app cases, scoped checks/assemble/architecture.

Current source: M21 prison ownership/cancellation is committed as `706e11330`; M26 completed-kill
occupant handoff as `13719ba1c`; bounded M24 Wrath death-position snapshot as `30ef8129c`.
M14/M27 Shadow Smash marker ownership is committed as `88d0abf0c`: placement success controls ownership,
natural expiry releases it, and source cleanup preserves later replacements. All127 Nex cases,
scoped checks, app assemble and architecture checks pass;167 unchanged shared results remain valid.
These four increments are SOURCE ONLY. Fixed-public sessions49f73a317 and the prepared owned-spawn
entrypoint are now verified:20 native groups,54 total instance cases,1,090 boss cases and322 shared/
app cases pass, with scoped formatting/checks, architecture and app assemble. Both source archives
are byte-verified; details and native failure limits are below. Neither is deployed. Nex public-room
adoption is now source-verified: one native Fixed session owns admission, five prepared actors and
dynamic Reavers; completed rounds retain that session, with exact identity and callback-safe cleanup.
All139 Nex cases,1,102 boss cases and322 shared/app cases pass, with scoped checks, architecture and
app assemble. Public-session commit219fa193a is backed up. Bounded S08 atomic defence-drain minimum
is also source-verified: Nex250 is configured before native add and the post-tick repair is removed.
All142 Nex cases,1,105 boss cases and484 engine/shared/weapons/app cases pass, with scoped checks,
architecture/app assemble and a byte-verified ten-file archive. Boosted-floor mechanics stay open.
S08 commit1ab48e36d is backed up. M18 captured Blood Sacrifice marking and native owned-tint/late-
queue cleanup now pass148 Nex cases,1,111 boss cases,494 engine/shared/weapons/app cases and408
existing ToA regression cases, scoped checks, architecture and app assemble. Its ten-file archive
is byte-verified below. Bounded M06 opening animation9203->captured9182 now passes148 Nex cases,
scoped checks, architecture and app assemble; its two-file archive is byte-verified. Captured opening
facing512/sound5303/overhead speech now pass150 Nex cases, scoped checks, architecture/app assemble;
its three-file archive is byte-verified. Bounded M11 native bodyguard drops and exact no-remains
handling now pass153 Nex/1,116 boss cases,501 engine/shared/weapons/app,62 drop-content and408
existing ToA cases (2,087 total), scoped checks/architecture/app assemble; nine-file archive verified.
Next: coordinate-only guard placement with retained capture anchors and canonical native map checks.
M04 cooldown/equipment, M20 general placement/clipping and normal timing evidence remain missing. All these
changes remain SOURCE ONLY; current deployment is unchanged.
Existing timing/damage/geometry constants remain characterized under E07. Full native public/private
sessions, other mechanics/rewards/evidence gaps below remain open; Nex is not complete.
Existing order remains Nex ->Yama ->Doom ->DT2 ->separate Dagannoth Kings review ->ToA ->ToB ->CoX.

## Completed source increment: M21 prison ownership and cancellation

The following mapping was recorded before implementation.

No shared API change is required. Pinned upstream `61139e76a` CampfireEvents uses clicked
BoundLocInfo, exact lookup and native loc deletion; ShootingStarManager retains its owned locs.
Current LocRepository already provides Boolean add/del, exact lookup and timed despawn callbacks,
including established local timer-replacement fixes. LocInfo/BoundLocInfo are values, so content
must retain its own cast/segment identity before a protected arrival delay; no allocation-ID API
exists in the inspected local/pinned source. This is mechanic state within the existing Run.

| Responsibility | Existing route and bounded correction | Observable acceptance |
|---|---|---|
| Clicked icicle | Registered NexWorld Op1 -> LocInteractions -> ProtectedAccess; capture segment/cast/player identity before arriveDelay, validate the same owner afterward | Clicking a higher-UID target's42944 releases that target; stale delayed clicks cannot select a same-value replacement |
| Native loc ownership | LocRepository.add(LocInfo,5) Boolean success, findExact, del(selected,Int.MAX_VALUE), existing timed onDespawn | Only a successful native deletion rescues; exactly one selected segment/collision is removed, remaining segments retain native expiry |
| Damage expiry | Existing BossEncounter.schedule(5) and native queued hit(+1); require the captured unresolved cast, invalidate it on rescue/recast/departure; preserve exact target session at impact | Rescue cannot fall through absent-map default0; untouched cast emits once; old casts/UID reuse/retired rooms cannot hit replacements |
| Cleanup | Existing encounter onCleanup and player departure, identity-checked segment ownership plus native repository timer cancellation | Old timers/cleanup cannot remove newer segments; natural segment expiry retires ownership without implying rescue |

Retained C-GM808 t1941:L49885-93/t1945:L49928-34 and t1614:L41421/t1615:L41451/
t1619:L41525-30 show selected-only deletion and later ring expiry;806 t364:L10126-31 rejects
self-rescue. Current240 cache42944 has Attack;42943 has no Op1. Exercise actual42944 dispatch,
and do not claim the draft mixed geometry represents OSRS. Keep five-tick loc/callback durations,
one-tick queued hit, damage0..75, existing prayer/CA policy and geometry unchanged. Weapon restrictions,
rescue success probability and full E07 mechanics remain missing evidence, not guessed checks.
Tests will use a real LocRepository/registry/collision and EntityLifecycleProcess at post-tick,
following the established Royal Titans fixture. The existing mocked Nex loc fixture alone is
insufficient. Cover two targets, self/outsider/unowned clicks, native deletion failure, untouched
expiry, recasts/overlaps, delay-time replacements, departure/UID reuse and room cleanup.
Production owner: primary; a bounded test owner may edit only the Nex fixture/new M21 tests.

M21 verification: `logs/nex-m21-prison-red-20260913.log` compiled and ran eight
behavioral groups, five failing against the old implementation. After the mapped content changes,
all eight pass (`logs/nex-m21-prison-green-20260913.log`,19s). All12 expanded groups pass in
`logs/nex-m21-prison-boundaries-20260913.log` (19s), including native add/delete rejection,
recast after emission, exact expiry-cycle rescue refusal and phase handoff before/after emission.
Two independent reviews found no blocking production defect. Native timer callbacks only retire
content ownership; manual deletion and same-tile/layer replacement explicitly retire superseded
segments. The cast remains owned until accepted native impact, so a recast cancels already queued
old damage. Unsupported42943 Op1 registration is removed; draft mixed spawn geometry is retained.
Identical external LocInfo replacement remains indistinguishable at native allocation level;
content-owned replacements are protected by captured cast/segment identity. No shared API changed.

`logs/nex-m21-shared-checks-20260913.log` passes in9s/177 tasks (2 executed). Valid unchanged
shared results were reused: player20, NPC18, instances34, bosses61, game-process16, death6 and
registry12 (167 total; zero failures/errors/skips). Architecture detector/self-tests pass.
`logs/nex-m21-final-checks2-20260913.log` passes Nex check/scoped formatting/app assemble in1m,
470 tasks (15 executed): all110 Nex cases, zero failures/errors/skips. The first final-check
invocation failed before task execution because PowerShell split an unquoted Gradle property;
the corrected invocation quotes that property. This was command parsing, not a source/test defect.
Changed source files: NexEncounterManager.kt, NexWorld.kt, NexMinionCombatCharacterizationTest.kt
and new NexIcePrisonIntegrationTest.kt. Focused source archive
`logs/nex-m21-prison-source-20260913.zip` contains these four entries, verified byte-for-byte;
SHA256 `79A9F830AEC0F97074E8866B6C24E784D7EB795BE4D18C3D86FDD55826382A1F`.
The existing complete source snapshot and offline PostgreSQL backup remain intact. Scoped diff
whitespace check passes. This increment is source-only; JVM23812 continues the deployed checkpoint
above. Next exact deliverable is the approved M26 completed-kill occupant handoff below.
M21 commit: `706e11330`; backup branch `backup/nex-ice-prison-20260913`.

## Completed source increment: M26 completed-kill occupant handoff

The mapping below was recorded before edits; the implementation and focused tests now pass.

Retained capture3284 t549:L13987-14017 ->t587:L14100-07 ->t790 shows consecutive rounds without
re-entry. This is Grid Master private-solo evidence for continuity, not public/private parity or
normal respawn timing. Keep the characterized50-cycle completion reset and five-cycle opening.
The current native-death test explicitly leaves for the bank and calls enter between rounds,
masking the membership loss in reset ->cleanup ->spawnFresh.

Existing upstream A06/A07/A10 and the inspected GeneralGraardor native respawn/bodyguard workflow
establish native actor lifecycle. Current InstanceManager.resetNpcs retains membership for an
allocated InstanceSession, but this fixed chamber has no such session; do not create a second
instance API or fake a session. Reuse the already approved M26 EndCycle/MapClock room driver,
PlayerUid.resolve, NPC/loc repositories and S03 EncounterRegistry cleanup. Full fixed/public/
private InstanceScript conversion remains separate.

| Responsibility | Existing route and bounded change | Observable acceptance |
|---|---|---|
| Completed round only | Native final death/reward completion ->existing RESETTING deadline ->reset | Abandonment/source-loss resets remain idle; no new timer or copied capture duration |
| Surviving occupants | Snapshot exact PlayerUid and Player before cleanup, revalidate membership/session/room eligibility after native cleanup and spawn callbacks | Eligible survivors stay at unchanged coordinates; departed/dead/disconnected/reused slots are excluded |
| Fresh round | Existing repositories create one Run/encounter and five actors; enrol valid survivors with fresh per-kill state, beginSpawn once | Two native kills without re-entry; no duplicate actors/rewards, old contribution/achievement/prison state not inherited |
| Old work | Existing encounter-owned cleanup, native hit validity and loc timers | Old queued hits/callbacks cannot acquire the replacement round; existing empty-room regressions stay green |

M01-M36/S01-S08 approval already includes M26. No new shared capability is required or proposed.
Production integration remains owned by the primary. Implement after completing M21 verification;
bounded M26 regression preparation may run independently in its own test files.

M26 pre-edit review refinement: rewardFinished means the delayed finalizer was scheduled, not
successful reward completion. Record completed-round eligibility only after the reward callback
returns, and recheck the captured room/source before transitioning because reward hooks may retire
it. Native NPC deletion callbacks can replace a player at the same packed UID; cleanup must retain
its original Player objects before those callbacks, so clearing loot duration cannot mutate the
replacement. Existing UID/Player identity checks suffice; no new shared route is needed.

M26 implementation keeps valid surviving occupants at their unchanged coordinates, creates fresh
per-kill Run/encounter state, and begins the existing opening once after a completed-round deadline.
Explicit logout, death, disconnect, slot/Player replacement and departure in cleanup/spawn callbacks
prevent carryover. Empty/source-loss resets stay idle. Cleanup clears loot settings only on original
captured players. A reward callback that retires Nex cannot mark the old round completed. Native
cleanup/reward exceptions propagate; a new encounter is not spawned after failed cleanup.

RED history: the first invocation caught a test-only read-only hitpoints assignment, corrected to
the native statMap setter. RED2 exposed a test reward-observation name mismatch from Kotlin's
CoordGrid method mangling as well as the real membership gap. After correcting that observation,
`logs/nex-m26-round-handoff-red3-20260913.log` runs6 groups:5 membership failures and the existing
empty/source-loss behavior passing. No production changes preceded that red run.
The first implementation passes6 groups plus NexMainNativeCombatTest in19s
(`logs/nex-m26-round-handoff-green-20260913.log`). Expanded8 groups plus NexMain pass in19s
(`logs/nex-m26-round-handoff-boundaries-20260913.log`), including post-reward source loss,
source deletion inside the reward callback, and same-UID replacement loot-state preservation.
These tests exercise native final death, queues, lifecycle and actor ownership; rewards are
observed at the existing mocked service boundary, not certified as durable multi-recipient drops.
`logs/nex-m26-final-checks-20260913.log` passes in57s/472 tasks (17 executed): all118 Nex cases,
zero failures/errors/skips, scoped formatting/Nex check/app assemble and architecture/self-tests.
Unchanged shared167-case results from the preceding M21 check remain valid; no shared source was
edited. Final production review found no blocking defect. Changed source: NexEncounterManager.kt,
NexMainNativeCombatTest.kt and new NexRoundHandoffIntegrationTest.kt. Archive
`logs/nex-m26-round-source-20260913.zip` contains those three entries, verified byte-for-byte;
SHA256 `244A71788201C55E10DBF9EDDA4CB0D16AA95D5506010DB2F3C22AB16D6C9F9F`.
Scoped whitespace check passes. Source-only, not deployed. Next exact step: M24 death-position
snapshot RED regression, then the mapped coordinate-only correction below.
M26 commit: `13719ba1c`; backup branch `backup/nex-round-handoff-20260913`.

## Completed source increment: bounded M24 Wrath death-position snapshot

The following mapping was recorded before implementation.

Retained `logs/nex-audit-20260909/captures/rsprox-808.txt` records death anchor(2920,5191)
atL19223, Wrath effects centered(2921,5192) atL19257-72, and the nearby player(2918,5191)
receiving41 damage before the same corpse deletion atL19309-13. Capture3284 L13922/L13960-80
independently records death anchor(2916,5191) and effects centered(2917,5192). The current fixed
spawn(2924,5202) five-tile selection cannot reach that observed damaged player.

Pinned official61139e76a NpcDeath.deathWithDrops captures its default CoordGrid before awaiting
native death; EffectInterpreter.applyTileAoE resolves a center before native hit selection. These
existing coordinate/native hit workflows suffice. Reuse the approved local S03 encounter-owned
schedule and roomHitValidity; no shared API addition is needed.

| Responsibility | Existing route and bounded change | Acceptance |
|---|---|---|
| Death origin | Accepted ZAROS handleDeath/deathStarted guard captures immutable CoordGrid once | Duplicate death handling and later corpse-coordinate changes cannot move the origin |
| Native delivery | Existing handleDeath ->deathNoDrops ->finishDeath ->schedule(3) ->queueHit(+1) | Hidden original Nex remains the attributable source through accepted impact |
| Position-relative selection | Existing Wrath emission-time participants and legacy radius5, translated from fixed spawn to captured death anchor | Native off-spawn death reaches nearby eligible target and excludes target only near old spawn |
| Lifetime | Existing encounter/participant/UID validity and round cleanup | Departure, replacement sessions/source/round cannot inherit old Wrath |

This is a coordinate-only correction. The entire damage boundary and its relation to visual
center offset(1,1) are unresolved; preserve the characterized anchor-relative radius instead of
guessing a new one. Keep damage0..55, prayer policy, delay3+1 and emission-time participant sampling.
E07 timing/radius/visuals and documented50-versus-draft55 damage conflict stay open. Reward location
remains current.arena.spawn under separate M29 evidence; do not change it in this slice. Implement
after the completed M26 checkpoint; independently prepared tests must remain outside source until then.

M24 result: the accepted native death captures CoordGrid once, and finishDeath retains that value
in its existing encounter-owned callback. Only Wrath's distance origin changed; reward tile,
radius, damage/prayer, timing, emission-time selection and native identity/lifetime are preserved.
`logs/nex-m24-wrath-origin-red-20260913.log` compiled and ran4 groups, all failing the old fixed-
spawn targeting. `logs/nex-m24-wrath-origin-green-20260913.log` passes all4 plus native combat/
round-handoff regressions in20s. Independent production review found no blocking defect.
`logs/nex-m24-final-checks-20260913.log` passes in58s/472 tasks (15 executed): all122 Nex cases,
zero failures/errors/skips, scoped formatting/Nex check/app assemble and architecture/self-tests.
Unchanged shared167-case results remain valid; no shared source was edited.

Changed source: NexEncounterManager.kt and new NexWrathOriginIntegrationTest.kt. Archive
`logs/nex-m24-wrath-source-20260913.zip` contains both files, verified byte-for-byte;
SHA256 `311B76A7F43590AEF50394BE5759228D8C084C7C372056921A9FC169B7A1379F`.
Scoped diff whitespace check passes. Source-only; JVM23812 remains online on deployedc4fc9390f.
Next exact step: complete the bounded M14 Shadow Smash native loc-ownership mapping and regression
review, reusing the already verified repository/timer pipeline; no M14 edits have begun.
M24 commit: `30ef8129c`; backup branch `backup/nex-wrath-origin-20260913`.

## Current source increment: bounded M14/M27 Shadow Smash marker ownership

The mapping below was recorded before implementation.

Retained `rsprox-808.txt` L5128-30 records animation9186 and separate42942 markers at both
players' captured tiles; L5611-12 records their native removal. This supports temporary target-tile
locs, not normal duration/damage proof. Keep the characterized three-cycle loc/callback duration,
one-cycle hit queue, damage0..50, prayer, target-tile snapshot, validity and achievement policy.

Shadow Smash is the sole remaining producer of temporaryLocs and caller of spawnLoc. The current
type overload returns LocInfo even on failed placement; the accumulating list also retains values
after natural expiry. Later room cleanup can therefore delete an identical replacement, while
immediate encounter/source teardown currently only clears prison locs.

Reuse already inspected official CampfireEvents clicked/timed loc workflow and current
LocRepository Boolean add(LocInfo,duration,onDespawn), findExact and del, plus approved local S03
encounter.onCleanup. No shared API is missing. A current-owned Set<LocInfo> suffices because native
same-tile/layer replacement cancels the old timer and no Smash interaction retains a delayed click.

| Responsibility | Existing route and correction | Acceptance |
|---|---|---|
| Placement ownership | Construct LocInfo; add to the owned set only after native Boolean add succeeds | Failed placement cannot acquire or later delete a pre-existing identical loc |
| Natural expiry | Native timed onDespawn removes content ownership only | After expiry, retiring the room preserves a subsequently added identical loc |
| Immediate source cleanup | Existing encounter.onCleanup clears still-owned Smash locs as well as prisons | Source deletion removes live markers immediately; later room cleanup/timers cannot delete replacements |
| Safe native deletion | Compare complete findExact result before del, clear owned state on cleanup | Different current locs survive old cleanup; repeated same-tile casts use the latest native timer |
| Existing combat | Keep marker placement and hit scheduling independent | Native marker/impact timing and all existing Shadow special regressions remain unchanged |

Do not call repository mutation from its timed-despawn callback. Arbitrary external identical
replacement before observed retirement remains indistinguishable at native allocation level,
as recorded for M21; a local token would not repair that native value-identity limitation.
Use the existing nativeLocs fixture/rejection controls/EntityLifecycleProcess; no fixture expansion
is needed. Production integration stays with the primary; a bounded new test file may be delegated.

M14 result: replaced the accumulating marker list with current native-owned values, used Boolean
placement success, released ownership from natural expiry, and added separate encounter cleanup
alongside prisons. Exact native deletion and idempotent ownership cleanup preserve replacements.
`logs/nex-m14-shadow-marker-red-20260913.log` compiled5 groups:3 ownership failures,2 native
baseline passes. `logs/nex-m14-shadow-marker-green-20260913.log` passes all5 plus existing special
and prison regressions in21s. Independent production review found no blocking defect.
`logs/nex-m14-final-checks-20260913.log` passes in58s/472 tasks (15 executed): all127 Nex cases,
zero failures/errors/skips, scoped formatting/Nex check/app assemble and architecture/self-tests.
Unchanged shared167-case results remain valid; no shared source was edited.
Changed source: NexEncounterManager.kt and new NexShadowMarkerIntegrationTest.kt. Archive
`logs/nex-m14-shadow-source-20260913.zip` contains both files, verified byte-for-byte;
SHA256 `2588966D7D526E576607BCB3EA2215CE3AA64251DBA8FA84E7FD992AE37B792D`.
Scoped diff whitespace check passes. Source-only; deployed JVM23812 is unchanged.

## Completed source foundation: fixed public instance sessions

The mapping below was recorded before implementation. The foundation passes15 focused native
behavioral groups and the final shared/all-boss/application checks. Nex adoption is still separate,
and this shared source has not been deployed.

Relevant full local/pinned61139e76a searches confirm no suitable existing route. createServerOwned
always resolves a template, calls RegionRepository.add, registers/protects allocated regions,
then joins through Region.normal coordinate translation. InstanceManager join/localCoord/resetNpcs/
departure/reconciliation require its private region map. Public InstanceSession construction,
finalizeEntry and attachNpc do not register authoritative session ownership. Official Scurrius,
Graardor and KBD public rooms use copied regions; local fixed Scurrius uses telejump without a session.
Do not fake a Region, manually fabricate a session, or add a second room manager.

The user's latest approval for new API/DSL routes only when absent applies to this verified gap.
This is a newly mapped shared instance extension under M01/M26/S03/A06, not the existing S08
stat-drain-floor approval. The following design and native acceptance were established before
shared edits; activation in Nex is a later increment.

- Explicit fixed public area/placement: canonical level-aware bounds/entry/exit and existing NPC
  spawn descriptions. Reject private creation and overlapping live fixed ownership.
- Existing createServerOwned/session indexes own the room without region allocation/build/protect
  or fabricated region IDs. Coordinate resolution/reset/teardown become placement-aware.
- Existing finalizeEntry follows tested physical movement. Fixed joining must preserve already
  attached actors rather than implicitly respawning them whenever occupancy was empty.
- Preserve current fixed-public logout/save/client-state behavior: existing finalizeEntry persists
  instance_exit_coord, leaveForLogout moves to the exit, AccountLoadResponseHook applies that exit,
  and assignInstance sets player_in_instance=1. These allocated-map assumptions need explicit fixed
  handling; existing copied-instance behavior must remain unchanged.
- Extend or retain exact live Player/UID binding at the existing membership boundary. Current
  UUID-only occupant/index lookup can alias a replacement Player; reconciliation skips raw missing
  players. The M26/M27 identity protections must remain through staged Nex activation.
- Test no allocation/copy/movement, duplicate/overlap/private rejection without leaked indexes,
  native actor identity/reset/cleanup, level/bank/logout/raw-disappearance/identity replacement,
  unchanged canonical map/collision and default copied-instance allocation/recovery behavior.

Final pre-edit mapping: add InstanceArea.Fixed with one canonical plane and half-open rectangular
bounds; validate entry/spawn anchors inside it and exclude RegionRegistry working coordinates.
InstancePlacement uses explicit Allocated/Fixed variants, sharing entry/exit/NPC descriptions.
The fixed branch of createServerOwned reserves overlapping ownership in the existing manager,
registers no region IDs and uses native NPC repositories. Private create rejects it before fees.
Fixed creation starts/spawns immediately unless spawnOnFirstJoin; that option initializes once on
the first successful physical finalizeEntry. Empty re-entry never resets actors or room state.
Explicit resetNpcs remains the coordinated actor replacement route. Native callbacks must not
resurrect a terminated session, and ownership stays reserved until teardown completes.

Fixed membership binds account UUID to the captured Player and PlayerUid. Authoritative queries,
timers, hunt validation and HP-bar recipients filter through that binding; reconciliation prunes
raw missing/replaced occupants. Stale departure cannot clear a newly admitted replacement. Fixed
finalizeEntry validates live identity, authoritative active session and physical in-bounds position.
It changes only temporary membership: it neither writes the map var nor writes/clears an existing
instance_exit_coord. Default fixed entry therefore retains flag0/no marker; copied/account state
already present remains owned by its existing flow. Logout/disconnect saves the current public
coordinate. Copied placement keeps its allocation,
XY multi-floor bounds, empty-join respawn, client var and persisted recovery-exit behavior.
Database coordinate application and the admin exit-coordinate switch gain exhaustive Fixed cases.

Acceptance is ten native manager test groups: no region/map work, invalid/private/overlap rejection,
physical entry, first-entry/empty-room continuity, paired reset, exact bounds, native departures,
UID/object replacement, cleanup/reentrancy/failure, and copied allocation/logout controls. Retained
C-GM room coordinates and M26 continuity evidence motivate the route; this foundation changes no
OSRS timing, damage, entry eligibility, rewards or Nex logout/relogin admission policy. Account
serialization remains its established pipeline; absence/presence of its recovery attribute is
tested here, not claimed as a new live PostgreSQL persistence run. Nex adoption is separate.

Primary owns shared implementation and builds. Independent agents may map native API/persistence
paths and prepare/review disjoint tests. Runtime/deployment holds and save protections remain intact.

Implementation follows that map with sealed Allocated/Fixed placements and no fabricated Region.
InstanceSession retains identity tokens for each fixed admission (including same-object re-entry),
while InstanceManager remains the only owner of indexes, actors and public-area reservations.
Fixed entry is validated again after native callbacks. Existing InstanceScript entry finalizes in
place; the legacy generic instance command directs fixed visitors to the room entrance instead
of teleporting past content access. Timing, hunt, HP-bar and contributor fallback lookups use the
authoritative native membership; attributed offline NPC damage retains its existing semantics.

Native lifecycle review found synchronous callbacks inside NpcRegistry.add/del. Fixed actor work
preclaims the candidate and defers requested teardown until the outer repository call returns.
Exact NpcList identity prevents recursive deletion; a retirement record survives the native Delete
listener's detach until collision/zone/UID retirement completes. Overlapping creation remains
denied during that tail. Actor reset preserves newly attached helpers and rechecks each snapshot
actor's current owner, so transferred actors survive their former owner's cleanup.

Native failure limit: a throwing Delete listener can leave list removal complete but collision,
zone or UID retirement incomplete; direct deletion inside Create/Spawn can leave an outer-add
zone entry after UID retirement. No existing repository repair route was found. The foundation
propagates these failures, attempts independent owned cleanup, and keeps the failed area reserved
and unavailable. It does not clear engine failure state, retry partial deletion, or claim repair.
These failure paths are exercised; engine repair is outside this increment. Ordinary content
cleanup failures still run other cleanup and release successfully retired native ownership.

Verification history: focused1 compiled production but failed test compilation because Spawn is
a keyed event, not an unbound event. That test subscription was corrected. Focused2 then passed
all13 groups in19s (`logs/fixed-public-foundation-focused2-20260913.log`). Broader checks1 passed
in2m38s/563 tasks (113 executed):1,090 boss cases and315 shared/app cases, zero failures/errors/
skips, scoped formatting/checks, architecture detector/self-tests and app assemble. Final review
added candidate preclaim and native-loss-before-closure validation plus two regression groups.
All15 pass in19s (`logs/fixed-public-foundation-boundaries-20260913.log`). Final checks2 passes in
2m32s/563 tasks (111 executed):1,090 boss cases and317 shared/app cases, zero failures/errors/skips,
including49 instance cases and127 Nex cases; scoped formatting/checks, architecture and app assemble
pass. Logs: `logs/fixed-public-foundation-checks2-20260913.log`; per-module counts are retained in
`logs/fixed-public-foundation-test-results-20260913.json`. The formerly reused167 shared results
were invalidated by this API change; affected tests were rerun by Gradle in these broader checks.
This is a new route, so no pre-existing API behavioral RED result is claimed.

Changed Kotlin sources are listed explicitly in `logs/fixed-public-foundation-format-20260913.txt`
(23 files):12 shared production files across api/instances and boss-hp-bar-plugin, the command
exit switch, the new fixed native fixture, and nine existing constructor fixtures adjusted for
the real NpcList injection/Allocated cast. Complete task selection is retained beside it.
Source archive `logs/fixed-public-foundation-source-20260913.zip` contains exactly those23 files,
verified byte-for-byte; SHA256 `0D4E2952D0200899D8CE457527889AC8D517D6BE64DC14CC63AD39051A06355B`.
Existing complete source/PostgreSQL backups are preserved. No cache/data/credentials are included.
Source-only: JVM23812 still serves the deployed checkpoint above; no restart was performed.
Foundation commit `49f73a317`; backup branch `backup/fixed-public-sessions-20260913`.
Next deliverable: Nex public-session adoption, preserving existing protected access, exact member
identity, delayed-hit/loc cleanup and completed-round handoff. Dynamic reaver creation must first
be mapped to an owned native spawn route; post-add attachment alone is not atomic across Create
callbacks. Full private Nex/rewards/OSRS evidence gaps remain in their existing ledger entries.

## Completed source increment: expose the existing fixed actor spawn pipeline

Pre-edit mapping: local/pinned InstanceManager offers post-registration attachNpc/registerSessionNpc
and private configured-roster spawning, but no public prepared-actor path that owns native Create/
Spawn callbacks. Existing Effect.Summon is present and was inspected: it chooses random walkable
tiles/fallback, uses a hard-coded100-cycle lifetime, applies cache-default stats and only calls
encounter.own after native add. Its separate BossCombat cleanup owner also differs from the fixed
session. It cannot preserve Nex's current exact reaver offsets, HP150, native permanent-add then
respawns=false semantics and returned actor identity. Do not duplicate or silently change Summon.

Expose `InstanceManager.spawnFixedNpc(session, preparedNpc, duration)` by extracting and reusing
the foundation's private preclaimed native-add pipeline. This is the smallest missing public route
under the user's conditional approval; it introduces no engine override or separate spawn manager.
The caller supplies an unregistered NPC with its existing pre-add stats/mode/coordinates. Validate
the exact active Fixed session, in-bounds anchor and fresh NPC identity before claiming anything.
Fresh means original zone/lifecycle sentinel values as well as no slot/UID. A deleted timed actor
retains its old deadline and addDelayed actors are already queued despite being unregistered;
reject them without clearing or bypassing that native work.
Existing native NpcRepository.add controls registration, lifecycle duration and respawn defaults.
Use the same actor-operation guard, exact post-add ownership check, deferred termination and
quarantine behavior as the tested configured roster. No copied-placement or Summon behavior changes.

Acceptance: native callbacks see prepared configuration and owning session; timed/permanent adds
retain repository semantics; duplicate/stale/wrong-placement/outside inputs cannot leak actors;
nested spawning cannot run inside an in-flight actor mutation; source termination during Create/
Spawn finishes the native tail and cleans the candidate before returning false. Focused native
tests precede shared/all-boss checks and a separate source backup. Nex wiring remains the next
content increment; timing, damage, reaver count/geometry and reward policy are not changed here.

Implementation extracts the foundation's configured-roster add into one private helper and exposes
spawnFixedNpc over that same operation. Preconfigured HP/mode/coordinates are visible at native
Create/Spawn, ownership is already indexed, and repository duration/respawns behavior is unchanged.
Reused/deleted/queued candidates are rejected without clearing their native work. Copied spawning
retains its prior route, and no DSL Summon behavior changed. Two independent reviews found the
freshness precondition gap before verification; focused tests cover both native stale cases.

`logs/fixed-public-spawn-focused-20260913.log` passes all20 native groups (five new) in19s.
`logs/fixed-public-spawn-checks-20260913.log` passes in2m33s/545 tasks (91 executed,2 cached):
1,090 boss cases and322 shared/app cases, zero failures/errors/skips, including54 instance cases
and127 Nex cases. Scoped checks/formatting, architecture and app assemble pass. Exact task and
per-module result files use the same fixed-public-spawn prefix under logs. No unrelated source
formatting, cache/data edits or deployment occurred.
Two-source-file archive `logs/fixed-public-spawn-source-20260913.zip` is byte-verified;
SHA256 `B009602CDB4F7B9BBA09479DFF1D097D11DBC0506FCA36B8BBCBCC9B36E52752`.
It contains InstanceManager.kt and FixedInstanceIntegrationTest.kt. Existing complete source and
PostgreSQL backups remain preserved. This increment is source-only; JVM23812 remains online.

## Completed source increment: Nex public-session adoption

Adoption review refinement: prepared actors must also retain an in-bounds native spawnCoords,
not just current coords. Native respawn uses the constructor anchor. Reject an outside-born actor
moved inside before add rather than rewriting its anchor. This strengthens the new route's existing
placement precondition; the invalid-candidate native test now covers it. Nex's existing constructors
already use their intended spawn tiles. Prepared-spawn checkpoint: `bcf764d91`, backup branch
`backup/fixed-public-spawn-20260913`.

Use a source-defined InstanceScript/spec on Fixed bounds south-west(2910,5189,0), exclusive
north-east(2941,5218,0), existing entry(2910,5203,0) and exit(2908,5203,0). Preserve existing
public behavior: no added fee, capacity restriction, time limit or duplicate native record award.
The fixed spec has an empty configured roster; prepare Nex and its four guards with their existing
HP before spawnFixedNpc, then retain current post-add respawns/mode/lock assignments. Reavers use
the same route with existing offsets/HP150, MAX native duration then respawns=false and aggression.

The existing protected barrier/altar/command movement remains the physical authority. Finalize
native admission only after the existing actual-arrival and exact Run/Player checks. InstanceSession
becomes the room-admission and actor owner; any retained Run participant collections represent
per-round mechanic state and must never independently authorize combat/rewards or alias a new
Player. Store round state under the native session and use native leave events for departure
cleanup regardless of logout/teleport listener order. Death policy must still see admitted HP-zero
players before native death relocation: membership resolution itself has no health filter.

M26 retains the same session across completed rounds. Snapshot exact eligible survivors, retire
old round effects, call shared resetNpcs to remove all owned actors, then create the five prepared
actors and fresh round state. Revalidate source/session/player identity after native callbacks;
never bind a partial roster. Source-loss/abandonment resets clear admission and remain idle.
Session termination owns final actor cleanup; content only retires its locs, effect scope and
captured original-player UI state. No second public room manager or manual session construction.

Acceptance: native authority/indexes and zero copied-region work; unchanged M01 protected access;
M21 prison/M14 marker/M24 Wrath lifetimes; M26 two kills without re-entry; exact logout/death/bank/
raw UID loss/replacement and source deletion; dynamic reaver pre-add stats/ownership; reset/cleanup
callback invalidation and no actor/resource leak. First expand the real Nex fixture to register
native InstanceLifecycleScript and manager, then add focused integration coverage. Private Nex,
S04/S05/S06/S08 and E07 timing/damage/geometry evidence remain their separate approved gaps.

Initial focused adoption check compiled production/fixtures and ran17 groups;16 passed, one exposed
captured-original loot cleanup after raw slot replacement. Correct original-owned NEX_DROP_DURATION
without touching replacement/later ownership. Review additionally requires exact altar admission,
rollback of newly committed but content-invalid native joins, and full-roster exact native validation
after all five synchronous Create/Spawn callback paths, before publishing a round. Invalid partial
creation closes the same native session through its existing cleanup pipeline. Existing admissions
and a newer successful content enrolment must survive an older failed entry callback. These are
the mapping's identity/cleanup acceptance criteria; no combat constants or engine overrides change.
The expanded focused checks and all138 Nex cases pass. Final callback review found reset reentry:
an old encounter cleanup callback can call world maintenance before the outer native reset returns.
Guard that same Run's reset operation across cleanup/spawn callbacks, so an inner reset cannot
publish a roster that the outer operation immediately deletes. Add a behavioral cleanup-callback
regression before the final required shared checks. Fixture scope is native handler/lifecycle
integration; its calibrated frame helper is not a complete GameCycle scheduling certification.

Final implementation uses source-defined NexPublicInstance and the tested Fixed InstanceSession
plus spawnFixedNpc routes. Native session admission and captured exact Player identities govern
combat, rewards and departure; retained Run collections contain only round state. Native leave
events clear owned player effects regardless of cleanup order. Five prepared actors are validated
together after native callbacks; partial creation terminates through shared native cleanup. The
same session survives completed-round actor reset, while source loss clears admission. A per-Run
guard prevents cleanup callbacks from recursively replacing and deleting a newly published roster.
Shared spawning also validates immutable respawn anchors, covered by the existing native fixture.

Verification: focused2 passes protected access, startup, round handoff and the expanded new suite
in20s. All138 Nex cases then pass in21s. The final reset-callback case brings the new suite to12
groups, all passing in18s (`logs/nex-public-session-boundaries-20260913.log`). Final selected checks
pass in2m43s/548 tasks (97 executed):1,102 boss cases and322 shared/app cases, zero failures/errors/
skips;139 Nex and54 instance cases included. Scoped formatting/checks, app assemble and architecture
detector/self-tests pass. Log, task selection, formatting/source manifests and per-module result JSON
are retained under `logs/nex-public-session-*20260913.*`. Build passed and these behaviors verified;
no new live-client acceptance or full OSRS-parity claim is made.

Changed source files: InstanceManager.kt, FixedInstanceIntegrationTest.kt, Nex build.gradle.kts,
NexEncounterManager.kt, NexLifecycle.kt, new NexPublicInstance.kt, NexStartupTest.kt, the shared
NexMinionCombatCharacterizationTest.kt fixture and new NexPublicSessionIntegrationTest.kt.
Archive `logs/nex-public-session-source-20260913.zip` contains exactly those nine files, verified
byte-for-byte; SHA256 `1B21B738BD34F5834276608C1A57C1B3224C9CC2D9D9B0FB333ACFB08DD365CD`.
Existing complete source and PostgreSQL backups remain preserved. No cache/data edits or deployment
occurred; JVM23812 still serves c4fc9390f on all three listeners. Next exact deliverable: S08 native
NPC defence-drain minimum and Nex adoption after recording the bounded setter/config mapping.
Public-session commit: `219fa193a`; backup branch `backup/nex-public-session-20260913`.

## Completed bounded source increment: S08 atomic native defence-drain minimum

Existing S08 approval applies. Current/pinned61139e76a searches confirm all relevant NPC defence
drains already write Npc.defenceLvl, whose only local policy is statDrain immunity. No suitable
minimum exists. Effect.StatDrain targets players and is not an NPC stat-mutation replacement.
Use one optional runtime Npc.defenceDrainMinimum, default null, enforced at that existing setter.
The native boundary has no Nex IDs/constants. Source-configured NPC policy is established by
Dt2EncounterManager.spawnBoss and CerberusEncounterManager.configureBoss before native add.
This refines S08's definition/config wording to that existing source configuration route: a new
typed parameter requires IDs/definitions and packed-cache installation; a type field needs codec/
copy compatibility. Neither is required for this per-encounter policy, and no suitable existing
floor parameter was found. Do not duplicate transports or modify current dirty cache/gameval data.

Retained W1 wiki-Nex revision15325291 decoded line22 gives base defence260. W2 wiki-Nex-Strategies
revision15331654 decoded line59 states maximum reduction10: ordinary floor250 follows by arithmetic.
Revision240 Nex11278-11282 definitions corroborate260. This is retained Wiki/cache evidence, not
primary Jagex/packet proof. Configure250 from NexConfig on the prepared boss before spawnFixedNpc;
guards/Reavers/default NPCs retain null. Remove the post-world-tick repair. Transmogs, native stat
copies and respawn retain the runtime policy like existing combatImmunities; new rounds configure
it explicitly. Existing Turmoil only changes presentation; exact boosts/boosted floor remain missing
evidence and no new formula is introduced. BGS attempted-drain spillover remains unchanged.

| Responsibility | Native mapping | Observable acceptance |
|---|---|---|
| Atomic reduction | Npc.defenceLvl, immunity checked before optional lower bound |260->255 accepted; attempts below250 clamp immediately, repeats stay250, immunity still rejects reductions |
| Safe defaults/boosts | null policy preserves raw setter behavior; apply minimum only to reductions, bounded by prior level | Ordinary negative/raw assignments remain unchanged; boosts/restoration work; configuring above a current depleted stat never heals it through an attempted drain |
| Actual weapon paths | Existing flat/percentage impact handlers and Ralos ordering write the same setter | Native DWH/BGS/Elder/Bone paths and Ralos second-roll defence see accepted value before tickWorld; no weapon timing/overflow rewrite |
| Policy lifetime | Existing per-NPC configuration, transmog/copyStats/respawn | Visual forms and numeric-stat copies retain policy; native regeneration works; fresh Nex rounds configure250 and ordinary new NPCs remain null |
| Source loss | Existing room lifecycle | Replace the old raw-value1 test probe with its accepted native value, and prove world maintenance does not mutate it after source retirement |

Root owns production/shared edits and all builds. Bounded test owners may write only their assigned
test files. Focused native setter/weapon/Nex behavior precedes required engine/game, game-process,
special-attack/shared/boss regressions and app assemble; then ledger/source backup checkpoint.

Implemented exactly that source-configured runtime policy. Eight new native setter/lifetime groups,
one added native regeneration group and three Nex actor-configuration groups pass in the focused
native run (`logs/nex-s08-native-focused-20260913.log`,33s); the existing source-retirement suite
also passes with its accepted-value probe. Eight real registered melee-special groups plus the
new charged Ralos group pass in20s (`logs/nex-s08-weapons-focused-20260913.log`). Melee tests retain
the production registered attack bodies and impact handlers through native Hit.dispatchImpact;
rolls, presentation and queue scheduling are isolated. Ralos executes registered Division and
observes accuracy reads260 then250. These are not full combat-loop or new weapon-parity claims.
Independent native-boundary review found no blocker. Mockito is test-only, matching the existing
Nex dependency; no special-attack production logic changed.

Final required checks pass in4m22s/570 tasks (215 executed,1 cached):1,105 boss cases and484 engine/
shared/weapons/app cases, zero failures/errors/skips. Includes142 Nex,43 engine/game,17 game-process
and60 special-attack cases. Scoped formatting/checks, architecture detector/self-tests and app
assemble pass. Log/task selection/formatting and source manifests/per-module result JSON are under
`logs/nex-s08-*20260913.*`. This replaces the old post-tick repair with immediate native enforcement;
it does not resolve Turmoil boost semantics or the separately documented BGS overflow evidence.

Changed source files: Npc.kt, new NpcDefenceDrainMinimumTest.kt, NpcRegenProcessorTest.kt,
NexConfig.kt, NexEncounterManager.kt, NexPublicRoomLifecycleTest.kt, new NexDefenceDrainIntegrationTest.kt,
special-attacks/build.gradle.kts, new NpcDefenceDrainSpecialAttackTest.kt and
TonalzticsOfRalosSpecialDamageTest.kt. Archive `logs/nex-s08-source-20260913.zip` contains exactly these
ten files, verified byte-for-byte; SHA256
`2AB62C1DAE609357E0441B09FEAB2D982E1ED0A9175BE9C7B0D42EBBC68439AB`.
Existing complete source/PostgreSQL backups are preserved. No cache/data edits or deployment.
JVM23812 remains on c4fc9390f with all three listeners. Next exact deliverable: M18 captured tint
and area sound, with a separately recorded shared tint-ownership/late-queue-consumption mapping.
S08 commit: `1ab48e36d`; backup branch `backup/nex-defence-minimum-20260913`.

## Completed source increment: M18 captured marking and owned native tint cleanup

Retained captures3284 t477 L11951-55 and808 L7476-81 show the selected Player tint with literal
start0/end210/hue0/saturation6/lightness28/weight112. Installed RSProt240 Tinting documentation
defines these as20ms client cycles; do not replace210 with the existing eight-game-tick impact delay.
3284 L11973 and808 L7493 show sound_area5223, delay0/loops1/radius15/size0 at Nex's3x3 center
(anchor+1,+1). The transcriber omits only zero delay/size. Use the existing WorldRepository.soundArea
coordinate/raw-ID overload, not the entity overload that supplies NPC size3. No separate sacrifice
spotanim is evidenced. Target selection, impact delay/damage/distance/prayer remain unchanged.

Existing PlayerTint/tint/clearTint, RspCycle.applyTint and ToA marking/cleanup are a shared local
extension; pinned61139e76a has no player tint API. Local/pinned and all pendingTint-writer searches
find no active owner retained after synchronization. Content cannot safely clear a sent tint by
comparing pendingTint because the outgoing update is consumed. The user's conditional missing-route
approval therefore covers this minimal extension at the existing Player tint boundary:

| Responsibility | Existing route / smallest extension | Observable acceptance |
|---|---|---|
| Owned visual update | Keep tint(PlayerTint):Unit and clearTint():Unit; add tintOwned(PlayerTint):PlayerTintToken and clearTint(token):Boolean | Fresh opaque reference per application; only the exact Player's current token can clear once |
| Producer replacement | Any non-null pendingTint assignment invalidates the prior token, including equal/same-object values and CLEAR; null consumes only transport | New unrelated/owned tint before or after synchronization survives stale cleanup; same-UID replacement cannot inherit the token |
| Native queue phase | PlayerPostTickProcess currently synchronizes, runs UpdateInfo/post-tick callbacks, then unconditionally clears pendingTint | Consume the sent tint immediately after successful clientCycle.update, before UpdateInfo/hooks; remove the late tint reset. A tint/CLEAR queued by those callbacks must reach the next synchronization once |
| Nex cast lifecycle | Retain exact target Player/UID plus tint token under Run; use existing encounter cleanup, departure and captured cast completion | Selected target only; old cast/Run/departure cleanup cannot clear a newer cast or other producer; no ownership map persists past cleanup |
| Packet-backed sound | Existing official zone sound path (raw-ID adapter already exists), center coordinate and captured wire fields | Exact5223/0/1/15/0 from current Nex center, with no new SynthSound/spotanim/timing guess |

Native clientCycle.update applies tint synchronously before returning; the actual RspCycle consumer
contains no game callback. Moving its existing consumption ahead of UpdateInfo/post-tick producers
preserves the native synchronization ordering without changing other pending update types. The
token is separate from the one-shot queue, so consumption does not surrender visual ownership.
No new timers, private combat hook, replacement output pipeline, value-equality owner or account-ID
owner. Root owns production/integration/builds; bounded native queue and Nex presentation test
owners may use separate files. Verify native frame ordering/delivery at the ClientCycle boundary,
exact captured fields, early cleanup/recasts/same-UID replacements, then required shared/boss checks.

Focused verification: existing3 plus6 new PlayerTint groups pass in7s; four real PlayerPostTickProcess
groups pass in15s, recording the ClientCycle synchronization boundary. The first Nex fixture compile
exposed a missing test-only RSProt dependency for inspecting native SoundArea payloads; adding the
existing libs.rsprot.api test dependency resolves it. All six new Nex presentation groups plus12
public-session groups pass in22s. Exact native SoundArea fields, selected target, recasts, departures,
source/encounter/session cleanup and same-UID/new-producer replacement survive the tested lifecycles.
Independent production review found no blocker. These checks do not claim live rendering, normal
OSRS impact timing or a new damage rule. Required broad checks include existing ToA regressions
because ToA already produces player tints; this does not advance raid migration ahead of the queue.

Bounded next-mechanic evidence check, M20: capture3284 t494 L12387-97 calls Containment at anchor
(2935,5198); placement t499 L12534-57 and matching deletion t509 L12896-12915 show eleven visible
42943(shape10) and nine invisible26209 interior blockers. Visible offsets are north/south dx0..3,
dz-1/3 and east dx3,dz0..2; the west dx-1 boundary is absent. Rotations vary. This is an observed
partial shape, not a universal layout or rotation rule. General placement/clipping remains missing.
Ten captured tick intervals also do not directly establish LocRepository.add(...,10): native
GameCycle's pre/post-clock offset must be exercised at the actual scheduling phase, and these
Grid Master rows do not certify normal-game duration. Do not implement a guessed ring/lifetime.
Native LocRepository and the existing M21 ownership route are sufficient once geometry is pinned;
Containment/Prison42943 overlap must retire superseded ownership on both sides. Independent M06
opening visual evidence can still be checked while this exact M20 gap remains open.

Final required checks pass in5m/571 tasks (267 executed,1 cached):1,111 boss cases,494 engine/shared/
weapons/app cases and408 existing ToA cases; zero failures/errors/skips. Includes148 Nex,49 engine/
game and21 game-process cases. The selected api/repo test task has no source tests; native sound
payload behavior is verified by the Nex integration suite. Scoped formatting/checks, architecture
detector/self-tests and app assemble pass. Exact commands/manifests/results are retained under
`logs/nex-m18-*20260913.*`. No live rendering/normal-game timing claim is made.

Changed source files: Player.kt, PlayerTint.kt, PlayerTintTest.kt, PlayerPostTickProcess.kt, new
PlayerTintPostTickIntegrationTest.kt, Nex build.gradle.kts, NexConfig.kt, NexEncounterManager.kt,
NexMinionCombatCharacterizationTest.kt fixture and new NexSacrificePresentationIntegrationTest.kt.
Archive `logs/nex-m18-source-20260913.zip` contains exactly these ten files, verified byte-for-byte;
SHA256 `4EB2CF69C85E9692F5E7F5689D17D2658890DF651C8907954417FBAFD11C56E6`.
Existing complete source/PostgreSQL backups are preserved. No cache/data edit or deployment.
JVM23812 remains on c4fc9390f. Next exact deliverable: bounded M06 opening animation correction;
retained first and consecutive openings both show9182, independently of the unresolved duration.
M18 commit: `e6bfba9f6`; backup branch `backup/nex-sacrifice-presentation-20260913`.

## Completed source increment: bounded M06 opening animation

Capture3284 t353 L8654-58 shows spawning form11279 at(2924,5202), index58283, AT LAST! and
sequence9182; t587 L14103-07 repeats9182 on the next opening's fresh index59712. At t374 L8800-04,
the first actor retains its index while transforming11279->11278. These prove the visual identity
and actor continuity; decoded field order does not prove server call order, and the Grid Master
interval does not certify the existing five-tick readiness timer.

Mapping: replace only beginSpawn's anim(9203) with anim(9182), through the existing resolver and
Npc.anim -> PathingEntityCommon.anim -> pending sequence/native protocol path.9182 is already
validated (NexAssets.BLAST). No new API/config/cache edit. The selected asset's native priority10
replaces9203's5; both report tickDuration3, and their full cache durations/skeletal sounds belong
to their respective assets. Npc.anim does not await them or schedule opening readiness. Keep the
existing explicit clock+5 readiness, movement lock, native session/actor and Smoke transform.
Sound5303, facing, staged guards and full opening parity remain separate work.

Acceptance: add assertions to the existing actual protected-barrier and consecutive-native-kill
integration groups for form11279/sequence9182, same actor/UID through five-cycle Smoke activation
and fresh owned boss on the next round. Focused groups then Nex check/scoped formatting/app assemble/
architecture; reuse M18's still-valid shared/ToA and other-boss results. Root owns the two source/
test edits; independent mapping review already established the native duration boundary above.

Only the sequence ID changed in production. Existing registered protected-barrier and two-round
native-kill tests now assert9182/form11279, a fresh next-round actor, and the same actor/UID through
the unchanged five-cycle11278/Smoke activation. Focused12 public-session groups pass in20s.
`logs/nex-m06-opening-checks-20260913.log` passes in58s/472 tasks (12 executed): all148 Nex
cases, zero failures/errors/skips, scoped formatting/check, app assemble and architecture/self-tests.
M18's unchanged963 other-boss,494 engine/shared/weapons/app and408 ToA results remain valid.
This verifies the tested native sequence selection/actor continuity, not normal OSRS opening timing
or live-client appearance. Changed files: NexEncounterManager.kt and NexPublicSessionIntegrationTest.kt.
`logs/nex-m06-opening-source-20260913.zip` contains exactly these two source files, verified byte-for-byte;
SHA256 `64C4C3E0B795CB4E40BDE041A23DCA3214CB255B30898B01AB4E35558FE7AE4B`.
Source-only; JVM23812 remains deployed c4fc9390f. Next: captured opening facing and sound.
M06 animation commit: `f26dce674`; backup branch `backup/nex-opening-animation-20260913`.

## Completed source increment: bounded M06 opening facing, sound and speech

Retained capture3284 t353 L8654-58 and t587 L14103-07, corroborated808 t110 L3554-58 and
t449 L11895-99, show spawning11279 at(2924,5202), add direction west, explicit angle512 and
SoundArea5303 at(2925,5203), delay0/loops1/radius15/size0 in the AT LAST!/9182 frame.
The installed official RSProx TextServerPacketTranscriber.kt3809-15 suppresses delay/size only
when zero; their absent text fields are decoded zeros. Native-cache-inspection.log148 shows
9182's embedded sounds5231/5293/5298/5207, so explicit5303 is a distinct recorded cue.
These presentation values do not resolve normal opening duration or staged guard timing.

| Responsibility | Existing native route | Acceptance |
|---|---|---|
| Add direction | Set prepared main Nex's respawnDir=Direction.West before InstanceManager.spawnFixedNpc; NetworkScript.createNpcAvatar283-94 reads that native property during Create allocation | Initial and replacement Create callbacks observe west; guard/reaver setup is unchanged |
| Explicit opening facing | After transition clears facing lock, resetFaceEntity/resetPendingFaceSquare then pendingFaceAngle=EntityFaceAngle(Direction.West.angle); existing NpcPostTickProcess155-68 emits512 and consumes it | Stale target/square cannot replace512; actual native post-tick emits once and leaves no pending angle |
| Opening sound | Existing WorldRepository coordinate/raw-ID soundArea -> native ZoneUpdateMap/transformer, same established M18 path; center=size/2, id5303, delay0, loops1, radius15, size0 | Exact native payload on initial and completed-round openings, no startup/next-tick duplicate |

No new API or engine/cache change is needed: pendingFaceAngle and respawnDir exist in pinned
upstream61139e76a; existing native facing processors and avatar creation own emission. Avoid the
otherwise established faceDirection(West) helper here: its1x1 target produces360 for a3x3 NPC.
No raw packet hook or persistent facing lock is introduced. Preserve native asset metadata,
clock+5 readiness, form/actor continuity and current guard staging. Existing M06 approval applies.
Root owns production and integration/builds; bounded test owner edits only the new opening
presentation integration test. Focused behavioral tests first, then Nex scoped check, architecture
and app assemble; unchanged M18 shared/other-boss/ToA results remain valid.

Focused review refinement before the additional production edit: the first20-case run compiled,
but both new opening groups found the captured overhead `AT LAST!` missing. Existing announce
only calls Player.mes; the recorded NpcInfoProtocol.setSay is correctly empty. Add opening-only
Npc.say("AT LAST!") -> existing infoProtocol.setSay (Npc.kt533-35), using the same native NPC
speech route as established content. Keep the existing player chat path; matching its separate
MessageGame npc_say type is outside this bounded overhead correction. No timer or API changes.
Tests retain the failed assertion and seed stale entity/square facing before entry, checking the
native512 emission clears those competing fields. This is a verified M06 presentation omission.
Comparable native speech content is generic-npcs/ducks/Ducks.kt59 and lumbridge/npcs/Hans.kt46.

The corrected focused20-case run passes in19s (`logs/nex-m06-presentation-focused2-20260913.log`).
Final `logs/nex-m06-presentation-checks-20260913.log` passes in57s/472 tasks (15 executed): all150
Nex cases, zero failures/errors/skips, scoped formatting/check, architecture/self-tests and app
assemble. M18's unchanged963 other-boss,494 engine/shared/weapons/app and408 ToA results remain valid.
Two new groups run actual native post-tick facing emission/consumption and native SoundArea payloads,
initial/second-player entry and next-round native Create/death/reset. Native header byte encoding,
separate chat message type and live-client acceptance are not covered; normal readiness/staging
remains characterized, not recertified. No shared source changed.
Changed source: NexConfig.kt, NexEncounterManager.kt and new NexOpeningPresentationIntegrationTest.kt.
Archive `logs/nex-m06-presentation-source-20260913.zip` contains exactly these three files, verified
byte-for-byte; SHA256 `E12C9C1014702EF282BC521B017A7B708050E2E606AC71A3179490A32C477C32`.
Source-only; JVM23812 remains c4fc9390f. Next: bounded M11 native bodyguard drop dispatch, after
mapping the native explicit no-remains sentinel and preserving ordinary fallback/kill hooks.
M06 presentation commit: `be131db42`; backup branch `backup/nex-opening-presentation-20260913`.

## Completed source increment: bounded M11 bodyguard native drop dispatch

This closes a delivery bypass, not the full S04 multi-recipient Nex reward contract. Current
NexCombat.kt62-72 unconditionally calls deathNoDrops for all encounter NPCs. The four existing
content/drops/resources tables fumus/umbra/cruor/glacies already register the correct guard aliases
and five equal ceremonial pieces1/1000 plus995/1000 nothing. Retained W6/Fumus revision15221135,
recorded in the evidence section below, cites Mod Ash26April2022 for the independent1/200 table.
Do not reroll/rebalance or replace these tables. Native cache inspection35-38 shows all four guards
11283-86 explicitly carry dropped_remains param46=-1 and have no killcount_varp65551. They must
not invent bones or increase main Nex KC.

Pinned upstream61139e76a NpcDeathScript already dispatches queue.death -> deathWithDrops;
GeneralGraardor.kt20-32 manages guard respawn while leaving their deaths to that default workflow.
Upstream NpcDeath shares the same sentinel bug: typed null coalesces to bones. Existing local
NpcServerType.paramMap.primitiveMap and params.dropped_remains.id expose the exact raw sentinel.
No missing API exists here; repair the shared interpretation in api/death, not the engine/cache.
Checking only hasParam plus typed-null would also change malformed positive-reference fallback;
use precisely raw==-1 so missing/valid/invalid-positive behavior remains as before.

| Responsibility | Existing route and bounded correction | Acceptance |
|---|---|---|
| No remains | NpcDeath.spawnDeathDrops skips only its remains/drop-hook block for raw dropped_remains=-1; native kill hooks remain outside it | Explicit none produces no bones/drop hook; all kill hooks and event ID still run; missing/valid/invalid references, suppression, no hero and consuming hook behavior retained |
| Owned guard death | NexCombat handleDeath BODYGUARD -> deathWithDrops with existing native animation/removal and respawnDelay; finishDeath unchanged | All four real native lethal queues reach one native kill context; duplicate queued lethal hits do not duplicate dispatch; removed actor/room cancels unfinished death |
| Existing personal table | NpcDeath findHero -> NpcDropTableKillHook -> registered ceremonial table -> NpcRewardDeliveryService/ObjRepository | Correct guard table/native winning hero, death location, personal ownership, no remains or main Nex reward adapter; shared log/tracker/broadcast policy retained |
| Other encounter deaths | Existing main Nex and Reaver deathNoDrops retained | Main Nex still owns delayed Wrath/current reward adapter; reavers do not acquire guard drops |

Preserve current guard phase handoff before animation, native source lifecycle/hit validity and
existing loot duration. Correct phase timing and durable main Nex multi-recipient completion remain
separate approvals/evidence work; no exactly-once persistence claim follows from one in-memory hook.
Root owns two production files, Nex fixture/integration, integration and builds. A bounded owner
edits only new api/death NpcDeathRemainsTest.kt; another may review actual table registration/delivery.
Focused behavioral tests first, then affected api/death/content-drops/shared checks, all-boss regression,
architecture and app assemble. Preserve deployment hold and all existing PostgreSQL/source backups.

Focused M11 results: the13-case Nex bodyguard/lifecycle selection passes in23s
(`logs/nex-m11-guard-drops-focused-20260913.log`). Three new integration groups cover all four
native lethal queues, unequal two-player contribution, duplicate queued lethals, post-animation
removal/drop dispatch, exact death locations, native killcount hook, guard/session retirement and
unchanged main Nex/Reaver no-drop paths. The fixture now accepts native death object/drop/kill
dependencies; defaults remain unchanged. Guard drop dispatch does not call the main Nex adapter.
Seven new native remains groups pass in16s (`logs/nex-m11-native-remains-focused2-20260913.log`).
The first compile required a test-only engine.annotations dependency for real player UID setup;
no production workaround. Missing/-1/valid/invalid-positive params, suppression, unavailable native
winner, consuming-hook order and shared event IDs are exercised through actual spawnDrops.
Two new content/drops groups pass in22s (`logs/nex-m11-guard-table-focused2-20260913.log`): one real
registry/resolver loads the four existing tables; all20 piece outcomes and eight nothing-boundary
deliveries traverse native table selection, NpcDropTableKillHook and NpcRewardDeliveryService.
A test-only entropy spy supplies existing RollRandom.Context then calls the real method. Object
registration, collection log and clan services are recording boundaries; typed native loot-tracker
script7192 output is inspected. The initial compile needed explicit heterogeneous-list types in
two assertions, corrected without changing expectations; test-only RSProt dependency was added.
Two independent production reviews found no concrete defect.

Final `logs/nex-m11-guard-drops-checks-20260913.log` passes in3m23s/579 tasks (55 executed,
three from cache):153 Nex/1,116 boss cases,501 engine/shared/weapons/app,62 content/drops and408
existing ToA cases, **2,087 total**, zero failures/errors/skips. Scoped formatting/checks,
architecture/self-tests and app assemble pass. api/death-plugin, api/drop-table-plugin and generic
killcount have NO-SOURCE tests; their compilation/checks are not additional passing cases.
Selection and complete module results are retained under `logs/nex-m11-guard-drops-*20260913.*`.
Changed files: api/death NpcDeath.kt/build.gradle.kts/new NpcDeathRemainsTest.kt; NexCombat.kt,
Nex module build.gradle.kts, NexMinionCombatCharacterizationTest.kt fixture and new
NexBodyguardDeathDropsIntegrationTest.kt; content/drops build.gradle.kts and new
NexBodyguardDropPipelineTest.kt. Only the two mapped production files change runtime behavior.
Archive `logs/nex-m11-guard-drops-source-20260913.zip` contains all nine files, byte-verified;
SHA256 `9F75C9A5F670386EE4EF1D70E729ED36C9DEBE735055964EFC8D820E35E5E1B8`.
Source-only; deployment remains JVM23812/c4fc9390f. Main Nex S04 reward durability/distributions
remain open. Next exact deliverable: guard-coordinate mapping and native canonical map validation.

### M04 focused evidence checkpoint (no implementation)

Retrieved pinned [Altar15329763](https://oldschool.runescape.wiki/w/Special:PermanentLink/15329763)
confirms42965,(2939,5202), Pray/Teleport, full Prayer plus1 per worn Zaros item; any qualifying item
also restores HP/run/spec, every ten minutes. Existing native protected Op1/Op2 dispatch is suitable.
No native GWD altar workflow exists locally or in pinned61139e76a to inherit. Available owners are
worn inventory, statHeal/statBoost, native run energy and SpecialAttackEnergy.restoreFullSpecialEnergy,
with persisted attributes for an evidence-defined cooldown. No new engine API is required.
Still missing: exact qualifying equipment/variants, cooldown progression offline/shared-altars,
whether full-stat uses consume cooldown, and Nex combat-use restriction. GWD15299834 describes
out-of-combat use for the four generals' altars, not proof of that gate for Nex. Do not invent a
transient Run/UID timer. Existing statHeal throws for current>base; future mapping must preserve
boosts with an explicit current<base guard. This evidence gap does not block independent increments.

## Source verification history: M01/M02 protected access and S10

The source-only/runtime-hold statements in this historical section describe pre-deployment checks;
the current deployed status and next deliverable are recorded above.

Completed source checkpoint **444a39eb3**, backup branch `backup/nex-protected-access-20260913`.
Source-only archive `logs/nex-protected-access-source-20260913.zip` contains4,387 files and
zero excluded-path violations; required new source/tests/handoff entries were checked. SHA256:
`138FFECD8768FB79BB3582B4F1721896A79B6931A93DAA8C4B111DB5DEF03BF4`.
This archive excludes PostgreSQL/caches/credentials/generated output and is not a database backup.
Seven changed files: NexEncounterManager, NexWorld, new NexAccessIntegrationTest and existing
NexMinionCombatCharacterizationTest fixture, NexMainNativeCombatTest, NexMinionLifecycleIntegrationTest,
NexPublicRoomLifecycleTest. These changes are NOT deployed; JVM1856 remains the recovery runtime.

Production barrier/altar loc dispatch and the registered `::nex` command now use native protected
movement. Success effects follow actual changed destination and surviving player/room identity.
Same-destination calls are no-ops because native telejump's Unit return cannot prove movement
acceptance from coordinates alone. Old reentry fixtures now explicitly position at the bank;
this does not implement public auto-reentry. No direct entry/bank/exit movement remains; boss-driven
pulls stay in their separate approved mapping. Altar refusal no longer produces the unrelated
"not inside" message. Replaced/departed sessions and retired rooms cannot commit stale ownership.

Red run `logs/nex-m01-m02-access-red-20260913.log` ran10 cases:7 behavioral failures and1 wrong
CoordGrid fixture argument-order error. Native arrival delay and physical Exempt semantics passed
against old code. Green run passed all92 Nex cases in37s. Review added6 regressions, including
registered command busy handling, stale identity/room cleanup and same-destination refusal.
The final check first needed test-only InternalApi opt-in; no production workaround was added.
`logs/nex-m01-m02-final-checks2-20260913.log`: BUILD SUCCESSFUL in1m11s/476 tasks (11 executed).
Nex98, player20, instances34, bosses61, game-process16, NPC18 and death6 report253 cases total,
zero failures/errors/skips. Valid unchanged suites were reused by Gradle. Scoped formatting,
Nex check, app assemble and boss architecture detector/self-tests passed; staged diff check passed.
These tests cover production coordinate/multiloc dispatch, protected coroutine delay, native
movement/hook ordering and lifecycle ownership, not full client click/reach routing or OSRS visuals.

Exact next deliverable: permanent S10 native Central shutdown correction (mapped below), with
producer quiescence, retained failed/pending social writes and propagated cleanup completion.
Keep the restored server online while this source work proceeds; two controlled restarts remain held.

The permanent Central dependency correction passes31 focused tests (17 new) in
`logs/central-s10-focused2-20260913.log`. The first run exposed a repeated WorldLink close defect;
the native close now observes its original close future after worker termination. Accepted notifier
callbacks finish without interruption; failed/queued social writes and their original owner remain
retained. Explicit embedded stop observes the same terminal result after Koin registry closure.
The pinned patch SHA256 is `85866821e86effb7f4446aab77461c4a863bb4a396abf824689e6fc9ee86a4a5`.
This dependency/integration increment is committed as `07b00480e`, backup branch
`backup/central-native-shutdown-20260913`. Its five-file source archive
`logs/central-native-shutdown-source-20260913.zip` has verified paths and SHA256
`5BC79BEC02B8D814F0E6FFE5D88A97CB5FA9C1E0CBC00C2213C221E5D5A43EFB`.
Focused credential-pattern scan found no candidates. Applied Central source and non-patch source
pass diff whitespace checks; the raw patch's required blank context prefixes are not stripped.
`scripts/setup-openrune-central-s10.ps1` prepared and then idempotently verified the exact official
source/patch in `.gradle/openrune-central-s10`; canonical Gradle validates it before substitution.

`logs/central-s10-canonical-checks-20260913.log`: BUILD SUCCESSFUL in4m25s/539 tasks
(255 executed). All27 boss module selections contain1,061 cases, zero failures/errors/skips.
Affected shared/app modules contain285 cases: net86, game-process16, player20, NPC18, instances34,
bosses61, death6, account7, registry12, DB4, services6 and app15. Scoped net check/format,
app assemble and architecture detector/self-tests pass. This verifies source behavior/build;
JVM1856 still uses old Central2.0.1 and remains online. Canonical same-instance explicit game-owned
JDBC remains authoritative; unrelated standalone embedded-PostgreSQL paths are not enabled.

Game-side client/activity shutdown source is complete in `0f729d055`, backup branch
`backup/central-game-producers-20260913`. Six changed files are OpenRuneCentralWorldLink,
CentralActivityLogWriter, NetworkModule, GameBootstrap and their two new net shutdown test classes.
All12 new focused tests pass (`logs/central-s10-game-producers-focused-20260913.log`,25s).
Final `logs/central-s10-game-producers-final-20260913.log` passes in1m43s/542 tasks (38 executed):
1,061 boss cases and297 affected shared/app cases, zero failures/errors/skips, including98 net cases.
Scoped net/app checks, app assemble and architecture checks pass; valid unchanged suites were reused.
The operational-helper owner is adding native drains and a stable retained failure/session root.
No live attachment or handover has occurred. Read-only persistence before handover still matches
the restored inventory/bank/realm fingerprints (`persistence-pre-central-handover.log` in recovery dir).

The bounded pre-edit game-side S10 mapping is now verified: OpenRuneCentralWorldLink has no
scope annotation or explicit binding, so Bootstrap/NetworkScript/ConnectionHandler/social consumers
receive separate instances. Its accepted authentication/heartbeat executor has no shutdown method,
and stopInboundWatch drops its thread reference without joining. Bind the existing native client
as one singleton; preserve watcher ownership through bounded native stop/join; add the missing
terminal shutdown operation to this same client using graceful ExecutorService shutdown/await.
Existing Bootstrap.Resources.stopCentral invokes it after native service/callback closure and before
the embedded Central owner. This is lifecycle completion, not a new combat or transport framework.
CentralActivityLogWriter stays in its existing GameLifecycle.Shutdown path: drain accepted records,
retain failed operations/causes, propagate failure and close its datasource only after success.
Acceptance covers queued/active auth work, watcher termination, singleton injection, ordered activity
writes and retained timeout/interruption/write failures. Existing service/bootstrap failure gates
remain authoritative. The old runtime requires all original clients to be captured and drained;
the new singleton binding cannot be retroactively assumed for JVM1856.
The new client drain proves accepted authentication completes after native service producers stop;
it does not certify consumption of every pre-admission game login callback. The legacy bridge
explicitly observes those queues and dispatches existing callbacks while Central remains live.
Native push-channel reconnect semantics do not imply offline SQL cleanup; claims/checks of native
disconnect finalization apply only to eligible registered non-push connections.

The one-time old-Central bridge `scripts/CentralLegacyNativeHandover.java` is committed as
`74885e6c9`; no shutdown/handover has been invoked. It identifies authorized character1/world255 and
existing native owners before
changing runtime state. Mapping: await native prepareShutdown; close native RSProt admission and
await workers; execute pending login rejection callbacks on the existing game executor and observe
the loader batch on its own executor; require all player/account work drained; stop native HTTP,
WorldLink/notifier/scheduler, then drain accepted social operations without shutdownNow, native
flush and datasource closure; only afterward call existing bootstrap.shutdown for service/PG/lock
closure. Existing old-WorldLink non-push finalization is checked through native session queries and
read-only online-marker postconditions because its exceptionCaught can suppress callback errors.
Push disconnects retain their documented reconnect semantics. Every failure blocks subsequent
cleanup/receipt; no flags/jobs/failure states are reset and no System.exit is invoked. A static session
retains every original owner and terminal failure; duplicate attempts are refused. All11 original
client injection paths are captured explicitly, including their original watcher threads. Existing
DB callbacks run on the game executor, with loader/gateway work observed on their native owners.
Accepted client-auth work and activity logs drain before Central stops; original notifier ownership
survives field clearing. Nineteen standalone gates pass independently, including actual queued work,
retained failure, authority on the game executor and ambiguous/missing capture refusal; see
`central-legacy-gate-tests-final.log` in recovery dir. They are not live persistence tests.

Read-only inspection against JVM1856 succeeded with11 distinct native clients,2 live watchers,
3 service executors,5 Central owners, live notifier and open activity datasource. No retained session,
save countdown, callback consumption, shutdown or receipt occurred. This verifies the actual capture
lookups before mutation. Exact frozen agent SHA256 is
`D30525FCC35ADD4C81A6609723228FEC3CF6B3054DA6B4EFDE1C0176B7E2C16A`;
reuse this loaded artifact for handover. Final source backup and independent review remain the last
handover prerequisites. Native pg_dump is absent from the
embedded binary package; preserve the existing offline backup and take a fresh offline copy after
verified native closure, before launching any replacement.

Pre-edit mapping reuses approved M01/M02 and existing APIs; no new shared route is needed.
The self-protection defect suggested during recovery is NOT verified: executing protected
coroutines are no longer suspended, so the old check does not inherently reject its own handler.
The verified gap is direct PathingEntityCommon movement bypassing native hooks/zone validation,
plus altar departure cleanup before movement. Acceptance is observable production access dispatch,
hook-before-movement/membership ordering, invalid/refused travel preserving membership/effects,
native busy/arrival handling and no enrollment into a replaced room.

| Mechanic | Existing native mapping | Evidence/limits |
|---|---|---|
| Public barrier pass | LocInteractions.opTrigger → existing LocCoordEvents → ProtectedAccessLauncher/event → arriveDelay → ProtectedAccess.telejump(Exempt); commit membership only on arrival and same active room | [Barrier15333724](https://oldschool.runescape.wiki/w/Special:PermanentLink/15333724); [official Venenatis physical traversal61139e76a](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/content/bosses/spindel/src/main/kotlin/org/rsmod/content/bosses/spindel/VenenatisLair.kt#L53). Exempt classification is comparable-content mapping, not captured Nex timing proof. |
| Administrative bank command | Existing ProtectedAccessLauncher as in KrakenWorld, native telejump(Exempt), then departure/preparation and success output | Existing TeleportType.Exempt explicitly covers administrative commands; preserve coordinates and command semantics. |
| Altar exit | Existing protected event and arriveDelay → telejump(Standard), then departure cleanup and success output; distinguish refused travel from absent membership | [Altar15329763](https://oldschool.runescape.wiki/w/Special:PermanentLink/15329763) documents Teleport to safe-room entrance; official InstanceScript defaultLeaveFlow uses Standard. Nex-specific Tele Block evidence is absent; injected validator refusals test API behavior only. |

ProtectedAccess/telejump are official upstream APIs. LocCoordEvents and PlayerTeleportHooks are
established local shared extensions. Exempt still checks valid destination zones and invokes
beforeTeleport hooks. Standard invokes existing validators; the current Wilderness hook returns
outside Wilderness before checking Tele Block. Kill-count/essence access, private sessions, altar
Prayer restoration rules, boss-driven pulls and full instance attachment remain separate mappings.
No encounter HP/phase/damage/timing/asset/geometry constants are changed in this increment.

## September13 crash recovery and S10 timeout correction

**Current runtime:** recovery completed; JVM1856 is online from03:32 EDT September13 on
game43594/HTTP8080/WorldLink9091. Native SQL audit `persistence-restored.log` matches the
before-recovery23 inventory containers,1,153 inventory/bank rows and1 realm fingerprint.
Canonical doctor verifies revision240, data authority and unchanged SERVER/LIVE dat2 hashes.
JVM1856 runs incoming-hit source `a87f5fc47` plus tested shutdown fix `9e592b389`; this is
runtime restoration, not two-restart deployment acceptance. Central dependency fix is not loaded.

Recovery artifacts below live in `logs/crash-recovery-20260913-025956/`. After native producer,
account saving and game database services completed, Central2.0.1's ApplicationStopped callback
could not resolve owners because Koin4.0.2 had closed its registry on ApplicationStopping.
Read-only capture recovered the original WorldLink/PgNotify/social-persistence/datasource owners,
verified listener identity and shared datasource, and showed social submitted0/active0/queued0.
`CentralNativeRecoveryFinish.java` then invoked existing native WorldLink stop and awaited workers,
native notifier stop/join, scheduler shutdown/await, social flush, datasource close, bootstrap-owned
PostgreSQL stop and lock release. Fresh `logs/native-central-final-*.ok` receipt precedes normal
exit solely to release the already-failed retention thread. No hard kill, failed-save discard,
failure-state reset or ordinary clean-shutdown receipt occurred. Earlier guard refusals remain in
startup stderr; the original ordinary restart still failed. Operational helper sources are archived
under `retained-helper-sources/`, not installed as gameplay or production engine hooks.

`9e592b389` is backed up by `backup/native-shutdown-empty-world-20260913`. Four focused cases
failed before the correction; all16 game-process cases pass afterward. Combined app/services/
account/registry/DB/Nex tests plus assemble passed in1m16s/470 tasks (`final-build.log`);
scoped formatting passed. Existing recovery helper error/suspend tests passed7 cases.

Remaining S10 deployment prerequisite: repair the native Central dependency's existing stop
workflow. Official source pin9d718ca61aa6437007f883f27c35126fac5a0558 matches all235 installed
2.0.1 source/resource entries; clean checkout is `../backups/upstream-central-s10-20260913`.
Existing KoinApplicationStopPreparing runs before registry close, but merely moving the event
does not prove saves completed: social flush currently discards pending work on timeout and
swallows write failures. Capture existing owners while available; stop and await producers before
drain, retain failed/pending operations, propagate the same terminal result through existing
CentralEmbeddedServer.stop, and close datasource/PG only after success. Public start/stop signatures
and the existing property-gated includeBuild route suffice; no production reflection is needed.
Two clean controlled restarts remain held until this source correction is tested and backed up.
Next approved boss source increment is Nex M01/M02 native protected movement and access dispatch.

The machine crash ended the historical JVM42364; it is no longer a live deployment hold.
Recovered incoming-hit source is committed as `a87f5fc47`, with recovery branch
`backup/nex-native-impact-20260913`. The final pre-crash all-boss build actually passed:
`logs/nex-s02-all-boss-checkpoint2-20260913.log`, 1m50s,522 tasks (44 executed).
Existing XML reports contain1,045 boss cases and244 affected cases, with zero failures/errors/skips;
some suites were up-to-date in that final invocation. No rendered-client acceptance is implied.

Cold recovery preserved an offline copy of canonical `.data/postgres` and a source archive in
`logs/crash-recovery-20260913-025956/`. The source archive SHA256 is
`3DF5B0D57720B5700A3AED9C3DB29BD6CBD856BDC4440CE8AE96A90A2553EBDD`.
JVM10576 started successfully at03:01:14 EDT, with the canonical build marker/revision240 and
ports43594/8080/9091. PostgreSQL was readable. First controlled restart did NOT complete:
preflight reported committed0/players0/pending0, then native preShutdown simulated1024 entire
world cycles despite the empty player list. Signal/cleanup timeouts retained PostgreSQL and the
JVM. This was an operational timeout, not a successful restart or a reported account-save failure.

Bounded pre-edit S10 correction mapping (existing approval, no combat API change):

| Responsibility | Existing native owner and correction | Verification |
|---|---|---|
| Final database responses | Keep GameDbSynchronizer fast-forward and explicitly dispatch all queued callbacks on the game thread before testing player emptiness | More than50 responses, callback-created pending logout and thrown callback |
| Native player logout cycles | MainGameProcess retains pendingShutdown flags and native GameCycle; stop when PlayerList empties and fail closed if the1024-cycle limit leaves any player | Empty world, actual removal after cycles, retained/logging-out player at limit |
| Already retained cold-start JVM | One-time operational helper verifies the exact timeout-only failure, completed service scopes/executors, empty player/login/logout/save/gateway queues, then invokes existing native Service.shutdown in Producer/Persistence/Resource order and bootstrap-owned Central/PostgreSQL/process-lock closure | Refuse dirty state, other failures and incomplete suspend callbacks; fresh recovery receipt only after successful cleanup |

The one-time helper must not clear native failure state, forge a clean ordinary-shutdown receipt,
skip persistence errors, interrupt the retained process, or hard-kill it. Normal JVM exit is allowed
only after verified native cleanup, to release the failed-shutdown retention thread. The original
failed restart stays recorded; two later clean controlled restarts are still required.

## Latest September13 continuation: native incoming Resolve/Impact integration

Approved M10/M11/M16/M17/M23 and S02/S03 wiring now replaces incoming queue-time gameplay mutation.
Shared prerequisite `63c5028ee` preserves immutable queued input separately from admin-adjusted input
and accepted HP loss in the existing Impact event. The subsequent conditional native headbar refresh
keeps silent absorption HP visible without moving event order or inventing a healing hitmark.
These are approved local shared extensions of the official A04 processor, not upstream signatures.
Nex's BossCombat registration is the existing A01 workflow with approved local Resolve/Impact slots.
No private hit scheduler, context-identity map, new boss framework, or alternate death path was added.

Implementation changes in the existing NexCombat/NexEncounterManager:

- Queue construction binds existing HitValidity to the captured native encounter and exact owner
  session. It neither changes HP nor grants contribution/healing, reflection or bodyguard calls.
- Resolve samples the current phase, guard vulnerability, Siphon window, Deflect overhead and HP.
  Native processing retains final HP clamping, source-tagged contributions and single queued death.
- Impact observes actual accepted damage for guard calls, the temporary existing reward adapter,
  Reaver player healing and Deflect reflection. Siphon uses each immutable original incoming hit,
  never admin-expanded damage; zero hits cannot clear a recorded positive-healing failure flag.
- Recoil, burn and thrall-attributed hits are no longer globally discarded as non-player sources.
  Unowned damage may damage the actor without fabricated player credit. A declared owner
  who is absent/departed or a retired/replaced session cannot acquire a fresh encounter membership.

All pre-existing NexRules caps, thresholds, 3/4 Reaver healing, Siphon8-tick window, rotations,
NPC/sequence/graphic/projectile/sound IDs and geometry remain characterized rather than recertified.
Nex/guard/Reaver flat armour is zero in these executable encounter fixtures. Moving policy to Resolve
uses native armour-before-resolution order; no nonzero-armour boss-local exception was introduced.
Existing debt remains explicit: the reward/contribution adapter is not yet M28/S04 native multi-recipient
completion; Reaver player lifesteal still sets the inherited Nex-healing CA flag (E02); Deflect's inherited
clamp-before-half rule leaves the last HP unreachable with melee during that overhead. The latter's
correct formula belongs to unresolved E03/M23 rather than an unannounced balance change in this wiring.
Public/private session conversion, secondary magic accuracy, prison/Drag/Wrath mechanics, rewards,
reclaim, persistence and CAs remain mapped work. No full Nex or 1:1 completion is claimed.

Tests execute production NexCombat registration, real NPC queues/processors and native guard death.
Nine new regressions first failed against old wiring (`nex-s02-incoming-red-20260913.log`,41s), then
passed with all existing Nex tests (`nex-s02-incoming-green-20260913.log`,53s). The next three tests
exposed the stale Siphon bar (`nex-s02-headbar-red-20260913.log`,43s,12 cases/1 failure); correction
passed with NPC/boss/Nex suites (`nex-s02-headbar-green-20260913.log`,58s). Additional arrival-overhead,
ownerless/indirect/session/retired ownership and private/delayed bar regressions are included in the
final checkpoint run. These are not rendered client, PostgreSQL or raw packet parity tests.

Shared headbar correction is separately committed as `815275a7f` on
`fix/npc-impact-headbar-20260913`; Nex integration continues on
`refactor/nex-native-impact-20260913`. Three read-only subagents independently reviewed API,
mechanics and tests; only the primary edited source. The test audit added cancellation inside
Resolve, cancellation after Impact healing, composed caller validity and ownerless native-credit
assertions. `nex-s02-review-cancellation-20260913.log`: BUILD SUCCESSFUL, 48s,169 tasks; NPC18
and Nex82 cases, zero failures/errors/skips. The full affected result set is244 cases, including
boss API61, game-process11, combat-scripts8, player20, death6, instances34 and cache4.

The preceding broad scoped check/assemble/architecture run passed in41s/483 tasks
(`nex-s02-full-checkpoint4-20260913.log`). Earlier attempts are retained: fixture compilation
needed InternalApi opt-in; the private-bar fixture needed a player source UID and a delay compatible
with native five-unit headbar packing. These were test corrections, not production protocol changes.
An unscoped NPC check then failed on formatting in nine untouched files
(`nex-s02-all-boss-checkpoint-20260913.log`,25s/200 tasks, primarily LF/CRLF differences).
No mass-formatting or unrelated changes were made; this is not reported as an all-module check pass.

Final broader build/backup results are recorded below when complete. Live JVM42364 remains
0372044fc; no attachment, restart or gameplay deployment occurred. The read-only doctor in
`logs/nex-s02-doctor-after-20260913.log` confirms the canonical root and unchanged revision240
SERVER/LIVE/PostgreSQL authorities. S10's old-JVM save-first handover and two verified restarts
remain the deployment gate. No rendered encounter or OpenRune-versus-OSRS packet diff was run.

## September 13 continuation: runtime safety and approved M26 world lifecycle

This slice is committed as **`55efb8fa3`**, recovery branch
`backup/nex-native-lifecycle-20260913`. Final source-only archive:
`logs/nex-native-lifecycle-20260913.zip`, 4,401 files, zero excluded-path violations,
SHA-256 `45D925B46A59DDBEB6E1DDA40EE6FBAA81E2A2253CB23695F4F91CE03FEC6984`.
Archive review retained the legitimate TwoFactorSecretResolver source (not secret material);
no credentials, PostgreSQL, cache, logs or generated output are included. This is not a DB backup.
No remote push/merge/PR or server restart was performed in this slice.

Changed files: Nex `build.gradle.kts`, `NexEncounterManager.kt`, `NexLifecycle.kt`, existing
`NexMinionCombatCharacterizationTest.kt` fixture, new `NexPublicRoomLifecycleTest.kt` and
`NexSpecialLifecycleTest.kt`; documentation `DSL_MIGRATION.md`, `MIGRATION_HANDOFF.md`,
`RESUME_OPENRUNE_BOSS_API_DSL.txt` and this ledger. No shared-engine source edits. The existing
four tracked `.data` changes and untracked Grand Exchange area are preserved and uncommitted.
Changed-source secret-signature scan found zero matches; staged and final diff checks passed.

Prior main-combat checkpoint (still deployed): `0372044fc`, recovery branch
`backup/nex-main-native-verified-20260912`. Source-only archive
`logs/nex-main-native-verified-20260912.zip` has 4,399 file entries, no excluded-path
violations, SHA-256 `7C56E647730A44479C48AF26763048138D398DEC4734CD7F5739DE273AB88492`.
It is not a database backup. The previous archive counted directory entries as well.
All 27 non-diagnostic boss module test tasks and unscoped Nex check passed:
`logs/nex-all-boss-regression-20260912.log`, 2m18s, 309 tasks; 1,017 tests, zero failures/errors.
Modules with NO-SOURCE are not claimed to have test coverage.

**Deployment is not persistence-verified.** The first replacement JVM is 42364, running the
main/guard-call checkpoint, not the older minion-only source. Startup marker 23:37:50 EDT on
September 12, ready/Nex IDLE room at 23:37:53; log
`logs/cerberus-audit-20260907/restart-2026091201.stdout.log`. The main NexBossSpecKt class is
loaded. `logs/nex-main-doctor-restart1-20260912.log` and the September 13 doctor confirm the
canonical directory and unchanged revision-240 SERVER/LIVE/PostgreSQL authorities.

The preceding shutdown FAILED to finish account saves safely. In
`logs/cerberus-audit-20260907/restart-2026091008.stdout.log`, connections close at 23:36:33
(L3444 onward), account saving starts at 23:36:44 (L3541), saves fail (L3542/L3583/L3624),
retries exhaust (L3665-L3666), yet L3708 incorrectly reports four saved requests. The existing
restart helper called System.exit and native hooks; no hard kill was performed. A second
restart is held. Do not reuse that shutdown path without verified save draining/ordering.

Read-only stored inventory/container/realm fingerprints match before and after startup in
`logs/nex-main-persistence-before-20260912.log` and `...restart1-20260912.log`.
**Matching stored data does not prove the failed final in-memory save was preserved.**
No rendered-client Nex fight or OSRS/OpenRune packet diff was completed.
Shared shutdown ownership/save-ordering is a newly discovered gap outside S01-S09; it needs
its own explicit mapping/checkpoint. No bootstrap/database/service change is included below.

### M26 bounded implementation mapping (existing approval; no shared API addition)

| Behavior / confidence | Existing implementation | Established workflow / classification | Planned change and executable evidence |
|---|---|---|---|
| A public room must progress with zero online players; architecture/lifecycle requirement, not a client-capture timing claim | First PlayerPostTickHook callback drives tickWorld, guarded by lastWorldTick | **Official upstream** GameLifecycle.EndCycle, GameCycle and ScriptContext.onEvent at pinned commit 61139e76a216592d66c2b20366ef26d48609875d | Register one world-cycle subscriber, remove the private per-player dedup timer; prove empty-world reset through production registration and native GameCycle publication |
| Preserve inherited five-cycle opening readiness, one-cycle abandoned-room reset and 50-cycle completion reset; these constants remain characterization, not normal-OSRS proof | Run deadlines read MapClock; cleanup uses NpcRepository/LocRepository | **Official upstream** MapClock/world lifecycle/repositories; **existing local** BossCombat deletion closes scoped encounters | Preserve deadlines/geometry and native NPC deletion; tests cover no observers, remaining partner, repeated resets and fresh encounter identity |
| Per-player Choke/Darkness/cleanup is not a world clock | PlayerPostTickHook updates effects, membership and barrier | PlayerPostTickHook is an **existing local/fork** workflow, not an upstream claim | Retain only player-specific processing there; test unrelated/multiple player callbacks cannot advance the room |

Exact upstream references inspected before edits:
[GameLifecycle](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/game-process/src/main/kotlin/org/rsmod/api/game/process/GameLifecycle.kt),
[GameCycle](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/game-process/src/main/kotlin/org/rsmod/api/game/process/GameCycle.kt),
[onEvent](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/script/src/main/kotlin/org/rsmod/api/script/ScriptEventExtensions.kt),
[ScurriusInstance](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/content/bosses/scurrius/src/main/kotlin/org/rsmod/content/bosses/scurrius/ScurriusInstance.kt).
Search also found local Vorkath/DT2 StartCycle and Grand Exchange EndCycle consumers; these are
local examples, not upstream proof. EndCycle is after the same MapClock increment and NPC
post-tick as the old callback, but after all player flushes. This removes player-order dependence;
chat/barrier output at transitions may flush on the following cycle. Client packet comparison
is still required. This bounded change does not finish public/private InstanceScript migration,
automatic consecutive-kill participation, hazard ownership or death/reclaim integration.

Retained consecutive-kill gap: decoded Grid Master private-solo capture 3284 rev234.1 has first
KC/deletion t549 L13987-L14017, player movement inside the arena t559-t562 L14047-L14060,
then fresh Nex index59712/opening at t587 L14100-L14107 without re-entry. Current reset still
does not enrol surviving occupants in the fresh room. This is separately mapped session work;
the observed 38-tick gap is not a normal-mode timer and was not copied. Empty/offline rooms
cannot be observed by a departed capture client.

M26 result: all five new `NexPublicRoomLifecycleTest` cases failed against the prior clock driver
(`logs/nex-world-lifecycle-red-20260913.log`, 47s). After the production event migration all 59
Nex tests passed (`logs/nex-world-lifecycle-green-20260913.log`, 54s, 162 tasks).
The room returns immediately after replacement instead of continuing to mutate the retired NPC.

### M27 / S03 bounded delayed-work mapping (existing approval)

| Required behavior | Existing defect / confidence | Native owner and classification | Implementation and tests |
|---|---|---|---|
| Retire pending special/death work when its encounter is destroyed | Eight encounter-owned raw world-queue sites retain callbacks until their deadline; source inspection | **Official** world queues/BossEncounter; **existing approved local** BossEncounter.schedule and cancellable queue extension | Use schedule with identical countdowns and retain existing phase guards. Native NPC deletion must remove pending callbacks immediately; initial pre-encounter startup stays world-owned |
| Departure/replaced target/retired encounter must invalidate hits at actual impact | Special callbacks check membership before queuing, but emitted hits omit validity; unlike existing ordinary/secondary attacks | **Official** native player hit queue/processor; **existing approved local** HitValidity and EncounterRegistry.isAttached | Reuse the existing secondary-hit attachment + captured player/room validity pattern for specialized damage and Deflect reflection. Exercise real hit processing, not just Hit.isValidNow |
| Wrath may resolve after HP reaches zero while its original source is retained | Existing native hidden-source tests passed | isAttached (not isCurrent), same registered NPC identity | Keep the positive native-death/Wrath tests; never introduce a blanket positive-source-HP restriction |

This does not change the special rotation, damage maxima, geometry, prayer policy or phase-at-impact
rules. Those remain separately evidenced/characterized work. Additional tests will force specials
through the registered DSL and compare countdown/impact behavior, player departure after the
callback, source and encounter replacement, and surviving duo ownership. It does not certify the
inherited Ice Prison escape/expiry logic, premature achievement flags, or private movement code.
Exact local extension owners inspected: `api/bosses/.../runtime/BossEncounter.kt` schedule,
`EncounterRegistry.kt` isAttached, `engine/game/.../queue/WorldQueueList.kt` addCancellable,
and `engine/game/.../hit/HitValidity.kt`. Official native workflow remains A01/A03/A04;
the extra cancellation/validity signatures are not represented as official upstream.

M27 regression sequence: the initial test build failed because the test lacked the existing
InternalApi opt-in (`logs/nex-special-lifetime-red-20260913.log`); no gameplay ran in that
invocation. The corrected six tests ran: the positive countdown/damage case passed and five
lifetime cases failed (`...red2-20260913.log`, 43s). With owned callbacks/impact validity,
64 of 65 Nex tests passed (`...green-20260913.log`, 50s). The remaining failure showed Choke
still draining prayer after the registered source had been deleted. M26/M27 correction mapping:
use the existing isAttached check before player effects and at room maintenance; retire and
replace a destroyed room through the existing cleanup/reset path. Add an explicit native
deletion regression, including no old-effect processing and no mutation of the retired actor.

M26/M27 final source verification: 13 new executable cases (six public-room lifecycle, seven
special-lifetime), 67 Nex tests total. The special suite forces six reachable specials through
BossCombat/Effect.External, preserves callback countdowns and native one-tick impacts, exercises
seven departure/identity conditions, NPC deletion/encounter replacement, duo isolation and
Deflect reflection. An additional native-death regression deletes hidden Nex BEFORE the pending
Wrath/reward callback and proves immediate cancellation/no reward invocation. Existing native
Wrath positive tests still pass. Drag remains unreachable in the current selector; its validity
wiring is not represented as a positive gameplay test. Prison-break semantics, CA attribution
and successful automatic consecutive-kill membership remain open.

`logs/nex-special-lifetime-green2-20260913.log`: 66 tests passed, 49s/162 tasks before the last
death-callback test. Initial broader invocation (`logs/nex-lifecycle-checkpoint-20260913.log`)
failed task parsing because PowerShell split the unquoted manifest property; no tests ran there.
The quoted rerun passed: `logs/nex-lifecycle-checkpoint2-20260913.log`, 1m25s/480 tasks
(17 executed, 463 up-to-date). Command:

```powershell
.\gradlew.bat -I scripts/format-selected.init.gradle '-PopenruneFormatManifest=logs/nex-lifecycle-format-20260913.txt' :content:bosses:nex:spotlessApply :content:bosses:nex:check :or-cache:test --tests dev.openrune.NexR240CacheAuditTest :api:bosses:test :api:game-process:test :api:player:test :api:combat:combat-scripts:test :api:npc:test :api:instances:test :api:death:test :server:app:assemble bossArchitectureCheck --no-daemon --console=plain
```

Result XML: Nex 67, boss API 61, game-process 11, player 20, combat-scripts 8, NPC 12,
instances 34, death 6, cache audit 4 = **223**, zero failures/errors. The shell's redirected
JVM warning appeared as a PowerShell NativeCommandError; Gradle's terminal result is BUILD
SUCCESSFUL. The subsequent broader invocation explicitly propagates Gradle's own exit code.
Formatting is scoped to five changed Kotlin files; no shared engine source was changed.
Manager lines are now 1,254 (1,236 before this slice; original inventory 1,321). The additional
validity/teardown checks are not disguised as a larger architecture reduction.

Broader verification also passed, explicitly propagated Gradle exit 0:
`logs/nex-lifecycle-all-boss-regression-20260913.log`, 24s, 309 tasks (2 executed, 307
up-to-date). All 27 non-diagnostic boss test tasks plus unscoped Nex check were selected;
their result XML contains 1,030 tests with zero failures/errors. Cached unchanged results and
NO-SOURCE modules are not fresh gameplay execution or missing test coverage filled in.
Exact task selection:

```powershell
$nexBossTasks = @(Get-ChildItem -LiteralPath content/bosses -Directory | Where-Object { $_.Name -ne 'diagnostics' -and (Test-Path -LiteralPath (Join-Path $_.FullName 'build.gradle.kts')) } | Sort-Object Name | ForEach-Object { ':content:bosses:' + $_.Name + ':test' })
& .\gradlew.bat @nexBossTasks :content:bosses:nex:check --no-daemon --console=plain
```

No September13 deployment/restart or rendered-client/packet verification was performed. The
unchanged live checkpoint and failed previous shutdown remain as recorded above. M26/M27 source
is ready for a focused checkpoint, not full encounter acceptance; S10 blocks safe deployment.

### S10 shared shutdown/save ordering (approved September 13; implementation in progress)

The user's subsequent `approve` explicitly covers this mapping. Source recovery branch
`backup/before-s10-shutdown-20260913` preserves `38fe43cc1`; work is isolated on
`fix/native-shutdown-order-20260913`. No deployment has occurred for this repair.
Source checkpoint `cde8b17a8` is preserved by `backup/native-save-shutdown-20260913`.
Focused source archive `logs/native-save-shutdown-source-20260913.zip` contains all28 checkpoint
files (not a whole-tree or database backup); SHA256
`D43B046FB337E75E16409C8247F638D5B0864DAD005896DE753B42D2A9C8613D`.
Final `nex-s10-ready-checkpoint-20260913.log`:44 core tests, scoped core checks and app assemble
pass,1m27s,477 tasks,20 executed. No remote push, live attachment or restart was performed.
The smallest ordering API uses fixed Producer/Persistence/Resource stages instead of an arbitrary
dependency graph: cycles are unrepresentable, and unrelated services retain concurrent producer
shutdown. This implements the approved ordering scope without adding a separate framework.

This is a deployment/persistence prerequisite, not a Nex combat mechanic. The observed failed
shutdown above demonstrates the requirement: finish committed native account saves before closing
their database, and never report attempted saves as committed saves. The existing restart helper's
process-exit observation is insufficient. Standing deployment authorization does not waive the
on-disk AGENTS checkpoint for a newly discovered shared-engine capability.

Searches covered the complete local service/bootstrap/account lifecycle, pinned upstream
`61139e76a`, native slowreboot/GameUpdate -> PlayerLogoutProcess -> AccountRegistry workflow,
Service/ListenerService/ScheduledListenerService, and existing database concurrency tests. There
is no service dependency/complete-drain barrier in those interfaces. The current database builders
leave Zonky's independent shutdown hook enabled; its concurrent JVM hook is a source-supported
likely cause of the observed premature database close, not a directly logged hook execution.
Separate latent races are also present: a second awaitShutdown caller returns AlreadyShutDown
while the first still works, and shutdownServices launches database/saver shutdown concurrently.

| Native owner / explicit source scope | Smallest proposed reusable correction | Regression requirements |
|---|---|---|
| `api/db/.../jdbc/EmbeddedSameInstancePostgres.kt`, `server/app/.../GameBootstrap.kt` | One bootstrap-owned PostgreSQL shutdown; disable the independent library hook in both builders. Preserve startup-failure cleanup and never stop reused PostgreSQL. Do not hard-halt/discard unsaved work as a success path | Controlled owned/reused database fixtures; failed/partial startup; PostgreSQL remains available until save commit; no forced-stop path for unfinished native saves |
| `server/services/.../ServiceManager.kt` | Concurrent shutdown callers wait for one completed terminal result, including errors | Latch-controlled concurrent callers cannot return early; cleanup once; both observe failure |
| `server/services/.../Service.kt`, ServiceManager, native GameService/AccountSavingService/GameDatabaseService declarations | Optional dependency ordering inside the existing service manager: producer/game finalization, saver drain, database close. This is a **proposed new shared API**, not a second framework | Dependency ordering, cycle detection, failed predecessor propagation, default behavior for unrelated services |
| `api/account/.../saver/AccountSavingService.kt` | Drain partial batches and queued requests, count actual commits, propagate terminal failure | Interrupted batch loses no request; callbacks once; four failed attempts produce zero committed saves and failed shutdown |
| `api/registry/.../account/AccountRegistry.kt` | Failed save cannot be treated as persisted logout; retain recoverable in-memory state and surface deployment-blocking failure, without adding a flat-file authority | Successful save permits removal; failed save does not falsely declare drain complete or discard the only pending state |
| Existing controlled restart helper / native pre-drain workflow | Verify committed logout/drain before requesting JVM shutdown, then require successful shutdown before starting a replacement. Do not use System.exit as the save-drain gate | Failed save or unsuccessful exit blocks restart; canonical identity retained; no live data used for failure-injection tests |

September13 implementation checkpoint (source only; no live shutdown requested):

- `Service.ShutdownStage` adds Producer/Persistence/Resource ordering to the existing manager;
  concurrency remains within each stage. Failed producers/saves retain resources. Concurrent
  shutdown callers join the same completed result, including interrupted/partial startup cleanup.
- Native account saving retains unfinished batches, separates ordinary cancellation from failed
  attempts, counts committed transactions, and refuses new admission after final drain begins.
  Failed snapshots retain player references; a later successful full snapshot can supersede them.
  Callback failures block deployment and are never automatically retried as database writes.
- Native AccountRegistry deletes only after Success and successful native player removal. Failed
  logout saves can be explicitly retried on the game thread after the underlying fault is repaired.
- GameBootstrap owns database shutdown once, after service completion; independent Zonky hooks,
  bootstrap hard-halt/watchdog, and forced PostgreSQL-stop fallback are removed. The owned process
  must actually exit, because Zonky can log a failed close without throwing. Reused PG is untouched.
  `GameServer.startupGame` now joins that same cleanup for late script-load failure; this is call-site
  wiring of the approved startup-failure requirement, not another lifecycle or persistence API.
  A failed cleanup retains the bootstrap/service heap on one non-daemon recovery thread, rather than
  allowing main-thread return to discard the only failed snapshots. This cannot cancel an already
  initiated external System.exit; it is a retention guard, not a successful save or automatic repair.
- `GameService.prepareShutdown` routes through native GameUpdate/logout/save on the game executor.
  Admission must be Updating; all players (including loggingOut), native logout responses and native
  saves must drain before it quiesces normal game-cycle producers and acknowledges the preflight.
- The existing restart helper now requires that native future and successful bootstrap cleanup;
  it writes a fresh PID/root/nonce receipt only afterward. It does not call System.exit or halt.
  A non-daemon receipt worker prevents natural main-thread exit from racing receipt creation;
  worker errors propagate and no receipt is created after failed cleanup.
  **The currently loaded legacy JVM lacks this entry point and is deliberately refused.** The
  one-time old-JVM handover is still to be implemented/verified; do not substitute process exit or
  stored database fingerprints for native save acknowledgements.

New capability classification: shutdown stages, completed-result barrier, native drain-status and
preflight entry points are approved **local shared extensions**, not claimed upstream APIs. Existing
ServiceManager/GameUpdate/AccountRegistry/account transactions remain the owners; no boss-local
replacement was added. Bootstrap Resources is a test seam for its existing resource owners.

Tests: 33 new executable JUnit cases (services6, account7, registry4, database3, bootstrap/game13).
The five affected module suites total44 cases, zero failures/errors. Six additional standalone
`NativeShutdownGateTest` cases verify success, failed/cancelled drain, cleanup/worker failure and
non-daemon receipt ownership without
attaching to a live JVM. Database ownership tests use mocked process/library handles, not live
PostgreSQL failure injection. Native future tests drive GameService with a mocked GameProcess;
the native logout integration additionally executes real PlayerLogoutProcess/Processor, AccountRegistry,
AccountManager, AccountSavingService and PlayerRegistry against a deterministic JDBC proxy. It proves
Logout-hook snapshot -> commit -> deferred callback removal, identity/zone/collision cleanup, exactly
once removal, failed-save retention and explicit repaired retry. A throwing native Delete hook still
blocks drain; its existing partial-removal recovery limitation is characterized, not redesigned.
Two isolated child-JVM tests use fake resources and prove failure survives main return with exactly
one retention guard, while success exits naturally. None certifies rendered-client behavior or PG
durability. This distinction is intentional.

Commands/results:

- `:server:services:test`: two initial red concurrency regressions, then passing after repair;
  `logs/nex-s10-services-red-20260913.log` (24s).
- `:api:account:test :server:services:test`: three account regressions red, then green;
  `logs/nex-s10-account-red-20260913.log` (33s), `nex-s10-save-services-green-20260913.log` (25s).
- Five core module test runs: initial fixture failures (null Mockito matcher and concurrent global
  PostgreSQL test state) corrected in tests; `nex-s10-core-tests2/3/4-20260913.log` pass.
- Focused formatting rejects `.kts` paths by design; the first checkpoint invocation failed before
  edits/build, then the manifest was corrected to `.kt` only. `nex-s10-checkpoint2-20260913.log`:
  497 tasks,76 executed,1 cached,420 up-to-date,1m56s; scoped affected checks, Nex/shared tests,
  revision240 Nex cache audit, architecture check and app assemble pass.
- `nex-s10-final-core-20260913.log`: five affected test modules plus app assemble,1m28s,
  471 tasks,18 executed,453 up-to-date; includes final GameServer wiring and all39 core cases.
- `javac --add-modules jdk.attach -d logs/s10-shutdown-helper-tests scripts/CerberusAuditShutdown.java scripts/NativeShutdownGateTest.java`,
  then `java --add-modules jdk.attach -cp logs/s10-shutdown-helper-tests NativeShutdownGateTest`:6 pass.
- `nex-s10-process-tests-20260913.log`: isolated child-JVM regressions and app assemble pass,
  1m34s,461 tasks,14 executed. `nex-s10-native-logout-tests-20260913.log` caught an invalid generic
  runTest call in the new test fixture; corrected without gameplay changes. Follow-up
  `nex-s10-native-logout-tests2-20260913.log`: all44 core cases pass,36s,460 tasks,6 executed.
- `nex-s10-all-boss-regression-20260913.log`: all27 non-diagnostic boss test task selections,
  unscoped Nex check and architecture check pass,1m46s;1,030 result cases,zero failures/errors/skips.
  Unchanged cached results are not new client or gameplay coverage.
- Read-only doctor `nex-s10-doctor-resumed-20260913.log` confirms canonical JVM42364 and revision240.

Remaining S10 acceptance: old-JVM save-first handover; native live logout/admission/acknowledgement
exercise; successful build marker and doctor; two persistence-verified controlled restarts. No live
failure injection, deployed S10, or completed S10 acceptance is claimed here. API/mechanics/tests
review agents remain read-only; the primary agent integrated every source edit.

Official source references:

[GameBootstrap](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/server/app/src/main/kotlin/org/rsmod/server/app/GameBootstrap.kt),
[ServiceManager](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/server/services/src/main/kotlin/org/rsmod/server/services/ServiceManager.kt),
[Service](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/server/services/src/main/kotlin/org/rsmod/server/services/Service.kt).
Existing local account save-gate/autosave and embedded PostgreSQL authority must be preserved;
they are not all upstream APIs. Tests belong in server/services, server/app, api/db, api/account,
api/registry plus Nex/relevant shared regressions. Two controlled restarts remain blocked until
the repair passes tests and native pre-drain can be verified. Approval is no longer a blocker.

## September 12 bodyguard-call and native death correction

The main DSL checkpoint is committed as `8f0a6928c`, with source recovery branch
`backup/nex-main-native-combat-20260912`. Source-only archive
`logs/nex-main-native-combat-20260912.zip`:7,478 entries, zero excluded-path violations;
SHA256 `C945A377EC3E41D1886DB60D14D94C49424AE28308DBABD1557A6ED75ECBBFF4`.
This is not a PostgreSQL backup. Its deployment status is recorded separately below.

M10/M11 correction: a called guard is now encounter coordination state, not an additional
BossEncounter combat phase. Nex retains elemental normals, forced-special count and pending
melee continuation during the call, including at the incoming damage floor. Incoming damage
gating is preserved. Blood/Siphon, Ice and delayed special validation use the actual elemental
phase. The change does not assert that the inherited incoming-damage formulas are correct.

Decoded evidence inspected (Grid Master, NOT normal timing or binary parity):

- `rsprox-3284.txt`:t631 L14926 calls Fumus; t632 L14952 reaches2550, L14953 immunity;
  L14964 Nex9189 and L14984-14986 projectile1997. Outgoing Smoke magic continues after the call.
- `rsprox-808.txt`:t2397 L60383-60384 Choke; ordinary attacks t2401 L60497, t2405 L60673,
  t2414 call L60983/melee L60987, t2418 melee L61085, t2422 L61202; next special t2426
  L61335-L61337. This supports preserving the attack sequence across the call, not copying
  altered-mode attack cadence. All paths are under `logs/nex-audit-20260909/captures/`.

Three executable `NexCalledGuardCombatTest` regressions failed against the pre-correction
implementation (`logs/nex-called-guard-red-20260912.log`). They exercise all four elemental
projectiles/styles at the floor, native ordinary count/continuation, and target reacquisition.

M06/M26 spawn-ownership defect: `NpcRepository.add(npc, Int.MAX_VALUE)` sets respawns=true.
The old Nex code set false BEFORE registration, so native death hid every actor for automatic
respawn while the room separately owned replacement. Helpers now set false AFTER registration
and use native deletion. Main Nex intentionally retains the native hide branch for Wrath source
identity; `deathNoDrops(respawnDelay=Int.MAX_VALUE)` disables the competing automatic reveal.
The room's existing reset then deletes the retained source. No shared API was added here.
Native death/delete workflows are official; the optional respawnDelay parameter is an existing
local extension. Native deathWithDrops itself runs synchronous drop handling after deletion;
the helper callback uses that same supported pattern, with no new suspension after removal.

The first native-death test exposed the overwritten flag. An experimental all-NPC-delete fix
passed two cycles (`logs/nex-native-death-fixed-20260912.log`,49s/162tasks), but review identified
lost Wrath source identity. That intermediate behavior was NOT deployed or retained. Final
coverage instead requires native hidden/dead Nex to remain resolvable through Wrath, no native
respawn timer, eventual room deletion, and two fresh encounter identities. Real helper death
coverage sends simultaneous lethal queued hits through all four guards and checks one phase
advance and removal per guard. No bodyguard death presentation or public auto-entry claim.

M24 evidence rechecked:808 rev234.1 t715 L19223-L19229 transforms11282, t716 L19239-L19245
Wrath dialogue, t717 L19249-L19250 death9184, t720 L19256-L19272 Wrath effects, t723
L19308-L19313 player hit before deletion. Anchor2920,5191/effect center2921,5192. Eleven
Grid Master deaths share death-sequence to FX3/deletion6 observed ticks; these are NOT certified
normal cadence. Current hidden corpse, fixed-spawn Wrath geometry and legacy3+1 queue timing
are explicitly preserved, not represented as visual parity. The source must remain attributable;
HitContext alone cannot restore a removed NPC UID. Exact presentation remains M24 debt.

Wrath validity regression: `logs/nex-wrath-validity-red2-20260912.log` has two tests, one failing
(teleported player incorrectly damaged99->44). The first attempted run failed compilation due
to a test accessing private Hit.sourceUid; the test now uses native resolveNpcSource instead.
Wrath now uses the approved existing native HitValidity path for membership and source lifetime,
including departure before the room post-tick. Its positive test checks both original UID
resolution and PlayerHitpointsChangedEvent.source at actual native player impact.

Final scoped formatting/check and affected-module verification passed1m36s/478tasks:
`logs/nex-main-final-checkpoint-20260912.log` (17 executed,461 up-to-date).
Tasks: Nex spotlessApply/check, `:or-cache:test --tests dev.openrune.NexR240CacheAuditTest`,
`:api:bosses:test`, `:api:player:test`, `:api:combat:combat-scripts:test`, `:api:npc:test`,
`:api:instances:test`, `:api:death:test`, `:server:app:assemble`, `bossArchitectureCheck`.
The format manifest is `logs/nex-main-format-20260912.txt`, used with
`-I scripts/format-selected.init.gradle -PopenruneFormatManifest=logs/nex-main-format-20260912.txt`.
All commands use `--no-daemon --console=plain`. Result XML: Nex54, boss API61, player20,
combat-scripts8, NPC12, instances34, death6, cache4 =199; zero failures/errors.
Manager lines:1,236 (initial inventory1,321; minion checkpoint1,308). Retained specialized
coordination is still migration debt, not hidden by the line-count reduction.

## September 12 main DSL integration checkpoint (source; deployment recorded separately)

S09 was approved and committed as `8b08bb84a`; optional shared S01 late-prayer policy is
`e2c28eb33`. Neither is labelled official upstream. The current focused branch is
`refactor/nex-main-native-combat-20260910`; pre-S09 recovery refs are
`backup/nex-before-s09-20260910` and `backup/boss-selector-continuation-20260910`.

The new `NexBossSpec` registers forms11278-11282 once through BossCombat. Ordinary melee,
magic and ranged attacks use Effect.NpcAttack/NvPCombat, with the same characterized maxima,
animation IDs, projectile definitions and prayer reductions. BossEncounter owns phase state,
ordinary selection, two same-target melee continuations, five-normal forced-special cadence,
and the twelve-opportunity Zaros overhead rotation. Removed the private attackNex/attackZaros,
melee/magic/ranged launch methods, normal counter, Zaros counter, melee-chain fields and
specialLockedUntil attack timer. The former pure Zaros arithmetic test is replaced by executing
24 registered attacks, asserting visible forms, immutable UID and live hit validity.

Named external handlers retain only normal-hit side effects, a one-use encounter-owned magic
launch snapshot, post-primary secondary splash, special alternation/dispatch and idempotent
native form presentation. They do not choose or schedule primary ordinary attacks. Retaining
`npc.transmog` is intentional: local Effect.Transmog invokes changeType/assignUid, which would
change the encounter identity. The run reads its phase from BossEncounter, not a second phase
variable. Public-room membership, environmental mechanics and reward/death integration remain
explicit migration debt; this is NOT full Nex completion.

Two explicit safety/geometry corrections accompany the wiring: all ordinary fired hits use the
native encounter attachment scope plus stable participant identity; raw disconnect, teleport,
logout, death and deletion invalidate them. Melee eligibility uses the existing footprint-aware
DSL condition instead of anchor-only distance. Four cardinal sides of Nex's3x3 footprint are
tested. This condition alone does NOT prove collision-sensitive contact/pathfinding parity.

The parameter resolver snapshots launch phase and prayer, and arms the secondary handoff only
after native beginAttack accepts. The trailing handler consumes it once, after primary RNG/FX,
including primary misses. Secondary hits now receive room/participant validity. Their existing
launch-only prayer handling and lack of separate accuracy remain explicitly unresolved; final
primary magic alone uses native deferred late-prayer evaluation. No damage or drop formula was
changed to hide these discrepancies.

Evidence: O1 was re-opened September12 and still documents two additional melee attempts,
late25% final magic and the lower-floor example. Existing five main characterizations remain
the migration behavior baseline, not proof of hidden OSRS random weights. C-GM3284/808 decoded
evidence remains Grid Master, not normal-mode or raw-binary parity. Capture-backed called-guard
corrections are a separate step, not silently mixed into this checkpoint.

Initial formatting invocation failed because its manifest named the not-yet-created new test;
no production/test failure occurred in that invocation. After creating it, all44 Nex tests
passed (`logs/nex-main-second-20260912.log`,58s/165tasks). Team coverage then passed47 tests
(`logs/nex-main-team-20260912.log`,59s/165tasks): primary miss with splash, one-use handoff,
secondary disconnect, primary-vs-secondary late prayer and player target replacement.
Broader checkpoint passed in1m51s/476tasks (148 executed,328 up-to-date):
`logs/nex-main-checkpoint-20260912.log`. It ran scoped Nex formatting/check, Nex cache audit,
boss API/player/combat-scripts/NPC/instance tests, application assemble and architecture check.
Result XML: Nex47, boss API61, player20, combat-scripts8, NPC12, instances34, cache4 =186,
zero failures/errors. Separate unscoped Nex check passed25s/165tasks (tests up-to-date),
`logs/nex-main-unscoped-20260912.log`. Full unscoped boss API
formatting still has the documented eight untouched baseline violations. No failing gameplay
build was deployed. Deployment results follow only after actual controlled restarts.

## September 10 deployed checkpoint and next main-loop boundary

**Historical September 10 production source: `41fd77bff`, minion DSL migration only.**
The September 13 continuation above supersedes this deployment status.
Preceding commit `6bf7424fa` only formats three existing NexRewards expressions. Both are on
`refactor/nex-native-minions-20260910`; no remote push, merge or PR was made.

### Deployment, recovery and persistence

- Source backup branch: `backup/nex-native-minions-20260910` at `41fd77bff`.
- Source-only archive: `logs/nex-native-minions-20260910.zip`, 7,475 entries, zero excluded-path
  violations. SHA-256: `DD14CF0BD0744A7E973BDFF05CE44395569974D9A3FF780E456F757C6798CEB8`.
  It excludes credentials, `.data`, logs and generated output; it is not a database backup.
- Controlled rebuild/assemble passed before deployment. Native orderly shutdowns/restarts:
  JVM 21724 -> 17376 -> 27808. No established game client was connected at either shutdown.
  No hard kill or manual player-data write was used.
- `OPENRUNE CUSTOM BUILD ACTIVE` appeared at 13:02:40 and 13:05:34 EDT, September 10.
  Startup completed and Nex's IDLE room spawned at 13:02:42 and 13:05:36 respectively.
  Logs: `logs/cerberus-audit-20260907/restart-2026091007.*.log` and `restart-2026091008.*.log`.
- `logs/nex-minion-doctor-restart1-20260910.log` and `...restart2...` confirm the canonical
  working directory, revision 240, SERVER/LIVE and project PostgreSQL authority. `jcmd`
  class-hierarchy checks confirm `NexMinionSpecKt` is loaded in both replacement JVMs.
- Fresh read-only SQL audits before, after restart 1 and after restart 2 all match:

| Persisted scope | Rows | SHA-256 |
|---|---:|---|
| Inventory containers | 23 | `0312a19af5d3db794f34a8beb4a6864f0cf4db8ca36b5010fd9927065d0d72ec` |
| Inventory items, including banks | 1,153 | `343de321331febedd4e1aaa551f3e232cd2799959ee8289b1011872e2867144b` |
| Realm spawn/respawn | 1 | `c971b1f6d8e431e9f25d64c6dc154d7c60ff22ce7c9e20274a73f671e911ef50` |

Audit command: Java 21, PostgreSQL JDBC from the existing app install, and
`scripts/BossMigrationPersistenceAudit.java`. Logs are
`logs/nex-minion-persistence-{before,restart1,restart2}-20260910.log`; no account contents printed.
Post-restart cache dat2 digests also match the pre-work fingerprints:
SERVER `D01985ED94C9814DBF99509B335E964C69F1FF91C2464A5165D42A78E1915D75`,
LIVE `FD0FE5EF66039A791A900EDDDF1CDBCFE57E09E8C32EA3496C69E96217A41A7B`.
Startup emitted isolated long-cycle warnings (747/723 ms); shutdown helper emitted the JDK's
dynamic-agent warning. Neither was represented as a gameplay test or silently treated as a crash.
No rendered-client Nex fight or OSRS/OpenRune packet comparison was performed in this pass.

### Main-combat characterization after deployment (test-only)

`NexMainCombatCharacterizationTest` adds five executable tests before replacing the main loop:
all five phase normals with launch prayer on/off; three consecutive same-target melee selections
with one random draw; a forced special interrupting an unfinished melee chain; range exit clearing
the chain without a draw and re-entry selecting afresh; and all four launch/impact-prayer
combinations for final magic. Tests execute production registration, native queues, hit modifiers
and HP processing. They are characterization of the draft, **not OSRS certification**.

The fixture now runs native `NpcPostTickProcess` (including facing and pending-animation cleanup),
captures injected style-selection draws, and inspects `DeferredPlayerHit` separately from already
modified hits. Early test failures exposed three incorrect fixture/expectation assumptions:
Smoke's native poison adds an immediate 8 damage; old pending sequences must be cleared between
frames; final-phase magic is deferred until impact and is not a premodified `Hit` queue argument.
No production constants were changed to make those tests pass.

All 35 Nex tests passed in 31 seconds / 165 tasks including scoped formatting:
`logs/nex-main-characterization-verified-20260910.log`. This is five more than the deployed
minion checkpoint's 30 tests. Tests do not prove bodyguard death timing (phase setup calls the
existing handoff hook), normal OSRS random weights, target/pathfinding parity, visual playback,
or the main loop's complete departure cancellation. Those remain implementation work.

Final unscoped checkpoint command:

```powershell
.\gradlew.bat :content:bosses:nex:check :or-cache:test --tests dev.openrune.NexR240CacheAuditTest :api:bosses:test :api:instances:test :api:combat:combat-scripts:test :api:npc:test :api:player:test :server:app:assemble bossArchitectureCheck --no-daemon --console=plain
```

Passed in 21 seconds / 475 tasks (4 executed, 471 up-to-date),
`logs/nex-native-checkpoint-final-20260910.log`. Result XML totals: Nex 35, cache 4,
boss API 50, instances 34, combat-scripts 8, NPC 12, player 17: **160 tests**, zero failures,
errors or skips. Up-to-date suites are identified honestly; they were not all rerun unnecessarily.
`git diff --check` passes. The unrelated four modified `.data` files and untracked GE area remain
untouched and outside the focused source commits. Post-deployment changes here are tests/docs only.

Test/document checkpoint: `7cbd04d2b`, backed up as
`backup/nex-main-characterization-20260910`. Source ZIP
`logs/nex-main-characterization-20260910.zip` has 7,475 entries and no excluded-path violations;
SHA-256 `07C7AAB412B21C174E5C5DE118BC5ADAFA33EB233728283874A8ECCEA8D90BA4`.
It excludes tracked tool archives as well as credentials/runtime/build data. Production remains
`41fd77bff`; no further server restart is needed for these test/document-only changes.

Focused files changed in this continuation:

- Nex production: `NexCombat.kt`, `NexEncounterManager.kt`, new `NexMinionSpec.kt`;
  `NexRewards.kt` has formatting only.
- Executable tests: `NexMinionCombatCharacterizationTest.kt`, new
  `NexMinionLifecycleIntegrationTest.kt`, new `NexMainCombatCharacterizationTest.kt`, and
  `or-cache/src/test/kotlin/dev/openrune/NexR240CacheAuditTest.kt`.
- Documentation/checkpoint wiring: this ledger, `docs/bosses/DSL_MIGRATION.md`,
  `MIGRATION_HANDOFF.md`, `RESUME_OPENRUNE_BOSS_API_DSL.txt`, `architecture-waivers.json`,
  and `scripts/resume-boss-migration.ps1`.

### S09 approved: target-bound ordinary-attack continuation

Approved S01 implementation, September 10: the existing shared
`NpcCalculatedProtectionPrayerPlayerHitModifier` now accepts optional `lateReductionPercent=0`.
Default launch-only behavior is unchanged; a matching late prayer is sampled when the modifier
is invoked, with the launch reduction taking precedence. Nex primary final magic uses the
existing native deferred-impact queue. All 20 player API tests pass, including three new late
policy tests; `logs/nex-s01-verified-20260910.log`. This is an approved local shared extension
of A03, not a claim that its signature exists upstream. No new prayer or hit pipeline was added.

**User approval received September 10; shared implementation tested. This is a new shared extension,
not an existing official upstream API.**
The shared checkpoint adds 11 executable tests (61 boss API tests total); all 35 Nex tests,
application classes and architecture check pass. Log: `logs/nex-s09-verified-20260910.log`.
Formatting passes for the changed files. An additional unscoped `:api:bosses:check` fails on
pre-existing formatting in eight untouched files (DragonDsl, DragonDslHigh, EffectBuilders,
StatDrainBuilder, BossFx, BossPluginScript, DamageExpr, TargetExpr); see
`logs/nex-s09-unscoped-20260910.log`. Those files were not mass-formatted.
No deployment of S09 has occurred at this checkpoint.
The existing M01-M36/S01-S08 approval remains valid. S09 is not retroactively folded into S07
(RNG injection); it changes selection expressiveness and its separate AGENTS checkpoint is now
satisfied. Do not request this approval again. Preserve defaults for other selectors. Source scope
also includes the existing SpecValidator's exhaustive selector validation and focused runtime tests;
these are integration of the approved selector, not another selection or validation pipeline.

Requirement: [Jagex, June 25, 2025](https://secure.runescape.com/m=news/summer-sweep-up-combat?oldschool=1)
documents two additional melee attempts when the target is within melee distance. Confidence:
officially documented for that requirement. The draft's one-in-three initiation, same-target UID
retention, eligibility sampling on ordinary opportunities and special-interruption behavior are
current implementation/characterization, not independently proven hidden OSRS algorithms.
Preserve those explicitly while separating any subsequent evidence-backed behavior correction.

Searches performed before proposing this addition:

- Complete local `api/` and `content/` selector/continuation references and tests; the only
  `meleeChain` state is Nex's private manager. No `RepeatSelected` implementation exists.
- `git grep` at official `upstream/main` commit `61139e76a216592d66c2b20366ef26d48609875d`;
  direct inspection of upstream/local BossEncounter, BossCombat, Selector and Condition;
  relevant selector Git history (`fa793579c`, `f9dd030fc`).
- Exact official declarations: [Selector](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/bosses/src/main/kotlin/org/rsmod/api/bosses/spec/Selector.kt),
  [Condition](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/bosses/src/main/kotlin/org/rsmod/api/bosses/spec/Condition.kt),
  and A01's BossEncounter/BossCombat references. Official weighted/rotation/conditional selectors
  lack prior-target and bounded-continuation state. Local MeleeOverrideRotation and
  AbilityCountModulo do not express it either.
- Official Scurrius remains the workflow/style reference (A05). The completed **local** Vorkath
  `VorkathCombatSpec` special registration and `VorkathArenaEffects.beginSpecial` show an acceptable
  tiny encounter-owned special alternation handler; they are not labelled official upstream.

Why existing alternatives are insufficient: `forceNextAbility` bypasses native ordinary attack
counting and takes priority over the scheduled special. `Repeat(3, gap=4)` is one ability commit,
not three independently gated normal opportunities. A phase per chain length resets forced cadence.
A content callback that picks ordinary melee/magic would simply preserve the private selector.
An outer `Whenever(range)` would hide a range exit from retained selector state. A weighted melee
entry alone would consume randomness even outside melee range, unlike the characterized draft.

Smallest proposed owner/change: extend **existing** `api/bosses/spec/Selector.kt` and
`runtime/BossEncounter.kt`, with focused tests under `api/bosses/src/test/`. Optional builder syntax
belongs only in the existing DSL builders if needed. No hit, movement, queue, instance or engine
replacement. Proposed `Selector.RepeatSelected` wraps an existing selector, with a repeated ability,
extra-selection count, eligibility Condition, deterministic ineligible fallback and same-target
flag. On each ordinary opportunity it checks eligibility first, clearing the chain and returning
the fallback without RNG if false; otherwise it continues the same immutable target UID without
RNG, or draws from the delegate and arms a fresh chain only for the named repeated ability.
State belongs to BossEncounter.SelectionState, not a content counter, player object or global map.
Forced specials retain their existing precedence and do not consume the chain. Each returned
ordinary attack, including fallback, passes normal basic-attack counting and native hit execution.
Phase transition/end/deletion must clear it; delegate state must not alias continuation state.
Other selectors keep their existing behavior. Names and source signatures remain proposals.

| Next main-loop behavior | Native owner / classification | Planned implementation and regression |
|---|---|---|
| Ordinary attacks, four-tick readiness, accuracy, projectiles, impacts | A01/A02 official workflows; approved local Effect.NpcAttack adapter | Declarative phase abilities; retain exact launch/HP characterizations and native attack metadata |
| Bounded melee continuation and no unnecessary choice RNG | Proposed S09 | Selector state above; no manager melee counter; same/different target, range exit/re-entry and no-extra-draw tests |
| Five normals before special | Official forced-ability workflow, local injected GameRandom | forceEveryAttacks(5,5); prove a special can interrupt the chain without changing its remaining normals |
| Alternating phase specials across handoffs | Official named external-handler workflow, approved local encounter-owned state | Tiny special coordinator retains only encounter-wide parity; DSL owns when it runs; no second normal selector |
| Zaros four/four/four overhead order | W2 plus official Rotation, local nested selection | Declarative 12-opportunity cycle and idempotent transformation handler; native hit callbacks for source-tagged effects |
| Launch/late protection | A03 and already approved S01, not a newly discovered gap | Preserve native queued/deferred processing; the five characterization tests include all launch/impact combinations |
| Called-guard phase and range geometry | M10/M11/M25, existing native reach/phase workflows | Explicit separate corrections: current lower-floor attack suppression is capture-contradicted, and anchor range is not native footprint reach |

S09 acceptance tests: injected RNG bounds, two extra same-target selections, range exit without
selection RNG, re-entry fresh roll, changed target/fresh chain, special interruption, normal-count
fallback, no cancellation for movement entirely between selection opportunities, and phase/end/
deletion/slot-reuse isolation. These are now executable S09 tests; see the September12 checkpoint.

## September10 native minion combat implementation

Current continuation branch: `refactor/nex-native-minions-20260910`, based on Royal Titans
checkpoint `30e94ae13`. Recoverable pre-work branch: `backup/nex-before-minion-dsl-20260910`.
The latest user priority is Nex. Older checkpoint sections below are retained history.

Implemented the previously approved first bounded mapping: two BossSpecs register all four
bodyguards and encounter Reaver11294 through BossCombat. The shared DSL/native NvPCombat pipeline
now owns their ordinary attack selection, five-tick readiness, animation/projectile dispatch,
accuracy, prayer reduction and queued damage. Removed both manager attack implementations; no
parallel legacy minion dispatcher remains. Guards retain movement locks; Reavers retain native
movement. The only named parameter resolver captures room/participant identity for hit validity.
The existing manager retains phase eligibility and bespoke room state pending its next migration.
No shared engine/API source, cache configuration, reward formula or balance constant changed.
Manager size falls1,321 ->1,308 lines; this modest decrease is not a measure of whole-fight completion.
The architecture detector's direct-combat allowance reduces15 ->14; the remaining override in
NexCombat is only the named parameter resolver. Existing other Nex debt remains explicitly waived.

Reuse classification remains explicit: BossCombat/BossSpec/EncounterRegistry/NvPCombat are official
workflow families (A01/A02). Effect.NpcAttack, BossAttackParameters, canTarget and scoped cancellation
are the **already approved local shared-framework extensions**, not upstream public APIs.
No additional extension or private combat scheduler was introduced. Main Nex still has its private
loop; this checkpoint must not be called a complete Nex DSL or instance migration.

Nine new production integration tests exercise real NPC registration/repositories and player hit
queues/HP processing: stationary guards, native Reaver ownership, damage impact, exits, external
teleport before room post-tick, pending/final logout, target death, source deletion, actual NPC slot
reuse, player slot replacement, idempotent teardown and fresh encounter state. Fired magic retains
the existing native source-death/phase-handoff policy until deletion. An unrelated Reaver cannot
borrow an active room's registration. Tests do not simulate a real network disconnect or claim
complete native guard death animations/room teardown.

Regression-first evidence: after fixing two test compilation mistakes, the unmodified production
handlers failed5 of10 minion tests (registration, departure, target replacement, deletion, teardown),
`logs/nex-minion-regression-before-20260910.log`,29s/162tasks. The first DSL implementation passed
all10 in33s/162tasks (`logs/nex-minion-dsl-first-20260910.log`). Two additional ownership/death tests
then passed. All30 Nex tests and4 new executable cache tests pass, zero failures/errors/skips:
`logs/nex-minion-dsl-cache-verified-20260910.log`,34s/169tasks, including explicitly scoped formatting.
The first cache run corrected an assumption: death form11282 still has Attack in its definition;
runtime operation masks, not absence of the cache option, must gate it.

The new `or-cache/.../NexR240CacheAuditTest.kt` decodes native SERVER definitions and actual LIVE
model/sequence/interface payloads. It covers five Nex forms, four guards, encounter Reaver, pet/POH
distinction, guard/Reaver stats and locomotion, sequences9177-9203/1979/836, graphics1997-2017 and
368/372/390 with linked sequences/models, ten packet sound assets, barriers/hazards/altar options
and collision definitions, statistics732 component existence, key/shard identities and relevant
varbit existence. It does NOT yet prove interface text, all reward/CA metadata, packet timing,
native actor pathing, new game-value behavior or rendered client parity. E09 is partially addressed.

### Retained minion presentation contradictions (not silently changed by migration)

The characterization still deliberately asserts legacy20/18 maxima, animation811, guard projectile
1999, no authored Reaver projectile and50% launch prayer. These are not OSRS values. The parallel
read-only follow-up strengthened M11/M17 correction evidence:

| Actor | Capture-observed presentation | Exact retained transcript evidence |
|---|---|---|
| Fumus11283 | Cast1979, projectile390, impact391 | 3284 t406:L9769,L9774,L9777-L9788; projectile start51/end76, angle16, progress64, heights172/124; sound183, delayed180 |
| Umbra11284 | Cast1979, impact383, no outgoing projectile in this sample | 808 t216:L6373-L6391; impact delay60, sounds178/175 |
| Cruor11285 | Cast1979, impact377, no outgoing projectile in this sample | 808 t256:L7615-L7643; impact delay60, sounds106/102 |
| Glacies11286 | Cast1979, projectile368, impact369 | 808 t349:L9834-L9856; start51/end106, angle16, progress64, heights172/0; sound171 |
| Reaver11294 | Attack9194, projectile372, impact373 | 808 t231:L6654-L6680; start42/end102, angle4, progress64, heights0/10; sound106 delay30,110 delay102 |

Both captures are **revision234.1 Grid Master**, decoded text inspected, not newly decoded raw
binary or normal-mode timing certification. These distance-specific samples are not universal
travel formulas. Animation811 belongs to ordinary ancient Saradomin wizards2209 (3284 t185:L6371-81).
Projectile1999 belongs to Nex Shadow (3284 t424:L10258-77), not a generic guard attack.
Reaver spawn:3284 t444:L10891-10907,11294 at2934,5212 with2017 and sound4497; attack9194 follows
t445:L10940-62. Reaver death9192 t455:L11206-11, deletion t458:L11300, same index58764.
Guard death836 then phase call then removal: Fumus407/408/409, Umbra439/440/441,
Cruor485/486/487, Glacies518/519/520 in3284; see retained minion follow-up packet ranges.

Wiki revisions15221135/15221134/15221130/15221131 document guard max29 and speed5;
Reaver15305108 documents max20 and speed5. SERVER guard attack_anim5247 also contradicts captured1979;
the cache test intentionally does not certify5247 as correct. Reaver LIVE stab defence0 versus
Wiki20, official player-heal75% versus Wiki50%, helper prayer fractions and normal travel formulas
remain unresolved. No balance or healing change is implied by these observations.

Next architecture work: characterize/migrate Nex ordinary phase rotation, then public-room/session
ownership under the existing mapping. Do not mark main phases, lifecycle, rewards, persistence,
Combat Achievements or client/packet comparison complete on the strength of helper tests.

Unscoped `:content:bosses:nex:check` initially found five pre-existing Nex formatter violations.
Only those files were selected for formatting: four line-ending-only fixes (Git-normalized content
unchanged) and three expression wraps in NexRewards. No reward logic changed. Final unscoped check,
shared boss/instance/combat-scripts/NPC/player tests, server app assemble and architecture enforcement
pass: `logs/nex-minion-broader-final-20260910.log`,1m15s/473tasks. Shared totals:50/34/8/12/17 tests,
zero failures/errors; including Nex30/cache4 gives155 tests. No shared gameplay source changed.

## September10 architecture-migration refresh

The all-boss migration now takes precedence for architecture sequencing; the original approved
M01-M36/S01-S08 mapping and E01-E09 evidence restrictions below remain intact. Royal Titans source
1279d1676 is deployed and backed up; Nex still has its1,321-line private manager and18 tests.
No Nex gameplay change was made during this three-agent read-only refresh. The Wilderness branch
is already removed; S02 native queue tests are probes, not production Nex integration. Do not redo
the removal, advertise those probes as gameplay coverage, or change unresolved formulas silently.

First bounded migration mapping: characterize the actual registered guard/Reaver attack handlers,
then move ordinary attack execution/cadence to BossCombat/BossSpec/Effect.NpcAttack. Preserve the
existing generic20/18 maxima,811 animation, guard1999 projectile/magic_spell controller, five-tick
cadence and50% prayer rule as **legacy characterization**, not verified OSRS values. Their known
definition/mechanical corrections remain M11/M17 and must be separate evidence-backed changes.
Keep current phase/member gates, but use the existing native encounter/target hit validity for
departure/deletion cancellation. BossSpec guards must retain stationary movement; Reavers retain
native movement. Tests must execute production registration and NvPCombat, assert exact queued
damage/cadence/presentation, cooldown rejection and cancellation. No new shared API is needed.

Classification: BossCombat/BossSpec/EncounterRegistry and native NvPCombat are official workflow
families at upstream61139e76a (A01/A02 links below). Effect.NpcAttack, BossAttackParameters,
BossPhaseClock, canTarget and HitContext/owned cancellation are existing **local migration
extensions**, not official upstream public APIs. Reuse these approved extensions rather than
creating another combat loop. Repository and exact upstream BossCombat/Scurrius source inspected.

Public-room ownership needs an explicit subsequent mapping: Nex currently occupies the fixed
Ancient Prison, whereas both local/upstream InstanceArea only support allocated Template or
CopyRegions rooms. Official Scurrius uses a copied server-owned public room; the local fixed-room
Scurrius adaptation is not upstream. Do not silently move Nex into a copied room or invent an
existing-region API. Preserve the public room through native spatial/BossEncounter ownership if
its barrier/spectator semantics can be retained; otherwise map the smallest reusable session
attachment capability before shared edits. This does not block the bounded helper combat step.

Retained capture refresh (decoded C-GM, all revision234.1; not normal timing certification):

- All346 special calls still support encounter-wide alternating parity, reset only on new kill.
  Called-bodyguard states must retain the underlying elemental phase:3284 t631:L14926 calls Fumus;
  t632:L14952 reaches2550 but normal9189/1997 follows atL14964/L14984; Dash t636:L15076 executes
  t641:L15215 while Fumus lives. Current attack suppression at the lower floor is contradicted.
- All8 guard handoffs in3284 order death presentation, phase call, then guard removal. Example
  Fumus t645:L15311, shadow t646:L15321, removal t647:L15351. Do not wait for removal to advance.
- Wrath uses actual death center2917,5192 (anchor2916,5191):3284 t542:L13917-27 dying transform;
  t543:L13942 death; t546:L13960-80 effects; t549:L13987-14017 reward/barrier then removal.
- Natural repeat must retain occupants:3284 completion549, fresh opening587, completion790 with
  no re-entry. Current reset creates an empty IDLE run; the startup test cannot catch this.
- Prison rescue must use the clicked segment's owner, not lowest UID;808 t1941:L49885-93 removes
  only2935,5195, with remaining ring to1945:L49928-34. Absent prisoner state must not later shatter.

No new raw packet decoding, normal-game capture, executable Nex cache audit or Nex client/packet
comparison was completed. E02 player-healing75% official/50% Wiki conflict and E03 damage formula
uncertainty remain. Do not label either resolved merely to advance the architecture migration.

### Minion characterization checkpoint (no production changes)

Branch `refactor/nex-boss-dsl-20260910` adds NexMinionCombatCharacterizationTest before replacing
any manager attack. It executes the actual NexCombat registration, native NpcRegistry/Repository,
StandardNpcAccessLauncher, NvPCombat, NPC hit queue/processor and injected clock/RNG. Assertions
cover all four called guards with/without launch prayer, retained projectile-derived hit delay,
animation811, current20/18 maxima, five-tick cooldown rejection without additional accuracy RNG,
inactive/outside targets, native accuracy misses, and a Reaver spawned by the production Siphon
path. These legacy values are characterization, not OSRS evidence. World output is recorded by a
mock; player hit payloads are inspected rather than processed to HP. Guard handoff is setup via
the existing production hook; no complete death/lifecycle or pathing parity is claimed.

First run:3 tests pass,36s/162tasks, `logs/nex-minion-characterization-first-20260910.log`.
Scoped formatter/check, all21 Nex tests, shared boss/instance suites, server app classes and
architecture enforcement pass in37s/458tasks (6executed452up-to-date), zero failures/errors/skips:
`logs/nex-minion-characterization-verified-20260910.log`. Test-only dependency additions expose
the existing native combat-formula and registry fixtures; no runtime dependency or API changed.
Next: add departure/deletion regressions, then migrate ordinary helper attacks per the mapping
above. Live remains Royal Titans source1279d1676; these test-only changes require no new restart.

## Status: approved implementation in progress, 9 September 2026

**S02 foundation and M35 removal implemented below; full Nex integration and runtime verification remain incomplete. Not ready for a complete Nex fix commit; not 1:1 OSRS.**

This revision supersedes the earlier draft's unsupported implementation/parity claims. It records
the existing defects, actual evidence, official/local API distinctions, proposed changes and
executable verification required before a completion claim. Nothing below marked "planned" has
been implemented. Root `AGENTS.md` explicitly requires approval of this mapping before combat edits.

The user has now approved all Nex work and directed that the rune-pouch display correction be
finished first, followed by Nex. **M01-M36 / S01-S08 are approved**; the same approval will not be
requested again. Evidence conflicts E01-E09 are not resolved by permission to implement. The audit
results below describe the pre-implementation checkpoint, not a completed Nex deployment.

The canonical workspace is
`C:\Users\norma\Documents\Codex\2026-08-10\lets-work-on-the-openrune-server\work\OpenRune-Server`.
At the initial audit checkpoint, the doctor confirmed server JVM **32336** in this directory, revision **240**,
server definitions `.data/cache/SERVER`, JS5 `.data/cache/LIVE`, and PostgreSQL `.data/postgres`.
No cache-path/revision change, deployment, database migration or restart was made during that initial audit.
Subsequent rune-pouch deployment and the current Nex source-only changes are distinguished below.

Pre-existing work is not Nex work: all **205** files in the initial dirty/untracked source hash
snapshot still matched before the initial documentation update. Nex source and this ledger had no
pre-existing diff. That read-only audit changed only this ledger and focused ignored research evidence.
No stage, commit, push, PR, source restoration or duplicate Nex implementation.

Evidence directory: [logs/nex-audit-20260909](../../../logs/nex-audit-20260909/).
Initial doctor: [doctor-before](../../../logs/nex-20260909-doctor-before.log).
Three explicitly requested **read-only** agents audited architecture, captures, and current Wiki/cache.
The primary performed evidence acquisition/transcription and owns all integration decisions/edits.

## Evidence labels and restrictions

- **O**: official Jagex documentation; authoritative for documented changes, not hidden algorithms.
- **W**: current Wiki documentation, pinned below; conflicts remain explicit.
- **C-GM**: observed in a decoded Grid Master capture; identities/visible event order are usable
  research evidence, but normal damage, drop rates and combat timings are **not certified**.
- **K**: revision-240 cache identity/definition inspection; a cache identity does not prove mechanics.
- **U**: verified official upstream OpenRune source; architectural evidence, not OSRS proof.
- **L**: existing local/fork source; never labelled official merely because it lives under `api/`.
- **I / X / ?**: inferred / contradicted / unresolved.
- "Tested" and "runtime verified" require their own retained results. Startup, constants and
  nonempty transcripts do not establish gameplay, visuals or raw packet parity.

### Current external sources

| Key | Exact reference | Use / limitation |
|---|---|---|
| O1 | [Summer Sweep Up: Combat, 25 June 2025](https://secure.runescape.com/m=news/summer-sweep-up-combat?oldschool=1) | Two additional melee attempts while target remains reachable; lower phase floor example 2720 to 2550; final magic late-prayer reduction 25%; Reaver text says 75%, disputed by W1. |
| O2 | [Nex Drop Table Changes & Monster Examine, 13 January 2022](https://secure.runescape.com/m=news/nex-drop-table-changes--monster-examine?oldschool=1) | Historical drop topology; do not retain subsequently superseded unique rate. |
| O3 | [Guardians of the Rift Improvements, 30 March 2022](https://secure.runescape.com/m=news/guardians-of-the-rift-improvements?oldschool=1) | Restores unique table chance to 1/43. |
| W1 | [Nex, revision 15325291](https://oldschool.runescape.wiki/w/Special:PermanentLink/15325291) | Current stats/rewards/history; snapshot dated 30 August 2026. |
| W2 | [Nex/Strategies, revision 15331654](https://oldschool.runescape.wiki/w/Special:PermanentLink/15331654) | Current phase mechanics/targeting/access; dated 5 September 2026. Some statements conflict with older official updates. |
| W3 | [Ancient Prison, revision 15205428](https://oldschool.runescape.wiki/w/Special:PermanentLink/15205428) | Access/map context; redirect/transclusion targets require separate verification where applicable. |
| W4 | [Blood Reaver, revision 15305108](https://oldschool.runescape.wiki/w/Special:PermanentLink/15305108) | Distinguishes prison11293 from encounter11294; current healing text and toxin susceptibility. |
| W5 | [Nexling, revision 15336495](https://oldschool.runescape.wiki/w/Special:PermanentLink/15336495) | Pet attribution/reclaim context; dated 9 September 2026. |
| W6 | [Fumus15221135](https://oldschool.runescape.wiki/w/Special:PermanentLink/15221135), [Umbra15221134](https://oldschool.runescape.wiki/w/Special:PermanentLink/15221134), [Cruor15221130](https://oldschool.runescape.wiki/w/Special:PermanentLink/15221130), [Glacies15221131](https://oldschool.runescape.wiki/w/Special:PermanentLink/15221131) | Bodyguard stats and independent ceremonial drops. |
| W7 | [Ancient Barrier transcript, revision14745459](https://oldschool.runescape.wiki/w/Special:PermanentLink/14745459) | Menu/warning/fee text; older transcript is not proof of every current CA-tier fee. |

Additional official history inspected:

- [Nex release, 5 January2022](https://secure.runescape.com/m=news/nex-the-fifth-general?oldschool=1): public room cap60, final Soul Split opening, logout/disconnect return and masks.
- [Nex Changes & Tweaks, 12 January2022](https://secure.runescape.com/m=news/nex-changes--tweaks?oldschool=1): private cap20, creator Hard CA tier, mutual friends online/same world; loot under Nex for teams below20, under players for20+; phase special-order statement.
- [Easter2022, 13 April](https://secure.runescape.com/m=news/easter-2022?oldschool=1): duo Reaver spawning fix; Nex Master now25KC.
- [More Poll79 Changes, 21 June2023](https://secure.runescape.com/m=news/more-poll-79-changes?oldschool=1): appropriate masks/Slayer helmets prevent secondary player-to-player cough infection.
- [While Guthix Sleeps, 10 July2024](https://secure.runescape.com/m=news/while-guthix-sleeps?oldschool=1): invulnerable bodyguards must not accept burn damage.
- [Renderer beta update, 9 July2025](https://secure.runescape.com/m=news/the-new-renderer-beta-is-out-today?oldschool=1): cough persists into final phase, allowing the doctor CA.
- [Summer Sweep Up: Miscellaneous, 2 September2026](https://secure.runescape.com/m=news/summer-sweep-up-miscellaneous?oldschool=1): invalid Nex PBs from minion-damage interactions; screen affected historical PB evidence.

W3 is a disambiguation page, not a complete access source. The relevant follow-up is
[God Wars Dungeon, revision15299834](https://oldschool.runescape.wiki/w/Special:PermanentLink/15299834).
Natural barrier and altar sources are [Ancient Barrier15333724](https://oldschool.runescape.wiki/w/Special:PermanentLink/15333724)
and [Altar15329763](https://oldschool.runescape.wiki/w/Special:PermanentLink/15329763).
Current CA reward pages explicitly distinguish private cost/essence tiers:
[Hard15321192](https://oldschool.runescape.wiki/w/Special:PermanentLink/15321192),
[Elite15321189](https://oldschool.runescape.wiki/w/Special:PermanentLink/15321189),
[Master15329081](https://oldschool.runescape.wiki/w/Special:PermanentLink/15329081),
[Grandmaster15321195](https://oldschool.runescape.wiki/w/Special:PermanentLink/15321195).
They document private fees150k/125k/100k/75k and essence35/30/25/15 respectively; base essence40.
These are Wiki-supported tier rules, not capture-observed transactions. Reclaim100k and subsequent
unsafe-death item loss likewise require native integration and transaction tests.

Raw Wiki API responses, revision IDs and timestamps are retained in `wiki-*.json` in the evidence
directory. The browser reader failed on some Wiki pages; the public MediaWiki API succeeded.
No private-server code is used as behavioral authority.

### Material conflicts / rules not yet approved as constants

| ID | Conflict or missing evidence | Decision |
|---|---|---|
| E01 | No complete **normal-game** Nex capture found. | All normal timing/geometry distributions still require corroboration. Never transplant Grid Master damage or timing as normal truth. |
| E02 | O1 says Reaver healing75%; W1 explicitly annotates50% as a correction of the news-post typo; W2/W4 also say50%. | Prior task-specific75% instruction is superseded. Current target is unresolved at the official-source conflict boundary; proposed50% remains explicitly Wiki-supported until correction/capture proof resolves it. Do not claim the current75% test proves anything. |
| E03 | Draft soft cap50 plus one-third excess, and10% damage beyond threshold, were not substantiated by current source searches. O1 proves extra floor allowance, not these formulae. | Do not silently carry them forward or infer their removal solely from absence in prose. Obtain impact samples/current authoritative confirmation; test impact pipeline independently meanwhile. |
| E04 | Older official special-order reset statement conflicts with W2 advanced strategy discussion of cross-phase ordering. | Follow-up: all346 special calls across3284/806/807/808 match encounter-wide alternating parity, zero mismatches, reset at `AT LAST!`, not at phase changes. Strong C-GM support for W2 ordering; not normal-duration or probability proof. |
| E05 | W2 private fee100k vs W7 transcript125k omitted tier context. | Current CA reward pages above resolve the Wiki-level discrepancy with150k/125k/100k/75k. Do not implement a universal100k fee; runtime fee/CA-boundary verification remains outstanding. |
| E06 | Common-table weights/quantity lump, MVP ties, eligibility source mix, pet share/bonus and tertiary topology are incomplete. | Native integration can be prepared, but no invented distribution or unconditional1/500 per-player pet roll. |
| E07 | Retarget probabilities, unreachable behavior, exact dash/drag selection, radius boundaries, prayer snapshots and several durations lack normal-game proof. | Named unresolved rules; no guessed constants presented as confirmed. |
| E08 | Current PB/start/rounding validity, recent PB correction history, all access/reclaim and public-room scenarios lack complete retained evidence. | Complete source/capture validation before runtime acceptance. |
| E09 | K identities are inspected, but no executable `NexR240CacheAuditTest` exists yet. | Add it after approval; do not call startup lookup a passed cache audit. |

Follow-up research after approval: the official June2025 Reaver sentence heals **the attacking
player**, not Nex. An agent's earlier recipient claim was incorrect. Wiki corrections
[14958162](https://oldschool.runescape.wiki/w/Special:Diff/14938316/14958162) and
[14990851](https://oldschool.runescape.wiki/w/Special:Diff/14990433/14990851) explicitly replace75%
with50% as a news-post error, but neither links a measurement or Jagex clarification. Player
lifesteal must not fail the CA for Nex receiving Siphon healing. E02 remains a rate conflict.
The [30 May2025 proposal](https://secure.runescape.com/m=news/summer-sweep-up-combat--loot?oldschool=1)
documents reduced post-threshold damage, but supplies no10% coefficient or crossing-hit rounding.
Additional local/remote searches found no normal Nex capture or primary proof of the draft soft cap.

Further prison evidence (808, C-GM): target SangKodai/projectile2006 t1935:L49810-12; perimeter42944
and center26209 t1937:L49827-34; helper destroys only(2935,5195) at t1941:L49885-93, punch422,
sound5272/range4; remaining ring persists to t1945:L49928-34. Another rescue t1614:L41421 sends
`<col=229628>You've been freed from the ice prison!</col>`, selected deletion t1615:L41451,
rest clears t1619:L41525-30. Self-rescue is rejected (806 t364:L10126-31); unsuitable weapons and
failed eligible attempts are observed (808 t326:L9421-23, t1291:L33039-44). Exact success probability
remains unverified. Do not delete whole rings, select lowest UID, or infer damage from unrelated
ordinary ice hits on the expiration tick.

## Capture manifest and packet evidence

All originals are preserved. The primary copied3284 from
`C:\Users\norma\.rsprox\binary\Old School RuneScape\yama-3284.bin` and downloaded other selected
submissions to the workspace without overwriting existing raw files. Both raw and matching decoded
text are under `logs/nex-audit-20260909/captures/`.

[Capture manifest](../../../logs/nex-audit-20260909/capture-manifest.md) contains provenance,
command and limits; [raw/transcript hashes](../../../logs/nex-audit-20260909/capture-hashes.json)
contains byte counts and SHA-256. The installed RSProx CLI, not a new decoder, transcribed all six
successfully to nonempty text. These are **transcript observations plus raw hashing**, not independent
binary packet verification.

| ID | Date / revision / mode | Covered encounter context | Confidence |
|---|---|---|---|
| 3284 | 25 Oct2025 /234.1 /Grid Master | Two complete private solo kills, all phases, private entry, respawn, external Bank Heist exit | C-GM high identification, no normal timing claim |
| 806 | 19 Oct2025 /234.1 /Grid Master | Twelve complete private solo kills | C-GM |
| 807 | 20 Oct2025 /234.1 /Grid Master | Nine solo then four duo completions with SangKodai; unfinished later attempt | C-GM |
| 808 | 20 Oct2025 /234.1 /Grid Master | Eleven complete private duo kills | C-GM |
| 2960 | 1 Jun2025 /230.1 /normal | **Rejected**: stored Nex KC at login, no Nex actors/fight | Not Nex combat evidence |
| 3934 | 15 Feb2026 /236.1 /normal | **Rejected**: Ancient Prison music-playlist message | Not Nex combat evidence |

Search scope included every requested local root, contextual Documents/Codex transcripts, and the
remote submission index. Local inventory:818 raw/726 text; unmatched local raws were OpenRune Server
captures, not untranscribed normal OSRS Nex. Numeric coincidences, login vars and music messages
were not classified as encounters.

Reusable tooling inspected: Cerberus search/transcribe, Arceuus search/transcribe/excerpt, and
Vorkath timeline/excerpt. `arceuus-capture-excerpt.ps1 -TranscriptPath ...` already supports generic
input; no new Nex-specific decoder/excerpt script is needed. Correct search categories include
NPC0, LOC2, MAPSQUARE3, MESSAGE_GAME4, SEQ8, SPOTANIM9, TEXT14. TEXT14 alone missed Nex speech.

### Representative capture facts (line numbers refer to retained matching transcript)

| Mechanic | Exact decoded evidence | Supported fact / boundary |
|---|---|---|
| Private entry | 3284 t292 L8469; t294 L8492; t299 L8520-8536 | Barrier42967 op2 at2909,5202; private create/join menu; instance11709 and barrier13184; entry2908,5203 to2910,5203. No paid fee proof. |
| Opening | 3284 t353 L8651-8658 | Spawning11279 at2924,5202 facing west; sequence9182, sound5303, overhead `AT LAST!`; draft uses barrier sequence9203 incorrectly. |
| Fumus | 3284 t358 L8680-8695 | 11283 at2913,5215 facing southeast. |
| Umbra | 3284 t362 L8710-8725 | 11284 at2937,5215 facing southwest. |
| Cruor | 3284 t366 L8739-8754 | 11285 at2937,5191 facing northwest. |
| Glacies | 3284 t370 L8768-8783 | 11286 at2913,5191 facing northeast. |
| Opening leech | 3284 L8691-8693 | Projectile2010 start30/end120, angle3, progress0, heights140 to190; sound5286 delay30. Client-cycle units, not game ticks. |
| Active form / Choke | 3284 t374 L8798-8811; t378 L8870-8879 | Same-index11279 to11278; first Choke precedes five normals, consistent with W2 opening. |
| Smoke normal | 3284 t382 L8999 onward; t390 L9239 | Projectile1997 sample start46/end146, angle5, progress84, heights100 to96; impact1998. End146 is one distance sample, not universal. |
| Smoke dash | 3284 t636 L15076; t641 L15215 | Two-stage dialogue; still needs full normal movement/damage timeline. |
| Shadow | 3284 t408 L9828; t416 L10092-10103; 808 t178 L5128-5130 | Darkness observed with9182; Shadow Smash9186 and loc42942 on both duo target tiles. |
| Siphon / Reaver | 3284 t444 L10878-10907 | Sequence9183, graphic2015;11294 at2934,5212, spawn graphic2017, sound4497. Healing percentage not established. |
| Sacrifice | 3284 t477 L11951-11973;808 t258 L7673 | Mark warning, player tint(start0/end210,hue0,saturation6,lightness28,weight112), sound5223; explicit escape message. |
| Containment | 3284 t494 L12387-12397; t499 L12535-12557 | Sequence9186; loc42943 packet placement/orientations around actual Nex. Sample has11 visible tiles, not proof of universal11. |
| Prison | 3284 t726 L17708-17725; t729 L17806-17830; t737 L17979 | Projectile2006 sample start46/end96, angle5, progress84, heights30 to20; eight loc42944 around2935,5195; warning then shatter. |
| Zaros | 3284 t519 L13193-13214; t523 L13327-13328 | Healing511 to1011, sequence9179, graphic2016; music711; same-index Soul Split form11280. |
| Wrath | 3284 t542 L13917-13927; t543 L13942; t546 L13960-13980 | Dying11282, options0;9184; sounds5236(delay60)/5241(delay90), central2013 and satellite2014 via2012 projectiles. No damage-radius proof. |
| Completion / loot | 3284 t549 L13987-14024 | KC varp3269, PB1:41 message, MVP message, items under actual Nex2917,5192, reveal100/despawn200 packet fields, barrier3 to2, deletion. Do not substitute boss-local18000-tick lifetime. |
| Repeat / exit | 3284 t587 L14101; t790 L19471-19474; t807 L19594-19596 | Fresh opening, second completion, then Grid Master external teleport. Does not prove altar exit. |
| Duo targeting | 807 t6087 L104194, t6091 L104333;808 t139 L3896,t143 L4057 | Projectiles actually target SangKodai, not merely a nearby player-name string. Selection/tie probabilities unresolved. |
| Reaver movement | 808 t222 L6488;t223/225/227/229 L6506/6518/6556/6603; t236 L6842 | Spawn, observed alternate-tick crawl and removal; normal-mode cadence still unverified. |

Decoder warnings: pure-Java bzip2 fallback in four fight logs;807 additionally reports a Netty
ByteBuf leak. The installed transcriber catches unsupported packet errors and can continue, so
successful output is not completeness proof. Private-instance normalization can mix source and
allocated coordinates on departure. Historical supporting-cache downloads are an automatic RSProx
CLI side effect (e.g. cache2196 under the existing external RSProx cache); the OpenRune SERVER/LIVE
paths and revision were not changed.

## Cache audit boundaries and corrections

Server combat definitions must be checked through native `ServerCacheManager.init(240)`, not
a generic OSRS decoder applied to custom SERVER enums. The generic decoder failed on enum65475;
that failure is not a reason to alter the active cache. LIVE remains the client/JS5 identity source.

Inspected identities: Nex11278-11282, bodyguards11283-11286, encounter Reaver11294 vs prison11293,
pet11277; barrier42937-42941/controllers42967-42968, shadow42942, icicles42943-42944, altar42965.
Full sequence9177-9203, graphics1997-2017, map/options/var/interface/reward audits remain planned.

Native SERVER/LIVE inspection was repeated by the primary using the existing built server jar and
JShell, exit0 with no reported decoding errors. Retained output:
[native-cache-inspection.log](../../../logs/nex-audit-20260909/native-cache-inspection.log).
This is read-only definition evidence, **not** the requested executable regression test.
The reproducible command is in [cache-inspection.md](../../../logs/nex-audit-20260909/cache-inspection.md).

Concrete inspection findings, **not yet executable-cache-test results**:

- Draft guard coordinates are contradicted by C-GM; update normal arena anchors only with this
  confidence and corroborating map checks.
- Current W1 Nex attack bonuses are magic100/ranged150, strength magic22/ranged5; old ledger swapped
  these categories. Some SERVER params omit melee attack200/ranged defences; patch the intentional
  server definition source after approval, not runtime stat repair or generated cache files.
- Modern bodyguard definitions require validation of -2 flat armour and ranged categories.
- W4 says Reavers are susceptible to poison/venom; blanket encounter immunity is unsupported.
- Item26356 is **Frozen key**, not the Ecumenical key shard claimed by `NexRewards.commonPool`.
- The correct Ecumenical key shard identity is **26388**, checked against revision240 game values
  and Wiki identity. Reaver11294 SERVER stab defence0 conflicts with W4's20.
- Nex sounds are available. E.g. LIVE sequence9180 frame1 sound5183;9183 frame1 sound5252;
 9184 frame1/55/56 sounds5180/5232/5218. These client animation sounds differ from explicit sound
 packets: avoid double playback. `dbrow.synth_nex`4649 exists.
- CA tasks410-420, structs4137-4147, varbits13652-13662 were identified. Identity alone neither
  implements task conditions nor proves current task descriptions/tier thresholds.

Additional K identities: Reaver idle9195/walk9193/attack9194/death9192; Nexling11277 is a follower
(11276 is the POH variant). Shadow42942 is nonblocking; both icicle forms block, but only42944
offers Attack. Altar42965 is3x3. Nex statistics interface732 has close2/title6 and personal9/11/13,
global15/17/19, default best `-`. Barrier varbit13184 is varp3264 bits17-18; KC3269 is persistent.
Warnings13180/13183; CA total13171; collection completion13191; presentation varcs550/950/951/952
and clientscript5312 require executable/interface tests before enabling.

Decoded spotanim-to-sequence pairs:1997→1981,1998→1987,1999→7615,2000→1969,2001→1966,
2002→1969,2003→1967,2004→1958,2005→1965,2006→1960,2007→1980,2008→664,2009→9197,
2010→9198,2011→9199,2012→9200,2013→9201,2014→9201,2015→8991,2016→9191,2017→9202.
In particular,2015 is not sequence9190. Definition existence does not establish projectile timing.

### Current documented rules, distinguished from packet-exact proof

W2 documents normal4tick cadence; phase calls2720/2040/1360/680; nearest-target selection with
current-style defence tie-breaking, not UID. Normal melee protection4/5, final melee2/3, normal
magic1/2, Blood magic2/3 and Shadow ranged1/2 are documented fractions. W2 gives Ice normal
freeze15ticks and missiles protection halving Ice special hits. Current draft freeze10 and
unconditional special bypass therefore need correction once routed through native policy.

W2 also documents Zaros overhead order Soul Split→Deflect Melee→none, four normal attacks each;
this draft constant is **Wiki-supported**, not disproven merely because no normal capture exists.
The healing/reflection/stat formulae and precise sampling still need evidence. Nex melee maximum
27 in W1 versus30 in W2 is another unresolved source distinction; do not silently choose one.
W2 Wrath maximum50 contradicts draft55, but exact radius/snapshot/seconds-to-ticks remain unverified.

W6 bodyguards:500HP, speed5, maximum29, stationary; Fumus poison4, Umbra Attack drain, Cruor
self-heal, Glacies freeze6. They have independent ceremonial table1/200, five equal pieces1/1000
each, citing [Mod Ash, 26 April2022](https://twitter.com/JagexAsh/status/1518900406830653442).
W4 encounter Reaver:150HP,size2,speed5,max20,Magic190,Defence100; not toxin immune.
Current manager's generic guard max20 and Reaver max18/animation811 are not those definitions.

Established reward facts from O2/O3/W1: unique denominator43; weights total12 (Torva pieces2each,
horn2,vambraces3,hilt1); MVP common/unique benefit10% and bones; multiple player uniques possible.
Contribution includes bodyguards/Reavers and documented thrall damage. Exact eligibility threshold,
MVP ties, common weights and pet scaling remain E06. W1's blood essence1/82, shards5/82 and8/209,
rune sword1/209 are listed in the **Other** section, not proven independent tertiary rolls.

### All11 CA contracts to execute after approval

These descriptions come from decoded240 task structs and Wiki; no CA was awarded or tested here.
Completion varbits13652-13662 map to varp3128 bits26-31 then3387 bits0-4, total56points.

| Task ID | Task / tier | Required event-based test |
|---|---|---|
|410|Nex Veteran / Elite|First eligible completion, award once|
|411|Nex Master / Master|25KC boundary, not old100|
|412|Nex Survivors / Elite|No participant death|
|413|A siphon will solve this / Master|No Siphon healing, including leftover Reaver absorption|
|414|Contain this! / Master|No participant Ice-special damage|
|415|There is no escape! / Master|No participant Smoke Dash hit|
|416|Nex Trio / Master|At most3 participants at encounter start|
|417|Nex Duo / Grandmaster|At most2 participants at encounter start|
|418|I should see a doctor / Grandmaster|A player coughing at completion, including final-phase persistence|
|419|Perfect Nex / Grandmaster|Combined Siphon, Ice, Dash and Shadow criteria; shared team attribution|
|420|Shadows Move... / Master|No participant Shadow Smash hit|

The exact zero-damage/prayer/avoidable-hit semantics and join/leave attribution must be pinned from
task descriptions/current clarifications, then exercised in T15 rather than guessed from animations.

## Official workflow catalogue (immutable upstream references)

Official repository: `https://github.com/OpenRune/OpenRune-Server`, fetched main
**61139e76a216592d66c2b20366ef26d48609875d**, 9 September2026. Origin is the user's fork, not
official upstream. Every **U** link below is pinned to that exact commit.

| API key | Classification and exact upstream source | Local difference / use |
|---|---|---|
| A01 | **U** [BossCombat](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/bosses/src/main/kotlin/org/rsmod/api/bosses/runtime/BossCombat.kt), [BossEncounter](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/bosses/src/main/kotlin/org/rsmod/api/bosses/runtime/BossEncounter.kt) | `projects.api.bosses` is official. Local ownerSlug/identity guard additions are L, not upstream. |
| A02 | **U** [Effect](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/bosses/src/main/kotlin/org/rsmod/api/bosses/spec/Effect.kt), [EffectInterpreter](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/bosses/src/main/kotlin/org/rsmod/api/bosses/runtime/EffectInterpreter.kt), [native finishNpcHit](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/combat/combat-commons/src/main/kotlin/org/rsmod/api/combat/commons/player/PlayerExtensions.kt) | Native accuracy, projectiles, queued hits and effects. Nex's NpcAttackOverride/NpcProjectile/exposed NvPCombat are L and must not be reused as official APIs. |
| A03 | **U** [StandardPlayerHitModifier](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/player/src/main/kotlin/org/rsmod/api/player/hit/modifier/StandardPlayerHitModifier.kt), [hit queue helpers](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/player/src/main/kotlin/org/rsmod/api/player/hit/PlayerExtensions.kt) | queueImpactHit already exists. Partial/calculated/bypass NPC-prayer modifiers currently used by Nex are L. |
| A04 | **U** [NpcCombatEvents](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/npc/src/main/kotlin/org/rsmod/api/npc/events/NpcCombatEvents.kt), [StandardNpcHitProcessor](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/npc/src/main/kotlin/org/rsmod/api/npc/hit/processor/StandardNpcHitProcessor.kt), [PlayerHitpointsChangedEvent](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/player/src/main/kotlin/org/rsmod/api/player/events/PlayerHitpointsChangedEvent.kt) | Modify is queue-time; Impact is post-resolution. Actual HP delta already available. HitImpactHandler is L; don't invent a second impact bus. |
| A05 | **U** [RouteFactory](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/route/src/main/kotlin/org/rsmod/api/route/RouteFactory.kt), [Scurrius arrival workflow](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/content/bosses/scurrius/src/main/kotlin/org/rsmod/content/bosses/scurrius/Scurrius.kt) | Size-aware native route/reach and arrival callbacks; no new pathfinder or ordinary movement teleports. |
| A06 | **U** [InstanceScript](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/instances/src/main/kotlin/org/rsmod/api/instances/InstanceScript.kt), [InstanceManager](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/instances/src/main/kotlin/org/rsmod/api/instances/InstanceManager.kt), [GraardorInstance](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/content/bosses/graardor/src/main/kotlin/org/rsmod/content/bosses/graardor/GraardorInstance.kt) | Public/server-owned and private lifecycle, costs/entry, attachment already exist. Local identity-based attach/detach fixes are pre-existing L changes to preserve. |
| A07 | **U** [NpcDeath](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/death/src/main/kotlin/org/rsmod/api/death/NpcDeath.kt), [NpcDeathKillHook](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/death/src/main/kotlin/org/rsmod/api/death/NpcDeathKillHook.kt), [native drop processing](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/content/drops/src/main/kotlin/org/rsmod/content/drops/NpcDropTableKillHook.kt) | Existing one-hero dispatch, native tables/conditions/loot tracker. Local NpcKillRewardContexts metadata isn't durable multi-recipient completion. |
| A08 | **U** [DamageContributions](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/engine/game/src/main/kotlin/org/rsmod/game/damage/DamageContributions.kt), [NpcDamageContributor](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/npc/src/main/kotlin/org/rsmod/api/npc/hit/NpcDamageContributor.kt) | Current rich source attribution and HitContext/HitScope are L additions. Reuse/normalize only as the explicitly proposed S03 extension, no parallel contribution map. |
| A09 | **U** [CollectionLog.grant](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/content/interfaces/collection-log/src/main/kotlin/org/rsmod/content/interfaces/collectionlog/CollectionLog.kt) | Current authoritative local shared service is **L api/collection-log/CollectionLogService**. Preserve it behind native drop integration; never install a second log implementation. |
| A10 | **U** [AccountManager](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/account/src/main/kotlin/org/rsmod/api/account/AccountManager.kt), [Database transactions](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/db/src/main/kotlin/org/rsmod/api/db/Database.kt), [InstanceKillTimer](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/instances/src/main/kotlin/org/rsmod/api/instances/timer/InstanceKillTimer.kt) | Persistent attrs/vars are native. Stable per-completion transactional reward dispatch is missing, not solved by transient UID sets. |
| A11 | **U** [HeronPet TODO](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/content/skills/fishing/src/main/kotlin/org/rsmod/content/skills/fishing/HeronPet.kt), [equipment pet TODO](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/content/interfaces/equipment/src/main/kotlin/org/rsmod/content/interfaces/equipment/EquipmentTabScript.kt) | No complete pet/follower or CA award service found upstream/local. Cache IDs, broadcast enums and persistent booleans are not such services. |
| A12 | **U** [Demonic Gorilla](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/content/bosses/demonic-gorilla/src/main/kotlin/org/rsmod/content/bosses/demonicgorilla/DemonicGorilla.kt), [General Graardor](https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/content/bosses/graardor/src/main/kotlin/org/rsmod/content/bosses/graardor/GeneralGraardor.kt) | Official examples for queued special effects/impact events and lifecycle bodyguard respawn, not proof of Nex mechanics. |

Searches covered the full local tree and `git grep`/`git show` at upstream SHA, including tests,
special attacks, NPC stat setters, lifecycle, contributions, death/drop services, pets/CAs and account
persistence. History: official GWD `020be08f0`, Scurrius `cd6cc0467`; many fork extensions originate
in `f9dd030fc19d17d6476e565383374aa0d33283d5`, newer provenance work remains uncommitted.
The full relevant shared diff is retained as `relevant-shared-diff-before.patch`.

Local GWD and [Vorkath repair](../vorkath/repair-validation-20260908.md) were inspected as examples.
Vorkath's native-loop test approach is useful; its fork-only combat override is **not** an approved
official API for Nex. Its document explicitly leaves graphical verification incomplete.

## Complete mechanic-to-API approval mapping

Each row links its required behavior/evidence confidence, current implementation, official workflow,
local difference (catalogue above), proposed work, executable tests (below), and missing capability.
All rows are **approved and planned**, not completed mechanics.

| ID / mechanic | Required target / evidence confidence | Existing Nex defect or limitation | Planned native integration / API keys | Executable tests / gap |
|---|---|---|---|---|
| M01 Access / Frozen Door / KC / bank | W1-W3 access rules; exact current options/thresholds to pin; bank safety required | Admin teleport and partial barrier handler; no complete natural route | Existing GWD counters, protected loc interactions, A06; real bank/barrier entry without command | T01; evidence E08, no new entry engine |
| M02 Public room / busy barrier / peek / late entry / spectators | W2, K; C-GM only private menu and barrier sequence | Debug-like peek; incomplete transforms; access-protected entry guard may reject valid handler | A06 public server-owned room and native object hooks; explicit participant gating, native dialogue/vars | T01,T12; verify public state rules |
| M03 Private create/join/fees/team cap/friends | W2/W7 and C-GM entry; fee conflict E05 | Unsupported | A06 settings row, private creation/join, native friend/online/fee checks; no new instance manager | T01,T15; E05 resolution first |
| M04 Altar restoration / cooldown / exit | W1: ten-minute cooldown and Zaros equipment branch; K altar42965 | Prayer-only unlimited restoration, custom teleport | Native stats, vars/timers, special energy and A06 exit; validate all equipment/option branches | T01,T12,T15; E08 |
| M05 Player death / reclaim chest | W2 reclaim behavior; exact fees/loss/re-entry to pin | Standard ground drops, no chest flow | Existing native player death selection + shared inventory storage/reclaim S05, not Nex codec | T12,T15; S05 |
| M06 Spawn / facing / guard creation / readiness | C-GM opening coordinates and sequence, K forms; normal timing E01 | Wrong guard coordinates, five-tick opening,9203 instead of9182 | A01/A06 one canonical owner; staged transforms/NPC attachment, packet-backed facing/presentation | T02,T16; S07 RNG only |
| M07 All phases normal accuracy/max hits/cadence/styles | W1/W2 stats; C-GM identities; O1 extra melee chain | Fork overrides, random secondary damage without accuracy | A01-A03 shared melee/magic/ranged effects/formulae; NPC properties in intentional definitions | T03,T16; S01, E01/E07 |
| M08 Partial prayer and late final magic | O1 late25%; W2 phase protection; exact snapshot E07 | Fork modifiers and impact callbacks | A03 shared declarative per-attack protection and native impact queue | T04; S01 |
| M09 Defence limits / armour / toxins / recoil / summons | W1/W4/W6; K omissions; exact floors to substantiate | Post-tick defence repair; non-player damage forced0; blanket Reaver immunity | A04/A08 native resolved damage/provenance, native definition toxin/armour rules, stat floor S08 | T04,T05,T11; S02,S03,S08 |
| M10 Threshold crossing / bodyguard call / immunity | O1 lower-floor allowance, W2 call order; E03 unresolved formula | Queue-time predicted transition, soft-cap guess, stale-HP races | A04 impact-time policy before HP clamp/contribution; call after accepted impact, non-final lethal prevention | T05; S02 |
| M11 Every guard vulnerability/death/phase advance | C-GM calls, W2/W6 distinct attacks and status effects; guard cap E03 | Immediate phase advance before death animation; repeated hits risk; generic guard combat omits individual effects | A01/A04/A07 one native guard death, one transition, current-phase gates and native poison/stat-drain/heal/freeze | T05,T06; S02 |
| M12 Smoke Choke duration/spread/masks/drains | W2 opening Choke; C-GM t378; exact normal tick/source rules pending | Starts after5 normals; global even-tick schedule,100tick duration unproven | Nex phase state + native effects/events/queues, source-specific infection expiry and cleanup | T07; S03; E01/E07 |
| M13 Smoke Drag and Dash | W2 and C-GM two-stage call; complete route/selection unresolved | Drag never selected; teleports every player and boss, anchor row/column damage | A05 native routing/forced movement and A02 scoped area hits; exact route only from evidence | T07,T12; S03; E07 |
| M14 Shadow normals / Smash / Darkness | W2; C-GM9182/9186,42942 duo snapshots | Fixed10tick darkness/5tile anchor radius; generic delayed hit | A01/A02/A05 target snapshots, native loc lifecycle/collision, per-participant exposure | T03,T08; S01,S03; E07 |
| M15 Blood normals / healing / prayer drain | W2; C-GM projectiles | Secondary damage skips accuracy; impact logic diverges | A02-A04 native attacks; heal from actual HP delta and verified source type | T03,T09; S01,S03 |
| M16 Blood Siphon / incoming hits / absorption | W2; C-GM9183/2015 | Incoming damage heals at queue-time; fixed8tick window; old Reavers not absorbed/cleared correctly | A04 native impact policy and actual damage events; phase-scoped summons/absorption | T05,T09; S02,S03; E07 |
| M17 Reavers count/spawn/crawl/attacks/death/healing | W4, K11294, C-GM movement; healing E02 | Team size clamped1..8, instant fixed spawns, generic811; pre-hit healing | A05/A06 owned NPCs, native definition/combat/death; resolved player healing, all simultaneous kills | T06,T09; S03; E02/E07 |
| M18 Blood Sacrifice target/mark/escape/impact | W2, C-GM tint/warning/escape | Unverified+8 and anchor-distance7; no proper marking; delayed-target races | A02/A05 native visuals/movement and scoped impact; target retained by stable identity | T09,T12; S03; E07 |
| M19 Ice normals / freezing | W2 and K; snapshot details unresolved | Generic freeze/prayer deductions tied to launch assumptions | A02/A03 native combat/freeze and protection policies, correct target validity | T03,T10; S01,S03 |
| M20 Containment geometry/collision/impact/expiry | C-GM loc42943 placement; W2 | Only anchor-distance area damage, missing real icicles | A02/A05/A06 native loc placement/collision and scoped area hit | T10,T12; S03; E07 |
| M21 Ice Prison selection/objects/break/escape/prayer | C-GM projectile2006 and eight42944 perimeter; W2 | Click breaks lowest UID rather than selected prison; removed entry still passes damage condition | Associate locs with one prison/target; native collision/protected interactions; resolved expiry state, not missing-map default | T10,T12; S03; E07 |
| M22 Zaros normal attacks / forced melee | O1 all phases extra attempts; W2 | Fixed melee selection chance and nearest-anchor target, final-specific guesses | A01/A05 readiness/target state; correct footprint reach and scoped native attacks | T03,T04,T11; S01,S03,S07; E07 |
| M23 Soul Split / Deflect / Turmoil / rotation | W2 four/four/four order; C-GM500heal,9179/2016,11280 transform | Rotation not runtime-tested; reflection/healing formulae and current-overhead impact sampling unproven | A01/A04 transform state and resolved HP events, native recoil/protection; substantiate reflect/heal formula | T11; S01,S02,S03; E07 |
| M24 Death / Wrath / actual death position | C-GM same-index11282,9184,2012-14, sounds; W2 damage requirement | Native NPC deleted before queued Wrath; explosion uses spawn instead of death tile | A07 native death coroutine waits/orchestration; capture death tile/participants, execute A02 scoped effects before cleanup | T11,T12; S03,S04; E01/E07 |
| M25 Aggression / target switching / reach / unreachable | W2 and C-GM actual duo target packets | Nearest anchor then UID; wrong3x3 reach | A05 native routing/interaction checks; content target policy only when evidenced | T03,T06,T12; E07 |
| M26 Empty room / consecutive kills / natural respawn | C-GM two complete3284 kills, A06/A07 | Player-post-tick-driven room; local lists/reset work incomplete | A01/A06/A07 world/instance lifecycle, attached owned NPCs/locs, one next spawn | T02,T12; S03 |
| M27 Logout/disconnect/teleport/death/generation | Required no stale incoming hit; native lifecycle U | Some special tasks guarded; ordinary/secondary queued hits not guarded | A06/A08 native scopes at hit resolution and lifecycle events; no private cancellation bus | T12; S03 |
| M28 Contribution/eligibility/MVP/source provenance | W1; detailed algorithm E06 | Boss-local integer map credited before impact, guessed tie rules | A04/A08 resolved source-aware contribution, exclude/weight only evidenced sources; stable participant identity | T13; S03,S04 |
| M29 Common/unique/personal rolls/tertiaries/bones/drop tiles | O2/O3/W1; C-GM actual loot packets, newer drop-tile rule requires pinning | Uniform pool, wrong26356, common+unique topology and tertiary assumptions,18000tick lifetime | A07 native tables and multi-recipient context, native clue conditions, shared ownership/reveal policy | T13,T14; S04; E06 |
| M30 KC/PB/persistence/reconnect/idempotency | C-GM3269/KC/PB; W1; E08 clock semantics | Reward-time varp increment and transient rewarded set; disconnected recipient lost | A07/A10 stable completion ID, per-recipient transaction/outbox, native vars/timer and PostgreSQL | T14,T15; S04 |
| M31 Collection Log / broadcasts / loot tracker | U shared drop pipeline; W1 collection entries | Direct local service and manual chat; old ledger wrongly says no service | A07/A09 authoritative shared drop processing; no second log, event identity from S04 | T14,T15; S04 |
| M32 Nexling | W1/W5 pet shared contribution rather than universal per-player1/500 | Boolean unlock, no follower; chance unverified | Native rewards -> shared pet service using NPC/inventory/account workflow | T14,T15; S06; E06 |
| M33 Combat Achievements, all11 | K task IDs410-420, current task text needs full final contract | Flags only; no award service | Shared CA award/persistence/cache task/tier workflow; source-tagged Nex conditions, no animation guesses | T15,T16; S06 |
| M34 HUD/interface/sounds/all visual parity | C-GM packet rows above + K; raw gaps E01/E09 | Wrong sequences; missing known sounds, guessed projectile timing, no retained client proof | A02 native output; cache metadata plus packets, distinguish sequence sounds from explicit sound packets | T02-T11,T16 plus live clients |
| M35 Unsupported Wilderness branch | X: custom final-phase snowfield is not canonical Nex | Second wildernessRun autoscheduled, custom attacks/leash/tests | Remove runtime/config/rules/tests and stale mode references within Nex; preserve unrelated wilderness spawns/areas | T01,T12, search regression; no shared combat gap |
| M36 Statistics interface / personal and global data | K interface732 components; current value/record rules need pinning | No retained native statistics interaction test | A06 protected loc/UI output and A10 committed persistence, no transient scoreboard map | T01,T15,T16; S04, E08 |

## Explicit approved shared capabilities

This is the complete **approved proposed shared API** scope, not permission to edit arbitrary engine files.
Existing local APIs remain labelled L; approving a proposal does not retroactively make them official.
Default behavior for non-Nex callers must remain unchanged. Any additional shared change discovered
later requires its own mapping/checkpoint.

### S01 Native partial/late protection policy

Requirement: O1 final magic late prayer and W2 partial normal protection.
Search: A02/A03, upstream effect builders, native hit helpers/modifiers and their tests; official
standard modifier hardcodes100% NPC protection. `queueImpactHit` already provides impact evaluation.

Smallest addition: declarative policy under `api/player/.../hit/modifier/`, passed by
`api/bosses/.../spec/Effect.kt`, `dsl/EffectBuilders.kt`, `runtime/EffectInterpreter.kt` and
`api/combat/combat-commons/.../player/PlayerExtensions.kt`. Keep native event/admin/absorption order,
launch snapshot and optional impact sampling. No Nex-only modifier or direct engine override.
Tests T04 plus existing player/combat/boss suites.

### S02 Impact-time NPC damage policy

Requirement: threshold floors, siphon windows and simultaneous queued-hit safety.
Search: A01/A04, `Npc.queueHit` and tests. Modify occurs at queue construction; Impact is too late to
prevent HP loss. No existing pre-resolution floor/immunity policy found.

Smallest addition in `api/npc/.../events/NpcCombatEvents.kt` and
`hit/processor/StandardNpcHitProcessor.kt`, registered through A01 (and native script helper if
needed): a keyed, default-no-op pre-resolution rule before final HP clamp, hitmark and contribution.
Use existing post-impact events for calls/heals. Preserve current dirty attribution and validity
checks; don't duplicate processors. Tests T05 with ordinary-NPC and admin-rule regressions.

September13 integration refinement (within approved S02, before edits): moving Blood Siphon healing
to Impact requires the incoming amount even when accepted damage is zero. Complete local event,
processor, consumer and pinned upstream61139e76 searches confirm Impact currently retains only final
damage. Append defaulted `originalHit` and `inputDamage` observations to the existing Impact event in
the already mapped NpcCombatEvents/StandardNpcHitProcessor files. `originalHit` is the immutable queued
hit after queue modifiers; `inputDamage` includes admin adjustment before Resolve; `hit.damage` remains
accepted HP loss. Preserve Impact timing and pin its dispatch key to the resolution identity. This is
approved local S02 metadata, not a separate hit pipeline or official upstream signature. Tests execute
real resolution for overkill, zero/private hits, admin expansion, transforms and invalid scopes.
Nex will retain its characterized formulas, use original damage for Siphon (no admin-expanded healing),
and credit/heal/call guards only after impact. Full source-origin reward eligibility remains M28/S03/S04.
Impact integration exposed a presentation dependency in this same approved processor: the normal
headbar precedes Impact, so an absorbed hit's silent native heal would leave its bar at old HP.
Preserve event ordering and reuse the processor's existing headbar encoder for a conditional refresh
when Impact changes HP, only for the same visible, non-delayed, still-assigned identity/headbar and
without suppression. Do not invent a heal hitmark or bypass InternalNpcHeadbars from content.
The native Nex headbar regression is red against the first integration (`nex-s02-headbar-red-20260913.log`,
12 tests,1 failure). Shared refresh/teardown regressions and the Nex test will verify the correction.
This emits a second same-type bar update when needed; raw packet count/parity remains unverified.
Metadata foundation implemented and tested separately before Nex production integration:
`nex-s02-impact-metadata-20260913.log`,1m4s,169 tasks,23 executed; NPC14, boss API61 and Nex67
tests pass (142 total). The two new processor regressions exercise immutable overkill/private-hit
observations and transform-stable dispatch; existing admin/invalid-scope tests also assert metadata.

### S03 Native ownership, attribution and cancellation context

Requirement: accepted indirect damage must not be discarded, and delayed attacks cannot affect
departed/new-generation participants. Search A04/A06/A08 and queue processing; official upstream
has limited source-death/melee invalidation but no complete encounter scope.

An implementation already exists **locally**, so do not build another one. Explicitly proposed:
normalize/complete its integration through native A02/A04/A06 pipelines, preserving dirty hunks.
In-scope existing files include `engine/game/.../hit/HitContext.kt`, `Hit.kt`, `HitBuilder.kt`,
`QueueArgumentValidity.kt`, `PathingEntity.kt`, `Npc.kt`, `Player.kt`, native hit queue helpers and
processors, `DamageContributions.kt`, `NpcDamageContributor.kt`, and instance contribution/lifecycle
hooks. Adapt declaration/propagation, not arbitrary boss callbacks; prefer official impact events
over local HitImpactHandler. Tests T04,T05,T12,T13 and existing toxin/recoil/thrall/RT/Vorkath suites.

### S04 Multi-recipient native completion/drop delivery

Requirement: W1 contribution-weighted personal loot, multiple uniques, persistent KC and replay-safe
rewards. Searches A07-A10 find one `findHero` dispatch; local `NpcKillRewardContexts` is a weak map,
not recipients or a durable completion ledger. Native drop delivery is private to the shared hook.

Propose a declarative recipient/reward context through `api/death/NpcDeath.kt`,
`NpcDeathKillHook.kt`, `api/drop-table-plugin/.../KillRollContext.kt` and existing
`NpcKillRewardContext.kt`, then the **existing** `content/drops/.../NpcDropTableKillHook.kt`.
Use native tables, conditions, clue transformations, provenance, Collection Log, broadcasts, loot
tracker and shared ground-object ownership, never `deathNoDrops -> NexRewardService`.

Durability belongs to existing `api/account`/`api/db` transactions and migrations: stable per-life
completion identity, unique recipient/event constraints, recorded rolls and retryable delivery.
The existing `api/repo/.../obj/ObjRepository.kt` / native object ownership integration may need a
stable delivery identifier to prevent replayed ground drops; this is included here, not a second
object repository. Account/inventory save and delivery recovery must be tested at crash boundaries;
a database row followed by an unguarded in-memory grant is **not** exactly-once delivery.
Tests T13-T15, ordinary single-hero drops, rollback/concurrency/restart/idempotent log and pickup.

### S05 Shared death reclaim

Requirement: Nex reclaim behavior must use unsafe-death selection and preserve item variables.
Search A06/A07 and local Vorkath/Yama/Zulrah storage: local `PlayerDeathStorageHook` exists;
no complete official generic item-reclaim service. Instance `Reclaim` state is room re-entry grace,
not an item chest.

Propose smallest reusable storage/reclaim service in `api/death`, using existing `api/inv-storage`,
`api/invtx`, account persistence and native death hooks. Nex supplies policy/chest identity only.
Keep pre-existing pouch/storage-hook fixes and other bosses untouched except necessary shared
default-preserving integration. Persist before clearing inventory; fee/capacity/reclaim transactional.
Tests T12/T15 plus Vorkath storage and player-death/pouch regression suites.

### S06 Shared pet/follower and Combat Achievement awards

Requirement: actual Nexling and all11 persistent CA awards, not booleans/flags alone.
Search: full local/upstream pets/followers/CA events/services/tests, A11 explicit TODOs.
No suitable complete award/lifecycle service found. Existing NPC movement, player vars, cache task
definitions, account/inventory persistence, UI output and broadcasts remain the owners.

Propose **shared** `api/pets` for owner-bound pet award/follow/recall/despawn/reclaim integration,
using native NPC/repository movement and account data; no new movement engine. Propose shared
`api/combat-achievements` for cache-backed task completion/tier queries and notifications using
native vars/account transactions. Wire through existing project dependency/module registration;
implement Nex condition evaluators in the existing Nex module only. Do not add unrelated boss tasks,
a second collection log, or independent flat-file persistence. Exact current Nex pet/task policies
must be evidence-pinned before enabling them. Tests T14-T16 including same-event awards and restarts.

### S07 Injectable native scheduling RNG

Requirement: deterministic execution tests, not flaky probability tests.
Search A01: upstream `BossEncounter.randomThreshold/selectWeightedRandom` uses Kotlin Random.
Inject existing `GameRandom` via `BossEncounter.kt`/`EncounterRegistry.kt`, retain default production
distribution. No new RNG subsystem. Tests selected branches plus default scheduler regression.

### S08 Native NPC stat-drain floor

Requirement: W1/W2 limited Nex defence reduction; current post-tick restoration is not atomic.
Search includes upstream `Npc.defenceLvl`, native regeneration, all local special-attack drain
call sites (Elder Maul, warhammers, bone/dorgeshuun, Ralos etc.). These directly set NPC levels;
no shared minimum-level rule was found. Existing local setters enforce immunity only.

Propose a default-preserving, data-driven minimum in the **existing NPC stat mutation boundary**
(`engine/game/.../entity/Npc.kt` and its native definition/config support), not a Nex override or
per-tick repair. Preserve other stat/immunity behavior. No broad stat-engine redesign. Tests current
drain weapons at/above/below floor, Turmoil changes and ordinary NPC defaults. Substantiate precise
Nex floor before configuring it. T04,T05,T16.

## Source edits planned after approval

- Refactor, do not delete/recreate, `content/bosses/nex/`: current module/config/combat/manager/
  lifecycle/world/rules/rewards and existing tests; add native integration fixtures there.
- Existing native Nex/bodyguard/Reaver reward tables under `content/drops/`; current Collection Log
  entries, KC keys and canonical registrations remain authoritative.
- Intentional NPC/instance/map/gameval configuration only where K/source evidence requires it.
  SERVER/LIVE paths and revision stay240. No generated output editing.
- `or-cache/src/test/kotlin/dev/openrune/NexR240CacheAuditTest.kt`.
- Shared files/modules explicitly listed S01-S08, with prior dirty hunks isolated before edits.
- Existing ledger and focused evidence. Reuse generic capture tooling; only parameterize it if needed.
- Remove canonical-module references to `wildernessRun`, `WILDERNESS_NEX_SPAWN/ARENA/ATTACK_RATE/
  ATTACK_RANGE/LEASH`, `WildernessNexAttack` and their auto-spawn/rules/tests. Outside references found:
  `.data/raw-cache/map/npcs/wilderness.toml` comment and custom multiway polygon. **Do not restore
  deleted historical wilderness spawns or change multiway geography as part of this combat fix**;
  remove stale mode descriptions only if appropriate. Canonical Slayer/Collection Log Nex references stay.

## Executable verification contract

The following test suites are **planned**. Tests must exercise registration, native queues,
processors, routefinding, instance ownership and transactions with injected clock/RNG. Rule-only
tests may supplement but cannot satisfy these rows.

| Test group | Required executable coverage |
|---|---|
| T01 access | Real frozen-door/bank/barrier handlers; option1/2/3; public/private state boundaries; fee rollback/tiers/friends/team cap; peek; altar cooldown/restoration/exit; command not required; no Wilderness auto-spawn. |
| T02 spawn | Exact index/form sequence, Nex/guard positions/facing, barrier vars, spawn readiness, sound/graphic output, initial target, repeated native respawn. |
| T03 standard combat | All five phases plus guards/Reavers; native accuracy/style/max-hit; normal cycles; forced melee; target switching,3x3 reach, LOS, unreachable targets. |
| T04 prayer/stats/sources | Launch/impact prayer matrix, final late prayer, native absorption/admin behavior, defence floors, flat armour, poison/venom, recoil/thrall/burn provenance; no global non-player-zero rule. |
| T05 damage resolution | Threshold crossing/floors/caps once evidenced; same-tick multiple hits, queued phase changes, siphon immunity/healing, bodyguard gating and non-final lethal prevention; overkill only actual damage credited. |
| T06 guards/Reavers | Every guard call/death, duplicate lethal hits, arrival/death timing; Reaver count/targets/crawl/stacking/pathing, ownership and phase-change cleanup. |
| T07 smoke | First Choke, spread/retention/mask/drain/expiry; drag targeting; dash launch/route/collision/snapshot/damage; all death/exit cleanup paths. |
| T08 shadow | Normal ranged; per-player Smash snapshots/tiles/expiry; Darkness evolving radius/visuals and multiple participants. |
| T09 blood | Siphon start/end and in-flight hit races; prior Reaver absorption; actual player healing and simultaneous deaths; sacrifice tint/target/escape/hit/heal/prayer semantics. |
| T10 ice | Native freeze; containment shape/collision/delay; prison associated icicle click, all escape/outcome paths, prayer, player death/logout/teleport during prison, full removal. |
| T11 Zaros/death | Normal/forced melee, Soul Split actual-loss heal, Deflect, Turmoil and overhead rotation; late prayer; Wrath actual death center/radius/timing; bank/outside exclusion; native death once. |
| T12 lifecycle | Leave/teleport/logout/disconnect/death/empty reset with each delayed mechanic pending; no stale hits/tasks/NPCs/locs/HUD/targets; two consecutive kills and instance entity-count invariants. |
| T13 rewards | Source eligibility boundaries, team shares, MVP ties, unique/common/tertiary topology and exact weights after proof; guard ceremonial loot; all recipient drop tiles/ownership and multiple uniques. |
| T14 completion | One native kill dispatch; duplicate callbacks, disconnect/reconnect, stable account identity, no duplicate items/KC/PB/log/broadcast; Nexling native award; shared clue behavior. |
| T15 persistence/CA/reclaim | PostgreSQL transactions, concurrent/replayed completion, restart recovery, item-var/fee/capacity reclaim; all11 cache-described CA success/failure/team conditions and award idempotency; pet recall/ownership. |
| T16 cache | SERVER native stats/params and LIVE models/options; NPC11277-11286/11293-11294; all used seq/spot/projanims/sounds/locs/interfaces/vars/reward items and CA rows; aliases must resolve to actual240 definitions. |

Required implementation commands (not claimed run yet):

```powershell
.\gradlew.bat :content:bosses:nex:test :or-cache:test --tests dev.openrune.NexR240CacheAuditTest --no-daemon --console=plain
.\gradlew.bat :content:bosses:nex:check --no-daemon --console=plain
```

Then all affected shared module suites and appropriate server build/check. Do not deploy a failing
build or fix unrelated failures. Controlled rebuild and two restarts must retain the custom-build
marker, doctor identity, cache paths/revision, PostgreSQL and source hashes.

Live checklist remains entirely **not completed by this pass**: natural bank/public/private entry,
solo/team/all phases/specials/guards/targeting/prayer/collision, deaths/disconnects/delayed effects,
rewards/pet/CA/reclaim/altar, two consecutive kills and two restarts. Capture a real OpenRune fight
through RSProx where possible; preserve raw/transcript and normalize events against a genuine normal
OSRS baseline. Record exact matches/mismatches/decoder gaps. No graphical-client or packet-diff
result is currently claimed.

## Initial read-only audit checkpoint

- Read root AGENTS/contribution guidance and searched nested instructions; inspected complete Nex
  source/tests and relevant shared dirty diff; compared pinned official source and local examples.
- Ran initial doctor and verified canonical running revision240 authority; no restart.
- Discovered/hashed/transcribed six candidates; four genuine Grid Master fight captures, two rejected.
  Retained raw Wiki revisions and source/capture provenance.
- Ran `:content:bosses:nex:test --no-daemon --console=plain`: **PASS,17 tests,0 failures/errors/skips**,
  23seconds. XML timestamps2026-09-09T09:23:33.434Z; [baseline summary](../../../logs/nex-audit-20260909/baseline-tests.json).
  These tests verify existing formulas/constants only, including contradicted/unproven assumptions.
- Final `git diff --check`: **PASS**; all205 baseline dirty/untracked source hashes unchanged;
  no missing local ledger links. [Checkpoint verification](../../../logs/nex-audit-20260909/checkpoint-verification.json).
- Repeated doctor with `-SearchRoots @((Get-Location).Path) -SkipGradleVersion`: **PASS**, exit0;
  same canonical JVM32336, revision240 and SERVER/LIVE fingerprints.
  [Doctor result](../../../logs/nex-audit-20260909/doctor-checkpoint-canonical.log).
  An earlier default-root repeat was stopped during its broad Documents duplicate-source scan,
  after reporting runtime/cache identity; it is not counted as a completed doctor run.
- Corrected documentation, not gameplay. No new native Nex integration/cache/runtime tests yet.
  No OpenRune-versus-OSRS event diff, live client verification or parity acceptance.
- **User approval of M01-M36 / S01-S08 received after this audit.** Approval covers only these
  native-compatible scopes and evidence-backed rules, not guessed resolutions of E01-E09.
  Material conflicts and newly discovered shared changes still require a focused follow-up.

## Approved implementation progress

### S02: impact-time damage resolution foundation

Implemented the approved reusable extension in `NpcHitEvents`, `StandardNpcHitProcessor`,
`NpcScriptEventExtensions` and `BossCombat`. This is an **approved new local shared API**, not an
API that existed in the pinned upstream revision. Its owning queue/hit/event pipeline is the
verified upstream workflow identified in S02.

- Optional keyed `Resolve` runs at impact after administrative one-hit adjustment and before HP,
  hitmarks, contribution and native death. No handler preserves the existing processing path.
- The queued `Hit` stays immutable; requested damage is separately clamped to zero/current HP.
  Native `Impact` observes accepted damage. Existing source attribution and impact callbacks stay
  in their original pipeline; no second contribution/death pipeline was added.
- Existing local `HitScope` validity is checked before resolution and again after the handler.
  `BossCombat` registers optional resolve/impact handlers only when supplied, retaining existing
  queue-time `onLethal` behavior for current consumers.
- `NpcHitResolutionTest` executes the real processor for current-HP races, dynamic immunity,
  zero/private hitmarks, indirect-source preservation, canceled scopes, bounds, native overkill,
  and administrator one-hit ordering. Seven new tests plus five existing module tests pass.
- `NexNativeHitQueueTest` executes native `NpcHitScript`, NPC queue processing and the hit processor
  for two arriving hits across an immunity change, duplicate lethal hits, and retired burn hits.
  The injected floor is a **test probe**, not a claim about Nex's unresolved damage formula.

**Integration boundary:** Nex's production combat registration and existing queue-time damage
policy have not yet been migrated to this API. Passing these tests does not fix or certify Nex
thresholds, siphon healing, damage caps, rewards, or the complete encounter. E02/E03 remain open.

### M35: unsupported Wilderness encounter removed

Removed the second run, automatic spawn/enrollment, custom final-phase attacks, leash correction,
Wilderness arena constants and their three rules-only tests from the existing Nex module.
Canonical Ancient Prison entry, targeting and rewards no longer branch on this custom mode.
No unrelated Wilderness spawn file or multiway geography was changed.

`NexStartupTest` executes production manager startup through native world queues and observes
exactly one canonical Nex plus four bodyguards, with no Wilderness enrollment. Its repository
is mocked, so this proves the removed branch is not scheduled; it does **not** prove native NPC
registration, correct OSRS spawn presentation or live room behavior.

Focused command:

```powershell
.\gradlew.bat :content:bosses:nex:test :api:npc:test --no-daemon --console=plain
```

Result: **PASS**, Nex 18 tests (14 retained rules tests, three native queue tests, one startup
test), NPC 12 tests; zero failures/errors/skips. See
[focused result](../../../logs/nex-audit-20260909/native-foundation-tests-2.log).
The first run caught a test mock returning Boolean from the void repository `add` method;
the mock was corrected, with no production workaround or weakened assertion.

The separate rune-pouch fix completed two controlled restarts before these Nex source changes;
see [pouch verification](../../pouches/ui-capacity-fix.md). The current canonical live JVM is
**28712**, revision 240. These Nex/shared changes have **not** been deployed or runtime-verified.
No Nex client fight or OSRS-versus-OpenRune packet comparison has been completed.

Broader foundation verification:

```powershell
.\gradlew.bat -I scripts/format-selected.init.gradle '-PopenruneFormatManifest=docs/bosses/nex/native-foundation-format-files.txt' :api:bosses:spotlessApply :api:npc:spotlessApply :api:script:spotlessApply :content:bosses:nex:spotlessApply :content:bosses:nex:check :api:npc:test :api:script:test :api:bosses:test :content:bosses:royal-titans:test :content:bosses:vorkath:test :server:app:assemble --no-daemon --console=plain
```

**PASS**, 1m37s, 474 tasks. Nex 18, NPC 12, Royal Titans 97 and Vorkath 103:
**230 tests, zero failures/errors/skips**. `api:script:test` and `api:bosses:test` are
`NO-SOURCE`, not additional passing suites. Nex check uses the explicit source-format manifest;
this is not a repository-wide formatting check. Server assembly succeeded, not deployed.
[Build log](../../../logs/nex-audit-20260909/native-foundation-regression-build.log).

Reviewed the relevant diff. Removed formatter-only hunks from unrelated functions in
`NpcScriptEventExtensions` and excluded that file from further bulk formatting; its only
remaining changes are the new handler and queue-time documentation. `git diff --check` passes;
LF/CRLF notices are Git conversion warnings, not whitespace-check failures.
Of the 209 pre-restart dirty/untracked source snapshots, 206 remain byte-identical. The three
expected differences are this ledger, the S02 processor hunk, and the completed pouch verification
document. The processor's pre-existing source-attribution hunk is preserved. New Nex/API files
were separately reviewed; nothing was staged, committed or restored wholesale.
