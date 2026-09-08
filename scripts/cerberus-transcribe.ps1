param([int[]]$SubmissionIds = @(3323, 3329, 416))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$captureDir = Join-Path $projectRoot 'logs/cerberus-audit-20260907/captures'
$javaPath = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot/bin/java.exe'
$repository = Join-Path $env:USERPROFILE '.rsprox/launcher/repository/*'
foreach ($submission in $SubmissionIds) {
    $binary = Join-Path $captureDir "rsprox-$submission.bin"
    if (-not (Test-Path -LiteralPath $binary)) { throw "Missing $binary" }
    & $javaPath -Xmx2g -cp $repository net.rsprox.proxy.cli.TranscribeCommandKt -name $binary
    if ($LASTEXITCODE -ne 0) { throw "Transcription failed for $submission with $LASTEXITCODE" }
    $transcript = [IO.Path]::ChangeExtension($binary, '.txt')
    if (-not (Test-Path -LiteralPath $transcript)) { throw "No transcript for $submission" }
    Get-Item -LiteralPath $transcript | Select-Object FullName, Length
}
