package org.theharmen.geenregenaub.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

/**
 * Home-screen widget. In milestone 1 it shows a placeholder; milestone 3 fills it from the
 * shared forecast cache (never the network) and adds the 2-hour graph at larger sizes.
 */
class RainWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val title = context.getString(R.string.widget_name)
        val message = context.getString(R.string.widget_no_data)
        val attribution = context.getString(R.string.widget_attribution)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)

        provideContent {
            GlanceTheme {
                Content(title, message, attribution, launch)
            }
        }
    }

    @Composable
    private fun Content(title: String, message: String, attribution: String, launch: android.content.Intent?) {
        var modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp)
        if (launch != null) modifier = modifier.clickable(actionStartActivity(launch))

        Column(modifier = modifier) {
            Text(
                text = title,
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = message,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = attribution,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
            )
        }
    }
}

class RainWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RainWidget()
}
