package org.rsmod.content.bosses.yama

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YamaFlareMeteorFixtureTest {
    @Test
    fun captureFixtureContainsBothPriorityStreamsAndNoDeveloperPath() {
        val text =
            checkNotNull(javaClass.getResource("/yama/rev235-flare-meteor-fixtures.csv")).readText()
        assertTrue("rsprox-3386-rev235,meteor_launch,246,14182" in text)
        assertTrue("rsprox-3386-rev235,meteor_impact,250,3272" in text)
        assertTrue("rsprox-3347-rev235,voidflare_spawn,1349,14179" in text)
        assertTrue("rsprox-3347-rev235,voidflare_deadline,1352,75" in text)
        assertTrue("endtime 600" in text)
        assertFalse("Downloads" in text || "Users/norma" in text)
    }
}
