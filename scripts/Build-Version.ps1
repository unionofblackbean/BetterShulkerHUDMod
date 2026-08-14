[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Id,
    [string]$JavaHome,
    [switch]$Clean,
    [switch]$GameTest,
    [string]$CompatibilityProfile,
    [string]$LocalArtifact
)

$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'Manifest.psm1') -Force
$manifest = Read-VersionManifest
$entry = Get-VersionEntry -Manifest $manifest -Id $Id
$projectPath = Resolve-RepositoryPath $entry.path

if (-not $GameTest -and
        (-not [string]::IsNullOrWhiteSpace($CompatibilityProfile) -or
         -not [string]::IsNullOrWhiteSpace($LocalArtifact))) {
    throw '-CompatibilityProfile and -LocalArtifact require -GameTest.'
}

$oldJavaHome = $env:JAVA_HOME
$oldPath = $env:PATH
$oldGradleUserHome = $env:GRADLE_USER_HOME
$initialLocation = (Get-Location).Path
$projectSubstDrive = $null
$gradleSubstDrive = $null
$localArtifactTempDirectory = $null

function New-ShortPathDrive {
    param(
        [Parameter(Mandatory = $true)][string]$TargetPath,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()]
        [System.Collections.Generic.HashSet[string]]$ReservedLetters
    )

    $resolvedTarget = [System.IO.Path]::GetFullPath($TargetPath).TrimEnd('\')
    if (-not (Test-Path -LiteralPath $resolvedTarget -PathType Container)) {
        throw "Short-path target directory does not exist: $resolvedTarget"
    }

    $usedLetters = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::OrdinalIgnoreCase)
    foreach ($root in [System.IO.Directory]::GetLogicalDrives()) {
        [void]$usedLetters.Add($root.Substring(0, 1))
    }
    foreach ($letter in $ReservedLetters) {
        [void]$usedLetters.Add($letter)
    }

    $substExecutable = Join-Path $env:SystemRoot 'System32\subst.exe'
    foreach ($codePoint in 90..68) {
        $letter = [char]$codePoint
        if ($usedLetters.Contains([string]$letter)) {
            continue
        }

        $drive = "${letter}:"
        & $substExecutable $drive $resolvedTarget | Out-Null
        if ($LASTEXITCODE -ne 0) {
            continue
        }
        if (-not (Test-Path -LiteralPath "$drive\" -PathType Container)) {
            & $substExecutable $drive '/D' | Out-Null
            continue
        }

        [void]$ReservedLetters.Add([string]$letter)
        return $drive
    }

    throw "Unable to allocate a temporary short-path drive for $resolvedTarget."
}

function Remove-ShortPathDrive {
    param([AllowNull()][string]$Drive)

    if ([string]::IsNullOrWhiteSpace($Drive)) {
        return
    }
    $substExecutable = Join-Path $env:SystemRoot 'System32\subst.exe'
    & $substExecutable $Drive '/D' | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Unable to remove temporary short-path drive $Drive."
    }
}

try {
    if (-not [string]::IsNullOrWhiteSpace($JavaHome)) {
        $resolvedJavaHome = [System.IO.Path]::GetFullPath($JavaHome)
        $javaRelativePath = if ($env:OS -eq 'Windows_NT') { 'bin/java.exe' } else { 'bin/java' }
        $javaExecutable = Join-Path $resolvedJavaHome $javaRelativePath
        if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf)) {
            throw "Java executable not found: $javaExecutable"
        }
        $env:JAVA_HOME = $resolvedJavaHome
        $env:PATH = (Join-Path $resolvedJavaHome 'bin') + [System.IO.Path]::PathSeparator + $oldPath
    }

    $javaRelativePath = if ($env:OS -eq 'Windows_NT') { 'bin/java.exe' } else { 'bin/java' }
    $javaCommand = Join-Path $env:JAVA_HOME $javaRelativePath
    # Java writes -XshowSettings to stderr even on success. Windows PowerShell
    # otherwise promotes that normal output to a terminating NativeCommandError.
    $savedErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $javaSettings = (& $javaCommand -XshowSettings:properties -version 2>&1 | ForEach-Object { $_.ToString() } | Out-String)
        $javaExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $savedErrorPreference
    }
    if ($javaExitCode -ne 0) {
        throw "Java version probe failed with exit code $javaExitCode."
    }
    if ($javaSettings -notmatch 'java\.specification\.version\s*=\s*([0-9]+)') {
        throw 'Unable to determine java.specification.version.'
    }
    if ([int]$matches[1] -ne [int]$entry.java) {
        throw "$Id requires Java $($entry.java), but JAVA_HOME provides Java $($matches[1])."
    }

    $isWindowsHost = $env:OS -eq 'Windows_NT'
    $executionProjectPath = $projectPath
    if ($GameTest -and $isWindowsHost) {
        # Gradle switches to a classpath JAR when the Windows command line is too
        # long. Fabric Loader cannot recover the real library boundary from that
        # JAR, which can load Loader/Mixin twice (Knot vs app classloaders). Map
        # the project (and, for the longer Java 21 remap classpath, the Gradle
        # cache) to temporary short paths so Loom can keep using its normal
        # argument file. Java 25 canonicalizes a substituted Gradle-cache path
        # back to its physical path for app-loader code sources; keeping that
        # cache on its physical path avoids a false Knot/app library mismatch in
        # unobfuscated 26.x. Mappings are removed in the outer finally block.
        $reservedLetters = [System.Collections.Generic.HashSet[string]]::new(
            [System.StringComparer]::OrdinalIgnoreCase)
        $projectSubstDrive = New-ShortPathDrive `
            -TargetPath $projectPath -ReservedLetters $reservedLetters

        $executionProjectPath = "$projectSubstDrive\"
        if ([int]$entry.java -lt 25) {
            $gradleUserHome = if ([string]::IsNullOrWhiteSpace($env:GRADLE_USER_HOME)) {
                Join-Path $env:USERPROFILE '.gradle'
            } else {
                $env:GRADLE_USER_HOME
            }
            $gradleUserHome = [System.IO.Path]::GetFullPath($gradleUserHome)
            if (-not (Test-Path -LiteralPath $gradleUserHome -PathType Container)) {
                New-Item -ItemType Directory -Path $gradleUserHome -Force | Out-Null
            }
            $gradleSubstDrive = New-ShortPathDrive `
                -TargetPath $gradleUserHome -ReservedLetters $reservedLetters
            $env:GRADLE_USER_HOME = "$gradleSubstDrive\"
        }
    }

    $wrapperName = if ($isWindowsHost) { 'gradlew.bat' } else { 'gradlew' }
    $wrapper = Join-Path $executionProjectPath $wrapperName
    $arguments = [System.Collections.Generic.List[string]]::new()
    $arguments.Add('--no-daemon')
    if ($Clean) {
        $arguments.Add('clean')
    }
    if ($GameTest) {
        if (-not [bool]$entry.tests.clientGameTest) {
            throw "$Id does not declare a Client GameTest lane."
        }
        $arguments.Add('runClientGameTest')
        $expectedGameTestProfile = 'base'
        if (-not [string]::IsNullOrWhiteSpace($CompatibilityProfile)) {
            $profile = @($entry.tests.compatibilityProfiles | Where-Object id -eq $CompatibilityProfile)
            if ($profile.Count -ne 1) {
                throw "$Id does not declare compatibility profile '$CompatibilityProfile'."
            }
            if ([bool]$profile[0].requiresLocalArtifact) {
                if ([string]::IsNullOrWhiteSpace($LocalArtifact)) {
                    throw "$Id profile '$CompatibilityProfile' requires -LocalArtifact <path>."
                }
                $resolvedLocalArtifact = [System.IO.Path]::GetFullPath($LocalArtifact)
                if (-not (Test-Path -LiteralPath $resolvedLocalArtifact -PathType Leaf)) {
                    throw "Local compatibility artifact not found: $resolvedLocalArtifact"
                }
                # Gradle's Windows launcher can corrupt a non-ASCII -P path
                # before Fabric Loader reads it. Stage the user-supplied jar at
                # a short ASCII-only temporary path and remove it in finally.
                $localArtifactTempDirectory = Join-Path `
                    ([System.IO.Path]::GetTempPath()) `
                    ('bsh-compat-' + [guid]::NewGuid().ToString('N'))
                New-Item -ItemType Directory -Path $localArtifactTempDirectory | Out-Null
                $artifactExtension = [System.IO.Path]::GetExtension($resolvedLocalArtifact)
                $stagedLocalArtifact = Join-Path `
                    $localArtifactTempDirectory ("compat-artifact$artifactExtension")
                Copy-Item -LiteralPath $resolvedLocalArtifact -Destination $stagedLocalArtifact
                $arguments.Add("-P$($profile[0].property)=$stagedLocalArtifact")
            } else {
                if (-not [string]::IsNullOrWhiteSpace($LocalArtifact)) {
                    throw "$Id profile '$CompatibilityProfile' does not accept -LocalArtifact."
                }
                $arguments.Add("-P$($profile[0].property)=true")
            }
            $expectedGameTestProfile = [string]$profile[0].id
        } elseif (-not [string]::IsNullOrWhiteSpace($LocalArtifact)) {
            throw '-LocalArtifact requires -CompatibilityProfile.'
        }
    } else {
        $arguments.Add('build')
    }

    Push-Location $executionProjectPath
    try {
        if ($isWindowsHost) {
            & $wrapper @arguments
        } else {
            # Several historical source lines keep gradlew as mode 100644.
            # Running it through bash keeps Linux CI reproducible without
            # rewriting those source snapshots merely to change file modes.
            & bash $wrapper @arguments
        }
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle failed for $Id with exit code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }

    if ($GameTest) {
        & (Join-Path $PSScriptRoot 'Test-GameTestLogs.ps1') `
                -VersionPath $projectPath -ExpectedProfile $expectedGameTestProfile
    }
} finally {
    # Leave any substituted drive before trying to remove it.
    Set-Location (Get-RepositoryRoot)
    Remove-ShortPathDrive -Drive $projectSubstDrive
    Remove-ShortPathDrive -Drive $gradleSubstDrive
    if (-not [string]::IsNullOrWhiteSpace($localArtifactTempDirectory) -and
            (Test-Path -LiteralPath $localArtifactTempDirectory -PathType Container)) {
        Remove-Item -LiteralPath $localArtifactTempDirectory -Recurse -Force
    }
    $env:JAVA_HOME = $oldJavaHome
    $env:PATH = $oldPath
    if ($null -eq $oldGradleUserHome) {
        Remove-Item Env:GRADLE_USER_HOME -ErrorAction SilentlyContinue
    } else {
        $env:GRADLE_USER_HOME = $oldGradleUserHome
    }
    if (Test-Path -LiteralPath $initialLocation -PathType Container) {
        Set-Location $initialLocation
    }
}
