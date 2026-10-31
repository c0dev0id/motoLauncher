package de.codevoid.motolauncher.data

import android.content.ComponentName
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.core.os.BundleCompat

/** A launcher activity in one user profile; the same component can exist in several. */
data class ComponentKey(val component: ComponentName, val user: UserHandle)

/** Reads a key written by [putComponentKey]; null when none is stored or its profile is gone. */
fun SharedPreferences.getComponentKey(key: String, users: UserManager): ComponentKey? {
    val component = getString(key, null)?.let { ComponentName.unflattenFromString(it) } ?: return null
    val user = if (contains(userKey(key))) {
        users.getUserForSerialNumber(getLong(userKey(key), 0)) ?: return null
    } else {
        Process.myUserHandle()
    }
    return ComponentKey(component, user)
}

/** Stores [value] under [key], its profile's serial number under a sibling key unless it is the own user. */
fun SharedPreferences.Editor.putComponentKey(
    key: String,
    value: ComponentKey,
    users: UserManager,
): SharedPreferences.Editor {
    putString(key, value.component.flattenToString())
    return if (value.user == Process.myUserHandle()) remove(userKey(key))
    else putLong(userKey(key), users.getSerialNumberForUser(value.user))
}

fun SharedPreferences.Editor.removeComponentKey(key: String): SharedPreferences.Editor =
    remove(key).remove(userKey(key))

private fun userKey(key: String) = "${key}_user"

/** Puts [value] into this bundle under [key], for dialog arguments and fragment results. */
fun Bundle.putComponentKey(key: String, value: ComponentKey) {
    putParcelable(key, value.component)
    putParcelable(userKey(key), value.user)
}

/** Reads a key written by [putComponentKey]; null when none is stored. */
fun Bundle.getComponentKey(key: String): ComponentKey? {
    val component = BundleCompat.getParcelable(this, key, ComponentName::class.java) ?: return null
    val user = BundleCompat.getParcelable(this, userKey(key), UserHandle::class.java) ?: return null
    return ComponentKey(component, user)
}
