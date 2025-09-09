package com.termux.shared.logger;

import android.content.Context;
import android.util.Log;

/**
 * Simplified logger for Termux integration.
 */
public class Logger {

    public static final int LOG_LEVEL_OFF = 0;
    public static final int LOG_LEVEL_NORMAL = 1;
    public static final int LOG_LEVEL_DEBUG = 2;
    public static final int LOG_LEVEL_VERBOSE = 3;

    private static String mDefaultLogTag = "Termux";
    private static int mCurrentLogLevel = LOG_LEVEL_NORMAL;

    public static void setDefaultLogTag(String tag) {
        mDefaultLogTag = tag;
    }

    public static void setLogLevel(Context context, int logLevel) {
        mCurrentLogLevel = logLevel;
    }

    public static void logVerbose(String message) {
        logVerbose(mDefaultLogTag, message);
    }

    public static void logVerbose(String tag, String message) {
        if (mCurrentLogLevel >= LOG_LEVEL_VERBOSE) {
            Log.v(tag, message);
        }
    }

    public static void logDebug(String message) {
        logDebug(mDefaultLogTag, message);
    }

    public static void logDebug(String tag, String message) {
        if (mCurrentLogLevel >= LOG_LEVEL_DEBUG) {
            Log.d(tag, message);
        }
    }

    public static void logInfo(String tag, String message) {
        if (mCurrentLogLevel >= LOG_LEVEL_NORMAL) {
            Log.i(tag, message);
        }
    }

    public static void logWarn(String tag, String message) {
        if (mCurrentLogLevel >= LOG_LEVEL_NORMAL) {
            Log.w(tag, message);
        }
    }

    public static void logError(String tag, String message) {
        if (mCurrentLogLevel >= LOG_LEVEL_NORMAL) {
            Log.e(tag, message);
        }
    }

    public static void logError(String tag, String message, Throwable throwable) {
        if (mCurrentLogLevel >= LOG_LEVEL_NORMAL) {
            Log.e(tag, message, throwable);
        }
    }

}