plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.attr)
    implementation(projects.api.config)
    implementation(projects.api.player)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.random)
    implementation(projects.api.repo)
    implementation(projects.api.route)
    implementation(projects.engine.game)
    testImplementation(kotlin("test"))
    testImplementation(libs.fastutil)
    testImplementation(projects.api.gameProcess)
    testImplementation(projects.api.registry)
    testImplementation(projects.engine.coroutine)
    testImplementation(projects.engine.events)
    testImplementation("org.mockito:mockito-core:5.18.0")
}

tasks.withType<Test>().configureEach {
    workingDir = rootProject.projectDir
    // ServerCacheManager's global decoders are not safe to initialize concurrently.
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
    // Decode the complete active map alongside the native interaction fixture.
    maxHeapSize = "4g"
    forkEvery = 1
}
