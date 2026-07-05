package spam.blocker.ui.setting.regex

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.RegexRule
import spam.blocker.def.Def
import spam.blocker.def.Def.ForNumber
import spam.blocker.def.Def.ForQuickCopy
import spam.blocker.def.Def.ForSms
import spam.blocker.ui.M
import spam.blocker.ui.setting.quick.ChannelIcons
import spam.blocker.ui.setting.regex.RegexMode.ModeType
import spam.blocker.ui.setting.regex.RegexMode.regexModeInlineMap
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.GreyIcon20
import spam.blocker.ui.widgets.OutlineCard
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.SimCardIcon
import spam.blocker.util.enabledRegexFlagsStr
import spam.blocker.util.hasFlag
import spam.blocker.util.spf

// Compact "Call, SMS, MMS, Title, Body" readout for the consolidated Apply-to dropdown.
fun RegexRule.applyToSummary(ctx: Context, forType: Int): String {
    val forCNAP = forType == ForNumber && patternModeType == ModeType.CallerName

    val parts = buildList {
        if (forType != ForSms && !forCNAP) {
            if (isForCall()) add(ctx.getString(R.string.calls))
        }
        if (!forCNAP) {
            if (isForSms()) add(ctx.getString(R.string.sms))
            if (isForMms()) add(ctx.getString(R.string.mms))
        }
        if (forType != ForQuickCopy) {
            if (isForNotifTitle()) add(ctx.getString(R.string.title_short))
            if (isForNotifBody()) add(ctx.getString(R.string.body_short))
        }
    }
    return parts.joinToString(", ")
}

@Composable
fun RegexCard(
    rule: RegexRule,
    forType: Int,
    modifier: Modifier = Modifier,
) {
    val C = G.palette
    val ctx = LocalContext.current
    val spf = spf.RegexOptions(ctx)

    OutlineCard {
        Box(
            modifier = modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = M.fillMaxWidth().padding(end = 60.dp),
            ) {
                // Regex and Description
                Column(
                    M.weight(1f).padding(end = 4.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    RowVCenterSpaced(2) {
                        if (rule.simSlot != null) {
                            SimCardIcon(rule.simSlot!!)
                        }
                        // Regex
                        Text(
                            text = rule.colorfulRegexStr(
                                ctx = LocalContext.current,
                                forType = forType,
                            ),
                            inlineContent = regexModeInlineMap(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = M.padding(top = 2.dp),
                            maxLines = spf.maxRegexRows,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Description
                    if (rule.description.isNotEmpty()) {
                        Text(
                            text = rule.description,
                            fontSize = 18.sp,
                            maxLines = spf.maxDescRows,
                            overflow = TextOverflow.Ellipsis,
                            color = C.textGrey,
                            modifier = M.padding(start = 10.dp),
                        )
                    }

                    // Apply-to summary
                    Text(
                        text = rule.applyToSummary(ctx, forType),
                        color = C.textGrey,
                        fontSize = 11.sp,
                        modifier = M.padding(start = 10.dp, top = 2.dp),
                    )
                }
            }

            // [Regex flags]  [Number, Message]  [BlockType] -- top right
            val flagsStr = rule.patternFlags.enabledRegexFlagsStr()
            val showTopRow = flagsStr.isNotEmpty() ||
                    (forType == Def.ForQuickCopy && (rule.flags.hasFlag(Def.FLAG_FOR_NUMBER) || rule.flags.hasFlag(Def.FLAG_FOR_CONTENT))) ||
                    (forType == Def.ForNumber && rule.isBlacklist && rule.isForCall())
            if (showTopRow) {
                RowVCenterSpaced(
                    space = 6,
                    modifier = M.align(Alignment.TopEnd),
                ) {
                    // [Regex flags]
                    if (flagsStr.isNotEmpty()) {
                        Text(
                            text = flagsStr,
                            fontSize = 12.sp,
                            color = C.regexFlags,
                        )
                    }
                    if (forType == Def.ForQuickCopy) {
                        // [Number, Message]
                        RowVCenterSpaced(space = 4) {
                            if (rule.flags.hasFlag(Def.FLAG_FOR_NUMBER))
                                GreyIcon16( iconId = R.drawable.ic_number_sign )
                            if (rule.flags.hasFlag(Def.FLAG_FOR_CONTENT))
                                GreyIcon16(iconId = R.drawable.ic_open_msg)
                        }
                    }
                    if (forType == Def.ForNumber && rule.isBlacklist && rule.isForCall()) {
                        // [BlockType]
                        when (rule.blockType) {
                            0 -> GreyIcon20( iconId = R.drawable.ic_call_blocked )
                            1 -> GreyIcon20( iconId = R.drawable.ic_call_miss )
                            2 -> GreyIcon20(iconId = R.drawable.ic_hang)
                        }
                    }
                }
            }

            // [NotifyType]  [Priority] -- bottom right
            RowVCenterSpaced(
                space = 8,
                modifier = M.align(Alignment.BottomEnd),
            ) {

                // [NotifyType]
                val forCNAP = forType == ForNumber && rule.patternModeType == ModeType.CallerName
                val applyToSms = rule.isForSms()

                val visible = when (forType) {
                    ForNumber -> rule.isBlacklist || (!forCNAP && applyToSms)
                    ForSms   -> true
                    else     -> false
                }

                if (visible) {
                    val ch = G.notificationChannels.find { it.channelId == rule.channel }
                    ChannelIcons(ch?.importance, ch?.mute)
                }

                // [Priority]
                ResIcon(R.drawable.ic_priority, color = C.priority, modifier = M.size(18.dp).offset(6.dp))
                Text(
                    text = "${rule.priority}",
                    color = C.priority,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

