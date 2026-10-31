package com.mvp.android

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.mvp.android.ui.analytics.AnalyticsViewModel
import com.mvp.android.ui.appearance.AppearanceViewModel
import com.mvp.android.ui.applock.AppLockViewModel
import com.mvp.android.ui.applock.LockScreen
import com.mvp.android.ui.auth.LoginScreen
import com.mvp.android.ui.auth.SessionUiState
import com.mvp.android.ui.auth.SessionViewModel
import com.mvp.android.ui.catalog.CatalogScreen
import com.mvp.android.ui.common.RecentsPrivacy
import com.mvp.android.ui.deeplink.DeepLinkViewModel
import com.mvp.android.ui.featureflags.FeatureFlagsViewModel
import com.mvp.android.ui.focus.FocusBanner
import com.mvp.android.ui.focus.FocusScreen
import com.mvp.android.ui.focus.FocusViewModel
import com.mvp.android.ui.onboarding.OnboardingScreen
import com.mvp.android.ui.onboarding.OnboardingViewModel
import com.mvp.android.ui.settings.SettingsScreen
import com.mvp.android.ui.theme.MvpTheme
import com.mvp.android.ui.theme.Spacing
import com.mvp.android.ui.todo.TodoScreen
import com.mvp.android.ui.weather.WeatherScreen
import com.mvp.core.common.domain.analytics.AnalyticsScreen
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.deeplink.domain.model.DeepLink
import com.mvp.core.designsystem.Appearance
import com.mvp.core.featureflags.domain.model.FeatureFlag
import com.mvp.feature.todo.domain.usecase.RefreshLocationRemindersAvailabilityUseCase
import com.mvp.feature.todocompose.ui.TodoComposeScreen
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.androidx.compose.koinViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

private enum class Tab(val label: String, val screen: AnalyticsScreen) {
    Todo("Tâches", AnalyticsScreen.Tasks),
    Weather("Météo", AnalyticsScreen.Weather),
    Catalog("Catalogue", AnalyticsScreen.Catalog),
    Settings("Réglages", AnalyticsScreen.Settings),
}

class MainActivity : ComponentActivity() {

    private val appLockViewModel: AppLockViewModel by viewModel()
    private val deepLinkViewModel: DeepLinkViewModel by viewModel()
    private val analyticsViewModel: AnalyticsViewModel by viewModel()
    private val appearanceViewModel: AppearanceViewModel by viewModel()
    // Présentation du premier lancement : vue une fois par appareil, pas par session (ADR 0016).
    private val onboardingViewModel: OnboardingViewModel by viewModel()
    // Feature flags (ADR 0021) : onglets affichés selon les flags, rafraîchis à chaque retour au premier plan.
    private val featureFlagsViewModel: FeatureFlagsViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Après une rotation, l'intent de départ est le même : son lien a déjà été pris en compte.
        if (savedInstanceState == null) handleDeepLink(intent)
        enableEdgeToEdge()
        setContent {
            // Thème et accent de Réglages (ADR 0015), aussi sur l'écran de connexion et l'écran verrouillé.
            val appearance by appearanceViewModel.appearance.collectAsState()
            val darkTheme = appearance.isDark(isSystemInDarkTheme())
            // Icônes de la barre d'état lisibles sur le thème choisi, même s'il n'est pas celui de l'appareil.
            // La barre de navigation garde le voile par défaut (lisible partout, Android 8 compris).
            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme })
            }
            MvpTheme(appearance) {
                val sessionViewModel = koinViewModel<SessionViewModel>()
                val session by sessionViewModel.state.collectAsStateWithLifecycle()
                // Collecté aussi en arrière-plan : l'écran verrouillé est prêt avant que l'app ne réapparaisse.
                val appLock by appLockViewModel.state.collectAsState()
                val onboardingRequired by onboardingViewModel.required.collectAsStateWithLifecycle()
                // Masqué aussi pendant l'affichage d'un écran sensible (fiche d'une tâche et ses pièces jointes).
                val sensitiveScreens by RecentsPrivacy.hasSensitiveScreen.collectAsState()
                val hidden = appLock.enabled || sensitiveScreens > 0
                LaunchedEffect(hidden) { hideFromRecents(hidden) }
                // Sans session, seul l'écran de connexion est accessible.
                when (session) {
                    SessionUiState.Loading -> Blank()
                    SessionUiState.LoggedOut -> LoginScreen()
                    // Verrou Face ID / empreinte : rien de l'app n'est composé tant qu'il n'est pas levé.
                    is SessionUiState.LoggedIn -> if (!appLock.loaded) {
                        Blank()
                    } else if (appLock.locked) {
                        LockScreen(appLock, appLockViewModel::consumeAutoPrompt, appLockViewModel::onAuthResult, appLockViewModel::onLogout)
                    } else if (onboardingRequired != false) {
                        // Après la connexion : les choix sont enregistrés pour ce compte. Un lien reçu attend les onglets.
                        if (onboardingRequired == null) Blank() else OnboardingScreen(onboardingViewModel)
                    } else {
                        // Les ViewModels des onglets sont liés à la session : neufs à chaque connexion.
                        CompositionLocalProvider(LocalViewModelStoreOwner provides sessionViewModel.screens) {
                            val flags by featureFlagsViewModel.state.collectAsStateWithLifecycle()
                            AppTabs(deepLinkViewModel, analyticsViewModel, appearance, catalogEnabled = flags.isEnabled(FeatureFlag.Catalog))
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Lancement et retour au premier plan : le distant n'est relu qu'une fois toutes les 15 minutes.
        featureFlagsViewModel.refresh()
        // Rappels par lieu (ADR 0023) : une permission de localisation accordée dans les paramètres du système ne
        // prévient pas l'app ; relue ici, les rappels sont reposés ou retirés.
        lifecycleScope.launch { get<RefreshLocationRemindersAvailabilityUseCase>()(NoParams) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    /**
     * Lien externe ou tap sur un rappel : la destination est mise en attente dans KMP, et les onglets
     * l'affichent dès qu'ils sont visibles. Rouvrir l'app depuis les apps récentes redonne l'intent
     * d'origine : son lien n'est pas rejoué.
     */
    private fun handleDeepLink(intent: Intent?) {
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.toString()?.let(deepLinkViewModel::open)
            // Raccourci « Nouvelle tâche » et App Action CREATE_THING de l'Assistant (res/xml/shortcuts.xml, ADR 0018).
            // L'activité est exportée : n'importe quelle app peut envoyer cet intent, d'où le champ seulement prérempli.
            // Des extras mal formés (Parcelable inconnu) feraient planter la lecture : ils sont ignorés.
            ACTION_NEW_TASK -> deepLinkViewModel.startDraft(runCatching { intent.getStringExtra(EXTRA_TITLE) }.getOrNull().orEmpty())
        }
    }

    override fun onStop() {
        super.onStop()
        // Une rotation recrée l'activité sans quitter l'app : pas de verrouillage dans ce cas.
        if (!isChangingConfigurations) {
            appLockViewModel.onBackground()
            analyticsViewModel.onBackground()
        }
    }

    /** Verrou activé ou écran sensible : l'aperçu de l'app dans les apps récentes ne montre pas son contenu. */
    private fun hideFromRecents(hide: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(!hide)
        } else if (hide) {
            // Avant Android 13, seul FLAG_SECURE masque l'aperçu (il bloque aussi les captures d'écran).
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private companion object {
        // Mêmes valeurs que dans res/xml/shortcuts.xml.
        const val ACTION_NEW_TASK = "com.mvp.android.action.NEW_TASK"
        const val EXTRA_TITLE = "title"
    }
}

@Composable
private fun Blank() = Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))

@Composable
private fun AppTabs(deepLinks: DeepLinkViewModel, analytics: AnalyticsViewModel, appearance: Appearance, catalogEnabled: Boolean) {
    // Revient sur « Tâches » à chaque nouvelle connexion.
    var tab by rememberSaveable { mutableStateOf(Tab.Todo) }
    // Onglet Catalogue coupé à distance (flag catalog) : retiré de la barre, et l'app quitte l'onglet s'il était ouvert.
    val tabs = Tab.entries.filter { it != Tab.Catalog || catalogEnabled }
    LaunchedEffect(catalogEnabled) { if (!catalogEnabled && tab == Tab.Catalog) tab = Tab.Todo }
    // Tâche ou produit demandé par le dernier lien, transmis à l'écran de l'onglet qui l'affiche.
    var todoRequest by remember { mutableStateOf<String?>(null) }
    var productRequest by remember { mutableStateOf<Long?>(null) }
    // Titre dicté par le raccourci vocal (ADR 0018), prérempli dans « Nouvelle tâche ».
    var draftRequest by remember { mutableStateOf<String?>(null) }
    // Onglet Tâches : écran natif (Jetpack Compose) ou version Compose Multiplatform partagée avec iOS (ADR 0010).
    var showComposeMultiplatformTodo by rememberSaveable { mutableStateOf(false) }
    // Mode focus (ADR 0028) : écran du compte à rebours à la place de la liste, bandeau sur la liste pendant une session.
    val focusViewModel = koinViewModel<FocusViewModel>()
    val focus by focusViewModel.state.collectAsStateWithLifecycle()
    var showFocus by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(focus.loaded, focus.timer == null) { if (focus.loaded && focus.timer == null) showFocus = false }
    BackHandler(enabled = tab == Tab.Todo && showComposeMultiplatformTodo) { showComposeMultiplatformTodo = false }
    val pendingLink by deepLinks.pending.collectAsStateWithLifecycle()
    // Mesuré seulement avec le consentement Analytics (vérifié dans KMP).
    LaunchedEffect(tab) { analytics.onScreenShown(tab.screen) }
    // Ces onglets ne sont composés qu'une fois connecté et déverrouillé : un lien reçu avant attend jusque-là.
    LaunchedEffect(pendingLink) {
        val link = pendingLink ?: return@LaunchedEffect
        // Comme sur iOS, tout lien referme la version Compose (la mise en évidence n'existe que dans l'écran natif).
        showComposeMultiplatformTodo = false
        when (link) {
            is DeepLink.Tasks -> {
                tab = Tab.Todo
                todoRequest = link.todoId
            }
            DeepLink.Weather -> tab = Tab.Weather
            // Catalogue coupé : le lien est ignoré, l'utilisateur reste où il est.
            is DeepLink.Catalog -> if (catalogEnabled) {
                tab = Tab.Catalog
                productRequest = link.productId
            }
            DeepLink.Settings -> tab = Tab.Settings
            is DeepLink.NewTask -> {
                tab = Tab.Todo
                draftRequest = link.title
            }
        }
        deepLinks.consume(link)
    }
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {},
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        // Les écrans ont leur propre Scaffold : on leur évite de réappliquer les insets déjà gérés ici.
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when (tab) {
                Tab.Todo -> if (showComposeMultiplatformTodo) {
                    TodoComposeScreen(onClose = { showComposeMultiplatformTodo = false }, appearance = appearance)
                } else if (showFocus && focus.timer != null) {
                    FocusScreen(focus, focusViewModel, onClose = { showFocus = false })
                } else {
                    Column(Modifier.fillMaxSize()) {
                        focus.timer?.let { timer ->
                            Box(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)) { FocusBanner(timer) { showFocus = true } }
                        }
                        Box(Modifier.weight(1f)) {
                            TodoScreen(
                                focusRequest = todoRequest,
                                onFocusRequestHandled = { todoRequest = null },
                                draftRequest = draftRequest,
                                onDraftRequestHandled = { draftRequest = null },
                                onOpenComposeMultiplatform = { showComposeMultiplatformTodo = true },
                                onStartFocusMode = { id, title -> focusViewModel.start(id, title) { showFocus = true } },
                            )
                        }
                    }
                }
                Tab.Weather -> WeatherScreen()
                Tab.Catalog -> CatalogScreen(productRequest = productRequest, onProductRequestHandled = { productRequest = null })
                Tab.Settings -> SettingsScreen()
            }
        }
    }
}
