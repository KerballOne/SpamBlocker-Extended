package spam.blocker.ui.setting

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import spam.blocker.G
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.ui.M
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.HtmlText
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.ResImage
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Section
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.ui.setting.quick.AppNotificationsPicker
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.Util
import spam.blocker.util.Util.isDefaultSmsAppNotificationEnabled
import spam.blocker.util.spf

@Composable
fun GloballyEnabled() {
    val ctx = LocalContext.current
    val C = G.palette
    val sectionSpf = spf.SectionCollapse(ctx)
    val spf = spf.Global(ctx)

    var sectionCollapsed by remember { mutableStateOf(sectionSpf.isScreeningCollapsed) }
    fun expandSection() {
        sectionCollapsed = false
        sectionSpf.isScreeningCollapsed = false
    }
    fun collapseSection() {
        sectionCollapsed = true
        sectionSpf.isScreeningCollapsed = true
    }

    val doubleSmsWarningTrigger = remember { mutableStateOf(false) }

    if (doubleSmsWarningTrigger.value) {
        PopupDialog(
            trigger = doubleSmsWarningTrigger,
            icon = { ResIcon(R.drawable.ic_warning, color = Color.Unspecified) },
            buttons = {
                StrokeButton(label = Str(R.string.dismiss), color = C.warning) {
                    spf.isDoubleSMSWarningDismissed = true
                    doubleSmsWarningTrigger.value = false
                }
                Spacer(modifier = M.width(10.dp))
                StrokeButton(label = Str(R.string.open_settings), color = C.teal200) {
                    doubleSmsWarningTrigger.value = false
                    Util.openDefaultSmsAppNotificationSetting(ctx)
                }
            },
        ) {
            HtmlText(Str(R.string.warning_double_sms))
        }
    }
    // Show warnings `onResume`
    // - double sms notification
    LifecycleResumeEffect(G.smsEnabled.value) {
        if (G.smsEnabled.value) {
            if (Build.VERSION.SDK_INT >= Def.ANDROID_13) {
                if (isDefaultSmsAppNotificationEnabled(ctx) && spf.isGloballyEnabled && spf.isSmsEnabled) {
                    if (!spf.isDoubleSMSWarningDismissed) {
                        doubleSmsWarningTrigger.value = true
                    }
                }
            }
        }
        onPauseOrDispose { }
    }

    Section(
        title = Str(R.string.screening),
        horizontalPadding = 8,
        collapsed = sectionCollapsed,
        onToggleCollapse = {
            sectionCollapsed = !sectionCollapsed
            sectionSpf.isScreeningCollapsed = sectionCollapsed
        },
        headerTrailing = {
            RowVCenterSpaced(4) {
                ResImage(
                    R.drawable.ic_call,
                    if (G.globallyEnabled.value && G.callEnabled.value) C.teal200 else C.disabled,
                    M.size(20.dp)
                )
                ResImage(
                    R.drawable.ic_sms,
                    if (G.globallyEnabled.value && G.smsEnabled.value) C.teal200 else C.disabled,
                    M.size(20.dp)
                )
                ResImage(
                    R.drawable.ic_notification,
                    if (G.globallyEnabled.value && G.notificationScreeningEnabled.value) C.teal200 else C.disabled,
                    M.size(20.dp)
                )
            }
            Box(modifier = M.padding(start = 10.dp))
            SwitchBox(G.globallyEnabled.value) { enabled ->
                spf.isGloballyEnabled = enabled
                G.globallyEnabled.value = enabled
                if (enabled) {
                    if (!G.callEnabled.value && !G.smsEnabled.value) {
                        expandSection()
                    }
                } else {
                    collapseSection()
                }
            }
        },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            LabeledRow(
                labelId = R.string.calls,
            ) {
                SwitchBox(checked = G.callEnabled.value, onCheckedChange = { isTurningOn ->
                    if (isTurningOn) {
                        G.permissionChain.ask(
                            ctx,
                            listOf(
                                PermissionWrapper(Permission.callScreening),
                            )
                        ) { granted ->
                            if (granted) {
                                spf.isCallEnabled = true
                                G.callEnabled.value = true
                            }
                        }
                    } else {
                        spf.isCallEnabled = false
                        G.callEnabled.value = false
                    }
                })
            }

            LabeledRow(
                labelId = R.string.sms_mms,
                helpTooltip = Str(R.string.help_enable_for_sms).format(Str(R.string.warning_double_sms))
            ) {
                SwitchBox(checked = G.smsEnabled.value, onCheckedChange = { isTurningOn ->
                    if (isTurningOn) {
                        G.permissionChain.ask(
                            ctx,
                            listOf(
                                PermissionWrapper(Permission.receiveSMS),
                                // isOptional because some might prefer "Optimized" than "Unrestricted"
                                PermissionWrapper(Permission.batteryUnRestricted, isOptional = true),
                                // MMS screening rides along with SMS, no separate toggle;
                                // isOptional so declining it doesn't block enabling SMS screening.
                                PermissionWrapper(Permission.receiveMMS, isOptional = true),
                                PermissionWrapper(Permission.readSMS, isOptional = true),
                            )
                        ) { granted ->
                            if (granted) {
                                spf.isSmsEnabled = true
                                G.smsEnabled.value = true
                            }
                        }
                    } else {
                        spf.isSmsEnabled = false
                        G.smsEnabled.value = false
                    }
                })
            }

            LabeledRow(
                labelId = R.string.notifications,
                helpTooltip = Str(R.string.help_notification_screening),
            ) {
                if (G.notificationScreeningEnabled.value) {
                    AppNotificationsPicker(modifier = M.padding(end = 8.dp))
                }
                SwitchBox(checked = G.notificationScreeningEnabled.value, onCheckedChange = { isTurningOn ->
                    if (isTurningOn) {
                        G.permissionChain.ask(
                            ctx,
                            listOf(
                                PermissionWrapper(Permission.notificationAccess),
                            )
                        ) { granted ->
                            if (granted) {
                                spf.isNotificationScreeningEnabled = true
                                G.notificationScreeningEnabled.value = true
                            }
                        }
                    } else {
                        spf.isNotificationScreeningEnabled = false
                        G.notificationScreeningEnabled.value = false
                    }
                })
            }

            // MMS and RCS toggles removed from Screening:
            // - MMS reception is always on whenever SMS screening is (see WapPushReceiver,
            //   gated only on isSmsEnabled), there's no separate master switch for it.
            //   Whether a given rule actually applies to MMS content is now controlled
            //   per-rule via the "Apply to" section on Number/Text Rules instead.
            // - RCS was a permanently-disabled placeholder row that instructed users to
            //   downgrade to an unencrypted messaging app in order to enable it, which is
            //   actively harmful security advice; it never had a working implementation.
        }
    }

}
