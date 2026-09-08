package org.rsmod.content.skills.hunter

/**
 * Hunter creature and trap definitions ported from the donor's Hunter package.
 *
 * XP remains raw OSRS base XP.  [ProtectedAccess.statAdvance] applies OpenRune's configured
 * multiplier exactly once, which keeps Hunter consistent with the other rebuilt skills.
 */
internal object HunterDefinitions {
    const val BIRD_SNARE = 10006
    const val BOX_TRAP = 10008
    const val SMALL_FISHING_NET = 303
    const val ROPE = 954
    const val BUTTERFLY_NET = 10010
    const val MAGIC_BUTTERFLY_NET = 11259
    const val EMPTY_IMPLING_JAR = 11260
    const val BUTTERFLY_JAR = 10012

    val birdSnare = TrapKind(
        itemId = BIRD_SNARE,
        readyLocId = 9345,
        failedLocId = 9344,
        setAnimation = 5208,
        resetAnimation = 5207,
        displayName = "bird snare",
    )

    val boxTrap = TrapKind(
        itemId = BOX_TRAP,
        readyLocId = 9380,
        failedLocId = 9385,
        setAnimation = 5208,
        resetAnimation = 5212,
        displayName = "box trap",
    )

    val trapKinds = listOf(birdSnare, boxTrap)

    val trapCreatures = listOf(
        TrapCreature(5549, "crimson swift", 1, 34.0, 10088, birdSnare, 9373),
        TrapCreature(5551, "golden warbler", 5, 47.0, 10090, birdSnare, 9377),
        TrapCreature(5552, "copper longtail", 9, 61.0, 10091, birdSnare, 9379),
        TrapCreature(5550, "cerulean twitch", 11, 64.5, 10089, birdSnare, 9375),
        TrapCreature(5548, "tropical wagtail", 19, 95.8, 10087, birdSnare, 9348),
        TrapCreature(2910, "chinchompa", 53, 198.4, 10033, boxTrap, 9384),
        TrapCreature(2911, "red chinchompa", 63, 265.0, 10034, boxTrap, 9384),
        TrapCreature(2912, "black chinchompa", 73, 315.4, 11959, boxTrap, 9384),
    )

    /**
     * Salamanders are exposed through their cache Catch/Trap interaction.  A fishing net and rope
     * are still required, and the NPC uses the normal hide/respawn lifecycle rather than being
     * deleted.  Tree-pair placement is handled separately once all relevant cache tree pairs are
     * audited; this prevents an incorrect temporary object mapping from trapping players.
     */
    val netCreatures = listOf(
        DirectCreature(2906, "swamp lizard", 29, 152.0, 10149, DirectMethod.NET),
        DirectCreature(2903, "orange salamander", 47, 224.0, 10146, DirectMethod.NET),
        DirectCreature(2904, "red salamander", 59, 272.0, 10147, DirectMethod.NET),
        DirectCreature(2905, "black salamander", 67, 319.2, 10148, DirectMethod.NET),
    )

    val implings = listOf(
        DirectCreature(1635, "baby impling", 17, 27.0, 11238, DirectMethod.IMPLING),
        DirectCreature(1636, "young impling", 22, 65.0, 11240, DirectMethod.IMPLING),
        DirectCreature(1637, "gourmet impling", 28, 113.0, 11242, DirectMethod.IMPLING),
        DirectCreature(1638, "earth impling", 36, 177.0, 11244, DirectMethod.IMPLING),
        DirectCreature(1639, "essence impling", 42, 255.0, 11246, DirectMethod.IMPLING),
        DirectCreature(1640, "eclectic impling", 50, 289.0, 11248, DirectMethod.IMPLING),
        DirectCreature(1641, "nature impling", 58, 353.0, 11250, DirectMethod.IMPLING),
        DirectCreature(1642, "magpie impling", 65, 409.0, 11252, DirectMethod.IMPLING),
        DirectCreature(1643, "ninja impling", 74, 481.0, 11254, DirectMethod.IMPLING),
        DirectCreature(1644, "dragon impling", 83, 553.0, 11256, DirectMethod.IMPLING),
        DirectCreature(7233, "lucky impling", 89, 650.0, 19732, DirectMethod.IMPLING),
    )

    val trapByNpcId = trapCreatures.associateBy(TrapCreature::npcId)
    private val transformedImplings =
        implings.mapIndexed { index, creature -> creature.copy(npcId = 1645 + index) }

    private val additionalDirect = buildList {
        // Falconry targets use the cache Catch action; the dedicated falcon rental is not present in
        // this source, so the catch is intentionally direct while preserving the real level, XP and fur.
        add(DirectCreature(5531, "spotted kebbit", 43, 104.0, 0, DirectMethod.FALCONRY, "Spotted kebbit fur"))
        add(DirectCreature(5532, "dark kebbit", 57, 132.0, 0, DirectMethod.FALCONRY, "Dark kebbit fur"))
        add(DirectCreature(5533, "dashing kebbit", 69, 156.0, 0, DirectMethod.FALCONRY, "Dashing kebbit fur"))

        add(DirectCreature(5553, "black warlock", 45, 54.0, 0, DirectMethod.BUTTERFLY, "Black warlock"))
        add(DirectCreature(5554, "snowy knight", 35, 111.5, 0, DirectMethod.BUTTERFLY, "Snowy knight"))
        add(DirectCreature(5555, "sapphire glacialis", 25, 34.0, 0, DirectMethod.BUTTERFLY, "Sapphire glacialis"))
        add(DirectCreature(5556, "ruby harvest", 15, 24.0, 0, DirectMethod.BUTTERFLY, "Ruby harvest"))
        add(DirectCreature(7302, "lucky impling", 89, 380.0, 19732, DirectMethod.IMPLING))

        add(DirectCreature(7587, "guanic bat", 1, 5.0, 0, DirectMethod.BAT, "Raw guanic bat"))
        add(DirectCreature(7588, "prael bat", 15, 9.0, 0, DirectMethod.BAT, "Raw prael bat"))
        add(DirectCreature(7589, "giral bat", 30, 11.0, 0, DirectMethod.BAT, "Raw giral bat"))
        add(DirectCreature(7590, "phluxia bat", 45, 14.0, 0, DirectMethod.BAT, "Raw phluxia bat"))
        add(DirectCreature(7591, "kryket bat", 60, 17.0, 0, DirectMethod.BAT, "Raw kryket bat"))
        add(DirectCreature(7592, "murng bat", 75, 20.0, 0, DirectMethod.BAT, "Raw murng bat"))
        add(DirectCreature(7593, "psykk bat", 90, 23.0, 0, DirectMethod.BAT, "Raw psykk bat"))

        for (id in 8741..8757) {
            add(DirectCreature(id, "crystal impling", 80, 280.0, 0, DirectMethod.IMPLING, "Crystal impling jar"))
        }
        add(DirectCreature(12770, "sunlight moth", 65, 74.0, 0, DirectMethod.BUTTERFLY, "Sunlight moth"))
        add(DirectCreature(12771, "moonlight moth", 75, 84.0, 0, DirectMethod.BUTTERFLY, "Moonlight moth"))
        add(DirectCreature(12772, "moonlight moth", 75, 84.0, 0, DirectMethod.BUTTERFLY, "Moonlight moth"))
    }
    val directByNpcId = (netCreatures + implings + transformedImplings + additionalDirect).associateBy(DirectCreature::npcId)
    val trappedObjectIds = trapCreatures.mapTo(hashSetOf()) { it.caughtLocId }

    fun maxTraps(level: Int): Int = when {
        level >= 80 -> 5
        level >= 60 -> 4
        level >= 40 -> 3
        level >= 20 -> 2
        else -> 1
    }
}

internal data class TrapKind(
    val itemId: Int,
    val readyLocId: Int,
    val failedLocId: Int,
    val setAnimation: Int,
    val resetAnimation: Int,
    val displayName: String,
)

internal data class TrapCreature(
    val npcId: Int,
    val displayName: String,
    val level: Int,
    val baseXp: Double,
    val lootId: Int,
    val trap: TrapKind,
    val caughtLocId: Int,
)

internal enum class DirectMethod { IMPLING, NET, BUTTERFLY, FALCONRY, BAT }

internal data class DirectCreature(
    val npcId: Int,
    val displayName: String,
    val level: Int,
    val baseXp: Double,
    val lootId: Int,
    val method: DirectMethod,
    val lootName: String? = null,
)
