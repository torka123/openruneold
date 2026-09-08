param([string]$Label = 'before-restart')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$auditDir = Join-Path $projectRoot 'logs/arceuus-audit-20260907'
$modules = @(
    'content/skills/magic/spell-attacks', 'content/skills/magic/spell-teleports',
    'content/skills/farming', 'content/skills/thieving',
    'api/invtx', 'engine/objtx', 'api/combat/combat-formulas', 'api/player',
    'or-cache', 'content/bosses/desert-treasure-ii', 'content/bosses/yama'
)
$results = foreach ($module in $modules) {
    $directory = Join-Path $projectRoot ($module + '/build/test-results/test')
    if (!(Test-Path -LiteralPath $directory)) { continue }
    foreach ($file in Get-ChildItem -LiteralPath $directory -Filter 'TEST-*.xml') {
        [xml]$xml = Get-Content -LiteralPath $file.FullName -Raw
        $suite = $xml.testsuite
        [pscustomobject]@{
            Module = $module; Suite = [string]$suite.name; Tests = [int]$suite.tests
            Failures = [int]$suite.failures; Errors = [int]$suite.errors
            Skipped = [int]$suite.skipped; Timestamp = [string]$suite.timestamp
        }
    }
}
$results | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $auditDir ('test-results-' + $Label + '.json'))
$results | Group-Object Module | ForEach-Object {
    [pscustomobject]@{
        Module = $_.Name
        Tests = ($_.Group | Measure-Object Tests -Sum).Sum
        Failures = ($_.Group | Measure-Object Failures -Sum).Sum
        Errors = ($_.Group | Measure-Object Errors -Sum).Sum
        Skipped = ($_.Group | Measure-Object Skipped -Sum).Sum
    }
} | Format-Table -AutoSize
$sources = @(
    'content/skills/magic/spell-attacks/src/main/kotlin/org/rsmod/content/skills/magic/spell/attacks/arceuus/ArceuusUtilityState.kt',
    'content/skills/magic/spell-attacks/src/main/kotlin/org/rsmod/content/skills/magic/spell/attacks/arceuus/ArceuusUtilitySpellScript.kt',
    'content/skills/magic/spell-attacks/src/main/kotlin/org/rsmod/content/skills/magic/spell/attacks/arceuus/ArceuusReanimationSpellScript.kt',
    'api/combat/combat-commons/src/main/kotlin/org/rsmod/api/combat/commons/magic/ArceuusSpellState.kt',
    'api/player/src/main/kotlin/org/rsmod/api/player/hit/modifier/WardOfArceuusState.kt',
    'api/combat/combat-manager/src/main/kotlin/org/rsmod/api/combat/manager/PlayerAttackManager.kt',
    'api/combat/combat-commons/src/main/kotlin/org/rsmod/api/combat/commons/CombatCorruption.kt',
    'engine/game/src/main/kotlin/org/rsmod/game/obj/EnsouledHeadProvenance.kt',
    'api/net/src/main/kotlin/org/rsmod/api/net/rsprot/handlers/OpObjTHandler.kt',
    'content/skills/thieving/src/main/kotlin/org/rsmod/content/skills/thieving/ThievingScript.kt',
    'content/skills/farming/src/main/kotlin/org/rsmod/content/skills/farming/ResurrectCropsScript.kt',
    'content/skills/magic/spell-teleports/src/main/kotlin/org/rsmod/content/skills/magic/spell/teleports/SpellTeleportScript.kt'
)
$hashes = foreach ($path in $sources) {
    [pscustomobject]@{ Path = $path; SHA256 = (Get-FileHash -LiteralPath (Join-Path $projectRoot $path) -Algorithm SHA256).Hash }
}
$hashFile = Join-Path $auditDir ('source-hashes-' + $Label + '.json')
$hashes | ConvertTo-Json | Set-Content -LiteralPath $hashFile
$baseline = Join-Path $auditDir 'source-hashes-before-restart.json'
if ($Label -ne 'before-restart' -and (Test-Path -LiteralPath $baseline)) {
    $difference = Compare-Object (Get-Content -LiteralPath $baseline -Raw) (Get-Content -LiteralPath $hashFile -Raw)
    if ($difference) { throw 'Audited source changed across restart verification.' }
    Write-Output 'Audited source hashes match the pre-restart baseline.'
}
Write-Output ('Source hashes saved: ' + $hashFile)
