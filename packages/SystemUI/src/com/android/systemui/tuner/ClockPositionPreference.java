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
import android.database.ContentObserver;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.Surface;
import android.view.View;

import androidx.preference.DropDownPreference;

import com.android.systemui.Dependency;
import com.android.systemui.res.R;
import com.android.systemui.statusbar.phone.ui.StatusBarIconController;
import com.android.systemui.tuner.TunerService.Tunable;

import lineageos.providers.LineageSettings;

public class ClockPositionPreference extends DropDownPreference implements Tunable {

    private static final String POSITION_LEFT = "2";
    private static final String POSITION_CENTER = "1";
    private static final String POSITION_RIGHT = "0";

    private final ContentObserver mObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            updateState();
        }
    };

    public ClockPositionPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        updateEntries();
    }

    @Override
    public void onAttached() {
        super.onAttached();
        updateEntries();
        Dependency.get(TunerService.class).addTunable(this, StatusBarIconController.ICON_HIDE_LIST);
        getContext().getContentResolver().registerContentObserver(
                LineageSettings.System.getUriFor(LineageSettings.System.STATUS_BAR_CLOCK),
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
        setVisible(!hideList.contains("clock"));
    }

    private void updateEntries() {
        final boolean disallowCenteredClock = hasCenteredCutout();
        final boolean isRtl = getContext().getResources().getConfiguration().getLayoutDirection()
                == View.LAYOUT_DIRECTION_RTL;

        if (disallowCenteredClock) {
            setEntries(isRtl ? R.array.clock_position_options_notch_rtl
                    : R.array.clock_position_options_notch);
            setEntryValues(new CharSequence[] { POSITION_LEFT, POSITION_RIGHT });
        } else {
            setEntries(isRtl ? R.array.clock_position_options_rtl
                    : R.array.clock_position_options);
            setEntryValues(new CharSequence[] { POSITION_LEFT, POSITION_CENTER, POSITION_RIGHT });
        }
    }

    private void updateState() {
        updateEntries();
        int position = LineageSettings.System.getIntForUser(
                getContext().getContentResolver(),
                LineageSettings.System.STATUS_BAR_CLOCK,
                2,
                ActivityManager.getCurrentUser());
        if (hasCenteredCutout() && position == 1) {
            position = 2;
        }
        setValue(String.valueOf(position));
    }

    @Override
    protected boolean persistString(String value) {
        final int position = Integer.parseInt(value);
        return LineageSettings.System.putIntForUser(
                getContext().getContentResolver(),
                LineageSettings.System.STATUS_BAR_CLOCK,
                position,
                ActivityManager.getCurrentUser());
    }

    private boolean hasCenteredCutout() {
        Display display = getContext().getDisplay();
        if (display == null) {
            return false;
        }
        DisplayCutout cutout = display.getCutout();
        if (cutout != null) {
            Point realSize = new Point();
            display.getRealSize(realSize);

            switch (display.getRotation()) {
                case Surface.ROTATION_0: {
                    Rect rect = cutout.getBoundingRectTop();
                    return !(rect.left <= 0 || rect.right >= realSize.x);
                }
                case Surface.ROTATION_90: {
                    Rect rect = cutout.getBoundingRectLeft();
                    return !(rect.top <= 0 || rect.bottom >= realSize.y);
                }
                case Surface.ROTATION_180: {
                    Rect rect = cutout.getBoundingRectBottom();
                    return !(rect.left <= 0 || rect.right >= realSize.x);
                }
                case Surface.ROTATION_270: {
                    Rect rect = cutout.getBoundingRectRight();
                    return !(rect.top <= 0 || rect.bottom >= realSize.y);
                }
            }
        }
        return false;
    }
}
