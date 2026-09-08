$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$auditDir = Join-Path $projectRoot 'logs/arceuus-audit-20260907'
$captureDir = Join-Path $auditDir 'captures'
$manifest = @(Get-ChildItem -LiteralPath $captureDir -Filter '*.bin' | Sort-Object Name | ForEach-Object {
    $transcript = [IO.Path]::ChangeExtension($_.FullName, '.txt')
    [PSCustomObject]@{
        Submission = [int]([regex]::Match($_.BaseName, '\d+').Value)
        Url = 'https://rsprox.net/download/' + [regex]::Match($_.BaseName, '\d+').Value
        BinaryBytes = $_.Length
        BinarySHA256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
        TranscriptBytes = (Get-Item -LiteralPath $transcript).Length
        TranscriptSHA256 = (Get-FileHash -LiteralPath $transcript -Algorithm SHA256).Hash
        Header = @(Get-Content -LiteralPath $transcript -TotalCount 8)
    }
})
[IO.File]::WriteAllText((Join-Path $auditDir 'capture-manifest.json'), ($manifest | ConvertTo-Json -Depth 6))
$exports = @(
    @{ Name='3153-owned-thralls'; Capture=3153; Pattern='resurrection|resurrect|thrall.*(spawn|attack)|\[del\].*thrall|\[add\].*thrall|stat=magic|update_stat.*prayer|id=5054' },
    @{ Name='4037-current-skeleton'; Capture=4037; Pattern='thrall.*attack|thrall.*spawn|map_projanim.*1906' },
    @{ Name='4030-current-ghost'; Capture=4030; Pattern='thrall|resurrect' },
    @{ Name='4102-death-charge'; Capture=4102; Pattern='arceuus_death_charge|special attack energy|Death Charge|human_cast_selfimbue|death_charge_cast' },
    @{ Name='1672-reanimation'; Capture=1672; Pattern='\[(add|del)\].*Reanimated|\[sequence\] id=3865|\[map_projanim.*id=1289|\[map_spotanim.*id=1290' }
)
foreach ($entry in $exports) {
    $lines = @(& (Join-Path $PSScriptRoot 'arceuus-capture-excerpt.ps1') -Capture $entry.Capture -Pattern $entry.Pattern -Limit 600)
    [IO.File]::WriteAllLines((Join-Path $auditDir ($entry.Name + '.txt')), [string[]]$lines)
}
$manifest | Select-Object Submission, BinaryBytes, TranscriptBytes
