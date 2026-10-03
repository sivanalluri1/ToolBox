package dev.siva.toolbox

internal object TapTiming {
 const val KEY = "tap_interval_ms"
 const val MIN_MS = 150
 const val MAX_MS = 700
 const val DEFAULT_MS = 300
 const val STEP_MS = 10
 fun clamp(value: Int) = value.coerceIn(MIN_MS, MAX_MS)
}
