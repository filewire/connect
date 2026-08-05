/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appconfig

object PushConfig {
    /**
     * Matrix pusher app_id — must match a key under `apps:` in Sygnal config.
     * Note: pusher_app_id cannot exceed 64 chars.
     */
    const val PUSHER_APP_ID: String = "org.filewire.connect"

    /**
     * Matrix push gateway (Sygnal) for Firebase FCM pushers.
     * Must end with /_matrix/push/v1/notify
     *
     * Use the Docker-internal URL so Synapse can reach Sygnal on matrix-network.
     * No Cloudflare Tunnel / published ports needed for Sygnal (Synapse calls this URL).
     * Troubleshoot "push loopback" from the phone may still fail — that test hits a
     * public URL; real message push uses Synapse → Sygnal on the Docker network.
     */
    const val FCM_PUSHER_HTTP_URL: String = "http://matrix-sygnal:5000/_matrix/push/v1/notify"
}
