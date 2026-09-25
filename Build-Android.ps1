[CmdletBinding()]
param([switch]$OverwriteUnpublished)
# Builds the Release APK into bin/Android/Release-<VERSION>/AF-SensorMonitor-<VERSION>.apk (R8-optimized, no debug
# symbols, not debuggable), signed with this project's local release key, and verifies the signature.
# The key and its DPAPI-encrypted password live in .local/android-signing/ (not in Git).
# The APK is for installing on your own devices; it is not distributed.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$version = (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'VERSION.txt') -Raw).Trim()
if ($version -notmatch '^\d+\.\d+\.\d+$') { throw "VERSION.txt must be MAJOR.MINOR.PATCH: $version" }
$outDir = Join-Path $PSScriptRoot "bin\Android\Release-$version"
$output = Join-Path $outDir "AF-SensorMonitor-$version.apk"
if ((Test-Path -LiteralPath $output) -and -not $OverwriteUnpublished) { throw "Release APK already exists: $output" }
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr' }
$keytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
if (-not (Test-Path -LiteralPath $keytool)) { throw "keytool not found. Set JAVA_HOME to a JDK 17+ (e.g. Android Studio's jbr)." }
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$apksigner = Get-ChildItem -LiteralPath (Join-Path $sdk 'build-tools') -Directory | Sort-Object { [version]($_.Name -replace '[^\d.].*$', '') } |
    Select-Object -Last 1 | ForEach-Object { Join-Path $_.FullName 'apksigner.bat' }
if (-not $apksigner -or -not (Test-Path -LiteralPath $apksigner)) { throw 'apksigner not found in Android SDK build-tools.' }

$local = Join-Path $PSScriptRoot '.local\android-signing'
New-Item -ItemType Directory -Force $local | Out-Null
$secretPath = Join-Path $local 'password.dpapi'
$keyPath = Join-Path $local 'sensormonitor.keystore'
if (-not (Test-Path -LiteralPath $secretPath)) {
    if (Test-Path -LiteralPath $keyPath) { throw 'Existing key has no password file. Restore the signing credentials.' }
    $bytes = New-Object byte[] 32
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    $secret = ConvertTo-SecureString ([Convert]::ToBase64String($bytes)) -AsPlainText -Force
    $secret | ConvertFrom-SecureString | Set-Content -LiteralPath $secretPath
}
$secure = (Get-Content -LiteralPath $secretPath -Raw).Trim() | ConvertTo-SecureString
$env:AFSM_KEY_PASSWORD = [Net.NetworkCredential]::new('', $secure).Password
$env:AFSM_KEYSTORE = $keyPath
try {
    if (-not (Test-Path -LiteralPath $keyPath)) {
        & $keytool -genkeypair -keystore $keyPath -storetype PKCS12 -alias sensormonitor -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=AF-SensorMonitor, O=AF' -storepass:env AFSM_KEY_PASSWORD -keypass:env AFSM_KEY_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Release signing key generation failed.' }
    }
    $logDir = Join-Path $PSScriptRoot 'Builds\Logs'
    New-Item -ItemType Directory -Force $logDir | Out-Null
    $log = Join-Path $logDir ('android-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ') + '.log')
    Write-Host "Building AF-SensorMonitor $version Release APK (log: $log)"
    & (Join-Path $PSScriptRoot 'gradlew.bat') --console=plain clean testDebugUnitTest assembleRelease *> $log
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed. See $log" }
    $built = Join-Path $PSScriptRoot 'app\build\outputs\apk\release\app-release.apk'
    if (-not (Test-Path -LiteralPath $built)) { throw "Release APK was not produced (signing configured?). See $log" }
    & $apksigner verify --print-certs $built | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    New-Item -ItemType Directory -Force $outDir | Out-Null
    Copy-Item -LiteralPath $built -Destination $output -Force
    $hash = (Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash
    Set-Content -LiteralPath "$output.sha256" -Value "$hash  AF-SensorMonitor-$version.apk"
    Write-Output $output
} finally {
    Remove-Item Env:AFSM_KEY_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:AFSM_KEYSTORE -ErrorAction SilentlyContinue
}
