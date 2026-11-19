package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CartItem
import com.example.data.model.MenuCategory
import com.example.data.model.MenuItem
import com.example.data.model.PaymentMethod
import com.example.data.model.PosTransaction
import com.example.data.model.UiState
import com.example.data.repository.KedaiRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DailyRevenuePoint(
    val dayLabel: String,
    val totalRevenue: Long,
    val isToday: Boolean
)

data class SalesSummary(
    val todayRevenue: Long = 0L,
    val todayCount: Int = 0,
    val averageOrder: Long = 0L,
    val cashRevenue: Long = 0L,
    val digitalRevenue: Long = 0L,
    val last7Days: List<DailyRevenuePoint> = emptyList()
)

class KedaiViewModel(
    private val repository: KedaiRepository,
    private val currentUserId: String
) : ViewModel() {

    val menuState: StateFlow<UiState<List<MenuItem>>> = repository.observeMenuItems(currentUserId)
        .map<List<MenuItem>, UiState<List<MenuItem>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing menu items", rootError)
            emit(UiState.Error(rootError.message ?: "Gagal memuat daftar menu."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val transactionsState: StateFlow<UiState<List<PosTransaction>>> =
        repository.observeTransactions(currentUserId)
            .map<List<PosTransaction>, UiState<List<PosTransaction>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing transactions", rootError)
                emit(UiState.Error(rootError.message ?: "Gagal memuat riwayat transaksi."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val _cartQuantities = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val _customerNote = MutableStateFlow("")
    val customerNote: StateFlow<String> = _customerNote.asStateFlow()

    private val _paymentMethod = MutableStateFlow(PaymentMethod.TUNAI)
    val paymentMethod: StateFlow<PaymentMethod> = _paymentMethod.asStateFlow()

    private val _cashReceivedText = MutableStateFlow("")
    val cashReceivedText: StateFlow<String> = _cashReceivedText.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _completedReceipt = MutableStateFlow<PosTransaction?>(null)
    val completedReceipt: StateFlow<PosTransaction?> = _completedReceipt.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val cartItems: StateFlow<List<CartItem>> = combine(menuState, _cartQuantities) { state, quantities ->
        val items = (state as? UiState.Success)?.data.orEmpty()
        items.mapNotNull { menuItem ->
            val qty = quantities[menuItem.id] ?: 0
            if (qty > 0 && menuItem.available) CartItem(menuItem, qty) else null
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    val cartTotal: StateFlow<Long> = cartItems
        .map { list -> list.sumOf { it.subtotal } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = 0L
        )

    val cartCount: StateFlow<Int> = cartItems
        .map { list -> list.sumOf { it.quantity } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = 0
        )

    val salesSummary: StateFlow<SalesSummary> = transactionsState
        .map { state ->
            val txs = (state as? UiState.Success)?.data.orEmpty()
            computeSalesSummary(txs)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SalesSummary()
        )

    fun addToCart(menuItem: MenuItem) {
        if (!menuItem.available) {
            _messages.tryEmit("${menuItem.name} sedang habis.")
            return
        }
        val current = _cartQuantities.value.toMutableMap()
        val existingQty = current[menuItem.id] ?: 0
        if (existingQty < 99) {
            current[menuItem.id] = existingQty + 1
            _cartQuantities.value = current
        }
    }

    fun decrementCartItem(menuItemId: String) {
        val current = _cartQuantities.value.toMutableMap()
        val existingQty = current[menuItemId] ?: 0
        if (existingQty > 1) {
            current[menuItemId] = existingQty - 1
        } else {
            current.remove(menuItemId)
        }
        _cartQuantities.value = current
    }

    fun removeFromCart(menuItemId: String) {
        val current = _cartQuantities.value.toMutableMap()
        current.remove(menuItemId)
        _cartQuantities.value = current
    }

    fun clearCart() {
        _cartQuantities.value = emptyMap()
        _customerNote.value = ""
        _cashReceivedText.value = ""
        _paymentMethod.value = PaymentMethod.TUNAI
    }

    fun setCustomerNote(note: String) {
        _customerNote.value = note.take(80)
    }

    fun setPaymentMethod(method: PaymentMethod) {
        _paymentMethod.value = method
        if (method != PaymentMethod.TUNAI) {
            _cashReceivedText.value = cartTotal.value.toString()
        }
    }

    fun setCashReceivedText(raw: String) {
        val digitsOnly = raw.filter { it.isDigit() }.take(9)
        _cashReceivedText.value = digitsOnly
    }

    fun setQuickCashAmount(amount: Long) {
        _cashReceivedText.value = amount.toString()
    }

    fun checkout(onSuccess: () -> Unit = {}) {
        val currentCart = cartItems.value
        if (currentCart.isEmpty()) {
            _messages.tryEmit("Keranjang masih kosong.")
            return
        }
        val total = currentCart.sumOf { it.subtotal }
        val method = _paymentMethod.value
        val cash = if (method == PaymentMethod.TUNAI) {
            _cashReceivedText.value.toLongOrNull() ?: 0L
        } else {
            total
        }

        if (method == PaymentMethod.TUNAI && cash < total) {
            _messages.tryEmit("Uang tunai masih kurang dari total tagihan.")
            return
        }

        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.createTransaction(
                userId = currentUserId,
                cartItems = currentCart,
                customerNote = _customerNote.value,
                paymentMethod = method,
                cashReceived = cash
            )
            _isBusy.value = false
            result.onSuccess { tx ->
                clearCart()
                _completedReceipt.value = tx
                onSuccess()
            }.onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Gagal memproses pembayaran.")
            }
        }
    }

    fun dismissCompletedReceipt() {
        _completedReceipt.value = null
    }

    fun saveMenuItem(
        editingId: String?,
        name: String,
        category: MenuCategory,
        priceText: String,
        available: Boolean,
        onSaved: () -> Unit
    ) {
        val cleanName = name.trim()
        val parsedPrice = priceText.filter { it.isDigit() }.toLongOrNull() ?: 0L
        if (cleanName.isEmpty() || cleanName.length > 60) {
            _messages.tryEmit("Nama menu wajib diisi (maks 60 karakter).")
            return
        }
        if (parsedPrice <= 0L || parsedPrice > 100_000_000L) {
            _messages.tryEmit("Masukkan harga yang valid (lebih dari Rp 0).")
            return
        }

        viewModelScope.launch {
            _isBusy.value = true
            val result = if (editingId == null) {
                repository.addMenuItem(currentUserId, cleanName, category, parsedPrice, available)
            } else {
                repository.updateMenuItem(currentUserId, editingId, cleanName, category, parsedPrice, available)
            }
            _isBusy.value = false
            result.onSuccess {
                _messages.tryEmit(if (editingId == null) "Menu berhasil ditambahkan." else "Menu berhasil diperbarui.")
                onSaved()
            }.onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Gagal menyimpan menu.")
            }
        }
    }

    fun toggleMenuAvailability(item: MenuItem) {
        viewModelScope.launch {
            val result = repository.toggleMenuAvailability(currentUserId, item.id, !item.available)
            result.onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Gagal mengubah status menu.")
            }
        }
    }

    fun deleteMenuItem(item: MenuItem) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deleteMenuItem(currentUserId, item.id)
            _isBusy.value = false
            result.onSuccess {
                removeFromCart(item.id)
                _messages.tryEmit("${item.name} dihapus.")
            }.onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Gagal menghapus menu.")
            }
        }
    }

    fun seedDefaultMenu() {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.seedDefaultMenu(currentUserId)
            _isBusy.value = false
            result.onSuccess {
                _messages.tryEmit("9 menu contoh Kedai Aa Sulthan berhasil ditambahkan!")
            }.onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Gagal menambahkan menu contoh.")
            }
        }
    }

    fun deleteTransaction(tx: PosTransaction, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deleteTransaction(currentUserId, tx.id)
            _isBusy.value = false
            result.onSuccess {
                _messages.tryEmit("Transaksi ${tx.receiptNumber} dibatalkan/dihapus.")
                onDeleted()
            }.onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Gagal menghapus transaksi.")
            }
        }
    }

    private fun computeSalesSummary(txs: List<PosTransaction>): SalesSummary {
        val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayLabelFormat = SimpleDateFormat("EEE", Locale.forLanguageTag("id-ID"))

        val todayCal = Calendar.getInstance()
        val todayKey = dayKeyFormat.format(todayCal.time)

        val groupedByDay = mutableMapOf<String, Long>()
        var todayRevenue = 0L
        var todayCount = 0
        var cashRevenue = 0L
        var digitalRevenue = 0L

        for (tx in txs) {
            val date = tx.createdAt?.toDate() ?: Date()
            val key = dayKeyFormat.format(date)
            groupedByDay[key] = (groupedByDay[key] ?: 0L) + tx.totalAmount
            if (key == todayKey) {
                todayRevenue += tx.totalAmount
                todayCount += 1
                if (tx.paymentMethod == PaymentMethod.TUNAI.label) {
                    cashRevenue += tx.totalAmount
                } else {
                    digitalRevenue += tx.totalAmount
                }
            }
        }

        val last7Days = (6 downTo 0).map { daysAgo ->
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
            val key = dayKeyFormat.format(cal.time)
            DailyRevenuePoint(
                dayLabel = dayLabelFormat.format(cal.time),
                totalRevenue = groupedByDay[key] ?: 0L,
                isToday = daysAgo == 0
            )
        }

        val avg = if (todayCount > 0) todayRevenue / todayCount else 0L
        return SalesSummary(
            todayRevenue = todayRevenue,
            todayCount = todayCount,
            averageOrder = avg,
            cashRevenue = cashRevenue,
            digitalRevenue = digitalRevenue,
            last7Days = last7Days
        )
    }

    private companion object {
        const val TAG = "KedaiVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
