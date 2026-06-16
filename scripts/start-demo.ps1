$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendUrl = "http://localhost:8080"
$frontendUrl = "http://localhost:5173"
$demoSecret = if ($env:TICKETFORGE_DEMO_SECRET) { $env:TICKETFORGE_DEMO_SECRET } else { "ticketforge-local-demo-secret" }

function Wait-HttpOk($url, $headers = @{}, $seconds = 90) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $url -Headers $headers -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                return $true
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    }
    return $false
}

Write-Host "Checking environment..."
& (Join-Path $PSScriptRoot "check-environment.ps1")

Write-Host "Launching backend in a new PowerShell window..."
Start-Process powershell.exe -ArgumentList @(
    "-NoExit",
    "-ExecutionPolicy", "Bypass",
    "-File", "`"$(Join-Path $PSScriptRoot 'start-backend.ps1')`""
) -WorkingDirectory $repoRoot

Write-Host "Waiting for backend health..."
if (-not (Wait-HttpOk "$backendUrl/actuator/health" @{} 120)) {
    throw "Backend health did not become available. Check the backend PowerShell window for Maven, Java or database errors."
}

Write-Host "Verifying demo profile..."
if (-not (Wait-HttpOk "$backendUrl/api/demo/profile" @{ "X-Demo-Secret" = $demoSecret } 30)) {
    throw "Demo profile is not available. Start the backend with profile demo and verify TICKETFORGE_DEMO_SECRET."
}

Write-Host "Launching frontend in a new PowerShell window..."
Start-Process powershell.exe -ArgumentList @(
    "-NoExit",
    "-ExecutionPolicy", "Bypass",
    "-File", "`"$(Join-Path $PSScriptRoot 'start-frontend.ps1')`""
) -WorkingDirectory $repoRoot

Write-Host "Waiting for frontend..."
if (-not (Wait-HttpOk $frontendUrl @{} 90)) {
    throw "Frontend did not become available on http://localhost:5173. Check the frontend PowerShell window."
}

Write-Host "Opening $frontendUrl"
Start-Process $frontendUrl
Write-Host "TicketForge demo is ready."
