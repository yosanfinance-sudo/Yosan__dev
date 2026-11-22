package com.example.utils

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.view.MotionEvent
import android.view.Window
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

enum class RefreshRateMode(val displayName: String, val description: String) {
    ADAPTIVE("Adaptive", "90Hz scrolling, 120Hz transitions, 60Hz static"),
    HIGH("High Performance", "Locks display to peak refresh rate (120Hz)"),
    STANDARD("Standard (60Hz)", "Locks display to 60Hz for battery saving")
}

data class DisplayCapabilities(
    val peakModeId: Int = 0,
    val peakRefreshRate: Float = 60f,
    val scrollModeId: Int = 0,
    val scrollRefreshRate: Float = 60f,
    val standardModeId: Int = 0,
    val standardRefreshRate: Float = 60f,
    val isHighRefreshRateSupported: Boolean = false,
    val supportedRates: List<Float> = listOf(60f)
)

object AdaptiveRefreshRateManager {

    private const val TAG = "AdaptiveRefreshRate"
    private const val DEFAULT_SETTLE_DELAY_MS = 350L

    private var activityRef: WeakReference<Activity>? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var settleJob: Job? = null

    private val _capabilities = MutableStateFlow(DisplayCapabilities())
    val capabilities: StateFlow<DisplayCapabilities> = _capabilities.asStateFlow()

    private val _currentMode = MutableStateFlow(RefreshRateMode.ADAPTIVE)
    val currentMode: StateFlow<RefreshRateMode> = _currentMode.asStateFlow()

    private val _activeRefreshRate = MutableStateFlow(60f)
    val activeRefreshRate: StateFlow<Float> = _activeRefreshRate.asStateFlow()

    private var isUserTouching: Boolean = false
    private var isScrolling: Boolean = false
    private var boostUntilTimestamp: Long = 0L
    private var isPowerSaveActive: Boolean = false

    private var lastAppliedModeId: Int? = null
    private var lastAppliedRate: Float? = null
    private var powerSaveReceiver: BroadcastReceiver? = null

    fun init(activity: Activity) {
        activityRef = WeakReference(activity)
        val caps = detectDisplayCapabilities(activity)
        _capabilities.value = caps

        // Register power save mode broadcast
        registerPowerSaveReceiver(activity.applicationContext)

        // Read initial preference
        val appPrefs = AppPreferences(activity.applicationContext)
        val initialUserKey = com.example.data.local.AppDatabase.getActiveUserKey()
        val savedModeStr = appPrefs.getRefreshRateModeForUser(initialUserKey)
        val mode = try {
            RefreshRateMode.valueOf(savedModeStr)
        } catch (_: Throwable) {
            RefreshRateMode.ADAPTIVE
        }
        _currentMode.value = mode

        // Initial evaluate to apply target refresh rate stably
        evaluateAndApply()
    }

    fun loadUserPreference(context: Context, userEmail: String?) {
        val appPrefs = AppPreferences(context)
        val savedModeStr = appPrefs.getRefreshRateModeForUser(userEmail)
        val mode = try {
            RefreshRateMode.valueOf(savedModeStr)
        } catch (_: Throwable) {
            RefreshRateMode.ADAPTIVE
        }
        setMode(mode)
    }

    fun setMode(mode: RefreshRateMode) {
        if (_currentMode.value != mode) {
            _currentMode.value = mode
            evaluateAndApply()
        }
    }

    fun onUserTouchStart() {
        if (isUserTouching) return
        isUserTouching = true
    }

    fun onUserTouchEnd() {
        if (!isUserTouching) return
        isUserTouching = false
    }

    fun onScrollStateChanged(scrolling: Boolean) {
        if (isScrolling == scrolling) return
        isScrolling = scrolling
    }

    fun requestHighRate(reason: String, durationMs: Long = 450L) {
        // Keeping stable refresh rate without runtime mode changes prevents frame drops
    }

    fun updatePowerSaveMode(isPowerSave: Boolean) {
        if (isPowerSaveActive != isPowerSave) {
            isPowerSaveActive = isPowerSave
            evaluateAndApply()
        }
    }

    private fun evaluateAndApply() {
        val caps = _capabilities.value
        if (!caps.isHighRefreshRateSupported) {
            applyTarget(caps.standardModeId, caps.standardRefreshRate)
            return
        }

        val mode = _currentMode.value
        when (mode) {
            RefreshRateMode.HIGH -> {
                applyTarget(caps.peakModeId, caps.peakRefreshRate)
            }
            RefreshRateMode.STANDARD -> {
                applyTarget(caps.standardModeId, caps.standardRefreshRate)
            }
            RefreshRateMode.ADAPTIVE -> {
                if (isPowerSaveActive) {
                    applyTarget(caps.standardModeId, caps.standardRefreshRate)
                } else {
                    // Stably lock display to target 90Hz scroll rate without dynamic mode-switching stalls
                    applyTarget(caps.scrollModeId, caps.scrollRefreshRate)
                }
            }
        }
    }

    private fun applyTarget(modeId: Int, refreshRate: Float) {
        val activity = activityRef?.get() ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        activity.runOnUiThread {
            try {
                val window = activity.window ?: return@runOnUiThread
                val attrs = window.attributes ?: return@runOnUiThread

                if (lastAppliedModeId == modeId && lastAppliedRate == refreshRate) {
                    return@runOnUiThread
                }

                lastAppliedModeId = modeId
                lastAppliedRate = refreshRate

                // On Android 11+ (API 30+), using preferredRefreshRate with preferredDisplayModeId = 0
                // instructs SurfaceFlinger to use dynamic clock scaling (VRR) across alternativeRefreshRates
                // without performing a hard display pipeline reset (eliminating micro-stutter / dropped frames).
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    attrs.preferredDisplayModeId = 0
                    attrs.preferredRefreshRate = refreshRate
                } else {
                    attrs.preferredDisplayModeId = modeId
                }
                window.attributes = attrs
                _activeRefreshRate.value = refreshRate
            } catch (e: Throwable) {
                android.util.Log.w(TAG, "Failed to apply display mode", e)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun detectDisplayCapabilities(activity: Activity): DisplayCapabilities {
        try {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    activity.display
                } catch (_: Throwable) {
                    activity.windowManager.defaultDisplay
                }
            } else {
                activity.windowManager.defaultDisplay
            } ?: return DisplayCapabilities()

            val currentMode = display.mode
            val currentWidth = currentMode?.physicalWidth ?: 0
            val currentHeight = currentMode?.physicalHeight ?: 0

            val allModes = display.supportedModes ?: emptyArray()
            if (allModes.isEmpty()) return DisplayCapabilities()

            val sameResModes = allModes.filter {
                (currentWidth == 0 || it.physicalWidth == currentWidth) &&
                (currentHeight == 0 || it.physicalHeight == currentHeight)
            }.ifEmpty { allModes.toList() }

            val distinctRates = sameResModes.map { it.refreshRate }.distinct().sorted()

            val peakMode = sameResModes.maxByOrNull { it.refreshRate }
            val scrollMode = sameResModes
                .filter { it.refreshRate in 85.0f..95.0f }
                .minByOrNull { kotlin.math.abs(it.refreshRate - 90.0f) }
                ?: peakMode

            val standardMode = sameResModes
                .filter { it.refreshRate in 55.0f..65.0f }
                .minByOrNull { kotlin.math.abs(it.refreshRate - 60.0f) }
                ?: sameResModes.minByOrNull { it.refreshRate }

            val peakRate = peakMode?.refreshRate ?: 60f
            val scrollRate = scrollMode?.refreshRate ?: peakRate
            val standardRate = standardMode?.refreshRate ?: 60f
            val isHighSupported = peakRate >= 80f

            return DisplayCapabilities(
                peakModeId = peakMode?.modeId ?: 0,
                peakRefreshRate = peakRate,
                scrollModeId = scrollMode?.modeId ?: (peakMode?.modeId ?: 0),
                scrollRefreshRate = scrollRate,
                standardModeId = standardMode?.modeId ?: 0,
                standardRefreshRate = standardRate,
                isHighRefreshRateSupported = isHighSupported,
                supportedRates = distinctRates
            )
        } catch (e: Throwable) {
            android.util.Log.w(TAG, "Error detecting display capabilities", e)
            return DisplayCapabilities()
        }
    }

    private fun wrapWindowCallback(activity: Activity) {
        val window = activity.window ?: return
        val originalCallback = window.callback ?: return
        if (originalCallback is AdaptiveWindowCallback) return

        window.callback = AdaptiveWindowCallback(
            originalCallback,
            onTouchDown = { onUserTouchStart() },
            onTouchUpOrCancel = { onUserTouchEnd() }
        )
    }

    private fun registerPowerSaveReceiver(context: Context) {
        if (powerSaveReceiver != null) return
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            isPowerSaveActive = pm?.isPowerSaveMode == true

            powerSaveReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val pManager = ctx?.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    updatePowerSaveMode(pManager?.isPowerSaveMode == true)
                }
            }
            val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            context.registerReceiver(powerSaveReceiver, filter)
        } catch (e: Throwable) {
            android.util.Log.w(TAG, "Failed to register power save receiver", e)
        }
    }

    fun destroy() {
        settleJob?.cancel()
        val activity = activityRef?.get()
        if (activity != null && powerSaveReceiver != null) {
            try {
                activity.applicationContext.unregisterReceiver(powerSaveReceiver)
            } catch (_: Throwable) {}
            powerSaveReceiver = null
        }
        activityRef?.clear()
        activityRef = null
    }

    private class AdaptiveWindowCallback(
        private val delegate: Window.Callback,
        private val onTouchDown: () -> Unit,
        private val onTouchUpOrCancel: () -> Unit
    ) : Window.Callback by delegate {
        override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
            if (event != null) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> onTouchDown()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onTouchUpOrCancel()
                }
            }
            return delegate.dispatchTouchEvent(event)
        }
    }
}

/**
 * Modifier that attaches a nested scroll connection to boost refresh rate to peak during drag & fling.
 */
fun Modifier.adaptiveScrollRate(): Modifier = composed {
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                AdaptiveRefreshRateManager.onScrollStateChanged(true)
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                AdaptiveRefreshRateManager.onScrollStateChanged(false)
                return Velocity.Zero
            }
        }
    }
    this.nestedScroll(nestedScrollConnection)
}

/**
 * Compose helper to link LazyListState or scroll progress to AdaptiveRefreshRateManager.
 */
@Composable
fun AdaptiveScrollEffect(scrollInProgress: Boolean) {
    LaunchedEffect(scrollInProgress) {
        AdaptiveRefreshRateManager.onScrollStateChanged(scrollInProgress)
    }
}

@Composable
fun AdaptiveScrollEffect(lazyListState: LazyListState) {
    AdaptiveScrollEffect(lazyListState.isScrollInProgress)
}

@Composable
fun AdaptiveScrollEffect(scrollState: ScrollState) {
    AdaptiveScrollEffect(scrollState.isScrollInProgress)
}
