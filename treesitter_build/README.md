# Tree-sitter Native Libraries Builder 🛠️

这个文件夹包含构建 Tree-sitter 原生库的所有脚本和工具。

## 📋 文件说明

- **Build-Complete-TreeSitter.ps1** - 🚀 **完整构建脚本（推荐）** - 从源码构建所有库，支持架构选择
- **Build-All.ps1** - 一键完成所有构建步骤（需要AndroidIDE库）
- **README.md** - 本说明文件

## 🚀 快速开始

### 准备工作
1. **下载 Android NDK r26+**
   ```
   https://developer.android.com/ndk/downloads
   ```
   解压到某个目录，比如 `C:\android-ndk-r26d`

### 🎯 完整构建（强烈推荐）
完全从源码构建所有库，无需依赖 AndroidIDE：

```powershell
cd treesitter_build

# 构建所有架构
.\Build-Complete-TreeSitter.ps1 -AndroidNdkPath "C:\android-ndk-r26d"

# 只构建特定架构（更快）
.\Build-Complete-TreeSitter.ps1 -AndroidNdkPath "C:\android-ndk-r26d" -Architectures @("arm64-v8a")

# 交互式选择架构
.\Build-Complete-TreeSitter.ps1 -AndroidNdkPath "C:\android-ndk-r26d" -ShowArchMenu
```

### 📱 架构选择说明
- **arm64-v8a** - 现代 Android 手机（推荐）
- **armeabi-v7a** - 老旧 Android 设备
- **x86_64** - Android 模拟器（64位）
- **x86** - 老旧模拟器（32位）

### 传统构建（需要AndroidIDE）
如果你已经有 AndroidIDE 的库文件：

```powershell
# 复制现有库文件到 ../app/src/main/assets/native/arm64-v8a/
.\Build-All.ps1 -AndroidNdkPath "C:\android-ndk-r26d"
```

### 手动构建步骤
```powershell
# 1. 设置构建环境
.\Build-TreeSitter-Native.ps1 -AndroidNdkPath "C:\android-ndk-r26d"

# 2. 编译（在 native_build 目录中）
cd native_build
C:\android-ndk-r26d\ndk-build.cmd

# 3. 复制库文件
cd ..
.\Copy-Built-Libraries.ps1
```

## 📦 构建结果

### 🚀 完整构建脚本输出
使用 `Build-Complete-TreeSitter.ps1` 会完全从源码构建：

- `libtree-sitter.so` - Tree-sitter 核心库
- `libandroid-tree-sitter.so` - Tree-sitter Android 绑定
- `libtree-sitter-java.so` - Java 语言支持
- `libtree-sitter-cpp.so` - C++ 语言支持
- `libtree-sitter-python.so` - Python 语言支持

### 📱 支持语言
- ✅ **Java** - 完整语法分析和智能补全
- ✅ **C++** - 语法高亮和错误检测
- ✅ **Python** - 语法分析和代码结构

### 📊 传统构建脚本
使用其他脚本的组合构建结果可能不完整。

## 🔧 特性

- **16KB 页面大小兼容性** - 支持 Android 15+ 设备
- **多架构支持** - arm64-v8a, armeabi-v7a, x86_64, x86
- **自动化构建** - 一键完成所有步骤
- **错误检测** - 构建过程中的错误提示

## 📝 注意事项

1. **Android NDK 版本** - 必须使用 r26 或更高版本
2. **网络连接** - 构建过程需要下载约 20MB 源码文件
3. **磁盘空间** - 确保至少有 500MB 可用空间
4. **构建时间** - 全架构构建需要 5-10 分钟，单架构约 2-3 分钟
5. **临时文件** - 构建完成后可删除 `complete_build` 临时目录
6. **权限问题** - 确保有写入项目目录的权限

## 🚀 测试

构建完成后，回到项目根目录测试：
```bash
cd ..
./gradlew assembleDebug
```

如果一切正常，你的应用将支持完整的 Tree-sitter 语法分析功能！ 