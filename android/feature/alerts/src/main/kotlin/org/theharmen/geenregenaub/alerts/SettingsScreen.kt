package org.theharmen.geenregenaub.alerts

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.theharmen.geenregenaub.domain.AlertSettings
import org.theharmen.geenregenaub.domain.Intensity
import org.theharmen.geenregenaub.ui.theme.AppearancePreferences
import org.theharmen.geenregenaub.ui.theme.ThemeMode
import org.theharmen.geenregenaub.ui.theme.rememberAppearance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: AlertPreferences,
    resumeCount: Int,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(preferences.settings) }
    var canNotify by remember { mutableStateOf(RainAlertNotifier.canNotify(context)) }

    fun save(new: AlertSettings) {
        settings = new
        preferences.settings = new
        if (!new.enabled) preferences.lastAnnouncedStart = null
        onChanged()
    }

    // Re-check the permission when coming back from Android's settings.
    LaunchedEffect(resumeCount) { canNotify = RainAlertNotifier.canNotify(context) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = RainAlertNotifier.canNotify(context)
        if (granted) save(settings.copy(enabled = true))
    }

    fun requestPermissionOrOpenSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }

    LaunchedEffect(Unit) { RainAlertNotifier.createChannel(context) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = stringResource(R.string.settings_section_alerts)) {
                SwitchRow(
                    title = stringResource(R.string.settings_alerts_enabled),
                    detail = stringResource(R.string.settings_alerts_enabled_detail),
                    checked = settings.enabled,
                    onCheckedChange = { on ->
                        if (on && !canNotify) requestPermissionOrOpenSettings() else save(settings.copy(enabled = on))
                    },
                )

                if (settings.enabled && !canNotify) {
                    Text(
                        text = stringResource(R.string.settings_permission_needed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    FilledTonalButton(onClick = { requestPermissionOrOpenSettings() }) {
                        Text(stringResource(R.string.settings_permission_open))
                    }
                }

                HorizontalDivider()
                Label(stringResource(R.string.settings_lead_time), enabled = settings.enabled)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val options = AlertSettings.LEAD_TIME_OPTIONS
                    options.forEachIndexed { index, lead ->
                        SegmentedButton(
                            selected = settings.leadTime == lead,
                            onClick = { save(settings.copy(leadTime = lead)) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            enabled = settings.enabled,
                        ) {
                            Text(stringResource(R.string.settings_lead_minutes, lead.toMinutes().toInt()))
                        }
                    }
                }

                HorizontalDivider()
                Label(stringResource(R.string.settings_min_intensity), enabled = settings.enabled)
                Column {
                    Intensity.entries.forEach { intensity ->
                        RadioRow(
                            label = stringResource(intensityLabel(intensity)),
                            selected = settings.minIntensity == intensity,
                            enabled = settings.enabled,
                            onClick = { save(settings.copy(minIntensity = intensity)) },
                        )
                    }
                }

                HorizontalDivider()
                SwitchRow(
                    title = stringResource(R.string.settings_quiet_hours),
                    detail = stringResource(R.string.settings_quiet_hours_detail),
                    checked = settings.quietHours,
                    enabled = settings.enabled,
                    onCheckedChange = { save(settings.copy(quietHours = it)) },
                )

                if (canNotify) {
                    OutlinedButton(onClick = { RainAlertNotifier.notifyTest(context) }) {
                        Text(stringResource(R.string.settings_test))
                    }
                }
            }

            AppearanceSection()

            WidgetSection()

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Licht / donker / systeem, and whether to take colours from the wallpaper. */
@Composable
private fun AppearanceSection() {
    val context = LocalContext.current
    val prefs = remember { AppearancePreferences(context) }
    val appearance = rememberAppearance()
    SectionCard(title = stringResource(R.string.settings_section_appearance)) {
        Label(stringResource(R.string.settings_theme), enabled = true)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = listOf(
                ThemeMode.SYSTEM to R.string.settings_theme_system,
                ThemeMode.LIGHT to R.string.settings_theme_light,
                ThemeMode.DARK to R.string.settings_theme_dark,
            )
            options.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = appearance.mode == mode,
                    onClick = {
                        prefs.appearance = appearance.copy(mode = mode)
                        AlertsHooks.onWidgetLookChanged(context)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(stringResource(label))
                }
            }
        }
        if (AppearancePreferences.supportsDynamicColor) {
            HorizontalDivider()
            SwitchRow(
                title = stringResource(R.string.settings_dynamic_color),
                detail = stringResource(R.string.settings_dynamic_color_detail),
                checked = appearance.dynamicColor,
                onCheckedChange = {
                    prefs.appearance = appearance.copy(dynamicColor = it)
                    AlertsHooks.onWidgetLookChanged(context)
                },
            )
        }
    }
}

/** Widget background: the theme colour, see-through, or none at all. */
@Composable
private fun WidgetSection() {
    val context = LocalContext.current
    val prefs = remember { WidgetPreferences(context) }
    var background by remember { mutableStateOf(prefs.background) }
    SectionCard(title = stringResource(R.string.settings_section_widgets)) {
        Label(stringResource(R.string.settings_widget_background), enabled = true)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = listOf(
                WidgetBackground.SOLID to R.string.settings_widget_background_solid,
                WidgetBackground.TRANSLUCENT to R.string.settings_widget_background_translucent,
                WidgetBackground.TRANSPARENT to R.string.settings_widget_background_transparent,
            )
            options.forEachIndexed { index, (option, label) ->
                SegmentedButton(
                    selected = background == option,
                    onClick = {
                        background = option
                        prefs.background = option
                        AlertsHooks.onWidgetLookChanged(context)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(stringResource(label))
                }
            }
        }
        HorizontalDivider()
        Label(stringResource(R.string.settings_widget_background_color), enabled = true)
        var backgroundColor by remember { mutableStateOf(prefs.backgroundColor) }
        ColorSwatches(
            selected = backgroundColor,
            defaultLabel = stringResource(R.string.settings_widget_color_theme),
            onSelect = {
                backgroundColor = it
                prefs.backgroundColor = it
                AlertsHooks.onWidgetLookChanged(context)
            },
        )
        HorizontalDivider()
        Label(stringResource(R.string.settings_widget_text_color), enabled = true)
        var textColor by remember { mutableStateOf(prefs.textColor) }
        ColorSwatches(
            selected = textColor,
            defaultLabel = stringResource(R.string.settings_widget_text_auto),
            onSelect = {
                textColor = it
                prefs.textColor = it
                AlertsHooks.onWidgetLookChanged(context)
            },
        )
        Text(
            text = stringResource(R.string.settings_widget_background_detail),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
        var intensityLines by remember { mutableStateOf(prefs.intensityLines) }
        SwitchRow(
            title = stringResource(R.string.settings_widget_intensity_lines),
            detail = stringResource(R.string.settings_widget_intensity_lines_detail),
            checked = intensityLines,
            onCheckedChange = {
                intensityLines = it
                prefs.intensityLines = it
                AlertsHooks.onWidgetLookChanged(context)
            },
        )
    }
}

/** A default choice ("Thema" / "Automatisch") followed by round colour swatches. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSwatches(selected: Int?, defaultLabel: String, onSelect: (Int?) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(defaultLabel) },
        )
        WidgetPreferences.SWATCHES.forEach { argb ->
            val colour = Color(argb)
            val isSelected = selected == argb
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colour)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(argb) }),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Text(
                        text = "✓",
                        color = if (colour.luminance() > 0.5f) Color.Black else Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

private fun intensityLabel(intensity: Intensity): Int = when (intensity) {
    Intensity.LIGHT -> R.string.settings_intensity_light
    Intensity.MODERATE -> R.string.settings_intensity_moderate
    Intensity.HEAVY -> R.string.settings_intensity_heavy
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun Label(text: String, enabled: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
