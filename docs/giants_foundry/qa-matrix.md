# Giants' Foundry QA matrix

Research pin: **2026-08-27**. The expected values below come from the revision-pinned research unless
marked `UNRESOLVED`. Tests must use independent golden values rather than importing the same
production tables/constants they intend to validate. Seeded RNG tests use a fake `GameRandom`.

## Test levels and acceptance

- **U**: pure unit test, no cache/world.
- **D**: static data/cache definition validation.
- **I**: integration test with player, inventory, events and tick processing.
- **P**: persistence/restart/crash injection.
- **S**: security/concurrency/repeated-packet test.
- **V**: manual differential parity vector against cited video/live behavior.

A row passes only when exact state, inventory, XP, reputation, client vars and number of game ticks
match its expected result. A guessed result cannot be marked pass; unresolved rows remain blocked.

## Repository, codec and static data

| ID | Level | Scenario/input | Exact assertion |
|---|---|---|---|
| GF-D001 | D | Resolve all Foundry logical NPC/loc/item/interface/component/var/seq/gfx/synth mappings | Every reference resolves in revision 240; no raw guessed ID |
| GF-D002 | D | 33 mould definitions | Each logical mould ID unique and assigned to exactly one of forte/blade/tip |
| GF-D003 | D | Default/unlock mould sets | 18 defaults + 15 paid; paid costs total 5,400; no duplicate unlock bit |
| GF-D004 | D | Six score columns per mould | All descriptors present once; no negative score |
| GF-D005 | D | Commission pair catalogue | Exactly 12 pairs; no duplicate descriptor and no heavy-heavy/light-light |
| GF-D006 | D | Crucible metal table | Exactly bronze..rune tiers 1..6; capacity 28; valid Smithing gates |
| GF-D007 | D | Equipment conversion catalogue | Every item resolves, is accepted once, and yield equals independent smithing cost minus one |
| GF-D008 | D | Reward catalogue | All nine item rewards and 15 mould unlocks valid; non-negative cost/buyback; collection entries exact |
| GF-D009 | D | Heat/section bands | Exhaustive/non-overlapping boundaries; lower/upper semantics proven by evidence vector |
| GF-D010 | D | Save codec fuzz | Decode valid V1; reject negative counts, >28 total, bad ordinals, mismatched IDs, trailing/truncated data |
| GF-D011 | P | V1 round trip | Every durable field round-trips; no Float/Double; runtime action/UI fields absent |
| GF-D012 | P | Unknown future/corrupt schema | Quarantine; no silent empty-state fallback or economy mutation |

## Formula golden tests

| ID | Level | Input | Expected |
|---|---|---|---|
| GF-F001 | U | 28 bronze | metal score 10; 3 sections |
| GF-F002 | U | 28 iron | 20; 4 |
| GF-F003 | U | 28 steel | 30; 4 |
| GF-F004 | U | 28 mithril | 40; 4 |
| GF-F005 | U | 28 adamant | 50; 4 |
| GF-F006 | U | 28 rune | 60; 5 |
| GF-F007 | U | 19 steel + 9 mithril | 59; 4 |
| GF-F008 | U | 18 mithril + 10 adamant | 89; 5 |
| GF-F009 | U | 19 adamant + 9 rune | 118; 6 |
| GF-F010 | U | 14 adamant + 14 rune | 130; 7; higher-tier tie order deterministic |
| GF-F011 | U/property | Every composition totaling 0..28 across six tiers | deterministic; top-two rule; score non-negative; input order irrelevant |
| GF-F012 | U | Add third metal smaller than top two | capacity includes it; score ignores it |
| GF-F013 | U | Equal amounts of three metals | two highest tiers score, lowest ignored |
| GF-F014 | U | Every mould combination/12 commissions | sum exactly three parts × two descriptor columns |
| GF-F015 | U | Best all-unlocked light-flat | score 69 |
| GF-F016 | U | q=0 | XP 30; coins 60; rep 0 |
| GF-F017 | U | q=133 | XP 13,260; coins 26,520; rep 133 |
| GF-F018 | U | q=153 | XP 16,500; coins 33,000; rep 153 |
| GF-F019 | U | q=156 | XP 17,040; coins 34,080; rep 156 |
| GF-F020 | U | q=168 | XP 19,170; coins 38,340; rep 168 |
| GF-F021 | U | q=176 | XP 20,670; coins 41,340; rep 176 |
| GF-F022 | U | q=188 | XP 23,010; coins 46,020; rep 188 |
| GF-F023 | U | q=199 | XP 25,230; coins 50,460; rep 199 |
| GF-F024 | U/property | q=0..199 | integer-floor order exact; XP/coins monotonic; no overflow |
| GF-F025 | U | quality repair/loss at cap/zero | clamp 0..max; exact penalty/repair remains blocked pending evidence |

## Eligibility, tutorial and commission

| ID | Level | Scenario | Assertion |
|---|---|---|---|
| GF-E001 | I | Smithing 14, quest start | Denied; no boosted bypass, items untouched |
| GF-E002 | I | Smithing 15 with exact quest items | First verified tutorial stage starts |
| GF-E003 | I | Missing/wrong quest item (including ball of wool) | Correct denial; no partial consumption |
| GF-E004 | I | Repeat each tutorial interaction/packet | Each checkpoint consumes/rewards once only |
| GF-E005 | P | Logout at every tutorial checkpoint | Same checkpoint resumes; no item/XP replay |
| GF-E006 | I | Complete tutorial | 1 QP + 6,000 Smithing XP exactly once; eligible state persisted |
| GF-E007 | I | Ineligible commission request | Denied; no hidden commission |
| GF-E008 | I | Eligible request | One valid pair, unique ID, timer starts at assignment |
| GF-E009 | S | 100 same-tick commission packets | One commission only; all responses reflect same ID |
| GF-E010 | I | Request while active | Existing commission preserved, never overwritten/rerolled |
| GF-E011 | I | Ask Kovac to scrap | Refusal; all state unchanged |
| GF-E012 | U/statistical | Large seeded generation sample | No invalid pairs; equal-pair hypothesis checked with disclosed tolerance (medium confidence, not exact claim) |
| GF-E013 | P/V | Store/logout/hop/restart timer vector | Timer behavior exact to resolved live reference; currently blocked/unresolved |

## Mould interface and spoof resistance

| ID | Level | Scenario | Assertion |
|---|---|---|---|
| GF-M001 | I | Open/reopen mould interface | Server selections/scores/unlocks sync identically |
| GF-M002 | I | Select every default mould | Correct slot and score update |
| GF-M003 | S | Forge locked mould button | Rejected/resynced; score and unlock unchanged |
| GF-M004 | S | Send valid mould ID in wrong slot | Rejected |
| GF-M005 | S | Replay old interface nonce/revision | Rejected, no selection rollback |
| GF-M006 | I | Close interface after selection | Selection persists; close does not confirm/reset authority |
| GF-M007 | I | Buy mould below level | Purchase/unlock allowed |
| GF-M008 | I | Attempt pour below current required level | Pour blocked; crucible intact |
| GF-M009 | I | Boost, pour, boost expires during processing | Existing preform remains finishable |
| GF-M010 | U | All Wiki best-score vectors | Default and all-unlocked scores/selections exactly match `wiki-data.json` golden data |

## Crucible and inventory transactions

| ID | Level | Scenario | Assertion |
|---|---|---|---|
| GF-C001..006 | I | One bar of each accepted tier at exact required level | Remove one; add one matching equivalent |
| GF-C007 | I | One below each tier level | Reject; inventory/crucible unchanged |
| GF-C008 | I | Valid boost above tier gate | Accept; later level drop does not undo crucible |
| GF-C009 | I | Each accepted equipment category | Whole item removed; adds 1/2/4 exact equivalents |
| GF-C010 | I | One-bar smithing product | Reject/no loss |
| GF-C011 | I | Black/white/dragon/ornamental/trimmed/unsupported metal | Reject/no loss |
| GF-C012 | I | Noted bar/equipment | Interim reject/no loss; remains blocked for live verification |
| GF-C013 | I | Capacity 27 + one bar | Total 28, no warning |
| GF-C014 | I | Capacity 28 + input | Full message, item untouched |
| GF-C015 | I | Capacity 27 + 4-equivalent item, decline overflow | Everything unchanged |
| GF-C016 | I | Same, accept bound overflow | Item consumed, total 28, exactly 3 equivalents destroyed |
| GF-C017 | S | Change item slot after overflow warning | Confirmation rejected, both new item and crucible untouched |
| GF-C018 | S | Repeat item-on-object same tick | One removal/delta only |
| GF-C019 | S | Integer min/zero/negative/huge requested quantity | Clamp/reject before transaction; no overflow/duplication |
| GF-C020 | I | Empty crucible decline/accept | Decline preserves; accept destroys all and reaches zero |
| GF-C021 | S | Stale empty confirmation after adding metal | Rejected; new contents preserved |
| GF-C022 | P | Disconnect immediately before/after mutation | Persisted snapshot is entirely old or entirely new, never item removed without metal or vice versa |
| GF-C023 | I | Fill next crucible while preform exists | Allowed; second pour remains blocked |
| GF-C024 | I | Two players load/use same world object | Independent crucible revisions and quantities |

## Pour, pickup, storage and processing

| ID | Level | Scenario | Assertion |
|---|---|---|---|
| GF-P001 | I | Pour without commission/mould/full metal | Reject, no consumption |
| GF-P002 | I | Valid pour | One animation flow; at commit consumes crucible and creates one preform |
| GF-P003 | S | Spam pour before/at/after commit | At most one preform; at most 28 equivalents consumed |
| GF-P004 | I/V | Interrupt every pour animation tick | Exact commit boundary matches parity vector; before boundary no loss |
| GF-P005 | I | Pickup with bucket / ice gloves / smiths gloves (i) | Each accepted; exact bucket/glove behavior as mapped |
| GF-P006 | I | Pickup without cooling requirement | Blocked; preform remains |
| GF-P007 | I | Pickup with two-handed gear and full inventory | Entire operation rejected; no dropped/lost gear |
| GF-P008 | I | Manual store/retrieve | Exact heat/progress/quality/section preserved; timer continues |
| GF-P009 | I | Try normal unequip/drop/destroy of preform | Denied; authoritative preform remains |
| GF-P010 | I | Machine cadence | Hammer mutation only each 5th tick; grinder/polisher only each 2nd tick |
| GF-P011 | I | Lower boundary/inside/upper boundary for each machine | Inclusive/exclusive behavior matches resolved vectors |
| GF-P012 | I | One unit below/above each band | Exact wrong-heat quality behavior; blocked until conflict resolved |
| GF-P013 | I | Wrong machine for current section | Exact single penalty timing; no client-derived state |
| GF-P014 | I | Click current machine outside sweet window | Next successful action gives no progress and no quality loss |
| GF-P015 | S | Spam same machine 20 times | Only one skip Boolean; no 20 queued jobs/penalties |
| GF-P016 | I | Claim sweet window then due action | One bonus, 2× machine XP, exact progress/quality repair once |
| GF-P017 | S | Spam sweet window | Consumed once; no duplicate bonus |
| GF-P018 | P | Logout/retrieve around sweet window | No opportunity reroll; exact resolved persistence policy |
| GF-P019 | I | Switch station one tick early/exact/late | Exact progress/quality/transition per parity vector |
| GF-P020 | I | Move before due cycle | Action stops; no partial heat/progress/XP |
| GF-P021 | I | Object transforms/despawns or route becomes stale | Job stops safely; no orphan animation/task |
| GF-P022 | I | Finish each section with overshoot-capable bonus | Clamp/transition exactly once; old token cannot mutate new section |
| GF-P023 | U/property | Random action sequence | Heat/progress/quality bounds always hold; quality never exceeds max |
| GF-P024 | I | Reach quality zero | Work blocked; hand-in path remains; 30 XP/60 coins |
| GF-P025 | I | Complete final section | Irreversible complete state; all old jobs stale |
| GF-P026 | I | Two players use same station same tick | Both process independently; no cross-player state leakage |

## Completion, shop and external reward economy

| ID | Level | Scenario | Assertion |
|---|---|---|---|
| GF-R001 | S | Forged hand-in with no/mismatched preform | Zero XP/coins/reputation; state unchanged |
| GF-R002 | I | Valid hand-in at each formula golden q | Exact XP/coins/rep/run energy; one result record |
| GF-R003 | S | Spam hand-in 100 times | One journal/award only |
| GF-R004 | P | Crash injection before mutation, between effects, after save | Recovery yields either old complete sword or one fully awarded result; never mixed/double |
| GF-R005 | I | Normal account full inventory | Bank coin route succeeds if bank accepts; no ground item |
| GF-R006 | I | UIM full inventory | Interim pending/no award/no loss; live branch unresolved |
| GF-R007 | I | Bank coin placeholder | Placeholder filled correctly |
| GF-R008 | I | Immediate next commission | New ID; old result cannot replay |
| GF-R009 | I | Insufficient reputation purchase | No item/unlock/debit |
| GF-R010 | I | Exact-cost item/mould purchase | Balance zero; item or unlock exactly once |
| GF-R011 | S | Forged reward ID/quantity/interface nonce | Rejected/no debit |
| GF-R012 | I | Full inventory purchase | If no compatible stack, fail with no debit; compatible stack may succeed |
| GF-R013 | I | Duplicate mould purchase | Reject/no debit |
| GF-R014 | I | Double ammo mould without/with Dwarf Cannon | Denied then allowed exactly as quest gate |
| GF-R015 | I | Buyback every eligible item | Exact item removal and 80% credit |
| GF-R016 | I | Buyback grog/Colossal blade/mould | Rejected |
| GF-R017 | P | Logout during purchase/buyback | Persist old or new coherent snapshot only |
| GF-R018 | I/statistical | Open ore packs with fixed seeds | 30 noted independent rolls; published empirical distribution tested only with disclosed tolerance |
| GF-R019 | I | Ore pack with insufficient space | No consume/re-roll/loss under selected pending policy; live parity pending |
| GF-R020 | I | Smiths uniform 0/1/2/3/4 pieces at anvil | 0/20/40/60/100% four-tick chance with deterministic seeds |
| GF-R021 | I | Uniform at three Foundry machines | 20/40/60/100% trigger; +5% hammer or +10% grinder/polisher exact |
| GF-R022 | I | Double ammo mould for all supported tiers | Two bars -> eight cannonballs and exact XP; quest gold vector correct |
| GF-R023 | I | Smithing catalyst normal furnace/Superheat/Blast Furnace | Exact consumption, coal and 2× XP rules; unsupported recipes unaffected |
| GF-R024 | I | Kovac's grog | +1 HP, +4 Smithing, -2 Attack/Ranged/Magic; normal boost decay |
| GF-R025 | I | Colossal blade size 1..6 | passive `2 * min(size,5)`, correct stats/speed/range and no special |

## Lifecycle, persistence, security and concurrency

| ID | Level | Scenario | Assertion |
|---|---|---|---|
| GF-L001 | P | Logout during every durable and transient state | Action cleared; exact durable state preserved; no duplication/loss |
| GF-L002 | P | Network disconnect at every inventory/reward boundary | Same coherent old/new snapshot rule |
| GF-L003 | P | Two controlled server restarts with active/stored/complete/pending states | State migrates/resumes via storage; no scheduler restored; no re-award |
| GF-L004 | I | Teleport/area exit during each processing segment | Auto-store before move; exact heat/progress/quality preserved |
| GF-L005 | I | Re-enter/login with stored preform | Documented “kept warm” message; retrieval restores exact state |
| GF-L006 | I | Forced death at each processing segment | Interim safe policy stores before drops; no preform ground item |
| GF-L007 | I | Close/reopen every interface | Authority unchanged; client fully resynchronized |
| GF-L008 | S | Forge object interaction outside region/range/wrong plane | Rejected before any mutation |
| GF-L009 | S | Interact with stale transformed object | Rejected; old job cancelled |
| GF-L010 | S | Simultaneous item, pour, shop and hand-in packets | Per-player guard/revision linearizes; one legal transition wins |
| GF-L011 | S | Reentrant callback/action during mutation | Cannot observe or mutate partial aggregate |
| GF-L012 | S | Cross-player forged commission/preform ID | Rejected; no state leak |
| GF-L013 | P | Autosave while mutation is queued | Database transaction contains coherent inventory+attrs+stats snapshot |
| GF-L014 | I | Start another skill/action while processing | Foundry action stops, no orphan lock; durable state remains |
| GF-L015 | I | Drop/destroy relevant ordinary materials after loading | Crucible unchanged; no refund/duplication |
| GF-L016 | P | Save codec from old player lacking Foundry key | Clean defaults, no migration error |
| GF-L017 | P | Corrupt non-empty save | Quarantine and alert; no empty reset/reward |
| GF-L018 | S | Run 10,000 random valid/invalid commands over two players | All 12 invariants hold; no orphan tasks/negative values/duplication |

## Differential parity scenarios

These become concrete only when `test-vectors.json` and timestamped video evidence are complete.

| ID | Scenario | Required comparison |
|---|---|---|
| GF-V001 | Complete first-time Sleeping Giants tutorial | Items, dialogue checkpoints, XP/QP, ticks, interface and region transitions |
| GF-V002 | Perfect representative 3-section commission | commission/mould/material scores, every heat/progress tick, final reward/time |
| GF-V003 | Perfect representative 7-section 14 adamant + 14 rune | metal 130, sections 7, sweet count 3 maximum, final exact outcome |
| GF-V004 | Deliberate wrong heat/wrong tool/overwork run | penalty timing/value, no-progress tick, quality/result formula |
| GF-V005 | Store/teleport/logout/retrieve mid-section | exact persistence, timer, messages and sweet behavior |
| GF-V006 | Reward shop purchase/buyback | interfaces, balance, inventory and duplicate behavior |

## Baseline and required commands

Record final commands verbatim. The initial global baseline already failed before Foundry work:

```text
.\gradlew.bat build --no-daemon --console=plain
.\gradlew.bat test --no-daemon --console=plain
```

The build failure was pre-existing Spotless debt in `api:music-plugin`, `api:game-process`,
`api:invtx`, and `api:music`. The test failure was pre-existing `api:player` `BlowpipeAmmoTest`
(`ArrayIndexOutOfBoundsException`, line 55) plus `or-cache` `PohInterfaceAuditTest` and
`PohViewerGamevalAuditTest` (`ArrayIndexOutOfBoundsException`, line 13). Final reporting must:

1. run the dedicated Foundry unit/integration suites and require them to pass;
2. run formatting/compile for only changed modules;
3. rerun global build/test and diff failures against this baseline;
4. run `scripts/openrune-doctor.ps1`, confirm `OPENRUNE CUSTOM BUILD ACTIVE`, then perform two
   controlled restarts as required by `AGENTS.md`;
5. never label an unresolved differential row “pass” merely because compilation succeeded.
