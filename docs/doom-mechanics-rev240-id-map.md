# Doom of Mokhaiotl rev-240 semantic ID map

The supplied `rsprox-3340-rev235.bin` is the behavioral authority. IDs were accepted only after the same semantic name was resolved in OpenRune's revision-240 cache. The capture was made on a Leagues world, so League-only player graphics are excluded.

| Semantic | rev-235 ID | Verified rev-240 ID | Type | Evidence | Code location |
|---|---:|---:|---|---|---|
| Doom / shielded / burrowed | 14707 / 14708 / 14709 | same | NPC | RSProx spawn and transmog names; rev-240 cache names | `DoomConfig.kt` |
| Neutral / ranged / magic / melee larvae | 14710 / 14711 / 14712 / 14713 | same | NPC | RSProx spawn names; rev-240 cache names | `DoomConfig.kt` |
| Giant ranged / magic larvae | 14788 / 14789 | same | NPC | RSProx delve-8 spawn names; rev-240 cache names | `DoomConfig.kt` |
| Acid / descend hole / rock | 57283 / 57285 / 57286 | same | location | RSProx loc-add names; rev-240 cache names | `DoomConfig.kt` |
| Idle / standard / rock throw | 12405 / 12406 / 12407 | same | sequence | RSProx sequence names; rev-240 cache names | `DoomConfig.kt` |
| Beam charge / loop / cancel / fire | 12408 / 12409 / 12410 / 12411 | same | sequence | RSProx charge timelines; rev-240 cache names | `DoomConfig.kt` |
| Area charge / loop / slam / recover | 12412 / 12413 / 12414 / 12415 | same | sequence | RSProx named sequence events; rev-240 cache names | `DoomConfig.kt` |
| Tongue / burrow move / emerge / explode / burrow / idle / despawn | 12416–12422 | same | sequence | RSProx named sequence events; rev-240 cache names | `DoomConfig.kt` |
| Larva spawn / death | 12458 / 12459 | same | sequence | RSProx larva lifecycle; rev-240 cache names | `DoomConfig.kt` |
| Hole spawn | 12477 | same | location sequence | RSProx post-death location animation; rev-240 cache name | `DoomConfig.kt` |
| Burrow move / emerge / explode / area / burrow / despawn | 3371–3375 / 3377 | same | spotanim | RSProx named graphics; rev-240 cache names | `DoomConfig.kt` |
| Standard melee / magic / ranged | 3378 / 3379 / 3380 | same | projectile graphic | RSProx projectile names; rev-240 cache names | `DoomConfig.kt` |
| Rock launch ranged / magic; split ranged / magic | 3384 / 3385 / 3386 / 3387 | same | spotanim/projectile | RSProx rock timeline; rev-240 cache names | `DoomConfig.kt` |
| Rock looming shadow | 2380 | same | map spotanim | RSProx `gargboss_debris_shadow_90_small`; rev-240 startup asset validation | `DoomConfig.kt`, `DoomEncounterManager.kt` |
| Rock debris 01–08 / impact | 3388–3395 / 3404 | same | projectile/spotanim | RSProx eight-projectile split; rev-240 cache names | `DoomConfig.kt` |
| Area slam 01–03 | 3405–3407 | same | map spotanim | RSProx delve-8/9 arena waves; rev-240 cache names | `DoomConfig.kt` |
| Beam head / middle / end / charge / impact / burrow charge | 3409–3414 | same | projectile/spotanim | RSProx five-segment beam packets; rev-240 cache names | `DoomConfig.kt` |
| Charge bar | 81 | 36 (`headbar.charge_100`) | NPC headbar | RSProx headbar 81 with 390/510/600 durations; rev-240 semantic gameval/cache mapping | `DoomConfig.kt`, `DoomEncounterManager.kt` |
| Burrow target / target spawn / larva spawn / larva-player impact | 3415 / 3416 / 3417 / 3426 | same | spotanim | RSProx car and larva events; rev-240 cache names | `DoomConfig.kt` |
| Acid splashes N/E/S/W / projectile | 3429–3432 / 3445 | same | spotanim/projectile | RSProx acid landing events; rev-240 cache names | `DoomConfig.kt` |

IDs 14714, 14715, 57284, and acid graphics 3427–3444 exist in rev 240 and are used by mechanics described by the current Wiki, but those exact entities were not present in the supplied capture. They remain Wiki/cache-backed, not packet-verified.
