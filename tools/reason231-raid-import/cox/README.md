# Reason Chambers of Xeric audit tooling

This directory owns reproducible, read-only tooling for comparing the Reason donor cache with stock build 229 and the active OpenRune revision-240 caches.

The donor archive and extracted donor cache must never be mutated. Any future migration output must target a separate revision-240 test cache and must be listed in `docs/raids/cox/reason231-cox-cache-change-allowlist.json` before packing.
