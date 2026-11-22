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

package com.android.systemui.statusbar.pipeline.networktraffic.ui.binder

import android.graphics.Color
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.res.R
import com.android.systemui.statusbar.StatusBarIconView
import com.android.systemui.statusbar.StatusBarIconView.STATE_HIDDEN
import com.android.systemui.statusbar.pipeline.shared.ui.binder.ModernStatusBarViewBinding
import com.android.systemui.statusbar.pipeline.shared.ui.binder.ModernStatusBarViewVisibilityHelper
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import org.lineageos.internal.statusbar.NetworkTraffic

object NetworkTrafficViewBinder {
    fun bind(view: ViewGroup): ModernStatusBarViewBinding {
        val trafficView = view.requireViewById<NetworkTraffic>(R.id.network_traffic_view)
        val dotView = view.requireViewById<StatusBarIconView>(R.id.status_bar_dot)

        @StatusBarIconView.VisibleState
        val visibilityState: MutableStateFlow<Int> = MutableStateFlow(STATE_HIDDEN)

        val iconTint: MutableStateFlow<Int> = MutableStateFlow(Color.WHITE)
        val decorTint: MutableStateFlow<Int> = MutableStateFlow(Color.WHITE)

        var isCollecting = false

        view.repeatWhenAttached {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                isCollecting = true

                launch {
                    visibilityState.collect { state ->
                        ModernStatusBarViewVisibilityHelper.setVisibilityState(
                            state,
                            trafficView,
                            dotView,
                        )
                    }
                }

                launch {
                    iconTint.collect { tint ->
                        trafficView.onIconTintChanged(tint)
                    }
                }

                launch {
                    decorTint.collect { tint ->
                        dotView.setDecorColor(tint)
                    }
                }

                try {
                    awaitCancellation()
                } finally {
                    isCollecting = false
                }
            }
        }

        trafficView.setOnTrafficVisibilityChangedListener { isVisible ->
            view.isVisible = isVisible
            view.requestLayout()
        }

        view.isVisible = trafficView.isTrafficVisible

        return object : ModernStatusBarViewBinding {
            override fun getShouldIconBeVisible(): Boolean {
                return trafficView.isTrafficVisible
            }

            override fun onVisibilityStateChanged(@StatusBarIconView.VisibleState state: Int) {
                visibilityState.value = state
            }

            override fun onIconTintChanged(newTint: Int, contrastTint: Int) {
                iconTint.value = newTint
            }

            override fun onDecorTintChanged(newTint: Int) {
                decorTint.value = newTint
            }

            override fun isCollecting(): Boolean {
                return isCollecting
            }
        }
    }
}
