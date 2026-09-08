package dev.openrune

import com.displee.cache.CacheLibrary
import dev.openrune.cache.CONFIGS
import dev.openrune.codec.osrs.impl.NpcServerCodec
import dev.openrune.definition.codec.NPCCodec
import dev.openrune.filesystem.Cache
import dev.openrune.gamevals.GameValProvider
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Repairs the activity form of Kovac. The client definition has the Foundry options but no base
 * animation set, so it slides when moved. This patches only the omitted animation opcodes and keeps
 * every client-side option and model definition intact.
 */
public fun main() {
    GameValProvider.load()
    val root = Paths.get(System.getProperty("user.dir")).normalize()
    installFoundryKovacAnimations(
        live = root.resolve(Paths.get(".data", "cache", "LIVE")),
        server = root.resolve(Paths.get(".data", "cache", "SERVER")),
    )
}

public fun installFoundryKovacAnimations(live: Path, server: Path) {
    require(Files.isDirectory(live)) { "Required LIVE cache not found at $live" }
    require(Files.isDirectory(server)) { "Required SERVER cache not found at $server" }
    patchLive(live)
    patchServer(server)
    verifyLive(live)
    verifyServer(server)
    println("Repaired Kovac ($KOVAC_ACTIVITY_NPC) movement animations in LIVE and SERVER.")
}

private fun patchLive(path: Path) {
    val encoded: ByteArray
    val cache = Cache.load(path)
    try {
        val bytes =
            requireNotNull(cache.data(CONFIGS, LIVE_NPC_ARCHIVE, KOVAC_ACTIVITY_NPC)) {
                "LIVE Kovac definition is missing"
            }
        encoded =
            if (hasKovacAnimations(NPCCodec(REVISION).loadData(KOVAC_ACTIVITY_NPC, bytes))) {
                bytes
            } else {
                patchClientAnimationOpcodes(bytes)
            }
    } finally {
        cache.close()
    }
    write(path, LIVE_NPC_ARCHIVE, encoded)
}

private fun patchServer(path: Path) {
    val encoded: ByteArray
    val cache = Cache.load(path)
    try {
        val codec = NpcServerCodec(REVISION)
        val bytes =
            requireNotNull(cache.data(CONFIGS, SERVER_NPC_ARCHIVE, KOVAC_ACTIVITY_NPC)) {
                "SERVER Kovac definition is missing"
            }
        val type = codec.loadData(KOVAC_ACTIVITY_NPC, bytes)
        type.standAnim = STAND_ANIMATION
        type.walkAnim = WALK_ANIMATION
        type.rotateBackAnim = WALK_ANIMATION
        type.walkLeftAnim = SIDE_STEP_ANIMATION
        type.walkRightAnim = SIDE_STEP_ANIMATION
        encoded = encode { with(codec) { it.encode(type) } }
    } finally {
        cache.close()
    }
    write(path, SERVER_NPC_ARCHIVE, encoded)
}

private fun verifyLive(path: Path) {
    val cache = Cache.load(path)
    try {
        val bytes = requireNotNull(cache.data(CONFIGS, LIVE_NPC_ARCHIVE, KOVAC_ACTIVITY_NPC))
        val type = NPCCodec(REVISION).loadData(KOVAC_ACTIVITY_NPC, bytes)
        verify(
            type.standAnim,
            type.walkAnim,
            type.rotateBackAnim,
            type.walkLeftAnim,
            type.walkRightAnim,
        )
        check(type.actions.getOpOrNull(0) == "Talk-to") { "Kovac lost Talk-to" }
        check(type.actions.getOpOrNull(2) == "Hand-in") { "Kovac lost Hand-in" }
        check(type.actions.getOpOrNull(3) == "Commission") { "Kovac lost Commission" }
        check(type.actions.getOpOrNull(4) == "Shop") { "Kovac lost Shop" }
    } finally {
        cache.close()
    }
}

private fun verifyServer(path: Path) {
    val cache = Cache.load(path)
    try {
        val type =
            NpcServerCodec(REVISION)
                .loadData(
                    KOVAC_ACTIVITY_NPC,
                    requireNotNull(cache.data(CONFIGS, SERVER_NPC_ARCHIVE, KOVAC_ACTIVITY_NPC)),
                )
        verify(
            type.standAnim,
            type.walkAnim,
            type.rotateBackAnim,
            type.walkLeftAnim,
            type.walkRightAnim,
        )
    } finally {
        cache.close()
    }
}

private fun hasKovacAnimations(type: dev.openrune.definition.type.NpcType): Boolean =
    type.standAnim == STAND_ANIMATION &&
        type.walkAnim == WALK_ANIMATION &&
        type.rotateBackAnim == WALK_ANIMATION &&
        type.walkLeftAnim == SIDE_STEP_ANIMATION &&
        type.walkRightAnim == SIDE_STEP_ANIMATION

private fun patchClientAnimationOpcodes(bytes: ByteArray): ByteArray {
    check(bytes.lastOrNull() == 0.toByte()) { "Kovac client definition is missing its terminator" }
    val buffer = Unpooled.buffer(bytes.size + 12)
    return try {
        buffer.writeBytes(bytes, 0, bytes.size - 1)
        buffer.writeByte(13)
        buffer.writeShort(STAND_ANIMATION)
        buffer.writeByte(17)
        buffer.writeShort(WALK_ANIMATION)
        buffer.writeShort(WALK_ANIMATION)
        buffer.writeShort(SIDE_STEP_ANIMATION)
        buffer.writeShort(SIDE_STEP_ANIMATION)
        buffer.writeByte(0)
        ByteArray(buffer.readableBytes()).also(buffer::readBytes)
    } finally {
        buffer.release()
    }
}

private fun verify(stand: Int, walk: Int, back: Int, left: Int, right: Int) {
    check(stand == STAND_ANIMATION) {
        "Expected Kovac stand animation $STAND_ANIMATION, got $stand"
    }
    check(walk == WALK_ANIMATION && back == WALK_ANIMATION) {
        "Expected Kovac forward/back walk animation $WALK_ANIMATION, got $walk/$back"
    }
    check(left == SIDE_STEP_ANIMATION && right == SIDE_STEP_ANIMATION) {
        "Expected Kovac side-step animation $SIDE_STEP_ANIMATION, got $left/$right"
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
        cache.put(CONFIGS, archive, KOVAC_ACTIVITY_NPC, bytes)
        check(cache.index(CONFIGS).update()) {
            "Cache index update failed for Kovac ($KOVAC_ACTIVITY_NPC) in $path"
        }
    } finally {
        cache.close()
    }
}

private const val REVISION = 240
private const val KOVAC_ACTIVITY_NPC = 11_472
private const val LIVE_NPC_ARCHIVE = 9
private const val SERVER_NPC_ARCHIVE = 58
private const val STAND_ANIMATION = 4_670
private const val WALK_ANIMATION = 6_372
private const val SIDE_STEP_ANIMATION = 6_371
