plugins {
    id("base-conventions")
}

base.archivesName.set("daily-adventure-system")

dependencies {
    implementation(projects.api.areaChecker)
    implementation(projects.api.attr)
    implementation(projects.api.combat.combatWeapon)
    implementation(projects.api.config)
    implementation(project(":api:daily-adventure"))
    implementation(projects.api.death)
    implementation(projects.api.gameProcess)
    implementation(projects.api.invtx)
    implementation(projects.api.market)
    implementation(projects.api.npc)
    implementation(projects.api.player)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.shops)
    implementation(projects.engine.game)
    implementation(libs.bundles.logging)
    testImplementation(kotlin("test"))
}
