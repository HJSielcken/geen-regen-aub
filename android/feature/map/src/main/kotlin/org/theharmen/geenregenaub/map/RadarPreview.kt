package org.theharmen.geenregenaub.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.theharmen.geenregenaub.ui.theme.isAppInDarkTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clock = DateTimeFormatter.ofPattern("HH:mm")

/**
 * A small, looping radar map for the home screen. Tap it to open the full radar map.
 * Shows nothing in builds without a relay key.
 */
@Composable
fun RadarPreview(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: RadarMapViewModel = viewModel(key = "radar-preview")
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.unavailable) return

    LaunchedEffect(state.isReady) { if (state.isReady) viewModel.autoplay() }

    val openLabel = stringResource(R.string.map_preview_open)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp),
        ) {
            RadarMap(state, isAppInDarkTheme(), Modifier.fillMaxSize(), interactive = false)
            // Catches taps, since the map view would otherwise swallow them.
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen),
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    val frames = state.frames
                    val frame = frames?.frames?.getOrNull(state.index)
                    if (frames == null || frame == null || !state.isReady) {
                        if (state.loading) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text(
                                "  " + stringResource(R.string.map_preview_title),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        } else {
                            Text(stringResource(R.string.map_preview_title), style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        Text(
                            clock.format(frame.time.atZone(ZoneId.systemDefault())) + "  ",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            frameLabel(frame.time, frame.observed, frames.issuedAt),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
