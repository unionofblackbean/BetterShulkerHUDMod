Set-StrictMode -Version Latest

function Get-RepositoryRoot {
    return [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
}

function Resolve-RepositoryPath {
    param([Parameter(Mandatory = $true)][string]$Path)

    $nativePath = $Path.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
    return [System.IO.Path]::GetFullPath((Join-Path (Get-RepositoryRoot) $nativePath))
}

function Read-VersionManifest {
    $manifestPath = Resolve-RepositoryPath 'versions.json'
    return Get-Content -LiteralPath $manifestPath -Raw -Encoding utf8 | ConvertFrom-Json
}

function Read-GradleProperties {
    param([Parameter(Mandatory = $true)][string]$Path)

    $properties = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding utf8) {
        if ($line -match '^\s*([^#!\s][^=]*?)\s*=\s*(.*?)\s*$') {
            $properties[$matches[1].Trim()] = $matches[2]
        }
    }
    return $properties
}

function Get-VersionEntry {
    param(
        [Parameter(Mandatory = $true)]$Manifest,
        [Parameter(Mandatory = $true)][string]$Id
    )

    $entry = @($Manifest.versions | Where-Object { $_.id -eq $Id })
    if ($entry.Count -ne 1) {
        throw "Expected exactly one versions.json entry for '$Id', found $($entry.Count)."
    }
    return $entry[0]
}

function Get-MaintainedVersionsTable {
    param([Parameter(Mandatory = $true)]$Manifest)

    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add('| Minecraft | Mod | 功能线 | 维护等级 | Java | 源码 |')
    $lines.Add('| --- | --- | --- | --- | --- | --- |')
    foreach ($entry in $Manifest.versions) {
        $range = $entry.minecraft.range
        $lines.Add("| ``$range`` | ``$($entry.modVersion)`` | $($entry.featureSummary) | ``$($entry.maintenanceLevel)`` | $($entry.java) | [``$($entry.path)``]($($entry.path)) |")
    }
    return $lines -join "`n"
}

function Get-VersionMatrixTable {
    param([Parameter(Mandatory = $true)]$Manifest)

    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add('| 源码目录 | Minecraft 范围 | Mod | Java | Loader | Fabric API | MaLiLib | 功能线 | 发布状态 |')
    $lines.Add('| --- | --- | --- | --- | --- | --- | --- | --- | --- |')
    foreach ($entry in $Manifest.versions) {
        $malilibVersion = ($entry.malilib -split ':')[-1]
        $lines.Add("| ``$($entry.path)`` | ``$($entry.minecraft.range)`` | ``$($entry.modVersion)`` | $($entry.java) | ``$($entry.loader.build)`` | ``$($entry.fabricApi)`` | ``$malilibVersion`` | ``$($entry.featureLevel)`` | ``$($entry.release.status)`` |")
    }
    return $lines -join "`n"
}

function Get-CompatibilityMatrixTable {
    param([Parameter(Mandatory = $true)]$Manifest)

    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add('| Minecraft 构建线 | QuickShulker | REI | JEI | EMI | Item Scroller |')
    $lines.Add('| --- | --- | --- | --- | --- | --- |')
    foreach ($entry in $Manifest.versions) {
        $compat = $entry.optionalCompatibility
        $quickProperty = $compat.PSObject.Properties['quickShulker']
        $reiProperty = $compat.PSObject.Properties['rei']
        $jeiProperty = $compat.PSObject.Properties['jei']
        $emiProperty = $compat.PSObject.Properties['emi']
        $itemScrollerProperty = $compat.PSObject.Properties['itemScroller']
        $quick = if ($null -ne $quickProperty) { $quickProperty.Value } else { '—' }
        $rei = if ($null -ne $reiProperty) { $reiProperty.Value } else { '—' }
        $jei = if ($null -ne $jeiProperty) { $jeiProperty.Value } else { '—' }
        $emi = if ($null -ne $emiProperty) { $emiProperty.Value } else { '—' }
        $itemScroller = if ($null -ne $itemScrollerProperty) { $itemScrollerProperty.Value } else { '—' }
        $lines.Add("| ``$($entry.id)`` | ``$quick`` | ``$rei`` | ``$jei`` | ``$emi`` | ``$itemScroller`` |")
    }
    return $lines -join "`n"
}

function Set-GeneratedMarkdownBlock {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Content,
        [switch]$Check
    )

    $start = "<!-- generated:$Name`:start -->"
    $end = "<!-- generated:$Name`:end -->"
    $original = Get-Content -LiteralPath $Path -Raw -Encoding utf8
    $normalized = $original.Replace("`r`n", "`n")
    $pattern = '(?s)' + [regex]::Escape($start) + '.*?' + [regex]::Escape($end)
    if (-not [regex]::IsMatch($normalized, $pattern)) {
        throw "Generated block '$Name' is missing from $Path."
    }

    $expectedBlock = "$start`n$($Content.TrimEnd())`n$end"
    $regex = [regex]::new($pattern)
    $updated = $regex.Replace($normalized, $expectedBlock, 1)
    if ($Check) {
        if ($updated -cne $normalized) {
            throw "$Path contains stale generated block '$Name'. Run scripts/Update-VersionDocs.ps1."
        }
        return
    }

    if ($updated -cne $normalized) {
        [System.IO.File]::WriteAllText($Path, $updated, [System.Text.UTF8Encoding]::new($false))
    }
}

function Get-BuildMatrix {
    param(
        [Parameter(Mandatory = $true)]$Manifest,
        [ValidateSet('build', 'gametest', 'compat')][string]$Mode = 'build',
        [int]$Java = 0
    )

    $rows = [System.Collections.Generic.List[object]]::new()
    foreach ($entry in $Manifest.versions) {
        if ($Java -ne 0 -and [int]$entry.java -ne $Java) {
            continue
        }
        if ($Mode -eq 'gametest' -and -not [bool]$entry.tests.clientGameTest) {
            continue
        }
        if ($Mode -eq 'compat') {
            if (-not [bool]$entry.tests.clientGameTest) {
                continue
            }
            foreach ($profile in $entry.tests.compatibilityProfiles) {
                if ([bool]$profile.ci) {
                    $rows.Add([ordered]@{
                        id = $entry.id
                        path = $entry.path
                        java = [int]$entry.java
                        profile = $profile.id
                        property = $profile.property
                    })
                }
            }
            continue
        }
        $rows.Add([ordered]@{
            id = $entry.id
            path = $entry.path
            java = [int]$entry.java
        })
    }
    return [ordered]@{ include = @($rows) }
}

function Read-ReleaseTrain {
    param([Parameter(Mandatory = $true)][string]$Path)

    $fullPath = if ([System.IO.Path]::IsPathRooted($Path)) { $Path } else { Resolve-RepositoryPath $Path }
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        throw "Release train manifest not found: $fullPath"
    }
    return Get-Content -LiteralPath $fullPath -Raw -Encoding utf8 | ConvertFrom-Json
}

Export-ModuleMember -Function Get-RepositoryRoot, Resolve-RepositoryPath, Read-VersionManifest, Read-GradleProperties, Get-VersionEntry, Get-MaintainedVersionsTable, Get-VersionMatrixTable, Get-CompatibilityMatrixTable, Set-GeneratedMarkdownBlock, Get-BuildMatrix, Read-ReleaseTrain
