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
    testImplementation(projects.api.net)
    testImplementation(projects.engine.coroutine)
    testImplementation(projects.engine.events)
    testImplementation(libs.rsprot.api)
    testImplementation("net.rsprot:osrs-240-desktop:${libs.versions.rsprot.get()}")
    testImplementation("org.mockito:mockito-core:5.18.0")
}

tasks.withType<Test>().configureEach {
    workingDir = rootProject.projectDir
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}
