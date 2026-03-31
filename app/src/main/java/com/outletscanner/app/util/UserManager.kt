package com.outletscanner.app.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class User(
    val username: String,
    val passwordHash: String,
    val role: String, // "admin", "superuser", "user"
    val assignedStore: String = ""
)

class UserManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "user_manager_prefs"
        private const val KEY_USERS = "users_json"
        private const val KEY_CURRENT_USER = "current_username"

        const val ROLE_ADMIN = "admin"
        const val ROLE_SUPERUSER = "superuser"
        const val ROLE_USER = "user"

        fun hashPassword(password: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
            return hashBytes.joinToString("") { "%02x".format(it) }
        }
    }

    init {
        if (!prefs.contains(KEY_USERS)) {
            initDefaultUsers()
        }
    }

    private fun initDefaultUsers() {
        val defaults = listOf(
            User("admin", hashPassword("admin123"), ROLE_ADMIN),
            User("superuser", hashPassword("super123"), ROLE_SUPERUSER),
            User("user1", hashPassword("user123"), ROLE_USER, "SS")
        )
        saveUsers(defaults)
    }

    private fun saveUsers(users: List<User>) {
        val jsonArray = JSONArray()
        users.forEach { user ->
            val obj = JSONObject().apply {
                put("username", user.username)
                put("passwordHash", user.passwordHash)
                put("role", user.role)
                put("assignedStore", user.assignedStore)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_USERS, jsonArray.toString()).apply()
    }

    fun getAllUsers(): List<User> {
        val json = prefs.getString(KEY_USERS, "[]") ?: "[]"
        val jsonArray = JSONArray(json)
        val users = mutableListOf<User>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            users.add(
                User(
                    username = obj.getString("username"),
                    passwordHash = obj.getString("passwordHash"),
                    role = obj.getString("role"),
                    assignedStore = obj.optString("assignedStore", "")
                )
            )
        }
        return users
    }

    fun authenticate(username: String, password: String): User? {
        val hash = hashPassword(password)
        return getAllUsers().find { it.username == username && it.passwordHash == hash }
    }

    fun getCurrentUser(): User? {
        val username = prefs.getString(KEY_CURRENT_USER, null) ?: return null
        return getAllUsers().find { it.username == username }
    }

    fun setCurrentUser(username: String) {
        prefs.edit().putString(KEY_CURRENT_USER, username).apply()
    }

    fun clearCurrentUser() {
        prefs.edit().remove(KEY_CURRENT_USER).apply()
    }

    fun addUser(username: String, password: String, role: String, assignedStore: String = ""): Boolean {
        val users = getAllUsers().toMutableList()
        if (users.any { it.username == username }) return false
        users.add(User(username, hashPassword(password), role, assignedStore))
        saveUsers(users)
        return true
    }

    fun removeUser(username: String): Boolean {
        val users = getAllUsers().toMutableList()
        val removed = users.removeAll { it.username == username }
        if (removed) saveUsers(users)
        return removed
    }

    fun updateUser(username: String, newPassword: String?, newRole: String?, newAssignedStore: String?): Boolean {
        val users = getAllUsers().toMutableList()
        val index = users.indexOfFirst { it.username == username }
        if (index == -1) return false
        val existing = users[index]
        users[index] = User(
            username = existing.username,
            passwordHash = if (newPassword != null && newPassword.isNotBlank()) hashPassword(newPassword) else existing.passwordHash,
            role = newRole ?: existing.role,
            assignedStore = newAssignedStore ?: existing.assignedStore
        )
        saveUsers(users)
        return true
    }
}
