# Royal Titans capture-validation ledger

Implementation target: OpenRune revision 240. Runtime behavior is implemented in the existing
OpenRune Kotlin instance, combat, hit, cache, drop-table, and script systems.

Evidence priority:

1. RSProx capture 2283 (normal-game solo, revision 228.1) for exact visible ordering and timing.
2. RSProx captures 3398 and 3402 for cleanup, duo targeting, and opposite kill-order checks.
3. Current OSRS Wiki mechanics and strategy pages for intended rules not directly observable in a
   packet trace.
4. RuneLite's generic opponent-info behavior for the single targeted-opponent HUD contract.

| Feature | Evidence | Implemented revision-240 mapping / timing | Status |
|---|---|---|---|
| Entrance and instance | 2283 loc 55986 at 2948,9573; captured entry local 31,37 | Source region 11669; loc 55986; local entry 31,37; maximum two players | Confirmed |
| Active versus corpse NPCs | 2283 NPC lifecycle | Branda 12596, Eldric 14147; corpses 14148/14149 | Confirmed |
| Initial spawn order | 2283 spawn packets | Branda local 26,30; Eldric local 34,30 two ticks later | Confirmed |
| Base health and HUD | Wiki; 2283 opponent update | 600 HP each; only the player's current Titan target is sent to the opponent HUD | Confirmed |
| Elemental weaknesses | Wiki | Branda water 50%; Eldric fire 50%, through revision-240 NPC cache params | Confirmed |
| Retreat/advance loop | 2283 movement and sequence events | 33-tick ranged window, 21-tick melee window; staggered retreat; 7-tick stomps | Confirmed |
| Inter-Titan casting | 2283 animations/projectiles | Harmless casts during the ranged window; no player damage | Confirmed behavior; projectile identity inferred from available elemental graphics |
| Fire and ice walls | 2283 loc/NPC/mapanim events; Wiki | Five-tile rows, four-tick telegraph; occupied tile omitted and can deal up to 12; one valid opposing Blast-or-higher cast removes the row | Confirmed |
| Wall spell rules | Wiki; 2283 player casts | Fire wall: Water Blast+ or Ancient ice; ice wall: Fire Blast+ | Confirmed |
| Elemental unlock/spawn | 2283 NPC/sequence events | Unlock when either Titan has lost 100 HP; packs of three spawn two ticks after the cast | Confirmed |
| Elemental magic special rule | Wiki | Opposing element only; minimum 30% special accuracy, magic bonus scaling, Void multiplier, guaranteed maximum thresholds, 3x3 splash | Confirmed rules; max-hit damage bonuses use OpenRune's spell pipeline where available |
| Elemental kill speed-up | Wiki | Every killed elemental shortens the next player attack delay, including multiple splash kills on one tick | Confirmed |
| 35% transition | 2283 health/movement/sequence events | Incoming damage capped at 210 HP before transition; Branda lands at +3, Eldric starts at +4 and lands at +7 | Confirmed |
| Arena floors and landings | 2283 loc 55993-56000 lifecycle | Elemental floor pairs spawn with each landing and swap to death variants after a Titan dies | Confirmed |
| Arena area attack | 2283 seq/mapanim/NPC-headbar/sound events | Charge six ticks; execute then pulses at +1/+3/+5; one safe tile; timer NPC 14154 uses charge-30 bar and disappears on first pulse | Confirmed |
| Arena cadence | 2283 event timeline | First cycle 36 ticks, subsequent cycles 29; summon at +14 and arena-wide stomps at +21/+28 | Confirmed |
| Final survivor behavior | 2283 and 3402 opposite kill orders; Wiki | First final attack after 13 ticks; safe row is farthest from survivor; all hazard graphics use survivor's element | Confirmed |
| Final melee prayer piercing | Wiki | Protect from Melee reduces final-phase stomp damage to a maximum of 10 rather than nullifying it | Confirmed |
| Death and corpses | 2283 NPC replacements | Correct death sequences/floors and selectable corpse interactions | Confirmed |
| Reward choices | 2283 interaction pages; Wiki | One personal claim across both corpses: normal loot, sacrifice at doubled 1/1500 pet chance, or 10-19 pages | Confirmed |
| Reset | 2283 message and NPC lifecycle | Exact 18-second reinvigoration message; encounter resets after 30 ticks | Confirmed |
| Leave/death/logout | OpenRune instance hooks; Wiki unsafe-death rule | Tunnel escape, external teleport cleanup, unsafe death cleanup, logout cleanup, destroy-empty instance | Implemented |
| Duo/late join | Wiki; 3402 targeting | Maximum two; no HP scaling; late join allowed only while both Titans remain at or above 450 HP | Confirmed |

## Validation

- `:content:bosses:royal-titans:test` checks IDs, coordinates, entry-dialogue release, 35% damage
  gating, late joining, elemental accuracy/attack-delay rules, wall spell gates, safe-tile behavior,
  ranged vulnerability, final stomp prayer behavior, captured transition timing, and arena cadence.
- Startup asset validation resolves every referenced NPC, location, sequence, spot animation, and the
  charge headbar from the revision-240 cache before the world becomes available.
- A live visual pass should still compare projectile curves and the harmless inter-Titan projectile
  presentation, because those visual details are not fully encoded by the available capture text.

## Sources

- https://rsprox.net/download/2283
- https://rsprox.net/download/3398
- https://rsprox.net/download/3402
- https://oldschool.runescape.wiki/w/Royal_Titans
- https://oldschool.runescape.wiki/w/Royal_Titans/Strategies
- https://github.com/runelite/runelite/blob/e1afd4502d3819700374bd045ab4dfcf6f9d82ec/runelite-client/src/main/java/net/runelite/client/plugins/opponentinfo/OpponentInfoPlugin.java
