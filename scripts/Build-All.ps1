[CmdletBinding()]
param(
    [string]$JavaHome,
    [string]$Java21Home,
    [string]$Java25Home,
    [switch]$Clean,
    [switch]$ContinueOnError,
    [string[]]$Id
)

$ErrorActionPreference = 'Stop'

$buildScript = Join-Path $PSScriptRoot 'Build-Version.ps1'
if (-not (Test-Path -LiteralPath $buildScript -PathType Leaf)) {
    throw "Build-Version.ps1 not found next to this script."
}

Import-Module (Join-Path $PSScriptRoot 'Manifest.psm1') -Force
$manifest = Read-VersionManifest

$entries = @($manifest.versions)
if ($Id) {
    $wanted = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($value in $Id) {
        [void]$wanted.Add($value)
    }
    $entries = @($entries | Where-Object { $wanted.Contains([string]$_.id) })
    $missing = @()
    foreach ($value in $Id) {
        $found = @($manifest.versions | Where-Object { [string]$_.id -eq $value })
        if ($found.Count -eq 0) {
            $missing += $value
        }
    }
    if ($missing.Count -gt 0) {
        throw "Unknown version ID(s): $($missing -join ', ')."
    }
}

$entryIds = ($entries | ForEach-Object { [string]$_.id }) -join ', '
Write-Host ("Building {0} maintained source line(s) from versions.json: {1}" -f
    $entries.Count, $entryIds)

$results = [System.Collections.Generic.List[object]]::new()
$failed = 0

function Find-JavaHome {
    param([Parameter(Mandatory = $true)][int]$Major)

    $explicit = if ($Major -eq 21) { $Java21Home } else { $Java25Home }
    if (-not [string]::IsNullOrWhiteSpace($explicit)) {
        return [System.IO.Path]::GetFullPath($explicit)
    }

    # Keep the historical -JavaHome switch as an explicit all-versions
    # override. Build-Version.ps1 still validates the actual Java major.
    if (-not [string]::IsNullOrWhiteSpace($JavaHome)) {
        return [System.IO.Path]::GetFullPath($JavaHome)
    }

    $environmentNames = if ($Major -eq 21) {
        @('JAVA21_HOME', 'JAVA_HOME_21_X64')
    } else {
        @('JAVA25_HOME', 'JAVA_HOME_25_X64')
    }
    foreach ($name in $environmentNames) {
        $value = [Environment]::GetEnvironmentVariable($name)
        if (-not [string]::IsNullOrWhiteSpace($value) -and
                (Test-Path -LiteralPath (Join-Path $value 'bin/java.exe') -PathType Leaf)) {
            return [System.IO.Path]::GetFullPath($value)
        }
    }

    $candidates = if ($Major -eq 21) {
        @(
            (Join-Path ${env:ProgramFiles} 'Java/latest/jdk-21'),
            (Join-Path ${env:ProgramFiles} 'Eclipse Adoptium/jdk-21'),
            'C:\Program Files\Java\jdk-21'
        )
    } else {
        @(
            'D:\JAVA25',
            (Join-Path ${env:ProgramFiles} 'Java/latest/jdk-25'),
            (Join-Path ${env:ProgramFiles} 'Eclipse Adoptium/jdk-25'),
            'C:\Program Files\Java\jdk-25'
        )
    }
    foreach ($candidate in $candidates) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and
                (Test-Path -LiteralPath (Join-Path $candidate 'bin/java.exe') -PathType Leaf)) {
            return [System.IO.Path]::GetFullPath($candidate)
        }
    }

    throw "Java $Major was not found. Pass -Java${Major}Home <JDK directory> or set JAVA${Major}_HOME."
}

foreach ($entry in $entries) {
    $label = [string]$entry.id
    Write-Host ""
    Write-Host "==> [$label] mod $($entry.modVersion) (feature $($entry.featureLevel), $($entry.maintenanceLevel))" -ForegroundColor Cyan
    $arguments = @{ Id = $label }
    $arguments.JavaHome = Find-JavaHome -Major ([int]$entry.java)
    if ($Clean) { $arguments.Clean = $true }
    try {
        & $buildScript @arguments
        $results.Add([pscustomobject]@{ Id = $label; ModVersion = $entry.modVersion; Result = 'ok' })
    } catch {
        $failed++
        $results.Add([pscustomobject]@{ Id = $label; ModVersion = $entry.modVersion; Result = "FAILED: $($_.Exception.Message)" })
        if (-not $ContinueOnError) {
            break
        }
    }
}

Write-Host ""
Write-Host "Build summary:" -ForegroundColor Cyan
$results | Format-Table -AutoSize | Out-String | Write-Host
if ($failed -gt 0) {
    throw "$failed of $($entries.Count) version build(s) failed."
}
Write-Host "All $($entries.Count) version build(s) succeeded."
