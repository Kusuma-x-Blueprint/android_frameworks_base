/*
 * Copyright (C) 2024 Kusuma
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

package com.android.systemui.qs.tiles;

import android.annotation.NonNull;
import android.content.Context;
import android.content.Intent;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.telephony.SubscriptionManager;
import android.text.BidiFormatter;
import android.text.format.Formatter;
import android.text.format.Formatter.BytesResult;
import android.util.Log;
import android.view.View;

import com.android.internal.jank.InteractionJankMonitor;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settingslib.net.DataUsageController;
import com.android.systemui.animation.ActivityLaunchAnimator;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.qs.QSHost;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.R;
import com.android.systemui.statusbar.connectivity.SignalCallback;
import com.android.systemui.statusbar.connectivity.NetworkController;
import com.android.systemui.statusbar.connectivity.WifiIndicators;

import javax.inject.Inject;

/** Quick settings tile: Data usage **/
public class DataUsageTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "datausage";

    protected final NetworkController mController;
    private DataUsageController mDataController;

    protected final WifiSignalCallback mSignalCallback = new WifiSignalCallback();
    private boolean mExpectDisabled;

    @Inject
    public DataUsageTile(
            QSHost host,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            NetworkController networkController,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger
    ) {
        super(host, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
        mController = networkController;
        mController.observe(getLifecycle(), mSignalCallback);
        mDataController = new DataUsageController(mContext);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.EXTRA_METRICS;
    }

    @Override
    public BooleanState newTileState() {
        BooleanState state = new BooleanState();
        state.handlesLongClick = false;
        return state;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void handleClick(View view) {
        ActivityLaunchAnimator.Controller animationController =
                view != null ? ActivityLaunchAnimator.Controller.fromView(view,
                        InteractionJankMonitor.CUJ_SHADE_APP_LAUNCH_FROM_QS_TILE) : null;
        Intent intent = new Intent();
        intent.setClassName("com.android.settings",
                "com.android.settings.Settings$DataUsageSummaryActivity");
        mActivityStarter.postStartActivityDismissingKeyguard(intent, 0,
                animationController);
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.data_usage_tile_title);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        if (DEBUG) Log.d(TAG, "handleUpdateState arg=" + arg);
        final CallbackInfo cb = mSignalCallback.mInfo;
        if (mExpectDisabled) {
            if (cb.enabled) {
                return; // Ignore updates until disabled event occurs.
            } else {
                mExpectDisabled = false;
            }
        }
        boolean wifiConnected = cb.enabled;

        if (!wifiConnected) {
            mDataController.setSubscriptionId(SubscriptionManager.getDefaultDataSubscriptionId());
        }

        DataUsageController.DataUsageInfo info = wifiConnected ? 
                mDataController.getWifiDailyDataUsageInfo() : mDataController.getDailyDataUsageInfo();
        boolean showData = info != null && info.usageLevel >= 0;
        String suffix = mContext.getResources().getString(wifiConnected ? 
                R.string.usage_wifi_default_suffix : R.string.usage_data_default_suffix);

        state.label = mContext.getString(R.string.data_usage_tile_title);
        state.contentDescription =  mContext.getString(R.string.data_usage_tile_title);
        if (showData) {
            state.secondaryLabel = formatDataUsage(info.usageLevel) + " (" + suffix + ")";
        } else {
            state.secondaryLabel = mContext.getString(R.string.usage_data_unknown);
        }
        state.icon = ResourceIcon.get(R.drawable.ic_datausage);
        state.value = true;
        state.state = Tile.STATE_INACTIVE;
        state.forceExpandIcon = true;
    }

    private CharSequence formatDataUsage(long byteValue) {
        final BytesResult res = Formatter.formatBytes(mContext.getResources(), byteValue,
                Formatter.FLAG_IEC_UNITS);
        return BidiFormatter.getInstance().unicodeWrap(mContext.getString(
                com.android.internal.R.string.fileSizeSuffix, res.value, res.units));
    }

    protected static final class CallbackInfo {
        boolean enabled;

        @Override
        public String toString() {
            return new StringBuilder("CallbackInfo[")
                    .append("enabled=").append(enabled)
                    .append(']').toString();
        }
    }
    protected final class WifiSignalCallback implements SignalCallback {
        final CallbackInfo mInfo = new CallbackInfo();

        @Override
        public void setWifiIndicators(@NonNull WifiIndicators indicators) {
            if (DEBUG) Log.d(TAG, "onWifiSignalChanged enabled=" + indicators.enabled);
            mInfo.enabled = indicators.enabled;
            refreshState();
        }
    }

}
