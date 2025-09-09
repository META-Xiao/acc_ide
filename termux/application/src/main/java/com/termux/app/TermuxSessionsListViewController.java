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

package com.termux.app;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.termux.app.TermuxActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller for the sessions list in the terminal drawer.
 * Simplified implementation for AccIDE.
 */
public class TermuxSessionsListViewController extends BaseAdapter implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {

    private final TermuxActivity mActivity;
    private final List<String> mSessions;
    private static final String LOG_TAG = "TermuxSessionsListViewController";

    public TermuxSessionsListViewController(TermuxActivity activity, List<String> sessions) {
        this.mActivity = activity;
        this.mSessions = sessions != null ? sessions : new ArrayList<>();
        
        // Add a default session if none exist
        if (this.mSessions.isEmpty()) {
            this.mSessions.add("AccIDE Terminal");
        }
    }

    @Override
    public int getCount() {
        return mSessions.size();
    }

    @Override
    public Object getItem(int position) {
        return position < mSessions.size() ? mSessions.get(position) : null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(mActivity).inflate(android.R.layout.simple_list_item_1, parent, false);
        }
        
        TextView textView = convertView.findViewById(android.R.id.text1);
        String sessionName = position < mSessions.size() ? mSessions.get(position) : "Unknown";
        textView.setText(sessionName);
        
        return convertView;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Log.d(LOG_TAG, "Session clicked: " + position);
        String sessionName = position < mSessions.size() ? mSessions.get(position) : "Unknown";
        Log.d(LOG_TAG, "Switching to session: " + sessionName);
        
        // Close drawer after selection
        if (mActivity.mDrawer != null) {
            mActivity.mDrawer.closeDrawers();
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        Log.d(LOG_TAG, "Session long clicked: " + position);
        String sessionName = position < mSessions.size() ? mSessions.get(position) : "Unknown";
        
        // Show context menu for session operations (rename, close, etc.)
        // For now, just log it
        Log.d(LOG_TAG, "Long click on session: " + sessionName);
        
        return true; // Consume the long click
    }

    public void addSession(String sessionName) {
        if (sessionName != null && !sessionName.trim().isEmpty()) {
            mSessions.add(sessionName);
            notifyDataSetChanged();
            Log.d(LOG_TAG, "Added session: " + sessionName);
        }
    }

    public void removeSession(int position) {
        if (position >= 0 && position < mSessions.size()) {
            String sessionName = mSessions.remove(position);
            notifyDataSetChanged();
            Log.d(LOG_TAG, "Removed session: " + sessionName);
        }
    }

    public void updateSessions(List<String> newSessions) {
        mSessions.clear();
        if (newSessions != null) {
            mSessions.addAll(newSessions);
        }
        // Ensure we always have at least one session
        if (mSessions.isEmpty()) {
            mSessions.add("AccIDE Terminal");
        }
        notifyDataSetChanged();
        Log.d(LOG_TAG, "Sessions updated, count: " + mSessions.size());
    }
}
