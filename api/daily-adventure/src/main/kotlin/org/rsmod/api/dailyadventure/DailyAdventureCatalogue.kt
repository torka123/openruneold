package org.rsmod.api.dailyadventure

public object DailyAdventureFeatures {
    public const val BOSS_REGISTRY: String = "boss_registry"
    public const val REVENANTS: String = "revenants"
    public const val BARROWS: String = "barrows"
    public const val WILDERNESS_BOSSES: String = "wilderness_bosses"
    public const val RAIDS: String = "raids"
    public const val COMBAT_MINIGAMES: String = "combat_minigames"
    public const val PVP_ANTI_ABUSE: String = "pvp_anti_abuse"
    public const val BOUNTY_HUNTER: String = "bounty_hunter"
    public const val HIGH_LEVEL_RESOURCES: String = "high_level_resources"
    public const val GIANTS_FOUNDRY: String = "giants_foundry"
    public const val SKILLING_ACTIVITIES: String = "skilling_activities"

    public val stableDefaults: Set<String> =
        setOf(
            BOSS_REGISTRY,
            REVENANTS,
            BARROWS,
            WILDERNESS_BOSSES,
            RAIDS,
            COMBAT_MINIGAMES,
            PVP_ANTI_ABUSE,
            HIGH_LEVEL_RESOURCES,
            GIANTS_FOUNDRY,
            SKILLING_ACTIVITIES,
        )
}

public object DailyAdventureCatalogue {
    public val tasks: List<DailyAdventureTaskDefinition> =
        listOf(
            combat("C01", DailyAdventureDifficulty.Easy, "Monster Hunter", "Kill 25 eligible monsters.", 25, 15,
                tags = setOf(DailyAdventureTag.GenericKill), match = action(DailyAdventureAction.NpcKill)),
            combat("C02", DailyAdventureDifficulty.Easy, "Combat Practice", "Deal 2,500 actual damage to monsters.", 2_500, 15,
                tags = setOf(DailyAdventureTag.Damage), match = action(DailyAdventureAction.NpcDamage)),
            combat("C03", DailyAdventureDifficulty.Easy, "Melee Practice", "Deal the final blow to 12 monsters with melee.", 12, 15,
                tags = setOf(DailyAdventureTag.CombatStyle), match = combatKill("melee")),
            combat("C04", DailyAdventureDifficulty.Easy, "Ranged Practice", "Deal the final blow to 12 monsters with Ranged.", 12, 15,
                tags = setOf(DailyAdventureTag.CombatStyle), match = combatKill("ranged")),
            combat("C05", DailyAdventureDifficulty.Easy, "Magic Practice", "Deal the final blow to 12 monsters with Magic.", 12, 15,
                tags = setOf(DailyAdventureTag.CombatStyle), match = combatKill("magic")),
            combat("C06", DailyAdventureDifficulty.Easy, "Special Tactics", "Land damage with 8 special-attack activations.", 8, 15,
                tags = setOf(DailyAdventureTag.TimedOrSpecial), match = positive(DailyAdventureAction.SpecialAttack)),
            combat("C07", DailyAdventureDifficulty.Easy, "On Assignment", "Kill 15 monsters assigned by your Slayer master.", 15, 20,
                match = { event -> if (event.action == DailyAdventureAction.NpcKill && event.slayerTarget) event.amount else 0L }),
            combat("C08", DailyAdventureDifficulty.Easy, "Into the Wilderness", "Kill 12 eligible monsters in the Wilderness.", 12, 20,
                tags = setOf(DailyAdventureTag.Wilderness), match = wilderness(DailyAdventureAction.NpcKill)),
            combat("C26", DailyAdventureDifficulty.Easy, "Accurate Strikes", "Land 30 damaging attacks on eligible monsters.", 30, 15,
                match = action(DailyAdventureAction.NpcHit)),
            combat("C09", DailyAdventureDifficulty.Medium, "Monster Sweep", "Kill 60 eligible monsters.", 60, 25,
                tags = setOf(DailyAdventureTag.GenericKill), match = action(DailyAdventureAction.NpcKill)),
            combat("C10", DailyAdventureDifficulty.Medium, "Heavy Hitter", "Deal 10,000 actual damage to monsters.", 10_000, 25,
                tags = setOf(DailyAdventureTag.Damage), match = action(DailyAdventureAction.NpcDamage)),
            combat("C11", DailyAdventureDifficulty.Medium, "Boss Hunter", "Defeat 3 registered bosses.", 3, 30,
                features = setOf(DailyAdventureFeatures.BOSS_REGISTRY), tags = setOf(DailyAdventureTag.Boss), match = action(DailyAdventureAction.BossKill)),
            combat("C12", DailyAdventureDifficulty.Medium, "Boss Punisher", "Deal 5,000 actual damage to registered bosses.", 5_000, 30,
                features = setOf(DailyAdventureFeatures.BOSS_REGISTRY), tags = setOf(DailyAdventureTag.Boss, DailyAdventureTag.Damage), match = action(DailyAdventureAction.BossDamage)),
            combat("C13", DailyAdventureDifficulty.Medium, "Revenant Cull", "Kill 20 revenants.", 20, 30,
                features = setOf(DailyAdventureFeatures.REVENANTS), tags = setOf(DailyAdventureTag.Wilderness), match = categoryKill("revenant")),
            combat("C14", DailyAdventureDifficulty.Medium, "Dragon Slayer", "Kill 20 dragons.", 20, 25, match = categoryKill("dragon")),
            combat("C15", DailyAdventureDifficulty.Medium, "Demon Bane", "Kill 25 demons.", 25, 25, match = categoryKill("demon")),
            combat("C16", DailyAdventureDifficulty.Medium, "Slayer Duty", "Complete one Slayer assignment.", 1, 30, match = action(DailyAdventureAction.SlayerAssignment)),
            combat("C17", DailyAdventureDifficulty.Medium, "Barrows Raider", "Loot 2 completed Barrows chests.", 2, 30,
                features = setOf(DailyAdventureFeatures.BARROWS), tags = setOf(DailyAdventureTag.RaidOrMinigame), match = action(DailyAdventureAction.BarrowsChest)),
            combat("C18", DailyAdventureDifficulty.Medium, "Wilderness Boss Hunt", "Defeat a registered Wilderness boss.", 1, 35,
                features = setOf(DailyAdventureFeatures.WILDERNESS_BOSSES), tags = setOf(DailyAdventureTag.Boss, DailyAdventureTag.Wilderness), match = wilderness(DailyAdventureAction.BossKill)),
            combat("C19", DailyAdventureDifficulty.Hard, "Boss Gauntlet", "Defeat 8 registered bosses.", 8, 45,
                features = setOf(DailyAdventureFeatures.BOSS_REGISTRY), tags = setOf(DailyAdventureTag.Boss), match = action(DailyAdventureAction.BossKill)),
            combat("C20", DailyAdventureDifficulty.Hard, "Wilderness Dominance", "Defeat 3 registered Wilderness bosses.", 3, 50,
                features = setOf(DailyAdventureFeatures.WILDERNESS_BOSSES), tags = setOf(DailyAdventureTag.Boss, DailyAdventureTag.Wilderness), match = wilderness(DailyAdventureAction.BossKill)),
            combat("C21", DailyAdventureDifficulty.Hard, "Raid Complete", "Complete one enabled, reward-qualified raid.", 1, 50,
                features = setOf(DailyAdventureFeatures.RAIDS), tags = setOf(DailyAdventureTag.RaidOrMinigame, DailyAdventureTag.RequiresOtherPlayers), match = qualified(DailyAdventureAction.RaidCompletion)),
            combat("C22", DailyAdventureDifficulty.Hard, "Combat Minigame Veteran", "Complete 3 enabled combat-minigame rounds.", 3, 45,
                features = setOf(DailyAdventureFeatures.COMBAT_MINIGAMES), tags = setOf(DailyAdventureTag.RaidOrMinigame, DailyAdventureTag.RequiresOtherPlayers), match = qualified(DailyAdventureAction.CombatMinigameRound)),
            combat("C23", DailyAdventureDifficulty.Hard, "Player Killer", "Defeat 2 legitimate PvP opponents.", 2, 50,
                features = setOf(DailyAdventureFeatures.PVP_ANTI_ABUSE), tags = setOf(DailyAdventureTag.Pvp, DailyAdventureTag.RequiresOtherPlayers), match = qualified(DailyAdventureAction.PlayerKill)),
            combat("C24", DailyAdventureDifficulty.Hard, "Bounty Hunter", "Defeat one legitimate assigned Bounty Hunter target.", 1, 50,
                features = setOf(DailyAdventureFeatures.BOUNTY_HUNTER), tags = setOf(DailyAdventureTag.Pvp, DailyAdventureTag.RequiresOtherPlayers), match = qualified(DailyAdventureAction.BountyTargetKill)),
            combat("C25", DailyAdventureDifficulty.Hard, "PvP Pressure", "Deal 1,500 legitimate PvP damage.", 1_500, 45,
                features = setOf(DailyAdventureFeatures.PVP_ANTI_ABUSE), tags = setOf(DailyAdventureTag.Pvp, DailyAdventureTag.Damage, DailyAdventureTag.RequiresOtherPlayers), match = qualified(DailyAdventureAction.PlayerDamage)),
            combat("C27", DailyAdventureDifficulty.Easy, "Combat Rhythm", "Land 50 damaging attacks on eligible monsters.", 50, 15,
                match = action(DailyAdventureAction.NpcHit)),
            combat("C28", DailyAdventureDifficulty.Medium, "Dangerous Foes", "Kill 100 eligible monsters.", 100, 30,
                tags = setOf(DailyAdventureTag.GenericKill), match = action(DailyAdventureAction.NpcKill)),
            combat("C29", DailyAdventureDifficulty.Hard, "Boss Onslaught", "Deal 20,000 actual damage to registered bosses.", 20_000, 50,
                features = setOf(DailyAdventureFeatures.BOSS_REGISTRY), tags = setOf(DailyAdventureTag.Boss, DailyAdventureTag.Damage), match = action(DailyAdventureAction.BossDamage)),
            combat("C30", DailyAdventureDifficulty.Hard, "Monster Eradicator", "Kill 300 eligible monsters.", 300, 45,
                tags = setOf(DailyAdventureTag.GenericKill), match = action(DailyAdventureAction.NpcKill)),
            combat("C31", DailyAdventureDifficulty.Hard, "Damage Dealer", "Deal 50,000 actual damage to eligible monsters.", 50_000, 45,
                tags = setOf(DailyAdventureTag.Damage), match = action(DailyAdventureAction.NpcDamage)),
            combat("C32", DailyAdventureDifficulty.Hard, "Wilderness Sweep", "Kill 75 eligible monsters in the Wilderness.", 75, 50,
                tags = setOf(DailyAdventureTag.Wilderness), match = wilderness(DailyAdventureAction.NpcKill)),
            combat("C33", DailyAdventureDifficulty.Hard, "Slayer Marathon", "Kill 100 monsters assigned by your Slayer master.", 100, 50,
                match = { event -> if (event.action == DailyAdventureAction.NpcKill && event.slayerTarget) event.amount else 0L }),
            skilling("S01", DailyAdventureDifficulty.Easy, "Prospector", "Mine 60 ores.", 60, 15, "mining"),
            skilling("S02", DailyAdventureDifficulty.Easy, "Lumberjack", "Chop 60 logs.", 60, 15, "woodcutting"),
            skilling("S03", DailyAdventureDifficulty.Easy, "Angler", "Catch 60 fish.", 60, 15, "fishing"),
            skilling("S04", DailyAdventureDifficulty.Easy, "Chef", "Successfully cook 60 pieces of food.", 60, 15, "cooking"),
            skilling("S05", DailyAdventureDifficulty.Easy, "Firestarter", "Burn 50 logs.", 50, 15, "firemaking"),
            skilling("S06", DailyAdventureDifficulty.Easy, "Light Fingers", "Complete 60 successful thefts.", 60, 15, "thieving"),
            skilling("S07", DailyAdventureDifficulty.Easy, "Fletcher", "Complete 50 Fletching actions.", 50, 15, "fletching"),
            skilling("S08", DailyAdventureDifficulty.Easy, "Crafter", "Complete 40 Crafting actions.", 40, 15, "crafting"),
            task("S26", DailyAdventurePath.Skilling, DailyAdventureDifficulty.Easy, "Skilling Practice", "Earn 25,000 base non-combat XP.", 25_000, 15,
                match = { event -> if (event.action == DailyAdventureAction.BaseExperience && event.skill != null && event.skill !in COMBAT_SKILLS) event.amount else 0L }),
            skilling("S09", DailyAdventureDifficulty.Medium, "Smith", "Complete 35 Smithing production actions.", 35, 25, "smithing"),
            skilling("S10", DailyAdventureDifficulty.Medium, "Smelter", "Smelt 50 bars.", 50, 25, "smithing", activity = "smelting"),
            skilling("S11", DailyAdventureDifficulty.Medium, "Herbalist", "Mix 30 completed potions.", 30, 25, "herblore"),
            skilling("S12", DailyAdventureDifficulty.Medium, "Runecrafter", "Consume or craft with 200 essence.", 200, 25, "runecrafting"),
            skilling("S13", DailyAdventureDifficulty.Medium, "Agility Runner", "Complete 8 full Agility laps.", 8, 25, "agility", action = DailyAdventureAction.AgilityLap),
            skilling("S14", DailyAdventureDifficulty.Medium, "Farmer", "Harvest 50 produce.", 50, 25, "farming"),
            skilling("S15", DailyAdventureDifficulty.Medium, "Hunter", "Successfully catch 35 Hunter creatures.", 35, 25, "hunter"),
            task("S16", DailyAdventurePath.Skilling, DailyAdventureDifficulty.Medium, "Builder", "Build 12 legitimate Construction hotspots; one hotspot counts at most 3 times.", 12, 30,
                skills = setOf("construction"), perDimensionCap = 3, match = action(DailyAdventureAction.ConstructionHotspot)),
            skilling("S17", DailyAdventureDifficulty.Medium, "Devout", "Consume 75 eligible bones or ashes.", 75, 25, "prayer"),
            task("S18", DailyAdventurePath.Skilling, DailyAdventureDifficulty.Medium, "Gathering Day", "Gather 200 resources, including at least 40 Mining, Woodcutting, and Fishing actions.", 200, 30,
                tags = setOf(DailyAdventureTag.MultiSkill), skills = setOf("mining", "woodcutting", "fishing"), requiredDimensions = setOf("mining", "woodcutting", "fishing"), minimumPerDimension = 40,
                match = skillSet(setOf("mining", "woodcutting", "fishing"))),
            task("S19", DailyAdventurePath.Skilling, DailyAdventureDifficulty.Hard, "Master Gatherer", "Gather 450 resources across gathering skills.", 450, 45,
                tags = setOf(DailyAdventureTag.MultiSkill), skills = setOf("mining", "woodcutting", "fishing", "hunter", "farming"), minimumDistinctDimensions = 3,
                match = skillSet(setOf("mining", "woodcutting", "fishing", "hunter", "farming"))),
            task("S20", DailyAdventurePath.Skilling, DailyAdventureDifficulty.Hard, "Master Artisan", "Complete 200 artisan actions across at least 3 skills.", 200, 45,
                tags = setOf(DailyAdventureTag.MultiSkill), skills = setOf("cooking", "smithing", "fletching", "crafting", "herblore", "runecrafting", "construction"), minimumDistinctDimensions = 3,
                match = skillSet(setOf("cooking", "smithing", "fletching", "crafting", "herblore", "runecrafting", "construction"))),
            task("S21", DailyAdventurePath.Skilling, DailyAdventureDifficulty.Hard, "Skilling Marathon", "Earn 500,000 base non-combat XP across at least 3 skills.", 500_000, 45,
                tags = setOf(DailyAdventureTag.MultiSkill), minimumDistinctDimensions = 3,
                match = { event -> if (event.action == DailyAdventureAction.BaseExperience && event.skill != null && event.skill !in COMBAT_SKILLS) event.amount else 0L }),
            task("S22", DailyAdventurePath.Skilling, DailyAdventureDifficulty.Hard, "High-Level Harvest", "Gather 125 level-70-or-higher resources.", 125, 45,
                features = setOf(DailyAdventureFeatures.HIGH_LEVEL_RESOURCES),
                match = { event -> if (event.action == DailyAdventureAction.SkillingAction && event.category == "level70_resource") event.amount else 0L }),
            task("S23", DailyAdventurePath.Skilling, DailyAdventureDifficulty.Hard, "Wilderness Skiller", "Complete 120 active Wilderness skilling actions.", 120, 50,
                tags = setOf(DailyAdventureTag.Wilderness), match = wilderness(DailyAdventureAction.SkillingAction)),
            task("S24", DailyAdventurePath.Skilling, DailyAdventureDifficulty.Hard, "Giants' Foundry Smith", "Complete 2 Giants' Foundry commissions.", 2, 50,
                features = setOf(DailyAdventureFeatures.GIANTS_FOUNDRY), tags = setOf(DailyAdventureTag.SpecialActivity), match = qualified(DailyAdventureAction.GiantsFoundryCommission)),
            task("S25", DailyAdventurePath.Skilling, DailyAdventureDifficulty.Hard, "Skilling Activity Veteran", "Complete 3 enabled skilling-activity rounds.", 3, 50,
                features = setOf(DailyAdventureFeatures.SKILLING_ACTIVITIES), tags = setOf(DailyAdventureTag.SpecialActivity), match = qualified(DailyAdventureAction.SkillingActivityRound)),
            task("S27", DailyAdventurePath.Skilling, DailyAdventureDifficulty.Hard, "Skill Veteran", "Earn 750,000 base non-combat XP.", 750_000, 50,
                match = { event -> if (event.action == DailyAdventureAction.BaseExperience && event.skill != null && event.skill !in COMBAT_SKILLS) event.amount else 0L }),
            task("S28", DailyAdventurePath.Skilling, DailyAdventureDifficulty.Hard, "Production Master", "Complete 300 skilling actions.", 300, 45,
                match = action(DailyAdventureAction.SkillingAction)),
            task("S29", DailyAdventurePath.Skilling, DailyAdventureDifficulty.Hard, "Dedicated Training", "Complete 600 skilling actions across at least 4 skills.", 600, 50,
                tags = setOf(DailyAdventureTag.MultiSkill), minimumDistinctDimensions = 4, match = action(DailyAdventureAction.SkillingAction)),
            task("S30", DailyAdventurePath.Skilling, DailyAdventureDifficulty.Medium, "Skilling Routine", "Complete 100 skilling actions across at least 2 skills.", 100, 30,
                tags = setOf(DailyAdventureTag.MultiSkill), minimumDistinctDimensions = 2, match = action(DailyAdventureAction.SkillingAction)),
            task("S31", DailyAdventurePath.Skilling, DailyAdventureDifficulty.Medium, "Experience Day", "Earn 150,000 base non-combat XP.", 150_000, 30,
                match = { event -> if (event.action == DailyAdventureAction.BaseExperience && event.skill != null && event.skill !in COMBAT_SKILLS) event.amount else 0L }),
        )

    public val byId: Map<String, DailyAdventureTaskDefinition> = tasks.associateBy { it.id }

    init {
        require(byId.size == tasks.size) { "Daily Adventure task IDs must be unique" }
        require(tasks.count { it.path == DailyAdventurePath.Combat } >= 25)
        require(tasks.count { it.path == DailyAdventurePath.Skilling } >= 25)
    }

    private val COMBAT_SKILLS: Set<String> = setOf("attack", "strength", "defence", "hitpoints", "ranged", "magic", "prayer")

    private fun combat(id: String, difficulty: DailyAdventureDifficulty, name: String, description: String, goal: Long, reward: Long,
        features: Set<String> = emptySet(), tags: Set<DailyAdventureTag> = emptySet(), match: (DailyAdventureProgressEvent) -> Long): DailyAdventureTaskDefinition =
        task(id, DailyAdventurePath.Combat, difficulty, name, description, goal, reward, features, tags, match = match)

    private fun skilling(id: String, difficulty: DailyAdventureDifficulty, name: String, description: String, goal: Long, reward: Long,
        skill: String, activity: String? = null, action: DailyAdventureAction = DailyAdventureAction.SkillingAction): DailyAdventureTaskDefinition =
        task(id, DailyAdventurePath.Skilling, difficulty, name, description, goal, reward, skills = setOf(skill),
            match = { event -> if (event.action == action && event.skill == skill && (activity == null || event.activity == activity)) event.amount else 0L })

    private fun task(id: String, path: DailyAdventurePath, difficulty: DailyAdventureDifficulty, name: String, description: String,
        goal: Long, reward: Long, features: Set<String> = emptySet(), tags: Set<DailyAdventureTag> = emptySet(), skills: Set<String> = emptySet(),
        requiredDimensions: Set<String> = emptySet(), minimumPerDimension: Long = 0L, minimumDistinctDimensions: Int = 0,
        perDimensionCap: Long? = null, match: (DailyAdventureProgressEvent) -> Long): DailyAdventureTaskDefinition =
        DailyAdventureTaskDefinition(id = id, path = path, difficulty = difficulty, name = name, description = description, goal = goal,
            reward = reward, requiredFeatures = features, tags = tags, skills = skills, requiredDimensions = requiredDimensions,
            minimumPerDimension = minimumPerDimension, minimumDistinctDimensions = minimumDistinctDimensions,
            perDimensionCap = perDimensionCap, matcher = match)

    private fun action(action: DailyAdventureAction): (DailyAdventureProgressEvent) -> Long =
        { event -> if (event.action == action && event.successful) event.amount else 0L }
    private fun positive(action: DailyAdventureAction): (DailyAdventureProgressEvent) -> Long =
        { event -> if (event.action == action && event.successful && event.amount > 0L) 1L else 0L }
    private fun qualified(action: DailyAdventureAction): (DailyAdventureProgressEvent) -> Long =
        { event -> if (event.action == action && event.successful && event.rewardQualified) event.amount else 0L }
    private fun wilderness(action: DailyAdventureAction): (DailyAdventureProgressEvent) -> Long =
        { event -> if (event.action == action && event.successful && event.wilderness) event.amount else 0L }
    private fun combatKill(style: String): (DailyAdventureProgressEvent) -> Long =
        { event -> if (event.action == DailyAdventureAction.NpcKill && event.combatStyle == style) event.amount else 0L }
    private fun categoryKill(category: String): (DailyAdventureProgressEvent) -> Long =
        { event -> if (event.action == DailyAdventureAction.NpcKill && event.category == category) event.amount else 0L }
    private fun skillSet(skills: Set<String>): (DailyAdventureProgressEvent) -> Long =
        { event -> if (event.action == DailyAdventureAction.SkillingAction && event.skill in skills) event.amount else 0L }
}
