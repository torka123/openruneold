# Royal Titans continued implementation — 2026-09-08

Status: implemented content corrections, not a finished encounter or 1:1 certification.
This report supersedes the previous phase where noted. Read-only agents audited upstream APIs,
decoded packet timelines, current sources and active cache values. Only the primary agent edited.

## Continued changes

- Native manual/autocast wall handlers dispatch the existing Standard/Ancient spell registry.
  Valid spells consume runes once, give no cast/damage XP, preserve existing action delay, use
  native projectile timing and remove the selected segment plus immediate neighbours on impact.
  Invalid spells emit the captured messages without casting. Ancient wall casts never apply
  AoE/freeze; Twinflame substitution uses native requirements/unlimited runes, with no wall echo.
  Player-removal examples have no wall-break sound, so that sound is suppressed for that cause.
- Persistent immutable PostgreSQL completion/participant/death tables via native GameDatabase.
  Stable completion IDs reject changed retry payloads. Duo completion counts once globally and
  once per eligible account. Account locks serialize PB decisions. One SQL snapshot supplies
  personal/global counts and minima. Varp4648 is projected only after commit and on login.
  Pending completions now survive an empty/destroyed run and retry independently. A reconnecting
  finisher's varp is refreshed without resurrecting the run or advertising unavailable loot.
  Global record-holder selection cannot name someone from a slower completion when the fastest
  completion has no participant record. Empty and PB-ineligible participant cases are tested.
- Scoreboard loc opens native interface909 with named components and committed statistics;
  close909:2 is registered. Time formatting uses integer hundredths, including5:20.40.
  Counts use English thousands separators, matching `58,395,586` / `967,404` in capture3402 T60.
- Last-defeated Titan spawn+1 determines displayed duration. It is not the old shared timer or
  an entry/first-attack timestamp. All40 capture completion messages fit this rule.
- Completion emits exact KC, duration/PB and eligible-loot text. Unclaimed corpses no longer
  reset at30 ticks. First successful claim starts normal-capture collapse+21, deletion+26,
  Branda respawn+29, Eldric+31. Collapse uses11992/11987 and Branda3224 slot2. Floor death variants
  move to collapse rather than first defeat. Full loc replacement/network ordering remains open.
- Enter/Join/Quick-start use correct loc operation slots. Native warning mesbox/choice flow
  persists16089 suppression even before a join attempt. Pending Eldric spawn is treated as600HP
  for joining. An initially suspected protection-lock bug was not reproduced; its proposed bypass
  was withdrawn, keeping the normal native access guard. Name-dialog/prefill/crawl remain open.
- Both existing drop-table amulet entries now award30637 uncharged instead of30638 charged.
  Shared hard/elite clue eligibility predicates now require *claimed* state2. The Royal tables'
  hardcoded23/95 rolls still need selection integration; this predicate fix alone does not fix them.

## Files changed in this continuation

Royal Titans module:

- `build.gradle.kts`: native spell/DB dependencies and isolated PostgreSQL test dependency.
- `RoyalTitansWallInteractions.kt` (new), `RoyalTitansCombat.kt`, `RoyalTitansRules.kt`:
  native wall dispatch/impact handling, exact shared wall spell acceptance.
- `RoyalTitansEncounterManager.kt`: deferred wall removal, completion persistence, timer origin,
  claim-reset clock, corpse/floor presentation and early join handling.
- `RoyalTitansWorld.kt`: scoreboard, real option routing and persistent warning flow.
- `RoyalTitansStatistics.kt` (new): native DB adapter, immutable SQL event operations/snapshots.
- `RoyalTitansScoreboard.kt` (new): native interface/text/login projection and time formatting.
- `src/main/resources/db/migration/V42__royal_titans_statistics.sql` (new).
- `RoyalTitansCombatIntegrationTest.kt`: expanded real-instance lifecycle/entry/kill-order tests.
- `RoyalTitansWallCastTest.kt` (new): manual/autocast/Ancient/Twinflame/rune/impact tests.
- `RoyalTitansStatisticsTest.kt` (new): real PostgreSQL transactions, concurrency and restarts.
- `docs/api-mapping-review.md`, `capture-validation.md`, `native-correction-report.md`,
  `shared-api-approval.md` (new), and this report.

Existing spell-attacks module:

- `RoyalTitansWallSpells.kt` (new shared content helper), `standard/ElementalSpells.kt`,
  `ancient/AncientCombatSpells.kt`: reuse the existing casts/FX and native rune/hit APIs.
- `standard/RoyalTitansElementalSpellTest.kt`: updated dependencies and separate wall behavior.
- `arceuus/ArceuusReanimationRuntimeTest.kt`: narrow Mockito cleanup only; repeated retained
  collision fixtures exhausted the full-suite heap. No Arceuus gameplay/source changed.

Existing drops module:

- `tables/monsters/BrandaTheFireQueenDropTable.kt`, `EldricTheIceKingDropTable.kt`: amulet identity.
- `ClueScrollDropChecks.kt`: hard/elite claimed-tier checks; other tiers untouched.
- `ClueRewardTierTest.kt` (new), `build.gradle.kts` (canonical test working directory and serial
  execution for cache-backed tests sharing the singleton decoders).

All paths above are under the existing source module packages. Earlier phase files are listed in
the earlier report. No second encounter/command/nexus or generated-directory source was created.
User AGENTS, PlayerDeath and Vorkath work remains untouched. Nothing staged or committed.

## Original defect disposition after continuation

| Original numbers | Disposition |
|---|---|
| 1–5 | Open: independent patrol/arrival schedules, ranged state, vertical packs/pathing/targeting. New detailed normal movement evidence is recorded, not claimed implemented. |
| 6 | Partial: injected safe RNG; true candidate distribution remains unproved. |
| 7 | Core wall spell removal/cast behavior implemented and tested; exact network loc replacement/add-delete order still open. |
| 8–10 | Earlier native damage/max-hit improvements retained. Source provenance/August saturation and full equipment/splash remain open. |
| 11–14 | Earlier Blast-survivor, elemental-death, uncapped crossing and separated-spawn fixes retained; full encounter timing/visual proof remains open. |
| 15–17 | In-place corpse lifecycle plus first-claim collapse/reset now covered. Per-player option packets, accepted delayed claims during collapse and complete floor packet order remain open. |
| 18–20 | Corrected spawn/active sounds and removed guessed projectile; new player wall-removal sound distinction. Live sound/graphics comparison remains open. |
| 21 | Partial: native option routing and persistent warning. Exact name input/reuse, friend-access policy and crawl/entry visuals still incomplete. |
| 22 | Native scoreboard wired to SQL. Live interface/viewer verification remains open. |
| 23–30 | Still open overall: reward delivery/allocation, quantities/unique replacement/tertiaries/scroll suppression. Amulet identity and shared CA clue predicate corrected, not full reward parity. |
| 31 | SQL KC/deaths/PB/global implementation and isolated DB tests added. Real startup/migration smoke verification is recorded below; actual client kill/death persistence remains unverified. |
| 32 | Completion row and participant snapshot are transactional/idempotent. Durable claim allocation/delivery and recovery of uncommitted runtime events remain open. |
| 33–35 | Outside graves, complete queued-hit cancellation and stale instance ownership remain open. No fork-only workaround introduced. |
| 36 | All11 CA integration remains open; shared award/event APIs need review. |
| 37 | More executable tests added. Golden routes, whole live encounter/rewards/CAs still missing. |
| 38 | Ledger corrected again for timer origin, first-claim reset, wall XP/impact/audio and native SQL scope. |

## Evidence

Exact ticks/lines and native/cache identities are in [capture-validation.md](capture-validation.md).
New important references: wall casts2283 T1339/L18248 andT1345/L18379; removal1341/1348;
wall invalid text2283T1324/L17928 and3402T1408/L18436; claim warning2283T1830/L30951;
collapse1851/L31114; timer2283L30734,3398L5867,3402L26731/L36060;
scoreboard3402T60/L3410-3417; warning3402T1169-1170/L15188-15194.

SERVER item identities30637/30638 and native varbits16089/16097/16098/12865/12866 were decoded
against revision240. LIVE scripts4782/4790 distinguish pending CA rewards1 from claimed2.
Raw captures were not re-decoded this continuation. No packet dump was fabricated.

## Verification and release gates

Final-source command results are below. Earlier successful intermediate runs:
wall suite36 tests; statistics addition38; lifecycle addition40. Those are not final-source counts.
Initial exploratory failures included an incorrect test expectation for Twinflame unlimited water
runes, a missing test working directory, and an incorrect assertion about access-protection state.
They are not represented as gameplay fixes or silently ignored.
The forced broader rerun exposed a shared cache-decoder initialization race between the new clue
test and an existing Grotesque Guardians test. The drops test JVM now executes those tests serially;
the full rerun passes. No Grotesque Guardians gameplay/test source was altered.

The original canonical GameServer PID32320 was gracefully stopped through the existing operational
shutdown helper. Real server startup checks are recorded below. No solo/duo client encounter,
outgoing OpenRune packet recording or event-by-event diff has been completed. An obscured-window
screenshot attempt was discarded and is not visual evidence. Test PostgreSQL restarts are **not**
live server restart evidence.
The authoritative `.data/postgres` received migration V42 through normal startup. Its location and
both cache paths/revision remain unchanged. Isolated test
clusters live under newly allocated `logs/royal-titans-statistics-test-*`, never in the live data.

Important limits: database transactions protect committed events, not a crash before an event is
committed. Pending in-memory death retries must not be described as a durable outbox. Faster
late-join PB eligibility, >=450 equality and other listed OSRS uncertainties remain unproved.

The exact shared integrations needing approval are in [shared-api-approval.md](shared-api-approval.md).
Native content work was not stopped merely because private loot delivery or transient scoreboards
were inconvenient: both existing public routes were inspected and the feasible pieces implemented.
No claim of finished, commit-ready or1:1 parity is warranted.

### Commands and results

The exact required command was run again against the review-fixed source:

```powershell
.\gradlew.bat :content:bosses:royal-titans:test :or-cache:test --tests dev.openrune.RoyalTitansR240CacheAuditTest --no-daemon --console=plain
```

Passed in 46 seconds: `logs/royal-titans-continued-required-final-20260908.log`.
Royal Titans executed 43 tests; the cache task was UP-TO-DATE in this command, then explicitly
re-executed in the broader verification command:

```powershell
.\gradlew.bat :content:bosses:royal-titans:check :content:skills:magic:spell-attacks:test --rerun :content:drops:test --rerun :api:db:test --rerun :api:combat:combat-formulas:test --rerun :content:other:commands:test :or-cache:test --tests dev.openrune.RoyalTitansR240CacheAuditTest --rerun :server:app:assemble --no-daemon --console=plain --continue
```

Passed in 51 seconds: `logs/royal-titans-continued-broader-verified-20260908.log`.
The preceding attempt (`...broader-final...`, 1m16s) failed on the cache initialization race above;
it is retained as failed evidence, not overwritten. Server assembly completed in that attempt and
was UP-TO-DATE in the passing rerun. The earlier broader assembly passed in 1m26s.

| Test module | Tests | Failures/errors/skipped |
|---|---:|---:|
| Royal Titans | 43 | 0/0/0 |
| Spell attacks | 65 | 0/0/0 |
| Drops | 46 | 0/0/0 |
| Database | 1 | 0/0/0 |
| Combat formulas | 11 | 0/0/0 |
| Required revision-240 cache audit | 1 | 0/0/0 |

Total: 167 tests. Commands module reports NO-SOURCE, not a passed test suite. Royal `check` includes
Spotless; the final broader run reused its already-passed tests. XML-derived counts are also in
`logs/royal-titans-verified-test-counts-20260908.json`.

The new database tests use real PostgreSQL for rollback, simultaneous/duplicate completions,
consistent readers, death identities, reconnect and two isolated database restarts. Combat tests
use native repositories, instance access, hit queues and event handlers, but mock statistics/HUD/
drop-service collaborators where identified in the tests. They are not packet or live-client tests.

### Real server startup and restart evidence

The existing `scripts/cerberus-controlled-restart.ps1` operational helper was reused. It verifies
the target JVM's canonical working directory and main class before invoking normal shutdown hooks;
no process was force-killed. Its historical filename/log directory does not imply a second boss
implementation. The helper launches `:server:app:run --no-daemon --console=plain` hidden.

| Helper restart number | Old PID -> new PID | Ready time in log | Source |
|---|---|---|---|
| 2026090801 | 32320 -> 39012 | 20:37:10, 18.522s startup | Before the final retry/record-holder review fixes |
| 2026090802 | 39012 -> 10160 | 20:47:03, 17.719s startup | Final gameplay source, first restart |
| 2026090803 | 10160 -> 35084 | 20:49:18, 17.935s startup | Same final gameplay source, second restart |

Every startup printed `OPENRUNE CUSTOM BUILD ACTIVE`; every post-start doctor identified the
canonical `user.dir`, cache revision 240, and unchanged SERVER/LIVE dat2 hashes. The final server
remains running as PID35084. The final two startup logs each report 432 loaded scripts and a ready
server. Startup logs include tick-overrun warnings (767ms and711ms respectively); no claim of
performance or combat-timing parity is made.

Evidence files under the canonical root:

- `logs/royal-titans-restart-{1,2,3}-command-20260908.log`.
- `logs/cerberus-audit-20260907/restart-202609080{1,2,3}.stdout.log` and corresponding stderr logs.
- `logs/royal-titans-doctor-restart-{1,2,3}-20260908.log`.
- `logs/royal-titans-live-database-restart-{1,2,3}-20260908.log`: read-only JDBC queries confirm
  V42 success and the same zero completion/participant/death rows. This proves migration startup
  and preservation, **not** a live kill/death/reward transaction.
- `logs/royal-titans-source-before-final-restarts-20260908.json` and
  `logs/royal-titans-unrelated-before-final-restarts-20260908.json`: SHA-256 comparisons passed
  after both final-source restarts, including the unrelated AGENTS/PlayerDeath/Vorkath files.
- `logs/royal-titans-diff-check-final-20260908.log`: `git diff --check` exit0. Its line-ending
  conversion warnings are not whitespace errors. No source was restored from Git.

### Runtime scenarios and event diff

Completed in the actual server: normal save/shutdown, revised startup, plugin/Guice registration,
revision240 cache load, native V42 migration, listening server, canonical-root doctor and two
final-source restart checks. No gameplay was injected into the running JVM.

Not completed in a client: solo/duo entrance and warning screens, threshold joins, movement,
staggering, walls, elementals, transition landings, Blast, either complete kill order, rewards,
scoreboard after real kills/deaths, teleport/logout/death cleanup or repeated fights.

OpenRune-versus-capture event diff: **not produced**. There is no outbound OpenRune encounter
trace from this continuation to compare. Decoded capture mining and native integration-test state
are not substitutes for that trace. The original38-defect disposition above remains the release
checklist; passing build/restarts does not close those mechanical, reward, CA or visual gaps.
