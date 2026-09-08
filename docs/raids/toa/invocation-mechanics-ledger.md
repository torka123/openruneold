# Tombs of Amascut invocation mechanics ledger

Audit date: 2026-08-28
Protected baseline: no invocations selected and every path at level 0
Status meaning: **automated verified** means the deterministic/server tests pass; **deployed** means the revision-240 server booted with the implementation; **live pending** means a player-client timing or visual check is still required before claiming exact OSRS parity.

## Central rules, UI, and raid lifecycle

| Feature | OSRS evidence | Revision-240/OpenRune mapping | Donor reference | Implementation and test status |
|---|---|---|---|---|
| Standard invocation catalog | Current ToA and invocation Wiki pages; RuneLite invocation helpers | Native interfaces 774/776; enum 4664; components 52, 69, 83, 84, 98; preset varbit 14541; every row maps explicitly to its struct and client invocation id | `InvocationType`, `TOAPartySettings`, `TOAPartyManagementInterface` | 44 standard invocations, raw sum 770 and legal maximum 600; league-only Blazing Tombs rows excluded. **Automated verified and deployed.** |
| Selection rules | Current dependency and exclusivity behavior | Server catalog owns prerequisites, mutually exclusive groups, category, raid-level value, affected mechanics, and client bitmaps | Same donor classes, with enum-order coupling deliberately rejected | Attempts, timer, Helpful Spirit, and initial path difficulty are exclusive families. Overclocked -> Overclocked 2 -> Insanity and Not Just a Head -> Arterial Spray/Blood Thinners are enforced. **Automated verified and deployed.** |
| Presets and board controls | Native ToA board behavior | Five server-side preset slots; explicit revision-240 component handlers; clear/save/load/close/navigation feedback | Donor party UI and settings | Invalid presets are rejected without changing the active selection. Client state is rebuilt from the server selection. **Automated verified and deployed; every visual/button path is live pending.** |
| Locked raid state | Invocations and raid parameters cannot change after entry | `ToaRaidStartSnapshot` captures party size, raid level, invocation set, four path levels, seed, and participant accounts | `TOAManager`/party settings used only as flow evidence | Defensive immutable collections and seeded RNG; all encounters consume the snapshot instead of mutable lobby state. Rejoin reapplies the locked restrictions. **Automated verified and deployed.** |
| Level-0 regression | User-verified level-0 raid is the protected baseline | Empty invocation set produces raid level 0 and three zero client bitmaps | Donor is not authoritative for the protected baseline | Empty rules profiles preserve existing timings/values. Full ToA module: **300 tests passed, 0 failed, 0 errors, 0 skipped.** |

The exact row/struct/client-id crosswalk and donor differences are recorded in `invocation-board-and-catalog-evidence.md`.

## Universal modifiers and path levels

| Invocation group | Implemented behavior | OpenRune integration | Verification status |
|---|---|---|---|
| Attempts | Counts an eligible full-party wipe once, including a same-cycle completion guard; ends the raid when the locked limit is exhausted | Raid lifecycle/wipe state | **Automated verified and deployed.** A disconnect alone preserves the existing rejoin baseline; current OSRS logout-as-wipe behavior remains a documented parity conflict. |
| Timers | Starts on first path entry; 40/35/30/25-minute tiers; applies 20/30/40/50 raid-level reward penalties | Raid timer UI, reward level, and reward service | **Automated verified and deployed; visible countdown/penalty transition live pending.** |
| Helpful Spirit | Supply multipliers 66/33/10 percent with minimum-one rounding | Helpful Spirit supply bundles | **Automated verified and deployed; party-size bundle visuals live pending.** |
| Quiet Prayers | Reduces protection effectiveness by 10 percentage points | Shared combat prayer resolution | **Automated verified and deployed.** |
| Deadly Prayers | Drains `floor(landed damage / 5)` prayer on protected non-typeless enemy damage; excludes self-damage | Shared post-hit combat hook | **Automated verified and deployed.** |
| On a Diet | Blocks food, removes Life food/silk and wipe honey locust while retaining adrenaline | Shared food restriction and ToA supply filtering | **Automated verified and deployed.** |
| Dehydration | Blocks health-restoring potions while allowing non-healing potion effects | Shared potion restriction | **Automated verified and deployed.** |
| Overly Draining | Specials require 100 percent energy; adrenaline lowers that locked minimum to 50 percent | Shared special-attack restriction | **Automated verified and deployed.** |
| Pathseeker/Pathfinder/Pathmaster | Sets the mutually exclusive initial level for all four paths | Locked path-level map | **Automated verified and deployed.** |
| Walk the Path | Levels remaining paths in a seeded 2/1/1 distribution after a path completion, capped at level 6 | Raid path-completion state and seeded RNG | **Automated verified and deployed.** |
| Raid/path scaling | Applies locked party size, raid level, and path level to ToA NPC health, offensive/defensive stats, and encounter damage | Spawn/transmogrify helpers and encounter damage profiles | **Automated verified and deployed.** Strict parity remains live pending: OSRS applies Defence as a calculated defence-roll multiplier rather than simply raising the visible Defence level, and externally owned Het/Scabaras NPCs need a final full-route scaling trace. |
| Modes/rewards | Entry 0-149, Normal 150-299, Expert 300-600; timer penalties feed the effective reward raid level | UI display and reward calculation | **Automated verified and deployed.** |

## Path and boss invocations

| Boss | Invocation | Implemented mechanic | Revision-240 assets / donor area | Status |
|---|---|---|---|---|
| Kephri | Lively Larvae | Seeded 3-5 eggs per player | Kephri 11719/11722; eggs 11728/11729; donor `nr_toa_room_kephri` | **Automated verified and deployed.** |
| Kephri | More Overlords | Adds the missing scarab type to each shield phase | Scarabs 11724/11725/11726; donor Kephri encounter | **Automated verified and deployed.** |
| Kephri | Blowing Mud | Group Dung Strike with telegraph, collision-stepped forced movement, stun, and deterministic wall ray | Dung object 45504; gfx 2145/2146 and stun 245; animations 9799/1114 | **Automated verified and deployed.** Protected-baseline conflict: empty-invocation OpenRune does not schedule base Dung Strike, so only the invocation-added group behavior is enabled. |
| Kephri | Medic! | Slow swarms spawn outside recovery, heal Kephri, remain attackable through shield recovery, and do not grant wave-completion credit | Swarm 11723; animation 9605; donor Kephri controller | **Automated verified and deployed.** |
| Kephri | Aerial Assault | Kephri and bomber-scarab bombs use the enlarged 3x3 area and invocation damage profile | Projectile 2266; shadow 1447; impact 2156 | **Automated verified and deployed; exact current live damage value remains live pending.** |
| Zebak | Not Just a Head | Enables blood magic, barrage/cloud package, and runtime targets | Zebak 11730-11734; clouds 11742/11743; gfx 2176/2177; donor `nr_toa_room_zebak` | **Automated verified and deployed.** |
| Zebak | Arterial Spray | Requires Not Just a Head; expands blood effects and heals Zebak from actual landed blood damage at impact | Same Zebak blood assets | **Automated verified and deployed; exact current 2/3 versus 1/1 healing multiplier remains live pending.** |
| Zebak | Blood Thinners | Requires Not Just a Head; spawns three smaller blood clouds | Small cloud 11743 | **Automated verified and deployed.** |
| Zebak | Upset Stomach | Acid becomes 5x5 and jug-cleared area 3x3 | Acid 2194; jug splash/break 2192/2193 | **Automated verified and deployed.** |
| Akkha | Double Trouble | Executes both memory and detonation special paths in the same cycle | Akkha 11790-11792; animations/gfx 9776-9789 and 2256-2259; donor `nr_toa_room_akkha` | **Automated verified and deployed.** |
| Akkha | Keep Back | Extends melee damage along the line behind the target | Melee animation 9770 | **Automated verified and deployed.** |
| Akkha | Stay Vigilant | Uses the current six-auto special cadence rather than the donor's stale random-auto behavior | Forms 11790-11792; attacks 9770/9772/9774 | **Automated verified and deployed.** |
| Akkha | Feeling Special? | Tightens memory-special step timing and pattern handling | Element gfx 2256-2259 and attacks 9776-9783 | **Automated verified and deployed.** |
| Ba-Ba | Mind the Gap! | Makes the pit/gap slam lethal to players caught in the danger area | Ba-Ba 11778; jump 9748; revision-240 arena map; donor `nr_toa_room_baba` | **Automated verified and deployed.** |
| Ba-Ba | Gotta Have Faith | Sarcophagus energy burst damage scales from remaining prayer | Ba-Ba/sarcophagus encounter assets | **Automated verified and deployed.** |
| Ba-Ba | Jungle Japes | Banana peels apply their damage/stun hazard | Baboon 11781; projectile family 2242/2243/2247/2248 | **Automated verified and deployed.** |
| Ba-Ba | Shaking Things Up | Expands the slam danger profile | Rockfall/shockwave 2249-2252/2257 | **Automated verified and deployed.** |
| Ba-Ba | Boulderdash | Releases rolling boulders every 7 ticks instead of baseline 11 without speeding normal autos | Boulders 11782/11783; roll 9796 | **Automated verified and deployed.** |

## Wardens invocations

| Invocation | Implemented phase change | Revision-240 assets / donor difference | Status |
|---|---|---|---|
| Ancient Haste | Each unblocked phase-one orb contributes two charge rather than one | P1 Wardens 11746-11749; charge animation 9682; agrees with donor charging-orb behavior | **Automated verified and deployed; visible energy-bar step live pending.** |
| Acceleration | P2 Warden interval 8 to 4 and Obelisk interval 40 to 30 | Obelisk 11751/11752; P2 Wardens 11753-11758; donor only covers the Obelisk speed | **Automated verified and deployed; exact current P2 live cadence pending.** |
| Penetration | Positive Obelisk hits gain 5 damage and disable all overhead prayers for the existing 8-cycle lock | Obelisk attacks/animations 9727/9732/9733; donor supplies behavioral evidence | **Automated verified and deployed; overhead relight/visual expiry live pending.** |
| Overclocked | Selects a 6-tick slam cadence | P3/P4 Wardens 11761-11764; slams 9675/9677/9678 | **Automated verified and deployed.** |
| Overclocked 2 | Requires Overclocked and selects a 4-tick slam cadence | Same Warden assets; donor cumulative timing is deliberately not copied | **Automated verified and deployed.** |
| Insanity | Selects 3-tick slams, faster phantom and P4 clocks, shortened siphons, preserved post-siphon slam cycle, lightning, and maximum tile-removal sequence | Siphons 11772/11773; phantoms 11774-11777; gfx 2111/2199/2220/2228 and 2160 | **Automated verified and deployed.** Exact current lightning/collapse tick capture, phantom cadence, missed-skull damage, and party-scaled siphon HP are live pending. |

## Donor and evidence policy

`%USERPROFILE%\\Downloads\\TOA.zip` was used to understand flow, state machines, and encounter intent. Donor Java and donor IDs were not copied. Revision-240 cache definitions and OpenRune systems remain authoritative. Known donor differences are recorded instead of being silently inherited, including enum-order UI mapping, stale Akkha switching, incomplete Acceleration behavior, cumulative Warden slam timing, and the Not Just a Head dependent-clear bug.

Primary behavior references:

- https://oldschool.runescape.wiki/w/Tombs_of_Amascut
- https://oldschool.runescape.wiki/w/Tombs_of_Amascut/Strategies
- https://oldschool.runescape.wiki/w/Energy_Siphon
- https://github.com/LlemonDuck/tombs-of-amascut
- https://github.com/runelite/runelite/discussions/15655
- https://github.com/runelite/runelite/discussions/16958
- https://tonsofxp.com/blog/osrs-toa-invocations-guide (secondary orientation only)

## Verification and deployment record

- `:content:minigames:tombs-of-amascut:test`: **300 tests, 0 failures, 0 errors, 0 skipped**, across 27 suites.
- `:server:app:shadowJar`: **BUILD SUCCESSFUL**.
- Revision-240 cache tests and startup asset guards passed.
- The canonical server completed the required two boots, printed `OPENRUNE CUSTOM BUILD ACTIVE`, loaded 384 scripts, and bound game/web/service ports 43594/8080/9091 with empty stderr logs.
- A generic long-cycle warning occurred once immediately after each cold start; the second server remained alive and owned all three ports.

The implementation is therefore automated-verified and deployed. It is not labelled fully 1:1 until the remaining live-client checks in this ledger are completed with revision-240 UI input, animation/timing observation, solo and 2-8 player raids, death/wipe/logout/rejoin, and reward retrieval.
