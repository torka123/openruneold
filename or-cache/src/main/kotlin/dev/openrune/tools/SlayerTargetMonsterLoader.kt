package dev.openrune.tools

import dev.openrune.toml.TomlMapper
import dev.openrune.toml.rsconfig.decodeRuneScape
import dev.openrune.toml.rsconfig.decodeRuneScapeBlocks
import dev.openrune.types.SlayerTargetMonster
import java.io.File
import kotlin.reflect.typeOf

object SlayerTargetMonsterLoader {
    private const val FILE_NAME = "slayer_target_monsters.toml"

    fun loadNpcSlayerTaskIds(directory: File, mapper: TomlMapper): Map<Int, IntArray> {
        val file = File(directory, FILE_NAME)
        if (!file.exists()) return emptyMap()

        val blocks = mapper.decodeRuneScapeBlocks(file.toPath())
        val entries =
            blocks
                .asSequence()
                .filter { it.name == "slayer_target_monster" }
                .map {
                    mapper.decodeRuneScape(typeOf<SlayerTargetMonster>(), it.map.properties)
                        as SlayerTargetMonster
                }
                .toList()
        return mapTaskIdsByNpc(entries)
    }

    internal fun mapTaskIdsByNpc(entries: Iterable<SlayerTargetMonster>): Map<Int, IntArray> {
        val taskIdsByNpc = linkedMapOf<Int, LinkedHashSet<Int>>()
        for (entry in entries) {
            for (npcRef in entry.targets) {
                taskIdsByNpc.getOrPut(npcRef, ::LinkedHashSet).add(entry.targetId)
            }
        }
        return taskIdsByNpc.mapValues { (_, taskIds) -> taskIds.toIntArray() }
    }
}
