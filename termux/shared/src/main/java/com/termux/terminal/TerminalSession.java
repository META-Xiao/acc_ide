package com.termux.terminal;

/**
 * Stub TerminalSession class for compilation compatibility
 */
public class TerminalSession {
    
    public String mSessionName;
    
    public TerminalSession(String executable, String cwd, String[] arguments, String[] environment, Integer terminalTranscriptRows, TerminalSessionClient client) {
        // Stub constructor
    }
    
    public TerminalEmulator getEmulator() {
        return null;
    }
    
    public TerminalBuffer getScreen() {
        return null;
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
}