# Boss migration checkpoint and player test guide

This is a resumable handoff, not a project-completion or full OSRS-parity claim. The detailed authority,
mechanic mappings, uncertainties, commands and results remain in [DSL_MIGRATION.md](DSL_MIGRATION.md).
Update this checkpoint after each completed boss and immediately before any authorized deployment.

## Current checkpoint

Latest deployed update (September10): Royal Titans source `1279d1676` has129 passing module tests
and3 cache audit cases. Native platform/arena/teleport phases, paired reset and elemental
death-deadline ownership are implemented; focused handlers leave an830-line coordinator
(original2,017). Scoped and broad505-task checks pass. Two controlled restarts are complete:
36776 ->25860 ->39612, with canonical revision240 doctor/marker checks and matching fresh
persistence/cache fingerprints. See the detailed deployment record below for shutdown warnings.
Earlier incremental sections are history, not instructions to repeat completed work. Next is Nex;
its first3 native minion characterization tests now pass (21 Nex tests total), with NO production
Nex migration edits yet. Existing mechanics/reward/CA
uncertainties and client acceptance remain explicitly open; this is not full OSRS parity.

Recoverable Royal Titans source backup: `backup/royal-titans-native-checkpoint-20260910`,
commit `1279d1676`; ZIP `logs/royal-titans-native-checkpoint-20260910.zip`, SHA256
`46FF15C79D008FE4407D930EDA0226E24C3A1739BCCA940A66145BA1901EE1AC`.
7,457 entries, zero excluded-path violations. No database/cache/runtime data is in this archive.
The user reported8% plan allowance at this checkpoint; do not infer a new remaining percentage
from context capacity. Finish the current bounded work and use this handoff at the requested cutoff.

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
2. Current branch `refactor/nex-boss-dsl-20260910` contains the passing minion characterization
   checkpoint only:21 Nex tests, scoped check/shared suites/app classes/architecture enforcement
   pass in37s/458tasks, log `nex-minion-characterization-verified-20260910.log`. Read the ledger's
   September10 mapping, add departure/deletion regressions, then wire ordinary guards/Reavers
   through the existing DSL. Do not treat the characterized legacy20/18 damage or811 animation
   as OSRS-correct. No Nex production changes or extra restart occurred after the RT deployment.
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
