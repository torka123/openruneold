# Grotesque Guardians revision-240 behaviour sheet

## Evidence

- Current OSRS mechanics: https://oldschool.runescape.wiki/w/Grotesque_Guardians and its strategy page.
- Revision-240 cache: source map-square 6727; roof entrance 31681; Cloister Bell 31684;
  Dawn/Dusk forms 7851-7855, 7882-7885, and 7887-7889; energy sphere locations
  31678-31680.
- Near Reality reference: existing four-phase state machine, transitions, animations, projectile
  families, sphere lifecycle, prison, and final reward flow were translated into native OpenRune
  instance/combat APIs. Donor IDs were not trusted where they differed from revision 240.
- Local RSProx search found only older League captures, not a current normal-mode revision-240
  fight. Values not observable from cache or the current Wiki remain isolated constants.

## Implemented flow

1. `::grotesque` / `::gg` creates a one-player private copy of the Slayer Tower roof. The real
   roof entrance also enters it. The player starts at local `(33,23)` and must ring the real bell.
2. Phase 1 makes Dawn vulnerable while Dusk pressures the player. Dawn uses two-hit ranged,
   adjacent melee, and stone-ball attacks. Damage is capped at the 55% phase boundary.
3. Dawn ascends and Dusk becomes vulnerable immediately; the obsolete phase-one lightning delay
   is not reproduced. Dusk uses melee, blinding explosion, and 3x3 falling debris. Only a direct
   debris tile stuns.
4. The Dusk-to-Dawn transition uses warned lightning tiles. Dawn then becomes vulnerable while
   three energy spheres progress through their stages. Absorbing one is harmless; each ignored
   sphere heals Dawn for 90.
5. Dawn is capped below 10 HP and must be finished with a rock/granite/thrown hammer. Dusk then
   enters phase 4 with enhanced melee, two-part ranged, and the flame prison. Failing the five-tick
   prison escape damages the player and heals Dusk.
6. Final Dusk death uses the server's normal drop pipeline, which records catalogued rewards in
   the Collection Log. The private encounter resets to its dormant bell state after the respawn
   delay.

## Lifecycle

Leave Instance, `::leavegg`, death, logout, and any external teleport clear the two Guardians,
energy spheres, lightning state, queued-generation state, combat state, and instance membership.
The instance is destroyed when empty.

## Validation

Focused tests cover the verified room/spawn coordinates, phase vulnerability, threshold damage
caps, sphere states/healing, debris footprint/stun distinction, phase-four ranged rules, and prison
healing. The module compile, formatting, and test tasks pass.

## Capture limitation

Exact live projectile slopes/sounds and the post-kill respawn delay still require a normal-mode
revision-240 RSProx capture. The implementation uses revision-240 assets and documented mechanics
without claiming those unobserved presentation values are packet-perfect.
