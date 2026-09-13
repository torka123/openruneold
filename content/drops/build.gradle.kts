plugins {
    id("base-conventions")
}

tasks.test {
    workingDir = rootProject.projectDir
    // Cache-backed tests share ServerCacheManager and its decoders within the test JVM.
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}

dependencies {
    implementation(libs.guice)
    implementation(projects.api.areaChecker)
    implementation(projects.api.attr)
    implementation(projects.api.collectionLog)
    implementation(projects.api.config)
    implementation(projects.api.death)
    implementation(projects.api.dropTable)
    implementation(projects.api.dropTablePlugin)
    implementation(projects.api.market)
    implementation(projects.api.net)
    implementation(projects.api.player)
    implementation(projects.api.playerOutput)
    implementation(projects.api.random)
    implementation(projects.api.repo)
    implementation(projects.content.skills.slayer)
    implementation(projects.content.quest)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.plugin)
    testImplementation(kotlin("test"))
    testImplementation("org.mockito:mockito-core:5.18.0")
    testImplementation(libs.rsprot.api)
}
