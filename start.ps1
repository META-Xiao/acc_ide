#!/usr/bin/env pwsh

# Tree-sitter Build Launcher
# Usage: ./start.ps1

Write-Host "=== Tree-sitter Builder Launcher ===" -ForegroundColor Green
Write-Host "Starting interactive build process..." -ForegroundColor Cyan
Write-Host ""

# Change to the treesitter_build directory and run the main script
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$buildScript = Join-Path $scriptDir "treesitter_build\Build-Complete-TreeSitter.ps1"

if (Test-Path $buildScript) {
    Set-Location (Join-Path $scriptDir "treesitter_build")
    & ".\Build-Complete-TreeSitter.ps1"
} else {
    Write-Host "ERROR: Build script not found at: $buildScript" -ForegroundColor Red
    Write-Host "Please ensure you're running this from the project root directory." -ForegroundColor Yellow
    exit 1
} 