# Revision-240 shared Combat Achievement integration evidence

Read-only audit,2026-09-08. This is a design/evidence handoff, **not an implemented award service**.
Scope2 is already approved in `shared-api-approval.md`; no renewed approval is needed to implement it.

## Native catalog

`ServerCacheManager.getEnum(3981..3986).values.values` contains the task struct IDs, ordered by tier.
`ServerCacheManager.getStruct(id).params` uses1306=task ID,1308=name,1309=description,
1310=tier/points,1311=type,1312=boss ID. Use the tier enums rather than all structs: obsolete or
unlisted task structs may remain in the cache.

Active revision240 has646 unique tasks, IDs0..645. Tier counts are41/60/86/164/173/122;
points/task are1..6. Total2672 points; cumulative reward thresholds41/161/419/1075/1940/2672.
Current Wiki has newer counts (including64 Medium), so copying current counts would corrupt
the revision240 projections.

| Task ID | Struct | Name | Tier |
|---|---|---|---|
| 588 | 2535 | Perfect Royal Titans | Hard |
| 589 | 2577 | Royal Titan Champion | Medium |
| 590 | 2623 | Royal Titan Adept | Medium |
| 591 | 2625 | One by one | Easy |
| 592 | 2626 | Royal Titan Speed-Runner | Hard |
| 593 | 2627 | Titan Killer | Hard |
| 594 | 2628 | Elemental Company | Easy |
| 595 | 2630 | I need room | Hard |
| 596 | 3019 | No time to pray | Elite |
| 597 | 3060 | Let them fight | Easy |
| 598 | 3814 | It takes too long | Medium |

All have boss ID46. All eleven add25 points and tier counts3/3/4/1/0/0. The active Perfect task
description includes jump-attack damage; the original checklist omitted that source.

## Native persistent projection

LIVE script4834 (129 decoded instructions) selects block=`taskId/32`,bit=`taskId%32`.
Completion varps `varp.ca_task_completed_0..20` are:

```text
3116,3117,3118,3119,3120,3121,3122,3123,3124,3125,3126,3127,3128,
3387,3718,3773,3774,4204,4496,4721,5673
```

Do not derive varp IDs arithmetically past block12. Royal tasks use4496 bits12..22.

Native varbits in easy/medium/hard/elite/master/grandmaster order:

- `ca_total_tasks_completed_{tier}`:12885,12886,12887,12888,12889,12890. These are per-tier
  counts, not cumulative; LIVE4779 sums them. First four occupy varp3136 bytes; last two3137.
- `ca_tier_status_{tier}`:12863..12868 in varp3132 two-bit fields. State2 means claimed rewards;
  awards must not automatically promote qualified state1 to2. LIVE4806/4815 and existing
  shared hard/elite clue checks use the claimed distinction.
- `ca_threshold_{tier}`:4132,10660,10661,14812,14813,14814 in varps3792..3794.
- `ca_points`:14815, varp3791 low16.
- `ca_total_tasks_completed_royal_titans`:16088, varp4082 bits20..24.

These backing varps have `Perm` lifetime and `OnSetAlways` transmission. Existing native
`VarPlayerIntMapSetter` preserves packed neighbors and emits updates/persistence hints;
`CharacterAccountRepository` already persists permanent vars. Do not add parallel authoritative
CA count/point tables.

## Boss-total mapping

LIVE4780 (221 instructions,one switch) provides the following exact boss-ID to total-varbit map.
Boss0/misc has no per-boss total in this switch, but still contributes tier counts and points.

```json
{"1":12917,"2":12926,"3":11291,"4":11010,"5":12893,"6":20083,"7":12925,"8":12901,"9":12916,"10":12904,"11":12910,"12":12912,"13":12891,"14":12892,"15":12908,"16":12896,"17":12931,"18":12932,"19":12898,"20":12899,"21":12900,"22":12922,"23":3209,"24":15024,"25":9778,"26":12895,"27":12906,"28":12923,"29":12927,"30":11290,"31":12907,"32":12905,"33":12914,"34":12894,"35":12897,"36":15026,"37":15718,"38":15401,"39":12928,"40":9777,"41":13171,"42":12934,"43":13002,"44":12920,"45":14712,"46":16088,"47":9580,"48":12929,"49":12911,"50":18145,"51":12918,"52":12936,"53":12939,"54":12935,"55":12938,"56":12915,"57":14297,"58":14298,"59":14299,"60":12937,"61":12921,"62":12913,"63":15025,"64":12902,"65":12903,"66":12924,"67":15027,"68":12919,"69":16255,"70":12930,"71":12909}
```

## Output and atomicity

Task parameter1307=3594 references notification metadata, **not** a boss count or task-list enum.
Struct3594 has title `Combat Achievements`; native upstream `ClientScripts.notificationDisplay`
sends3343. Existing collection-log UI demonstrates opening the notification-display interface
before calling it. Exact CA notification title/body/colour behavior remains capture-unverified.
Native popup preference is12455; recompletion notifications12456; failure12454/refailure12457.

The approved local `AccountSavingService.commitPlayerMutationBlocking` can serialize a short
mutation and full native account save. It is a reviewed local extension, not inherited upstream.
Snapshot/rollback every touched backing varp and receipt attribute. A separately committed SQL
receipt followed by transient var mutations is not crash-safe. Notifications follow successful
commit and only newly completed tasks; duplicate success must not replay notifications.

Use native task bits for lifetime idempotence and a bounded string receipt if completion payload
validation is required. Persisted `MutableSet` attributes are unsafe: loading normalizes JSON
arrays to lists. Existing Cerberus's private award helper sets only its own bits/count and custom
chat; it is not a reusable or exact shared CA service.

## Precise stat observations implemented separately

All production current-level changes pass through `PlayerStatMap.setCurrentLevel`, including
direct writes, not only statSub/statHeal. New immutable synchronous observations are bound by
`PlayerRegistry` through native EventBus. Actual MapClock is captured, not potentially stale
player-processing time. Base changes are distinguished from current changes; unsigned levels
above127 and reentrant restoration preserve snapshot values. Legacy delayed `onChangeStat`
retains its existing timing and arguments.

The CA listener must latch loss synchronously before a same-tick completion. Delayed polling can
miss drain/restoration or execute after the second Titan's death. Existing player HP-change events
and `onNpcHit` remain the source for their respective resolved damage observations.

Required award tests remain: all eleven predicates, ordinary unrelated tasks, block boundaries
31/32 and415/416, neighboring packed bits, all-tier totals, claimed reward preservation, rollback,
duplicate/concurrent awards, reconnect/restart and no notification replay.
