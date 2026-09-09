plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.combat.combatManager)
    implementation(projects.api.player)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.realm)
    implementation(projects.api.script)
    implementation(projects.api.spells)
    implementation(projects.api.spellsRunes)
    implementation(projects.content.quest)
    implementation(projects.content.skills.construction)
    testImplementation(kotlin("test"))
    testImplementation(projects.engine.coroutine)
    testImplementation(projects.engine.events)
    testImplementation(projects.api.registry)
    testImplementation(libs.guice)
    testImplementation(libs.fastutil)
    testImplementation(projects.api.invtx)
    testImplementation(projects.api.invStorage)
    testImplementation(projects.api.net)
    testImplementation(projects.api.gameProcess)
    testImplementation(projects.api.spellsAutocast)
    testImplementation("net.rsprot:osrs-240-desktop:${libs.versions.rsprot.get()}")
    testImplementation("org.mockito:mockito-core:5.18.0")
}

tasks.test {
    workingDir = rootProject.projectDir
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}
