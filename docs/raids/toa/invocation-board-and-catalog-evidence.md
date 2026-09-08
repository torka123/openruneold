# Tombs of Amascut invocation board and catalog evidence

This ledger covers the invocation board and locked invocation catalog only. Encounter effects are
implemented and tested by their owning room modules. The no-invocation selection remains a raid
level of 0 and produces three zero client bitmaps.

## Current standard set

The implementation contains 44 standard Tombs of Amascut invocations. Their raw level sum is 770;
the maximum legal level is 600 after selecting only one invocation from each mutually exclusive
family. The four exclusive families are attempts, raid timer, helpful spirit, and initial path
difficulty. Walk the Path is independent of the initial path-difficulty family.

The dependency chains are explicit:

- Overclocked -> Overclocked 2 -> Insanity.
- Not Just a Head -> Arterial Spray.
- Not Just a Head -> Blood Thinners.

Disabling a prerequisite disables every selected dependent. Selecting a member of an exclusive
family replaces its selected sibling. Quiet Prayers and Deadly Prayers may coexist. On a Diet,
Dehydration, and Overly Draining may also coexist.

## Revision-240 client crosswalk

OpenRune's revision-240 cache is the authority for client-facing identifiers. The server catalog
maps each standard invocation explicitly to interface 774's row, enum 4664's struct, the struct's
client invocation id, category, and raid-level parameters. It never derives a mapping from Kotlin
enum order. Interface components 52, 69, 83, 84, and 98 and preset-selection varbit 14541 are
validated at startup and by `ToaInvocationCacheMappingTest`.

The native revision-240 enum contains two additional league-only rows, Blazing Tombs I and Blazing
Tombs II. They are intentionally excluded from the standard raid catalog and its selectable rows.

## Donor differences

The donor archive (`TOA.zip`, including `nr_toa_foundation.zip`) was used only as behavioral
evidence. Its `InvocationType`, `TOAManager`, `TOAPartySettings`, and
`TOAPartyManagementInterface` show the 44 standard definitions, three 31-bit client bitmaps,
dependency behavior, and five preset slots. The implementation does not copy donor Java or ids.

Two donor behaviors were deliberately not reproduced:

- The donor party UI indexes `InvocationType.VALUES` by interface row, coupling UI behavior to enum
  order. OpenRune uses the explicit revision-240 cache crosswalk instead.
- When Not Just a Head is disabled, the donor's `else if` cascade can clear Arterial Spray or Blood
  Thinners but not both. OpenRune clears both selected dependents.

The donor build predates the revision-240 cache and is not authoritative for native component,
varbit, struct, or script identifiers.

## External behavior references

- OSRS Wiki, Tombs of Amascut invocation and raid-level overview:
  https://oldschool.runescape.wiki/w/Tombs_of_Amascut#Invocations_and_Raid_Level
- OSRS Wiki, invocation details:
  https://oldschool.runescape.wiki/w/Invocations_toa
- OSRS Wiki, Tombs of Amascut strategies:
  https://oldschool.runescape.wiki/w/Tombs_of_Amascut/Strategies#Invocations_and_Raid_Level
- OSRS update notes for invocation dependency changes:
  https://oldschool.runescape.wiki/w/Update:Quest_Speedrunning,_Wilderness_Boss_Rework_Rewards_%26_Poison_Dynamite_Betas#Tombs_of_Amascut_Changes
- RuneLite ToA invocation enum and preset helper references:
  https://github.com/LlemonDuck/tombs-of-amascut/blob/main/src/main/java/com/duckblade/osrs/toa/util/Invocation.java
  https://github.com/LlemonDuck/tombs-of-amascut/blob/main/src/main/java/com/duckblade/osrs/toa/features/invocationpresets/InvocationPreset.java

The user-provided Tons of XP guide was treated as secondary orientation only because some values
and descriptions are approximate:
https://tonsofxp.com/blog/osrs-toa-invocations-guide
