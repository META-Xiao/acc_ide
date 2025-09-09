package com.termux.shared.termux;

import java.io.File;

/**
 * Essential Termux constants for directory structure and app configuration.
 * Based on AndroidIDE-dev implementation.
 */
public final class TermuxConstants {

    /**
     * Termux app name.
     */
    public static final String TERMUX_APP_NAME = "Termux";

    /**
     * Termux package name.
     */
    public static final String TERMUX_PACKAGE_NAME = "com.acc_ide"; // Our package name
    
    /**
     * Termux Files Directory Path: "/data/data/com.acc_ide/files"
     */
    public static final String TERMUX_FILES_DIR_PATH = "/data/data/" + TERMUX_PACKAGE_NAME + "/files";
    
    /**
     * Termux Files Directory
     */
    public static final File TERMUX_FILES_DIR = new File(TERMUX_FILES_DIR_PATH);

    /**
     * Termux PREFIX Directory Path: "/data/data/com.acc_ide/files/usr"
     */
    public static final String TERMUX_PREFIX_DIR_PATH = TERMUX_FILES_DIR_PATH + "/usr";
    
    /**
     * Termux PREFIX Directory
     */
    public static final File TERMUX_PREFIX_DIR = new File(TERMUX_PREFIX_DIR_PATH);

    /**
     * Termux bin directory Path: "/data/data/com.acc_ide/files/usr/bin"
     */
    public static final String TERMUX_BIN_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/bin";
    
    /**
     * Termux bin directory
     */
    public static final File TERMUX_BIN_PREFIX_DIR = new File(TERMUX_BIN_PREFIX_DIR_PATH);

    /**
     * Termux etc directory Path: "/data/data/com.acc_ide/files/usr/etc"
     */
    public static final String TERMUX_ETC_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/etc";
    
    /**
     * Termux etc directory
     */
    public static final File TERMUX_ETC_PREFIX_DIR = new File(TERMUX_ETC_PREFIX_DIR_PATH);

    /**
     * Termux lib directory Path: "/data/data/com.acc_ide/files/usr/lib"
     */
    public static final String TERMUX_LIB_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/lib";
    
    /**
     * Termux lib directory
     */
    public static final File TERMUX_LIB_PREFIX_DIR = new File(TERMUX_LIB_PREFIX_DIR_PATH);

    /**
     * Termux tmp directory Path: "/data/data/com.acc_ide/files/usr/tmp"
     */
    public static final String TERMUX_TMP_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/tmp";
    
    /**
     * Termux tmp directory
     */
    public static final File TERMUX_TMP_PREFIX_DIR = new File(TERMUX_TMP_PREFIX_DIR_PATH);

    /**
     * Termux HOME directory Path: "/data/data/com.acc_ide/files/home"
     */
    public static final String TERMUX_HOME_DIR_PATH = TERMUX_FILES_DIR_PATH + "/home";
    
    /**
     * Termux HOME directory
     */
    public static final File TERMUX_HOME_DIR = new File(TERMUX_HOME_DIR_PATH);

    /**
     * Termux config home directory Path: "/data/data/com.acc_ide/files/home/.config/termux"
     */
    public static final String TERMUX_CONFIG_HOME_DIR_PATH = TERMUX_HOME_DIR_PATH + "/.config/termux";
    
    /**
     * Termux config home directory
     */
    public static final File TERMUX_CONFIG_HOME_DIR = new File(TERMUX_CONFIG_HOME_DIR_PATH);

    /**
     * Termux data home directory Path: "/data/data/com.acc_ide/files/home/.termux"
     */
    public static final String TERMUX_DATA_HOME_DIR_PATH = TERMUX_HOME_DIR_PATH + "/.termux";
    
    /**
     * Termux data home directory
     */
    public static final File TERMUX_DATA_HOME_DIR = new File(TERMUX_DATA_HOME_DIR_PATH);

    /**
     * Termux environment file path: "/data/data/com.acc_ide/files/home/.config/termux/termux.env"
     */
    public static final String TERMUX_ENV_FILE_PATH = TERMUX_CONFIG_HOME_DIR_PATH + "/termux.env";

    /**
     * Termux environment temp file path: "/data/data/com.acc_ide/files/home/.config/termux/termux.env.tmp"
     */
    public static final String TERMUX_ENV_TEMP_FILE_PATH = TERMUX_CONFIG_HOME_DIR_PATH + "/termux.env.tmp";

}