package com.termux.terminal;

/**
 * Stub TerminalBuffer class for compilation compatibility
 */
public class TerminalBuffer {
    
    public int getColumns() {
        return 80;
    }
    
    public int getRows() {
        return 24;
    }
    
    public String getSelectedText(int cx1, int cy1, int cx2, int cy2) {
        return "";
    }
    
    public void clearScrollback() {
        // Stub implementation
    }
    
    public String getTranscriptText() {
        return "";
    }
    
    public String getTranscriptTextWithFullLinesJoined() {
        return "";
    }
    
    public String getTranscriptTextWithoutJoinedLines() {
        return "";
    }
    
    public int getCursorRow() {
        return 0;
    }
    
    public int getCursorCol() {
        return 0;
    }
}