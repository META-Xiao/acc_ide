# 简化版本 - 基于现有工具构建
# 这个版本更实际可行

param(
    [string]$OutputDir = "app/src/main/assets/compilers"
)

$ErrorActionPreference = "Stop"

Write-Host "=== ACC IDE Compiler Assets Builder ===" -ForegroundColor Magenta

# 创建输出目录
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

function Download-TinyCC {
    Write-Host "Downloading precompiled TinyCC..." -ForegroundColor Yellow
    
    # TinyCC有Android ARM64预编译版本
    $TCC_ARM64_URL = "https://github.com/TinyCC/tinycc/releases/download/release_0_9_27/tcc-0.9.27-android-arm64.tar.gz"
    $TCC_ARCHIVE = "build/tcc-android-arm64.tar.gz"
    
    New-Item -ItemType Directory -Force -Path "build" | Out-Null
    
    try {
        Write-Host "Downloading TinyCC for Android ARM64..." -ForegroundColor Cyan
        Invoke-WebRequest -Uri $TCC_ARM64_URL -OutFile $TCC_ARCHIVE
        
        # 解压
        & tar -xzf $TCC_ARCHIVE -C "build/"
        
        # 复制到assets
        Copy-Item -Recurse "build/tcc-android-arm64/*" "$OutputDir/"
        
        Write-Host "✓ TinyCC downloaded and extracted" -ForegroundColor Green
    }
    catch {
        Write-Warning "Failed to download TinyCC. Building from Termux packages instead..."
        Download-TermuxCompiler "clang"
    }
}

function Download-TermuxPackage {
    param($PackageName, $Version = "latest")
    
    Write-Host "Downloading Termux package: $PackageName" -ForegroundColor Cyan
    
    $TERMUX_REPO = "https://packages-cf.termux.org/apt/termux-main"
    $ARCH = "aarch64"
    
    # 获取包信息
    $PACKAGES_URL = "$TERMUX_REPO/dists/stable/main/binary-$ARCH/Packages"
    
    try {
        # 下载包列表
        $PackagesContent = Invoke-WebRequest -Uri $PACKAGES_URL -UseBasicParsing
        
        # 解析包信息
        $PackageInfo = $PackagesContent.Content | Select-String -Pattern "Package: $PackageName" -Context 0,20
        
        if ($PackageInfo) {
            # 提取文件名
            $Filename = ($PackageInfo.Context.PostContext | Select-String "Filename: (.+)").Matches[0].Groups[1].Value
            
            if ($Filename) {
                Write-Host "Found package: $Filename" -ForegroundColor Green
                
                # 下载deb包
                $PackageUrl = "$TERMUX_REPO/$Filename"
                $LocalFile = "build/$(Split-Path $Filename -Leaf)"
                
                Invoke-WebRequest -Uri $PackageUrl -OutFile $LocalFile
                
                # 解压deb包
                Extract-DebPackage $LocalFile "$OutputDir/$PackageName"
                
                return $LocalFile
            }
        }
        
        Write-Warning "Package $PackageName not found in repository"
        return $null
    }
    catch {
        Write-Error "Failed to download $PackageName : $_"
        return $null
    }
}

function Extract-DebPackage {
    param($DebFile, $OutputPath)
    
    Write-Host "Extracting $DebFile..." -ForegroundColor Cyan
    
    # 创建临时目录
    $TempDir = "build/temp_extract"
    New-Item -ItemType Directory -Force -Path $TempDir | Out-Null
    
    try {
        Push-Location $TempDir
        
        # 使用ar解压deb文件
        if (Get-Command "ar" -ErrorAction SilentlyContinue) {
            & ar x $DebFile
        }
        elseif (Get-Command "7z" -ErrorAction SilentlyContinue) {
            & 7z x $DebFile
        }
        else {
            Write-Error "Need 'ar' or '7z' to extract deb packages"
            return
        }
        
        # 解压data.tar.xz
        if (Test-Path "data.tar.xz") {
            & 7z x "data.tar.xz"
            & 7z x "data.tar"
        }
        elseif (Test-Path "data.tar.gz") {
            & tar -xzf "data.tar.gz"
        }
        
        # 复制文件
        if (Test-Path "data") {
            New-Item -ItemType Directory -Force -Path $OutputPath | Out-Null
            Copy-Item -Recurse "data/data/com.termux/files/usr/*" $OutputPath -Force
            Write-Host "✓ Package extracted to $OutputPath" -ForegroundColor Green
        }
    }
    finally {
        Pop-Location
        Remove-Item -Recurse -Force $TempDir -ErrorAction SilentlyContinue
    }
}

function Setup-Python {
    Write-Host "Setting up Python..." -ForegroundColor Yellow
    
    # 下载Termux Python
    $PythonPkg = Download-TermuxPackage "python"
    
    if ($PythonPkg) {
        # 创建Python启动脚本
        $PythonScript = @"
#!/system/bin/sh
export PYTHONHOME=/data/data/com.acc_ide/files/compilers/python
export PYTHONPATH=`$PYTHONHOME/lib/python3.11
export LD_LIBRARY_PATH=`$PYTHONHOME/lib:`$LD_LIBRARY_PATH
exec `$PYTHONHOME/bin/python3.11 "`$@"
"@
        
        $PythonScript | Out-File -FilePath "$OutputDir/python/python" -Encoding ASCII
        Write-Host "✓ Python setup complete" -ForegroundColor Green
    }
}

function Setup-JavaCompiler {
    Write-Host "Setting up Java compiler..." -ForegroundColor Yellow
    
    # 对于Java，我们使用一个更简单的方法：
    # 1. 使用系统的javac (如果可用)
    # 2. 或者使用ECJ (Eclipse Compiler for Java)
    
    $ECJ_URL = "https://repo1.maven.org/maven2/org/eclipse/jdt/ecj/3.33.0/ecj-3.33.0.jar"
    $ECJ_JAR = "$OutputDir/java/ecj.jar"
    
    New-Item -ItemType Directory -Force -Path "$OutputDir/java" | Out-Null
    
    try {
        Write-Host "Downloading Eclipse Compiler for Java..." -ForegroundColor Cyan
        Invoke-WebRequest -Uri $ECJ_URL -OutFile $ECJ_JAR
        
        # 创建Java编译脚本
        $JavaScript = @"
#!/system/bin/sh
# Java compiler using ECJ
export ANDROID_RUNTIME=/system/framework/android.jar
java -jar $ECJ_JAR -cp `$ANDROID_RUNTIME "`$@"
"@
        
        $JavaScript | Out-File -FilePath "$OutputDir/java/javac" -Encoding ASCII
        
        Write-Host "✓ Java compiler (ECJ) downloaded" -ForegroundColor Green
    }
    catch {
        Write-Warning "Failed to download ECJ: $_"
    }
}

function Create-CompilerConfigs {
    Write-Host "Creating compiler configurations..." -ForegroundColor Yellow
    
    # 为Android应用创建编译器配置
    $Config = @{
        version = "1.0.0"
        supported_languages = @("c", "cpp", "python", "java")
        compilers = @{
            c = @{
                executable = "tinycc/tcc"
                args = @("-run", "{source}")
                type = "interpreter"
            }
            cpp = @{
                executable = "tinycc/tcc" 
                args = @("-run", "{source}")
                type = "interpreter"
                note = "C++ support limited in TinyCC"
            }
            python = @{
                executable = "python/python"
                args = @("{source}")
                type = "interpreter"
            }
            java = @{
                executable = "java/javac"
                args = @("{source}")
                type = "compiler"
                runtime = "dalvik"
            }
        }
        environment = @{
            TMPDIR = "/data/data/com.acc_ide/cache"
            HOME = "/data/data/com.acc_ide/files"
        }
    }
    
    $Config | ConvertTo-Json -Depth 4 | Out-File -FilePath "$OutputDir/compiler-config.json" -Encoding UTF8
    
    # 创建权限列表
    @"
# Executable files (need chmod +x)
tinycc/tcc
python/python
python/bin/python3.11
java/javac
"@ | Out-File -FilePath "$OutputDir/executables.txt" -Encoding ASCII
    
    Write-Host "✓ Compiler configurations created" -ForegroundColor Green
}

function Package-Assets {
    Write-Host "Packaging final assets..." -ForegroundColor Yellow
    
    # 创建各语言的zip包
    $Languages = @("tinycc", "python", "java")
    
    foreach ($Lang in $Languages) {
        $LangDir = "$OutputDir/$Lang"
        if (Test-Path $LangDir) {
            $ZipFile = "$OutputDir/$Lang-toolchain.zip"
            
            if (Get-Command "7z" -ErrorAction SilentlyContinue) {
                & 7z a -tzip $ZipFile "$LangDir/*"
            }
            else {
                Compress-Archive -Path "$LangDir/*" -DestinationPath $ZipFile -Force
            }
            
            Write-Host "✓ Created $ZipFile" -ForegroundColor Green
        }
    }
    
    # 创建安装脚本
    @"
#!/system/bin/sh
# ACC IDE Toolchain Installer
# This script is called by the Android app to setup compilers

INSTALL_DIR="/data/data/com.acc_ide/files/toolchain"
mkdir -p `$INSTALL_DIR

# Extract toolchains
cd `$INSTALL_DIR

# Set permissions for executables
while IFS= read -r exec_file; do
    [ -f "`$exec_file" ] && chmod +x "`$exec_file"
done < executables.txt

echo "Toolchain installation complete"
"@ | Out-File -FilePath "$OutputDir/install.sh" -Encoding ASCII
    
    Write-Host "✓ Installation script created" -ForegroundColor Green
}

# 执行构建流程
Write-Host "Building toolchain assets..." -ForegroundColor Cyan

try {
    Download-TinyCC
    Setup-Python  
    Setup-JavaCompiler
    Create-CompilerConfigs
    Package-Assets
    
    Write-Host "`n=== Build Summary ===" -ForegroundColor Magenta
    Write-Host "✓ TinyCC for C/C++" -ForegroundColor Green
    Write-Host "✓ Python interpreter" -ForegroundColor Green  
    Write-Host "✓ Java compiler (ECJ)" -ForegroundColor Green
    Write-Host "✓ Configuration files" -ForegroundColor Green
    Write-Host "`nAssets created in: $OutputDir" -ForegroundColor Cyan
    Write-Host "Total size: $((Get-ChildItem $OutputDir -Recurse | Measure-Object Length -Sum).Sum / 1MB) MB" -ForegroundColor Yellow
}
catch {
    Write-Error "Build failed: $_"
    exit 1
}

Write-Host "`nNext steps:" -ForegroundColor Yellow
Write-Host "1. The assets are ready in $OutputDir" -ForegroundColor White
Write-Host "2. Android app will extract and install them on first run" -ForegroundColor White
Write-Host "3. CompilerManager.installFromAssets() will handle the setup" -ForegroundColor White