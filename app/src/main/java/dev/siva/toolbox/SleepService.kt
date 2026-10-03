package dev.siva.toolbox

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.*
import android.os.PowerManager
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.accessibility.*
import android.widget.Toast

class SleepService : AccessibilityService(), android.content.SharedPreferences.OnSharedPreferenceChangeListener {
 private val prefs by lazy { getSharedPreferences("toolbox", MODE_PRIVATE) }
 private val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
 private var area: View? = null
 private var connected=false
 private var lastSupported=false
 private var decision="starting"
 private val handler = Handler(Looper.getMainLooper())
 private var firstDown = 0L
 private var lastOutside = 0L
 private var outsideCount = 0
 private var lockCount = 0
 private val lockAfterQuiet = Runnable {
  if (supportedScreen()) {
   val success = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
   if (success) lockCount++
   android.util.Log.d("ToolboxGesture", "lock success=$success")
   if (!success) Toast.makeText(this,"Android could not lock the screen",Toast.LENGTH_SHORT).show()
  }
  firstDown=0L
 }
 private val receiver=object : BroadcastReceiver() { override fun onReceive(context: Context?, intent: Intent?) { updateArea() } }
 override fun onServiceConnected() {
  connected=true
  prefs.registerOnSharedPreferenceChangeListener(this)
  val filter=IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) }
  registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED)
  updateArea()
 }
 override fun onAccessibilityEvent(event: AccessibilityEvent?) {
  if (!connected || event == null || event.packageName == packageName) return
  if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED || event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
   cancelPair()
  }
  updateArea()
 }
 private fun cancelPair() { firstDown=0L; handler.removeCallbacks(lockAfterQuiet) }
 override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
  if (key == TapTiming.KEY) cancelPair() else updateArea(true)
 }
 override fun onInterrupt() { removeArea() }
 private fun isHome(): Boolean {
  val root=rootInActiveWindow ?: return false
  if(root.packageName?.toString() != "com.google.android.apps.nexuslauncher") return false
  // Workspace is present on Pixel Launcher's home page. Require it to be visible;
  // package name alone also matches app drawer and overview.
  val workspace=root.findAccessibilityNodeInfosByViewId("com.google.android.apps.nexuslauncher:id/workspace")
  return workspace.any { it.isVisibleToUser } &&
   root.findAccessibilityNodeInfosByViewId("com.google.android.apps.nexuslauncher:id/apps_view").none { it.isVisibleToUser } &&
   root.findAccessibilityNodeInfosByViewId("com.google.android.apps.nexuslauncher:id/overview_panel").none { it.isVisibleToUser }
 }
 private fun hasVisibleResource(node: AccessibilityNodeInfo, names: Set<String>): Boolean {
  if ((node.isVisibleToUser || node.viewIdResourceName == "element:lockscreen") && node.viewIdResourceName in names) return true
  for (index in 0 until node.childCount) {
   val child=node.getChild(index) ?: continue
   if (hasVisibleResource(child,names)) return true
  }
  return false
 }
 private fun supportedScreen(): Boolean {
  val awake=(getSystemService(POWER_SERVICE) as PowerManager).isInteractive
  val keyguard=(getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
  val root=if(keyguard) windows.asSequence().mapNotNull { it.root }.firstOrNull {
   it.packageName?.toString() == "com.android.systemui" && hasVisibleResource(it, setOf("element:lockscreen", "com.android.systemui:id/keyguard_status_view", "com.android.systemui:id/keyguard_clock_container"))
  } else rootInActiveWindow
  val systemUi=root?.packageName?.toString() == "com.android.systemui"
  // Android 17's scene-based keyguard exposes a Compose test-tag resource
  // name rather than the legacy clock/status IDs. Traverse directly because
  // element:lockscreen is not a conventional package:id/name identifier.
  val lockSurface = root?.let { node ->
   hasVisibleResource(node, setOf("element:lockscreen",
    "com.android.systemui:id/keyguard_status_view",
    "com.android.systemui:id/keyguard_clock_container"))
  } ?: false
  // Fail closed if the PIN/password bouncer is exposed or window identity is unknown.
  val bouncer=root?.let { node -> hasVisibleResource(node, setOf(
   "element:bouncer", "element:quick_settings", "element:shade",
   "com.android.systemui:id/keyguardPINView",
   "com.android.systemui:id/keyguardPasswordView",
   "com.android.systemui:id/keyguardPatternView",
   "com.android.systemui:id/bouncer")) } ?: true
  val allowed=awake && if(keyguard) prefs.getBoolean("lock",false) && systemUi && lockSurface && !bouncer else prefs.getBoolean("home",false) && isHome()
  decision="awake=$awake keyguard=$keyguard root=${root?.packageName} systemUi=$systemUi lockSurface=$lockSurface bouncer=$bouncer"
  return allowed
 }
 @android.annotation.SuppressLint("ClickableViewAccessibility")
 private fun updateArea(recreate: Boolean = false) {
  lastSupported=supportedScreen()
  if (!lastSupported) { removeArea(); return }
  if (recreate) removeArea()
  if (area != null) return
  val view=View(this).apply {
   importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
   setOnTouchListener { _, event ->
    if (event.actionMasked == MotionEvent.ACTION_OUTSIDE && supportedScreen()) {
     outsideCount++
     lastOutside=event.eventTime
     android.util.Log.d("ToolboxGesture", "outside event #$outsideCount; coordinatesAvailable=${event.x != 0f || event.y != 0f}")
     val elapsed=event.eventTime-firstDown
     if (firstDown != 0L && elapsed in 40L..TapTiming.clamp(prefs.getInt(TapTiming.KEY,TapTiming.DEFAULT_MS)).toLong()) {
      firstDown=0L
      // Outside notifications contain DOWN only, not UP/MOVE. Give the
      // underlying view time to report a click/scroll before committing.
      handler.postDelayed(lockAfterQuiet, 160L)
     } else {
      handler.removeCallbacks(lockAfterQuiet)
      firstDown=event.eventTime
     }
    }
    false
   }
  }
  val params=WindowManager.LayoutParams(1,1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
   WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
   WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
   WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
   android.graphics.PixelFormat.TRANSLUCENT).apply {
   gravity=Gravity.TOP or Gravity.START
   title="Toolbox touch observer"
  }
  try { wm.addView(view,params); area=view } catch(e: WindowManager.BadTokenException) { android.util.Log.w("Toolbox","Observer unavailable",e) }
 }
 override fun dump(fd: java.io.FileDescriptor, writer: java.io.PrintWriter, args: Array<out String>) {
  writer.println("observerAttached=${area != null} supported=$lastSupported decision=$decision outsideEvents=$outsideCount lastOutside=$lastOutside locks=$lockCount")
 }
 private fun removeArea() { cancelPair(); area?.let { wm.removeView(it) }; area=null }
 override fun onDestroy() {
  removeArea()
  prefs.unregisterOnSharedPreferenceChangeListener(this)
  if(connected) unregisterReceiver(receiver)
  connected=false
  super.onDestroy()
 }
}
