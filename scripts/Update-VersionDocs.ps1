[CmdletBinding()]
param([switch]$Check)

$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'Manifest.psm1') -Force

$manifest = Read-VersionManifest
$root = Get-RepositoryRoot

Set-GeneratedMarkdownBlock -Path (Join-Path $root 'README.md') -Name 'maintained-versions' -Content (Get-MaintainedVersionsTable $manifest) -Check:$Check
Set-GeneratedMarkdownBlock -Path (Join-Path $root 'VERSION_MATRIX.md') -Name 'version-matrix' -Content (Get-VersionMatrixTable $manifest) -Check:$Check
Set-GeneratedMarkdownBlock -Path (Join-Path $root 'VERSION_MATRIX.md') -Name 'compatibility-matrix' -Content (Get-CompatibilityMatrixTable $manifest) -Check:$Check

if ($Check) {
    Write-Host 'Generated version documentation is current.'
} else {
    Write-Host 'Updated generated version documentation from versions.json.'
}
