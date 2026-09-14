package org.rsmod.content.bosses.dt2

import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.droptable.NpcKillRewardContext
import org.rsmod.api.droptable.NpcKillRewardContexts
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class Dt2Combat
@Inject
constructor(
    private val encounters: Dt2EncounterManager,
    private val death: NpcDeath,
    private val players: PlayerList,
    private val deps: BossDeps,
) : PluginScript() {
    override fun ScriptContext.startup() {
        registerDukeBossCombat(
            this,
            deps,
            canTarget = encounters::prepareDukeOpportunity,
            launch = encounters::launchDukeAction,
            modifyHit = {
                val player =
                    if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                    else null
                if (player == null) hit.damage = 0 else encounters.modifyNpcHit(player, npc, hit)
            },
        )
        deps.extensionRegistry.register(LEVIATHAN_ORDINARY_ATTACK) { access, npc, target, _ ->
            encounters.launchLeviathanOrdinary(access, npc, target)
        }
        BossCombat.register(
            this,
            leviathanBossSpec(),
            deps,
            ownerSlug = "leviathan",
            canTarget = encounters::prepareLeviathanOpportunity,
            onModifyHit = {
                val player =
                    if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                    else null
                if (player == null) hit.damage = 0 else encounters.modifyNpcHit(player, npc, hit)
            },
        )
        deps.extensionRegistry.registerAttackParameters(WHISPERER_ATTACK_PARAMETERS) {
            npc,
            target,
            data ->
            encounters.whispererAttackParameters(npc, target, data as Dt2AttackStyle)
        }
        BossCombat.register(
            this,
            whispererBossSpec(),
            deps,
            ownerSlug = "whisperer",
            canTarget = encounters::canAttackWhisperer,
            onModifyHit = {
                val player =
                    if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                    else null
                if (player == null) hit.damage = 0 else encounters.modifyNpcHit(player, npc, hit)
            },
        )
        deps.extensionRegistry.registerAttackParameters(VARDORVIS_ATTACK_PARAMETERS) {
            npc,
            target,
            _ ->
            encounters.vardorvisAttackParameters(npc, target)
        }
        BossCombat.register(
            this,
            vardorvisBossSpec(),
            deps,
            ownerSlug = "vardorvis",
            canTarget = encounters::canAttackVardorvis,
            onModifyHit = {
                val player =
                    if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                    else null
                if (player == null) hit.damage = 0 else encounters.modifyNpcHit(player, npc, hit)
            },
        )
        encounters.combatNpcTypes().forEach { type ->
            if (
                type.id !in
                    setOf(
                        12203,
                        12204,
                        12205,
                        Dt2Boss.VARDORVIS.normalNpcId,
                        Dt2Boss.LEVIATHAN.normalNpcId,
                        Dt2Boss.DUKE.normalNpcId,
                    )
            ) {
                onModifyNpcHit(type) {
                    val player =
                        if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                        else null
                    if (player == null) hit.damage = 0
                    else encounters.modifyNpcHit(player, npc, hit)
                }
            }
            onNpcQueue(type, "queue.death") {
                val run = encounters.beginBossDeath(npc)
                if (run == null) {
                    death.deathNoDrops(this)
                } else {
                    if (encounters.rewardEligible(run)) {
                        NpcKillRewardContexts.set(
                            npc,
                            NpcKillRewardContext(
                                rewardEligible = true,
                                awakened = run.difficulty == Dt2Difficulty.AWAKENED,
                                perfectKill =
                                    (run.bossKind == Dt2Boss.LEVIATHAN &&
                                        run.leviathanPerfectKill) ||
                                        (run.bossKind == Dt2Boss.DUKE && run.duke.perfect),
                                eventId = "${run.eventToken}:reward",
                            ),
                        )
                        try {
                            encounters.prepareDropForm(npc, run)
                            death.deathWithDrops(
                                this,
                                encounters.rewardTile(run),
                                if (run.bossKind == Dt2Boss.DUKE) DukeRules.DEATH else null,
                            )
                        } finally {
                            NpcKillRewardContexts.clear(npc)
                        }
                    } else {
                        death.deathNoDrops(
                            this,
                            if (run.bossKind == Dt2Boss.DUKE) DukeRules.DEATH else null,
                        )
                    }
                    encounters.finishBossDeath(npc, run)
                }
            }
        }
        encounters.helperNpcTypes().forEach { type ->
            onModifyNpcHit(type) {
                val player =
                    if (hit.isFromPlayer) hit.sourceUid?.let(::PlayerUid)?.resolve(players)
                    else null
                if (player == null) hit.damage = 0 else encounters.modifyNpcHit(player, npc, hit)
            }
            onNpcQueue(type, "queue.death") {
                encounters.helperDied(npc)
                death.deathNoDrops(this)
            }
        }
    }

    @Suppress("unused")
    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) {
            "Missing revision-240 DT2 npc definition: $id"
        }
}
