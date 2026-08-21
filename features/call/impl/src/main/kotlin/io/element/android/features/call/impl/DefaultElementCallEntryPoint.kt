/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.call.api.CallData
import io.element.android.features.call.api.ElementCallEntryPoint
import io.element.android.features.call.impl.notifications.CallNotificationData
import io.element.android.features.call.impl.utils.ActiveCallManager
import io.element.android.features.call.impl.utils.IntentProvider
import io.element.android.features.call.impl.utils.OutgoingCallGate
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.UserId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

@ContributesBinding(AppScope::class)
class DefaultElementCallEntryPoint(
    @ApplicationContext private val context: Context,
    private val activeCallManager: ActiveCallManager,
    private val matrixClientProvider: MatrixClientProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : ElementCallEntryPoint {
    companion object {
        const val EXTRA_CALL_TYPE = "EXTRA_CALL_TYPE"
        const val REQUEST_CODE = 2255
    }

    override fun startCall(callData: CallData) {
        appCoroutineScope.launch {
            // If room already has a live call and we are not forcing a new one after local hang-up,
            // mark as answering so CallScreenPresenter uses JOIN_EXISTING instead of START_CALL.
            if (!activeCallManager.shouldForceStartNewCall(callData.roomId)) {
                val hasRoomCall = matrixClientProvider.getOrRestore(callData.sessionId).getOrNull()
                    ?.getRoom(callData.roomId)
                    ?.info()?.hasRoomCall == true
                if (hasRoomCall) {
                    Timber.d("Room %s has active call; marking JOIN for startCall path", callData.roomId)
                    activeCallManager.markAnsweringIncoming(callData.roomId)
                }
            }
            when (val gate = activeCallManager.awaitReadyForOutgoingCall(callData)) {
                OutgoingCallGate.Proceed,
                OutgoingCallGate.AlreadyInThisCall -> {
                    Timber.d("Starting call activity (%s) for %s", gate, callData.roomId)
                    context.startActivity(IntentProvider.createIntent(context, callData))
                }
                OutgoingCallGate.BusyWithOtherCall -> {
                    Timber.w("Ignoring startCall for %s — already in another call", callData.roomId)
                }
            }
        }
    }

    override suspend fun handleIncomingCall(
        callData: CallData,
        eventId: EventId,
        senderId: UserId,
        roomName: String?,
        senderName: String?,
        avatarUrl: String?,
        timestamp: Long,
        expirationTimestamp: Long,
        notificationChannelId: String,
        textContent: String?,
    ) {
        val incomingCallNotificationData = CallNotificationData(
            sessionId = callData.sessionId,
            roomId = callData.roomId,
            eventId = eventId,
            senderId = senderId,
            roomName = roomName,
            senderName = senderName,
            avatarUrl = avatarUrl,
            timestamp = timestamp,
            expirationTimestamp = expirationTimestamp,
            notificationChannelId = notificationChannelId,
            textContent = textContent,
            audioOnly = callData.isAudioCall,
        )
        activeCallManager.registerIncomingCall(notificationData = incomingCallNotificationData)
    }
}
