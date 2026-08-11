/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.ui

import io.element.android.libraries.architecture.AsyncData

/**
 * Typed call-screen errors so the UI can show a clear title/message and Retry when useful.
 */
sealed interface CallScreenError {
    /** Element Call WebView did not become ready within the load timeout. */
    data object LoadTimeout : CallScreenError

    /** WebView reported a load/HTTP/SSL failure before the call UI was ready. */
    data class WebView(val details: String?) : CallScreenError

    /** Widget URL / MatrixRTC setup failed before the WebView could start. */
    data class Setup(val details: String?) : CallScreenError
}

data class CallScreenState(
    val urlState: AsyncData<String>,
    val callError: CallScreenError?,
    val userAgent: String,
    val isCallActive: Boolean,
    /**
     * Bumped on Retry so Compose recreates the WebView (required after a render-process crash).
     */
    val webViewInstanceKey: Int = 0,
    val eventSink: (CallScreenEvent) -> Unit,
) {
    /** All current call errors offer Retry; the user can also hang up. */
    val canRetryError: Boolean
        get() = callError != null
}
