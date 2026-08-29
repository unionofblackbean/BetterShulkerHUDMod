[CmdletBinding()]
param(
    [string]$JavaHome,
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

Write-Host ("Building {0} maintained source line(s) from versions.json: {1}" -f
    $entries.Count, ($entries | ForEach-Object { $_.id } -join ', '))

$results = [System.Collections.Generic.List[object]]::new()
$failed = 0
foreach ($entry in $entries) {
    $label = [string]$entry.id
    Write-Host ""
    Write-Host "==> [$label] mod $($entry.modVersion) (feature $($entry.featureLevel), $($entry.maintenanceLevel))" -ForegroundColor Cyan
    $arguments = @{ Id = $label }
    if ($JavaHome) { $arguments.JavaHome = $JavaHome }
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
