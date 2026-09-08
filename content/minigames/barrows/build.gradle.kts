plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.collectionLog)
    implementation(projects.api.combat.combatScripts)
    implementation(projects.api.script)
    implementation(libs.guice)
    implementation(projects.api.attr)
    implementation(projects.api.bosses)
    implementation(projects.api.death)
    implementation(projects.api.npc)
    implementation(projects.api.player)
    implementation(projects.api.playerOutput)
    implementation(projects.api.utils.utilsLogging)
    implementation(projects.api.utils.utilsVars)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.random)
    implementation(projects.api.repo)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.plugin)
}
