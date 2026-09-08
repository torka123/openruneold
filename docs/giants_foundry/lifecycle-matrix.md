# Giants' Foundry lifecycle and transition matrix

Research pin: **2026-08-27**. This matrix is normative for safety and idempotency. `Preserve` means
the durable state survives unchanged; `stop` means the transient action/animation is cancelled.
Where live OSRS evidence is missing, the row states the conservative behavior and confidence.

## Transition result vocabulary

- **Commit**: one validated, revision-checked state change; repeat packets return the committed view.
- **Reject**: no state/economy mutation; optionally send one message and resync.
- **Stop**: invalidate action token, clear animation/facing, no partial cycle.
- **Store**: atomically remove the equipped preform projection and persist its exact heat, progress,
  quality, section, commission, sweet-plan consumption, and revision.
- **Reconcile**: decode/migrate state, resolve a pending operation by identity, enforce invariants,
  then expose UI.

## Player-command transitions

| Command/event | Allowed from | Guards | Commit/result | Illegal/stale handling |
|---|---|---|---|---|
| Begin Sleeping Giants | `NOT_INTRODUCED` | 15 Smithing unboosted and verified quest requirements | Persist first tutorial checkpoint | Reject; no bypass |
| Advance tutorial | matching `TUTORIAL_IN_PROGRESS` checkpoint | exact object/NPC/item/region and inventory snapshot | Consume/advance exactly once | Resync checkpoint; no reward/XP replay |
| Finish tutorial | final tutorial checkpoint | all required checkpoints | Set quest complete/eligible, grant quest reward once | Reject duplicate completion |
| Request commission | `ELIGIBLE_IDLE` | quest complete, no commission/preform/pending op | Lock, choose valid pair, persist ID/timer, unlock | Existing commission is repeated, not replaced |
| Ask to scrap | any active commission/preform state | none | Dialogue refusal; state unchanged | Same result for forged/rapid clicks |
| Open mould UI | active commission | valid jig/region | Project selections/unlocks/scores from server | Deny if ineligible/no commission |
| Select mould | mould UI open | interface nonce, slot, logical ID, default/unlocked, current selection revision | Replace exactly one slot; increment revision | Close/resync forged locked/wrong-slot/stale click |
| Close mould UI | any | interface open | Clear transient nonce only | No-op |
| Add valid bar/equipment | any eligible state where crucible may be loaded | exact slot/item/vars/count; current Smithing; capacity/revision | Inventory delete + crucible delta, no yield | Reject unsupported/notes/level/stale item |
| Confirm overflow | prior bound overflow warning | all snapshot fields still match | Consume intended input; fill remaining capacity; destroy excess | Reject stale confirmation, consume nothing |
| Empty crucible | non-empty crucible | revision-bound confirmation | Destroy all contents; increment revision | Empty/stale is no-op |
| Pour | `READY_TO_POUR` | full validated metal; valid moulds/levels; no preform/pending op | Enter `POURING` with bound snapshot | Reject repeated/stale/forged object |
| Pour completes | matching `POURING` token | unchanged commission/mould/crucible revisions and location | Consume crucible + create preform atomically | Stale token stops; no partial loss |
| Interrupt pour before commit | `POURING` | movement/new protected action/logout | Stop and return ready; contents preserved | No-op after commit |
| Pick up preform | `PREFORM_CREATED` or stored retrieval | cooling requirement; equipment projection can be made atomically | Equip projection, mark active | If inventory cannot receive displaced gear, reject everything |
| Store preform manually | active/complete preform | storage object/reachable | Stop + store exact state | No preform: message/no-op |
| Retrieve stored preform | `SUSPENDED_STORED` | storage object; equipment transaction succeeds | Equip exact saved projection/state | Full inventory/gear conflict: preserve stored |
| Start heat/cool | active processing | correct source object, position, token-free | Start one runtime action | Wrong state/object: reject |
| Start workstation | active processing | required machine/object/position | Start one runtime action | Wrong section may trigger verified mistake only on due action, never on click packet alone |
| Reclick current machine in sweet window | using same station | live unclaimed window and nonce | Consume window, arm one bonus | Duplicate click no-op |
| Reclick current machine otherwise | using same station | not already skip-armed | Arm one `skipNextSuccessfulAction` | Packet spam cannot stack skips |
| Due action cycle | matching runtime token/revision | player/object/position/preform/section still valid | One deterministic reducer tick | Any guard failure stops before mutation |
| Move/new protected action | any runtime action | route/action change | Stop before next cycle; durable state preserved | No partial cycle |
| Finish final section | processing | exact reducer threshold | Clamp, mark complete, stop | Later job tokens stale/no-op |
| Work at quality zero | ruined complete state | none | Reject work; direct to hand-in | Cannot lower below zero |
| Hand in | ready/ruined, exact commission-bound preform | Kovac/reachability; no pending op | Create immutable pending journal, atomically award, consume state | Forged/repeated click cannot award twice |
| Reopen result UI | awarded result available | display only | Show committed values | Never grants rewards |
| Buy reward item | reward UI | PIN, interface nonce, valid definition, balance, inventory capacity | Atomic add + reputation debit + collection update | Any failure leaves all unchanged |
| Buy mould unlock | reward UI | PIN, valid locked mould, balance | Atomic debit + permanent bit | Already unlocked/stale packet: reject, no debit |
| Sell eligible reward to Kovac | valid inventory item | exact item/slot/quantity; eligible buyback | Atomic remove + reputation credit | Excluded/forged/stale item: reject |

## External lifecycle matrix

| Event | No preform | Pouring | Equipped/processing | Stored | Complete/ruined | Pending reward | Evidence/confidence |
|---|---|---|---|---|---|---|---|
| Close mould/shop/dialogue UI | Preserve | Preserve | Preserve/action continues only if UI-independent | Preserve | Preserve | Preserve | High for authoritative state; UI is never authority |
| Close HUD | Preserve | Preserve | Preserve; resync when reopened | Preserve | Preserve | Preserve | Security invariant; exact client behavior cache-dependent |
| Walk within area | Preserve | Stop pour before commit if route interrupts | Stop current cycle on movement | Preserve | Stop transient action | Preserve | Standard action semantics; exact pour interruption pending video |
| Walk out of cave/area | Preserve commission/crucible | Stop then preserve crucible | **Store** exact preform | Preserve stored | Store complete projection | Finish/reconcile before leave | Auto-store high confidence |
| Teleport | Same | Stop then preserve | **Store** exact preform before teleport | Preserve | Store | Finish/reconcile before teleport | Auto-store high confidence |
| Normal logout | Preserve | Stop before save | **Store**, clear action | Preserve | Store | Reconcile/save | Login reminder high; exact save tick medium |
| Network disconnect | Preserve | Stop before save | **Store**, clear action | Preserve | Store | Reconcile/save | Treat as logout; safe behavior |
| World hop | Preserve | Stop | **Store** | Preserve | Store | Reconcile/save | Live semantics unresolved; conservative anti-dupe choice |
| Login | Decode/migrate | Never resume pouring; return ready if snapshot valid | Convert any legacy equipped state to stored; reminder | Reminder; no action restored | Stored/ready hand-in | Reconcile by transaction ID | Storage reminder high; detailed ordering medium |
| Controlled shutdown/restart | Persist | Persist pre-commit source state, never action | Persist as stored | Preserve | Preserve | Reconcile | OSRS restart semantics unresolved; OpenRune-safe choice |
| Forced/unexpected process crash | Last database-atomic snapshot | Either pre-pour or post-pour coherent snapshot | Last coherent stored/active snapshot; login forces storage | Preserve | Preserve | Reconcile journal | Engine guarantee depends on account saver; crash injection test required |
| Forced player death | Preserve | Stop, no consumption if pre-commit | **Store before death drops; never drop preform** | Preserve | Store | Reconcile | Live behavior unresolved; activity is safe; security divergence |
| Player reset/admin reset | Deny while op pending; explicit audited recovery | Same | Store then audited reset only | Audited reset only | Audited reset only | Must reconcile first | Administrative policy, not player cancellation |
| Interface spoof/foreign button | Reject | Reject | Reject | Reject | Reject | Reject | Server-authoritative requirement |
| Two packets same tick | First valid transition wins revision | Same | Reclick rule or dedupe | First wins | First wins | One journal only | Security invariant |
| Two players same station | Independent state | Independent | Both allowed; no shared progress | Independent | Independent | Independent | Shared activity/per-player state; contention details medium |

## Inventory and equipment boundary matrix

| Situation | Expected transaction behavior | Failure behavior |
|---|---|---|
| Exact crucible capacity | Delete exact input and reach 28 | None |
| Input exceeds remaining capacity | Require bound warning; on accept consume input and destroy verified excess | Decline/stale warning consumes nothing |
| Unsupported or one-bar product | Reject before transaction | Item remains |
| Noted bar/equipment | Reject pending live verification | Note remains; mark parity unresolved |
| Slot contents changed during dialogue | Snapshot mismatch | Nothing consumed |
| Pickup with empty hands/space | Equip authoritative preform projection | — |
| Pickup with weapon/shield and enough inventory room | Atomically relocate displaced equipment and equip | — |
| Pickup with no room for displaced equipment | Reject entire pickup | Stored/created preform remains |
| Manual storage | Atomically remove only matching preform projection and mark stored | Mismatch quarantines; never delete arbitrary worn item |
| Normal hand-in, full inventory | Coins go bank, so inventory fullness does not block | Bank transaction failure keeps pending/no award |
| UIM hand-in, full inventory | Conservative: keep pending and award nothing | Retry after space; exact live branch unresolved |
| Shop purchase into full inventory with compatible stack | Allow if transaction simulation succeeds | No reputation debit on add failure |
| Ore pack open with insufficient result capacity | Roll once into pending immutable reward, add all atomically when space exists | Do not consume/re-roll pack on failure; exact live branch must be checked |

## Runtime-action cancellation table

| Cause | Cancel timing | Durable effect |
|---|---|---|
| Movement/route replacement | Before next due cycle | No partial heat/progress/XP |
| Different station click | Invalidate old token before validating new | Old cycle cannot run |
| Same station click | Does not create second job | Sweet claim or one Boolean overwork skip |
| Interface close | Only if interface itself owns the protected action | No state reset |
| Wrong object transform/stale instance | Before reducer | Stop, resync |
| Section changes | Old token invalidated in transition reducer | Must explicitly start/continue as evidence specifies |
| Quality reaches zero | Same atomic reducer | Stop permanently; hand-in only |
| Final section completes | Same atomic reducer | Complete; all scheduled callbacks become stale |
| Logout/death/teleport/area exit | Before save/death/teleport side effects | Auto-store exact state |
| Server shutdown | Shutdown hook invalidates all tokens | Only durable aggregate saved |

## Recovery and corruption matrix

| Loaded condition | Recovery action | Reason |
|---|---|---|
| Valid known schema | Enforce invariants, normalize transient state, sync | Normal path |
| Older known schema | Pure tested migration, then save V1 | Compatibility |
| Future schema | Quarantine and deny mutation | Downgrade must not erase value |
| Malformed non-empty codec | Quarantine, structured alert, no default-empty fallback | Prevent duplicated/lost preform/rewards |
| Preform without commission | Quarantine | Cannot safely value/hand in |
| Commission IDs disagree | Quarantine | Possible stale/duplicated state |
| Crucible total > 28 or negative | Quarantine | Corrupt/economy-invalid |
| Equipped projection but save says stored | Remove projection only if identity matches; retain stored | Avoid duplicate representation |
| Save says active/equipped after login | Force stored and send reminder | Never resume stale job |
| Pending hand-in with unapplied ID | Reconcile full saved snapshot; apply only if commission/preform still present | Exactly once |
| Applied transaction ID but stale result UI | Show result only | Never pay twice |

## Unresolved parity decisions

| Topic | Current evidence | Required focused test | Interim safety choice |
|---|---|---|---|
| Offline/world-hop/server-down timer | storage timer continues; lifecycle details unpublished | Assign, store, log/hop/restart, compare timer | Continue absolute elapsed time |
| Forced death | activity safe; forced death unpublished | Controlled live death if possible | Store, never drop |
| Full inventory pickup/hand-in | partial evidence only | Fill inventory and exercise every branch | Fail atomically/preserve state |
| Noted inputs | unpublished | Use noted bars/equipment on live crucible | Reject |
| Unique duplicate purchases | infinite stock but gate unclear | Buy each unique twice | Data-gate; no invented rule |
| Sweet window across logout/storage | unpublished | Trigger, store/logout, retrieve | Consume/expire to prevent reroll |
| Pour interruption commit tick | unpublished | Move/teleport on each animation tick | Commit only at verified completion tick |
| Ore-pack full inventory | unpublished | Open at 0/1/several free slots and existing stacks | Pending immutable roll, no consume/re-roll |
