param([string]$Label = 'final')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$auditDir = Join-Path $projectRoot 'logs/arceuus-audit-20260907'
New-Item -ItemType Directory -Force -Path $auditDir | Out-Null
$log = Join-Path $auditDir ("verify-" + $Label + ".log")
$exitFile = Join-Path $auditDir ("verify-" + $Label + ".exit")
Push-Location $projectRoot
try {
    $ErrorActionPreference = 'Continue'
    & .\gradlew.bat ':content:skills:magic:spell-attacks:test' ':content:skills:farming:test' ':content:skills:magic:spell-teleports:test' ':content:skills:thieving:test' ':api:invtx:test' ':engine:objtx:test' ':api:combat:combat-formulas:test' ':api:player:test' '--tests' '*WardOfArceuus*' ':or-cache:test' '--tests' 'dev.openrune.Arceuus*Test' ':content:bosses:desert-treasure-ii:test' ':content:bosses:yama:test' ':server:app:installDist' '--console=plain' *> $log
    $result = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    [IO.File]::WriteAllText($exitFile, [string]$result)
    Get-Content -LiteralPath $log -Tail 50
    exit $result
} finally {
    Pop-Location
}
