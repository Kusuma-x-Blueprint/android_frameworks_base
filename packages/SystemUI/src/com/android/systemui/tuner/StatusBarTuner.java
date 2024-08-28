/*
 * Copyright (C) 2017 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.tuner;

import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.MenuItem;

import androidx.preference.DropDownPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.R;

public class StatusBarTuner extends PreferenceFragment {

    private static final String NETWORK_TRAFFIC = "network_traffic_enabled";

    private DropDownPreference mNetMonitor;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);

        mNetMonitor = (DropDownPreference) findPreference(NETWORK_TRAFFIC);
        int networkTrafficEnabled = Settings.System.getIntForUser(getActivity().getContentResolver(),
                    Settings.System.NETWORK_TRAFFIC_ENABLED, 1, UserHandle.USER_CURRENT);
        int networkTrafficAutohide = Settings.System.getIntForUser(getActivity().getContentResolver(),
                    Settings.System.NETWORK_TRAFFIC_AUTOHIDE, 1, UserHandle.USER_CURRENT);
        int networkTrafficValue;
        if (networkTrafficEnabled == 1 && networkTrafficAutohide == 1) {
            networkTrafficValue = 1;
        } else if (networkTrafficEnabled == 0 && networkTrafficAutohide == 0) {
            networkTrafficValue = 2;
        } else {
            networkTrafficValue = 0;
        }

        mNetMonitor.setValue(String.valueOf(networkTrafficValue));
        mNetMonitor.setSummary(mNetMonitor.getEntry());

        mNetMonitor.setOnPreferenceChangeListener((preference, newValue) -> {
            int selectedValue = Integer.parseInt((String) newValue);
            int enabled = (selectedValue == 2) ? 0 : 1;
            int autohide = (selectedValue == 1) ? 1 : 0;

            Settings.System.putIntForUser(getActivity().getContentResolver(),
                    Settings.System.NETWORK_TRAFFIC_ENABLED, enabled, UserHandle.USER_CURRENT);
            Settings.System.putIntForUser(getActivity().getContentResolver(),
                    Settings.System.NETWORK_TRAFFIC_AUTOHIDE, autohide, UserHandle.USER_CURRENT);
            preference.setSummary(mNetMonitor.getEntries()[mNetMonitor.findIndexOfValue((String) newValue)]);
            return true;
        });
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.status_bar_prefs);
    }

    @Override
    public void onResume() {
        super.onResume();
        MetricsLogger.visibility(getContext(), MetricsEvent.TUNER, true);
    }

    @Override
    public void onPause() {
        super.onPause();
        MetricsLogger.visibility(getContext(), MetricsEvent.TUNER, false);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mNetMonitor) {
            // The OnPreferenceChangeListener handles the click
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

}
