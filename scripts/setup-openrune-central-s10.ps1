[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ProjectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$ExpectedCommit = '9d718ca61aa6437007f883f27c35126fac5a0558'
$OfficialRemote = 'https://github.com/OpenRune/OpenRune-Central-Server.git'
$GradleDirectory = Join-Path $ProjectRoot '.gradle'
$Checkout = [System.IO.Path]::GetFullPath((Join-Path $GradleDirectory 'openrune-central-s10'))
$PatchFile = Join-Path $ProjectRoot 'patches\openrune-central-s10.patch'
$StampFile = Join-Path $Checkout '.git\openrune-central-s10.properties'

# This script owns only the generated checkout. It never resets, cleans, deletes,
# changes the selected Gradle property, or touches server runtime/data directories.
function Assert-NoReparsePoint([string]$Path) {
    if (Test-Path -LiteralPath $Path) {
        $item = Get-Item -LiteralPath $Path -Force
        if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "Refusing redirected checkout path (symlink/junction): $Path"
        }
    }
}

function Invoke-CentralGit {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [int[]]$AllowedExitCodes = @(0)
    )

    $savedPreference = $ErrorActionPreference
    try {
        # Native Git progress is written to stderr on Windows PowerShell 5.1.
        $ErrorActionPreference = 'Continue'
        $output = @(& git -c core.quotePath=false @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $savedPreference
    }
    $text = ($output | ForEach-Object { $_.ToString() }) -join "`n"
    if ($AllowedExitCodes -notcontains $exitCode) {
        throw "Git failed (exit $exitCode): git $($Arguments -join ' ')`n$text"
    }
    return [pscustomobject]@{ ExitCode = $exitCode; Text = $text.TrimEnd([char[]]"`r`n") }
}

function Normalize-Patch([string]$Text) {
    return $Text.Replace("`r`n", "`n").TrimEnd([char]10)
}

function Assert-CheckoutIdentity {
    Assert-NoReparsePoint $Checkout
    Assert-NoReparsePoint (Join-Path $Checkout '.git')
    if (-not (Test-Path -LiteralPath (Join-Path $Checkout '.git') -PathType Container)) {
        throw "Expected a standalone Git checkout at $Checkout. Existing files have been preserved."
    }
    $actualRoot = (Invoke-CentralGit -Arguments @('-C', $Checkout, 'rev-parse', '--show-toplevel')).Text
    if (-not [System.String]::Equals([System.IO.Path]::GetFullPath($actualRoot), $Checkout, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Git root does not match the canonical checkout: $actualRoot"
    }
    $origin = (Invoke-CentralGit -Arguments @('-C', $Checkout, 'remote', 'get-url', 'origin')).Text
    if ($origin -cne $OfficialRemote) {
        throw "Unexpected Central origin '$origin'. Expected $OfficialRemote. Existing checkout preserved."
    }
}

if (-not (Test-Path -LiteralPath $PatchFile -PathType Leaf)) {
    throw "Missing checked-in patch: $PatchFile. Restore the patch before preparing Central."
}
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw 'Git is required to prepare the pinned Central source checkout.'
}
Assert-NoReparsePoint $GradleDirectory
Assert-NoReparsePoint $Checkout
$expectedPath = [System.IO.Path]::GetFullPath((Join-Path $ProjectRoot '.gradle\openrune-central-s10'))
if (-not [System.String]::Equals($Checkout, $expectedPath, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing a noncanonical checkout path: $Checkout"
}
$patchText = Normalize-Patch ([System.IO.File]::ReadAllText($PatchFile, [System.Text.Encoding]::UTF8))
if (-not $patchText.StartsWith('diff --git ')) {
    throw "Expected a nonempty full-index Git patch at $PatchFile."
}
$patchHash = (Get-FileHash -LiteralPath $PatchFile -Algorithm SHA256).Hash.ToLowerInvariant()

if (-not (Test-Path -LiteralPath $Checkout)) {
    if (-not (Test-Path -LiteralPath $GradleDirectory)) {
        New-Item -ItemType Directory -Path $GradleDirectory | Out-Null
    }
    Write-Output "Cloning official Central source into $Checkout"
    Invoke-CentralGit -Arguments @('clone', '--no-checkout', '--origin', 'origin', $OfficialRemote, $Checkout) | Out-Null
    Assert-CheckoutIdentity
    Invoke-CentralGit -Arguments @('-C', $Checkout, 'fetch', '--no-tags', 'origin', $ExpectedCommit) | Out-Null
    Invoke-CentralGit -Arguments @('-C', $Checkout, 'checkout', '--detach', $ExpectedCommit) | Out-Null
}

Assert-CheckoutIdentity
$headCommit = (Invoke-CentralGit -Arguments @('-C', $Checkout, 'rev-parse', 'HEAD')).Text
if ($headCommit -cne $ExpectedCommit) {
    throw "Central HEAD is $headCommit; expected $ExpectedCommit. Existing checkout preserved; review it manually."
}
$workingChanges = Invoke-CentralGit -Arguments @('-C', $Checkout, 'diff', '--quiet', '--') -AllowedExitCodes @(0, 1)
if ($workingChanges.ExitCode -ne 0) {
    throw "Central contains unstaged changes. Existing files preserved; review $Checkout before rerunning setup."
}
$untracked = (Invoke-CentralGit -Arguments @('-C', $Checkout, 'ls-files', '--others', '--exclude-standard')).Text
if (-not [string]::IsNullOrEmpty($untracked)) {
    throw "Central contains untracked files. Existing files preserved:`n$untracked"
}
$stagedPatch = Normalize-Patch ((Invoke-CentralGit -Arguments @('-C', $Checkout, 'diff', '--cached', '--binary', '--full-index', 'HEAD', '--')).Text)
if ($stagedPatch -ceq $patchText) {
    Write-Output 'The exact checked-in S10 patch is already applied.'
} elseif ([string]::IsNullOrEmpty($stagedPatch)) {
    Invoke-CentralGit -Arguments @('-C', $Checkout, 'apply', '--check', '--index', $PatchFile) | Out-Null
    Invoke-CentralGit -Arguments @('-C', $Checkout, 'apply', '--index', $PatchFile) | Out-Null
} else {
    throw 'Central contains a different staged patch. Existing files preserved; setup will not reset or overwrite it.'
}

$verifiedPatch = Normalize-Patch ((Invoke-CentralGit -Arguments @('-C', $Checkout, 'diff', '--cached', '--binary', '--full-index', 'HEAD', '--')).Text)
if ($verifiedPatch -cne $patchText) {
    throw 'Applied source does not exactly reproduce the checked-in full-index patch. Checkout preserved; no setup stamp written.'
}
Invoke-CentralGit -Arguments @('-C', $Checkout, 'diff', '--quiet', '--') | Out-Null
$stamp = "commit=$ExpectedCommit`npatch.sha256=$patchHash`n"
[System.IO.File]::WriteAllText($StampFile, $stamp, [System.Text.UTF8Encoding]::new($false))
Write-Output "Central S10 ready: commit=$ExpectedCommit patch.sha256=$patchHash"
Write-Output 'The canonical Gradle launch now substitutes all three Central modules from this verified checkout.'
