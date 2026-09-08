package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.min
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invDel
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseConstructionLvl
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.content.interfaces.bank.BankTab
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid

internal const val TEMP_CONSTRUCTION_SERVANT_TEST_SPAWN_X = 3093
internal const val TEMP_CONSTRUCTION_SERVANT_TEST_SPAWN_Z = 3492
internal val TEMP_CONSTRUCTION_SERVANT_TEST_SPAWN =
    CoordGrid(TEMP_CONSTRUCTION_SERVANT_TEST_SPAWN_X, TEMP_CONSTRUCTION_SERVANT_TEST_SPAWN_Z, 0)

private const val SERVANT_NPC_LIFETIME_TICKS = 100_000
private const val PAID_SERVICES_PER_WAGE = 8
private const val COINS_ID = 995

internal data class ServantMaterial(val itemId: Int, val displayName: String)

internal data class SawmillMaterial(
    val logsId: Int,
    val planksId: Int,
    val displayName: String,
    val feePerLog: Int,
)

internal val SERVANT_FETCH_MATERIALS =
    listOf(
        ServantMaterial(960, "Planks"),
        ServantMaterial(8778, "Oak planks"),
        ServantMaterial(8780, "Teak planks"),
        ServantMaterial(8782, "Mahogany planks"),
        ServantMaterial(1761, "Soft clay"),
        ServantMaterial(3420, "Limestone bricks"),
        ServantMaterial(2353, "Steel bars"),
        ServantMaterial(8790, "Bolts of cloth"),
        ServantMaterial(8784, "Gold leaves"),
        ServantMaterial(8786, "Marble blocks"),
        ServantMaterial(8788, "Magic stones"),
    )

internal val SERVANT_SAWMILL_MATERIALS =
    listOf(
        SawmillMaterial(1511, 960, "Logs", 100),
        SawmillMaterial(1521, 8778, "Oak logs", 250),
        SawmillMaterial(6333, 8780, "Teak logs", 500),
        SawmillMaterial(6332, 8782, "Mahogany logs", 1_500),
    )

@Singleton
internal class ConstructionServantService
@Inject
constructor(
    private val npcs: NpcRepository,
    private val instances: InstanceManager,
    private val clock: MapClock,
    private val players: PlayerList,
) {
    private val summoned = mutableMapOf<Long, SummonedServant>()

    fun call(access: ProtectedAccess) {
        val player = access.player
        val ownerId = player.uuid ?: return
        val session = instances.sessionForPlayer(player)
        if (session == null || session.key != HOUSE_INSTANCE_KEY || session.owner != ownerId) {
            access.mes("You can only call your servant from inside your own house.")
            return
        }
        val blueprint = player.loadHouseBlueprint()
        val state = blueprint.servant
        if (state == null) {
            access.mes("You do not have a servant. Hire one before using Call Servant.")
            return
        }
        if (!hasTwoCompletedBedrooms(blueprint)) {
            access.mes("Your servant needs two bedrooms, each containing a completed bed.")
            return
        }
        if (state.task != null || state.runtimeState == ServantRuntimeState.ON_ERRAND) {
            access.mes("Your servant is away on an errand and cannot be called yet.")
            return
        }

        val existing = summoned[ownerId]
        if (existing != null && existing.sessionId == session.id && existing.npc.isSlotAssigned) {
            if (existing.npc.coords.chebyshevDistance(player.coords) <= 1) {
                existing.npc.facePlayer(player)
                access.mes("Your servant is already beside you.")
                return
            }
            npcs.del(existing.npc, Int.MAX_VALUE)
            summoned.remove(ownerId)
        } else if (existing != null) {
            if (existing.npc.isSlotAssigned) npcs.del(existing.npc, Int.MAX_VALUE)
            summoned.remove(ownerId)
        }

        val spawn = findServantSpawn(access)
        if (spawn == null) {
            access.mes("There is no reachable space nearby for your servant.")
            return
        }
        val typeId = state.type.npcGameval.asRSCM(RSCMType.NPC)
        val type =
            requireNotNull(ServerCacheManager.getNpc(typeId)) {
                "Missing revision-240 servant NPC ${state.type.npcGameval}"
            }
        val npc =
            Npc(type, spawn).also {
                it.mode = NpcMode.None
                it.respawns = false
                it.assignSpawnOwner(player, clock.cycle)
            }
        npcs.add(npc, SERVANT_NPC_LIFETIME_TICKS)
        instances.attachNpc(session.id, npc)
        npc.facePlayer(player)
        summoned[ownerId] = SummonedServant(session.id, npc)
        saveServant(player, state.copy(runtimeState = ServantRuntimeState.FOLLOWING_OR_SUMMONED))
        access.mes("Your ${state.type.displayName} appears beside you.")
    }

    suspend fun talk(access: ProtectedAccess, npc: Npc, definition: ConstructionServant) {
        val ownerId = access.player.uuid ?: return
        val session = instances.sessionForPlayer(access.player)
        val insideOwnHouse =
            session != null &&
                session.key == HOUSE_INSTANCE_KEY &&
                session.owner == ownerId &&
                instances.instanceForNpc(npc) == session.id
        access.startDialogue(npc) {
            if (insideOwnHouse) {
                servantDialogue(definition)
            } else {
                hiringDialogue(definition)
            }
        }
    }

    fun reconcile(access: ProtectedAccess) {
        val player = access.player
        val state = player.loadHouseBlueprint().servant ?: return
        val task = state.task
        if (task == null) {
            if (state.runtimeState != ServantRuntimeState.IDLE) {
                saveServant(player, state.copy(runtimeState = ServantRuntimeState.IDLE))
            }
            return
        }
        val recovery = if (task.status == ServantTaskStatus.COMPLETED) task.output else task.input
        val withCoins = recovery.toMutableMap()
        if (task.status != ServantTaskStatus.COMPLETED && task.coinPayment > 0) {
            withCoins[COINS_ID] = (withCoins[COINS_ID] ?: 0) + task.coinPayment
        }
        if (addAllToBank(access, withCoins)) {
            saveServant(
                player,
                state.copy(
                    runtimeState = ServantRuntimeState.IDLE,
                    task = null,
                    escrow = mutableMapOf(),
                ),
            )
            access.mes("Your interrupted servant errand was safely returned to your bank.")
        } else {
            access.mes("Your servant is holding an interrupted errand until your bank has room.")
        }
    }

    private suspend fun Dialogue.hiringDialogue(definition: ConstructionServant) {
        val blueprint = player.loadHouseBlueprint()
        if (!player.hasConstructionHouse()) {
            chatNpc(neutral, "You need to own a house before you can employ me.")
            return
        }
        if (player.baseConstructionLvl < definition.level) {
            chatNpc(
                neutral,
                "You need level ${definition.level} Construction before you can employ me.",
            )
            return
        }
        if (!hasTwoCompletedBedrooms(blueprint)) {
            chatNpc(
                neutral,
                "I require two bedrooms, with one completed bed in each, before I can work for you.",
            )
            return
        }
        val current = blueprint.servant
        if (current?.type == definition) {
            chatNpc(neutral, "I already work for you. Call me from your House Options menu.")
            return
        }
        if (current != null) {
            if (current.task != null || current.escrow.isNotEmpty()) {
                chatNpc(
                    neutral,
                    "Your current servant is holding an errand, so I cannot replace them.",
                )
                return
            }
            chatNpc(
                neutral,
                "You already employ ${current.type.displayName}. Do you want to replace them with me?",
            )
            if (choice2("Yes, replace them.", 1, "No.", 2) != 1) return
        } else {
            chatNpc(
                neutral,
                "I charge ${definition.wage} coins after every eight completed errands. Employ me?",
            )
            if (choice2("Yes, you're hired.", 1, "No thanks.", 2) != 1) return
        }
        saveServant(player, ServantState(definition))
        chatNpc(happy, "Very good. Use Call Servant while inside your house when you need me.")
    }

    private suspend fun Dialogue.servantDialogue(definition: ConstructionServant) {
        val state = player.loadHouseBlueprint().servant
        if (state == null || state.type != definition) {
            chatNpc(neutral, "I am not employed by you.")
            return
        }
        if (state.task != null) {
            chatNpc(neutral, "I am still handling your current errand.")
            return
        }
        if (state.paymentDue) {
            paymentDialogue(state)
            return
        }
        chatNpc(neutral, "What would you like me to do?")
        when (
            choice4(
                "Fetch Construction materials.",
                1,
                "Take an item to the bank.",
                2,
                "Un-note an item.",
                3,
                "More services...",
                4,
            )
        ) {
            1 -> fetchPageOne(state)
            2 -> depositDialogue(state)
            3 -> unnoteDialogue(state)
            4 -> moreServices(state)
        }
    }

    private suspend fun Dialogue.moreServices(state: ServantState) {
        when (
            choice3(
                "Take logs to the sawmill.",
                1,
                "Repeat the last fetch request.",
                2,
                "Never mind.",
                3,
            )
        ) {
            1 -> sawmillDialogue(state)
            2 -> repeatLastFetch(state)
        }
    }

    private suspend fun Dialogue.fetchPageOne(state: ServantState) {
        when (choice4("Planks", 1, "Oak planks", 2, "Teak planks", 3, "More materials...", 4)) {
            1 -> fetchQuantity(state, SERVANT_FETCH_MATERIALS[0])
            2 -> fetchQuantity(state, SERVANT_FETCH_MATERIALS[1])
            3 -> fetchQuantity(state, SERVANT_FETCH_MATERIALS[2])
            4 -> fetchPageTwo(state)
        }
    }

    private suspend fun Dialogue.fetchPageTwo(state: ServantState) {
        when (
            choice4(
                "Mahogany planks",
                1,
                "Soft clay",
                2,
                "Limestone bricks",
                3,
                "More materials...",
                4,
            )
        ) {
            1 -> fetchQuantity(state, SERVANT_FETCH_MATERIALS[3])
            2 -> fetchQuantity(state, SERVANT_FETCH_MATERIALS[4])
            3 -> fetchQuantity(state, SERVANT_FETCH_MATERIALS[5])
            4 -> fetchPageThree(state)
        }
    }

    private suspend fun Dialogue.fetchPageThree(state: ServantState) {
        when (
            choice4("Steel bars", 1, "Bolts of cloth", 2, "Gold leaves", 3, "More materials...", 4)
        ) {
            1 -> fetchQuantity(state, SERVANT_FETCH_MATERIALS[6])
            2 -> fetchQuantity(state, SERVANT_FETCH_MATERIALS[7])
            3 -> fetchQuantity(state, SERVANT_FETCH_MATERIALS[8])
            4 -> fetchPageFour(state)
        }
    }

    private suspend fun Dialogue.fetchPageFour(state: ServantState) {
        when (choice3("Marble blocks", 1, "Magic stones", 2, "Never mind.", 3)) {
            1 -> fetchQuantity(state, SERVANT_FETCH_MATERIALS[9])
            2 -> fetchQuantity(state, SERVANT_FETCH_MATERIALS[10])
        }
    }

    private suspend fun Dialogue.fetchQuantity(state: ServantState, material: ServantMaterial) {
        val amount = access.countDialog("How many ${material.displayName.lowercase()}?")
        if (amount <= 0) return
        dispatchFetch(access, state, material, amount)
    }

    private suspend fun Dialogue.repeatLastFetch(state: ServantState) {
        val parts = state.lastRequest?.split(':')
        if (parts?.size != 3 || parts[0] != "FETCH") {
            chatNpc(neutral, "You have no previous fetch request to repeat.")
            return
        }
        val itemId = parts[1].toIntOrNull()
        val amount = parts[2].toIntOrNull()
        val material = SERVANT_FETCH_MATERIALS.firstOrNull { it.itemId == itemId }
        if (material == null || amount == null || amount <= 0) {
            chatNpc(neutral, "Your previous request is no longer valid.")
            return
        }
        dispatchFetch(access, state, material, amount)
    }

    private suspend fun Dialogue.depositDialogue(state: ServantState) {
        val item = access.objDialog("Select an item from your inventory to bank:")
        val available = access.inv.count(item.internalName)
        if (available <= 0) {
            chatNpc(neutral, "You do not have that item in your inventory.")
            return
        }
        val amount = access.countDialog("How many? (Maximum ${state.type.capacity})")
        if (amount <= 0) return
        dispatchDeposit(access, state, item, min(amount, available))
    }

    private suspend fun Dialogue.unnoteDialogue(state: ServantState) {
        val noted = access.objDialog("Select a noted item from your inventory:")
        if (!noted.isCert) {
            chatNpc(neutral, "That is not a noted item.")
            return
        }
        val unnoted = requireNotNull(ServerCacheManager.getItem(noted.certlink))
        val name = unnoted.lowercaseName
        if (name.contains("bone")) {
            chatNpc(neutral, "I cannot un-note bones.")
            return
        }
        val available = access.inv.count(noted.internalName)
        val maxBySpace = access.inv.freeSpace() + 1
        val max = min(state.type.capacity, min(available, maxBySpace))
        if (max <= 0) {
            chatNpc(neutral, "You do not have enough inventory space.")
            return
        }
        val amount = access.countDialog("How many? (Maximum $max)").coerceIn(0, max)
        if (amount <= 0) return
        dispatchTransform(access, state, ServantTaskType.UNNOTE, noted.id, unnoted.id, amount, 0)
    }

    private suspend fun Dialogue.sawmillDialogue(state: ServantState) {
        if (!state.type.sawmill) {
            chatNpc(neutral, "I am not able to visit the sawmill.")
            return
        }
        val material =
            when (choice4("Logs", 1, "Oak logs", 2, "Teak logs", 3, "Mahogany logs", 4)) {
                1 -> SERVANT_SAWMILL_MATERIALS[0]
                2 -> SERVANT_SAWMILL_MATERIALS[1]
                3 -> SERVANT_SAWMILL_MATERIALS[2]
                4 -> SERVANT_SAWMILL_MATERIALS[3]
                else -> return
            }
        val available = access.inv.count(itemInternal(material.logsId))
        val affordable = access.inv.count(itemInternal(COINS_ID)) / material.feePerLog
        val max = min(state.type.capacity, min(available, affordable))
        if (max <= 0) {
            chatNpc(neutral, "You need logs and enough coins for the sawmill fee.")
            return
        }
        val amount = access.countDialog("How many? (Maximum $max)").coerceIn(0, max)
        if (amount <= 0) return
        dispatchTransform(
            access,
            state,
            ServantTaskType.SAWMILL,
            material.logsId,
            material.planksId,
            amount,
            amount * material.feePerLog,
        )
    }

    private suspend fun Dialogue.paymentDialogue(state: ServantState) {
        chatNpc(
            neutral,
            "My wage of ${state.type.wage} coins is due before I can run another errand.",
        )
        if (choice2("Pay the wage.", 1, "Not now.", 2) != 1) return
        if (access.inv.count(itemInternal(COINS_ID)) < state.type.wage) {
            chatNpc(neutral, "You do not have enough coins in your inventory.")
            return
        }
        if (
            access
                .invDel(access.inv, itemInternal(COINS_ID), state.type.wage, strict = true)
                .failure
        ) {
            chatNpc(neutral, "I could not collect the wage.")
            return
        }
        saveServant(
            player,
            state.copy(
                completedServices = 0,
                paymentDue = false,
                runtimeState = ServantRuntimeState.IDLE,
            ),
        )
        chatNpc(happy, "Thank you. I am ready for more work.")
    }

    private suspend fun dispatchFetch(
        access: ProtectedAccess,
        state: ServantState,
        material: ServantMaterial,
        requested: Int,
    ) {
        val available = access.bank.count(itemInternal(material.itemId))
        val amount =
            min(requested, min(state.type.capacity, min(available, access.inv.freeSpace())))
        if (amount <= 0) {
            access.mes("Your bank has none available, or your inventory has no delivery space.")
            return
        }
        if (
            access.invDel(access.bank, itemInternal(material.itemId), amount, strict = true).failure
        ) {
            access.mes("Your servant could not reserve those materials from your bank.")
            return
        }
        val task =
            newTask(
                access.player,
                state,
                ServantTaskType.FETCH_FROM_BANK,
                material.itemId,
                material.itemId,
                amount,
                0,
            )
        val updated =
            state.copy(
                runtimeState = ServantRuntimeState.ON_ERRAND,
                task = task,
                lastRequest = "FETCH:${material.itemId}:$amount",
                escrow = mutableMapOf(material.itemId to amount),
            )
        saveServant(access.player, updated)
        dispatchAndDeliver(access, updated, deliverToBank = false)
    }

    private suspend fun dispatchDeposit(
        access: ProtectedAccess,
        state: ServantState,
        item: ItemServerType,
        requested: Int,
    ) {
        val amount = min(requested, state.type.capacity)
        if (
            amount <= 0 ||
                access.invDel(access.inv, item.internalName, amount, strict = true).failure
        ) {
            access.mes("Your servant could not take those items.")
            return
        }
        val task =
            newTask(
                access.player,
                state,
                ServantTaskType.DEPOSIT_TO_BANK,
                item.id,
                item.id,
                amount,
                0,
            )
        val updated =
            state.copy(
                runtimeState = ServantRuntimeState.ON_ERRAND,
                task = task,
                escrow = mutableMapOf(item.id to amount),
            )
        saveServant(access.player, updated)
        dispatchAndDeliver(access, updated, deliverToBank = true)
    }

    private suspend fun dispatchTransform(
        access: ProtectedAccess,
        state: ServantState,
        type: ServantTaskType,
        inputId: Int,
        outputId: Int,
        amount: Int,
        coinPayment: Int,
    ) {
        val removal =
            access.player.invTransaction(access.inv) {
                val inventory = select(access.inv)
                delete(inventory, inputId, amount, strict = true)
                if (coinPayment > 0) delete(inventory, COINS_ID, coinPayment, strict = true)
            }
        if (removal.failure) {
            access.mes("Your servant could not reserve the items and payment for that errand.")
            return
        }
        val task = newTask(access.player, state, type, inputId, outputId, amount, coinPayment)
        val updated =
            state.copy(
                runtimeState = ServantRuntimeState.ON_ERRAND,
                task = task,
                escrow = mutableMapOf(inputId to amount),
            )
        saveServant(access.player, updated)
        dispatchAndDeliver(access, updated, deliverToBank = false)
    }

    private suspend fun dispatchAndDeliver(
        access: ProtectedAccess,
        state: ServantState,
        deliverToBank: Boolean,
    ) {
        val task = requireNotNull(state.task)
        dismiss(access.player)
        access.mes(
            "Your ${state.type.displayName} leaves on the errand and will return in ${state.type.tripSeconds} seconds."
        )
        access.delay(servantTripTicks(state.type.tripSeconds))
        val current = access.player.loadHouseBlueprint().servant ?: return
        if (current.task?.id != task.id || current.task.status != ServantTaskStatus.DISPATCHED)
            return
        val completedTask = task.copy(status = ServantTaskStatus.COMPLETED)
        val completed =
            current.copy(runtimeState = ServantRuntimeState.RETURNING, task = completedTask)
        saveServant(access.player, completed)

        val stillInOwnHouse =
            instances.sessionForPlayer(access.player)?.let { session ->
                session.key == HOUSE_INSTANCE_KEY && session.owner == access.player.uuid
            } == true
        val delivered =
            if (deliverToBank || !stillInOwnHouse) {
                addAllToBank(access, completedTask.output)
            } else {
                addAllToInventory(access, completedTask.output) ||
                    addAllToBank(access, completedTask.output)
            }
        if (!delivered) {
            saveServant(
                access.player,
                completed.copy(runtimeState = ServantRuntimeState.HOLDING_ITEMS),
            )
            access.mes(
                "Your servant is holding the completed errand until storage space is available."
            )
            return
        }
        finishPaidService(access.player, completed)
        if (stillInOwnHouse) call(access)
        access.mes("Your ${state.type.displayName} has completed the errand.")
    }

    private fun newTask(
        player: Player,
        state: ServantState,
        type: ServantTaskType,
        inputId: Int,
        outputId: Int,
        amount: Int,
        coinPayment: Int,
    ): ServantTaskRecord {
        val tripTicks = servantTripTicks(state.type.tripSeconds)
        return ServantTaskRecord(
            id = "${player.uuid}:${clock.cycle}:${type.name}",
            servant = state.type,
            type = type,
            input = mutableMapOf(inputId to amount),
            output = mutableMapOf(outputId to amount),
            coinPayment = coinPayment,
            dispatchCycle = clock.cycle,
            completionCycle = clock.cycle + tripTicks,
        )
    }

    private fun finishPaidService(player: Player, state: ServantState) {
        val count = (state.completedServices + 1).coerceAtMost(PAID_SERVICES_PER_WAGE)
        val due = count >= PAID_SERVICES_PER_WAGE
        saveServant(
            player,
            state.copy(
                completedServices = count,
                paymentDue = due,
                runtimeState =
                    if (due) ServantRuntimeState.AWAITING_PAYMENT else ServantRuntimeState.IDLE,
                task = null,
                escrow = mutableMapOf(),
            ),
        )
    }

    private fun findServantSpawn(access: ProtectedAccess): CoordGrid? {
        repeat(20) {
            val candidate = access.mapFindSquareLineOfWalk(access.coords, 1, 3) ?: return@repeat
            if (npcs.findAll(candidate).any()) return@repeat
            if (players.any { it.isSlotAssigned && it.coords == candidate }) return@repeat
            return candidate
        }
        return null
    }

    private fun dismiss(player: Player) {
        val ownerId = player.uuid ?: return
        val active = summoned.remove(ownerId) ?: return
        if (active.npc.isSlotAssigned) npcs.del(active.npc, Int.MAX_VALUE)
    }

    private fun hasTwoCompletedBedrooms(blueprint: HouseBlueprint): Boolean =
        blueprint.rooms
            .asSequence()
            .filter { it.room == "BEDROOM" }
            .filter { blueprint.furniture[furnitureKey(it, "BED")] != null }
            .distinctBy { Triple(it.zoneX, it.zoneZ, it.level) }
            .take(2)
            .count() == 2

    private fun saveServant(player: Player, state: ServantState?) {
        val blueprint = player.loadHouseBlueprint()
        player.saveHouseBlueprint(blueprint.copy(servant = state))
    }

    private fun itemInternal(itemId: Int): String =
        requireNotNull(ServerCacheManager.getItem(itemId)) { "Missing revision-240 item $itemId" }
            .internalName

    private fun addAllToInventory(access: ProtectedAccess, items: Map<Int, Int>): Boolean {
        if (items.isEmpty()) return true
        return access.player
            .invTransaction(access.inv) {
                val inventory = select(access.inv)
                items.forEach { (itemId, amount) -> add(inventory, itemId, amount) }
            }
            .success
    }

    private fun addAllToBank(access: ProtectedAccess, items: Map<Int, Int>): Boolean {
        if (items.isEmpty()) return true
        val result =
            access.player.invTransaction(access.bank) {
                val bank = select(access.bank)
                items.forEach { (itemId, amount) -> add(bank, itemId, amount) }
            }
        if (result.success) BankTab.repairLayout(access.player, access.bank)
        return result.success
    }

    private data class SummonedServant(
        val sessionId: org.rsmod.api.instances.InstanceId,
        val npc: Npc,
    )
}
