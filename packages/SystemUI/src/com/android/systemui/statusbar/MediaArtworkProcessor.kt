/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.statusbar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import android.util.MathUtils
import com.android.internal.graphics.ColorUtils
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.notification.MediaNotificationProcessor
import javax.inject.Inject

private const val TAG = "MediaArtworkProcessor"

@SysUISingleton
class MediaArtworkProcessor @Inject constructor() {

    private var mArtworkCache: Bitmap? = null

    fun processArtwork(context: Context, artwork: Bitmap): Bitmap? {
        // Return cached artwork if available
        mArtworkCache?.let {
            return it
        }
    
        var inBitmap: Bitmap? = null
        try {
            @Suppress("DEPRECATION")
            val displaySize = Point()
            context.display?.getSize(displaySize)
    
            // Calculate the aspect ratio of the display
            val displayAspectRatio = displaySize.x.toFloat() / displaySize.y
    
            // Set DOWNSAMPLE based on display aspect ratio
            val DOWNSAMPLE = displayAspectRatio
    
            // Calculate the maximum allowed size for the artwork
            val maxWidth = displaySize.x * DOWNSAMPLE
            val maxHeight = displaySize.y * DOWNSAMPLE
    
            // Fit the artwork into the display size using the DOWNSAMPLE factor
            val rect = Rect(0, 0, artwork.width, artwork.height)
            MathUtils.fitRect(rect, Math.max(maxWidth.toInt(), maxHeight.toInt()))
    
            // Scale the artwork to fit within the calculated dimensions
            inBitmap = Bitmap.createScaledBitmap(artwork, rect.width(), rect.height(), true)
    
            // Create a mutable copy of the scaled bitmap
            val outBitmap = inBitmap.copy(inBitmap.config ?: Bitmap.Config.ARGB_8888, true)
    
            // Cache the result
            mArtworkCache = outBitmap
    
            return outBitmap
    
        } catch (ex: IllegalArgumentException) {
            Log.e(TAG, "Error while processing artwork", ex)
            return null
        } finally {
            inBitmap?.recycle()
        }
    }

    fun clearCache() {
        mArtworkCache?.recycle()
        mArtworkCache = null
    }
}
