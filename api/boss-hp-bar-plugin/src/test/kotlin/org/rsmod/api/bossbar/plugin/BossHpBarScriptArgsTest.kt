package org.rsmod.api.bossbar.plugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BossHpBarScriptArgsTest {
    @Test
    fun `component ids are flattened before the trailing script value`() {
        val components = intArrayOf(101, 202, 303)

        val args = bossHpBarScriptArgs(components, 255)

        assertEquals(listOf(101, 202, 303, 255), args)
        assertTrue(args.all { it is Int })
    }

    @Test
    fun `empty components still preserve the trailing script value`() {
        assertEquals(listOf(1), bossHpBarScriptArgs(intArrayOf(), 1))
    }
}
