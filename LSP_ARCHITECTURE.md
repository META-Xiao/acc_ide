# LSP 架构说明文档 / LSP Architecture Documentation

## 概述 / Overview

本项目已为 sora-editor 集成了基础的 Language Server Protocol (LSP) 支持架构，目前支持 Java、C/C++ 和 Python 语言。

This project has integrated a basic Language Server Protocol (LSP) support architecture for sora-editor, currently supporting Java, C/C++, and Python languages.

## 架构组件 / Architecture Components

### 1. LspManager
- **位置 / Location**: `app/src/main/java/com/acc_ide/lsp/LspManager.kt`
- **职责 / Responsibility**: LSP 管理器，负责管理语言服务器的生命周期
- **状态 / Status**: 基础架构已实现，使用增强TextMate作为当前实现

### 2. LanguageServerStarter
- **位置 / Location**: `app/src/main/java/com/acc_ide/lsp/LanguageServerStarter.kt`
- **职责 / Responsibility**: 语言服务器启动器，负责启动和管理各种LSP服务器
- **状态 / Status**: 框架已完成，等待真实服务器集成

### 3. EditorFragment 集成
- **位置 / Location**: `app/src/main/java/com/acc_ide/ui/editor/EditorFragment.kt`
- **功能 / Features**:
  - 支持在TextMate和LSP之间切换
  - 自动检测支持的语言
  - 优雅的回退机制

### 4. 设置界面
- **位置 / Location**: `app/src/main/java/com/acc_ide/ui/settings/SettingsFragment.kt`
- **功能 / Features**:
  - LSP 支持开关
  - 实时状态反馈
  - 持久化设置存储

## 当前实现状态 / Current Implementation Status

### ✅ 已完成 / Completed
- [x] 添加 `editor-lsp` 依赖
- [x] 基础 LSP 管理器架构
- [x] 语言服务器启动器框架
- [x] 设置界面集成
- [x] 语言检测和切换逻辑
- [x] 多语言字符串资源
- [x] 增强的TextMate集成
- [x] LSP端口管理和状态检测
- [x] 资源清理和生命周期管理

### 🚧 开发中 / In Development
- [x] LSP架构框架 (已完成基础版本)
- [ ] 真实语言服务器集成
- [ ] 完整的LSP客户端连接
- [ ] LSP事件处理和回调

### 📋 待实现 / TODO
- [ ] Java LSP 服务器集成 (Eclipse JDT LS)
- [ ] C/C++ LSP 服务器集成 (clangd)
- [ ] Python LSP 服务器集成 (pylsp)
- [ ] LSP 特性完整实现:
  - [ ] 文档同步
  - [ ] 自动补全增强
  - [ ] 错误诊断实时显示
  - [ ] 悬停信息显示
  - [ ] 转到定义
  - [ ] 查找引用

## 使用方法 / Usage

### 启用 LSP 支持 / Enable LSP Support
1. 打开设置页面
2. 找到 "LSP Support (Java/C++/Python)" 开关
3. 启用该开关
4. 重新打开代码文件以生效

**当前行为 / Current Behavior**:
- 启用LSP后，系统会尝试检测外部LSP服务器
- 如果没有外部服务器，将使用增强的TextMate (启用自动补全)
- 状态信息会显示当前使用的模式

### 外部LSP服务器连接 / External LSP Server Connection

如果您想连接到外部LSP服务器：

1. **Java (jdtls)**:
   - 在端口9999启动Eclipse JDT Language Server
   - 系统会自动检测并连接

2. **C/C++ (clangd)**:
   - 在端口9998启动clangd服务器
   - 确保有适当的编译数据库配置

3. **Python (pylsp)**:
   - 在端口9997启动python-lsp-server
   - 确保Python环境正确配置

### 支持的语言 / Supported Languages
- ☑️ Java (.java)
- ☑️ C/C++ (.cpp, .c, .hpp, .h)
- ☑️ Python (.py)

## 技术细节 / Technical Details

### 系统要求 / System Requirements

- **最低 SDK 版本**: Android 8.0 (API 26)
- **目标 SDK 版本**: Android 13 (API 33)
- **原因**: `editor-lsp` 库要求最低 API 26

> **注意**: 从 API 24 升级到 API 26 主要是为了支持 LSP 功能。如果您需要支持更低版本的 Android，可以考虑禁用 LSP 功能并仅使用 TextMate。

### LSP 服务器建议 / Recommended LSP Servers

#### Java
- **服务器**: Eclipse JDT Language Server
- **启动方式**: Socket 连接或进程管道
- **配置**: 需要 Java 运行时环境

#### C/C++
- **服务器**: clangd
- **启动方式**: 进程管道
- **配置**: 需要编译器工具链

#### Python
- **服务器**: python-lsp-server (pylsp)
- **启动方式**: 进程管道
- **配置**: 需要 Python 环境

### 集成架构 / Integration Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   EditorFragment │────│   LspManager    │────│ Language Servers│
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         │                       │                       ├─ Java (jdtls)
         │                       │                       ├─ C++ (clangd)
         │                       │                       └─ Python (pylsp)
         │                       │
         └───────────────────────┼─────────────────────────────────────
                                 │
                    ┌─────────────────┐
                    │   TextMate      │ (Fallback)
                    │   (Syntax Only) │
                    └─────────────────┘
```

## 配置说明 / Configuration

### 设置选项 / Settings Options
- `enable_lsp_support`: Boolean - 是否启用LSP支持
- 默认值: `false` (为了稳定性)

### 优先级 / Priority
1. 如果启用LSP且语言支持 → 尝试使用LSP
2. 如果LSP启动失败 → 回退到TextMate
3. 如果语言不支持LSP → 使用TextMate

## 开发指南 / Development Guide

### 添加新语言支持 / Adding New Language Support

1. 在 `LspManager.isLanguageSupported()` 中添加语言
2. 实现对应的语言服务器启动逻辑
3. 在 `EditorFragment.isLspSupportedLanguage()` 中添加语言
4. 更新字符串资源文件

### 实现完整LSP集成的步骤 / Steps for Full LSP Integration

1. **服务器管理**:
   ```kotlin
   // 启动语言服务器进程
   private fun startLanguageServer(language: String): Process
   
   // 建立Socket连接
   private fun createServerConnection(language: String): Socket
   ```

2. **客户端集成**:
   ```kotlin
   // 使用sora-editor的LSP模块
   val lspEditor = LspEditor.wrap(editor)
   val languageClient = DefaultLanguageClient(clientContext)
   val wrapper = LanguageServerWrapper(lspEditor, serverDefinition, languageClient)
   ```

3. **功能实现**:
   - 文档同步
   - 自动补全
   - 错误诊断
   - 代码格式化

## 故障排除 / Troubleshooting

### 常见问题 / Common Issues

1. **LSP开关无效**
   - 确保重新打开文件
   - 检查日志输出

2. **语言不被识别**
   - 确认文件扩展名正确
   - 检查语言映射配置

3. **回退到TextMate**
   - 这是正常行为，表示LSP暂不可用
   - 不影响基本的语法高亮功能

### 日志标签 / Log Tags
- `LspManager`: LSP管理器日志
- `EditorFragment`: 编辑器集成日志

## 未来计划 / Future Plans

1. **完整LSP实现** - 集成真实的语言服务器
2. **性能优化** - 异步加载和智能缓存
3. **更多语言** - TypeScript、Rust、Go等
4. **高级功能** - 重构、代码导航等

---

**注意**: 当前实现为基础架构版本，主要用于展示集成方案。完整的LSP功能需要进一步开发。

**Note**: The current implementation is a basic architecture version, mainly used to demonstrate the integration approach. Full LSP functionality requires further development. 