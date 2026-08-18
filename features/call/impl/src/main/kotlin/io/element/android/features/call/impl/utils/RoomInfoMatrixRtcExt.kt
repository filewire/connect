/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.utils

import io.element.android.libraries.matrix.api.room.RoomInfo

/**
 * MatrixRTC is idle when there is no active room call, or no participants remain in the session.
 * Used before starting a new call so remote clients (e.g. Element desktop) drop the Join banner.
 */
internal fun RoomInfo.isMatrixRtcIdle(): Boolean {
    return !hasRoomCall || activeRoomCallParticipants.isEmpty()
}
