# Wall autocast and Home Teleport correction — 2026-09-09

Scope: this pass fixes Royal Titans wall clicks/casting cadence and the requested OpenRune Home
Teleport behavior. It does not assert complete Royal Titans parity or replace the prior phase-one
platform fix. Existing dirty/untracked work remains in place; no source reset or duplicate handler.

## Native API mapping and corrections

| Mechanic | Existing implementation/API used | Correction |
| --- | --- | --- |
| Douse/Melt click and approach | Cache NPC options, `OpNpcHandler`, `PlayerInteractionProcessor`, `onApNpc1/onOpNpc1` | Replace nonexistent op2 registrations with op1. |
| Manual/autocast wall spells | `MagicSpellRegistry`, `AutocastWeapons`, `MagicRuneManager`, registered spell handlers and native projectile/hit queues | Keep no added attack delay, preserve any pre-existing combat delay, consume runes once, guaranteed valid-spell removal at impact. No guessed two-tick cooldown or delay refund. |
| Invalid ice-wall spell | Existing wall rejection message | Match the normal capture's exact text. |
| Home presentation | Existing Standard `SpellTeleportScript` constants and `ProtectedAccess` animation, spotanim, sound, delay, telejump | Standard start714/end715, GFX111 height92, `synth.teleport_all`, four-tick departure, native five-tick action delay (not a recharge). Edgeville remains(3087,3495,0). |
| Home eligibility | Native `Player.isInCombat`, `CoordGrid.wildernessLevel`, `PlayerTeleportValidator`, `TeleportType.Standard` | Block current combat and Wilderness>=20, check again before departure, retain native teleport restrictions. Home ignores a leftover ordinary action delay once out of combat. |
| Home timer | Existing `HomeTeleportCooldown.syncClient` and native varp output | Send varp892=0 on login/book changes/cast. Ignore old saved timestamps, do not mutate account history or write a new recharge. |
| Existing movement/teleport replacement | `Player.clearInteractionRoute`, `ProtectedAccess.clearQueue` | Stop an old walking route and cancel only the pending `queue.spell_teleport` when an allowed Home starts, preventing a second arrival elsewhere. |
| Other teleports | Existing separate handlers and `HomeTeleport.castResting` | Ordinary Standard/Ancient/Arceuus spells unchanged. Minigame still uses its22-tick resting sequence and separate20-minute varp888 cooldown. |

No engine API was added in this pass. Mapping was reported before editing. The Home settings are
an explicit user-requested OpenRune customization, not an OSRS Home Teleport parity claim.
Supported existing Home buttons are Standard, Ancient and Arceuus; no new Lunar handler was added.

## Evidence

- Active revision240 SERVER and LIVE definitions: fire hitbox14152/Douse op1; ice14153/Melt op1;
  neither has Attack/op2. Loc56001/56002 are1x1 walking blockers, not projectile blockers, and
  have no options. Registry/cache definitions choose spell components rather than old capture IDs:
  current218:74 Water Surge and218:81 Fire Surge, versus capture228's218:71/218:78.
- Normal RSProx2283 T1339/L18241: plain opnpc1 on ice50324;7855/1464 cast at L18269-18270,
 1465 projectile atL18275, three removals T1341/L18319-18327. Repeated plainop1 T1367/L18926.
- Normal2283 T1447/L21187 and1449/L21243 requests, cast sequences L21215/L21271: two casts
  two ticks apart. T1467/L21706 wall cast, T1469/L21752-21763 Titan melee, T1470/L21798-21806
  wall removal: ordinary attack can precede wall impact. These observations are not an imposed
  production cooldown. Native projectile delay remains distance-dependent.
- Ice invalid-spell text: `You'll need to use a fire spell to melt the ice.` at2283
  T1336/L18163, T1441/L21082 and T1460/L21540.
- [Fire Wiki revision15139154](https://oldschool.runescape.wiki/w/Fire_(Royal_Titans)?oldid=15139154)
  and [Icicles revision15139669](https://oldschool.runescape.wiki/w/Icicles_(Royal_Titans)?oldid=15139669)
  describe valid-spell success regardless of accuracy and no attack delay.
- Home coordinates/effects are reused from this tree's authoritative Standard teleport handler;
  cooldown-free behavior and inclusive Wilderness20 threshold come from the user's request.
  Native combat activity lasts through last-combat+8, tested for both PvN and PvP; no additional
 17-tick resting lookback is applied to Home.

## Files changed in this pass

- `RoyalTitansWallInteractions.kt`: op1 registrations.
- `RoyalTitansWallSpells.kt`: exact ice rejection.
- `RoyalTitansWallCastTest.kt`: real packet dispatch and cooldown/message regressions.
- `content/bosses/royal-titans/build.gradle.kts`: native packet-handler test dependency only.
- `HomeTeleportCooldown.kt`: disable recharge display on every book.
- `HomeTeleport.kt`: shared fast Home implementation, combat/Wilderness checks, safe pending-action replacement.
- `SpellTeleportScript.kt`, `AncientSpellTeleportScript.kt`, `ArceuusSpellTeleportScript.kt`:
  wire their existing Home buttons to the shared behavior; reuse Standard visual constants.
- `ArceuusTeleportRuntimeTest.kt`: all supported Home books/buttons, repeated clicks, old saved
  timestamps, boundary checks, native validation, interruption, queue/movement and minigame regressions.
- `content/skills/magic/spell-teleports/build.gradle.kts`: native queue/movement test dependency.
- `docs/capture-validation.md`, this report, and `wall-home-format-files.txt`: scoped evidence and formatting manifest.

## Verification

### Tests and build

- Focused run: `:content:bosses:royal-titans:test --tests '*RoyalTitansWallCastTest'
  :content:skills:magic:spell-teleports:test --no-daemon --console=plain`: passed8 wall tests and21
  teleport tests, including17 runtime methods with parameterized book/button/boundary cases.
  Final log `logs/wall-home-focused-5.log`, successful40s.
- Required Royal/cache command plus affected modules:

  ```text
  .\gradlew.bat :content:bosses:royal-titans:test :or-cache:test --tests dev.openrune.RoyalTitansR240CacheAuditTest :content:skills:magic:spell-teleports:test :content:skills:magic:spell-attacks:test :api:combat:combat-commons:test :api:spells-autocast:test :content:interfaces:spellbook:test --no-daemon --console=plain
  ```

  Successful57s; `logs/wall-home-all-tests.log`. XML totals: Royal97, cache3, teleports21,
  spell-attacks66, combat-commons27, spellbook4;218 total with zero failures/errors/skips.
  Unchanged cache audit and the immediately preceding teleport results were up-to-date in this
  final run. Autocast module has no standalone tests; its resolver is exercised by the wall suite.
- `:api:combat:combat-commons:check :content:bosses:royal-titans:check
  :content:skills:magic:spell-attacks:check :content:skills:magic:spell-teleports:check
  :server:app:assemble --no-daemon --console=plain`: successful1m18s.
  Formatting checks used `-I scripts/format-selected.init.gradle
  -PopenruneFormatManifest=content/bosses/royal-titans/docs/wall-home-format-files.txt` so unrelated
  user source was not reformatted. Log `logs/wall-home-check-assemble.log`.
- Earlier iterations exposed a missing native packet-handler test dependency, a pre-existing
  Home source BOM rejected by ktfmt, and an incorrect route-test method name; all corrected.
  Three presentation assertions also exposed missing native end-of-tick cleanup in the test
  fixture: it now clears pending visual updates like `PlayerPostTickProcess`, rather than comparing
  new arrival animations against a stale pending departure update. Final tests retain exact715
  assertions. Read-only review found and drove the walking/queued-teleport regression tests.
- `git diff --check`: passed (`logs/wall-home-diff-check.log`).

### Runtime boundary

The pre-change doctor confirmed PID37260 using the canonical project working directory. It exited
through the normal project shutdown helper, not a force kill. The rebuilt server then completed an
initial startup and two controlled restarts. Times below are America/New_York on2026-09-09.

| Run | PID | Custom-build marker | Server ready |
| --- | ---: | --- | --- |
| Rebuilt startup | 37292 | 02:31:56 | 02:31:58 |
| Restart2026090907 | 23284 | 02:33:28 | 02:33:31 |
| Restart2026090908 | 27660 | 02:34:55 | 02:34:57 |

Each loaded432 scripts, revision240 and `OPENRUNE CUSTOM BUILD ACTIVE`. Doctor checks after all
three startups confirmed canonical JVM `user.dir`. Final PID27660 listens on43594,8080,9091.
All11 scoped Kotlin/build files retained their SHA-256 hashes through both restarts; startup did
not overwrite the fixes. Module hashes before and after restarts also match:

- Royal Titans: `9F569A4766692F2DFCC8DBDA1D6F664124EE2E002DDCD78D88A55385FACCAD90`.
- Spell teleports: `0117B1E4B8E5FCFB6D073991938BF51169E09BF1EAD8E76AF28D8D91339649FA`.
- Combat commons: `D0CDAE2D7B2D1AFEB1912079FB98DFC3C7CB2256E15D94CFEF3F278CADF9F6E2`.

Cache revision/path and fingerprints are unchanged; no player/database data migration was made:

- SERVER:27 files,35318332 bytes, dat2 SHA-256
  `A77ED0C2DFD48B093050913041B098FB4A700D6A1C32DAECABFCD2A6DD81696C`.
- LIVE:25 files,237756491 bytes, dat2 SHA-256
  `FD0FE5EF66039A791A900EDDDF1CDBCFE57E09E8C32EA3496C69E96217A41A7B`.

Logs: `logs/wall-home-start.stdout.log`,
`logs/cerberus-audit-20260907/restart-2026090907.stdout.log`,
`logs/cerberus-audit-20260907/restart-2026090908.stdout.log`. Doctor logs:
`logs/royal-titans-wall-autocast-doctor-before.log`, `logs/wall-home-doctor-start.log`,
`logs/wall-home-doctor-restart1.log`, `logs/wall-home-doctor-restart2.log`.
Source fingerprints: `logs/wall-home-source-hashes-before-restarts.json` and
`logs/wall-home-source-hashes-after-restarts.json`.

The pre-existing `Game database service failed to shut down` / `Connection reset by peer` warning
still appears on controlled shutdown. Process exit and subsequent startup succeeded; this is not
certification of a clean database flush. Persistence source was not changed for this task.

No new live-client fight, visual recording, or OpenRune-versus-RSProx packet replay has been performed
in this pass. Native packet-dispatch, movement, queue and spell execution tests are not a live-client
visual verification claim. Client handoff: Douse/Melt with the opposing autocast selected; switch
wall spells rapidly; cast Home out of combat at Wilderness19, verify rejection at20, and retry Home
immediately after arrival. Previous full-encounter parity limitations remain open.
