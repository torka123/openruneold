# Nex revision-240 evidence ledger and approval mapping

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

**Live production source: `41fd77bff`, minion DSL migration only. Main Nex is not yet converted.**
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
