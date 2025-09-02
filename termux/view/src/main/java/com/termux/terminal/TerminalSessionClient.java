package com.termux.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Terminal session client interface for handling terminal events
 */
public interface TerminalSessionClient {
    
    void onTextChanged(@NonNull TerminalSession changedSession);
    
    void onTitleChanged(@NonNull TerminalSession updatedSession);
    
    void onSessionFinished(@NonNull TerminalSession finishedSession);
    
    void onCopyTextToClipboard(@NonNull TerminalSession session, String text);
    
    void onPasteTextFromClipboard(@Nullable TerminalSession session);
    
    void onBell(@NonNull TerminalSession session);
    
    void onColorsChanged(@NonNull TerminalSession changedSession);
    
    Integer getTerminalCursorStyle();
    
    void setTerminalShellPid(@NonNull TerminalSession session, int pid);
    
    void onTerminalCursorStateChange(boolean state);
}