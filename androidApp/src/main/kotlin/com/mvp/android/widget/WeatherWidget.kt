package com.mvp.android.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import com.mvp.android.MainActivity
import com.mvp.android.ui.theme.Radius
import com.mvp.android.ui.theme.Spacing
import com.mvp.android.ui.theme.WidgetTheme
import com.mvp.android.ui.weather.appearance
import com.mvp.android.ui.weather.celsius
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.weather.domain.model.WeatherWidgetState
import com.mvp.feature.weather.domain.usecase.ObserveWeatherWidgetUseCase
import com.mvp.feature.weather.domain.usecase.RefreshWeatherWidgetUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Widget Météo de l'écran d'accueil (ADR 0026) : la dernière météo « Ma position » obtenue par l'app. Il ne demande
 * jamais la position : à chaque mise à jour (toutes les 30 min, le minimum d'Android), il actualise la météo de la
 * position déjà gardée (arrondie à environ 1 km) par le use case partagé. Sans session valide ou avec une météo de
 * plus de 3 h, il n'affiche aucune météo.
 */
class WeatherWidget : GlanceAppWidget(), KoinComponent {

    private val observeWidget: ObserveWeatherWidgetUseCase by inject()
    private val refresh: RefreshWeatherWidgetUseCase by inject()

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val states = observeWidget(NoParams).map { (it as? AppResult.Success)?.data }
        val initial = states.first()
        provideContent {
            // Après le premier affichage : hors ligne ou serveur lent, l'ancienne météo reste visible en attendant.
            // Relancé à chaque redessin (updateAll compris) : KMP n'appelle le serveur qu'une fois par quart d'heure,
            // donc pas de boucle écriture → redessin → actualisation.
            LaunchedEffect(Unit) { refresh(NoParams) }
            val state by remember { states }.collectAsState(initial)
            WeatherWidgetContent(state)
        }
    }
}

class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()
}

@Composable
private fun WeatherWidgetContent(state: WeatherWidgetState?) {
    val context = LocalContext.current
    // Intent explicite vers l'app elle-même : le lien passe par la connexion et le verrou, comme tout lien.
    val link = (state as? WeatherWidgetState.Weather)?.link
    val open = if (link != null) {
        actionStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link), context, MainActivity::class.java))
    } else {
        actionStartActivity(Intent(context, MainActivity::class.java))
    }
    Column(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(WidgetTheme.surface)
            .cornerRadius(Radius.lg)
            .padding(Spacing.md)
            .clickable(open),
    ) {
        when (state) {
            is WeatherWidgetState.Weather -> Weather(state)
            WeatherWidgetState.SignedOut -> Message("Connectez-vous pour voir la météo ici.")
            WeatherWidgetState.NoData, null -> Message("Ouvrez Météo et touchez « Ma position » pour l'afficher ici.")
        }
    }
}

@Composable
private fun Weather(state: WeatherWidgetState.Weather) {
    val context = LocalContext.current
    val (symbol, label) = state.condition.appearance(state.isDay)
    Text("Ma position", style = WidgetTheme.title, maxLines = 1)
    Spacer(GlanceModifier.height(Spacing.xs))
    Text("$symbol ${celsius(state.temperatureCelsius)}", style = WidgetTheme.headline, maxLines = 1)
    Text(label, style = WidgetTheme.body, maxLines = 1)
    Text(
        "Mise à jour ${DateUtils.formatDateTime(context, state.updatedAt, DateUtils.FORMAT_SHOW_TIME)}",
        style = WidgetTheme.caption,
        maxLines = 1,
    )
}

@Composable
private fun Message(text: String) {
    Text("Météo", style = WidgetTheme.title, maxLines = 1)
    Spacer(GlanceModifier.height(Spacing.xs))
    Text(text, style = WidgetTheme.body)
}
