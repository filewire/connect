/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.connectprivacy

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.inputs
import io.element.android.libraries.di.SessionScope

@ContributesNode(SessionScope::class)
@AssistedInject
class ConnectPrivacyNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
) : Node(buildContext = buildContext, plugins = plugins) {
    data class Inputs(
        val showBlockedUsers: Boolean,
        val nbOfBlockedUsers: Int,
    ) : NodeInputs

    interface Callback : Plugin {
        fun navigateToNotificationSettings()
        fun navigateToLockScreenSettings()
        fun navigateToBlockedUsers()
    }

    private val inputs = inputs<Inputs>()
    private val callback: Callback = callback()

    @Composable
    override fun View(modifier: Modifier) {
        ConnectPrivacyView(
            showBlockedUsers = inputs.showBlockedUsers,
            nbOfBlockedUsers = inputs.nbOfBlockedUsers,
            onBackClick = ::navigateUp,
            onOpenNotificationSettings = callback::navigateToNotificationSettings,
            onOpenLockScreenSettings = callback::navigateToLockScreenSettings,
            onOpenBlockedUsers = callback::navigateToBlockedUsers,
            modifier = modifier,
        )
    }
}
