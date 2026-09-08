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

private val RETIRED_OVERLAY_CHILDREN = (36..72).toSet()

/** Restores the untouched revision-240 Adventure Paths modal by removing the retired overlay. */
public fun main() {
    val root = Paths.get(System.getProperty("user.dir")).normalize()
    patchNativeDailyAdventureInterface(
        live = root.resolve(Paths.get(".data", "cache", "LIVE")),
        server = root.resolve(Paths.get(".data", "cache", "SERVER")),
    )
}

public fun patchNativeDailyAdventureInterface(live: Path, server: Path) {
    val paths = listOf(server, live)
    paths.forEach { require(Files.isDirectory(it)) { "Required cache not found at $it" } }
    paths.forEach { path ->
        val before = Cache.load(path)
        val removable =
            try {
                val children = before.files(INTERFACES, INTERFACE_ID).toSet()
                require(children.containsAll(STOCK_CHILDREN)) {
                    "Native Adventure Paths interface $INTERFACE_ID is incomplete in $path"
                }
                val unknown = children - STOCK_CHILDREN - RETIRED_OVERLAY_CHILDREN
                require(unknown.isEmpty()) {
                    "Native Adventure Paths interface $INTERFACE_ID has unknown children ${unknown.sorted()} in $path"
                }
                (children intersect RETIRED_OVERLAY_CHILDREN).sorted()
            } finally {
                before.close()
            }
        val writable = CacheLibrary(path.toString())
        try {
            removable.forEach { child -> writable.remove(INTERFACES, INTERFACE_ID, child) }
            check(writable.index(INTERFACES).update()) { "Interface index update failed for $path" }
        } finally {
            writable.close()
        }
        val restored = Cache.load(path)
        try {
            check(restored.files(INTERFACES, INTERFACE_ID).toSet() == STOCK_CHILDREN) {
                "Native Adventure Paths interface $INTERFACE_ID was not restored in $path"
            }
        } finally {
            restored.close()
        }
        println("Removed ${removable.size} retired Daily Adventure children from $path.")
    }
}

private fun encode(source: Path, components: Map<Int, ComponentType>): Map<Int, ByteArray> {
    val cache = Cache.load(source)
    return try {
        val decoder = ComponentDecoder(cache, CACHE_REVISION)
        components.mapValues { (_, component) ->
            val buffer = Unpooled.buffer()
            try {
                decoder.encode(component, buffer)
                ByteArray(buffer.readableBytes()).also(buffer::readBytes)
            } finally {
                buffer.release()
            }
        }
    } finally {
        cache.close()
    }
}

private fun verifyOwnership(path: Path, owned: Set<Int>) {
    val cache = Cache.load(path)
    try {
        val children = cache.files(INTERFACES, INTERFACE_ID).toSet()
        require(children.containsAll(STOCK_CHILDREN)) {
            "Native Adventure Paths interface $INTERFACE_ID is incomplete in $path"
        }
        val unknown = children - STOCK_CHILDREN - owned
        require(unknown.isEmpty()) {
            "Native Adventure Paths interface $INTERFACE_ID has unowned children ${unknown.sorted()} in $path"
        }
    } finally {
        cache.close()
    }
}

private fun verify(path: Path, expected: Map<Int, ComponentType>) {
    val cache = Cache.load(path)
    try {
        val children = cache.files(INTERFACES, INTERFACE_ID).toSet()
        check(children == STOCK_CHILDREN + expected.keys) {
            "Native Daily Adventure component mismatch in $path: ${children.sorted()}"
        }
        val decoder = ComponentDecoder(cache, CACHE_REVISION)
        expected.forEach { (child, component) ->
            val data = checkNotNull(cache.data(INTERFACES, INTERFACE_ID, child))
            val buffer = Unpooled.wrappedBuffer(data)
            try {
                val decoded =
                    decoder.read(packed(child), buffer, component.internalName ?: "daily_$child")
                check(decoded.packed == component.packed)
                check(decoded.layer == component.layer)
                check(decoded.type == component.type)
            } finally {
                buffer.release()
            }
        }
    } finally {
        cache.close()
    }
}

private fun buildNativeDailyAdventureComponents(): Map<Int, ComponentType> {
    val result = linkedMapOf<Int, ComponentType>()
    fun add(child: Int, name: String, block: ComponentTypeBuilder.() -> Unit) {
        val builder = ComponentTypeBuilder(name)
        builder.v3 = true
        builder.block()
        result[child] = builder.build(packed(child))
    }
    fun rect(
        child: Int,
        name: String,
        parent: Int,
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
            layer = packed(parent)
            colour1 = colour
            fill = true
            noClickThrough = clickable
            if (clickable) {
                events = 2
                opBase = name.replace('_', ' ')
                op = arrayOf("Select")
            }
        }
    fun text(
        child: Int,
        name: String,
        parent: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        value: String,
        colour: Int = WHITE,
        centered: Boolean = false,
        clickable: Boolean = false,
    ) =
        add(child, name) {
            type = 4
            this.x = x
            this.y = y
            this.width = width
            this.height = height
            layer = packed(parent)
            this.text = value
            textFont = FONT_PLAIN
            colour1 = colour
            textShadow = true
            textAlignH = if (centered) 1 else 0
            textAlignV = 1
            textLineHeight = 12
            noClickThrough = clickable
            if (clickable) {
                events = 2
                opBase = name.replace('_', ' ')
                op = arrayOf("Select")
            }
        }
    fun graphic(child: Int, name: String, x: Int, y: Int, sprite: Int, opText: String) =
        add(child, name) {
            type = 5
            this.x = x
            this.y = y
            width = 22
            height = 22
            layer = packed(FILTERS_PARENT)
            graphic = sprite
            outline = 1
            events = 2
            opBase = opText
            op = arrayOf(opText)
            noClickThrough = true
        }
    fun placeholderGraphic(child: Int, name: String, x: Int, y: Int, sprite: Int) =
        add(child, name) {
            type = 5
            this.x = x
            this.y = y
            width = 22
            height = 22
            layer = packed(TASKS_PARENT)
            graphic = sprite
            outline = 1
        }

    repeat(DAILY_ROWS) { slot ->
        val y = slot * ROW_STRIDE
        rect(
            child = rowBackground(slot),
            name = "daily_task_row_background_${slot + 1}",
            parent = TASKS_PARENT,
            x = 0,
            y = y + 1,
            width = TASK_LIST_WIDTH,
            height = ROW_HEIGHT - 2,
            colour = if (slot % 2 == 0) ROW_DARK else ROW_LIGHT,
            clickable = true,
        )
        placeholderGraphic(
            child = rowGraphic(slot),
            name = "daily_task_placeholder_${slot + 1}",
            x = 2,
            y = y + 2,
            sprite = PLACEHOLDER_SPRITES[slot],
        )
        text(
            child = rowText(slot),
            name = "daily_task_row_${slot + 1}",
            parent = TASKS_PARENT,
            x = TASK_TEXT_X,
            y = y + 1,
            width = TASK_TEXT_WIDTH,
            height = ROW_HEIGHT - 2,
            value = "",
            clickable = true,
        )
    }

    text(PROGRESS, "daily_task_progress", DETAILS_PARENT, 5, 67, 295, 18, "Progress: --", GOLD)
    text(REWARD, "daily_task_reward", DETAILS_PARENT, 5, 87, 295, 18, "Reward: --", GOLD)
    text(REROLLS, "daily_task_rerolls", DETAILS_PARENT, 5, 107, 295, 18, "Rerolls: --", PALE)

    graphic(TASK_SHOP, "daily_task_shop", 315, 3, TASK_SHOP_SPRITE, "Open Task Shop")
    rect(
        STREAK_BUTTON,
        "daily_streak_rewards",
        FILTERS_PARENT,
        342,
        2,
        110,
        24,
        BUTTON,
        clickable = true,
    )
    text(
        STREAK_LABEL,
        "daily_streak_rewards_label",
        FILTERS_PARENT,
        342,
        1,
        110,
        26,
        "Daily Streak<br>Rewards",
        GOLD,
        centered = true,
        clickable = true,
    )
    graphic(RESET, "daily_task_reset", 455, 3, RESET_SPRITE, "Reroll selected task")
    return result
}

private fun rowBackground(slot: Int): Int = ROW_BACKGROUND_FIRST + slot

private fun rowGraphic(slot: Int): Int = ROW_GRAPHIC_FIRST + slot

private fun rowText(slot: Int): Int = ROW_TEXT_FIRST + slot

private fun packed(child: Int): Int = (INTERFACE_ID shl 16) or child

private const val CACHE_REVISION = 240
private const val INTERFACE_ID = 642
private val STOCK_CHILDREN = (0..35).toSet()

private const val TASKS_PARENT = 6
private const val FILTERS_PARENT = 21
private const val DETAILS_PARENT = 28
private const val DAILY_ROWS = 10
private const val ROW_STRIDE = 25
private const val ROW_HEIGHT = 25
private const val TASK_LIST_WIDTH = 144
private const val TASK_TEXT_X = 26
private const val TASK_TEXT_WIDTH = 118

private const val ROW_BACKGROUND_FIRST = 36
private const val ROW_GRAPHIC_FIRST = 46
private const val ROW_TEXT_FIRST = 56
private const val PROGRESS = 66
private const val REWARD = 67
private const val REROLLS = 68
private const val TASK_SHOP = 69
private const val STREAK_BUTTON = 70
private const val STREAK_LABEL = 71
private const val RESET = 72

private const val TASK_SHOP_SPRITE = 1090
private const val RESET_SPRITE = 7039
private val PLACEHOLDER_SPRITES = intArrayOf(197, 197, 197, 198, 198, 198, 199, 199, 199, 200)
private const val FONT_PLAIN = 495
private const val WHITE = 0xFFFFFF
private const val GOLD = 0xFF981F
private const val PALE = 0xFFF2CE
private const val ROW_DARK = 0x17130F
private const val ROW_LIGHT = 0x211B15
private const val BUTTON = 0x3D3429
