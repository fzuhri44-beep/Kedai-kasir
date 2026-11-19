package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.PosTransaction
import com.example.data.printer.ThermalPrinterManager
import com.example.data.repository.KedaiRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOut
import com.example.ui.components.PrinterSetupBottomSheet
import com.example.ui.components.ReceiptDialog
import com.example.ui.components.shareReceiptText
import com.example.ui.screens.KasirScreen
import com.example.ui.screens.MenuScreen
import com.example.ui.screens.RiwayatScreen
import com.example.ui.theme.KedaiAaSulthanTheme
import com.example.ui.viewmodel.KedaiViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private enum class PosTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    KASIR("Kasir", Icons.Filled.PointOfSale, Icons.Outlined.PointOfSale),
    MENU("Menu", Icons.Filled.RestaurantMenu, Icons.Outlined.RestaurantMenu),
    RIWAYAT("Riwayat", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KedaiAaSulthanTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = { /* authStateFlow updates automatically */ })
    } else {
        val uid = user.uid
        val cashierName = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")
            ?: "Kasir Kedai"
        key(uid) {
            MainPosApp(
                currentUserId = uid,
                cashierName = cashierName
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainPosApp(
    currentUserId: String,
    cashierName: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: KedaiViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                KedaiViewModel(KedaiRepository(db), currentUserId)
            }
        }
    )

    val menuState by viewModel.menuState.collectAsStateWithLifecycle()
    val transactionsState by viewModel.transactionsState.collectAsStateWithLifecycle()
    val cartItems by viewModel.cartItems.collectAsStateWithLifecycle()
    val cartTotal by viewModel.cartTotal.collectAsStateWithLifecycle()
    val cartCount by viewModel.cartCount.collectAsStateWithLifecycle()
    val customerNote by viewModel.customerNote.collectAsStateWithLifecycle()
    val paymentMethod by viewModel.paymentMethod.collectAsStateWithLifecycle()
    val cashReceivedText by viewModel.cashReceivedText.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val completedReceipt by viewModel.completedReceipt.collectAsStateWithLifecycle()
    val salesSummary by viewModel.salesSummary.collectAsStateWithLifecycle()

    val printerManager = remember(context) { ThermalPrinterManager(context) }
    val printerState by printerManager.state.collectAsStateWithLifecycle()

    DisposableEffect(printerManager) {
        onDispose { printerManager.release() }
    }

    var activeTab by remember { mutableStateOf(PosTab.KASIR) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showPrinterSheet by remember { mutableStateOf(false) }
    var pendingPrintTx by remember { mutableStateOf<PosTransaction?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    fun triggerPrintReceipt(tx: PosTransaction) {
        printerManager.refreshStatus()
        val currentPrinter = printerManager.state.value
        if (currentPrinter.selectedPrinter != null &&
            currentPrinter.hasPermissions &&
            currentPrinter.isBluetoothEnabled
        ) {
            scope.launch {
                val res = printerManager.printReceipt(tx, cashierName)
                res.onSuccess {
                    snackbarHostState.showSnackbar("Struk ${tx.receiptNumber} berhasil dicetak!")
                }.onFailure { err ->
                    snackbarHostState.showSnackbar(err.localizedMessage ?: "Gagal mencetak struk.")
                    pendingPrintTx = tx
                    showPrinterSheet = true
                }
            }
        } else {
            pendingPrintTx = tx
            showPrinterSheet = true
        }
    }

    // Back press on secondary tabs returns to primary Kasir screen
    BackHandler(enabled = activeTab != PosTab.KASIR) {
        activeTab = PosTab.KASIR
    }

    if (showPrinterSheet) {
        PrinterSetupBottomSheet(
            printerManager = printerManager,
            printerState = printerState,
            pendingReceiptToPrint = pendingPrintTx,
            cashierName = cashierName,
            onDismiss = {
                showPrinterSheet = false
                pendingPrintTx = null
            }
        )
    }

    completedReceipt?.let { tx ->
        ReceiptDialog(
            transaction = tx,
            cashierName = cashierName,
            allowDelete = false,
            selectedPrinterName = printerState.selectedPrinter?.name,
            selectedPaperWidth = printerState.paperWidth.label,
            isPrinting = printerState.isPrinting,
            onSelectPrinter = {
                pendingPrintTx = tx
                showPrinterSheet = true
            },
            onPrint = { triggerPrintReceipt(tx) },
            onShare = { shareReceiptText(context, tx, cashierName) },
            onDismiss = { viewModel.dismissCompletedReceipt() }
        )
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Keluar dari Akun?") },
            text = { Text("Sesi kasir ($cashierName) akan ditutup. Data menu & transaksi tetap aman tersimpan di cloud.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutConfirm = false
                        signOut(credentialManager = credentialManager, scope = scope)
                    }
                ) {
                    Text("Keluar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text("Batal")
                }
            }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Kedai Aa Sulthan",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Kasir: $cashierName",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                pendingPrintTx = null
                                showPrinterSheet = true
                            },
                            modifier = Modifier.testTag("open_printer_setup_button")
                        ) {
                            Icon(
                                imageVector = if (printerState.selectedPrinter != null) {
                                    Icons.Filled.Print
                                } else {
                                    Icons.Outlined.Print
                                },
                                contentDescription = "Cari Printer Bluetooth",
                                tint = if (printerState.selectedPrinter != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        IconButton(
                            onClick = { showSignOutConfirm = true },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Keluar akun"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        PosTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.weight(0.1f))
                        PosTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (activeTab) {
                        PosTab.KASIR -> KasirScreen(
                            menuState = menuState,
                            cartItems = cartItems,
                            cartTotal = cartTotal,
                            cartCount = cartCount,
                            customerNote = customerNote,
                            paymentMethod = paymentMethod,
                            cashReceivedText = cashReceivedText,
                            isBusy = isBusy,
                            onAddToCart = viewModel::addToCart,
                            onDecrementCartItem = viewModel::decrementCartItem,
                            onClearCart = viewModel::clearCart,
                            onCustomerNoteChange = viewModel::setCustomerNote,
                            onPaymentMethodChange = viewModel::setPaymentMethod,
                            onCashReceivedChange = viewModel::setCashReceivedText,
                            onQuickCashPick = viewModel::setQuickCashAmount,
                            onCheckout = { viewModel.checkout() },
                            onSeedDefaultMenu = viewModel::seedDefaultMenu,
                            onNavigateToMenuTab = { activeTab = PosTab.MENU }
                        )

                        PosTab.MENU -> MenuScreen(
                            menuState = menuState,
                            isBusy = isBusy,
                            onSaveMenuItem = viewModel::saveMenuItem,
                            onToggleAvailability = viewModel::toggleMenuAvailability,
                            onDeleteMenuItem = viewModel::deleteMenuItem
                        )

                        PosTab.RIWAYAT -> RiwayatScreen(
                            transactionsState = transactionsState,
                            salesSummary = salesSummary,
                            cashierName = cashierName,
                            selectedPrinterName = printerState.selectedPrinter?.name,
                            selectedPaperWidth = printerState.paperWidth.label,
                            isPrinting = printerState.isPrinting,
                            onOpenPrinterSetup = { tx ->
                                pendingPrintTx = tx
                                showPrinterSheet = true
                            },
                            onPrintReceipt = { tx -> triggerPrintReceipt(tx) },
                            onDeleteTransaction = viewModel::deleteTransaction
                        )
                    }
                }
            }
        }
    }
}
