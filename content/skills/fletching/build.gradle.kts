plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.player)
    implementation(projects.api.pluginCommons)
    implementation(projects.content.skills.utils)
    implementation(projects.engine.game)
    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    workingDir = rootProject.projectDir
}
