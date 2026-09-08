# Sailing performance plan

## Current risks

The imported foundation performs linear scans in `BoatKinematics.tick`, `BoatManager.boatAt`, and `WorldEntityList.findAt`. That is acceptable only as a small scaffold. It is not an architecture for a world-spanning skill or a 4,095-slot protocol limit.

Other high-cost paths are observer fanout, deck-zone rebuild/replay, rotated-footprint collision, passenger root-view updates, mounted facility output, port congestion, creature targeting, crew searches, and persistence bursts.

## Budgets

Budgets must be measured on the production Java 21 runtime and normal server tick configuration.

- Sailing work must not cause the total game tick to exceed the server's established p99 budget.
- No per-tick allocation proportional to the whole cache, all players, or all map zones.
- Idle/moored boats without state changes should perform no kinematic or output work.
- Network output is interest-filtered and delta-based; rebuilds occur only on actual view/region changes.
- Persistence is dirty-field/aggregate based and bounded per tick, with shutdown flush and backlog metrics.

Concrete millisecond/byte thresholds remain `BLOCKED_BY_CAPTURE` until the baseline server profiler and production hardware budget are recorded.

## Required structures

- Dense slot array for protocol identity plus a boat-ID map for authority lookups.
- Zone/spatial index for root footprint, nearby boats, docks, hazards, fishing/encounter areas, and observers.
- Active navigation set instead of scanning every owned/moored boat.
- Cached rotated footprint/collision masks keyed by class/angle bucket where protocol semantics permit.
- Per-boat observer set with deterministic delta fanout.
- Scheduled activity queues for crew, wreck respawns, task refresh, encounters, and hazards.
- Batched/transactional persistence separate from the authoritative tick mutation.

## Benchmarks

| Scenario | Sizes | Measures |
|---|---|---|
| Kinematics | 1, 32, 256, 1,024, 4,095 entities | tick time, allocations, deterministic output |
| Spatial queries | sparse/open sea, dense port, overlapping boundaries | p50/p95/p99 lookup and correctness |
| Observers | 1 boat with 1/32/256 observers; many boats | output count/bytes, build-area churn |
| Passengers | class capacities plus disconnect/rejoin | root-view and update costs |
| Facilities/combat | full sloop, volleys, crew orders | target/search/output allocation |
| Persistence | login burst, autosave, shutdown, recovery | queue depth, latency, retries, duplicate safety |
| UI | side-panel/crew/cargo refresh under activity | script/var/output count |

Every benchmark includes a correctness digest so an optimization cannot change gameplay silently.

## Instrumentation

Record active/moving/moored boats, world-entity slots, spatial entries, passengers, observers, per-tick kinematic/collision time, output messages/bytes, rebuilds, activity queue depth, persistence dirty/backlog/retry counts, and recovery conflicts. Metrics must not include player names, cargo contents, or secrets.

## Optimization gate

Optimize only after a reproducible benchmark identifies a bottleneck. Preserve golden behavior before and after each change. Changes to tick order, collision, rounding, fanout, or persistence semantics require parity review, not just a faster benchmark.
