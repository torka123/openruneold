plugins {
    id("base-conventions")
}

dependencies {
    implementation(libs.guice)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.combat.combatFormulas)
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.combat.combatScripts)
    implementation(projects.api.npc)
    implementation(projects.api.player)
    implementation(projects.api.random)
    implementation(projects.api.repo)
    implementation(projects.api.script)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.plugin)
    implementation(projects.engine.coroutine)
    testImplementation(kotlin("test"))
    testImplementation("org.mockito:mockito-core:5.18.0")
    testImplementation(projects.engine.events)
    testImplementation(projects.engine.routefinder)
    testImplementation(projects.api.config)
}

tasks.test {
    workingDir = rootProject.projectDir
    // Native combat fixtures load the complete revision-240 definitions and protocol adapters.
    maxHeapSize = "1g"
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}
