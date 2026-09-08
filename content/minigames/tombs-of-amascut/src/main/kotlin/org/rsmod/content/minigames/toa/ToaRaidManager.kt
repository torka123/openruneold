package org.rsmod.content.minigames.toa

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.random.Random
import net.rsprot.protocol.game.outgoing.camera.CamReset
import net.rsprot.protocol.game.outgoing.camera.CamShake
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSettings
import org.rsmod.api.instances.RaidLeaveResult
import org.rsmod.api.instances.RaidRewardClaimResult
import org.rsmod.api.mechanics.toxins.NpcPoisonEffectService
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.mechanics.toxins.impl.PlayerVenom
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.clearInteractionRoute
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.clearInteractionRoute
import org.rsmod.api.player.combatClearQueue
import org.rsmod.api.player.disablePrayers
import org.rsmod.api.player.hit.modifier.NpcBypassProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.NpcPartialProtectionPrayerPlayerHitModifier
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.hit.queueImpactHit
import org.rsmod.api.player.midiSong
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.prayer.ProtectionPrayerLock
import org.rsmod.api.player.prayer.disableProtectionPrayers
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statRestoreAll
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.walkTo
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.api.specials.energy.SpecialAttackEnergyModifier
import org.rsmod.api.specials.restriction.ConsumableRestrictionState
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerTint
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitImpact
import org.rsmod.game.hit.HitImpactHandler
import org.rsmod.game.hit.HitType
import org.rsmod.game.hit.Hitmark
import org.rsmod.game.interact.InteractionPlayer
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.obj.Obj
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap

private data class AkkhaMemorySymbol(val locId: Int, val localX: Int, val localZ: Int)

private val AKKHA_MEMORY_SYMBOLS =
    mapOf(
        AkkhaQuadrant.BURN to AkkhaMemorySymbol(45868, 31, 33),
        AkkhaQuadrant.DARKNESS to AkkhaMemorySymbol(45871, 34, 33),
        AkkhaQuadrant.LIGHTNING to AkkhaMemorySymbol(45869, 34, 30),
        AkkhaQuadrant.FREEZE to AkkhaMemorySymbol(45870, 31, 30),
    )

private const val AKKHA_MEMORY_SYMBOL_SOUND = 5667
private const val AKKHA_MEMORY_FIRST_AREA_SOUND = 5688
private const val AKKHA_MEMORY_BURN_HEIGHT = 124
private const val AKKHA_TRAIL_CAST_RECOVERY = 6
private const val AKKHA_DARKNESS_DAMAGE = 5
private const val AKKHA_DARKNESS_HITS = 5
private const val AKKHA_DARKNESS_CADENCE = 1
private const val AKKHA_FORCED_STYLE_SWITCH_TICKS = 100
private const val AKKHA_STYLE_CHANGE_SOUND = 5585

@Singleton
internal class ToaRaidManager
@Inject
constructor(
    private val clock: MapClock,
    private val players: PlayerList,
    private val wornBonuses: WornBonuses,
    private val instances: InstanceManager,
    private val locs: LocRepository,
    private val npcs: NpcRepository,
    private val interactions: AiPlayerInteractions,
    private val world: WorldRepository,
    private val routeFactory: RouteFactory,
    private val collision: CollisionFlagMap,
    private val rewards: ToaRewardService,
    private val supplies: ToaSupplies,
    private val crondisZebak: ToaCrondisZebakRuntime,
    private val scabaras: ToaScabarasIntegration,
    private val het: ToaHetOpenRuneBridge,
    private val hetPickaxeStorage: ToaHetPickaxeStorage,
    private val ui: ToaUi,
    private val partyBoard: ToaPartyBoard,
    private val specialEnergy: SpecialAttackEnergy,
    private val bossHpBar: BossHpBarScript,
    private val npcToxins: NpcPoisonEffectService,
    private val protectedAccess: ProtectedAccessLauncher,
) {
    private val log = InlineLogger()
    private val parties = linkedMapOf<PlayerUid, ToaParty>()
    private val membership = mutableMapOf<PlayerUid, ToaParty>()
    private val viewedParties = mutableMapOf<PlayerUid, java.util.UUID>()
    private val raids = mutableMapOf<InstanceId, ToaRaidSession>()
    private val participantRaids = mutableMapOf<Long, ToaRaidSession>()
    private val devSeedOverrides = ToaDevSeedOverrides()
    private val pendingResets = mutableMapOf<InstanceId, Int>()
    private val wardenFloorOriginalLocs = WardenFloorLocJournal<InstanceId, CoordGrid, LocInfo?>()
    private val akkhaFinalSwapReadyAt = mutableMapOf<InstanceId, Int>()
    private val akkhaLastStyleChangeAt = mutableMapOf<InstanceId, Int>()
    private val stepValidator = StepValidator(collision)
    private val apmeken = ToaApmekenLiveRuntime()
    private val apmekenPlayerSlots = mutableMapOf<InstanceId, LinkedHashMap<PlayerUid, Int>>()
    private val apmekenNpcs = mutableMapOf<InstanceId, MutableMap<Long, Npc>>()
    private val apmekenNpcKeys = java.util.IdentityHashMap<Npc, Long>()

    fun validateAssets() {
        val requiredLocs =
            ToaLocs.pathEntrances.keys +
                ToaLocs.bossCrystals.keys +
                ToaLocs.disabledPathEntrances +
                ToaLocs.disabledBossCrystals +
                ToaLocs.rewardSarcophagi +
                ToaLocs.leaveRaid +
                ToaLocs.returnToLobby +
                BabaAssets.locIds +
                CrondisAssets.locIds +
                ZebakAssets.locIds +
                ScabarasRevision240RuntimeLayout.verifiedStaticMechanicLocIds +
                WardenPhaseOneMechanics.PYRAMID_LOCS +
                WardenChargeCircle.PLATFORM_LOCS +
                WardenFloor.replacementLocIds +
                AKKHA_MEMORY_SYMBOLS.values.map { it.locId } +
                setOf(
                    ToaLocs.GROUPING_OBELISK,
                    ToaLocs.INVOCATION_BOARD,
                    ToaLocs.START,
                    ToaLocs.CHALLENGE_BARRIER,
                    ToaLocs.BANK_CAMEL,
                    *ToaLocs.rewardNiches.toTypedArray(),
                    ToaLocs.WARDENS_PATH_LOCKED,
                    ToaLocs.WARDENS_PATH,
                    ToaLocs.WARDENS_CRYSTAL,
                    ToaLocs.REWARD_ENTRY,
                )
        requiredLocs.forEach {
            requireNotNull(ServerCacheManager.getObject(it)) {
                "Missing native revision-240 ToA loc $it"
            }
        }
        (ToaNpcs.allCombat +
                ZebakAssets.validationNpcIds +
                ToaNpcs.apmekenWaveTypes +
                ToaNpcs.akkhaForms +
                setOf(
                    AkkhaAssets.HEADBAR,
                    ToaNpcs.COMPLETION_SPIRIT,
                    ToaNpcs.HELPFUL_SPIRIT,
                    ToaNpcs.OSMUMTEN,
                ))
            .forEach {
                requireNotNull(ServerCacheManager.getNpc(it)) {
                    "Missing native revision-240 ToA npc $it"
                }
            }
        ToaAssets.sequences.forEach {
            requireNotNull(ServerCacheManager.getAnim(it)) {
                "Missing native revision-240 ToA sequence $it"
            }
        }
        ToaAssets.spotanims.forEach {
            requireNotNull(RSCM.getReverseMapping(RSCMType.SPOTANIM, it)) {
                "Missing native revision-240 ToA spotanim $it"
            }
        }
        requireNotNull(ServerCacheManager.getVarbit(ToaLocs.REWARD_NICHE_VARBIT)) {
            "Missing native revision-240 ToA Rewards Niche varbit ${ToaLocs.REWARD_NICHE_VARBIT}"
        }
        rewards.verifyDefinitions()
        supplies.verifyDefinitions()
        partyBoard.validateAssets()
        het.validateAssets()
        hetPickaxeStorage.validateAssets()
        ui.verifyDefinitions()
    }

    fun createParty(player: Player): ToaParty {
        partyFor(player)?.let {
            return it
        }
        return ToaParty(player.uid).also {
            parties[player.uid] = it
            membership[player.uid] = it
            player.mes("You create a Tombs of Amascut party.")
        }
    }

    fun openLobby(player: Player): Boolean {
        hetPickaxeStorage.sync(player)
        raidFor(player)?.let {
            return rejoin(player, it)
        }
        syncRewardNiche(player)
        if (rewards.hasStored(player)) {
            player.mes("The Rewards Niche is holding an unclaimed Tombs reward for you.")
        }
        openPartyList(player)
        return true
    }

    fun teleportLobby(player: Player): Boolean {
        if (raidFor(player) != null) {
            player.mes("Leave or complete your active Tombs raid before returning to the lobby.")
            return false
        }
        if (instances.sessionForPlayer(player) != null) {
            player.mes("Leave your current instance before teleporting to Tombs of Amascut.")
            return false
        }
        prepare(player)
        syncRewardNiche(player)
        PathingEntityCommon.telejump(player, collision, TOA_ENTRANCE)
        player.mes(
            "You arrive in the Tombs of Amascut waiting room. Use the grouping obelisk to form a party."
        )
        return true
    }

    fun openInvocationBoard(player: Player): Boolean {
        partyBoard.openInvocationBoard(player)
        player.mes("Choose invocations through your party's Invocations tab before entering.")
        return true
    }

    fun refreshPartyListFilter(player: Player): Boolean {
        // The revision-240 list has a native Filter control. OpenRune does not yet expose the
        // social-list predicate it needs, so do not silently apply an invented filter; acknowledge
        // the action and redraw the unfiltered, authoritative party board instead.
        player.mes("Friends-only filtering is not available yet; showing all public ToA parties.")
        return openPartyList(player)
    }

    fun openPartyList(player: Player): Boolean {
        viewedParties.remove(player.uid)
        partyBoard.openList(
            player = player,
            parties = parties.values.toList(),
            hasParty = partyFor(player) != null,
        ) {
            label(it.leader)
        }
        return true
    }

    fun openOwnMembers(player: Player): Boolean {
        val party = partyFor(player) ?: createParty(player)
        viewedParties[player.uid] = party.id
        partyBoard.openMembers(player, party) { it.resolve(players) }
        return true
    }

    fun openApplicants(player: Player): Boolean {
        val party = partyFor(player) ?: return openOwnMembers(player)
        viewedParties[player.uid] = party.id
        partyBoard.openApplicants(player, party) { it.resolve(players) }
        return true
    }

    fun openInvocations(player: Player): Boolean {
        val party = partyFor(player)
        if (party == null) {
            player.mes("Create or join a party first.")
            openOwnMembers(player)
            return false
        }
        viewedParties[player.uid] = party.id
        partyBoard.openInvocations(player, party) { it.resolve(players) }
        return true
    }

    fun openPartyView(player: Player, boardSlot: Int): Boolean {
        val party = parties.values.elementAtOrNull(boardSlot)
        if (party == null) return openPartyList(player)
        viewedParties[player.uid] = party.id
        partyBoard.openView(player, party) { it.resolve(players) }
        return true
    }

    fun applyToViewedParty(player: Player): Boolean {
        val party = viewedParty(player) ?: return false
        if (partyFor(player) != null) {
            player.mes("You are already in a ToA party.")
            return false
        }
        if (
            player.uid in party.blocked ||
                party.activeSession != null ||
                party.members.size >= TOA_MAX_PARTY_SIZE
        ) {
            player.mes("That party is not accepting your application.")
            return false
        }
        party.applicants += player.uid
        player.mes("You apply to ${label(party.leader)}'s ToA party.")
        openPartyView(player, parties.values.indexOf(party))
        party.leader.resolve(players)?.mes("${player.displayName} has applied to your ToA party.")
        return true
    }

    fun partyMainAction(player: Player): Boolean {
        val party = partyFor(player)
        val viewed = viewedParty(player)
        if (viewed != null && player.uid !in viewed.members) {
            if (player.uid in viewed.applicants) {
                viewed.applicants.remove(player.uid)
                player.mes("You withdraw your ToA party application.")
                partyBoard.openView(player, viewed) { it.resolve(players) }
                return true
            }
            return applyToViewedParty(player)
        }
        return when {
            party == null -> {
                createParty(player)
                openOwnMembers(player)
            }
            party.leader == player.uid -> {
                disbandParty(player, party)
                openPartyList(player)
            }
            else -> {
                leaveParty(player)
                openPartyList(player)
            }
        }
    }

    fun openViewedMembers(player: Player): Boolean {
        val party = viewedParty(player) ?: partyFor(player) ?: return openPartyList(player)
        partyBoard.openView(player, party) { it.resolve(players) }
        return true
    }

    fun openViewedApplicants(player: Player): Boolean {
        val party = viewedParty(player) ?: partyFor(player) ?: return openPartyList(player)
        if (party.leader != player.uid) return openViewedMembers(player)
        partyBoard.openApplicants(player, party) { it.resolve(players) }
        return true
    }

    fun openViewedInvocations(player: Player): Boolean {
        val party = viewedParty(player) ?: partyFor(player) ?: return openPartyList(player)
        partyBoard.openInvocations(player, party) { it.resolve(players) }
        return true
    }

    fun openPartySummary(player: Player): Boolean {
        player.mes(
            "Raid summaries are shown after a raid finishes. Your party settings have not changed."
        )
        return openViewedMembers(player)
    }

    fun openPartyRewards(player: Player): Boolean {
        if (rewards.hasStored(player)) {
            player.mes("A Tombs reward is waiting for you in the Rewards Niche.")
        } else {
            player.mes("You do not have an unclaimed Tombs reward in the Rewards Niche.")
        }
        return openViewedMembers(player)
    }

    fun openPartyCompletions(player: Player): Boolean {
        val entry = player.attr.getOrDefault(TOA_ENTRY_COMPLETIONS, 0)
        val normal = player.attr.getOrDefault(TOA_NORMAL_COMPLETIONS, 0)
        val expert = player.attr.getOrDefault(TOA_EXPERT_COMPLETIONS, 0)
        player.mes("Tombs completions - Entry: $entry, Normal: $normal, Expert: $expert.")
        return openViewedMembers(player)
    }

    fun openInvocationPresets(player: Player): Boolean {
        player.mes("Select one of the five preset slots, then choose Load or Save.")
        return openViewedInvocations(player)
    }

    fun loadInvocationPreset(player: Player): Boolean {
        val party = editableInvocationParty(player) ?: return false
        val slot = selectedInvocationPreset(player)
        if (slot == null) {
            player.mes("Select an invocation preset slot before loading.")
            return openViewedInvocations(player)
        }
        val encoded = presetStorage(player)[slot]
        if (encoded.isBlank()) {
            player.mes("That invocation preset slot is empty.")
            return openViewedInvocations(player)
        }
        val invocations = ToaInvocationPresetCodec.decode(encoded)
        if (invocations == null) {
            player.mes("That saved invocation preset is invalid and was not loaded.")
            return openViewedInvocations(player)
        }
        return when (val result = party.invocations.replace(invocations)) {
            ToaInvocationLoadResult.Loaded -> {
                setSelectedInvocationPreset(player, null)
                player.mes("Invocation preset ${slot + 1} has been loaded.")
                openViewedInvocations(player)
            }
            is ToaInvocationLoadResult.Rejected -> {
                player.mes(
                    result.violations.joinToString(" ", transform = ToaInvocationViolation::message)
                )
                openViewedInvocations(player)
            }
        }
    }

    fun saveInvocationPreset(player: Player): Boolean {
        val party = editableInvocationParty(player) ?: return false
        val slot = selectedInvocationPreset(player)
        if (slot == null) {
            player.mes("Select an invocation preset slot before saving.")
            return openViewedInvocations(player)
        }
        val storage = presetStorage(player)
        val overwrote = storage[slot].isNotBlank()
        storage[slot] = ToaInvocationPresetCodec.encode(party.invocations.all())
        player.attr[TOA_INVOCATION_PRESETS] = storage
        setSelectedInvocationPreset(player, null)
        player.mes(
            if (overwrote) "Invocation preset ${slot + 1} has been overwritten."
            else "Invocation preset ${slot + 1} has been saved."
        )
        return openViewedInvocations(player)
    }

    fun selectInvocationPreset(player: Player, slot: Int, clear: Boolean): Boolean {
        editableInvocationParty(player) ?: return false
        if (slot !in 0 until ToaInvocationPresetCodec.SLOT_COUNT) {
            player.mes("That invocation preset slot does not exist.")
            return false
        }
        if (clear) {
            val storage = presetStorage(player)
            storage[slot] = ""
            player.attr[TOA_INVOCATION_PRESETS] = storage
            setSelectedInvocationPreset(player, null)
            player.mes("Invocation preset ${slot + 1} has been cleared.")
        } else {
            val selected = if (selectedInvocationPreset(player) == slot) null else slot
            setSelectedInvocationPreset(player, selected)
        }
        return openViewedInvocations(player)
    }

    fun clearBlockedApplicants(player: Player): Boolean {
        val party = partyFor(player) ?: return false
        if (party.leader != player.uid || party.activeSession != null) return false
        party.blocked.clear()
        player.mes("Rejected players may apply to your ToA party again.")
        return openApplicants(player)
    }

    fun memberRowAction(player: Player, index: Int): Boolean {
        val party = partyFor(player) ?: return false
        if (party.activeSession != null) {
            player.mes("Party membership cannot be changed after the raid has started.")
            return false
        }
        val member = party.members.elementAtOrNull(index) ?: return false
        if (party.leader == player.uid) {
            if (member == player.uid) {
                player.mes("Use the Disband Party button to close a party you lead.")
                return false
            }
            return kickMember(player, index)
        }
        if (member != player.uid) {
            player.mes("Only the party leader can remove another member.")
            return false
        }
        val left = leaveParty(player)
        if (left) openPartyList(player)
        return left
    }

    fun kickMember(player: Player, index: Int): Boolean {
        val party = partyFor(player) ?: return false
        if (party.leader != player.uid || party.activeSession != null || index <= 0) return false
        val member = party.members.elementAtOrNull(index) ?: return false
        party.members.remove(member)
        membership.remove(member)
        member.resolve(players)?.mes("You were removed from ${player.displayName}'s ToA party.")
        message(party, "${label(member)} leaves the ToA party.")
        return openOwnMembers(player)
    }

    fun acceptApplicant(player: Player, index: Int): Boolean =
        applicantAction(player, index, accept = true)

    fun rejectApplicant(player: Player, index: Int): Boolean =
        applicantAction(player, index, accept = false)

    private fun applicantAction(player: Player, index: Int, accept: Boolean): Boolean {
        val party = partyFor(player) ?: return false
        if (party.leader != player.uid || party.activeSession != null) return false
        val applicant = party.applicants.elementAtOrNull(index) ?: return false
        party.applicants.remove(applicant)
        if (
            accept && party.members.size < TOA_MAX_PARTY_SIZE && !membership.containsKey(applicant)
        ) {
            party.members += applicant
            membership[applicant] = party
            applicant
                .resolve(players)
                ?.mes("You have been accepted into ${player.displayName}'s ToA party.")
            message(party, "${label(applicant)} joins the ToA party.")
        } else if (!accept) {
            party.blocked += applicant
            applicant.resolve(players)?.mes("Your ToA party application was declined.")
        }
        return openApplicants(player)
    }

    fun toggleInvocationFromInterface(player: Player, subcomponent: Int): Boolean {
        val invocation = ToaInvocation.fromInterfaceSubcomponent(subcomponent)
        if (invocation == null) {
            player.mes("That Tombs invocation interface entry is not selectable.")
            openInvocations(player)
            return false
        }
        val changed = toggleInvocation(player, invocation.key)
        // Refresh even when it is rejected, so a client-side toggle cannot visually imply a
        // nonzero invocation is active.
        openInvocations(player)
        return changed
    }

    fun leaveParty(player: Player): Boolean {
        val party = partyFor(player) ?: return false
        party.activeSession?.let {
            return leaveRaid(player, it, true)
        }
        party.members.remove(player.uid)
        membership.remove(player.uid)
        if (party.leader == player.uid) transferOrDisband(party)
        player.mes("You leave the ToA party.")
        return true
    }

    fun toggleInvocation(player: Player, key: String): Boolean {
        val party = partyFor(player)
        if (party == null) {
            player.mes("Create a party at the grouping obelisk first.")
            return false
        }
        if (party.leader != player.uid || party.activeSession != null) {
            player.mes("Only the party leader can change invocations before the raid starts.")
            return false
        }
        val invocation = ToaInvocation.find(key)
        if (invocation == null) {
            player.mes("That invocation is not available on the Tombs interface.")
            return false
        }
        return when (val result = party.invocations.toggleValidated(invocation)) {
            is ToaInvocationMutation.Changed -> {
                val action = if (result.enabled) "enabled" else "disabled"
                player.mes("${invocation.displayName} has been $action.")
                if (result.automaticallyDisabled.isNotEmpty()) {
                    player.mes(
                        "Also disabled: " +
                            ToaInvocationCatalog.sortForClient(result.automaticallyDisabled)
                                .joinToString { it.displayName } +
                            "."
                    )
                }
                true
            }
            is ToaInvocationMutation.Rejected -> {
                player.mes(
                    result.violations.joinToString(" ", transform = ToaInvocationViolation::message)
                )
                false
            }
        }
    }

    fun clearInvocations(player: Player): Boolean {
        val party = partyFor(player)
        if (party == null) {
            player.mes("Create a party at the grouping obelisk first.")
            return false
        }
        if (party.leader != player.uid || party.activeSession != null) return false
        party.invocations.clear()
        player.mes("All ToA invocations have been disabled.")
        return true
    }

    fun invocationStatus(player: Player): String {
        val set = partyFor(player)?.invocations ?: return "You are not in a ToA party."
        val names = set.all().joinToString { it.displayName }.ifEmpty { "none" }
        return "ToA raid level ${set.raidLevel}; enabled: $names."
    }

    private fun editableInvocationParty(player: Player): ToaParty? {
        val party = partyFor(player)
        if (party == null) {
            player.mes("Create a party at the grouping obelisk first.")
            return null
        }
        if (party.leader != player.uid || party.activeSession != null) {
            player.mes("Only the party leader can change invocations before the raid starts.")
            return null
        }
        return party
    }

    private fun presetStorage(player: Player): MutableList<String> =
        ToaInvocationPresetCodec.normalizeStorage(player.attr[TOA_INVOCATION_PRESETS])

    private fun selectedInvocationPreset(player: Player): Int? {
        val varbit =
            requireNotNull(
                ServerCacheManager.getVarbit(ToaPartyBoard.INVOCATION_PRESET_SELECT_VARBIT)
            )
        return (player.vars[varbit] - 1).takeIf {
            it in 0 until ToaInvocationPresetCodec.SLOT_COUNT
        }
    }

    private fun setSelectedInvocationPreset(player: Player, slot: Int?) {
        val varbit =
            requireNotNull(
                ServerCacheManager.getVarbit(ToaPartyBoard.INVOCATION_PRESET_SELECT_VARBIT)
            )
        VarPlayerIntMapSetter.set(player, varbit, slot?.plus(1) ?: 0)
    }

    fun start(player: Player): Boolean {
        val party = partyFor(player)
        if (party == null) {
            player.mes("You need to create a party at the grouping obelisk before starting a raid.")
            return false
        }
        if (party.leader != player.uid) {
            player.mes("Only the party leader can start the raid.")
            return false
        }
        if (party.activeSession != null || raidFor(player) != null) return false
        val resolved = party.members.associateWith { it.resolve(players) }
        val offline = resolved.filterValues { it == null }.keys
        if (offline.isNotEmpty()) {
            player.mes(
                "Every party member must be online before the raid can start: ${offline.joinToString { label(it) }}."
            )
            return false
        }
        val online = resolved.values.filterNotNull()
        val unclaimed = online.filter(rewards::hasStored)
        if (unclaimed.isNotEmpty()) {
            player.mes(
                "Collect the reward held in the Rewards Niche before starting: " +
                    unclaimed.joinToString { it.displayName }
            )
            return false
        }
        if (online.isEmpty() || online.size > TOA_MAX_PARTY_SIZE) return false
        if (online.any { instances.sessionForPlayer(it) != null }) {
            player.mes("Every party member must leave their current instance first.")
            return false
        }
        val lockedInvocations = party.invocations.all()
        val invocationViolations = ToaInvocationRules.validate(lockedInvocations)
        if (invocationViolations.isNotEmpty()) {
            player.mes(
                invocationViolations.joinToString(" ", transform = ToaInvocationViolation::message)
            )
            return false
        }
        val level = ToaInvocationCatalog.raidLevel(lockedInvocations)
        val settings =
            InstanceSettings(
                    maxPlayers = TOA_MAX_PARTY_SIZE,
                    reclaimTicks = 2_000,
                    graceTicks = 10,
                    destroyWhenEmpty = false,
                    description = "Tombs of Amascut level=$level party=${party.id}",
                    timeLimitTicks = null,
                )
                .withArea(ToaInstanceLayout.area(), settingsRowId = 0)
        val created =
            instances.create(
                player,
                TOA_INSTANCE_KEY,
                settings,
                InstanceAccess.Private,
                clock.cycle,
            )
        if (created !is InstanceManager.Result.Created) {
            player.mes(
                (created as? InstanceManager.Result.Failed)?.reason
                    ?: "The raid could not be created."
            )
            return false
        }

        // Joining is transactional: do not publish a raid or move anyone until every party member
        // has an entry coordinate. A failed late join tears down the fresh instance cleanly.
        val entries = linkedMapOf(player.uid to created.enter)
        for (member in online) {
            if (member.uid == player.uid) continue
            val joined = instances.join(member, created.session, clock.cycle, forceAccess = true)
            if (joined !is InstanceManager.Result.Joined) {
                instances.terminate(created.session)
                player.mes(
                    (joined as? InstanceManager.Result.Failed)?.reason
                        ?: "${member.displayName} could not enter the raid. No one was moved."
                )
                return false
            }
            entries[member.uid] = joined.enter
        }

        val pathLevels = ToaRules.initialPathLevels(lockedInvocations)
        val snapshot =
            ToaRaidStartSnapshot.create(
                partySize = online.size,
                raidLevel = level,
                invocations = lockedInvocations,
                pathLevels = pathLevels,
                seed =
                    devSeedOverrides.consume(player.accountHash)
                        ?: (party.id.mostSignificantBits xor
                            party.id.leastSignificantBits xor
                            clock.cycle.toLong()),
                participantAccountHashes = online.mapTo(linkedSetOf()) { it.accountHash },
            )
        val raid =
            ToaRaidSession(party, snapshot, clock.cycle).also { it.instance = created.session }
        online.forEach { member ->
            raid.participants.add(
                ToaParticipant(
                    uid = member.uid,
                    accountHash = member.accountHash,
                    tertiaryProfile = rewards.snapshotTertiaryProfile(member),
                )
            )
        }
        raids[created.session.id] = raid
        party.activeSession = raid
        syncWardensEntrance(raid, unlocked = false)
        online.forEach { member ->
            val entry = requireNotNull(entries[member.uid])
            prepare(member)
            applyRaidRestrictions(member, raid)
            PathingEntityCommon.telejump(member, collision, entry)
            instances.finalizeEntry(member, created.session, clock.cycle)
            participantRaids[member.accountHash] = raid
            supplies.sanitizeStaleLogin(member)
            supplies.begin(member, raid)
            raid.participant(member.accountHash)?.currentRoom = ToaRoom.LOBBY
            resetCombat(member)
            ui.open(member, raid, clock.cycle)
            member.mes("You enter the Tombs at raid level $level (${raid.mode}).")
        }
        log.info {
            "[ToA] start party=${party.id} instance=${raid.instance.id} players=${online.size} level=$level"
        }
        return true
    }

    fun rejoin(player: Player, raid: ToaRaidSession): Boolean {
        if (raid.state in setOf(ToaRaidState.ABANDONED, ToaRaidState.COMPLETE)) return false
        val participant = raid.participant(player.accountHash)
        if (participant == null || !participant.rewardEligible) {
            player.mes("You were not a participant in that Tombs raid.")
            return false
        }
        val result = instances.join(player, raid.instance, clock.cycle, forceAccess = true)
        if (result !is InstanceManager.Result.Joined) return false
        val oldUid = raid.participants.rebind(player.accountHash, player.uid) ?: return false
        rebindRaidPlayer(raid, oldUid, player.uid)
        prepare(player)
        applyRaidRestrictions(player, raid)
        if (!supplies.resume(player, raid)) supplies.begin(player, raid)
        supplies.claimPendingWipeSupplies(player, raid)
        het.syncRejoin(player, raid)
        hetPickaxeStorage.sync(player)
        val room = rejoinDestination(raid, participant)
        participant.currentRoom = room
        val destination =
            if (isWardenRoom(room) && raid.encounterStarted && !participant.spectating) {
                fightCoord(raid, room)
            } else {
                roomCoord(raid, room)
            }
        PathingEntityCommon.telejump(player, collision, destination)
        instances.finalizeEntry(player, raid.instance, clock.cycle)
        participant.connected = true
        participantRaids[player.accountHash] = raid
        rewards.sync(player, raid)
        if (raid.state == ToaRaidState.REWARDS) {
            recordCompletion(player, raid, participant)
        }
        ui.open(player, raid, clock.cycle)
        if (
            room == ToaRoom.APMEKEN &&
                raid.challenge?.stage == ToaChallengeStage.ACTIVE &&
                ToaChallengeRoomRules.isInside(raid, instances, room, player.coords)
        ) {
            enterApmekenPlayer(raid, player)
        }
        player.mes("You rejoin your party in ${room.displayName}.")
        return true
    }

    fun enterPath(player: Player, path: ToaPath): Boolean {
        val raid = raidFor(player) ?: return false
        if (path !in TOA_ACTIVE_PATHS) return disabledKephriRoute(player)
        if (path in raid.completedPaths) {
            player.mes("Your party has already completed the Path of ${path.displayName}.")
            return false
        }
        val destination =
            if (
                raid.currentPath == path &&
                    (raid.currentRoom == path.boss || raid.challenge?.complete == true)
            )
                path.boss
            else path.challenge
        if (raid.currentPath == null) {
            if (!canLeadRaidAction(player, raid)) {
                player.mes("The party leader must enter the room first.")
                return false
            }
            if (raid.completedPaths.size == 2) {
                raid.participants.values.forEach { participant ->
                    supplies.forfeitFirstCheckpoint(raid, participant.accountHash)
                }
            }
            raid.currentPath = path
            if (raid.timerStartedAt == null) raid.timerStartedAt = clock.cycle
            raid.currentRoom = destination
            raid.challenge = ToaChallengeState(path)
            raid.encounterStarted = false
            raid.completionPending = false
            message(
                raid.party,
                "The leader chooses ${destination.displayName} at path level ${raid.pathLevels[path] ?: 0}.",
            )
        } else if (raid.currentPath != path) {
            player.mes("You must enter the same room the party leader did.")
            return false
        }
        enterRoom(player, raid, destination)
        return true
    }

    fun useChallengeBarrier(
        player: Player,
        barrier: BoundLocInfo,
        confirmed: Boolean,
    ): ToaBarrierDecision {
        if (barrier.id != ToaLocs.CHALLENGE_BARRIER) return ToaBarrierDecision.DENY
        val raid = raidFor(player) ?: return ToaBarrierDecision.DENY
        val participant = raid.participant(player.uid) ?: return ToaBarrierDecision.DENY
        val path = raid.currentPath ?: return ToaBarrierDecision.DENY
        val challenge = raid.challenge ?: return ToaBarrierDecision.DENY
        if (
            challenge.path != path ||
                raid.currentRoom != path.challenge ||
                participant.currentRoom != path.challenge
        ) {
            return ToaBarrierDecision.DENY
        }
        val inside = ToaChallengeRoomRules.isInside(raid, instances, path.challenge, player.coords)
        val decision =
            ToaChallengeRoomRules.decide(
                challenge.stage,
                participant.spectating,
                inside,
                confirmed,
                completionExitOpen =
                    path == ToaPath.CRONDIS && raid.crondis.state == CrondisState.COMPLETE,
            )
        when (decision) {
            ToaBarrierDecision.START_CUTSCENE -> {
                challenge.stage = ToaChallengeStage.STARTING
                challenge.entryStarter = player.uid
                challenge.entryDeadline = clock.cycle + CHALLENGE_ENTRY_TIMEOUT_TICKS
            }
            ToaBarrierDecision.EXIT_COMPLETED_CHALLENGE ->
                exitCompletedChallengeBarrier(player, raid, path, barrier)
            ToaBarrierDecision.BLOCK_ACTIVE_EXIT ->
                player.mes("The challenge is active; you cannot leave through the barrier.")
            ToaBarrierDecision.ENTRY_BUSY ->
                player.mes("A party member is entering the challenge. Please wait.")
            ToaBarrierDecision.DENY ->
                if (participant.spectating) player.mes("You are unable to pass as a ghost.")
            else -> Unit
        }
        return decision
    }

    private fun exitCompletedChallengeBarrier(
        player: Player,
        raid: ToaRaidSession,
        path: ToaPath,
        barrier: BoundLocInfo,
    ) {
        val destination = challengeBarrierExitDestination(player, barrier)
        if (destination == null) {
            player.mes("The passage is blocked from this side.")
            return
        }
        prepare(player)
        applyRaidRestrictions(player, raid)
        PathingEntityCommon.telejump(player, collision, destination)
        player.mes(
            "You pass through the barrier. Continue through the passage to " +
                path.boss.displayName +
                "."
        )
    }

    /**
     * Resolve the far side of the exact cache-authored barrier that the player clicked. Challenge
     * entrances are not all aligned with their generic room entry coordinate, and large barrier
     * locs can occupy more than one tile. The closest valid tile beyond the full object footprint
     * is therefore authoritative for both standard Pass and RuneLite Quick-Pass.
     */
    private fun challengeBarrierExitDestination(player: Player, barrier: BoundLocInfo): CoordGrid? {
        val minX = barrier.x
        val maxX = barrier.x + barrier.adjustedWidth - 1
        val minZ = barrier.z
        val maxZ = barrier.z + barrier.adjustedLength - 1
        val horizontal =
            kotlin.math.abs(player.coords.x - barrier.adjustedCentre.x) >=
                kotlin.math.abs(player.coords.z - barrier.adjustedCentre.z)
        val distances = listOf(2, 3, 1, 4, 5, 6)
        val offsets = listOf(0, -1, 1, -2, 2)
        val candidates = buildList {
            for (distance in distances) for (offset in offsets) {
                if (horizontal) {
                    val x = if (player.coords.x <= minX) maxX + distance else minX - distance
                    add(CoordGrid(x, player.coords.z + offset, player.coords.level))
                } else {
                    val z = if (player.coords.z <= minZ) maxZ + distance else minZ - distance
                    add(CoordGrid(player.coords.x + offset, z, player.coords.level))
                }
            }
        }
        return candidates.firstOrNull { collision.isZoneValid(it) && !collision.isWalkBlocked(it) }
    }

    fun challengeEntryCutscene(player: Player): ToaResolvedEntryCutscene? {
        val raid = raidFor(player) ?: return null
        val path = raid.currentPath ?: return null
        if (raid.currentRoom != path.challenge) return null
        return ToaChallengeRoomRules.resolveCutscene(raid, instances, path.challenge)
    }

    fun finishChallengeBarrier(player: Player, barrier: BoundLocInfo): Boolean {
        val raid = raidFor(player) ?: return false
        val participant = raid.participant(player.uid) ?: return false
        val path = raid.currentPath ?: return false
        val challenge = raid.challenge ?: return false
        if (
            challenge.path != path ||
                raid.currentRoom != path.challenge ||
                participant.currentRoom != path.challenge ||
                participant.spectating
        ) {
            return false
        }
        val startsChallenge = challenge.stage == ToaChallengeStage.STARTING
        if (startsChallenge && challenge.entryStarter != player.uid) return false
        if (!startsChallenge && challenge.stage != ToaChallengeStage.ACTIVE) return false

        val destination =
            challengeBarrierEntryDestination(player, raid, path.challenge, barrier)
                ?: run {
                    player.mes("The passage beyond this barrier is blocked.")
                    return false
                }
        prepare(player)
        applyRaidRestrictions(player, raid)
        PathingEntityCommon.telejump(player, collision, destination)

        if (startsChallenge) {
            challenge.stage = ToaChallengeStage.ACTIVE
            challenge.entryStarter = null
            challenge.entryDeadline = -1
            beginChallenge(raid, path)
            message(
                raid.party,
                "${path.challenge.displayName} begins as the red barrier is crossed.",
            )
        } else if (path == ToaPath.APMEKEN) {
            enterApmekenPlayer(raid, player)
        }
        return true
    }

    private fun challengeBarrierEntryDestination(
        player: Player,
        raid: ToaRaidSession,
        room: ToaRoom,
        barrier: BoundLocInfo,
    ): CoordGrid? {
        val minX = barrier.x
        val maxX = barrier.x + barrier.adjustedWidth - 1
        val minZ = barrier.z
        val maxZ = barrier.z + barrier.adjustedLength - 1
        return ToaBarrierCrossing.candidates(player.coords, minX, minZ, maxX, maxZ).firstOrNull {
            ToaChallengeRoomRules.isInside(raid, instances, room, it) &&
                collision.isZoneValid(it) &&
                !collision.isWalkBlocked(it)
        }
    }

    fun cancelChallengeBarrier(player: Player) {
        val challenge = raidFor(player)?.challenge ?: return
        if (challenge.stage == ToaChallengeStage.STARTING && challenge.entryStarter == player.uid) {
            challenge.stage = ToaChallengeStage.INACTIVE
            challenge.entryStarter = null
            challenge.entryDeadline = -1
        }
    }

    fun disabledKephriRoute(player: Player): Boolean {
        player.mes("That Tombs path is not available.")
        return false
    }

    fun interactChallenge(player: Player, objectId: Int): Boolean {
        val raid = raidFor(player) ?: return false
        if (raid.currentPath != ToaPath.CRONDIS || raid.currentRoom != ToaRoom.CRONDIS) return false
        if (!requireActiveChallenge(player, raid, ToaPath.CRONDIS, requireInside = false))
            return false
        return when (objectId) {
            CrondisAssets.CONTINUE_LOC -> {
                if (!crondisZebak.canContinueFromCrondis(player, raid)) return false
                clearEncounterNpcs(raid)
                completeChallenge(raid)
                true
            }
            else -> false
        }
    }

    fun interactHetObject(player: Player, action: HetLiveObjectAction, loc: BoundLocInfo): Boolean {
        val raid = raidFor(player) ?: return false
        if (!requireActiveChallenge(player, raid, ToaPath.HET)) return false
        return handleHetResult(raid, het.interactMirror(player, raid, loc, action))
    }

    fun mineHetSeal(player: Player, npc: Npc): Boolean {
        val raid = raidFor(player) ?: return false
        if (!requireActiveChallenge(player, raid, ToaPath.HET)) return false
        return handleHetResult(raid, het.mineSeal(player, raid, npc))
    }

    fun placeHetMirror(player: Player): Boolean {
        val raid = raidFor(player) ?: return false
        if (!requireActiveChallenge(player, raid, ToaPath.HET)) return false
        return handleHetResult(raid, het.placeMirror(player, raid))
    }

    fun takeHetPickaxe(player: Player): Boolean {
        val raid = raidFor(player) ?: return false
        if (!requireActiveChallenge(player, raid, ToaPath.HET)) return false
        return hetPickaxeStorage.take(player)
    }

    fun depositHetPickaxe(player: Player): Boolean {
        val raid = raidFor(player) ?: return false
        if (!requireActiveChallenge(player, raid, ToaPath.HET)) return false
        return hetPickaxeStorage.deposit(player)
    }

    fun interactScabarasObject(
        player: Player,
        action: ScabarasLiveObjectAction,
        loc: BoundLocInfo,
    ): Boolean {
        val raid = raidFor(player) ?: return false
        if (raid.currentPath != ToaPath.SCABARAS || raid.currentRoom != ToaRoom.SCABARAS) {
            return false
        }
        if (!requireActiveChallenge(player, raid, ToaPath.SCABARAS)) return false
        val result = scabaras.interact(raid, player, action, loc)
        if (result.challengeCompleted) completeScabarasChallenge(raid)
        return result.handled
    }

    fun scabarasPassageDestination(player: Player, passage: BoundLocInfo): CoordGrid? {
        val raid = raidFor(player) ?: return null
        if (raid.currentPath != ToaPath.SCABARAS || raid.currentRoom != ToaRoom.SCABARAS) {
            return null
        }
        if (!requireActiveChallenge(player, raid, ToaPath.SCABARAS)) return null

        val sourceLane =
            ScabarasRevision240RuntimeLayout.branchPassages.entries
                .firstOrNull { (_, source) ->
                    instances.localCoord(
                        raid.instance,
                        ToaInstanceLayout.roomLocal(ToaRoom.SCABARAS, source.tile.x, source.tile.z),
                    ) == passage.coords
                }
                ?.key ?: return null
        val destinationLane =
            if (sourceLane == ScabarasLane.NORTH) ScabarasLane.SOUTH else ScabarasLane.NORTH
        return ScabarasRevision240RuntimeLayout.branchPassageLandings
            .getValue(destinationLane)
            .asSequence()
            .mapNotNull { local ->
                instances.localCoord(
                    raid.instance,
                    ToaInstanceLayout.roomLocal(ToaRoom.SCABARAS, local.x, local.z),
                )
            }
            .firstOrNull { destination ->
                collision.isZoneValid(destination) && !collision.isWalkBlocked(destination)
            }
    }

    fun fillCrondisContainer(player: Player, waterfall: BoundLocInfo): Boolean {
        val raid = raidFor(player) ?: return false
        if (!requireActiveChallenge(player, raid, ToaPath.CRONDIS)) return false
        return crondisZebak.fillCrondisContainer(player, raid, waterfall)
    }

    fun waterCrondisPalm(player: Player, palm: Npc): Boolean {
        val raid = raidFor(player) ?: return false
        if (!requireActiveChallenge(player, raid, ToaPath.CRONDIS)) return false
        return crondisZebak.waterCrondisPalm(player, palm, raid)
    }

    fun checkCrondisContainer(player: Player): Boolean {
        val raid = raidFor(player) ?: return false
        if (!requireActiveChallenge(player, raid, ToaPath.CRONDIS)) return false
        return crondisZebak.checkCrondisContainer(player, raid)
    }

    fun emptyCrondisContainer(player: Player): Boolean {
        val raid = raidFor(player) ?: return false
        if (!requireActiveChallenge(player, raid, ToaPath.CRONDIS)) return false
        return crondisZebak.emptyCrondisContainer(player, raid)
    }

    fun destroyCrondisContainer(player: Player): Boolean {
        val raid = raidFor(player) ?: return false
        if (!requireActiveChallenge(player, raid, ToaPath.CRONDIS)) return false
        return crondisZebak.destroyCrondisContainer(player, raid)
    }

    fun takeCrondisContainer(player: Player, obj: Obj): Boolean {
        val raid = raidFor(player) ?: return false
        if (!requireActiveChallenge(player, raid, ToaPath.CRONDIS)) return false
        return crondisZebak.takeCrondisContainer(player, raid, obj)
    }

    fun pushZebakJug(player: Player, npc: Npc): Boolean =
        raidFor(player)?.let { crondisZebak.pushJug(player, npc, it) } ?: false

    fun pullZebakJug(player: Player, npc: Npc): Boolean =
        raidFor(player)?.let { crondisZebak.pullJug(player, npc, it) } ?: false

    fun hitZebakJug(player: Player, npc: Npc): Boolean =
        raidFor(player)?.let { crondisZebak.hitJug(player, npc, it) } ?: false

    fun climbZebakSteps(player: Player, steps: BoundLocInfo): Boolean =
        raidFor(player)?.let { crondisZebak.climbZebakSteps(player, steps, it) } ?: false

    fun crondisTrace(player: Player): String =
        raidFor(player)?.let(crondisZebak::crondisTrace) ?: "You are not in a Tombs raid."

    fun zebakTrace(player: Player): String =
        raidFor(player)?.let(crondisZebak::zebakTrace) ?: "You are not in a Tombs raid."

    fun setCrondisTrace(player: Player, mode: String) {
        val raid = raidFor(player)
        if (raid == null) {
            player.mes("You are not in a Tombs raid.")
            return
        }
        when (mode.lowercase()) {
            "on" -> {
                crondisZebak.setCrondisTrace(raid, true)
                player.mes("Crondis trace enabled for this raid.")
            }
            "off" -> {
                crondisZebak.setCrondisTrace(raid, false)
                player.mes("Crondis trace disabled for this raid.")
            }
            else -> player.mes(crondisZebak.crondisTrace(raid))
        }
    }

    fun setZebakTrace(player: Player, mode: String) {
        val raid = raidFor(player)
        if (raid == null) {
            player.mes("You are not in a Tombs raid.")
            return
        }
        when (mode.lowercase()) {
            "on" -> {
                crondisZebak.setZebakTrace(raid, true)
                player.mes("Zebak trace enabled for this raid.")
            }
            "off" -> {
                crondisZebak.setZebakTrace(raid, false)
                player.mes("Zebak trace disabled for this raid.")
            }
            else -> player.mes(crondisZebak.zebakTrace(raid))
        }
    }

    private fun canEnterBoss(player: Player, path: ToaPath): Boolean {
        val raid = raidFor(player) ?: return false
        if (path.challenge !in raid.completedRooms) {
            player.mes(
                "Complete the Path of ${path.displayName} challenge before entering its guardian arena."
            )
            return false
        }
        if (raid.currentPath != path || raid.currentRoom != path.boss) {
            player.mes("Your party has not selected ${path.boss.displayName}.")
            return false
        }
        if (raid.completionPending) {
            player.mes("Use Osmumten's spirit to return to the lobby.")
            return false
        }
        val participant = raid.participant(player.uid) ?: return false
        if (participant.spectating) {
            player.mes("You are unable to enter the arena as a ghost.")
            return false
        }
        return true
    }

    fun bossEntryCutscene(player: Player, path: ToaPath): ToaResolvedEntryCutscene? {
        if (path == ToaPath.HET) return null
        if (!canEnterBoss(player, path)) return null
        val raid = raidFor(player) ?: return null
        return ToaChallengeRoomRules.resolveCutscene(raid, instances, path.boss)
    }

    fun enterBoss(player: Player, path: ToaPath): Boolean {
        if (!canEnterBoss(player, path)) return false
        val raid = raidFor(player) ?: return false
        val participant = raid.participant(player.uid) ?: return false
        val waitingAkkha =
            if (path == ToaPath.HET && !raid.encounterStarted) ensureAkkhaWaitingActor(raid)
            else null
        prepare(player)
        applyRaidRestrictions(player, raid)
        PathingEntityCommon.telejump(player, collision, fightCoord(raid, path.boss))
        participant.currentRoom = path.boss
        if (path == ToaPath.HET) {
            player.spotanim(AkkhaEntryLifecycle.PLAYER_SPOTANIM)
            player.soundSynth(AkkhaEntryLifecycle.START_SOUND)
            player.midiSong(AkkhaEntryLifecycle.MIDI)
        }
        if (!raid.encounterStarted) {
            clearEncounterNpcs(raid, setOfNotNull(waitingAkkha))
            raid.encounter.reset()
            when (path) {
                ToaPath.CRONDIS -> crondisZebak.initializeZebak(raid)
                ToaPath.HET -> {
                    val source = waitingAkkha ?: ensureAkkhaWaitingActor(raid)
                    initializeAkkha(raid, source, player)
                }
                else -> spawnBoss(raid, bossId(path))
            }
            raid.encounterStarted = true
            raid.nextMechanicAt = clock.cycle + if (path == ToaPath.HET) 1 else 8
            message(
                raid.party,
                if (path == ToaPath.HET) AkkhaEntryLifecycle.START_MESSAGE
                else "${path.boss.displayName} awakens.",
            )
        }
        return true
    }

    fun enterWardensRoom(player: Player): Boolean {
        val raid = raidFor(player) ?: return false
        if (!isToaWardensUnlocked(raid.completedPaths)) {
            val remaining = TOA_ACTIVE_PATHS.filterNot { it in raid.completedPaths }
            player.mes(
                "Defeat the remaining path ${if (remaining.size == 1) "boss" else "bosses"}: " +
                    remaining.joinToString { it.displayName } +
                    "."
            )
            return false
        }
        if (!syncWardensEntrance(raid, unlocked = true)) {
            player.mes(
                "The Wardens passage could not be opened. Please report this to an administrator."
            )
            return false
        }
        if (raid.currentRoom == ToaRoom.LOBBY) {
            if (!canLeadRaidAction(player, raid)) {
                player.mes("The party leader must enter the Wardens first.")
                return false
            }
            supplies.forfeitUnclaimedBeforeWardens(raid)
            raid.state = ToaRaidState.WARDENS
            raid.currentRoom = ToaRoom.WARDENS
            raid.currentPath = null
            raid.wardenPhase = 0
            raid.encounterStarted = false
            raid.completionPending = false
        } else if (raid.currentRoom != ToaRoom.WARDENS) {
            player.mes("You must enter the same room the party leader did.")
            return false
        }
        enterRoom(player, raid, ToaRoom.WARDENS)
        return true
    }

    fun wardensEntryCutscene(player: Player): ToaResolvedEntryCutscene? {
        val raid = raidFor(player) ?: return null
        val participant = raid.participant(player.uid) ?: return null
        if (raid.currentRoom != ToaRoom.WARDENS || participant.spectating) return null
        return ToaChallengeRoomRules.resolveCutscene(raid, instances, ToaRoom.WARDENS)
    }

    fun startWardensFight(player: Player): Boolean {
        val raid = raidFor(player) ?: return false
        if (raid.currentRoom != ToaRoom.WARDENS) return false
        val participant = raid.participant(player.uid) ?: return false
        if (participant.spectating) return false
        prepare(player)
        PathingEntityCommon.telejump(player, collision, fightCoord(raid, ToaRoom.WARDENS))
        participant.currentRoom = ToaRoom.WARDENS
        if (!raid.encounterStarted) {
            clearEncounterNpcs(raid)
            raid.encounter.reset()
            spawnWardenObeliskPhase(raid)
            activateWardenEncounter(raid)
            raid.encounterStarted = true
            raid.nextMechanicAt = clock.cycle + 1
            message(raid.party, "Challenge started: The Wardens.")
        }
        return true
    }

    fun useExit(player: Player): Boolean {
        val raid = raidFor(player) ?: return false
        if (raid.state == ToaRaidState.REWARDS) return leaveRewardRoom(player, raid)
        return returnToLobby(player, raid, completeRoom = false)
    }

    fun leave(player: Player): Boolean {
        val raid = raidFor(player) ?: return false
        return if (raid.state == ToaRaidState.REWARDS) leaveRewardRoom(player, raid)
        else leaveRaid(player, raid, true)
    }

    private fun leaveRewardRoom(player: Player, raid: ToaRaidSession): Boolean {
        when (rewards.claimToInventoryForLeave(player, raid)) {
            RaidRewardClaimResult.Claimed,
            RaidRewardClaimResult.AlreadyClaimed -> Unit
            RaidRewardClaimResult.InventoryFull,
            RaidRewardClaimResult.NothingToClaim ->
                if (!rewards.detachToStorage(player, raid)) return false
            RaidRewardClaimResult.Busy -> return false
        }
        if (allEligibleRewardsClaimed(raid)) {
            raid.state = ToaRaidState.COMPLETE
        }
        return leaveRaid(player, raid, removeFromParty = true)
    }

    fun leaveFromLogoutTab(player: Player, expectedInstanceId: InstanceId): RaidLeaveResult {
        val raid = raidFor(player) ?: return RaidLeaveResult.Unhandled
        if (raid.instance.id != expectedInstanceId) return RaidLeaveResult.Unhandled
        if (raid.state == ToaRaidState.REWARDS) {
            when (rewards.claimToInventoryForLeave(player, raid)) {
                RaidRewardClaimResult.Claimed,
                RaidRewardClaimResult.AlreadyClaimed -> Unit
                RaidRewardClaimResult.InventoryFull,
                RaidRewardClaimResult.NothingToClaim ->
                    if (!rewards.detachToStorage(player, raid)) {
                        player.mes("Your Tombs reward is not ready. You have not been moved.")
                        return RaidLeaveResult.RewardUnavailable
                    }
                RaidRewardClaimResult.Busy -> return RaidLeaveResult.Busy
            }
            if (allEligibleRewardsClaimed(raid)) {
                raid.state = ToaRaidState.COMPLETE
            }
        }
        return if (leaveRaid(player, raid, removeFromParty = true)) {
            RaidLeaveResult.Left
        } else {
            RaidLeaveResult.Failed
        }
    }

    fun hasStoredReward(player: Player): Boolean = rewards.hasStored(player)

    fun rewardRoll(player: Player): ToaRewardRoll? {
        val raid = raidFor(player)
        if (raid == null) return rewards.pending(player)
        if (raid.state != ToaRaidState.REWARDS) return null
        return rewards.pending(player, raid)
    }

    fun claimRewardToInventory(player: Player, tile: CoordGrid): Boolean {
        val raid = raidFor(player)
        val claimed =
            if (raid == null) rewards.claimStoredToInventory(player)
            else {
                if (raid.state != ToaRaidState.REWARDS) return false
                rewards.claimToInventory(player, raid)
            }
        syncRewardNiche(player)
        return claimed
    }

    fun claimRewardToBank(player: Player, bank: org.rsmod.game.inv.Inventory): Boolean {
        val raid = raidFor(player)
        val claimed =
            if (raid == null) rewards.claimStoredToBank(player, bank)
            else {
                if (raid.state != ToaRaidState.REWARDS) return false
                rewards.claimToBank(player, raid, bank)
            }
        syncRewardNiche(player)
        return claimed
    }

    fun discardReward(player: Player): Boolean {
        val raid = raidFor(player)
        val discarded =
            if (raid == null) rewards.discardStored(player)
            else {
                if (raid.state != ToaRaidState.REWARDS) return false
                rewards.discard(player, raid)
            }
        syncRewardNiche(player)
        return discarded
    }

    fun canEnterRewards(player: Player): Boolean {
        val raid = raidFor(player) ?: return false
        val ready =
            raid.state == ToaRaidState.REWARDS ||
                (raid.state == ToaRaidState.WARDENS && raid.completionPending)
        if (!ready) {
            player.mes("Defeat the Wardens before entering the treasure room.")
        }
        return ready
    }

    fun enterRewards(player: Player, preserveActiveCoroutine: Boolean = false): Boolean {
        val raid = raidFor(player) ?: return false
        if (!canEnterRewards(player)) return false
        if (raid.state == ToaRaidState.WARDENS && raid.completionPending) unlockRewards(raid)
        enterRoom(player, raid, ToaRoom.REWARD, !preserveActiveCoroutine)
        return true
    }

    fun validateAttack(player: Player, npc: Npc): NpcAttackValidateResult {
        val raid = raidFor(npc) ?: return NpcAttackValidateResult.Pass
        if (raidFor(player) !== raid)
            return NpcAttackValidateResult.Deny("That creature belongs to another raid party.")
        val participant = raid.participant(player.uid)
        if (participant?.spectating == true)
            return NpcAttackValidateResult.Deny("You cannot attack while spectating.")
        if (participant?.currentRoom != raid.currentRoom)
            return NpcAttackValidateResult.Deny("That creature is in another Tombs room.")
        if (!challengePlayerIsActive(raid, player)) {
            return NpcAttackValidateResult.Deny(
                "Pass the red barrier before interacting with the challenge."
            )
        }
        if (scabaras.owns(raid, npc)) {
            return if (scabaras.isPlayerAttackable(raid, npc)) {
                NpcAttackValidateResult.BypassSingleWayPvnRestriction
            } else {
                NpcAttackValidateResult.Deny("That cannot be attacked.")
            }
        }
        val stoneReleaseAt = raid.encounter.wardenStoneReleaseAt[player.uid]
        if (stoneReleaseAt != null && clock.cycle < stoneReleaseAt) {
            return NpcAttackValidateResult.Deny("You cannot attack while imprisoned in stone.")
        }
        if (
            raid.currentRoom == ToaRoom.TUMEKEN_WARDEN &&
                raid.npcRoles[npc] == ToaNpcRole.WARDEN_PHASE &&
                raid.encounter.wardenStage == ToaWardenStage.DEATH_SEQUENCE
        ) {
            return NpcAttackValidateResult.Deny("The Warden has already been defeated.")
        }
        if (
            raid.currentRoom == ToaRoom.AKKHA &&
                raid.npcRoles[npc] == ToaNpcRole.BOSS &&
                raid.akkha.state !in setOf(AkkhaState.NORMAL_COMBAT, AkkhaState.FINAL_STAND) &&
                !(raid.akkha.state == AkkhaState.SHADOW_TRANSITION &&
                    raid.akkha.pendingLureQuadrant != null)
        )
            return NpcAttackValidateResult.Deny("Akkha's shadows are protecting him.")
        if (raid.currentRoom == ToaRoom.BABA && raid.npcRoles[npc] == ToaNpcRole.BOSS) {
            val boulderStart =
                babaCoord(raid, BabaArenaLayout.BOULDER_START_LOCAL_X, BabaArenaLayout.BOSS_LOCAL_Z)
            val rollingExit =
                raid.baba.state == BabaState.ROLLING_STONES &&
                    BabaRollingExit.inExitZone(player.coords, boulderStart)
            if (raid.baba.state != BabaState.NORMAL_COMBAT && !rollingExit) {
                return NpcAttackValidateResult.Deny("Ba-Ba is protected by the rolling stones.")
            }
        }
        if (
            raid.currentRoom == ToaRoom.KEPHRI &&
                raid.npcRoles[npc] == ToaNpcRole.BOSS &&
                (raid.encounter.kephriRecovering || raid.encounter.kephriDying)
        )
            return NpcAttackValidateResult.Deny("Kephri cannot be attacked right now.")
        if (raid.npcRoles[npc] !in ATTACKABLE_ROLES)
            return NpcAttackValidateResult.Deny("That cannot be attacked.")
        if (raid.currentRoom == ToaRoom.AKKHA) {
            val status = raid.akkha.statuses[player.uid]
            if (
                status != null &&
                    status.freezeExpiresAt > clock.cycle &&
                    player.actionDelay <= clock.cycle
            ) {
                if (!status.freezeAttackDelayApplied) {
                    player.actionDelay = clock.cycle + AkkhaTuning.FREEZE_ATTACK_DELAY.value
                    status.freezeAttackDelayApplied = true
                } else {
                    status.freezeAttackDelayApplied = false
                }
            }
        }
        return NpcAttackValidateResult.BypassSingleWayPvnRestriction
    }

    fun attackTargetValid(player: Player, npc: Npc): Boolean =
        raidFor(npc)?.let { raid ->
            val participant = raid.participant(player.uid)
            raidFor(player) === raid &&
                participant?.spectating != true &&
                participant?.currentRoom == raid.currentRoom &&
                challengePlayerIsActive(raid, player) &&
                (if (scabaras.owns(raid, npc)) {
                    scabaras.isCombatNpc(raid, npc)
                } else {
                    raid.npcRoles[npc] in ATTACKABLE_ROLES
                })
        } == true

    fun isAkkhaBoss(npc: Npc): Boolean =
        raidFor(npc)?.let { raid ->
            raid.currentRoom == ToaRoom.AKKHA && raid.npcRoles[npc] == ToaNpcRole.BOSS
        } == true

    fun suppressAkkhaNormalAttacks(npc: Npc): Boolean =
        raidFor(npc)?.let { raid ->
            raid.currentRoom == ToaRoom.AKKHA &&
                raid.akkha.state !in setOf(AkkhaState.NORMAL_COMBAT, AkkhaState.SHADOW_TRANSITION)
        } == true

    fun isBabaBoss(npc: Npc): Boolean =
        raidFor(npc)?.let { raid ->
            raid.currentRoom == ToaRoom.BABA && raid.npcRoles[npc] == ToaNpcRole.BOSS
        } == true

    fun usesScriptedWardenPhaseTwoCombat(npc: Npc): Boolean =
        raidFor(npc)?.let { raid ->
            raid.currentRoom == ToaRoom.WARDENS &&
                raid.encounter.wardenStage == ToaWardenStage.PHASE_TWO &&
                raid.npcRoles[npc] == ToaNpcRole.WARDEN_PHASE
        } == true

    fun isBabaBaboon(npc: Npc): Boolean =
        raidFor(npc)?.let { raid ->
            raid.currentRoom == ToaRoom.BABA && npc.id == BabaAssets.BABOON
        } == true

    fun suppressBabaNormalAttacks(npc: Npc): Boolean =
        raidFor(npc)?.let { raid ->
            raid.currentRoom == ToaRoom.BABA && raid.baba.state != BabaState.NORMAL_COMBAT
        } == true

    fun babaPrayerReduction(npc: Npc): Int =
        prayerReduction(npc, BabaTuning.MELEE_PRAYER_REDUCTION.value)

    fun prayerReduction(npc: Npc, basePercent: Int): Int =
        raidFor(npc)?.let { raid ->
            ToaRules.prayerReductionPercent(basePercent, raid.startSnapshot.invocations)
        } ?: basePercent

    fun onPrayerMitigatedImpact(npc: Npc, impact: HitImpact) {
        val raid = raidFor(npc) ?: return
        ToaPrayerInvocationRules.standardCombatImpact(impact, raid.startSnapshot.invocations)
    }

    fun onKephriSpittingAttack(npc: Npc, target: Player) {
        val raid = raidFor(npc) ?: return
        if (raid.currentRoom != ToaRoom.KEPHRI || raid.npcRoles[npc] != ToaNpcRole.ADD) return
        PlayerPoison.tryPoison(
            target,
            initialDamage = KephriScarabRanger.POISON_DAMAGE,
            source = npc,
        )
    }

    private fun prayerImpactHandler(
        raid: ToaRaidSession,
        player: Player,
        type: HitType,
        existing: HitImpactHandler? = null,
    ): HitImpactHandler? =
        ToaPrayerInvocationRules.compose(
            existing,
            ToaPrayerInvocationRules.deadlyImpactHandler(
                player,
                type,
                raid.startSnapshot.invocations,
            ),
        )

    fun performBabaSpecialIfDue(source: Npc): Int? {
        val raid = raidFor(source) ?: return null
        val state = raid.baba
        if (!isBabaBoss(source) || state.state != BabaState.NORMAL_COMBAT) return null
        if (state.pendingRollingThresholds.isNotEmpty()) {
            beginBabaRollingPhase(raid, source)
            return 4
        }
        if (state.attackCounter < BabaTuning.SPECIAL_AFTER_AUTOS.value) return null
        val special = state.nextSpecial(allowRockfall = state.rubbleDue(clock.cycle))
        return startBabaSpecial(raid, source, special)
    }

    fun recordBabaStandardAttack(source: Npc, attacked: Player) {
        val raid = raidFor(source) ?: return
        if (!isBabaBoss(source) || raid.baba.state != BabaState.NORMAL_COMBAT) return
        val state = raid.baba
        state.previousTarget = state.target
        state.target = attacked.uid
        val due = state.recordAuto()
        val next = selectBabaTarget(raid, postBoulder = state.completedRollingPhases > 0)
        if (next != null && next.uid != attacked.uid) source.apPlayer2(next, interactions)
        traceBaba(
            raid,
            source,
            "auto_emitted",
            "target" to attacked.uid,
            "nextTarget" to next?.uid,
            "autoAttackCount" to state.attackCounter,
            "specialDue" to due,
        )
    }

    fun performAkkhaSpecialIfDue(source: Npc, target: Player): Int? {
        val raid = raidFor(source) ?: return null
        val state = raid.akkha
        if (
            raid.currentRoom != ToaRoom.AKKHA ||
                raid.npcRoles[source] != ToaNpcRole.BOSS ||
                state.state !in setOf(AkkhaState.NORMAL_COMBAT, AkkhaState.SHADOW_TRANSITION)
        )
            return null
        if (
            state.attackCounter <
                AkkhaInvocationRules.attacksBeforeSpecial(
                    ToaInvocation.STAY_VIGILANT in raid.startSnapshot.invocations
                )
        )
            return null
        val specials =
            AkkhaSpecialSchedule.select(
                raid.startSnapshot.partySize,
                ToaInvocation.DOUBLE_TROUBLE in raid.startSnapshot.invocations,
                state.lastSpecial,
                state::nextInt,
            )
        var duration = 1
        specials.forEach {
            duration = maxOf(duration, performAkkhaSpecial(raid, source, target, it))
        }
        val previousStyle = state.style
        state.startSpecial(specials, clock.cycle + duration)
        if (state.state != AkkhaState.SPECIAL_ATTACK) {
            source.transmog(
                requireNotNull(ServerCacheManager.getNpc(state.style.npcId)),
                Int.MAX_VALUE,
            )
            source.apRangeOverride = akkhaAttackRange(state.style)
            state.target = null
            if (state.style != previousStyle) {
                akkhaLastStyleChangeAt[raid.instance.id] = clock.cycle
                playAkkhaStyleChangeSound(source)
            }
            rearmAkkhaAggression(raid, source, force = true)
        }
        traceAkkha(
            raid,
            source,
            "special_start",
            "special" to specials.joinToString("+"),
            "endsAt" to state.specialEndsAt,
        )
        return duration
    }

    fun recordAkkhaStandardAttack(source: Npc, target: Player, style: ToaCombatStyle) {
        val raid = raidFor(source) ?: return
        if (!isAkkhaBoss(source)) return
        val state = raid.akkha
        if (
            state.style.name != style.name ||
                state.state !in setOf(AkkhaState.NORMAL_COMBAT, AkkhaState.SHADOW_TRANSITION)
        )
            return
        val previousTarget = state.target
        state.target = target.uid
        if (previousTarget != target.uid)
            traceAkkha(
                raid,
                source,
                "target_change",
                "previousTarget" to previousTarget,
                "target" to target.uid,
            )
        if (state.state in setOf(AkkhaState.NORMAL_COMBAT, AkkhaState.SHADOW_TRANSITION))
            state.recordEmittedAuto(
                clock.cycle,
                ToaInvocation.STAY_VIGILANT in raid.startSnapshot.invocations,
            )
        val extras =
            when (style) {
                ToaCombatStyle.MELEE ->
                    activePlayers(raid).filter {
                        it !== target &&
                            it.coords.chebyshevDistance(target.coords) <= 1 &&
                            isAdjacentToAkkha(source, it.coords)
                    }
                ToaCombatStyle.RANGED,
                ToaCombatStyle.MAGIC -> activePlayers(raid).filter { it !== target }
            }
        val type =
            when (style) {
                ToaCombatStyle.MELEE -> HitType.Melee
                ToaCombatStyle.RANGED -> HitType.Ranged
                ToaCombatStyle.MAGIC -> HitType.Magic
            }
        val reduction =
            if (style == ToaCombatStyle.MELEE) AkkhaTuning.MELEE_PRAYER_REDUCTION.value
            else AkkhaTuning.NORMAL_PRAYER_REDUCTION.value
        val maximum = scaledDamage(raid, AkkhaTuning.BASE_MAX_HIT.value)
        extras.forEach { player ->
            state.style.projectile?.let { sendProjectile(source, player, it) }
            val magicImpact =
                if (style == ToaCombatStyle.MAGIC) {
                    HitImpactHandler { impact ->
                        (impact.target as? Player)?.spotanim(AkkhaAssets.MAGIC_IMPACT)
                    }
                } else {
                    null
                }
            queueAkkhaCombatHit(
                player,
                source,
                state.nextInt(maximum + 1),
                akkhaImpactDelay(style),
                type,
                reduction,
                magicImpact,
            )
        }
        if (
            ToaInvocation.KEEP_BACK in raid.startSnapshot.invocations &&
                style != ToaCombatStyle.MELEE
        ) {
            activePlayers(raid)
                .filter { isAdjacentToAkkha(source, it.coords) }
                .forEach { player ->
                    queueAkkhaCombatHit(
                        player,
                        source,
                        state.nextInt(scaledDamage(raid, AkkhaTuning.KEEP_BACK_MAX_HIT.value) + 1),
                        1,
                        HitType.Melee,
                        AkkhaTuning.MELEE_PRAYER_REDUCTION.value,
                    )
                }
        }
        traceAkkha(
            raid,
            source,
            "auto_emitted",
            "target" to target.uid,
            "style" to style,
            "autoAttackCount" to state.attackCounter,
            "projectileId" to state.style.projectile,
        )
    }

    private fun attachRewardPointImpact(
        raid: ToaRaidSession,
        player: Player,
        npc: Npc,
        role: ToaNpcRole,
        hit: HitBuilder,
    ) {
        val room = rewardPointRoom(raid.currentRoom) ?: return
        val target = rewardPointTarget(raid, npc, role)
        if (!target.isEligible) return
        val accountHash = player.accountHash
        val previousImpact = hit.impactHandler
        hit.impactHandler = HitImpactHandler { impact ->
            previousImpact?.onImpact(impact)
            if (
                ToaRewardPointImpactGuard.canAward(
                    raidState = raid.state,
                    capturedRoom = room,
                    currentRoom = rewardPointRoom(raid.currentRoom),
                    raidOwnsTarget = raid.owns(npc),
                    scabarasOwnsTarget = scabaras.owns(raid, npc),
                    sameRaid = raidFor(player) === raid,
                    sameTarget = impact.target === npc,
                    damage = impact.damage,
                )
            ) {
                raid.awardRewardDamage(accountHash, room, target, impact.damage)
            }
        }
    }

    private fun rewardPointTarget(
        raid: ToaRaidSession,
        npc: Npc,
        role: ToaNpcRole,
    ): ToaRewardPointTarget {
        if (
            role in
                setOf(
                    ToaNpcRole.HAZARD,
                    ToaNpcRole.CORE,
                    ToaNpcRole.BOULDER,
                    ToaNpcRole.COMPLETION_SPIRIT,
                    ToaNpcRole.HELPFUL_SPIRIT,
                )
        ) {
            return ToaRewardPointTargets.ineligible()
        }
        val context =
            when (raid.currentRoom) {
                ToaRoom.WARDENS ->
                    ToaRewardPointNpcContext(
                        wardenPhase =
                            if (raid.encounter.wardenStage == ToaWardenStage.PHASE_TWO) {
                                ToaRewardPointWardenPhase.PHASE_TWO
                            } else {
                                ToaRewardPointWardenPhase.NONE
                            },
                        phaseTwoDownsCompleted = raid.encounter.wardenCoreDowns,
                    )
                ToaRoom.TUMEKEN_WARDEN ->
                    ToaRewardPointNpcContext(wardenPhase = ToaRewardPointWardenPhase.PHASE_THREE)
                else ->
                    ToaRewardPointNpcContext(
                        ordinaryDamageableTarget =
                            role in
                                setOf(
                                    ToaNpcRole.BOSS,
                                    ToaNpcRole.CHALLENGE,
                                    ToaNpcRole.ADD,
                                    ToaNpcRole.OBELISK,
                                    ToaNpcRole.WARDEN_PHASE,
                                )
                    )
            }
        return ToaRewardPointTargets.fromNpc(npc.id, context)
    }

    private fun rewardPointRoom(room: ToaRoom): ToaRewardPointRoom? =
        when (room) {
            ToaRoom.SCABARAS -> ToaRewardPointRoom.SCABARAS
            ToaRoom.KEPHRI -> ToaRewardPointRoom.KEPHRI
            ToaRoom.APMEKEN -> ToaRewardPointRoom.APMEKEN
            ToaRoom.BABA -> ToaRewardPointRoom.BABA
            ToaRoom.CRONDIS -> ToaRewardPointRoom.CRONDIS
            ToaRoom.ZEBAK -> ToaRewardPointRoom.ZEBAK
            ToaRoom.HET -> ToaRewardPointRoom.HET
            ToaRoom.AKKHA -> ToaRewardPointRoom.AKKHA
            ToaRoom.WARDENS,
            ToaRoom.TUMEKEN_WARDEN -> ToaRewardPointRoom.WARDENS
            ToaRoom.LOBBY,
            ToaRoom.REWARD -> null
        }

    fun modifyNpcHit(player: Player, npc: Npc, hit: HitBuilder) {
        val raid = raidFor(npc)
        if (raid == null || raidFor(player) !== raid) {
            hit.damage = 0
            return
        }
        if (scabaras.owns(raid, npc)) {
            if (scabaras.isCombatNpc(raid, npc)) {
                attachRewardPointImpact(raid, player, npc, ToaNpcRole.CHALLENGE, hit)
                return
            }
            val result = scabaras.onNpcHit(raid, player, npc)
            hit.damage = 0
            if (result.challengeCompleted) completeScabarasChallenge(raid)
            return
        }
        val role =
            raid.npcRoles[npc]
                ?: run {
                    hit.damage = 0
                    return
                }
        attachRewardPointImpact(raid, player, npc, role, hit)
        if (crondisZebak.modifyNpcHit(player, npc, hit, raid)) return
        if (raid.currentRoom == ToaRoom.APMEKEN && role == ToaNpcRole.CHALLENGE && hit.damage > 0) {
            if (ApmekenCombatRules.receivesGuaranteedMaximum(npc.id, hit.type)) {
                hit.damage =
                    player.vars["varp.com_maxhit"].coerceAtLeast(1).coerceAtMost(npc.hitpoints)
            }
            val key = apmekenNpcKeys[npc]
            if (key != null) {
                val previousImpact = hit.impactHandler
                hit.impactHandler = HitImpactHandler { impact ->
                    previousImpact?.onImpact(impact)
                    val remaining = npc.hitpoints.coerceAtLeast(0)
                    val events =
                        apmeken.onNpcHit(
                            raid.instance.id,
                            ApmekenNpcHitContext(
                                npcKey = key,
                                tile = apmekenLocalTile(raid, npc.coords),
                                remainingHitpoints = remaining,
                                poisoned = npcToxins.isPoisoned(npc),
                                venomed = npcToxins.isEnvenomed(npc),
                            ),
                            clock.cycle,
                        )
                    applyApmekenEvents(raid, events)
                }
            }
            return
        }
        val state = raid.encounter
        when {
            raid.currentRoom == ToaRoom.KEPHRI && role == ToaNpcRole.BOSS -> {
                if (state.kephriRecovering) {
                    hit.damage = 0
                    return
                }
                if (!state.kephriActivated) {
                    state.kephriActivated = true
                    raid.nextMechanicAt = clock.cycle + KephriEncounter.FIRST_ATTACK_DELAY
                    forEachOnline(raid) { it.midiSong(KephriEncounter.COMBAT_MIDI) }
                }
                if (
                    state.phase < KephriEncounter.FINAL_PHASE &&
                        hit.damage > 0 &&
                        hit.damage >= npc.hitpoints
                ) {
                    hit.damage = (npc.hitpoints - 1).coerceAtLeast(0)
                    val previousImpact = hit.impactHandler
                    hit.impactHandler = HitImpactHandler { impact ->
                        previousImpact?.onImpact(impact)
                        beginKephriRecovery(raid, npc)
                    }
                }
                return
            }
            raid.currentRoom == ToaRoom.KEPHRI &&
                role == ToaNpcRole.ADD &&
                npc.id in setOf(KephriSwarms.NPC, KephriEggs.EXPLOSIVE, KephriEggs.AGILE) -> {
                hit.damage =
                    player.vars["varp.com_maxhit"].coerceAtLeast(1).coerceAtMost(npc.hitpoints)
                return
            }
            raid.currentRoom == ToaRoom.BABA && role == ToaNpcRole.BOSS -> {
                val baba = raid.baba

                if (baba.state == BabaState.ROLLING_STONES) {
                    val boulderStart =
                        babaCoord(
                            raid,
                            BabaArenaLayout.BOULDER_START_LOCAL_X,
                            BabaArenaLayout.BOSS_LOCAL_Z,
                        )
                    if (BabaRollingExit.canFinish(player.coords, boulderStart, hit.type)) {
                        finishBabaRollingPhase(raid, npc, "melee_reached_top")
                    } else {
                        hit.damage = 0
                        return
                    }
                } else if (baba.state != BabaState.NORMAL_COMBAT) {
                    hit.damage = 0
                    return
                }
                if (hit.damage > 0) {
                    val after = (npc.hitpoints - hit.damage).coerceAtLeast(0)
                    val crossed =
                        baba.queueCrossedThresholds(
                            npc.hitpoints,
                            after,
                            npc.baseHitpointsLvl.coerceAtLeast(1),
                        )
                    if (crossed.isNotEmpty()) {
                        traceBaba(
                            raid,
                            npc,
                            "threshold_queued",
                            "thresholds" to crossed.joinToString(","),
                            "bossHp" to after,
                        )
                    }
                    if (hit.damage >= npc.hitpoints && baba.hasUnresolvedRollingThresholds()) {
                        hit.damage = (npc.hitpoints - 1).coerceAtLeast(0)
                    }
                }
                return
            }
            raid.currentRoom == ToaRoom.BABA &&
                role == ToaNpcRole.ADD &&
                npc.id == BabaAssets.BABOON -> {
                hit.damage =
                    player.vars["varp.com_maxhit"].coerceAtLeast(1).coerceAtMost(npc.hitpoints)
                return
            }
            raid.currentRoom == ToaRoom.BABA && role == ToaNpcRole.BOULDER -> {
                val runtime = raid.baba.rollingBoulders[npc.hashCode()]
                if (npc.id == BabaAssets.WEAK_BOULDER) {
                    if (!babaWeakBoulderCanReceive(hit.type)) {
                        hit.damage = 0
                        player.mes("The cracked boulder can only be broken with Ranged.")
                    } else {
                        hit.damage =
                            runtime?.damageForValidRangedHit(
                                player.vars["varp.com_maxhit"],
                                npc.hitpoints,
                            ) ?: 0
                    }
                }
                return
            }
            raid.currentRoom == ToaRoom.WARDENS && role == ToaNpcRole.OBELISK -> {
                if (state.wardenStage != ToaWardenStage.OBELISK) {
                    hit.damage = 0
                    return
                }
                if (!state.wardenObeliskActivated) activateWardenEncounter(raid)
                return
            }
            raid.currentRoom == ToaRoom.WARDENS && role == ToaNpcRole.WARDEN_PHASE -> {
                if (state.wardenStage != ToaWardenStage.PHASE_TWO) {
                    hit.damage = 0
                    return
                }
                val required =
                    when (state.wardenPhaseTwoGod) {
                        WardenGod.ELIDINIS -> ElidinisWarden.requiredStyle(npc.id)
                        WardenGod.TUMEKEN -> TumekenWarden.requiredStyle(npc.id)
                    }
                if (required != null && hit.type != required) {
                    hit.damage = 0
                    player.mes("The Warden is protecting against the other two combat styles.")
                } else if (hit.damage > 0) {
                    hit.damage =
                        WardenCores.shieldDamage(state.wardenPhaseTwoShieldCharge, hit.damage)
                            .coerceAtMost(npc.hitpoints)
                    val previousImpact = hit.impactHandler
                    hit.impactHandler = HitImpactHandler { impact ->
                        previousImpact?.onImpact(impact)
                        applyWardenShieldImpact(raid, player, npc, impact.damage)
                    }
                }
                return
            }
            raid.currentRoom == ToaRoom.WARDENS && role == ToaNpcRole.CORE -> {
                if (hit.type == HitType.Melee) {
                    hit.damage =
                        player.vars["varp.com_maxhit"].coerceAtLeast(1).coerceAtMost(npc.hitpoints)
                }
                hit.damage =
                    hit.damage
                        .coerceAtMost(WardenCores.coreHitpoints(state.wardenPhaseTwoRemaining))
                        .coerceAtMost(npc.hitpoints)
                if (hit.damage > 0) {
                    val previousImpact = hit.impactHandler
                    hit.impactHandler = HitImpactHandler { impact ->
                        previousImpact?.onImpact(impact)
                        applyWardenCoreImpact(raid, player, npc, impact.damage)
                    }
                }
                return
            }
            raid.currentRoom == ToaRoom.TUMEKEN_WARDEN &&
                role == ToaNpcRole.WARDEN_PHASE &&
                state.wardenSkullsActive -> {
                hit.damage = 0
                return
            }
            raid.currentRoom == ToaRoom.TUMEKEN_WARDEN &&
                role == ToaNpcRole.WARDEN_PHASE &&
                state.wardenStage == ToaWardenStage.DEATH_SEQUENCE -> {
                hit.damage = 0
                return
            }
            raid.currentRoom == ToaRoom.TUMEKEN_WARDEN &&
                role == ToaNpcRole.WARDEN_PHASE &&
                hit.damage > 0 -> {
                hit.damage =
                    TumekenWarden.clampDamageToNextTransition(
                        currentHitpoints = npc.hitpoints,
                        maximumHitpoints = npc.baseHitpointsLvl,
                        requestedDamage = hit.damage,
                        siphonsSent = state.wardenSkullsSent,
                        enraged = state.wardenEnraged,
                    )
                if (WardenDeathSequence.shouldBegin(state.wardenStage, npc.hitpoints, hit.damage)) {
                    val previousImpact = hit.impactHandler
                    hit.impactHandler = HitImpactHandler { impact ->
                        previousImpact?.onImpact(impact)
                        if (impact.damage > 0 && npc.hitpoints <= 0) {
                            beginWardenDeathSequence(raid, npc)
                        }
                    }
                }
                return
            }
            role == ToaNpcRole.SKULL -> {
                if (!WardenEnergySiphons.releasesAttackDelay(hit.type)) {
                    hit.damage = 0
                    player.mes("Energy Siphons can only be disabled with melee attacks.")
                } else {
                    hit.damage = 1.coerceAtMost(npc.hitpoints)
                    player.actionDelay = player.currentMapClock
                }
                return
            }
        }
        if (
            raid.currentRoom == ToaRoom.AKKHA &&
                role == ToaNpcRole.ADD &&
                npc.id in ToaNpcs.akkhaShadows
        ) {
            val center = roomCoord(raid, ToaRoom.AKKHA, true)
            val quadrant = akkhaQuadrant(center, npc.coords)
            if (!raid.akkha.canDisruptShadow(quadrant)) {
                hit.damage = 0
                player.mes(
                    if (quadrant in raid.akkha.defeatedShadows)
                        "That shadow has already been disrupted and is now immune."
                    else "Only one unused shadow can be disrupted at this threshold."
                )
            } else if (
                hit.damage > 0 &&
                    hit.damage >= npc.hitpoints &&
                    !raid.akkha.recordShadowDeath(quadrant)
            ) {
                // Reserve the one legal disruption before queued lethal hits can overlap.
                hit.damage = 0
            }
            return
        }

        if (raid.currentRoom != ToaRoom.AKKHA || role != ToaNpcRole.BOSS) return
        val akkha = raid.akkha
        val lure = akkha.pendingLureQuadrant
        if (lure != null) {
            if (!akkhaBossInsideQuadrant(raid, npc, lure)) {
                hit.damage = 0
                player.mes("Akkha must be lured into the disrupted shadow's quadrant.")
                return
            }
            if (akkha.confirmLure(lure)) {
                akkha.target = null
                rearmAkkhaAggression(raid, npc, force = true)
                traceAkkha(raid, npc, "shadow_lure_complete", "quadrant" to lure)
            }
        }
        if (akkha.state == AkkhaState.FINAL_STAND && akkha.pendingFinalStandSwap != null) {
            hit.damage = 0
            return
        }
        if (!akkha.canReceiveDamage(hit.type)) {
            hit.damage = 0
            warnAkkhaWrongStyle(player, raid)
            return
        }
        if (hit.damage <= 0) return
        if (akkha.state == AkkhaState.NORMAL_COMBAT) {
            val maximum = npc.baseHitpointsLvl.coerceAtLeast(1)
            hit.damage =
                akkha.capDamageAtNextShadowThreshold(
                    currentHitpoints = npc.hitpoints,
                    proposedDamage = hit.damage,
                    maximum = maximum,
                )
            if (hit.damage <= 0) return
            val current = (npc.hitpoints - hit.damage).coerceAtLeast(0)
            val crossed = akkha.queueCrossedThresholds(npc.hitpoints, current, maximum)
            if (crossed.isNotEmpty())
                traceAkkha(
                    raid,
                    npc,
                    "threshold_queued",
                    "thresholds" to crossed.joinToString(","),
                    "bossHp" to current,
                )
            if (hit.damage >= npc.hitpoints) {
                if (akkha.hasUnresolvedShadowThresholds()) {
                    hit.damage = (npc.hitpoints - 1).coerceAtLeast(0)
                    traceAkkha(
                        raid,
                        npc,
                        "lethal_threshold_clamped",
                        "queuedThresholds" to akkha.pendingThresholds.joinToString(","),
                        "bossHp" to npc.hitpoints,
                    )
                } else {
                    hit.damage = 0
                    beginAkkhaFinalStand(raid, npc)
                }
                return
            }
        } else if (akkha.state == AkkhaState.FINAL_STAND) {
            if (hit.damage >= npc.hitpoints) {
                prepareAkkhaFinalDeath(raid, npc)
            } else if (akkha.recordFinalStandHit(raid.startSnapshot.partySize)) {
                startAkkhaFinalStandSwap(raid)
            }
        }
    }

    fun combatProfile(npc: Npc): ToaCombatProfile? {
        val raid = raidFor(npc) ?: return null
        if (
            raid.currentRoom in ToaChallengeRoomRules.rooms &&
                raid.challenge?.stage != ToaChallengeStage.ACTIVE
        )
            return null
        if (scabaras.owns(raid, npc)) {
            if (!scabaras.isCombatNpc(raid, npc)) return null
            val pathLevel = raid.pathLevels[ToaPath.SCABARAS] ?: 0
            val base = ToaCombatProfile(ToaCombatStyle.RANGED, maxHit = 6, rate = 5)
            return base.copy(
                maxHit = ToaRules.maxHit(base.maxHit, raid.raidLevel, pathLevel),
                rate = ToaInvocationEffects.attackRate(base.rate, raid),
            )
        }
        val role = raid.npcRoles[npc] ?: return null
        if (role == ToaNpcRole.WARDEN_PHASE) return null
        if (raid.currentRoom == ToaRoom.ZEBAK && npc.id in ZebakAssets.npcIds) return null
        if (
            role in
                setOf(
                    ToaNpcRole.HAZARD,
                    ToaNpcRole.CORE,
                    ToaNpcRole.SKULL,
                    ToaNpcRole.BOULDER,
                    ToaNpcRole.COMPLETION_SPIRIT,
                    ToaNpcRole.HELPFUL_SPIRIT,
                )
        ) {
            return null
        }
        if (
            raid.currentRoom == ToaRoom.AKKHA &&
                role == ToaNpcRole.ADD &&
                npc.id in ToaNpcs.akkhaShadows
        ) {
            return null
        }
        val pathLevel = raid.currentPath?.let { raid.pathLevels[it] } ?: 0
        val base =
            when {
                raid.currentRoom == ToaRoom.BABA && role == ToaNpcRole.BOSS ->
                    ToaCombatProfile(
                        ToaCombatStyle.MELEE,
                        BabaTuning.BASE_MELEE_MAX_HIT.value,
                        BabaTuning.ATTACK_INTERVAL.value,
                    )
                raid.currentRoom == ToaRoom.BABA && npc.id == BabaAssets.BABOON ->
                    ToaCombatProfile(ToaCombatStyle.RANGED, BabaTuning.BABOON_MAX_HIT.value, 4)
                npc.id == ToaNpcs.ZEBAK -> ToaCombatProfile(ToaCombatStyle.RANGED, 30, 5)
                npc.id == ToaNpcs.KEPHRI -> ToaCombatProfile(ToaCombatStyle.MAGIC, 24, 6)
                raid.currentRoom == ToaRoom.AKKHA && role == ToaNpcRole.BOSS ->
                    when (raid.akkha.style) {
                        AkkhaStyle.MELEE ->
                            ToaCombatProfile(
                                ToaCombatStyle.MELEE,
                                AkkhaTuning.BASE_MAX_HIT.value,
                                AkkhaTuning.BASE_ATTACK_INTERVAL.value,
                            )
                        AkkhaStyle.RANGED ->
                            ToaCombatProfile(
                                ToaCombatStyle.RANGED,
                                AkkhaTuning.BASE_MAX_HIT.value,
                                AkkhaTuning.BASE_ATTACK_INTERVAL.value,
                            )
                        AkkhaStyle.MAGIC ->
                            ToaCombatProfile(
                                ToaCombatStyle.MAGIC,
                                AkkhaTuning.BASE_MAX_HIT.value,
                                AkkhaTuning.BASE_ATTACK_INTERVAL.value,
                            )
                    }
                npc.id in setOf(ElidinisWarden.MAGIC_FORM, TumekenWarden.MAGIC_FORM) ->
                    ToaCombatProfile(ToaCombatStyle.MAGIC, 34, 7)
                npc.id in setOf(ElidinisWarden.RANGED_FORM, TumekenWarden.RANGED_FORM) ->
                    ToaCombatProfile(ToaCombatStyle.RANGED, 34, 7)
                npc.id in setOf(ElidinisWarden.FINAL_FORM, TumekenWarden.FINAL_FORM) ->
                    ToaCombatProfile(ToaCombatStyle.MAGIC, 38, 7)
                npc.id == KephriScarabMelee.NPC ->
                    ToaCombatProfile(
                        ToaCombatStyle.MELEE,
                        KephriScarabMelee.MAX_HIT,
                        KephriScarabMelee.ATTACK_INTERVAL,
                    )
                npc.id == KephriScarabRanger.NPC ->
                    ToaCombatProfile(
                        ToaCombatStyle.RANGED,
                        KephriScarabRanger.MAX_HIT,
                        KephriScarabRanger.ATTACK_INTERVAL,
                    )
                npc.id == KephriScarabAgile.NPC ->
                    ToaCombatProfile(
                        ToaCombatStyle.RANGED,
                        KephriScarabAgile.MAX_HIT,
                        KephriScarabAgile.ATTACK_INTERVAL,
                    )
                npc.id == KephriScarabMager.NPC -> return null
                npc.id in ApmekenRevision240Assets.npcIds -> {
                    val definition = ApmekenCombatRules.definition(npc.id) ?: return null
                    if (definition.scripted) return null
                    ToaCombatProfile(definition.style, definition.maxHit, definition.attackRate)
                }
                else -> ToaCombatProfile(ToaCombatStyle.MELEE, 15, 5)
            }
        return base.copy(
            maxHit = ToaRules.maxHit(base.maxHit, raid.raidLevel, pathLevel),
            rate =
                when {
                    raid.currentRoom in setOf(ToaRoom.WARDENS, ToaRoom.TUMEKEN_WARDEN) ->
                        raid.encounter.wardensRuleset.phaseTwoWardenAttackInterval(
                            raid.encounter.wardenPhaseTwoGod
                        )
                    raid.currentRoom == ToaRoom.AKKHA && role == ToaNpcRole.BOSS -> base.rate
                    else -> ToaInvocationEffects.attackRate(base.rate, raid)
                },
        )
    }

    fun handleNpcKill(hero: Player, npc: Npc) {
        val raid = raidFor(npc) ?: return
        if (scabaras.owns(raid, npc)) {
            val result = scabaras.onNpcKill(raid, hero, npc)
            if (result.challengeCompleted) completeScabarasChallenge(raid)
            return
        }
        val apmekenKey = apmekenNpcKeys.remove(npc)
        if (apmekenKey != null) apmekenNpcs[raid.instance.id]?.remove(apmekenKey)
        val role = raid.npcRoles[npc] ?: return
        if (
            raid.currentRoom == ToaRoom.KEPHRI &&
                role == ToaNpcRole.BOSS &&
                raid.encounter.phase >= KephriEncounter.FINAL_PHASE &&
                !raid.encounter.kephriDying
        ) {
            val state = raid.encounter
            state.kephriDying = true
            state.kephriDeathHero = hero.uid
            state.kephriCompletesAt = clock.cycle + KephriEncounter.DEATH_COMPLETION_TICKS
            state.kephriBombTargets.clear()
            state.kephriBombs.clear()
            state.kephriPendingDungStrikes.clear()
            state.kephriEggBatches.clear()
            npc.abortRoute()
            npc.clearInteraction()
            npc.mode = null
            npc.ignoreCombatInteractions = true
            npc.anim(KephriEncounter.DEATH_ANIMATION)
            npc.transmog(
                requireNotNull(ServerCacheManager.getNpc(KephriEncounter.DEAD)),
                Int.MAX_VALUE,
            )
            raid.nextMechanicAt = Int.MAX_VALUE
            return
        }
        // Zebak's living body is retained for the four-tick 9634 -> 11733 death transform.
        // Other actors follow the normal eager bookkeeping removal path.
        if (
            raid.currentRoom == ToaRoom.ZEBAK &&
                role == ToaNpcRole.BOSS &&
                npc === raid.zebak.body &&
                crondisZebak.onZebakNpcKilled(hero, npc, raid, role)
        ) {
            return
        }
        raid.npcRoles.remove(npc)
        raid.spawnedNpcs.remove(npc)
        if (crondisZebak.onZebakNpcKilled(hero, npc, raid, role)) return
        if (
            raid.currentRoom == ToaRoom.CRONDIS && crondisZebak.onCrondisCrocodileKilled(raid, npc)
        ) {
            return
        }
        when (role) {
            ToaNpcRole.CHALLENGE -> {
                if (
                    raid.currentRoom == ToaRoom.APMEKEN &&
                        apmekenKey != null &&
                        apmeken.contains(raid.instance.id)
                ) {
                    applyApmekenEvents(
                        raid,
                        apmeken.onNpcKilled(raid.instance.id, apmekenKey, clock.cycle),
                    )
                }
            }
            ToaNpcRole.BOSS -> {
                if (raid.currentRoom == ToaRoom.AKKHA) raid.akkha.state = AkkhaState.DEFEATED
                if (raid.currentRoom == ToaRoom.BABA) {
                    raid.baba.state = BabaState.DEFEATED
                    cleanupBaba(raid, resetState = false)
                }
                completePathBoss(raid, hero)
            }
            ToaNpcRole.OBELISK -> {
                if (
                    raid.currentRoom == ToaRoom.WARDENS &&
                        raid.encounter.wardenStage == ToaWardenStage.OBELISK
                ) {
                    beginWardenPhaseOneTransition(raid, hero)
                }
            }
            ToaNpcRole.WARDEN_PHASE -> advanceWarden(raid, hero, npc)
            ToaNpcRole.CORE -> advanceWardenCore(raid, hero, npc)
            ToaNpcRole.SKULL -> {
                val disabled =
                    spawnNpc(raid, WardenEnergySiphons.DISABLED, npc.coords, ToaNpcRole.HAZARD)
                lockWardenActor(disabled)
                // OSRS keeps the disabled actor in place. Every stored Siphon returns together
                // only when the set resolves; sending one immediately desynchronizes the mechanic.
                finishWardenSiphonsIfClear(raid)
            }
            ToaNpcRole.BOULDER -> handleBabaBoulderKill(raid, npc)
            ToaNpcRole.ADD -> {
                if (raid.currentRoom == ToaRoom.KEPHRI) {
                    raid.encounter.kephriEggs.remove(npc.hashCode())
                    raid.encounter.kephriSoldiers.remove(npc.hashCode())
                    raid.encounter.kephriSpitters.remove(npc.hashCode())
                    raid.encounter.kephriArcanes.remove(npc.hashCode())
                }
                if (raid.currentRoom == ToaRoom.BABA && npc.id == BabaAssets.BABOON) {
                    handleBabaBaboonDeath(raid, npc.coords)
                }
                if (raid.currentRoom == ToaRoom.KEPHRI && npc.id == KephriSwarms.NPC) {
                    resolveKephriSwarm(raid, npc, healed = false)
                }
                if (raid.currentRoom == ToaRoom.AKKHA && npc.id in ToaNpcs.akkhaShadows) {
                    // completeShadow is the authority, including deaths during a blocking special.
                    finishAkkhaShadow(raid, npc)
                }
            }
            ToaNpcRole.HAZARD,
            ToaNpcRole.COMPLETION_SPIRIT,
            ToaNpcRole.HELPFUL_SPIRIT -> Unit
        }
    }

    fun markDeath(player: Player) {
        val raid = raidFor(player) ?: return
        val participant = raid.participant(player.uid) ?: return
        val pointDeath = raid.applyRewardPointDeath(player.accountHash, clock.cycle.toLong())
        if (!pointDeath.applied) return
        if (raid.currentRoom == ToaRoom.AKKHA) cleanupAkkhaPlayer(raid, player)
        if (raid.currentRoom == ToaRoom.BABA) cleanupBabaPlayer(raid, player)
        if (isWardenRoom(raid.currentRoom)) cleanupWardenPlayer(raid, player)
        participant.deaths++
        participant.spectating = true
        message(raid.party, "${player.displayName} has fallen.")
        if (!ToaWipeRules.isTrueWipe(raid.participants.values, raid.completionPending)) return

        raid.attemptsUsed++
        val limit = ToaRules.attemptLimit(raid.startSnapshot.invocations)
        if (limit != null && raid.attemptsUsed >= limit) {
            abort(raid, "the party ran out of attempts")
            return
        }
        message(
            raid.party,
            "The team has wiped. Attempts ${raid.attemptsUsed}/${limit ?: "unlimited"}.",
        )
        supplies.queueWipeSupplies(raid)
        pendingResets[raid.instance.id] = clock.cycle + 1
    }

    fun respawnAfterDeath(player: Player) {
        val raid = partyFor(player)?.activeSession ?: return
        participantRaids[player.accountHash] = raid
        raid.participant(player.accountHash)?.connected = true
        PathingEntityCommon.telejump(player, collision, roomCoord(raid, raid.currentRoom))
        ui.open(player, raid, clock.cycle)
        player.mes("You spectate until the encounter resets or your party clears it.")
    }

    fun login(player: Player) {
        supplies.sanitizeStaleLogin(player)
    }

    fun logout(player: Player) {
        val raid = raidFor(player)
        if (raid == null) {
            participantRaids.remove(player.accountHash)
            return
        }
        // A disconnect is not the Leave action. Keep the party slot, participant state, path
        // progress, and pending reward so the grouping obelisk can call rejoin on next login.
        supplies.suspendForDisconnect(player, raid)
        instances.leaveForLogout(player, clock.cycle)
        raid.participant(player.accountHash)?.connected = false
        if (raid.currentRoom == ToaRoom.AKKHA) cleanupAkkhaPlayer(raid, player)
        if (raid.currentRoom == ToaRoom.BABA) cleanupBabaPlayer(raid, player)
        if (isWardenRoom(raid.currentRoom)) cleanupWardenPlayer(raid, player)
        ui.close(player)
        log.info { "[ToA] disconnect player=${player.displayName} instance=${raid.instance.id}" }
    }

    fun externalTeleport(player: Player, instanceId: InstanceId) {
        val raid = raidFor(player) ?: return
        if (raid.instance.id != instanceId) return
        val participant = raid.participant(player.accountHash) ?: return

        // External teleports cannot be cancelled after the instance manager has detached the
        // player.
        // If the roll cannot fit in inventory, detach it into persistent lobby storage before the
        // raid session and party links are removed.
        if (raid.state == ToaRaidState.REWARDS && !participant.rewardClaimed) {
            when (rewards.claimToInventoryForLeave(player, raid)) {
                RaidRewardClaimResult.Claimed,
                RaidRewardClaimResult.AlreadyClaimed -> Unit
                RaidRewardClaimResult.InventoryFull,
                RaidRewardClaimResult.NothingToClaim ->
                    if (!rewards.detachToStorage(player, raid)) return
                RaidRewardClaimResult.Busy -> return
            }
            if (allEligibleRewardsClaimed(raid)) {
                raid.state = ToaRaidState.COMPLETE
            }
        }
        supplies.terminalCleanup(player, raid)
        retirePlayer(player, raid, removeFromParty = true)
        prepare(player)
        ui.close(player)
        syncRewardNiche(player)
        player.mes("Your Tombs raid ended because you teleported out of its instance.")
        when {
            raid.party.members.isEmpty() && raid.state == ToaRaidState.COMPLETE ->
                completeRaid(raid)
            raid.party.members.isEmpty() -> abort(raid, "party left the instance")
            raid.party.leader == player.uid -> transferOrDisband(raid.party)
        }
    }

    fun tick() {
        pendingResets
            .filterValues { clock.cycle >= it }
            .keys
            .toList()
            .forEach { id ->
                val raid = raids[id]
                if (raid == null) {
                    pendingResets.remove(id)
                    return@forEach
                }
                if (!ToaWipeRules.isTrueWipe(raid.participants.values, raid.completionPending)) {
                    pendingResets.remove(id)
                    return@forEach
                }
                if (!ToaRaidPauseRules.shouldAdvance(raid.participants.values)) return@forEach
                pendingResets.remove(id)
                resetEncounter(raid)
            }
        raids.values.toList().forEach { raid ->
            if (raid.state !in setOf(ToaRaidState.ACTIVE, ToaRaidState.WARDENS)) return@forEach
            processInvocationTimer(raid)
            if (!ToaRaidPauseRules.shouldAdvance(raid.participants.values)) return@forEach
            val challenge = raid.challenge
            if (
                challenge?.stage == ToaChallengeStage.STARTING &&
                    clock.cycle >= challenge.entryDeadline
            ) {
                challenge.stage = ToaChallengeStage.INACTIVE
                challenge.entryStarter = null
                challenge.entryDeadline = -1
                message(raid.party, "The challenge entry was interrupted; pass the barrier again.")
            }
            if (
                raid.currentRoom in ToaChallengeRoomRules.rooms &&
                    challenge?.stage != ToaChallengeStage.ACTIVE
            )
                return@forEach
            when (raid.currentRoom) {
                ToaRoom.KEPHRI -> {
                    processKephriDeath(raid)
                    processKephriBombs(raid)
                    processKephriDungStrikes(raid)
                    processKephriDungPlacements(raid)
                    processKephriEggs(raid)
                    processKephriScarabs(raid)
                    processKephriBomberBombs(raid)
                    boss(raid)?.let { processKephriSwarms(raid, it) }
                    processKephriMedic(raid)
                    processKephriRecovery(raid)
                }
                ToaRoom.CRONDIS -> crondisZebak.tickCrondis(raid)
                ToaRoom.APMEKEN -> tickApmeken(raid)
                ToaRoom.HET -> handleHetResult(raid, het.tick(raid))
                ToaRoom.SCABARAS -> {
                    if (scabaras.tick(raid)) completeScabarasChallenge(raid)
                }
                ToaRoom.ZEBAK -> crondisZebak.tickZebak(raid)?.let { completePathBoss(raid, it) }
                ToaRoom.BABA -> tickBabaEncounter(raid)
                ToaRoom.AKKHA -> {
                    processAkkhaTimedMechanics(raid)
                    processAkkhaThresholds(raid)
                }
                ToaRoom.WARDENS,
                ToaRoom.TUMEKEN_WARDEN -> processWardenTransitions(raid)
                else -> Unit
            }
            processPendingHits(raid)
            if (clock.cycle >= raid.nextMechanicAt && boss(raid) != null) tickBossMechanic(raid)
            if (clock.cycle % 5 == 0) forEachOnline(raid) { ui.sync(it, raid, clock.cycle) }
        }
    }

    private fun processInvocationTimer(raid: ToaRaidSession) {
        val invocations = raid.startSnapshot.invocations
        val limit = ToaRules.timeLimitTicks(invocations)
        if (!ToaRules.timerHasExpired(raid.timerStartedAt, clock.cycle, limit, raid.timerExpired))
            return
        raid.timerExpired = true
        val penalty = ToaRules.timePenaltyLevels(invocations)
        raid.rewardRaidLevel = (raid.raidLevel - penalty).coerceAtLeast(0)
        message(
            raid.party,
            "Your invocation time has expired; the final raid level is reduced by $penalty.",
        )
    }

    fun raidFor(player: Player): ToaRaidSession? =
        participantRaids[player.accountHash] ?: partyFor(player)?.activeSession

    fun raidFor(npc: Npc): ToaRaidSession? =
        raids.values.firstOrNull { it.owns(npc) || scabaras.owns(it, npc) }

    fun partyFor(player: Player): ToaParty? = membership[player.uid]

    fun status(player: Player): String {
        val raid = raidFor(player)
        if (raid != null) {
            return ToaDevStatusSnapshot(
                    seed = raid.startSnapshot.seed,
                    lockedPartySize = raid.startSnapshot.partySize,
                    raidLevel = raid.raidLevel,
                    pathLevels = raid.pathLevels.toMap(),
                    room = raid.currentRoom,
                    completedPaths = raid.completedPaths.toSet(),
                    wardenStage = raid.encounter.wardenStage,
                )
                .render()
        }
        return invocationStatus(player)
    }

    fun setBabaTrace(player: Player, setting: String): Boolean {
        val raid = raidFor(player)
        if (raid == null) {
            player.mes("Join or create an active Tombs raid first.")
            return false
        }
        return when (setting.lowercase()) {
            "on" -> {
                raid.baba.trace.enabled = true
                traceBaba(raid, boss(raid), "trace_enabled")
                player.mes("Ba-Ba tick trace enabled for this raid party.")
                true
            }
            "off" -> {
                raid.baba.trace.enabled = false
                player.mes("Ba-Ba tick trace disabled.")
                true
            }
            "status",
            "dump" -> {
                player.mes(
                    "Ba-Ba trace=${raid.baba.trace.enabled}, state=${raid.baba.state}, autos=${raid.baba.attackCounter}, rollingSet=${raid.baba.rollingSet}."
                )
                raid.baba.trace.snapshot(8).forEach { player.mes(it.toString()) }
                if (setting.equals("dump", ignoreCase = true)) {
                    BabaTuning.traceRequired.forEach { (name, value) ->
                        player.mes(
                            "TRACE_REQUIRED $name=${value.value} ${value.units}: ${value.evidence}"
                        )
                    }
                }
                true
            }
            else -> {
                player.mes(
                    "Use ::babatrace on, ::babatrace off, ::babatrace status, or ::babatrace dump."
                )
                false
            }
        }
    }

    fun setAkkhaTrace(player: Player, setting: String): Boolean {
        val raid = raidFor(player)
        if (raid == null) {
            player.mes("Join or create an active Tombs raid first.")
            return false
        }
        return when (setting.lowercase()) {
            "on" -> {
                raid.akkha.trace.enabled = true
                traceAkkha(raid, boss(raid), "trace_enabled")
                player.mes("Akkha tick trace enabled for this raid party.")
                true
            }
            "off" -> {
                raid.akkha.trace.enabled = false
                player.mes("Akkha tick trace disabled.")
                true
            }
            "status",
            "dump" -> {
                player.mes(
                    "Akkha trace=${raid.akkha.trace.enabled}, state=${raid.akkha.state}, style=${raid.akkha.style}, autos=${raid.akkha.attackCounter}."
                )
                raid.akkha.trace.snapshot(8).forEach { player.mes(it.toString()) }
                true
            }
            else -> {
                player.mes("Use ::akkhatrace on, ::akkhatrace off, or ::akkhatrace status.")
                false
            }
        }
    }

    fun reset(player: Player): Boolean {
        val raid = raidFor(player) ?: return false
        abort(raid, "reset by an administrator")
        return true
    }

    fun devStartSolo(player: Player): Boolean {
        val party = partyFor(player) ?: createParty(player)
        if (party.members.any { it != player.uid }) {
            player.mes("Leave your current group before starting the solo developer raid.")
            return false
        }
        return start(player)
    }

    fun devStart(player: Player, expectedPartySize: Int, seed: Long): Boolean {
        if (!ToaDevStartRules.validExpectedPartySize(expectedPartySize)) {
            player.mes("Developer party size must be between 1 and $TOA_MAX_PARTY_SIZE.")
            return false
        }
        val party = partyFor(player) ?: createParty(player)
        if (party.leader != player.uid) {
            player.mes("Only the party leader can start a deterministic Tombs raid.")
            return false
        }
        if (!ToaDevStartRules.matchesPartySize(expectedPartySize, party.members.size)) {
            player.mes(
                "Developer start expected $expectedPartySize party members, but the party has " +
                    "${party.members.size}."
            )
            return false
        }

        devSeedOverrides.arm(player.accountHash, seed)
        val started = start(player)
        if (!started) {
            devSeedOverrides.consume(player.accountHash)
            return false
        }
        player.mes(
            "Developer: started raid level ${party.invocations.raidLevel} for " +
                "$expectedPartySize player(s) with seed $seed."
        )
        return true
    }

    fun devStartWardens(player: Player): Boolean {
        if (raidFor(player) == null && !devStartSolo(player)) {
            return false
        }
        val entered = devEnterRoom(player, "wardens")
        if (entered) {
            player.mes("Developer: all four paths are complete; the Wardens encounter is ready.")
        }
        return entered
    }

    fun devCompletePath(player: Player, path: ToaPath): Boolean {
        val raid = raidFor(player) ?: return false
        recordPathCompletion(raid, path)
        player.mes(
            "Developer: ${path.displayName} marked complete (${raid.completedPaths.size}/4)."
        )
        return true
    }

    fun devCompleteAllPaths(player: Player): Boolean {
        val raid = raidFor(player) ?: return false
        TOA_ACTIVE_PATHS.forEach { recordPathCompletion(raid, it) }
        clearEncounterNpcs(raid)
        raid.state = ToaRaidState.ACTIVE
        raid.currentRoom = ToaRoom.LOBBY
        raid.currentPath = null
        raid.encounterStarted = false
        raid.completionPending = false
        teleportParty(raid, ToaRoom.LOBBY)
        syncWardensEntrance(raid, unlocked = true)
        player.mes("Developer: all four paths are complete and the real Wardens entrance is open.")
        return true
    }

    fun devEnterRoom(player: Player, name: String): Boolean {
        val raid = raidFor(player) ?: return false
        val route = ToaDevRoomRoutes.parse(name)
        if (route == null) {
            player.mes(
                "Use a challenge or boss name: scabaras/kephri, apmeken/baba, " +
                    "crondis/zebak, het/akkha, wardens, tumeken, or reward."
            )
            return false
        }
        when (route) {
            is ToaDevRoomRoute.Challenge -> {
                val path = route.path
                clearEncounterNpcs(raid)
                raid.completedPaths.remove(path)
                raid.completedRooms.remove(path.challenge)
                raid.completedRooms.remove(path.boss)
                raid.state = ToaRaidState.ACTIVE
                raid.currentPath = path
                raid.currentRoom = path.challenge
                raid.challenge = ToaChallengeState(path)
                raid.encounter.reset()
                raid.encounterStarted = false
                raid.completionPending = false
                teleportParty(raid, path.challenge)
                player.mes(
                    "Developer: entered ${path.challenge.displayName}; pass its red barrier to start."
                )
                return true
            }
            is ToaDevRoomRoute.Boss -> {
                val path = route.path
                clearEncounterNpcs(raid)
                raid.completedPaths.remove(path)
                raid.completedRooms.remove(path.challenge)
                raid.completedRooms.remove(path.boss)
                raid.state = ToaRaidState.ACTIVE
                raid.currentPath = path
                raid.currentRoom = path.boss
                raid.challenge = null
                raid.encounter.reset()
                raid.encounterStarted = true
                raid.completionPending = false
                teleportParty(raid, path.boss)
                if (path == ToaPath.CRONDIS) crondisZebak.initializeZebak(raid)
                else spawnBoss(raid, bossId(path))
                raid.nextMechanicAt = clock.cycle + 5
                player.mes("Developer: entered ${path.boss.displayName}.")
                return true
            }
            else -> Unit
        }
        return when (route) {
            ToaDevRoomRoute.Wardens -> {
                TOA_ACTIVE_PATHS.forEach { recordPathCompletion(raid, it) }
                clearEncounterNpcs(raid)
                raid.state = ToaRaidState.WARDENS
                raid.currentRoom = ToaRoom.WARDENS
                raid.currentPath = null
                raid.wardenPhase = 0
                raid.encounter.reset()
                // Match the normal route: the command enters the crystal waiting room, while
                // startWardensFight owns the threshold teleport and phase-one initialization.
                raid.encounterStarted = false
                raid.completionPending = false
                teleportParty(raid, ToaRoom.WARDENS)
                true
            }
            ToaDevRoomRoute.Tumeken -> {
                clearEncounterNpcs(raid)
                raid.state = ToaRaidState.WARDENS
                raid.currentRoom = ToaRoom.TUMEKEN_WARDEN
                raid.currentPath = null
                raid.wardenPhase = 1
                raid.encounter.reset()
                raid.encounter.wardenStage = ToaWardenStage.PHASE_THREE
                raid.encounter.wardenPhaseThreeGod = WardenGod.TUMEKEN
                raid.encounterStarted = true
                raid.completionPending = false
                teleportParty(raid, ToaRoom.TUMEKEN_WARDEN)
                spawnWardenPhaseThree(raid)
                raid.nextMechanicAt = clock.cycle + 5
                true
            }
            ToaDevRoomRoute.Reward -> {
                clearEncounterNpcs(raid)
                raid.state = ToaRaidState.WARDENS
                raid.currentRoom = ToaRoom.TUMEKEN_WARDEN
                raid.completionPending = true
                unlockRewards(raid)
                teleportParty(raid, ToaRoom.REWARD)
                true
            }
            is ToaDevRoomRoute.Challenge,
            is ToaDevRoomRoute.Boss ->
                error("Path routes return before the terminal developer-room switch.")
        }
    }

    /** Starts the path exactly once after the first party member crosses its red barrier. */
    private fun beginChallenge(raid: ToaRaidSession, path: ToaPath) {
        check(raid.currentRoom == path.challenge) {
            "ToA challenge started outside its map segment: path=$path room=${raid.currentRoom}"
        }
        check(raid.challenge?.stage == ToaChallengeStage.ACTIVE) {
            "ToA challenge started before its red barrier: path=$path"
        }
        when (path) {
            ToaPath.CRONDIS -> crondisZebak.initializeCrondis(raid)
            ToaPath.APMEKEN -> initializeApmeken(raid)
            ToaPath.SCABARAS -> scabaras.initialize(raid)
            ToaPath.HET -> handleHetResult(raid, het.initialize(raid))
        }
    }

    private fun handleHetResult(raid: ToaRaidSession, result: HetLiveBridgeResult): Boolean {
        if (!result.handled) return false
        result.sealWorkByAccountHash.forEach { (accountHash, appliedWork) ->
            raid.awardRewardDamage(
                accountHash,
                ToaRewardPointRoom.HET,
                ToaRewardPointTargets.hetSeal(),
                appliedWork,
            )
        }
        if (result.challengeComplete) completeHetChallenge(raid)
        return true
    }

    private fun completeHetChallenge(raid: ToaRaidSession) {
        val challenge = raid.challenge ?: return
        if (
            challenge.path != ToaPath.HET || challenge.complete || raid.currentRoom != ToaRoom.HET
        ) {
            return
        }
        completeChallenge(raid)
    }

    private fun completeScabarasChallenge(raid: ToaRaidSession) {
        val challenge = raid.challenge ?: return
        if (
            challenge.path != ToaPath.SCABARAS ||
                challenge.complete ||
                raid.currentRoom != ToaRoom.SCABARAS
        ) {
            return
        }
        scabaras.cleanup(raid)
        completeChallenge(raid)
    }

    private fun settleRewardPoints(raid: ToaRaidSession, room: ToaRoom) {
        val rewardRoom = rewardPointRoom(room) ?: return
        raid.settleRewardPointRoom(rewardRoom, clock.cycle.toLong())
    }

    private fun completeChallenge(raid: ToaRaidSession) {
        val state = raid.challenge ?: return
        if (state.complete) return
        if (state.path == ToaPath.HET) clearEncounterNpcs(raid)
        state.stage = ToaChallengeStage.COMPLETE
        state.complete = true
        raid.completedRooms += state.path.challenge
        settleRewardPoints(raid, state.path.challenge)
        raid.currentRoom = state.path.boss
        raid.encounterStarted = false
        raid.completionPending = false
        raid.participants.values.forEach { it.currentRoom = state.path.boss }
        teleportParty(raid, state.path.boss)
        if (state.path == ToaPath.HET) ensureAkkhaWaitingActor(raid)
        message(
            raid.party,
            "The Path of ${state.path.displayName} challenge is complete. Use its crystal to enter ${state.path.boss.displayName}'s arena.",
        )
    }

    private fun initializeApmeken(raid: ToaRaidSession) {
        cleanupApmeken(raid)
        val roster = raid.participants.values.toList()
        val slots = LinkedHashMap<PlayerUid, Int>()
        roster.forEachIndexed { index, participant ->
            if (participant.rewardEligible) slots[participant.uid] = index
        }
        apmekenPlayerSlots[raid.instance.id] = slots
        apmekenNpcs[raid.instance.id] = linkedMapOf()
        applyApmekenEvents(
            raid,
            apmeken.initialize(
                instanceKey = raid.instance.id,
                seed = raid.startSnapshot.seed xor 0x41504D454B454EL,
                partySize = raid.startSnapshot.partySize,
                startTick = clock.cycle,
                raidLevel = raid.raidLevel,
                pathLevel = raid.pathLevels.getValue(ToaPath.APMEKEN),
            ),
        )
        roster.forEachIndexed { index, participant ->
            if (!participant.rewardEligible) {
                applyApmekenEvents(
                    raid,
                    apmeken.retirePlayer(raid.instance.id, index, clock.cycle),
                    allowChallengeComplete = false,
                )
            }
        }
        roster
            .filter { it.connected && !it.spectating && it.currentRoom == ToaRoom.APMEKEN }
            .mapNotNull { it.uid.resolve(players) }
            .filter { ToaChallengeRoomRules.isInside(raid, instances, ToaRoom.APMEKEN, it.coords) }
            .forEach { enterApmekenPlayer(raid, it) }
    }

    private fun enterApmekenPlayer(raid: ToaRaidSession, player: Player) {
        if (!apmeken.contains(raid.instance.id)) return
        val slot = apmekenPlayerSlots[raid.instance.id]?.get(player.uid) ?: return
        applyApmekenEvents(raid, apmeken.enterPlayer(raid.instance.id, slot, clock.cycle))
    }

    fun interactApmekenObject(player: Player, loc: BoundLocInfo): Boolean {
        val raid = raidFor(player) ?: return false
        if (raid.currentPath != ToaPath.APMEKEN || raid.currentRoom != ToaRoom.APMEKEN) return false
        if (raid.challenge?.stage != ToaChallengeStage.ACTIVE) {
            return when (loc.id) {
                ApmekenRevision240RuntimeLayout.hammerSupplyLoc.locId -> {
                    if (!supplies.grantChallengeItem(player, raid, ApmekenLiveTuning.HAMMER_ITEM)) {
                        player.mes("You need more inventory space.")
                    }
                    true
                }
                ApmekenRevision240RuntimeLayout.potionSupplyLoc.locId -> {
                    if (
                        !supplies.grantChallengeItem(
                            player,
                            raid,
                            ApmekenLiveTuning.NEUTRALISING_POTION_ITEM,
                        )
                    ) {
                        player.mes("You need more inventory space.")
                    }
                    true
                }
                else -> {
                    requireActiveChallenge(player, raid, ToaPath.APMEKEN)
                    false
                }
            }
        }
        if (!apmeken.contains(raid.instance.id)) return false
        val slot = apmekenPlayerSlots[raid.instance.id]?.get(player.uid) ?: return false
        val events =
            apmeken.interactObject(
                raid.instance.id,
                ApmekenObjectInteraction(
                    player = slot,
                    locId = loc.id,
                    tile = apmekenLocalTile(raid, loc.coords),
                    hasHammer =
                        supplies.hasOwnedChallengeItem(player, raid, ApmekenLiveTuning.HAMMER_ITEM),
                    neutralisingPotions =
                        supplies.ownedChallengeItemCount(
                            player,
                            raid,
                            ApmekenLiveTuning.NEUTRALISING_POTION_ITEM,
                        ),
                ),
                clock.cycle,
            )
        if (events.isEmpty()) return false
        applyApmekenEvents(raid, events)
        return true
    }

    fun useApmekenPotion(player: Player, target: Player): Boolean {
        val raid = raidFor(player) ?: return false
        if (!requireActiveChallenge(player, raid, ToaPath.APMEKEN)) return false
        if (
            raid.currentRoom != ToaRoom.APMEKEN ||
                raidFor(target) !== raid ||
                !apmeken.contains(raid.instance.id)
        ) {
            return false
        }
        val sourceSlot = apmekenPlayerSlots[raid.instance.id]?.get(player.uid) ?: return false
        val targetSlot = apmekenPlayerSlots[raid.instance.id]?.get(target.uid) ?: return false
        applyApmekenEvents(
            raid,
            apmeken.useNeutralisingPotion(
                raid.instance.id,
                ApmekenPotionInteraction(
                    player = sourceSlot,
                    target = targetSlot,
                    neutralisingPotions =
                        supplies.ownedChallengeItemCount(
                            player,
                            raid,
                            ApmekenLiveTuning.NEUTRALISING_POTION_ITEM,
                        ),
                ),
                clock.cycle,
            ),
        )
        return true
    }

    fun pourApmekenPotion(player: Player): Boolean {
        val raid = raidFor(player) ?: return false
        if (!requireActiveChallenge(player, raid, ToaPath.APMEKEN)) return false
        if (raid.currentRoom != ToaRoom.APMEKEN || !apmeken.contains(raid.instance.id)) {
            return false
        }
        val slots = apmekenPlayerSlots[raid.instance.id] ?: return false
        val sourceSlot = slots[player.uid] ?: return false
        val sourceTile = apmekenLocalTile(raid, player.coords)
        val playersOnTile =
            activePlayers(raid)
                .asSequence()
                .filter { apmekenLocalTile(raid, it.coords) == sourceTile }
                .mapNotNull { slots[it.uid] }
                .toMutableSet()
                .apply { add(sourceSlot) }
        applyApmekenEvents(
            raid,
            apmeken.pourNeutralisingPotion(
                raid.instance.id,
                ApmekenPotionPourInteraction(
                    player = sourceSlot,
                    tile = sourceTile,
                    playersOnTile = playersOnTile,
                    neutralisingPotions =
                        supplies.ownedChallengeItemCount(
                            player,
                            raid,
                            ApmekenLiveTuning.NEUTRALISING_POTION_ITEM,
                        ),
                ),
                clock.cycle,
            ),
        )
        return true
    }

    private fun tickApmeken(raid: ToaRaidSession) {
        if (!apmeken.contains(raid.instance.id)) return
        val slots = apmekenPlayerSlots[raid.instance.id] ?: return
        val active = activePlayers(raid)
        val activeSlots = active.mapNotNull { slots[it.uid] }.toSet()
        val playerTiles =
            active
                .mapNotNull { player ->
                    slots[player.uid]?.let { it to apmekenLocalTile(raid, player.coords) }
                }
                .toMap()
        apmeken
            .snapshot(raid.instance.id)
            ?.actors
            ?.filter { it.alive }
            ?.forEach { actor ->
                val npc = apmekenNpcs[raid.instance.id]?.get(actor.npcKey) ?: return@forEach
                if (!npc.isSlotAssigned || npc.hitpoints <= 0) return@forEach
                val target =
                    actor.targetPlayer
                        ?.let { playerForApmekenSlot(raid, it) }
                        ?.takeIf { it in active } ?: nearestActive(raid, npc.coords)
                when (actor.type) {
                    ApmekenBaboon.VOLATILE -> {
                        if (target != null && !actor.volatileCharging) {
                            moveApmekenNpcToward(npc, target.coords)
                        } else if (actor.volatileCharging) {
                            npc.abortRoute()
                        }
                    }
                    ApmekenBaboon.CURSED -> {
                        val current = apmekenLocalTile(raid, npc.coords)
                        val next =
                            ApmekenLiveMovementPolicy.cursedWanderCandidates(
                                    current,
                                    actor.npcKey,
                                    clock.cycle,
                                )
                                .firstOrNull {
                                    ApmekenRevision240RuntimeLayout.isWalkableFloor(it) &&
                                        ApmekenRevision240RuntimeLayout.isVenomPlacementTile(it)
                                }
                        if (next != null) {
                            npc.walkTo(
                                routeFactory,
                                apmekenCoord(raid, next),
                                passThroughEntities = false,
                            )
                        }
                    }
                    ApmekenBaboon.SHAMAN -> {
                        if (target != null) {
                            npc.faceSquare(target.coords)
                            if (
                                npc.coords.chebyshevDistance(target.coords) >
                                    ApmekenLiveMovementPolicy.attackRange(actor.type)
                            ) {
                                moveApmekenNpcToward(npc, target.coords)
                            } else {
                                npc.abortRoute()
                            }
                        }
                    }
                    else -> {
                        if (target != null) maintainApmekenAggression(npc, actor.type, target)
                    }
                }
            }
        val npcTiles =
            apmekenNpcs[raid.instance.id]
                .orEmpty()
                .mapNotNull { (key, npc) ->
                    npc.takeIf { it.isSlotAssigned && it.hitpoints > 0 }
                        ?.let { key to apmekenLocalTile(raid, it.coords) }
                }
                .toMap()
        applyApmekenEvents(
            raid,
            apmeken.tick(
                raid.instance.id,
                ApmekenLiveTickInput(
                    tick = clock.cycle,
                    activePlayers = activeSlots,
                    playerTiles = playerTiles,
                    npcTiles = npcTiles,
                ),
            ),
        )
    }

    private fun applyApmekenEvents(
        raid: ToaRaidSession,
        events: List<ApmekenLiveEvent>,
        allowChallengeComplete: Boolean = true,
    ) {
        events.forEach { event ->
            when (event) {
                is ApmekenLiveEvent.Initialized -> Unit
                is ApmekenLiveEvent.WaveScheduled ->
                    if (event.wave > 1) {
                        message(raid.party, "Apmeken wave ${event.wave} is approaching.")
                    }
                is ApmekenLiveEvent.WaveStarted ->
                    message(
                        raid.party,
                        "Apmeken wave ${event.wave}/${ApmekenWaves.WAVE_COUNT} begins.",
                    )
                is ApmekenLiveEvent.SpawnNpc -> spawnApmekenNpc(raid, event.request)
                is ApmekenLiveEvent.KillNpcRequest -> removeApmekenNpc(raid, event.npcKey)
                is ApmekenLiveEvent.DespawnNpcRequest -> removeApmekenNpc(raid, event.npcKey)
                is ApmekenLiveEvent.SightChanged -> {
                    event.previousPlayer
                        ?.let { playerForApmekenSlot(raid, it) }
                        ?.mes("Apmeken's sight leaves you.")
                    event.currentPlayer
                        ?.let { playerForApmekenSlot(raid, it) }
                        ?.mes("Apmeken grants you sight. Watch for the room's warnings.")
                }
                is ApmekenLiveEvent.PlayerGraphic ->
                    playerForApmekenSlot(raid, event.player)?.spotanim(event.spotanimId)
                is ApmekenLiveEvent.MapGraphic -> {
                    if (event.visibleTo == null) {
                        world.spotanimMap(
                            SpotanimType(event.spotanimId),
                            apmekenCoord(raid, event.tile),
                            height = event.height,
                        )
                    } else {
                        playerForApmekenSlot(raid, event.visibleTo)?.let { player ->
                            world.spotanimMapPrivate(
                                player,
                                SpotanimType(event.spotanimId),
                                apmekenCoord(raid, event.tile),
                                height = event.height,
                            )
                        }
                    }
                }
                is ApmekenLiveEvent.PlayerAnimation ->
                    playerForApmekenSlot(raid, event.player)?.anim(event.sequenceId)
                is ApmekenLiveEvent.NpcAnimation ->
                    apmekenNpcs[raid.instance.id]?.get(event.npcKey)?.anim(event.sequenceId)
                is ApmekenLiveEvent.NpcAttackRequest -> applyApmekenNpcAttack(raid, event)
                is ApmekenLiveEvent.QteStarted ->
                    message(
                        raid.party,
                        when (event.snapshot.type) {
                            ApmekenQteType.ROOF_SUPPORTS -> "The roof supports begin to collapse!"
                            ApmekenQteType.VENTS -> "Venomous fumes pour from the vents!"
                            ApmekenQteType.CORRUPTION -> "A party member has become corrupted!"
                        },
                    )
                is ApmekenLiveEvent.QteFeedback -> Unit
                is ApmekenLiveEvent.QteResolved ->
                    message(
                        raid.party,
                        if (event.success) "Apmeken's challenge is resolved."
                        else "Apmeken's challenge was not resolved in time.",
                    )
                is ApmekenLiveEvent.QteCancelled ->
                    message(raid.party, "Apmeken's warning fades as a party member leaves.")
                is ApmekenLiveEvent.DamagePlayer -> {
                    val target = playerForApmekenSlot(raid, event.player) ?: return@forEach
                    target.queueHit(
                        delay = 1,
                        type = HitType.Typeless,
                        damage = scaledDamage(raid, event.baseDamage),
                        modifier = org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier,
                    )
                }
                is ApmekenLiveEvent.ApplyVenom ->
                    playerForApmekenSlot(raid, event.player)?.let(PlayerVenom::tryVenom)
                is ApmekenLiveEvent.ChangeObject -> changeApmekenObject(raid, event)
                is ApmekenLiveEvent.SupplyTransfer -> applyApmekenSupplyTransfer(raid, event)
                is ApmekenLiveEvent.Feedback -> applyApmekenFeedback(raid, event)
                is ApmekenLiveEvent.ShamanSuppressionChanged -> Unit
                is ApmekenLiveEvent.SpawnVenomPool -> spawnApmekenPool(raid, event)
                is ApmekenLiveEvent.RemoveVenomPool -> removeApmekenPool(raid, event)
                is ApmekenLiveEvent.ChallengeComplete ->
                    if (allowChallengeComplete) {
                        cleanupApmeken(raid)
                        completeChallenge(raid)
                    }
                is ApmekenLiveEvent.CleanupComplete -> Unit
            }
        }
        syncApmekenChallengeState(raid)
    }

    private fun spawnApmekenNpc(raid: ToaRaidSession, request: ApmekenNpcSpawnRequest) {
        val direction =
            if (request.facing == ApmekenLiveFacing.SOUTH) Direction.South else Direction.North
        val npc =
            spawnNpc(
                raid,
                request.npcId,
                apmekenCoord(raid, request.tile),
                ToaNpcRole.CHALLENGE,
                direction,
            )
        npc.anim(request.spawnSequence)
        request.spawnSpotanim?.let(npc::spotanim)
        npc.mode = null
        npc.clearInteraction()
        npc.abortRoute()
        val type = ApmekenCombatRules.type(request.npcId)
        val target = request.targetPlayer?.let { playerForApmekenSlot(raid, it) }
        if (type != null) {
            npc.maxOpRangeOverride = ApmekenLiveMovementPolicy.ROOM_CHASE_RANGE
            npc.apRangeOverride = ApmekenLiveMovementPolicy.attackRange(type)
            if (target != null) {
                npc.faceSquare(target.coords)
                if (ApmekenLiveMovementPolicy.usesAutoCombat(type)) {
                    npc.apPlayer2(target, interactions)
                }
            }
        }
        apmekenNpcs.getOrPut(raid.instance.id, ::linkedMapOf)[request.npcKey] = npc
        apmekenNpcKeys[npc] = request.npcKey
    }

    private fun moveApmekenNpcToward(npc: Npc, target: CoordGrid) {
        npc.walkTo(routeFactory, target, passThroughEntities = false)
    }

    private fun maintainApmekenAggression(npc: Npc, type: ApmekenBaboon, target: Player) {
        val desiredAttackRange = ApmekenLiveMovementPolicy.attackRange(type)
        npc.maxOpRangeOverride = ApmekenLiveMovementPolicy.ROOM_CHASE_RANGE
        npc.apRangeOverride = desiredAttackRange
        npc.faceSquare(target.coords)
        val interaction = npc.interaction as? InteractionPlayer
        if (
            ApmekenLiveMovementPolicy.shouldRearm(
                npc.mode == NpcMode.ApPlayer2,
                interaction?.uid == target.uid,
                interaction?.apRange == desiredAttackRange,
            )
        ) {
            npc.apPlayer2(target, interactions)
        }
    }

    private fun applyApmekenNpcAttack(
        raid: ToaRaidSession,
        event: ApmekenLiveEvent.NpcAttackRequest,
    ) {
        val source = apmekenNpcs[raid.instance.id]?.get(event.npcKey) ?: return
        val target = playerForApmekenSlot(raid, event.targetPlayer) ?: return
        if (!source.isSlotAssigned || source.hitpoints <= 0 || target.hitpoints <= 0) return
        if (
            source.coords.chebyshevDistance(target.coords) >
                ApmekenLiveMovementPolicy.PROJECTILE_ATTACK_RANGE
        ) {
            return
        }
        source.anim(event.sequenceId)
        sendProjectile(source, target, event.projectileId)
        val seed =
            raid.party.id.mostSignificantBits xor
                raid.party.id.leastSignificantBits xor
                event.npcKey xor
                clock.cycle.toLong()
        val damage = scaledDamage(raid, Random(seed).nextInt(8))
        val nativeImpact =
            if (event.drainPrayerByLandedDamage) {
                HitImpactHandler { impact ->
                    if (impact.damage > 0) {
                        target.statSub("stat.prayer", constant = impact.damage, percent = 0)
                    }
                }
            } else {
                null
            }
        target.queueHit(
            source = source,
            delay = 2,
            type = HitType.Magic,
            damage = damage,
            modifier =
                NpcPartialProtectionPrayerPlayerHitModifier(
                    ToaRules.prayerReductionPercent(100, raid.startSnapshot.invocations)
                ),
            impactHandler = prayerImpactHandler(raid, target, HitType.Magic, nativeImpact),
        )
    }

    private fun applyApmekenSupplyTransfer(
        raid: ToaRaidSession,
        event: ApmekenLiveEvent.SupplyTransfer,
    ) {
        val player = playerForApmekenSlot(raid, event.player) ?: return
        require(
            event.itemId == ApmekenLiveTuning.HAMMER_ITEM ||
                event.itemId == ApmekenLiveTuning.NEUTRALISING_POTION_ITEM
        )
        if (event.amount > 0) {
            if (!supplies.grantChallengeItem(player, raid, event.itemId, event.amount)) {
                player.mes("You need more inventory space.")
            }
        } else if (event.amount < 0) {
            supplies.consumeChallengeItem(player, raid, event.itemId, -event.amount)
        }
    }

    private fun applyApmekenFeedback(raid: ToaRaidSession, event: ApmekenLiveEvent.Feedback) {
        val text =
            when (event.code) {
                ApmekenLiveFeedbackCode.NEED_HAMMER -> "You need a hammer to repair this."
                ApmekenLiveFeedbackCode.NEED_NEUTRALISING_POTION ->
                    "You need a neutralising potion."
                ApmekenLiveFeedbackCode.STAND_ON_VENT ->
                    "Stand in the centre of a vent before pouring the potion."
                ApmekenLiveFeedbackCode.FIXTURE_ALREADY_COMPLETE ->
                    "This part of the challenge is already complete."
                ApmekenLiveFeedbackCode.QTE_PROGRESS -> "You make progress on Apmeken's challenge."
                ApmekenLiveFeedbackCode.QTE_COMPLETE -> "You complete your part of the challenge."
                ApmekenLiveFeedbackCode.QTE_FAILED -> "Apmeken's challenge has failed."
                ApmekenLiveFeedbackCode.NOTHING_TO_NEUTRALISE ->
                    "There is nothing here to neutralise."
                ApmekenLiveFeedbackCode.SHAMAN_SUPPRESSED ->
                    "The poisoned shaman can no longer summon thralls."
            }
        if (event.player == null) message(raid.party, text)
        else playerForApmekenSlot(raid, event.player)?.mes(text)
    }

    private fun changeApmekenObject(raid: ToaRaidSession, event: ApmekenLiveEvent.ChangeObject) {
        val tile = apmekenCoord(raid, event.tile)
        val current = locs.findAll(tile).firstOrNull { it.id == event.fromLocId }
        if (current != null) {
            locs.change(
                current,
                requireNotNull(ServerCacheManager.getObject(event.toLocId)),
                Int.MAX_VALUE,
            )
        }
        if (event.animationId >= 0) {
            locs
                .findAll(tile)
                .firstOrNull { it.id == event.toLocId }
                ?.let { world.locAnim(it, event.animationId) }
        }
    }

    private fun spawnApmekenPool(raid: ToaRaidSession, event: ApmekenLiveEvent.SpawnVenomPool) {
        val tile = apmekenCoord(raid, event.tile)
        if (locs.findAll(tile).any { it.id == event.locId }) return
        val angles = arrayOf(LocAngle.North, LocAngle.East, LocAngle.South, LocAngle.West)
        locs.add(
            tile,
            requireNotNull(ServerCacheManager.getObject(event.locId)),
            Int.MAX_VALUE,
            angles[event.angle and 3],
            LocShape.CentrepieceStraight,
        )
    }

    private fun removeApmekenPool(raid: ToaRaidSession, event: ApmekenLiveEvent.RemoveVenomPool) {
        val tile = apmekenCoord(raid, event.tile)
        locs
            .findAll(tile)
            .filter { it.id == event.locId }
            .toList()
            .forEach { locs.del(it, Int.MAX_VALUE) }
    }

    private fun removeApmekenNpc(raid: ToaRaidSession, key: Long) {
        val npc = apmekenNpcs[raid.instance.id]?.remove(key) ?: return
        apmekenNpcKeys.remove(npc)
        removeNpc(raid, npc)
    }

    private fun syncApmekenChallengeState(raid: ToaRaidSession) {
        val state = raid.challenge?.takeIf { it.path == ToaPath.APMEKEN } ?: return
        val snapshot = apmeken.snapshot(raid.instance.id) ?: return
        state.wave = snapshot.currentWave
        state.waveAlive = snapshot.actors.count { it.alive }
        state.complete = snapshot.complete
    }

    private fun retireApmekenPlayer(raid: ToaRaidSession, player: Player) {
        val id = raid.instance.id
        val slots = apmekenPlayerSlots[id] ?: return
        val slot = slots[player.uid] ?: return
        if (apmeken.contains(id)) {
            applyApmekenEvents(
                raid,
                apmeken.retirePlayer(id, slot, clock.cycle),
                allowChallengeComplete = false,
            )
        }
        slots.remove(player.uid)
    }

    private fun cleanupApmeken(raid: ToaRaidSession) {
        val id = raid.instance.id
        forEachOnline(raid) { player ->
            supplies.removeIssuedChallengeItems(player, raid, ApmekenLiveTuning.HAMMER_ITEM)
            supplies.removeIssuedChallengeItems(
                player,
                raid,
                ApmekenLiveTuning.NEUTRALISING_POTION_ITEM,
            )
            PlayerPoison.clear(player)
            PlayerVenom.clear(player)
        }
        if (apmeken.contains(id)) {
            applyApmekenEvents(raid, apmeken.cleanup(id), allowChallengeComplete = false)
        }
        apmekenNpcs.remove(id)?.values?.toList()?.forEach { npc ->
            apmekenNpcKeys.remove(npc)
            removeNpc(raid, npc)
        }
        apmekenNpcKeys.entries.removeIf { (npc, _) -> raid.owns(npc) }
        apmekenPlayerSlots.remove(id)
    }

    private fun playerForApmekenSlot(raid: ToaRaidSession, slot: Int): Player? {
        val uid =
            apmekenPlayerSlots[raid.instance.id]?.entries?.firstOrNull { it.value == slot }?.key
                ?: return null
        return raid
            .participant(uid)
            ?.takeIf { it.connected && !it.spectating && it.currentRoom == ToaRoom.APMEKEN }
            ?.uid
            ?.resolve(players)
            ?.takeIf {
                raid.challenge?.stage == ToaChallengeStage.ACTIVE &&
                    ToaChallengeRoomRules.isInside(raid, instances, ToaRoom.APMEKEN, it.coords)
            }
    }

    private fun apmekenCoord(raid: ToaRaidSession, tile: ApmekenTile): CoordGrid =
        requireNotNull(
            instances.localCoord(
                raid.instance,
                ToaInstanceLayout.roomLocal(ToaRoom.APMEKEN, tile.x, tile.z),
            )
        ) {
            "Missing Apmeken local coordinate ${tile.x},${tile.z}"
        }

    private fun apmekenLocalTile(raid: ToaRaidSession, tile: CoordGrid): ApmekenTile {
        val origin = apmekenCoord(raid, ApmekenTile(0, 0))
        return ApmekenTile(tile.x - origin.x, tile.z - origin.z)
    }

    private fun completePathBoss(raid: ToaRaidSession, hero: Player) {
        val path = raid.currentPath ?: return
        if (path in raid.completedPaths || raid.completionPending) return
        forEachOnline(raid) { player ->
            resetCombat(player)
            PlayerPoison.clear(player)
            PlayerVenom.clear(player)
        }
        settleRewardPoints(raid, path.boss)
        clearEncounterNpcs(raid)
        raid.encounterStarted = false
        raid.completionPending = true
        raid.participants.eligible().forEach { it.spectating = false }
        spawnNpc(
            raid,
            ToaNpcs.COMPLETION_SPIRIT,
            spiritCoord(raid, path.boss),
            ToaNpcRole.COMPLETION_SPIRIT,
        )
        message(
            raid.party,
            "${path.boss.displayName} is defeated. Speak to Osmumten's spirit to return.",
        )
    }

    private fun recordPathCompletion(raid: ToaRaidSession, path: ToaPath) {
        if (!raid.completedPaths.add(path)) return
        raid.completedRooms += path.boss
        if (ToaInvocation.WALK_THE_PATH in raid.startSnapshot.invocations) {
            val seed = raid.startSnapshot.seed xor raid.completedPaths.size.toLong()
            val raised =
                ToaRules.levelRemainingPaths(raid.pathLevels, raid.completedPaths, Random(seed))
            if (raised.isNotEmpty()) {
                message(
                    raid.party,
                    "Walk the Path raises ${raised.joinToString { it.displayName }}.",
                )
            }
        }
        if (isToaWardensUnlocked(raid.completedPaths)) {
            syncWardensEntrance(raid, unlocked = true)
        }
    }

    private fun beginKephriRecovery(raid: ToaRaidSession, source: Npc) {
        val state = raid.encounter
        if (state.kephriRecovering || state.phase >= KephriEncounter.FINAL_PHASE) return
        val invocationRules =
            KephriInvocationRules.resolve(
                raid.startSnapshot.invocations,
                raid.startSnapshot.partySize,
            )
        state.phase++
        state.attackCounter = 0
        state.kephriBombTargets.clear()
        state.kephriBombs.clear()
        state.kephriPendingDungStrikes.clear()
        state.kephriEggBatches.clear()
        state.kephriEggs.values.toList().forEach { resolveKephriEgg(raid, it) }
        if (state.phase == KephriEncounter.FINAL_PHASE) {
            state.kephriRecovering = false
            state.kephriAttacksUntilSpecial = KephriAttackCycle.FINAL_FIRST_DUNG_BOMBS
            state.kephriNextSpecialDung = true
            val hp =
                ToaRules.hitpoints(
                    KephriEncounter.FINAL_HITPOINTS,
                    raid.startSnapshot.partySize,
                    raid.raidLevel,
                    raid.pathLevels[ToaPath.SCABARAS] ?: 0,
                )
            source.baseHitpointsLvl = hp
            source.hitpoints = hp
            source.transmog(
                requireNotNull(ServerCacheManager.getNpc(KephriEncounter.FINAL)),
                Int.MAX_VALUE,
            )
            source.spotanim(KephriEncounter.SHIELD_BREAK_GRAPHIC)
            source.anim(KephriEncounter.SPECIAL_ANIMATION)
            source.ignoreCombatInteractions = true
            scheduleKephriDungStrike(
                raid,
                activePlayers(raid),
                raid.startSnapshot.partySize,
                telegraph = false,
            )
            processKephriDungStrikes(raid)
            raid.nextMechanicAt = clock.cycle + KephriAttackCycle.DUNG_DELAY
            message(raid.party, "Kephri's shield is shattered. Her final health is exposed.")
            return
        }
        state.kephriRecovering = true
        state.kephriRecoverySwarms = 0
        state.kephriRecoveryResolved = 0
        // Medic swarms already in flight remain valid during the shield intermission. They are
        // removed with all remaining swarms when the intermission ends.
        val wave = KephriSwarms.wave(state.phase)
        state.kephriRecoveryTotal = wave.total
        state.kephriRecoveryHealCap = wave.healCap
        state.kephriSwarmStartLane =
            ((raid.party.id.leastSignificantBits xor state.phase.toLong()).toInt() and
                Int.MAX_VALUE) % KephriArenaLayout.swarmLanes.size
        state.kephriNextSwarmAt =
            clock.cycle + KephriSwarms.FIRST_SPAWN_DELAY + if (state.phase == 2) 1 else 0
        state.kephriNextBomberBombAt =
            if (invocationRules.aerialAssault) clock.cycle + KephriBomberScarabs.INITIAL_DELAY
            else -1
        source.abortRoute()
        source.clearInteraction()
        source.mode = null
        source.moveRestrict = MoveRestrict.NoMove
        source.ignoreCombatInteractions = true
        source.transmog(
            requireNotNull(ServerCacheManager.getNpc(KephriEncounter.RECOVERING)),
            Int.MAX_VALUE,
        )
        source.spotanim(KephriEncounter.SHIELD_BREAK_GRAPHIC)
        source.spotanim(KephriEncounter.RECOVERY_SHIELD_GRAPHIC, delay = 30)
        source.anim(KephriEncounter.STUN_ANIMATION)
        spawnKephriOverlords(raid, state.phase)
        message(raid.party, "Kephri is dazed as scarab swarms restore her shield.")
        raid.nextMechanicAt = Int.MAX_VALUE
    }

    private fun processKephriDeath(raid: ToaRaidSession) {
        val state = raid.encounter
        if (!state.kephriDying || clock.cycle < state.kephriCompletesAt) return
        val hero =
            state.kephriDeathHero?.resolve(players) ?: activePlayers(raid).firstOrNull() ?: return
        state.kephriDying = false
        state.kephriCompletesAt = -1
        completePathBoss(raid, hero)
    }

    private fun spawnKephriOverlords(raid: ToaRaidSession, phase: Int) {
        val center = roomCoord(raid, ToaRoom.KEPHRI, true)
        val invocationRules =
            KephriInvocationRules.resolve(
                raid.startSnapshot.invocations,
                raid.startSnapshot.partySize,
            )
        val ids = KephriOverlords.ids(phase, invocationRules.addMissingOverlord)
        ids.forEach { id ->
            val offset = KephriOverlords.offset(id)
            spawnNpc(
                    raid,
                    id,
                    CoordGrid(center.x + offset.first, center.z + offset.second, center.level),
                    ToaNpcRole.ADD,
                )
                .also { scarab ->
                    val hp =
                        ToaRules.hitpoints(
                            40,
                            raid.startSnapshot.partySize,
                            raid.raidLevel,
                            raid.pathLevels[ToaPath.SCABARAS] ?: 0,
                        )
                    scarab.baseHitpointsLvl = hp
                    scarab.hitpoints = hp
                    scarab.suppressHitpointsRegen = true
                    scarab.apRangeOverride =
                        when (id) {
                            KephriScarabMelee.NPC -> KephriScarabMelee.ATTACK_RANGE
                            KephriScarabRanger.NPC -> KephriScarabRanger.ATTACK_RANGE
                            else -> KephriScarabMager.ATTACK_RANGE
                        }
                    scarab.maxOpRangeOverride = 12
                    when (id) {
                        KephriScarabMager.NPC -> {
                            scarab.ignoreCombatInteractions = true
                            scarab.anim(KephriScarabMager.SPAWN_ANIMATION)
                            raid.encounter.kephriArcanes[scarab.hashCode()] =
                                KephriArcaneRuntime(
                                    actorKey = scarab.hashCode(),
                                    nextChargeAt = clock.cycle + 5,
                                )
                        }
                        KephriScarabMelee.NPC -> {
                            raid.encounter.kephriSoldiers[scarab.hashCode()] =
                                KephriSoldierRuntime(
                                    actorKey = scarab.hashCode(),
                                    nextHealAt = clock.cycle + 8,
                                )
                            nearestActive(raid, scarab.coords)?.let {
                                scarab.apPlayer2(it, interactions)
                            }
                        }
                        KephriScarabRanger.NPC -> {
                            scarab.ignoreCombatInteractions = true
                            raid.encounter.kephriSpitters[scarab.hashCode()] =
                                KephriSpittingRuntime(
                                    actorKey = scarab.hashCode(),
                                    nextAttackAt = clock.cycle + 5,
                                )
                        }
                        else ->
                            nearestActive(raid, scarab.coords)?.let {
                                scarab.apPlayer2(it, interactions)
                            }
                    }
                }
        }
    }

    private fun processKephriScarabs(raid: ToaRaidSession) {
        val active = activePlayers(raid)
        if (active.isEmpty()) return
        processKephriSoldiers(raid)
        processKephriSpitters(raid)
        processKephriArcanes(raid)
        raid.npcRoles.entries
            .asSequence()
            .filter { (npc, role) ->
                role == ToaNpcRole.ADD &&
                    npc.id in setOf(KephriScarabMelee.NPC, KephriScarabAgile.NPC) &&
                    npc.isSlotAssigned &&
                    npc.hitpoints > 0
            }
            .forEach { (scarab, _) ->
                val interaction = scarab.interaction as? InteractionPlayer
                val target = interaction?.uid?.resolve(players)
                if (
                    scarab.id == KephriScarabAgile.NPC &&
                        target != null &&
                        scarab.coords.chebyshevDistance(target.coords) <= 4
                ) {
                    val dx = (scarab.coords.x - target.coords.x).coerceIn(-1, 1)
                    val dz = (scarab.coords.z - target.coords.z).coerceIn(-1, 1)
                    if (dx != 0 || dz != 0) {
                        scarab.walk(
                            CoordGrid(
                                scarab.coords.x + dx * 2,
                                scarab.coords.z + dz * 2,
                                scarab.coords.level,
                            )
                        )
                    }
                }
                if (scarab.mode != NpcMode.ApPlayer2 || target !in active) {
                    nearestActive(raid, scarab.coords)?.let { scarab.apPlayer2(it, interactions) }
                }
            }
    }

    private fun processKephriSpitters(raid: ToaRaidSession) {
        val state = raid.encounter
        state.kephriSpitters.values.toList().forEach { runtime ->
            val spitter =
                raid.spawnedNpcs.firstOrNull {
                    it.hashCode() == runtime.actorKey && it.isSlotAssigned && it.hitpoints > 0
                }
                    ?: run {
                        state.kephriSpitters.remove(runtime.actorKey)
                        return@forEach
                    }
            if (clock.cycle < runtime.nextAttackAt) return@forEach
            val targets =
                activePlayers(raid).filter {
                    it.coords.chebyshevDistance(spitter.coords) <= KephriScarabRanger.ATTACK_RANGE
                }
            if (targets.isNotEmpty()) {
                spitter.anim(KephriScarabRanger.ANIMATION)
                targets.forEach { target ->
                    sendProjectile(spitter, target, KephriScarabRanger.PROJECTILE)
                    onKephriSpittingAttack(spitter, target)
                    val max = scaledDamage(raid, KephriScarabRanger.MAX_HIT)
                    val damage =
                        Random(
                                raid.startSnapshot.seed xor
                                    clock.cycle.toLong() xor
                                    (target.coords.x.toLong() shl 32) xor
                                    target.coords.z.toLong()
                            )
                            .nextInt(max + 1)
                    queueAkkhaCombatHit(
                        player = target,
                        source = spitter,
                        damage = damage,
                        delay = 2,
                        type = HitType.Ranged,
                        prayerReductionPercent = 66,
                    )
                }
            }
            runtime.nextAttackAt = clock.cycle + KephriScarabRanger.ATTACK_INTERVAL
        }
    }

    private fun processKephriSoldiers(raid: ToaRaidSession) {
        val source = boss(raid) ?: return
        val state = raid.encounter
        state.kephriSoldiers.values.toList().forEach { runtime ->
            val soldier =
                raid.spawnedNpcs.firstOrNull {
                    it.hashCode() == runtime.actorKey && it.isSlotAssigned && it.hitpoints > 0
                }
                    ?: run {
                        state.kephriSoldiers.remove(runtime.actorKey)
                        return@forEach
                    }
            if (clock.cycle < runtime.nextHealAt) return@forEach
            val cap =
                if (state.phase >= KephriEncounter.FINAL_PHASE) source.baseHitpointsLvl
                else KephriSwarms.shieldCap(source.baseHitpointsLvl)
            if (source.hitpoints < cap) {
                sendProjectile(
                    soldier,
                    CoordGrid(source.coords.x + 2, source.coords.z + 2, source.coords.level),
                    KephriScarabMelee.HEAL_PROJECTILE,
                    travelTicks = 1,
                )
                val heal = KephriScarabMelee.healAmount(soldier.hitpoints, soldier.baseHitpointsLvl)
                source.hitpoints = (source.hitpoints + heal).coerceAtMost(cap)
            }
            runtime.nextHealAt = clock.cycle + KephriScarabMelee.HEAL_INTERVAL
        }
    }

    private fun processKephriArcanes(raid: ToaRaidSession) {
        val state = raid.encounter
        val active = activePlayers(raid)
        if (active.isEmpty()) return
        val anchor = roomCoord(raid, ToaRoom.KEPHRI, true)
        val locations = listOf(7 to -5, -5 to 7, 1 to -5, 7 to 7)
        val ring = listOf(0 to 0, 0 to 1, 0 to 2, 1 to 2, 2 to 2, 2 to 1, 2 to 0, 1 to 0)
        state.kephriArcanes.values.toList().forEach { runtime ->
            val arcane =
                raid.spawnedNpcs.firstOrNull {
                    it.hashCode() == runtime.actorKey && it.isSlotAssigned && it.hitpoints > 0
                }
                    ?: run {
                        state.kephriArcanes.remove(runtime.actorKey)
                        return@forEach
                    }
            if (runtime.impactsAt >= 0) {
                if (clock.cycle < runtime.impactsAt) return@forEach
                val target = runtime.target?.resolve(players)
                if (target != null) {
                    queueAkkhaCombatHit(
                        player = target,
                        source = arcane,
                        damage = scaledDamage(raid, KephriScarabMager.PRAYER_DAMAGE),
                        delay = 1,
                        type = HitType.Magic,
                        prayerReductionPercent = 66,
                    )
                }
                runtime.target = null
                runtime.impactsAt = -1
                runtime.chargeStep = 0
                runtime.locationIndex = (runtime.locationIndex + 1) % locations.size
                val (dx, dz) = locations[runtime.locationIndex]
                PathingEntityCommon.telejump(
                    arcane,
                    collision,
                    CoordGrid(anchor.x + dx, anchor.z + dz, anchor.level),
                )
                arcane.anim(KephriScarabMager.SPAWN_ANIMATION)
                runtime.nextChargeAt = clock.cycle + 5
                return@forEach
            }
            if (clock.cycle < runtime.nextChargeAt) return@forEach
            if (runtime.chargeStep < KephriScarabMager.CHARGE_STEPS) {
                val (dx, dz) = ring[runtime.chargeStep]
                world.spotanimMap(
                    SpotanimType(KephriScarabMager.CHARGE_GRAPHIC),
                    CoordGrid(arcane.coords.x + dx, arcane.coords.z + dz, arcane.coords.level),
                )
                runtime.chargeStep++
                runtime.nextChargeAt = clock.cycle + KephriScarabMager.CHARGE_INTERVAL
                return@forEach
            }
            ring.forEach { (dx, dz) ->
                world.spotanimMap(
                    SpotanimType(KephriScarabMager.CHARGED_GRAPHIC),
                    CoordGrid(arcane.coords.x + dx, arcane.coords.z + dz, arcane.coords.level),
                )
            }
            arcane.anim(KephriScarabMager.DETONATE_ANIMATION)
            val target = active.minByOrNull { it.coords.chebyshevDistance(arcane.coords) }
            if (target != null) {
                sendProjectile(arcane, target, KephriScarabMager.CHARGE_GRAPHIC)
                runtime.target = target.uid
                runtime.impactsAt = clock.cycle + KephriScarabMager.IMPACT_DELAY - 1
            } else {
                runtime.nextChargeAt = clock.cycle + 1
            }
        }
    }

    fun returnFromCompletionSpirit(player: Player, spirit: Npc): Boolean {
        val raid = raidFor(spirit) ?: return false
        if (raidFor(player) !== raid || raid.npcRoles[spirit] != ToaNpcRole.COMPLETION_SPIRIT)
            return false
        return returnToLobby(player, raid, completeRoom = true)
    }

    private fun spawnWardenObeliskPhase(raid: ToaRaidSession) {
        val state = raid.encounter
        state.wardenStage = ToaWardenStage.OBELISK
        state.wardenElidinisCharge = 0
        state.wardenTumekenCharge = 0
        state.wardenObeliskActivated = false
        state.wardenElidinisChargeCycles = 0
        state.wardenTumekenChargeCycles = 0
        state.wardenChargeBalls.clear()
        clearWardenChargeCircle(raid)
        state.wardenNextEnergyPulseAt = -1
        state.wardenNextPassiveChargeAt = -1
        state.wardenPhaseOneSpecials.clear()
        state.wardenPhaseOneSpecialEndsAt.clear()
        state.wardenPhaseOneSpecialQueue.clear()
        clearWardenPhaseOneHazards(raid)
        val obelisk =
            spawnNpc(
                raid,
                WardenObelisk.INACTIVE,
                wardenRoomCoord(
                    raid,
                    ToaRoom.WARDENS,
                    WardenPhaseOneLayout.OBELISK_X,
                    WardenPhaseOneLayout.OBELISK_Z,
                ),
                ToaNpcRole.OBELISK,
            )
        val hp =
            ToaRules.hitpoints(
                WardenObelisk.BASE_HITPOINTS,
                raid.startSnapshot.partySize,
                raid.raidLevel,
                0,
            )
        obelisk.baseHitpointsLvl = hp
        obelisk.hitpoints = hp
        obelisk.suppressHitpointsRegen = true
        lockWardenActor(obelisk)
        state.wardenObeliskActor = obelisk.hashCode()
        val elidinis =
            spawnNpc(
                    raid,
                    ElidinisWarden.INACTIVE,
                    wardenRoomCoord(
                        raid,
                        ToaRoom.WARDENS,
                        WardenPhaseOneLayout.ELIDINIS_X,
                        WardenPhaseOneLayout.WARDEN_Z,
                    ),
                    ToaNpcRole.HAZARD,
                    Direction.East,
                )
                .also(::lockWardenActor)
        state.wardenElidinisChargeActor = elidinis.hashCode()
        val tumeken =
            spawnNpc(
                    raid,
                    TumekenWarden.INACTIVE,
                    wardenRoomCoord(
                        raid,
                        ToaRoom.WARDENS,
                        WardenPhaseOneLayout.TUMEKEN_X,
                        WardenPhaseOneLayout.WARDEN_Z,
                    ),
                    ToaNpcRole.HAZARD,
                    Direction.West,
                )
                .also(::lockWardenActor)
        state.wardenTumekenChargeActor = tumeken.hashCode()
        syncWardenChargePlatform(raid, WardenGod.ELIDINIS, 0)
        syncWardenChargePlatform(raid, WardenGod.TUMEKEN, 0)
    }

    private fun activateWardenChargeActors(raid: ToaRaidSession) {
        WardenGod.entries.forEach { god ->
            val identity = god.chargeActorIdentity()
            val actor = wardenChargeActor(raid, god)
            if (actor == null) {
                log.error {
                    "[ToA] missing ${god.displayName()} phase-one Warden actor " +
                        "instance=${raid.instance.id} expected=${identity.originalId}"
                }
                return@forEach
            }
            actor.transmog(
                requireNotNull(ServerCacheManager.getNpc(identity.chargingVisualId)),
                Int.MAX_VALUE,
            )
            lockWardenActor(actor)
        }
        updateWardenChargeHeadbars(raid)
    }

    private fun activateWardenEncounter(raid: ToaRaidSession) {
        val state = raid.encounter
        if (state.wardenStage != ToaWardenStage.OBELISK || state.wardenObeliskActivated) return
        val obelisk =
            raid.spawnedNpcs.firstOrNull {
                it.hashCode() == state.wardenObeliskActor && raid.npcRoles[it] == ToaNpcRole.OBELISK
            }
        if (obelisk == null) {
            log.error { "[ToA] cannot activate Wardens without the phase-one Obelisk" }
            return
        }
        obelisk.transmog(
            requireNotNull(ServerCacheManager.getNpc(WardenObelisk.ATTACKABLE)),
            Int.MAX_VALUE,
        )
        state.wardenObeliskActivated = true
        activateWardenChargeActors(raid)
        state.wardenNextEnergyPulseAt = clock.cycle + 1
        state.wardenNextPassiveChargeAt =
            clock.cycle + state.wardensRuleset.phaseOnePassiveChargeInterval
        state.attackCounter = 0
        raid.nextMechanicAt = clock.cycle + 1
    }

    private fun beginWardenPhaseTwo(raid: ToaRaidSession, hero: Player) {
        val state = raid.encounter
        state.wardenPhaseTwoGod =
            WardenObelisk.phaseTwoWarden(
                state.wardenElidinisChargeCycles * 100 + state.wardenElidinisCharge,
                state.wardenTumekenChargeCycles * 100 + state.wardenTumekenCharge,
            )
        state.wardenPhaseThreeGod =
            if (state.wardenPhaseTwoGod == WardenGod.ELIDINIS) WardenGod.TUMEKEN
            else WardenGod.ELIDINIS
        state.wardenLastForm =
            if (state.wardenPhaseTwoGod == WardenGod.ELIDINIS) ElidinisWarden.MAGIC_FORM
            else TumekenWarden.RANGED_FORM
        state.wardenPhaseTwoTrueMax =
            ToaRules.hitpoints(
                WardenCores.PHASE_TWO_TRUE_HITPOINTS,
                raid.startSnapshot.partySize,
                raid.raidLevel,
                0,
            )
        state.wardenPhaseTwoRemaining = state.wardenPhaseTwoTrueMax
        state.wardenCoreDowns = 0
        state.attackCounter = 0
        state.wardenObeliskSpecialStep = 0
        clearEncounterNpcs(raid)
        spawnNpc(
                raid,
                WardenObelisk.EXHAUSTED,
                wardenRoomCoord(
                    raid,
                    ToaRoom.WARDENS,
                    WardenPhaseOneLayout.OBELISK_X,
                    WardenPhaseOneLayout.OBELISK_Z,
                ),
                ToaNpcRole.HAZARD,
            )
            .also { exhausted ->
                lockWardenActor(exhausted)
                state.wardenObeliskActor = exhausted.hashCode()
            }
        state.wardenStage = ToaWardenStage.PHASE_TWO
        spawnWardenPhaseTwoShield(raid, reviving = false)
        state.wardenNextPhaseTwoAttackAt =
            clock.cycle + state.wardensRuleset.phaseTwoFirstAttackDelay
        state.wardenNextPhaseTwoObeliskAttackAt =
            clock.cycle + state.wardensRuleset.phaseTwoObeliskAttackInterval
        raid.nextMechanicAt =
            WardenPhaseTwoCadence.nextMechanicAt(
                state.wardenNextPhaseTwoAttackAt,
                state.wardenNextPhaseTwoObeliskAttackAt,
            )
        message(
            raid.party,
            "${state.wardenPhaseTwoGod.displayName()} Warden powers up. Follow its protection prayers.",
        )
    }

    private fun advanceWarden(raid: ToaRaidSession, hero: Player, defeated: Npc) {
        when {
            raid.currentRoom == ToaRoom.WARDENS &&
                raid.encounter.wardenStage == ToaWardenStage.PHASE_TWO -> {
                exposeWardenCore(raid, defeated.coords)
            }
            raid.currentRoom == ToaRoom.TUMEKEN_WARDEN -> beginWardenDeathSequence(raid, defeated)
        }
    }

    private fun applyWardenShieldImpact(
        raid: ToaRaidSession,
        hero: Player,
        warden: Npc,
        damage: Int,
    ) {
        val state = raid.encounter
        if (
            damage <= 0 ||
                raid.currentRoom != ToaRoom.WARDENS ||
                state.wardenStage != ToaWardenStage.PHASE_TWO ||
                raid.npcRoles[warden] != ToaNpcRole.WARDEN_PHASE
        ) {
            return
        }
        state.wardenPhaseTwoShieldCharge =
            (state.wardenPhaseTwoShieldCharge + damage).coerceAtMost(
                WardenCores.PHASE_TWO_SHIELD_HITPOINTS
            )
        showWardenChargeHeadbar(
            warden,
            state.wardenPhaseTwoShieldCharge,
            WardenCores.PHASE_TWO_SHIELD_HITPOINTS,
        )
        syncWardenPhaseTwoShieldHud(raid, warden)
        if (state.wardenPhaseTwoShieldCharge < WardenCores.PHASE_TWO_SHIELD_HITPOINTS) return

        val bodyTile = warden.coords
        warden.clearQueue("queue.death")
        if (warden in raid.spawnedNpcs) removeNpc(raid, warden)
        exposeWardenCore(raid, bodyTile)
    }

    private fun spawnWardenPhaseTwoShield(raid: ToaRaidSession, reviving: Boolean) {
        val state = raid.encounter
        val shield = spawnBoss(raid, state.wardenLastForm, ToaNpcRole.WARDEN_PHASE)
        val hp = WardenCores.PHASE_TWO_SHIELD_HITPOINTS
        state.wardenPhaseTwoShieldCharge = 0
        shield.baseHitpointsLvl = hp
        shield.hitpoints = hp
        shield.cachedHitmark = WardenPhaseTwoShieldHud.HITMARK
        shield.suppressHitpointsRegen = true
        // Phase two damage fills the yellow shield/stagger bar. The Warden's true health is
        // represented by the normal green/red headbar only while its core is exposed.
        shield.suppressHitpointHeadbar = true
        lockWardenActor(shield)
        showWardenChargeHeadbar(shield, 0, hp)
        syncWardenPhaseTwoShieldHud(raid, shield)
        shield.faceSquare(fightCoord(raid, ToaRoom.WARDENS))
        if (reviving) shield.anim(WardenPhaseTwoMechanics.CORE_REVIVE_ANIMATION)
    }

    private fun syncWardenPhaseTwoShieldHud(raid: ToaRaidSession, warden: Npc) {
        val charge = WardenPhaseTwoShieldHud.currentValue(raid.encounter.wardenPhaseTwoShieldCharge)
        activePlayers(raid).forEach { player ->
            bossHpBar.onOpenCustom(
                player,
                warden,
                charge,
                WardenCores.PHASE_TWO_SHIELD_HITPOINTS,
                WardenPhaseTwoShieldHud.STYLE,
            )
        }
    }

    private fun exposeWardenCore(raid: ToaRaidSession, bodyTile: CoordGrid) {
        val state = raid.encounter
        state.wardenStage = ToaWardenStage.CORE
        state.wardenAreaPulses.clear()
        state.pendingHits.clear()
        state.wardenCoreReturnsAt = -1
        state.wardenCoreProjectileAt = clock.cycle + 5
        state.wardenCoreSpawnsAt = clock.cycle + 7
        val bodyId =
            if (state.wardenPhaseTwoGod == WardenGod.ELIDINIS) ElidinisWarden.CORE_EJECTED
            else TumekenWarden.CORE_EJECTED
        val body =
            spawnNpc(raid, bodyId, bodyTile, ToaNpcRole.HAZARD).also {
                it.baseHitpointsLvl = state.wardenPhaseTwoTrueMax
                it.hitpoints = state.wardenPhaseTwoRemaining
                it.suppressHitpointsRegen = true
                it.suppressHitpointHeadbar = false
                lockWardenActor(it)
                it.anim(WardenPhaseTwoMechanics.CORE_EJECT_ANIMATION)
            }
        updateWardenCoreHealthBar(raid, body)
        state.wardenCorePendingBody = body.hashCode()
        state.wardenCorePendingTile =
            CoordGrid(
                bodyTile.x + WardenPhaseTwoMechanics.CORE_X_OFFSET,
                bodyTile.z + WardenPhaseTwoMechanics.CORE_Z_OFFSET,
                bodyTile.level,
            )
        raid.nextMechanicAt = state.wardenCoreProjectileAt
        message(raid.party, "The Warden begins ejecting its core.")
    }

    private fun processPendingWardenCore(raid: ToaRaidSession) {
        val state = raid.encounter
        if (state.wardenStage != ToaWardenStage.CORE) return
        val body =
            raid.spawnedNpcs.firstOrNull {
                it.hashCode() == state.wardenCorePendingBody && it.isSlotAssigned
            } ?: return
        val coreTile = state.wardenCorePendingTile ?: return
        if (state.wardenCoreProjectileAt >= 0 && clock.cycle >= state.wardenCoreProjectileAt) {
            sendProjectile(
                body,
                coreTile,
                WardenPhaseTwoMechanics.coreProjectile(state.wardenPhaseTwoGod),
                travelTicks = 2,
            )
            state.wardenCoreProjectileAt = -1
            raid.nextMechanicAt = state.wardenCoreSpawnsAt
        }
        if (state.wardenCoreSpawnsAt < 0 || clock.cycle < state.wardenCoreSpawnsAt) return
        val coreId =
            if (state.wardenPhaseTwoGod == WardenGod.ELIDINIS) WardenCores.ELIDINIS
            else WardenCores.TUMEKEN
        spawnNpc(raid, coreId, coreTile, ToaNpcRole.CORE).also { core ->
            val hp = WardenCores.coreHitpoints(state.wardenPhaseTwoRemaining)
            core.baseHitpointsLvl = hp
            core.hitpoints = hp
            core.suppressHitpointsRegen = true
            core.suppressHitpointHeadbar = true
            state.wardenCoreStartHitpoints = hp
            lockWardenActor(core)
        }
        val remainingPercent =
            state.wardenPhaseTwoRemaining * 100 / state.wardenPhaseTwoTrueMax.coerceAtLeast(1)
        state.wardenCoreReturnsAt = clock.cycle + WardenCores.exposureTicks(remainingPercent)
        state.wardenCoreSpawnsAt = -1
        state.wardenCorePendingBody = -1
        state.wardenCorePendingTile = null
        raid.nextMechanicAt = state.wardenCoreReturnsAt
        message(
            raid.party,
            "The Warden's core is exposed. Melee attacks always deal their maximum hit.",
        )
    }

    private fun advanceWardenCore(raid: ToaRaidSession, hero: Player, core: Npc) {
        if (
            raid.currentRoom != ToaRoom.WARDENS || raid.encounter.wardenStage != ToaWardenStage.CORE
        )
            return
        resolveWardenCore(raid, core, hero)
    }

    private fun applyWardenCoreImpact(raid: ToaRaidSession, hero: Player, core: Npc, damage: Int) {
        val state = raid.encounter
        if (
            damage <= 0 ||
                raid.currentRoom != ToaRoom.WARDENS ||
                state.wardenStage != ToaWardenStage.CORE ||
                raid.npcRoles[core] != ToaNpcRole.CORE
        ) {
            return
        }
        state.wardenPhaseTwoRemaining =
            WardenCores.hiddenHitpointsAfterCoreDamage(state.wardenPhaseTwoRemaining, damage)
        wardenCoreBody(raid)?.let { updateWardenCoreHealthBar(raid, it) }
        if (state.wardenPhaseTwoRemaining > 0) return

        core.clearQueue("queue.death")
        resolveWardenCore(raid, core, hero)
    }

    private fun wardenCoreBody(raid: ToaRaidSession): Npc? =
        raid.npcRoles.entries
            .firstOrNull { (npc, role) ->
                role == ToaNpcRole.HAZARD &&
                    npc.id in setOf(ElidinisWarden.CORE_EJECTED, TumekenWarden.CORE_EJECTED)
            }
            ?.key

    private fun updateWardenCoreHealthBar(raid: ToaRaidSession, body: Npc) {
        val state = raid.encounter
        body.baseHitpointsLvl = state.wardenPhaseTwoTrueMax
        body.hitpoints = state.wardenPhaseTwoRemaining
        showWardenTrueHealthHeadbar(body)
        activePlayers(raid).forEach { player ->
            bossHpBar.onOpenCustom(
                player,
                body,
                state.wardenPhaseTwoRemaining,
                state.wardenPhaseTwoTrueMax,
                BossHpBarScript.ORIGINAL_STYLE,
            )
        }
    }

    private fun showWardenTrueHealthHeadbar(warden: Npc) {
        val maximum = warden.baseHitpointsLvl.coerceAtLeast(1)
        val headbar = warden.visHeadbar(params.headbar)
        val fill =
            (warden.hitpoints.coerceIn(0, maximum) * headbar.segments / maximum).coerceIn(
                0,
                headbar.segments,
            )
        warden.showHeadbar(
            Headbar.fromNoSource(
                self = headbar.id,
                public = headbar.id,
                startFill = fill,
                endFill = fill,
                startTime = 0,
                endTime = 0,
            )
        )
    }

    private fun resolveWardenCore(raid: ToaRaidSession, core: Npc, hero: Player?) {
        val state = raid.encounter
        if (raid.currentRoom != ToaRoom.WARDENS || state.wardenStage != ToaWardenStage.CORE) {
            return
        }
        state.wardenCoreDowns++
        val phaseTwoBodies =
            raid.npcRoles
                .filter { (npc, role) ->
                    role == ToaNpcRole.HAZARD &&
                        npc.id in setOf(ElidinisWarden.CORE_EJECTED, TumekenWarden.CORE_EJECTED)
                }
                .keys
                .toList()
        state.wardenCoreReturnsAt = -1
        state.wardenCoreProjectileAt = -1
        state.wardenCoreSpawnsAt = -1
        state.wardenCorePendingBody = -1
        state.wardenCorePendingTile = null
        state.wardenCoreStartHitpoints = 0
        if (state.wardenPhaseTwoRemaining <= 0) {
            closeWardenPhaseTwoBossBars(raid, phaseTwoBodies)
            if (core in raid.spawnedNpcs) removeNpc(raid, core)
            beginWardenPhaseThree(raid, phaseTwoBodies.firstOrNull())
            return
        }
        if (core in raid.spawnedNpcs) removeNpc(raid, core)
        phaseTwoBodies.forEach { removeNpc(raid, it) }
        state.wardenLastForm =
            when (state.wardenLastForm) {
                ElidinisWarden.MAGIC_FORM -> ElidinisWarden.RANGED_FORM
                ElidinisWarden.RANGED_FORM -> ElidinisWarden.MAGIC_FORM
                TumekenWarden.RANGED_FORM -> TumekenWarden.MAGIC_FORM
                else -> TumekenWarden.RANGED_FORM
            }
        state.wardenStage = ToaWardenStage.PHASE_TWO
        spawnWardenPhaseTwoShield(raid, reviving = true)
        state.wardenNextPhaseTwoAttackAt =
            clock.cycle + state.wardensRuleset.phaseTwoRecoveryAttackDelay
        state.wardenNextPhaseTwoObeliskAttackAt =
            clock.cycle + state.wardensRuleset.phaseTwoObeliskAttackInterval
        raid.nextMechanicAt =
            WardenPhaseTwoCadence.nextMechanicAt(
                state.wardenNextPhaseTwoAttackAt,
                state.wardenNextPhaseTwoObeliskAttackAt,
            )
        message(
            raid.party,
            "The core retreats with ${state.wardenPhaseTwoRemaining} true health remaining.",
        )
    }

    private fun closeWardenPhaseTwoBossBars(raid: ToaRaidSession, actors: Collection<Npc>) {
        actors.forEach { actor ->
            forEachOnline(raid) { player -> bossHpBar.onClose(player, actor, instant = true) }
        }
    }

    private fun beginWardenPhaseThree(raid: ToaRaidSession, phaseTwoBody: Npc?) {
        val state = raid.encounter
        if (state.wardenStage == ToaWardenStage.PHASE_THREE_TRANSITION) return
        cleanupWardenEffects(raid)
        closeWardenPhaseTwoBossBars(raid, listOfNotNull(phaseTwoBody))
        state.wardenStage = ToaWardenStage.PHASE_THREE_TRANSITION
        state.wardenPhaseThreeTransitionEndsAt =
            clock.cycle + WardenPhaseThreeTransition.DURATION_TICKS
        state.wardenPendingSlam = null
        phaseTwoBody?.anim(WardenPhaseThreeTransition.SURVIVING_WARDEN_ANIMATION)

        val destroyedId =
            if (state.wardenPhaseThreeGod == WardenGod.ELIDINIS) ElidinisWarden.DESTROYED
            else TumekenWarden.DESTROYED
        val destroyedLocalX =
            if (state.wardenPhaseThreeGod == WardenGod.ELIDINIS) WardenPhaseOneLayout.ELIDINIS_X
            else WardenPhaseOneLayout.TUMEKEN_X
        spawnNpc(
                raid,
                destroyedId,
                wardenRoomCoord(
                    raid,
                    ToaRoom.WARDENS,
                    destroyedLocalX,
                    WardenPhaseOneLayout.WARDEN_Z,
                ),
                ToaNpcRole.HAZARD,
            )
            .also { destroyed ->
                lockWardenActor(destroyed)
                destroyed.anim(WardenPhaseThreeTransition.DESTROYED_WARDEN_ANIMATION)
                destroyed.spotanim(WardenPhaseThreeTransition.DESTROYED_WARDEN_SPOTANIM)
            }

        val camera =
            wardenRoomCoord(
                raid,
                ToaRoom.WARDENS,
                WardenPhaseThreeTransition.CAMERA_LOCAL_X,
                WardenPhaseThreeTransition.CAMERA_LOCAL_Z,
            )
        val focus =
            wardenRoomCoord(
                raid,
                ToaRoom.WARDENS,
                WardenPhaseThreeTransition.FOCUS_LOCAL_X,
                WardenPhaseThreeTransition.FOCUS_LOCAL_Z,
            )
        activePlayers(raid).forEach { player ->
            player.combatClearQueue()
            player.clearInteractionRoute()
            protectedAccess.launchLenient(player) {
                camModeFixed()
                minimapHideMap()
                try {
                    camMoveTo(
                        camera,
                        height = WardenPhaseThreeTransition.CAMERA_HEIGHT,
                        rate = 100,
                        rate2 = 100,
                    )
                    camLookAt(
                        focus,
                        height = WardenPhaseThreeTransition.FOCUS_HEIGHT,
                        rate = 100,
                        rate2 = 100,
                    )
                    delay(WardenPhaseThreeTransition.DURATION_TICKS)
                } finally {
                    camReset()
                    camModeReset()
                    minimapReset()
                }
            }
        }
        raid.nextMechanicAt = state.wardenPhaseThreeTransitionEndsAt
    }

    private fun processWardenPhaseThreeTransition(raid: ToaRaidSession) {
        val state = raid.encounter
        if (
            state.wardenStage != ToaWardenStage.PHASE_THREE_TRANSITION ||
                state.wardenPhaseThreeTransitionEndsAt < 0 ||
                clock.cycle < state.wardenPhaseThreeTransitionEndsAt
        ) {
            return
        }
        clearEncounterNpcs(raid)
        raid.completedRooms += ToaRoom.WARDENS
        raid.currentRoom = ToaRoom.TUMEKEN_WARDEN
        raid.wardenPhase = 1
        state.wardenStage = ToaWardenStage.PHASE_THREE
        state.wardenPhaseThreeTransitionEndsAt = -1
        state.attackCounter = 0
        state.wardenCoreReturnsAt = -1
        state.wardenSkullsSent = 0
        state.wardenSiphonsSpawned = 0
        state.wardenSiphonPartySize = 1
        state.wardenSkullsActive = false
        state.wardenSiphonEndsAt = -1
        state.wardenEnraged = false
        state.wardenSlamStep = 0
        state.wardenPendingSlam = null
        state.wardenSlamInterruptedAt = -1
        state.wardenEnrageStartedAt = -1
        state.wardenNextLightningAt = -1
        state.wardenPhantomsSpawned = 0
        state.wardenAkkhaPhantomStyle = 0
        state.wardenNextSlamAt = clock.cycle + state.wardensRuleset.phaseThreeInitialSlamDelay
        state.wardenPhantomNextAttackAt.clear()
        state.wardenNextPhantomAttackAt = Int.MAX_VALUE
        state.wardenPhantomAttackCounter = 0
        teleportParty(raid, ToaRoom.TUMEKEN_WARDEN, cancelActiveCoroutine = false)
        spawnWardenPhaseThree(raid)
        boss(raid)?.spotanim(WardenPhaseThreeTransition.FINAL_WARDEN_SPOTANIM)
        raid.nextMechanicAt = state.wardenNextSlamAt
        message(
            raid.party,
            "The core is destroyed. ${state.wardenPhaseThreeGod.displayName()} Warden awakens for the final phase.",
        )
    }

    private fun spawnWardenPhaseThree(raid: ToaRaidSession) {
        val state = raid.encounter
        val id =
            if (state.wardenPhaseThreeGod == WardenGod.ELIDINIS) ElidinisWarden.FINAL_FORM
            else TumekenWarden.FINAL_FORM
        spawnBoss(raid, id, ToaNpcRole.WARDEN_PHASE).also { warden ->
            val hp =
                ToaRules.hitpoints(
                    TumekenWarden.FINAL_BASE_HITPOINTS,
                    raid.startSnapshot.partySize,
                    raid.raidLevel,
                    0,
                )
            warden.baseHitpointsLvl = hp
            warden.hitpoints = hp
            warden.suppressHitpointsRegen = true
            lockWardenActor(warden)
            warden.faceSquare(fightCoord(raid, ToaRoom.TUMEKEN_WARDEN))
        }
    }

    private fun completeWardens(raid: ToaRaidSession) {
        cleanupWardenEffects(raid)
        clearEncounterNpcs(raid)
        raid.encounterStarted = false
        raid.completionPending = true
        raid.wardenPhase = 2
        raid.completedRooms += ToaRoom.WARDENS
        raid.completedRooms += ToaRoom.TUMEKEN_WARDEN
        val passageOpened = syncRewardEntry(raid)
        message(
            raid.party,
            if (passageOpened) {
                "Tumeken's Warden is defeated. Enter the white light to reach the treasure room."
            } else {

                "Tumeken's Warden is defeated, but the treasure-room light failed to appear. Please report this to an administrator."
            },
        )
    }

    private fun beginWardenDeathSequence(raid: ToaRaidSession, defeated: Npc) {
        val state = raid.encounter
        if (state.wardenStage == ToaWardenStage.DEATH_SEQUENCE) return
        defeated.clearQueue("queue.death")
        lockWardenActor(defeated)
        defeated.spotanim(WardenPhaseThreeTransition.FINAL_WARDEN_SPOTANIM)
        state.wardenStage = ToaWardenStage.DEATH_SEQUENCE
        state.wardenDeathStartedAt = clock.cycle
        state.wardenDeathSource = defeated.hashCode()
        state.wardenDeathBossRemoved = false
        state.wardenDeathPhantomsRemoved = false
        state.wardenDeathBossBarCleared = false
        state.wardenDeathLightOpened = false
        state.wardenPendingSlam = null
        state.wardenAreaPulses.clear()
        state.pendingHits.clear()
        state.wardenPhantomNextAttackAt.clear()
        state.wardenNextFloorBreakAt = Int.MAX_VALUE
        state.wardenNextLightningAt = Int.MAX_VALUE
        state.wardenNextPhantomAttackAt = Int.MAX_VALUE
        activePlayers(raid).forEach {
            it.clearQueue("queue.hit")
            it.client.write(CamReset)
        }
        raid.completedAt = clock.cycle
        raid.completionPending = true
        raid.nextMechanicAt = clock.cycle + 1
    }

    private fun processWardenDeathSequence(raid: ToaRaidSession) {
        val state = raid.encounter
        if (state.wardenStage != ToaWardenStage.DEATH_SEQUENCE) return
        val elapsed = clock.cycle - state.wardenDeathStartedAt
        val source =
            raid.spawnedNpcs.firstOrNull { it.hashCode() == state.wardenDeathSource }
                ?: raid.npcRoles.keys.firstOrNull { it.hashCode() == state.wardenDeathSource }
        if (elapsed >= WardenDeathSequence.BOSS_REMOVAL_TICKS && !state.wardenDeathBossRemoved) {
            source?.let { warden ->
                prepareNpcRemoval(warden)
                if (warden.isSlotAssigned) npcs.del(warden, Int.MAX_VALUE)
            }
            state.wardenDeathBossRemoved = true
        }
        if (
            elapsed >= WardenDeathSequence.PHANTOM_REMOVAL_TICKS &&
                !state.wardenDeathPhantomsRemoved
        ) {
            raid.npcRoles
                .filter { (npc, role) ->
                    role == ToaNpcRole.HAZARD &&
                        npc.id in
                            setOf(
                                TumekenWarden.ZEBAK_PHANTOM,
                                TumekenWarden.BABA_PHANTOM,
                                TumekenWarden.KEPHRI_PHANTOM,
                                TumekenWarden.AKKHA_PHANTOM,
                            )
                }
                .keys
                .toList()
                .forEach { phantom ->
                    state.wardenPhantomNextAttackAt.remove(phantom.hashCode())
                    removeNpc(raid, phantom)
                }
            state.wardenDeathPhantomsRemoved = true
        }
        if (
            elapsed >= WardenDeathSequence.BOSS_BAR_CLOSE_TICKS && !state.wardenDeathBossBarCleared
        ) {
            source?.let { warden ->
                forEachOnline(raid) { player -> bossHpBar.onClose(player, warden, instant = true) }
            }
            state.wardenDeathBossBarCleared = true
        }
        if (elapsed >= WardenDeathSequence.REWARD_LIGHT_TICKS && !state.wardenDeathLightOpened) {
            state.wardenDeathLightOpened = true
            completeWardens(raid)
            return
        }
        raid.nextMechanicAt = clock.cycle + 1
    }

    private fun finishWardenSiphonsIfClear(raid: ToaRaidSession) {
        if (raid.npcRoles.values.any { it == ToaNpcRole.SKULL }) return
        finishWardenSiphons(raid, success = true)
    }

    private fun finishWardenSiphons(raid: ToaRaidSession, success: Boolean) {
        val state = raid.encounter
        val disabled =
            raid.npcRoles
                .filter { (npc, role) ->
                    role == ToaNpcRole.HAZARD && npc.id == WardenEnergySiphons.DISABLED
                }
                .keys
                .toList()
        val returnTarget =
            wardenRoomCoord(
                raid,
                ToaRoom.TUMEKEN_WARDEN,
                WardenEnergySiphons.RETURN_LOCAL_X,
                WardenEnergySiphons.RETURN_LOCAL_Z,
            )
        disabled.forEach { siphon ->
            sendProjectile(
                siphon,
                returnTarget,
                WardenEnergySiphons.OUTWARD_PROJECTILE,
                travelTicks = WardenEnergySiphons.RETURN_TRAVEL_TICKS,
            )
        }

        state.wardenSkullsActive = false
        state.wardenSiphonEndsAt = -1
        state.wardenSiphonProjectileAt = -1
        state.wardenSiphonsSpawnAt = -1
        state.wardenSiphonLinkAt = -1
        state.wardenSiphonPhase = -1
        state.wardenStage =
            if (state.wardenEnraged) ToaWardenStage.ENRAGE else ToaWardenStage.PHASE_THREE
        state.wardenSlamInterruptedAt = -1
        state.wardenPendingSlam = null
        if (state.wardensRuleset.resetsSlamAfterSiphons) {
            state.wardenSlamStep = 0
        }
        val resumeAt = clock.cycle + state.wardensRuleset.phaseThreeSiphonResumeDelay
        state.wardenPhantomNextAttackAt.keys.toList().forEach { key ->
            state.wardenPhantomNextAttackAt[key] =
                maxOf(resumeAt, state.wardenPhantomNextAttackAt.getValue(key))
        }
        state.wardenNextPhantomAttackAt =
            state.wardenPhantomNextAttackAt.values.minOrNull() ?: Int.MAX_VALUE
        raid.nextMechanicAt =
            if (state.wardenEnraged) {
                state.wardenNextLightningAt = maxOf(resumeAt, state.wardenNextLightningAt)
                minOf(state.wardenNextLightningAt, state.wardenNextPhantomAttackAt)
            } else {
                state.wardenNextSlamAt = maxOf(resumeAt, state.wardenNextSlamAt)
                minOf(state.wardenNextSlamAt, state.wardenNextPhantomAttackAt)
            }
        boss(raid)?.let { source ->
            val activeId =
                if (state.wardenPhaseThreeGod == WardenGod.ELIDINIS) ElidinisWarden.FINAL_FORM
                else TumekenWarden.FINAL_FORM
            source.transmog(requireNotNull(ServerCacheManager.getNpc(activeId)), Int.MAX_VALUE)
            if (success) {
                source.anim(WardenEnergySiphons.RETURN_ANIMATION)
                source.spotanim(WardenPhaseThreeTransition.FINAL_WARDEN_SPOTANIM)
                val reflected = WardenEnergySiphons.reflectedDamage(source.baseHitpointsLvl)
                source.hitpoints = (source.hitpoints - reflected).coerceAtLeast(1)
                val hitmarkGroup = WardenPhaseTwoShieldHud.HITMARK
                val lit = hitmarkGroup.lit.asRSCM(RSCMType.HITMARK)
                val tint = hitmarkGroup.tint?.asRSCM(RSCMType.HITMARK) ?: lit
                source.showHitmark(Hitmark.fromNoSource(lit, lit, tint, reflected, 0))
                showWardenTrueHealthHeadbar(source)
            }
            lockWardenActor(source)
        }
        disabled.forEach { removeNpc(raid, it) }
        state.wardenSiphonsSpawned = 0
        state.wardenSiphonPartySize = 1
        message(
            raid.party,
            if (success) {
                "Every Energy Siphon was disabled. The stored energy strikes the Warden."
            } else {
                "The Energy Siphons detonate before they can all be disabled."
            },
        )
    }

    private fun unlockRewards(raid: ToaRaidSession) {
        if (raid.state == ToaRaidState.REWARDS) return
        processInvocationTimer(raid)
        settleRewardPoints(raid, ToaRoom.WARDENS)
        if (raid.completedAt == null) raid.completedAt = clock.cycle
        raid.syncRewardPointTotals()
        // Persist the complete party reward plan before making the treasure room reachable.
        // If the transaction fails, completionPending and the WARDENS state remain intact so
        // entering the reward room retries the exact in-memory plan instead of stranding the raid.
        rewards.prepare(raid)
        raid.state = ToaRaidState.REWARDS
        raid.currentRoom = ToaRoom.REWARD
        raid.completedRooms += ToaRoom.WARDENS
        raid.completedRooms += ToaRoom.TUMEKEN_WARDEN
        raid.completionPending = false
        recordCompletion(raid)
        message(
            raid.party,
            "The Tombs are complete. Enter the treasure room and search your sarcophagus.",
        )
    }

    private fun recordCompletion(raid: ToaRaidSession) {
        forEachOnline(raid) { player ->
            val participant = raid.participant(player.accountHash) ?: return@forEachOnline
            recordCompletion(player, raid, participant)
        }
    }

    private fun recordCompletion(
        player: Player,
        raid: ToaRaidSession,
        participant: ToaParticipant,
    ) {
        if (!ToaCompletionCredit.claim(participant)) return
        val timerStart = raid.timerStartedAt ?: raid.startedAt
        val elapsed = (raid.completedAt ?: clock.cycle) - timerStart
        val key =
            when (raid.mode) {
                ToaMode.ENTRY -> TOA_ENTRY_COMPLETIONS
                ToaMode.NORMAL -> TOA_NORMAL_COMPLETIONS
                ToaMode.EXPERT -> TOA_EXPERT_COMPLETIONS
            }
        player.attr[key] = player.attr.getOrDefault(key, 0) + 1
        val best = player.attr.getOrDefault(TOA_BEST_TICKS, Int.MAX_VALUE)
        if (elapsed < best) player.attr[TOA_BEST_TICKS] = elapsed
    }

    private fun allEligibleRewardsClaimed(raid: ToaRaidSession): Boolean =
        raid.participants.eligible().all(ToaParticipant::rewardClaimed)

    private fun resetEncounter(raid: ToaRaidSession) {
        if (isWardenRoom(raid.currentRoom)) restoreWardenFloor(raid)
        if (isWardenRoom(raid.currentRoom)) cleanupWardenEffects(raid)
        clearEncounterNpcs(raid)
        forEachOnline(raid) { player -> supplies.claimPendingWipeSupplies(player, raid) }
        if (isWardenRoom(raid.currentRoom)) {
            raid.participants.eligible().forEach { it.spectating = false }
            raid.completionPending = false
            raid.currentRoom = ToaRoom.WARDENS
            raid.wardenPhase = 0
            teleportParty(raid, ToaRoom.WARDENS)
            raid.encounter.reset()
            spawnWardenObeliskPhase(raid)
            raid.encounterStarted = true
            activateWardenEncounter(raid)
            raid.nextMechanicAt = clock.cycle + 1
            message(raid.party, "The Wardens encounter resets from the Obelisk.")
            return
        }
        raid.participants.eligible().forEach { it.spectating = false }
        raid.completionPending = false
        when (val room = raid.currentRoom) {
            ToaRoom.KEPHRI,
            ToaRoom.BABA -> {
                teleportParty(raid, room)
                raid.encounter.reset()
                spawnBoss(raid, bossId(raid.currentPath ?: return))
                raid.encounterStarted = true
            }
            ToaRoom.AKKHA -> {
                teleportParty(raid, room)
                raid.encounter.reset()
                raid.akkha.reset()
                ensureAkkhaWaitingActor(raid)
                raid.encounterStarted = false
                raid.nextMechanicAt = Int.MAX_VALUE
                message(
                    raid.party,
                    "Akkha withdraws. Use the crystal when the party is ready to try again.",
                )
            }
            ToaRoom.APMEKEN,
            ToaRoom.SCABARAS,
            ToaRoom.HET,
            ToaRoom.CRONDIS -> {
                val path = raid.currentPath ?: return
                raid.challenge = ToaChallengeState(path)
                raid.encounterStarted = false
                raid.completionPending = false
                teleportParty(raid, room)
                message(raid.party, "Pass the red barrier to restart the challenge.")
            }
            ToaRoom.ZEBAK -> {
                teleportParty(raid, room)
                raid.encounter.reset()
                crondisZebak.initializeZebak(raid)
                raid.encounterStarted = true
            }
            ToaRoom.WARDENS,
            ToaRoom.TUMEKEN_WARDEN -> error("Handled before room reset.")
            else -> teleportParty(raid, room)
        }
        message(raid.party, "The encounter resets.")
    }

    private fun tickBossMechanic(raid: ToaRaidSession) {
        val source = boss(raid) ?: return
        when (raid.currentRoom) {
            ToaRoom.KEPHRI -> tickKephri(raid, source)
            ToaRoom.ZEBAK -> Unit // Zebak is driven by ToaCrondisZebakRuntime every game tick.
            ToaRoom.BABA -> Unit // Ba-Ba mechanics are driven by its per-raid state machine.
            ToaRoom.AKKHA -> tickAkkha(raid, source)
            ToaRoom.WARDENS,
            ToaRoom.TUMEKEN_WARDEN -> tickWardens(raid, source)
            else -> Unit
        }
    }

    private fun tickKephri(raid: ToaRaidSession, source: Npc) {
        val targets = activePlayers(raid)
        if (
            targets.isEmpty() ||
                !raid.encounter.kephriActivated ||
                raid.encounter.kephriRecovering ||
                raid.encounter.kephriDying
        )
            return
        val state = raid.encounter
        source.faceSquare(targets.first().coords)
        state.attackCounter++
        val invocationRules =
            KephriInvocationRules.resolve(
                raid.startSnapshot.invocations,
                raid.startSnapshot.partySize,
            ) { bound ->
                val mixed = raid.startSnapshot.seed xor state.attackCounter.toLong()
                ((mixed and Long.MAX_VALUE) % bound).toInt()
            }
        val decision =
            KephriAttackCycle.next(
                phase = state.phase,
                attacksUntilSpecial = state.kephriAttacksUntilSpecial,
                nextSpecialDung = state.kephriNextSpecialDung,
                agileCount =
                    raid.npcRoles.count { (npc, role) ->
                        role == ToaNpcRole.ADD &&
                            npc.id == KephriScarabAgile.NPC &&
                            npc.isSlotAssigned &&
                            npc.hitpoints > 0
                    },
            )
        state.kephriAttacksUntilSpecial = decision.attacksUntilSpecial
        state.kephriNextSpecialDung = decision.nextSpecialDung
        when (decision.kind) {
            KephriAttackKind.DUNG_STRIKE -> {
                scheduleKephriDungStrike(raid, targets, invocationRules.dungStrikeTargets)
            }
            KephriAttackKind.MASS_INCUBATION -> {
                source.anim(KephriEncounter.SPECIAL_ANIMATION)
                forEachOnline(raid) { it.soundSynth(6442) }
                scheduleKephriEggBatch(raid, source, invocationRules.massIncubationBrownEggs)
            }
            KephriAttackKind.BOMB -> {
                source.anim(KephriEncounter.BOMB_ANIMATION)
                forEachOnline(raid) { it.soundSynth(6435) }
                targets.forEach {
                    scheduleKephriBomb(
                        raid,
                        source,
                        it,
                        invocationRules.bombRadius,
                        invocationRules.bombDamage,
                    )
                }
            }
        }
        raid.nextMechanicAt =
            clock.cycle + ToaInvocationEffects.mechanicInterval(decision.nextDelay, raid)
    }

    private fun scheduleKephriDungStrike(
        raid: ToaRaidSession,
        targets: List<Player>,
        requestedTargets: Int,
        telegraph: Boolean = true,
    ) {
        val state = raid.encounter
        if (requestedTargets <= 0 || targets.isEmpty()) return
        val activeUids = targets.mapTo(hashSetOf(), Player::uid)
        val roster = raid.participants.values.toList()
        var index = state.kephriDungTargetCursor.mod(roster.size.coerceAtLeast(1))
        var visited = 0
        var selected = 0
        while (visited < roster.size && selected < requestedTargets.coerceAtMost(targets.size)) {
            val uid = roster[index].uid
            val target = if (uid in activeUids) uid.resolve(players) else null
            if (target != null) {
                if (telegraph) target.spotanim(KephriDungStrike.PLAYER_FLIES_GRAPHIC)
                state.kephriPendingDungStrikes +=
                    KephriDungStrikeRuntime(
                        target = target.uid,
                        resolvesAt =
                            clock.cycle + if (telegraph) KephriDungStrike.TELEGRAPH_TICKS else 0,
                    )
                selected++
            }
            index = (index + 1).mod(roster.size)
            visited++
        }
        state.kephriDungTargetCursor = index
    }

    private fun processKephriDungStrikes(raid: ToaRaidSession) {
        val state = raid.encounter
        val due = state.kephriPendingDungStrikes.filter { clock.cycle >= it.resolvesAt }
        if (due.isEmpty()) return
        state.kephriPendingDungStrikes.removeAll(due.toSet())
        val anchor = roomCoord(raid, ToaRoom.KEPHRI, true)
        val center = CoordGrid(anchor.x + 2, anchor.z + 2, anchor.level)
        boss(raid)?.anim(KephriEncounter.SPECIAL_ANIMATION)
        forEachOnline(raid) { it.soundSynth(6442) }
        due.forEach { strike ->
            val target = strike.target.resolve(players) ?: return@forEach
            val participant = raid.participant(strike.target)
            if (participant?.connected != true || participant.currentRoom != ToaRoom.KEPHRI) {
                return@forEach
            }
            val start = target.coords
            val step = KephriDungStrike.direction(center, start)
            val destination =
                KephriDungStrike.collisionSafeDestination(
                    start,
                    step,
                    KephriDungStrike.remainingPushTiles(center, start),
                ) { from, direction ->
                    val next = CoordGrid(from.x + direction.dx, from.z + direction.dz, from.level)
                    next !in state.kephriDungTiles &&
                        stepValidator.canTravel(
                            level = from.level,
                            x = from.x,
                            z = from.z,
                            offsetX = direction.dx,
                            offsetZ = direction.dz,
                            size = 1,
                        )
                }
            val path = KephriDungStrike.path(center, start, destination)
            target.combatClearQueue()
            target.clearInteractionRoute()
            target.anim(KephriDungStrike.PLAYER_THROW_ANIMATION)
            target.spotanim(KephriDungStrike.PLAYER_STUN_GRAPHIC)
            target.applyShoveStun(KephriDungStrike.STUN_TICKS, postStunImmunityCycles = 1)
            target.mes("<col=ff289d>Kephri throws you back!</col>")
            if (destination != start) {
                PathingEntityCommon.exactMove(
                    target,
                    start,
                    destination,
                    KephriDungStrike.MOVE_START_CLIENT_CYCLES,
                    KephriDungStrike.movementClientCycles(start.chebyshevDistance(destination)),
                    babaKnockbackFacing(step.dx, step.dz),
                    collision,
                )
            }
            path.forEachIndexed { index, tile ->
                state.kephriDungPlacements +=
                    KephriDungPlacementRuntime(
                        tile = tile,
                        graphicAt = clock.cycle + KephriDungStrike.TRAIL_GRAPHIC_DELAY,
                        locAt = clock.cycle + KephriDungStrike.FIRST_LOC_DELAY + index / 3,
                        clientDelay = index * 10,
                    )
            }
        }
    }

    private fun processKephriDungPlacements(raid: ToaRaidSession) {
        val state = raid.encounter
        if (state.kephriDungPlacements.isEmpty()) return
        val dungType = requireNotNull(ServerCacheManager.getObject(KephriDungStrike.DUNG_LOC))
        state.kephriDungPlacements.toList().forEach { placement ->
            if (!placement.graphicSent && clock.cycle >= placement.graphicAt) {
                world.spotanimMap(
                    SpotanimType(KephriDungStrike.DUNG_GRAPHIC),
                    placement.tile,
                    delay = placement.clientDelay,
                )
                forEachOnline(raid) { it.soundSynth(6480, delay = 17 + placement.clientDelay) }
                placement.graphicSent = true
            }
            if (clock.cycle < placement.locAt) return@forEach
            if (locs.findAll(placement.tile).none { it.id == KephriDungStrike.DUNG_LOC }) {
                locs.add(
                    placement.tile,
                    dungType,
                    Int.MAX_VALUE,
                    LocAngle.North,
                    LocShape.CentrepieceStraight,
                )
            }
            state.kephriDungTiles += placement.tile
            state.kephriDungPlacements.remove(placement)
        }
    }

    private fun scheduleKephriEggBatch(raid: ToaRaidSession, source: Npc, requestedDarkEggs: Int) {
        val state = raid.encounter
        val anchor = roomCoord(raid, ToaRoom.KEPHRI, true)
        val rng = Random(raid.startSnapshot.seed xor state.attackCounter.toLong())
        val tiles =
            KephriEggs.offsets
                .shuffled(rng)
                .map { (dx, dz) -> CoordGrid(anchor.x + dx, anchor.z + dz, anchor.level) }
                .filterNot { it in state.kephriDungTiles }
                .take(KephriEggs.TOTAL)
        val darkCount = requestedDarkEggs.coerceIn(0, tiles.size)
        tiles.forEachIndexed { index, tile ->
            sendKephriEggProjectile(
                source,
                tile,
                if (index < darkCount) ToaAssets.KEPHRI_EGG else ToaAssets.KEPHRI_EGG_ALT,
            )
        }
        state.kephriEggBatches +=
            KephriEggBatchRuntime(
                spawnsAt = clock.cycle + KephriEggs.SPAWN_DELAY,
                tiles = tiles,
                darkCount = darkCount,
            )
    }

    private fun processKephriEggs(raid: ToaRaidSession) {
        val state = raid.encounter
        val batches = state.kephriEggBatches.filter { clock.cycle >= it.spawnsAt }
        state.kephriEggBatches.removeAll(batches.toSet())
        batches.forEach { batch ->
            batch.tiles.forEachIndexed { index, tile ->
                if (tile in state.kephriDungTiles) return@forEachIndexed
                val dark = index < batch.darkCount
                val egg =
                    spawnNpc(
                        raid,
                        if (dark) KephriEggs.AGILE else KephriEggs.EXPLOSIVE,
                        tile,
                        ToaNpcRole.ADD,
                    )
                egg.baseHitpointsLvl = 30
                egg.hitpoints = 30
                egg.suppressHitpointsRegen = true
                egg.ignoreCombatInteractions = true
                egg.mode = null
                egg.anim(KephriEggs.SPAWN_ANIMATION)
                forEachOnline(raid) { it.soundSynth(6446) }
                state.kephriEggs[egg.hashCode()] =
                    KephriEggRuntime(
                        actorKey = egg.hashCode(),
                        tile = tile,
                        dark = dark,
                        resolvesAt = clock.cycle + KephriEggs.LIFETIME,
                    )
                activePlayers(raid)
                    .filter { it.coords == tile }
                    .forEach { player ->
                        player.queueHit(
                            delay = 1,
                            type = HitType.Typeless,
                            damage = KephriEggs.LANDING_DAMAGE,
                            modifier = org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier,
                        )
                    }
            }
        }
        state.kephriEggs.values
            .filter { !it.dark && !it.hitQueued && clock.cycle >= it.resolvesAt - 1 }
            .forEach { egg ->
                egg.hitQueued = true
                val source = boss(raid) ?: return@forEach
                activePlayers(raid).forEach { player ->
                    player.queueImpactHit(
                        source = source,
                        delay = 1,
                        type = HitType.Typeless,
                        damage = scaledDamage(raid, KephriEggs.EXPLOSION_DAMAGE),
                        modifier =
                            object : PlayerHitModifier {
                                override fun HitBuilder.modify(target: Player) {
                                    if (
                                        target.coords.level != egg.tile.level ||
                                            target.coords.chebyshevDistance(egg.tile) > 1
                                    ) {
                                        damage = 0
                                    }
                                }
                            },
                    )
                }
            }
        state.kephriEggs.values
            .filter { clock.cycle >= it.resolvesAt }
            .toList()
            .forEach { resolveKephriEgg(raid, it) }
    }

    private fun resolveKephriEgg(raid: ToaRaidSession, runtime: KephriEggRuntime) {
        val state = raid.encounter
        val egg =
            raid.spawnedNpcs.firstOrNull { it.hashCode() == runtime.actorKey }
                ?: run {
                    state.kephriEggs.remove(runtime.actorKey)
                    return
                }
        state.kephriEggs.remove(runtime.actorKey)
        if (runtime.dark) {
            world.spotanimMap(SpotanimType(KephriEggs.HATCH_ANIMATION), runtime.tile)
            removeNpc(raid, egg)
            if (runtime.tile !in state.kephriDungTiles) {
                spawnNpc(raid, KephriScarabAgile.NPC, runtime.tile, ToaNpcRole.ADD).also { agile ->
                    agile.baseHitpointsLvl = 30
                    agile.hitpoints = 30
                    agile.suppressHitpointsRegen = true
                    agile.apRangeOverride = KephriScarabAgile.ATTACK_RANGE
                    agile.maxOpRangeOverride = 12
                    agile.anim(KephriScarabAgile.SPAWN_ANIMATION)
                    nearestActive(raid, agile.coords)?.let { agile.apPlayer2(it, interactions) }
                }
            }
        } else {
            val variant = KephriBombs.IMPACT + ((runtime.tile.x + runtime.tile.z) and 3)
            world.spotanimMap(SpotanimType(variant), runtime.tile)
            forEachOnline(raid) { it.soundSynth(6436) }
            removeNpc(raid, egg)
        }
    }

    private fun scheduleKephriBomb(
        raid: ToaRaidSession,
        source: Npc,
        target: Player,
        radius: Int,
        baseDamage: Int,
    ) {
        sendKephriOverheadProjectile(source)
        raid.encounter.kephriBombTargets +=
            KephriBombTargetRuntime(
                target = target.uid,
                locksAt = clock.cycle + KephriBombs.TARGET_LOCK_DELAY,
                damage =
                    Random(
                            raid.startSnapshot.seed xor
                                clock.cycle.toLong() xor
                                (target.coords.x.toLong() shl 32) xor
                                target.coords.z.toLong()
                        )
                        .nextInt(scaledDamage(raid, baseDamage).coerceAtLeast(1) + 1),
                radius = radius,
            )
    }

    private fun processKephriBomberBombs(raid: ToaRaidSession) {
        val state = raid.encounter
        if (!state.kephriRecovering) return
        val rules =
            KephriInvocationRules.resolve(
                raid.startSnapshot.invocations,
                raid.startSnapshot.partySize,
            )
        if (!rules.aerialAssault) {
            state.kephriNextBomberBombAt = -1
            return
        }
        if (state.kephriNextBomberBombAt < 0) {
            state.kephriNextBomberBombAt = clock.cycle + KephriBomberScarabs.INITIAL_DELAY
            return
        }
        if (clock.cycle < state.kephriNextBomberBombAt) return
        val center = roomCoord(raid, ToaRoom.KEPHRI, true)
        activePlayers(raid)
            .distinctBy { it.coords }
            .forEach { target ->
                val tile = target.coords
                KephriBombs.tiles(tile, rules.bombRadius).forEach {
                    world.spotanimMap(SpotanimType(KephriBombs.SHADOW), it)
                }
                sendProjectile(
                    KephriBomberScarabs.nearestSource(center, tile),
                    tile,
                    ToaAssets.KEPHRI_BUG,
                    KephriBombs.IMPACT_AFTER_LOCK,
                )
                state.kephriBombs +=
                    KephriBombRuntime(
                        tile = tile,
                        landsAt = clock.cycle + KephriBombs.IMPACT_AFTER_LOCK,
                        // OSRS documents bomber scarabs as the same attack, including Aerial's
                        // increased damage and 3x3 area; do not invent a separate bomber max hit.
                        damage = scaledDamage(raid, rules.bombDamage),
                        radius = rules.bombRadius,
                    )
            }
        state.kephriNextBomberBombAt = clock.cycle + KephriBomberScarabs.INTERVAL
    }

    private fun processKephriBombs(raid: ToaRaidSession) {
        val source = boss(raid) ?: return
        val state = raid.encounter
        val locks = state.kephriBombTargets.filter { clock.cycle >= it.locksAt }
        state.kephriBombTargets.removeAll(locks.toSet())
        locks.forEach { pending ->
            val target = pending.target.resolve(players) ?: return@forEach
            val participant = raid.participant(pending.target)
            if (participant?.connected != true || participant.currentRoom != ToaRoom.KEPHRI) {
                return@forEach
            }
            val tile = target.coords
            KephriBombs.tiles(tile, pending.radius).forEach {
                world.spotanimMap(SpotanimType(KephriBombs.SHADOW), it)
            }
            sendKephriBombProjectile(source, tile)
            state.kephriBombs +=
                KephriBombRuntime(
                    tile = tile,
                    landsAt = clock.cycle + KephriBombs.IMPACT_AFTER_LOCK,
                    damage = pending.damage,
                    radius = pending.radius,
                )
        }
        state.kephriBombs
            .filter { !it.hitQueued && clock.cycle >= it.landsAt - 1 }
            .forEach { bomb ->
                bomb.hitQueued = true
                activePlayers(raid).forEach { player ->
                    player.queueImpactHit(
                        source = source,
                        delay = 1,
                        type = HitType.Magic,
                        damage = bomb.damage,
                        modifier =
                            object : PlayerHitModifier {
                                override fun HitBuilder.modify(target: Player) {
                                    if (
                                        target.coords.level != bomb.tile.level ||
                                            target.coords.chebyshevDistance(bomb.tile) > bomb.radius
                                    ) {
                                        damage = 0
                                        return
                                    }
                                    NpcPartialProtectionPrayerPlayerHitModifier(
                                            ToaRules.prayerReductionPercent(
                                                66,
                                                raid.startSnapshot.invocations,
                                            )
                                        )
                                        .run { this@modify.modify(target) }
                                }
                            },
                        impactHandler = prayerImpactHandler(raid, player, HitType.Magic),
                    )
                }
            }
        val due = state.kephriBombs.filter { clock.cycle >= it.landsAt }
        if (due.isEmpty()) return
        state.kephriBombs.removeAll(due.toSet())
        due.forEach { bomb ->
            KephriBombs.tiles(bomb.tile, bomb.radius).forEach {
                val variant = KephriBombs.IMPACT + ((it.x + it.z) and 3)
                world.spotanimMap(SpotanimType(variant), it)
            }
        }
        forEachOnline(raid) { it.soundSynth(6475) }
    }

    private fun tickBabaEncounter(raid: ToaRaidSession) {
        val source = boss(raid) ?: return
        val state = raid.baba
        processBabaPendingRocks(raid, source)
        processBabaPendingRubble(raid)
        processBabaPendingShockwaves(raid, source)
        tickBabaBananas(raid, source)
        tickBabaSarcophagi(raid, source)
        if (
            state.forcedRockfallAt >= 0 &&
                clock.cycle >= state.forcedRockfallAt &&
                state.state !in
                    setOf(
                        BabaState.ROLLING_STONES_PREP,
                        BabaState.ROLLING_STONES,
                        BabaState.DEFEATED,
                    )
        ) {
            state.forcedRockfallAt = -1
            startBabaSpecial(raid, source, BabaSpecial.ROCKFALL)
            return
        }
        when (state.state) {
            BabaState.INTRO ->
                if (clock.cycle >= state.specialEndsAt) {
                    state.startNormalCombat()
                    source.transmog(
                        requireNotNull(ServerCacheManager.getNpc(BabaAssets.BABA)),
                        Int.MAX_VALUE,
                    )
                    selectBabaTarget(raid, postBoulder = false)?.let {
                        source.apPlayer2(it, interactions)
                    }
                    traceBaba(raid, source, "intro_complete", "autosBeforeSpecial" to 2)
                }
            BabaState.NORMAL_COMBAT -> {
                if (state.pendingRollingThresholds.isNotEmpty()) {
                    beginBabaRollingPhase(raid, source)
                } else {
                    maintainBabaAggression(raid, source)
                    tickBabaUnderTargetStomp(raid, source)
                }
            }
            BabaState.ROCKFALL ->
                if (clock.cycle >= state.specialEndsAt) {
                    startBabaSpecial(raid, source, BabaSpecial.ROCK_THROW)
                    traceBaba(
                        raid,
                        source,
                        "rockfall_cover_complete",
                        "next" to BabaSpecial.ROCK_THROW,
                    )
                }
            BabaState.SHOCKWAVE ->
                if (clock.cycle >= state.specialEndsAt) {
                    state.startNormalCombat()
                    selectBabaTarget(raid, postBoulder = false)?.let {
                        source.apPlayer2(it, interactions)
                    }
                    traceBaba(raid, source, "shockwave_complete")
                }
            BabaState.ROCK_THROW ->
                if (clock.cycle >= state.specialEndsAt) {
                    // Rock Throw is one cast per player, not a loop that continues while any
                    // rubble survives. Destroyed rubble schedules the separate follow-up Rockfall
                    // above; surviving cover remains in the arena for later mechanics.
                    state.startNormalCombat()
                    selectBabaTarget(raid, postBoulder = false)?.let {
                        source.apPlayer2(it, interactions)
                    }
                    traceBaba(raid, source, "special_complete")
                }
            BabaState.BABOON_DISCHARGE ->
                if (clock.cycle >= state.specialEndsAt) {
                    state.startNormalCombat()
                    selectBabaTarget(raid, postBoulder = false)?.let {
                        source.apPlayer2(it, interactions)
                    }
                    traceBaba(raid, source, "special_complete")
                }
            BabaState.ROLLING_STONES_PREP ->
                if (clock.cycle >= state.specialEndsAt) {
                    state.beginRollingSet(
                        ToaInvocation.BOULDERDASH in raid.startSnapshot.invocations,
                        clock.cycle,
                    )
                    // Current OSRS releases the first synchronized row immediately after
                    // the short scream/knockback transition. Three prep ticks elapsed above;
                    // the following tick completes that four-tick transition.
                    state.nextBoulderAt = clock.cycle + 1
                    traceBaba(
                        raid,
                        source,
                        "rolling_set_start",
                        "set" to state.rollingSet,
                        "weakLane" to state.weakLane,
                    )
                }
            BabaState.ROLLING_STONES -> tickBabaRollingStones(raid, source)
            BabaState.ROLLING_STONES_RETURN ->
                if (clock.cycle >= state.returnEndsAt) {
                    val form =
                        if (state.completedRollingPhases >= 2) BabaAssets.BABA_COFFIN
                        else BabaAssets.BABA
                    source.transmog(requireNotNull(ServerCacheManager.getNpc(form)), Int.MAX_VALUE)
                    // Resume from the top of the arena. Natural AP pathing brings Ba-Ba back to
                    // her target; teleporting to BOSS_LOCAL_X/BOSS_LOCAL_Z caused the visible snap
                    // after both the 66% and 33% rolling-stone thresholds.
                    state.startNormalCombat()
                    selectBabaTarget(raid, postBoulder = true)?.let {
                        source.apPlayer2(it, interactions)
                    }
                    traceBaba(raid, source, "rolling_return_complete", "bossTile" to source.coords)
                }
            BabaState.WAITING,
            BabaState.DEFEATED,
            BabaState.RESETTING -> Unit
        }
    }

    private fun startBabaSpecial(raid: ToaRaidSession, source: Npc, special: BabaSpecial): Int {
        val state = raid.baba
        state.attackCounter = 0
        source.abortRoute()
        source.mode = null
        val duration =
            when (special) {
                BabaSpecial.ROCKFALL -> {
                    state.state = BabaState.ROCKFALL
                    state.markRubbleSequenceStarted(clock.cycle)
                    source.anim(BabaAssets.ROCKFALL_CAST)
                    val targets = activePlayers(raid)
                    targets.forEach { target ->
                        val landingTile = target.coords
                        sendProjectile(
                            source,
                            landingTile,
                            BabaAssets.ROCKFALL,
                            BabaTuning.ROCKFALL_WARNING_TICKS.value,
                        )
                        world.spotanimMap(SpotanimType(BabaAssets.ROCKFALL), landingTile)
                        state.pendingRocks +=
                            BabaPendingRock(
                                target.uid,
                                landingTile,
                                clock.cycle + BabaTuning.ROCKFALL_WARNING_TICKS.value,
                                scaledDamage(raid, BabaTuning.ROCKFALL_MAX_HIT.value),
                                BabaAssets.ROCKFALL_FASTEST,
                                thrown = false,
                            )
                    }
                    queueBabaRubble(raid, targets, count = 2)
                    BabaTuning.ROCKFALL_WARNING_TICKS.value + 2
                }
                BabaSpecial.ROCK_THROW -> {
                    state.state = BabaState.ROCK_THROW
                    source.anim(BabaAssets.ROCK_THROW_CAST)
                    activePlayers(raid).forEach { target ->
                        val landingTile = target.coords
                        sendProjectile(
                            source,
                            landingTile,
                            BabaAssets.BABA_RANGED_TRAVEL,
                            BabaTuning.ROCK_THROW_WARNING_TICKS.value,
                        )
                        state.pendingRocks +=
                            BabaPendingRock(
                                target.uid,
                                landingTile,
                                clock.cycle + BabaTuning.ROCK_THROW_WARNING_TICKS.value,
                                scaledDamage(raid, BabaTuning.ROCK_THROW_MAX_HIT.value),
                                BabaAssets.EXPLOSION,
                                thrown = true,
                            )
                    }
                    BabaTuning.ROCK_THROW_WARNING_TICKS.value + 1
                }
                BabaSpecial.SHOCKWAVE -> {
                    state.state = BabaState.SHOCKWAVE
                    source.anim(BabaAssets.SHOCKWAVE)
                    queueBabaShockwave(
                        raid,
                        source,
                        BabaTuning.SHOCKWAVE_WARNING_TICKS.value,
                        BabaTuning.SHOCKWAVE_MAX_HIT.value,
                    )
                    BabaTuning.SHOCKWAVE_WARNING_TICKS.value + 1
                }
                BabaSpecial.BABOON_DISCHARGE -> {
                    state.state = BabaState.BABOON_DISCHARGE
                    source.anim(BabaAssets.MANDRILL_ATTACK_MAGIC_1)
                    spawnBabaBaboons(raid, source)
                    5
                }
            }
        state.specialEndsAt = clock.cycle + duration
        traceBaba(
            raid,
            source,
            "special_start",
            "special" to special,
            "endsAt" to state.specialEndsAt,
        )
        return duration
    }

    private fun processBabaPendingRocks(raid: ToaRaidSession, source: Npc) {
        val state = raid.baba
        val iterator = state.pendingRocks.iterator()
        while (iterator.hasNext()) {
            val pending = iterator.next()
            if (clock.cycle < pending.landsAt) continue
            iterator.remove()
            val target = pending.target.resolve(players) ?: continue
            if (target !in activePlayers(raid)) continue
            val impact = pending.tile
            world.spotanimMap(SpotanimType(pending.graphic), impact)
            if (pending.thrown) {
                val cover =
                    BabaCoverResolver.select(
                        impact,
                        state.rubble.values.map { it.tile },
                        state.sarcophagi.values
                            .filter { it.state != BabaSarcophagusState.OPEN }
                            .map { it.tile },
                        raid.spawnedNpcs
                            .filter { it.id == BabaAssets.BABOON && it.isSlotAssigned }
                            .map { it.coords },
                    )
                if (cover == null) {
                    if (BabaRockImpactResolver.playerRemainsOnImpactTile(target.coords, impact)) {
                        queueAkkhaCombatHit(
                            target,
                            source,
                            pending.damage,
                            0,
                            HitType.Typeless,
                            null,
                        )
                    } else {
                        traceBaba(
                            raid,
                            source,
                            "rock_throw_dodged",
                            "target" to target.uid,
                            "impact" to impact,
                            "playerTile" to target.coords,
                        )
                    }
                } else {
                    applyBabaCoverDamage(raid, cover)
                    traceBaba(
                        raid,
                        source,
                        "rock_throw_blocked",
                        "target" to target.uid,
                        "impact" to impact,
                        "cover" to cover.first,
                        "coverTile" to cover.second,
                    )
                }
                continue
            }
            when (target.coords.chebyshevDistance(pending.tile)) {
                0 -> queueAkkhaCombatHit(target, source, pending.damage, 0, HitType.Typeless, null)
                1 -> {
                    queueAkkhaCombatHit(
                        target,
                        source,
                        pending.damage / 2,
                        0,
                        HitType.Typeless,
                        null,
                    )
                    pushBabaPlayer(raid, target, pending.tile, 1)
                }
            }
        }
    }

    private fun queueBabaShockwave(
        raid: ToaRaidSession,
        source: Npc,
        warningTicks: Int,
        maximum: Int,
    ) {
        val target =
            selectBabaTarget(raid, postBoulder = raid.baba.completedRollingPhases > 0) ?: return
        val enhanced = ToaInvocation.SHAKING_THINGS_UP in raid.startSnapshot.invocations
        val (direction, tiles) = BabaShockwaveMask.tiles(source.coords, target.coords, enhanced)
        val lethalTiles =
            if (ToaInvocation.MIND_THE_GAP in raid.startSnapshot.invocations) {
                BabaShockwaveMask.mindGapTiles(source.coords, target.coords)
            } else {
                emptySet()
            }
        tiles.forEach { world.spotanimMap(SpotanimType(BabaAssets.SHOCKWAVE_WARNING), it) }
        raid.baba.pendingShockwaves +=
            BabaPendingShockwave(
                tiles,
                lethalTiles,
                clock.cycle + warningTicks,
                scaledDamage(raid, maximum),
            )
        traceBaba(
            raid,
            source,
            "shockwave_telegraph",
            "target" to target.uid,
            "direction" to direction,
            "tiles" to tiles.size,
            "lethalTiles" to lethalTiles.size,
            "landsAt" to (clock.cycle + warningTicks),
        )
    }

    private fun processBabaPendingShockwaves(raid: ToaRaidSession, source: Npc) {
        val iterator = raid.baba.pendingShockwaves.iterator()
        while (iterator.hasNext()) {
            val pending = iterator.next()
            if (clock.cycle < pending.landsAt) continue
            iterator.remove()
            activePlayers(raid).forEach { player ->
                when {
                    player.coords in pending.lethalTiles -> {
                        pushBabaPlayer(
                            raid,
                            player,
                            source.coords,
                            BabaTuning.BOULDER_PUSH_TILES.value,
                        )
                        queueAkkhaCombatHit(
                            player,
                            source,
                            player.hitpoints.coerceAtLeast(1),
                            0,
                            HitType.Typeless,
                            null,
                        )
                    }
                    player.coords in pending.tiles ->
                        queueAkkhaCombatHit(
                            player,
                            source,
                            pending.damage,
                            0,
                            HitType.Typeless,
                            null,
                        )
                }
            }
        }
    }

    private fun tickBabaUnderTargetStomp(raid: ToaRaidSession, source: Npc) {
        val under = activePlayers(raid).filter { isUnderBaba(source, it) }
        if (under.isEmpty()) {
            raid.baba.underTargetTicks = 0
            return
        }
        raid.baba.underTargetTicks++
        if (raid.baba.underTargetTicks < BabaTuning.UNDER_TARGET_STOMP_TICKS.value) return
        raid.baba.underTargetTicks = 0
        source.anim(BabaAssets.SHOCKWAVE)
        val tiles = under.mapTo(linkedSetOf()) { it.coords }
        tiles.forEach { world.spotanimMap(SpotanimType(BabaAssets.SHOCKWAVE_WARNING), it) }
        raid.baba.pendingShockwaves +=
            BabaPendingShockwave(
                tiles,
                emptySet(),
                clock.cycle + BabaTuning.FAST_STOMP_WARNING_TICKS.value,
                scaledDamage(raid, BabaTuning.FAST_STOMP_MAX_HIT.value),
            )
        traceBaba(raid, source, "under_target_stomp", "targets" to under.size)
    }

    private fun isUnderBaba(source: Npc, player: Player): Boolean =
        player.coords.level == source.coords.level &&
            player.coords.x in source.coords.x until (source.coords.x + 5) &&
            player.coords.z in source.coords.z until (source.coords.z + 5)

    private fun queueBabaRubble(raid: ToaRaidSession, targets: List<Player>, count: Int) {
        if (targets.isEmpty()) return
        val maximum = 2
        val occupied = buildSet {
            addAll(raid.baba.rubble.values.map { it.tile })
            addAll(raid.baba.pendingRubble.map { it.tile })
        }
        val needed = minOf(count, (maximum - occupied.size).coerceAtLeast(0))
        if (needed == 0) return
        val min = babaCoord(raid, BabaArenaLayout.MIN_LOCAL_X, BabaArenaLayout.MIN_LOCAL_Z)
        val max = babaCoord(raid, BabaArenaLayout.MAX_LOCAL_X, BabaArenaLayout.MAX_LOCAL_Z)
        val offsets = listOf(-2 to 0, 2 to 0, 0 to -2, 0 to 2, -2 to -2, 2 to 2)
        val selected = linkedSetOf<CoordGrid>()
        repeat(needed) { index ->
            val target = targets[index % targets.size]
            val start = (raid.baba.nextInt(offsets.size) + index) % offsets.size
            val tile =
                offsets.indices
                    .asSequence()
                    .map { offsets[(start + it) % offsets.size] }
                    .map { offset ->
                        CoordGrid(
                            (target.coords.x + offset.first).coerceIn(min.x, max.x),
                            (target.coords.z + offset.second).coerceIn(min.z, max.z),
                            target.coords.level,
                        )
                    }
                    .firstOrNull { it !in occupied && it !in selected } ?: return@repeat
            selected += tile
            val variant = raid.baba.nextInt(4)
            world.spotanimMap(SpotanimType(BabaAssets.ROCKFALL), tile)
            raid.baba.pendingRubble +=
                BabaPendingRubble(
                    tile,
                    clock.cycle + BabaTuning.ROCKFALL_WARNING_TICKS.value,
                    variant,
                )
        }
    }

    private fun processBabaPendingRubble(raid: ToaRaidSession) {
        val iterator = raid.baba.pendingRubble.iterator()
        while (iterator.hasNext()) {
            val pending = iterator.next()
            if (clock.cycle < pending.landsAt) continue
            iterator.remove()
            if (raid.baba.rubble.values.any { it.tile == pending.tile }) continue
            val ids =
                intArrayOf(
                    BabaAssets.RUBBLE_LARGE,
                    BabaAssets.RUBBLE_MEDIUM,
                    BabaAssets.RUBBLE_SMALL,
                    BabaAssets.RUBBLE_TINY,
                )
            val actor =
                spawnNpc(
                    raid,
                    ids[pending.variant.coerceIn(ids.indices)],
                    pending.tile,
                    ToaNpcRole.BOULDER,
                )
            actor.abortRoute()
            actor.clearInteraction()
            actor.mode = null
            actor.moveRestrict = MoveRestrict.NoMove
            actor.baseHitpointsLvl = BabaScaling.rubbleHitpoints(raid.startSnapshot.partySize)
            actor.hitpoints = actor.baseHitpointsLvl
            actor.suppressHitpointsRegen = true
            actor.anim(BabaAssets.RUBBLE_FALL)
            showBabaRubbleHeadbar(actor)
            raid.baba.rubble[actor.hashCode()] = BabaRubble(actor.hashCode(), pending.tile)
            traceBaba(raid, boss(raid), "rubble_land", "tile" to pending.tile, "npc" to actor.id)
        }
    }

    private fun showBabaRubbleHeadbar(actor: Npc) {
        val headbar = actor.visHeadbar(params.headbar)
        val maximum = actor.baseHitpointsLvl.coerceAtLeast(1)
        val fill = (actor.hitpoints.coerceIn(0, maximum) * headbar.segments / maximum)
        actor.showHeadbar(
            Headbar.fromNoSource(
                self = headbar.id,
                public = headbar.id,
                startFill = fill,
                endFill = fill,
                startTime = 0,
                endTime = 0,
            )
        )
    }

    private fun applyBabaCoverDamage(raid: ToaRaidSession, cover: Pair<BabaCoverKind, CoordGrid>) {
        when (cover.first) {
            BabaCoverKind.RUBBLE -> {
                val entry =
                    raid.baba.rubble.values.firstOrNull { it.tile == cover.second } ?: return
                val actor =
                    raid.spawnedNpcs.firstOrNull { it.hashCode() == entry.actorKey } ?: return
                actor.hitpoints =
                    (actor.hitpoints - BabaTuning.RUBBLE_COVER_DAMAGE.value).coerceAtLeast(0)
                actor.anim(BabaAssets.MANDRILL_EXPLODE)
                showBabaRubbleHeadbar(actor)
                if (actor.hitpoints == 0) {
                    raid.baba.rubble.remove(entry.actorKey)
                    removeNpc(raid, actor)
                    if (raid.baba.state == BabaState.ROCK_THROW) {
                        raid.baba.forcedRockfallAt = clock.cycle + 2
                    }
                }
            }
            BabaCoverKind.SARCOPHAGUS -> {
                val sarc = raid.baba.sarcophagi[cover.second] ?: return
                damageBabaSarcophagus(raid, sarc, BabaTuning.SARCOPHAGUS_COVER_DAMAGE.value)
            }
            BabaCoverKind.BABOON -> {
                val actor =
                    raid.spawnedNpcs.firstOrNull {
                        it.id == BabaAssets.BABOON && it.coords == cover.second
                    } ?: return
                actor.hitpoints =
                    (actor.hitpoints - BabaTuning.BABOON_COVER_DAMAGE.value).coerceAtLeast(0)
                actor.anim(BabaAssets.BABOON_DEFEND)
                if (actor.hitpoints == 0) {
                    handleBabaBaboonDeath(raid, actor.coords)
                    removeNpc(raid, actor)
                }
            }
        }
        world.spotanimMap(SpotanimType(BabaAssets.EXPLOSION), cover.second)
    }

    private fun spawnBabaBaboons(raid: ToaRaidSession, source: Npc) {
        val count = BabaScaling.baboonCount(activePlayers(raid).size.coerceAtLeast(1))
        repeat(count) { index ->
            val baboon = spawnAround(raid, BabaAssets.BABOON, ToaNpcRole.ADD, index, count, 7)
            baboon.baseHitpointsLvl = 10
            baboon.hitpoints = 10
            baboon.suppressHitpointsRegen = true
            maintainBabaBaboonAggression(raid, baboon)
        }
        if (raid.baba.baboonSarcophagusRunner == null) {
            raid.baba.nextBaboonSarcophagusAt =
                clock.cycle + BabaTuning.BABOON_SARCOPHAGUS_DELAY.value
        }
        traceBaba(raid, source, "baboon_discharge", "count" to count)
    }

    private fun directBaboonToSarcophagus(
        raid: ToaRaidSession,
        baboon: Npc,
        target: BabaSarcophagusRuntime,
    ) {
        baboon.abortRoute()
        baboon.clearInteraction()
        baboon.mode = null
        baboon.faceSquare(target.tile)
        val token = raid.baba.token()
        baboon.walkTo(routeFactory, target.tile) {
            if (!raid.baba.isCurrent(token) || !baboon.isSlotAssigned) return@walkTo
        }
    }

    private fun maintainBabaBaboonAggression(raid: ToaRaidSession, baboon: Npc) {
        val target = nearestActive(raid, baboon.coords)
        if (target == null) {
            baboon.abortRoute()
            baboon.clearInteraction()
            baboon.mode = null
            return
        }
        baboon.apRangeOverride = BabaBaboonDischargePolicy.ATTACK_RANGE
        baboon.maxOpRangeOverride = BabaBaboonDischargePolicy.CHASE_RANGE
        baboon.faceSquare(target.coords)
        val interaction = baboon.interaction as? InteractionPlayer
        if (
            baboon.mode != NpcMode.ApPlayer2 ||
                interaction?.uid != target.uid ||
                interaction.apRange != BabaBaboonDischargePolicy.ATTACK_RANGE
        ) {
            baboon.apPlayer2(target, interactions)
        }
    }

    private fun discoverBabaSarcophagi(raid: ToaRaidSession) {
        val state = raid.baba
        for (x in BabaArenaLayout.MIN_LOCAL_X..BabaArenaLayout.MAX_LOCAL_X) {
            for (z in BabaArenaLayout.MIN_LOCAL_Z..BabaArenaLayout.MAX_LOCAL_Z) {
                val tile = babaCoord(raid, x, z)
                locs
                    .findAll(tile)
                    .firstOrNull {
                        it.id in
                            setOf(
                                BabaAssets.CLOSED_SARCOPHAGUS_LOC,
                                BabaAssets.OPEN_SARCOPHAGUS_LOC,
                            )
                    }
                    ?.let { loc ->
                        val open = loc.id == BabaAssets.OPEN_SARCOPHAGUS_LOC
                        state.sarcophagi[tile] =
                            BabaSarcophagusRuntime(
                                tile = tile,
                                hitpoints = if (open) 0 else BabaTuning.SARCOPHAGUS_HITPOINTS.value,
                                state =
                                    if (open) BabaSarcophagusState.OPEN
                                    else BabaSarcophagusState.CLOSED,
                            )
                    }
            }
        }
        traceBaba(raid, boss(raid), "sarcophagi_discovered", "count" to state.sarcophagi.size)
    }

    private fun damageBabaSarcophagus(
        raid: ToaRaidSession,
        sarc: BabaSarcophagusRuntime,
        damage: Int,
    ) {
        if (sarc.state in setOf(BabaSarcophagusState.OPENING, BabaSarcophagusState.OPEN)) return
        sarc.hitpoints = (sarc.hitpoints - damage).coerceAtLeast(0)
        sarc.state = BabaSarcophagusState.DAMAGED
        traceBaba(
            raid,
            boss(raid),
            "sarcophagus_damage",
            "tile" to sarc.tile,
            "damage" to damage,
            "hitpoints" to sarc.hitpoints,
        )
        if (sarc.hitpoints == 0) openBabaSarcophagus(raid, sarc)
    }

    private fun openBabaSarcophagus(raid: ToaRaidSession, sarc: BabaSarcophagusRuntime) {
        if (sarc.state in setOf(BabaSarcophagusState.OPENING, BabaSarcophagusState.OPEN)) return
        sarc.state = BabaSarcophagusState.OPENING
        val current =
            locs.findAll(sarc.tile).firstOrNull { it.id == BabaAssets.CLOSED_SARCOPHAGUS_LOC }
        if (current != null) {
            locs.change(
                current,
                requireNotNull(ServerCacheManager.getObject(BabaAssets.OPEN_SARCOPHAGUS_LOC)),
                Int.MAX_VALUE,
            )
        }
        val actor =
            raid.spawnedNpcs.firstOrNull {
                it.id == BabaAssets.SARCOPHAGUS && it.coords == sarc.tile
            }
                ?: spawnNpc(raid, BabaAssets.SARCOPHAGUS, sarc.tile, ToaNpcRole.HAZARD).also {
                    it.abortRoute()
                    it.mode = null
                    it.baseHitpointsLvl = BabaTuning.SARCOPHAGUS_HITPOINTS.value
                    it.hitpoints = it.baseHitpointsLvl
                    it.suppressHitpointsRegen = true
                }
        actor.abortRoute()
        actor.mode = null
        sarc.hitpoints = 0
        sarc.state = BabaSarcophagusState.OPEN
        sarc.nextEnergyAt = clock.cycle + 3
        traceBaba(raid, boss(raid), "sarcophagus_open", "tile" to sarc.tile)
    }

    private fun tickBabaSarcophagi(raid: ToaRaidSession, source: Npc) {
        val state = raid.baba
        val liveBaboons =
            raid.spawnedNpcs.filter {
                it.id == BabaAssets.BABOON && it.isSlotAssigned && it.hitpoints > 0
            }
        var runner =
            state.baboonSarcophagusRunner?.let { key ->
                liveBaboons.firstOrNull { it.hashCode() == key }
            }
        if (state.baboonSarcophagusRunner != null && runner == null) {
            state.baboonSarcophagusRunner = null
            state.nextBaboonSarcophagusAt =
                if (liveBaboons.isEmpty()) -1
                else clock.cycle + BabaTuning.BABOON_SARCOPHAGUS_DELAY.value
        }
        val closedSarcophagi =
            state.sarcophagi.values.filter {
                it.state in setOf(BabaSarcophagusState.CLOSED, BabaSarcophagusState.DAMAGED)
            }
        if (
            BabaBaboonDischargePolicy.runnerDue(
                clock.cycle,
                state.nextBaboonSarcophagusAt,
                state.baboonSarcophagusRunner,
                liveBaboons.isNotEmpty(),
                closedSarcophagi.isNotEmpty(),
            )
        ) {
            runner = liveBaboons.first()
            state.baboonSarcophagusRunner = runner.hashCode()
            state.nextBaboonSarcophagusAt = -1
            val target = closedSarcophagi.minBy { it.tile.chebyshevDistance(runner.coords) }
            directBaboonToSarcophagus(raid, runner, target)
            traceBaba(
                raid,
                source,
                "baboon_sarcophagus_runner",
                "baboon" to runner.hashCode(),
                "target" to target.tile,
            )
        }
        liveBaboons.forEach { baboon ->
            if (baboon.hashCode() != state.baboonSarcophagusRunner) {
                maintainBabaBaboonAggression(raid, baboon)
                return@forEach
            }
            val target = closedSarcophagi.minByOrNull { it.tile.chebyshevDistance(baboon.coords) }
            if (target == null) {
                state.baboonSarcophagusRunner = null
                state.nextBaboonSarcophagusAt =
                    clock.cycle + BabaTuning.BABOON_SARCOPHAGUS_DELAY.value
                maintainBabaBaboonAggression(raid, baboon)
            } else if (baboon.coords.chebyshevDistance(target.tile) <= 2) {
                baboon.abortRoute()
                baboon.clearInteraction()
                baboon.mode = null
                if (clock.cycle % 4 == 0) {
                    baboon.faceSquare(target.tile)
                    baboon.anim(BabaAssets.MANDRILL_ATTACK_MELEE)
                    damageBabaSarcophagus(raid, target, BabaTuning.BABOON_SARCOPHAGUS_DAMAGE.value)
                }
            } else if (clock.cycle % 4 == 0) {
                directBaboonToSarcophagus(raid, baboon, target)
            }
        }
        raid.baba.sarcophagi.values
            .filter { it.state == BabaSarcophagusState.OPEN }
            .forEach { sarc ->
                if (clock.cycle < sarc.nextEnergyAt) return@forEach
                val target = activePlayers(raid).randomOrNull() ?: return@forEach
                val actor =
                    raid.spawnedNpcs.firstOrNull {
                        it.id == BabaAssets.SARCOPHAGUS && it.coords == sarc.tile
                    } ?: source
                sendProjectile(actor, target, BabaAssets.SARCOPHAGUS_ENERGY)
                val faith = ToaInvocation.GOTTA_HAVE_FAITH in raid.startSnapshot.invocations
                val damage =
                    if (faith) BabaTuning.GOTTA_HAVE_FAITH_DAMAGE.value
                    else BabaTuning.SARCOPHAGUS_ENERGY_DAMAGE.value
                queueAkkhaCombatHit(
                    target,
                    actor,
                    scaledDamage(raid, damage),
                    2,
                    HitType.Magic,
                    null,
                )
                sarc.nextEnergyAt =
                    clock.cycle +
                        if (faith) BabaTuning.GOTTA_HAVE_FAITH_INTERVAL.value
                        else BabaTuning.SARCOPHAGUS_ENERGY_INTERVAL.value
            }
    }

    private fun handleBabaBaboonDeath(raid: ToaRaidSession, tile: CoordGrid) {
        if (ToaInvocation.JUNGLE_JAPES !in raid.startSnapshot.invocations) return
        world.spotanimMap(SpotanimType(BabaAssets.EXPLOSION), tile)
        val bananaType = requireNotNull(ServerCacheManager.getObject(BabaAssets.BANANA_PILE_LOC))
        listOf(tile, CoordGrid(tile.x + 1, tile.z, tile.level)).forEach { bananaTile ->
            raid.baba.bananas.remove(bananaTile)?.let { old ->
                locs
                    .findAll(old.tile)
                    .filter { it.id == BabaAssets.BANANA_PILE_LOC }
                    .forEach { locs.del(it, Int.MAX_VALUE) }
            }
            locs.add(
                bananaTile,
                bananaType,
                Int.MAX_VALUE,
                LocAngle.North,
                LocShape.CentrepieceStraight,
            )
            raid.baba.bananas[bananaTile] =
                BabaBananaPile(
                    bananaTile,
                    clock.cycle + 1,
                    clock.cycle + BabaTuning.BANANA_LIFETIME.value,
                )
        }
    }

    private fun tickBabaBananas(raid: ToaRaidSession, source: Npc) {
        val iterator = raid.baba.bananas.iterator()
        while (iterator.hasNext()) {
            val (_, pile) = iterator.next()
            if (clock.cycle >= pile.expiresAt) {
                locs
                    .findAll(pile.tile)
                    .filter { it.id == BabaAssets.BANANA_PILE_LOC }
                    .forEach { locs.del(it, Int.MAX_VALUE) }
                iterator.remove()
                continue
            }
            if (clock.cycle < pile.armedAt) continue
            val player =
                activePlayers(raid).firstOrNull {
                    it.coords == pile.tile || it.lastProcessedCoord == pile.tile
                } ?: continue
            locs
                .findAll(pile.tile)
                .filter { it.id == BabaAssets.BANANA_PILE_LOC }
                .forEach { locs.del(it, Int.MAX_VALUE) }
            iterator.remove()
            CombatEffects.bind(player, BabaTuning.BABOON_STUN_TICKS.value)
            queueAkkhaCombatHit(
                player,
                source,
                scaledDamage(raid, BabaTuning.BANANA_DAMAGE.value),
                0,
                HitType.Typeless,
                null,
            )
            traceBaba(raid, source, "banana_trigger", "target" to player.uid, "tile" to pile.tile)
        }
    }

    private fun beginBabaRollingPhase(raid: ToaRaidSession, source: Npc) {
        val threshold = raid.baba.beginNextRollingPhase() ?: return
        // Every threshold scream clears debris from the previous combat section.
        clearBabaRubbleSequence(raid)
        source.abortRoute()
        source.mode = null
        source.anim(BabaAssets.ROLLING_STONES_START)
        source.transmog(
            requireNotNull(ServerCacheManager.getNpc(BabaAssets.BABA_DIGGING)),
            Int.MAX_VALUE,
        )
        PathingEntityCommon.telejump(
            source,
            collision,
            babaCoord(raid, BabaArenaLayout.THRONE_LOCAL_X, BabaArenaLayout.THRONE_LOCAL_Z),
        )
        activePlayers(raid).forEach { player ->
            CombatEffects.bind(player, BabaTuning.BABOON_STUN_TICKS.value)
            pushBabaRollingBoulderPlayer(raid, player, 64)
        }
        raid.baba.specialEndsAt = clock.cycle + 3
        traceBaba(
            raid,
            source,
            "rolling_start",
            "threshold" to threshold,
            "throneTile" to source.coords,
            "waves" to BabaTuning.BOULDER_MAX_SETS.value,
            "stonesPerWave" to BabaArenaLayout.laneLocalZ.size,
        )
    }

    private fun tickBabaRollingStones(raid: ToaRaidSession, source: Npc) {
        val state = raid.baba
        advanceBabaBoulders(raid, source)
        if (state.pendingBoulderSpawnAt >= 0 && clock.cycle >= state.pendingBoulderSpawnAt) {
            val releasedSet = state.pendingBoulderSet
            val releasedWeakLane = state.pendingWeakLane
            BabaArenaLayout.laneLocalZ.indices.forEach { lane ->
                releaseBabaBoulder(raid, lane, releasedWeakLane)
            }
            state.bouldersReleasedThisSet = BabaArenaLayout.laneLocalZ.size
            state.pendingBoulderSpawnAt = -1
            state.pendingBoulderSet = 0
            state.pendingWeakLane = -1
            traceBaba(
                raid,
                source,
                "boulder_row_release",
                "set" to releasedSet,
                "count" to BabaArenaLayout.laneLocalZ.size,
                "weakLane" to releasedWeakLane,
            )
        }
        if (state.isRollingCastDue(clock.cycle)) {
            val castSet = state.rollingSet
            val castWeakLane = state.weakLane
            source.anim(BabaAssets.ROLLING_STONES_CAST)
            BabaArenaLayout.laneLocalZ.indices.forEach { lane ->
                val tile =
                    babaCoord(
                        raid,
                        BabaArenaLayout.BOULDER_START_LOCAL_X + 2,
                        BabaArenaLayout.laneLocalZ[lane],
                    )
                sendProjectile(source, tile, BabaAssets.BOULDER_LAUNCH, 2)
            }
            forEachOnline(raid) { it.soundSynth(5960) }
            state.pendingBoulderSpawnAt = clock.cycle + 2
            state.pendingBoulderSet = castSet
            state.pendingWeakLane = castWeakLane
            traceBaba(
                raid,
                source,
                "boulder_row_cast",
                "set" to castSet,
                "weakLane" to castWeakLane,
                "spawnsAt" to state.pendingBoulderSpawnAt,
            )
            val nextSetScheduled =
                state.advanceRollingScheduleAfterCast(
                    ToaInvocation.BOULDERDASH in raid.startSnapshot.invocations,
                    clock.cycle,
                    BabaTuning.BOULDER_MAX_SETS.value,
                )
            if (nextSetScheduled) {
                traceBaba(
                    raid,
                    source,
                    "rolling_set_start",
                    "set" to state.rollingSet,
                    "weakLane" to state.weakLane,
                )
            }
        }
        if (
            state.rollingSet >= BabaTuning.BOULDER_MAX_SETS.value &&
                state.pendingBoulderSpawnAt < 0 &&
                state.rollingBoulders.isEmpty()
        ) {
            finishBabaRollingPhase(raid, source, "ten_rows_complete")
        }
    }

    private fun releaseBabaBoulder(raid: ToaRaidSession, lane: Int, weakLane: Int) {
        val state = raid.baba
        val weak = lane == weakLane
        val tile =
            babaCoord(raid, BabaArenaLayout.BOULDER_START_LOCAL_X, BabaArenaLayout.laneLocalZ[lane])
        val actor =
            spawnNpc(
                raid,
                if (weak) BabaAssets.WEAK_BOULDER else BabaAssets.BOULDER,
                tile,
                ToaNpcRole.BOULDER,
                Direction.West,
            )
        val hitpoints =
            BabaScaling.boulderHitpoints(
                raid.startSnapshot.partySize,
                raid.pathLevels[ToaPath.APMEKEN] ?: 0,
            )
        actor.abortRoute()
        actor.mode = null
        actor.baseHitpointsLvl = hitpoints
        actor.hitpoints = hitpoints
        actor.suppressHitpointsRegen = true
        actor.ignoreCombatInteractions = true
        actor.suppressCombatDefendAnimation = true
        actor.moveRestrict = MoveRestrict.NoMove
        actor.faceDirection(Direction.West)
        actor.anim(BabaAssets.BOULDER_ROLL)
        state.rollingBoulders[actor.hashCode()] =
            BabaRollingBoulder(actor.hashCode(), lane, weak, tile)
        traceBaba(
            raid,
            boss(raid),
            "boulder_release",
            "set" to state.rollingSet,
            "lane" to lane,
            "weak" to weak,
            "hitpoints" to hitpoints,
        )
    }

    private fun advanceBabaBoulders(raid: ToaRaidSession, source: Npc) {
        val iterator = raid.baba.rollingBoulders.iterator()
        val endX =
            babaCoord(raid, BabaArenaLayout.BOULDER_END_LOCAL_X, BabaArenaLayout.BOSS_LOCAL_Z).x
        while (iterator.hasNext()) {
            val (_, runtime) = iterator.next()
            val actor =
                raid.spawnedNpcs.firstOrNull {
                    it.hashCode() == runtime.actorKey && it.isSlotAssigned
                }
            if (actor == null) {
                iterator.remove()
                continue
            }
            val current = actor.coords

            if (current.x <= endX) {
                removeNpc(raid, actor)
                iterator.remove()
                continue
            }
            val next = BabaRollingPath.next(current, endX)
            if (next == null) {
                removeNpc(raid, actor)
                iterator.remove()
                continue
            }
            // The OSRS packet trace advances rolling stones with one-tile teleports while the
            // west-facing roll sequence remains active. A walk update makes the model turn and
            // visually roll backwards in RuneLite.
            actor.faceDirection(Direction.West)
            PathingEntityCommon.telejump(actor, collision, next)
            runtime.tile = next
            activePlayers(raid)
                .filter {
                    it.uid !in runtime.hitPlayers &&
                        BabaRollingCollision.touches(
                            it.coords,
                            it.lastProcessedCoord,
                            next,
                            actor.visType.size,
                        )
                }
                .forEach { player ->
                    runtime.hitPlayers += player.uid
                    queueBabaRollingBoulderCollision(raid, source, actor, runtime, player)
                }
            raid.baba.rubble.values
                .filter { it.tile.chebyshevDistance(next) <= 1 }
                .toList()
                .forEach { rubble ->
                    raid.spawnedNpcs
                        .firstOrNull { it.hashCode() == rubble.actorKey }
                        ?.let { removeNpc(raid, it) }
                    raid.baba.rubble.remove(rubble.actorKey)
                }
            raid.spawnedNpcs
                .filter { it.id == BabaAssets.BABOON && it.coords.chebyshevDistance(next) <= 1 }
                .toList()
                .forEach { baboon ->
                    handleBabaBaboonDeath(raid, baboon.coords)
                    removeNpc(raid, baboon)
                }
        }
    }

    private fun queueBabaRollingBoulderCollision(
        raid: ToaRaidSession,
        source: Npc,
        boulder: Npc,
        runtime: BabaRollingBoulder,
        player: Player,
    ) {
        val actorKey = runtime.actorKey
        player.queueImpactHit(
            source = source,
            delay = 1,
            type = HitType.Typeless,
            damage = scaledDamage(raid, BabaTuning.BOULDER_COLLISION_MAX_HIT.value),
            modifier =
                object : PlayerHitModifier {
                    override fun HitBuilder.modify(target: Player) {
                        if (boulder.hitpoints <= 0 || actorKey in raid.baba.destroyedBoulders) {
                            damage = 0
                            return
                        }
                        NpcBypassProtectionPrayerPlayerHitModifier.run {
                            this@modify.modify(target)
                        }
                    }
                },
            impactHandler =
                HitImpactHandler {
                    if (
                        boulder.hitpoints > 0 &&
                            actorKey !in raid.baba.destroyedBoulders &&
                            player.isSlotAssigned &&
                            player.hitpoints > 0
                    ) {
                        player.anim("seq.stunned_shove")
                        player.spotanim(
                            spot = "spotanim.stunned_shove",
                            slot = constants.spotanim_slot_combat,
                            height = 96,
                        )
                        player.applyShoveStun(
                            stunCycles = BabaTuning.BOULDER_STUN_TICKS.value,
                            postStunImmunityCycles = 1,
                        )
                        pushBabaRollingBoulderPlayer(
                            raid,
                            player,
                            BabaTuning.BOULDER_PUSH_TILES.value,
                        )
                    }
                },
        )
    }

    private fun pushBabaRollingBoulderPlayer(raid: ToaRaidSession, player: Player, tiles: Int) {
        val floorEndX =
            babaCoord(raid, BabaArenaLayout.BOULDER_END_LOCAL_X, BabaArenaLayout.BOSS_LOCAL_Z).x
        val safeTiles = BabaRollingKnockback.safeDistance(player.coords.x, floorEndX, tiles)
        if (safeTiles == 0) return
        val destination = babaKnockbackDestination(raid, player.coords, -1, 0, safeTiles)
        if (destination == player.coords) return
        PathingEntityCommon.exactMove(
            player,
            player.coords,
            destination,
            0,
            60,
            constants.em_face_east,
            collision,
        )
    }

    private fun handleBabaBoulderKill(raid: ToaRaidSession, npc: Npc) {
        val actorKey = npc.hashCode()
        raid.baba.rubble.remove(actorKey)
        val runtime = raid.baba.rollingBoulders.remove(actorKey)
        if (runtime != null) raid.baba.destroyedBoulders += actorKey
        // handleNpcKill has already detached the role/controller bookkeeping. Delete the actor
        // immediately as well; otherwise a dead cracked boulder remains rendered and stationary.
        removeNpc(raid, npc)
        if (runtime == null) return
        if (runtime.weak) {
            message(
                raid.party,
                "The cracked boulder breaks, leaving a safe lane through this wave.",
            )
            traceBaba(
                raid,
                boss(raid),
                "weak_boulder_broken",
                "lane" to runtime.lane,
                "set" to raid.baba.rollingSet,
                "hits" to runtime.validRangedHits,
            )
        }
    }

    private fun clearBabaRubbleSequence(raid: ToaRaidSession) {
        raid.baba.pendingRocks.clear()
        raid.baba.pendingRubble.clear()
        raid.baba.pendingShockwaves.clear()
        raid.baba.rubble.keys.toList().forEach { key ->
            raid.spawnedNpcs.firstOrNull { it.hashCode() == key }?.let { removeNpc(raid, it) }
        }
        raid.baba.rubble.clear()
    }

    private fun finishBabaRollingPhase(raid: ToaRaidSession, source: Npc, reason: String) {
        raid.baba.rollingBoulders.keys.toList().forEach { key ->
            raid.spawnedNpcs.firstOrNull { it.hashCode() == key }?.let { removeNpc(raid, it) }
        }
        raid.baba.rollingBoulders.clear()
        source.abortRoute()
        source.mode = null
        source.transmog(
            requireNotNull(ServerCacheManager.getNpc(BabaAssets.BABA_COFFIN)),
            Int.MAX_VALUE,
        )
        source.anim(BabaAssets.ROLLING_STONES_END)
        raid.baba.finishRollingPhase(clock.cycle + 2)
        traceBaba(
            raid,
            source,
            "rolling_return",
            "reason" to reason,
            "completed" to raid.baba.completedRollingPhases,
        )
    }

    private fun pushBabaPlayer(
        raid: ToaRaidSession,
        player: Player,
        origin: CoordGrid,
        tiles: Int,
    ) {
        val dx = (player.coords.x - origin.x).coerceIn(-1, 1).let { if (it == 0) 1 else it }
        val dz = (player.coords.z - origin.z).coerceIn(-1, 1)
        val destination = babaKnockbackDestination(raid, player.coords, dx, dz, tiles)
        if (destination == player.coords) return
        PathingEntityCommon.exactMove(
            player,
            player.coords,
            destination,
            0,
            60,
            babaKnockbackFacing(dx, dz),
            collision,
        )
    }

    private fun babaKnockbackDestination(
        raid: ToaRaidSession,
        start: CoordGrid,
        dx: Int,
        dz: Int,
        tiles: Int,
    ): CoordGrid {
        val min = babaCoord(raid, BabaArenaLayout.MIN_LOCAL_X, BabaArenaLayout.MIN_LOCAL_Z)
        val max = babaCoord(raid, BabaArenaLayout.MAX_LOCAL_X, BabaArenaLayout.MAX_LOCAL_Z)
        var destination = start
        repeat(tiles.coerceAtLeast(0)) {
            val next = CoordGrid(destination.x + dx, destination.z + dz, destination.level)
            if (next.x !in min.x..max.x || next.z !in min.z..max.z) return destination
            if (
                !stepValidator.canTravel(
                    level = destination.level,
                    x = destination.x,
                    z = destination.z,
                    offsetX = dx,
                    offsetZ = dz,
                    size = 1,
                )
            ) {
                return destination
            }
            destination = next
        }
        return destination
    }

    private fun babaKnockbackFacing(dx: Int, dz: Int): Int =
        when {
            dx < 0 && dz < 0 -> constants.em_face_northeast
            dx < 0 && dz > 0 -> constants.em_face_southeast
            dx > 0 && dz < 0 -> constants.em_face_northwest
            dx > 0 && dz > 0 -> constants.em_face_southwest
            dx < 0 -> constants.em_face_east
            dx > 0 -> constants.em_face_west
            dz < 0 -> constants.em_face_north
            else -> constants.em_face_south
        }

    private fun tickAkkha(raid: ToaRaidSession, source: Npc) {
        val state = raid.akkha
        if (state.state != AkkhaState.FINAL_STAND) {
            raid.nextMechanicAt = clock.cycle + 1
            return
        }
        completeAkkhaFinalStandSwapIfDue(raid)
        val real = boss(raid) ?: return
        moveFinalOrbs(raid, real)
        if (
            clock.cycle >= state.nextFinalOrbAt &&
                state.finalOrbs.size < AkkhaTuning.FINAL_ORB_ACTIVE_CAP.value
        ) {
            spawnFinalOrb(raid)
            state.nextFinalOrbAt = clock.cycle + AkkhaTuning.FINAL_ORB_SPAWN_INTERVAL.value
        }
        raid.nextMechanicAt = clock.cycle + 1
    }

    private fun spawnFinalOrb(raid: ToaRaidSession) {
        val state = raid.akkha
        val center = roomCoord(raid, ToaRoom.AKKHA, true)
        val radius = AkkhaArenaLayout.ARENA_RADIUS
        val lane = state.nextInt(8)
        val offset = state.nextInt(radius * 2 + 1) - radius
        val (tile, direction) = AkkhaFinalOrbController.spawn(center, lane, offset)
        val actor = spawnNpc(raid, AkkhaAssets.ENRAGE_ORB, tile, ToaNpcRole.HAZARD)
        actor.moveRestrict = MoveRestrict.PassThru
        actor.abortRoute()
        actor.mode = null
        actor.lockFacing(AkkhaFinalOrbController.next(tile, direction))
        val key = actor.hashCode()
        state.finalOrbs[key] = AkkhaFinalOrb(key, tile, direction, clock.cycle)
        traceAkkha(
            raid,
            boss(raid),
            "orb_spawn",
            "orbId" to AkkhaAssets.ENRAGE_ORB,
            "orbTile" to tile,
            "orbDirection" to direction,
        )
    }

    private fun moveFinalOrbs(raid: ToaRaidSession, source: Npc) {
        val state = raid.akkha
        val center = roomCoord(raid, ToaRoom.AKKHA, true)
        val iterator = state.finalOrbs.iterator()
        while (iterator.hasNext()) {
            val (_, orb) = iterator.next()
            val actor = raid.spawnedNpcs.firstOrNull { it.hashCode() == orb.actorKey }
            if (actor == null || !actor.isSlotAssigned) {
                iterator.remove()
                continue
            }
            // The actor moves after this raid tick. Reconcile from its authoritative tile instead
            // of advancing the tracked destination a second time before that movement is applied.
            val current = actor.coords
            val next = AkkhaFinalOrbController.next(current, orb.direction)
            if (!insideAkkhaArena(center, next)) {
                removeNpc(raid, actor)
                iterator.remove()
                continue
            }
            val collided =
                activePlayers(raid).filter { player ->
                    val traversed = exactPlayerTraversal(player)
                    current in traversed || next in traversed
                }
            if (collided.isNotEmpty()) {
                world.spotanimMap(SpotanimType(AkkhaAssets.ENRAGE_ORB_IMPACT), next)
                val raw = scaledDamage(raid, AkkhaTuning.TRAILING_ORB_MAX_HIT.value)
                collided.forEach { player ->
                    val protected =
                        raid.raidLevel < 300 && player.vars["varbit.prayer_protectfrommagic"] == 1
                    val afterPrayer =
                        if (protected) {
                            raw * (100 - AkkhaTuning.FINAL_ORB_PRAYER_REDUCTION.value) / 100
                        } else {
                            raw
                        }
                    val total = afterPrayer.coerceAtMost(AkkhaTuning.FINAL_ORB_DAMAGE_CAP.value)
                    val share = (total + collided.size - 1) / collided.size
                    queueAkkhaCombatHit(player, source, share, 1, HitType.Magic, null)
                }
                traceAkkha(
                    raid,
                    source,
                    "orb_collision",
                    "targets" to collided.joinToString { it.uid.toString() },
                    "splitCount" to collided.size,
                    "orbTile" to next,
                    "orbDirection" to orb.direction,
                )
                removeNpc(raid, actor)
                iterator.remove()
                continue
            }
            actor.clearFacingLock()
            actor.faceSquare(next)
            PathingEntityCommon.forceWalkStep(actor, collision, next)
            actor.lockFacing(AkkhaFinalOrbController.next(next, orb.direction))
            // This remains the intended destination for collision tracing only; actor.coords wins.
            orb.tile = next
        }
    }

    private fun tickWardens(raid: ToaRaidSession, source: Npc) {
        val targets = activePlayers(raid)
        if (targets.isEmpty()) return
        val state = raid.encounter
        when (state.wardenStage) {
            ToaWardenStage.OBELISK -> tickWardenObelisk(raid, source, targets)
            ToaWardenStage.PHASE_ONE_TRANSITION -> raid.nextMechanicAt = Int.MAX_VALUE
            ToaWardenStage.PHASE_TWO -> tickWardenPhaseTwo(raid, source, targets)
            ToaWardenStage.PHASE_THREE -> tickWardenPhaseThree(raid, source, targets)
            ToaWardenStage.ENRAGE -> tickWardenEnrage(raid, source, targets)
            ToaWardenStage.CORE,
            ToaWardenStage.PHASE_THREE_TRANSITION,
            ToaWardenStage.ENERGY_SIPHONS,
            ToaWardenStage.DEATH_SEQUENCE -> raid.nextMechanicAt = clock.cycle + 1
        }
    }

    private fun tickWardenObelisk(raid: ToaRaidSession, source: Npc, targets: List<Player>) {
        val state = raid.encounter
        val ruleset = state.wardensRuleset
        if (!state.wardenObeliskActivated) {
            raid.nextMechanicAt = clock.cycle + 1
            return
        }
        state.wardenPhaseOneSpecialEndsAt
            .filterValues { clock.cycle >= it }
            .keys
            .toList()
            .forEach { finishWardenPhaseOneSpecial(raid, it) }
        val queued = state.wardenPhaseOneSpecialQueue.iterator()
        while (queued.hasNext()) {
            val special = queued.next()
            if (special.god in state.wardenPhaseOneSpecials) continue
            queued.remove()
            beginWardenPhaseOneSpecial(raid, source, targets, special)
        }
        if (state.wardenNextPassiveChargeAt < 0) {
            state.wardenNextPassiveChargeAt = clock.cycle + ruleset.phaseOnePassiveChargeInterval
        }
        while (clock.cycle >= state.wardenNextPassiveChargeAt) {
            WardenGod.entries.forEach { god ->
                advanceWardenCharge(raid, god, ruleset.phaseOnePassiveChargeAmount)
            }
            state.wardenNextPassiveChargeAt += ruleset.phaseOnePassiveChargeInterval
        }
        if (state.wardenNextEnergyPulseAt < 0) {
            state.wardenNextEnergyPulseAt = clock.cycle
        }
        if (clock.cycle < state.wardenNextEnergyPulseAt) {
            raid.nextMechanicAt = clock.cycle + 1
            return
        }
        val blockers =
            targets
                .mapNotNull { player ->
                    WardenPhaseOneLayout.blockingSide(
                            source.coords.x,
                            source.coords.z,
                            player.coords.x,
                            player.coords.z,
                        )
                        ?.let { it to player }
                }
                .toMap()
        resolveWardenEnergyPulse(raid, source, WardenGod.ELIDINIS, blockers[WardenGod.ELIDINIS])
        resolveWardenEnergyPulse(raid, source, WardenGod.TUMEKEN, blockers[WardenGod.TUMEKEN])
        state.attackCounter++
        state.wardenNextEnergyPulseAt = clock.cycle + ruleset.phaseOneEnergyPulseInterval
        raid.nextMechanicAt = clock.cycle + 1
    }

    private fun resolveWardenEnergyPulse(
        raid: ToaRaidSession,
        obelisk: Npc,
        god: WardenGod,
        blocker: Player?,
    ) {
        val identity = god.chargeActorIdentity()
        val warden =
            raid.spawnedNpcs.firstOrNull {
                it.id == identity.originalId &&
                    it.transmog?.id == identity.chargingVisualId &&
                    it.isSlotAssigned
            } ?: return
        val chargeGraphic = ToaAssets.WARDEN_PHASE_ONE_SMALL_BALL
        val ruleset = raid.encounter.wardensRuleset
        val indicatorLocal = WardenPhaseOneLayout.indicatorLocalTile(god)
        val indicatorTile =
            wardenRoomCoord(raid, ToaRoom.WARDENS, indicatorLocal.x, indicatorLocal.z)
        val lane = WardenPhaseOneLayout.energyStreamLane(god, raid.encounter.attackCounter)
        val projectileTimeline = WardenPhaseOneProjectileTiming.timeline(lane, ruleset)
        val start =
            CoordGrid(
                obelisk.coords.x + lane.obeliskXOffset,
                obelisk.coords.z + lane.obeliskZOffset,
                obelisk.coords.level,
            )
        if (blocker == null) {
            val fork =
                CoordGrid(
                    obelisk.coords.x + lane.forkXOffset,
                    obelisk.coords.z + lane.forkZOffset,
                    obelisk.coords.level,
                )
            val branch =
                CoordGrid(
                    obelisk.coords.x + lane.branchXOffset,
                    obelisk.coords.z + lane.branchZOffset,
                    obelisk.coords.level,
                )
            val target =
                CoordGrid(
                    warden.coords.x + lane.wardenXOffset,
                    warden.coords.z + lane.wardenZOffset,
                    warden.coords.level,
                )
            sendWardenChargeProjectile(
                start = start,
                target = fork,
                graphic = chargeGraphic,
                startTimeClientCycles = projectileTimeline.trunkStartCycles,
                endTimeClientCycles = projectileTimeline.turnStartCycles,
            )
            sendWardenChargeProjectile(
                start = fork,
                target = branch,
                graphic = chargeGraphic,
                startTimeClientCycles = projectileTimeline.turnStartCycles,
                endTimeClientCycles = projectileTimeline.laneStartCycles,
            )
            sendWardenChargeProjectile(
                start = branch,
                target = target,
                graphic = chargeGraphic,
                startTimeClientCycles = projectileTimeline.laneStartCycles,
                endTimeClientCycles = projectileTimeline.laneEndCycles,
            )
        } else {
            val startTime = ToaProjectileTiming.clientCyclesForServerDelay(lane.launchDelayTicks)
            sendWardenChargeProjectile(
                start = start,
                target = blocker.coords,
                graphic = chargeGraphic,
                startTimeClientCycles = startTime,
                endTimeClientCycles =
                    startTime +
                        ToaProjectileTiming.clientCyclesForServerTicks(
                            ruleset.phaseOneEnergyTravelTicks
                        ),
            )
            queueAkkhaCombatHit(
                blocker,
                obelisk,
                3,
                ruleset.phaseOneEnergyTravelTicks + lane.launchDelayTicks,
                HitType.Typeless,
                null,
            )
        }
        if (blocker == null) {
            raid.encounter.wardenChargeBalls +=
                WardenChargeBallRuntime(
                    god = god,
                    landsAt = WardenPhaseOneTiming.ballLandsAt(clock.cycle, lane, ruleset),
                    chargeAmount = ruleset.phaseOneOrbCharge(blocked = false),
                    indicatorTile = indicatorTile,
                )
        }
    }

    private fun processWardenPhaseOneCharge(raid: ToaRaidSession) {
        val state = raid.encounter
        if (state.wardenStage != ToaWardenStage.OBELISK) {
            state.wardenChargeBalls.clear()
            clearWardenChargeCircle(raid)
            return
        }
        processWardenChargeCircleResets(raid)
        val arrivals = state.wardenChargeBalls.iterator()
        while (arrivals.hasNext()) {
            val ball = arrivals.next()
            if (clock.cycle < ball.landsAt) continue
            absorbWardenChargeBall(raid, ball)
            arrivals.remove()
        }
    }

    private fun absorbWardenChargeBall(raid: ToaRaidSession, ball: WardenChargeBallRuntime) {
        val state = raid.encounter
        val currentUnits = wardenCircleUnits(state, ball.god)
        val advance = WardenChargeCircle.advance(currentUnits, ball.chargeAmount)
        setWardenCircleUnits(state, ball.god, advance.remainingUnits)
        if (!syncWardenChargePlatform(raid, ball.god, advance.displayUnits)) {
            log.error {
                "[ToA] failed to advance ${ball.god.displayName()} charging platform " +
                    "instance=${raid.instance.id} units=${advance.displayUnits}"
            }
        }
        advanceWardenCharge(raid, ball.god, ball.chargeAmount)
        if (advance.completedCycle) completeWardenChargeCircle(raid, ball.god)
    }

    private fun advanceWardenCharge(raid: ToaRaidSession, god: WardenGod, amount: Int) {
        if (amount <= 0) return
        val state = raid.encounter
        val oldCharge =
            if (god == WardenGod.ELIDINIS) state.wardenElidinisCharge else state.wardenTumekenCharge
        val newCharge = (oldCharge + amount).coerceAtMost(100)
        if (god == WardenGod.ELIDINIS) {
            if (newCharge >= 100) {
                state.wardenPhaseOneSpecialQueue +=
                    WardenPhaseOneMechanics.nextSpecial(
                        WardenGod.ELIDINIS,
                        state.wardenElidinisChargeCycles,
                    )
                state.wardenElidinisChargeCycles++
                state.wardenElidinisCharge = 0
            } else {
                state.wardenElidinisCharge = newCharge
            }
        } else {
            if (newCharge >= 100) {
                state.wardenPhaseOneSpecialQueue +=
                    WardenPhaseOneMechanics.nextSpecial(
                        WardenGod.TUMEKEN,
                        state.wardenTumekenChargeCycles,
                    )
                state.wardenTumekenChargeCycles++
                state.wardenTumekenCharge = 0
            } else {
                state.wardenTumekenCharge = newCharge
            }
        }
        updateWardenChargeHeadbars(raid)
    }

    private fun completeWardenChargeCircle(raid: ToaRaidSession, god: WardenGod) {
        val state = raid.encounter
        state.wardenChargeCircleResets.removeAll { it.god == god }
        state.wardenChargeCircleResets +=
            WardenChargeCircleResetRuntime(
                god = god,
                resetsAt = clock.cycle + WardenChargeCircle.RESET_VISUAL_TICKS,
            )
    }

    private fun processWardenChargeCircleResets(raid: ToaRaidSession) {
        val resets = raid.encounter.wardenChargeCircleResets.iterator()
        while (resets.hasNext()) {
            val reset = resets.next()
            if (clock.cycle < reset.resetsAt) continue
            syncWardenChargePlatform(raid, reset.god, wardenCircleUnits(raid.encounter, reset.god))
            resets.remove()
        }
    }

    private fun wardenCircleUnits(state: ToaEncounterState, god: WardenGod): Int =
        if (god == WardenGod.ELIDINIS) state.wardenElidinisCircleUnits
        else state.wardenTumekenCircleUnits

    private fun setWardenCircleUnits(state: ToaEncounterState, god: WardenGod, units: Int) {
        if (god == WardenGod.ELIDINIS) state.wardenElidinisCircleUnits = units
        else state.wardenTumekenCircleUnits = units
    }

    private fun clearWardenChargeCircle(raid: ToaRaidSession) {
        WardenGod.entries.forEach { restoreWardenChargePlatform(raid, it) }
        val state = raid.encounter
        state.wardenElidinisCircleUnits = 0
        state.wardenTumekenCircleUnits = 0
        state.wardenChargeCircleResets.clear()
    }

    /**
     * The two map platforms are both loc 45605, type 10, at the Wardens' south-west tiles. The
     * cache places Elidinis at angle 3 (south) and Tumeken at angle 1 (north). Replacing the loc in
     * LocRepository preserves its exact tile, plane, layer, shape and angle and broadcasts one
     * authoritative visual state to every player in the instance.
     */
    private fun syncWardenChargePlatform(
        raid: ToaRaidSession,
        god: WardenGod,
        units: Int,
    ): Boolean = syncWardenChargePlatformLoc(raid, god, WardenChargeCircle.platformStateLoc(units))

    private fun restoreWardenChargePlatform(raid: ToaRaidSession, god: WardenGod): Boolean =
        syncWardenChargePlatformLoc(raid, god, WardenChargeCircle.BASE_PLATFORM_LOC)

    private fun syncWardenChargePlatformLoc(
        raid: ToaRaidSession,
        god: WardenGod,
        targetId: Int,
    ): Boolean {
        val tile =
            wardenRoomCoord(
                raid,
                ToaRoom.WARDENS,
                if (god == WardenGod.ELIDINIS) WardenPhaseOneLayout.ELIDINIS_X
                else WardenPhaseOneLayout.TUMEKEN_X,
                WardenPhaseOneLayout.WARDEN_Z,
            )
        val states =
            locs.findAll(tile).filter { it.id in WardenChargeCircle.PLATFORM_LOCS }.toList()
        val targetStates = states.filter { it.id == targetId }
        if (targetStates.isNotEmpty()) {
            targetStates.drop(1).forEach { locs.del(it, Int.MAX_VALUE) }
            states.filter { it.id != targetId }.forEach { locs.del(it, Int.MAX_VALUE) }
            return true
        }
        val target = requireNotNull(ServerCacheManager.getObject(targetId))
        val current = states.firstOrNull()
        if (current != null) {
            locs.change(current, target, Int.MAX_VALUE)
        } else {
            val angle = if (god == WardenGod.ELIDINIS) LocAngle.South else LocAngle.North
            log.error {
                "[ToA] missing ${god.displayName()} charging platform; restoring target " +
                    "instance=${raid.instance.id} tile=$tile target=$targetId"
            }
            locs.add(tile, target, Int.MAX_VALUE, angle, LocShape.CentrepieceStraight)
        }
        val replaced = locs.findAll(tile).any { it.id == targetId }
        if (!replaced) {
            log.error {
                "[ToA] failed ${god.displayName()} charging-platform replacement " +
                    "instance=${raid.instance.id} tile=$tile target=$targetId"
            }
        }
        return replaced
    }

    private fun sendWardenChargeProjectile(
        start: CoordGrid,
        target: CoordGrid,
        graphic: Int,
        startTimeClientCycles: Int,
        endTimeClientCycles: Int,
    ) {
        require(startTimeClientCycles >= 0)
        require(endTimeClientCycles >= startTimeClientCycles)
        world.projAnim(
            ProjAnim(
                spotanim = graphic,
                startHeight = 6,
                endHeight = 6,
                startTime = startTimeClientCycles,
                endTime = endTimeClientCycles,
                angle = 0,
                progress = 16,
                sourceIndex = 0,
                targetIndex = 0,
                startCoord = start,
                endCoord = target,
            )
        )
    }

    private fun beginWardenPhaseOneSpecial(
        raid: ToaRaidSession,
        obelisk: Npc,
        targets: List<Player>,
        special: WardenPhaseOneSpecial,
    ) {
        val state = raid.encounter
        state.wardenPhaseOneSpecials[special.god] = special
        state.wardenPhaseOneSpecialEndsAt[special.god] =
            clock.cycle + WardenPhaseOneMechanics.SPECIAL_DURATION_TICKS
        when (special) {
            WardenPhaseOneSpecial.RED_PYRAMID,
            WardenPhaseOneSpecial.GOLD_PYRAMID -> {
                val pyramidId =
                    if (special == WardenPhaseOneSpecial.RED_PYRAMID)
                        WardenPhaseOneMechanics.RED_PYRAMID_LOC
                    else WardenPhaseOneMechanics.GOLD_PYRAMID_LOC
                val pyramidType = requireNotNull(ServerCacheManager.getObject(pyramidId))
                targets.forEach { target ->
                    val southwest =
                        CoordGrid(
                            target.coords.x - WardenPhaseOneMechanics.PYRAMID_CENTER_OFFSET,
                            target.coords.z - WardenPhaseOneMechanics.PYRAMID_CENTER_OFFSET,
                            target.coords.level,
                        )
                    locs
                        .findAll(southwest)
                        .filter { it.id in WardenPhaseOneMechanics.PYRAMID_LOCS }
                        .toList()
                        .forEach { locs.del(it, Int.MAX_VALUE) }
                    locs.add(
                        southwest,
                        pyramidType,
                        Int.MAX_VALUE,
                        arrayOf(LocAngle.North, LocAngle.East, LocAngle.South, LocAngle.West)[
                            (state.attackCounter +
                                    state.wardenPhaseOneHazardTiles[special.god].orEmpty().size)
                                .mod(4)],
                        LocShape.CentrepieceStraight,
                    )
                    state.wardenPhaseOneHazardTiles
                        .getOrPut(special.god) { linkedSetOf() }
                        .add(southwest)
                    for (dx in -1..1) {
                        for (dz in -1..1) {
                            world.spotanimMap(
                                SpotanimType(WardenFloor.WARNING_GRAPHIC),
                                CoordGrid(
                                    target.coords.x + dx,
                                    target.coords.z + dz,
                                    target.coords.level,
                                ),
                            )
                        }
                    }
                    state.pendingHits +=
                        ToaPendingHit(
                            target.uid,
                            target.coords,
                            clock.cycle + 3,
                            scaledDamage(raid, WardenPhaseOneMechanics.PYRAMID_DAMAGE),
                            ToaAssets.WARDEN_FLOOR,
                            radius = 1,
                            requireTargetOnTile = true,
                        )
                }
                message(
                    raid.party,
                    if (special == WardenPhaseOneSpecial.RED_PYRAMID)
                        "The red pyramid illuminates the floor. Move off the marked tiles."
                    else "The gold pyramid illuminates the floor. Move off the marked tiles.",
                )
            }
            WardenPhaseOneSpecial.ELIDINIS_ORB,
            WardenPhaseOneSpecial.TUMEKEN_ORB -> {
                val god = special.god
                val warden = wardenChargeActor(raid, god)
                if (warden == null) {
                    log.error {
                        "[ToA] missing ${god.displayName()} Phase 1 Warden for charged shot " +
                            "instance=${raid.instance.id}"
                    }
                    return
                }
                warden.anim(WardenPhaseOneMechanics.CHARGED_SHOT_ANIMATION)
                targets.forEach { target ->
                    val nearby =
                        targets.count {
                            it.coords.level == target.coords.level &&
                                it.coords.chebyshevDistance(target.coords) <= 1
                        }
                    val damage =
                        if (god == WardenGod.ELIDINIS) {
                            WardenPhaseOneMechanics.elidinisSpreadDamage(targets.size, nearby > 1)
                        } else {
                            WardenPhaseOneMechanics.tumekenStackDamage(nearby)
                        }
                    scheduleTrackingHit(
                        raid,
                        warden,
                        target,
                        WardenPhaseOneMechanics.chargedShotGraphic(god),
                        damage,
                        WardenPhaseOneMechanics.CHARGED_SHOT_TRAVEL_TICKS,
                        HitType.Typeless,
                        null,
                    )
                }
                message(raid.party, WardenPhaseOneMechanics.chargedShotWarning(god))
            }
        }
    }

    private fun finishWardenPhaseOneSpecial(raid: ToaRaidSession) {
        val state = raid.encounter
        state.wardenPhaseOneSpecials.keys.toList().forEach { finishWardenPhaseOneSpecial(raid, it) }
        clearWardenPhaseOneHazards(raid)
        state.wardenPhaseOneSpecials.clear()
        state.wardenPhaseOneSpecialEndsAt.clear()
    }

    private fun finishWardenPhaseOneSpecial(raid: ToaRaidSession, god: WardenGod) {
        clearWardenPhaseOneHazards(raid, god)
        raid.encounter.wardenPhaseOneSpecials.remove(god)
        raid.encounter.wardenPhaseOneSpecialEndsAt.remove(god)
    }

    private fun clearWardenPhaseOneHazards(raid: ToaRaidSession, god: WardenGod? = null) {
        val state = raid.encounter
        val hazardTiles =
            if (god == null) state.wardenPhaseOneHazardTiles.values.flatten()
            else state.wardenPhaseOneHazardTiles[god].orEmpty()
        hazardTiles.forEach { tile ->
            locs
                .findAll(tile)
                .filter { it.id in WardenPhaseOneMechanics.PYRAMID_LOCS }
                .toList()
                .forEach { locs.del(it, Int.MAX_VALUE) }
        }
        if (god == null) {
            state.wardenPhaseOneHazardTiles.clear()
        } else {
            state.wardenPhaseOneHazardTiles.remove(god)
        }
    }

    private fun tickWardenPhaseTwo(raid: ToaRaidSession, source: Npc, targets: List<Player>) {
        val state = raid.encounter
        val ruleset = state.wardensRuleset
        if (state.wardenNextPhaseTwoAttackAt < 0) {
            state.wardenNextPhaseTwoAttackAt = clock.cycle + ruleset.phaseTwoFirstAttackDelay
        }
        if (state.wardenNextPhaseTwoObeliskAttackAt < 0) {
            state.wardenNextPhaseTwoObeliskAttackAt =
                clock.cycle + ruleset.phaseTwoObeliskAttackInterval
        }
        if (clock.cycle >= state.wardenNextPhaseTwoObeliskAttackAt) {
            raid.spawnedNpcs
                .firstOrNull { it.hashCode() == state.wardenObeliskActor }
                ?.let { performWardenObeliskAttack(raid, it, targets) }
            state.wardenNextPhaseTwoObeliskAttackAt =
                WardenPhaseTwoCadence.advancePast(
                    clock.cycle,
                    state.wardenNextPhaseTwoObeliskAttackAt,
                    ruleset.phaseTwoObeliskAttackInterval,
                )
        }
        if (clock.cycle >= state.wardenNextPhaseTwoAttackAt) {
            performWardenPhaseTwoAttack(raid, source, targets)
            state.wardenNextPhaseTwoAttackAt =
                clock.cycle + ruleset.phaseTwoWardenAttackInterval(state.wardenPhaseTwoGod)
        }
        raid.nextMechanicAt =
            WardenPhaseTwoCadence.nextMechanicAt(
                state.wardenNextPhaseTwoAttackAt,
                state.wardenNextPhaseTwoObeliskAttackAt,
            )
    }

    private fun performWardenPhaseTwoAttack(
        raid: ToaRaidSession,
        source: Npc,
        targets: List<Player>,
    ) {
        val state = raid.encounter
        val ruleset = state.wardensRuleset
        state.attackCounter++
        val target = targets[state.attackCounter % targets.size]
        source.faceSquare(target.coords)
        if (state.attackCounter % ruleset.phaseTwoAttacksBeforeSpecial == 0) {
            source.anim(WardenPhaseTwoMechanics.SPECIAL_ATTACK_ANIMATION)
            val remainingPercent =
                state.wardenPhaseTwoRemaining * 100 / state.wardenPhaseTwoTrueMax.coerceAtLeast(1)
            if (remainingPercent > 55) {
                state.style = (state.style + 1) % 3
                val projectile = WardenPhaseTwoMechanics.divineProjectile(state.style)
                targets.forEach { player ->
                    val damage = rollWardenDamage(raid, WardenDamageRolls.DIVINE_PROJECTILE_MAX_HIT)
                    // Divine Projectile clears overheads before launch so the player must react
                    // to the sword/arrow/spell telegraph. Penetration belongs to the Obelisk.
                    player.disableProtectionPrayers()
                    scheduleTrackingHit(
                        raid,
                        source,
                        player,
                        projectile.projectile,
                        damage,
                        3,
                        projectile.hitType,
                        if (projectile.hitType == HitType.Melee) 90 else 80,
                        impactGraphic = projectile.impactGraphic,
                    )
                }
            } else {
                sendProjectile(
                    source,
                    target.coords,
                    WardenPhaseTwoMechanics.STONE_PROJECTILE,
                    travelTicks = 3,
                )
                state.pendingHits +=
                    ToaPendingHit(
                        target = target.uid,
                        tile = target.coords,
                        landsAt = clock.cycle + 3,
                        damage = scaledDamage(raid, 34),
                        impactGraphic = WardenPhaseTwoMechanics.STONE_GRAPHIC,
                        requireTargetOnTile = true,
                        wardenImpact = WardenImpact.IMPRISONMENT,
                    )
            }
        } else {
            val attack =
                WardenPhaseTwoMechanics.basicAttack(
                    source.coords.chebyshevDistance(target.coords),
                    state.attackCounter,
                )
            val damage =
                rollWardenDamage(raid, WardenDamageRolls.basicMaximum(state.wardenPhaseTwoGod))
            source.anim(attack.animation)
            if (attack.attack == WardenBasicAttack.MELEE) {
                queueAkkhaCombatHit(
                    target,
                    source,
                    scaledDamage(raid, damage),
                    1,
                    HitType.Melee,
                    90,
                )
            } else {
                scheduleTrackingHit(
                    raid,
                    source,
                    target,
                    requireNotNull(attack.projectile),
                    damage,
                    2,
                    attack.hitType,
                    80,
                    impactGraphic = attack.impactGraphic,
                )
            }
        }
    }

    private fun performWardenObeliskAttack(
        raid: ToaRaidSession,
        obelisk: Npc,
        targets: List<Player>,
    ) {
        val damage = raid.encounter.wardensRuleset.phaseTwoObeliskDamage(45)
        val special = WardenPhaseTwoMechanics.special(raid.encounter.wardenObeliskSpecialStep++)
        obelisk.anim(WardenPhaseTwoMechanics.obeliskAnimation(special))
        when (special) {
            WardenObeliskSpecial.CONVERGING_BEAM -> {
                val distances = listOf(2, 4, 6, 8, 10, 12)
                distances.forEachIndexed { index, distance ->
                    scheduleWardenAreaPulse(
                        raid,
                        ToaRoom.WARDENS,
                        WardenPhaseTwoMechanics.convergingBeam(distance, northSouth = true),
                        delay = 2 + index,
                        damage = damage,
                        warningGraphic = WardenPhaseTwoMechanics.BEAM_WARNING,
                        impactGraphic = WardenPhaseTwoMechanics.BEAM_ACTIVE,
                    )
                    scheduleWardenAreaPulse(
                        raid,
                        ToaRoom.WARDENS,
                        WardenPhaseTwoMechanics.convergingBeam(distance, northSouth = false),
                        delay = 7 + index,
                        damage = damage,
                        warningGraphic = WardenPhaseTwoMechanics.BEAM_WARNING,
                        impactGraphic = WardenPhaseTwoMechanics.BEAM_ACTIVE,
                    )
                }
                message(raid.party, "The Obelisk releases converging north/south beams.")
            }
            WardenObeliskSpecial.WINDMILL_BEAM -> {
                repeat(8) { step ->
                    scheduleWardenAreaPulse(
                        raid,
                        ToaRoom.WARDENS,
                        WardenPhaseTwoMechanics.windmill(step),
                        delay = 2 + step,
                        damage = damage,
                        warningGraphic = WardenPhaseTwoMechanics.WINDMILL_WARNING,
                        impactGraphic = WardenPhaseTwoMechanics.BEAM_ACTIVE,
                    )
                }
                message(raid.party, "The Obelisk's windmill beams rotate around the room.")
            }
            WardenObeliskSpecial.LIGHTNING_SKULL -> {
                val origin = wardenRoomCoord(raid, ToaRoom.WARDENS, 0, 0)
                targets.forEach { target ->
                    sendProjectile(
                        obelisk,
                        target.coords,
                        WardenPhaseTwoMechanics.LIGHTNING_SKULL_PROJECTILE,
                        travelTicks = 3,
                    )
                    val localX = target.coords.x - origin.x
                    val localZ = target.coords.z - origin.z
                    scheduleWardenAreaPulse(
                        raid,
                        ToaRoom.WARDENS,
                        WardenPhaseTwoMechanics.lightningSkull(localX, localZ),
                        delay = 3,
                        damage = damage,
                        warningGraphic = WardenFloor.WARNING_GRAPHIC,
                        impactGraphic = WardenFloor.LIGHTNING_GRAPHIC,
                    )
                }
                message(raid.party, "Lightning skulls land; stand in a cardinal safe lane.")
            }
        }
    }

    private fun tickWardenPhaseThree(raid: ToaRaidSession, source: Npc, targets: List<Player>) {
        val state = raid.encounter
        val ruleset = state.wardensRuleset
        val slamAt = state.wardenNextSlamAt.takeIf { it >= 0 } ?: clock.cycle
        if (clock.cycle >= slamAt) {
            state.attackCounter++
            val pattern = WardenFloor.pattern(state.wardenSlamStep++)
            source.anim(WardenFloor.slamAnimation(pattern))
            targets.forEach {
                world.spotanimMap(SpotanimType(WardenFloor.WARNING_GRAPHIC), it.coords)
            }
            state.wardenPendingSlam =
                WardenSlamRuntime(
                    pattern,
                    slamAt + ruleset.phaseThreeSlamWarningTicks,
                    source.hashCode(),
                )
            state.wardenNextSlamAt =
                WardenPhaseTwoCadence.advancePast(
                    clock.cycle,
                    slamAt,
                    ruleset.phaseThreeFloorSlamInterval,
                )
        }
        val nextPhantomAt =
            tickDueWardenPhantoms(raid, targets, ruleset.phaseThreePhantomAttackInterval)
        raid.nextMechanicAt = minOf(state.wardenNextSlamAt, nextPhantomAt)
    }

    private fun tickDueWardenPhantoms(
        raid: ToaRaidSession,
        targets: List<Player>,
        interval: Int,
    ): Int {
        val state = raid.encounter
        val iterator = state.wardenPhantomNextAttackAt.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val phantom =
                raid.spawnedNpcs.firstOrNull {
                    it.hashCode() == entry.key &&
                        it.isSlotAssigned &&
                        raid.npcRoles[it] == ToaNpcRole.HAZARD
                }
            if (phantom == null) {
                iterator.remove()
                continue
            }
            if (clock.cycle >= entry.value) {
                tickWardenPhantom(raid, phantom, targets, ++state.wardenPhantomAttackCounter)
                entry.setValue(
                    WardenPhaseTwoCadence.advancePast(clock.cycle, entry.value, interval)
                )
            }
        }
        val nextAt = state.wardenPhantomNextAttackAt.values.minOrNull() ?: Int.MAX_VALUE
        state.wardenNextPhantomAttackAt = nextAt
        return nextAt
    }

    private fun tickWardenPhantom(
        raid: ToaRaidSession,
        phantom: Npc,
        targets: List<Player>,
        cycle: Int,
    ) {
        val target = targets[cycle % targets.size]
        phantom.faceSquare(target.coords)
        val damage = rollWardenDamage(raid, WardenDamageRolls.PHANTOM_MAX_HIT)
        when (phantom.id) {
            TumekenWarden.BABA_PHANTOM ->
                scheduleTileHit(raid, phantom, target, ToaAssets.BABA_BOULDER, damage, 3, 0)
            TumekenWarden.KEPHRI_PHANTOM ->
                scheduleTileHit(raid, phantom, target, ToaAssets.KEPHRI_BOMB, damage, 3, 1)
            TumekenWarden.AKKHA_PHANTOM -> {
                val style = raid.encounter.wardenAkkhaPhantomStyle.mod(3)
                raid.encounter.wardenAkkhaPhantomStyle++
                when (style) {
                    0 ->
                        queueAkkhaCombatHit(
                            target,
                            phantom,
                            scaledDamage(raid, damage),
                            1,
                            HitType.Melee,
                            90,
                        )
                    1 ->
                        scheduleTrackingHit(
                            raid,
                            phantom,
                            target,
                            ToaAssets.WARDEN_ARROW,
                            damage,
                            2,
                            HitType.Ranged,
                            80,
                        )
                    else ->
                        scheduleTrackingHit(
                            raid,
                            phantom,
                            target,
                            ToaAssets.WARDEN_MAGIC,
                            damage,
                            2,
                            HitType.Magic,
                            80,
                        )
                }
            }
            else ->
                scheduleTrackingHit(
                    raid,
                    phantom,
                    target,
                    ToaAssets.WARDEN_ARROW,
                    damage,
                    2,
                    HitType.Ranged,
                    80,
                )
        }
    }

    private fun tickWardenEnrage(raid: ToaRaidSession, source: Npc, targets: List<Player>) {
        val state = raid.encounter
        val ruleset = state.wardensRuleset
        val lightningAt = state.wardenNextLightningAt.takeIf { it >= 0 } ?: clock.cycle
        if (clock.cycle >= lightningAt) {
            source.anim(9685)
            state.attackCounter++
            val origin = wardenRoomCoord(raid, ToaRoom.TUMEKEN_WARDEN, 0, 0)
            val playerTiles =
                targets.map { WardenLocalTile(it.coords.x - origin.x, it.coords.z - origin.z) }
            scheduleWardenAreaPulse(
                raid,
                ToaRoom.TUMEKEN_WARDEN,
                WardenFloor.lightningLocalTiles(
                    state.wardenFloorRow,
                    state.attackCounter,
                    playerTiles,
                ),
                delay = ruleset.phaseFourLightningWarningTicks,
                damage = 32,
                warningGraphic = WardenFloor.WARNING_GRAPHIC,
                impactGraphic = WardenFloor.LIGHTNING_GRAPHIC,
            )
            state.wardenNextLightningAt =
                WardenPhaseTwoCadence.advancePast(
                    clock.cycle,
                    lightningAt,
                    ruleset.phaseFourLightningInterval,
                )
        }
        val nextPhantomAt =
            tickDueWardenPhantoms(raid, targets, ruleset.phaseFourPhantomAttackInterval)
        raid.nextMechanicAt = minOf(state.wardenNextLightningAt, nextPhantomAt)
    }

    private fun processKephriMedic(raid: ToaRaidSession) {
        val state = raid.encounter
        val rules =
            KephriInvocationRules.resolve(
                raid.startSnapshot.invocations,
                raid.startSnapshot.partySize,
            )
        if (!rules.medicSwarmsOutsideShield || state.kephriRecovering || !raid.encounterStarted) {
            state.kephriNextMedicAt = -1
            return
        }
        val source = boss(raid) ?: return
        if (state.kephriNextMedicAt < 0) {
            state.kephriNextMedicAt = clock.cycle + rules.medicSpawnInterval
            return
        }
        if (clock.cycle < state.kephriNextMedicAt) return
        spawnKephriMedicSwarm(raid, rules)
        source.spotanim(ToaAssets.KEPHRI_BUG)
        state.kephriNextMedicAt = clock.cycle + rules.medicSpawnInterval
    }

    private fun spawnKephriMedicSwarm(raid: ToaRaidSession, rules: KephriInvocationProfile) {
        val state = raid.encounter
        val center = roomCoord(raid, ToaRoom.KEPHRI, true)
        val laneIndex =
            (state.kephriSwarmStartLane + state.kephriMedicSpawnIndex * 2 + 1) %
                KephriArenaLayout.swarmLanes.size
        val lane = KephriArenaLayout.swarmLanes[laneIndex]
        val spawn = CoordGrid(center.x + lane.spawnX, center.z + lane.spawnZ, center.level)
        val approach = CoordGrid(center.x + lane.approachX, center.z + lane.approachZ, center.level)
        val actor = spawnNpc(raid, KephriSwarms.NPC, spawn, ToaNpcRole.ADD)
        val hp =
            ToaRules.hitpoints(
                35,
                raid.startSnapshot.partySize,
                raid.raidLevel,
                raid.pathLevels[ToaPath.SCABARAS] ?: 0,
            )
        actor.baseHitpointsLvl = hp
        actor.hitpoints = hp
        actor.suppressHitpointsRegen = true
        actor.ignoreCombatInteractions = false
        actor.mode = null
        actor.abortRoute()
        actor.faceSquare(approach)
        actor.anim(KephriSwarms.SPAWN_ANIMATION)
        state.kephriSwarms[actor.hashCode()] =
            KephriSwarmRuntime(
                actorKey = actor.hashCode(),
                healEligible = true,
                approachTile = approach,
                startsMovingAt = clock.cycle + KephriSwarms.SPAWN_DELAY,
                medic = true,
                moveInterval = rules.medicMoveInterval,
            )
        state.kephriMedicSpawnIndex++
    }

    private fun processKephriRecovery(raid: ToaRaidSession) {
        val state = raid.encounter
        if (!state.kephriRecovering) return
        val source = boss(raid) ?: return

        if (state.kephriRecoveryTotal == 0) {
            if (clock.cycle >= state.kephriNextSwarmAt) finishKephriRecovery(raid, source)
            return
        }
        if (
            state.kephriRecoverySwarms < state.kephriRecoveryTotal &&
                clock.cycle >= state.kephriNextSwarmAt
        ) {
            val pathLevel = raid.pathLevels[ToaPath.SCABARAS] ?: 0
            val batch = if (pathLevel >= 2 && (state.kephriRecoverySwarms / 4) % 2 == 1) 3 else 1
            repeat(batch.coerceAtMost(state.kephriRecoveryTotal - state.kephriRecoverySwarms)) {
                spawnKephriSwarm(raid)
            }
            state.kephriNextSwarmAt =
                clock.cycle + KephriSwarms.spawnInterval(state.kephriRecoverySwarms)
        }
        if (
            state.kephriRecoverySwarms >= state.kephriRecoveryTotal &&
                clock.cycle >= state.kephriNextSwarmAt
        ) {
            finishKephriRecovery(raid, source)
        }
    }

    private fun spawnKephriSwarm(raid: ToaRaidSession) {
        val state = raid.encounter
        val center = roomCoord(raid, ToaRoom.KEPHRI, true)
        val index = state.kephriRecoverySwarms
        val laneIndex =
            (state.kephriSwarmStartLane + index + index / 7) % KephriArenaLayout.swarmLanes.size
        val lane = KephriArenaLayout.swarmLanes[laneIndex]
        val spawn = CoordGrid(center.x + lane.spawnX, center.z + lane.spawnZ, center.level)
        val approach = CoordGrid(center.x + lane.approachX, center.z + lane.approachZ, center.level)
        val actor = spawnNpc(raid, KephriSwarms.NPC, spawn, ToaNpcRole.ADD)
        val hp =
            ToaRules.hitpoints(
                35,
                raid.startSnapshot.partySize,
                raid.raidLevel,
                raid.pathLevels[ToaPath.SCABARAS] ?: 0,
            )
        actor.baseHitpointsLvl = hp
        actor.hitpoints = hp
        actor.suppressHitpointsRegen = true
        actor.ignoreCombatInteractions = false
        actor.mode = null
        actor.abortRoute()
        actor.faceSquare(approach)
        actor.anim(KephriSwarms.SPAWN_ANIMATION)
        state.kephriSwarms[actor.hashCode()] =
            KephriSwarmRuntime(
                actorKey = actor.hashCode(),
                healEligible = index < state.kephriRecoveryHealCap,
                approachTile = approach,
                startsMovingAt = clock.cycle + KephriSwarms.SPAWN_DELAY,
            )
        state.kephriRecoverySwarms++
    }

    private fun processKephriSwarms(raid: ToaRaidSession, source: Npc) {
        val state = raid.encounter
        state.kephriSwarms.values.toList().forEach { runtime ->
            val actor =
                raid.spawnedNpcs.firstOrNull { it.hashCode() == runtime.actorKey }
                    ?: run {
                        state.kephriSwarms.remove(runtime.actorKey)
                        if (runtime.healEligible && !runtime.medic) state.kephriRecoveryResolved++
                        return@forEach
                    }
            if (clock.cycle < runtime.startsMovingAt) return@forEach
            if (actor.coords.chebyshevDistance(runtime.approachTile) <= 0) {
                if (runtime.settlesAt < 0) {
                    runtime.settlesAt = clock.cycle + KephriSwarms.ARRIVAL_SETTLE_TICKS
                    return@forEach
                }
                if (clock.cycle >= runtime.settlesAt) {
                    resolveKephriSwarm(raid, actor, healed = true)
                }
                return@forEach
            }
            if (clock.cycle >= runtime.nextMoveAt) {
                val step = KephriSwarms.nextStep(actor.coords, runtime.approachTile)
                actor.walk(step)
                runtime.nextMoveAt = clock.cycle + runtime.moveInterval
            }
        }
    }

    private fun resolveKephriSwarm(raid: ToaRaidSession, actor: Npc, healed: Boolean) {
        val state = raid.encounter
        val runtime = state.kephriSwarms.remove(actor.hashCode()) ?: return
        if (runtime.healEligible && !runtime.medic) state.kephriRecoveryResolved++
        if (runtime.healEligible && healed) {
            val source = boss(raid)
            if (source != null) {
                source.hitpoints =
                    (source.hitpoints +
                            KephriSwarms.healAmount(
                                source.baseHitpointsLvl,
                                raid.startSnapshot.partySize,
                            ))
                        .coerceAtMost(KephriSwarms.shieldCap(source.baseHitpointsLvl))
                source.spotanim(ToaAssets.KEPHRI_BUG)
                forEachOnline(raid) { it.soundSynth(6462) }
            }
        }
        if (healed) removeNpc(raid, actor)
    }

    private fun finishKephriRecovery(raid: ToaRaidSession, source: Npc) {
        val state = raid.encounter
        state.kephriSwarms.keys.toList().forEach { key ->
            raid.spawnedNpcs
                .firstOrNull { it.hashCode() == key }
                ?.let {
                    it.anim(9608)
                    removeNpc(raid, it)
                }
        }
        state.kephriSwarms.clear()
        source.hitpoints = source.hitpoints.coerceAtLeast(1)
        source.transmog(
            requireNotNull(ServerCacheManager.getNpc(KephriEncounter.ACTIVE)),
            Int.MAX_VALUE,
        )
        source.spotanim(KephriEncounter.REFORM_GRAPHIC)
        source.anim(KephriEncounter.RECOVER_ANIMATION)
        source.ignoreCombatInteractions = true
        source.moveRestrict = MoveRestrict.NoMove
        source.mode = null
        state.kephriRecovering = false
        state.kephriNextSwarmAt = -1
        state.kephriNextBomberBombAt = -1
        state.kephriRecoveryTotal = 0
        state.kephriRecoveryHealCap = 0
        state.kephriRecoveryResolved = 0
        state.kephriAttacksUntilSpecial =
            if (state.phase == 1) 4 else KephriAttackCycle.BETWEEN_SHIELDED_SPECIALS
        state.kephriNextSpecialDung = true
        raid.nextMechanicAt = clock.cycle + 13
        message(raid.party, "Kephri's restored shield is vulnerable again.")
    }

    private fun processWardenTransitions(raid: ToaRaidSession) {
        val state = raid.encounter
        if (raid.currentRoom !in setOf(ToaRoom.WARDENS, ToaRoom.TUMEKEN_WARDEN)) return
        if (state.wardenStage == ToaWardenStage.DEATH_SEQUENCE) {
            processWardenDeathSequence(raid)
            return
        }
        if (state.wardenStage == ToaWardenStage.PHASE_THREE_TRANSITION) {
            processWardenPhaseThreeTransition(raid)
            return
        }
        if (raid.currentRoom == ToaRoom.WARDENS) {
            recoverWardenPhaseOneTransition(raid)
            processWardenPhaseOneCharge(raid)
            processPendingWardenCore(raid)
        }
        state.wardenStoneReleaseAt
            .filterValues { clock.cycle >= it }
            .keys
            .toList()
            .forEach { uid ->
                state.wardenStoneReleaseAt.remove(uid)
                uid.resolve(players)?.let(CombatEffects::unfreeze)
            }
        resolveWardenAreaPulses(raid)
        if (state.wardenStage == ToaWardenStage.PHASE_ONE_TRANSITION) {
            processWardenPhaseOneTransition(raid)
            return
        }
        if (state.wardenStage == ToaWardenStage.ENRAGE) processWardenFloorCollapse(raid)
        resolveWardenSlamIfDue(raid)
        if (
            raid.currentRoom == ToaRoom.WARDENS &&
                state.wardenStage == ToaWardenStage.CORE &&
                state.wardenCoreReturnsAt >= 0 &&
                clock.cycle >= state.wardenCoreReturnsAt
        ) {
            raid.npcRoles.entries
                .firstOrNull { it.value == ToaNpcRole.CORE }
                ?.key
                ?.let { resolveWardenCore(raid, it, null) }
            return
        }
        if (raid.currentRoom != ToaRoom.TUMEKEN_WARDEN) return
        if (state.wardenStage == ToaWardenStage.ENERGY_SIPHONS) {
            processPendingWardenSiphons(raid)
            if (state.wardenSiphonProjectileAt >= 0 || state.wardenSiphonsSpawnAt >= 0) {
                return
            }
            if (raid.npcRoles.values.none { it == ToaNpcRole.SKULL }) {
                finishWardenSiphons(raid, success = true)
            } else if (state.wardenSiphonEndsAt >= 0 && clock.cycle >= state.wardenSiphonEndsAt) {
                expireWardenSiphons(raid)
            }
            return
        }
        if (state.wardenStage !in setOf(ToaWardenStage.PHASE_THREE, ToaWardenStage.ENRAGE)) return
        val source = boss(raid) ?: return
        val percent =
            source.hitpoints.coerceAtLeast(0) * 100 / source.baseHitpointsLvl.coerceAtLeast(1)
        val threshold = TumekenWarden.siphonThresholds.getOrNull(state.wardenSkullsSent)
        if (threshold != null && percent <= threshold) {
            beginWardenSiphons(raid, source)
            return
        }
        if (!state.wardenEnraged && percent <= TumekenWarden.ENRAGE_PERCENT) {
            state.wardenEnraged = true
            state.wardenStage = ToaWardenStage.ENRAGE
            state.wardenFloorRow = 0
            state.wardenEnrageStartedAt = clock.cycle
            val ruleset = state.wardensRuleset
            val heal = source.baseHitpointsLvl * TumekenWarden.ENRAGE_HEAL_PERCENT / 100
            source.hitpoints = (source.hitpoints + heal).coerceAtMost(source.baseHitpointsLvl)
            state.wardenNextFloorBreakAt =
                state.wardenEnrageStartedAt + ruleset.phaseFourCollapseOffset(0)
            state.wardenNextLightningAt =
                state.wardenEnrageStartedAt + ruleset.phaseFourFirstLightningDelay
            state.wardenNextPhantomAttackAt =
                state.wardenPhantomNextAttackAt.values.minOrNull() ?: Int.MAX_VALUE
            raid.nextMechanicAt =
                minOf(state.wardenNextLightningAt, state.wardenNextPhantomAttackAt)
            source.baseDefenceLvl = ToaRules.combatStat(180, raid.raidLevel, 0)
            source.defenceLvl = source.baseDefenceLvl
            source.anim(9684)
            message(
                raid.party,
                "The Warden enrages, restores some health, and the arena begins to collapse!",
            )
        }
    }

    private fun scheduleWardenAreaPulse(
        raid: ToaRaidSession,
        room: ToaRoom,
        localTiles: Set<WardenLocalTile>,
        delay: Int,
        damage: Int,
        warningGraphic: Int,
        impactGraphic: Int,
        impact: WardenImpact? = null,
    ) {
        val tiles = localTiles.mapTo(linkedSetOf()) { wardenRoomCoord(raid, room, it.x, it.z) }
        raid.encounter.wardenAreaPulses +=
            WardenAreaPulse(
                tiles = tiles,
                warnsAt = clock.cycle + (delay - 2).coerceAtLeast(0),
                landsAt = clock.cycle + delay,
                damage = scaledDamage(raid, damage),
                warningGraphic = warningGraphic,
                impactGraphic = impactGraphic,
                impact = impact,
            )
    }

    private fun resolveWardenAreaPulses(raid: ToaRaidSession) {
        val source =
            if (raid.encounter.wardenStage == ToaWardenStage.PHASE_TWO) {
                raid.spawnedNpcs.firstOrNull { it.hashCode() == raid.encounter.wardenObeliskActor }
            } else {
                boss(raid)
            } ?: return
        val iterator = raid.encounter.wardenAreaPulses.iterator()
        while (iterator.hasNext()) {
            val pulse = iterator.next()
            if (!pulse.warned && clock.cycle >= pulse.warnsAt) {
                pulse.tiles.forEach { world.spotanimMap(SpotanimType(pulse.warningGraphic), it) }
                pulse.warned = true
            }
            if (clock.cycle < pulse.landsAt) continue
            iterator.remove()
            pulse.tiles.forEach { world.spotanimMap(SpotanimType(pulse.impactGraphic), it) }
            activePlayers(raid)
                .filter { it.coords in pulse.tiles }
                .forEach { player ->
                    if (
                        raid.encounter.wardenStage == ToaWardenStage.PHASE_TWO &&
                            raid.encounter.wardensRuleset.penetration
                    ) {
                        applyObeliskPenetration(player)
                    }
                    queueAkkhaCombatHit(player, source, pulse.damage, 1, HitType.Typeless, null)
                    pulse.impact?.let { applyWardenImpact(raid, player, it) }
                }
        }
    }

    private fun beginWardenPhaseOneTransition(raid: ToaRaidSession, hero: Player) {
        val state = raid.encounter
        if (state.wardenStage != ToaWardenStage.OBELISK) return
        finishWardenPhaseOneSpecial(raid)
        state.wardenChargeBalls.clear()
        state.wardenPhaseTwoGod =
            WardenObelisk.phaseTwoWarden(
                state.wardenElidinisChargeCycles * 100 + state.wardenElidinisCharge,
                state.wardenTumekenChargeCycles * 100 + state.wardenTumekenCharge,
            )
        val dyingGod =
            if (state.wardenPhaseTwoGod == WardenGod.ELIDINIS) WardenGod.TUMEKEN
            else WardenGod.ELIDINIS
        val dyingId =
            if (dyingGod == WardenGod.ELIDINIS) ElidinisWarden.CHARGING else TumekenWarden.CHARGING
        val dying = raid.spawnedNpcs.firstOrNull { it.id == dyingId && it.isSlotAssigned }
        clearWardenChargeCircle(raid)
        state.wardenStage = ToaWardenStage.PHASE_ONE_TRANSITION
        state.wardenTransitionHero = hero.uid
        state.wardenTransitionEndsAt =
            clock.cycle + WardenPhaseOneMechanics.TRANSITION_DURATION_TICKS
        state.wardenTransitionDestroyedAt =
            clock.cycle + WardenPhaseOneMechanics.TRANSITION_DESTROYED_DELAY_TICKS
        state.wardenTransitionDyingActor = dying?.hashCode() ?: -1
        state.wardenTransitionDestroyed = false
        dying?.anim(9669)
        dying?.spotanim(2216)
        raid.nextMechanicAt = Int.MAX_VALUE
        message(
            raid.party,
            "${dyingGod.displayName()} Warden falls as the opposite Warden powers up.",
        )
    }

    private fun recoverWardenPhaseOneTransition(raid: ToaRaidSession) {
        val state = raid.encounter
        if (state.wardenStage != ToaWardenStage.OBELISK) return
        val obelisk =
            raid.spawnedNpcs.firstOrNull {
                it.hashCode() == state.wardenObeliskActor && raid.npcRoles[it] == ToaNpcRole.OBELISK
            }
        if (
            !WardenObelisk.shouldRecoverPhaseTwoTransition(
                activated = state.wardenObeliskActivated,
                actorPresent = obelisk != null,
                actorAssigned = obelisk?.isSlotAssigned == true,
                hitpoints = obelisk?.hitpoints ?: 0,
            )
        ) {
            return
        }
        val hero = activePlayers(raid).firstOrNull() ?: return
        log.warn {
            "[ToA] recovered stalled Wardens P1 transition " +
                "instance=${raid.instance.id} obeliskPresent=${obelisk != null}"
        }
        beginWardenPhaseOneTransition(raid, hero)
    }

    private fun processWardenPhaseOneTransition(raid: ToaRaidSession) {
        val state = raid.encounter
        if (!state.wardenTransitionDestroyed && clock.cycle >= state.wardenTransitionDestroyedAt) {
            raid.spawnedNpcs
                .firstOrNull {
                    it.hashCode() == state.wardenTransitionDyingActor && it.isSlotAssigned
                }
                ?.let { dying ->
                    val destroyedId =
                        if (dying.id == ElidinisWarden.CHARGING) ElidinisWarden.DESTROYED
                        else TumekenWarden.DESTROYED
                    dying.transmog(
                        requireNotNull(ServerCacheManager.getNpc(destroyedId)),
                        Int.MAX_VALUE,
                    )
                    lockWardenActor(dying)
                }
            state.wardenTransitionDestroyed = true
        }
        if (clock.cycle < state.wardenTransitionEndsAt) return
        val hero = state.wardenTransitionHero?.resolve(players) ?: activePlayers(raid).firstOrNull()
        if (hero == null) {
            state.wardenTransitionEndsAt = clock.cycle + 1
            return
        }
        beginWardenPhaseTwo(raid, hero)
    }

    private fun processWardenFloorCollapse(raid: ToaRaidSession) {
        val state = raid.encounter
        pushPlayersFromCollapsedWardenFloor(raid)
        if (clock.cycle < state.wardenNextFloorBreakAt) return
        val rowIndex = state.wardenFloorRow
        val row = WardenFloor.collapseRows.getOrNull(rowIndex) ?: return
        val start = wardenRoomCoord(raid, ToaRoom.TUMEKEN_WARDEN, row.minLocalX, row.localZ)
        val end = wardenRoomCoord(raid, ToaRoom.TUMEKEN_WARDEN, row.maxLocalX, row.localZ)
        val void =
            wardenRoomCoord(
                raid,
                ToaRoom.TUMEKEN_WARDEN,
                WardenFloor.VOID_LOCAL_X,
                WardenFloor.VOID_LOCAL_Z,
            )
        forEachOnline(raid) { player ->
            WardenFloor.collapseCameraShake.forEach { shake ->
                player.client.write(CamShake(shake.axis, shake.random, shake.amplitude, shake.rate))
            }
        }
        for (x in start.x..end.x) {
            val tile = CoordGrid(x, start.z, start.level)
            sendWardenFloorProjectile(tile, void)
        }
        WardenFloor.replacementChanges(rowIndex, raid.startSnapshot.seed).forEach { change ->
            replaceWardenFloorLoc(raid, change)
        }
        val soundTile =
            wardenRoomCoord(raid, ToaRoom.TUMEKEN_WARDEN, WardenFloor.VOID_LOCAL_X, row.localZ)
        world.soundArea(soundTile, 6247, radius = 9)
        world.soundArea(soundTile, 6153, delay = 30, radius = 9)
        world.soundArea(soundTile, 6124, delay = 60, radius = 9)
        world.soundArea(soundTile, 6219, delay = 90, radius = 9)
        state.wardenFloorRow++
        pushPlayersFromCollapsedWardenFloor(raid)
        state.wardenNextFloorBreakAt =
            if (state.wardenFloorRow < WardenFloor.collapseRows.size) {
                state.wardenEnrageStartedAt +
                    state.wardensRuleset.phaseFourCollapseOffset(state.wardenFloorRow)
            } else {
                Int.MAX_VALUE
            }
    }

    private fun replaceWardenFloorLoc(raid: ToaRaidSession, change: WardenFloorLocChange) {
        val tile = wardenRoomCoord(raid, ToaRoom.TUMEKEN_WARDEN, change.localX, change.localZ)
        val visible = locs.findExact(tile, LocShape.GroundDecor)
        wardenFloorOriginalLocs.recordFirst(raid.instance.id, tile, visible)
        // LOC_ADD_CHANGE replaces one ground-decoration layer on the OSRS client. Explicitly
        // remove the currently visible state first so an instance never retains an older broken
        // edge beneath the permanent void seam sent by a later row collapse.
        if (visible != null) {
            locs.del(visible, Int.MAX_VALUE)
        }
        val angle = WardenFloor.rawLocAngle(change.rotation)
        locs.add(
            tile,
            requireNotNull(ServerCacheManager.getObject(change.locId)),
            Int.MAX_VALUE,
            angle,
            LocShape.GroundDecor,
        )
    }

    private fun restoreWardenFloor(raid: ToaRaidSession) {
        val originals = wardenFloorOriginalLocs.drain(raid.instance.id)
        originals.forEach { (tile, original) ->
            locs
                .findAll(tile)
                .filter {
                    it.shape == LocShape.GroundDecor && it.id in WardenFloor.replacementLocIds
                }
                .toList()
                .forEach { locs.del(it, Int.MAX_VALUE) }
            if (original != null) {
                locs.add(original, Int.MAX_VALUE)
            }
        }
    }

    private fun sendWardenFloorProjectile(source: CoordGrid, target: CoordGrid) {
        world.projAnim(
            ProjAnim(
                spotanim = WardenFloor.REMOVE_PROJECTILE,
                startHeight = 0,
                endHeight = 200,
                startTime = 0,
                endTime = 90,
                angle = 20,
                progress = 0,
                sourceIndex = 0,
                targetIndex = 0,
                startCoord = source,
                endCoord = target,
            )
        )
    }

    private fun pushPlayersFromCollapsedWardenFloor(raid: ToaRaidSession) {
        val state = raid.encounter
        if (state.wardenFloorRow <= 0) return
        val origin = wardenRoomCoord(raid, ToaRoom.TUMEKEN_WARDEN, 0, 0)
        activePlayers(raid).forEach { player ->
            val localX = player.coords.x - origin.x
            val localZ = player.coords.z - origin.z
            WardenFloor.pushedLocalTile(state.wardenFloorRow, localX, localZ)?.let { destination ->
                PathingEntityCommon.telejump(
                    player,
                    collision,
                    wardenRoomCoord(raid, ToaRoom.TUMEKEN_WARDEN, destination.x, destination.z),
                )
                player.mes("The collapsing floor forces you onto the remaining tiles.")
            }
        }
    }

    private fun beginWardenSiphons(raid: ToaRaidSession, source: Npc) {
        val state = raid.encounter
        val phase = state.wardenSkullsSent
        spawnNextWardenPhantom(raid, phase)
        state.wardenSkullsSent++
        state.wardenSkullsActive = true
        state.wardenSlamInterruptedAt = clock.cycle
        state.wardenPendingSlam = null
        state.wardenStage = ToaWardenStage.ENERGY_SIPHONS
        val invulnerableId =
            if (state.wardenPhaseThreeGod == WardenGod.ELIDINIS) ElidinisWarden.FINAL_INVULNERABLE
            else TumekenWarden.FINAL_INVULNERABLE
        source.transmog(requireNotNull(ServerCacheManager.getNpc(invulnerableId)), Int.MAX_VALUE)
        lockWardenActor(source)
        source.anim(9682)
        val livePartySize = activePlayers(raid).size.coerceAtLeast(1)
        val count = WardenEnergySiphons.count(livePartySize, phase)
        state.wardenSiphonPartySize = livePartySize
        state.wardenSiphonPhase = phase
        state.wardenSiphonsSpawned = count
        state.wardenSiphonProjectileAt = clock.cycle + 2
        state.wardenSiphonsSpawnAt = clock.cycle + WardenEnergySiphons.SPAWN_DELAY_TICKS
        state.wardenSiphonLinkAt = clock.cycle + WardenEnergySiphons.LINK_DELAY_TICKS
        state.wardenSiphonEndsAt = -1
        raid.nextMechanicAt = state.wardenSiphonProjectileAt
        message(
            raid.party,
            "The Warden summons $count Energy Siphons. Disable each one with melee.",
        )
    }

    private fun processPendingWardenSiphons(raid: ToaRaidSession) {
        val state = raid.encounter
        if (state.wardenStage != ToaWardenStage.ENERGY_SIPHONS) return
        val source = boss(raid) ?: return
        val count = state.wardenSiphonsSpawned.coerceAtLeast(1)
        val tiles =
            WardenEnergySiphons.localTiles(
                    state.wardenSiphonPartySize,
                    state.wardenSiphonPhase,
                    count,
                )
                .map { local -> wardenRoomCoord(raid, ToaRoom.TUMEKEN_WARDEN, local.x, local.z) }
        if (state.wardenSiphonProjectileAt >= 0 && clock.cycle >= state.wardenSiphonProjectileAt) {
            tiles.forEach { tile ->
                sendProjectile(
                    source,
                    tile,
                    WardenEnergySiphons.OUTWARD_PROJECTILE,
                    travelTicks = 4,
                )
            }
            state.wardenSiphonProjectileAt = -1
            raid.nextMechanicAt = state.wardenSiphonsSpawnAt
        }
        if (state.wardenSiphonsSpawnAt >= 0 && clock.cycle >= state.wardenSiphonsSpawnAt) {
            val siphonHitpoints = WardenEnergySiphons.hitpoints(state.wardenSiphonPartySize)
            tiles.forEach { tile ->
                world.spotanimMap(SpotanimType(WardenEnergySiphons.LANDING_GRAPHIC), tile)
                spawnNpc(raid, WardenEnergySiphons.ACTIVE, tile, ToaNpcRole.SKULL).also { siphon ->
                    siphon.baseHitpointsLvl = siphonHitpoints
                    siphon.hitpoints = siphonHitpoints
                    siphon.suppressHitpointsRegen = true
                    lockWardenActor(siphon)
                }
            }
            state.wardenSiphonsSpawnAt = -1
            state.wardenSiphonEndsAt =
                clock.cycle + state.wardensRuleset.energySiphonDuration(count)
            raid.nextMechanicAt = minOf(state.wardenSiphonLinkAt, state.wardenSiphonEndsAt)
        }
        if (state.wardenSiphonLinkAt >= 0 && clock.cycle >= state.wardenSiphonLinkAt) {
            raid.npcRoles
                .filter { (npc, role) ->
                    role == ToaNpcRole.SKULL ||
                        (role == ToaNpcRole.HAZARD && npc.id == WardenEnergySiphons.DISABLED)
                }
                .keys
                .forEach { siphon ->
                    sendProjectile(
                        source,
                        siphon.coords,
                        WardenEnergySiphons.LINK_PROJECTILE,
                        travelTicks = 2,
                    )
                }
            state.wardenSiphonLinkAt = clock.cycle + WardenEnergySiphons.LINK_INTERVAL_TICKS
            raid.nextMechanicAt = minOf(state.wardenSiphonLinkAt, state.wardenSiphonEndsAt)
        }
    }

    private fun spawnNextWardenPhantom(raid: ToaRaidSession, phase: Int) {
        val state = raid.encounter
        val phantomIndex = TumekenWarden.phantomIndexForSiphonPhase(phase) ?: return
        if (state.wardenPhantomsSpawned > phantomIndex) return
        val ids = TumekenWarden.phantomIds(state.wardenPhaseThreeGod)
        val tiles = arrayOf(38 to 33, 22 to 33)
        spawnNpc(
                raid,
                ids[phantomIndex],
                wardenRoomCoord(
                    raid,
                    ToaRoom.TUMEKEN_WARDEN,
                    tiles[phantomIndex].first,
                    tiles[phantomIndex].second,
                ),
                ToaNpcRole.HAZARD,
            )
            .also { phantom ->
                lockWardenActor(phantom)
                state.wardenPhantomNextAttackAt[phantom.hashCode()] =
                    clock.cycle + state.wardensRuleset.phaseThreePhantomAttackInterval
            }
        state.wardenPhantomsSpawned++
    }

    private fun expireWardenSiphons(raid: ToaRaidSession) {
        val state = raid.encounter
        val alive = raid.npcRoles.filterValues { it == ToaNpcRole.SKULL }.keys.toList()
        val disabled =
            raid.npcRoles
                .filter { (npc, role) ->
                    role == ToaNpcRole.HAZARD && npc.id == WardenEnergySiphons.DISABLED
                }
                .keys
                .toList()
        val source = boss(raid)
        (alive + disabled).forEach { siphon ->
            world.spotanimMap(SpotanimType(WardenEnergySiphons.FAILURE_GRAPHIC), siphon.coords)
        }
        if (source != null) {
            source.anim(WardenEnergySiphons.FAILURE_ANIMATION)
            val damage = state.wardensRuleset.energySiphonFailureDamagePerTarget * alive.size
            if (damage > 0) {
                activePlayers(raid).forEach { player ->
                    queueAkkhaCombatHit(player, source, damage, 1, HitType.Typeless, null)
                }
            }
        }
        alive.forEach { removeNpc(raid, it) }
        finishWardenSiphons(raid, success = false)
    }

    private fun resolveWardenSlamIfDue(raid: ToaRaidSession) {
        val state = raid.encounter
        if (state.wardenStage !in setOf(ToaWardenStage.PHASE_THREE, ToaWardenStage.ENRAGE)) {
            state.wardenPendingSlam = null
            return
        }
        val slam = state.wardenPendingSlam ?: return
        if (clock.cycle < slam.landsAt) return
        state.wardenPendingSlam = null
        val source =
            raid.spawnedNpcs.firstOrNull { it.hashCode() == slam.sourceKey } ?: boss(raid) ?: return
        val centerX = fightCoord(raid, ToaRoom.TUMEKEN_WARDEN).x
        activePlayers(raid)
            .filterNot { WardenFloor.isSafe(slam.pattern, centerX, it.coords.x) }
            .forEach { player ->
                world.spotanimMap(SpotanimType(WardenFloor.BREAK_GRAPHIC), player.coords)
                queueAkkhaCombatHit(
                    player,
                    source,
                    scaledDamage(raid, 40),
                    1,
                    HitType.Typeless,
                    null,
                )
            }
    }

    private fun processAkkhaThresholds(raid: ToaRaidSession) {
        val source = boss(raid) ?: return
        val state = raid.akkha
        if (state.state != AkkhaState.NORMAL_COMBAT || state.pendingThresholds.isEmpty()) return
        startAkkhaShadowTransition(raid, source)
    }

    private fun startAkkhaShadowTransition(raid: ToaRaidSession, source: Npc) {
        val threshold = raid.akkha.beginNextShadow(clock.cycle) ?: return
        source.clearInteraction()
        source.abortRoute()
        source.mode = null
        clearAkkhaShadows(raid)
        source.transmog(
            requireNotNull(ServerCacheManager.getNpc(raid.akkha.style.npcId)),
            Int.MAX_VALUE,
        )
        source.apRangeOverride = akkhaAttackRange(raid.akkha.style)
        ensureAkkhaShadows(raid, restoreHitpoints = true)
        rearmAkkhaAggression(raid, source, force = true)
        traceAkkha(
            raid,
            source,
            "shadow_start",
            "shadowThreshold" to threshold,
            "shadowTimer" to raid.akkha.shadowChargeEndsAt,
        )
        message(
            raid.party,
            if (threshold == 80) "Shadows appear throughout the room!"
            else "The shadows in the room are restored!",
        )
    }

    private fun processAkkhaTimedMechanics(raid: ToaRaidSession) {
        val source = boss(raid) ?: return
        val state = raid.akkha
        processAkkhaMemoryVisibility(raid, source)
        when (state.state) {
            AkkhaState.INTRO ->
                if (clock.cycle >= state.specialEndsAt) {
                    source.transmog(
                        requireNotNull(ServerCacheManager.getNpc(AkkhaAssets.MELEE)),
                        Int.MAX_VALUE,
                    )
                    source.anim(AkkhaAssets.TELEPORT_APPEAR_SPEAR)
                    state.startNormalCombat()
                    source.apRangeOverride = akkhaAttackRange(state.style)
                    rearmAkkhaAggression(raid, source, force = true)
                    traceAkkha(raid, source, "intro_end", "combatStyle" to state.style)
                }
            AkkhaState.SPECIAL_ATTACK -> {
                if (AkkhaSpecial.MEMORY_BLAST in state.activeSpecials) {
                    state.pauseShadowCharge()
                } else {
                    processAkkhaShadowCharge(raid, source)
                }
                if (clock.cycle >= state.specialEndsAt) {
                    val wasMemory = AkkhaSpecial.MEMORY_BLAST in state.activeSpecials
                    val previousStyle = state.style
                    state.finishSpecial()
                    state.target = null
                    if (state.style != previousStyle) {
                        akkhaLastStyleChangeAt[raid.instance.id] = clock.cycle
                        playAkkhaStyleChangeSound(source)
                    }
                    if (state.state == AkkhaState.SHADOW_TRANSITION)
                        startAkkhaShadowTransition(raid, source)
                    else {
                        source.transmog(
                            requireNotNull(ServerCacheManager.getNpc(state.style.npcId)),
                            Int.MAX_VALUE,
                        )
                        if (!wasMemory) source.anim(AkkhaAssets.TELEPORT_APPEAR_SPEAR)
                        source.apRangeOverride = akkhaAttackRange(state.style)
                        rearmAkkhaAggression(raid, source, force = true)
                        traceAkkha(raid, source, "special_end", "combatStyle" to state.style)
                    }
                }
            }
            AkkhaState.SHADOW_TRANSITION -> {
                state.pendingLureQuadrant?.let { quadrant ->
                    if (
                        akkhaBossInsideQuadrant(raid, source, quadrant) &&
                            state.confirmLure(quadrant)
                    ) {
                        state.target = null
                        traceAkkha(raid, source, "shadow_lure_complete", "quadrant" to quadrant)
                    }
                }
                processAkkhaForcedStyleSwitch(raid, source)
                maintainAkkhaAggression(raid, source)
                processAkkhaShadowCharge(raid, source)
            }
            AkkhaState.FINAL_STAND_INTRO -> processAkkhaFinalStandIntro(raid, source)
            AkkhaState.NORMAL_COMBAT -> {
                processAkkhaForcedStyleSwitch(raid, source)
                maintainAkkhaAggression(raid, source)
                processAkkhaShadowCharge(raid, source)
            }
            else -> Unit
        }
        processAkkhaMemoryBlast(raid, source)
        processAkkhaDetonation(raid, source)
        processAkkhaTrailingOrbs(raid, source)
        processAkkhaStatuses(raid, source)
    }

    private fun processAkkhaForcedStyleSwitch(raid: ToaRaidSession, source: Npc) {
        val state = raid.akkha
        if (ToaInvocation.STAY_VIGILANT !in raid.startSnapshot.invocations) return
        val lastStyleChange = akkhaLastStyleChangeAt.getOrPut(raid.instance.id) { clock.cycle }
        if (clock.cycle - lastStyleChange < AKKHA_FORCED_STYLE_SWITCH_TICKS) return
        val previous = state.style
        state.style = state.style.next()
        akkhaLastStyleChangeAt[raid.instance.id] = clock.cycle
        state.target = null
        source.transmog(requireNotNull(ServerCacheManager.getNpc(state.style.npcId)), Int.MAX_VALUE)
        source.apRangeOverride =
            AkkhaAggressionPolicy.attackRange(
                state.style,
                lureRequired = state.pendingLureQuadrant != null,
            )
        playAkkhaStyleChangeSound(source)
        traceAkkha(
            raid,
            source,
            "idle_style_switch",
            "previousStyle" to previous,
            "nextStyle" to state.style,
        )
    }

    private fun playAkkhaStyleChangeSound(source: Npc) {
        world.soundArea(source.coords, AKKHA_STYLE_CHANGE_SOUND, radius = 14, size = source.size)
    }

    private fun processAkkhaMemoryVisibility(raid: ToaRaidSession, source: Npc) {
        val state = raid.akkha
        if (AkkhaSpecial.MEMORY_BLAST !in state.activeSpecials) return
        val hideAt = state.memoryHideAt
        when (clock.cycle) {
            hideAt - 2 ->
                activePlayers(raid).forEach {
                    it.mes("<col=a53fff>Sections of the room start to glow...</col>")
                }
            hideAt - 1 -> source.anim(AkkhaAssets.TELEPORT_DISAPPEAR_SPEAR)
        }
        when (state.pollMemoryVisibility(clock.cycle)) {
            AkkhaMemoryVisibilityTransition.HIDE -> {
                source.clearInteraction()
                source.abortRoute()
                source.mode = null
                source.ignoreCombatInteractions = true
                npcs.hide(
                    source,
                    akkhaMemoryRepositoryHideDuration(clock.cycle, state.memoryReappearAt),
                )
                traceAkkha(raid, source, "memory_hide")
            }
            AkkhaMemoryVisibilityTransition.REAPPEAR -> {
                clearAkkhaMemorySymbols(raid)
                PathingEntityCommon.telejump(
                    source,
                    collision,
                    akkhaCoord(raid, AkkhaArenaLayout.CENTER_X, AkkhaArenaLayout.CENTER_Z - 2),
                )
                source.hidden = false
                source.ignoreCombatInteractions = false
                source.transmog(
                    requireNotNull(ServerCacheManager.getNpc(state.style.npcId)),
                    Int.MAX_VALUE,
                )
                source.anim(AkkhaAssets.TELEPORT_APPEAR_SPEAR)
                source.apRangeOverride = akkhaAttackRange(state.style)
                traceAkkha(raid, source, "memory_reappear", "tile" to source.coords)
            }
            null -> Unit
        }
    }

    private fun processAkkhaShadowCharge(raid: ToaRaidSession, source: Npc) {
        val state = raid.akkha
        updateAkkhaShadowHeadbars(raid)
        if (state.activeShadowThreshold == null && state.pendingThresholds.isNotEmpty())
            startAkkhaShadowTransition(raid, source)
        if (!state.hasChargingShadows()) return
        state.shadowBlast?.let {
            processAkkhaShadowBlast(raid, source, it)
            if (!state.hasChargingShadows()) return
            return
        }
        if (clock.cycle < state.shadowChargeEndsAt) return
        val center = roomCoord(raid, ToaRoom.AKKHA, true)
        val living = livingShadowQuadrants(raid, center)
        val blast = state.startShadowBlast(clock.cycle, living) ?: return
        traceAkkha(
            raid,
            source,
            "shadow_expired",
            "shadowThreshold" to state.activeShadowThreshold,
            "quadrants" to living.joinToString(),
            "blastResolvesAt" to blast.resolvesAt,
        )
        processAkkhaShadowBlast(raid, source, blast)
    }

    private fun processAkkhaShadowBlast(raid: ToaRaidSession, source: Npc, blast: AkkhaFloorBlast) {
        val state = raid.akkha
        val center = roomCoord(raid, ToaRoom.AKKHA, true)
        val waveTick = clock.cycle - blast.startedAt
        if (clock.cycle < blast.resolvesAt) {
            if (blast.renderedWaveTick < 0) {
                renderAkkhaFloorBlast(center, blast.quadrants)
                blast.renderedWaveTick = 0
            }
            return
        }
        activePlayers(raid).forEach { player ->
            val element = AkkhaFloorBlastController.quadrantAt(center, player.coords)
            if (element != null && element in blast.quadrants) {
                queueSpecialHit(
                    player,
                    source,
                    scaledDamage(raid, AkkhaTuning.SHADOW_CHARGE_MAX_HIT.value),
                    1,
                )
                applyAkkhaElement(raid, player, source, element)
            }
        }
        state.finishShadowBlast(clock.cycle)
        updateAkkhaShadowHeadbars(raid)
        traceAkkha(
            raid,
            source,
            "shadow_recharged",
            "shadowThreshold" to state.activeShadowThreshold,
            "quadrants" to blast.quadrants.joinToString(),
            "nextChargeAt" to state.shadowChargeEndsAt,
        )
    }

    private fun processAkkhaMemoryBlast(raid: ToaRaidSession, source: Npc) {
        val state = raid.akkha
        val center = roomCoord(raid, ToaRoom.AKKHA, true)
        state.memorySteps
            .filter { !it.revealed && clock.cycle >= it.revealAt }
            .forEach { step ->
                val firstReveal = state.memorySteps.none { it.revealed }
                showAkkhaMemorySymbol(raid, step.safeQuadrant)
                if (firstReveal) {
                    activePlayers(raid).forEach { it.soundSynth(AKKHA_MEMORY_FIRST_AREA_SOUND) }
                }
                step.revealed = true
                traceAkkha(
                    raid,
                    source,
                    "memory_reveal",
                    "quadrant" to step.safeQuadrant,
                    "resolveAt" to step.resolvesAt,
                )
            }
        state.memorySteps
            .filter { clock.cycle >= it.blastStartsAt && it.renderedWaveTick < 0 }
            .forEach { step ->
                renderAkkhaMemoryBlast(center, step.safeQuadrant)
                // The cache graphics contain their own multi-tile animation. Rendering once keeps
                // the three unsafe sections synchronized and prevents map-spotanim packet floods.
                step.renderedWaveTick = 0
            }
        val due = state.memorySteps.filter { clock.cycle >= it.resolvesAt }
        state.memorySteps.removeAll(due.toSet())
        due.forEach { step ->
            activePlayers(raid).forEach { player ->
                val occupied = AkkhaFloorBlastController.quadrantAt(center, player.coords)
                if (occupied != step.safeQuadrant) {
                    queueSpecialHit(
                        player,
                        source,
                        scaledDamage(raid, AkkhaTuning.MEMORY_BLAST_MAX_HIT.value),
                        1,
                    )
                    occupied?.let { applyAkkhaElement(raid, player, source, it) }
                }
            }
            traceAkkha(raid, source, "memory_resolve", "quadrant" to step.safeQuadrant)
        }
    }

    private fun renderAkkhaFloorBlast(center: CoordGrid, quadrants: Set<AkkhaQuadrant>) {
        quadrants.forEach { quadrant ->
            val tiles = AkkhaFloorBlastController.quadrantTiles(center, setOf(quadrant))
            val memoryDelays =
                tiles.associateWith {
                    AkkhaFloorBlastController.memoryBlastDelay(center, it, quadrant)
                }
            val maximum = memoryDelays.values.maxOrNull() ?: 30
            tiles.forEach { tile ->
                world.spotanimMap(
                    SpotanimType(quadrant.graphic),
                    tile,
                    height = if (quadrant == AkkhaQuadrant.BURN) AKKHA_MEMORY_BURN_HEIGHT else 0,
                    // Charge blasts travel from the clipped outside edge towards the centre.
                    delay = 30 + maximum - memoryDelays.getValue(tile),
                )
            }
        }
    }

    private fun renderAkkhaMemoryBlast(center: CoordGrid, safeQuadrant: AkkhaQuadrant) {
        for ((quadrant, tiles) in
            AkkhaFloorBlastController.memoryBlastTiles(center, safeQuadrant)) {
            for (tile in tiles) {
                world.spotanimMap(
                    SpotanimType(quadrant.graphic),
                    tile,
                    height = if (quadrant == AkkhaQuadrant.BURN) AKKHA_MEMORY_BURN_HEIGHT else 0,
                    delay = AkkhaFloorBlastController.memoryBlastDelay(center, tile, quadrant),
                )
            }
        }
    }

    private fun showAkkhaMemorySymbol(raid: ToaRaidSession, quadrant: AkkhaQuadrant) {
        clearAkkhaMemorySymbols(raid)
        val symbol = AKKHA_MEMORY_SYMBOLS.getValue(quadrant)
        val tile = akkhaCoord(raid, symbol.localX, symbol.localZ)
        locs.add(
            tile,
            requireNotNull(ServerCacheManager.getObject(symbol.locId)),
            Int.MAX_VALUE,
            LocAngle.North,
            LocShape.CentrepieceStraight,
        )
        activePlayers(raid).forEach { it.soundSynth(AKKHA_MEMORY_SYMBOL_SOUND) }
    }

    private fun clearAkkhaMemorySymbols(raid: ToaRaidSession) {
        val ids = AKKHA_MEMORY_SYMBOLS.values.mapTo(hashSetOf()) { it.locId }
        AKKHA_MEMORY_SYMBOLS.values.forEach { symbol ->
            val tile = akkhaCoord(raid, symbol.localX, symbol.localZ)
            locs
                .findAll(tile)
                .filter { it.id in ids }
                .toList()
                .forEach { locs.del(it, Int.MAX_VALUE) }
        }
    }

    private fun renderAkkhaQuadrantFloor(center: CoordGrid, quadrants: Set<AkkhaQuadrant>) {
        quadrants.forEach { quadrant ->
            world.spotanimMap(
                SpotanimType(quadrant.graphic),
                AkkhaFloorBlastController.markerTile(center, quadrant),
            )
        }
    }

    private fun processAkkhaDetonation(raid: ToaRaidSession, source: Npc) {
        val state = raid.akkha
        val detonation = state.detonation ?: return
        if (clock.cycle < detonation.resolvesAt) return
        state.detonation = null
        val center = roomCoord(raid, ToaRoom.AKKHA, true)
        val activeByUid = activePlayers(raid).associateBy { it.uid }
        // Marked participants remain locked from cast, but each explosion follows its marked
        // player and is centred on the tile occupied when the telegraph resolves.
        val origins =
            detonation.origins.keys.mapNotNull { uid ->
                activeByUid[uid]?.let { it.coords to akkhaQuadrant(center, it.coords) }
            }
        val blastTiles = linkedMapOf<CoordGrid, AkkhaQuadrant>()
        origins.forEach { (origin, element) ->
            for (dx in -AkkhaTuning.DETONATE_RADIUS.value..AkkhaTuning.DETONATE_RADIUS.value) {
                for (dz in -AkkhaTuning.DETONATE_RADIUS.value..AkkhaTuning.DETONATE_RADIUS.value) {
                    val tile = CoordGrid(origin.x + dx, origin.z + dz, origin.level)
                    if (
                        isAkkhaDetonationTile(tile, origin, detonation.diagonal) &&
                            insideAkkhaArena(center, tile)
                    ) {
                        blastTiles.putIfAbsent(tile, element)
                    }
                }
            }
        }
        blastTiles.forEach { (tile, element) ->
            world.spotanimMap(SpotanimType(element.graphic), tile)
        }
        activePlayers(raid).forEach { player ->
            val element = blastTiles[player.coords]
            if (element != null) {
                queueSpecialHit(
                    player,
                    source,
                    scaledDamage(raid, AkkhaTuning.DETONATE_MAX_HIT.value),
                    1,
                )
                applyAkkhaElement(raid, player, source, element)
                traceAkkha(
                    raid,
                    source,
                    "detonate_hit",
                    "target" to player.uid,
                    "targetTile" to player.coords,
                )
            }
        }
        traceAkkha(
            raid,
            source,
            "detonate_resolve",
            "origins" to origins.joinToString { it.first.toString() },
            "diagonal" to detonation.diagonal,
        )
    }

    private fun processAkkhaTrailingOrbs(raid: ToaRaidSession, source: Npc) {
        val state = raid.akkha
        val center = roomCoord(raid, ToaRoom.AKKHA, true)
        if (state.trailEndsAt >= 0) {
            when {
                clock.cycle >= state.trailEndsAt -> {
                    activePlayers(raid).forEach {
                        if (clock.cycle == state.trailEndsAt) {
                            it.mes("<col=229628>The magical orbs stop materialising.</col>")
                        }
                        it.clearTint()
                    }
                    state.trailStartsAt = -1
                    state.trailEndsAt = -1
                    state.trailLastTiles.clear()
                }
                clock.cycle < state.trailStartsAt ->
                    activePlayers(raid).forEach { state.trailLastTiles[it.uid] = it.coords }
                else -> {
                    if (clock.cycle == state.trailStartsAt) {
                        activePlayers(raid).forEach {
                            it.mes(
                                "<col=a53fff>Magical orbs begin to materialise behind you!</col>"
                            )
                            it.tint(PlayerTint(0, 605, 0, 0, 0, 112))
                        }
                    }
                    activePlayers(raid).forEach { player ->
                        val previous = state.trailLastTiles[player.uid] ?: player.previousCoords
                        state.trailLastTiles[player.uid] = player.coords
                        if (previous == player.coords) return@forEach
                        val traversed = exactPlayerTraversal(player)
                        val vacated = traversed.filter { it != player.coords }.toMutableList()
                        if (ToaInvocation.FEELING_SPECIAL in raid.startSnapshot.invocations) {
                            val segmentStart =
                                player.lastProcessedCoord.takeIf {
                                    player.pendingStepCount > 1 && it != CoordGrid.NULL
                                } ?: previous
                            TrailingOrbController.oneTileAhead(segmentStart, player.coords)?.let {
                                vacated += it
                            }
                        }
                        vacated
                            .distinct()
                            .filter { insideAkkhaArena(center, it) }
                            .forEach { tile ->
                                val element = akkhaQuadrant(center, tile)
                                val actor =
                                    spawnNpc(raid, element.trailNpc, tile, ToaNpcRole.HAZARD)
                                actor.abortRoute()
                                actor.mode = null
                                state.trailOrbs +=
                                    AkkhaTrailOrb(
                                        player.uid,
                                        element,
                                        tile,
                                        clock.cycle,
                                        clock.cycle + AkkhaTuning.TRAIL_ORB_LIFETIME.value,
                                    )
                                traceAkkha(
                                    raid,
                                    source,
                                    "trail_orb_spawn",
                                    "target" to player.uid,
                                    "orbId" to element.trailNpc,
                                    "orbTile" to tile,
                                )
                            }
                    }
                }
            }
        }
        val iterator = state.trailOrbs.iterator()
        while (iterator.hasNext()) {
            val orb = iterator.next()
            if (clock.cycle >= orb.expiresAt) {
                removeTrailActor(raid, orb)
                iterator.remove()
                continue
            }
            // The orb materialises behind the player before becoming dangerous.
            if (clock.cycle <= orb.spawnedAt) continue
            val victim = activePlayers(raid).firstOrNull { orb.tile in exactPlayerTraversal(it) }
            if (victim != null) {
                queueSpecialHit(
                    victim,
                    source,
                    scaledDamage(raid, AkkhaTuning.TRAILING_ORB_MAX_HIT.value),
                    1,
                )
                applyAkkhaElement(raid, victim, source, orb.element)
                removeTrailActor(raid, orb)
                iterator.remove()
                traceAkkha(
                    raid,
                    source,
                    "trail_orb_collision",
                    "target" to victim.uid,
                    "orbTile" to orb.tile,
                )
            }
        }
    }

    private fun removeTrailActor(raid: ToaRaidSession, orb: AkkhaTrailOrb) {
        raid.spawnedNpcs
            .firstOrNull { it.id == orb.element.trailNpc && it.coords == orb.tile }
            ?.let { removeNpc(raid, it) }
    }

    private fun performAkkhaSpecial(
        raid: ToaRaidSession,
        source: Npc,
        target: Player,
        special: AkkhaSpecial,
    ): Int {
        val state = raid.akkha
        source.abortRoute()
        source.mode = null
        return when (special) {
            AkkhaSpecial.DETONATE -> {
                source.anim(special.spearAnimation)
                activePlayers(raid).forEach {
                    it.mes("<col=a53fff>You have been marked for detonation!</col>")
                }
                state.detonation =
                    AkkhaDetonation(
                        activePlayers(raid).associate { it.uid to it.coords },
                        clock.cycle + AkkhaTuning.DETONATE_TELEGRAPH.value,
                        ToaInvocation.FEELING_SPECIAL in raid.startSnapshot.invocations,
                    )
                AkkhaTuning.DETONATE_TELEGRAPH.value + 1
            }
            AkkhaSpecial.MEMORY_BLAST -> {
                source.anim(special.spearAnimation)
                val count = memoryIterations(raid.pathLevels[ToaPath.HET] ?: 0)
                val window =
                    AkkhaInvocationRules.memoryStepWindow(
                        ToaInvocation.FEELING_SPECIAL in raid.startSnapshot.invocations
                    )
                state.memorySteps.clear()
                state.memorySteps +=
                    MemoryBlastController.schedule(
                        clock.cycle,
                        state.nextInt(4),
                        count,
                        window,
                        state::nextBoolean,
                    )
                state.scheduleMemoryLifecycle(clock.cycle, state.memorySteps)
                (state.memoryReappearAt - clock.cycle).coerceAtLeast(1)
            }
            AkkhaSpecial.TRAILING_ORBS -> {
                source.anim(special.spearAnimation)
                state.startTrailingOrbs(clock.cycle)
                activePlayers(raid).forEach { state.trailLastTiles[it.uid] = it.coords }
                AKKHA_TRAIL_CAST_RECOVERY
            }
        }
    }

    private fun ensureAkkhaShadows(raid: ToaRaidSession, restoreHitpoints: Boolean) {
        val center = roomCoord(raid, ToaRoom.AKKHA, true)
        val path = raid.pathLevels[ToaPath.HET] ?: 0
        val hp = AkkhaScaling.shadowHitpoints(raid.startSnapshot.partySize, raid.raidLevel, path)
        raid.akkha.shadowHeadbarActors.entries.removeAll { (_, key) ->
            raid.spawnedNpcs.none { it.hashCode() == key && it.isSlotAssigned }
        }
        AkkhaQuadrant.entries.forEach { quadrant ->
            val tile = AkkhaShadowLifecycle.spawnTile(center, quadrant)
            val hourglassTile = AkkhaShadowLifecycle.hourglassTile(center, quadrant)
            val direction = AkkhaShadowLifecycle.spawnDirection(quadrant)
            val shadow =
                raid.npcRoles.entries
                    .firstOrNull { (npc, role) ->
                        role == ToaNpcRole.ADD &&
                            npc.id in ToaNpcs.akkhaShadows &&
                            npc.isSlotAssigned &&
                            npc.coords == tile
                    }
                    ?.key
            if (shadow == null) {
                spawnNpc(raid, AkkhaAssets.SHADOW, tile, ToaNpcRole.ADD, respawnDir = direction)
                    .also {
                        it.baseHitpointsLvl = hp
                        it.hitpoints = hp
                        it.suppressHitpointsRegen = true
                        it.abortRoute()
                        it.mode = null
                        it.lockFacing(hourglassTile)
                        it.transmog(
                            requireNotNull(ServerCacheManager.getNpc(AkkhaAssets.SHADOW)),
                            Int.MAX_VALUE,
                        )
                        it.anim(AkkhaAssets.TELEPORT_APPEAR_SPEAR)
                    }
            } else {
                if (restoreHitpoints) {
                    shadow.baseHitpointsLvl = hp
                    shadow.hitpoints = hp
                }
                shadow.suppressHitpointsRegen = true
                shadow.abortRoute()
                shadow.mode = null
                shadow.lockFacing(hourglassTile)
            }
            val timer =
                raid.akkha.shadowHeadbarActors[quadrant]?.let { key ->
                    raid.spawnedNpcs.firstOrNull { it.hashCode() == key && it.isSlotAssigned }
                }
                    ?: raid.npcRoles.entries
                        .firstOrNull { (npc, role) ->
                            role == ToaNpcRole.HAZARD &&
                                npc.id == AkkhaAssets.HEADBAR &&
                                npc.isSlotAssigned &&
                                npc.coords == hourglassTile
                        }
                        ?.key
                    ?: spawnNpc(
                        raid,
                        AkkhaAssets.HEADBAR,
                        hourglassTile,
                        ToaNpcRole.HAZARD,
                        respawnDir = direction,
                    )
            timer.baseHitpointsLvl = AkkhaTuning.SHADOW_CHARGE.value
            timer.hitpoints = AkkhaTuning.SHADOW_CHARGE.value
            timer.suppressHitpointsRegen = true
            raid.akkha.shadowHeadbarActors[quadrant] = timer.hashCode()
        }
        updateAkkhaShadowHeadbars(raid)
    }

    private fun updateAkkhaShadowHeadbars(raid: ToaRaidSession) {
        val state = raid.akkha
        updateAkkhaShadowVisuals(raid)
        val maximum = AkkhaTuning.SHADOW_CHARGE.value.coerceAtLeast(1)
        val remaining =
            if (state.shadowBlast != null) 0
            else (state.shadowChargeEndsAt - clock.cycle).coerceIn(0, maximum)
        state.shadowHeadbarActors.entries.removeAll { (_, key) ->
            val actor = raid.spawnedNpcs.firstOrNull { it.hashCode() == key && it.isSlotAssigned }
            if (actor == null) {
                true
            } else {
                val elapsed = maximum - remaining
                val fill = akkhaShadowTimerFill(elapsed, maximum)
                actor.showHeadbar(
                    Headbar.fromNoSource(
                        self = AkkhaAssets.SHADOW_TIMER_HEADBAR,
                        public = AkkhaAssets.SHADOW_TIMER_HEADBAR,
                        startFill = fill,
                        endFill = fill,
                        startTime = 0,
                        endTime = 0,
                    )
                )
                false
            }
        }
    }

    private fun updateAkkhaShadowVisuals(raid: ToaRaidSession) {
        val center = roomCoord(raid, ToaRoom.AKKHA, true)
        raid.npcRoles.entries.forEach { (npc, role) ->
            if (role != ToaNpcRole.ADD || npc.id !in ToaNpcs.akkhaShadows || !npc.isSlotAssigned) {
                return@forEach
            }
            val quadrant = akkhaQuadrant(center, npc.coords)
            val tint = AkkhaShadowVisuals.tint(raid.akkha, quadrant)
            npc.tint(
                tint.startTime,
                tint.endTime,
                tint.hue,
                tint.saturation,
                tint.lightness,
                tint.weight,
            )
        }
    }

    private fun clearAkkhaShadowQuadrant(raid: ToaRaidSession, quadrant: AkkhaQuadrant) {
        val center = roomCoord(raid, ToaRoom.AKKHA, true)
        val shadowTile = AkkhaShadowLifecycle.spawnTile(center, quadrant)
        val hourglassTile = AkkhaShadowLifecycle.hourglassTile(center, quadrant)
        raid.akkha.shadowHeadbarActors.remove(quadrant)
        raid.npcRoles.entries
            .filter { (npc, role) ->
                npc.isSlotAssigned &&
                    ((role == ToaNpcRole.ADD &&
                        npc.id in ToaNpcs.akkhaShadows &&
                        npc.coords == shadowTile) ||
                        (role == ToaNpcRole.HAZARD &&
                            npc.coords == hourglassTile &&
                            npc.id == AkkhaAssets.HEADBAR))
            }
            .map { it.key }
            .toList()
            .forEach { removeNpc(raid, it) }
    }

    private fun clearAkkhaShadows(raid: ToaRaidSession) {
        raid.npcRoles.entries
            .filter { (npc, role) ->
                (role == ToaNpcRole.ADD && npc.id in ToaNpcs.akkhaShadows) ||
                    (role == ToaNpcRole.HAZARD && npc.id == AkkhaAssets.HEADBAR)
            }
            .map { it.key }
            .toList()
            .forEach { removeNpc(raid, it) }
        raid.akkha.shadowHeadbarActors.clear()
    }

    private fun livingShadowQuadrants(raid: ToaRaidSession, center: CoordGrid): Set<AkkhaQuadrant> =
        raid.npcRoles.entries
            .filter { (npc, role) ->
                role == ToaNpcRole.ADD &&
                    npc.id in ToaNpcs.akkhaShadows &&
                    npc.isSlotAssigned &&
                    npc.hitpoints > 0
            }
            .map { akkhaQuadrant(center, it.key.coords) }
            .toSet()

    private fun finishAkkhaShadow(raid: ToaRaidSession, shadow: Npc) {
        if (shadow.hitpoints > 0) {
            log.warn {
                "[ToA] Ignored premature Akkha shadow completion for npc=${shadow.id}, hp=${shadow.hitpoints}"
            }
            return
        }
        val center = roomCoord(raid, ToaRoom.AKKHA, true)
        val killedQuadrant = akkhaQuadrant(center, shadow.coords)
        traceAkkha(
            raid,
            boss(raid),
            "shadow_death",
            "quadrant" to killedQuadrant,
            "defeated" to raid.akkha.defeatedShadows.joinToString(),
        )
        finishAkkhaShadow(raid, killedQuadrant)
    }

    private fun finishAkkhaShadow(raid: ToaRaidSession, quadrant: AkkhaQuadrant) {
        val source = boss(raid) ?: return
        if (!raid.akkha.completeShadow(quadrant, clock.cycle)) return
        clearAkkhaShadowQuadrant(raid, quadrant)
        if (raid.akkha.state == AkkhaState.SHADOW_TRANSITION) {
            rearmAkkhaAggression(raid, source, force = true)
        }
        traceAkkha(
            raid,
            source,
            "shadow_complete",
            "quadrant" to quadrant,
            "bossTile" to source.coords,
            "lureRequired" to !akkhaBossInsideQuadrant(raid, source, quadrant),
            "chargingQuadrants" to
                livingShadowQuadrants(raid, roomCoord(raid, ToaRoom.AKKHA, true)),
            "nextState" to raid.akkha.state,
        )
    }

    private fun beginAkkhaFinalStand(raid: ToaRaidSession, source: Npc) {
        val state = raid.akkha
        state.beginFinalStand()
        raid.encounter.pendingHits.clear()
        clearAkkhaShadows(raid)
        source.abortRoute()
        source.mode = null
        source.apRangeOverride = 1
        source.ignoreCombatInteractions = true
        source.transmog(
            requireNotNull(ServerCacheManager.getNpc(AkkhaAssets.ENRAGE_SPAWN)),
            Int.MAX_VALUE,
        )
        source.anim(AkkhaAssets.FINAL_STAND_SPEAR)
        state.scheduleFinalStandIntro(clock.cycle)
        akkhaFinalSwapReadyAt.remove(raid.instance.id)
        raid.nextMechanicAt = clock.cycle + 1
        traceAkkha(raid, source, "final_stand_start", "bossMaxHp" to source.baseHitpointsLvl)
        message(raid.party, "<col=ff289d>Akkha makes his final stand!</col>")
    }

    private fun processAkkhaFinalStandIntro(raid: ToaRaidSession, source: Npc) {
        val state = raid.akkha
        when (state.advanceFinalStandIntro(clock.cycle)) {
            1 -> {
                source.hitpoints = akkhaFinalHitpoints(source.baseHitpointsLvl)
                source.transmog(
                    requireNotNull(ServerCacheManager.getNpc(AkkhaAssets.ENRAGE_INITIAL)),
                    Int.MAX_VALUE,
                )
                traceAkkha(raid, source, "final_stand_form", "npcId" to AkkhaAssets.ENRAGE_INITIAL)
            }
            2 -> source.anim(AkkhaAssets.TELEPORT_DISAPPEAR_SPEAR)
            3 -> activateAkkhaFinalStandFormation(raid, source)
        }
    }

    private fun activateAkkhaFinalStandFormation(raid: ToaRaidSession, source: Npc) {
        val state = raid.akkha
        val bossIndex = state.nextInt(AkkhaArenaLayout.finalStandAnchors.size)
        PathingEntityCommon.telejump(source, collision, akkhaFinalStandTile(raid, bossIndex))
        source.transmog(
            requireNotNull(ServerCacheManager.getNpc(AkkhaAssets.ENRAGE)),
            Int.MAX_VALUE,
        )
        source.anim(AkkhaAssets.TELEPORT_APPEAR_SPEAR)
        source.ignoreCombatInteractions = false
        source.moveRestrict = MoveRestrict.NoMove
        source.abortRoute()
        source.mode = null
        val decoys = linkedMapOf<Int, Int>()
        AkkhaArenaLayout.finalStandAnchors.indices
            .filterNot { it == bossIndex }
            .forEach { index ->
                val decoy =
                    spawnNpc(
                        raid,
                        AkkhaAssets.SHADOW_ENRAGE,
                        akkhaFinalStandTile(raid, index),
                        ToaNpcRole.HAZARD,
                    )
                decoy.ignoreCombatInteractions = true
                decoy.moveRestrict = MoveRestrict.NoMove
                decoy.abortRoute()
                decoy.mode = null
                decoy.anim(AkkhaAssets.TELEPORT_APPEAR_SPEAR)
                decoys[index] = decoy.hashCode()
            }
        state.registerFinalStandFormation(source.hashCode(), bossIndex, decoys)
        state.activateFinalStand(clock.cycle)
        traceAkkha(
            raid,
            source,
            "final_stand_active",
            "bossIndex" to bossIndex,
            "bossTile" to source.coords,
            "decoys" to decoys.keys.joinToString(),
        )
    }

    private fun startAkkhaFinalStandSwap(raid: ToaRaidSession) {
        val swap = raid.akkha.pendingFinalStandSwap ?: return
        val outgoing = akkhaActor(raid, swap.outgoingBossActorKey) ?: return
        val incoming = akkhaActor(raid, swap.incomingBossActorKey) ?: return
        outgoing.clearInteraction()
        incoming.clearInteraction()
        outgoing.abortRoute()
        incoming.abortRoute()
        outgoing.mode = null
        incoming.mode = null
        outgoing.ignoreCombatInteractions = true
        incoming.ignoreCombatInteractions = true
        outgoing.anim(AkkhaAssets.TELEPORT_DISAPPEAR_SWORD)
        incoming.anim(AkkhaAssets.TELEPORT_DISAPPEAR_SWORD)
        akkhaFinalSwapReadyAt[raid.instance.id] = clock.cycle + 1
        traceAkkha(
            raid,
            outgoing,
            "final_swap_start",
            "from" to swap.fromIndex,
            "to" to swap.toIndex,
        )
    }

    private fun completeAkkhaFinalStandSwapIfDue(raid: ToaRaidSession) {
        val readyAt = akkhaFinalSwapReadyAt[raid.instance.id] ?: return
        if (clock.cycle < readyAt) return
        val swap =
            raid.akkha.pendingFinalStandSwap
                ?: run {
                    akkhaFinalSwapReadyAt.remove(raid.instance.id)
                    return
                }
        val outgoing = akkhaActor(raid, swap.outgoingBossActorKey) ?: return
        val incoming = akkhaActor(raid, swap.incomingBossActorKey) ?: return
        val sharedHitpoints = outgoing.hitpoints
        val sharedMaximum = outgoing.baseHitpointsLvl
        raid.npcRoles[outgoing] = ToaNpcRole.HAZARD
        raid.npcRoles[incoming] = ToaNpcRole.BOSS
        outgoing.transmog(
            requireNotNull(ServerCacheManager.getNpc(AkkhaAssets.SHADOW_ENRAGE)),
            Int.MAX_VALUE,
        )
        outgoing.ignoreCombatInteractions = true
        outgoing.baseHitpointsLvl = sharedMaximum
        outgoing.hitpoints = sharedHitpoints
        outgoing.anim(AkkhaAssets.TELEPORT_APPEAR_SWORD)
        incoming.transmog(
            requireNotNull(ServerCacheManager.getNpc(AkkhaAssets.ENRAGE)),
            Int.MAX_VALUE,
        )
        incoming.ignoreCombatInteractions = false
        incoming.baseHitpointsLvl = sharedMaximum
        incoming.hitpoints = sharedHitpoints
        incoming.suppressHitpointsRegen = true
        incoming.apRangeOverride = 1
        incoming.maxOpRangeOverride = AkkhaTuning.ROOM_CHASE_RANGE.value
        incoming.anim(AkkhaAssets.TELEPORT_APPEAR_SWORD)
        raid.akkha.completeFinalStandSwap()
        akkhaFinalSwapReadyAt.remove(raid.instance.id)
        traceAkkha(
            raid,
            incoming,
            "final_swap_complete",
            "from" to swap.fromIndex,
            "to" to swap.toIndex,
            "bossHp" to sharedHitpoints,
        )
    }

    private fun akkhaActor(raid: ToaRaidSession, key: Int?): Npc? =
        key?.let { actorKey ->
            raid.spawnedNpcs.firstOrNull { it.hashCode() == actorKey && it.isSlotAssigned }
        }

    private fun prepareAkkhaFinalDeath(raid: ToaRaidSession, source: Npc) {
        val state = raid.akkha
        if (state.state != AkkhaState.FINAL_STAND) return
        state.state = AkkhaState.DEFEATED
        state.target = null
        source.ignoreCombatInteractions = true
        source.clearInteraction()
        source.abortRoute()
        source.mode = null
        state.finalStandDecoyActorKeys.values
            .mapNotNull { akkhaActor(raid, it) }
            .filter { it !== source }
            .forEach { decoy ->
                decoy.ignoreCombatInteractions = true
                decoy.clearInteraction()
                decoy.abortRoute()
                decoy.mode = null
                decoy.anim(AkkhaAssets.DEATH_SPEAR)
            }
        state.finalOrbs.keys
            .toList()
            .mapNotNull { akkhaActor(raid, it) }
            .forEach { removeNpc(raid, it) }
        state.finalOrbs.clear()
        akkhaFinalSwapReadyAt.remove(raid.instance.id)
        activePlayers(raid).forEach { it.clearTint() }
        traceAkkha(raid, source, "final_death_start")
    }

    private fun akkhaFinalStandTile(raid: ToaRaidSession, index: Int): CoordGrid {
        val center = roomCoord(raid, ToaRoom.AKKHA, true)
        val anchor = AkkhaArenaLayout.finalStandAnchors[index]
        return CoordGrid(
            center.x + anchor.first - AkkhaArenaLayout.CENTER_X,
            center.z + anchor.second - AkkhaArenaLayout.CENTER_Z,
            center.level,
        )
    }

    private fun warnAkkhaWrongStyle(player: Player, raid: ToaRaidSession) {
        val state = raid.akkha
        val last = state.wrongStyleWarnedAt[player.uid] ?: Int.MIN_VALUE
        if (clock.cycle - last < 4) return
        state.wrongStyleWarnedAt[player.uid] = clock.cycle
        if (state.state == AkkhaState.FINAL_STAND)
            player.mes("Akkha's final stand can only be harmed with melee attacks.")
        else if (state.state == AkkhaState.NORMAL_COMBAT)
            player.mes(
                "Akkha is protecting from ${state.style.hitType.name.lowercase()}; either other combat style can damage him."
            )
        else player.mes("Akkha cannot be harmed during this mechanic.")
    }

    private fun akkhaQuadrant(center: CoordGrid, tile: CoordGrid): AkkhaQuadrant =
        requireNotNull(AkkhaFloorBlastController.quadrantAt(center, tile)) {
            "Akkha tile is outside the clipped arena floor: center=$center tile=$tile"
        }

    private fun akkhaBossInsideQuadrant(
        raid: ToaRaidSession,
        source: Npc,
        quadrant: AkkhaQuadrant,
    ): Boolean {
        val center = roomCoord(raid, ToaRoom.AKKHA, true)
        return (0 until source.size).all { dx ->
            (0 until source.size).all { dz ->
                AkkhaFloorBlastController.quadrantAt(
                    center,
                    CoordGrid(source.coords.x + dx, source.coords.z + dz, source.coords.level),
                ) == quadrant
            }
        }
    }

    private fun akkhaQuadrantTile(
        center: CoordGrid,
        quadrant: AkkhaQuadrant,
        radius: Int,
    ): CoordGrid =
        when (quadrant) {
            AkkhaQuadrant.BURN -> CoordGrid(center.x - radius, center.z + radius, center.level)
            AkkhaQuadrant.DARKNESS -> CoordGrid(center.x + radius, center.z + radius, center.level)
            AkkhaQuadrant.LIGHTNING -> CoordGrid(center.x + radius, center.z - radius, center.level)
            AkkhaQuadrant.FREEZE -> CoordGrid(center.x - radius, center.z - radius, center.level)
        }

    private fun insideAkkhaArena(center: CoordGrid, tile: CoordGrid) =
        AkkhaFloorBlastController.quadrantAt(center, tile) != null

    private fun scheduleTileHit(
        raid: ToaRaidSession,
        source: Npc,
        target: Player,
        graphic: Int,
        baseDamage: Int,
        delay: Int,
        radius: Int,
        wardenImpact: WardenImpact? = null,
    ) {
        sendProjectile(source, target, graphic)
        raid.encounter.pendingHits +=
            ToaPendingHit(
                target.uid,
                target.coords,
                clock.cycle + delay,
                scaledDamage(raid, baseDamage),
                graphic,
                radius,
                requireTargetOnTile = true,
                wardenImpact = wardenImpact,
            )
    }

    private fun scheduleTrackingHit(
        raid: ToaRaidSession,
        source: Npc,
        target: Player,
        graphic: Int,
        baseDamage: Int,
        delay: Int,
        hitType: HitType = HitType.Typeless,
        prayerReductionPercent: Int? = null,
        wardenImpact: WardenImpact? = null,
        impactGraphic: Int? = graphic,
    ) {
        sendProjectile(source, target, graphic)
        raid.encounter.pendingHits +=
            ToaPendingHit(
                target.uid,
                target.coords,
                clock.cycle + delay,
                scaledDamage(raid, baseDamage),
                impactGraphic,
                requireTargetOnTile = false,
                hitType = hitType,
                prayerReductionPercent = prayerReductionPercent,
                wardenImpact = wardenImpact,
            )
    }

    private fun sendKephriOverheadProjectile(source: Npc) {
        world.projAnim(
            ProjAnim(
                spotanim = KephriBombs.OVERHEAD_PROJECTILE,
                startHeight = 175,
                endHeight = 250,
                startTime = 39,
                endTime = 90,
                angle = 50,
                progress = 0,
                sourceIndex = source.slotId + 1,
                targetIndex = 0,
                startCoord = source.coords,
                endCoord = source.coords,
            )
        )
    }

    private fun sendKephriBombProjectile(source: Npc, target: CoordGrid) {
        world.projAnim(
            ProjAnim(
                spotanim = KephriBombs.PROJECTILE,
                startHeight = 250,
                endHeight = 9,
                startTime = 0,
                endTime = 120,
                angle = 115,
                progress = 0,
                sourceIndex = source.slotId + 1,
                targetIndex = 0,
                startCoord = source.coords,
                endCoord = target,
            )
        )
    }

    private fun sendKephriEggProjectile(source: Npc, target: CoordGrid, graphic: Int) {
        world.projAnim(
            ProjAnim(
                spotanim = graphic,
                startHeight = 86,
                endHeight = 0,
                startTime = 70,
                endTime = 120,
                angle = 14,
                progress = 96,
                sourceIndex = source.slotId + 1,
                targetIndex = 0,
                startCoord = source.coords,
                endCoord = target,
            )
        )
    }

    private fun sendProjectile(source: Npc, target: Player, graphic: Int) {
        val distance = source.coords.chebyshevDistance(target.coords).coerceAtLeast(1)
        world.projAnim(
            ProjAnim(
                spotanim = graphic,
                startHeight = 60,
                endHeight = 30,
                startTime = 0,
                endTime = 30 + distance * 5,
                angle = 15,
                progress = 32,
                sourceIndex = source.slotId + 1,
                targetIndex = -(target.slotId + 1),
                startCoord = source.coords,
                endCoord = target.coords,
            )
        )
    }

    private fun sendProjectile(
        source: Npc,
        target: CoordGrid,
        graphic: Int,
        travelTicks: Int? = null,
    ) {
        val distance = source.coords.chebyshevDistance(target).coerceAtLeast(1)
        world.projAnim(
            ProjAnim(
                spotanim = graphic,
                startHeight = 60,
                endHeight = 30,
                startTime = 0,
                endTime =
                    travelTicks?.let(ToaProjectileTiming::clientCyclesForServerTicks)
                        ?: (30 + distance * 5),
                angle = 15,
                progress = 32,
                sourceIndex = source.slotId + 1,
                targetIndex = 0,
                startCoord = source.coords,
                endCoord = target,
            )
        )
    }

    private fun sendProjectile(
        source: CoordGrid,
        target: CoordGrid,
        graphic: Int,
        travelTicks: Int,
    ) {
        world.projAnim(
            ProjAnim(
                spotanim = graphic,
                startHeight = 112,
                endHeight = 0,
                startTime = 0,
                endTime = ToaProjectileTiming.clientCyclesForServerTicks(travelTicks),
                angle = 15,
                progress = 32,
                sourceIndex = 0,
                targetIndex = 0,
                startCoord = source,
                endCoord = target,
            )
        )
    }

    private fun processPendingHits(raid: ToaRaidSession) {
        val source = boss(raid)
        val iterator = raid.encounter.pendingHits.iterator()
        while (iterator.hasNext()) {
            val pending = iterator.next()
            if (raid.currentRoom == ToaRoom.ZEBAK && pending.generation != raid.zebak.generation) {
                iterator.remove()
                continue
            }
            if (clock.cycle < pending.landsAt) continue
            iterator.remove()
            pending.impactGraphic?.let { world.spotanimMap(SpotanimType(it), pending.tile) }
            val target = pending.target.resolve(players) ?: continue
            if (source == null || target !in activePlayers(raid)) continue
            if (
                pending.requireTargetOnTile &&
                    target.coords.chebyshevDistance(pending.tile) > pending.radius
            ) {
                continue
            }
            val zebakBloodImpact =
                if (pending.zebakBloodHealing) {
                    HitImpactHandler { impact ->
                        val body = raid.zebak.body
                        if (
                            impact.damage > 0 &&
                                raid.currentRoom == ToaRoom.ZEBAK &&
                                pending.generation == raid.zebak.generation &&
                                body === source &&
                                body.isSlotAssigned &&
                                body.hitpoints > 0
                        ) {
                            val rules = ZebakInvocationRules.resolve(raid.startSnapshot.invocations)
                            if (rules.bloodMagic) {
                                val healing =
                                    ZebakInvocationRules.healAmount(
                                        impact.damage,
                                        rules.arterialHealing,
                                    )
                                body.hitpoints =
                                    (body.hitpoints + healing).coerceAtMost(body.baseHitpointsLvl)
                            }
                        }
                    }
                } else {
                    null
                }
            queueAkkhaCombatHit(
                player = target,
                source = source,
                damage = pending.damage,
                delay = 1,
                type = pending.hitType,
                prayerReductionPercent = pending.prayerReductionPercent,
                additionalImpactHandler = zebakBloodImpact,
            )
            pending.akkhaElement?.let { applyAkkhaElement(raid, target, source, it) }
            pending.wardenImpact?.let { applyWardenImpact(raid, target, it) }
        }
    }

    private fun applyWardenImpact(raid: ToaRaidSession, target: Player, impact: WardenImpact) {
        when (impact) {
            WardenImpact.IMPRISONMENT -> {
                CombatEffects.clearFreezeImmunity(target)
                CombatEffects.freeze(target, WardenPhaseTwoMechanics.STONE_DURATION_TICKS)
                target.anim(WardenPhaseTwoMechanics.STONE_PLAYER_ANIMATION)
                target.spotanim(WardenPhaseTwoMechanics.STONE_GRAPHIC)
                raid.encounter.wardenStoneReleaseAt[target.uid] =
                    clock.cycle + WardenPhaseTwoMechanics.STONE_DURATION_TICKS
                target.combatClearQueue()
                target.clearInteractionRoute()
                target.mes("The Warden imprisons you in stone.")
            }
        }
    }

    private fun rollWardenDamage(raid: ToaRaidSession, maximum: Int): Int {
        val sequence = raid.encounter.wardenDamageRollCounter++
        return WardenDamageRolls.roll(
            maximum = maximum,
            raidSeed = raid.startSnapshot.seed,
            sequence = sequence,
        )
    }

    private fun scaledDamage(raid: ToaRaidSession, base: Int): Int {
        val pathLevel = raid.currentPath?.let { raid.pathLevels[it] } ?: 0
        return ToaRules.maxHit(base, raid.raidLevel, pathLevel)
    }

    private fun spawnBoss(raid: ToaRaidSession, id: Int, role: ToaNpcRole = ToaNpcRole.BOSS): Npc =
        spawnNpc(raid, id, roomCoord(raid, raid.currentRoom, true), role).also { npc ->
            val pathLevel = raid.currentPath?.let { raid.pathLevels[it] } ?: 0
            val hp =
                ToaRules.hitpoints(
                    npc.baseHitpointsLvl.coerceAtLeast(100),
                    raid.startSnapshot.partySize,
                    raid.raidLevel,
                    pathLevel,
                )
            npc.baseHitpointsLvl = hp
            npc.hitpoints = hp
            npc.apRangeOverride = 32
            when {
                raid.currentRoom == ToaRoom.KEPHRI && role == ToaNpcRole.BOSS ->
                    initializeKephri(raid, npc)
                raid.currentRoom == ToaRoom.BABA && role == ToaNpcRole.BOSS ->
                    initializeBaba(raid, npc)
                raid.currentRoom == ToaRoom.AKKHA && role == ToaNpcRole.BOSS ->
                    initializeAkkha(raid, npc, null)
                else -> nearestActive(raid, npc.coords)?.let { npc.apPlayer2(it, interactions) }
            }
        }

    private fun initializeKephri(raid: ToaRaidSession, source: Npc) {
        raid.encounter.reset()
        val hp =
            ToaRules.hitpoints(
                KephriEncounter.SHIELD_HITPOINTS,
                raid.startSnapshot.partySize,
                raid.raidLevel,
                raid.pathLevels[ToaPath.SCABARAS] ?: 0,
            )
        source.baseHitpointsLvl = hp
        source.hitpoints = hp
        source.suppressHitpointsRegen = true
        source.apRangeOverride = 32
        source.moveRestrict = MoveRestrict.NoMove
        source.ignoreCombatInteractions = true
        source.mode = null
        source.abortRoute()
        source.clearInteraction()
        source.spotanim(KephriEncounter.SHIELD_GRAPHIC)
        forEachOnline(raid) { it.midiSong(KephriEncounter.ROOM_MIDI) }
    }

    private fun initializeBaba(raid: ToaRaidSession, source: Npc) {
        cleanupBaba(raid, resetState = false)
        val state = raid.baba
        state.reset()
        state.startIntro(clock.cycle + BabaTuning.INTRO_TICKS.value)
        val hp =
            BabaScaling.hitpoints(
                activePlayers(raid).size.coerceAtLeast(1),
                raid.raidLevel,
                raid.pathLevels[ToaPath.APMEKEN] ?: 0,
            )
        source.baseHitpointsLvl = hp
        source.hitpoints = hp
        source.suppressHitpointsRegen = true
        source.apRangeOverride = BabaTuning.MELEE_ATTACK_RANGE.value
        source.maxOpRangeOverride = BabaTuning.ROOM_CHASE_RANGE.value
        source.abortRoute()
        source.mode = null
        source.anim(BabaAssets.IDLE)
        val initialTarget = activePlayers(raid).firstOrNull()
        state.target = initialTarget?.uid
        initialTarget?.let { source.faceSquare(it.coords) }
        discoverBabaSarcophagi(raid)
        raid.nextMechanicAt = Int.MAX_VALUE
        traceBaba(
            raid,
            source,
            "intro_start",
            "bossHp" to hp,
            "bossTile" to source.coords,
            "targetableAt" to state.specialEndsAt,
        )
    }

    private fun initializeAkkha(raid: ToaRaidSession, source: Npc, initialTarget: Player?) {
        val state = raid.akkha
        state.reset()
        state.startIntro()
        state.startNormalCombat()
        val hp =
            AkkhaScaling.hitpoints(
                raid.startSnapshot.partySize,
                raid.raidLevel,
                raid.pathLevels[ToaPath.HET] ?: 0,
            )
        source.baseHitpointsLvl = hp
        source.suppressHitpointsRegen = true
        source.hitpoints = hp
        source.abortRoute()
        source.mode = null
        source.ignoreCombatInteractions = false
        source.transmog(requireNotNull(ServerCacheManager.getNpc(AkkhaAssets.MELEE)), Int.MAX_VALUE)
        source.apRangeOverride = akkhaAttackRange(state.style)
        source.maxOpRangeOverride = AkkhaTuning.ROOM_CHASE_RANGE.value
        val target = initialTarget ?: activePlayers(raid).firstOrNull()
        akkhaLastStyleChangeAt[raid.instance.id] = clock.cycle
        state.target = target?.uid
        target?.let { rearmAkkhaAggression(raid, source, force = true) }
        traceAkkha(
            raid,
            source,
            "combat_start",
            "initialTarget" to target?.uid,
            "activation" to if (initialTarget == null) "developer" else "crystal",
        )
    }

    private fun ensureAkkhaWaitingActor(raid: ToaRaidSession): Npc {
        check(raid.currentRoom == ToaRoom.AKKHA) {
            "Akkha waiting actor requested outside the Akkha room: ${raid.currentRoom}"
        }
        val existing =
            raid.npcRoles.entries
                .firstOrNull { (npc, role) ->
                    role == ToaNpcRole.BOSS && npc.id == AkkhaAssets.SPAWN && npc.isSlotAssigned
                }
                ?.key
        val source =
            existing
                ?: spawnNpc(
                    raid,
                    AkkhaAssets.SPAWN,
                    akkhaCoord(
                        raid,
                        AkkhaEntryLifecycle.WAITING_LOCAL_X,
                        AkkhaEntryLifecycle.WAITING_LOCAL_Z,
                    ),
                    ToaNpcRole.BOSS,
                )
        source.ignoreCombatInteractions = true
        source.moveRestrict = source.type.moveRestrict
        source.movementLocked = false
        source.mode = source.type.defaultMode
        return source
    }

    private fun spawnAround(
        raid: ToaRaidSession,
        id: Int,
        role: ToaNpcRole,
        index: Int,
        count: Int,
        radius: Int,
    ): Npc {
        val center = roomCoord(raid, raid.currentRoom, true)
        val points =
            arrayOf(
                -radius to -radius,
                radius to -radius,
                radius to radius,
                -radius to radius,
                0 to -radius,
                radius to 0,
                0 to radius,
                -radius to 0,
            )
        val offset = points[index % points.size]
        return spawnNpc(
                raid,
                id,
                CoordGrid(center.x + offset.first, center.z + offset.second, center.level),
                role,
            )
            .also { npc ->
                nearestActive(raid, npc.coords)?.let { npc.apPlayer2(it, interactions) }
            }
    }

    private fun spawnNpc(
        raid: ToaRaidSession,
        id: Int,
        tile: CoordGrid,
        role: ToaNpcRole,
        respawnDir: Direction? = null,
    ): Npc {
        val type = requireNotNull(ServerCacheManager.getNpc(id)) { "Missing ToA npc $id" }
        return Npc(type, tile).also { npc ->
            ToaNpcStatScaling.apply(npc, raid, role)
            respawnDir?.let { npc.respawnDir = it }
            val stationary = AkkhaShadowLifecycle.requiresStationarySpawn(id, role)
            npc.mode = if (stationary) null else type.defaultMode
            if (stationary) {
                npc.moveRestrict = MoveRestrict.NoMove
                npc.clearInteraction()
                npc.abortRoute()
            }

            npcs.add(npc, Int.MAX_VALUE)
            // NpcRepository treats Int.MAX_VALUE as a permanent world spawn and enables
            // respawning. TOA instance actors are permanent only for the instance lifetime and
            // must never rebuild themselves after an encounter death.
            npc.respawns = false
            instances.attachNpc(raid.instance.id, npc)
            raid.spawnedNpcs += npc
            raid.npcRoles[npc] = role
        }
    }

    private fun boss(raid: ToaRaidSession): Npc? =
        raid.npcRoles.entries
            .firstOrNull {
                it.key.isSlotAssigned &&
                    (it.value == ToaNpcRole.BOSS ||
                        it.value == ToaNpcRole.WARDEN_PHASE ||
                        it.value == ToaNpcRole.OBELISK)
            }
            ?.key

    private fun updateWardenChargeHeadbars(raid: ToaRaidSession) {
        val state = raid.encounter
        val charges =
            listOf(
                WardenGod.ELIDINIS to state.wardenElidinisCharge,
                WardenGod.TUMEKEN to state.wardenTumekenCharge,
            )
        charges.forEach { (god, charge) ->
            val actor = wardenChargeActor(raid, god) ?: return@forEach
            showWardenChargeHeadbar(actor, charge, 100)
        }
    }

    private fun wardenChargeActor(raid: ToaRaidSession, god: WardenGod): Npc? {
        val state = raid.encounter
        val actorKey =
            if (god == WardenGod.ELIDINIS) state.wardenElidinisChargeActor
            else state.wardenTumekenChargeActor
        raid.spawnedNpcs
            .firstOrNull { it.hashCode() == actorKey && it.isSlotAssigned }
            ?.let {
                return it
            }
        val identity = god.chargeActorIdentity()
        val actor =
            raid.spawnedNpcs.firstOrNull {
                it.isSlotAssigned &&
                    (it.id == identity.originalId ||
                        it.id == identity.chargingVisualId ||
                        it.transmog?.id == identity.chargingVisualId)
            } ?: return null
        if (god == WardenGod.ELIDINIS) state.wardenElidinisChargeActor = actor.hashCode()
        else state.wardenTumekenChargeActor = actor.hashCode()
        return actor
    }

    private fun showWardenChargeHeadbar(actor: Npc, value: Int, maximum: Int) {
        val headbar =
            ServerCacheManager.getHealthBar(
                WardenChargeHeadbar.gameval(maximum).asRSCM(RSCMType.HEADBAR)
            ) ?: return
        val fill = WardenChargeHeadbar.fill(value, maximum, headbar.segments)
        actor.showHeadbar(
            Headbar.fromNoSource(
                self = headbar.id,
                public = headbar.id,
                startFill = fill,
                endFill = fill,
                startTime = 0,
                endTime = 0,
            )
        )
    }

    private fun lockWardenActor(npc: Npc) {
        npc.ignoreCombatInteractions = true
        npc.moveRestrict = MoveRestrict.NoMove
        npc.movementLocked = true
        npc.mode = null
        npc.abortRoute()
        npc.clearInteraction()
    }

    private fun unlockWardenActor(npc: Npc) {
        npc.ignoreCombatInteractions = false
        npc.moveRestrict = npc.type.moveRestrict
        npc.movementLocked = false
        npc.mode = npc.type.defaultMode
        npc.apRangeOverride = 32
        npc.clearFacingLock()
    }

    private fun applyObeliskPenetration(player: Player) {
        player.disableProtectionPrayers()
        player.mes("The Obelisk's attack penetrates your protection prayer.")
    }

    private fun bossId(path: ToaPath): Int =
        when (path) {
            ToaPath.SCABARAS -> ToaNpcs.KEPHRI
            ToaPath.APMEKEN -> ToaNpcs.BABA
            ToaPath.CRONDIS -> ToaNpcs.ZEBAK
            ToaPath.HET -> ToaNpcs.AKKHA
        }

    private fun roomCoord(raid: ToaRaidSession, room: ToaRoom, boss: Boolean = false): CoordGrid =
        requireNotNull(instances.localCoord(raid.instance, ToaInstanceLayout.local(room, boss))) {
            "Missing ToA room coordinate $room"
        }

    private fun wardenRoomCoord(
        raid: ToaRaidSession,
        room: ToaRoom,
        localX: Int,
        localZ: Int,
    ): CoordGrid =
        requireNotNull(
            instances.localCoord(raid.instance, ToaInstanceLayout.roomLocal(room, localX, localZ))
        ) {
            "Missing Warden room coordinate $room $localX,$localZ"
        }

    private fun fightCoord(raid: ToaRaidSession, room: ToaRoom): CoordGrid =
        requireNotNull(instances.localCoord(raid.instance, ToaInstanceLayout.fightLocal(room))) {
            "Missing ToA fight coordinate $room"
        }

    private fun spiritCoord(raid: ToaRaidSession, room: ToaRoom): CoordGrid =
        requireNotNull(instances.localCoord(raid.instance, ToaInstanceLayout.spiritLocal(room))) {
            "Missing ToA spirit coordinate $room"
        }

    private fun rewardEntryCoord(raid: ToaRaidSession): CoordGrid =
        requireNotNull(instances.localCoord(raid.instance, ToaInstanceLayout.rewardEntryLocal())) {
            "Missing ToA reward-entry coordinate"
        }

    private fun enterRoom(
        player: Player,
        raid: ToaRaidSession,
        room: ToaRoom,
        cancelActiveCoroutine: Boolean = true,
    ) {
        prepare(player, cancelActiveCoroutine)
        applyRaidRestrictions(player, raid)
        PathingEntityCommon.telejump(player, collision, roomCoord(raid, room))
        raid.participant(player.uid)?.apply {
            currentRoom = room
            returnRoom = ToaRoom.LOBBY
        }
        if (
            room == ToaRoom.APMEKEN &&
                raid.challenge?.stage == ToaChallengeStage.ACTIVE &&
                ToaChallengeRoomRules.isInside(raid, instances, room, player.coords)
        ) {
            enterApmekenPlayer(raid, player)
        }
        ui.open(player, raid, clock.cycle)
    }

    private fun returnToLobby(
        player: Player,
        raid: ToaRaidSession,
        completeRoom: Boolean,
    ): Boolean {
        val participant = raid.participant(player.uid) ?: return false
        if (participant.spectating) {
            player.mes("You are unable to use this as a ghost.")
            return false
        }
        if (completeRoom && raid.completionPending) {
            val path = raid.currentPath ?: return false
            recordPathCompletion(raid, path)
            clearEncounterNpcs(raid)
            raid.currentPath = null
            raid.currentRoom = ToaRoom.LOBBY
            raid.challenge = null
            raid.encounterStarted = false
            raid.completionPending = false
            if (raid.completedPaths.size >= 2 && !raid.helpfulSpiritSpawned) {
                spawnNpc(
                    raid,
                    ToaNpcs.HELPFUL_SPIRIT,
                    lobbyCoord(raid, 28, 34),
                    ToaNpcRole.HELPFUL_SPIRIT,
                )
                raid.helpfulSpiritSpawned = true
            }
            teleportParty(raid, ToaRoom.LOBBY)
            message(
                raid.party,
                if (isToaWardensUnlocked(raid.completedPaths))
                    "All paths are complete. The Wardens await."
                else "${path.boss.displayName} is complete. Choose another path.",
            )
            return true
        }
        if (canLeadRaidAction(player, raid) && !completeRoom) {
            clearEncounterNpcs(raid)
            raid.currentPath = null
            raid.currentRoom = ToaRoom.LOBBY
            raid.challenge = null
            raid.encounterStarted = false
            raid.completionPending = false
            teleportParty(raid, ToaRoom.LOBBY)
            message(raid.party, "The leader returns the party to the lobby.")
            return true
        }
        enterRoom(player, raid, ToaRoom.LOBBY)
        return true
    }

    private fun lobbyCoord(raid: ToaRaidSession, localX: Int, localZ: Int): CoordGrid =
        requireNotNull(
            instances.localCoord(
                raid.instance,
                ToaInstanceLayout.roomLocal(ToaRoom.LOBBY, localX, localZ),
            )
        ) {
            "Missing ToA lobby coordinate $localX,$localZ"
        }

    /**
     * Keeps the donor lobby's sealed/open Wardens entrance authoritative in the instance.
     * LocRepository performs the region broadcast and collision replacement, so every nearby player
     * observes the same object and concurrent clicks cannot create duplicate entrances.
     */
    private fun syncWardensEntrance(raid: ToaRaidSession, unlocked: Boolean): Boolean {
        val tile = lobbyCoord(raid, 28, 14)
        val targetId = if (unlocked) ToaLocs.WARDENS_PATH else ToaLocs.WARDENS_PATH_LOCKED
        val otherId = if (unlocked) ToaLocs.WARDENS_PATH_LOCKED else ToaLocs.WARDENS_PATH
        val states = locs.findAll(tile).filter { it.id == targetId || it.id == otherId }.toList()
        val targetStates = states.filter { it.id == targetId }
        if (targetStates.isNotEmpty()) {
            targetStates.drop(1).forEach { locs.del(it, Int.MAX_VALUE) }
            states.filter { it.id == otherId }.forEach { locs.del(it, Int.MAX_VALUE) }
            return true
        }
        val current = states.firstOrNull { it.id == otherId }
        if (current == null) {
            log.error {
                "[ToA] missing Wardens entrance state instance=${raid.instance.id} tile=$tile " +
                    "expected=$otherId target=$targetId"
            }
            return false
        }
        val target = requireNotNull(ServerCacheManager.getObject(targetId))
        locs.change(current, target, Int.MAX_VALUE)
        val replaced = locs.findAll(tile).any { it.id == targetId }
        if (!replaced) {
            log.error {
                "[ToA] failed Wardens entrance replacement instance=${raid.instance.id} " +
                    "tile=$tile from=$otherId to=$targetId"
            }
        }
        return replaced
    }

    /**
     * Spawns the native white completion light after Tumeken's Warden dies. LocRepository owns the
     * region broadcast and collision update; exact-tile de-duplication prevents repeated death
     * callbacks from creating multiple passages in the same raid instance.
     */
    private fun syncRewardEntry(raid: ToaRaidSession): Boolean {
        val tile = rewardEntryCoord(raid)
        val existing = locs.findAll(tile).filter { it.id == ToaLocs.REWARD_ENTRY }.toList()
        if (existing.isNotEmpty()) {
            existing.drop(1).forEach { locs.del(it, Int.MAX_VALUE) }
            return true
        }
        val type = requireNotNull(ServerCacheManager.getObject(ToaLocs.REWARD_ENTRY))
        locs.add(tile, type, Int.MAX_VALUE, LocAngle.North, LocShape.CentrepieceStraight)
        val spawned = locs.findAll(tile).any { it.id == ToaLocs.REWARD_ENTRY }
        if (!spawned) {
            log.error {
                "[ToA] failed to spawn reward entry instance=${raid.instance.id} tile=$tile " +
                    "loc=${ToaLocs.REWARD_ENTRY}"
            }
        }
        return spawned
    }

    private fun clearRewardEntry(raid: ToaRaidSession) {
        // Teardown can run after a partial instance allocation, so cleanup must be idempotent.
        val tile =
            instances.localCoord(raid.instance, ToaInstanceLayout.rewardEntryLocal()) ?: return
        locs
            .findAll(tile)
            .filter { it.id == ToaLocs.REWARD_ENTRY }
            .toList()
            .forEach { locs.del(it, Int.MAX_VALUE) }
    }

    private fun teleportParty(
        raid: ToaRaidSession,
        room: ToaRoom,
        cancelActiveCoroutine: Boolean = true,
    ) {
        raid.participants.eligible().forEach { participant ->
            participant.spectating = false
            participant.currentRoom = room
        }
        forEachOnline(raid) { player ->
            prepare(player, cancelActiveCoroutine)
            applyRaidRestrictions(player, raid)
            PathingEntityCommon.telejump(player, collision, roomCoord(raid, room))
            ui.open(player, raid, clock.cycle)
        }
    }

    private fun queueSpecialHit(player: Player, source: Npc, damage: Int, delay: Int) =
        queueAkkhaCombatHit(
            player = player,
            source = source,
            damage = damage,
            delay = delay,
            type = HitType.Typeless,
            prayerReductionPercent = null,
        )

    private fun queueAkkhaCombatHit(
        player: Player,
        source: Npc,
        damage: Int,
        delay: Int,
        type: HitType,
        prayerReductionPercent: Int?,
        additionalImpactHandler: HitImpactHandler? = null,
    ) {
        val raid = raidFor(source)
        val modifier =
            prayerReductionPercent?.let { base ->
                NpcPartialProtectionPrayerPlayerHitModifier(
                    raid?.let {
                        ToaRules.prayerReductionPercent(base, it.startSnapshot.invocations)
                    } ?: base
                )
            } ?: NpcBypassProtectionPrayerPlayerHitModifier
        val prayerImpactHandler =
            if (raid != null && prayerReductionPercent != null) {
                prayerImpactHandler(raid, player, type)
            } else null
        val combinedImpactHandler =
            when {
                additionalImpactHandler == null -> prayerImpactHandler
                prayerImpactHandler == null -> additionalImpactHandler
                else ->
                    HitImpactHandler { impact ->
                        additionalImpactHandler.onImpact(impact)
                        prayerImpactHandler.onImpact(impact)
                    }
            }
        val safeDelay = delay.coerceAtLeast(1)
        if (akkhaHitCanUseNpcSource(source.isSlotAssigned)) {
            player.queueHit(
                source = source,
                delay = safeDelay,
                type = type,
                damage = damage,
                modifier = modifier,
                impactHandler = combinedImpactHandler,
            )
        } else {
            // Scripted floor hazards may resolve after their visual NPC has been removed. The
            // damage remains valid, but packing a removed NPC slot (-1) as the hit source is not.
            player.queueHit(
                delay = safeDelay,
                type = type,
                damage = damage,
                modifier = modifier,
                impactHandler = combinedImpactHandler,
            )
        }
    }

    private fun applyAkkhaElement(
        raid: ToaRaidSession,
        player: Player,
        source: Npc,
        quadrant: AkkhaQuadrant,
    ) {
        world.spotanimMap(SpotanimType(quadrant.graphic), player.coords)
        val status = raid.akkha.statuses.getOrPut(player.uid, ::AkkhaStatus)
        when (quadrant) {
            AkkhaQuadrant.BURN -> {
                status.nextBurnAt = clock.cycle + AkkhaTuning.BURN_CADENCE.value
                status.burnExpiresAt =
                    clock.cycle + AkkhaTuning.BURN_TICKS.value * AkkhaTuning.BURN_CADENCE.value
                activePlayers(raid)
                    .filter { it !== player && it.coords.chebyshevDistance(player.coords) <= 1 }
                    .forEach { teammate ->
                        val spread = raid.akkha.statuses.getOrPut(teammate.uid, ::AkkhaStatus)
                        spread.nextBurnAt = status.nextBurnAt
                        spread.burnExpiresAt = status.burnExpiresAt
                    }
                player.mes("The burn spreads to players standing next to you.")
            }
            AkkhaQuadrant.DARKNESS -> {
                if (!status.combatDrained) {
                    CombatEffects.statDrain(
                        player,
                        AKKHA_DRAIN_STATS,
                        AkkhaTuning.DARKNESS_DRAIN.value,
                    )
                    status.combatDrained = true
                }
                status.nextDarknessHitAt = clock.cycle + AKKHA_DARKNESS_CADENCE
                status.darknessExpiresAt =
                    clock.cycle + AKKHA_DARKNESS_HITS * AKKHA_DARKNESS_CADENCE
                player.mes("Darkness drains your Combat skills.")
            }
            AkkhaQuadrant.LIGHTNING -> {
                player.disableProtectionPrayers()
                status.lightningExpiresAt = clock.cycle + AkkhaTuning.LIGHTNING_SUPPRESSION.value
                player.mes("Lightning temporarily suppresses your protection prayers.")
            }
            AkkhaQuadrant.FREEZE -> {
                status.freezeExpiresAt = clock.cycle + AkkhaTuning.FREEZE_DURATION.value
                status.freezeAttackDelayApplied = false
                player.mes("Freeze slows each of your attacks by one game tick.")
            }
        }
        traceAkkha(
            raid,
            source,
            "status_applied",
            "target" to player.uid,
            "statusApplied" to quadrant,
        )
    }

    private fun processAkkhaStatuses(raid: ToaRaidSession, source: Npc) {
        val statuses = raid.akkha.statuses.toMap()
        val burnSpreads = mutableListOf<Pair<Player, Int>>()
        statuses.forEach { (uid, status) ->
            val player = uid.resolve(players) ?: return@forEach
            if (
                status.nextBurnAt >= 0 &&
                    clock.cycle >= status.nextBurnAt &&
                    clock.cycle <= status.burnExpiresAt
            ) {
                queueSpecialHit(player, source, AkkhaTuning.BURN_DAMAGE.value, 1)
                status.nextBurnAt += AkkhaTuning.BURN_CADENCE.value
                activePlayers(raid)
                    .filter { it !== player && it.coords.chebyshevDistance(player.coords) <= 1 }
                    .forEach { teammate -> burnSpreads += teammate to status.burnExpiresAt }
            }
            if (status.burnExpiresAt >= 0 && clock.cycle > status.burnExpiresAt) {
                status.burnExpiresAt = -1
                status.nextBurnAt = -1
            }
            if (
                status.nextDarknessHitAt >= 0 &&
                    clock.cycle >= status.nextDarknessHitAt &&
                    clock.cycle <= status.darknessExpiresAt
            ) {
                queueSpecialHit(player, source, AKKHA_DARKNESS_DAMAGE, 1)
                status.nextDarknessHitAt += AKKHA_DARKNESS_CADENCE
            }
            if (status.darknessExpiresAt >= 0 && clock.cycle > status.darknessExpiresAt) {
                status.darknessExpiresAt = -1
                status.nextDarknessHitAt = -1
            }
            if (status.lightningExpiresAt >= 0 && clock.cycle >= status.lightningExpiresAt) {
                status.lightningExpiresAt = -1
                ProtectionPrayerLock.clear(player)
            }
            if (status.freezeExpiresAt >= 0 && clock.cycle >= status.freezeExpiresAt) {
                status.freezeExpiresAt = -1
                status.freezeAttackDelayApplied = false
                player.mes("<col=229628>The ice around you melts away.</col>")
            }
        }
        burnSpreads.forEach { (recipient, expiresAt) ->
            val status = raid.akkha.statuses.getOrPut(recipient.uid, ::AkkhaStatus)
            status.burnExpiresAt = maxOf(status.burnExpiresAt, expiresAt)
            if (status.nextBurnAt < clock.cycle) {
                status.nextBurnAt = clock.cycle + AkkhaTuning.BURN_CADENCE.value
            }
        }
    }

    private fun cleanupBabaPlayer(raid: ToaRaidSession, player: Player) {
        raid.baba.pendingRocks.removeAll { it.target == player.uid }
        if (raid.baba.target == player.uid) raid.baba.target = null
        if (raid.baba.previousTarget == player.uid) raid.baba.previousTarget = null
    }

    private fun cleanupBaba(raid: ToaRaidSession, resetState: Boolean = true) {
        val state = raid.baba
        state.pendingRocks.clear()
        state.pendingRubble.clear()
        state.pendingShockwaves.clear()
        state.bananas.values.toList().forEach { pile ->
            locs
                .findAll(pile.tile)
                .filter { it.id == BabaAssets.BANANA_PILE_LOC }
                .toList()
                .forEach { locs.del(it, Int.MAX_VALUE) }
        }
        state.bananas.clear()
        state.sarcophagi.values.toList().forEach { sarc ->
            sarc.state = BabaSarcophagusState.RESETTING
            val opened =
                locs.findAll(sarc.tile).firstOrNull { it.id == BabaAssets.OPEN_SARCOPHAGUS_LOC }
            if (opened != null) {
                locs.change(
                    opened,
                    requireNotNull(ServerCacheManager.getObject(BabaAssets.CLOSED_SARCOPHAGUS_LOC)),
                    Int.MAX_VALUE,
                )
            }
        }
        val hazards =
            raid.npcRoles
                .filter { (npc, role) ->
                    role == ToaNpcRole.BOULDER ||
                        (role == ToaNpcRole.ADD && npc.id == BabaAssets.BABOON) ||
                        (role == ToaNpcRole.HAZARD && npc.id == BabaAssets.SARCOPHAGUS)
                }
                .keys
                .toList()
        hazards.forEach { removeNpc(raid, it) }
        state.rubble.clear()
        state.rollingBoulders.clear()
        state.destroyedBoulders.clear()
        state.sarcophagi.clear()
        forEachOnline(raid) { cleanupBabaPlayer(raid, it) }
        if (resetState) state.reset()
    }

    private fun maintainBabaAggression(raid: ToaRaidSession, source: Npc) {
        val target = selectBabaTarget(raid, postBoulder = false)
        if (target == null) {
            source.abortRoute()
            source.mode = null
            return
        }
        val interaction = source.interaction as? InteractionPlayer
        if (source.mode != NpcMode.ApPlayer2 || interaction?.uid != target.uid) {
            source.faceSquare(target.coords)
            source.apPlayer2(target, interactions)
            traceBaba(raid, source, "aggression_rearmed", "target" to target.uid)
        }
    }

    private fun selectBabaTarget(raid: ToaRaidSession, postBoulder: Boolean): Player? {
        val active = activePlayers(raid)
        if (active.isEmpty()) return null
        val state = raid.baba
        val first = state.target?.resolve(players)?.takeIf { it in active }
        if (state.attackCounter == 0 && !postBoulder && first != null) return first
        val ordered = active.sortedBy(Player::hitpoints)
        val chosen =
            if (postBoulder && ordered.size > 1 && ordered.first().uid == state.previousTarget)
                ordered[1]
            else ordered.first()
        val previous = state.target
        state.previousTarget = previous
        state.target = chosen.uid
        if (previous != chosen.uid) {
            traceBaba(
                raid,
                boss(raid),
                "target_change",
                "previousTarget" to previous,
                "target" to chosen.uid,
                "postBoulder" to postBoulder,
            )
        }
        return chosen
    }

    private fun traceBaba(
        raid: ToaRaidSession,
        source: Npc?,
        event: String,
        vararg fields: Pair<String, Any?>,
    ) {
        val state = raid.baba
        val common =
            arrayOf<Pair<String, Any?>>(
                "encounterId" to state.encounterId,
                "generation" to state.generation,
                "partySize" to activePlayers(raid).size,
                "raidLevel" to raid.raidLevel,
                "pathLevel" to (raid.pathLevels[ToaPath.APMEKEN] ?: 0),
                "state" to state.state,
                "npcId" to source?.id,
                "npcTile" to source?.coords,
                "target" to state.target,
                "autoAttackCount" to state.attackCounter,
                "pendingThresholds" to state.pendingRollingThresholds.joinToString(","),
                "rollingSet" to state.rollingSet,
                "weakLane" to state.weakLane,
                "bossHp" to source?.hitpoints,
                "bossMaxHp" to source?.baseHitpointsLvl,
            )
        state.trace.record(clock.cycle, event, *(common + fields))?.let {
            log.info { it.toString() }
        }
    }

    private fun babaCoord(raid: ToaRaidSession, localX: Int, localZ: Int): CoordGrid =
        requireNotNull(
            instances.localCoord(
                raid.instance,
                ToaInstanceLayout.roomLocal(ToaRoom.BABA, localX, localZ),
            )
        ) {
            "Missing Ba-Ba room coordinate $localX,$localZ"
        }

    private fun akkhaCoord(raid: ToaRaidSession, localX: Int, localZ: Int): CoordGrid =
        requireNotNull(
            instances.localCoord(
                raid.instance,
                ToaInstanceLayout.roomLocal(ToaRoom.AKKHA, localX, localZ),
            )
        ) {
            "Missing Akkha room coordinate $localX,$localZ"
        }

    private fun cleanupAkkhaPlayer(raid: ToaRaidSession, player: Player) {
        ProtectionPrayerLock.clear(player)
        player.clearTint()
        raid.akkha.trailOrbs
            .filter { it.owner == player.uid }
            .toList()
            .forEach { removeTrailActor(raid, it) }
        raid.akkha.retirePlayer(player.uid)
        raid.encounter.pendingHits.removeAll { it.target == player.uid }
    }

    private fun cleanupAkkha(raid: ToaRaidSession) {
        forEachOnline(raid) { player -> cleanupAkkhaPlayer(raid, player) }
        akkhaFinalSwapReadyAt.remove(raid.instance.id)
        akkhaLastStyleChangeAt.remove(raid.instance.id)
        clearAkkhaMemorySymbols(raid)
        raid.encounter.pendingHits.clear()
        raid.akkha.reset()
    }

    private fun traceAkkha(
        raid: ToaRaidSession,
        source: Npc?,
        event: String,
        vararg fields: Pair<String, Any?>,
    ) {
        val state = raid.akkha
        val common =
            arrayOf<Pair<String, Any?>>(
                "encounterId" to state.encounterId,
                "partySize" to activePlayers(raid).size,
                "raidLevel" to raid.raidLevel,
                "pathLevel" to (raid.pathLevels[ToaPath.HET] ?: 0),
                "state" to state.state,
                "npcId" to source?.id,
                "npcTile" to source?.coords,
                "target" to state.target,
                "combatStyle" to state.style,
                "overheadPrayer" to state.style.hitType,
                "autoAttackCount" to state.attackCounter,
                "special" to state.activeSpecials.joinToString("+"),
                "quadrant" to state.quadrant,
                "shadowThreshold" to state.activeShadowThreshold,
                "shadowTimer" to state.shadowChargeEndsAt,
                "bossHp" to source?.hitpoints,
                "bossMaxHp" to source?.baseHitpointsLvl,
                "finalStandHitCount" to state.finalStandHitCounter,
            )
        state.trace.record(clock.cycle, event, *(common + fields))?.let { entry ->
            log.info { entry.toString() }
        }
    }

    private fun randomActive(raid: ToaRaidSession): Player? = activePlayers(raid).randomOrNull()

    private fun selectAkkhaTarget(raid: ToaRaidSession, source: Npc): Player? {
        val state = raid.akkha
        val active = activePlayers(raid)
        val retained = state.target?.resolve(players)?.takeIf { it in active }
        val selected =
            retained
                ?: active.minWithOrNull(
                    compareBy<Player> { akkhaDistance(source, it.coords) }
                        .thenByDescending(::akkhaDefenceTotal)
                        .thenBy { it.uid.packed }
                )
        val previous = state.target
        state.target = selected?.uid
        if (previous != state.target) {
            traceAkkha(
                raid,
                source,
                "target_change",
                "previousTarget" to previous,
                "target" to state.target,
                "reason" to if (retained == null) "initial_or_invalid" else "retained",
            )
        }
        return selected
    }

    private fun maintainAkkhaAggression(raid: ToaRaidSession, source: Npc) {
        rearmAkkhaAggression(raid, source, force = false)
    }

    private fun rearmAkkhaAggression(raid: ToaRaidSession, source: Npc, force: Boolean): Player? {
        val target = selectAkkhaTarget(raid, source)
        if (target == null) {
            source.abortRoute()
            source.mode = null
            return null
        }
        source.maxOpRangeOverride = AkkhaTuning.ROOM_CHASE_RANGE.value
        val desiredAttackRange =
            AkkhaAggressionPolicy.attackRange(
                raid.akkha.style,
                lureRequired = raid.akkha.pendingLureQuadrant != null,
            )
        source.apRangeOverride = desiredAttackRange
        // Refresh facing even when the retained target UID has not changed. Akkha teleports and
        // transmogs throughout the encounter. AP interactions also snapshot their attack range,
        // so the shadow-lure chase must replace any retained ranged or magic interaction.
        source.faceSquare(target.coords)
        val interaction = source.interaction as? InteractionPlayer
        if (
            AkkhaAggressionPolicy.shouldRearm(
                force,
                source.mode == NpcMode.ApPlayer2,
                interaction?.uid == target.uid,
                interaction?.apRange == desiredAttackRange,
            )
        ) {
            source.apPlayer2(target, interactions)
            traceAkkha(
                raid,
                source,
                "aggression_rearmed",
                "target" to target.uid,
                "forced" to force,
            )
        }
        return target
    }

    private fun canLeadRaidAction(player: Player, raid: ToaRaidSession): Boolean {
        if (raid.party.leader == player.uid) return true
        val leaderOnline = raid.participant(raid.party.leader)?.connected == true
        if (leaderOnline) return false
        val actingLeader =
            raid.party.members.firstOrNull { uid -> raid.participant(uid)?.connected == true }
        return actingLeader == player.uid
    }

    private fun akkhaDefenceTotal(player: Player): Int {
        val bonuses = wornBonuses.calculate(player)
        return bonuses.defStab +
            bonuses.defSlash +
            bonuses.defCrush +
            bonuses.defRange +
            bonuses.defMagic
    }

    private fun exactPlayerTraversal(player: Player): Set<CoordGrid> = buildSet {
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

    private fun isAdjacentToAkkha(source: Npc, tile: CoordGrid): Boolean =
        akkhaDistance(source, tile) == 1

    private fun akkhaDistance(source: Npc, tile: CoordGrid): Int {
        val minX = source.coords.x
        val maxX = minX + source.size - 1
        val minZ = source.coords.z
        val maxZ = minZ + source.size - 1
        val dx = if (tile.x < minX) minX - tile.x else if (tile.x > maxX) tile.x - maxX else 0
        val dz = if (tile.z < minZ) minZ - tile.z else if (tile.z > maxZ) tile.z - maxZ else 0
        return maxOf(dx, dz)
    }

    private fun nearestActive(raid: ToaRaidSession, tile: CoordGrid): Player? =
        activePlayers(raid).minByOrNull { it.coords.chebyshevDistance(tile) }

    private fun challengePlayerIsActive(raid: ToaRaidSession, player: Player): Boolean {
        val room = raid.currentRoom
        if (room !in ToaChallengeRoomRules.rooms) return true
        return raid.challenge?.stage == ToaChallengeStage.ACTIVE &&
            ToaChallengeRoomRules.isInside(raid, instances, room, player.coords)
    }

    private fun requireActiveChallenge(
        player: Player,
        raid: ToaRaidSession,
        path: ToaPath,
        requireInside: Boolean = true,
    ): Boolean {
        val participant = raid.participant(player.uid) ?: return false
        if (
            raid.currentPath != path ||
                raid.currentRoom != path.challenge ||
                participant.currentRoom != path.challenge ||
                participant.spectating
        ) {
            return false
        }
        if (raid.challenge?.stage != ToaChallengeStage.ACTIVE) {
            player.mes("Pass the red barrier to begin the challenge.")
            return false
        }
        if (
            requireInside &&
                !ToaChallengeRoomRules.isInside(raid, instances, path.challenge, player.coords)
        ) {
            player.mes("Pass the red barrier before using that.")
            return false
        }
        return true
    }

    private fun activePlayers(raid: ToaRaidSession): List<Player> =
        raid.participants.values
            .filter {
                it.rewardEligible &&
                    !it.spectating &&
                    it.connected &&
                    it.currentRoom == raid.currentRoom
            }
            .mapNotNull { it.uid.resolve(players) }
            .filter { challengePlayerIsActive(raid, it) }

    private inline fun forEachOnline(raid: ToaRaidSession, action: (Player) -> Unit) =
        raid.participants.values
            .filter { it.rewardEligible && it.connected }
            .mapNotNull { it.uid.resolve(players) }
            .forEach(action)

    private fun terminalCleanupSupplies(raid: ToaRaidSession) {
        raid.participants.values.forEach { participant ->
            val player = if (participant.connected) participant.uid.resolve(players) else null
            if (player != null) {
                supplies.terminalCleanup(player, raid)
            } else {
                supplies.terminalCleanup(raid, participant.accountHash)
            }
        }
        supplies.terminalCleanupRaid(raid)
    }

    private fun clearRaidNpcs(raid: ToaRaidSession) {
        cleanupApmeken(raid)
        scabaras.cleanup(raid)
        het.cleanup(raid)
        crondisZebak.cleanup(raid)
        cleanupKephri(raid)
        if (raid.currentRoom == ToaRoom.BABA) cleanupBaba(raid)
        clearRewardEntry(raid)
        closeBossBars(raid, raid.spawnedNpcs)
        raid.spawnedNpcs.toList().forEach(::prepareNpcRemoval)
        raid.spawnedNpcs.toList().forEach { if (it.isSlotAssigned) npcs.del(it, Int.MAX_VALUE) }
        raid.spawnedNpcs.clear()
        raid.npcRoles.clear()
    }

    private fun cleanupKephri(raid: ToaRaidSession) {
        val state = raid.encounter
        state.kephriPendingDungStrikes.clear()
        state.kephriDungPlacements.clear()
        state.kephriBombTargets.clear()
        state.kephriBombs.clear()
        state.kephriEggBatches.clear()
        state.kephriEggs.clear()
        state.kephriSwarms.clear()
        state.kephriSoldiers.clear()
        state.kephriSpitters.clear()
        state.kephriArcanes.clear()
        state.kephriDungTiles.toList().forEach { tile ->
            locs
                .findAll(tile)
                .filter { it.id == KephriDungStrike.DUNG_LOC }
                .forEach { locs.del(it, Int.MAX_VALUE) }
        }
        state.kephriDungTiles.clear()
        state.kephriNextMedicAt = -1
        state.kephriNextBomberBombAt = -1
        state.kephriDying = false
        state.kephriCompletesAt = -1
        state.kephriDeathHero = null
    }

    private fun removeNpc(raid: ToaRaidSession, npc: Npc) {
        closeBossBars(raid, listOf(npc))
        prepareNpcRemoval(npc)
        if (npc.isSlotAssigned) npcs.del(npc, Int.MAX_VALUE)
        raid.spawnedNpcs.remove(npc)
        raid.npcRoles.remove(npc)
    }

    private fun clearEncounterNpcs(raid: ToaRaidSession, preserve: Set<Npc> = emptySet()) {
        cleanupApmeken(raid)
        scabaras.cleanup(raid)
        het.cleanup(raid)
        crondisZebak.cleanup(raid)
        cleanupKephri(raid)
        raid.encounter.pendingHits.clear()
        raid.encounter.kephriBombs.clear()
        raid.encounter.kephriSwarms.clear()
        if (raid.currentRoom == ToaRoom.AKKHA) cleanupAkkha(raid)
        if (raid.currentRoom == ToaRoom.BABA) cleanupBaba(raid)
        val removable =
            raid.npcRoles
                .filter { (npc, role) -> role != ToaNpcRole.HELPFUL_SPIRIT && npc !in preserve }
                .keys
                .toList()
        closeBossBars(raid, removable)
        removable.forEach(::prepareNpcRemoval)
        removable.forEach { npc ->
            if (npc.isSlotAssigned) npcs.del(npc, Int.MAX_VALUE)
            raid.spawnedNpcs.remove(npc)
            raid.npcRoles.remove(npc)
        }
    }

    private fun closeBossBars(raid: ToaRaidSession, actors: Collection<Npc>) {
        actors.forEach { actor ->
            forEachOnline(raid) { player -> bossHpBar.onClose(player, actor, instant = true) }
        }
    }

    private fun isWardenRoom(room: ToaRoom): Boolean =
        room == ToaRoom.WARDENS || room == ToaRoom.TUMEKEN_WARDEN

    private fun cleanupWardenPlayer(raid: ToaRaidSession, player: Player) {
        if (raid.encounter.wardenStoneReleaseAt.remove(player.uid) != null) {
            CombatEffects.unfreeze(player)
        }
    }

    private fun cleanupWardenEffects(raid: ToaRaidSession) {
        raid.encounter.wardenStoneReleaseAt.keys
            .toList()
            .mapNotNull { it.resolve(players) }
            .forEach(CombatEffects::unfreeze)
        raid.encounter.wardenStoneReleaseAt.clear()
        raid.encounter.wardenAreaPulses.clear()
        clearWardenPhaseOneHazards(raid)
        raid.encounter.wardenPhaseOneSpecials.clear()
        raid.encounter.wardenPhaseOneSpecialEndsAt.clear()
        raid.encounter.wardenPhaseOneSpecialQueue.clear()
        raid.encounter.wardenNextEnergyPulseAt = -1
        raid.encounter.wardenNextPassiveChargeAt = -1
        raid.encounter.wardenNextPhaseTwoAttackAt = -1
        raid.encounter.wardenNextPhaseTwoObeliskAttackAt = -1
        raid.encounter.wardenNextPhantomAttackAt = -1
        raid.encounter.wardenPhantomAttackCounter = 0
        raid.encounter.wardenChargeBalls.clear()
        clearWardenChargeCircle(raid)
        raid.encounter.pendingHits.clear()
    }

    private fun prepareNpcRemoval(npc: Npc) {
        npc.mode = null
        npc.clearInteractionRoute()
        npc.abortRoute()
        npc.pendingStepCount = 0
        npc.pendingTeleport = false
        npc.pendingTelejump = false
        npc.pendingExactMove = null
        npc.lastProcessedCoord = CoordGrid.NULL
        npc.lastMovement = Int.MIN_VALUE
    }

    private fun prepare(player: Player, cancelActiveCoroutine: Boolean = true) {
        if (cancelActiveCoroutine) player.cancelActiveCoroutine()
        player.combatClearQueue()
        player.clearInteractionRoute()
        ConsumableRestrictionState.clear(player)
        SpecialAttackEnergyModifier.clearMinimumCost(player)
    }

    private fun applyRaidRestrictions(player: Player, raid: ToaRaidSession) {
        val invocations = raid.startSnapshot.invocations
        ConsumableRestrictionState.setFoodBlocked(player, ToaInvocation.ON_A_DIET in invocations)
        ConsumableRestrictionState.setHealingPotionsBlocked(
            player,
            ToaInvocation.DEHYDRATION in invocations,
        )
        if (ToaInvocation.OVERLY_DRAINING in invocations) {
            SpecialAttackEnergyModifier.setMinimumCost(player, SpecialAttackEnergy.MAX_ENERGY)
        } else {
            SpecialAttackEnergyModifier.clearMinimumCost(player)
        }
    }

    private fun resetCombat(player: Player) {
        player.disablePrayers()
        player.statRestoreAll(COMBAT_STATS)
        specialEnergy.restoreFullSpecialEnergy(player)
        player.runEnergy = constants.run_max_energy
        UpdateRun.energy(player, player.runEnergy)
    }

    private fun rejoinDestination(raid: ToaRaidSession, participant: ToaParticipant): ToaRoom {
        if (raid.state == ToaRaidState.REWARDS) return ToaRoom.REWARD
        val path = raid.currentPath
        if (path != null && raid.currentRoom == path.boss) return path.boss
        if (
            path != null &&
                participant.currentRoom == path.challenge &&
                raid.challenge?.complete == true
        ) {
            return path.boss
        }
        return participant.currentRoom
    }

    private fun rebindRaidPlayer(raid: ToaRaidSession, oldUid: PlayerUid, newUid: PlayerUid) {
        if (oldUid == newUid) return
        raid.encounter.rebindPlayer(oldUid, newUid)
        raid.akkha.rebindPlayer(oldUid, newUid)
        raid.baba.rebindPlayer(oldUid, newUid)
        raid.crondis.rebindPlayer(oldUid, newUid)
        raid.zebak.rebindPlayer(oldUid, newUid)
        scabaras.rebindPlayer(raid, oldUid, newUid)
        apmekenPlayerSlots[raid.instance.id]?.rebindKey(oldUid, newUid)

        val party = raid.party
        val rebound = party.members.mapTo(linkedSetOf()) { if (it == oldUid) newUid else it }
        party.members.clear()
        party.members.addAll(rebound)
        membership.remove(oldUid)
        membership[newUid] = party
        viewedParties.remove(oldUid)?.let { viewedParties[newUid] = it }
        if (party.leader == oldUid) {
            parties.remove(oldUid)
            party.leader = newUid
            parties[newUid] = party
        }
    }

    private fun retirePlayer(player: Player, raid: ToaRaidSession, removeFromParty: Boolean) {
        val participant = raid.participant(player.accountHash) ?: return
        retireApmekenPlayer(raid, player)
        participant.connected = false
        participant.spectating = true
        participant.rewardEligible = false
        participantRaids.remove(player.accountHash)
        raid.encounter.retirePlayer(player.uid)
        if (raid.currentRoom == ToaRoom.AKKHA) {
            cleanupAkkhaPlayer(raid, player)
        } else {
            raid.akkha.retirePlayer(player.uid)
        }
        raid.baba.retirePlayer(player.uid)
        raid.crondis.retirePlayer(player.uid)
        raid.zebak.retirePlayer(player.uid)
        scabaras.retirePlayer(raid, player.uid)
        if (raid.currentRoom == ToaRoom.BABA) cleanupBabaPlayer(raid, player)
        if (isWardenRoom(raid.currentRoom)) cleanupWardenPlayer(raid, player)
        if (removeFromParty) {
            raid.party.members.remove(player.uid)
            membership.remove(player.uid)
        }
    }

    private fun leaveRaid(
        player: Player,
        raid: ToaRaidSession,
        removeFromParty: Boolean,
        teleport: Boolean = true,
    ): Boolean {
        raid.spawnedNpcs.forEach { actor -> bossHpBar.onClose(player, actor, instant = true) }
        supplies.terminalCleanup(player, raid)
        retirePlayer(player, raid, removeFromParty)
        if (instances.sessionForPlayer(player)?.id == raid.instance.id)
            instances.leave(player, raid.instance, clock.cycle)
        prepare(player)
        if (teleport) PathingEntityCommon.telejump(player, collision, TOA_ENTRANCE)
        else player.coords = TOA_ENTRANCE
        ui.close(player)
        if (raid.party.members.isEmpty()) {
            if (raid.state == ToaRaidState.COMPLETE) completeRaid(raid)
            else abort(raid, "party empty")
        } else if (raid.party.leader == player.uid) transferOrDisband(raid.party)
        syncRewardNiche(player)
        return true
    }

    private fun syncRewardNiche(player: Player) {
        val varbit = requireNotNull(ServerCacheManager.getVarbit(ToaLocs.REWARD_NICHE_VARBIT))
        VarPlayerIntMapSetter.set(player, varbit, if (rewards.hasStored(player)) 1 else 0)
    }

    private fun completeRaid(raid: ToaRaidSession) {
        if (raid.cleanupStarted) return
        raid.cleanupStarted = true
        raid.state = ToaRaidState.COMPLETE
        if (isWardenRoom(raid.currentRoom)) cleanupWardenEffects(raid)
        clearRaidNpcs(raid)
        terminalCleanupSupplies(raid)
        raid.participants.values.forEach { participantRaids.remove(it.accountHash) }
        restoreWardenFloor(raid)
        raids.remove(raid.instance.id)
        raid.party.activeSession = null
        pendingResets.remove(raid.instance.id)
        parties.remove(raid.party.leader)
        raid.party.applicants.clear()
        viewedParties.entries.removeIf { it.value == raid.party.id }
        instances.terminate(raid.instance)
        log.info { "[ToA] complete party=" + raid.party.id + " instance=" + raid.instance.id }
    }

    private fun abort(raid: ToaRaidSession, reason: String) {
        if (raid.cleanupStarted) return
        raid.cleanupStarted = true
        raid.state = ToaRaidState.ABANDONED
        if (isWardenRoom(raid.currentRoom)) cleanupWardenEffects(raid)
        clearRaidNpcs(raid)
        terminalCleanupSupplies(raid)
        forEachOnline(raid) { player ->
            if (instances.sessionForPlayer(player)?.id == raid.instance.id)
                instances.leave(player, raid.instance, clock.cycle)
            prepare(player)
            PathingEntityCommon.telejump(player, collision, TOA_ENTRANCE)
            ui.close(player)
            player.mes("Your Tombs raid has ended: $reason.")
        }
        raid.participants.values.forEach { participantRaids.remove(it.accountHash) }
        restoreWardenFloor(raid)
        raids.remove(raid.instance.id)
        raid.party.activeSession = null
        pendingResets.remove(raid.instance.id)
        instances.terminate(raid.instance)
        log.info {
            "[ToA] abort party=${raid.party.id} instance=${raid.instance.id} reason=$reason"
        }
    }

    private fun transferOrDisband(party: ToaParty) {
        val replacement = party.members.firstOrNull()
        parties.remove(party.leader)
        if (replacement != null) {
            party.leader = replacement
            parties[replacement] = party
            message(party, "${label(replacement)} is now the party leader.")
        } else {
            party.applicants.forEach {
                it.resolve(players)?.mes("The ToA party you applied to was disbanded.")
            }
            party.applicants.clear()
            viewedParties.entries.removeIf { it.value == party.id }
        }
    }

    private fun disbandParty(player: Player, party: ToaParty) {
        if (party.leader != player.uid || party.activeSession != null) return
        parties.remove(party.leader)
        party.members.forEach { uid ->
            membership.remove(uid)
            uid.resolve(players)?.mes("The ToA party has been disbanded.")
        }
        party.applicants.forEach { uid ->
            uid.resolve(players)?.mes("The ToA party you applied to was disbanded.")
        }
        viewedParties.entries.removeIf { it.value == party.id }
        party.members.clear()
        party.applicants.clear()
    }

    private fun viewedParty(player: Player): ToaParty? {
        val id = viewedParties[player.uid] ?: return null
        return parties.values.firstOrNull { it.id == id }
    }

    private fun label(uid: PlayerUid): String = uid.resolve(players)?.displayName ?: uid.toString()

    private fun message(party: ToaParty, text: String) =
        party.members.forEach { it.resolve(players)?.mes(text) }

    private companion object {
        const val CHALLENGE_ENTRY_TIMEOUT_TICKS = 20
        val ATTACKABLE_ROLES =
            setOf(
                ToaNpcRole.BOSS,
                ToaNpcRole.CHALLENGE,
                ToaNpcRole.WARDEN_PHASE,
                ToaNpcRole.OBELISK,
                ToaNpcRole.ADD,
                ToaNpcRole.CORE,
                ToaNpcRole.SKULL,
                ToaNpcRole.BOULDER,
            )
        val AKKHA_DRAIN_STATS =
            listOf("stat.attack", "stat.strength", "stat.defence", "stat.ranged", "stat.magic")
        val COMBAT_STATS =
            listOf(
                "stat.attack",
                "stat.strength",
                "stat.defence",
                "stat.hitpoints",
                "stat.ranged",
                "stat.magic",
                "stat.prayer",
            )
    }
}
