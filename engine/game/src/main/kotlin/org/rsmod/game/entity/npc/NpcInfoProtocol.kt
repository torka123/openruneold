package org.rsmod.game.entity.npc

import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.Hitmark

public interface NpcInfoProtocol {
    public fun setSequence(seq: Int, delay: Int)

    public fun setSpotanim(spotanim: Int, delay: Int, height: Int, slot: Int)

    public fun setTinting(
        startTime: Int,
        endTime: Int,
        hue: Int,
        saturation: Int,
        lightness: Int,
        weight: Int,
    )

    public fun setSay(text: String)

    public fun setFacePathingEntity(slot: Int)

    public fun setFaceAngle(angle: Int, instant: Boolean)

    public fun setTransmog(type: Int)

    public fun resetTransmog(originalType: Int)

    /** Replaces body models only; does not change NPC identity, stats or collision. */
    public fun setBodyModels(models: List<Int>)

    public fun resetBodyModels()

    public fun showHeadbar(headbar: Headbar)

    public fun showHitmark(hitmark: Hitmark)

    public fun toggleOps(ops: OpVisibility)

    public fun setHeadIcon(slot: Int, graphic: Int, index: Int)

    /** Overrides the combat level displayed by the client for this NPC. */
    public fun setCombatLevel(level: Int)

    public fun exactMove(
        deltaX1: Int,
        deltaZ1: Int,
        deltaX2: Int,
        deltaZ2: Int,
        delay1: Int,
        delay2: Int,
        direction: Int,
    )

    public fun crawl(deltaX: Int, deltaZ: Int)

    public fun walk(deltaX: Int, deltaZ: Int)

    public fun run(deltaX1: Int, deltaZ1: Int, deltaX2: Int, deltaZ2: Int)

    public fun teleport(x: Int, z: Int, level: Int, jump: Boolean)

    public fun disable()

    public fun hide()

    public fun reveal()

    public fun isActive(): Boolean
}

public data object NoopNpcInfo : NpcInfoProtocol {
    override fun setSequence(seq: Int, delay: Int) {}

    override fun setSpotanim(spotanim: Int, delay: Int, height: Int, slot: Int) {}

    override fun setTinting(
        startTime: Int,
        endTime: Int,
        hue: Int,
        saturation: Int,
        lightness: Int,
        weight: Int,
    ) {}

    override fun setSay(text: String) {}

    override fun setFacePathingEntity(slot: Int) {}

    override fun setFaceAngle(angle: Int, instant: Boolean) {}

    override fun setTransmog(type: Int) {}

    override fun resetTransmog(originalType: Int) {}

    override fun setBodyModels(models: List<Int>) {}

    override fun resetBodyModels() {}

    override fun showHeadbar(headbar: Headbar) {}

    override fun showHitmark(hitmark: Hitmark) {}

    override fun toggleOps(ops: OpVisibility) {}

    override fun setHeadIcon(slot: Int, graphic: Int, index: Int) {}

    override fun setCombatLevel(level: Int) {}

    override fun exactMove(
        deltaX1: Int,
        deltaZ1: Int,
        deltaX2: Int,
        deltaZ2: Int,
        delay1: Int,
        delay2: Int,
        direction: Int,
    ) {}

    override fun crawl(deltaX: Int, deltaZ: Int) {}

    override fun walk(deltaX: Int, deltaZ: Int) {}

    override fun run(deltaX1: Int, deltaZ1: Int, deltaX2: Int, deltaZ2: Int) {}

    override fun teleport(x: Int, z: Int, level: Int, jump: Boolean) {}

    override fun disable() {}

    override fun hide() {}

    override fun reveal() {}

    override fun isActive(): Boolean = false
}
