plugins { id("base-conventions") }

dependencies {
    implementation(projects.api.account)
    implementation(projects.api.attr)
    implementation(projects.api.db)
    implementation(projects.api.collectionLog)
    implementation(project(":api:boss-hp-bar-plugin"))
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.combat.combatFormulas)
    implementation(projects.api.combat.combatScripts)
    implementation(projects.api.config)
    implementation(projects.api.death)
    implementation(projects.api.cheat)
    implementation(projects.api.gameProcess)
    implementation(projects.api.instances)
    implementation(projects.api.invtx)
    implementation(projects.content.other.consumables)
    implementation(projects.api.mechanics.toxins)
    implementation(projects.api.npc)
    implementation(projects.api.player)
    implementation(projects.api.playerOutput)
    implementation(projects.api.random)
    implementation(projects.api.repo)
    implementation(projects.api.route)
    implementation(projects.api.script)
    implementation(projects.api.specials)
    implementation(projects.api.utils.utilsLogging)
    implementation(projects.engine.events)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.objtx)
    implementation(projects.engine.plugin)
    implementation(projects.engine.routefinder)
    implementation(projects.server.services)
    implementation(libs.guice)
    implementation(libs.rsprot.api)
    implementation("com.michael-bull.kotlin-inline-logger:kotlin-inline-logger:1.0.6")
    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    workingDir = rootProject.projectDir
}
