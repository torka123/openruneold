import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.shadow)
    id("base-conventions")
    application
}

application {
    mainClass.set("org.rsmod.server.app.GameServerKt")
    applicationDefaultJvmArgs = listOf("-XX:AutoBoxCacheMax=65535", "-Xms1g")
}

dependencies {
    implementation(libs.bundles.logging)
    implementation(libs.clikt)
    implementation(libs.guice)
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.openrs2.cache)
    implementation(projects.api.cache)
    implementation(projects.api.core)
    implementation(projects.api.gameProcess)
    implementation(projects.api.mechanics.toxins)
    implementation(projects.api.invPlugin)
    implementation(projects.api.net)
    implementation(projects.api.objPlugin)
    implementation(projects.api.parsers.jackson)
    implementation(projects.api.parsers.json)
    implementation(projects.api.parsers.toml)
    implementation(projects.api.registry)
    implementation(projects.api.shops)
    implementation(projects.engine.annotations)
    implementation(projects.engine.events)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.module)
    implementation(projects.engine.routefinder)
    implementation(projects.engine.plugin)
    implementation(projects.server.install)
    implementation(projects.server.logging)
    implementation(projects.server.services)
    implementation(projects.server.shared)
    testImplementation(kotlin("test"))
}

tasks.named<JavaExec>("run") {
    description = "Runs the RS Mod game server"
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("generateWildernessInteractionAudit") {
    group = "verification"
    description = "Loads the production cache/map/scripts and writes the Wilderness interaction audit manifests"
    dependsOn(tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.rsmod.server.app.WildernessInteractionAuditMainKt")
    workingDir = rootProject.projectDir
    providers.gradleProperty("auditArgs").orNull
        ?.split(',')
        ?.filter(String::isNotBlank)
        ?.let(::args)
    providers.gradleProperty("auditArgsAt").orNull
        ?.split('@')
        ?.filter(String::isNotBlank)
        ?.let(::args)
}

tasks.register<JavaExec>("generateHouseTest2Audit") {
    group = "verification"
    description =
        "Loads production cache/map/scripts and writes the HouseTest2 interaction audit"
    dependsOn(tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.rsmod.server.app.HouseTest2AuditMainKt")
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("generateArmourAudit") {
    group = "verification"
    description = "Loads the production revision caches and writes raw-ID armour audit manifests"
    dependsOn(tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.rsmod.server.app.ArmourAuditMainKt")
    workingDir = rootProject.projectDir
}

tasks.named<ShadowJar>("shadowJar") {
    archiveFileName.set("server.jar")
    isZip64 = true
    mergeServiceFiles()
}
