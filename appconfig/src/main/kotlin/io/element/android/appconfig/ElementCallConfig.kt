/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appconfig

object ElementCallConfig {
    /**
     * The default duration of a ringing call in seconds before it's automatically dismissed.
     * Also used as the upper cap for MatrixRTC notification `lifetime` (MSC4075).
     */
    const val RINGING_CALL_DURATION_SECONDS = 90

    /**
     * How long to wait for Element Call to finish loading in the WebView before showing
     * a recoverable error. Slow networks / JWT / ICE often need more than a few seconds.
     */
    const val CALL_WIDGET_LOAD_TIMEOUT_SECONDS = 45

    /**
     * After hanging up, wait this long before starting another call UI for the same room.
     * Rapid hang-up → recall otherwise races WebView teardown and can crash the renderer
     * (and the whole app if [android.webkit.WebViewClient.onRenderProcessGone] is not handled).
     */
    const val CALL_REJOIN_COOLDOWN_SECONDS = 5

    /**
     * After the user hangs up, keep the call WebView alive briefly so Element Call can send
     * MatrixRTC leave / end-ring events before the Activity is destroyed.
     */
    const val CALL_HANGUP_GRACE_SECONDS = 5

    /**
     * Max time to wait for our session to leave the room's active call participants list
     * before opening a new outgoing call in the same room.
     */
    const val CALL_LEAVE_SETTLE_MAX_SECONDS = 20
}
