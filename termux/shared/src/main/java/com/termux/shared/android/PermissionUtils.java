package com.termux.shared.android;

import android.content.Context;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;

public class PermissionUtils {
    
    public static boolean checkPermission(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }
    
    public static boolean checkStoragePermission(Context context) {
        return checkPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }
}
