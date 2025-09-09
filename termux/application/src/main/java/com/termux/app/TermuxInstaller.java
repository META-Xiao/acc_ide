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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.system.Os;
import android.util.Log;
import android.util.Pair;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 完整的 Termux Installer 实现
 * 安装真实的 termux bootstrap 包
 */
public class TermuxInstaller {
    
    private static final String TAG = "TermuxInstaller";
    
    /**
     * 设置 bootstrap（如果需要的话）
     */
    public static void setupBootstrapIfNeeded(final Activity activity, final Runnable whenDone) {
        String prefix = TermuxBootstrap.getTermuxPrefix(activity);
        
        // 检查是否已经安装了 bootstrap
        if (isBootstrapInstalled(prefix)) {
            Log.i(TAG, "Bootstrap already installed, skipping setup");
            if (whenDone != null) whenDone.run();
            return;
        }
        
        Log.i(TAG, "Bootstrap not found, starting installation");
        
        // 显示进度对话框并开始安装
        final ProgressDialog progress = new ProgressDialog(activity);
        progress.setTitle("AccIDE - Termux Setup");
        progress.setMessage("Installing Termux bootstrap packages...\nThis enables terminal functionality in your IDE.");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.setCancelable(false);
        progress.show();
        
        new AsyncTask<Void, Integer, Boolean>() {
            private String errorMessage;
            
            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    return installBootstrap(activity, this::publishProgress);
                } catch (Exception e) {
                    Log.e(TAG, "Bootstrap installation failed", e);
                    errorMessage = e.getMessage();
                    return false;
                }
            }
            
            @Override
            protected void onProgressUpdate(Integer... values) {
                if (progress != null) {
                    progress.setProgress(values[0]);
                }
            }
            
            @Override
            protected void onPostExecute(Boolean success) {
                if (progress != null) {
                    progress.dismiss();
                }
                
                if (success) {
                    Log.i(TAG, "Bootstrap installation completed successfully");
                    android.widget.Toast.makeText(activity, 
                        "Termux setup complete! Terminal is now ready to use.", 
                        android.widget.Toast.LENGTH_LONG).show();
                    if (whenDone != null) whenDone.run();
                } else {
                    showBootstrapErrorDialog(activity, whenDone, errorMessage);
                }
            }
        }.execute();
    }
    
    /**
     * 检查 bootstrap 是否已安装（检查真正的bootstrap而不是fallback脚本）
     */
    private static boolean isBootstrapInstalled(String prefix) {
        File prefixDir = new File(prefix);
        if (!prefixDir.exists()) return false;
        
        // 检查关键文件是否存在
        File[] criticalFiles = {
            new File(prefix, "bin/sh"),
            new File(prefix, "bin/pkg"),
            new File(prefix, "bin/apt")
        };
        
        for (File file : criticalFiles) {
            if (!file.exists() || !file.canExecute()) {
                return false;
            }
        }
        
        // 额外检查：确保这些是真正的bootstrap二进制文件，不是小的包装脚本
        // 真正的Termux bootstrap包含大量二进制文件，需要多个关键文件都是真实的
        File busyboxFile = new File(prefix, "bin/busybox");
        File tarFile = new File(prefix, "bin/tar");
        File bashFile = new File(prefix, "bin/bash");
        
        // 检查是否有足够多的真实bootstrap文件，且文件大小合理
        boolean hasBusybox = busyboxFile.exists() && busyboxFile.length() > 100000; // busybox应该>100KB
        boolean hasTar = tarFile.exists() && tarFile.length() > 50000; // tar应该>50KB
        boolean hasBash = bashFile.exists() && bashFile.length() > 50000; // bash应该>50KB
        
        // 需要至少2个真实的核心二进制文件才算真正安装了bootstrap
        int realBinaryCount = 0;
        if (hasBusybox) realBinaryCount++;
        if (hasTar) realBinaryCount++;
        if (hasBash) realBinaryCount++;
        
        boolean hasRealBootstrap = realBinaryCount >= 2; // 至少需要2个真实二进制
        
        Log.d(TAG, "Bootstrap check detailed:");
        Log.d(TAG, "  busybox: exists=" + busyboxFile.exists() + ", size=" + busyboxFile.length() + ", real=" + hasBusybox);
        Log.d(TAG, "  tar: exists=" + tarFile.exists() + ", size=" + tarFile.length() + ", real=" + hasTar);
        Log.d(TAG, "  bash: exists=" + bashFile.exists() + ", size=" + bashFile.length() + ", real=" + hasBash);
        Log.d(TAG, "  realBinaryCount=" + realBinaryCount + ", hasRealBootstrap=" + hasRealBootstrap);
        
        return hasRealBootstrap;
    }
    
    /**
     * 执行 bootstrap 安装
     */
    private static boolean installBootstrap(Activity activity, ProgressCallback progressCallback) throws Exception {
        String prefix = TermuxBootstrap.getTermuxPrefix(activity);
        String stagingPrefix = prefix + "-staging";
        
        progressCallback.updateProgress(10);
        
        // 清理旧的安装
        deleteRecursively(new File(prefix));
        deleteRecursively(new File(stagingPrefix));
        
        // 创建目录
        File stagingDir = new File(stagingPrefix);
        if (!stagingDir.mkdirs()) {
            throw new RuntimeException("Failed to create staging directory: " + stagingPrefix);
        }
        
        progressCallback.updateProgress(20);
        
        // 加载 bootstrap zip 数据
        Log.i(TAG, "Loading Termux official bootstrap zip data");
        byte[] zipData = TermuxBootstrap.loadZipBytes(activity);
        if (zipData == null || zipData.length == 0) {
            throw new RuntimeException("Bootstrap zip data is empty");
        }
        
        progressCallback.updateProgress(30);
        
        // 解压和安装 bootstrap - 完全按照 Termux 官方逻辑
        Log.i(TAG, "Extracting bootstrap to staging directory: " + stagingPrefix);
        
        final List<Pair<String, String>> symlinks = new ArrayList<>();
        final byte[] buffer = new byte[8192];
        
        try (ZipInputStream zipInput = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry zipEntry;
            int filesProcessed = 0;
            int totalEstimatedFiles = 1000; // 估计文件数
            
            while ((zipEntry = zipInput.getNextEntry()) != null) {
                String zipEntryName = zipEntry.getName();
                
                if (zipEntryName.equals("SYMLINKS.txt")) {
                    // 处理符号链接 - 完全按照 Termux 官方逻辑
                    BufferedReader symlinksReader = new BufferedReader(new InputStreamReader(zipInput));
                    String line;
                    while ((line = symlinksReader.readLine()) != null) {
                        String[] parts = line.split("←");
                        if (parts.length != 2) {
                            throw new RuntimeException("Malformed symlink line: " + line);
                        }
                        String oldPath = parts[0];
                        String newPath = stagingPrefix + "/" + parts[1];
                        symlinks.add(Pair.create(oldPath, newPath));
                        
                        // 确保父目录存在
                        File parentFile = new File(newPath).getParentFile();
                        if (parentFile != null && !parentFile.exists()) {
                            if (!parentFile.mkdirs()) {
                                throw new RuntimeException("Failed to create directory: " + parentFile.getAbsolutePath());
                            }
                        }
                    }
                } else {
                    // 处理普通文件 - 完全按照 Termux 官方逻辑
                    File targetFile = new File(stagingPrefix, zipEntryName);
                    boolean isDirectory = zipEntry.isDirectory();
                    
                    // 确保父目录存在
                    File parentFile = isDirectory ? targetFile : targetFile.getParentFile();
                    if (parentFile != null && !parentFile.exists()) {
                        if (!parentFile.mkdirs()) {
                            throw new RuntimeException("Failed to create directory: " + parentFile.getAbsolutePath());
                        }
                    }
                    
                    if (!isDirectory) {
                        // 写入文件内容
                        try (FileOutputStream outStream = new FileOutputStream(targetFile)) {
                            int readBytes;
                            while ((readBytes = zipInput.read(buffer)) != -1) {
                                outStream.write(buffer, 0, readBytes);
                            }
                        }
                        
                        // 设置可执行权限 - 完全按照 Termux 官方逻辑
                        if (zipEntryName.startsWith("bin/") || 
                            zipEntryName.startsWith("libexec/") ||
                            zipEntryName.startsWith("lib/apt/apt-helper") || 
                            zipEntryName.startsWith("lib/apt/methods/")) {
                            
                            try {
                                //noinspection OctalInteger
                                Os.chmod(targetFile.getAbsolutePath(), 0700);
                                Log.d(TAG, "Set executable permissions for: " + zipEntryName);
                            } catch (Exception e) {
                                Log.w(TAG, "Failed to set permissions for: " + zipEntryName, e);
                                // 尝试 Java API 作为后备
                                targetFile.setExecutable(true, false);
                                targetFile.setReadable(true, false);
                                targetFile.setWritable(true, false);
                            }
                        }
                    }
                }
                
                filesProcessed++;
                
                // 更新进度 (30% - 80%)
                if (filesProcessed % 50 == 0) {
                    int progress = 30 + Math.min(50, (filesProcessed * 50) / totalEstimatedFiles);
                    progressCallback.updateProgress(progress);
                }
            }
        }
        
        progressCallback.updateProgress(85);
        
        // 验证符号链接文件存在
        if (symlinks.isEmpty()) {
            throw new RuntimeException("No SYMLINKS.txt encountered");
        }
        
        // 创建符号链接 - 完全按照 Termux 官方逻辑
        Log.i(TAG, "Creating symlinks");
        for (Pair<String, String> symlink : symlinks) {
            try {
                Os.symlink(symlink.first, symlink.second);
                Log.d(TAG, "Created symlink: " + symlink.first + " -> " + symlink.second);
            } catch (Exception e) {
                Log.w(TAG, "Failed to create symlink: " + symlink.first + " -> " + symlink.second, e);
                // 符号链接失败不应该阻止整个安装过程
            }
        }
        
        progressCallback.updateProgress(90);
        
        // 移动 staging 到最终位置 - 完全按照 Termux 官方逻辑
        Log.i(TAG, "Moving termux prefix staging to prefix directory");
        File stagingDirFile = new File(stagingPrefix);
        File finalDir = new File(prefix);
        
        if (!stagingDirFile.renameTo(finalDir)) {
            throw new RuntimeException("Moving termux prefix staging to prefix directory failed");
        }
        
        progressCallback.updateProgress(95);
        
        // 创建环境配置文件 - 按照 Termux 官方逻辑
        createTermuxEnvironment(activity);
        
        progressCallback.updateProgress(100);
        
        Log.i(TAG, "Bootstrap packages installed successfully");
        return true;
    }
    
    /**
     * 创建 Termux 环境配置 - 按照官方逻辑
     */
    private static void createTermuxEnvironment(Activity activity) {
        Log.i(TAG, "Creating Termux environment configuration");
        
        try {
            // 创建环境配置文件（类似 TermuxShellEnvironment.writeEnvironmentToFile）
            TermuxBootstrap.createEnvironmentConfigFiles(activity);
            
            // 设置存储符号链接（可选，但建议）
            setupStorageSymlinks(activity);
            
        } catch (Exception e) {
            Log.w(TAG, "Failed to create environment configuration", e);
            // 环境配置失败不应该阻止bootstrap安装成功
        }
    }
    
    /**
     * 设置存储符号链接 - 简化版本
     */
    private static void setupStorageSymlinks(Context context) {
        Log.i(TAG, "Setting up storage symlinks");
        
        try {
            String homeDir = TermuxBootstrap.getTermuxHome(context);
            File storageDir = new File(homeDir, "storage");
            
            // 清理现有的存储目录
            if (storageDir.exists()) {
                deleteRecursively(storageDir);
            }
            storageDir.mkdirs();
            
            // 创建主要存储符号链接
            File extStorage = android.os.Environment.getExternalStorageDirectory();
            if (extStorage != null && extStorage.exists()) {
                try {
                    Os.symlink(extStorage.getAbsolutePath(), 
                              new File(storageDir, "shared").getAbsolutePath());
                    Log.d(TAG, "Created shared storage symlink");
                } catch (Exception e) {
                    Log.w(TAG, "Failed to create shared storage symlink", e);
                }
            }
            
            // 创建下载目录符号链接
            File downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS);
            if (downloadsDir != null && downloadsDir.exists()) {
                try {
                    Os.symlink(downloadsDir.getAbsolutePath(), 
                              new File(storageDir, "downloads").getAbsolutePath());
                    Log.d(TAG, "Created downloads symlink");
                } catch (Exception e) {
                    Log.w(TAG, "Failed to create downloads symlink", e);
                }
            }
            
            Log.i(TAG, "Storage symlinks setup completed");
            
        } catch (Exception e) {
            Log.w(TAG, "Failed to setup storage symlinks", e);
        }
    }
    
    /**
     * 显示 bootstrap 错误对话框
     */
    private static void showBootstrapErrorDialog(Activity activity, Runnable whenDone, String message) {
        Log.e(TAG, "Bootstrap Error: " + message);
        
        activity.runOnUiThread(() -> {
            try {
                new AlertDialog.Builder(activity)
                    .setTitle("AccIDE - Termux Setup Error")
                    .setMessage("Failed to setup Termux terminal environment:\n\n" + message + 
                              "\n\nThe basic terminal environment will be used instead.")
                    .setPositiveButton("Continue", (dialog, which) -> {
                        dialog.dismiss();
                        // 设置基础环境作为后备
                        TermuxBootstrap.setupBootstrapIfNeeded(activity);
                        if (whenDone != null) whenDone.run();
                    })
                    .setNegativeButton("Retry", (dialog, which) -> {
                        dialog.dismiss();
                        setupBootstrapIfNeeded(activity, whenDone);
                    })
                    .show();
            } catch (Exception e) {
                Log.e(TAG, "Failed to show error dialog", e);
                if (whenDone != null) whenDone.run();
            }
        });
    }
    
    /**
     * 递归删除目录
     */
    private static void deleteRecursively(File file) {
        if (!file.exists()) return;
        
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
    
    /**
     * 进度回调接口
     */
    private interface ProgressCallback {
        void updateProgress(int progress);
    }
}