[CmdletBinding()]
param([switch]$SkipGeneratedDocs)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'Manifest.psm1') -Force

function Assert-Equal {
    param(
        [Parameter(Mandatory = $true)]$Actual,
        [Parameter(Mandatory = $true)]$Expected,
        [Parameter(Mandatory = $true)][string]$Label
    )
    if ([string]$Actual -cne [string]$Expected) {
        throw "$Label mismatch: expected '$Expected', found '$Actual'."
    }
}

function Get-JsonPropertyValue {
    param($Object, [string]$Name)
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $null
    }
    return $property.Value
}

$manifest = Read-VersionManifest
Assert-Equal $manifest.schemaVersion 1 'versions.json schemaVersion'
Assert-Equal $manifest.repository.slug 'unionofblackbean/BetterShulkerHUDMod' 'repository slug'
Assert-Equal $manifest.repository.defaultBranch 'master' 'default branch'

$ids = @($manifest.versions | ForEach-Object { [string]$_.id })
$duplicateIds = @($ids | Group-Object | Where-Object Count -gt 1)
if ($duplicateIds.Count -gt 0) {
    throw "Duplicate version IDs: $($duplicateIds.Name -join ', ')."
}

$sourceDirectories = @(Get-ChildItem -LiteralPath (Resolve-RepositoryPath 'versions') -Directory | ForEach-Object Name | Sort-Object)
$manifestDirectories = @($ids | Sort-Object)
if (($sourceDirectories -join "`n") -cne ($manifestDirectories -join "`n")) {
    throw "versions.json must list every versions/* source line exactly once. Found directories [$($sourceDirectories -join ', ')], manifest [$($manifestDirectories -join ', ')]."
}

foreach ($entry in $manifest.versions) {
    $label = [string]$entry.id
    $projectPath = Resolve-RepositoryPath $entry.path
    if (-not (Test-Path -LiteralPath $projectPath -PathType Container)) {
        throw "$label project path does not exist: $projectPath"
    }

    $propertiesPath = Join-Path $projectPath 'gradle.properties'
    $properties = Read-GradleProperties $propertiesPath
    Assert-Equal $properties.minecraft_version $entry.minecraft.build "$label minecraft_version"
    Assert-Equal $properties.mod_version $entry.modVersion "$label mod_version"
    Assert-Equal $properties.loader_version $entry.loader.build "$label loader_version"
    Assert-Equal $properties.fabric_api_version $entry.fabricApi "$label fabric_api_version"
    Assert-Equal $properties.loom_version $entry.loom "$label loom_version"

    $wrapperProperties = Get-Content -LiteralPath (Join-Path $projectPath 'gradle/wrapper/gradle-wrapper.properties') -Raw -Encoding utf8
    if ($wrapperProperties -notmatch ('gradle-' + [regex]::Escape([string]$entry.gradle) + '-bin\.zip')) {
        throw "$label Gradle wrapper does not match versions.json Gradle $($entry.gradle)."
    }
    if (-not (Test-Path -LiteralPath (Join-Path $projectPath 'gradle/wrapper/gradle-wrapper.jar') -PathType Leaf)) {
        throw "$label Gradle wrapper JAR is missing."
    }

    $fabricPath = Join-Path $projectPath 'src/main/resources/fabric.mod.json'
    $fabric = Get-Content -LiteralPath $fabricPath -Raw -Encoding utf8 | ConvertFrom-Json
    Assert-Equal $fabric.id 'better-shulker-hud' "$label Fabric mod ID"
    Assert-Equal (Get-JsonPropertyValue $fabric.depends 'minecraft') $entry.minecraft.range "$label fabric.mod.json Minecraft range"
    Assert-Equal (Get-JsonPropertyValue $fabric.depends 'fabricloader') $entry.loader.requirement "$label fabric.mod.json Loader requirement"
    Assert-Equal (Get-JsonPropertyValue $fabric.depends 'java') ">=$($entry.java)" "$label fabric.mod.json Java requirement"

    $buildPath = Join-Path $projectPath 'build.gradle'
    $build = Get-Content -LiteralPath $buildPath -Raw -Encoding utf8
    if ($build -notmatch [regex]::Escape([string]$entry.malilib)) {
        throw "$label MaLiLib coordinate differs from versions.json: $($entry.malilib)."
    }
    if ($build -notmatch ('options\.release\s*=\s*' + [regex]::Escape([string]$entry.java))) {
        throw "$label Java compile release is not $($entry.java)."
    }
    if ($build -notmatch "exclude\s+group:\s*'net\.fabricmc',\s*module:\s*'fabric-loader'") {
        throw "$label does not exclude the transitive Fabric Loader from MaLiLib."
    }

    $expectedSources = ([System.IO.Path]::GetFileNameWithoutExtension([string]$entry.release.artifact)) + '-sources.jar'
    Assert-Equal $entry.release.sourcesArtifact $expectedSources "$label source artifact name"
    if (-not ([string]$entry.release.artifact).StartsWith([string]$manifest.repository.releaseArtifactPrefix + '-')) {
        throw "$label release artifact must use the repository artifact prefix."
    }
    if ([string]$entry.release.artifact -notmatch ('-' + [regex]::Escape([string]$entry.modVersion) + '\.jar$')) {
        throw "$label release artifact does not contain Mod version $($entry.modVersion)."
    }

    if ([bool]$entry.tests.clientGameTest) {
        if (-not (Test-Path -LiteralPath (Join-Path $projectPath 'src/clientGameTest') -PathType Container)) {
            throw "$label is marked clientGameTest=true but has no src/clientGameTest."
        }
        if ($build -notmatch 'runClientGameTest|clientGameTest') {
            throw "$label is marked clientGameTest=true but has no Loom Client GameTest configuration."
        }
    } elseif (@($entry.tests.compatibilityProfiles).Count -gt 0) {
        throw "$label declares compatibility profiles without a Client GameTest lane."
    }

    $profileIds = @($entry.tests.compatibilityProfiles | ForEach-Object { [string]$_.id })
    $duplicateProfileIds = @($profileIds | Group-Object | Where-Object Count -gt 1)
    if ($duplicateProfileIds.Count -gt 0) {
        throw "$label has duplicate compatibility profiles: $($duplicateProfileIds.Name -join ', ')."
    }
    foreach ($profile in $entry.tests.compatibilityProfiles) {
        if ([string]$profile.id -notmatch '^[a-z0-9_-]+$') {
            throw "$label has an unsafe compatibility profile ID: $($profile.id)."
        }
        if ([string]$profile.property -notmatch '^[a-z0-9_]+$') {
            throw "$label has an unsafe compatibility property: $($profile.property)."
        }
        $requiresLocalArtifact = [bool](Get-JsonPropertyValue $profile 'requiresLocalArtifact')
        if ([bool]$profile.ci -and $requiresLocalArtifact) {
            throw "$label profile '$($profile.id)' cannot require a local artifact in CI."
        }
        $propertyPattern = "hasProperty\('" + [regex]::Escape([string]$profile.property) + "'\)"
        if ($build -notmatch $propertyPattern) {
            throw "$label profile '$($profile.id)' is not isolated behind $($profile.property)."
        }
    }
}

$legacy = $manifest.legacyRoot
if ([bool]$legacy.includedInCi) {
    throw 'The historical root project must remain excluded from maintained CI.'
}
$legacyProperties = Read-GradleProperties (Resolve-RepositoryPath 'gradle.properties')
Assert-Equal $legacyProperties.minecraft_version $legacy.minecraft 'legacy root Minecraft version'
Assert-Equal $legacyProperties.mod_version $legacy.modVersion 'legacy root Mod version'

$releaseManifests = @(Get-ChildItem -LiteralPath (Resolve-RepositoryPath 'release-trains') -File -Filter 'release-*.json' |
    Where-Object Name -ne 'release.schema.json')
foreach ($releaseFile in $releaseManifests) {
    $train = Read-ReleaseTrain $releaseFile.FullName
    Assert-Equal $train.schemaVersion 1 "$($releaseFile.Name) schemaVersion"
    Assert-Equal ($releaseFile.BaseName) $train.train "$($releaseFile.Name) train"
    if ([string]$train.train -notmatch '^release-[0-9]{4}\.[0-9]{2}-r[0-9]+(?:-beta\.[0-9]+)?$') {
        throw "$($releaseFile.Name) has an invalid release train tag."
    }
    $trainIds = @($train.versions)
    if (@($trainIds | Group-Object | Where-Object Count -gt 1).Count -gt 0) {
        throw "$($releaseFile.Name) contains duplicate version IDs."
    }
    foreach ($id in $trainIds) {
        $null = Get-VersionEntry -Manifest $manifest -Id $id
    }
    if (-not (Test-Path -LiteralPath (Resolve-RepositoryPath $train.notesFile) -PathType Leaf)) {
        throw "$($releaseFile.Name) notes file is missing: $($train.notesFile)."
    }
}

if (-not $SkipGeneratedDocs) {
    & (Join-Path $PSScriptRoot 'Update-VersionDocs.ps1') -Check
}

Write-Host "versions.json is consistent with $($manifest.versions.Count) maintained source lines."
