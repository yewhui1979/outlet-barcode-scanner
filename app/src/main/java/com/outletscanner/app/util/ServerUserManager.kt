package com.outletscanner.app.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Server-side user management. All user accounts are stored in the central database
 * on the AWS server. Admin manages users from one place, all phones authenticate
 * against the same database.
 */
class ServerUserManager(context: Context) {

    private val prefsManager = PrefsManager(context)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    companion object {
        const val ROLE_ADMIN = "admin"
        const val ROLE_SUPERUSER = "superuser"
        const val ROLE_BUYER = "buyer"
        const val ROLE_USER = "user"

        fun hashPassword(password: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
            return hashBytes.joinToString("") { "%02x".format(it) }
        }
    }

    private fun getApiUrl(action: String): String {
        val baseUrl = prefsManager.serverUrl.trimEnd('/')
        return "$baseUrl/api_users.php?action=$action"
    }

    /**
     * Authenticate user against the server database.
     * Returns User on success, null on failure.
     * Falls back to local auth if server is unreachable.
     */
    suspend fun authenticate(username: String, password: String): User? = withContext(Dispatchers.IO) {
        val hash = hashPassword(password)

        try {
            val json = JSONObject().apply {
                put("username", username)
                put("password_hash", hash)
            }

            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(getApiUrl("login"))
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext null

            val result = JSONObject(responseBody)
            if (result.getBoolean("success")) {
                val userObj = result.getJSONObject("user")
                User(
                    username = userObj.getString("username"),
                    passwordHash = hash,
                    role = userObj.getString("role"),
                    assignedStore = userObj.optString("assigned_store", "")
                )
            } else {
                null
            }
        } catch (e: Exception) {
            // Server unreachable - fall back to local UserManager
            null
        }
    }

    /**
     * Get all users from server (admin only).
     */
    suspend fun getAllUsers(): List<User> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(getApiUrl("list"))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext emptyList()

            val result = JSONObject(responseBody)
            if (result.getBoolean("success")) {
                val usersArray = result.getJSONArray("users")
                val users = mutableListOf<User>()
                for (i in 0 until usersArray.length()) {
                    val obj = usersArray.getJSONObject(i)
                    users.add(User(
                        username = obj.getString("username"),
                        passwordHash = "",
                        role = obj.getString("role"),
                        assignedStore = obj.optString("assigned_store", "")
                    ))
                }
                users
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Add a new user on the server.
     */
    suspend fun addUser(username: String, password: String, role: String, assignedStore: String = ""): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("username", username)
                put("password_hash", hashPassword(password))
                put("role", role)
                put("assigned_store", assignedStore)
            }

            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(getApiUrl("add"))
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext false

            JSONObject(responseBody).getBoolean("success")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Delete a user on the server.
     */
    suspend fun removeUser(username: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("username", username)
            }

            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(getApiUrl("delete"))
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext false

            JSONObject(responseBody).getBoolean("success")
        } catch (e: Exception) {
            false
        }
    }
}
