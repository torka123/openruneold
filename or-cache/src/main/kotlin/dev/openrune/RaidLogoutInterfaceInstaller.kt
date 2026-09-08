package dev.openrune

import com.displee.cache.CacheLibrary
import dev.openrune.cache.INTERFACES
import dev.openrune.cache.filestore.definition.ComponentDecoder
import dev.openrune.filesystem.Cache
import io.netty.buffer.Unpooled
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Copies the prepared revision-240 raid Logout tab into the two runtime caches. */
public fun main(args: Array<String>) {
    require(args.size == 3) { "Expected source cache, SERVER cache, and LIVE cache paths." }
    val source = Paths.get(args[0]).toAbsolutePath().normalize()
    val destinations = args.drop(1).map { Paths.get(it).toAbsolutePath().normalize() }
    require(Files.isDirectory(source)) { "Raid Logout source cache not found: $source" }
    destinations.forEach { path ->
        require(Files.isDirectory(path)) { "Runtime cache not found: $path" }
    }

    val components = readAndVerifySource(source)
    destinations.forEach { destination ->
        writeInterface(destination, components)
        verifyDestination(destination, components)
    }
    println(
        "Installed raid Logout interface $INTERFACE_ID (${components.size} components) into " +
            destinations.joinToString()
    )
}

private fun readAndVerifySource(source: Path): Map<Int, ByteArray> {
    val cache = Cache.load(source)
    return try {
        require(INTERFACE_ID in cache.archives(INTERFACES)) {
            "Source cache does not contain interface $INTERFACE_ID"
        }
        val children = cache.files(INTERFACES, INTERFACE_ID).sorted()
        require(children.isNotEmpty()) { "Interface $INTERFACE_ID has no components" }
        val components =
            children.associateWith { child ->
                requireNotNull(cache.data(INTERFACES, INTERFACE_ID, child)) {
                    "Missing interface $INTERFACE_ID:$child in source cache"
                }
            }
        verifyLeaveOperation(cache, components)
        components
    } finally {
        cache.close()
    }
}

private fun writeInterface(destination: Path, components: Map<Int, ByteArray>) {
    val cache = CacheLibrary(destination.toString())
    try {
        components.forEach { (child, bytes) -> cache.put(INTERFACES, INTERFACE_ID, child, bytes) }
        check(cache.index(INTERFACES).update()) { "Interface index update failed for $destination" }
    } finally {
        cache.close()
    }
}

private fun verifyDestination(destination: Path, expected: Map<Int, ByteArray>) {
    val cache = Cache.load(destination)
    try {
        val actualChildren = cache.files(INTERFACES, INTERFACE_ID).toSet()
        check(actualChildren == expected.keys) {
            "Interface $INTERFACE_ID component mismatch in $destination: " +
                "expected=${expected.keys.sorted()}, actual=${actualChildren.sorted()}"
        }
        expected.forEach { (child, bytes) ->
            val actual = requireNotNull(cache.data(INTERFACES, INTERFACE_ID, child))
            check(actual.contentEquals(bytes)) {
                "Interface $INTERFACE_ID:$child differs after writing to $destination"
            }
        }
        verifyLeaveOperation(cache, expected)
    } finally {
        cache.close()
    }
}

private fun verifyLeaveOperation(cache: Cache, components: Map<Int, ByteArray>) {
    require(LEAVE_COMPONENT in components) {
        "Interface $INTERFACE_ID is missing Leave Instance component $LEAVE_COMPONENT"
    }
    val decoder = ComponentDecoder(cache, CACHE_REVISION)
    val buffer = Unpooled.wrappedBuffer(components.getValue(LEAVE_COMPONENT))
    val component =
        try {
            decoder.read(packed(LEAVE_COMPONENT), buffer, "leave_instance")
        } finally {
            buffer.release()
        }
    require(component.op.any { it.equals("Leave Instance", ignoreCase = true) }) {
        "Interface $INTERFACE_ID:$LEAVE_COMPONENT has no Leave Instance operation"
    }
}

private fun packed(child: Int): Int = (INTERFACE_ID shl 16) or child

private const val CACHE_REVISION = 240
private const val INTERFACE_ID = 1200
private const val LEAVE_COMPONENT = 8
