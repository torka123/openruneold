package org.rsmod.content.bosses.yama

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.flatten
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.combat.NpcAttackOverride
import org.rsmod.api.combat.NvPCombat
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.magic.ArceuusSpellState
import org.rsmod.api.combat.commons.magic.MagicSpellChecks
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.commons.types.RangedAttackType
import org.rsmod.api.combat.player.PlayerAttackRangeModifierState
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.rollCount
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.RaidLeaveResult
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.mechanics.toxins.BurnEffectService
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.clearSpawnOwner
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.player.bonus.PrayerBonusModifierState
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.cinematic.CameraMode
import org.rsmod.api.player.cinematic.Cinematic
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.processor.InstantPlayerHitProcessor
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.hit.takeInstantHit
import org.rsmod.api.player.output.Camera
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.prayer.PrayerDrainResistance
import org.rsmod.api.player.prayer.disableProtectionPrayers
import org.rsmod.api.player.stat.StatRestorationModifierState
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.specials.energy.SpecialAttackEnergyRegeneration
import org.rsmod.api.specials.restriction.ConsumableRestrictionState
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.drops.eliteClueDropDenominator
import org.rsmod.content.drops.tables.monsters.YAMA_ELITE_CLUE_DENOMINATOR
import org.rsmod.content.drops.tables.monsters.yamaStandardRoll
import org.rsmod.content.drops.tables.monsters.yamaUniqueRoll
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitImpactHandler
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.isAnyType
import org.rsmod.game.inv.isType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Singleton
internal class YamaEncounterManager
@Inject
constructor(
    private val clock: MapClock,
    private val instances: InstanceManager,
    private val npcs: NpcRepository,
    private val locs: LocRepository,
    private val objects: ObjRepository,
    private val world: WorldRepository,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val interactions: AiPlayerInteractions,
    private val combat: NvPCombat,
    private val attackTypes: AttackTypes,
    private val players: PlayerList,
    private val rewardService: YamaRewardService,
    private val bossHpBar: BossHpBarScript,
    private val burns: BurnEffectService,
    private val instantHitProcessor: InstantPlayerHitProcessor,
    private val wornBonuses: WornBonuses,
    private val bossEncounters: EncounterRegistry,
) {
    private val log = InlineLogger()
    private val runs = mutableMapOf<InstanceId, Run>()
    private val participantRuns = mutableMapOf<Long, Run>()
    private val npcRuns = mutableMapOf<Npc, Run>()
    private val invitations = mutableMapOf<Long, Long>()
    private val pendingContracts = mutableMapOf<Long, YamaContract>()
    private val recordsImps = mutableMapOf<Long, Npc>()
    private var generation = 1L

    fun validateAssets() {
        YamaAssets.npcIds.forEach {
            requireNotNull(ServerCacheManager.getNpc(it)) { "Missing revision-240 Yama NPC $it" }
        }
        YamaAssets.locIds.forEach {
            requireNotNull(ServerCacheManager.getObject(it)) { "Missing revision-240 Yama loc $it" }
        }
        YamaAssets.sequences.forEach(::anim)
        YamaAssets.spotanims.forEach(::spot)
        requireNotNull(
            ServerCacheManager.getHealthBar(
                RSCM.run { YamaAssets.VOID_FLARE_CHARGE_HEADBAR.asRSCM(RSCMType.HEADBAR) }
            )
        ) {
            "Missing revision-240 Yama Voidflare charge headbar"
        }
        YamaContract.itemContracts.forEach { contract ->
            val item =
                requireNotNull(ServerCacheManager.getItem(requireNotNull(contract.itemId))) {
                    "Missing revision-240 ${contract.displayName}"
                }
            require(item.name == contract.displayName) {
                "Yama contract mismatch: id=${contract.itemId} expected=${contract.displayName} actual=${item.name}"
            }
        }
        require(ServerCacheManager.getItem(YamaAssets.DOSSIER)?.name == "Dossier")
        requireNotNull(ServerCacheManager.getItem(YamaAssets.CERT_PINEAPPLE_PIZZA))
        listOf(
                YamaAssets.OATHPLATE_HELM,
                YamaAssets.OATHPLATE_CHEST,
                YamaAssets.OATHPLATE_LEGS,
                YamaAssets.SOULFLAME_HORN,
                YamaAssets.OATHPLATE_SHARDS,
                YamaAssets.SIGIL_LEFT,
                YamaAssets.SIGIL_RIGHT,
                YamaAssets.SIGIL_MIDDLE,
                YamaAssets.SIGIL_TOP,
                YamaAssets.SIGIL_BOTTOM,
                YamaAssets.YAMI,
            )
            .forEach(::requireItem)
        listOf(
                YamaAssets.VARBIT_THRONE_STATE,
                YamaAssets.VARBIT_VOICE_PROGRESS,
                YamaAssets.VARBIT_TRANSITION_DAMAGE,
                YamaAssets.VARBIT_TRANSITION_PHASE,
            )
            .forEach {
                requireNotNull(ServerCacheManager.getVarbit(it)) {
                    "Missing revision-240 Yama varbit $it"
                }
            }
    }

    fun exposeNativeVoiceOptions(player: Player) {
        val type = ServerCacheManager.getVarbit(YamaAssets.VARBIT_VOICE_PROGRESS) ?: return
        if (player.vars[type] < 2) VarPlayerIntMapSetter.set(player, type, 2)
    }

    fun records(player: Player): YamaRecordSnapshot =
        YamaRecords.snapshot(
            killCount = player.attr[YAMA_KILL_COUNT],
            personalBestTicks = player.attr[YAMA_BEST_TIME],
            recentKillTimes = player.attr[YAMA_RECENT_KILL_TIMES],
        )

    fun summonRecordsImp(player: Player): Npc {
        removeRecordsImp(player)
        val imp = Npc(requireNpc(YamaAssets.IMP), YAMA_RECORDS_IMP_SPAWN)
        imp.mode = null
        imp.assignSpawnOwner(player, clock.cycle)
        npcs.add(imp, YamaRecords.IMP_SAFETY_LIFETIME_TICKS)
        recordsImps[player.accountHash] = imp
        imp.walk(YAMA_RECORDS_IMP_PRESENTATION)
        imp.facePlayer(player)
        return imp
    }

    fun beginRecordsImpDestruction(player: Player, imp: Npc) {
        if (recordsImps[player.accountHash] !== imp || !imp.isSlotAssigned) return
        imp.movementLocked = true
        imp.anim("seq.imp_death")
        imp.spotanim(spot(YamaAssets.GFX_DESPAWN))
    }

    fun removeRecordsImp(player: Player, expected: Npc? = null) {
        val imp = recordsImps[player.accountHash] ?: return
        if (expected != null && imp !== expected) return
        recordsImps.remove(player.accountHash)
        if (imp.isSlotAssigned) npcs.del(imp, Int.MAX_VALUE)
    }

    private fun recordSuccessfulKill(player: Player, killTimeTicks: Int): YamaRecordSnapshot {
        val records =
            YamaRecords.recordSuccess(
                killCount = player.attr[YAMA_KILL_COUNT],
                personalBestTicks = player.attr[YAMA_BEST_TIME],
                recentKillTimes = player.attr[YAMA_RECENT_KILL_TIMES],
                killTimeTicks = killTimeTicks,
            )
        player.attr[YAMA_KILL_COUNT] = records.killCount
        records.personalBestTicks?.let { player.attr[YAMA_BEST_TIME] = it }
        player.attr[YAMA_RECENT_KILL_TIMES] = records.recentKillTimes.toMutableList()
        records.fourKillAverageTicks?.let { player.attr[YAMA_FOUR_KILL_AVERAGE] = it }
        return records
    }

    fun teleportOutside(player: Player): Boolean {
        participantRuns[player.accountHash]?.let {
            removeParticipant(player, "admin travel", teleport = false)
        }
        instances.sessionForPlayer(player)?.let {
            player.mes("Leave your current instance before travelling to Yama.")
            return false
        }
        prepare(player)
        PathingEntityCommon.telejump(player, collision, YAMA_OUTSIDE)
        exposeNativeVoiceOptions(player)
        player.mes("You arrive before the Voice of Yama.")
        return true
    }

    fun invite(leader: Player, targetName: String): Boolean {
        if (participantRuns.containsKey(leader.accountHash)) {
            leader.mes("You cannot change your pact after entering the Domain.")
            return false
        }
        val target =
            players.firstOrNull {
                it.displayName.equals(targetName, ignoreCase = true) && it.isSlotAssigned
            }
        if (target == null || target === leader) {
            leader.mes("That player is not available.")
            return false
        }
        if (
            participantRuns.containsKey(target.accountHash) ||
                instances.sessionForPlayer(target) != null
        ) {
            leader.mes("${target.displayName} is already occupied.")
            return false
        }
        invitations[target.accountHash] = leader.accountHash
        target.mes(
            "${leader.displayName} has invited you to fight Yama. Use Join on the Voice of Yama after they enter."
        )
        leader.mes("You invite ${target.displayName} to your Yama pact.")
        return true
    }

    fun travel(player: Player): Boolean {
        if (
            participantRuns.containsKey(player.accountHash) ||
                instances.sessionForPlayer(player) != null
        ) {
            player.mes("You already have an active instance.")
            return false
        }
        val result =
            instances.create(
                player,
                YAMA_INSTANCE_KEY,
                yamaSpec(),
                InstanceAccess.Private,
                clock.cycle,
            )
        if (result !is InstanceManager.Result.Created) {
            player.mes(
                (result as? InstanceManager.Result.Failed)?.reason
                    ?: "Yama's Domain is unavailable."
            )
            return false
        }
        prepare(player)
        resetEncounterClientState(player)
        PathingEntityCommon.telejump(player, collision, result.enter)
        instances.finalizeEntry(player, result.session, clock.cycle)
        player.lootDropDuration = YAMA_DROP_DURATION
        val throne =
            spawnNpc(
                player,
                result.session,
                YamaAssets.THRONE_PARENT,
                local(result.session, 28, 41),
            )
        throne.movementLocked = true
        val run =
            Run(
                ownerHash = player.accountHash,
                session = result.session,
                generation = generation++,
                throne = throne,
                state = YamaState.LOBBY,
                stateCycle = clock.cycle,
                startCycle = clock.cycle,
            )
        run.participants[player.accountHash] = Participant(player.accountHash, player.uid, 0)
        runs[result.session.id] = run
        participantRuns[player.accountHash] = run
        npcRuns[throne] = run
        player.mes("You enter Yama's Domain.")
        log.info {
            "[Yama] generation=${run.generation} owner=${player.displayName} instance=${run.session.id} lobby"
        }
        return true
    }

    fun joinInvitation(player: Player): Boolean {
        if (
            participantRuns.containsKey(player.accountHash) ||
                instances.sessionForPlayer(player) != null
        ) {
            player.mes("You already have an active instance.")
            return false
        }
        val leaderHash = invitations[player.accountHash]
        if (leaderHash == null) {
            player.mes("You do not have a pending Yama invitation.")
            return false
        }
        val run = participantRuns[leaderHash]
        if (run == null || run.state != YamaState.LOBBY || run.participants.size >= 2) {
            player.mes("That invitation is no longer available.")
            invitations.remove(player.accountHash)
            return false
        }
        val result = instances.join(player, run.session, clock.cycle, forceAccess = true)
        if (result !is InstanceManager.Result.Joined) {
            player.mes(
                (result as? InstanceManager.Result.Failed)?.reason
                    ?: "You could not join that pact."
            )
            return false
        }
        prepare(player)
        resetEncounterClientState(player)
        PathingEntityCommon.telejump(player, collision, result.enter)
        instances.finalizeEntry(player, run.session, clock.cycle)
        player.lootDropDuration = YAMA_DROP_DURATION
        run.participants[player.accountHash] =
            Participant(player.accountHash, player.uid, run.participants.size)
        participantRuns[player.accountHash] = run
        invitations.remove(player.accountHash)
        player.mes("You join the pact in Yama's Domain.")
        owner(run)?.mes("${player.displayName} joins your Yama pact.")
        return true
    }

    fun selectContract(player: Player, contract: YamaContract) {
        val run = participantRuns[player.accountHash]
        if (run != null && (run.ownerHash != player.accountHash || run.state != YamaState.LOBBY)) {
            player.mes("Only the pact leader can select a contract before the fight.")
            return
        }
        pendingContracts[player.accountHash] = contract
        player.mes("Selected: ${contract.displayName}.")
    }

    fun confirmSelectedContract(player: Player) {
        val selected = pendingContracts[player.accountHash]
        if (selected == null || selected in setOf(YamaContract.NONE, YamaContract.CUSTOM)) {
            player.mes("Read a contract in your inventory first.")
        } else {
            player.mes("Your selected contract is ${selected.displayName}.")
        }
    }

    fun challenge(player: Player): Boolean {
        val run = participantRuns[player.accountHash]
        if (run == null || run.state != YamaState.LOBBY) {
            player.mes("Yama cannot be challenged right now.")
            return false
        }
        if (run.ownerHash != player.accountHash) {
            player.mes("Only the pact leader may issue the challenge.")
            return false
        }
        val contract = YamaLifecycleRules.selectedContract(pendingContracts, player.accountHash)
        if (!YamaLifecycleRules.canStartWithParty(contract, run.participants.size)) {
            player.mes("${contract.displayName} is a solo-only contract.")
            return false
        }
        if (!consumeEntry(player, contract)) return false
        run.contractSupplierHash = player.accountHash
        clearPendingLobbyState(run.participants.keys)
        run.contract = contract
        run.startCycle = clock.cycle
        run.observedHitpoints.clear()
        run.observedPrayer.clear()
        run.participants.values.mapNotNull(::resolve).forEach { member ->
            applyContractPlayerState(member, contract)
            run.observedHitpoints[member.accountHash] = member.hitpoints
            run.observedPrayer[member.accountHash] = member.stat("stat.prayer")
        }
        run.state = YamaState.SUMMONING
        run.stateCycle = YamaCombatTimeline.bossSpawnCycle(clock.cycle)
        run.throne.anim(anim(YamaAssets.ANIM_THRONE_SUMMON))
        run.throne.spotanim(spot(YamaAssets.GFX_THRONE_SUMMON))
        for (x in 30..32) spawnLoc(local(run.session, x, 19), YamaAssets.FIREWALL, Int.MAX_VALUE)
            ?.let(run.firewalls::add)
        run.participants.values.forEach { participant ->
            resolve(participant)?.let {
                setVarbit(it, YamaAssets.VARBIT_THRONE_STATE, YamaAssets.THRONE_STATE_EMPTY)
            }
        }
        player.mes(
            if (contract == YamaContract.NONE) "You challenge Yama."
            else "You seal ${contract.displayName} and challenge Yama."
        )
        return true
    }

    private fun consumeEntry(player: Player, contract: YamaContract): Boolean {
        val cost = YamaLifecycleRules.entryCost(contract) ?: return true
        val result = player.invDel(player.inv, cost.itemId, cost.quantity)
        if (!result.success) {
            player.mes(
                if (contract == YamaContract.CUSTOM) {
                    "You need ${cost.quantity} noted pineapple pizzas for a custom contract."
                } else {
                    "You no longer have ${contract.displayName}."
                }
            )
            return false
        }
        return true
    }

    fun openDossier(player: Player) {
        if (player.invDel(player.inv, YamaAssets.DOSSIER, 1).failure) return
        val contract = YamaContract.itemContracts[random.of(YamaContract.itemContracts.indices)]
        val result = player.invAdd(player.inv, requireNotNull(contract.itemId), 1)
        if (result.failure) {
            objects.add(
                requireItem(requireNotNull(contract.itemId)),
                player.coords,
                YAMA_DROP_DURATION,
                player,
            )
        }
        player.mes("The dossier contains ${contract.displayName}.")
    }

    fun tickAll() {
        runs.values.toList().forEach(::tick)
    }

    fun tick(player: Player) {
        participantRuns[player.accountHash]?.let(::tick)
    }

    fun externalTeleport(player: Player, instanceId: InstanceId) {
        clearPendingLobbyState(setOf(player.accountHash))
        val run = participantRuns[player.accountHash] ?: return
        if (run.session.id != instanceId) return

        closeEncounterBars(run, player)
        detachParticipantState(run, player)
        instances.leave(player, run.session, clock.cycle)
        player.mes("Your Yama encounter ended because you teleported out of its instance.")
        if (run.participants.isEmpty()) {
            abort(run, "last participant teleported out", teleport = false)
        } else {
            if (run.ownerHash == player.accountHash) {
                run.ownerHash = run.participants.keys.first()
            }
            online(run).forEach { member ->
                member.mes("${player.displayName} teleported out; your Yama pact continues.")
            }
            switchTarget(run)
            keepAggressive(run)
        }
    }

    fun leaveFromLogoutTab(player: Player, expectedInstanceId: InstanceId): RaidLeaveResult {
        val run = participantRuns[player.accountHash] ?: return RaidLeaveResult.Failed
        if (
            run.session.id != expectedInstanceId ||
                instances.sessionForPlayer(player)?.id != expectedInstanceId
        ) {
            return RaidLeaveResult.Failed
        }
        removeParticipant(player, "leave instance", teleport = true)
        val leftRun = participantRuns[player.accountHash] !== run
        val leftSession = instances.sessionForPlayer(player)?.id != expectedInstanceId
        return if (leftRun && leftSession) {
            player.mes("You leave Yama's Domain and return to the lobby.")
            RaidLeaveResult.Left
        } else {
            RaidLeaveResult.Failed
        }
    }

    private fun tick(run: Run) {
        if (run.lastTick == clock.cycle) return
        run.lastTick = clock.cycle
        val online = online(run)
        if (online.isEmpty()) {
            abort(run, "empty party", teleport = false)
            return
        }
        val outside = online.firstOrNull { instances.sessionForPlayer(it)?.id != run.session.id }
        if (outside != null) {
            externalTeleport(outside, run.session.id)
            return
        }
        // Player attacks resolve before LateCycle. Start transition immunity before any pending
        // Yama attack can resolve on the threshold tick.
        if (tryBeginJudge(run)) return
        processGlyphProtections(run)
        // Movement has already resolved for this game tick. Activate a glyph before resolving
        // impacts so stepping onto its centre on the landing tick can protect the party.
        processGlyphs(run)
        processGlyphAttacks(run)
        processPendingHits(run)
        processFireStreaks(run)
        processWaves(run)
        processFlares(run)
        processMeteors(run)
        processShadowCrashes(run)
        processForfeit(run)
        processDivineSeverance(run)
        online.forEach { run.previousPlayerTiles[it.accountHash] = it.coords }

        when (run.state) {
            YamaState.SUMMONING -> processSummoning(run)
            YamaState.PHASE_ONE,
            YamaState.PHASE_TWO,
            YamaState.PHASE_THREE -> processCombat(run)
            YamaState.JUDGE_ONE,
            YamaState.JUDGE_TWO -> processJudge(run)
            else -> Unit
        }
    }

    private fun processSummoning(run: Run) {
        if (run.boss == null && clock.cycle >= run.stateCycle) {
            val boss = spawnBoss(run, YamaRules.profile(run.contract).hitpoints)
            boss.anim(anim(YamaAssets.ANIM_SUMMON))
            boss.spotanim(spot(YamaAssets.GFX_SUMMON))
            boss.movementLocked = true
            run.stateCycle = clock.cycle + YamaCombatTimeline.ENABLE_ATTACK_DELAY
            return
        }
        val boss = run.boss
        if (boss != null && clock.cycle >= run.stateCycle) {
            run.state = YamaState.PHASE_ONE
            run.phase = 1
            boss.movementLocked = false
            spawnGlyphs(run)
            activateCombatPhase(run)
            openYamaBar(run, boss)
            keepAggressive(run)
        }
    }

    private fun processCombat(run: Run) {
        val boss = run.boss ?: return
        online(run).forEach { player ->
            bossHpBar.onUpdate(player, boss, boss.hitpoints, boss.baseHitpointsLvl)
        }
        keepAggressive(run)
        if (
            YamaRules.profile(run.contract).continuousFlares &&
                run.flares.isEmpty() &&
                clock.cycle >= run.nextContinuousFlareCycle
        ) {
            spawnVoidFlares(run)
            run.nextContinuousFlareCycle = clock.cycle + 30
        }
    }

    private fun tryBeginJudge(run: Run): Boolean {
        val boss = run.boss ?: return false
        val maximum = YamaRules.profile(run.contract).hitpoints
        return if (
            run.state == YamaState.PHASE_ONE &&
                boss.hitpoints <= YamaRules.firstThreshold(maximum, run.contract)
        ) {
            beginJudge(run, first = true)
            true
        } else if (
            run.state == YamaState.PHASE_TWO &&
                boss.hitpoints <= YamaRules.secondThreshold(maximum, run.contract)
        ) {
            beginJudge(run, first = false)
            true
        } else {
            false
        }
    }

    fun canAttack(npc: Npc, target: Player): Boolean = combatRun(npc, target) != null

    private fun combatRun(npc: Npc, target: Player): Run? {
        val run = participantRuns[target.accountHash] ?: return null
        return run.takeIf {
            it.boss === npc &&
                it.state in setOf(YamaState.PHASE_ONE, YamaState.PHASE_TWO, YamaState.PHASE_THREE)
        }
    }

    /**
     * Native BossCombat owns cadence and action counting; this adapter preserves native AoE order.
     */
    fun standardAttack(access: org.rsmod.api.npc.access.StandardNpcAccess, target: Player) {
        val run = combatRun(access.npc, target) ?: return
        standardAttack(access, run, target)
    }

    fun specialAttack(npc: Npc, target: Player, special: YamaSpecial) {
        val run = combatRun(npc, target) ?: return
        executeSpecial(run, special, target)
    }

    fun phaseThreeFollowup(npc: Npc, target: Player) {
        val run = combatRun(npc, target) ?: return
        if (run.phase == 3 && run.phaseThreeFourWavePending) {
            startShadowWaves(run, countOverride = 4, animate = false)
            run.phaseThreeFourWavePending = false
            run.phaseThreeWaveSets = 0
        }
    }

    fun afterAttack(npc: Npc, target: Player) {
        combatRun(npc, target)?.let(::switchTarget)
    }

    private fun activateCombatPhase(run: Run, delay: Int = 0) {
        val boss = run.boss ?: return
        val encounter = bossEncounters.of(boss)
        encounter.transitionTo(yamaCombatPhase(run.phase, run.contract), clock.cycle)
        // Native actionDelay retains the exact return-to-combat delay, independently of rate.
        encounter.lastAbilityTick = clock.cycle - encounter.expectedAttackRate()
        boss.actionDelay = maxOf(boss.actionDelay, clock.cycle + delay)
    }

    private fun pauseCombat(run: Run) {
        val boss = run.boss ?: return
        bossEncounters.existing(boss)?.transitionTo(YAMA_INACTIVE_PHASE, clock.cycle)
    }

    private fun standardAttack(
        access: org.rsmod.api.npc.access.StandardNpcAccess,
        run: Run,
        target: Player,
    ) {
        val boss = run.boss ?: return
        val style = chooseStandardStyle(run, target)
        val victims =
            if (style == YamaAttackStyle.MELEE)
                online(run).filter { distance(it.coords, target.coords) <= 3 }
            else listOf(target)
        when (style) {
            YamaAttackStyle.MELEE -> boss.anim(anim(YamaAssets.ANIM_MELEE))
            YamaAttackStyle.RANGED -> {
                boss.anim(anim(YamaAssets.ANIM_STANDARD))
                boss.spotanim(spot(YamaAssets.GFX_SHADOW_CAST))
            }
            YamaAttackStyle.MAGIC -> {
                boss.anim(anim(YamaAssets.ANIM_STANDARD))
                boss.spotanim(spot(YamaAssets.GFX_FIRE_CAST))
            }
        }
        victims.forEach { victim -> queueStandardAttack(access, run, victim, style) }

        run.lastStandardStyle = style
        when {
            run.phase == 3 && style == YamaAttackStyle.MELEE ->
                spawnVoidFlares(run, countOverride = 1)
            run.phase == 3 ->
                startShadowCrash(
                    run,
                    sets = YamaRules.profile(run.contract).shadowCrashSets,
                    animate = false,
                )
            run.phase in 1..2 &&
                run.contract.acquisitionShadowCrash &&
                style != YamaAttackStyle.MELEE ->
                startShadowCrash(run, targets = listOf(target), sets = 1, animate = false)
        }
    }

    private fun queueStandardAttack(
        access: org.rsmod.api.npc.access.StandardNpcAccess,
        run: Run,
        target: Player,
        style: YamaAttackStyle,
    ) {
        val glyph =
            when (style) {
                YamaAttackStyle.MAGIC -> YamaGlyph.FIRE
                YamaAttackStyle.RANGED -> YamaGlyph.SHADOW
                YamaAttackStyle.MELEE -> null
            }
        val impactGraphic =
            when (style) {
                YamaAttackStyle.MAGIC -> YamaAssets.GFX_FIRE_IMPACT
                YamaAttackStyle.RANGED -> YamaAssets.GFX_SHADOW_IMPACT
                YamaAttackStyle.MELEE -> null
            }
        val phase = run.phase
        val contract = run.contract
        var accurate = false
        var absorbed = false
        val modifier = PlayerHitModifier { player ->
            val active = participantRuns[player.accountHash] === run
            if (!active || !accurate) {
                damage = 0
            } else if (glyph != null && absorbWithGlyph(run, player, glyph)) {
                absorbed = true
                damage = 0
            } else {
                val maximum =
                    YamaRules.protectedStandardMaximum(
                        phase,
                        style,
                        isProtected(player, style),
                        contract,
                    )
                damage = if (maximum == 0) 0 else random.of(maximum + 1)
                if (contract == YamaContract.BLOODIED && maximum > 0) {
                    val hpPercent =
                        (player.hitpoints * 100 / player.baseHitpointsLvl.coerceAtLeast(1))
                            .coerceIn(0, 100)
                    damage = damage.coerceAtLeast(YamaRules.bloodiedMinimumHit(hpPercent, maximum))
                }
                damage = applyForfeitDamageDrain(run, player, damage)
            }
            NpcBypassProtectionPrayerPlayerHitModifier.run { modify(player) }
        }
        val attack =
            when (style) {
                YamaAttackStyle.MELEE -> CombatAttack.NpcMelee(MeleeAttackType.Crush)
                YamaAttackStyle.RANGED -> CombatAttack.NpcRanged(RangedAttackType.Standard)
                YamaAttackStyle.MAGIC ->
                    CombatAttack.NpcMagic(YamaRules.profile(contract).standardMaxHit)
            }
        combat.attack(
            access,
            target,
            attack,
            NpcAttackOverride(
                attackAnimation =
                    anim(
                        if (style == YamaAttackStyle.MELEE) YamaAssets.ANIM_MELEE
                        else YamaAssets.ANIM_STANDARD
                    ),
                maxHit = YamaRules.profile(contract).standardMaxHit,
                impactDelay = YamaCombatTimeline.STANDARD_IMPACT_DELAY,
                attackRate = 0,
                applyModifierOnImpact = true,
                hitModifier = modifier,
                onSuccessfulAttack = { _, _ -> accurate = true },
                onImpact = { _, _ ->
                    if (!absorbed && participantRuns[target.accountHash] === run) {
                        impactGraphic?.let { target.spotanim(spot(it)) }
                    }
                },
            ),
        )
    }

    private fun chooseStandardStyle(run: Run, target: Player): YamaAttackStyle {
        if (run.contract == YamaContract.SENSORY) return YamaAttackStyle.MELEE
        val boss = run.boss
        if (
            boss != null &&
                YamaHazardRules.occupiedRectangleDistance(boss.coords, boss.size, target.coords) <=
                    1
        ) {
            return YamaAttackStyle.MELEE
        }
        if (run.phase == 3) {
            run.phaseThreeStyle = YamaRules.nextAlternatingStandardStyle(run.phaseThreeStyle)
            return run.phaseThreeStyle
        }
        run.nextStandardStyle = YamaRules.nextAlternatingStandardStyle(run.nextStandardStyle)
        return run.nextStandardStyle
    }

    private fun executeSpecial(run: Run, special: YamaSpecial, target: Player) {
        when (special) {
            YamaSpecial.GLYPH_ATTACK -> startGlyphAttack(run, target)
            YamaSpecial.SHADOW_WAVES -> {
                startShadowWaves(run, animate = run.phase != 3)
                if (run.phase == 3 && ++run.phaseThreeWaveSets == 2) {
                    run.phaseThreeFourWavePending = true
                }
            }
            YamaSpecial.FIRE_STREAKS -> startFireStreaks(run, target)
            YamaSpecial.VOID_FLARES -> spawnVoidFlares(run)
            YamaSpecial.METEOR_STRIKE -> startMeteor(run, target)
            YamaSpecial.SHADOW_STOMP -> startShadowStomp(run, target)
            YamaSpecial.SHADOW_CRASH -> startShadowCrash(run)
        }
    }

    private fun startGlyphAttack(run: Run, target: Player) {
        val boss = run.boss ?: return
        val type = run.nextGlyphAttack
        run.nextGlyphAttack = YamaRules.nextGlyphAttack(type)
        val cast =
            if (type == YamaGlyph.FIRE) YamaAssets.GFX_FIREBALL_CAST
            else YamaAssets.GFX_SHADOW_SPIKE_CAST
        val projectile =
            if (type == YamaGlyph.FIRE) YamaAssets.GFX_FIREBALL_PROJECTILE
            else YamaAssets.GFX_SHADOW_SPIKE_PROJECTILE
        val impact =
            if (type == YamaGlyph.FIRE) YamaAssets.GFX_FIREBALL_IMPACT
            else YamaAssets.GFX_SHADOW_SPIKE_IMPACT
        target.mes(
            if (type == YamaGlyph.FIRE) "<col=ff8e32>Yama conjures a fire-based attack at you."
            else "<col=a53fff>Yama launches a shadow-based attack at you."
        )
        boss.anim(anim(YamaAssets.ANIM_SPECIAL))
        boss.spotanim(spot(cast))
        world.projAnim(
            ProjAnim(
                spotanim = projectile,
                startHeight = 2000,
                endHeight = 0,
                startTime = 65,
                endTime = 180,
                angle = 60,
                progress = 100,
                sourceIndex = boss.slotId + 1,
                targetIndex = -(target.slotId + 1),
                startCoord = boss.coords,
                endCoord = target.coords,
            )
        )
        run.glyphAttacks +=
            PendingGlyphAttack(
                accountHash = target.accountHash,
                sourceTile = boss.coords,
                targetTile = target.coords,
                type = type,
                impactCycle = YamaCombatTimeline.glyphAttackImpactCycle(clock.cycle),
                maximum = YamaRules.profile(run.contract).standardMaxHit,
                impactGraphic = impact,
            )
    }

    private fun startShadowWaves(run: Run, countOverride: Int? = null, animate: Boolean = true) {
        val boss = run.boss ?: return
        if (animate) boss.anim(anim(YamaAssets.ANIM_SPECIAL))
        val count = countOverride ?: if (run.phase >= 2 || run.contract.isContractFight) 2 else 1
        repeat(count) { index ->
            val origin =
                if (index % 2 == 0) YamaHazardRules.WaveOrigin.SOUTH_WEST
                else YamaHazardRules.WaveOrigin.SOUTH_EAST
            val pairDelay = (index / 2) * YamaCombatTimeline.PHASE_THREE_FOUR_WAVE_PAIR_GAP
            val startCycle = clock.cycle + YamaCombatTimeline.SHADOW_WAVE_TELEGRAPH + pairDelay
            val wave = ActiveWave(origin, startCycle, startCycle + 40)
            run.waves += wave
            for (localX in 20..40) {
                for (localZ in 20..42) {
                    val distance = YamaHazardRules.shadowWaveDistance(origin, localX, localZ)
                    if (distance < 0) continue
                    val delay =
                        YamaHazardRules.shadowWaveClientDelay(origin, localX, localZ) +
                            pairDelay * 30
                    world.spotanimMap(
                        SpotanimType(origin.graphic),
                        local(run.session, localX, localZ),
                        delay = delay,
                    )
                }
            }
        }
    }

    private fun startFireStreaks(run: Run, target: Player) {
        run.boss?.anim(anim(YamaAssets.ANIM_SPECIAL))
        val lanes = YamaRules.FIRE_STREAK_LANES
        val count = YamaRules.fireStreakCount(run.contract)
        val laneOffset = random.of(lanes.indices)
        repeat(count) { index ->
            val direction =
                if (index % 2 == 0) YamaHazardRules.HorizontalDirection.EAST
                else YamaHazardRules.HorizontalDirection.WEST
            val startX =
                if (direction == YamaHazardRules.HorizontalDirection.EAST)
                    YamaHazardRules.ARENA_MIN_X
                else YamaHazardRules.ARENA_MAX_X
            val localZ = lanes[(laneOffset + index) % lanes.size]
            val activeCycle = clock.cycle + YamaCombatTimeline.FIRE_STREAK_TELEGRAPH
            val fire = ActiveFire(startX, localZ, direction, activeCycle, activeCycle + 15)
            run.fireStreaks += fire
            repeat(YamaCombatTimeline.FIRE_STREAK_TELEGRAPH) { telegraph ->
                world.spotanimMap(
                    SpotanimType(YamaAssets.GFX_FIRE_WALL),
                    local(run.session, startX, localZ),
                    delay = telegraph * 30,
                )
            }
            repeat(15) { activeTick ->
                val front = YamaHazardRules.fireFrontTiles(startX, localZ, direction, activeTick)
                front.forEach { localX ->
                    if (localX !in YamaHazardRules.ARENA_MIN_X..YamaHazardRules.ARENA_MAX_X)
                        return@forEach
                    world.spotanimMap(
                        SpotanimType(YamaAssets.GFX_FIRE_WALL),
                        local(run.session, localX, localZ),
                        delay = (YamaCombatTimeline.FIRE_STREAK_TELEGRAPH + activeTick) * 30,
                    )
                }
            }
        }
    }

    private fun spawnVoidFlares(run: Run, countOverride: Int? = null) {
        val count = countOverride ?: YamaRules.flareCount(run.phase, run.contract)
        val positions = YamaRules.flareSpawnPositions(count, run.nextFlarePositionIndex)
        if (count == 1) run.nextFlarePositionIndex++
        positions.forEachIndexed { index, (localX, localZ) ->
            val tile = local(run.session, localX, localZ)
            val flare = spawnNpc(owner(run) ?: return, run.session, YamaAssets.VOID_FLARE, tile)
            flare.baseHitpointsLvl = YamaRules.flareHitpoints(run.phase, run.contract, index)
            flare.hitpoints = flare.baseHitpointsLvl
            flare.movementLocked = true
            flare.anim(anim(YamaAssets.ANIM_VOID_FLARE_SPAWN))
            val type = run.nextFlareElement
            run.nextFlareElement = YamaRules.nextGlyphAttack(type)
            run.flares[flare] =
                ActiveFlare(
                    type = type,
                    attackableCycle = clock.cycle + YamaRules.VOID_FLARE_ATTACKABLE_DELAY,
                    explodeCycle = clock.cycle + YamaRules.VOID_FLARE_BASE_LIFETIME,
                    damagedBy = mutableSetOf(),
                )
            npcRuns[flare] = run
        }
    }

    private fun startMeteor(run: Run, target: Player) {
        val boss = run.boss ?: return
        boss.say("Ven, estella infernus")
        boss.anim(anim(YamaAssets.ANIM_STOMP))
        val participantStartTiles =
            YamaHazardRules.meteorParticipantsAtCast(
                target.coords,
                online(run).associate { it.accountHash to it.coords },
            )
        boss.spotanim(spot(YamaAssets.GFX_METEOR_CAST))
        val tile = target.coords
        target.combatClearQueue()
        target.clearInteraction()
        target.abortRoute()
        target.tempMoveSpeed = MoveSpeed.Walk
        target.actionDelay = max(target.actionDelay, clock.cycle + 1)
        val helper = spawnNpc(owner(run) ?: target, run.session, YamaAssets.METEOR, tile)
        helper.movementLocked = true
        helper.spotanim(spot(YamaAssets.GFX_METEOR_PROJECTILE))
        val impactCycle = YamaCombatTimeline.meteorImpactCycle(clock.cycle)
        for (dx in -2..2) for (dz in -2..2) {
            world.spotanimMap(
                SpotanimType(YamaAssets.GFX_FALLING_ROCK_PLAYER),
                tile.translate(dx, dz),
                delay = (impactCycle - clock.cycle) * 30,
            )
        }
        run.meteors +=
            PendingMeteor(
                target.accountHash,
                participantStartTiles,
                tile,
                impactCycle,
                clock.cycle + 1,
                helper,
            )
    }

    private fun startShadowStomp(run: Run, target: Player) {
        val boss = run.boss ?: return
        boss.say("Umbra, proriumpse")
        boss.anim(anim(YamaAssets.ANIM_STOMP))
        boss.spotanim(spot(YamaAssets.GFX_SHADOW_ROOT_CAST))
        run.pendingHits +=
            PendingHit(
                YamaCombatTimeline.shadowStompImpactCycle(clock.cycle),
                target.accountHash,
                YamaAttackStyle.MAGIC,
                YamaRules.SHADOW_STOMP_MAX,
                YamaAssets.GFX_SHADOW_ROOT_IMPACT,
                prayerDrain = YamaRules.SHADOW_STOMP_PRAYER_DRAIN,
                rootTicks = YamaRules.SHADOW_STOMP_ROOT_TICKS,
                glyph = YamaGlyph.SHADOW,
            )
    }

    private fun startShadowCrash(
        run: Run,
        targets: List<Player> = online(run),
        sets: Int = YamaRules.profile(run.contract).shadowCrashSets,
        animate: Boolean = true,
    ) {
        val boss = run.boss ?: return
        if (animate) boss.anim(anim(YamaAssets.ANIM_SPECIAL))
        targets.forEach { target ->
            run.shadowCrashSequences +=
                ActiveCrashSequence(
                    accountHash = target.accountHash,
                    lockCycles = YamaCombatTimeline.shadowCrashLockCycles(clock.cycle, sets),
                    orientations =
                        YamaHazardRules.crashSequence(
                            firstVertical = random.of(2) == 0,
                            sets = sets,
                        ),
                )
        }
    }

    private fun beginJudge(run: Run, first: Boolean) {
        val boss = run.boss ?: return
        run.savedBossHitpoints = boss.hitpoints
        boss.say("Enough.")
        boss.anim(anim(YamaAssets.ANIM_PORTAL))
        boss.spotanim(spot(YamaAssets.GFX_PORTAL_SHADOW))
        boss.clearInteraction()
        boss.movementLocked = true
        clearTransientHazards(run)
        clearGlyphs(run)
        online(run).forEach(Player::combatClearQueue)
        run.state = if (first) YamaState.JUDGE_ONE else YamaState.JUDGE_TWO
        pauseCombat(run)
        run.phase = if (first) 1 else 2
        run.stateCycle = YamaCombatTimeline.judgeArrivalCycle(clock.cycle)
        run.judgeHudCloseCycle = clock.cycle + 2
        run.judgeHudClosed = false
        run.judgeStarted = false
        run.judgeEntryAnimCycle = clock.cycle + 2
        run.judgeEntryAnimPlayed = false
        run.judgeHitpoints = YamaRules.JUDGE_HITPOINTS
        run.judgeDeathQueued = false
        run.judgeReturnPrepared = false
        run.judgeReturnAnimPlayed = false
        run.judgeReturnAnimCycle = Int.MAX_VALUE
        run.judgeReturnTeleportCycle = Int.MAX_VALUE
        run.participants.values.forEach { participant ->
            participant.stoneStep = 0
            participant.nextJudgeSurgeCycle = Int.MAX_VALUE
            participant.nextJudgeAttackCycle = Int.MAX_VALUE
            participant.judgeAttackCount = 0
            participant.lastJudgeDamageCycle = Int.MIN_VALUE
            participant.judgeCameraResetCycle = Int.MAX_VALUE
            val target = resolve(participant) ?: return@forEach
            target.spotanim(
                spot(YamaAssets.GFX_PORTAL),
                slot = YamaRules.JUDGE_PORTAL_SPOTANIM_SLOT,
            )
        }
    }

    private fun startJudgeStage(run: Run) {
        run.judgeStarted = true
        run.boss?.let { boss ->
            PathingEntityCommon.telejump(boss, collision, local(run.session, 27, 23))
            boss.movementLocked = true
        }
        run.participants.values.forEach { participant ->
            val target = resolve(participant) ?: return@forEach
            val start = judgeStartTile(run, participant.slot)
            setVarbit(target, YamaAssets.VARBIT_TRANSITION_PHASE, 1)
            setVarbit(target, YamaAssets.VARBIT_TRANSITION_DAMAGE, 0)
            PathingEntityCommon.telejump(target, collision, start)
            target.anim(anim(YamaAssets.ANIM_PLAYER_PORTAL_FACE))
            target.spotanim(
                spot(YamaAssets.GFX_PORTAL_FALL),
                height = YamaRules.JUDGE_PORTAL_FALL_HEIGHT,
                slot = YamaRules.JUDGE_PORTAL_SPOTANIM_SLOT,
            )
            resetJudgeCamera(target)
            val judge =
                spawnNpc(
                    owner(run) ?: target,
                    run.session,
                    YamaAssets.JUDGE,
                    judgeTile(run, participant.slot),
                )
            judge.baseHitpointsLvl = YamaRules.JUDGE_HITPOINTS
            judge.hitpoints = YamaRules.JUDGE_HITPOINTS
            judge.movementLocked = true
            run.judges += judge
            npcRuns[judge] = run
            run.judgeBlockedStyles[participant.accountHash] =
                mutableSetOf(YamaAttackStyle.MELEE, YamaAttackStyle.RANGED)
            participant.nextJudgeSurgeCycle = clock.cycle
        }
    }

    fun jumpStone(player: Player, destination: CoordGrid): Boolean {
        val run = participantRuns[player.accountHash] ?: return false
        if (run.state !in setOf(YamaState.JUDGE_ONE, YamaState.JUDGE_TWO)) return false
        val participant = run.participants[player.accountHash] ?: return false
        if (!run.judgeStarted) {
            player.mes("Wait for the Judge's cavern to form.")
            return false
        }
        if (YamaRules.judgeCrossingComplete(participant.stoneStep)) {
            player.mes("You have already crossed to the Judge.")
            return false
        }
        val origin = local(run.session, 0, 0)
        val localX = destination.x - origin.x
        val localZ = destination.z - origin.z
        if (
            destination.level != origin.level ||
                !YamaRules.isValidJudgeStone(
                    participant.slot,
                    participant.stoneStep,
                    localX,
                    localZ,
                )
        ) {
            player.mes("Jump to a stone in the next row.")
            return false
        }
        val start = player.coords
        participant.stoneStep++
        val crossingComplete = YamaRules.judgeCrossingComplete(participant.stoneStep)
        val landing =
            if (crossingComplete) {
                local(
                    run.session,
                    YamaRules.judgeLandingX(participant.slot),
                    YamaRules.JUDGE_LANDING_Z,
                )
            } else {
                destination
            }
        player.combatClearQueue()
        PathingEntityCommon.exactMove(
            player,
            start,
            landing,
            delay1 = 0,
            delay2 = YamaCombatTimeline.STONE_JUMP_CLIENT_CYCLES,
            dir = YamaRules.exactMoveAngle(start.x, start.z, landing.x, landing.z),
            collision = collision,
        )
        player.anim(anim(YamaAssets.ANIM_SHORT_JUMP))
        if (crossingComplete) {
            participant.nextJudgeSurgeCycle = Int.MAX_VALUE
            participant.nextJudgeAttackCycle =
                clock.cycle + YamaCombatTimeline.JUDGE_POST_CROSS_ATTACK_DELAY
            clearJudgeSurges(run, participant.slot)
            val judge = run.judges.getOrNull(participant.slot) ?: run.judges.firstOrNull()
            if (judge != null) {
                bossHpBar.onOpen(player, judge)
                bossHpBar.onUpdate(player, judge, run.judgeHitpoints, YamaRules.JUDGE_HITPOINTS)
            }
        }
        return true
    }

    private fun processJudge(run: Run) {
        run.participants.values.forEach { participant ->
            if (clock.cycle < participant.judgeCameraResetCycle) return@forEach
            participant.judgeCameraResetCycle = Int.MAX_VALUE
            resolve(participant)?.let { player -> resetJudgeCamera(player) }
        }
        if (!run.judgeHudClosed && clock.cycle >= run.judgeHudCloseCycle) {
            run.boss?.let { boss ->
                online(run).forEach { bossHpBar.onClose(it, boss, instant = true) }
            }
            run.judgeHudClosed = true
        }
        if (!run.judgeStarted) {
            if (!run.judgeEntryAnimPlayed && clock.cycle >= run.judgeEntryAnimCycle) {
                online(run).forEach { it.anim(anim(YamaAssets.ANIM_PLAYER_PORTAL), delay = 5) }
                run.judgeEntryAnimPlayed = true
            }
            if (clock.cycle < run.stateCycle) return
            startJudgeStage(run)
        }
        processJudgeSurges(run)
        if (run.judgeDeathQueued) {
            if (!run.judgeReturnPrepared && clock.cycle >= run.stateCycle) {
                prepareReturnFromJudge(run)
            }
            if (
                run.judgeReturnPrepared &&
                    !run.judgeReturnAnimPlayed &&
                    clock.cycle >= run.judgeReturnAnimCycle
            ) {
                online(run).forEach { it.anim(anim(YamaAssets.ANIM_PLAYER_PORTAL), delay = 5) }
                run.judgeReturnAnimPlayed = true
            }
            if (run.judgeReturnPrepared && clock.cycle >= run.judgeReturnTeleportCycle) {
                completeReturnFromJudge(run)
            }
            return
        }
        run.participants.values.forEach { participant ->
            if (
                !YamaRules.judgeCrossingComplete(participant.stoneStep) ||
                    clock.cycle < participant.nextJudgeAttackCycle
            )
                return@forEach
            val target = resolve(participant) ?: return@forEach
            participant.nextJudgeAttackCycle =
                clock.cycle + YamaRules.profile(run.contract).judgeAttackRate
            val judge =
                run.judges.getOrNull(participant.slot) ?: run.judges.firstOrNull() ?: return@forEach
            judge.anim(anim(69))
            repeat(YamaRules.profile(run.contract).judgeTargets) { offset ->
                val tile = target.coords.translate(if (offset == 0) 0 else 1, 0)
                world.spotanimMap(
                    SpotanimType(YamaAssets.GFX_JUDGE_FIREBALL_SHADOW),
                    tile,
                    delay = 30,
                )
                world.projAnim(
                    ProjAnim(
                        spotanim = YamaAssets.GFX_JUDGE_FIREBALL_PROJECTILE,
                        startHeight = 345,
                        endHeight = 0,
                        startTime = 0,
                        endTime = 120,
                        angle = 30,
                        progress = 128,
                        sourceIndex = judge.slotId + 1,
                        targetIndex = 0,
                        startCoord = judge.coords.translate(1, 1),
                        endCoord = tile,
                    )
                )
                world.spotanimMap(
                    SpotanimType(YamaAssets.GFX_JUDGE_FIREBALL_IMPACT),
                    tile,
                    height = 30,
                    delay = 120,
                )
                run.pendingHits +=
                    PendingHit(
                        YamaCombatTimeline.judgeFireballImpactCycle(clock.cycle),
                        target.accountHash,
                        YamaAttackStyle.MAGIC,
                        YamaRules.JUDGE_FIREBALL_MAX,
                        targetTile = tile,
                        damagesJudgeIfUnder = true,
                        judgeSlot = participant.slot,
                    )
            }
            recordJudgeAttack(run, participant, target)
        }
    }

    private fun recordJudgeAttack(run: Run, participant: Participant, target: Player) {
        participant.judgeAttackCount++
        if (!YamaRules.judgeCavernDamageTurn(participant.judgeAttackCount)) return
        repeat(3) { axis -> Camera.camShake(target, axis, 5 + random.of(4), 0, 0) }
        participant.judgeCameraResetCycle = clock.cycle + 1
        resolvedMechanicFixedHit(run, target, 5 + random.of(3))
    }

    private fun spawnJudgeSurges(run: Run, participant: Participant, target: Player) {
        val judge = run.judges.getOrNull(participant.slot) ?: run.judges.firstOrNull() ?: return
        judge.anim(anim(69))
        val lanes = linkedSetOf<Int>()
        val surgeCount = YamaRules.judgeSurgeCount(random.of(2))
        while (lanes.size < surgeCount) lanes += random.of(YamaRules.JUDGE_WEST_X_RANGE)
        lanes.forEach { westX ->
            val localX = if (participant.slot == 0) westX else YamaJudgeRules.mirrorLocalX(westX)
            val surge =
                spawnNpc(target, run.session, YamaAssets.FIRE_SURGE, local(run.session, localX, 49))
            surge.movementLocked = true
            surge.hideAllOps()
            run.judgeSurges += ActiveJudgeSurge(surge, participant.slot, clock.cycle)
            npcRuns[surge] = run
        }
        recordJudgeAttack(run, participant, target)
    }

    private fun processJudgeSurges(run: Run) {
        if (!run.judgeDeathQueued) {
            run.participants.values.forEach { participant ->
                if (
                    YamaRules.judgeCrossingComplete(participant.stoneStep) ||
                        clock.cycle < participant.nextJudgeSurgeCycle
                )
                    return@forEach
                val target = resolve(participant) ?: return@forEach
                spawnJudgeSurges(run, participant, target)
                participant.nextJudgeSurgeCycle =
                    clock.cycle + YamaCombatTimeline.JUDGE_SURGE_INTERVAL
            }
        }
        val arenaOrigin = local(run.session, 0, 0)
        val iterator = run.judgeSurges.iterator()
        while (iterator.hasNext()) {
            val active = iterator.next()
            val surge = active.npc
            if (!surge.isSlotAssigned) {
                iterator.remove()
                npcRuns.remove(surge)
                continue
            }
            if (clock.cycle <= active.spawnCycle) continue
            val start = surge.coords
            val destination = start.translate(0, -1)
            PathingEntityCommon.exactMove(
                surge,
                start,
                destination,
                delay1 = 0,
                delay2 = YamaCombatTimeline.JUDGE_SURGE_MOVE_CLIENT_CYCLES,
                dir = 0,
                collision = collision,
            )
            online(run)
                .filter { player ->
                    run.participants[player.accountHash]?.slot == active.slot &&
                        player.coords == destination &&
                        clock.cycle >
                            (run.lastJudgeSurgeHit[player.accountHash] ?: Int.MIN_VALUE) + 1
                }
                .forEach { player ->
                    run.lastJudgeSurgeHit[player.accountHash] = clock.cycle
                    player.spotanim(spot(YamaAssets.GFX_FIRE_SURGE_IMPACT))
                    resolvedMechanicHit(run, player, YamaRules.JUDGE_SURGE_MAX)
                }
            if (destination.z - arenaOrigin.z < 28) {
                iterator.remove()
                npcRuns.remove(surge)
                npcs.del(surge, Int.MAX_VALUE)
            }
        }
    }

    private fun clearJudgeSurges(run: Run, slot: Int? = null) {
        val iterator = run.judgeSurges.iterator()
        while (iterator.hasNext()) {
            val active = iterator.next()
            if (slot != null && active.slot != slot) continue
            iterator.remove()
            npcRuns.remove(active.npc)
            if (active.npc.isSlotAssigned) npcs.del(active.npc, Int.MAX_VALUE)
        }
        if (slot == null) run.lastJudgeSurgeHit.clear()
    }

    private fun prepareReturnFromJudge(run: Run) {
        clearJudgeSurges(run)
        run.judges.toList().forEachIndexed { index, judge ->
            run.participants.values
                .firstOrNull { it.slot == index }
                ?.let(::resolve)
                ?.let { bossHpBar.onClose(it, judge, instant = true) }
            if (judge.isSlotAssigned) npcs.del(judge, Int.MAX_VALUE)
            npcRuns.remove(judge)
        }
        run.judges.clear()
        run.participants.values.forEach { participant ->
            resolve(participant)?.let { player ->
                setVarbit(player, YamaAssets.VARBIT_TRANSITION_PHASE, 0)
                setVarbit(player, YamaAssets.VARBIT_TRANSITION_DAMAGE, 0)
            }
        }
        val boss = run.boss ?: spawnBoss(run, run.savedBossHitpoints)
        boss.hitpoints = run.savedBossHitpoints.coerceIn(1, boss.baseHitpointsLvl)
        PathingEntityCommon.telejump(boss, collision, local(run.session, 29, 28))
        boss.movementLocked = true
        run.judgeReturnPrepared = true
    }

    private fun completeReturnFromJudge(run: Run) {
        val nextPhase = if (run.state == YamaState.JUDGE_ONE) 2 else 3
        run.participants.values.forEach { participant ->
            resolve(participant)?.let { player ->
                PathingEntityCommon.telejump(player, collision, judgeReturnTile(run, participant))
                player.anim(anim(YamaAssets.ANIM_PLAYER_PORTAL_FACE))
                player.spotanim(
                    spot(YamaAssets.GFX_PORTAL_FALL),
                    height = YamaRules.JUDGE_PORTAL_FALL_HEIGHT,
                    slot = YamaRules.JUDGE_PORTAL_SPOTANIM_SLOT,
                )
                resetJudgeCamera(player)
            }
        }
        val boss = run.boss ?: return
        run.judgeStarted = false
        run.phase = nextPhase
        run.state = if (nextPhase == 2) YamaState.PHASE_TWO else YamaState.PHASE_THREE
        boss.movementLocked = nextPhase == 3
        if (nextPhase != 3) spawnGlyphs(run)
        run.phaseThreeWaveSets = 0
        run.phaseThreeFourWavePending = false
        activateCombatPhase(run, YamaCombatTimeline.PHASE_RESUME_ATTACK_DELAY)
        openYamaBar(run, boss)
        keepAggressive(run)
    }

    fun modifyNpcHit(player: Player, npc: Npc, hit: HitBuilder) {
        val run = npcRuns[npc]
        if (run == null || player.accountHash !in run.participants || hit.damage < 0) {
            hit.damage = 0
            return
        }
        if (npc === run.boss) {
            if (
                run.state !in setOf(YamaState.PHASE_ONE, YamaState.PHASE_TWO, YamaState.PHASE_THREE)
            ) {
                hit.damage = 0
                return
            }

            if (run.contract == YamaContract.BLOODIED && hit.damage > 0) {
                val hpPercent =
                    (player.hitpoints * 100 / player.baseHitpointsLvl.coerceAtLeast(1)).coerceIn(
                        0,
                        100,
                    )
                hit.damage =
                    hit.damage.coerceAtLeast(
                        YamaRules.bloodiedMinimumHit(hpPercent, player.vars["varp.com_maxhit"])
                    )
            }
            run.contribution[player.accountHash] =
                (run.contribution[player.accountHash] ?: 0) + hit.damage.coerceAtMost(npc.hitpoints)
            if (run.contract == YamaContract.NONE) {
                val style = attackTypes.get(player)
                if (style?.isMagic == true) {
                    run.recentPlayerMagic = true
                    npc.magicDefenceLvl = npc.baseMagicDefenceLvl + 60
                } else if (style?.isMelee == true) {
                    run.recentPlayerMagic = false
                    npc.magicDefenceLvl = (npc.baseMagicDefenceLvl - 30).coerceAtLeast(1)
                }
            }
            return
        }
        val flare = run.flares[npc]
        if (flare != null) {
            if (clock.cycle < flare.attackableCycle) {
                hit.damage = 0
                return
            }
            hit.damage =
                YamaRules.flareModifiedDamage(
                    rolledDamage = hit.damage,
                    currentMaxHit = player.vars["varp.com_maxhit"],
                    style = currentAttackStyle(player, hit),
                    scorchingBow = isScorchingBow(hit),
                    demonbane = isDemonbane(hit),
                )
            if (hit.damage > 0) {
                flare.damagedBy += player.accountHash
                player.actionDelay = clock.cycle
            }
            if (hit.damage >= npc.hitpoints) npc.anim(anim(YamaAssets.ANIM_VOID_FLARE_DEATH))
            return
        }
        if (npc in run.judges) {
            if (run.judgeDeathQueued) {
                hit.damage = 0
                return
            }
            val participant = run.participants[player.accountHash]
            if (participant == null || !YamaRules.judgeCrossingComplete(participant.stoneStep)) {
                hit.damage = 0
                return
            }
            val style = currentAttackStyle(player, hit)
            val blocked =
                run.judgeBlockedStyles.getOrPut(player.accountHash) {
                    YamaJudgeRules.initialBlockedStyles().toMutableSet()
                }
            val resolution =
                YamaJudgeRules.resolvePlayerHit(
                    style = style,
                    rolledDamage = hit.damage,
                    currentMaxHit = player.vars["varp.com_maxhit"],
                    blockedStyles = blocked,
                    alreadyAcceptedThisTick = participant.lastJudgeDamageCycle == clock.cycle,
                    sharedHitpoints = run.judgeHitpoints,
                )
            hit.damage = resolution.damage
            if (resolution.accepted) {
                participant.lastJudgeDamageCycle = clock.cycle
                blocked.clear()
                blocked += resolution.nextBlockedStyles
                val previousImpact = hit.impactHandler
                hit.impactHandler = HitImpactHandler { impact ->
                    onJudgeHit(npc, impact.damage)
                    previousImpact?.onImpact(impact)
                }
            }
            return
        }
        hit.damage = 0
    }

    fun onJudgeHit(npc: Npc, damage: Int) {
        val run = npcRuns[npc] ?: return
        if (npc !in run.judges || damage <= 0) return
        if (run.judgeDeathQueued) return
        synchronizeJudgeHitpoints(
            run,
            YamaJudgeRules.remainingHitpoints(run.judgeHitpoints, damage),
        )
        if (run.judgeHitpoints == 0) beginJudgeDeath(run, queueNpcDeath = false)
    }

    fun judgeMeleeApRange(player: Player, npc: Npc): Int? {
        val run = npcRuns[npc] ?: return null
        if (npc !in run.judges) return null
        val participant = run.participants[player.accountHash] ?: return null
        return if (YamaRules.judgeCrossingComplete(participant.stoneStep)) {
            YamaJudgeRules.MELEE_AP_RANGE
        } else {
            null
        }
    }

    fun beginBossDeath(npc: Npc): Run? {
        val run = npcRuns[npc] ?: return null
        if (
            run.boss !== npc ||
                run.state in setOf(YamaState.DYING, YamaState.DEFEATED, YamaState.INVALIDATED)
        )
            return null
        run.state = YamaState.DYING
        pauseCombat(run)
        npc.anim(anim(YamaAssets.ANIM_DESPAWN))
        npc.spotanim(spot(YamaAssets.GFX_DESPAWN))
        clearTransientHazards(run)
        clearGlyphs(run)
        online(run).forEach(Player::combatClearQueue)
        return run
    }

    fun finishBossDeath(npc: Npc) {
        val run = npcRuns.remove(npc) ?: return
        if (run.state != YamaState.DYING) return
        val ticks = clock.cycle - run.startCycle
        var returnedContract = false
        online(run).forEach { player ->
            val rewardTile = rewardTile(run, player)
            closeEncounterBars(run, player)
            val damage = run.contribution[player.accountHash] ?: 0
            val roll =
                rewardService.roll(
                    YamaRewardInput(
                        damage = damage,
                        maximumHitpoints = YamaRules.profile(run.contract).hitpoints,
                        contract = run.contract,
                        eliteClueDenominator =
                            player.eliteClueDropDenominator(YAMA_ELITE_CLUE_DENOMINATOR),
                    )
                )
            deliverRewards(run, player, rewardTile, roll)
            if (
                !returnedContract &&
                    YamaLifecycleRules.shouldReturnContract(
                        contract = run.contract,
                        supplierHash = run.contractSupplierHash,
                        recipientHash = player.accountHash,
                    )
            ) {
                giveItem(player, requireNotNull(run.contract.itemId), 1, rewardTile)
                returnedContract = true
            }
            val records = recordSuccessfulKill(player, ticks)
            player.mes("Your Yama success count is: <col=ff0000>${records.killCount}</col>.")
            clearContractPlayerState(player)
            PathingEntityCommon.telejump(player, collision, rewardTile)
            resetEncounterClientState(player)
        }
        run.boss = null
        run.state = YamaState.DEFEATED
        run.throne.transmog(requireNpc(YamaAssets.THRONE_EMPTY), Int.MAX_VALUE)
        run.throne.anim(anim(YamaAssets.ANIM_THRONE_RESPAWN))
        run.throne.spotanim(spot(YamaAssets.GFX_THRONE_RESPAWN))
        log.info {
            "[Yama] generation=${run.generation} complete ticks=$ticks contract=${run.contract}"
        }
    }

    private fun deliverRewards(
        run: Run,
        player: Player,
        rewardTile: CoordGrid,
        roll: YamaRewardRoll,
    ) {
        when (roll.primary) {
            YamaPrimaryReward.JUNK ->
                giveItem(player, YamaRewardRules.junkItemId(random.of(11)), 1, rewardTile)
            YamaPrimaryReward.UNIQUE -> rollRewardTable(player, yamaUniqueRoll, rewardTile)
            YamaPrimaryReward.DOSSIER -> giveItem(player, YamaAssets.DOSSIER, 1, rewardTile)
            YamaPrimaryReward.LOCKBOX -> giveItem(player, "obj.forgotten_lockbox", 1, rewardTile)
            YamaPrimaryReward.SHARDS ->
                giveItem(player, YamaAssets.OATHPLATE_SHARDS, roll.primaryQuantity, rewardTile)
            YamaPrimaryReward.STANDARD ->
                rollRewardTable(player, yamaStandardRoll, rewardTile, roll)
            YamaPrimaryReward.NONE -> Unit
        }
        if (YamaTertiaryReward.PET in roll.tertiaries) {
            giveItem(player, YamaAssets.YAMI, 1, rewardTile)
        }
        if (YamaTertiaryReward.ELITE_CLUE in roll.tertiaries) {
            player.clueScrollTransformObj("obj.trail_elite_emote_exp1")?.let {
                giveItem(player, it, 1, rewardTile)
            }
        }
        if (roll.grantContractReward) giveContractReward(run, player, rewardTile)
    }

    private fun rollRewardTable(
        player: Player,
        table: dtx.core.Rollable<Player, DropRollItem>,
        rewardTile: CoordGrid,
        contributionRoll: YamaRewardRoll? = null,
    ) {
        when (val result = table.roll(player, ArgMap()).flatten()) {
            is RollResult.Single -> giveDrop(player, result.result, rewardTile, contributionRoll)
            is RollResult.ListOf ->
                result.results.forEach { giveDrop(player, it, rewardTile, contributionRoll) }
            else -> Unit
        }
    }

    private fun giveContractReward(run: Run, player: Player, rewardTile: CoordGrid) {
        when (run.contract) {
            YamaContract.CATALYST -> giveItem(player, 30771, 2000, rewardTile)
            YamaContract.WORM -> giveItem(player, "obj.diabolic_worms", 250, rewardTile)
            YamaContract.SHARD -> giveItem(player, YamaAssets.OATHPLATE_SHARDS, 45, rewardTile)
            YamaContract.OATHPLATE ->
                giveItem(
                    player,
                    listOf(
                        YamaAssets.OATHPLATE_HELM,
                        YamaAssets.OATHPLATE_CHEST,
                        YamaAssets.OATHPLATE_LEGS,
                    )[random.of(3)],
                    1,
                    rewardTile,
                )
            YamaContract.HARMONY -> giveItem(player, YamaAssets.SOULFLAME_HORN, 1, rewardTile)
            YamaContract.FAMILIAR -> {
                val damage = run.contribution[player.accountHash] ?: 0
                if (
                    YamaRules.petEligible(damage, YamaRules.profile(run.contract).hitpoints) &&
                        random.of(100) == 0
                ) {
                    giveItem(player, YamaAssets.YAMI, 1, rewardTile)
                }
            }
            YamaContract.GLYPHIC -> giveItem(player, YamaAssets.SIGIL_BOTTOM, 1, rewardTile)
            YamaContract.SENSORY -> giveItem(player, YamaAssets.SIGIL_LEFT, 1, rewardTile)
            YamaContract.BLOODIED -> giveItem(player, YamaAssets.SIGIL_MIDDLE, 1, rewardTile)
            YamaContract.DIVINE -> giveItem(player, YamaAssets.SIGIL_RIGHT, 1, rewardTile)
            YamaContract.FORFEIT -> giveItem(player, YamaAssets.SIGIL_TOP, 1, rewardTile)
            else -> Unit
        }
    }

    private fun giveDrop(
        player: Player,
        drop: DropRollItem,
        rewardTile: CoordGrid,
        contributionRoll: YamaRewardRoll? = null,
    ) {
        if (drop.isNothing || !drop.condition(player)) return
        val obj = drop.transformObj(player) ?: drop.obj
        val baseCount = drop.rollCount(random)
        val count = contributionRoll?.scaledStandardQuantity(baseCount) ?: baseCount
        if (count > 0) objects.add(obj, rewardTile, YAMA_DROP_DURATION, player, count)
    }

    private fun giveItem(player: Player, id: Int, count: Int, rewardTile: CoordGrid) =
        objects.add(requireItem(id), rewardTile, YAMA_DROP_DURATION, player, count)

    private fun giveItem(player: Player, internal: String, count: Int, rewardTile: CoordGrid) =
        objects.add(internal, rewardTile, YAMA_DROP_DURATION, player, count)

    fun finishAuxiliaryDeath(npc: Npc) {
        val run = npcRuns.remove(npc) ?: return
        if (run.flares.remove(npc) != null) return
        if (npc in run.judges && run.judgeDeathQueued) return
    }

    private fun processGlyphAttacks(run: Run) {
        val iterator = run.glyphAttacks.iterator()
        while (iterator.hasNext()) {
            val attack = iterator.next()
            if (clock.cycle < attack.impactCycle) continue
            iterator.remove()
            run.pendingHits +=
                PendingHit(
                    impactCycle = attack.impactCycle,
                    accountHash = attack.accountHash,
                    style =
                        if (attack.type == YamaGlyph.FIRE) YamaAttackStyle.MAGIC
                        else YamaAttackStyle.RANGED,
                    maximum = attack.maximum,
                    graphic = attack.impactGraphic,
                    glyph = attack.type,
                )
        }
    }

    private fun processPendingHits(run: Run) {
        val iterator = run.pendingHits.iterator()
        while (iterator.hasNext()) {
            val pending = iterator.next()
            if (pending.impactCycle > clock.cycle) continue
            iterator.remove()
            if (pending.damagesJudgeIfUnder) {
                if (run.judgeDeathQueued) continue
                val judge = run.judges.getOrNull(pending.judgeSlot ?: 0) ?: run.judges.firstOrNull()
                val tile = pending.targetTile
                if (
                    judge != null &&
                        tile != null &&
                        YamaRules.judgeFireballHitsJudge(
                            judge.coords.x,
                            judge.coords.z,
                            judge.size,
                            tile.x,
                            tile.z,
                        )
                ) {
                    damageJudge(
                        run,
                        YamaRules.JUDGE_FIREBALL_SELF_MIN +
                            random.of(
                                YamaRules.JUDGE_FIREBALL_SELF_MAX -
                                    YamaRules.JUDGE_FIREBALL_SELF_MIN + 1
                            ),
                    )
                    continue
                }
            }
            val player = run.participants[pending.accountHash]?.let(::resolve) ?: continue
            if (pending.targetTile != null && player.coords != pending.targetTile) continue
            if (pending.glyph != null && absorbWithGlyph(run, player, pending.glyph)) continue
            pending.graphic?.let { player.spotanim(spot(it)) }
            if (pending.prayerDrain > 0)
                player.statSub("stat.prayer", constant = pending.prayerDrain, percent = 0)
            if (pending.rootTicks > 0) CombatEffects.freeze(player, pending.rootTicks)
            val maxHit = pending.maximum.coerceAtLeast(0)
            var damage = if (maxHit == 0) 0 else random.of(maxHit + 1)
            if (run.contract == YamaContract.BLOODIED && maxHit > 0) {
                val hpPercent =
                    (player.hitpoints * 100 / player.baseHitpointsLvl.coerceAtLeast(1)).coerceIn(
                        0,
                        100,
                    )
                damage = damage.coerceAtLeast(YamaRules.bloodiedMinimumHit(hpPercent, maxHit))
            }
            damage = applyForfeitDamageDrain(run, player, damage)
            player.takeInstantHit(
                type = hitType(pending.style),
                damage = damage,
                processor = instantHitProcessor,
                modifier = NpcBypassProtectionPrayerPlayerHitModifier,
            )
        }
    }

    private fun processGlyphProtections(run: Run) {
        val iterator = run.protections.iterator()
        while (iterator.hasNext()) {
            val (accountHash, protection) = iterator.next()
            if (clock.cycle < protection.untilCycle) continue
            iterator.remove()
            run.participants[accountHash]
                ?.let(::resolve)
                ?.mes(YamaRules.glyphFadeMessage(protection.type))
        }
    }

    private fun processGlyphs(run: Run) {
        if (run.state !in setOf(YamaState.PHASE_ONE, YamaState.PHASE_TWO)) return
        run.glyphs.forEach { glyph ->
            if (!glyph.active && clock.cycle >= glyph.inactiveVisualCycle) {
                glyph.transientLoc?.let { locs.del(it, Int.MAX_VALUE) }
                glyph.transientLoc = null
                glyph.inactiveVisualCycle = Int.MAX_VALUE
            }
            if (!glyph.active && clock.cycle >= glyph.respawnCycle) {
                glyph.loc?.let { locs.del(it, Int.MAX_VALUE) }
                glyph.transientLoc?.let { locs.del(it, Int.MAX_VALUE) }
                glyph.transientLoc = null
                glyph.loc = spawnLoc(glyph.tile, YamaAssets.activeGlyph(glyph.type), Int.MAX_VALUE)
                glyph.active = true
            }
            if (!glyph.active) return@forEach
            // The captured loc coordinate is the south-west anchor of a 3x3 object. OSRS
            // activates the glyph from its visible centre, one tile north-east of that anchor.
            val centre =
                glyph.tile.translate(YamaRules.GLYPH_CENTRE_OFFSET, YamaRules.GLYPH_CENTRE_OFFSET)
            val stepper = online(run).firstOrNull { it.coords == centre } ?: return@forEach
            val boss = run.boss
            val distance =
                if (boss == null) {
                    0
                } else {
                    YamaHazardRules.occupiedRectangleDistance(boss.coords, boss.size, centre)
                }
            val cost = YamaRules.glyphCost(distance, run.contract, glyph.type)
            if (glyph.type == YamaGlyph.FIRE) resolvedMechanicFixedHit(run, stepper, cost)
            else stepper.statSub("stat.prayer", constant = cost, percent = 0)
            val onlineParticipants = online(run)
            val recipientHashes =
                YamaRules.glyphRecipientHashes(
                    run.participants.keys,
                    onlineParticipants.map { it.accountHash },
                )
            onlineParticipants
                .filter { it.accountHash in recipientHashes }
                .forEach { protected ->
                    run.protections[protected.accountHash] =
                        GlyphProtection(
                            glyph.type,
                            clock.cycle + YamaRules.profile(run.contract).glyphProtectionTicks,
                        )
                    protected.spotanim(
                        spot(
                            if (glyph.type == YamaGlyph.FIRE) YamaAssets.GFX_FIRE_IMMUNITY
                            else YamaAssets.GFX_SHADOW_IMMUNITY
                        ),
                        slot = 2,
                    )
                    protected.mes(YamaRules.glyphAbsorbMessage(glyph.type))
                }
            glyph.loc?.let { locs.del(it, Int.MAX_VALUE) }
            // Captures send the inactive base and matching one-tick deactivation loc together,
            // followed by the inactive loc on the next tick.
            glyph.loc = spawnLoc(glyph.tile, YamaAssets.GLYPH_INACTIVE, Int.MAX_VALUE)
            val deactivate = YamaAssets.deactivatingGlyph(glyph.type)
            glyph.transientLoc = spawnLoc(glyph.tile, deactivate, Int.MAX_VALUE)
            glyph.active = false
            glyph.inactiveVisualCycle = clock.cycle + YamaRules.GLYPH_DEACTIVATE_TICKS
            glyph.respawnCycle = clock.cycle + YamaRules.profile(run.contract).glyphRespawnTicks
        }
    }

    private fun spawnGlyphs(run: Run) {
        clearGlyphs(run)
        val positions = listOf(25 to 36, 23 to 29, 30 to 24, 37 to 29, 35 to 36)
        val fireFirst = random.of(2) == 0
        run.nextStandardStyle = YamaRules.phaseOneStyleSeed(fireFirst)
        run.phaseThreeStyle = YamaRules.phaseThreeStyleSeed(fireFirst)
        run.nextGlyphAttack = if (fireFirst) YamaGlyph.FIRE else YamaGlyph.SHADOW
        positions.forEachIndexed { index, (x, z) ->
            val type = if ((index % 2 == 0) == fireFirst) YamaGlyph.FIRE else YamaGlyph.SHADOW
            val tile = local(run.session, x, z)
            val loc = spawnLoc(tile, YamaAssets.activeGlyph(type), Int.MAX_VALUE)
            run.glyphs += ActiveGlyph(tile, type, loc, true, Int.MAX_VALUE)
        }
    }

    private fun processFireStreaks(run: Run) {
        val iterator = run.fireStreaks.iterator()
        while (iterator.hasNext()) {
            val fire = iterator.next()
            if (clock.cycle >= fire.endCycle) {
                iterator.remove()
                continue
            }
            if (clock.cycle < fire.activeCycle) continue
            val activeTick = clock.cycle - fire.activeCycle
            val front =
                YamaHazardRules.fireFrontTiles(
                    fire.startLocalX,
                    fire.localZ,
                    fire.direction,
                    activeTick,
                )
            if (
                front.last < YamaHazardRules.ARENA_MIN_X ||
                    front.first > YamaHazardRules.ARENA_MAX_X
            ) {
                iterator.remove()
                continue
            }
            val base = local(run.session, 0, 0)
            val frontTiles =
                front.mapTo(linkedSetOf()) { localX ->
                    CoordGrid(base.x + localX, base.z + fire.localZ, base.level)
                }
            online(run).forEach { player ->
                val collisionResult =
                    YamaHazardRules.movingFrontCollision(
                        traversedTiles = exactPlayerTraversal(player),
                        movementSteps = player.pendingStepCount,
                        frontTiles = frontTiles,
                    )
                if (collisionResult != YamaHazardRules.MovingFrontCollision.HIT) {
                    return@forEach
                }
                if (clock.cycle <= (run.lastFireHit[player.accountHash] ?: Int.MIN_VALUE) + 1) {
                    return@forEach
                }
                run.lastFireHit[player.accountHash] = clock.cycle
                if (!absorbWithGlyph(run, player, YamaGlyph.FIRE)) {
                    queuedMechanicHit(
                        run,
                        player,
                        YamaRules.FIRE_STREAK_MAX,
                        YamaCombatTimeline.MOVING_HAZARD_HIT_DELAY,
                    )
                    CombatEffects.freeze(player, 2)
                }
            }
        }
    }

    private fun processWaves(run: Run) {
        val iterator = run.waves.iterator()
        while (iterator.hasNext()) {
            val wave = iterator.next()
            if (clock.cycle >= wave.endCycle) {
                iterator.remove()
                continue
            }
            if (clock.cycle < wave.startCycle) continue
            val activeTick = clock.cycle - wave.startCycle
            val base = local(run.session, 0, 0)
            val frontTiles = buildSet {
                for (localX in YamaHazardRules.ARENA_MIN_X..YamaHazardRules.ARENA_MAX_X) {
                    for (localZ in YamaHazardRules.ARENA_MIN_Z..YamaHazardRules.ARENA_MAX_Z) {
                        if (
                            YamaHazardRules.shadowWaveArrivalTick(wave.origin, localX, localZ) ==
                                activeTick
                        ) {
                            add(CoordGrid(base.x + localX, base.z + localZ, base.level))
                        }
                    }
                }
            }
            online(run)
                .filter { it.accountHash !in wave.resolvedPlayers }
                .forEach { player ->
                    when (
                        YamaHazardRules.movingFrontCollision(
                            traversedTiles = exactPlayerTraversal(player),
                            movementSteps = player.pendingStepCount,
                            frontTiles = frontTiles,
                        )
                    ) {
                        YamaHazardRules.MovingFrontCollision.NONE -> return@forEach
                        YamaHazardRules.MovingFrontCollision.SKIPPED -> {
                            wave.resolvedPlayers += player.accountHash
                            return@forEach
                        }
                        YamaHazardRules.MovingFrontCollision.HIT ->
                            wave.resolvedPlayers += player.accountHash
                    }
                    if (!absorbWithGlyph(run, player, YamaGlyph.SHADOW)) {
                        player.disableProtectionPrayers()
                        queuedMechanicHit(
                            run,
                            player,
                            YamaRules.SHADOW_WAVE_MAX,
                            YamaCombatTimeline.MOVING_HAZARD_HIT_DELAY,
                        )
                    }
                }
        }
    }

    private fun processFlares(run: Run) {
        run.flares.forEach { (npc, flare) ->
            if (
                !flare.progressShown && clock.cycle >= flare.attackableCycle && npc.isSlotAssigned
            ) {
                flare.progressShown = true
                npc.anim(anim(YamaAssets.ANIM_VOID_FLARE_IDLE))
                showFlareDeadline(npc)
            }
        }
        val expired = run.flares.filterValues { clock.cycle >= it.explodeCycle }.toList()
        expired.forEach { (npc, flare) ->
            run.flares.remove(npc)
            npcRuns.remove(npc)
            if (!npc.isSlotAssigned) return@forEach
            npc.anim(anim(YamaAssets.ANIM_VOID_FLARE_EXPLODE))
            npc.spotanim(spot(YamaAssets.GFX_FLARE_EXPLODE))
            val damage = YamaRules.flareExplosionDamage(npc.hitpoints, npc.baseHitpointsLvl)
            val affected = online(run)
            var unabsorbed = false
            affected.forEach { player ->
                val absorbed = absorbWithGlyph(run, player, flare.type)
                if (!absorbed) {
                    unabsorbed = true
                    player.spotanim(spot(YamaAssets.GFX_FLARE_PLAYER))
                    queuedMechanicFixedHit(
                        run,
                        player,
                        if (run.contract in setOf(YamaContract.SENSORY, YamaContract.DIVINE))
                            player.hitpoints
                        else damage,
                        YamaCombatTimeline.VOID_FLARE_HIT_DELAY,
                    )
                }
            }
            if (YamaRules.flareShouldHeal(if (unabsorbed) 1 else 0))
                run.boss?.heal(YamaRules.flareHealing(damage))
            npcs.del(npc, Int.MAX_VALUE)
        }
    }

    private fun showFlareDeadline(flare: Npc) {
        val type =
            ServerCacheManager.getHealthBar(
                RSCM.run { YamaAssets.VOID_FLARE_CHARGE_HEADBAR.asRSCM(RSCMType.HEADBAR) }
            ) ?: return
        flare.showHeadbar(
            Headbar.fromNoSource(
                self = type.id,
                public = type.id,
                startFill = 0,
                endFill = type.segments.coerceIn(1, 255),
                startTime = 0,
                endTime = YamaRules.VOID_FLARE_PROGRESS_CLIENT_CYCLES,
            )
        )
    }

    private fun processMeteors(run: Run) {
        val iterator = run.meteors.iterator()
        while (iterator.hasNext()) {
            val meteor = iterator.next()
            val selected = run.participants[meteor.accountHash]?.let(::resolve)
            if (clock.cycle <= meteor.runLockedUntilCycle) {
                selected?.tempMoveSpeed = MoveSpeed.Walk
            }
            if (clock.cycle < meteor.impactCycle) continue
            iterator.remove()
            selected?.tempMoveSpeed = null
            if (meteor.helper.isSlotAssigned) npcs.del(meteor.helper, Int.MAX_VALUE)
            online(run).forEach { player ->
                val startTile = meteor.participantStartTiles[player.accountHash] ?: return@forEach
                val moved = distance(startTile, player.coords)
                val centreDistance = distance(player.coords, meteor.targetTile)
                val absorbed = absorbWithGlyph(run, player, YamaGlyph.FIRE)
                val damage =
                    if (absorbed) 0
                    else YamaRules.meteorDamage(moved, centreDistance, player.hitpoints)
                player.spotanim(spot(YamaAssets.GFX_FALLING_ROCK_PLAYER))
                if (!absorbed) {
                    resolvedMechanicFixedHit(run, player, damage)
                    if (damage > 0) {
                        run.boss?.let { burns.apply(it, player, YamaRules.METEOR_BURN_VALUE) }
                    }
                }
            }
        }
    }

    private fun processShadowCrashes(run: Run) {
        // A 3262 marker locks the target tile three ticks before its 3252 impact. Resolve the
        // previous snapshot before locking the next one, matching the capture packet order.
        val impacts = run.shadowCrashImpacts.iterator()
        while (impacts.hasNext()) {
            val impact = impacts.next()
            if (clock.cycle < impact.impactCycle) continue
            impacts.remove()
            val player = run.participants[impact.accountHash]?.let(::resolve) ?: continue
            YamaHazardRules.crashShadowVisuals(impact.centre, impact.orientation).forEach { visual
                ->
                world.spotanimMap(
                    SpotanimType(YamaAssets.GFX_SHADOW_CRASH_IMPACT),
                    visual.tile,
                    delay = visual.clientDelay,
                )
            }
            val perpendicular =
                YamaHazardRules.isOneTilePerpendicularDodge(
                    impact.centre,
                    player.coords,
                    impact.orientation,
                )
            if (
                perpendicular &&
                    ArceuusSpellState.isMarked(player, clock.cycle) &&
                    player.actionDelay > clock.cycle
            ) {
                player.actionDelay--
            }
            if (
                player.coords in
                    YamaHazardRules.crashDamageTiles(impact.centre, impact.orientation) &&
                    !absorbWithGlyph(run, player, YamaGlyph.SHADOW)
            ) {
                resolvedMechanicHit(run, player, YamaRules.SHADOW_CRASH_MAX)
            }
        }

        val sequences = run.shadowCrashSequences.iterator()
        while (sequences.hasNext()) {
            val sequence = sequences.next()
            if (sequence.nextIndex >= sequence.lockCycles.size) {
                sequences.remove()
                continue
            }
            if (clock.cycle < sequence.lockCycles[sequence.nextIndex]) continue
            val player = run.participants[sequence.accountHash]?.let(::resolve)
            if (player == null) {
                sequences.remove()
                continue
            }
            val centre = player.coords
            val orientation = sequence.orientations[sequence.nextIndex]
            YamaHazardRules.crashImpactMarkers(centre, orientation).forEach { tile ->
                world.spotanimMap(
                    SpotanimType(YamaAssets.GFX_FALLING_ROCK_PLAYER),
                    tile,
                    delay = if (tile == centre) 0 else 3,
                )
            }
            run.shadowCrashImpacts +=
                PendingCrashImpact(
                    accountHash = sequence.accountHash,
                    impactCycle = YamaCombatTimeline.shadowCrashImpactCycle(clock.cycle),
                    centre = centre,
                    orientation = orientation,
                )
            sequence.nextIndex++
            if (sequence.nextIndex >= sequence.lockCycles.size) sequences.remove()
        }
    }

    private fun processForfeit(run: Run) {
        if (
            run.contract != YamaContract.FORFEIT ||
                !YamaRules.forfeitRunDrain(clock.cycle, run.startCycle)
        )
            return
        online(run).forEach { player ->
            if (drainRunEnergy(player, 100) == 0)
                resolvedMechanicFixedHit(run, player, player.hitpoints)
        }
    }

    private fun processDivineSeverance(run: Run) {
        if (run.contract != YamaContract.DIVINE) return
        online(run).forEach { player ->
            val active =
                player.vars["varbit.prayer_protectfrommelee"] == 1 ||
                    player.vars["varbit.prayer_protectfrommissiles"] == 1 ||
                    player.vars["varbit.prayer_protectfrommagic"] == 1
            if (!active) {
                run.divinePrayerStarted.remove(player.accountHash)
                return@forEach
            }
            val started = run.divinePrayerStarted.getOrPut(player.accountHash) { clock.cycle }
            if (clock.cycle > started) {
                player.disableProtectionPrayers()
                run.divinePrayerStarted.remove(player.accountHash)
            }
        }
    }

    private fun drainRunEnergy(player: Player, amount: Int): Int {
        val next = (player.vars["varp.runenergy"] - amount.coerceAtLeast(0)).coerceAtLeast(0)
        VarPlayerIntMapSetter.set(player, "varp.runenergy", next)
        return next
    }

    private fun applyForfeitDamageDrain(run: Run, player: Player, damage: Int): Int {
        if (run.contract != YamaContract.FORFEIT || damage <= 0) return damage
        return if (drainRunEnergy(player, damage * 100) == 0) player.hitpoints else damage
    }

    fun passFirewall(player: Player, quick: Boolean): Boolean {
        val run = participantRuns[player.accountHash] ?: return false
        if (run.state !in setOf(YamaState.LOBBY, YamaState.DEFEATED)) {
            player.mes("You cannot leave while the pact is active.")
            return false
        }
        removeParticipant(player, if (quick) "quick pass" else "pass firewall", teleport = true)
        return true
    }

    fun removeParticipant(
        player: Player,
        reason: String,
        teleport: Boolean,
        logout: Boolean = false,
    ) {
        clearPendingLobbyState(setOf(player.accountHash))
        val run = participantRuns[player.accountHash] ?: return
        closeEncounterBars(run, player)
        detachParticipantState(run, player)
        if (logout) instances.handleLogout(player, clock.cycle)
        else {
            val exit = instances.leave(player, run.session, clock.cycle)
            if (teleport && player.isSlotAssigned)
                PathingEntityCommon.telejump(player, collision, exit)
        }
        if (run.participants.isEmpty()) {
            abort(run, reason, teleport = false)
        } else {
            if (run.ownerHash == player.accountHash) {
                run.ownerHash = run.participants.keys.first()
            }
            owner(run)?.mes("${player.displayName} has left the Yama pact.")
            switchTarget(run)
            keepAggressive(run)
        }
    }

    private fun detachParticipantState(run: Run, player: Player) {
        val accountHash = player.accountHash
        prepare(player)
        burns.consumeRemainingDamage(player)
        clearContractPlayerState(player)
        participantRuns.remove(accountHash)
        run.participants.remove(accountHash)
        run.protections.remove(accountHash)
        run.divinePrayerStarted.remove(accountHash)
        run.lastFireHit.remove(accountHash)
        run.previousPlayerTiles.remove(accountHash)
        run.lastJudgeSurgeHit.remove(accountHash)
        run.judgeBlockedStyles.remove(accountHash)
        run.observedHitpoints.remove(accountHash)
        run.observedPrayer.remove(accountHash)
        run.shadowCrashSequences.removeAll { it.accountHash == accountHash }
        run.shadowCrashImpacts.removeAll { it.accountHash == accountHash }
        run.glyphAttacks.removeAll { it.accountHash == accountHash }
        run.pendingHits.removeAll { it.accountHash == accountHash }
        val meteors = run.meteors.iterator()
        while (meteors.hasNext()) {
            val meteor = meteors.next()
            if (meteor.accountHash != accountHash) continue
            if (meteor.helper.isSlotAssigned) npcs.del(meteor.helper, Int.MAX_VALUE)
            meteors.remove()
        }
        player.lootDropDuration = null
        resetEncounterClientState(player)
    }

    private fun abort(run: Run, reason: String, teleport: Boolean) {
        if (run.state == YamaState.INVALIDATED) return
        clearPendingLobbyState(run.participants.keys)
        val members = online(run)
        members.forEach { member ->
            closeEncounterBars(run, member)
            clearContractPlayerState(member)
            prepare(member)
        }
        run.state = YamaState.INVALIDATED
        run.boss?.let(bossEncounters::remove)
        runs.remove(run.session.id)
        clearTransientHazards(run)
        clearGlyphs(run)
        run.firewalls.forEach { locs.del(it, Int.MAX_VALUE) }
        run.firewalls.clear()
        npcRuns
            .filterValues { it === run }
            .keys
            .toList()
            .forEach { npc ->
                npcRuns.remove(npc)
                if (npc.isSlotAssigned) npcs.del(npc, Int.MAX_VALUE)
            }
        members.forEach { member ->
            participantRuns.remove(member.accountHash)
            val exit = instances.leave(member, run.session, clock.cycle)
            if (teleport) PathingEntityCommon.telejump(member, collision, exit)
            resetEncounterClientState(member)
        }
        run.participants.keys.forEach(participantRuns::remove)
        instances.terminate(run.session)
        log.info { "[Yama] generation=${run.generation} abort=$reason" }
    }

    fun attackDenial(player: Player, npc: Npc): String? {
        val run = npcRuns[npc] ?: return "That creature is not part of your pact."
        if (player.accountHash !in run.participants) return "That creature belongs to another pact."
        if (npc === run.boss)
            return if (
                run.state in setOf(YamaState.PHASE_ONE, YamaState.PHASE_TWO, YamaState.PHASE_THREE)
            )
                null
            else "Yama is immune during the transition."
        if (npc in run.flares) return null
        if (npc in run.judges) {
            val participant =
                run.participants[player.accountHash] ?: return "Cross the stones first."
            return if (YamaRules.judgeCrossingComplete(participant.stoneStep)) null
            else "Cross the stones before attacking the Judge."
        }
        return "You cannot attack that."
    }

    fun isEncounterNpc(npc: Npc): Boolean = npcRuns.containsKey(npc)

    fun isOwnedBy(player: Player, npc: Npc): Boolean =
        npcRuns[npc]?.participants?.containsKey(player.accountHash) == true

    fun isActive(player: Player): Boolean = participantRuns.containsKey(player.accountHash)

    fun debugPhase(player: Player, phase: Int) {
        val run =
            participantRuns[player.accountHash] ?: return player.mes("Yama: no active encounter.")
        val boss = run.boss ?: return player.mes("Yama: the boss is not currently active.")
        val maxHp = YamaRules.profile(run.contract).hitpoints
        when (phase) {
            1 -> {
                boss.hitpoints = maxHp
                run.phase = 1
                run.state = YamaState.PHASE_ONE
                activateCombatPhase(run)
            }
            2 -> {
                boss.hitpoints = YamaRules.firstThreshold(maxHp, run.contract) - 1
                beginJudge(run, true)
            }
            3 -> {
                boss.hitpoints = YamaRules.secondThreshold(maxHp, run.contract) - 1
                beginJudge(run, false)
            }
        }
        player.mes("Yama debug transition requested for phase $phase.")
    }

    fun debugStatus(player: Player): String {
        val run = participantRuns[player.accountHash] ?: return "Yama: no active encounter."
        return "Yama generation=${run.generation} state=${run.state} phase=${run.phase} contract=${run.contract} hp=${run.boss?.hitpoints ?: run.savedBossHitpoints} judge=${run.judgeHitpoints} flares=${run.flares.size} waves=${run.waves.size} fire=${run.fireStreaks.size} contribution=${run.contribution[player.accountHash] ?: 0}"
    }

    private fun switchTarget(run: Run) {
        val targets = online(run)
        if (targets.isEmpty()) return
        run.targetIndex = (run.targetIndex + 1) % targets.size
        run.boss?.apPlayer2(targets[run.targetIndex], interactions)
    }

    private fun keepAggressive(run: Run) {
        val boss = run.boss ?: return
        if (!boss.isSlotAssigned || boss.hitpoints <= 0) return
        val targets = online(run).filter { it.hitpoints > 0 }
        if (targets.isEmpty()) return
        val current = targets[run.targetIndex.coerceIn(0, targets.lastIndex)]
        boss.apPlayer2(current, interactions)
    }

    private fun spawnBoss(run: Run, hitpoints: Int): Npc {
        val owner = owner(run) ?: error("Yama run ${run.generation} has no online owner")
        val boss = spawnNpc(owner, run.session, YamaAssets.BOSS, local(run.session, 29, 28))
        // InstanceSession owns this shared actor. A single-player spawn owner blocks the duo's
        // second participant in the native BossCombat owner gate.
        boss.clearSpawnOwner()
        val profile = YamaRules.profile(run.contract)
        boss.baseHitpointsLvl = profile.hitpoints
        boss.hitpoints = hitpoints.coerceIn(1, profile.hitpoints)
        boss.baseAttackLvl = if (run.contract.isContractFight) 400 else 320
        boss.attackLvl = boss.baseAttackLvl
        boss.baseStrengthLvl = if (run.contract.isContractFight) 402 else 350
        boss.strengthLvl = boss.baseStrengthLvl
        boss.baseDefenceLvl = if (run.contract.isContractFight) 247 else 225
        boss.defenceLvl = boss.baseDefenceLvl
        boss.baseMagicLvl = if (run.contract.isContractFight) 275 else 250
        boss.magicLvl = boss.baseMagicLvl
        boss.baseMagicDefenceLvl = boss.baseMagicLvl
        boss.magicDefenceLvl = boss.baseMagicDefenceLvl
        boss.baseRangedLvl = if (run.contract.isContractFight) 241 else 210
        boss.rangedLvl = boss.baseRangedLvl
        boss.apRangeOverride = YamaRules.ACTIVE_FIGHT_RANGE
        boss.maxOpRangeOverride = YamaRules.ACTIVE_FIGHT_RANGE
        boss.apRequiresLineOfSight = true
        run.boss = boss
        npcRuns[boss] = run
        return boss
    }

    private fun spawnNpc(owner: Player, session: InstanceSession, id: Int, tile: CoordGrid): Npc {
        val npc = Npc(requireNpc(id), tile)
        npc.mode = null
        npc.assignSpawnOwner(owner, clock.cycle)
        npcs.add(npc, YAMA_NPC_LIFETIME)
        instances.attachNpc(session.id, npc)
        return npc
    }

    private fun spawnLoc(tile: CoordGrid, id: Int, duration: Int): LocInfo? =
        ServerCacheManager.getObject(id)?.let {
            locs.add(tile, it, duration, LocAngle.North, LocShape.CentrepieceStraight)
        }

    private fun openYamaBar(run: Run, boss: Npc) {
        online(run).forEach { player ->
            bossHpBar.onOpen(player, boss)
            bossHpBar.onUpdate(player, boss, boss.hitpoints, boss.baseHitpointsLvl)
        }
    }

    private fun closeEncounterBars(run: Run, player: Player) {
        run.boss?.let { bossHpBar.onClose(player, it, instant = true) }
        run.judges.forEach { bossHpBar.onClose(player, it, instant = true) }
    }

    private fun clearGlyphs(run: Run) {
        run.glyphs.forEach {
            it.loc?.let { loc -> locs.del(loc, Int.MAX_VALUE) }
            it.transientLoc?.let { loc -> locs.del(loc, Int.MAX_VALUE) }
        }
        run.glyphs.clear()
        run.protections.clear()
    }

    private fun clearTransientHazards(run: Run) {
        clearJudgeSurges(run)
        run.boss?.let(burns::clearSource)
        run.flares.keys.toList().forEach {
            npcRuns.remove(it)
            if (it.isSlotAssigned) npcs.del(it, Int.MAX_VALUE)
        }
        run.flares.clear()
        run.meteors.forEach {
            run.participants[it.accountHash]?.let(::resolve)?.tempMoveSpeed = null
            if (it.helper.isSlotAssigned) npcs.del(it.helper, Int.MAX_VALUE)
        }
        run.meteors.clear()
        run.fireStreaks.clear()
        run.lastFireHit.clear()
        run.waves.clear()
        run.shadowCrashSequences.clear()
        run.shadowCrashImpacts.clear()
        run.glyphAttacks.clear()
        run.pendingHits.clear()
        run.previousPlayerTiles.clear()
    }

    private fun hasGlyphProtection(run: Run, player: Player, type: YamaGlyph): Boolean =
        run.protections[player.accountHash]?.let {
            YamaRules.glyphProtects(it.type, type) && clock.cycle < it.untilCycle
        } == true

    private fun absorbWithGlyph(run: Run, player: Player, type: YamaGlyph): Boolean {
        if (!hasGlyphProtection(run, player, type)) return false
        player.mes(YamaRules.glyphBlockedMessage(type))
        return true
    }

    private fun resolvedMechanicHit(run: Run, player: Player, maximum: Int) =
        resolvedMechanicFixedHit(run, player, random.of(maximum.coerceAtLeast(0) + 1))

    private fun resolvedMechanicFixedHit(run: Run, player: Player, damage: Int) {
        player.takeInstantHit(
            type = HitType.Typeless,
            damage = applyForfeitDamageDrain(run, player, damage.coerceAtLeast(0)),
            processor = instantHitProcessor,
            modifier = NpcBypassProtectionPrayerPlayerHitModifier,
        )
    }

    private fun queuedMechanicHit(run: Run, player: Player, maximum: Int, delay: Int) =
        queuedMechanicFixedHit(run, player, random.of(maximum.coerceAtLeast(0) + 1), delay)

    private fun queuedMechanicFixedHit(run: Run, player: Player, damage: Int, delay: Int) {
        val source = run.boss ?: run.judges.firstOrNull() ?: return
        player.queueHit(
            source = source,
            delay = delay,
            type = HitType.Typeless,
            damage = applyForfeitDamageDrain(run, player, damage.coerceAtLeast(0)),
            modifier = NpcBypassProtectionPrayerPlayerHitModifier,
        )
    }

    private fun currentAttackStyle(player: Player): YamaAttackStyle {
        val style = attackTypes.get(player)
        return when {
            style?.isMagic == true -> YamaAttackStyle.MAGIC
            style?.isRanged == true -> YamaAttackStyle.RANGED
            else -> YamaAttackStyle.MELEE
        }
    }

    private fun currentAttackStyle(player: Player, hit: HitBuilder): YamaAttackStyle =
        when (hit.type) {
            HitType.Magic -> YamaAttackStyle.MAGIC
            HitType.Ranged -> YamaAttackStyle.RANGED
            HitType.Melee -> YamaAttackStyle.MELEE
            else -> currentAttackStyle(player)
        }

    private fun isDemonbane(hit: HitBuilder): Boolean {
        val weapon = hit.righthandType()
        if (
            weapon?.isAnyType(
                "obj.silverlight",
                "obj.agrith_silverlight_dyed",
                "obj.darklight",
                "obj.arclight",
                "obj.emberlight",
                "obj.scorching_bow",
            ) == true
        )
            return true
        val spell = hit.secondaryType() ?: return false
        return MagicSpellChecks.isDemonbaneSpell(spell)
    }

    private fun isScorchingBow(hit: HitBuilder): Boolean =
        listOfNotNull(hit.righthandType(), hit.secondaryType()).any {
            it.isType("obj.scorching_bow")
        }

    private fun isProtected(player: Player, style: YamaAttackStyle): Boolean =
        when (style) {
            YamaAttackStyle.MELEE -> player.vars["varbit.prayer_protectfrommelee"] == 1
            YamaAttackStyle.RANGED -> player.vars["varbit.prayer_protectfrommissiles"] == 1
            YamaAttackStyle.MAGIC -> player.vars["varbit.prayer_protectfrommagic"] == 1
        }

    private fun hitType(style: YamaAttackStyle): HitType =
        when (style) {
            YamaAttackStyle.MELEE -> HitType.Melee
            YamaAttackStyle.RANGED -> HitType.Ranged
            YamaAttackStyle.MAGIC -> HitType.Magic
        }

    private fun damageJudge(run: Run, damage: Int) {
        if (damage <= 0 || run.judgeDeathQueued) return
        synchronizeJudgeHitpoints(
            run,
            YamaJudgeRules.remainingHitpoints(run.judgeHitpoints, damage),
        )
        if (run.judgeHitpoints == 0) beginJudgeDeath(run, queueNpcDeath = true)
    }

    private fun synchronizeJudgeHitpoints(run: Run, remaining: Int) {
        run.judgeHitpoints = remaining.coerceIn(0, YamaRules.JUDGE_HITPOINTS)
        run.judges.forEach { it.hitpoints = run.judgeHitpoints.coerceAtMost(it.baseHitpointsLvl) }
        run.participants.values.forEach { participant ->
            val player = resolve(participant) ?: return@forEach
            val judge = run.judges.getOrNull(participant.slot) ?: return@forEach
            bossHpBar.onUpdate(player, judge, run.judgeHitpoints, YamaRules.JUDGE_HITPOINTS)
        }
    }

    private fun beginJudgeDeath(run: Run, queueNpcDeath: Boolean) {
        if (run.judgeHitpoints != 0 || run.judgeDeathQueued) return
        run.judgeDeathQueued = true
        run.stateCycle = YamaCombatTimeline.judgeReturnCycle(clock.cycle)
        run.judgeReturnAnimCycle = YamaCombatTimeline.judgeReturnPlayerAnimCycle(clock.cycle)
        run.judgeReturnTeleportCycle = YamaCombatTimeline.judgeReturnTeleportCycle(clock.cycle)
        clearJudgeSurges(run)
        online(run).forEach(Player::combatClearQueue)
        run.judges.forEach { it.anim(anim(YamaAssets.ANIM_JUDGE_DEATH)) }
        online(run).forEach { it.spotanim(spot(YamaAssets.GFX_PORTAL), delay = 90, slot = 2) }
        if (queueNpcDeath) run.judges.firstOrNull { it.isSlotAssigned }?.queueDeath()
    }

    private fun resetJudgeCamera(player: Player) {
        Camera.camReset(player)
        Cinematic.setCameraMode(player, CameraMode.Normal)
    }

    private fun judgeStartTile(run: Run, slot: Int): CoordGrid =
        local(run.session, if (slot == 0) 7 else YamaJudgeRules.mirrorLocalX(7), 29)

    private fun judgeTile(run: Run, slot: Int): CoordGrid =
        local(run.session, if (slot == 0) 6 else YamaJudgeRules.mirrorSouthWestX(6), 50)

    private fun judgeReturnTile(run: Run, participant: Participant): CoordGrid {
        val firstReturn = run.state == YamaState.JUDGE_ONE
        val westX = if (firstReturn) 30 else 32
        val eastX = if (firstReturn) 32 else 30
        return local(run.session, if (participant.slot == 0) westX else eastX, 37)
    }

    private fun exactPlayerTraversal(player: Player): List<CoordGrid> =
        buildList {
                val previous = player.previousCoords
                if (previous != CoordGrid.ZERO && previous != CoordGrid.NULL) add(previous)
                val intermediate = player.lastProcessedCoord
                if (
                    player.pendingStepCount > 1 &&
                        intermediate != CoordGrid.NULL &&
                        intermediate != previous &&
                        intermediate != player.coords
                ) {
                    add(intermediate)
                }
                add(player.coords)
            }
            .distinct()

    private fun insideArena(run: Run, tile: CoordGrid): Boolean {
        val sw = local(run.session, 20, 20)
        val ne = local(run.session, 40, 42)
        return tile.level == sw.level && tile.x in sw.x..ne.x && tile.z in sw.z..ne.z
    }

    private fun setVarbit(player: Player, id: Int, value: Int) =
        VarPlayerIntMapSetter.set(player, requireNotNull(ServerCacheManager.getVarbit(id)), value)

    private fun rewardTile(run: Run, player: Player): CoordGrid {
        val slot = run.participants[player.accountHash]?.slot ?: 0
        val (x, z) = YamaLifecycleRules.rewardLocalCoordinates(slot)
        return local(run.session, x, z)
    }

    private fun clearPendingLobbyState(accountHashes: Collection<Long>) =
        YamaLifecycleRules.clearPendingLobbyState(
            invitations = invitations,
            pendingContracts = pendingContracts,
            accountHashes = accountHashes,
        )

    private fun local(session: InstanceSession, x: Int, z: Int): CoordGrid =
        requireNotNull(instances.localCoord(session, RegionLocal(0, 23, 157, x, z)))

    private fun owner(run: Run): Player? =
        run.participants[run.ownerHash]?.let(::resolve) ?: online(run).firstOrNull()

    private fun online(run: Run): List<Player> =
        run.participants.values.mapNotNull(::resolve).filter {
            it.isSlotAssigned && !it.pendingLogout && !it.loggingOut
        }

    private fun resolve(participant: Participant): Player? =
        participant.uid.resolve(players)
            ?: players.firstOrNull {
                it.accountHash == participant.accountHash && it.isSlotAssigned
            }

    private fun applyContractPlayerState(player: Player, contract: YamaContract) {
        clearContractPlayerState(player)
        if (contract.enhancedAcquisition) {
            val restorationPercent =
                if (contract == YamaContract.FAMILIAR) 0
                else YamaRules.profile(contract).restorationPercent
            StatRestorationModifierState.setPercentage(player, "stat.hitpoints", restorationPercent)
            StatRestorationModifierState.setPercentage(player, "stat.prayer", restorationPercent)
        }
        if (contract == YamaContract.FAMILIAR) {
            ConsumableRestrictionState.setFoodBlocked(player, true)
            ConsumableRestrictionState.setHealingPotionsBlocked(player, true)
            SpecialAttackEnergyRegeneration.setIntervalDivisor(player, 2)
        }
        if (contract == YamaContract.SENSORY) {
            PlayerAttackRangeModifierState.set(player, numerator = 1, denominator = 2)
        }
        if (contract == YamaContract.GLYPHIC) {
            PrayerBonusModifierState.set(player, multiplier = -1)
            PrayerDrainResistance.refresh(player, wornBonuses)
        }
    }

    private fun clearContractPlayerState(player: Player) {
        StatRestorationModifierState.clear(player)
        ConsumableRestrictionState.clear(player)
        SpecialAttackEnergyRegeneration.clearIntervalDivisor(player)
        PlayerAttackRangeModifierState.clear(player)
        PrayerBonusModifierState.clear(player)
        PrayerDrainResistance.refresh(player, wornBonuses)
    }

    private fun prepare(player: Player) {
        player.combatClearQueue()
        player.clearInteraction()
        player.resetAnim()
        player.tempMoveSpeed = null
    }

    private fun resetEncounterClientState(player: Player) {
        setVarbit(player, YamaAssets.VARBIT_THRONE_STATE, YamaAssets.THRONE_STATE_OCCUPIED)
        setVarbit(player, YamaAssets.VARBIT_TRANSITION_PHASE, 0)
        setVarbit(player, YamaAssets.VARBIT_TRANSITION_DAMAGE, 0)
        resetJudgeCamera(player)
    }

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) { "Missing Yama NPC $id" }

    private fun requireItem(id: Int) =
        requireNotNull(ServerCacheManager.getItem(id)) { "Missing Yama item $id" }

    private fun anim(id: Int) =
        requireNotNull(RSCM.getReverseMapping(RSCMType.SEQ, id)) { "Missing Yama sequence $id" }

    private fun spot(id: Int) =
        requireNotNull(RSCM.getReverseMapping(RSCMType.SPOTANIM, id)) {
            "Missing Yama spotanim $id"
        }

    private fun distance(a: CoordGrid, b: CoordGrid) = max(abs(a.x - b.x), abs(a.z - b.z))

    internal data class Participant(
        val accountHash: Long,
        var uid: PlayerUid,
        val slot: Int,
        var stoneStep: Int = 0,
        var nextJudgeSurgeCycle: Int = Int.MAX_VALUE,
        var nextJudgeAttackCycle: Int = Int.MAX_VALUE,
        var judgeAttackCount: Int = 0,
        var lastJudgeDamageCycle: Int = Int.MIN_VALUE,
        var judgeCameraResetCycle: Int = Int.MAX_VALUE,
    )

    internal data class GlyphProtection(val type: YamaGlyph, val untilCycle: Int)

    internal data class ActiveGlyph(
        val tile: CoordGrid,
        val type: YamaGlyph,
        var loc: LocInfo?,
        var active: Boolean,
        var respawnCycle: Int,
        var inactiveVisualCycle: Int = Int.MAX_VALUE,
        var transientLoc: LocInfo? = null,
    )

    internal data class ActiveFire(
        val startLocalX: Int,
        val localZ: Int,
        val direction: YamaHazardRules.HorizontalDirection,
        val activeCycle: Int,
        val endCycle: Int,
    )

    internal data class ActiveWave(
        val origin: YamaHazardRules.WaveOrigin,
        val startCycle: Int,
        val endCycle: Int,
        val resolvedPlayers: MutableSet<Long> = mutableSetOf(),
    )

    internal data class ActiveJudgeSurge(val npc: Npc, val slot: Int, val spawnCycle: Int)

    internal data class ActiveFlare(
        val type: YamaGlyph,
        val attackableCycle: Int,
        val explodeCycle: Int,
        val damagedBy: MutableSet<Long>,
        var progressShown: Boolean = false,
    )

    internal data class PendingMeteor(
        val accountHash: Long,
        val participantStartTiles: Map<Long, CoordGrid>,
        val targetTile: CoordGrid,
        val impactCycle: Int,
        val runLockedUntilCycle: Int,
        val helper: Npc,
    )

    internal data class ActiveCrashSequence(
        val accountHash: Long,
        val lockCycles: List<Int>,
        val orientations: List<YamaHazardRules.CrashOrientation>,
        var nextIndex: Int = 0,
    )

    internal data class PendingCrashImpact(
        val accountHash: Long,
        val impactCycle: Int,
        val centre: CoordGrid,
        val orientation: YamaHazardRules.CrashOrientation,
    )

    internal data class PendingGlyphAttack(
        val accountHash: Long,
        val sourceTile: CoordGrid,
        val targetTile: CoordGrid,
        val type: YamaGlyph,
        val impactCycle: Int,
        val maximum: Int,
        val impactGraphic: Int,
    )

    internal data class PendingHit(
        val impactCycle: Int,
        val accountHash: Long,
        val style: YamaAttackStyle,
        val maximum: Int,
        val graphic: Int? = null,
        val prayerDrain: Int = 0,
        val rootTicks: Int = 0,
        val glyph: YamaGlyph? = null,
        val targetTile: CoordGrid? = null,
        val damagesJudgeIfUnder: Boolean = false,
        val judgeSlot: Int? = null,
    )

    internal data class Run(
        var ownerHash: Long,
        val session: InstanceSession,
        val generation: Long,
        val throne: Npc,
        var state: YamaState,
        var stateCycle: Int,
        var startCycle: Int,
        var lastTick: Int = Int.MIN_VALUE,
        var contract: YamaContract = YamaContract.NONE,
        var contractSupplierHash: Long? = null,
        var phase: Int = 0,
        var boss: Npc? = null,
        var savedBossHitpoints: Int = 0,
        var nextContinuousFlareCycle: Int = 0,
        var targetIndex: Int = 0,
        var nextStandardStyle: YamaAttackStyle = YamaAttackStyle.MAGIC,
        var nextGlyphAttack: YamaGlyph = YamaGlyph.FIRE,
        var nextFlareElement: YamaGlyph = YamaGlyph.SHADOW,
        var nextFlarePositionIndex: Int = 0,
        var phaseThreeStyle: YamaAttackStyle = YamaAttackStyle.MAGIC,
        var phaseThreeWaveSets: Int = 0,
        var phaseThreeFourWavePending: Boolean = false,
        var lastStandardStyle: YamaAttackStyle? = null,
        var recentPlayerMagic: Boolean = false,
        var judgeHitpoints: Int = YamaRules.JUDGE_HITPOINTS,
        var judgeStarted: Boolean = false,
        var judgeHudCloseCycle: Int = Int.MAX_VALUE,
        var judgeHudClosed: Boolean = false,
        var judgeEntryAnimCycle: Int = Int.MAX_VALUE,
        var judgeEntryAnimPlayed: Boolean = false,
        var judgeDeathQueued: Boolean = false,
        var judgeReturnPrepared: Boolean = false,
        var judgeReturnAnimCycle: Int = Int.MAX_VALUE,
        var judgeReturnAnimPlayed: Boolean = false,
        var judgeReturnTeleportCycle: Int = Int.MAX_VALUE,
        val participants: MutableMap<Long, Participant> = linkedMapOf(),
        val contribution: MutableMap<Long, Int> = mutableMapOf(),
        val protections: MutableMap<Long, GlyphProtection> = mutableMapOf(),
        val divinePrayerStarted: MutableMap<Long, Int> = mutableMapOf(),
        val glyphs: MutableList<ActiveGlyph> = mutableListOf(),
        val firewalls: MutableList<LocInfo> = mutableListOf(),
        val fireStreaks: MutableList<ActiveFire> = mutableListOf(),
        val lastFireHit: MutableMap<Long, Int> = mutableMapOf(),
        val waves: MutableList<ActiveWave> = mutableListOf(),
        val flares: MutableMap<Npc, ActiveFlare> = linkedMapOf(),
        val meteors: MutableList<PendingMeteor> = mutableListOf(),
        val shadowCrashSequences: MutableList<ActiveCrashSequence> = mutableListOf(),
        val shadowCrashImpacts: MutableList<PendingCrashImpact> = mutableListOf(),
        val glyphAttacks: MutableList<PendingGlyphAttack> = mutableListOf(),
        val pendingHits: MutableList<PendingHit> = mutableListOf(),
        val previousPlayerTiles: MutableMap<Long, CoordGrid> = mutableMapOf(),
        val judges: MutableList<Npc> = mutableListOf(),
        val judgeSurges: MutableList<ActiveJudgeSurge> = mutableListOf(),
        val lastJudgeSurgeHit: MutableMap<Long, Int> = mutableMapOf(),
        val judgeBlockedStyles: MutableMap<Long, MutableSet<YamaAttackStyle>> = mutableMapOf(),
        val observedHitpoints: MutableMap<Long, Int> = mutableMapOf(),
        val observedPrayer: MutableMap<Long, Int> = mutableMapOf(),
    )
}
