package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.CartItem
import com.example.data.model.MenuCategory
import com.example.data.model.MenuItem
import com.example.data.model.PaymentMethod
import com.example.data.model.PosTransaction
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.DailyRevenuePoint
import com.google.firebase.Timestamp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatRupiah(amount: Long): String {
    val formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"))
    return "Rp ${formatter.format(amount)}"
}

fun formatShortDateTime(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: Date()
    val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID"))
    return fmt.format(date)
}

fun categoryIcon(category: MenuCategory): ImageVector = when (category) {
    MenuCategory.MAKANAN -> Icons.Filled.Restaurant
    MenuCategory.MINUMAN -> Icons.Filled.LocalCafe
    MenuCategory.CEMILAN -> Icons.Filled.Cookie
    MenuCategory.PAKET -> Icons.Filled.Inventory2
}

@Composable
fun categoryBadgeColors(category: MenuCategory): Pair<Color, Color> = when (category) {
    MenuCategory.MAKANAN -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    MenuCategory.MINUMAN -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    MenuCategory.CEMILAN -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    MenuCategory.PAKET -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
}

fun buildReceiptShareText(tx: PosTransaction, cashierName: String): String {
    val sb = StringBuilder()
    sb.appendLine("==============================")
    sb.appendLine("      KEDAI AA SULTHAN        ")
    sb.appendLine("==============================")
    sb.appendLine("No. Struk : ${tx.receiptNumber}")
    sb.appendLine("Tanggal   : ${formatShortDateTime(tx.createdAt)}")
    sb.appendLine("Kasir     : $cashierName")
    if (!tx.customerNote.isNullOrBlank()) {
        sb.appendLine("Catatan   : ${tx.customerNote}")
    }
    sb.appendLine("------------------------------")
    tx.items.forEach { item ->
        sb.appendLine("${item.name}")
        sb.appendLine("  ${item.quantity} x ${formatRupiah(item.unitPrice)} = ${formatRupiah(item.subtotal)}")
    }
    sb.appendLine("------------------------------")
    sb.appendLine("TOTAL     : ${formatRupiah(tx.totalAmount)}")
    sb.appendLine("Metode    : ${tx.paymentMethod}")
    if (tx.paymentMethod == PaymentMethod.TUNAI.label) {
        sb.appendLine("Tunai     : ${formatRupiah(tx.cashReceived)}")
        sb.appendLine("Kembali   : ${formatRupiah(tx.changeAmount)}")
    }
    sb.appendLine("==============================")
    sb.appendLine("Terima kasih telah jajan di")
    sb.appendLine("Kedai Aa Sulthan!")
    return sb.toString()
}

fun shareReceiptText(context: Context, tx: PosTransaction, cashierName: String) {
    val text = buildReceiptShareText(tx, cashierName)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Struk Kedai Aa Sulthan - ${tx.receiptNumber}")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Bagikan Struk"))
}

@Composable
fun RevenueBarChart(
    points: List<DailyRevenuePoint>,
    modifier: Modifier = Modifier
) {
    val maxVal = (points.maxOfOrNull { it.totalRevenue } ?: 0L).coerceAtLeast(1L)
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondaryContainer
    val barInactiveColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(top = 8.dp)
        ) {
            if (points.isEmpty()) return@Canvas
            val barSpacing = 14.dp.toPx()
            val totalSpacing = barSpacing * (points.size - 1)
            val barWidth = ((size.width - totalSpacing) / points.size).coerceAtLeast(12f)
            val maxBarHeight = size.height - 8.dp.toPx()

            points.forEachIndexed { index, point ->
                val ratio = point.totalRevenue.toFloat() / maxVal.toFloat()
                val barHeight = if (point.totalRevenue > 0L) {
                    (ratio * maxBarHeight).coerceAtLeast(10.dp.toPx())
                } else {
                    4.dp.toPx()
                }
                val x = index * (barWidth + barSpacing)
                val y = size.height - barHeight
                val color = when {
                    point.totalRevenue == 0L -> barInactiveColor.copy(alpha = 0.5f)
                    point.isToday -> primaryColor
                    else -> secondaryColor
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.forEach { point ->
                Text(
                    text = point.dayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (point.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (point.isToday) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun CartCheckoutPanel(
    cartItems: List<CartItem>,
    totalAmount: Long,
    customerNote: String,
    paymentMethod: PaymentMethod,
    cashReceivedText: String,
    isBusy: Boolean,
    onIncrement: (MenuItem) -> Unit,
    onDecrement: (String) -> Unit,
    onClearCart: () -> Unit,
    onCustomerNoteChange: (String) -> Unit,
    onPaymentMethodChange: (PaymentMethod) -> Unit,
    onCashReceivedChange: (String) -> Unit,
    onQuickCashPick: (Long) -> Unit,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cashAmount = if (paymentMethod == PaymentMethod.TUNAI) {
        cashReceivedText.toLongOrNull() ?: 0L
    } else {
        totalAmount
    }
    val changeAmount = (cashAmount - totalAmount).coerceAtLeast(0L)
    val canPay = cartItems.isNotEmpty() && !isBusy &&
        (paymentMethod != PaymentMethod.TUNAI || cashAmount >= totalAmount)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Rincian Pesanan",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (cartItems.isNotEmpty()) {
                TextButton(
                    onClick = onClearCart,
                    modifier = Modifier.testTag("clear_cart_button")
                ) {
                    Text("Kosongkan")
                }
            }
        }

        if (cartItems.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Belum ada item di keranjang. Ketuk menu untuk menambahkan pesanan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cartItems, key = { it.menuItem.id }) { item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.menuItem.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${formatRupiah(item.menuItem.price)} × ${item.quantity}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = { onDecrement(item.menuItem.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Remove,
                                        contentDescription = "Kurangi ${item.menuItem.name}",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = item.quantity.toString(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { onIncrement(item.menuItem) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = "Tambah ${item.menuItem.name}",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = customerNote,
                onValueChange = onCustomerNoteChange,
                label = { Text("Pelanggan / No. Meja (Opsional)") },
                placeholder = { Text("Contoh: Meja 2 atau Bungkus") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("customer_note_input")
            )

            Text(
                text = "Metode Pembayaran",
                style = MaterialTheme.typography.labelLarge
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PaymentMethod.entries.forEach { method ->
                    val selected = paymentMethod == method
                    val icon = when (method) {
                        PaymentMethod.TUNAI -> Icons.Filled.Payments
                        PaymentMethod.QRIS -> Icons.Filled.QrCode2
                        PaymentMethod.TRANSFER -> Icons.Filled.AccountBalance
                    }
                    FilterChip(
                        selected = selected,
                        onClick = { onPaymentMethodChange(method) },
                        label = { Text(method.label) },
                        leadingIcon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("payment_${method.name.lowercase(Locale.ROOT)}")
                    )
                }
            }

            if (paymentMethod == PaymentMethod.TUNAI) {
                OutlinedTextField(
                    value = cashReceivedText,
                    onValueChange = onCashReceivedChange,
                    label = { Text("Uang Diterima (Rp)") },
                    placeholder = { Text("Masukkan nominal tunai") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cash_received_input")
                )

                val quickAmounts = listOf(
                    "Uang Pas" to totalAmount,
                    "20 rb" to 20_000L,
                    "50 rb" to 50_000L,
                    "100 rb" to 100_000L
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickAmounts.forEach { (label, amount) ->
                        OutlinedButton(
                            onClick = { onQuickCashPick(amount) },
                            shape = RoundedCornerShape(50.dp)
                        ) {
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Tagihan", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = formatRupiah(totalAmount),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (paymentMethod == PaymentMethod.TUNAI) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Kembalian", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = if (cashAmount >= totalAmount) formatRupiah(changeAmount) else "Kurang ${formatRupiah(totalAmount - cashAmount)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (cashAmount >= totalAmount) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onCheckout,
                enabled = canPay,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("process_payment_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = "Proses Pembayaran • ${formatRupiah(totalAmount)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutBottomSheet(
    cartItems: List<CartItem>,
    totalAmount: Long,
    customerNote: String,
    paymentMethod: PaymentMethod,
    cashReceivedText: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onIncrement: (MenuItem) -> Unit,
    onDecrement: (String) -> Unit,
    onClearCart: () -> Unit,
    onCustomerNoteChange: (String) -> Unit,
    onPaymentMethodChange: (PaymentMethod) -> Unit,
    onCashReceivedChange: (String) -> Unit,
    onQuickCashPick: (Long) -> Unit,
    onCheckout: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            CartCheckoutPanel(
                cartItems = cartItems,
                totalAmount = totalAmount,
                customerNote = customerNote,
                paymentMethod = paymentMethod,
                cashReceivedText = cashReceivedText,
                isBusy = isBusy,
                onIncrement = onIncrement,
                onDecrement = onDecrement,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuFormBottomSheet(
    editingItem: MenuItem?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, category: MenuCategory, priceText: String, available: Boolean) -> Unit
) {
    var name by remember(editingItem) { mutableStateOf(editingItem?.name ?: "") }
    var category by remember(editingItem) {
        mutableStateOf(
            if (editingItem != null) MenuCategory.fromLabel(editingItem.category) else MenuCategory.MAKANAN
        )
    }
    var priceText by remember(editingItem) {
        mutableStateOf(if (editingItem != null && editingItem.price > 0) editingItem.price.toString() else "")
    }
    var available by remember(editingItem) { mutableStateOf(editingItem?.available ?: true) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (editingItem == null) "Tambah Menu Baru" else "Ubah Detail Menu",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text("Nama Menu") },
                placeholder = { Text("Contoh: Kopi Susu Aa Sulthan") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("menu_name_input")
            )

            Text("Kategori", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MenuCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat.label) },
                        leadingIcon = {
                            Icon(
                                imageVector = categoryIcon(cat),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors()
                    )
                }
            }

            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it.filter { ch -> ch.isDigit() }.take(9) },
                label = { Text("Harga (Rp)") },
                placeholder = { Text("Contoh: 15000") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("menu_price_input")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { available = !available }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Tersedia untuk Dipesan", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (available) "Tampil aktif di layar kasir" else "Ditandai habis di layar kasir",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = available,
                    onCheckedChange = { available = it }
                )
            }

            Button(
                onClick = { onSave(name, category, priceText, available) },
                enabled = !isBusy,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_menu_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (editingItem == null) "Simpan Menu" else "Simpan Perubahan",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ReceiptDialog(
    transaction: PosTransaction,
    cashierName: String,
    allowDelete: Boolean,
    selectedPrinterName: String? = null,
    selectedPaperWidth: String? = null,
    isPrinting: Boolean = false,
    onSelectPrinter: () -> Unit,
    onPrint: () -> Unit,
    onShare: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Batalkan & Hapus Transaksi?") },
            text = {
                Text("Struk ${transaction.receiptNumber} senilai ${formatRupiah(transaction.totalAmount)} akan dihapus dari catatan penjualan.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Batal")
                }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Struk Kedai Aa Sulthan",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = transaction.receiptNumber,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Tutup struk"
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Waktu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatShortDateTime(transaction.createdAt), style = MaterialTheme.typography.bodySmall)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Kasir", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(cashierName, style = MaterialTheme.typography.bodySmall)
                }
                if (!transaction.customerNote.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Catatan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(transaction.customerNote, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }

                HorizontalDivider()

                transaction.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                text = "${item.quantity} × ${formatRupiah(item.unitPrice)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = JetBrainsMonoFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatRupiah(item.subtotal),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Tagihan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = formatRupiah(transaction.totalAmount),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Metode (${transaction.paymentMethod})", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = formatRupiah(transaction.cashReceived),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = JetBrainsMonoFamily
                    )
                }
                if (transaction.paymentMethod == PaymentMethod.TUNAI.label) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Kembalian", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = formatRupiah(transaction.changeAmount),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(onClick = onSelectPrinter)
                        .testTag("receipt_select_printer_row")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bluetooth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (selectedPrinterName != null) {
                                    "$selectedPrinterName (${selectedPaperWidth ?: "58 mm"})"
                                } else {
                                    "Pilih Printer Termal Bluetooth"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = if (selectedPrinterName != null) "Ubah" else "Cari",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Button(
                    onClick = onPrint,
                    enabled = !isPrinting,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("print_receipt_button")
                ) {
                    if (isPrinting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mencetak Struk...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Print,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedPrinterName != null) "Print Struk Termal" else "Cari Printer & Print Struk",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (allowDelete && onDelete != null) {
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Hapus")
                        }
                    }
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("share_receipt_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bagikan Struk")
                    }
                }
            }
        }
    }
}
