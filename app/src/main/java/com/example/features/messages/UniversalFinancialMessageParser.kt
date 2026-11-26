package com.example.features.messages

import com.example.data.local.entity.TransactionType
import java.util.regex.Pattern

data class ParsedFinancialMessage(
    val amount: Double,
    val type: TransactionType,
    val merchant: String?,
    val accountOrCardMask: String?,
    val referenceNumber: String?,
    val balance: Double?,
    val timestamp: Long,
    val rawText: String,
    val source: String,
    val confidence: Float = 0.95f
) {
    /**
     * Deduplication key to prevent processing identical transactions
     * if both SMS and push notifications arrive for the same payment.
     */
    fun deduplicationKey(): String {
        val cleanMerchant = merchant?.lowercase()?.replace(Regex("[^a-z0-9]"), "") ?: "unknown"
        val cleanAcc = accountOrCardMask ?: "none"
        val ref = referenceNumber ?: ""
        return if (ref.isNotBlank()) {
            "ref_${ref}"
        } else {
            "${(amount * 100).toLong()}_${type.name}_${cleanAcc}_${cleanMerchant}"
        }
    }
}

/**
 * Universal Bank-Agnostic Financial Message Parser
 * 
 * Works across any Indian or international bank, credit card issuer, or UPI app (Google Pay,
 * PhonePe, Paytm, CRED, etc.) by applying a 3-layer deterministic grammar without relying
 * on hardcoded bank templates.
 */
object UniversalFinancialMessageParser {

    // Layer 1: Strict Negative Constraint Filter (Reject OTPs, Spam, Marketing, Bill Reminders)
    private val OTP_PATTERNS = listOf(
        Regex("(?i)\\b(otp|one[ -]time[ -]password|verification[ -]code|security[ -]code|login[ -]code)\\b"),
        Regex("(?i)\\b(secret[ -]pin|do not share|valid for \\d+ mins?|valid till|is your code)\\b"),
        Regex("(?i)\\b(pre[ -]approved|apply (?:now|for)|congratulations|you have won|cashback won|discount coupon)\\b"),
        Regex("(?i)\\b(flat \\d+% off|claim your|click here|statement generated|payment due|due on \\d+)\\b")
    )

    // Layer 2: Entity Extraction Regular Expressions (Bank-Agnostic)
    private val AMOUNT_PATTERNS = listOf(
        Regex("(?i)(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)"),
        Regex("(?i)([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:Rs\\.?|INR|₹)"),
        Regex("(?i)(?:debited|credited|paid|spent|transferred|withdrawn|received)\\s*(?:by|for|of)?\\s*(?:Rs\\.?|INR|₹)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)")
    )

    private val DEBIT_KEYWORDS = listOf(
        "debited", "spent", "paid", "sent", "withdrawn", "deducted",
        "transferred to", "txn of", "purchase", "purchased", "debit"
    )

    private val CREDIT_KEYWORDS = listOf(
        "credited", "received", "refunded", "deposited", "added to",
        "transferred from", "cashback of", "credit"
    )

    private val ACCOUNT_PATTERNS = listOf(
        Regex("(?i)(?:a/c|ac|acct|account|card)\\s*(?:no\\.)?\\s*(?:ending(?: with)?)?\\s*[*xX\\s]{0,8}(\\d{3,4})"),
        Regex("(?i)(?:ending with|ending in)\\s*(\\d{4})")
    )

    private val MERCHANT_PATTERNS = listOf(
        Regex("(?i)(?:to|at|info\\*|vpa|paid to|sent to|by)\\s+([A-Za-z0-9\\.\\-_&@ ]{2,32}?)(?=\\s+(?:on|ref|rrn|utr|upi|avl|bal|val|via|using|through|from|\\.|,|$))"),
        Regex("(?i)(?:to:)([A-Za-z0-9\\.\\-_&@]+)"),
        Regex("(?i)\\b([A-Za-z0-9\\.\\-_]+@[a-zA-Z]{3,})\\b")
    )

    private val REFERENCE_PATTERNS = listOf(
        Regex("(?i)(?:upi\\s*ref(?:erence)?(?:\\s*no)?|rrn|utr|ref\\s*no|txn\\s*id|transaction\\s*id)\\s*[:#]?\\s*([A-Za-z0-9]{6,22})"),
        Regex("(?i)\\bupi\\s*[:#]?\\s*(\\d{6,18})"),
        Regex("(?i)(?:ref(?:erence)?|utr)\\s*:?\\s*(\\d{12})")
    )

    private val BALANCE_PATTERNS = listOf(
        Regex("(?i)(?:bal(?:ance)?|avl\\s*bal(?:ance)?|available balance)[\\s:]+(?:INR|Rs\\.?|₹)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)")
    )

    /**
     * Parses an incoming raw text message (from SMS or Push Notification)
     * and extracts structured financial transaction data.
     * 
     * Returns null if the message is an OTP, marketing, non-financial, or cannot be parsed.
     */
    fun parse(rawText: String, source: String, timestamp: Long = System.currentTimeMillis()): ParsedFinancialMessage? {
        val cleanText = rawText.trim()
        if (cleanText.isBlank()) return null

        // Layer 0: Check if from a personal 10-digit number for SMS (ignore spam/phishing)
        if (source.startsWith("SMS", ignoreCase = true)) {
            val sender = source.substringAfter(":").trim()
            if (sender.matches(Regex("^\\+?\\d{10,13}$"))) {
                return null // Ignore direct personal SMS
            }
        }

        // Layer 1: Negative constraint rejection (Drop OTPs, login verification, marketing)
        for (otpRegex in OTP_PATTERNS) {
            if (otpRegex.containsMatchIn(cleanText)) {
                // If contains OTP/security code, drop immediately unless there's an explicit debited/credited amount confirmation
                val hasExplicitDebit = cleanText.contains("debited", ignoreCase = true) || cleanText.contains("credited", ignoreCase = true)
                val isStrictOtp = cleanText.contains("otp", ignoreCase = true) || cleanText.contains("verification code", ignoreCase = true)
                if (isStrictOtp || !hasExplicitDebit) {
                    return null
                }
            }
        }

        // Layer 2: Extract Amount
        var extractedAmount: Double? = null
        for (amtRegex in AMOUNT_PATTERNS) {
            val match = amtRegex.find(cleanText)
            if (match != null) {
                val groupVal = match.groupValues[1].replace(",", "").trim()
                val parsed = groupVal.toDoubleOrNull()
                if (parsed != null && parsed > 0.0) {
                    extractedAmount = parsed
                    break
                }
            }
        }
        if (extractedAmount == null) return null // No valid financial amount found

        // Layer 2: Determine Direction (Debit vs Credit)
        val lowerText = cleanText.lowercase()
        val debitIndex = DEBIT_KEYWORDS.map { lowerText.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: -1
        val creditIndex = CREDIT_KEYWORDS.map { lowerText.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: -1

        val transactionType: TransactionType = when {
            debitIndex >= 0 && creditIndex >= 0 -> if (debitIndex < creditIndex) TransactionType.EXPENSE else TransactionType.INCOME
            debitIndex >= 0 -> TransactionType.EXPENSE
            creditIndex >= 0 -> TransactionType.INCOME
            else -> return null // Neither debit nor credit action confirmed
        }

        // Layer 2: Extract Masked Account / Card
        var accountMask: String? = null
        for (accRegex in ACCOUNT_PATTERNS) {
            val match = accRegex.find(cleanText)
            if (match != null) {
                accountMask = match.groupValues[1].trim()
                break
            }
        }

        // Layer 2: Extract Merchant / Payee
        var merchant: String? = null
        for (merchRegex in MERCHANT_PATTERNS) {
            val match = merchRegex.find(cleanText)
            if (match != null) {
                val rawMerch = match.groupValues[1].trim()
                val cleaned = sanitizeMerchant(rawMerch)
                if (cleaned.isNotBlank() && cleaned.length >= 2) {
                    merchant = cleaned
                    break
                }
            }
        }

        // If merchant still null, try extracting from VPA format
        if (merchant == null) {
            val vpaMatch = Regex("(?i)([A-Za-z0-9.\\-_]+)@([a-zA-Z]+)").find(cleanText)
            if (vpaMatch != null) {
                merchant = sanitizeMerchant(vpaMatch.groupValues[1])
            }
        }

        // Layer 2: Extract Reference / UTR
        var referenceNumber: String? = null
        for (refRegex in REFERENCE_PATTERNS) {
            val match = refRegex.find(cleanText)
            if (match != null) {
                referenceNumber = match.groupValues[1].trim()
                break
            }
        }

        // Layer 2: Extract Running Balance
        var balance: Double? = null
        for (balRegex in BALANCE_PATTERNS) {
            val match = balRegex.find(cleanText)
            if (match != null) {
                val cleanBal = match.groupValues[1].replace(",", "").trim()
                balance = cleanBal.toDoubleOrNull()
                break
            }
        }

        return ParsedFinancialMessage(
            amount = extractedAmount,
            type = transactionType,
            merchant = merchant ?: if (transactionType == TransactionType.EXPENSE) "Card / UPI Transaction" else "Bank Inflow",
            accountOrCardMask = accountMask,
            referenceNumber = referenceNumber,
            balance = balance,
            timestamp = timestamp,
            rawText = cleanText,
            source = source,
            confidence = 0.95f
        )
    }

    private fun sanitizeMerchant(raw: String): String {
        return raw.trim()
            .replace(Regex("(?i)^(vpa|to|at|info\\*)\\s+"), "")
            .replace(Regex("[.,;:*\\-_]+$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
