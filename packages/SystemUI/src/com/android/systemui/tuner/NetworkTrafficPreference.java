/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.tuner;

import android.content.Context;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.AttributeSet;

import androidx.preference.DropDownPreference;

import com.android.systemui.Dependency;
import com.android.systemui.statusbar.phone.ui.StatusBarIconController;

import lineageos.providers.LineageSettings;

public class NetworkTrafficPreference extends DropDownPreference implements TunerService.Tunable {

    private static final String BLACKLIST = "blacklist";
    private static final String AUTOHIDE = "autohide";
    private static final String ON = "on";

    private final String mSlot;
    private boolean mTrafficEnabled;
    private boolean mAutoHide;
    private ArraySet<String> mHideList = new ArraySet<>();
    private boolean mHasSetValue;

    public NetworkTrafficPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mSlot = context.getString(com.android.internal.R.string.status_bar_network_traffic);
        setEntryValues(new CharSequence[] { ON, AUTOHIDE, BLACKLIST });
    }

    @Override
    public void onAttached() {
        super.onAttached();
        mAutoHide = LineageSettings.Secure.getInt(getContext().getContentResolver(),
                LineageSettings.Secure.NETWORK_TRAFFIC_AUTOHIDE, 0) == 1;
        Dependency.get(TunerService.class).addTunable(this, StatusBarIconController.ICON_HIDE_LIST);
    }

    @Override
    public void onDetached() {
        Dependency.get(TunerService.class).removeTunable(this);
        super.onDetached();
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        if (StatusBarIconController.ICON_HIDE_LIST.equals(key)) {
            mHideList = StatusBarIconController.getIconHideList(getContext(), newValue);
            final int mode = LineageSettings.Secure.getInt(getContext().getContentResolver(),
                    LineageSettings.Secure.NETWORK_TRAFFIC_MODE, 0);
            mTrafficEnabled = !mHideList.contains(mSlot) && mode != 0;
        }
        if (!mHasSetValue) {
            mHasSetValue = true;
            if (!mTrafficEnabled) {
                setValue(BLACKLIST);
            } else if (mAutoHide) {
                setValue(AUTOHIDE);
            } else {
                setValue(ON);
            }
        }
    }

    @Override
    protected boolean persistString(String value) {
        final boolean autoHide = AUTOHIDE.equals(value);
        mAutoHide = autoHide;
        if (BLACKLIST.equals(value)) {
            mHideList.add(mSlot);
            LineageSettings.Secure.putInt(getContext().getContentResolver(),
                    LineageSettings.Secure.NETWORK_TRAFFIC_MODE, 0);
        } else {
            mHideList.remove(mSlot);
            LineageSettings.Secure.putInt(getContext().getContentResolver(),
                    LineageSettings.Secure.NETWORK_TRAFFIC_MODE, 3);
            LineageSettings.Secure.putInt(getContext().getContentResolver(),
                    LineageSettings.Secure.NETWORK_TRAFFIC_AUTOHIDE, autoHide ? 1 : 0);
        }
        Dependency.get(TunerService.class).setValue(StatusBarIconController.ICON_HIDE_LIST,
                TextUtils.join(",", mHideList));
        return true;
    }
}
