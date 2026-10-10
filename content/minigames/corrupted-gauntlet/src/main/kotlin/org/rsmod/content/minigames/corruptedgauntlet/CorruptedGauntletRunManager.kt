@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.content.minigames.corruptedgauntlet

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.types.InvStackType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcServerType
import dev.openrune.types.ObjectServerType
import dev.openrune.types.ProjAnimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.UUID
import kotlin.math.abs
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceSettings
import org.rsmod.api.instances.RaidLeaveResult
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.disablePrayers
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.NpcCalculatedProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

internal const val CORRUPTED_GAUNTLET_INSTANCE_KEY = "corrupted_gauntlet"

@Singleton
internal class CorruptedGauntletRunManager
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val npcs: NpcRepository,
    private val interactions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val accuracy: AccuracyFormulae,
    private val world: WorldRepository,
    private val locs: LocRepository,
    private val ui: CorruptedGauntletUi,
    private val snapshots: CorruptedGauntletSnapshotService,
    private val rewards: CorruptedGauntletRewardService,
) {
    private val log = InlineLogger()
    private val runs = mutableMapOf<PlayerUid, Run>()
    private var generation = 1L

    fun validateAssets() {
        CorruptedGauntletAssetManifest.npcIds.forEach { id ->
            requireNotNull(ServerCacheManager.getNpc(id)) {
                "Missing revision-240 Gauntlet npc $id"
            }
        }
        CorruptedGauntletAssetManifest.locIds.forEach { id ->
            requireNotNull(ServerCacheManager.getObject(id)) {
                "Missing revision-240 Gauntlet loc $id"
            }
        }
        CorruptedGauntletAssetManifest.interfaceIds.forEach { id ->
            requireNotNull(ServerCacheManager.getInterface(id)) {
                "Missing revision-240 Gauntlet interface $id"
            }
        }
        require(
            ServerCacheManager.getNpc(CorruptedGauntletConfig.Npc.HUNLLEF_MELEE_PROTECTED)
                ?.hitpoints == 1_000
        )
        rewards.verifyDefinitions()
    }

    fun eligible(player: Player): Boolean {
        val normal =
            requireNotNull(
                ServerCacheManager.getVarp(CorruptedGauntletConfig.NORMAL_COMPLETION_VARP)
            )
        return player.vars[normal] > 0
    }

    fun enter(
        player: Player,
        seed: Long = random.of(Int.MAX_VALUE).toLong(),
        bypass: Boolean = false,
    ): Boolean {
        if (player.hitpoints <= 0 || player.isAccessProtected) {
            player.mes("Finish what you are doing before entering the Corrupted Gauntlet.")
            return false
        }
        if (!bypass && !eligible(player)) {
            player.mes(
                "You must complete the regular Gauntlet before entering the Corrupted Gauntlet."
            )
            return false
        }
        if (runs.containsKey(player.uid) || instances.sessionForPlayer(player) != null) {
            player.mes("You already have an active instance.")
            return false
        }
        if (rewards.hasPending(player)) {
            player.mes(
                "Search the nearby reward chest before beginning another Corrupted Gauntlet."
            )
            return false
        }
        if (snapshots.blockedByAnotherSandbox(player) || snapshots.hasUnresolved(player)) {
            player.mes("Your carried items are already protected by another activity.")
            return false
        }

        val runId = UUID.randomUUID()
        val layout = CorruptedGauntletLayoutGenerator.generate(seed)
        val initialHunllef = CorruptedGauntletRules.initialHunllefState(seed)
        val bossType = hunllefTypeFor(initialHunllef.protectionStyle)
        val settings =
            InstanceSettings(
                    maxPlayers = 1,
                    destroyWhenEmpty = true,
                    bossNpc = listOf(bossType),
                    bossName = "Corrupted Hunllef",
                    recommendedCombat = 80..126,
                    teamSize = 1,
                    description = "Private Corrupted Gauntlet",
                    recordsEligible = true,
                )
                .withArea(CorruptedGauntletInstanceLayout.area(layout), settingsRowId = 0)
        val result =
            instances.create(
                owner = player,
                key = CORRUPTED_GAUNTLET_INSTANCE_KEY,
                spec = settings,
                access = InstanceAccess.Private,
                currentTick = clock.cycle,
            )
        if (result !is InstanceManager.Result.Created) {
            player.mes(
                (result as? InstanceManager.Result.Failed)?.reason ?: "The Gauntlet is unavailable."
            )
            return false
        }
        if (!snapshots.capture(player, runId)) {
            instances.leave(player, result.session, clock.cycle)
            player.mes("Your carried items could not be stored safely.")
            return false
        }

        val originalCoord = player.coords
        var spawnedBoss: Npc? = null
        return runCatching {
                PathingEntityCommon.telejump(player, collision, result.enter)
                instances.finalizeEntry(player, result.session, clock.cycle)
                val origin = CorruptedGauntletInstanceLayout.originFromEntry(layout, result.enter)
                val boss = spawnBoss(player, result.session, origin, initialHunllef.protectionStyle)
                spawnedBoss = boss
                val run =
                    Run(
                        id = runId,
                        player = player,
                        session = result.session,
                        layout = layout,
                        origin = origin,
                        boss = boss,
                        generation = generation++,
                        seed = seed,
                        startCycle = clock.cycle,
                        hunllef = initialHunllef,
                    )
                run.npcs += boss
                run.revealed += layout.start
                run.revealed += layout.boss
                run.npcData[boss] =
                    MonsterData(CorruptedGauntletMonsterTier.DEMI_BOSS, null, null, boss = true)
                runs[player.uid] = run
                giveStartingKit(player)
                populate(run)
                ui.open(player, layout)
                player.mes("You enter the Corrupted Gauntlet.")
                player.mes(
                    "You have 7 minutes and 30 seconds to prepare for the Corrupted Hunllef."
                )
                true
            }
            .getOrElse { error ->
                runs.remove(player.uid)
                spawnedBoss?.takeIf { it.isSlotAssigned }?.let { npcs.del(it, Int.MAX_VALUE) }
                instances.leave(player, result.session, clock.cycle)
                val restored = snapshots.restore(player)
                PathingEntityCommon.telejump(player, collision, originalCoord)
                log.error(error) {
                    "Corrupted Gauntlet entry rolled back for $player (run=$runId)."
                }
                player.mes(
                    if (restored)
                        "The Corrupted Gauntlet could not be created; your items were restored."
                    else
                        "The Corrupted Gauntlet could not be created. Your items are protected for login recovery."
                )
                false
            }
    }

    fun tickAll() {
        runs.values.toList().forEach(::tick)
    }

    private fun tick(run: Run) {
        if (runs[run.player.uid] !== run || !run.player.isSlotAssigned) return
        val currentRoom = CorruptedGauntletInstanceLayout.roomAt(run.origin, run.player.coords)
        if (currentRoom != null && currentRoom != run.currentRoom) {
            run.currentRoom = currentRoom
            run.revealed += currentRoom
            ui.revealRoom(run.player, currentRoom)
            ui.setCurrentRoom(run.player, currentRoom)
        }
        when (run.stage) {
            CorruptedGauntletStage.PREPARATION -> {
                val elapsed = clock.cycle - run.startCycle
                val remaining = CorruptedGauntletRules.remainingPreparationTicks(elapsed)
                ui.updateTimer(run.player, remaining, boss = false)
                activateRoomMonsters(run, currentRoom)
                if (remaining == 0) startBoss(run.player, forced = true)
            }
            CorruptedGauntletStage.HUNLLEF -> {
                ui.updateTimer(run.player, clock.cycle - run.bossStartCycle, boss = true)
                processHunllefStyleSwitch(run)
                processFloors(run)
                processTornadoes(run)
                processTrample(run)
                keepHunllefAggressive(run)
            }
            CorruptedGauntletStage.COMPLETED -> retryCompletedResult(run)
            CorruptedGauntletStage.FAILED -> Unit
        }
    }

    fun lightNode(player: Player, clicked: BoundLocInfo): Boolean {
        val run = runs[player.uid] ?: return false
        if (run.stage != CorruptedGauntletStage.PREPARATION) return false
        val sceptreId = requireItem(CorruptedGauntletConfig.Obj.SCEPTRE).id
        if (
            player.righthand?.id != sceptreId &&
                countItem(player, CorruptedGauntletConfig.Obj.SCEPTRE) == 0
        ) {
            player.mes("You need the corrupted sceptre to light the nodes.")
            return false
        }
        val current = roomFor(run, player.coords) ?: return false
        val target =
            CorruptedGauntletNavigationRules.destination(
                current = current,
                nodeId = clicked.id,
                angle = clicked.angle,
            )
        if (target == null) {
            player.mes("There is no chamber beyond these nodes.")
            return false
        }
        if (target == run.layout.boss) {
            player.mes("The Corrupted Hunllef's chamber must be entered through its barrier.")
            return false
        }
        lightCorridorNodes(clicked)
        val firstReveal = run.revealed.add(target)
        ui.revealRoom(player, target)
        player.mes(
            if (firstReveal) "You light the nodes in the corridor to help guide the way."
            else "The corridor nodes are already lit."
        )
        return true
    }

    private fun lightCorridorNodes(clicked: BoundLocInfo) {
        val candidates = sequence {
            for (xOffset in -2..2) {
                for (zOffset in -2..2) {
                    yieldAll(locs.findAll(clicked.coords.translate(xOffset, zOffset)))
                }
            }
        }
        candidates
            .filter { it.id in CorruptedGauntletConfig.Loc.unlitNodes }
            .filter { it.angle == clicked.angle }
            .toList()
            .forEach { node -> locs.change(node, requireObject(node.id + 2), Int.MAX_VALUE) }
    }

    fun startBoss(player: Player, forced: Boolean = false): Boolean {
        val run = runs[player.uid] ?: return false
        if (run.stage != CorruptedGauntletStage.PREPARATION) return true
        run.stage = CorruptedGauntletStage.HUNLLEF
        run.bossStartCycle = clock.cycle
        run.nextFloorCycle = clock.cycle + 8
        run.boss.movementLocked = false
        run.boss.moveRestrict = MoveRestrict.Normal
        run.boss.showAllOps()
        PathingEntityCommon.telejump(
            player,
            collision,
            CorruptedGauntletInstanceLayout.bossEntry(run.origin),
        )
        ui.beginBoss(player)
        run.boss.apPlayer2(player, interactions)
        player.mes(
            if (forced) "Time is up. You are pulled into the Hunllef's chamber!"
            else "The Corrupted Hunllef awakens."
        )
        return true
    }

    fun attack(access: StandardNpcAccess, target: Player) {
        val run = runs[target.uid] ?: return access.resetMode()
        val data = run.npcData[access.npc] ?: return access.resetMode()
        if (!access.npc.isSpawnOwnedBy(target) || access.actionDelay > clock.cycle) return
        if (data.boss) {
            attackHunllef(access, run)
        } else if (
            run.stage == CorruptedGauntletStage.PREPARATION &&
                roomFor(run, access.npc.coords) == run.currentRoom
        ) {
            attackMonster(access, target, data)
        } else {
            access.resetMode()
        }
    }

    private fun attackHunllef(access: StandardNpcAccess, run: Run) {
        if (run.stage != CorruptedGauntletStage.HUNLLEF) return access.resetMode()
        if (CorruptedGauntletRules.tornadoesDue(run.hunllef)) {
            access.npc.facePlayer(run.player)
            access.npc.anim(CorruptedGauntletConfig.Sequence.HUNLLEF_TORNADO_CAST)
            run.pendingTornadoCycle = clock.cycle + 3
            val previous = run.hunllef.outgoingStyle
            run.hunllef = CorruptedGauntletRules.afterTornadoCast(run.hunllef)
            scheduleHunllefStyleSwitch(run, previous)
            access.actionDelay = clock.cycle + CorruptedGauntletConfig.HUNLLEF_ATTACK_INTERVAL
            return
        }
        val style = run.hunllef.outgoingStyle
        access.npc.facePlayer(run.player)
        access.npc.anim(CorruptedGauntletConfig.Sequence.HUNLLEF_ATTACK)
        val projectileId =
            if (style == CorruptedGauntletCombatStyle.MAGIC && random.of(100) < 15) {
                CorruptedGauntletConfig.Projectile.PRAYER_DISABLE
            } else if (style == CorruptedGauntletCombatStyle.MAGIC) {
                CorruptedGauntletConfig.Projectile.HUNLLEF_MAGIC
            } else {
                CorruptedGauntletConfig.Projectile.HUNLLEF_RANGED
            }
        val projectile =
            ProjAnim.fromNpcToPlayer(access.npc, run.player, projectileId, HUNLLEF_PROJECTILE)
        world.projAnim(projectile)
        val accurate =
            if (style == CorruptedGauntletCombatStyle.MAGIC)
                accuracy.rollMagicAccuracy(access.npc, run.player, random)
            else accuracy.rollRangedAccuracy(access.npc, run.player, random)
        run.player.queueHit(
            source = access.npc,
            delay = projectile.serverCycles.coerceAtLeast(1),
            type =
                if (style == CorruptedGauntletCombatStyle.MAGIC) HitType.Magic else HitType.Ranged,
            damage = if (accurate) random.of(69) else 0,
            modifier =
                object : PlayerHitModifier {
                    override fun HitBuilder.modify(target: Player) {
                        if (damage > 0 && protectedFrom(target, style)) {
                            damage =
                                random.of(
                                    CorruptedGauntletRules.correctPrayerMaximumHit(
                                        armourTierSum(target)
                                    ) + 1
                                )
                        }
                        if (projectileId == CorruptedGauntletConfig.Projectile.PRAYER_DISABLE) {
                            target.disablePrayers()
                            target.mes("Your prayers have been disabled!")
                        }
                        NpcBypassProtectionPrayerPlayerHitModifier.run { modify(target) }
                    }
                },
        )
        val previous = run.hunllef.outgoingStyle
        run.hunllef = CorruptedGauntletRules.afterHunllefAttack(run.hunllef)
        scheduleHunllefStyleSwitch(run, previous)
        access.actionDelay = clock.cycle + CorruptedGauntletConfig.HUNLLEF_ATTACK_INTERVAL
    }

    private fun attackMonster(access: StandardNpcAccess, target: Player, data: MonsterData) {
        val style = data.style ?: CorruptedGauntletCombatStyle.MELEE
        access.npc.facePlayer(target)
        access.npc.anim(data.animation ?: 4933)
        val maxHit =
            when (data.tier) {
                CorruptedGauntletMonsterTier.WEAK -> 14
                CorruptedGauntletMonsterTier.STRONG -> 22
                CorruptedGauntletMonsterTier.DEMI_BOSS -> 28
            }
        if (style == CorruptedGauntletCombatStyle.MELEE) {
            val accurate =
                accuracy.rollMeleeAccuracy(access.npc, target, attackType = null, random = random)
            target.queueHit(
                access.npc,
                delay = 1,
                type = HitType.Melee,
                damage = if (accurate) random.of(maxHit + 1) else 0,
                modifier =
                    NpcCalculatedProtectionPrayerPlayerHitModifier(
                        protectedFrom(target, style),
                        100,
                    ),
            )
        } else {
            val projectileId = if (style == CorruptedGauntletCombatStyle.MAGIC) 1_702 else 1_606
            val projectile =
                ProjAnim.fromNpcToPlayer(access.npc, target, projectileId, MONSTER_PROJECTILE)
            world.projAnim(projectile)
            val accurate =
                if (style == CorruptedGauntletCombatStyle.MAGIC)
                    accuracy.rollMagicAccuracy(access.npc, target, random)
                else accuracy.rollRangedAccuracy(access.npc, target, random)
            target.queueHit(
                access.npc,
                delay = projectile.serverCycles.coerceAtLeast(1),
                type =
                    if (style == CorruptedGauntletCombatStyle.MAGIC) HitType.Magic
                    else HitType.Ranged,
                damage = if (accurate) random.of(maxHit + 1) else 0,
                modifier =
                    NpcCalculatedProtectionPrayerPlayerHitModifier(
                        protectedFrom(target, style),
                        100,
                    ),
            )
        }
        access.actionDelay = clock.cycle + 4
    }

    fun modifyNpcHit(player: Player, npc: Npc, hit: HitBuilder) {
        val run =
            runs[player.uid]
                ?: run {
                    hit.damage = 0
                    return
                }
        val data =
            run.npcData[npc]
                ?: run {
                    hit.damage = 0
                    return
                }
        if (!npc.isSpawnOwnedBy(player)) {
            hit.damage = 0
            return
        }
        if (!data.boss) return
        if (run.stage != CorruptedGauntletStage.HUNLLEF) {
            hit.damage = 0
            return
        }
        val style = hit.type.toGauntletStyle() ?: return
        if (style == run.hunllef.protectionStyle) {
            hit.damage = 0
            return
        }
        run.hunllef = CorruptedGauntletRules.afterValidPlayerAttack(run.hunllef, style)
        val desiredType = hunllefTypeFor(run.hunllef.protectionStyle)
        if (npc.visType.id != desiredType.id) npc.transmog(desiredType, Int.MAX_VALUE)
        run.damageDealt += hit.damage.coerceAtLeast(0)
    }

    fun beforeNpcDeath(npc: Npc): Boolean {
        val run = runs.values.firstOrNull { npc in it.npcs } ?: return false
        val data = run.npcData[npc] ?: return false
        if (data.boss) {
            run.stage = CorruptedGauntletStage.COMPLETED
            run.bossKilled = true
            run.generation++
            clearHazards(run)
        } else {
            rewardMonster(run, data)
            run.npcData.remove(npc)
            run.npcs.remove(npc)
        }
        return true
    }

    fun afterNpcDeath(npc: Npc) {
        val run = runs.values.firstOrNull { it.boss === npc } ?: return
        if (!run.bossKilled) return
        retryCompletedResult(run)
    }

    private fun retryCompletedResult(run: Run) {
        if (runs[run.player.uid] !== run || clock.cycle < run.nextResultRetryCycle) return
        val prep = (run.bossStartCycle - run.startCycle).coerceAtLeast(0)
        val kill = (clock.cycle - run.bossStartCycle).coerceAtLeast(0)
        val pending = rewards.prepareSuccess(run.player, run.id, prep, kill)
        if (pending?.runId != run.id) {
            run.nextResultRetryCycle = clock.cycle + 10
            if (!run.resultFailureNotified) {
                run.resultFailureNotified = true
                run.player.mes(
                    "Your reward could not be saved safely yet. The Gauntlet will retry it."
                )
            }
            return
        }
        syncCompletionVarp(run.player)
        finishRun(run, successful = true, teleport = true, external = false)
    }

    fun harvest(player: Player, clicked: BoundLocInfo): CorruptedGauntletResource? {
        val run = runs[player.uid] ?: return null
        if (run.stage != CorruptedGauntletStage.PREPARATION) return null
        val node = run.nodes[clicked.coords.packed] ?: return null
        if (node.remaining <= 0 || node.loc.entity.id != clicked.entity.id) return null
        val requiredTool =
            when (node.resource) {
                CorruptedGauntletResource.ORE -> CorruptedGauntletConfig.Obj.PICKAXE
                CorruptedGauntletResource.BARK -> CorruptedGauntletConfig.Obj.AXE
                CorruptedGauntletResource.FISH -> CorruptedGauntletConfig.Obj.HARPOON
                CorruptedGauntletResource.GRYM,
                CorruptedGauntletResource.LINUM -> null
            }
        if (requiredTool != null && countItem(player, requiredTool) == 0) {
            player.mes("You need the appropriate Corrupted Gauntlet tool for that resource.")
            return null
        }
        if (!addItem(player, node.resource.itemId, 1)) {
            player.mes("You do not have enough inventory space.")
            return null
        }
        node.remaining--
        if (random.of(8) == 0) addItem(player, CorruptedGauntletConfig.Obj.SHARDS, 1 + random.of(3))
        if (node.remaining == 0) {
            locs.del(node.loc, Int.MAX_VALUE)
            node.loc =
                locs.add(
                    node.loc.coords,
                    requireObject(node.resource.depletedNodeId),
                    Int.MAX_VALUE,
                    node.loc.angle,
                    node.loc.shape,
                )
        }
        return node.resource
    }

    fun cookAll(player: Player): Int {
        if (!isPreparing(player)) return 0
        val count = countItem(player, CorruptedGauntletConfig.Obj.RAW_PADDLEFISH)
        if (count == 0) return 0
        removeItem(player, CorruptedGauntletConfig.Obj.RAW_PADDLEFISH, count)
        addItem(player, CorruptedGauntletConfig.Obj.PADDLEFISH, count)
        return count
    }

    fun fillVials(player: Player): Int {
        if (!isPreparing(player)) return 0
        val count = countItem(player, CorruptedGauntletConfig.Obj.VIAL)
        if (count == 0) return 0
        removeItem(player, CorruptedGauntletConfig.Obj.VIAL, count)
        addItem(player, CorruptedGauntletConfig.Obj.VIAL_OF_WATER, count)
        return count
    }

    fun grindShards(player: Player): Boolean {
        if (!isPreparing(player) || countItem(player, CorruptedGauntletConfig.Obj.SHARDS) < 10)
            return false
        removeItem(player, CorruptedGauntletConfig.Obj.SHARDS, 10)
        return addItem(player, CorruptedGauntletConfig.Obj.DUST, 10)
    }

    fun mixPotion(player: Player, secondStep: Boolean): Boolean {
        if (!isPreparing(player)) return false
        val from =
            if (secondStep) CorruptedGauntletConfig.Obj.UNFINISHED_POTION
            else CorruptedGauntletConfig.Obj.VIAL_OF_WATER
        val ingredient =
            if (secondStep) CorruptedGauntletConfig.Obj.DUST
            else CorruptedGauntletConfig.Obj.GRYM_LEAF
        val to =
            if (secondStep) CorruptedGauntletConfig.Obj.POTION_THREE_DOSE
            else CorruptedGauntletConfig.Obj.UNFINISHED_POTION
        if (countItem(player, from) == 0 || countItem(player, ingredient) == 0) return false
        removeItem(player, from, 1)
        removeItem(player, ingredient, 1)
        return addItem(player, to, 1)
    }

    fun recoverMissingTools(player: Player): CorruptedGauntletToolRecovery {
        if (!isPreparing(player)) return CorruptedGauntletToolRecovery(0, 0)
        val carried = buildSet {
            player.inv.filterNotNull().forEach { add(it.id) }
            player.worn.filterNotNull().forEach { add(it.id) }
        }
        val missing = CorruptedGauntletConsumableRules.missingTools(carried)
        var recovered = 0
        missing.forEach { itemId -> if (addItem(player, itemId, 1)) recovered++ }
        return CorruptedGauntletToolRecovery(
            recovered = recovered,
            unavailable = missing.size - recovered,
        )
    }

    fun consumePaddlefish(player: Player, slot: Int, itemId: Int): Boolean {
        if (!isActive(player) || itemId != CorruptedGauntletConfig.Obj.PADDLEFISH) return false
        if (slot !in 0 until player.inv.size) return false
        val held = player.inv[slot] ?: return false
        if (held.id != requireItem(itemId).id) return false
        player.inv[slot] = null
        return true
    }

    fun consumeEgniol(player: Player, slot: Int, itemId: Int): Int? {
        if (!isActive(player)) return null
        val dose = CorruptedGauntletConsumableRules.egniolDose(itemId) ?: return null
        val replacement = CorruptedGauntletConsumableRules.nextEgniolItem(itemId) ?: return null
        if (slot !in 0 until player.inv.size) return null
        val held = player.inv[slot] ?: return null
        if (held.id != requireItem(itemId).id) return null
        player.inv[slot] = InvObj(requireItem(replacement), 1, held.vars)
        return dose - 1
    }

    fun craft(player: Player, outputItemId: Int): Boolean {
        if (!isPreparing(player)) return false
        val recipe = CorruptedGauntletCraftingRules.byOutput[outputItemId] ?: return false
        val counts = inventoryCounts(player)
        if (!CorruptedGauntletCraftingRules.canCraft(recipe, counts)) {
            player.mes("You do not have the required materials and corrupted shards.")
            return false
        }
        if (recipe.shardCost > 0)
            removeItem(player, CorruptedGauntletConfig.Obj.SHARDS, recipe.shardCost)
        recipe.ingredients.forEach { removeItem(player, it.itemId, it.amount) }
        check(addItem(player, recipe.outputItemId, 1))
        player.mes("You sing the materials into a corrupted item.")
        return true
    }

    fun teleportToStart(player: Player): Boolean {
        val run = runs[player.uid] ?: return false
        if (run.stage != CorruptedGauntletStage.PREPARATION) return false
        val base = CorruptedGauntletInstanceLayout.roomBase(run.origin, run.layout.start)
        PathingEntityCommon.telejump(player, collision, base.translate(5, 5))
        return true
    }

    fun teleportToRewardChest(player: Player): Boolean {
        val run = runs[player.uid]
        if (run != null) {
            if (run.bossKilled) {
                retryCompletedResult(run)
                return runs[player.uid] == null
            }
            return leave(player)
        }
        if (instances.sessionForPlayer(player) != null) {
            player.mes("Leave your current instance before going to the Gauntlet lobby.")
            return false
        }
        PathingEntityCommon.telejump(
            player,
            collision,
            CoordGrid(
                CorruptedGauntletConfig.LOBBY_X,
                CorruptedGauntletConfig.LOBBY_Z,
                CorruptedGauntletConfig.LOBBY_LEVEL,
            ),
        )
        return true
    }

    fun claim(player: Player): Boolean {
        val claimed = rewards.claim(player) ?: return false
        ui.setRewardAvailable(player, false)
        return claimed.isNotEmpty()
    }

    fun isPreparing(player: Player): Boolean =
        runs[player.uid]?.stage == CorruptedGauntletStage.PREPARATION

    fun isActive(player: Player): Boolean = runs.containsKey(player.uid)

    fun isEncounterNpc(npc: Npc): Boolean = runs.values.any { npc in it.npcs }

    fun isParticipant(player: Player, npc: Npc): Boolean =
        runs[player.uid]?.let { npc in it.npcs && npc.isSpawnOwnedBy(player) } == true

    fun attackDenial(player: Player, npc: Npc): String? {
        val run = runs[player.uid] ?: return "You do not have an active Corrupted Gauntlet."
        val data = run.npcData[npc] ?: return "That creature belongs to another Gauntlet."
        if (data.boss && run.stage != CorruptedGauntletStage.HUNLLEF)
            return "The Hunllef has not awakened."
        return null
    }

    fun leave(player: Player): Boolean {
        val run = runs[player.uid] ?: return false
        failAndFinish(run, teleport = true, external = false)
        return true
    }

    fun leaveFromLogoutTab(player: Player, expected: InstanceId): RaidLeaveResult {
        val run = runs[player.uid] ?: return RaidLeaveResult.Unhandled
        if (run.session.id != expected) return RaidLeaveResult.Unhandled
        return if (leave(player)) RaidLeaveResult.Left else RaidLeaveResult.Failed
    }

    fun membershipEnded(player: Player, instanceId: InstanceId) {
        val run = runs[player.uid] ?: return
        if (run.session.id != instanceId) return
        failAndFinish(run, teleport = false, external = true)
    }

    fun externalTeleport(player: Player, instanceId: InstanceId) =
        membershipEnded(player, instanceId)

    fun logout(player: Player) {
        val run = runs[player.uid] ?: return
        failAndFinish(run, teleport = false, external = true)
        instances.handleLogout(player, clock.cycle)
    }

    fun playerDied(player: Player) {
        val run = runs[player.uid] ?: return
        failAndFinish(run, teleport = false, external = true, died = true)
    }

    fun onLogin(player: Player) {
        syncCompletionVarp(player)
        ui.setRewardAvailable(player, rewards.hasPending(player))
        if (!snapshots.hasUnresolved(player)) return
        if (!snapshots.restore(player)) {
            player.mes(
                "Your Corrupted Gauntlet items are protected, but recovery could not be saved. Please reconnect to retry."
            )
            return
        }
        PathingEntityCommon.telejump(
            player,
            collision,
            CoordGrid(
                CorruptedGauntletConfig.LOBBY_X,
                CorruptedGauntletConfig.LOBBY_Z,
                CorruptedGauntletConfig.LOBBY_LEVEL,
            ),
        )
        ui.close(player)
        player.mes("Your abandoned Corrupted Gauntlet was cleaned up and your items were restored.")
    }

    fun status(player: Player): String {
        val run =
            runs[player.uid]
                ?: return "No active Corrupted Gauntlet. Pending reward=${rewards.hasPending(player)}"
        return "CG seed=${run.seed} stage=${run.stage} room=${run.currentRoom} prep=${clock.cycle - run.startCycle} bossHp=${run.boss.hitpoints} tornadoes=${run.tornadoes.size} hazards=${run.floors.size}"
    }

    private fun failAndFinish(
        run: Run,
        teleport: Boolean,
        external: Boolean,
        died: Boolean = false,
    ) {
        if (runs[run.player.uid] !== run) return
        run.stage = CorruptedGauntletStage.FAILED
        val prep =
            ((run.bossStartCycle.takeIf { it > 0 } ?: clock.cycle) - run.startCycle).coerceAtLeast(
                0
            )
        val kill =
            if (run.bossStartCycle > 0) (clock.cycle - run.bossStartCycle).coerceAtLeast(0) else 0
        val pending =
            rewards.prepareFailure(run.player, run.id, run.damageDealt > 0, prep, kill, died)
        if (pending?.runId != run.id) {
            run.player.mes(
                "Your consolation reward could not be saved; no temporary items were retained."
            )
        }
        finishRun(run, successful = false, teleport = teleport, external = external)
    }

    private fun finishRun(run: Run, successful: Boolean, teleport: Boolean, external: Boolean) {
        run.generation++
        clearRun(run)
        runs.remove(run.player.uid)
        val restored = snapshots.restore(run.player)
        ui.close(run.player)
        ui.setRewardAvailable(run.player, rewards.hasPending(run.player))
        if (!external) {
            val exit = instances.leave(run.player, run.session, clock.cycle)
            if (teleport && run.player.isSlotAssigned)
                PathingEntityCommon.telejump(run.player, collision, exit)
        }
        if (!restored) {
            run.player.mes(
                "Your temporary Gauntlet state was cleared, but your stored items need login recovery."
            )
            return
        }
        run.player.mes(
            if (successful) "Corrupted Hunllef has been defeated. Search the reward chest."
            else "Your Corrupted Gauntlet attempt has ended."
        )
    }

    private fun clearRun(run: Run) {
        run.player.combatClearQueue()
        clearHazards(run)
        run.nodes.values.forEach { locs.del(it.loc, Int.MAX_VALUE) }
        run.nodes.clear()
        run.npcs.toList().forEach { npc ->
            npc.clearInteraction()
            if (npc.isSlotAssigned) npcs.del(npc, Int.MAX_VALUE)
        }
        run.npcs.clear()
        run.npcData.clear()
    }

    private fun populate(run: Run) {
        run.layout.rooms.forEach { room ->
            when (room.kind) {
                CorruptedGauntletRoomKind.START,
                CorruptedGauntletRoomKind.BOSS -> Unit
                CorruptedGauntletRoomKind.RESOURCE ->
                    spawnResources(run, room, 2 + roomRoll(run, room, 0, 4))
                CorruptedGauntletRoomKind.COMBAT -> {
                    spawnResources(run, room, roomRoll(run, room, 1, 3))
                    spawnCombatRoom(run, room)
                }
                CorruptedGauntletRoomKind.DEMI_BOSS -> {
                    spawnResources(run, room, roomRoll(run, room, 2, 3))
                    spawnDemiBoss(run, room)
                }
            }
        }
    }

    private fun spawnResources(run: Run, room: CorruptedGauntletRoom, requestedCount: Int) {
        val resources =
            if (room.guaranteedFishNodes > 0)
                List(room.guaranteedFishNodes) { CorruptedGauntletResource.FISH }
            else {
                val entries = CorruptedGauntletResource.entries
                List(requestedCount.coerceIn(0, 5)) { index ->
                    entries[
                        Math.floorMod(
                            (run.seed + room.grid.x * 31L + room.grid.z * 17L + index).toInt(),
                            entries.size,
                        )]
                }
            }
        val base = CorruptedGauntletInstanceLayout.roomBase(run.origin, room.grid)
        resources.forEachIndexed { index, resource ->
            val tile = findRoomSpawn(run, base, index, footprint = 1) ?: return@forEachIndexed
            val loc =
                locs.add(
                    tile,
                    requireObject(resource.nodeId),
                    Int.MAX_VALUE,
                    LocAngle.North,
                    LocShape.CentrepieceStraight,
                )
            run.nodes[tile.packed] = ResourceNode(resource, resource.yield, loc)
        }
    }

    private fun spawnCombatRoom(run: Run, room: CorruptedGauntletRoom) {
        val outer = room.grid.x in setOf(0, 6) || room.grid.z in setOf(0, 6)
        val ids = if (outer) intArrayOf(9_043, 9_044, 9_045) else intArrayOf(9_040, 9_041, 9_042)
        repeat(2) { index ->
            val id =
                ids[
                    Math.floorMod(
                        (run.seed + room.grid.x * 13 + room.grid.z * 29 + index).toInt(),
                        ids.size,
                    )]
            val tier =
                if (outer) CorruptedGauntletMonsterTier.STRONG
                else CorruptedGauntletMonsterTier.WEAK
            spawnMonster(run, room, id, tier, index)
        }
    }

    private fun spawnDemiBoss(run: Run, room: CorruptedGauntletRoom) {
        val boss = checkNotNull(room.demiBoss)
        val id =
            when (boss) {
                CorruptedGauntletDemiBoss.BEAR -> 9_046
                CorruptedGauntletDemiBoss.DRAGON -> 9_047
                CorruptedGauntletDemiBoss.DARK_BEAST -> 9_048
            }
        spawnMonster(run, room, id, CorruptedGauntletMonsterTier.DEMI_BOSS, 0, boss)
    }

    private fun spawnMonster(
        run: Run,
        room: CorruptedGauntletRoom,
        id: Int,
        tier: CorruptedGauntletMonsterTier,
        index: Int,
        demi: CorruptedGauntletDemiBoss? = null,
    ) {
        val base = CorruptedGauntletInstanceLayout.roomBase(run.origin, room.grid)
        val type = requireNpc(id)
        val tile = findRoomSpawn(run, base, index + 7, footprint = type.size) ?: return
        val npc = Npc(type, tile)
        npc.respawns = false
        npc.movementLocked = false
        npc.apRangeOverride = if (id == 9_047 || id == 9_048) 8 else 1
        npc.maxOpRangeOverride = 16
        npc.assignSpawnOwner(run.player, clock.cycle)
        npcs.add(npc, Int.MAX_VALUE)
        instances.attachNpc(run.session.id, npc)
        val data = monsterData(id, tier, demi)
        run.npcs += npc
        run.npcData[npc] = data
    }

    private fun monsterData(
        id: Int,
        tier: CorruptedGauntletMonsterTier,
        demi: CorruptedGauntletDemiBoss?,
    ): MonsterData =
        when (id) {
            9_047 -> MonsterData(tier, demi, CorruptedGauntletCombatStyle.MAGIC, animation = 84)
            9_048 -> MonsterData(tier, demi, CorruptedGauntletCombatStyle.RANGED, animation = 2_731)
            9_046 -> MonsterData(tier, demi, CorruptedGauntletCombatStyle.MELEE, animation = 4_925)
            9_040 -> MonsterData(tier, demi, CorruptedGauntletCombatStyle.MELEE, animation = 4_933)
            9_041 -> MonsterData(tier, demi, CorruptedGauntletCombatStyle.RANGED, animation = 8_339)
            9_042 -> MonsterData(tier, demi, CorruptedGauntletCombatStyle.MAGIC, animation = 4_915)
            9_043 -> MonsterData(tier, demi, CorruptedGauntletCombatStyle.MELEE, animation = 6_376)
            9_044 -> MonsterData(tier, demi, CorruptedGauntletCombatStyle.RANGED, animation = 6_254)
            9_045 -> MonsterData(tier, demi, CorruptedGauntletCombatStyle.MAGIC, animation = 6_579)
            else -> MonsterData(tier, demi, CorruptedGauntletCombatStyle.MELEE, animation = 4_933)
        }

    private fun activateRoomMonsters(run: Run, current: CorruptedGauntletGridPoint?) {
        run.npcData.forEach { (npc, data) ->
            if (data.boss || !npc.isSlotAssigned || npc.hitpoints <= 0) return@forEach
            if (current != null && roomFor(run, npc.coords) == current) {
                if (npc.mode == null) npc.apPlayer2(run.player, interactions)
            } else {
                npc.clearInteraction()
            }
        }
    }

    private fun rewardMonster(run: Run, data: MonsterData) {
        val killsBefore = run.killsByTier.getOrDefault(data.tier, 0)
        run.killsByTier[data.tier] = killsBefore + 1
        val range = CorruptedGauntletRules.monsterShardRange(data.tier)
        addItem(
            run.player,
            CorruptedGauntletConfig.Obj.SHARDS,
            range.first + random.of(range.last - range.first + 1),
        )
        val ownsFrame = countItem(run.player, CorruptedGauntletConfig.Obj.WEAPON_FRAME) > 0
        if (
            CorruptedGauntletRules.guaranteesWeaponFrame(data.tier, killsBefore, ownsFrame) ||
                random.of(4) == 0
        ) {
            addItem(run.player, CorruptedGauntletConfig.Obj.WEAPON_FRAME, 1)
        }
        when (random.of(8)) {
            0 -> addItem(run.player, CorruptedGauntletConfig.Obj.RAW_PADDLEFISH, 1 + random.of(3))
            1 -> addItem(run.player, CorruptedGauntletConfig.Obj.GRYM_LEAF, 1)
            2 -> addItem(run.player, CorruptedGauntletConfig.Obj.TELEPORT_CRYSTAL, 1)
        }
        data.demi?.let { demi ->
            val owned =
                setOf(23_831, 23_832, 23_833).filterTo(mutableSetOf()) {
                    countItem(run.player, it) > 0
                }
            val component =
                CorruptedGauntletRules.demiBossComponent(demi, owned, random.of(Int.MAX_VALUE))
            addItem(run.player, component, 1)
        }
    }

    private fun spawnBoss(
        player: Player,
        session: InstanceSession,
        origin: CoordGrid,
        protection: CorruptedGauntletCombatStyle,
    ): Npc {
        val npc = Npc(hunllefTypeFor(protection), CorruptedGauntletInstanceLayout.bossSpawn(origin))
        npc.baseHitpointsLvl = CorruptedGauntletConfig.HUNLLEF_MAX_HITPOINTS
        npc.hitpoints = CorruptedGauntletConfig.HUNLLEF_MAX_HITPOINTS
        npc.respawns = false
        npc.movementLocked = false
        npc.moveRestrict = MoveRestrict.Normal
        npc.apRangeOverride = 8
        npc.maxOpRangeOverride = 16
        npc.hideAllOps()
        npc.assignSpawnOwner(player, clock.cycle)
        npcs.add(npc, Int.MAX_VALUE)
        instances.attachNpc(session.id, npc)
        return npc
    }

    private fun processFloors(run: Run) {
        if (clock.cycle >= run.nextFloorCycle && run.floors.isEmpty()) spawnFloorPattern(run)
        run.floors.toList().forEach { floor ->
            val age = clock.cycle - floor.warningCycle
            if (!floor.damaging && age >= floor.schedule.warningTicks) {
                locs.del(floor.loc, Int.MAX_VALUE)
                floor.loc =
                    locs.add(
                        floor.tile,
                        requireObject(CorruptedGauntletConfig.Loc.DAMAGING_FLOOR),
                        Int.MAX_VALUE,
                        LocAngle.North,
                        LocShape.GroundDecor,
                    )
                floor.damaging = true
            }
            if (
                CorruptedGauntletRules.floorDamagesPlayer(
                    age,
                    run.player.coords == floor.tile,
                    floor.schedule,
                )
            ) {
                run.player.queueHit(
                    run.boss,
                    1,
                    HitType.Typeless,
                    10 + random.of(11),
                    modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                )
            }
            if (CorruptedGauntletRules.floorExpired(age, floor.schedule)) {
                locs.del(floor.loc, Int.MAX_VALUE)
                run.floors.remove(floor)
            }
        }
    }

    private fun spawnFloorPattern(run: Run) {
        val schedule = CorruptedGauntletRules.floorSchedule(run.boss.hitpoints)
        val base = CorruptedGauntletInstanceLayout.roomBase(run.origin, run.layout.boss)
        val pattern =
            CorruptedGauntletFloorPatterns.select(
                currentHitpoints = run.boss.hitpoints,
                seed = run.seed,
                ordinal = run.floorPattern++,
            )
        pattern.forEach { (offsetX, offsetZ) ->
            val tile = base.translate(offsetX + 2, offsetZ + 2)
            val loc =
                locs.add(
                    tile,
                    requireObject(CorruptedGauntletConfig.Loc.WARNING_FLOOR),
                    Int.MAX_VALUE,
                    LocAngle.North,
                    LocShape.GroundDecor,
                )
            run.floors += FloorHazard(tile, clock.cycle, schedule, loc)
        }
        run.nextFloorCycle = clock.cycle + schedule.cadenceTicks
    }

    private fun processTornadoes(run: Run) {
        if (run.pendingTornadoCycle >= 0 && clock.cycle >= run.pendingTornadoCycle) {
            run.pendingTornadoCycle = -1
            spawnTornadoes(run)
        }
        run.tornadoes.toList().forEach { tornado ->
            if (!tornado.npc.isSlotAssigned || clock.cycle >= tornado.expireCycle) {
                if (tornado.npc.isSlotAssigned) npcs.del(tornado.npc, Int.MAX_VALUE)
                run.tornadoes.remove(tornado)
                run.npcs.remove(tornado.npc)
                run.npcData.remove(tornado.npc)
                return@forEach
            }
            tornado.npc.walk(run.player.coords)
            if (tornado.npc.coords == run.player.coords && tornado.lastDamageCycle != clock.cycle) {
                tornado.lastDamageCycle = clock.cycle
                val damage = CorruptedGauntletRules.tornadoDamage(armourTierSum(run.player))
                run.player.queueHit(
                    run.boss,
                    1,
                    HitType.Typeless,
                    damage.minimum + random.of(damage.maximum - damage.minimum + 1),
                    modifier = NpcBypassProtectionPrayerPlayerHitModifier,
                )
            }
        }
    }

    private fun spawnTornadoes(run: Run) {
        val base = CorruptedGauntletInstanceLayout.roomBase(run.origin, run.layout.boss)
        val spawnOffsets = arrayOf(2 to 2, 13 to 13, 2 to 13, 13 to 2)
        repeat(CorruptedGauntletRules.tornadoCount(run.boss.hitpoints)) { index ->
            val offset = spawnOffsets[index]
            val preferred = base.translate(offset.first, offset.second)
            val tile =
                if (collision.isZoneValid(preferred) && !collision.isWalkBlocked(preferred))
                    preferred
                else findRoomSpawn(run, base, index + 20, 1) ?: return@repeat
            val npc = Npc(requireNpc(CorruptedGauntletConfig.Npc.TORNADO), tile)
            npc.respawns = false
            npc.movementLocked = false
            npc.moveRestrict = MoveRestrict.Normal
            npc.hideAllOps()
            npc.assignSpawnOwner(run.player, clock.cycle)
            npcs.add(npc, Int.MAX_VALUE)
            instances.attachNpc(run.session.id, npc)
            run.npcs += npc
            run.npcData[npc] = MonsterData(CorruptedGauntletMonsterTier.WEAK, null, null)
            run.tornadoes +=
                Tornado(npc, clock.cycle + CorruptedGauntletConfig.HUNLLEF_TORNADO_LIFETIME)
        }
    }

    private fun processTrample(run: Run) {
        val inside =
            run.player.coords.x in
                run.boss.coords.x until run.boss.coords.x + run.boss.visType.size &&
                run.player.coords.z in
                    run.boss.coords.z until run.boss.coords.z + run.boss.visType.size
        if (inside && clock.cycle >= run.nextTrampleCycle) {
            run.nextTrampleCycle = clock.cycle + 2
            run.boss.anim(CorruptedGauntletConfig.Sequence.HUNLLEF_TRAMPLE)
            run.player.queueHit(
                run.boss,
                1,
                HitType.Typeless,
                20 + random.of(31),
                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
            )
        }
    }

    private fun keepHunllefAggressive(run: Run) {
        if (run.boss.isSlotAssigned && run.boss.hitpoints > 0 && run.boss.mode == null)
            run.boss.apPlayer2(run.player, interactions)
    }

    private fun scheduleHunllefStyleSwitch(run: Run, previous: CorruptedGauntletCombatStyle) {
        if (previous == run.hunllef.outgoingStyle) return
        run.pendingStyleSwitch = run.hunllef.outgoingStyle
        run.pendingStyleSwitchCycle = clock.cycle + 1
    }

    private fun processHunllefStyleSwitch(run: Run) {
        val style = run.pendingStyleSwitch ?: return
        if (clock.cycle < run.pendingStyleSwitchCycle) return
        run.pendingStyleSwitch = null
        run.pendingStyleSwitchCycle = -1
        run.boss.anim(
            if (style == CorruptedGauntletCombatStyle.MAGIC)
                CorruptedGauntletConfig.Sequence.HUNLLEF_SWITCH_TO_MAGIC
            else CorruptedGauntletConfig.Sequence.HUNLLEF_SWITCH_TO_RANGED
        )
    }

    private fun roomRoll(run: Run, room: CorruptedGauntletRoom, minimum: Int, bound: Int): Int {
        require(bound > 0)
        val mixed = run.seed xor (room.grid.x.toLong() shl 32) xor room.grid.z.toLong()
        return minimum + Math.floorMod((mixed xor (mixed ushr 29)).toInt(), bound)
    }

    private fun findRoomSpawn(run: Run, base: CoordGrid, salt: Int, footprint: Int): CoordGrid? {
        val maximum = 14 - footprint
        if (maximum < 2) return null
        val width = maximum - 1
        repeat(width * width) { attempt ->
            val ordinal =
                Math.floorMod((run.seed + salt * 43L + attempt * 17L).toInt(), width * width)
            val x = 2 + ordinal % width
            val z = 2 + ordinal / width
            val candidate = base.translate(x, z)
            val clear =
                (0 until footprint).all { dx ->
                    (0 until footprint).all { dz ->
                        val tile = candidate.translate(dx, dz)
                        collision.isZoneValid(tile) &&
                            !collision.isWalkBlocked(tile) &&
                            tile.packed !in run.nodes
                    }
                }
            if (!clear) return@repeat
            val occupied =
                run.npcs.any { npc ->
                    npc.isSlotAssigned &&
                        npc.coords.level == candidate.level &&
                        abs(npc.coords.x - candidate.x) < maxOf(npc.visType.size, footprint) &&
                        abs(npc.coords.z - candidate.z) < maxOf(npc.visType.size, footprint)
                }
            if (!occupied) return candidate
        }
        return null
    }

    private fun clearHazards(run: Run) {
        run.floors.forEach { locs.del(it.loc, Int.MAX_VALUE) }
        run.floors.clear()
        run.tornadoes.forEach { if (it.npc.isSlotAssigned) npcs.del(it.npc, Int.MAX_VALUE) }
        run.tornadoes.clear()
    }

    private fun giveStartingKit(player: Player) {
        player.righthand = InvObj(requireItem(CorruptedGauntletConfig.Obj.SCEPTRE))
        listOf(23_821, 23_822, 23_823, 23_865, 23_858).forEach { check(addItem(player, it, 1)) }
    }

    private fun syncCompletionVarp(player: Player) {
        val varp =
            requireNotNull(
                ServerCacheManager.getVarp(CorruptedGauntletConfig.CORRUPTED_COMPLETION_VARP)
            )
        VarPlayerIntMapSetter.set(player, varp, rewards.completions(player))
    }

    private fun addItem(player: Player, clientId: Int, count: Int): Boolean {
        val type = requireItem(clientId)
        val singleStack = player.inv.type.stack == InvStackType.Always || type.isStackable
        if (singleStack) {
            val slot = player.inv.indices.firstOrNull { player.inv[it]?.id == type.id }
            if (slot != null) {
                val old = checkNotNull(player.inv[slot])
                player.inv[slot] = InvObj(old.id, old.count + count, old.vars)
                return true
            }
            val empty = player.inv.indices.firstOrNull { player.inv[it] == null } ?: return false
            player.inv[empty] = InvObj(type, count)
            return true
        }
        if (player.inv.freeSpace() < count) return false
        repeat(count) {
            val empty = player.inv.indices.first { player.inv[it] == null }
            player.inv[empty] = InvObj(type)
        }
        return true
    }

    private fun removeItem(player: Player, clientId: Int, amount: Int): Boolean {
        val type = requireItem(clientId)
        if (countItem(player, clientId) < amount) return false
        var remaining = amount
        for (slot in player.inv.indices) {
            val obj = player.inv[slot] ?: continue
            if (obj.id != type.id) continue
            val take = minOf(obj.count, remaining)
            val left = obj.count - take
            player.inv[slot] = if (left == 0) null else InvObj(obj.id, left, obj.vars)
            remaining -= take
            if (remaining == 0) return true
        }
        return remaining == 0
    }

    private fun countItem(player: Player, clientId: Int): Int {
        val id = requireItem(clientId).id
        return player.inv.filterNotNull { it.id == id }.sumOf { it.count }
    }

    private fun inventoryCounts(player: Player): Map<Int, Int> =
        player.inv.filterNotNull().groupingBy { it.id }.fold(0) { acc, obj -> acc + obj.count }

    private fun armourTierSum(player: Player): Int {
        fun tier(ids: IntArray): Int {
            val wornIds = player.worn.filterNotNull().mapTo(hashSetOf()) { it.id }
            val index = ids.indexOfLast { requireItem(it).id in wornIds }
            return if (index < 0) 0 else index + 1
        }
        return CorruptedGauntletRules.armourTierSum(
            tier(intArrayOf(23_840, 23_841, 23_842)),
            tier(intArrayOf(23_843, 23_844, 23_845)),
            tier(intArrayOf(23_846, 23_847, 23_848)),
        )
    }

    private fun protectedFrom(player: Player, style: CorruptedGauntletCombatStyle): Boolean =
        player.vars[
                when (style) {
                    CorruptedGauntletCombatStyle.MELEE -> "varbit.prayer_protectfrommelee"
                    CorruptedGauntletCombatStyle.RANGED -> "varbit.prayer_protectfrommissiles"
                    CorruptedGauntletCombatStyle.MAGIC -> "varbit.prayer_protectfrommagic"
                }] == 1

    private fun hunllefTypeFor(style: CorruptedGauntletCombatStyle): NpcServerType =
        requireNpc(
            when (style) {
                CorruptedGauntletCombatStyle.MELEE ->
                    CorruptedGauntletConfig.Npc.HUNLLEF_MELEE_PROTECTED
                CorruptedGauntletCombatStyle.RANGED ->
                    CorruptedGauntletConfig.Npc.HUNLLEF_RANGED_PROTECTED
                CorruptedGauntletCombatStyle.MAGIC ->
                    CorruptedGauntletConfig.Npc.HUNLLEF_MAGIC_PROTECTED
            }
        )

    private fun HitType.toGauntletStyle(): CorruptedGauntletCombatStyle? =
        when (this) {
            HitType.Melee -> CorruptedGauntletCombatStyle.MELEE
            HitType.Ranged -> CorruptedGauntletCombatStyle.RANGED
            HitType.Magic -> CorruptedGauntletCombatStyle.MAGIC
            else -> null
        }

    private fun roomFor(run: Run, coords: CoordGrid) =
        CorruptedGauntletInstanceLayout.roomAt(run.origin, coords)

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) { "Missing npc $id" }

    private fun requireObject(id: Int): ObjectServerType =
        requireNotNull(ServerCacheManager.getObject(id)) { "Missing loc $id" }

    private fun requireItem(id: Int) =
        requireNotNull(ServerCacheManager.getItem(id)) { "Missing item $id" }

    private data class Run(
        val id: UUID,
        val player: Player,
        val session: InstanceSession,
        val layout: CorruptedGauntletLayout,
        val origin: CoordGrid,
        val boss: Npc,
        var generation: Long,
        val seed: Long,
        val startCycle: Int,
        var stage: CorruptedGauntletStage = CorruptedGauntletStage.PREPARATION,
        var bossStartCycle: Int = 0,
        var currentRoom: CorruptedGauntletGridPoint? = null,
        val revealed: MutableSet<CorruptedGauntletGridPoint> = mutableSetOf(),
        val nodes: MutableMap<Int, ResourceNode> = mutableMapOf(),
        val npcs: MutableSet<Npc> = mutableSetOf(),
        val npcData: MutableMap<Npc, MonsterData> = mutableMapOf(),
        val killsByTier: MutableMap<CorruptedGauntletMonsterTier, Int> = mutableMapOf(),
        var hunllef: CorruptedGauntletHunllefState = CorruptedGauntletHunllefState(),
        var pendingTornadoCycle: Int = -1,
        var pendingStyleSwitchCycle: Int = -1,
        var pendingStyleSwitch: CorruptedGauntletCombatStyle? = null,
        var nextFloorCycle: Int = Int.MAX_VALUE,
        var nextTrampleCycle: Int = 0,
        var floorPattern: Int = 0,
        val floors: MutableList<FloorHazard> = mutableListOf(),
        val tornadoes: MutableList<Tornado> = mutableListOf(),
        var damageDealt: Int = 0,
        var bossKilled: Boolean = false,
        var nextResultRetryCycle: Int = 0,
        var resultFailureNotified: Boolean = false,
    )

    private data class ResourceNode(
        val resource: CorruptedGauntletResource,
        var remaining: Int,
        var loc: LocInfo,
    )

    private data class MonsterData(
        val tier: CorruptedGauntletMonsterTier,
        val demi: CorruptedGauntletDemiBoss?,
        val style: CorruptedGauntletCombatStyle?,
        val animation: Int? = null,
        val boss: Boolean = false,
    )

    private data class FloorHazard(
        val tile: CoordGrid,
        val warningCycle: Int,
        val schedule: CorruptedGauntletFloorSchedule,
        var loc: LocInfo,
        var damaging: Boolean = false,
    )

    private data class Tornado(val npc: Npc, val expireCycle: Int, var lastDamageCycle: Int = -1)

    companion object {
        private val HUNLLEF_PROJECTILE =
            ProjAnimType(
                startHeight = CorruptedGauntletConfig.Projectile.REFERENCE_START_HEIGHT,
                endHeight = CorruptedGauntletConfig.Projectile.REFERENCE_END_HEIGHT,
                delay = CorruptedGauntletConfig.Projectile.REFERENCE_START_TIME,
                angle = 18,
                lengthAdjustment = CorruptedGauntletConfig.Projectile.REFERENCE_END_TIME,
                progress = 0,
                stepMultiplier = 5,
            )
        private val MONSTER_PROJECTILE =
            ProjAnimType(
                startHeight = 36,
                endHeight = 36,
                delay = 41,
                angle = 14,
                lengthAdjustment = 3,
                progress = 4,
                stepMultiplier = 5,
            )
    }
}
