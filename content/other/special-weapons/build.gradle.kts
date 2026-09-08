plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.combat.combatManager)
    implementation(projects.api.mechanics.toxins)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.scriptAdvanced)
    implementation(projects.api.player)
    implementation(projects.api.random)
    implementation(projects.api.repo)
    implementation(projects.api.weapons)
    implementation(projects.engine.utilsBits)
    testImplementation(kotlin("test"))
}
