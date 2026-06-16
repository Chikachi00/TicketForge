. "$PSScriptRoot\common.ps1"

Assert-K6
Assert-BackendHealth
Assert-LoadTestProfile
Reset-LoadTestData -TotalStock 5 -UserCount 5
Invoke-K6Scenario -Scenario "scenarios/smoke.js" -Name "smoke"
