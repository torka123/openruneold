package org.rsmod.content.skills.magic.spell.attacks.arceuus

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.player.events.interact.ObjTargetEvent
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.ui.IfOverlayButtonT
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.obj.EnsouledHeadProvenance
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Authoritative cast handler and catalog for Arceuus reanimations. */
internal class ArceuusReanimationSpellScript
@Inject constructor(
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
    private val state: ArceuusUtilityState,
    private val eventBus: EventBus,
    private val protectedAccess: ProtectedAccessLauncher,
    private val world: WorldRepository,
    private val objects: ObjRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (tier in ArceuusReanimations.tiers) {
            val spell = resolveSpell(tier.spellObj) ?: continue
            val key = EventBus.composeLongKey(spell.component.packed, INVENTORY_ITEMS_COMPONENT.asRSCM(RSCMType.COMPONENT))
            onEvent<IfOverlayButtonT>(key) { dispatchReanimation(spell, tier) }
            onProtectedEvent<ObjTargetEvent>(spell.component.packed) { event ->
                if (event.selected.objType == null) castGroundReanimation(spell, tier, event.obj)
            }
        }
    }
    private fun resolveSpell(internal: String): MagicSpell? {
        val type = ServerCacheManager.getItem(internal.asRSCM(RSCMType.OBJ)) ?: return null
        return spells.getObjSpell(type)
    }
    private fun IfOverlayButtonT.dispatchReanimation(
        spell: MagicSpell,
        tier: ArceuusReanimationTier,
    ) {
        val item = targetObj ?: return
        val definition = ArceuusReanimations.byHead[item.id] ?: run {
            player.mes("This spell cannot reanimate that item.")
            return
        }
        if (definition.tier != tier || targetSlot !in player.inv.indices || player.inv[targetSlot]?.id != item.id) {
            player.mes("This spell cannot reanimate that item.")
            return
        }
        player.clearPendingAction(eventBus)
        player.resetFaceEntity()
        val current = player.inv[targetSlot] ?: return
        protectedAccess.launch(player) {
            if (inv[targetSlot] != current) return@launch
            castReanimation(spell, definition, item.id, current.vars, player.coords.translate(1, 0)) {
                invDel(inv, item.internalName, 1, slot = targetSlot).success
            }
        }
    }

    private suspend fun ProtectedAccess.castGroundReanimation(
        spell: MagicSpell, tier: ArceuusReanimationTier, obj: Obj,
    ) {
        val definition = ArceuusReanimations.byHead[obj.type]
        if (definition == null || definition.tier != tier) {
            mes("This spell cannot reanimate that item.")
            return
        }
        if (!obj.isVisibleTo(player) || objects.findAll(obj.coords).none { it === obj }) return
        if (player.coords.level != obj.level || player.distanceTo(obj.coords) > 1) return
        // The item is removed only after the same spell/location/active-creature checks as inventory casts.
        castReanimation(spell, definition, obj.type, obj.vars, obj.coords, obj.coords) { objects.del(obj) }
    }

    private suspend fun ProtectedAccess.castReanimation(
        spell: MagicSpell,
        definition: ArceuusReanimationDefinition,
        headId: Int,
        headVars: Int,
        target: CoordGrid,
        eligibilityCoords: CoordGrid = player.coords,
        consumeHead: () -> Boolean,
    ) {
        if (actionDelay > mapClock) return
        if (!isByDarkAltar(player) &&
            !EnsouledHeadProvenance.canReanimateAt(headId, headVars, eligibilityCoords)) {
            mes("That creature cannot be reanimated here. The power of the crystals by the Dark Altar will increase the potency of the spell.")
            return
        }
        if (state.hasActiveReanimation(player)) {
            mes("You should finish off your last one first.")
            return
        }
        if (runes.attemptCast(player, spell).isFailure()) return
        if (!consumeHead()) return

        player.faceSquare(target)
        player.anim(seq(REANIMATION_ANIMATION), priority = 6)
        spotanim(spot(REANIMATION_CAST_GRAPHIC))
        val delay =
            world
                .projAnim(player, target, SpotanimType(REANIMATION_PROJECTILE), MAGIC_PROJECTILE)
                .durations.serverDelay
                .coerceAtLeast(1)
        actionDelay = mapClock + REANIMATION_ACTION_DELAY
        statAdvance(MAGIC_STAT, spell.castXp)
        delay(delay)
        world.spotanimMap(SpotanimType(REANIMATION_IMPACT_GRAPHIC), target)
        delay(REANIMATION_SPAWN_DELAY)
        if (player.coords.level == target.level && player.distanceTo(target) <= 8) {
            state.spawnReanimated(player, definition, target)
        }
    }

    private companion object {
        const val MAGIC_STAT = "stat.magic"
        const val REANIMATION_ANIMATION = 7198
        const val REANIMATION_CAST_GRAPHIC = 1288
        const val REANIMATION_PROJECTILE = 1289
        const val REANIMATION_IMPACT_GRAPHIC = 1290
        const val REANIMATION_ACTION_DELAY = 8
        const val REANIMATION_SPAWN_DELAY = 5
        const val INVENTORY_ITEMS_COMPONENT = "component.inventory:items"
        const val MAGIC_PROJECTILE = "projanim.magic_spell"
    }
}

internal object ArceuusReanimations {
    val definitions =
        listOf(
            reanimation(ArceuusReanimationTier.Basic, setOf(13448), 7018, 130.0),
            reanimation(ArceuusReanimationTier.Basic, setOf(13451), 7019, 182.0),
            reanimation(ArceuusReanimationTier.Basic, setOf(13454), 7020, 286.0),
            reanimation(ArceuusReanimationTier.Basic, setOf(13457), 7021, 364.0),
            reanimation(ArceuusReanimationTier.Basic, setOf(13460), 7022, 454.0),
            reanimation(ArceuusReanimationTier.Basic, setOf(13463), 7023, 480.0),
            reanimation(ArceuusReanimationTier.Basic, setOf(13466), 7024, 494.0),
            reanimation(ArceuusReanimationTier.Adept, setOf(13469), 7025, 520.0),
            reanimation(ArceuusReanimationTier.Adept, setOf(13472), 7026, 584.0),
            reanimation(ArceuusReanimationTier.Adept, setOf(13475), 7027, 650.0),
            reanimation(ArceuusReanimationTier.Adept, setOf(13478), 7028, 716.0),
            reanimation(ArceuusReanimationTier.Adept, setOf(13481), 7029, 754.0),
            reanimation(ArceuusReanimationTier.Adept, setOf(13484), 7030, 780.0),
            reanimation(ArceuusReanimationTier.Adept, setOf(13486, 13487), 7031, 832.0),
            reanimation(ArceuusReanimationTier.Expert, setOf(13490), 7032, 884.0),
            reanimation(ArceuusReanimationTier.Expert, setOf(13493), 7033, 936.0),
            reanimation(ArceuusReanimationTier.Expert, setOf(13496), 7034, 1040.0),
            reanimation(ArceuusReanimationTier.Expert, setOf(13499), 7035, 1104.0),
            reanimation(ArceuusReanimationTier.Expert, setOf(13502), 7036, 1170.0),
            reanimation(ArceuusReanimationTier.Expert, setOf(26997), 11463, 1200.0),
            reanimation(ArceuusReanimationTier.Master, setOf(13505), 7037, 1234.0),
            reanimation(ArceuusReanimationTier.Master, setOf(13508), 7038, 1300.0),
            reanimation(ArceuusReanimationTier.Master, setOf(13511), 7039, 1560.0),
        )

    val byHead = definitions.flatMap { definition -> definition.headIds.map { it to definition } }.toMap()
    val tiers = ArceuusReanimationTier.entries

    private fun reanimation(
        tier: ArceuusReanimationTier,
        heads: Set<Int>,
        npcId: Int,
        prayerXp: Double,
    ) = ArceuusReanimationDefinition(
        tier,
        // Each head has adjacent dropped and tradeable variants; the following ID is the note.
        setOf(heads.max() - 1, heads.max()),
        npcId,
        prayerXp,
    )
}

private fun isByDarkAltar(player: Player): Boolean {
    if (player.coords.level != 0) return false
    val x = player.coords.x
    val z = player.coords.z
    var inside = false
    var previous = DARK_ALTAR_POLYGON.lastIndex
    for (current in DARK_ALTAR_POLYGON.indices) {
        val (currentX, currentZ) = DARK_ALTAR_POLYGON[current]
        val (previousX, previousZ) = DARK_ALTAR_POLYGON[previous]
        val crosses = (currentZ > z) != (previousZ > z)
        if (crosses) {
            val boundaryX =
                (previousX - currentX).toDouble() * (z - currentZ).toDouble() /
                    (previousZ - currentZ).toDouble() + currentX
            if (x < boundaryX) inside = !inside
        }
        previous = current
    }
    return inside
}

private val DARK_ALTAR_POLYGON =
    listOf(
        1636 to 3887, 1638 to 3870, 1645 to 3877, 1655 to 3877, 1657 to 3875,
        1657 to 3871, 1655 to 3869, 1653 to 3869, 1650 to 3866, 1650 to 3860,
        1654 to 3856, 1667 to 3856, 1670 to 3853, 1678 to 3853, 1680 to 3851,
        1681 to 3851, 1690 to 3842, 1698 to 3842, 1699 to 3843, 1700 to 3843,
        1701 to 3844, 1703 to 3844, 1704 to 3845, 1709 to 3845, 1711 to 3847,
        1743 to 3848, 1743 to 3857, 1747 to 3867, 1743 to 3871, 1741 to 3871,
        1742 to 3874, 1728 to 3904, 1708 to 3904, 1702 to 3898, 1702 to 3896,
        1696 to 3896, 1696 to 3891, 1694 to 3893, 1691 to 3893, 1689 to 3891,
        1689 to 3887, 1688 to 3887, 1688 to 3894, 1682 to 3894, 1682 to 3887,
        1681 to 3887, 1681 to 3890, 1679 to 3892, 1675 to 3892, 1675 to 3897,
        1669 to 3897, 1669 to 3890, 1667 to 3890, 1657 to 3900, 1649 to 3900,
    )


private fun seq(id: Int): String = RSCM.getReverseMapping(RSCMType.SEQ, id)
private fun spot(id: Int): String = RSCM.getReverseMapping(RSCMType.SPOTANIM, id)
