# ACC IDE × Termux — TODO & Roadmap

> 2026-06-08 | 分支: feature/termux-backend | 子模块: custom/acc-ide

---

## 已完成

- [x] Terminal Emulator 集成 (TerminalView + TerminalSession + TermuxBridge)
- [x] Docker 构建管道 (`scripts/run-docker.ps1` + package-builder 容器)
- [x] Bootstrap 自编译 (`build-bootstraps.sh` 适配 com.acc_ide)
- [x] BootstrapManager (解压 + fixAllPaths + createSymlinks + fixPermissions)
- [x] Bug fix: bzip2 → libbz2 (`fix/build-bootstraps-bzip2`)
- [x] Bug fix: rm -f /* (`fix/rm-undefined-var`)
- [x] Bug fix: configure env vars (工作树中，待独立分支)
- [x] 文档: TERMUX_INTEGRATION.md

---

## 当前阻塞

### APT 在线安装不可用

从 `packages.termux.dev` 下载的 .deb 包使用 `com.termux` 前缀，
与 ACC IDE 的 `com.acc_ide` 前缀不兼容。dpkg 拒绝安装：

```
unable to stat './data/data/com.termux': Permission denied
```

**解决方案**: 自建 APT 仓库（见下文 Phase 1）

---

## Phase 1: 自定义 APT 仓库 + 预编译开发工具包

**目标**: 用户可以通过 `apt install` 安装开发工具

### 1.1 验证编译流程

- [ ] 在 Docker 容器中编译 clang (libllvm) — 最大最复杂的包，验证可行性
- [ ] 编译 build-essential (metapkg) + cmake + make + pkg-config
- [ ] 编译 python + nodejs + git + gdb
- [ ] 记录每个包的编译时间，估算全量构建耗时

### 1.2 搭建 APT 仓库

- [ ] 编写 `generate-repo.sh` — 从编译产物生成 Packages 索引
  ```bash
  # 在 output/ 目录生成 repository
  cd output
  dpkg-scanpackages . /dev/null | gzip > Packages.gz
  ```
- [ ] 编写 GitHub Release 上传脚本 (可用 GitHub Actions 自动化)
- [ ] 配置 bootstrap 中的 `$PREFIX/etc/apt/sources.list`：
  ```
  deb https://github.com/META-Xiao/acc-ide-packages/releases/download/v1 stable main
  ```

### 1.3 修改 bootstrap 包列表

- [ ] 在 `build-bootstraps.sh` 的 ADDITIONAL_PACKAGES 中加入核心开发工具
- [ ] 决策：哪些包打进 bootstrap，哪些留给 apt 在线安装？
  - 打进 bootstrap: bash, coreutils, dpkg, apt, 基础库
  - 留给 apt: clang, cmake, python, nodejs, git, gdb

---

## Phase 2: 内置 proot + Linux 发行版（可选）

**目标**: 高级用户可以一键安装完整 ARM Linux

### 2.1 适配 proot

- [ ] 在 bootstrap 中加入 `proot` 包
- [ ] 编译 proot (`packages/proot/`) 使用 `com.acc_ide` 前缀
- [ ] 测试 proot 基本功能：`proot -r <path> bash`

### 2.2 集成 proot-distro

- [ ] 评估 proot-distro 脚本的修改量（路径替换）
- [ ] 支持 Debian/Ubuntu ARM rootfs 下载
- [ ] 实现 "安装 Linux" UI（按钮触发下载 + 解压 + 配置）

### 2.3 IDE 集成

- [ ] IDE 通过 proot 调用 Linux 内工具：`proot -r <rootfs> -S <rootfs> clangd ...`
- [ ] 语言服务器协议 (LSP) 适配
- [ ] 编译任务适配 (gcc/g++/cmake 通过 proot 调用)

---

## Phase 3: IDE 功能完善

**目标**: 达到 VSCode 级别的开发体验

- [ ] LSP 客户端集成 (clangd, typescript-language-server, pyright)
- [ ] 代码补全、跳转、诊断
- [ ] 内置终端 + 构建任务 (Ctrl+Shift+B)
- [ ] Git 集成 (图形化 diff, stage, commit)
- [ ] 调试器集成 (lldb/gdb 前端)

---

## 技术债务

- [ ] Bug fix: configure env vars — 从工作树提取到独立分支，向上游提 PR
- [ ] `SYS_BASHRC` 编译期路径警告 — 不影响功能，低优先级
- [ ] run-docker.ps1 中的 `--init --cap-add --device fuse` 改动确认是否需要
- [ ] 评估 targetSdkVersion 限制（当前用 28 绕过 W^X，Play Store 需要替代方案）

---

## 架构决策记录

| 日期 | 决策 | 原因 |
|------|------|------|
| 2026-06-07 | 自编译 bootstrap 替代路径补丁方案 | dpkg ELF 路径不可修复 |
| 2026-06-07 | Docker 容器构建 | 需要 Android NDK 交叉编译 |
| 2026-06-08 | 分层架构 (bootstrap + apt + proot) | 兼顾体积、灵活性、用户体验 |
| 2026-06-08 | GitHub Releases 托管 APT 仓库 | 免费、无需运维服务器 |

---

## 编译目标包清单

### Tier 1: 必须（打进 bootstrap 或通过 apt）
```
libllvm (→ clang, lld, lldb, llvm-tools, libcompiler-rt)
build-essential  (metapkg: clang + make + pkg-config)
cmake
git
python
nodejs
gdb
```

### Tier 2: 推荐
```
nano / vim
openssh
curl / wget
ripgrep / fd
tmux
patchelf
```

### Tier 3: 语言生态
```
rust
golang
ruby
php
lua
```

---

## 相关链接

- 集成方案: [TERMUX_INTEGRATION.md](./TERMUX_INTEGRATION.md)
- 上游 Issue: https://github.com/termux/termux-packages/issues/27093
- Fix: bzip2 → libbz2: `fix/build-bootstraps-bzip2`
- Fix: rm -f /*: `fix/rm-undefined-var`
- 构建分支: `custom/acc-ide`
