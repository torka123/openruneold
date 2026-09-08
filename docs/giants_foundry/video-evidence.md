# Giants' Foundry video evidence

Research date: 2026-08-28
Target: current Old School RuneScape behaviour, with historical footage used only where its age is stated.

## Method and limitations

Public YouTube metadata, descriptions, chapters, and captions were used to locate relevant sequences. Captions are navigation aids, not proof of a number that is only visible in the game client. Exact tiles, pixels, animation IDs, sound IDs, heat values, progress values, and tick boundaries require frame-by-frame review or a reproducible live observation.

The in-app video browser was unavailable in this research environment because its Windows sandbox helper failed during startup. Consequently:

- timestamps below identify the relevant sequence but do not claim frame-accurate boundaries;
- RuneLite/base-client status is `unknown` unless the title or narration explicitly identifies a plugin;
- a video described as continuous is not certified as uncut without a frame-level discontinuity review;
- no tick value has been inferred from narration alone;
- plugin overlays are never specified as native revision-240 interfaces.

## Coverage matrix

| Requested evidence | Best source | Coverage | Remaining gap |
|---|---|---|---|
| Sleeping Giants/tutorial | Bubba OSRS, 2022-06-08 | Complete first-time repair, crucible, mould, processing, and hand-in sequence | Edited quick guide; an independently verified uncut tutorial is still needed |
| Two complete normal commissions | TheEdB0ys one-hour recording, 2022-06-11 | Two assignment-to-hand-in sequences at 00:30-06:50 and 07:01-12:20 | Presented as a continuous hour, but cuts could not be ruled out frame-by-frame |
| Mistakes/suboptimal play | TheEdB0ys one-hour recording | Early running, missed sweet spot, and quality-loss commentary are present | Exact quality delta and tick of each mistake are not legible from captions |
| Mould interface | 10Boot 2025; Patyfatycake 2022 | Interface use and mould selection are narrated | Widget IDs, button packets, and exact sort order need live/cache verification |
| Reward shop purchases | 10Boot 2025; Patyfatycake 2022 | Shop and reward items are shown/discussed | A complete current purchase/buyback transaction was not proven from accessible evidence |
| Material loading/equipment conversion | Patyfatycake 2022; 10Boot 2025; PaniikOSRS 2025 | Crucible loading and equipment conversion are demonstrated or narrated | Exact inventory deltas must use Wiki rules and deterministic tests |

## Source catalogue

### VID-01 - current general guide

- URL: https://www.youtube.com/watch?v=dK_8_gMx0kA
- Title: `Giants Foundry Guide (2025) – Fast Smithing XP & Profitable Training!`
- Channel: 10Boot OSRS
- Publication: 2025-03-02
- Duration: 6:25
- Editing: edited; narration explicitly says the later process is sped up at about 03:29.
- Client/overlays: not verified frame-by-frame.
- Version warning: predates the 2025-05-07 bonus-action quality-recovery change.

| Time | Observation | Evidentiary use |
|---:|---|---|
| 01:45 | Commission is requested. | Assignment lifecycle. |
| 01:46-02:09 | Mould jig is used and the crucible is filled. | Mould/material workflow only. |
| 02:08-02:18 | Metal is poured and the preform is taken to the mould. | State transition ordering. |
| 02:19-03:24 | Processing and quality are explained. | Qualitative tool/temperature guidance; no exact tick proof. |
| 03:27-04:56 | A sword is processed; the latter portion is sped up. | Not suitable for timing tests. |
| 04:56-05:07 | Sword is handed in. | Completion lifecycle. |
| 05:07-05:37 | Reward points, mould purchases, uniform, and double-ammo mould are discussed. | Shop discovery only; not proof of a purchase packet or buyback. |

### VID-02 - current quick guide

- URL: https://www.youtube.com/watch?v=VV642fe3yqM
- Title: `QUICK and EASY Giant's Foundry Guide - OSRS | 2025`
- Channel: PaniikOSRS
- Publication: 2025-03-08
- Duration: 3:31
- Editing: edited quick guide.
- Client/overlays: not verified.
- Version warning: predates the 2025-05-07 bonus-action quality-recovery change.

| Time | Observation | Evidentiary use |
|---:|---|---|
| 00:58 | Crucible quantities and material choice are discussed. | Material-loading corroboration. |
| 01:16-01:31 | Commission and mould selection are shown. | Mould-interface workflow. |
| 01:31-01:38 | Crucible, pour, and preform pickup are shown. | State ordering. |
| 02:43-02:51 | Hand-in and reward currency are shown. | Completion/reward workflow. |
| 02:53-03:08 | Mould and reward choices are discussed. | Shop catalogue corroboration, not transaction proof. |

### VID-03 - post-update RuneLite guide

- URL: https://www.youtube.com/watch?v=vQy1HmHO0cA
- Title: `*UPDATED* Giant's Foundry OSRS Runelite Plugin | Easiest Way to TRAIN + PROFIT Smithing (IRONMAN)`
- Channel: DaviScape OSRS
- Publication: 2025-10-10
- Duration: 6:26
- Editing: edited explanatory guide.
- Client/overlays: explicitly RuneLite/plugin-assisted.
- Version use: current enough to include the May 2025 quality-recovery era; plugin presentation must be excluded from the base-client specification.

| Time | Observation | Evidentiary use |
|---:|---|---|
| 00:42 | A mistake is discussed. | Qualitative failure evidence. |
| 00:60-02:18 | Materials, collected sword, shop-bought moulds, and mould selection are discussed. | Lifecycle and unlock corroboration. |
| 03:06-04:08 | Commission, mould, and crucible workflow. | Current lifecycle corroboration. |
| 04:33 | Reward shop. | Catalogue discovery only. |
| 05:49 | Reduced quality is discussed. | Confirms quality can be lost; does not establish amount. |
| 06:03-06:16 | Hand-in/right-click hand-in. | Completion interaction corroboration. |

### VID-04 - current 2026 quick guide

- URL: https://www.youtube.com/watch?v=WPk1f74T4kM
- Title: `Giants Foundry Quick Guide OSRS (2026)`
- Channel: Dehsow
- Publication: 2026-01-30
- Duration: 3:27
- Editing: edited quick guide.
- Client/overlays: not verified.

| Time | Observation | Evidentiary use |
|---:|---|---|
| 01:09 | Mould ratings/rewards are discussed. | Current mould workflow corroboration. |
| 01:59-02:16 | Ask Kovac, receive commission, choose mould, load crucible, pour, and collect preform. | Current lifecycle ordering. |
| 02:23 | Quality line is referenced. | Presence only; no numeric extraction. |
| 03:20-03:25 | Hand-in and rewards. | Current completion corroboration. |

### VID-05 - long-form commissions and mistakes

- URL: https://www.youtube.com/watch?v=j9VJg3hdPXE
- Title: `OSRS 1 Hour of Giant's Foundry | New Smithing Minigame Examples`
- Channel: TheEdB0ys
- Publication: 2022-06-11
- Duration: 1:02:54
- Editing: presented as one hour of play; frame-level continuity was not available for verification, so `uncut` remains provisional.
- Client/overlays: unknown without frame review.
- Version warning: launch-week footage. It predates the 2022-06-15 and 2022-06-29 fixes and the 2025-05-07 quality-recovery change. Use it for the broad lifecycle and historical comparison, not current sweet-spot behaviour.

#### Complete commission A

| Time | Observation |
|---:|---|
| 00:30 | Narrow/Heavy commission already assigned. |
| 00:36-01:20 | Mould selection. |
| 01:45-01:58 | Crucible loading and pour. |
| 02:11 | Player says they ran too early, an observed suboptimal action. |
| 01:58-06:45 | Processing sequence. |
| 06:50 | Hand-in; narration reports final quality 182. The visible value was not independently read. |

#### Complete commission B

| Time | Observation |
|---:|---|
| 07:01-07:03 | Next commission requested; Narrow/Heavy is reported. |
| 07:10 | Mould selection. |
| 07:24 | Narration reports 14 of each material. |
| 07:35 | Player starts leaving before pouring and corrects the mistake. |
| 07:55-12:13 | Processing sequence. |
| 12:20 | Hand-in; narration reports 182 quality and 521 in a second field. The second field is not assigned a semantic meaning here. |

#### Further mistake evidence

| Time | Observation | Caution |
|---:|---|---|
| 11:41-11:51 | Quality loss is discussed; narrator estimates eight. | Commentary is not an authoritative formula. |
| 12:28-17:25 | A third Broad/Flat commission, reported quality 195. | Useful end-to-end corroboration. |
| 15:42-16:07 | Player worries about a boundary but reports no loss. | Exact temperature/quality values unavailable. |
| 22:24-22:31 | A sweet spot is missed and visible quality loss is described. | Exact delta and tick unavailable. |
| 23:08 | Hand-in, reported quality 183. | Narrated, not independently read. |
| 23:52 | Player leaves before pouring. | Suboptimal lifecycle action. |

### VID-06 - full guide with a complete sword segment

- URL: https://www.youtube.com/watch?v=QYkIOdTTkKA
- Title: `OSRS Giants Foundry Guide | New Smithing Method Full Guide`
- Channel: TheEdB0ys
- Publication: 2022-06-22
- Duration: 15:19
- Editing: edited/explanatory; contains a continuous-looking full-sword chapter from about 04:34 to 08:25, but it is not certified uncut.
- Client/overlays: unknown.
- Version warning: after the 2022-06-15 update, before 2022-06-29 and later changes.

Key timestamps: requirements 00:31; commission 01:03; mould 01:10; crucible 01:53; equipment conversion 02:06; material score 02:26; interface/tools 02:55; full sword 04:34-08:25; reward shop 12:46.

### VID-07 - detailed mould/material/reward guide

- URL: https://www.youtube.com/watch?v=f7ayLvyeQ-8
- Title: `The Ultimate Giants' Foundry Guide - OSRS`
- Channel: Patyfatycake
- Publication: 2022-12-29
- Duration: 16:52
- Editing: edited guide.
- Client/overlays: unknown.

Key timestamps: interface 01:56; commission 05:36; mould selection 05:41-06:30; crucible 06:46; equipment conversion 07:04; pour 07:26; processing 08:12; hand-in 09:02; rewards/shop 09:11; moulds/materials 09:21-13:51.

The equipment segment corroborates the general conversion behaviour. Exact conversion amounts are taken from the current Wiki because the caption is ambiguous.

### VID-08 - Sleeping Giants tutorial walkthrough

- URL: https://www.youtube.com/watch?v=B0uIiddFuFY
- Title: `Sleeping Giants Quick Quest Guide - (Giants’ Foundry Quest)`
- Channel: Bubba OSRS
- Publication: 2022-06-08
- Duration: 9:53
- Editing: edited quick guide; therefore it does not satisfy the strict uncut-tutorial requirement.
- Client/overlays: unknown.

| Time | Observation |
|---:|---|
| 01:48 | Talk to Kovac. |
| 01:55-02:06 | Repair polishing wheel, mould jig, and grindstone. |
| 03:07 | Inspect crucible. |
| 04:40-05:16 | Fill crucible and return to Kovac. |
| 05:34-06:13 | Mould selection, pour, and pickup. |
| 06:20-09:29 | Processing, quality warning, and hand-in. |

### VID-09 - official historical context

- URL: https://www.youtube.com/watch?v=n-HxC1K8CCc
- Title: `Giants' Foundry Q&A with Mods Sarnie, Dylan and Oasis! | OSRS Q&A June 9th`
- Channel: Old School RuneScape
- Publication: 2022-06-09
- Editing: official livestream/Q&A recording.
- Use: design intent and launch clarification only. Current behaviour must be checked against later official updates/current Wiki.

Official text summary: https://oldschool.runescape.wiki/w/Update%3AQ%26A_Summary_09/06/2022

## Video-derived scenarios for later differential testing

These scenarios define observable ordering, not exact tick assertions.

1. `VIDEO-COMMISSION-A`: request commission -> choose matching mould -> load exactly 28 equivalents -> pour -> collect preform -> process through every segment -> hand in -> immediately request another commission.
2. `VIDEO-EARLY-LEAVE`: load materials -> leave the crucible without pouring -> return -> pour. The player's materials and unfinished state must remain coherent.
3. `VIDEO-MISSED-BONUS`: miss a bonus action during processing. Current behaviour must be measured after the 2025-05-07 change; launch-week quality-loss commentary is not a current oracle.
4. `VIDEO-EQUIPMENT-CONVERSION`: load eligible smithable equipment and verify each inventory removal and bar-equivalent contribution against the current conversion table.
5. `VIDEO-SHOP`: hand in a sword, open the shop, purchase and buy back an eligible item, then attempt the excluded buybacks. A current transaction capture is still required as a visual oracle.

## Unresolved video work

- Capture or locate a demonstrably uncut Sleeping Giants/tutorial run.
- Frame-review VID-05 to confirm that its two commission ranges contain no cuts or time compression.
- Capture a current base-client mould interface without RuneLite overlays.
- Capture a current reward-shop purchase and buyback, including point deltas and confirmation behaviour.
- Record current post-May-2025 sweet-spot recovery with readable quality/progress values and a tick counter.
- Record exact station animation/sound events and the heat/progress values before and after each machine tick.

Until those are available, tests must use the current Wiki formulas and reproducible revision-240/live observations rather than guessing from captions.
