/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.utils

import com.google.common.truth.Truth.assertThat
import io.element.android.features.call.impl.utils.isMatrixRtcIdle
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.room.aRoomInfo
import org.junit.Test

class RoomInfoMatrixRtcExtTest {
    @Test
    fun `isMatrixRtcIdle - true when no room call`() {
        assertThat(aRoomInfo(hasRoomCall = false).isMatrixRtcIdle()).isTrue()
    }

    @Test
    fun `isMatrixRtcIdle - true when room call but no participants`() {
        assertThat(
            aRoomInfo(hasRoomCall = true, activeRoomCallParticipants = emptyList()).isMatrixRtcIdle(),
        ).isTrue()
    }

    @Test
    fun `isMatrixRtcIdle - false when participants remain`() {
        assertThat(
            aRoomInfo(hasRoomCall = true, activeRoomCallParticipants = listOf(A_SESSION_ID)).isMatrixRtcIdle(),
        ).isFalse()
    }
}
