# Royal Titans approved shared integration — work-in-progress report

Date: 2026-09-08. The user's `approve` authorizes the bounded design in
[shared-api-approval.md](shared-api-approval.md). Approval is not a remaining blocker. This report
does **not** certify the complete encounter, all four approved workstreams, or 1:1 parity.

## Implemented in this approved phase

1. Shared `HitContext` contains typed origin/owner/mechanic attribution and composable generation
   tokens. Native NPC/player queue creation captures source and target lifecycles. Processors
   reject retired work before damage, hitmarks, headbars, retaliation, recoil, death and events.
   Early queue pruning uses permanent retirement only; legacy impact predicates remain impact-time
   predicates. A denied logout click does not invalidate combat or grant immunity.
2. Coordinate mutation, accepted logout/disconnect and lethal player damage retire membership.
   Run reset and external instance termination invalidate outstanding work. Burn, poison, venom,
   thralls, recoil and Twinflame echoes retain the original context rather than capturing a new
   valid generation after departure. Thrall replacement/dismissal retires its own producer scope.
3. Native damage ledgers retain aggregate totals and add source-origin queries, using resolved
   HP-capped damage and explicit owner UUIDs, including offline ownership. Elemental damage remains
   excluded from Royal Titans boss contribution. This does not settle August loot saturation.
4. Rejected toxin applications cannot mutate existing status: invalid venom does not cure poison;
   invalid burn creates no empty target entry. Scoped player toxin persistence markers prevent
   reconnect from rearming a retired effect as ordinary unscoped poison. Ordinary poison remains
   persistent. Consumed burn stacks cannot erase already emitted valid hits.
5. Instance ownership uses NPC object identity, validates registration before attachment, prevents
   cross-instance attachment, returns snapshots, and detaches on native deletion. HUD deletion
   closes the displayed object even after transformation and in either listener order.
6. Royal Titans reuses `PlayerDeathDropLocationHook` and native unsafe-drop delivery. The original
   instance/plane resolves a pile at `(2951,9573)` outside the entrance; subsequent instance
   destruction cannot erase it. Cleanup no longer replaces the native respawn coordinate. This
   is a death pile, **not a complete gravestone service**. Existing PlayerDeath/Vorkath work is intact.
7. Shared freeze calculation returns immunity at 100% instead of a one-tick bind. The tracked NPC
   parameter source now supplies the verified Titan stats, bonuses, immunities, attack styles,
   weakness and XP parameters. A selected-record installer updates only four SERVER NPC parameter
   dictionaries, preserving their non-parameter definitions and all LIVE JS5 groups.
8. `ProtectedAccess.nameDialog` wraps native script109/name input, retaining modal/delay protection.
   The entrance resolves the returned same-world player UID again after its warning. Native client
   scripts preserve the remembered-name link. Friend-list enforcement and full entry UI parity
   are not complete.
9. Shared `PlayerStatChangedEvent` snapshots current/base kind, previous/current unsigned level,
   tick and membership token synchronously from `PlayerStatMap`. `PlayerRegistry` owns binding and
   unbinding. It preserves existing update/engine queues and observes drain/restoration even under
   protected access or reentrant restoration. This supplies the approved observation foundation;
   it does not implement the CA award service or the eleven encounter predicates.
10. Packet-backed output corrections: Branda summon3213 and elemental spawn3202/3204 use slot2;
    death3203/3205 remains slot0. Blast execution sends synth10155 once per pulse per participant,
    loops1 and client-cycle delays28/88/148, instead of area sounds or duplicated Titan sounds.

The actual recoil producer exercised is the existing Dharok/amulet armour-recoil script. The
separate ring-of-recoil/suffering TODO is not implemented by tagging this producer.

## Files changed

[format-files.txt](format-files.txt) lists the exact Kotlin files in the combined corrective
Royal Titans worktree, including the earlier content-only phase. It is also the explicit formatter
manifest, excluding unrelated PlayerDeath, Vorkath and AGENTS changes. No source was restored from
Git and no second Royal Titans module/command was created.

Additional non-Kotlin changes:

- `.data/raw-cache/server/npcs.toml` — authoritative four-NPC parameters.
- `or-cache/build.gradle.kts` — explicit `installRoyalTitansConfig` task.
- `api/combat/combat-commons/build.gradle.kts` — tests/dependencies and serial cache-backed tests.
- `api/registry/build.gradle.kts` — InternalApi annotation dependency for registry-owned binding.
- `content/bosses/royal-titans/build.gradle.kts` — native runtime-test dependencies.
- `content/skills/magic/spell-attacks/build.gradle.kts` — actual thrall/queued-hit tests.
- `content/drops/build.gradle.kts` — earlier clue predicate tests.
- `content/bosses/royal-titans/src/main/resources/db/migration/V42__royal_titans_statistics.sql`
  — earlier content-phase statistics migration, not a durable reward-claim migration.
- `scripts/format-selected.init.gradle` — explicitly invoked formatting helper; never a startup
  dependency and never a source restore/generator. Requires a validated in-root source manifest.
- This report, `shared-api-approval.md`, `api-mapping-review.md`, `capture-validation.md`,
  `shared-ca-cache-audit.md` and `format-files.txt` document scope, evidence and remaining work.

## Evidence for new constants and output

The [capture ledger](capture-validation.md) retains coordinates, wall/stomp/transition/reset timing,
NPC/loc/sequence/graphic identities, stopwatch rule, interface909 components and exact messages
from the earlier content phase. Packet evidence is distinct from production verification.

| Value/change | Evidence | Executable coverage |
|---|---|---|
| NPC12596/14147 parameters,600HP,size3,350combat,levels300/250/100/100/150,bonuses150/100/700,freeze100,XP1225/735,weakness50 | Current Wiki revisions Branda15200593/Eldric15271709, active SERVER definitions and tracked TOML overlay | RoyalTitansR240CacheAuditTest; native freeze regression |
| Fire14150 levels35/35/20/20/20 slash; ice14151 all30 stab;40HP,rate4,weakness100 | Fire elemental Wiki15200594/Ice15200595 and active SERVER | RoyalTitansR240CacheAuditTest |
| Spawn sound ice10172/fire10190,loops1/radius5 | 2283 T1406/L19957;T1416/L20324;T1716/L28403 | IDs retained; sound-origin/formation parity still open |
| Summon3213 slot2/height0/delay0,sequence11978 | 2283 T1414/L20224-20227;repeats1493/L22356,1584/L24679,1649/L26785 | CombatIntegrationTest summon/spawn/death test |
| Ice spawn11954/3202 slot2;fire11956/3204 slot2;zero height/delay | 2283 T1406/L19948-19956;T1416/L20306-20314 | Same executable test |
| Ice death11951/3203 slot0;fire11957/3205 slot0;zero height/delay | 2283 T1416/L20291-20305;T1431/L20847-20861 | Same executable test |
| Blast synth10155 loops1 delays28/88/148 | 2283 execute1576/L24471-24473;repeated1612/1641/1670/1706/1735/1771 | CombatIntegrationTest checks actual SynthSound output for two viewers |
| Native name input109,remembered name361 | 3402 T1165;LIVE109/682/681;681 instructions117-121 save stripped input then RESUME_NAMEDIALOG | NameDialogTest correct response type and modal/delay protection |
| Outside death pile2951,9573 | Existing authoritative Royal entrance coordinate; native PlayerDeathDropLocationHook and map/clone boundary | DeathDropLocationTest native item/bones delivery and cleanup |

Omitted capture defaults were checked in recovered RSProx transcribers: NPC-info727-730 omits
slot/delay/height only at0; server synth2903-2904 omits loops only at1. Synth delays are 20ms client
cycles, not server ticks. No binary decoder rerun or visual client parity is asserted.

NPC source citations: [Branda](https://oldschool.runescape.wiki/w/Branda_the_Fire_Queen?oldid=15200593),
[Eldric](https://oldschool.runescape.wiki/w/Eldric_the_Ice_King?oldid=15271709),
[Fire elemental](https://oldschool.runescape.wiki/w/Fire_elemental_(Royal_Titans)?oldid=15200594),
[Ice elemental](https://oldschool.runescape.wiki/w/Ice_elemental_(Royal_Titans)?oldid=15200595).

### Cache deployment

Revision remains240. Paths remain `.data/cache/SERVER` and `.data/cache/LIVE`.
The explicit `:or-cache:installRoyalTitansConfig` task succeeded in29s. It backed up the four NPC
records under `logs/npc-parameter-backups/`; a read-only decoder comparison found every non-param
field unchanged. The refactored Cerberus installer still delegates its original three records.

- SERVER dat2 before: `899D421EE88ADE6CE7956EC2A13A09ADCC4882264D27B1CF2BC1CED42AC821E3`
- SERVER dat2 after: `21BEADD96D112CB4704B24822A71ED6CB48CC62EDD01F039C865B75A3E512788`
- LIVE dat2 unchanged: `FD0FE5EF66039A791A900EDDDF1CDBCFE57E09E8C32EA3496C69E96217A41A7B`

## Original defect checklist

| Original defects | Status after this phase |
|---|---|
| 1-3 patrol/shared timer/per-Titan ranged | Open: legacy behavior retained and explicitly contradicted; no golden production route test |
| 4-6 formation/pathing/safe RNG | Open: horizontal packs/stacking/lifetime wrong; random safe selection exists but candidate distribution unproved |
| 7 wall spell removes five | Corrected to selected/left/right through native cast and hit queues; tested |
| 8 non-player damage blocked | Boss damage remains legal; native burn/thrall/recoil tested |
| 9 provenance | Shared source-aware ledgers implemented; August contribution policy remains open |
| 10 fixed elemental max hits | Earlier native scalable spell pipeline correction retained; broad equipment/visual parity incomplete |
| 11 death cancels entire Blast | Earlier survivor filtering retained; exact independent scheduler remains open |
| 12 custom elemental death | Native custom death precedes delayed deletion; tested, including queued lethal races |
| 13 capped transition hit | Uncapped221→194 test passes; crossing-to-teleport delay still differs |
| 14 initial spawn | Native two-tick separation/coordinates/facing/animations tested; full initial retreat incomplete |
| 15-17 corpse transforms/options/visuals | Transform identity/inactive/death/reset visuals implemented and tested; per-player UI and delayed claim interaction incomplete |
| 18-20 sounds/projectile | Spawn IDs and wall delays corrected; Blast synth fixed; unsupported inter-Titan projectile removed; live visual parity open |
| 21 entrance | Native ops/warning/name-input integration exists; complete dialogue/friend restriction/boundary equality not proved |
| 22 scoreboard | Interface909/SQL snapshot implemented in earlier phase; actual client scenario open |
| 23-29 rewards | Open: native delivery, scaling, two rolls/unique replacement/pages/sacrifice/suppression/durable claims |
| 30 CA clue rates | Shared claimed-tier predicates corrected; table roll topology/complete rate integration open |
| 31 statistics | PostgreSQL KC/deaths/PB/global data and varp4648 implemented/tested; live completed-fight proof open |
| 32 idempotence | Completion payloads/idempotence tested; crash-safe completion outbox and reward delivery remain open |
| 33 death destination | Native unsafe pile outside instance implemented/tested; full gravestone service absent |
| 34 NPC ownership leak | Shared identity index and deletion cleanup implemented/tested, including reused slots/HUD order |
| 35 run cancellation/state | Shared generation retirement implemented/tested; legacy scheduler/target/pack behavior still requires rework |
| 36 all11 CAs | Not awarded: source provenance and precise stat observation foundations exist; award service/predicates unfinished |
| 37 executable tests | Replaced core source assertions with native runtime fixtures; required full scenario coverage still incomplete |
| 38 evidence ledger | Contradicted claims withdrawn and current limitations marked; no blanket Confirmed/1:1 label |

## Tests and checks

Logs are in `logs/royal-titans-*20260908.log`. Commands execute from the canonical root.

- Required tests plus spell-attacks: `royal-titans-approved-required5` passed38s (65 Royal tests,
  cache audit,66 spell-attack tests including the real thrall producer).
- Broad affected-module tests plus repo/death/death-plugin/hit-plugin/net/drop-table/collection-log
  and `:server:app:assemble`: `royal-titans-approved-broad` passed6m33s,491 tasks; full cache suite92.
  Modules with no test sources report NO-SOURCE, not additional passing tests.
- Added exact summon/death slot and two-client Blast-output tests plus four stat observation tests:
  `royal-titans-observation-output-tests2` passed56s (71 Royal tests, registry and engine tests).
- Final affected checks/rebuild: `royal-titans-approved-final-check` passed1m55s,528 tasks.
  This ran affected `:check` tasks, adjacent tests and `:server:app:assemble`; formatting checks were
  explicitly limited to `format-files.txt` to preserve unrelated source. The added actual-world-clock
  test passes, bringing Royal Titans to72 tests and stat-observation tests to5.
- Exact required command after final changes: `royal-titans-approved-verified-required` passed14s,
  196 tasks up-to-date, using the successful final-source test results:

```powershell
.\gradlew.bat :content:bosses:royal-titans:test :or-cache:test --tests dev.openrune.RoyalTitansR240CacheAuditTest --no-daemon --console=plain
```

The final XML inventory contains470 tests,zero failures/errors/skips across21 modules, including
the filtered2-test cache audit. The earlier full cache run contains92 tests; counting those90
additional distinct cases gives560 tested cases across this phase, not560 newly added tests.
Final Royal count72; spell-attacks66; engine28; combat-commons27; instances20; drops46; net83.
`git diff --check` passes. No staging, commit, reset, source restoration, source deletion, nexus
edit or unrelated PlayerDeath/Vorkath/AGENTS edit was performed.

Failures were retained and fixed, not hidden: absent freeze params exposed by a real bind test;
invalid test use of the custom NPC queue iterator; a name-message assertion type; recoil fixture
attacking during INTRO; missing registry annotation dependency. Initial formatting invocation
incorrectly reached included build-logic and had an unquoted PowerShell property; it made no source
changes. The selected-source helper now excludes included builds and uses a quoted manifest.

## Runtime and packet comparison

Earlier content-phase restarts (PID10160/35084) are not validation of this new shared source.
The old process35084 exited with `ClassNotFoundException: org.rsmod.game.entity.PlayerAvatar`
during dependency rebuilding. No replacement process was started until builds completed.
Final-source startup and two controlled restarts completed:

| Stage | JVM PID | Custom build marker / scripts loaded | Doctor log |
|---|---|---|---|
| Initial rebuilt start | 42508 | 23:09:53 /23:09:56,432 scripts | `royal-titans-approved-start-doctor-20260908.log` |
| Controlled restart2026090804 | 33892 | 23:11:09 /23:11:11,432 scripts | `royal-titans-approved-restart1-doctor-20260908.log` |
| Controlled restart2026090805 | 16396 | 23:13:02 /23:13:04,432 scripts | `royal-titans-approved-restart2-doctor-20260908.log` |

Times are local2026-09-08. Every doctor read the canonical `user.dir`,revision240 and the cache
fingerprints above. PID16396 listens on43594 and8080. The restart helper validated the target JVM
and used normal shutdown hooks; no process was force-killed by the task. No runtime JAR was rebuilt
while these final-source JVMs were running.

Important remaining runtime issue: shutdowns logged PostgreSQL connection resets and
`Game database service failed to shut down` while services closed. The same issue was present in
earlier shutdown output. Restart/startup succeeded, but **clean shutdown/data-flush verification is
not satisfied**. Startup also logged an initial over-budget game cycle. These are not hidden as
unqualified clean-runtime results; no unrelated bootstrap/embedded-PostgreSQL rewrite was made.

A read-only transaction after restart2 validated the authoritative `.data/postgres` path and found
migration42 applied once,zero completions,zero participants,zero deaths. Log:
`royal-titans-approved-restart2-sql-20260908.log`. This is schema retention, not a live kill/death test.

No solo/duo **client fight**, real grave UI, live reward claim, live scoreboard interaction or full
OpenRune-versus-2283 event diff has been completed. The new SynthSound assertions inspect real
production output calls in a deterministic fixture, not an outgoing network capture.

## Remaining approved work and release gates

The CA award/persistence service and durable native reward allocation/delivery/recovery are still
approved but unfinished; they do not need another conversational approval. Likewise, known
movement, elemental, action-budget and reward-rule gaps must still be resolved. Full gravestone
support was explicitly outside the approved bounded death-pile integration.

Do not mark this encounter complete or commit it as verified1:1 until the open rows, complete
automated coverage, controlled runtime checks and live packet/client comparisons are satisfied.
