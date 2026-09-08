# Armour Manual Verification Checklist

Run these checks on the live revision-240 server. Record the item ID, body type, target type, observed result, and screenshot/log evidence for any failure.

## Definition and appearance

- [ ] Run `::armourdiff`; confirm the four current reports regenerate with zero direct definition mismatches.
- [ ] Run `::armourtestbank head`, then repeat for cape, neck, ammunition, body, shield, legs, hands, feet, and ring.
- [ ] Equip every normalized family on player body type A.
- [ ] Equip every normalized family on player body type B.
- [ ] Confirm inventory icon, zoom, rotations, and offsets.
- [ ] Confirm male/female worn models and head models.
- [ ] Confirm recolours and retextures.
- [ ] Confirm hair, beard, jaw, arms, hands, feet, torso, and cape hide/replace behavior.
- [ ] Confirm nearby players see each equipment change without relogging.
- [ ] Confirm equip, replace, unequip, bank, death, logout/login, and instance changes leave no stale bonuses or appearance.

## Barrows and Amulet of the damned

- [ ] Test each complete set with intact, 100, 75, 50, and 25 states.
- [ ] Confirm removing each required piece immediately disables its set effect.
- [ ] Confirm broken/0 pieces never activate a set effect.
- [ ] Verify Verac bypasses Defence and protection Prayer only on its proc.
- [ ] Verify Guthan healing and damned overheal cap.
- [ ] Verify Torag run-energy drain is PvP-only.
- [ ] Verify Karil Agility drain and separate damned half-damage hit; confirm no duplicate XP.
- [ ] Verify Ahrim Strength drain and damned damage share one proc.
- [ ] Verify Dharok damned recoil targets the exact attacker and awards no XP.
- [ ] Verify Verac plus damned adds exactly +7 Prayer.

## Moons of Peril

- [ ] Blood Moon: verify both macuahuitl splats roll independently and shortened attacks occur at the expected 5-in-9 rate.
- [ ] Eclipse Moon: verify 1-in-5 burn, 10 damage, 40-cycle duration, five-stack cap, and cleanup.
- [ ] Blue Moon: verify bind/ice spells use 1-in-5 and Grasp spells use 1-in-2.
- [ ] Blue Moon: verify the next selected melee attack bypasses the normal delay exactly once in PvM and PvP.
- [ ] Remove a Blue Moon piece before the follow-up and confirm the stored proc clears.
- [ ] Verify death, logout, teleport, target change, and instance exit cleanup for all Moon effects.

## Other registered families

- [ ] Void melee, ranger, mage, elite, and supported deflector combinations.
- [ ] Crystal helm/body/legs with crystal bow and Bow of Faerdhinen only.
- [ ] Slayer headgear, recolours, imbued variants, task targets, non-task targets, and boss variants.
- [ ] Justiciar PvM reduction, PvP exclusion, typeless exclusion, minimum, stacking, and rounding.
- [ ] Inquisitor per-piece/full-set crush modifiers and off-style exclusions.
- [ ] Virtus per-piece Ancient Magicks behavior for smoke, shadow, blood, and ice spells.
- [ ] Ava recovery rates and metallic-body restriction.

## Known partial or missing families

- [ ] Barrows and Moon degradation, repair, persistence, and broken transformations.
- [ ] Serpentine charge/immunity/venom lifecycle.
- [ ] Dragonfire/wyvern shield charge, discharge, cooldown, persistence, and death behavior.
- [ ] Spectral/Elysian exclusions and Prayer-drain behavior.
- [ ] Charged jewellery recoil/healing/charge/death lifecycles.
- [ ] Swampbark bind-duration and Bloodbark healing passives.
- [ ] Dizana's quiver extra-ammunition slot, charge storage, switching, and death behavior.

These partial/missing rows must remain unresolved until their required runtime/client state exists and is backed by repeatable evidence.
