param([int[]] $Capture = @(3279, 1851, 1857, 550, 1158))
$ErrorActionPreference = 'Stop'
foreach ($captureId in $Capture) {
    $capturePath = Join-Path (Join-Path $env:USERPROFILE '.rsprox\binary\vorkath-audit') "rsprox-$captureId.txt"
    $tick = -1
    $lineNumber = 0
    $events = [System.Collections.Generic.List[object]]::new()
    foreach ($captureText in [System.IO.File]::ReadLines($capturePath)) {
        $lineNumber++
        if ($captureText -match '^\[(\d+)\]$') { $tick = [int]$Matches[1] }
        $kind = $null
        if ($captureText -match '\[sequence\] id=(?:[a-z0-9_]+ \()?(7950|7951|7952|7957|7960|7949|7890|7891)(?:\)|,|$)') {
            $kind = "seq$($Matches[1])"
            if ($kind -eq 'seq7960' -and $captureText -match 'delay=2') { $kind = 'fireball' }
        } elseif ($captureText -match '\[map_projanim_v[12]\] id=(?:[a-z_]+ \()?(395|1482|1484)(?:\)|,)') {
            $kind = "proj$($Matches[1])"
        } elseif ($captureText -match '\[loc_add_change_v[12]\] id=(?:[a-z_]+ \()?32000(?:\)|,)') {
            $kind = 'pool'
        } elseif ($captureText -match '\[add\].*(?:vorkath_spawn \(8063\)|Zombified Spawn \(id=8063\))') {
            $kind = 'spawnadd'
        } elseif ($captureText -match 'You become unfrozen as you kill the spawn') {
            $kind = 'resolved'
        } elseif ($captureText -match 'The spawn violently explodes') {
            $kind = 'exploded'
        }
        if ($kind) { $events.Add([pscustomobject]@{Tick=$tick; Line=$lineNumber; Kind=$kind}) }
    }
    $iceTicks = @{}
    foreach ($event in $events) { if ($event.Kind -eq 'proj395') { $iceTicks[$event.Tick] = $true } }
    $normal = @($events | Where-Object { $_.Kind -in @('seq7951','fireball') -or ($_.Kind -eq 'seq7952' -and -not $iceTicks.ContainsKey($_.Tick)) })
    $specials = @($events | Where-Object { $_.Kind -in @('seq7957','proj395') })
    $acid = @($specials | Where-Object Kind -eq 'seq7957')
    $rows = @()
    foreach ($cast in $acid) {
        $shots = @($events | Where-Object { $_.Kind -eq 'proj1482' -and $_.Tick -ge $cast.Tick -and $_.Tick -le $cast.Tick+32 })
        $pools = @($events | Where-Object { $_.Kind -eq 'pool' -and $_.Tick -ge $cast.Tick -and $_.Tick -le $cast.Tick+4 })
        $resume = $normal | Where-Object Tick -gt $cast.Tick | Select-Object -First 1
        $shotFirst = if ($shots.Count) { $shots[0].Tick-$cast.Tick } else { -1 }
        $shotLast = if ($shots.Count) { $shots[-1].Tick-$cast.Tick } else { -1 }
        $poolFirst = if ($pools.Count) { $pools[0].Tick-$cast.Tick } else { -1 }
        $resumeDelta = if ($resume -and $resume.Tick -le $cast.Tick+40) { $resume.Tick-$cast.Tick } else { -1 }
        $rows += [pscustomobject]@{Cast=$cast.Tick;Line=$cast.Line;Shots=$shots.Count;First=$shotFirst;Last=$shotLast;PoolDelay=$poolFirst;Pools=$pools.Count;Resume=$resumeDelta}
    }
    "rsprox-$captureId normal=$($normal.Count) ice=$($iceTicks.Count) acid=$($acid.Count)"
    foreach ($group in ($rows | Group-Object Shots,First,Last,PoolDelay,Resume)) {
        $example = $group.Group[0]
        " acid count=$($group.Count) signature=$($group.Name) example=t$($example.Cast):line$($example.Line) pools=$($example.Pools)"
    }
    $arrivalDeltas = @()
    foreach ($launch in ($events | Where-Object Kind -eq 'proj1484')) {
        $arrival = $events | Where-Object { $_.Kind -eq 'spawnadd' -and $_.Tick -ge $launch.Tick -and $_.Tick -le $launch.Tick+5 } | Select-Object -First 1
        if ($arrival) { $arrivalDeltas += $arrival.Tick-$launch.Tick }
    }
    foreach ($group in ($arrivalDeltas | Group-Object)) { " spawnArrival delay=$($group.Name) count=$($group.Count)" }
    $normalCount = 0
    $previousNormal = $null
    $previousSpecial = $null
    $cadences = @()
    $counts = @()
    $alternationMismatch = 0
    foreach ($event in $events) {
        if ($event.Kind -eq 'seq7950') { $normalCount=0; $previousNormal=$null; $previousSpecial=$null }
        $isNormal = $event.Kind -in @('seq7951','fireball') -or ($event.Kind -eq 'seq7952' -and -not $iceTicks.ContainsKey($event.Tick))
        if ($isNormal) {
            if ($null -ne $previousNormal) { $cadences += $event.Tick-$previousNormal }
            $previousNormal = $event.Tick
            $normalCount++
        }
        if ($event.Kind -in @('seq7957','proj395')) {
            $counts += $normalCount
            if ($previousSpecial -eq $event.Kind) { $alternationMismatch++ }
            $previousSpecial=$event.Kind
            $normalCount=0
            $previousNormal=$null
        }
    }
    foreach ($group in ($cadences | Group-Object)) { " normalCadence delay=$($group.Name) count=$($group.Count)" }
    foreach ($group in ($counts | Group-Object)) { " normalsBeforeSpecial attacks=$($group.Name) count=$($group.Count)" }
    " specialAlternationMismatch=$alternationMismatch"
}
