package com.example.data.local

import com.example.data.local.entity.*

object DatabaseInitializer {

    suspend fun purgeLegacyDemoData(database: AppDatabase) {
        val accountDao = database.accountDao()
        val categoryDao = database.categoryDao()
        val ruleDao = database.categorizationRuleDao()
        val profileDao = database.userProfileDao()
        val loanDao = database.loanDao()
        val companyExpenseDao = database.companyExpenseDao()
        val transactionDao = database.transactionDao()

        // Purge all old demo data from existing database tables
        transactionDao.deleteAllTransactions()
        loanDao.deleteAllLoans()
        database.loanRepaymentDao().deleteAllRepayments()
        companyExpenseDao.deleteAllCompanyExpenses()
        accountDao.deleteAllAccounts()
        categoryDao.deleteAllCategories()
        ruleDao.deleteAllRules()

        // Re-seed clean essential defaults (categories and profile)
        seedInitialData(database)
    }

    suspend fun seedInitialData(database: AppDatabase) {
        val categoryDao = database.categoryDao()
        val profileDao = database.userProfileDao()

        // 1. Seed Profile if none exists or reset
        val existingProfile = profileDao.getUserProfileOnce()
        if (existingProfile == null) {
            profileDao.insertOrUpdateProfile(
                UserProfileEntity(
                    id = 1,
                    name = "User",
                    email = "",
                    currencySymbol = "₹",
                    isDarkMode = false,
                    isOnboardingCompleted = false
                )
            )
        }

        seedDefaultSubcategoriesIfMissing(database)
    }

    suspend fun seedDefaultSubcategoriesIfMissing(database: AppDatabase) {
        val categoryDao = database.categoryDao()
        val allCats = categoryDao.getAllCategoriesList()

        val subcategoriesMap = mapOf(
            "Shopping" to listOf("Clothing", "Electronics", "Groceries", "Home Essentials", "Online Shopping"),
            "Food" to listOf("Restaurants", "Food Delivery", "Groceries", "Cafes & Snacks"),
            "Dining" to listOf("Restaurants", "Food Delivery", "Groceries", "Cafes & Snacks"),
            "Travel" to listOf("Fuel", "Cabs & Auto", "Public Transit", "Flights & Hotels"),
            "Transportation" to listOf("Fuel", "Cabs & Auto", "Public Transit", "Maintenance"),
            "Bills" to listOf("Electricity", "Mobile & WiFi", "Water & Gas", "Subscriptions", "Rent"),
            "Utilities" to listOf("Electricity", "Mobile & WiFi", "Water & Gas", "Subscriptions"),
            "Entertainment" to listOf("Movies & Events", "OTT & Streaming", "Gaming"),
            "Health" to listOf("Pharmacy & Medicine", "Doctor Consultation", "Gym & Fitness"),
            "Medical" to listOf("Pharmacy & Medicine", "Doctor Consultation", "Hospital"),
            "Investment" to listOf("Mutual Funds", "Stocks", "Fixed Deposits", "Gold")
        )

        val existingParents = allCats.filter { it.parentCategoryId == null }

        for (parent in existingParents) {
            val matchingKey = subcategoriesMap.keys.firstOrNull { key ->
                parent.name.contains(key, ignoreCase = true)
            }
            if (matchingKey != null) {
                val hasSubs = allCats.any { it.parentCategoryId == parent.id }
                if (!hasSubs) {
                    val defaults = subcategoriesMap[matchingKey] ?: emptyList()
                    defaults.forEach { subName ->
                        categoryDao.insertCategory(
                            CategoryEntity(
                                name = subName,
                                type = parent.type,
                                colorHex = parent.colorHex,
                                parentCategoryId = parent.id
                            )
                        )
                    }
                }
            }
        }
    }
}
