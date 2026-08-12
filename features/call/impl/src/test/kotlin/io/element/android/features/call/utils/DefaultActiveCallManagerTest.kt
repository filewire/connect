/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.utils

import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.element.android.features.call.api.CallData
import io.element.android.features.call.impl.notifications.RingingCallNotificationCreator
import io.element.android.features.call.impl.notifications.aCallNotificationData
import io.element.android.features.call.impl.utils.ActiveCall
import io.element.android.features.call.impl.utils.CallState
import io.element.android.features.call.impl.utils.DefaultActiveCallManager
import io.element.android.features.call.impl.utils.DefaultCurrentCallService
import io.element.android.features.call.impl.utils.OutgoingCallGate
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.test.AN_EVENT_ID
import io.element.android.libraries.matrix.test.AN_EVENT_ID_2
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_ROOM_ID_2
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.FakeMatrixClientProvider
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.aRoomInfo
import io.element.android.libraries.matrix.ui.media.test.FakeImageLoaderHolder
import io.element.android.libraries.push.api.notifications.ForegroundServiceType
import io.element.android.libraries.push.api.notifications.NotificationIdProvider
import io.element.android.libraries.push.test.notifications.FakeOnMissedCallNotificationHandler
import io.element.android.libraries.push.test.notifications.push.FakeNotificationBitmapLoader
import io.element.android.services.appnavstate.test.FakeAppForegroundStateService
import io.element.android.services.toolbox.test.systemclock.A_FAKE_TIMESTAMP
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.lambda.value
import io.element.android.tests.testutils.plantTestTimber
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.robolectric.Shadows.shadowOf

class DefaultActiveCallManagerTest : RobolectricTest() {
    private val notificationId = NotificationIdProvider.getForegroundServiceNotificationId(ForegroundServiceType.INCOMING_CALL)

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `registerIncomingCall - sets the incoming call as active`() = runTest {
        setupShadowPowerManager()
        val notificationManagerCompat = mockk<NotificationManagerCompat>(relaxed = true)
        val manager = createActiveCallManager(notificationManagerCompat = notificationManagerCompat)

        assertThat(manager.activeWakeLock?.isHeld).isFalse()
        assertThat(manager.activeCall.value).isNull()

        val callNotificationData = aCallNotificationData()
        manager.registerIncomingCall(callNotificationData)

        assertThat(manager.activeCall.value).isEqualTo(
            ActiveCall(
                callData = CallData(
                    sessionId = callNotificationData.sessionId,
                    roomId = callNotificationData.roomId,
                    isAudioCall = false,
                ),
                callState = CallState.Ringing(callNotificationData)
            )
        )

        runCurrent()

        assertThat(manager.activeWakeLock?.isHeld).isTrue()
        verify { notificationManagerCompat.notify(notificationId, any()) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `registerIncomingCall - sets the incoming audio call as active`() = runTest {
        setupShadowPowerManager()
        val notificationManagerCompat = mockk<NotificationManagerCompat>(relaxed = true)
        val manager = createActiveCallManager(notificationManagerCompat = notificationManagerCompat)

        val callNotificationData = aCallNotificationData(audioOnly = true)
        manager.registerIncomingCall(callNotificationData)

        assertThat(manager.activeCall.value).isEqualTo(
            ActiveCall(
                callData = CallData(
                    sessionId = callNotificationData.sessionId,
                    roomId = callNotificationData.roomId,
                    isAudioCall = true,
                ),
                callState = CallState.Ringing(callNotificationData)
            )
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `registerIncomingCall - when there is an already active call adds missed call notification`() = runTest {
        val addMissedCallNotificationLambda = lambdaRecorder<SessionId, RoomId, EventId, Unit> { _, _, _ -> }
        val onMissedCallNotificationHandler = FakeOnMissedCallNotificationHandler(addMissedCallNotificationLambda = addMissedCallNotificationLambda)
        val manager = createActiveCallManager(
            onMissedCallNotificationHandler = onMissedCallNotificationHandler,
        )

        // Register existing call
        val callNotificationData = aCallNotificationData()
        manager.registerIncomingCall(callNotificationData)
        val activeCall = manager.activeCall.value

        // Now add a new call
        manager.registerIncomingCall(aCallNotificationData(roomId = A_ROOM_ID_2))

        assertThat(manager.activeCall.value).isEqualTo(activeCall)
        assertThat(manager.activeCall.value?.callData?.roomId).isNotEqualTo(A_ROOM_ID_2)

        advanceTimeBy(1)

        addMissedCallNotificationLambda.assertions()
            .isCalledOnce()
            .with(value(A_SESSION_ID), value(A_ROOM_ID_2), value(AN_EVENT_ID))
    }

    @Test
    fun `incomingCallTimedOut - when there isn't an active call does nothing`() = runTest {
        val addMissedCallNotificationLambda = lambdaRecorder<SessionId, RoomId, EventId, Unit> { _, _, _ -> }
        val manager = createActiveCallManager(
            onMissedCallNotificationHandler = FakeOnMissedCallNotificationHandler(addMissedCallNotificationLambda = addMissedCallNotificationLambda)
        )

        manager.incomingCallTimedOut(displayMissedCallNotification = true)

        addMissedCallNotificationLambda.assertions().isNeverCalled()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `incomingCallTimedOut - when there is an active call removes it and adds a missed call notification`() = runTest {
        setupShadowPowerManager()
        val notificationManagerCompat = mockk<NotificationManagerCompat>(relaxed = true)
        val addMissedCallNotificationLambda = lambdaRecorder<SessionId, RoomId, EventId, Unit> { _, _, _ -> }
        val manager = createActiveCallManager(
            onMissedCallNotificationHandler = FakeOnMissedCallNotificationHandler(addMissedCallNotificationLambda = addMissedCallNotificationLambda),
            notificationManagerCompat = notificationManagerCompat,
        )

        manager.registerIncomingCall(aCallNotificationData())
        assertThat(manager.activeCall.value).isNotNull()
        assertThat(manager.activeWakeLock?.isHeld).isTrue()

        manager.incomingCallTimedOut(displayMissedCallNotification = true)
        advanceTimeBy(1)

        assertThat(manager.activeCall.value).isNull()
        assertThat(manager.activeWakeLock?.isHeld).isFalse()
        addMissedCallNotificationLambda.assertions().isCalledOnce()
        verify { notificationManagerCompat.cancel(notificationId) }
    }

    @Test
    fun `hangUpCall - removes existing call if the CallData matches`() = runTest {
        setupShadowPowerManager()
        val notificationManagerCompat = mockk<NotificationManagerCompat>(relaxed = true)
        val manager = createActiveCallManager(notificationManagerCompat = notificationManagerCompat)

        val notificationData = aCallNotificationData()
        manager.registerIncomingCall(notificationData)
        assertThat(manager.activeCall.value).isNotNull()
        assertThat(manager.activeWakeLock?.isHeld).isTrue()

        manager.hangUpCall(CallData(notificationData.sessionId, notificationData.roomId, false))
        assertThat(manager.activeCall.value).isNull()
        assertThat(manager.activeWakeLock?.isHeld).isFalse()

        verify { notificationManagerCompat.cancel(notificationId) }
    }

    @Test
    fun `Decline event - Hangup on a ringing call should send a decline event`() = runTest {
        setupShadowPowerManager()
        val notificationManagerCompat = mockk<NotificationManagerCompat>(relaxed = true)

        val room = mockk<JoinedRoom>(relaxed = true)

        val matrixClient = FakeMatrixClient().apply {
            givenGetRoomResult(A_ROOM_ID, room)
        }
        val clientProvider = FakeMatrixClientProvider({ Result.success(matrixClient) })

        val manager = createActiveCallManager(
            matrixClientProvider = clientProvider,
            notificationManagerCompat = notificationManagerCompat
        )

        val notificationData = aCallNotificationData(roomId = A_ROOM_ID)
        manager.registerIncomingCall(notificationData)

        manager.hangUpCall(CallData(notificationData.sessionId, notificationData.roomId, false))

        coVerify {
            room.declineCall(notificationEventId = notificationData.eventId)
        }
    }

    @Test
    fun `Decline event - Hangup on a unknown call should send a decline event`() = runTest {
        setupShadowPowerManager()
        val notificationManagerCompat = mockk<NotificationManagerCompat>(relaxed = true)

        val room = mockk<JoinedRoom>(relaxed = true)

        val matrixClient = FakeMatrixClient().apply {
            givenGetRoomResult(A_ROOM_ID, room)
        }
        val clientProvider = FakeMatrixClientProvider({ Result.success(matrixClient) })

        val manager = createActiveCallManager(
            matrixClientProvider = clientProvider,
            notificationManagerCompat = notificationManagerCompat
        )

        val notificationData = aCallNotificationData(roomId = A_ROOM_ID)
        // Do not register the incoming call, so the manager doesn't know about it
        manager.hangUpCall(
            callData = CallData(notificationData.sessionId, notificationData.roomId, false),
            notificationData = notificationData,
        )
        coVerify {
            room.declineCall(notificationEventId = notificationData.eventId)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `Decline event - Declining from another session should stop ringing`() = runTest {
        setupShadowPowerManager()
        val notificationManagerCompat = mockk<NotificationManagerCompat>(relaxed = true)

        val room = FakeJoinedRoom()

        val matrixClient = FakeMatrixClient().apply {
            givenGetRoomResult(A_ROOM_ID, room)
        }
        val clientProvider = FakeMatrixClientProvider({ Result.success(matrixClient) })

        val manager = createActiveCallManager(
            matrixClientProvider = clientProvider,
            notificationManagerCompat = notificationManagerCompat
        )

        val notificationData = aCallNotificationData(roomId = A_ROOM_ID)
        manager.registerIncomingCall(notificationData)

        runCurrent()

        // Simulate declined from other session
        room.baseRoom.givenDecliner(matrixClient.sessionId, notificationData.eventId)

        runCurrent()

        assertThat(manager.activeCall.value).isNull()
        assertThat(manager.activeWakeLock?.isHeld).isFalse()

        verify { notificationManagerCompat.cancel(notificationId) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `Decline event - Should ignore decline for other notification events`() = runTest {
        plantTestTimber()
        setupShadowPowerManager()
        val notificationManagerCompat = mockk<NotificationManagerCompat>(relaxed = true)

        val room = FakeJoinedRoom()

        val matrixClient = FakeMatrixClient().apply {
            givenGetRoomResult(A_ROOM_ID, room)
        }
        val clientProvider = FakeMatrixClientProvider({ Result.success(matrixClient) })

        val manager = createActiveCallManager(
            matrixClientProvider = clientProvider,
            notificationManagerCompat = notificationManagerCompat
        )

        val notificationData = aCallNotificationData(roomId = A_ROOM_ID)
        manager.registerIncomingCall(notificationData)

        runCurrent()

        // Simulate declined for another notification event
        room.baseRoom.givenDecliner(matrixClient.sessionId, AN_EVENT_ID_2)

        runCurrent()

        assertThat(manager.activeCall.value).isNotNull()
        assertThat(manager.activeWakeLock?.isHeld).isTrue()

        verify(exactly = 0) { notificationManagerCompat.cancel(notificationId) }
    }

    @Test
    fun `hangUpCall - does nothing if the CallData doesn't match`() = runTest {
        setupShadowPowerManager()
        val notificationManagerCompat = mockk<NotificationManagerCompat>(relaxed = true)
        val manager = createActiveCallManager(notificationManagerCompat = notificationManagerCompat)

        manager.registerIncomingCall(aCallNotificationData())
        assertThat(manager.activeCall.value).isNotNull()
        assertThat(manager.activeWakeLock?.isHeld).isTrue()

        manager.hangUpCall(
            CallData(
                sessionId = A_SESSION_ID,
                roomId = A_ROOM_ID_2,
                isAudioCall = true,
            )
        )
        assertThat(manager.activeCall.value).isNotNull()
        assertThat(manager.activeWakeLock?.isHeld).isTrue()

        // The notification is always cancelled do not block the user
        verify(exactly = 1) { notificationManagerCompat.cancel(notificationId) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `joinedCall - register an ongoing call and tries sending the call notify event`() = runTest {
        val notificationManagerCompat = mockk<NotificationManagerCompat>(relaxed = true)
        val manager = createActiveCallManager(notificationManagerCompat = notificationManagerCompat)
        assertThat(manager.activeCall.value).isNull()

        manager.joinedCall(CallData(A_SESSION_ID, A_ROOM_ID, true))
        assertThat(manager.activeCall.value).isEqualTo(
            ActiveCall(
                callData = CallData(
                    sessionId = A_SESSION_ID,
                    roomId = A_ROOM_ID,
                    isAudioCall = true,
                ),
                callState = CallState.InCall,
            )
        )

        runCurrent()

        verify { notificationManagerCompat.cancel(notificationId) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `observeRingingCalls - will cancel the active ringing call if the call is cancelled`() = runTest {
        val room = FakeBaseRoom().apply {
            givenRoomInfo(aRoomInfo())
        }
        val client = FakeMatrixClient().apply {
            givenGetRoomResult(A_ROOM_ID, room)
        }
        val matrixClientProvider = FakeMatrixClientProvider(getClient = { Result.success(client) })
        val manager = createActiveCallManager(matrixClientProvider = matrixClientProvider)

        manager.registerIncomingCall(aCallNotificationData())

        // Call is active (the other user join the call)
        room.givenRoomInfo(aRoomInfo(hasRoomCall = true))
        advanceTimeBy(1)
        // Call is cancelled (the other user left the call)
        room.givenRoomInfo(aRoomInfo(hasRoomCall = false))
        advanceTimeBy(1)

        assertThat(manager.activeCall.value).isNull()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `observeRingingCalls - will do nothing if either the session or the room are not found`() = runTest {
        val room = FakeBaseRoom().apply {
            givenRoomInfo(aRoomInfo())
        }
        val client = FakeMatrixClient().apply {
            givenGetRoomResult(A_ROOM_ID, room)
        }
        val matrixClientProvider = FakeMatrixClientProvider(getClient = { Result.failure(IllegalStateException("Matrix client not found")) })
        val manager = createActiveCallManager(matrixClientProvider = matrixClientProvider)

        // No matrix client

        manager.registerIncomingCall(aCallNotificationData())

        room.givenRoomInfo(aRoomInfo(hasRoomCall = true))
        advanceTimeBy(1)
        room.givenRoomInfo(aRoomInfo(hasRoomCall = false))
        advanceTimeBy(1)

        // The call should still be active
        assertThat(manager.activeCall.value).isNotNull()

        // No room
        client.givenGetRoomResult(A_ROOM_ID, null)
        matrixClientProvider.getClient = { Result.success(client) }

        manager.registerIncomingCall(aCallNotificationData())

        room.givenRoomInfo(aRoomInfo(hasRoomCall = true))
        advanceTimeBy(1)
        room.givenRoomInfo(aRoomInfo(hasRoomCall = false))
        advanceTimeBy(1)

        // The call should still be active
        assertThat(manager.activeCall.value).isNotNull()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `IncomingCall - rings no longer than expiration time`() = runTest {
        setupShadowPowerManager()
        val notificationManagerCompat = mockk<NotificationManagerCompat>(relaxed = true)
        val clock = FakeSystemClock()
        val manager = createActiveCallManager(notificationManagerCompat = notificationManagerCompat, systemClock = clock)

        assertThat(manager.activeWakeLock?.isHeld).isFalse()
        assertThat(manager.activeCall.value).isNull()

        val eventTimestamp = A_FAKE_TIMESTAMP
        // The call should not ring more than 30 seconds after the initial event was sent
        val expirationTimestamp = eventTimestamp + 30_000

        val callNotificationData = aCallNotificationData(
            timestamp = eventTimestamp,
            expirationTimestamp = expirationTimestamp,
        )

        // suppose it took 10s to be notified
        clock.epochMillisResult = eventTimestamp + 10_000
        manager.registerIncomingCall(callNotificationData)

        assertThat(manager.activeCall.value).isEqualTo(
            ActiveCall(
                callData = CallData(
                    sessionId = callNotificationData.sessionId,
                    roomId = callNotificationData.roomId,
                    isAudioCall = false,
                ),
                callState = CallState.Ringing(callNotificationData)
            )
        )

        runCurrent()

        assertThat(manager.activeWakeLock?.isHeld).isTrue()
        verify { notificationManagerCompat.notify(notificationId, any()) }

        // advance by 21s it should have stopped ringing
        advanceTimeBy(21_000)
        runCurrent()

        verify { notificationManagerCompat.cancel(any()) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `IncomingCall - ignore expired ring lifetime`() = runTest {
        setupShadowPowerManager()
        val notificationManagerCompat = mockk<NotificationManagerCompat>(relaxed = true)
        val clock = FakeSystemClock()
        val manager = createActiveCallManager(notificationManagerCompat = notificationManagerCompat, systemClock = clock)

        assertThat(manager.activeWakeLock?.isHeld).isFalse()
        assertThat(manager.activeCall.value).isNull()

        val eventTimestamp = A_FAKE_TIMESTAMP
        // The call should not ring more than 30 seconds after the initial event was sent
        val expirationTimestamp = eventTimestamp + 30_000

        val callNotificationData = aCallNotificationData(
            timestamp = eventTimestamp,
            expirationTimestamp = expirationTimestamp,
        )

        // suppose it took 35s to be notified
        clock.epochMillisResult = eventTimestamp + 35_000
        manager.registerIncomingCall(callNotificationData)

        assertThat(manager.activeCall.value).isNull()

        runCurrent()

        assertThat(manager.activeWakeLock?.isHeld).isFalse()
        verify(exactly = 0) { notificationManagerCompat.notify(notificationId, any()) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `registerIncomingCall - same room as active call is ignored without missed notification`() = runTest {
        val addMissedCallNotificationLambda = lambdaRecorder<SessionId, RoomId, EventId, Unit> { _, _, _ -> }
        val onMissedCallNotificationHandler = FakeOnMissedCallNotificationHandler(addMissedCallNotificationLambda = addMissedCallNotificationLambda)
        val manager = createActiveCallManager(
            onMissedCallNotificationHandler = onMissedCallNotificationHandler,
        )

        val callNotificationData = aCallNotificationData()
        manager.registerIncomingCall(callNotificationData)
        val activeCall = manager.activeCall.value

        manager.registerIncomingCall(aCallNotificationData(eventId = AN_EVENT_ID_2))

        assertThat(manager.activeCall.value).isEqualTo(activeCall)
        advanceTimeBy(1)
        addMissedCallNotificationLambda.assertions().isNeverCalled()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `awaitReadyForOutgoingCall - waits rejoin cooldown after hang up`() = runTest {
        val manager = createActiveCallManager()
        val callData = CallData(A_SESSION_ID, A_ROOM_ID, isAudioCall = true)

        manager.joinedCall(callData)
        manager.hangUpCall(callData)

        val gateDeferred = backgroundScope.async {
            manager.awaitReadyForOutgoingCall(callData)
        }
        runCurrent()
        assertThat(gateDeferred.isCompleted).isFalse()

        advanceTimeBy(5_000)
        runCurrent()

        assertThat(gateDeferred.await()).isEqualTo(OutgoingCallGate.Proceed)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `awaitReadyForOutgoingCall - skips room idle wait when forcing start after hang up`() = runTest {
        val room = FakeJoinedRoom(
            baseRoom = FakeBaseRoom().apply {
                givenRoomInfo(
                    aRoomInfo(
                        hasRoomCall = true,
                        activeRoomCallParticipants = listOf(A_SESSION_ID),
                    ),
                )
            },
        )
        val client = FakeMatrixClient(sessionId = A_SESSION_ID).apply {
            givenGetRoomResult(A_ROOM_ID, room)
        }
        val manager = createActiveCallManager(
            matrixClientProvider = FakeMatrixClientProvider(getClient = { Result.success(client) }),
        )
        val callData = CallData(A_SESSION_ID, A_ROOM_ID, isAudioCall = true)

        manager.joinedCall(callData)
        manager.hangUpCall(callData)
        assertThat(manager.shouldForceStartNewCall(A_ROOM_ID)).isTrue()

        val gate = manager.awaitReadyForOutgoingCall(callData)

        assertThat(gate).isEqualTo(OutgoingCallGate.Proceed)
        assertThat(manager.shouldForceStartNewCall(A_ROOM_ID)).isTrue()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `awaitReadyForOutgoingCall - waits until room call is idle when not forcing start`() = runTest {
        val room = FakeJoinedRoom(
            baseRoom = FakeBaseRoom().apply {
                givenRoomInfo(
                    aRoomInfo(
                        hasRoomCall = true,
                        activeRoomCallParticipants = listOf(A_SESSION_ID),
                    ),
                )
            },
        )
        val client = FakeMatrixClient(sessionId = A_SESSION_ID).apply {
            givenGetRoomResult(A_ROOM_ID, room)
        }
        val manager = createActiveCallManager(
            matrixClientProvider = FakeMatrixClientProvider(getClient = { Result.success(client) }),
        )
        val callData = CallData(A_SESSION_ID, A_ROOM_ID, isAudioCall = true)

        // Simulate a stale room call without a local hang-up (no force-START flag).
        room.baseRoom.givenRoomInfo(
            aRoomInfo(hasRoomCall = true, activeRoomCallParticipants = listOf(A_SESSION_ID)),
        )

        val gateDeferred = backgroundScope.async {
            manager.awaitReadyForOutgoingCall(callData)
        }
        runCurrent()
        assertThat(gateDeferred.isCompleted).isFalse()

        room.baseRoom.givenRoomInfo(
            aRoomInfo(hasRoomCall = false, activeRoomCallParticipants = emptyList()),
        )
        advanceTimeBy(5_000)
        runCurrent()

        assertThat(gateDeferred.await()).isEqualTo(OutgoingCallGate.Proceed)
        assertThat(manager.shouldForceStartNewCall(A_ROOM_ID)).isFalse()
    }

    @Test
    fun `awaitReadyForOutgoingCall - busy when another room call is active`() = runTest {
        val manager = createActiveCallManager()
        manager.joinedCall(CallData(A_SESSION_ID, A_ROOM_ID, isAudioCall = true))

        val gate = manager.awaitReadyForOutgoingCall(CallData(A_SESSION_ID, A_ROOM_ID_2, isAudioCall = true))
        assertThat(gate).isEqualTo(OutgoingCallGate.BusyWithOtherCall)
    }

    @Test
    fun `awaitReadyForOutgoingCall - already in this call when same room is active`() = runTest {
        val manager = createActiveCallManager()
        val callData = CallData(A_SESSION_ID, A_ROOM_ID, isAudioCall = true)
        manager.joinedCall(callData)

        val gate = manager.awaitReadyForOutgoingCall(callData)
        assertThat(gate).isEqualTo(OutgoingCallGate.AlreadyInThisCall)
    }

    private fun setupShadowPowerManager() {
        shadowOf(InstrumentationRegistry.getInstrumentation().targetContext.getSystemService<PowerManager>()).apply {
            setIsWakeLockLevelSupported(PowerManager.PARTIAL_WAKE_LOCK, true)
        }
    }

    private fun TestScope.createActiveCallManager(
        matrixClientProvider: FakeMatrixClientProvider = FakeMatrixClientProvider(),
        onMissedCallNotificationHandler: FakeOnMissedCallNotificationHandler = FakeOnMissedCallNotificationHandler(),
        notificationManagerCompat: NotificationManagerCompat = mockk(relaxed = true),
        systemClock: FakeSystemClock = FakeSystemClock(),
    ) = DefaultActiveCallManager(
        context = InstrumentationRegistry.getInstrumentation().targetContext,
        coroutineScope = backgroundScope,
        onMissedCallNotificationHandler = onMissedCallNotificationHandler,
        ringingCallNotificationCreator = RingingCallNotificationCreator(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            matrixClientProvider = matrixClientProvider,
            imageLoaderHolder = FakeImageLoaderHolder(),
            notificationBitmapLoader = FakeNotificationBitmapLoader(),
        ),
        notificationManagerCompat = notificationManagerCompat,
        matrixClientProvider = matrixClientProvider,
        defaultCurrentCallService = DefaultCurrentCallService(),
        appForegroundStateService = FakeAppForegroundStateService(),
        imageLoaderHolder = FakeImageLoaderHolder(),
        systemClock = systemClock,
    )
}
