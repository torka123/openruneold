param(
    [int[]] $Capture = @(3279, 1851, 1857, 550, 1158),
    [int] $FromTick = -1,
    [int] $ToTick = [int]::MaxValue,
    [string] $Pattern = 'ds2_vorkath|vorkath_.*(?:travel|impact|acid)|dragonbreath|ds2_spawn|zombified_spawn|frozen|prayer.*disabled',
    [switch] $AllLines,
    [switch] $Summary,
    [switch] $Geometry,
    [switch] $SpawnLocations
)
$ErrorActionPreference = 'Stop'
foreach ($captureId in $Capture) {
    $capturePath = Join-Path (Join-Path $env:USERPROFILE '.rsprox\binary\vorkath-audit') "rsprox-$captureId.txt"
    $captureTick = -1
    $captureLine = 0
    $captureGroups = @{}
    $pendingProjectile = $null
    $geometryCount = 0
    $geometryMismatch = 0
    $localIndex = -1
    $playerX = 0
    $playerZ = 0
    foreach ($captureText in [System.IO.File]::ReadLines($capturePath)) {
        $captureLine++
        if ($captureText -match '^local player index: (\d+)') { $localIndex = [int]$Matches[1] }
        if ($captureText -match '^\[(\d+)\]$') { $captureTick = [int]$Matches[1] }
        if ($captureTick -lt $FromTick -or $captureTick -gt $ToTick) { continue }
        if ($SpawnLocations) {
            if ($captureText -match '^        \[(?:idle|run|walk|teleport|add)\] player=\(index=(\d+),.*?coord=\((\d+), (\d+),') {
                if ([int]$Matches[1] -eq $localIndex) {
                    $playerX = [int]$Matches[2]
                    $playerZ = [int]$Matches[3]
                    if ($captureText -match 'newcoord=\((\d+), (\d+),') {
                        $playerX = [int]$Matches[1]
                        $playerZ = [int]$Matches[2]
                    }
                }
            }
            if ($captureText -match '\[map_projanim_v[12]\] id=(?:[a-z_]+ \()?1484(?:\)|,)') {
                $pendingProjectile = @{ line = $captureLine; tick = $captureTick; x = $playerX; z = $playerZ }
            } elseif ($pendingProjectile -and $captureText -match '\[target\] coord=\((\d+), (\d+),') {
                $distance = [Math]::Max([Math]::Abs([int]$Matches[1] - $pendingProjectile.x), [Math]::Abs([int]$Matches[2] - $pendingProjectile.z))
                $captureKey = "distance=$distance"
                if (-not $captureGroups.ContainsKey($captureKey)) {
                    $captureGroups[$captureKey] = @{ count = 0; firstLine = $pendingProjectile.line; firstTick = $pendingProjectile.tick; player = "$($pendingProjectile.x),$($pendingProjectile.z)"; target = "$($Matches[1]),$($Matches[2])" }
                }
                $captureGroups[$captureKey].count++
                $pendingProjectile = $null
            }
            continue
        }
        if ($Geometry) {
            if ($captureText -match '\[map_projanim_v[12]\] id=(?:[a-z_]+ \()?(393|395|1470|1471|1477|1479)(?:\)|), starttime=30, endtime=(\d+)') {
                $pendingProjectile = @{ id = [int]$Matches[1]; end = [int]$Matches[2]; line = $captureLine; tick = $captureTick }
            } elseif ($pendingProjectile -and $captureText -match '\[source\] coord=\((\d+), (\d+),') {
                $pendingProjectile.x = [int]$Matches[1]
                $pendingProjectile.z = [int]$Matches[2]
            } elseif ($pendingProjectile -and $captureText -match '\[target\] coord=\((\d+), (\d+),') {
                $distance = [Math]::Max([Math]::Abs([int]$Matches[1] - $pendingProjectile.x), [Math]::Abs([int]$Matches[2] - $pendingProjectile.z))
                $predictedEnd = 70 + 5 * $distance
                $geometryCount++
                if ($predictedEnd -ne $pendingProjectile.end) {
                    $geometryMismatch++
                    if ($geometryMismatch -le 8) {
                        "rsprox-${captureId}.txt:$($pendingProjectile.line) tick=$($pendingProjectile.tick) id=$($pendingProjectile.id) end=$($pendingProjectile.end) mouthDistance=$distance predicted=$predictedEnd"
                    }
                }
                $pendingProjectile = $null
            }
            continue
        }
        if ($AllLines -or $captureText -match $Pattern) {
            if ($Summary) {
                $captureKey = $captureText.Trim()
                if (-not $captureGroups.ContainsKey($captureKey)) {
                    $captureGroups[$captureKey] = @{ count = 0; firstTick = $captureTick; firstLine = $captureLine }
                }
                $captureGroups[$captureKey].count++
                $captureGroups[$captureKey].lastTick = $captureTick
                $captureGroups[$captureKey].lastLine = $captureLine
            } else {
                "rsprox-${captureId}.txt:${captureLine} tick=${captureTick} $captureText"
            }
        }
    }
    if ($Geometry) { "rsprox-${captureId}.txt standardProjectiles=$geometryCount formulaMismatches=$geometryMismatch formula=70+5*Chebyshev(mouth,target)" }
    if ($SpawnLocations) {
        foreach ($captureKey in ($captureGroups.Keys | Sort-Object)) {
            $group = $captureGroups[$captureKey]
            "rsprox-${captureId}.txt:$($group.firstLine) tick=$($group.firstTick) count=$($group.count) $captureKey player=$($group.player) target=$($group.target)"
        }
    }
    if ($Summary) {
        foreach ($captureKey in ($captureGroups.Keys | Sort-Object)) {
            $group = $captureGroups[$captureKey]
            "rsprox-${captureId}.txt:$($group.firstLine) ticks=$($group.firstTick)..$($group.lastTick) count=$($group.count) $captureKey"
        }
    }
}
