plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.account)
    implementation(projects.api.attr)
    implementation(projects.api.db)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.registry)
    implementation(libs.fastutil)
    testImplementation(libs.embedded.postgres)
    testImplementation(kotlin("test"))
    testImplementation(libs.openrune.central.common)
    testImplementation(projects.api.net)
    testImplementation(projects.api.parsers.jackson)
    testImplementation(projects.api.parsers.json)
    testImplementation(projects.engine.module)
}

tasks.withType<Test>().configureEach {
    workingDir = rootProject.projectDir
}
