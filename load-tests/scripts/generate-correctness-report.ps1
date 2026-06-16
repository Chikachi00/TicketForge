$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$loadTestsRoot = Split-Path -Parent $scriptDir
$repoRoot = Split-Path -Parent $loadTestsRoot
$resultsDir = Join-Path $loadTestsRoot "results"
$outputPath = Join-Path $repoRoot "docs/performance/correctness-ci.md"

$scenarios = @(
    @{ Name = "smoke"; File = "smoke-summary.json" },
    @{ Name = "oversell spike"; File = "oversell-spike-summary.json" },
    @{ Name = "idempotency retry"; File = "idempotency-retry-summary.json" },
    @{ Name = "payment callback replay"; File = "payment-callback-replay-summary.json" }
)

function Read-Summary($fileName) {
    $path = Join-Path $resultsDir $fileName
    if (-not (Test-Path $path)) {
        return $null
    }
    return Get-Content -Raw $path | ConvertFrom-Json
}

function Metric-Value($summary, $metricName, $valueName) {
    if ($null -eq $summary -or $null -eq $summary.metrics) {
        return "N/A"
    }
    $metric = $summary.metrics.PSObject.Properties[$metricName]
    if ($null -eq $metric -or $null -eq $metric.Value.values) {
        return "N/A"
    }
    $value = $metric.Value.values.PSObject.Properties[$valueName]
    if ($null -eq $value) {
        return "N/A"
    }
    return $value.Value
}

function Format-Number($value) {
    if ($value -is [string]) {
        return $value
    }
    if ($value -is [double] -or $value -is [decimal]) {
        return ("{0:N2}" -f $value)
    }
    return "$value"
}

$generatedAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# TicketForge Correctness CI Report")
$lines.Add("")
$lines.Add("Generated at: $generatedAt")
$lines.Add("")
$lines.Add('This report is generated from local `load-tests/results/*-summary.json` files when they exist. Raw JSON files remain ignored and are not committed.')
$lines.Add("")
$lines.Add("This is a small GitHub Actions correctness run, not a production capacity benchmark.")
$lines.Add("")
$lines.Add("| Scenario | Checks pass rate | Business success | Orders created | OUT_OF_STOCK | Oversell | Inventory inconsistency | Duplicate orders | Duplicate payment processing | Unexpected errors | HTTP requests | RPS | P50 ms | P90 ms | P95 ms |")
$lines.Add("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")

$foundAny = $false
foreach ($scenario in $scenarios) {
    $summary = Read-Summary $scenario.File
    if ($null -ne $summary) {
        $foundAny = $true
    }
    $checks = Metric-Value $summary "checks" "rate"
    $business = Metric-Value $summary "business_success_rate" "rate"
    $orders = Metric-Value $summary "order_created" "count"
    $outOfStock = Metric-Value $summary "out_of_stock" "count"
    $oversell = Metric-Value $summary "oversell_detected" "count"
    $inventory = Metric-Value $summary "inventory_inconsistent" "count"
    $duplicateOrders = Metric-Value $summary "duplicate_order_detected" "count"
    $duplicatePayments = Metric-Value $summary "duplicate_payment_processing_detected" "count"
    $unexpected = Metric-Value $summary "unexpected_error" "count"
    $requests = Metric-Value $summary "http_reqs" "count"
    $rps = Metric-Value $summary "http_reqs" "rate"
    $p50 = Metric-Value $summary "http_req_duration" "med"
    $p90 = Metric-Value $summary "http_req_duration" "p(90)"
    $p95 = Metric-Value $summary "http_req_duration" "p(95)"

    $lines.Add("| $($scenario.Name) | $(Format-Number $checks) | $(Format-Number $business) | $(Format-Number $orders) | $(Format-Number $outOfStock) | $(Format-Number $oversell) | $(Format-Number $inventory) | $(Format-Number $duplicateOrders) | $(Format-Number $duplicatePayments) | $(Format-Number $unexpected) | $(Format-Number $requests) | $(Format-Number $rps) | $(Format-Number $p50) | $(Format-Number $p90) | $(Format-Number $p95) |")
}

$lines.Add("")
if (-not $foundAny) {
    $lines.Add("No local summary JSON files were found. Run the manual performance workflow or local k6 scripts, then rerun this generator.")
} else {
    $lines.Add("The current local summaries include the expected correctness dimensions: smoke, oversell spike, idempotency retry and payment callback replay.")
}
$lines.Add("")
$lines.Add("P99 is intentionally not reported unless it exists in the source summaries.")

$outputDir = Split-Path -Parent $outputPath
if (-not (Test-Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir | Out-Null
}
Set-Content -Path $outputPath -Value ($lines -join [Environment]::NewLine) -Encoding UTF8
Write-Host "Generated $outputPath"
