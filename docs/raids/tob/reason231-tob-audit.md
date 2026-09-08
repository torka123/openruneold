# Theatre of Blood donor audit

Generated: 2026-08-16T13:15:45.696954200Z

## Revision evidence

The donor server declares build `229.1` and OpenRS2 cache `2048`. OpenRS2 identifies cache 2048 as OSRS live build 229 (2025-02-18). The user-facing revision-231 label is therefore not used as technical evidence.

## Cache paths

- Reason runtime cache: `C:\tmp\reason231-cox-donor-20260816\osrs server\server reasonps\data\cache`
- Clean stock build 229: `C:\tmp\openrs2-cache-2048\cache`
- OpenRune revision 240 LIVE: `<repo-root>\.data\cache\LIVE`
- OpenRune server destination remains `.data/cache/SERVER`.

## Source code findings

The donor has 47 Theatre source files with party, dynamic-room, boss, hazard, death, spectator, supply, and treasure logic. It also contains shared static completion fields and production debug output, so it is used as behavioral evidence and never copied directly into OpenRune.

## Mapping summary

- NATIVE_240_REPLACEMENT: 49
- NATIVE_240_SAME_ID: 230

No cache archive is approved for modification. Revision-240 native assets are used wherever a verified mapping exists.

## Unresolved

No unresolved decoded dependencies.

The complete machine-readable mapping (279 entries) is in `reason231-to-openrune240-tob-id-map.json`.
