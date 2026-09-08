# Theatre of Blood finish pass — 2026-08-16

## Status

The existing `content/minigames/theatre-of-blood` implementation was repaired in place. No second raid/session/party/instance implementation was created. The focused test suite, complete server application build, revision-240 cache build, and two controlled server boots pass.

The server-side implementation and deployment are complete for this pass. The 51-step visual/interactive acceptance run is **not claimed as complete** because this workspace has no native RSProx input harness and no second logged-in client. Items requiring visible animation, wall-clock observation, four-direction attacks, actual combat, repeated chest clicks, reconnect, or two simultaneous players remain marked `LIVE CLIENT PENDING` below.

## Root causes and repairs

### Nylocas

- Wave NPCs had only coarse destination state and did not continuously use the real route finder. They can now receive one of the four active pillar targets, walk with collision/entity blocking enabled, retry when blocked, face the selected pillar when adjacent, and repeat their native style attack animation.
- The old pillar-health/failure path was incompatible with the requested custom rule. Nylocas-room pillar attacks now queue an explicit zero hitmark and never mutate pillar HP. This is scoped to the Nylocas encounter; Verzik pillars retain normal HP/collapse behavior.
- Vasilias creation depended on wave progress/clear state. Wave 3 now stores one room-level spawn deadline and a `bossSpawned` guard. It spawns once without waiting for wave 3 deaths; no later wave can be scheduled.
- Only the basic three wave forms were classified consistently. Entry, Normal, and Hard mode now map incoming-small, incoming-large, fighting-small, fighting-large, and Vasilias forms to one authoritative melee/ranged/magic index. Large forms split into two matching fighting-small forms and inherit a valid pillar target.
- Wrong-style damage could reach the boss through the generic hit path. The authoritative NPC-hit modifier now reduces every wrong-style hit to zero before HP mutation, then preserves the Vasilias heal/reflection behavior.
- Cache death inference selected an idle or unrelated compatible animation for several Nylocas forms. The revision-240 resolver now has reviewed Nylocas idle/death families and explicit detonating death sequences for all large Entry/Normal/Hard variants.

### Sotetseg

- Attack selection launched mechanics without setting the current player as Sotetseg's facing entity. Every selected target is now faced before the animation/projectile; target changes refresh facing, and no-target/maze transitions clear stale facing.

### Xarpus

- Recovery could transition based on exhumed completion and used the prior six-HP heal. It now has one immutable recovery deadline; every active exhumed healing event restores exactly four HP, capped by normal `heal`, and recovery/exhumed state is cleared before combat begins once.
- Room completion shared a forward-advance path that could move the player into the next room. Xarpus death now marks Xarpus complete and cleans its owned NPC/hazard state while leaving players in the room. Only loc `32751` advances Xarpus to Verzik staging.

### Progression and cleanup

- The generic barrier/passage path could reinitialize a completed encounter or start the next room as a side effect. Progress is now monotonic: `LOCKED -> READY -> ACTIVE -> COMPLETE`; only `source + 1` is a legal forward destination, completed destinations reject re-entry, and completion is recorded before cleanup/unlock.
- Encounter state is party/raid-instance state, not NPC-local state. Repeated barrier clicks see the existing `READY`/`ACTIVE`/`COMPLETE` state instead of creating a boss.
- The treasure room was initially marked complete when the first teammate entered. It now remains active in `REWARDS` until every entered member has claimed, allowing all party members to cross the Verzik exit. The last valid claim atomically marks the raid and treasure room complete.

### Dawnbringer and Verzik start

- The final barrier used the same generic activation path as combat rooms, so crossing could start Verzik. It now only validates the Dawnbringer and moves the player into the prepared arena.
- A revision-240 seated, non-combat Verzik (`14795`) is spawned while the room is `READY`. NPC option 1 runs the dialogue and a guarded party-wide `startVerzik` transition. Duplicate talk attempts cannot start another encounter.
- Dawnbringer issuance now scans the party's inventories, worn slots, and the Xarpus/Verzik raid floor before issuing item `22516`. Full inventory refuses safely. Logout removes the carried raid copy and re-opens the claim; phase-one completion, Verzik completion, abort, reset, and instance cleanup remove inventory/worn/floor copies.
- Dawnbringer special damage is 75–150 and costs 35% energy. Non-Dawnbringer phase-one damage is capped at 10 melee or 3 ranged/magic. Crossing the barrier alone does not enable combat.

### Verzik and rewards

- Phase 1 is now gated by `encounter.started`, begins only from the seated-Verzik dialogue, uses the 14-tick phase-one cadence, and removes the Dawnbringer at the phase transition.
- Existing Phase 2 mechanics were preserved: ranged urn/bomb, melee bounce, lightning, explosive Nylocas, Athanatos healing/shield window, and cleanup.
- Existing Phase 3 mechanics were preserved and tied to current tank selection/facing and movement: melee/ranged/magic, Nylocas, webs, yellow pools, green ball, and low-HP tornado.
- Verzik completion is guarded, cleans encounter-owned NPCs/hazards and Dawnbringer state, leaves the party in the completed room, and requires manual loc `32738` passage to rewards.
- The existing reward service retains its per-player `claiming` lock and persistent participant `rewardClaimed` flag. Repeated/concurrent chest clicks cannot award twice; full inventory uses the existing safe floor-drop behavior.

## Authoritative encounter state

One `TobRaidSession` owns one ordered `rooms` collection and one active `encounter` object:

`MAIDEN -> BLOAT -> NYLOCAS -> SOTETSEG -> XARPUS -> VERZIK -> TREASURE`

- `enterPassage` resolves a loc against the player's current source room and accepts only the next room.
- `enterChallengeBarrier` can prepare a `READY` room once and only activates normal combat rooms once. Verzik is the deliberate exception: barrier entry prepares the seated room; Talk-to activates it.
- `completeRoom` changes state to `COMPLETE` before clearing NPCs/hazards and exposing the next forward interaction.
- The central raid tick owns encounter timing. There are no `Thread.sleep`, detached wall timers, or per-spider boss timers to leak across rooms.

## Timing

- Server cycle: 600 ms.
- Vasilias deadline: 17 cycles after wave 3 begins. That is 10.2 seconds on the 600 ms tick grid—the first authoritative cycle not earlier than 10.0 seconds.
- Vasilias form rotation: 10 cycles.
- Xarpus recovery deadline: 34 cycles. That is 20.4 seconds on the 600 ms tick grid—the first authoritative cycle not earlier than 20.0 seconds.
- Xarpus healing: exactly 4 HP per active-exhumed healing event.

The requested decimal durations cannot be represented exactly by whole 600 ms game cycles. The implementation deliberately rounds up so neither event fires early. This must be measured in the client before calling the visual timing acceptance complete.

## IDs used or newly covered

### Objects/items/NPCs

- Xarpus forward door `32751`; Verzik-to-treasure passage `32738`; shared formidable passage `33113`; challenge barrier `32755`.
- Skeleton/Dawnbringer object `32741`; Dawnbringer item `22516`; seated Verzik `14795`.
- Vasilias melee/ranged/magic: Entry `10787/10789/10788`, Normal `8355/8357/8356`, Hard `10808/10810/10809`.
- Incoming small: Entry `10774–10776`, Normal `8342–8344`, Hard `10791–10793`.
- Incoming large: Entry `10777–10779`, Normal `8345–8347`, Hard `10794–10796`.
- Fighting small: Entry `10780–10782`, Normal `8348–8350`, Hard `10797–10799`.
- Fighting large: Entry `10783–10785`, Normal `8351–8353`, Hard `10800–10802`.

### Nylocas sequences/projectiles

- Attack sequences: magic `7989`, ranged `7999`, melee `8004`.
- Standard death sequences: magic `7991`, ranged `7998`, melee `8005`.
- Large/detonating death sequences: magic `7992`, ranged `8000`, melee `8006`.
- Projectiles/spotanims: magic `1580`, ranged `1560`.

### Dawnbringer special

- Cast/projectile/impact spotanims: `1546/1547/1548`.
- Cast sequence remains the cache-native `seq.human_casting` mapping already used by the project.

The active revision-240 cache/gameval reverse mappings and sequence skeleton/name inspection were used. Public mechanics were checked against the OSRS Wiki Theatre, Nylocas, Vasilias, Sotetseg, Xarpus, Verzik, Dawnbringer, and strategy pages. No donor animation ID was blindly copied.

## Files changed in this pass

- `content/minigames/theatre-of-blood/src/main/kotlin/org/rsmod/content/minigames/tob/TobDomain.kt`
- `content/minigames/theatre-of-blood/src/main/kotlin/org/rsmod/content/minigames/tob/TobRules.kt`
- `content/minigames/theatre-of-blood/src/main/kotlin/org/rsmod/content/minigames/tob/TobConfig.kt`
- `content/minigames/theatre-of-blood/src/main/kotlin/org/rsmod/content/minigames/tob/TobCombat.kt`
- `content/minigames/theatre-of-blood/src/main/kotlin/org/rsmod/content/minigames/tob/DawnbringerSpecialAttack.kt`
- `content/minigames/theatre-of-blood/src/main/kotlin/org/rsmod/content/minigames/tob/TobWorld.kt`
- `content/minigames/theatre-of-blood/src/main/kotlin/org/rsmod/content/minigames/tob/TobRaidManager.kt`
- `content/minigames/theatre-of-blood/src/test/kotlin/org/rsmod/content/minigames/tob/TobRulesTest.kt`
- `or-cache/src/main/kotlin/dev/openrune/tools/NpcDeathAnimationResolver.kt`
- `docs/raids/tob/tob-finish-report-2026-08-16.md`

## Automated/build/deployment verification

- `:content:minigames:theatre-of-blood:cleanTest :content:minigames:theatre-of-blood:test :or-cache:test`: **PASS**.
- ToB tests: **26 run, 0 failed, 0 errors, 0 skipped**.
- Coverage includes ordered room/progression rules, no barrier double-start, mode IDs, Nylocas large-form split mapping, pillar route adjacency, Nylocas wave/boss state, tick conversion, Sotetseg maze state, Xarpus/Verzik state transitions, reward rates, and party-board slots.
- `:server:app:build -x :server:app:spotlessKotlinCheck`: **PASS** in 53 seconds.
- `:or-cache:buildCache`: **PASS**; LIVE and SERVER models/configs, 37 DB tables, and 18 server config groups packed.
- Rebuilt SERVER dat2 SHA-256: `E8893F0C0F92EDA855D31743518B5A36F625937CA9043F14872EE704EC1F2A72`.
- Rebuilt LIVE dat2 SHA-256: `22E1D74423DD616A42EA60B689029C024B12AE47423118DAE09702F40ADA65E6`.
- Controlled boot 1: **PASS**, PID `21236`, port `43594`, revision `240`, custom-build marker, 335 scripts, ready in 14.249 seconds.
- Controlled boot 2: **PASS**, PID `31784`, port `43594`, revision `240`, custom-build marker, 335 scripts, ready in 14.328 seconds. This is the live server left running.

## Required acceptance matrix

`IMPLEMENTED/AUTOMATED` means server state/rules have direct test or build coverage. `LIVE CLIENT PENDING` means the feature is implemented but visual/interactive proof still requires RSProx.

| # | Acceptance item | Result |
|---:|---|---|
| 1 | Fresh Nylocas pillars intact | IMPLEMENTED/AUTOMATED; LIVE CLIENT PENDING |
| 2 | Small Nylocas route from lanes | IMPLEMENTED/AUTOMATED route/adjacency; LIVE CLIENT PENDING |
| 3 | Face and visibly attack pillars | IMPLEMENTED; LIVE CLIENT PENDING |
| 4 | Every pillar attack displays/deals 0 | IMPLEMENTED; LIVE CLIENT PENDING |
| 5 | Pillars never lose HP/fall | IMPLEMENTED/AUTOMATED state; LIVE CLIENT PENDING |
| 6 | Measure wave-3 timer | LIVE CLIENT PENDING |
| 7 | One Vasilias at +10 seconds before clear | IMPLEMENTED/AUTOMATED one-shot deadline; LIVE CLIENT PENDING (17-tick grid note above) |
| 8 | Starts gray/white melee | IMPLEMENTED/AUTOMATED ID/style; LIVE CLIENT PENDING |
| 9–14 | Correct damage-only style in all three forms | IMPLEMENTED authoritative hit modifier; LIVE CLIENT PENDING for six attack cases/delayed hits |
| 15 | Correct retaliation/death visuals | Cache mappings rebuilt; LIVE CLIENT PENDING |
| 16 | Vasilias completes room once | IMPLEMENTED/AUTOMATED guards; LIVE CLIENT PENDING |
| 17–18 | Backward entry blocked/no respawn | IMPLEMENTED/AUTOMATED progression; LIVE CLIENT PENDING |
| 19–21 | Sotetseg facing/retargeting from all sides | IMPLEMENTED; LIVE CLIENT/PARTY PENDING |
| 22 | Sotetseg maze unaffected | IMPLEMENTED/AUTOMATED maze generation; LIVE CLIENT PENDING |
| 23–26 | Xarpus 20-second recovery, 4 HP events, stop once | IMPLEMENTED/AUTOMATED state; LIVE CLIENT PENDING (34-tick grid note above) |
| 27–30 | Kill Xarpus, remain in room, manual forward exit | IMPLEMENTED progression; LIVE CLIENT PENDING |
| 31–35 | Staging, skeleton, unique Dawnbringer, manual barrier | IMPLEMENTED inventory/worn/floor guards; LIVE CLIENT PENDING |
| 36–40 | Fresh pillars, inactive seated Verzik, Talk-to starts once | IMPLEMENTED/AUTOMATED state guards; LIVE CLIENT PENDING |
| 41–43 | Complete phases 1, 2, and 3 exactly once | IMPLEMENTED phase machine; LIVE COMBAT PENDING |
| 44–48 | No respawn, manual rewards, one claim, no re-entry | IMPLEMENTED claim/progression guards; LIVE CLIENT PENDING |
| 49–51 | Fresh second raid and no leaked tasks/NPC/state | IMPLEMENTED fresh session/central tick cleanup; LIVE REPEAT/PARTY PENDING |

## Remaining proof required

Do one complete fresh raid in RSProx, then a second fresh raid, and one two-player run. Record the 51 items above, especially the exact visible Nylocas/Xarpus timing, all Nylocas animations, Sotetseg facing from four sides, every Verzik phase, duplicate Dawnbringer/chest attempts, logout/rejoin, and post-raid cleanup. Until that succeeds, this report intentionally does not label the task as fully live-accepted or 1:1 visual parity.
