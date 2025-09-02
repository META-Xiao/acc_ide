package com.termux.view;

import android.view.KeyEvent;
import android.view.MotionEvent;
import androidx.annotation.NonNull;

import com.termux.terminal.TerminalSession;

/**
 * Terminal view client interface for handling view events
 */
public interface TerminalViewClient {
    
    float onScale(float scale);
    
    void onSingleTapUp(MotionEvent e);
    
    boolean shouldBackButtonBeMappedToEscape();
    
    boolean shouldEnforceCharBasedInput();
    
    boolean shouldUseCtrlSpaceWorkaround();
    
    boolean isTerminalViewSelected();
    
    void copyModeChanged(boolean copyMode);
    
    boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session);
    
    boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session);
    
    boolean onKeyUp(int keyCode, KeyEvent e);
    
    boolean onLongPress(MotionEvent event);
    
    boolean readControlKey();
    
    boolean readAltKey();
    
    boolean readShiftKey();
    
    boolean readFnKey();
    
    void onEmulatorSet();
    
    void logError(String tag, String message);
    
    void logWarn(String tag, String message);
    
    void logInfo(String tag, String message);
    
    void logDebug(String tag, String message);
    
    void logVerbose(String tag, String message);
}