[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$ReleaseManifest)

$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'Manifest.psm1') -Force
$manifest = Read-VersionManifest
$train = Read-ReleaseTrain $ReleaseManifest
$rows = [System.Collections.Generic.List[object]]::new()
foreach ($id in $train.versions) {
    $entry = Get-VersionEntry -Manifest $manifest -Id $id
    $rows.Add([ordered]@{ id = $entry.id; path = $entry.path; java = [int]$entry.java })
}
Write-Output ([ordered]@{ include = @($rows) } | ConvertTo-Json -Depth 10 -Compress)
