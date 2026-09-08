plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.playerOutput)
    implementation(projects.api.repo)
    implementation(projects.api.spellsAutocast)
    testImplementation(kotlin("test"))
}
