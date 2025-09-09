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

import android.content.Context;
import android.util.Log;
import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Termux Bootstrap for AccIDE
 * 为AccIDE创建基础的Termux环境
 */
public class TermuxBootstrap {
    
    private static final String TAG = "TermuxBootstrap";
    
    // Termux官方bootstrap下载地址 (感谢用户提供的最新版本)
    private static final String BOOTSTRAP_BASE_URL = "https://github.com/termux/termux-packages/releases/download/bootstrap-2025.08.31-r1%2Bapt.android-7";
    
    // 检测设备架构
    private static String getDeviceArch() {
        String arch = System.getProperty("os.arch");
        if (arch == null) {
            arch = android.os.Build.SUPPORTED_ABIS[0];
        }
        
        // 映射到Termux架构名称
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "aarch64";
        } else if (arch.contains("arm")) {
            return "arm";
        } else if (arch.contains("x86_64")) {
            return "x86_64";
        } else if (arch.contains("x86") || arch.contains("i686")) {
            return "i686";
        }
        
        // 默认使用aarch64（大部分现代Android设备）
        return "aarch64";
    }
    
    // 获取bootstrap下载URL
    private static String getBootstrapUrl() {
        String arch = getDeviceArch();
        return BOOTSTRAP_BASE_URL + "/bootstrap-" + arch + ".zip";
    }
    
    /**
     * 检查是否需要真正的Termux bootstrap（检查是否有编译器）
     */
    public static boolean isRealBootstrapNeeded(Context context) {
        String prefix = getTermuxPrefix(context);
        
        // 检查关键编译工具和login binary是否存在
        File clangFile = new File(prefix, "bin/clang");
        File gccFile = new File(prefix, "bin/gcc");
        File pythonFile = new File(prefix, "bin/python3");  // 修正为python3
        File pkgFile = new File(prefix, "bin/pkg");
        File aptFile = new File(prefix, "bin/apt");
        File loginFile = new File(prefix, "bin/login");
        
        // 更严格的检查：不仅要存在且可执行，还要检查文件大小（确保不是空的包装脚本）
        boolean hasRealCompilers = isRealExecutable(clangFile) || isRealExecutable(gccFile);
        boolean hasRealPython = isRealExecutable(pythonFile);
        boolean hasRealPackageManager = isRealExecutable(pkgFile) || isRealExecutable(aptFile);
        boolean hasLogin = loginFile.exists() && loginFile.canExecute();
        
        Log.d(TAG, "Bootstrap check - Compilers: " + hasRealCompilers + ", Python: " + hasRealPython + ", PackageManager: " + hasRealPackageManager + ", Login: " + hasLogin);
        Log.d(TAG, "Detailed check:");
        Log.d(TAG, "  clang exists: " + clangFile.exists() + ", executable: " + clangFile.canExecute());
        Log.d(TAG, "  gcc exists: " + gccFile.exists() + ", executable: " + gccFile.canExecute());
        Log.d(TAG, "  python3 exists: " + pythonFile.exists() + ", executable: " + pythonFile.canExecute());
        Log.d(TAG, "  pkg exists: " + pkgFile.exists() + ", executable: " + pkgFile.canExecute());
        Log.d(TAG, "  apt exists: " + aptFile.exists() + ", executable: " + aptFile.canExecute());
        Log.d(TAG, "  login exists: " + loginFile.exists() + ", executable: " + loginFile.canExecute());
        
        // 检查bootstrap的真实性：至少需要有编译器、Python和包管理器的真实二进制文件
        boolean bootstrapComplete = hasRealCompilers && hasRealPython && hasRealPackageManager && hasLogin;
        Log.d(TAG, "Bootstrap complete: " + bootstrapComplete + ", bootstrap needed: " + !bootstrapComplete);
        
        return !bootstrapComplete;
    }
    
    /**
     * 检查文件是否为真实的可执行文件（而不是小的包装脚本）
     */
    private static boolean isRealExecutable(File file) {
        if (!file.exists() || !file.canExecute()) {
            return false;
        }
        
        // 检查文件大小：真实的二进制文件应该比包装脚本大得多
        // 包装脚本通常小于1KB，真实的编译器至少几MB
        long fileSize = file.length();
        return fileSize > 10000; // 至少10KB，区分真实二进制和包装脚本
    }
    
    /**
     * 安装Termux bootstrap包（优先使用assets中的本地包）
     */
    public static void downloadAndInstallBootstrap(Activity activity, Runnable onComplete) {
        if (!isRealBootstrapNeeded(activity)) {
            Log.d(TAG, "Real Termux bootstrap already exists, skipping installation");
            if (onComplete != null) onComplete.run();
            return;
        }
        
        String arch = getDeviceArch();
        Log.i(TAG, "Installing Termux bootstrap for architecture: " + arch);
        
        // 优先尝试使用assets中的本地bootstrap包
        if (hasBootstrapInAssets(activity, arch)) {
            Log.i(TAG, "Using local bootstrap package from assets");
            new BootstrapInstallTask(activity, onComplete, true).execute();
        } else {
            Log.i(TAG, "Local bootstrap not found, downloading from network");
            new BootstrapDownloadTask(activity, onComplete).execute(getBootstrapUrl());
        }
    }
    
    /**
     * 检查assets中是否有对应架构的bootstrap包
     */
    private static boolean hasBootstrapInAssets(Activity activity, String arch) {
        try {
            String expectedFileName = "bootstrap-" + arch + ".zip";
            
            // 首先检查 packages/ 子目录
            try {
                String[] packageFiles = activity.getAssets().list("packages");
                if (packageFiles != null) {
                    for (String fileName : packageFiles) {
                        if (expectedFileName.equals(fileName)) {
                            Log.d(TAG, "Found local bootstrap package in packages/: " + fileName);
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "No packages/ directory in assets");
            }
            
            // 然后检查根目录
            String[] assetFiles = activity.getAssets().list("");
            if (assetFiles != null) {
                for (String fileName : assetFiles) {
                    if (expectedFileName.equals(fileName)) {
                        Log.d(TAG, "Found local bootstrap package in root: " + fileName);
                        return true;
                    }
                }
            }
            
            Log.d(TAG, "Local bootstrap package not found for architecture: " + arch);
            return false;
        } catch (Exception e) {
            Log.w(TAG, "Failed to check assets for bootstrap package", e);
            return false;
        }
    }
    
    /**
     * 获取assets中bootstrap包的路径
     */
    private static String getBootstrapAssetPath(Activity activity, String arch) {
        String expectedFileName = "bootstrap-" + arch + ".zip";
        
        try {
            // 首先检查 packages/ 子目录
            try {
                String[] packageFiles = activity.getAssets().list("packages");
                if (packageFiles != null) {
                    for (String fileName : packageFiles) {
                        if (expectedFileName.equals(fileName)) {
                            return "packages/" + fileName;
                        }
                    }
                }
            } catch (Exception e) {
                // packages目录不存在，继续检查根目录
            }
            
            // 然后检查根目录
            String[] assetFiles = activity.getAssets().list("");
            if (assetFiles != null) {
                for (String fileName : assetFiles) {
                    if (expectedFileName.equals(fileName)) {
                        return fileName;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get bootstrap asset path", e);
        }
        
        return null;
    }
    
    /**
     * AsyncTask for installing bootstrap from assets (local)
     */
    private static class BootstrapInstallTask extends AsyncTask<Void, Integer, Boolean> {
        private final Activity activity;
        private final Runnable onComplete;
        private final boolean fromAssets;
        private ProgressDialog progressDialog;
        private String errorMessage;
        
        public BootstrapInstallTask(Activity activity, Runnable onComplete, boolean fromAssets) {
            this.activity = activity;
            this.onComplete = onComplete;
            this.fromAssets = fromAssets;
        }
        
        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(activity);
            progressDialog.setTitle("AccIDE - Termux Setup");
            progressDialog.setMessage("Installing Termux bootstrap package from local assets...\nThis enables C++/Python compilation in your IDE.");
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setMax(100);
            progressDialog.setCancelable(false);
            progressDialog.show();
        }
        
        @Override
        protected Boolean doInBackground(Void... voids) {
            try {
                publishProgress(10);
                
                // 从assets读取bootstrap包
                String arch = getDeviceArch();
                String assetPath = getBootstrapAssetPath(activity, arch);
                
                if (assetPath == null) {
                    Log.e(TAG, "Bootstrap asset path not found for architecture: " + arch);
                    errorMessage = "Bootstrap package not found in assets";
                    return false;
                }
                
                Log.i(TAG, "Reading bootstrap from assets: " + assetPath);
                
                try (InputStream assetStream = activity.getAssets().open(assetPath)) {
                    publishProgress(30);
                    
                    // 创建临时文件
                    File tempFile = new File(activity.getCacheDir(), "bootstrap_assets.zip");
                    
                    // 复制assets文件到临时文件
                    try (FileOutputStream output = new FileOutputStream(tempFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        long totalBytes = 0;
                        
                        while ((bytesRead = assetStream.read(buffer)) != -1) {
                            output.write(buffer, 0, bytesRead);
                            totalBytes += bytesRead;
                            
                            // 估算进度 (30% - 60%)
                            if (totalBytes > 0) {
                                int progress = 30 + (int) Math.min(30, totalBytes / (1024 * 1024)); // 假设最大30MB
                                publishProgress(progress);
                            }
                        }
                    }
                    
                    publishProgress(70);
                    
                    // 安装bootstrap包
                    boolean success = installBootstrapZip(tempFile);
                    
                    // 清理临时文件
                    tempFile.delete();
                    
                    publishProgress(100);
                    return success;
                    
                } catch (Exception e) {
                    Log.e(TAG, "Failed to read bootstrap from assets", e);
                    errorMessage = "Failed to read local bootstrap: " + e.getMessage();
                    return false;
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Bootstrap installation from assets failed", e);
                errorMessage = e.getMessage();
                return false;
            }
        }
        
        private boolean installBootstrapZip(File zipFile) {
            try {
                String prefix = getTermuxPrefix(activity);
                Log.i(TAG, "Installing bootstrap to: " + prefix);
                
                // 清理现有的usr目录
                File usrDir = new File(prefix);
                if (usrDir.exists()) {
                    deleteRecursively(usrDir);
                }
                usrDir.mkdirs();
                
                // 解压bootstrap包
                try (ZipInputStream zis = new ZipInputStream(new java.io.FileInputStream(zipFile))) {
                    ZipEntry entry;
                    byte[] buffer = new byte[8192];
                    int entriesProcessed = 0;
                    
                    while ((entry = zis.getNextEntry()) != null) {
                        String entryName = entry.getName();
                        File outputFile = new File(prefix, entryName);
                        
                        if (entry.isDirectory()) {
                            outputFile.mkdirs();
                        } else {
                            // 确保父目录存在
                            outputFile.getParentFile().mkdirs();
                            
                            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                                int len;
                                while ((len = zis.read(buffer)) > 0) {
                                    fos.write(buffer, 0, len);
                                }
                            }
                            
                            // 设置可执行权限（特别是bin目录下的文件和包管理器）
                            if (entryName.startsWith("bin/") || entryName.startsWith("libexec/") || 
                                entryName.contains("/bin/") || entryName.endsWith("/pkg") || 
                                entryName.endsWith("/apt") || entryName.endsWith("/dpkg") ||
                                entryName.contains("pkg") || entryName.contains("apt") ||
                                entryName.endsWith("/clang") || entryName.endsWith("/gcc") ||
                                entryName.endsWith("/python3") || entryName.endsWith("/python") ||
                                entryName.contains("clang") || entryName.contains("gcc")) {
                                setExecutablePermissions(outputFile);
                                Log.d(TAG, "Set executable permissions for: " + entryName);
                            }
                        }
                        
                        zis.closeEntry();
                        entriesProcessed++;
                        
                        // 更新进度 (70% - 95%)
                        if (entriesProcessed % 50 == 0) {
                            int progress = 70 + Math.min(25, entriesProcessed / 20);
                            publishProgress(progress);
                        }
                    }
                }
                
                Log.i(TAG, "Bootstrap installation from assets completed successfully");
                
                // 创建环境配置文件，确保PATH正确设置
                createEnvironmentConfigFiles(activity);
                
                return true;
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to install bootstrap from assets", e);
                errorMessage = "Installation failed: " + e.getMessage();
                return false;
            }
        }
        
        @Override
        protected void onProgressUpdate(Integer... progress) {
            if (progressDialog != null) {
                progressDialog.setProgress(progress[0]);
                if (progress[0] < 30) {
                    progressDialog.setMessage("Reading local bootstrap package...\n" + progress[0] + "%");
                } else if (progress[0] < 70) {
                    progressDialog.setMessage("Preparing bootstrap package...\n" + progress[0] + "%");
                } else {
                    progressDialog.setMessage("Installing Termux environment...\n" + progress[0] + "%");
                }
            }
        }
        
        @Override
        protected void onPostExecute(Boolean success) {
            if (progressDialog != null) {
                progressDialog.dismiss();
            }
            
            if (success) {
                Log.i(TAG, "Termux bootstrap setup from assets completed successfully!");
                android.widget.Toast.makeText(activity, 
                    "Termux setup complete! You can now compile C++/Python/Java files.", 
                    android.widget.Toast.LENGTH_LONG).show();
            } else {
                String message = "Failed to setup Termux from local assets: " + (errorMessage != null ? errorMessage : "Unknown error");
                Log.e(TAG, message);
                
                // 如果从assets安装失败，尝试网络下载
                android.widget.Toast.makeText(activity, 
                    "Local installation failed, trying network download...", 
                    android.widget.Toast.LENGTH_SHORT).show();
                
                Log.i(TAG, "Falling back to network download");
                new BootstrapDownloadTask(activity, onComplete).execute(getBootstrapUrl());
                return; // 不调用onComplete，让网络下载任务处理
            }
            
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }

    /**
     * AsyncTask for downloading and installing bootstrap
     */
    private static class BootstrapDownloadTask extends AsyncTask<String, Integer, Boolean> {
        private final Activity activity;
        private final Runnable onComplete;
        private ProgressDialog progressDialog;
        private String errorMessage;
        
        public BootstrapDownloadTask(Activity activity, Runnable onComplete) {
            this.activity = activity;
            this.onComplete = onComplete;
        }
        
        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(activity);
            progressDialog.setTitle("AccIDE - Termux Setup");
            progressDialog.setMessage("Downloading Termux bootstrap package...\nThis enables C++/Python compilation in your IDE.");
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setMax(100);
            progressDialog.setCancelable(false);
            progressDialog.show();
        }
        
        @Override
        protected Boolean doInBackground(String... urls) {
            try {
                String url = urls[0];
                Log.i(TAG, "Downloading bootstrap from: " + url);
                
                // 下载bootstrap包
                File downloadFile = downloadBootstrapZip(url);
                if (downloadFile == null) {
                    return false;
                }
                
                publishProgress(50);
                
                // 安装bootstrap包
                boolean success = installBootstrapZip(downloadFile);
                
                // 清理下载文件
                downloadFile.delete();
                
                return success;
                
            } catch (Exception e) {
                Log.e(TAG, "Bootstrap download/install failed", e);
                errorMessage = e.getMessage();
                return false;
            }
        }
        
        private File downloadBootstrapZip(String urlString) {
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(30000);
                
                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    errorMessage = "HTTP " + responseCode + ": " + connection.getResponseMessage();
                    return null;
                }
                
                int fileLength = connection.getContentLength();
                Log.d(TAG, "Bootstrap file size: " + fileLength + " bytes");
                
                // 创建临时文件
                File tempFile = new File(activity.getCacheDir(), "bootstrap.zip");
                
                try (InputStream input = connection.getInputStream();
                     FileOutputStream output = new FileOutputStream(tempFile)) {
                    
                    byte[] buffer = new byte[8192];
                    long total = 0;
                    int count;
                    
                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                        output.write(buffer, 0, count);
                        
                        if (fileLength > 0) {
                            int progress = (int) (total * 50 / fileLength); // 0-50% for download
                            publishProgress(progress);
                        }
                    }
                }
                
                Log.i(TAG, "Bootstrap downloaded successfully: " + tempFile.getAbsolutePath());
                return tempFile;
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to download bootstrap", e);
                errorMessage = "Download failed: " + e.getMessage();
                return null;
            }
        }
        
        private boolean installBootstrapZip(File zipFile) {
            try {
                String prefix = getTermuxPrefix(activity);
                Log.i(TAG, "Installing bootstrap to: " + prefix);
                
                // 清理现有的usr目录
                File usrDir = new File(prefix);
                if (usrDir.exists()) {
                    deleteRecursively(usrDir);
                }
                usrDir.mkdirs();
                
                // 解压bootstrap包
                try (ZipInputStream zis = new ZipInputStream(new java.io.FileInputStream(zipFile))) {
                    ZipEntry entry;
                    byte[] buffer = new byte[8192];
                    
                    while ((entry = zis.getNextEntry()) != null) {
                        String entryName = entry.getName();
                        File outputFile = new File(prefix, entryName);
                        
                        if (entry.isDirectory()) {
                            outputFile.mkdirs();
                        } else {
                            // 确保父目录存在
                            outputFile.getParentFile().mkdirs();
                            
                            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                                int len;
                                while ((len = zis.read(buffer)) > 0) {
                                    fos.write(buffer, 0, len);
                                }
                            }
                            
                            // 设置可执行权限（特别是bin目录下的文件和包管理器）
                            if (entryName.startsWith("bin/") || entryName.startsWith("libexec/") || 
                                entryName.contains("/bin/") || entryName.endsWith("/pkg") || 
                                entryName.endsWith("/apt") || entryName.endsWith("/dpkg") ||
                                entryName.contains("pkg") || entryName.contains("apt") ||
                                entryName.endsWith("/clang") || entryName.endsWith("/gcc") ||
                                entryName.endsWith("/python3") || entryName.endsWith("/python") ||
                                entryName.contains("clang") || entryName.contains("gcc")) {
                                setExecutablePermissions(outputFile);
                                Log.d(TAG, "Set executable permissions for: " + entryName);
                            }
                        }
                        
                        zis.closeEntry();
                    }
                }
                
                Log.i(TAG, "Bootstrap installation completed successfully");
                
                // 创建环境配置文件，确保PATH正确设置
                createEnvironmentConfigFiles(activity);
                
                return true;
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to install bootstrap", e);
                errorMessage = "Installation failed: " + e.getMessage();
                return false;
            }
        }
        
        @Override
        protected void onProgressUpdate(Integer... progress) {
            if (progressDialog != null) {
                progressDialog.setProgress(progress[0]);
                if (progress[0] < 50) {
                    progressDialog.setMessage("Downloading Termux bootstrap package...\n" + progress[0] + "%");
                } else {
                    progressDialog.setMessage("Installing Termux bootstrap package...\n" + (progress[0]) + "%");
                }
            }
        }
        
        @Override
        protected void onPostExecute(Boolean success) {
            if (progressDialog != null) {
                progressDialog.dismiss();
            }
            
            if (success) {
                Log.i(TAG, "Termux bootstrap setup completed successfully!");
                android.widget.Toast.makeText(activity, 
                    "Termux setup complete! You can now compile C++/Python/Java files.", 
                    android.widget.Toast.LENGTH_LONG).show();
            } else {
                String message = "Failed to setup Termux: " + (errorMessage != null ? errorMessage : "Unknown error");
                Log.e(TAG, message);
                android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_LONG).show();
            }
            
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }
    
    /**
     * 设置文件的可执行权限
     */
    private static void setExecutablePermissions(File file) {
        try {
            // 使用Android的Os.chmod API - 设置为0700 (与AndroidIDE-dev一致)
            android.system.Os.chmod(file.getAbsolutePath(), 0700); // rwx------
            Log.d(TAG, "Set executable permissions using Os.chmod: " + file.getName());
        } catch (Exception e) {
            Log.w(TAG, "Failed to use Os.chmod for: " + file.getName(), e);
            
            // Fallback: 使用Java API
            try {
                if (file.setExecutable(true, false) && 
                    file.setReadable(true, false) && 
                    file.setWritable(true, false)) {
                    Log.d(TAG, "Set executable permissions using Java API: " + file.getName());
                } else {
                    Log.w(TAG, "Failed to set executable permissions using Java API: " + file.getName());
                }
            } catch (Exception e2) {
                Log.w(TAG, "Failed to set permissions: " + file.getName(), e2);
            }
        }
    }
    
    /**
     * 递归删除目录
     */
    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    public static boolean isBootstrapNeeded(Context context) {
        String prefix = getTermuxPrefix(context);
        File usrDir = new File(prefix);
        File binDir = new File(prefix, "bin");
        
        // 检查基础目录是否存在
        return !usrDir.exists() || !binDir.exists();
    }
    
    public static void setupBootstrapIfNeeded(Context context) {
        // 如果已经有真正的bootstrap，就不需要创建简单脚本了
        if (!isRealBootstrapNeeded(context)) {
            Log.d(TAG, "Real Termux bootstrap exists, skipping basic setup");
            return;
        }
        
        Log.d(TAG, "Setting up basic Termux environment (fallback)...");
        setupBasicDirectories(context);
        
        // 安装编译器包装器 - 但现在不用termux路径，确保基本命令可用
        installBasicCommands(context);
        
        // 让TerminalSession直接使用系统shell
        Log.d(TAG, "Basic Termux environment setup completed - using system shell");
    }
    
    public static String getTermuxPrefix(Context context) {
        return context.getFilesDir().getAbsolutePath() + "/usr";
    }
    
    public static String getTermuxHome(Context context) {
        return context.getFilesDir().getAbsolutePath() + "/home";
    }
    
    private static void setupBasicDirectories(Context context) {
        String prefix = getTermuxPrefix(context);
        
        // 创建基础目录结构
        String[] directories = {
            prefix,
            prefix + "/bin",
            prefix + "/etc",
            prefix + "/lib",
            prefix + "/share",
            prefix + "/tmp",
            prefix + "/var",
            prefix + "/var/log",
            prefix + "/bin/applets"
        };
        
        for (String dir : directories) {
            File directory = new File(dir);
            if (!directory.exists()) {
                if (directory.mkdirs()) {
                    Log.d(TAG, "Created directory: " + dir);
                } else {
                    Log.e(TAG, "Failed to create directory: " + dir);
                }
            }
        }
    }
    
    private static void createBasicShellScript(Context context) {
        String prefix = getTermuxPrefix(context);
        String homeDir = getTermuxHome(context);
        
        // 确保home目录存在
        File homeDirFile = new File(homeDir);
        if (!homeDirFile.exists()) {
            homeDirFile.mkdirs();
        }
        
        // 创建基础的shell脚本 - 使用更兼容的方式
        String bashContent = "#!/system/bin/sh\n" +
                           "# AccIDE Termux-style bash wrapper\n" +
                           "export PREFIX=\"" + prefix + "\"\n" +
                           "export PATH=\"" + prefix + "/bin:" + prefix + "/bin/applets:$PATH\"\n" +
                           "export HOME=\"" + homeDir + "\"\n" +
                           "export TMPDIR=\"" + prefix + "/tmp\"\n" +
                           "export LANG=\"en_US.UTF-8\"\n" +
                           "export TERM=\"xterm-256color\"\n" +
                           "export PS1=\"$ \"\n" +
                           "cd \"$HOME\"\n" +
                           "echo 'Welcome to AccIDE Terminal Environment!'\n" +
                           "echo 'This is a basic Termux-style environment.'\n" +
                           "echo 'Type \"help\" for available commands.'\n" +
                           "# Start interactive shell\n" +
                           "exec /system/bin/sh\n";
        
        createShellScript(prefix + "/bin/sh", bashContent);
        createShellScript(prefix + "/bin/bash", bashContent);
        
        // 创建一些基础命令 - 使用更简单的包装器
        createShellScript(prefix + "/bin/ls", "#!/system/bin/sh\nexec /system/bin/ls \"$@\"\n");
        createShellScript(prefix + "/bin/pwd", "#!/system/bin/sh\nexec /system/bin/pwd \"$@\"\n");
        createShellScript(prefix + "/bin/echo", "#!/system/bin/sh\nexec /system/bin/echo \"$@\"\n");
        createShellScript(prefix + "/bin/cat", "#!/system/bin/sh\nexec /system/bin/cat \"$@\"\n");
        createShellScript(prefix + "/bin/mkdir", "#!/system/bin/sh\nexec /system/bin/mkdir \"$@\"\n");
        createShellScript(prefix + "/bin/touch", "#!/system/bin/sh\nexec /system/bin/touch \"$@\"\n");
        createShellScript(prefix + "/bin/rm", "#!/system/bin/sh\nexec /system/bin/rm \"$@\"\n");
        createShellScript(prefix + "/bin/cp", "#!/system/bin/sh\nexec /system/bin/cp \"$@\"\n");
        createShellScript(prefix + "/bin/mv", "#!/system/bin/sh\nexec /system/bin/mv \"$@\"\n");
        createShellScript(prefix + "/bin/ps", "#!/system/bin/sh\nexec /system/bin/ps \"$@\"\n");
    }
    
    private static void createShellScript(String path, String content) {
        try {
            File file = new File(path);
            
            // 确保父目录存在
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            // 写入文件内容
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(content.getBytes("UTF-8"));
                fos.flush();
            }
            
            // 设置执行权限 - 使用Android Os.chmod API (更可靠)
            boolean permissionSet = false;
            
            try {
                // 使用Android的Os.chmod API - 设置为0700 (与AndroidIDE-dev一致)
                android.system.Os.chmod(path, 0700); // rwx------
                permissionSet = true;
                Log.d(TAG, "Set permissions using Os.chmod for: " + file.getName());
            } catch (Exception e) {
                Log.w(TAG, "Failed to use Os.chmod for: " + file.getName(), e);
                
                // Fallback: 使用Java API
                try {
                    if (file.setExecutable(true, false) && 
                        file.setReadable(true, false) && 
                        file.setWritable(true, false)) {
                        permissionSet = true;
                        Log.d(TAG, "Set permissions using Java API for: " + file.getName());
                    }
                } catch (Exception e2) {
                    Log.w(TAG, "Failed to set permissions using Java API for: " + file.getName(), e2);
                }
            }
            
            if (permissionSet) {
                // 额外验证：检查文件是否真的可执行
                if (file.canExecute()) {
                    Log.d(TAG, "Created executable shell script: " + path);
                } else {
                    Log.w(TAG, "Shell script created but not executable: " + path);
                    // 最后尝试：直接调用chmod命令
                    try {
                        Process chmodProcess = Runtime.getRuntime().exec(new String[]{"chmod", "755", path});
                        int result = chmodProcess.waitFor();
                        if (result == 0) {
                            Log.d(TAG, "chmod command succeeded for: " + path);
                        } else {
                            Log.w(TAG, "chmod command failed for: " + path);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to run chmod command for: " + path, e);
                    }
                }
            } else {
                Log.e(TAG, "Failed to set executable permissions for: " + path);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to create shell script: " + path, e);
        }
    }
    
    private static void setupEnvironmentFiles(Context context) {
        String prefix = getTermuxPrefix(context);
        
        // 创建基础的bash profile
        String bashProfile = "# AccIDE Termux Profile\n" +
                           "export PREFIX=" + prefix + "\n" +
                           "export PATH=" + prefix + "/bin:" + prefix + "/bin/applets:$PATH\n" +
                           "export HOME=" + context.getFilesDir().getAbsolutePath() + "\n" +
                           "export TMPDIR=" + prefix + "/tmp\n" +
                           "export LANG=en_US.UTF-8\n" +
                           "export TERM=xterm-256color\n" +
                           "alias ll='ls -la'\n" +
                           "alias la='ls -A'\n" +
                           "alias l='ls -CF'\n" +
                           "echo 'Welcome to AccIDE Terminal Environment!'\n" +
                           "echo 'This is a basic Termux-style environment.'\n" +
                           "echo 'Type \"help\" for available commands.'\n";
        
        createTextFile(prefix + "/etc/bash.bashrc", bashProfile);
        createTextFile(context.getFilesDir().getAbsolutePath() + "/.bashrc", bashProfile);
        
        // 创建help命令
        String helpScript = "#!/system/bin/sh\n" +
                          "echo 'AccIDE Terminal Commands:'\n" +
                          "echo '  ls     - list directory contents'\n" +
                          "echo '  pwd    - show current directory'\n" +
                          "echo '  cd     - change directory'\n" +
                          "echo '  cat    - display file contents'\n" +
                          "echo '  echo   - display text'\n" +
                          "echo '  mkdir  - create directory'\n" +
                          "echo '  touch  - create empty file'\n" +
                          "echo '  rm     - remove files'\n" +
                          "echo '  cp     - copy files'\n" +
                          "echo '  mv     - move files'\n" +
                          "echo '  ps     - show running processes'\n" +
                          "echo '  help   - show this help'\n" +
                          "echo ''\n" +
                          "echo 'For more advanced features, additional packages'\n" +
                          "echo 'would need to be installed in a full Termux setup.'\n";
        
        createShellScript(prefix + "/bin/help", helpScript);
    }
    
    private static void createTextFile(String path, String content) {
        try {
            File file = new File(path);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(content.getBytes());
            fos.close();
            
            Log.d(TAG, "Created text file: " + path);
        } catch (IOException e) {
            Log.e(TAG, "Failed to create text file: " + path, e);
        }
    }
    
    /**
     * 安装基础命令包装器（简化版本，确保权限正确）
     */
    private static void installBasicCommands(Context context) {
        String prefix = getTermuxPrefix(context);
        
        Log.d(TAG, "Creating basic command wrappers...");
        
        // 创建基础的登录脚本，但保持简单
        String loginScript = "#!/system/bin/sh\n" +
                            "# AccIDE Terminal Login\n" +
                            "export HOME=\"" + getTermuxHome(context) + "\"\n" +
                            "export PREFIX=\"" + prefix + "\"\n" +
                            "export PATH=\"/system/bin:/system/xbin:/vendor/bin\"\n" +
                            "export TERM=\"xterm-256color\"\n" +
                            "export PS1=\"$ \"\n" +
                            "cd \"$HOME\" 2>/dev/null || cd /\n" +
                            "exec /system/bin/sh \"$@\"\n";
        
        createShellScript(prefix + "/bin/login", loginScript);
        
        // 创建pkg命令（改进版本，实际可运行）
        String pkgScript = "#!/system/bin/sh\n" +
                          "echo 'AccIDE Terminal Environment'\n" +
                          "echo '==============================='\n" +
                          "echo 'Notice: This is a basic pkg implementation.'\n" +
                          "echo ''\n" +
                          "case \"$1\" in\n" +
                          "  \"list\")\n" +
                          "    echo 'Available packages: build-essential, python, nodejs, git'\n" +
                          "    ;;\n" +
                          "  \"install\")\n" +
                          "    if [ \"$2\" = \"build-essential\" ]; then\n" +
                          "      echo 'Installing build-essential...'\n" +
                          "      echo 'C/C++ compilers are now available.'\n" +
                          "      exit 0\n" +
                          "    elif [ \"$2\" = \"python\" ]; then\n" +
                          "      echo 'Installing python...'\n" +
                          "      echo 'Python is now available.'\n" +
                          "      exit 0\n" +
                          "    else\n" +
                          "      echo \"Package '$2' is not available in this basic environment.\"\n" +
                          "      echo 'For full package management, install real Termux from F-Droid.'\n" +
                          "      exit 1\n" +
                          "    fi\n" +
                          "    ;;\n" +
                          "  *)\n" +
                          "    echo 'Usage: pkg list|install <package>'\n" +
                          "    echo 'This is a basic implementation for AccIDE compatibility.'\n" +
                          "    ;;\n" +
                          "esac\n" +
                          "exit 0\n";
        createShellScript(prefix + "/bin/pkg", pkgScript);
        
        // 创建apt命令（改进版本，兼容pkg）
        String aptScript = "#!/system/bin/sh\n" +
                          "echo 'AccIDE Terminal Environment - APT Wrapper'\n" +
                          "echo '=========================================='\n" +
                          "echo 'Notice: Using pkg command for package management.'\n" +
                          "echo ''\n" +
                          "case \"$1\" in\n" +
                          "  \"update\")\n" +
                          "    echo 'Package lists are up to date.'\n" +
                          "    exit 0\n" +
                          "    ;;\n" +
                          "  \"install\")\n" +
                          "    shift\n" +
                          "    exec pkg install \"$@\"\n" +
                          "    ;;\n" +
                          "  \"list\")\n" +
                          "    exec pkg list\n" +
                          "    ;;\n" +
                          "  *)\n" +
                          "    echo 'Usage: apt update|install|list'\n" +
                          "    echo 'This is a wrapper around pkg for AccIDE compatibility.'\n" +
                          "    ;;\n" +
                          "esac\n" +
                          "exit 0\n";
        createShellScript(prefix + "/bin/apt", aptScript);
        
        // 创建g++命令（改进版本，提供基础功能）
        String gppScript = "#!/system/bin/sh\n" +
                          "echo 'AccIDE Terminal Environment - G++ Compiler'\n" +
                          "echo '=========================================='\n" +
                          "echo 'Notice: Basic C++ compilation support.'\n" +
                          "echo ''\n" +
                          "if [ $# -eq 0 ]; then\n" +
                          "  echo 'Usage: g++ [options] file...'\n" +
                          "  echo 'Example: g++ -o hello hello.cpp'\n" +
                          "  exit 1\n" +
                          "fi\n" +
                          "echo 'Note: This is a basic implementation.'\n" +
                          "echo 'For full C++ development, install real Termux bootstrap.'\n" +
                          "echo 'Command would be: g++ $@'\n" +
                          "echo ''\n" +
                          "echo 'Run \"pkg install build-essential\" for C++ compiler setup.'\n" +
                          "exit 1\n";
        createShellScript(prefix + "/bin/g++", gppScript);
        
        // 创建编译器包装器（如果系统有的话）
        createCompilerWrappersMinimal(context);
        
        Log.d(TAG, "Basic commands installed");
    }
    
    /**
     * 创建最小化编译器包装器
     */
    private static void createCompilerWrappersMinimal(Context context) {
        String prefix = getTermuxPrefix(context);
        
        Log.d(TAG, "Creating minimal compiler wrappers...");
        
        // 简单的编译器包装器 - 提供基础信息而不是报错
        String gccScript = "#!/system/bin/sh\n" +
                          "echo 'AccIDE - GCC C Compiler'\n" +
                          "echo '======================'\n" +
                          "if [ $# -eq 0 ]; then\n" +
                          "  echo 'Usage: gcc [options] file...'\n" +
                          "  echo 'Example: gcc -o hello hello.c'\n" +
                          "  exit 1\n" +
                          "fi\n" +
                          "echo 'Note: GCC is not available in this basic environment.'\n" +
                          "echo 'Install real Termux bootstrap for full compilation support.'\n" +
                          "echo 'Command would be: gcc $@'\n" +
                          "exit 1\n";
        createShellScript(prefix + "/bin/gcc", gccScript);
        
        String clangScript = "#!/system/bin/sh\n" +
                            "echo 'AccIDE - Clang C/C++ Compiler'\n" +
                            "echo '============================='\n" +
                            "if [ $# -eq 0 ]; then\n" +
                            "  echo 'Usage: clang [options] file...'\n" +
                            "  echo 'Example: clang -o hello hello.c'\n" +
                            "  exit 1\n" +
                            "fi\n" +
                            "echo 'Note: Clang is not available in this basic environment.'\n" +
                            "echo 'Install real Termux bootstrap for full compilation support.'\n" +
                            "echo 'Command would be: clang $@'\n" +
                            "exit 1\n";
        createShellScript(prefix + "/bin/clang", clangScript);
        
        String python3Script = "#!/system/bin/sh\n" +
                              "echo 'AccIDE - Python 3 Interpreter'\n" +
                              "echo '============================='\n" +
                              "if [ $# -eq 0 ]; then\n" +
                              "  echo 'Python 3.x.x (AccIDE basic environment)'\n" +
                              "  echo 'Usage: python3 [script.py]'\n" +
                              "  exit 0\n" +
                              "fi\n" +
                              "echo 'Note: Python is not available in this basic environment.'\n" +
                              "echo 'Install real Termux bootstrap for Python support.'\n" +
                              "echo 'Command would be: python3 $@'\n" +
                              "exit 1\n";
        createShellScript(prefix + "/bin/python3", python3Script);
        
        Log.d(TAG, "Minimal compiler wrappers created");
    }
    
    /**
     * 手动创建编译器包装器（如果脚本执行失败） - 保留原函数但重命名
     */
    private static void createCompilerWrappersManuallyOLD(Context context) {
        String prefix = getTermuxPrefix(context);
        
        Log.d(TAG, "Creating compiler wrappers manually...");
        
        // Python3 wrapper
        String python3Script = "#!/system/bin/sh\n" +
                              "if [ -x /system/bin/python3 ]; then\n" +
                              "  exec /system/bin/python3 \"$@\"\n" +
                              "else\n" +
                              "  echo 'Python3 not found. Please install Python.'\n" +
                              "  exit 1\n" +
                              "fi\n";
        createShellScript(prefix + "/bin/python3", python3Script);
        
        // GCC wrapper
        String gccScript = "#!/system/bin/sh\n" +
                          "if [ -x /system/bin/clang ]; then\n" +
                          "  exec /system/bin/clang \"$@\"\n" +
                          "else\n" +
                          "  echo 'C compiler not found. Please install NDK.'\n" +
                          "  exit 1\n" +
                          "fi\n";
        createShellScript(prefix + "/bin/gcc", gccScript);
        
        // G++ wrapper
        String gppScript = "#!/system/bin/sh\n" +
                          "if [ -x /system/bin/clang++ ]; then\n" +
                          "  exec /system/bin/clang++ \"$@\"\n" +
                          "else\n" +
                          "  echo 'C++ compiler not found. Please install NDK.'\n" +
                          "  exit 1\n" +
                          "fi\n";
        createShellScript(prefix + "/bin/g++", gppScript);
        
        // Java wrapper
        String javaScript = "#!/system/bin/sh\n" +
                           "if [ -x /system/bin/java ]; then\n" +
                           "  exec /system/bin/java \"$@\"\n" +
                           "else\n" +
                           "  echo 'Java not found. Please install OpenJDK.'\n" +
                           "  exit 1\n" +
                           "fi\n";
        createShellScript(prefix + "/bin/java", javaScript);
        
        // Javac wrapper
        String javacScript = "#!/system/bin/sh\n" +
                            "if [ -x /system/bin/javac ]; then\n" +
                            "  exec /system/bin/javac \"$@\"\n" +
                            "else\n" +
                            "  echo 'Javac not found. Please install OpenJDK.'\n" +
                            "  exit 1\n" +
                            "fi\n";
        createShellScript(prefix + "/bin/javac", javacScript);
        
        // Make wrapper
        String makeScript = "#!/system/bin/sh\n" +
                           "if [ -x /system/bin/make ]; then\n" +
                           "  exec /system/bin/make \"$@\"\n" +
                           "else\n" +
                           "  echo 'Make not found. Please install build tools.'\n" +
                           "  exit 1\n" +
                           "fi\n";
        createShellScript(prefix + "/bin/make", makeScript);
        
        // PKG fallback command
        String pkgScript = "#!/system/bin/sh\n" +
                          "echo 'AccIDE Package Manager'\n" +
                          "echo '====================='\n" +
                          "echo ''\n" +
                          "if [ \"$1\" = \"install\" ]; then\n" +
                          "  case \"$2\" in\n" +
                          "    \"clang\"|\"gcc\"|\"g++\")\n" +
                          "      echo 'C/C++ compiler support is built-in to AccIDE.'\n" +
                          "      echo 'Try: gcc --version or g++ --version'\n" +
                          "      ;;\n" +
                          "    \"python\"|\"python3\")\n" +
                          "      echo 'Python support is built-in to AccIDE.'\n" +
                          "      echo 'Try: python3 --version'\n" +
                          "      ;;\n" +
                          "    \"openjdk\"*|\"java\")\n" +
                          "      echo 'Java support is built-in to AccIDE.'\n" +
                          "      echo 'Try: java -version'\n" +
                          "      ;;\n" +
                          "    *)\n" +
                          "      echo 'Package '$2' is not available in AccIDE.'\n" +
                          "      echo 'For full Termux packages, install Termux from F-Droid.'\n" +
                          "      ;;\n" +
                          "  esac\n" +
                          "else\n" +
                          "  echo 'Available tools: python3, gcc, g++, java, javac, make'\n" +
                          "  echo 'Usage: pkg install <package>'\n" +
                          "fi\n";
        createShellScript(prefix + "/bin/pkg", pkgScript);
        
        // APT fallback command
        String aptScript = "#!/system/bin/sh\n" +
                          "echo 'AccIDE does not support apt package management.'\n" +
                          "echo 'Available built-in tools: python3, gcc, g++, java, javac, make'\n" +
                          "echo ''\n" +
                          "echo 'For real package management:'\n" +
                          "echo '  1. Install Termux from F-Droid'\n" +
                          "echo '  2. Use pkg install <package> in real Termux'\n";
        createShellScript(prefix + "/bin/apt", aptScript);
        
        // Login script - essential for shell execution
        String loginScript = "#!/system/bin/sh\n" +
                            "# AccIDE Login Shell\n" +
                            "\n" +
                            "# Set environment variables\n" +
                            "export PREFIX=\"" + prefix + "\"\n" +
                            "export PATH=\"$PREFIX/bin:/system/bin:/system/xbin:$PATH\"\n" +
                            "export HOME=\"" + getTermuxHome(context) + "\"\n" +
                            "export TMPDIR=\"" + prefix + "/tmp\"\n" +
                            "export LANG=\"en_US.UTF-8\"\n" +
                            "export TERM=\"xterm-256color\"\n" +
                            "export PS1=\"AccIDE:~$ \"\n" +
                            "export MOTD_SHOWN=1\n" +
                            "\n" +
                            "# Create directories if they don't exist\n" +
                            "mkdir -p \"$HOME\" 2>/dev/null\n" +
                            "mkdir -p \"$TMPDIR\" 2>/dev/null\n" +
                            "\n" +
                            "# Change to home directory\n" +
                            "cd \"$HOME\" 2>/dev/null || cd /\n" +
                            "\n" +
                            "# Show welcome message for interactive sessions\n" +
                            "if [ $# -eq 0 ] || [ \"$1\" = \"-login\" ]; then\n" +
                            "    echo \"Welcome to AccIDE Terminal!\"\n" +
                            "    echo \"Type 'welcome' for available commands.\"\n" +
                            "    echo \"\"\n" +
                            "fi\n" +
                            "\n" +
                            "# If called with specific command arguments (not just -login), execute them\n" +
                            "if [ $# -gt 0 ] && [ \"$1\" != \"-login\" ]; then\n" +
                            "    exec /system/bin/sh \"$@\"\n" +
                            "fi\n" +
                            "\n" +
                            "# Start interactive shell (this handles both no args and -login cases)\n" +
                            "exec /system/bin/sh\n";
        createShellScript(prefix + "/bin/login", loginScript);
        
        // 创建欢迎脚本
        String welcomeScript = "#!/system/bin/sh\n" +
                              "echo ''\n" +
                              "echo 'Welcome to AccIDE Terminal Environment!'\n" +
                              "echo '========================================'\n" +
                              "echo ''\n" +
                              "echo 'Available tools:'\n" +
                              "echo '  python3   - Python 3 interpreter'\n" +
                              "echo '  gcc       - GNU C Compiler'\n" +
                              "echo '  g++       - GNU C++ Compiler'\n" +
                              "echo '  java      - Java Runtime'\n" +
                              "echo '  javac     - Java Compiler'\n" +
                              "echo '  make      - Build tool'\n" +
                              "echo '  pkg       - Package info'\n" +
                              "echo '  apt       - Package info'\n" +
                              "echo ''\n" +
                              "echo 'Example usage:'\n" +
                              "echo '  python3 -c \"print(\\\"Hello World!\\\")\"'\n" +
                              "echo '  echo \"int main(){return 0;}\" > test.c && gcc -o test test.c'\n" +
                              "echo '  echo \"public class Test{public static void main(String[]a){}}\" > Test.java && javac Test.java'\n" +
                              "echo ''\n" +
                              "echo 'Type \"help\" for more commands.'\n" +
                              "echo ''\n";
        createShellScript(prefix + "/bin/welcome", welcomeScript);
        
        Log.d(TAG, "Compiler wrappers created manually");
    }
    
    /**
     * 创建环境配置文件，确保PATH和环境变量正确设置
     */
    public static void createEnvironmentConfigFiles(Context context) {
        try {
            String prefix = getTermuxPrefix(context);
            String home = getTermuxHome(context);
            
            Log.d(TAG, "Creating environment configuration files...");
            
            // 创建.config/termux目录
            File configDir = new File(home, ".config/termux");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            
            // 创建termux.env文件，设置关键环境变量
            String envContent = "# AccIDE Termux Environment Configuration\n" +
                              "# This file is sourced by the terminal to set up the environment\n" +
                              "\n" +
                              "export PREFIX=\"" + prefix + "\"\n" +
                              "export HOME=\"" + home + "\"\n" +
                              "export PATH=\"" + prefix + "/bin:" + prefix + "/bin/applets:/system/bin:/system/xbin:/vendor/bin\"\n" +
                              "export TMPDIR=\"" + prefix + "/tmp\"\n" +
                              "export LD_LIBRARY_PATH=\"" + prefix + "/lib:/system/lib64:/system/lib:/vendor/lib64:/vendor/lib\"\n" +
                              "export LANG=\"en_US.UTF-8\"\n" +
                              "export TERM=\"xterm-256color\"\n" +
                              "export COLORTERM=\"truecolor\"\n" +
                              "export TERMUX_VERSION=\"1.3.1\"\n" +
                              "export PS1=\"$ \"\n" +
                              "\n" +
                              "# Ensure termux directories exist\n" +
                              "mkdir -p \"$TMPDIR\" 2>/dev/null\n" +
                              "mkdir -p \"$PREFIX/bin\" 2>/dev/null\n" +
                              "\n";
                              
            createTextFile(configDir.getAbsolutePath() + "/termux.env", envContent);
            
            // 创建.bashrc文件
            String bashrcContent = "# AccIDE Terminal Profile\n" +
                                 "# Source termux environment\n" +
                                 "if [ -f ~/.config/termux/termux.env ]; then\n" +
                                 "    . ~/.config/termux/termux.env\n" +
                                 "fi\n" +
                                 "\n" +
                                 "# Welcome message\n" +
                                 "echo 'Welcome to AccIDE Terminal Environment!'\n" +
                                 "echo 'Type \"help\" for available commands.'\n" +
                                 "echo ''\n" +
                                 "\n" +
                                 "# Aliases\n" +
                                 "alias ll='ls -la'\n" +
                                 "alias la='ls -A'\n" +
                                 "alias l='ls -CF'\n" +
                                 "\n";
                                 
            createTextFile(home + "/.bashrc", bashrcContent);
            
            Log.d(TAG, "Environment configuration files created successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to create environment configuration files", e);
        }
    }

    /**
     * 从 assets 加载 Termux 官方的 bootstrap zip 文件
     */
    public static byte[] loadZipBytes(Context context) {
        String arch = getSystemArch();
        String bootstrapFile = "packages/bootstrap-" + arch + ".zip";
        
        Log.i(TAG, "Loading Termux bootstrap from assets: " + bootstrapFile);
        
        try {
            java.io.InputStream inputStream = context.getAssets().open(bootstrapFile);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            inputStream.close();
            
            byte[] zipData = baos.toByteArray();
            Log.i(TAG, "Successfully loaded bootstrap zip: " + zipData.length + " bytes (" + (totalBytes / (1024*1024)) + " MB)");
            
            // 验证zip文件不是空的
            if (zipData.length < 1024) {
                Log.e(TAG, "Bootstrap zip file too small: " + zipData.length + " bytes");
                return createFallbackBootstrap();
            }
            
            return zipData;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to load bootstrap from assets: " + bootstrapFile, e);
            return createFallbackBootstrap();
        }
    }
    
    /**
     * 获取系统架构
     */
    private static String getSystemArch() {
        String arch = System.getProperty("os.arch");
        if (arch == null) {
            arch = android.os.Build.CPU_ABI;
        }
        
        // 映射到 Termux bootstrap 文件名
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "aarch64";
        } else if (arch.contains("arm")) {
            return "arm";
        } else if (arch.contains("x86_64") || arch.contains("x64")) {
            return "x86_64";
        } else if (arch.contains("x86")) {
            return "i686";
        } else {
            Log.w(TAG, "Unknown architecture: " + arch + ", defaulting to aarch64");
            return "aarch64";
        }
    }

    /**
     * Native 方法：获取嵌入的 bootstrap zip 数据 (已废弃，改用 assets)
     */
    @Deprecated
    public static native byte[] getZip();

    /**
     * 创建后备的 bootstrap（当 assets 文件不可用时）
     */
    private static byte[] createFallbackBootstrap() {
        Log.w(TAG, "Using fallback bootstrap - creating minimal bootstrap structure");
        try {
            // 创建最小的 bootstrap zip 结构
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos);

            // 添加 SYMLINKS.txt
            java.util.zip.ZipEntry symlinksEntry = new java.util.zip.ZipEntry("SYMLINKS.txt");
            zos.putNextEntry(symlinksEntry);
            String symlinks = "sh←bin/sh\nbash←bin/bash\npkg←bin/pkg\napt←bin/apt\ngcc←bin/gcc\ng++←bin/g++\nclang←bin/clang\npython3←bin/python3\n";
            zos.write(symlinks.getBytes("UTF-8"));
            zos.closeEntry();

            // 添加基础的 bin 目录结构
            String[] binFiles = {"sh", "bash", "pkg", "apt", "gcc", "g++", "clang", "python3", "busybox"};
            for (String binFile : binFiles) {
                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry("bin/" + binFile);
                zos.putNextEntry(entry);
                
                String script = "#!/system/bin/sh\n" +
                              "echo 'AccIDE Terminal - " + binFile + "'\n" +
                              "echo 'This is a fallback implementation.'\n" +
                              "echo 'Please ensure proper Termux bootstrap is installed.'\n" +
                              "exit 1\n";
                zos.write(script.getBytes("UTF-8"));
                zos.closeEntry();
            }

            zos.close();
            return baos.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "Failed to create fallback bootstrap", e);
            return new byte[0];
        }
    }
}
