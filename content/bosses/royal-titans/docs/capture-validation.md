# Royal Titans capture-validation ledger

Implementation target: OpenRune revision 240, using established OpenRune content APIs.
This is an evidence ledger, not a parity certificate. The previous version incorrectly treated
several contradicted timings and implementation assumptions as confirmed. Those claims are
withdrawn below. The encounter is not ready for a 1:1 or full upstream-compatibility claim.

Evidence priority:

1. RSProx capture 2283 (normal-game solo, revision 228.1) for exact visible ordering and timing.
2. RSProx captures 3398 and 3402 (Grid Master) for cleanup, duo targeting, interfaces and opposite
   kill order; not accelerated combat timings or damage.
3. Current OSRS Wiki mechanics and strategy pages for intended rules not directly observable in a
   packet trace.
4. Active revision-240 cache for identities and definitions, not behavioral timing.
5. Existing code is a starting point, not behavior evidence.

## Observed facts versus implementation

Line references below are to `transcripts/royal-titans-2283.txt` unless a different capture is named.
The raw 2283 SHA-256 matches the supplied README:
`100EDE960B4ADBCE90E171B53C741AFB6055E01ED30D96848594EF70D682498B`.
The original audit read decoded streams and decoder source. The 2026-09-09 body-model follow-up
also parsed all 36,137 packets / 1,871 tick boundaries in raw2283, consuming all444,169 bytes.
Its four body set/reset blocks are detailed in [body-model-api-report.md](body-model-api-report.md).

| Feature | Specific evidence | Current implementation / limitation |
|---|---|---|
| Phase-two body models | 2283 B1558 L24086-87 model55952, E1562 L24168-69 model55940. Raw body reset flag01 at B1651/E1806; LIVE model archives verified | Approved shared model API now sets at the two landings, clears independently on corpse transformation and before protocol release. Actual r240 serialized bytes, later observers, pool reset, both kill orders and repeated completions have executable tests. Full live-client visual parity is not certified; see body-model report. |
| Spawn | T1269 L16800 Branda 12596 at (2906,9566), sequence11966, face(2912,9566); T1271 L16830 Eldric14147 at (2914,9566), sequence11963, face(2908,9566) | Native repository spawn, animations and facing added. Real-instance test covers separation and these values. No initial-retreat parity claim. |
| Movement | Branda T1274/1275 x2905/2904; E T1276/1277 x2915/2916. Route sections L17075-17169,18067-18168,18447-18649,22629-22919 include long north/south and player-dependent returns | Medal correction uses independent arrival-driven native routes. First retreats and first patrols have golden anchor/facing/action tests through native movement. Full return/summon selection, random weighting and close-action budgets remain explicitly unverified. |
| Stomp | Branda seq11974 T1303, impact1304; Eldric11973 T1306, impact1307. GFX3211 delays0/2/4/6/8 across columns | Directional current east/west coverage and one-tick queued hits exist. Full facing/footprint and tick-order verification remains incomplete. No Titan-to-player stomp projectile. |
| Projectiles | All 43 normal-capture projectile records audited have player origins; IDs1459/1465/1887/337 | Removed the inferred inter-Titan projectile. Tile graphics3209/3210 are not evidence of a Titan projectile. |
| Wall formation | Five tiles x2909..2913; four-tick telegraph. T1356 L18667-18681 omits(2911,9567), hit7 | Five-piece native collision and occupied-piece omission tested separately from client movement order. Capture uses the preceding player position; that engine-order issue remains open. |
| Wall spell removal | T1339 L18248-18296 FireSurge casts; impact/removal1341 L18319-18327 selected2911, then2910, then2912; endpoints remain. WaterSurge1345 L18379-18408 impacts1348 L18448-18456 | Native manual/autocast handlers consume runes with no XP or action delay, use native projectile durations, remove triplet at impact and never roll damage/accuracy. Tests include Ancient ice and Twinflame substitution. Full network ordering/loc29733 replacement remains unverified. |
| Wall lifetime/audio | Ice cast1357/form1361/end1400/NPC-delete1401; fire1364/1368/1407/1408. Telegraph packets fire10147 delay5 +10148 delay120; ice10116 delay5 +10118 delay122; radius1/loops1. Break fire10146 delay0, ice10119 delay4 | Lifetime39 from formation and delayed area sounds corrected. Natural deletion ordering, overlapping walls and cached-sequence audio duplication still need client/packet tests. |
| Elemental packs | Ice T1406 L19948 x2912,y9563..65; fire T1416 L20306 x2909,y9571..73; other packs1495/1500/1586/1651/1716/1759 use different X/Y | Vertical formation and native stacking/routefinding replace horizontal reserved-tile behavior. Observed pack coordinates seed a named selection policy; complete candidates, collision displacement, target retention and distribution remain unverified. |
| Elemental presentation | Ice14151: spawn11954/GFX3202 slot2; death11951/GFX3203 slot0; explosion11959/map3206. Fire14150:11956/3204 slot2;11957/3205 slot0;11961/map3207. Spawn sounds ice10172/fire10190, radius5 at first pack tile | Sound IDs corrected; custom death precedes deletion. Native spawn/summon slot2 and death slot0 are now integration-tested with zero height/delay. Wrong pack coordinates/sound origin and complete animation timing remain open. |
| Elemental routes/lifetime | Ice spawn1406, face1407, first move1409; stack1410. Fire1586 pack explodes1602/1616/1620; ice1759 pack1780/1793/1799 | Native routefinding/stacking, dynamic-wall detours and native four-tick melee handoff now have executable coverage. Source-defined pursuit leash covers the instance without widening attackRange1. Exact first-step cadence is not proved; legacy lifetime18 remains an explicit defect. |
| Elemental spell accuracy | Current Royal Titans/Strategies: max(30,magic bonus+30) percent, full Void x1.45, capped100%; +70 or +39 full Void guarantee success | Applied before splash selection in the existing standard spell handler through public `AccuracyFormulae.isSuccessfulHit`. Native scalable spell max/damage calculation retained. No guaranteed-max override is inferred from guaranteed accuracy. |
| Elemental delay refund | Current Royal Titans/Strategies; February official update explicitly fixes Twinflame second-hit refunds | Native final `onNpcHit` event refunds one tick per lethal elemental. Tests exercise queued-hit races and a later magic hit without spell metadata. This is not full Twinflame launch verification. |
| Transition | T1553 L23973:27 damage,221 to194. Branda teleport/dialogue1555 L24005, land1558 L24082; Eldric teleport/dialogue1559 L24111, land1562 | Removed crossing-hit cap. Dialogue moved to teleport start. Absolute transition clock fixes shared-counter drift; actual +3/+4/+7 offsets and Branda landing face are tested. Branda graphics3216/3217 use slot2. Crossing-hit-to-teleport delay still differs; exact landing/knockback and full packet fields remain open. |
| Blast | Charge1570 L24345, execute1576, pulses1577/1579/1581. Timer14154/headbar78:fill0 to30,endTime210; removed1577 L24484. Safe56003 shape22 removed1583 L24666 | Charge6; execute-relative pulses1/3/5; timer first pulse and safe deletion+13 retained/corrected. Legacy shared scheduler remains. Native synth10155 loops1 delays28/88/148 now replaces area-sound dispatch; production execution is tested for exactly one sequence per duo viewer. |
| Blast coverage | Tile graphics split before/after the safe tile in Y-then-X order. Survivor Eldric safe row9570; examples(2909,9570),(2911,9570) | First death no longer cancels all coverage; dead element filtered. Candidate-uniform safe RNG is only a documented implementation assumption, not a proven OSRS distribution. |
| Blast cadence | Charges1570,1606,1635,1664,1700,1729,1765,1801. Summons1714 and1757 only43 ticks apart | Unconditional29-tick cadence is contradicted. Draft65-tick summon cooldown withdrawn. Below100 desynchronization condition and action budgets remain unproved. |
| Corpse identity | Branda same index49582 transforms14148 at1651, seq11990, ops00000. Eldric index49605 transforms14149 at1806, seq11985; both ops01101 | Native in-place transforms and completion-gated claims implemented. Corpse cache stats (500 HP) keep native interactions valid; queued hits are discarded by corpse routing, not a zero-HP substitute. Native operation dispatch now keys explicit transformed identity. Both kill orders and all three operations have native interaction/reach tests. Full per-player options, reward delivery and client visuals remain incomplete. |
| Reward/reset lifecycle | Eligible text1806; first claim1830; collapse1851 L31114-31130, deletion1856, B1859/E1861. Duo3402 firstclaim1042 L13825-13827, partner1047, collapse1062 | Completion no longer starts reset. First claim starts normal-capture+21/+26/+29/+31 sequence; death seq11992/11987, Branda3224 slot2 and floor death variants occur at collapse. Unclaimed corpses persist in tests. Delayed picking animation/rewards and crash-safe claims remain open. |
| Stopwatch | All40 messages fit completion tick minus last-defeated Titan spawn+1. Normal E:1806-(1271+1)=534=5:20.40.3398 B:269-(237+1)=31=0:18.60.3402 latejoin:2030-(1995+1)=34 | Content per-NPC spawn clock feeds immutable completion record. Old universal Branda+3 claim withdrawn. Kill-order tests cover the rule. Live packet-boundary reconciliation and faster late-join PB qualification remain unverified. |
| Scoreboard | Capture3402 T60 L3410-3417 interface909, close909:2 T64 L3435; title6, personal9/11/13, global15/17/19 | Named native UI now opens909 from the real scoreboard loc and reads one committed SQL snapshot. KC4648 is projected after commit/login. SQL tests cover concurrency/reconnect/two isolated DB restarts; live client/restart verification is not complete. |
| Late join | February official update establishes450 HP threshold, not comparison equality | Each Titan checked independently; pending second spawn treated as600. Tests exercise449/450/451 and the two-player limit through native entry calls. Existing >=450 equality remains an assumption. No HP scaling/new entry requirements. |

## Native integration boundaries

The implementation now reads the two Titans' native processed-damage records rather than a
parallel integer contribution map. Elemental damage is excluded. Native owner-attributed hits
remain possible. The approved shared hit context now preserves Direct/Recoil/Poison/Venom/Burn/
Thrall/Environment provenance and explicit owner UUIDs through actual HP clipping into both native
and instance damage ledgers. It does not establish the unpublished August full-loot saturation
threshold. Generic instance contribution includes helpers and is not used for loot.

Earlier unapproved draft changes were withdrawn. On 2026-09-08 the user explicitly approved the
separate bounded shared-system proposal. The current typed context/retirement implementation is
that approved integration, not restoration of the old formula callbacks. See `api-mapping-review.md`
and `shared-api-approval.md`. New project APIs are not renamed as official upstream APIs.

Tests now execute leave/rejoin, direct external coordinate mutation, disconnect tokens, lethal-hit
retirement, inherited burn, retaliation, scoped toxin reload, external instance termination,
actual helper expiry, repeated NPC deletion and both HUD listener orders. Retirement is permanent
and is checked before impact side effects. An ordinary denied logout click does not grant immunity.
The existing death-location hook is now wired to Royal Titans; native unsafe piles are outside the
instance and cleanup must not override the normal respawn. This is not gravestone parity.
Shared CA awards and durable reward APIs remain unfinished. Immutable synchronous stat observations
now exist at the shared setter and are tested; this does not award any of the eleven tasks.
The shared 100% freeze clamp and missing canonical Titan freeze parameters are fixed and tested.
The four selected NPC parameter records were installed without changing revision240, cache paths,
LIVE groups, or any non-parameter fields. See [approved-integration-report.md](approved-integration-report.md).
That was the earlier approved-phase installation. The subsequent Medal correction additionally
installs explicitly named `blockWalk=None` and `maxRange=64` for the two elementals only. The latter
is native OpenRune pursuit configuration for one map square, not an OSRS attack range claim;
`attackRange=1` and normal movement collision remain. See [medal-flow-review.md](medal-flow-review.md).

## Remaining reward, persistence and achievement work

The following original requirements are not complete: two standard rolls with unique replacement;
verified unique topology; contribution-scaled quantities/chances/pages; learned-scroll suppression;
complete sacrifice/tertiaries; native personal-drop delivery, lifetime,
Collection Log/broadcast/tracker/provenance; one transactional persistent claim across corpses;
crash-safe lifecycle completion recovery; all11 CAs and exact duo attribution. Content SQL KC/deaths/PB/global
totals and scoreboard now exist, but native personal rewards and live persistence verification remain open. Do not infer these
from a passing combat test or the presence of drop-table/cache entries.

Hard/elite shared clue predicates now require native reward state2; status1 is qualified but
unclaimed (LIVE script4782 instructions75-83 and140-154; getter4790). This corrects the shared
predicate stubs; the Royal Titans tables still need conditional-rate roll integration.
Both amulet entries now use30637 `obj.giantsoul_amulet_uncharged`, not30638 charged.

### Earlier content-phase verification (2026-09-08; before shared integration)

43 Royal Titans tests pass; all six checked suites total167 tests with zero failures/errors/skips.
The final source was rebuilt and started twice through normal server shutdown/restart, reaching
PID10160 and thenPID35084 in the canonical tree. Both startups printed the custom-build marker;
doctor confirmed revision240 and unchanged cache hashes. Native migrationV42 is committed in the
live project database. The real statistics tables still contain zero encounter/death rows: this is
startup/migration evidence, not a live fight or persistence-through-kill test.

Post-review tests additionally cover run-independent completion retries after both players leave,
reconnect varp projection without resurrecting loot, empty/PB-ineligible global-record participants,
and the scoreboard's captured comma-separated counts. No outgoing encounter packets or client
scenario were captured. Exact commands, failure history, restart logs and remaining release gates
are in [continued-implementation-report.md](continued-implementation-report.md).

## Continued packet evidence

- Invalid fire-wall message:2283 T1324/L17928. Ice wall current full message:3402 T1408/L18436.
  Invalid examples contain facing but no cast sequence/projectile/rune loss. Valid wall casts have
  no Magic XP updates. FireSurge1447 followed by WaterSurge1449 disproves a normal five-tick gate.
- Native standard magic projectile timing is retained: FireSurge1339 start51/end56, angle16,
  progress64, start/end heights43/31; map1466 delay56 height124. WaterSurge1345 end76 and removal+3.
  Launch graphics height92. Valid wall spell examples contain spell cast/impact synths but no wall
  break synth; player removal now suppresses the latter. Natural/opposing removals still use them.
- Normal first-claim warning1830/L30951 is exactly
  `<col=ef1020>The Royal Titans will reinvigorate themselves in 18 seconds.</col>`.
  Revision235 clean resets use+20/+25/+27/+29, unlike normal2283. Normal timings remain selected;
  whether this is a post-release lifecycle change or packet boundary needs further evidence.
- Unclaimed expiry is not30 ticks:3398 completion269 remains claimable328;3402 completion1835
  rewards1927. A partner's accepted claim can finish during collapse:3402 click2054, picking2055,
  reward2057. Current immediate manager claim still lacks this complete interaction sequence.
- Warning suppression:3402 T1169/L15188 menu; T1170/L15192-15194 option2 sets16089 from0to1 even
  though join subsequently fails. `varbit.royal_titans_enter_warning` is persisted by native vars.
  Enter/Join/Quick-start options correspond to55986 ops1/2/3. Native name input now sends script109,
  resumes the native name response and retains modal protection. LIVE109/682/681 use client string361
  for the remembered name. Client interaction timing and friend-list enforcement remain open.
- Latejoin3402 KC29: E creationcycle764256 minus offset762261 gives spawn1995; entry2001;
  completion2030/L26731 shows0:20.40 with ordinary PB text. KC35 independently matches. Neither
  beats the existing PB, so awarding a faster latejoin PB remains an isolated qualification assumption.

## Normal movement fixture evidence (partially implemented)

All complete routes show final walk -> next-tick inward face -> following-tick action. Edge action1
to first patrol step is9 ticks; edge action2 to return announcement8, return step9. Last close
action to outward facing7, retreat step9. Both edge phases contain exactly two actions separated
by a patrol. The close-action budget/summon substitution and endpoint choice remain unproved.

Golden anchors:

- B1286-1289: x2904,y9565,9564,9563,9562 south; face(2910,9562)1290; wall1291.
- E1288-1292: x2916,y9565 down to9561; face(2910,9561)1293; wall1294.
- B1331-1336: x2904,y9563 up to9568; face(2910,9568)1337; wall1338.
- B1347-1350: (2905,9567),(2906,9566),(2906,9565),(2906,9564); face(2912,9564)1351; wall1352.
- E1348-1355: x2916,y9564 up to9571; face(2910,9571)1356; wall1357.
- B1502-1508: x2904,y9567 down to9561; face(2910,9561)1509; wall1510.
- E1507-1510: x2916,y9567 up to9570; face(2910,9570)1511; wall1512.

Sources are the requested normal windows L17075-17169,18067-18168,18447-18649,22629-22919 and
immediately adjacent facing/action lines17183/17196,18184/18217,18673/18699,22971/23008.
`RoyalTitansMovementTest` executes the first retreats and B1286-1289/E1288-1292 through native
routefinding, collision, movement and arrival callbacks with injected row choices. The remaining
listed routes are evidence, not passing golden tests or full production path parity.

## Validation

### Additional normal-capture constraints from the approved-phase audit

Nine complete pack births were observed (three consecutive vertical tiles each):

| Tick | Element | Global X | Global Y range | Lines |
|---|---|---|---|---|
| 1406 | Ice | 2912 | 9563-9565 | 19948-19956 |
| 1416 | Fire | 2909 | 9571-9573 | 20306-20314 |
| 1495 | Fire | 2909 | 9569-9571 | 22392-22400 |
| 1500 | Ice | 2909 | 9561-9563 | 22541-22549 |
| 1586 | Fire | 2911 | 9568-9570 | 24723-24731 |
| 1586 | Ice | 2911 | 9564-9566 | 24714-24722 |
| 1651 | Ice | 2909 | 9564-9566 | 26842-26850 |
| 1716 | Ice | 2910 | 9564-9566 | 28394-28402 |
| 1759 | Ice | 2910 | 9564-9566 | 29527-29535 |

Summon precedes birth by2 ticks. Both Titans summon1649, but Branda dies1650, so only the ice pack
appears1651. All12 observed self-exploders have zero HP before exploding; their ages span
16/19/21/25/28/30/31/34/40 ticks. This disproves a universal18-tick timer, not merely its numeric value.
No replacement lifetime rule is proved. First ice pack faces1407, starts distant travel1409,
converges1410. Another elemental overlaps the player's spawn tile1716 and steps east1717, so an
unconditional two-tick movement prohibition is also not supported.

Opposing Blast fire tile2910,9567 at1613/L25733 kills all stacked ice NPCs54387-54389, clears their
ops1614/L25784-25792, animates death1615/L25813-25821 and deletes1618/L25934-25936. The stacked fire
elemental survives. Elemental explosion1620 at2909,9568 hits the formerly adjacent player for10
at1621 despite that player's move to2911,9569 (L26053/26100-26104): impact-time location alone is
not enough to reproduce victim selection. These remain production gaps.

Observed phase-two action sequences (S=summon,M=stomp):

```text
charge execute ordinary actions            next charge
1570   1576    S1584 M1591 M1598            1606
1606   1612    M1620 M1627                  1635
1635   1641    S1649 M1656 (Eldric only)    1664
1664   1670    M1678 M1685 M1692            1700
1700   1706    S1714 M1721                  1729
1729   1735    M1743 M1750 S1757            1765
1765   1771    M1779 M1786 M1793            1801
```

This supports charge+6 execute,+14 first ordinary action,ordinary spacing7,last ordinary+8
next charge. Selection of two versus three ordinary actions remains unknown. Branda122→79HP at
1609/L25596 still synchronizes the1635 charge and1649 summon; crossing100 alone does not prove
an immediate desynchronization trigger. No production golden scheduler test is claimed.

### Executable verification boundaries

- `RoyalTitansCombatIntegrationTest` executes the production encounter scripts with real allocated
  instances, repositories, NPC hit queues, hit processor and generic death fallback. Most tests use
  synthetic terrain with blocked Titan platforms; selected movement tests decode the real arena;
  HUD and drop-table collaborators are mocked. This does not exercise a live client.
- `RoyalTitansElementalSpellTest` executes registered standard spell content and real rune checking,
  while recording calls into the combat manager. It verifies special accuracy before splash,
  native max-hit delegation and manual/autocast handler behavior; it does not prove all equipment
  formulas or packet parity.
- Removed the source-string entry test and assertions endorsing a transition cap, fixed spell-hit
  table, fixed Blast cadence and fixed-five-tick refund. Entry dialogue lock release still needs an
  executable interaction test; manager entry coverage is not a substitute for it.
- Startup asset validation resolves every referenced NPC, location, sequence, spot animation, and the
  charge headbar from the revision-240 cache before the world becomes available.
- Baseline tests passed before implementation. Corrective test runs and their results are recorded
  separately in the implementation report. Test-file existence is not a passing-test claim.
- Approved shared source passes affected tests/checks and rebuilt startup plus two controlled
  restart/startup checks, ending atPID16396. Database-close errors prevent a clean-shutdown claim.
  No live client fight or OpenRune packet event diff has been completed. Do not compare an invented
  inter-Titan projectile curve. Exact results are in `approved-integration-report.md`.

## 2026-09-09 phase-one platform/damage regression

- Real revision-240 SERVER region 11669 map and loc collision reproduces the reported first-phase
  stall: platform tiles carry `BLOCK_WALK` (`0x200000`), while active Titans previously used
  `MoveRestrict.Normal`. Both remained at their spawn anchors for 65 ticks and queued no stomp.
- Active Titans now use native `MoveRestrict.Blocked`; `RouteFactory.create` receives the same
  strategy as native NPC step execution. This permits their blocked-terrain platform routes while
  retaining wall/loc collision. Player and elemental movement restrictions are unchanged. Their
  normal type restriction is restored at each second-phase landing, not at the start of the paired
  transition (Eldric may still be completing a first-phase step then).
- `RoyalTitansPhaseOneDamageTest` executes real-map routes and the native player queue/script/hit
  processor. It verifies damage with prayer off, zero damage with Protect from Melee, all supported
  side-platform destinations and 3x3 terrain footprints, blocked player access, retained loc
  collision, and post-landing movement restoration. A deterministic 12 is a test roll, not a new
  damage constant.
- `RoyalTitansMovementTest` uses real arena collision for its opening golden trace. Capture2283:
  Branda1299–1304 L17302–17444; Eldric1302–1307 L17371–17538. Branda returns via(2905,9562),
  (2906,9562), faces at1302, stomps11974 at1303, damage12 at1304. Eldric returns via(2915,9562),
  (2914,9563), faces at1305, stomps11973 at1306. The test injects the observed destination choices;
  it does not establish the unpublished production selection distribution or replay every player
  movement packet. Eldric's20 damage at1314 L17689–17690 confirms five-tile forward reach.
- Corrected two elemental tests that previously expected routes through the blocked Titan
  platforms: a full wall closes the real walkway; an opened segment permits a collision-aware
  detour. No elemental production code changed in this pass.
- See `phase1-damage-report.md` for commands and deployment verification. Test success is not a
  new live-client fight or packet-diff claim. Earlier full-parity limitations remain open.

## 2026-09-09 wall autocast input correction

- Revision-240 SERVER/LIVE NPC14152 has Douse at op1; NPC14153 has Melt at op1. Neither
  has an Attack/op2 option. Loc56001/56002 have no options; clicks target their hitbox NPCs.
  `RoyalTitansWallInteractions` now registers native AP1/OP1, retaining manual spell AP-T.
- Normal2283 T1339/L18241 is a plain `opnpc1` on ice hitbox50324, followed by cast7855,
  GFX1464 and projectile1465 at L18269-18275; the selected triplet disappears at1341/L18319-18327.
  T1367/L18926 repeats the op1 route. The earlier tests directly published OP2 events and missed
  the live packet handler rejecting this nonexistent cache option.
- T1447 and1449 casts (L21187-21271) are two ticks apart; T1467 wall cast followed by T1469
  Titan melee precedes the T1470 wall impact (L21706-21806). These are examples, not proof of a
  two-tick cooldown. Current Fire/Icicles Wiki pages specify no attack delay. Production continues
  to preserve the player's existing action delay, spend native rune requirements once, and remove
  segments on native projectile impact. No Yama/ToA hit-delay refund was copied.
- Ice rejection is exactly `You'll need to use a fire spell to melt the ice.` (2283 T1336/L18163,
  T1441/L21082, T1460/L21540). Fire rejection remains the longer water-or-ice requirement.
- `RoyalTitansWallCastTest` now executes `OpNpcV2` -> `OpNpcHandler` ->
  `PlayerInteractionProcessor` -> registered native AP1/OP1. It tests both walls, an outstanding
  combat delay, repeated one-tick casts, two-tick opposite-wall casts, exact rejection messages,
  rune use, no XP, and delayed three-segment removal. Manual casts still enter at the AP-T event
  in this fixture; this is not a complete live-client packet replay.
- See `wall-home-report.md` for test/build/deployment results and the separate requested
  OpenRune Home Teleport customization. Previous full-encounter parity limitations remain open.

## Sources

- https://rsprox.net/download/2283
- https://rsprox.net/download/3398
- https://rsprox.net/download/3402
- https://oldschool.runescape.wiki/w/Royal_Titans
- https://oldschool.runescape.wiki/w/Royal_Titans/Strategies
- https://secure.runescape.com/m=news/royal-titans-cas--birthday-rewards?oldschool=1
- https://secure.runescape.com/m=news/summer-sweep-up-slayer--more?oldschool=1
- https://github.com/runelite/runelite/blob/e1afd4502d3819700374bd045ab4dfcf6f9d82ec/runelite-client/src/main/java/net/runelite/client/plugins/opponentinfo/OpponentInfoPlugin.java
