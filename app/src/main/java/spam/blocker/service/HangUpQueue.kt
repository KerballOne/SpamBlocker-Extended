package spam.blocker.service

import android.content.Context
import spam.blocker.util.Util
import spam.blocker.util.spf

// Calls blocked by "Answer + Hang up".
// Entries are added in CallScreeningService, then answered and ended in CallStateReceiver.
// Multiple entries are needed because robocallers often call twice at once, a single slot
//  would be overwritten by the second call.
object HangUpQueue {
    // An unanswered call is dropped after this, long enough to wait for an earlier hang-up.
    private const val UNANSWERED_TIMEOUT = 30_000L
    // An answered call is dropped after its hang-up delay plus this.
    private const val ANSWERED_GRACE = 10_000L

    data class Entry(
        val number: String,
        val screenedAt: Long,
        val delaySec: Int,
        val answeredAt: Long = 0, // 0 means not answered yet
    ) {
        val isAnswered get() = answeredAt > 0

        fun isExpired(now: Long): Boolean {
            return if (isAnswered)
                now - answeredAt > delaySec * 1000L + ANSWERED_GRACE
            else
                now - screenedAt > UNANSWERED_TIMEOUT
        }

        fun serialize() = "$number,$screenedAt,$delaySec,$answeredAt"

        companion object {
            fun parse(s: String): Entry? {
                val parts = s.split(",")
                if (parts.size != 4)
                    return null
                return Entry(
                    number = parts[0],
                    screenedAt = parts[1].toLongOrNull() ?: return null,
                    delaySec = parts[2].toIntOrNull() ?: return null,
                    answeredAt = parts[3].toLongOrNull() ?: return null,
                )
            }
        }
    }

    private fun load(ctx: Context): MutableList<Entry> {
        val now = System.currentTimeMillis()
        return spf.Temporary(ctx).pendingHangUps
            .split(";")
            .mapNotNull { Entry.parse(it) }
            .filterNot { it.isExpired(now) }
            .toMutableList()
    }

    private fun save(ctx: Context, entries: List<Entry>) {
        spf.Temporary(ctx).pendingHangUps = entries.joinToString(";") { it.serialize() }
    }

    @Synchronized
    fun add(ctx: Context, rawNumber: String, delaySec: Int) {
        val number = Util.clearNumber(rawNumber)
        val entries = load(ctx)
        entries.removeAll { it.number == number }
        entries += Entry(number, System.currentTimeMillis(), delaySec)
        save(ctx, entries)
    }

    @Synchronized
    fun find(ctx: Context, rawNumber: String): Entry? {
        val entries = load(ctx)
        val number = Util.clearNumber(rawNumber)
        return entries.firstOrNull { it.number == number }
            ?: entries.firstOrNull { isSameNumber(it.number, rawNumber) }
    }

    // The ringing broadcast may carry the number in another format than the screened call,
    //  e.g. a SIP URI on Wi-Fi calling ("sip:+13135551234@ims.example.org;user=phone"), or
    //  without the country code ("3135551234"). Without a match the call is never answered,
    //  it rings silently until it goes to voicemail.
    private const val MIN_SUFFIX_DIGITS = 7

    private fun digitsOf(raw: String): String {
        return raw.substringAfter("sip:").substringAfter("tel:")
            .substringBefore('@').substringBefore(';')
            .filter { it.isDigit() }
            .trimStart('0') // national prefix, e.g. "030..." vs "+4930..."
    }

    // Same number if the digits match, or one ends with the other (country code missing).
    fun isSameNumber(a: String, b: String): Boolean {
        val da = digitsOf(a)
        val db = digitsOf(b)
        if (da.isEmpty() || db.isEmpty()) {
            return false // private numbers only match exactly, see find()
        }
        if (da == db) {
            return true
        }
        val (short, long) = if (da.length < db.length) da to db else db to da
        return short.length >= MIN_SUFFIX_DIGITS && long.endsWith(short)
    }

    @Synchronized
    fun markAnswered(ctx: Context, number: String) {
        val entries = load(ctx).map {
            if (it.number == number) it.copy(answeredAt = System.currentTimeMillis()) else it
        }
        save(ctx, entries)
    }

    @Synchronized
    fun remove(ctx: Context, number: String) {
        save(ctx, load(ctx).filterNot { it.number == number })
    }

    @Synchronized
    fun removeAnswered(ctx: Context) {
        save(ctx, load(ctx).filterNot { it.isAnswered })
    }

    // Called when an allowed or outgoing call shows up. A queued call may have been dropped by
    //  its caller, answering "the next ringing call" could then answer the allowed call, and a
    //  private number's entry (empty number) would match an outgoing call.
    @Synchronized
    fun removeUnanswered(ctx: Context) {
        save(ctx, load(ctx).filter { it.isAnswered })
    }

    // Whether the ongoing call is one that is being hung up.
    @Synchronized
    fun hasAnswered(ctx: Context): Boolean {
        return load(ctx).any { it.isAnswered }
    }

    @Synchronized
    fun isEmpty(ctx: Context): Boolean {
        return load(ctx).isEmpty()
    }

    @Synchronized
    fun hasUnanswered(ctx: Context): Boolean {
        return load(ctx).any { !it.isAnswered }
    }
}
