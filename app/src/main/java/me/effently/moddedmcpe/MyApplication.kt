package me.effently.moddedmcpe

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import org.endercore.android.EnderCore
import org.endercore.android.mod.script.ScriptController
import java.lang.ref.WeakReference

class MyApplication : Application() {
    companion object {
        private const val TAG = "Modded-MCPE-UI"
    }

    private var minecraftActivityRef: WeakReference<Activity>? = null
    private var buttonManager: ModButtonManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        EnderCore.instance.initialize(this, EnderCore.MODE_PUBLIC)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                Log.d(TAG, "Activity Created: ${activity.javaClass.name}")
                if (activity.javaClass.name == "com.mojang.minecraftpe.AgentMainActivity") {
                    minecraftActivityRef = WeakReference(activity)
                }
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                if (activity.javaClass.name == "com.mojang.minecraftpe.AgentMainActivity") {
                    minecraftActivityRef = WeakReference(activity)

                    setupButtonManager(activity)
                }
            }

            override fun onActivityPaused(activity: Activity) {
                if (activity.javaClass.name == "com.mojang.minecraftpe.AgentMainActivity") {
                    removeButtonManager()
                }
            }

            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                if (activity.javaClass.name == "com.mojang.minecraftpe.AgentMainActivity" &&
                    minecraftActivityRef?.get() == activity) {
                    removeButtonManager()
                    minecraftActivityRef = null
                    finishGameProcess()
                }
            }
        })
    }

    private fun finishGameProcess() {
        if (!isGameProcess()) return
        mainHandler.post {
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    private fun isGameProcess(): Boolean {
        val pid = android.os.Process.myPid()
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return activityManager.runningAppProcesses?.any {
            it.pid == pid && it.processName.endsWith(":game")
        } == true
    }

    private fun setupButtonManager(activity: Activity) {
        if (activity.isFinishing) return

        val rootView = activity.window.decorView.findViewById<View>(android.R.id.content)

        rootView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                rootView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                runOnUiThread {
                    try {
                        removeButtonManager()

                        buttonManager = ModButtonManager(activity).apply {
                            // @TODO: impl
                            // addModGearMainButton()

                            // exp buttons for Script / Native modding Testing
                            /* addCustomButton( // Test button for running C++ code via JNI
                                text = "Run Script JNI Code",
                                x = 0,
                                y = 100,
                                onClick = {
                                    Log.d(TAG, "Run Script button clicked")
                                    ScriptController.executeCustomFunction(activity)
                                }
                            )
                            Log.e(TAG, "setupButtonManager done") */
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting up button manager: ${e.message}")
                    }
                }
            }
        })
    }

    private fun removeButtonManager() {
        runOnUiThread {
            try {
                buttonManager?.removeAllButtons()
                buttonManager = null
            } catch (e: Exception) {
                Log.e(TAG, "Error removing button manager: ${e.message}")
            }
        }
    }

    private fun runOnUiThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }
}

/**
 * Button definition
 * containing all needed properties to display and interact with a button
 */
data class ModButtonDefinition(
    val id: String,
    val text: String? = null,
    val iconResId: Int? = null,
    val draggable: Boolean = false,
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    val height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    val backgroundColor: Int = Color.BLUE,
    val textColor: Int = Color.WHITE,
    val visibility: Boolean = false,
    val onClick: () -> Unit = {}
)

/**
 * Manages all mod buttons
 */
class ModButtonManager(private val context: Context) {
    companion object {
        private const val TAG = "ModButtonManager"
        private const val MAIN_GEAR_BUTTON_ID = "main_mod_gear"
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val buttons = mutableMapOf<String, Pair<View, WindowManager.LayoutParams>>()

    init {
    }

    /**
     * Add the ModGear button
     */
    fun addModGearMainButton() {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        val buttonSize = context.resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        val centerX = (screenWidth / 2) - (buttonSize / 2)

        val button = ModButtonDefinition(
            id = MAIN_GEAR_BUTTON_ID,
            text = null,
            iconResId = android.R.drawable.ic_menu_manage,
            x = centerX,
            y = 10,
            width = buttonSize,
            height = buttonSize,
            backgroundColor = Color.GRAY,
            onClick = {
                Log.d(TAG, "ModGear main button clicked")

                val themedContext = ContextThemeWrapper(context, R.style.AppTheme)
                AlertDialog.Builder(themedContext)
                    .setTitle("Settings menu")
                    .setMessage("Will be added in the future...")
                    .setPositiveButton("ОК") { dialog, _ -> dialog.dismiss() }
                    .show()
                // In the future: open settings dialog
            }
        )

        addButton(button)
    }

    /**
     * Add a custom button with text
     */
    fun addCustomButton(
        id: String = "button_${System.currentTimeMillis()}",
        text: String,
        x: Int,
        y: Int,
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        backgroundColor: Int = Color.BLUE,
        textColor: Int = Color.WHITE,
        onClick: () -> Unit = {}
    ) {
        val button = ModButtonDefinition(
            id = id,
            text = text,
            x = x,
            y = y,
            width = width,
            height = height,
            backgroundColor = backgroundColor,
            textColor = textColor,
            onClick = onClick
        )

        addButton(button)
    }

    /**
     * Add a custom button with an icon
     */
    fun addCustomIconButton(
        id: String = "icon_button_${System.currentTimeMillis()}",
        iconResId: Int,
        x: Int,
        y: Int,
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        backgroundColor: Int = Color.TRANSPARENT,
        onClick: () -> Unit = {}
    ) {
        val button = ModButtonDefinition(
            id = id,
            iconResId = iconResId,
            x = x,
            y = y,
            width = width,
            height = height,
            backgroundColor = backgroundColor,
            onClick = onClick
        )

        addButton(button)
    }

    fun addButtonRelative(
        buttonDef: ModButtonDefinition,
        positionX: Float, // 0.0f to 1.0f (% of screen width)
        positionY: Float  // 0.0f to 1.0f (% of screen height)
    ) {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val actualX = (screenWidth * positionX).toInt()
        val actualY = (screenHeight * positionY).toInt()

        val updatedButtonDef = buttonDef.copy(x = actualX, y = actualY)
        addButton(updatedButtonDef)
    }

    private fun makeButtonDraggable(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var startClickTime = 0L
        var hasMoved = false
        val clickDuration = 200 // milliseconds

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    startClickTime = System.currentTimeMillis()
                    hasMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    val distance = Math.sqrt((dx * dx + dy * dy).toDouble())

                    if (distance > 10) {
                        hasMoved = true
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val clickDurationMillis = System.currentTimeMillis() - startClickTime

                    if (clickDurationMillis < clickDuration && !hasMoved) {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

//    fun toggleButtonsVisibility(visible: Boolean) {
//        buttons.values.forEach { button ->
//            button.visibility = if (visible) View.VISIBLE else View.GONE
//        }
//    }

    /**
     * Add a button using the full button definition
     */
    private fun addButton(buttonDef: ModButtonDefinition) {
        try {
            // Remove any existing button with the same ID
            removeButton(buttonDef.id)

            val view = if (buttonDef.text != null) {
                Button(context).apply {
                    text = buttonDef.text
                    setBackgroundColor(buttonDef.backgroundColor)
                    setTextColor(buttonDef.textColor)
                }
            } else if (buttonDef.iconResId != null) {
                ImageButton(context).apply {
                    setImageResource(buttonDef.iconResId)
                    setBackgroundColor(buttonDef.backgroundColor)
                }
            } else {
                Button(context).apply {
                    text = "Button"
                    setBackgroundColor(buttonDef.backgroundColor)
                    setTextColor(buttonDef.textColor)
                }
            }

            val params = WindowManager.LayoutParams().apply {
                width = buttonDef.width
                height = buttonDef.height
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
                gravity = Gravity.START or Gravity.TOP
                x = buttonDef.x
                y = buttonDef.y
            }

            view.setOnClickListener {
                try {
                    buttonDef.onClick()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in button click handler: ${e.message}")
                }
            }

            windowManager.addView(view, params)
            buttons[buttonDef.id] = Pair(view, params)

            if (buttonDef.draggable) {
                makeButtonDraggable(view, params)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error adding button ${buttonDef.id}: ${e.message}")
        }
    }

    /**
     * Remove a specific button by ID
     */
    private fun removeButton(id: String) {
        buttons[id]?.let { (view, _) ->
            try {
                view.setOnClickListener(null)
                windowManager.removeView(view)
                buttons.remove(id)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing button $id: ${e.message}")
            }
        }
    }

    /**
     * Remove all buttons
     */
    fun removeAllButtons() {
        try {
            buttons.values.forEach { (view, _) ->
                view.setOnClickListener(null)
                windowManager.removeView(view)
            }
            buttons.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Error removing all buttons: ${e.message}")
        }
    }
}
