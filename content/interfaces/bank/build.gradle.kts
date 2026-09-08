plugins {
    id("base-conventions")

}

dependencies {
    implementation(projects.api.attr)
    implementation(projects.api.specials)
    implementation(projects.api.pluginCommons)
}
