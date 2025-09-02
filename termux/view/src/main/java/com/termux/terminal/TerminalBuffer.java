package com.termux.terminal;

/**
 * Stub TerminalBuffer class for compilation compatibility
 */
public class TerminalBuffer {
    
    private int mActiveTranscriptRows = 2000;
    private int mScreenRows = 24;
    
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
    
    // Missing methods from TerminalView errors
    public int getActiveTranscriptRows() {
        return mActiveTranscriptRows;
    }
    
    public int getActiveRows() {
        return mActiveTranscriptRows + mScreenRows;
    }
    
    // Additional methods for TerminalRenderer
    public int externalToInternalRow(int externalRow) {
        return externalRow + mActiveTranscriptRows;
    }
    
    public TerminalRow allocateFullLineIfNecessary(int row) {
        return new TerminalRow();
    }
    
    // Stub TerminalRow class
    public static class TerminalRow {
        public char[] mText = new char[80];
        
        public int getSpanCount() {
            return 1;
        }
        
        public void getSpan(int spanIndex, int[] styleArray) {
            // Stub implementation
        }
        
        public int getSpaceUsed() {
            return mText.length;
        }
        
        public long getStyle(int column) {
            return 0L;
        }
    }
}