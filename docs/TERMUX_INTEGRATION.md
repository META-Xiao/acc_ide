# ACC IDE × Termux 集成方案

> 状态：Bootstrap 构建成功，Terminal Emulator 已集成，APT 在线安装存在路径不兼容问题

---

## 架构概述

```
┌──────────────────────────────────────────────────────────┐
│                    ACC IDE App                            │
│  ┌──────────────────────────────────────────────────┐    │
│  │  ShellFragment.kt                                 │    │
│  │  ┌────────────────────────────────────────────┐  │    │
│  │  │  TerminalView (termux-view)                 │  │    │
│  │  │  TerminalSession (termux-terminal)          │  │    │
│  │  └────────────────────────────────────────────┘  │    │
│  └──────────────────────────────────────────────────┘    │
│                         │                                  │
│  ┌──────────────────────────────────────────────────┐    │
│  │  TermuxBridge.kt                                  │    │
│  │  - 选择 shell: Termux bash / system sh             │    │
│  │  - 设置环境变量: PREFIX, HOME, PATH, LD_*          │    │
│  │  - 启动 JNI 进程: exec() bash                      │    │
│  └──────────────────────────────────────────────────┘    │
│                         │                                  │
│  ┌──────────────────────────────────────────────────┐    │
│  │  BootstrapManager.kt                              │    │
│  │  - 解压 bootstrap .zip 到 $PREFIX                  │    │
│  │  - 修复文本文件中的路径                             │    │
│  │  - 创建符号链接 + 设置权限 + chroot lib dirs        │    │
│  │  - 版本管理 (.bootstrap_version sentinel)          │    │
│  └──────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────┐
│  Android Filesystem                                       │
│  /data/data/com.acc_ide/files/                            │
│    ├── usr/              ← $PREFIX (Termux 的 /usr)       │
│    │   ├── bin/          bash, apt, dpkg, ...             │
│    │   ├── etc/          profile, bash.bashrc, apt/       │
│    │   ├── lib/          libtermux-exec-ld-preload.so     │
│    │   ├── tmp/                                           │
│    │   └── var/lib/dpkg/ dpkg status + info               │
│    └── home/             ← $HOME                          │
└──────────────────────────────────────────────────────────┘
```

---

## 核心机制：路径前缀 (PREFIX)

Termux 的一切都基于一个核心常量：**`TERMUX_APP__PACKAGE_NAME`**。

```
Termux 官方: TERMUX_APP__PACKAGE_NAME = com.termux
             PREFIX = /data/data/com.termux/files/usr
             HOME   = /data/data/com.termux/files/home

ACC IDE:     TERMUX_APP__PACKAGE_NAME = com.acc_ide
             PREFIX = /data/data/com.acc_ide/files/usr
             HOME   = /data/data/com.acc_ide/files/home
```

这个常量影响：
- C 编译时的 `#define` 宏（进入 ELF `.rodata` 段）
- shell 脚本中的 `$PREFIX` 变量
- `.deb` 包内的文件路径
- 所有配置文件的路径引用

---

## 构建方式：从源码编译 Bootstrap

### 为什么不用官方预编译 Bootstrap

官方的 `bootstrap-aarch64.zip` 所有路径都硬编码为 `/data/data/com.termux/files/usr`。虽然可以用文本替换 + `LD_PRELOAD` 做运行时翻译，但：
- ELF 二进制中的 `.rodata` 段无法安全替换（新路径长度不同会破坏二进制）
- dpkg 的 CONFDIR 编译期路径不可达会导致包管理完全不可用

### 自建构建流程

```
┌─────────────────────────────────────────────────────────┐
│ 1. Docker 容器 (termux/package-builder)                  │
│    ├── Android NDK + 交叉编译工具链                       │
│    └── 环境变量:                                          │
│        TERMUX_APP__PACKAGE_NAME=com.acc_ide               │
│        TERMUX_APP__DATA_DIR=/data/data/com.acc_ide/files  │
│        TERMUX__PREFIX=/data/data/com.acc_ide/files/usr    │
│                                                          │
│ 2. 运行 build-bootstraps.sh                              │
│    ├── 编译 ~30 个核心包 (bash, apt, dpkg, ...)          │
│    ├── 打包 .deb → 解压到 rootfs                          │
│    └── 生成 bootstrap-aarch64.zip                         │
│                                                          │
│ 3. BootstrapManager.kt 在 app 首次启动时                  │
│    ├── 解压 bootstrap-aarch64.zip 到 $PREFIX              │
│    ├── fixAllPaths() — 修复残留的 termux 路径             │
│    ├── createSymlinks() — 1160+ 个符号链接                │
│    └── fixPermissions() — 设置 0700/0500/0400 权限        │
└─────────────────────────────────────────────────────────┘
```

### 启动命令

```powershell
.\scripts\run-docker.ps1 ./scripts/build-bootstraps.sh --architectures aarch64
```

### Bootstrap 构建环境变量

```bash
# 在容器中 /home/builder/termux-packages/scripts/properties.sh
TERMUX_APP__PACKAGE_NAME=com.acc_ide
TERMUX_APP__DATA_DIR=/data/data/com.acc_ide/files
TERMUX__ROOTFS=/data/data/com.acc_ide/files
TERMUX__PREFIX=/data/data/com.acc_ide/files/usr
TERMUX__HOME=/data/data/com.acc_ide/files/home
```

---

## 目前已完成的工作

### Terminal Emulator 层

| 组件 | 状态 | 说明 |
|------|------|------|
| TerminalView (termux-view) | ✅ | 终端渲染组件已集成 |
| TerminalSession | ✅ | 虚拟终端进程管理 |
| TermuxBridge.kt | ✅ | 桥梁：选择 shell，设置环境变量 |
| ShellFragment.kt | ✅ | UI 层：展示终端界面 |

### Bootstrap 层

| 组件 | 状态 | 说明 |
|------|------|------|
| BootstrapManager.kt | ✅ | 解压、修复路径、符号链接、权限 |
| fixAllPaths() | ✅ | 文本文件路径替换 (260+ 文件) |
| createSymlinks() | ✅ | 符号链接创建 (1160+ 链接) |
| fixPermissions() | ✅ | chmod 权限设置 |
| 版本检测 | ✅ | `.bootstrap_version` sentinel |

### 构建基础设施

| 组件 | 状态 | 说明 |
|------|------|------|
| Docker 容器 | ✅ | termux/package-builder |
| build-bootstraps.sh 适配 | ✅ | 自定义 PREFIX、自定义包列表 |
| 子模块 termux-packages | ✅ | 分支 custom/acc-ide |

---

## 发现并修复的 Bug

### Bug 1: bzip2 不是独立包

**位置**: `scripts/build-bootstraps.sh` 第 432 行

**问题**: `PACKAGES+=("bzip2")` — 但 `packages/` 目录下没有 `bzip2/`。`bzip2` 是 `libbz2` 的子包（见 `packages/libbz2/bzip2.subpackage.sh`）。

**错误信息**:
```
ERROR: No package bzip2 found in any of the enabled repositories.
```

**修复**: `PACKAGES+=("libbz2")` — 编译 `libbz2` 自动生成 `bzip2*.deb`

**分支**: `fix/build-bootstraps-bzip2`
**上游 Issue**: https://github.com/termux/termux-packages/issues/27093

---

### Bug 2: `TERMUX_BUILT_PACKAGES_DIRECTORY_FOR_ARCH` 未定义 → `rm -f /*`

**位置**: `scripts/build-bootstraps.sh` 第 400 行

**问题**: `TERMUX_BUILT_PACKAGES_DIRECTORY_FOR_ARCH` 在整个代码库中从未被定义。Bash 将空字符串展开：
```bash
rm -f "$TERMUX_BUILT_PACKAGES_DIRECTORY_FOR_ARCH"/*
→ rm -f ""/*
→ rm -f /*
```

这会尝试删除根目录下的文件（`/tmp`, `/usr`, `/var` 等）。虽然 `rm -f`（无 `-r`）不能删除目录，但这是一个危险操作。

**修复**: 删除该行。下面的 `TERMUX_BUILT_DEBS_DIRECTORY` 已在 `build-package.sh` 中正确定义，功能相同。

**分支**: `fix/rm-undefined-var`

---

### Bug 3: 5 个环境变量未传入 autotools configure

**位置**: `scripts/build/configure/termux_step_configure_autotools.sh` 第 104 行

**问题**: `termux-tools` 等约 30 个包的 `configure.ac` 使用 `AC_SUBST(termux_app_package)` 等宏获取路径，但 `./configure` 调用时未传入这些环境变量，导致 fallback 到默认的 `com.termux` 路径。

**修复**: 添加 5 个环境变量：
```bash
env $AVOID_GNULIB \
    TERMUX_APP_PACKAGE="$TERMUX_APP_PACKAGE" \      # ← 新增
    TERMUX_BASE_DIR="$TERMUX_BASE_DIR" \            # ← 新增
    TERMUX_PREFIX="$TERMUX_PREFIX" \                # ← 新增
    TERMUX_ANDROID_HOME="$TERMUX_ANDROID_HOME" \    # ← 新增
    TERMUX_CACHE_DIR="$TERMUX_CACHE_DIR" \          # ← 新增
    "$TERMUX_PKG_SRCDIR/configure" \
```

**状态**: 仍在工作树中（`custom/acc-ide`），未独立分支

---

## 已知问题

### 问题 1: APT 在线安装的包路径不兼容 ⚠️ 核心问题

**现象**:
```
~ $ apt install clang
dpkg: error processing archive .../0-libcompiler-rt_21.1.8-2_aarch64.deb (--unpack):
 unable to stat './data/data/com.termux' (which was about to be installed):
 Permission denied
E: Sub-process /data/data/com.acc_ide/files/usr/bin/dpkg returned an error code (1)
```

**根因**:

Termux 官方 APT 仓库（`packages.termux.dev`）中的 `.deb` 包全部以 `TERMUX_APP__PACKAGE_NAME=com.termux` 编译，文件路径硬编码：

```
deb 包内部路径:
./data/data/com.termux/files/usr/lib/libllvm.so
./data/data/com.termux/files/usr/bin/clang
                            ↑
                        com.termux — ACC IDE 无权访问
```

当 dpkg 尝试安装时，会尝试在 `/data/data/com.termux/` 下创建文件。Android 的 app 隔离机制禁止 `com.acc_ide` 访问其他 app 的数据目录。

**影响范围**:
- ✅ Bootstrap 内置包：正常（使用 `com.acc_ide` 编译）
- ❌ APT 在线安装的任何包：全部失败（使用 `com.termux` 编译）

**解决方案选项**:

| 方案 | 说明 | 代价 |
|------|------|------|
| A. 自建 APT 镜像 | 用 `com.acc_ide` 重编译所有需要的包，托管自定义仓库 | 需要服务器 + 持续维护 |
| B. 离线预编译 | 把需要的包直接打进 bootstrap，禁用 APT 在线功能 | 用户无法自定义安装包 |
| C. APT hook 路径转换 | 下载后自动 repack `.deb` 替换路径 | 需要实现自动化 repack 工具 |
| D. Root 设备 | `ln -s com.acc_ide com.termux` | 仅 root 设备，不通用 |

**当前决策**: 待定，倾向方案 B（离线预编译核心包）+ 未来方案 A（自建 apt repo）

---

### 问题 2: SYS_BASHRC 编译期路径警告（不致命）

bash 编译时 `#define SYS_BASHRC "/data/data/com.termux/files/usr/etc/bash.bashrc"` 存储在 ELF `.rodata` 段。启动时打印警告但不影响功能——bash 仍然通过 `$PREFIX/etc/profile` 加载正确的 bashrc。

---

## 技术要点

### PATH 翻译机制

Termux 使用多层路径翻译：

```
层 1: $PREFIX 环境变量 — 脚本中使用 $PREFIX 而非硬编码
层 2: LD_PRELOAD (termux-exec) — execve() 拦截，运行时翻译 shebang
层 3: 编译期 #define — C 代码中的路径常量 (ELF .rodata)
```

层 1 和层 2 在我们自编译的 bootstrap 中已经正确，层 3 的残留路径只能通过重新编译消除。

### Bootstrap 文件统计

```
总文件数:    ~3,650
文本文件:    ~3,330 (含硬编码路径: ~420)
ELF 二进制:  ~320
符号链接:    ~1,160
```

---

## 相关链接

- 上游 Issue: https://github.com/termux/termux-packages/issues/27093
- 上游 PR (参考): https://github.com/termux/termux-packages/pull/24647
- Fix: bzip2 → libbz2 — 分支 `fix/build-bootstraps-bzip2`
- Fix: rm -f /* — 分支 `fix/rm-undefined-var`
- 构建分支: `custom/acc-ide` (子模块 termux-packages)
