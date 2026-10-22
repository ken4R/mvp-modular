package com.mvp.android.ui.weather

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mvp.android.ui.common.StatusBanner
import com.mvp.android.ui.theme.Spacing
import com.mvp.feature.weather.domain.model.City
import com.mvp.feature.weather.domain.model.CurrentWeather
import com.mvp.feature.weather.domain.model.WeatherCondition
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

@Composable
fun WeatherScreen(viewModel: WeatherViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Position approximative seulement : suffisante pour la météo, et moins intrusive.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onLocate() else viewModel.onLocationPermissionDenied()
    }
    val onMyPosition = {
        val granted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.onLocate() else permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    // Après un refus définitif, Android ne montre plus la demande : seul l'écran de l'app dans les paramètres permet d'autoriser.
    val openAppSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    WeatherContentView(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onSearch = viewModel::onSearch,
        onMyPosition = onMyPosition,
        onSelect = viewModel::onSelect,
        onRefresh = viewModel::onRefresh,
        onOpenSettings = openAppSettings,
    )
}

@Composable
private fun WeatherContentView(
    state: WeatherUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onMyPosition: () -> Unit,
    onSelect: (City) -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val search = {
        focusManager.clearFocus()
        onSearch()
    }
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text("Météo", style = MaterialTheme.typography.headlineSmall)
            if (!state.isOnline) StatusBanner("Hors ligne · la météo s'affichera au retour du réseau")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    label = { Text("Rechercher une ville") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = search) { Text("Rechercher") }
            }
            OutlinedButton(
                onClick = {
                    focusManager.clearFocus()
                    onMyPosition()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Météo à ma position") }

            when (val content = state.content) {
                WeatherContent.Idle -> Text(
                    "Cherchez une ville, ou utilisez votre position, pour voir la météo actuelle.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                WeatherContent.Loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is WeatherContent.Results -> CityList(content.cities, onSelect)
                is WeatherContent.Weather -> WeatherCard(content.place, content.weather, onRefresh)
                is WeatherContent.Error -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(content.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                    if (content.canOpenSettings) {
                        TextButton(onClick = onOpenSettings) { Text("Ouvrir les paramètres") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CityList(cities: List<City>, onSelect: (City) -> Unit) {
    if (cities.isEmpty()) {
        Text("Aucune ville trouvée", style = MaterialTheme.typography.bodyLarge)
        return
    }
    LazyColumn {
        items(cities, key = { it.id }) { city ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(city) }
                    .padding(vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(city.name, style = MaterialTheme.typography.bodyLarge)
                city.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun WeatherCard(place: WeatherPlace, weather: CurrentWeather, onRefresh: () -> Unit) {
    val (symbol, label) = weather.condition.appearance(weather.isDay)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            when (place) {
                is WeatherPlace.OfCity -> {
                    Text(place.city.name, style = MaterialTheme.typography.titleMedium)
                    place.city.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                WeatherPlace.MyPosition -> {
                    Text("Ma position", style = MaterialTheme.typography.titleMedium)
                    Text("Position approximative (environ 1 km)", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(symbol, style = MaterialTheme.typography.headlineSmall)
                Text(celsius(weather.temperatureCelsius), style = MaterialTheme.typography.headlineSmall)
            }
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Detail("Ressenti", celsius(weather.feelsLikeCelsius))
            Detail("Humidité", "${weather.humidityPercent} %")
            Detail("Vent", "${weather.windSpeedKmh.roundToInt()} km/h")
            TextButton(onClick = onRefresh) { Text("Actualiser") }
        }
    }
}

@Composable
private fun Detail(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

private fun celsius(value: Double) = "${value.roundToInt()} °C"

/** Symbole et libellé d'une condition météo KMP (mêmes libellés que sur iOS). */
private fun WeatherCondition.appearance(isDay: Boolean): Pair<String, String> = when (this) {
    WeatherCondition.Clear -> (if (isDay) "☀️" else "🌙") to "Ciel dégagé"
    WeatherCondition.PartlyCloudy -> (if (isDay) "⛅" else "☁️") to "Partiellement nuageux"
    WeatherCondition.Cloudy -> "☁️" to "Couvert"
    WeatherCondition.Fog -> "🌫️" to "Brouillard"
    WeatherCondition.Drizzle -> "🌦️" to "Bruine"
    WeatherCondition.Rain -> "🌧️" to "Pluie"
    WeatherCondition.Snow -> "❄️" to "Neige"
    WeatherCondition.Thunderstorm -> "⛈️" to "Orage"
    WeatherCondition.Unknown -> "❔" to "Conditions inconnues"
}

/** « Région, Pays », ou null si aucun des deux n'est connu. */
private val City.subtitle: String?
    get() = listOfNotNull(region, country).takeIf { it.isNotEmpty() }?.joinToString(", ")
