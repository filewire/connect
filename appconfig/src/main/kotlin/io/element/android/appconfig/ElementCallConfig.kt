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
}
