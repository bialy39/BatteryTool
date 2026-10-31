package io.github.domi04151309.batterytool.activities

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import io.github.domi04151309.batterytool.R
import io.github.domi04151309.batterytool.adapters.LoadingAdapter
import io.github.domi04151309.batterytool.adapters.RunningAppsAdapter
import io.github.domi04151309.batterytool.adapters.SimpleListAdapter
import io.github.domi04151309.batterytool.data.SimpleListItem
import io.github.domi04151309.batterytool.helpers.AppHelper
import io.github.domi04151309.batterytool.helpers.P
import io.github.domi04151309.batterytool.helpers.Root
import io.github.domi04151309.batterytool.interfaces.RecyclerViewHelperInterface
import io.github.domi04151309.batterytool.services.ForegroundService

class MainActivity : BaseActivity(), RecyclerViewHelperInterface {
    private val listItems: MutableList<SimpleListItem> = arrayListOf()
    private lateinit var recyclerView: RecyclerView
    private lateinit var addButton: FloatingActionButton
    private var mode = MODE_BLOCKED
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { startBatteryService() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (
            !SetupActivity.demoMode &&
            !P.getPreferences(this).getBoolean(
                P.SETUP_COMPLETE,
                P.SETUP_COMPLETE_DEFAULT,
            )
        ) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        applyEdgeToEdgeInsets(findViewById(R.id.root))

        startServiceWithPermission()

        recyclerView = findViewById(R.id.list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = LoadingAdapter()

        findViewById<MaterialToolbar>(R.id.toolbar).setOnMenuItemClickListener {
            startActivity(
                Intent(
                    this@MainActivity,
                    SettingsActivity::class.java,
                ),
            )
            true
        }

        addButton = findViewById(R.id.add)
        addButton.setOnClickListener {
            startActivity(Intent(this, AddingActivity::class.java))
        }

        findViewById<FloatingActionButton>(R.id.hibernate)?.setOnClickListener {
            AppHelper.hibernate(this)
            Toast.makeText(this, R.string.toast_stopped_all, Toast.LENGTH_SHORT).show()
            Handler(Looper.getMainLooper()).postDelayed({ loadApps() }, HIBERNATE_UPDATE_DELAY)
        }

        val tabs = findViewById<TabLayout>(R.id.tabs)
        tabs.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    switchMode(if (tab.position == TAB_RUNNING) MODE_RUNNING else MODE_BLOCKED)
                }

                override fun onTabUnselected(tab: TabLayout.Tab) = Unit

                override fun onTabReselected(tab: TabLayout.Tab) {
                    // Coming back to the tab that is already selected: refresh it anyway.
                    loadApps()
                }
            },
        )
        // The TabLayout restores its selection after a configuration change, the mode has to
        // follow it before onStart loads the list. onStart does the first load, so no reload here.
        switchMode(
            if (tabs.selectedTabPosition == TAB_RUNNING) MODE_RUNNING else MODE_BLOCKED,
            reload = false,
        )
    }

    override fun onStart() {
        super.onStart()
        loadApps()
    }

    private fun startServiceWithPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startBatteryService()
        }
    }

    private fun startBatteryService() {
        ContextCompat.startForegroundService(this, Intent(this, ForegroundService::class.java))
    }

    private fun switchMode(
        newMode: Int,
        reload: Boolean = true,
    ) {
        // The add button belongs to the list of blocked apps, apps are added there.
        mode = newMode
        addButton.visibility =
            if (mode == MODE_RUNNING) {
                View.GONE
            } else {
                View.VISIBLE
            }
        if (reload) loadApps()
    }

    private fun loadApps() =
        if (mode == MODE_RUNNING) {
            loadRunningApps()
        } else {
            loadBlockedApps()
        }

    private fun loadRunningApps() =
        Thread {
            val requestedMode = mode
            val runningApps = AppHelper.loadRunningApps(this)
            val items =
                runningApps.ifEmpty {
                    listOf(
                        SimpleListItem(
                            resources.getString(R.string.running_empty),
                            resources.getString(R.string.running_empty_summary),
                            ResourcesCompat.getDrawable(resources, R.drawable.ic_empty, theme),
                        ),
                    )
                }

            runOnUiThread {
                if (mode == requestedMode) {
                    listItems.clear()
                    listItems.addAll(items)
                    recyclerView.adapter = RunningAppsAdapter(listItems, ::onKill)
                }
            }
        }.start()

    private fun loadBlockedApps() =
        Thread {
            val requestedMode = mode
            val blacklist = P.getBlacklist(this)
            val soonApps: MutableList<SimpleListItem> = ArrayList(blacklist.size / 2)
            val unnecessaryApps: MutableList<SimpleListItem> = ArrayList(blacklist.size / 2)
            val stoppedApps: MutableList<SimpleListItem> = ArrayList(blacklist.size / 2)
            val services = Root.getServices()

            @Suppress("LoopWithTooManyJumpStatements")
            for (app in blacklist) {
                val listItem = AppHelper.generateListItem(this, app)
                if (
                    packageManager.getApplicationInfo(
                        listItem.summary,
                        PackageManager.GET_META_DATA,
                    ).flags and ApplicationInfo.FLAG_STOPPED != 0
                ) {
                    stoppedApps.add(listItem)
                    continue
                }
                if (
                    services.contains(listItem.summary) || P.getForced(this).contains(listItem.summary)
                ) {
                    soonApps.add(listItem)
                    continue
                }
                unnecessaryApps.add(listItem)
            }

            val emptyList =
                listOf(
                    SimpleListItem(
                        resources.getString(R.string.main_empty),
                        resources.getString(R.string.main_empty_summary),
                        ResourcesCompat.getDrawable(resources, R.drawable.ic_empty, theme),
                    ),
                )

            val items: MutableList<SimpleListItem> = arrayListOf()
            items.add(SimpleListItem(summary = resources.getString(R.string.main_stopped_soon)))
            items.addAll(soonApps.ifEmpty { emptyList })
            items.add(SimpleListItem(summary = resources.getString(R.string.main_stopped_unnecessary)))
            items.addAll(unnecessaryApps.ifEmpty { emptyList })
            items.add(SimpleListItem(summary = resources.getString(R.string.main_stopped)))
            items.addAll(stoppedApps.ifEmpty { emptyList })

            Looper.prepare()
            runOnUiThread {
                if (mode == requestedMode) {
                    listItems.clear()
                    listItems.addAll(items)
                    recyclerView.adapter = SimpleListAdapter(listItems, this)
                }
            }
        }.start()

    private fun onKill(position: Int) {
        val packageName = listItems.getOrNull(position)?.summary ?: return
        Thread {
            // Root calls block, so they never run on the UI thread. The list is refreshed with a
            // delay because am force-stop returns before the process is gone.
            Root.shell("am force-stop $packageName")
            runOnUiThread {
                Toast.makeText(this, R.string.toast_killed, Toast.LENGTH_SHORT).show()
                Handler(Looper.getMainLooper()).postDelayed({ loadApps() }, KILL_UPDATE_DELAY)
            }
        }.start()
    }

    override fun onItemClicked(position: Int) {
        if (
            listItems[position].title.isBlank() ||
            listItems[position].title == resources.getString(R.string.main_empty)
        ) {
            return
        }

        val options =
            resources
                .getStringArray(R.array.main_click_dialog_options)
                .toMutableList()
                .apply {
                    add(
                        2,
                        resources.getString(
                            if (
                                P.getForced(this@MainActivity).contains(listItems[position].summary)
                            ) {
                                R.string.main_click_dialog_turn_off_always
                            } else {
                                R.string.main_click_dialog_turn_on_always
                            },
                        ),
                    )
                }
                .toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.main_click_dialog_title)
            .setItems(options) { _, which ->
                onDialogItemClicked(which, listItems[position].summary)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .show()
    }

    private fun onDialogItemClicked(
        which: Int,
        packageName: String,
    ) {
        when (which) {
            ITEM_STOP_NOW -> {
                Root.shell("am force-stop $packageName")
                loadApps()
                Toast.makeText(this, R.string.toast_stopped, Toast.LENGTH_SHORT).show()
            }
            ITEM_OPEN_SETTINGS -> {
                startActivity(
                    Intent().apply {
                        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        data = Uri.fromParts("package", packageName, null)
                    },
                )
            }
            ITEM_ALWAYS_STOP -> {
                val forcedSet = P.getForcedMutable(this)
                Toast.makeText(
                    this,
                    if (forcedSet.contains(packageName)) {
                        forcedSet.remove(packageName)
                        R.string.main_click_dialog_turn_off_always
                    } else {
                        forcedSet.add(packageName)
                        R.string.main_click_dialog_turn_on_always
                    },
                    Toast.LENGTH_LONG,
                ).show()
                P.setForced(this, forcedSet)
                loadApps()
            }
            ITEM_REMOVE_FROM_LIST -> {
                P.setBlacklist(
                    this,
                    P.getBlacklistMutable(this).apply {
                        remove(packageName)
                    },
                )
                loadApps()
            }
        }
    }

    companion object {
        private const val HIBERNATE_UPDATE_DELAY = 1000L
        private const val KILL_UPDATE_DELAY = 500L
        private const val MODE_BLOCKED = 0
        private const val MODE_RUNNING = 1
        private const val TAB_RUNNING = 1
        private const val ITEM_STOP_NOW = 0
        private const val ITEM_OPEN_SETTINGS = 1
        private const val ITEM_ALWAYS_STOP = 2
        private const val ITEM_REMOVE_FROM_LIST = 3
    }
}
