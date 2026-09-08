# Giants' Foundry test plan

Research date: 2026-08-28
Oracle inputs: `evidence-ledger.md`, `parity-specification.md`, and `test-vectors.json`.

## Test principles

1. The test oracle must not call the production calculator it is validating.
2. Time-dependent tests use a deterministic game-tick scheduler; no wall-clock sleeps.
3. Random tests use an injected, recorded seed.
4. Every item/point/reward operation asserts both sides of the transaction and the persisted result.
5. Client presentation tests use revision-240 interfaces and scripts. RuneLite overlays are not expected.
6. Any unresolved evidence row remains a pending parity test, not a guessed passing assertion.
7. A build or server boot alone is not gameplay verification.

## Test layers

### 1. Static data validation

- Every accepted metal item resolves to one and only one tier.
- Every supported equipment variant resolves to the explicit contribution in `test-vectors.json`.
- Ornamental, one-bar, unsupported-metal, noted, and malformed variants cannot enter the crucible.
- Every mould has a unique identity, level, ownership rule, and complete descriptor-affinity data.
- All required NPC/object/item/interface identifiers resolve in revision 240.
- Reward prices and buyback values match the pinned shop revision.
- No plugin-only sprites/widgets are required by the server implementation.

### 2. Pure formula tests

#### Material score

- Run every `material_score_vectors` row.
- Generate all accepted-metal partitions summing to 28 and compare production output with a separate rational-arithmetic oracle.
- Permute input loading order; score and selected top two must not change.
- For equal amounts, verify higher-tier tie preference.
- Add a third metal and verify it occupies the denominator while being excluded from the two score terms.
- Verify every difficulty boundary vector.

#### Quality and rewards

- Run every initial-quality cap vector.
- Exhaustively evaluate successful hand-in quality 1 through 199 against:

  ```text
  (floor(q*q/73) + floor(3*q/2) + 1) * 30
  ```

- Assert coins are exactly twice hand-in XP and reputation exactly equals the supplied final quality.
- Keep processing XP out of the hand-in calculation.
- Verify zero-quality failure uses the fixed 30 XP/60 coin path.

### 3. State-machine tests

Test every legal transition and reject every illegal transition in the specification.

Minimum cases:

- assignment with no prior commission;
- duplicate assignment click;
- mould select/change/cancel before pour;
- locked mould and insufficient Smithing level;
- load to 27, exactly 28, and overflow with each contribution size;
- empty crucible and attempt to pour;
- pour without commission/mould or with existing preform;
- hot-preform pickup with bucket, ice gloves, smiths gloves (i), and no protection;
- station click for the wrong section and right section;
- complete section, final section, and sword completion;
- store/restore from every valid processing section;
- fail to zero quality;
- hand in success/failure twice;
- request a new commission immediately after hand-in.

For every rejection, assert inventory, crucible, sword, points, XP, and world-object state are unchanged.

### 4. Tick/action tests

Use a fake tick source and capture all emitted state deltas.

| Station | Assertions |
|---|---|
| Trip hammer | No action through tick 4; one action on tick 5; next on tick 10; correct base progress/heat/XP |
| Grindstone | No action on tick 1; one on tick 2; next on tick 4; correct base progress/heat/XP |
| Polishing wheel | No action on tick 1; one on tick 2; next on tick 4; correct base progress/heat/XP |

Also test:

- movement, teleport, death, storage, logout, and section completion cancel the scheduled action;
- repeated click outside the bonus window skips exactly one next action and loses no quality;
- repeated clicks cannot queue multiple XP/progress events on the same tick;
- a stale task from an earlier preform identity cannot mutate a newer sword;
- fractional Smithing XP goes through the shared accumulator and is neither truncated per action nor double-counted;
- heat/progress/quality are clamped at their legal bounds.

Exact heat-station curves and temperature thresholds remain pending until `OBS-HEAT-CURVES` and `OBS-TEMPERATURE-BANDS` are measured.

### 5. Bonus-action and uniform tests

Pending observation must first record prompt start/end, click, next action, progress delta, and quality recovery on current OSRS. Once resolved:

- prompt opens only after an eligible action;
- click before, during, on the final tick, and one tick after expiry;
- accepted click changes the appropriate interface state exactly once;
- result is consumed on the next legal machine action;
- wrong tool/temperature cannot exploit the stored bonus;
- a player cannot click another player's prompt;
- three opportunities occur for six/seven sections, two otherwise;
- storage/logout cannot duplicate a pending bonus;
- lost quality cannot be recovered above the allowed ceiling.

For uniform behaviour:

- 0/1/2/3/4 pieces use probabilities 0/20/40/60/100 percent;
- seeded exact-roll boundary tests cover failure/success;
- four pieces always proc;
- trip hammer proc applies its documented 5% progress bonus;
- grindstone/polish proc applies the documented 10% bonus;
- resolve and test ordering when a uniform proc and bonus action coincide.

Statistical tests may detect gross distribution errors over many trials, but seeded boundary tests are the authoritative regression tests.

### 6. Persistence and recovery tests

Serialize and restore each state:

- assigned commission before mould selection;
- partially loaded crucible;
- ready to pour;
- hot preform;
- every processing section with fractional progress/heat;
- pending/consumed bonus state;
- stored preform;
- completed but unclaimed sword;
- reward-shop balance and unlocked moulds;
- PB quality/time.

For each state:

1. save;
2. restart the content/service boundary;
3. advance offline time;
4. reconnect;
5. assert semantic equality and absence of duplicate scheduled tasks.

Storage freezes progress/heat but not the commission timer. Active machinery must not keep processing while offline. A crash between reward debit/credit operations must recover to exactly one committed outcome.

### 7. Interface and packet tests

- Open every activity interface from the correct NPC/object and verify the expected native revision-240 interface.
- Verify commission descriptors, mould score/order/locks, crucible amount, quality, heat, progress, current tool, bonus prompt, hand-in summary, PB, and shop balance.
- Click every valid component and validate the server-side state transition.
- Send wrong component IDs, stale interface IDs, negative/large X amounts, duplicate packets, and packets from outside the interaction area; all must fail safely.
- Reopen/close interfaces during an active action; no action lock or typing lock may remain.
- Confirm a RuneLite plugin is unnecessary for core gameplay and its overlay cannot alter server results.

### 8. Economy and concurrency tests

- Two players load, pour, process, store, and hand in concurrently on shared-looking machinery with isolated state.
- Two simultaneous clicks on the same player's hand-in produce one reward.
- Two simultaneous shop purchase/buyback packets produce one consistent balance/item result per accepted transaction.
- Full inventory and full bank conditions preserve value or reject before debit.
- Ultimate Iron rewards route to inventory; normal-account coins route to bank.
- Reward points never underflow/overflow.
- Mould purchases persist and cannot be sold through an unsupported generic-shop path.
- Failed sword materials are never refunded.

### 9. Reward integration tests

- Double ammo mould: Dwarf Cannon gate, exactly two steel bars consumed, eight cannonballs produced, inventory capacity, interruption.
- Smithing catalyst: normal furnace coal halving and double XP; Blast Furnace double XP without extra coal reduction; odd resource boundaries.
- Kovac's grog: +4 visible Smithing, -2 Attack/Ranged/Magic, stat floors/caps, restoration.
- Smiths' gloves (i): valid hot-preform collection and correct shared-item behaviour.
- Uniform: Foundry progress proc and normal-anvil 5-to-4 tick integration.

### 10. Black-box gameplay scenarios

#### Scenario A - standard end-to-end commission

1. Request a commission.
2. Select the best owned usable mould.
3. Load a known 28-equivalent alloy from a golden vector.
4. Pour and collect using each valid protection method in separate runs.
5. Complete every section with correct tool/temperature.
6. Store and restore once mid-sword.
7. Hand in and verify XP, coins, points, run energy, summary, PB, and state cleanup.
8. Immediately request another commission.

#### Scenario B - controlled failure/mistakes

1. Create a fixed-quality sword.
2. Record quality.
3. Apply exactly one wrong-tool event and record the delta.
4. Repeat with one wrong-temperature event.
5. Overwork the correct station outside a bonus window and prove quality is unchanged while one action is skipped.
6. Miss and hit a bonus prompt with readable values.
7. Continue to zero quality and verify the fixed failure reward.

Scenario B is the mandatory live resolution for ledger rows GF-018 and GF-021.

#### Scenario C - video differential

Reproduce the ordering from VID-05 commission A/B and VID-04 current lifecycle. Compare:

- interaction order;
- interface changes;
- inventory deltas;
- animation start/stop points;
- heat/progress/quality changes;
- hand-in result and cleanup.

Do not compare exact elapsed time against sped-up or edited footage. Exact tick comparisons begin only after a frame-verified current recording is acquired.

#### Scenario D - reward shop

With an exact known balance, buy and buy back every eligible reward, try both no-buyback items, test the quest-gated mould, buy a mould below use level, then confirm it remains unselectable until the level requirement is met.

## Coverage matrix

| Ledger rows | Primary test suites |
|---|---|
| GF-001-GF-003 | access, assignment, mould interface/state |
| GF-004-GF-011 | crucible conversion, scoring, pour, pickup |
| GF-012-GF-019 | deterministic tick engine, heat/tool/bonus/uniform |
| GF-020-GF-026 | quality, failure, persistence, rewards, PB |
| GF-027-GF-032 | shop, economy, and reward integrations |

## Planned automation fixtures

- `FakeGameClock`: advances one game tick and drains scheduled jobs deterministically.
- `FixedRng`: queues exact uniform/bonus-selection rolls and records consumption order.
- `FoundryPlayerFixture`: isolated inventory, bank, stats, quest state, location, persistence record, and packet sink.
- `FoundryWorldFixture`: revision-240 objects/NPCs with clipping and interaction reach checks.
- `FoundryOracle`: test-only rational material-score and reward calculators implemented independently from production.
- `EventTrace`: ordered log of state mutations, inventory operations, XP/points, animations, graphics, sounds, interfaces, and scheduled-task lifecycle.

Fixture names are design names, not claims that these classes already exist.

## Build and runtime gates

After each major implementation slice:

1. compile the affected content module;
2. run its unit/property/state-machine suite;
3. run the broader server tests affected by shared inventory, Smithing, persistence, or shop APIs;
4. boot revision 240 cleanly;
5. connect through the normal RSProx flow;
6. execute the corresponding black-box scenario;
7. inspect server/client logs for exceptions, stale tasks, invalid IDs, and duplicate rewards.

## Completion gates

Giants' Foundry must not be reported as 1:1 until:

- all deterministic golden vectors pass;
- every legal state transition and rejection path passes;
- two current full normal commissions pass from assignment through hand-in;
- current mistake, bonus recovery, heat curves, and temperature boundaries are measured and locked into tests;
- storage survives logout and server restart without changing progress/heat;
- concurrent player and duplicate-packet tests show no state or value leak;
- the full reward-shop purchase/buyback matrix passes;
- all revision-240 asset/interface IDs are verified;
- the project compiles, server boots, RSProx connects, and no related exception remains;
- every unresolved row in the evidence ledger is either resolved and tested or explicitly accepted as a documented parity gap by the project owner.
