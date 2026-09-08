# Arceuus spellbook audit — 2026-09-07

For the subsequent full gameplay audit, reanimation implementation and current verification, see [Full Arceuus audit](arceuus-full-audit-20260907.md). The findings and restart IDs below document earlier deployments.

The live server now allows Arceuus spells without quest completion. The change removes the quest requirement from the spell definitions; it does not complete quests or change player quest variables. Magic level, runes, Prayer, Book of the Dead possession, target restrictions, cooldowns, and teleport restrictions still apply.

The cache revision remains 240. The existing `.data/cache/SERVER` and `.data/cache/LIVE` paths remain authoritative. Original spell records are backed up under `.data/audit-backups/arceuus-access/{LIVE,SERVER}/item-<id>.bin` (26 records per cache).

## Confirmed defects and repairs

| Defect | Evidence | Repair |
| --- | --- | --- |
| Quest locks persisted in the client despite quest-free server checks | Live cache obj param `spell_questreq_struct` is 81 on 24 spells, 9 on West Ardougne and 67 on Harmony Island. Decoded client proc 4128 reads this param and bypasses the quest branch when it is -1. | Set only this param to -1 on the 26 affected Arceuus objects in both caches. The normal cache builder invokes the installer. A second installation changed 0 definitions in both caches. |
| Combat and utility casting throws during sound lookup | Real-cache test reproduced `IllegalStateException: Missing mapping '5026' in table 'synth'`. The corresponding sound bytes exist. Source calls reverse lookup during casts and expiry. | Added all 31 missing mappings used by existing Arceuus casts, impacts, summons and expiry. Checked every mapped sound against actual LIVE cache bytes. |
| Thrall attempts to fire between different floors | Real runtime test reproduced `IllegalArgumentException` after the owner and target changed floor while the thrall remained behind. | Remove summons outside the owner's floor or leash before targeting or firing; clear abandoned routes. |
| Thralls survive owner death or can retain stale hits after departure/replacement | Deterministic runtime tests failed for death, teleport and summon replacement. | Clear state on dead/disconnected owners, track target NPC identity, and attach summon/owner/target validity to each delayed hit. |
| NPC damage ignored the existing generic hit-validity contract | Player hit processing checked `isValidNow()`; NPC hit processing did not. | Apply the same check at the start of standard NPC hit processing. Regression tests pass cancelled summon hits through the actual processor and confirm unchanged HP. Other hits retain their default valid behavior. |
| Thrall mode was unset and ordinary combat interaction was allowed | Runtime summon test failed its scripted-mode assertion. | Explicit `NpcMode.None`, ignore ordinary combat interactions, zero hunting range. All nine summons spawn, attack and expire in deterministic tests. |
| Ward/Mark casts changed the player's buff-display preferences | Tests reproduced writes to `buff_mark_of_darkness_disabled` and `buff_ward_of_arceuus_disabled` during cast/expiry. | Keep timed combat effects in their existing state service without mutating display preferences. |
| Reanimation rejected most freshly dropped unnoted head variants | Cache contains adjacent dropped/tradeable variants for all 22 heads; original lookup accepted only 23 of the 44 unnoted items. | Accept both unnoted variants of every head; validate each against the correct NPC name and exclude notes. |

The quest distinction matches the [OSRS Wiki Arceuus spellbook description](https://oldschool.runescape.wiki/w/Arceuus_spellbook); removal is an intentional OpenRune access policy. Asset availability and client lock behavior were established from the active cache, not inferred from that page.

## Coverage and remaining limits

Audited the existing seven target-spell definitions, ten utility spells, nine thrall spells, four reanimation tiers, thirteen Arceuus teleports, Resurrect Crops in Farming, and the minigame teleport registration: 45 catalog entries total. No duplicate spell handlers were added.

The new access test loads the real registry and rune repositories, checks all 45 entries on a questless player, rejects wrong spellbooks and insufficient levels, consumes actual rune requirements, and confirms missing runes still prevent casting. This is an access/consumption check, not an end-to-end client cast of every spell.

The eight new runtime tests cover all nine summons, seeded damage, manual-clock expiry, floor transitions, distant teleport, owner death, explicit logout cleanup, replaced summons, actual cancelled-hit processing, Death Charge idempotency/expiry, buff preferences, and both variants of every head. No sleeps or statistical assertions are used.

Existing behavior still requiring a separate parity pass:
- Reanimation is restricted to the Dark Altar area; location-qualified reanimation of a freshly dropped head elsewhere is not modeled.
- Thrall target acquisition is fed by the shared positive-damage contribution hook; acquisition on an initial miss is not proven by the runtime tests.
- Arceuus Home Teleport uses the existing short server teleport sequence rather than the full OSRS resting sequence/recharge behavior.
- Buff effect state is functional, but this change does not add missing native timed buff icons.
- No complete client-side casting/visual pass has been observed. Existing RuneLite logs also contain older client-script and HD/player-outline errors; these were not established as Arceuus failures and are not claimed fixed.

## Verification

| Module | Tests | Failures / errors / skipped |
| --- | ---: | --- |
| content:skills:magic:spell-attacks | 37 | 0 / 0 / 0 |
| content:skills:magic:spell-teleports | 3 | 0 / 0 / 0 |
| content:interfaces:spellbook | 4 | 0 / 0 / 0 |
| api:combat:combat-commons | 26 | 0 / 0 / 0 |
| api:npc | 5 | 0 / 0 / 0 |
| api:player | 16 | 0 / 0 / 0 |
| api:spells | 1 | 0 / 0 / 0 |
| content:skills:farming | 1 | 0 / 0 / 0 |
| api:combat:combat-formulas | 10 | 0 / 0 / 0 |
| content:bosses:cerberus | 52 | 0 / 0 / 0 |
| or-cache: Arceuus and Cerberus audits | 5 | 0 / 0 / 0 |
| Total | 160 | 0 / 0 / 0 |

Commands:
```powershell
.\gradlew.bat :or-cache:installArceuusSpellAccess --console=plain
.\gradlew.bat :content:skills:magic:spell-attacks:test :content:skills:magic:spell-teleports:test :content:interfaces:spellbook:test :api:combat:combat-commons:test :api:npc:test :api:player:test :api:spells:test :content:skills:farming:test --console=plain
.\gradlew.bat :content:bosses:cerberus:test :api:combat:combat-formulas:test :server:app:installDist --console=plain
.\gradlew.bat :or-cache:test --tests dev.openrune.ArceuusR240CacheAuditTest --tests dev.openrune.CerberusR240CacheAuditTest --console=plain
```

All final commands exited 0. Broad spell/module verification: BUILD SUCCESSFUL in 51s. Regression tests plus full distribution rebuild: BUILD SUCCESSFUL in 20s. Final real-cache audits: BUILD SUCCESSFUL in 30s. Initial failing regressions were repaired before deployment.

First controlled restart: PID 31476 -> 36012; build marker 06:21:35 EDT, server ready 06:21:37 EDT. Second controlled restart: PID 36012 -> 36844; build marker 06:23:41 EDT, server ready 06:23:43 EDT. Doctor exited 0 after both restarts and confirmed the canonical project directory, revision 240, and identical cache fingerprints across both restarts. Current live JVM: 36844.

Runtime logs: `logs/cerberus-audit-20260907/restart-11.stdout.log` and `restart-12.stdout.log`, with matching `.stderr.log` files. The existing controlled-restart helper was reused; these are the Arceuus deployment restarts. Neither startup logged an exception. `git diff --check` exited 0; only existing Windows line-ending notices were emitted.

## Modified source files

- `.data/gamevals/synth.rscm`
- `api/combat/combat-commons/src/main/kotlin/org/rsmod/api/combat/commons/magic/ArceuusSpellState.kt`
- `api/npc/src/main/kotlin/org/rsmod/api/npc/hit/processor/StandardNpcHitProcessor.kt`
- `content/skills/magic/spell-attacks/build.gradle.kts`
- `content/skills/magic/spell-attacks/src/main/kotlin/org/rsmod/content/skills/magic/spell/attacks/arceuus/ArceuusUtilityState.kt`
- `content/skills/magic/spell-attacks/src/main/kotlin/org/rsmod/content/skills/magic/spell/attacks/arceuus/ArceuusUtilitySpellScript.kt`
- `content/skills/magic/spell-attacks/src/test/kotlin/org/rsmod/content/skills/magic/spell/attacks/arceuus/ArceuusRulesTest.kt`
- `content/skills/magic/spell-attacks/src/test/kotlin/org/rsmod/content/skills/magic/spell/attacks/arceuus/ArceuusRuntimeTest.kt`
- `or-cache/build.gradle.kts`
- `or-cache/src/main/kotlin/dev/openrune/CacheTools.kt`
- `or-cache/src/main/kotlin/dev/openrune/ArceuusSpellAccessInstaller.kt`
- `or-cache/src/test/kotlin/dev/openrune/ArceuusR240CacheAuditTest.kt`
- `docs/arceuus-audit-20260907.md`

## Manual client verification

1. Fully close and reopen RuneLite so it receives the changed spell definitions. Open Arceuus on an account without quests; check the locked-spell filter and the previously locked combat/utility spells.
2. With adequate Magic/runes, cast each Grasp and Demonbane spell on a valid target; check animation, sound, hit and autocast. Verify Demonbane rejects non-demons.
3. Cast Mark, Ward, Shadow Veil, both Corruptions, Vile Vigour, Death Charge, Degrime and both Offerings with suitable targets/items/resources. Check effect, rune consumption, cooldown and expiry.
4. Carry Book of the Dead; summon each thrall tier/style, attack a monster, move nearby, change floor, teleport away, die, log out and resummon. Confirm old summons/hits do not remain.
5. Try both unnoted head variants at the Dark Altar and reject a noted head. Check the correct creature and Prayer experience.
6. Try each teleport, especially West Ardougne and Harmony Island without quests; test teleport denials in restricted areas. Check Resurrect Crops on a dead Farming patch.
7. Relog and confirm quest-free access persists. Report the exact spell and action if a client crash remains; distinguish client exit, disconnect and a rejected cast.

## Follow-up: quest lock icons still displayed

The original client installer appended a second opcode-249 parameter block. This left the original quest requirement in the same record; the map-based audit only saw the last value. A new raw-record assertion reproduced the defect on spell 8796 with quest values `[81, -1]`, despite its decoded map reporting no quest requirement. Evidence: `logs/arceuus-lock-regression-before.log`.

`ArceuusSpellAccessInstaller` now replaces every integer occurrence of the quest parameter in place and examines every Arceuus client record, including records whose last value is already -1. It preserves all other bytes and avoids adding another parameter block. The normal cache builder already calls this same installer. No quest progress, casting handler, cache revision or cache path was changed.

Installation repaired 26 LIVE spell records; SERVER already had the correct quest values and required zero edits. A second installation changed zero records in both caches. Four cache audit tests passed, including every raw quest value, native client quest-check instructions, byte preservation with duplicated/string/integer parameters, and repeat-install behavior. The server classes rebuild also passed. Logs: `logs/arceuus-lock-install.log`, `logs/arceuus-lock-reinstall.log`, `logs/arceuus-lock-unit-tests.log`, and `logs/arceuus-lock-tests-after.log`.

The client must be fully closed and reopened after deployment to reload item definitions. The in-game lock-icon display has not yet been observed after this follow-up.

Deployment passed controlled restarts 18 and 19, with JVM PIDs 25388 and 33308 and build markers at 18:05:57 and 18:07:02 EDT. Both bound port 43594; doctors confirmed the canonical working directory and stable corrected cache fingerprints across the two restarts. Revision remains 240. SERVER dat2 SHA-256 remains `899D421EE88ADE6CE7956EC2A13A09ADCC4882264D27B1CF2BC1CED42AC821E3`; the intentional LIVE item-record correction changes its dat2 SHA-256 to `FD0FE5EF66039A791A900EDDDF1CDBCFE57E09E8C32EA3496C69E96217A41A7B`. See `logs/arceuus-lock-doctor18.log`, `logs/arceuus-lock-doctor19.log`, and the corresponding restart stdout/stderr under `logs/cerberus-audit-20260907`. Each startup logged one cycle overrun (812 ms / 726 ms); no exception was observed in the startup checks. Final source and existing scoped Git diff/status were checked after deployment.
