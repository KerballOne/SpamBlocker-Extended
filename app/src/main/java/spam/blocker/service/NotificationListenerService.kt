package spam.blocker.service

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import spam.blocker.db.NumberRegexTable
import spam.blocker.db.ContentRegexTable
import spam.blocker.db.PushAlertRecord
import spam.blocker.db.PushAlertTable
import spam.blocker.db.RegexRule
import spam.blocker.def.Def
import spam.blocker.service.checker.ByRegexRule
import spam.blocker.service.checker.Checker
import spam.blocker.service.checker.ICheckResult
import spam.blocker.service.checker.IChecker
import spam.blocker.service.checker.NotificationField
import spam.blocker.service.checker.NumberCandidate
import spam.blocker.service.checker.numberRuleToChecker
import spam.blocker.util.CountryCode
import spam.blocker.util.Permission
import spam.blocker.util.PermissiveJson
import spam.blocker.util.SaveableLogger
import spam.blocker.util.Util
import spam.blocker.util.getSaveableOutput
import spam.blocker.util.spf.AppAlertConfig
import spam.blocker.util.regexFind
import spam.blocker.util.regexMatches
import spam.blocker.util.regexMatchesNumber
import spam.blocker.util.spf

// A generic phone-number-shaped substring, e.g. "555-123-4567", "(555) 123-4567", "+15551234567".
// Used to find number candidates embedded anywhere within a notification's title,
//  which are then normalized (stripping formatting) and matched against each rule's
//  own pattern via `regexMatchesNumber`, the same way a real call/SMS number is matched.
private val phoneNumberCandidateRegex = Regex("""\+?[\s().-]*(?:\d[\s().-]*){3,15}""")

fun findNumberCandidates(title: String): List<String> {
    return phoneNumberCandidateRegex.findAll(title)
        // The regex's leading `[\s().-]*` can capture stray whitespace/punctuation
        //  before the first digit (e.g. a leading space from "from 5551234567"),
        //  which would otherwise reach Basic Rule checkers (e.g. Spam Database) as
        //  part of `rawNumber` and break their exact-match lookups. The old Number
        //  Rule gate never hit this because `regexMatchesNumber` normalizes first.
        // A leading "(" is kept when it opens an area code, e.g. "(555) 123-4567",
        //  otherwise stripping it would leave the unbalanced "555) 123-4567".
        .map { match ->
            val c = match.value.trim()
            val opensAreaCode = c.startsWith('(') && c.contains(')')
            val start = if (opensAreaCode) c else c.trimStart('(', ')', '-', '.')
            val closesAreaCode = start.endsWith(')') && start.contains('(')
            val end = if (closesAreaCode) start else start.trimEnd('(', ')', '-', '.')
            // Parentheses around the whole number, e.g. "(5551234567)", aren't part of it.
            if (end.startsWith('(') && end.endsWith(')') && end.count { it == '(' } == 1)
                end.substring(1, end.length - 1).trim()
            else end
        }
        .filter { candidate -> candidate.count { it.isDigit() } in 3..15 }
        .toList()
}

// Whether a candidate is long enough to be a phone number rather than e.g. a short code,
//  a verification code or an order number.
fun isPhoneLength(candidate: String): Boolean {
    return candidate.count { it.isDigit() } >= MIN_LOCAL_NUMBER_LENGTH
}

// Basic Rule checkers (e.g. Spam Database) match `rawNumber` as an exact string
//  against however the number happens to be stored, with only a narrow set of
//  fallback normalizations. A voicemail app's notification text commonly omits the
//  country code (e.g. "New voicemail from 555-123-4567"), which then can't match a
//  database entry saved with one (e.g. "+1 (555) 123-4567"), even after formatting
//  is stripped. Widen each candidate with a country-code-prepended variant so both
//  forms get tried, without changing how Basic Rule checkers themselves look numbers
//  up (keeps this fix scoped to Voicemail Notification's own candidate list).
// Shortest plausible local number length (without country code) across common numbering
//  plans — used only to avoid mistaking a short/malformed candidate for one that already
//  has a country-code prefix merely because its leading digit(s) happen to match.
private const val MIN_LOCAL_NUMBER_LENGTH = 7

private fun withCountryCodeVariants(ctx: Context, candidates: List<String>): List<String> {
    val cc = CountryCode.current(ctx) ?: return candidates
    val ccStr = cc.toString()
    return candidates.flatMap { candidate ->
        val cleaned = Util.clearNumber(candidate)
        // Don't prepend the country code again if the cleaned number already starts with it
        //  AND is long enough to plausibly be [country code][local number] (e.g. candidate
        //  "+15125551234" cleans to "15125551234", 11 digits — already has the "1"; prepending
        //  again would produce the nonsensical "115125551234"). The length check avoids treating
        //  a short/malformed candidate as already-prefixed just because its leading digit(s)
        //  happen to coincide with the country code (e.g. a stray "1..." local-only fragment).
        val alreadyHasCc = cleaned.startsWith(ccStr) &&
            cleaned.length >= ccStr.length + MIN_LOCAL_NUMBER_LENGTH
        if (alreadyHasCc) {
            listOf(candidate, cleaned)
        } else {
            listOf(candidate, "$cc$cleaned", "+$cc$cleaned")
        }
    }.distinct()
}

// The set of package names to screen (App Notifications), cached to avoid
//  reading SharedPref on every notification.
private var notificationScreeningPkgs: Set<String>? = null

// Rules with "Notification Title"/"Notification Body" enabled in their "Apply to",
//  cached to avoid DB access on every notification.
private var notifTitleNumberRules: List<RegexRule>? = null // Number Rules, checked against the title
private var notifBodyNumberRules: List<RegexRule>? = null // Number Rules, checked against the body
private var notifTitleContentRules: List<RegexRule>? = null // Content/Message Rules, checked against the title
private var notifBodyContentRules: List<RegexRule>? = null // Content/Message Rules, checked against the body

fun resetNotificationScreeningCache() {
    notificationScreeningPkgs = null
    notifTitleNumberRules = null
    notifBodyNumberRules = null
    notifTitleContentRules = null
    notifBodyContentRules = null
}

// Voicemail Notification: separate from the Number/Text-Rule gate above, this runs
//  candidate numbers extracted from selected apps' notifications through the FULL
//  Basic+Number+Text Rule pipeline (Checker.checkSms), not just a Number/Text-Rule
//  pre-check. See spf.VoicemailNotification and ui/setting/regex/VoicemailNotification.kt.
private var voicemailNotificationEnabled: Boolean? = null
private var voicemailNotificationPkgs: Set<String>? = null
private var voicemailNotificationApplyToTitle: Boolean? = null
private var voicemailNotificationApplyToBody: Boolean? = null
private var voicemailNotificationIncludeRegexRules: Boolean? = null
private var voicemailNotificationAllowIfCallAllowed: Boolean? = null

// Own dedup set, independent from `screenedNotifications` above, so this feature's
//  pass/no-match outcome doesn't get short-circuited by (or short-circuit) the
//  existing Number/Text-Rule gate's dedup bookkeeping for the same notification.
private var voicemailScreenedNotifications: MutableSet<String> = mutableSetOf()

fun resetVoicemailNotificationCache() {
    voicemailNotificationEnabled = null
    voicemailNotificationPkgs = null
    voicemailNotificationApplyToTitle = null
    voicemailNotificationApplyToBody = null
    voicemailNotificationIncludeRegexRules = null
    voicemailNotificationAllowIfCallAllowed = null
    // Config changed (e.g. toggled off/on, app list changed) — forget prior screening
    //  decisions so they don't silently stay skipped under the new configuration.
    voicemailScreenedNotifications.clear()
}

private fun ensureVoicemailNotificationCache(ctx: Context) {
    if (voicemailNotificationEnabled == null) {
        val spf = spf.VoicemailNotification(ctx)
        voicemailNotificationEnabled = spf.isEnabled
        voicemailNotificationPkgs = spf.getApps().toSet()
        voicemailNotificationApplyToTitle = spf.applyToTitle
        voicemailNotificationApplyToBody = spf.applyToBody
        voicemailNotificationIncludeRegexRules = spf.includeNumberTextRules
        voicemailNotificationAllowIfCallAllowed = spf.allowIfCallAllowed
    }
}

private fun ensureNotificationScreeningCache(ctx: Context) {
    if (notificationScreeningPkgs == null) {
        notificationScreeningPkgs = spf.AppNotifications(ctx).getList().toSet()
    }
    if (notifTitleNumberRules == null || notifBodyNumberRules == null) {
        val numberRules = NumberRegexTable().listAll(ctx)
        notifTitleNumberRules = numberRules.filter { it.isForNotifTitle() }
        notifBodyNumberRules = numberRules.filter { it.isForNotifBody() }
    }
    if (notifTitleContentRules == null || notifBodyContentRules == null) {
        val contentRules = ContentRegexTable().listAll(ctx)
        notifTitleContentRules = contentRules.filter { it.isForNotifTitle() }
        notifBodyContentRules = contentRules.filter { it.isForNotifBody() }
    }
}

// A set of "pkgName|title|text" already screened, to avoid re-screening the same
//  notification content multiple times. Some apps assign a new sbn.key/postTime
//  on every repost (e.g. an unrelated group/summary refresh), even though the
//  actual title+text content is unchanged, so identity is tracked by content instead.
private var screenedNotifications: MutableSet<String> = mutableSetOf()

// A map of:
//   <packageName, listOf PushAlertRecord>
// to prevent database access on every notification.
private var cache: HashMap<String, MutableList<PushAlertRecord>>? = null

fun resetPushAlertCache() {
    cache = null
}

private fun ensureCache(ctx: Context) {
    if (cache == null) {
//        logi("rebuild push alert cache")
        cache = hashMapOf()
        val records = PushAlertTable.listAll(ctx)
            .filter {
                it.enabled && it.isValid()
            }
        records.forEach {
            val list = cache!!.getOrPut(it.pkgName) { mutableListOf() }
            list.add(it)
        }
    }
}

/*
How it works:

Limitation by Android:

- In doze mode(screen is off), notifications will not be pushed to `NotificationListenerService`,
 they are cached and will be delivered to the service when the screen is turned on.
- On an incoming call, the OS invokes `CallScreeningService.onScreenCall()`, which "activates" the app process.
- This in turn activates the `NotificationListenerService`, the OS will push all cached notifications to it.
- The execution order of `CallScreeningService` and `NotificationListenerService` is unpredictable, e.g.:
  1. `NotificationListenerService` receives notification 1
  2. `CallScreeningService` executes  <----  this blocks the whole process, following steps will not get executed before this returns.
  3. `NotificationListenerService` receives notification 2, 3...

To solve this, make `CallScreeningService` asynchronous using a coroutine and delay it by 500ms,
  so that all notifications would've been processed during this period.
*/

// Whether a notification's Title names the sender by phone number: the whole Title, as
//  messaging apps show an unsaved sender, e.g. "+1 555-123-4567", or after "from" at its end,
//  e.g. "Voicemail from +1 555-123-4567". Rather than text containing a number, e.g.
//  "Order 12345678 shipped".
private val senderNumberTitleRegex = Regex("""(?:.*\bfrom\b\s*:?\s*)?(\+?[\d\s().-]+)""", RegexOption.IGNORE_CASE)
fun isSenderNumberTitle(text: String): Boolean {
    val number = senderNumberTitleRegex.matchEntire(text.trim())?.groupValues?.get(1) ?: return false
    return isPhoneLength(number)
}

// At most this many of the Body's numbers are checked, so a notification listing many
//  numbers can't hold up the screening (e.g. with Instant Query).
private const val MAX_BODY_NUMBERS = 2

// How Notification Screening checks one notification: the numbers (with the parts they're
//  in and the text checked along with them), and the rules.
// Shared by the screening and the Test screen, so a test does what the screening does.
class NotificationScreeningPlan(
    val candidates: List<NumberCandidate>,
    val checkers: List<IChecker>,
    // A Number/Text Rule matched, so all rules apply. Otherwise only the Basic Rules that
    //  can block apply, and only a block is acted on.
    val anyRuleMatches: Boolean,
)

// null: nothing applies, the notification is left alone.
// `senderNumber`: the Test screen's Phone Number, used as the Title's number.
fun planNotificationScreening(
    ctx: Context,
    title: String,
    text: String,
    isVoicemailApp: Boolean = false,
    senderNumber: String? = null,
): NotificationScreeningPlan? {
    ensureNotificationScreeningCache(ctx)

    // Extract phone-number-shaped substrings from the title/body (tolerating
    //  dashes/spaces/parens/+), then test each against every Number Rule using
    //  the same matching logic used for real numbers, which normalizes formatting
    //  per the rule's own Raw Number/Ignore Country Code flags before comparing.
    // A Number Rule can independently target the title and/or the body, e.g. a
    //  voicemail notification like "New voicemail from: 5551234567" has the
    //  number in the body, not the title.
    val sender = senderNumber?.takeIf { it.isNotEmpty() }
    val titleCandidates = sender?.let { listOf(it) } ?: findNumberCandidates(title)
    val bodyCandidates = findNumberCandidates(text)

    val matchedTitleNumber = notifTitleNumberRules?.firstNotNullOfOrNull { rule ->
        titleCandidates.firstOrNull { candidate ->
            rule.pattern.regexMatchesNumber(ctx, candidate, rule.patternFlags)
        }
    }
    val matchedBodyNumber = if (matchedTitleNumber != null) null else
        notifBodyNumberRules?.firstNotNullOfOrNull { rule ->
            bodyCandidates.firstOrNull { candidate ->
                rule.pattern.regexMatchesNumber(ctx, candidate, rule.patternFlags)
            }
        }

    // Or each Content/Message Rule (applies to Notification Title and/or Body)
    //  against the corresponding field. Since `Checker.checkSms` re-validates
    //  Content Rules with a full-string match against whatever `messageBody` it's
    //  given, the field that actually matched must be the one passed onward,
    //  otherwise the downstream re-check fails against the wrong field.
    val titleContentMatches = notifTitleContentRules
        ?.any { rule -> rule.pattern.regexFind(title, rule.patternFlags) != null } == true
    val bodyContentMatches = notifBodyContentRules
        ?.any { rule -> rule.pattern.regexFind(text, rule.patternFlags) != null } == true

    val anyRuleMatches = matchedTitleNumber != null || matchedBodyNumber != null ||
            titleContentMatches || bodyContentMatches

    // Basic Rules that can block (Contacts' Block non-contacts, Database) with the Title
    //  and/or Body "Apply to" options on: phone numbers found in those parts are checked,
    //  without needing a Number/Text Rule to match first.
    // Contacts' Allow alone can only allow, which is already the default, so it's skipped.
    // Non-Contacts only blocks a Title naming the sender by number, not a number inside
    //  other text (an order number, a date...).
    // For apps chosen in Voicemail Notification, that feature applies the Basic Rules
    //  itself (with its own Title/Body choice), so they're not applied here a second time.
    val titlePhoneNumber = titleCandidates.firstOrNull { isPhoneLength(it) }
    val titleIsNumber = sender != null || isSenderNumberTitle(title)
    val bodyPhoneNumbers = bodyCandidates.filter { isPhoneLength(it) }.distinct().take(MAX_BODY_NUMBERS)
    val contacts = spf.Contact(ctx)
    val spamDb = spf.SpamDB(ctx)
    val blocksNonContacts = contacts.isEnabled && contacts.isStrict && !isVoicemailApp
    val spamDbApplies = spamDb.isEnabled && !isVoicemailApp
    val basicRulesForTitle = titlePhoneNumber != null &&
            ((blocksNonContacts && contacts.blockForTitle && titleIsNumber) ||
                    (spamDbApplies && spamDb.forTitle))
    val basicRulesForBody = bodyPhoneNumbers.isNotEmpty() &&
            ((blocksNonContacts && contacts.blockForBody) || (spamDbApplies && spamDb.forBody))

    return when {
        anyRuleMatches -> {
            // All rules apply, decided by priority across the Title (its number, with
            //  its text) and the Body (each of its numbers, with its text), so e.g. a
            //  contact's number in the Title (Contacts Allow, 10) wins over a Number Rule
            //  blocking a number mentioned in the Body (0), and a rule for only one part
            //  only checks that part. Content Rules re-validate against the text of the
            //  part they apply to, so each part keeps its own text.
            // A Body without a number is still checked (by Text Rules) with the Title's
            //  number, or the raw title, as the number, but as the Title's number for the
            //  Basic and Number Rules' Title/Body options.
            // Contact/Non-Contact ignore a number that isn't phone-number-like.
            val titleNumber = matchedTitleNumber ?: titlePhoneNumber ?: title
            val bodyNumbers = (listOfNotNull(matchedBodyNumber) + bodyPhoneNumbers)
                .distinct().take(MAX_BODY_NUMBERS)
            val candidates = buildList {
                add(NumberCandidate(titleNumber, NotificationField.Title, title,
                    isLooseNumber = !titleIsNumber))
                if (bodyNumbers.isNotEmpty()) {
                    bodyNumbers.forEach { add(NumberCandidate(it, NotificationField.Body, text)) }
                } else if (text.isNotEmpty()) {
                    add(NumberCandidate(titleNumber, NotificationField.Body, text,
                        numberField = NotificationField.Title, isLooseNumber = !titleIsNumber))
                }
            }
            NotificationScreeningPlan(candidates, Checker.defaultSmsCheckers(ctx), true)
        }
        basicRulesForTitle || basicRulesForBody -> {
            // Only those Basic Rules apply, not every Basic Rule (e.g. Off Time or
            //  Instant Query on every notification). The title's number (the sender,
            //  in most apps) is checked first, then the body's, if their options are on.
            val candidates = buildList {
                titlePhoneNumber?.let {
                    add(NumberCandidate(it, NotificationField.Title, title, isLooseNumber = !titleIsNumber))
                }
                if (basicRulesForBody) {
                    bodyPhoneNumbers.forEach { add(NumberCandidate(it, NotificationField.Body, text)) }
                }
            }
            NotificationScreeningPlan(candidates, Checker.basicNotificationCheckers(ctx), false)
        }
        else -> null
    }
}

// Checks all candidate numbers found in a notification together (with the part each was found
//  in, see CheckContext.notificationField), so the notification is decided by rule priority
//  across all of them, and logged to history once with the deciding number.
private fun checkNumberCandidates(
    ctx: Context,
    candidates: List<Pair<String, NotificationField?>>,
    messageBody: String,
    checkers: List<IChecker>,
): Pair<String, Triple<ICheckResult, String?, Boolean>> {
    val (candidate, check) = Checker.checkNotificationNumbers(
        ctx, candidates.map { (number, field) -> NumberCandidate(number, field, messageBody) },
        logger = SaveableLogger(), checkers = checkers,
    )
    return candidate.number to check
}

class NotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val pkgName = sbn.packageName
        val postTime = sbn.postTime

        val extras = sbn.notification.extras
        if (extras == null)
            return

        val title = extras.getString("android.title", "")
        val text = extras.getString("android.text", "")

        // Notification Screening: works independently of Push Alert and of the SMS switch,
        //  only requires Notification Access permission (already granted for this service to run at all).
        val spfGlobal = spf.Global(this)
        val contentKey = "$pkgName|$title|$text"
        val alreadyScreened = screenedNotifications.contains(contentKey)

        // A group summary (e.g. Google Messages' "2 new messages") only repeats its group's
        //  own notifications, which are screened individually. Dismissing it would also
        //  dismiss every notification in the group, including allowed ones.
        val isGroupSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0

        ensureNotificationScreeningCache(this)

        // The Notification Screening result for this notification, if it was screened (and
        //  logged to history) by it, so Voicemail Notification doesn't log it a second time.
        var screeningResult: ICheckResult? = null

        if (spfGlobal.isNotificationScreeningEnabled &&
            Permission.notificationAccess.isGranted &&
            notificationScreeningPkgs?.contains(pkgName) == true &&
            !alreadyScreened &&
            !isGroupSummary
        ) {
            ensureVoicemailNotificationCache(this)
            val isVoicemailApp = voicemailNotificationEnabled == true &&
                    voicemailNotificationPkgs?.contains(pkgName) == true
            val plan = planNotificationScreening(this, title, text, isVoicemailApp)

            if (plan != null) {
                screenedNotifications.add(contentKey)

                val (decisive, check) = Checker.checkNotificationNumbers(
                    this, plan.candidates, logger = SaveableLogger(), checkers = plan.checkers,
                )
                // With only the Basic Rules, a notification they let through is left alone,
                //  without a history record: only a block is acted on.
                if (plan.anyRuleMatches || check.first.shouldBlock()) {
                    val result = SmsReceiver.processSms(
                        ctx = this,
                        rawNumber = decisive.number,
                        // Logged as the history record's content: the message itself.
                        messageBody = text.ifEmpty { title },
                        simSlot = null,
                        isTest = false,
                        showNotification = false,
                        source = Def.SOURCE_NOTIFICATION,
                        checkResult = check,
                    )
                    screeningResult = result
                    if (result.shouldBlock()) {
                        cancelNotification(sbn.key)
                    } else {
                        // The allowed-notification alert is for Number/Text Rule matches.
                        // Prefer the matched rule's own alert config, if it set one;
                        // otherwise fall back to the app-level default.
                        val ruleAlertJson = (result as? ByRegexRule)
                            ?.rule?.alertConfigJson?.takeIf { it.isNotEmpty() }

                        val config = ruleAlertJson
                            ?.let { runCatching { PermissiveJson.decodeFromString<AppAlertConfig>(it) }.getOrNull() }
                            ?: spf.AllowedNotificationAlerts(this).find(pkgName)

                        config?.let { fireAllowedNotificationAlert(this, it) }
                    }
                }
            }
        }

        // Voicemail Notification: independent of the Number/Text-Rule gate above. Runs
        //  candidate numbers through a deliberately narrow Basic-Rule checker set (see
        //  `Checker.voicemailNotificationCheckers`: Non-Contact + Spam Database only — the
        //  other Basic Rules either don't apply to a notification (STIR, call-only) or are
        //  calls-only trust signals (Repeated/Dialed/Answered) per their own tooltips, not
        //  meaningful for a voicemail notification that isn't itself a call), plus optionally
        //  Number/Text Regex Rules (user toggle, since those already run on all Notification
        //  Screening apps via the gate above, but with different number-extraction logic that
        //  might catch matches this feature's own extraction/normalization would miss).
        run {
            val voicemailContentKey = "$pkgName|$title|$text"
            val voicemailAlreadyScreened = voicemailScreenedNotifications.contains(voicemailContentKey)

            ensureVoicemailNotificationCache(this)

            if (voicemailNotificationEnabled == true &&
                Permission.notificationAccess.isGranted &&
                voicemailNotificationPkgs?.contains(pkgName) == true &&
                !voicemailAlreadyScreened &&
                !isGroupSummary &&
                // Already dismissed by Notification Screening (app selected for both).
                screeningResult?.shouldBlock() != true
            ) {
                // Only phone-length numbers, judged before the country-code variants are
                //  added, so a verification code like "482913" can't pass as the phone
                //  number "1482913" and get blocked as a Non-Contact.
                val rawCandidates = buildList {
                    if (voicemailNotificationApplyToTitle == true) addAll(findNumberCandidates(title))
                    if (voicemailNotificationApplyToBody == true) addAll(findNumberCandidates(text))
                }.filter { isPhoneLength(it) }.distinct()
                val candidates = withCountryCodeVariants(this, rawCandidates)

                if (candidates.isNotEmpty()) {
                    voicemailScreenedNotifications.add(voicemailContentKey)

                    val checkers = buildList {
                        addAll(Checker.voicemailNotificationCheckers(
                            this@NotificationListenerService,
                            includeAllowedCall = voicemailNotificationAllowIfCallAllowed == true,
                        ))
                        if (voicemailNotificationIncludeRegexRules == true) {
                            ensureNotificationScreeningCache(this@NotificationListenerService)
                            val regexRules = buildList {
                                notifTitleNumberRules?.let { addAll(it) }
                                notifBodyNumberRules?.let { addAll(it) }
                            }.distinctBy { it.id }
                            addAll(regexRules.map { it.numberRuleToChecker(this@NotificationListenerService) })

                            val contentRules = buildList {
                                notifTitleContentRules?.let { addAll(it) }
                                notifBodyContentRules?.let { addAll(it) }
                            }.distinctBy { it.id }
                            addAll(contentRules.map { Checker.Content(this@NotificationListenerService, it) })
                        }
                    }

                    // Decided by rule priority across all candidates (e.g. a contact match on one
                    //  country-code variant beats Non-Contact on another), logged once.
                    // The part each number was found in is left open (null): this feature picks
                    //  its own parts to search, so a Basic Rule applies here if either its Title
                    //  or Body option is on.
                    val (decisiveNumber, decisiveCheck) = checkNumberCandidates(
                        this, candidates.map { it to null }, messageBody = text, checkers = checkers,
                    )

                    // Already allowed (and logged, and alerted) by Notification Screening (app
                    //  selected for both): only a block here changes anything, so only that
                    //  gets logged, instead of a second record and alert for the same notification.
                    if (screeningResult != null && !decisiveCheck.first.shouldBlock()) {
                        return@run
                    }

                    val result = SmsReceiver.processSms(
                        ctx = this,
                        rawNumber = decisiveNumber,
                        messageBody = text,
                        simSlot = null,
                        isTest = false,
                        showNotification = false,
                        source = Def.SOURCE_NOTIFICATION,
                        checkResult = decisiveCheck,
                    )
                    val blocked = result.shouldBlock()
                    val allowResult: ICheckResult? = if (blocked) null else result

                    if (blocked) {
                        cancelNotification(sbn.key)
                    } else if (allowResult != null) {
                        val ruleAlertJson = (allowResult as? ByRegexRule)
                            ?.rule?.alertConfigJson?.takeIf { it.isNotEmpty() }

                        val config = ruleAlertJson
                            ?.let { runCatching { PermissiveJson.decodeFromString<AppAlertConfig>(it) }.getOrNull() }
                            ?: spf.AllowedNotificationAlerts(this).find(pkgName)

                        config?.let { fireAllowedNotificationAlert(this, it) }
                    }
                }
            }
        }

        ensureCache(this)

        // All records for this package name
        val recs = cache?.get(pkgName)
        if (recs == null)
            return

        val body = listOf(title, text).joinToString("\n").trim()

        // Keep all records that match the notification content
        val records = recs.filter {
            it.body.regexMatches(body, it.bodyFlags)
        }

        if (records.isEmpty())
            return

        // Get the record with max duration
        val max = records.maxBy {
            it.duration
        }


        val spf = spf.PushAlert(this)

        // Ignore if the new expire time is less than the previous time
        val prevExpireTime = spf.expireTime
        val newExpireTime = postTime + max.duration.toLong() * 60 * 1000
        if (newExpireTime <= prevExpireTime)
            return

//        logi("push alert update, regex = ${max.body}, content: $body, expire: $newExpireTime")

        spf.pkgName = pkgName
        spf.body = body
        spf.expireTime = newExpireTime
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)

        val extras = sbn.notification.extras ?: return
        val title = extras.getString("android.title", "")
        val text = extras.getString("android.text", "")
        val contentKey = "${sbn.packageName}|$title|$text"
        screenedNotifications.remove(contentKey)
        voicemailScreenedNotifications.remove(contentKey)
    }
}