param([Parameter(Mandatory = $true)][int]$ServerProcessId, [Parameter(Mandatory = $true)][int]$RestartNumber)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$javaBin = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot/bin'
$auditDir = Join-Path $projectRoot 'logs/cerberus-audit-20260907'
$helperDir = Join-Path $auditDir 'shutdown-helper'
New-Item -ItemType Directory -Force -Path $helperDir | Out-Null
$source = Join-Path $PSScriptRoot 'CerberusAuditShutdown.java'
& (Join-Path $javaBin 'javac.exe') --add-modules jdk.attach -d $helperDir $source
if ($LASTEXITCODE -ne 0) { throw 'Shutdown helper compilation failed' }
$manifest = Join-Path $helperDir 'MANIFEST.MF'
[IO.File]::WriteAllText($manifest, "Manifest-Version: 1.0`r`nAgent-Class: CerberusAuditShutdown`r`n`r`n")
$agentJar = Join-Path $helperDir 'shutdown-agent.jar'
& (Join-Path $javaBin 'jar.exe') --create --file $agentJar --manifest $manifest -C $helperDir 'CerberusAuditShutdown.class'
if ($LASTEXITCODE -ne 0) { throw 'Shutdown helper packaging failed' }
& (Join-Path $javaBin 'java.exe') --add-modules jdk.attach -cp $helperDir CerberusAuditShutdown $ServerProcessId $projectRoot $agentJar
if ($LASTEXITCODE -ne 0) { throw 'Native save/shutdown verification failed. No replacement will start; do not force-kill the server.' }
$deadline = [DateTime]::UtcNow.AddSeconds(55)
while (Get-Process -Id $ServerProcessId -ErrorAction SilentlyContinue) {
    if ([DateTime]::UtcNow -ge $deadline) { throw 'Server has not completed normal shutdown' }
    Start-Sleep -Milliseconds 250
}
$stdout = Join-Path $auditDir "restart-$RestartNumber.stdout.log"
$stderr = Join-Path $auditDir "restart-$RestartNumber.stderr.log"
Start-Process -FilePath (Join-Path $projectRoot 'gradlew.bat') -ArgumentList ':server:app:run','--no-daemon','--console=plain' -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr
Write-Output "Normal shutdown completed for PID $ServerProcessId. Restart $RestartNumber launched; logs: $stdout"
