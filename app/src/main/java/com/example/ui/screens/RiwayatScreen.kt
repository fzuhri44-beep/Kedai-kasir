package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.PaymentMethod
import com.example.data.model.PosTransaction
import com.example.data.model.UiState
import com.example.ui.components.ReceiptDialog
import com.example.ui.components.RevenueBarChart
import com.example.ui.components.formatRupiah
import com.example.ui.components.formatShortDateTime
import com.example.ui.components.shareReceiptText
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.SalesSummary

private enum class HistoryFilter(val label: String) {
    ALL("Semua"),
    TUNAI("Tunai"),
    QRIS("QRIS"),
    TRANSFER("Transfer")
}

@Composable
fun RiwayatScreen(
    transactionsState: UiState<List<PosTransaction>>,
    salesSummary: SalesSummary,
    cashierName: String,
    selectedPrinterName: String?,
    selectedPaperWidth: String,
    isPrinting: Boolean,
    onOpenPrinterSetup: (PosTransaction?) -> Unit,
    onPrintReceipt: (PosTransaction) -> Unit,
    onDeleteTransaction: (PosTransaction, () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    var selectedReceipt by remember { mutableStateOf<PosTransaction?>(null) }

    selectedReceipt?.let { tx ->
        ReceiptDialog(
            transaction = tx,
            cashierName = cashierName,
            allowDelete = true,
            selectedPrinterName = selectedPrinterName,
            selectedPaperWidth = selectedPaperWidth,
            isPrinting = isPrinting,
            onSelectPrinter = { onOpenPrinterSetup(tx) },
            onPrint = { onPrintReceipt(tx) },
            onShare = { shareReceiptText(context, tx, cashierName) },
            onDelete = {
                onDeleteTransaction(tx) {
                    selectedReceipt = null
                }
            },
            onDismiss = { selectedReceipt = null }
        )
    }

    when (transactionsState) {
        is UiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is UiState.Error -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        text = transactionsState.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        is UiState.Success -> {
            val allTxs = transactionsState.data
            val filteredTxs = remember(allTxs, filter) {
                when (filter) {
                    HistoryFilter.ALL -> allTxs
                    HistoryFilter.TUNAI -> allTxs.filter { it.paymentMethod == PaymentMethod.TUNAI.label }
                    HistoryFilter.QRIS -> allTxs.filter { it.paymentMethod == PaymentMethod.QRIS.label }
                    HistoryFilter.TRANSFER -> allTxs.filter { it.paymentMethod == PaymentMethod.TRANSFER.label }
                }
            }

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Hero Daily Omzet Card
                item {
                    Card(
                        shape = MaterialTheme.shapes.extraLarge,
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                )
                                .padding(20.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Omzet Hari Ini",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f)
                                    )
                                    Surface(
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(50.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "${salesSummary.todayCount} Transaksi",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = formatRupiah(salesSummary.todayRevenue),
                                    style = MaterialTheme.typography.displaySmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Tunai",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                                        )
                                        Text(
                                            text = formatRupiah(salesSummary.cashRevenue),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontFamily = JetBrainsMonoFamily,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "QRIS / Transfer",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                                        )
                                        Text(
                                            text = formatRupiah(salesSummary.digitalRevenue),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontFamily = JetBrainsMonoFamily,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Rata-rata",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                                        )
                                        Text(
                                            text = formatRupiah(salesSummary.averageOrder),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontFamily = JetBrainsMonoFamily,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 7-Day Revenue Chart Card
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Tren Penjualan 7 Hari Terakhir",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            RevenueBarChart(points = salesSummary.last7Days)
                        }
                    }
                }

                // Filter Chips & Section Title
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Daftar Struk Penjualan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HistoryFilter.entries.forEach { option ->
                                FilterChip(
                                    selected = filter == option,
                                    onClick = { filter = option },
                                    label = { Text(option.label) }
                                )
                            }
                        }
                    }
                }

                if (filteredTxs.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = "Belum ada riwayat transaksi",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Setiap pembayaran yang diproses di tab Kasir akan tercatat otomatis di sini.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(filteredTxs, key = { it.id }) { tx ->
                        TransactionHistoryCard(
                            transaction = tx,
                            onClick = { selectedReceipt = tx }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionHistoryCard(
    transaction: PosTransaction,
    onClick: () -> Unit
) {
    val method = PaymentMethod.fromLabel(transaction.paymentMethod)
    val icon = when (method) {
        PaymentMethod.TUNAI -> Icons.Filled.Payments
        PaymentMethod.QRIS -> Icons.Filled.QrCode2
        PaymentMethod.TRANSFER -> Icons.Filled.AccountBalance
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = method.label,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.receiptNumber,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                )
                val summaryLine = buildString {
                    append("${transaction.itemCount} item • ${transaction.paymentMethod}")
                    if (!transaction.customerNote.isNullOrBlank()) {
                        append(" • ${transaction.customerNote}")
                    }
                }
                Text(
                    text = summaryLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatShortDateTime(transaction.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = formatRupiah(transaction.totalAmount),
                style = MaterialTheme.typography.titleSmall,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
