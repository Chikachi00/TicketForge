$ErrorActionPreference = "Stop"

function Get-LoadTestBaseUrl {
    if ($env:BASE_URL) { return $env:BASE_URL }
    return "http://127.0.0.1:8080"
}

function Get-LoadTestSecret {
    if ($env:LOAD_TEST_SECRET) { return $env:LOAD_TEST_SECRET }
    return "ticketforge-local-loadtest-secret"
}

function Assert-K6 {
    $command = Get-Command k6 -ErrorAction SilentlyContinue
    if (-not $command) {
        throw "k6 is not installed. Install it with: winget install k6 --source winget"
    }
    k6 version
}

function Assert-BackendHealth {
    $baseUrl = Get-LoadTestBaseUrl
    $health = Invoke-RestMethod -Uri "$baseUrl/actuator/health" -Method Get
    if ($health.status -ne "UP") {
        throw "Backend health is not UP."
    }
}

function Assert-LoadTestProfile {
    $baseUrl = Get-LoadTestBaseUrl
    $secret = Get-LoadTestSecret
    $headers = @{ "X-Load-Test-Secret" = $secret }
    $profile = Invoke-RestMethod -Uri "$baseUrl/api/load-test/profile" -Method Get -Headers $headers
    if (-not $profile.enabled) {
        throw "Backend is not running with the loadtest profile."
    }
}

function Reset-LoadTestData {
    param(
        [int] $TotalStock = 10,
        [int] $UserCount = 10
    )
    $baseUrl = Get-LoadTestBaseUrl
    $secret = Get-LoadTestSecret
    $eventSlug = if ($env:EVENT_SLUG) { $env:EVENT_SLUG } else { "ticketforge-load-test-live" }
    $ticketCode = if ($env:TICKET_CODE) { $env:TICKET_CODE } else { "LOAD" }
    $headers = @{ "X-Load-Test-Secret" = $secret }
    $body = @{
        eventSlug = $eventSlug
        ticketCode = $ticketCode
        totalStock = $TotalStock
        userCount = $UserCount
    } | ConvertTo-Json
    Invoke-RestMethod -Uri "$baseUrl/api/load-test/reset" -Method Post -Headers $headers -ContentType "application/json" -Body $body | Out-Null
}

function Invoke-K6Scenario {
    param(
        [Parameter(Mandatory = $true)][string] $Scenario,
        [Parameter(Mandatory = $true)][string] $Name
    )
    New-Item -ItemType Directory -Force -Path "results" | Out-Null
    $env:SCENARIO_NAME = $Name
    k6 run $Scenario
    if ($LASTEXITCODE -ne 0) {
        throw "k6 scenario failed: $Scenario"
    }
}
