package org.rsmod.api.net.social

public enum class ClanEventType(public val storageId: Int) {
    Bossing(0),
    Skilling(1),
    Pvp(2),
    Social(3),
}

public enum class ClanEventSubtype(public val storageId: Int) {
    None(0),
    Mass(1),
    Wilderness(2),
    Risky(3),
    Serious(4),
    Chill(5),
    Meta(6),
    Competition(7),
    Rewards(8),
    Bingo(9),
}

public data class ClanEventActivity(
    val storageKey: String,
    val displayName: String,
    val type: ClanEventType?,
)

public object ClanEventActivities {
    public val ToBeConfirmed: ClanEventActivity =
        ClanEventActivity("to_be_confirmed", "To Be Confirmed", null)

    public val all: List<ClanEventActivity> =
        listOf(ToBeConfirmed) +
            names(
                ClanEventType.Bossing,
                "Abyssal Sire",
                "Alchemical Hydra",
                "Amoxliatl",
                "Artio",
                "Barrows",
                "Callisto",
                "Calvar'ion",
                "Cerberus",
                "Chambers of Xeric",
                "CoX Challenge Mode",
                "Chaos Elemental",
                "Chaos Fanatic",
                "Commander Zilyana",
                "Corporeal Beast",
                "Crazy Archaeologist",
                "Dagannoth Kings",
                "Deranged Archaeologist",
                "Duke Sucellus",
                "Fight Caves",
                "Gauntlet",
                "General Graardor",
                "Giant Mole",
                "Grotesque Guardians",
                "Hueycoatl",
                "Inferno",
                "K'ril Tsutsaroth",
                "Kalphite Queen",
                "King Black Dragon",
                "Kraken",
                "Kree'arra",
                "Leviathan",
                "Nex",
                "Nightmare",
                "Phantom Muspah",
                "Revenant Maledictus",
                "Royal Titans",
                "Sarachnis",
                "Scorpia",
                "Scurrius",
                "Spindel",
                "Tempoross",
                "Theatre of Blood",
                "Thermonuclear Smoke Devil",
                "Tombs of Amascut",
                "ToA Entry",
                "ToA Expert",
                "Vardorvis",
                "Venenatis",
                "Vet'ion",
                "Vorkath",
                "Whisperer",
                "Wintertodt",
                "Zalcano",
                "Zulrah",
            ) +
            names(
                ClanEventType.Skilling,
                "Agility",
                "Combat",
                "Construction",
                "Cooking",
                "Crafting",
                "Farming",
                "Firemaking",
                "Fishing",
                "Fletching",
                "Herblore",
                "Hunter",
                "Mining",
                "Prayer",
                "Runecrafting",
                "Slayer",
                "Smithing",
                "Thieving",
                "Woodcutting",
            ) +
            names(
                ClanEventType.Pvp,
                "1 Defence",
                "Berserker",
                "Camelot",
                "Castle Wars",
                "Chaos Altar",
                "Deep Wilderness",
                "Dharoks",
                "Edgeville",
                "Free to play",
                "Grand Exchange",
                "Last Man Standing",
                "Lumbridge",
                "Med-Level",
                "Revenants",
                "Rimmington",
                "Soul Wars",
            ) +
            names(
                ClanEventType.Social,
                "Bank Standing",
                "Barbarian Assault",
                "Clan Hall Gathering",
                "Clues",
                "FashionScape",
                "Games Room",
                "Hide and Seek",
                "House party",
                "Questing",
                "Quiz",
            )

    private val byKey: Map<String, ClanEventActivity> = all.associateBy { it.storageKey }

    init {
        require(byKey.size == all.size) { "Clan event activity keys must be unique." }
    }

    public fun find(storageKey: String): ClanEventActivity? = byKey[storageKey]

    private fun names(type: ClanEventType, vararg displayNames: String): List<ClanEventActivity> =
        displayNames.map { displayName ->
            ClanEventActivity(displayName.toStorageKey(), displayName, type)
        }

    private fun String.toStorageKey(): String =
        lowercase().replace("'", "").replace(Regex("[^a-z0-9]+"), "_").trim('_')
}

public data class ClanEventDraft(
    val type: ClanEventType,
    val subtype: ClanEventSubtype,
    val activityKey: String,
    val daysUntilStart: Int,
    val startHourUtc: Int,
    val startMinuteUtc: Int,
    val durationTicks: Long,
    val minimumJoinRank: Int,
    val worldId: Int,
)

public object ClanEventRules {
    public fun validationError(event: ClanEventDraft): String? {
        val activity =
            ClanEventActivities.find(event.activityKey)
                ?: return "The clan event activity is not valid."
        if (activity.type != null && activity.type != event.type) {
            return "The clan event activity does not match its type."
        }
        if (event.daysUntilStart !in 0..ClanDomainLimits.MAX_EVENT_DAYS_IN_ADVANCE) {
            return "A clan event may be scheduled at most 30 days in advance."
        }
        if (event.startHourUtc !in 0..23 || event.startMinuteUtc !in 0..59) {
            return "The clan event UTC time is not valid."
        }
        if (
            event.durationTicks !in
                ClanDomainLimits.GAME_TICKS_PER_DAY..ClanDomainLimits.MAX_EVENT_DURATION_TICKS
        ) {
            return "A clan event duration must be between 1 and 30 days."
        }
        if (!ClanRanks.isValidThreshold(event.minimumJoinRank, allowGuest = true)) {
            return "The clan event join rank is not valid."
        }
        if (event.worldId !in 1..Short.MAX_VALUE) {
            return "The clan event world is not valid."
        }
        return null
    }
}
