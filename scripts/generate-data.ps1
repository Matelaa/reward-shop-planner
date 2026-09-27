<#
  Reward Shop Planner - data generator.

  Reads scripts/sources.json, pulls the collection log pages and store prices from the
  OSRS Wiki, merges the curated manual rewards and writes:
    src/main/resources/com/rewardshopplanner/data/{currencies,activities,rewards}.json
    docs/data-review.md

  Usage (Windows PowerShell 5.1+):
    powershell -ExecutionPolicy Bypass -File scripts/generate-data.ps1
#>
$ErrorActionPreference = 'Stop'
$root    = Split-Path -Parent $PSScriptRoot
$outDir  = Join-Path $root 'src/main/resources/com/rewardshopplanner/data'
$docsDir = Join-Path $root 'docs'
$api     = 'https://oldschool.runescape.wiki/api.php'
$headers = @{ 'User-Agent' = 'reward-shop-planner-datagen/0.1 (RuneLite plugin data build)' }
New-Item -ItemType Directory -Force $outDir, $docsDir | Out-Null

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
Write-Host 'Lendo o collection log...'
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
    if (-not $clogItems.ContainsKey($a.clogPage)) { Warn "Página do log não encontrada: $($a.clogPage)" }
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
Write-Host 'Lendo as lojas...'
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

function Add-Offer($reward, [string]$activity, [string]$store, $cost) {
    foreach ($o in $reward.offers) {
        if ($o.store -eq $store -and (($o.cost | ConvertTo-Json -Compress) -eq ($cost | ConvertTo-Json -Compress))) {
            if ($o.activities -notcontains $activity) { $o.activities += $activity }
            return
        }
    }
    [void]$reward.offers.Add([ordered]@{ activities = @($activity); store = $store; cost = $cost })
}

foreach ($a in $sources.activities) {
    $pageItems = $clogItems[$a.clogPage]
    if (-not $pageItems) { continue }
    # Highest price per item across the activity's stores: TzHaar lists the Karamja-gloves
    # discount as a second row, and its two equipment stores sell the same items.
    $best = @{}; $bestStores = @{}; $bestCurrency = @{}
    foreach ($st in $a.stores) {
        $rows = Invoke-Bucket ("bucket('storeline').select('sold_item','store_sell_price','store_currency').where('page_name','" + (Escape-Lua $st.name) + "').limit(500).run()")
        if (-not $rows) { Warn "Loja sem dados na wiki: $($st.name)"; continue }
        foreach ($r in $rows) {
            $n = Normalize-Name $r.sold_item
            $p = To-Int $r.store_sell_price
            if (-not $pageItems.Contains($n)) { continue }
            if ($null -eq $p -or $p -le 0) { continue }
            if (-not $best.ContainsKey($n) -or $best[$n] -lt $p) { $best[$n] = $p; $bestCurrency[$n] = $st.currency }
            if (-not $bestStores.ContainsKey($n)) { $bestStores[$n] = New-Object System.Collections.ArrayList }
            if (-not $bestStores[$n].Contains($st.name)) { [void]$bestStores[$n].Add($st.name) }
        }
    }
    foreach ($n in $best.Keys) {
        $rw = Get-Reward $n
        $cost = [ordered]@{}; $cost[$bestCurrency[$n]] = $best[$n]
        Add-Offer $rw $a.id ($bestStores[$n] -join ' / ') $cost
    }
}

# ---------------------------------------------------------------------------
# 3. Manual rewards (override / enrich)
# ---------------------------------------------------------------------------
Write-Host 'Aplicando dados manuais...'
$all9 = @($sources.all9Logs)
foreach ($m in $sources.manualRewards) {
    $activity = $sources.activities | Where-Object { $_.id -eq $m.activity }
    if (-not $activity) { Warn "Atividade desconhecida em manualRewards: $($m.activity)"; continue }
    $items = if ($m.items) { @($m.items) } else { @($m.item) }
    foreach ($item in $items) {
        if ($clogItems[$activity.clogPage] -and -not $clogItems[$activity.clogPage].Contains($item)) {
            Warn "Item manual '$item' não está na página '$($activity.clogPage)' do log."
        }
        $rw = Get-Reward $item
        if ($m.type) { $rw.type = $m.type }
        if ($m.cost) {
            $cost = Map-ToOrdered $m.cost
            $storeLabel = if ($activity.stores.Count -gt 0) { $activity.stores[0].name } else { $activity.name }
            # a manual cost replaces store offers from the same activity
            $keep = @($rw.offers | Where-Object { $_.activities -notcontains $activity.id })
            $rw.offers = New-Object System.Collections.ArrayList
            foreach ($k in $keep) { [void]$rw.offers.Add($k) }
            Add-Offer $rw $activity.id $storeLabel $cost
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
Write-Host 'Buscando IDs dos itens...'
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
        $rows = Invoke-Bucket ("bucket('infobox_item').select('page_name','item_id','item_name').where(bucket.Or($ors)).limit(1000).run()")
        $byPage = @{}
        foreach ($r in $rows) {
            $id = @($r.item_id) | Where-Object { $_ } | Select-Object -First 1
            if (-not $id) { continue }
            $in = [System.Net.WebUtility]::HtmlDecode($r.item_name)
            $pn = [System.Net.WebUtility]::HtmlDecode($r.page_name)
            if ($chunk -contains $in -and -not $ids.ContainsKey($in)) { $ids[$in] = [int]$id }
            if (-not $byPage.ContainsKey($pn)) { $byPage[$pn] = [int]$id }
        }
        foreach ($n in $chunk) { if (-not $ids.ContainsKey($n) -and $byPage.ContainsKey($n)) { $ids[$n] = $byPage[$n] } }
    }
    return $ids
}

# Every slot on the covered pages gets an id, so the plugin can match the in-game log to the data.
$allClogNames = @($clogItems.Values | ForEach-Object { $_ })
$ids = Resolve-ItemIds (@($rewards.Keys) + $allClogNames + @($sources.currencies | Where-Object { $_.wikiItem } | ForEach-Object { $_.wikiItem }))
foreach ($rw in $rewards.Values) {
    if ($ids.ContainsKey($rw.name)) { $rw.itemId = $ids[$rw.name] }
    elseif ($rw.name -ne 'Bones to peaches' -and $rw.name -ne 'Animation overrides') { Warn "Sem item ID: $($rw.name)" }
}

# ---------------------------------------------------------------------------
# 5. Build output documents
# ---------------------------------------------------------------------------
$currencyOut = New-Object System.Collections.ArrayList
foreach ($c in $sources.currencies) {
    $o = Map-ToOrdered $c
    if ($c.wikiItem) {
        if ($ids.ContainsKey($c.wikiItem)) { $o.itemId = $ids[$c.wikiItem] } else { Warn "Sem item ID para a moeda: $($c.wikiItem)" }
    }
    [void]$currencyOut.Add($o)
}

$activityOut = New-Object System.Collections.ArrayList
foreach ($a in $sources.activities) {
    $page  = $a.clogPage
    $items = [object[]]@()
    if ($clogItems[$page]) { $items = [object[]]$clogItems[$page].ToArray() }
    # parallel to clogItems; 0 when the wiki has no item id (e.g. spell unlocks)
    $itemIds = [object[]]@($items | ForEach-Object { if ($ids.ContainsKey($_)) { $ids[$_] } else { 0 } })
    foreach ($n in $items) {
        if (-not $ids.ContainsKey($n) -and $n -notin @('Bones to peaches', 'Animation overrides')) { Warn "Sem item ID no log: $n ($page)" }
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
        notes      = $a.notes
    })
}

# Only keep rewards that belong to a covered page and have something to plan
$rewardOut = New-Object System.Collections.ArrayList
foreach ($rw in $rewards.Values) {
    if ($rw.clogPages.Count -eq 0) { continue }
    if ($rw.type -notin @('RANDOM', 'MILESTONE') -and $rw.offers.Count -eq 0) {
        Warn "Item sem custo (ficou fora): $($rw.name)"; continue
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

# ---------------------------------------------------------------------------
# 6. Human review report
# ---------------------------------------------------------------------------
$curName = @{}; foreach ($c in $sources.currencies) { $curName[$c.id] = $c.name }
function Format-Cost($cost) {
    (@($cost.Keys) | ForEach-Object { '{0:N0} {1}' -f $cost[$_], $curName[$_] }) -join ' + '
}

$md = New-Object System.Text.StringBuilder
[void]$md.AppendLine('# Revisão dos dados gerados')
[void]$md.AppendLine('')
[void]$md.AppendLine('Gerado a partir da OSRS Wiki + scripts/sources.json (sem data, para o diff semanal mostrar só mudanças reais).')
[void]$md.AppendLine('')
[void]$md.AppendLine('| Conteúdo | Aba | Slots | Compráveis | Aleatórios | Marcos | Não compráveis | Custo para comprar todos os slots |')
[void]$md.AppendLine('|---|---|---|---|---|---|---|---|')
foreach ($a in $activityOut) {
    [void]$md.AppendLine("| $($a.name) | $($a.clogTab) | $($a.counts.total) | $($a.counts.purchasable) | $($a.counts.random) | $($a.counts.milestone) | $($a.counts.notPurchasable) | $(Format-Cost $a.totalCost) |")
}
[void]$md.AppendLine('')
foreach ($a in $activityOut) {
    [void]$md.AppendLine("## $($a.name)")
    [void]$md.AppendLine('')
    [void]$md.AppendLine('| Item | Tipo | Custo | Consome | Observações |')
    [void]$md.AppendLine('|---|---|---|---|---|')
    $none = New-Object System.Collections.ArrayList
    $seenSets = @{}
    foreach ($n in $a.clogItems) {
        if (-not $rewards.Contains($n)) { [void]$none.Add($n); continue }
        $rw = $rewards[$n]
        if ($rw.type -notin @('RANDOM', 'MILESTONE') -and $rw.offers.Count -eq 0) { [void]$none.Add($n); continue }
        $costTxt = (@($rw.offers) | ForEach-Object {
            $label = Format-Cost $_.cost
            if ($rw.offers.Count -gt 1) { "$label ($($_.store))" } else { $label }
        }) -join ' **ou** '
        if ($rw.set) {
            if ($seenSets.ContainsKey($rw.set)) { $costTxt = "(incluso no set)" } else { $costTxt = "$costTxt por set"; $seenSets[$rw.set] = $true }
        }
        if ($rw.milestone) { $costTxt = "$($rw.milestone.amount) $($rw.milestone.counter)" }
        $obs = @()
        if ($rw.clogPages.Count -gt 1) { $obs += "Também em: " + ((@($rw.clogPages) | Where-Object { $_ -ne $a.clogPage }) -join ', ') }
        if ($rw.materials.Count -gt 0) { $obs += "Materiais: " + ((@($rw.materials.Keys) | ForEach-Object { "$($rw.materials[$_]) $_" }) -join ', ') }
        if ($rw.requirements.Count -gt 0) { $obs += "Requisitos: " + ($rw.requirements -join ', ') }
        if ($rw.refund) {
            if ($rw.refund.rate) { $obs += "Revende por $([int]($rw.refund.rate * 100))%" + $(if ($rw.refund.notForUim) { ' (exceto UIM)' } else { '' }) }
            if ($rw.refund.fixed) { $obs += "Ironman revende por " + (Format-Cost $rw.refund.fixed) }
        }
        if ($rw.notes) { $obs += $rw.notes }
        [void]$md.AppendLine("| $n | $($rw.type) | $costTxt | $($rw.consumes -join ', ') | $($obs -join '; ') |")
    }
    if ($none.Count -gt 0) {
        [void]$md.AppendLine('')
        [void]$md.AppendLine("Não compráveis (só drop/jogando): " + ($none -join ', '))
    }
    [void]$md.AppendLine('')
}
[void]$md.AppendLine('## Moedas')
[void]$md.AppendLine('')
[void]$md.AppendLine('| Moeda | Onde o plugin lê | ID | Observações |')
[void]$md.AppendLine('|---|---|---|---|')
foreach ($c in $currencyOut) {
    $where = switch ($c.source) {
        'ITEM'             { 'Item (banco + inventário)' }
        'VARP'             { "VarPlayer $($c.gameval)" }
        'VARBIT'           { "Varbit $($c.gameval)" }
        'VARBIT_COMPOSITE' { "Varbits $($c.gameval)" }
        default            { '**A descobrir**' }
    }
    $idTxt = if ($c.itemId) { "item $($c.itemId)" } elseif ($c.varId) { "$($c.varId)" } elseif ($c.varIds) { ($c.varIds -join ', ') } else { '' }
    [void]$md.AppendLine("| $($c.name) | $where | $idTxt | $($c.notes) |")
}
[void]$md.AppendLine('')
[void]$md.AppendLine('## Avisos do gerador')
[void]$md.AppendLine('')
if ($warnings.Count -eq 0) { [void]$md.AppendLine('Nenhum.') } else { foreach ($w in $warnings) { [void]$md.AppendLine("- $w") } }
[System.IO.File]::WriteAllText((Join-Path $docsDir 'data-review.md'), $md.ToString(), (New-Object System.Text.UTF8Encoding($false)))

Write-Host ("Pronto: {0} conteúdos, {1} itens, {2} moedas, {3} avisos." -f $activityOut.Count, $rewardOut.Count, $currencyOut.Count, $warnings.Count)
