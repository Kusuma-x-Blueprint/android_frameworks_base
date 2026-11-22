/*
 * Copyright (C) 2026 The LineageOS Project
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

package com.android.systemui.statusbar.pipeline.networktraffic.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import com.android.systemui.res.R
import com.android.systemui.statusbar.StatusBarIconView
import com.android.systemui.statusbar.pipeline.networktraffic.ui.binder.NetworkTrafficViewBinder
import com.android.systemui.statusbar.pipeline.shared.ui.view.ModernStatusBarView

class ModernStatusBarNetworkTrafficView(context: Context, attrs: AttributeSet?) :
    ModernStatusBarView(context, attrs) {

    override fun toString(): String {
        return "ModernStatusBarNetworkTrafficView(" +
            "slot='$slot', " +
            "isCollecting=${binding.isCollecting()}, " +
            "visibleState=${StatusBarIconView.getVisibleStateString(visibleState)}); " +
            "viewString=${super.toString()}"
    }

    companion object {
        @JvmStatic
        fun constructAndBind(
            context: Context,
            slot: String,
        ): ModernStatusBarNetworkTrafficView {
            return (LayoutInflater.from(context).inflate(
                    R.layout.status_bar_network_traffic_group,
                    null,
                ) as ModernStatusBarNetworkTrafficView)
                .apply {
                    initView(slot) { NetworkTrafficViewBinder.bind(this) }
                }
        }
    }
}
