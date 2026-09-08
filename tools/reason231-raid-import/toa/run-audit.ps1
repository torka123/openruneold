$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$source = 'C:\tmp\reason231-cox-donor-20260816\osrs server\server reasonps\data\cache'
$stock = 'C:\tmp\openrs2-cache-2048\cache'
$donorSource = 'C:\tmp\reason231-cox-donor-20260816\osrs server\server reasonps\kronos-server\src\main\java\io\ruin\model\activities\raids\toa'
$output = Join-Path $repo 'docs\raids\toa'
foreach ($path in @($source, $stock, $donorSource)) {
    if (-not (Test-Path -LiteralPath $path)) { throw "Missing read-only audit input: $path" }
}
& (Join-Path $repo 'gradlew.bat') ':or-cache:auditToaCaches' "-PtoaSource=$source" "-PtoaStock=$stock" "-PtoaDonorSource=$donorSource" "-PtoaAuditOutput=$output"
if ($LASTEXITCODE -ne 0) { throw "ToA cache audit failed with exit code $LASTEXITCODE" }
