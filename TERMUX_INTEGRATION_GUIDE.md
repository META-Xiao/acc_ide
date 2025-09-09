# ACC IDE Termux 集成方案

## 概述

本方案基于对 Termux 和 AndroidIDE 的深入分析，为 ACC IDE 项目提供完整的 Termux 集成方案，避免 SELinux 权限限制并提供完整的 Linux 环境支持。

## 1. 项目结构调整

### 1.1 添加 Termux 模块

在项目根目录创建以下模块结构：

```
acc_ide/
├── app/
├── termux/
│   ├── shared/         # Termux 共享库
│   ├── emulator/       # 终端仿真器
│   ├── view/           # 终端 UI 组件
│   └── application/    # Termux 应用核心
├── settings.gradle.kts
└── build.gradle.kts
```

### 1.2 更新 settings.gradle.kts

```kotlin
include(":app")
include(":termux:shared")
include(":termux:emulator")  
include(":termux:view")
include(":termux:application")
```

## 2. 权限配置

### 2.1 更新 AndroidManifest.xml

在 `app/src/main/AndroidManifest.xml` 中添加必要权限：

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    package="com.acc_ide">

    <!-- Termux 核心权限 -->
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" 
        tools:ignore="ScopedStorage" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    
    <!-- 关键系统权限 -->
    <uses-permission android:name="android.permission.READ_LOGS" />
    <uses-permission android:name="android.permission.DUMP" />
    <uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS" />
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" 
        tools:ignore="ProtectedPermissions" />

    <application
        android:name=".AccIDEApplication"
        android:requestLegacyExternalStorage="true"
        android:extractNativeLibs="true">

        <!-- Termux Service -->
        <service
            android:name="com.termux.app.TermuxService"
            android:exported="false" />

        <!-- 其他组件 -->
        
    </application>
</manifest>
```

## 3. 应用程序类集成

### 3.1 创建 AccIDEApplication.kt

```kotlin
package com.acc_ide

import android.content.Context
import com.termux.app.TermuxApplication
import com.termux.shared.reflection.ReflectionUtils
import com.termux.shared.termux.TermuxBootstrap
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.file.TermuxFileUtils
import com.termux.shared.termux.shell.TermuxShellManager
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties

class AccIDEApplication : TermuxApplication() {

    companion object {
        private const val TERMUX_PACKAGE_VARIANT = "apt-android-7"
        private const val LOG_TAG = "AccIDEApplication"
        
        @JvmStatic
        lateinit var instance: AccIDEApplication
            private set
    }

    override fun onCreate() {
        instance = this
        
        // 绕过隐藏 API 限制
        ReflectionUtils.bypassHiddenAPIReflectionRestrictions()
        
        // 设置 Termux 包变体
        TermuxBootstrap.setTermuxPackageManagerAndVariant(TERMUX_PACKAGE_VARIANT)
        
        super.onCreate()
        
        // 初始化你的 IDE 组件
        initializeIDE()
    }
    
    private fun initializeIDE() {
        // 初始化代码编辑器、语法高亮等 IDE 功能
        // 这里整合您现有的 IDE 初始化逻辑
    }
}
```

### 3.2 更新 build.gradle

```kotlin
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.acc_ide'
    compileSdk 34

    defaultConfig {
        applicationId "com.acc_ide"
        minSdk 24
        targetSdk 34
        versionCode 140
        versionName "1.4.0"

        // Termux 配置
        buildConfigField("String", "TERMUX_PACKAGE_VARIANT", "\"apt-android-7\"")
        
        // 替换包名变量
        manifestPlaceholders["TERMUX_PACKAGE_NAME"] = applicationId
        manifestPlaceholders["TERMUX_APP_NAME"] = "ACC IDE"

        externalNativeBuild {
            cmake {
                cppFlags "-std=c++17"
                abiFilters "arm64-v8a", "armeabi-v7a", "x86_64"
            }
            ndkBuild {
                cFlags "-std=c11", "-Wall", "-Wextra", "-Werror", "-Os", "-fno-stack-protector", "-Wl,--gc-sections"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path file('src/main/cpp/CMakeLists.txt')
            version '3.22.1'
        }
        ndkBuild {
            path file('src/main/cpp/Android.mk')
        }
    }
}

dependencies {
    // 现有依赖
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    
    // Termux 依赖
    implementation project(':termux:shared')
    implementation project(':termux:emulator')
    implementation project(':termux:view')
    implementation project(':termux:application')
    
    // 其他现有依赖...
}
```

## 4. 终端活动集成

### 4.1 创建 TerminalActivity.kt

```kotlin
package com.acc_ide.activities

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.termux.app.TermuxActivity
import com.termux.app.terminal.TermuxTerminalSessionActivityClient
import com.acc_ide.R
import com.acc_ide.terminal.AccIDETerminalSessionClient

class TerminalActivity : TermuxActivity() {

    override val navigationBarColor: Int
        get() = ContextCompat.getColor(this, android.R.color.black)
        
    override val statusBarColor: Int
        get() = ContextCompat.getColor(this, android.R.color.black)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 设置自定义样式
    }

    override fun onCreateTerminalSessionClient(): TermuxTerminalSessionActivityClient {
        return AccIDETerminalSessionClient(this)
    }

    override fun onServiceConnected(componentName: ComponentName?, service: IBinder?) {
        super.onServiceConnected(componentName, service)
        // 确保必要目录存在
        createRequiredDirectories()
    }

    private fun createRequiredDirectories() {
        // 创建必要的目录结构
        val dirs = listOf(
            "/data/data/com.acc_ide/files/usr",
            "/data/data/com.acc_ide/files/usr/bin", 
            "/data/data/com.acc_ide/files/usr/etc",
            "/data/data/com.acc_ide/files/usr/lib",
            "/data/data/com.acc_ide/files/usr/share",
            "/data/data/com.acc_ide/files/usr/tmp",
            "/data/data/com.acc_ide/files/home"
        )
        
        dirs.forEach { dirPath ->
            val dir = java.io.File(dirPath)
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
    }
}
```

### 4.2 创建自定义终端客户端

```kotlin
package com.acc_ide.terminal

import android.content.Context
import com.termux.app.terminal.TermuxTerminalSessionActivityClient
import com.termux.terminal.TerminalSession

class AccIDETerminalSessionClient(
    private val terminalActivity: TerminalActivity
) : TermuxTerminalSessionActivityClient(terminalActivity) {

    override fun onTextChanged(changedSession: TerminalSession) {
        super.onTextChanged(changedSession)
        // 可以在这里添加 IDE 特定的处理逻辑
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
        super.onSessionFinished(finishedSession)
        // 处理会话结束事件
    }
}
```

## 5. 环境配置和路径管理

### 5.1 创建 Environment.kt

```kotlin
package com.acc_ide.utils

import java.io.File

object Environment {
    // 基础路径
    private const val BASE_PATH = "/data/data/com.acc_ide"
    
    // Termux 标准路径
    val FILES_DIR = File("$BASE_PATH/files")
    val PREFIX_DIR = File("$BASE_PATH/files/usr")
    val HOME_DIR = File("$BASE_PATH/files/home")
    val TMP_DIR = File("$BASE_PATH/files/usr/tmp")
    val BIN_DIR = File("$BASE_PATH/files/usr/bin")
    val ETC_DIR = File("$BASE_PATH/files/usr/etc")
    val LIB_DIR = File("$BASE_PATH/files/usr/lib")
    
    // IDE 特定路径
    val PROJECTS_DIR = File("$HOME_DIR/projects")
    val WORKSPACE_DIR = File("$HOME_DIR/workspace") 
    val CONFIG_DIR = File("$HOME_DIR/.acc_ide")
    
    fun initializeDirectories() {
        val dirs = listOf(
            FILES_DIR, PREFIX_DIR, HOME_DIR, TMP_DIR, 
            BIN_DIR, ETC_DIR, LIB_DIR,
            PROJECTS_DIR, WORKSPACE_DIR, CONFIG_DIR
        )
        
        dirs.forEach { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
    }
    
    fun mkdirIfNotExits(dir: File) {
        if (!dir.exists()) {
            dir.mkdirs()
        }
    }
}
```

## 6. 主活动中集成终端

### 6.1 更新 MainActivity.kt

```kotlin
package com.acc_ide

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.acc_ide.activities.TerminalActivity
import com.acc_ide.fragments.TerminalFragment

class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // 初始化环境
        Environment.initializeDirectories()
    }
    
    private fun openTerminal() {
        // 方式1：在新活动中打开终端
        startActivity(Intent(this, TerminalActivity::class.java))
        
        // 方式2：在 Fragment 中嵌入终端
        // val fragment = TerminalFragment()
        // supportFragmentManager.beginTransaction()
        //     .replace(R.id.fragment_container, fragment)
        //     .commit()
    }
}
```

## 7. Bootstrap 包集成

### 7.1 添加 Bootstrap 包

在 `app/src/main/assets/` 目录下添加 bootstrap 包：

```
app/src/main/assets/
└── bootstrap/
    └── bootstrap-arm64-v8a.zip  # ARM64 架构的 bootstrap 包
    └── bootstrap-armeabi-v7a.zip # ARM32 架构的 bootstrap 包
```

### 7.2 Bootstrap 安装器

```kotlin
package com.acc_ide.bootstrap

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class BootstrapInstaller(private val context: Context) {

    suspend fun install(): Boolean = withContext(Dispatchers.IO) {
        try {
            val arch = getDeviceArch()
            val bootstrapAsset = "bootstrap/bootstrap-$arch.zip"
            
            context.assets.open(bootstrapAsset).use { inputStream ->
                ZipInputStream(inputStream).use { zipStream ->
                    extractBootstrap(zipStream)
                }
            }
            
            setupPermissions()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    private fun getDeviceArch(): String {
        return when (android.os.Build.CPU_ABI) {
            "arm64-v8a" -> "arm64-v8a"
            "armeabi-v7a" -> "armeabi-v7a"
            else -> "arm64-v8a" // 默认
        }
    }
    
    private fun extractBootstrap(zipStream: ZipInputStream) {
        var entry = zipStream.nextEntry
        while (entry != null) {
            val file = File(Environment.PREFIX_DIR, entry.name)
            
            if (entry.isDirectory) {
                file.mkdirs()
            } else {
                file.parentFile?.mkdirs()
                FileOutputStream(file).use { output ->
                    zipStream.copyTo(output)
                }
            }
            
            entry = zipStream.nextEntry
        }
    }
    
    private fun setupPermissions() {
        // 设置可执行权限
        File(Environment.BIN_DIR, "sh").setExecutable(true)
        File(Environment.BIN_DIR, "bash").setExecutable(true)
        // 其他必要的权限设置...
    }
}
```

## 8. IDE 与终端的集成

### 8.1 创建集成服务

```kotlin
package com.acc_ide.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TerminalIntegrationService {
    
    private val scope = CoroutineScope(Dispatchers.Main)
    
    fun executeCommand(command: String, callback: (String) -> Unit) {
        scope.launch {
            // 在终端中执行命令并返回结果
            val result = executeInTerminal(command)
            callback(result)
        }
    }
    
    private suspend fun executeInTerminal(command: String): String {
        // 通过 Termux Service 执行命令
        // 这里需要与 TermuxService 交互
        return ""
    }
    
    fun compileProject() {
        executeCommand("cd /data/data/com.acc_ide/files/home/projects && make") { result ->
            // 处理编译结果
        }
    }
    
    fun installPackage(packageName: String) {
        executeCommand("pkg install $packageName") { result ->
            // 处理安装结果
        }
    }
}
```

## 9. 初始化流程

### 9.1 首次启动初始化

```kotlin
package com.acc_ide.initialization

import android.content.Context
import com.acc_ide.bootstrap.BootstrapInstaller
import com.acc_ide.utils.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class InitializationManager(private val context: Context) {
    
    private val scope = CoroutineScope(Dispatchers.Main)
    
    fun performFirstTimeSetup(callback: (Boolean) -> Unit) {
        scope.launch {
            try {
                // 1. 创建目录结构
                Environment.initializeDirectories()
                
                // 2. 安装 Bootstrap 包
                val installer = BootstrapInstaller(context)
                val bootstrapSuccess = installer.install()
                
                if (!bootstrapSuccess) {
                    callback(false)
                    return@launch
                }
                
                // 3. 设置环境变量
                setupEnvironmentVariables()
                
                // 4. 安装基础工具
                installBasicTools()
                
                callback(true)
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false)
            }
        }
    }
    
    private fun setupEnvironmentVariables() {
        // 设置必要的环境变量
    }
    
    private fun installBasicTools() {
        // 安装 git, make, gcc 等基础工具
    }
}
```

## 10. 使用指南

### 10.1 在您的 IDE 中集成终端

1. **在侧边栏添加终端标签**：
   ```kotlin
   // 在您的侧边栏中添加终端选项
   binding.sidebarTerminal.setOnClickListener {
       openTerminal()
   }
   ```

2. **在底部面板嵌入终端**：
   ```kotlin
   // 创建终端 Fragment 并嵌入到底部面板
   val terminalFragment = TerminalFragment()
   supportFragmentManager.beginTransaction()
       .replace(R.id.bottom_panel, terminalFragment)
       .commit()
   ```

3. **执行编译命令**：
   ```kotlin
   terminalIntegrationService.executeCommand("gcc main.cpp -o main") { result ->
       // 显示编译结果
       showCompileResult(result)
   }
   ```

## 注意事项

1. **SELinux 兼容性**：确保所有操作都在应用的私有数据目录中进行
2. **权限申请**：在运行时申请必要的权限，特别是存储权限
3. **架构支持**：确保为目标架构提供相应的 native 库
4. **性能优化**：终端操作应在后台线程中执行
5. **错误处理**：添加适当的错误处理和用户反馈

通过以上方案，您可以在 ACC IDE 中完整集成 Termux 功能，提供完整的 Linux 开发环境，同时避免 SELinux 权限限制。
