# Arceuus reanimation and RuneLite audit — 2026-09-07

This audit follows the authoritative spell catalog, server cast events, ground-item routing, NPC death attribution, and the installed RuneLite 1.12.38 timer plugin. Revision 240 and the existing SERVER/LIVE cache paths are unchanged.

## Confirmed repairs

- Reanimation now has one handler, `ArceuusReanimationSpellScript`, for inventory and ground targets. The old registration and implementation were removed from the utility spell script.
- Dog heads belong to Adept Reanimation. The catalog now includes hellhound heads: 26996/26997, NPC 11463, Expert Reanimation, 1,200 Prayer XP. All 23 species and 46 unnoted variants are represented; notes are excluded.
- Casting awards the four live spell definitions' Magic XP rather than discontinued individual-head spell XP. Basic/Adept/Expert/Master use levels 16/41/72/90 and grant 32/80/138/170 XP. Their rune costs are respectively 4 body + 2 nature; 4 body + 3 nature + 1 soul; 1 blood + 3 nature + 2 soul; 2 blood + 4 nature + 4 soul. These match the [Arceuus spellbook table](https://oldschool.runescape.wiki/w/Arceuus_spellbook), [Adept Reanimation](https://oldschool.runescape.wiki/w/Adept_Reanimation), and [Expert Reanimation](https://oldschool.runescape.wiki/w/Expert_Reanimation).
- The previously absent `OpObjT` packet route now validates the selected interface and target mask, finds a visible ground item, and uses the existing object interaction reach/visibility checks before dispatching its targeted event. The cast rechecks exact ground-item identity before consuming runes and one head.
- Genuine NPC drops record their original coordinate in the fresh head's item variables. Inventory copies, manual drops, pickups, and split ground piles preserve that origin. Reanimation outside the Dark Altar accepts only fresh heads within the original 31×31 square on the original floor. Banking and economic-container insertion normalize fresh heads to their tradeable form and clear provenance. Merely spawning an originless fresh ID does not grant local eligibility. The location rule and its indefinite duration are documented by [Adept Reanimation](https://oldschool.runescape.wiki/w/Adept_Reanimation).
- The NPC spawns at the original cast impact location. A player's living reanimation blocks another cast; only its owner may attack it; Prayer XP is awarded once.
- Reanimations suppress item drops while retaining attributed NPC kill hooks. This restores delivery to the existing Slayer hook without generating ordinary remains or table loot. Thralls retain complete reward suppression. Task eligibility continues to use the authoritative Slayer rules.

## RuneLite compatibility evidence

The installed `%USERPROFILE%/.runelite/repository2/client-1.12.38.jar` was inspected with `javap -p -constants`. Its Timers & Buffs plugin recognizes specific coloured chat suffixes for resurrection, Shadow Veil, Ward, and Mark. The original server strings lacked the closing colour token, and the Mark message used different wording. These differences prevent timer creation even when the server effect itself works.

The [official TimersAndBuffsPlugin source](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/timersandbuffs/TimersAndBuffsPlugin.java) also observes resurrection, Death Charge, corruption, Shadow Veil and Ward cooldown/active varbits. Buff settings are display preferences and must remain separate from effect state. RuneLite overlay failure alone does not establish an NPC despawn: the server's actual NPC lifecycle and movement logs were investigated separately by the main audit.

## Verification

`ArceuusReanimationRuntimeTest` contains nine focused tests using real revision-240 spell definitions, rune transactions, registered spell events, actual NPC creation and a manual clock:

1. Independent expected levels, Magic XP and rune costs for all four tiers.
2. Inventory casts for all 46 variants, correct NPC, resource consumption, cast XP, owner restrictions and one Prayer award.
3. Ground casts for all 23 fresh heads away from the altar.
4. Fresh inventory origin boundaries at ±15/±16 tiles and another floor.
5. Originless, tradeable and bank-normalized heads rejected away from the altar without consumption.
6. Notes, wrong tier, low Magic, missing runes and wrong spellbook rejected.
7. Active reanimation and stale ground-item rejection.
8. Split ground piles preserve original owner and provenance.
9. The actual NPC death reward boundary publishes attributed kill hooks while suppressing remains, and preserves normal/full-suppression behavior.

The companion economic-storage tests cover bank preview/commit, partial deposit, bulk insertion, withdrawal permanence, and ordinary inventory transfer. The main audit owns the combined Gradle run, controlled deployment, and two-restart runtime verification. Test execution results are recorded there; this document does not substitute source inspection for a client casting pass.
