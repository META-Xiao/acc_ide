package com.itsaky.androidide.app;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.os.Bundle;

/**
 * Simple base activity for Termux integration
 */
public abstract class BaseIDEActivity extends AppCompatActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
    
    public Context getContext() {
        return this;
    }
    
    // Dummy implementations for required methods
    public int navigationBarColor() {
        return 0;
    }
    
    public int statusBarColor() {
        return 0;
    }
    
    protected void flashError(String message) {
        // Simple toast implementation
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }
    
    // Dummy method for session handling
    public void addNewSession(Object session) {
        // Override in subclasses
    }
    
    public void removeFinishedSession(Object session) {
        // Override in subclasses
    }
}
