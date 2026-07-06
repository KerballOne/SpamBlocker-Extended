package spam.blocker.ui.widgets

import android.content.Intent
import android.media.RingtoneManager
import android.media.RingtoneManager.TYPE_ALL
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.platform.LocalContext
import spam.blocker.R
import spam.blocker.util.RingtoneUtil

@Composable
fun RingtonePicker(
    trigger: MutableState<Boolean>,
    // TYPE_ALL includes full continuous call ringtones, which are inappropriate for
    //  a brief non-continuous alert chime. Callers building a short alert (e.g. the
    //  per-app/per-rule Notification Screening Alert) should pass TYPE_NOTIFICATION.
    type: Int = TYPE_ALL,
    onRingtoneSelected: (String?, String?) -> Unit
) {
    val ctx = LocalContext.current

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        trigger.value = false
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            // Get the display name for the selected URI
            val displayName = uri?.let { RingtoneUtil.getName(ctx, it) }
            onRingtoneSelected(uri?.toString(), displayName)
        } else {
            onRingtoneSelected(null, null)
        }
    }

    // Launch the ringtone picker when trigger changes to true
    if (trigger.value) {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, type)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, ctx.getString(R.string.select_notification_sound))
        }
        ringtonePickerLauncher.launch(intent)
    }
}

