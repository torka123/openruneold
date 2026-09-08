package org.rsmod.content.systems.dailyadventure

import jakarta.inject.Inject
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.config.refs.CanonicalSlayerCategoryMembership
import org.rsmod.api.dailyadventure.DailyAdventureAction
import org.rsmod.api.dailyadventure.DailyAdventureProgressEvent
import org.rsmod.api.dailyadventure.currentDailyAdventureDay
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.npc.hit.NpcDamageContributor
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.module.PluginModule

public class DailyAdventureModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcDamageContributor>(DailyAdventureDamageHook::class.java)
        addSetBinding<NpcDeathKillHook>(DailyAdventureKillHook::class.java)
    }
}

public class DailyAdventureDamageHook
@Inject
constructor(
    private val service: DailyAdventureService,
    private val attackStyles: AttackStyles,
) : NpcDamageContributor {
    override fun onPlayerDamageNpc(npc: Npc, source: Player, damage: Int) {
        if (damage <= 0) return
        val metadata = npcMetadata(npc)
        val style = attackStyle(source, attackStyles)
        val wilderness = source.inDailyAdventureWilderness()
        service.progress(
            source,
            DailyAdventureProgressEvent(
                id = service.nextEventId(source, "npc-damage"),
                action = DailyAdventureAction.NpcDamage,
                amount = damage.toLong(),
                combatStyle = style,
                category = metadata.category,
                wilderness = wilderness,
                dimension = style,
            ),
        )
        service.progress(
            source,
            DailyAdventureProgressEvent(
                id = service.nextEventId(source, "npc-hit"),
                action = DailyAdventureAction.NpcHit,
                combatStyle = style,
                category = metadata.category,
                wilderness = wilderness,
                dimension = style,
            ),
        )
        if (metadata.boss) {
            service.progress(
                source,
                DailyAdventureProgressEvent(
                    id = service.nextEventId(source, "boss-damage"),
                    action = DailyAdventureAction.BossDamage,
                    amount = damage.toLong(),
                    combatStyle = style,
                    category = metadata.category,
                    wilderness = wilderness,
                    dimension = style,
                ),
            )
        }
    }
}

public class DailyAdventureKillHook
@Inject
constructor(
    private val service: DailyAdventureService,
    private val attackStyles: AttackStyles,
) : NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        val player = context.hero
        val npc = context.npc
        val metadata = npcMetadata(npc)
        val style = attackStyle(player, attackStyles)
        val wilderness = player.inDailyAdventureWilderness()
        val eventBase =
            "${currentDailyAdventureDay()}:${player.accountId}:npc-kill:${context.lootTrackerEventId}:${npc.visType.id}"
        service.progress(
            player,
            DailyAdventureProgressEvent(
                id = "$eventBase:normal",
                action = DailyAdventureAction.NpcKill,
                combatStyle = style,
                category = metadata.category,
                wilderness = wilderness,
                slayerTarget = player.isCurrentSlayerTarget(npc),
                dimension = style,
            ),
        )
        if (metadata.boss) {
            service.progress(
                player,
                DailyAdventureProgressEvent(
                    id = "$eventBase:boss",
                    action = DailyAdventureAction.BossKill,
                    combatStyle = style,
                    category = metadata.category,
                    wilderness = wilderness,
                    dimension = style,
                ),
            )
        }
    }
}

private data class DailyAdventureNpcMetadata(val boss: Boolean, val category: String?)

private fun npcMetadata(npc: Npc): DailyAdventureNpcMetadata {
    val type = npc.visType
    val boss =
        (type.paramOrNull(BaseParams.boss_hp_bar_mode) ?: 0) != 0 ||
            type.paramOrNull(BaseParams.killcount_varp) != null
    val category =
        when {
            (type.paramOrNull(BaseParams.revenant) ?: 0) != 0 -> "revenant"
            (type.paramOrNull(BaseParams.draconic) ?: 0) != 0 -> "dragon"
            (type.paramOrNull(BaseParams.demon) ?: 0) != 0 -> "demon"
            else -> null
        }
    return DailyAdventureNpcMetadata(boss, category)
}

private fun attackStyle(player: Player, styles: AttackStyles): String {
    val style = styles.get(player)
    return when {
        style?.isRanged == true -> "ranged"
        style?.isMagic == true -> "magic"
        else -> "melee"
    }
}

private fun Player.inDailyAdventureWilderness(): Boolean =
    vars["varbit.inside_wilderness"] != 0

private fun Player.isCurrentSlayerTarget(npc: Npc): Boolean {
    val categoryId = vars[CanonicalSlayerCategoryMembership.ASSIGNMENT_CATEGORY_VARP]
    if (categoryId > 0) {
        return CanonicalSlayerCategoryMembership.matches(categoryId, npc.visType.id)
    }
    val taskId = vars["varp.slayer_target"]
    if (taskId <= 0) return false
    return taskId in npc.visType.slayerTaskIds ||
        npc.visType.paramOrNull(BaseParams.slayer_task_id) == taskId
}
