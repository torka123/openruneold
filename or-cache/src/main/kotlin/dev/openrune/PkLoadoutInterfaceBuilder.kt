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

/**
 * Reproducible revision-240 cache builder for the PK loadout interface.
 *
 * Interface 969 is the first group above the stock revision-240 maximum (968). The component layout
 * is shared with the server through [PkLoadoutInterfaceIds]. No sprites are replaced: the
 * RuneScape-style frame comes from stock clientscript 227 and the panels use native IF3 colours.
 */
public fun main() {
    val root = Paths.get(System.getProperty("user.dir")).normalize()
    val cachePaths =
        listOf("SERVER", "LIVE").map { name -> root.resolve(Paths.get(".data", "cache", name)) }
    cachePaths.forEach { path ->
        require(Files.isDirectory(path)) { "Required cache not found at $path" }
    }

    val components = buildPkLoadoutInterface()
    val encoded = linkedMapOf<Int, ByteArray>()
    val readCache = Cache.load(cachePaths.first())
    try {
        cachePaths.forEach { path ->
            val collisionCache = if (path == cachePaths.first()) readCache else Cache.load(path)
            try {
                val existingMax =
                    collisionCache
                        .archives(INTERFACES)
                        .filter { it != PkLoadoutInterfaceIds.Id }
                        .maxOrNull() ?: -1
                require(existingMax < PkLoadoutInterfaceIds.Id) {
                    "Interface ${PkLoadoutInterfaceIds.Id} is not collision-free in $path (max=$existingMax)."
                }
            } finally {
                if (collisionCache !== readCache) collisionCache.close()
            }
        }
        val decoder = ComponentDecoder(readCache, PkLoadoutInterfaceIds.Revision)
        components.forEach { (child, component) ->
            val buffer = Unpooled.buffer()
            try {
                decoder.encode(component, buffer)
                val bytes = ByteArray(buffer.readableBytes())
                buffer.readBytes(bytes)
                encoded[child] = bytes
            } finally {
                buffer.release()
            }
        }
    } finally {
        readCache.close()
    }
    cachePaths.forEach { path ->
        val writableCache = CacheLibrary(path.toString())
        try {
            encoded.forEach { (child, bytes) ->
                writableCache.put(INTERFACES, PkLoadoutInterfaceIds.Id, child, bytes)
            }
            check(writableCache.index(INTERFACES).update()) {
                "Interface index update failed for $path."
            }
        } finally {
            writableCache.close()
        }
    }
    verifyPackedInterface(cachePaths, components)
    writeGamevals(root, components)
    println(
        "Built PK loadout interface ${PkLoadoutInterfaceIds.Id} with ${components.size} components in ${cachePaths.joinToString()}"
    )
}

private fun verifyPackedInterface(cachePaths: List<Path>, components: Map<Int, ComponentType>) {
    cachePaths.forEach { path ->
        val cache = Cache.load(path)
        try {
            val files = cache.files(INTERFACES, PkLoadoutInterfaceIds.Id).toSet()
            check(files == components.keys) {
                "Interface ${PkLoadoutInterfaceIds.Id} file mismatch in $path: expected=${components.size}, actual=${files.size}"
            }
            val decoder = ComponentDecoder(cache, PkLoadoutInterfaceIds.Revision)
            components.forEach { (child, expected) ->
                val bytes = checkNotNull(cache.data(INTERFACES, PkLoadoutInterfaceIds.Id, child))
                val buffer = Unpooled.wrappedBuffer(bytes)
                try {
                    val decoded =
                        decoder.read(
                            PkLoadoutInterfaceIds.packed(child),
                            buffer,
                            expected.internalName ?: "component_$child",
                        )
                    check(decoded.packed == expected.packed) {
                        "Component $child failed verification in $path"
                    }
                } finally {
                    buffer.release()
                }
            }
        } finally {
            cache.close()
        }
    }
}

public object PkLoadoutInterfaceIds {
    public const val Revision: Int = 240
    public const val Id: Int = 969
    public const val MaxPremadeRows: Int = 30
    public const val MaxCustomRows: Int = 20

    public const val Root: Int = 0
    public const val Frame: Int = 1
    public const val PremadeTitle: Int = 2
    public const val PremadePanel: Int = 3
    public const val PremadeScroll: Int = 4
    public const val PremadeScrollbar: Int = 5
    public const val PremadeRowFirst: Int = 6

    public const val CustomTitle: Int = 66
    public const val CustomPanel: Int = 67
    public const val CustomScroll: Int = 68
    public const val CustomScrollbar: Int = 69
    public const val CustomRowFirst: Int = 70

    public const val InventoryTitle: Int = 110
    public const val InventoryPanel: Int = 111
    public const val InventorySlotFirst: Int = 112

    public const val EquipmentTitle: Int = 196
    public const val EquipmentPanel: Int = 197
    public const val EquipmentSlotFirst: Int = 198

    public const val StatsTitle: Int = 231
    public const val StatsPanel: Int = 232
    public const val SelectedName: Int = 233
    public const val StatFirst: Int = 234
    public const val CombatLevel: Int = 241
    public const val Spellbook: Int = 242
    public const val SpecialEnergy: Int = 243
    public const val QuickPrayers: Int = 244
    public const val Description: Int = 245
    public const val Status: Int = 246

    public const val DeathToggle: Int = 247
    public const val DeathLabel: Int = 248
    public const val LoadButton: Int = 249
    public const val LoadLabel: Int = 250
    public const val SaveButton: Int = 251
    public const val SaveLabel: Int = 252
    public const val RenameButton: Int = 253
    public const val RenameLabel: Int = 254
    public const val DeleteButton: Int = 255
    public const val DeleteLabel: Int = 256
    public const val DefaultButton: Int = 257
    public const val DefaultLabel: Int = 258

    public fun premadeRow(index: Int): Int = PremadeRowFirst + (index * 2)

    public fun premadeText(index: Int): Int = premadeRow(index) + 1

    public fun customRow(slot: Int): Int = CustomRowFirst + (slot * 2)

    public fun customText(slot: Int): Int = customRow(slot) + 1

    public fun inventoryBackground(slot: Int): Int = InventorySlotFirst + (slot * 3)

    public fun inventoryModel(slot: Int): Int = inventoryBackground(slot) + 1

    public fun inventoryCount(slot: Int): Int = inventoryBackground(slot) + 2

    public fun equipmentBackground(slot: Int): Int = EquipmentSlotFirst + (slot * 3)

    public fun equipmentModel(slot: Int): Int = equipmentBackground(slot) + 1

    public fun equipmentCount(slot: Int): Int = equipmentBackground(slot) + 2

    public fun stat(index: Int): Int = StatFirst + index

    public fun packed(child: Int): Int = (Id shl 16) or child
}

private const val ActiveComponent: Int = -2147483645
private const val FontPlain: Int = 495
private const val FontBold: Int = 496
private const val Gold: Int = 0xFFB83F
private const val Tan: Int = 0xD8B67A
private const val Pale: Int = 0xF2E5C4
private const val Panel: Int = 0x2B241B
private const val PanelAlt: Int = 0x352C21
private const val Slot: Int = 0x17130F
private const val Border: Int = 0x6B573A

private fun buildPkLoadoutInterface(): Map<Int, ComponentType> {
    val result = linkedMapOf<Int, ComponentType>()
    fun add(child: Int, name: String, block: ComponentTypeBuilder.() -> Unit) {
        val builder = ComponentTypeBuilder(name)
        builder.v3 = true
        builder.block()
        result[child] = builder.build(PkLoadoutInterfaceIds.packed(child))
    }
    fun container(
        child: Int,
        name: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        layer: Int = PkLoadoutInterfaceIds.Root,
        scrollHeight: Int = 0,
    ) =
        add(child, name) {
            type = 0
            this.x = x
            this.y = y
            this.width = width
            this.height = height
            this.layer = if (layer < 0) -1 else PkLoadoutInterfaceIds.packed(layer)
            scrollWidth = width
            this.scrollHeight = scrollHeight
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
        layer: Int = PkLoadoutInterfaceIds.Root,
        clickable: Boolean = false,
    ) =
        add(child, name) {
            type = 3
            this.x = x
            this.y = y
            this.width = width
            this.height = height
            this.layer = PkLoadoutInterfaceIds.packed(layer)
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
        layer: Int = PkLoadoutInterfaceIds.Root,
        centered: Boolean = false,
        bold: Boolean = false,
    ) =
        add(child, name) {
            type = 4
            this.x = x
            this.y = y
            this.width = width
            this.height = height
            this.layer = PkLoadoutInterfaceIds.packed(layer)
            this.text = value
            textFont = if (bold) FontBold else FontPlain
            colour1 = colour
            textShadow = true
            textAlignH = if (centered) 1 else 0
            textAlignV = 1
            textLineHeight = 12
        }
    fun model(child: Int, name: String, x: Int, y: Int, width: Int, height: Int) =
        add(child, name) {
            type = 6
            this.x = x
            this.y = y
            this.width = width
            this.height = height
            layer = PkLoadoutInterfaceIds.packed(PkLoadoutInterfaceIds.Root)
            model = -1
            modelZoom = 450
            modelAngleX = 0
            modelAngleY = 0
            modelAngleZ = 0
            modelOrthog = true
            modelObjWidth = width
        }

    container(PkLoadoutInterfaceIds.Root, "universe", 0, 0, 500, 332, layer = -1)
    add(PkLoadoutInterfaceIds.Frame, "frame") {
        type = 0
        layer = PkLoadoutInterfaceIds.packed(PkLoadoutInterfaceIds.Root)
        onLoad = arrayOf(227, ActiveComponent, "PK Loadouts")
    }

    text(
        PkLoadoutInterfaceIds.PremadeTitle,
        "premade_title",
        8,
        29,
        104,
        16,
        "Premade Kits",
        Gold,
        centered = true,
        bold = true,
    )
    rect(PkLoadoutInterfaceIds.PremadePanel, "premade_panel", 8, 44, 105, 101, Panel)
    container(
        PkLoadoutInterfaceIds.PremadeScroll,
        "premade_scroll",
        10,
        46,
        90,
        97,
        scrollHeight = PkLoadoutInterfaceIds.MaxPremadeRows * 15,
    )
    container(PkLoadoutInterfaceIds.PremadeScrollbar, "premade_scrollbar", 100, 46, 12, 97)
    repeat(PkLoadoutInterfaceIds.MaxPremadeRows) { index ->
        rect(
            PkLoadoutInterfaceIds.premadeRow(index),
            "premade_row_$index",
            0,
            index * 15,
            90,
            14,
            if (index % 2 == 0) PanelAlt else Panel,
            PkLoadoutInterfaceIds.PremadeScroll,
            clickable = true,
        )
        text(
            PkLoadoutInterfaceIds.premadeText(index),
            "premade_text_$index",
            3,
            index * 15,
            84,
            14,
            "",
            Tan,
            PkLoadoutInterfaceIds.PremadeScroll,
        )
    }

    text(
        PkLoadoutInterfaceIds.CustomTitle,
        "custom_title",
        8,
        148,
        104,
        16,
        "Custom Loadouts",
        Gold,
        centered = true,
        bold = true,
    )
    rect(PkLoadoutInterfaceIds.CustomPanel, "custom_panel", 8, 163, 105, 103, Panel)
    container(
        PkLoadoutInterfaceIds.CustomScroll,
        "custom_scroll",
        10,
        165,
        90,
        99,
        scrollHeight = PkLoadoutInterfaceIds.MaxCustomRows * 15,
    )
    container(PkLoadoutInterfaceIds.CustomScrollbar, "custom_scrollbar", 100, 165, 12, 99)
    repeat(PkLoadoutInterfaceIds.MaxCustomRows) { slot ->
        rect(
            PkLoadoutInterfaceIds.customRow(slot),
            "custom_row_$slot",
            0,
            slot * 15,
            90,
            14,
            if (slot % 2 == 0) PanelAlt else Panel,
            PkLoadoutInterfaceIds.CustomScroll,
            clickable = true,
        )
        text(
            PkLoadoutInterfaceIds.customText(slot),
            "custom_text_$slot",
            3,
            slot * 15,
            84,
            14,
            "",
            Tan,
            PkLoadoutInterfaceIds.CustomScroll,
        )
    }

    text(
        PkLoadoutInterfaceIds.InventoryTitle,
        "inventory_title",
        120,
        29,
        124,
        16,
        "Inventory",
        Gold,
        centered = true,
        bold = true,
    )
    rect(PkLoadoutInterfaceIds.InventoryPanel, "inventory_panel", 120, 44, 124, 222, Panel)
    repeat(28) { slot ->
        val col = slot % 4
        val row = slot / 4
        val x = 124 + (col * 29)
        val y = 49 + (row * 30)
        rect(
            PkLoadoutInterfaceIds.inventoryBackground(slot),
            "inventory_bg_$slot",
            x,
            y,
            27,
            28,
            Slot,
        )
        model(PkLoadoutInterfaceIds.inventoryModel(slot), "inventory_model_$slot", x, y, 27, 27)
        text(
            PkLoadoutInterfaceIds.inventoryCount(slot),
            "inventory_count_$slot",
            x,
            y + 17,
            25,
            10,
            "",
            Gold,
            centered = false,
        )
    }

    text(
        PkLoadoutInterfaceIds.EquipmentTitle,
        "equipment_title",
        250,
        29,
        104,
        16,
        "Equipment",
        Gold,
        centered = true,
        bold = true,
    )
    rect(PkLoadoutInterfaceIds.EquipmentPanel, "equipment_panel", 250, 44, 104, 222, Panel)
    val equipmentPositions =
        listOf(
            1 to 0,
            0 to 1,
            1 to 1,
            0 to 2,
            1 to 2,
            2 to 2,
            1 to 3,
            0 to 4,
            1 to 4,
            2 to 4,
            2 to 1,
        )
    equipmentPositions.forEachIndexed { slot, (col, row) ->
        val x = 256 + (col * 31)
        val y = 49 + (row * 37)
        rect(
            PkLoadoutInterfaceIds.equipmentBackground(slot),
            "equipment_bg_$slot",
            x,
            y,
            28,
            32,
            Slot,
        )
        model(PkLoadoutInterfaceIds.equipmentModel(slot), "equipment_model_$slot", x, y, 28, 28)
        text(
            PkLoadoutInterfaceIds.equipmentCount(slot),
            "equipment_count_$slot",
            x,
            y + 21,
            27,
            10,
            "",
            Gold,
        )
    }

    text(
        PkLoadoutInterfaceIds.StatsTitle,
        "stats_title",
        360,
        29,
        132,
        16,
        "Stats & Spellbook",
        Gold,
        centered = true,
        bold = true,
    )
    rect(PkLoadoutInterfaceIds.StatsPanel, "stats_panel", 360, 44, 132, 222, Panel)
    text(
        PkLoadoutInterfaceIds.SelectedName,
        "selected_name",
        364,
        47,
        124,
        20,
        "Select a loadout",
        Gold,
        centered = true,
        bold = true,
    )
    val statNames =
        listOf("Attack", "Strength", "Defence", "Hitpoints", "Ranged", "Prayer", "Magic")
    statNames.forEachIndexed { index, name ->
        text(
            PkLoadoutInterfaceIds.stat(index),
            "stat_${name.lowercase()}",
            367,
            70 + (index * 18),
            118,
            16,
            "$name: -",
            Pale,
        )
    }
    text(PkLoadoutInterfaceIds.CombatLevel, "combat_level", 367, 198, 118, 15, "Combat: -", Tan)
    text(PkLoadoutInterfaceIds.Spellbook, "spellbook", 367, 213, 118, 15, "Spellbook: -", Tan)
    text(
        PkLoadoutInterfaceIds.SpecialEnergy,
        "special_energy",
        367,
        228,
        118,
        15,
        "Special: -",
        Tan,
    )
    text(
        PkLoadoutInterfaceIds.QuickPrayers,
        "quick_prayers",
        367,
        243,
        118,
        15,
        "Quick prayers: -",
        Tan,
    )
    text(
        PkLoadoutInterfaceIds.Description,
        "description",
        120,
        266,
        372,
        18,
        "",
        Tan,
        centered = true,
    )
    text(PkLoadoutInterfaceIds.Status, "status", 8, 316, 484, 12, "", Tan, centered = true)

    rect(
        PkLoadoutInterfaceIds.DeathToggle,
        "death_toggle",
        8,
        274,
        16,
        16,
        Border,
        clickable = true,
    )
    text(PkLoadoutInterfaceIds.DeathLabel, "death_label", 28, 273, 84, 18, "Open after death", Pale)
    val buttons =
        listOf(
            Triple(
                PkLoadoutInterfaceIds.LoadButton,
                PkLoadoutInterfaceIds.LoadLabel,
                "Load Preset",
            ),
            Triple(PkLoadoutInterfaceIds.SaveButton, PkLoadoutInterfaceIds.SaveLabel, "Save Setup"),
            Triple(PkLoadoutInterfaceIds.RenameButton, PkLoadoutInterfaceIds.RenameLabel, "Rename"),
            Triple(PkLoadoutInterfaceIds.DeleteButton, PkLoadoutInterfaceIds.DeleteLabel, "Delete"),
            Triple(
                PkLoadoutInterfaceIds.DefaultButton,
                PkLoadoutInterfaceIds.DefaultLabel,
                "Set Default",
            ),
        )
    buttons.forEachIndexed { index, (button, label, value) ->
        val x = 120 + (index * 74)
        rect(
            button,
            value.lowercase().replace(' ', '_') + "_button",
            x,
            288,
            70,
            25,
            Border,
            clickable = true,
        )
        text(
            label,
            value.lowercase().replace(' ', '_') + "_label",
            x,
            289,
            70,
            23,
            value,
            Gold,
            centered = true,
            bold = true,
        )
    }
    return result
}

private fun writeGamevals(root: Path, components: Map<Int, ComponentType>) {
    val dir = root.resolve(Paths.get(".data", "gamevals"))
    Files.createDirectories(dir)
    mergeMappings(
        dir.resolve("interface.rscm"),
        "pk_loadouts=",
        "pk_loadouts=${PkLoadoutInterfaceIds.Id}",
    )
    val componentMappings =
        components.entries.joinToString("\n", postfix = "\n") { (_, component) ->
            "pk_loadouts:${component.internalName}=${component.packed}"
        }
    mergeMappings(dir.resolve("component.rscm"), "pk_loadouts:", componentMappings.trimEnd())
}

private fun mergeMappings(path: Path, ownedPrefix: String, replacement: String) {
    val preserved =
        if (Files.exists(path)) Files.readAllLines(path).filterNot { it.startsWith(ownedPrefix) }
        else emptyList()
    val merged =
        (preserved + replacement.lines())
            .filter(String::isNotBlank)
            .joinToString("\n", postfix = "\n")
    Files.writeString(path, merged)
}
