# Rooftop agility repair — PR #216

## Scope and source

Requested source: [cd51a72](https://github.com/OpenRune/OpenRune-Server/pull/216/changes/cd51a72db78f0bcd5de471923ccfdfb14c6c9e97).
Also reviewed and incorporated rooftop-only fixes from [4de22de](https://github.com/OpenRune/OpenRune-Server/commit/4de22deef41230d1ddf0ceb5ff58b194922eb379), a later revision in that PR.
This is contribution code in an open PR, not a claim that the PR has merged.
No blanket cherry-pick, shortcuts, tunnels plugin, or cheat-menu changes.

The existing Agility module remains authoritative. Its nine old rooftop definitions and matching
routes were removed as their replacements were integrated. Gnome, Barbarian, Prifddinas and
Wilderness agility implementations were preserved. No shared engine, cache, database or combat
content files were changed for this task.

## Implementation

- Nine data-driven rooftop routes: Draynor, Al Kharid, Varrock, Canifis, Falador, Seers,
  Pollnivneach, Rellekka and Ardougne.
- Native protected loc operation/approach handlers, cache gamevals and animation durations.
- Cache-timed climbs and glides; explicit glide plane for cross-level jumps; zipline grab/slide.
- One-tile-per-tick balance, handhold and monkey-bar movement with temporary native base animations.
- Al Kharid rope swing approach handler allows operation from its landing platform.
- Revision-240 native collision testing additionally required Pollnivneach approach handlers:
  market stall/banner/drying line use range 2; gap/tree/wall/monkey bars/tree top use range 1.
  The former local routes already used custom approaches here. These are narrowly registered;
  enabling approach handlers on every rooftop obstacle disturbed normal route requests and was
  rejected by the real-map test.
- Strict ordered lap progression, not the PR's unordered bit mask. Failure, logout and interrupted
  attempts invalidate progress. The finish only grants one lap bonus per completed route.
- Native skill XP and environmental queued hits. No private hit pipeline.
- Existing local energy restoration and mark policy retained: course chances (including
  Falador 2/5 and Ardougne 2/3), 300-tick mark cooldown, Canifis overlevel exception,
  1,000-tick lifetime and private ownership. PR defaults would have changed these.
- Long approach movement uses the real route finder; wrong-plane clicks cannot pull players upstairs.
- Interrupted movement restores the previous base animation set. Landing cleanup only changes a
  still-owned movement tile and cannot undo an external teleport or a death.

## Evidence limits

API cross-check: official upstream revision `61139e76a216592d66c2b20366ef26d48609875d`,
including `api/route/.../RouteFactory.kt` and `api/player/.../protect/ProtectedAccess.kt`.
The native loc events, route finder, protected movement, animation/BAS, XP, environmental hit queue,
attributes and object repository are reused. Existing local teleport hooks remain in their normal
pipeline; no new hook, engine override or shared API was added.

Coordinates, move selection, failure rules and animation choices are sourced to the two PR revisions.
Revision-240 cache checks validate referenced locs, first operations, sequence durations and map
placements. Tests exercise native server movement and interactions; they are not client video or
OSRS packet comparisons. The PR's interpolated failure probability is inherited, not independently
proven as OSRS's exact probability. No 1:1 OSRS visual/timing claim is made.

## Verification

Passed: 26 Agility tests (6 definition regressions, 5 non-rooftop route regressions, 14 native
runtime tests and 1 complete-cache/map integration test). The map integration test clicks every
obstacle through native approach/operation processing, using actual r240 locs and collision,
for two consecutive laps on all nine courses. It does not reposition the player between obstacles
or laps, and exercises both split-ledge loc variants. Separate deterministic tests cover low-level
rejection, all configured failures, XP, duplicate clicks, reordered/skipped stages, logout,
interruption/death/teleport cleanup, balance cadence, pathfinding, and mark ownership/cooldown.

Passed: 83 Wilderness tests, including the 18 existing Wilderness agility rules tests.
Passed: server application assemble, including normal and shadow distributions.

Final build command:

```powershell
.\gradlew.bat -I scripts/format-selected.init.gradle '-PopenruneFormatManifest=docs/agility/rooftop-format-files.txt' :content:skills:agility:test :content:skills:agility:check :content:areas:wilderness:test :server:app:assemble --no-daemon --console=plain
```

Result: Gradle BUILD SUCCESSFUL in 1m 17s, 463 tasks (16 executed).
Log: `logs/rooftop-agility-20260909-verified-build.log`.
An unscoped check first reported pre-existing formatting in untouched `AgilityScript.kt` and
`AgilityMissingLocsScript.kt`; those files were not reformatted. The final check limits formatting
validation to the explicit changed-source manifest, without filtering executable tests.
Early test runs also exposed concurrent global cache initialization and insufficient heap when
decoding the entire map. Tests now run serially, in isolated class JVMs, with a 4 GiB maximum heap.
One exhausted test worker was stopped; the live server was unaffected.

## Changed files

All gameplay edits are within `content/skills/agility/`:

- `build.gradle.kts`: native attribute/route dependencies and test dependencies/settings.
- `AgilityDefinitions.kt`, `AgilityRoutes.kt`: remove the old rooftop registrations/data only.
- `AgilityObstacles.kt`: protected movement/animation helpers.
- `rooftop/RooftopCourse.kt`, `rooftop/RooftopCourses.kt`: sole rooftop data model and nine routes.
- `rooftop/RooftopScript.kt`: native interactions, progression and XP.
- `rooftop/MarksOfGrace.kt`: retained mark policy through ObjRepository.
- `AgilityDefinitionsTest.kt`, `AgilityRouteCoverageTest.kt`: retain non-rooftop regression coverage.
- `AgilityMapPlacementTest.kt`, `RooftopRuntimeTest.kt`: cache/native gameplay coverage.

Documentation: this report and `docs/agility/rooftop-format-files.txt`.
No staging, commit, push, source restore or blanket PR merge was performed.

## Deployment

First controlled restart: original canonical JVM 30824 stopped normally; restart 2026090923
started JVM 17264. Custom-build marker at 07:20:08, 436 scripts loaded and server ready at 07:20:10
(local log time). Doctor confirms canonical cwd, revision 240 and the same PostgreSQL authority.
Logs: `logs/cerberus-audit-20260907/restart-2026090923.stdout.log`,
`logs/rooftop-agility-20260909-doctor-restart-1.log`.

All 12 changed source/build-script file hashes were unchanged after the rebuild/first restart.
Agility jar SHA-256: `4A6239AF7810725C70121D59645641D49394D7E0630944910CB516B5A09F27CF`.
SERVER dat2 hash remains `72A2EEDDB5FB463EBB5D952BDC12E519BCE5CE8F0E5D3F39DD389AE035C0E6F6`.
LIVE dat2 hash remains `FD0FE5EF66039A791A900EDDDF1CDBCFE57E09E8C32EA3496C69E96217A41A7B`.
PostgreSQL authority remains `.data/postgres`; no persistence schema or cache revision/path changes.

Second controlled restart: JVM 17264 stopped normally; restart 2026090924 started JVM 41396.
Custom-build marker at 07:21:54, 436 scripts loaded and server ready at 07:21:56.
Doctor again confirms the canonical cwd, revision 240 and `.data/postgres`.
Logs: `logs/cerberus-audit-20260907/restart-2026090924.stdout.log`,
`logs/rooftop-agility-20260909-doctor-restart-2.log`.
All 12 source/build-script hashes, the agility jar and both cache dat2 hashes remained unchanged.
`git diff --check` passed after deployment. The live server is running this build.

## Remaining verification

No live-client visual run or RSProx packet comparison was performed. Animated appearance, exact
OSRS timing/failure probabilities and visual landing polish are not independently verified.
The automated map test does verify continuous playable server routes, not just endpoint constants.
The changes are isolated for a focused review/commit; repository-wide formatting cleanliness and
unrelated pending work are not claimed. Nothing has been staged or committed.
