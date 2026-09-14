package de.codevoid.motolauncher.data

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Park lock state: whether the launcher is currently parked, and the PIN that ends it.
 *
 * The PIN is salted and hashed rather than stored plainly. That is hygiene, not security:
 * a four-digit PIN falls to a trivial search by anyone who can read this file, and anyone
 * who can read it already has adb, which defeats the park lock outright. It exists so the
 * PIN is not sitting in a prefs XML in plain sight.
 */
class ParkStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Set when park mode starts, cleared by a correct PIN. Survives a reboot, which is
     *  what restores the park screen after one — lock task mode itself does not. */
    var isParked: Boolean
        get() = prefs.getBoolean(KEY_PARKED, false)
        set(value) = prefs.edit().putBoolean(KEY_PARKED, value).apply()

    val hasPin: Boolean get() = prefs.getString(KEY_HASH, null) != null

    fun setPin(pin: String) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }.encode()
        prefs.edit()
            .putString(KEY_SALT, salt)
            .putString(KEY_HASH, hash(pin, salt))
            .apply()
    }

    fun verify(pin: String): Boolean {
        val salt = prefs.getString(KEY_SALT, null) ?: return false
        val stored = prefs.getString(KEY_HASH, null) ?: return false
        return stored == hash(pin, salt)
    }

    companion object {
        private const val PREFS = "settings"
        private const val KEY_PARKED = "parked"
        private const val KEY_SALT = "park_pin_salt"
        private const val KEY_HASH = "park_pin_hash"
        private const val SALT_BYTES = 16

        const val PIN_LENGTH = 4

        private fun hash(pin: String, salt: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest((salt + pin).toByteArray(Charsets.UTF_8))
                .encode()

        private fun ByteArray.encode(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    }
}
