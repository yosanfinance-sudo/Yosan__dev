package com.example.features.budget

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CategoryType
import com.example.features.reports.BudgetStatus
import com.example.features.reports.CategoryBudgetProgress
import com.example.features.transactions.FinanceViewModel
import com.example.ui.components.EmptyState
import com.example.ui.components.MetricCard
import com.example.ui.theme.*
import com.example.utils.CurrencyFormatter
import com.example.utils.HapticHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class BudgetMainTab(val label: String) {
    TARGETS("Category Targets"),
    OVERVIEW("Month Summary")
}

private fun formatBudgetAmount(amount: Double, privacyEnabled: Boolean, isRevealed: Boolean): String {
    return if (privacyEnabled && !isRevealed) "₹••••" else CurrencyFormatter.formatInr(amount)
}

@Composable
fun BudgetPlannerScreen(
    viewModel: FinanceViewModel,
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val categories by viewModel.categories.collectAsState()
    val allBudgets by viewModel.allBudgets.collectAsState()
    val selectedMonth by viewModel.selectedBudgetMonth.collectAsState()
    val budgetOverview by viewModel.monthlyBudgetOverview.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val isRevealed by viewModel.isAmountTemporarilyRevealed.collectAsState()
    val privacyEnabled = userProfile?.isPrivacyBlurEnabled ?: true

    var showSetBudgetDialog by remember { mutableStateOf(false) }
    var editingProgressItem by remember { mutableStateOf<CategoryBudgetProgress?>(null) }
    var deletingBudgetId by remember { mutableStateOf<Long?>(null) }
    var currentMainTab by remember { mutableStateOf(BudgetMainTab.TARGETS) }
    var selectedFilterStatus by remember { mutableStateOf<BudgetStatus?>(null) }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val monthLabel = remember(selectedMonth) {
        try {
            val parts = selectedMonth.split("-")
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, parts[0].toInt())
                set(Calendar.MONTH, parts[1].toInt() - 1)
            }
            SimpleDateFormat("MMMM yyyy", Locale.US).format(cal.time)
        } catch (_: Exception) {
            selectedMonth
        }
    }

    val currentMonthKey = remember {
        SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    }

    val distinctCategories = remember(budgetOverview.categoryBreakdowns) {
        budgetOverview.categoryBreakdowns.map { it.categoryName }.distinct()
    }

    val filteredBreakdowns = remember(
        budgetOverview.categoryBreakdowns,
        selectedFilterStatus,
        selectedCategoryFilter,
        searchQuery
    ) {
        budgetOverview.categoryBreakdowns.filter { item ->
            val matchesStatus = selectedFilterStatus == null || item.status == selectedFilterStatus
            val matchesCat = selectedCategoryFilter == null || item.categoryName.equals(selectedCategoryFilter, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() ||
                    item.categoryName.contains(searchQuery, ignoreCase = true) ||
                    (!item.subcategoryName.isNullOrBlank() && item.subcategoryName.contains(searchQuery, ignoreCase = true))
            matchesStatus && matchesCat && matchesQuery
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("budget_planner_screen"),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
            // 1. Sub-header Description & KPI Metric Summary Cards (Horizontal Carousel matching Official Expenses)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp)
                ) {
                    Text(
                        text = "Track monthly spending limits, category targets, and budget health.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            title = "Total Budget",
                            amount = budgetOverview.totalBudget,
                            icon = Icons.Default.Savings,
                            iconColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(150.dp)
                        )
                        MetricCard(
                            title = "Total Spent",
                            amount = budgetOverview.totalSpent,
                            icon = Icons.Default.TrendingDown,
                            iconColor = ExpenseRed,
                            modifier = Modifier.width(150.dp)
                        )
                        MetricCard(
                            title = if (budgetOverview.totalRemaining >= 0) "Remaining" else "Over Budget",
                            amount = kotlin.math.abs(budgetOverview.totalRemaining),
                            icon = Icons.Default.AccountBalanceWallet,
                            iconColor = if (budgetOverview.totalRemaining >= 0) IncomeGreen else ExpenseRed,
                            modifier = Modifier.width(150.dp)
                        )
                        val percentUsed = if (budgetOverview.totalBudget > 0) {
                            (budgetOverview.totalSpent / budgetOverview.totalBudget * 100.0)
                        } else 0.0
                        MetricCard(
                            title = "Budget Health",
                            amount = 0.0,
                            customValueText = String.format(Locale.US, "%.0f%%", percentUsed),
                            subtitle = if (budgetOverview.totalRemaining >= 0) "Within Limits" else "Exceeded",
                            icon = Icons.Default.PieChart,
                            iconColor = if (budgetOverview.totalRemaining >= 0) IncomeGreen else ExpenseRed,
                            modifier = Modifier.width(150.dp)
                        )
                    }
                }
            }

            // 2. Navigation Tabs (Category Targets vs Month Summary)
            item {
                TabRow(
                    selectedTabIndex = if (currentMainTab == BudgetMainTab.TARGETS) 0 else 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {}
                ) {
                    Tab(
                        selected = currentMainTab == BudgetMainTab.TARGETS,
                        onClick = {
                            HapticHelper.vibrateTick(context)
                            currentMainTab = BudgetMainTab.TARGETS
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Category Targets",
                                    fontWeight = if (currentMainTab == BudgetMainTab.TARGETS) FontWeight.Bold else FontWeight.Normal
                                )
                                if (budgetOverview.categoryBreakdowns.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = "${budgetOverview.categoryBreakdowns.size}",
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )

                    Tab(
                        selected = currentMainTab == BudgetMainTab.OVERVIEW,
                        onClick = {
                            HapticHelper.vibrateTick(context)
                            currentMainTab = BudgetMainTab.OVERVIEW
                        },
                        text = {
                            Text(
                                text = "Month Summary",
                                fontWeight = if (currentMainTab == BudgetMainTab.OVERVIEW) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // 3. Month Navigation Carousel
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = {
                                val cal = Calendar.getInstance().apply {
                                    val parts = selectedMonth.split("-")
                                    set(Calendar.YEAR, parts[0].toInt())
                                    set(Calendar.MONTH, parts[1].toInt() - 2)
                                }
                                viewModel.setBudgetMonth(SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time))
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month", modifier = Modifier.size(18.dp))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = monthLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (selectedMonth == currentMonthKey) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Current",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                val cal = Calendar.getInstance().apply {
                                    val parts = selectedMonth.split("-")
                                    set(Calendar.YEAR, parts[0].toInt())
                                    set(Calendar.MONTH, parts[1].toInt())
                                }
                                viewModel.setBudgetMonth(SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time))
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // 4. Tab Content: Category Targets
            if (currentMainTab == BudgetMainTab.TARGETS) {
                // Search Bar + Target Button & Filter Chips
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search categories or targets...", fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                            )

                            Button(
                                onClick = {
                                    editingProgressItem = null
                                    showSetBudgetDialog = true
                                },
                                modifier = Modifier.height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 14.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Target", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Status Filter Chips with Colored Indicator Dots
                        val allCount = budgetOverview.categoryBreakdowns.size
                        val onTrackCount = budgetOverview.categoryBreakdowns.count { it.status == BudgetStatus.ON_TRACK }
                        val warningCount = budgetOverview.categoryBreakdowns.count { it.status == BudgetStatus.WARNING }
                        val exceededCount = budgetOverview.categoryBreakdowns.count { it.status == BudgetStatus.EXCEEDED }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedFilterStatus == null,
                                onClick = { selectedFilterStatus = null },
                                label = { Text("All Targets ($allCount)", fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = selectedFilterStatus == BudgetStatus.ON_TRACK,
                                onClick = { selectedFilterStatus = if (selectedFilterStatus == BudgetStatus.ON_TRACK) null else BudgetStatus.ON_TRACK },
                                label = { Text("On Track ($onTrackCount)", fontSize = 12.sp) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(IncomeGreen, CircleShape)
                                    )
                                }
                            )
                            FilterChip(
                                selected = selectedFilterStatus == BudgetStatus.WARNING,
                                onClick = { selectedFilterStatus = if (selectedFilterStatus == BudgetStatus.WARNING) null else BudgetStatus.WARNING },
                                label = { Text("Nearing ($warningCount)", fontSize = 12.sp) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(OutstandingAmber, CircleShape)
                                    )
                                }
                            )
                            FilterChip(
                                selected = selectedFilterStatus == BudgetStatus.EXCEEDED,
                                onClick = { selectedFilterStatus = if (selectedFilterStatus == BudgetStatus.EXCEEDED) null else BudgetStatus.EXCEEDED },
                                label = { Text("Exceeded ($exceededCount)", fontSize = 12.sp) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(ExpenseRed, CircleShape)
                                    )
                                }
                            )
                        }

                        // Category Filter Chips
                        if (distinctCategories.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                item {
                                    FilterChip(
                                        selected = selectedCategoryFilter == null,
                                        onClick = { selectedCategoryFilter = null },
                                        label = { Text("All Categories") }
                                    )
                                }
                                items(distinctCategories) { cat ->
                                    FilterChip(
                                        selected = selectedCategoryFilter == cat,
                                        onClick = { selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat },
                                        label = { Text(cat) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Copy from Prev Month Banner if Empty
                if (budgetOverview.categoryBreakdowns.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "No Targets for $monthLabel",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Copy targets from the previous month with 1 tap or set fresh ones.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Button(
                                    onClick = {
                                        HapticHelper.vibrateClick(context)
                                        viewModel.copyBudgetsFromPreviousMonth(selectedMonth)
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Copy Prev")
                                }
                            }
                        }
                    }
                }

                // Category Breakdown Cards
                if (filteredBreakdowns.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.Savings,
                            title = "No Budget Targets Found",
                            description = "Set monthly spending limits for categories to monitor your expenses and stay on track.",
                            actionText = "Set Target",
                            onActionClick = {
                                editingProgressItem = null
                                showSetBudgetDialog = true
                            }
                        )
                    }
                } else {
                    items(filteredBreakdowns, key = { "${it.categoryId}_${it.subcategoryId ?: 0}" }) { item ->
                        CategoryBudgetCard(
                            progress = item,
                            privacyEnabled = privacyEnabled,
                            isRevealed = isRevealed,
                            onClick = {
                                editingProgressItem = item
                                showSetBudgetDialog = true
                            },
                            onLongClick = {
                                deletingBudgetId = item.budgetId
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            } else {
                // Tab 2: Month Summary / Ledger Overview
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Monthly Budget Ledger Summary",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                val isSurplus = budgetOverview.totalRemaining >= 0
                                Surface(
                                    color = if (isSurplus) IncomeGreen.copy(alpha = 0.15f) else ExpenseRed.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (isSurplus) "Surplus" else "Deficit",
                                        color = if (isSurplus) IncomeGreen else ExpenseRed,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Total Budget", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(formatBudgetAmount(budgetOverview.totalBudget, privacyEnabled, isRevealed), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Column {
                                    Text("Total Spent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(formatBudgetAmount(budgetOverview.totalSpent, privacyEnabled, isRevealed), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ExpenseRed)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Net Balance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        formatBudgetAmount(kotlin.math.abs(budgetOverview.totalRemaining), privacyEnabled, isRevealed),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (budgetOverview.totalRemaining >= 0) IncomeGreen else ExpenseRed
                                    )
                                }
                            }
                        }
                    }
                }

                // Quick Copy Previous Month Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Copy Previous Month's Budgets", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("Replicate all category spending targets from the previous calendar month.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = {
                                    HapticHelper.vibrateClick(context)
                                    viewModel.copyBudgetsFromPreviousMonth(selectedMonth)
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Copy")
                            }
                        }
                    }
                }

                // Status Distribution Cards
                val onTrackItems = budgetOverview.categoryBreakdowns.filter { it.status == BudgetStatus.ON_TRACK }
                val warningItems = budgetOverview.categoryBreakdowns.filter { it.status == BudgetStatus.WARNING }
                val exceededItems = budgetOverview.categoryBreakdowns.filter { it.status == BudgetStatus.EXCEEDED }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Status Distribution",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = IncomeGreen.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("On Track", color = IncomeGreen, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("${onTrackItems.size} Categories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = OutstandingAmber.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Nearing Limit", color = OutstandingAmber, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("${warningItems.size} Categories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = ExpenseRed.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Exceeded", color = ExpenseRed, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("${exceededItems.size} Categories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

    // Delete Confirmation Dialog
    deletingBudgetId?.let { bId ->
        AlertDialog(
            onDismissRequest = { deletingBudgetId = null },
            title = { Text("Delete Budget Target?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this category budget target? Existing transactions will not be affected.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteBudget(bId)
                        deletingBudgetId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Target")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingBudgetId = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Set / Edit Budget Dialog
    if (showSetBudgetDialog) {
        SetBudgetDialog(
            categories = categories,
            selectedMonth = selectedMonth,
            editingItem = editingProgressItem,
            onDismiss = {
                showSetBudgetDialog = false
                editingProgressItem = null
            },
            onSave = { catId, subcatId, monthYear, amount, threshold, isRollover ->
                viewModel.setOrUpdateBudget(catId, subcatId, monthYear, amount, threshold, isRollover)
                showSetBudgetDialog = false
                editingProgressItem = null
            },
            onDelete = { bId ->
                viewModel.deleteBudget(bId)
                showSetBudgetDialog = false
                editingProgressItem = null
            }
        )
    }
}

/**
 * CategoryBudgetCard matching OfficialExpenseCard from OfficialExpensesScreen
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryBudgetCard(
    progress: CategoryBudgetProgress,
    privacyEnabled: Boolean,
    isRevealed: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val catColor = try {
        Color(android.graphics.Color.parseColor(progress.colorHex))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    val statusColor = when (progress.status) {
        BudgetStatus.ON_TRACK -> IncomeGreen
        BudgetStatus.WARNING -> OutstandingAmber
        BudgetStatus.EXCEEDED -> ExpenseRed
    }

    val statusLabel = when (progress.status) {
        BudgetStatus.ON_TRACK -> "On Track"
        BudgetStatus.WARNING -> "Nearing Limit"
        BudgetStatus.EXCEEDED -> "Over Budget"
    }

    val statusIcon = when (progress.status) {
        BudgetStatus.ON_TRACK -> Icons.Default.CheckCircle
        BudgetStatus.WARNING -> Icons.Default.AccessTime
        BudgetStatus.EXCEEDED -> Icons.Default.Warning
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Row 1: Title on left, Budget Target Amount on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = progress.categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatBudgetAmount(progress.budgetedAmount, privacyEnabled, isRevealed),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sleek Progress Bar
            LinearProgressIndicator(
                progress = { (progress.percentageUsed / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: Category • Subcategory Pill Badge + Spent info on left, Status Pill Badge on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Surface(
                        color = catColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (progress.subcategoryName != null) "${progress.categoryName} • ${progress.subcategoryName}" else progress.categoryName,
                            style = MaterialTheme.typography.labelSmall,
                            color = catColor,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${formatBudgetAmount(progress.actualSpent, privacyEnabled, isRevealed)} spent (${String.format(Locale.US, "%.0f%%", progress.percentageUsed)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SetBudgetDialog(
    categories: List<CategoryEntity>,
    selectedMonth: String,
    editingItem: CategoryBudgetProgress?,
    onDismiss: () -> Unit,
    onSave: (Long, Long?, String, Double, Int, Boolean) -> Unit,
    onDelete: ((Long) -> Unit)? = null
) {
    val expenseCategories = remember(categories) {
        categories.filter { it.type == CategoryType.EXPENSE && it.parentCategoryId == null }
    }

    var selectedParentCat by remember {
        mutableStateOf(
            if (editingItem != null) {
                categories.firstOrNull { it.id == editingItem.categoryId } ?: expenseCategories.firstOrNull()
            } else {
                expenseCategories.firstOrNull()
            }
        )
    }

    var selectedSubcat by remember {
        mutableStateOf(
            if (editingItem?.subcategoryId != null) {
                categories.firstOrNull { it.id == editingItem.subcategoryId }
            } else null
        )
    }

    var amountText by remember {
        mutableStateOf(
            if (editingItem != null) {
                if (editingItem.budgetedAmount % 1.0 == 0.0) editingItem.budgetedAmount.toLong().toString()
                else String.format(Locale.US, "%.2f", editingItem.budgetedAmount)
            } else ""
        )
    }

    var isRecurring by remember {
        mutableStateOf(editingItem?.monthYear == "DEFAULT")
    }

    var alertThreshold by remember {
        mutableStateOf(editingItem?.alertThresholdPercent ?: 80)
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val availableSubcategories = remember(selectedParentCat, categories) {
        categories.filter { it.parentCategoryId == selectedParentCat?.id }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (editingItem != null) "Edit Budget Target" else "Set Category Budget",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Select Category
                Text("Select Category", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(expenseCategories) { cat ->
                        val isSel = selectedParentCat?.id == cat.id
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                selectedParentCat = cat
                                selectedSubcat = null
                            },
                            label = { Text(cat.name) }
                        )
                    }
                }

                // Subcategory Selection
                if (availableSubcategories.isNotEmpty()) {
                    Text(
                        "Subcategory (Optional)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedSubcat == null,
                                onClick = { selectedSubcat = null },
                                label = { Text("Entire Category") }
                            )
                        }
                        items(availableSubcategories) { sub ->
                            val isSubSel = selectedSubcat?.id == sub.id
                            FilterChip(
                                selected = isSubSel,
                                onClick = { selectedSubcat = if (isSubSel) null else sub },
                                label = { Text(sub.name) }
                            )
                        }
                    }
                }

                // Budget Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it.filter { ch -> ch.isDigit() || ch == '.' }
                        errorMessage = null
                    },
                    label = { Text("Monthly Budget Amount") },
                    prefix = { Text("₹ ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                // Recurring Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isRecurring = !isRecurring }
                ) {
                    Checkbox(checked = isRecurring, onCheckedChange = { isRecurring = it })
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("Recurring Monthly Budget", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (isRecurring) "Applies automatically every month" else "Applies only to $selectedMonth",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Alert Threshold
                Text("Warning Threshold ($alertThreshold%)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(70, 80, 90).forEach { threshold ->
                        FilterChip(
                            selected = alertThreshold == threshold,
                            onClick = { alertThreshold = threshold },
                            label = { Text("$threshold%") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cat = selectedParentCat
                    if (cat == null) {
                        errorMessage = "Please select a category"
                        return@Button
                    }
                    val amt = amountText.toDoubleOrNull()
                    if (amt == null || amt <= 0.0) {
                        errorMessage = "Please enter a valid amount greater than 0"
                        return@Button
                    }
                    val targetMonth = if (isRecurring) "DEFAULT" else selectedMonth
                    onSave(cat.id, selectedSubcat?.id, targetMonth, amt, alertThreshold, false)
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save Target")
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (editingItem != null && editingItem.budgetId > 0L && onDelete != null) {
                    TextButton(onClick = { onDelete(editingItem.budgetId) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
