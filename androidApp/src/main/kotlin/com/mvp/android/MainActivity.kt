package com.mvp.android

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.mvp.android.ui.applock.AppLockViewModel
import com.mvp.android.ui.applock.LockScreen
import com.mvp.android.ui.auth.LoginScreen
import com.mvp.android.ui.auth.SessionUiState
import com.mvp.android.ui.auth.SessionViewModel
import com.mvp.android.ui.catalog.CatalogScreen
import com.mvp.android.ui.deeplink.DeepLinkViewModel
import com.mvp.android.ui.settings.SettingsScreen
import com.mvp.android.ui.theme.MvpTheme
import com.mvp.android.ui.todo.TodoScreen
import com.mvp.android.ui.weather.WeatherScreen
import com.mvp.core.deeplink.domain.model.DeepLink
import org.koin.androidx.compose.koinViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

private enum class Tab(val label: String) { Todo("Tâches"), Weather("Météo"), Catalog("Catalogue"), Settings("Réglages") }

class MainActivity : ComponentActivity() {

    private val appLockViewModel: AppLockViewModel by viewModel()
    private val deepLinkViewModel: DeepLinkViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Après une rotation, l'intent de départ est le même : son lien a déjà été pris en compte.
        if (savedInstanceState == null) handleDeepLink(intent)
        enableEdgeToEdge()
        setContent {
            MvpTheme {
                val sessionViewModel = koinViewModel<SessionViewModel>()
                val session by sessionViewModel.state.collectAsStateWithLifecycle()
                // Collecté aussi en arrière-plan : l'écran verrouillé est prêt avant que l'app ne réapparaisse.
                val appLock by appLockViewModel.state.collectAsState()
                LaunchedEffect(appLock.enabled) { hideFromRecents(appLock.enabled) }
                // Sans session, seul l'écran de connexion est accessible.
                when (session) {
                    SessionUiState.Loading -> Blank()
                    SessionUiState.LoggedOut -> LoginScreen()
                    // Verrou Face ID / empreinte : rien de l'app n'est composé tant qu'il n'est pas levé.
                    is SessionUiState.LoggedIn -> if (!appLock.loaded) {
                        Blank()
                    } else if (appLock.locked) {
                        LockScreen(appLock, appLockViewModel::consumeAutoPrompt, appLockViewModel::onAuthResult, appLockViewModel::onLogout)
                    } else {
                        // Les ViewModels des onglets sont liés à la session : neufs à chaque connexion.
                        CompositionLocalProvider(LocalViewModelStoreOwner provides sessionViewModel.screens) {
                            AppTabs(deepLinkViewModel)
                        }
                    }
                }
            }
        }
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
        if (intent?.action != Intent.ACTION_VIEW) return
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        intent.data?.toString()?.let(deepLinkViewModel::open)
    }

    override fun onStop() {
        super.onStop()
        // Une rotation recrée l'activité sans quitter l'app : pas de verrouillage dans ce cas.
        if (!isChangingConfigurations) appLockViewModel.onBackground()
    }

    /** Verrou activé : l'aperçu de l'app dans les apps récentes ne montre pas son contenu. */
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
}

@Composable
private fun Blank() = Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))

@Composable
private fun AppTabs(deepLinks: DeepLinkViewModel) {
    // Revient sur « Tâches » à chaque nouvelle connexion.
    var tab by rememberSaveable { mutableStateOf(Tab.Todo) }
    // Tâche ou produit demandé par le dernier lien, transmis à l'écran de l'onglet qui l'affiche.
    var todoRequest by remember { mutableStateOf<String?>(null) }
    var productRequest by remember { mutableStateOf<Long?>(null) }
    val pendingLink by deepLinks.pending.collectAsStateWithLifecycle()
    // Ces onglets ne sont composés qu'une fois connecté et déverrouillé : un lien reçu avant attend jusque-là.
    LaunchedEffect(pendingLink) {
        val link = pendingLink ?: return@LaunchedEffect
        when (link) {
            is DeepLink.Tasks -> {
                tab = Tab.Todo
                todoRequest = link.todoId
            }
            DeepLink.Weather -> tab = Tab.Weather
            is DeepLink.Catalog -> {
                tab = Tab.Catalog
                productRequest = link.productId
            }
            DeepLink.Settings -> tab = Tab.Settings
        }
        deepLinks.consume(link)
    }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
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
                Tab.Todo -> TodoScreen(focusRequest = todoRequest, onFocusRequestHandled = { todoRequest = null })
                Tab.Weather -> WeatherScreen()
                Tab.Catalog -> CatalogScreen(productRequest = productRequest, onProductRequestHandled = { productRequest = null })
                Tab.Settings -> SettingsScreen()
            }
        }
    }
}
