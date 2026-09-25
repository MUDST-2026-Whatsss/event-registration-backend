@echo off
REM =========================================================================
REM  Runner script for DB_proposal_v2.sql unit tests
REM =========================================================================

cd /d "%~dp0"
echo Running DB Proposal Unit Tests...
node run_tests.js
exit /b %ERRORLEVEL%

