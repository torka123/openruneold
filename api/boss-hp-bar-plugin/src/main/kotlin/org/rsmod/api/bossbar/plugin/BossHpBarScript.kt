package org.rsmod.api.bossbar.plugin

import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.awt.Color
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bossbar.BossHpBarMode
import org.rsmod.api.config.refs.params
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.events.InstancePlayerJoinUnboundEvent
import org.rsmod.api.instances.events.InstancePlayerLeaveUnboundEvent
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifSetHide
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.player.ui.setColour
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
public class BossHpBarScript
@Inject
constructor(
    private val instances: InstanceManager,
    private val contributor: BossHpBarDamageContributor,
    private val protectedAccess: ProtectedAccessLauncher,
    private val players: PlayerList,
) : PluginScript() {

    internal var Player.bossHudDisabled by boolVarBit("varbit.hpbar_hud_boss_disabled")
    internal var Player.bossHudPercentageEnabled by
        boolVarBit("varbit.hpbar_hud_boss_percentage_enabled")
    internal var Player.bossHudCurrentHp by intVarBit("varbit.hpbar_hud_hp")
    internal var Player.bossHudBaseHp by intVarBit("varbit.hpbar_hud_basehp")
    internal var Player.bossHudNpcID by intVarp("varp.hpbar_hud_npc")
    internal var Player.bossHudBarSize by intVarBit("varbit.hpbar_hud_boss")
    private val customBars = HashMap<Long, Npc>()
    private val displayedBars = HashMap<Long, Npc>()
    private val percentagePreferences = HashMap<Long, Boolean>()
    private val labelOverrides = BossHpBarLabelRegistry<Npc>()

    override fun ScriptContext.startup() {
        onEvent<InstancePlayerJoinUnboundEvent> {
            for (npc in instances.npcsForInstance(instanceId)) {
                if (npcBarMode(npc) == BossHpBarMode.ON_ENTER && npc.hitpoints > 0) {
                    onOpen(player, npc)
                }
            }
        }

        onEvent<InstancePlayerLeaveUnboundEvent> {
            for (npc in instances.npcsForInstance(instanceId)) {
                if (npcBarMode(npc) == BossHpBarMode.ON_ENTER) {
                    onClose(player, npc, instant = true)
                }
            }
        }

        onPlayerSoftTimer(BossHpBarDamageContributor.COMBAT_CHECK_TIMER) {
            contributor.checkAndExpirePlayer(player)
        }

        onEvent<NpcStateEvents.Delete> {
            val tracked = contributor.removeTrackedForNpc(npc)
            for (player in tracked) {
                onClose(player, npc)
            }
            // A transform may change ON_ENTER/ON_ATTACK to NEVER before deletion.
            closeForAllInstancePlayers(npc)
            labelOverrides.removeTarget(npc)
        }

        onEvent<NpcStateEvents.Respawn> {
            if (npcBarMode(npc) != BossHpBarMode.ON_ENTER) return@onEvent
            val instanceId = instances.instanceForNpc(npc) ?: return@onEvent
            for (player in contributor.playersInInstance(instanceId)) {
                onOpen(player, npc)
                onUpdate(player, npc, npc.baseHitpointsLvl, npc.baseHitpointsLvl)
            }
        }

        onPlayerLogout {
            clearCustomOverride(player)
            player.uuid?.let { key ->
                displayedBars.remove(key)
                labelOverrides.removePlayer(key)
            }
        }
    }

    /**
     * Overrides the visible name for this player's current bar without changing the NPC type.
     *
     * The override survives normal/custom bar reopenings and NPC transmogs until that NPC is
     * deleted or the player logs out.
     */
    public fun setLabel(player: Player, npc: Npc, label: String) {
        val key = player.uuid ?: return
        labelOverrides.put(key, npc, label)
        if (!player.bossHudDisabled && displayedBars[key] === npc) {
            player.ifSetText("component.hpbar_hud:creature_name", label)
        }
    }

    public fun onOpen(player: Player, npc: Npc) {
        if (player.bossHudDisabled) return
        clearCustomOverride(player)
        open(player, npc, npc.hitpoints, npc.baseHitpointsLvl, ORIGINAL_STYLE)
    }

    public fun onOpenCustom(
        player: Player,
        npc: Npc,
        currentValue: Int,
        maximumValue: Int,
        style: BossHpBarStyle,
    ) {
        if (player.bossHudDisabled) return
        val key = player.uuid ?: return
        val alreadyOpen = customBars[key] === npc
        if (style.forceExactValues) {
            percentagePreferences.putIfAbsent(key, player.bossHudPercentageEnabled)
            player.bossHudPercentageEnabled = false
        } else {
            clearCustomOverride(player)
        }
        customBars[key] = npc
        if (alreadyOpen) {
            applyStyle(player, style)
            onUpdate(player, npc, currentValue, maximumValue)
            return
        }
        open(player, npc, currentValue, maximumValue, style)
    }

    private fun open(
        player: Player,
        npc: Npc,
        currentValue: Int,
        maximumValue: Int,
        style: BossHpBarStyle,
    ) {
        player.uuid?.let { displayedBars[it] = npc }
        player.ifSetHide("component.hpbar_hud:hp", false)
        player.bossHudNpcID = npc.id
        player.bossHudBaseHp = maximumValue.coerceAtLeast(1)
        player.bossHudCurrentHp = currentValue.coerceIn(0, maximumValue.coerceAtLeast(1))
        player.bossHudBarSize = 1
        applyStyle(player, style)
        player.runClientScript(2287, bossHpBarScriptArgs(commonComponents, 0))
        openScripts(player)
        val key = player.uuid
        if (key != null) {
            labelOverrides.get(key, npc)?.let { label ->
                player.ifSetText("component.hpbar_hud:creature_name", label)
            }
        }
    }

    private fun applyStyle(player: Player, style: BossHpBarStyle) {
        player.setColour("component.hpbar_hud:inner", style.inner)
        player.setColour("component.hpbar_hud:health_bar_back", style.back)
        player.setColour("component.hpbar_hud:health_bar_sliding", style.sliding)
        player.setColour("component.hpbar_hud:health_bar_remaining", style.remaining)
    }

    private fun clearCustomOverride(player: Player, npc: Npc? = null) {
        val key = player.uuid ?: return
        val active = customBars[key] ?: return
        if (npc != null && active !== npc) return
        customBars.remove(key)
        percentagePreferences.remove(key)?.let { player.bossHudPercentageEnabled = it }
    }

    @OptIn(InternalApi::class)
    public fun onClose(player: Player, npc: Npc, instant: Boolean = false) {
        val key = player.uuid
        val displayed = key?.let(displayedBars::get)
        if (displayed != null && displayed !== npc) return
        clearCustomOverride(player, npc)
        if (key != null && displayedBars[key] === npc) {
            displayedBars.remove(key)
        }
        if (instant) {
            clearHudValues(player)
            player.ifSetHide("component.hpbar_hud:hp", true)
            return
        }
        protectedAccess.launchLenient(player) {
            player.runClientScript(2889, bossHpBarScriptArgs(commonComponents, 0))
            delay(2)
            if (key != null && displayedBars[key] != null) {
                return@launchLenient
            }
            clearHudValues(player)
            player.ifSetHide("component.hpbar_hud:hp", true)
        }
    }

    public fun onUpdate(
        player: Player,
        npc: Npc,
        currentHp: Int = npc.hitpoints,
        maxHp: Int = npc.baseHitpointsLvl,
    ) {
        if (player.bossHudDisabled) return

        val key = player.uuid
        if (key != null && displayedBars[key] !== npc) return
        val maximum = maxHp.coerceAtLeast(1)
        player.bossHudBaseHp = maximum
        player.bossHudCurrentHp = currentHp.coerceIn(0, maximum)
    }

    private fun clearHudValues(player: Player) {
        player.bossHudBarSize = 0
        player.bossHudCurrentHp = 0
        player.bossHudBaseHp = 1
        player.bossHudNpcID = 0
    }

    private fun closeForAllInstancePlayers(npc: Npc) {
        // Deletion listeners may already have detached the NPC from its instance. The HUD owns
        // its displayed-target identity and must close regardless of listener registration order.
        for (player in players.filter { displayedBars[it.uuid] === npc }) {
            onClose(player, npc)
        }
    }

    private fun npcBarMode(npc: Npc): BossHpBarMode =
        BossHpBarMode.fromId(npc.visType.paramOrNull(params.boss_hp_bar_mode) ?: 0)

    private fun openScripts(player: Player) {
        player.runClientScript(2887, bossHpBarScriptArgs(commonComponents, 255))
        player.runClientScript(2102, bossHpBarScriptArgs(commonComponents, 1))

        player.runClientScript(
            2376,
            "component.hpbar_hud:universe".asRSCM(),
            "component.hpbar_hud:hpdodgerside".asRSCM(),
            "component.hpbar_hud:hpdodger".asRSCM(),
            "component.hpbar_hud:hp".asRSCM(),
            "component.hpbar_hud:name_area".asRSCM(),
            "component.hpbar_hud:hp_bar".asRSCM(),
            "component.hpbar_hud:hp_bar_text".asRSCM(),
            "component.hpbar_hud:health_bar_back".asRSCM(),
            "component.hpbar_hud:health_bar_sliding".asRSCM(),
            "component.hpbar_hud:health_bar_remaining".asRSCM(),
            "component.hpbar_hud:creature_name".asRSCM(),
            "component.hpbar_hud:outer_border".asRSCM(),
            "component.hpbar_hud:name_backing".asRSCM(),
            "component.hpbar_hud:inner_border".asRSCM(),
            "component.hpbar_hud:health_bar_lower_threshold".asRSCM(),
            "component.hpbar_hud:health_bar_upper_threshold".asRSCM(),
            "component.hpbar_hud:hp_bar_1".asRSCM(),
            "component.hpbar_hud:hp_bar_2".asRSCM(),
            "component.hpbar_hud:hpcontentside".asRSCM(),
        )
    }

    public val commonComponents: IntArray =
        intArrayOf(
            "component.hpbar_hud:hp".asRSCM(),
            "component.hpbar_hud:name_area".asRSCM(),
            "component.hpbar_hud:outer_border".asRSCM(),
            "component.hpbar_hud:name_backing".asRSCM(),
            "component.hpbar_hud:creature_name".asRSCM(),
            "component.hpbar_hud:inner_border".asRSCM(),
            "component.hpbar_hud:health_bar_back".asRSCM(),
            "component.hpbar_hud:health_bar_sliding".asRSCM(),
            "component.hpbar_hud:health_bar_remaining".asRSCM(),
            "component.hpbar_hud:hp_bar_text".asRSCM(),
            "component.hpbar_hud:health_bar_lower_threshold".asRSCM(),
            "component.hpbar_hud:health_bar_upper_threshold".asRSCM(),
            "component.hpbar_hud:hp_bar_1".asRSCM(),
            "component.hpbar_hud:hp_bar_2".asRSCM(),
        )

    public companion object {
        public val ORIGINAL_COLORS: Array<Color> =
            arrayOf(Color(204, 0, 0), Color(149, 0, 0), Color(0, 245, 0))
        public val ORIGINAL_STYLE: BossHpBarStyle =
            BossHpBarStyle(
                inner = ORIGINAL_COLORS[0],
                back = ORIGINAL_COLORS[1],
                sliding = ORIGINAL_COLORS[2],
            )
    }
}

internal fun bossHpBarScriptArgs(components: IntArray, trailingValue: Int): List<Any> =
    buildList(components.size + 1) {
        for (component in components) {
            add(component)
        }
        add(trailingValue)
    }

/** Player-scoped label overrides use target identity so NPC transmogs retain their label safely. */
internal class BossHpBarLabelRegistry<T : Any> {
    private data class Entry<T : Any>(val target: T, val label: String)

    private val entries = HashMap<Long, Entry<T>>()

    fun put(playerId: Long, target: T, label: String) {
        entries[playerId] = Entry(target, label)
    }

    fun get(playerId: Long, target: T): String? =
        entries[playerId]?.takeIf { it.target === target }?.label

    fun removePlayer(playerId: Long) {
        entries.remove(playerId)
    }

    fun removeTarget(target: T) {
        entries.entries.removeIf { it.value.target === target }
    }
}
