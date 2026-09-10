# Royal Titans reward continuation: native workflow mapping

The user requested the remaining Royal Titans work after saving a reusable all-boss
resume prompt. Existing `shared-api-approval.md` scope 3 authorizes the bounded
shared delivery/allocator work below. No new combat engine or boss-local reward
pipeline is proposed. This is an implementation record, not a completion claim.

## Pre-edit mapping

| Responsibility | Existing owner and classification | Bounded correction and tests |
|---|---|---|
| Deferred corpse reward delivery | Official-family `content/drops/NpcDropTableKillHook.spawnDrop`; local additions provide provenance, stable Collection Log IDs and clan broadcasts | Extract this implementation into `NpcRewardDeliveryService`; normal deaths and Royal Titans use the same method. Test personal duration, transformations, quantities, bonus drops, tracker/log/broadcast identity and eligibility. |
| Loot tracker identity | Official `api/death/NpcDeath.nextLootTrackerEventId`, currently private | Expose the existing singleton allocator to deferred rewards; no second counter. Test normal/deferred allocations and wrap behavior. |
| Corpse source identity | Native `Npc.transmog` retains original `type`; registry already resolves `npc.type` | Use the actual corpse NPC in drop context, not a constructed proxy. Test both original identities after corpse transforms. |
| Standard/unique composition | Existing local `RSDropTable.separateRolls` and `mainRollsFor` | Retain the current provisional independent unique pre-roll topology, use its result to replace the two standard rolls, and keep tertiaries. Do not use `exclusivePreRollWhen`, which also suppresses tertiaries. |
| Learned scrolls | Existing prayer unlock varbits, `dropRollableWithPlayerCondition` and native chance-entry inclusion hooks | Exclude learned scrolls before selection; do not discard a winning unique after it has already suppressed standards. Executable tests found that the outer chance wrapper does not inherit the inner inclusion hook; wire that existing hook explicitly. No new private selector or broad shared-condition change. |
| Clues | Existing hard/elite claimed-tier predicates and clue transformation | Base rates 25/100, claimed-tier rates 23/95. Preserve shared clue/box handling. |
| Deterministic table execution | Existing dtx `ArgMap`/table selectors; selectors currently use Kotlin randomness | Add an optional roll-random argument to the existing weighted/chance selectors, defaulting to their old Kotlin behavior. Adapt injected `GameRandom` in the plugin; no parallel selector or table engine. Tests force each existing table branch without probability sampling. |

Pinned upstream reference: `61139e76a216592d66c2b20366ef26d48609875d`.
Paths: `content/drops/.../NpcDropTableKillHook.kt`, `api/death/.../NpcDeath.kt`,
`api/drop-table/.../RSDropTable.kt`, `api/repo/.../ObjRepository.kt`.
The extracted service and optional RNG argument are new reusable LOCAL extensions
of these pipelines, not claims that those public APIs already exist upstream.

## Evidence and unresolved conflicts

- Official February18 update: https://secure.runescape.com/m=news/royal-titans-cas--birthday-rewards?oldschool=1
- Official August27 update: https://secure.runescape.com/m=news/summer-sweep-up-slayer--more?oldschool=1
- Current reward descriptions: https://oldschool.runescape.wiki/w/Royal_Titans#Contribution
  and https://oldschool.runescape.wiki/w/Branda_the_Fire_Queen#Drops
- Decoded GM3398 L23150-23151: two water-rune awards in group60275. Two standard
  rolls may independently choose the same item. GM3402 L31722-31723: ice crown plus
  clue box; a unique must not suppress tertiaries. These altered-mode captures do
  not establish normal rates or exact unique ordering/mutual exclusivity.
- Learned scroll varbits: Deadeye16097/Mystic Vigour16098, existing prayer script;
  GM3398 L12188/L8204. Claimed CA reward state is 2, not merely qualified state1.
- The post-August full-loot saturation threshold remains unpublished. Do not
  silently choose 1200/1100/1000 damage or renormalize contribution to fit it.
- Normal2283 T1830 L30951-30962 shows inventory93 reward updates, conflicting with
  the original prompt's ground-delivery requirement. This bounded refactor retains
  existing personal ground delivery; it does not certify that location as OSRS.
- GM3402 T2988 L41991 includes colour and a final period in the sacrifice-failure
  message; the original prompt specified an uncoloured punctuation-free string.
  Exact confirmation/picking presentation remains a separate evidence correction.

## Explicitly not solved by extraction

Durable allocation, native pickup/account-save transactions, ground reward-line
identity across merging/splitting/expiry, recovery without rerolls and rollback-safe
notifications remain required. A memory claim flag or SQL flag plus transient
spawn is NOT crash-safe delivery. Native `ObjRepository.add` returning an object
does not prove registration succeeded or identify merged/split output.

The shared pet/follower workflow is absent in the inspected local/upstream trees;
native tables currently drop pet items. A Bran item/Collection Log award is not
pet-unlock certification. All eleven CA predicates/awards remain separate work.
Do not change the contradictory elemental lifetime or unknown 2/3-action rotation
selection to another guess merely because the migration is near a checkpoint.

## Verification

### Implemented in this bounded pass

- Extracted the existing native `NpcDropTableKillHook.spawnDrop` into
  `content/drops/NpcRewardDeliveryService`. The normal hook delegates to it, preserving
  DT2 kit append order, conditions, transformations, quantities, provenance, personal
  lifetime, recursive bonuses, tracker, Collection Log and clan-broadcast calls.
- Royal Titans no longer constructs a proxy NPC or has a private `giveDrop` method.
  All three claim choices use that same delivery service and the original corpse's
  active NPC identity. They use `NpcDeath.nextLootTrackerEventId`, not another counter.
- Completion/account/ordinal IDs reach the existing Collection Log/broadcast pipeline.
  In-memory duplicate clicks still share one personal claim across both corpses.
  A missing table no longer consumes the claim. This is NOT a durable transaction.
- Updated the two existing registered tables, not a second implementation:
  two independent standard selections; a unique skips both standards while retaining
  tertiaries; learned scrolls excluded before their chance roll; clue rates25/100
  or23/95 only for claimed CA tiers. Existing standard weights/quantities are preserved.
- Pages and sacrifice now roll native pet/clue tertiaries. Sacrifice uses1/1500 rather
  than1/3000 for the pet only. Their full-contribution quantities and existing generic
  messages remain provisional; contribution scaling and confirmation/picking are open.
- Injected entropy reaches the existing dtx weighted/multi-chance selectors via
  `ArgMap`. Default callers keep Kotlin entropy; Royal Titans adapts `GameRandom`.
  Native flattening order is preserved, not silently changed during the refactor.
- Corrected the ledger's false zero-HP claim for twelve elemental explosions.
  No new lifetime, movement, action budget or combat balance was guessed.

New tests execute actual registered tables, native completion/hit/instance/ground
flows and emitted loot-tracker packets. Collection Log delivery is observed at its
service boundary; its existing persistence/dedup tests also run. These tests do not
simulate a rendered client, a durable claim recovery or a pet follower unlock.

### Focused verification

Four native-delivery characterization cases passed BEFORE extraction
(`logs/rt-reward-characterization-20260910.log`). The new cases caught incorrect
test fixture assumptions about inline coordinates, native flatten order and item
gamevals, plus the real outer chance-wrapper inclusion requirement. Those are fixed.

Final focused command (stdout/stderr in `logs/rt-rewards-verification-final-20260910.log`):

```text
.\gradlew.bat :content:bosses:royal-titans:test :or-cache:test --tests dev.openrune.RoyalTitansR240CacheAuditTest :api:death:test :api:drop-table:test :content:drops:test :api:collection-log:test --no-daemon --console=plain
```

BUILD SUCCESSFUL,42s,206tasks. Reports: Royal Titans133; drops60; death6; drop-table5;
Collection Log10; revision240 cache3. All217 cases pass with no skips. Unchanged
tasks, including the cache audit, were up-to-date in the final invocation.
Nineteen tests were added in this pass. Focused source-only formatting uses
`scripts/format-selected.init.gradle` and `logs/rt-rewards-format-manifest-20260910.txt`.
Architecture detector and its self-test pass; `git diff --check` passes.

### Exact native references and classification

- Official source family:
  https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/content/drops/src/main/kotlin/org/rsmod/content/drops/NpcDropTableKillHook.kt
- Official death allocator:
  https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/death/src/main/kotlin/org/rsmod/api/death/NpcDeath.kt
- Official table composition:
  https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/drop-table/src/main/kotlin/dtx/rs/RSDropTable.kt
- Official personal-object workflow:
  https://github.com/OpenRune/OpenRune-Server/blob/61139e76a216592d66c2b20366ef26d48609875d/api/repo/src/main/kotlin/org/rsmod/api/repo/obj/ObjRepository.kt

The public delivery extraction, optional roll-RNG context, current CollectionLogService,
stable notification IDs, clue claimed-tier helpers and `mainRollsFor` are local/fork
integrations. They are not misrepresented as public upstream APIs. They extend/delegate
to the native pipelines instead of creating a boss-local delivery/selection framework.

### What remains before Royal Titans can be called complete

Durable claims/account pickup recovery; contribution quantity/chance scaling and the
post-August full-loot policy; eleven persistent CAs; pet/follower integration; exact
claim presentation/destination; elemental lifetime/selection and phase action-budget
evidence; the movement/targeting/visual acceptance items in the main capture ledger.
The normal capture proves inventory delivery; this pass preserves the user's earlier
ground-delivery instruction pending their clarification. It does not certify it as OSRS.
No new OpenRune-versus-OSRS fight capture or rendered-client acceptance has been completed.

### Broader verification and source checkpoint

`logs/rt-rewards-broad-check-20260910.log`: BUILD SUCCESSFUL in2m12s,505tasks.
Scoped checks passed for death, drop-table, drop-table-plugin, Collection Log, drops,
Royal Titans, boss DSL and instances. Regression suites passed for Kraken, Vorkath,
Corporeal Beast, Hydra, Grotesque Guardians, Zulrah, Muspah, Cerberus and Nex.
`:server:app:assemble` passed. No unrelated failures were repaired or formatting applied.
Exact invocation includes the selected-format init script/manifest named above.

Shared extraction/RNG commit: `ac1ca5e90` (10files,+457/-114, including tests).
Royal Titans coordinator is829lines versus830 at the preceding architecture checkpoint;
this pass centralizes delivery and adds tests, not another combat rewrite.

Royal Titans integration commit: `eb5d0f4a8` (12files,+757/-126, including tests/docs).
Both commits are local; nothing was pushed/merged remotely. Backup branch:
`backup/royal-titans-native-rewards-20260910`. Source-only ZIP:
`logs/royal-titans-native-rewards-20260910.zip`,7471 entries (4391 source files),
SHA256 `D362F52C91281BA6E93486947C6AE87097F19CE2D3627BD7E1B3DCDCDF672A10`.
The archive excludes .data, credentials, logs and generated output; it is not a DB backup.
High-signal credential-pattern scan found no candidates in the changed source/docs.

### Controlled runtime verification

Deployed source `eb5d0f4a8` through the existing restart helper:

```text
scripts/cerberus-controlled-restart.ps1 -ServerProcessId 39612 -RestartNumber 202609105
scripts/cerberus-controlled-restart.ps1 -ServerProcessId 42216 -RestartNumber 202609106
```

Normal JVM transitions:39612 ->42216 ->21724. No connected game clients were observed
at either shutdown. Custom markers at05:18:30/05:19:58 EDT September10; ready at
05:18:32/05:20:01. Both doctors confirm canonical user.dir and revision240.
`jcmd 21724 VM.class_hierarchy org.rsmod.content.drops.NpcRewardDeliveryService`
confirms the new shared delivery class is loaded. The startup marker still has
unavailable Git metadata; source identity comes from Git plus controlled rebuild,
not an invented marker hash.

The pre-existing database-close/service-order errors recurred during shutdown.
Account saving reported successful shutdown; fingerprints below match. This does
not certify clean service ordering or unobserved in-memory state.

Fresh before/restart1/restart2 PostgreSQL fingerprints match:

- 23 inventory containers: `0312a19af5d3db794f34a8beb4a6864f0cf4db8ca36b5010fd9927065d0d72ec`.
- 1153 inventory/bank rows: `343de321331febedd4e1aaa551f3e232cd2799959ee8289b1011872e2867144b`.
- 1 realm row: `c971b1f6d8e431e9f25d64c6dc154d7c60ff22ce7c9e20274a73f671e911ef50`.

These supersede the earlier snapshot after intervening user activity; the audit is
read-only and did not restore prior account rows. Cache hashes remain:
SERVER `D01985ED94C9814DBF99509B335E964C69F1FF91C2464A5165D42A78E1915D75`;
LIVE `FD0FE5EF66039A791A900EDDDF1CDBCFE57E09E8C32EA3496C69E96217A41A7B`.

Evidence logs: `logs/rt-rewards-doctor-restart{1,2}-20260910.log`,
`logs/rt-rewards-persistence-{before,restart1,restart2}-20260910.log`,
`logs/rt-rewards-live-delivery-class-20260910.log`, and
`logs/cerberus-audit-20260907/restart-20260910{5,6}.stdout.log`.
This is startup/persistence verification, NOT rendered fight/reward acceptance.

### Player testing checklist for this deployed increment

1. Finish a solo kill and claim either corpse. With no unique, expect two standard
   rolls (possibly the same item merged on the ground), one personal claim total.
2. Check Loot Tracker and Collection Log for rewards; relog to check native account
   persistence. Learned prayer scrolls should be excluded from subsequent selection.
3. Try Take pages and Sacrifice on separate kills. Both now include pet/clue rolls;
   sacrifice excludes normal/unique loot. Rare rates are covered by deterministic
   tests, not something a few client kills can establish.
4. In a duo, both partners can claim the same corpse independently. One partner
   claiming must not block the other or expose their personal stack.
5. Kill a previously working ordinary boss and check its usual drop delivery,
   tracker/log and bonus items after the shared extraction.

Do not use this checklist to certify partial-contribution quantities, pet following,
crash-safe claims, exact dialogue or fight visuals: those remain explicitly open.
