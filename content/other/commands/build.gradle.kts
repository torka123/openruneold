plugins {
    id("base-conventions")
}

dependencies {
    implementation(libs.fastutil)
    implementation(projects.orCache)
    implementation(libs.simmetrics.core)
    implementation(projects.api.areaChecker)
    implementation(projects.api.instances)
    implementation(projects.api.registry)
    implementation(projects.api.db)
    implementation(projects.api.dbGateway)
    implementation(projects.api.mechanics.toxins)
    implementation(projects.api.net)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.specials)
    implementation(projects.api.spellsAutocast)
    implementation(projects.content.interfaces.bank)

    implementation(projects.api.utils.utilsSystem)
    implementation(projects.engine.utilsBits)
}
