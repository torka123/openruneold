# Tombs of Amascut donor-route implementation report

## Current status

OpenRune now contains a native revision-240 Tombs of Amascut module built around the supplied Reason/Kronos donor's raid flow. The production route is command-free: players use the grouping obelisk, native ToA party screens, raid-start object, path objects, boss crystals, completion spirits, Wardens passage, and reward sarcophagi.

This report does not claim that every encounter is visually 1:1 until the complete route is exercised in the revision-240 client. Compilation, deterministic rules, cache mappings, object routing, and startup asset guards are verified.

## Donor and cache decision

- User-supplied archive: `%USERPROFILE%\OneDrive\Desktop\osrs server.rar`
- Current archive SHA-256: `823B1C8C37D08D2395850D9312A8FF4D865BDF16800F9CF240519088EDCC28CE`
- Donor evidence: Reason/Kronos build 229.1, not revision 231.
- Destination: OpenRune revision 240.
- Donor/cache references audited: 305.
- Unresolved revision-240 dependencies: 0.
- Cache edits or copied donor cache bytes: none.
- Active `SERVER` and `LIVE` caches remain separate and unchanged.

The donor contains the lobby, party interfaces, four direct boss routes, Wardens, supplies, deaths, and rewards. It does not contain the four OSRS puzzle/challenge-room implementations. The OpenRune route therefore matches the donor's direct-boss structure instead of inserting unrelated placeholder puzzles.

The donor's custom interface IDs `1117`, `1118`, `1119`, `1120`, `1121`, and `1124` are not present in the revision-240 cache and were not copied into it. The donor party behavior is bound to revision-240's native ToA party list `772`, party details `774`, invocations `776`, and mid-raid loot `777`, using the cache's native client scripts.

## How players enter

1. Interact with grouping obelisk `46068`.
2. Create or join a party in the native ToA party UI, following the donor's party flow.
3. Configure invocations in the native ToA invocation UI.
4. Interact with raid-start object `46089`.
5. Select a path using the donor path object.
6. Use that room's crystal to start its boss encounter.

There is no normal `::toa`, `::toaparty`, `::toainv`, `::toasupply`, or room-skip command. Starting without an obelisk-created party is rejected. The only retained command is administrator-only `::toadev status|reset`; it cannot enter, start, or skip any raid room.

## Party and raid lifecycle

- Native revision-240 party list, party details, applicants, and invocation screens implementing the donor party behavior.
- Donor maximum of six players.
- Leader-controlled party creation, applicant acceptance/rejection, invocation selection, and raid start.
- Private per-party raid instance with isolated NPC ownership, damage validation, progression, and rewards.
- Rejoin support for disconnected participants.
- Solo death ends the run; team deaths spectate until the room resolves or the team wipes.
- Wipes reset the active encounter unless the configured attempt limit ends the raid.
- Abort, completion, and instance teardown remove raid NPCs, pending attacks, temporary supplies, UI state, and membership safely.

## Donor route and encounters

- Lobby routes directly to Kephri, Ba-Ba, Zebak, or Akkha.
- A path cannot be selected twice after completion.
- The party leader establishes the current room; members may only enter that same room.
- Crystals start boss combat only for the selected room and prevent ghost entry.
- Completion spirit returns the party to the lobby after each path boss.
- All four paths are required before Wardens.
- Wardens advance through native phase definitions and unlock the reward passage only after the final phase.
- Helpful Spirit supplies use the donor Life, Chaos, and Power bundles with invocation-scaled limits.

### Combat controllers

- Kephri: shield recovery phases, bomb/egg tile attacks, scarab overlords, Medic healing, and Kephri invocation timing/radius changes.
- Zebak: native ranged/magic projectiles, acid-area attacks, multi-target behavior, and Zebak invocation timing/radius changes.
- Ba-Ba: boulder phases, slam and sarcophagus attacks, baboon add support, and Ba-Ba invocation changes.
- Akkha: melee/ranged/magic form rotation, special tile attacks, shadow thresholds, final orb phase, and Akkha invocation changes.
- Wardens: native projectile/floor cycles, phase advancement, scaled damage, and Warden invocation changes.
- Delayed tile attacks are dodgeable; tracking attacks resolve on their intended participant.
- Boss/add targets and attacks are rejected across parties, instances, rooms, spirits, and non-combat hazards.

## Main native IDs

### Locations

- Grouping obelisk: `46068`
- Raid start: `46089`
- Kephri paths: `46155`, `46156`; crystal `45505`
- Ba-Ba paths: `46158`, `46159`; crystal `45754`
- Zebak paths: `46161`, `46162`; crystal `45506`
- Akkha paths: `46164`, `46165`; crystal `45866`
- Wardens path: `46168`; crystal `45579`
- Reward passage: `45138`
- Bank: `46223`
- Lobby return objects: `45129`, `45144`, `45543`, `45844`, `46055`
- Leave raid: `45128`
- Reward sarcophagi: `46220`, `46218`, `44547`, `44545`, `29994`, `46216`, `46215`, `46219`, `46217`

### NPCs

- Kephri: `11719`
- Zebak: `11730`
- Ba-Ba: `11778`
- Akkha base form: `11790`
- Wardens phases: `11769`, `11753`, `11759`, `11763`
- Completion Spirit: `11689`
- Helpful Spirit: `11694`
- Reward-room Osmumten: `11693`

## Files

- Runtime module: `content/minigames/tombs-of-amascut/`
- Cache audit tools: `or-cache/src/main/kotlin/dev/openrune/audit/toa/`
- Cache audit registration: `or-cache/build.gradle.kts`
- Evidence and reports: `docs/raids/toa/`
- Donor reproduction evidence: `tools/reason231-raid-import/toa/`

No ToB, CoX, login service, RSProx file, cache archive, or unrelated gameplay module was replaced.

## Automated verification

- `:content:minigames:tombs-of-amascut:test`: 14 tests, 0 failures, 0 errors.
- `:server:shared:classes`: successful with ToA on the shared runtime classpath.
- `:or-cache:auditToaCaches`: successful; 305 mappings and 0 unresolved dependencies.
- Source audit: no normal ToA entry/start/party/supply command and no command-exposed room-skip helper.
- Startup validation guards all required revision-240 objects, NPCs, sequences, spot animations, rewards, supplies, and party interfaces.

## Live acceptance checklist

Before calling the raid visually 1:1, test the complete route in the client:

1. Obelisk party creation, applications, six-player cap, and invocation buttons.
2. Start object, all eight path object variants, all five crystals, and lobby-return spirits.
3. Each boss's projectiles, tile warnings, dodge timing, movement, phases, adds, and death transition.
4. Wardens progression, reward passage, sarcophagus claim, bank, and raid exit.
5. Solo death, team spectating, wipe reset, disconnect/rejoin, simultaneous clicks, and instance cleanup.
