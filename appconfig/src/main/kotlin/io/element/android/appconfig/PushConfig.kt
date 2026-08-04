/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appconfig

object PushConfig {
    /**
     * Matrix push gateway (Sygnal) for Firebase FCM pushers.
     * Must end with /_matrix/push/v1/notify
     */
    const val FCM_PUSHER_HTTP_URL: String = "https://matrix.filewire.eu.org/_matrix/push/v1/notify"
}
