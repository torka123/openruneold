# Chambers of Xeric donor audit

Generated: 2026-08-16T12:06:08.152015500Z

## Revision evidence

The donor server declares build `229.1` and OpenRS2 cache `2048`. OpenRS2 identifies cache 2048 as OSRS live build 229 (2025-02-18). The user-facing revision-231 label is therefore not used as technical evidence.

## Cache paths

- Reason runtime cache: `C:\tmp\reason231-cox-donor-20260816\osrs server\server reasonps\data\cache`
- Clean stock build 229: `C:\tmp\openrs2-cache-2048\cache`
- OpenRune revision 240 LIVE: `<repo-root>\.data\cache\LIVE`
- OpenRune server destination remains `.data/cache/SERVER`.

## Source code findings

The donor has two divergent CoX implementations. The 48-file legacy implementation leaves Ice Demon, Vespula, and Jewelled Crabs disabled in its room enum, uses shared static layout arrays, and includes unfinished Olm/prayer paths. The five-file rework is a second fixed-layout implementation. Neither is safe as a direct drop-in port.

## Mapping summary

- NATIVE_240_REPLACEMENT: 62
- NATIVE_240_SAME_ID: 209

No cache archive is approved for modification. Revision-240 native assets are used wherever a verified mapping exists.

## Unresolved

No unresolved decoded dependencies.

The complete machine-readable mapping (271 entries) is in `reason231-to-openrune240-cox-id-map.json`.
