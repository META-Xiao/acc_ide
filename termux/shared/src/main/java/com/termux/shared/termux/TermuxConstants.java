package com.termux.shared.termux;

import java.io.File;

public class TermuxConstants {
    
    public static final String TERMUX_PACKAGE_NAME = "com.acc_ide";
    public static final String TERMUX_APP_NAME = "AccIDE";
    
    public static final String TERMUX_PREFIX_DIR_PATH = "/data/data/" + TERMUX_PACKAGE_NAME + "/files/usr";
    public static final File TERMUX_PREFIX_DIR = new File(TERMUX_PREFIX_DIR_PATH);
    
    public static final String TERMUX_STAGING_PREFIX_DIR_PATH = "/data/data/" + TERMUX_PACKAGE_NAME + "/files/usr-staging";
    public static final File TERMUX_STAGING_PREFIX_DIR = new File(TERMUX_STAGING_PREFIX_DIR_PATH);
    
    public static final String TERMUX_HOME_DIR_PATH = "/data/data/" + TERMUX_PACKAGE_NAME + "/files/home";
    public static final File TERMUX_HOME_DIR = new File(TERMUX_HOME_DIR_PATH);
    
    public static class TERMUX_APP {
        public static class TERMUX_ACTIVITY {
            public static final String INTENT_EXTRA_FAILSAFE_SESSION = "failsafe_session";
        }
        
        public static class TERMUX_SERVICE {
            public static final String ACTION_STOP_SERVICE = TERMUX_PACKAGE_NAME + ".ACTION_STOP_SERVICE";
            public static final String ACTION_WAKE_LOCK = TERMUX_PACKAGE_NAME + ".ACTION_WAKE_LOCK";
            public static final String ACTION_WAKE_UNLOCK = TERMUX_PACKAGE_NAME + ".ACTION_WAKE_UNLOCK";
        }
    }
}
