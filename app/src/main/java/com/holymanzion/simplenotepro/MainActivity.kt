package com.holymanzion.simplenotepro

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.core.content.IntentCompat
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import com.holymanzion.simplenotepro.lock.DeviceAuth
import com.holymanzion.simplenotepro.lock.LockScreen
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.holymanzion.simplenotepro.ui.theme.isDark
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.holymanzion.simplenotepro.data.Note
import com.holymanzion.simplenotepro.reminder.ReminderScheduler
import com.holymanzion.simplenotepro.ui.editor.EditorScreen
import com.holymanzion.simplenotepro.ui.editor.EditorViewModel
import com.holymanzion.simplenotepro.ui.home.HomeScreen
import com.holymanzion.simplenotepro.ui.home.NotesViewModel
import com.holymanzion.simplenotepro.ui.labels.LabelsScreen
import com.holymanzion.simplenotepro.ui.settings.PolicyScreen
import com.holymanzion.simplenotepro.ui.settings.SettingsScreen
import com.holymanzion.simplenotepro.ui.theme.SimpleNoteTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val LABELS = "labels"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
    const val EDITOR = "editor?${EditorViewModel.ARG_NOTE_ID}={${EditorViewModel.ARG_NOTE_ID}}" +
        "&${EditorViewModel.ARG_CHECKLIST}={${EditorViewModel.ARG_CHECKLIST}}" +
        "&${EditorViewModel.ARG_LABEL_ID}={${EditorViewModel.ARG_LABEL_ID}}"

    fun editor(noteId: Long = -1, checklist: Boolean = false, labelId: Long? = null) =
        "editor?${EditorViewModel.ARG_NOTE_ID}=$noteId&${EditorViewModel.ARG_CHECKLIST}=$checklist" +
            "&${EditorViewModel.ARG_LABEL_ID}=${labelId ?: -1}"
}

// FragmentActivity (a ComponentActivity) because the fingerprint prompt needs one.
class MainActivity : FragmentActivity() {
    private val container by lazy { (application as SimpleNoteApp).container }

    /** Editor routes requested by notifications, shortcuts or shared text. */
    private val pendingRoutes = Channel<String>(Channel.BUFFERED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            // System bar icons must follow the app's theme, not the phone's, or they
            // vanish when the two differ (e.g. light app on a dark-mode phone).
            val dark = settings.themeMode.isDark()
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            // With the app lock on, keep note contents out of screenshots and the recents screen.
            LaunchedEffect(settings.appLock) {
                if (settings.appLock) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            val locked by container.appLock.locked.collectAsStateWithLifecycle()
            SimpleNoteTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                val navController = rememberNavController()
                LaunchedEffect(navController) {
                    pendingRoutes.receiveAsFlow().collect { route ->
                        navController.popBackStack(Routes.HOME, inclusive = false)
                        navController.navigate(route)
                    }
                }
                AppNavHost(navController)
                if (locked) {
                    // Registered last, so it wins over the hidden screens' own back handlers.
                    BackHandler { moveTaskToBack(true) }
                    LockScreen(promptAutomatically = container.appLock.promptAutomatically) { onError ->
                        DeviceAuth.authenticate(
                            this@MainActivity,
                            title = "Unlock Simple Note Pro",
                            onSuccess = container.appLock::unlock,
                            onError = onError,
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.appLock.onForeground(System.currentTimeMillis())
    }

    override fun onStop() {
        super.onStop()
        // Rotation stops and restarts the activity; that isn't leaving the app.
        if (!isChangingConfigurations) container.appLock.onBackground(System.currentTimeMillis())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val noteId = intent.getLongExtra(ReminderScheduler.EXTRA_NOTE_ID, -1)
        when {
            noteId > 0 -> pendingRoutes.trySend(Routes.editor(noteId))
            intent.action == ACTION_NEW_NOTE -> pendingRoutes.trySend(Routes.editor())
            intent.action == ACTION_NEW_CHECKLIST -> pendingRoutes.trySend(Routes.editor(checklist = true))
            intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true -> {
                val image = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return
                // The read grant only lasts while this activity lives, so copy the image now.
                lifecycleScope.launch {
                    val id = container.repository.save(
                        Note(title = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty(), content = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()),
                        emptyList(),
                    )
                    runCatching { container.repository.addImage(id, image) }
                    pendingRoutes.send(Routes.editor(id))
                    container.appScope.launch { container.repository.readPendingImageText() }
                }
            }
            intent.action == Intent.ACTION_SEND && intent.type == "text/plain" -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
                if (text.isBlank() && subject.isBlank()) return
                lifecycleScope.launch {
                    val id = container.repository.save(Note(title = subject, content = text), emptyList())
                    pendingRoutes.send(Routes.editor(id))
                }
            }
        }
    }

    @Composable
    private fun AppNavHost(navController: NavHostController) {
        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                val vm: NotesViewModel = viewModel(factory = NotesViewModel.Factory)
                HomeScreen(
                    viewModel = vm,
                    onOpenNote = { navController.navigate(Routes.editor(it)) },
                    onNewNote = { checklist, labelId -> navController.navigate(Routes.editor(checklist = checklist, labelId = labelId)) },
                    onEditLabels = { navController.navigate(Routes.LABELS) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(
                Routes.EDITOR,
                arguments = listOf(
                    navArgument(EditorViewModel.ARG_NOTE_ID) { type = NavType.LongType; defaultValue = -1L },
                    navArgument(EditorViewModel.ARG_CHECKLIST) { type = NavType.BoolType; defaultValue = false },
                    navArgument(EditorViewModel.ARG_LABEL_ID) { type = NavType.LongType; defaultValue = -1L },
                ),
            ) {
                val vm: EditorViewModel = viewModel(factory = EditorViewModel.Factory)
                EditorScreen(
                    viewModel = vm,
                    onBack = { navController.navigateUp() },
                    onOpenNote = { id ->
                        navController.popBackStack()
                        navController.navigate(Routes.editor(id))
                    },
                )
            }
            composable(Routes.LABELS) {
                LabelsScreen(repository = container.repository, onBack = { navController.navigateUp() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    container = container,
                    onBack = { navController.navigateUp() },
                    onOpenPrivacyPolicy = { navController.navigate(Routes.PRIVACY) },
                )
            }
            composable(Routes.PRIVACY) {
                PolicyScreen(onBack = { navController.navigateUp() })
            }
        }
    }

    companion object {
        const val ACTION_NEW_NOTE = "com.holymanzion.simplenotepro.NEW_NOTE"
        const val ACTION_NEW_CHECKLIST = "com.holymanzion.simplenotepro.NEW_CHECKLIST"

        // Same scrims androidx.activity uses by default for 3-button navigation.
        private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
