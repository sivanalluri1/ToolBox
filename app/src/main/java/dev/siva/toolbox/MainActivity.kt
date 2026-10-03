package dev.siva.toolbox

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.graphics.Color
import android.view.accessibility.AccessibilityManager
import android.widget.*

class MainActivity : Activity() {
 private val prefs by lazy { getSharedPreferences("toolbox", MODE_PRIVATE) }
 private lateinit var status: TextView
 override fun onCreate(state: Bundle?) {
  super.onCreate(state)
  val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48,72,48,40); setBackgroundColor(Color.rgb(245,247,250)) }
  fun text(value: String, size: Float = 16f) = TextView(this).apply { text=value; textSize=size; setPadding(0,16,0,16); box.addView(this) }
  text("Toolbox", 32f)
  text("Double tap to sleep", 22f)
  status = text("")
  text("Double tap an empty area on the home or lock screen to turn off and lock the screen. There is no visible sleep area. Single taps and swipes go to the underlying screen.")
  fun toggle(label: String, key: String, default: Boolean) {
   box.addView(Switch(this).apply { text=label; isChecked=prefs.getBoolean(key,default); setOnCheckedChangeListener { _, value -> prefs.edit().putBoolean(key,value).apply() } })
  }
  toggle("Enable on home screen", "home", false)
  toggle("Enable on lock screen", "lock", false)
  text("Accessibility permission lets Toolbox inspect window structure to identify supported screens and lock the device when you double tap. Processing stays on this device; Toolbox has no internet permission. Android 17 Advanced Protection may block this service.")
  box.addView(Button(this).apply { text=getString(R.string.setup_accessibility); setOnClickListener {
   android.app.AlertDialog.Builder(this@MainActivity).setTitle("Enable Toolbox")
    .setMessage("Toolbox reads window structure locally and observes touch-start notifications on supported screens using an invisible one-pixel window. Enabling the service grants Android accessibility access. Do you agree to continue to Settings?")
    .setNegativeButton("Cancel",null).setPositiveButton("Agree and open Settings") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }.show()
  } })
  text("Experimental: Android supplies touch-start notifications, not full gestures. Rapid swipes or long presses may be mistaken for double taps. Icon clicks and scroll events cancel pending detection.",14f)
  setContentView(ScrollView(this).apply { addView(box) })
 }
 override fun onResume() {
  super.onResume()
  val manager=getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
  val enabled=manager.getEnabledAccessibilityServiceList(-1).any { it.resolveInfo.serviceInfo.packageName == packageName }
  status.text=if(enabled) "Accessibility service enabled" else "Accessibility service disabled"
 }
}
