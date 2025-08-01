# Tree-sitter Build Scripts

This directory contains scripts to build Tree-sitter libraries for Android.

## 🚀 Quick Start (Interactive Mode)

Simply run the launcher script from the project root:

```powershell
# From project root directory
./start.ps1
```

Or run directly from the treesitter_build directory:

```powershell
# From treesitter_build directory
./Build-Complete-TreeSitter.ps1
```

The script will guide you through:
1. **NDK Path Configuration** - Enter your Android NDK path
2. **CMake Selection** - Choose from auto-detected CMake installations or enter custom path
3. **Architecture Selection** - Select which architectures to build

## 📋 Interactive Process

### Step 1: Android NDK
- Enter your NDK path (e.g., `C:\android-ndk-r26d`)
- Script validates the path and checks for ndk-build.cmd

### Step 2: CMake Configuration
- Auto-detects CMake from:
  - Android SDK (`AndroidSDK\cmake\3.22.1\bin\cmake.exe`)
  - MinGW installations
  - System PATH
- Choose from detected options or enter custom path

### Step 3: Architecture Selection
1. arm64-v8a (64-bit ARM - Modern phones)
2. armeabi-v7a (32-bit ARM - Older devices)  
3. x86_64 (64-bit x86 - Emulators)
4. x86 (32-bit x86 - Old emulators)
5. All architectures (recommended)

## 🛠️ Non-Interactive Mode

For automation or CI/CD:

```powershell
./Build-Complete-TreeSitter.ps1 -AndroidNdkPath "C:\android-ndk-r26d" -CustomCMakePath "C:\Android\Sdk\cmake\3.22.1\bin\cmake.exe" -Architectures @("arm64-v8a", "x86_64") -NonInteractive
```

## 📁 Supported CMake Sources

- **Android SDK CMake 3.22.1+** (recommended)
- **MinGW CMake**
- **System CMake**
- **Custom path**

> **Note**: Android NDK doesn't include CMake executable, only `android.toolchain.cmake` for cross-compilation.

## 📦 Output

Built libraries are copied to:
```
../app/src/main/jniLibs/
├── arm64-v8a/
├── armeabi-v7a/
├── x86_64/
└── x86/
```

## 🔧 Features

- **16KB Page Alignment** - Android 15+ compatibility
- **Auto-detection** - Finds NDK and CMake automatically
- **Validation** - Checks library alignment using llvm-readelf
- **Recovery** - CMake rebuild if ndk-build fails alignment
- **Interactive** - User-friendly step-by-step process

## 🆘 Troubleshooting

If build fails:
1. Ensure Android NDK 27+ is installed
2. Install CMake via Android Studio SDK Manager
3. Check internet connection for source downloads
4. Verify sufficient disk space (>500MB)
5. Try building single architecture first

## 📄 Libraries Built

- `libtree-sitter.so` - Core library
- `libtree-sitter-java.so` - Java parser
- `libtree-sitter-cpp.so` - C++ parser
- `libtree-sitter-python.so` - Python parser 