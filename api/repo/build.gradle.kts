plugins {
    id("base-conventions")

}

kotlin {
    explicitApi()
}

tasks.test {
    workingDir = rootProject.projectDir
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}

dependencies {
    implementation(libs.guice)
    implementation(libs.fastutil)
    implementation(projects.api.registry)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.routefinder)
    testImplementation(kotlin("test"))
    testImplementation(libs.rsprot.api)
}
