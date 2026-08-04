/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.connectprivacy

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun ConnectPrivacyView(
    showBlockedUsers: Boolean,
    nbOfBlockedUsers: Int,
    onBackClick: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenLockScreenSettings: () -> Unit,
    onOpenBlockedUsers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = stringResource(id = R.string.screen_connect_privacy_title),
    ) {
        Text(
            text = stringResource(id = R.string.screen_connect_privacy_subtitle),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        ListItem(
            headlineContent = { Text(stringResource(id = R.string.screen_notification_settings_title)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Notifications())),
            onClick = onOpenNotificationSettings,
        )
        ListItem(
            headlineContent = { Text(stringResource(id = CommonStrings.common_screen_lock)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Lock())),
            onClick = onOpenLockScreenSettings,
        )
        if (showBlockedUsers) {
            ListItem(
                headlineContent = { Text(stringResource(id = CommonStrings.common_blocked_users)) },
                leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Block())),
                onClick = onOpenBlockedUsers,
                trailingContent = ListItemContent.Text(nbOfBlockedUsers.toString()),
            )
        }
        HorizontalDivider()
    }
}
