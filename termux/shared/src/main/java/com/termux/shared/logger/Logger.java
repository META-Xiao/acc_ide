package com.termux.shared.logger;

import android.util.Log;

public class Logger {
    
    public static void logError(String tag, String message) {
        Log.e(tag, message);
    }
    
    public static void logError(String tag, String message, Throwable throwable) {
        Log.e(tag, message, throwable);
    }
    
    public static void logWarn(String tag, String message) {
        Log.w(tag, message);
    }
    
    public static void logInfo(String tag, String message) {
        Log.i(tag, message);
    }
    
    public static void logDebug(String tag, String message) {
        Log.d(tag, message);
    }
    
    public static void logVerbose(String tag, String message) {
        Log.v(tag, message);
    }
    
    public static void logStackTraceWithMessage(String tag, String message, Exception e) {
        Log.e(tag, message, e);
    }
    
    public static void logStackTrace(String tag, Exception e) {
        Log.e(tag, "Stack trace", e);
    }
}
