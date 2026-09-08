plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.player)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.random)
    implementation(projects.engine.game)
    testImplementation(kotlin("test"))
}
