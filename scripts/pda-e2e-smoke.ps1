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
  if ($Response -is [System.Net.Http.HttpResponseMessage]) {
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
Write-Host "[OK] lock race winner=$raceWinner loser=$((@($TerminalA,$TerminalB) | Where-Object { $_ -ne $raceWinner })[0])"

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

# 11) Send preview + send
$preview = Invoke-Api -Method 'GET' -Path "/api/v1/pos/tickets/$ticketId/send-preview" -Token $token -TerminalId $TerminalA -Expected @(200)
Assert-True ($preview.Json.pendingLines.Count -gt 0) 'send-preview should include pending lines'
$send = Invoke-Api -Method 'POST' -Path "/api/v1/pos/tickets/$ticketId/send" -Token $token -TerminalId $TerminalA -Body @{ destination = 'ALL' } -Expected @(200)
Assert-True ($send.Json.sentCount -ge 1) 'send should report at least one sent line'
Write-Host '[OK] send comanda'

# 12) Payment summary and payment (full pending)
$summary = Invoke-Api -Method 'GET' -Path "/api/v1/pos/tickets/$ticketId/payment-summary" -Token $token -TerminalId $TerminalA -Expected @(200)
$pending = [int]$summary.Json.pendingCents
if ($pending -gt 0) {
  $pay = Invoke-Api -Method 'POST' -Path "/api/v1/pos/tickets/$ticketId/payments" -Token $token -TerminalId $TerminalA -Body @{ method = 'CARD'; amountCents = $pending } -Expected @(201)
  Assert-True ($pay.Json.amountCents -eq $pending) 'payment amount should match pending'
  Write-Host '[OK] payment full pending'
} else {
  Write-Host '[INFO] pending already 0, skipping payment'
}

# 13) Unlock cleanup
$unlock = Invoke-Api -Method 'POST' -Path "/api/v1/pos/salon/tables/$tableNumber/unlock" -Token $token -TerminalId $TerminalA -Body @{ terminalId = $TerminalA } -Expected @(204,409)
if ($unlock.Status -eq 204) { Write-Host '[OK] unlock cleanup' } else { Write-Host '[INFO] unlock cleanup returned 409 (already released/expired)' }

# 14) Offline/reconnect replay semantics via idempotency (SEND + PAYMENT)
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

Write-Host ''
Write-Host 'PDA E2E smoke PASSED' -ForegroundColor Green
