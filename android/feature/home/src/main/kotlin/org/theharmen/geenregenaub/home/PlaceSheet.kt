package org.theharmen.geenregenaub.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.theharmen.geenregenaub.data.LocationSettings
import org.theharmen.geenregenaub.data.Place

/** Bottom sheet to follow the phone's location or pick a fixed place by search. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaceSheet(
    settings: LocationSettings,
    query: String,
    results: SearchResults,
    onQueryChange: (String) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onChoose: (Place) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.places_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.places_current)) },
                supportingContent = { Text(stringResource(R.string.places_current_detail)) },
                leadingContent = { Icon(painterResource(R.drawable.ic_my_location), contentDescription = null) },
                modifier = Modifier.clickable(onClick = onUseCurrentLocation),
            )
            if (!settings.useCurrentLocation) {
                settings.chosenPlace?.let { place ->
                    PlaceRow(place, onChoose)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(R.string.places_search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )
            when (results) {
                SearchResults.Idle -> Unit
                SearchResults.Failed -> Hint(stringResource(R.string.places_failed))
                is SearchResults.Found ->
                    if (results.places.isEmpty()) {
                        Hint(stringResource(R.string.places_none))
                    } else {
                        LazyColumn(Modifier.heightIn(max = 360.dp)) {
                            items(results.places, key = { "${it.name}|${it.lat}|${it.lon}" }) { PlaceRow(it, onChoose) }
                        }
                    }
            }
            Hint(stringResource(R.string.places_source))
        }
    }
}

@Composable
private fun PlaceRow(place: Place, onChoose: (Place) -> Unit) {
    ListItem(
        headlineContent = { Text(place.shortName()) },
        supportingContent = { Text(place.name) },
        leadingContent = { Icon(painterResource(R.drawable.ic_place), contentDescription = null) },
        modifier = Modifier.clickable { onChoose(place) },
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    )
}
