plugins {
    id("base-conventions")
}

// The area plugin is also named "wilderness". Keep this module's Gradle
// capability and jar name distinct so both plugins remain on the runtime classpath.
group = "org.rsmod.content.bosses"
base.archivesName.set("wilderness-bosses")

dependencies {
    implementation(libs.guice)
    implementation(projects.api.areaChecker)
    implementation(projects.api.bosses)
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.combat.combatFormulas)
    implementation(projects.api.combat.combatScripts)
    implementation(projects.api.config)
    implementation(projects.api.death)
    implementation(projects.api.gameProcess)
    implementation(projects.api.npc)
    implementation(projects.api.objCharges)
    implementation(projects.api.instances)
    implementation(projects.api.player)
    implementation(projects.api.playerOutput)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.random)
    implementation(projects.api.repo)
    implementation(projects.api.registry)
    implementation(projects.api.route)
    implementation(projects.api.script)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.routefinder)
    implementation(projects.engine.plugin)
    implementation(projects.content.areas.wilderness)
    testImplementation(kotlin("test"))
}
