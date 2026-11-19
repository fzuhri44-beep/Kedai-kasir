package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.CartItem
import com.example.data.model.MenuCategory
import com.example.data.model.MenuItem
import com.example.data.model.PaymentMethod
import com.example.data.model.PosTransaction
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KedaiRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireAuthorizedUid(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus masuk dengan Google terlebih dahulu.")
        check(uid == expectedUserId) { "Sesi pengguna berubah; operasi dibatalkan." }
        return uid
    }

    fun observeMenuItems(userId: String): Flow<List<MenuItem>> = flow {
        if (userId.isBlank() || auth.currentUser?.uid != userId) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/menuItems"
        emitAll(
            db.collection("users").document(userId).collection("menuItems")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MenuItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(compareBy<MenuItem> { it.category }.thenBy { it.name.lowercase(Locale.ROOT) })
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeTransactions(userId: String): Flow<List<PosTransaction>> = flow {
        if (userId.isBlank() || auth.currentUser?.uid != userId) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/transactions"
        emitAll(
            db.collection("users").document(userId).collection("transactions")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PosTransaction::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addMenuItem(
        userId: String,
        name: String,
        category: MenuCategory,
        price: Long,
        available: Boolean
    ): Result<Unit> {
        val cleanName = name.trim()
        var docPath = "users/$userId/menuItems"
        return try {
            val uid = requireAuthorizedUid(userId)
            require(cleanName.length in 1..60) { "Nama menu harus 1–60 karakter." }
            require(price in 1L..100_000_000L) { "Harga menu harus lebih dari Rp 0." }
            val docRef = db.collection("users").document(uid).collection("menuItems").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "name" to cleanName,
                "category" to category.label,
                "price" to price,
                "available" to available,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateMenuItem(
        userId: String,
        itemId: String,
        name: String,
        category: MenuCategory,
        price: Long,
        available: Boolean
    ): Result<Unit> {
        val cleanName = name.trim()
        val docPath = "users/$userId/menuItems/$itemId"
        return try {
            val uid = requireAuthorizedUid(userId)
            require(itemId.isNotBlank()) { "ID menu tidak valid." }
            require(cleanName.length in 1..60) { "Nama menu harus 1–60 karakter." }
            require(price in 1L..100_000_000L) { "Harga menu harus lebih dari Rp 0." }
            db.collection("users").document(uid).collection("menuItems").document(itemId)
                .update(
                    mapOf(
                        "name" to cleanName,
                        "category" to category.label,
                        "price" to price,
                        "available" to available,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleMenuAvailability(
        userId: String,
        itemId: String,
        available: Boolean
    ): Result<Unit> {
        val docPath = "users/$userId/menuItems/$itemId"
        return try {
            val uid = requireAuthorizedUid(userId)
            require(itemId.isNotBlank()) { "ID menu tidak valid." }
            db.collection("users").document(uid).collection("menuItems").document(itemId)
                .update(
                    mapOf(
                        "available" to available,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteMenuItem(userId: String, itemId: String): Result<Unit> {
        val docPath = "users/$userId/menuItems/$itemId"
        return try {
            val uid = requireAuthorizedUid(userId)
            require(itemId.isNotBlank()) { "ID menu tidak valid." }
            db.collection("users").document(uid).collection("menuItems").document(itemId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun seedDefaultMenu(userId: String): Result<Unit> {
        val colPath = "users/$userId/menuItems"
        return try {
            val uid = requireAuthorizedUid(userId)
            val defaults = listOf(
                Triple("Kopi Susu Aa Sulthan", MenuCategory.MINUMAN, 15000L),
                Triple("Es Teh Manis Jumbo", MenuCategory.MINUMAN, 6000L),
                Triple("Jeruk Peras Hangat / Es", MenuCategory.MINUMAN, 8000L),
                Triple("Nasi Goreng Kampung Aa", MenuCategory.MAKANAN, 22000L),
                Triple("Mie Nyemek Spesial", MenuCategory.MAKANAN, 18000L),
                Triple("Ayam Geprek Sambal Korek", MenuCategory.MAKANAN, 20000L),
                Triple("Pisang Bakar Coklat Keju", MenuCategory.CEMILAN, 14000L),
                Triple("Tahu Crispy Sambal Kecap", MenuCategory.CEMILAN, 12000L),
                Triple("Paket Hemat Kopi + Pisang", MenuCategory.PAKET, 26000L)
            )
            val colRef = db.collection("users").document(uid).collection("menuItems")
            val batch = db.batch()
            for ((name, category, price) in defaults) {
                val docRef = colRef.document()
                batch.set(
                    docRef,
                    mapOf(
                        "userId" to uid,
                        "name" to name,
                        "category" to category.label,
                        "price" to price,
                        "available" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, colPath)
            Result.failure(e)
        }
    }

    suspend fun createTransaction(
        userId: String,
        cartItems: List<CartItem>,
        customerNote: String?,
        paymentMethod: PaymentMethod,
        cashReceived: Long
    ): Result<PosTransaction> {
        var docPath = "users/$userId/transactions"
        return try {
            val uid = requireAuthorizedUid(userId)
            require(cartItems.isNotEmpty() && cartItems.size <= 50) {
                "Pesanan harus berisi 1 sampai 50 jenis menu."
            }
            val totalAmount = cartItems.sumOf { it.subtotal }
            require(totalAmount in 1L..500_000_000L) { "Total tagihan tidak valid." }
            val itemCount = cartItems.sumOf { it.quantity.toLong() }
            require(itemCount in 1L..500L) { "Jumlah item maksimal 500 per transaksi." }

            val effectiveCash = if (paymentMethod == PaymentMethod.TUNAI) cashReceived else totalAmount
            require(effectiveCash >= totalAmount && effectiveCash <= 500_000_000L) {
                "Uang tunai kurang dari total tagihan."
            }
            val changeAmount = (effectiveCash - totalAmount).coerceAtLeast(0L)

            val now = Date()
            val datePrefix = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(now)
            val suffix = (100..999).random()
            val receiptNumber = "AAS-$datePrefix-$suffix"

            val cleanNote = customerNote?.trim()?.take(80)?.takeIf { it.isNotEmpty() }

            val serializedItems = cartItems.map { item ->
                mapOf(
                    "menuItemId" to item.menuItem.id,
                    "name" to item.menuItem.name.take(60),
                    "unitPrice" to item.menuItem.price,
                    "quantity" to item.quantity.toLong(),
                    "subtotal" to item.subtotal
                )
            }

            val docRef = db.collection("users").document(uid).collection("transactions").document()
            docPath = docRef.path

            val payload = mapOf<String, Any?>(
                "userId" to uid,
                "receiptNumber" to receiptNumber,
                "customerNote" to cleanNote,
                "paymentMethod" to paymentMethod.label,
                "totalAmount" to totalAmount,
                "cashReceived" to effectiveCash,
                "changeAmount" to changeAmount,
                "itemCount" to itemCount,
                "items" to serializedItems,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            docRef.set(payload).await()

            val createdTx = PosTransaction(
                id = docRef.id,
                userId = uid,
                receiptNumber = receiptNumber,
                customerNote = cleanNote,
                paymentMethod = paymentMethod.label,
                totalAmount = totalAmount,
                cashReceived = effectiveCash,
                changeAmount = changeAmount,
                itemCount = itemCount,
                items = cartItems.map { item ->
                    com.example.data.model.OrderLineItem(
                        menuItemId = item.menuItem.id,
                        name = item.menuItem.name,
                        unitPrice = item.menuItem.price,
                        quantity = item.quantity.toLong(),
                        subtotal = item.subtotal
                    )
                },
                createdAt = com.google.firebase.Timestamp(now),
                updatedAt = com.google.firebase.Timestamp(now)
            )
            Result.success(createdTx)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTransaction(userId: String, transactionId: String): Result<Unit> {
        val docPath = "users/$userId/transactions/$transactionId"
        return try {
            val uid = requireAuthorizedUid(userId)
            require(transactionId.isNotBlank()) { "ID transaksi tidak valid." }
            db.collection("users").document(uid).collection("transactions").document(transactionId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
