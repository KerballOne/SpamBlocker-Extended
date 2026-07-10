package spam.blocker.ui.setting.regex

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.R
import spam.blocker.service.resetVoicemailNotificationCache
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.setting.SettingLabel
import spam.blocker.ui.setting.SettingRow
import spam.blocker.ui.setting.quick.PopupChooseApps
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.BalloonQuestionMark
import spam.blocker.ui.widgets.DrawableImage
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.MultiSelectDropdownButton
import spam.blocker.ui.widgets.RowVCenter
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.AppInfo
import spam.blocker.util.spf


// Voicemail Notification: extracts candidate phone numbers from selected apps'
// notification title/body and runs them through the full Basic+Number+Text Rule
// pipeline (via Checker.checkSms), instead of only the Number/Text-Rule pre-check
// that ordinarily gates Notification Screening. See NotificationListenerService.kt
// for the matching side.
@Composable
fun VoicemailNotification() {
    val ctx = LocalContext.current
    val vmSpf = spf.VoicemailNotification(ctx)
    val notifSpf = spf.AppNotifications(ctx)

    var isEnabled by remember { mutableStateOf(vmSpf.isEnabled) }
    var collapsed by remember { mutableStateOf(vmSpf.isCollapsed) }

    val chosenApps = remember {
        mutableStateListOf<String>().apply {
            // Drop any app that's no longer in the Notification Screening set,
            //  e.g. the user removed it there after choosing it here.
            val stillScreened = vmSpf.getApps().filter { notifSpf.getList().contains(it) }
            if (stillScreened.size != vmSpf.getApps().size) {
                vmSpf.setApps(stillScreened)
            }
            addAll(stillScreened)
        }
    }

    val applyToTitle = remember { mutableStateOf(vmSpf.applyToTitle) }
    val applyToBody = remember { mutableStateOf(vmSpf.applyToBody) }
    val includeNumberTextRules = remember { mutableStateOf(vmSpf.includeNumberTextRules) }
    val allowIfCallAllowed = remember { mutableStateOf(vmSpf.allowIfCallAllowed) }

    LabeledRow(
        labelId = R.string.voicemail_notification,
        isCollapsed = collapsed,
        toggleCollapse = {
            collapsed = !collapsed
            vmSpf.isCollapsed = collapsed
        },
        helpTooltip = Str(R.string.help_voicemail_notification),
        content = {
            SwitchBox(isEnabled) { isTurningOn ->
                vmSpf.isEnabled = isTurningOn
                isEnabled = isTurningOn
                resetVoicemailNotificationCache()
            }
        }
    )

    AnimatedVisibleV(isEnabled && !collapsed) {
        Column {
            val appsPopupTrigger = remember { mutableStateOf(false) }

            PopupChooseApps(
                popupTrigger = appsPopupTrigger,
                finder = { pkgName ->
                    if (chosenApps.contains(pkgName)) true else null
                },
                onCheckChange = { pkgName, isChecked ->
                    if (isChecked) {
                        chosenApps.add(pkgName)
                    } else {
                        chosenApps.remove(pkgName)
                    }
                    vmSpf.setApps(chosenApps)
                    resetVoicemailNotificationCache()
                },
                // Restrict the picker to apps already chosen for Notification Screening,
                //  this feature only makes sense as a subset of those.
                appListProvider = { c ->
                    spf.AppNotifications(c).getList().map { AppInfo.fromPackage(c, it) }
                },
            )

            SettingRow(
                modifier = M
                    .padding(start = 20.dp)
                    .clickable { appsPopupTrigger.value = true },
            ) {
                RowVCenterSpaced(
                    space = 2,
                    modifier = M.fillMaxWidth()
                ) {
                    if (chosenApps.isEmpty()) {
                        GreyButton(Str(R.string.choose)) {
                            appsPopupTrigger.value = true
                        }
                    } else {
                        LazyRow(
                            modifier = M.padding(0.dp),
                        ) {
                            items(chosenApps) { pkgName ->
                                DrawableImage(
                                    AppInfo.fromPackage(ctx, pkgName).icon,
                                    modifier = M
                                        .size(20.dp)
                                        .padding(horizontal = 2.dp)
                                )
                            }
                        }
                    }

                    SettingLabel(R.string.voicemail_notification_apps)

                    BalloonQuestionMark(Str(R.string.help_voicemail_notification))

                    RowVCenter(
                        modifier = M.weight(1f),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        val options = remember {
                            listOf(
                                ctx.getString(R.string.title_short) to applyToTitle,
                                ctx.getString(R.string.body_short) to applyToBody,
                            )
                        }
                        MultiSelectDropdownButton(options = options)
                    }
                }
            }

            LabeledRow(
                labelId = null,
                paddingHorizontal = 20,
                helpTooltip = Str(R.string.help_voicemail_notification_options),
                content = {
                    val options = remember {
                        listOf(
                            ctx.getString(R.string.voicemail_notification_include_regex_rules) to includeNumberTextRules,
                            ctx.getString(R.string.voicemail_notification_allow_if_call_allowed) to allowIfCallAllowed,
                        )
                    }
                    MultiSelectDropdownButton(options = options)
                }
            )

            LaunchedEffect(applyToTitle.value, applyToBody.value, includeNumberTextRules.value, allowIfCallAllowed.value) {
                vmSpf.applyToTitle = applyToTitle.value
                vmSpf.applyToBody = applyToBody.value
                vmSpf.includeNumberTextRules = includeNumberTextRules.value
                vmSpf.allowIfCallAllowed = allowIfCallAllowed.value
                resetVoicemailNotificationCache()
            }
        }
    }
}
