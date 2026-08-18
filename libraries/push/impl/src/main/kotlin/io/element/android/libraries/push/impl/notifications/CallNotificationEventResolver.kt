/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.impl.notifications

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.exception.NotificationResolverException
import io.element.android.libraries.matrix.api.notification.CallIntent
import io.element.android.libraries.matrix.api.notification.NotificationContent
import io.element.android.libraries.matrix.api.notification.NotificationData
import io.element.android.libraries.matrix.api.notification.RtcNotificationType
import io.element.android.libraries.matrix.api.timeline.item.event.EventType

/**
 * Helper to resolve a valid [NotifiableEvent] from a [NotificationData].
 */
interface CallNotificationEventResolver {
    /**
     * Resolve a call notification event from a notification data depending on whether it should be a ringing one or not.
     * @param sessionId the current session id
     * @param notificationData the notification data
     * @param forceNotify `true` to force the notification to be non-ringing, `false` to use the default behaviour. Default is `false`.
     * @return a [NotifiableEvent] if the notification data is a call notification, null otherwise
     */
    suspend fun resolveEvent(
        sessionId: SessionId,
        notificationData: NotificationData,
        forceNotify: Boolean = false,
    ): Result<NotifiableEvent>
}

@ContributesBinding(AppScope::class)
class DefaultCallNotificationEventResolver(
    private val stringProvider: StringProvider,
    private val appForegroundStateService: AppForegroundStateService,
    private val clientProvider: MatrixClientProvider,
) : CallNotificationEventResolver {
    override suspend fun resolveEvent(
        sessionId: SessionId,
        notificationData: NotificationData,
        forceNotify: Boolean
    ): Result<NotifiableEvent> = runCatchingExceptions {
        val content = notificationData.content as? NotificationContent.MessageLike.RtcNotification
            ?: throw NotificationResolverException.UnknownError("content is not a call notify")

        // RING should ring immediately. Waiting for hasRoomCall delayed incoming calls by seconds
        // even when chat messages already arrive in 1–4s.
        val shouldRing = content.type == RtcNotificationType.RING && !forceNotify

        if (shouldRing) {
            appForegroundStateService.updateHasRingingCall(true)
        }

        notificationData.run {
            if (shouldRing) {
                Timber.d("Ringing call notification intent ${content.callIntent} in room $roomId")
                NotifiableRingingCallEvent(
                    sessionId = sessionId,
                    roomId = roomId,
                    eventId = eventId,
                    roomName = roomDisplayName,
                    editedEventId = null,
                    canBeReplaced = true,
                    timestamp = this.timestamp,
                    isRedacted = false,
                    isUpdated = false,
                    description = if (content.callIntent ==
                        CallIntent.AUDIO) {
                            stringProvider.getString(R.string.notification_incoming_audio_call)
                        } else {
                            stringProvider.getString(
                        R.string.notification_incoming_call
                    )
                        },
                    senderDisambiguatedDisplayName = getDisambiguatedDisplayName(content.senderId),
                    roomAvatarUrl = roomAvatarUrl,
                    rtcNotificationType = content.type,
                    callIntent = content.callIntent,
                    senderId = content.senderId,
                    senderAvatarUrl = senderAvatarUrl,
                    expirationTimestamp = content.expirationTimestampMillis,
                )
            } else {
                Timber.d("Event $eventId is call notify but should not ring, notify: ${content.type}")
                // Create a simple message notification event
                buildNotifiableMessageEvent(
                    sessionId = sessionId,
                    senderId = content.senderId,
                    roomId = roomId,
                    eventId = eventId,
                    noisy = true,
                    timestamp = this.timestamp,
                    senderDisambiguatedDisplayName = getDisambiguatedDisplayName(content.senderId),
                    body = if (content.callIntent == CallIntent.VIDEO) {
                        stringProvider.getString(R.string.notification_incoming_call)
                    } else {
                        stringProvider.getString(R.string.notification_incoming_audio_call)
                    },
                    roomName = roomDisplayName,
                    roomIsDm = isDm,
                    roomAvatarPath = roomAvatarUrl,
                    senderAvatarPath = senderAvatarUrl,
                    type = EventType.RTC_NOTIFICATION,
                )
            }
        }
    }
}
