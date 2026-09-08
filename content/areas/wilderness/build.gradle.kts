plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.areaChecker)
    implementation(projects.api.config)
    implementation(projects.api.death)
    implementation(projects.api.gameProcess)
    implementation(projects.api.player)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.repo)
    implementation(projects.api.random)
    implementation(projects.api.realm)
    implementation(projects.api.registry)
    implementation(projects.api.scriptAdvanced)
    implementation(projects.api.attr)
    implementation(projects.api.collectionLog)
    implementation(projects.api.dropTable)
    implementation(projects.api.dropTablePlugin)
    implementation(projects.engine.events)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.routefinder)
    implementation(projects.content.generic.genericLocs)
    implementation(projects.content.drops)
    testImplementation(kotlin("test"))
}
