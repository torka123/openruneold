package org.rsmod.api.bossbar.plugin

import java.awt.Color

public data class BossHpBarStyle(
    public val inner: Color,
    public val back: Color,
    public val sliding: Color,
    public val remaining: Color = sliding,
    public val forceExactValues: Boolean = false,
)
