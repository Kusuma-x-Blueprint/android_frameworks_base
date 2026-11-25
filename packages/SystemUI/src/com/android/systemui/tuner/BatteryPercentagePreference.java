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

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.util.ArraySet;
import android.util.AttributeSet;

import androidx.preference.DropDownPreference;

import com.android.settingslib.fuelgauge.BatteryUtils;
import com.android.systemui.Dependency;
import com.android.systemui.statusbar.phone.ui.StatusBarIconController;
import com.android.systemui.tuner.TunerService.Tunable;

import lineageos.providers.LineageSettings;

public class BatteryPercentagePreference extends DropDownPreference implements Tunable {

    private static final String PERCENT_HIDDEN = "0";
    private static final String PERCENT_INSIDE = "1";
    private static final String PERCENT_NEXT = "2";

    private static final int BATTERY_STYLE_TEXT = 2;

    private final String mBattery;
    private boolean mBatteryPresent;
    private boolean mBatteryVisible = true;

    private final ContentObserver mObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            updateState();
        }
    };

    public BatteryPercentagePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mBattery = context.getString(com.android.internal.R.string.status_bar_battery);
        setEntryValues(new CharSequence[] {
                PERCENT_NEXT,
                PERCENT_INSIDE,
                PERCENT_HIDDEN
        });
        Intent intent = BatteryUtils.getBatteryIntent(context);
        if (intent != null) {
            mBatteryPresent = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true);
        }
    }

    @Override
    public void onAttached() {
        super.onAttached();
        Dependency.get(TunerService.class).addTunable(this, StatusBarIconController.ICON_HIDE_LIST);
        getContext().getContentResolver().registerContentObserver(
                LineageSettings.System.getUriFor(
                        LineageSettings.System.STATUS_BAR_SHOW_BATTERY_PERCENT),
                false, mObserver, ActivityManager.getCurrentUser());
        getContext().getContentResolver().registerContentObserver(
                LineageSettings.System.getUriFor(LineageSettings.System.STATUS_BAR_BATTERY_STYLE),
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
        mBatteryVisible = !hideList.contains(mBattery);
        updateState();
    }

    private void updateState() {
        final int batteryStyle = LineageSettings.System.getIntForUser(
                getContext().getContentResolver(),
                LineageSettings.System.STATUS_BAR_BATTERY_STYLE,
                0,
                ActivityManager.getCurrentUser());
        setVisible(mBatteryPresent && mBatteryVisible && batteryStyle != BATTERY_STYLE_TEXT);

        final int percentMode = LineageSettings.System.getIntForUser(
                getContext().getContentResolver(),
                LineageSettings.System.STATUS_BAR_SHOW_BATTERY_PERCENT,
                0,
                ActivityManager.getCurrentUser());
        setValue(String.valueOf(percentMode));
    }

    @Override
    protected boolean persistString(String value) {
        final int mode = Integer.parseInt(value);
        return LineageSettings.System.putIntForUser(
                getContext().getContentResolver(),
                LineageSettings.System.STATUS_BAR_SHOW_BATTERY_PERCENT,
                mode,
                ActivityManager.getCurrentUser());
    }
}
