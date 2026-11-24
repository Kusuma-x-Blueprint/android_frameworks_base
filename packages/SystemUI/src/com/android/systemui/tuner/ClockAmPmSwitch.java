/*
 * Copyright (C) 2026 Pelita
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

import android.app.ActivityManager;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.ArraySet;
import android.util.AttributeSet;

import androidx.preference.SwitchPreferenceCompat;

import com.android.systemui.Dependency;
import com.android.systemui.statusbar.phone.ui.StatusBarIconController;
import com.android.systemui.tuner.TunerService.Tunable;

import lineageos.providers.LineageSettings;

public class ClockAmPmSwitch extends SwitchPreferenceCompat implements Tunable {

    private static final int AM_PM_STYLE_NORMAL = 0;
    private static final int AM_PM_STYLE_GONE = 2;

    private boolean mClockVisible = true;

    private final ContentObserver mObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            updateState();
        }
    };

    public ClockAmPmSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onAttached() {
        super.onAttached();
        Dependency.get(TunerService.class).addTunable(this, StatusBarIconController.ICON_HIDE_LIST);
        getContext().getContentResolver().registerContentObserver(
                LineageSettings.System.getUriFor(LineageSettings.System.STATUS_BAR_AM_PM),
                false, mObserver, ActivityManager.getCurrentUser());
        getContext().getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.TIME_12_24),
                false, mObserver, ActivityManager.getCurrentUser());
        updateState();
    }

    @Override
    public void onDetached() {
        getContext().getContentResolver().unregisterContentObserver(mObserver);
        Dependency.get(TunerService.class).removeTunable(this);
        super.onDetached();
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        if (!StatusBarIconController.ICON_HIDE_LIST.equals(key)) {
            return;
        }
        ArraySet<String> hideList = StatusBarIconController.getIconHideList(getContext(), newValue);
        mClockVisible = !hideList.contains("clock");
        updateVisibility();
    }

    private void updateVisibility() {
        final boolean is24Hour = DateFormat.is24HourFormat(getContext());
        setVisible(!is24Hour && mClockVisible);
    }

    private void updateState() {
        updateVisibility();

        final int style = LineageSettings.System.getIntForUser(
                getContext().getContentResolver(),
                LineageSettings.System.STATUS_BAR_AM_PM,
                AM_PM_STYLE_GONE,
                ActivityManager.getCurrentUser());
        setChecked(style != AM_PM_STYLE_GONE);
    }

    @Override
    protected boolean persistBoolean(boolean value) {
        final int style = value ? AM_PM_STYLE_NORMAL : AM_PM_STYLE_GONE;
        return LineageSettings.System.putIntForUser(
                getContext().getContentResolver(),
                LineageSettings.System.STATUS_BAR_AM_PM,
                style,
                ActivityManager.getCurrentUser());
    }
}
