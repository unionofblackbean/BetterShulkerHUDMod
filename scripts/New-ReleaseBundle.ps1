[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ReleaseManifest,
    [string]$ArtifactsRoot,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'Manifest.psm1') -Force

function Get-Sha256 {
    param([Parameter(Mandatory = $true)][string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-ZipEntryText {
    param($Archive, [string]$Name)
    $entry = $Archive.GetEntry($Name)
    if ($null -eq $entry) {
        throw "JAR entry is missing: $Name"
    }
    $reader = [System.IO.StreamReader]::new($entry.Open(), [System.Text.Encoding]::UTF8)
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}

function Test-ModJar {
    param([Parameter(Mandatory = $true)][string]$Path, [Parameter(Mandatory = $true)]$VersionEntry)

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $fabric = Get-ZipEntryText -Archive $archive -Name 'fabric.mod.json' | ConvertFrom-Json
        if ([string]$fabric.id -cne 'better-shulker-hud') {
            throw "$Path has unexpected Fabric mod ID '$($fabric.id)'."
        }
        if ([string]$fabric.version -cne [string]$VersionEntry.modVersion) {
            throw "$Path metadata version '$($fabric.version)' does not match '$($VersionEntry.modVersion)'."
        }
        if ([string]$fabric.depends.minecraft -cne [string]$VersionEntry.minecraft.range) {
            throw "$Path Minecraft range '$($fabric.depends.minecraft)' does not match '$($VersionEntry.minecraft.range)'."
        }
        if ([string]$fabric.depends.java -cne ">=$($VersionEntry.java)") {
            throw "$Path Java dependency '$($fabric.depends.java)' does not match Java $($VersionEntry.java)."
        }

        # Validate a class owned by this mod, not an embedded Java 8 library.
        $classEntry = $archive.Entries | Where-Object { $_.FullName -match '^(betterbundle|bettershulkerhud)/.+\.class$' } | Select-Object -First 1
        if ($null -eq $classEntry) {
            throw "$Path contains no class files."
        }
        $stream = $classEntry.Open()
        try {
            $header = [byte[]]::new(8)
            if ($stream.Read($header, 0, 8) -ne 8) {
                throw "$Path contains a truncated class file: $($classEntry.FullName)."
            }
            if ($header[0] -ne 0xCA -or $header[1] -ne 0xFE -or $header[2] -ne 0xBA -or $header[3] -ne 0xBE) {
                throw "$Path contains an invalid class header: $($classEntry.FullName)."
            }
            $major = ([int]$header[6] * 256) + [int]$header[7]
            $expectedMajor = 44 + [int]$VersionEntry.java
            if ($major -ne $expectedMajor) {
                throw "$Path class major $major does not match Java $($VersionEntry.java) (expected $expectedMajor)."
            }
        } finally {
            $stream.Dispose()
        }
    } finally {
        $archive.Dispose()
    }
}

function Find-BuiltArtifact {
    param(
        [Parameter(Mandatory = $true)]$VersionEntry,
        [Parameter(Mandatory = $true)][bool]$Sources
    )

    $searchRoot = if ([string]::IsNullOrWhiteSpace($ArtifactsRoot)) {
        Join-Path (Resolve-RepositoryPath $VersionEntry.path) 'build/libs'
    } else {
        [System.IO.Path]::GetFullPath($ArtifactsRoot)
    }
    if (-not (Test-Path -LiteralPath $searchRoot -PathType Container)) {
        throw "Artifact search root is missing: $searchRoot"
    }

    $versionPattern = [regex]::Escape([string]$VersionEntry.modVersion)
    $idPattern = [regex]::Escape([string]$VersionEntry.id)
    $candidates = @(Get-ChildItem -LiteralPath $searchRoot -Recurse -File -Filter '*.jar' |
        Where-Object {
            $isSources = $_.Name.EndsWith('-sources.jar', [System.StringComparison]::OrdinalIgnoreCase)
            $sourceMatches = $isSources -eq $Sources
            $versionMatches = $_.Name -match ("-" + $versionPattern + "(?:-sources)?\.jar$")
            $scopeMatches = [string]::IsNullOrWhiteSpace($ArtifactsRoot) -or $_.FullName -match ("(^|[\\/])" + $idPattern + "([\\/]|$)")
            $sourceMatches -and $versionMatches -and $scopeMatches
        })
    if ($candidates.Count -ne 1) {
        $kind = if ($Sources) { 'sources' } else { 'main' }
        throw "Expected one $kind JAR for $($VersionEntry.id) $($VersionEntry.modVersion) under $searchRoot, found $($candidates.Count): $($candidates.FullName -join ', ')"
    }
    return $candidates[0].FullName
}

$manifest = Read-VersionManifest
$train = Read-ReleaseTrain $ReleaseManifest
if ([string]$train.train -notmatch '^release-[0-9]{4}\.[0-9]{2}-r[0-9]+(?:-beta\.[0-9]+)?$') {
    throw "Invalid release train tag: $($train.train)"
}

$output = [System.IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $output) {
    if (@(Get-ChildItem -LiteralPath $output -Force).Count -gt 0) {
        throw "Release output directory must be empty: $output"
    }
} else {
    New-Item -ItemType Directory -Path $output | Out-Null
}

$assetRows = [System.Collections.Generic.List[object]]::new()
foreach ($id in $train.versions) {
    $entry = Get-VersionEntry -Manifest $manifest -Id $id
    $mainSource = Find-BuiltArtifact -VersionEntry $entry -Sources $false
    $sourcesSource = Find-BuiltArtifact -VersionEntry $entry -Sources $true
    Test-ModJar -Path $mainSource -VersionEntry $entry

    $mainDestination = Join-Path $output $entry.release.artifact
    $sourcesDestination = Join-Path $output $entry.release.sourcesArtifact
    Copy-Item -LiteralPath $mainSource -Destination $mainDestination
    Copy-Item -LiteralPath $sourcesSource -Destination $sourcesDestination
    $assetRows.Add([ordered]@{
        id = $entry.id
        minecraft = $entry.minecraft.range
        modVersion = $entry.modVersion
        java = [int]$entry.java
        artifact = $entry.release.artifact
        sha256 = Get-Sha256 $mainDestination
        sourcesArtifact = $entry.release.sourcesArtifact
        sourcesSha256 = Get-Sha256 $sourcesDestination
    })
}

Copy-Item -LiteralPath (Resolve-RepositoryPath 'versions.json') -Destination (Join-Path $output 'versions.json')
$assetManifest = [ordered]@{
    schemaVersion = 1
    train = $train.train
    title = $train.title
    prerelease = [bool]$train.prerelease
    repository = $manifest.repository.slug
    commit = if ($env:GITHUB_SHA) { $env:GITHUB_SHA } else { (git -C (Get-RepositoryRoot) rev-parse HEAD).Trim() }
    assets = @($assetRows)
}
$assetsPath = Join-Path $output 'ASSETS.json'
[System.IO.File]::WriteAllText($assetsPath, ($assetManifest | ConvertTo-Json -Depth 20) + "`n", [System.Text.UTF8Encoding]::new($false))

$hashLines = @(Get-ChildItem -LiteralPath $output -File | Sort-Object Name | ForEach-Object {
    "$(Get-Sha256 $_.FullName)  $($_.Name)"
})
$hashPath = Join-Path $output 'SHA256SUMS.txt'
[System.IO.File]::WriteAllText($hashPath, ($hashLines -join "`n") + "`n", [System.Text.UTF8Encoding]::new($false))

Write-Host "Created verified release bundle '$($train.train)' with $($assetRows.Count) version lines at $output."
