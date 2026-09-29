<#
  Reward Shop Planner - data generator.

  Reads scripts/sources.json, pulls the collection log pages and store prices from the
  OSRS Wiki, merges the curated manual rewards and writes:
    src/main/resources/com/rewardshopplanner/data/{currencies,activities,rewards}.json

  Usage (Windows PowerShell 5.1+):
    powershell -ExecutionPolicy Bypass -File scripts/generate-data.ps1
#>
$ErrorActionPreference = 'Stop'
$root    = Split-Path -Parent $PSScriptRoot
$outDir  = Join-Path $root 'src/main/resources/com/rewardshopplanner/data'
$api     = 'https://oldschool.runescape.wiki/api.php'
$headers = @{ 'User-Agent' = 'reward-shop-planner-datagen/0.1 (RuneLite plugin data build)' }
New-Item -ItemType Directory -Force $outDir | Out-Null

$sources  = Get-Content (Join-Path $PSScriptRoot 'sources.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$warnings = New-Object System.Collections.ArrayList

function Warn($msg) { [void]$warnings.Add($msg); Write-Warning $msg }

function Invoke-Wiki([string]$query) {
    $lastError = $null
    for ($i = 0; $i -lt 3; $i++) {
        try { return Invoke-RestMethod -Headers $headers -Uri "${api}?$query" }
        catch { $lastError = $_; Start-Sleep -Seconds 2 }
    }
    throw "Wiki request failed: $query ($lastError)"
}

function Escape-Lua([string]$s) { $s.Replace('\', '\\').Replace("'", "\'") }

function Invoke-Bucket([string]$bucketQuery) {
    (Invoke-Wiki ("action=bucket&format=json&query=" + [uri]::EscapeDataString($bucketQuery))).bucket
}

function Normalize-Name([string]$s) {
    $s = [System.Net.WebUtility]::HtmlDecode($s)
    $hash = $s.IndexOf('#')
    if ($hash -ge 0) { $s = $s.Substring(0, $hash) }
    return $s.Trim()
}

function To-Int($v) { if ($null -eq $v -or "$v" -eq '') { return $null }; return [int]("$v" -replace '[^\d]', '') }

function Map-ToOrdered($obj) {
    $m = [ordered]@{}
    if ($null -ne $obj) { foreach ($p in $obj.PSObject.Properties) { $m[$p.Name] = $p.Value } }
    return $m
}

# ---------------------------------------------------------------------------
# 1. Collection log pages -> item lists
# ---------------------------------------------------------------------------
Write-Host 'Reading the collection log...'
$sections = (Invoke-Wiki 'action=parse&format=json&prop=sections&page=Collection_log').parse.sections
$clogItems = @{}   # page -> ordered list of item names
$clogTab   = @{}   # page -> tab name
$currentTab = $null
foreach ($s in $sections) {
    if ($s.toclevel -eq 1) { $currentTab = $s.line; continue }
    $page = [System.Net.WebUtility]::HtmlDecode($s.line)
    $wanted = $sources.activities | Where-Object { $_.clogPage -eq $page }
    if (-not $wanted) { continue }
    $html = (Invoke-Wiki ("action=parse&format=json&prop=text&page=Collection_log&section=" + $s.index)).parse.text.'*'
    # Items live in the section's tables/lists; intro text links (shops, NPCs, "Edit section") are skipped.
    $names = New-Object System.Collections.ArrayList
    foreach ($block in [regex]::Matches($html, '<table.*?</table>|<ul.*?</ul>', 'Singleline')) {
        foreach ($m in [regex]::Matches($block.Value, '<a [^>]*title="([^"]+)"')) {
            $n = [System.Net.WebUtility]::HtmlDecode($m.Groups[1].Value)
            if ($n -match '^(File|Category|Special|Template):' -or $n -like 'Edit section*') { continue }
            if ($n -eq $page) { continue }
            if (-not $names.Contains($n)) { [void]$names.Add($n) }
        }
    }
    $clogItems[$page] = $names
    $clogTab[$page]   = $currentTab
}
foreach ($a in $sources.activities) {
    if (-not $clogItems.ContainsKey($a.clogPage)) { Warn "Collection log page not found: $($a.clogPage)" }
}

# item name -> list of clog pages it appears on
$itemPages = @{}
foreach ($page in $clogItems.Keys) {
    foreach ($n in $clogItems[$page]) {
        if (-not $itemPages.ContainsKey($n)) { $itemPages[$n] = New-Object System.Collections.ArrayList }
        [void]$itemPages[$n].Add($page)
    }
}

# ---------------------------------------------------------------------------
# 2. Store prices -> offers
# ---------------------------------------------------------------------------
Write-Host 'Reading the shops...'
$rewards = [ordered]@{}   # item name -> reward (ordered hashtable)

function Get-Reward([string]$item) {
    if (-not $rewards.Contains($item)) {
        $rewards[$item] = [ordered]@{
            name         = $item
            itemId       = $null
            clogPages    = @()
            type         = 'SHOP'
            offers       = New-Object System.Collections.ArrayList
            consumes     = @()
            materials    = [ordered]@{}
            requirements = @()
            refund       = $null
            set          = $null
            milestone    = $null
            notes        = $null
        }
    }
    return $rewards[$item]
}

function Add-Offer($reward, [string]$activity, [string]$store, $cost, $buyBack = $null, $extra = $null) {
    foreach ($o in $reward.offers) {
        if ($o.store -eq $store -and (($o.cost | ConvertTo-Json -Compress) -eq ($cost | ConvertTo-Json -Compress))) {
            if ($o.activities -notcontains $activity) { $o.activities += $activity }
            return
        }
    }
    $offer = [ordered]@{ activities = @($activity); store = $store; cost = $cost }
    if ($buyBack) { $offer.buyBack = $buyBack }
    if ($extra) { foreach ($k in $extra.Keys) { $offer[$k] = $extra[$k] } }
    [void]$reward.offers.Add($offer)
}

function Single-Cost([string]$currency, $amount) {
    if (-not $amount) { return $null }
    $m = [ordered]@{}; $m[$currency] = $amount; return $m
}

# wiki item name -> log slot name, for slots the wiki sells under another name
$aliasByWikiItem = @{}
foreach ($al in @($sources.clogAliases)) { if ($al) { $aliasByWikiItem[$al.wikiItem] = $al.clogName } }

foreach ($a in $sources.activities) {
    $pageItems = $clogItems[$a.clogPage]
    if (-not $pageItems) { continue }
    # Highest regular price per item across the activity's stores (TzHaar's two equipment
    # stores sell the same items). Rows noted "(Karamja gloves)" are TzHaar's prices while
    # wearing the gloves: kept separately. The store's buy price (what it pays when the item is
    # sold back) comes from the same row.
    $best = @{}; $bestStores = @{}; $bestCurrency = @{}; $bestBuy = @{}; $gloves = @{}; $glovesBuy = @{}
    foreach ($st in $a.stores) {
        $rows = Invoke-Bucket ("bucket('storeline').select('sold_item','store_sell_price','store_buy_price','store_currency','store_notes').where('page_name','" + (Escape-Lua $st.name) + "').limit(500).run()")
        if (-not $rows) { Warn "No wiki data for shop: $($st.name)"; continue }
        foreach ($r in $rows) {
            $n = Normalize-Name $r.sold_item
            if ($aliasByWikiItem.ContainsKey($n) -and $pageItems.Contains($aliasByWikiItem[$n])) { $n = $aliasByWikiItem[$n] }
            $p = To-Int $r.store_sell_price
            if (-not $pageItems.Contains($n)) { continue }
            if ($null -eq $p -or $p -le 0) { continue }
            $buy = if ("$($r.store_buy_price)" -match '^[\d,]+$') { To-Int $r.store_buy_price } else { $null }
            $buy = if ($buy -gt 0) { [Math]::Min($buy, $p) } else { $null }
            if ("$($r.store_notes)" -match 'Karamja gloves') {
                $gloves[$n] = $p; $glovesBuy[$n] = $buy
                continue
            }
            if (-not $best.ContainsKey($n) -or $best[$n] -lt $p) {
                $best[$n] = $p; $bestCurrency[$n] = $st.currency; $bestBuy[$n] = $buy
            }
            if (-not $bestStores.ContainsKey($n)) { $bestStores[$n] = New-Object System.Collections.ArrayList }
            if (-not $bestStores[$n].Contains($st.name)) { [void]$bestStores[$n].Add($st.name) }
        }
    }
    foreach ($n in $best.Keys) {
        $rw = Get-Reward $n
        $currency = $bestCurrency[$n]
        $extra = $null
        if ($gloves.ContainsKey($n)) {
            $extra = [ordered]@{ karamjaGlovesCost = (Single-Cost $currency $gloves[$n]) }
            if ($glovesBuy[$n]) { $extra.karamjaGlovesBuyBack = (Single-Cost $currency $glovesBuy[$n]) }
        }
        Add-Offer $rw $a.id ($bestStores[$n] -join ' / ') (Single-Cost $currency $best[$n]) (Single-Cost $currency $bestBuy[$n]) $extra
    }
}

# ---------------------------------------------------------------------------
# 3. Manual rewards (override / enrich)
# ---------------------------------------------------------------------------
Write-Host 'Applying curated data...'
$all9 = @($sources.all9Logs)
foreach ($m in $sources.manualRewards) {
    $activity = $sources.activities | Where-Object { $_.id -eq $m.activity }
    if (-not $activity) { Warn "Unknown activity in manualRewards: $($m.activity)"; continue }
    $items = if ($m.items) { @($m.items) } else { @($m.item) }
    foreach ($item in $items) {
        if ($clogItems[$activity.clogPage] -and -not $clogItems[$activity.clogPage].Contains($item)) {
            Warn "Curated item '$item' is not on the '$($activity.clogPage)' log page."
        }
        $rw = Get-Reward $item
        if ($m.type) { $rw.type = $m.type }
        if ($m.cost) {
            $cost = Map-ToOrdered $m.cost
            $storeLabel = if ($activity.stores.Count -gt 0) { $activity.stores[0].name } else { $activity.name }
            # a manual cost replaces store offers from the same activity (keeping the store's buy-back)
            $replaced = @($rw.offers | Where-Object { $_.activities -contains $activity.id })
            $buyBack = if ($replaced.Count -gt 0 -and $replaced[0].buyBack) { $replaced[0].buyBack } else { $null }
            $keep = @($rw.offers | Where-Object { $_.activities -notcontains $activity.id })
            $rw.offers = New-Object System.Collections.ArrayList
            foreach ($k in $keep) { [void]$rw.offers.Add($k) }
            Add-Offer $rw $activity.id $storeLabel $cost $buyBack
        }
        if ($m.consumes)     { $rw.consumes = @($m.consumes) }
        if ($m.requirements) { $rw.requirements = @($m.requirements) }
        if ($m.refund)       { $rw.refund = Map-ToOrdered $m.refund; if ($m.refund.fixed) { $rw.refund.fixed = Map-ToOrdered $m.refund.fixed } }
        if ($m.milestone)    { $rw.milestone = Map-ToOrdered $m.milestone }
        if ($m.notes)        { $rw.notes = $m.notes }
        if ($m.setName)      { $rw.set = $m.setName }
        if ($m.materials) {
            $mat = [ordered]@{}
            foreach ($p in $m.materials.PSObject.Properties) {
                if ($p.Name -eq '@all9') { foreach ($l in $all9) { $mat[$l] = [int]$p.Value } }
                else { $mat[$p.Name] = [int]$p.Value }
            }
            $rw.materials = $mat
        }
    }
}

# ---------------------------------------------------------------------------
# 4. Clog pages per reward + item ids
# ---------------------------------------------------------------------------
Write-Host 'Looking up item ids...'
# Assign arrays directly: routing them through an if-expression would unwrap
# single-element arrays into scalars (Windows PowerShell 5.1 pipeline behaviour).
foreach ($rw in $rewards.Values) {
    $rw.clogPages = [object[]]@()
    if ($itemPages.ContainsKey($rw.name)) { $rw.clogPages = [object[]]$itemPages[$rw.name].ToArray() }
}

function Resolve-ItemIds([string[]]$names) {
    $ids = @{}
    $names = @($names | Sort-Object -Unique)
    for ($i = 0; $i -lt $names.Count; $i += 30) {
        $chunk = $names[$i..([Math]::Min($i + 29, $names.Count - 1))]
        # match on the exact item name first (versioned pages like "Celestial ring"),
        # then fall back to the page name
        $ors = ($chunk | ForEach-Object { $e = Escape-Lua $_; "{'item_name','$e'},{'page_name','$e'}" }) -join ','
        $rows = Invoke-Bucket ("bucket('infobox_item').select('page_name','item_id','item_name','version_anchor').where(bucket.Or($ors)).limit(1000).run()")
        $byPage = @{}; $byPageRank = @{}
        foreach ($r in $rows) {
            $id = @($r.item_id) | Where-Object { $_ } | Select-Object -First 1
            if (-not $id) { continue }
            $in = [System.Net.WebUtility]::HtmlDecode($r.item_name)
            $pn = [System.Net.WebUtility]::HtmlDecode($r.page_name)
            if ($chunk -contains $in -and -not $ids.ContainsKey($in)) { $ids[$in] = [int]$id }
            # A page can list several versions (Castle Wars gold armour: Normal, Broken, Locked). The
            # collection log shows the normal one, so broken and locked versions only fill in.
            $anchor = "$($r.version_anchor)"
            $rank = if ($anchor -match '(?i)broken|locked' -or $in -match '\((broken|l)\)$') { 2 } elseif ($anchor -eq '' -or $anchor -match '(?i)^normal$') { 0 } else { 1 }
            if (-not $byPage.ContainsKey($pn) -or $rank -lt $byPageRank[$pn]) { $byPage[$pn] = [int]$id; $byPageRank[$pn] = $rank }
        }
        foreach ($n in $chunk) { if (-not $ids.ContainsKey($n) -and $byPage.ContainsKey($n)) { $ids[$n] = $byPage[$n] } }
    }
    return $ids
}

# Every slot on the covered pages gets an id, so the plugin can match the in-game log to the data.
$allClogNames = @($clogItems.Values | ForEach-Object { $_ })
# Materials (logs, bars...) are counted in the bank and inventory, so they need ids too.
$materialNames = @($rewards.Values | Where-Object { $_.materials } | ForEach-Object { $_.materials.Keys } | Sort-Object -Unique)
$iconItems = @($sources.currencies | Where-Object { $_.icon -and $_.icon.item } | ForEach-Object { $_.icon.item })
$ids = Resolve-ItemIds (@($rewards.Keys) + $allClogNames + $materialNames + $iconItems + @($sources.currencies | Where-Object { $_.wikiItem } | ForEach-Object { $_.wikiItem }))
foreach ($al in @($sources.clogAliases)) { if ($al) { $ids[$al.clogName] = [int]$al.itemId } }
foreach ($rw in $rewards.Values) {
    if ($ids.ContainsKey($rw.name)) { $rw.itemId = $ids[$rw.name] }
    elseif ($rw.name -ne 'Bones to peaches' -and $rw.name -ne 'Animation overrides') { Warn "No item id: $($rw.name)" }
}

# ---------------------------------------------------------------------------
# 5. Build output documents
# ---------------------------------------------------------------------------
$currencyOut = New-Object System.Collections.ArrayList
foreach ($c in $sources.currencies) {
    $o = Map-ToOrdered $c
    if ($c.wikiItem) {
        if ($ids.ContainsKey($c.wikiItem)) { $o.itemId = $ids[$c.wikiItem] } else { Warn "No item id for currency: $($c.wikiItem)" }
    }
    # currencies that aren't items show an item's icon, or a spell's sprite (Mage Training Arena)
    $o.Remove('icon')
    if ($c.icon -and $c.icon.item) {
        if ($ids.ContainsKey($c.icon.item)) { $o.iconItemId = $ids[$c.icon.item] } else { Warn "No item id for currency icon: $($c.icon.item)" }
    }
    if ($c.icon -and $c.icon.sprite) { $o.iconSpriteId = [int]$c.icon.sprite }
    [void]$currencyOut.Add($o)
}

$materialOut = New-Object System.Collections.ArrayList
foreach ($n in $materialNames) {
    if ($ids.ContainsKey($n)) { [void]$materialOut.Add([ordered]@{ name = $n; itemId = $ids[$n] }) }
    else { Warn "No item id for material: $n" }
}

$activityOut = New-Object System.Collections.ArrayList
foreach ($a in $sources.activities) {
    $page  = $a.clogPage
    $items = [object[]]@()
    if ($clogItems[$page]) { $items = [object[]]$clogItems[$page].ToArray() }
    # parallel to clogItems; 0 when the wiki has no item id (e.g. spell unlocks)
    $itemIds = [object[]]@($items | ForEach-Object { if ($ids.ContainsKey($_)) { $ids[$_] } else { 0 } })
    foreach ($n in $items) {
        if (-not $ids.ContainsKey($n) -and $n -notin @('Bones to peaches', 'Animation overrides')) { Warn "No item id for log slot: $n ($page)" }
    }
    $counts = [ordered]@{ total = $items.Count; purchasable = 0; random = 0; milestone = 0; notPurchasable = 0 }
    $currencies = New-Object System.Collections.ArrayList
    $totalCost  = [ordered]@{}   # cost of every purchasable slot on this page, sets counted once
    $seenSets   = @{}
    foreach ($n in $items) {
        $rw = if ($rewards.Contains($n)) { $rewards[$n] } else { $null }
        if (-not $rw) { $counts.notPurchasable++; continue }
        switch ($rw.type) {
            'RANDOM'    { $counts.random++ }
            'MILESTONE' { $counts.milestone++ }
            default {
                if ($rw.offers.Count -gt 0) { $counts.purchasable++ } else { $counts.notPurchasable++; break }
                foreach ($o in $rw.offers) { foreach ($k in $o.cost.Keys) { if (-not $currencies.Contains($k)) { [void]$currencies.Add($k) } } }
                if ($rw.set) { if ($seenSets.ContainsKey($rw.set)) { break }; $seenSets[$rw.set] = $true }
                $offer = @($rw.offers | Where-Object { $_.activities -contains $a.id })[0]
                if (-not $offer) { $offer = $rw.offers[0] }
                foreach ($k in $offer.cost.Keys) { $totalCost[$k] = [int]$totalCost[$k] + [int]$offer.cost[$k] }
            }
        }
    }
    [void]$activityOut.Add([ordered]@{
        id         = $a.id
        name       = $a.name
        clogTab    = $clogTab[$page]
        clogPage   = $page
        currencies = @($currencies)
        clogItems  = $items
        clogItemIds = $itemIds
        counts     = $counts
        totalCost  = $totalCost
        # map regions where the on-screen progress shows; none means it shows after earning the currency
        regions    = [object[]]@($a.regions | Where-Object { $null -ne $_ })
        minPlane   = if ($a.minPlane) { [int]$a.minPlane } else { 0 }
        # finer places: polygons (tile corners walked in game) or regions, optionally for some currencies only
        areas      = [object[]]@($a.areas | Where-Object { $null -ne $_ } | ForEach-Object {
            $area = [ordered]@{}
            if ($null -ne $_.plane) { $area.plane = [int]$_.plane }
            if ($_.regions) { $area.regions = [object[]]@($_.regions | ForEach-Object { [int]$_ }) }
            # each point stays a two-element array, even through PowerShell's array flattening
            if ($_.points) { $area.points = [object[]]@($_.points | ForEach-Object { ,[object[]]@([int]$_[0], [int]$_[1]) }) }
            if ($_.currencies) { $area.currencies = [object[]]@($_.currencies) }
            $area
        })
        notes      = $a.notes
    })
}

# Only keep rewards that belong to a covered page and have something to plan
$rewardOut = New-Object System.Collections.ArrayList
foreach ($rw in $rewards.Values) {
    if ($rw.clogPages.Count -eq 0) { continue }
    if ($rw.type -notin @('RANDOM', 'MILESTONE') -and $rw.offers.Count -eq 0) {
        Warn "Item has no price and was left out: $($rw.name)"; continue
    }
    [void]$rewardOut.Add($rw)
}

function Write-Json($obj, [string]$file) {
    $json = ConvertTo-Json -InputObject $obj -Depth 12
    [System.IO.File]::WriteAllText((Join-Path $outDir $file), $json, (New-Object System.Text.UTF8Encoding($false)))
}
Write-Json @($currencyOut) 'currencies.json'
Write-Json @($activityOut) 'activities.json'
Write-Json @($rewardOut)   'rewards.json'
Write-Json @($materialOut) 'materials.json'

Write-Host ("Done: {0} activities, {1} rewards, {2} currencies, {3} materials, {4} warnings." -f $activityOut.Count, $rewardOut.Count, $currencyOut.Count, $materialOut.Count, $warnings.Count)
