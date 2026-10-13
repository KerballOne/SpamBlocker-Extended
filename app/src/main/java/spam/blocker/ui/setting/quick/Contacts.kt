package spam.blocker.ui.setting.quick

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.Events
import spam.blocker.G
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PopupSize
import spam.blocker.ui.widgets.PriorityBox
import spam.blocker.ui.widgets.PriorityLabel
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Section
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.spf

@Composable
fun Contacts() {
    val ctx = LocalContext.current
    val C = G.palette

    val spf = spf.Contact(ctx)

    var isEnabled by remember { mutableStateOf(spf.isEnabled && Permission.contacts.isGranted) }
    var isStrict by remember { mutableStateOf(spf.isStrict) }
    var priLenient by remember { mutableIntStateOf(spf.lenientPriority) }
    var priStrict by remember { mutableIntStateOf(spf.strictPriority) }
    val allowForCall = remember { mutableStateOf(spf.allowForCall) }
    val allowForSms = remember { mutableStateOf(spf.allowForSms) }
    val allowForMms = remember { mutableStateOf(spf.allowForMms) }
    val allowForTitle = remember { mutableStateOf(spf.allowForTitle) }
    val allowForBody = remember { mutableStateOf(spf.allowForBody) }
    val blockForCall = remember { mutableStateOf(spf.blockForCall) }
    val blockForSms = remember { mutableStateOf(spf.blockForSms) }
    val blockForMms = remember { mutableStateOf(spf.blockForMms) }
    val blockForTitle = remember { mutableStateOf(spf.blockForTitle) }
    val blockForBody = remember { mutableStateOf(spf.blockForBody) }

    val popupTrigger = rememberSaveable { mutableStateOf(false) }

    PopupDialog(
        trigger = popupTrigger,
        // As wide as the Number Rule editor, so all 5 Apply to chips fit on one row.
        popupSize = PopupSize(maxWidthPercentage = 0.9f, minWidthDp = 340, maxWidthDp = 600),
        content = {
            Column {
                Section(
                    title = Str(R.string.contacts),
                    bgColor = C.dialogBg,
                    compact = true,
                ) {
                    Column {
                        LabeledRow(R.string.allow) {
                            SwitchBox(isEnabled) { isTurningOn ->
                                spf.isEnabled = isTurningOn
                                isEnabled = isTurningOn
                            }
                        }

                        AnimatedVisibleV(isEnabled) {
                            Column {
                                PriorityBox(priLenient) { newValue, hasError ->
                                    if (!hasError) {
                                        priLenient = newValue!!
                                        spf.lenientPriority = newValue
                                    }
                                }
                                ApplyToTypes(allowForCall, allowForSms, allowForMms, allowForTitle, allowForBody) {
                                    spf.allowForCall = allowForCall.value
                                    spf.allowForSms = allowForSms.value
                                    spf.allowForMms = allowForMms.value
                                    spf.allowForTitle = allowForTitle.value
                                    spf.allowForBody = allowForBody.value
                                }
                            }
                        }
                    }
                }
                AnimatedVisibleV(isEnabled) {
                    Section(
                        title = Str(R.string.non_contacts),
                        bgColor = C.dialogBg,
                        compact = true,
                    ) {
                        Column {
                            LabeledRow(R.string.block) {
                                SwitchBox(isStrict) { isTurningOn ->
                                    spf.isStrict = isTurningOn
                                    isStrict = isTurningOn
                                }
                            }

                            AnimatedVisibleV(isStrict) {
                                Column {
                                    PriorityBox(priStrict) { newValue, hasError ->
                                        if (!hasError) {
                                            priStrict = newValue!!
                                            spf.strictPriority = newValue
                                        }
                                    }
                                    ApplyToTypes(blockForCall, blockForSms, blockForMms, blockForTitle, blockForBody) {
                                        spf.blockForCall = blockForCall.value
                                        spf.blockForSms = blockForSms.value
                                        spf.blockForMms = blockForMms.value
                                        spf.blockForTitle = blockForTitle.value
                                        spf.blockForBody = blockForBody.value
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )

    LabeledRow(
        R.string.contacts,
        helpTooltip = Str(R.string.help_contacts),
        content = {
            if (isEnabled) {
                StrokeButton(
                    color = C.textGrey,
                    icon = {
                        RowVCenterSpaced(6) {
                            // Contacts
                            RowVCenterSpaced(2) {
                                GreyIcon16(R.drawable.ic_contact_square)
                                if (priLenient != 10) {
                                    PriorityLabel(priLenient)
                                }
                            }

                            // Non Contacts
                            if (isStrict) {
                                // Vertical Divider
                                VerticalDivider(thickness = 1.dp, color = C.disabled)

                                RowVCenterSpaced(2) {
                                    ResIcon(
                                        R.drawable.ic_question,
                                        modifier = M.size(16.dp),
                                        color = C.error
                                    )
                                    if (priStrict != 0) {
                                        PriorityLabel(priStrict)
                                    }
                                }
                            }
                        }
                    }
                ) {
                    popupTrigger.value = true
                }
            }

            SwitchBox(isEnabled) { isTurningOn ->
                if (isTurningOn) {
                    G.permissionChain.ask(
                        ctx,
                        listOf(PermissionWrapper(Permission.contacts))
                    ) { granted ->
                        if (granted) {
                            spf.isEnabled = true
                            isEnabled = true
                            Events.basicRuleUpdated.fire()
                        }
                    }
                } else {
                    spf.isEnabled = false
                    isEnabled = false
                    Events.basicRuleUpdated.fire()
                }
            }
        }
    )
}