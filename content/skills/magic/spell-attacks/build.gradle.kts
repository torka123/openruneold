plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.combat.combatManager)
    implementation(projects.api.combat.combatFormulas)
    implementation(projects.api.combat.combatScripts)
    implementation(projects.api.death)
    implementation(projects.api.mechanics.toxins)
    implementation(projects.api.npc)
    implementation(projects.api.repo)
    implementation(projects.api.config)
    implementation(projects.api.player)
    implementation(projects.api.playerOutput)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.random)
    implementation(projects.api.registry)
    implementation(projects.api.script)
    implementation(projects.api.specials)
    implementation(projects.api.spells)
    testImplementation(projects.api.spellsAutocast)
    testImplementation(projects.api.spellsRunes)
    testImplementation(projects.api.combat.combatFormulas)
    testImplementation(kotlin("test"))
    testImplementation(projects.engine.events)
    testImplementation(projects.engine.coroutine)
    testImplementation(projects.api.invtx)
    testImplementation(projects.api.invStorage)
    testImplementation(projects.api.gameProcess)
    testImplementation(projects.api.hitPlugin)
    testImplementation(projects.api.route)
    testImplementation(projects.api.utils.utilsLogging)
    testImplementation(libs.fastutil)
    testImplementation(libs.guice)
    testImplementation(libs.rsprot.api)
    testImplementation("org.mockito:mockito-core:5.18.0")
}

tasks.test {
    workingDir = rootProject.projectDir
    maxHeapSize = "2g"
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}
