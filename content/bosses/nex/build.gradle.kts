plugins { id("base-conventions") }

dependencies {
    implementation(libs.guice)
    implementation(projects.api.attr)
    implementation(projects.api.collectionLog)
    implementation(projects.api.bosses)
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.combat.combatScripts)
    implementation(projects.api.death)
    implementation(projects.api.gameProcess)
    implementation(projects.api.instances)
    implementation(projects.api.npc)
    implementation(projects.api.player)
    implementation(projects.api.playerOutput)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.random)
    implementation(projects.api.repo)
    implementation(projects.api.script)
    implementation(projects.api.utils.utilsLogging)
    implementation(projects.api.utils.utilsVars)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.plugin)
    implementation(projects.engine.routefinder)
    testImplementation(kotlin("test"))
    testImplementation("org.mockito:mockito-core:5.18.0")
    testImplementation(projects.api.combat.combatFormulas)
    testImplementation(projects.api.registry)
    testImplementation(libs.fastutil)
    testImplementation(libs.rsprot.api)
    testImplementation(projects.api.hitPlugin)
    testImplementation(projects.engine.coroutine)
    testImplementation(projects.engine.events)
}

tasks.withType<Test>().configureEach {
    workingDir = rootProject.projectDir
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}
