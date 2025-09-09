/*
 * This file is part of AccIDE.
 *
 * AccIDE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AccIDE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AccIDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.termux.app.terminal;

import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.termux.app.TermuxActivity;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalViewClient;

/**
 * The {@link TerminalViewClient} implementation for AccIDE.
 * Based on AndroidIDE-dev implementation but simplified for AccIDE needs.
 */
public class TermuxTerminalViewClient implements TerminalViewClient {

    protected final TermuxActivity mActivity;
    protected final TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;
    
    private static final String LOG_TAG = "TermuxTerminalViewClient";

    public TermuxTerminalViewClient(TermuxActivity activity, TermuxTerminalSessionActivityClient termuxTerminalSessionActivityClient) {
        this.mActivity = activity;
        this.mTermuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient;
    }

    public void onCreate() {
        Log.d(LOG_TAG, "TermuxTerminalViewClient created");
    }

    @Override
    public float onScale(float scale) {
        Log.v(LOG_TAG, "Scale: " + scale);
        return Math.max(0.9f, Math.min(1.1f, scale));
    }

    @Override
    public void onSingleTapUp(MotionEvent e) {
        Log.v(LOG_TAG, "Single tap up");
        // Show soft keyboard when user taps on terminal
        showSoftKeyboard();
    }
    
    private void showSoftKeyboard() {
        if (mActivity != null) {
            android.view.inputmethod.InputMethodManager imm = 
                (android.view.inputmethod.InputMethodManager) mActivity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null && mActivity.getTerminalView() != null) {
                // Request focus for the terminal view
                mActivity.getTerminalView().requestFocus();
                // Show soft keyboard
                imm.showSoftInput(mActivity.getTerminalView(), android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                Log.d(LOG_TAG, "Requested soft keyboard display");
            }
        }
    }

    @Override
    public boolean shouldBackButtonBeMappedToEscape() {
        return true; // Map back button to ESC key
    }

    @Override
    public boolean shouldEnforceCharBasedInput() {
        return false;
    }

    @Override
    public boolean shouldUseCtrlSpaceWorkaround() {
        return false;
    }

    @Override
    public boolean isTerminalViewSelected() {
        return true; // Always consider terminal view as selected for now
    }

    @Override
    public void copyModeChanged(boolean copyMode) {
        Log.d(LOG_TAG, "Copy mode changed: " + copyMode);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session) {
        Log.v(LOG_TAG, "Key down: " + keyCode);
        
        // Handle special keys
        if (keyCode == KeyEvent.KEYCODE_BACK && shouldBackButtonBeMappedToEscape()) {
            // Send ESC to terminal
            if (session != null) {
                session.writeCodePoint(false, 27); // ESC character
                return true;
            }
        }
        
        return false; // Let terminal handle the key
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) {
        Log.v(LOG_TAG, "Key up: " + keyCode);
        return false; // Let terminal handle the key
    }

    @Override
    public boolean onLongPress(MotionEvent event) {
        Log.v(LOG_TAG, "Long press");
        // Could show context menu here
        return false;
    }

    @Override
    public boolean readControlKey() {
        return false; // Control key state
    }

    @Override
    public boolean readAltKey() {
        return false; // Alt key state
    }

    @Override
    public boolean readShiftKey() {
        return false; // Shift key state
    }

    @Override
    public boolean readFnKey() {
        return false; // Fn key state
    }

    @Override
    public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) {
        Log.v(LOG_TAG, "Code point: " + codePoint + ", ctrl: " + ctrlDown);
        
        if (session != null) {
            try {
                // Convert code point to character and write to terminal
                char[] chars = Character.toChars(codePoint);
                String text = new String(chars);
                
                // Write the character to the terminal session
                session.write(text);
                Log.v(LOG_TAG, "Character written to session: " + text);
                return true; // We handled the character
            } catch (Exception e) {
                Log.e(LOG_TAG, "Failed to write character to session", e);
            }
        } else {
            Log.w(LOG_TAG, "Session is null, cannot write character");
        }
        
        return false; // Let terminal handle the character if we failed
    }

    @Override
    public void onEmulatorSet() {
        Log.d(LOG_TAG, "Emulator set");
        if (mTermuxTerminalSessionActivityClient != null) {
            // Notify that emulator is ready
        }
    }

    @Override
    public void logError(String tag, String message) {
        Log.e(LOG_TAG, tag + ": " + message);
    }

    @Override
    public void logWarn(String tag, String message) {
        Log.w(LOG_TAG, tag + ": " + message);
    }

    @Override
    public void logInfo(String tag, String message) {
        Log.i(LOG_TAG, tag + ": " + message);
    }

    @Override
    public void logDebug(String tag, String message) {
        Log.d(LOG_TAG, tag + ": " + message);
    }

    @Override
    public void logVerbose(String tag, String message) {
        Log.v(LOG_TAG, tag + ": " + message);
    }

    @Override
    public void logStackTraceWithMessage(String tag, String message, Exception e) {
        Log.e(LOG_TAG, tag + ": " + message, e);
    }

    @Override
    public void logStackTrace(String tag, Exception e) {
        Log.e(LOG_TAG, tag, e);
    }
}
