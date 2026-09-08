package dev.openrune.audit

import com.google.gson.GsonBuilder
import dev.openrune.OsrsCacheProvider
import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.ItemType
import dev.openrune.filesystem.Cache
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.util.Wearpos
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Cache-first armour audit used by the offline report generator and administrator commands.
 *
 * The LIVE cache is the expected revision-specific source for client definitions. The SERVER cache
 * is the value the game actually consumes after raw-cache overlays have been packed. Keeping both
 * sides in the same row makes accidental overlay regressions visible without collapsing raw IDs.
 */
public object ArmourAudit {
    public const val SCHEMA_VERSION: Int = 1

    private val targetSlots =
        linkedMapOf(
            Wearpos.Hat.slot to "Head",
            Wearpos.Back.slot to "Cape",
            Wearpos.Front.slot to "Neck",
            Wearpos.Quiver.slot to "Ammunition",
            Wearpos.Torso.slot to "Body",
            Wearpos.LeftHand.slot to "Shield",
            Wearpos.Legs.slot to "Legs",
            Wearpos.Hands.slot to "Hands",
            Wearpos.Feet.slot to "Feet",
            Wearpos.Ring.slot to "Ring",
        )

    private val directStatParams =
        listOf(
            "attack_stab",
            "attack_slash",
            "attack_crush",
            "attack_magic",
            "attack_ranged",
            "defence_stab",
            "defence_slash",
            "defence_crush",
            "defence_magic",
            "defence_ranged",
            "melee_strength",
            "ranged_strength",
            "magic_damage",
            "item_prayer_bonus",
        )

    private val effectDefinitions =
        listOf(
            ArmourEffectDefinition(
                "barrows",
                true,
                listOf("barrows_"),
                "REGISTERED",
                "Six 1/4 set effects; repaired and 100/75/50/25 states",
                "https://oldschool.runescape.wiki/w/Barrows_equipment",
            ),
            ArmourEffectDefinition(
                "barrows_damned",
                true,
                listOf("damned_", "damned "),
                "REGISTERED",
                "Per-set Amulet of the damned enhancements and degraded variant",
                "https://oldschool.runescape.wiki/w/Amulet_of_the_damned",
            ),
            ArmourEffectDefinition(
                "moons_of_peril",
                true,
                listOf("blood_moon", "frost_moon", "blue_moon", "eclipse_moon"),
                "PARTIAL",
                "Bloodrager, Eclipse burn, and Frostweaver combat paths registered; automatic combat-style switching and armour degradation remain unresolved",
                "https://oldschool.runescape.wiki/w/Armour_set_effects#Moons_of_Peril",
            ),
            ArmourEffectDefinition(
                "void_knight",
                true,
                listOf("game_pest_", "void_knight", "void knight"),
                "REGISTERED",
                "Style-helm accuracy/damage modifiers; elite ranged/magic bonuses",
                "https://oldschool.runescape.wiki/w/Void_Knight_equipment",
            ),
            ArmourEffectDefinition(
                "crystal_armour",
                true,
                listOf(
                    "crystal_helmet",
                    "crystal_chestplate",
                    "crystal_platelegs",
                    "crystal helm",
                    "crystal body",
                    "crystal legs",
                ),
                "REGISTERED",
                "Per-piece compatible crystal-bow accuracy and damage modifiers",
                "https://oldschool.runescape.wiki/w/Crystal_armour",
            ),
            ArmourEffectDefinition(
                "slayer_headgear",
                false,
                listOf("slayer_helm", "black_mask", "slayer helmet", "black mask"),
                "REGISTERED",
                "Task-only melee and imbued ranged/magic combat modifiers",
                "https://oldschool.runescape.wiki/w/Slayer_helmet",
            ),
            ArmourEffectDefinition(
                "justiciar",
                true,
                listOf("justiciar_", "justiciar "),
                "REGISTERED",
                "PvM non-typeless defence-based damage reduction",
                "https://oldschool.runescape.wiki/w/Justiciar_armour",
            ),
            ArmourEffectDefinition(
                "inquisitor",
                true,
                listOf("inquisitor", "inquisitors_"),
                "REGISTERED",
                "Per-piece and full-set crush accuracy/damage modifiers",
                "https://oldschool.runescape.wiki/w/Inquisitor%27s_armour",
            ),
            ArmourEffectDefinition(
                "virtus",
                true,
                listOf("virtus_", "virtus "),
                "REGISTERED",
                "Per-piece Ancient Magicks magic-damage modifier",
                "https://oldschool.runescape.wiki/w/Virtus_robes",
            ),
            ArmourEffectDefinition(
                "bark_armour",
                true,
                listOf("bloodbark", "swampbark"),
                "MISSING",
                "Swampbark bind-duration and Bloodbark healing passives need a verified spell hook",
                "https://oldschool.runescape.wiki/w/Bark_armour",
            ),
            ArmourEffectDefinition(
                "serpentine_helm",
                false,
                listOf("serpentine_helm", "serpentine helm", "tanzanite helm", "magma helm"),
                "PARTIAL",
                "Charged immunity/venom weapon hooks present; full scale lifecycle needs review",
                "https://oldschool.runescape.wiki/w/Serpentine_helm",
            ),
            ArmourEffectDefinition(
                "dragonfire_shields",
                false,
                listOf(
                    "dragonfire_shield",
                    "dragonfire_ward",
                    "dragonfire shield",
                    "dragonfire ward",
                    "ancient_wyvern_shield",
                    "ancient wyvern shield",
                ),
                "PARTIAL",
                "Protection tiers present; charge/discharge lifecycle needs review",
                "https://oldschool.runescape.wiki/w/Dragonfire",
            ),
            ArmourEffectDefinition(
                "spirit_shields",
                false,
                listOf(
                    "spirit_shield",
                    "spirit shield",
                    "elysian_",
                    "elysian ",
                    "spectral_",
                    "spectral ",
                    "arcane_",
                    "arcane ",
                ),
                "PARTIAL",
                "Elysian reduction registered; remaining drain exclusions need review",
                "https://oldschool.runescape.wiki/w/Spirit_shield",
            ),
            ArmourEffectDefinition(
                "ava_devices",
                false,
                listOf("avas_", "ava's ", "assembler", "accumulator", "attractor"),
                "REGISTERED",
                "Cache recovery-rate params and metallic-interference rule",
                "https://oldschool.runescape.wiki/w/Ava%27s_device",
            ),
            ArmourEffectDefinition(
                "dizanas_quiver",
                false,
                listOf("dizanas_quiver", "dizana's quiver"),
                "MISSING",
                "Extra ammo slot, charged bonus and charge consumption need client-supported storage",
                "https://oldschool.runescape.wiki/w/Dizana%27s_quiver",
            ),
            ArmourEffectDefinition(
                "charged_jewellery",
                false,
                listOf(
                    "ring_of_recoil",
                    "ring of recoil",
                    "ring_of_suffering",
                    "ring of suffering",
                    "lightbearer",
                    "amulet_of_the_damned",
                    "amulet of the damned",
                    "amulet_of_blood_fury",
                    "amulet of blood fury",
                ),
                "PARTIAL",
                "Formula hooks exist for several families; charge/death lifecycles need per-family review",
                "https://oldschool.runescape.wiki/w/Passive_effect",
            ),
            ArmourEffectDefinition(
                "utility_armour",
                false,
                listOf(
                    "graceful",
                    "prospector",
                    "rogue_",
                    "rogue ",
                    "karils_coif",
                    "brimstone_ring",
                    "ring_of_wealth",
                    "ring of wealth",
                ),
                "PARTIAL",
                "Mixed skill, combat and location passives",
                "https://oldschool.runescape.wiki/w/Passive_effect",
            ),
        )

    public fun scan(projectRoot: Path, revision: Int): ArmourAuditSnapshot {
        val liveCachePath = projectRoot.resolve(Path.of(".data", "cache", "LIVE")).normalize()
        val serverCachePath = projectRoot.resolve(Path.of(".data", "cache", "SERVER")).normalize()
        val overlayPath = projectRoot.resolve(Path.of(".data", "raw-cache", "server", "items.toml"))
        val clientItems = loadClientItems(liveCachePath, revision)
        val serverItems = ServerCacheManager.getItems()
        val overlayIds = readOverlayIds(overlayPath)
        val statParamIds = directStatParams.associateWith(::paramId)
        val additiveRangedStrengthParam = paramId("additive_ranged_strength")
        val requirementParamIds =
            mapOf(
                "statreq1_skill" to paramId("statreq1_skill"),
                "statreq1_level" to paramId("statreq1_level"),
                "statreq2_skill" to paramId("statreq2_skill"),
                "statreq2_level" to paramId("statreq2_level"),
            )
        val chargedVariantParam = paramIdOrNull("charged_variant")
        val unchargedVariantParam = paramIdOrNull("uncharged_variant")

        val rows =
            serverItems.values
                .asSequence()
                .filter { it.isEquipable && it.wearpos1 in targetSlots }
                .filterNot { it.isCert || it.isPlaceholder }
                .mapNotNull { server ->
                    val client = clientItems[server.id] ?: return@mapNotNull null
                    val effects = effectsFor(server)
                    val currentStats =
                        directStatParams.associateWith { name ->
                            if (name == "ranged_strength") {
                                server.intParam(statParamIds.getValue(name)) +
                                    server.intParam(additiveRangedStrengthParam)
                            } else {
                                server.intParam(statParamIds.getValue(name))
                            }
                        }
                    val expectedStats =
                        directStatParams.associateWith { name ->
                            if (name == "ranged_strength") {
                                client.intParam(statParamIds.getValue(name)) +
                                    client.intParam(additiveRangedStrengthParam)
                            } else {
                                client.intParam(statParamIds.getValue(name))
                            }
                        }
                    if (
                        currentStats.values.none { it != 0 } &&
                            expectedStats.values.none { it != 0 } &&
                            effects.isEmpty()
                    ) {
                        return@mapNotNull null
                    }

                    buildRow(
                        server = server,
                        client = client,
                        currentStats = currentStats,
                        expectedStats = expectedStats,
                        effects = effects,
                        requirementParamIds = requirementParamIds,
                        overlayOwned = server.id in overlayIds,
                        chargedVariantParam = chargedVariantParam,
                        unchargedVariantParam = unchargedVariantParam,
                    )
                }
                .sortedWith(
                    compareBy<ArmourAuditRow>(
                        { targetSlots.keys.indexOf(it.slotId) },
                        ArmourAuditRow::id,
                    )
                )
                .toList()

        val mismatchRows = rows.count { it.auditStatus == "MISMATCH" }
        val manualModelRows = rows.count { it.model.status == "MANUAL_REVIEW" }
        val bySlot = rows.groupingBy(ArmourAuditRow::slot).eachCount().toSortedMap()
        val effectSummary =
            rows
                .flatMap(ArmourAuditRow::effects)
                .groupingBy(ArmourEffectAudit::family)
                .eachCount()
                .toSortedMap()
        return ArmourAuditSnapshot(
            schemaVersion = SCHEMA_VERSION,
            generatedAt = Instant.now().toString(),
            revision = revision,
            liveCachePath = liveCachePath.toAbsolutePath().toString(),
            serverCachePath = serverCachePath.toAbsolutePath().toString(),
            rawOverlayPath = overlayPath.toAbsolutePath().toString(),
            scopeRule =
                "Raw unnoted equipable IDs in Head/Cape/Neck/Ammunition/Body/Shield/Legs/Hands/Feet/Ring with any non-zero direct stat or a verified effect-family candidate.",
            sourcePriority =
                listOf(
                    "active revision-$revision LIVE client cache",
                    "active revision-$revision SERVER cache",
                    "raw server overlay",
                    "OSRS Wiki/Jagex effect verification",
                ),
            summary =
                ArmourAuditSummary(
                    clientDefinitions = clientItems.size,
                    serverDefinitions = serverItems.size,
                    scopedRawIds = rows.size,
                    normalizedFamilies = rows.map(ArmourAuditRow::sourceFamily).distinct().size,
                    matchingRows = rows.size - mismatchRows,
                    mismatchRows = mismatchRows,
                    manualModelReviewRows = manualModelRows,
                    bySlot = bySlot,
                    effectCandidates = effectSummary,
                ),
            items = rows,
        )
    }

    private fun buildRow(
        server: ItemServerType,
        client: ItemType,
        currentStats: Map<String, Int>,
        expectedStats: Map<String, Int>,
        effects: List<ArmourEffectAudit>,
        requirementParamIds: Map<String, Int>,
        overlayOwned: Boolean,
        chargedVariantParam: Int?,
        unchargedVariantParam: Int?,
    ): ArmourAuditRow {
        val internalName = internalName(server.id)
        val slot = targetSlots.getValue(server.wearpos1)
        val currentRequirements = requirements(server.paramsRaw, requirementParamIds)
        val expectedRequirements = requirements(client.params, requirementParamIds)
        val comparisons = linkedMapOf<String, ArmourFieldComparison>()
        comparisons["name"] = comparison(server.name, client.name, overlayOwned)
        comparisons["slot"] = comparison(server.wearpos1, client.equipSlot, overlayOwned)
        comparisons["weight"] = comparison(server.weight, client.weight, overlayOwned)
        comparisons["inventory_options"] =
            comparison(
                server.interfaceOptions.joinToString("|"),
                client.interfaceOptions.joinToString("|"),
                overlayOwned,
            )
        for (stat in directStatParams) {
            comparisons["stat.$stat"] =
                comparison(currentStats.getValue(stat), expectedStats.getValue(stat), overlayOwned)
        }
        comparisons["requirements"] =
            comparison(
                currentRequirements.joinToString("|"),
                expectedRequirements.joinToString("|"),
                overlayOwned,
            )

        val model = modelAudit(client, slot)
        val mismatchFields = comparisons.filterValues { !it.matches }.keys.toList()
        val status = if (mismatchFields.isEmpty()) "MATCH" else "MISMATCH"
        val chargeState =
            chargeState(server, internalName, chargedVariantParam, unchargedVariantParam)
        return ArmourAuditRow(
            id = server.id,
            internalName = internalName,
            name = server.name,
            sourceFamily = sourceFamily(server.name, internalName),
            members = client.members,
            tradeable = server.tradeable,
            slot = slot,
            slotId = server.wearpos1,
            requirements = currentRequirements,
            expectedRequirements = expectedRequirements,
            stats = currentStats,
            expectedStats = expectedStats,
            weight = server.weight,
            inventoryOptions = server.interfaceOptions.toList(),
            model = model,
            chargeState = chargeState.first,
            degradeState = chargeState.second,
            effects = effects,
            source = "active revision cache",
            owningSourceFile =
                if (overlayOwned) ".data/raw-cache/server/items.toml"
                else ".data/cache/LIVE -> ItemServerCodec",
            auditStatus =
                if (status == "MISMATCH") status
                else if (model.status == "MANUAL_REVIEW") "NEEDS_MANUAL_EVIDENCE" else "CORRECT",
            mismatchFields = mismatchFields,
            comparisons = comparisons,
        )
    }

    private fun loadClientItems(path: Path, revision: Int): Map<Int, ItemType> {
        val cache = Cache.load(path)
        return try {
            mutableMapOf<Int, ItemType>().also {
                OsrsCacheProvider.ItemDecoder(revision).load(cache, it)
            }
        } finally {
            cache.close()
        }
    }

    private fun readOverlayIds(path: Path): Set<Int> {
        if (!Files.isRegularFile(path)) return emptySet()
        val idPattern = Regex("(?m)^id\\s*=\\s*\"([^\"]+)\"")
        return idPattern
            .findAll(Files.readString(path))
            .mapNotNull { match ->
                val value = match.groupValues[1]
                value.toIntOrNull()
                    ?: runCatching { value.asRSCM(RSCMType.OBJ) }.getOrNull()?.takeIf { it >= 0 }
            }
            .toSet()
    }

    private fun modelAudit(item: ItemType, slot: String): ArmourModelAudit {
        val visible = slot != "Ring" && slot != "Ammunition"
        val male = listOf(item.maleModel0, item.maleModel1, item.maleModel2)
        val female = listOf(item.femaleModel0, item.femaleModel1, item.femaleModel2)
        val status =
            when {
                item.inventoryModel <= 0 -> "MANUAL_REVIEW"
                !visible -> "NOT_VISIBLE"
                male.all { it < 0 } || female.all { it < 0 } -> "MANUAL_REVIEW"
                else -> "CACHE_DEFINED"
            }
        return ArmourModelAudit(
            inventoryModel = item.inventoryModel,
            inventoryZoom = item.zoom2d,
            inventoryRotations = listOf(item.xan2d, item.yan2d, item.zan2d),
            inventoryOffsets = listOf(item.xOffset2d, item.yOffset2d),
            resize = listOf(item.resizeX, item.resizeY, item.resizeZ),
            maleWornModels = male,
            femaleWornModels = female,
            maleOffset = item.maleOffset,
            femaleOffset = item.femaleOffset,
            maleHeadModels = listOf(item.maleHeadModel0, item.maleHeadModel1),
            femaleHeadModels = listOf(item.femaleHeadModel0, item.femaleHeadModel1),
            appearanceOverrideSlots = listOf(item.appearanceOverride1, item.appearanceOverride2),
            hiddenBodyParts =
                listOf(item.appearanceOverride1, item.appearanceOverride2)
                    .filter { it >= 0 }
                    .map { Wearpos[it]?.toString() ?: "wearpos.$it" },
            teamId = item.teamCape,
            recolourFrom = item.originalColours?.toList().orEmpty(),
            recolourTo = item.modifiedColours?.toList().orEmpty(),
            retextureFrom = item.originalTextureColours?.toList().orEmpty(),
            retextureTo = item.modifiedTextureColours?.toList().orEmpty(),
            status = status,
        )
    }

    private fun requirements(
        params: Map<Int, Any>?,
        ids: Map<String, Int>,
    ): List<ArmourRequirementAudit> =
        (1..2).mapNotNull { index ->
            val skill = params.intParam(ids.getValue("statreq${index}_skill"))
            val level = params.intParam(ids.getValue("statreq${index}_level"))
            if (skill == null && level == null) return@mapNotNull null
            ArmourRequirementAudit(
                index = index,
                skillId = skill,
                skill = skill?.let(::statName),
                level = level ?: 0,
            )
        }

    private fun effectsFor(item: ItemServerType): List<ArmourEffectAudit> {
        val haystack = "${item.internalName} ${item.name}".lowercase()
        return effectDefinitions
            .filter { definition -> definition.tokens.any(haystack::contains) }
            .map { definition ->
                ArmourEffectAudit(
                    family = definition.family,
                    kind = if (definition.setEffect) "SET" else "PASSIVE",
                    registrationStatus = definition.status,
                    expectedBehavior = definition.expectedBehavior,
                    source = definition.source,
                )
            }
    }

    private fun comparison(
        current: Any?,
        expected: Any?,
        overlayOwned: Boolean,
    ): ArmourFieldComparison =
        ArmourFieldComparison(
            current = current?.toString(),
            expected = expected?.toString(),
            matches = current == expected,
            source = "revision LIVE cache",
            owningSourceFile =
                if (overlayOwned) ".data/raw-cache/server/items.toml"
                else "or-cache/ItemServerCodec.kt",
            corrected = false,
        )

    private fun chargeState(
        item: ItemServerType,
        internalName: String,
        chargedVariantParam: Int?,
        unchargedVariantParam: Int?,
    ): Pair<String, String> {
        val haystack = "$internalName ${item.name}".lowercase()
        val hasChargeLink =
            chargedVariantParam?.let { item.paramsRaw.intParam(it) != null } == true ||
                unchargedVariantParam?.let { item.paramsRaw.intParam(it) != null } == true
        val charge =
            when {
                hasChargeLink -> "CACHE_LINKED"
                "uncharged" in haystack -> "UNCHARGED_VARIANT"
                "charged" in haystack -> "CHARGED_VARIANT"
                else -> "NOT_CHARGED"
            }
        val degrade =
            when {
                Regex("_(100|75|50|25)$").containsMatchIn(internalName) -> "BARROWS_STAGE"
                Regex("_(0|broken)$").containsMatchIn(internalName) || "broken" in haystack ->
                    "BROKEN_STAGE"
                "degraded" in haystack || "inactive" in haystack -> "DEGRADED_VARIANT"
                else -> "NOT_DEGRADABLE"
            }
        return charge to degrade
    }

    private fun sourceFamily(name: String, internal: String): String {
        val normalized =
            name
                .lowercase()
                .replace(
                    Regex("\\s*\\((?:charged|uncharged|broken|inactive|full|[0-9]+)\\)\\s*$"),
                    "",
                )
                .replace(Regex("\\s+[0-9]+$"), "")
                .trim()
        return normalized.ifEmpty {
            internal.removePrefix("obj.").replace(Regex("_(?:100|75|50|25|0)$"), "")
        }
    }

    private fun internalName(id: Int): String =
        runCatching { RSCM.getReverseMapping(RSCMType.OBJ, id) }.getOrDefault("obj.$id")

    private fun statName(id: Int): String =
        runCatching { RSCM.getReverseMapping(RSCMType.STAT, id) }.getOrDefault("stat.$id")

    private fun paramId(name: String): Int = "param.$name".asRSCM(RSCMType.PARAM)

    private fun paramIdOrNull(name: String): Int? =
        runCatching { paramId(name) }.getOrNull()?.takeIf { it >= 0 }

    private fun ItemServerType.intParam(id: Int): Int = paramsRaw.intParam(id) ?: 0

    private fun ItemType.intParam(id: Int): Int = params.intParam(id) ?: 0

    private fun Map<Int, Any>?.intParam(id: Int): Int? = (this?.get(id) as? Number)?.toInt()

    private data class ArmourEffectDefinition(
        val family: String,
        val setEffect: Boolean,
        val tokens: List<String>,
        val status: String,
        val expectedBehavior: String,
        val source: String,
    )
}

public object ArmourAuditWriter {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    public fun write(snapshot: ArmourAuditSnapshot, output: Path): ArmourAuditPaths {
        Files.createDirectories(output)
        val json = output.resolve("armour-audit.json")
        val csv = output.resolve("armour-audit.csv")
        val effects = output.resolve("armour-effect-audit.json")
        val models = output.resolve("armour-model-audit.json")
        Files.writeString(json, gson.toJson(snapshot) + System.lineSeparator())
        Files.writeString(csv, snapshot.toCsv())
        Files.writeString(
            effects,
            gson.toJson(
                ArmourEffectAuditReport(
                    generatedAt = snapshot.generatedAt,
                    revision = snapshot.revision,
                    items =
                        snapshot.items
                            .filter { it.effects.isNotEmpty() }
                            .map {
                                ArmourEffectAuditItem(
                                    it.id,
                                    it.internalName,
                                    it.name,
                                    it.slot,
                                    it.effects,
                                )
                            },
                )
            ) + System.lineSeparator(),
        )
        Files.writeString(
            models,
            gson.toJson(
                ArmourModelAuditReport(
                    generatedAt = snapshot.generatedAt,
                    revision = snapshot.revision,
                    manualReviewCount = snapshot.items.count { it.model.status == "MANUAL_REVIEW" },
                    items =
                        snapshot.items.map {
                            ArmourModelAuditItem(it.id, it.internalName, it.name, it.slot, it.model)
                        },
                )
            ) + System.lineSeparator(),
        )
        return ArmourAuditPaths(json, csv, effects, models)
    }

    private fun ArmourAuditSnapshot.toCsv(): String {
        val header = buildList {
            addAll(listOf("id", "internal_name", "name", "source_family", "slot", "slot_id"))
            for (stat in items.firstOrNull()?.stats?.keys.orEmpty()) {
                add("current_$stat")
                add("expected_$stat")
                add("match_$stat")
            }
            addAll(
                listOf(
                    "current_requirements",
                    "expected_requirements",
                    "members",
                    "tradeable",
                    "weight",
                    "inventory_model",
                    "inventory_zoom",
                    "inventory_rotations",
                    "inventory_offsets",
                    "male_worn_models",
                    "female_worn_models",
                    "male_head_models",
                    "female_head_models",
                    "charge_state",
                    "degrade_state",
                    "effects",
                    "model_status",
                    "audit_status",
                    "mismatch_fields",
                    "source",
                    "owning_source_file",
                )
            )
        }
        return buildString {
            appendLine(header.joinToString(",", transform = ::csv))
            for (item in items) {
                val values = buildList {
                    addAll(
                        listOf(
                            item.id,
                            item.internalName,
                            item.name,
                            item.sourceFamily,
                            item.slot,
                            item.slotId,
                        )
                    )
                    for ((stat, current) in item.stats) {
                        val expected = item.expectedStats.getValue(stat)
                        add(current)
                        add(expected)
                        add(current == expected)
                    }
                    add(item.requirements.joinToString(" | "))
                    add(item.expectedRequirements.joinToString(" | "))
                    add(item.members)
                    add(item.tradeable)
                    add(item.weight)
                    add(item.model.inventoryModel)
                    add(item.model.inventoryZoom)
                    add(item.model.inventoryRotations.joinToString("|"))
                    add(item.model.inventoryOffsets.joinToString("|"))
                    add(item.model.maleWornModels.joinToString("|"))
                    add(item.model.femaleWornModels.joinToString("|"))
                    add(item.model.maleHeadModels.joinToString("|"))
                    add(item.model.femaleHeadModels.joinToString("|"))
                    add(item.chargeState)
                    add(item.degradeState)
                    add(
                        item.effects.joinToString("|") { effect ->
                            "${effect.family}:${effect.registrationStatus}"
                        }
                    )
                    add(item.model.status)
                    add(item.auditStatus)
                    add(item.mismatchFields.joinToString("|"))
                    add(item.source)
                    add(item.owningSourceFile)
                }
                appendLine(values.joinToString(",", transform = ::csv))
            }
        }
    }

    private fun csv(value: Any?): String {
        val text = value?.toString().orEmpty()
        return if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${text.replace("\"", "\"\"")}\""
        } else {
            text
        }
    }
}

public data class ArmourAuditSnapshot(
    val schemaVersion: Int,
    val generatedAt: String,
    val revision: Int,
    val liveCachePath: String,
    val serverCachePath: String,
    val rawOverlayPath: String,
    val scopeRule: String,
    val sourcePriority: List<String>,
    val summary: ArmourAuditSummary,
    val items: List<ArmourAuditRow>,
)

public data class ArmourAuditSummary(
    val clientDefinitions: Int,
    val serverDefinitions: Int,
    val scopedRawIds: Int,
    val normalizedFamilies: Int,
    val matchingRows: Int,
    val mismatchRows: Int,
    val manualModelReviewRows: Int,
    val bySlot: Map<String, Int>,
    val effectCandidates: Map<String, Int>,
)

public data class ArmourAuditRow(
    val id: Int,
    val internalName: String,
    val name: String,
    val sourceFamily: String,
    val members: Boolean,
    val tradeable: Boolean,
    val slot: String,
    val slotId: Int,
    val requirements: List<ArmourRequirementAudit>,
    val expectedRequirements: List<ArmourRequirementAudit>,
    val stats: Map<String, Int>,
    val expectedStats: Map<String, Int>,
    val weight: Double,
    val inventoryOptions: List<String?>,
    val model: ArmourModelAudit,
    val chargeState: String,
    val degradeState: String,
    val effects: List<ArmourEffectAudit>,
    val source: String,
    val owningSourceFile: String,
    val auditStatus: String,
    val mismatchFields: List<String>,
    val comparisons: Map<String, ArmourFieldComparison>,
)

public data class ArmourRequirementAudit(
    val index: Int,
    val skillId: Int?,
    val skill: String?,
    val level: Int,
)

public data class ArmourFieldComparison(
    val current: String?,
    val expected: String?,
    val matches: Boolean,
    val source: String,
    val owningSourceFile: String,
    val corrected: Boolean,
)

public data class ArmourModelAudit(
    val inventoryModel: Int,
    val inventoryZoom: Int,
    val inventoryRotations: List<Int>,
    val inventoryOffsets: List<Int>,
    val resize: List<Int>,
    val maleWornModels: List<Int>,
    val femaleWornModels: List<Int>,
    val maleOffset: Int,
    val femaleOffset: Int,
    val maleHeadModels: List<Int>,
    val femaleHeadModels: List<Int>,
    val appearanceOverrideSlots: List<Int>,
    val hiddenBodyParts: List<String>,
    val teamId: Int,
    val recolourFrom: List<Int>,
    val recolourTo: List<Int>,
    val retextureFrom: List<Int>,
    val retextureTo: List<Int>,
    val status: String,
)

public data class ArmourEffectAudit(
    val family: String,
    val kind: String,
    val registrationStatus: String,
    val expectedBehavior: String,
    val source: String,
)

public data class ArmourAuditPaths(
    val json: Path,
    val csv: Path,
    val effects: Path,
    val models: Path,
)

private data class ArmourEffectAuditReport(
    val generatedAt: String,
    val revision: Int,
    val items: List<ArmourEffectAuditItem>,
)

private data class ArmourEffectAuditItem(
    val id: Int,
    val internalName: String,
    val name: String,
    val slot: String,
    val effects: List<ArmourEffectAudit>,
)

private data class ArmourModelAuditReport(
    val generatedAt: String,
    val revision: Int,
    val manualReviewCount: Int,
    val items: List<ArmourModelAuditItem>,
)

private data class ArmourModelAuditItem(
    val id: Int,
    val internalName: String,
    val name: String,
    val slot: String,
    val model: ArmourModelAudit,
)
