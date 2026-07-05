package spam.blocker.ui.widgets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.slightDiff
import spam.blocker.util.Lambda

@Composable
fun Section(
    title: String?,
    horizontalPadding: Int = 0,
    bgColor: Color = G.palette.background,

    // null: not collapsible, stays always expanded, no header chevron/click.
    // true/false: collapsible, current collapsed state.
    collapsed: Boolean? = null,
    onToggleCollapse: Lambda? = null,

    // Optional short status content shown next to the title, e.g. a rule count or on/off state.
    badge: (@Composable () -> Unit)? = null,

    // Optional content shown at the far right of the header row, e.g. status icons + a toggle switch.
    headerTrailing: (@Composable () -> Unit)? = null,

    content: @Composable () -> Unit,
) {
    val cardColor = bgColor.slightDiff()
    val isCollapsible = collapsed != null

    val arrowRotation by animateFloatAsState(if (collapsed == true) -90f else 0f)

    Box(
        modifier = M
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(cardColor)
    ) {
        Box(modifier = M.wrapContentHeight()) {
            Column {
                if (title != null) {
                    RowVCenter(
                        modifier = M
                            .fillMaxWidth()
                            .let {
                                if (isCollapsible) it.clickable { onToggleCollapse?.invoke() } else it
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        if (isCollapsible) {
                            GreyIcon16(
                                iconId = R.drawable.ic_dropdown_arrow,
                                modifier = M.rotate(arrowRotation),
                            )
                            Box(modifier = M.padding(start = 6.dp))
                        }
                        Text(
                            text = title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = G.palette.infoBlue,
                        )
                        if (badge != null || headerTrailing != null) {
                            RowVCenter(
                                modifier = M.weight(1f),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                badge?.invoke()
                                if (badge != null && headerTrailing != null) {
                                    Box(modifier = M.padding(start = 8.dp))
                                }
                                headerTrailing?.invoke()
                            }
                        }
                    }
                }
                if (collapsed != true) {
                    if (isCollapsible) {
                        HorizontalDivider(thickness = 1.dp, color = cardColor.slightDiff())
                    }
                    Box(
                        modifier = M
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .wrapContentHeight()
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

// A Section header badge showing separate Allow/Block counts, e.g. for Number/Text Rules.
@Composable
fun AllowBlockCountBadge(
    allowCount: Int,
    blockCount: Int,
) {
    RowVCenter {
        ResIcon16(R.drawable.ic_check_green, color = G.palette.success)
        Box(modifier = M.padding(start = 2.dp))
        Text(text = "$allowCount", fontSize = 12.sp, color = G.palette.textGrey)

        Box(modifier = M.padding(start = 10.dp))

        ResIcon16(R.drawable.ic_fail_red, color = G.palette.error)
        Box(modifier = M.padding(start = 2.dp))
        Text(text = "$blockCount", fontSize = 12.sp, color = G.palette.textGrey)
    }
}
