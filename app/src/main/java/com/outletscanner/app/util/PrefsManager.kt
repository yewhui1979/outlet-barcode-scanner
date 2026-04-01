package com.outletscanner.app.util

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "outlet_scanner_prefs"
        private const val KEY_OUTLET = "selected_outlet"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_CURRENT_USERNAME = "current_username"
        private const val KEY_CURRENT_ROLE = "current_role"
        private const val KEY_PRINTER_ADDRESS = "saved_printer_address"
        private const val KEY_PRINTER_NAME = "saved_printer_name"

        val OUTLETS = listOf(
            "AJB", "AM", "ASP", "BG", "BM",
            "DAD", "ENS",
            "H025", "H026", "H027", "H028", "H029", "H031",
            "JSA", "KJ", "KK",
            "MJ",
            "PC", "PJ3", "PS",
            "RS",
            "SB", "SD", "SJ", "SM07", "SMP", "SS",
            "TDT", "TSS"
        )
    }

    var selectedOutlet: String
        get() = prefs.getString(KEY_OUTLET, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OUTLET, value).apply()

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var lastSyncTimestamp: String
        get() = prefs.getString(KEY_LAST_SYNC, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_SYNC, value).apply()

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()

    var currentUsername: String
        get() = prefs.getString(KEY_CURRENT_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CURRENT_USERNAME, value).apply()

    var currentRole: String
        get() = prefs.getString(KEY_CURRENT_ROLE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CURRENT_ROLE, value).apply()

    var savedPrinterAddress: String
        get() = prefs.getString(KEY_PRINTER_ADDRESS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PRINTER_ADDRESS, value).apply()

    var savedPrinterName: String
        get() = prefs.getString(KEY_PRINTER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PRINTER_NAME, value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun logout() {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .remove(KEY_OUTLET)
            .apply()
    }
}
