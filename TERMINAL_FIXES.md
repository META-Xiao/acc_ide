# ACC IDE 终端输入问题修复

## 修复的问题

### ❌ 原问题：
1. **键盘弹出时整个页面向上移动**
2. **只能输入一个字母，第二个字母就卡住**
3. **回车键无法执行命令**

## ✅ 解决方案

### 1. 修复页面移动问题

**问题原因：**
Android 默认的 `windowSoftInputMode` 会在键盘弹出时调整布局。

**解决方案：**
在 `AndroidManifest.xml` 中为 MainActivity 添加：
```xml
android:windowSoftInputMode="adjustNothing"
```

这样键盘弹出时页面不会移动，保持 Termux 风格。

### 2. 修复输入卡顿问题

**问题原因：**
`TextWatcher` 在处理文本变化时可能产生循环调用，导致输入卡顿。

**解决方案：**
```kotlin
private var isUpdating = false

override fun afterTextChanged(s: Editable?) {
    if (isUpdating) return  // 防止循环调用
    // ... 处理逻辑
}
```

### 3. 修复回车执行问题

**问题原因：**
回车键处理逻辑不完善，需要同时处理 `TextWatcher` 和 `OnKeyListener`。

**解决方案：**
```kotlin
// 1. 在 TextWatcher 中检测换行符
if (newText.contains('\n')) {
    isUpdating = true
    val commandText = newText.replace('\n', ' ').trim()
    currentInput = commandText
    executeCurrentCommand()
    binding.hiddenInput.setText("")
    isUpdating = false
}

// 2. 添加专门的按键监听器
binding.hiddenInput.setOnKeyListener { _, keyCode, event ->
    if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
        executeCurrentCommand()
        binding.hiddenInput.text.clear()
        true
    } else {
        false
    }
}
```

### 4. 改进显示更新逻辑

**优化前：**
```kotlin
binding.terminalView.text = beforePrompt + currentInput + "_"
```

**优化后：**
```kotlin
// 移除之前的光标
var cleanText = currentText
if (cleanText.endsWith("_")) {
    cleanText = cleanText.substring(0, cleanText.length - 1)
}

// 更新显示：提示符 + 当前输入 + 光标
val displayText = if (currentInput.isNotEmpty()) {
    beforePrompt + currentInput + "_"
} else {
    beforePrompt + "_"
}
binding.terminalView.text = displayText
```

## 🎯 现在的体验

### ✅ 修复后的行为：
1. **点击终端** → 键盘弹出，页面不移动
2. **输入文字** → 流畅输入，实时显示光标
3. **按回车** → 立即执行命令，清空输入
4. **继续输入** → 可以连续输入多个命令

### ✅ 保持的功能：
- 双指缩放字体大小
- 长按显示菜单
- 命令演示系统
- 自动滚动到底部

## 技术要点

### 防止循环调用
```kotlin
private var isUpdating = false
// 在更新时设置标志位防止递归
```

### 多重回车处理
```kotlin
// 方法1：TextWatcher 检测换行符
if (newText.contains('\n')) { ... }

// 方法2：OnKeyListener 监听回车键
if (keyCode == KeyEvent.KEYCODE_ENTER) { ... }
```

### 安全的文本清空
```kotlin
// 使用 text.clear() 而不是 setText("")
binding.hiddenInput.text.clear()
```

## 测试建议

请测试以下场景：
1. ✅ 点击终端弹出键盘，页面不移动
2. ✅ 连续输入多个字符
3. ✅ 按回车执行命令
4. ✅ 输入多个命令连续执行
5. ✅ 双指缩放功能正常
6. ✅ 长按菜单功能正常

现在的终端应该具有完全类似 Termux 的输入体验！🎉
