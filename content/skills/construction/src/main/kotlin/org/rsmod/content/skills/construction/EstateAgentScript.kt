package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseConstructionLvl
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal const val ESTATE_AGENT = "npc.poh_estate_agent"
internal const val ESTATE_AGENT_ID = 3097
internal val EDGEVILLE_ESTATE_AGENT_COORDS = CoordGrid(3092, 3492, 0)
internal const val STARTER_HOUSE_PRICE = 1_000
internal const val CONSTRUCTION_CAPE_PRICE = 99_000

internal class EstateAgentScript @Inject constructor(private val npcs: NpcRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        ensureEdgevilleAgent()
        onOpNpc1(ESTATE_AGENT) { talk(it.npc) }
        onOpNpc3(ESTATE_AGENT) { relocate(it.npc) }
        onOpNpc4(ESTATE_AGENT) { redecorate(it.npc) }
    }

    private fun ensureEdgevilleAgent() {
        val existing =
            npcs.findAll(ZoneKey.from(EDGEVILLE_ESTATE_AGENT_COORDS), zoneRadius = 1).firstOrNull {
                npc ->
                npc.isSlotAssigned &&
                    npc.type.id == ESTATE_AGENT_ID &&
                    npc.spawnCoords == EDGEVILLE_ESTATE_AGENT_COORDS
            }
        if (existing != null) {
            existing.mode = NpcMode.None
            existing.respawns = true
            return
        }
        val type = requireNotNull(ServerCacheManager.getNpc(ESTATE_AGENT_ID))
        val agent =
            Npc(type, EDGEVILLE_ESTATE_AGENT_COORDS).also {
                it.mode = NpcMode.None
                it.respawns = true
            }
        npcs.add(agent, Int.MAX_VALUE)
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) =
        startDialogue(npc) {
            if (!player.hasConstructionHouse()) {
                chatNpc(
                    neutral,
                    "I can sell you a starter house for 1,000 coins. It will be connected to the house portal here in Edgeville.",
                )
                when (
                    choice3("Yes, please.", 1, "Can I buy a Construction cape?", 2, "No thanks.", 3)
                ) {
                    1 -> purchaseHouse()
                    2 -> purchaseCape()
                }
                return@startDialogue
            }
            chatNpc(neutral, "What can I do for you?")
            when (
                choice4(
                    "Tell me about my house.",
                    1,
                    "Can you move my house?",
                    2,
                    "Can you redecorate my house?",
                    3,
                    "Can I buy a Construction cape?",
                    4,
                )
            ) {
                1 ->
                    chatNpc(
                        neutral,
                        "Use the Edgeville house portal to enter normally or in building mode. Everything you build is saved to your account.",
                    )
                2 -> relocationDialogue()
                3 -> redecorationDialogue()
                4 -> purchaseCape()
            }
        }

    private suspend fun ProtectedAccess.relocate(npc: Npc) =
        startDialogue(npc) {
            if (!player.hasConstructionHouse()) {
                chatNpc(neutral, "You will need to buy a house before I can relocate it.")
            } else {
                relocationDialogue()
            }
        }

    private suspend fun ProtectedAccess.redecorate(npc: Npc) =
        startDialogue(npc) {
            if (!player.hasConstructionHouse()) {
                chatNpc(neutral, "You will need to buy a house before I can redecorate it.")
            } else {
                redecorationDialogue()
            }
        }

    private suspend fun Dialogue.purchaseHouse() {
        if (player.hasConstructionHouse()) {
            chatNpc(neutral, "You already own a house.")
            return
        }
        if (access.inv.count(COINS) < STARTER_HOUSE_PRICE) {
            chatNpc(neutral, "You need 1,000 coins in your inventory to buy a house.")
            return
        }
        val payment = access.invDel(access.inv, COINS, count = STARTER_HOUSE_PRICE, strict = true)
        if (payment.failure) {
            chatNpc(neutral, "I could not collect the payment, so no house was registered.")
            return
        }
        player.createConstructionHouse()
        chatNpc(
            happy,
            "Congratulations. Your house is in Rimmington. You can always use the Edgeville portal as a convenient entrance.",
        )
    }

    private suspend fun Dialogue.relocationDialogue() {
        val current = player.loadHouseBlueprint().location
        chatNpc(
            neutral,
            "Your house is currently registered in ${current.displayName}. Where would you like to move it?",
        )
        relocationPageOne()
    }

    private suspend fun Dialogue.relocationPageOne() {
        when (choice4("Rimmington", 1, "Taverley", 2, "Pollnivneach", 3, "More locations...", 4)) {
            1 -> changeLocation(HouseLocation.RIMMINGTON)
            2 -> changeLocation(HouseLocation.TAVERLEY)
            3 -> changeLocation(HouseLocation.POLLNIVNEACH)
            4 -> relocationPageTwo()
        }
    }

    private suspend fun Dialogue.relocationPageTwo() {
        when (choice4("Hosidius", 1, "Rellekka", 2, "Brimhaven", 3, "More locations...", 4)) {
            1 -> changeLocation(HouseLocation.HOSIDIUS)
            2 -> changeLocation(HouseLocation.RELLEKKA)
            3 -> changeLocation(HouseLocation.BRIMHAVEN)
            4 -> relocationPageThree()
        }
    }

    private suspend fun Dialogue.relocationPageThree() {
        when (
            choice4("Yanille", 1, "Prifddinas", 2, "Previous locations...", 3, "Never mind.", 4)
        ) {
            1 -> changeLocation(HouseLocation.YANILLE)
            2 -> changeLocation(HouseLocation.PRIFDDINAS)
            3 -> relocationPageOne()
        }
    }

    private suspend fun Dialogue.changeLocation(location: HouseLocation) {
        val blueprint = player.loadHouseBlueprint()
        if (blueprint.location == location) {
            chatNpc(neutral, "Your house is already in ${location.displayName}.")
            return
        }
        if (player.baseConstructionLvl < location.level) {
            chatNpc(
                neutral,
                "You need level ${location.level} Construction to move your house there.",
            )
            return
        }
        chatNpc(
            neutral,
            "Moving your house to ${location.displayName} costs ${location.price} coins. Shall I do it?",
        )
        if (choice2("Yes, move it.", 1, "No thanks.", 2) != 1) return
        if (!takeCoins(location.price)) return
        player.saveHouseBlueprint(blueprint.copy(location = location))
        chatNpc(
            happy,
            "Your house is now in ${location.displayName}. The Edgeville portal will still work as a convenient entrance.",
        )
    }

    private suspend fun Dialogue.redecorationDialogue() {
        val current = player.loadHouseBlueprint().style
        chatNpc(
            neutral,
            "Your house currently uses the ${current.displayName} style. What would you like?",
        )
        redecorationPageOne()
    }

    private suspend fun Dialogue.redecorationPageOne() {
        when (
            choice4("Basic wood", 1, "Basic stone", 2, "Whitewashed stone", 3, "More styles...", 4)
        ) {
            1 -> changeStyle(HouseStyle.BASIC_WOOD)
            2 -> changeStyle(HouseStyle.BASIC_STONE)
            3 -> changeStyle(HouseStyle.WHITEWASHED_STONE)
            4 -> redecorationPageTwo()
        }
    }

    private suspend fun Dialogue.redecorationPageTwo() {
        when (
            choice4(
                "Fremennik-style wood",
                1,
                "Tropical wood",
                2,
                "Fancy stone",
                3,
                "More styles...",
                4,
            )
        ) {
            1 -> changeStyle(HouseStyle.FREMENNIK_WOOD)
            2 -> changeStyle(HouseStyle.TROPICAL_WOOD)
            3 -> changeStyle(HouseStyle.FANCY_STONE)
            4 -> redecorationPageThree()
        }
    }

    private suspend fun Dialogue.redecorationPageThree() {
        when (
            choice4(
                "Deathly mansion",
                1,
                "Twisted theme",
                2,
                "Hosidius house",
                3,
                "More styles...",
                4,
            )
        ) {
            1 -> changeStyle(HouseStyle.DEATHLY_MANSION)
            2 -> changeStyle(HouseStyle.TWISTED_THEME)
            3 -> changeStyle(HouseStyle.HOSIDIUS_HOUSE)
            4 -> redecorationPageFour()
        }
    }

    private suspend fun Dialogue.redecorationPageFour() {
        when (choice3("Cosy cabin", 1, "Previous styles...", 2, "Never mind.", 3)) {
            1 -> changeStyle(HouseStyle.COSY_CABIN)
            2 -> redecorationPageOne()
        }
    }

    private suspend fun Dialogue.changeStyle(style: HouseStyle) {
        check(style.estateAgentAvailable) { "Unavailable house style was offered: $style" }
        val blueprint = player.loadHouseBlueprint()
        if (blueprint.style == style) {
            chatNpc(neutral, "Your house already uses the ${style.displayName} style.")
            return
        }
        if (player.baseConstructionLvl < style.level) {
            chatNpc(neutral, "You need level ${style.level} Construction to use that style.")
            return
        }
        chatNpc(
            neutral,
            "The ${style.displayName} style costs ${style.price} coins. Shall I redecorate your house?",
        )
        if (choice2("Yes, redecorate it.", 1, "No thanks.", 2) != 1) return
        if (!takeCoins(style.price)) return
        player.saveHouseBlueprint(blueprint.copy(style = style))
        chatNpc(
            happy,
            "Your house now uses the ${style.displayName} style. Your rooms and furniture have been preserved.",
        )
    }

    private suspend fun Dialogue.takeCoins(amount: Int): Boolean {
        if (access.inv.count(COINS) < amount) {
            chatNpc(neutral, "You do not have enough coins in your inventory.")
            return false
        }
        if (access.invDel(access.inv, COINS, count = amount, strict = true).failure) {
            chatNpc(neutral, "I could not collect the payment, so nothing was changed.")
            return false
        }
        return true
    }

    private suspend fun Dialogue.purchaseCape() {
        if (player.baseConstructionLvl < 99) {
            chatNpc(neutral, "Only a master with level 99 Construction may buy that cape.")
            return
        }
        chatNpc(
            neutral,
            "The Construction cape and its hood cost 99,000 coins. Would you like to buy them?",
        )
        if (choice2("Yes, please.", 1, "No thanks.", 2) != 1) return
        if (access.inv.count(COINS) < CONSTRUCTION_CAPE_PRICE) {
            chatNpc(neutral, "You do not have enough coins in your inventory.")
            return
        }
        if (access.inv.freeSpace() < 2) {
            chatNpc(neutral, "You need two free inventory spaces for the cape and hood.")
            return
        }
        if (
            access.invDel(access.inv, COINS, count = CONSTRUCTION_CAPE_PRICE, strict = true).failure
        ) {
            chatNpc(neutral, "I could not collect the payment.")
            return
        }
        val cape = access.invAdd(access.inv, CONSTRUCTION_CAPE, 1)
        val hood = access.invAdd(access.inv, CONSTRUCTION_HOOD, 1)
        if (cape.failure || hood.failure) {
            if (cape.success) access.invDel(access.inv, CONSTRUCTION_CAPE, 1)
            if (hood.success) access.invDel(access.inv, CONSTRUCTION_HOOD, 1)
            check(access.invAdd(access.inv, COINS, CONSTRUCTION_CAPE_PRICE).success)
            chatNpc(neutral, "I could not hand over both items, so your coins were returned.")
            return
        }
        chatNpc(happy, "Excellent. Wear it with pride.")
    }

    private companion object {
        const val COINS = "obj.coins"
        const val CONSTRUCTION_CAPE = "obj.skillcape_construction"
        const val CONSTRUCTION_HOOD = "obj.skillcape_construction_hood"
    }
}
