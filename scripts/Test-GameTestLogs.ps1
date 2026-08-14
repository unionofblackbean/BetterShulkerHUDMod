[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$VersionPath,
    [string]$ExpectedProfile
)

$ErrorActionPreference = 'Stop'
$resolved = [System.IO.Path]::GetFullPath($VersionPath)
$preferredLog = Join-Path $resolved 'build/run/clientGameTest/logs/latest.log'
if (Test-Path -LiteralPath $preferredLog -PathType Leaf) {
    $log = Get-Item -LiteralPath $preferredLog
} else {
    $log = Get-ChildItem -LiteralPath $resolved -Recurse -File -Filter 'latest.log' `
            -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match '[\\/]build[\\/](clientGameTest|run|runs)[\\/]' } |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
}

if ($null -eq $log) {
    throw "Client GameTest produced no latest.log under $resolved."
}

$text = Get-Content -LiteralPath $log.FullName -Raw -Encoding utf8
if ($text -match '(?is)ClassCastException' -and
        $text -match '(?is)(loader\s+[\x27\x22]?knot|KnotClassLoader)' -and
        $text -match '(?is)(loader\s+[\x27\x22]?app|AppClassLoader|app classloader)') {
    throw "Client GameTest Knot/app classloader failure detected in $($log.FullName)."
}
if ($text -match '(?is)(Failed to read classTweaker|ClassTweakerFormatException|Namespace \([^\r\n]+\) does not match current runtime namespace)') {
    throw "Client GameTest classTweaker namespace failure detected in $($log.FullName)."
}
if ($text -match '(?im)^.*(Minecraft has crashed!|Uncaught exception in thread [\x22\x27]main[\x22\x27]).*$') {
    throw "Client GameTest crashed according to $($log.FullName)."
}

$start = [regex]::Match($text, 'BSH_CLIENT_GAMETEST_START profile=([a-z0-9_-]+)')
$success = [regex]::Match($text, 'BSH_CLIENT_GAMETEST_SUCCESS profile=([a-z0-9_-]+)')
if (-not $start.Success) {
    throw "Client GameTest entrypoint did not start according to $($log.FullName)."
}
if (-not $success.Success) {
    throw "Client GameTest did not reach its success marker according to $($log.FullName)."
}
if ($start.Groups[1].Value -ne $success.Groups[1].Value) {
    throw "Client GameTest profile changed during execution in $($log.FullName)."
}
if (-not [string]::IsNullOrWhiteSpace($ExpectedProfile) -and
        $success.Groups[1].Value -ne $ExpectedProfile) {
    throw "Client GameTest ran profile '$($success.Groups[1].Value)' instead of expected profile '$ExpectedProfile' in $($log.FullName)."
}

Write-Host "Client GameTest profile '$($success.Groups[1].Value)' completed without classloader, classTweaker, or crash failures ($($log.FullName))."
