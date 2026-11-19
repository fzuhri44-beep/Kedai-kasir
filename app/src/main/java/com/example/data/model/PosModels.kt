package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class MenuCategory(val label: String) {
    MAKANAN("Makanan"),
    MINUMAN("Minuman"),
    CEMILAN("Cemilan"),
    PAKET("Paket");

    companion object {
        fun fromLabel(raw: String): MenuCategory =
            entries.firstOrNull { it.label == raw || it.name == raw } ?: MAKANAN
    }
}

enum class PaymentMethod(val label: String) {
    TUNAI("Tunai"),
    QRIS("QRIS"),
    TRANSFER("Transfer");

    companion object {
        fun fromLabel(raw: String): PaymentMethod =
            entries.firstOrNull { it.label == raw || it.name == raw } ?: TUNAI
    }
}

@Keep
@IgnoreExtraProperties
data class MenuItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val category: String = MenuCategory.MAKANAN.label,
    val price: Long = 0L,
    val available: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OrderLineItem(
    val menuItemId: String = "",
    val name: String = "",
    val unitPrice: Long = 0L,
    val quantity: Long = 0L,
    val subtotal: Long = 0L
)

@Keep
@IgnoreExtraProperties
data class PosTransaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val receiptNumber: String = "",
    val customerNote: String? = null,
    val paymentMethod: String = PaymentMethod.TUNAI.label,
    val totalAmount: Long = 0L,
    val cashReceived: Long = 0L,
    val changeAmount: Long = 0L,
    val itemCount: Long = 0L,
    val items: List<OrderLineItem> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class CartItem(
    val menuItem: MenuItem,
    val quantity: Int
) {
    val subtotal: Long get() = menuItem.price * quantity
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
