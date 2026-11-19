package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CartItem
import com.example.data.model.MenuCategory
import com.example.data.model.MenuItem
import com.example.data.model.PaymentMethod
import com.example.data.model.UiState
import com.example.ui.components.CartCheckoutPanel
import com.example.ui.components.CheckoutBottomSheet
import com.example.ui.components.categoryBadgeColors
import com.example.ui.components.categoryIcon
import com.example.ui.components.formatRupiah
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun KasirScreen(
    menuState: UiState<List<MenuItem>>,
    cartItems: List<CartItem>,
    cartTotal: Long,
    cartCount: Int,
    customerNote: String,
    paymentMethod: PaymentMethod,
    cashReceivedText: String,
    isBusy: Boolean,
    onAddToCart: (MenuItem) -> Unit,
    onDecrementCartItem: (String) -> Unit,
    onClearCart: () -> Unit,
    onCustomerNoteChange: (String) -> Unit,
    onPaymentMethodChange: (PaymentMethod) -> Unit,
    onCashReceivedChange: (String) -> Unit,
    onQuickCashPick: (Long) -> Unit,
    onCheckout: () -> Unit,
    onSeedDefaultMenu: () -> Unit,
    onNavigateToMenuTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<MenuCategory?>(null) }
    var showCheckoutSheet by remember { mutableStateOf(false) }

    val cartQtyMap = remember(cartItems) {
        cartItems.associate { it.menuItem.id to it.quantity }
    }

    if (showCheckoutSheet) {
        CheckoutBottomSheet(
            cartItems = cartItems,
            totalAmount = cartTotal,
            customerNote = customerNote,
            paymentMethod = paymentMethod,
            cashReceivedText = cashReceivedText,
            isBusy = isBusy,
            onDismiss = { showCheckoutSheet = false },
            onIncrement = onAddToCart,
            onDecrement = onDecrementCartItem,
            onClearCart = onClearCart,
            onCustomerNoteChange = onCustomerNoteChange,
            onPaymentMethodChange = onPaymentMethodChange,
            onCashReceivedChange = onCashReceivedChange,
            onQuickCashPick = onQuickCashPick,
            onCheckout = {
                onCheckout()
                showCheckoutSheet = false
            }
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideSplit = maxWidth >= 720.dp

        Row(modifier = Modifier.fillMaxSize()) {
            // Left / Primary POS Menu Pane
            Box(
                modifier = Modifier
                    .weight(if (isWideSplit) 1.35f else 1f)
                    .fillMaxHeight()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Search + Category Filter
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Cari menu kopi, makanan, cemilan...") },
                            leadingIcon = {
                                Icon(Icons.Filled.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Hapus pencarian")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("kasir_search_input")
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { selectedCategory = null },
                                label = { Text("Semua") }
                            )
                            MenuCategory.entries.forEach { category ->
                                FilterChip(
                                    selected = selectedCategory == category,
                                    onClick = {
                                        selectedCategory = if (selectedCategory == category) null else category
                                    },
                                    label = { Text(category.label) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = categoryIcon(category),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }

                    when (menuState) {
                        is UiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        is UiState.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    Text(
                                        text = menuState.message,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(16.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        is UiState.Success -> {
                            val allItems = menuState.data
                            if (allItems.isEmpty()) {
                                EmptyCatalogOnboarding(
                                    isBusy = isBusy,
                                    onSeedDefaultMenu = onSeedDefaultMenu,
                                    onNavigateToMenuTab = onNavigateToMenuTab
                                )
                            } else {
                                val filtered = remember(allItems, searchQuery, selectedCategory) {
                                    allItems.filter { item ->
                                        val matchesCat = selectedCategory == null ||
                                            MenuCategory.fromLabel(item.category) == selectedCategory
                                        val matchesQuery = searchQuery.isBlank() ||
                                            item.name.contains(searchQuery.trim(), ignoreCase = true)
                                        matchesCat && matchesQuery
                                    }
                                }

                                if (filtered.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Tidak ada menu yang cocok dengan filter saat ini.",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(minSize = 156.dp),
                                        contentPadding = PaddingValues(
                                            start = 16.dp,
                                            end = 16.dp,
                                            top = 4.dp,
                                            bottom = if (!isWideSplit && cartCount > 0) 96.dp else 20.dp
                                        ),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(filtered, key = { it.id }) { item ->
                                            PosMenuGridCard(
                                                item = item,
                                                qtyInCart = cartQtyMap[item.id] ?: 0,
                                                onAdd = { onAddToCart(item) },
                                                onDecrement = { onDecrementCartItem(item.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Floating Cart Bar on Compact screens
                if (!isWideSplit) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = cartCount > 0,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = MaterialTheme.shapes.large,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.large)
                                .clickable { showCheckoutSheet = true }
                                .testTag("open_checkout_sheet_bar")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Filled.ShoppingBag, contentDescription = null)
                                    Column {
                                        Text(
                                            text = "$cartCount Item di Keranjang",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Ketuk untuk bayar",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                                Text(
                                    text = formatRupiah(cartTotal),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Right Side Checkout Pane on Tablets / Foldables
            if (isWideSplit) {
                Surface(
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        CartCheckoutPanel(
                            cartItems = cartItems,
                            totalAmount = cartTotal,
                            customerNote = customerNote,
                            paymentMethod = paymentMethod,
                            cashReceivedText = cashReceivedText,
                            isBusy = isBusy,
                            onIncrement = onAddToCart,
                            onDecrement = onDecrementCartItem,
                            onClearCart = onClearCart,
                            onCustomerNoteChange = onCustomerNoteChange,
                            onPaymentMethodChange = onPaymentMethodChange,
                            onCashReceivedChange = onCashReceivedChange,
                            onQuickCashPick = onQuickCashPick,
                            onCheckout = onCheckout
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PosMenuGridCard(
    item: MenuItem,
    qtyInCart: Int,
    onAdd: () -> Unit,
    onDecrement: () -> Unit
) {
    val category = MenuCategory.fromLabel(item.category)
    val (badgeBg, badgeFg) = categoryBadgeColors(category)

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (qtyInCart > 0) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (qtyInCart > 0) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        },
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (item.available) 1f else 0.55f)
            .clip(MaterialTheme.shapes.large)
            .clickable(enabled = item.available, onClick = onAdd)
            .testTag("menu_grid_card_${item.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon(category),
                        contentDescription = category.label,
                        tint = badgeFg,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (!item.available) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(50.dp)
                    ) {
                        Text(
                            text = "Habis",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                } else if (qtyInCart > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(50.dp)
                    ) {
                        Text(
                            text = "${qtyInCart}×",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    minLines = 2
                )
                Text(
                    text = category.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatRupiah(item.price),
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (qtyInCart > 0 && item.available) {
                    IconButton(
                        onClick = onDecrement,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = "Kurangi ${item.name}",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCatalogOnboarding(
    isBusy: Boolean,
    onSeedDefaultMenu: () -> Unit,
    onNavigateToMenuTab: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.RestaurantMenu,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = "Menu Kedai Masih Kosong",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Tambahkan menu makanan & minuman Kedai Aa Sulthan sendiri, atau muat 9 menu contoh khas kedai untuk langsung mencoba kasir.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onSeedDefaultMenu,
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("seed_default_menu_button")
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Isi 9 Menu Contoh Kedai", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onNavigateToMenuTab,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Buat Menu Manual")
                }
            }
        }
    }
}
