$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendDir = Join-Path $repoRoot "frontend"

$env:HTTP_PROXY = "http://127.0.0.1:7890"
$env:HTTPS_PROXY = "http://127.0.0.1:7890"
$env:VITE_DEMO_SECRET = if ($env:VITE_DEMO_SECRET) { $env:VITE_DEMO_SECRET } else { "ticketforge-local-demo-secret" }

Set-Location $frontendDir
if (-not (Test-Path (Join-Path $frontendDir "node_modules"))) {
    Write-Host "Installing frontend dependencies..."
    npm.cmd install
}

Write-Host "Starting TicketForge frontend..."
npm.cmd run dev
