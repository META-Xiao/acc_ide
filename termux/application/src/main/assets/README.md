# Termux Bootstrap Assets

## 如何添加bootstrap包到assets

为了提升用户体验，避免网络下载，您可以将Termux bootstrap包放置到此目录中。

### 1. 下载bootstrap包

从以下地址下载对应架构的bootstrap包：
- **aarch64** (推荐，支持大部分现代Android设备): 
  ```
  https://github.com/termux/termux-packages/releases/download/bootstrap-2025.08.31-r1%2Bapt.android-7/bootstrap-aarch64.zip
  ```
- **arm** (32位ARM设备):
  ```
  https://github.com/termux/termux-packages/releases/download/bootstrap-2025.08.31-r1%2Bapt.android-7/bootstrap-arm.zip
  ```

### 2. 放置文件

将下载的bootstrap包重命名并放置到 `packages/` 子目录中：

```
termux/application/src/main/assets/
└── packages/
    ├── bootstrap-aarch64.zip    <- 最重要，支持大部分设备
    ├── bootstrap-arm.zip        <- 可选，支持老设备
    ├── bootstrap-x86_64.zip     <- 可选，支持x86_64模拟器
    └── bootstrap-i686.zip       <- 可选，支持x86模拟器
```

**注意**: 系统会按以下顺序搜索bootstrap包：
1. 首先检查 `packages/` 子目录（推荐）
2. 然后检查 `assets/` 根目录（备用）

### 3. 优先级

AccIDE会按以下优先级选择bootstrap包：

1. **第一优先级**: assets中的本地包（即时安装，无需网络）
2. **备用方案**: 网络下载（如果本地包不存在或损坏）

### 4. 支持的架构

- `aarch64`: 64位ARM（推荐，覆盖90%+现代Android设备）
- `arm`: 32位ARM（老设备）
- `x86_64`: 64位x86（模拟器）
- `i686`: 32位x86（老模拟器）

### 5. 文件大小

每个bootstrap包大约30-50MB，包含：
- C++编译器 (clang, gcc)
- Python解释器 (python3)
- 包管理器 (pkg, apt)
- 基础Linux工具链
- 开发库和头文件

### 6. 验证

构建应用后，查看日志确认是否正确识别：
```
TermuxBootstrap: Found local bootstrap package in packages/: bootstrap-aarch64.zip
TermuxBootstrap: Using local bootstrap package from assets
TermuxBootstrap: Reading bootstrap from assets: packages/bootstrap-aarch64.zip
```

### 注意事项

- 建议至少包含 `bootstrap-aarch64.zip`，这覆盖了大部分现代Android设备
- 如果assets中没有对应架构的包，系统会自动回退到网络下载
- 本地包安装速度极快（2-5秒），网络下载可能需要1-5分钟
