/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import im.vector.app.features.analytics.plan.MobileScreen
import io.element.android.appconfig.ElementCallConfig
import io.element.android.features.call.api.CallData
import io.element.android.features.call.impl.data.WidgetMessage
import io.element.android.features.call.impl.utils.ActiveCallManager
import io.element.android.features.call.impl.utils.CallWidgetProvider
import io.element.android.features.call.impl.utils.WebViewWidgetMessageInterceptor
import io.element.android.features.call.impl.utils.WidgetMessageInterceptor
import io.element.android.features.call.impl.utils.WidgetMessageSerializer
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.runCatchingUpdatingState
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.sync.SyncState
import io.element.android.libraries.matrix.api.widget.MatrixWidgetDriver
import io.element.android.libraries.network.useragent.UserAgentProvider
import io.element.android.services.analytics.api.ScreenTracker
import io.element.android.services.appnavstate.api.AppForegroundStateService
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

@AssistedInject
class CallScreenPresenter(
    @Assisted private val callData: CallData,
    @Assisted private val navigator: CallScreenNavigator,
    private val callWidgetProvider: CallWidgetProvider,
    userAgentProvider: UserAgentProvider,
    private val clock: SystemClock,
    private val dispatchers: CoroutineDispatchers,
    private val matrixClientsProvider: MatrixClientProvider,
    private val screenTracker: ScreenTracker,
    private val activeCallManager: ActiveCallManager,
    private val languageTagProvider: LanguageTagProvider,
    private val appForegroundStateService: AppForegroundStateService,
    @AppCoroutineScope
    private val appCoroutineScope: CoroutineScope,
    private val widgetMessageSerializer: WidgetMessageSerializer,
) : Presenter<CallScreenState> {
    @AssistedFactory
    interface Factory {
        fun create(callData: CallData, navigator: CallScreenNavigator): CallScreenPresenter
    }

    private val userAgent = userAgentProvider.provide()

    @Composable
    override fun present(): CallScreenState {
        val coroutineScope = rememberCoroutineScope()
        val urlState = remember { mutableStateOf<AsyncData<String>>(AsyncData.Uninitialized) }
        val callWidgetDriver = remember { mutableStateOf<MatrixWidgetDriver?>(null) }
        val messageInterceptor = remember { mutableStateOf<WidgetMessageInterceptor?>(null) }
        var isWidgetLoaded by rememberSaveable { mutableStateOf(false) }
        var ignoreWebViewError by rememberSaveable { mutableStateOf(false) }
        var callError by remember { mutableStateOf<CallScreenError?>(null) }
        var loadAttempt by remember { mutableIntStateOf(0) }
        val languageTag = languageTagProvider.provideLanguageTag()
        val theme = "dark"

        DisposableEffect(Unit) {
            coroutineScope.launch {
                // Sets the call as joined
                activeCallManager.joinedCall(callData)
                fetchRoomCallUrl(
                    callData = callData,
                    urlState = urlState,
                    callWidgetDriver = callWidgetDriver,
                    languageTag = languageTag,
                    theme = theme,
                    onSetupFailure = { details ->
                        callError = CallScreenError.Setup(details)
                    },
                )
            }
            onDispose {
                appCoroutineScope.launch { activeCallManager.hangUpCall(callData) }
            }
        }
        screenTracker.TrackScreen(screen = MobileScreen.ScreenName.RoomCall)
        HandleMatrixClientSyncState()

        callWidgetDriver.value?.let { driver ->
            LaunchedEffect(driver) {
                driver.incomingMessages
                    .onEach {
                        // Relay message to the WebView
                        messageInterceptor.value?.sendMessage(it)
                    }
                    .launchIn(this)

                driver.run()
            }
        }

        messageInterceptor.value?.let { interceptor ->
            LaunchedEffect(interceptor, loadAttempt) {
                interceptor.interceptedMessages
                    .onEach {
                        // We are receiving messages from the WebView, consider that the application is loaded
                        ignoreWebViewError = true
                        callError = null
                        // Relay message to Widget Driver
                        callWidgetDriver.value?.send(it)

                        val parsedMessage = parseMessage(it)
                        if (parsedMessage?.direction == WidgetMessage.Direction.FromWidget) {
                            if (parsedMessage.action == WidgetMessage.Action.Close) {
                                close(callWidgetDriver.value, navigator)
                            } else if (parsedMessage.action == WidgetMessage.Action.ContentLoaded) {
                                isWidgetLoaded = true
                            }
                        }
                    }
                    .launchIn(this)
            }

            LaunchedEffect(interceptor, loadAttempt) {
                // Wait for the call UI to become ready; slow JWT/ICE/network often exceeds a few seconds.
                delay(ElementCallConfig.CALL_WIDGET_LOAD_TIMEOUT_SECONDS.seconds)

                if (!isWidgetLoaded) {
                    Timber.w(
                        "The call took more than %ds to load. Showing recoverable timeout error.",
                        ElementCallConfig.CALL_WIDGET_LOAD_TIMEOUT_SECONDS,
                    )
                    callError = CallScreenError.LoadTimeout
                }
            }
        }

        // Surface URL setup failures that already ended in AsyncData.Failure
        LaunchedEffect(urlState.value) {
            val failure = urlState.value as? AsyncData.Failure ?: return@LaunchedEffect
            if (callError == null) {
                callError = CallScreenError.Setup(failure.error.message)
            }
        }

        fun handleEvent(event: CallScreenEvent) {
            when (event) {
                is CallScreenEvent.Hangup -> {
                    coroutineScope.launch {
                        val widgetId = callWidgetDriver.value?.id
                        val interceptor = messageInterceptor.value
                        val driver = callWidgetDriver.value
                        if (widgetId != null && interceptor != null) {
                            // Ask Element Call to leave so the remote side stops ringing.
                            sendHangupMessage(widgetId, interceptor)
                            isWidgetLoaded = false
                            // Skip grace when already on an error dialog (WebView is gone).
                            if (callError == null) {
                                Timber.d(
                                    "Waiting %ds for Element Call to leave before closing call UI",
                                    ElementCallConfig.CALL_HANGUP_GRACE_SECONDS,
                                )
                                delay(ElementCallConfig.CALL_HANGUP_GRACE_SECONDS.seconds)
                            }
                        }
                        close(driver, navigator)
                    }
                }
                is CallScreenEvent.Retry -> {
                    Timber.d("Retrying call setup for roomId: ${callData.roomId}")
                    callError = null
                    ignoreWebViewError = false
                    isWidgetLoaded = false
                    // Drop the previous interceptor/driver so load-timeout effects and
                    // driver.run() cannot race against the new attempt.
                    val previousDriver = callWidgetDriver.value
                    messageInterceptor.value = null
                    callWidgetDriver.value = null
                    urlState.value = AsyncData.Uninitialized
                    loadAttempt += 1
                    coroutineScope.launch {
                        previousDriver?.close()
                        fetchRoomCallUrl(
                            callData = callData,
                            urlState = urlState,
                            callWidgetDriver = callWidgetDriver,
                            languageTag = languageTag,
                            theme = theme,
                            onSetupFailure = { details ->
                                callError = CallScreenError.Setup(details)
                            },
                        )
                    }
                }
                is CallScreenEvent.SetupMessageChannels -> {
                    messageInterceptor.value = event.widgetMessageInterceptor
                }
                is CallScreenEvent.OnWebViewError -> {
                    val isRenderProcessCrash =
                        event.description == WebViewWidgetMessageInterceptor.RENDER_PROCESS_CRASH_DETAILS
                    if (isRenderProcessCrash || !ignoreWebViewError) {
                        callError = CallScreenError.WebView(event.description)
                    }
                    // Else ignore the error, give a chance the Element Call to recover by itself.
                }
            }
        }

        return CallScreenState(
            urlState = urlState.value,
            callError = callError,
            userAgent = userAgent,
            isCallActive = isWidgetLoaded,
            webViewInstanceKey = loadAttempt,
            eventSink = ::handleEvent,
        )
    }

    private suspend fun fetchRoomCallUrl(
        callData: CallData,
        urlState: MutableState<AsyncData<String>>,
        callWidgetDriver: MutableState<MatrixWidgetDriver?>,
        languageTag: String?,
        theme: String?,
        onSetupFailure: (String?) -> Unit,
    ) {
        urlState.runCatchingUpdatingState {
            val result = callWidgetProvider.getWidget(
                sessionId = callData.sessionId,
                roomId = callData.roomId,
                clientId = UUID.randomUUID().toString(),
                isAudioCall = callData.isAudioCall,
                languageTag = languageTag,
                theme = theme,
            ).getOrThrow()
            callWidgetDriver.value = result.driver
            Timber.d("Call widget driver initialized for sessionId: ${callData.sessionId}, roomId: ${callData.roomId}")
            result.url
        }.onFailure { error ->
            Timber.e(error, "Failed to prepare call widget URL")
            onSetupFailure(error.message)
        }
    }

    @Composable
    private fun HandleMatrixClientSyncState() {
        val coroutineScope = rememberCoroutineScope()
        DisposableEffect(Unit) {
            val client = matrixClientsProvider.getOrNull(callData.sessionId) ?: return@DisposableEffect onDispose {
                Timber.w("No MatrixClient found for sessionId, can't send call notification: ${callData.sessionId}")
            }
            coroutineScope.launch {
                Timber.d("Observing sync state in-call for sessionId: ${callData.sessionId}")
                client.syncService.syncState
                    .collect { state ->
                        if (state != SyncState.Running) {
                            appForegroundStateService.updateIsInCallState(true)
                        }
                    }
            }
            onDispose {
                Timber.d("Stopped observing sync state in-call for sessionId: ${callData.sessionId}")
                // Make sure we mark the call as ended in the app state
                appForegroundStateService.updateIsInCallState(false)
            }
        }
    }

    private fun parseMessage(message: String): WidgetMessage? {
        return widgetMessageSerializer.deserialize(message).getOrNull()
    }

    private fun sendHangupMessage(widgetId: String, messageInterceptor: WidgetMessageInterceptor) {
        val message = WidgetMessage(
            direction = WidgetMessage.Direction.ToWidget,
            widgetId = widgetId,
            requestId = "widgetapi-${clock.epochMillis()}",
            action = WidgetMessage.Action.HangUp,
            data = null,
        )
        messageInterceptor.sendMessage(widgetMessageSerializer.serialize(message))
    }

    private fun CoroutineScope.close(widgetDriver: MatrixWidgetDriver?, navigator: CallScreenNavigator) = launch(dispatchers.io) {
        navigator.close()
        widgetDriver?.close()
    }
}
