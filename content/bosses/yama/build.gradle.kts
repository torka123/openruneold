plugins { id("base-conventions") }

dependencies {
    implementation(libs.guice)
    implementation(projects.api.attr)
    implementation(projects.api.bosses)
    implementation(project(":api:boss-hp-bar-plugin"))
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.combat.combatScripts)
    implementation(projects.api.combat.combatWeapon)
    implementation(projects.api.death)
    implementation(projects.api.dropTable)
    implementation(projects.api.dropTablePlugin)
    implementation(projects.api.instances)
    implementation(projects.api.invtx)
    implementation(projects.api.market)
    implementation(projects.api.mechanics.toxins)
    implementation(projects.api.npc)
    implementation(projects.api.player)
    implementation(projects.api.playerOutput)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.random)
    implementation(projects.api.specials)
    implementation(projects.api.repo)
    implementation(projects.api.script)
    implementation(projects.api.utils.utilsLogging)
    implementation(projects.content.drops)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.plugin)
    implementation(projects.engine.routefinder)
    testImplementation(projects.api.combat.combatFormulas)
    testImplementation(kotlin("test"))
    testImplementation("org.mockito:mockito-core:5.18.0")
    testImplementation(projects.engine.coroutine)
    testImplementation(projects.engine.events)
}

tasks.withType<Test>().configureEach {
    workingDir = rootProject.projectDir
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}
