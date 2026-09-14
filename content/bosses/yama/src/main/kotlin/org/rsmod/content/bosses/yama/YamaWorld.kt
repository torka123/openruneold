package org.rsmod.content.bosses.yama

import dev.openrune.ServerCacheManager
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceScript
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.plugin.scripts.ScriptContext

internal class YamaWorld
@Inject
constructor(
    private val encounters: YamaEncounterManager,
    private val storage: YamaDeathStorage,
    registry: BossInstanceRegistry,
) : InstanceScript(registry, YAMA_INSTANCE_KEY, yamaSpec()) {
    override fun ScriptContext.configure() {
        encounters.validateAssets()
        onInstancePlayerJoin {
            manager.sessionForId(instanceId)?.let { encounters.onJoin(player, it) }
        }
        onInstancePlayerLeave { encounters.onLeave(player, instanceId) }

        val voiceTypes =
            listOf(
                    YamaAssets.VOICE_PARENT,
                    YamaAssets.VOICE_TALK,
                    YamaAssets.VOICE_TRAVEL,
                    YamaAssets.VOICE_JOIN,
                )
                .map(::requireNpc)
        voiceTypes.forEach { voice ->
            onOpNpc1(voice) { talkToVoice() }
            onOpNpc3("npc.${voice.internalName.removePrefix("npc.")}") {
                arriveDelay()
                encounters.travel(player)
            }
            onOpNpc4("npc.${voice.internalName.removePrefix("npc.")}") {
                arriveDelay()
                encounters.joinInvitation(player)
            }
        }

        val judge = requireNpc(YamaAssets.JUDGE)
        onOpNpc2("npc.${judge.internalName.removePrefix("npc.")}") {
            if (!encounters.isEncounterNpc(it.npc)) {
                arriveDelay()
                encounters.travel(player)
            }
        }

        listOf(YamaAssets.THRONE_PARENT, YamaAssets.THRONE_OCCUPIED).map(::requireNpc).forEach {
            throne ->
            onOpNpc1(throne) { configureChallenge() }
            onOpNpc3("npc.${throne.internalName.removePrefix("npc.")}") {
                arriveDelay()
                encounters.challenge(player)
            }
        }

        val steppingStone = requireLoc(YamaAssets.STEPPING_STONE)
        onOpLoc1(steppingStone) { encounters.jumpStone(player, it.loc.coords) }
        onApLoc1(steppingStone) {
            if (isWithinApRange(it.loc, YamaRules.JUDGE_STONE_INTERACTION_RANGE)) {
                encounters.jumpStone(player, it.loc.coords)
            }
        }
        onOpLoc1(requireLoc(YamaAssets.FIREWALL)) {
            arriveDelay()
            encounters.passFirewall(player, quick = false)
        }
        onOpLoc2(requireLoc(YamaAssets.FIREWALL)) {
            arriveDelay()
            encounters.passFirewall(player, quick = true)
        }

        onOpHeld1(requireItem(YamaAssets.DOSSIER)) { encounters.openDossier(player) }
        YamaContract.itemContracts.forEach { contract ->
            onOpHeld1(requireItem(requireNotNull(contract.itemId))) {
                encounters.selectContract(player, contract)
            }
        }

        onCommand("yama") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Travel to the Voice of Yama"
            invalidArgs = "Use as ::yama"
            cheat { if (args.isEmpty()) encounters.teleportOutside(player) }
        }
        onCommand("yamadebug") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Show current Yama encounter state"
            invalidArgs = "Use as ::yamadebug"
            cheat { if (args.isEmpty()) player.mes(encounters.debugStatus(player)) }
        }
        onCommand("yamaphase") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Move the active Yama test encounter to phase 1, 2, or 3"
            invalidArgs = "Use as ::yamaphase 1|2|3"
            cheat {
                val phase = args.singleOrNull()?.toIntOrNull()
                if (phase == null || phase !in 1..3) player.mes("Use as ::yamaphase 1|2|3")
                else encounters.debugPhase(player, phase)
            }
        }
    }

    private suspend fun ProtectedAccess.talkToVoice() {
        if (storage.hasItems(player)) {
            when (
                choice5(
                    "Reclaim my lost items",
                    1,
                    "View my records",
                    2,
                    "Enter Yama's Domain",
                    3,
                    "Invite a duo partner",
                    4,
                    "Join my invitation",
                    5,
                )
            ) {
                1 -> reclaimFromVoice()
                2 -> showRecords()
                3 -> encounters.travel(player)
                4 -> {
                    val name = stringDialog("Enter the player's name:").trim()
                    if (name.isNotEmpty()) encounters.invite(player, name)
                }
                5 -> encounters.joinInvitation(player)
            }
            return
        }
        when (
            choice5(
                "Enter Yama's Domain",
                1,
                "Invite a duo partner",
                2,
                "Join my invitation",
                3,
                "View my records",
                4,
                "Never mind",
                5,
            )
        ) {
            1 -> encounters.travel(player)
            2 -> {
                val name = stringDialog("Enter the player's name:").trim()
                if (name.isNotEmpty()) encounters.invite(player, name)
            }
            3 -> encounters.joinInvitation(player)
            4 -> showRecords()
        }
    }

    private suspend fun ProtectedAccess.showRecords() {
        val imp = encounters.summonRecordsImp(player)
        try {
            delay(YamaRecords.IMP_APPROACH_TICKS)
            val records = encounters.records(player)
            startDialogue(imp) {
                if (records.killCount == 0) {
                    chatNpc(neutral, "I have no completed Yama challenges recorded for you yet.")
                } else {
                    chatNpc(
                        neutral,
                        "Yama kills: ${records.killCount}.<br>Personal best: " +
                            YamaRecords.formatTime(records.personalBestTicks) +
                            ".",
                    )
                    val recent =
                        records.recentKillTimes.joinToString(", ") { YamaRecords.formatTime(it) }
                    chatNpc(neutral, "Your recent kill times are: $recent.")
                    chatNpc(
                        neutral,
                        "Your rolling four-kill average is " +
                            YamaRecords.formatTime(records.fourKillAverageTicks) +
                            ".",
                    )
                }
            }
        } finally {
            encounters.beginRecordsImpDestruction(player, imp)
            try {
                delay(YamaRecords.IMP_DESTROY_TICKS)
            } finally {
                encounters.removeRecordsImp(player, imp)
            }
        }
    }

    private suspend fun ProtectedAccess.reclaimFromVoice() {
        val reclaimFee = storage.fee(player)
        player.mes(
            if (reclaimFee == 0) "The Voice will return your items without charge."
            else "The Voice demands $reclaimFee coins to release your items."
        )
        if (choice2("Reclaim my items.", 1, "Not now.", 2) == 1) storage.reclaim(player, bank)
    }

    private suspend fun ProtectedAccess.configureChallenge() {
        when (
            choice4(
                "Normal challenge",
                1,
                "Use my selected contract",
                2,
                "Offer 100 noted pineapple pizzas",
                3,
                "Cancel",
                4,
            )
        ) {
            1 -> encounters.selectContract(player, YamaContract.NONE)
            2 -> encounters.confirmSelectedContract(player)
            3 -> encounters.selectContract(player, YamaContract.CUSTOM)
        }
    }

    private fun requireNpc(id: Int) =
        requireNotNull(ServerCacheManager.getNpc(id)) { "Missing Yama NPC $id" }

    private fun requireLoc(id: Int) =
        requireNotNull(ServerCacheManager.getObject(id)) { "Missing Yama loc $id" }

    private fun requireItem(id: Int) =
        requireNotNull(ServerCacheManager.getItem(id)) { "Missing Yama item $id" }
}
