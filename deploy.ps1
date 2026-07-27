# CashLog — wireless deploy script
# Run from the repo root: .\deploy.ps1
# On first run you may need: Set-ExecutionPolicy -Scope CurrentUser RemoteSigned

# ── Config ────────────────────────────────────────────────────────────────────
$ADB        = "D:\Android\Sdk\platform-tools\adb.exe"
$JAVA_HOME  = "D:\AndroidStudio\jbr"
$PHONE_GUID = "adb-34703d4a-jWR7Zq"
$PHONE_IP   = "192.168.1.161"
$PACKAGE    = "com.maru.cashlog"
$REPO_DIR   = $PSScriptRoot
$ANDROID    = Join-Path $REPO_DIR "android"
$APK        = Join-Path $ANDROID "app\build\outputs\apk\debug\app-debug.apk"
# ─────────────────────────────────────────────────────────────────────────────

function Info  ($msg) { Write-Host "  $msg" -ForegroundColor Cyan }
function Ok    ($msg) { Write-Host "  ✓ $msg" -ForegroundColor Green }
function Warn  ($msg) { Write-Host "  ! $msg" -ForegroundColor Yellow }
function Fail  ($msg) { Write-Host "  ✗ $msg" -ForegroundColor Red }
function Step  ($msg) { Write-Host "`n[$msg]" -ForegroundColor White }

# ── Java ──────────────────────────────────────────────────────────────────────
$env:JAVA_HOME = $JAVA_HOME
$env:PATH = "$JAVA_HOME\bin;$env:PATH"

# ── Step 1: Git pull ──────────────────────────────────────────────────────────
Step "1/5  Git pull"
$pull = git -C $REPO_DIR pull 2>&1
Write-Host "  $pull"
if ($LASTEXITCODE -ne 0) { Fail "git pull failed — fix conflicts then re-run."; exit 1 }
Ok "Repo up to date"

# ── Step 2: Find device ───────────────────────────────────────────────────────
Step "2/5  Find device"
& $ADB kill-server | Out-Null
& $ADB start-server | Out-Null
$mdns = & $ADB mdns services 2>&1

$port = $null
foreach ($line in $mdns) {
    if ($line -match [regex]::Escape($PHONE_GUID)) {
        if ($line -match "$([regex]::Escape($PHONE_IP)):(\d+)") {
            $port = $Matches[1]; break
        }
    }
}

if ($port) {
    Ok "Found via mDNS: $PHONE_IP:$port"
} else {
    Warn "mDNS discovery failed."
    Warn "On your phone: Settings → Developer options → Wireless debugging"
    Warn "The IP:PORT is shown at the top of that screen."
    $input = Read-Host "  Enter IP:PORT"
    $parts = $input.Trim() -split ":"
    if ($parts.Count -ne 2) { Fail "Invalid format — expected IP:PORT"; exit 1 }
    $PHONE_IP = $parts[0]
    $port     = $parts[1]
}

$DEVICE = "$PHONE_IP:$port"

# ── Step 3: Connect ───────────────────────────────────────────────────────────
Step "3/5  Connect"
& $ADB connect $DEVICE | Out-Null
Start-Sleep -Milliseconds 800

$status = (& $ADB devices -l | Select-String $DEVICE)
if ($status -match "device\b" -and $status -notmatch "offline") {
    Ok "Connected: $DEVICE"
} else {
    Warn "Device offline — pairing required."
    Warn "On your phone: tap 'Pair device with pairing code' (shows a DIFFERENT port + 6-digit code)."
    $pairAddr = Read-Host "  Enter pairing IP:PORT"
    $pairCode = Read-Host "  Enter 6-digit code"
    & $ADB pair $pairAddr $pairCode
    & $ADB connect $DEVICE | Out-Null
    Start-Sleep -Milliseconds 800
    $check = & $ADB devices -l | Select-String $DEVICE
    if ($check -notmatch "device\b" -or $check -match "offline") {
        Fail "Still not connected. Check that Wireless debugging is enabled and the phone screen is on."
        exit 1
    }
    Ok "Connected after pairing: $DEVICE"
}

# ── Step 4: Build ─────────────────────────────────────────────────────────────
Step "4/5  Build APK"
Push-Location $ANDROID
& .\gradlew.bat assembleDebug
$buildOk = $LASTEXITCODE
Pop-Location

if ($buildOk -ne 0) { Fail "Build failed — see output above."; exit 1 }
if (-not (Test-Path $APK)) { Fail "APK not found at $APK"; exit 1 }
Ok "Build succeeded"

# ── Step 5: Install & verify ──────────────────────────────────────────────────
Step "5/5  Install & verify"
& $ADB -s $DEVICE install -r $APK
if ($LASTEXITCODE -ne 0) { Fail "Install failed."; exit 1 }
Ok "Installed"

Info "Launching MainActivity…"
& $ADB -s $DEVICE shell am start -n "$PACKAGE/.MainActivity" | Out-Null
Start-Sleep -Seconds 2

$crashes = & $ADB -s $DEVICE logcat -d -b crash 2>&1 | Select-String $PACKAGE
if ($crashes) {
    Fail "Crashes detected:"
    $crashes | ForEach-Object { Write-Host "    $_" -ForegroundColor Red }
} else {
    Ok "No crashes"
}

Info "Screenshot saved to deploy-screenshot.png"
& $ADB -s $DEVICE exec-out screencap -p | Set-Content -Path (Join-Path $REPO_DIR "deploy-screenshot.png") -Encoding Byte

Write-Host "`nDone! CashLog deployed to $DEVICE`n" -ForegroundColor Green
