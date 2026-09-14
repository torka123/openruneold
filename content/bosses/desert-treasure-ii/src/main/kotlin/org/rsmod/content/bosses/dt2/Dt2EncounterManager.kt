package org.rsmod.content.bosses.dt2

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcServerType
import dev.openrune.types.ProjAnimType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.combat.NpcProjectile
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.combat.formulas.isSlayerTask
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.RaidLeaveResult
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedBy
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.NpcCalculatedProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.NpcPartialProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.hit.queueImpactHit
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.output.Camera
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdd
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RouteFactory
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitImpactHandler
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Owner-only, seeded encounter reducer shared by all four repeatable DT2 bosses. */
@Singleton
internal class Dt2EncounterManager
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val bossHpBar: BossHpBarScript,
    private val npcs: NpcRepository,
    private val locs: LocRepository,
    private val interactions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
    private val combat: NvPCombat,
    private val world: WorldRepository,
    private val eventBus: EventBus,
    private val leviathanRecords: LeviathanRecordService,
    private val musicPlayer: MusicPlayer,
    private val routeFactory: RouteFactory,
    private val dukeEncounter: DukeEncounter,
) {
    private val log = InlineLogger()
    private val runs = mutableMapOf<PlayerUid, Run>()
    private var generation = 1L

    fun combatNpcTypes(): Set<NpcServerType> =
        listOf(12203, 12204, 12205, 12223, 12214, 12167, 12191, 12195)
            .mapNotNull(ServerCacheManager::getNpc)
            .toSet()

    fun helperNpcTypes(): Set<NpcServerType> =
        Dt2Assets.helperNpcIds.mapNotNull(ServerCacheManager::getNpc).toSet()

    fun validateAssets() {
        listOf(
                12203,
                12204,
                12205,
                12223,
                12225,
                12226,
                12227,
                12229,
                12214,
                12220,
                12222,
                12167,
                12191,
                12195,
            )
            .plus(Dt2Assets.helperNpcIds)
            .distinct()
            .forEach { id ->
                requireNotNull(ServerCacheManager.getNpc(id)) {
                    "Missing revision-240 DT2 npc: $id"
                }
            }
        Dt2Assets.sequences.forEach { id ->
            requireNotNull(ServerCacheManager.getAnim(id)) {
                "Missing revision-240 DT2 sequence: $id"
            }
        }
        Dt2Assets.spotanims.forEach { SpotanimType(it) }
        (Dt2Assets.exitLocs +
                Dt2Assets.dukePrepLocs +
                Dt2Assets.whispererMechanicLocs +
                Dt2Assets.LEVIATHAN_START_HANDHOLDS +
                Dt2Assets.LEVIATHAN_SCOREBOARD +
                Dt2Assets.LEVIATHAN_DEBRIS_BLOCKER)
            .distinct()
            .forEach { id ->
                requireNotNull(ServerCacheManager.getObject(id)) {
                    "Missing revision-240 DT2 location: $id"
                }
            }
        (Dt2Assets.preparationItems +
                listOf(
                    Dt2Assets.AWAKENERS_ORB,
                    Dt2Assets.BLACKSTONE_FRAGMENT,
                    Dt2Assets.PESTLE_AND_MORTAR,
                    Dt2Assets.IRON_PICKAXE,
                ))
            .forEach { id ->
                requireNotNull(ServerCacheManager.getItem(id)) {
                    "Missing revision-240 DT2 item: $id"
                }
            }
    }

    fun enter(player: Player, boss: Dt2Boss, difficulty: Dt2Difficulty, seed: Long): Boolean {
        if (boss == Dt2Boss.VARDORVIS) return enterVardorvis(player, difficulty, seed)
        if (player.hitpoints <= 0 || player.isAccessProtected) {
            player.mes("Finish what you are doing before starting this encounter.")
            return false
        }
        if (instances.sessionForPlayer(player) != null || runs.containsKey(player.uid)) {
            player.mes("Leave your current instance before starting another encounter.")
            return false
        }
        val initialType = initialType(boss, difficulty)
        val result =
            instances.create(
                owner = player,
                key = boss.key,
                spec = boss.spec(initialType),
                access = InstanceAccess.Private,
                currentTick = clock.cycle,
            )
        if (result !is InstanceManager.Result.Created) {
            player.mes(
                (result as? InstanceManager.Result.Failed)?.reason
                    ?: "The ${boss.displayName} arena is unavailable."
            )
            return false
        }
        prepare(player)
        PathingEntityCommon.telejump(player, collision, result.enter)
        instances.finalizeEntry(player, result.session, clock.cycle)
        player.lootDropDuration = DT2_DROP_DURATION
        val npc = spawnBoss(player, result.session, boss, difficulty)
        val initialState =
            if (boss in setOf(Dt2Boss.DUKE, Dt2Boss.WHISPERER, Dt2Boss.LEVIATHAN))
                Dt2State.PREPARING
            else Dt2State.STARTING
        val run =
            Run(
                player,
                result.session,
                generation++,
                boss,
                difficulty,
                npc,
                initialState,
                Dt2SeededRng(seed),
                seed,
                clock.cycle + 4,
                clock.cycle,
                rewardEligible = Dt2Rules.rewardEligibleOnEntry(difficulty),
            )
        if (boss == Dt2Boss.LEVIATHAN) {
            run.attackCounter =
                Dt2Rules.leviathanInitialAttackCounter(difficulty == Dt2Difficulty.AWAKENED)
        }
        runs[player.uid] = run
        if (boss != Dt2Boss.LEVIATHAN) {
            bossHpBar.onOpen(player, npc)
            bossHpBar.onUpdate(player, npc, npc.hitpoints, npc.baseHitpointsLvl)
        }
        if (boss == Dt2Boss.LEVIATHAN) spawnLeviathanSceneActors(run)
        when (boss) {
            Dt2Boss.WHISPERER -> {
                npc.anim(10227)
                player.ifOpenOverlay(DT2_SANITY_INTERFACE, eventBus)
                setSanity(run, 100)
                val fragment = requireItem(Dt2Assets.BLACKSTONE_FRAGMENT)
                if (player.inv.count(fragment.internalName) == 0) {
                    if (!player.invAdd(player.inv, fragment.id, 1).success) {
                        abort(player, "could not issue Blackstone fragment", teleport = true)
                        player.mes("Make one free inventory space before entering The Whisperer.")
                        return false
                    }
                    player.mes("A Blackstone fragment has been placed in your inventory.")
                }
                player.mes("Disturb the Odd Figure when you are ready.")
            }
            Dt2Boss.DUKE -> {
                dukeEncounter.prepare(run)
                player.mes("Prepare the Arder-musca poison, then use it on Duke Sucellus.")
            }
            Dt2Boss.LEVIATHAN -> {
                npc.anim(10276)
                player.mes(
                    "Climb the south-western handholds when you are ready to face " +
                        "The Leviathan."
                )
            }
            else -> player.mes("${boss.displayName} is preparing to attack.")
        }
        log.info {
            "[DT2] enter player=${player.displayName} boss=${boss.command} difficulty=$difficulty " +
                "seed=$seed instance=${result.session.id}"
        }
        return true
    }

    private fun enterVardorvis(player: Player, difficulty: Dt2Difficulty, seed: Long): Boolean {
        if (difficulty == Dt2Difficulty.AWAKENED) {
            player.mes(
                "Awakened Vardorvis is unavailable: revision-240 NPC 12224 is the quest form."
            )
            return false
        }
        if (player.hitpoints <= 0 || player.isAccessProtected) {
            player.mes("Finish what you are doing before starting this encounter.")
            return false
        }
        if (instances.sessionForPlayer(player) != null || runs.containsKey(player.uid)) {
            player.mes("Leave your current instance before starting another encounter.")
            return false
        }
        val result =
            instances.create(
                owner = player,
                key = Dt2Boss.VARDORVIS.key,
                spec = Dt2Boss.VARDORVIS.spec(requireNpc(Dt2Boss.VARDORVIS.normalNpcId)),
                access = InstanceAccess.Private,
                currentTick = clock.cycle,
            )
        if (result !is InstanceManager.Result.Created) {
            player.mes(
                (result as? InstanceManager.Result.Failed)?.reason
                    ?: "The Vardorvis arena is unavailable."
            )
            return false
        }
        val safeEntry =
            requireNotNull(instances.localCoord(result.session, Dt2Assets.VARDORVIS_SAFE_ENTRY))
        prepare(player)
        PathingEntityCommon.telejump(player, collision, safeEntry)
        instances.finalizeEntry(player, result.session, clock.cycle)
        player.lootDropDuration = DT2_DROP_DURATION
        val npc = spawnBoss(player, result.session, Dt2Boss.VARDORVIS, Dt2Difficulty.NORMAL)
        npc.movementLocked = true
        npc.apRangeOverride = 1
        val run =
            Run(
                player,
                result.session,
                generation++,
                Dt2Boss.VARDORVIS,
                Dt2Difficulty.NORMAL,
                npc,
                Dt2State.PREPARING,
                Dt2SeededRng(seed),
                seed,
                Int.MAX_VALUE,
                clock.cycle,
                rewardEligible = Dt2Rules.rewardEligibleOnEntry(Dt2Difficulty.NORMAL),
            )
        run.lastPlayerTile = player.coords
        runs[player.uid] = run
        setVardorvisExitLocked(run, locked = false)
        player.mes("You enter the ritual site.")
        player.mes("Vardorvis remains dormant until you attack him.")
        log.info {
            "[DT2] enter player=${player.displayName} boss=vardorvis difficulty=NORMAL " +
                "seed=$seed instance=${result.session.id}"
        }
        return true
    }

    fun tickAll(dukeOnly: Boolean = false) {
        val activePlayers =
            runs.values.filter { (it.bossKind == Dt2Boss.DUKE) == dukeOnly }.map { it.player }
        activePlayers.forEach { player ->
            try {
                tick(player)
            } catch (error: Exception) {
                log.error(error) {
                    "[DT2] encounter tick failed for player=${player.displayName}; cleaning up instance"
                }
                runCatching { abort(player, "server-side encounter exception", teleport = true) }
                    .onFailure { cleanup ->
                        log.error(cleanup) { "[DT2] exception cleanup also failed" }
                    }
            }
        }
    }

    fun tick(player: Player) {
        val run = runs[player.uid] ?: return
        if (!player.isSlotAssigned || player.pendingLogout || player.loggingOut) return
        if (instances.sessionForPlayer(player)?.id != run.session.id) {
            abort(player, "left instance outside an exit", teleport = false)
            return
        }
        if (run.bossKind == Dt2Boss.DUKE) {
            if (player.hitpoints <= 0) return
            dukeEncounter.tick(run)
            if (run.state == Dt2State.RESPAWNING) {
                if (clock.cycle >= run.respawnCycle) respawn(run)
            }
            return
        }
        if (run.bossKind == Dt2Boss.VARDORVIS) {
            tickVardorvis(run)
            return
        }
        if (run.bossKind == Dt2Boss.WHISPERER) tickWhispererRuntime(run)
        if (run.bossKind == Dt2Boss.LEVIATHAN) tickLeviathanRuntime(run)
        if (run.prayerUnlockCycle > clock.cycle) clearProtectionPrayers(player)
        if (run.rootEndCycle > clock.cycle) player.clearInteraction()
        tickDangerTiles(run)
        tickMovingHazards(run)
        when (run.state) {
            Dt2State.STARTING -> tickStarting(run)
            Dt2State.ACTIVE,
            Dt2State.ENRAGED -> {
                keepAggressive(run)
                tickActive(run)
            }
            Dt2State.SPECIAL -> tickSpecial(run)
            Dt2State.STUNNED -> tickLeviathanStun(run)
            Dt2State.RESPAWNING -> if (clock.cycle >= run.respawnCycle) respawn(run)
            Dt2State.PREPARING,
            Dt2State.DYING,
            Dt2State.INVALIDATED -> Unit
        }
    }

    fun attack(access: StandardNpcAccess, target: Player) {
        val run = runs[target.uid] ?: return
        if (run.boss !== access.npc || run.state !in setOf(Dt2State.ACTIVE, Dt2State.ENRAGED))
            return
        if (
            run.bossKind == Dt2Boss.LEVIATHAN &&
                run.leviathanAction != LeviathanAction.STANDARD_VOLLEY
        ) {
            return
        }
        when (run.bossKind) {
            Dt2Boss.WHISPERER -> attackWhisperer(access, run)
            Dt2Boss.VARDORVIS -> Unit
            Dt2Boss.LEVIATHAN -> attackLeviathan(access, run)
            Dt2Boss.DUKE -> Unit // Duke's clock-driven state machine owns every attack.
        }
    }

    fun modifyNpcHit(player: Player, npc: Npc, hit: HitBuilder) {
        val run = runs[player.uid]
        if (run == null || !npc.isSpawnOwnedBy(player)) {
            hit.damage = 0
            return
        }
        if (npc !== run.boss) {
            modifyHelperHit(run, npc, hit)
            return
        }
        val damageable =
            run.state in setOf(Dt2State.ACTIVE, Dt2State.ENRAGED, Dt2State.STUNNED) ||
                (run.bossKind == Dt2Boss.WHISPERER && run.state == Dt2State.SPECIAL) ||
                (run.bossKind == Dt2Boss.LEVIATHAN &&
                    run.state in setOf(Dt2State.ACTIVE, Dt2State.ENRAGED))
        if (!damageable) {
            hit.damage = 0
            return
        }
        hit.damage = hit.damage.coerceAtLeast(0)
        when (run.bossKind) {
            Dt2Boss.WHISPERER -> modifyWhispererHit(run, hit)
            Dt2Boss.LEVIATHAN -> modifyLeviathanHit(run, hit)
            else -> Unit
        }
        val after = (run.boss.hitpoints - hit.damage).coerceAtLeast(0)
        if (run.bossKind in setOf(Dt2Boss.LEVIATHAN, Dt2Boss.VARDORVIS)) {
            bossHpBar.onUpdate(run.player, run.boss, after, run.maximumHp)
        }
        if (run.bossKind == Dt2Boss.WHISPERER) checkWhispererThreshold(run, after)
        if (
            run.bossKind == Dt2Boss.LEVIATHAN &&
                !run.leviathanTornadoStarted &&
                Dt2Rules.leviathanTornadoActive(
                    after,
                    run.maximumHp,
                    run.difficulty == Dt2Difficulty.AWAKENED,
                )
        ) {
            beginLeviathanTornado(run)
        }
        if (run.bossKind == Dt2Boss.VARDORVIS && !run.enraged && Dt2Rules.vardorvisEnraged(after)) {
            run.vardorvisEnragePending = true
        }
        if (
            run.bossKind == Dt2Boss.LEVIATHAN &&
                !run.enraged &&
                Dt2Rules.leviathanEnraged(after, run.maximumHp)
        ) {
            beginEnrage(run)
        }
    }

    private fun modifyWhispererHit(run: Run, hit: HitBuilder) {
        if (run.state == Dt2State.SPECIAL)
            hit.damage = Dt2Rules.whispererSpecialDamage(hit.damage, run.shadowRealm)
        if (hit.damage < run.boss.hitpoints || run.enraged) return
        hit.damage = 0
        cleanupTransientHazards(run)
        run.boss.hitpoints = if (run.difficulty == Dt2Difficulty.AWAKENED) 250 else 140
        run.enraged = true
        run.state = Dt2State.ENRAGED
        run.activeSpecial = null
        run.specialProgress = 0
        run.specialRequired = 0
        run.attackCounter = 0
        run.nextSpecialCycle = Int.MAX_VALUE
        run.boss.actionDelay = clock.cycle + 3
        run.boss.anim(10250)
        run.player.mes("The Whisperer pulls you into the Shadow Realm for her final stand!")
        setShadowRealm(run, true)
        setSanity(run, 100)
    }

    private fun modifyLeviathanHit(run: Run, hit: HitBuilder) {
        if (hit.isFromPlayer) {
            val secondary = hit.secondaryType()
            val allowedForUnconventional =
                Dt2Rules.isLeviathanShadowSpell(secondary) ||
                    (hit.type == HitType.Ranged &&
                        Dt2Rules.isLeviathanMithrilAmmunition(secondary?.internalName))
            if (!allowedForUnconventional) run.leviathanOnlyMithrilAmmunition = false
        }
        val successfulShadowHit =
            hit.isFromPlayer &&
                hit.damage > 0 &&
                Dt2Rules.isLeviathanShadowSpell(hit.secondaryType())
        if (Dt2Rules.leviathanCanStun(run.leviathanAction, run.enraged, successfulShadowHit)) {
            run.leviathanSuccessfulStuns++
            run.leviathanAction = LeviathanAction.STUNNED
            run.specialStartCycle = clock.cycle
            run.specialEndCycle = clock.cycle + Dt2Rules.leviathanStunTicks(run.difficulty)
            run.leviathanStunDamage = hit.damage
            run.boss.clearInteraction()
            run.boss.anim(10280)
            run.boss.actionDelay = run.specialEndCycle
            run.player.mes("The Leviathan is stunned. Strike the exposed wound from behind!")
            run.player.actionDelay = clock.cycle
            return
        }
        if (run.leviathanAction == LeviathanAction.STUNNED) {
            val style =
                when (hit.type) {
                    HitType.Ranged -> Dt2AttackStyle.RANGED
                    HitType.Magic -> Dt2AttackStyle.MAGIC
                    else -> Dt2AttackStyle.MELEE
                }
            val behind =
                Dt2Rules.leviathanBehindWound(
                    run.player.coords,
                    run.boss.coords,
                    run.boss.size,
                    LeviathanFacing.NORTH_WEST,
                )
            hit.damage =
                Dt2Rules.leviathanWoundDamage(
                    hit.damage,
                    run.player.vars["varp.com_maxhit"],
                    style,
                    behind,
                )
            run.leviathanStunDamage += hit.damage
            if (
                Dt2Rules.leviathanStunResetsVolley(run.leviathanStunDamage, run.maximumHp, behind)
            ) {
                run.stunCount++
                run.attackCounter = Dt2Rules.leviathanResetAttackCounter(run.stunCount)
                beginLeviathanRetaliation(run)
                run.player.mes(
                    if (behind) "You strike the exposed wound. The Leviathan retaliates!"
                    else "The accumulated damage forces the Leviathan to retaliate!"
                )
            }
            return
        }
        if (
            run.leviathanAction in
                setOf(LeviathanAction.LIGHTNING_BARRAGE, LeviathanAction.SMOKE_BLAST)
        ) {
            hit.damage = Dt2Rules.leviathanSpecialDamage(hit.damage)
        }
        if (hit.isFromPlayer) {
            val style =
                when (hit.type) {
                    HitType.Ranged -> Dt2AttackStyle.RANGED
                    HitType.Magic -> Dt2AttackStyle.MAGIC
                    else -> Dt2AttackStyle.MELEE
                }
            hit.damage =
                Dt2Rules.leviathanPathfinderOutgoingDamage(
                    hit.damage,
                    run.player.vars["varp.com_maxhit"],
                    style,
                    run.enraged,
                    isInsideLeviathanPathfinder(run),
                )
        }
    }

    private fun modifyHelperHit(run: Run, npc: Npc, hit: HitBuilder) {
        if (npc !in run.helpers || !hit.isFromPlayer) {
            hit.damage = 0
            return
        }
        if (npc.type.id == Dt2Assets.WHISPERER_SOUL && !run.shadowRealm) hit.damage = 0
        if (run.bossKind == Dt2Boss.VARDORVIS) hit.damage = 0
        if (
            run.bossKind == Dt2Boss.LEVIATHAN &&
                npc.type.id in
                    setOf(
                        Dt2Assets.LEVIATHAN_TAIL_WEST,
                        Dt2Assets.LEVIATHAN_TAIL_SOUTH,
                        Dt2Assets.LEVIATHAN_TAIL_EAST,
                        Dt2Assets.LEVIATHAN_PATHFINDER,
                        Dt2Assets.LEVIATHAN_TORNADO,
                    )
        ) {
            hit.damage = 0
        }
    }

    fun rewardEligible(run: Run): Boolean = run.rewardEligible

    fun rewardEligible(player: Player): Boolean = runs[player.uid]?.rewardEligible == true

    fun isLeviathanActive(player: Player): Boolean = runs[player.uid]?.bossKind == Dt2Boss.LEVIATHAN

    fun rewardTile(run: Run): CoordGrid =
        when (run.bossKind) {
            Dt2Boss.DUKE -> run.boss.coords.translate(3, -1)
            Dt2Boss.VARDORVIS -> run.boss.coords
            else -> run.player.coords
        }

    fun beginBossDeath(npc: Npc): Run? {
        val run = runs.values.firstOrNull { it.boss === npc } ?: return null
        if (run.state in setOf(Dt2State.DYING, Dt2State.RESPAWNING)) return null
        run.generation = generation++
        run.state = Dt2State.DYING
        if (run.bossKind == Dt2Boss.LEVIATHAN) run.leviathanAction = LeviathanAction.DYING
        if (run.bossKind == Dt2Boss.VARDORVIS) {
            run.boss.anim(Dt2Assets.VARDORVIS_DEATH_ANIM)
            setVardorvisExitLocked(run, locked = false)
        }
        if (run.bossKind == Dt2Boss.WHISPERER) run.boss.anim(10260)
        if (run.bossKind == Dt2Boss.DUKE) {
            run.boss.anim(DukeRules.DEATH)
            run.respawnCycle = clock.cycle + DukeRules.RESPAWN_TICKS
        }
        cleanupHazards(run)
        if (run.bossKind == Dt2Boss.DUKE) dukeEncounter.beginDeath(run)
        bossHpBar.onClose(run.player, run.boss)
        prepare(run.player)
        run.killTicks = (clock.cycle - run.killStartCycle).coerceAtLeast(0)
        return run
    }

    fun prepareDropForm(npc: Npc, run: Run) {
        val normal = ServerCacheManager.getNpc(run.bossKind.normalNpcId) ?: return
        if (npc.type.id != normal.id) npc.transmog(normal, Int.MAX_VALUE)
    }

    fun finishBossDeath(npc: Npc) {
        val run = runs.values.firstOrNull { it.boss === npc } ?: return
        if (runs[run.player.uid] !== run) return
        if (run.bossKind == Dt2Boss.LEVIATHAN) recordLeviathanKill(run)
        run.player.mes(
            "<col=ff9900>${run.bossKind.displayName} defeated in " +
                "${Dt2Rules.formatTicks(run.killTicks)}.</col>"
        )
        run.state = Dt2State.RESPAWNING
        if (run.bossKind == Dt2Boss.DUKE) {
            dukeEncounter.showCorpse(run)
            return
        }
        if (run.bossKind == Dt2Boss.LEVIATHAN) {
            run.leviathanAction = LeviathanAction.RESPAWNING
        }
        if (run.bossKind == Dt2Boss.VARDORVIS) {
            run.respawnCycle = clock.cycle + VARDORVIS_RESPAWN_TICKS
            return
        }
        run.respawnCycle =
            clock.cycle +
                if (run.bossKind == Dt2Boss.LEVIATHAN) LEVIATHAN_RESPAWN_TICKS
                else if (run.bossKind == Dt2Boss.WHISPERER) WHISPERER_RESPAWN_TICKS
                else DT2_RESPAWN_TICKS
    }

    fun helperDied(npc: Npc) {
        val run = runs.values.firstOrNull { npc in it.helpers } ?: return
        run.helpers.remove(npc)
        val group = run.whispererSoulGroups.remove(npc)
        if (run.activeSpecial == WhispererSpecial.SOUL_SIPHON && group != null) {
            val killed = (run.whispererSoulKills[group] ?: 0) + 1
            run.whispererSoulKills[group] = killed
            run.player.mes("A ${group.name.lowercase()} soul is freed ($killed/${group.required}).")
            if (killed >= group.required && group !in run.whispererCompletedSoulGroups) {
                run.whispererCompletedSoulGroups += group
                applyWhispererSoulGroup(run, group)
                run.specialProgress = run.specialRequired
            }
            return
        }
        run.specialProgress++
    }

    fun isActive(player: Player): Boolean = runs.containsKey(player.uid)

    fun isEncounterNpc(npc: Npc): Boolean = runs.values.any { it.boss === npc || npc in it.helpers }

    fun isOwnedBy(player: Player, npc: Npc): Boolean =
        runs[player.uid]?.let {
            (it.boss === npc || npc in it.helpers) && npc.isSpawnOwnedBy(player)
        } == true

    fun bypassLeviathanPlayerLineOfSight(player: Player, npc: Npc): Boolean {
        val run = runs[player.uid] ?: return false
        return run.bossKind == Dt2Boss.LEVIATHAN && run.boss === npc && npc.isSpawnOwnedBy(player)
    }

    fun attackDenial(player: Player, npc: Npc): String? {
        val run = runs[player.uid] ?: return "You do not have an active DT2 encounter."
        if (npc !== run.boss && npc !in run.helpers)
            return "That creature belongs to another encounter."
        if (npc in run.helpers) {
            return if (run.bossKind == Dt2Boss.VARDORVIS) {
                "That is part of Vardorvis' attack and cannot be fought."
            } else null
        }
        if (run.bossKind == Dt2Boss.VARDORVIS && npc === run.boss) {
            if (run.vardorvisQteOpen) return "Destroy the tendrils before attacking Vardorvis."
            if (run.state == Dt2State.PREPARING) {
                activateVardorvis(run)
                return null
            }
            if (run.state == Dt2State.SPECIAL) {
                return "Vardorvis cannot be attacked during that transition."
            }
        }
        return when (run.state) {
            Dt2State.PREPARING -> "Finish the encounter preparation first."
            Dt2State.STARTING -> "Wait for ${run.bossKind.displayName} to finish emerging."
            Dt2State.SPECIAL ->
                if (run.bossKind == Dt2Boss.WHISPERER) null else "Resolve the special attack first."
            Dt2State.DYING,
            Dt2State.RESPAWNING,
            Dt2State.INVALIDATED -> "That boss cannot be attacked right now."
            else -> null
        }
    }

    fun activateWhisperer(player: Player, npc: Npc): Boolean {
        val run = runs[player.uid] ?: return false
        if (
            run.bossKind != Dt2Boss.WHISPERER ||
                run.boss !== npc ||
                !Dt2Rules.whispererCanDisturb(npc.visType.id, run.state)
        ) {
            return false
        }
        val fragment = requireItem(Dt2Assets.BLACKSTONE_FRAGMENT)
        if (player.inv.count(fragment.internalName) == 0) {
            player.mes(
                "It would not be wise to proceed without a way to traverse the Shadow Realm."
            )
            return true
        }
        prepare(player)
        player.anim(9799)
        run.boss.anim(10227)
        run.startStage = 1
        run.startReadyCycle = clock.cycle + 4
        run.state = Dt2State.STARTING
        player.mes("The Odd Figure awakens.")
        return true
    }

    fun toggleWhispererShadowRealm(player: Player): Boolean {
        val run = runs[player.uid]
        if (run == null || run.bossKind != Dt2Boss.WHISPERER) {
            player.mes("The Blackstone fragment is unresponsive.")
            return false
        }
        if (run.state !in setOf(Dt2State.ACTIVE, Dt2State.SPECIAL, Dt2State.ENRAGED)) {
            player.mes("The Blackstone fragment is unresponsive.")
            return true
        }
        if (run.enraged && run.shadowRealm) {
            player.mes("The Whisperer will not let you escape the Shadow Realm.")
            return true
        }
        setShadowRealm(run, !run.shadowRealm)
        player.mes(
            if (run.shadowRealm) "Your Blackstone fragment pulses with dark energy."
            else "Your Blackstone fragment loses all its energy."
        )
        return true
    }

    fun activateLeviathan(player: Player, locId: Int, clicked: CoordGrid): Boolean {
        val run = runs[player.uid] ?: return false
        if (run.bossKind != Dt2Boss.LEVIATHAN || run.state != Dt2State.PREPARING) return false
        val expected =
            instances.localCoord(
                run.session,
                org.rsmod.api.instances.RegionLocal(0, 32, 99, 22, 32),
            )
        if (!Dt2Rules.leviathanValidStart(locId, clicked, expected)) {
            player.mes("Those are not the south-western handholds used to enter the arena.")
            return false
        }
        prepare(player)
        player.anim(828)
        run.startStage = 1
        run.startReadyCycle = clock.cycle + 1
        run.state = Dt2State.STARTING
        player.mes("The brain holds fall away as you use them.")
        return true
    }

    suspend fun useDukePreparation(access: ProtectedAccess, locId: Int, tile: CoordGrid) {
        val player = access.player
        val run = runs[player.uid] ?: return
        if (run.bossKind != Dt2Boss.DUKE) return
        if (locId in setOf(47522, 47524, 47528)) dukeEncounter.gather(run, access, locId, tile)
        else dukeEncounter.ingredient(run, player, locId, tile)
    }

    fun inspectDukeVat(player: Player, tile: CoordGrid, empty: Boolean) {
        val run = runs[player.uid] ?: return
        if (run.bossKind == Dt2Boss.DUKE) dukeEncounter.inspectVat(run, tile, empty)
    }

    fun grindDukeIngredient(access: ProtectedAccess, musca: Boolean) {
        val run = runs[access.player.uid] ?: return
        if (run.bossKind == Dt2Boss.DUKE && dukeEncounter.grind(run, musca)) access.anim(364)
    }

    fun poisonDuke(player: Player, npc: Npc, item: ItemServerType) {
        val run = runs[player.uid] ?: return
        if (run.bossKind == Dt2Boss.DUKE && run.boss === npc) dukeEncounter.feed(run, item.id)
    }

    fun feedDuke(player: Player, npc: Npc) {
        val run = runs[player.uid] ?: return
        if (run.bossKind != Dt2Boss.DUKE || run.boss !== npc) return
        val item =
            listOf(Dt2Assets.ARDER_MUSCA_POISON, Dt2Assets.ARDER_POWDER, Dt2Assets.MUSCA_POWDER)
                .firstOrNull { player.inv.count(requireItem(it).internalName) > 0 }
        if (item == null) player.mes("You need arder-musca poison or mushroom powder.")
        else dukeEncounter.feed(run, item)
    }

    fun solve(player: Player): Boolean {
        val run = runs[player.uid] ?: return false
        if (run.state != Dt2State.SPECIAL) {
            player.mes("There is no active puzzle mechanic to solve.")
            return false
        }
        run.rewardEligible = false
        run.specialProgress = run.specialRequired
        player.mes("The active mechanic is marked solved for this deterministic smoke test.")
        return true
    }

    fun forcePhase(player: Player, requested: String?): Boolean {
        val run = runs[player.uid] ?: return false
        if (run.state !in setOf(Dt2State.ACTIVE, Dt2State.ENRAGED)) {
            player.mes("The boss must be active before a phase can be forced.")
            return false
        }
        run.rewardEligible = false
        if (run.bossKind == Dt2Boss.VARDORVIS) {
            beginVardorvisMechanic(run, requested)
            return true
        }
        beginSpecial(run, requested)
        return true
    }

    fun reset(player: Player): Boolean {
        val run = runs[player.uid] ?: return false
        run.rewardEligible = false
        if (run.bossKind == Dt2Boss.VARDORVIS) bossHpBar.onClose(run.player, run.boss)
        cleanupHazards(run)
        if (run.boss.isSlotAssigned) npcs.del(run.boss, Int.MAX_VALUE)
        run.boss = spawnBoss(run.player, run.session, run.bossKind, run.difficulty)
        resetRun(run)
        if (run.bossKind == Dt2Boss.VARDORVIS) {
            resetVardorvisState(run)
            run.boss.anim(Dt2Assets.VARDORVIS_SPAWN_ANIM)
        }
        player.mes("The private ${run.bossKind.displayName} encounter has reset.")
        return true
    }

    fun escape(player: Player, locId: Int): Boolean {
        val run = runs[player.uid] ?: return false
        if (run.bossKind == Dt2Boss.VARDORVIS) {
            val vardorvisDefeated = run.state == Dt2State.RESPAWNING
            if (locId == Dt2Assets.VARDORVIS_EXIT_DISABLED && !vardorvisDefeated) {
                player.mes("The tendrils seal the exit while Vardorvis is active.")
                return true
            }
            if (locId != Dt2Assets.VARDORVIS_EXIT && locId != Dt2Assets.VARDORVIS_EXIT_DISABLED)
                return false
        }
        if (run.bossKind == Dt2Boss.LEVIATHAN && locId !in Dt2Assets.LEVIATHAN_EXIT_ROWBOATS) {
            return false
        }
        if (run.bossKind != Dt2Boss.LEVIATHAN && locId in Dt2Assets.LEVIATHAN_EXIT_ROWBOATS) {
            return false
        }
        abort(player, "used an arena exit", teleport = true)
        player.mes("You leave the encounter.")
        return true
    }

    fun leave(player: Player): Boolean {
        val boss = runs[player.uid]?.bossKind ?: return false
        abort(player, "used Leave Instance", teleport = true)
        player.mes(
            when (boss) {
                Dt2Boss.VARDORVIS -> "You leave Vardorvis' private ritual site."
                Dt2Boss.LEVIATHAN -> "You leave The Leviathan's private arena."
                Dt2Boss.WHISPERER -> "You leave The Whisperer's private arena."
                else -> "You leave the private encounter."
            }
        )
        return true
    }

    fun leaveFromLogoutTab(player: Player, expected: InstanceId): RaidLeaveResult {
        val run = runs[player.uid] ?: return RaidLeaveResult.Unhandled
        if (
            run.session.id != expected ||
                run.bossKind !in
                    setOf(Dt2Boss.VARDORVIS, Dt2Boss.LEVIATHAN, Dt2Boss.WHISPERER, Dt2Boss.DUKE)
        ) {
            return RaidLeaveResult.Unhandled
        }
        return if (leave(player)) RaidLeaveResult.Left else RaidLeaveResult.Failed
    }

    fun membershipEnded(player: Player, instanceId: InstanceId) {
        val run = runs[player.uid] ?: return
        if (run.session.id != instanceId) return
        runs.remove(player.uid)
        invalidateRun(run)
    }

    fun externalTeleport(player: Player, instanceId: InstanceId) {
        membershipEnded(player, instanceId)
    }

    fun playerDied(player: Player) {
        val run = runs[player.uid]
        if (run?.bossKind == Dt2Boss.LEVIATHAN) {
            leviathanRecords.recordDeath(
                player,
                eventId = "${run.eventToken}:death",
                rewardEligible = run.rewardEligible,
            )
            abort(player, "player death", teleport = true)
        } else {
            abort(player, "player death", teleport = false)
        }
    }

    fun abort(player: Player, reason: String, teleport: Boolean, logout: Boolean = false) {
        val run = runs.remove(player.uid) ?: return
        invalidateRun(run)
        if (logout) instances.handleLogout(player, clock.cycle)
        else {
            val exit = instances.leave(player, run.session, clock.cycle)
            if (teleport && player.isSlotAssigned)
                PathingEntityCommon.telejump(player, collision, exit)
        }
        prepare(player)
        log.info { "[DT2] player=${player.displayName} boss=${run.bossKind.command} abort=$reason" }
    }

    private fun invalidateRun(run: Run) {
        if (run.bossKind == Dt2Boss.LEVIATHAN && run.rewardEligible) {
            leviathanRecords.endVisit(run.player)
        }
        if (
            run.bossKind == Dt2Boss.LEVIATHAN &&
                run.player.isSlotAssigned &&
                run.leviathanMusicRestorePoint != null
        ) {
            musicPlayer.restoreTemporaryMusic(run.player, run.leviathanMusicRestorePoint!!)
            run.leviathanMusicRestorePoint = null
        }
        run.state = Dt2State.INVALIDATED
        if (run.bossKind == Dt2Boss.LEVIATHAN) {
            run.leviathanAction = LeviathanAction.INVALIDATED
        }
        cleanupHazards(run)
        removePreparationItems(run.player)
        closeWhispererUi(run)
        bossHpBar.onClose(run.player, run.boss)
        if (run.boss.isSlotAssigned) npcs.del(run.boss, Int.MAX_VALUE)
        run.player.lootDropDuration = null
        prepare(run.player)
    }

    private fun recordLeviathanKill(run: Run) {
        if (!run.rewardEligible || run.leviathanKillRecordCommitted) return
        run.leviathanKillRecordCommitted = true
        val type = requireNpc(Dt2Boss.LEVIATHAN.normalNpcId)
        val killCountVarp = type.paramOrNull(BaseParams.killcount_varp)
        val killCount = killCountVarp?.let { run.player.vars[it] } ?: 0
        if (killCount <= 0) {
            log.warn {
                "[DT2] refusing Leviathan record update without a positive cache-backed killcount " +
                    "player=${run.player.displayName}"
            }
            return
        }
        val update =
            leviathanRecords.recordKill(
                run.player,
                LeviathanKillEvidence(
                    eventId = "${run.eventToken}:kill:${clock.cycle}",
                    rewardEligible = true,
                    elapsedTicks = run.killTicks,
                    killCountAfterKill = killCount,
                    awakened = run.difficulty == Dt2Difficulty.AWAKENED,
                    stunCount = run.leviathanSuccessfulStuns,
                    perfectKill = run.leviathanPerfectKill,
                    onSlayerTask =
                        run.player.vars["varp.slayer_count"] > 0 &&
                            run.boss.visType.isSlayerTask(run.player),
                    onlyMithrilAmmunition = run.leviathanOnlyMithrilAmmunition,
                    neverAbove25Hitpoints = run.leviathanNeverAbove25Hitpoints,
                ),
            )
        if (update.newPersonalBest) {
            run.player.mes(
                "<col=ff9900>New Leviathan personal best: " +
                    "${Dt2Rules.formatTicks(run.killTicks)}.</col>"
            )
        }
        update.newlyCompletedTasks.forEach { task ->
            run.player.mes("<col=0cc919>Combat task completed: ${task.displayName}.</col>")
        }
    }

    private fun markLeviathanPerfectFailure(run: Run, failure: LeviathanPerfectFailure) {
        if (runs[run.player.uid] !== run || run.bossKind != Dt2Boss.LEVIATHAN) return
        run.leviathanPerfectKill = false
        run.leviathanPerfectFailures += failure
    }

    fun debugStatus(player: Player): String {
        val run = runs[player.uid] ?: return "DT2: no active encounter."
        return "DT2 boss=${run.bossKind.command} difficulty=${run.difficulty} generation=${run.generation} " +
            "state=${run.state} hp=${run.boss.hitpoints}/${run.maximumHp} attacks=${run.attackCounter} " +
            "tier=${Dt2Rules.leviathanVolleyTier(run.attackCounter)} action=${run.leviathanAction} roarDue=${run.leviathanRoarCycle} " +
            "special=${run.activeSpecial ?: "none"} progress=${run.specialProgress}/${run.specialRequired} " +
            "sanity=${run.sanity} shadow=${run.shadowRealm} hazards=${run.dangerTiles.size}+${run.movingHazards.size} seed=${run.seed}"
    }

    private fun tickStarting(run: Run) {
        if (clock.cycle < run.startReadyCycle) return
        if (run.bossKind == Dt2Boss.WHISPERER && run.startStage == 1) {
            run.boss.transmog(requireNpc(Dt2Assets.WHISPERER_ACTIVE_ID), Int.MAX_VALUE)
            run.boss.anim(10229)
            run.startStage = 2
            activate(run)
            run.player.mes("A whisper slips into your mind as the battle begins.")
            return
        }
        if (run.bossKind != Dt2Boss.LEVIATHAN || run.startStage == 0) {
            activate(run)
            return
        }
        when (run.startStage) {
            1 -> {
                val inside =
                    requireNotNull(
                        instances.localCoord(run.session, Dt2Assets.LEVIATHAN_INSIDE_ENTRY)
                    )
                PathingEntityCommon.telejump(run.player, collision, inside)
                // The handhold transition places the player at the real arena entrance.
                // Do not wait for a deeper walk-in tile: the copied revision-240 room
                // has a stair collision seam that can clip/teleport the player before
                // the old capture marker is reached. Start the emergence sequence as
                // soon as the player arrives in the arena instead.
                run.boss.anim(10291)
                run.boss.actionDelay = clock.cycle + 4
                run.startStage = 3
                run.startReadyCycle = clock.cycle + 4
            }
            else -> activate(run)
        }
    }

    private fun tickLeviathanRuntime(run: Run) {
        if (runs[run.player.uid] !== run) return
        if (clock.cycle >= run.leviathanCameraResetCycle) {
            Camera.camReset(run.player)
            run.leviathanCameraResetCycle = Int.MAX_VALUE
        }
        tickLeviathanDebris(run)
        if (run.state !in setOf(Dt2State.ACTIVE, Dt2State.ENRAGED)) return
        if (
            !run.enraged &&
                run.leviathanAction == LeviathanAction.STANDARD_VOLLEY &&
                clock.cycle >= run.leviathanRoarCycle
        ) {
            beginLeviathanRoar(run)
        }
        if (run.player.hitpoints > 25) run.leviathanNeverAbove25Hitpoints = false
        if (run.leviathanProjectileEndCycle <= clock.cycle) {
            run.leviathanProjectileEndCycle = Int.MAX_VALUE
            if (run.leviathanAction == LeviathanAction.STANDARD_VOLLEY) run.boss.anim(10281)
        }
        if (run.enraged) {
            tickLeviathanEnrageDebris(run)
            if (clock.cycle >= run.leviathanPathfinderSpawnCycle) {
                spawnLeviathanPathfinder(run)
            }
            tickLeviathanPathfinder(run)
        }
        if (run.leviathanTornadoStarted) tickLeviathanTornado(run)
        when (run.leviathanAction) {
            LeviathanAction.STUNNED -> tickLeviathanStun(run)
            LeviathanAction.ROAR -> {
                if (clock.cycle >= run.specialEndCycle) {
                    if (run.leviathanRetaliationPending) {
                        run.leviathanRetaliationPending = false
                        beginLeviathanSpecial(run, null)
                    } else {
                        finishLeviathanAction(run)
                    }
                }
            }
            LeviathanAction.LIGHTNING_BARRAGE -> {
                tickLeviathanLightning(run)
                if (clock.cycle >= run.specialEndCycle) {
                    Dt2Rules.leviathanSpecialContract(LeviathanSpecial.LIGHTNING_BARRAGE)
                        .endAnimation
                        ?.let(run.boss::anim)
                    finishLeviathanAction(run)
                }
            }
            LeviathanAction.SMOKE_BLAST -> {
                tickLeviathanSmoke(run)
                if (clock.cycle >= run.specialEndCycle) {
                    Dt2Rules.leviathanSpecialContract(LeviathanSpecial.SMOKE_BLAST)
                        .endAnimation
                        ?.let(run.boss::anim)
                    world.spotanimMap(
                        SpotanimType(Dt2Assets.LEVIATHAN_SMOKE_EXPLOSION),
                        run.player.coords,
                    )
                    if (!isShieldedByLeviathanDebris(run)) {
                        val maximum =
                            Dt2Rules.leviathanSmokeMaximumHit(
                                run.difficulty == Dt2Difficulty.AWAKENED
                            )
                        damagePlayer(
                            run,
                            run.rng.nextInt(maximum + 1),
                            0,
                            LeviathanPerfectFailure.SMOKE_BLAST,
                        )
                    }
                    removeCurrentSmokeDebris(run)
                    finishLeviathanAction(run)
                }
            }
            else -> Unit
        }
    }

    private fun tickLeviathanStun(run: Run) {
        if (clock.cycle == run.specialStartCycle + 3) run.boss.anim(10294)
        if (clock.cycle < run.specialEndCycle) return
        finishLeviathanStun(run)
    }

    private fun finishLeviathanStun(run: Run) {
        run.leviathanAction = LeviathanAction.STANDARD_VOLLEY
        run.specialStartCycle = Int.MAX_VALUE
        run.specialEndCycle = Int.MAX_VALUE
        run.boss.actionDelay = clock.cycle + 1
        run.boss.apPlayer2(run.player, interactions)
    }

    private fun beginLeviathanRetaliation(run: Run) {
        run.specialStartCycle = Int.MAX_VALUE
        run.specialEndCycle = Int.MAX_VALUE
        when (val action = Dt2Rules.leviathanRetaliationAction(run.leviathanNextSpecial)) {
            LeviathanSpecial.VOLLEY_ROAR -> {
                run.leviathanRetaliationPending = true
                beginLeviathanRoar(run)
            }
            else -> {
                run.leviathanRetaliationPending = false
                beginLeviathanSpecial(run, action.name)
            }
        }
    }

    private fun activate(run: Run) {
        run.state = Dt2State.ACTIVE
        if (run.bossKind == Dt2Boss.LEVIATHAN) {
            run.leviathanAction = LeviathanAction.STANDARD_VOLLEY
            bossHpBar.onOpen(run.player, run.boss)
            bossHpBar.onUpdate(run.player, run.boss, run.boss.hitpoints, run.boss.baseHitpointsLvl)
            if (run.leviathanMusicRestorePoint == null) {
                run.leviathanMusicRestorePoint =
                    musicPlayer.playTemporaryMidi(run.player, Dt2Assets.LEVIATHAN_MIDI)
            }
            if (!run.leviathanAppearanceSoundPlayed) {
                run.player.soundSynth(Dt2Assets.LEVIATHAN_APPEARANCE_SOUND)
                run.leviathanAppearanceSoundPlayed = true
            }
        }
        run.killStartCycle = clock.cycle
        run.boss.movementLocked = run.bossKind != Dt2Boss.VARDORVIS
        run.boss.apRangeOverride =
            if (run.bossKind == Dt2Boss.VARDORVIS) 1 else Dt2Rules.LEVIATHAN_AP_RANGE
        if (run.bossKind == Dt2Boss.LEVIATHAN) {
            run.boss.maxOpRangeOverride = Dt2Rules.LEVIATHAN_MAX_OP_RANGE
        }
        run.boss.apRequiresLineOfSight =
            run.bossKind !in setOf(Dt2Boss.WHISPERER, Dt2Boss.LEVIATHAN)
        run.nextSpecialCycle =
            if (run.bossKind in setOf(Dt2Boss.WHISPERER, Dt2Boss.LEVIATHAN)) Int.MAX_VALUE
            else clock.cycle + normalSpecialDelay(run)
        run.boss.apPlayer2(run.player, interactions)
    }

    private fun tickActive(run: Run) {
        if (run.bossKind == Dt2Boss.LEVIATHAN) {
            if (run.leviathanAction != LeviathanAction.STANDARD_VOLLEY) return
        }
        if (run.bossKind != Dt2Boss.WHISPERER && clock.cycle >= run.nextSpecialCycle) {
            beginSpecial(run, null)
        }
    }

    private fun beginLeviathanRoar(run: Run) {
        val contract = Dt2Rules.leviathanSpecialContract(LeviathanSpecial.VOLLEY_ROAR)
        run.leviathanAction = LeviathanAction.ROAR
        run.boss.clearInteraction()
        run.boss.anim(contract.startAnimation)
        run.boss.actionDelay = clock.cycle + contract.duration
        run.activeSpecial = LeviathanSpecial.VOLLEY_ROAR
        run.specialStartCycle = clock.cycle
        run.specialEndCycle = clock.cycle + contract.duration
        run.specialProgress = 0
        run.specialRequired = 1
        run.leviathanRoarCycle = Int.MAX_VALUE
        run.player.soundSynth(Dt2Assets.LEVIATHAN_ROAR_SOUND, delay = 5)
        damagePlayer(run, run.rng.nextInt(Dt2Rules.LEVIATHAN_ROAR_MAX_HIT + 1), 0)
        val axis =
            if (run.rng.nextInt(2) == 0) LeviathanDebrisAxis.EAST_WEST
            else LeviathanDebrisAxis.NORTH_SOUTH
        if (run.leviathanNextSpecial == null) {
            run.leviathanNextSpecial = Dt2Rules.leviathanFirstSpecial(axis)
        }
        val blockerTiles = mutableListOf(run.player.coords)
        val candidates =
            leviathanArenaTiles(run).filterNotTo(mutableListOf()) { tile ->
                tile == run.player.coords || tile in run.permanentDebris
            }
        val blockerCount =
            Dt2Rules.leviathanRoarBlockerCount(run.difficulty == Dt2Difficulty.AWAKENED)
        while (blockerTiles.size < blockerCount && candidates.isNotEmpty()) {
            blockerTiles += candidates.removeAt(run.rng.nextInt(candidates.size))
        }
        addLeviathanRoarTransientDebris(run, blockerTiles.toSet())
        blockerTiles.forEachIndexed { index, tile ->
            addLeviathanDebris(
                run,
                tile,
                Dt2Rules.LEVIATHAN_ROAR_BLOCKER_LAND_TICKS,
                Dt2Rules.LEVIATHAN_DEBRIS_MAX_HIT,
                permanent = true,
                angle = LocAngle.entries[index % LocAngle.entries.size],
                smokeSequence = null,
                visualIndex = index,
                warningGraphic =
                    Dt2Assets.LEVIATHAN_DEBRIS[index % Dt2Assets.LEVIATHAN_DEBRIS.size],
                warningDelay = Dt2Rules.LEVIATHAN_ROAR_BLOCKER_CLIENT_DELAY,
                emitImpactGraphic = false,
            )
        }
    }

    private fun leviathanArenaTiles(run: Run): MutableList<CoordGrid> {
        val northWest =
            requireNotNull(instances.localCoord(run.session, Dt2Assets.LEVIATHAN_PATHFINDER_START))
        val center = run.boss.coords.translate(run.boss.size / 2, run.boss.size / 2)
        val southEast = Dt2Rules.leviathanPathfinderCorners(northWest, center)[2]
        val tiles = mutableListOf<CoordGrid>()
        for (x in northWest.x..southEast.x) {
            for (z in southEast.z..northWest.z) {
                val tile = CoordGrid(x, z, northWest.level)
                if (collision.isZoneValid(tile) && !collision.isWalkBlocked(tile)) tiles += tile
            }
        }
        return tiles
    }

    private fun addLeviathanRoarTransientDebris(run: Run, excluded: Set<CoordGrid>) {
        val candidates =
            leviathanArenaTiles(run).filterNotTo(mutableListOf()) {
                it in excluded || it in run.permanentDebris || it == run.player.coords
            }
        val count =
            Dt2Rules.leviathanRoarTransientDebrisCount(run.rng.nextInt(Int.MAX_VALUE))
                .coerceAtMost(candidates.size)
        repeat(count) { index ->
            val tile = candidates.removeAt(run.rng.nextInt(candidates.size))
            val clientDelay =
                Dt2Rules.LEVIATHAN_DEBRIS_CLIENT_DELAYS[
                        run.rng.nextInt(Dt2Rules.LEVIATHAN_DEBRIS_CLIENT_DELAYS.size)]
            val graphic =
                if (run.rng.nextInt(2) == 0) Dt2Assets.LEVIATHAN_DEBRIS_WARNING
                else Dt2Assets.LEVIATHAN_DEBRIS_TRANSIENT
            world.spotanimMap(SpotanimType(graphic), tile, delay = clientDelay)
            run.pendingLeviathanDebris +=
                PendingLeviathanDebris(
                    tile = tile,
                    impactCycle = clock.cycle + Dt2Rules.leviathanDebrisImpactTicks(clientDelay),
                    damage = run.rng.nextInt(Dt2Rules.LEVIATHAN_DEBRIS_MAX_HIT + 1),
                    permanent = false,
                    angle = LocAngle.North,
                    smokeSequence = null,
                    visualIndex = index,
                    generation = run.generation,
                    emitImpactGraphic = false,
                )
        }
    }

    private fun tickLeviathanEnrageDebris(run: Run) {
        if (clock.cycle >= run.leviathanEnrageDebrisCycle) {
            val pendingBlockers =
                run.pendingLeviathanDebris
                    .filterTo(linkedSetOf()) { it.permanent }
                    .mapTo(linkedSetOf()) { it.tile }
            addLeviathanRoarTransientDebris(run, run.permanentDebris + pendingBlockers)
            run.leviathanEnrageDebrisCycle =
                clock.cycle + Dt2Rules.leviathanEnrageDebrisInterval(run.rng.nextInt(Int.MAX_VALUE))
        }
        if (clock.cycle >= run.leviathanEnragePersistentDebrisCycle) {
            addLeviathanEnrageBlockers(run)
            run.leviathanEnragePersistentDebrisCycle =
                clock.cycle + Dt2Rules.LEVIATHAN_ENRAGE_PERSISTENT_DEBRIS_INTERVAL
        }
    }

    private fun addLeviathanEnrageBlockers(run: Run) {
        val occupied =
            run.permanentDebris +
                run.pendingLeviathanDebris.filter { it.permanent }.mapTo(linkedSetOf()) { it.tile }
        val candidates =
            leviathanArenaTiles(run).filterTo(mutableListOf()) { tile ->
                tile != run.player.coords && tile !in occupied
            }
        while (candidates.isNotEmpty()) {
            val anchor = candidates.removeAt(run.rng.nextInt(candidates.size))
            val tiles =
                if (run.difficulty == Dt2Difficulty.AWAKENED) {
                    listOf(
                        anchor,
                        anchor.translate(-1, 0),
                        anchor.translate(1, 0),
                        anchor.translate(0, -1),
                        anchor.translate(0, 1),
                    )
                } else {
                    listOf(anchor)
                }
            if (
                tiles.any {
                    it == run.player.coords ||
                        it in occupied ||
                        !collision.isZoneValid(it) ||
                        collision.isWalkBlocked(it)
                }
            ) {
                continue
            }
            val axis =
                if (run.rng.nextInt(2) == 0) LeviathanDebrisAxis.EAST_WEST
                else LeviathanDebrisAxis.NORTH_SOUTH
            tiles.forEachIndexed { index, tile ->
                addLeviathanDebris(
                    run,
                    tile,
                    Dt2Rules.LEVIATHAN_ENRAGE_PERSISTENT_DEBRIS_LAND_TICKS,
                    Dt2Rules.LEVIATHAN_DEBRIS_MAX_HIT,
                    permanent = true,
                    angle =
                        if (axis == LeviathanDebrisAxis.EAST_WEST) LocAngle.North
                        else LocAngle.West,
                    smokeSequence = null,
                    visualIndex = index,
                )
            }
            return
        }
    }

    private fun spawnLeviathanPathfinder(run: Run) {
        if (run.leviathanPathfinder != null || runs[run.player.uid] !== run) return
        val northWest =
            requireNotNull(instances.localCoord(run.session, Dt2Assets.LEVIATHAN_PATHFINDER_START))
        val center = run.boss.coords.translate(run.boss.size / 2, run.boss.size / 2)
        val corners = Dt2Rules.leviathanPathfinderCorners(northWest, center)
        val start =
            corners.firstOrNull { collision.isZoneValid(it) && !collision.isWalkBlocked(it) }
                ?: northWest
        val route =
            Dt2Rules.leviathanClockwisePath(corners).filter {
                collision.isZoneValid(it) && !collision.isWalkBlocked(it)
            }
        val pathfinder =
            spawnHelper(run, Dt2Assets.LEVIATHAN_PATHFINDER, start, movementLocked = false)
        pathfinder.anim(10296)
        run.leviathanPathfinder = pathfinder
        run.leviathanPathfinderRoute.clear()
        run.leviathanPathfinderRoute += if (route.isEmpty()) listOf(northWest) else route
        run.leviathanPathfinderRouteIndex =
            run.leviathanPathfinderRoute.indexOf(start).coerceAtLeast(0)
        run.leviathanPathfinderSpawnCycle = Int.MAX_VALUE
    }

    private fun tickLeviathanPathfinder(run: Run) {
        val pathfinder = run.leviathanPathfinder ?: return
        if (!pathfinder.isSlotAssigned || run.leviathanPathfinderRoute.isEmpty()) return
        pathfinder.movementLocked = false
        val target = run.leviathanPathfinderRoute[run.leviathanPathfinderRouteIndex]
        if (pathfinder.coords == target) {
            run.leviathanPathfinderRouteIndex =
                (run.leviathanPathfinderRouteIndex + 1) % run.leviathanPathfinderRoute.size
        }
        pathfinder.walk(run.leviathanPathfinderRoute[run.leviathanPathfinderRouteIndex])
    }

    private fun isInsideLeviathanPathfinder(run: Run): Boolean {
        val pathfinder = run.leviathanPathfinder
        val geometricInside =
            pathfinder?.let {
                Dt2Rules.leviathanInsidePathfinder(
                    run.player.coords,
                    it.coords,
                    run.difficulty == Dt2Difficulty.AWAKENED,
                )
            } ?: false
        return Dt2Rules.leviathanInsidePathfinderOrTransition(
            run.enraged,
            pathfinder != null,
            geometricInside,
        )
    }

    private fun beginSpecial(run: Run, requested: String?) {
        if (run.bossKind == Dt2Boss.DUKE) {
            run.player.mes("Duke's specials follow his standard attack counters.")
            return
        }
        if (run.bossKind == Dt2Boss.LEVIATHAN) {
            beginLeviathanSpecial(run, requested)
            return
        }
        cleanupTransientHazards(run)
        run.state = Dt2State.SPECIAL
        run.boss.clearInteraction()
        run.boss.actionDelay = clock.cycle + 12
        run.specialStartCycle = clock.cycle
        run.specialProgress = 0
        run.specialRequired = 1
        when (run.bossKind) {
            Dt2Boss.WHISPERER -> beginWhispererSpecial(run, requested)
            Dt2Boss.VARDORVIS -> return
            Dt2Boss.LEVIATHAN -> beginLeviathanSpecial(run, requested)
            Dt2Boss.DUKE -> Unit
        }
    }

    private fun beginWhispererSpecial(run: Run, requested: String?) {
        val special =
            requested?.let { key ->
                WhispererSpecial.entries.firstOrNull { it.name.equals(key, true) }
            } ?: Dt2Rules.whispererSpecial(run.specialIndex, (run.seed and 1L).toInt())
        run.activeSpecial = special
        run.specialIndex++
        run.whispererSpecialPerfect = true
        run.boss.anim(
            when (special) {
                WhispererSpecial.CORRUPTED_SEEDS -> 10254
                WhispererSpecial.SCREECH -> 10250
                WhispererSpecial.SOUL_SIPHON -> 10256
            }
        )
        when (special) {
            WhispererSpecial.CORRUPTED_SEEDS -> {
                run.specialRequired = Dt2Rules.whispererHealthySeedTiles.size
                run.specialEndCycle = clock.cycle + 22
                Dt2Rules.whispererSeedTiles.forEach { local ->
                    val tile = whispererLocal(run, local)
                    val healthy = local in Dt2Rules.whispererHealthySeedTiles
                    val loc = spawnWhispererLoc(run, tile, Dt2Assets.WHISPERER_SEED_REGULAR)
                    run.whispererSeeds[tile] = WhispererSeedState(tile, healthy, loc)
                    world.spotanimMap(SpotanimType(2459), tile)
                }
                run.player.mes("Leeches begin to grow throughout the cavern...")
                run.player.mes("Your Blackstone fragment pulses with dark energy.")
            }
            WhispererSpecial.SCREECH -> {
                run.specialEndCycle = clock.cycle + 30
                Dt2Rules.whispererScreechPillars.forEach { local ->
                    val tile = whispererLocal(run, local)
                    val loc = spawnWhispererLoc(run, tile, Dt2Assets.WHISPERER_SCREECH_PILLAR)
                    val helper = spawnHelper(run, Dt2Assets.WHISPERER_SCREECH_SAFE, tile)
                    run.whispererScreechPillars +=
                        WhispererScreechPillar(
                            tile,
                            Dt2Rules.whispererScreechStrength(local),
                            loc,
                            helper,
                        )
                }
                run.player.mes("The Whisperer prepares to let out a powerful screech...")
            }
            WhispererSpecial.SOUL_SIPHON -> {
                run.specialRequired = 1
                run.specialEndCycle = clock.cycle + 20
                run.whispererSoulKills.clear()
                run.whispererCompletedSoulGroups.clear()
                Dt2Rules.whispererSoulTiles.forEach { (local, group) ->
                    val soul =
                        spawnHelper(run, Dt2Assets.WHISPERER_SOUL, whispererLocal(run, local))
                    soul.anim(9047)
                    run.whispererSoulGroups[soul] = group
                }
                run.player.mes("The Whisperer begins to call upon the lost...")
                run.player.mes("Enter the Shadow Realm and manipulate one complete chant.")
            }
        }
    }

    private fun beginLeviathanSpecial(run: Run, requested: String?) {
        val special =
            requested?.let { key ->
                LeviathanSpecial.entries.firstOrNull { it.name.equals(key, true) }
            } ?: run.leviathanNextSpecial
        if (special == null) {
            beginLeviathanRoar(run)
            return
        }
        if (special == LeviathanSpecial.VOLLEY_ROAR) {
            beginLeviathanRoar(run)
            return
        }
        run.activeSpecial = special
        run.specialStartCycle = clock.cycle
        run.specialProgress = 0
        run.specialRequired = 1
        if (special == LeviathanSpecial.SMOKE_BLAST) {
            run.leviathanSmokeSequence++
            removeCurrentSmokeDebris(run)
        }
        if (special == LeviathanSpecial.LIGHTNING_BARRAGE) {
            run.leviathanCurrentTrailTile = run.player.coords
        }
        run.leviathanAction =
            when (special) {
                LeviathanSpecial.LIGHTNING_BARRAGE -> LeviathanAction.LIGHTNING_BARRAGE
                LeviathanSpecial.SMOKE_BLAST -> LeviathanAction.SMOKE_BLAST
                LeviathanSpecial.VOLLEY_ROAR -> LeviathanAction.ROAR
            }
        val contract = Dt2Rules.leviathanSpecialContract(special)
        run.specialEndCycle = clock.cycle + contract.duration
        run.boss.clearInteraction()
        run.boss.actionDelay = run.specialEndCycle
        run.boss.anim(contract.startAnimation)
        when (special) {
            LeviathanSpecial.LIGHTNING_BARRAGE -> {
                run.player.mes("Lightning sweeps around the arena. Keep moving around the pool!")
            }
            LeviathanSpecial.SMOKE_BLAST -> {
                run.player.mes("The Leviathan spits debris. Take cover before the smoke blast!")
            }
            LeviathanSpecial.VOLLEY_ROAR -> Unit
        }
    }

    private fun tickSpecial(run: Run) {
        if (run.bossKind == Dt2Boss.WHISPERER) tickWhispererSpecial(run)
        when (run.activeSpecial) {
            LeviathanSpecial.LIGHTNING_BARRAGE -> {
                tickLeviathanLightning(run)
                if (clock.cycle >= run.specialEndCycle) {
                    Dt2Rules.leviathanSpecialContract(LeviathanSpecial.LIGHTNING_BARRAGE)
                        .endAnimation
                        ?.let(run.boss::anim)
                    finishSpecial(run)
                }
                return
            }
            LeviathanSpecial.SMOKE_BLAST -> {
                tickLeviathanSmoke(run)
                if (clock.cycle >= run.specialEndCycle) {
                    Dt2Rules.leviathanSpecialContract(LeviathanSpecial.SMOKE_BLAST)
                        .endAnimation
                        ?.let(run.boss::anim)
                    world.spotanimMap(
                        SpotanimType(Dt2Assets.LEVIATHAN_SMOKE_EXPLOSION),
                        run.player.coords,
                    )
                    if (!isShieldedByLeviathanDebris(run)) damagePlayer(run, 39, 0)
                    finishSpecial(run)
                }
                return
            }
            else -> Unit
        }
        if (run.specialProgress >= run.specialRequired) {
            finishSpecial(run)
            return
        }
        if (clock.cycle < run.specialEndCycle) return
        when (run.activeSpecial) {
            WhispererSpecial.CORRUPTED_SEEDS -> damagePlayer(run, 75, 75)
            WhispererSpecial.SCREECH -> Unit
            WhispererSpecial.SOUL_SIPHON -> {
                damagePlayer(run, 50, 0)
                run.boss.heal(100)
            }
            else -> Unit
        }
        finishSpecial(run)
    }

    private fun finishSpecial(run: Run) {
        val completed = run.activeSpecial
        if (
            completed == WhispererSpecial.CORRUPTED_SEEDS &&
                run.specialProgress >= run.specialRequired &&
                run.whispererSpecialPerfect
        ) {
            setSanity(run, run.sanity + 15)
        }
        cleanupTransientHazards(run)
        if (run.bossKind == Dt2Boss.WHISPERER && !run.enraged) setShadowRealm(run, false)
        run.activeSpecial = null
        run.specialProgress = 0
        run.specialRequired = 0
        run.state = if (run.enraged) Dt2State.ENRAGED else Dt2State.ACTIVE
        run.nextSpecialCycle =
            if (run.bossKind == Dt2Boss.WHISPERER) Int.MAX_VALUE
            else if (completed != LeviathanSpecial.VOLLEY_ROAR)
                clock.cycle + normalSpecialDelay(run)
            else run.nextSpecialCycle
        if (run.bossKind == Dt2Boss.WHISPERER && !run.enraged) {
            run.boss.transmog(requireNpc(Dt2Assets.WHISPERER_MELEE_ID), Int.MAX_VALUE)
            run.boss.movementLocked = false
            run.boss.apRangeOverride = 1
            run.whispererMeleeEndCycle = clock.cycle + 13
        }
        run.boss.actionDelay = clock.cycle + 2
        run.boss.apPlayer2(run.player, interactions)
    }

    private fun tickLeviathanLightning(run: Run) {
        val elapsed = clock.cycle - run.specialStartCycle
        if (elapsed !in 1..12) return
        if (elapsed == 3) {
            Dt2Rules.leviathanSpecialContract(LeviathanSpecial.LIGHTNING_BARRAGE)
                .loopAnimation
                ?.let(run.boss::anim)
        }
        val impactTicks = Dt2Rules.leviathanLightningImpactTicks(run.player.hitpoints)
        val damageMaximum =
            Dt2Rules.leviathanLightningMaximumHit(run.difficulty == Dt2Difficulty.AWAKENED)
        val currentTiles =
            Dt2Rules.leviathanLightningTrail(run.leviathanCurrentTrailTile, run.player.coords)
        run.leviathanCurrentTrailTile = run.player.coords
        currentTiles.forEach { currentTile ->
            world.spotanimMap(SpotanimType(Dt2Assets.LEVIATHAN_CURRENT_CHARGE), currentTile)
            world.spotanimMap(
                SpotanimType(Dt2Assets.LEVIATHAN_CURRENT_IMPACT),
                currentTile,
                delay = impactTicks * 30,
            )
            run.dangerTiles +=
                DangerTile(
                    currentTile,
                    clock.cycle + impactTicks,
                    run.rng.nextInt(damageMaximum + 1),
                    0,
                    run.generation,
                    perfectFailure = LeviathanPerfectFailure.LIGHTNING_CURRENT,
                )
        }

        val northWest =
            requireNotNull(instances.localCoord(run.session, Dt2Assets.LEVIATHAN_PATHFINDER_START))
        val center = run.boss.coords.translate(run.boss.size / 2, run.boss.size / 2)
        val southEast = Dt2Rules.leviathanPathfinderCorners(northWest, center)[2]
        val strikeTiles =
            leviathanArenaTiles(run).filter {
                it !in currentTiles &&
                    it !in run.permanentDebris &&
                    Dt2Rules.leviathanLightningTileAllowed(
                        it,
                        run.boss.coords,
                        run.boss.size,
                        northWest,
                        southEast,
                    )
            }
        if (strikeTiles.isEmpty()) return
        val strikeTile = strikeTiles[run.rng.nextInt(strikeTiles.size)]
        world.spotanimMap(SpotanimType(Dt2Assets.LEVIATHAN_LIGHTNING_CHARGE), strikeTile)
        world.projAnim(
            ProjAnim(
                spotanim = Dt2Assets.LEVIATHAN_LIGHTNING_PROJECTILE,
                startHeight = 250,
                endHeight = 0,
                startTime = 0,
                endTime = impactTicks * 30,
                angle = 30,
                progress = 64,
                sourceIndex = run.boss.slotId + 1,
                targetIndex = 0,
                startCoord = center,
                endCoord = strikeTile,
            )
        )
        world.spotanimMap(
            SpotanimType(Dt2Assets.LEVIATHAN_LIGHTNING_IMPACT),
            strikeTile,
            delay = impactTicks * 30,
        )
        run.dangerTiles +=
            DangerTile(
                strikeTile,
                clock.cycle + impactTicks,
                run.rng.nextInt(damageMaximum + 1),
                0,
                run.generation,
                perfectFailure = LeviathanPerfectFailure.LIGHTNING_CURRENT,
            )
    }

    private fun tickLeviathanSmoke(run: Run) {
        val elapsed = clock.cycle - run.specialStartCycle
        if (elapsed !in 1..10) return
        if (elapsed == 2) run.boss.anim(10289)
        addLeviathanDebris(
            run,
            run.player.coords,
            delay = 2,
            damage = run.rng.nextInt(Dt2Rules.LEVIATHAN_DEBRIS_MAX_HIT + 1),
            permanent = true,
            angle = LocAngle.entries[run.rng.nextInt(LocAngle.entries.size)],
            smokeSequence = run.leviathanSmokeSequence,
            visualIndex = elapsed - 1,
        )
    }

    private fun addLeviathanDebris(
        run: Run,
        tile: CoordGrid,
        delay: Int,
        damage: Int,
        permanent: Boolean,
        angle: LocAngle,
        smokeSequence: Int?,
        visualIndex: Int,
        warningGraphic: Int = Dt2Assets.LEVIATHAN_DEBRIS_WARNING,
        warningDelay: Int = 0,
        emitImpactGraphic: Boolean = true,
    ) {
        world.spotanimMap(SpotanimType(warningGraphic), tile, delay = warningDelay)
        run.pendingLeviathanDebris +=
            PendingLeviathanDebris(
                tile = tile,
                impactCycle = clock.cycle + delay,
                damage = damage,
                permanent = permanent,
                angle = angle,
                smokeSequence = smokeSequence,
                visualIndex = visualIndex,
                generation = run.generation,
                emitImpactGraphic = emitImpactGraphic,
            )
    }

    private fun tickLeviathanDebris(run: Run) {
        var landedThisTick = false
        val iterator = run.pendingLeviathanDebris.iterator()
        while (iterator.hasNext()) {
            val pending = iterator.next()
            if (pending.generation != run.generation || runs[run.player.uid] !== run) {
                iterator.remove()
                continue
            }
            if (clock.cycle < pending.impactCycle) continue
            val graphic =
                Dt2Assets.LEVIATHAN_DEBRIS[
                        Math.floorMod(pending.visualIndex, Dt2Assets.LEVIATHAN_DEBRIS.size)]
            if (pending.emitImpactGraphic) world.spotanimMap(SpotanimType(graphic), pending.tile)
            landedThisTick = true
            if (run.player.coords == pending.tile) {
                damagePlayer(run, pending.damage, 0, LeviathanPerfectFailure.FALLING_DEBRIS)
            }
            if (pending.permanent && !collision.isWalkBlocked(pending.tile)) {
                val loc =
                    locs.add(
                        pending.tile,
                        requireNotNull(
                            ServerCacheManager.getObject(Dt2Assets.LEVIATHAN_DEBRIS_BLOCKER)
                        ),
                        Int.MAX_VALUE,
                        pending.angle,
                        LocShape.CentrepieceStraight,
                    )
                run.leviathanDebrisLocs[pending.tile] = loc
                run.permanentDebris += pending.tile
                if (pending.smokeSequence == run.leviathanSmokeSequence) {
                    run.currentSmokeDebris += pending.tile
                }
            }
            iterator.remove()
        }
        if (landedThisTick) {
            run.player.soundSynth(Dt2Assets.LEVIATHAN_DEBRIS_LAND_SOUND)
            run.player.soundSynth(Dt2Assets.LEVIATHAN_DEBRIS_RUMBLE_SOUND)
            shakeLeviathanCamera(run)
        }
    }

    private fun shakeLeviathanCamera(run: Run) {
        if (run.leviathanCameraLastShakeCycle == clock.cycle) return
        run.leviathanCameraLastShakeCycle = clock.cycle
        repeat(3) { axis -> Camera.camShake(run.player, axis, 5 + run.rng.nextInt(4), 0, 0) }
        run.leviathanCameraResetCycle = clock.cycle + LEVIATHAN_CAMERA_SHAKE_TICKS
    }

    private fun removeCurrentSmokeDebris(run: Run) {
        run.currentSmokeDebris.toList().forEach { tile ->
            run.leviathanDebrisLocs.remove(tile)?.let { loc -> locs.del(loc, Int.MAX_VALUE) }
            run.permanentDebris.remove(tile)
            world.spotanimMap(SpotanimType(Dt2Assets.LEVIATHAN_DEBRIS_BREAK), tile)
        }
        run.currentSmokeDebris.clear()
    }

    private fun isShieldedByLeviathanDebris(run: Run): Boolean {
        val bossCenter = run.boss.coords.translate(run.boss.size / 2, run.boss.size / 2)
        return run.currentSmokeDebris.any { debris ->
            Dt2Rules.leviathanDebrisBlocksLine(bossCenter, run.player.coords, debris)
        }
    }

    private fun finishLeviathanAction(run: Run) {
        val completed = run.activeSpecial as? LeviathanSpecial
        if (
            completed == LeviathanSpecial.LIGHTNING_BARRAGE ||
                completed == LeviathanSpecial.SMOKE_BLAST
        ) {
            run.leviathanLastSpecial = completed
            run.leviathanNextSpecial = Dt2Rules.leviathanNextSpecial(completed)
            run.specialIndex++
        }
        run.activeSpecial = null
        run.specialProgress = 0
        run.specialRequired = 0
        run.specialStartCycle = Int.MAX_VALUE
        run.specialEndCycle = Int.MAX_VALUE
        run.leviathanAction = LeviathanAction.STANDARD_VOLLEY
        if (!run.enraged && completed != LeviathanSpecial.VOLLEY_ROAR) {
            run.nextSpecialCycle = clock.cycle + normalSpecialDelay(run)
        }
        run.boss.actionDelay = clock.cycle + 2
        run.boss.apPlayer2(run.player, interactions)
    }

    private fun attackWhisperer(access: StandardNpcAccess, run: Run) {
        if (run.boss.visType.id == Dt2Assets.WHISPERER_MELEE_ID) {
            val protected = run.player.vars["varbit.prayer_protectfrommelee"] == 1
            combat.attack(
                access,
                run.player,
                CombatAttack.NpcMelee(MeleeAttackType.Crush),
                NpcAttackOverride(
                    attackAnimation = animation(10234),
                    maxHit =
                        Dt2Rules.whispererMaximumHit(
                            Dt2AttackStyle.MELEE,
                            run.difficulty == Dt2Difficulty.AWAKENED,
                        ),
                    attackRate = 2,
                    hitModifier =
                        NpcPartialProtectionPrayerPlayerHitModifier(if (protected) 50 else 0),
                    onAttack = {
                        run.pendingWhispererShots +=
                            PendingWhispererShot(
                                Dt2AttackStyle.MELEE,
                                clock.cycle + 1,
                                projectile = false,
                            )
                    },
                ),
            )
            return
        }
        val volley = Dt2Rules.whispererVolley(run.attackCounter, run.specialIndex, run.enraged)
        val style = volley.styles.first()
        val protected = whispererProtected(run, style)
        combat.attack(
            access,
            run.player,
            if (style == Dt2AttackStyle.MAGIC)
                CombatAttack.NpcMagic(
                    Dt2Rules.whispererMaximumHit(style, run.difficulty == Dt2Difficulty.AWAKENED)
                )
            else CombatAttack.NpcRanged(RangedAttackType.Standard),
            NpcAttackOverride(
                attackAnimation = animation(if (style == Dt2AttackStyle.MAGIC) 10243 else 10237),
                maxHit =
                    Dt2Rules.whispererMaximumHit(style, run.difficulty == Dt2Difficulty.AWAKENED),
                projectile =
                    NpcProjectile(
                        spotanimName(Dt2Rules.whispererProjectile(style)),
                        WHISPERER_PROJECTILE_TYPE,
                    ),
                impactDelay = 2,
                attackRate = volley.attackRate,
                hitModifier = NpcCalculatedProtectionPrayerPlayerHitModifier(protected, 100),
                onAttack = {
                    volley.styles.drop(1).forEachIndexed { index, followUp ->
                        run.pendingWhispererShots +=
                            PendingWhispererShot(followUp, clock.cycle + index + 1)
                    }
                    run.attackCounter++
                    if (run.enraged || run.specialIndex >= 2) spawnTentaclePattern(run)
                },
                onImpact = { _, _ ->
                    if (style == Dt2AttackStyle.MAGIC)
                        run.player.spotanim(Dt2Assets.WHISPERER_MAGIC_IMPACT)
                },
            ),
        )
    }

    private fun activateVardorvis(run: Run) {
        run.state = Dt2State.ACTIVE
        run.killStartCycle = clock.cycle
        run.nextSpecialCycle = clock.cycle + Dt2Rules.vardorvisSpecialDelay(enraged = false)
        run.boss.movementLocked = false
        run.boss.apRangeOverride = 1
        updateVardorvisStats(run)
        setVardorvisExitLocked(run, locked = true)
        bossHpBar.onOpen(run.player, run.boss)
        bossHpBar.onUpdate(run.player, run.boss, run.boss.hitpoints, run.maximumHp)
        run.boss.apPlayer2(run.player, interactions)
        run.vardorvisNextPathCheckCycle = clock.cycle + 5
        run.player.mes("Vardorvis awakens.")
    }

    private fun tickVardorvis(run: Run) {
        if (run.rootEndCycle > clock.cycle) run.player.clearInteraction()
        if (run.prayerUnlockCycle > clock.cycle) clearProtectionPrayers(run.player)
        tickVardorvisAxes(run)
        tickVardorvisDarts(run)
        tickVardorvisHead(run)
        tickVardorvisStrangle(run)
        tickVardorvisBleed(run)
        when (run.state) {
            Dt2State.RESPAWNING -> if (clock.cycle >= run.respawnCycle) respawnVardorvis(run)
            Dt2State.ACTIVE,
            Dt2State.ENRAGED -> {
                updateVardorvisStats(run)
                keepAggressive(run)
                tickVardorvisRouteFallback(run)
                if (!run.vardorvisQteOpen && clock.cycle >= run.nextSpecialCycle) {
                    beginVardorvisMechanic(run, null)
                }
            }
            else -> Unit
        }
        run.lastPlayerTile = run.player.coords
    }

    private fun updateVardorvisStats(run: Run) {
        val strength = Dt2Rules.vardorvisStrengthLevel(run.boss.hitpoints)
        val defence = Dt2Rules.vardorvisDefenceLevel(run.boss.hitpoints)
        run.boss.baseStrengthLvl = strength
        run.boss.strengthLvl = strength
        run.boss.baseDefenceLvl = defence
        run.boss.defenceLvl = defence
    }

    fun canAttackVardorvis(npc: Npc, target: Player): Boolean =
        runs[target.uid]?.let {
            it.bossKind == Dt2Boss.VARDORVIS &&
                it.boss === npc &&
                it.state in setOf(Dt2State.ACTIVE, Dt2State.ENRAGED)
        } == true

    /** Computes per-launch parameters; BossSpec and NvPCombat own selection, launch and cadence. */
    fun vardorvisAttackParameters(npc: Npc, target: Player): NpcAttackOverride {
        val run = requireNotNull(runs[target.uid])
        check(run.bossKind == Dt2Boss.VARDORVIS && run.boss === npc)
        return NpcAttackOverride(
            maxHit = Dt2Rules.vardorvisNormalMaxHit(run.boss.hitpoints),
            onAttack = {
                run.attackCounter++
                if (run.vardorvisEnragePending && !run.enraged) {
                    run.enraged = true
                    run.state = Dt2State.ENRAGED
                    run.vardorvisEnragePending = false
                    run.player.mes("Vardorvis enters an enraged frenzy!")
                }
                if (run.attackCounter % Dt2Rules.VARDORVIS_AXE_ATTACK_INTERVAL == 0) {
                    beginVardorvisAxes(run)
                }
            },
            onImpact = { _, impact -> healVardorvis(run, impact.damage) },
        )
    }

    private fun tickVardorvisRouteFallback(run: Run) {
        if (
            clock.cycle < run.vardorvisNextPathCheckCycle ||
                run.vardorvisDashSteps.isNotEmpty() ||
                run.vardorvisQteOpen
        ) {
            return
        }
        run.vardorvisNextPathCheckCycle = clock.cycle + 5
        val route = routeFactory.create(run.boss.avatar, run.player.avatar)
        val final = route.lastOrNull()?.let { CoordGrid(it.x, it.z, it.level) } ?: run.boss.coords
        if (route.success || distanceToFootprint(final, run.boss.size, run.player.coords) <= 1) {
            return
        }
        if (run.rng.nextInt(2) == 0) {
            val destination = vardorvisAdjacentDestination(run, run.boss.coords) ?: return
            performVardorvisDash(run, destination)
        } else {
            run.vardorvisDarts +=
                VardorvisDart(
                    tile = run.player.coords,
                    warningCycle = clock.cycle,
                    impactCycle = clock.cycle + 3,
                    wave = 0,
                )
        }
        run.boss.actionDelay = max(run.boss.actionDelay, clock.cycle) + 1
    }

    private fun distanceToFootprint(origin: CoordGrid, size: Int, target: CoordGrid): Int {
        val maxX = origin.x + size - 1
        val maxZ = origin.z + size - 1
        val dx =
            when {
                target.x < origin.x -> origin.x - target.x
                target.x > maxX -> target.x - maxX
                else -> 0
            }
        val dz =
            when {
                target.z < origin.z -> origin.z - target.z
                target.z > maxZ -> target.z - maxZ
                else -> 0
            }
        return max(dx, dz)
    }

    private fun vardorvisAdjacentDestination(run: Run, from: CoordGrid): CoordGrid? {
        val player = run.player.coords
        return listOf(
                player.translate(-2, -1),
                player.translate(1, -1),
                player.translate(1, 1),
                player.translate(-2, 1),
            )
            .filter { it != from && isVardorvisFootprintClear(it, run.boss.size) }
            .minByOrNull { max(abs(it.x - from.x), abs(it.z - from.z)) }
    }

    private fun isVardorvisFootprintClear(origin: CoordGrid, size: Int): Boolean =
        (0 until size).all { dx ->
            (0 until size).all { dz ->
                val tile = origin.translate(dx, dz)
                collision.isZoneValid(tile) && !collision.isWalkBlocked(tile)
            }
        }

    private fun performVardorvisDash(run: Run, destination: CoordGrid) {
        val start = run.boss.coords
        if (start == destination) {
            run.boss.anim(Dt2Assets.VARDORVIS_DASH_ANIM)
            return
        }
        run.boss.anim(Dt2Assets.VARDORVIS_DASH_ANIM)
        PathingEntityCommon.exactMove(
            run.boss,
            start,
            destination,
            delay1 = 0,
            delay2 = 30,
            dir = exactMoveAngle(start, destination),
            collision = collision,
        )
    }

    private fun exactMoveAngle(start: CoordGrid, end: CoordGrid): Int {
        val dx = end.x - start.x
        val dz = end.z - start.z
        if (dx == 0 && dz == 0) return 0
        return (1024 + (atan2(dx.toDouble(), dz.toDouble()) * 325.94932345220167).roundToInt()) and
            2047
    }

    private fun beginVardorvisMechanic(run: Run, requested: String?) {
        val explicit =
            requested?.let { key ->
                VardorvisSpecial.entries.firstOrNull { it.name.equals(key, true) }
            }
        val selected =
            explicit
                ?: Dt2Rules.vardorvisSelectSpecial(
                    run.rng.nextInt(Int.MAX_VALUE),
                    run.vardorvisPreviousSpecial,
                    Dt2Rules.vardorvisHeadUnlocked(run.boss.hitpoints),
                )
        val special =
            if (
                selected == VardorvisSpecial.STRANGLE &&
                    run.vardorvisPreviousSpecial == VardorvisSpecial.STRANGLE
            ) {
                VardorvisSpecial.DARTING_SPIKES
            } else selected
        run.vardorvisPreviousSpecial = special
        run.activeSpecial = special
        run.nextSpecialCycle =
            clock.cycle + Dt2Rules.vardorvisSpecialDelay(run.enraged) + run.rng.nextInt(3)
        when (special) {
            VardorvisSpecial.AXES -> beginVardorvisAxes(run)
            VardorvisSpecial.DARTING_SPIKES -> beginVardorvisDarts(run)
            VardorvisSpecial.HEAD_GAZE ->
                if (Dt2Rules.vardorvisHeadUnlocked(run.boss.hitpoints)) {
                    beginVardorvisHead(run)
                } else beginVardorvisDarts(run)
            VardorvisSpecial.STRANGLE ->
                if (Dt2Rules.vardorvisHeadUnlocked(run.boss.hitpoints)) {
                    beginVardorvisStrangle(run)
                } else beginVardorvisDarts(run)
        }
    }

    private fun beginVardorvisAxes(run: Run) {
        val count = Dt2Rules.vardorvisAxeCount(run.boss.hitpoints, awakened = false)
        val lanes = Dt2Rules.vardorvisAxeLanes()
        val states = mutableListOf<VardorvisAxeState>()
        repeat(count) { offset ->
            val lane = lanes[(run.vardorvisAxeRotation + offset) % lanes.size]
            val origin = vardorvisTile(run, lane.originX, lane.originZ)
            val tendril = spawnHelper(run, Dt2Assets.VARDORVIS_LARGE_TENDRIL, origin)
            tendril.anim(10364)
            states +=
                VardorvisAxeState(
                    origin,
                    vardorvisTile(run, lane.movingStartX, lane.movingStartZ),
                    Dt2Rules.vardorvisAxeMovementTiles(lane).map { (x, z) ->
                        vardorvisTile(run, x, z)
                    },
                    tendril,
                )
        }
        run.vardorvisAxeRotation = (run.vardorvisAxeRotation + count) % lanes.size
        run.vardorvisAxeSets += VardorvisAxeSet(clock.cycle, states)
    }

    private fun tickVardorvisAxes(run: Run) {
        val setIterator = run.vardorvisAxeSets.iterator()
        while (setIterator.hasNext()) {
            val set = setIterator.next()
            val elapsed = clock.cycle - set.startCycle
            set.lanes.forEach { axe ->
                if (elapsed == 1) axe.tendril?.anim(10365)
                if (elapsed == 3 && axe.moving == null) {
                    axe.tendril?.let { removeHelper(run, it) }
                    axe.tendril = null
                    axe.static = spawnHelper(run, Dt2Assets.VARDORVIS_STATIC_AXE, axe.origin)
                    axe.static?.anim(10336)
                    axe.moving = spawnHelper(run, Dt2Assets.VARDORVIS_AXE, axe.movingStart)
                    axe.moving?.anim(10366)
                }
                if (elapsed in 3..7) {
                    val step = elapsed - 3
                    val from = axe.lastMovingTile
                    val destination = axe.movementTiles[step]
                    axe.moving?.let { moving ->
                        PathingEntityCommon.exactMove(
                            moving,
                            from,
                            destination,
                            delay1 = 0,
                            delay2 = 30,
                            dir = exactMoveAngle(from, destination),
                            collision = collision,
                        )
                    }
                    if (
                        !axe.hit &&
                            Dt2Rules.vardorvisAxeSweptHit(
                                playerX = run.player.coords.x,
                                playerZ = run.player.coords.z,
                                fromX = from.x,
                                fromZ = from.z,
                                toX = destination.x,
                                toZ = destination.z,
                            )
                    ) {
                        axe.hit = true
                        val rolled = 1 + run.rng.nextInt(Dt2Rules.VARDORVIS_AXE_MAX_HIT)
                        val protected = run.player.vars["varbit.prayer_protectfrommelee"] == 1
                        damageVardorvisPlayer(
                            run,
                            if (protected) Dt2Rules.protectedDamage(rolled, 50) else rolled,
                        ) {
                            applyVardorvisBleed(run)
                        }
                    }
                    axe.lastMovingTile = destination
                }
                if (elapsed >= 8) {
                    axe.moving?.let { removeHelper(run, it) }
                    axe.static?.let { removeHelper(run, it) }
                    axe.moving = null
                    axe.static = null
                }
            }
            if (elapsed >= 8) {
                setIterator.remove()
                if (run.enraged && run.state in setOf(Dt2State.ACTIVE, Dt2State.ENRAGED)) {
                    run.vardorvisNextEnragedAxeCycle = clock.cycle + 1
                }
            }
        }
        if (
            run.enraged &&
                run.vardorvisAxeSets.isEmpty() &&
                clock.cycle >= run.vardorvisNextEnragedAxeCycle &&
                run.state in setOf(Dt2State.ACTIVE, Dt2State.ENRAGED)
        ) {
            run.vardorvisNextEnragedAxeCycle = Int.MAX_VALUE
            beginVardorvisAxes(run)
        }
    }

    private fun applyVardorvisBleed(run: Run) {
        run.vardorvisBleedStartCycle = clock.cycle
        run.vardorvisBleedLastCycle = clock.cycle - Dt2Rules.VARDORVIS_BLEED_WALK_INTERVAL
        run.player.mes("<col=ff3045>You have started bleeding!</col>")
    }

    private fun tickVardorvisBleed(run: Run) {
        if (run.vardorvisBleedStartCycle == Int.MIN_VALUE) return
        val elapsed = clock.cycle - run.vardorvisBleedStartCycle
        if (elapsed >= Dt2Rules.VARDORVIS_BLEED_DURATION_TICKS) {
            run.vardorvisBleedStartCycle = Int.MIN_VALUE
            return
        }
        val last = run.lastPlayerTile
        val running =
            last != null &&
                max(abs(run.player.coords.x - last.x), abs(run.player.coords.z - last.z)) > 1
        if (
            Dt2Rules.vardorvisBleedDue(elapsed, clock.cycle - run.vardorvisBleedLastCycle, running)
        ) {
            run.vardorvisBleedLastCycle = clock.cycle
            damageVardorvisPlayer(run, Dt2Rules.VARDORVIS_BLEED_DAMAGE)
        }
    }

    private fun beginVardorvisDarts(run: Run) {
        val arenaCenter = vardorvisTile(run, 40, 25)
        val dashStart = run.player.coords
        val atEdge =
            Dt2Rules.vardorvisAtDartEdge(dashStart.x, dashStart.z, arenaCenter.x, arenaCenter.z)
        val count = if (atEdge) 1 else Dt2Rules.vardorvisDartCount(run.boss.hitpoints)
        run.boss.clearInteraction()
        run.boss.movementLocked = true
        run.vardorvisDashSteps.clear()

        if (atEdge) {
            val (stepX, stepZ) =
                Dt2Rules.vardorvisEdgeDartOffset(
                    dashStart.x,
                    dashStart.z,
                    arenaCenter.x,
                    arenaCenter.z,
                )
            val crack = dashStart.translate(stepX, stepZ)
            run.vardorvisDashSteps += VardorvisDashStep(clock.cycle, run.boss.coords)
            run.vardorvisDarts += VardorvisDart(crack, clock.cycle, clock.cycle + 3, wave = 0)
        } else {
            val horizontal =
                abs(dashStart.x - run.boss.coords.x) >= abs(dashStart.z - run.boss.coords.z)
            var dashFrom = run.boss.coords
            repeat(count) { wave ->
                val rolls =
                    if (wave == 2) {
                        List(Dt2Rules.VARDORVIS_RANDOM_DART_COUNT) {
                            run.rng.nextInt(Int.MAX_VALUE)
                        }
                    } else {
                        emptyList()
                    }
                Dt2Rules.vardorvisDartPattern(wave, horizontal, rolls).forEach { (x, z) ->
                    run.vardorvisDarts +=
                        VardorvisDart(
                            dashStart.translate(x, z),
                            clock.cycle + wave,
                            clock.cycle + wave + 3,
                            wave,
                        )
                }
                val destination = vardorvisDartDestination(run, dashFrom, wave) ?: dashFrom
                run.vardorvisDashSteps += VardorvisDashStep(clock.cycle + wave, destination)
                dashFrom = destination
            }
        }
        run.boss.actionDelay = max(run.boss.actionDelay, clock.cycle) + count
    }

    private fun tickVardorvisDarts(run: Run) {
        val dashIterator = run.vardorvisDashSteps.iterator()
        while (dashIterator.hasNext()) {
            val dash = dashIterator.next()
            if (clock.cycle < dash.cycle) continue
            performVardorvisDash(run, dash.destination)
            dashIterator.remove()
        }
        if (
            run.boss.movementLocked &&
                run.vardorvisDashSteps.isEmpty() &&
                run.state in setOf(Dt2State.ACTIVE, Dt2State.ENRAGED)
        ) {
            run.boss.movementLocked = false
            run.boss.apPlayer2(run.player, interactions)
        }

        val iterator = run.vardorvisDarts.iterator()
        while (iterator.hasNext()) {
            val dart = iterator.next()
            if (!dart.warned && clock.cycle >= dart.warningCycle) {
                dart.warned = true
                world.spotanimMap(SpotanimType(2510), dart.tile)
            }
            if (clock.cycle < dart.impactCycle) continue
            world.spotanimMap(SpotanimType(2512), dart.tile)
            if (run.player.coords == dart.tile) {
                val damage = 1 + run.rng.nextInt(Dt2Rules.VARDORVIS_DART_MAX_HIT)
                damageVardorvisPlayer(run, damage)
            }
            iterator.remove()
        }
    }

    private fun vardorvisDartDestination(run: Run, from: CoordGrid, wave: Int): CoordGrid? {
        val player = run.player.coords
        val candidates =
            listOf(
                    player.translate(-2, -1),
                    player.translate(1, -1),
                    player.translate(1, 1),
                    player.translate(-2, 1),
                )
                .filter { it != from && isVardorvisFootprintClear(it, run.boss.size) }
        if (candidates.isEmpty()) return null
        return if (wave % 2 == 0) {
            candidates.maxByOrNull { max(abs(it.x - from.x), abs(it.z - from.z)) }
        } else {
            candidates.minByOrNull { max(abs(it.x - from.x), abs(it.z - from.z)) }
        }
    }

    private fun beginVardorvisHead(run: Run) {
        if (run.vardorvisHead != null) return
        val head = spawnHelper(run, Dt2Assets.VARDORVIS_HEAD, run.boss.coords.translate(0, -3))
        head.anim(Dt2Assets.VARDORVIS_HEAD_ANIM)
        head.spotanim(Dt2Assets.VARDORVIS_HEAD_CAST)
        run.vardorvisHead = head
        run.vardorvisHeadImpactCycle = clock.cycle + 3
        run.boss.anim(Dt2Assets.VARDORVIS_MELEE_ANIM)
        run.player.mes("<col=ff289d>Vardorvis' head gazes upon you...</col>")
        world.projAnim(
            ProjAnim(
                spotanim = Dt2Assets.VARDORVIS_HEAD_PROJECTILE,
                startHeight = 124,
                endHeight = 96,
                startTime = 15,
                endTime = 90,
                angle = 8,
                progress = 124,
                sourceIndex = head.slotId + 1,
                targetIndex = -(run.player.slotId + 1),
                startCoord = head.coords,
                endCoord = run.player.coords,
            )
        )
        run.boss.actionDelay =
            max(
                run.boss.actionDelay,
                run.vardorvisHeadImpactCycle + Dt2Rules.VARDORVIS_HEAD_BODY_RECOVERY_TICKS,
            )
    }

    private fun tickVardorvisHead(run: Run) {
        val head = run.vardorvisHead ?: return
        if (clock.cycle < run.vardorvisHeadImpactCycle) return
        run.player.spotanim(Dt2Assets.VARDORVIS_HEAD_IMPACT)
        if (run.player.vars["varbit.prayer_protectfrommissiles"] != 1) {
            run.player.statSub(
                "stat.prayer",
                constant = Dt2Rules.VARDORVIS_HEAD_PRAYER_DRAIN,
                percent = 0,
            )
            clearProtectionPrayers(run.player)
            run.prayerUnlockCycle = clock.cycle + Dt2Rules.VARDORVIS_HEAD_PRAYER_DISABLE_TICKS
            run.player.mes("You've been injured and can't use protection prayers!")
        }
        removeHelper(run, head)
        run.vardorvisHead = null
        run.vardorvisHeadImpactCycle = Int.MAX_VALUE
    }

    private fun beginVardorvisStrangle(run: Run) {
        if (run.vardorvisQteOpen || run.vardorvisQteOpenCycle != Int.MAX_VALUE) return
        val count = Dt2Rules.vardorvisStrangleCount(run.boss.hitpoints)
        run.boss.anim(Dt2Assets.VARDORVIS_STRANGLE_START_ANIM)
        run.vardorvisQteRemaining.clear()
        (6 until 6 + count).forEach(run.vardorvisQteRemaining::add)
        run.vardorvisQteOpenCycle = clock.cycle + 2
        run.vardorvisQteEndCycle =
            run.vardorvisQteOpenCycle + Dt2Rules.VARDORVIS_STRANGLE_WINDOW_TICKS
        run.rootEndCycle = run.vardorvisQteEndCycle
    }

    private fun tickVardorvisStrangle(run: Run) {
        if (run.state !in setOf(Dt2State.ACTIVE, Dt2State.ENRAGED)) return
        if (
            !run.vardorvisQteOpen &&
                run.vardorvisQteOpenCycle != Int.MAX_VALUE &&
                clock.cycle >= run.vardorvisQteOpenCycle
        ) {
            run.vardorvisQteOpen = true
            run.boss.anim(Dt2Assets.VARDORVIS_STRANGLE_LOOP_ANIM)
            run.player.anim(10166)
            run.player.spotanim(2523)
            run.player.ifOpenOverlay(VARDORVIS_QTE_INTERFACE, eventBus)
            run.player.runClientScript(1816, run.vardorvisQteRemaining.size)
            run.player.mes("<col=ff3045>Vardorvis entangles you in some tendrils!</col>")
        }
        if (run.vardorvisQteOpen && clock.cycle >= run.vardorvisQteEndCycle) {
            val damage = 1 + run.rng.nextInt(Dt2Rules.VARDORVIS_STRANGLE_MAX_HIT)
            damageVardorvisPlayer(run, damage)
            run.player.mes("The tendrils tighten around you, damaging you in the process!")
            finishVardorvisStrangle(run, success = false)
        }
    }

    fun destroyVardorvisSpore(player: Player, component: Int): Boolean {
        val run = runs[player.uid] ?: return false
        if (run.bossKind != Dt2Boss.VARDORVIS || !run.vardorvisQteOpen) return false
        if (!run.vardorvisQteRemaining.remove(component)) return false
        if (run.vardorvisQteRemaining.isEmpty()) finishVardorvisStrangle(run, success = true)
        return true
    }

    private fun finishVardorvisStrangle(run: Run, success: Boolean) {
        if (success) {
            run.player.mes("<col=229628>You manage to escape from the tendrils.</col>")
            run.player.spotanim(2525)
            run.player.runClientScript(1823)
        } else {
            run.player.spotanim(2524)
            run.player.mes("The tendrils tighten before you can escape.")
        }
        run.boss.anim(Dt2Assets.VARDORVIS_STRANGLE_END_ANIM)
        run.player.ifCloseOverlay(VARDORVIS_QTE_INTERFACE, eventBus)
        run.vardorvisQteOpen = false
        run.vardorvisQteOpenCycle = Int.MAX_VALUE
        run.vardorvisQteEndCycle = Int.MAX_VALUE
        run.vardorvisQteRemaining.clear()
        run.rootEndCycle = Int.MIN_VALUE
        run.player.resetAnim()
    }

    private fun respawnVardorvis(run: Run) {
        cleanupHazards(run)
        run.boss = spawnBoss(run.player, run.session, Dt2Boss.VARDORVIS, Dt2Difficulty.NORMAL)
        resetVardorvisState(run)
        run.boss.anim(Dt2Assets.VARDORVIS_SPAWN_ANIM)
        setVardorvisExitLocked(run, locked = false)
        run.player.mes("Vardorvis reforms in the ritual site.")
    }

    private fun resetVardorvisState(run: Run) {
        run.state = Dt2State.PREPARING
        run.enraged = false
        run.vardorvisEnragePending = false
        run.attackCounter = 0
        run.activeSpecial = null
        run.nextSpecialCycle = Int.MAX_VALUE
        run.vardorvisPreviousSpecial = null
        run.vardorvisAxeRotation = 0
        run.vardorvisNextEnragedAxeCycle = Int.MAX_VALUE
        run.vardorvisBleedStartCycle = Int.MIN_VALUE
        run.vardorvisBleedLastCycle = Int.MIN_VALUE
        run.vardorvisHead = null
        run.vardorvisHeadImpactCycle = Int.MAX_VALUE
        run.vardorvisQteOpen = false
        run.vardorvisQteOpenCycle = Int.MAX_VALUE
        run.vardorvisQteEndCycle = Int.MAX_VALUE
        run.vardorvisQteRemaining.clear()
        run.vardorvisAxeSets.clear()
        run.vardorvisDarts.clear()
        run.vardorvisDashSteps.clear()
        run.vardorvisNextPathCheckCycle = Int.MAX_VALUE
        run.prayerUnlockCycle = Int.MIN_VALUE
        run.rootEndCycle = Int.MIN_VALUE
        run.lastPlayerTile = run.player.coords
        run.boss.movementLocked = true
        run.boss.apRangeOverride = 1
        updateVardorvisStats(run)
    }

    private fun vardorvisTile(run: Run, localX: Int, localZ: Int): CoordGrid =
        requireNotNull(
            instances.localCoord(
                run.session,
                org.rsmod.api.instances.RegionLocal(0, 17, 53, localX, localZ),
            )
        )

    private fun removeHelper(run: Run, npc: Npc) {
        run.helpers.remove(npc)
        if (npc.isSlotAssigned) npcs.del(npc, Int.MAX_VALUE)
    }

    private fun setVardorvisExitLocked(run: Run, locked: Boolean) {
        if (run.bossKind != Dt2Boss.VARDORVIS) return
        val tile =
            vardorvisTile(
                run,
                Dt2Assets.VARDORVIS_EXIT_TILE.localX,
                Dt2Assets.VARDORVIS_EXIT_TILE.localZ,
            )
        val targetId = if (locked) Dt2Assets.VARDORVIS_EXIT_DISABLED else Dt2Assets.VARDORVIS_EXIT
        val current =
            locs.findAll(tile).firstOrNull {
                it.id == Dt2Assets.VARDORVIS_EXIT || it.id == Dt2Assets.VARDORVIS_EXIT_DISABLED
            } ?: return
        if (current.id == targetId) return
        val target =
            requireNotNull(ServerCacheManager.getObject(targetId)) {
                "Missing revision-240 Vardorvis exit object: $targetId"
            }
        locs.change(current, target, Int.MAX_VALUE)
    }

    private fun attackLeviathan(access: StandardNpcAccess, run: Run) {
        val tier = Dt2Rules.leviathanVolleyTier(run.attackCounter)
        val bite =
            Dt2Rules.leviathanAdjacentToFootprint(run.player.coords, run.boss.coords, run.boss.size)
        val style =
            if (bite) Dt2AttackStyle.MELEE
            else Dt2Rules.leviathanStyle(tier, run.enraged, run.rng.nextInt(Int.MAX_VALUE))
        val contract = Dt2Rules.leviathanAttackContract(style, bite, run.attackCounter)
        val attackRate =
            Dt2Rules.leviathanAttackRate(
                tier,
                run.enraged,
                run.difficulty == Dt2Difficulty.AWAKENED,
            )
        val generation = run.generation
        val modifier = PlayerHitModifier { target ->
            val builder = this
            with(NpcBypassProtectionPrayerPlayerHitModifier) { builder.modify(target) }
            if (runs[target.uid] !== run || run.generation != generation) {
                builder.damage = 0
            } else if (bite && leviathanProtected(target, Dt2AttackStyle.MELEE)) {
                builder.damage = 0
            } else if (!bite) {
                builder.damage =
                    Dt2Rules.leviathanOrbDamage(
                        builder.damage,
                        leviathanProtected(target, style),
                        run.enraged,
                        isInsideLeviathanPathfinder(run),
                    )
            }
        }
        val attackAnimation =
            if (bite) contract.animation
            else Dt2Rules.leviathanProjectileAnimation(tier, run.enraged)
        val projectileType =
            LEVIATHAN_PROJECTILE_TYPE.copy(
                lengthAdjustment =
                    Dt2Rules.leviathanProjectileLengthAdjustment(
                        tier,
                        run.enraged,
                        run.difficulty == Dt2Difficulty.AWAKENED,
                    )
            )
        val projectileSource =
            if (bite) null
            else
                Dt2Rules.leviathanProjectileSource(
                    run.player.coords,
                    run.boss.coords,
                    run.boss.size,
                )
        combat.attack(
            access,
            run.player,
            when (style) {
                Dt2AttackStyle.MAGIC -> CombatAttack.NpcMagic(contract.maximumHit)
                Dt2AttackStyle.RANGED -> CombatAttack.NpcRanged(RangedAttackType.Standard)
                Dt2AttackStyle.MELEE -> CombatAttack.NpcMelee(MeleeAttackType.Crush)
            },
            NpcAttackOverride(
                attackAnimation = animation(attackAnimation),
                maxHit = contract.maximumHit,
                projectile =
                    contract.projectileGraphic?.let {
                        NpcProjectile(spotanimName(it), projectileType, source = projectileSource)
                    },
                // Let the projectile's distance-derived 60-150 client-cycle flight determine
                // impact time. The old one/two-tick override landed damage before the orb.
                impactDelay = if (bite) 1 else null,
                attackRate = attackRate,
                applyModifierOnImpact = true,
                hitModifier = modifier,
                onAttack = {
                    when (style) {
                        Dt2AttackStyle.MAGIC ->
                            run.player.soundSynth(Dt2Assets.LEVIATHAN_MAGIC_ATTACK_SOUND)
                        Dt2AttackStyle.RANGED ->
                            run.player.soundSynth(Dt2Assets.LEVIATHAN_RANGED_ATTACK_SOUND)
                        Dt2AttackStyle.MELEE -> Unit
                    }
                    contract.castGraphic?.let(run.boss::spotanim)
                    if (!bite && attackAnimation == 10278) {
                        run.leviathanProjectileEndCycle = clock.cycle + 1
                    }
                    run.attackCounter++
                    if (!run.enraged && Dt2Rules.leviathanVolleyComplete(run.attackCounter)) {
                        val roarCycle = clock.cycle + attackRate
                        run.leviathanRoarCycle = roarCycle
                        run.boss.actionDelay = roarCycle + 1
                    }
                },
                onImpact = { _, impact ->
                    if (runs[run.player.uid] === run && run.generation == generation) {
                        // RSProx sends Leviathan's impact in slot 2 at height 100. Height 0
                        // rendered the impact at the player's feet instead of on their body.
                        contract.impactGraphic?.let { graphic ->
                            run.player.spotanim(
                                graphic,
                                height = Dt2Rules.LEVIATHAN_ORB_IMPACT_HEIGHT,
                                slot = Dt2Rules.LEVIATHAN_ORB_IMPACT_SLOT,
                            )
                        }
                        val protectedAtImpact = leviathanProtected(run.player, style)
                        if (
                            Dt2Rules.leviathanPerfectStandardFailure(
                                impact.damage,
                                bite,
                                protectedAtImpact,
                            )
                        ) {
                            markLeviathanPerfectFailure(
                                run,
                                if (bite) LeviathanPerfectFailure.BITE
                                else LeviathanPerfectFailure.STANDARD_ORB,
                            )
                        }
                    }
                },
            ),
        )
    }

    private fun leviathanProtected(player: Player, style: Dt2AttackStyle): Boolean =
        player.vars[
                when (style) {
                    Dt2AttackStyle.MAGIC -> "varbit.prayer_protectfrommagic"
                    Dt2AttackStyle.RANGED -> "varbit.prayer_protectfrommissiles"
                    Dt2AttackStyle.MELEE -> "varbit.prayer_protectfrommelee"
                }] == 1

    private fun checkWhispererThreshold(run: Run, after: Int) {
        if (run.enraged || run.state !in setOf(Dt2State.ACTIVE, Dt2State.ENRAGED)) return
        val threshold =
            Dt2Rules.whispererThresholds(run.difficulty).getOrNull(run.thresholdIndex) ?: return
        if (after > threshold) return
        run.thresholdIndex++
        beginSpecial(run, null)
    }

    private fun beginEnrage(run: Run) {
        run.enraged = true
        run.state = Dt2State.ENRAGED
        run.nextSpecialCycle = clock.cycle + 8
        if (run.bossKind == Dt2Boss.LEVIATHAN) {
            run.nextSpecialCycle = Int.MAX_VALUE
            run.leviathanRoarCycle = Int.MAX_VALUE
            run.leviathanPathfinderSpawnCycle =
                clock.cycle +
                    Dt2Rules.leviathanPathfinderSpawnDelay(run.difficulty == Dt2Difficulty.AWAKENED)
            run.leviathanEnrageDebrisCycle = clock.cycle
            run.leviathanEnragePersistentDebrisCycle = clock.cycle
            beginLeviathanRoar(run)
        }
        run.player.mes("${run.bossKind.displayName} enters its enrage phase!")
    }

    private fun beginLeviathanTornado(run: Run) {
        if (run.difficulty != Dt2Difficulty.AWAKENED || run.leviathanTornadoStarted) return
        val start =
            requireNotNull(instances.localCoord(run.session, Dt2Assets.LEVIATHAN_TORNADO_START))
        val tornado = spawnHelper(run, Dt2Assets.LEVIATHAN_TORNADO, start, movementLocked = false)
        tornado.spotanim(Dt2Assets.LEVIATHAN_TORNADO_GRAPHIC)
        run.leviathanTornado = tornado
        run.leviathanTornadoStarted = true
        run.leviathanTornadoLastHitCycle = Int.MIN_VALUE
    }

    private fun tickLeviathanTornado(run: Run) {
        val tornado = run.leviathanTornado ?: return
        if (!tornado.isSlotAssigned) return
        if (!Dt2Rules.leviathanTornadoCanMove(clock.cycle, run.leviathanTornadoLastHitCycle)) {
            return
        }
        if (tornado.coords == run.player.coords) {
            run.leviathanTornadoLastHitCycle = clock.cycle
            npcs.hide(tornado, Dt2Rules.LEVIATHAN_TORNADO_CONTACT_COOLDOWN)
            run.player.spotanim(Dt2Assets.LEVIATHAN_TORNADO_GRAPHIC)
            damagePlayer(run, Dt2Rules.LEVIATHAN_TORNADO_MIN_HIT, 0)
            return
        }
        val dx = run.player.coords.x - tornado.coords.x
        val dz = run.player.coords.z - tornado.coords.z
        val diagonal = tornado.coords.translate(dx.coerceIn(-1, 1), dz.coerceIn(-1, 1))
        val primary =
            if (kotlin.math.abs(dx) >= kotlin.math.abs(dz)) {
                tornado.coords.translate(dx.coerceIn(-1, 1), 0)
            } else {
                tornado.coords.translate(0, dz.coerceIn(-1, 1))
            }
        val secondary =
            if (kotlin.math.abs(dx) >= kotlin.math.abs(dz)) {
                tornado.coords.translate(0, dz.coerceIn(-1, 1))
            } else {
                tornado.coords.translate(dx.coerceIn(-1, 1), 0)
            }
        val next =
            listOf(diagonal, primary, secondary).distinct().firstOrNull {
                it != tornado.coords && collision.isZoneValid(it) && !collision.isWalkBlocked(it)
            }
        if (next != null) tornado.walk(next)
    }

    private fun spawnTentaclePattern(run: Run) {
        val target = run.player.coords
        val offsets =
            if (run.enraged) {
                val all = listOf(-4 to -4, 4 to -4, 4 to 4, -4 to 4)
                listOf(all[run.attackCounter % all.size])
            } else {
                listOf(-4 to -4, 4 to -4, 4 to 4, -4 to 4)
            }
        offsets.forEach { offset ->
            val tentacle =
                spawnHelper(
                    run,
                    Dt2Assets.WHISPERER_TENTACLE,
                    target.translate(offset.first, offset.second),
                )
            tentacle.anim(10263)
            tentacle.spotanim(2452)
            run.pendingWhispererTentacles +=
                PendingWhispererTentacle(tentacle, target, clock.cycle + 3)
        }
    }

    private fun tickWhispererRuntime(run: Run) {
        if (run.state in setOf(Dt2State.ACTIVE, Dt2State.SPECIAL, Dt2State.ENRAGED)) {
            val updated =
                Dt2Rules.whispererSanityTick(
                    run.sanity,
                    run.shadowRealm,
                    run.difficulty == Dt2Difficulty.AWAKENED,
                    clock.cycle,
                )
            if (updated != run.sanity) setSanity(run, updated)
            if (run.shadowRealm) {
                run.player.statSub(
                    "stat.prayer",
                    constant = if (run.difficulty == Dt2Difficulty.AWAKENED) 4 else 3,
                    percent = 0,
                )
            }
            if (run.sanity == 0) {
                val elapsed = clock.cycle - run.whispererInsanityStartCycle
                Dt2Rules.whispererInsanityDamage(elapsed)?.let { damagePlayer(run, it, 0) }
            }
        }
        if (
            run.whispererMeleeEndCycle != Int.MAX_VALUE &&
                clock.cycle >= run.whispererMeleeEndCycle &&
                run.boss.isSlotAssigned &&
                !run.enraged
        ) {
            run.boss.transmog(requireNpc(Dt2Assets.WHISPERER_ACTIVE_ID), Int.MAX_VALUE)
            run.boss.movementLocked = true
            run.boss.apRangeOverride = 12
            run.whispererMeleeEndCycle = Int.MAX_VALUE
        }
        tickWhispererShots(run)
        tickWhispererTentacles(run)
    }

    private fun tickWhispererShots(run: Run) {
        val iterator = run.pendingWhispererShots.iterator()
        while (iterator.hasNext()) {
            val shot = iterator.next()
            if (clock.cycle < shot.fireCycle) continue
            val maximum =
                Dt2Rules.whispererMaximumHit(shot.style, run.difficulty == Dt2Difficulty.AWAKENED)
            val damage = run.rng.nextInt(maximum + 1)
            if (shot.projectile) {
                run.boss.anim(if (shot.style == Dt2AttackStyle.MAGIC) 10244 else 10238)
                val projectile =
                    ProjAnim.fromNpcToPlayer(
                        run.boss,
                        run.player,
                        Dt2Rules.whispererProjectile(shot.style),
                        WHISPERER_PROJECTILE_TYPE,
                    )
                world.projAnim(projectile)
                run.player.queueHit(
                    source = run.boss,
                    delay = projectile.serverCycles,
                    type =
                        if (shot.style == Dt2AttackStyle.MAGIC) HitType.Magic else HitType.Ranged,
                    damage = damage,
                    modifier =
                        NpcCalculatedProtectionPrayerPlayerHitModifier(
                            whispererProtected(run, shot.style),
                            100,
                        ),
                )
            } else {
                run.player.queueHit(
                    source = run.boss,
                    delay = 1,
                    type = HitType.Melee,
                    damage = damage,
                    modifier =
                        NpcPartialProtectionPrayerPlayerHitModifier(
                            if (whispererProtected(run, Dt2AttackStyle.MELEE)) 50 else 0
                        ),
                )
            }
            iterator.remove()
        }
    }

    private fun tickWhispererTentacles(run: Run) {
        val iterator = run.pendingWhispererTentacles.iterator()
        while (iterator.hasNext()) {
            val pending = iterator.next()
            if (clock.cycle < pending.impactCycle) continue
            if (pending.npc.isSlotAssigned) {
                pending.npc.anim(10266)
                pending.npc.spotanim(2454)
            }
            if (run.player.coords == pending.target) {
                val damage = if (run.difficulty == Dt2Difficulty.AWAKENED) 30 else 20
                damagePlayer(run, damage, damage)
            }
            if (pending.npc.isSlotAssigned) npcs.del(pending.npc, Int.MAX_VALUE)
            run.helpers.remove(pending.npc)
            iterator.remove()
        }
    }

    private fun whispererProtected(run: Run, style: Dt2AttackStyle): Boolean =
        run.player.vars[
                when (style) {
                    Dt2AttackStyle.MAGIC -> "varbit.prayer_protectfrommagic"
                    Dt2AttackStyle.RANGED -> "varbit.prayer_protectfrommissiles"
                    Dt2AttackStyle.MELEE -> "varbit.prayer_protectfrommelee"
                }] == 1

    private fun tickWhispererSpecial(run: Run) {
        when (run.activeSpecial) {
            WhispererSpecial.CORRUPTED_SEEDS -> tickWhispererSeeds(run)
            WhispererSpecial.SCREECH -> tickWhispererScreech(run)
            else -> Unit
        }
    }

    private fun tickWhispererSeeds(run: Run) {
        if (!run.shadowRealm) return
        val seed = run.whispererSeeds[run.player.coords] ?: return
        if (seed.healthy && !seed.stepped) {
            seed.stepped = true
            seed.loc?.let { locs.del(it, Int.MAX_VALUE) }
            seed.loc = null
            run.specialProgress++
            run.player.mes(
                "You trample a healthy leech (${run.specialProgress}/${run.specialRequired})."
            )
        } else if (!seed.healthy && !seed.punished) {
            seed.punished = true
            run.whispererSpecialPerfect = false
            damagePlayer(run, 15 + run.rng.nextInt(11), 0)
        }
    }

    private fun tickWhispererScreech(run: Run) {
        val elapsed = clock.cycle - run.specialStartCycle
        val pulse =
            when (elapsed) {
                16 -> 1
                22 -> 2
                28 -> 3
                else -> return
            }
        run.boss.anim(10252)
        run.boss.spotanim(2470)
        val covered =
            run.shadowRealm &&
                run.whispererScreechPillars.any { pillar ->
                    pillar.strength >= pulse &&
                        pillarShields(run.boss.coords, pillar.tile, run.player.coords)
                }
        if (!covered) damagePlayer(run, 45, 45)
        run.whispererScreechPillars
            .filter { it.strength == pulse }
            .forEach { pillar ->
                pillar.loc?.let { locs.del(it, Int.MAX_VALUE) }
                pillar.loc = null
                if (pillar.npc.isSlotAssigned) npcs.del(pillar.npc, Int.MAX_VALUE)
                run.helpers.remove(pillar.npc)
            }
    }

    private fun pillarShields(boss: CoordGrid, pillar: CoordGrid, player: CoordGrid): Boolean {
        val bx = pillar.x - boss.x
        val bz = pillar.z - boss.z
        val px = player.x - boss.x
        val pz = player.z - boss.z
        val cross = abs(bx * pz - bz * px)
        val dot = bx * px + bz * pz
        val pillarDistance = bx * bx + bz * bz
        return dot > pillarDistance &&
            cross <= max(2, abs(bx) + abs(bz)) &&
            abs(player.x - pillar.x) <= 3 &&
            abs(player.z - pillar.z) <= 3
    }

    private fun applyWhispererSoulGroup(run: Run, group: WhispererSoulGroup) {
        when (group) {
            WhispererSoulGroup.VITA -> {
                val baseHitpoints = run.player.statBase("stat.hitpoints")
                val missing = (baseHitpoints - run.player.hitpoints).coerceAtLeast(0)
                val restore = minOf(missing, max(1, baseHitpoints / 5))
                if (restore > 0) run.player.statAdd("stat.hitpoints", restore, 0)
            }
            WhispererSoulGroup.ORATIO -> {
                val base = run.player.statBase("stat.prayer")
                val missing = (base - run.player.stat("stat.prayer")).coerceAtLeast(0)
                val restore = minOf(missing, max(1, base / 5))
                if (restore > 0) run.player.statAdd("stat.prayer", restore, 0)
            }
            WhispererSoulGroup.SANITAS -> setSanity(run, run.sanity + 15)
            WhispererSoulGroup.MORS -> {
                run.boss.hitpoints = (run.boss.hitpoints - 50).coerceAtLeast(1)
                run.boss.spotanim(2468)
            }
        }
        run.player.mes("You manipulated the Whisperer's chant in your favour.")
    }

    private fun whispererLocal(run: Run, local: Pair<Int, Int>): CoordGrid =
        requireNotNull(
            instances.localCoord(run.session, RegionLocal(0, 41, 99, local.first, local.second))
        )

    private fun spawnWhispererLoc(run: Run, tile: CoordGrid, id: Int): LocInfo? {
        val type = ServerCacheManager.getObject(id) ?: return null
        val loc = locs.add(tile, type, Int.MAX_VALUE, LocAngle.North, LocShape.GroundDecor)
        run.whispererLocs += loc
        return loc
    }

    private fun spawnDanger(
        run: Run,
        tile: CoordGrid,
        delay: Int,
        damage: Int,
        graphic: Int,
        sanity: Int = 0,
        perfectFailure: LeviathanPerfectFailure? = null,
    ) {
        world.spotanimMap(SpotanimType(graphic), tile)
        run.dangerTiles +=
            DangerTile(tile, clock.cycle + delay, damage, sanity, run.generation, perfectFailure)
    }

    private fun spawnDangerRing(
        run: Run,
        safe: CoordGrid,
        safeRadius: Int,
        delay: Int,
        damage: Int,
        graphic: Int,
    ) {
        val center = run.player.coords
        (-5..5).forEach { dx ->
            (-5..5).forEach { dz ->
                val tile = center.translate(dx, dz)
                if (abs(tile.x - safe.x) > safeRadius || abs(tile.z - safe.z) > safeRadius) {
                    spawnDanger(run, tile, delay, damage, graphic)
                }
            }
        }
    }

    private fun spawnMovingHazard(
        run: Run,
        npcId: Int,
        from: CoordGrid,
        to: CoordGrid,
        duration: Int,
        damage: Int,
        visualNpc: Boolean = true,
    ) {
        val npc = if (visualNpc) spawnHelper(run, npcId, from) else null
        run.movingHazards +=
            MovingHazard(npc, from, to, clock.cycle, clock.cycle + duration, damage, run.generation)
    }

    private fun spawnHelper(
        run: Run,
        id: Int,
        tile: CoordGrid,
        movementLocked: Boolean = true,
    ): Npc {
        val npc = Npc(requireNpc(id), tile)
        npc.mode = null
        npc.assignSpawnOwner(run.player, clock.cycle)
        npc.movementLocked = movementLocked
        npcs.add(npc, DT2_NPC_LIFETIME)
        instances.attachNpc(run.session.id, npc)
        run.helpers += npc
        return npc
    }

    private fun tickDangerTiles(run: Run) {
        val iterator = run.dangerTiles.iterator()
        while (iterator.hasNext()) {
            val danger = iterator.next()
            if (danger.generation != run.generation || runs[run.player.uid] !== run) {
                iterator.remove()
                continue
            }
            if (clock.cycle < danger.impactCycle) continue
            if (run.player.coords == danger.tile) {
                damagePlayer(run, danger.damage, danger.sanity, danger.perfectFailure)
            }
            iterator.remove()
        }
    }

    private fun tickMovingHazards(run: Run) {
        val iterator = run.movingHazards.iterator()
        while (iterator.hasNext()) {
            val hazard = iterator.next()
            if (hazard.generation != run.generation || runs[run.player.uid] !== run) {
                hazard.npc?.let { if (it.isSlotAssigned) npcs.del(it, Int.MAX_VALUE) }
                iterator.remove()
                continue
            }
            if (clock.cycle >= hazard.endCycle) {
                hazard.npc?.let { if (it.isSlotAssigned) npcs.del(it, Int.MAX_VALUE) }
                iterator.remove()
                continue
            }
            val elapsed = (clock.cycle - hazard.startCycle).coerceAtLeast(0)
            val duration = (hazard.endCycle - hazard.startCycle).coerceAtLeast(1)
            val x = hazard.from.x + (hazard.to.x - hazard.from.x) * elapsed / duration
            val z = hazard.from.z + (hazard.to.z - hazard.from.z) * elapsed / duration
            val tile = CoordGrid(x, z, hazard.from.level)
            hazard.npc?.teleport(collision, tile)
            if (
                abs(run.player.coords.x - x) <= 1 &&
                    abs(run.player.coords.z - z) <= 1 &&
                    hazard.lastHitCycle + 2 <= clock.cycle
            ) {
                hazard.lastHitCycle = clock.cycle
                damagePlayer(run, hazard.damage, 0)
            }
        }
    }

    private fun damagePlayer(
        run: Run,
        damage: Int,
        sanity: Int,
        perfectFailure: LeviathanPerfectFailure? = null,
        onImpact: ((Int) -> Unit)? = null,
    ) {
        val generation = run.generation
        val modifier = PlayerHitModifier { target ->
            val builder = this
            with(NpcBypassProtectionPrayerPlayerHitModifier) { builder.modify(target) }
            if (runs[target.uid] !== run || run.generation != generation) builder.damage = 0
        }
        run.player.queueImpactHit(
            source = run.boss,
            delay = 1,
            type = HitType.Typeless,
            damage = damage,
            modifier = modifier,
            impactHandler =
                if (perfectFailure != null || onImpact != null) {
                    HitImpactHandler { impact ->
                        if (
                            impact.damage > 0 &&
                                runs[run.player.uid] === run &&
                                run.generation == generation
                        ) {
                            val failure = perfectFailure
                            if (failure != null) {
                                markLeviathanPerfectFailure(run, failure)
                            }
                            onImpact?.invoke(impact.damage)
                        }
                    }
                } else null,
        )
        if (sanity > 0) drainSanity(run, sanity)
    }

    private fun damageVardorvisPlayer(run: Run, damage: Int, onDamage: (() -> Unit)? = null) {
        damagePlayer(run, damage, sanity = 0) { actual ->
            onDamage?.invoke()
            healVardorvis(run, actual)
        }
    }

    private fun healVardorvis(run: Run, actualDamage: Int) {
        val heal = Dt2Rules.vardorvisHeal(actualDamage)
        if (heal <= 0 || run.state in setOf(Dt2State.DYING, Dt2State.INVALIDATED)) return
        run.boss.heal(heal)
        bossHpBar.onUpdate(run.player, run.boss, run.boss.hitpoints, run.maximumHp)
    }

    private fun drainSanity(run: Run, amount: Int) {
        setSanity(run, (run.sanity - amount).coerceAtLeast(0))
    }

    private fun setSanity(run: Run, value: Int) {
        val previous = run.sanity
        run.sanity = value.coerceIn(0, 100)
        if (run.sanity == 0 && previous > 0) {
            run.whispererInsanityStartCycle = clock.cycle
            run.player.mes("Your sanity shatters.")
        } else if (run.sanity > 0) {
            run.whispererInsanityStartCycle = Int.MAX_VALUE
        }
        VarPlayerIntMapSetter.set(run.player, "varbit.sanity", run.sanity)
    }

    private fun setShadowRealm(run: Run, active: Boolean) {
        run.shadowRealm = active
        VarPlayerIntMapSetter.set(
            run.player,
            "varbit.whisperer_shadow_realm_visited",
            if (active) 1 else 0,
        )
        run.player.spotanim(if (active) 2466 else 2467)
        if (run.activeSpecial == WhispererSpecial.CORRUPTED_SEEDS) {
            run.whispererSeeds.values.forEach { seed ->
                seed.loc?.let { locs.del(it, Int.MAX_VALUE) }
                seed.loc =
                    if (seed.stepped) null
                    else
                        spawnWhispererLoc(
                            run,
                            seed.tile,
                            if (!active) Dt2Assets.WHISPERER_SEED_REGULAR
                            else if (seed.healthy) Dt2Assets.WHISPERER_SEED_SHADOW_WEAK
                            else Dt2Assets.WHISPERER_SEED_SHADOW_DANGER,
                        )
            }
        }
        if (run.activeSpecial == WhispererSpecial.SCREECH) {
            run.whispererScreechPillars.forEach { pillar ->
                if (pillar.npc.isSlotAssigned) {
                    pillar.npc.transmog(
                        requireNpc(
                            if (active) Dt2Assets.WHISPERER_SCREECH_SHADOW
                            else Dt2Assets.WHISPERER_SCREECH_SAFE
                        ),
                        Int.MAX_VALUE,
                    )
                }
            }
        }
        if (run.activeSpecial == WhispererSpecial.SOUL_SIPHON) {
            run.whispererSoulGroups.keys.forEach { soul ->
                if (soul.isSlotAssigned) {
                    soul.transmog(
                        requireNpc(
                            if (active) Dt2Assets.WHISPERER_SOUL_VULNERABLE
                            else Dt2Assets.WHISPERER_SOUL
                        ),
                        Int.MAX_VALUE,
                    )
                    soul.anim(if (active) 5545 else 5542)
                }
            }
        }
    }

    private fun clearProtectionPrayers(player: Player) {
        VarPlayerIntMapSetter.set(player, "varbit.prayer_protectfrommelee", 0)
        VarPlayerIntMapSetter.set(player, "varbit.prayer_protectfrommagic", 0)
        VarPlayerIntMapSetter.set(player, "varbit.prayer_protectfrommissiles", 0)
    }

    private fun normalSpecialDelay(run: Run): Int =
        when (run.bossKind) {
            Dt2Boss.WHISPERER -> 36
            Dt2Boss.VARDORVIS -> Dt2Rules.vardorvisSpecialDelay(run.enraged)
            Dt2Boss.LEVIATHAN -> 34
            Dt2Boss.DUKE -> 30
        }

    private fun keepAggressive(run: Run) {
        if (
            run.bossKind == Dt2Boss.LEVIATHAN &&
                run.leviathanAction != LeviathanAction.STANDARD_VOLLEY
        )
            return
        if (
            run.boss.isSlotAssigned &&
                run.boss.mode == null &&
                run.state in setOf(Dt2State.ACTIVE, Dt2State.ENRAGED)
        ) {
            run.boss.apPlayer2(run.player, interactions)
        }
    }

    private fun respawn(run: Run) {
        if (run.bossKind == Dt2Boss.VARDORVIS) {
            respawnVardorvis(run)
            return
        }
        cleanupHazards(run)
        closeWhispererUi(run)
        if (run.bossKind == Dt2Boss.DUKE) instances.detachNpc(run.session.id, run.boss)
        run.boss = spawnBoss(run.player, run.session, run.bossKind, run.difficulty)
        resetRun(run, afterKill = true)
        if (run.bossKind != Dt2Boss.LEVIATHAN) {
            bossHpBar.onOpen(run.player, run.boss)
            bossHpBar.onUpdate(run.player, run.boss, run.boss.hitpoints, run.boss.baseHitpointsLvl)
        }
        if (run.bossKind == Dt2Boss.LEVIATHAN) {
            spawnLeviathanSceneActors(run)
            run.boss.anim(10291)
        }
        if (run.bossKind == Dt2Boss.WHISPERER) {
            run.boss.anim(10227)
            run.player.ifOpenOverlay(DT2_SANITY_INTERFACE, eventBus)
            setSanity(run, 100)
            run.player.mes("The Odd Figure returns. Disturb it when you are ready.")
        }
    }

    private fun resetRun(run: Run, afterKill: Boolean = false) {
        run.enraged = false
        run.generation = generation++
        run.shadowRealm = false
        run.thresholdIndex = 0
        run.specialIndex = 0
        run.attackCounter =
            if (run.bossKind == Dt2Boss.LEVIATHAN) {
                Dt2Rules.leviathanInitialAttackCounter(run.difficulty == Dt2Difficulty.AWAKENED)
            } else 0
        run.poisonDoses = 0
        run.stunCount = 0
        run.specialStartCycle = Int.MAX_VALUE
        run.specialEndCycle = Int.MAX_VALUE
        run.specialProgress = 0
        run.specialRequired = 0
        run.whispererInsanityStartCycle = Int.MAX_VALUE
        run.whispererMeleeEndCycle = Int.MAX_VALUE
        run.whispererSpecialPerfect = true
        run.leviathanRoarCycle = Int.MAX_VALUE
        run.leviathanAction = LeviathanAction.DORMANT
        run.leviathanStunDamage = 0
        run.leviathanSuccessfulStuns = 0
        run.leviathanPerfectKill = true
        run.leviathanPerfectFailures.clear()
        run.leviathanOnlyMithrilAmmunition = true
        run.leviathanNeverAbove25Hitpoints = true
        run.leviathanKillRecordCommitted = false
        run.leviathanNextSpecial = null
        run.leviathanLastSpecial = null
        run.leviathanRetaliationPending = false
        run.leviathanProjectileEndCycle = Int.MAX_VALUE
        run.leviathanSmokeSequence = 0
        run.leviathanCurrentTrailTile = null
        run.pendingLeviathanDebris.clear()
        run.currentSmokeDebris.clear()
        run.leviathanDebrisLocs.clear()
        run.leviathanPathfinder = null
        run.leviathanPathfinderRoute.clear()
        run.leviathanPathfinderRouteIndex = 0
        run.leviathanPathfinderSpawnCycle = Int.MAX_VALUE
        run.leviathanEnrageDebrisCycle = Int.MAX_VALUE
        run.leviathanEnragePersistentDebrisCycle = Int.MAX_VALUE
        run.leviathanTornado = null
        run.leviathanTornadoStarted = false
        run.leviathanTornadoLastHitCycle = Int.MIN_VALUE
        run.leviathanCameraResetCycle = Int.MAX_VALUE
        run.leviathanCameraLastShakeCycle = Int.MIN_VALUE
        run.leviathanAppearanceSoundPlayed = false
        run.permanentDebris.clear()
        run.startStage = if (afterKill && run.bossKind == Dt2Boss.LEVIATHAN) 3 else 0
        run.startReadyCycle =
            clock.cycle + if (afterKill && run.bossKind == Dt2Boss.LEVIATHAN) 3 else 4
        run.killStartCycle = clock.cycle
        run.nextSpecialCycle = Int.MAX_VALUE
        run.activeSpecial = null
        run.state =
            when {
                afterKill && run.bossKind == Dt2Boss.LEVIATHAN -> Dt2State.STARTING
                run.bossKind in setOf(Dt2Boss.WHISPERER, Dt2Boss.DUKE, Dt2Boss.LEVIATHAN) ->
                    Dt2State.PREPARING
                else -> Dt2State.STARTING
            }
        if (run.bossKind == Dt2Boss.DUKE) dukeEncounter.prepare(run, preserveVats = afterKill)
    }

    private fun spawnBoss(
        player: Player,
        session: InstanceSession,
        boss: Dt2Boss,
        difficulty: Dt2Difficulty,
    ): Npc {
        val tile = requireNotNull(instances.localCoord(session, boss.spawn))
        val npc = Npc(initialType(boss, difficulty), tile)
        npc.mode = null
        npc.baseHitpointsLvl = boss.hitpoints(difficulty)
        npc.hitpoints = boss.hitpoints(difficulty)
        npc.assignSpawnOwner(player, clock.cycle)
        npc.movementLocked = boss != Dt2Boss.VARDORVIS
        npc.apRangeOverride = if (boss == Dt2Boss.VARDORVIS) 1 else Dt2Rules.LEVIATHAN_AP_RANGE
        if (boss == Dt2Boss.LEVIATHAN) {
            npc.maxOpRangeOverride = Dt2Rules.LEVIATHAN_MAX_OP_RANGE
            npc.apRequiresLineOfSight = false
            npc.combatImmunities.toxins = true
            if (difficulty == Dt2Difficulty.AWAKENED) {
                val stats = Dt2Rules.LEVIATHAN_AWAKENED_STATS
                npc.baseAttackLvl = stats.attack
                npc.attackLvl = stats.attack
                npc.baseStrengthLvl = stats.strength
                npc.strengthLvl = stats.strength
                npc.baseDefenceLvl = stats.defence
                npc.defenceLvl = stats.defence
                npc.baseRangedLvl = stats.ranged
                npc.rangedLvl = stats.ranged
                npc.baseMagicLvl = stats.magic
                npc.magicLvl = stats.magic
                npc.baseMagicDefenceLvl = stats.magic
                npc.magicDefenceLvl = stats.magic
            }
            val awakened = difficulty == Dt2Difficulty.AWAKENED
            npc.combatImmunities.statDrain = awakened
            npc.combatImmunities.slayerTaskBonuses = awakened
        }
        npcs.add(npc, DT2_NPC_LIFETIME)
        instances.attachNpc(session.id, npc)
        if (boss == Dt2Boss.DUKE) {
            dukeEncounter.configureNpc(npc, difficulty == Dt2Difficulty.AWAKENED)
        }
        return npc
    }

    private fun spawnLeviathanSceneActors(run: Run) {
        Dt2Assets.LEVIATHAN_TAIL_ACTORS.forEach { (id, local) ->
            val tile = requireNotNull(instances.localCoord(run.session, local))
            spawnHelper(run, id, tile, movementLocked = true)
        }
    }

    private fun initialType(boss: Dt2Boss, difficulty: Dt2Difficulty): NpcServerType {
        val id =
            if (boss == Dt2Boss.DUKE) DukeRules.NORMAL // Authoritative combat and drop-table type.
            else if (boss == Dt2Boss.WHISPERER) Dt2Assets.WHISPERER_SPAWN_ID
            else if (difficulty == Dt2Difficulty.AWAKENED) boss.awakenedNpcId else boss.normalNpcId
        return requireNpc(id)
    }

    private fun cleanupTransientHazards(run: Run) {
        run.dangerTiles.clear()
        run.pendingWhispererShots.clear()
        run.pendingWhispererTentacles.clear()
        run.whispererLocs.toList().forEach { locs.del(it, Int.MAX_VALUE) }
        run.whispererLocs.clear()
        run.whispererSeeds.clear()
        run.whispererScreechPillars.clear()
        run.whispererSoulGroups.clear()
        run.whispererSoulKills.clear()
        run.whispererCompletedSoulGroups.clear()
        run.movingHazards.forEach {
            it.npc?.let { npc -> if (npc.isSlotAssigned) npcs.del(npc, Int.MAX_VALUE) }
        }
        run.movingHazards.clear()
        run.helpers.toList().forEach { npc -> if (npc.isSlotAssigned) npcs.del(npc, Int.MAX_VALUE) }
        run.helpers.clear()
    }

    private fun cleanupHazards(run: Run) {
        if (run.bossKind == Dt2Boss.DUKE) dukeEncounter.clear(run)
        if (run.bossKind == Dt2Boss.LEVIATHAN) {
            Camera.camReset(run.player)
            run.leviathanCameraResetCycle = Int.MAX_VALUE
            run.leviathanCameraLastShakeCycle = Int.MIN_VALUE
        }
        if (run.bossKind == Dt2Boss.VARDORVIS) {
            run.vardorvisAxeSets.clear()
            run.vardorvisDarts.clear()
            run.vardorvisDashSteps.clear()
            run.vardorvisHead = null
            run.vardorvisHeadImpactCycle = Int.MAX_VALUE
            run.vardorvisBleedStartCycle = Int.MIN_VALUE
            run.vardorvisBleedLastCycle = Int.MIN_VALUE
            if (run.vardorvisQteOpen || run.vardorvisQteOpenCycle != Int.MAX_VALUE) {
                run.player.ifCloseOverlay(VARDORVIS_QTE_INTERFACE, eventBus)
            }
            run.vardorvisQteOpen = false
            run.vardorvisQteOpenCycle = Int.MAX_VALUE
            run.vardorvisQteEndCycle = Int.MAX_VALUE
            run.vardorvisQteRemaining.clear()
            run.player.resetAnim()
        }
        cleanupTransientHazards(run)
        run.pendingLeviathanDebris.clear()
        run.currentSmokeDebris.clear()
        run.leviathanDebrisLocs.values.toList().forEach { loc -> locs.del(loc, Int.MAX_VALUE) }
        run.leviathanDebrisLocs.clear()
        run.leviathanPathfinder = null
        run.leviathanPathfinderRoute.clear()
        run.leviathanPathfinderRouteIndex = 0
        run.leviathanPathfinderSpawnCycle = Int.MAX_VALUE
        run.leviathanEnrageDebrisCycle = Int.MAX_VALUE
        run.leviathanEnragePersistentDebrisCycle = Int.MAX_VALUE
        run.leviathanTornado = null
        run.permanentDebris.clear()
        run.rootEndCycle = Int.MIN_VALUE
        run.prayerUnlockCycle = Int.MIN_VALUE
    }

    private fun closeWhispererUi(run: Run) {
        if (run.bossKind != Dt2Boss.WHISPERER) return
        run.player.ifCloseOverlay(DT2_SANITY_INTERFACE, eventBus)
        setSanity(run, 100)
        setShadowRealm(run, false)
    }

    private fun removePreparationItems(player: Player) {
        Dt2Assets.preparationItems.forEach { id ->
            val item = requireItem(id)
            val count = player.inv.count(item.internalName)
            if (count > 0) player.invDel(player.inv, item.id, count)
        }
    }

    private fun prepare(player: Player) {
        player.combatClearQueue()
        player.clearInteraction()
        player.resetAnim()
    }

    private fun animation(id: Int): String =
        requireNotNull(ServerCacheManager.getAnim(id)) { "Missing revision-240 DT2 sequence: $id" }
            .internalName

    private fun spotanimName(id: Int): String =
        requireNotNull(RSCM.getReverseMapping(RSCMType.SPOTANIM, id)) {
            "Missing revision-240 DT2 spotanim: $id"
        }

    private fun requireNpc(id: Int): NpcServerType =
        requireNotNull(ServerCacheManager.getNpc(id)) { "Missing revision-240 DT2 npc: $id" }

    private fun requireItem(id: Int): ItemServerType =
        requireNotNull(ServerCacheManager.getItem(id)) { "Missing revision-240 DT2 item: $id" }

    internal data class Run(
        val player: Player,
        val session: InstanceSession,
        var generation: Long,
        val bossKind: Dt2Boss,
        val difficulty: Dt2Difficulty,
        var boss: Npc,
        var state: Dt2State,
        val rng: Dt2SeededRng,
        val seed: Long,
        var startReadyCycle: Int,
        var killStartCycle: Int,
        var killTicks: Int = 0,
        var respawnCycle: Int = Int.MAX_VALUE,
        var nextSpecialCycle: Int = Int.MAX_VALUE,
        var specialStartCycle: Int = Int.MAX_VALUE,
        var specialEndCycle: Int = Int.MAX_VALUE,
        var activeSpecial: Any? = null,
        var specialIndex: Int = 0,
        var specialProgress: Int = 0,
        var specialRequired: Int = 0,
        var thresholdIndex: Int = 0,
        var attackCounter: Int = 0,
        var startStage: Int = 0,
        var stunCount: Int = 0,
        var leviathanRoarCycle: Int = Int.MAX_VALUE,
        var poisonDoses: Int = 0,
        var duke: DukeState = DukeState(0),
        var sanity: Int = 100,
        var shadowRealm: Boolean = false,
        var enraged: Boolean = false,
        var rootEndCycle: Int = Int.MIN_VALUE,
        var prayerUnlockCycle: Int = Int.MIN_VALUE,
        var whispererInsanityStartCycle: Int = Int.MAX_VALUE,
        var whispererMeleeEndCycle: Int = Int.MAX_VALUE,
        var whispererSpecialPerfect: Boolean = true,
        var vardorvisEnragePending: Boolean = false,
        var vardorvisPreviousSpecial: VardorvisSpecial? = null,
        var vardorvisAxeRotation: Int = 0,
        var vardorvisNextEnragedAxeCycle: Int = Int.MAX_VALUE,
        var vardorvisNextPathCheckCycle: Int = Int.MAX_VALUE,
        var vardorvisBleedStartCycle: Int = Int.MIN_VALUE,
        var vardorvisBleedLastCycle: Int = Int.MIN_VALUE,
        var lastPlayerTile: CoordGrid? = null,
        var vardorvisHead: Npc? = null,
        var vardorvisHeadImpactCycle: Int = Int.MAX_VALUE,
        var vardorvisQteOpen: Boolean = false,
        var vardorvisQteOpenCycle: Int = Int.MAX_VALUE,
        var vardorvisQteEndCycle: Int = Int.MAX_VALUE,
        val vardorvisQteRemaining: MutableSet<Int> = linkedSetOf(),
        val vardorvisAxeSets: MutableList<VardorvisAxeSet> = mutableListOf(),
        val vardorvisDarts: MutableList<VardorvisDart> = mutableListOf(),
        val vardorvisDashSteps: MutableList<VardorvisDashStep> = mutableListOf(),
        val helpers: MutableSet<Npc> = linkedSetOf(),
        val pendingWhispererShots: MutableList<PendingWhispererShot> = mutableListOf(),
        val pendingWhispererTentacles: MutableList<PendingWhispererTentacle> = mutableListOf(),
        val whispererLocs: MutableSet<LocInfo> = linkedSetOf(),
        val whispererSeeds: MutableMap<CoordGrid, WhispererSeedState> = linkedMapOf(),
        val whispererScreechPillars: MutableList<WhispererScreechPillar> = mutableListOf(),
        val whispererSoulGroups: MutableMap<Npc, WhispererSoulGroup> = linkedMapOf(),
        val whispererSoulKills: MutableMap<WhispererSoulGroup, Int> = mutableMapOf(),
        val whispererCompletedSoulGroups: MutableSet<WhispererSoulGroup> = linkedSetOf(),
        val permanentDebris: MutableSet<CoordGrid> = linkedSetOf(),
        var leviathanAction: LeviathanAction = LeviathanAction.DORMANT,
        var leviathanStunDamage: Int = 0,
        var leviathanSuccessfulStuns: Int = 0,
        var leviathanPerfectKill: Boolean = true,
        val leviathanPerfectFailures: MutableSet<LeviathanPerfectFailure> = linkedSetOf(),
        var leviathanOnlyMithrilAmmunition: Boolean = true,
        var leviathanNeverAbove25Hitpoints: Boolean = true,
        var leviathanKillRecordCommitted: Boolean = false,
        val eventToken: String = UUID.randomUUID().toString(),
        var leviathanNextSpecial: LeviathanSpecial? = null,
        var leviathanLastSpecial: LeviathanSpecial? = null,
        var leviathanRetaliationPending: Boolean = false,
        var leviathanProjectileEndCycle: Int = Int.MAX_VALUE,
        var leviathanSmokeSequence: Int = 0,
        var leviathanCurrentTrailTile: CoordGrid? = null,
        val pendingLeviathanDebris: MutableList<PendingLeviathanDebris> = mutableListOf(),
        val leviathanDebrisLocs: MutableMap<CoordGrid, LocInfo> = linkedMapOf(),
        val currentSmokeDebris: MutableSet<CoordGrid> = linkedSetOf(),
        var leviathanPathfinder: Npc? = null,
        val leviathanPathfinderRoute: MutableList<CoordGrid> = mutableListOf(),
        var leviathanPathfinderRouteIndex: Int = 0,
        var leviathanPathfinderSpawnCycle: Int = Int.MAX_VALUE,
        var leviathanEnrageDebrisCycle: Int = Int.MAX_VALUE,
        var leviathanEnragePersistentDebrisCycle: Int = Int.MAX_VALUE,
        var leviathanTornado: Npc? = null,
        var leviathanTornadoStarted: Boolean = false,
        var leviathanTornadoLastHitCycle: Int = Int.MIN_VALUE,
        var leviathanCameraResetCycle: Int = Int.MAX_VALUE,
        var leviathanCameraLastShakeCycle: Int = Int.MIN_VALUE,
        var leviathanMusicRestorePoint: MusicPlayer.MusicRestorePoint? = null,
        var leviathanAppearanceSoundPlayed: Boolean = false,
        var rewardEligible: Boolean = false,
        val dangerTiles: MutableList<DangerTile> = mutableListOf(),
        val movingHazards: MutableList<MovingHazard> = mutableListOf(),
    ) {
        val maximumHp: Int
            get() = bossKind.hitpoints(difficulty)
    }

    internal data class PendingWhispererShot(
        val style: Dt2AttackStyle,
        val fireCycle: Int,
        val projectile: Boolean = true,
    )

    internal data class PendingWhispererTentacle(
        val npc: Npc,
        val target: CoordGrid,
        val impactCycle: Int,
    )

    internal data class WhispererSeedState(
        val tile: CoordGrid,
        val healthy: Boolean,
        var loc: LocInfo?,
        var stepped: Boolean = false,
        var punished: Boolean = false,
    )

    internal data class WhispererScreechPillar(
        val tile: CoordGrid,
        val strength: Int,
        var loc: LocInfo?,
        val npc: Npc,
    )

    internal data class PendingLeviathanDebris(
        val tile: CoordGrid,
        val impactCycle: Int,
        val damage: Int,
        val permanent: Boolean,
        val angle: LocAngle,
        val smokeSequence: Int?,
        val visualIndex: Int,
        val generation: Long,
        val emitImpactGraphic: Boolean = true,
    )

    internal data class DangerTile(
        val tile: CoordGrid,
        val impactCycle: Int,
        val damage: Int,
        val sanity: Int,
        val generation: Long,
        val perfectFailure: LeviathanPerfectFailure?,
    )

    internal data class MovingHazard(
        val npc: Npc?,
        val from: CoordGrid,
        val to: CoordGrid,
        val startCycle: Int,
        val endCycle: Int,
        val damage: Int,
        val generation: Long,
        var lastHitCycle: Int = Int.MIN_VALUE,
    )

    private companion object {
        const val LEVIATHAN_CAMERA_SHAKE_TICKS = 5

        val WHISPERER_PROJECTILE_TYPE =
            ProjAnimType(
                startHeight = 43,
                endHeight = 31,
                delay = 30,
                angle = 10,
                lengthAdjustment = 24,
                progress = 15,
                stepMultiplier = 5,
            )

        val LEVIATHAN_PROJECTILE_TYPE =
            ProjAnimType(
                startHeight = Dt2Rules.LEVIATHAN_ORB_START_HEIGHT,
                endHeight = Dt2Rules.LEVIATHAN_ORB_END_HEIGHT,
                delay = 30,
                angle = 30,
                lengthAdjustment = 0,
                progress = 124,
                stepMultiplier = 5,
            )
    }
}

internal data class VardorvisAxeSet(val startCycle: Int, val lanes: MutableList<VardorvisAxeState>)

internal data class VardorvisAxeState(
    val origin: CoordGrid,
    val movingStart: CoordGrid,
    val movementTiles: List<CoordGrid>,
    var tendril: Npc?,
    var static: Npc? = null,
    var moving: Npc? = null,
    var lastMovingTile: CoordGrid = movingStart,
    var hit: Boolean = false,
)

internal data class VardorvisDart(
    val tile: CoordGrid,
    val warningCycle: Int,
    val impactCycle: Int,
    val wave: Int,
    var warned: Boolean = false,
)

internal data class VardorvisDashStep(val cycle: Int, val destination: CoordGrid)
