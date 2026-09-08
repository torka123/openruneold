$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$auditDir = Join-Path $projectRoot 'logs/cerberus-audit-20260907'
New-Item -ItemType Directory -Force -Path $auditDir | Out-Null
$groups = @(
    @{ Name = 'npcs-complete'; Type = 0; Queries = @(5862,5863,5866,5867,5868,5869) },
    @{ Name = 'boss-anims-base'; Type = 8; Queries = @(4484,4485,4486,4487,4488,4489,4490) },
    @{ Name = 'boss-anims-attacks'; Type = 8; Queries = @(4491,4492,4493,4494,4495,4501) },
    @{ Name = 'coordinates-text'; Type = 14; Queries = @('1238,1250','1238, 1250','0_19_19_22_34','1240,1226','Aaarrrooooooo','Grrrrrrrrrrrrrr') }
)
foreach ($group in $groups) {
    $conditions = @($group.Queries | ForEach-Object { @{ type = $group.Type; query = [string]$_ } })
    $body = @{ conditions = $conditions; mode = 'ANY'; page = 0; limit = 100; sort = 'NEWEST' } | ConvertTo-Json -Depth 6
    $result = Invoke-RestMethod -Uri 'https://rsprox.net/api/search' -Method Post -ContentType 'application/json' -Body $body
    $json = $result | ConvertTo-Json -Depth 15
    [IO.File]::WriteAllText((Join-Path $auditDir ('rsprox-' + $group.Name + '.json')), $json)
    Write-Output ($group.Name + ': ' + $json)
}
