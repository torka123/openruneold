package org.rsmod.content.other.pkkits

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Singleton
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.game.entity.Player

@Singleton
public class KitPersistence {
    public fun read(player: Player): KitPersistenceRead {
        val raw = player.attr[DataKey] ?: return KitPersistenceRead(KitPlayerData())
        return decode(raw)
    }

    public fun write(player: Player, data: KitPlayerData) {
        player.attr[DataKey] = encode(data)
    }

    public fun saveCustom(player: Player, kit: CustomKit): KitMutationResult {
        val read = read(player)
        if (read.unsupportedVersion) {
            return KitMutationResult.Failure(
                "Your saved-kit data uses a newer unsupported version."
            )
        }
        val capacity = KitSlotManager.capacity(read.data.slotTier)
        if (kit.slot !in 0 until capacity) {
            return KitMutationResult.Failure(
                "Custom slot ${kit.slot + 1} is locked; your current capacity is $capacity."
            )
        }
        val cleanName =
            sanitizeName(kit.name)
                ?: return KitMutationResult.Failure(
                    "Kit names must contain 1-24 visible characters."
                )
        val kits = read.data.customKits.filterNot { it.slot == kit.slot }.toMutableList()
        kits += kit.copy(name = cleanName)
        write(player, read.data.copy(customKits = kits.sortedBy(CustomKit::slot)))
        return KitMutationResult.Success
    }

    public fun deleteCustom(player: Player, slot: Int): KitMutationResult {
        val read = read(player)
        if (read.unsupportedVersion)
            return KitMutationResult.Failure("Saved-kit data version is unsupported.")
        val capacity = KitSlotManager.capacity(read.data.slotTier)
        if (slot !in 0 until capacity) {
            return KitMutationResult.Failure(
                "Custom slot ${slot + 1} is locked; your current capacity is $capacity."
            )
        }
        val kit =
            read.data.customKits.firstOrNull { it.slot == slot }
                ?: return KitMutationResult.Failure("There is no custom kit in slot ${slot + 1}.")
        val updatedDefault =
            read.data.defaultKit?.takeUnless { it.source == KitSource.CUSTOM && it.id == kit.id }
        write(
            player,
            read.data.copy(
                customKits = read.data.customKits.filterNot { it.slot == slot },
                defaultKit = updatedDefault,
            ),
        )
        return KitMutationResult.Success
    }

    public fun renameCustom(player: Player, slot: Int, name: String): KitMutationResult {
        val clean =
            sanitizeName(name)
                ?: return KitMutationResult.Failure(
                    "Kit names must contain 1-24 visible characters."
                )
        val read = read(player)
        val capacity = KitSlotManager.capacity(read.data.slotTier)
        if (slot !in 0 until capacity) {
            return KitMutationResult.Failure(
                "Custom slot ${slot + 1} is locked; your current capacity is $capacity."
            )
        }
        val current =
            read.data.customKits.firstOrNull { it.slot == slot }
                ?: return KitMutationResult.Failure("There is no custom kit in slot ${slot + 1}.")
        val now = System.currentTimeMillis()
        val kits =
            read.data.customKits.map {
                if (it.slot == slot) current.copy(name = clean, updatedAt = now) else it
            }
        write(player, read.data.copy(customKits = kits))
        return KitMutationResult.Success
    }

    public fun setDefault(player: Player, reference: KitReference?): KitMutationResult {
        val read = read(player)
        if (reference?.source == KitSource.CUSTOM) {
            val custom =
                read.data.customKits.firstOrNull { it.id == reference.id }
                    ?: return KitMutationResult.Failure("That custom kit no longer exists.")
            val capacity = KitSlotManager.capacity(read.data.slotTier)
            if (custom.slot !in 0 until capacity) {
                return KitMutationResult.Failure(
                    "Custom slot ${custom.slot + 1} is locked; your current capacity is $capacity."
                )
            }
        }
        write(player, read.data.copy(defaultKit = reference))
        return KitMutationResult.Success
    }

    public fun setSlotTier(player: Player, tier: Int): KitMutationResult {
        if (tier !in KitSlotManager.validTiers) {
            return KitMutationResult.Failure("Slot tier must be ${KitSlotManager.validTiers}.")
        }
        val read = read(player)
        // A downgrade intentionally keeps higher slots in storage; they become locked, never
        // deleted.
        write(player, read.data.copy(slotTier = tier))
        return KitMutationResult.Success
    }

    public fun setOpenAfterDeath(player: Player, enabled: Boolean): KitMutationResult {
        val read = read(player)
        if (read.unsupportedVersion) {
            return KitMutationResult.Failure("Saved-kit data version is unsupported.")
        }
        write(player, read.data.copy(openAfterDeath = enabled))
        return KitMutationResult.Success
    }

    public fun decode(raw: Map<String, Any>): KitPersistenceRead {
        val version = raw.int("version") ?: 1
        if (version > KitPlayerData.CURRENT_VERSION) {
            return KitPersistenceRead(
                KitPlayerData(version = version),
                listOf(
                    "Saved-kit version $version is newer than supported version ${KitPlayerData.CURRENT_VERSION}."
                ),
                unsupportedVersion = true,
            )
        }
        val warnings = mutableListOf<String>()
        val slotTier = (raw.int("slotTier") ?: 0).coerceIn(0, 4)
        val defaultKit = KitReference.decode(raw.string("defaultKit"))
        val openAfterDeath = raw.bool("openAfterDeath") ?: false
        val kitMaps = raw.list("customKits").orEmpty().mapNotNull { it as? Map<*, *> }
        val custom =
            kitMaps
                .mapIndexedNotNull { index, entry ->
                    val normalized = entry.entries.associate { it.key.toString() to it.value }
                    decodeCustom(normalized, warnings, index)
                }
                .distinctBy(CustomKit::slot)
                .sortedBy(CustomKit::slot)
        return KitPersistenceRead(
            KitPlayerData(
                version = KitPlayerData.CURRENT_VERSION,
                slotTier = slotTier,
                defaultKit = defaultKit,
                customKits = custom,
                openAfterDeath = openAfterDeath,
            ),
            warnings,
        )
    }

    public fun encode(data: KitPlayerData): MutableMap<String, Any> =
        linkedMapOf(
            "version" to KitPlayerData.CURRENT_VERSION,
            "slotTier" to data.slotTier,
            "defaultKit" to (data.defaultKit?.encode() ?: ""),
            "openAfterDeath" to data.openAfterDeath,
            "customKits" to
                data.customKits.sortedBy(CustomKit::slot).map(::encodeCustom).toMutableList(),
        )

    private fun encodeCustom(kit: CustomKit): MutableMap<String, Any> =
        linkedMapOf(
            "slot" to kit.slot,
            "id" to kit.id,
            "name" to kit.name,
            "category" to kit.category.name,
            "loadPolicy" to kit.loadPolicy.name,
            "createdAt" to kit.createdAt,
            "updatedAt" to kit.updatedAt,
            "inventory" to kit.inventory.map(::encodeItem).toMutableList(),
            "equipment" to kit.equipment.map(::encodeItem).toMutableList(),
            "stats" to kit.stats.asMap().toMutableMap(),
            "settings" to encodeSettings(kit.settings),
        )

    private fun encodeItem(item: KitItem): MutableMap<String, Any> =
        linkedMapOf(
            "key" to item.internalName,
            "id" to item.id,
            "count" to item.count,
            "vars" to item.vars,
            "slot" to item.slot,
            "policy" to item.policyOverride.name,
        )

    private fun encodeSettings(settings: KitCombatSettings): MutableMap<String, Any> =
        linkedMapOf(
            "spellbook" to settings.spellbook.name,
            "prayerBook" to settings.prayerBook.name,
            "quickPrayers" to settings.quickPrayers,
            "combatStance" to settings.combatStance,
            "meleeStyle" to settings.meleeStyle,
            "autocastSpell" to settings.autocastSpell,
            "autocastDefensive" to settings.autocastDefensive,
            "specialEnergy" to settings.specialEnergy,
            "runEnergy" to settings.runEnergy,
            "runEnabled" to settings.runEnabled,
        )

    private fun decodeCustom(
        raw: Map<String, Any?>,
        warnings: MutableList<String>,
        index: Int,
    ): CustomKit? {
        val slot = raw.int("slot")
        val id = raw.string("id")
        val name = raw.string("name")
        if (
            slot == null ||
                slot !in 0 until KitSlotManager.MAX_CAPACITY ||
                id.isNullOrBlank() ||
                name.isNullOrBlank()
        ) {
            warnings += "Custom-kit entry $index has invalid identity fields and was ignored."
            return null
        }
        val inventory =
            decodeItems(raw.list("inventory"), 28, warnings, "$name inventory") ?: return null
        val equipment =
            decodeItems(raw.list("equipment"), 14, warnings, "$name equipment") ?: return null
        val stats = decodeStats(raw.map("stats"))
        val settings = decodeSettings(raw.map("settings"))
        if (stats == null || settings == null) {
            warnings += "Custom kit '$name' has invalid stats or settings and was ignored."
            return null
        }
        val category = raw.enum<KitCategory>("category") ?: KitCategory.Custom
        val loadPolicy = raw.enum<KitLoadPolicy>("loadPolicy") ?: KitLoadPolicy.MIXED
        return CustomKit(
            slot = slot,
            id = id,
            name = name,
            category = category,
            inventory = inventory,
            equipment = equipment,
            stats = stats,
            settings = settings,
            loadPolicy = loadPolicy,
            createdAt = raw.long("createdAt") ?: System.currentTimeMillis(),
            updatedAt = raw.long("updatedAt") ?: System.currentTimeMillis(),
        )
    }

    private fun decodeItems(
        raw: List<Any?>?,
        size: Int,
        warnings: MutableList<String>,
        context: String,
    ): List<KitItem>? {
        val items = mutableListOf<KitItem>()
        for ((index, value) in raw.orEmpty().withIndex()) {
            val map = (value as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }
            val key = map?.string("key")
            val count = map?.int("count")
            val vars = map?.int("vars") ?: 0
            val slot = map?.int("slot")
            if (
                key.isNullOrBlank() ||
                    count == null ||
                    count <= 0 ||
                    slot == null ||
                    slot !in 0 until size
            ) {
                warnings += "$context item $index is malformed; the kit was ignored."
                return null
            }
            val id = key.asRSCM(RSCMType.OBJ)
            val type = ServerCacheManager.getItem(id)
            if (type == null || type.internalName != key) {
                warnings += "$context references missing cache item '$key'; the kit was ignored."
                return null
            }
            val override =
                map.enum<KitItemPolicyOverride>("policy") ?: KitItemPolicyOverride.INHERIT
            items += KitItem(key, id, count, vars, slot, override)
        }
        if (items.map { it.slot }.distinct().size != items.size) {
            warnings += "$context contains duplicate slots; the kit was ignored."
            return null
        }
        return items
    }

    private fun decodeStats(raw: Map<String, Any?>?): KitStats? {
        raw ?: return null
        return KitStats(
                attack = raw.int("stat.attack") ?: return null,
                strength = raw.int("stat.strength") ?: return null,
                defence = raw.int("stat.defence") ?: return null,
                hitpoints = raw.int("stat.hitpoints") ?: return null,
                ranged = raw.int("stat.ranged") ?: return null,
                magic = raw.int("stat.magic") ?: return null,
                prayer = raw.int("stat.prayer") ?: return null,
            )
            .takeIf { it.validate().isEmpty() }
    }

    private fun decodeSettings(raw: Map<String, Any?>?): KitCombatSettings? {
        raw ?: return null
        val settings =
            KitCombatSettings(
                spellbook = raw.enum<Spellbook>("spellbook") ?: Spellbook.Standard,
                prayerBook = raw.enum<KitPrayerBook>("prayerBook") ?: KitPrayerBook.STANDARD,
                quickPrayers = raw.int("quickPrayers") ?: 0,
                combatStance = raw.int("combatStance") ?: 0,
                meleeStyle = raw.int("meleeStyle") ?: 0,
                autocastSpell = raw.int("autocastSpell") ?: 0,
                autocastDefensive = raw.bool("autocastDefensive") ?: false,
                specialEnergy = raw.int("specialEnergy") ?: 1000,
                runEnergy = raw.int("runEnergy") ?: 1000,
                runEnabled = raw.bool("runEnabled") ?: true,
            )
        return settings.takeIf { it.validate().isEmpty() }
    }

    private fun sanitizeName(value: String): String? {
        val clean = value.replace(Regex("[<>]"), "").trim().replace(Regex("\\s+"), " ")
        return clean.takeIf { it.length in 1..24 }
    }

    private companion object {
        val DataKey = AttributeKey<MutableMap<String, Any>>(persistenceKey = "pk_kit_data_v1")
    }
}

public data class KitPersistenceRead(
    val data: KitPlayerData,
    val warnings: List<String> = emptyList(),
    val unsupportedVersion: Boolean = false,
)

public sealed interface KitMutationResult {
    public data object Success : KitMutationResult

    public data class Failure(val reason: String) : KitMutationResult
}

public object KitSlotManager {
    public const val MAX_CAPACITY: Int = 20
    public val validTiers: IntRange = 0..4
    private val capacities = intArrayOf(5, 7, 10, 15, 20)

    public fun capacity(tier: Int): Int = capacities[tier.coerceIn(validTiers)]

    public fun isUnlocked(tier: Int, slot: Int): Boolean = slot in 0 until capacity(tier)
}

private fun Map<*, *>.string(key: String): String? = this[key] as? String

private fun Map<*, *>.int(key: String): Int? = (this[key] as? Number)?.toInt()

private fun Map<*, *>.long(key: String): Long? = (this[key] as? Number)?.toLong()

private fun Map<*, *>.bool(key: String): Boolean? = this[key] as? Boolean

@Suppress("UNCHECKED_CAST")
private fun Map<*, *>.list(key: String): List<Any?>? = this[key] as? List<Any?>

private fun Map<*, *>.map(key: String): Map<String, Any?>? =
    (this[key] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }

private inline fun <reified T : Enum<T>> Map<*, *>.enum(key: String): T? =
    string(key)?.let { value -> enumValues<T>().firstOrNull { it.name.equals(value, true) } }
