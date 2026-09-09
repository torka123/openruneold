# Phase-one damage correction — 2026-09-09

Scope: the user's report that the Titans do not hit before their second-position teleport. This is
a targeted platform-routing correction, not certification of the entire encounter as 1:1.

## Reproduction and correction

The real revision-240 arena collision (`SERVER`, region 11669, source 2880/9536) marks the fire and
ice platforms `BLOCK_WALK=0x200000`. The Titans used normal-terrain movement. Both stayed at their
initial anchors through 65 deterministic test ticks and never reached the arrival-driven stomp.
The earlier synthetic flat-map fixture did not represent this restriction.

The correction is content-only:

| Mechanic | Existing native API |
|---|---|
| Titan-only platform movement | `Npc.moveRestrict = MoveRestrict.Blocked` |
| Matching route and step collision | `RouteFactory.create(..., titan.collisionStrategy)`; `Npc.walk` and native movement processor |
| Stomp damage/prayer/timing | Existing `Player.queueHit` → `PlayerHitScript` → `StandardPlayerHitProcessor`; unchanged |
| Second-phase behavior | Restore type movement restriction at each landing; no scheduler/damage/visual changes |

Compared the established NPC movement pipeline and Cerberus's use of `moveRestrict`; selected
`Blocked`, not Cerberus's broader `BlockedNormal` projectile-collision mode. `Blocked` retains
loc/wall collision and cannot walk onto the normal central walkway. No shared engine API, cache
revision/path/definition, damage value, prayer rule, reward, or persistence behavior was changed.

## Files edited in this pass

- `src/main/kotlin/org/rsmod/content/bosses/royaltitans/RoyalTitansEncounterManager.kt`
- `src/test/kotlin/org/rsmod/content/bosses/royaltitans/RoyalTitansCombatIntegrationTest.kt` — fixture
- `src/test/kotlin/org/rsmod/content/bosses/royaltitans/RoyalTitansPhaseOneDamageTest.kt` — four new tests
- `src/test/kotlin/org/rsmod/content/bosses/royaltitans/RoyalTitansMovementTest.kt`
- `src/test/kotlin/org/rsmod/content/bosses/royaltitans/RoyalTitansElementalMovementTest.kt`
- `docs/capture-validation.md`, this report, `docs/phase1-damage-format-files.txt`

Existing uncommitted/untracked changes remain in place; these paths are relative to the existing
Royal Titans module. The explicit formatting manifest limits formatting to the five touched Kotlin
files; unrelated source was not formatted or restored.

## Evidence

- Cache: `GameMapDecoder.putMaps/putLocs`, decoding SERVER map archive11669 files0/1 in the fixture;
  the new test checks the 3x3 footprint for every supported inner/outer anchor at rows 25..35.
- Native collision: `MoveRestrict.Blocked`, `game/movement/MoveRestrict.kt`,
  `routefinder/collision/CollisionStrategy.kt`, `NpcMovementProcessor`, `StepFactory`.
- Normal capture2283: initial anchors B(2906,9566), E(2914,9566), initial retreat to2904/2916;
  opening return B1300/1301 at(2905,9562)/(2906,9562), face1302, stomp1303, damage1304;
  E1303/1304 at(2915,9562)/(2914,9563), face1305, stomp1306, impact1307.
  Exact line references are in the ledger addition. Eldric1314's20 confirms five-tile forward reach.
- Prayer rule: [current strategies revision15329439](https://oldschool.runescape.wiki/w/Royal_Titans/Strategies?oldid=15329439):
  protection fully blocks the first-phase stomp while both live. The rule was preserved, not added.

## Verification

The initial real-map regression failed for both prayer settings before the production correction.
Follow-up testing exposed and corrected test fixture assumptions and an intermediate early
movement-restoration side effect before deployment. Final command/runtime results follow below.

No fresh client fight or OpenRune-versus-RSProx packet capture was performed in this pass. The
golden comparison is an executable server-side movement/facing/animation trace with injected
observed destination choices, not a claim of complete packet parity or target-selection proof.

### Commands and tests

- Required command: `gradlew.bat :content:bosses:royal-titans:test :or-cache:test --tests dev.openrune.RoyalTitansR240CacheAuditTest --no-daemon --console=plain`.
  Passed: 96 Royal Titans tests, 3 cache audits, zero failures/errors. Final rerun was up-to-date,
  successful in 16s; preceding executing run succeeded in 54s. Logs:
  `logs/royal-titans-phase1-damage-required-tests.log`, `logs/royal-titans-phase1-damage-final-tests.log`.
- Scoped formatting and `:content:bosses:royal-titans:check :server:app:assemble`: successful in
  1m19s, including all 96 tests after extending the real-map golden to the first stomps.
  Log: `logs/royal-titans-phase1-damage-check-assemble.log`. Formatting used the explicit manifest
  and `scripts/format-selected.init.gradle`; no whole-tree formatting claim is made.
- `git diff --check`: passed; `logs/royal-titans-phase1-damage-diff-check.log`.
- Deployed module SHA-256: `FCF2E1E777FC0ECB608B4C90D623FEA505C8A9B50FAE926294535C9908174970`.

### Runtime and restart checks

Times are local America/New_York on 2026-09-09. Initial pre-change JVM was PID41432, confirmed
canonical by the doctor before edits. It was stopped through the project's normal shutdown helper,
not force-killed. Rebuilt startup and two subsequent controlled restarts completed:

| Run | PID | Custom-build marker | Server ready |
|---|---:|---|---|
| Rebuilt startup | 29224 | 01:53:36 | 01:53:38 |
| Restart 2026090905 | 24496 | 01:54:57 | 01:55:00 |
| Restart 2026090906 | 37260 | 01:56:03 | 01:56:05 |

All three loaded 432 scripts and revision240. Each doctor check confirmed the canonical JVM
`user.dir`; the module hash stayed identical across the restarts. Final PID37260 listens on
43594, 8080 and9091. Logs: `logs/royal-titans-phase1-damage-start.stdout.log`,
`logs/cerberus-audit-20260907/restart-2026090905.stdout.log`,
`logs/cerberus-audit-20260907/restart-2026090906.stdout.log`; doctor logs use the
`royal-titans-phase1-damage-doctor-` prefix with `before`, `start`, `restart1`, `restart2`.

Cache fingerprints before/after match:

- SERVER: 27 files,35318332 bytes; dat2 SHA-256
  `A77ED0C2DFD48B093050913041B098FB4A700D6A1C32DAECABFCD2A6DD81696C`.
- LIVE: 25 files,237756491 bytes; dat2 SHA-256
  `FD0FE5EF66039A791A900EDDDF1CDBCFE57E09E8C32EA3496C69E96217A41A7B`.

The pre-existing shutdown issue still logs `Game database service failed to shut down` with
`Connection reset by peer`. The processes exited and subsequent startups succeeded; this is not
a certification of clean database shutdown/flush. No persistence source was changed for this fix.

Remaining handoff: play a fresh first phase in the client. Automated checks establish the platform
stall and its correction; they do not constitute a newly observed live client fight. Protect from
Melee should still fully block the first-phase stomp while both Titans are alive.
