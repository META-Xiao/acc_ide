# Android编译器工具链构建脚本
# 类似于tree-sitter-build，为Android构建原生编译器

param(
    [string]$Target = "all",
    [string]$AndroidNdk = "",
    [string]$OutputDir = "app/src/main/assets/toolchain"
)

$ErrorActionPreference = "Stop"

# 检查NDK路径
if ([string]::IsNullOrEmpty($AndroidNdk)) {
    $AndroidNdk = $env:ANDROID_NDK_ROOT
    if ([string]::IsNullOrEmpty($AndroidNdk)) {
        $AndroidNdk = $env:ANDROID_HOME + "\ndk-bundle"
        if (!(Test-Path $AndroidNdk)) {
            Write-Error "Android NDK not found. Please set ANDROID_NDK_ROOT environment variable."
        }
    }
}

Write-Host "Using Android NDK: $AndroidNdk" -ForegroundColor Green

# 设置构建参数
$API_LEVEL = "21"  # Android 5.0+
$ARCH = "aarch64"  # ARM64
$TARGET_TRIPLE = "$ARCH-linux-android$API_LEVEL"
$TOOLCHAIN_DIR = "$AndroidNdk/toolchains/llvm/prebuilt/windows-x86_64"

# 创建输出目录
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
New-Item -ItemType Directory -Force -Path "$OutputDir/bin" | Out-Null
New-Item -ItemType Directory -Force -Path "$OutputDir/lib" | Out-Null
New-Item -ItemType Directory -Force -Path "$OutputDir/include" | Out-Null

function Build-ClangToolchain {
    Write-Host "Building Clang toolchain for Android..." -ForegroundColor Yellow
    
    # 复制clang相关文件
    $CLANG_SRC = "$TOOLCHAIN_DIR/bin/clang"
    $CLANGPP_SRC = "$TOOLCHAIN_DIR/bin/clang++"
    
    if (Test-Path $CLANG_SRC) {
        # 创建包装脚本而不是直接复制二进制文件
        @"
#!/system/bin/sh
export TMPDIR=/data/data/com.acc_ide/cache
exec $TOOLCHAIN_DIR/bin/$TARGET_TRIPLE-clang `$@
"@ | Out-File -FilePath "$OutputDir/bin/clang" -Encoding ASCII
        
        @"
#!/system/bin/sh
export TMPDIR=/data/data/com.acc_ide/cache
exec $TOOLCHAIN_DIR/bin/$TARGET_TRIPLE-clang++ `$@
"@ | Out-File -FilePath "$OutputDir/bin/clang++" -Encoding ASCII
        
        Write-Host "✓ Clang wrapper scripts created" -ForegroundColor Green
    } else {
        Write-Warning "Clang not found in NDK"
    }
    
    # 复制标准库头文件
    $SYSROOT = "$TOOLCHAIN_DIR/sysroot"
    if (Test-Path $SYSROOT) {
        Copy-Item -Recurse "$SYSROOT/usr/include" "$OutputDir/" -Force
        Write-Host "✓ Standard headers copied" -ForegroundColor Green
    }
}

function Build-TinyCC {
    Write-Host "Building TinyCC for Android..." -ForegroundColor Yellow
    
    $TCC_VERSION = "0.9.27"
    $TCC_URL = "http://download.savannah.nongnu.org/releases/tinycc/tcc-$TCC_VERSION.tar.bz2"
    $TCC_DIR = "build/tcc-$TCC_VERSION"
    
    # 下载TinyCC源码
    if (!(Test-Path $TCC_DIR)) {
        Write-Host "Downloading TinyCC..." -ForegroundColor Cyan
        New-Item -ItemType Directory -Force -Path "build" | Out-Null
        
        # 下载并解压
        Invoke-WebRequest -Uri $TCC_URL -OutFile "build/tcc-$TCC_VERSION.tar.bz2"
        
        # 解压 (需要7zip或其他工具)
        if (Get-Command "7z" -ErrorAction SilentlyContinue) {
            & 7z x "build/tcc-$TCC_VERSION.tar.bz2" -o"build"
            & 7z x "build/tcc-$TCC_VERSION.tar" -o"build"
        } else {
            Write-Warning "7zip not found. Please extract tcc-$TCC_VERSION.tar.bz2 manually to build/"
            return
        }
    }
    
    # 配置交叉编译
    Push-Location $TCC_DIR
    
    try {
        # 设置交叉编译环境
        $env:CC = "$TOOLCHAIN_DIR/bin/$TARGET_TRIPLE-clang"
        $env:CXX = "$TOOLCHAIN_DIR/bin/$TARGET_TRIPLE-clang++"
        $env:AR = "$TOOLCHAIN_DIR/bin/llvm-ar"
        $env:STRIP = "$TOOLCHAIN_DIR/bin/llvm-strip"
        
        # 配置
        & ./configure --cross-prefix="$TARGET_TRIPLE-" --cpu=aarch64 --prefix="$OutputDir"
        
        # 编译
        & make
        & make install
        
        Write-Host "✓ TinyCC built successfully" -ForegroundColor Green
    }
    catch {
        Write-Error "Failed to build TinyCC: $_"
    }
    finally {
        Pop-Location
    }
}

function Build-Python {
    Write-Host "Building Python for Android..." -ForegroundColor Yellow
    
    $PYTHON_VERSION = "3.11.0"
    $PYTHON_URL = "https://www.python.org/ftp/python/$PYTHON_VERSION/Python-$PYTHON_VERSION.tgz"
    $PYTHON_DIR = "build/Python-$PYTHON_VERSION"
    
    # 实际上，对于Python，推荐使用已有的解决方案
    Write-Host "For Python, consider using:" -ForegroundColor Cyan
    Write-Host "1. Chaquopy (https://chaquo.com/chaquopy/)" -ForegroundColor White
    Write-Host "2. QPython assets" -ForegroundColor White
    Write-Host "3. Termux Python packages" -ForegroundColor White
    
    # 这里可以集成现有的Python for Android构建
    # 或者从Termux仓库下载预编译的Python
    Download-TermuxPython
}

function Download-TermuxPython {
    Write-Host "Downloading Termux Python..." -ForegroundColor Cyan
    
    # Termux的APT仓库有预编译的ARM64 Python
    $TERMUX_REPO = "https://packages-cf.termux.org/apt/termux-main"
    $PYTHON_PKG = "python_3.11.0_aarch64.deb"
    
    try {
        # 下载Python deb包
        Invoke-WebRequest -Uri "$TERMUX_REPO/$PYTHON_PKG" -OutFile "build/$PYTHON_PKG"
        
        # 解压deb包 (需要dpkg-deb或ar工具)
        if (Get-Command "ar" -ErrorAction SilentlyContinue) {
            Push-Location "build"
            & ar x $PYTHON_PKG
            
            # 解压data.tar.xz
            if (Test-Path "data.tar.xz") {
                & 7z x "data.tar.xz"
                & 7z x "data.tar"
                
                # 复制Python文件
                if (Test-Path "data/data/com.termux/files/usr/bin/python3") {
                    Copy-Item -Recurse "data/data/com.termux/files/usr/*" "$OutputDir/"
                    Write-Host "✓ Termux Python installed" -ForegroundColor Green
                }
            }
            Pop-Location
        }
    }
    catch {
        Write-Warning "Failed to download Termux Python: $_"
        Write-Host "You can manually download from: https://github.com/termux/termux-packages" -ForegroundColor Yellow
    }
}

function Build-JavaTools {
    Write-Host "Setting up Java compilation tools..." -ForegroundColor Yellow
    
    # 对于Java，我们主要需要dx工具来转换.class到.dex
    $BUILD_TOOLS_VERSION = "33.0.2"  # 或最新版本
    $BUILD_TOOLS_DIR = "$env:ANDROID_HOME/build-tools/$BUILD_TOOLS_VERSION"
    
    if (Test-Path "$BUILD_TOOLS_DIR/dx") {
        # 创建dx的包装脚本
        @"
#!/system/bin/sh
# Java to DEX compiler wrapper
export ANDROID_HOME=$env:ANDROID_HOME
export JAVA_HOME=$env:JAVA_HOME
exec $BUILD_TOOLS_DIR/dx `$@
"@ | Out-File -FilePath "$OutputDir/bin/dx" -Encoding ASCII
        
        Write-Host "✓ Java tools configured" -ForegroundColor Green
    } else {
        Write-Warning "Android build tools not found"
    }
    
    # 复制Android运行时的核心类
    $ANDROID_JAR = "$env:ANDROID_HOME/platforms/android-33/android.jar"
    if (Test-Path $ANDROID_JAR) {
        Copy-Item $ANDROID_JAR "$OutputDir/lib/android.jar"
        Write-Host "✓ Android runtime copied" -ForegroundColor Green
    }
}

function Create-CompilerWrapper {
    Write-Host "Creating compiler wrapper scripts..." -ForegroundColor Yellow
    
    # 创建统一的编译器入口脚本
    @"
#!/system/bin/sh
# ACC IDE Compiler Wrapper
# Usage: acc-compile <lang> <source> [output]

LANG=`$1
SOURCE=`$2
OUTPUT=`$3

TOOLCHAIN_DIR="/data/data/com.acc_ide/files/toolchain"
export PATH="`$TOOLCHAIN_DIR/bin:`$PATH"
export LD_LIBRARY_PATH="`$TOOLCHAIN_DIR/lib:`$LD_LIBRARY_PATH"

case `$LANG in
    "c")
        exec clang -o "`$OUTPUT" "`$SOURCE" -I`$TOOLCHAIN_DIR/include
        ;;
    "cpp"|"cxx")
        exec clang++ -o "`$OUTPUT" "`$SOURCE" -I`$TOOLCHAIN_DIR/include -lstdc++
        ;;
    "java")
        # Compile to .class then convert to .dex
        javac -cp `$TOOLCHAIN_DIR/lib/android.jar -d /tmp "`$SOURCE"
        dx --dex --output="`$OUTPUT" /tmp
        ;;
    "python"|"py")
        exec python3 "`$SOURCE"
        ;;
    *)
        echo "Unsupported language: `$LANG"
        exit 1
        ;;
esac
"@ | Out-File -FilePath "$OutputDir/bin/acc-compile" -Encoding ASCII
    
    Write-Host "✓ Compiler wrapper created" -ForegroundColor Green
}

function Package-Toolchain {
    Write-Host "Packaging toolchain..." -ForegroundColor Yellow
    
    # 创建版本信息
    @{
        version = "1.0.0"
        arch = $ARCH
        api_level = $API_LEVEL
        build_date = (Get-Date).ToString("yyyy-MM-dd")
        components = @{
            clang = Test-Path "$OutputDir/bin/clang"
            tinycc = Test-Path "$OutputDir/bin/tcc"
            python = Test-Path "$OutputDir/bin/python3"
            java_tools = Test-Path "$OutputDir/bin/dx"
        }
    } | ConvertTo-Json | Out-File -FilePath "$OutputDir/toolchain.json" -Encoding UTF8
    
    # 设置权限信息文件
    @"
# Files that need execute permissions
bin/clang
bin/clang++
bin/tcc
bin/python3
bin/dx
bin/acc-compile
"@ | Out-File -FilePath "$OutputDir/executables.list" -Encoding ASCII
    
    Write-Host "✓ Toolchain packaged in $OutputDir" -ForegroundColor Green
}

# 主构建流程
Write-Host "=== Android Compiler Toolchain Build Script ===" -ForegroundColor Magenta

switch ($Target.ToLower()) {
    "clang" { Build-ClangToolchain }
    "tinycc" { Build-TinyCC }
    "python" { Build-Python }
    "java" { Build-JavaTools }
    "all" {
        Build-ClangToolchain
        Build-TinyCC  
        Build-Python
        Build-JavaTools
        Create-CompilerWrapper
        Package-Toolchain
    }
    default {
        Write-Host "Usage: ./build-toolchain.ps1 [-Target all|clang|tinycc|python|java]" -ForegroundColor Yellow
        Write-Host "Available targets:" -ForegroundColor Cyan
        Write-Host "  all     - Build complete toolchain (default)" -ForegroundColor White
        Write-Host "  clang   - Build Clang/LLVM toolchain" -ForegroundColor White  
        Write-Host "  tinycc  - Build TinyCC compiler" -ForegroundColor White
        Write-Host "  python  - Setup Python interpreter" -ForegroundColor White
        Write-Host "  java    - Setup Java compilation tools" -ForegroundColor White
    }
}

Write-Host "Build completed!" -ForegroundColor Green
Write-Host "Assets generated in: $OutputDir" -ForegroundColor Cyan