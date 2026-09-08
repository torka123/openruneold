# OpenRune boss roster audit

Verified: **2026-08-13**
Authority: [current OSRS Wiki Boss page](https://oldschool.runescape.wiki/w/Boss)

## Scope result

The live page contains **94 individual named bosses** when linked multi-boss rows are expanded (for example, the six Barrows brothers, three Dagannoth Kings, three Moons, paired Wilderness variants, Brutus/Demonic Brutus, the two Royal Titans, and Nightmare/Phosani). The machine-readable source of truth is [`boss-roster.json`](boss-roster.json).

The Wiki currently categorises **Obor** and **Bryophyta** as *Instanced bosses*, not *Sporadic bosses*. They are still included in the requested first five-boss acceptance checkpoint together with The Mimic, Hespori, and Skotizo.

## Honest baseline

| State | Count | Meaning |
|---|---:|---|
| PARTIAL | 15 | Obor and Bryophyta now join the six Barrows brothers, six earlier `content/bosses` modules, and TzTok-Jad. Their focused tests, cache build and two clean server restarts pass, but the full live access/combat/reward/reset contract still needs two consecutive in-game cycles. |
| MISSING | 76 | No complete owning encounter module was verified. A generated drop table by itself is not an encounter implementation. |
| ENGINE_BLOCKED | 2 | The Mimic lacks its Treasure Trail casket lifecycle; Hespori lacks a persisted Farming patch lifecycle. Implementing just their combat rooms would violate their access/reward contracts. |
| MAP_BLOCKED | 1 | Skotizo's boss spawn exists, but the active raw map supplies no Awakened Altar placements or resolved dark-totem entrance interaction. |
| IMPLEMENTED_TESTED | 0 | Compilation alone is not accepted as gameplay proof. |

## First checkpoint evidence

| Boss | Current Wiki category | Active revision-240 NPC key | Current state | Exact next work |
|---|---|---|---|---|
| Obor | Instanced bosses | `npc.hillgiant_boss` (7416) | PARTIAL | Owned instance, permanent first-key unlock, prayer-aware combat, collision-safe knockback, chest reward/reset, drop table, focused tests, cache build and two clean startups are in place. Resolve the exact rock projectile audiovisual sequence and pass two live repeatability cycles. |
| Bryophyta | Instanced bosses | `npc.gb_mossgiant` (8195), `npc.gb_growthling` (8194) | PARTIAL | Owned instance, Growthling wave/immunity/execution/cleanup, key/chest reward/reset, drop table, focused tests, cache build and two clean startups are in place. Resolve poison/attack audiovisual details and pass two live repeatability cycles. |
| The Mimic | Sporadic bosses | `npc.trail_mimic_combat` (8633) | ENGINE_BLOCKED | Exact-source search found no elite/master casket-open or Mimic-casket interaction handler. Add the Treasure Trail casket/attempt/reward-preservation/death-bank persistence owner before encounter combat. |
| Hespori | Sporadic bosses | `npc.hespori` (8583) | ENGINE_BLOCKED | `content/skills/farming` does not exist. Add a persisted patch lifecycle covering seed use, 22-32 hour growth, readiness, harvest, replant and reset before encounter combat. |
| Skotizo | Sporadic bosses | `npc.cata_boss` (7286) | MAP_BLOCKED | Active raw map has only the boss at `0_35_88_31_29` (2271,5661); no four Awakened Altar placements or dark-totem entry handler resolve. Recover exact revision-240 placements/entry object before implementing the owned arena. |

These five primary NPC definitions are present in `.data/cache/SERVER` and map to the listed RSCM keys in `.data/gamevals-binary/gamevals.dat`. `dump.npc` is not present under `.data/osrs-dumps` or `.data/raw-cache`; that missing fallback is recorded, but it does not block the already resolved primary forms.
### First-checkpoint blocker evidence

The classifications above are deliberate no-approximation stops, not placeholder implementations:

- `rg` across `content`, `api`, and `engine` finds no `onOpHeld*`/`onOpObj*` handler for elite/master reward caskets or the Mimic casket. Existing clue references are drop generation and dialogue only.
- `content/skills/farming` is absent. Four inactive Hespori flower NPC placements exist, but there is no persistent patch clock/readiness/harvest owner to create the fight honestly.
- `.data/raw-cache/map/npcs/kourend.toml` supplies Skotizo's spawn at `0_35_88_31_29`; no `cata_*altar`/Awakened Altar placement occurs anywhere in `.data/raw-cache/map`.

The automated roster task intentionally continues to report these entries as unowned until their prerequisite engine/map work exists. Assigning empty Kotlin modules would hide the missing behavior and is forbidden by the acceptance rules.

## Existing implementation ownership

| Bosses | Owning module | Audit state | Why not complete |
|---|---|---|---|
| General Graardor | `content/bosses/graardor` | PARTIAL | Combat and room instance exist; numeric fidelity, target switching, prayer/formula behavior, bodyguard styles, drops and repeatable cleanup are not proven by tests. |
| King Black Dragon | `content/bosses/kbd` | PARTIAL | Breath variants and instance exist; special-breath probabilities/effects, correct accuracy, timing, drops and reset tests are incomplete. |
| Kree'arra | `content/bosses/kreearra` | PARTIAL | Combat, melee restriction and room exist; attack probabilities, pushback, bodyguards, formulas and lifecycle tests are incomplete. |
| K'ril Tsutsaroth | `content/bosses/kril` | PARTIAL | Combat and room exist; prayer-drain special, bodyguards, formulas, drops and lifecycle tests require verification. |
| Scurrius | `content/bosses/scurrius` | PARTIAL | Rich phase code and public/private rooms exist; thresholds, rubble ownership, rat cleanup, formulas, drops and repeatability require executable tests. |
| Commander Zilyana | `content/bosses/zilyana` | PARTIAL | Combat and room exist; multi-target behavior, bodyguards, formulas, drops and lifecycle tests require verification. |
| Barrows brothers | `content/minigames/barrows` | PARTIAL | A custom minigame implementation exists, but each brother still requires independent numeric/mechanics/drop/reset validation against this roster standard. |
| TzTok-Jad | `content/minigames/fight-caves` | PARTIAL | Fight Caves owns Jad and recently received lifecycle fixes; attack-timing, healers, reward and repeatability still need roster-level automated proof. |
All six `content/bosses` modules and `content/bosses/diagnostics` compile against the shared boss API. Their Gradle `test` tasks report `NO-SOURCE`, so compilation is recorded as regression evidence but none is promoted to tested gameplay status.


## Category roster

The JSON companion carries stable slugs, exact Wiki links, ownership and per-record inherited audit fields for every name below.

- **World bosses (25):** Ahrim, Karil, Dharok, Guthan, Torag, Verac, Gemstone Crab, Scurrius, Giant Mole, Deranged Archaeologist, Dagannoth Supreme, Dagannoth Rex, Dagannoth Prime, Sarachnis, Blood Moon, Blue Moon, Eclipse Moon, Kalphite Queen, Kree'arra, Commander Zilyana, General Graardor, K'ril Tsutsaroth, The Hueycoatl, Corporeal Beast, Nex.
- **Wilderness bosses (12):** Chaos Fanatic, Crazy archaeologist, Scorpia, King Black Dragon, Chaos Elemental, Revenant maledictus, Calvar'ion, Vet'ion, Spindel, Venenatis, Artio, Callisto.
- **Instanced bosses (16):** Brutus, Demonic Brutus, Obor, Bryophyta, Amoxliatl, Branda the Fire Queen, Eldric the Ice King, Doom of Mokhaiotl, Mad Angel, Zulrah, Vorkath, Phantom Muspah, Maggot King, The Nightmare, Phosani's Nightmare, Yama.
- **The Forgotten Four (4):** Duke Sucellus, The Leviathan, The Whisperer, Vardorvis.
- **Sporadic bosses (3):** The Mimic, Hespori, Skotizo.
- **Slayer bosses (8):** Shellbane gryphon, Grotesque Guardians, Abyssal Sire, Kraken, Cerberus, Araxxor, Thermonuclear smoke devil, Alchemical Hydra.
- **Minigame bosses (5):** Crystalline Hunllef, Corrupted Hunllef, TzTok-Jad, TzKal-Zuk, Sol Heredit.
- **Skilling bosses (3):** Tempoross, Wintertodt, Zalcano.
- **Chambers of Xeric (6):** Tekton, Vanguard, Vespula, Vasa Nistirio, Muttadile, Great Olm.
- **Theatre of Blood (6):** Maiden of Sugadinti, Pestilent Bloat, Nylocas Vasilias, Sotetseg, Xarpus, Verzik Vitur.
- **Tombs of Amascut (6):** Akkha, Ba-Ba, Kephri, Zebak, Tumeken's Warden, Elidinis' Warden.

## Machine-readable field semantics

Every boss inherits all fields from `fieldDefaults`; record-level values override them. Empty asset arrays mean **not yet resolved**, never ?not required.? `MISSING` is used until evidence supports a more specific cache/map/engine blocker. `PARTIAL` means code exists but the complete eight-step gameplay loop has not been proven. Only executable repeat-entry and cleanup tests can advance a record to `IMPLEMENTED_TESTED`.

## Validation

Run `./gradlew :tools:boss-roster:validateBossRoster`. It validates unique stable slugs, module ownership and loadable source structure, placeholder claims, duplicate primary ownership, and active gameval resolution for every referenced NPC form. It intentionally fails while roster targets remain unowned; this is a coverage gate, not a cosmetic report.

### Executed verification evidence

- `scripts/openrune-doctor.ps1 -SkipGradleVersion` passed before gameplay edits and identified the canonical project/cache/runtime.
- `:content:bosses:obor:test :content:bosses:bryophyta:test :or-cache:compileKotlin` passed: 167 tasks; both focused test suites executed.
- The six earlier `content/bosses` modules plus diagnostics compile; their test tasks are `NO-SOURCE`, so they remain `PARTIAL`.
- A project build passed with only the repository's unrelated Spotless gates and the pre-existing `:api:drop-table:test` no-discovered-tests task excluded: 712 actionable tasks, 179 executed.
- `:or-cache:buildCache` passed and rebuilt both LIVE and SERVER caches, including all 37 database tables and 18,262 decoded server database rows.
- Two consecutive clean canonical server restarts passed. Each loaded revision 240/world 255, bound ports 43594/8080/9091 and produced empty stderr after startup. The second restart remains running.
- `:tools:boss-roster:validateBossRoster` correctly fails with 79 ownership issues across 94 targets. Obor and Bryophyta are no longer among those issues; the failure is the intended incomplete-coverage gate.
- The strict unexcluded `clean build` is not green: unrelated existing Spotless violations and Windows build-directory locks were encountered, and after excluding Spotless the pre-existing no-discovered-tests configuration in `:api:drop-table:test` remained. No bulk formatting or unrelated build-task rewrite was performed.

This evidence proves packaging, cache generation and repeatable startup. It does **not** prove the required live player access/combat/death/reward/re-entry cycles, so neither new encounter is promoted to `IMPLEMENTED_TESTED`.
