package spam.blocker.ui.setting

import android.view.ViewTreeObserver
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spam.blocker.Events
import spam.blocker.G
import spam.blocker.R
import spam.blocker.service.checker.IChecker
import spam.blocker.ui.M
import spam.blocker.ui.setting.api.ApiHeader
import spam.blocker.ui.setting.api.ApiList
import spam.blocker.ui.setting.api.ApiQueryPresets
import spam.blocker.ui.setting.api.ApiReportPresets
import spam.blocker.ui.setting.bot.BotHeader
import spam.blocker.ui.setting.bot.BotList
import spam.blocker.ui.setting.misc.About
import spam.blocker.ui.setting.misc.BackupRestore
import spam.blocker.ui.setting.misc.FAQ
import spam.blocker.ui.setting.misc.Language
import spam.blocker.ui.setting.misc.Theme
import spam.blocker.ui.setting.quick.Alerts
import spam.blocker.ui.setting.quick.Answered
import spam.blocker.ui.setting.quick.BlockType
import spam.blocker.ui.setting.quick.CallerID
import spam.blocker.ui.setting.quick.Contacts
import spam.blocker.ui.setting.quick.Dialed
import spam.blocker.ui.setting.quick.EmergencySituation
import spam.blocker.ui.setting.quick.MeetingMode
import spam.blocker.ui.setting.quick.Notification
import spam.blocker.ui.setting.quick.OffTime
import spam.blocker.ui.setting.quick.RecentApps
import spam.blocker.ui.setting.quick.RepeatedCall
import spam.blocker.ui.setting.quick.SpamDB
import spam.blocker.ui.setting.quick.Stir
import spam.blocker.ui.setting.regex.PushAlertHeader
import spam.blocker.ui.setting.regex.PushAlertList
import spam.blocker.ui.setting.regex.PushAlertViewModel
import spam.blocker.ui.setting.regex.RegexHeader
import spam.blocker.ui.setting.regex.RegexHeaderMenuButton
import spam.blocker.ui.setting.regex.RegexList
import spam.blocker.ui.setting.regex.RegexViewModel
import spam.blocker.ui.setting.regex.SmsAlert
import spam.blocker.ui.setting.regex.SmsBomb
import spam.blocker.ui.theme.White
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.BalloonQuestionMark
import spam.blocker.ui.widgets.Fab
import spam.blocker.ui.widgets.FabWrapper
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.NormalColumnScrollbar
import spam.blocker.ui.widgets.ResIcon16
import spam.blocker.ui.widgets.RowVCenter
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.SearchBox
import spam.blocker.ui.widgets.AllowBlockCountBadge
import spam.blocker.ui.widgets.Section
import spam.blocker.ui.widgets.Str
import spam.blocker.util.Lambda
import spam.blocker.util.Util.isFreshInstall
import spam.blocker.util.spf

const val SettingRowMinHeight = 40

@Composable
fun SettingScreen() {
    val C = G.palette
    val ctx = LocalContext.current

    val testingTrigger = rememberSaveable { mutableStateOf(false) }
    TestDialog(testingTrigger)

    // Hide FAB when scrolled to the bottom
    val scrollState = rememberScrollState()
    val bottomReached by remember {
        derivedStateOf {
            scrollState.maxValue > 0 && scrollState.value == scrollState.maxValue
        }
    }

    val priorityConflicts = remember { mutableStateListOf<IChecker>() }

    fun checkConflicts() {
        priorityConflicts.apply {
            clear()
            addAll(detectConflictCheckers(ctx))
        }
    }
    // Detect priority conflicts when recomposed (e.g. on tab switching)
    LaunchedEffect(Unit) {
        checkConflicts()
    }

    // Detect priority conflicts when this screen regains focus (e.g., after closing a popup)
    val view = LocalView.current
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) {
                checkConflicts()
            }
        }

        val observer = view.viewTreeObserver
        observer.addOnWindowFocusChangeListener(listener)

        onDispose {
            if (observer.isAlive) {
                observer.removeOnWindowFocusChangeListener(listener)
            }
        }
    }

    // Detect conflicts when clicking the "Test" button, show a popup warning if there are conflicts.
    val conflictTrigger = remember { mutableStateOf(false) }
    PriorityConflictDialog(trigger = conflictTrigger, conflicts = priorityConflicts)

    val sectionSpf = spf.SectionCollapse(ctx)

    // Show text "Testing" on the testing tube icon, and hide this text once it's clicked.
    val spf = spf.Global(ctx)
    var alsoShowTestButtonLabel by remember {
        mutableStateOf(
            isFreshInstall(ctx) && !spf.isTestIconClicked
        )
    }
    FabWrapper(
        fabRow = { positionModifier ->
            Fab(
                visible = !bottomReached,
                text = if (alsoShowTestButtonLabel) Str(R.string.title_rule_testing) else null,
                iconId = R.drawable.ic_tube,
                iconColor = White,
                bgColor = if (priorityConflicts.isEmpty()) C.teal200 else C.warning,
                modifier = positionModifier
            ) {
                checkConflicts()
                if (priorityConflicts.isEmpty()) {
                    testingTrigger.value = true

                    spf.isTestIconClicked = true
                    alsoShowTestButtonLabel = false
                } else {
                    conflictTrigger.value = true
                }
            }
        }
    ) {

        NormalColumnScrollbar(state = scrollState) {
            Column(
                modifier = M
                    .verticalScroll(scrollState)
                    .padding(top = 8.dp)
            ) {
                // global
                GloballyEnabled()

                // basic rules
                var quickSettingsCollapsed by remember { mutableStateOf(sectionSpf.isQuickSettingsCollapsed) }
                Section(
                    title = Str(R.string.basic_rules),
                    horizontalPadding = 8,
                    collapsed = quickSettingsCollapsed,
                    onToggleCollapse = {
                        quickSettingsCollapsed = !quickSettingsCollapsed
                        sectionSpf.isQuickSettingsCollapsed = quickSettingsCollapsed
                    },
                    headerTrailing = { BasicRulesStatusIcons() },
                ) {
                    Column {
                        Contacts()
                        Stir()
                        SpamDB()
                        RepeatedCall()
                        Dialed()
                        Answered()
                        OffTime()
                        EmergencySituation()
                        RecentApps()
                        MeetingMode()
                    }
                }

                // Number Rules
                var numberRulesCollapsed by remember { mutableStateOf(sectionSpf.isNumberRulesCollapsed) }
                LaunchedEffect(true) { G.NumberRuleVM.reloadDbAndOptions(ctx) }
                Section(
                    title = Str(R.string.label_number_rules),
                    horizontalPadding = 8,
                    collapsed = numberRulesCollapsed,
                    onToggleCollapse = {
                        numberRulesCollapsed = !numberRulesCollapsed
                        sectionSpf.isNumberRulesCollapsed = numberRulesCollapsed
                    },
                    badge = {
                        AllowBlockCountBadge(
                            allowCount = G.NumberRuleVM.rules.count { !it.isBlacklist },
                            blockCount = G.NumberRuleVM.rules.count { it.isBlacklist },
                        )
                    },
                ) {
                    Column {
                        SearchBox(G.NumberRuleVM.searchEnabled, G.NumberRuleVM.filter) {
                            G.NumberRuleVM.reloadDb(ctx)
                        }
                        RegexList(G.NumberRuleVM)
                        RowVCenter(
                            modifier = M.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            RegexHeaderMenuButton(G.NumberRuleVM)
                        }
                    }
                }

                // Text Rules
                var textRulesCollapsed by remember { mutableStateOf(sectionSpf.isTextRulesCollapsed) }
                LaunchedEffect(true) { G.ContentRuleVM.reloadDbAndOptions(ctx) }
                Section(
                    title = Str(R.string.label_content_rules),
                    horizontalPadding = 8,
                    collapsed = textRulesCollapsed,
                    onToggleCollapse = {
                        textRulesCollapsed = !textRulesCollapsed
                        sectionSpf.isTextRulesCollapsed = textRulesCollapsed
                    },
                    badge = {
                        AllowBlockCountBadge(
                            allowCount = G.ContentRuleVM.rules.count { !it.isBlacklist },
                            blockCount = G.ContentRuleVM.rules.count { it.isBlacklist },
                        )
                    },
                ) {
                    Column {
                        SearchBox(G.ContentRuleVM.searchEnabled, G.ContentRuleVM.filter) {
                            G.ContentRuleVM.reloadDb(ctx)
                        }
                        RegexList(G.ContentRuleVM)
                        RowVCenter(
                            modifier = M.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            RegexHeaderMenuButton(G.ContentRuleVM)
                        }
                    }
                }

                // Advanced Rules: QuickCopy, Push Alert, SMS Alert, SMS Bomb
                var advancedRulesCollapsed by remember { mutableStateOf(sectionSpf.isAdvancedRulesCollapsed) }
                Section(
                    title = Str(R.string.advanced_rules),
                    horizontalPadding = 8,
                    collapsed = advancedRulesCollapsed,
                    onToggleCollapse = {
                        advancedRulesCollapsed = !advancedRulesCollapsed
                        sectionSpf.isAdvancedRulesCollapsed = advancedRulesCollapsed
                    },
                ) {
                    Column {
                        // QuickCopy
                        LaunchedEffect(true) { G.QuickCopyRuleVM.reloadDbAndOptions(ctx) }
                        RegexHeader(G.QuickCopyRuleVM)
                        AnimatedVisibleV(!G.QuickCopyRuleVM.listCollapsed.value) {
                            Column {
                                SearchBox(G.QuickCopyRuleVM.searchEnabled, G.QuickCopyRuleVM.filter) {
                                    G.QuickCopyRuleVM.reloadDb(ctx)
                                }
                                RegexList(G.QuickCopyRuleVM)
                            }
                        }

                        // Push Alert
                        LaunchedEffect(true) { PushAlertViewModel.reloadDbAndOptions(ctx) }
                        PushAlertHeader()
                        AnimatedVisibleV(!PushAlertViewModel.listCollapsed.value) {
                            PushAlertList()
                        }

                        // SMS Alert
                        SmsAlert()

                        // SMS Bomb
                        SmsBomb()
                    }
                }

                // Actions: Block Type, Action Notification, Caller ID
                var actionsCollapsed by remember { mutableStateOf(sectionSpf.isActionsCollapsed) }
                Section(
                    title = Str(R.string.actions),
                    horizontalPadding = 8,
                    collapsed = actionsCollapsed,
                    onToggleCollapse = {
                        actionsCollapsed = !actionsCollapsed
                        sectionSpf.isActionsCollapsed = actionsCollapsed
                    },
                ) {
                    Column {
                        BlockType()
                        CallerID()
                        Notification()
                        Alerts()
                    }
                }

                // Integrations: Instant Query, Report Number, Automation
                var integrationsCollapsed by remember { mutableStateOf(sectionSpf.isIntegrationsCollapsed) }
                LaunchedEffect(true) {
                    G.apiQueryVM.reloadDb(ctx)
                    G.apiReportVM.reloadDb(ctx)
                    G.botVM.reload(ctx)
                }
                Section(
                    title = Str(R.string.integrations),
                    horizontalPadding = 8,
                    collapsed = integrationsCollapsed,
                    onToggleCollapse = {
                        integrationsCollapsed = !integrationsCollapsed
                        sectionSpf.isIntegrationsCollapsed = integrationsCollapsed
                    },
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        // Instant Query
                        ApiHeader(G.apiQueryVM, ApiQueryPresets)
                        AnimatedVisibleV(!G.apiQueryVM.listCollapsed.value) {
                            ApiList(G.apiQueryVM)
                        }

                        // Report Number
                        ApiHeader(G.apiReportVM, ApiReportPresets)
                        AnimatedVisibleV(!G.apiReportVM.listCollapsed.value) {
                            ApiList(G.apiReportVM)
                        }

                        // Automation
                        BotHeader(G.botVM)
                        AnimatedVisibleV(!G.botVM.listCollapsed.value) {
                            BotList()
                        }
                    }
                }

                // Miscellaneous
                var miscellaneousCollapsed by remember { mutableStateOf(sectionSpf.isMiscellaneousCollapsed) }
                Section(
                    title = Str(R.string.miscellaneous),
                    horizontalPadding = 8,
                    collapsed = miscellaneousCollapsed,
                    onToggleCollapse = {
                        miscellaneousCollapsed = !miscellaneousCollapsed
                        sectionSpf.isMiscellaneousCollapsed = miscellaneousCollapsed
                    },
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        Language()
                        Theme()
                        BackupRestore()
                        SettingRow {
                            RowVCenterSpaced(8) {
                                FAQ()
                                About()
                            }
                        }
                    }
                }
            }
        }
    }
}


// Borderless icons mirroring each Basic Rule's own enabled-state indicator,
// shown right-aligned in the "Basic Rules" section header.
@Composable
fun BasicRulesStatusIcons() {
    val ctx = LocalContext.current
    val C = G.palette

    var refreshTick by remember { mutableStateOf(0) }
    Events.basicRuleUpdated.Listen { refreshTick++ }

    key(refreshTick) {
    RowVCenterSpaced(8) {
        val contactSpf = spf.Contact(ctx)
        if (contactSpf.isEnabled) {
            ResIcon16(R.drawable.ic_contact_square, color = C.textGrey)
        }
        if (spf.Stir(ctx).isEnabled) {
            ResIcon16(R.drawable.ic_incognito, color = C.error)
        }
        if (spf.SpamDB(ctx).isEnabled) {
            ResIcon16(R.drawable.ic_database, color = C.error)
        }
        if (spf.RepeatedCall(ctx).isEnabled) {
            ResIcon16(R.drawable.ic_repeat, color = C.textGrey)
        }
        if (spf.Dialed(ctx).isEnabled) {
            ResIcon16(R.drawable.ic_phone_forwarded, color = C.textGrey)
        }
        if (spf.Answered(ctx).isEnabled) {
            ResIcon16(R.drawable.ic_phone_callback, color = C.textGrey)
        }
        if (spf.OffTime(ctx).isEnabled) {
            ResIcon16(R.drawable.ic_time_slot, color = C.textGrey)
        }
        if (spf.EmergencySituation(ctx).isEnabled) {
            ResIcon16(R.drawable.ic_warning_tintable, color = C.textGrey)
        }
        if (spf.RecentApps(ctx).getList().isNotEmpty()) {
            ResIcon16(R.drawable.ic_duration, color = C.textGrey)
        }
        if (spf.MeetingMode(ctx).getList().isNotEmpty()) {
            ResIcon16(R.drawable.ic_videocam, color = C.error)
        }
    }
    }
}

@Composable
fun SettingRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    RowVCenter(
        modifier = modifier
            .heightIn(min = SettingRowMinHeight.dp)
            .padding(vertical = 2.dp)
    ) {
        content()
    }
}

@Composable
fun SettingLabel(
    labelId: Int,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    Text(
        text = stringResource(id = labelId),
        modifier = modifier,
        maxLines = 1,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = color ?: G.palette.infoBlue,
    )
}

// This is used in SettingScreen
@Composable
fun LabeledRow(
    labelId: Int?,
    modifier: Modifier = Modifier,
    color: Color? = null,

    // Padding indentation for labels in Section Group
    paddingHorizontal: Int = 0,

    // Show the question icon or not
    helpTooltip: String? = null,

    // Show a down arrow to indicate the content below is collapsed
    // - null: it's not collapsable
    // - true/false: if it's collapsed or not
    isCollapsed: Boolean? = null,
    toggleCollapse: Lambda? = null,

    // Items on the right side, e.g.: "New" button
    content: @Composable RowScope.() -> Unit,
) {
    // Rotating chevron: points right when collapsed, down when expanded (standard convention).
    val arrowRotation by animateFloatAsState(if (isCollapsed == true) -90f else 0f)

    SettingRow(
        modifier = modifier
            .clickable {
                // 1. expand/collapse
                if (toggleCollapse != null)
                    toggleCollapse()
            }
            .padding(horizontal = paddingHorizontal.dp),
    ) {
        RowVCenterSpaced(
            space = 2,
            modifier = M.fillMaxWidth()
        ) {
            // label
            if (labelId != null) {
                SettingLabel(
                    labelId,
                    color = color
                )
            }

            // collapsed/expanded indicator
            if (isCollapsed != null) {
                GreyIcon16(
                    iconId = R.drawable.ic_dropdown_arrow,
                    modifier = M.rotate(arrowRotation),
                )
            }

            // balloon tooltip
            helpTooltip?.let {
                BalloonQuestionMark(it)
            }

            // rest content
            RowVCenter(
                modifier = M.weight(1f),
                horizontalArrangement = Arrangement.End,
            ) {
                content()
            }
        }
    }
}

