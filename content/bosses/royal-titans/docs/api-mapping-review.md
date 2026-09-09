# Royal Titans API mapping and approved integration status

Status: corrective content work resumed with the user's instruction to continue under the native-API
rules on 2026-09-08. This mapping is not evidence of completed parity. All audit agents remain
read-only. The user subsequently approved the bounded shared changes in
[shared-api-approval.md](shared-api-approval.md) on 2026-09-08. Historical review-baseline statements
below are not current approval blockers.

## Approved shared integration in progress

Typed `HitContext` attribution and composable generation tokens now flow through the native NPC
and player hit queues/processors, damage ledgers, retaliation, recoil, burn, poison/venom, thralls
and Twinflame echo. Permanent retirement is checked before access/impact side effects; the older
impact-time validity predicate is deliberately not used to prune work early. Royal Titans creates
one run scope and one bounded/disconnect-aware membership scope per participant.

The existing instance manager now indexes owned NPC object identities, returns list snapshots,
rejects unregistered/cross-instance attachments and detaches on the native deletion event. HUD
deletion closes the displayed object regardless of transform or listener order. Shared freeze
calculation now returns immunity for 100% resistance. Missing Titan parameters were corrected in
the tracked `.data/raw-cache/server/npcs.toml` source and installed into SERVER using the explicit
params-only installer. The real freeze integration and revision-240 cache audits pass.

Royal Titans uses the already existing death-pile location resolver, with original-instance/plane
validation. Cleanup preserves the native respawn coordinate. This is ordinary unsafe-death pile
delivery, not a newly implemented gravestone service. No unrelated PlayerDeath or Vorkath source
was modified for this integration.

The protected native name-dialog wrapper now sends script109 and resumes only native name input,
with the existing modal/delay guard. LIVE109/682/681 retain the last name in client variable361.
Shared stat observations now publish immutable previous/current levels synchronously at the
PlayerStatMap setter, bound/unbound by PlayerRegistry. Tests cover same-tick loss/restoration,
protected access, reentrant restoration, unsigned levels, offline loading and registration cleanup.
This is observation infrastructure, not a completed CA award service.

Shared CA awards and durable claim/delivery/recovery remain unfinished.
Approval alone is not implementation evidence, and these new project APIs are not represented
as inherited official upstream APIs. Final affected checks/build and72 Royal Titans tests pass.
Rebuilt source started and restarted twice, ending atPID16396, but database-close errors during
shutdown remain a runtime gate. Earlier167-test/two-restart results apply to the previous content phase.
Current results and the exact remaining gates are in [approved-integration-report.md](approved-integration-report.md).

## Baseline and classifications

The upstream comparison uses the locally available `upstream/main-audit-latest` reference at
`1c60a15d54a06b960d8c51da960988e48a4c5de0` (2026-08-28), with movement and selected hooks also checked
against local `upstream/main`. No claim is made that these references equal today's remote HEAD.
An API's presence in this worktree does not establish that it is official upstream.

The original implementation and unrelated changes remain preserved. The user-owned `AGENTS.md`
change is not part of the implementation edits.

## Approved corrective phase

1. Remove only this session's new `Npc.spellAccuracyOverride` / `spellMaximumOverride` callbacks
   and their consumers. These are direct engine overrides prohibited by the new rule.
2. Hold the new damage-provenance API and emitter changes for explicit shared-API review.
   Rework dependent content consistently if that API is not approved; never restore whole files
   from Git or erase unrelated changes.
3. Remove unsupported production choices introduced during this session, including invented
   dialogue and the contradicted 65-tick summon cooldown. An uncertainty label does not validate
   a constant, distribution, or implementation.
4. Repair the current pathfinder nullability compilation failure using the established collision
   strategy contract. Then implement and test the approved upstream-supported content mappings.
5. For each unresolved API gap below, present its concrete public design and tests before changing
   shared code. Do not substitute private callbacks or claim existing fork APIs are upstream.

The draft engine overrides and damage-kind additions have now been withdrawn hunk by hunk. The
speculative scheduler (including its nullable pathfinder call) was withdrawn with its unsupported
RNG/cooldown policy; it was not converted into a supposedly accurate production policy. Legacy
movement/rotation remains explicitly incomplete. No deployment has been performed.

### Native routes established by the follow-up audit

The following refine the approved existing-API mapping; they add no engine API:

- `onNpcHit(type)` receives final HP-capped damage after HP subtraction and before death-queue
  execution. Use it for elemental lethal-hit refunds rather than `HitImpactHandler`.
- Use the Titans' native `damageContributions` records for resolved boss totals. Do not use the
  generic instance total: `InstanceNpcDamageContributor` also includes damage to helpers. Source-kind
  distinctions and the August full-loot saturation remain unresolved.
- `onNpcQueue(type, "queue.death")` owns custom NPC death presentation. Upstream Ducks demonstrates
  this extension point; it does not require a generic deletion before the custom animation.
  Corpse types also override this queue to prevent late projectiles invoking generic death after
  transformation. Native `queueList.clear`, `noneMode`, and a zero base HP target make the defeated
  NPC inactive without an engine callback or a regeneration-suppression flag.
- The existing `ElementalSpells.ElementalSpellAttack.cast` is the single manual/autocast content
  path. Apply the target-specific probability before its splash branch using public
  `AccuracyFormulae.isSuccessfulHit`; retain `SpellAttackManager.rollMaxHit`, the scalable
  `getMaxHit(player.magicLvl)`, casting, projectile, XP and queued-hit pipeline. No second registration
  or `Npc.spellAccuracyOverride` is required.
- Native target-specific `onApNpcT`/`onOpNpcT` handlers are candidates for wall spells. Their exact
  cast-resource/delay behavior needs separate tests before replacing the legacy hit modifier.

The local Ancient/Twinflame implementations are not thereby classified as upstream. Existing
generation-validity, instance-detachment and death-location hooks are also still fork-only. New
uses introduced by the draft were withdrawn; their missing upstream capabilities remain gaps.

### Continued native implementation

- Wall manual/autocast operations now delegate the single registered Standard/Ancient handlers.
  `MagicRuneManager.attemptCast` consumes resources without cast XP; native projectile durations
  feed a zero-damage `queue.hit`, consumed by the wall script at impact. No action-delay mutation,
  damage roll, freeze or secondary wall echo. Tests include full RuneManager and native op events.
- `GameDatabase.withTransactionBlocking` plus content migrationV42 now owns immutable completion,
  participant and death records. This is an existing public API, not an approval gap. Dedicated
  tables avoid account-attribute autosave overwrite. One SQL snapshot supplies scoreboard values.
  Durable claim allocation and ground-item pickup/recovery remain separate gaps.
- Shared instance timing has only one start, overwritten at spawn and completed on the first death.
  It cannot reproduce the last-defeated Titan's spawn+1 rule seen in40 captures. Content MapClock
  timestamps therefore do not duplicate an equivalent native timer.
- Native named interface APIs open909. Native varp4648 is a post-commit/login projection only.
- Enter/Join/Quick-start use the cache operations; native mesbox/choices persist warning16089.
  An initial suspected protected-access conflict was not reproduced; the unnecessary bypass was
  withdrawn, and the ordinary native access guard remains. Pending Eldric spawn is treated as600HP.
  Name input/prefill and entry visual sequence remain incomplete.
- Existing shared hard/elite clue predicates now read claimed state2, not constanttrue. LIVE
  scripts4782/4790 establish state1 as pending/unclaimed. This changes content behavior through
  existing vars, not an engine API.

Concrete outstanding shared changes are scoped in [shared-api-approval.md](shared-api-approval.md).
The older mapping cells below describe the review baseline where superseded by this section.
The final continuation now passes167 checked tests and two real final-source startup/restart
checks; migrationV42 was applied by normal startup. See [continued-implementation-report.md](continued-implementation-report.md)
for exact scope and evidence. This supersedes earlier statements that no deployment had occurred;
it does not establish full combat, reward or visual parity.

## Mechanic-to-API mapping

| Mechanics | Proposed established route | Gap or constraint |
|---|---|---|
| NPC stats, size, bonuses, immunities, weakness, Slayer categories and XP | Cache definitions and intentional server-config generator; shared combat and Slayer parameter consumers | Active definitions need additional bonus/immunity verification. Branda's configured attack sequence is incorrectly 11981 rather than stomp 11974. |
| Initial spawn, two-tick separation, facing, animation, graphic slots | NPC repository, scripted queues/MapClock, `Npc.anim`, `spotanim`, `faceSquare` | Capture 2283 ticks 1269/1271, lines 16800/16830. No new engine API needed. |
| Patrol, return routes, footprints, movement arrival, staggering | `RouteFinding.findRoute(srcSize=3)` and upstream `Npc.walk(waypoints,onArrival)`; native collision/step validation | Check actual destination after callback: route abortion can also empty the queue. Patrol RNG and melee budget are unproved. |
| Elemental formation, stacking, follow/retarget, collision and attack cadence | NPC repository, full routefinder, native movement and queued melee hits | Vertical packs are observed; candidate distribution and displaced duo spawn rules remain unproved. |
| Directional stomp and prayer behavior | Content footprint/tile selection into shared queued-hit and prayer pipeline | Capture animation/impact 1303/1304 and 1306/1307; previous-tick position must be reconciled with engine order. |
| Wall telegraph, formation, collision, expiry and three-piece removal | Loc/NPC repositories, native collision, world map graphics/sounds, scripted timing | Telegraph +4 formation; observed natural expiry +39 from formation. Spell removal order is selected, left, right. |
| Valid wall spells, elemental special accuracy and spell maximum | Native spell handlers, `SpellAttackManager`, public `AccuracyFormulae.isSuccessfulHit` before splash, native max-hit/weakness calculation | Standard opposing-element probability now uses this content route. Full wall cast/resource/delay handling is still unresolved; new NPC callback overrides are prohibited. |
| Elemental splash, Twinflame echo, lethal races and delay reductions | Native spell damage and queued hits; `onNpcHit` final-impact event | Refund now uses final lethal damage and actual remaining delay once per NPC, with no impact callback. Full splash timing and inherited fork echo integration are not verified. |
| Freeze immunity | Cache freeze resistance and shared `CombatEffects.bind` | Existing calculation clamps 100% resistance to one tick. Proposed shared bugfix requires disclosure and approval. |
| Per-Titan ranged accuracy | Native ranged accuracy calculation with independently tracked Titan readiness | Existing `incomingRangedAccuracyMultiplier` is a fork addition, not upstream. Needs an approved integration route. |
| Transition hit, teleport/landing, knockback, floors | Shared normal damage resolution; scripted teleport, collision-aware tile selection, native animation/facing/loc APIs | Crossing hit is uncapped. Landing knockback destinations remain unproved. Dialogue occurs at teleport start. |
| Blast charge, safe tile, timer/headbar, pulses and survivor coverage | Per-Titan content schedules, native headbars, map graphics and queued damage | Charge +6 execution, pulses +7/+9/+11; timer removed first pulse, safe loc +13. Safe distribution, action budgets and below-100 behavior remain unresolved. Current shared Blast execution still needs rework. |
| Projectiles, sound, dialogue and opponent HUD | Native projectile/sound/NPC presentation and existing HUD pipeline | No Titan projectile is present in audited normal capture. Blast sound packets are synth sounds, not area sounds. Only the player's selected target should drive HUD. |
| Defeat, inactive/death sequence, corpse transforms and option gating | Upstream `Npc.transmog`, scripted death queues and native operations | Same NPC index must survive transform. Global hide/show does not implement per-player claim gating. |
| Claim-triggered collapse/reset and repeated encounters | Existing module's run state, native queues/repositories and instance ownership | Unclaimed 30-tick expiry is contradicted by duo capture. Reset generation and all delayed work require complete cancellation mapping. |
| Entrance, start/join/quick-start, warning and remembered name | `ProtectedAccess` dialogue, `InstanceManager`/`InstanceSession`, native persistent vars/attributes | Warning varbit 16089. Threshold 450 is established; exact equality remains unproved. No access requirements or new travel system. |
| Scoreboard and exact messages | Native interface/text APIs and committed statistics | Interface 909, components 2/6/9/11/13/15/17/19; KC varp 4648. Data must be committed before display. |
| Direct/recoil/toxin/burn/thrall contribution | Upstream processed-hit pipeline and `DamageContributions` | Existing source/amount totals do not encode kind. The new `DamageKind` fields and emitter changes are a shared API proposal, not inherited upstream functionality. August saturation is unpublished. |
| Standard rolls, uniques, pages, sacrifice and tertiaries | Existing authoritative drop tables and native drop/clue/pet components | Two standard rolls; unique replacement must preserve tertiaries. Unique topology/order remains unknown. Learned-scroll vars exist. Current clue-tier predicates are stubs. |
| Personal loot, Collection Log, broadcast, tracker and provenance | Shared `NpcDropTableKillHook` and `CollectionLogService` | Upstream resolved-drop delivery helper is private. A public reusable award path requires API review. Existing catalog already contains all seven entries. |
| KC, deaths, PB/global statistics, completion and claim identity | Native PostgreSQL/account persistence and instance timing concepts | No generic transactional completion/claim/outbox service established. Current blocking/exclusive-save helpers are fork additions. Claimed flag plus transient ground spawn is not crash-safe delivery. |
| Exit, teleport, logout, death, disconnect and empty-instance cleanup | Native death cleanup and instance lifecycle, repositories and queued-hit pipeline | `HitValidity`, `detachNpc`, and `PlayerDeathDropLocationHook` are established local fork APIs. Native graves were not found; outside death piles are not grave parity. |
| Rebuild, restarts, runtime/client scenarios and event diff | Existing Gradle/runtime test fixtures, isolated PostgreSQL tests, doctor/startup marker and native protocol | Corrective source compiles; encounter safety/parity remains incomplete, so no revised deployment is claimed. No complete in-repository client automation or outgoing packet recorder found; recorder changes need their own mapping. |

## All Combat Achievements

Native cache storage exists: tasks 588–598 occupy varp 4496 bits 12–22; Royal Titans task total
uses varbit 16088. Storage identity does not prove a complete shared award service.

| Task | Required observed event / established route | Gap |
|---|---|---|
| One by one | Attack-launch order for both Titans, including misses | Existing launch hook is fork-only. |
| Let them fight | Ten elemental deaths attributed to Titans during completion | Requires source-kind death attribution. |
| Elemental Company | No player attack launch against elementals | Damage-only tracking misses unsuccessful attacks. |
| Champion / Adept | Committed encounter KC 10 / 25 | Must use one completion, not two NPC deaths. |
| It takes too long | Both defeat events share an active charge identity | Needs independently tracked charge identity and timing. |
| Titan Killer | Instance join/leave and 15-completion streak | Use shared instance lifecycle; late join resets and leave invalidates. |
| I need room | Explicit cause on every wall removal | Distinguish player, opposing wall and natural expiry. |
| Speed-Runner | Committed duration below 150 ticks | Exact start event still needs runtime/capture reconciliation. |
| Perfect | Source-tagged processed avoidable damage | Current Wiki also distinguishes elemental melee and solo/duo enraged damage; semantics must be reconciled. |
| No time to pray | Every actual prayer-point loss | Tick sampling can miss loss followed by restoration within one tick; no sufficient upstream event API established. |

No complete upstream CA award/event service was established. The existing Cerberus helper is
private local content, not an approved shared replacement. Clue-tier status identities are known,
but the inspected client scripts prove display state rather than independently proving claimed
reward eligibility.

## Evidence constraints

Normal capture 2283 remains authoritative for visible timing. Its raw SHA-256 matches the capture
README (`100EDE960B4ADBCE90E171B53C741AFB6055E01ED30D96848594EF70D682498B`). The audits inspected decoded
streams and decoder source; binary decoding was not rerun.

- Arrival: final walk, next-tick face, following-tick action throughout audited phase-one routes.
- Transition: 27 damage at tick 1553 takes Branda below 210; teleport/dialogue 1555, landing 1558;
  Eldric teleport/dialogue 1559, landing 1562.
- Blast: charge starts 1570,1606,1635,1664,1700,1729,1765,1801; ordinary actions are seven ticks
  apart, next charge eight ticks after the last ordinary action. Two/three actions are observed;
  uniform random choice is not established.
- Summons at 1714 and 1757 are 43 ticks apart (lines 28354,29505), contradicting the new 65-tick
  cooldown. Elemental self-destruct ages vary even within a single pack.
- Corpse transforms at 1651 and 1806 preserve indices and use inactive sequences 11990/11985.
- Successful loot 1830, collapse 1851, deletion 1856, respawns 1859/1861. Duo 3402 completion1835
  remains claimable at1924, contradicting unconditional unclaimed 30-tick expiry.
- Current official updates publish neither numeric full-loot saturation nor unique roll topology.

Relevant current sources:

- https://oldschool.runescape.wiki/w/Royal_Titans
- https://oldschool.runescape.wiki/w/Royal_Titans/Strategies
- https://secure.runescape.com/m=news/royal-titans-cas--birthday-rewards?oldschool=1
- https://secure.runescape.com/m=news/summer-sweep-up-slayer--more?oldschool=1

## Historical verification state at the API-review pause

- Requested baseline test command: passed (27 seconds), before implementation changes.
- First implementation compilation: passed (1 minute 11 seconds).
- Latest compilation: failed at `RoyalTitansEncounterManager.kt:327`, nullable
  `CollisionStrategy?` supplied where `CollisionStrategy` is required.
- Existing tests still need conversion and updates for changed rule signatures.
- `git diff --check`: passed after the rule update.
- Live JVM PID32320 remains the original process; no deployment or controlled restart performed.
- No client scenario or OpenRune-versus-capture event diff completed. No 1:1 claim is warranted.

The compilation failure and test-signature issues above were subsequently resolved in the approved
corrective phase. Current results and remaining release gates are recorded in
[native-correction-report.md](native-correction-report.md); the historical failed run is not the
current source state.
