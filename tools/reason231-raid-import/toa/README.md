# Tombs of Amascut import audit

This directory is the reproducible evidence bundle for the Tombs of Amascut work.

## Decision

The desktop archive `osrs server.rar` is a Reason/Kronos build 229.1 distribution, not a revision-231 source tree. Its ToA code is useful for identifying native cache IDs and broad encounter intent, but it is not safe to compile into OpenRune and it omits the four challenge-room implementations.

OpenRune revision 240 already contains the native ToA maps, locations, NPCs, objects, items, interfaces, sequences, graphics, varbits, and scripts required by the implementation. No donor cache archive was copied, merged, or mounted.

## Reproduce

Run `run-audit.ps1`. It performs a read-only three-way comparison between the extracted donor cache, clean stock cache, and active revision-240 LIVE cache, then writes the audit documents to `docs/raids/toa`.

The audit intentionally has an empty cache-change allowlist. Donor-only private-server variants, including Demonic Masori items, are excluded.

## Outputs

- `reason231-toa-manifest.json`
- `reason231-to-openrune240-toa-id-map.json`
- `reason231-toa-dependency-graph.json`
- `reason231-toa-cache-change-allowlist.json`
- `reason231-toa-unresolved.json`
- `reason231-toa-audit.md`

The historic `reason231` filename is retained because it is part of the requested output contract; each report records the corrected donor build label.
