[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$BranchName,
    [Parameter(Mandatory = $true)][string]$PullRequestTitle
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($BranchName.StartsWith('codex/', [System.StringComparison]::Ordinal) -and
        $BranchName -notmatch '^codex/mc-[a-z0-9.]+(?:-[a-z0-9.]+)*-mod-[a-z0-9.]+(?:-[a-z0-9.]+)*-[a-z0-9][a-z0-9-]*$') {
    throw "Codex branch '$BranchName' must use 'codex/mc-<Minecraft version>-mod-<Mod version>-<purpose>'."
}

if ($PullRequestTitle -notmatch '^\[MC [^\]]+\]\[Mod [^\]]+\]\s+\S') {
    throw "PR title '$PullRequestTitle' must use '[MC <Minecraft version>][Mod <Mod version>] <content>'."
}

Write-Host "Version labels are explicit in branch '$BranchName' and PR title '$PullRequestTitle'."
