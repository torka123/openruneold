package org.rsmod.content.other.pkkits

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combat.commons.magic.Spellbook

@Singleton
public class StarterKitRegistry @Inject constructor(private val itemPolicy: KitItemPolicy) {
    private val logger = InlineLogger()
    private var definitions: Map<String, KitDefinition> = emptyMap()
    private var validation: Map<String, List<String>> = emptyMap()

    init {
        reload()
    }

    public fun reload(): KitRegistryReport {
        val built = StarterTemplates.map(::resolve)
        val duplicateIds = built.groupBy { it.id }.filterValues { it.size > 1 }.keys
        val results =
            built.associate { definition ->
                val errors = validate(definition).toMutableList()
                if (definition.id in duplicateIds) errors += "duplicate kit id '${definition.id}'"
                definition.id to errors.distinct()
            }
        definitions = built.associateBy { it.id }
        validation = results
        val report = report()
        if (!report.valid) {
            logger.error { "PK kit registry loaded with validation errors: ${report.invalid}" }
        } else {
            logger.info { "PK kit registry loaded ${report.loaded} validated starter kits." }
        }
        return report
    }

    public fun get(id: String): KitDefinition? =
        definitions[id.lowercase()]?.takeIf { validation[it.id].isNullOrEmpty() && it.enabled }

    public fun all(includeInvalid: Boolean = false): List<KitDefinition> =
        definitions.values
            .filter { includeInvalid || (validation[it.id].isNullOrEmpty() && it.enabled) }
            .sortedWith(compareBy(KitDefinition::category, KitDefinition::name))

    public fun validationErrors(id: String): List<String> = validation[id].orEmpty()

    public fun report(): KitRegistryReport =
        KitRegistryReport(
            loaded = definitions.size,
            validCount = validation.count { it.value.isEmpty() },
            invalid = validation.filterValues { it.isNotEmpty() },
        )

    public fun validate(definition: KitDefinition): List<String> = buildList {
        if (!IdPattern.matches(definition.id)) add("invalid id '${definition.id}'")
        if (definition.name.isBlank()) add("display name cannot be blank")
        addAll(definition.stats.validate())
        addAll(definition.settings.validate())
        validateItems(definition, definition.inventory, 28, equipment = false, this)
        validateItems(definition, definition.equipment, 14, equipment = true, this)
    }

    private fun validateItems(
        definition: KitDefinition,
        items: List<KitItem>,
        size: Int,
        equipment: Boolean,
        errors: MutableList<String>,
    ) {
        val duplicateSlots = items.groupBy { it.slot }.filterValues { it.size > 1 }.keys
        for (slot in duplicateSlots) errors +=
            "duplicate ${if (equipment) "equipment" else "inventory"} slot $slot"
        for (item in items) {
            if (item.slot !in 0 until size) {
                errors += "${item.internalName} has invalid slot ${item.slot}"
                continue
            }
            if (item.count <= 0) errors += "${item.internalName} has non-positive count"
            val type = ServerCacheManager.getItem(item.id)
            if (type == null) {
                errors += "missing cache item ${item.internalName} (${item.id})"
                continue
            }
            if (type.internalName != item.internalName) {
                errors +=
                    "item id ${item.id} resolves to ${type.internalName}, not ${item.internalName}"
            }
            if (equipment && type.wearpos1 != item.slot) {
                errors +=
                    "${item.internalName} belongs in wear slot ${type.wearpos1}, not ${item.slot}"
            }
            if (itemPolicy.access(definition, item) == KitItemAccess.FORBIDDEN) {
                errors += "${item.internalName} is forbidden by ${definition.loadPolicy} policy"
            }
        }
    }

    private fun resolve(template: Template): KitDefinition {
        val inventory = template.inventory.map(::resolve)
        val equipment = template.equipment.map(::resolve)
        val definition =
            KitDefinition(
                id = template.id,
                name = template.name,
                category = template.category,
                description = template.description,
                inventory = inventory,
                equipment = equipment,
                stats = template.stats,
                settings = template.settings,
                loadPolicy = template.policy,
                riskValue = 0,
            )
        return definition.copy(riskValue = itemPolicy.riskValue(inventory + equipment))
    }

    private fun resolve(item: RawItem): KitItem {
        val id = item.internalName.asRSCM(RSCMType.OBJ)
        return KitItem(item.internalName, id, item.count, item.vars, item.slot, item.override)
    }

    public data class KitRegistryReport(
        val loaded: Int,
        val validCount: Int,
        val invalid: Map<String, List<String>>,
    ) {
        val valid: Boolean
            get() = loaded == EXPECTED_STARTER_COUNT && invalid.isEmpty()
    }

    private data class RawItem(
        val internalName: String,
        val count: Int,
        val slot: Int,
        val vars: Int = 0,
        val override: KitItemPolicyOverride = KitItemPolicyOverride.INHERIT,
    )

    private data class Template(
        val id: String,
        val name: String,
        val category: KitCategory,
        val description: String,
        val inventory: List<RawItem>,
        val equipment: List<RawItem>,
        val stats: KitStats,
        val settings: KitCombatSettings,
        val policy: KitLoadPolicy = KitLoadPolicy.MIXED,
    )

    private companion object {
        const val EXPECTED_STARTER_COUNT = 17
        val IdPattern = Regex("[a-z0-9_]{2,40}")

        fun inv(name: String, slot: Int, count: Int = 1): RawItem = RawItem(name, count, slot)

        fun worn(name: String, slot: Int): RawItem = RawItem(name, 1, slot)

        fun supplies(vararg switches: RawItem): List<RawItem> {
            val items = switches.toMutableList()
            val used = items.mapTo(mutableSetOf()) { it.slot }
            val fixed =
                listOf(
                    inv("obj.4dose2combat", 4),
                    inv("obj.4doserangerspotion", 5),
                    inv("obj.4dose1magic", 6),
                    inv("obj.4dose2restore", 7),
                )
            fixed.filterTo(items) { it.slot !in used }
            used += fixed.map { it.slot }
            for (slot in 8 until 28) {
                if (slot in used) continue
                val food = if (slot % 3 == 0) "obj.tbwt_cooked_karambwan" else "obj.shark"
                items += inv(food, slot)
            }
            return items.sortedBy { it.slot }
        }

        val Pure = KitStats(60, 99, 1, 99, 99, 99, 52)
        val Zerker = KitStats(75, 99, 45, 99, 99, 99, 52)
        val Med = KitStats(75, 99, 70, 99, 99, 99, 77)
        val Main = KitStats(82, 99, 80, 99, 99, 99, 77)
        val Max = KitStats(99, 99, 99, 99, 99, 99, 99)
        val Tank = KitStats(75, 70, 99, 99, 99, 99, 77)

        fun settings(book: Spellbook = Spellbook.Standard): KitCombatSettings =
            KitCombatSettings(spellbook = book)

        val StarterTemplates =
            listOf(
                Template(
                    "pure_melee",
                    "Pure Melee",
                    KitCategory.Pure,
                    "60 Attack melee pure with dagger and maul switches.",
                    supplies(inv("obj.dragon_dagger_p++", 0), inv("obj.granite_maul", 1)),
                    listOf(
                        worn("obj.amulet_of_strength", 2),
                        worn("obj.rune_scimitar", 3),
                        worn("obj.ring_of_recoil", 12),
                    ),
                    Pure,
                    settings(),
                ),
                Template(
                    "pure_range",
                    "Pure Range",
                    KitCategory.Pure,
                    "One-defence ranged pure with shortbow supplies.",
                    supplies(inv("obj.dragon_dagger_p++", 0), inv("obj.rune_arrow", 2, 500)),
                    listOf(
                        worn("obj.amulet_of_glory", 2),
                        worn("obj.magic_shortbow", 3),
                        worn("obj.black_dragonhide_chaps", 7),
                        worn("obj.black_dragon_vambraces", 9),
                        worn("obj.rune_arrow", 13),
                    ),
                    Pure,
                    settings(),
                ),
                Template(
                    "pure_tribrid",
                    "Pure Tribrid",
                    KitCategory.Pure,
                    "One-defence melee, ranged, and Ancient Magicks switches.",
                    supplies(
                        inv("obj.dragon_dagger_p++", 0),
                        inv("obj.magic_shortbow", 1),
                        inv("obj.staff_of_zaros", 2),
                        inv("obj.rune_arrow", 3, 400),
                    ),
                    listOf(
                        worn("obj.amulet_of_glory", 2),
                        worn("obj.rune_scimitar", 3),
                        worn("obj.black_dragonhide_chaps", 7),
                        worn("obj.black_dragon_vambraces", 9),
                        worn("obj.rune_arrow", 13),
                    ),
                    Pure,
                    settings(Spellbook.Ancients),
                ),
                Template(
                    "zerker_vengeance",
                    "Zerker Vengeance",
                    KitCategory.Zerker,
                    "45 Defence Vengeance setup with AGS ownership switch.",
                    supplies(inv("obj.ags", 0), inv("obj.dragon_dagger_p++", 1)),
                    listOf(
                        worn("obj.viking_helmet_crush", 0),
                        worn("obj.tzhaar_cape_fire", 1),
                        worn("obj.amulet_of_glory", 2),
                        worn("obj.abyssal_whip", 3),
                        worn("obj.barbassault_penance_fighter_torso", 4),
                        worn("obj.rune_platelegs", 7),
                        worn("obj.hundred_gauntlets_level_10", 9),
                        worn("obj.rune_armoured_boots", 10),
                    ),
                    Zerker,
                    settings(Spellbook.Lunars),
                ),
                Template(
                    "zerker_tribrid",
                    "Zerker Tribrid",
                    KitCategory.Zerker,
                    "45 Defence Ancient Magicks tribrid.",
                    supplies(
                        inv("obj.magic_shortbow", 0),
                        inv("obj.staff_of_zaros", 1),
                        inv("obj.dragon_dagger_p++", 2),
                        inv("obj.rune_arrow", 3, 400),
                    ),
                    listOf(
                        worn("obj.mystic_hat", 0),
                        worn("obj.amulet_of_glory", 2),
                        worn("obj.dragon_scimitar", 3),
                        worn("obj.mystic_robe_top", 4),
                        worn("obj.mystic_robe_bottom", 7),
                        worn("obj.mystic_gloves", 9),
                        worn("obj.mystic_boots", 10),
                    ),
                    Zerker,
                    settings(Spellbook.Ancients),
                ),
                Template(
                    "med_vengeance",
                    "Med Vengeance",
                    KitCategory.Med,
                    "70 Defence Vengeance setup with AGS switch.",
                    supplies(inv("obj.ags", 0), inv("obj.dragon_claws", 1)),
                    listOf(
                        worn("obj.fris_kingly_helm", 0),
                        worn("obj.tzhaar_cape_fire", 1),
                        worn("obj.enchanted_onyx_amulet", 2),
                        worn("obj.abyssal_whip", 3),
                        worn("obj.barbassault_penance_fighter_torso", 4),
                        worn("obj.rune_platelegs", 7),
                        worn("obj.hundred_gauntlets_level_10", 9),
                        worn("obj.dragon_boots", 10),
                        worn("obj.rune_parryingdagger", 5),
                    ),
                    Med,
                    settings(Spellbook.Lunars),
                ),
                Template(
                    "med_tribrid",
                    "Med Tribrid",
                    KitCategory.Med,
                    "70 Defence Ancient Magicks tribrid.",
                    supplies(
                        inv("obj.staff_of_zaros", 0),
                        inv("obj.magic_shortbow", 1),
                        inv("obj.dragon_dagger_p++", 2),
                        inv("obj.rune_arrow", 3, 400),
                    ),
                    listOf(
                        worn("obj.mystic_hat", 0),
                        worn("obj.avas_assembler", 1),
                        worn("obj.occult_necklace", 2),
                        worn("obj.abyssal_whip", 3),
                        worn("obj.mystic_robe_top", 4),
                        worn("obj.mystic_robe_bottom", 7),
                        worn("obj.hundred_gauntlets_level_10", 9),
                        worn("obj.dragon_boots", 10),
                    ),
                    Med,
                    settings(Spellbook.Ancients),
                ),
                Template(
                    "main_vengeance",
                    "Main Vengeance",
                    KitCategory.Main,
                    "Main-account Vengeance setup with claws and AGS.",
                    supplies(inv("obj.ags", 0), inv("obj.dragon_claws", 1)),
                    listOf(
                        worn("obj.fris_kingly_helm", 0),
                        worn("obj.tzhaar_cape_fire", 1),
                        worn("obj.enchanted_onyx_amulet", 2),
                        worn("obj.abyssal_whip", 3),
                        worn("obj.rune_platebody", 4),
                        worn("obj.rune_platelegs", 7),
                        worn("obj.hundred_gauntlets_level_10", 9),
                        worn("obj.dragon_boots", 10),
                        worn("obj.rune_parryingdagger", 5),
                    ),
                    Main,
                    settings(Spellbook.Lunars),
                ),
                Template(
                    "main_tribrid",
                    "Main Tribrid",
                    KitCategory.Main,
                    "Main-account Ancient Magicks tribrid.",
                    supplies(
                        inv("obj.staff_of_zaros", 0),
                        inv("obj.magic_shortbow", 1),
                        inv("obj.dragon_claws", 2),
                        inv("obj.rune_arrow", 3, 400),
                    ),
                    listOf(
                        worn("obj.mystic_hat", 0),
                        worn("obj.avas_assembler", 1),
                        worn("obj.occult_necklace", 2),
                        worn("obj.abyssal_whip", 3),
                        worn("obj.mystic_robe_top", 4),
                        worn("obj.mystic_robe_bottom", 7),
                        worn("obj.hundred_gauntlets_level_10", 9),
                        worn("obj.dragon_boots", 10),
                    ),
                    Main,
                    settings(Spellbook.Ancients),
                ),
                Template(
                    "max_melee",
                    "Max Melee",
                    KitCategory.Max,
                    "Maxed melee setup with high-value owned switches.",
                    supplies(inv("obj.ags", 0), inv("obj.dragon_claws", 1)),
                    listOf(
                        worn("obj.fris_kingly_helm", 0),
                        worn("obj.tzhaar_cape_fire", 1),
                        worn("obj.enchanted_onyx_amulet", 2),
                        worn("obj.abyssal_whip", 3),
                        worn("obj.rune_platebody", 4),
                        worn("obj.rune_platelegs", 7),
                        worn("obj.hundred_gauntlets_level_10", 9),
                        worn("obj.dragon_boots", 10),
                        worn("obj.rune_parryingdagger", 5),
                    ),
                    Max,
                    settings(Spellbook.Lunars),
                ),
                Template(
                    "max_tribrid",
                    "Max Tribrid",
                    KitCategory.Max,
                    "Maxed Ancient Magicks tribrid.",
                    supplies(
                        inv("obj.staff_of_zaros", 0),
                        inv("obj.barrows_karil_weapon_100", 1),
                        inv("obj.dragon_claws", 2),
                        inv("obj.barrows_karil_ammo", 3, 500),
                    ),
                    listOf(
                        worn("obj.barrows_ahrim_head_100", 0),
                        worn("obj.avas_assembler", 1),
                        worn("obj.occult_necklace", 2),
                        worn("obj.abyssal_whip", 3),
                        worn("obj.barrows_ahrim_body_100", 4),
                        worn("obj.barrows_ahrim_legs_100", 7),
                        worn("obj.hundred_gauntlets_level_10", 9),
                        worn("obj.dragon_boots", 10),
                    ),
                    Max,
                    settings(Spellbook.Ancients),
                ),
                Template(
                    "dharok",
                    "Dharok",
                    KitCategory.Dharok,
                    "Full charged Dharok set with melee switches; ownership required.",
                    supplies(inv("obj.abyssal_whip", 0), inv("obj.dragon_dagger_p++", 1)),
                    listOf(
                        worn("obj.barrows_dharok_head_100", 0),
                        worn("obj.tzhaar_cape_fire", 1),
                        worn("obj.enchanted_onyx_amulet", 2),
                        worn("obj.barrows_dharok_weapon_100", 3),
                        worn("obj.barrows_dharok_body_100", 4),
                        worn("obj.barrows_dharok_legs_100", 7),
                        worn("obj.hundred_gauntlets_level_10", 9),
                        worn("obj.dragon_boots", 10),
                    ),
                    Max,
                    settings(),
                    KitLoadPolicy.REQUIRE_OWNERSHIP,
                ),
                Template(
                    "range_tank",
                    "Range Tank",
                    KitCategory.Tank,
                    "99 Defence Karil ranged tank; Barrows gear requires ownership.",
                    supplies(inv("obj.xbows_crossbow_runite", 0), inv("obj.dragon_dagger_p++", 1)),
                    listOf(
                        worn("obj.barrows_karil_head_100", 0),
                        worn("obj.avas_assembler", 1),
                        worn("obj.amulet_of_glory", 2),
                        worn("obj.barrows_karil_weapon_100", 3),
                        worn("obj.barrows_karil_body_100", 4),
                        worn("obj.barrows_karil_legs_100", 7),
                        worn("obj.hundred_gauntlets_level_10", 9),
                        worn("obj.dragon_boots", 10),
                        worn("obj.barrows_karil_ammo", 13),
                    ),
                    Tank,
                    settings(),
                ),
                Template(
                    "lms_pure",
                    "LMS Pure",
                    KitCategory.Lms,
                    "Free-spawn Last Man Standing pure practice kit.",
                    supplies(
                        inv("obj.dragon_dagger_p++", 0),
                        inv("obj.magic_shortbow", 1),
                        inv("obj.staff_of_zaros", 2),
                        inv("obj.rune_arrow", 3, 400),
                    ),
                    listOf(
                        worn("obj.amulet_of_glory", 2),
                        worn("obj.rune_scimitar", 3),
                        worn("obj.black_dragonhide_chaps", 7),
                        worn("obj.black_dragon_vambraces", 9),
                        worn("obj.rune_arrow", 13),
                    ),
                    Pure,
                    settings(Spellbook.Ancients),
                    KitLoadPolicy.FREE_SPAWN,
                ),
                Template(
                    "lms_zerker",
                    "LMS Zerker",
                    KitCategory.Lms,
                    "Free-spawn Last Man Standing zerker practice kit.",
                    supplies(
                        inv("obj.dragon_dagger_p++", 0),
                        inv("obj.magic_shortbow", 1),
                        inv("obj.staff_of_zaros", 2),
                        inv("obj.rune_arrow", 3, 400),
                    ),
                    listOf(
                        worn("obj.mystic_hat", 0),
                        worn("obj.amulet_of_glory", 2),
                        worn("obj.dragon_scimitar", 3),
                        worn("obj.mystic_robe_top", 4),
                        worn("obj.mystic_robe_bottom", 7),
                        worn("obj.mystic_gloves", 9),
                        worn("obj.mystic_boots", 10),
                    ),
                    Zerker,
                    settings(Spellbook.Ancients),
                    KitLoadPolicy.FREE_SPAWN,
                ),
                Template(
                    "lms_med",
                    "LMS Med",
                    KitCategory.Lms,
                    "Free-spawn Last Man Standing med practice kit.",
                    supplies(
                        inv("obj.dragon_dagger_p++", 0),
                        inv("obj.magic_shortbow", 1),
                        inv("obj.staff_of_zaros", 2),
                        inv("obj.rune_arrow", 3, 400),
                    ),
                    listOf(
                        worn("obj.mystic_hat", 0),
                        worn("obj.amulet_of_glory", 2),
                        worn("obj.dragon_scimitar", 3),
                        worn("obj.mystic_robe_top", 4),
                        worn("obj.mystic_robe_bottom", 7),
                        worn("obj.mystic_gloves", 9),
                        worn("obj.mystic_boots", 10),
                    ),
                    Med,
                    settings(Spellbook.Ancients),
                    KitLoadPolicy.FREE_SPAWN,
                ),
                Template(
                    "lms_main",
                    "LMS Main",
                    KitCategory.Lms,
                    "Free-spawn Last Man Standing main practice kit.",
                    supplies(
                        inv("obj.dragon_dagger_p++", 0),
                        inv("obj.magic_shortbow", 1),
                        inv("obj.staff_of_zaros", 2),
                        inv("obj.rune_arrow", 3, 400),
                    ),
                    listOf(
                        worn("obj.mystic_hat", 0),
                        worn("obj.amulet_of_glory", 2),
                        worn("obj.dragon_scimitar", 3),
                        worn("obj.mystic_robe_top", 4),
                        worn("obj.mystic_robe_bottom", 7),
                        worn("obj.mystic_gloves", 9),
                        worn("obj.mystic_boots", 10),
                    ),
                    Main,
                    settings(Spellbook.Ancients),
                    KitLoadPolicy.FREE_SPAWN,
                ),
            )
    }
}
