param([switch]$DownloadVideo, [switch]$InspectFrames, [ValidateRange(-1,3600)][int]$FrameAt = -1, [ValidatePattern('^[a-zA-Z0-9]+$')][string]$ClipId = 'ntCmAkS6hzYvvgWLu')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$auditDir = Join-Path $projectRoot 'logs/cerberus-visual-fix-20260907'
New-Item -ItemType Directory -Force -Path $auditDir | Out-Null
if ($InspectFrames) {
    $inspectVideo = Join-Path $auditDir ('medal-' + $ClipId + '.mp4')
    $inspectImage = Join-Path $auditDir ('medal-' + $ClipId + '-preview.jpg')
    if ($FrameAt -ge 0) {
        & 'C:\tmp\ffmpeg-essentials\ffmpeg-9.0.1-essentials_build\bin\ffmpeg.exe' -hide_banner -loglevel error -y -ss $FrameAt -i $inspectVideo -frames:v 1 -q:v 3 $inspectImage
    } else {
        & 'C:\tmp\ffmpeg-essentials\ffmpeg-9.0.1-essentials_build\bin\ffmpeg.exe' -hide_banner -loglevel error -y -i $inspectVideo -vf 'fps=1/3,scale=426:-1,tile=3x5' -frames:v 1 -q:v 6 $inspectImage
    }
    if ($LASTEXITCODE -ne 0) { throw 'Failed to inspect Medal frames.' }
    Write-Output ('data:image/jpeg;base64,' + [Convert]::ToBase64String([IO.File]::ReadAllBytes($inspectImage)))
    return
}
$commit = Invoke-RestMethod 'https://api.github.com/repos/MouldyToast/Offline_Play/commits/main'
[IO.File]::WriteAllText((Join-Path $auditDir 'reference-commit.txt'), [string]$commit.sha)
$base = 'https://raw.githubusercontent.com/MouldyToast/Offline_Play/' + $commit.sha + '/near-reality-server-main/core/src/main/java/com/zenyte/game/content/boss/cerberus/'
foreach ($name in @('Cerberus.java','SummonedSoul.java','CerberusRoom.java')) {
    Invoke-WebRequest -UseBasicParsing ($base + $name) -OutFile (Join-Path $auditDir $name)
}
$clipUrl = 'https://medal.tv/games/screen-capture/clips/' + $ClipId
$htmlPath = Join-Path $auditDir ('medal-' + $ClipId + '.html')
Invoke-WebRequest -UseBasicParsing $clipUrl -OutFile $htmlPath
$clipHtml = [IO.File]::ReadAllText($htmlPath)
$videoUrls = [regex]::Matches($clipHtml, 'https[^"<>\s]+\.mp4[^"<>\s]*') | ForEach-Object { [Net.WebUtility]::HtmlDecode($_.Value).Replace('\u0026', '&') } | Select-Object -Unique
Write-Output ('Found ' + $videoUrls.Count + ' video URL candidates in the supplied clip page.')
Write-Output ('Saved donor source at commit ' + $commit.sha)
if (-not $DownloadVideo) { return }
$videoPath = Join-Path $auditDir ('medal-' + $ClipId + '.mp4')
if (-not (Test-Path -LiteralPath $videoPath)) {
    $downloaded = $false
    foreach ($videoUrl in $videoUrls) {
        try {
            Invoke-WebRequest -UseBasicParsing $videoUrl -Headers @{Referer = $clipUrl} -OutFile $videoPath
            $downloaded = $true
            break
        } catch { Write-Output ('Video download failed: ' + $_.Exception.Message) }
    }
    if (-not $downloaded) { throw 'Medal did not provide a downloadable video.' }
}
$ffmpegDir = 'C:\tmp\ffmpeg-essentials\ffmpeg-9.0.1-essentials_build\bin'
& (Join-Path $ffmpegDir 'ffprobe.exe') -v error -show_entries format=duration:stream=width,height -of json $videoPath
& (Join-Path $ffmpegDir 'ffmpeg.exe') -hide_banner -loglevel error -y -i $videoPath -vf 'fps=1/3,scale=640:-1,tile=4x6' -frames:v 1 (Join-Path $auditDir ('medal-' + $ClipId + '-contact-sheet.png'))
if ($LASTEXITCODE -ne 0) { throw 'Failed to extract Medal frames.' }
Write-Output ('Saved video and frame contact sheet in ' + $auditDir)
