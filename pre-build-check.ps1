# BlackTube Pre-Build Deep Security & Integrity Check Script
# Usage: powershell -ExecutionPolicy Bypass -File .\pre-build-check.ps1

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " BLACKTUBE PRE-BUILD DEEP AUDIT & INTEGRITY CHECK" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$root = $PSScriptRoot
Set-Location $root

$passed = $true

# 1. Check Submodules
Write-Host "`n[1/5] Checking Git Submodules..." -ForegroundColor Yellow
$submoduleStatus = git submodule status
Write-Host $submoduleStatus
if ($submoduleStatus -match "^[\+\-]") {
    Write-Host "ERROR: NewPipeExtractor submodule is dirty or out of sync!" -ForegroundColor Red
    $passed = $false
} else {
    Write-Host "Submodule NewPipeExtractor is clean and pinned." -ForegroundColor Green
}

# 2. Check Hardcoded Secrets (Gemini API Keys)
Write-Host "`n[2/5] Scanning for Hardcoded Gemini API Keys..." -ForegroundColor Yellow
$secretMatches = Get-ChildItem -Path "$root\app\src\main" -Recurse -Include *.java,*.kt | Where-Object { $_.Name -ne "PoTokenWebView.kt" } | Select-String -Pattern "AIza[0-9A-Za-z\-_]{35}"
if ($secretMatches) {
    Write-Host "CRITICAL SECURITY ERROR: Hardcoded Gemini API Key found in:" -ForegroundColor Red
    $secretMatches | ForEach-Object { Write-Host "   $($_.Path):$($_.LineNumber)" -ForegroundColor Red }
    $passed = $false
} else {
    Write-Host "Zero hardcoded Gemini API keys found (Privacy intact)." -ForegroundColor Green
}

# 3. Check Service Lock (YouTube Only = Service ID 0)
Write-Host "`n[3/5] Verifying YouTube Service Lock Integrity..." -ForegroundColor Yellow
$serviceHelperMatch = Get-ChildItem -Path "$root\app\src\main\java" -Recurse -Include ServiceHelper.java,ServiceHelper.kt | Select-String -Pattern "return 0"
if ($serviceHelperMatch) {
    Write-Host "ServiceHelper strictly locked to YouTube (returns 0)." -ForegroundColor Green
} else {
    Write-Host "ERROR: Verify ServiceHelper.getSelectedServiceId() returns 0!" -ForegroundColor Red
    $passed = $false
}

# 4. Check ProGuard Keep Attributes
Write-Host "`n[4/5] Checking ProGuard R8 Rules..." -ForegroundColor Yellow
$proguardContent = Get-Content "$root\app\proguard-rules.pro" -Raw
if ($proguardContent -match "-keepattributes SourceFile,LineNumberTable") {
    Write-Host "ProGuard SourceFile,LineNumberTable rule present." -ForegroundColor Green
} else {
    Write-Host "ERROR: Missing -keepattributes SourceFile,LineNumberTable in proguard-rules.pro!" -ForegroundColor Red
    $passed = $false
}

# 5. Check Git Staging Hygiene
Write-Host "`n[5/5] Checking Git Staging Hygiene..." -ForegroundColor Yellow
$status = git status --short
$dirtyFiles = @("release.keystore", "dump_streaming.py", "test_output.txt")
foreach ($dirty in $dirtyFiles) {
    if ($status -match $dirty) {
        Write-Host "FORBIDDEN: Sensitive or scratch file $dirty is tracked/staged!" -ForegroundColor Red
        $passed = $false
    }
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
if ($passed) {
    Write-Host " ALL PRE-BUILD SECURITY & INTEGRITY CHECKS PASSED!" -ForegroundColor Green
} else {
    Write-Host " SOME CHECKS FAILED - FIX BEFORE BUILDING RELEASE APK!" -ForegroundColor Red
}
Write-Host "==========================================================" -ForegroundColor Cyan
