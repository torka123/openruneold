# Boss migration checkpoint and player test guide

This is a resumable handoff, not a project-completion or full OSRS-parity claim. The detailed authority,
mechanic mappings, uncertainties, commands and results remain in [DSL_MIGRATION.md](DSL_MIGRATION.md).
Update this checkpoint after each completed boss and immediately before any authorized deployment.

## Current checkpoint

- Canonical workspace: `C:\Users\norma\Documents\Codex\2026-08-10\lets-work-on-the-openrune-server\work\OpenRune-Server`.
- Source safety baseline: `798b8c320`, branch `backup/boss-dsl-source-baseline-20260909`.
- Additional passing source checkpoint: `69f9deeba`,
  `backup/boss-dsl-passing-20260909-muspah-lifecycle`. Includes native Muspah membership/respawn,
  hazard retirement, native KC metadata, DSL final cadence and the spatial split (60 passing tests).
  This local Git backup includes no new runtime/cache/PostgreSQL copy and is not a deployment.
- Deployed source: Muspah checkpoint `f35675be2`, shared phase clock `f0f1d8d99` (46 API tests),
  deployment checkpoint `c48e36cad`, diagnostic-harness follow-ups `c40dca8ad` and `7784e155e`.
  Current checkpoint branch: `chore/boss-migration-checkpoint-20260909`; re-read actual Git state.
- Recoverable predeployment source branch: `backup/boss-dsl-predeploy-20260909-muspah` (`c48e36cad`).
  Source-only ZIP: `logs/boss-dsl-predeploy-20260909-muspah-source.zip`, SHA256
  `C1B93AE0C0B631FB7DB2938792A66C88DB5D1B1C8629B31B036F16E4F4603AAB`.
  Diagnostic follow-ups are also backed up on `backup/boss-dsl-live-audit-checkpoint-20260909`
  and `backup/boss-dsl-native-logout-checkpoint-20260909`. No remote push/merge occurred.
- Completed source migrations: Kraken, Vorkath, Corporeal Beast, Alchemical Hydra, Grotesque Guardians,
  Zulrah and Muspah. This means source/headless migration, not client or full OSRS parity certification.
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

1. Collect user client-test feedback. Backup, local deployment, two gameplay checkpoint restarts,
   persistent fingerprints and the corrected75-assertion live Vorkath audit are complete. Do not
   repeat them merely because an older section or message says they are pending. Visual/full OSRS
   acceptance remains separate.
2. Continue Cerberus, Royal Titans, Nex, Yama, Doom and DT2 in separate focused migration
   branches/commits. Review Dagannoth Kings separately. Preserve existing research and mechanics.
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

Optional `-CopyPrompt` copies the prompt to the clipboard. Optional `-Verify -Boss muspah` runs the
doctor, architecture detector and focused boss/API/instance tests first. It does not modify source,
switch branches, commit, deploy, restart or contact GitHub. Build/test outputs are the usual local
generated files. Paste its output into a new session in the canonical workspace after allowance resets.
