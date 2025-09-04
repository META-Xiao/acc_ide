package com.termux.shared.data;

import android.content.Context;

public class DataUtils {
    
    public static String getStringFromBundle(android.os.Bundle bundle, String key, String defaultValue) {
        if (bundle == null) return defaultValue;
        String value = bundle.getString(key);
        return value != null ? value : defaultValue;
    }
    
    public static boolean getBooleanFromBundle(android.os.Bundle bundle, String key, boolean defaultValue) {
        if (bundle == null) return defaultValue;
        return bundle.getBoolean(key, defaultValue);
    }
    
    public static int getIntFromBundle(android.os.Bundle bundle, String key, int defaultValue) {
        if (bundle == null) return defaultValue;
        return bundle.getInt(key, defaultValue);
    }
}
