/*
 * This file is part of AccIDE.
 *
 * AccIDE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AccIDE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AccIDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.termux.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.button.MaterialButton;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.view.TerminalView;
import java.io.File;
import java.util.ArrayList;

/**
 * Main Termux Activity for AccIDE
 * Provides integrated terminal functionality within the IDE
 */
public class TermuxActivity extends AppCompatActivity {

    private static final String TAG = "TermuxActivity";
    
    // Intent extras for session configuration
    public static final String EXTRA_SESSION_WORKING_DIR = "TERMUX_ACTIVITY.EXTRA_SESSION_WORKING_DIR";
    public static final String EXTRA_SESSION_NAME = "TERMUX_ACTIVITY.EXTRA_SESSION_NAME";
    public static final String EXTRA_FAILSAFE_SESSION = "TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION";

    public TerminalView mTerminalView;
    public DrawerLayout mDrawer;
    protected TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;
    protected TermuxTerminalViewClient mTermuxTerminalViewClient;
    private TermuxSessionsListViewController mTermuxSessionListViewController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "TermuxActivity starting...");
        
        // 使用新的 TermuxInstaller 检查并安装 bootstrap
        TermuxInstaller.setupBootstrapIfNeeded(this, () -> {
            // Bootstrap 安装完成后，初始化 Termux
            runOnUiThread(() -> initializeAfterBootstrap());
        });
    }
    
    private void initializeAfterBootstrap() {
        try {
            // 初始化基础Termux环境（创建目录结构等）
            TermuxBootstrap.setupBootstrapIfNeeded(this);
            
            // 使用真正的termux布局
            setContentView(com.termux.R.layout.activity_termux);
            
            // 初始化终端视图和客户端
        setTermuxTerminalViewAndClients();

            // 初始化抽屉和会话列表
            setTermuxSessionsListView();
            
            // 初始化按钮
            setTerminalToolbarButtons();
            
            Log.d(TAG, "TermuxActivity layout initialized successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to set termux layout, falling back to simple view", e);
            // 如果布局有问题，回退到简单布局
            setContentView(createFallbackView());
        }
        
        // Extract intent extras
        Intent intent = getIntent();
        String workingDir = intent.getStringExtra(EXTRA_SESSION_WORKING_DIR);
        String sessionName = intent.getStringExtra(EXTRA_SESSION_NAME);
        boolean isFailsafe = intent.getBooleanExtra(EXTRA_FAILSAFE_SESSION, false);
        
        Log.d(TAG, "Session configuration:");
        Log.d(TAG, "  Working Directory: " + workingDir);
        Log.d(TAG, "  Session Name: " + sessionName);
        Log.d(TAG, "  Failsafe Mode: " + isFailsafe);
        
        // Set the activity title
        if (sessionName != null) {
            setTitle(sessionName);
        } else {
            setTitle("AccIDE Terminal");
        }
        
        Log.d(TAG, "TermuxActivity initialized successfully");
    }
    
    private android.view.View createFallbackView() {
        android.widget.TextView textView = new android.widget.TextView(this);
        textView.setText("AccIDE Terminal (Fallback)\n\nTerminal view loading...");
        textView.setPadding(32, 32, 32, 32);
        textView.setTextSize(16);
        return textView;
    }

    private void setTermuxTerminalViewAndClients() {
        // 创建会话客户端
        mTermuxTerminalSessionActivityClient = new TermuxTerminalSessionActivityClient(this);
        mTermuxTerminalViewClient = new TermuxTerminalViewClient(this, mTermuxTerminalSessionActivityClient);

        // 获取TerminalView并设置客户端
        mTerminalView = findViewById(com.termux.R.id.terminal_view);
        if (mTerminalView != null) {
            // 首先设置字体大小来初始化渲染器
            mTerminalView.setTextSize(18);
            
            // 然后设置AgaveNerdFont字体
            try {
                android.graphics.Typeface agaveFont = android.graphics.Typeface.createFromAsset(
                    getAssets(), "fonts/AgaveNerdFontMono-Regular.ttf");
                mTerminalView.setTypeface(agaveFont);
                Log.d(TAG, "AgaveNerdFont typeface set successfully");
            } catch (Exception e) {
                Log.w(TAG, "Failed to load AgaveNerdFont, using default", e);
            }
            
            // 设置焦点属性
            mTerminalView.setFocusable(true);
            mTerminalView.setFocusableInTouchMode(true);
            
            // 然后设置客户端
            mTerminalView.setTerminalViewClient(mTermuxTerminalViewClient);
            Log.d(TAG, "TerminalView initialized successfully");
        } else {
            Log.e(TAG, "Failed to find TerminalView in layout");
            return;
        }

        // 初始化客户端
        if (mTermuxTerminalViewClient != null) {
            mTermuxTerminalViewClient.onCreate();
        }
        if (mTermuxTerminalSessionActivityClient != null) {
            mTermuxTerminalSessionActivityClient.onCreate();
    }

        // 创建并启动一个基本的终端会话
        createInitialTerminalSession();
    }

    private void setTermuxSessionsListView() {
        // 获取抽屉布局
        mDrawer = findViewById(com.termux.R.id.drawer_layout);
        
        // 设置会话列表
        ListView termuxSessionsListView = findViewById(com.termux.R.id.terminal_sessions_list);
        if (termuxSessionsListView != null) {
            // 创建一个示例会话列表
            ArrayList<String> sessions = new ArrayList<>();
            sessions.add("AccIDE Terminal");
            
            mTermuxSessionListViewController = new TermuxSessionsListViewController(this, sessions);
        termuxSessionsListView.setAdapter(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemClickListener(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemLongClickListener(mTermuxSessionListViewController);
            
            Log.d(TAG, "Sessions list view initialized");
        } else {
            Log.e(TAG, "Failed to find sessions list view");
        }
    }

    private void setTerminalToolbarButtons() {
        // 获取切换键盘按钮
        MaterialButton toggleKeyboardButton = findViewById(com.termux.R.id.toggle_keyboard_button);
        if (toggleKeyboardButton != null) {
            toggleKeyboardButton.setOnClickListener(v -> {
                Log.d(TAG, "Toggle keyboard button clicked");
                // TODO: 实现键盘切换逻辑
                Toast.makeText(this, "Keyboard toggle", Toast.LENGTH_SHORT).show();
            });
        }

        // 获取新建会话按钮
        MaterialButton newSessionButton = findViewById(com.termux.R.id.new_session_button);
        if (newSessionButton != null) {
            newSessionButton.setOnClickListener(v -> {
                Log.d(TAG, "New session button clicked");
                if (mTermuxTerminalSessionActivityClient != null) {
                    mTermuxTerminalSessionActivityClient.addNewSession(false, "New Session");
                    // 更新会话列表
                    if (mTermuxSessionListViewController != null) {
                        mTermuxSessionListViewController.addSession("New Session " + System.currentTimeMillis());
                    }
                }
                Toast.makeText(this, "New session created", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void createInitialTerminalSession() {
        try {
            Log.d(TAG, "Creating initial terminal session...");
            
            // 获取Intent传递的参数
            Intent intent = getIntent();
            String workingDir = intent.getStringExtra(EXTRA_SESSION_WORKING_DIR);
            String sessionName = intent.getStringExtra(EXTRA_SESSION_NAME);
            boolean isFailsafe = intent.getBooleanExtra(EXTRA_FAILSAFE_SESSION, false);
            
            // 设置默认值
            if (workingDir == null) {
                // 使用用户的home目录作为默认工作目录
                workingDir = getFilesDir().getAbsolutePath();
            }
            
            // 确保工作目录存在
            File workDir = new File(workingDir);
            if (!workDir.exists()) {
                workDir.mkdirs();
            }
            if (sessionName == null) {
                sessionName = "AccIDE Terminal";
            }
            
            Log.d(TAG, "Creating session: " + sessionName + " in " + workingDir);
            
            // 创建一个基本的终端会话
            createTerminalSession(workingDir, sessionName, isFailsafe);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to create initial terminal session", e);
            // 显示一个提示给用户
            android.widget.Toast.makeText(this, 
                "Terminal session initialization failed: " + e.getMessage(), 
                android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void createTerminalSession(String workingDir, String sessionName, boolean isFailsafe) {
        Log.d(TAG, "Creating terminal session with working dir: " + workingDir);
        
        try {
            // 这里我们创建一个真正的终端会话
            // 在完整实现中，这里会创建真正的shell进程
            
            // 创建一个基本的终端会话对象
            com.termux.terminal.TerminalSession session = createBasicTerminalSession(workingDir, sessionName);
            
            if (session != null && mTerminalView != null) {
                // 将会话附加到TerminalView - 这必须在initializeEmulator之前
                boolean attached = mTerminalView.attachSession(session);
                if (attached) {
                    Log.d(TAG, "Terminal session attached successfully");
                    
                    // 设置终端视图的文本大小来初始化渲染器 - 使用DIP计算合适的字体大小
                    int fontSizeInPixels = getDefaultFontSize();
                    mTerminalView.setTextSize(fontSizeInPixels);
                    Log.d(TAG, "TerminalView text size set to: " + fontSizeInPixels + " pixels");
                    
                    // 初始化终端仿真器（这会启动shell进程）- 在attach之后调用
                    // 使用默认的终端大小
                    session.initializeEmulator(80, 24);
                    
                    Log.d(TAG, "Terminal emulator initialized");
                } else {
                    Log.e(TAG, "Failed to attach session to TerminalView");
                }
            } else {
                Log.e(TAG, "Session or TerminalView is null");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating terminal session", e);
        }
    }

    private com.termux.terminal.TerminalSession createBasicTerminalSession(String workingDir, String sessionName) {
        try {
            Log.d(TAG, "Creating real Termux terminal session with JNI");
            
            // 设置真正的shell路径 - 优先使用Termux的shell，但要确保可执行
            String termuxPrefix = TermuxBootstrap.getTermuxPrefix(this);
            String termuxHome = TermuxBootstrap.getTermuxHome(this);
            String shellPath = "/system/bin/sh"; // 默认fallback
            
            // 确保termux home目录存在
            File termuxHomeDir = new File(termuxHome);
            if (!termuxHomeDir.exists()) {
                termuxHomeDir.mkdirs();
            }
            
            // 使用AndroidIDE的方法：直接使用系统shell，不依赖login脚本
            // AndroidIDE注释说："Do not start a login shell since ~/.profile may cause startup failure"
            shellPath = "/system/bin/sh";
            Log.d(TAG, "Using system shell directly (AndroidIDE approach): " + shellPath);
            
            Log.d(TAG, "Using shell: " + shellPath);
            
            // 设置命令参数 - 遵循AndroidIDE的模式
            // 第一个参数是进程名（显示在ps中的名称）
            String processName = "sh"; // 简单的进程名
            String[] args = new String[] { 
                processName  // 这是argv[0]，即进程名称
            };
            
            // 设置完整的环境变量 - 包含termux和系统路径
            // 关键修复：将termux prefix bin目录添加到PATH前面，这样termux工具优先
            String termuxPath = termuxPrefix + "/bin:" + termuxPrefix + "/bin/applets";
            String systemPath = "/system/bin:/system/xbin:/vendor/bin";
            String fullPath = termuxPath + ":" + systemPath;
            
            String[] environment = new String[] {
                "TERM=xterm-256color",
                "HOME=" + termuxHome,
                "PATH=" + fullPath, // 修复：包含termux bin目录，让pkg/apt/g++等命令可以找到
                "PREFIX=" + termuxPrefix,
                "TMPDIR=" + termuxPrefix + "/tmp",
                "ANDROID_DATA=/data",
                "ANDROID_ROOT=/system",
                "LANG=en_US.UTF-8", 
                "PWD=" + termuxHome,
                "SHELL=" + shellPath,
                "PS1=$ ", // 简化提示符
                "LD_LIBRARY_PATH=" + termuxPrefix + "/lib:/system/lib64:/system/lib:/vendor/lib64:/vendor/lib",
                "ANDROID_BOOTLOGO=1",
                "EXTERNAL_STORAGE=/sdcard",
                // 添加更多termux环境变量
                "COLORTERM=truecolor",
                "TERMUX_VERSION=1.3.1" // 使用固定版本号，避免复杂的获取逻辑
            };
            
            // 设置转录行数（历史记录）
            Integer transcriptRows = 2000;
            
            // 使用Termux内部存储作为工作目录，确保有正确的权限
            String workingDirectory = termuxHome; // 直接使用termux home目录
            File homeDir = new File(workingDirectory);
            if (!homeDir.exists()) {
                homeDir.mkdirs();
            }
            
            // 创建真正的TerminalSession - 这将使用JNI createSubprocess
            com.termux.terminal.TerminalSession session = new com.termux.terminal.TerminalSession(
                shellPath, 
                workingDirectory, // 使用内部存储确保权限正确
                args, 
                environment, 
                transcriptRows, 
                mTermuxTerminalSessionActivityClient
            );
            
            // 设置会话名称
            session.mSessionName = sessionName;
            
            Log.d(TAG, "Real TerminalSession created successfully: " + sessionName);
            return session;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to create basic terminal session", e);
            return null;
        }
    }

    /**
     * Get the terminal view for keyboard input handling
     */
    public com.termux.view.TerminalView getTerminalView() {
        return mTerminalView;
    }
    
    /**
     * Calculate default font size based on screen density like AndroidIDE does
     */
    private int getDefaultFontSize() {
        float dipInPixels = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, 1, 
            getResources().getDisplayMetrics());
        
        // Use AndroidIDE's logic: 12 DIP as default, make it even
        int defaultFontSize = Math.round(12 * dipInPixels);
        if (defaultFontSize % 2 == 1) defaultFontSize--;
        
        Log.d(TAG, "Calculated font size: " + defaultFontSize + " pixels (DIP factor: " + dipInPixels + ")");
        return defaultFontSize;
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "TermuxActivity destroying...");
        super.onDestroy();
    }
}
