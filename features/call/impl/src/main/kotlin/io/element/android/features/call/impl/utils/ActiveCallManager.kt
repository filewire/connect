/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.appconfig.ElementCallConfig
import io.element.android.features.call.api.CallData
import io.element.android.features.call.api.CurrentCall
import io.element.android.features.call.impl.notifications.CallNotificationData
import io.element.android.features.call.impl.notifications.RingingCallNotificationCreator
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.ui.media.ImageLoaderHolder
import io.element.android.libraries.push.api.notifications.ForegroundServiceType
import io.element.android.libraries.push.api.notifications.NotificationIdProvider
import io.element.android.libraries.push.api.notifications.OnMissedCallNotificationHandler
import io.element.android.services.appnavstate.api.AppForegroundStateService
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

/**
 * Manages the active call state.
 */
interface ActiveCallManager {
    /**
     * The active call state flow, which will be updated when the active call changes.
     */
    val activeCall: StateFlow<ActiveCall?>

    /**
     * Registers an incoming call if there isn't an existing active call and posts a [CallState.Ringing] notification.
     * @param notificationData The data for the incoming call notification.
     */
    suspend fun registerIncomingCall(notificationData: CallNotificationData)

    /**
     * Called to hang up the active call. It will hang up the call and remove any existing UI and the active call.
     * @param callData The data about the call.
     * @param notificationData The data for the incoming call notification.
     */
    suspend fun hangUpCall(
        callData: CallData,
        notificationData: CallNotificationData? = null,
    )

    /**
     * Called after the user joined a call. It will remove any existing UI and set the call state as [CallState.InCall].
     *
     * @param callData The data about the call.
     */
    suspend fun joinedCall(callData: CallData)

    /**
     * Decide whether an outgoing call UI may be started, waiting out a short rejoin cooldown when needed.
     *
     * Rapid hang-up → recall races Element Call WebView teardown; the cooldown reduces that race.
     */
    suspend fun awaitReadyForOutgoingCall(callData: CallData): OutgoingCallGate

    /**
     * After a local hang-up, MatrixRTC may still report an active room call.
     * Returning true forces Element Call to use a START intent instead of JOIN_EXISTING,
     * which otherwise hangs on a zombie session (blank "Please wait" then load timeout).
     */
    fun shouldForceStartNewCall(roomId: RoomId): Boolean

    /**
     * Clear the force-START flag once the new call widget URL has been built.
     */
    fun clearForceStartNewCall(roomId: RoomId)
}

/**
 * Result of [ActiveCallManager.awaitReadyForOutgoingCall].
 */
sealed interface OutgoingCallGate {
    /** No active call — start a new call Activity. */
    data object Proceed : OutgoingCallGate

    /** Local UI is already in this room call — bring the existing Activity to the front. */
    data object AlreadyInThisCall : OutgoingCallGate

    /** Local UI is in a different call — do not start another. */
    data object BusyWithOtherCall : OutgoingCallGate
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultActiveCallManager(
    @ApplicationContext context: Context,
    @AppCoroutineScope
    private val coroutineScope: CoroutineScope,
    private val onMissedCallNotificationHandler: OnMissedCallNotificationHandler,
    private val ringingCallNotificationCreator: RingingCallNotificationCreator,
    private val notificationManagerCompat: NotificationManagerCompat,
    private val matrixClientProvider: MatrixClientProvider,
    private val defaultCurrentCallService: DefaultCurrentCallService,
    private val appForegroundStateService: AppForegroundStateService,
    private val imageLoaderHolder: ImageLoaderHolder,
    private val systemClock: SystemClock,
) : ActiveCallManager {
    private val tag = "ActiveCallManager"
    private var timedOutCallJob: Job? = null

    /** Room + timestamp of the last local hang-up, used for rejoin cooldown. */
    private var lastHangUpRoomId: RoomId? = null
    private var lastHangUpEpochMillis: Long = 0L

    /**
     * After hang-up, force Element Call START intent until we successfully join again
     * (avoids JOIN_EXISTING against a sticky / zombie MatrixRTC session).
     */
    private var forceStartNewCallRoomId: RoomId? = null

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal val activeWakeLock: PowerManager.WakeLock? = context.getSystemService<PowerManager>()
        ?.takeIf { it.isWakeLockLevelSupported(PowerManager.PARTIAL_WAKE_LOCK) }
        ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "${context.packageName}:IncomingCallWakeLock")

    override val activeCall = MutableStateFlow<ActiveCall?>(null)

    private val mutex = Mutex()

    init {
        observeRingingCall()
        observeCurrentCall()
    }

    override suspend fun registerIncomingCall(notificationData: CallNotificationData) {
        mutex.withLock {
            val ringDuration =
                min(
                    notificationData.expirationTimestamp - systemClock.epochMillis(),
                    ElementCallConfig.RINGING_CALL_DURATION_SECONDS * 1000L
                )

            if (ringDuration < 0) {
                // Should already have stopped ringing, ignore.
                Timber.tag(tag).d("Received timed-out incoming ringing call for room id: ${notificationData.roomId}, cancel ringing")
                return
            }

            appForegroundStateService.updateHasRingingCall(true)
            Timber.tag(tag).d("Received incoming call for room id: ${notificationData.roomId}, ringDuration(ms): $ringDuration")
            val currentActiveCall = activeCall.value
            if (currentActiveCall != null) {
                if (currentActiveCall.callData.roomId == notificationData.roomId) {
                    // Already ringing or joined this room — do not treat as a missed call.
                    Timber.tag(tag).d(
                        "Incoming call for room already active locally (%s), ignoring",
                        currentActiveCall.callState,
                    )
                    return
                }
                displayMissedCallNotification(notificationData)
                Timber.tag(tag).w("Already have an active call, ignoring incoming call: $notificationData")
                return
            }
            activeCall.value = ActiveCall(
                callData = CallData(
                    sessionId = notificationData.sessionId,
                    roomId = notificationData.roomId,
                    isAudioCall = notificationData.audioOnly,
                ),
                callState = CallState.Ringing(notificationData),
            )

            timedOutCallJob = coroutineScope.launch {
                setUpCoil(notificationData.sessionId)
                showIncomingCallNotification(notificationData)

                // Wait for the ringing call to time out
                delay(timeMillis = ringDuration)
                incomingCallTimedOut(displayMissedCallNotification = true)
            }

            // Acquire a wake lock to keep the device awake during the incoming call, so we can process the room info data
            if (activeWakeLock?.isHeld == false) {
                Timber.tag(tag).d("Acquiring partial wakelock")
                activeWakeLock.acquire(ringDuration)
            }
        }
    }

    @OptIn(DelicateCoilApi::class)
    private suspend fun setUpCoil(sessionId: SessionId) {
        val matrixClient = matrixClientProvider.getOrRestore(sessionId).getOrNull() ?: return
        // Ensure that the image loader is set, else the IncomingCallActivity will not be able to render the caller avatar
        SingletonImageLoader.setUnsafe(imageLoaderHolder.get(matrixClient))
    }

    /**
     * Called when the incoming call timed out. It will remove the active call and remove any associated UI, adding a 'missed call' notification.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun incomingCallTimedOut(displayMissedCallNotification: Boolean) = mutex.withLock {
        Timber.tag(tag).d("Incoming call timed out")

        val previousActiveCall = activeCall.value ?: return@withLock
        val notificationData = (previousActiveCall.callState as? CallState.Ringing)?.notificationData ?: return@withLock
        activeCall.value = null
        if (activeWakeLock?.isHeld == true) {
            Timber.tag(tag).d("Releasing partial wakelock after timeout")
            activeWakeLock.release()
        }

        cancelIncomingCallNotification()

        if (displayMissedCallNotification) {
            displayMissedCallNotification(notificationData)
        }
    }

    override suspend fun hangUpCall(
        callData: CallData,
        notificationData: CallNotificationData?,
    ) = mutex.withLock {
        Timber.tag(tag).d("Hang up call: $callData")
        cancelIncomingCallNotification()
        val currentActiveCall = activeCall.value ?: run {
            // activeCall.value can be null if the application has been killed while the call was ringing
            // Build a currentActiveCall with the provided parameters.
            notificationData?.let {
                ActiveCall(
                    callData = callData,
                    callState = CallState.Ringing(
                        notificationData = notificationData,
                    )
                )
            }
        } ?: run {
            Timber.tag(tag).w("No active call, ignoring hang up")
            return@withLock
        }

        if (currentActiveCall.callData != callData) {
            Timber.tag(tag).w("Call type $callData does not match the active call type, ignoring")
            return@withLock
        }
        if (currentActiveCall.callState is CallState.Ringing) {
            // Decline the call
            val notificationData = currentActiveCall.callState.notificationData
            matrixClientProvider.getOrRestore(notificationData.sessionId).getOrNull()
                ?.getRoom(notificationData.roomId)
                ?.declineCall(notificationData.eventId)
                ?.onFailure {
                    Timber.e(it, "Failed to decline incoming call")
                }
                ?: run {
                    Timber.tag(tag).d("Couldn't find session or room to decline call for incoming call")
                }
        }
        if (activeWakeLock?.isHeld == true) {
            Timber.tag(tag).d("Releasing partial wakelock after hang up")
            activeWakeLock.release()
        }
        timedOutCallJob?.cancel()
        recordHangUp(callData)
        activeCall.value = null
    }

    override suspend fun joinedCall(callData: CallData) = mutex.withLock {
        Timber.tag(tag).d("Joined call: $callData")
        cancelIncomingCallNotification()
        if (activeWakeLock?.isHeld == true) {
            Timber.tag(tag).d("Releasing partial wakelock after joining call")
            activeWakeLock.release()
        }
        timedOutCallJob?.cancel()

        activeCall.value = ActiveCall(
            callData = callData,
            callState = CallState.InCall,
        )
    }

    override suspend fun awaitReadyForOutgoingCall(callData: CallData): OutgoingCallGate {
        evaluateOutgoingGate(callData)?.let { return it }

        val remainingMs = rejoinCooldownRemainingMs(callData)
        if (remainingMs > 0) {
            Timber.tag(tag).d(
                "Waiting %dms before rejoining call in room %s (WebView teardown cooldown)",
                remainingMs,
                callData.roomId,
            )
            delay(remainingMs)
        }

        waitForRoomCallIdle(callData)

        return evaluateOutgoingGate(callData) ?: OutgoingCallGate.Proceed
    }

    override fun shouldForceStartNewCall(roomId: RoomId): Boolean {
        return forceStartNewCallRoomId == roomId
    }

    override fun clearForceStartNewCall(roomId: RoomId) {
        if (forceStartNewCallRoomId == roomId) {
            forceStartNewCallRoomId = null
        }
    }

    /**
     * After a local hang-up, MatrixRTC membership / room call flag can linger (MSC4140 delayed leave,
     * remote still ringing). Wait until our session is no longer listed as an active participant
     * before opening another outgoing call in the same room.
     */
    private suspend fun waitForRoomCallIdle(callData: CallData) {
        if (lastHangUpRoomId != callData.roomId) return

        val client = matrixClientProvider.getOrRestore(callData.sessionId).getOrNull() ?: return
        val room = client.getRoom(callData.roomId) ?: return

        val forcingStart = forceStartNewCallRoomId == callData.roomId
        if (forcingStart) {
            Timber.tag(tag).d(
                "Waiting for local session to leave MatrixRTC in %s before recall (force START_CALL)",
                callData.roomId,
            )
        }

        val becameIdle = withTimeoutOrNull(ElementCallConfig.CALL_LEAVE_SETTLE_MAX_SECONDS.seconds) {
            while (true) {
                val roomInfo = room.roomInfoFlow.first()
                val sessionStillInCall = callData.sessionId in roomInfo.activeRoomCallParticipants
                if (!sessionStillInCall) {
                    if (!roomInfo.hasRoomCall || forcingStart) {
                        break
                    }
                }
                delay(500)
            }
            Timber.tag(tag).d(
                "Room call idle for session in %s (forceStart=%s), safe to start a new call",
                callData.roomId,
                forcingStart,
            )
            true
        } == true

        if (!becameIdle) {
            Timber.tag(tag).w(
                "Timed out after %ds waiting for session to leave call in %s; proceeding with START_CALL",
                ElementCallConfig.CALL_LEAVE_SETTLE_MAX_SECONDS,
                callData.roomId,
            )
        }
    }

    private suspend fun evaluateOutgoingGate(callData: CallData): OutgoingCallGate? = mutex.withLock {
        val current = activeCall.value ?: return@withLock null
        return@withLock when {
            current.callData.roomId == callData.roomId && current.callState is CallState.InCall ->
                OutgoingCallGate.AlreadyInThisCall
            current.callData.roomId == callData.roomId && current.callState is CallState.Ringing ->
                // Local ringing UI already owns this room; bringing the call Activity up is fine.
                OutgoingCallGate.AlreadyInThisCall
            else -> OutgoingCallGate.BusyWithOtherCall
        }
    }

    private fun recordHangUp(callData: CallData) {
        lastHangUpRoomId = callData.roomId
        lastHangUpEpochMillis = systemClock.epochMillis()
        forceStartNewCallRoomId = callData.roomId
        Timber.tag(tag).d(
            "Recorded local hang-up for roomId=%s; next outgoing call will use START_CALL until content_loaded",
            callData.roomId,
        )
    }

    private fun rejoinCooldownRemainingMs(callData: CallData): Long {
        if (lastHangUpRoomId != callData.roomId) return 0L
        val elapsed = systemClock.epochMillis() - lastHangUpEpochMillis
        val cooldownMs = ElementCallConfig.CALL_REJOIN_COOLDOWN_SECONDS.seconds.inWholeMilliseconds
        return (cooldownMs - elapsed).coerceAtLeast(0L)
    }

    @SuppressLint("MissingPermission")
    private suspend fun showIncomingCallNotification(notificationData: CallNotificationData) {
        Timber.tag(tag).d("Displaying ringing call notification")
        val notification = ringingCallNotificationCreator.createNotification(
            sessionId = notificationData.sessionId,
            roomId = notificationData.roomId,
            eventId = notificationData.eventId,
            senderId = notificationData.senderId,
            roomName = notificationData.roomName,
            senderDisplayName = notificationData.senderName ?: notificationData.senderId.value,
            roomAvatarUrl = notificationData.avatarUrl,
            notificationChannelId = notificationData.notificationChannelId,
            timestamp = notificationData.timestamp,
            textContent = notificationData.textContent,
            expirationTimestamp = notificationData.expirationTimestamp,
            audioOnly = notificationData.audioOnly,
        ) ?: return
        runCatchingExceptions {
            notificationManagerCompat.notify(
                NotificationIdProvider.getForegroundServiceNotificationId(ForegroundServiceType.INCOMING_CALL),
                notification,
            )
        }.onFailure {
            Timber.e(it, "Failed to publish notification for incoming call")
        }
    }

    private fun cancelIncomingCallNotification() {
        appForegroundStateService.updateHasRingingCall(false)
        Timber.tag(tag).d("Ringing call notification cancelled")
        notificationManagerCompat.cancel(NotificationIdProvider.getForegroundServiceNotificationId(ForegroundServiceType.INCOMING_CALL))
    }

    private fun displayMissedCallNotification(notificationData: CallNotificationData) {
        Timber.tag(tag).d("Displaying missed call notification")
        coroutineScope.launch {
            onMissedCallNotificationHandler.addMissedCallNotification(
                sessionId = notificationData.sessionId,
                roomId = notificationData.roomId,
                eventId = notificationData.eventId,
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeRingingCall() {
        activeCall
            .filterNotNull()
            .filter { it.callState is CallState.Ringing }
            .flatMapLatest { activeCall ->
                val callData = activeCall.callData
                val ringingInfo = activeCall.callState as CallState.Ringing
                val client = matrixClientProvider.getOrRestore(callData.sessionId).getOrNull() ?: run {
                    Timber.tag(tag).d("Couldn't find session for incoming call: $activeCall")
                    return@flatMapLatest flowOf()
                }
                val room = client.getRoom(callData.roomId) ?: run {
                    Timber.tag(tag).d("Couldn't find room for incoming call: $activeCall")
                    return@flatMapLatest flowOf()
                }

                Timber.tag(tag).d("Found room for ringing call: ${room.roomId}")

                // If we have declined from another phone we want to stop ringing.
                room.subscribeToCallDecline(ringingInfo.notificationData.eventId)
                    .filter { decliner ->
                        Timber.tag(tag).d("Call: $activeCall was declined by $decliner")
                        // only want to listen if the call was declined from another of my sessions,
                        // (we are ringing for an incoming call in a DM)
                        decliner == client.sessionId
                    }
            }
            .onEach { decliner ->
                Timber.tag(tag).d("Call: $activeCall was declined by user from another session")
                // Remove the active call and cancel the notification
                activeCall.value = null
                if (activeWakeLock?.isHeld == true) {
                    Timber.tag(tag).d("Releasing partial wakelock after call declined from another session")
                    activeWakeLock.release()
                }
                cancelIncomingCallNotification()
            }
            .launchIn(coroutineScope)
        // This will observe ringing calls and ensure they're terminated if the room call is cancelled or if the user
        // has joined the call from another session.
        activeCall
            .filterNotNull()
            .filter { it.callState is CallState.Ringing }
            .flatMapLatest { activeCall ->
                val callData = activeCall.callData
                // Get a flow of updated `hasRoomCall` and `activeRoomCallParticipants` values for the room
                val room = matrixClientProvider.getOrRestore(callData.sessionId).getOrNull()?.getRoom(callData.roomId) ?: run {
                    Timber.tag(tag).d("Couldn't find room for incoming call: $activeCall")
                    return@flatMapLatest flowOf()
                }
                room.roomInfoFlow.map {
                    Timber.tag(tag).d("Has room call status changed for ringing call: ${it.hasRoomCall}")
                    it.hasRoomCall to (callData.sessionId in it.activeRoomCallParticipants)
                }
            }
            // We only want to check if the room active call status changes
            .distinctUntilChanged()
            // Skip the first one, we're not interested in it (if the check below passes, it had to be active anyway)
            .drop(1)
            .onEach { (roomHasActiveCall, userIsInTheCall) ->
                if (!roomHasActiveCall) {
                    // The call was cancelled
                    timedOutCallJob?.cancel()
                    incomingCallTimedOut(displayMissedCallNotification = true)
                } else if (userIsInTheCall) {
                    // The user joined the call from another session
                    timedOutCallJob?.cancel()
                    incomingCallTimedOut(displayMissedCallNotification = false)
                }
            }
            .launchIn(coroutineScope)
    }

    private fun observeCurrentCall() {
        activeCall
            .onEach { value ->
                if (value == null) {
                    defaultCurrentCallService.onCallEnded()
                } else {
                    when (value.callState) {
                        is CallState.Ringing -> {
                            // Nothing to do
                        }
                        is CallState.InCall -> {
                            defaultCurrentCallService.onCallStarted(CurrentCall.RoomCall(value.callData.roomId))
                        }
                    }
                }
            }
            .launchIn(coroutineScope)
    }
}

/**
 * Represents an active call.
 */
data class ActiveCall(
    val callData: CallData,
    val callState: CallState,
)

/**
 * Represents the state of an active call.
 */
sealed interface CallState {
    /**
     * The call is in a ringing state.
     * @param notificationData The data for the incoming call notification.
     */
    data class Ringing(val notificationData: CallNotificationData) : CallState

    /**
     * The call is in an in-call state.
     */
    data object InCall : CallState
}
