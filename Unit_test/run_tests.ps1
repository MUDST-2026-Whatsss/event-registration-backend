# =========================================================================
#  PowerShell runner script for DB_proposal_v2.sql unit tests
# =========================================================================

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir
Write-Host "Running DB Proposal Unit Tests..." -ForegroundColor Cyan
& node run_tests.js
exit $LASTEXITCODE

