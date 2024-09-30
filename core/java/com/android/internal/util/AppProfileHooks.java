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

package com.android.internal.util;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class AppProfileHooks {

    private static final String TAG = AppProfileHooks.class.getSimpleName();
    private static final String INTENT_ACTION_PROFILE_APP_TRIGGER = 
            "lineageos.extra.platform.intent.action.PROFILE_APP_TRIGGER";

    private static final long BOOT_TIME_THRESHOLD_MS = 90000; // 3 minutes (?)
    private static boolean isBootTimePassed = false;

    public static void setProfile(Context context) {
        final String packageName = context.getPackageName();

        if (TextUtils.isEmpty(packageName)) {
            return;
        }

        // Hold the code for ? minutes, because Settings.System can't be accessed
        // right away when device is in boot phase
        if (!isBootTimePassed) {
            if (SystemClock.uptimeMillis() >= BOOT_TIME_THRESHOLD_MS) {
                isBootTimePassed = true;
                Log.d(TAG, "System uptime exceeds threshold. Proceeding with profile trigger.");
            } else {
                Log.d(TAG, "System uptime is below threshold. Skipping profile trigger for now.");
                return;
            }
        }

        String profileUuid = getProfileUuidForApp(context, packageName);

        if (profileUuid != null) {
            Intent intent = new Intent(INTENT_ACTION_PROFILE_APP_TRIGGER);
            intent.putExtra("PROFILE_UUID", profileUuid);
            context.sendBroadcast(intent);
            Log.d(TAG, "App " + packageName + " is selected and profile with UUID: " + profileUuid + " will be triggered");
        }
    }

    private static String getProfileUuidForApp(Context context, String packageName) {
        String jsonString = Settings.System.getString(context.getContentResolver(), 
                Settings.System.PROFILE_APP_TRIGGER_LIST);

        if (TextUtils.isEmpty(jsonString)) {
            Log.w(TAG, "Profile App Trigger list is empty or not found.");
            return null;
        }

        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            JSONArray instances = jsonObject.names();
            for (int i = 0; i < instances.length(); i++) {
                String instanceName = instances.getString(i);
                JSONArray appArray = jsonObject.getJSONArray(instanceName);
                for (int j = 0; j < appArray.length(); j++) {
                    if (appArray.getString(j).equals(packageName)) {
                        return instanceName;
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse JSON", e);
        }
        return null;
    }
}
