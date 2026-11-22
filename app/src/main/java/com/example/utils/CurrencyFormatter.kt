package com.example.utils

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CurrencyFormatter {

    private val indianLocale = Locale.Builder().setLanguage("en").setRegion("IN").build()
    private val indianSymbols = DecimalFormatSymbols(indianLocale)

    private val inrDecimalFormat = ThreadLocal.withInitial {
        DecimalFormat("##,##,##0.00", indianSymbols)
    }
    private val inrNoDecimalFormat = ThreadLocal.withInitial {
        DecimalFormat("##,##,##0", indianSymbols)
    }

    fun roundFinancialAmount(amount: Double): Double {
        return try {
            java.math.BigDecimal.valueOf(amount)
                .setScale(2, java.math.RoundingMode.HALF_EVEN)
                .toDouble()
        } catch (_: Exception) {
            amount
        }
    }

    fun formatInr(amount: Double, includeDecimals: Boolean = true): String {
        return try {
            val rounded = roundFinancialAmount(amount)
            val formatter = if (includeDecimals) inrDecimalFormat.get() else inrNoDecimalFormat.get()
            "₹ " + formatter.format(rounded)
        } catch (e: Exception) {
            "₹ " + String.format(Locale.ENGLISH, "%.2f", amount)
        }
    }

    fun formatInrCompact(amount: Double): String {
        val rounded = roundFinancialAmount(amount)
        return when {
            rounded >= 10000000 -> String.format(Locale.ENGLISH, "₹%.2f Cr", rounded / 10000000.0)
            rounded >= 100000 -> String.format(Locale.ENGLISH, "₹%.2f L", rounded / 100000.0)
            rounded >= 1000 -> String.format(Locale.ENGLISH, "₹%.1f K", rounded / 1000.0)
            else -> formatInr(rounded, includeDecimals = false)
        }
    }
}

object DateUtils {
    private val isoFormat = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH) }
    private val displayFormat = ThreadLocal.withInitial { SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH) }
    private val shortDisplayFormat = ThreadLocal.withInitial { SimpleDateFormat("dd MMM", Locale.ENGLISH) }
    private val monthFormat = ThreadLocal.withInitial { SimpleDateFormat("MMMM yyyy", Locale.ENGLISH) }
    private val dateTimeFormat = ThreadLocal.withInitial { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH) }

    fun getCurrentDate(): String = isoFormat.get().format(Date())

    fun formatForDisplay(isoDate: String): String {
        return try {
            val date = isoFormat.get().parse(isoDate) ?: return isoDate
            displayFormat.get().format(date)
        } catch (e: Exception) {
            isoDate
        }
    }

    fun formatShortDisplay(isoDate: String): String {
        return try {
            val date = isoFormat.get().parse(isoDate) ?: return isoDate
            shortDisplayFormat.get().format(date)
        } catch (e: Exception) {
            isoDate
        }
    }

    fun formatMonthHeader(isoDate: String): String {
        return try {
            val date = isoFormat.get().parse(isoDate) ?: return isoDate
            monthFormat.get().format(date)
        } catch (e: Exception) {
            isoDate
        }
    }

    fun formatDateTime(timestamp: Long): String {
        return try {
            dateTimeFormat.get().format(Date(timestamp))
        } catch (_: Exception) {
            timestamp.toString()
        }
    }
}

