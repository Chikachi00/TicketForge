$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot

function Write-Check($name, $status, $detail = "") {
    $suffix = if ($detail) { " - $detail" } else { "" }
    Write-Host "[$status] $name$suffix"
}

function Get-PortOwner($port) {
    $connection = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $connection) {
        return $null
    }
    return $connection.OwningProcess
}

$java = Get-Command java -ErrorAction SilentlyContinue
if ($java) {
    $versionText = (cmd /c "java -version 2>&1" | Select-Object -First 1)
    $major = if ($versionText -match '"(\d+)') { [int]$Matches[1] } else { 0 }
    if ($major -eq 21) {
        Write-Check "Java" "OK" $versionText
    } else {
        Write-Check "Java" "WARN" "Java 21 is recommended; detected: $versionText"
    }
} else {
    Write-Check "Java" "FAIL" "Install Java 21 and make java available on PATH."
}

$node = Get-Command node -ErrorAction SilentlyContinue
if ($node) {
    Write-Check "Node.js" "OK" (& node --version)
} else {
    Write-Check "Node.js" "FAIL" "Install Node.js LTS."
}

$npm = Get-Command npm.cmd -ErrorAction SilentlyContinue
if ($npm) {
    Write-Check "npm" "OK" (& npm.cmd --version)
} else {
    Write-Check "npm" "FAIL" "Install npm with Node.js."
}

$postgresReachable = Test-NetConnection -ComputerName "localhost" -Port 5432 -InformationLevel Quiet -WarningAction SilentlyContinue
if ($postgresReachable) {
    Write-Check "PostgreSQL 5432" "OK" "localhost:5432 is reachable."
} else {
    Write-Check "PostgreSQL 5432" "WARN" "Start local PostgreSQL before launching the backend."
}

if (Test-Path (Join-Path $repoRoot "backend/src/main/resources/application.yml")) {
    Write-Check "ticketforge database config" "OK" "application.yml contains the local ticketforge defaults."
} else {
    Write-Check "ticketforge database config" "FAIL" "Missing backend application.yml."
}

foreach ($port in @(8080, 5173)) {
    $ownerPid = Get-PortOwner $port
    if ($ownerPid) {
        Write-Check "Port $port" "WARN" "Already in use by PID $ownerPid. Close the old backend/frontend if this is not expected."
    } else {
        Write-Check "Port $port" "OK" "Available."
    }
}

Write-Host ""
Write-Host "Environment check complete. This script does not stop existing java.exe or node.exe processes."
