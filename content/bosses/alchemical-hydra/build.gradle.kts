plugins { id("base-conventions") }

dependencies {
    implementation(libs.guice)
    implementation(projects.api.bosses)
    implementation(projects.api.combat.combatScripts)
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.combat.combatFormulas)
    implementation(projects.api.death)
    implementation(projects.api.instances)
    implementation(projects.api.mechanics.toxins)
    implementation(projects.api.npc)
    implementation(projects.api.player)
    implementation(projects.api.playerOutput)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.random)
    implementation(projects.api.repo)
    implementation(projects.api.script)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.plugin)
    implementation(projects.engine.routefinder)
    testImplementation(kotlin("test"))
    testImplementation("org.mockito:mockito-core:5.18.0")
    testImplementation(projects.engine.coroutine)
    testImplementation(projects.engine.events)
    testImplementation(projects.api.registry)
    testImplementation(projects.api.deathPlugin)
    testImplementation(projects.content.generic.killcount)
}

tasks.test {
    workingDir = rootProject.projectDir
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}
