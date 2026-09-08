package org.rsmod.api.attr

/** Cross-module encounter facts consumed while a boss death is rolling rewards. */
public object BossRewardAttributes {
    /** Exact duration of the current Phantom Muspah kill; deliberately not persisted. */
    public val phantomMuspahLastKillTicks: AttributeKey<Int> = AttributeKey()

    /** Set when Charged ice has been consumed on Muphin. */
    public val phantomMuspahChargedIceUsed: AttributeKey<Boolean> =
        AttributeKey(persistenceKey = "phantom_muspah_charged_ice_used")
}
