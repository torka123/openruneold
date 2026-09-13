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
    implementation(libs.embedded.postgres)
    implementation(libs.openrune.central.common)
    implementation(libs.bundles.logging)
    implementation(libs.guice)
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.postgresql)
    implementation(projects.api.serverConfig)
    implementation(projects.engine.module)
    implementation(projects.server.services)
    testImplementation(kotlin("test"))
    testImplementation("org.mockito:mockito-core:5.18.0")
}
