package org.theharmen.geenregenaub.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import org.theharmen.geenregenaub.data.LocationSettings
import org.theharmen.geenregenaub.data.Place
import org.theharmen.geenregenaub.data.isSameSpot

/** What the name dialog is for: saving a new favourite, or renaming one. */
private data class NameRequest(val place: Place, val isRename: Boolean)

/**
 * Bottom sheet to follow the phone's location, pick one of the saved places, or find a new place
 * by search. The star on a search result saves it as a favourite.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaceSheet(
    settings: LocationSettings,
    favorites: List<Place>,
    query: String,
    results: SearchResults,
    onQueryChange: (String) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onChoose: (Place) -> Unit,
    onSaveFavorite: (place: Place, name: String, choose: Boolean) -> Unit,
    onRemoveFavorite: (Place) -> Unit,
    onDismiss: () -> Unit,
) {
    var naming by remember { mutableStateOf<NameRequest?>(null) }
    val chosen = settings.chosenPlace?.takeIf { !settings.useCurrentLocation }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(R.string.places_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.places_current)) },
                supportingContent = { Text(stringResource(R.string.places_current_detail)) },
                leadingContent = { Icon(painterResource(R.drawable.ic_my_location), contentDescription = null) },
                colors = selectedColors(settings.useCurrentLocation),
                modifier = Modifier.clickable(onClick = onUseCurrentLocation),
            )

            if (favorites.isNotEmpty()) {
                SectionTitle(stringResource(R.string.places_saved))
                favorites.forEach { place ->
                    FavoriteRow(
                        place = place,
                        selected = chosen?.isSameSpot(place) == true,
                        onChoose = onChoose,
                        onRename = { naming = NameRequest(place, isRename = true) },
                        onRemove = { onRemoveFavorite(place) },
                    )
                }
            }

            // A chosen place that isn't saved yet: offer to save it.
            if (chosen != null && favorites.none { it.isSameSpot(chosen) }) {
                PlaceRow(
                    place = chosen,
                    selected = true,
                    onChoose = onChoose,
                    onSave = { naming = NameRequest(chosen, isRename = false) },
                )
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
                        // At most 8 results, so a plain column inside the scrolling sheet is fine.
                        results.places.forEach { place ->
                            PlaceRow(
                                place = place,
                                selected = false,
                                onChoose = onChoose,
                                onSave = { naming = NameRequest(place, isRename = false) },
                            )
                        }
                    }
            }
            Hint(stringResource(R.string.places_source))
        }
    }

    naming?.let { request ->
        NameDialog(
            initial = request.place.shortName(),
            isRename = request.isRename,
            onConfirm = { name ->
                naming = null
                onSaveFavorite(request.place, name, !request.isRename)
            },
            onDismiss = { naming = null },
        )
    }
}

@Composable
private fun selectedColors(selected: Boolean) =
    if (selected) {
        ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    } else {
        ListItemDefaults.colors()
    }

@Composable
private fun FavoriteRow(
    place: Place,
    selected: Boolean,
    onChoose: (Place) -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(place.shortName()) },
        leadingContent = { Icon(painterResource(R.drawable.ic_star), contentDescription = null) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRename) {
                    Icon(
                        painterResource(R.drawable.ic_edit),
                        contentDescription = stringResource(R.string.places_rename_named, place.shortName()),
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.places_remove_named, place.shortName()),
                    )
                }
            }
        },
        colors = selectedColors(selected),
        modifier = Modifier.clickable { onChoose(place) },
    )
}

@Composable
private fun PlaceRow(place: Place, selected: Boolean, onChoose: (Place) -> Unit, onSave: () -> Unit) {
    ListItem(
        headlineContent = { Text(place.shortName()) },
        supportingContent = { Text(place.name) },
        leadingContent = { Icon(painterResource(R.drawable.ic_place), contentDescription = null) },
        trailingContent = {
            IconButton(onClick = onSave) {
                Icon(
                    painterResource(R.drawable.ic_star_outline),
                    contentDescription = stringResource(R.string.places_save_named, place.shortName()),
                )
            }
        },
        colors = selectedColors(selected),
        modifier = Modifier.clickable { onChoose(place) },
    )
}

@Composable
private fun NameDialog(initial: String, isRename: Boolean, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isRename) R.string.places_rename_title else R.string.places_save_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text(stringResource(R.string.places_name_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onConfirm(name) }),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text(stringResource(R.string.places_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp)
            .semantics { heading() },
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
