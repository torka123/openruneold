plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.config)
    implementation(projects.api.player)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.random)
    implementation(projects.api.repo)
    implementation(projects.engine.game)
    testImplementation(kotlin("test"))
    testImplementation(libs.fastutil)
}

tasks.withType<Test>().configureEach {
    workingDir = rootProject.projectDir
}
