$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $repoRoot "backend"

$env:HTTP_PROXY = "http://127.0.0.1:7890"
$env:HTTPS_PROXY = "http://127.0.0.1:7890"

Set-Location $backendDir
Write-Host "Starting TicketForge backend with the demo profile..."
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
