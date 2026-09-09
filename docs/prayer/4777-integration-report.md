# Prayer fixes from PR #216

Source: Blade9766's [commit 4777f6e9374f4c633502359a6d8485bedd1bc1c8](https://github.com/OpenRune/OpenRune-Server/commit/4777f6e9374f4c633502359a6d8485bedd1bc1c8),
co-authored by Claude Fable 5.1. Only this commit's two behavioral changes were integrated; this
is a working-tree integration, not a merge of PR #216 or a new Git commit. The deferred level-up
commit was not applied. Grand Exchange settings and other existing work were left unchanged.

## Mechanic-to-native-API mapping

The existing implementation was inspected before editing. No new engine API or private combat
hook was needed. The requested patch and standing project authorization cover these existing APIs:

| Fix | Existing implementation/API | Evidence |
| --- | --- | --- |
| Reset healing and boosted-stat timers when all prayers stop | `Player.disablePrayers`, `Player.softTimer`, shared `constants` | Referenced commit; manual-off path in prayer-tab `disablePrayerStatRegen`; `PreservePrayerScript` |
| Correct client button state on a zero-point activation | Existing `PrayerTabScript.enablePrayer`, `Player.resyncVar`, native `mes` | Referenced commit; same resync path already used for failed requirements/protection locks |

The two reset intervals are the existing **100-tick** constants. Rapid Heal uses 50 ticks while
active; Preserve uses 150 ticks for boosted-stat decay. The upstream comment incorrectly said
both timers were sped up; the integration comment distinguishes faster healing from slower decay.
No rates, requirements, prayer locks, combat effects, or cache definitions were otherwise changed.

## Files changed

- `api/player/src/main/kotlin/org/rsmod/api/player/PlayerExtensions.kt`: add both native timer resets
  after existing prayer/queue cleanup.
- `content/interfaces/prayer-tab/src/main/kotlin/org/rsmod/content/interfaces/prayer/tab/scripts/PrayerTabScript.kt`:
  resync the rejected prayer's var and send the existing depletion message. Scoped formatter also
  wrapped the pre-existing registration loop; its behavior is unchanged.
- `content/interfaces/prayer-tab/build.gradle.kts`: test-only packet/Mockito dependencies and
  canonical-root, serial cache-backed test configuration.
- `content/interfaces/prayer-tab/src/test/kotlin/org/rsmod/content/interfaces/prayer/tab/PrayerLifecycleTest.kt`:
  six executable regression tests using the revision-240 cache, native players, registered prayer
  queue/timer handlers, recording clients and native timer maps.
- `docs/prayer/4777-format-files.txt` and this report.

Tests cover complete disable cleanup (including Preserve activation cancellation), timer interval
and expiry reset, unrelated-timer preservation, actual prayer-drain depletion, every configured
prayer rejected at zero points, repeated zero-point resync packets, ordinary Rapid Heal activation
and manual deactivation, and disabling an already-enabled prayer at zero points.

## Verification

Before editing, `scripts/openrune-doctor.ps1` confirmed PID 1420 used the canonical project root
and revision 240 (`logs/prayer-4777-doctor-before.log`). The pre-existing dirty source was hashed
in `logs/prayer-4777-source-hashes-before.json`.

Focused tests (preceded in the same Gradle invocation by manifest-scoped Kotlin formatting):

```powershell
.\gradlew.bat :content:interfaces:prayer-tab:test :api:player:test --no-daemon --console=plain
```

Broader regression/build:

```powershell
.\gradlew.bat :content:interfaces:prayer-tab:test :api:player:test :api:death:test `
  :content:other:special-attacks:test :content:bosses:vorkath:test `
  :content:bosses:royal-titans:test :content:other:grand-exchange:test `
  :server:app:assemble --no-daemon --console=plain
```

Execution logs: `logs/prayer-4777-focused-1.log`, `logs/prayer-4777-regression-build.log`.

Results on 9 September 2026:

- Focused run: **24 tests passed** (prayer tab 7, player API 17), build successful in 1m1s.
- Broader run: **298 tests passed**, zero failures/errors/skips, assembly successful in 1m24s.
  Counts: prayer tab 7, player API 17, special attacks 51, Vorkath 103, Royal Titans 97, GE 23.
  The death API test task reported `NO-SOURCE`. Counts are recorded in
  `logs/prayer-4777-test-summary.json`.
- Two controlled normal-shutdown restarts passed. Restart `2026090917` reached ready at 05:11:09
  local (PID 33672); restart `2026090918` reached ready at 05:12:34 (PID 32336).
  Both logged `OPENRUNE CUSTOM BUILD ACTIVE`, loaded 435 scripts, and became ready without a
  prayer registration/startup error. The server remains running as PID 32336.
- Startup logs: `logs/cerberus-audit-20260907/restart-2026090917.stdout.log:1359` and
  `logs/cerberus-audit-20260907/restart-2026090918.stdout.log:1359`.
- Doctor logs `logs/prayer-4777-doctor-restart-1.log` and
  `logs/prayer-4777-doctor-restart-2.log` verify canonical JVM working directory and revision 240.
- All **205 recorded source files** and the server/player/prayer JAR hashes survived both restarts
  unchanged. Both SERVER and LIVE data-file hashes also remained unchanged from the GE deployment.
  Exact hashes are in `logs/prayer-4777-restart-verification.json`.
- All **199 pre-existing changed/untracked files** remained byte-identical to the pre-task
  snapshot, including the GE work. `git diff --check` passed. No staging, commits, source resets,
  persistence migrations or cache maintenance were performed.

The pre-existing cycle-overrun startup warning and PostgreSQL connection-close shutdown error
recurred. Both normal shutdown/restart operations completed and both new JVMs became ready. These
warnings are not counted as passing tests or claimed fixed by this narrowly scoped prayer patch.

## Verification boundary

The tests execute registered prayer handlers and inspect outgoing messages/varp packets. This
does not constitute desktop-client visual playtesting or an audit of all prayer mechanics.
The requested commit is the behavioral source; this task does not claim independently captured
OSRS packet parity. Cache paths/revision and persistence formats are not changed by this patch.
