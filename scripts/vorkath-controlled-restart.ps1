param(
    [Parameter(Mandatory = $true)][int]$ServerProcessId,
    [Parameter(Mandatory = $true)][int]$RestartNumber,
    [switch]$RunLiveAudit
)

$ErrorActionPreference = 'Stop'
# Keep each previous audit outcome before the next opt-in run replaces the latest report.
$vorkathAuditDirectory = Join-Path (Split-Path -Parent $PSScriptRoot) 'logs'
$vorkathAuditReport = Join-Path $vorkathAuditDirectory 'vorkath-live-audit-latest.log'
$vorkathSourceManifest = Join-Path $vorkathAuditDirectory 'vorkath-source-before-deploy.json'
if (Test-Path -LiteralPath $vorkathSourceManifest) {
    $vorkathProjectRoot = Split-Path -Parent $PSScriptRoot
    $vorkathSourcePaths = @((Get-Content -LiteralPath $vorkathSourceManifest -Raw | ConvertFrom-Json).Path) + @(
        'content/skills/magic/spell-attacks/src/main/kotlin/org/rsmod/content/skills/magic/spell/attacks/standard/CrumbleUndeadSpells.kt',
        'content/skills/magic/spell-attacks/src/test/kotlin/org/rsmod/content/skills/magic/spell/attacks/standard/CrumbleUndeadRulesTest.kt'
    )
    $vorkathSourcePaths | Sort-Object -Unique | ForEach-Object {
        $vorkathHash = Get-FileHash -LiteralPath (Join-Path $vorkathProjectRoot $_) -Algorithm SHA256
        [PSCustomObject]@{ Path = $_; Hash = $vorkathHash.Hash }
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $vorkathAuditDirectory "vorkath-source-restart-$RestartNumber.json")
}
if ($RunLiveAudit -and (Test-Path -LiteralPath $vorkathAuditReport)) {
    Copy-Item -LiteralPath $vorkathAuditReport -Destination (Join-Path $vorkathAuditDirectory "vorkath-live-audit-before-restart-$RestartNumber.log")
}
# Reuse the canonical graceful JVM shutdown/start mechanism; never force-kill the server.
$vorkathPreviousAudit = [Environment]::GetEnvironmentVariable('OPENRUNE_VORKATH_LIVE_AUDIT', 'Process')
try {
    $vorkathAuditValue = if ($RunLiveAudit) { 'true' } else { $null }
    [Environment]::SetEnvironmentVariable('OPENRUNE_VORKATH_LIVE_AUDIT', $vorkathAuditValue, 'Process')
    & (Join-Path $PSScriptRoot 'cerberus-controlled-restart.ps1') -ServerProcessId $ServerProcessId -RestartNumber $RestartNumber
}
finally {
    [Environment]::SetEnvironmentVariable('OPENRUNE_VORKATH_LIVE_AUDIT', $vorkathPreviousAudit, 'Process')
}
