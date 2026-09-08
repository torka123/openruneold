# Cerberus stalled encounter regression — 2026-09-07

**Later user feedback:** the encounter was working apart from Souls reacting to player movement. The [Soul position follow-up](#soul-position-follow-up) below addresses that remaining reported defect and records the latest verification. Earlier process IDs and the 50-test totals in the body are historical.

The second Medal report exposed another blocking defect after the combat-dispatch correction. The dispatch tests selected the correct callback but did not execute the encounter manager inside an allocated instance. They therefore missed the guard that rejected every attack. This follow-up reproduces and corrects that failure using the real instance allocator, cache definitions, combat formulas, repositories, delayed world queues and outgoing zone packets.

Full OSRS parity is still unaccepted. Emitting packets in a deterministic server harness does not establish that every graphic renders correctly in the client or matches current OSRS.

## Confirmed defects and changes

1. **All custom attacks were rejected.** `InstanceSession.regionIds` contains `Region.uid` allocation identifiers. Cerberus compared those identifiers with `(coords.mx << 8) | coords.mz`, an OSRS map-square number. The boss became ACTIVE but stayed at attack counter 1; normal projectiles, the opening triple, Souls and lava never launched. `CerberusArena` now resolves the physical allocation through `RegionRegistry` and checks its UID against the owning session before allowing attacks or delayed hits. Both player and boss must be inside that allocation.
2. **The death-item destination used the same wrong identifier.** The normal death-location hook returned null for a real Cerberus instance. It now uses the same allocation check and returns the existing recoverable public exit. The old tests fabricated map-square IDs as region UIDs; they have been replaced with actual allocated-session tests, including rejection of another instance and an external destination.
3. **Source-room geometry was interpreted directly from allocated coordinates.** Chamber, previous-position barrier checks and lava boss-footprint filtering now normalize through the registry's existing source-zone mapping before applying source-local masks. The tests validate all 4,096 source tiles in each of four simultaneously retained allocations. This establishes translation correctness, not the accuracy of the provisional OSRS chamber mask.
4. **The repository overwrote the encounter's respawn flag.** `NpcRepository.add(npc, Int.MAX_VALUE)` sets `respawns=true`. Setting it false before registration therefore allowed generic death handling to schedule a respawn in addition to Cerberus's own sitting respawn. Boss and Soul flags are now set after registration. The integration tests verify that the repository-registered NPCs do not request generic respawn and that encounter death completion creates exactly one sitting boss.
5. **The Medal fetcher discarded CDN authentication parameters.** The helper now preserves the full signed MP4 URL, accepts an explicit clip ID, and can extract contact sheets or a full-size frame. No encounter timing or asset values were changed from video impressions.

No shared gameplay source, public entrance, command registration, cache revision/path, account state or reward table was changed in this follow-up.

## Evidence and saved media

- Engine sources: `api/instances/.../InstanceManager.kt` allocates sessions from `region.uid`; `api/registry/.../RegionRegistry.kt` allocates monotonic UIDs and supplies `normalizeCoords`; `api/repo/.../NpcRepository.kt` assigns `respawns` during registration. These are the evidence for the corrections above.
- User regression video: [Medal clip ntCLWSCGqEJ1E1Qbf](https://medal.tv/games/screen-capture/clips/ntCLWSCGqEJ1E1Qbf). Successfully downloaded and inspected this follow-up, including the contact sheet and a full-resolution frame at 18 seconds. The 36.854-second recording shows the reported stalled encounter; Cerberus has 176/600 HP in the sampled frames, the player remains in the chamber, and no Soul wave, lava launch or projectile is visible in those samples. Chat shows god mode enabled, so lack of player HP loss alone is not diagnostic. This is an OpenRune regression recording, not an OSRS reference or a post-fix recording.
- The [Near-Reality source comparison](cerberus-visual-fix-20260907.md#reference-comparison) remains comparative evidence only. No donor timing changes were made here.
- [Original evidence ledger](cerberus-rev240-evidence.md) and [main audit](cerberus-20260907-audit-report.md) retain the Wiki, RuneLite, cache and RSProx evidence and limitations.

Saved files, relative to the canonical project root:

```text
logs/cerberus-visual-fix-20260907/medal-ntCLWSCGqEJ1E1Qbf.html
logs/cerberus-visual-fix-20260907/medal-ntCLWSCGqEJ1E1Qbf.mp4
logs/cerberus-visual-fix-20260907/medal-ntCLWSCGqEJ1E1Qbf-contact-sheet.png
logs/cerberus-visual-fix-20260907/medal-ntCLWSCGqEJ1E1Qbf-preview.jpg
```

No new RSProx `.bin` or decoded `.txt` capture was acquired in this follow-up. The original captures 1702/3250 still do not prove live combat. The earlier supplementary `.bin`/`.txt` downloads and negative search results are listed in the main audit.

## Deterministic verification

`CerberusEncounterIntegrationTest` uses a manually advanced `MapClock`, `DefaultGameRandom(42L)`, real revision-240 cache definitions, actual allocated instances, ordinary combat formulas and `WorldQueueListProcess`. Its source collision fixture is explicitly flat. It inspects actual `MapProjAnimV2` packets and normal queued `Hit` objects. It does not run a client, render NPC animations, simulate movement steps, or process a complete player's damage/death/reward transaction.

The initial four regression tests against the previous gameplay source produced **4 tests, 3 failures**: counter stuck at 1, death destination null, and generic respawn enabled. The source-normalization test passed for the initially reused allocation; the final fixture additionally retains concurrent allocations through standard occupancy flags and checks every source tile. Test-fixture compile/setup issues were resolved before counting successful regressions.

The final ten integration tests cover:

- Chamber activation and source mapping across four allocated clones.
- Opening magic/ranged/melee order at launch ticks 0/2/4, outgoing graphic IDs and player target indices, and prayer switches after each launch.
- A seeded 35-attack low-health fight that actually produces normal and special projectiles, Souls and active lava.
- Three different unhittable Soul forms, west-to-east one-shot projectile execution, fixed 30-damage queued hits, and despawn.
- Exactly three distinct lava packet destinations, owned-instance checks, initial 10-damage queued hit, and no hit queued against the second instance.
- Boss/Soul generic respawn flags, boss death at six Soul stages, duplicate death completion and exactly one sitting respawn.
- Leaving between triple components, destroying the old session, reentering, and rejection of the previous run's hits/timers.
- Stale membership after external movement and real death-item destination resolution.

| Command | Result |
|---|---|
| `.\gradlew.bat :content:bosses:cerberus:test --tests '*CerberusEncounterIntegrationTest' --console=plain --quiet` against previous gameplay source | Expected failure: 4 tests, 3 failures; exit 1, 23s. |
| `.\gradlew.bat :content:bosses:cerberus:test --console=plain --quiet` final source | PASS: exit 0; XML totals 50 tests, 0 failures/errors/skips. |
| `.\gradlew.bat :content:bosses:cerberus:test --console=plain` final verification | PASS: exit 0, 33s, 146 tasks (2 executed, 144 up-to-date); 50 tests, 0 failures/errors/skips. |
| `.\gradlew.bat :or-cache:test --tests dev.openrune.CerberusR240CacheAuditTest --rerun :server:app:installDist --console=plain` | PASS: exit 0, 26s, 445 tasks (5 executed, 440 up-to-date); fresh cache audit 2 tests, 0 failures/errors/skips, and complete server distribution. No `GameValProvider$load$3` class-loading failure. |
| `.\gradlew.bat :api:instances:test :api:registry:test :api:repo:test --console=plain` | PASS: exit 0, 13s, 146 tasks up-to-date; existing 20 instance and 7 registry tests have zero failures/errors/skips. Repository unit tests: NO-SOURCE. |

Final Cerberus count: 27 rules + 7 decisions + 4 dispatch + 2 real-instance death-location + 10 encounter integration = **50**. The historical broad project test still has unrelated consumables test-compilation and agility map-placement failures documented in the main audit; a passing whole-project test is not claimed.

## Runtime verification

Doctor confirmed the starting GameServer PID 29880 used the canonical working directory before gameplay changes. Cache revision remains 240, `.data/cache/SERVER` remains the active cache, `.data/cache/LIVE` supplies JS5 groups, and `.data/postgres` remains the persistent database authority.

The rebuilt gameplay source passed two controlled shutdown/start cycles: **29880 → 20928 → 17200**. Both restart commands exited 0. Both JVMs reported the canonical project `user.dir`; doctor completed with exit 0 after each restart.

- `.\scripts\cerberus-controlled-restart.ps1 -ServerProcessId 29880 -RestartNumber 7`: `logs/cerberus-audit-20260907/restart-7.stdout.log` records `OPENRUNE CUSTOM BUILD ACTIVE` at **05:31:58 EDT**, then `Server ready` at **05:32:02**.
- `.\scripts\cerberus-controlled-restart.ps1 -ServerProcessId 20928 -RestartNumber 8`: `logs/cerberus-audit-20260907/restart-8.stdout.log` records the same marker at **05:34:44 EDT**, then `Server ready` at **05:34:48**. Final GameServer JVM: **17200**.
- `.\scripts\openrune-doctor.ps1 -SkipGradleVersion -SearchRoots .`: exit 0 before gameplay changes and after both controlled restarts. No source/cache/database authority was changed by doctor.
- Each startup logged one cycle overrun (1094ms and 875ms); no encounter or duplicate-registration exception was found in those startup logs. Operational restart success does not establish an observed fight or a completed account-persistence transaction.

## Remaining acceptance gaps and manual checklist

The [full-fight capture checklist](cerberus-20260907-audit-report.md#explicit-full-fight-capture-checklist--still-required) still applies. Exact current OSRS projectile profiles, close/distant June-2025 lava reaction delays, chamber geometry/collision, Soul movement/visual ordering, fire-wall lifecycle/sounds, music/ambient behavior and respawn clock anchor remain unproven. This fix deliberately does not replace those unknowns with new guessed values. Already-launched Soul hits are currently invalidated on boss death; OSRS same-tick ordering remains unresolved. The absent cannon placement system and broader interaction/persistence limitations remain listed in the main audit.

- [ ] Record two ordinary complete kills after this restart, including the opening triple, normal magic/ranged at range and adjacent to the boss, and sitting respawn.
- [ ] Confirm `::cerbdebug status` advances above counter 1; use only `status` for reward-eligible verification.
- [ ] Keep Cerberus below 400 HP through a Soul wave and below 200 through lava. Check special overlaps at attacks 21 and 35 and allowable skips.
- [ ] Switch protection before/after normal, triple and Soul launches. Verify Spectral, Ward, both together, Elysian, low Prayer, and every requested lava-protection combination in-game.
- [ ] Cross the barrier both ways, step under and around the 5x5 footprint, attack from chamber edges, leave to reset, and reenter.
- [ ] Die with projectiles/Souls/lava pending; test simultaneous boss/player death and recover normal death items at the public exit.
- [ ] Use `::leavecerberus`, an external teleport, logout/disconnect and fresh entry while specials are pending; confirm no old hazards return.
- [ ] For each eligible kill, confirm one KC increment/message, ashes/main roll, actual collection entries, genuine Slayer progression and event-based achievement awards. Confirm debug kills award none.
- [ ] Verify these values after relog and a controlled server restart. The operational restarts below do not themselves prove a post-kill account transaction.
- [ ] Compare synchronized post-fix OpenRune video/packets with a complete current OSRS fight before declaring 1:1.

## Files modified in this follow-up

```text
content/bosses/cerberus/build.gradle.kts
content/bosses/cerberus/src/main/kotlin/org/rsmod/content/bosses/cerberus/CerberusArena.kt
content/bosses/cerberus/src/main/kotlin/org/rsmod/content/bosses/cerberus/CerberusEncounterManager.kt
content/bosses/cerberus/src/main/kotlin/org/rsmod/content/bosses/cerberus/CerberusDeathDropLocation.kt
content/bosses/cerberus/src/test/kotlin/org/rsmod/content/bosses/cerberus/CerberusTestCache.kt
content/bosses/cerberus/src/test/kotlin/org/rsmod/content/bosses/cerberus/CerberusEncounterIntegrationTest.kt
content/bosses/cerberus/src/test/kotlin/org/rsmod/content/bosses/cerberus/CerberusCombatDispatchTest.kt
content/bosses/cerberus/src/test/kotlin/org/rsmod/content/bosses/cerberus/CerberusDeathDropLocationTest.kt
scripts/cerberus-fetch-visual-reference.ps1
docs/cerberus-instance-guard-fix-20260907.md
docs/cerberus-visual-fix-20260907.md
docs/cerberus-20260907-audit-report.md
```

The earlier complete task file list remains in the main audit. Git status/diff were inspected; Cerberus and scripts are already untracked in this working tree, so a tracked-file diff alone is insufficient. Source changes were applied explicitly; generated output was only produced by Gradle/media tooling. Unrelated existing uncommitted work was preserved.

## Soul position follow-up

The user confirmed the encounter was otherwise working but reported that Souls moved when the player moved. Inspection found that the Soul launcher retained default NPC movement behavior, left movement unlocked, and called `facePlayer` on each Soul when it attacked. That last call sends a persistent entity-facing target to the client, so the NPC continues turning with the player after the attack.

Souls now use the existing `NpcMode.None`, ignore ordinary combat interactions, and have zero hunt range. Their facing is fixed along their existing approach lane. The encounter unlocks movement only for its existing approach and retreat paths; on attack arrival it clears leftover routes and locks the attack position. The NPC no longer receives a player-facing target. The projectile still targets the player, and attack order, timings, damage, prayers, spawn coordinates and despawn rules are unchanged.

Evidence for this correction is the user's requested behavior and the standard engine `NpcModeProcessor`, `NpcMovementProcessor`, `NpcFaceSquareProcessor`, and `Npc.facePlayer`/`lockFacing` implementations. It is not a newly captured OSRS choreography claim. No new assets, coordinates, sounds or timing values were introduced.

Two additional allocated-instance regression tests exercise the ordinary movement/facing processors. Against the old source, both failed: one found `EntityFaceTarget(entitySlot=65537)` tracking the player, and the other demonstrated a Soul moving from source-local `(23,40)` to `(24,40)` when given an unscheduled route. After the fix they pass. The existing wave test also verifies disabled generic AI for all three Soul forms. The first post-fix test attempt initially included the scheduled retreat in its stationary assertion; it was corrected to check player-driven updates during the attack hold, preserving the existing retreat timing.

- Before fix: `.\gradlew.bat :content:bosses:cerberus:test --tests '*attacking Souls*' --console=plain --quiet` — expected exit 1, 2 tests, 2 failures, 21s.
- After fix: `.\gradlew.bat :content:bosses:cerberus:test --console=plain --quiet` — exit 0, **52 tests, 0 failures/errors/skips**: 27 rules, 7 decisions, 4 dispatch, 2 death-location, 12 encounter integration.
- `.\gradlew.bat :or-cache:test --tests dev.openrune.CerberusR240CacheAuditTest --rerun :server:app:installDist --console=plain` — exit 0, **2 cache tests, 0 failures/errors/skips**, complete server distribution built; 16s, 445 tasks (5 executed, 440 up-to-date).
- Both controlled restarts exited 0: `.\scripts\cerberus-controlled-restart.ps1 -ServerProcessId 17200 -RestartNumber 9`, then `-ServerProcessId 8196 -RestartNumber 10`. JVM sequence: **17200 → 8196 → 31476**.
- `restart-9.stdout.log` records `OPENRUNE CUSTOM BUILD ACTIVE` at **05:48:34 EDT** and `Server ready` at **05:48:37**; `restart-10.stdout.log` records the marker at **05:50:05** and ready at **05:50:08**. Logs are under `logs/cerberus-audit-20260907/`. Each start logged one cycle overrun (801ms and 930ms); no encounter/duplicate-registration exception was found.
- `.\scripts\openrune-doctor.ps1 -SkipGradleVersion -SearchRoots .` exited 0 before gameplay edits and after both restarts, confirming the canonical working directory. Cache revision/path and PostgreSQL authority are unchanged. Git status/diff were checked before and after edits; unrelated work was retained.

Files changed in this Soul follow-up only:

```text
content/bosses/cerberus/src/main/kotlin/org/rsmod/content/bosses/cerberus/CerberusEncounterManager.kt
content/bosses/cerberus/src/test/kotlin/org/rsmod/content/bosses/cerberus/CerberusEncounterIntegrationTest.kt
docs/cerberus-instance-guard-fix-20260907.md
```

Manual check: enter a fresh instance, keep Cerberus below 400 HP until Souls appear, and move east/west while each Soul attacks. Verify they hold their lane and facing, their projectiles still reach the player, and their scheduled retreat/despawn completes.
