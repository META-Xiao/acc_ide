param(
    [string]$AndroidNdkPath = "",
    [string[]]$Architectures = @("arm64-v8a", "armeabi-v7a", "x86_64", "x86"),
    [switch]$ShowArchMenu = $false
)

Write-Host "=== Complete Tree-sitter Library Builder ===" -ForegroundColor Green
Write-Host "Build all Tree-sitter libraries from source for Android" -ForegroundColor Cyan

if ($AndroidNdkPath -eq "" -or !(Test-Path $AndroidNdkPath)) {
    Write-Host "ERROR: Please specify Android NDK path!" -ForegroundColor Red
    Write-Host "Download from: https://developer.android.com/ndk/downloads" -ForegroundColor Yellow
    Write-Host "Usage: .\Build-Complete-TreeSitter.ps1 -AndroidNdkPath 'C:\android-ndk-r26d'" -ForegroundColor Yellow
    Write-Host "Optional: -Architectures @('arm64-v8a', 'x86_64')" -ForegroundColor Yellow
    exit 1
}

$NdkBuild = Join-Path $AndroidNdkPath "ndk-build.cmd"
if (!(Test-Path $NdkBuild)) {
    Write-Host "ERROR: ndk-build.cmd not found in: $AndroidNdkPath" -ForegroundColor Red
    exit 1
}

# Architecture selection menu
if ($ShowArchMenu) {
    Write-Host ""
    Write-Host "Select architectures to build:" -ForegroundColor Cyan
    Write-Host "1. arm64-v8a (64-bit ARM - Modern phones)" -ForegroundColor Yellow
    Write-Host "2. armeabi-v7a (32-bit ARM - Older devices)" -ForegroundColor Yellow
    Write-Host "3. x86_64 (64-bit x86 - Emulators)" -ForegroundColor Yellow
    Write-Host "4. x86 (32-bit x86 - Old emulators)" -ForegroundColor Yellow
    Write-Host "5. All architectures" -ForegroundColor Green
    
    $choice = Read-Host "Enter your choice (1-5)"
    switch ($choice) {
        "1" { $Architectures = @("arm64-v8a") }
        "2" { $Architectures = @("armeabi-v7a") }
        "3" { $Architectures = @("x86_64") }
        "4" { $Architectures = @("x86") }
        "5" { $Architectures = @("arm64-v8a", "armeabi-v7a", "x86_64", "x86") }
        default { 
            Write-Host "Invalid choice, using all architectures" -ForegroundColor Yellow
            $Architectures = @("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }
}

Write-Host "SUCCESS: Android NDK found at $AndroidNdkPath" -ForegroundColor Green
Write-Host "INFO: Building for architectures: $($Architectures -join ', ')" -ForegroundColor Green

# Create build directory
$BuildDir = "complete_build"
if (Test-Path $BuildDir) {
    Remove-Item $BuildDir -Recurse -Force
}
New-Item -ItemType Directory -Path $BuildDir -Force | Out-Null

Write-Host ""
Write-Host "INFO: Downloading Tree-sitter sources..." -ForegroundColor Cyan


$Sources = @{
    "tree-sitter" = "https://github.com/tree-sitter/tree-sitter/archive/refs/tags/v0.22.6.zip"
    "tree-sitter-java" = "https://github.com/tree-sitter/tree-sitter-java/archive/refs/heads/master.zip"
    "tree-sitter-cpp" = "https://github.com/tree-sitter/tree-sitter-cpp/archive/refs/heads/master.zip"
    "tree-sitter-python" = "https://github.com/tree-sitter/tree-sitter-python/archive/refs/heads/master.zip"
}

Set-Location $BuildDir

try {
    foreach ($source in $Sources.GetEnumerator()) {
        Write-Host "Downloading $($source.Key)..." -ForegroundColor Yellow
        $zipFile = "$($source.Key).zip"
        Invoke-WebRequest -Uri $source.Value -OutFile $zipFile -UseBasicParsing
        Expand-Archive -Path $zipFile -DestinationPath "." -Force
        Remove-Item $zipFile
    }
    
    if (Test-Path "tree-sitter-0.22.6") { Rename-Item "tree-sitter-0.22.6" "tree-sitter" }
    if (Test-Path "tree-sitter-java-master") { Rename-Item "tree-sitter-java-master" "tree-sitter-java" }
    if (Test-Path "tree-sitter-cpp-master") { Rename-Item "tree-sitter-cpp-master" "tree-sitter-cpp" }
    if (Test-Path "tree-sitter-python-master") { Rename-Item "tree-sitter-python-master" "tree-sitter-python" }
    
    Write-Host "SUCCESS: All sources downloaded and extracted" -ForegroundColor Green
    
    # Check source file structure and adjust paths
    $RequiredFiles = @(
        "tree-sitter/lib/src/lib.c",
        "tree-sitter-java/src/parser.c",
        "tree-sitter-cpp/src/parser.c",
        "tree-sitter-python/src/parser.c"
    )
    
    foreach ($file in $RequiredFiles) {
        if (!(Test-Path $file)) {
            throw "Required source file not found: $file"
        }
    }
    
    # Check for scanner files and determine correct names
    $CppScannerFile = ""
    if (Test-Path "tree-sitter-cpp/src/scanner.cc") {
        $CppScannerFile = "tree-sitter-cpp/src/scanner.cc"
    } elseif (Test-Path "tree-sitter-cpp/src/scanner.c") {
        $CppScannerFile = "tree-sitter-cpp/src/scanner.c"
    } elseif (Test-Path "tree-sitter-cpp/src/scanner.cpp") {
        $CppScannerFile = "tree-sitter-cpp/src/scanner.cpp"
    }
    
    $PythonScannerFile = ""
    if (Test-Path "tree-sitter-python/src/scanner.c") {
        $PythonScannerFile = "tree-sitter-python/src/scanner.c"
    } elseif (Test-Path "tree-sitter-python/src/scanner.cc") {
        $PythonScannerFile = "tree-sitter-python/src/scanner.cc"
    }
    
    Write-Host "SUCCESS: All required source files verified" -ForegroundColor Green
    if ($CppScannerFile -ne "") {
        Write-Host "INFO: C++ scanner file: $CppScannerFile" -ForegroundColor Cyan
    } else {
        Write-Host "INFO: C++ scanner file: Not found (parser only)" -ForegroundColor Yellow
    }
    if ($PythonScannerFile -ne "") {
        Write-Host "INFO: Python scanner file: $PythonScannerFile" -ForegroundColor Cyan
    } else {
        Write-Host "INFO: Python scanner file: Not found (parser only)" -ForegroundColor Yellow
    }
    
} catch {
    Write-Host "ERROR: Download failed: $($_.Exception.Message)" -ForegroundColor Red
    Set-Location ..
    exit 1
}

Write-Host ""
Write-Host "INFO: Creating build configuration..." -ForegroundColor Cyan

# Build C++ source files list
$CppSources = "tree-sitter-cpp/src/parser.c"
if ($CppScannerFile -ne "") {
    $CppSources += " $CppScannerFile"
}

# Build Python source files list  
$PythonSources = "tree-sitter-python/src/parser.c"
if ($PythonScannerFile -ne "") {
    $PythonSources += " $PythonScannerFile"
}

Write-Host "INFO: C++ sources: $CppSources" -ForegroundColor Cyan
Write-Host "INFO: Python sources: $PythonSources" -ForegroundColor Cyan

$AndroidMk = @"
LOCAL_PATH := `$(call my-dir)

# Tree-sitter core library
include `$(CLEAR_VARS)
LOCAL_MODULE := tree-sitter
LOCAL_SRC_FILES := tree-sitter/lib/src/lib.c
LOCAL_C_INCLUDES := tree-sitter/lib/include tree-sitter/lib/src
LOCAL_EXPORT_C_INCLUDES := tree-sitter/lib/include
LOCAL_CFLAGS := -std=c11 -O2 -DTREE_SITTER_HIDE_SYMBOLS
include `$(BUILD_SHARED_LIBRARY)

# Java grammar parser
include `$(CLEAR_VARS)
LOCAL_MODULE := tree-sitter-java
LOCAL_SRC_FILES := tree-sitter-java/src/parser.c
LOCAL_C_INCLUDES := tree-sitter/lib/include
LOCAL_SHARED_LIBRARIES := tree-sitter
LOCAL_CFLAGS := -std=c11 -O2
include `$(BUILD_SHARED_LIBRARY)

# C++ grammar parser
include `$(CLEAR_VARS)
LOCAL_MODULE := tree-sitter-cpp
LOCAL_SRC_FILES := $CppSources
LOCAL_C_INCLUDES := tree-sitter/lib/include
LOCAL_SHARED_LIBRARIES := tree-sitter
LOCAL_CFLAGS := -std=c11 -O2
LOCAL_CPPFLAGS := -std=c++11 -O2
include `$(BUILD_SHARED_LIBRARY)

# Python grammar parser
include `$(CLEAR_VARS)
LOCAL_MODULE := tree-sitter-python
LOCAL_SRC_FILES := $PythonSources
LOCAL_C_INCLUDES := tree-sitter/lib/include
LOCAL_SHARED_LIBRARIES := tree-sitter
LOCAL_CFLAGS := -std=c11 -O2
include `$(BUILD_SHARED_LIBRARY)
"@

Set-Content -Path "Android.mk" -Value $AndroidMk -Encoding UTF8

# Create Application.mk
$ArchString = $Architectures -join " "
$ApplicationMk = @"
APP_PLATFORM := android-21
APP_ABI := $ArchString
APP_STL := c++_shared
APP_CPPFLAGS := -frtti -fexceptions -std=c++11

# 16KB page size support (Android 15+)
APP_LDFLAGS := -Wl,-z,max-page-size=16384

# Optimization settings
APP_OPTIM := release
APP_CFLAGS := -O2 -DNDEBUG
"@

Set-Content -Path "Application.mk" -Value $ApplicationMk -Encoding UTF8

Write-Host "SUCCESS: Build configuration created" -ForegroundColor Green

# Start compilation
Write-Host ""
Write-Host "INFO: Starting compilation..." -ForegroundColor Cyan
Write-Host "INFO: This may take several minutes..." -ForegroundColor Yellow

$buildSuccess = $false
try {
    & $NdkBuild NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=Android.mk
    if ($LASTEXITCODE -eq 0) {
        $buildSuccess = $true
        Write-Host "SUCCESS: Compilation completed successfully!" -ForegroundColor Green
    } else {
        throw "NDK build failed with exit code $LASTEXITCODE"
    }
} catch {
    Write-Host "ERROR: Compilation failed: $($_.Exception.Message)" -ForegroundColor Red
}

if ($buildSuccess) {
    Write-Host ""
    Write-Host "INFO: Copying libraries to assets..." -ForegroundColor Cyan
    
    $OutputDir = "..\app\src\main\assets\native"
    $ObjDir = "obj\local"
    
    $AllLibs = @(
        "libtree-sitter.so",
        "libtree-sitter-java.so",
        "libtree-sitter-cpp.so", 
        "libtree-sitter-python.so"
    )
    
    $CopiedCount = 0
    $TotalSize = 0
    
    foreach ($arch in $Architectures) {
        $SourceDir = Join-Path $ObjDir $arch
        $TargetDir = Join-Path $OutputDir $arch
        
        if (Test-Path $SourceDir) {
            if (!(Test-Path $TargetDir)) {
                New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null
            }
            
            Write-Host "Processing $arch..." -ForegroundColor Yellow
            
            foreach ($lib in $AllLibs) {
                $sourceFile = Join-Path $SourceDir $lib
                $targetFile = Join-Path $TargetDir $lib
                
                if (Test-Path $sourceFile) {
                    Copy-Item $sourceFile $targetFile -Force
                    $size = (Get-Item $sourceFile).Length
                    $TotalSize += $size
                    Write-Host "  SUCCESS: $lib ($([math]::Round($size/1KB, 1)) KB)" -ForegroundColor Green
                    $CopiedCount++
                } else {
                    Write-Host "  WARNING: $lib (not built)" -ForegroundColor Yellow
                }
            }
        } else {
            Write-Host "ERROR: No build output for $arch" -ForegroundColor Red
        }
    }
    
    Write-Host ""
    Write-Host "SUCCESS: Build completed successfully!" -ForegroundColor Green
    Write-Host "Statistics:" -ForegroundColor Cyan
    Write-Host "  - Libraries copied: $CopiedCount" -ForegroundColor White
    Write-Host "  - Total size: $([math]::Round($TotalSize/1MB, 2)) MB" -ForegroundColor White
    Write-Host "  - Architectures: $($Architectures -join ', ')" -ForegroundColor White
    
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor Yellow
    Write-Host "1. cd .." -ForegroundColor White
    Write-Host "2. ./gradlew assembleDebug" -ForegroundColor White
    Write-Host "3. Test your app with Tree-sitter support!" -ForegroundColor White
    
    Write-Host ""
    Write-Host "NOTE: libandroid-tree-sitter.so was not built (using direct Tree-sitter integration)" -ForegroundColor Cyan
    Write-Host "Your NativeLibraryManager will need to load only the core libraries." -ForegroundColor Cyan
    
    # Cleanup option
    Write-Host ""
    $cleanup = Read-Host "Delete build directory to save space? [Y/n]"
    if ($cleanup -ne "n" -and $cleanup -ne "N") {
        Set-Location ..
        if (Test-Path $BuildDir) {
            Remove-Item $BuildDir -Recurse -Force
            Write-Host "SUCCESS: Build directory cleaned up" -ForegroundColor Green
        }
    } else {
        Set-Location ..
    }
    
} else {
    Set-Location ..
    Write-Host ""
    Write-Host "Troubleshooting tips:" -ForegroundColor Yellow
    Write-Host "1. Ensure Android NDK r26+ is installed" -ForegroundColor White
    Write-Host "2. Check internet connection for source downloads" -ForegroundColor White
    Write-Host "3. Verify sufficient disk space (>500MB)" -ForegroundColor White
    Write-Host "4. Try building single architecture first" -ForegroundColor White
    Write-Host "5. Check NDK version compatibility" -ForegroundColor White
    exit 1
} 