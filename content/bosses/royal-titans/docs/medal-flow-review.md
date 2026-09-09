# Medal fight-flow review (2026-09-09)

Input: https://medal.tv/games/screen-capture/clips/nulYuwdtIhxH6PC6v . Original video downloaded
without alteration to `logs/royal-titans-medal-review-20260909/medal-original.mp4` (174.697 seconds,
1280x720, 50 fps). Timestamped, cropped contact sheets inspect the entire recording at three-second
intervals; this is visual sampling, not a tick-exact packet diff or an audio verification.
Original media SHA-256: `36839DFC7E6FF81900551DC38581A56E25A0CDC07552AB34CA9E453B67FD0206`.

## Comparison before edits

- 00:09-00:51: phase-one Titans remain around their fixed east/west positions rather than the
  normal north/south patrol. Source still uses one fixed retreat/advance timer for both NPCs.
- 00:15-00:18, 01:21, 01:39: two HP displays disagree. Source opens/predicts the native boss bar
  in the launch-time hit modifier, before actual impact; the client opponent overlay is separate.
- 00:54-01:06: phase-two presentation leaves prominent upright orange pieces. The dump confirms
  the four side loc IDs, coordinates, shape10 and rotation0, so changing their shape is NOT justified.
  The code used `LocAngle.North` (numeric1), not rotation0; `LocAngle.West` is the required numeric0.
  The earlier verbal suggestion that existing orientations were correct was wrong and is withdrawn.
  Missing body customization is a separate confirmed difference (Branda55952, Eldric55940).
- 01:09-02:15: horizontal pack formation and repeated shared rotation. This is not the vertical
  pack formation or independent readiness observed in2283.
- 02:18-02:54: completion message appears, then both corpses repeatedly produce “I can't reach
  that!”. This is a native interaction/reach defect, not proof that reward allocation succeeded.

## Mechanic-to-API mapping for this correction

| Mechanic | Native route | Evidence / boundary |
|---|---|---|
| Independent movement and arrival | Content-owned readiness, `Npc.walk`, `RouteFactory`, `NpcMovementProcessor`, `MapClock` | 2283 Branda arrival1350/face1351/action1352 while Eldric walks until1355 |
| Facing and animation | `faceSquare`, `anim`, cache locomotion | Last step L, face L+1, action L+2; no explicit walk action sequence |
| Phase-one route selection | Named injectable content policy | Observed west/east anchors and Y9561..9571 verified; exact RNG/alignment policy NOT established |
| Stomp geometry | Native bounds/footprint and queued player hits | Branda1303 and Eldric1306, directional5x3; damage+1 |
| HP display | Current target and native boss-bar API after resolved damage | Remove launch-time HP prediction; retain client overlay distinction |
| Corpse choices | Native NPC operation/approach dispatch | Must test actual reachable boundary, not call claim directly |
| Landing appearance | Shared NPC protocol body-model operation | Separately approved by `ye` and implemented in the subsequent [body-model pass](body-model-api-report.md); no synthetic NPC types |
| Floors | Native LocRepository | 2283 L24088-24098/L24170-24180: side loc identities/shape confirmed; change incorrect numeric1 orientation to0 |
| Landing telegraphs | Native WorldRepository map animations | Cast1555/1559: graphics3219/3222 on5x3; height0/delay0 |
| Opposing Blast elemental deaths | Existing native NPC death queue | 2283 fire tile1613 kills all three stacked ice elementals, not the stacked fire elemental |
| Retaliation during scripted patrol | Type-specific native `onNpcQueue` for `com_retaliate_player` | Consume default chase only; retain native incoming hits, attribution and lifecycle validation |
| Elemental melee | Existing `NvPCombat.attack` / `NpcAttackOverride`, as in DagannothKingsCombat.attackRex | Native accuracy/prayers/action delay; max4,rate4,delay1; fireSlash/iceStab; native captured hit scopes remain active |

Authoritative flow sources: normal2283 (not accelerated Grid Master combat), current
[Strategies revision15329439](https://oldschool.runescape.wiki/w/Royal_Titans/Strategies?oldid=15329439),
[official ranged-vulnerability clarification](https://secure.runescape.com/m=news/game-jam-poh-improvements?oldschool=1).
Below100 desynchronization and the selection of two versus three ordinary phase-two actions remain
unpublished; do not call either guessed policy 1:1. Results and remaining differences are recorded
below after verification.

## Corrections implemented in this pass

| Defect | Correction and executable coverage |
|---|---|
| Fixed phase-one anchors/shared timer | `RoyalTitanMovement` stores each Titan's destination, arrival, facing and action readiness. Shared `RouteFactory` plans3x3 paths; `Npc.walk` and native movement execute them. Exact endpoint required; no teleport recovery. `RoyalTitansMovementTest` compares first captured retreats/patrols and tests a blocked destination footprint. |
| Ambient AI overriding scripted walks | Titans use native `NpcMode.None` while content owns movement. Removed oversized approach range and the aggression helper that could overwrite routes. No forced walk action animation. |
| Global ranged vulnerability | Phase-one vulnerability follows each Titan's own retreat/return state. Production fixture checks one boosted while the other is not. Blast grace remains an unverified inherited4ticks. |
| Wrong horizontal elemental formation/occupancy | Vertical three-tile packs, native non-reserving occupancy, full native pathfinder detours. Separate Titan target choice with retention and departure retarget. `RoyalTitansElementalMovementTest` checks vertical births, stacked travel, wall detour and duo retention/departure. This does not prove unpublished OSRS selection or retarget rules. |
| Dead Titan's pending summon still appearing | Each pending summon retains its source Titan and requires that Titan still alive/active. Production test kills Branda between summon and birth; only three ice elementals appear. |
| Wrong loc orientation and missing landing blockers | Side floors, walls and safe loc emit numeric rotation0. Add nine loc26209 blockers per Titan in captured side-piece/blocker ordering; cleanup tracks them. Presentation test inspects actual zone payloads and collision. |
| Missing landing telegraphs | Graphics3219/3222,15tiles,zero height/delay at teleport casts. Presentation test asserts every map-animation tile. Overhead speech correctly remains at cast1555/1559, not landing despite the initial prompt's claim. |
| Predictive/stale HUD | Read only actual current Titan interaction and resolved HP at encounter tick end; update each viewer separately and clear on target loss. Duo fixture checks delayed27damage, switching, clearing, and unchanged partner target. Client overlay remains separate. |
| Corpse native reach/dispatch failures | Transform in place, then load authoritative corpse stats (500 HP) so native validity checks pass. Existing event dispatch now uses explicit transformed type, not immutable spawn ID. Test drives native player interaction processor from captured adjacent tiles into all three operation callbacks for both kill orders. Claim itself is not invoked by this dispatcher test. |
| First charge one tick late | Account for stage tick0 being the tick after landing: charge15ticks after Branda cast,8after Eldric landing. Test reproduces first charge/execute/ordinary readiness. |
| First death restarted survivor rotation | Changing survivor stage preserves action clock. Both kill orders tested with death during charge; survivor executes on its original tick and starts next charge on schedule. |
| Stomp immediately before next charge | Remove legacy29-cycle stomp at+28, which was overwritten by charge at+29. Two/three-action choice remains unresolved; test labels legacy cycle-selection policy explicitly. |
| Blast did not kill opposing stacked elementals | Each emitted live-element pulse queues native death for every opposing elemental on its tile. Same-element/safe/dead-Titan coverage is excluded. Death/pulse packet ordering still needs a live comparison; this new branch has not had a full opposing-stack production scenario test. |

`RoyalTitansTestWorld` now runs native NPC modes/movement/post-tick processing in addition to the
previous hit/death/instance fixture. New patrol-retaliation and elemental-cadence tests additionally
run native `AiPlayerModeProcessor` and `NpcInteractionProcessor`, with the actual native retaliation
script registered. Unrelated AI mode collaborators are mocked. Accuracy formula collaborators in
the fixture return deterministic misses; the production handler calls shared accuracy, not a
boss-local roll. These are not real map-terrain or live damage-distribution tests. Native corpse tests use actual
reach/dispatch but substitute counting listeners for reward handlers. This distinction matters.

## Event comparison results (fixture, not live network)

Normal2283 Branda spawn1269 is fixture tick100; production source executes under an injected clock.
Row-selection RNG is injected to the observed first patrol endpoints rather than pretending the
finite capture proves random weights.

| Observed event | Capture | Production-fixture result |
|---|---|---|
| Branda retreat anchors | 1274/1275:2905,9566 then2904,9566 | Match at relative+5/+6 |
| Eldric retreat anchors | 1276/1277:2915,9566 then2916,9566 | Match at+7/+8 |
| Branda first patrol | 1286-1289:x2904,y9565 down to9562 | All four anchors match |
| Eldric first patrol | 1288-1292:x2916,y9565 down to9561 | All five anchors match |
| Inward facing then wall | B1290/1291;E1293/1294 | Match; Eldric still walking when Branda casts |
| Landing telegraphs |1555/1559,3219/3222,15tiles each | Tile sets,IDs,height0,delay0 match |
| Side floors and blocking |1558/1562,shape10/rotation0,18blockers | Native zone payload/collision assertions pass |
| First charge and execute |1570/1576 | Relative+15/+21 from cast match |
| First three ordinary actions |1584summon,1591stomp,1598stomp | Relative+29/+36/+43 match |
| Second charge |1606 | Relative+51 match |

There is **no new live OpenRune packet capture or full2283 event diff**. The Medal frames are the
user's pre-correction run, not evidence that the new deployment has passed client verification.
The Browser skill's required browser discovery returned no available browser; the comparison used
the clip's public media file and local extracted frames, not an interactive browser session.
No audio parity is claimed.

## Files changed for this Medal correction

- `RoyalTitansEncounterManager.kt`, `RoyalTitansRules.kt`, new `RoyalTitansMovement.kt` in the existing
  module; no second encounter implementation or command registration.
- `RoyalTitansCombat.kt`: scoped native retaliation-queue handler; module test dependency adds
  shared combat formulas for the real `NvPCombat` integration fixture.
- `api/player/.../events/interact/NpcEvents.kt`: repair explicit-transmog operation event keys.
- `.data/raw-cache/server/npcs.toml`: elemental `blockWalk=None`; normal movement restrictions kept.
- Elemental native `maxRange=64` covers the single instanced map square, while `attackRange=1`
  remains unchanged. This is OpenRune chase configuration, not a claim of64-tile OSRS attack reach.
- `or-cache/.../NpcParameterConfigInstaller.kt`: explicit selected occupancy overlay only; backs up
  original selected records and does not copy decoder defaults into unrelated fields.
- Existing `RoyalTitansCombatIntegrationTest.kt`, `RoyalTitansRulesTest.kt`, cache audit;
  new `RoyalTitansMovementTest.kt`, `RoyalTitansCorpseInteractionTest.kt`,
  `RoyalTitansPresentationTest.kt`, `RoyalTitansElementalMovementTest.kt`.
- This report, `capture-validation.md`, `medal-format-files.txt` (scoped formatting list).

## Still unresolved

The body-model API gap was separately approved by `ye` and addressed in the subsequent
[body-model pass](body-model-api-report.md): landing55952/55940 and corpse/removal resets.
That correction does not establish complete floor/body client visual parity.

Explicitly unverified/incorrect rules still include phase-one action/summon selection, exact patrol
and return weighting, fixed close-action budget3, phase-two independent scheduling/below100 rule,
variable29/36cycle choice, elemental life trigger (legacy18 disproved), exact spawn-to-route cadence,
landing knockback, safe-tile distribution, and exact grace. Those are not labeled1:1.
Reward scaling/native transactional delivery, complete CAs and other original encounter release
gates remain as documented in `capture-validation.md` and `approved-integration-report.md`.

## Automated verification

Logs below live under `logs/royal-titans-medal-review-20260909/` in the canonical repository.

- `broad-check.log`: success5m30s,478tasks, full cache suite92tests with no failures/errors;
  includes affected/adjacent tests and `:server:app:assemble` before the final AI/leash corrections.
- `install-leash.log`: explicit selected-NPC cache maintenance succeeded24s. Backups are in
  `logs/npc-parameter-backups`. No revision/path/LIVE change, startup generator, or source restore.
- `final-required-2.log`: success35s,88 Royal tests and2 focused cache tests, including native AI.
- `final-check.log`: success1m30s,480tasks; affected checks, adjacent tests and final server assemble.
  Formatting checks are restricted to the explicit source manifest, not unrelated dirty files.
- `diff-check.log`: `git diff --check` exit0 (line-ending warnings only).

Final check command:

```powershell
.\gradlew.bat -I scripts/format-selected.init.gradle '-PopenruneFormatManifest=content/bosses/royal-titans/docs/medal-format-files.txt' :content:bosses:royal-titans:check :api:player:check :api:game-process:test :api:npc:test :api:registry:test :api:instances:test :api:boss-hp-bar-plugin:test :api:net:test :api:combat:combat-scripts:test :content:skills:magic:spell-attacks:test :or-cache:test --tests dev.openrune.RoyalTitansR240CacheAuditTest :or-cache:check :server:app:assemble --no-daemon --console=plain
```

Exact minimum command was rerun after the final source/test changes (`verified-required.log`):
success38s,88 Royal tests,focused cache tests up-to-date from the passing final check.

```powershell
.\gradlew.bat :content:bosses:royal-titans:test :or-cache:test --tests dev.openrune.RoyalTitansR240CacheAuditTest --no-daemon --console=plain
```

Failure history is retained: early compilation reference corrections, fixture timing assertions,
the production one-tick charge offset, and the real native spawn-leash cancellation exposed by
the adjacent-AI test. These were corrected, not skipped. See `corrected-tests.log`, `route-tests*`,
`native-ai-tests*`, and `ai-trace-test*`. The full cache run's five-minute cost was repeated complete
cache initialization, not a hang:92tests passed and POH interface decoding took5.925seconds.

## Runtime verification

The old server was shut down normally before rebuilding loaded JARs. Final production source was
assembled, started and checked with the project doctor. No runtime JAR was rebuilt while these
new game JVMs ran. Logs for the two controlled restarts live under
`logs/cerberus-audit-20260907/restart-2026090901.*` and `restart-2026090902.*`; commands use the
existing reviewed `scripts/cerberus-controlled-restart.ps1` helper and normal shutdown hooks.

| Stage | Game JVM | Custom build marker | Ready / scripts | Doctor |
|---|---|---|---|---|
| Initial rebuilt start |10108|00:32:52|00:32:55 /432|`start-doctor.log`|
| Controlled restart1 |21296|00:34:09|00:34:11 /432|`restart1-doctor.log`|
| Controlled restart2 |5916|00:35:51|00:35:54 /432|`restart2-doctor.log`|

Times are local2026-09-09. All three confirmed canonical `user.dir`, revision240, `.data/postgres` and
unchanged cache paths. SERVER dat2 after the explicit selected changes:
`A77ED0C2DFD48B093050913041B098FB4A700D6A1C32DAECABFCD2A6DD81696C`.
LIVE dat2 unchanged:
`FD0FE5EF66039A791A900EDDDF1CDBCFE57E09E8C32EA3496C69E96217A41A7B`.

The existing database shutdown error reproduced: `Game database service failed to shut down`
with `SocketException: Connection reset by peer`. The next startup succeeds, but this is not a
clean data-flush certification. Initial startup also logged an over-budget cycle. These issues
are not hidden as clean-runtime evidence and were not patched by an unrelated database rewrite.

No fresh solo/duo client fight, client sound/visual comparison, live reward claim or outgoing
network encounter capture was completed in this pass. An attempted capture of the existing native
game window returned invalid-window-properties/I/O error; no claim is made to have controlled it.
The comparison table above is from deterministic production-code fixtures, not a live packet diff.

Final server is left running as PID5916 after both controlled restarts. No staging/commit, source
restoration/deletion, nexus edit, or modification of unrelated AGENTS/PlayerDeath/Vorkath work was
performed. `git diff --check` passes; existing unrelated dirty work remains in place.

### Additional pre-deployment review corrections

The read-only agent review found gaps not covered by the initial passing84tests:

- A wall forming AFTER an elemental had a route left a blocked waypoint queued forever. Replan
  through the native pathfinder when a movable NPC makes no progress; also recover a Titan's
  interrupted route without treating a failed callback as arrival. Added dynamic-wall regression.
- Default native retaliation could replace a Titan patrol with a chase route. A type-specific
  native queue registration consumes only this default chase action. A real-retaliation test
  checks the golden route still completes and incoming damage/contribution still resolve.
- Initial elemental unlock could issue a summon on top of another action. Unlock now marks each
  Titan's pending summon; its own next ready action consumes it instead of running two mechanics.
  Crossing immediately before/on a wall-action tick is tested. Exact OSRS summon-priority and
  subsequent resummon selection remain unverified, not inferred from this safety correction.
- Adjacent elemental custom callbacks had no action-delay guard and could attack every tick.
  Replaced custom damage handling with existing `NvPCombat`, which supplies native gating,
  accuracy, queued hits and prayers. The native approach fixture checks four-tick attack cadence.
- That fixture exposed a further existing native spawn-leash issue: both elemental cache entries
  had `maxRange=7`, `attackRange=1`, `giveChase=true`. Birth(32,27) to player(31,37) is10tiles, so
  `NpcInteractionProcessor.isWithinMaxOpRange` cancelled the attack even when currently adjacent.
  Corrected the native source-defined pursuit range for the single-map-square instance; did not
  use the fork-only `maxOpRangeOverride` field or change attack reach. The selected cache installer
  applies this field only when explicitly present in the named TOML block.
