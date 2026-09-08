# Giants' Foundry parity specification

Version anchor: OSRS evidence available on 2026-08-28.
Normative evidence: `evidence-ledger.md`. Video evidence supplies observable workflows but does not override current official/Wiki mechanics.

## 1. Scope

This specification describes the complete player-owned Giants' Foundry commission lifecycle, its deterministic calculations, processing state, persistence, rewards, and related reward-shop transactions. It does not define RuneLite overlays. It also does not invent exact values for unresolved live mechanics listed in section 12.

## 2. Player state model

Each player owns at most one commission/preform state. World machinery may be shared visually, but one player's inputs, progress, heat, quality, bonus state, and rewards must never be readable or mutable by another player.

```text
NO_COMMISSION
  -> COMMISSION_ASSIGNED
  -> MOULD_SELECTED
  -> CRUCIBLE_LOADING
  -> READY_TO_POUR
  -> POURED_HOT_PREFORM
  -> PROCESSING
       <-> STORED
       -> FAILED
       -> COMPLETED
  -> HANDED_IN
  -> NO_COMMISSION or COMMISSION_ASSIGNED
```

Required transition invariants:

- assignment fixes the two commission descriptors and starts the completion timer;
- changing a mould before pouring recalculates only the mould contribution;
- all crucible mutations are atomic inventory-to-state transactions;
- pouring is legal only at exactly 28 bar-equivalents and when the player has no other poured/unfinished preform;
- collection is legal only with one of the documented heat-protection paths;
- only the tool required by the current section can legally progress that section;
- a completed or failed sword cannot be processed again;
- hand-in applies rewards once, clears the active sword once, and makes a new commission immediately available;
- logout, region change, duplicate clicks, or reconnect must not replay material consumption, hand-in, shop purchase, or storage transitions.

## 3. Commission and mould model

A commission contains two descriptors. Every mould has:

- a stable identity;
- a Smithing level requirement;
- ownership/unlock state;
- affinity values for the descriptor dimensions used by the activity.

For a legal mould, `mouldScore` is the sum of the two values corresponding to the assigned descriptors. The selector lists the best usable matches first. A purchased mould may remain unusable until the Smithing requirement is met.

Selecting or replacing the mould before pouring must be idempotent and must not change crucible contents. The mould selection committed at pour becomes part of the preform snapshot.

## 4. Crucible and material conversion

### 4.1 Capacity and accepted metals

The crucible capacity is exactly 28 bar-equivalents. Accepted metals and tier values are:

| Metal | Tier |
|---|---:|
| Bronze | 1 |
| Iron | 2 |
| Steel | 3 |
| Mithril | 4 |
| Adamant | 5 |
| Rune | 6 |

Unsupported metals are rejected without mutating inventory or crucible state.

### 4.2 Equipment conversion

Eligible, non-ornamental, smithable equipment contributes one fewer equivalent than the number of bars normally used to smith it.

| Contribution | Eligible equipment families |
|---:|---|
| 1 | scimitar, longsword, full helm, square shield, claws |
| 2 | warhammer, battleaxe, chainbody, kiteshield, two-handed sword, platelegs, plateskirt |
| 4 | platebody |

Equipment that is merely metal-looking, ornamented, non-smithable, or outside the accepted metal set is rejected.

### 4.3 Atomic loading

For a selected stack/item:

1. resolve its accepted metal and contribution;
2. compute remaining capacity;
3. validate that at least one equivalent can be credited;
4. commit inventory removal and credited equivalents in one transaction;
5. if the item's contribution exceeds capacity, destroy the excess according to current OSRS behaviour;
6. persist the new crucible state.

Emptying the crucible clears and destroys all loaded contents. There is no refund path.

## 5. Material score and difficulty

Let the two most abundant metal groups be `m1` and `m2`; ties are ordered by higher tier first. Metals below the top two are ignored in the direct score terms but remain part of the fixed 28-equivalent denominator.

For each selected group:

```text
Vn = 10 * tier_n * amount_n / 28
```

Then:

```text
materialScore = floor(
    (floor(10 * V1) + floor(10 * V2) + floor(V1 * V2)) / 10
)
```

The implementation must preserve this order of floors. Use integer/rational arithmetic or an equivalent exact representation; do not use a binary floating-point calculation followed by one final floor.

Difficulty is derived from the material score:

| Material score | Sections |
|---:|---:|
| 10-19 | 3 |
| 20-59 | 4 |
| 60-89 | 5 |
| 90-119 | 6 |
| 120-130 | 7 |

Initial quality is:

```text
initialQuality = min(199, materialScore + mouldScore)
```

## 6. Pouring and collection

Pour is available only when:

- an active commission and selected legal mould exist;
- the crucible contains exactly 28 equivalents;
- the player has no existing poured/unfinished preform;
- no conflicting player action is in progress.

The pour transaction snapshots the commission, selected mould, material score, section plan, initial quality, and timer identity. It then creates one hot preform.

Collection succeeds only with a bucket of water, equipped ice gloves, or smiths gloves (i). Consumption/equipment behaviour must use the shared item systems. A failed eligibility check leaves the hot preform intact.

## 7. Processing engine

### 7.1 Per-player deterministic clock

Processing is scheduled in game ticks. Each operation records:

- active station;
- action start tick;
- next machine-action tick;
- current section and section progress;
- heat;
- quality and initial-quality ceiling;
- bonus prompt state and expiry tick;
- overwork skip flag;
- equipment snapshot needed for a uniform roll;
- deterministic RNG state where applicable.

Moving away, interruption, logout, completion, failure, storage, or changing to an incompatible state cancels the scheduled operation exactly once.

### 7.2 Base machine actions

| Station | Action interval | Base progress | Heat change | Processing XP |
|---|---:|---:|---:|---:|
| Trip hammer | every 5 ticks | +2 percentage points | -2.5 percentage points | 4.5 |
| Grindstone | every 2 ticks | +1 point | +1.5 points | 3.2 |
| Polishing wheel | every 2 ticks | +1 point | -1.7 points | 3.3 |

At each scheduled machine tick:

1. confirm the player/preform/action are still valid;
2. if an overwork skip is armed, consume the flag and perform no machine action;
3. validate required section tool and current temperature;
4. if invalid, apply the verified quality-damage rule and stop/continue exactly as observed;
5. if valid, compute base progress and heat;
6. apply a consumed bonus action, if armed for this tick;
7. apply a smiths' uniform proc, if eligible;
8. add processing XP through the shared fractional-XP mechanism;
9. clamp legal heat/progress/quality bounds;
10. advance the section or complete the sword as appropriate;
11. schedule the next machine tick only if the operation remains active.

The relative ordering of bonus and uniform progress must be verified where it changes rounding. Until observed, the implementation and tests must expose the intermediate values rather than hide the choice.

### 7.3 Overwork and bonus actions

Clicking an already running station outside a live bonus prompt arms one skipped machine action and causes no quality damage.

A bonus prompt:

- begins immediately after an eligible machine action;
- remains available for approximately ten ticks/six seconds;
- accepts at most one click;
- marks the interface border green on success;
- applies on the next legal machine action;
- requires the correct station and temperature;
- adds approximately 5% progress and recovers a currently unresolved unit/portion of lost quality.

The approximate duration, progress, and recovery amount are research blockers, not license to hard-code guesses. Six- and seven-section swords have three opportunities; other difficulties have two.

### 7.4 Heat-changing stations

The waterfall and lava pool are the slow cooling/heating methods. Quench and dunk are the faster methods. All are described as changing at increasing rates. Exact curves, bounds, animation intervals, and temperature-band endpoints require target/live evidence before parity sign-off.

## 8. Storage and persistence

Storing a preform freezes heat and progress, cancels active station tasks, and serializes the entire preform snapshot. It does not pause the commission timer. Leaving, logout, server restart, and reconnect must not mutate stored heat/progress/quality.

Restoring a preform must recreate only the owner's state and show the appropriate reminder. A stored or active preform blocks pouring another one.

## 9. Failure and hand-in rewards

If quality reaches zero, processing stops and the sword enters `FAILED`. The failure reward is exactly 30 Smithing XP and 60 coins, independent of materials and mould.

For a successful final quality `q`:

```text
handInXp = (floor(q*q / 73) + floor(3*q / 2) + 1) * 30
coins = 2 * handInXp
reputation = q
```

The integer expression `floor(3*q/2)` is equivalent to `floor(1.5*q)` and avoids floating-point error. Processing XP is separate and must not be included in `handInXp`.

Hand-in also restores 50 percentage points of run energy subject to the shared cap, reports quality/time, and updates the personal best using:

1. higher final quality wins;
2. at equal quality, shorter elapsed time wins.

Coins go to the bank for normal accounts and inventory for Ultimate Iron accounts. Reward credit, PB update, sword clearing, and completion messaging form one idempotent transaction.

## 10. Reward shop and unlock integrations

| Reward | Buy | Buyback |
|---|---:|---:|
| Double ammo mould | 2,000 | 1,600 |
| Kovac's grog | 300 | unavailable |
| Smithing catalyst | 15 | 12 |
| Ore pack | 200 | 160 |
| Smiths' tunic | 4,000 | 3,200 |
| Smiths' trousers | 4,000 | 3,200 |
| Smiths' boots | 3,500 | 2,800 |
| Smiths' gloves | 3,500 | 2,800 |
| Colossal blade | 5,000 | unavailable |

The shop has infinite stock. Point debit and item credit are atomic. Buyback is 80% only where the table supplies a value. The double ammo mould is subject to the Dwarf Cannon requirement. Mould purchases below the use level are allowed, but selection remains level-gated.

The reward integrations must use shared skill/item APIs:

- four uniform pieces make the documented processing proc certain; fewer pieces each contribute their documented 20% roll;
- double ammo mould consumes two steel bars and makes eight cannonballs;
- catalyst applies its normal-furnace/Blast-Furnace coal and XP rules;
- grog boosts visible Smithing by 4 and drains Attack, Ranged, and Magic by 2.

## 11. Interface and interaction requirements

The server must drive native revision-240 interfaces and scripts. Plugin overlays visible in VID-03 are explicitly non-normative.

At minimum, interfaces/interactions must expose:

- assigned descriptors;
- usable/locked moulds and affinity score;
- crucible capacity/content and X-input memory where supported by revision 240;
- section plan, temperature, progress, quality, correct station, and bonus prompt;
- final quality, elapsed time, PB comparison, reward totals;
- reward shop balance, purchase, and valid buyback;
- persistent-preform reminder.

All menu options require server-side validation. A spoofed widget/component, stale selection, duplicate packet, or interaction from the wrong location must not grant items, points, progress, or rewards.

## 12. Known unresolved parity points

These values must be measured rather than guessed:

- exact quality loss per wrong-tool and wrong-temperature event, because the current Wiki contains `-10 per damage` and `-5 per mistake` statements;
- exact temperature-band endpoints and inclusive/exclusive comparisons;
- exact accelerating heat/cooling curves;
- exact bonus prompt lifetime, progress increment, quality recovery amount, and distribution algorithm after 2025-05-07;
- exact ordering/rounding if bonus action and uniform proc occur together;
- exact visual varbits, component IDs, animation IDs, graphics, sounds, and client scripts in revision 240;
- exact bucket consumption/result and all message strings where not already captured from cache/live play.

An implementation is not “1:1” until these rows are resolved in `evidence-ledger.md` and their tests change from pending to passing.
