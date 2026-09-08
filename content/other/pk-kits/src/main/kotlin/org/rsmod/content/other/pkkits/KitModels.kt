@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.other.pkkits

import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.game.inv.InvObj

public enum class KitCategory(public val displayName: String) {
    Pure("Pure"),
    Zerker("Zerker"),
    Med("Med"),
    Main("Main"),
    Max("Max"),
    Dharok("Dharok"),
    Tank("Tank"),
    Lms("LMS"),
    Vengeance("Vengeance"),
    NoHonour("No-Honour"),
    F2P("F2P"),
    RiskFight("Risk Fight"),
    Custom("Custom"),
}

public enum class KitLoadPolicy {
    FREE_SPAWN,
    REQUIRE_OWNERSHIP,
    MIXED,
}

public enum class KitItemPolicyOverride {
    INHERIT,
    FREE_SPAWN,
    REQUIRE_OWNERSHIP,
    DISALLOWED,
}

/**
 * OpenRune currently exposes the OSRS prayer book only. The field remains versioned so a future
 * alternate-prayer implementation can be added without invalidating saved kits.
 */
public enum class KitPrayerBook {
    STANDARD
}

public data class KitStats(
    val attack: Int,
    val strength: Int,
    val defence: Int,
    val hitpoints: Int,
    val ranged: Int,
    val magic: Int,
    val prayer: Int,
) {
    public fun asMap(): Map<String, Int> =
        linkedMapOf(
            "stat.attack" to attack,
            "stat.strength" to strength,
            "stat.defence" to defence,
            "stat.hitpoints" to hitpoints,
            "stat.ranged" to ranged,
            "stat.magic" to magic,
            "stat.prayer" to prayer,
        )

    public fun validate(): List<String> =
        asMap().mapNotNull { (stat, level) ->
            if (level in 1..99) null else "$stat must be between 1 and 99 (was $level)"
        }
}

public data class KitCombatSettings(
    val spellbook: Spellbook = Spellbook.Standard,
    val prayerBook: KitPrayerBook = KitPrayerBook.STANDARD,
    val quickPrayers: Int = 0,
    val combatStance: Int = 0,
    val meleeStyle: Int = 0,
    val autocastSpell: Int = 0,
    val autocastDefensive: Boolean = false,
    val specialEnergy: Int = 1000,
    val runEnergy: Int = 1000,
    val runEnabled: Boolean = true,
) {
    public fun validate(): List<String> = buildList {
        if (combatStance !in 0..3) add("combat stance must be between 0 and 3")
        if (meleeStyle !in 0..3) add("melee style must be between 0 and 3")
        if (autocastSpell < 0) add("autocast spell cannot be negative")
        if (quickPrayers < 0) add("quick-prayer mask contains unsupported bits")
        if (specialEnergy !in 0..1000) add("special energy must be between 0 and 1000")
        if (runEnergy !in 0..1000) add("run energy must be between 0 and 1000")
    }
}

public data class KitItem(
    val internalName: String,
    val id: Int,
    val count: Int,
    val vars: Int = 0,
    val slot: Int,
    val policyOverride: KitItemPolicyOverride = KitItemPolicyOverride.INHERIT,
) {
    public val obj: InvObj
        get() = InvObj(id, count, vars)
}

public data class KitDefinition(
    val id: String,
    val name: String,
    val category: KitCategory,
    val description: String,
    val inventory: List<KitItem>,
    val equipment: List<KitItem>,
    val stats: KitStats,
    val settings: KitCombatSettings,
    val loadPolicy: KitLoadPolicy,
    val riskValue: Long,
    val enabled: Boolean = true,
    val source: KitSource = KitSource.PREMADE,
)

public enum class KitSource {
    PREMADE,
    CUSTOM,
}

public data class CustomKit(
    val slot: Int,
    val id: String,
    val name: String,
    val category: KitCategory,
    val inventory: List<KitItem>,
    val equipment: List<KitItem>,
    val stats: KitStats,
    val settings: KitCombatSettings,
    val loadPolicy: KitLoadPolicy = KitLoadPolicy.MIXED,
    val createdAt: Long,
    val updatedAt: Long,
) {
    public fun asDefinition(riskValue: Long): KitDefinition =
        KitDefinition(
            id = id,
            name = name,
            category = category,
            description = "Player-saved custom PK kit.",
            inventory = inventory,
            equipment = equipment,
            stats = stats,
            settings = settings,
            loadPolicy = loadPolicy,
            riskValue = riskValue,
            source = KitSource.CUSTOM,
        )
}

public data class KitReference(val source: KitSource, val id: String) {
    public fun encode(): String = "${source.name.lowercase()}:$id"

    public companion object {
        public fun decode(value: String?): KitReference? {
            val split = value?.split(':', limit = 2) ?: return null
            if (split.size != 2) return null
            val source =
                KitSource.entries.firstOrNull { it.name.equals(split[0], true) } ?: return null
            return KitReference(source, split[1])
        }
    }
}

public data class KitPlayerData(
    val version: Int = CURRENT_VERSION,
    val slotTier: Int = 0,
    val defaultKit: KitReference? = null,
    val customKits: List<CustomKit> = emptyList(),
    val openAfterDeath: Boolean = false,
) {
    public companion object {
        public const val CURRENT_VERSION: Int = 2
    }
}

public sealed interface KitLoadResult {
    public data class Success(val kit: KitDefinition) : KitLoadResult

    public data class Blocked(val reasons: List<String>) : KitLoadResult

    public data class Failed(val reasons: List<String>) : KitLoadResult
}
