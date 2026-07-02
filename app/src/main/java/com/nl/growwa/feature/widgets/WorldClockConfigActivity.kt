package com.nl.growwa.feature.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nl.growwa.feature.components.SearchField
import com.nl.growwa.ui.theme.GrowwaTheme
import java.util.Calendar
import java.util.TimeZone

private const val MAX_CITIES = 3

/** Configuration screen launched when a world-clock widget is added or re-configured. */
class WorldClockConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Default result cancels the add if the user backs out.
        setResult(Activity.RESULT_CANCELED, resultIntent(appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            GrowwaTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    WorldClockConfigScreen(
                        initial = WorldClockStore.cityIds(this, appWidgetId),
                        onCancel = { finish() },
                        onSave = { chosen ->
                            WorldClockStore.save(this, appWidgetId, chosen)
                            updateWorldClockWidget(this, AppWidgetManager.getInstance(this), appWidgetId)
                            setResult(Activity.RESULT_OK, resultIntent(appWidgetId))
                            finish()
                        },
                    )
                }
            }
        }
    }

    private fun resultIntent(appWidgetId: Int) =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

@Composable
private fun WorldClockConfigScreen(
    initial: List<String>,
    onCancel: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // Ordered selection (max 3); order determines left → right segment placement.
    val selected = remember { mutableStateListOf<String>().apply { addAll(initial.take(MAX_CITIES)) } }

    val q = query.trim()
    val shown = remember(query) {
        if (q.isBlank()) WORLD_CITIES
        else WORLD_CITIES.filter { it.name.contains(q, ignoreCase = true) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Text(
            "World clock",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp),
        )
        Text(
            "Choose up to $MAX_CITIES cities. Each segment is tinted by that city's local time.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        SearchField(
            query = query,
            onQueryChange = { query = it },
            hint = "Search cities",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) {
            items(shown, key = { it.id }) { city ->
                val index = selected.indexOf(city.id)
                CityRow(
                    city = city,
                    order = if (index >= 0) index + 1 else null,
                    enabled = index >= 0 || selected.size < MAX_CITIES,
                    onToggle = {
                        if (index >= 0) selected.removeAt(index)
                        else if (selected.size < MAX_CITIES) selected.add(city.id)
                    },
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { onSave(selected.toList()) },
                enabled = selected.isNotEmpty(),
            ) { Text("Add widget") }
        }
    }
}

@Composable
private fun CityRow(
    city: ClockCity,
    order: Int?,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val selected = order != null
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else Color.Transparent,
            )
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Live preview swatch of the city's current time-of-day colour.
        val cal = Calendar.getInstance(TimeZone.getTimeZone(city.zoneId))
        val swatch = TimeOfDayPalette.colorFor(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(swatch)),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                city.name,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                localTimeLabel(city.zoneId),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (order != null) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

private fun localTimeLabel(zoneId: String): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone(zoneId))
    return "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}
