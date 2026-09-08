# Arceuus RSProx evidence audit — 7 September 2026

This audit uses public OSRS packet captures, with the `.bin` downloads converted locally to `.txt` by the installed RSProx `TranscribeCommandKt -name` command. It does not treat OpenRune recordings or server implementation code as evidence of OSRS behavior. Older captures identify behavior; the revision-240 cache check separately identifies current assets. Cache paths and revision were not changed by this audit.

## Reproduce and inspect

- Discovery: `scripts/arceuus-search-captures.ps1`, following the existing Cerberus discovery script's public `POST https://rsprox.net/api/search` route. Queries are saved as JSON under `logs/arceuus-audit-20260907`; batches of more than five conditions returned HTTP 400, so discovery uses at most five per request.
- Download/transcription: `scripts/arceuus-transcribe-captures.ps1`. The public source route is `https://rsprox.net/download/<submission>`. It preserves downloaded binaries and converted text under `logs/arceuus-audit-20260907/captures`.
- Provenance/excerpts: `scripts/arceuus-export-evidence.ps1` writes `capture-manifest.json` with SHA-256 hashes, byte sizes, source URLs, and transcript revision/world headers, plus annotated excerpts.
- Focused extraction: `scripts/arceuus-capture-excerpt.ps1 -Capture 3153 -From 22396 -To 22439`. Its line numbers are one-based; `tick=` is the most recent enclosing RSProx tick header. Tick differences come from header values, not from counting displayed headers; empty ticks can be omitted.
- Native assets: `ArceuusNativeAssetsAuditTest` is a read-only cache inspection, checking native binary mappings separately from custom RSCM additions, current sequence data, client NPC models, and the client scripts that write Mark/Ward timer variables.

The transcriber initially reported a shared RSProx rolling-log error. The audit uses a separate console-only Log4j configuration in `scripts/arceuus-transcriber-log4j2.xml`; no running proxy was stopped. Historic cache hydration belongs to RSProx's own `.rsprox/caches` directory and does not alter OpenRune's `.data/cache/SERVER` or `.data/cache/LIVE`.

## Capture coverage

| Submission | Revision / world | What the inspected transcript proves |
|---|---|---|
| [3153](https://rsprox.net/download/3153) | 232.1 / normal members world | Two owner casts at 99 Magic: greater ghost and greater skeleton, precise spawn/buff/cooldown/expiry ordering and separate player/NPC graphics. |
| [4037](https://rsprox.net/download/4037) | 237.1 / world 344, Pest Control | Another player's greater skeletal thrall uses the raised spawn and new attack sequence; ranged projectiles recur every four ticks while attacking. |
| [4030](https://rsprox.net/download/4030) | 237.1 / Pest Control | Another player's greater ghost uses the raised spawn animation. No owner active/cooldown packets. |
| [1693](https://rsprox.net/download/1693) | 226.1 / normal members world | Greater ghost attack at spawn +4 and again four ticks later, using the older animation family; projectile source and target packets are explicit. |
| [2278](https://rsprox.net/download/2278) | 228.1 / normal members world | Another player's greater ghost movement. It supplies no local-owner lifecycle state. |
| [1672](https://rsprox.net/download/1672) | 226.1 / world 481, Nex FFA | Reanimated dragons and horrors near the Dark Altar. The world's advertised activity does not identify the player's actual location. |
| [4102](https://rsprox.net/download/4102) | 237.1 / world 367, normal members | Two owner Death Charge casts expire exactly 100 ticks after casting; base one-charge state and native graphic/sound. |
| [3340](https://rsprox.net/download/3340) | 235 / Grid Master: Forestry | Existing local transcript: upgraded two-charge Death Charge state transitions and denial text. Seasonal context is retained; it is not used as ordinary-world balance evidence. |

Additional downloaded search candidates were 3098, 3082, 2734, 418, 3131, 3137, 768, 446, and 1084. These are preserved in the manifest but are not claimed as proof of the utility casts being sought. Several text matches were books containing “reanimate” or “thrall”; the candidate returned for a numeric Vile Vigour animation search contained no actual sequence 8978 cast in its transcript. A database search result alone is insufficient evidence of a cast.

## Resurrection: owner-visible timeline

Submission 3153, `rsprox-3153.txt`, lines 22396–23078, greater ghost at Yanille, current and base Magic both 99:

| Tick | Offset | Observed packets |
|---|---:|---|
| 3855 | 0 | `if_buttonx` component 218:190; `arceuus_resurrection_cooldown` 12290 = 1; `arceuus_resurrection_used` 12412 = 6. |
| 3855 | 0 | Caster at (2544,3096,0): sequence 8973, cast spotanim 1873. NPC 10880 added at (2543,3096,0): sequence 9047, impact spotanim 1903. Sound 5063. Prayer 99 → 93; Magic XP +88. |
| 3860 | +5 | `<col=ff289d>You resurrect a greater ghostly thrall.</col>`; `arceuus_resurrection_active` 12413 = 1. |
| 3872 | +17 | Cooldown 12290 clears. |
| 3957 | +102 | NPC 10880 deleted. |
| 3958 | +103 | `<col=ff289d>Your greater ghostly thrall returns to the grave.</col>`; active 12413 clears; sound 5054. |

The second cast, greater skeleton, reproduces these offsets at another location. Lines 23547–23788: cast 4019, active/message 4024, cooldown clear 4036, NPC delete 4121, active/message clear 4122. Used varbit becomes 7, caster spotanim is 1874, NPC spawn sequence is 9048, and NPC impact spotanim is 1904. The NPC initially appears one tile south of the player. Both examples show the client-visible NPC before the active buff indicator.

These captures support 17 ticks for cooldown and, at 99 Magic, NPC presence until cast +102 and an owner expiry notification at +103. Expressing those two observed expiry offsets as Magic +3 and Magic +4 is an implementation inference from two same-level examples, not a multi-level measurement. These examples do not distinguish base from boosted Magic for lifetime. Used values 6 and 7 support greater ghost/skeleton identities; lesser/superior/zombie values were not observed in owner packets.

The buff/message delay must not itself be treated as proof of the first combat tick. Submission 1693 lines 38547–38986 has another player's ghost added at tick 2719, first attack at 2723 (+4), and another attack at 2727. It uses old sequence 5540 and projectile 1907, with starttime 15, endtime 40, source (1437,9679,0), and an explicit NPC target at (1434,9678,0). The newer revision-237 sample does not expose enough of the spawn interval to measure the earliest possible attack.

## Current thrall assets and attack cadence

Submission 4037, lines 14165–14167, tick 592: greater skeletal NPC 10883 is added with impact spotanim 1904 and sequence `skeleton_update_thrall_spawn_raised` 13684. At tick 594 the observing player moves 16 tiles from its location and receives an NPC deletion. The same index and creationcycle return at tick 622. That deletion is visibility loss, not thrall expiry.

The same capture emits projectile `thrall_ranged_travel` 1906 at ticks 601, 605, 609, 613, 617, and then 623, 627, 631, 635 while attacking. Observed attack sequence: `skeleton_update_champion_attack_thrall` 13672. Projectile packets carry starttime 57, endtime 72–82 depending on distance, angle 15, progress 32, startheight 100, endheight 124. This verifies four-tick attacks during uninterrupted attack runs, not a fixed first-attack delay or a universal maximum range.

The final comparison found the shared `magic_spell` template uses height 172, start 51, angle 16, progress 64 and end `46 + 10 × distance`. These contradict the skeleton observations. Captured one/two/three-tile shots end at 72/77/82: lines 14470–14472, 14997–14999, and 14381–14383 respectively. The dedicated skeleton correction uses end `67 + 5 × distance`, with start 57, heights 100/124, angle 15 and progress 32. Source index remains zero, matching these coordinate-sourced packets. The runtime regression inspects the actual emitted MapProjAnimV2 for all three skeleton tiers at all three measured distances. Ghost fields are not extrapolated from skeleton captures.

Submission 4030 lines 21829–21831 uses greater ghost 10880 with `ghost_update_thrall_spawn_raised` 13683. Native revision-240 binary mappings name ghost attack `ghost_update_tendrill_attack_thrall` 11101, skeleton attack 13672, and zombie attack `zombie_update_attack_normal_thrall` 13681. The earlier spawn 9047/9048 and attack 5540/5512/5568 IDs still existing in cache does not establish that they fit the current NPC models. Current model and sequence details are printed by the native asset audit.

The corrected native audit passed: `:or-cache:test --tests dev.openrune.ArceuusNativeAssetsAuditTest`, `BUILD SUCCESSFUL in 13s`, one test with zero failures. Evidence: `logs/arceuus-audit-20260907/native-assets-test.log` and the Gradle XML report. The official `NPCCodec(240)` confirms the following client definitions; all referenced models exist in LIVE, and the SERVER standing/walking IDs match.

| Kind / NPC IDs | Model | Standing / walking | Current spawn | Current attack |
|---|---:|---|---|---|
| Ghost 10878–10880 | 41988 | 894 / 10792 | 13683 `ghost_update_thrall_spawn_raised` | 11101 `ghost_update_tendrill_attack_thrall` |
| Skeleton 10881–10883 | 41989 | 13670 / 13671 | 13684 `skeleton_update_thrall_spawn_raised` | 13672 `skeleton_update_champion_attack_thrall` |
| Zombie 10884–10886 | 41985 | 13675 / 13676 | 9046 `zombie_update_thrawl_spawn` (native spelling) | 13681 `zombie_update_attack_normal_thrall` |

The model audit uses the official decoder and supports recursive transform definitions. The nine present definitions themselves have direct models and no transforms. Earlier test-only parsing assumptions about opcode order/model encoding failed and were replaced with the official decoder; those failures were not missing model assets.

## Native buff script arguments

`scripts/ArceuusBuffScriptAudit.java` reads only the local LIVE script index and data sectors, checks JS5 sector identities and decompressed lengths, and decodes CS2 instructions plus switch tables. It runs with the already installed JDK source launcher and does not need Gradle, dependencies, or downloads. Output is saved in `logs/arceuus-audit-20260907/native-buff-script-audit.txt`. This resolves argument semantics; it is not evidence of a captured Mark/Ward cast.

Native script 5926 loads its two integer arguments, invokes procedure 5927, then returns. Procedure 5927 switches on its first argument:

| Effect | First argument | Second argument / effect |
|---|---:|---|
| Corruption cooldown | 3119 | Start clock → client integer 515 |
| Mark of Darkness | 3120 | Duration in game ticks → client integer 982; sets start integer 516 from current `varp.map_clock` 3079 |
| Shadow Veil cooldown / active | 3121 / 3122 | Start clock → integers 517 / 518 |
| Death Charge cooldown / active | 3123 / 3124 | Start clock → integers 519 / 520 |
| Ward of Arceuus cooldown / active | 3125 / 3126 | Start clock → integers 521 / 522 |
| Resurrection cooldown / active | 3127 / 3128 | Start clock → integers 523 / 524; active also computes duration into integer 1147 via procedure 1934 |

The native timer reader 5923 handles Mark by calling procedure 5940 with `(start516, duration982)`. Procedure 5940 computes exactly `duration - (map_clock3079 - start)`. Mark's duration argument is therefore in game ticks, not seconds or client frames. A zero duration clears its displayed remaining time. Ward's active reader calls the same procedure with `(start522, base Magic level)` using opcode 3306, stat 6; a zero start disables that reader. Ward's cooldown reader uses varbit 12293 multiplied by 50. Shadow Veil's active display also uses base Magic.

Procedure 1934, used for resurrection's displayed duration, reads **current** Magic (opcode 3305, stat 6), doubles it if procedure 6931 called with argument 5 returns 1, and returns the result. Reader 5923 subtracts three from cached duration 1147 before calling 5940. The doubling condition is identified by its exact procedure/argument; its reward meaning needs separate verification. This confirms the client timer uses boosted Magic, while the captures above do not separately measure boosted NPC lifetime.

The clock is server-sent: clean capture 4102 increments `varp.map_clock` 3079 from 343174 at tick 39 to 343175 at tick 40 and 343176 at tick 41. OpenRune already has an authoritative map-clock service. Mark/Ward calls must share that clock's units and origin.

No explicit 5926/5927 packet is present in the inspected owner thrall/Death Charge cast windows; those spells send native active/cooldown varbits. Native listener scripts 635 and 5931 invoke procedure 5927, supporting the existing client listener path. Calling 5926 explicitly for Mark/Ward is an inference from the native wrapper and reader bytecode, not a captured packet claim, and is not evidence that all spells require explicit wrapper calls.

The native binary mappings contain no `hitmark` table. A string named `corruption=2974` belongs to other gameval data and is not proof of a hitsplat ID. The custom `hitmark.corruption=0` placeholder is not validated by this audit, and the installed RuneLite runtime API does not expose a separate HitsplatID constant class. No replacement corruption hitsplat ID is claimed.

## Reanimation observations and limits

The public database NPC search for all IDs 7018–7039 returned only submission 1672. Its header is revision 226.1, world 481, normal membership flags 1. The actual observed locations are near the Dark Altar, independently of the world's “Nex FFA” activity label.

Examples in that transcript:

- Tick 129, line 3834: reanimated dragon 7039 added at (1706,3883,0); deletion at tick 143, line 3941.
- Tick 150, line 4022: another dragon added at (1705,3883,0).
- Tick 157, line 4090: reanimated horror 7031 added at (1707,3880,0), while the dragon is present.
- Further dragon/horror additions occur across x1705–1710, y3880–3883.

The simultaneous NPCs are not proof that one caster may maintain multiple reanimations. Owner inputs are not established in these inspected windows. No captured, freshly dropped head was observed being reanimated away from the Dark Altar. That behavior requires another source or a dedicated recording; these captures cannot validate provenance lifetime, movement radius, trade/bank invalidation, or exact delay from the owner's click to the NPC addition.

## Death Charge

Normal-world revision-237 submission 4102, lines 8077–8094: tick 41 sends component 218:188, cooldown 12138 = 1 and active 12411 = 1, sequence 8970, spotanim 1854, sound 5034, and Magic XP +90. The message says the next foe's death restores some special attack energy. At tick 141 (+100), both cooldown and active clear and the expiry message arrives. Cast tick 152 expires at 252, reproducing 100 ticks with no kill proc.

The player's configured message color changes within the recording, so color differences alone are not different spell semantics. The literal captured message is `Upon the death of your next foe, some of your special attack energy will be restored.` and expiry is `Your Death Charge has faded away.`

Existing seasonal submission 3340, `%USERPROFILE%/.rsprox/binary/Old School RuneScape/yama-3340.txt`, lines 56842–58033: upgraded cast tick 3114 gives active 12411 = 2, cooldown = 1, sequence 8970, upgraded spotanim 3288, sound 5034. Kills at 3140 and 3178 decrement active 2 → 1 → 0, each with a restoration message. Casting at 3154 reports `You already have a Death Charge pending on you.` Casting at 3185 and 3209 reports `You can only cast Death Charge every 60 seconds.` Cooldown clears 3214 (+100). This is observed upgraded-state encoding; the clean revision-237 sample above is the ordinary-world cooldown proof.

## Remaining evidence gaps

- No owner cast for all nine resurrection variants, boosted-vs-base lifetime comparison, or current-revision first-attack measurement.
- No dedicated owner reanimation input sequence away from the Dark Altar.
- No verified packet sequence establishing Vile Vigour's Prayer consumption, Mark/Ward cast and expiry timing, corruption PvP drain/hitsplat behavior, offering selection/order, teleport arrival, or Resurrect Crops outcomes. Mark/Ward client timer arguments are separately decoded above.
- The native cache can identify client variables and animation models; it does not by itself establish server mechanics. Missing RSProx evidence must stay distinct from independently verified gameplay tests and external documented behavior.
