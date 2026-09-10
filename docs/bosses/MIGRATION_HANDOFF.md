# Boss migration checkpoint and player test guide

This is a resumable handoff, not a completion or live-deployment claim. The detailed authority,
mechanic mappings, uncertainties, commands and results remain in [DSL_MIGRATION.md](DSL_MIGRATION.md).
Update this checkpoint after each completed boss and immediately before any authorized deployment.

## Current checkpoint

- Canonical workspace: `C:\Users\norma\Documents\Codex\2026-08-10\lets-work-on-the-openrune-server\work\OpenRune-Server`.
- Source safety baseline: `798b8c320`, branch `backup/boss-dsl-source-baseline-20260909`.
- Additional passing source checkpoint: `69f9deeba`,
  `backup/boss-dsl-passing-20260909-muspah-lifecycle`. Includes native Muspah membership/respawn,
  hazard retirement, native KC metadata, DSL final cadence and the spatial split (60 passing tests).
  This local Git backup includes no new runtime/cache/PostgreSQL copy and is not a deployment.
- Current work: `refactor/muspah-boss-dsl-20260909`. Re-read actual Git state on resume.
- Completed source migrations: Kraken, Vorkath, Corporeal Beast, Alchemical Hydra, Grotesque Guardians,
  Zulrah. This means source/headless migration, not client or full OSRS parity certification.
- Latest passing verification: `logs/zulrah-final-native-check.log` (43 Zulrah tests and cache audit);
  `logs/zulrah-broad-regression.log` (all applicable boss/shared tests, app classes, architecture check;
  49s/509 tasks). Shared boss API has 41 passing tests. Final Zulrah commit: `2aecefe5f`.
- Muspah partial checkpoint verification: `logs/muspah-native-dsl-final.log` (54 tests, scoped check);
  `logs/muspah-broad-regression.log` (all applicable boss/shared/drop tests, app classes, architecture;
  24s/509tasks, including unchanged passing up-to-date results).
- Zulrah uses declarative rotation programs, native instances/death/respawn and scoped spatial
  handlers. Its private membership and ordinary attack/phase timers are removed.
- Muspah is **in progress**: ordinary attacks now use BossSpec/native NvPCombat; 54 tests pass
  (14 native, 40 retained). Fixed an unmapped projectile controller, blocked-attempt RNG/melee
  counter mutation and post-exit ordinary projectiles. Native encounter initialization precedes
  activation so an incoming hit cannot re-lock melee movement. Private instance/phase/hazard and
  respawn ownership still require migration; do not mark Muspah finished or deploy a half-wired change.
- Live deployment: **not performed during this migration**. Existing server PID must be rechecked;
  the last doctor identified 41396 in this canonical workspace. Do not assume it is still current.
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

## Changes ready for testing after deployment

| Component | Source changes | Headless verification already completed | What to test in the client |
|---|---|---|---|
| Shared boss DSL | Delays, parallel effects, repeat gaps, injected selection RNG, cadence/forced counters, native volleys, hit scopes and cancellation | Native interpreter/combat regression suite; exact counts in detailed report | No double attacks, wrong delays or attacks after leaving; smoke-test existing DSL bosses such as Scurrius |
| Shared instances | Source-defined InstanceScript, typed session state, protected entry/exit, keyed lifecycle cleanup, native coordinated actor reset | Native lifecycle/reset/isolation tests | Entry, exit, teleport, logout/disconnect, death and re-entry; separate rooms must not affect each other |
| Kraken | Ordinary boss/tentacle combat moved to DSL; native room membership/spawns/cleanup | 12 module tests | Enter (`::kraken`/`::krak` is a convenience), awaken whirlpools, fight, loot, leave and re-enter |
| Vorkath | Normal/forced attacks and phases on DSL; native instance lifecycle; acid/spawn mechanics retained as focused handlers | 110 module tests plus cache audit | Natural boat entry, awakening, normal attacks, acid walk, zombified spawn, death/reclaim, loot, repeat and exit |
| Corporeal Beast | DSL ordinary attack selection; native public-room lifecycle, focused core and split-area handlers | 24 module tests | Both existing lairs, melee/magic attacks, core, stomp, group targeting, room reset and loot |
| Alchemical Hydra | DSL normal volleys/phase rotations; native instance and same-actor respawn; removed duplicate KC increment | 22 module tests | `::hydra`, observation-room doors, vents/forms/specials, prayer, loot/KC once, two kills, `::leavehydra` |
| Grotesque Guardians | Two DSL specs; native roof instance, paired forms and reset; base-Dusk drop/KC identity fixed | 21 module tests; 48 drop-module tests; all boss/shared regression tasks and app classes pass | Natural roof entrance, bell, both attacks, lightning/spheres, hammer, prison, loot/KC once, 17-tick paired reset, repeat and exit |
| Zulrah | Four DSL rotation programs and snake specs; native boat instance and same-actor respawn; scoped clouds/eggs/tail; native KC metadata | 43 module tests (21 native), revision-240 cache audit, broad boss/shared regressions | Natural boat entry/camera, all forms and rotations, clouds, eggs/snakes, tail dodge, prayer, death/reclaim, loot/KC once, two kills, exit/teleport/logout; debug mode must award nothing |
| Architecture enforcement | Existing detector/self-tests now run from root Gradle check; explicit reviewed waivers retained | `bossArchitectureCheck` passes | Developer check, not a visible game change |

Important: Guardians KC needs the intentional seven-line source overlay and Zulrah KC needs its
eight-line overlay in `.data/raw-cache/server/npcs.toml` packed during an authorized controlled
deployment. Both were tested in memory through the native kill hook; SERVER/LIVE have **not** been
repacked. Do not silently deploy only jars and declare those metadata fixes live. Preserve the
unrelated source-data hunks in that file.

For each fight record: account, solo/group, entry path, observed problem, approximate tick/time and
a clip/RSProx capture when possible. Test both normal completion and departure during a delayed
attack. Check inventory/bank/reclaim/KC after reconnect and two controlled restarts. Passing unit
tests and a boot marker do not certify visuals or full OSRS parity.

## Work still required

1. Finish Muspah's native instance/session, phase, forced-special, hazard and respawn migration.
   Characterize threshold races, native reward/KC identities, two kills and all departure paths
   before removing the old ownership code. Preserve the completed ordinary attack DSL and tests.
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
