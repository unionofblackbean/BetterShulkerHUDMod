[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'Manifest.psm1') -Force

$manifest = Read-VersionManifest
foreach ($entry in $manifest.versions) {
    $label = [string]$entry.id
    $projectPath = Resolve-RepositoryPath ([string]$entry.path)
    $clientRoot = Join-Path $projectPath 'src/client/java/betterbundle'
    $rendererPath = Join-Path $clientRoot 'gui/BundlePanelRenderer.java'
    $interactionPath = Join-Path $clientRoot 'gui/BundlePanelInteraction.java'
    $containerMixinPath = Join-Path $clientRoot 'mixin/AbstractContainerScreenMixin.java'

    foreach ($requiredPath in @(
            $rendererPath, $interactionPath, $containerMixinPath)) {
        if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
            throw "$label scrollbar contract file is missing: $requiredPath"
        }
    }

    $renderer = Get-Content -LiteralPath $rendererPath -Raw -Encoding utf8
    foreach ($method in @(
            'isMouseOverScrollBar',
            'handleScrollBarClick',
            'handleScrollBarDrag',
            'handleScrollBarRelease')) {
        if ($renderer -notmatch ('\b' + [regex]::Escape($method) + '\s*\(')) {
            throw "$label renderer does not implement $method."
        }
    }
    if (($renderer -notmatch 'scrollBarDragging') -or
            ($renderer -notmatch 'scrollThumbY\s*\(')) {
        throw "$label renderer does not preserve scrollbar drag/thumb state."
    }

    $interaction = Get-Content -LiteralPath $interactionPath -Raw -Encoding utf8
    $scrollStart = $interaction.IndexOf('public static boolean handleScroll(')
    if ($scrollStart -lt 0) {
        throw "$label interaction does not implement handleScroll."
    }
    $scrollEnd = $interaction.IndexOf(
        'public static boolean ', $scrollStart + 1)
    if ($scrollEnd -lt 0) {
        $scrollEnd = $interaction.Length
    }
    $scrollMethod = $interaction.Substring(
        $scrollStart, $scrollEnd - $scrollStart)
    if ($scrollMethod -notmatch 'isMouseOverScrollBar\s*\(') {
        throw "$label HUD wheel handling is not restricted to the scrollbar."
    }
    if ($scrollMethod -match 'isInside(Grid|Panel)\s*\(') {
        throw "$label HUD wheel handling still consumes the item grid."
    }
    if ($scrollMethod -notmatch 'scrollDelta\s*==\s*0\.0') {
        throw "$label HUD wheel handling consumes zero vertical deltas."
    }

    $containerMixin = Get-Content -LiteralPath $containerMixinPath -Raw -Encoding utf8
    foreach ($method in @(
            'handleScrollBarClick',
            'handleScrollBarDrag',
            'handleScrollBarRelease')) {
        if ($containerMixin -notmatch (
                'BundlePanelRenderer\.' + [regex]::Escape($method) + '\s*\(')) {
            throw "$label container input path does not call $method."
        }
    }

    # 1.21.8 had a reported tooltip-layer regression: rendering the HUD from
    # AbstractContainerScreen.render() at TAIL places it above vanilla tooltips.
    # Keep the fix explicit so a future sync cannot silently reintroduce it.
    if ($label -eq '1.21.8') {
        $inventoryMixinPath = Join-Path $clientRoot 'mixin/InventoryScreenMixin.java'
        $inventoryMixin = Get-Content -LiteralPath $inventoryMixinPath -Raw -Encoding utf8
        if ($inventoryMixin -notmatch '@Inject\(method = "renderContents", at = @At\("TAIL"\)\)') {
            throw "$label HUD must render from renderContents at TAIL before vanilla tooltip."
        }
        if ($inventoryMixin -match '@Inject\(method = "render", at = @At\("TAIL"\)\)' -and
                $inventoryMixin -match 'BundlePanelRenderer\.renderOverlay\s*\(') {
            throw "$label HUD still renders from render TAIL and can cover tooltips."
        }
        if ($inventoryMixin -match 'instanceof\s+AbstractRecipeBookScreen') {
            throw "$label common HUD renderer must not exclude recipe-book screens."
        }
        $recipeMixinPath = Join-Path $clientRoot 'mixin/AbstractRecipeBookScreenMixin.java'
        $recipeMixin = Get-Content -LiteralPath $recipeMixinPath -Raw -Encoding utf8
        if ($recipeMixin -match '@Inject\(method = "render", at = @At\("TAIL"\)\)' -and
                $recipeMixin -match 'BundlePanelRenderer\.renderOverlay\s*\(') {
            throw "$label recipe-book HUD has a duplicate render TAIL entry."
        }
    }

    $recipeMixinPath = Join-Path $clientRoot 'mixin/AbstractRecipeBookScreenMixin.java'
    if (Test-Path -LiteralPath $recipeMixinPath -PathType Leaf) {
        $recipeMixin = Get-Content -LiteralPath $recipeMixinPath -Raw -Encoding utf8
        if (($recipeMixin -match '@Inject\(method = "mouseDragged"') -and
                ($recipeMixin -notmatch
                'BundlePanelRenderer\.handleScrollBarDrag\s*\(')) {
            throw "$label recipe-book drag path can bypass the scrollbar."
        }
    }
}

Write-Host ("Scrollbar wheel ownership and drag routing are valid for {0} maintained source lines." -f @($manifest.versions).Count)
