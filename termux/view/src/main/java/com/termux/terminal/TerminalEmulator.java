package com.termux.terminal;

/**
 * Stub TerminalEmulator class for compilation compatibility
 */
public class TerminalEmulator {
    
    // Constants
    public static final int DEFAULT_TERMINAL_CURSOR_STYLE = 0;
    public static final int DEFAULT_TERMINAL_TRANSCRIPT_ROWS = 2000;
    public static final int TERMINAL_TRANSCRIPT_ROWS_MIN = 100;
    public static final int TERMINAL_TRANSCRIPT_ROWS_MAX = 50000;
    public static final int TERMINAL_CURSOR_STYLE_BLOCK = 0;
    public static final int TERMINAL_CURSOR_STYLE_UNDERLINE = 1;
    public static final int TERMINAL_CURSOR_STYLE_BAR = 2;
    
    // Mouse button constants
    public static final int MOUSE_LEFT_BUTTON = 0;
    public static final int MOUSE_LEFT_BUTTON_MOVED = 32;
    public static final int MOUSE_WHEELDOWN_BUTTON = 65;
    public static final int MOUSE_WHEELUP_BUTTON = 64;
    
    // Other constants
    public static final int UNICODE_REPLACEMENT_CHAR = 0xFFFD;
    
    // Public fields
    public int mRows = 24;
    public int mColumns = 80;
    
    // Private fields for internal state
    private boolean mCursorBlinkingEnabled = true;
    private boolean mCursorBlinkState = true;
    private int mScrollCounter = 0;
    private boolean mAutoScrollDisabled = false;
    
    public void sendTextToTerminal(String text) {
        // Stub implementation
    }
    
    public int getCursorRow() {
        return 0;
    }
    
    public int getCursorCol() {
        return 0;
    }
    
    public boolean isAlternateBufferActive() {
        return false;
    }
    
    public void reset() {
        // Stub implementation
    }
    
    public TerminalBuffer getScreen() {
        return new TerminalBuffer();
    }
    
    // Missing methods from TerminalView errors
    public boolean isMouseTrackingActive() {
        return false;
    }
    
    public int getScrollCounter() {
        return mScrollCounter;
    }
    
    public void clearScrollCounter() {
        mScrollCounter = 0;
    }
    
    public boolean isAutoScrollDisabled() {
        return mAutoScrollDisabled;
    }
    
    public void sendMouseEvent(int mouseButton, int x, int y, boolean pressed) {
        // Stub implementation
    }
    
    public void paste(String text) {
        // Stub implementation
    }
    
    public void setCursorBlinkState(boolean state) {
        mCursorBlinkState = state;
    }
    
    public void setCursorBlinkingEnabled(boolean enabled) {
        mCursorBlinkingEnabled = enabled;
    }
    
    public boolean isCursorEnabled() {
        return true;
    }
    
    public boolean isCursorKeysApplicationMode() {
        return false;
    }
    
    public boolean isKeypadApplicationMode() {
        return false;
    }
    
    public String getSelectedText(int x1, int y1, int x2, int y2) {
        return "";
    }
    
    // Additional methods for TerminalRenderer
    public boolean isReverseVideo() {
        return false;
    }
    
    public boolean shouldCursorBeVisible() {
        return mCursorBlinkingEnabled ? mCursorBlinkState : true;
    }
    
    public int getCursorStyle() {
        return DEFAULT_TERMINAL_CURSOR_STYLE;
    }
    
    // Mock TerminalColors class
    public TerminalColors mColors = new TerminalColors();
    
    public static class TerminalColors {
        public int[] mCurrentColors = new int[16];
        
        public TerminalColors() {
            // Initialize with basic colors
            for (int i = 0; i < mCurrentColors.length; i++) {
                mCurrentColors[i] = 0xFF000000; // Default black
            }
        }
    }
}