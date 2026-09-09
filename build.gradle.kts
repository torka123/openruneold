plugins {
    alias(libs.plugins.manes.versions)
    alias(libs.plugins.gradle.download)
    id("kotlin-conventions")
}

allprojects {
    group = "org.rsmod"
    version = "0.0.1"
}

dependencies {
    implementation(projects.server.install)
}

val bossArchitectureShell =
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "powershell.exe"
    else "pwsh"

val bossArchitectureSelfTest by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs the existing boss architecture detector's deterministic self-tests."
    workingDir(rootDir)
    commandLine(
        bossArchitectureShell,
        "-NoProfile",
        "-File",
        "scripts/check-boss-architecture.ps1",
        "-SelfTest",
    )
}

val bossArchitectureCheck by tasks.registering(Exec::class) {
    group = "verification"
    description = "Checks boss architecture against explicitly reviewed migration waivers."
    dependsOn(bossArchitectureSelfTest)
    workingDir(rootDir)
    commandLine(
        bossArchitectureShell,
        "-NoProfile",
        "-File",
        "scripts/check-boss-architecture.ps1",
    )
}

tasks.named("check") { dependsOn(bossArchitectureCheck) }

tasks.register("run") {
    group = "application"
    description = "Runs the RS Mod game server"

    dependsOn(":server:app:run")
}

tasks.register("configureOsrsMcp") {
    group = "MCP"
    description =
        "Alias for :tools:osrs-mcp:configureOsrsMcp (interactive menu if -Pclient omitted; same -PdebugMcp / -PdryRun)"
    dependsOn(":tools:osrs-mcp:configureOsrsMcp")
}

tasks.register("updateOsrsMcp") {
    group = "MCP"
    description =
        "Alias for :tools:osrs-mcp:updateOsrsMcp (rebuild install layout + refresh MCP client configs)."
    dependsOn(":tools:osrs-mcp:updateOsrsMcp")
}

tasks.register("removeOsrsMcp") {
    group = "MCP"
    description =
        "Alias for :tools:osrs-mcp:removeOsrsMcp (strip MCP entries, optional Claude global + install cleanup)."
    dependsOn(":tools:osrs-mcp:removeOsrsMcp")
}

tasks.register("runMcp") {
    group = "MCP"
    description = "Runs the osrs-mcp stdio MCP server (alias for :tools:osrs-mcp:runMcp)."
    dependsOn(":tools:osrs-mcp:runMcp")
}

tasks.register<JavaExec>("install") {
    group = "installation"
    description = "Runs the complete RS Mod server installation task."

    mainClass.set("org.rsmod.server.install.GameServerInstallKt")
    classpath = sourceSets["main"].runtimeClasspath

    dependsOn(":or-cache:freshCache")

    doLast {
        copy {
            into(rootProject.projectDir)
            from("game.example.yml") {
                rename { "game.yml" }
            }
        }
        logger.lifecycle("Installation process completed.")
    }
}

tasks.register<JavaExec>("cleanInstall") {
    group = "installation"
    description = "Cleans up any partial or corrupted artifacts from previous RS Mod installations."

    args = getArgsFromProperty("rsmodInstallClean")
    mainClass.set("org.rsmod.server.install.GameServerCleanInstallKt")
    classpath = sourceSets["main"].runtimeClasspath

    doFirst { logger.lifecycle("Starting clean up of any previous installation attempts...") }
    doLast { logger.lifecycle("Clean-up process completed. You can now run the `install` task.") }

    finalizedBy("install")
}

tasks.register<JavaExec>("generateRsa") {
    group = "security"
    description =
        "Generates RSA network keys when .data/game.key or .data/client.key is missing."

    val gameKey = layout.projectDirectory.file(".data/game.key")
    val clientKey = layout.projectDirectory.file(".data/client.key")
    onlyIf { !gameKey.asFile.isFile || !clientKey.asFile.isFile }

    args = getArgsFromProperty("rsa")
    mainClass.set("org.rsmod.server.install.GameNetworkRsaGeneratorKt")
    classpath = sourceSets["main"].runtimeClasspath

    doFirst { logger.lifecycle("Starting the rsa-key generation process...") }
    doLast { logger.lifecycle("RSA generation process completed.") }
}

tasks.register<JavaExec>("setupLogbackNovice") {
    description = "Copies the novice logback configuration."

    mainClass.set("org.rsmod.server.install.GameServerLogbackCopyKt")
    classpath = sourceSets["main"].runtimeClasspath

    doFirst { logger.lifecycle("Starting logback copy for novice configuration...") }
    doLast { logger.lifecycle("Logback novice configuration copied successfully.") }
}

tasks.register<JavaExec>("setupLogbackAdvanced") {
    description = "Copies the novice logback configuration."

    mainClass.set("org.rsmod.server.install.GameServerLogbackCopyKt")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf("--advanced-logback")

    doFirst { logger.lifecycle("Starting logback copy for advanced configuration...") }
    doLast { logger.lifecycle("Logback advanced configuration copied successfully.") }
}

fun getArgsFromProperty(propertyName: String): List<String> {
    val argsProp = project.findProperty(propertyName)
    return argsProp?.toString()?.split(" ") ?: emptyList()
}
