package spam.blocker.ui.setting.quick

import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.NotificationManager.IMPORTANCE_NONE
import android.graphics.BitmapFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.serialization.json.Json
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.ContentRegexTable
import spam.blocker.db.Notification.Channel
import spam.blocker.db.Notification.ChannelTable
import spam.blocker.db.NumberRegexTable
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.slightDiff
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.ColorPickerButton
import spam.blocker.ui.widgets.ComboBox
import spam.blocker.ui.widgets.FileChooser
import spam.blocker.ui.widgets.FooterButton
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.GreyIcon18
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.GreyText
import spam.blocker.ui.widgets.HtmlText
import spam.blocker.ui.widgets.InitFile
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.MIME_ICON
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.RingtonePicker
import spam.blocker.ui.widgets.RowVCenter
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.AppIcon
import spam.blocker.util.AppInfo
import spam.blocker.util.FileUtils.readDataFromUri
import spam.blocker.util.Lambda2
import spam.blocker.util.Notification
import spam.blocker.util.Notification.createChannel
import spam.blocker.util.Notification.isBuiltInChannel
import spam.blocker.util.Notification.manager
import spam.blocker.util.Notification.openChannelSettings
import spam.blocker.util.Notification.reloadChannels
import spam.blocker.util.RingtoneUtil
import spam.blocker.util.spf
import spam.blocker.util.spf.AppAlertConfig
import androidx.compose.material3.Text
import androidx.compose.foundation.Image as ComposeImage


const val Notification_Icon_Last_Dir_Tag = "notification_icon_last_dir_tag"

@Composable
fun ChannelIcons(
    importance: Int?,
    mute: Boolean?,
    color: Color = G.palette.textGrey,
) {

    G.notificationChannels

    if (importance != null) {
        when(importance) {
            IMPORTANCE_NONE -> ResIcon(iconId = R.drawable.ic_bell_mute, modifier = M.size(16.dp), color = color)
            IMPORTANCE_LOW -> ResIcon(iconId = R.drawable.ic_statusbar_shade, modifier = M.size(16.dp), color = color)
            IMPORTANCE_DEFAULT -> {
                RowVCenterSpaced(2) {
                    if (!mute!!) {
                        ResIcon(R.drawable.ic_bell_ringing, modifier = M.size(16.dp), color = color)
                    }
                    ResIcon(R.drawable.ic_statusbar_shade, modifier = M.size(16.dp), color = color)
                }
            }
            IMPORTANCE_HIGH -> {
                RowVCenterSpaced(2) {
                    if (!mute!!) {
                        ResIcon(R.drawable.ic_bell_ringing, modifier = M.size(16.dp), color = color)
                    }
                    ResIcon(R.drawable.ic_statusbar_shade, modifier = M.size(16.dp), color = color)
                    ResIcon(R.drawable.ic_heads_up, modifier = M.size(16.dp), color = color)
                }
            }
        }
    } else {
        ResIcon(R.drawable.ic_question_circle, modifier = M.size(16.dp), color = G.palette.warning)
    }
}

@Composable
fun EditChannelDialog(
    editTrigger: MutableState<Boolean>,
    initChannel: Channel,
) {
    val ctx = LocalContext.current
    if (!editTrigger.value) {
        return
    }
    val C = G.palette

    var chId by remember { mutableStateOf(initChannel.channelId) }
    var importance by remember { mutableIntStateOf(initChannel.importance) }
    var group by remember { mutableStateOf(initChannel.group) }
    var mute by remember { mutableStateOf(initChannel.mute) }
    var sound by remember { mutableStateOf(initChannel.sound) }
    var soundName by remember(sound) { mutableStateOf(RingtoneUtil.getName(ctx, sound.toUri())) }
    var icon by remember { mutableStateOf(initChannel.icon) }
    var iconColor by remember { mutableStateOf<Int?>(initChannel.iconColor) }
    var led by remember { mutableStateOf(initChannel.led) }
    var ledColor by remember { mutableIntStateOf(initChannel.ledColor) }


    val isCreatingNewChannel by remember { mutableStateOf(initChannel.channelId == "") }
    val isBuiltin by remember(chId) { mutableStateOf(isBuiltInChannel(chId)) }

    var anyError by remember(chId) {
        mutableStateOf(
            chId.isEmpty()
        )
    }
    PopupDialog(
        trigger = editTrigger,
        buttons = {
            RowVCenterSpaced(8) {
                // Delete
                val deleteConfirm = remember { mutableStateOf(false) }
                PopupDialog(
                    trigger = deleteConfirm,
                    buttons = {
                        StrokeButton(label = Str(R.string.delete), color = C.error) {
                            Notification.deleteChannel(ctx, chId)
                            ChannelTable.deleteByChannelId(ctx, chId)
                            reloadChannels(ctx)
                            deleteConfirm.value = false
                            editTrigger.value = false
                        }
                    }
                ) {
                    // Show a warning: this channel is currently used by following rules...
                    val usedByRules = (NumberRegexTable().listAll(ctx) + ContentRegexTable().listAll(ctx))
                        .filter { it.channel == chId }
                        .map { ctx.getString(R.string.regex_pattern) + " " + it.descOrPattern() }
                        .toMutableList()
                    val spf = spf.Notification(ctx)
                    if (spf.spamCallChannelId == chId) {
                        usedByRules += ctx.getString(R.string.call)
                    }
                    if (spf.spamSmsChannelId == chId || spf.validSmsChannelId == chId) {
                        usedByRules += ctx.getString(R.string.sms)
                    }
                    if (usedByRules.isNotEmpty()) {
                        HtmlText(ctx.getString(R.string.warning_delete_channel)
                            .format(usedByRules.joinToString ("<br>")))
                    }

                    GreyText(Str(R.string.confirm_to_delete))
                }
                StrokeButton(
                    label = Str(R.string.delete),
                    enabled = !isBuiltin && chId.isNotEmpty(),
                    color = if (!isBuiltin && chId.isNotEmpty()) C.error else C.disabled
                ) {
                    deleteConfirm.value = true
                }

                // Save
                StrokeButton(
                    label = Str(R.string.save),
                    enabled = !anyError,
                    color = if (anyError) C.disabled else C.teal200
                ) {
                    val newCh = Channel(
                        channelId = chId,
                        importance = importance,
                        group = group,
                        mute = mute,
                        sound = sound,
                        icon = icon,
                        iconColor = iconColor,
                        led = led,
                        ledColor = ledColor,
                    )
                    // 1. create notification channel
                    createChannel(ctx, newCh)
                    // 2. update db
                    ChannelTable.addOrReplace(ctx, newCh)
                    // 3. refresh the channel list
                    reloadChannels(ctx)

                    editTrigger.value = false
                }
            }
        }
    ) {
        Column {
            // Sync sound/ledColor with system channel, user might have manually changed it in system settings.
            // Don't sync other attributes, as they are supposed to be edited within this app,
            //  sound and ledColor can only be modified in system settings.
            LifecycleResumeEffect(true) {
                // Do not sync when creating a new channel
                if (!isCreatingNewChannel) {
                    val sysCh = manager(ctx).getNotificationChannel(chId)
                    if (sysCh != null) {
                        sound = sysCh.sound?.toString() ?: ""
                        led = sysCh.shouldShowLights()
                        ledColor = sysCh.lightColor
                        importance = sysCh.importance
                    }
                }

                onPauseOrDispose { }
            }


            // Channel Id
            StrInputBox(
                label = { GreyLabel(Str(R.string.channel_id)) },
                text = chId,
                helpTooltip = Str(R.string.help_channel_id),
                enabled = isCreatingNewChannel, // only enable for creating channels
                onValueChange = {
                    if (it.isNotEmpty()) {
                        chId = it
                    }
                }
            )

            // Group
            StrInputBox(
                label = { GreyLabel(Str(R.string.channel_group)) },
                text = group,
                helpTooltip = Str(R.string.help_channel_group),
                onValueChange = {
                    group = it
                }
            )

            // Importance
            val ids = remember {
                listOf(
                    IMPORTANCE_NONE, IMPORTANCE_LOW, IMPORTANCE_DEFAULT, IMPORTANCE_HIGH
                )
            }
            val names = remember {
                listOf(
                    ctx.getString(R.string.none),
                    ctx.getString(R.string.low),
                    ctx.getString(R.string.medium),
                    ctx.getString(R.string.high)
                )
            }
            val importanceItems = remember {
                ids.mapIndexed { index, impo ->
                    LabelItem(
                        label = names[index],
                        leadingIcon = {
                            ChannelIcons(impo, false)
                        },
                    ) {
                        importance = impo
                    }
                }
            }
            LabeledRow(
                labelId = R.string.channel_importance,
                helpTooltip = Str(R.string.help_channel_importance)
            ) {
                RowVCenterSpaced(6) {
                    ComboBox(
                        items = importanceItems,
                        selected = ids.indexOf(importance),
                        enabled = isCreatingNewChannel,
                    )
                    if (!isCreatingNewChannel) {
                        ResIcon(R.drawable.ic_note, modifier = M.clickable {
                            openChannelSettings(ctx, chId)
                        })
                    }
                }
            }

            // Mute + Sound
            AnimatedVisibleV(importance >= IMPORTANCE_DEFAULT) {
                Column {
                    // Mute + Sound
                    val soundTrigger = remember { mutableStateOf(false) }
                    RingtonePicker(soundTrigger) { uri, name ->
                        uri?.let {
                            sound = uri
                        }
                    }
                    // Mute
                    LabeledRow(
                        R.string.mute,
                        helpTooltip = Str(R.string.help_mute_channel),
                    ) {
                        SwitchBox(mute) { isTurningOn ->
                            mute = isTurningOn
                        }
                    }

                    // Sound
                    AnimatedVisibleV(!mute) {
                        LabeledRow(
                            labelId = R.string.sound,
                            helpTooltip = Str(R.string.help_sound),
                        ) {
                            RowVCenterSpaced(6) {
                                GreyButton(
                                    if (sound.isEmpty() || sound == "content://settings/system/notification_sound")
                                        ctx.getString(R.string.default_)
                                    else
                                        soundName,
                                    enabled = isCreatingNewChannel,
                                ) {
                                    soundTrigger.value = true
                                }
                                if (!isCreatingNewChannel) {
                                    ResIcon(R.drawable.ic_note, modifier = M.clickable {
                                        openChannelSettings(ctx, chId)
                                    })
                                }
                            }
                        }
                    }
                }
            }

            // Icon
            LabeledRow(R.string.icon) {
                fun choose() {
                    FileChooser.popupRead(
                        init = InitFile(
                            filename = "",
                            mimeType = MIME_ICON,
                            rememberDirTag = Notification_Icon_Last_Dir_Tag,
                        ),
                        onResult = { uri ->
                            if (uri != null) {
                                val raw = readDataFromUri(ctx, uri)
                                    ?: return@popupRead

                                icon = raw
                            }
                        }
                    )
                }

                if (icon == null) {
                    // Auto Icon button
                    GreyButton(Str(R.string.automatic)) {
                        choose()
                    }
                } else {
                    RowVCenterSpaced(6) {
                        // Icon image
                        ComposeImage(
                            BitmapFactory.decodeByteArray(icon, 0, icon!!.size).asImageBitmap(),
                            "",
                            modifier = M.size(30.dp).clickable {
                                choose()
                            }
                        )
                        // Clear icon
                        GreyButton(Str(R.string.clear)) {
                            icon = null
                        }
                    }
                }
            }

            // Icon Color

            LabeledRow(
                R.string.icon_color,
            ) {
                RowVCenterSpaced(4) {
                    ColorPickerButton(
                        color = iconColor?.let { Color(it) },
                        text = if (iconColor == null) Str(R.string.automatic) else null,
                        clearLabel = Str(R.string.clear),
                    ) {
                        iconColor = it?.toArgb()
                    }
                }
            }

            // LED
            LabeledRow(
                R.string.led,
            ) {
                SwitchBox(led) { isTurningOn ->
                    led = isTurningOn
                }
            }

            // LED Color
            AnimatedVisibleV(led) {
                LabeledRow(
                    R.string.led_color,
                    helpTooltip = Str(R.string.help_led_color)
                ) {
                    RowVCenterSpaced(6) {
                        ColorPickerButton(
                            color = Color(ledColor),
                            enabled = isCreatingNewChannel,
                        ) {
                            it?.let {
                                ledColor = it.toArgb()
                            }
                        }

                        if (!isCreatingNewChannel) {
                            ResIcon(R.drawable.ic_note, modifier = M.clickable {
                                openChannelSettings(ctx, chId)
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelPicker(
    selectedChannelId: String,
    onSelected: Lambda2<Int, Channel>,
) {
    val ctx = LocalContext.current

    val selectedIndex = remember(selectedChannelId) {
        derivedStateOf {
            G.notificationChannels.indexOfFirst {
                selectedChannelId == it.channelId
            }
        }
    }

    var editingChannel by remember { mutableStateOf<Channel?>(null) }
    val editTrigger = remember { mutableStateOf(false) }

    val items = remember {
        derivedStateOf {
            G.notificationChannels.mapIndexed { index, channel ->
                LabelItem(
                    label = channel.displayName(ctx),
                    leadingIcon = {
                        ChannelIcons(channel.importance, channel.mute)
                    },
                    onClick = {
                        onSelected(index, channel)
                    },
                    onLongClick = {
                        editingChannel = channel
                        editTrigger.value = true
                    }
                )
            } +
                    // Customize
                    LabelItem(
                        label = ctx.getString(R.string.customize),
                        leadingIcon = { GreyIcon18(R.drawable.ic_note) },
                        dismissOnClick = false,
                        tooltip = ctx.getString(R.string.help_create_notification_channel)
                    ) {
                        editingChannel = null
                        editTrigger.value = true
                    }
        }
    }

    EditChannelDialog(
        editTrigger = editTrigger,
        initChannel = editingChannel ?: Channel(),
    )
    ComboBox(
        items = items.value,
        selected = selectedIndex.value,

        onLongClick = {
            if (selectedIndex.value in G.notificationChannels.indices) {
                editingChannel = G.notificationChannels[selectedIndex.value]
                editTrigger.value = true
            }
        }
    )
}


@Composable
fun Notification() {
    val ctx = LocalContext.current
    val C = G.palette

    val spf = spf.Notification(ctx)

    // Call
    var spamCallChannelId by remember { mutableStateOf(spf.spamCallChannelId) }

    // SMS
    var spamSmsChannelId by remember { mutableStateOf(spf.spamSmsChannelId) }
    var validSmsChannelId by remember { mutableStateOf(spf.validSmsChannelId) }
    var activeSmsChatChannelId by remember { mutableStateOf(spf.smsChatChannelId) }

    // Call config popup
    val configTrigger = rememberSaveable { mutableStateOf(false) }

    PopupDialog(
        trigger = configTrigger,
        content = {
            Column {
                // 1. Calls Blocked
                if (G.callEnabled.value) {
                    LabeledRow(
                        R.string.calls_blocked,
                    ) {
                        ChannelPicker(
                            spamCallChannelId,
                        ) { _, newCh ->
                            spf.spamCallChannelId = newCh.channelId
                            spamCallChannelId = newCh.channelId
                        }
                    }
                }

                if (G.smsEnabled.value || G.notificationScreeningEnabled.value) {
                    // 2. SMS/MMS Allowed
                    LabeledRow(
                        R.string.sms_mms_allowed,
                    ) {
                        ChannelPicker(
                            validSmsChannelId,
                        ) { _, ch ->
                            spf.validSmsChannelId = ch.channelId
                            validSmsChannelId = ch.channelId
                        }
                    }
                    // 3. SMS/MMS Blocked
                    LabeledRow(
                        R.string.sms_mms_blocked,
                    ) {
                        ChannelPicker(
                            spamSmsChannelId,
                        ) { _, ch ->
                            spf.spamSmsChannelId = ch.channelId
                            spamSmsChannelId = ch.channelId
                        }
                    }
                    // 4. Active Chat
                    LabeledRow(
                        R.string.active_chat,
                        helpTooltip = Str(R.string.help_active_chat),
                    ) {
                        ChannelPicker(
                            activeSmsChatChannelId,
                        ) { _, ch ->
                            spf.smsChatChannelId = ch.channelId
                            activeSmsChatChannelId = ch.channelId
                        }
                    }
                }

            }
        }
    )

    LabeledRow(
        R.string.notifications,
        helpTooltip = Str(R.string.help_notification),
        content = {
            RowVCenterSpaced(4) {
                // Call Button
                if (G.callEnabled.value) {
                    val ch = G.notificationChannels.find {
                        it.channelId == spamCallChannelId
                    }
                    FooterButton(
                        footerIconId = R.drawable.ic_call,
                        footerSize = 10,
                        footerOffset = Pair(-2, -2),
                        icon = {
                            ChannelIcons(ch?.importance, ch?.mute, color = C.error)
                        }
                    ) {
                        configTrigger.value = true
                    }
                }

                // SMS Button
                if (G.smsEnabled.value || G.notificationScreeningEnabled.value) {
                    val chValid = G.notificationChannels.find {
                        it.channelId == validSmsChannelId
                    }
                    val chSpam = G.notificationChannels.find {
                        it.channelId == spamSmsChannelId
                    }

                    FooterButton(
                        footerIconId = R.drawable.ic_sms,
                        footerSize = 8,
                        footerOffset = Pair(-3, -2),
                        icon = {
                            RowVCenterSpaced(4) {
                                // Valid SMS icon
                                ChannelIcons(chValid?.importance, chValid?.mute)
                                // Vertical Divider
                                VerticalDivider(thickness = 1.dp, color = C.disabled)
                                // Spam SMS icon
                                ChannelIcons(chSpam?.importance, chSpam?.mute, color = C.error)
                            }
                        }
                    ) {
                        configTrigger.value = true
                    }
                }
            }
        }
    )
}

// "Basic Rules Alerts": collapsible section, one row per app currently selected for
// Notification Screening, each with its own Vibration/Flashlight/Wake-Screen toggle icons
// and a ringtone button. Independent from Notification() ("Basic Rules Notifications")
// above, since notifications and alerts serve different purposes (see help text).
@Composable
fun Alerts() {
    val ctx = LocalContext.current
    val C = G.palette

    if (!G.notificationScreeningEnabled.value) {
        return
    }

    var collapsed by rememberSaveable { mutableStateOf(true) }

    val screenedPkgs = remember { spf.AppNotifications(ctx).getList() }

    LabeledRow(
        R.string.alerts,
        helpTooltip = Str(R.string.help_notification_allowed_alert),
        isCollapsed = collapsed,
        toggleCollapse = { collapsed = !collapsed },
        content = {}
    )
    AnimatedVisibleV(!collapsed) {
        if (screenedPkgs.isEmpty()) {
            GreyText(Str(R.string.no_screened_apps))
        } else {
            Column(modifier = M.padding(start = 16.dp)) {
                screenedPkgs.forEachIndexed { index, pkgName ->
                    AppAlertConfigRow(pkgName)

                    if (index < screenedPkgs.lastIndex) {
                        HorizontalDivider(thickness = 1.dp, color = C.dialogBg.slightDiff())
                    }
                }
            }
        }
    }
}

// Reusable Vibration/Flashlight/Wake-Screen toggle icons + ringtone button row.
// Used both for the per-app config (Basic Rules Notification > Notification Allowed Alert)
// and the per-rule config (Number/Text Rule edit dialog > Alert).
@Composable
fun AlertConfigControls(
    initConfig: AppAlertConfig,
    onSave: (AppAlertConfig) -> Unit,
) {
    val ctx = LocalContext.current
    val C = G.palette

    var ringtone by remember { mutableStateOf(initConfig.ringtone) }
    var ringtoneName by remember(ringtone) {
        mutableStateOf(
            if (ringtone.isEmpty()) "" else RingtoneUtil.getName(ctx, ringtone.toUri())
        )
    }
    var vibrate by remember { mutableStateOf(initConfig.vibrate) }
    var flashlight by remember { mutableStateOf(initConfig.flashlight) }
    var wakeScreen by remember { mutableStateOf(initConfig.wakeScreen) }

    fun save() {
        onSave(
            initConfig.copy(
                ringtone = ringtone,
                vibrate = vibrate,
                flashlight = flashlight,
                wakeScreen = wakeScreen,
            )
        )
    }

    val soundTrigger = remember { mutableStateOf(false) }
    RingtonePicker(soundTrigger) { uri, name ->
        if (uri != null) {
            ringtone = uri
            ringtoneName = name ?: ""
            save()
        }
    }

    RowVCenterSpaced(10) {
        // Vibration
        ResIcon(
            R.drawable.ic_vibration,
            modifier = M
                .size(20.dp)
                .clickable { vibrate = !vibrate; save() },
            color = if (vibrate) C.teal200 else C.disabled,
        )
        // Flashlight
        ResIcon(
            R.drawable.ic_flashlight,
            modifier = M
                .size(20.dp)
                .clickable { flashlight = !flashlight; save() },
            color = if (flashlight) C.teal200 else C.disabled,
        )
        // Wake Screen
        ResIcon(
            R.drawable.ic_wake_screen,
            modifier = M
                .size(20.dp)
                .clickable { wakeScreen = !wakeScreen; save() },
            color = if (wakeScreen) C.teal200 else C.disabled,
        )

        RowVCenter(modifier = M.weight(1f), horizontalArrangement = Arrangement.End) {
            ResIcon(
                R.drawable.ic_music,
                modifier = M.size(20.dp),
                color = if (ringtone.isEmpty()) C.disabled else C.teal200,
            )
            Spacer(modifier = M.width(4.dp))
            GreyButton(
                if (ringtone.isEmpty()) Str(R.string.none) else ringtoneName,
            ) {
                soundTrigger.value = true
            }
        }
    }
}

// A single app's row: label + AlertConfigControls, backed by the per-app AllowedNotificationAlerts store.
@Composable
fun AppAlertConfigRow(
    pkgName: String,
) {
    val ctx = LocalContext.current
    val C = G.palette
    val spf = spf.AllowedNotificationAlerts(ctx)

    val initConfig = remember { spf.find(pkgName) ?: AppAlertConfig(pkgName = pkgName) }

    Column(modifier = M.padding(vertical = 4.dp)) {
        RowVCenterSpaced(10) {
            AppIcon(pkgName)
            Text(
                AppInfo.fromPackage(ctx, pkgName).label,
                color = C.infoBlue,
                modifier = M.weight(1f),
            )
        }
        Column(modifier = M.padding(start = 30.dp, top = 4.dp)) {
            AlertConfigControls(initConfig = initConfig, onSave = { spf.save(it) })
        }
    }
}

// Per-rule Alert config popup: same controls as the per-app one, but backed by a single
// RegexRule's own `alertConfigJson` field instead of the app-level store. Empty JSON means
// "use the app-level default", so a "Reset to Default" option is offered.
@Composable
fun RuleAlertConfigDialog(
    trigger: MutableState<Boolean>,
    alertConfigJson: MutableState<String>,
) {
    if (!trigger.value) {
        return
    }

    val C = G.palette

    val initConfig = remember {
        alertConfigJson.value
            .takeIf { it.isNotEmpty() }
            ?.let { runCatching { Json.decodeFromString<AppAlertConfig>(it) }.getOrNull() }
            ?: AppAlertConfig(pkgName = "")
    }

    PopupDialog(
        trigger = trigger,
        buttons = {
            StrokeButton(
                label = Str(R.string.reset_to_default),
                color = C.textGrey,
            ) {
                alertConfigJson.value = ""
                trigger.value = false
            }
        },
        content = {
            AlertConfigControls(
                initConfig = initConfig,
                onSave = { alertConfigJson.value = Json.encodeToString(it) },
            )
        }
    )
}