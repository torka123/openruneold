package org.rsmod.content.skills.sailing

import dev.openrune.ServerCacheManager
import dev.openrune.gamevals.GameValProvider
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.parallel.ResourceLock

@ResourceLock("sailing-server-cache")
class SailingDockDefinitionRegistryTest {
    @Test
    fun `all revision-240 dock metadata is typed and uniquely indexed`() {
        val root = Path.of("").toAbsolutePath()
        GameValProvider.load("$root${File.separator}")
        val cache = ServerCacheManager.init(root.resolve(".data/cache/SERVER"), REVISION)
        try {
            val registry = SailingDockDefinitionRegistry()
            assertEquals(SailingDockDefinitionRegistry.EXPECTED_DOCK_COUNT, registry.all.size)
            assertEquals(
                SailingDockDefinitionRegistry.EXPECTED_DOCK_IDS.toList(),
                registry.all.map(SailingDockDefinition::dockId),
            )
            assertEquals(
                setOf(SailingDockDefinitionRegistry.TABLE_ID),
                registry.all.map(SailingDockDefinition::tableId).toSet(),
            )
            for (definition in registry.all) {
                assertEquals(definition, registry.byDockId(definition.dockId))
                assertEquals(definition, registry.byRowId(definition.rowId))
                assertEquals(true, definition in registry.named(definition.displayName.uppercase()))
                assertEquals(true, definition.displayName.isNotBlank())
                assertEquals(true, definition.levelRequired in 1..99)
                assertEquals(true, definition.smallSpriteId >= 0)
            }
            assertNull(registry.byDockId(-1))
            assertNull(registry.byDockId(SailingDockDefinitionRegistry.EXPECTED_DOCK_COUNT))
            assertNull(registry.byRowId(-1))
            assertEquals(emptyList(), registry.named("not a revision-240 dock"))
        } finally {
            cache.close()
        }
    }

    private companion object {
        private const val REVISION = 240
    }
}
