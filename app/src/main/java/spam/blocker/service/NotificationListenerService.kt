package spam.blocker.service

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
import spam.blocker.service.checker.numberRuleToChecker
import spam.blocker.util.CountryCode
import spam.blocker.util.Permission
import spam.blocker.util.PermissiveJson
import spam.blocker.util.SaveableLogger
import spam.blocker.util.Util
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

private fun findNumberCandidates(title: String): List<String> {
    return phoneNumberCandidateRegex.findAll(title)
        // The regex's leading `[\s().-]*` can capture stray whitespace/punctuation
        //  before the first digit (e.g. a leading space from "from 5551234567"),
        //  which would otherwise reach Basic Rule checkers (e.g. Spam Database) as
        //  part of `rawNumber` and break their exact-match lookups. The old Number
        //  Rule gate never hit this because `regexMatchesNumber` normalizes first.
        .map { it.value.trim().trimStart('(', ')', '-', '.') }
        .filter { candidate -> candidate.count { it.isDigit() } in 3..15 }
        .toList()
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

        ensureNotificationScreeningCache(this)

        if (spfGlobal.isNotificationScreeningEnabled &&
            Permission.notificationAccess.isGranted &&
            notificationScreeningPkgs?.contains(pkgName) == true &&
            !alreadyScreened
        ) {
            // Extract phone-number-shaped substrings from the title/body (tolerating
            //  dashes/spaces/parens/+), then test each against every Number Rule using
            //  the same matching logic used for real numbers, which normalizes formatting
            //  per the rule's own Raw Number/Ignore Country Code flags before comparing.
            // A Number Rule can independently target the title and/or the body, e.g. a
            //  voicemail notification like "New voicemail from: 5551234567" has the
            //  number in the body, not the title.
            val titleCandidates = findNumberCandidates(title)
            val bodyCandidates = findNumberCandidates(text)

            val matchedNumber = notifTitleNumberRules?.firstNotNullOfOrNull { rule ->
                titleCandidates.firstOrNull { candidate ->
                    rule.pattern.regexMatchesNumber(candidate, rule.patternFlags)
                }
            } ?: notifBodyNumberRules?.firstNotNullOfOrNull { rule ->
                bodyCandidates.firstOrNull { candidate ->
                    rule.pattern.regexMatchesNumber(candidate, rule.patternFlags)
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

            if (matchedNumber != null || titleContentMatches || bodyContentMatches) {
                screenedNotifications.add(contentKey)

                // Prefer the number extracted from the title/body; if only the message
                //  content matched, fall back to the raw title as the number passed onward.
                val rawNumber = matchedNumber ?: title

                // Pass whichever field triggered the Content Rule match as `messageBody`,
                //  so the downstream full-match re-check succeeds against the same text.
                // If it matched via the title, use the title; otherwise use the body as usual.
                val messageBodyForCheck = if (titleContentMatches) title else text

                val result = SmsReceiver.processSms(
                    ctx = this,
                    rawNumber = rawNumber,
                    messageBody = messageBodyForCheck,
                    simSlot = null,
                    isTest = false,
                    logger = SaveableLogger(),
                    showNotification = false,
                    source = Def.SOURCE_NOTIFICATION,
                )
                if (result.shouldBlock()) {
                    cancelNotification(sbn.key)
                } else {
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
                !voicemailAlreadyScreened
            ) {
                val rawCandidates = buildList {
                    if (voicemailNotificationApplyToTitle == true) addAll(findNumberCandidates(title))
                    if (voicemailNotificationApplyToBody == true) addAll(findNumberCandidates(text))
                }.distinct()
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

                    // Dismiss if ANY candidate is blocked by any of the checkers above.
                    var blocked = false
                    var allowResult: ICheckResult? = null

                    for (candidate in candidates) {
                        val result = SmsReceiver.processSms(
                            ctx = this,
                            rawNumber = candidate,
                            messageBody = text,
                            simSlot = null,
                            isTest = false,
                            logger = SaveableLogger(),
                            showNotification = false,
                            source = Def.SOURCE_NOTIFICATION,
                            checkers = checkers,
                        )
                        if (result.shouldBlock()) {
                            blocked = true
                            break
                        }
                        if (allowResult == null) {
                            allowResult = result
                        }
                    }

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