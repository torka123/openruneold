# The Leviathan evidence worksheet

## Sources

- Decoded transcript `yama-3285.txt`, capture 3285, revision 234 (Leagues): Leviathan evidence
  begins around cycle 17349 and includes seven kills, with the final fight explicitly Awakened.
- OpenRune revision-240 cache: source region 8291, NPC/loc/sequence definitions.
- Current OSRS Wiki `The Leviathan/Strategies`: attack tiers, maximum hits, stun, specials,
  enrage and pathfinder rules.
- Current RuneLite generated gamevals: names for sequences and spot animations. RuneLite has no
  encounter helper that supplies authoritative Leviathan server timing.

## Confirmed protocol and cache evidence

| Mechanic | Evidence | Implemented contract |
|---|---|---|
| Room | rev-240 map cache | Copy region 8291 on plane 0. |
| Entry handholds | rev-240 map cache plus transcripts 788 and 3285 | Loc 47593 at (2070,6368,0). The player uses animation 828, telejumps one tile east to local (23,32), then remains under player control. No sequence 829 occurs in either captured Leviathan entry. |
| Combat start | transcripts 3285 cycles 17351-17356 and 788 cycles 23583-23588 | The player walks north from the inside entry. The copied revision-240 room blocks the old z=40 stair tile, so this implementation starts on the last reachable tile (local z=39) before that boundary; the server must not teleport the player to the middle. The capture still records the boss appearing as the player reaches local z=40 in the live room. |
| Exit | rev-240 map cache and transcript 3285 | Both rowboat forms 49213 and 49214 belong to the room lifecycle. The public Travel destination is not established by this transcript. |
| NPC | rev-240 cache | Normal NPC 12214, 900 HP. NPC 12215 is a 720-HP quest form and is not used as the awakened form. |
| Spawn/death | RSProx and rev-240 sequence cache | Spawn 10291 (3 ticks), death 10293 (6 ticks). Respawn is 36 ticks from the death sequence in the observed capture. |
| Ranged orb | RSProx normal attacks and RuneLite gamevals | Cast spotanim 2484, projectile 2487, start animation 10278. |
| Magic orb | RSProx normal attacks and RuneLite gamevals | Cast spotanim 2486, projectile 2489, start animation 10278. |
| Melee orb/bite | RuneLite gamevals and Wiki | Orange orb 2485/2488; adjacent bite alternates 10283/10284. |
| Volley | transcript 3285 | Six attacks precede roar. Orb styles vary; deterministic modulo rotation is not capture-faithful, while seeded randomness is retained for tests. |
| Roar/debris | transcript 3285 sequence 10282 and map animations 2475-2480 | Each roar has 12-36 transient 2475/2476 effects with client delays 20/50/80. Persistent 2477-2480 pieces use delay 50; normal captures show one or three, while the Awakened fight shows larger formations. |
| Lightning | rev-240 cache names plus Wiki only | The transcript contains no encounter packets for sequences 10285-10287 or graphics/projectile 2493/2494/2503/2504/2506. Current/strike behavior is implemented from documented mechanics, but exact projectile parameters remain unverified. |
| Smoke | rev-240 cache names plus Wiki only | The transcript contains no encounter packets for sequences 10288-10290. Ten one-per-tick, two-tick-land boulders and cover rules are Wiki-derived; exact Awakened upper damage and packet timing remain unverified. |
| Shadow stun | current Wiki plus rev-240 cache | The 25%-during-stun reset and player attack-delay reset are documented behavior, not observable in transcript 3285. |
| Enrage | transcript 3285 plus Wiki | Pathfinder 12220 follows a fixed clockwise route and never chases. In the Awakened fight, enrage/tornado begins at 17963 and Pathfinder appears at 17986, establishing a 23-cycle protected transition gap. |
| Cleanup | OpenRune instance events | Leave button, command, rowboat, external teleport, logout, death and disconnect remove the boss, pathfinder, tornado, hazards and membership. |

## Inferences and limitations

- Transcript 3285 is revision 234 and was captured during Leagues. Player damage, hit values and relic
  cadence are not reused as canonical values; only boss-side visible ordering/assets are treated as evidence.
- The transcript contains seven Leviathan kills. The final fight is explicitly Awakened despite an older
  ledger row labelling it normal. Terrain/map events span all planes; they are not evidence for changing
  the arena's intended plane-0 gameplay surface.
- Lightning and Smoke exact packet timing are absent. Their named mechanics remain isolated as
  Wiki/cache-derived behavior and must not be described as packet-replay verified.
- No current RuneLite plugin implements the server state machine for this encounter. Generated
  gamevals were useful for asset naming only.
- The cache has no separate awakened Leviathan NPC definition. Awakened debug mode therefore uses
  NPC 12214 with the configured 2,700 HP and awakened rule values instead of the incorrect quest NPC
  12215.

## Automated coverage

- `Dt2RulesTest` and `LeviathanRulesTest`: volley tiers, randomized seeded styles, stun resets,
  Pathfinder policies/transition gap, roar delay/count bounds, lightning prohibited tiles and tornado cooldown.
- `Dt2CaptureReplayTest`: region, NPC, handhold, rowboat, projectile and respawn contracts.
- `LeviathanR240CacheAuditTest`: exact rev-240 handhold/rowboat coordinates and sequence names.
- `InstanceLogoutTabPolicyTest`: Leave Instance UI is enabled without raid death/retention semantics.
