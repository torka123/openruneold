# OpenRune Armour Audit and Implementation Report

Date: 2026-08-15

## Runtime and data sources

- Server revision: 240
- Client/protocol revision: 240
- LIVE cache revision: 240
- Runtime server cache: `.data/cache/SERVER`
- Client-definition cache: `.data/cache/LIVE`
- Server item-definition source: the revision-240 cache decoded by `ItemServerCodec`, with the project's raw server overlay
- Equipment statistics and requirements: `ItemServerType` definitions consumed by the existing worn-bonus and combat-formula systems
- Player appearance: revision-240 item wear models, appearance slots, recolours, retextures, and hidden-body-part definitions from the active cache
- Effect architecture: existing combat formula, hit impact, equipment-check, spell, charge, event, and plugin systems; no second equipment system was added

## Required completion counts

| Measure | Result |
|---|---:|
| Raw wearable item IDs audited | 2,523 |
| Normalized cache-name families audited | 1,733 |
| Direct cache/server definitions already correct | 2,523 |
| Stat corrections | 0 |
| Requirement corrections | 0 |
| Slot corrections | 0 |
| Model-definition corrections | 0 |
| Recolour/retexture corrections | 0 |
| Passive mechanic paths implemented or corrected | 12 |
| Set-family paths audited/corrected | 9 |
| Charge systems corrected | 0 |
| Degradation systems corrected | 0 |
| Cache files or archives changed | 0 |

The static-definition correction counts are zero because all 2,523 scoped server definitions already match the active revision-240 LIVE cache. A zero model-review count means every referenced definition was present at definition level; it is not a substitute for in-game visual testing on both body types.

## Gameplay mechanics implemented or corrected

1. Barrows set recognition now accepts the intact and 100/75/50/25 states and rejects broken/0 states.
2. Full and degraded Amulet of the damned variants are recognized consistently.
3. Verac's 1-in-4 proc bypasses Defence/armour and protection Prayer.
4. Guthan's 1-in-4 successful-hit heal works, including the damned overheal cap.
5. Torag's 1-in-4 successful PvP hit drains 20% run energy.
6. Karil's 1-in-4 successful PvP hit drains 20% Agility.
7. Karil plus Amulet of the damned has its separate 1-in-4 half-damage hit, with no duplicate XP award.
8. Ahrim uses one synchronized proc per successful cast for the Strength drain and damned damage bonus.
9. Dharok plus Amulet of the damned can recoil 15% of received non-typeless damage to the exact attacker without awarding XP.
10. Full Verac plus Amulet of the damned adds the required +7 Prayer bonus.
11. Blood Moon's Bloodrager performs two independent 1-in-3 successful-hit rolls, producing the OSRS 5-in-9 chance of the shortened next attack.
12. Eclipse Moon's burn is applied through the existing burn service with a 1-in-5 successful-hit roll, 10 damage, 40-cycle duration, and five-stack cap.
13. Blue Moon's Frostweaver recognizes bind, ice, and grasp spells and arms a one-use melee delay bypass in PvM and PvP; removing a required piece clears the state.

The report counts Ahrim's paired proc as one passive mechanic and Guthan's base/enhanced heal as one mechanic, producing 12 distinct passive paths despite the 13 implementation bullets above.

## Developer commands added

- `::armouraudit <itemId>`: prints slot, requirements, bonuses, weight, models, colours, state, effect family, and audit status.
- `::armourdiff`: regenerates the JSON, CSV, effect, and model reports.
- `::armourtestbank [slot]`: safely banks one valid raw test representative per normalized family and is safe to rerun.
- `::modeltest <itemId>`: adds/equips through the normal lifecycle and prints the appearance definitions.

All four commands use the existing command architecture and are administrator-only.

## Armour-task source files changed or added

- `or-cache/src/main/kotlin/dev/openrune/audit/ArmourAudit.kt`
- `server/app/src/main/kotlin/org/rsmod/server/app/ArmourAuditMain.kt`
- `server/app/build.gradle.kts`
- `content/other/commands/src/main/kotlin/org/rsmod/content/other/commands/ArmourAdminCommands.kt`
- `content/other/commands/build.gradle.kts`
- `api/player/src/main/kotlin/org/rsmod/api/player/worn/EquipmentChecks.kt`
- `api/player/src/main/kotlin/org/rsmod/api/player/bonus/WornBonuses.kt`
- `api/player/src/main/kotlin/org/rsmod/api/player/events/PlayerHitpointsChangedEvent.kt`
- `api/player/src/main/kotlin/org/rsmod/api/player/hit/processor/StandardPlayerHitProcessor.kt`
- `api/player/src/test/kotlin/org/rsmod/api/player/worn/EquipmentChecksUnitTest.kt`
- `api/player/src/integration/kotlin/org/rsmod/api/player/worn/EquipmentChecksTest.kt`
- `api/player/build.gradle.kts`
- `api/combat/combat-formulas/src/main/kotlin/org/rsmod/api/combat/formulas/accuracy/AccuracyOperations.kt`
- `api/combat/combat-formulas/src/main/kotlin/org/rsmod/api/combat/formulas/attributes/collector/CombatMagicAttributeCollector.kt`
- `api/combat/combat-formulas/src/main/kotlin/org/rsmod/api/combat/formulas/attributes/AhrimPassiveState.kt`
- `api/combat/combat-formulas/src/main/kotlin/org/rsmod/api/combat/formulas/attributes/FrostweaverState.kt`
- `api/combat/combat-commons/src/main/kotlin/org/rsmod/api/combat/commons/magic/MagicSpellChecks.kt`
- `api/combat/combat-scripts/src/main/kotlin/org/rsmod/api/combat/ArmourCombatEffects.kt`
- `api/combat/combat-scripts/src/main/kotlin/org/rsmod/api/combat/PvNCombat.kt`
- `api/combat/combat-scripts/src/main/kotlin/org/rsmod/api/combat/PvPCombat.kt`
- `api/combat/combat-scripts/src/main/kotlin/org/rsmod/api/combat/scripts/armour/ArmourRecoilScript.kt`
- `api/spells/src/main/kotlin/org/rsmod/api/spells/attack/SpellAttackManager.kt`
- `api/spells/build.gradle.kts`
- `content/other/special-weapons/src/main/kotlin/org/rsmod/content/other/special/weapons/melee/DualMacuahuitlWeapons.kt`
- `content/other/special-weapons/build.gradle.kts`

Generated reports and documentation:

- `docs/audit/armour/baseline/armour-audit.json`
- `docs/audit/armour/baseline/armour-audit.csv`
- `docs/audit/armour/baseline/armour-effect-audit.json`
- `docs/audit/armour/baseline/armour-model-audit.json`
- `docs/audit/armour/current/armour-audit.json`
- `docs/audit/armour/current/armour-audit.csv`
- `docs/audit/armour/current/armour-effect-audit.json`
- `docs/audit/armour/current/armour-model-audit.json`
- `docs/audit/armour/ARMOUR_IMPLEMENTATION_REPORT.md`
- `docs/audit/armour/TEST_RESULTS.md`
- `docs/audit/armour/MANUAL_VERIFICATION_CHECKLIST.md`

## Effect-audit status

- Registered (9): Ava devices, Barrows, Barrows/Damned, Crystal armour, Inquisitor, Justiciar, Slayer headgear, Virtus, and Void Knight.
- Partial (6): Moons of Peril, charged jewellery, dragonfire shields, serpentine helms, spirit shields, and mixed utility armour.
- Missing (2): Bark armour passives and Dizana's quiver runtime support.

## Unresolved items and evidence

1. **Barrows and Moon degradation:** item variants exist in the cache, but the runtime has no complete armour degradation, repair, persistence, or break transformation lifecycle. The existing generic object-charge manager is not a safe substitute for armour durability.
2. **Serpentine, dragonfire, spirit-shield, jewellery, and utility lifecycles:** several formula/protection hooks exist, but end-to-end charge consumption, persistence, uncharge, death, and transformation behavior is not consistently present for every family. These remain `PARTIAL` in the effect audit.
3. **Bark armour:** the required Swampbark bind-duration and Bloodbark healing changes do not have a verified shared spell-impact/healing hook covering all supported spellbooks. This remains `MISSING` rather than being guessed.
4. **Dizana's quiver:** the extra ammunition slot and charged-state storage require client-supported equipment/inventory state that is not present in the current revision-240 client integration. This remains `MISSING`.
5. **Blue Moon automatic style switching:** the verified one-use delay bypass is implemented, but the client/server automatic combat-style toggle used by OSRS is not available. A player must select the melee follow-up; the family remains `PARTIAL`.
6. **Visual parity:** cache model, head-model, recolour, retexture, zoom, offset, slot, and hidden-body-part references resolve at definition level. Both player body types, nearby-player refreshes, clipping, stance changes, and every family still require the in-game checklist.
7. **Lifecycle matrix:** bank/death/logout/login/restart/degrade/break/charge-depletion tests require complete family-specific persistence systems and a live test account. They are explicitly listed in the manual checklist.
8. **Integration test source set:** the set-detection integration test was added in the project's integration location, but that suite is not wired by the current Gradle conventions. The executable unit regression test is wired and passes.
9. **Formatting gate:** the normal root `build` stops at existing Spotless violations across approximately 75 unrelated dirty-tree files. The full compilation and test graph passes when only the formatting gate is excluded; unrelated user files were not bulk-reformatted.
