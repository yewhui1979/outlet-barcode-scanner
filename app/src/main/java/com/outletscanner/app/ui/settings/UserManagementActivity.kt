package com.outletscanner.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.outletscanner.app.R
import com.outletscanner.app.databinding.ActivityUserManagementBinding
import com.outletscanner.app.databinding.ItemUserBinding
import com.outletscanner.app.util.PrefsManager
import com.outletscanner.app.util.ServerUserManager
import com.outletscanner.app.util.User
import com.outletscanner.app.util.UserManager

class UserManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserManagementBinding
    private lateinit var userManager: UserManager
    private lateinit var serverUserManager: ServerUserManager
    private lateinit var adapter: UserAdapter
    private var currentUsername: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userManager = UserManager(this)
        serverUserManager = ServerUserManager(this)
        currentUsername = userManager.getCurrentUser()?.username
            ?: PrefsManager(this).currentUsername

        setupToolbar()
        setupRecyclerView()
        setupFab()
        loadUsers()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = UserAdapter(
            currentUsername = currentUsername,
            onDelete = { user -> confirmDelete(user) }
        )
        binding.rvUsers.layoutManager = LinearLayoutManager(this)
        binding.rvUsers.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAddUser.setOnClickListener { showAddUserDialog() }
    }

    private fun loadUsers() {
        lifecycleScope.launch {
            // Try server first, fall back to local
            val users = serverUserManager.getAllUsers().ifEmpty {
                userManager.getAllUsers()
            }
            adapter.submitList(users)
            binding.tvEmpty.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showAddUserDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_user, null)
        val etUsername = dialogView.findViewById<TextInputEditText>(R.id.etDialogUsername)
        val etPassword = dialogView.findViewById<TextInputEditText>(R.id.etDialogPassword)
        val tilStore = dialogView.findViewById<TextInputLayout>(R.id.tilDialogStore)
        val actvRole = dialogView.findViewById<AutoCompleteTextView>(R.id.actvDialogRole)
        val actvStore = dialogView.findViewById<AutoCompleteTextView>(R.id.actvDialogStore)

        val roles = listOf("admin", "superuser", "buyer", "user")
        actvRole.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, roles))

        actvStore.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, PrefsManager.OUTLETS)
        )

        tilStore.visibility = View.GONE

        actvRole.setOnItemClickListener { _, _, position, _ ->
            tilStore.visibility = if (roles[position] == "user") View.VISIBLE else View.GONE
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_user)
            .setView(dialogView)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val username = etUsername.text.toString().trim()
                val password = etPassword.text.toString().trim()
                val role = actvRole.text.toString().trim()
                val store = actvStore.text.toString().trim()

                if (username.isBlank() || password.isBlank() || role.isBlank()) {
                    Toast.makeText(this, R.string.fill_all_fields, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (role == "user" && store.isBlank()) {
                    Toast.makeText(this, R.string.assign_store_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    // Add to server first, then local as backup
                    val serverSuccess = serverUserManager.addUser(username, password, role, store)
                    val localSuccess = userManager.addUser(username, password, role, store)

                    if (serverSuccess || localSuccess) {
                        Toast.makeText(this@UserManagementActivity, R.string.user_added, Toast.LENGTH_SHORT).show()
                        loadUsers()
                    } else {
                        Toast.makeText(this@UserManagementActivity, R.string.username_exists, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(user: User) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_user)
            .setMessage(getString(R.string.confirm_delete_user, user.username))
            .setPositiveButton(R.string.confirm) { _, _ ->
                lifecycleScope.launch {
                    serverUserManager.removeUser(user.username)
                    userManager.removeUser(user.username)
                    Toast.makeText(this@UserManagementActivity, R.string.user_deleted, Toast.LENGTH_SHORT).show()
                    loadUsers()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    class UserAdapter(
        private val currentUsername: String,
        private val onDelete: (User) -> Unit
    ) : RecyclerView.Adapter<UserAdapter.ViewHolder>() {

        private var users = listOf<User>()

        fun submitList(list: List<User>) {
            users = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemUserBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(users[position])
        }

        override fun getItemCount() = users.size

        inner class ViewHolder(private val binding: ItemUserBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(user: User) {
                binding.tvUsername.text = user.username
                binding.tvRole.text = user.role.replaceFirstChar { it.uppercase() }

                if (user.role == UserManager.ROLE_USER && user.assignedStore.isNotBlank()) {
                    binding.tvStore.visibility = View.VISIBLE
                    binding.tvStore.text = itemView.context.getString(
                        R.string.assigned_store_label, user.assignedStore
                    )
                } else {
                    binding.tvStore.visibility = View.GONE
                }

                // Cannot delete yourself
                if (user.username == currentUsername) {
                    binding.ivDelete.visibility = View.GONE
                } else {
                    binding.ivDelete.visibility = View.VISIBLE
                    binding.ivDelete.setOnClickListener { onDelete(user) }
                }
            }
        }
    }
}
