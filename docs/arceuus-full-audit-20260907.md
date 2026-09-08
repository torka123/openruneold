# Full Arceuus audit — 7 September 2026

This follow-up covers the entire 45-entry Arceuus catalog, all nine thralls, and all 23 reanimated creatures (46 unnoted heads). Quest-free access is the intentional local policy; level, rune, Prayer, Book of the Dead, target and cooldown requirements remain. The earlier access/lock deployment is historical evidence in [the original audit](arceuus-audit-20260907.md). The findings below supersede that document's earlier outstanding gameplay list.

## Evidence and scope

- [Spell-by-spell Wiki matrix](arceuus-wiki-audit-20260907.md): all 45 entries, source links, handler coverage and unresolved differences.
- [RSProx and native cache audit](arceuus-rsprox-audit-20260907.md): 16 downloaded captures converted to `.txt`, source URLs and SHA-256 manifest in `logs/arceuus-audit-20260907/capture-manifest.json`, plus revision-240 model, animation and client-script decoding. Seasonal and other-player observations are identified separately.
- [Reanimation and RuneLite audit](arceuus-reanimation-runelite-20260907.md): installed RuneLite plugin inspection, complete head catalog, spell-on-ground packet path and persistent drop provenance.

The local cache remains revision 240 at `.data/cache/SERVER` and `.data/cache/LIVE`. This full gameplay audit does not modify either cache. The previously deployed client quest-param correction remains in place.

## Repairs

| Area | Defect and corrected behavior |
|---|---|
| Disappearing thralls | The live log contains `Expected movement within 2 tile distance` exceptions. A raw route left the NPC at stationary move speed 127, allowing a movement burst that caused removal during NPC processing. Thralls now use the walking API. Regression coverage passes all nine through the real movement and post-tick processors. |
| Thrall targets and lifecycle | Attack launch now records targets even for zero damage or a splash. Delayed impacts and Twinflame echoes cannot steal the target. Owner death, logout, replacement, floor changes and distant travel invalidate the old summon and its queued hits. |
| Thrall visuals and timing | Use current native ghost/skeleton/zombie animation families and verified models. The first attack can occur at +4, raised buff at +5, and cooldown ends at +17. The observed Magic-99 NPC removal/buff-clear offsets are +102/+103; extrapolation to other levels is documented as inference. |
| Skeletal projectiles | Replace generic magic height/timing with the captured ranged fields. Actual outgoing projectile tests cover all three skeleton tiers at distances one/two/three, matching end frames 72/77/82, start frame 57, heights 100/124, angle 15 and progress 32. |
| Reanimations | Correct dog to Adept and add hellhound to Expert. All four tiers award their modern fixed cast XP. Both unnoted variants of every head work, with notes and incorrect tiers rejected. |
| Fresh heads | Inventory and ground targeting share one cast implementation. Fresh monster drops retain their origin, allowing same-floor casting within the 31×31 origin area; stored/tradeable/originless heads require the Dark Altar. Bank/tradeoffer transactions permanently normalize fresh heads before capacity checks and preserve the original on rollback. |
| Reanimated kills | The summoner receives Prayer XP once. A separate drop-suppression flag prevents remains/item loot while retaining the existing attributed kill hooks, including Slayer. Thralls retain full reward suppression. |
| Mark and Ward | Combat effects use timed state instead of buff-display preferences. Mark reaches the real demonbane formula collector and Yama path; Ward reduces demonic damage by subtracting the floored reduction, including Duke's separate path. |
| Native buff timers | Mark and Ward use native wrapper 5926: Mark key 3120 takes game-tick duration; Ward key 3126 takes the starting map clock. Expiry/clear sends zero. These calls follow decoded native scripts, not an observed Mark/Ward packet capture. Ward and Shadow Veil use base Magic, matching their native timer readers. |
| Shadow Veil | The existing pickpocket failure path now rolls Veil's 15% first, then the Dodgy necklace's 25%. Veil success preserves necklace charges; necklace protection consumes a charge and destroys the necklace on its tenth activation. Charges persist with the player. |
| Death Charge | Uses finishing damage, proximity/floor and PvP restrictions; supports the native two-charge upgrade, 100-tick duration/cooldown, appropriate graphics, and excluded targets. |
| Corruption | Tests execute staged Prayer drains for both tiers and Mark acceleration, non-stacking behavior, and Ward cure/immunity. Native armed/cooldown state is updated. |
| Dark Lure | Registered targeting permits implings without an Attack option, maintains timed owned following using real movement, and suppresses normal retreat through the existing retaliation path. Splash, expiry and owner cleanup are tested. |
| Other utilities | Vile Vigour uses a 17-tick cooldown; Degrime uses current Herblore level and an 8-tick action delay; Offering uses an 8-tick cooldown and the correct superior-dragon-bone ID, Prayer gate and restoration. |
| Teleports | User-requested override (September 8): Arceuus Home goes to Edgeville (3087, 3495, 0) using the ordinary three-tick Arceuus teleport sequence, with no recharge or displayed cooldown. The native Arceuus animation is `human_spellcast_arceuus_teleport` (6575), paired with `arceuus_teleport_spotanim` (1296); both are cleared on arrival or cancellation so the departure pose cannot leave the player hidden. Repeated button packets preserve the active Arceuus/Ancient cast, including Ancient Home's complete 22-tick sequence. Other Home books keep their persisted 30-minute recharge, and Home retains its 17-tick recent-combat restriction. Minigame Teleport retains its separate native 20-minute timer. |
| Resurrect Crops | Real spell-on-patch tests exercise persistent farming state, success/failure, rune consumption and the one-attempt rule. |

## Verification

The combined verification passed: `scripts/arceuus-verify.ps1 -Label final5`, `BUILD SUCCESSFUL in 54s`, including a complete `:server:app:installDist` rebuild. XML reports contain **355 tests, zero failures/errors/skips**. The low-level transaction module has no standalone tests; five actual inventory transaction tests exercise its changes. Full per-suite counts and timestamps are saved in `logs/arceuus-audit-20260907/test-results-before-restart.json`.

| Module | Tests |
|---|---:|
| Magic spell attacks | 59 |
| Magic teleports | 13 |
| Farming | 3 |
| Thieving | 11 |
| Inventory transactions | 5 |
| Combat formulas | 11 |
| Player Ward checks | 3 |
| Arceuus cache audits | 5 |
| Desert Treasure II | 133 |
| Yama | 112 |

Earlier combined runs found two test-fixture compilation omissions (level byte types and the packet-model dependency), then an existing Duke test that still expected the incorrect Ward rounding. These were corrected before the successful run; the Duke regression now explicitly checks that an eight-damage hit remains eight because its 10% reduction rounds to zero.

Deployment verification passed two controlled restarts after the successful rebuild. All times below are local EDT on 7 September 2026. The pre-restart doctor confirmed PID 33308 used the canonical project root.

| Restart | Active JVM | Custom build marker | Server ready | Doctor |
|---|---:|---|---|---|
| 20 | 32540 | 22:35:39 | 22:35:42, port 43594 | `logs/arceuus-audit-20260907/doctor-restart20.log` |
| 21 | 31480 | 22:44:49 | 22:44:51, port 43594 | `logs/arceuus-audit-20260907/doctor-restart21.log` |

Both doctors confirmed the canonical JVM working directory and revision-240 cache paths. `scripts/arceuus-verification-report.ps1 -Label restart20` and `-Label restart21` both passed the comparison of all 12 audited source SHA-256 fingerprints against `source-hashes-before-restart.json`. The final repository status has the same 1,360 entries as before deployment, and the scoped `git diff --check` passed.

SERVER dat2 SHA-256 stayed `899D421EE88ADE6CE7956EC2A13A09ADCC4882264D27B1CF2BC1CED42AC821E3`; LIVE stayed `FD0FE5EF66039A791A900EDDDF1CDBCFE57E09E8C32EA3496C69E96217A41A7B`. Runtime stdout/stderr are in `logs/cerberus-audit-20260907/restart-20.*.log` and `restart-21.*.log` (the existing controlled-restart helper owns that log directory). Startup reported one slow cycle per restart, 885 ms and 724 ms respectively, alongside existing Hunter coverage warnings; no startup exception was observed and stderr was empty. These startup checks do not substitute for the live gameplay pass described below.

Handler tests use real registry/rune/state/transaction paths where described. They do not establish that every client animation, mouse route or every boss-specific exception has been observed in a live RuneLite session.

## Remaining parity limits

- The generic Minigame chooser currently exposes Pest Control and Giants' Foundry. The native menu's other 19 destinations and their access restrictions remain unsupported. Shared Teleport to Target has no Bounty Hunter gameplay authority in this tree.
- The Master Combat Achievement thrall-duration reward has no reliable completion/reward service here; an existing unconditional-true stub is deliberately not treated as earned completion.
- Exact audiovisual offsets, all teleport cosmetic overrides, all farming patch exceptions, and every boss/style immunity combination have not been proved. Existing boss hit modifiers still govern thrall damage.
- Captures did not establish surplus-Prayer Vile Vigour behavior, corruption's native hitsplat ID, or failed corruption-proc consumption. The source reports these limits; placeholder IDs are not presented as verified assets.
- No complete live RuneLite cast/visual pass has been performed. Restarting RuneLite remains necessary to reload the earlier quest-lock definition correction.

The audit provides reproducible repairs and named evidence gaps; it does not certify perfect 1:1 OSRS parity.
