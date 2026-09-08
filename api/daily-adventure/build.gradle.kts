plugins {
    id("base-conventions")
}
// Keep this API's Gradle capability distinct from the same-named content module.
group = "org.rsmod.api"

kotlin {
    explicitApi()
}

dependencies { testImplementation(kotlin("test")) }
