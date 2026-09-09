package dev.openrune

import java.nio.file.Path

/** Compatibility entry point; all parameter merging uses the shared installer. */
fun main() =
    applyNpcParameterOverlays(
        setOf("npc.cerberus_attacking", "npc.cerberus_sitting", "npc.cerberus_resetting"),
        Path.of("logs/cerberus-audit-20260907/cache-before"),
    )
