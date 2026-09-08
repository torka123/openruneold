$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$auditDir = Join-Path $projectRoot 'logs/arceuus-audit-20260907'
New-Item -ItemType Directory -Force -Path $auditDir | Out-Null
$groups = @(
    @{ Name = 'thrall-npcs'; Type = 0; Queries = @(10878,10879,10880,10881,10882,10883,10884,10885,10886) },
    @{ Name = 'reanimation-npcs'; Type = 0; Queries = @(7018,7019,7020,7021,7022,7023,7024,7025,7026,7027,7028,7029,7030,7031,7032,7033,7034,7035,7036,7037,7038,7039) },
    @{ Name = 'arceuus-text'; Type = 14; Queries = @('reanimate','thrall','Mark of Darkness','Death Charge','Shadow Veil','Vile Vigour','Sinister Offering','Demonic Offering','Dark Lure','Ward of Arceuus') },
    @{ Name = 'vile-sequence'; Type = 8; Queries = @(8978) },
    @{ Name = 'shadow-sequence'; Type = 8; Queries = @(8979) },
    @{ Name = 'offering-sequence'; Type = 8; Queries = @(8975) },
    @{ Name = 'corruption-sequence'; Type = 8; Queries = @(8977) },
    @{ Name = 'self-imbue-sequence'; Type = 8; Queries = @(8970) }
)
foreach ($group in $groups) {
    for ($offset = 0; $offset -lt $group.Queries.Count; $offset += 5) {
    $conditions = @($group.Queries | Select-Object -Skip $offset -First 5 | ForEach-Object { @{ type = $group.Type; query = [string]$_ } })
    $body = @{ conditions = $conditions; mode = 'ANY'; page = 0; limit = 100; sort = 'NEWEST' } | ConvertTo-Json -Depth 6
    try {
        $result = Invoke-RestMethod -Uri 'https://rsprox.net/api/search' -Method Post -ContentType 'application/json' -Body $body
    } catch {
        Write-Output $_.ErrorDetails.Message
        throw
    }
    $json = $result | ConvertTo-Json -Depth 15
    [IO.File]::WriteAllText((Join-Path $auditDir ('rsprox-' + $group.Name + '-' + $offset + '.json')), $json)
    Write-Output ($group.Name + '-' + $offset + ': ' + $json)
    }
}
