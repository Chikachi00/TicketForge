. "$PSScriptRoot\common.ps1"

Assert-K6
Assert-BackendHealth
Assert-LoadTestProfile

$env:TOTAL_STOCK = if ($env:TOTAL_STOCK) { $env:TOTAL_STOCK } else { "100" }
$env:VUS = if ($env:VUS) { $env:VUS } else { "200" }
$env:USER_COUNT = if ($env:USER_COUNT) { $env:USER_COUNT } else { "1200" }
Invoke-K6Scenario -Scenario "scenarios/oversell-spike.js" -Name "oversell-spike"

$env:IDEMPOTENCY_REQUESTS = if ($env:IDEMPOTENCY_REQUESTS) { $env:IDEMPOTENCY_REQUESTS } else { "20" }
Invoke-K6Scenario -Scenario "scenarios/idempotency-retry.js" -Name "idempotency-retry"

$env:CALLBACK_REPLAY_REQUESTS = if ($env:CALLBACK_REPLAY_REQUESTS) { $env:CALLBACK_REPLAY_REQUESTS } else { "20" }
Invoke-K6Scenario -Scenario "scenarios/payment-callback-replay.js" -Name "payment-callback-replay"
