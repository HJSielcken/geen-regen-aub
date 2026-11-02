package org.theharmen.geenregenaub.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.theharmen.geenregenaub.data.CachedForecast
import org.theharmen.geenregenaub.data.FailureReason
import org.theharmen.geenregenaub.data.LocationSettings
import org.theharmen.geenregenaub.data.Place
import org.theharmen.geenregenaub.domain.ForecastSource
import org.theharmen.geenregenaub.domain.Headline
import org.theharmen.geenregenaub.domain.Intensity
import org.theharmen.geenregenaub.domain.RainForecast
import org.theharmen.geenregenaub.domain.Verdict
import org.theharmen.geenregenaub.domain.VerdictText
import org.theharmen.geenregenaub.domain.remainingAt
import org.theharmen.geenregenaub.domain.verdict
import org.theharmen.geenregenaub.ui.theme.GeenRegenColors
import org.theharmen.geenregenaub.ui.theme.GeenRegenTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clock = DateTimeFormatter.ofPattern("HH:mm")

/** The home screen: where am I, will it rain, and the two-hour graph. */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
    viewModel: HomeViewModel = viewModel(),
    /** Extra cards at the bottom of the screen, e.g. the weather forecast summary. */
    bottomContent: @Composable () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        viewModel.refresh()
    }
    var showPlaces by remember { mutableStateOf(false) }

    // Refresh on open and every 5 minutes while the screen is visible.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.keepFresh() }
    }

    HomeContent(
        state = state,
        hasPermission = hasPermission,
        onRefresh = viewModel::refresh,
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
        onChoosePlace = { showPlaces = true },
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        bottomContent = bottomContent,
    )

    if (showPlaces) {
        PlaceSheet(
            settings = state.settings,
            query = query,
            results = results,
            onQueryChange = viewModel::onQueryChange,
            onUseCurrentLocation = {
                showPlaces = false
                viewModel.useCurrentLocation()
                if (!hasPermission) permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },
            onChoose = { place ->
                showPlaces = false
                viewModel.choose(place)
            },
            onDismiss = { showPlaces = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
    state: HomeUiState,
    hasPermission: Boolean,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onChoosePlace: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    bottomContent: @Composable () -> Unit = {},
) {
    Scaffold(modifier = modifier) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Header(state, onChoosePlace, onOpenSettings)

                val needsPlace = state.settings.useCurrentLocation && !hasPermission && state.settings.chosenPlace == null
                if (needsPlace && state.forecast == null) {
                    PermissionCard(onRequestPermission, onChoosePlace)
                }

                val forecast = state.forecast
                if (forecast != null) {
                    ForecastSection(forecast, state.now, zone)
                } else if (!needsPlace && (state.isRefreshing || !state.hasLoaded)) {
                    Loading()
                }

                state.failure?.let { ErrorCard(it, onRefresh, onChoosePlace) }

                if (forecast != null && needsPlace) {
                    PermissionCard(onRequestPermission, onChoosePlace)
                }

                bottomContent()

                Spacer(Modifier.height(8.dp))
                Text(
                    text = forecast?.attribution ?: ForecastSource.KNMI.attribution,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Header(state: HomeUiState, onChoosePlace: () -> Unit, onOpenSettings: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        val place = state.forecast?.place
        val following = state.settings.useCurrentLocation
        val label = when {
            !following -> state.settings.chosenPlace?.shortName() ?: stringResource(R.string.home_choose_place)
            place != null -> place.shortName()
            else -> stringResource(R.string.places_current)
        }
        AssistChip(
            onClick = onChoosePlace,
            label = { Text(label, maxLines = 1) },
            leadingIcon = {
                Icon(
                    painterResource(if (following) R.drawable.ic_my_location else R.drawable.ic_place),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            trailingIcon = {
                Icon(painterResource(R.drawable.ic_expand), contentDescription = null, modifier = Modifier.size(18.dp))
            },
        )
        if (onOpenSettings != null) {
            IconButton(onClick = onOpenSettings) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.home_settings))
            }
        }
    }
}

/** "Utrecht, Utrecht, Utrecht" → "Utrecht"; keeps "3511AA Utrecht" whole. */
internal fun Place.shortName(): String = name.substringBefore(',').trim()

@Composable
private fun ForecastSection(cached: CachedForecast, now: Instant, zone: ZoneId) {
    val remaining = cached.forecast.remainingAt(now)
    if (remaining == null) {
        // Everything we have is in the past: say so instead of a stale verdict.
        VerdictBlock(
            headline = stringResource(R.string.home_no_data),
            detail = updatedText(cached, now, zone),
            color = MaterialTheme.colorScheme.onSurface,
        )
        return
    }
    val verdict = remaining.verdict()
    val headline = Headline.dutch(verdict, now)
    val detail = VerdictText.dutch(verdict, zone)

    VerdictBlock(headline = headline, detail = detail, color = verdictColor(verdict))

    if (cached.isDemo) Notice(stringResource(R.string.home_demo))
    if (cached.stale) Notice(stringResource(R.string.home_stale))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_graph_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                Text(
                    text = updatedText(cached, now, zone),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            RainGraph(
                forecast = remaining,
                zone = zone,
                summary = graphSummary(remaining, headline, detail),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Legend()
        }
    }
}

@Composable
private fun VerdictBlock(headline: String, detail: String, color: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = headline,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun verdictColor(verdict: Verdict): Color = when (verdict) {
    is Verdict.Dry -> MaterialTheme.colorScheme.onSurface
    is Verdict.RainingNow, is Verdict.RainLater -> MaterialTheme.colorScheme.primary
}

@Composable
private fun updatedText(cached: CachedForecast, now: Instant, zone: ZoneId): String {
    val age = Duration.between(cached.fetchedAt, now).toMinutes()
    return if (age >= 30) {
        stringResource(R.string.home_updated_ago, age.toInt())
    } else {
        stringResource(R.string.home_updated, clock.format(cached.fetchedAt.atZone(zone)))
    }
}

private fun graphSummary(forecast: RainForecast, headline: String, detail: String): String {
    val max = forecast.intensities.maxOrNull() ?: 0.0
    return if (max < Intensity.LIGHT.minMmPerHour) {
        "$headline. $detail."
    } else {
        "$headline. $detail. Hoogste intensiteit ${mmPerHour(max)}."
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(GeenRegenColors.RainLight, "Licht")
        LegendItem(GeenRegenColors.RainModerate, "Matig")
        LegendItem(GeenRegenColors.RainHeavy, "Zwaar")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Notice(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun Loading() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 48.dp),
    ) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.home_loading), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun PermissionCard(onRequestPermission: () -> Unit, onChoosePlace: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.home_permission_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.home_permission_body), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRequestPermission) { Text(stringResource(R.string.home_permission_allow)) }
                OutlinedButton(onClick = onChoosePlace) { Text(stringResource(R.string.home_choose_place)) }
            }
        }
    }
}

@Composable
private fun ErrorCard(reason: FailureReason, onRetry: () -> Unit, onChoosePlace: () -> Unit) {
    val text = when (reason) {
        FailureReason.NO_LOCATION -> R.string.error_no_location
        FailureReason.OUTSIDE_NL -> R.string.error_outside_nl
        FailureReason.UNAUTHORIZED -> R.string.error_unauthorized
        FailureReason.RELAY_UNAVAILABLE -> R.string.error_relay
        FailureReason.NETWORK -> R.string.error_network
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            when (reason) {
                FailureReason.NO_LOCATION, FailureReason.OUTSIDE_NL ->
                    OutlinedButton(onClick = onChoosePlace) { Text(stringResource(R.string.home_choose_place)) }
                FailureReason.UNAUTHORIZED -> Unit
                else -> OutlinedButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 16.dp)) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    val issued = Instant.parse("2026-11-02T13:00:00Z")
    val values = List(7) { 0.0 } + listOf(0.3, 1.2, 3.4, 6.0, 2.1, 0.8, 0.2) + List(11) { 0.0 }
    val state = HomeUiState(
        now = issued.plusSeconds(120),
        forecast = CachedForecast(
            forecast = RainForecast(issued, Duration.ofMinutes(5), values, ForecastSource.KNMI),
            place = Place("Utrecht", 52.09, 5.12, isCurrentLocation = true),
            fetchedAt = issued.plusSeconds(60),
            stale = false,
            attribution = ForecastSource.KNMI.attribution,
        ),
        settings = LocationSettings(),
        hasLoaded = true,
    )
    GeenRegenTheme(dynamicColor = false) {
        HomeContent(state, hasPermission = true, onRefresh = {}, onRequestPermission = {}, onChoosePlace = {}, zone = ZoneId.of("Europe/Amsterdam"))
    }
}
