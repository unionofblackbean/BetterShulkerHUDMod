[CmdletBinding()]
param(
    [ValidateSet('build', 'gametest', 'compat')][string]$Mode = 'build',
    [ValidateSet(0, 21, 25)][int]$Java = 0,
    [switch]$IncludeDeprecated
)

$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'Manifest.psm1') -Force
$matrix = Get-BuildMatrix -Manifest (Read-VersionManifest) -Mode $Mode -Java $Java -IncludeDeprecated:$IncludeDeprecated
Write-Output ($matrix | ConvertTo-Json -Depth 10 -Compress)
