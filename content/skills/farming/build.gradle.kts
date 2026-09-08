plugins {
    id("base-conventions")
}

dependencies {
    implementation(libs.guice)
    implementation(projects.api.attr)
    implementation(projects.api.combat.combatManager)
    implementation(projects.api.invtx)
    implementation(projects.api.player)
    implementation(projects.api.playerOutput)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.random)
    implementation(projects.api.script)
    implementation(projects.api.spells)
    implementation(projects.api.stats.xpmod)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    testImplementation(kotlin("test"))
    testImplementation(projects.api.spellsRunes)
    testImplementation(projects.api.invStorage)
    testImplementation(projects.engine.events)
    testImplementation(projects.engine.coroutine)
    testImplementation("org.mockito:mockito-core:5.18.0")
}

tasks.test {
    workingDir = rootProject.projectDir
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}
