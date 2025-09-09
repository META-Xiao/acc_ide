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

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.termux.app.TermuxActivity;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

/**
 * The {@link TerminalSessionClient} implementation that handles terminal session events for AccIDE.
 * Based on AndroidIDE-dev implementation but simplified for AccIDE needs.
 */
public class TermuxTerminalSessionActivityClient implements TerminalSessionClient {

    protected final TermuxActivity mActivity;
    private static final String LOG_TAG = "TermuxTerminalSessionActivityClient";

    public TermuxTerminalSessionActivityClient(TermuxActivity activity) {
        this.mActivity = activity;
    }

    public void onCreate() {
        Log.d(LOG_TAG, "TermuxTerminalSessionActivityClient created");
    }

    public void onStart() {
        Log.d(LOG_TAG, "TermuxTerminalSessionActivityClient started");
    }

    public void onResume() {
        Log.d(LOG_TAG, "TermuxTerminalSessionActivityClient resumed");
    }

    public void onStop() {
        Log.d(LOG_TAG, "TermuxTerminalSessionActivityClient stopped");
    }

    public void addNewSession(boolean isFailsafe, String sessionName) {
        addNewSession(isFailsafe, sessionName, null);
    }

    public void addNewSession(boolean isFailsafe, String sessionName, String workingDirectory) {
        Log.d(LOG_TAG, "Adding new session: " + sessionName + " in " + workingDirectory);
        // For now, this is a placeholder - in full implementation this would create actual terminal sessions
    }

    public void setCurrentSession(TerminalSession session) {
        Log.d(LOG_TAG, "Setting current session: " + (session != null ? session.getTitle() : "null"));
        if (mActivity != null && mActivity.mTerminalView != null) {
            mActivity.mTerminalView.attachSession(session);
        }
    }

    public TerminalSession getCurrentStoredSessionOrLast() {
        // Return null for now - in full implementation this would return actual sessions
        return null;
    }

    public void termuxSessionListNotifyUpdated() {
        Log.d(LOG_TAG, "Session list updated");
    }

    public void onResetTerminalSession() {
        Log.d(LOG_TAG, "Terminal session reset");
    }

    public void onReloadActivityStyling() {
        Log.d(LOG_TAG, "Activity styling reloaded");
    }

    // TerminalSessionClient interface implementation
    @Override
    public void onTextChanged(TerminalSession changedSession) {
        Log.v(LOG_TAG, "Text changed in session: " + changedSession.getTitle());
        
        // Update the terminal view display
        if (mActivity != null) {
            mActivity.runOnUiThread(() -> {
                // Get the terminal view and trigger a redraw
                com.termux.view.TerminalView terminalView = mActivity.getTerminalView();
                if (terminalView != null) {
                    // Force the terminal view to redraw
                    terminalView.invalidate();
                    Log.v(LOG_TAG, "Terminal view invalidated for display update");
                }
            });
        }
    }

    @Override
    public void onTitleChanged(TerminalSession updatedSession) {
        Log.d(LOG_TAG, "Title changed: " + updatedSession.getTitle());
        if (mActivity != null) {
            mActivity.runOnUiThread(() -> {
                if (updatedSession.getTitle() != null) {
                    mActivity.setTitle(updatedSession.getTitle());
                }
            });
        }
    }

    @Override
    public void onSessionFinished(TerminalSession finishedSession) {
        Log.d(LOG_TAG, "Session finished: " + finishedSession.getTitle());
    }

    @Override
    public void onBell(TerminalSession session) {
        Log.d(LOG_TAG, "Bell in session: " + session.getTitle());
    }

    @Override
    public void onColorsChanged(TerminalSession changedSession) {
        Log.d(LOG_TAG, "Colors changed in session: " + changedSession.getTitle());
    }

    @Override
    public void onTerminalCursorStateChange(boolean state) {
        Log.v(LOG_TAG, "Cursor state changed: " + state);
    }

    @Override
    public Integer getTerminalCursorStyle() {
        return null; // Use default cursor style
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

    @Override
    public void onCopyTextToClipboard(TerminalSession session, String text) {
        Log.d(LOG_TAG, "Copy text to clipboard: " + text);
        // TODO: Implement clipboard functionality
    }

    @Override
    public void onPasteTextFromClipboard(TerminalSession session) {
        Log.d(LOG_TAG, "Paste text from clipboard");
        // TODO: Implement clipboard functionality
    }

    @Override
    public void setTerminalShellPid(TerminalSession session, int pid) {
        Log.d(LOG_TAG, "Terminal shell PID set: " + pid + " for session: " + session.getTitle());
    }
}
