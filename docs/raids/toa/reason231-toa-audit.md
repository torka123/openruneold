# Tombs of Amascut donor audit

Generated: 2026-08-17T01:42:16.762957200Z

## Revision evidence

The donor server declares build `229.1` and OpenRS2 cache `2048`. OpenRS2 identifies cache 2048 as OSRS live build 229 (2025-02-18). The user-facing revision-231 label is therefore not used as technical evidence.

## Cache paths

- Reason runtime cache: `%USERPROFILE%\Downloads\osrs server\osrs server\server reasonps\data\cache`
- Clean stock build 229: `C:\tmp\openrs2-cache-2048\cache`
- OpenRune revision 240 LIVE: `<repo-root>\.data\cache\LIVE`
- OpenRune server destination remains `.data/cache/SERVER`.

## Source code findings

The donor includes party, invocation, dynamic-room, four path-boss, Warden, death, supply, and reward code, but no challenge-room implementation. It also uses inaccurate scaling and shared mutable state, so it is behavioral and ID evidence only and is never copied directly into OpenRune.

## Mapping summary

- NATIVE_240_REPLACEMENT: 80
- NATIVE_240_SAME_ID: 225

No cache archive is approved for modification. Revision-240 native assets are used wherever a verified mapping exists.

## Unresolved

No unresolved decoded dependencies.

The complete machine-readable mapping (305 entries) is in `reason231-to-openrune240-toa-id-map.json`.
