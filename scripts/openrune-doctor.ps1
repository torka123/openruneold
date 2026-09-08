[CmdletBinding()]
param(
    [string[]]$SearchRoots = @(),
    [switch]$SkipGradleVersion
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$DataRoot = Join-Path $ProjectRoot '.data'
$ServerCache = Join-Path $DataRoot 'cache\SERVER'
$LiveCache = Join-Path $DataRoot 'cache\LIVE'
$PostgresData = Join-Path $DataRoot 'postgres'

function Write-Section([string]$Title) {
    Write-Output ''
    Write-Output "=== $Title ==="
}

function Get-GitText([string[]]$Arguments) {
    $output = & git -C $ProjectRoot @Arguments 2>$null
    if ($LASTEXITCODE -ne 0) {
        return '<unavailable>'
    }
    $text = ($output -join [Environment]::NewLine).Trim()
    if ([string]::IsNullOrWhiteSpace($text)) {
        return '<clean>'
    }
    return $text
}

function Write-PathInfo([string]$Label, [string]$Path) {
    if (Test-Path -LiteralPath $Path) {
        $item = Get-Item -LiteralPath $Path
        Write-Output ("{0}: {1} (modified {2:u})" -f $Label, $item.FullName, $item.LastWriteTimeUtc)
    } else {
        Write-Output ("{0}: <missing> {1}" -f $Label, $Path)
    }
}

function Get-CacheFingerprint([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        return '<missing>'
    }
    $files = @(Get-ChildItem -LiteralPath $Path -Recurse -File -ErrorAction SilentlyContinue)
    $bytes = ($files | Measure-Object Length -Sum).Sum
    $dat2 = $files | Where-Object { $_.Name -eq 'main_file_cache.dat2' } | Select-Object -First 1
    $dat2Hash = if ($null -ne $dat2) { (Get-FileHash -LiteralPath $dat2.FullName -Algorithm SHA256).Hash } else { '<no dat2 file>' }
    return ("files={0}; bytes={1}; dat2-sha256={2}" -f $files.Count, $bytes, $dat2Hash)
}

function Get-SourceFiles([string[]]$Roots, [string]$Filter) {
    foreach ($root in $Roots) {
        if (-not (Test-Path -LiteralPath $root)) {
            continue
        }
        Get-ChildItem -LiteralPath $root -Recurse -File -Filter $Filter -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -notmatch '\\(build|out|\.gradle|\.git|\.kotlin)\\' }
    }
}

Write-Section 'OpenRune doctor (read-only)'
Write-PathInfo 'Project root' $ProjectRoot
Write-Output 'Canonical launch command: .\gradlew.bat :server:app:run --no-daemon'
Write-Output 'Active server module: :server:app'

Write-Section 'Git'
Write-Output ('Branch: ' + (Get-GitText @('branch', '--show-current')))
Write-Output ('Commit: ' + (Get-GitText @('rev-parse', '--short', 'HEAD')))
Write-Output 'Dirty files:'
Get-GitText @('status', '--short')

Write-Section 'Runtime and build outputs'
$savedErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$javaLine = & java -version 2>&1 | Select-Object -First 1
$ErrorActionPreference = $savedErrorActionPreference
Write-Output ('Java: ' + ($javaLine -join ''))
if (-not $SkipGradleVersion) {
    $gradleWrapper = Join-Path $ProjectRoot 'gradlew.bat'
    if (Test-Path -LiteralPath $gradleWrapper) {
        $gradleLine = & $gradleWrapper --version --no-daemon 2>&1 | Select-String '^Gradle ' | Select-Object -First 1
        if ($null -ne $gradleLine) {
            Write-Output ('Gradle: ' + $gradleLine.ToString().Trim())
        }
    }
}
Write-PathInfo 'Server classes' (Join-Path $ProjectRoot 'server\app\build\classes\kotlin\main')
Write-PathInfo 'Server JAR' (Join-Path $ProjectRoot 'server\app\build\libs\server.jar')
Write-PathInfo 'Commands JAR' (Join-Path $ProjectRoot 'content\other\commands\build\libs\commands-0.0.1.jar')

$serverProcesses = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -in @('java.exe', 'javaw.exe') -and $_.CommandLine -match 'org\.rsmod\.server\.app\.GameServerKt' }
if ($serverProcesses) {
    Write-Output 'Running GameServer JVMs:'
    foreach ($process in $serverProcesses) {
        Write-Output ("  PID {0}: {1}" -f $process.ProcessId, $process.CommandLine)
        if ($process.ExecutablePath) {
            $jcmd = Join-Path (Split-Path -Parent $process.ExecutablePath) 'jcmd.exe'
            if (Test-Path -LiteralPath $jcmd) {
                $properties = & $jcmd $process.ProcessId VM.system_properties 2>$null
                $properties | Where-Object { $_ -match '^(user\.dir|java\.class\.path)=' } | ForEach-Object { "    $_" }
            }
        }
    }
} else {
    Write-Output 'Running GameServer JVMs: none detected'
}

Write-Section 'Data, cache, and persistence'
Write-PathInfo 'Data directory' $DataRoot
Write-PathInfo 'Player-save database (embedded PostgreSQL)' $PostgresData
Write-PathInfo 'Active server cache' $ServerCache
Write-PathInfo 'LIVE JS5 cache' $LiveCache
$gameYml = Join-Path $ProjectRoot 'game.yml'
if (Test-Path -LiteralPath $gameYml) {
    $revision = Select-String -LiteralPath $gameYml -Pattern '^revision:\s*(.+)$' | Select-Object -First 1
    if ($null -ne $revision) {
        Write-Output ('Configured cache revision: ' + $revision.Matches[0].Groups[1].Value.Trim())
    }
}
Write-Output ('SERVER cache fingerprint: ' + (Get-CacheFingerprint $ServerCache))
Write-Output ('LIVE cache fingerprint: ' + (Get-CacheFingerprint $LiveCache))

if ($SearchRoots.Count -eq 0) {
    $SearchRoots = @(
        $ProjectRoot,
        (Join-Path $env:USERPROFILE 'Documents'),
        (Join-Path $env:OneDrive 'Documents')
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_) } | Select-Object -Unique
}

Write-Section 'Duplicate gameplay sources'
Write-Output 'Search roots:'
$SearchRoots | ForEach-Object { Write-Output "  $_" }
$adminFiles = @(Get-SourceFiles $SearchRoots 'AdminCommands.kt' | Sort-Object FullName -Unique)
Write-Output 'AdminCommands.kt files:'
$adminFiles | ForEach-Object {
    $tracked = $false
    $insideActiveRepository = $_.FullName.StartsWith($ProjectRoot, [System.StringComparison]::OrdinalIgnoreCase)
    if ($insideActiveRepository) {
        $savedErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & git -C $ProjectRoot ls-files --error-unmatch -- $_.FullName 2>$null | Out-Null
        $tracked = $LASTEXITCODE -eq 0
        $ErrorActionPreference = $savedErrorActionPreference
    }
    Write-Output ("  {0} | {1} bytes | {2:u} | tracked-by-active-git={3}" -f $_.FullName, $_.Length, $_.LastWriteTimeUtc, $tracked)
}
$specialbankCount = @($adminFiles | ForEach-Object { Select-String -LiteralPath $_.FullName -Pattern '"specialbank"' -SimpleMatch -CaseSensitive }).Count
Write-Output ("specialbank definitions: {0}" -f $specialbankCount)

$barrowsFiles = @(Get-SourceFiles @($ProjectRoot) '*.kt' |
    Where-Object { $_.FullName -match '(?i)barrows|dharok|guthan|verac|(?:^|[\\_])torag|karil|ahrim' } |
    Sort-Object FullName)
Write-Output 'Barrows-related Kotlin sources:'
$barrowsFiles | ForEach-Object { Write-Output "  $($_.FullName)" }

$homeHits = @(Get-SourceFiles @($ProjectRoot) '*.kt' |
    Select-String -Pattern 'spawnCoord|respawnCoord|homeTeleport|Edgeville' |
    Select-Object -First 30)
Write-Output 'Home/respawn coordinate references (first 30):'
$homeHits | ForEach-Object { Write-Output ("  {0}:{1}: {2}" -f $_.Path, $_.LineNumber, $_.Line.Trim()) }

Write-Section 'Generated or non-authoritative directories'
Get-ChildItem -LiteralPath $ProjectRoot -Directory -Recurse -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -in @('build', 'out', 'generated', '.gradle', '.kotlin') } |
    ForEach-Object { Write-Output "  $($_.FullName)" }

Write-Output ''
Write-Output 'Doctor completed without modifying source, data, cache, Git state, or build output.'
