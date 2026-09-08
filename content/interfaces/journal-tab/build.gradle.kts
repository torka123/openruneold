plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(project(":content:systems:daily-adventure"))
    testImplementation(kotlin("test"))
}
