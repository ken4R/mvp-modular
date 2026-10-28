package com.mvp.android.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.Text
import com.mvp.android.MainActivity
import com.mvp.android.ui.theme.Radius
import com.mvp.android.ui.theme.Spacing
import com.mvp.android.ui.theme.WidgetTheme
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.todo.domain.model.TodoWidgetItem
import com.mvp.feature.todo.domain.model.TodoWidgetState
import com.mvp.feature.todo.domain.usecase.CompleteTodoFromWidgetUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodoWidgetUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Widget Tâches de l'écran d'accueil (ADR 0011). Il tourne dans le processus de l'app et lit les tâches
 * par le use case partagé : rien n'est recopié hors de la base de l'app. Sans session valide ou avec le
 * déverrouillage par empreinte activé, il n'affiche aucune tâche. La case de chaque ligne termine la tâche
 * (ADR 0026) : la tâche quitte le widget dès que la base a changé.
 */
class TodoWidget : GlanceAppWidget(), KoinComponent {

    private val observeWidget: ObserveTodoWidgetUseCase by inject()

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val states = observeWidget(NoParams).map { (it as? AppResult.Success)?.data }
        // Premier état lu avant d'afficher, puis suivi tant que la session Glance reste ouverte : un updateAll
        // reçu pendant ce temps ne rappelle pas provideGlance. Erreur : rien d'affiché.
        val initial = states.first()
        provideContent {
            val state by remember { states }.collectAsState(initial)
            TodoWidgetContent(state)
        }
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
    Row(GlanceModifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        CheckBox(
            checked = false,
            onCheckedChange = actionRunCallback<CompleteTodoAction>(actionParametersOf(CompleteTodoAction.todoId to item.id)),
            colors = CheckboxDefaults.colors(checkedColor = WidgetTheme.primary, uncheckedColor = WidgetTheme.onSurface),
            modifier = GlanceModifier.semantics { contentDescription = "Terminer « ${item.title} »" },
        )
        Column(GlanceModifier.defaultWeight().clickable(openLink(context, item.link))) {
            Text(item.title, style = WidgetTheme.body, maxLines = 1)
            item.reminderAt?.let { at ->
                Text("Rappel ${reminderTime(context, at)}", style = WidgetTheme.caption, maxLines = 1)
            }
        }
    }
}

/**
 * Case cochée dans le widget. Glance la reçoit par son propre récepteur, non exporté, avec un PendingIntent
 * explicite : une autre app ne peut pas la déclencher. Le use case partagé vérifie quand même la session et le
 * verrou (un widget pas encore redessiné peut montrer une tâche qu'il ne devrait plus montrer) ; la modification
 * part au serveur par la file d'envoi. Le widget suit la base : la tâche terminée disparaît de la liste.
 */
class CompleteTodoAction : ActionCallback, KoinComponent {

    private val complete: CompleteTodoFromWidgetUseCase by inject()

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[todoId] ?: return
        val result = complete(id)
        if (result is AppResult.Failure) {
            // Le type d'erreur seulement : le message d'une erreur inconnue peut venir de la base.
            Log.w("TodoWidget", "Tâche non terminée depuis le widget : ${result.error::class.simpleName}")
            // Le système a déjà coché la case ; rien n'a changé en base, donc rien ne la redessinerait.
            TodoWidget().update(context, glanceId)
        }
    }

    companion object {
        val todoId = ActionParameters.Key<String>("todoId")
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
