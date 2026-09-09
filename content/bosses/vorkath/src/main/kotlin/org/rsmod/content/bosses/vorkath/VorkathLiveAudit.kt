package org.rsmod.content.bosses.vorkath

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.zone.payload.MapAnim
import net.rsprot.protocol.game.outgoing.zone.payload.MapProjAnimV2
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.script.onEvent
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.attack.SpellAttackRegistry
import org.rsmod.api.spells.attack.attack
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.InvObj
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.movement.RouteRequestCoord
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Opt-in audit against the running game loop, native cache, collision and outgoing zone packets.
 * The disposable player has no account/character row, no socket and no rewards. It is removed
 * directly from the entity registry; no account login, save or logout request is ever submitted.
 * Enable only for a controlled audit with OPENRUNE_VORKATH_LIVE_AUDIT=true. This does not render
 * the client, and therefore reports protocol/engine verification separately from visual review.
 */
@OptIn(InternalApi::class)
internal class VorkathLiveAudit
@Inject
constructor(
    private val encounters: VorkathEncounterManager,
    private val diagnostics: VorkathDiagnostics,
    private val arena: VorkathWorld,
    private val registry: PlayerRegistry,
    private val instances: InstanceManager,
    private val collision: CollisionFlagMap,
    private val clock: MapClock,
    private val updates: ZoneUpdateMap,
    private val events: EventBus,
    private val spellDefinitions: MagicSpellRegistry,
    private val spellAttacks: SpellAttackRegistry,
    private val protectedAccess: ProtectedAccessLauncher,
) : PluginScript() {
    private val enabled =
        System.getenv("OPENRUNE_VORKATH_LIVE_AUDIT") == "true" ||
            System.getProperty("openrune.vorkath.audit") == "true"
    private val report = Path.of("logs", "vorkath-live-audit-latest.log")
    private var player: Player? = null
    private var started = false
    private var complete = false
    private var startTick = 0
    private var stage = "spawn-success"
    private var stageTick = 0
    private var previousState: VorkathState? = null
    private var previousNormals = 0
    private val normalTicks = mutableListOf<Int>()
    private val specials = mutableListOf<VorkathState>()
    private val rapidTicks = mutableListOf<Int>()
    private val seenProjectiles = mutableSetOf<Int>()
    private var specialTick = 0
    private var poolsSeen = false
    private var spawnLaunch = -1
    private var spawnSeen = false
    private var acidCompleted = false
    private var spawnCompleted = false
    private var attackIndex = 0
    private var attackIssued = false
    private var fireballTarget: CoordGrid? = null
    private var fireballLaunch = -1
    private var prayerImpact = -1
    private var forcedAttackSeen = false
    private var fireballImpactSeen = false
    private var walkEndpoint: CoordGrid? = null
    private val acidWalkTiles = mutableSetOf<CoordGrid>()
    private var retainedRun: VorkathArenaState? = null
    private var wakeTick = 0
    private var deathStarted = false
    private var deathBoss: Npc? = null
    private var crumbleProjectileSeen = false
    private var spawnLethalSeen = false
    private var spawnLethalTick = -1
    private var disconnectRequested = false
    private var assertions = 0

    override fun ScriptContext.startup() {
        if (!enabled) return
        onEvent<GameLifecycle.StartCycle> { guarded { if (!started) start() } }
        onEvent<GameLifecycle.LateCycle> { guarded { observe(clock.cycle - 1) } }
        onEvent<GameLifecycle.Shutdown> {
            if (started && !complete) finish(false, "Server stopped before audit completed")
        }
    }

    private fun start() {
        started = true
        Files.createDirectories(report.parent)
        Files.writeString(report, "Vorkath live engine audit ${Instant.now()}\n")
        log("Native revision 240; no renderer; synthetic accountId=0 characterId=0; no persistence")
        startTick = clock.cycle
        val audit = Player(client = AuditClient())
        player = audit
        audit.uuid = Long.MIN_VALUE + System.nanoTime()
        audit.observerUUID = audit.uuid
        audit.accountHash = 0L
        audit.userId = 0L
        audit.accountId = 0
        audit.characterId = 0
        audit.userHash = 0L
        audit.username = "vorkathaudit"
        audit.displayName = "VorkathAudit"
        audit.members = true
        audit.coords = VORKATH_OUTSIDE
        audit.slotId = requireNotNull(registry.playerList.nextFreeSlot())
        check(audit.isSlotAssigned) { "No synthetic-player slot available" }
        registry.add(audit)
        for (stat in
            listOf("attack", "strength", "defence", "hitpoints", "ranged", "magic", "prayer")) {
            audit.statMap.setBaseLevel("stat.$stat", 99)
            audit.statMap.setCurrentLevel("stat.$stat", 99)
            audit.statMap.setXP("stat.$stat", 13_034_431)
        }
        enterAndWake(audit)
        log("START slot=${audit.slotId} tick=$startTick")
    }

    private fun enterAndWake(audit: Player) {
        check(protectedAccess.launch(audit) { with(arena) { enterVorkath(32) } })
        val run = requireNotNull(encounters.auditRun(audit))
        run.rewardEligible = false
        PathingEntityCommon.telejump(
            audit,
            collision,
            CoordGrid(run.boss.coords.x + 3, run.boss.coords.z - 5, run.boss.coords.level),
        )
        check(run.boss.visType.id == 8059) { "Entry did not create native sleeping Vorkath" }
        check(encounters.poke(audit, run.boss)) { "Native sleeping interaction failed" }
        wakeTick = clock.cycle
        previousState = null
        previousNormals = 0
        normalTicks.clear()
    }

    private inner class AuditClient : Client<Any, Any> by NoopClient {
        override fun read(player: Player) {
            guarded { input(player) }
        }

        override fun write(message: Any) {
            if (!complete && message is MessageGame) log("CHAT $message")
        }
    }

    private fun input(audit: Player) {
        if (complete) return
        if (disconnectRequested) {
            // StartCycle sees the final disconnect flag; clear it before the account logout stage.
            audit.pendingLogout = false
            disconnectRequested = false
        }
        if (stage != "player-death") audit.statMap.setCurrentLevel("stat.hitpoints", 99)
        val run = encounters.auditRun(audit)
        if (
            stage == "natural" &&
                run?.state == VorkathState.ACID_SPECIAL &&
                run.acidSafeLane.isNotEmpty()
        ) {
            if (walkEndpoint == null || audit.coords == walkEndpoint)
                walkEndpoint = run.acidSafeLane.maxBy { it.chebyshevDistance(audit.coords) }
            walkAsClient(audit, requireNotNull(walkEndpoint))
        }
        when (stage) {
            "natural" ->
                if (
                    run != null &&
                        run.pendingHits.any {
                            it.kind == VorkathTileAttack.FIREBALL && it.tile == audit.coords
                        }
                ) {
                    val targetX =
                        if (audit.coords.x > run.boss.coords.x + 3) run.boss.coords.x + 3
                        else run.boss.coords.x + 6
                    walkAsClient(audit, CoordGrid(targetX, audit.coords.z, audit.coords.level))
                }
            "spawn-success" ->
                if (run?.state == VorkathState.ACTIVE) {
                    check(diagnostics.debugSpecial(audit, VorkathSpecial.ZOMBIFIED_SPAWN))
                    next("spawn-success-wait")
                }
            "spawn-success-wait" ->
                if (run?.spawn != null && !deathStarted) {
                    val spawn = requireNotNull(run.spawn)
                    val spell =
                        spellDefinitions.allSpells().single {
                            it.obj.isType("obj.39_crumble_undead")
                        }
                    log(
                        "CRUMBLE target=${spawn.visType.internalName} book=${audit.vars["varbit.spellbook"]} required=${spell.spellbook}"
                    )
                    for ((slot, requirement) in spell.objReqs.withIndex()) audit.inv[slot] =
                        InvObj(requirement.obj, requirement.count)
                    val attack = requireNotNull(spellAttacks["obj.39_crumble_undead"])
                    check(
                        protectedAccess.launch(audit) {
                            attack.attack(this, spawn, CombatAttack.Spell(null, spell, false))
                        }
                    )
                    deathStarted = true
                    stageTick = clock.cycle
                    log(
                        "Spawn success uses registered Crumble Undead spell: rune consumption, accuracy, projectile and hit queue"
                    )
                }
            "attacks" ->
                if (run?.state == VorkathState.ACTIVE && !attackIssued) {
                    val attack = VorkathStandardAttack.entries[attackIndex]
                    if (attack == VorkathStandardAttack.MELEE) {
                        PathingEntityCommon.telejump(
                            audit,
                            collision,
                            CoordGrid(
                                run.boss.coords.x + 3,
                                run.boss.coords.z - 1,
                                run.boss.coords.level,
                            ),
                        )
                    }
                    if (attack == VorkathStandardAttack.PRAYER_DRAGONFIRE) {
                        VarPlayerIntMapSetter.set(audit, "varbit.prayer_protectfrommagic", 1)
                        prayerImpact = -1
                    }
                    check(diagnostics.debugAttack(audit, attack))
                    attackIssued = true
                    prayerImpact = -1
                    fireballLaunch = -1
                    forcedAttackSeen = false
                    fireballImpactSeen = false
                    stageTick = clock.cycle
                    if (attack == VorkathStandardAttack.FIREBALL) fireballTarget = audit.coords
                    log("FORCE $attack tick=${clock.cycle} tile=${audit.coords}")
                }
            "reset-acid" ->
                if (run?.state == VorkathState.ACTIVE) {
                    check(diagnostics.debugSpecial(audit, VorkathSpecial.ACID))
                    next("reset-acid-wait")
                }
            "reset-spawn" ->
                if (run?.state == VorkathState.ACTIVE) {
                    check(diagnostics.debugSpecial(audit, VorkathSpecial.ZOMBIFIED_SPAWN))
                    next("reset-spawn-wait")
                }
            "reset-fireball" ->
                if (run?.state == VorkathState.ACTIVE) {
                    check(diagnostics.debugAttack(audit, VorkathStandardAttack.FIREBALL))
                    next("reset-fireball-wait")
                }
            "boss-death" ->
                if (run?.state == VorkathState.ACTIVE && !deathStarted) {
                    deathBoss = run.boss
                    check(diagnostics.debugKill(audit))
                    deathStarted = true
                    stageTick = clock.cycle
                }
            "player-death" ->
                if (run?.state == VorkathState.ACTIVE && !deathStarted) {
                    audit.queueHit(
                        source = run.boss,
                        delay = 1,
                        type = HitType.Typeless,
                        damage = audit.hitpoints,
                    )
                    deathStarted = true
                    stageTick = clock.cycle
                }
        }
        if (stage == "attacks" && fireballLaunch >= 0 && clock.cycle == fireballLaunch + 1) {
            val tile = requireNotNull(fireballTarget)
            walkAsClient(audit, CoordGrid(tile.x + 3, tile.z, tile.level))
        }
    }

    private fun walkAsClient(audit: Player, destination: CoordGrid) {
        if (audit.isDelayed) return
        // Match MoveGameClickHandler: cancel retaliation and use the native route finder.
        audit.clearPendingAction(events)
        audit.resetFaceEntity()
        audit.routeRequest = RouteRequestCoord(destination, clientRequest = true)
        audit.tempMoveSpeed = MoveSpeed.Walk
    }

    private fun observe(tick: Int) {
        if (!started || complete) return
        check(tick - startTick < 800) { "Audit timed out at $stage" }
        val audit = requireNotNull(player)
        val run = encounters.auditRun(audit)
        if (run != null) observePackets(run, tick)
        if (stage == "spawn-success-wait" && run?.spawn?.hitpoints == 0 && !spawnLethalSeen) {
            spawnLethalSeen = true
            spawnLethalTick = tick
        }
        if (
            stage == "attacks" &&
                attackIssued &&
                VorkathStandardAttack.entries[attackIndex] == VorkathStandardAttack.MELEE &&
                run?.boss?.pendingSequence?.id == 7951
        )
            forcedAttackSeen = true
        if (stage == "attacks" && prayerImpact >= 0 && tick <= prayerImpact) {
            val active = audit.vars["varbit.prayer_protectfrommagic"] == 1
            requireAudit(
                active == (tick < prayerImpact),
                "Prayer disable occurs at projectile impact tick=$tick due=$prayerImpact active=$active",
            )
        }
        if (stage == "natural" && run != null) observeNatural(run, tick)

        when (stage) {
            "natural" ->
                if (acidCompleted && spawnCompleted && run?.state == VorkathState.ACTIVE) {
                    requireAudit(
                        specials.size == 2 && specials[0] != specials[1],
                        "First selection followed by alternating special: $specials",
                    )
                    resetWake(audit)
                    next("attacks")
                }
            "attacks" ->
                if (attackIssued && tick >= stageTick + 7) {
                    val attack = VorkathStandardAttack.entries[attackIndex]
                    if (attack == VorkathStandardAttack.FIREBALL) {
                        requireAudit(
                            fireballLaunch >= stageTick &&
                                run?.pendingHits?.none {
                                    it.kind == VorkathTileAttack.FIREBALL &&
                                        it.impactCycle == fireballLaunch + 4
                                } == true,
                            "Fireball completed its tile impact",
                        )
                        requireAudit(
                            audit.coords != fireballTarget,
                            "Fireball target remains fixed while player moves",
                        )
                        requireAudit(
                            fireballImpactSeen,
                            "Fireball emits native landing effect at its four-tick impact",
                        )
                    }
                    requireAudit(forcedAttackSeen, "Actual NPC AI emitted forced attack $attack")
                    log("ATTACK complete $attack tick=$tick")
                    attackIndex++
                    attackIssued = false
                    resetWake(audit)
                    if (attackIndex == VorkathStandardAttack.entries.size) next("reset-acid")
                }
            "reset-acid-wait" ->
                if (run != null && tick >= stageTick + 5) {
                    requireAudit(
                        run.acidVisuals.isNotEmpty() && run.shotsFired > 0,
                        "Reset exercised live pools and barrage",
                    )
                    resetWake(audit)
                    next("reset-spawn")
                }
            "reset-spawn-wait" ->
                if (run?.spawn != null) {
                    requireAudit(run.ownsFreeze, "Reset exercised an active frozen spawn phase")
                    resetWake(audit)
                    next("reset-fireball")
                }
            "reset-fireball-wait" ->
                if (run != null && run.pendingHits.any { it.kind == VorkathTileAttack.FIREBALL }) {
                    resetWake(audit)
                    next("spawn-success")
                    deathStarted = false
                    crumbleProjectileSeen = false
                    spawnLethalSeen = false
                    spawnLethalTick = -1
                }
            "spawn-success-wait" ->
                if (deathStarted && run?.state == VorkathState.ACTIVE) {
                    requireAudit(
                        crumbleProjectileSeen &&
                            spawnLethalSeen &&
                            run.spawn == null &&
                            !run.ownsFreeze,
                        "Registered Crumble Undead projectile kills spawn and native death releases freeze and immunity",
                    )
                    requireAudit(
                        tick == spawnLethalTick + 3,
                        "Spawn death unfreezes three ticks after lethal spell impact",
                    )
                    resetWake(audit)
                    next(if (!acidCompleted && !spawnCompleted) "natural" else "boss-death")
                    deathStarted = false
                }
            "boss-death" ->
                if (deathStarted && run?.state == VorkathState.SLEEPING) {
                    requireAudit(
                        deathBoss?.isSlotAssigned == false &&
                            run.boss !== deathBoss &&
                            run.killcount == 0 &&
                            run.killTicks == 0,
                        "Native boss death despawned corpse and respawned sleeping boss without developer rewards",
                    )
                    requireClean(run, "boss death")
                    resetWake(audit)
                    next("player-death")
                    deathStarted = false
                }
            "player-death" ->
                if (deathStarted && run == null && audit.hitpoints > 0) {
                    requireAudit(
                        true,
                        "Native player death queue removed encounter and restored player",
                    )
                    enterAndWake(audit)
                    next("external-teleport")
                }
            "external-teleport" ->
                if (run?.state == VorkathState.ACTIVE) {
                    retainedRun = run
                    check(instances.leaveForExternalTeleport(audit, VORKATH_OUTSIDE, clock.cycle))
                    PathingEntityCommon.telejump(audit, collision, VORKATH_OUTSIDE)
                    requireRemoved(run, "external teleport")
                    enterAndWake(audit)
                    next("leave")
                }
            "leave" ->
                if (run?.state == VorkathState.ACTIVE) {
                    check(protectedAccess.launch(audit) { with(arena) { leaveVorkath(32) } })
                    requireRemoved(run, "arena exit")
                    enterAndWake(audit)
                    next("disconnect")
                }
            "disconnect" ->
                if (run?.state == VorkathState.ACTIVE) {
                    retainedRun = run
                    audit.pendingLogout = true
                    disconnectRequested = true
                    next("disconnect-wait")
                }
            "disconnect-wait" ->
                if (run == null) {
                    requireRemoved(requireNotNull(retainedRun), "disconnect flag")
                    enterAndWake(audit)
                    next("logout")
                }
            "logout" ->
                if (run?.state == VorkathState.ACTIVE) {
                    events.publish(SessionStateEvent.Logout(audit))
                    requireRemoved(run, "logout event")
                    requireAudit(
                        seenProjectiles.containsAll(
                            listOf(393, 395, 1470, 1471, 1477, 1479, 1481, 1482, 1483, 1484)
                        ),
                        "All ten native projectile specifications observed: $seenProjectiles",
                    )
                    finish(
                        true,
                        "$assertions assertions; natural AI, native packets, both specials, all standard attacks and cleanup paths",
                    )
                }
        }
        if (!complete && stage != "player-death")
            audit.statMap.setCurrentLevel("stat.hitpoints", 99)
    }

    private fun observeNatural(run: VorkathArenaState, tick: Int) {
        if (run.standardAttacks > previousNormals) {
            normalTicks += tick
            log("NORMAL index=${run.standardAttacks} tick=$tick anim=${run.boss.pendingSequence}")
        }
        if (run.state != previousState) {
            log("STATE ${run.state} tick=$tick")
            if (run.state == VorkathState.ACTIVE && previousState == VorkathState.AWAKENING) {
                requireAudit(tick == wakeTick + 7, "Wake activates after seven ticks")
            }
            if (
                run.state == VorkathState.ACID_SPECIAL ||
                    run.state == VorkathState.ZOMBIFIED_SPAWN_SPECIAL
            ) {
                requireAudit(
                    normalTicks.size == 6 && normalTicks.zipWithNext().all { (a, b) -> b - a == 5 },
                    "Six normal attacks at five-tick cadence: $normalTicks",
                )
                requireAudit(
                    tick == normalTicks.last() + 5,
                    "Special starts five ticks after sixth normal",
                )
                specials += run.state
                specialTick = tick
                rapidTicks.clear()
                normalTicks.clear()
                poolsSeen = false
                spawnSeen = false
                spawnLaunch = -1
            }
            if (previousState == VorkathState.ACID_SPECIAL && run.state == VorkathState.ACTIVE) {
                requireAudit(
                    rapidTicks.size == 25 &&
                        rapidTicks.first() == specialTick + 4 &&
                        rapidTicks.zipWithNext().all { (a, b) -> b - a == 1 },
                    "25 actual rapid projectiles at one-tick cadence: $rapidTicks",
                )
                requireAudit(
                    run.acidVisuals.isEmpty() && run.acidTiles.isEmpty(),
                    "Acid resolution removes all pools",
                )
                requireAudit(
                    acidWalkTiles.size >= 5,
                    "Synthetic player traversed safe lane through native collision/pathing: ${acidWalkTiles.size} tiles",
                )
                acidCompleted = true
            }
            if (
                previousState == VorkathState.ZOMBIFIED_SPAWN_SPECIAL &&
                    run.state == VorkathState.ACTIVE
            ) {
                requireAudit(
                    spawnSeen && !run.ownsFreeze && run.spawn == null,
                    "Spawn failure explosion resolved, freeze and immunity released",
                )
                spawnCompleted = true
            }
        }
        if (run.state == VorkathState.ACID_SPECIAL && run.player.coords in run.acidSafeLane)
            acidWalkTiles += run.player.coords
        if (run.state == VorkathState.ACID_SPECIAL && run.acidVisuals.isNotEmpty() && !poolsSeen) {
            poolsSeen = true
            requireAudit(tick == specialTick + 3, "Acid pools appear three ticks after cast")
            requireAudit(
                run.acidSafeLane.size >= 5 && run.acidSafeLane.none { it in run.acidTiles },
                "Walkable safe lane remains free of pools",
            )
        }
        if (run.state == VorkathState.ZOMBIFIED_SPAWN_SPECIAL && run.spawn != null && !spawnSeen) {
            spawnSeen = true
            requireAudit(
                tick == spawnLaunch + 4,
                "Zombified Spawn arrives four ticks after actual projectile",
            )
            requireAudit(
                run.spawn!!.hitpoints == 38 && run.ownsFreeze,
                "Native 38-HP spawn arrives with owned freeze",
            )
        }
        previousState = run.state
        previousNormals = run.standardAttacks
    }

    private fun observePackets(run: VorkathArenaState, tick: Int) {
        val origin = requireNotNull(instances.localCoord(run.session, RegionLocal(0, 35, 63, 0, 0)))
        for (entry in updates.updatedZones.int2ObjectEntrySet()) {
            val zoneOrigin = ZoneKey(entry.intKey).toCoords()
            if (
                zoneOrigin.level != origin.level ||
                    zoneOrigin.x !in origin.x until (origin.x + 64) ||
                    zoneOrigin.z !in origin.z until (origin.z + 64)
            )
                continue
            val zone = entry.value
            for (packet in zone) {
                if (
                    stage == "attacks" &&
                        packet is MapAnim &&
                        packet.id == VorkathAssets.FIREBALL_IMPACT &&
                        fireballLaunch >= 0 &&
                        tick == fireballLaunch + 4
                )
                    fireballImpactSeen = true
                if (packet !is MapProjAnimV2) continue
                val spawn = run.spawn
                if (
                    stage == "spawn-success-wait" &&
                        spawn != null &&
                        packet.id == "spotanim.crumbleundead_travel".asRSCM(RSCMType.SPOTANIM) &&
                        packet.targetIndex == spawn.slotId + 1
                ) {
                    crumbleProjectileSeen = true
                    log("CRUMBLE PROJECTILE tick=$tick $packet")
                    continue
                }
                if (packet.sourceIndex < 0) continue
                if (
                    stage == "attacks" &&
                        attackIssued &&
                        packet.id ==
                            VorkathProjectiles.standard(VorkathStandardAttack.entries[attackIndex])
                                ?.spot
                )
                    forcedAttackSeen = true
                seenProjectiles += packet.id
                val spec =
                    VorkathProjectiles.all.firstOrNull { it.spot == packet.id }
                        ?: error("Unexpected Vorkath projectile ${packet.id}")
                val mouth = zoneOrigin.translate(packet.xInZone, packet.zInZone)
                val target = CoordGrid(packet.end.x, packet.end.z, packet.end.level)
                val expectedEnd =
                    if (spec.distanceAdjusted) 70 + 5 * mouth.chebyshevDistance(target)
                    else spec.end
                check(
                    packet.startTime == spec.start &&
                        packet.endTime == expectedEnd &&
                        packet.angle == spec.angle &&
                        packet.progress == spec.progress &&
                        packet.startHeight == spec.startHeight &&
                        packet.endHeight == spec.endHeight
                ) {
                    "Projectile geometry mismatch: $packet"
                }
                log("PROJECTILE tick=$tick $packet")
                if (stage == "natural" && packet.id == 1482) rapidTicks += tick
                if (stage == "natural" && packet.id == 1484) spawnLaunch = tick
                if (
                    stage == "attacks" &&
                        attackIssued &&
                        VorkathStandardAttack.entries[attackIndex] ==
                            VorkathStandardAttack.PRAYER_DRAGONFIRE &&
                        packet.id == 1471 &&
                        prayerImpact < 0
                )
                    prayerImpact = tick + packet.endTime / 30
                if (
                    stage == "attacks" &&
                        attackIssued &&
                        VorkathStandardAttack.entries[attackIndex] ==
                            VorkathStandardAttack.FIREBALL &&
                        packet.id == 1481 &&
                        fireballLaunch < 0
                ) {
                    requireAudit(packet.targetIndex == 0, "Fireball uses a fixed tile target")
                    fireballLaunch = tick
                    val hit = run.pendingHits.single { it.kind == VorkathTileAttack.FIREBALL }
                    fireballTarget = hit.tile
                    requireAudit(
                        hit.impactCycle == tick + 4,
                        "Fireball server impact agrees with 120-cycle projectile",
                    )
                }
            }
        }
    }

    private fun resetWake(audit: Player) {
        val run = requireNotNull(encounters.auditRun(audit))
        check(diagnostics.debugReset(audit))
        requireClean(run, "reset")
        requireAudit(
            run.state == VorkathState.SLEEPING && run.boss.visType.id == 8059,
            "Reset restores exactly the owned sleeping boss",
        )
        check(encounters.poke(audit, run.boss))
        wakeTick = clock.cycle
    }

    private fun requireClean(run: VorkathArenaState, reason: String) {
        requireAudit(
            run.acidTiles.isEmpty() &&
                run.acidVisuals.isEmpty() &&
                run.pendingAcidPools.isEmpty() &&
                run.pendingHits.isEmpty() &&
                run.pendingFireballs.isEmpty() &&
                run.spawn == null &&
                run.retiringSpawns.isEmpty() &&
                !run.ownsFreeze,
            "$reason clears hazards, delayed impacts, spawn and freeze",
        )
    }

    private fun requireRemoved(run: VorkathArenaState, reason: String) {
        requireClean(run, reason)
        requireAudit(
            encounters.auditRun(requireNotNull(player)) == null && !run.boss.isSlotAssigned,
            "$reason removes boss and encounter membership",
        )
    }

    private fun next(value: String) {
        stage = value
        stageTick = clock.cycle
        log("STAGE $stage tick=$stageTick")
    }

    private fun requireAudit(condition: Boolean, message: String) {
        check(condition) { message }
        assertions++
        log("PASS $message")
    }

    private fun log(message: String) {
        Files.writeString(report, "$message\n", java.nio.file.StandardOpenOption.APPEND)
    }

    private inline fun guarded(block: () -> Unit) {
        if (complete) return
        try {
            block()
        } catch (failure: Throwable) {
            finish(false, "$stage tick=${clock.cycle}: ${failure.stackTraceToString()}")
        }
    }

    private fun finish(success: Boolean, message: String) {
        complete = true
        val audit = player
        if (audit != null && audit.isSlotAssigned) {
            runCatching { instances.sessionForPlayer(audit)?.let(instances::terminate) }
            registry.del(audit)
        }
        log("${if (success) "SUCCESS" else "FAILURE"} $message")
    }
}
