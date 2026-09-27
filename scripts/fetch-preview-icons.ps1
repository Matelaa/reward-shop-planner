<#
  Downloads item icons from the OSRS Wiki into .cache/preview-icons/<itemId>.png, for the
  screenshot renderer (src/test/.../ui/MarketingRenderer.java). The plugin itself gets icons
  from RuneLite's ItemManager and never uses these files.

  Usage:
    powershell -ExecutionPolicy Bypass -File scripts/fetch-preview-icons.ps1
#>
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root '.cache/preview-icons'
New-Item -ItemType Directory -Force $out | Out-Null
$headers = @{ 'User-Agent' = 'reward-shop-planner-preview/0.1' }
$activities = Get-Content (Join-Path $root 'src/main/resources/com/rewardshopplanner/data/activities.json') -Raw | ConvertFrom-Json

$ok = 0; $failed = @()
foreach ($activity in $activities) {
    for ($i = 0; $i -lt $activity.clogItems.Count; $i++) {
        $name = $activity.clogItems[$i]; $id = $activity.clogItemIds[$i]
        if (-not $id) { continue }
        $file = Join-Path $out "$id.png"
        if (Test-Path $file) { $ok++; continue }
        $url = 'https://oldschool.runescape.wiki/images/' + [uri]::EscapeDataString(($name -replace ' ', '_') + '.png')
        try { Invoke-WebRequest -Headers $headers -Uri $url -OutFile $file -UseBasicParsing; $ok++ }
        catch { $failed += $name }
    }
}
Write-Host "Icons: $ok ready, $($failed.Count) not found$(if ($failed) { ': ' + ($failed -join ', ') })"
