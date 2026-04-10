<# :
@echo off
powershell -ExecutionPolicy Bypass -File "%~f0" %*
exit /b
#>

# WebDAV Server Launcher - auto-elevates to admin for port 80
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

# Auto-elevate to admin
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "Requesting administrator privileges..." -ForegroundColor Yellow
    Start-Process powershell.exe -Verb RunAs -ArgumentList "-ExecutionPolicy Bypass -File `"$PSCommandPath`"" -Wait
    exit
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  WebDAV File Server (Port 80)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check Java
try { $javaVer = & java -version 2>&1 | Select-String "version" } catch {
    Write-Host "[ERROR] Java not found!" -ForegroundColor Red
    Write-Host "Install JDK 17+: https://adoptium.net/"
    Read-Host "Press Enter to exit"
    exit 1
}
Write-Host "Java: $($javaVer.Line)" -ForegroundColor Green

# Compile if needed
if (-not (Test-Path "WebDAVServer.class") -or (Get-Item "WebDAVServer.java").LastWriteTime -gt (Get-Item "WebDAVServer.class" -ErrorAction SilentlyContinue).LastWriteTime) {
    Write-Host "Compiling WebDAVServer.java..." -ForegroundColor Yellow
    & javac -encoding UTF-8 WebDAVServer.java
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] Compilation failed!" -ForegroundColor Red
        Read-Host "Press Enter to exit"
        exit 1
    }
    Write-Host "Compiled OK" -ForegroundColor Green
}

# Kill any existing server
Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.Path -like "*Eclipse Adoptium*" } | Stop-Process -Force -ErrorAction SilentlyContinue

# Check port 80 is free
$port80 = netstat -ano | Select-String ":80.*LISTENING"
if ($port80) {
    Write-Host "[WARNING] Port 80 is in use:" -ForegroundColor Yellow
    Write-Host $port80
    Write-Host "Attempting to free it..." -ForegroundColor Yellow
    $pids = ($port80 -split "\s+")[-1]
    Stop-Process -Id $pids -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1
}

# Get local IP
$ip = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.InterfaceAlias -notlike "*Loopback*" -and $_.IPAddress -ne "127.0.0.1" } | Select-Object -First 1).IPAddress
if (-not $ip) { $ip = "YOUR_IP" }

Write-Host ""
Write-Host "Server address: http://localhost:80" -ForegroundColor White
Write-Host "Network address: http://${ip}:80" -ForegroundColor White
Write-Host "Username: admin" -ForegroundColor White
Write-Host "Password: admin123" -ForegroundColor White
Write-Host ""
Write-Host "Android app config:" -ForegroundColor Yellow
Write-Host "  Protocol: WebDAV" -ForegroundColor White
Write-Host "  Address:  http://${ip}:80" -ForegroundColor White
Write-Host "  User:     admin" -ForegroundColor White
Write-Host "  Pass:     admin123" -ForegroundColor White
Write-Host ""
Write-Host "Press Ctrl+C to stop" -ForegroundColor Gray
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

& java WebDAVServer
Write-Host ""
Write-Host "Server stopped." -ForegroundColor Yellow
Read-Host "Press Enter to exit"
