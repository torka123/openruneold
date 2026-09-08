param([int[]]$SubmissionIds = @(4037, 4030, 3153, 2278, 1693, 1672, 3098, 3082, 2734, 418))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$captureDir = Join-Path $projectRoot 'logs/arceuus-audit-20260907/captures'
New-Item -ItemType Directory -Force -Path $captureDir | Out-Null
$javaPath = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot/bin/java.exe'
$repository = Join-Path $env:USERPROFILE '.rsprox/launcher/repository/*'
$loggingConfig = Join-Path $PSScriptRoot 'arceuus-transcriber-log4j2.xml'
foreach ($submission in $SubmissionIds) {
    $binary = Join-Path $captureDir "rsprox-$submission.bin"
    if (-not (Test-Path -LiteralPath $binary)) {
        Invoke-WebRequest -UseBasicParsing -Uri "https://rsprox.net/download/$submission" -OutFile $binary
    }
    $transcript = [IO.Path]::ChangeExtension($binary, '.txt')
    if (-not (Test-Path -LiteralPath $transcript)) {
        & $javaPath "-Dlog4j.configurationFile=$loggingConfig" -Xmx2g -cp $repository net.rsprox.proxy.cli.TranscribeCommandKt -name $binary
        if ($LASTEXITCODE -ne 0) { throw "Transcription failed for $submission with $LASTEXITCODE" }
    }
    if (-not (Test-Path -LiteralPath $transcript)) { throw "No transcript for $submission" }
    Get-Item -LiteralPath $transcript | Select-Object FullName, Length
    Get-FileHash -LiteralPath $binary -Algorithm SHA256 | Select-Object Path, Hash
}
