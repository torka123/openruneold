plugins {
    id("base-conventions")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.guice)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    implementation(projects.api.attr)
    implementation(projects.api.playerOutput)
    implementation(projects.engine.game)
    testImplementation(kotlin("test"))
}
