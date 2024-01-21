/**
 * Copyright (C) 2020-2022 crDroid Android Project
 *
 * @author: Randall Rushing <randall.rushing@gmail.com>
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
 *
 * Generalized renderer color state management and color event dispatch
 */

package com.android.systemui.pulse;

import com.android.systemui.Dependency;
import com.android.systemui.statusbar.policy.ConfigurationController;

import android.content.Context;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.TypedValue;

public class ColorController extends ContentObserver
        implements ColorAnimator.ColorAnimationListener,
        ConfigurationController.ConfigurationListener {

    private Context mContext;
    private Renderer mRenderer;
    private int mAccentColor;

    public ColorController(Context context, Handler handler) {
        super(handler);
        mContext = context;
        mAccentColor = getAccentColor();
        updateSettings();
        Dependency.get(ConfigurationController.class).addCallback(this);
    }

    void setRenderer(Renderer renderer) {
        mRenderer = renderer;
        notifyRenderer();
    }

    void updateSettings() {
        notifyRenderer();
    }

    void notifyRenderer() {
        if (mRenderer != null) {
            mRenderer.onUpdateColor(mAccentColor);
        }
    }

    int getAccentColor() {
        final TypedValue value = new TypedValue();
        mContext.getTheme().resolveAttribute(android.R.attr.colorAccent, value, true);
        return value.data;
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        updateSettings();
    }

    @Override
    public void onConfigChanged(Configuration newConfig) {
        final int lastAccent = mAccentColor;
        final int currentAccent = getAccentColor();
        if (lastAccent != currentAccent) {
            mAccentColor = currentAccent;
            if (mRenderer != null) {
                mRenderer.onUpdateColor(mAccentColor);
            }
        }
    }

    @Override
    public void onColorChanged(ColorAnimator colorAnimator, int color) {
        if (mRenderer != null) {
            mRenderer.onUpdateColor(color);
        }
    }
}
