[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string[]] $DonorCacheRoots,

    [Parameter(Mandatory = $true)]
    [string] $BeforeManifest,

    [string] $OutputManifest
)

$ErrorActionPreference = 'Stop'

function Get-CacheManifest([string[]] $Roots) {
    $rows = foreach ($root in $Roots) {
        $resolvedRoot = (Resolve-Path -LiteralPath $root).Path
        Get-ChildItem -LiteralPath $resolvedRoot -File |
            Where-Object Name -Like 'main_file_cache.*' |
            Sort-Object FullName |
            ForEach-Object {
                [pscustomobject]@{
                    Path = $_.FullName
                    Length = $_.Length
                    Sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash
                }
            }
    }
    return @($rows | Sort-Object Path)
}

$before = @(Get-Content -Raw -LiteralPath $BeforeManifest | ConvertFrom-Json)
$after = @(Get-CacheManifest -Roots $DonorCacheRoots)

if ($OutputManifest) {
    $after | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $OutputManifest -Encoding utf8
}

$beforeByPath = @{}
foreach ($entry in $before) { $beforeByPath[$entry.Path.ToLowerInvariant()] = $entry }
$afterByPath = @{}
foreach ($entry in $after) { $afterByPath[$entry.Path.ToLowerInvariant()] = $entry }

$changes = [System.Collections.Generic.List[object]]::new()
foreach ($path in ($beforeByPath.Keys + $afterByPath.Keys | Sort-Object -Unique)) {
    $old = $beforeByPath[$path]
    $new = $afterByPath[$path]
    if ($null -eq $old -or $null -eq $new -or $old.Sha256 -ne $new.Sha256 -or $old.Length -ne $new.Length) {
        $changes.Add([pscustomobject]@{ Path = $path; Before = $old; After = $new })
    }
}

[pscustomobject]@{
    BeforeCount = $before.Count
    AfterCount = $after.Count
    ChangedFiles = $changes.Count
    Changes = $changes
} | ConvertTo-Json -Depth 8

if ($changes.Count -ne 0) {
    throw "Vorkath donor-cache safety check failed: $($changes.Count) file(s) changed."
}
