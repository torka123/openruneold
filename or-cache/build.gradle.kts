plugins {
    id("base-conventions")
}

dependencies {
    // Source: https://mvnrepository.com/artifact/com.google.code.gson/gson
    implementation("com.google.code.gson:gson:2.13.2")
    implementation(libs.kotlin.coroutines.core)

    implementation(rootProject.libs.or2.all.cache)
    implementation(rootProject.project.libs.or2.tools)
    implementation(rootProject.project.libs.or2.server.utils)
    api(libs.or2.definition)
    api(libs.or2.filestore)
    api(libs.or2.filesystem)
    implementation(projects.engine.map)
    implementation(projects.engine.routefinder)
    implementation("com.michael-bull.kotlin-inline-logger:kotlin-inline-logger:1.0.6")
    implementation("com.squareup:kotlinpoet:2.2.0")
    implementation("me.tongfei:progressbar:0.9.2")
    implementation("io.netty:netty-buffer:4.1.107.Final")
    implementation(libs.jackson.dataformat.toml)
    implementation(libs.jackson.databind)
    implementation("dev.or2:toml-rsconfig:1.0")
    implementation(libs.fastutil)
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    workingDir = rootProject.projectDir
    // ServerCacheManager owns shared mutable definition stores and cannot be initialized safely by
    // multiple test classes at once. Keep cache-backed audits deterministic.
    systemProperty("junit.jupiter.execution.parallel.enabled", false)
}

tasks {
    register<JavaExec>("installNpcParameters") {
        group = "cache"
        description = "Explicitly installs selected named NPC overlays into SERVER; never regenerates source"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.NpcParameterConfigInstallerKt")
        workingDir = rootProject.projectDir
        dependsOn("classes")
        doFirst {
            val names = providers.gradleProperty("npcParameterNames").get().split(',')
            require(names.isNotEmpty() && names.all { it.startsWith("npc.") })
            args(names)
        }
    }
    register<JavaExec>("installGrandExchangeConfig") {
        group = "cache"
        description = "Installs only GE staff movement, music row/index and area into SERVER"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.GrandExchangeConfigInstallerKt")
        workingDir = rootProject.projectDir
        dependsOn("classes")
    }
    register<JavaExec>("installLootingBagConfig") {
        group = "cache"
        description = "Clears only the looting-bag modal transaction block in the SERVER cache"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.LootingBagConfigInstallerKt")
        workingDir = rootProject.projectDir
        dependsOn("classes")
    }
    register<JavaExec>("installArceuusSpellAccess") {
        group = "openrune"
        description = "Removes only Arceuus spell quest locks from the existing revision-240 caches"
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("dev.openrune.ArceuusSpellAccessInstallerKt")
        workingDir = rootProject.projectDir
    }
    register<JavaExec>("installCerberusConfig") {
        group = "cache"
        description = "Applies only canonical Cerberus NPC parameters to the existing SERVER cache"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.CerberusConfigInstallerKt")
        workingDir = rootProject.projectDir
        dependsOn("classes")
    }

    register<JavaExec>("installRoyalTitansConfig") {
        group = "cache"
        description = "Applies canonical Royal Titans NPC parameters to SERVER only; no source regeneration"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.NpcParameterConfigInstallerKt")
        args("npc.rt_fire_queen", "npc.rt_ice_king",
            "npc.rt_summon_elemental_fire", "npc.rt_summon_elemental_ice")
        workingDir = rootProject.projectDir
        dependsOn("classes")
    }

    register("buildCache",JavaExec::class) {
        group = "cache"
        description = "Build Cache"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.CacheToolsKt")
        args = listOf("BUILD")
    }

    register("freshCache",JavaExec::class) {
        group = "cache"
        description = "Fresh Install Cache"

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.CacheToolsKt")
        args = listOf("FRESH_INSTALL")
    }

    register<JavaExec>("mergePluginGamevals") {
        group = "cache"
        description =
            "Writes plugin gamevals.toml entries into .data/gamevals/*.rscm (used by release CI)"

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.gamevals.PluginGamevalMergerKt")
        workingDir = rootProject.projectDir
        dependsOn("classes")
    }

    register<JavaExec>("generateCollectionLogCatalog") {
        group = "verification"
        description =
            "Generates the revision-240 Collection Log catalog and Wiki mismatch report"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.collectionlog.CollectionLogCatalogGeneratorKt")
        workingDir = rootProject.projectDir
        dependsOn("classes")
        val output =
            rootProject.layout.projectDirectory.dir("api/collection-log/src/main/resources/collection-log")
        val evidence = rootProject.layout.projectDirectory.dir("docs/collection-log/source")
        args(output.asFile.absolutePath, evidence.asFile.absolutePath)
    }

    register<JavaExec>("buildPkLoadoutInterface") {
        group = "cache"
        description = "Build the collision-free PK loadout interface in the server cache"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.PkLoadoutInterfaceBuilderKt")
        workingDir = rootProject.projectDir
        dependsOn("classes")
    }

    register<JavaExec>("buildWildernessHudInterface") {
        group = "cache"
        description =
            "Adds non-flashing, native-font Wilderness level components to interface 90"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.WildernessHudInterfaceBuilderKt")
        workingDir = rootProject.projectDir
        dependsOn("classes")
    }

    register<JavaExec>("buildTobEntranceOption") {
        group = "cache"
        description = "Adds the party-board option to the Theatre entrance in LIVE and SERVER"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.TobEntranceOptionBuilderKt")
        workingDir = rootProject.projectDir
        dependsOn("classes")
    }

    register<JavaExec>("installFoundryKovacAnimations") {
        group = "cache"
        description = "Repairs the Giants' Foundry Kovac activity form movement animations"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.KovacAnimationCacheInstallerKt")
        workingDir = rootProject.projectDir
        dependsOn("classes")
    }
    register<JavaExec>("auditCoxCaches") {
        group = "verification"
        description = "Builds the read-only Reason-to-revision-240 Chambers of Xeric cache audit"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.audit.CoxCacheAuditMainKt")
        workingDir = rootProject.projectDir
        dependsOn("classes")
        doFirst {
            val source = project.findProperty("coxSource")?.toString()
                ?: error("Missing -PcoxSource=<Reason cache path>")
            val stock = project.findProperty("coxStock")?.toString()
                ?: error("Missing -PcoxStock=<stock build-229 cache path>")
            val donorSource = project.findProperty("coxDonorSource")?.toString()
                ?: error("Missing -PcoxDonorSource=<Reason Xeric source path>")
            val output = project.findProperty("coxAuditOutput")?.toString()
                ?: rootProject.layout.projectDirectory.dir("docs/raids/cox").asFile.absolutePath
            args(source, stock, rootProject.layout.projectDirectory.dir(".data/cache/LIVE").asFile.absolutePath, donorSource, output)
        }
    }

    register<JavaExec>("auditTobCaches") {
        group = "verification"
        description = "Builds the read-only Reason-to-revision-240 Theatre of Blood cache audit"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.audit.tob.TobCacheAuditMainKt")
        workingDir = rootProject.projectDir
        dependsOn("classes")
        doFirst {
            val source = project.findProperty("tobSource")?.toString()
                ?: error("Missing -PtobSource=<Reason cache path>")
            val stock = project.findProperty("tobStock")?.toString()
                ?: error("Missing -PtobStock=<stock build-229 cache path>")
            val donorSource = project.findProperty("tobDonorSource")?.toString()
                ?: error("Missing -PtobDonorSource=<Reason Theatre source path>")
            val output = project.findProperty("tobAuditOutput")?.toString()
                ?: rootProject.layout.projectDirectory.dir("docs/raids/tob").asFile.absolutePath
            args(source, stock, rootProject.layout.projectDirectory.dir(".data/cache/LIVE").asFile.absolutePath, donorSource, output)
        }
    }
    register<JavaExec>("auditToaCaches") {
        group = "verification"
        description = "Builds the read-only Reason-to-revision-240 Tombs of Amascut cache audit"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.audit.toa.ToaCacheAuditMainKt")
        workingDir = rootProject.projectDir
        dependsOn("classes")
        doFirst {
            val source = project.findProperty("toaSource")?.toString()
                ?: error("Missing -PtoaSource=<Reason cache path>")
            val stock = project.findProperty("toaStock")?.toString()
                ?: error("Missing -PtoaStock=<stock build-229 cache path>")
            val donorSource = project.findProperty("toaDonorSource")?.toString()
                ?: error("Missing -PtoaDonorSource=<Reason Tombs source path>")
            val output = project.findProperty("toaAuditOutput")?.toString()
                ?: rootProject.layout.projectDirectory.dir("docs/raids/toa").asFile.absolutePath
            args(source, stock, rootProject.layout.projectDirectory.dir(".data/cache/LIVE").asFile.absolutePath, donorSource, output)
        }
    }
    register<JavaExec>("queryToaDefinitions") {
        group = "verification"
        description = "Queries decoded revision-240 definitions while implementing Tombs of Amascut"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.audit.toa.ToaDefinitionQueryMainKt")
        workingDir = rootProject.projectDir
        dependsOn("classes")
        doFirst {
            val cache = project.findProperty("toaQueryCache")?.toString()
                ?: rootProject.layout.projectDirectory.dir(".data/cache/LIVE").asFile.absolutePath
            val query = project.findProperty("toaQuery")?.toString().orEmpty()
            args(listOf(cache, "240") + query.split(',').filter(String::isNotBlank))
        }
    }
    register<JavaExec>("queryToaClientScripts") {
        group = "verification"
        description = "Prints revision-240 ToA client-script argument signatures"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.audit.toa.ToaClientScriptQueryMainKt")
        workingDir = rootProject.projectDir
        dependsOn("classes")
        doFirst {
            val cache = project.findProperty("toaQueryCache")?.toString()
                ?: rootProject.layout.projectDirectory.dir(".data/cache/LIVE").asFile.absolutePath
            val query = project.findProperty("toaQuery")?.toString().orEmpty()
            args(listOf(cache) + query.split(',').filter(String::isNotBlank))
        }
    }

    register<JavaExec>("installRaidLogoutInterface") {
        group = "cache"
        description = "Copies interface 1200 into the SERVER and LIVE runtime caches"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.RaidLogoutInterfaceInstallerKt")
        workingDir = rootProject.projectDir
        dependsOn("classes")
        doFirst {
            val source =
                project.findProperty("raidLogoutInterfaceSource")?.toString()
                    ?: error("Missing -PraidLogoutInterfaceSource=<prepared revision-240 cache>")
            args(
                source,
                rootProject.layout.projectDirectory.dir(".data/cache/SERVER").asFile.absolutePath,
                rootProject.layout.projectDirectory.dir(".data/cache/LIVE").asFile.absolutePath,
            )
        }
    }

    register<JavaExec>("buildDailyAdventureInterface") {
        group = "cache"
        description = "Builds the native revision-240 Daily Adventure Paths interface"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.DailyAdventureInterfaceBuilderKt")
        workingDir = rootProject.projectDir
        dependsOn("classes")
    }

    register<JavaExec>("auditDailyAdventureInterface") {
        group = "verification"
        description = "Read-only collision audit for Daily Adventure interface 970"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.DailyAdventureInterfaceAuditKt")
        workingDir = rootProject.projectDir
        dependsOn("classes")
    }

    register<JavaExec>("auditNativeAdventurePathInterface") {
        group = "verification"
        description = "Prints revision-240 native Adventure Paths interface components"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.NativeAdventurePathInterfaceAuditKt")
        workingDir = rootProject.projectDir
        dependsOn("classes")
    }

    register<JavaExec>("installNativeDailyAdventureInterface") {
        group = "cache"
        description = "Restores stock interface 642 by removing the retired Daily task overlay"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("dev.openrune.NativeDailyAdventureInterfaceInstallerKt")
        workingDir = rootProject.projectDir
        dependsOn("classes")
    }
}
