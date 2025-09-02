package com.termux.terminal;

/**
 * Stub TerminalEmulator class for compilation compatibility
 */
public class TerminalEmulator {
    
    public static final int DEFAULT_TERMINAL_CURSOR_STYLE = 0;
    public static final int DEFAULT_TERMINAL_TRANSCRIPT_ROWS = 2000;
    public static final int TERMINAL_TRANSCRIPT_ROWS_MIN = 100;
    public static final int TERMINAL_TRANSCRIPT_ROWS_MAX = 50000;
    public static final int TERMINAL_CURSOR_STYLE_BLOCK = 0;
    public static final int TERMINAL_CURSOR_STYLE_UNDERLINE = 1;
    public static final int TERMINAL_CURSOR_STYLE_BAR = 2;
    
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
        return null;
    }
}