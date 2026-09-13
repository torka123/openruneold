import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Properties

rootProject.name = "OpenRune-Server"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

val centralServerBuild = providers.gradleProperty("openrune.central.includeBuild")
if (centralServerBuild.isPresent) {
    val pinnedCommit = "9d718ca61aa6437007f883f27c35126fac5a0558"
    val officialRemote = "https://github.com/OpenRune/OpenRune-Central-Server.git"
    val setupAdvice =
        "Run powershell -NoProfile -ExecutionPolicy Bypass -File scripts/setup-openrune-central-s10.ps1 " +
            "from the project root. Setup preserves existing changes and reports conflicts."
    fun requireCentral(condition: Boolean, message: String) {
        check(condition) { "Central S10 source checkout is not ready: $message $setupAdvice" }
    }
    fun normalizePatch(value: String): String = value.replace("\r\n", "\n").trimEnd('\n')

    val requestedPath = centralServerBuild.get()
    requireCentral(requestedPath.isNotBlank(), "openrune.central.includeBuild must name a checkout.")
    val canonicalCheckout = file(".gradle/openrune-central-s10").absoluteFile.normalize()
    val selectedCheckout = file(requestedPath).absoluteFile.normalize()
    val checkout = selectedCheckout.canonicalFile
    val isCanonicalCheckout = selectedCheckout == canonicalCheckout
    requireCentral(
        !isCanonicalCheckout || checkout == canonicalCheckout,
        "The canonical checkout must not resolve through a symlink or junction.",
    )
    requireCentral(checkout.isDirectory, "Missing checkout: $checkout.")
    requireCentral(File(checkout, ".git").isDirectory, "Expected a standalone Git checkout: $checkout.")

    fun centralGit(vararg arguments: String): String {
        val execution =
            providers.exec {
                commandLine(
                    listOf("git", "--no-optional-locks", "-C", checkout.path, "-c", "core.quotePath=false") +
                        arguments,
                )
                isIgnoreExitValue = true
            }
        requireCentral(
            execution.result.get().exitValue == 0,
            "Git validation failed (${arguments.joinToString(" ")}): " +
                execution.standardError.asText.get().trim(),
        )
        return execution.standardOutput.asText.get().trimEnd('\r', '\n')
    }

    requireCentral(
        File(centralGit("rev-parse", "--show-toplevel")).canonicalFile == checkout,
        "Selected directory is not the Git repository root.",
    )
    requireCentral(centralGit("rev-parse", "HEAD") == pinnedCommit, "Checkout is not at $pinnedCommit.")
    requireCentral(centralGit("remote", "get-url", "origin") == officialRemote, "Unexpected origin remote.")
    val patchFile = file("patches/openrune-central-s10.patch")
    requireCentral(patchFile.isFile, "Missing checked-in patch: $patchFile.")
    val patchBytes = patchFile.readBytes()
    val patchHash = MessageDigest.getInstance("SHA-256").digest(patchBytes).joinToString("") { "%02x".format(it) }
    val expectedPatch = normalizePatch(patchBytes.toString(Charsets.UTF_8))
    requireCentral(expectedPatch.startsWith("diff --git "), "The checked-in patch is empty or invalid.")
    requireCentral(
        normalizePatch(centralGit("diff", "--cached", "--binary", "--full-index", "HEAD", "--")) == expectedPatch,
        "Staged source does not exactly match the checked-in S10 patch.",
    )
    requireCentral(
        centralGit("diff", "--name-only", "--").isEmpty(),
        "Checkout contains unstaged changes; preserve and review them before setup.",
    )
    requireCentral(
        centralGit("ls-files", "--others", "--exclude-standard").isEmpty(),
        "Checkout contains untracked files; preserve and review them before setup.",
    )
    if (isCanonicalCheckout) {
        val stampFile = File(checkout, ".git/openrune-central-s10.properties")
        requireCentral(stampFile.isFile, "Missing setup stamp.")
        val stamp = Properties().apply { stampFile.inputStream().use { load(it) } }
        requireCentral(
            stamp.getProperty("commit") == pinnedCommit && stamp.getProperty("patch.sha256") == patchHash,
            "Setup stamp is stale for the current checked-in patch.",
        )
    }

    includeBuild(checkout) {
        dependencySubstitution {
            substitute(module("dev.or2:openrune-central")).using(project(":central-app"))
            substitute(module("dev.or2:central-common")).using(project(":central-common"))
            substitute(module("dev.or2:central-worldlink")).using(project(":central-worldlink"))
        }
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://repo.openrs2.org/repository/openrs2-snapshots")
        maven("https://raw.githubusercontent.com/OpenRune/hosting/master")
    }
}

include(
    "api",
    "content",
    "engine",
    "server",
    "or-cache",
    "tools:boss-roster",
    "tools:osrs-mcp",
    "tools:wiki-dumping",
)

includeProjects(project(":api"))
includeProjects(project(":content"))
includeProjects(project(":engine"))
includeProjects(project(":server"))

fun includeProjects(pluginProject: ProjectDescriptor) {
    val projectPath = pluginProject.projectDir.toPath()
    Files.walk(projectPath).forEach {
        if (!Files.isDirectory(it)) {
            return@forEach
        }
        searchProject(pluginProject.name, projectPath, it)
    }
}

fun searchProject(parentName: String, root: Path, currentPath: Path) {
    val hasBuildFile = Files.exists(currentPath.resolve("build.gradle.kts"))
    if (!hasBuildFile) {
        return
    }
    val relativePath = root.relativize(currentPath)
    if (relativePath.toString().isEmpty()) {
        return
    }
    val projectName = relativePath.toString().replace(File.separator, ":")
    include("$parentName:$projectName")
}
