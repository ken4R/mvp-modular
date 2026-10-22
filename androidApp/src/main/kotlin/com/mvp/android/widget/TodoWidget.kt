package com.mvp.android.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import com.mvp.android.MainActivity
import com.mvp.android.ui.theme.Radius
import com.mvp.android.ui.theme.Spacing
import com.mvp.android.ui.theme.WidgetTheme
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.todo.domain.model.TodoWidgetItem
import com.mvp.feature.todo.domain.model.TodoWidgetState
import com.mvp.feature.todo.domain.usecase.ObserveTodoWidgetUseCase
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Widget Tâches de l'écran d'accueil (ADR 0011). Il tourne dans le processus de l'app et lit les tâches
 * par le use case partagé : rien n'est recopié hors de la base de l'app. Sans session valide ou avec le
 * déverrouillage par empreinte activé, il n'affiche aucune tâche.
 */
class TodoWidget : GlanceAppWidget(), KoinComponent {

    private val observeWidget: ObserveTodoWidgetUseCase by inject()

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Lu à chaque mise à jour : TodoWidgetUpdater en relance une à chaque changement. Erreur : rien d'affiché.
        val state = (observeWidget(NoParams).first() as? AppResult.Success)?.data
        provideContent { TodoWidgetContent(state) }
    }
}

class TodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodoWidget()
}

@Composable
private fun TodoWidgetContent(state: TodoWidgetState?) {
    Column(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(WidgetTheme.surface)
            .cornerRadius(Radius.lg)
            .padding(Spacing.md),
    ) {
        when (state) {
            is TodoWidgetState.Tasks -> Tasks(state)
            TodoWidgetState.SignedOut -> Message("Connectez-vous pour voir vos tâches ici.")
            TodoWidgetState.Locked -> Message("Tâches masquées : le déverrouillage par empreinte est activé. Touchez pour ouvrir l'app.")
            null -> Message("Ouvrez l'app pour voir vos tâches.")
        }
    }
}

@Composable
private fun Tasks(state: TodoWidgetState.Tasks) {
    val context = LocalContext.current
    Text(
        text = if (state.remainingCount == 0) "Tout est fait" else "À faire · ${state.remainingCount}",
        style = WidgetTheme.title,
        modifier = GlanceModifier.fillMaxWidth().clickable(openLink(context, state.link)),
    )
    Spacer(GlanceModifier.height(Spacing.sm))
    LazyColumn(GlanceModifier.fillMaxSize()) {
        items(state.items, itemId = { it.id.hashCode().toLong() }) { item -> TaskRow(item) }
    }
}

@Composable
private fun TaskRow(item: TodoWidgetItem) {
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxWidth().padding(vertical = Spacing.xs).clickable(openLink(context, item.link))) {
        Text(item.title, style = WidgetTheme.body, maxLines = 1)
        item.reminderAt?.let { at ->
            Row { Text("Rappel ${reminderTime(context, at)}", style = WidgetTheme.caption, maxLines = 1) }
        }
    }
}

@Composable
private fun Message(text: String) {
    val context = LocalContext.current
    val openApp = actionStartActivity(Intent(context, MainActivity::class.java))
    Text(text, style = WidgetTheme.body, modifier = GlanceModifier.fillMaxSize().clickable(openApp))
}

/**
 * Lien profond vers l'app elle-même, par un intent explicite : aucune autre app déclarant le schéma
 * mvpmodular ne peut le recevoir. MainActivity le traite comme les autres liens (connexion et verrou d'abord).
 */
private fun openLink(context: Context, link: String): Action =
    actionStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link), context, MainActivity::class.java))

private fun reminderTime(context: Context, at: Long): String {
    val flags = if (DateUtils.isToday(at)) {
        DateUtils.FORMAT_SHOW_TIME
    } else {
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_TIME
    }
    return DateUtils.formatDateTime(context, at, flags)
}
