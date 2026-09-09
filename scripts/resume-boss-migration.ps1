[CmdletBinding()]
param(
    [switch]$Verify,
    [switch]$CopyPrompt,
    [ValidatePattern('^[a-z0-9-]+$')][string]$Boss = 'zulrah'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$migrationRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$canonicalMigrationRoot = 'C:\Users\norma\Documents\Codex\2026-08-10\lets-work-on-the-openrune-server\work\OpenRune-Server'
if (![string]::Equals($migrationRoot, $canonicalMigrationRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Refusing to resume from a non-canonical OpenRune workspace.'
}
$handoffPath = Join-Path $migrationRoot 'docs/bosses/MIGRATION_HANDOFF.md'
if (!(Test-Path -LiteralPath $handoffPath -PathType Leaf)) { throw 'Migration handoff is missing.' }
$bossBuild = Join-Path $migrationRoot "content/bosses/$Boss/build.gradle.kts"
if (!(Test-Path -LiteralPath $bossBuild -PathType Leaf)) { throw "Unknown boss module: $Boss" }

Push-Location -LiteralPath $migrationRoot
try {
    $migrationBranch = & git branch --show-current
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read the current Git branch.' }
    $migrationCommit = & git rev-parse HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read the current Git commit.' }
    $migrationStatus = & git status --short
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read Git status.' }

    if ($Verify) {
        & (Join-Path $PSScriptRoot 'openrune-doctor.ps1')
        & (Join-Path $PSScriptRoot 'check-boss-architecture.ps1')
        if ($LASTEXITCODE -ne 0) { throw 'Architecture check failed; inspect before resuming.' }
        & .\gradlew.bat ":content:bosses:${Boss}:test" ':api:bosses:test' ':api:instances:test' --no-daemon --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Focused tests failed. Preserve the work and inspect the reports.' }
    }

    $migrationPrompt = @"
Continue the OpenRune all-boss DSL migration in this canonical workspace:
$migrationRoot

Current Git branch: $migrationBranch
Current HEAD: $migrationCommit
Current status (recheck before editing):
$($migrationStatus -join "`n")

Read docs/bosses/DSL_MIGRATION.md for the full mechanic-to-API mapping and exact test evidence.
The current checkpoint follows; actual newer code/commits take precedence over stale handoff notes.

$(Get-Content -LiteralPath $handoffPath -Raw)
"@
    if ($CopyPrompt) { Set-Clipboard -Value $migrationPrompt }
    Write-Output $migrationPrompt
}
finally {
    Pop-Location
}
