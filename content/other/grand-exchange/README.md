# Local Grand Exchange

This module uses one in-process order book per world, saved to `.data/grand-exchange.json`.
There is no global coordinator, cross-world matching or external market service.

Every account has eight offer slots, independent of membership. Purchases have no buy limits:
matching depends on item, price, available quantity and price/time priority, never on purchase
history or elapsed time. The second column of `ge-items.tsv` is retained for upstream format
compatibility but is not read into gameplay data.

Sellers pay **4% (40 per mille)**, rounded down separately for each item and capped at
**5,000,000 coins per item**. Existing named exemptions are unchanged. Login sends the same
rate through `varbit.ge_transmit_taxrate` for the client display.

New saves contain only `nextId`, `offers` and `history`. The JSON reader ignores the retired
`buyWindows` property in legacy files; it creates no live purchase state and does not write
that property back. Existing offers, collectable items/coins and history retain their values.

Price/time priority, partial fills, buyer price-difference refunds, collection, aborting, history,
Repeat Offer, item validation, ironman bond restrictions, music and completion jingles are retained.
Item sets are not implemented. Existing offer-value/inventory capacity checks still apply.

Source: Blade9766's [GE commit in PR #216](https://github.com/OpenRune/OpenRune-Server/commit/83cd11a53f57a4e83b5be31f81226b95a6ea4464).
This checkout did not previously contain that module; only its GE content/configuration was
integrated, with the local policies above. No other PR features or membership-policy changes were
imported. See `docs/grand-exchange/no-limits-report.md` for integration and verification evidence.

Focused tests:

```powershell
.\gradlew.bat :content:other:grand-exchange:test --no-daemon --console=plain
```
