# Wardens-only revision 211 to revision 240 checklist

This checklist was produced before the Wardens port was edited.

- Source: `%USERPROFILE%\OneDrive\Desktop\TOA`
- Target: `<repo-root>`
- Scope: Wardens P1-P4 only. Other Tombs of Amascut rooms, the revision-240 cache/client/protocol/maps/interfaces, and unrelated content are out of scope.
- Baseline: `:content:minigames:tombs-of-amascut:test` passed before implementation.

## Mechanic map

| Phase | Source mechanic/function | Target file/function | Required revision-240 mappings | Final status |
|---|---|---|---|---|
| P1 | No complete donor implementation. `ToAObjects.enterElidinisRoom` enters the donor's P2 room directly. | `ToaRaidManager.spawnWardenObeliskPhase`, `tickWardenObelisk`, `resolveWardenEnergyPulse`, `beginWardenPhaseOneSpecial`, `beginWardenPhaseOneTransition` | NPCs 11746-11752 and 11759-11760; locs 45748-45751; projectiles/spotanims 2208, 2224, 2225; native Wardens map 15184 level 1 | implemented; compiled; helpers tested; startup assets validated |
| P1 | Obelisk damage, threshold clamping, fatal adjacent explosion, destroyed transition | `WardenPhaseOneMechanics`, `WardenObelisk`, `ToaRaidManager.onNpcHit`, `resolveWardenObeliskExplosion`, `processWardenPhaseOneTransition` | NPCs 11751, 11752, 11759, 11760; animations 9662; spotanim 2220 | implemented; compiled; regression tested |
| P1 | Three charging-orb lanes, player collision/interception, passive Warden charge, active-P2 result | `WardenPhaseOneLayout.energyStreamLanes`, `blockingSide`, `resolveWardenEnergyPulse`, `WardenObelisk.phaseTwoWarden` | NPCs 11748, 11749; projectiles 2224, 2225; charge headbar | implemented; compiled; regression tested |
| P1 | Ancient Haste cadence, rotating traps, blue spread, orange stack | `WardensRuleset`, P1 runtime helpers in `ToaRaidManager` | locs 45748-45751; spotanims/projectiles 2208, 2220, 2224, 2225 | implemented; compiled; damage/cadence tests passed |
| P2 | `ElidinisWarden.preDefend` style restriction | `ElidinisWarden.requiredStyle`, `TumekenWarden.requiredStyle`, `ToaRaidManager.onNpcHit` | forms 11753/11754 and 11756/11757 | implemented; wrong-style zero-damage regression passed |
| P2 | `ElidinisWarden.attack`, `meleeAttack`, `mageAttack`, `rangeAttack` | `ToaRaidManager.tickWardenPhaseTwo` | animations 9660 and 9770; projectiles 2224 and 2241 | implemented; Warden movement AI and target re-armed |
| P2 | `launchSwordSpecial`, `launchArrowSpecial`, `launchBlueballSpecial` and Penetration prayer disable | `tickWardenPhaseTwo`, `scheduleTrackingHit`, `applyWardenPenetration` | projectiles 2204, 2206, 2208; animation 9660 | implemented; donor 1-in-11 Penetration roll tested |
| P2 | `launchStuckInRockSpecial`, `handleStuckInStone` | stone/imprisonment runtime in `ToaRaidManager` | projectile 2210; warning 2111; player animation 739; player graphic 2211 | implemented; assets validated at live startup; cleanup audited |
| P2 | `shadowLightningAttack`, `handleLinesSpecial`, `handleWindmillSpecial` | `performWardenObeliskAttack` explicit converging-lines, windmill, and lightning-skull runtime | projectiles 2225; spotanims 2111, 2198, 2220, 2234, 2235, 2236 | implemented; all three pattern cycles regression tested |
| P2 | `ElidinisWarden.sendCore`, `startDeath` | `exposeWardenCore`, `resolveWardenCore`, `spawnWardenPhaseTwoShield` | body 11755/11758; cores 11771/11770; projectile 2227; animations 9670/9672 | implemented; 5x transfer/location/timer/revive/style helpers tested |
| P2 | Acceleration and Penetration | `WardensRuleset`, `tickWardenPhaseTwo`, `applyWardenPenetration` | no additional asset | implemented as fixed maximum ruleset; tested |
| P3 | `TumekenWarden.leftAttack`, `rightAttack`, `drawLCenterAttack` | `WardenFloor.pattern`, `slamAnimation`, `resolveWardenSlamIfDue` | animations 9675, 9677, 9678; warning/impact 2111/2220 | mapped; existing implementation tested by unit baseline |
| P3 | Donor thresholds are 85/65/45/27; requested behavior is 80/60/40/20 | `TumekenWarden.siphonThresholds`, `processWardenTransitions` | NPCs 11772/11773; projectiles 2227/2226; impact 2160; animations 9683/9684 | implemented; damage clamps at 80/60/40/20 and 5%; tests passed |
| P3 | `sendSkulls`, melee-only skull damage, `skullsFailedEvent`, `sendSkullsToBoss` | `beginWardenSiphons`, `onNpcHit`, `expireWardenSiphons`, `finishWardenSiphons` | NPCs 11772/11773; projectiles 2227/2226; spotanim 2160 | implemented; donor counts, team HP, reflected success and local failure damage tested |
| P3 | Donor Tumeken branch: Zebak/Ba-Ba | `spawnNextWardenPhantom`, `tickWardenPhantom` | NPCs 11774/11775 | implemented; branch selection tested |
| P3 | Donor has no Elidinis final branch; requested target branch: Akkha/Kephri | `spawnNextWardenPhantom`, `tickWardenPhantom` | NPCs 11777/11776 | implemented with native 240 assets; branch selection tested |
| P3 | Overclocked I/II and Insanity cadence | `WardensRuleset`, `tickWardenPhaseThree`, siphon timers | no additional asset | implemented as fixed maximum ruleset; timing tests passed |
| P3 | Stay Vigilant and Aerial Assault phantom effects | `tickWardenPhantom` | existing phantom NPCs and Warden floor graphics | implemented: rotating Akkha styles and radius-one Kephri bombs |
| P4 | Donor `startFloorBreakingEvent`, `removeTile`, `handleLightningEvent`; requested transition is 5%, not donor's 15% | `processWardenTransitions`, `processWardenFloorCollapse`, `tickWardenEnrage` | animation 9685; projectile 2228; spotanims 2111, 2199, 2220 | implemented; heal, exact eight collapse rows, inward push, and remaining-floor lightning tested |
| Cleanup | Donor room events rely on region teardown | `ToaEncounterState.reset`, `cleanupWardenEffects`, raid lifecycle/session teardown | no additional asset | implemented for death/reset/logout/teleport/abort/completion; static audit and regression compile passed |

## Asset map

All mapped IDs below are native revision-240 definitions. No revision-211 cache, client, packet, interface, map, or generated-ID files will be copied.

### NPCs

| Role | Source ID | Revision-240 ID | Status |
|---|---:|---:|---|
| Elidinis inactive / charging | donor P1 absent | 11746 / 11748 | mapped from native 240 Wardens family |
| Tumeken inactive / charging | donor P1 absent | 11747 / 11749 | mapped from native 240 Wardens family |
| Attackable / exhausted obelisk | donor P1 absent | 11751 / 11752 | mapped from native 240 Wardens family |
| Elidinis mage / ranged / core-ejected | 11753 / 11754 / 11755 | 11753 / 11754 / 11755 | exact |
| Tumeken ranged / mage / core-ejected | donor class does not script P2 | 11756 / 11757 / 11758 | mapped from native 240 Wardens family |
| Destroyed Elidinis / Tumeken | donor P1 absent | 11759 / 11760 | mapped from native 240 Wardens family |
| Final Elidinis / Tumeken | 11761 | 11761 / 11762 | exact for donor Tumeken-room spawn; alternate native form mapped |
| Invulnerable final forms | donor transforms skull state indirectly | 11763 / 11764 | mapped from native 240 Wardens family |
| Tumeken / Elidinis core | donor 11771 | 11770 / 11771 | exact Elidinis; native alternate mapped |
| Active / disabled energy siphon | 11772 / 11773 | 11772 / 11773 | exact |
| Zebak / Ba-Ba phantom | 11774 / 11775 | 11774 / 11775 | exact |
| Kephri / Akkha phantom | donor branch absent | 11776 / 11777 | mapped from native 240 Wardens family |

### Objects and maps

| Role | Source | Revision-240 mapping | Status |
|---|---|---|---|
| P1/P2 room | dynamic donor map containing source square 15184, plane 1 | `ToaRoom.WARDENS`, source region 15184, plane 1 | exact native map |
| P2 boss tile | 3799,5154 = local 23,34 | `ToaRoom.WARDENS` boss local 23,34 | exact |
| P2 entry tile | 3808,5177 = local 32,57 | `ToaRoom.WARDENS` entry local 32,57 | exact |
| P3/P4 room | dynamic donor map containing source square 15696, plane 1 | `ToaRoom.TUMEKEN_WARDEN`, source region 15696, plane 1 | exact native map |
| P3 boss tile | 3934,5152 = local 30,32 | `ToaRoom.TUMEKEN_WARDEN` boss local 30,32 | exact |
| P3 entry tile | 3936,5161 = local 32,41 | `ToaRoom.TUMEKEN_WARDEN` entry local 32,41 | exact |
| Reward entry crystal | object 45138 at local 32,36 | `ToaLocs.REWARD_ENTRY` 45138, local 32,36 | exact |
| P1 pyramid warnings | donor P1 absent | objects 45748, 45749, 45750, 45751 | native 240 mapping |

### Animations

| Use | Source ID | Revision-240 ID | Status |
|---|---:|---:|---|
| Warden attack/special | 9660 / 9662 | 9660 / 9662 | exact |
| Core ejection/revive | 9670 / 9672 | 9670 / 9672 | exact |
| P3 left/right/center slam | 9675 / 9677 / 9678 | 9675 / 9677 / 9678 | exact |
| P3 death/skulls/enrage | 9682 / 9683 / 9684 / 9685 | 9682 / 9683 / 9684 / 9685 | exact |
| Warden melee | 9770 | 9770 | exact |
| Player imprisoned in stone | 739 | 739 | exact; validated by clean revision-240 startup |

### Graphics and projectiles

| Use | Source ID | Revision-240 ID | Status |
|---|---:|---:|---|
| Tile warning / P4 lightning / expanding lightning | 2111 / 2199 / 2198 | same IDs | exact |
| Siphon failure / player stone | 2160 / 2211 | same IDs | exact; validated by clean revision-240 startup |
| Sword / arrow / blue ball | 2204 / 2206 / 2208 | same IDs | exact |
| Stone / floor / mage / shadow / siphon-to-boss / core-or-siphon spawn / floor removal | 2210 / 2220 / 2224 / 2225 / 2226 / 2227 / 2228 | same IDs | exact |
| Isolation lines / skull-bomb tiles / wheel warning | 2234 / 2235 / 2236 | same IDs | exact |
| Ranged attack | 2241 | 2241 | exact |

## Known source differences that will not be copied blindly

- The donor starts at its P2 room and therefore cannot be the authority for P1. The existing revision-240 P1 is retained and completed using native 240 assets.
- The donor only implements the Tumeken final-room branch. The required Elidinis/Akkha/Kephri branch remains a target-native implementation.
- The donor's skull thresholds (85/65/45/27) and enrage threshold (15%) conflict with the requested 80/60/40/20 and 5%. The requested values are authoritative.
- The donor hard-codes dynamic-region bases, global events, and cache IDs. The target keeps its existing `InstanceService`, raid state, combat pipeline, local-coordinate conversion, cache validation, and cleanup lifecycle.
- No separate P4E will be added.

## Completion evidence

- [x] Focused Wardens regression class passes after the final changes.
- [x] Full Tombs of Amascut module test suite passes: 158 tasks.
- [x] Full revision-240 project assembles: 801 tasks.
- [x] Server starts on port 43594, reports revision/cache 240, validates all mapped Wardens assets, and reaches `Server ready`.
- [x] P2 wrong-style zero damage, core 5x transfer, core locations, revival/style helpers, all Obelisk patterns, siphon thresholds/counts/HP/success/failure, both phantom branches, P4 push, and remaining-floor lightning have deterministic regression coverage.
- [x] Death/reset/logout/teleport/abort/completion paths clear Wardens freezes, pending hits, area pulses, spawned actors, and real P1 dynamic trap objects.
- [ ] A human-controlled live raid still needs to click through P1, P2, P3, and P4. The repository has no headless game client, so this is deliberately not marked as tested from terminal-only evidence.

Live test entry: use `::toawarden` on an administrator account. It creates a solo raid, marks only the four path completions required to unlock Wardens, teleports to the native Wardens room, and starts the real P1 state machine.
## 2026-08-29 authoritative reconciliation

This section supersedes any conflicting timing, HP, animation, or status claim above.

### Evidence hierarchy

1. Current OSRS Wiki mechanics and official balancing notes for current behavior.
2. Full solo Entry Mode RsProx capture `%USERPROFILE%\.rsprox\binary\Old School RuneScape\zebak-audit-rsprox-14.txt` (Wardens packets approximately lines 129627-164718).
3. OpenRune revision-240 cache definitions for every target-side NPC, object, animation, graphic, projectile, and map.
4. Donor code only for control-flow comparison where it agrees with current OSRS.

### Reconciled mechanics and implementation status

| Phase | Evidence-backed correction | OpenRune implementation | Test status |
|---|---|---|---|
| Shared lifecycle | Explicit `IDLE -> ACTIVATING -> OBELISK -> PHASE_ONE_TRANSITION -> PHASE_TWO -> CORE -> PHASE_THREE -> ENERGY_SIPHONS -> ENRAGE -> DEATH_SEQUENCE -> COMPLETE`; rejoin uses the active Wardens fight coordinate. | `ToaWardenStage`, `activateWardenEncounter`, `processWardenTransitions`, `beginWardenDeathSequence` | Compiles; focused and full ToA tests pass. Human click-through pending. |
| P1 | Challenge start transforms inactive actors immediately; attackable Obelisk is 11751 with 260 HP; dying Warden uses animation 9669 and graphic 2216; unsupported final death-ball damage removed. | `WardenObelisk`, `spawnWardenObeliskPhase`, `activateWardenEncounter`, `beginWardenPhaseOneTransition` | Deterministic identity, layout, damage, and transition tests pass. |
| P2 | Mustard shield is 140; hidden Warden HP is 4500; core damage is 5x; exposure lengths are 21/29/37 ticks; core projectile launches at eject +5 and core becomes active at +7; baseline Elidinis/Tumeken cadence is 7/8. | `WardenCores`, delayed-core fields in `ToaEncounterState`, `exposeWardenCore`, `processPendingWardenCore`, god-specific `WardensRuleset.phaseTwoWardenAttackInterval` | HP, multiplier, exposure, projectile ID, shield, style, and cadence tests pass. |
| P3 | Final Warden base HP is 880; baseline slam cycle is right/left/both with animations 9674/9676/9678 and a 7-tick cadence; Siphons trigger at 80/60/40/20 and cancel the queued slam. | `TumekenWarden`, `WardenFloor`, `tickWardenPhaseThree`, `beginWardenSiphons` | Threshold, clamp, HP, animation, and cadence tests pass. |
| Energy Siphons | Outward projectile 2226 at threshold +2; actors 11772 spawn at +6; link projectile 2227 at +7; solo count is 4/5/6/7 and each successful solo set reflects 50 damage; failure damages every active player. | Packet-timed fields in `ToaEncounterState`, `processPendingWardenSiphons`, `finishWardenSiphons`, `expireWardenSiphons` | IDs, delays, solo reflected damage, count, HP scaling, and failure base damage are deterministic-tested. |
| Phantoms | Each spawned phantom has its own attack clock: first attack +8, baseline P3 interval 8, level-zero enrage interval 5. | `wardenPhantomNextAttackAt`, `tickDueWardenPhantoms`, `spawnNextWardenPhantom` | Branch and interval tests pass. Exact attack geometry remains a live-test item. |
| Enrage | Starts at <=5%, heals 20%, uses transition animation 9684 then loop/attack animation 9685; defence resets to level-zero 180; first lightning warning is +5, impact is warning +3, then warnings repeat every 6; first floor-removal wave starts +6 and later waves every 18. | `processWardenTransitions`, `tickWardenEnrage`, `processWardenFloorCollapse`, `WardensRuleset.BASELINE` | Timing, collapse, push, remaining-floor, heal threshold, and animation tests pass. |
| Death/completion | Timer stops at HP zero; boss removal starts +1, phantoms clear +3, boss bar closes +9, white-light/reward passage opens +14. | `beginWardenDeathSequence`, `processWardenDeathSequence`, `completeWardens` | Compiles and lifecycle cleanup tests pass. Visible sequence still needs a human live raid. |

### Explicit remaining uncertainty

The implementation does not claim packet-perfect 1:1 behavior for these unproven areas:

- P1 UFO geometry/cadence, equal-charge tie selection, and exact transition damage.
- P2 shield damage formula/regeneration constants, exact Divine/Imprisonment timing, and Tumeken cadence branches beyond the observed/wiki baseline.
- Multiplayer Siphon counts, group reflected-damage percentage, and exact group failure formula. The packet-proven solo 50 is exact; group behavior retains the prior fallback until a group capture exists.
- Exact phantom animations/projectiles/damage/geometry and all Insanity-specific packet clocks.
- Exact native floor-location mutation sequence and the post-death black-phantom visual tail.

### Verification performed

- Focused Wardens tests: passed after formatting.
- Full `:content:minigames:tombs-of-amascut:test`: passed.
- Controlled `:server:app:assemble --rerun-tasks`: passed (423 executed tasks).
- Read-only OpenRune doctor: passed before and after deployment.
- Restart 1: revision-240 cache, `OPENRUNE CUSTOM BUILD ACTIVE`, and `Server ready` verified, then stopped.
- Restart 2: the same markers verified; this process remains live.
- Human-controlled P1-to-reward raid: not performed by the terminal agent and therefore not claimed.
