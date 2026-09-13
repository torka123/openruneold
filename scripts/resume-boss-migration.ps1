[CmdletBinding()]
param(
    [switch]$Verify,
    [switch]$CopyPrompt,
    [ValidatePattern('^[a-z0-9-]+$')][string]$Boss = 'nex'
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
$starterPath = Join-Path $migrationRoot 'docs/bosses/RESUME_OPENRUNE_BOSS_API_DSL.txt'
if (!(Test-Path -LiteralPath $starterPath -PathType Leaf)) { throw 'Boss workflow starter prompt is missing.' }
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
$(Get-Content -LiteralPath $starterPath -Raw)

Current Git branch: $migrationBranch
Current HEAD: $migrationCommit
Current status (recheck before editing):
$($migrationStatus -join "`n")

Selected boss for this continuation: $Boss.
Read docs/bosses/nex/NEX_OSRS_AUDIT.md for the current Nex mapping, main/minion migration,
executable results and remaining conflicts. Nex is the latest user priority unless this
selection or a newer user request changes it. Preserve the completed Royal Titans reward pass.
Do not repeat completed deployments or discard research. Client acceptance remains open.
Routine boss checkpoints are updates, not reasons to stop: back up, verify, deploy when authorized,
record the result, and continue. Preserve unrelated work and do not push or merge remotely.
The current checkpoint follows; actual newer code/commits take precedence over stale handoff notes.

$(Get-Content -LiteralPath $handoffPath -Raw)
"@
    if ($CopyPrompt) { Set-Clipboard -Value $migrationPrompt }
    Write-Output $migrationPrompt
}
finally {
    Pop-Location
}
