package com.example.features.messages

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.entity.PendingStatus
import com.example.data.local.entity.PendingTransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.features.rules.CategorizationEngine
import com.example.utils.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MessageIngestionManager {

    private const val TAG = "MessageIngestion"
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Ingests an incoming raw message (SMS or Push Notification),
     * parses it using the universal bank-agnostic grammar,
     * and stages it in the pending_transactions table awaiting user approval.
     */
    suspend fun processIncomingMessage(
        context: Context,
        rawText: String,
        source: String,
        timestamp: Long = System.currentTimeMillis(),
        ignoreTrackingSwitch: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val appPrefs = AppPreferences(context)
        if (!ignoreTrackingSwitch) {
            if (!appPrefs.isMessageTrackingEnabled) {
                return@withContext false
            }

            val mode = appPrefs.messageTrackingMode
            if (mode == "SMS_ONLY" && source.startsWith("Notification", ignoreCase = true)) {
                return@withContext false
            }
            if (mode == "NOTIFICATIONS_ONLY" && source.startsWith("SMS", ignoreCase = true)) {
                return@withContext false
            }
        }

        val parsed = UniversalFinancialMessageParser.parse(rawText, source, timestamp) ?: return@withContext false

        try {
            val db = AppDatabase.getDatabase(context, scope)
            val dedupeKey = parsed.deduplicationKey()

            // 1. Check if already staged in pending table
            val existing = db.pendingTransactionDao().getByDeduplicationKey(dedupeKey)
            if (existing != null) {
                Log.d(TAG, "Duplicate transaction detected in pending for dedupeKey: $dedupeKey, skipping")
                return@withContext false
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val dateStr = dateFormat.format(Date(timestamp))

            // 2. Cross-Source Deduplication Check:
            // Check if this transaction already exists in the statement transactions table
            // (e.g. from an uploaded PDF/Excel statement, manual entry, or previously approved message)
            val existingInStatement = if (!parsed.referenceNumber.isNullOrBlank()) {
                db.transactionDao().getTransactionByReferenceNumber(parsed.referenceNumber)
            } else {
                db.transactionDao().getTransactionByDateAmountType(dateStr, parsed.amount, parsed.type)
            }

            if (existingInStatement != null) {
                Log.d(TAG, "Cross-source deduplication: Transaction already recorded in statement (#${existingInStatement.id}, date=${existingInStatement.transactionDate}, amount=${existingInStatement.amount}, ref=${existingInStatement.referenceNumber}). Skipping staging.")
                return@withContext false
            }

            // Categorization Engine
            val activeRules = db.categorizationRuleDao().getActiveRulesList()
            val textToCategorize = "${parsed.merchant.orEmpty()} ${parsed.rawText}".trim()
            val catResult = CategorizationEngine.categorize(
                description = textToCategorize,
                rules = activeRules,
                isCredit = parsed.type == TransactionType.INCOME
            )

            // Account Matching
            val allAccounts = db.accountDao().getAllAccountsList()
            val matchedAccount = if (!parsed.accountOrCardMask.isNullOrBlank()) {
                allAccounts.firstOrNull { it.accountNumberMasked.endsWith(parsed.accountOrCardMask) }
            } else null
            val accountId = matchedAccount?.id ?: 0L

            val pendingEntity = PendingTransactionEntity(
                amount = parsed.amount,
                transactionType = parsed.type,
                description = parsed.rawText.trim(),
                merchant = parsed.merchant,
                categoryId = catResult.categoryId,
                categoryName = catResult.categoryName,
                subcategoryId = null,
                subcategoryName = null,
                accountId = accountId,
                accountOrCardMask = parsed.accountOrCardMask,
                referenceNumber = parsed.referenceNumber,
                runningBalance = parsed.balance,
                transactionDate = dateStr,
                timestamp = timestamp,
                source = parsed.source,
                rawMessage = parsed.rawText,
                deduplicationKey = dedupeKey,
                status = PendingStatus.PENDING,
                createdAt = System.currentTimeMillis()
            )

            val insertedId = db.pendingTransactionDao().insert(pendingEntity)
            Log.d(TAG, "Successfully staged pending transaction #$insertedId from ${parsed.source}")
            return@withContext insertedId > 0L
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stage incoming financial message", e)
            return@withContext false
        }
    }

    /**
     * Scans the Android SMS inbox for past bank/UPI messages
     * within the specified number of days and stages them for user review.
     */
    suspend fun scanSmsInbox(context: Context, daysBack: Int = 30): Int = withContext(Dispatchers.IO) {
        val appPrefs = AppPreferences(context)
        appPrefs.isMessageTrackingEnabled = true
        var stagedCount = 0
        val cutoffTime = System.currentTimeMillis() - (daysBack.toLong() * 24 * 60 * 60 * 1000)

        val uri = Uri.parse("content://sms/inbox")
        val projection = arrayOf("_id", "address", "body", "date")
        val selection = "date >= ?"
        val selectionArgs = arrayOf(cutoffTime.toString())
        val sortOrder = "date DESC"

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
            if (cursor != null && cursor.moveToFirst()) {
                val addressIdx = cursor.getColumnIndex("address")
                val bodyIdx = cursor.getColumnIndex("body")
                val dateIdx = cursor.getColumnIndex("date")

                do {
                    val address = if (addressIdx >= 0) cursor.getString(addressIdx) else "SMS"
                    val body = if (bodyIdx >= 0) cursor.getString(bodyIdx) else ""
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else System.currentTimeMillis()

                    if (body.isNotBlank()) {
                        val staged = processIncomingMessage(
                            context = context,
                            rawText = body,
                            source = "SMS: $address",
                            timestamp = date,
                            ignoreTrackingSwitch = true
                        )
                        if (staged) {
                            stagedCount++
                        }
                    }
                } while (cursor.moveToNext())
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission denied while scanning SMS inbox: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error while scanning SMS inbox", e)
        } finally {
            cursor?.close()
        }

        return@withContext stagedCount
    }
}
