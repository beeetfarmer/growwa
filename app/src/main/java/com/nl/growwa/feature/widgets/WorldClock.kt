package com.nl.growwa.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.widget.RemoteViews
import com.nl.growwa.R
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

/** A city the world-clock widget can display. */
data class ClockCity(val id: String, val name: String, val zoneId: String)

/** Curated set of well-known cities, grouped roughly west → east. */
val WORLD_CITIES: List<ClockCity> = listOf(
    ClockCity("honolulu", "Honolulu", "Pacific/Honolulu"),
    ClockCity("los_angeles", "Los Angeles", "America/Los_Angeles"),
    ClockCity("denver", "Denver", "America/Denver"),
    ClockCity("mexico_city", "Mexico City", "America/Mexico_City"),
    ClockCity("chicago", "Chicago", "America/Chicago"),
    ClockCity("new_york", "New York", "America/New_York"),
    ClockCity("toronto", "Toronto", "America/Toronto"),
    ClockCity("sao_paulo", "São Paulo", "America/Sao_Paulo"),
    ClockCity("reykjavik", "Reykjavik", "Atlantic/Reykjavik"),
    ClockCity("london", "London", "Europe/London"),
    ClockCity("lisbon", "Lisbon", "Europe/Lisbon"),
    ClockCity("madrid", "Madrid", "Europe/Madrid"),
    ClockCity("paris", "Paris", "Europe/Paris"),
    ClockCity("amsterdam", "Amsterdam", "Europe/Amsterdam"),
    ClockCity("copenhagen", "Copenhagen", "Europe/Copenhagen"),
    ClockCity("berlin", "Berlin", "Europe/Berlin"),
    ClockCity("rome", "Rome", "Europe/Rome"),
    ClockCity("stockholm", "Stockholm", "Europe/Stockholm"),
    ClockCity("athens", "Athens", "Europe/Athens"),
    ClockCity("cairo", "Cairo", "Africa/Cairo"),
    ClockCity("istanbul", "Istanbul", "Europe/Istanbul"),
    ClockCity("moscow", "Moscow", "Europe/Moscow"),
    ClockCity("nairobi", "Nairobi", "Africa/Nairobi"),
    ClockCity("jeddah", "Jeddah", "Asia/Riyadh"),
    ClockCity("dubai", "Dubai", "Asia/Dubai"),
    ClockCity("karachi", "Karachi", "Asia/Karachi"),
    ClockCity("mumbai", "Mumbai", "Asia/Kolkata"),
    ClockCity("dhaka", "Dhaka", "Asia/Dhaka"),
    ClockCity("bangkok", "Bangkok", "Asia/Bangkok"),
    ClockCity("jakarta", "Jakarta", "Asia/Jakarta"),
    ClockCity("singapore", "Singapore", "Asia/Singapore"),
    ClockCity("hong_kong", "Hong Kong", "Asia/Hong_Kong"),
    ClockCity("shanghai", "Shanghai", "Asia/Shanghai"),
    ClockCity("tokyo", "Tokyo", "Asia/Tokyo"),
    ClockCity("seoul", "Seoul", "Asia/Seoul"),
    ClockCity("sydney", "Sydney", "Australia/Sydney"),
    ClockCity("auckland", "Auckland", "Pacific/Auckland"),
)

fun cityById(id: String): ClockCity? = WORLD_CITIES.firstOrNull { it.id == id }

/** Persists each widget's chosen cities keyed by its appWidgetId. */
object WorldClockStore {
    private const val PREFS = "world_clock_widgets"
    private fun key(id: Int) = "cities_$id"

    fun save(context: Context, appWidgetId: Int, cityIds: List<String>) {
        prefs(context).edit().putString(key(appWidgetId), cityIds.joinToString(",")).apply()
    }

    fun cityIds(context: Context, appWidgetId: Int): List<String> {
        val raw = prefs(context).getString(key(appWidgetId), null) ?: return emptyList()
        return raw.split(",").filter { it.isNotBlank() }
    }

    /** Cities for a widget, falling back to the device's local time zone when unconfigured. */
    fun cities(context: Context, appWidgetId: Int): List<ClockCity> {
        val chosen = cityIds(context, appWidgetId).mapNotNull(::cityById)
        return chosen.ifEmpty { listOf(localCity()) }
    }

    fun clear(context: Context, appWidgetId: Int) {
        prefs(context).edit().remove(key(appWidgetId)).apply()
    }

    private fun localCity(): ClockCity {
        val tz = TimeZone.getDefault()
        val name = tz.id.substringAfterLast('/').replace('_', ' ').ifBlank { "Local" }
        return ClockCity("local", name, tz.id)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Maps a local time of day to a background colour: night indigo → dawn → day blue → dusk. */
object TimeOfDayPalette {
    // (hourOfDay, ARGB) keyframes, interpolated linearly across the 24-hour cycle.
    private val stops = listOf(
        0f to 0xFF232358.toInt(),   // deep night
        5f to 0xFF35357F.toInt(),   // pre-dawn indigo
        6.2f to 0xFFAEB9EC.toInt(), // sunrise — quick lightening
        9f to 0xFF6E9BF0.toInt(),   // morning
        12f to 0xFF2F7BE6.toInt(),  // midday sky
        15f to 0xFF3E86EE.toInt(),  // afternoon
        17.5f to 0xFFF0A24E.toInt(),// golden hour
        19f to 0xFFE07A54.toInt(),  // sunset
        20.5f to 0xFF6E56B8.toInt(),// dusk purple
        22f to 0xFF383876.toInt(),  // night
        24f to 0xFF232358.toInt(),  // wrap to deep night
    )

    fun colorFor(hourOfDay: Int, minute: Int): Int {
        val h = (hourOfDay % 24) + (minute.coerceIn(0, 59) / 60f)
        for (i in 0 until stops.size - 1) {
            val (h0, c0) = stops[i]
            val (h1, c1) = stops[i + 1]
            if (h in h0..h1) {
                val t = if (h1 == h0) 0f else (h - h0) / (h1 - h0)
                return lerpColor(c0, c1, t)
            }
        }
        return stops.last().second
    }
}

/** White for dark backgrounds, deep navy for light ones. */
fun onColor(bg: Int): Int {
    val r = (bg shr 16 and 0xFF) / 255.0
    val g = (bg shr 8 and 0xFF) / 255.0
    val b = (bg and 0xFF) / 255.0
    val lum = 0.299 * r + 0.587 * g + 0.114 * b
    return if (lum < 0.58) 0xFFFFFFFF.toInt() else 0xFF11214A.toInt()
}

private fun lerpColor(a: Int, b: Int, t: Float): Int {
    val f = t.coerceIn(0f, 1f)
    fun ch(shift: Int): Int {
        val ca = (a shr shift) and 0xFF
        val cb = (b shr shift) and 0xFF
        return (ca + (cb - ca) * f).roundToInt().coerceIn(0, 255)
    }
    return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/** Renders the capsule background (one colour-per-city segment, rounded outer ends) as a bitmap. */
fun drawWorldClockBackground(widthPx: Int, heightPx: Int, colors: IntArray): Bitmap {
    val w = widthPx.coerceAtLeast(1)
    val h = heightPx.coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val n = colors.size.coerceAtLeast(1)
    val r = h / 2f
    val segW = w.toFloat() / n
    for (i in 0 until n) {
        paint.color = colors[i]
        val left = i * segW
        // Overlap by 1px on inner edges so anti-aliasing leaves no transparent seam.
        val right = if (i == n - 1) w.toFloat() else left + segW + 1f
        val z = 0f
        val radii = when {
            n == 1 -> floatArrayOf(r, r, r, r, r, r, r, r)
            i == 0 -> floatArrayOf(r, r, z, z, z, z, r, r)
            i == n - 1 -> floatArrayOf(z, z, r, r, r, r, z, z)
            else -> floatArrayOf(z, z, z, z, z, z, z, z)
        }
        val path = Path().apply { addRoundRect(RectF(left, 0f, right, h.toFloat()), radii, Path.Direction.CW) }
        canvas.drawPath(path, paint)
    }
    return bmp
}

/** Builds and pushes the RemoteViews for one world-clock widget instance. */
fun updateWorldClockWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
    val cities = WorldClockStore.cities(context, appWidgetId).take(3)
    val views = RemoteViews(context.packageName, R.layout.widget_world_clock)

    val cellIds = intArrayOf(R.id.wc_cell0, R.id.wc_cell1, R.id.wc_cell2)
    val cityIds = intArrayOf(R.id.wc_city0, R.id.wc_city1, R.id.wc_city2)
    val timeIds = intArrayOf(R.id.wc_time0, R.id.wc_time1, R.id.wc_time2)
    val dateIds = intArrayOf(R.id.wc_date0, R.id.wc_date1, R.id.wc_date2)

    val colors = IntArray(cities.size)
    cities.forEachIndexed { i, city ->
        val cal = Calendar.getInstance(TimeZone.getTimeZone(city.zoneId))
        val color = TimeOfDayPalette.colorFor(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        colors[i] = color
        val fg = onColor(color)
        views.setViewVisibility(cellIds[i], View.VISIBLE)
        views.setTextViewText(cityIds[i], city.name)
        views.setTextColor(cityIds[i], fg)
        views.setTextColor(timeIds[i], fg)
        views.setTextColor(dateIds[i], fg)
        // TextClock renders live time/date for the city's zone with no service needed.
        views.setString(timeIds[i], "setTimeZone", city.zoneId)
        views.setString(dateIds[i], "setTimeZone", city.zoneId)
    }
    for (i in cities.size until 3) views.setViewVisibility(cellIds[i], View.GONE)

    val (wPx, hPx) = widgetSizePx(context, manager, appWidgetId)
    views.setImageViewBitmap(R.id.wc_background, drawWorldClockBackground(wPx, hPx, colors))

    manager.updateAppWidget(appWidgetId, views)
}

/**
 * Bitmap size for the capsule: width follows the widget, height is the fixed pill height (the
 * capsule is a slim strip centred in its cell, so it must not stretch to the full cell height).
 */
private fun widgetSizePx(context: Context, manager: AppWidgetManager, appWidgetId: Int): Pair<Int, Int> {
    val density = context.resources.displayMetrics.density
    val opts = runCatching { manager.getAppWidgetOptions(appWidgetId) }.getOrNull()
    val wDp = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)?.takeIf { it > 0 } ?: 250
    val w = (wDp * density).roundToInt().coerceIn(1, 2000)
    val h = context.resources.getDimensionPixelSize(R.dimen.world_clock_pill_height).coerceIn(1, 800)
    return w to h
}
