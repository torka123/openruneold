plugins { id("base-conventions") }

dependencies {
    implementation(libs.guice)
    implementation(projects.api.instances)
    implementation(projects.api.invtx)
    implementation(projects.api.net)
    implementation(projects.api.player)
    implementation(projects.api.playerOutput)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.repo)
    implementation(projects.api.script)
    implementation(projects.api.serverConfig)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.plugin)
    implementation(projects.engine.routefinder)
    testImplementation(kotlin("test"))
}
