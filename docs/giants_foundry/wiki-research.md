# Giants' Foundry — OSRS Wiki mechanics research

Research pin: **2026-08-27**. This document records live-game behaviour for an OpenRune implementation; it is not an implementation status report. Numeric claims use revision-pinned OSRS Wiki pages where possible. Wiki descriptions that are approximate, internally inconsistent, or silent are called out rather than converted into invented constants.

## Source ledger

The primary mechanics source is [Giants' Foundry, revision 15307204](https://oldschool.runescape.wiki/w/Giants%27_Foundry?oldid=15307204) (2026-08-19). Supporting sources are:

| Page | Revision | Revision timestamp | Use |
|---|---:|---|---|
| [Giants' Foundry](https://oldschool.runescape.wiki/w/Giants%27_Foundry?oldid=15307204) | 15307204 | 2026-08-19 16:12 UTC | Core gameplay, formulas, moulds, rewards, changes |
| [Strategies](https://oldschool.runescape.wiki/w/Giants%27_Foundry/Strategies?oldid=15275840) | 15275840 | 2026-07-26 12:57 UTC | Machine/temperature behaviour, alloy practice |
| [Reward Shop](https://oldschool.runescape.wiki/w/Giants%27_Foundry_Reward_Shop?oldid=15318598) | 15318598 | 2026-08-25 01:43 UTC | Prices, stock and buyback |
| [Mould List](https://oldschool.runescape.wiki/w/Giants%27_Foundry/Mould_List?oldid=14715973) | 14715973 | 2024-08-10 22:44 UTC | Mould catalogue cross-check |
| [Foundry Reputation](https://oldschool.runescape.wiki/w/Foundry_Reputation?oldid=14974229) | 14974229 | 2025-08-26 07:10 UTC | Currency semantics |
| [Preform](https://oldschool.runescape.wiki/w/Preform?oldid=15190927) | 15190927 | 2026-04-22 04:40 UTC | Equipment and disposal constraints |
| [Preform storage](https://oldschool.runescape.wiki/w/Preform_storage?oldid=14998309) | 14998309 | 2025-10-03 05:49 UTC | Persistence and timer |
| [Transcript:Kovac](https://oldschool.runescape.wiki/w/Transcript:Kovac?oldid=15162322) | 15162322 | 2026-03-31 12:50 UTC | Commission/hand-in/cancellation dialogue |
| [Smiths' Uniform](https://oldschool.runescape.wiki/w/Smiths%27_Uniform?oldid=15276149) | 15276149 | 2026-07-26 21:05 UTC | Uniform timing/progress effects |
| [Double ammo mould](https://oldschool.runescape.wiki/w/Double_ammo_mould?oldid=15190919) | 15190919 | 2026-04-22 04:40 UTC | Ammo output and requirements |
| [Smithing catalyst](https://oldschool.runescape.wiki/w/Smithing_catalyst?oldid=15190922) | 15190922 | 2026-04-22 04:40 UTC | Catalyst effects |
| [Ore pack](https://oldschool.runescape.wiki/w/Ore_pack_%28Giants%27_Foundry%29?oldid=15190928) | 15190928 | 2026-04-22 04:40 UTC | Pack contents; empirical probabilities |
| [Kovac's grog](https://oldschool.runescape.wiki/w/Kovac%27s_grog?oldid=15304454) | 15304454 | 2026-08-17 18:03 UTC | Consumable effects |
| [Colossal blade](https://oldschool.runescape.wiki/w/Colossal_blade?oldid=15261790) | 15261790 | 2026-07-12 13:05 UTC | Weapon stats/passive/reclaim |
| [Sleeping Giants](https://oldschool.runescape.wiki/w/Sleeping_Giants?oldid=15241064) | 15241064 | 2026-06-27 15:20 UTC | Access quest and tutorial |
| [Q&A Summary 09/06/2022](https://oldschool.runescape.wiki/w/Update:Q%26A_Summary_09/06/2022?oldid=14296513) | 14296513 | 2022-06-21 17:55 UTC | Developer answers on commission selection, recycling and uniform |
| [Giants' Foundry release](https://oldschool.runescape.wiki/w/Update:Giants%27_Foundry?oldid=14293393) | 14293393 | 2022-06-09 15:15 UTC | Official release behaviour |
| [Giants' Foundry Changes](https://oldschool.runescape.wiki/w/Update:Giants%27_Foundry_Changes?oldid=14295784) | 14295784 | 2022-06-18 06:17 UTC | Early fixes |
| [Poll 78 Updates & More](https://oldschool.runescape.wiki/w/Update:Poll_78_Updates_%26_More?oldid=14375650) | 14375650 | 2023-02-11 09:51 UTC | Reward buyback change |
| [More Poll 84 & Yama Prep](https://oldschool.runescape.wiki/w/Update:More_Poll_84_%26_Yama_Prep?oldid=14896480) | 14896480 | 2025-05-07 12:49 UTC | Sweet-spot quality restoration |

## Access, tutorial, and safety

- Giants' Foundry is members-only, non-combat and safe. It is a shared/non-instanced activity after the tutorial. Access requires completion of **Sleeping Giants**. [Main page](https://oldschool.runescape.wiki/w/Giants%27_Foundry?oldid=15307204), [release post](https://oldschool.runescape.wiki/w/Update:Giants%27_Foundry?oldid=14293393).
- Sleeping Giants requires **15 Smithing without boosts**. The quest requires 3 oak logs, wool (not a ball of wool), 10 nails, a valid hammer, a chisel, and either a bucket of water or ice gloves. It awards 1 quest point and 6,000 Smithing XP. [Sleeping Giants](https://oldschool.runescape.wiki/w/Sleeping_Giants?oldid=15241064).
- The tutorial repairs the trip hammer (1 oak log + 5 nails), grindstone (chisel), and polishing wheel (2 oak logs + 5 nails + wool), then teaches crate, crucible, mould, pour, pickup, and all three machines. The quest instance warns that dropped items are lost when progressing through Kovac's instance dialogue. [Sleeping Giants](https://oldschool.runescape.wiki/w/Sleeping_Giants?oldid=15241064).
- Normal required supplies are 28 bar-equivalents of accepted metal and either a bucket of water (buckets are available in the foundry) or ice gloves / smiths gloves (i) for pickup. [Main page](https://oldschool.runescape.wiki/w/Giants%27_Foundry?oldid=15307204).

## Commission state

Kovac chooses two descriptors:

- first: `broad`, `heavy`, `light`, or `narrow`;
- second: `flat`, `heavy`, `light`, or `spiked`;
- a commission never repeats `heavy` or `light` as both descriptors.

That yields the 12 unordered valid pairs: broad-heavy, broad-light, broad-spiked, broad-flat, narrow-heavy, narrow-light, narrow-spiked, narrow-flat, heavy-spiked, heavy-flat, light-spiked, and light-flat. Jagex's Q&A says there are **no weights as such** and invalid pairs are prevented. This supports an unweighted choice among valid outcomes, but does not publish the exact server RNG or reroll algorithm. Treat equal selection as medium-confidence, not a proven implementation detail. [Main page](https://oldschool.runescape.wiki/w/Giants%27_Foundry?oldid=15307204), [Q&A, questions 5 and 11](https://oldschool.runescape.wiki/w/Update:Q%26A_Summary_09/06/2022?oldid=14296513).

The commission timer starts at assignment and stops at hand-in. Storage does not pause it. The result screen shows current quality/time and personal best; PB compares quality first and uses shortest time only as a tie-breaker. A sufficiently long elapsed time causes the result to omit the timer, but the public source does not state the threshold. A player may accept the next commission directly after hand-in. [Main page](https://oldschool.runescape.wiki/w/Giants%27_Foundry?oldid=15307204).

**Cancellation is intentionally unavailable.** Kovac's `Can I scrap my current commission?` branch says he will still reward a bad sword and suggests storing it; he does not scrap swords. An unwanted preform must be completed, ruined/handed in, or stored indefinitely. [Kovac transcript](https://oldschool.runescape.wiki/w/Transcript:Kovac?oldid=15162322), [Preform](https://oldschool.runescape.wiki/w/Preform?oldid=15190927).

## Mould scoring

The mould has exactly three slots: forte, blade, and tip. Each mould part has six descriptor values. For the two commission descriptors, add both values for each selected part, then sum the three parts. The result is the mould score. It raises starting/final quality but **does not** increase difficulty; difficulty is based only on metal score. Maximum mould score is 69 (light-flat). Defaults average 38 over the 12 commissions; fully unlocked optimal sets average 59. [Main page](https://oldschool.runescape.wiki/w/Giants%27_Foundry?oldid=15307204).

Score order below is `[broad, narrow, heavy, light, spiked, flat]`.

### Default moulds

| Slot | Mould | Scores |
|---|---|---|
| forte | Chopper Forte | 4, 0, 0, 4, 0, 4 |
| forte | Gladius Ricasso | 4, 0, 4, 0, 0, 4 |
| forte | Disarming Forte | 0, 4, 0, 4, 4, 0 |
| forte | Medusa Ricasso | 8, 0, 6, 0, 0, 8 |
| forte | Serpent Ricasso | 0, 6, 0, 8, 0, 8 |
| forte | Serrated Forte | 0, 8, 8, 0, 6, 0 |
| blade | Saw Blade | 4, 0, 0, 4, 4, 0 |
| blade | Defenders Edge | 4, 0, 4, 0, 4, 0 |
| blade | Fish Blade | 0, 4, 0, 4, 0, 4 |
| blade | Medusa Blade | 8, 0, 8, 0, 0, 6 |
| blade | Stiletto Blade | 0, 8, 0, 6, 0, 8 |
| blade | Gladius Edge | 0, 6, 8, 0, 0, 8 |
| tip | People Poker Point | 0, 4, 4, 0, 0, 4 |
| tip | Chopper Tip | 4, 0, 0, 4, 4, 0 |
| tip | Medusa's Head | 4, 0, 4, 0, 4, 0 |
| tip | Serpent's Fang | 0, 8, 0, 6, 8, 0 |
| tip | Gladius Point | 0, 8, 8, 0, 0, 6 |
| tip | Saw Tip | 6, 0, 8, 0, 8, 0 |

### Reputation-unlocked moulds

| Slot | Mould | Smithing | Rep | Scores |
|---|---|---:|---:|---|
| forte | Stiletto Forte | 50 | 300 | 0, 8, 0, 10, 0, 8 |
| forte | Defender Base | 51 | 300 | 8, 0, 10, 0, 0, 8 |
| forte | Juggernaut Forte | 61 | 350 | 4, 0, 4, 0, 16, 0 |
| forte | Chopper Forte +1 | 70 | 400 | 3, 0, 0, 4, 0, 18 |
| forte | Spiker! | 79 | 450 | 0, 1, 2, 0, 22, 0 |
| blade | Flamberge Blade | 48 | 300 | 0, 8, 0, 8, 10, 0 |
| blade | Serpent Blade | 50 | 300 | 0, 10, 0, 8, 0, 8 |
| blade | Claymore Blade | 59 | 350 | 16, 0, 4, 0, 0, 4 |
| blade | Fleur de Blade | 71 | 400 | 4, 0, 18, 0, 1, 0 |
| blade | Choppa! | 80 | 450 | 1, 0, 0, 22, 0, 2 |
| tip | Corrupted Point | 49 | 300 | 0, 8, 0, 10, 8, 0 |
| tip | Defenders Tip | 52 | 300 | 10, 0, 8, 0, 8, 0 |
| tip | Serrated Tip | 60 | 350 | 0, 4, 0, 16, 4, 0 |
| tip | Needle Point | 69 | 400 | 0, 18, 0, 3, 0, 4 |
| tip | The Point! | 81 | 450 | 2, 0, 0, 1, 0, 22 |

All unlocks cost 5,400 reputation. A player may buy a mould below its Smithing requirement. The boosted/current level is checked at pour: if it falls below the requirement before pouring, the mould cannot be used; after pour the preform remains finishable. Moulds are permanent unlocks rather than inventory items. [Main page](https://oldschool.runescape.wiki/w/Giants%27_Foundry?oldid=15307204), [Reward Shop](https://oldschool.runescape.wiki/w/Giants%27_Foundry_Reward_Shop?oldid=15318598).

The exact tables, optimal selections, and per-commission best scores are encoded in `wiki-data.json`.

## Crucible and material conversion

- Capacity is exactly **28 bar-equivalents**. A bar contributes 1. Accepted tiers are bronze, iron, steel, mithril, adamant, and rune. [Crucible](https://oldschool.runescape.wiki/w/Crucible?oldid=15202175).
- Accepted equipment must be player-smithable and normally require at least two bars. It contributes `smithing bar cost - 1`: scimitar/longsword/full helm/square shield/claws = 1; warhammer/battleaxe/chainbody/kiteshield/2h sword/platelegs/plateskirt = 2; platebody = 4. One-bar products contribute 0 and are not useful. [Main page](https://oldschool.runescape.wiki/w/Giants%27_Foundry?oldid=15307204), [Q&A question 30](https://oldschool.runescape.wiki/w/Update:Q%26A_Summary_09/06/2022?oldid=14296513).
- Black, white, dragon and ornamental/trimmed equipment are rejected. Lead, cupronickel, and keel parts are rejected. The main page also lists lovakite, blurite and elemental materials as unusable for the minigame.
- Overflow prompts a warning and destroys excess metal if accepted. Emptying destroys the entire crucible contents. Pouring is blocked while a previously poured preform has not been handed in. [Crucible](https://oldschool.runescape.wiki/w/Crucible?oldid=15202175).
- Adding a tier uses its normal furnace smelting requirement: bronze/iron 15, steel 30, mithril 50, adamant 70, rune 85. Boosts work. The crucible can be filled for a following sword before the current commission is finished, allowing one boost to cover two fills. [Main page](https://oldschool.runescape.wiki/w/Giants%27_Foundry?oldid=15307204).
- If three or more metals exist, only the two with the largest amounts score; ties prefer the higher tier. Other metals still consume capacity but score zero. [Main page](https://oldschool.runescape.wiki/w/Giants%27_Foundry?oldid=15307204).
- The public pages do not explicitly define acceptance of **noted bars/equipment**. Do not infer it from the ore pack or bank chest; this needs cache/runtime confirmation.

## Metal score, rounding, quality, and difficulty

For the two scoring metals, sorted by amount descending with higher-tier tie-break:

```text
Vn = 10 * tier_n * amount_n / 28
metalScore = floor((floor(10*V1) + floor(10*V2) + floor(V1*V2)) / 10)
```

The nested floors and their order are material. A pure metal sets `V2 = 0`. Tier is bronze=1, iron=2, steel=3, mithril=4, adamant=5, rune=6. Initial quality is `metalScore + mouldScore`; documented range is 10–199 (130+69). [Main formula](https://oldschool.runescape.wiki/w/Giants%27_Foundry?oldid=15307204).

Verified examples:

| Material | Metal score | Sections |
|---|---:|---:|
| 28 bronze | 10 | 3 |
| 28 iron | 20 | 4 |
| 28 steel | 30 | 4 |
| 28 mithril | 40 | 4 |
| 28 adamant | 50 | 4 |
| 28 rune | 60 | 5 |
| 19 steel + 9 mithril | 59 | 4 |
| 18 mithril + 10 adamant | 89 | 5 |
| 19 adamant + 9 rune | 118 | 6 |
| 14 adamant + 14 rune | 130 | 7 |

Difficulty boundaries are 10–19: 3 sections; 20–59: 4; 60–89: 5; 90–119: 6; 120–130: 7. Higher difficulty makes temperature ranges narrower, linearly reaching half their original width at metal score 130. Exact numeric heat-band endpoints are not public. [Main page](https://oldschool.runescape.wiki/w/Giants%27_Foundry?oldid=15307204).

## Preform state and refinement

Pickup after pouring requires a bucket of water, ice gloves, or smiths gloves (i). The preform is automatically equipped two-handed and can only be unequipped through preform storage. It cannot simply be discarded; finish it, ruin and hand it in, or store it. [Preform](https://oldschool.runescape.wiki/w/Preform?oldid=15190927).

| Tool | Required heat | Cadence | Progress | Heat delta | Smithing XP per normal successful action |
|---|---|---:|---:|---:|---:|
| Trip hammer | hot | every 5th game tick | +2% | -2.5% | 4.5 |
| Grindstone | medium | every 2nd game tick | +1% | +1.5% | 3.2 |
| Polishing wheel | cold | every 2nd game tick | +1% | -1.7% | 3.3 |

The waterfall's `Cool-preform` and lava pool's `Heat-preform` start slower and accelerate. Their `Quench-preform` and `Dunk-preform` options start faster and accelerate. The strategy guide says an unattended interaction can reach roughly 10% of the full temperature bar per tick; this is descriptive, not a published acceleration table. Passive cooling oscillates and is explicitly unreliable. [Main page](https://oldschool.runescape.wiki/w/Giants%27_Foundry?oldid=15307204), [Strategies](https://oldschool.runescape.wiki/w/Giants%27_Foundry/Strategies?oldid=15275840).

Using the wrong machine for the current progress section, or the correct machine outside its heat range, damages quality. The main gameplay section says a damaging action loses **10 quality points**, while the rewards prose says final reputation is metal+mould minus **5 per mistake**. Those two current statements conflict. Do not silently choose one; cache tests should determine whether the HUD uses half-units, whether one interaction creates two mistake events, or whether one paragraph is stale. At quality zero, work is blocked and Kovac awards exactly 30 Smithing XP and 60 coins regardless of material/mould. [Main page](https://oldschool.runescape.wiki/w/Giants%27_Foundry?oldid=15307204), [Kovac transcript](https://oldschool.runescape.wiki/w/Transcript:Kovac?oldid=15162322).

Clicking a machine while already working it outside a sweet spot overworks the preform: the next action gives no progress, but it does not damage quality. Early hotfixes specifically removed a false trip-hammer quality-loss timing case. [Main page](https://oldschool.runescape.wiki/w/Giants%27_Foundry?oldid=15307204), [changes](https://oldschool.runescape.wiki/w/Update:Giants%27_Foundry_Changes?oldid=14295784).

### Sweet spots

- The window appears immediately after a successful machine tick and lasts about 6 seconds / 10 ticks.
- One click during it turns the border green. The **next successful machine action** gains about 5% bonus progress and twice the usual machine XP.
- Since 7 May 2025, a promptly hit bonus action also restores a small portion of lost quality. The public source calls this a unit/portion and does not publish an exact numeric amount.
- The correct tool and heat are still required. The bonus can carry into the next progress section.
- Swords with 3–5 sections offer at most 2 sweet spots; 6–7 sections offer at most 3. A missed opportunity is consumed.

The exact random scheduling/distribution algorithm and exact bonus-progress/repair integer units are not public. [Main page](https://oldschool.runescape.wiki/w/Giants%27_Foundry?oldid=15307204), [Strategies](https://oldschool.runescape.wiki/w/Giants%27_Foundry/Strategies?oldid=15275840), [2025 update](https://oldschool.runescape.wiki/w/Update:More_Poll_84_%26_Yama_Prep?oldid=14896480).

## Hand-in rewards

Let `q` be final displayed quality:

```text
smithingXp = (floor(q*q / 73) + floor(1.5*q) + 1) * 30
coins = 2 * smithingXp
reputation = q
runEnergy = min(100%, runEnergy + 50 percentage points)
```

This gives 30–25,230 XP and 60–50,460 coins for q=0–199. The page's separate “expected XP per sword” table contains averages across random commissions, not integer-quality formula test vectors; do not use those averages to change the rounding above. Coins go directly to the bank; Ultimate Ironmen receive them in inventory. A bank coin placeholder may be filled. [Main page](https://oldschool.runescape.wiki/w/Giants%27_Foundry?oldid=15307204), [Foundry Reputation](https://oldschool.runescape.wiki/w/Foundry_Reputation?oldid=14974229).

## Reward shop

The shop has infinite stock and no restock timer. It is Bank-PIN protected. Moulds are permanent unlocks and cannot be sold back. All listed item rewards except Kovac's grog and the Colossal blade can be used on Kovac for 80% reputation buyback. [Reward Shop](https://oldschool.runescape.wiki/w/Giants%27_Foundry_Reward_Shop?oldid=15318598), [Kovac transcript](https://oldschool.runescape.wiki/w/Transcript:Kovac?oldid=15162322).

| Reward | Cost | Buyback | Gate |
|---|---:|---:|---|
| Double ammo mould | 2,000 | 1,600 | Dwarf Cannon complete |
| Kovac's grog | 300 | none | — |
| Smithing catalyst | 15 | 12 | — |
| Ore pack | 200 | 160 | — |
| Smiths tunic | 4,000 | 3,200 | — |
| Smiths trousers | 4,000 | 3,200 | — |
| Smiths boots | 3,500 | 2,800 | — |
| Smiths gloves | 3,500 | 2,800 | — |
| Colossal blade | 5,000 | none | — |

One of every item plus every mould costs 27,915 reputation; excluding moulds costs 22,515. The nine item rewards, not the mould unlocks, are the Giants' Foundry collection-log entries. Exact duplicate-purchase restrictions for unique rewards are not documented on these pages and should be cache/runtime verified.

## External reward effects

### Smiths' uniform

- Four pieces total 15,000 reputation.
- Each independently contributes 20 percentage points to the chance that an anvil action takes 4 ticks instead of 5; 20/40/60/100% by pieces.
- At Foundry machines the same aggregate chance increases that action's progress: +5% for trip hammer; +10% for grindstone or polishing wheel. The page describes the full set as approximately 20% more XP/hour.
- Smiths gloves combine with ice gloves into smiths gloves (i), retaining both effects.
- The timing effect does not speed incendiary cannonballs or dragon metal sheets.

[Smiths' Uniform](https://oldschool.runescape.wiki/w/Smiths%27_Uniform?oldid=15276149), [Q&A question 24](https://oldschool.runescape.wiki/w/Update:Q%26A_Summary_09/06/2022?oldid=14296513).

### Double ammo mould

Requires Dwarf Cannon to buy. It consumes 2 bars and makes 8 cannonballs per action instead of 1 bar/4, halving action count. Current supported cannonball tiers and per-two-bar outputs are bronze level 5/18 XP, iron 20/34, steel 35/51.2, mithril 55/68, adamant 75/85, rune 90/101. The quest's gold cannonball operation makes 2 from 2 gold bars for 75 XP. The Sailing Ancient Furnace is an external modifier that can smelt cannonballs twice as fast. [Double ammo mould](https://oldschool.runescape.wiki/w/Double_ammo_mould?oldid=15190919), [Cannonball](https://oldschool.runescape.wiki/w/Cannonball?oldid=15299595).

### Smithing catalyst

One catalyst is consumed per produced coal-requiring bar. It halves coal and doubles Smithing XP at a normal furnace or with Superheat Item. At Blast Furnace it still doubles XP but cannot halve coal again. It applies only to coal-using recipes; the current page lists elemental, steel, lovakite, mithril, adamantite, and runite bars. [Smithing catalyst](https://oldschool.runescape.wiki/w/Smithing_catalyst?oldid=15190922).

### Ore pack

Opening rolls 30 independent noted ores from coal, iron, mithril, adamantite, and runite. Mining and Smithing levels do not influence it. The published distribution is explicitly empirical (1,913 packs / 57,390 rolls), not an exact Jagex table: coal 81.6421%, iron 10.1115%, mithril 6.4489%, adamantite 1.5159%, runite 0.2823%. Treat these as medium-confidence estimates and do not claim exact parity from them. [Ore pack](https://oldschool.runescape.wiki/w/Ore_pack_%28Giants%27_Foundry%29?oldid=15190928).

### Kovac's grog

Restores 1 HP, boosts Smithing by 4, and drains Attack, Ranged, and Magic by 2. It is tradeable and noteable. [Kovac's grog](https://oldschool.runescape.wiki/w/Kovac%27s_grog?oldid=15304454).

### Colossal blade

Requires 60 Attack, is two-handed, has no special attack, and adds `2 * min(targetSize, 5)` to max hit (maximum +10); the passive stacks with black-mask effects. Stats: attack stab -4/slash 98/crush 65/magic -4/ranged 0; defence all 0 except ranged -1; melee strength 100; speed 6; attack range 1. It is untradeable, unnoteable and cannot be alched. Perdu reclaims it for 300,000 coins only when the player owns no other copy; a PvP killer receives 225,000. [Colossal blade](https://oldschool.runescape.wiki/w/Colossal_blade?oldid=15261790).

## Persistence and edge cases

| Event | Current documented behaviour | Confidence |
|---|---|---|
| Manual preform storage | Preserves progress and heat; timer continues | high |
| Exit cave / teleport away | Automatically unequips and stores preform | high |
| Return / login | Message says current commission is kept warm in storage | high |
| Scrap/cancel request | Refused; finish, ruin/hand in, or store | high |
| Ruined sword hand-in | 30 Smithing XP, 60 coins, quality 0 result | high |
| Crucible overflow | Warn, then excess is destroyed if accepted | high |
| Empty crucible | Destroys all stored metal | high |
| Existing poured preform | Blocks another pour | high |
| Logout/reconnect | Login message and storage persistence are documented; exact save tick/order is not | medium |
| World hop/server restart | Exact persistence semantics are not published | unresolved |
| Player death | Normal area is safe; forced/external death behaviour is not published | unresolved |
| Full inventory at reward/pickup | Coins routing is known; all other full-inventory branches need cache/runtime evidence | unresolved |
| Multiple players on one machine | Shared non-instanced activity with per-player preform state is implied; contention/order is not published | medium |
| Duplicate unique reward purchase | Shop is infinite, but unique-item gating is not fully described | unresolved |
| Noted bars/equipment in crucible | Not explicitly specified | unresolved |

## Historical changes that affect parity

- 2025-05-07: prompt sweet-spot actions began restoring a portion of lost quality. [Update](https://oldschool.runescape.wiki/w/Update:More_Poll_84_%26_Yama_Prep?oldid=14896480).
- 2023-02-08: eligible unique rewards became sellable to Kovac for 80%; Colossal blade and grog excluded. [Update](https://oldschool.runescape.wiki/w/Update:Poll_78_Updates_%26_More?oldid=14375650).
- 2022-07-13: shop gained Bank-PIN protection and coin reward gained placeholder support.
- 2022-06-29: coins moved directly to bank, total sword count was added to hand-in chat, sweet spots were distributed more evenly.
- 2022-06-15: best moulds sort first; back-to-back commission confirmation removed; finished-sword examine no longer movement-locks; crucible input-X remembered; mould-jig scroll/facing fixed.
- 2022 launch hotfix: a trip-hammer timing case no longer falsely damages quality.
- Pre-release concept values of 20 bars and lower XP are obsolete. The shipped design uses 28 bars, tripled proposed XP, and a coin reward. [Concept revision](https://oldschool.runescape.wiki/w/Update:Giants%27_Foundry_-_Concept_%26_Rewards).

## Open evidence gaps — do not invent

1. Exact numeric hot/medium/cold boundaries for every metal score and the exact linear rounding rule.
2. Lava/water acceleration curve, passive cooling sequence, and within-tick ordering among machine progress, heat, section transition, natural cooling, damage and sweet-spot processing.
3. Current-page conflict: 10 quality lost per damaging action versus reward prose saying 5 per mistake.
4. Exact sweet-spot RNG/distribution, bonus progress (described as about 5%), and post-2025 repair amount.
5. Exact commission RNG/reroll implementation despite the Q&A saying no weights.
6. Exact ore-pack probabilities; published figures are empirical only.
7. Noted crucible input, duplicate unique reward gating, inventory-full branches, world-hop/server-restart/death cleanup, and the long-timer omission threshold.
8. Exact UI component IDs, varbits, animations, sounds, object IDs and packet ordering are cache/runtime facts, not Wiki mechanics; obtain them from revision-240 cache analysis.

These gaps should be explicit test cases in the engine/cache pass. A substitute constant must be labelled as a project decision, not OSRS-verified behaviour.
