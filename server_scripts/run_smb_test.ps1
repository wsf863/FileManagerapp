$ErrorActionPreference = 'Continue'
$hostAddr = "u3ff4d95.natappfree.cc"
$port = 55051
$shareName = "SMBData"
$username = "32431"
$password = "123321"

Write-Host "SMB Connection Test"
Write-Host "Host: $hostAddr`:$port"
Write-Host "Share: $shareName"
Write-Host "User: $username"
Write-Host ""

# Find jcifs-ng jar
$cp = $null
$m2Repo = "$env:USERPROFILE\.m2\repository\eu\agno3\jcifs\jcifs-ng\2.1.10\jcifs-ng-2.1.10.jar"
if (Test-Path $m2Repo) {
    $cp = $m2Repo
    Write-Host "Found jcifs-ng: $cp"
}
if (-not $cp) {
    $jars = Get-ChildItem -Path "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\eu.agno3.jcifs\jcifs-ng\2.1.10" -Recurse -Filter "*.jar" -ErrorAction SilentlyContinue
    if ($jars) {
        $cp = $jars[0].FullName
        Write-Host "Found jcifs-ng via gradle: $cp"
    }
}
if (-not $cp) {
    $cp = "$env:TEMP\jcifs-ng-2.1.10.jar"
    if (-not (Test-Path $cp)) {
        Write-Host "Downloading jcifs-ng..."
        Invoke-WebRequest -Uri "https://repo1.maven.org/maven2/eu/agno3/jcifs/jcifs-ng/2.1.10/jcifs-ng-2.1.10.jar" -OutFile $cp -UseBasicParsing
        Write-Host "Downloaded"
    }
}

# Write Java test file
$javaCode = @"
import eu.agno3.jcifs.internal.Version;
import eu.agno3.jcifs.*;
import eu.agno3.jcifs.config.*;
import eu.agno3.jcifs.context.*;
import eu.agno3.jcifs.smb.*;
import java.util.Properties;

public class SmbTest {
    public static void main(String[] args) throws Exception {
        String host = "u3ff4d95.natappfree.cc";
        int port = 55051;
        String share = "SMBData";
        String user = "32431";
        String pass = "123321";

        System.out.println("jcifs version: " + Version.getVersion());
        System.out.println("URL: smb://" + host + ":" + port + "/" + share + "/");

        Properties props = new Properties();
        props.setProperty("jcifs.smb.client.responseTimeout", "10000");
        props.setProperty("jcifs.smb.client.soTimeout", "15000");
        props.setProperty("jcifs.smb.client.connTimeout", "15000");
        Configuration config = new PropertyConfiguration(props);

        CIFSContext baseCtx = new SingletonContext(config);
        NtlmPasswordAuthenticator auth = new NtlmPasswordAuthenticator(null, user, pass);
        CIFSContext ctx = baseCtx.withCredentials(auth);

        String url = "smb://" + host + ":" + port + "/" + share + "/";
        try {
            SmbFile smb = new SmbFile(url, ctx);
            boolean exists = smb.exists();
            System.out.println("Share exists: " + exists);
            if (exists) {
                SmbFile[] files = smb.listFiles();
                System.out.println("Files found (" + files.length + "):");
                for (SmbFile f : files) {
                    System.out.println("  [" + (f.isDirectory() ? "DIR" : "FILE") + "] " + f.getName());
                }
            }
            smb.close();
            System.out.println("RESULT:SUCCESS");
        } catch (SmbAuthException e) {
            System.out.println("RESULT:AUTH_FAILED - " + e.getMessage());
        } catch (Exception e) {
            System.out.println("RESULT:ERROR - " + e.getClass().getName() + ": " + e.getMessage());
        }
    }
}
"@

$javaFile = "$env:TEMP\SmbTest.java"
$classDir = "$env:TEMP\smbtest_classes"
New-Item -ItemType Directory -Path $classDir -Force | Out-Null
Set-Content -Path $javaFile -Value $javaCode -Encoding UTF8

Write-Host "Compiling..."
javac -encoding UTF-8 -d $classDir -cp $cp $javaFile
if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilation failed"
    exit 1
}

Write-Host "Running SMB test..."
$output = java -cp "$cp;$classDir" SmbTest 2>&1
Write-Host ""
Write-Host "========== RESULT =========="
Write-Host $output
