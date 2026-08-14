[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'Manifest.psm1') -Force
$manifest = Read-VersionManifest
$optionalIds = @('quickshulker', 'itemscroller', 'litematica', 'modernui', 'cozyui', 'cozyui-plus')

foreach ($entry in $manifest.versions) {
    $projectPath = Resolve-RepositoryPath $entry.path
    $fabricPath = Join-Path $projectPath 'src/main/resources/fabric.mod.json'
    $fabric = Get-Content -LiteralPath $fabricPath -Raw -Encoding utf8 | ConvertFrom-Json
    foreach ($optionalId in $optionalIds) {
        if ($null -ne $fabric.depends.PSObject.Properties[$optionalId]) {
            throw "$($entry.id): optional compatibility '$optionalId' became a hard Fabric dependency."
        }
    }

    $mixinPath = Join-Path $projectPath 'src/client/resources/better-shulker-hud.client.mixins.json'
    if (-not (Test-Path -LiteralPath $mixinPath -PathType Leaf)) {
        throw "$($entry.id): client mixin configuration is missing."
    }
    $mixin = Get-Content -LiteralPath $mixinPath -Raw -Encoding utf8 | ConvertFrom-Json
    $declared = @($mixin.mixins) + @($mixin.client)
    $duplicates = @($declared | Group-Object | Where-Object Count -gt 1)
    if ($duplicates.Count -gt 0) {
        throw "$($entry.id): duplicate mixin declarations: $($duplicates.Name -join ', ')"
    }
    foreach ($className in $declared) {
        if ([string]::IsNullOrWhiteSpace([string]$className)) {
            continue
        }
        $qualifiedName = [string]$mixin.package + '.' + [string]$className
        $classParts = $qualifiedName -split '\.'
        $simpleName = $classParts[-1]
        $expectedPackage = ($classParts[0..($classParts.Count - 2)] -join '.')
        $sourceCandidates = @(Get-ChildItem -LiteralPath (Join-Path $projectPath 'src/client/java') -Recurse -File -Filter "$simpleName.java" |
            Where-Object { (Get-Content -LiteralPath $_.FullName -Raw -Encoding utf8) -match ('(?m)^\s*package\s+' + [regex]::Escape($expectedPackage) + '\s*;') })
        if ($sourceCandidates.Count -ne 1) {
            throw "$($entry.id): expected exactly one source for mixin $qualifiedName, found $($sourceCandidates.Count)."
        }
    }
}

Write-Host 'Optional-mod dependency boundaries and mixin source anchors are valid.'
