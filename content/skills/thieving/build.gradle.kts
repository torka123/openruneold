plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.attr)
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.player)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.random)
    implementation(projects.api.repo)
    implementation(projects.engine.game)
    testImplementation(kotlin("test"))
    testImplementation(projects.engine.coroutine)
    testImplementation(projects.engine.events)
    testImplementation(projects.api.invtx)
    testImplementation(projects.api.invStorage)
    testImplementation("org.mockito:mockito-core:5.18.0")
}

tasks.test {
    workingDir = rootProject.projectDir
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}
