param(
  [string]$GatewayBaseUrl = 'http://localhost:8080',
  [string]$Username = 'admin',
  [string]$Password = 'admin123',
  [string]$TerminalA = 'QA-A',
  [string]$TerminalB = 'QA-B'
)

$ErrorActionPreference = 'Stop'

function ConvertTo-JsonBody([object]$Body) {
  if ($null -eq $Body) { return $null }
  return ($Body | ConvertTo-Json -Depth 10 -Compress)
}

function Read-ResponseBody([object]$Response) {
  if ($null -eq $Response) { return '' }

  # PowerShell 7 / GitHub Actions: HttpResponseMessage
  if ($Response.GetType().FullName -eq 'System.Net.Http.HttpResponseMessage') {
    if ($null -eq $Response.Content) { return '' }
    try {
      return $Response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    } catch {
      return ''
    }
  }

  # Windows PowerShell: WebResponse
  if ($Response -is [System.Net.WebResponse]) {
    $stream = $Response.GetResponseStream()
    if ($null -eq $stream) { return '' }
    $reader = New-Object System.IO.StreamReader($stream)
    try {
      return $reader.ReadToEnd()
    } finally {
      $reader.Dispose()
      $stream.Dispose()
    }
  }

  return ''
}

function Invoke-Api {
  param(
    [Parameter(Mandatory = $true)][string]$Method,
    [Parameter(Mandatory = $true)][string]$Path,
    [object]$Body = $null,
    [string]$Token = '',
    [string]$TerminalId = '',
    [hashtable]$ExtraHeaders = @{},
    [int[]]$Expected = @(200)
  )

  $headers = @{}
  if ($Token) { $headers['Authorization'] = "Bearer $Token" }
  if ($TerminalId) { $headers['X-Terminal-Id'] = $TerminalId }
  if ($ExtraHeaders) {
    foreach ($key in $ExtraHeaders.Keys) {
      $headers[$key] = [string]$ExtraHeaders[$key]
    }
  }

  $uri = "$GatewayBaseUrl$Path"
  $jsonBody = ConvertTo-JsonBody $Body

  try {
    $resp = Invoke-WebRequest -UseBasicParsing -Method $Method -Uri $uri -Headers $headers -ContentType 'application/json' -Body $jsonBody
    $status = [int]$resp.StatusCode
    $raw = $resp.Content
  } catch {
    if ($_.Exception.Response -eq $null) { throw }
    $status = [int]$_.Exception.Response.StatusCode
    $raw = ''
    if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
      $raw = $_.ErrorDetails.Message
    }
    if (-not $raw) {
      $raw = Read-ResponseBody $_.Exception.Response
    }
  }

  $parsed = $null
  if ($raw) {
    try { $parsed = $raw | ConvertFrom-Json } catch { $parsed = $null }
  }

  if ($Expected -notcontains $status) {
    $detail = if ($raw) { $raw } else { '<empty body>' }
    throw "Unexpected status $status for $Method $Path. Expected: $($Expected -join ','). Body: $detail"
  }

  [PSCustomObject]@{
    Status = $status
    Raw = $raw
    Json = $parsed
  }
}

function Assert-True {
  param([bool]$Condition, [string]$Message)
  if (-not $Condition) { throw "ASSERT FAILED: $Message" }
}

function New-IdempotencyKey([string]$Prefix) {
  return "$Prefix-$([guid]::NewGuid().ToString('N'))"
}

function Invoke-LockRace {
  param(
    [Parameter(Mandatory = $true)][string]$GatewayBaseUrl,
    [Parameter(Mandatory = $true)][string]$Token,
    [Parameter(Mandatory = $true)][int]$TableNumber,
    [Parameter(Mandatory = $true)][string]$TerminalA,
    [Parameter(Mandatory = $true)][string]$TerminalB
  )

  $jobScript = {
    param($baseUrl, $token, $tableNumber, $terminalId)
    $headers = @{
      Authorization = "Bearer $token"
      'X-Terminal-Id' = $terminalId
    }
    $body = @{ terminalId = $terminalId } | ConvertTo-Json -Compress
    $status = -1
    $raw = ''
    try {
      $resp = Invoke-WebRequest -UseBasicParsing -Method 'POST' -Uri "$baseUrl/api/v1/pos/salon/tables/$tableNumber/lock" -Headers $headers -ContentType 'application/json' -Body $body
      $status = [int]$resp.StatusCode
      $raw = $resp.Content
    } catch {
      if ($_.Exception.Response) {
        $status = [int]$_.Exception.Response.StatusCode
      }
      if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
        $raw = $_.ErrorDetails.Message
      }
    }
    [PSCustomObject]@{
      TerminalId = $terminalId
      Status = $status
      Raw = $raw
    }
  }

  $jobA = Start-Job -ScriptBlock $jobScript -ArgumentList @($GatewayBaseUrl, $Token, $TableNumber, $TerminalA)
  $jobB = Start-Job -ScriptBlock $jobScript -ArgumentList @($GatewayBaseUrl, $Token, $TableNumber, $TerminalB)
  try {
    Wait-Job -Job @($jobA, $jobB) | Out-Null
    $results = @()
    $results += Receive-Job -Job $jobA
    $results += Receive-Job -Job $jobB
    return $results
  } finally {
    Remove-Job -Job @($jobA, $jobB) -Force -ErrorAction SilentlyContinue
  }
}

function Invoke-MoveRace {
  param(
    [Parameter(Mandatory = $true)][string]$GatewayBaseUrl,
    [Parameter(Mandatory = $true)][string]$Token,
    [Parameter(Mandatory = $true)][int64]$TicketAId,
    [Parameter(Mandatory = $true)][int64]$TicketBId,
    [Parameter(Mandatory = $true)][int]$TargetTable,
    [Parameter(Mandatory = $true)][string]$TerminalA,
    [Parameter(Mandatory = $true)][string]$TerminalB
  )

  $jobScript = {
    param($baseUrl, $token, $ticketId, $targetTable, $terminalId)
    $headers = @{
      Authorization = "Bearer $token"
      'X-Terminal-Id' = $terminalId
    }
    $body = @{ tableNumber = $targetTable } | ConvertTo-Json -Compress
    $status = -1
    $raw = ''
    $json = $null
    try {
      $resp = Invoke-WebRequest -UseBasicParsing -Method 'POST' -Uri "$baseUrl/api/v1/pos/tickets/$ticketId/move-table" -Headers $headers -ContentType 'application/json' -Body $body
      $status = [int]$resp.StatusCode
      $raw = $resp.Content
    } catch {
      if ($_.Exception.Response) {
        $status = [int]$_.Exception.Response.StatusCode
      }
      if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
        $raw = $_.ErrorDetails.Message
      }
    }
    if ($raw) {
      try { $json = $raw | ConvertFrom-Json } catch { $json = $null }
    }
    [PSCustomObject]@{
      TicketId = $ticketId
      TerminalId = $terminalId
      Status = $status
      Raw = $raw
      Json = $json
    }
  }

  $jobA = Start-Job -ScriptBlock $jobScript -ArgumentList @($GatewayBaseUrl, $Token, $TicketAId, $TargetTable, $TerminalA)
  $jobB = Start-Job -ScriptBlock $jobScript -ArgumentList @($GatewayBaseUrl, $Token, $TicketBId, $TargetTable, $TerminalB)
  try {
    Wait-Job -Job @($jobA, $jobB) | Out-Null
    $results = @()
    $results += Receive-Job -Job $jobA
    $results += Receive-Job -Job $jobB
    return $results
  } finally {
    Remove-Job -Job @($jobA, $jobB) -Force -ErrorAction SilentlyContinue
  }
}

function Invoke-PaymentRace {
  param(
    [Parameter(Mandatory = $true)][string]$GatewayBaseUrl,
    [Parameter(Mandatory = $true)][string]$Token,
    [Parameter(Mandatory = $true)][int64]$TicketId,
    [Parameter(Mandatory = $true)][int]$AmountCents,
    [Parameter(Mandatory = $true)][string]$TerminalA,
    [Parameter(Mandatory = $true)][string]$TerminalB
  )

  $jobScript = {
    param($baseUrl, $token, $ticketId, $amountCents, $terminalId)
    $headers = @{
      Authorization = "Bearer $token"
      'X-Terminal-Id' = $terminalId
      'Content-Type' = 'application/json'
    }
    $body = @{ method = 'CARD'; amountCents = $amountCents } | ConvertTo-Json -Compress
    $status = -1
    $raw = ''
    $json = $null
    try {
      $resp = Invoke-WebRequest -UseBasicParsing -Method 'POST' -Uri "$baseUrl/api/v1/pos/tickets/$ticketId/payments" -Headers $headers -Body $body
      $status = [int]$resp.StatusCode
      $raw = $resp.Content
    } catch {
      if ($_.Exception.Response) {
        $status = [int]$_.Exception.Response.StatusCode
      }
      if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
        $raw = $_.ErrorDetails.Message
      }
    }
    if ($raw) {
      try { $json = $raw | ConvertFrom-Json } catch { $json = $null }
    }
    [PSCustomObject]@{
      TerminalId = $terminalId
      Status = $status
      Raw = $raw
      Json = $json
    }
  }

  $jobA = Start-Job -ScriptBlock $jobScript -ArgumentList @($GatewayBaseUrl, $Token, $TicketId, $AmountCents, $TerminalA)
  $jobB = Start-Job -ScriptBlock $jobScript -ArgumentList @($GatewayBaseUrl, $Token, $TicketId, $AmountCents, $TerminalB)
  try {
    Wait-Job -Job @($jobA, $jobB) | Out-Null
    $results = @()
    $results += Receive-Job -Job $jobA
    $results += Receive-Job -Job $jobB
    return $results
  } finally {
    Remove-Job -Job @($jobA, $jobB) -Force -ErrorAction SilentlyContinue
  }
}

Write-Host '== PDA E2E smoke =='
Write-Host "Gateway: $GatewayBaseUrl"

# 1) Static PDA assets
$index = Invoke-Api -Method 'GET' -Path '/pda/index.html' -Expected @(200)
Assert-True ($index.Raw -match 'Acceso PDA') 'index.html should contain login view'
Assert-True ($index.Raw -match 'Conflictos') 'index.html should include conflicts button'
Write-Host '[OK] static /pda/index.html'

# 2) Login
$login = Invoke-Api -Method 'POST' -Path '/api/v1/auth/login' -Body @{ username = $Username; password = $Password } -Expected @(200)
$token = $login.Json.accessToken
Assert-True (-not [string]::IsNullOrWhiteSpace($token)) 'login must return accessToken'
Write-Host '[OK] auth login'

# 3) Ensure open cash session
$currentCash = Invoke-Api -Method 'GET' -Path '/api/v1/pos/cash-sessions/current' -Token $token -TerminalId $TerminalA -Expected @(200,404)
if ($currentCash.Status -eq 404) {
  $openCash = Invoke-Api -Method 'POST' -Path '/api/v1/pos/cash-sessions/open' -Token $token -TerminalId $TerminalA -Body @{ openingCashCents = 0; note = 'PDA QA smoke' } -Expected @(200,409)
  if ($openCash.Status -eq 409) {
    Write-Host '[INFO] cash session already open by another terminal'
  } else {
    Write-Host '[OK] opened cash session'
  }
} else {
  Write-Host '[OK] cash session already open'
}

# 4) Catalog sanity
$cats = Invoke-Api -Method 'GET' -Path '/api/v1/pos/categories' -Token $token -TerminalId $TerminalA -Expected @(200)
$categories = @($cats.Json)
if ($categories.Count -eq 0) {
  $seed = Invoke-Api -Method 'POST' -Path '/api/v1/pos/admin/seed-catalog' -Token $token -TerminalId $TerminalA -Expected @(200)
  Write-Host "[OK] seeded catalog categoriesCreated=$($seed.Json.categoriesCreated) productsCreated=$($seed.Json.productsCreated)"
  $cats = Invoke-Api -Method 'GET' -Path '/api/v1/pos/categories' -Token $token -TerminalId $TerminalA -Expected @(200)
  $categories = @($cats.Json)
}
Assert-True ($categories.Count -gt 0) 'categories should not be empty'
$categoryId = [int64]$categories[0].id
$products = Invoke-Api -Method 'GET' -Path "/api/v1/pos/products?categoryId=$categoryId" -Token $token -TerminalId $TerminalA -Expected @(200)
Assert-True ($products.Json.Count -gt 0) 'products should not be empty for first category'
$productId = [int64]$products.Json[0].id
Write-Host "[OK] catalog category=$categoryId product=$productId"

# 5) Find candidate table (not locked by other)
$tables = Invoke-Api -Method 'GET' -Path '/api/v1/pos/salon/tables' -Token $token -TerminalId $TerminalA -Expected @(200)
$candidate = $tables.Json | Where-Object { $_.status -eq 'FREE' -and -not $_.lockedTerminalId } | Select-Object -First 1
if (-not $candidate) {
  $candidate = $tables.Json | Where-Object { -not $_.lockedTerminalId } | Select-Object -First 1
}
if (-not $candidate) {
  throw 'No free lock candidate table found (all tables locked)'
}
$tableNumber = [int]$candidate.tableNumber
Write-Host "[INFO] using table $tableNumber"

# 5b) Alias update by table (PDA path, no admin salon lookup)
$aliasValue = "QA-$tableNumber"
$aliasUpdated = Invoke-Api -Method 'PUT' -Path "/api/v1/pos/salon/tables/$tableNumber/alias" -Token $token -TerminalId $TerminalA -Body @{ alias = $aliasValue } -Expected @(200)
Assert-True ($aliasUpdated.Json.tableNumber -eq $tableNumber) 'alias update should return target tableNumber'
Assert-True ($aliasUpdated.Json.alias -eq $aliasValue) 'alias update should persist alias value'
$tablesAfterAlias = Invoke-Api -Method 'GET' -Path '/api/v1/pos/salon/tables' -Token $token -TerminalId $TerminalA -Expected @(200)
$aliasTable = $tablesAfterAlias.Json | Where-Object { [int]$_.tableNumber -eq $tableNumber } | Select-Object -First 1
Assert-True ($null -ne $aliasTable) 'table should exist after alias update'
Assert-True ([string]$aliasTable.tableAlias -eq $aliasValue) 'tables list should expose updated alias'
Write-Host '[OK] alias update by table'

# 6) Lock race A vs B in parallel
$raceResults = Invoke-LockRace -GatewayBaseUrl $GatewayBaseUrl -Token $token -TableNumber $tableNumber -TerminalA $TerminalA -TerminalB $TerminalB
$race200 = @($raceResults | Where-Object { $_.Status -eq 200 })
$raceDenied = @($raceResults | Where-Object { @(
  403, # backend can map lock conflict to forbidden under race timing
  409  # explicit lock conflict
) -contains $_.Status })
$raceStatuses = (@($raceResults | ForEach-Object { $_.Status } | Sort-Object) -join ',')
Assert-True ($race200.Count -eq 1) "lock race should produce exactly one 200 (got: $raceStatuses)"
Assert-True ($raceDenied.Count -eq 1) "lock race should produce exactly one denied status (403/409) (got: $raceStatuses)"
$raceWinner = $race200[0].TerminalId
$raceLoser = (@((@($TerminalA,$TerminalB) | Where-Object { $_ -ne $raceWinner }))[0])
Write-Host "[OK] lock race winner=$raceWinner loser=$raceLoser"

# cleanup after race to keep deterministic flow
$unlockRace = Invoke-Api -Method 'POST' -Path "/api/v1/pos/salon/tables/$tableNumber/unlock" -Token $token -TerminalId $raceWinner -Body @{ terminalId = $raceWinner } -Expected @(204,409)
if ($unlockRace.Status -eq 204) { Write-Host '[OK] race cleanup unlock' } else { Write-Host '[INFO] race cleanup unlock returned 409' }

# 7) Lock A + collision B (baseline deterministic for rest of test)
$lockA = Invoke-Api -Method 'POST' -Path "/api/v1/pos/salon/tables/$tableNumber/lock" -Token $token -TerminalId $TerminalA -Body @{ terminalId = $TerminalA } -Expected @(200)
Assert-True ($lockA.Json.terminalId -eq $TerminalA) 'lock owner should be terminal A after race cleanup'
$lockB = Invoke-Api -Method 'POST' -Path "/api/v1/pos/salon/tables/$tableNumber/lock" -Token $token -TerminalId $TerminalB -Body @{ terminalId = $TerminalB } -Expected @(409)
Write-Host '[OK] lock baseline A owner + B conflict'

# 8) Heartbeat A
$hbA = Invoke-Api -Method 'POST' -Path "/api/v1/pos/salon/tables/$tableNumber/heartbeat" -Token $token -TerminalId $TerminalA -Body @{ terminalId = $TerminalA } -Expected @(200)
Assert-True ($hbA.Json.terminalId -eq $TerminalA) 'heartbeat should keep terminal A lock'
Write-Host '[OK] heartbeat'

# 9) Open or reuse ticket
$ticketId = $candidate.ticketId
if (-not $ticketId) {
  $opened = Invoke-Api -Method 'POST' -Path "/api/v1/pos/salon/tables/$tableNumber/open-ticket" -Token $token -TerminalId $TerminalA -Expected @(201,409)
  if ($opened.Status -eq 201) {
    $ticketId = [int64]$opened.Json.id
  } else {
    $tables2 = Invoke-Api -Method 'GET' -Path '/api/v1/pos/salon/tables' -Token $token -TerminalId $TerminalA -Expected @(200)
    $current = $tables2.Json | Where-Object { $_.tableNumber -eq $tableNumber } | Select-Object -First 1
    if (-not $current -or -not $current.ticketId) {
      throw "Could not resolve ticket for table $tableNumber after open-ticket conflict"
    }
    $ticketId = [int64]$current.ticketId
  }
}
Write-Host "[OK] ticket id=$ticketId"

# 10) Add line
$ticketAfterAdd = Invoke-Api -Method 'POST' -Path "/api/v1/pos/tickets/$ticketId/lines" -Token $token -TerminalId $TerminalA -Body @{ productId = $productId; qty = 1 } -Expected @(201)
Assert-True ($ticketAfterAdd.Json.lines.Count -gt 0) 'ticket should have at least one line after add'
Write-Host '[OK] add line'

# 10b) Edit unsent line (qty + price) and delete line
$lineToEdit = @($ticketAfterAdd.Json.lines | Where-Object { -not $_.sent } | Select-Object -Last 1)
Assert-True ($lineToEdit.Count -eq 1) 'should have one unsent line to edit'
$lineId = [int64]$lineToEdit[0].id
$originalQty = [int]$lineToEdit[0].qty
$originalPrice = [int]$lineToEdit[0].unitPriceCents

$editedQty = Invoke-Api -Method 'PATCH' -Path "/api/v1/pos/tickets/$ticketId/lines/$lineId" -Token $token -TerminalId $TerminalA -Body @{ qty = ($originalQty + 1) } -Expected @(200)
$editedLineQty = @($editedQty.Json.lines | Where-Object { [int64]$_.id -eq $lineId } | Select-Object -First 1)
Assert-True ($editedLineQty.Count -eq 1) 'edited line should remain in ticket after qty patch'
Assert-True ([int]$editedLineQty[0].qty -eq ($originalQty + 1)) 'line qty should be updated'

$newPrice = $originalPrice + 50
$editedPrice = Invoke-Api -Method 'PATCH' -Path "/api/v1/pos/tickets/$ticketId/lines/$lineId/price" -Token $token -TerminalId $TerminalA -Body @{ priceCents = $newPrice } -Expected @(200)
$editedLinePrice = @($editedPrice.Json.lines | Where-Object { [int64]$_.id -eq $lineId } | Select-Object -First 1)
Assert-True ($editedLinePrice.Count -eq 1) 'edited line should remain in ticket after price patch'
Assert-True ([int]$editedLinePrice[0].unitPriceCents -eq $newPrice) 'line price should be updated'

$afterDelete = Invoke-Api -Method 'DELETE' -Path "/api/v1/pos/tickets/$ticketId/lines/$lineId" -Token $token -TerminalId $TerminalA -Expected @(200)
$deletedLine = @($afterDelete.Json.lines | Where-Object { [int64]$_.id -eq $lineId })
Assert-True ($deletedLine.Count -eq 0) 'line should be removed after delete'
Write-Host '[OK] edit line qty/price + delete line'

# 11) Re-add line, then send preview + send
$ticketAfterReAdd = Invoke-Api -Method 'POST' -Path "/api/v1/pos/tickets/$ticketId/lines" -Token $token -TerminalId $TerminalA -Body @{ productId = $productId; qty = 1 } -Expected @(201)
Assert-True ($ticketAfterReAdd.Json.lines.Count -gt 0) 'ticket should have at least one line after re-add'

# 12) Send preview + send
$preview = Invoke-Api -Method 'GET' -Path "/api/v1/pos/tickets/$ticketId/send-preview" -Token $token -TerminalId $TerminalA -Expected @(200)
Assert-True ($preview.Json.pendingLines.Count -gt 0) 'send-preview should include pending lines'
$send = Invoke-Api -Method 'POST' -Path "/api/v1/pos/tickets/$ticketId/send" -Token $token -TerminalId $TerminalA -Body @{ destination = 'ALL' } -Expected @(200)
Assert-True ($send.Json.sentCount -ge 1) 'send should report at least one sent line'
Write-Host '[OK] send comanda'

# 13) Payment summary and payment (full pending)
$summary = Invoke-Api -Method 'GET' -Path "/api/v1/pos/tickets/$ticketId/payment-summary" -Token $token -TerminalId $TerminalA -Expected @(200)
$pending = [int]$summary.Json.pendingCents
if ($pending -gt 0) {
  $pay = Invoke-Api -Method 'POST' -Path "/api/v1/pos/tickets/$ticketId/payments" -Token $token -TerminalId $TerminalA -Body @{ method = 'CARD'; amountCents = $pending } -Expected @(201)
  Assert-True ($pay.Json.amountCents -eq $pending) 'payment amount should match pending'
  Write-Host '[OK] payment full pending'
} else {
  Write-Host '[INFO] pending already 0, skipping payment'
}

# 14) Unlock cleanup
$unlock = Invoke-Api -Method 'POST' -Path "/api/v1/pos/salon/tables/$tableNumber/unlock" -Token $token -TerminalId $TerminalA -Body @{ terminalId = $TerminalA } -Expected @(204,409)
if ($unlock.Status -eq 204) { Write-Host '[OK] unlock cleanup' } else { Write-Host '[INFO] unlock cleanup returned 409 (already released/expired)' }

# 15) Offline/reconnect replay semantics via idempotency (SEND + PAYMENT)
$tablesReplay = Invoke-Api -Method 'GET' -Path '/api/v1/pos/salon/tables' -Token $token -TerminalId $TerminalA -Expected @(200)
$replayCandidate = $tablesReplay.Json | Where-Object {
  $_.tableNumber -ne $tableNumber -and $_.status -eq 'FREE' -and -not $_.lockedTerminalId -and -not $_.ticketId
} | Select-Object -First 1
if (-not $replayCandidate) {
  $replayCandidate = $tablesReplay.Json | Where-Object {
    $_.status -eq 'FREE' -and -not $_.lockedTerminalId -and -not $_.ticketId
  } | Select-Object -First 1
}
if (-not $replayCandidate) {
  throw 'No free table without ticket available for replay-idempotency scenario'
}
$replayTable = [int]$replayCandidate.tableNumber
Write-Host "[INFO] replay scenario table=$replayTable"

$lockReplay = Invoke-Api -Method 'POST' -Path "/api/v1/pos/salon/tables/$replayTable/lock" -Token $token -TerminalId $TerminalA -Body @{ terminalId = $TerminalA } -Expected @(200)
Assert-True ($lockReplay.Json.terminalId -eq $TerminalA) 'replay scenario lock must be owned by terminal A'

$openReplay = Invoke-Api -Method 'POST' -Path "/api/v1/pos/salon/tables/$replayTable/open-ticket" -Token $token -TerminalId $TerminalA -Expected @(201,409)
if ($openReplay.Status -eq 201) {
  $replayTicketId = [int64]$openReplay.Json.id
} else {
  $tablesReplay2 = Invoke-Api -Method 'GET' -Path '/api/v1/pos/salon/tables' -Token $token -TerminalId $TerminalA -Expected @(200)
  $currentReplay = $tablesReplay2.Json | Where-Object { $_.tableNumber -eq $replayTable } | Select-Object -First 1
  if (-not $currentReplay -or -not $currentReplay.ticketId) {
    throw "Could not resolve replay ticket on table $replayTable"
  }
  $replayTicketId = [int64]$currentReplay.ticketId
}

$ticketReplayAdded = Invoke-Api -Method 'POST' -Path "/api/v1/pos/tickets/$replayTicketId/lines" -Token $token -TerminalId $TerminalA -Body @{ productId = $productId; qty = 1 } -Expected @(201)
Assert-True ($ticketReplayAdded.Json.lines.Count -gt 0) 'replay ticket should have lines'

$sendReplayKey = New-IdempotencyKey 'qa-send-replay'
$sendReplay1 = Invoke-Api -Method 'POST' -Path "/api/v1/pos/tickets/$replayTicketId/send" -Token $token -TerminalId $TerminalA -ExtraHeaders @{ 'Idempotency-Key' = $sendReplayKey } -Body @{ destination = 'ALL' } -Expected @(200)
$sendReplay2 = Invoke-Api -Method 'POST' -Path "/api/v1/pos/tickets/$replayTicketId/send" -Token $token -TerminalId $TerminalA -ExtraHeaders @{ 'Idempotency-Key' = $sendReplayKey } -Body @{ destination = 'ALL' } -Expected @(200)
Assert-True ($sendReplay1.Json.sentCount -ge 1) 'first replay send should send at least one line'
Assert-True ($sendReplay2.Json.sentCount -eq $sendReplay1.Json.sentCount) 'second replay send with same key should return same sentCount'
$send1Ids = @($sendReplay1.Json.sentLineIds) -join ','
$send2Ids = @($sendReplay2.Json.sentLineIds) -join ','
Assert-True ($send1Ids -eq $send2Ids) 'second replay send with same key should return same sentLineIds'
Write-Host '[OK] idempotent replay for SEND'

$summaryReplay = Invoke-Api -Method 'GET' -Path "/api/v1/pos/tickets/$replayTicketId/payment-summary" -Token $token -TerminalId $TerminalA -Expected @(200)
$pendingReplay = [int]$summaryReplay.Json.pendingCents
Assert-True ($pendingReplay -gt 0) 'replay ticket should have pending amount'

$payReplayKey = New-IdempotencyKey 'qa-pay-replay'
$payReplay1 = Invoke-Api -Method 'POST' -Path "/api/v1/pos/tickets/$replayTicketId/payments" -Token $token -TerminalId $TerminalA -ExtraHeaders @{ 'Idempotency-Key' = $payReplayKey } -Body @{ method = 'CARD'; amountCents = $pendingReplay } -Expected @(201)
$payReplay2 = Invoke-Api -Method 'POST' -Path "/api/v1/pos/tickets/$replayTicketId/payments" -Token $token -TerminalId $TerminalA -ExtraHeaders @{ 'Idempotency-Key' = $payReplayKey } -Body @{ method = 'CARD'; amountCents = $pendingReplay } -Expected @(201)
Assert-True ($payReplay1.Json.id -eq $payReplay2.Json.id) 'second replay payment with same key should return same payment id'
Assert-True ($payReplay1.Json.amountCents -eq $payReplay2.Json.amountCents) 'second replay payment with same key should return same amount'

$ticketReplaySummary = Invoke-Api -Method 'GET' -Path "/api/v1/pos/tickets/$replayTicketId/summary" -Token $token -TerminalId $TerminalA -Expected @(200)
$paymentsInSummary = @($ticketReplaySummary.Json.payments)
Assert-True ($paymentsInSummary.Count -eq 1) "replay ticket should contain exactly one persisted payment (got $($paymentsInSummary.Count))"
Assert-True ([int]$ticketReplaySummary.Json.remainingCents -eq 0) 'replay ticket should be fully paid after idempotent replay payment'
Write-Host '[OK] idempotent replay for PAYMENT'

$unlockReplay = Invoke-Api -Method 'POST' -Path "/api/v1/pos/salon/tables/$replayTable/unlock" -Token $token -TerminalId $TerminalA -Body @{ terminalId = $TerminalA } -Expected @(204,409)
if ($unlockReplay.Status -eq 204) { Write-Host '[OK] unlock replay cleanup' } else { Write-Host '[INFO] unlock replay cleanup returned 409' }

# 16) Concurrent move-table race (two tickets -> same destination)
$tablesMove = Invoke-Api -Method 'GET' -Path '/api/v1/pos/salon/tables' -Token $token -TerminalId $TerminalA -Expected @(200)
$moveCandidates = @($tablesMove.Json | Where-Object { $_.status -eq 'FREE' -and -not $_.lockedTerminalId -and -not $_.ticketId } | Select-Object -First 3)
Assert-True ($moveCandidates.Count -ge 3) "need at least 3 free tables for move race (got $($moveCandidates.Count))"

$sourceTableA = [int]$moveCandidates[0].tableNumber
$sourceTableB = [int]$moveCandidates[1].tableNumber
$targetMoveTable = [int]$moveCandidates[2].tableNumber
Write-Host "[INFO] move race sourceA=$sourceTableA sourceB=$sourceTableB target=$targetMoveTable"

$openMoveA = Invoke-Api -Method 'POST' -Path "/api/v1/pos/salon/tables/$sourceTableA/open-ticket" -Token $token -TerminalId $TerminalA -Expected @(201)
$openMoveB = Invoke-Api -Method 'POST' -Path "/api/v1/pos/salon/tables/$sourceTableB/open-ticket" -Token $token -TerminalId $TerminalB -Expected @(201)
$moveTicketA = [int64]$openMoveA.Json.id
$moveTicketB = [int64]$openMoveB.Json.id

$moveRaceResults = Invoke-MoveRace -GatewayBaseUrl $GatewayBaseUrl -Token $token -TicketAId $moveTicketA -TicketBId $moveTicketB -TargetTable $targetMoveTable -TerminalA $TerminalA -TerminalB $TerminalB
$move200 = @($moveRaceResults | Where-Object { $_.Status -eq 200 })
$moveDenied = @($moveRaceResults | Where-Object { @(403,409) -contains $_.Status })
$moveStatuses = (@($moveRaceResults | ForEach-Object { $_.Status } | Sort-Object) -join ',')
Assert-True ($move200.Count -eq 1) "move race should produce exactly one 200 (got: $moveStatuses)"
Assert-True ($moveDenied.Count -eq 1) "move race should produce exactly one denied status (403/409) (got: $moveStatuses)"

$moveWinner = $move200[0]
$winnerTicketId = [int64]$moveWinner.TicketId
$loserTicketId = if ($winnerTicketId -eq $moveTicketA) { $moveTicketB } else { $moveTicketA }

$winnerTicket = Invoke-Api -Method 'GET' -Path "/api/v1/pos/tickets/$winnerTicketId" -Token $token -TerminalId $TerminalA -Expected @(200)
$loserTicket = Invoke-Api -Method 'GET' -Path "/api/v1/pos/tickets/$loserTicketId" -Token $token -TerminalId $TerminalA -Expected @(200)
Assert-True ([int]$winnerTicket.Json.tableNumber -eq $targetMoveTable) "winner ticket should end at target table $targetMoveTable"

$expectedLoserTable = if ($loserTicketId -eq $moveTicketA) { $sourceTableA } else { $sourceTableB }
Assert-True ([int]$loserTicket.Json.tableNumber -eq $expectedLoserTable) "loser ticket should remain on original table $expectedLoserTable"
Write-Host "[OK] move-table race winner ticket=$winnerTicketId target=$targetMoveTable loser ticket=$loserTicketId"

# cleanup move race tickets
$cancelWinner = Invoke-Api -Method 'POST' -Path "/api/v1/pos/tickets/$winnerTicketId/cancel" -Token $token -TerminalId $TerminalA -Expected @(200,409)
$cancelLoser = Invoke-Api -Method 'POST' -Path "/api/v1/pos/tickets/$loserTicketId/cancel" -Token $token -TerminalId $TerminalA -Expected @(200,409)
if ($cancelWinner.Status -eq 200 -and $cancelLoser.Status -eq 200) {
  Write-Host '[OK] cleanup move race tickets'
} else {
  Write-Host '[INFO] cleanup move race tickets returned conflict on one ticket'
}

# 17) Partial payment race (same pending amount in parallel)
$tablesPayRace = Invoke-Api -Method 'GET' -Path '/api/v1/pos/salon/tables' -Token $token -TerminalId $TerminalA -Expected @(200)
$payRaceCandidate = $tablesPayRace.Json | Where-Object { $_.status -eq 'FREE' -and -not $_.lockedTerminalId -and -not $_.ticketId } | Select-Object -First 1
if (-not $payRaceCandidate) {
  throw 'No free table without ticket available for payment race'
}
$payRaceTable = [int]$payRaceCandidate.tableNumber
Write-Host "[INFO] payment race table=$payRaceTable"

$openPayRace = Invoke-Api -Method 'POST' -Path "/api/v1/pos/salon/tables/$payRaceTable/open-ticket" -Token $token -TerminalId $TerminalA -Expected @(201)
$payRaceTicketId = [int64]$openPayRace.Json.id
$ticketPayRaceAdded = Invoke-Api -Method 'POST' -Path "/api/v1/pos/tickets/$payRaceTicketId/lines" -Token $token -TerminalId $TerminalA -Body @{ productId = $productId; qty = 1 } -Expected @(201)
Assert-True ($ticketPayRaceAdded.Json.lines.Count -gt 0) 'payment race ticket should have lines'

$payRaceSummaryBefore = Invoke-Api -Method 'GET' -Path "/api/v1/pos/tickets/$payRaceTicketId/payment-summary" -Token $token -TerminalId $TerminalA -Expected @(200)
$payRacePending = [int]$payRaceSummaryBefore.Json.pendingCents
Assert-True ($payRacePending -gt 0) 'payment race ticket should have pending amount'

$payRaceResults = Invoke-PaymentRace -GatewayBaseUrl $GatewayBaseUrl -Token $token -TicketId $payRaceTicketId -AmountCents $payRacePending -TerminalA $TerminalA -TerminalB $TerminalB
$payRace201 = @($payRaceResults | Where-Object { $_.Status -eq 201 })
$payRaceDenied = @($payRaceResults | Where-Object { @(403,409) -contains $_.Status })
$payRaceStatuses = (@($payRaceResults | ForEach-Object { $_.Status } | Sort-Object) -join ',')
Assert-True ($payRace201.Count -eq 1) "payment race should produce exactly one 201 (got: $payRaceStatuses)"
Assert-True ($payRaceDenied.Count -eq 1) "payment race should produce exactly one denied status (403/409) (got: $payRaceStatuses)"

$payRaceSummaryAfter = Invoke-Api -Method 'GET' -Path "/api/v1/pos/tickets/$payRaceTicketId/payment-summary" -Token $token -TerminalId $TerminalA -Expected @(200)
Assert-True ([int]$payRaceSummaryAfter.Json.pendingCents -eq 0) 'payment race ticket should end with pending=0'
$payRaceTicketSummary = Invoke-Api -Method 'GET' -Path "/api/v1/pos/tickets/$payRaceTicketId/summary" -Token $token -TerminalId $TerminalA -Expected @(200)
$payRacePayments = @($payRaceTicketSummary.Json.payments)
Assert-True ($payRacePayments.Count -eq 1) "payment race ticket should persist exactly one payment (got $($payRacePayments.Count))"
Assert-True ([int]$payRacePayments[0].amountCents -eq $payRacePending) "payment race persisted amount should match pending ($payRacePending)"
Write-Host '[OK] partial payment race guarded against double charge'

Write-Host ''
Write-Host 'PDA E2E smoke PASSED' -ForegroundColor Green
