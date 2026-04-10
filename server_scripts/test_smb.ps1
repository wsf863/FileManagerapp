# SMB3 Connection Test
$ErrorActionPreference = 'Continue'

$hostAddr = "u3ff4d95.natappfree.cc"
$port = 55051
$shareName = "SMBData"
$username = "32431"
$password = Read-Host "Enter Windows password for user $username" -AsSecureString
$plainPassword = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto([System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($password))

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  SMB3 Connection Test via Tunnel" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Host:     $hostAddr"
Write-Host "  Port:     $port"
Write-Host "  Share:    $shareName"
Write-Host "  Username: $username"
Write-Host ""

# Step 1: TCP connectivity test
Write-Host "[1] Testing TCP connection to tunnel..." -ForegroundColor Yellow
try {
    $tcp = New-Object System.Net.Sockets.TcpClient
    $tcp.Connect($hostAddr, $port)
    Write-Host "    TCP connected successfully" -ForegroundColor Green
    $tcp.Close()
} catch {
    Write-Host "    TCP failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Step 2: Write Java test program
Write-Host "[2] Preparing SMB test..." -ForegroundColor Yellow
$javaTestCode = @"
import eu.agno3.jcifs.internal.Version;
import eu.agno3.jcifs.*;
import eu.agno3.jcifs.config.PropertyConfiguration;
import eu.agno3.jcifs.context.*;
import eu.agno3.jcifs.smb.*;
import java.util.Properties;
import java.util.List;

public class SmbTest {
    public static void main(String[] args) throws Exception {
        String host = "$hostAddr";
        int port = $port;
        String share = "$shareName";
        String user = "$username";
        String pass = "$plainPassword";

        System.out.println("jcifs version: " + Version.getVersion());

        Properties props = new Properties();
        props.setProperty("jcifs.smb.client.responseTimeout", "10000");
        props.setProperty("jcifs.smb.client.soTimeout", "15000");
        props.setProperty("jcifs.smb.client.connTimeout", "15000");
        props.setProperty("jcifs.smb.client.transportFactory", "eu.agno3.jcifs.smb.SmbTransportFactory");
        Configuration config = new PropertyConfiguration(props);

        CIFSContext baseCtx = new SingletonContext(config);
        NtlmPasswordAuthenticator auth = new NtlmPasswordAuthenticator(null, user, pass);
        CIFSContext ctx = baseCtx.withCredentials(auth);

        String url = "smb://" + host + ":" + port + "/" + share + "/";
        System.out.println("Connecting to: " + url);

        try {
            SmbFile smb = new SmbFile(url, ctx);
            boolean exists = smb.exists();
            System.out.println("Share exists: " + exists);

            if (exists) {
                SmbFile[] files = smb.listFiles();
                System.out.println("Files in share (" + files.length + "):");
                for (SmbFile f : files) {
                    String name = f.getName();
                    boolean isDir = f.isDirectory();
                    System.out.println("  [" + (isDir ? "DIR" : "FILE") + "] " + name);
                }
            }
            smb.close();
            System.out.println("TEST_PASSED");
        } catch (SmbAuthException e) {
            System.out.println("AUTH_FAILED: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
"@

Set-Content -Path "$env:TEMP\SmbTest.java" -Value $javaTestCode -Encoding UTF8

Write-Host "[3] Compiling and running SMB test..." -ForegroundColor Yellow

# Find jcifs-ng jar
$m2Repo = "$env:USERPROFILE\.m2\repository\eu\agno3\jcifs\jcifs-ng\2.1.10\jcifs-ng-2.1.10.jar"
if (-not (Test-Path $m2Repo)) {
    $m2Repo = "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\eu.agno3.jcifs\jcifs-ng\2.1.10\*\jcifs-ng-2.1.10.jar"
    $jars = Get-ChildItem -Path "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\eu.agno3.jcifs\jcifs-ng\2.1.10" -Recurse -Filter "*.jar" -ErrorAction SilentlyContinue
    if ($jars) { $m2Repo = $jars[0].FullName }
}

if (Test-Path $m2Repo) {
    Write-Host "    Found jcifs-ng: $m2Repo" -ForegroundColor Gray
    $classPath = $m2Repo
} else {
    Write-Host "    jcifs-ng not found locally, will download..." -ForegroundColor Yellow
    $m2Repo = "$env:TEMP\jcifs-ng-2.1.10.jar"
    Invoke-WebRequest -Uri "https://repo1.maven.org/maven2/eu/agno3/jcifs/jcifs-ng/2.1.10/jcifs-ng-2.1.10.jar" -OutFile $m2Repo -UseBasicParsing
    $classPath = $m2Repo
    Write-Host "    Downloaded jcifs-ng" -ForegroundColor Green
}

# Compile
$compileErr = "$env:TEMP\SmbTest_compile.err"
javac -encoding UTF-8 -cp $classPath "$env:TEMP\SmbTest.java" 2> $compileErr
if ($LASTEXITCODE -ne 0) {
    Write-Host "    Compilation errors:" -ForegroundColor Red
    Get-Content $compileErr | Select-Object -First 10
    exit 1
}

# Run
Write-Host "    Running SMB test..." -ForegroundColor Gray
$javaOutput = java -cp "$classPath;$env:TEMP" SmbTest 2>&1
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  SMB Test Result" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host $javaOutput
Write-Host ""

if ($javaOutput -match "TEST_PASSED") {
    Write-Host "Result: SUCCESS" -ForegroundColor Green
} elseif ($javaOutput -match "AUTH_FAILED") {
    Write-Host "Result: AUTH FAILED - Check username/password" -ForegroundColor Red
} elseif ($javaOutput -match "ERROR") {
    Write-Host "Result: ERROR" -ForegroundColor Red
} else {
    Write-Host "Result: UNKNOWN" -ForegroundColor Yellow
}
