# Armour Audit Test Results

Date: 2026-08-15

## Passed

- Focused module compilation: `:api:spells:compileKotlin`, `:api:combat:combat-scripts:compileKotlin`, and `:content:other:commands:compileKotlin` passed.
- Focused regression test: `:api:player:test --tests org.rsmod.api.player.worn.EquipmentChecksUnitTest` passed.
- Audit generator: `:server:app:generateArmourAudit` passed.
- Final audit result: revision 240; 2,523 scoped raw IDs; 2,523 direct matches; zero direct mismatches; zero missing model-definition references.
- Full compile/test graph: `gradlew.bat build -x spotlessCheck -x spotlessKotlinCheck --no-daemon` passed with 793 actionable tasks (81 executed, 712 up-to-date).
- Workbook was exported, re-imported, and scanned: registered/partial/missing counts are 9/6/2 and zero formula-error cells were found.
- Server boot 1 passed: port 43594, revision 240, custom-build marker, canonical project root, `.data/cache/SERVER`, and `Server ready` marker verified.
- Server boot 2 passed: the same checks passed and the second server remains running for manual testing.

## Repository-wide formatting gate

The unmodified root command `gradlew.bat build --no-daemon` reached Spotless and failed on pre-existing formatting violations across approximately 75 unrelated files, including controller, boss-bar, Wilderness area, core, and attribute code. Those unrelated dirty-tree files were preserved. This is a formatting-only repository gate; the complete compilation and test graph passes when the two Spotless checks are excluded.

## Not claimed as automated

- In-game male/female body-type visual parity
- Client reconnection after the final restart
- Multiplayer nearby-player appearance synchronization
- Every family-specific charge/degradation/death persistence case
- Proc-rate statistical validation over large sample sizes
- Blue Moon's unavailable automatic style-toggle behavior

Use `MANUAL_VERIFICATION_CHECKLIST.md` for these checks.
