package spam.blocker.ui.setting.quick

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import spam.blocker.Events
import spam.blocker.R
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.widgets.MultiSelectDropdownButton

// A Basic Rule's "Apply to" chips: Calls / SMS / MMS / notification Title / notification Body,
//  the same as a Number Rule's.
// `onChange` persists the states whenever a chip is toggled.
@Composable
fun ApplyToTypes(
    forCall: MutableState<Boolean>,
    forSms: MutableState<Boolean>,
    forMms: MutableState<Boolean>,
    forTitle: MutableState<Boolean>,
    forBody: MutableState<Boolean>,
    onChange: () -> Unit,
) {
    val ctx = LocalContext.current

    // No label, like a Number Rule's chips, so all fit on one row.
    LabeledRow(labelId = null) {
        val options = remember {
            listOf(
                ctx.getString(R.string.calls) to forCall,
                ctx.getString(R.string.sms) to forSms,
                ctx.getString(R.string.mms) to forMms,
                ctx.getString(R.string.title_short) to forTitle,
                ctx.getString(R.string.body_short) to forBody,
            )
        }
        MultiSelectDropdownButton(options = options)
    }

    // The chips only flip their states, persist on every change (not on the first composition).
    val isFirstRun = remember { mutableStateOf(true) }
    LaunchedEffect(forCall.value, forSms.value, forMms.value, forTitle.value, forBody.value) {
        if (isFirstRun.value) {
            isFirstRun.value = false
        } else {
            onChange()
            Events.basicRuleUpdated.fire()
        }
    }
}
