# Clan UI RSProx audit

## Evidence

- Capture: RSProx `2778`, revision 229.
- Binary: `%USERPROFILE%\.rsprox\binary\rsprox-2778-rev229.bin`.
- Decoded text: `%USERPROFILE%\.rsprox\binary\rsprox-2778-rev229.txt`.
- Revision-240 cache cross-check: `ClanChannelClientScriptAuditTest` disassembles scripts
  `4471`, `4474`-`4478`, and `5301` against the active SERVER cache.

The capture is older than the server cache, so it is authoritative for the observed flow and
packet ordering, while component names and client-script contracts are taken from revision 240.

## Revision-240 interface map

| Interface | Cache name | Observed or intended role |
| --- | --- | --- |
| 689 | `clans_banned` | Ban list |
| 690 | `clans_info` | Main settings and ownership |
| 691 | `clans_interests` | Interests and recruitment profile |
| 692 | `clans_hall` | Clan Hall settings |
| 693 | `clans_members` | Members, ranks, and context actions |
| 694 | `clans_outfit` | Motif/banner editor |
| 695 | `clans_ranktitles` | Custom rank titles |
| 699 | `clans_applicants` | Recruitment applications |
| 700 | `clans_board` | Recruitment board/search |
| 701 | `clans_sidepanel` | Own-clan channel panel |
| 702 | `clans_guest_sidepanel` | Guest-clan channel panel |
| 703 | `clans_events` | Event list |
| 704 | `clans_events_create` | Event create/edit |
| 705 | `clans_creation_sidepanel` | Founder and clan-name flow |
| 706 | `clans_permissions` | Permission thresholds |
| 707 | `side_channels` | Friends, own clan, guest clan, and grouping tabs |

## Confirmed packet and UI flow

- Login supplies `ClanSettingsFull` and `ClanChannelFull` before clan screens render.
- Side-channel tab clicks are sent from interface `707`; the selected tab is client varbit
  `13071` in revision 240. Scripts `4475` and `4478` read/write values `0..3` for Friends,
  own clan, guest clan, and grouping. The server must preserve this value whenever base varp
  `1054` is resent, otherwise the client redraws the yellow Friends tab.
- Opening Clan Info uses interface `690`, then scripts `4289` and `4290` populate real size,
  owner, founding date, and ownership/resignation state.
- Members opens `693` and enables row/context operations on components `11`, `13`, `19`, and
  `20`; mutations are followed by clan settings/channel delta packets.
- Permissions opens `706` from Clan Info component `23`; component `12` receives row events.
- Applicants opens `699`; component `1` carries navigation/recruitment controls and component
  `11` carries application rows. Recruitment toggles produce explicit player feedback and the
  screen is refreshed after the mutation.
- The recruitment board uses `700` with enabled events on components `1`, `5`, and `20`.
- `VarClanEnable` brackets screens that consume clan variables; closing the flow sends
  `VarClanDisable`.

## Project-specific deviation
