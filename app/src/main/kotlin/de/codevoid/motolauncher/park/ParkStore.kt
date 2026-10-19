package de.codevoid.motolauncher.park

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom

/** Park lock state: whether the launcher is parked, and the salted hash of the PIN that ends it. */
class ParkStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Set while parked; cleared by a correct PIN. */
    var isParked: Boolean
        get() = prefs.getBoolean(KEY_PARKED, false)
        set(value) = prefs.edit { putBoolean(KEY_PARKED, value) }

    val hasPin: Boolean get() = prefs.getString(KEY_HASH, null) != null

    fun setPin(pin: String) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }.encode()
        prefs.edit {
            putString(KEY_SALT, salt)
            putString(KEY_HASH, hash(pin, salt))
        }
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
