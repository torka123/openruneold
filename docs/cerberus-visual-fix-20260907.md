# Cerberus combat dispatch follow-up — 2026-09-07

**Superseded runtime verification:** the next user recording exposed a second guard failure after callback selection. See [the allocated-instance regression follow-up](cerberus-instance-guard-fix-20260907.md). The 40-test result below tested dispatch selection and did not establish that the encounter manager launched attacks. The second Medal clip was successfully downloaded and inspected in that follow-up; the failed download below describes the earlier attempt.

The missing Souls and invisible magic/ranged attacks exposed a combat-dispatch defect that the earlier rule tests did not cover. The earlier audit's passing tests did not establish a working observed fight. Full OSRS parity remains unaccepted; the unresolved evidence ledger and full-fight/manual checklists in [the main audit](cerberus-20260907-audit-report.md) still apply.

## Confirmed defect and correction

`CerberusEncounterManager.spawnBoss` creates base NPC 5863 (sitting). `activate` transmogs that NPC to visible 5862 (active). OpenRune's `Npc.id` still returns the original `type.id`, while `visType.id` reports the transformation. `AiPlayerEvents.Ap2` and `NpcQueueEvents.Type` dispatch using the original ID; incoming `NpcHitEvents.Impact` uses the visible ID.

The combat plugin previously registered attack and death callbacks only for 5862. The normally spawned encounter therefore selected the generic combat handler: the Cerberus attack counter, custom magic/ranged projectiles, triple attack, Souls and lava launchers were bypassed. Its custom death cleanup/respawn callback was also absent under the sitting spawn's ID.

The same encounter callbacks now register for all three boss forms: sitting 5863, active 5862 and resetting 5866. Existing ownership, generation and phase guards still govern execution. No shared NPC identity semantics, projectile constants, cache paths or cache revision were changed by this follow-up.

## Reference comparison

The user-supplied [Near-Reality Cerberus source](https://github.com/MouldyToast/Offline_Play/blob/f0744c78168eefcd28ac75b6a0f64234767e1852/near-reality-server-main/core/src/main/java/com/zenyte/game/content/boss/cerberus/Cerberus.java#L348) was downloaded at commit `f0744c78168eefcd28ac75b6a0f64234767e1852`. Its `setTransformation` explicitly assigns `this.id = id`, unlike OpenRune's visual-only transformation. This explains why its encounter structure cannot be ported by assuming equivalent NPC identity behavior.

That implementation is useful comparative source code, not proof of current OSRS timing. Its lava-before-Souls priority, older lava animation and respawn value were not adopted. No donor source was copied into the engine or used as a substitute for an OSRS fight capture.

Saved reference files under `logs/cerberus-visual-fix-20260907/`:

- `reference-commit.txt`
- `Cerberus.java`
- `SummonedSoul.java`
- `CerberusRoom.java`
- `medal.html`

The supplied Medal page was fetched, but the embedded video CDN returned Access Denied. No successful MP4 download or video inspection is claimed. No new RSProx `.bin` or `.txt` fight capture was found or downloaded in this follow-up.

## Deterministic regression and verification

`CerberusCombatDispatchTest` loads the real revision-240 cache and exercises OpenRune's actual EventBus, NPC transmog and AI event selection. It covers the normal sitting-to-active spawn, all nine base/visible boss combinations, death callback registration under immutable identity, and preservation of unrelated NPC generic combat.

The regression was first run with the original active-only binding preserved: **4 tests ran, 3 failed**, reproducing both generic-combat fallback and missing death registration. With all three forms bound, the same tests pass. Test setup initially needed an import correction and an Int-to-Long event key conversion; those compile failures were resolved before the reproducing run.

| Command | Exact result |
|---|---|
| `.\gradlew.bat :content:bosses:cerberus:test --tests org.rsmod.content.bosses.cerberus.CerberusCombatDispatchTest --console=plain` before fixing bindings | Expected failure: 4 tests, 3 failures; 15s, 146 tasks (2 executed, 144 up-to-date). |
| `.\gradlew.bat :content:bosses:cerberus:test --console=plain` after fixing bindings | SUCCESS: 40 tests, 0 failures/errors/skips; 14s, 146 tasks (3 executed, 143 up-to-date). |
| `.\gradlew.bat :or-cache:test --tests dev.openrune.CerberusR240CacheAuditTest --console=plain` | SUCCESS, up-to-date; 3s, 18 tasks. |
| `.\gradlew.bat :or-cache:test --tests dev.openrune.CerberusR240CacheAuditTest --rerun :server:app:installDist --console=plain` | SUCCESS: fresh real-cache audit 2 tests, 0 failures/errors/skips; complete server distribution rebuilt; 16s, 445 tasks (5 executed, 440 up-to-date). |

Cerberus XML totals: 27 rule tests + 7 decision tests + 2 death-location tests + 4 real-cache dispatch tests = **40**. These are not 40 complete simulated fights. The fresh cache audit again passed without the reported `GameValProvider$load$3` class-loading error.

No shared gameplay module was changed in this follow-up. Earlier shared test results and the unrelated consumables/agility failures from the broad project test remain documented in the main audit. They were not represented as a passing full-project test here.

## Runtime and manual verification

Before changes/restart, doctor confirmed GameServer PID 35992 used the canonical working directory. The corrected build passed two controlled shutdown/start cycles: **35992 → 16800 → 29880**. Doctor confirmed both new JVMs use that same canonical directory.

- `.\scripts\cerberus-controlled-restart.ps1 -ServerProcessId 35992 -RestartNumber 5`: exit 0; `logs/cerberus-audit-20260907/restart-5.stdout.log` records `OPENRUNE CUSTOM BUILD ACTIVE` at 04:46:24 EDT and `Server ready` at 04:46:27.
- `.\scripts\cerberus-controlled-restart.ps1 -ServerProcessId 16800 -RestartNumber 6`: exit 0; `logs/cerberus-audit-20260907/restart-6.stdout.log` records the same marker at 04:48:21 EDT and `Server ready` at 04:48:24. Final JVM: **29880**.
- `.\scripts\openrune-doctor.ps1 -SkipGradleVersion -SearchRoots .`: exit 0 before and after the controlled restarts; cache revision remains 240 and the SERVER/LIVE/PostgreSQL authorities remain unchanged.
- Both starts logged one startup cycle-overrun message (779ms and 735ms respectively); no encounter or duplicate-registration exception was observed in those startup logs. These operational restarts do not substitute for a player completing a fight or a post-kill persistence check.

- [ ] Enter a fresh instance with `::cerberus` or `::cerb`; wait for sitting-to-standing.
- [ ] Confirm `::cerbdebug status` attack counter advances above 1 during combat. The status-only command does not taint rewards.
- [ ] Observe the opening magic/ranged/melee triple and visible normal magic/ranged projectiles at distance and adjacent to the 5×5 boss.
- [ ] Keep the boss below 400 HP through a due Soul wave; verify three visible Souls and one attack each in west-to-east order. Allow for due-special skips and triple priority.
- [ ] Keep the boss below 200 HP through a due lava attack; verify three pools and the text/animation.
- [ ] Complete two ordinary kills and confirm death cleanup and sitting respawn; verify kill count/rewards once per eligible kill.
- [ ] Exercise the main audit's reset, death, logout, protection/equipment, overlap and persistence checklist. A post-fix client recording and synchronized full OSRS capture are still needed before claiming 1:1 behavior.

## Files changed in this follow-up

- `content/bosses/cerberus/src/main/kotlin/org/rsmod/content/bosses/cerberus/CerberusCombat.kt`
- `content/bosses/cerberus/src/test/kotlin/org/rsmod/content/bosses/cerberus/CerberusCombatDispatchTest.kt`
- `content/bosses/cerberus/build.gradle.kts`
- `scripts/cerberus-fetch-visual-reference.ps1`
- `docs/cerberus-visual-fix-20260907.md`
- `docs/cerberus-20260907-audit-report.md`

Generated reports and build outputs were produced by Gradle only. Unrelated existing uncommitted work was retained.
