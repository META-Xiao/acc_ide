package com.termux.terminal;

/**
 * Stub TerminalSession class for compilation compatibility
 */
public class TerminalSession {
    
    public String mSessionName;
    private TerminalSessionClient mClient;
    
    public TerminalSession(String executable, String cwd, String[] arguments, String[] environment, Integer terminalTranscriptRows, TerminalSessionClient client) {
        mClient = client;
    }
    
    public TerminalEmulator getEmulator() {
        return new TerminalEmulator();
    }
    
    public TerminalBuffer getScreen() {
        return new TerminalBuffer();
    }
    
    public boolean isRunning() {
        return false;
    }
    
    public Integer getExitStatus() {
        return 0;
    }
    
    public String getTitle() {
        return "";
    }
    
    public int getPid() {
        return 0;
    }
    
    public void finishIfRunning() {
        // Stub implementation
    }
    
    public void write(String data) {
        // Stub implementation
    }
    
    public void write(byte[] data) {
        // Stub implementation
    }
    
    // Missing methods from TerminalView errors
    public void writeCodePoint(boolean prependEscape, int codePoint) {
        // Stub implementation
    }
    
    public void updateSize(int columns, int rows) {
        // Stub implementation
    }
    
    public void onCopyTextToClipboard(String text) {
        if (mClient != null) {
            mClient.onCopyTextToClipboard(this, text);
        }
    }
    
    public void onPasteTextFromClipboard() {
        if (mClient != null) {
            mClient.onPasteTextFromClipboard(this);
        }
    }
}