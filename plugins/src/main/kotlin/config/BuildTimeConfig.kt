/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package config

object BuildTimeConfig {
    /** Android applicationId — must match Firebase Android app package names. */
    const val APPLICATION_ID = "org.filewire.connect"
    const val APPLICATION_NAME = "Connect"

    const val GOOGLE_APP_ID_RELEASE = "1:247291343746:android:c8165338a10fd4f473559b"
    const val GOOGLE_APP_ID_DEBUG = "1:247291343746:android:9bb275e8eb91d66473559b"
    const val GOOGLE_APP_ID_NIGHTLY = GOOGLE_APP_ID_RELEASE

    /** OAuth redirect scheme base (reversed domain). Used if homeserver supports OIDC/MAS login. */
    val METADATA_HOST_REVERSED: String? = "eu.org.filewire.connect"
    val URL_WEBSITE: String? = "https://filewire.eu.org"
    val URL_LOGO: String? = null
    val URL_COPYRIGHT: String? = "https://filewire.eu.org"
    val URL_ACCEPTABLE_USE: String? = "https://filewire.eu.org/terms"
    val URL_PRIVACY: String? = "https://filewire.eu.org/privacy"
    val URL_POLICY: String? = "https://filewire.eu.org/privacy"
    val OAUTH_CLIENT_URL_PATH: String? = null
    val SERVICES_MAPTILER_BASE_URL: String? = null
    val SERVICES_MAPTILER_APIKEY: String? = null
    val SERVICES_MAPTILER_LIGHT_MAPID: String? = null
    val SERVICES_MAPTILER_DARK_MAPID: String? = null
    val SERVICES_POSTHOG_HOST: String? = null
    val SERVICES_POSTHOG_APIKEY: String? = null
    val SERVICES_SENTRY_DSN: String? = null
    val SERVICES_SENTRY_DSN_RUST: String? = null
    val BUG_REPORT_URL: String? = null
    val BUG_REPORT_APP_NAME: String? = "Connect"

    const val PUSH_CONFIG_INCLUDE_FIREBASE = true
    const val PUSH_CONFIG_INCLUDE_UNIFIED_PUSH = true
}
