package dev.openrune

import com.displee.cache.CacheLibrary
import dev.openrune.cache.INTERFACES
import dev.openrune.cache.filestore.definition.ComponentDecoder
import dev.openrune.definition.type.widget.ComponentType
import dev.openrune.definition.type.widget.ComponentTypeBuilder
import dev.openrune.filesystem.Cache
import io.netty.buffer.Unpooled
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private val LegacyDailyAdventureGaps: Set<Int> =
    setOf(8, 9, 16, 17, 18, 19, 26, 27, 28, 29, 32, 33, 34, 35, 36, 37, 38, 39)

private fun isOwnedDailyAdventureLayout(files: Set<Int>, expected: Set<Int>): Boolean =
    files == expected || files == expected - LegacyDailyAdventureGaps

/** Builds the revision-240 native Daily Adventure Paths interface in both served caches. */
public fun main() {
    val root = Paths.get(System.getProperty("user.dir")).normalize()
    val paths = listOf("SERVER", "LIVE").map { root.resolve(Paths.get(".data", "cache", it)) }
    paths.forEach { require(Files.isDirectory(it)) { "Required cache not found at $it" } }
    val components = buildDailyAdventureInterface()
    val encoded = linkedMapOf<Int, ByteArray>()
    val readCache = Cache.load(paths.first())
    try {
        paths.forEach { path ->
            val cache = if (path == paths.first()) readCache else Cache.load(path)
            try {
                if (DailyAdventureInterfaceIds.Id in cache.archives(INTERFACES)) {
                    val files = cache.files(INTERFACES, DailyAdventureInterfaceIds.Id).toSet()
                    require(isOwnedDailyAdventureLayout(files, components.keys)) {
                        "Interface ${DailyAdventureInterfaceIds.Id} is occupied in $path " +
                            "by ${files.size} unknown components"
                    }
                }
            } finally {
                if (cache !== readCache) cache.close()
            }
        }
        val decoder = ComponentDecoder(readCache, DailyAdventureInterfaceIds.Revision)
        components.forEach { (child, component) ->
            val buffer = Unpooled.buffer()
            try {
                decoder.encode(component, buffer)
                encoded[child] = ByteArray(buffer.readableBytes()).also(buffer::readBytes)
            } finally {
                buffer.release()
            }
        }
    } finally {
        readCache.close()
    }
    paths.forEach { path ->
        val writable = CacheLibrary(path.toString())
        try {
            encoded.forEach { (child, bytes) ->
                writable.put(INTERFACES, DailyAdventureInterfaceIds.Id, child, bytes)
            }
            check(writable.index(INTERFACES).update()) { "Interface index update failed for $path" }
        } finally {
            writable.close()
        }
    }
    verifyDailyAdventureInterface(paths, components)
    writeDailyAdventureGamevals(root, components)
    println(
        "Built Daily Adventure interface ${DailyAdventureInterfaceIds.Id} with " +
            "${components.size} revision-240 components."
    )
}

public fun patchDailyAdventureInterface(live: Path, server: Path) {
    val paths = listOf(server, live)
    paths.forEach { require(Files.isDirectory(it)) { "Required cache not found at $it" } }
    val components = buildDailyAdventureInterface()
    val encoded = linkedMapOf<Int, ByteArray>()
    val readCache = Cache.load(server)
    try {
        paths.forEach { path ->
            val cache = if (path == server) readCache else Cache.load(path)
            try {
                if (DailyAdventureInterfaceIds.Id in cache.archives(INTERFACES)) {
                    val files = cache.files(INTERFACES, DailyAdventureInterfaceIds.Id).toSet()
                    require(isOwnedDailyAdventureLayout(files, components.keys)) {
                        "Interface ${DailyAdventureInterfaceIds.Id} is occupied in $path " +
                            "by ${files.size} unknown components"
                    }
                }
            } finally {
                if (cache !== readCache) cache.close()
            }
        }
        val decoder = ComponentDecoder(readCache, DailyAdventureInterfaceIds.Revision)
        components.forEach { (child, component) ->
            val buffer = Unpooled.buffer()
            try {
                decoder.encode(component, buffer)
                encoded[child] = ByteArray(buffer.readableBytes()).also(buffer::readBytes)
            } finally {
                buffer.release()
            }
        }
    } finally {
        readCache.close()
    }
    paths.forEach { path ->
        val writable = CacheLibrary(path.toString())
        try {
            encoded.forEach { (child, bytes) ->
                writable.put(INTERFACES, DailyAdventureInterfaceIds.Id, child, bytes)
            }
            check(writable.index(INTERFACES).update()) { "Interface index update failed for $path" }
        } finally {
            writable.close()
        }
    }
    verifyDailyAdventureInterface(paths, components)
    val root = live.parent.parent.parent
    writeDailyAdventureGamevals(root, components)
    println(
        "Built Daily Adventure interface ${DailyAdventureInterfaceIds.Id} with " +
            "${components.size} revision-240 components."
    )
}

public object DailyAdventureInterfaceIds {
    public const val Revision: Int = 240
    public const val Id: Int = 970
    public const val Root: Int = 0
    public const val Frame: Int = 1
    public const val Reset: Int = 2
    public const val Streak: Int = 3
    public const val Points: Int = 4
    public const val Rerolls: Int = 5
    public const val ShopButton: Int = 6
    public const val ShopLabel: Int = 7
    public const val CombatCard: Int = 10
    public const val CombatTitle: Int = 11
    public const val CombatProgress: Int = 12
    public const val CombatStatus: Int = 13
    public const val CombatAction: Int = 14
    public const val CombatActionLabel: Int = 15
    public const val SkillingCard: Int = 20
    public const val SkillingTitle: Int = 21
    public const val SkillingProgress: Int = 22
    public const val SkillingStatus: Int = 23
    public const val SkillingAction: Int = 24
    public const val SkillingActionLabel: Int = 25
    public const val TaskPanel: Int = 30
    public const val TaskHeading: Int = 31
    public const val TaskFirst: Int = 40
    public const val TaskStride: Int = 4
    public const val Footer: Int = 80
    public const val ClaimButton: Int = 81
    public const val ClaimLabel: Int = 82
    public const val Status: Int = 83

    public fun taskBackground(index: Int): Int = TaskFirst + (index * TaskStride)

    public fun taskText(index: Int): Int = taskBackground(index) + 1

    public fun taskReroll(index: Int): Int = taskBackground(index) + 2

    public fun taskRerollLabel(index: Int): Int = taskBackground(index) + 3

    public fun packed(child: Int): Int = (Id shl 16) or child
}

private const val FontPlain: Int = 495
private const val FontBold: Int = 496
private const val Gold: Int = 0xFFB83F
private const val Pale: Int = 0xF2E5C4
private const val Tan: Int = 0xD8B67A
private const val Panel: Int = 0x2B241B
private const val PanelAlt: Int = 0x352C21
private const val Border: Int = 0x6B573A
private const val Locked: Int = 0x3D3A34

private fun buildDailyAdventureInterface(): Map<Int, ComponentType> {
    val result = linkedMapOf<Int, ComponentType>()
    fun add(child: Int, name: String, block: ComponentTypeBuilder.() -> Unit) {
        val builder = ComponentTypeBuilder(name)
        builder.v3 = true
        builder.block()
        result[child] = builder.build(DailyAdventureInterfaceIds.packed(child))
    }
    fun container(child: Int, name: String, layer: Int = -1) =
        add(child, name) {
            type = 0
            x = 0
            y = 0
            width = 500
            height = 332
            this.layer = if (layer < 0) -1 else DailyAdventureInterfaceIds.packed(layer)
            scrollWidth = width
            scrollHeight = height
            noClickThrough = true
        }
    fun rect(
        child: Int,
        name: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        colour: Int,
        clickable: Boolean = false,
    ) =
        add(child, name) {
            type = 3
            this.x = x
            this.y = y
            this.width = width
            this.height = height
            layer = DailyAdventureInterfaceIds.packed(DailyAdventureInterfaceIds.Root)
            colour1 = colour
            fill = true
            if (clickable) {
                events = 2
                opBase = name.replace('_', ' ')
                op = arrayOf("Select")
            }
        }
    fun text(
        child: Int,
        name: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        value: String,
        colour: Int = Pale,
        centered: Boolean = false,
        bold: Boolean = false,
    ) =
        add(child, name) {
            type = 4
            this.x = x
            this.y = y
            this.width = width
            this.height = height
            layer = DailyAdventureInterfaceIds.packed(DailyAdventureInterfaceIds.Root)
            this.text = value
            textFont = if (bold) FontBold else FontPlain
            colour1 = colour
            textShadow = true
            textAlignH = if (centered) 1 else 0
            textAlignV = 1
            textLineHeight = 12
        }

    container(DailyAdventureInterfaceIds.Root, "universe")
    text(
        DailyAdventureInterfaceIds.Frame,
        "frame",
        0,
        4,
        500,
        20,
        "Daily Adventure Paths",
        Gold,
        true,
        true,
    )
    text(DailyAdventureInterfaceIds.Reset, "reset", 10, 28, 130, 16, "Resets in: --:--:--", Tan)
    text(DailyAdventureInterfaceIds.Streak, "streak", 142, 28, 85, 16, "Streak: 0", Gold)
    text(DailyAdventureInterfaceIds.Points, "points", 229, 28, 110, 16, "Task Points: 0", Gold)
    text(DailyAdventureInterfaceIds.Rerolls, "rerolls", 341, 28, 80, 16, "Rerolls: 0/3", Tan)
    rect(DailyAdventureInterfaceIds.ShopButton, "shop_button", 423, 27, 68, 18, Border, true)
    text(
        DailyAdventureInterfaceIds.ShopLabel,
        "shop_label",
        423,
        27,
        68,
        18,
        "Task Shop",
        Gold,
        true,
        true,
    )

    fun card(base: Int, title: String, x: Int, action: String) {
        rect(base, title.lowercase().replace(' ', '_') + "_card", x, 48, 235, 69, Panel, true)
        text(
            base + 1,
            title.lowercase().replace(' ', '_') + "_title",
            x + 7,
            51,
            221,
            17,
            title,
            Gold,
            true,
            true,
        )
        text(
            base + 2,
            title.lowercase().replace(' ', '_') + "_progress",
            x + 8,
            69,
            219,
            15,
            "0/10 tasks complete",
            Pale,
            true,
        )
        text(
            base + 3,
            title.lowercase().replace(' ', '_') + "_status",
            x + 8,
            84,
            219,
            14,
            "Complete any 8",
            Tan,
            true,
        )
        rect(
            base + 4,
            title.lowercase().replace(' ', '_') + "_action",
            x + 55,
            99,
            125,
            15,
            Border,
            true,
        )
        text(
            base + 5,
            title.lowercase().replace(' ', '_') + "_action_label",
            x + 55,
            98,
            125,
            16,
            action,
            Gold,
            true,
            true,
        )
    }
    card(DailyAdventureInterfaceIds.CombatCard, "COMBAT PATH", 10, "Choose Combat")
    card(DailyAdventureInterfaceIds.SkillingCard, "SKILLING PATH", 255, "Choose Skilling")

    rect(DailyAdventureInterfaceIds.TaskPanel, "task_panel", 10, 121, 480, 177, Locked)
    text(
        DailyAdventureInterfaceIds.TaskHeading,
        "task_heading",
        16,
        123,
        468,
        15,
        "Choose a path to see today's tasks",
        Gold,
        true,
        true,
    )
    repeat(10) { index ->
        val y = 140 + (index * 15)
        rect(
            DailyAdventureInterfaceIds.taskBackground(index),
            "task_background_$index",
            14,
            y,
            472,
            14,
            if (index % 2 == 0) PanelAlt else Panel,
        )
        text(
            DailyAdventureInterfaceIds.taskText(index),
            "task_text_$index",
            18,
            y,
            425,
            14,
            "",
            Pale,
        )
        rect(
            DailyAdventureInterfaceIds.taskReroll(index),
            "task_reroll_$index",
            447,
            y,
            35,
            14,
            Border,
            true,
        )
        text(
            DailyAdventureInterfaceIds.taskRerollLabel(index),
            "task_reroll_label_$index",
            447,
            y,
            35,
            14,
            "Reroll",
            Gold,
            true,
        )
    }
    text(
        DailyAdventureInterfaceIds.Footer,
        "footer",
        12,
        300,
        300,
        15,
        "8/10 required; all 10 tasks award points",
        Tan,
    )
    rect(DailyAdventureInterfaceIds.ClaimButton, "claim_button", 350, 299, 140, 18, Border, true)
    text(
        DailyAdventureInterfaceIds.ClaimLabel,
        "claim_label",
        350,
        299,
        140,
        18,
        "Claim daily bonus",
        Gold,
        true,
        true,
    )
    text(DailyAdventureInterfaceIds.Status, "status", 10, 317, 480, 12, "", Tan, true)

    // The revision-240 client iterates every child slot when firing interface load events and does
    // not
    // null-check sparse entries. Keep intentional numbering gaps as hidden inert components so
    // opening
    // this custom modal cannot dereference a missing child.
    for (child in DailyAdventureInterfaceIds.Root..DailyAdventureInterfaceIds.Status) {
        if (child !in result) {
            add(child, "unused_$child") {
                type = 0
                layer = DailyAdventureInterfaceIds.packed(DailyAdventureInterfaceIds.Root)
                hide = true
            }
        }
    }
    return result
}

private fun verifyDailyAdventureInterface(paths: List<Path>, components: Map<Int, ComponentType>) {
    paths.forEach { path ->
        val cache = Cache.load(path)
        try {
            check(cache.files(INTERFACES, DailyAdventureInterfaceIds.Id).toSet() == components.keys)
            val decoder = ComponentDecoder(cache, DailyAdventureInterfaceIds.Revision)
            components.forEach { (child, expected) ->
                val data =
                    checkNotNull(cache.data(INTERFACES, DailyAdventureInterfaceIds.Id, child))
                val buffer = Unpooled.wrappedBuffer(data)
                try {
                    val decoded =
                        decoder.read(
                            DailyAdventureInterfaceIds.packed(child),
                            buffer,
                            expected.internalName ?: "component_$child",
                        )
                    check(decoded.packed == expected.packed)
                } finally {
                    buffer.release()
                }
            }
        } finally {
            cache.close()
        }
    }
}

private fun writeDailyAdventureGamevals(root: Path, components: Map<Int, ComponentType>) {
    val dir = root.resolve(Paths.get(".data", "gamevals"))
    Files.createDirectories(dir)
    mergeDailyAdventureMappings(
        dir.resolve("interface.rscm"),
        "daily_adventure=",
        "daily_adventure=${DailyAdventureInterfaceIds.Id}",
    )
    val componentMappings =
        components.values.joinToString("\n") { component ->
            "daily_adventure:${component.internalName}=${component.packed}"
        }
    mergeDailyAdventureMappings(
        dir.resolve("component.rscm"),
        "daily_adventure:",
        componentMappings,
    )
}

private fun mergeDailyAdventureMappings(path: Path, prefix: String, replacement: String) {
    val preserved =
        if (Files.exists(path)) Files.readAllLines(path).filterNot { it.startsWith(prefix) }
        else emptyList()
    val merged =
        (preserved + replacement.lines())
            .filter(String::isNotBlank)
            .joinToString("\n", postfix = "\n")
    Files.writeString(path, merged)
}
