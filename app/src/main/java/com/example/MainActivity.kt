package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.datastore.MainTabId
import com.example.core.designsystem.DialMeBackground
import com.example.core.designsystem.DialMeCard
import com.example.core.designsystem.DialMeTheme
import com.example.core.permissions.PermissionHelper
import com.example.data.CallReminderWorker
import com.example.domain.model.ActiveCallSession
import com.example.domain.model.CallStateType
import com.example.domain.model.CallerVerificationStatus
import com.example.domain.model.InCallUiState
import com.example.feature.automation.AutomationAndRemindersScreen
import com.example.feature.backup.BackupAndRestoreScreen
import com.example.feature.callblocking.BlockingAndPrivacyScreen
import com.example.feature.callhistory.CallHistoryScreen
import com.example.feature.common.DialMeMainViewModel
import com.example.feature.contacts.ContactsScreen
import com.example.feature.customization.CustomizationScreen
import com.example.feature.dialpad.DialpadScreen
import com.example.feature.incall.InCallScreen
import com.example.feature.settings.SettingsScreen
import com.example.feature.statistics.StatisticsScreen
import kotlinx.serialization.Serializable

@Serializable
sealed interface DialMeRoute {
    @Serializable data object Favorites : DialMeRoute
    @Serializable data object Contacts : DialMeRoute
    @Serializable data object Recents : DialMeRoute
    @Serializable data object Dialpad : DialMeRoute
    @Serializable data object Settings : DialMeRoute
    @Serializable data class Customization(val section: String = "theme") : DialMeRoute
    @Serializable data class BlockingPrivacy(val tab: Int = 0) : DialMeRoute
    @Serializable data object Statistics : DialMeRoute
    @Serializable data object Automation : DialMeRoute
    @Serializable data object Backup : DialMeRoute
    @Serializable data object CallerPreview : DialMeRoute
}

class MainActivity : AppCompatActivity() {

    private var mainViewModelRef: DialMeMainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as DialMeApplication).container

        setContent {
            val viewModel: DialMeMainViewModel = viewModel(factory = DialMeMainViewModel.factory(container))
            mainViewModelRef = viewModel

            LaunchedEffect(intent) {
                handleIncomingIntent(intent, viewModel)
            }

            val config by viewModel.config.collectAsStateWithLifecycle()
            DialMeTheme(config = config) {
                DialMeRootApp(
                    viewModel = viewModel,
                    onAuthenticateBiometric = { onSuccess -> launchBiometricPrompt(onSuccess) }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        mainViewModelRef?.let { handleIncomingIntent(intent, it) }
    }

    private fun handleIncomingIntent(intent: Intent?, viewModel: DialMeMainViewModel) {
        if (intent == null) return
        val reminderNum = intent.getStringExtra(CallReminderWorker.EXTRA_DIAL_NUMBER)
        if (!reminderNum.isNullOrBlank()) {
            viewModel.setDialInput(reminderNum)
            return
        }
        if ((intent.action == Intent.ACTION_DIAL || intent.action == Intent.ACTION_VIEW) &&
            intent.data?.scheme == "tel"
        ) {
            val number = Uri.decode(intent.data?.schemeSpecificPart.orEmpty())
            if (number.isNotBlank()) {
                viewModel.setDialInput(number)
            }
        }
    }

    private fun launchBiometricPrompt(onAuthenticated: () -> Unit) {
        val manager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            onAuthenticated()
            return
        }
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onAuthenticated()
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_lock_title))
            .setSubtitle(getString(R.string.app_lock_subtitle))
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(promptInfo)
    }
}

@Composable
private fun DialMeRootApp(
    viewModel: DialMeMainViewModel,
    onAuthenticateBiometric: (() -> Unit) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val config by viewModel.config.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val pendingSimNumber by viewModel.pendingSimChoiceNumber.collectAsStateWithLifecycle()
    val simAccounts by viewModel.simAccounts.collectAsStateWithLifecycle()
    val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()
    val quickResponses by viewModel.quickResponses.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshSystemState()
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshSystemState()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSystemState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    // Biometric App Lock Gate
    if (config.appLockEnabled && !isUnlocked) {
        LaunchedEffect(Unit) {
            onAuthenticateBiometric { viewModel.markUnlocked() }
        }
        DialMeBackground {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.app_lock_title), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.app_lock_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { onAuthenticateBiometric { viewModel.markUnlocked() } }) {
                    Text(stringResource(R.string.app_lock_unlock))
                }
            }
        }
        return
    }

    val startRoute = remember(config.startTab) { routeForTab(config.startTab) }
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentDestRoute = navBackStack?.destination?.route.orEmpty()

    val isMainTabRoute = remember(currentDestRoute) {
        MainTabId.entries.any { tab ->
            currentDestRoute.contains(routeForTab(tab)::class.simpleName ?: "")
        } || currentDestRoute.isEmpty()
    }

    val requestDefaultDialer: () -> Unit = {
        val intent = PermissionHelper.buildDefaultDialerIntent(context)
        if (intent != null) {
            roleLauncher.launch(intent)
        } else {
            viewModel.notify("Default dialer role unavailable on this device")
        }
    }

    val requestCallScreeningRole: () -> Unit = {
        val intent = PermissionHelper.buildCallScreeningRoleIntent(context)
        if (intent != null) {
            roleLauncher.launch(intent)
        } else {
            viewModel.notify("Call screening role requires Android 10+")
        }
    }

    val requestCorePermissions: () -> Unit = {
        permissionsLauncher.launch(PermissionHelper.coreDialerRuntimePermissions())
    }

    DialMeBackground {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (!useRail && isMainTabRoute) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(
                                alpha = config.cardTransparencyAlpha.coerceAtLeast(0.82f)
                            )
                        ) {
                            config.navTabOrder.forEach { tab ->
                                val route = routeForTab(tab)
                                val selected = currentDestRoute.contains(route::class.simpleName ?: "")
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(route) {
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(iconForTab(tab), contentDescription = stringResource(labelForTab(tab))) },
                                    label = { Text(stringResource(labelForTab(tab)), maxLines = 1) },
                                    modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    if (useRail && isMainTabRoute) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(
                                alpha = config.cardTransparencyAlpha.coerceAtLeast(0.82f)
                            ),
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            config.navTabOrder.forEach { tab ->
                                val route = routeForTab(tab)
                                val selected = currentDestRoute.contains(route::class.simpleName ?: "")
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(route) {
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(iconForTab(tab), contentDescription = stringResource(labelForTab(tab))) },
                                    label = { Text(stringResource(labelForTab(tab))) }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = startRoute
                        ) {
                            composable<DialMeRoute.Favorites> {
                                ContactsScreen(
                                    viewModel = viewModel,
                                    favoritesOnly = true,
                                    onRequestContactsPermission = requestCorePermissions,
                                    onOpenPrivateVault = { navController.navigate(DialMeRoute.BlockingPrivacy(2)) }
                                )
                            }
                            composable<DialMeRoute.Contacts> {
                                ContactsScreen(
                                    viewModel = viewModel,
                                    favoritesOnly = false,
                                    onRequestContactsPermission = requestCorePermissions,
                                    onOpenPrivateVault = { navController.navigate(DialMeRoute.BlockingPrivacy(2)) }
                                )
                            }
                            composable<DialMeRoute.Recents> {
                                CallHistoryScreen(
                                    viewModel = viewModel,
                                    onRequestCallLogPermission = requestCorePermissions,
                                    onOpenStatistics = { navController.navigate(DialMeRoute.Statistics) }
                                )
                            }
                            composable<DialMeRoute.Dialpad> {
                                DialpadScreen(
                                    viewModel = viewModel,
                                    onRequestDefaultDialer = requestDefaultDialer
                                )
                            }
                            composable<DialMeRoute.Settings> {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onOpenAppearance = { navController.navigate(DialMeRoute.Customization("theme")) },
                                    onOpenCallerScreen = { navController.navigate(DialMeRoute.Customization("caller")) },
                                    onOpenPrivacyBlocking = { navController.navigate(DialMeRoute.BlockingPrivacy(0)) },
                                    onOpenStatistics = { navController.navigate(DialMeRoute.Statistics) },
                                    onOpenAutomation = { navController.navigate(DialMeRoute.Automation) },
                                    onOpenBackup = { navController.navigate(DialMeRoute.Backup) },
                                    onRequestDefaultDialer = requestDefaultDialer,
                                    onRequestAllPermissions = requestCorePermissions
                                )
                            }
                            composable<DialMeRoute.Customization> { backEntry ->
                                val section = backEntry.arguments?.getString("section") ?: "theme"
                                CustomizationScreen(
                                    viewModel = viewModel,
                                    initialFocusSection = section,
                                    onBack = { navController.popBackStack() },
                                    onPreviewCallerScreen = { navController.navigate(DialMeRoute.CallerPreview) }
                                )
                            }
                            composable<DialMeRoute.BlockingPrivacy> { backEntry ->
                                val tab = backEntry.arguments?.getInt("tab") ?: 0
                                BlockingAndPrivacyScreen(
                                    viewModel = viewModel,
                                    initialTab = tab,
                                    onRequestCallScreeningRole = requestCallScreeningRole,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable<DialMeRoute.Statistics> {
                                StatisticsScreen(
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable<DialMeRoute.Automation> {
                                AutomationAndRemindersScreen(
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable<DialMeRoute.Backup> {
                                BackupAndRestoreScreen(
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable<DialMeRoute.CallerPreview> {
                                var previewState by remember {
                                    mutableStateOf(
                                        InCallUiState(
                                            calls = listOf(
                                                ActiveCallSession(
                                                    callId = "preview",
                                                    number = "+880 1711-000000",
                                                    displayName = "DialMe Caller Preview",
                                                    photoUri = null,
                                                    isIncoming = true,
                                                    isUnknownPresentation = false,
                                                    state = CallStateType.RINGING,
                                                    connectTimeMillis = System.currentTimeMillis(),
                                                    canHold = true,
                                                    canMerge = false,
                                                    canSwap = false,
                                                    isConference = false,
                                                    simLabel = "SIM 1",
                                                    verificationStatus = CallerVerificationStatus.PASSED
                                                )
                                            ),
                                            primaryCall = ActiveCallSession(
                                                callId = "preview",
                                                number = "+880 1711-000000",
                                                displayName = "DialMe Caller Preview",
                                                photoUri = null,
                                                isIncoming = true,
                                                isUnknownPresentation = false,
                                                state = CallStateType.RINGING,
                                                connectTimeMillis = System.currentTimeMillis(),
                                                canHold = true,
                                                canMerge = false,
                                                canSwap = false,
                                                isConference = false,
                                                simLabel = "SIM 1",
                                                verificationStatus = CallerVerificationStatus.PASSED
                                            )
                                        )
                                    )
                                }
                                InCallScreen(
                                    uiState = previewState,
                                    config = config,
                                    quickResponses = quickResponses,
                                    isPreviewMode = true,
                                    onAnswer = {
                                        val active = previewState.primaryCall?.copy(
                                            state = CallStateType.ACTIVE,
                                            connectTimeMillis = System.currentTimeMillis()
                                        )
                                        previewState = previewState.copy(
                                            calls = listOfNotNull(active),
                                            primaryCall = active
                                        )
                                    },
                                    onReject = { _, _ -> navController.popBackStack() },
                                    onDisconnect = { navController.popBackStack() },
                                    onToggleMute = { m -> previewState = previewState.copy(isMuted = m) },
                                    onToggleSpeaker = { },
                                    onSelectAudioEndpoint = { },
                                    onToggleHold = { },
                                    onSwapCalls = { },
                                    onMergeConference = { },
                                    onPlayDtmf = { },
                                    onStopDtmf = { },
                                    onSaveNote = { _, _ -> },
                                    onExitPreview = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dual SIM Chooser Dialog
    pendingSimNumber?.let { targetNumber ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissSimChooser() },
            title = { Text(stringResource(R.string.dialpad_choose_sim)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Calling ${viewModel.formatForDisplay(targetNumber)}")
                    simAccounts.forEach { sim ->
                        DialMeCard(
                            onClick = { viewModel.confirmSimAndCall(sim.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.SimCard, null, tint = Color(sim.colorArgb))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(sim.label, style = MaterialTheme.typography.titleMedium)
                                    if (sim.phoneNumber.isNotBlank()) {
                                        Text(sim.phoneNumber, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSimChooser() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

private fun routeForTab(tab: MainTabId): DialMeRoute = when (tab) {
    MainTabId.FAVORITES -> DialMeRoute.Favorites
    MainTabId.CONTACTS -> DialMeRoute.Contacts
    MainTabId.RECENTS -> DialMeRoute.Recents
    MainTabId.DIALPAD -> DialMeRoute.Dialpad
    MainTabId.SETTINGS -> DialMeRoute.Settings
}

private fun iconForTab(tab: MainTabId) = when (tab) {
    MainTabId.FAVORITES -> Icons.Rounded.Star
    MainTabId.CONTACTS -> Icons.Rounded.Contacts
    MainTabId.RECENTS -> Icons.Rounded.History
    MainTabId.DIALPAD -> Icons.Rounded.Dialpad
    MainTabId.SETTINGS -> Icons.Rounded.Settings
}

private fun labelForTab(tab: MainTabId) = when (tab) {
    MainTabId.FAVORITES -> R.string.nav_favorites
    MainTabId.CONTACTS -> R.string.nav_contacts
    MainTabId.RECENTS -> R.string.nav_recents
    MainTabId.DIALPAD -> R.string.nav_dialpad
    MainTabId.SETTINGS -> R.string.nav_settings
}
