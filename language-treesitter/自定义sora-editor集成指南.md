# 自定义sora-editor集成指南

## 项目概述

我们成功修改了sora-editor的`language-treesitter`模块，使其不再依赖AndroidIDE的Tree-sitter实现，而是可以直接使用您项目中的自定义JNI Tree-sitter实现。

## 修改的文件清单

### 1. 核心适配器文件
- **`language-treesitter/src/main/java/io/github/rosemoe/sora/editor/ts/adapter/TSLanguageInterface.kt`**
  - 定义了`TSLanguageInterface`接口作为语言适配器的抽象
  - 实现了`CustomTSLanguageWrapper`类来包装自定义TreeSitter实现

- **`language-treesitter/src/main/java/io/github/rosemoe/sora/editor/ts/adapter/TSAdapterClasses.kt`**
  - 实现了所有AndroidIDE Tree-sitter类的适配器版本
  - 包括`TSQueryAdapter`, `TSNodeAdapter`, `UTF16StringAdapter`等
  - `UTF16StringAdapter`实现了`CharSequence`接口以兼容sora-editor API

- **`language-treesitter/src/main/java/io/github/rosemoe/sora/editor/ts/adapter/TreeSitterLanguageFactory.kt`**
  - 提供工厂方法来创建语言适配器

### 2. 修改的sora-editor核心文件
以下文件已全部更新为使用新的适配器类型而非AndroidIDE类型：

- `TsLanguageSpec.kt` - 语言规范类
- `TsAnalyzeManager.kt` - 分析管理器
- `LineSpansGenerator.kt` - 代码高亮生成器
- `TsBracketPairs.kt` - 括号匹配
- `TsScopedVariables.kt` - 作用域变量
- `TsTheme.kt` - 主题系统
- 所有谓词系统相关文件 (`predicate/` 目录下)
- 所有span工厂相关文件 (`spans/` 目录下)

### 3. 项目配置文件
- **`language-treesitter/build.gradle.kts`**
  - 移除了对AndroidIDE tree-sitter的依赖
  - 配置为独立的Android库模块

- **`app/build.gradle`**
  - 使用本地的`:language-treesitter`模块替代官方版本

- **`settings.gradle.kts`**
  - 添加了`:language-treesitter`模块

### 4. 应用层集成
- **`app/src/main/java/com/acc_ide/lsp/analyzer/TreeSitterAnalyzer.kt`**
  - 更新了`createTsLanguage`方法来使用新的适配器
  - 通过`TreeSitterLanguageFactory.wrapLanguage()`创建适配器

## 集成方式说明

### 当前集成方式：本地模块依赖

```gradle
// app/build.gradle
dependencies {
    implementation project(':language-treesitter')
    // ... 其他依赖
}
```

### 关键集成代码

在`TreeSitterAnalyzer.kt`中：

```kotlin
// 使用修改后的language-treesitter模块创建适配器
val adaptedLanguage = TreeSitterLanguageFactory.wrapLanguage(
    tsLanguage,
    { tsLanguage.getLanguageName() },
    { tsLanguage.getInstance() }
)

val spec = TsLanguageSpec(
    language = adaptedLanguage,
    highlightScmSource = safeHighlightScm,
    codeBlocksScmSource = codeBlocksScm,
    bracketsScmSource = bracketsScm,
    localsScmSource = localsScm
)

val tsLang = TsLanguage(
    languageSpec = spec,
    tab = true
) {
    // 主题配置
}
```

## 技术细节

### 适配器模式实现
我们使用适配器模式来桥接两个不兼容的接口：
- **源接口**: `TreeSitterWrapper.TSLanguage` (您的自定义JNI实现)
- **目标接口**: `TSLanguageInterface` (sora-editor期望的接口)
- **适配器**: `CustomTSLanguageWrapper`

### UTF16String兼容性
`UTF16StringAdapter`实现了`CharSequence`接口，确保与sora-editor的文本处理系统完全兼容。

### 查询系统适配
所有Tree-sitter查询相关的类都有对应的适配器版本，保持API一致性。

## 编译和构建

项目现在可以成功编译：

```bash
./gradlew assembleDebug
```

## 功能验证

修改后的系统提供以下功能：
1. ✅ **语法高亮** - 通过Tree-sitter查询和SCM文件
2. ✅ **代码块折叠** - 基于语法树的代码结构
3. ✅ **括号匹配** - 智能括号配对
4. ✅ **作用域变量** - 局部变量识别
5. ✅ **主题系统** - 可配置的代码高亮主题

## 优势

1. **完全自主** - 不依赖已归档的AndroidIDE项目
2. **高性能** - 直接使用您优化的JNI实现
3. **可扩展** - 可以轻松添加新的语言支持
4. **兼容性** - 保持与sora-editor API的完全兼容
5. **维护性** - 代码结构清晰，易于维护和更新

## 后续发展

### Git子模块方式 (可选)
```bash
git submodule add https://github.com/your-fork/sora-editor.git external/sora-editor
```

### Maven本地发布 (可选)
```kotlin
// language-treesitter/build.gradle.kts
apply plugin: 'maven-publish'

publishing {
    publications {
        maven(MavenPublication) {
            from components.release
            groupId = 'com.acc_ide'
            artifactId = 'language-treesitter-custom'
            version = '1.0.0'
        }
    }
}
```

## 结论

我们成功地创建了一个定制的sora-editor `language-treesitter`模块，它：
- 完全移除了对AndroidIDE的依赖
- 使用您现有的Tree-sitter JNI实现
- 保持了所有原有功能
- 编译通过并可以正常使用

这为您的Android IDE项目提供了完整的Tree-sitter支持，包括语法高亮、智能补全和错误检查的基础设施。 