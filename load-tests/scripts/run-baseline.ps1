. "$PSScriptRoot\common.ps1"

Assert-K6
Assert-BackendHealth
Assert-LoadTestProfile

Invoke-K6Scenario -Scenario "scenarios/smoke.js" -Name "smoke"

$env:TOTAL_STOCK = if ($env:TOTAL_STOCK) { $env:TOTAL_STOCK } else { "100000" }
$env:USER_COUNT = if ($env:USER_COUNT) { $env:USER_COUNT } else { "5000" }
$env:VUS = if ($env:VUS) { $env:VUS } else { "50" }
$env:DURATION = if ($env:DURATION) { $env:DURATION } else { "30s" }
Invoke-K6Scenario -Scenario "scenarios/order-baseline.js" -Name "order-baseline"
Invoke-K6Scenario -Scenario "scenarios/full-journey.js" -Name "full-journey"
