<# :
@echo off
powershell -ExecutionPolicy Bypass -File "%~f0" %*
exit /b
#>

# SMB3 Share Configuration - auto-elevates to admin
$ErrorActionPreference = "SilentlyContinue"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# Auto-elevate to admin
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "Requesting administrator privileges..." -ForegroundColor Yellow
    Start-Process powershell.exe -Verb RunAs -ArgumentList "-ExecutionPolicy Bypass -File `"$PSCommandPath`"" -Wait
    exit
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  SMB3 Share Configuration (Port 445)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Create share directory
Write-Host "[1/4] Creating share folder C:\SMBShare..." -ForegroundColor Yellow
New-Item -ItemType Directory -Path "C:\SMBShare" -Force | Out-Null
New-Item -ItemType Directory -Path "C:\SMBShare\$([char]0x6587)$([char]0x6863)" -Force | Out-Null
New-Item -ItemType Directory -Path "C:\SMBShare\$([char]0x6587)$([char]0x6863)\$([char]0x5B50)$([char]0x6587)$([char]0x4EF6)$([char]0x5939)" -Force | Out-Null
New-Item -ItemType Directory -Path "C:\SMBShare\$([char]0x7167)$([char]0x7247)" -Force | Out-Null
New-Item -ItemType Directory -Path "C:\SMBShare\$([char]0x97F3)$([char]0x4E50)" -Force | Out-Null
New-Item -ItemType Directory -Path "C:\SMBShare\$([char]0x9879)$([char]0x76EE)$([char]0x8D44)$([char]0x6599)" -Force | Out-Null
Write-Host "  Folders created" -ForegroundColor Green

# Step 2: Create demo files
Write-Host "[2/4] Creating demo files..." -ForegroundColor Yellow
$enc = [System.Text.Encoding]::UTF8

$files = @{
    "$([char]0x6587)$([char]0x6863)\readme.txt" = "$([char]0x6B22)$([char]0x8FCE)$([char]0x4F7F)$([char]0x7528)$([char]0x8FDC)$([char]0x7A0B)$([char]0x6587)$([char]0x4EF6)$([char]0x7BA1)$([char]0x7406)$([char]0x5668)`n$([char]0x8FD9)$([char]0x662F)$([char]0x4E00)$([char]0x4E2A)$([char]0x6F14)$([char]0x793A)$([char]0x6587)$([char]0x4EF6)`n$([char]0x652F)$([char]0x6301) WebDAV $([char]0x548C) SMB3"
    "$([char]0x6587)$([char]0x6863)\$([char]0x4F1A)$([char]0x8BAE)$([char]0x8BB0)$([char]0x5F55).txt" = "2026$([char]0x5E74)4$([char]0x6708)10$([char]0x65E5) $([char]0x4F1A)$([char]0x8BAE)$([char]0x7EAA)$([char]0x8981)`n`n1. $([char]0x9879)$([char]0x76EE)$([char]0x8FDB)$([char]0x5C55)$([char]0x987A)$([char]0x5229)`n2. $([char]0x5DF2)$([char]0x5B8C)$([char]0x6210) WebDAV`n3. $([char]0x5DF2)$([char]0x5B8C)$([char]0x6210) SMB3"
    "$([char]0x6587)$([char]0x6863)\$([char]0x5B50)$([char]0x6587)$([char]0x4EF6)$([char]0x5939)\$([char]0x5D4C)$([char]0x5957)$([char]0x6587)$([char]0x4EF6).txt" = "$([char]0x8FD9)$([char]0x662F)$([char]0x5D4C)$([char]0x5957)$([char]0x76EE)$([char]0x5F55)$([char]0x4E2D)$([char]0x7684)$([char]0x6587)$([char]0x4EF6)"
    "$([char]0x7167)$([char]0x7247)\$([char]0x98CE)$([char]0x666F).txt" = "[$([char]0x98CE)$([char]0x666F)$([char]0x7167)$([char]0x7247]"
    "$([char]0x7167)$([char]0x7247)\$([char]0x5408)$([char]0x5F71).txt" = "[$([char]0x5408)$([char]0x5F71)$([char]0x7167)$([char]0x7247]"
    "$([char]0x97F3)$([char]0x4E50)\$([char]0x6B4C)$([char]0x66F2)$([char]0x5217)$([char]0x8868).txt" = "1. $([char]0x6625)$([char]0x98CE)$([char]0x5341)$([char]0x91CC)`n2. $([char]0x590F)$([char]0x591C)$([char]0x665A)$([char]0x98CE)"
    "$([char]0x9879)$([char]0x76EE)$([char]0x8D44)$([char]0x6599)\$([char]0x9700)$([char]0x6C42)$([char]0x6587)$([char]0x6863).txt" = "$([char]0x9879)$([char]0x76EE)$([char]0x9700)$([char]0x6C42)$([char]0x6587)$([char]0x6863) v1.0`n`n$([char]0x6838)$([char]0x5FC3)$([char]0x529F)$([char]0x80FD):`n1. $([char]0x6587)$([char]0x4EF6)$([char]0x6D4F)$([char]0x89C8)`n2. $([char]0x6587)$([char]0x4EF6)$([char]0x4E0A)$([char]0x4F20)$([char]0x4E0B)$([char]0x8F7D)"
    "$([char]0x9879)$([char]0x76EE)$([char]0x8D44)$([char]0x6599)\$([char]0x6280)$([char]0x672F)$([char]0x65B9)$([char]0x6848).txt" = "$([char]0x6280)$([char]0x672F)$([char]0x67B6)$([char]0x6784):`n- Kotlin + Jetpack Compose`n- OkHttp (WebDAV)`n- jcifs-ng (SMB3)"
    "$([char]0x6D4B)$([char]0x8BD5)$([char]0x6587)$([char]0x4EF6).txt" = "$([char]0x8FD9)$([char]0x662F)$([char]0x4E00)$([char]0x884C)$([char]0x6D4B)$([char]0x8BD5)$([char]0x6570)$([char]0x636E)"
}

foreach ($f in $files.GetEnumerator()) {
    [System.IO.File]::WriteAllText("C:\SMBShare\$($f.Key)", $f.Value, $enc)
}
Write-Host "  Demo files created" -ForegroundColor Green

# Step 3: Configure SMB share
Write-Host "[3/4] Configuring SMB share..." -ForegroundColor Yellow
& net share SMBShare /delete 2>&1 | Out-Null
$result = & net share SMBShare="C:\SMBShare" /GRANT:everyone,FULL 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "  Share 'SMBShare' created" -ForegroundColor Green
} else {
    Write-Host "  Trying PowerShell method..." -ForegroundColor Yellow
    New-SmbShare -Name "SMBShare" -Path "C:\SMBShare" -FullAccess "Everyone" -ErrorAction SilentlyContinue | Out-Null
}

# Step 4: Firewall
Write-Host "[4/4] Configuring firewall..." -ForegroundColor Yellow
$rule = Get-NetFirewallRule -DisplayName "SMB-In" -ErrorAction SilentlyContinue
if (-not $rule) {
    New-NetFirewallRule -DisplayName "SMB-In" -Direction Inbound -Protocol TCP -LocalPort 445 -Action Allow | Out-Null
    Write-Host "  Firewall rule added" -ForegroundColor Green
} else {
    Write-Host "  Firewall rule exists" -ForegroundColor Green
}

# Get local IP
$ip = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.InterfaceAlias -notlike "*Loopback*" -and $_.IPAddress -ne "127.0.0.1" } | Select-Object -First 1).IPAddress
if (-not $ip) { $ip = "YOUR_IP" }

# Get username
$user = $env:USERNAME

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  Configuration Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Share name: SMBShare" -ForegroundColor White
Write-Host "  Path:       C:\SMBShare" -ForegroundColor White
Write-Host "  Port:       445" -ForegroundColor White
Write-Host "  Your IP:    $ip" -ForegroundColor White
Write-Host "  Your user:  $user" -ForegroundColor White
Write-Host ""
Write-Host "  Android app config:" -ForegroundColor Yellow
Write-Host "  Protocol:  SMB3" -ForegroundColor White
Write-Host "  IP:        $ip" -ForegroundColor White
Write-Host "  Share:     SMBShare" -ForegroundColor White
Write-Host "  Username:  $user" -ForegroundColor White
Write-Host "  Password:  (your Windows password)" -ForegroundColor White
Write-Host ""
Write-Host "  To create a dedicated user:" -ForegroundColor Gray
Write-Host "  net user smbuser MyPass123 /add" -ForegroundColor Gray
Write-Host ""
Read-Host "Press Enter to exit"
