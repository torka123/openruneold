package org.rsmod.server.app

import dev.openrune.ServerCacheManager
import dev.openrune.cache.filestore.definition.InterfaceType
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import java.nio.file.Path
import org.rsmod.api.area.checker.isInWilderness
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.table.slayer.SlayerMasterTaskRow
import org.rsmod.api.table.slayer.SlayerTaskRow
import org.rsmod.content.areas.wilderness.WildernessNpcAggressionAuditWriter
import org.rsmod.content.areas.wilderness.interactions.WildernessInteractionAuditWriter
import org.rsmod.content.areas.wilderness.interactions.WildernessInteractionScanner
import org.rsmod.game.entity.NpcList
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Generates the audit from the same cache, decoded map, dependency graph, and scripts as
 * production.
 */
public fun main(args: Array<String>) {
    val server = GameServer(skipTypeVerificationOverride = false)
    val injector = server.createInjector()
    server.prepareGame(injector)
    if (args.any { it == "--wilderness-aggression-audit" || it.startsWith("--wilderness-aggression-audit=") }) {
        val argument = args.first { it == "--wilderness-aggression-audit" || it.startsWith("--wilderness-aggression-audit=") }
        val path =
            argument.substringAfter('=', "")
                .takeIf(String::isNotBlank)
                ?.let(Path::of)
                ?: Path.of("docs", "wilderness-npc-aggression-audit.md")
        val written = injector.getInstance(WildernessNpcAggressionAuditWriter::class.java).write(output = path)
        println("Wilderness NPC aggression audit: ${written.toAbsolutePath()}")
    }
    val scanner = injector.getInstance(WildernessInteractionScanner::class.java)
    val writer = injector.getInstance(WildernessInteractionAuditWriter::class.java)
    val audit = scanner.scan()
    val output =
        args.firstOrNull { !it.startsWith("--") }?.let(Path::of) ?: Path.of("docs", "wilderness")
    if ("--slayer-task-probe" in args) {
        SlayerTaskRow.all()
            .sortedBy { it.id }
            .forEach { task ->
                println(
                    "Slayer task probe: id=${task.id}, row=${task.rowId}, " +
                        "lower=${task.nameLowercase}, upper=${task.nameUppercase}, " +
                        "minCombat=${task.minComlevel}, " +
                        "allStats=${task.minStatRequirementAll}, anyStats=${task.minStatRequirementAny}"
                )
            }
    }
    if ("--slayer-master-task-probe" in args) {
        SlayerMasterTaskRow.all().sortedWith(compareBy({ it.masterId }, { it.task.id })).forEach {
            task ->
            println(
                "Slayer master task probe: master=${task.masterId}, task=${task.task.id}, " +
                    "name=${task.task.nameLowercase}, weight=${task.weight}, " +
                    "amount=${task.minAmount}..${task.maxAmount}, row=${task.rowId}"
            )
        }
    }
    if ("--wilderness-npc-probe" in args) {
        val npcs = injector.getInstance(NpcList::class.java)
        val areaChecker = injector.getInstance(org.rsmod.api.area.checker.AreaChecker::class.java)
        val spawns =
            npcs
                .asSequence()
                .filter { it.respawns && it.spawnCoords.isInWilderness(areaChecker) }
                .groupBy { it.visType.internalName }
        spawns.toSortedMap().forEach { (_, group) ->
            val npc = group.first()
            println(
                "Wilderness npc probe: id=${npc.visType.id}, internal=${npc.visType.internalName}, " +
                    "name=${npc.visType.name}, combat=${npc.visType.combatLevel}, " +
                    "size=${npc.visType.size}, slayer=${npc.visType.slayerTaskIds.toList()}, " +
                    "count=${group.size}, samples=${group.take(4).map { it.spawnCoords }}"
            )
        }
    }
    if ("--wilderness-slayer-category-probe" in args) {
        val npcs = injector.getInstance(NpcList::class.java)
        val areaChecker = injector.getInstance(org.rsmod.api.area.checker.AreaChecker::class.java)
        val spawns =
            npcs
                .asSequence()
                .filter {
                    it.respawns &&
                        it.visType.combatLevel > 0 &&
                        it.spawnCoords.isInWilderness(areaChecker)
                }
                .toList()
        val byTask =
            spawns
                .flatMap { npc -> npc.visType.slayerTaskIds.map { taskId -> taskId to npc } }
                .groupBy({ it.first }, { it.second })
        byTask.toSortedMap().forEach { (taskId, taskNpcs) ->
            val task = SlayerTaskRow.all().firstOrNull { it.id == taskId }
            val definitions =
                taskNpcs
                    .distinctBy { it.visType.id }
                    .sortedBy { it.visType.id }
                    .joinToString { "${it.visType.id}:${it.visType.internalName}" }
            println(
                "Wilderness Slayer category probe: task=$taskId, " +
                    "name=${task?.nameLowercase ?: "<missing>"}, " +
                    "definitions=[$definitions], spawns=${taskNpcs.size}"
            )
        }
    }
    val paths = writer.write(audit, output)
    val summary = audit.summary
    println(
        "Wilderness interaction audit complete: revision=${summary.activeRevision}, " +
            "regions=${summary.regionsScanned}, planes=${summary.planesScanned.sorted()}, " +
            "candidates=${summary.candidateObjects}, routes=${summary.routeRecords}, " +
            "registered=${summary.registeredRoutes}, missingHandlers=${summary.missingHandlers}."
    )
    println("Markdown: ${paths.markdown.toAbsolutePath()}")
    println("JSON: ${paths.json.toAbsolutePath()}")
    println("CSV: ${paths.csv.toAbsolutePath()}")
    args
        .filter { it.startsWith("--loc-probe=") }
        .forEach { argument ->
            val id = argument.substringAfter('=').toInt()
            val type = ServerCacheManager.getObject(id)
            println("Loc probe $id (${type?.internalName ?: "<missing>"}): ${type ?: "<missing>"}")
        }
    args
        .filter { it.startsWith("--loc-name-probe=") }
        .forEach { argument ->
            val id = argument.substringAfter('=').toInt()
            val type = ServerCacheManager.getObject(id)
            println(
                "Loc name probe $id: internal=${type?.internalName ?: "<missing>"}, " +
                    "name=${type?.name}, actions=${type?.actions}"
            )
        }
    args
        .filter { it.startsWith("--npc-name-probe=") }
        .forEach { argument ->
            val internal = argument.substringAfter('=')
            val id = internal.asRSCM(RSCMType.NPC)
            val type = ServerCacheManager.getNpc(id)
            println(
                "Npc name probe $internal: id=$id, internal=${type?.internalName ?: "<missing>"}, " +
                    "name=${type?.name}, respawn=${type?.respawnRate}"
            )
        }
    args
        .filter { it.startsWith("--seq-name-probe=") }
        .forEach { argument ->
            val internal = argument.substringAfter('=')
            val id = internal.asRSCM(RSCMType.SEQ)
            val type = ServerCacheManager.getAnim(id)
            println("Seq name probe $internal: id=$id, definition=${type ?: "<missing>"}")
        }
    args
        .filter { it.startsWith("--seq-prefix-probe=") }
        .forEach { argument ->
            val prefix = argument.substringAfter('=').lowercase()
            ServerCacheManager.getAnims()
                .values
                .filter { it.internalName.lowercase().contains(prefix) }
                .sortedBy { it.id }
                .forEach { type ->
                    println(
                        "Seq prefix probe: id=${type.id}, internal=${type.internalName}, definition=$type"
                    )
                }
        }
    args
        .filter { it.startsWith("--rscm-prefix-probe=") }
        .forEach { argument ->
            val values = argument.substringAfter('=').split(',')
            require(values.size == 3) { "Use --rscm-prefix-probe=type,prefix,max-id" }
            val type = RSCMType.valueOf(values[0].uppercase())
            val prefix = values[1].lowercase()
            (0..values[2].toInt()).forEach { id ->
                val internal = runCatching { RSCM.getReverseMapping(type, id) }.getOrNull()
                if (internal != null && internal.lowercase().contains(prefix)) {
                    println("RSCM prefix probe: type=$type, id=$id, internal=$internal")
                }
            }
        }
    args
        .filter { it.startsWith("--npc-prefix-probe=") }
        .forEach { argument ->
            val prefix = argument.substringAfter('=').lowercase()
            ServerCacheManager.getNpcs()
                .values
                .filter { it.internalName.lowercase().contains(prefix) }
                .sortedBy { it.id }
                .forEach { type -> println("Npc prefix probe: id=${type.id}, definition=$type") }
        }
    args
        .filter { it.startsWith("--loc-prefix-probe=") }
        .forEach { argument ->
            val prefix = argument.substringAfter('=').lowercase()
            ServerCacheManager.getObjects()
                .values
                .filter { it.internalName.lowercase().contains(prefix) }
                .sortedBy { it.id }
                .forEach { type -> println("Loc prefix probe: id=${type.id}, definition=$type") }
        }
    args
        .filter { it.startsWith("--proj-prefix-probe=") }
        .forEach { argument ->
            val prefix = argument.substringAfter('=').lowercase()
            ServerCacheManager.getProjectiles()
                .values
                .filter {
                    RSCM.getReverseMapping(RSCMType.PROJANIM, it.id).lowercase().contains(prefix)
                }
                .sortedBy { it.id }
                .forEach { type -> println("Proj prefix probe: id=${type.id}, definition=$type") }
        }
    args
        .filter { it.startsWith("--interface-prefix-probe=") }
        .forEach { argument ->
            val prefix = argument.substringAfter('=').lowercase()
            val interfacesField = ServerCacheManager::class.java.getDeclaredField("interfaces")
            interfacesField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val interfaces = interfacesField.get(ServerCacheManager) as Map<Int, InterfaceType>
            interfaces
                .values
                .filter { it.internalName.lowercase().contains(prefix) }
                .sortedBy { it.id }
                .forEach { println("Interface prefix probe: id=${it.id}, name=${it.internalName}") }
        }
    args
        .filter { it.startsWith("--interface-probe=") }
        .forEach { argument ->
            val id = argument.substringAfter('=').toInt()
            val type = requireNotNull(ServerCacheManager.getInterface(id))
            println("Interface probe: id=$id, name=${type.internalName}, components=${type.components.size}")
            type.components.toSortedMap().forEach { (child, component) ->
                val fields =
                    generateSequence(component.javaClass as Class<*>?) { it.superclass }
                        .flatMap { it.declaredFields.asSequence() }
                        .distinctBy { it.name }
                        .joinToString(" | ") { field ->
                            runCatching {
                                field.isAccessible = true
                                val value = field.get(component)
                                val printable =
                                    when (value) {
                                        is Array<*> -> value.contentDeepToString()
                                        is IntArray -> value.contentToString()
                                        is LongArray -> value.contentToString()
                                        else -> value
                                    }
                                "${field.name}=$printable"
                            }.getOrElse { "${field.name}=<unavailable>" }
                        }
                println(
                    "Interface component: child=$child, packed=${component.packed}, " +
                        "internal=${component.internalName}, class=${component.javaClass.simpleName}, $fields"
                )
            }
        }
    args
        .filter { it.startsWith("--collision-probe=") }
        .forEach { argument ->
            val collision = injector.getInstance(CollisionFlagMap::class.java)
            val values = argument.substringAfter('=').split(',').map(String::toInt)
            require(values.size == 4) { "Use --collision-probe=x,y,plane,radius" }
            printCollisionProbe(
                collision = collision,
                center = CoordGrid(values[0], values[1], values[2]),
                radius = values[3],
            )
        }
    args
        .filter { it.startsWith("--map-probe=") }
        .forEach { argument ->
            val locs = injector.getInstance(LocRepository::class.java)
            val values = argument.substringAfter('=').split(',').map(String::toInt)
            require(values.size == 4) { "Use --map-probe=x,y,plane,radius" }
            val center = CoordGrid(values[0], values[1], values[2])
            val radius = values[3]
            println("Map loc probe around $center (radius=$radius):")
            for (x in center.x - radius..center.x + radius) {
                for (z in center.z - radius..center.z + radius) {
                    val tile = CoordGrid(x, z, center.level)
                    locs
                        .findAll(tile)
                        .filter { loc ->
                            val type = ServerCacheManager.getObject(loc.id)
                            !type?.name.isNullOrBlank() ||
                                (0..4).any { option ->
                                    !type?.actions?.getOpOrNull(option).isNullOrBlank()
                                }
                        }
                        .forEach { loc ->
                            val type = ServerCacheManager.getObject(loc.id)
                            println(
                                "  id=${loc.id}, internal=${type?.internalName ?: "<missing>"}, " +
                                    "name=${type?.name ?: "<missing>"}, actions=${type?.actions}, " +
                                    "tile=${loc.coords}, shape=${loc.shape}, angle=${loc.angle}"
                            )
                        }
                }
            }
        }
    args
        .filter { it.startsWith("--map-prefix-probe=") }
        .forEach { argument ->
            val locs = injector.getInstance(LocRepository::class.java)
            val values = argument.substringAfter('=').split(',')
            require(values.size == 5) { "Use --map-prefix-probe=x,y,plane,radius,prefix" }
            val center = CoordGrid(values[0].toInt(), values[1].toInt(), values[2].toInt())
            val radius = values[3].toInt()
            val prefix = values[4].lowercase()
            println("Map loc prefix probe around $center (radius=$radius, prefix=$prefix):")
            for (x in center.x - radius..center.x + radius) {
                for (z in center.z - radius..center.z + radius) {
                    val tile = CoordGrid(x, z, center.level)
                    locs.findAll(tile).forEach { loc ->
                        val type = ServerCacheManager.getObject(loc.id)
                        val internal = type?.internalName ?: return@forEach
                        if (internal.lowercase().contains(prefix)) {
                            println(
                                "  id=${loc.id}, internal=$internal, name=${type.name}, " +
                                    "actions=${type.actions}, tile=${loc.coords}, shape=${loc.shape}, angle=${loc.angle}"
                            )
                        }
                    }
                }
            }
        }
}

private fun printCollisionProbe(collision: CollisionFlagMap, center: CoordGrid, radius: Int) {
    println("Collision probe around $center ('.'=walkable, '#'=blocked, '?'=unloaded):")
    for (z in center.z + radius downTo center.z - radius) {
        val row = buildString {
            for (x in center.x - radius..center.x + radius) {
                val tile = CoordGrid(x, z, center.level)
                append(
                    when {
                        !collision.isZoneValid(tile) -> '?'
                        collision.isWalkBlocked(tile) -> '#'
                        else -> '.'
                    }
                )
            }
        }
        println("$z $row")
    }
    println("    ${center.x - radius}..${center.x + radius}")
}
