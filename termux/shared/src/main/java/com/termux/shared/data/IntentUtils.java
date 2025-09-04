package com.termux.shared.data;

import android.content.Intent;
import android.os.Bundle;

public class IntentUtils {
    
    public static String getStringFromIntent(Intent intent, String key, String defaultValue) {
        if (intent == null) return defaultValue;
        String value = intent.getStringExtra(key);
        return value != null ? value : defaultValue;
    }
    
    public static boolean getBooleanFromIntent(Intent intent, String key, boolean defaultValue) {
        if (intent == null) return defaultValue;
        return intent.getBooleanExtra(key, defaultValue);
    }
    
    public static Bundle getBundleFromIntent(Intent intent, String key, Bundle defaultValue) {
        if (intent == null) return defaultValue;
        Bundle bundle = intent.getBundleExtra(key);
        return bundle != null ? bundle : defaultValue;
    }
}
