[CmdletBinding()]
param(
    [ValidateSet('build', 'gametest', 'compat')][string]$Mode = 'build',
    [ValidateSet(0, 21, 25)][int]$Java = 0
)

$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'Manifest.psm1') -Force
$matrix = Get-BuildMatrix -Manifest (Read-VersionManifest) -Mode $Mode -Java $Java
Write-Output ($matrix | ConvertTo-Json -Depth 10 -Compress)
