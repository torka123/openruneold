package dev.openrune

import com.displee.cache.CacheLibrary
import dev.openrune.cache.CONFIGS
import dev.openrune.codec.osrs.impl.ObjectServerCodec
import dev.openrune.definition.codec.ObjectCodec
import dev.openrune.filesystem.Cache
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Adds party management to the Theatre entrance without changing any unrelated cache files. */
public fun main() {
    val root = Paths.get(System.getProperty("user.dir")).normalize()
    val live = root.resolve(Paths.get(".data", "cache", "LIVE"))
    val server = root.resolve(Paths.get(".data", "cache", "SERVER"))
    patchTobEntranceOption(live, server)
}

/** Applies and verifies the option after either cache is rebuilt. */
public fun patchTobEntranceOption(live: Path, server: Path) {
    require(Files.isDirectory(live)) { "Required LIVE cache not found at $live" }
    require(Files.isDirectory(server)) { "Required SERVER cache not found at $server" }
    patchLive(live)
    patchServer(server)
    verifyLive(live)
    verifyServer(server)
    println(
        "Added '${TobEntranceOptionIds.PartyBoard}' to Theatre entrance " +
            "${TobEntranceOptionIds.Object} in LIVE and SERVER."
    )
}

private fun patchLive(path: Path) {
    val encoded: ByteArray
    val cache = Cache.load(path)
    try {
        val codec = ObjectCodec(TobEntranceOptionIds.Revision)
        val bytes =
            requireNotNull(
                cache.data(
                    CONFIGS,
                    TobEntranceOptionIds.LiveObjectArchive,
                    TobEntranceOptionIds.Object,
                )
            ) {
                "LIVE Theatre entrance definition is missing"
            }
        val type = codec.loadData(TobEntranceOptionIds.Object, bytes)
        type.actions.setOp(TobEntranceOptionIds.PartyBoardSlot, TobEntranceOptionIds.PartyBoard)
        encoded = encode { with(codec) { it.encode(type) } }
    } finally {
        cache.close()
    }
    write(path, TobEntranceOptionIds.LiveObjectArchive, encoded)
}

private fun patchServer(path: Path) {
    val encoded: ByteArray
    val cache = Cache.load(path)
    try {
        val codec = ObjectServerCodec(TobEntranceOptionIds.Revision)
        val bytes =
            requireNotNull(
                cache.data(
                    CONFIGS,
                    TobEntranceOptionIds.ServerObjectArchive,
                    TobEntranceOptionIds.Object,
                )
            ) {
                "SERVER Theatre entrance definition is missing"
            }
        val type = codec.loadData(TobEntranceOptionIds.Object, bytes)
        type.actions.setOp(TobEntranceOptionIds.PartyBoardSlot, TobEntranceOptionIds.PartyBoard)
        encoded = encode { with(codec) { it.encode(type) } }
    } finally {
        cache.close()
    }
    write(path, TobEntranceOptionIds.ServerObjectArchive, encoded)
}

private fun verifyLive(path: Path) {
    val cache = Cache.load(path)
    try {
        val bytes =
            requireNotNull(
                cache.data(
                    CONFIGS,
                    TobEntranceOptionIds.LiveObjectArchive,
                    TobEntranceOptionIds.Object,
                )
            )
        val type =
            ObjectCodec(TobEntranceOptionIds.Revision).loadData(TobEntranceOptionIds.Object, bytes)
        check(type.actions.getOpOrNull(0) == "Enter") { "LIVE entrance lost its Enter option" }
        check(
            type.actions.getOpOrNull(TobEntranceOptionIds.PartyBoardSlot) ==
                TobEntranceOptionIds.PartyBoard
        )
    } finally {
        cache.close()
    }
}

private fun verifyServer(path: Path) {
    val cache = Cache.load(path)
    try {
        val bytes =
            requireNotNull(
                cache.data(
                    CONFIGS,
                    TobEntranceOptionIds.ServerObjectArchive,
                    TobEntranceOptionIds.Object,
                )
            )
        val type =
            ObjectServerCodec(TobEntranceOptionIds.Revision)
                .loadData(TobEntranceOptionIds.Object, bytes)
        check(type.actions.getOpOrNull(0) == "Enter") { "SERVER entrance lost its Enter option" }
        check(
            type.actions.getOpOrNull(TobEntranceOptionIds.PartyBoardSlot) ==
                TobEntranceOptionIds.PartyBoard
        )
    } finally {
        cache.close()
    }
}

private inline fun encode(block: (ByteBuf) -> Unit): ByteArray {
    val buffer = Unpooled.buffer()
    return try {
        block(buffer)
        ByteArray(buffer.readableBytes()).also(buffer::readBytes)
    } finally {
        buffer.release()
    }
}

private fun write(path: Path, archive: Int, bytes: ByteArray) {
    val cache = CacheLibrary(path.toString())
    try {
        cache.put(CONFIGS, archive, TobEntranceOptionIds.Object, bytes)
        check(cache.index(CONFIGS).update()) {
            "Cache index update failed for object ${TobEntranceOptionIds.Object} in $path"
        }
    } finally {
        cache.close()
    }
}

private object TobEntranceOptionIds {
    const val Revision = 240
    const val Object = 32653
    const val LiveObjectArchive = 6
    const val ServerObjectArchive = 55
    const val PartyBoardSlot = 1
    const val PartyBoard = "Party board"
}
