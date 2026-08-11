/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.utils

import android.view.ViewGroup
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.element.android.libraries.core.extensions.runCatchingExceptions
import timber.log.Timber

/**
 * Tear down a call [WebView] without racing a new instance.
 *
 * Destroying a live Element Call WebView too abruptly (or while creating another one)
 * has been observed to crash the Chromium renderer and, if unhandled, the whole process.
 */
internal fun WebView.safelyDestroy(javascriptInterfaces: Collection<String> = emptyList()) {
    runCatchingExceptions {
        stopLoading()
        javascriptInterfaces.forEach { name ->
            runCatchingExceptions { removeJavascriptInterface(name) }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            runCatchingExceptions {
                WebViewCompat.removeWebMessageListener(this, WebViewWidgetMessageInterceptor.LISTENER_NAME)
            }
        }
        // Blank the document before detach/destroy so media / WebRTC tear down more cleanly.
        loadUrl("about:blank")
        onPause()
        removeAllViews()
        (parent as? ViewGroup)?.removeView(this)
        destroy()
    }.onFailure {
        Timber.e(it, "Failed to safely destroy call WebView")
    }
}
