plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.attr)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.scriptAdvanced)
    implementation(projects.content.interfaces.bank)
    testImplementation(kotlin("test"))
    testImplementation(projects.api.net)
    testImplementation(projects.api.objPlugin)
    testImplementation(projects.api.invStorage)
    testImplementation(projects.api.spellsRunes)
    testImplementation(projects.api.specials)
    testImplementation(projects.api.account)
    testImplementation(projects.content.areas.wilderness)
    testImplementation(libs.rsprot.api)
    testImplementation(libs.fastutil)
    testImplementation("org.mockito:mockito-core:5.18.0")
}

tasks.withType<Test>().configureEach {
    workingDir = rootProject.projectDir
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}
