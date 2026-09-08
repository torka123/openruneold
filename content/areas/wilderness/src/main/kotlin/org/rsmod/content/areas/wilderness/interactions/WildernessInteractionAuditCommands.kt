package org.rsmod.content.areas.wilderness.interactions

import dev.openrune.ServerCacheManager
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import java.util.Collections
import java.util.IdentityHashMap
import java.util.WeakHashMap
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

public object WildernessInteractionTestMode {
    private val players = Collections.newSetFromMap(WeakHashMap<Player, Boolean>())

    public fun enable(player: Player) {
        synchronized(players) { players += player }
    }

    public fun disable(player: Player) {
        synchronized(players) { players -= player }
    }

    public fun isEnabled(player: Player): Boolean = synchronized(players) { player in players }
}

public class WildernessInteractionAuditCommands
@Inject
constructor(
    private val scanner: WildernessInteractionScanner,
    private val writer: WildernessInteractionAuditWriter,
    private val protectedAccess: ProtectedAccessLauncher,
    private val collision: CollisionFlagMap,
) : PluginScript() {
    private var latestAudit: WildernessInteractionAudit? = null
    private val sessions = IdentityHashMap<Player, TestSession>()

    override fun ScriptContext.startup() {
        onCommand("wildernessinteractionaudit") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Scan and validate all Wilderness environmental interactions"
            invalidArgs = "Use as ::wildernessinteractionaudit"
            cheat { if (args.isEmpty()) runAudit(this) }
        }
        onCommand("wildyinteraction") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Show one Wilderness interaction audit record"
            invalidArgs = "Use as ::wildyinteraction <auditIndex>"
            cheat { showRecord(this) }
        }
        onCommand("testwildyinteraction") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Stage beside a real Wilderness object for reward-free testing"
            invalidArgs = "Use as ::testwildyinteraction <auditIndex>"
            cheat { stageRecord(this, roundTrip = false) }
        }
        onCommand("nextwildyinteraction") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Stage the next unresolved Wilderness interaction"
            invalidArgs = "Use as ::nextwildyinteraction"
            cheat { if (args.isEmpty()) stageNext(this) }
        }
        onCommand("wildyroundtrip") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Stage a Wilderness source and display its reverse-route evidence"
            invalidArgs = "Use as ::wildyroundtrip <auditIndex>"
            cheat { stageRecord(this, roundTrip = true) }
        }
        onCommand("wildyinteractionreset") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Clear Wilderness audit test state and return to the saved tile"
            invalidArgs = "Use as ::wildyinteractionreset"
            cheat { if (args.isEmpty()) reset(this) }
        }
    }

    private fun runAudit(cheat: Cheat) =
        with(cheat) {
            val audit = scanner.scan()
            val paths = writer.write(audit)
            latestAudit = audit
            val summary = audit.summary
            player.mes(
                "Wilderness interaction audit: ${summary.regionsScanned} regions, " +
                    "planes ${summary.planesScanned.sorted().joinToString()}, " +
                    "${summary.candidateObjects} candidate objects, ${summary.routeRecords} routes."
            )
            player.mes(
                "Registered=${summary.registeredRoutes}, pass=${summary.passingRoutes}, " +
                    "missing handlers=${summary.missingHandlers}, wrong destinations=${summary.wrongDestinations}."
            )
            player.mes(
                "Missing reverse=${summary.missingReverseRoutes}, intentional one-way=${summary.intentionalOneWayRoutes}, " +
                    "invalid destinations=${summary.invalidDestinations}, missing cache=${summary.missingCacheContent}."
            )
            player.mes("Handler layers requiring review=${summary.conflictingRegistrations}.")
            player.mes("Audit files: ${paths.markdown}; ${paths.json}; ${paths.csv}")
        }

    private fun showRecord(cheat: Cheat) =
        with(cheat) {
            val record = resolveRecord(args.firstOrNull())
            if (record == null) {
                player.mes("Unknown audit index. Run ::wildernessinteractionaudit first.")
                return@with
            }
            player.mes(
                "#${record.auditIndex} ${record.routeName}: object=${record.sourceObjectName} " +
                    "(${record.sourceObjectId}), action=${record.sourceAction}."
            )
            player.mes(
                "Source=${record.sourceX},${record.sourceY},${record.sourcePlane} region=${record.sourceRegionId}; " +
                    "destination=${destinationText(record)}."
            )
            player.mes(
                "Reverse=${record.reverseObjectId ?: "unverified"}/${record.reverseAction.ifBlank { "unverified" }}; " +
                    "direction=${record.routeDirectionality}; handler=${record.existingHandlerFile.ifBlank { "none" }}."
            )
            player.mes(
                "Requirements: skill=${record.skillRequirement.ifBlank { "none recorded" }}, " +
                    "diary=${record.diaryRequirement.ifBlank { "none recorded" }}, " +
                    "quest=${record.questRequirement.ifBlank { "none recorded" }}, " +
                    "item=${record.itemRequirement.ifBlank { "none recorded" }}, fee=${record.fee.ifBlank { "none recorded" }}."
            )
            player.mes(
                "Restrictions: combat=${record.combatRestriction.ifBlank { "unverified" }}, " +
                    "freeze=${record.freezeRestriction.ifBlank { "unverified" }}, " +
                    "teleblock=${record.teleblockRestriction.ifBlank { "unverified" }}."
            )
            player.mes(
                "Status=${record.auditStatus}; automated=${record.automatedTestStatus}; manual=${record.manualTestStatus}."
            )
        }

    private fun stageRecord(cheat: Cheat, roundTrip: Boolean) =
        with(cheat) {
            val record = resolveRecord(args.firstOrNull())
            if (record == null) {
                player.mes("Unknown audit index. Run ::wildernessinteractionaudit first.")
                return@with
            }
            stage(player, record, roundTrip)
        }

    private fun stageNext(cheat: Cheat) =
        with(cheat) {
            val audit = audit()
            val current = sessions[player]?.auditIndex ?: 0
            val next =
                audit.records.firstOrNull {
                    it.auditIndex > current &&
                        (it.auditStatus != "PASS" || it.manualTestStatus != "PASS")
                }
                    ?: audit.records.firstOrNull {
                        it.auditStatus != "PASS" || it.manualTestStatus != "PASS"
                    }
            if (next == null) {
                player.mes("No unresolved Wilderness interaction records remain.")
                return@with
            }
            stage(player, next, roundTrip = false)
        }

    private fun stage(player: Player, record: WildernessInteractionRecord, roundTrip: Boolean) {
        val source = CoordGrid(record.sourceX, record.sourceY, record.sourcePlane)
        val staging = findStagingTile(source, record.sourceObjectId, record.sourceObjectOrientation)
        if (staging == null) {
            player.mes("No loaded, walkable tile was found beside audit #${record.auditIndex}.")
            return
        }
        val original = sessions[player]?.original ?: player.coords
        sessions[player] = TestSession(original, record.auditIndex, roundTrip)
        WildernessInteractionTestMode.enable(player)
        val launched =
            protectedAccess.launch(
                player,
                "Finish your current action before starting the audit test.",
            ) {
                telejump(staging, TeleportType.Exempt)
            }
        if (!launched) {
            return
        }
        player.mes(
            "Audit #${record.auditIndex}: click the real '${record.sourceAction}' option on " +
                "${record.sourceObjectName} (${record.sourceObjectId})."
        )
        player.mes(
            "Test mode suppresses supported fees/rewards. Use ::wildyinteractionreset to return."
        )
        if (roundTrip) {
            player.mes(
                if (record.reverseObjectId == null) {
                    "Reverse route is not yet verified; do not infer a round trip from this record."
                } else {
                    "After arrival, click ${record.reverseAction} on object ${record.reverseObjectId}; " +
                        "expected reverse source=${record.reverseSourceTile}."
                }
            )
        }
    }

    private fun reset(cheat: Cheat) =
        with(cheat) {
            val session = sessions.remove(player)
            WildernessInteractionTestMode.disable(player)
            if (session == null) {
                player.mes("No Wilderness interaction test session is active.")
                return@with
            }
            protectedAccess.launch(
                player,
                "Finish your current action before resetting the audit test.",
            ) {
                telejump(session.original, TeleportType.Exempt)
            }
            player.mes(
                "Wilderness interaction test mode cleared; returning to ${session.original.toConventionalString()}."
            )
        }

    private fun resolveRecord(token: String?): WildernessInteractionRecord? {
        val index = token?.toIntOrNull() ?: return null
        return audit().records.firstOrNull { it.auditIndex == index }
    }

    private fun audit(): WildernessInteractionAudit =
        latestAudit ?: scanner.scan().also { latestAudit = it }

    private fun findStagingTile(source: CoordGrid, objectId: Int, orientation: Int): CoordGrid? {
        val type = ServerCacheManager.getObject(objectId)
        var width = type?.width ?: 1
        var length = type?.length ?: 1
        if (orientation and 1 == 1) {
            val swap = width
            width = length
            length = swap
        }
        val candidates = buildList {
            for (x in source.x - 1..source.x + width) {
                add(CoordGrid(x, source.z - 1, source.level))
                add(CoordGrid(x, source.z + length, source.level))
            }
            for (z in source.z until source.z + length) {
                add(CoordGrid(source.x - 1, z, source.level))
                add(CoordGrid(source.x + width, z, source.level))
            }
        }
        return candidates
            .distinct()
            .filter { collision.isZoneValid(it) && !collision.isWalkBlocked(it) }
            .minByOrNull { it.chebyshevDistance(source) }
    }

    private fun destinationText(record: WildernessInteractionRecord): String =
        if (
            record.destinationX == null ||
                record.destinationY == null ||
                record.destinationPlane == null
        ) {
            "unverified"
        } else {
            "${record.destinationX},${record.destinationY},${record.destinationPlane} (${record.destinationArea})"
        }

    private data class TestSession(
        val original: CoordGrid,
        val auditIndex: Int,
        val roundTrip: Boolean,
    )
}
