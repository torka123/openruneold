package org.rsmod.content.skills.farming

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.farmingLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext
import kotlin.math.absoluteValue

private const val RAKE = 5341
private const val SEED_DIBBER = 5343
private const val SPADE = 952
private const val PLANT_CURE = 6036
private const val COMPOST = 6032
private const val SUPERCOMPOST = 6034
private const val ULTRACOMPOST = 21483
private const val EMPTY_BUCKET = 1925

/** Player-owned Farming patches backed by the live cache's mult-loc varbits. */
@Singleton
class FarmingService @Inject constructor(private val xpMods: XpModifiers) {
    private val patchByBaseId = FarmingData.patches.associateBy(FarmingPatchDef::baseLocId)
    private val usablePatches: List<FarmingPatchDef> by lazy {
        FarmingData.patches.filter { ServerCacheManager.getObject(it.baseLocId) != null }
    }

    fun registeredVisualIds(): Set<Int> = usablePatches.flatMapTo(mutableSetOf()) { visualIds(it.baseLocId) }

    fun resurrectionStatus(player: Player, baseLocId: Int): FarmingResurrectionStatus {
        val patch = patchByBaseId[baseLocId] ?: return FarmingResurrectionStatus.NotFarmingPatch
        val state = refresh(player, patch, player.loadFarmingPatch(patch.key))
        return when {
            state.cropKey == null || !state.dead -> FarmingResurrectionStatus.NotDead
            state.resurrected -> FarmingResurrectionStatus.AlreadyAttempted
            else -> FarmingResurrectionStatus.Ready
        }
    }

    fun resurrect(player: Player, baseLocId: Int, successful: Boolean): FarmingResurrectionStatus {
        val patch = patchByBaseId[baseLocId] ?: return FarmingResurrectionStatus.NotFarmingPatch
        val state = refresh(player, patch, player.loadFarmingPatch(patch.key))
        val status = when {
            state.cropKey == null || !state.dead -> FarmingResurrectionStatus.NotDead
            state.resurrected -> FarmingResurrectionStatus.AlreadyAttempted
            else -> FarmingResurrectionStatus.Ready
        }
        if (status != FarmingResurrectionStatus.Ready) return status
        val next =
            if (successful) {
                state.copy(dead = false, diseased = false, resurrected = true)
            } else {
                FarmingPatchState(compost = 0, weeds = 3)
            }
        player.saveFarmingPatch(patch.key, next)
        sync(player, patch, next)
        return if (successful) FarmingResurrectionStatus.Resurrected else FarmingResurrectionStatus.Cleared
    }

    fun syncPlayer(player: Player) {
        usablePatches.forEach { patch -> sync(player, patch, refresh(player, patch, player.loadFarmingPatch(patch.key))) }
    }

    suspend fun handle(access: ProtectedAccess, baseLocId: Int, option: Int) {
        val patch = patchByBaseId[baseLocId] ?: return
        var state = refresh(access.player, patch, access.player.loadFarmingPatch(patch.key))
        when (option) {
            1 -> {
                when {
                    state.weeds < 3 -> rake(access, patch, state)
                    state.cropKey == null -> access.mes("This ${patch.label} patch is clear and ready to be planted.")
                    state.dead -> clear(access, patch, state)
                    state.diseased -> access.mes("Your ${crop(state)?.name ?: "crop"} is diseased. Use plant cure on it before it dies.")
                    !mature(state, crop(state)) -> access.mes(growthMessage(state, crop(state)))
                    else -> interactMature(access, patch, state)
                }
            }
            2 -> access.mes(inspect(state, patch))
            3 -> clear(access, patch, state)
            4 -> access.mes("Use a seed, sapling, compost, plant cure, rake, or spade on this ${patch.label} patch.")
        }
    }

    suspend fun useItem(access: ProtectedAccess, baseLocId: Int, itemInternal: Int) {
        val patch = patchByBaseId[baseLocId] ?: return
        val rawItem = FarmingData.crops.firstOrNull { internalObj(it.inputId) == itemInternal }
        if (rawItem != null) {
            plant(access, patch, rawItem)
            return
        }
        when (itemInternal) {
            internalObj(COMPOST) -> compost(access, patch, 1)
            internalObj(SUPERCOMPOST) -> compost(access, patch, 2)
            internalObj(ULTRACOMPOST) -> compost(access, patch, 3)
            internalObj(PLANT_CURE) -> cure(access, patch)
            internalObj(SPADE) -> clear(access, patch, refresh(access.player, patch, access.player.loadFarmingPatch(patch.key)))
            internalObj(RAKE) -> rake(access, patch, refresh(access.player, patch, access.player.loadFarmingPatch(patch.key)))
            else -> access.mes("Nothing interesting happens.")
        }
    }

    fun forceGrow(player: Player): Int {
        var changed = 0
        usablePatches.forEach { patch ->
            val state = player.loadFarmingPatch(patch.key)
            val crop = crop(state) ?: return@forEach
            player.saveFarmingPatch(
                patch.key,
                state.copy(plantedAt = System.currentTimeMillis() - crop.growthMinutes * 60_000L, diseased = false, dead = false),
            )
            sync(player, patch, player.loadFarmingPatch(patch.key))
            changed++
        }
        return changed
    }

    fun clearAll(player: Player): Int {
        usablePatches.forEach { patch ->
            player.saveFarmingPatch(patch.key, FarmingPatchState())
            sync(player, patch, FarmingPatchState())
        }
        return usablePatches.size
    }

    fun status(player: Player): String {
        val active = usablePatches.mapNotNull { patch ->
            val state = refresh(player, patch, player.loadFarmingPatch(patch.key))
            crop(state)?.let { "${patch.label}: ${it.name}" }
        }
        return if (active.isEmpty()) "You have no crops planted." else "Active crops: ${active.joinToString()}."
    }

    private suspend fun rake(access: ProtectedAccess, patch: FarmingPatchDef, state: FarmingPatchState) {
        if (state.weeds >= 3) {
            access.mes("This ${patch.label} patch is already clear.")
            return
        }
        if (!has(access.player, RAKE)) {
            access.mes("You need a rake to clear the weeds.")
            return
        }
        anim(access, 2273)
        val next = state.copy(weeds = (state.weeds + 1).coerceAtMost(3))
        access.player.saveFarmingPatch(patch.key, next)
        award(access, 3.0)
        access.mes(if (next.weeds == 3) "You clear the ${patch.label} patch." else "You rake away some weeds.")
        sync(access.player, patch, next)
    }

    private suspend fun plant(access: ProtectedAccess, patch: FarmingPatchDef, crop: FarmingCrop) {
        var state = refresh(access.player, patch, access.player.loadFarmingPatch(patch.key))
        if (state.weeds < 3) {
            access.mes("You need to clear the weeds before planting anything.")
            return
        }
        if (state.cropKey != null) {
            access.mes("There is already something growing in this patch.")
            return
        }
        if (!canPlant(patch, crop)) {
            access.mes("You cannot plant ${crop.name} in this ${patch.label} patch.")
            return
        }
        if (access.player.farmingLvl < crop.level) {
            access.mes("You need Farming level ${crop.level} to plant ${crop.name}.")
            return
        }
        val tree = crop.kind in setOf(FarmingPatchKind.TREE, FarmingPatchKind.FRUIT_TREE, FarmingPatchKind.CALQUAT, FarmingPatchKind.SPIRIT_TREE)
        val tool = if (tree) SPADE else SEED_DIBBER
        if (!has(access.player, tool)) {
            access.mes("You need a ${if (tree) "spade" else "seed dibber"} to plant that.")
            return
        }
        if (access.player.invDel(access.player.inv, internalObj(crop.inputId), 1, strict = true).failure) {
            access.mes("You no longer have the ${crop.name} to plant.")
            return
        }
        anim(access, if (tree) 830 else 2291)
        state = state.copy(cropKey = crop.key, plantedAt = System.currentTimeMillis(), diseased = false, dead = false, harvestsLeft = harvestCount(crop, state.compost), healthChecked = false)
        access.player.saveFarmingPatch(patch.key, state)
        award(access, crop.plantXp)
        access.mes("You plant the ${crop.name}.")
        sync(access.player, patch, state)
    }

    private suspend fun compost(access: ProtectedAccess, patch: FarmingPatchDef, grade: Int) {
        val state = refresh(access.player, patch, access.player.loadFarmingPatch(patch.key))
        if (state.weeds < 3) {
            access.mes("You need to clear the weeds before treating this patch.")
            return
        }
        if (state.compost >= grade) {
            access.mes("This patch has already been treated with compost of equal or better quality.")
            return
        }
        val raw = when (grade) { 1 -> COMPOST; 2 -> SUPERCOMPOST; else -> ULTRACOMPOST }
        if (access.player.invDel(access.player.inv, internalObj(raw), 1, strict = true).failure) {
            access.mes("You do not have that compost.")
            return
        }
        access.player.invAdd(access.player.inv, internalObj(EMPTY_BUCKET), 1, strict = false)
        anim(access, 2283)
        val next = state.copy(compost = grade)
        access.player.saveFarmingPatch(patch.key, next)
        award(access, 4.0)
        access.mes("You treat the ${patch.label} patch with ${if (grade == 1) "compost" else if (grade == 2) "supercompost" else "ultracompost"}.")
        sync(access.player, patch, next)
    }

    private suspend fun cure(access: ProtectedAccess, patch: FarmingPatchDef) {
        val state = refresh(access.player, patch, access.player.loadFarmingPatch(patch.key))
        if (!state.diseased || state.dead) {
            access.mes("This plant does not need plant cure.")
            return
        }
        if (access.player.invDel(access.player.inv, internalObj(PLANT_CURE), 1, strict = true).failure) {
            access.mes("You do not have any plant cure.")
            return
        }
        anim(access, 2288)
        val crop = crop(state) ?: return
        val stage = growthStage(state, crop)
        val cured = state.copy(diseased = false, plantedAt = System.currentTimeMillis() - stage * crop.stageMillis)
        access.player.saveFarmingPatch(patch.key, cured)
        access.mes("The plant cure restores your crop to health.")
        sync(access.player, patch, cured)
    }

    private suspend fun clear(access: ProtectedAccess, patch: FarmingPatchDef, state: FarmingPatchState) {
        if (state.cropKey == null) {
            access.mes("There is nothing to clear from this ${patch.label} patch.")
            return
        }
        if (!has(access.player, SPADE)) {
            access.mes("You need a spade to clear this patch.")
            return
        }
        anim(access, 831)
        val cleared = FarmingPatchState(compost = 0, weeds = 3)
        access.player.saveFarmingPatch(patch.key, cleared)
        access.mes("You clear the ${patch.label} patch.")
        sync(access.player, patch, cleared)
    }

    private suspend fun interactMature(access: ProtectedAccess, patch: FarmingPatchDef, state: FarmingPatchState) {
        val crop = crop(state) ?: return
        if (crop.kind == FarmingPatchKind.HESPORI) {
            access.mes("The Hespori is fully grown. Its boss encounter is accessed from the mature patch.")
            return
        }
        if (crop.outputId < 0) {
            if (!state.healthChecked && crop.healthXp > 0.0) {
                val checked = state.copy(healthChecked = true)
                access.player.saveFarmingPatch(patch.key, checked)
                award(access, crop.healthXp)
                access.mes("Your ${crop.name} is healthy.")
                sync(access.player, patch, checked)
            } else {
                access.mes("Your ${crop.name} is fully grown. Use a spade to clear it when you are ready to replant.")
            }
            return
        }
        if (state.harvestsLeft <= 0) {
            access.mes("There is nothing left to harvest. Use a spade to clear this patch.")
            return
        }
        if (access.player.invAdd(access.player.inv, internalObj(crop.outputId), 1, strict = true).failure) {
            access.mes("You need an empty inventory space to harvest this crop.")
            return
        }
        anim(access, 2282)
        val left = state.harvestsLeft - 1
        val next = if (left <= 0 && crop.kind !in setOf(FarmingPatchKind.FRUIT_TREE, FarmingPatchKind.BUSH, FarmingPatchKind.CACTUS, FarmingPatchKind.CALQUAT, FarmingPatchKind.TREE)) {
            FarmingPatchState(compost = 0, weeds = 3)
        } else {
            state.copy(harvestsLeft = left.coerceAtLeast(0))
        }
        access.player.saveFarmingPatch(patch.key, next)
        award(access, crop.harvestXp)
        access.mes("You harvest ${crop.name}.")
        sync(access.player, patch, next)
    }

    private fun refresh(player: Player, patch: FarmingPatchDef, stored: FarmingPatchState): FarmingPatchState {
        val crop = crop(stored) ?: return stored
        if (stored.dead || stored.diseased) return stored
        val stage = growthStage(stored, crop)
        if (stage in 1 until crop.stages && !diseaseImmune(crop)) {
            val chancePerStage = when (stored.compost) { 0 -> 35; 1 -> 24; 2 -> 12; else -> 1 }
            val sick = (1..stage).any { growthDiseaseRoll(player, patch, crop, it) < chancePerStage }
            if (sick) {
                val diseased = stored.copy(diseased = true)
                player.saveFarmingPatch(patch.key, diseased)
                return diseased
            }
        }
        return stored
    }

    private fun sync(player: Player, patch: FarmingPatchDef, state: FarmingPatchState) {
        val type = ServerCacheManager.getObject(patch.baseLocId) ?: return
        val varbit = type.multiVarBit.takeIf { it >= 0 }?.let(ServerCacheManager::getVarbit) ?: return
        val crop = crop(state)
        val rawValue = when {
            crop == null -> if (state.weeds < 3) state.weeds else 3
            state.dead -> 128 + crop.visualIndex
            state.diseased -> 64 + crop.visualIndex + growthStage(state, crop)
            else -> crop.visualIndex + growthStage(state, crop)
        }
        val visualValue = rawValue.coerceIn(0, type.multiLoc.lastIndex.coerceAtLeast(0))
        VarPlayerIntMapSetter.set(player, varbit, visualValue)
    }

    private fun inspect(state: FarmingPatchState, patch: FarmingPatchDef): String {
        val crop = crop(state)
        if (crop == null) return "This is a ${patch.label} patch. " + if (state.weeds < 3) "It needs weeding." else "It is clear for planting."
        if (state.dead) return "Your ${crop.name} has died. Use a spade to clear the patch."
        if (state.diseased) return "Your ${crop.name} is diseased. Use plant cure before it dies."
        return if (mature(state, crop)) "Your ${crop.name} is fully grown." else growthMessage(state, crop)
    }

    private fun growthMessage(state: FarmingPatchState, crop: FarmingCrop?): String {
        if (crop == null) return "There is nothing growing here."
        val remaining = ((crop.growthMinutes * 60_000L - (System.currentTimeMillis() - state.plantedAt)).coerceAtLeast(0L) + 59_999L) / 60_000L
        return "Your ${crop.name} is growing. About $remaining minute${if (remaining == 1L) "" else "s"} remain."
    }

    private fun crop(state: FarmingPatchState): FarmingCrop? = state.cropKey?.takeIf { it.isNotBlank() }?.let(FarmingData.cropsByKey::get)
    private fun mature(state: FarmingPatchState, crop: FarmingCrop?): Boolean = crop != null && growthStage(state, crop) >= crop.stages
    private fun growthStage(state: FarmingPatchState, crop: FarmingCrop): Int = ((System.currentTimeMillis() - state.plantedAt).coerceAtLeast(0L) / crop.stageMillis).toInt().coerceIn(0, crop.stages)
    private fun harvestCount(crop: FarmingCrop, compost: Int): Int = if (crop.harvests == 0) 0 else crop.harvests + compost.coerceAtMost(2)
    private fun has(player: Player, rawItemId: Int): Boolean = runCatching { player.inv.count(RSCM.getReverseMapping(RSCMType.OBJ, rawItemId)) > 0 }.getOrDefault(false)
    private fun internalObj(rawId: Int): Int = rawId
    private suspend fun anim(access: ProtectedAccess, rawSequence: Int) { access.anim(RSCM.getReverseMapping(RSCMType.SEQ, rawSequence)) }
    private fun award(access: ProtectedAccess, xp: Double) { access.statAdvance("stat.farming", xp * xpMods.get(access.player, "stat.farming")) }
    private fun diseaseImmune(crop: FarmingCrop): Boolean = crop.key in setOf("poison_ivy", "attas", "iasor", "kronos", "spirit_tree", "hespori")
    private fun growthDiseaseRoll(player: Player, patch: FarmingPatchDef, crop: FarmingCrop, stage: Int): Int {
        val source = "${player.uuid}:${patch.key}:${crop.key}:$stage".hashCode()
        return source.absoluteValue % 1000
    }
    private fun canPlant(patch: FarmingPatchDef, crop: FarmingCrop): Boolean = when (patch.kind) {
        FarmingPatchKind.TREE -> crop.kind == FarmingPatchKind.TREE && when {
            patch.key.startsWith("redwood_") -> crop.key == "redwood"
            patch.key == "celastrus" -> crop.key == "celastrus"
            else -> crop.key !in setOf("redwood", "celastrus")
        }
        else -> patch.kind == crop.kind
    }
    private fun visualIds(baseId: Int): Set<Int> {
        val found = mutableSetOf<Int>()
        fun visit(id: Int) {
            if (id < 0 || !found.add(id)) return
            val type = ServerCacheManager.getObject(id) ?: return
            type.multiLoc.forEach { visit(it and 0xFFFF) }
            visit(type.multiDefault)
        }
        visit(baseId)
        return found
    }
}

enum class FarmingResurrectionStatus {
    Ready,
    Resurrected,
    Cleared,
    NotDead,
    AlreadyAttempted,
    NotFarmingPatch,
}
