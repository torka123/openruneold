plugins { id("base-conventions") }

dependencies {
    implementation(libs.guice)
    implementation(libs.or2.all.cache)
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.combat.combatFormulas)
    implementation(projects.api.death)
    implementation(projects.api.instances)
    implementation(projects.api.npc)
    implementation(projects.api.player)
    implementation(projects.api.playerOutput)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.random)
    implementation(projects.api.registry)
    implementation(projects.api.repo)
    implementation(projects.api.route)
    implementation(projects.api.script)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.plugin)
    implementation(projects.engine.routefinder)
    testImplementation(kotlin("test"))
    testImplementation("org.mockito:mockito-core:5.18.0")
    testImplementation(libs.fastutil)
    testImplementation(libs.rsprot.api)
    testImplementation(projects.engine.events)
    testImplementation(projects.engine.coroutine)
    testImplementation(projects.api.gameProcess)
    testImplementation(projects.api.combat.combatWeapon)
}

tasks.test {
    workingDir = rootProject.projectDir
    // Allocated-region fixtures each own a full collision map and native instance lifecycle.
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}
