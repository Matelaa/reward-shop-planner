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

# The image file doesn't always carry the item's name (stacks: "Pieces of eight 3.png", variants:
# "Soul cape (blue).png"); the wiki's item data knows it. The biggest stack is listed last.
function Get-WikiImage([string]$itemName) {
    $q = "bucket('infobox_item').select('item_name','image').where('item_name','" + $itemName.Replace("'", "\'") + "').limit(5).run()"
    try {
        $rows = (Invoke-RestMethod -Headers $headers -Uri ("https://oldschool.runescape.wiki/api.php?action=bucket&format=json&query=" + [uri]::EscapeDataString($q))).bucket
        $images = @($rows | ForEach-Object { $_.image } | Where-Object { $_ })
        if ($images.Count -gt 0) { return ($images[-1] -replace '^File:', '') }
    } catch { }
    return $null
}

function Save-Icon([string]$fileName, [string]$itemName, [string]$file) {
    foreach ($candidate in @($fileName, (Get-WikiImage $itemName))) {
        if (-not $candidate) { continue }
        $url = 'https://oldschool.runescape.wiki/images/' + [uri]::EscapeDataString(($candidate -replace ' ', '_'))
        try { Invoke-WebRequest -Headers $headers -Uri $url -OutFile $file -UseBasicParsing; return $true } catch { }
    }
    return $false
}
foreach ($activity in $activities) {
    for ($i = 0; $i -lt $activity.clogItems.Count; $i++) {
        $name = $activity.clogItems[$i]; $id = $activity.clogItemIds[$i]
        if (-not $id) { continue }
        $file = Join-Path $out "$id.png"
        if (Test-Path $file) { $ok++; continue }
        if (Save-Icon "$name.png" $name $file) { $ok++ } else { $failed += $name }
    }
}
# Currency icons: the currency's own item, its stand-in item, or a spell sprite (sprite-<id>.png)
$sources = Get-Content (Join-Path $PSScriptRoot 'sources.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$currencies = Get-Content (Join-Path $root 'src/main/resources/com/rewardshopplanner/data/currencies.json') -Raw | ConvertFrom-Json
foreach ($c in $currencies) {
    $src = $sources.currencies | Where-Object { $_.id -eq $c.id } | Select-Object -First 1
    $itemName = $null; $wikiFile = $null; $file = $null
    if ($c.itemId -and $src.wikiItem) { $file = Join-Path $out "$($c.itemId).png"; $itemName = $src.wikiItem }
    elseif ($c.iconItemId -and $src.icon.item) { $file = Join-Path $out "$($c.iconItemId).png"; $itemName = $src.icon.item }
    elseif ($c.iconSpriteId -and $src.icon.wikiImage) { $file = Join-Path $out "sprite-$($c.iconSpriteId).png"; $wikiFile = $src.icon.wikiImage }
    if (-not $file) { continue }
    if (Test-Path $file) { $ok++; continue }
    if (-not $wikiFile) { $wikiFile = "$itemName.png" }
    if (Save-Icon $wikiFile $itemName $file) { $ok++ } else { $failed += $c.id }
}
Write-Host "Icons: $ok ready, $($failed.Count) not found$(if ($failed) { ': ' + ($failed -join ', ') })"
