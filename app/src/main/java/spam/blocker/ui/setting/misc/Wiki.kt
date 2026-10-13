package spam.blocker.ui.setting.misc

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import spam.blocker.G
import spam.blocker.R
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton

@Composable
fun Wiki() {
    val C = G.palette
    val ctx = LocalContext.current

    StrokeButton(
        label = Str(R.string.wiki),
        color = C.infoBlue,
        onClick = {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, "$REPO/wiki".toUri()))
        }
    )
}
