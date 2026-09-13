package org.rsmod.api.npc.hit.processor

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.HealthBarServerType
import jakarta.inject.Inject
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.headbar.InternalNpcHeadbars
import org.rsmod.api.npc.hit.NpcDamageContributor
import org.rsmod.api.player.cheat.adminOneHit
import org.rsmod.api.player.ironman.shouldBlockNpcCombatXp
import org.rsmod.api.player.output.soundSynth
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.Hit

public class StandardNpcHitProcessor
@Inject
constructor(
    private val playerList: PlayerList,
    private val eventBus: EventBus,
    private val damageContributors: Set<NpcDamageContributor>,
) : NpcHitProcessor {
    override fun StandardNpcAccess.process(hit: Hit) {
        if (!hit.isValidNow()) return
        val source = if (hit.isFromPlayer) hit.resolvePlayerSource(playerList) else null
        val processedHit =
            if (
                shouldApplyAdminOneHit(
                    enabled = source?.adminOneHit == true,
                    incomingDamage = hit.damage,
                    hitpoints = npc.hitpoints,
                    ignoresCombat = npc.ignoreCombatInteractions,
                    scriptedDamageGate = npc.scriptedDamageGate,
                )
            ) {
                hit.copy(hitmark = hit.hitmark.copy(damage = npc.hitpoints))
            } else {
                hit
            }
        val resolveKey = npc.visType.id.toLong()
        val resolvedDamage =
            if (eventBus.keyed[NpcHitEvents.Resolve::class.java, resolveKey] != null) {
                val event = NpcHitEvents.Resolve(npc, hit, processedHit.damage, resolveKey)
                eventBus.publish(event)
                // A content rule may retire the encounter while resolving this queued hit.
                if (!hit.isValidNow()) return
                event.damage.coerceIn(0, npc.hitpoints.coerceAtLeast(0))
            } else processedHit.damage.coerceAtMost(npc.hitpoints)
        val changedDamage = resolvedDamage.takeIf { it != processedHit.damage }

        if (changedDamage == 0) {
            val zeroDamageHitmark = hitmark_groups.zero_damage
            val modifiedHitmark =
                processedHit.hitmark.copy(
                    self = zeroDamageHitmark.lit.asRSCM(RSCMType.HITMARK),
                    source = zeroDamageHitmark.lit.asRSCM(RSCMType.HITMARK),
                    public =
                        if (processedHit.hitmark.isPrivate) null
                        else zeroDamageHitmark.tint?.asRSCM(RSCMType.HITMARK),
                    damage = changedDamage,
                )
            takeHit(
                processedHit.copy(hitmark = modifiedHitmark),
                hit,
                processedHit.damage,
                resolveKey,
            )
            return
        }

        if (source != null && source.shouldBlockNpcCombatXp(npc)) {
            val blocked = hitmark_groups.ironman_blocked
            val mark = blocked.lit.asRSCM(RSCMType.HITMARK)
            val modifiedHitmark =
                processedHit.hitmark.copy(
                    self = mark,
                    source = mark,
                    public = if (processedHit.hitmark.isPrivate) null else mark,
                    damage = changedDamage ?: processedHit.damage,
                )
            takeHit(
                processedHit.copy(hitmark = modifiedHitmark),
                hit,
                processedHit.damage,
                resolveKey,
            )
            return
        }

        if (changedDamage != null) {
            val modifiedHitmark = processedHit.hitmark.copy(damage = changedDamage)
            takeHit(
                processedHit.copy(hitmark = modifiedHitmark),
                hit,
                processedHit.damage,
                resolveKey,
            )
            return
        }

        takeHit(processedHit, hit, processedHit.damage, resolveKey)
    }

    private fun StandardNpcAccess.takeHit(
        hit: Hit,
        originalHit: Hit,
        inputDamage: Int,
        resolveKey: Long,
    ) {
        check(hit.damage <= npc.hitpoints) {
            "Expected hit damage to be less than or equal to available hitpoints: " +
                "health=${npc.hitpoints}, hit=$hit"
        }
        // TODO(combat): Process recoils, retribution(?), etc.
        npc.hitpoints -= hit.damage

        if (hit.damage > 0) {
            val attribution = hit.context.attribution
            val source =
                if (attribution.ownerUuid != null) {
                    playerList.firstOrNull { it.uuid == attribution.ownerUuid }
                } else if (hit.isFromPlayer) hit.resolvePlayerSource(playerList) else null
            val owner = attribution.ownerUuid ?: source?.uuid
            if (owner != null) {
                npc.recordDamage(owner, hit.damage, attribution.origin)
                for (contributor in damageContributors) {
                    contributor.onPlayerDamageNpc(
                        npc,
                        source,
                        hit.damage,
                        attribution.copy(ownerUuid = owner),
                    )
                }
            } else if (hit.isFromNpc) {
                npc.recordFinalNonPlayerDamage(hit.damage)
            }
        }

        playDefendSound(hit)

        val queueDeath = npc.hitpoints == 0 && "queue.death" !in npc.queueList
        if (queueDeath) {
            queueDeath()
        }

        npc.showHitmark(hit.hitmark)

        if (shouldShowHitpointHeadbar(npc.suppressHitpointHeadbar)) {
            val visHeadbar = npc.visHeadbar(params.headbar)
            val headbar = hit.createHeadbar(npc.hitpoints, npc.baseHitpointsLvl, visHeadbar)
            npc.showHeadbar(headbar)
        }

        npc.publishHitEvent(hit, originalHit, inputDamage, resolveKey)
        hit.dispatchImpact(npc, hit.damage)
    }

    private fun StandardNpcAccess.playDefendSound(hit: Hit) {
        val source = if (hit.isFromPlayer) hit.resolvePlayerSource(playerList) else null
        if (source == null) {
            return
        }
        val defendSound = npc.visType.paramOrNull(params.defend_sound) ?: return
        source.soundSynth(defendSound)
    }

    private fun Hit.createHeadbar(currHp: Int, maxHp: Int, headbar: HealthBarServerType): Headbar =
        InternalNpcHeadbars.createFromHitmark(hitmark, currHp, maxHp, headbar)

    private fun Npc.publishHitEvent(
        hit: Hit,
        originalHit: Hit,
        inputDamage: Int,
        resolveKey: Long,
    ) {
        val event = NpcHitEvents.Impact(this, hit, resolveKey, originalHit, inputDamage)
        eventBus.publish(event)
    }
}

internal fun shouldApplyAdminOneHit(
    enabled: Boolean,
    incomingDamage: Int,
    hitpoints: Int,
    ignoresCombat: Boolean,
    scriptedDamageGate: Boolean,
): Boolean = enabled && incomingDamage > 0 && hitpoints > 0 && !ignoresCombat && !scriptedDamageGate

internal fun shouldShowHitpointHeadbar(suppressed: Boolean): Boolean = !suppressed
