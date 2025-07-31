param(
    [string]$AndroidNdkPath = ""
)

Write-Host "=== Tree-sitter 一键构建脚本 ===" -ForegroundColor Green
Write-Host "This script will build all missing Tree-sitter libraries" -ForegroundColor Cyan

if ($AndroidNdkPath -eq "" -or !(Test-Path $AndroidNdkPath)) {
    Write-Host "❌ Please specify Android NDK path!" -ForegroundColor Red
    Write-Host "Download from: https://developer.android.com/ndk/downloads" -ForegroundColor Yellow
    Write-Host "Usage: .\Build-All.ps1 -AndroidNdkPath 'C:\android-ndk-r26d'" -ForegroundColor Yellow
    exit 1
}

$NdkBuild = Join-Path $AndroidNdkPath "ndk-build.cmd"
if (!(Test-Path $NdkBuild)) {
    Write-Host "❌ ndk-build.cmd not found in: $AndroidNdkPath" -ForegroundColor Red
    exit 1
}

Write-Host "✅ Android NDK found: $AndroidNdkPath" -ForegroundColor Green

# 检查现有库文件
$AssetsDir = "..\app\src\main\assets\native\arm64-v8a"
$ExistingLibs = @()
if (Test-Path $AssetsDir) {
    $ExistingLibs = Get-ChildItem $AssetsDir -Filter "*.so" | Select-Object -ExpandProperty Name
    if ($ExistingLibs.Count -gt 0) {
        Write-Host "✅ Found existing libraries:" -ForegroundColor Green
        $ExistingLibs | ForEach-Object { Write-Host "  - $_" -ForegroundColor Yellow }
    }
} else {
    Write-Host "⚠️  Assets directory not found, will be created" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "🚀 Starting build process..." -ForegroundColor Cyan

try {
    # 步骤1: 设置构建环境
    Write-Host "📦 Step 1: Setting up build environment..." -ForegroundColor Yellow
    & .\Build-TreeSitter-Native.ps1 -AndroidNdkPath $AndroidNdkPath
    if ($LASTEXITCODE -ne 0) {
        throw "Build environment setup failed"
    }
    
    # 步骤2: 编译库文件
    Write-Host ""
    Write-Host "🔨 Step 2: Compiling native libraries..." -ForegroundColor Yellow
    Push-Location "native_build"
    
    Write-Host "Running: $NdkBuild" -ForegroundColor Cyan
    & $NdkBuild
    if ($LASTEXITCODE -ne 0) {
        throw "Native library compilation failed"
    }
    
    Pop-Location
    
    # 步骤3: 复制库文件
    Write-Host ""
    Write-Host "📋 Step 3: Copying built libraries..." -ForegroundColor Yellow
    & .\Copy-Built-Libraries.ps1
    if ($LASTEXITCODE -ne 0) {
        throw "Library copying failed"
    }
    
    # 成功完成
    Write-Host ""
    Write-Host "🎉 Build completed successfully!" -ForegroundColor Green
    Write-Host ""
    
    # 显示最终状态
    Write-Host "📊 Final library status:" -ForegroundColor Cyan
    $FinalAssetsDir = "..\app\src\main\assets\native\arm64-v8a"
    if (Test-Path $FinalAssetsDir) {
        $FinalLibs = Get-ChildItem $FinalAssetsDir -Filter "*.so" | Select-Object -ExpandProperty Name
        $FinalLibs | ForEach-Object { 
            $size = (Get-Item (Join-Path $FinalAssetsDir $_)).Length
            Write-Host "  ✅ $_ ($([math]::Round($size/1KB, 1)) KB)" -ForegroundColor Green 
        }
    }
    
    Write-Host ""
    Write-Host "🚀 Next steps:" -ForegroundColor Yellow
    Write-Host "1. cd .." -ForegroundColor White
    Write-Host "2. ./gradlew assembleDebug" -ForegroundColor White
    Write-Host "3. Test Tree-sitter functionality in your app" -ForegroundColor White
    
    # 清理选项
    Write-Host ""
    $cleanup = Read-Host "Delete temporary build directory? [y/N]"
    if ($cleanup -eq "y" -or $cleanup -eq "Y") {
        if (Test-Path "native_build") {
            Remove-Item "native_build" -Recurse -Force
            Write-Host "🗑️  Temporary build directory cleaned up" -ForegroundColor Green
        }
    }
    
} catch {
    Write-Host ""
    Write-Host "❌ Build failed: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host ""
    Write-Host "🔍 Troubleshooting:" -ForegroundColor Yellow
    Write-Host "1. Check Android NDK path is correct" -ForegroundColor White
    Write-Host "2. Ensure internet connection for downloading sources" -ForegroundColor White
    Write-Host "3. Check available disk space" -ForegroundColor White
    Write-Host "4. Try running individual scripts manually" -ForegroundColor White
    exit 1
} finally {
    # 确保回到原始目录
    if (Get-Location | Select-Object -ExpandProperty Path | Select-String "native_build") {
        Pop-Location
    }
} 