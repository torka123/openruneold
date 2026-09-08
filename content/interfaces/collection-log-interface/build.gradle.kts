plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.attr)
    implementation(projects.api.collectionLog)
    implementation(projects.api.config)
    implementation(projects.api.invtx)
    implementation(projects.api.player)
    implementation(projects.api.playerOutput)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.script)
    implementation(projects.engine.game)
    implementation(projects.orCache)

    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    workingDir = rootProject.projectDir
}
