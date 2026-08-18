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
     * Rapid hang-up → recall otherwise races WebView teardown and can crash the renderer.
     */
    const val CALL_REJOIN_COOLDOWN_SECONDS = 2

    /**
     * Minimum time to keep the call WebView alive after sending hangup so Element Call can
     * process `im.vector.hangup` before we poll for MatrixRTC leave.
     */
    const val CALL_HANGUP_MIN_GRACE_SECONDS = 2

    /**
     * Max time to poll for MatrixRTC leave when needed. Hang-up UI no longer blocks on this.
     */
    const val CALL_LEAVE_SETTLE_MAX_SECONDS = 25

    /**
     * Reserved for optional recall idle wait. Hang-up / recall UI does not block on this.
     */
    const val CALL_RECALL_IDLE_WAIT_MAX_SECONDS = 8

    /** Poll interval while waiting for the room call to go idle. */
    const val CALL_ROOM_IDLE_POLL_INTERVAL_MS = 250L
}
