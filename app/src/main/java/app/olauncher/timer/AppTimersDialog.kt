package app.olauncher.timer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.AppTimerConfig
import app.olauncher.data.Prefs
import app.olauncher.databinding.DialogAppTimersListBinding
import app.olauncher.databinding.DialogEditAppTimerBinding
import app.olauncher.databinding.ItemAppTimerBinding
import app.olauncher.helper.OlDialog
import app.olauncher.helper.appLabelMatches
import app.olauncher.helper.appUsagePermissionGranted
import app.olauncher.helper.createDialog

object AppTimersDialog {

    fun showAppTimersListDialog(
        activity: Activity,
        allApps: List<AppModel>,
        onUpdated: () -> Unit = {}
    ) {
        val prefs = Prefs(activity)
        val binding = DialogAppTimersListBinding.inflate(activity.layoutInflater)
        val dialog = OlDialog(activity)
        dialog.setContentView(binding.root)

        // Filter only AppModel.App with non-empty package
        val appList = allApps.filterIsInstance<AppModel.App>().filter { it.appPackage.isNotEmpty() }
            .sortedWith(compareByDescending<AppModel.App> {
                prefs.getAppTimerConfig(it.appPackage)?.isConfigured() == true
            }.thenBy { it.appLabel.lowercase() })
            .toMutableList()

        val adapter = AppTimersAdapter(activity, prefs, appList) { clickedApp ->
            showEditTimerDialog(activity, clickedApp) {
                // Refresh list item after saving
                binding.rvAppsList.adapter?.notifyDataSetChanged()
                onUpdated()
            }
        }

        binding.rvAppsList.layoutManager = LinearLayoutManager(activity)
        binding.rvAppsList.adapter = adapter

        // Search filtering
        binding.etSearchApp.doAfterTextChanged { text ->
            adapter.filter(text?.toString())
        }

        // Permission checks
        val hasOverlay = Settings.canDrawOverlays(activity)
        val hasUsage = activity.appUsagePermissionGranted()

        if (!hasOverlay || !hasUsage) {
            binding.layoutPermissionsNotice.visibility = View.VISIBLE
            binding.btnGrantOverlay.visibility = if (!hasOverlay) View.VISIBLE else View.GONE
            binding.btnGrantUsage.visibility = if (!hasUsage) View.VISIBLE else View.GONE

            binding.btnGrantOverlay.setOnClickListener {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${activity.packageName}")
                )
                activity.startActivity(intent)
            }

            binding.btnGrantUsage.setOnClickListener {
                activity.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        } else {
            binding.layoutPermissionsNotice.visibility = View.GONE
        }

        dialog.showRespectingStatusBar()
    }

    fun showEditTimerDialog(
        context: Context,
        app: AppModel.App,
        onSaved: () -> Unit = {}
    ) {
        val prefs = Prefs(context)
        val existingConfig = prefs.getAppTimerConfig(app.appPackage)
        val binding = DialogEditAppTimerBinding.inflate(LayoutInflater.from(context))

        // Set current delay selection
        val delay = existingConfig?.delaySeconds ?: 0
        when (delay) {
            5 -> binding.rbDelay5.isChecked = true
            10 -> binding.rbDelay10.isChecked = true
            15 -> binding.rbDelay15.isChecked = true
            else -> binding.rbDelay0.isChecked = true
        }

        // Set current limit selection
        val limit = existingConfig?.limitMinutes ?: 0
        when (limit) {
            5 -> binding.rbLimit5.isChecked = true
            10 -> binding.rbLimit10.isChecked = true
            15 -> binding.rbLimit15.isChecked = true
            20 -> binding.rbLimit20.isChecked = true
            30 -> binding.rbLimit30.isChecked = true
            45 -> binding.rbLimit45.isChecked = true
            60 -> binding.rbLimit60.isChecked = true
            else -> binding.rbLimit0.isChecked = true
        }

        // Mutual exclusivity between rgLimitRow1 and rgLimitRow2
        var isSettingRadio = false
        binding.rgLimitRow1.setOnCheckedChangeListener { _, checkedId ->
            if (isSettingRadio || checkedId == -1) return@setOnCheckedChangeListener
            isSettingRadio = true
            binding.rgLimitRow2.clearCheck()
            isSettingRadio = false
        }
        binding.rgLimitRow2.setOnCheckedChangeListener { _, checkedId ->
            if (isSettingRadio || checkedId == -1) return@setOnCheckedChangeListener
            isSettingRadio = true
            binding.rgLimitRow1.clearCheck()
            isSettingRadio = false
        }

        binding.swFloatingBadge.isChecked = existingConfig?.showFloatingBadge ?: true

        val dialog = context.createDialog(
            title = R.string.app_timers_title,
            action = R.string.save,
            neutral = R.string.reset,
            onNeutral = {
                prefs.removeAppTimerConfig(app.appPackage)
                onSaved()
            },
            onAction = {
                val selectedDelay = when {
                    binding.rbDelay5.isChecked -> 5
                    binding.rbDelay10.isChecked -> 10
                    binding.rbDelay15.isChecked -> 15
                    else -> 0
                }

                val selectedLimit = when {
                    binding.rbLimit5.isChecked -> 5
                    binding.rbLimit10.isChecked -> 10
                    binding.rbLimit15.isChecked -> 15
                    binding.rbLimit20.isChecked -> 20
                    binding.rbLimit30.isChecked -> 30
                    binding.rbLimit45.isChecked -> 45
                    binding.rbLimit60.isChecked -> 60
                    else -> 0
                }

                val config = AppTimerConfig(
                    packageName = app.appPackage,
                    appName = app.appLabel,
                    delaySeconds = selectedDelay,
                    limitMinutes = selectedLimit,
                    showFloatingBadge = binding.swFloatingBadge.isChecked
                )

                prefs.setAppTimerConfig(config)
                onSaved()
            },
            content = { binding.root }
        )

        // Custom action text: "Save" and "Reset"
        dialog.showRespectingStatusBar()
    }

    private class AppTimersAdapter(
        private val context: Context,
        private val prefs: Prefs,
        private val originalList: List<AppModel.App>,
        private val onItemClick: (AppModel.App) -> Unit
    ) : RecyclerView.Adapter<AppTimersAdapter.ViewHolder>() {

        private var displayedList = originalList.toList()

        fun filter(query: String?) {
            displayedList = if (query.isNullOrBlank()) {
                originalList
            } else {
                originalList.filter { appLabelMatches(it.appLabel, query) }
            }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                ItemAppTimerBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = displayedList[position]
            val config = prefs.getAppTimerConfig(app.appPackage)

            holder.binding.tvAppName.text = app.appLabel

            if (config != null && config.isConfigured()) {
                val summaryParts = mutableListOf<String>()
                if (config.limitMinutes > 0) summaryParts.add("${config.limitMinutes}m limit")
                if (config.delaySeconds > 0) summaryParts.add("${config.delaySeconds}s delay")
                holder.binding.tvTimerSummary.text = summaryParts.joinToString(" • ")
                holder.binding.tvTimerSummary.visibility = View.VISIBLE

                holder.binding.tvBadge.text = if (config.limitMinutes > 0) "${config.limitMinutes}m" else "${config.delaySeconds}s"
                holder.binding.tvBadge.alpha = 1.0f
            } else {
                holder.binding.tvTimerSummary.visibility = View.GONE
                holder.binding.tvBadge.text = context.getString(R.string.off)
                holder.binding.tvBadge.alpha = 0.4f
            }

            holder.itemView.setOnClickListener {
                onItemClick(app)
            }
        }

        override fun getItemCount(): Int = displayedList.size

        class ViewHolder(val binding: ItemAppTimerBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
