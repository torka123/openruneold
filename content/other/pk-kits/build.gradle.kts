plugins {
    id("base-conventions")
}

dependencies {
    implementation(libs.fastutil)
    implementation(projects.api.areaChecker)
    implementation(projects.api.attr)
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.combat.combatWeapon)
    implementation(projects.api.config)
    implementation(projects.api.death)
    implementation(projects.api.instances)
    implementation(projects.api.player)
    implementation(projects.api.playerOutput)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.script)
    implementation(projects.api.specials)
    implementation(projects.api.spellsAutocast)
    implementation(projects.content.interfaces.bank)
    implementation(projects.content.interfaces.prayerTab)
    implementation(projects.engine.game)
    implementation(projects.orCache)

    testImplementation(kotlin("test"))
}
