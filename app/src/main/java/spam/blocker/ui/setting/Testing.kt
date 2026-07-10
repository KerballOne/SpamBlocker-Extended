package spam.blocker.ui.setting

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import spam.blocker.G
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.def.Def.ANDROID_12
import spam.blocker.service.CallScreeningService
import spam.blocker.service.SmsReceiver
import spam.blocker.ui.priorityInlineMap
import spam.blocker.ui.setting.regex.RegexMode.ModeType
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.BalloonQuestionMark
import spam.blocker.ui.widgets.FloatingWindow
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.Placeholder
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PopupSize
import spam.blocker.ui.widgets.RadioGroup
import spam.blocker.ui.widgets.RadioItem
import spam.blocker.ui.widgets.RowVCenter
import spam.blocker.ui.widgets.SimPicker
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.A
import spam.blocker.util.Clipboard
import spam.blocker.util.JetpackTextLogger
import spam.blocker.util.MultiLogger
import spam.blocker.util.SaveableLogger
import spam.blocker.util.Util
import spam.blocker.util.spf


class TestingViewModel {
    val selectedType = mutableIntStateOf(0)
    val phone = mutableStateOf("")
    val callerName = mutableStateOf("")
    val sms = mutableStateOf("")
    val notifTitle = mutableStateOf("")
    val notifBody = mutableStateOf("")
    val simSlot = mutableStateOf<Int?>(null)
    val showCallerID = mutableStateOf(false)
}


@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun TestDialog(
    trigger: MutableState<Boolean>,
) {
    val ctx = LocalContext.current
    val C = G.palette

    val coroutine = rememberCoroutineScope()

    val items = remember {
        listOf(
            RadioItem(text = ctx.getString(R.string.call), color = C.textGrey),
            RadioItem(text = ctx.getString(R.string.sms), color = C.textGrey),
            RadioItem(text = ctx.getString(R.string.notification), color = C.textGrey),
        )
    }

    val logStr = remember { mutableStateOf(buildAnnotatedString {}) }

    // Log output dialog
    val logTrigger = rememberSaveable { mutableStateOf(false) }
    PopupDialog(
        trigger = logTrigger,
        popupSize = PopupSize(maxWidthPercentage = 0.9f, minWidthDp = 320, maxWidthDp = 1200),
        buttons = if (logStr.value.text.length > 3000){
            {
                StrokeButton(Str(R.string.copy), color = C.teal200) {
                    Clipboard.copy(ctx, logStr.value.text)
                }
            }
        } else {
            null
        }
    ) {
        Text(
            text = logStr.value,
            color = C.textGrey, // the default text color
            inlineContent = priorityInlineMap()
        )
    }


    fun clearPreviousResult() {
        logStr.value = buildAnnotatedString {  }
    }

    val vm = G.testingVM

    PopupDialog(
        trigger = trigger,
        popupSize = PopupSize(maxWidthPercentage = 0.8f, minWidthDp = 340, maxWidthDp = 500),
        onDismiss = {
            if (spf.CallerID(ctx).isEnabled) {
                FloatingWindow.hide(ctx)
            }
        },
        title = {
            RowVCenter {
                GreyLabel(Str(R.string.title_rule_testing))
                BalloonQuestionMark(Str(R.string.help_test_rules))
            }
        },
        buttons = {

            val isForCall by remember {
                derivedStateOf {
                    vm.selectedType.intValue == 0
                }
            }
            val isForNotification by remember {
                derivedStateOf {
                    vm.selectedType.intValue == 2
                }
            }

            // Test Button
            StrokeButton(label = Str(R.string.test), color = C.teal200) {
                clearPreviousResult()
                logTrigger.value = true

                val multiLogger = MultiLogger(listOf(
                    JetpackTextLogger(logStr),
                    SaveableLogger()
                ))

                coroutine.launch(IO) {
                    when {
                        isForCall ->
                            CallScreeningService.processCall(
                                ctx, rawNumber = vm.phone.value, simSlot = vm.simSlot.value,
                                callDetails = null, cnap = vm.callerName.value.ifEmpty { null },
                                isTest = true, logger = multiLogger, showCallerId = vm.showCallerID.value
                            )
                        isForNotification -> {
                            // A rule can independently target the notification's Title and/or
                            //  Body (see help_apply_to_notification), so test each field that
                            //  was actually filled in as its own check rather than silently
                            //  collapsing them into one result.
                            if (vm.notifTitle.value.isNotEmpty()) {
                                SmsReceiver.processSms(
                                    ctx, rawNumber = vm.phone.value, messageBody = vm.notifTitle.value,
                                    simSlot = vm.simSlot.value, isTest = true, logger = multiLogger,
                                    source = Def.SOURCE_NOTIFICATION,
                                )
                            }
                            if (vm.notifBody.value.isNotEmpty()) {
                                SmsReceiver.processSms(
                                    ctx, rawNumber = vm.phone.value, messageBody = vm.notifBody.value,
                                    simSlot = vm.simSlot.value, isTest = true, logger = multiLogger,
                                    source = Def.SOURCE_NOTIFICATION,
                                )
                            }
                        }
                        else ->
                            SmsReceiver.processSms(
                                ctx, rawNumber = vm.phone.value, messageBody = vm.sms.value,
                                simSlot = vm.simSlot.value, isTest = true, logger = multiLogger
                            )
                    }
                }
            }
        },
        content = {
            Column {
                val isForCall by remember {
                    derivedStateOf {
                        vm.selectedType.intValue == 0
                    }
                }
                val isForSms by remember {
                    derivedStateOf {
                        vm.selectedType.intValue == 1
                    }
                }
                val isForNotification by remember {
                    derivedStateOf {
                        vm.selectedType.intValue == 2
                    }
                }

                // Type   [Call, SMS, Notification]
                LabeledRow(labelId = R.string.type) {
                    RadioGroup(items = items, selectedIndex = vm.selectedType.intValue) { newSel ->
                        vm.selectedType.intValue = newSel
                    }
                }

                // SIM
                LabeledRow(
                    labelId = R.string.sim_card,
                    color = if(Build.VERSION.SDK_INT < ANDROID_12) C.disabled else null,
                    helpTooltip = Str(R.string.help_test_sim_card)
                ) {
                    SimPicker(vm.simSlot)
                }

                // Show Caller ID
                AnimatedVisibleV(spf.CallerID(ctx).isEnabled && isForCall) {
                    LabeledRow(
                        labelId = R.string.caller_id
                    ) {
                        SwitchBox(vm.showCallerID.value) { isTurningOn ->
                            vm.showCallerID.value = isTurningOn
                        }
                    }
                }

                val geoLocation = remember(vm.phone.value) {
                    Util.numberGeoLocation(ctx, vm.phone.value)
                }
                val carrier = remember(vm.phone.value) {
                    Util.numberCarrier(ctx, vm.phone.value)
                }

                // Phone number
                StrInputBox(
                    text = vm.phone.value,
                    label = { GreyLabel(Str(R.string.phone_number)) },
                    placeholder = if (isForNotification) {
                        { Placeholder(Str(R.string.optional)) }
                    } else {
                        null
                    },
                    leadingIconId = R.drawable.ic_call,
                    onValueChange = {
                        vm.phone.value = it
                        clearPreviousResult()
                    },
                    supportingText = listOfNotNull(
                        // Geolocation
                        geoLocation?.let {
                            Str(R.string.label_value_pair).format(
                                Str(R.string.geolocation), geoLocation
                            )
                        },
                        // Carrier
                        carrier?.let {
                            Str(R.string.label_value_pair).format(
                                Str(R.string.carrier), carrier
                            )
                        }
                    ).takeIf { it.isNotEmpty() }?.joinToString("\n")?.A(C.textGrey),
                )

                // Only show the Caller Name field when there's at least 1 CNAP rule configured
                var hasCnapRule by remember(G.NumberRuleVM.rules, G.ContentRuleVM.rules) {
                    val foundNumberRule = G.NumberRuleVM.rules.any {
                        it.patternModeType == ModeType.CallerName
                    }

                    val foundContentRule = G.ContentRuleVM.rules.any {
                        it.patternExtraModeType == ModeType.CallerName
                    }

                    mutableStateOf(foundNumberRule || foundContentRule)
                }

                // Caller Name
                AnimatedVisibleV(isForCall && hasCnapRule) {
                    StrInputBox(
                        text = vm.callerName.value,
                        label = { GreyLabel(Str(R.string.caller_name)) },
                        leadingIconId = R.drawable.ic_id_card,
                        onValueChange = {
                            vm.callerName.value = it
                            clearPreviousResult()
                        },
                    )
                }
                // SMS content
                AnimatedVisibleV(isForSms) {
                    StrInputBox(
                        text = vm.sms.value,
                        label = { GreyLabel(Str(R.string.sms_content)) },
                        leadingIconId = R.drawable.ic_sms,
                        onValueChange = {
                            vm.sms.value = it
                            clearPreviousResult()
                        }
                    )
                }

                // Notification Title
                AnimatedVisibleV(isForNotification) {
                    StrInputBox(
                        text = vm.notifTitle.value,
                        label = { GreyLabel(Str(R.string.title_short)) },
                        leadingIconId = R.drawable.ic_sms,
                        onValueChange = {
                            vm.notifTitle.value = it
                            clearPreviousResult()
                        }
                    )
                }
                // Notification Body
                AnimatedVisibleV(isForNotification) {
                    StrInputBox(
                        text = vm.notifBody.value,
                        label = { GreyLabel(Str(R.string.body_short)) },
                        leadingIconId = R.drawable.ic_sms,
                        onValueChange = {
                            vm.notifBody.value = it
                            clearPreviousResult()
                        }
                    )
                }

                if ((isForCall && !G.callEnabled.value) ||
                    (isForSms && !G.smsEnabled.value) ||
                    (isForNotification && !spf.Global(ctx).isNotificationScreeningEnabled)
                ) {
                    Text(
                        text = Str(
                            when {
                                isForCall -> R.string.call_screening_not_enabled
                                isForSms -> R.string.sms_screening_not_enabled
                                else -> R.string.notification_screening_not_enabled
                            }
                        ),
                        color = C.warning,
                    )
                }
            }
        }
    )
}
