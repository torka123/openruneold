# Royal Titans native-API corrective phase — 2026-09-08

This file is the earlier corrective-phase snapshot. Continued implementation and current verification
are reported in [continued-implementation-report.md](continued-implementation-report.md), which supersedes
its wall, entrance, reset, statistics and scoreboard dispositions below.

This is an implemented, tested corrective phase, **not a completed encounter or a 1:1 certificate**.
The latest user direction makes official OpenRune content APIs the integration constraint. New
engine APIs require a separate review under `AGENTS.md`. No shared-engine API was added by this
corrective patch. The pre-existing encounter still contains fork dependencies and known defects.

## Source of truth and preservation

- Canonical tree: `C:\Users\norma\Documents\Codex\2026-08-10\lets-work-on-the-openrune-server\work\OpenRune-Server`.
- Doctor confirmed live GameServer PID32320 has this `user.dir`. It is the original process, not
  a deployment of this patch.
- Cache revision remains240. `.data/cache/SERVER`, `.data/cache/LIVE` and `.data/postgres` were not
  changed. SERVER dat2 SHA-256 remains
  `899D421EE88ADE6CE7956EC2A13A09ADCC4882264D27B1CF2BC1CED42AC821E3`; LIVE remains
  `FD0FE5EF66039A791A900EDDDF1CDBCFE57E09E8C32EA3496C69E96217A41A7B`.
- Existing Royal Titans module/command/drop tables were preserved as the base. No second encounter,
  command registration, nexus, access requirement or account-mode branch was added.
- The concurrent `AGENTS.md`, `PlayerDeath.kt`, Vorkath config/storage and Vorkath test edits belong
  to other work and were not overwritten. Nothing was staged, committed, reset or restored from Git.
- Our own draft `DamageKind.kt` and `RoyalTitanCycle.kt` were withdrawn, together with their draft
  consumers. These were session-created files, not user baseline files. Their proposals/limitations
  remain documented; no user source was deleted.

## Files changed in this corrective patch

Within `content/bosses/royal-titans/`:

- `build.gradle.kts`: real hit/death/instance test dependencies and deterministic test configuration.
- `src/main/kotlin/org/rsmod/content/bosses/royaltitans/RoyalTitansCombat.kt`: native impact/death
  event registrations, inactive corpse hit/death queue routing.
- `src/main/kotlin/org/rsmod/content/bosses/royaltitans/RoyalTitansEncounterManager.kt`: native
  contribution, refunds, uncapped transition, presentation, wall, Blast and corpse corrections.
- `src/main/kotlin/org/rsmod/content/bosses/royaltitans/RoyalTitansConfig.kt`: corrected elemental
  spawn sounds and named inactive sequences.
- `src/main/kotlin/org/rsmod/content/bosses/royaltitans/RoyalTitansRules.kt`: wall triplet, injected
  safe-tile RNG, actual remaining-delay rule; removed fixed elemental max-hit table.
- `src/test/kotlin/org/rsmod/content/bosses/royaltitans/RoyalTitansCombatIntegrationTest.kt`: new
  real-instance/hit/death tests.
- `src/test/kotlin/org/rsmod/content/bosses/royaltitans/RoyalTitansRulesTest.kt` and
  `RoyalTitansEntryContractTest.kt`: corrected rules/tests; removed misleading source-text assertion.
- `docs/api-mapping-review.md`, `docs/capture-validation.md`, and this report.

Within `content/skills/magic/spell-attacks/`:

- `build.gradle.kts`: public accuracy-formula dependency; test worker heap2g.
- `src/main/kotlin/org/rsmod/content/skills/magic/spell/attacks/standard/ElementalSpells.kt`: special
  opposing-element probability in the existing cast path, before splash, retaining native max hits.
- `src/main/kotlin/org/rsmod/content/skills/magic/spell/attacks/standard/RoyalTitansElementalAccuracy.kt`:
  bounded probability calculation using the native 10,000-point accuracy scale.
- `src/test/kotlin/org/rsmod/content/skills/magic/spell/attacks/standard/RoyalTitansElementalSpellTest.kt`:
  registered spell/rune pipeline tests; Mockito cleanup and no unnecessary world collision arrays.

Formatter-only line-ending touches may appear on other Royal Titans Kotlin files; they contain no
semantic change. No drop-table, command or cache-audit source change is claimed for this phase.

## Original defect disposition

Numbers refer to the original 38-defect request. “Partial” explicitly means the defect is not closed.

| Defects | Implemented correction / outstanding work |
|---|---|
| 1–3 | Still open: fixed retreats/returns, shared schedulers and global ranged state. Speculative random scheduler and65-tick cooldown withdrawn. Native arrival routing needs verified movement policy; fork ranged multiplier needs approved integration. |
| 4–5 | Still open: horizontal packs, routing, target retention and lifetime. Evidence contradicts a single fixed lifetime, including within one pack. No guessed replacement distribution installed. |
| 6 | Partial: injectable RNG replaces row-major safe selection; survivor far-row candidates retained. Uniform candidate probability is not established OSRS behavior. |
| 7 | Selected wall segment plus its immediate left/right neighbours now removed; endpoints remain when targeting the middle. Native collision tested. Actual wall cast delay/resource/accuracy and complete packet ordering remain open. |
| 8–9 | Partial: non-player active boss damage no longer unconditionally zeroed. Boss contribution reads native finalized damage records across both same-object Titans/corpses, excluding helpers. Source-kind classification and August saturation remain open. |
| 10 | Removed hardcoded elemental damage table. Standard elemental cast uses native scalable base max and native damage/weakness calculation; special probability runs before splash. Full equipment/Twinflame/splash integration is not proved. |
| 11 | First Titan death no longer cancels the entire active Blast; subsequent coverage filters the dead element. Full pulse/scheduler integration tests remain open. |
| 12 | Custom elemental death runs before repository deletion. Final native impact refunds action delay once per killed NPC; simultaneous kills stack and queued lethal races do not duplicate refunds. |
| 13 | Native queued221→194 crossing hit is uncapped and tested. Crossing-hit-to-teleport delay remains different from the capture. |
| 14 | Branda then Eldric+2 ticks, coordinates, facing and spawn sequences exercised through real instance repositories. Initial retreat parity still open. |
| 15–17 | Partial: transform existing NPC into corpse, keep UID/slot/contribution, apply inactive sequence and gate claims until both die. Clear pending queues, consume later corpse hits, prevent generic death fallback and regeneration. Collapse/reinvigoration/reset graphics and per-player menu visibility remain open. |
| 18–19 | Spawn sound IDs corrected. Wall formation/active/break sounds use observed delays. Live sound mix, packet ordering and possible cached-sequence duplication remain unverified. |
| 20 | Removed guessed inter-Titan projectile: audited normal-capture projectiles are player attacks. No replacement projectile fabricated. |
| 21–22 | Still open: native entrance warning/start/join/quick-start flow and persisted scoreboard909. Existing command retained. |
| 23–30 | Still open: native reward delivery, contribution scaling, two main rolls, unique replacement, pages/sacrifice tertiaries, learned-scroll suppression and CA clue tiers. No guessed full-loot threshold or unique topology installed. |
| 31–32 | Still open: transactional completion/claim persistence, KC/deaths/PB/global values and crash-safe delivery. |
| 33–35 | Still open overall: outside unsafe-death storage, native departure hit cancellation and complete instance detachment. New refund/death bookkeeping clears on reset; that does not prove all repeated-run state is clean. |
| 36 | All11 CAs remain open. Cache task identities alone do not implement native awards or event semantics. |
| 37 | Partial: executable production-path tests replace several misleading assertions. Golden movement, full encounter, reward/persistence/concurrency and live protocol suites remain missing. |
| 38 | Corrected ledger; blanket confirmation of contradicted/inferred claims withdrawn. Each retained observation has a capture/cache/source reference and is separated from implementation status. |

## Evidence behind implemented values

The detailed event ledger is [capture-validation.md](capture-validation.md). Raw captures were read
only; decoded streams and decoder source were inspected, but raw binaries were not re-decoded.

- Spawn:2283 T1269/L16800 and T1271/L16830; Branda(2906,9566),11966,face(2912,9566), then
  Eldric(2914,9566),11963,face(2908,9566). Source11669 and entry(2911,9573) retain cache/capture mapping.
- Transition:2283 T1553/L23973 has27 damage221→194. Teleport/dialogue1555, Branda landing1558,
  Eldric teleport1559 and landing1562: relative+3/+4/+7. Branda GFX3216/3217 slot2 and
  landing face(2911,9567). Absolute clock now avoids the legacy counter's extra tick; this does not
  fix the initial hit-to-teleport delay or validate landing knockback.
- Wall:2283 T1341/L18319–18327 removes selected2911, then2910, then2912. Four-tick formation;
  occupied omission at1356/L18667–18681. Form-to-end39 ticks from1361→1400 and1368→1407.
  Fire10147 delay5,10148 delay120,10146 delay0; ice10116 delay5,10118 delay122,10119 delay4.
- Elementals: packet order maps ice spawn11954/GFX3202, death11951/3203, explosion11959/map3206;
  fire11956/3204,11957/3205,11961/map3207. Spawn sounds ice10172/fire10190. Cache identities checked
  by the existing revision240 audit. Full slot/radius/lifetime parity remains open.
- Accuracy: current [Royal Titans strategies](https://oldschool.runescape.wiki/w/Royal_Titans/Strategies)
  gives `max(30, magic bonus+30)%`, Void×1.45, capped100%. Native boundary tests include+70 and+39
  Void; damage is not replaced with a guaranteed maximum.
- Refund: the [February update](https://secure.runescape.com/m=news/royal-titans-cas--birthday-rewards?oldschool=1)
  includes the Twinflame second-hit refund correction. Tests prove a metadata-free later native hit
  receives the refund, not full visual/echo parity.
- Corpse:2283 T1651 index49582→14148/11990; T1806 index49605→14149/11985. Runtime tests preserve
  object UID/slot for either order. Queue/regen safeguards are native integration requirements, not
  invented OSRS packet events.
- Blast:2283 T1570 charge,1576 execute,1577/1579/1581 pulses;14154/headbar78 removed first pulse,
  safe56003 removed1583. Survivor Eldric safe row9570. Full production schedule remains incorrect.
- Rates and scoreboard: no new rates or interface values were installed this phase. The ledger
  records interface909 and its captured components, and explicitly leaves reward/persistence open.

## Automated verification

- Required exact Royal Titans/cache command passed in31s:

  ```text
  .\gradlew.bat :content:bosses:royal-titans:test :or-cache:test --tests dev.openrune.RoyalTitansR240CacheAuditTest --no-daemon --console=plain
  ```

  Log: `logs/royal-titans-native-required-final-20260908.log`.
  Royal Titans29 tests (15 integration,10 rules,4 entry contracts); cache audit1 test. No failures.
- Full spell-attacks suite:65 tests, no failures/skips, in
  `logs/royal-titans-native-verification2-20260908.log`. That combined invocation failed only the
  then-unfixed corpse headbar regression; the subsequent required command above verifies its fix.
- Earlier fixture failures were corrected: missing native setup, pending-animation lifecycle,
  injected RNG shadowing, and excess collision-map/mock retention. Failed exploratory runs remain
  in `logs/royal-titans-native-*`; none is represented as passing.
- Broader final check/build passed in1m17s:

  ```text
  .\gradlew.bat :content:bosses:royal-titans:check :content:skills:magic:spell-attacks:test :api:combat:combat-formulas:test :content:other:commands:test :server:app:assemble --no-daemon --console=plain --continue
  ```

  Log: `logs/royal-titans-native-broader-final-20260908.log`. Full spell suite65 tests passed again;
  Royal Titans check passed; server distributions and shadow JAR assembled. Formula tests were
  UP-TO-DATE (11 tests in existing passing reports). Commands test was NO-SOURCE, not a test pass.
- Scoped Spotless formatting completed; unrelated magic/Vorkath sources were not formatted.
- `git diff --check` passed after the corrective code changes.
- Final scoped doctor passed: `logs/royal-titans-native-doctor-final-20260908.log`. It still reports
  original PID32320 and the unchanged canonical paths/cache fingerprints.

Tests use injected RNG/manual clocks. The encounter fixture uses real instance allocation, NPC/loc
repositories, collision, queued-hit processing and generic death fallback; it does not run terrain
routing or a client. HUD/drop-table collaborators are mocked. Spell tests execute registered content
and rune transactions but record damage-formula delegation using a mocked combat manager.

## Runtime and packet comparison

No revised server startup, solo/duo client scenario, two-restart persistence verification, outgoing
packet recording or OpenRune-versus2283 event diff has been completed. Doctor confirms the canonical
existing JVM only. Existing historical startup markers are not evidence for this patch.

Deployment is held: departure-hit safety, death storage and transactional rewards remain unfinished.
Passing tests/builds do not make this a safe complete encounter or justify a1:1 claim.

## Next approval boundary

Continue content work only through the mapping in [api-mapping-review.md](api-mapping-review.md).
Before shared-engine changes, review minimal public capabilities for source-tagged finalized damage,
generation-safe queued-hit cancellation, resolved native personal-drop delivery, persistent atomic
completion/claims, death storage and CA/prayer-loss events. Existing fork callbacks must not simply
be reused or renamed as official APIs. The shared100%-freeze bug also needs explicit review.

Independently, more behavioral evidence is required for patrol/rotation policy, elemental lifetime,
safe distribution, late-join equality and unpublished contribution/unique topology. API approval
cannot turn these uncertainties into verified OSRS rules.
