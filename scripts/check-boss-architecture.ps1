[CmdletBinding()]
param([switch]$InventoryOnly, [switch]$SelfTest)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

function Get-ArchitectureFindings([string]$Boss, [string]$BuildText, [string]$SourceText) {
    if ($BuildText -notmatch 'projects\.api\.bosses') { "$Boss|boss-api-dependency|1" }
    $patterns = [ordered]@{
        'private-timeline' = 'class\s+\w*(?:CombatTimeline|Timeline|CombatScheduler|EncounterEngine)\b'
        'global-runs-map' = '(?:val|var)\s+runs\s*=\s*(?:mutableMapOf|hashMapOf|HashMap|ConcurrentHashMap)'
        'manual-lifecycle' = 'onPlayerLogout\s*\{|PlayerPostTickHook|InstancePlayerExternalTeleportEvent'
        'direct-combat-review' = 'queueHit\s*\(|NpcAttackOverride\s*\(|\.projAnim\s*\('
    }
    foreach ($rule in $patterns.Keys) {
        $count = [regex]::Matches($SourceText, $patterns[$rule]).Count
        if ($count -gt 0) { "$Boss|$rule|$count" }
    }
    if ($SourceText -match 'instances\.create\(|instances\.createServerOwned\(' -and
        $SourceText -notmatch ':\s*InstanceScript\(') { "$Boss|instance-script|1" }
}

if ($SelfTest) {
    $bad = @(Get-ArchitectureFindings 'new-boss' '' 'class CombatTimeline { val runs = mutableMapOf<Int, Int>(); fun f() { instances.create(); queueHit(); onPlayerLogout {} } }')
    if ($bad.Count -ne 6) { throw "Expected six architecture signals; got $($bad.Count)." }
    $good = @(Get-ArchitectureFindings 'native' 'implementation(projects.api.bosses)' 'class Native : InstanceScript(registry) { fun f() { BossCombat.register(); } }')
    if ($good.Count -ne 0) { throw 'Native fixture was incorrectly rejected.' }
    Write-Output 'Architecture detector self-tests passed.'
    exit 0
}

$findings = foreach ($module in Get-ChildItem -LiteralPath (Join-Path $projectRoot 'content/bosses') -Directory) {
    if ($module.Name -eq 'diagnostics') { continue }
    $buildPath = Join-Path $module.FullName 'build.gradle.kts'
    $sourceRoot = Join-Path $module.FullName 'src/main'
    if (!(Test-Path -LiteralPath $buildPath) -or !(Test-Path -LiteralPath $sourceRoot)) { continue }
    $sourceFiles = @(Get-ChildItem -LiteralPath $sourceRoot -Recurse -File -Filter '*.kt')
    $sourceText = ($sourceFiles | ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw }) -join "`n"
    Get-ArchitectureFindings $module.Name (Get-Content -LiteralPath $buildPath -Raw) $sourceText
}
if ($InventoryOnly) { $findings; exit 0 }

$waivers = Get-Content -LiteralPath (Join-Path $projectRoot 'docs/bosses/architecture-waivers.json') -Raw | ConvertFrom-Json
$failures = @()
foreach ($finding in $findings) {
    $boss, $rule, $countText = $finding -split '\|'
    $count = [int]$countText
    $waiver = @($waivers | Where-Object { $_.boss -eq $boss -and $_.rule -eq $rule })
    if ($waiver.Count -ne 1 -or $count -gt $waiver[0].maximum -or [string]::IsNullOrWhiteSpace($waiver[0].reason)) {
        $failures += "$boss / ${rule}: $count occurrences require a focused architecture review and documented waiver."
    }
}
if ($failures.Count -gt 0) { $failures | Write-Output; exit 1 }
Write-Output 'Boss architecture check passed against explicit review budgets. Existing waivers are migration debt, not parity certification.'
