param(
    [int]$Capture,
    [string]$Pattern = '',
    [int]$From = 1,
    [int]$To = [int]::MaxValue,
    [int]$Limit = 120,
    [string]$TranscriptPath = ''
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$capturePath = $TranscriptPath
if (-not $capturePath) { $capturePath = Join-Path $projectRoot "logs/arceuus-audit-20260907/captures/rsprox-$Capture.txt" }
$captureLines = [IO.File]::ReadAllLines($capturePath)
$captureTick = ''
$captureEntity = ''
$shown = 0
for ($i = 0; $i -lt $captureLines.Length; $i++) {
    $line = $captureLines[$i]
    if ($line -match '^\[(\d+)\]$') { $captureTick = $Matches[1] }
    if ($line -match '^        \[(idle|add|run|walk|teleport)\] (npc|player)=') { $captureEntity = $line.Trim() }
    if ($i + 1 -lt $From -or $i + 1 -gt $To) { continue }
    if ($Pattern -and $line -notmatch $Pattern) { continue }
    '{0}: tick={1} {2}' -f ($i + 1), $captureTick, $line
    if ($line -match '^            \[(animation|anim|sequence|spotanim|transformation|transmog)\]') { '    entity: ' + $captureEntity }
    $shown++
    if ($shown -ge $Limit) { break }
}
