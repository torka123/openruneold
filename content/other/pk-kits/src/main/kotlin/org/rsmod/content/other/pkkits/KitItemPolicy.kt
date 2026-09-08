package org.rsmod.content.other.pkkits

import dev.openrune.ServerCacheManager
import dev.openrune.types.ItemServerType
import jakarta.inject.Singleton
import kotlin.math.max
import org.rsmod.api.config.refs.BaseParams

public enum class KitItemAccess {
    FREE,
    OWNED,
    FORBIDDEN,
}

/** Conservative PK-kit policy: only explicit, ordinary revision-240 gear/supplies are free. */
@Singleton
public open class KitItemPolicy {
    public open fun access(definition: KitDefinition, item: KitItem): KitItemAccess {
        val type = ServerCacheManager.getItem(item.id) ?: return KitItemAccess.FORBIDDEN
        if (isForbidden(type, item)) return KitItemAccess.FORBIDDEN
        return when (item.policyOverride) {
            KitItemPolicyOverride.FREE_SPAWN ->
                if (isFreeSpawnable(type, item)) KitItemAccess.FREE else KitItemAccess.FORBIDDEN
            KitItemPolicyOverride.REQUIRE_OWNERSHIP -> KitItemAccess.OWNED
            KitItemPolicyOverride.DISALLOWED -> KitItemAccess.FORBIDDEN
            KitItemPolicyOverride.INHERIT ->
                when (definition.loadPolicy) {
                    KitLoadPolicy.REQUIRE_OWNERSHIP -> KitItemAccess.OWNED
                    KitLoadPolicy.MIXED ->
                        if (isFreeSpawnable(type, item)) KitItemAccess.FREE else KitItemAccess.OWNED
                    KitLoadPolicy.FREE_SPAWN ->
                        if (isFreeSpawnable(type, item)) KitItemAccess.FREE
                        else KitItemAccess.FORBIDDEN
                }
        }
    }

    public fun isFreeSpawnable(type: ItemServerType, item: KitItem): Boolean {
        if (isForbidden(type, item)) return false
        if (item.vars != 0 || type.objvar.isNotEmpty()) return false
        return type.internalName in FreeSpawnInternalNames
    }

    public fun isForbidden(type: ItemServerType, item: KitItem): Boolean {
        if (item.count <= 0) return true
        if (type.isDummyItem || type.isPlaceholder || type.isCert || type.isTransformation)
            return true
        if (type.name.isBlank() || type.name.equals("null", ignoreCase = true)) return true
        if (type.param(BaseParams.no_bank) != 0) return true
        if (type.internalName in ForbiddenInternalNames) return true
        if (
            type.internalName.startsWith("obj.placeholder_") ||
                type.internalName.startsWith("obj.cert_")
        )
            return true
        return false
    }

    public fun riskValue(items: Iterable<KitItem>): Long =
        items.sumOf { item ->
            val type = ServerCacheManager.getItem(item.id)
            val unit = type?.let(::unitValue) ?: 0
            unit.toLong() * item.count.toLong()
        }

    private fun unitValue(type: ItemServerType): Int =
        max(
            max(type.playerCost, type.playerCostDerived),
            max(type.playerCostDerivedConst, type.cost),
        )

    public companion object {
        public val FreeSpawnInternalNames: Set<String> =
            setOf(
                "obj.rune_scimitar",
                "obj.magic_shortbow",
                "obj.xbows_crossbow_runite",
                "obj.dragon_scimitar",
                "obj.dragon_dagger",
                "obj.dragon_dagger_p++",
                "obj.granite_maul",
                "obj.black_dragonhide_body",
                "obj.black_dragonhide_chaps",
                "obj.black_dragon_vambraces",
                "obj.mystic_hat",
                "obj.mystic_robe_top",
                "obj.mystic_robe_bottom",
                "obj.mystic_gloves",
                "obj.mystic_boots",
                "obj.rune_platebody",
                "obj.rune_platelegs",
                "obj.rune_kiteshield",
                "obj.rune_armoured_boots",
                "obj.death_climbingboots",
                "obj.amulet_of_strength",
                "obj.amulet_of_glory",
                "obj.jewl_bracelet_of_combat",
                "obj.ring_of_recoil",
                "obj.snakeskin_boots",
                "obj.staff_of_zaros",
                "obj.shark",
                "obj.tbwt_cooked_karambwan",
                "obj.4dose2combat",
                "obj.4dose2attack",
                "obj.4dose2strength",
                "obj.4dose2defense",
                "obj.4doserangerspotion",
                "obj.4dose1magic",
                "obj.4dose2restore",
                "obj.4doseprayerrestore",
                "obj.rune_arrow",
                "obj.dragon_arrow",
                "obj.airrune",
                "obj.waterrune",
                "obj.earthrune",
                "obj.firerune",
                "obj.chaosrune",
                "obj.deathrune",
                "obj.bloodrune",
                "obj.lawrune",
                "obj.naturerune",
                "obj.soulrune",
            )

        private val ForbiddenInternalNames: Set<String> =
            setOf(
                "obj.bh_rune_pouch",
                "obj.rune_pouch",
                "obj.divine_rune_pouch",
                "obj.looting_bag",
                "obj.looting_bag_2",
            )
    }
}
