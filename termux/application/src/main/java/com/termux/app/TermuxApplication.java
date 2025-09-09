package com.termux.app;

import android.content.Context;
import androidx.multidex.MultiDexApplication;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

public class TermuxApplication extends MultiDexApplication {

    private static final String LOG_TAG = "TermuxApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        Context context = getApplicationContext();
        
        Logger.logDebug("Starting Termux Application");

        // Set log config for the app
        setLogConfig(context);

        // Setup termux directories
        setupTermuxDirectories(context);

        // Initialize Termux environment
        initializeTermuxEnvironment(context);

        Logger.logInfo(LOG_TAG, "Termux Application initialized successfully");
    }

    private static void setLogConfig(Context context) {
        Logger.setDefaultLogTag(TermuxConstants.TERMUX_APP_NAME);
        // For now use default log level
        Logger.setLogLevel(context, Logger.LOG_LEVEL_NORMAL);
    }

    private void setupTermuxDirectories(Context context) {
        try {
            Logger.logDebug(LOG_TAG, "Setting up Termux directories");
            
            // Create main termux directories
            String filesDir = context.getFilesDir().getAbsolutePath();
            
            // Create PREFIX directory structure (/data/data/com.acc_ide/files/usr)
            createDirectory(filesDir + "/usr");
            createDirectory(filesDir + "/usr/bin");
            createDirectory(filesDir + "/usr/etc");
            createDirectory(filesDir + "/usr/include");
            createDirectory(filesDir + "/usr/lib");
            createDirectory(filesDir + "/usr/libexec");
            createDirectory(filesDir + "/usr/share");
            createDirectory(filesDir + "/usr/tmp");
            createDirectory(filesDir + "/usr/var");
            createDirectory(filesDir + "/usr/var/log");
            
            // Create HOME directory structure (/data/data/com.acc_ide/files/home)
            createDirectory(filesDir + "/home");
            createDirectory(filesDir + "/home/.config");
            createDirectory(filesDir + "/home/.config/termux");
            createDirectory(filesDir + "/home/.termux");
            createDirectory(filesDir + "/home/storage");
            
            // Create staging directory
            createDirectory(filesDir + "/usr-staging");
            
            Logger.logDebug(LOG_TAG, "Termux directories created successfully");
            
        } catch (Exception e) {
            Logger.logError(LOG_TAG, "Failed to setup Termux directories", e);
        }
    }

    private void createDirectory(String path) {
        java.io.File dir = new java.io.File(path);
        if (!dir.exists()) {
            if (dir.mkdirs()) {
                Logger.logVerbose(LOG_TAG, "Created directory: " + path);
            } else {
                Logger.logWarn(LOG_TAG, "Failed to create directory: " + path);
            }
        }
    }

    private void initializeTermuxEnvironment(Context context) {
        try {
            Logger.logDebug(LOG_TAG, "Initializing Termux environment");
            
            // Create basic shell scripts in usr/bin
            createBasicShellScripts(context);
            
            // Setup environment variables
            setupEnvironmentFiles(context);
            
            Logger.logDebug(LOG_TAG, "Termux environment initialized successfully");
            
        } catch (Exception e) {
            Logger.logError(LOG_TAG, "Failed to initialize Termux environment", e);
        }
    }

    private void createBasicShellScripts(Context context) {
        String prefixPath = context.getFilesDir().getAbsolutePath() + "/usr";
        String binPath = prefixPath + "/bin";
        
        // Create sh wrapper
        createShellScript(binPath + "/sh", 
            "#!/system/bin/sh\n" +
            "exec /system/bin/sh \"$@\"\n");
        
        // Create bash wrapper (pointing to sh for now)
        createShellScript(binPath + "/bash", 
            "#!/system/bin/sh\n" +
            "exec /system/bin/sh \"$@\"\n");
        
        // Create basic command wrappers
        createShellScript(binPath + "/ls", 
            "#!/system/bin/sh\n" +
            "exec /system/bin/ls \"$@\"\n");
        
        createShellScript(binPath + "/pwd", 
            "#!/system/bin/sh\n" +
            "exec /system/bin/pwd \"$@\"\n");
        
        createShellScript(binPath + "/echo", 
            "#!/system/bin/sh\n" +
            "exec /system/bin/echo \"$@\"\n");
        
        createShellScript(binPath + "/cat", 
            "#!/system/bin/sh\n" +
            "exec /system/bin/cat \"$@\"\n");
        
        createShellScript(binPath + "/grep", 
            "#!/system/bin/sh\n" +
            "exec /system/bin/grep \"$@\"\n");
    }

    private void createShellScript(String path, String content) {
        try {
            java.io.File file = new java.io.File(path);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            fos.write(content.getBytes());
            fos.close();
            
            // Set executable permissions
            if (!file.setExecutable(true, false)) {
                Logger.logWarn(LOG_TAG, "Failed to set executable permission for: " + path);
            }
            if (!file.setReadable(true, false)) {
                Logger.logWarn(LOG_TAG, "Failed to set readable permission for: " + path);
            }
            if (!file.setWritable(true, false)) {
                Logger.logWarn(LOG_TAG, "Failed to set writable permission for: " + path);
            }
            
            Logger.logDebug(LOG_TAG, "Created shell script: " + path);
            
        } catch (java.io.IOException e) {
            Logger.logError(LOG_TAG, "Failed to create shell script: " + path, e);
        }
    }

    private void setupEnvironmentFiles(Context context) {
        try {
            String prefixPath = context.getFilesDir().getAbsolutePath() + "/usr";
            String homePath = context.getFilesDir().getAbsolutePath() + "/home";
            String configPath = homePath + "/.config/termux";
            
            // Create termux.env file
            String envContent = 
                "# Termux environment variables\n" +
                "PREFIX=" + prefixPath + "\n" +
                "HOME=" + homePath + "\n" +
                "PATH=" + prefixPath + "/bin:/system/bin:/system/xbin\n" +
                "TMPDIR=" + prefixPath + "/tmp\n" +
                "TERM=xterm-256color\n" +
                "LANG=en_US.UTF-8\n" +
                "USER=termux\n";
            
            java.io.File envFile = new java.io.File(configPath + "/termux.env");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(envFile);
            fos.write(envContent.getBytes());
            fos.close();
            
            Logger.logDebug(LOG_TAG, "Created environment file: " + envFile.getAbsolutePath());
            
        } catch (java.io.IOException e) {
            Logger.logError(LOG_TAG, "Failed to setup environment files", e);
        }
    }
}
