plugins {
    id("base-conventions")
}

dependencies {
    implementation(libs.rsprot.api)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    implementation(projects.api.pluginCommons)
    implementation(projects.content.interfaces.bank)
    testImplementation(kotlin("test"))
    testImplementation(projects.api.net)
    testImplementation(projects.api.gameProcess)
    testImplementation(projects.api.invStorage)
    testImplementation(projects.api.utils.utilsLogging)
    testImplementation(libs.fastutil)
    testImplementation("net.rsprot:osrs-240-desktop:${libs.versions.rsprot.get()}")
    testImplementation("org.mockito:mockito-core:5.18.0")
}

tasks.withType<Test>().configureEach {
    workingDir = rootProject.projectDir
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}
