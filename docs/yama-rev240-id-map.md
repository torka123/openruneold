# Yama revision-240 asset map

The runtime gate is `YamaVoiceR240CacheTest`. It opens `.data/cache/LIVE` as revision 240 and fails if a live definition is absent or misnamed. The supplied capture is revision 235; equal numbers below were independently resolved in revision 240 and are not assumed by continuity.

## NPCs and instance map

| Semantic name | Recorded rev-235 ID | Target rev-240 ID | Type | Validation source | Target code location |
|---|---:|---:|---|---|---|
| Yama throne, morph parent | 123 | 123 | NPC | capture `yama_throne`; r240 name/options/morph audit | `YamaConfig.kt` `THRONE_PARENT` |
| Yama seated/occupied | 124 | 124 | NPC | capture morph; r240 cache | `THRONE_OCCUPIED` |
| Yama seated/unoccupied | 14157 | 14157 | NPC | r240 cache and empty-throne model | `THRONE_EMPTY` |
| Yama normal | 14176 | 14176 | NPC | capture and r240 name `Yama` | `BOSS` |
| Yama phase three | 14176 | 14176 | NPC state | r240 has no distinct phase-three definition; movement/stat/state is server controlled | `spawnBoss`, `returnFromJudge` |
| Yama, all contract variants | 14176 | 14176 | NPC state | r240 has no contract-specific morph; profile supplies HP/stats/rules | `YamaRules.profile`, `spawnBoss` |
| Void Flare | 14179 | 14179 | NPC | capture and r240 name | `VOID_FLARE` |
| Judge of Yama | 14180 | 14180 | NPC | capture and r240 name | `JUDGE` |
| Throne/statistics imp | not observed | 14181 | NPC | r240 cache | reserved `IMP`; scoreboard sequence remains a capture gap |
| Meteor helper | 14182 | 14182 | NPC/helper | capture entity stream and r240 cache | `METEOR` |
| Fire surge raised | 13507 | 13507 | NPC/helper | capture exact-move stream and r240 cache | `FIRE_SURGE`; visual map animation used by runtime |
| Voice of Yama morph parent | 7668 | 7668 | NPC | r240 morph varbit 16284 | `VOICE_PARENT` |
| Voice: Talk-to | 14183 | 14183 | NPC | r240 options | `VOICE_TALK` |
| Voice: Travel | 14184 | 14184 | NPC | r240 options | `VOICE_TRAVEL` |
| Voice: Talk/Travel/Join | 14185 | 14185 | NPC | r240 options | `VOICE_JOIN` |
| Domain source template | region 6045 | region 6045 | map region | capture coordinates and r240 map load | `YAMA_REGION`, `yamaArea()` |
| Domain entry | (1503,10050,0) | source-local (31,2) | coordinate | capture entry teleport | `yamaArea()` |
| Throne | (1500,10089,0) | source-local (28,41) | coordinate | capture NPC add | `travel()` |
| Yama spawn | (1501,10076,0) | source-local (29,28) | coordinate | capture NPC add | `spawnBoss()` |
| Outside Voice | (1439,10079,0) | (1439,10079,0) | coordinate | corrected entrance spawn; command lands five tiles south at (1439,10074,0) | `YAMA_VOICE_SPAWN`, `YAMA_OUTSIDE` |

## Locations

| Semantic name | Recorded rev-235 ID | Target rev-240 ID | Type | Validation source | Target code location |
|---|---:|---:|---|---|---|
| Judge stepping stone | 56246 | 56246 | location | capture option `Jump`; r240 option | `STEPPING_STONE` |
| Arena firewall/exit | 56264 | 56264 | location | capture `Pass`/`Quick-pass`; r240 options | `FIREWALL` |
| Throne scenery | 56265 | 56265 | location | r240 cache/model | `THRONE_LOC` |
| Glyph of Shadow | 56335 | 56335 | location | capture model/state; r240 cache | `GLYPH_SHADOW` |
| Glyph of Fire | 56336 | 56336 | location | capture model/state; r240 cache | `GLYPH_FIRE` |
| Inactive glyph | 56337 | 56337 | location | capture `floorkit_summoning03_inactive` | `GLYPH_INACTIVE` |
| Shadow glyph deactivate | 56338 | 56338 | location | r240 examine: depleted shadowy energy | `GLYPH_SHADOW_DEACTIVATE` |
| Fire glyph deactivate | 56339 | 56339 | location | r240 examine: depleted fiery energy | `GLYPH_FIRE_DEACTIVATE` |
| Five glyph tiles | capture coordinates | local (25,36), (23,29), (30,24), (37,29), (35,36) | coordinates | decoded capture | `spawnGlyphs()` |
| West Judge route | capture coordinates | local (7,29), (7,31), (8,34), (9,37), (7,40), (8,43), (7,46), (8,48) | coordinates | decoded capture | `judgePath()` |
| East Judge route | not present in solo recording | mirror `x=58-x` | coordinates | Wiki duo rule; r240 map geometry | `judgePath()` |

## Sequences

All target definitions below are present in revision 240 and are startup-validated before scripts register.

| Semantic name | Rev-235 | Rev-240 | Type | Validation | Code |
|---|---:|---:|---|---|---|
| Yama idle | 12140 | 12140 | sequence | r240 archive | `ANIM_IDLE` |
| Yama walk | 12141 | 12141 | sequence | r240 archive | `ANIM_WALK` |
| standard cast | 12144 | 12144 | sequence | capture/r240 | `ANIM_STANDARD` |
| special cast | 12145 | 12145 | sequence | capture/r240 | `ANIM_SPECIAL` |
| melee | 12146 | 12146 | sequence | capture/r240 | `ANIM_MELEE` |
| Judge portal | 12147 | 12147 | sequence | capture/r240 | `ANIM_PORTAL` |
| stomp | 12148 | 12148 | sequence | capture/r240 | `ANIM_STOMP` |
| player portal | 12149 | 12149 | sequence | capture/r240 | `ANIM_PLAYER_PORTAL` |
| player portal-facing | 12152 | 12152 | sequence | capture/r240 | `ANIM_PLAYER_PORTAL_FACE` |
| throne summon | 12154 | 12154 | sequence | capture tick 197/r240 | `ANIM_THRONE_SUMMON` |
| Yama summon | 12156 | 12156 | sequence | capture tick 199/r240 | `ANIM_SUMMON` |
| Yama despawn/death | 12161 | 12161 | sequence | capture/r240 | `ANIM_DESPAWN` |
| throne respawn | 12163 | 12163 | sequence | capture/r240 | `ANIM_THRONE_RESPAWN` |
| flare idle/death/spawn/explode | 12133-12136 | 12133-12136 | sequences | r240 archive | `ANIM_VOID_FLARE_*` |
| Judge death | 9042 | 9042 | sequence | capture/r240 | `ANIM_JUDGE_DEATH` |
| stepping-stone jump | 12196 | 12196 | sequence | capture exact-move/r240 | `ANIM_SHORT_JUMP` |

## Graphics and projectile visuals

OpenRune currently emits the recorded projectile visuals through spot-animation/map-animation APIs; there is no separate live Yama projectile-definition wrapper in this module. IDs are nevertheless independently present in revision 240.

| Semantic group | Rev-235 | Rev-240 | Type | Validation | Code |
|---|---:|---:|---|---|---|
| shadow cast/impact | 3243-3244 | 3243-3244 | spotanim | capture/r240 | `GFX_SHADOW_CAST`, `GFX_SHADOW_IMPACT` |
| fire cast/impact | 3246-3247 | 3246-3247 | spotanim | capture/r240 | `GFX_FIRE_CAST`, `GFX_FIRE_IMPACT` |
| flare explosion/player | 3238, 3242 | 3238, 3242 | spotanim | capture/r240 | `GFX_FLARE_*` |
| fireball cast/projectile/impact | 3253-3255 | 3253-3255 | spotanim/projectile visual | capture/r240 | `GFX_FIREBALL_*` |
| Judge post-cross shadow/projectile/impact | 1446, 2855, 2856 | 1446, 2855, 2856 | map spotanim/projectile | 3340 ticks 258/263; independently present in r240 | `GFX_JUDGE_FIREBALL_*` |
| shadow spike cast/projectile/impact | 3256-3258 | 3256-3258 | spotanim/projectile visual | capture/r240 | `GFX_SHADOW_SPIKE_*` |
| shadow root cast/projectile/impact | 3259-3261 | 3259-3261 | spotanim/projectile visual | capture/r240 | `GFX_SHADOW_ROOT_*` |
| Shadow Crash bow-tie impact | 3252 | 3252 | delayed map spotanim | `3347`/`3386` bow-tie tiers; independently present in r240 | `GFX_SHADOW_CRASH_IMPACT` |
| falling rock on player | 3262 | 3262 | spotanim | capture/r240 | `GFX_FALLING_ROCK_PLAYER` |
| shadow wall middle/end | 3265-3266 | 3265-3266 | map spotanim | capture/r240 | `GFX_SHADOW_WALL_*` |
| fire wall | 3267 | 3267 | map spotanim | capture/r240 | `GFX_FIRE_WALL` |
| meteor cast/projectile | 3270, 3272 | 3270, 3272 | spotanim/projectile visual | capture/r240 | `GFX_METEOR_*` |
| portal/fall/shadow | 3274-3276 | 3274-3276 | spotanim | capture/r240 | `GFX_PORTAL*` |
| throne/Yama summon | 3278-3279 | 3278-3279 | spotanim | capture tick 197/199; r240 | `GFX_THRONE_SUMMON`, `GFX_SUMMON` |
| fire/shadow immunity | 3280-3281 | 3280-3281 | spotanim slot 2 | capture/r240 | `GFX_*_IMMUNITY` |
| Voidflare yellow deadline | health-bar 75 | 31 | headbar | 3347 tick 1352: 0 to 30 over 600 client cycles; r240 gameval headbar.charge_30 and cache definition | VOID_FLARE_CHARGE_HEADBAR |
| Yama despawn | 3284 | 3284 | spotanim | capture/r240 | `GFX_DESPAWN` |
| throne respawn | 3286 | 3286 | spotanim | capture/r240 | `GFX_THRONE_RESPAWN` |

The capture uses hitsplat 16 and health-bar 20 for its client build. OpenRune uses its typed `HitType` and native boss-bar service rather than hard-coding those rev-235 protocol IDs. Sounds were observed but are not yet emitted by the module; they remain a validation gap.

## State, UI, and items

| Semantic name | Rev-235 | Rev-240 | Type | Validation | Code |
|---|---:|---:|---|---|---|
| Voice progress/morph | 16284 | 16284 | varbit | r240 morph definition | `VARBIT_VOICE_PROGRESS` |
| throne state | 16286 | 16286 | varbit | capture/r240 | `VARBIT_THRONE_STATE` |
| transition damage | 16549 | 16549 | varbit | capture/r240 | `VARBIT_TRANSITION_DAMAGE` |
| transition phase | 16550 | 16550 | varbit | capture/r240 | `VARBIT_TRANSITION_PHASE` |
| Dossier | 30803 | 30803 | item | r240 name/options | `DOSSIER` |
| noted pineapple pizza | 2302 | 2302 | item | r240 note-link/name | `CERT_PINEAPPLE_PIZZA` |
| Glyphic/Sensory/Bloodied/Divine/Forfeit contracts | 30810/30813/30816/30819/30822 | same | items | r240 exact names | `YamaContract` |
| Oathplate/Shard/Catalyst/Worm/Harmony/Familiar contracts | 30825/30828/30831/30834/30837/30840 | same | items | r240 exact names | `YamaContract` |
| Oathplate helm/body/legs | 30750/30753/30756 | same | rewards | r240 exact names | `deliverRewards` and drop table |
| Soulflame horn | 30759 | 30759 | reward | r240 exact name | Yama drop table |
| Oathplate shards | 30765 | 30765 | reward | r240 exact name | `deliverRewards` and drop table |
| Yami pet | 30888 | 30888 | reward | r240 exact name/model | `deliverRewards` |
| Purifying sigil left/right/middle/top/bottom | 30783/30785/30787/30789/30791 | same | guaranteed contract rewards | r240 exact names and models | `YamaAssets.SIGIL_*`, `deliverRewards` |
| Voice death storage | not a cache definition | persistent item triples + fee | server state | OpenRune death-storage hook and OSRS fee tiers | `YamaDeathStorage.kt` |
| native entry dialogue | no standalone widget ID | no standalone widget ID | server dialogue | r240 NPC options and dialogue API | `YamaWorld.kt` |
| boss health bar | capture HUD interface 303 | native boss-bar service | interface state | rev-240 client/service | combat hit integration |

Revision-240 has no separate normal/phase-three/contract Yama NPCs, no contract board widget, and no distinct duo map. These are deliberate server states over the verified definitions above, not missing numeric guesses.
