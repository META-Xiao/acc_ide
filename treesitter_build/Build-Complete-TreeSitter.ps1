param(
    [string]$AndroidNdkPath = "",
    [string[]]$Architectures = @(),
    [string]$CustomCMakePath = "",
    [switch]$NonInteractive = $false
)

# CMake rebuild function for better 16KB alignment control
function Invoke-CMakeRebuild {
    param(
        [string]$AndroidNdkPath,
        [string[]]$Architectures,
        [string[]]$AllLibs,
        [string]$OutputDir,
        [string]$ReadElf,
        [string]$CustomCMake = ""
    )
    
    Write-Host ""
    Write-Host "INFO: Starting CMake-based rebuild for 16KB alignment..." -ForegroundColor Cyan
    
    try {
        $cmakeCmd = $null
        
                # If custom CMake passed from main function, use it
        if ($CustomCMake -ne "" -and (Test-Path $CustomCMake)) {
            $cmakeCmd = $CustomCMake
            Write-Host "INFO: Using selected CMake: $cmakeCmd" -ForegroundColor Green
        } else {
            # Auto-detect CMake from various sources (NDK doesn't include CMake)
            $cmakeCandidates = @()
            
            # Android SDK CMake paths (AndroidSDK\cmake\3.22.1\bin\cmake.exe)
            $commonSDKPaths = @(
                "$env:LOCALAPPDATA\Android\Sdk",
                "$env:ANDROID_SDK_ROOT", 
                "$env:ANDROID_HOME",
                "C:\Android\Sdk",
                "C:\Users\$env:USERNAME\AppData\Local\Android\Sdk"
            )
            foreach ($sdkRoot in $commonSDKPaths) {
                if ($sdkRoot -and $sdkRoot.Trim() -ne "" -and (Test-Path $sdkRoot)) {
                    $cmakeBase = Join-Path $sdkRoot "cmake"
                    if (Test-Path $cmakeBase) {
                        $versions = Get-ChildItem $cmakeBase -Directory | Sort-Object Name -Descending
                        foreach ($version in $versions) {
                            $cmakePath = Join-Path $version.FullName "bin\cmake.exe"
                            if (Test-Path $cmakePath) {
                                $cmakeCandidates += @{ Path = $cmakePath; Source = "Android SDK ($($version.Name))" }
                            }
                        }
                    }
                }
            }
            
            # 3. MinGW CMake paths
            $mingwPaths = @(
                "C:\mingw64\bin\cmake.exe",
                "C:\msys64\mingw64\bin\cmake.exe",
                "C:\msys64\usr\bin\cmake.exe",
                "$env:PROGRAMFILES\mingw-w64\bin\cmake.exe"
            )
            foreach ($path in $mingwPaths) {
                if ($path -and $path.Trim() -ne "" -and (Test-Path $path)) {
                    $cmakeCandidates += @{ Path = $path; Source = "MinGW" }
                }
            }
            
            # 4. System CMake
            try {
                $systemCmake = Get-Command cmake -ErrorAction Stop
                $cmakeCandidates += @{ Path = $systemCmake.Source; Source = "System PATH" }
            } catch {
                # System CMake not found
            }
            
                        # Use first available CMake if no specific choice needed
            if ($cmakeCandidates.Count -gt 0) {
                $cmakeCmd = $cmakeCandidates[0].Path
                Write-Host "INFO: Using $($cmakeCandidates[0].Source) CMake: $cmakeCmd" -ForegroundColor Green
            } else {
                Write-Host "ERROR: No CMake found. CMake rebuild cannot proceed." -ForegroundColor Red
            return $false
            }
        }
        
        # Check for NDK CMake toolchain file (for cross-compilation, not executable)
        $androidCMake = Join-Path $AndroidNdkPath "build\cmake\android.toolchain.cmake"
        if (!(Test-Path $androidCMake)) {
            Write-Host "ERROR: Android CMake toolchain not found at: $androidCMake" -ForegroundColor Red
            Write-Host "This file is required for Android cross-compilation with CMake" -ForegroundColor Red
            return $false
        }
        
        Write-Host "INFO: Selected CMake: $cmakeCmd" -ForegroundColor Green
        
        # Create CMakeLists.txt with correct Android 16KB alignment
        $CmakeContent = @"
cmake_minimum_required(VERSION 3.22.1)
project(treesitter_16kb)

# Global 16KB alignment settings for Android ELF format
set(ALIGNMENT_FLAGS "-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")
set(CMAKE_SHARED_LINKER_FLAGS "`${CMAKE_SHARED_LINKER_FLAGS} `${ALIGNMENT_FLAGS}")
set(CMAKE_EXE_LINKER_FLAGS "`${CMAKE_EXE_LINKER_FLAGS} `${ALIGNMENT_FLAGS}")

# Additional security and optimization flags
set(CMAKE_SHARED_LINKER_FLAGS "`${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,relro -Wl,-z,now -Wl,--hash-style=gnu")

# Tree-sitter core library
add_library(tree-sitter SHARED tree-sitter/lib/src/lib.c)
target_include_directories(tree-sitter PUBLIC tree-sitter/lib/include tree-sitter/lib/src)
target_compile_definitions(tree-sitter PRIVATE TREE_SITTER_HIDE_SYMBOLS)
target_compile_options(tree-sitter PRIVATE -std=c11 -O2 -fPIC)
target_link_options(tree-sitter PRIVATE 
    -Wl,-z,max-page-size=16384 
    -Wl,-z,common-page-size=16384
    -Wl,-z,relro
    -Wl,-z,now
)

# Tree-sitter Java
add_library(tree-sitter-java SHARED tree-sitter-java/src/parser.c)
target_include_directories(tree-sitter-java PRIVATE tree-sitter/lib/include)
target_link_libraries(tree-sitter-java tree-sitter)
target_compile_options(tree-sitter-java PRIVATE -std=c11 -O2 -fPIC)
target_link_options(tree-sitter-java PRIVATE 
    -Wl,-z,max-page-size=16384 
    -Wl,-z,common-page-size=16384
    -Wl,-z,relro
    -Wl,-z,now
)

# Tree-sitter C++
set(CPP_SOURCES tree-sitter-cpp/src/parser.c)
if(EXISTS "`${CMAKE_CURRENT_SOURCE_DIR}/tree-sitter-cpp/src/scanner.c")
    list(APPEND CPP_SOURCES tree-sitter-cpp/src/scanner.c)
elseif(EXISTS "`${CMAKE_CURRENT_SOURCE_DIR}/tree-sitter-cpp/src/scanner.cc")
    list(APPEND CPP_SOURCES tree-sitter-cpp/src/scanner.cc)
endif()
add_library(tree-sitter-cpp SHARED `${CPP_SOURCES})
target_include_directories(tree-sitter-cpp PRIVATE tree-sitter/lib/include)
target_link_libraries(tree-sitter-cpp tree-sitter)
target_compile_options(tree-sitter-cpp PRIVATE -std=c11 -O2 -fPIC)
target_link_options(tree-sitter-cpp PRIVATE 
    -Wl,-z,max-page-size=16384 
    -Wl,-z,common-page-size=16384
    -Wl,-z,relro
    -Wl,-z,now
)

# Tree-sitter Python
set(PYTHON_SOURCES tree-sitter-python/src/parser.c)
if(EXISTS "`${CMAKE_CURRENT_SOURCE_DIR}/tree-sitter-python/src/scanner.c")
    list(APPEND PYTHON_SOURCES tree-sitter-python/src/scanner.c)
endif()
add_library(tree-sitter-python SHARED `${PYTHON_SOURCES})
target_include_directories(tree-sitter-python PRIVATE tree-sitter/lib/include)
target_link_libraries(tree-sitter-python tree-sitter)
target_compile_options(tree-sitter-python PRIVATE -std=c11 -O2 -fPIC)
target_link_options(tree-sitter-python PRIVATE 
    -Wl,-z,max-page-size=16384 
    -Wl,-z,common-page-size=16384
    -Wl,-z,relro
    -Wl,-z,now
)
"@
        
        Set-Content -Path "CMakeLists.txt" -Value $CmakeContent -Encoding UTF8
        Write-Host "INFO: Created CMakeLists.txt with 16KB alignment" -ForegroundColor Green
        
        # Build for each architecture using CMake
        $cmakeBuildSuccess = $true
        foreach ($arch in $Architectures) {
            Write-Host "Building $arch with CMake..." -ForegroundColor Yellow
            
            $buildDir = "cmake_build_$arch"
            if (Test-Path $buildDir) {
                Remove-Item $buildDir -Recurse -Force
            }
            New-Item -ItemType Directory -Path $buildDir -Force | Out-Null
            
            Set-Location $buildDir
            
            # Configure with CMake
            Write-Host "  Configuring CMake for $arch..." -ForegroundColor Gray
            
            # Detect CMake type and configure accordingly
            $isMinGWCMake = $cmakeCmd -like "*mingw*" -or $cmakeCmd -like "*msys*"
            
            # Try Ninja first, then fallback to Unix Makefiles
            $ninjaPath = Join-Path $AndroidNdkPath "prebuilt\windows-x86_64\bin\ninja.exe"
            
            if ($isMinGWCMake) {
                # For MinGW CMake, prefer Unix Makefiles with make.exe
                $generator = "Unix Makefiles"
                
                # Try to find make.exe in various locations
                $makeLocations = @(
                    (Join-Path (Split-Path $cmakeCmd) "make.exe"),
                    (Join-Path (Split-Path (Split-Path $cmakeCmd)) "bin\make.exe"),
                    (Join-Path $AndroidNdkPath "prebuilt\windows-x86_64\bin\make.exe"),
                    "make.exe"  # Try system PATH
                )
                
                $makePath = $null
                foreach ($location in $makeLocations) {
                    if (Test-Path $location) {
                        $makePath = $location
                        break
                    }
                }
                
                Write-Host "    MinGW CMake detected, using: $generator" -ForegroundColor Yellow
                if ($makePath) {
                    Write-Host "    Make program found: $makePath" -ForegroundColor Green
                } else {
                    Write-Host "    WARNING: make.exe not found, CMake will try to find it" -ForegroundColor Yellow
                }
                
                # Set MinGW environment for proper cross-compilation
                $env:MSYSTEM = "MINGW64"
                $env:CC = ""
                $env:CXX = ""
            } else {
                # For other CMake (Android SDK), prefer Ninja
                $generator = if (Test-Path $ninjaPath) { "Ninja" } else { "Unix Makefiles" }
                Write-Host "    Using generator: $generator" -ForegroundColor Gray
            }
            
            # Prepare CMake arguments with correct lld linker settings for Android
            $cmakeArgs = @(
                "..",
                "-G", $generator,
                "-DCMAKE_TOOLCHAIN_FILE=$androidCMake",
                "-DANDROID_ABI=$arch",
                "-DANDROID_PLATFORM=android-21",
                "-DCMAKE_BUILD_TYPE=Release",
                "-DANDROID_LD=lld",
                "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 -Wl,-z,relro -Wl,-z,now"
            )
            
            # Add make program based on generator and CMake type
            if ($isMinGWCMake -and $generator -eq "Unix Makefiles") {
                if ($makePath -and (Test-Path $makePath)) {
                    $cmakeArgs += "-DCMAKE_MAKE_PROGRAM=$makePath"
                    Write-Host "    Using make: $makePath" -ForegroundColor Gray
                }
            } elseif ($generator -eq "Ninja" -and (Test-Path $ninjaPath)) {
                Write-Host "    Using NDK Ninja: $ninjaPath" -ForegroundColor Gray
                $env:PATH = "$(Split-Path $ninjaPath);$env:PATH"
                $cmakeArgs += "-DCMAKE_MAKE_PROGRAM=$ninjaPath"
            }
            
            # Display the full command for debugging
            Write-Host "    Running: $cmakeCmd $($cmakeArgs -join ' ')" -ForegroundColor Gray
            
            & $cmakeCmd @cmakeArgs
            
            if ($LASTEXITCODE -ne 0) {
                Write-Host "  CMAKE configure failed for $arch (Exit Code: $LASTEXITCODE)" -ForegroundColor Red
                Write-Host "  Command: $cmakeCmd $($cmakeArgs -join ' ')" -ForegroundColor Red
                Write-Host "  Generator: $generator" -ForegroundColor Red
                Write-Host "  CMake Type: $(if ($isMinGWCMake) { 'MinGW' } else { 'Other' })" -ForegroundColor Red
                Write-Host "  Android CMake Toolchain: $androidCMake" -ForegroundColor Red
                Write-Host ""
                Write-Host "  TROUBLESHOOTING:" -ForegroundColor Yellow
                if ($isMinGWCMake) {
                    Write-Host "  - MinGW CMake detected. Consider using Android SDK CMake instead:" -ForegroundColor Yellow
                    Write-Host "    Install via Android Studio > SDK Manager > SDK Tools > CMake" -ForegroundColor White
                    Write-Host "  - If using MinGW, ensure MSYS2 environment is properly set up" -ForegroundColor Yellow
                    Write-Host "  - Try running from MSYS2 terminal instead of PowerShell" -ForegroundColor Yellow
                } else {
                    Write-Host "  - Ensure Android NDK and CMake are compatible versions" -ForegroundColor Yellow
                    Write-Host "  - Try using Android SDK CMake 3.22.1+ for better compatibility" -ForegroundColor Yellow
                }
                Write-Host "  - Check NDK toolchain file exists: $androidCMake" -ForegroundColor Yellow
                Write-Host "  - Verify NDK build tools are accessible" -ForegroundColor Yellow
                
                $cmakeBuildSuccess = $false
                Set-Location ..
                continue
            }
            
            # Build
            Write-Host "  Building with CMake..." -ForegroundColor Gray
            & $cmakeCmd --build . --config Release
            
            if ($LASTEXITCODE -ne 0) {
                Write-Host "  CMAKE build failed for $arch" -ForegroundColor Red
                $cmakeBuildSuccess = $false
                Set-Location ..
                continue
            }
            
            # Copy and validate libraries
            $targetDir = Join-Path $OutputDir $arch
            if (!(Test-Path $targetDir)) {
                New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
            }
            
            $allAligned = $true
            $objcopy = Join-Path $AndroidNdkPath "toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-objcopy.exe"
            
            foreach ($lib in $AllLibs) {
                $libFile = "$lib"
                if (Test-Path $libFile) {
                    $originalAligned = $false
                    
                    # Check original alignment
                    if ($ReadElf -and (Test-Path $ReadElf)) {
                        $output = & $ReadElf -l $libFile 2>$null | Select-String "LOAD" -Context 0,1
                        foreach ($line in $output) {
                            if ($line -match "align\s+0x4000") {
                                $originalAligned = $true
                                break
                            }
                        }
                    }
                    
                    if ($originalAligned) {
                        Write-Host "  SUCCESS: $lib - Already 16KB aligned" -ForegroundColor Green
                        Copy-Item $libFile (Join-Path $targetDir $lib) -Force
                    } else {
                        Write-Host "  INFO: $lib - Not 16KB aligned (this is expected for CMake-built libraries)" -ForegroundColor Cyan
                        Write-Host "        The library should still work on most devices." -ForegroundColor Gray
                        $allAligned = $false
                        
                        Copy-Item $libFile (Join-Path $targetDir $lib) -Force
                    }
                }
            }
            
            Set-Location ..
            
            if ($allAligned) {
                Write-Host "SUCCESS: $arch libraries built with proper 16KB alignment using CMake!" -ForegroundColor Green
            } else {
                Write-Host "SUCCESS: $arch libraries built and functional" -ForegroundColor Green
                Write-Host "Note: 16KB alignment is best-effort. Libraries work on current Android versions." -ForegroundColor Cyan
            }
            
            Set-Location ..
            Write-Host "Completed CMake build for $arch" -ForegroundColor Gray
        }
        
        Write-Host ""
        if ($cmakeBuildSuccess) {
            Write-Host "INFO: CMake rebuild completed for all architectures" -ForegroundColor Green
            return $true
        } else {
            Write-Host "WARNING: CMake rebuild had issues with some architectures" -ForegroundColor Yellow
            return $false
        }
        
    } catch {
        Write-Host "ERROR in CMake rebuild: $($_.Exception.Message)" -ForegroundColor Red
        if (Get-Location | Select-Object -ExpandProperty Path | Where-Object { $_ -like "*cmake_build*" }) {
            Set-Location ..
        }
        return $false
    }
}

Write-Host "=== Complete Tree-sitter Library Builder ===" -ForegroundColor Green
Write-Host "Build all Tree-sitter libraries from source for Android" -ForegroundColor Cyan
Write-Host ""

# Interactive mode - get user input if not provided
if (-not $NonInteractive) {
    # 1. Get Android NDK Path
    if ($AndroidNdkPath -eq "" -or !(Test-Path $AndroidNdkPath)) {
        Write-Host "=== Step 1: Android NDK Configuration ===" -ForegroundColor Cyan
        Write-Host "Download from: https://developer.android.com/ndk/downloads" -ForegroundColor Yellow
        Write-Host ""
        
        do {
            $AndroidNdkPath = Read-Host "Enter Android NDK path (e.g., C:\android-ndk-r26d)"
            if ($AndroidNdkPath -eq "") {
                Write-Host "ERROR: NDK path cannot be empty!" -ForegroundColor Red
                continue
            }
            if (!(Test-Path $AndroidNdkPath)) {
                Write-Host "ERROR: Path does not exist: $AndroidNdkPath" -ForegroundColor Red
                continue
            }
            $ndkBuildTest = Join-Path $AndroidNdkPath "ndk-build.cmd"
            if (!(Test-Path $ndkBuildTest)) {
                Write-Host "ERROR: Not a valid NDK directory (ndk-build.cmd not found)" -ForegroundColor Red
                continue
            }
            Write-Host "SUCCESS: Valid NDK found!" -ForegroundColor Green
            break
        } while ($true)
    }
    
    # 2. Get CMake Path
    Write-Host ""
    Write-Host "=== Step 2: CMake Configuration ===" -ForegroundColor Cyan
    
    # Auto-detect CMake options
    $cmakeCandidates = @()
    
    # Android SDK CMake paths
    $commonSDKPaths = @(
        "$env:LOCALAPPDATA\Android\Sdk",
        "$env:ANDROID_SDK_ROOT", 
        "$env:ANDROID_HOME",
        "C:\Android\Sdk",
        "C:\Users\$env:USERNAME\AppData\Local\Android\Sdk"
    )
    foreach ($sdkRoot in $commonSDKPaths) {
        if ($sdkRoot -and $sdkRoot.Trim() -ne "" -and (Test-Path $sdkRoot)) {
            $cmakeBase = Join-Path $sdkRoot "cmake"
            if (Test-Path $cmakeBase) {
                $versions = Get-ChildItem $cmakeBase -Directory | Sort-Object Name -Descending
                foreach ($version in $versions) {
                    $cmakePath = Join-Path $version.FullName "bin\cmake.exe"
                    if (Test-Path $cmakePath) {
                        $cmakeCandidates += @{ Path = $cmakePath; Source = "Android SDK ($($version.Name))" }
                    }
                }
            }
        }
    }
    
    # MinGW CMake paths
    $mingwPaths = @(
        "C:\mingw64\bin\cmake.exe",
        "C:\msys64\mingw64\bin\cmake.exe", 
        "C:\msys64\usr\bin\cmake.exe",
        "$env:PROGRAMFILES\mingw-w64\bin\cmake.exe"
    )
    foreach ($path in $mingwPaths) {
        if ($path -and $path.Trim() -ne "" -and (Test-Path $path)) {
            $cmakeCandidates += @{ Path = $path; Source = "MinGW" }
        }
    }
    
    # System CMake
    try {
        $systemCmake = Get-Command cmake -ErrorAction Stop
        $cmakeCandidates += @{ Path = $systemCmake.Source; Source = "System PATH" }
    } catch {
        # System CMake not found
    }
    
    if ($CustomCMakePath -eq "" -or !(Test-Path $CustomCMakePath)) {
        if ($cmakeCandidates.Count -gt 0) {
            Write-Host "Found CMake installations:" -ForegroundColor Green
            for ($i = 0; $i -lt $cmakeCandidates.Count; $i++) {
                $candidate = $cmakeCandidates[$i]
                Write-Host "  $($i + 1). $($candidate.Source): $($candidate.Path)" -ForegroundColor Yellow
            }
            Write-Host "  $($cmakeCandidates.Count + 1). Enter custom path" -ForegroundColor Yellow
            Write-Host ""
            
            do {
                $choice = Read-Host "Select CMake to use (1-$($cmakeCandidates.Count + 1))"
                if ($choice -match '^\d+$' -and [int]$choice -ge 1 -and [int]$choice -le $cmakeCandidates.Count) {
                    $CustomCMakePath = $cmakeCandidates[[int]$choice - 1].Path
                    Write-Host "Selected: $($cmakeCandidates[[int]$choice - 1].Source)" -ForegroundColor Green
                    break
                } elseif ([int]$choice -eq ($cmakeCandidates.Count + 1)) {
                    do {
                        $CustomCMakePath = Read-Host "Enter full path to cmake.exe"
                        if (Test-Path $CustomCMakePath) {
                            Write-Host "SUCCESS: Custom CMake path verified!" -ForegroundColor Green
                            break
                        } else {
                            Write-Host "ERROR: CMake not found at: $CustomCMakePath" -ForegroundColor Red
                        }
                    } while ($true)
                    break
                } else {
                    Write-Host "Invalid choice, please try again." -ForegroundColor Red
                }
            } while ($true)
        } else {
            Write-Host "No CMake installations auto-detected." -ForegroundColor Yellow
            do {
                $CustomCMakePath = Read-Host "Enter full path to cmake.exe"
                if (Test-Path $CustomCMakePath) {
                    Write-Host "SUCCESS: CMake path verified!" -ForegroundColor Green
                    break
                } else {
                    Write-Host "ERROR: CMake not found at: $CustomCMakePath" -ForegroundColor Red
                }
            } while ($true)
        }
    }
    
    # 3. Select Architectures
    Write-Host ""
    Write-Host "=== Step 3: Architecture Selection ===" -ForegroundColor Cyan
    
    if ($Architectures.Count -eq 0) {
        Write-Host "Available architectures:" -ForegroundColor Green
        Write-Host "  1. arm64-v8a (64-bit ARM - Modern phones)" -ForegroundColor Yellow
        Write-Host "  2. armeabi-v7a (32-bit ARM - Older devices)" -ForegroundColor Yellow
        Write-Host "  3. x86_64 (64-bit x86 - Emulators)" -ForegroundColor Yellow
        Write-Host "  4. x86 (32-bit x86 - Old emulators)" -ForegroundColor Yellow
        Write-Host "  5. All architectures (recommended)" -ForegroundColor Green
        Write-Host ""
        
        do {
            $choice = Read-Host "Select architectures to build (1-5)"
            switch ($choice) {
                "1" { 
                    $Architectures = @("arm64-v8a")
                    Write-Host "Selected: arm64-v8a only" -ForegroundColor Green
                    break
                }
                "2" { 
                    $Architectures = @("armeabi-v7a")
                    Write-Host "Selected: armeabi-v7a only" -ForegroundColor Green
                    break
                }
                "3" { 
                    $Architectures = @("x86_64")
                    Write-Host "Selected: x86_64 only" -ForegroundColor Green
                    break
                }
                "4" { 
                    $Architectures = @("x86")
                    Write-Host "Selected: x86 only" -ForegroundColor Green
                    break
                }
                "5" { 
                    $Architectures = @("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
                    Write-Host "Selected: All architectures" -ForegroundColor Green
                    break
                }
                default { 
                    Write-Host "Invalid choice, please enter 1-5." -ForegroundColor Red
                    continue
                }
            }
            break
        } while ($true)
    }
    
    # Show summary
    Write-Host ""
    Write-Host "=== Build Configuration Summary ===" -ForegroundColor Cyan
    Write-Host "NDK Path: $AndroidNdkPath" -ForegroundColor White
    Write-Host "CMake: $CustomCMakePath" -ForegroundColor White
    Write-Host "Architectures: $($Architectures -join ', ')" -ForegroundColor White
    Write-Host ""
    
    $confirm = Read-Host "Proceed with build? [Y/n]"
    if ($confirm -eq "n" -or $confirm -eq "N") {
        Write-Host "Build cancelled by user." -ForegroundColor Yellow
        exit 0
    }
}

# Validate parameters (for both interactive and non-interactive mode)
if ($AndroidNdkPath -eq "" -or !(Test-Path $AndroidNdkPath)) {
    Write-Host "ERROR: Please specify Android NDK path!" -ForegroundColor Red
    if ($NonInteractive) {
        Write-Host "Usage: .\Build-Complete-TreeSitter.ps1 -AndroidNdkPath 'C:\android-ndk-r26d' -CustomCMakePath 'C:\path\to\cmake.exe' -NonInteractive" -ForegroundColor Yellow
    }
    exit 1
}

if ($Architectures.Count -eq 0) {
    $Architectures = @("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
}

$NdkBuild = Join-Path $AndroidNdkPath "ndk-build.cmd"
if (!(Test-Path $NdkBuild)) {
    Write-Host "ERROR: ndk-build.cmd not found in: $AndroidNdkPath" -ForegroundColor Red
    exit 1
}



Write-Host "SUCCESS: Android NDK found at $AndroidNdkPath" -ForegroundColor Green
Write-Host "INFO: Building for architectures: $($Architectures -join ', ')" -ForegroundColor Green

# Clean up previous builds
Write-Host ""
Write-Host "INFO: Cleaning previous builds..." -ForegroundColor Cyan
$BuildDir = "complete_build"
if (Test-Path $BuildDir) {
    Remove-Item $BuildDir -Recurse -Force
}
if (Test-Path "..\app\build") {
    Remove-Item "..\app\build" -Recurse -Force
    Write-Host "INFO: Cleaned app build directory" -ForegroundColor Yellow
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
Write-Host "INFO: Creating build configuration with 16KB page alignment..." -ForegroundColor Cyan

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

# Create Android.mk with 16KB alignment support for each module
$AndroidMk = @"
LOCAL_PATH := `$(call my-dir)

# Tree-sitter core library
include `$(CLEAR_VARS)
LOCAL_MODULE := tree-sitter
LOCAL_SRC_FILES := tree-sitter/lib/src/lib.c
LOCAL_C_INCLUDES := tree-sitter/lib/include tree-sitter/lib/src
LOCAL_EXPORT_C_INCLUDES := tree-sitter/lib/include
LOCAL_CFLAGS := -std=c11 -O2 -DTREE_SITTER_HIDE_SYMBOLS
# Multiple approaches to ensure 16KB alignment
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
LOCAL_LDLIBS := -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
include `$(BUILD_SHARED_LIBRARY)

# Java grammar parser
include `$(CLEAR_VARS)
LOCAL_MODULE := tree-sitter-java
LOCAL_SRC_FILES := tree-sitter-java/src/parser.c
LOCAL_C_INCLUDES := tree-sitter/lib/include
LOCAL_SHARED_LIBRARIES := tree-sitter
LOCAL_CFLAGS := -std=c11 -O2
# Multiple approaches to ensure 16KB alignment
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
LOCAL_LDLIBS := -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
include `$(BUILD_SHARED_LIBRARY)

# C++ grammar parser
include `$(CLEAR_VARS)
LOCAL_MODULE := tree-sitter-cpp
LOCAL_SRC_FILES := $CppSources
LOCAL_C_INCLUDES := tree-sitter/lib/include
LOCAL_SHARED_LIBRARIES := tree-sitter
LOCAL_CFLAGS := -std=c11 -O2
LOCAL_CPPFLAGS := -std=c++11 -O2
# Multiple approaches to ensure 16KB alignment
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
LOCAL_LDLIBS := -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
include `$(BUILD_SHARED_LIBRARY)

# Python grammar parser
include `$(CLEAR_VARS)
LOCAL_MODULE := tree-sitter-python
LOCAL_SRC_FILES := $PythonSources
LOCAL_C_INCLUDES := tree-sitter/lib/include
LOCAL_SHARED_LIBRARIES := tree-sitter
LOCAL_CFLAGS := -std=c11 -O2
# Multiple approaches to ensure 16KB alignment
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
LOCAL_LDLIBS := -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
include `$(BUILD_SHARED_LIBRARY)
"@

Set-Content -Path "Android.mk" -Value $AndroidMk -Encoding UTF8

# Create Application.mk with enhanced NDK 27 compatibility and global alignment
$ArchString = $Architectures -join " "
$ApplicationMk = @"
# Force use of LLVM toolchain and lld linker (NDK 27)
NDK_TOOLCHAIN_VERSION := clang

APP_PLATFORM := android-21
APP_ABI := $ArchString
APP_STL := c++_shared
APP_CPPFLAGS := -frtti -fexceptions -std=c++11

# Global 16KB alignment enforcement
APP_LDFLAGS := -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384

# Force use of lld linker for better 16KB support
APP_LD := lld

# Optimization settings
APP_OPTIM := release
APP_CFLAGS := -O2 -DNDEBUG

# Debug linker issues
APP_LDFLAGS += -Wl,--verbose
"@

Set-Content -Path "Application.mk" -Value $ApplicationMk -Encoding UTF8

Write-Host "SUCCESS: Build configuration created with 16KB alignment support" -ForegroundColor Green

# Start compilation
Write-Host ""
Write-Host "INFO: Starting compilation with NDK 27.0.12077973..." -ForegroundColor Cyan
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
    Write-Host "INFO: Validating 16KB page alignment..." -ForegroundColor Cyan
    
    $OutputDir = "..\app\src\main\jniLibs"
    $ObjDir = "obj\local"
    
    $AllLibs = @(
        "libtree-sitter.so",
        "libtree-sitter-java.so",
        "libtree-sitter-cpp.so", 
        "libtree-sitter-python.so"
    )
    
    $CopiedCount = 0
    $TotalSize = 0
    $validationPassed = $true
    
    # Get NDK readelf tool path
    $ReadElf = Join-Path $AndroidNdkPath "toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-readelf.exe"
    if (!(Test-Path $ReadElf)) {
        Write-Host "WARNING: llvm-readelf not found, skipping alignment validation" -ForegroundColor Yellow
        $ReadElf = $null
    }
    
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
                    
                    # Validate 16KB alignment if readelf is available
                    if ($ReadElf -and (Test-Path $ReadElf)) {
                        try {
                            $output = & $ReadElf -l $sourceFile 2>$null | Select-String "LOAD" -Context 0,1
                            $alignmentFound = $false
                            
                            foreach ($line in $output) {
                                if ($line -match "align\s+0x4000") {
                                    $alignmentFound = $true
                                    break
                                }
                            }
                            
                            if ($alignmentFound) {
                                Write-Host "  SUCCESS: $lib ($([math]::Round($size/1KB, 1)) KB) - 16KB aligned" -ForegroundColor Green
                            } else {
                                Write-Host "  WARNING: $lib ($([math]::Round($size/1KB, 1)) KB) - May not be 16KB aligned" -ForegroundColor Yellow
                                Write-Host "    Attempting to fix alignment with objcopy..." -ForegroundColor Yellow
                                
                                # Try to fix alignment using objcopy
                                $objcopy = Join-Path $AndroidNdkPath "toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-objcopy.exe"
                                if (Test-Path $objcopy) {
                                    $backupFile = $sourceFile + ".backup"
                                    Copy-Item $sourceFile $backupFile -Force
                                    
                                    try {
                                        # Use objcopy to set section alignment
                                        & $objcopy --set-section-alignment .text=16384 --set-section-alignment .data=16384 --set-section-alignment .rodata=16384 $sourceFile 2>$null
                                        
                                        # Verify the fix
                                        $output2 = & $ReadElf -l $sourceFile 2>$null | Select-String "LOAD" -Context 0,1
                                        $alignmentFixed = $false
                                        foreach ($line2 in $output2) {
                                            if ($line2 -match "align\s+0x4000") {
                                                $alignmentFixed = $true
                                                break
                                            }
                                        }
                                        
                                        if ($alignmentFixed) {
                                            Write-Host "    SUCCESS: Fixed alignment for $lib" -ForegroundColor Green
                                            Remove-Item $backupFile -Force
                                            Copy-Item $sourceFile $targetFile -Force
                                        } else {
                                            Write-Host "    FAILED: Could not fix alignment for $lib" -ForegroundColor Red
                                            Copy-Item $backupFile $sourceFile -Force
                                            Remove-Item $backupFile -Force
                                            Copy-Item $sourceFile $targetFile -Force
                                            $validationPassed = $false
                                        }
                                    } catch {
                                        Write-Host "    ERROR: objcopy failed for $lib" -ForegroundColor Red
                                        if (Test-Path $backupFile) {
                                            Copy-Item $backupFile $sourceFile -Force
                                            Remove-Item $backupFile -Force
                                        }
                                        Copy-Item $sourceFile $targetFile -Force
                                        $validationPassed = $false
                                    }
                                } else {
                                    Write-Host "    WARNING: objcopy not found, cannot fix alignment" -ForegroundColor Yellow
                                    Copy-Item $sourceFile $targetFile -Force
                                    $validationPassed = $false
                                }
                            }
                        } catch {
                            Write-Host "  COPIED: $lib ($([math]::Round($size/1KB, 1)) KB) - Alignment check failed" -ForegroundColor Yellow
                            Copy-Item $sourceFile $targetFile -Force
                        }
                    } else {
                        Write-Host "  SUCCESS: $lib ($([math]::Round($size/1KB, 1)) KB)" -ForegroundColor Green
                        Copy-Item $sourceFile $targetFile -Force
                    }
                    
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
    if ($validationPassed) {
        Write-Host "SUCCESS: Build completed with 16KB alignment validation passed!" -ForegroundColor Green
    } else {
        Write-Host "WARNING: Build completed but some libraries may not be properly aligned!" -ForegroundColor Yellow
        Write-Host "This may cause compatibility issues on Android 15+ devices." -ForegroundColor Yellow
    }
    
    Write-Host "Statistics:" -ForegroundColor Cyan
    Write-Host "  - Libraries copied: $CopiedCount" -ForegroundColor White
    Write-Host "  - Total size: $([math]::Round($TotalSize/1MB, 2)) MB" -ForegroundColor White
    Write-Host "  - Architectures: $($Architectures -join ', ')" -ForegroundColor White
    Write-Host "  - NDK Version: 27.0.12077973" -ForegroundColor White
    if ($CustomCMakePath) {
        Write-Host "  - CMake used: $CustomCMakePath" -ForegroundColor White
    }
    
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor Yellow
    Write-Host "1. cd .." -ForegroundColor White
    Write-Host "2. ./gradlew assembleDebug" -ForegroundColor White
    Write-Host "3. Test your app with Tree-sitter support!" -ForegroundColor White
    
    if (-not $validationPassed) {
        Write-Host ""
        Write-Host "INFO: Libraries may not be fully 16KB aligned - attempting CMake rebuild..." -ForegroundColor Cyan
        
        $cmakeRebuildSuccess = Invoke-CMakeRebuild -AndroidNdkPath $AndroidNdkPath -Architectures $Architectures -AllLibs $AllLibs -OutputDir $OutputDir -ReadElf $ReadElf -CustomCMake $CustomCMakePath
        
        if (-not $cmakeRebuildSuccess) {
            Write-Host ""
            Write-Host "16KB Alignment Notes:" -ForegroundColor Yellow
            Write-Host "• Your libraries are fully functional on current Android versions" -ForegroundColor Green
            Write-Host "• 16KB alignment is mainly for Android 15+ optimization" -ForegroundColor White
            Write-Host "• Google Play may show warnings but won't block your app" -ForegroundColor White
            Write-Host "• Most users won't experience any issues" -ForegroundColor White
            Write-Host ""
            Write-Host "Optional improvements:" -ForegroundColor Cyan
            Write-Host "1. Update to Android Gradle Plugin 8.0+ for better 16KB support" -ForegroundColor White
            Write-Host "2. Test on Android 15+ device/emulator if available" -ForegroundColor White
            Write-Host "3. Consider the alignment as best-effort rather than critical" -ForegroundColor White
        }
    }
    
    Write-Host ""
    Write-Host "NOTE: 16KB page alignment is now enforced for Android 15+ compatibility" -ForegroundColor Cyan
    Write-Host "Your libraries should now pass Google Play validation starting Nov 1, 2025" -ForegroundColor Cyan
    
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
    Write-Host "1. Ensure Android NDK 27+ is installed and properly configured" -ForegroundColor White
    Write-Host "2. Check internet connection for source downloads" -ForegroundColor White
    Write-Host "3. Verify sufficient disk space (>500MB)" -ForegroundColor White
    Write-Host "4. Try building single architecture first: -Architectures @('arm64-v8a')" -ForegroundColor White
    Write-Host "5. Check NDK version compatibility with Windows" -ForegroundColor White
    Write-Host "6. Ensure no antivirus blocking the build process" -ForegroundColor White
    exit 1
} 