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
 * Bottom sheet in three parts: the phone's location, the saved places, and finding another place
 * (on the map, or by search). Tapping a place shows it; the star marks the default place, the one
 * the app always comes back to. The place showing now is highlighted.
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
    onSaveFavorite: (place: Place, name: String, show: Boolean) -> Unit,
    onRemoveFavorite: (Place) -> Unit,
    onDismiss: () -> Unit,
    /** A place shown for a while instead of the default, if any. */
    temporary: Place? = null,
    /** Makes a place the default; null makes it the phone's location. */
    onSetDefault: (Place?) -> Unit = {},
    /** Saves a place that isn't saved yet and makes it the default (its star). */
    onSaveAsDefault: (Place) -> Unit = {},
    /** Opens the radar map to pick a point; null hides the option. */
    onPickOnMap: (() -> Unit)? = null,
) {
    var naming by remember { mutableStateOf<NameRequest?>(null) }
    val defaultPlace = settings.chosenPlace?.takeIf { !settings.useCurrentLocation }
    val showingHere = temporary?.isCurrentLocation ?: settings.useCurrentLocation
    val showing = if (temporary != null) temporary.takeIf { !it.isCurrentLocation } else defaultPlace

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

            SectionTitle(stringResource(R.string.places_mine))
            val hereName = stringResource(R.string.places_current)
            ListItem(
                headlineContent = { Text(hereName) },
                supportingContent = { Text(stringResource(R.string.places_current_detail)) },
                leadingContent = { Icon(painterResource(R.drawable.ic_my_location), contentDescription = null) },
                trailingContent = {
                    DefaultStar(
                        name = hereName,
                        isDefault = settings.useCurrentLocation,
                        onToggle = { if (!settings.useCurrentLocation) onSetDefault(null) },
                    )
                },
                colors = selectedColors(showingHere),
                modifier = Modifier.clickable(onClick = onUseCurrentLocation),
            )

            // Saved places, plus a default or temporary place that isn't saved yet (to save it).
            val unsaved = listOfNotNull(defaultPlace, temporary?.takeIf { !it.isCurrentLocation })
                .filter { place -> favorites.none { it.isSameSpot(place) } }
                .distinctBy { it.lat to it.lon }
            if (favorites.isNotEmpty() || unsaved.isNotEmpty()) {
                SectionTitle(stringResource(R.string.places_saved))
                favorites.forEach { place ->
                    val isDefault = defaultPlace?.isSameSpot(place) == true
                    FavoriteRow(
                        place = place,
                        selected = showing?.isSameSpot(place) == true,
                        isDefault = isDefault,
                        onChoose = onChoose,
                        onToggleDefault = { onSetDefault(if (isDefault) null else place) },
                        onRename = { naming = NameRequest(place, isRename = true) },
                        onRemove = { onRemoveFavorite(place) },
                    )
                }
                unsaved.forEach { place ->
                    val isDefault = defaultPlace?.isSameSpot(place) == true
                    PlaceRow(
                        place = place,
                        selected = showing?.isSameSpot(place) == true,
                        onChoose = onChoose,
                        onSave = { naming = NameRequest(place, isRename = false) },
                        isDefault = isDefault,
                        // Starring a place that isn't saved saves it too, so the default stays in the list.
                        onToggleDefault = { if (isDefault) onSetDefault(null) else onSaveAsDefault(place) },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 4.dp))
            SectionTitle(stringResource(R.string.places_other))
            if (onPickOnMap != null) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.places_pick_on_map)) },
                    supportingContent = { Text(stringResource(R.string.places_pick_on_map_detail)) },
                    leadingContent = { Icon(painterResource(R.drawable.ic_map), contentDescription = null) },
                    trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onPickOnMap),
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(R.string.places_search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
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
                onSaveFavorite(request.place, name, false)
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

/** Filled star: the default place. Outline: tap to make this the default. */
@Composable
private fun DefaultStar(name: String, isDefault: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            painterResource(if (isDefault) R.drawable.ic_star else R.drawable.ic_star_outline),
            contentDescription = stringResource(
                if (isDefault) R.string.places_is_default_named else R.string.places_make_default_named,
                name,
            ),
            tint = if (isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FavoriteRow(
    place: Place,
    selected: Boolean,
    isDefault: Boolean,
    onChoose: (Place) -> Unit,
    onToggleDefault: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(place.shortName()) },
        supportingContent = if (isDefault) {
            { Text(stringResource(R.string.places_default)) }
        } else {
            null
        },
        leadingContent = { Icon(painterResource(R.drawable.ic_place), contentDescription = null) },
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
                DefaultStar(place.shortName(), isDefault, onToggleDefault)
            }
        },
        colors = selectedColors(selected),
        modifier = Modifier.clickable { onChoose(place) },
    )
}

/** A place that isn't saved: tap to show it, the bookmark saves it. */
@Composable
private fun PlaceRow(
    place: Place,
    selected: Boolean,
    onChoose: (Place) -> Unit,
    onSave: () -> Unit,
    isDefault: Boolean = false,
    onToggleDefault: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(place.shortName()) },
        supportingContent = { Text(place.name) },
        leadingContent = { Icon(painterResource(R.drawable.ic_place), contentDescription = null) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSave) {
                    Icon(
                        painterResource(R.drawable.ic_bookmark_add),
                        contentDescription = stringResource(R.string.places_save_named, place.shortName()),
                    )
                }
                if (onToggleDefault != null) DefaultStar(place.shortName(), isDefault, onToggleDefault)
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
