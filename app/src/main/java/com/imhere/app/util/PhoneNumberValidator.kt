package com.imhere.app.util

import android.content.Context
import android.telephony.TelephonyManager
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import java.util.Locale

/**
 * Validates user input and converts it to E.164 ("+79161234567").
 *
 * The region is taken from the mobile network the device is registered on; when
 * that is unavailable the system locale is used. A number typed in international
 * format is accepted regardless of the region.
 */
class PhoneNumberValidator(context: Context) {

    private val util: PhoneNumberUtil = PhoneNumberUtil.getInstance()

    private val region: String = runCatching {
        (context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)
            ?.networkCountryIso
            ?.takeIf { it.length == 2 }
            ?.uppercase(Locale.ROOT)
    }.getOrNull()
        ?: Locale.getDefault().country.takeIf { it.length == 2 }
        ?: DEFAULT_REGION

    /** Returns the number in E.164 format, or `null` when it is not a valid number. */
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val candidate = trimmed.replace(" ", "")
        return try {
            val parsed = util.parse(candidate, region)
            if (!util.isValidNumber(parsed)) return null
            if (util.isPossibleNumber(parsed).not()) return null
            util.format(parsed, PhoneNumberFormat.E164)
        } catch (exception: NumberParseException) {
            null
        }
    }

    /** Formats an E.164 number for display, falling back to the raw value. */
    fun formatForDisplay(e164: String): String = try {
        val parsed = util.parse(e164, region)
        util.format(parsed, PhoneNumberFormat.INTERNATIONAL)
    } catch (exception: NumberParseException) {
        e164
    }

    private companion object {
        const val DEFAULT_REGION = "RU"
    }
}
