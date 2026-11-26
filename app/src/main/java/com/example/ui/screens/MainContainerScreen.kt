package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.entity.LoanEntity
import com.example.data.local.entity.TransactionEntity
import com.example.ui.components.SmartRulePromptDialog
import com.example.ui.components.OnboardingPreferencesDialog
import com.example.ui.theme.*
import com.example.features.auth.AuthViewModel
import com.example.features.dashboard.DashboardScreen
import com.example.features.settings.SettingsScreen
import com.example.features.sync.ExcelSyncScreen
import com.example.features.transactions.FinanceViewModel
import com.example.features.transactions.CompanyExpensePrompt
import com.example.features.transactions.AddTab
import com.example.features.transactions.TransactionsScreen
import com.example.features.transactions.AddTransactionSheet
import com.example.features.transactions.TransactionDetailSheet
import com.example.features.rules.RulesScreen
import com.example.features.official_expenses.OfficialExpensesScreen
import com.example.features.reports.ReportsScreen
import com.example.features.import.ImportStatementScreen
import com.example.features.lending.LendingScreen
import com.example.features.lending.BorrowingScreen
import com.example.features.transactions.PersonalExpensesScreen
import com.example.features.transactions.InvestmentsScreen
import com.example.features.transactions.TransfersScreen
import com.example.features.ai.FinancialChatSheet
import com.example.features.walkthrough.IntroductionNoteDialog
import com.example.features.walkthrough.SpotlightTourManager
import com.example.features.walkthrough.SpotlightTourOverlay
import com.example.features.walkthrough.SpotlightStep
import com.example.features.walkthrough.UserWalkthroughDialog
import com.example.features.budget.BudgetPlannerScreen
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import com.example.utils.AppPreferences
import kotlinx.coroutines.launch

enum class MainTab(
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val testTag: String
) {
    DASHBOARD("Home", Icons.Outlined.Home, Icons.Default.Home, "tab_dashboard"),
    TRANSACTIONS("Statement", Icons.Outlined.ReceiptLong, Icons.Default.ReceiptLong, "tab_transactions"),
    REPORTS("Reports", Icons.Outlined.Assessment, Icons.Default.Assessment, "tab_reports"),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Default.Settings, "tab_settings")
}

enum class SubScreen {
    NONE, RULES, COMPANY_EXPENSES, REPORTS, IMPORT_STATEMENT, LENDING, BORROWING, PERSONAL_EXPENSES, INVESTMENTS, TRANSFERS, EXCEL_SYNC, BUDGET_PLANNER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContainerScreen(
    viewModel: FinanceViewModel = viewModel(),
    authViewModel: AuthViewModel? = null
) {
    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context) }

    var currentTab by remember { mutableStateOf(MainTab.DASHBOARD) }
    var currentSubScreen by remember { mutableStateOf(SubScreen.NONE) }

    // Dialog & Sheet States
    var showAddSheet by remember { mutableStateOf(false) }
    var showAiChatSheet by remember { mutableStateOf(false) }
    var autoOpenGeminiConfig by remember { mutableStateOf(false) }
    var showGoogleAiOptInDialog by remember { mutableStateOf(false) }
    var addSheetTab by remember { mutableStateOf(AddTab.EXPENSE) }
    var preselectedLoanForRepay by remember { mutableStateOf<LoanEntity?>(null) }
    var selectedTransactionForDetail by remember { mutableStateOf<TransactionEntity?>(null) }

    // Adaptive Refresh Rate Boost for Navigation & Bottom Sheet Transitions
    LaunchedEffect(currentTab) {
        com.example.utils.AdaptiveRefreshRateManager.requestHighRate("tab_change", 450L)
    }
    LaunchedEffect(currentSubScreen) {
        com.example.utils.AdaptiveRefreshRateManager.requestHighRate("subscreen_change", 450L)
    }
    LaunchedEffect(showAddSheet, showAiChatSheet, selectedTransactionForDetail) {
        com.example.utils.AdaptiveRefreshRateManager.requestHighRate("sheet_transition", 450L)
    }

    val userProfile by viewModel.userProfile.collectAsState()

    LaunchedEffect(userProfile?.email) {
        val currentEmail = userProfile?.email
        if (!appPreferences.isProductTourCompletedForUser(currentEmail)) {
            currentSubScreen = SubScreen.IMPORT_STATEMENT
            SpotlightTourManager.startTour()
        }
    }
    var showIntroNoteDialog by remember { mutableStateOf(false) }
    var showUserWalkthroughDialog by remember { mutableStateOf(false) }

    val smartRulePrompt by viewModel.smartRulePrompt.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()
    val isFloatingEffectEnabled by viewModel.isFloatingEffectEnabled.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiMessage) {
        uiMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissMessage()
        }
    }

    val companyExpensePrompt by viewModel.companyExpensePrompt.collectAsState()

    companyExpensePrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissCompanyExpensePrompt() },
            title = {
                Text(
                    text = if (prompt.isReimbursement) "Detect Reimbursement" else "Detect Official Expense",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (prompt.isReimbursement) {
                        "We detected a potential reimbursement of ₹${prompt.amount} on ${prompt.date}: '${prompt.description}'. Would you like to mark the matching outstanding official expense as reimbursed?"
                    } else {
                        "We detected an official transaction '${prompt.description}' of ₹${prompt.amount} on ${prompt.date}. Would you like to log it as an Official Expense?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (prompt.isReimbursement) {
                            viewModel.acceptReimbursementPrompt(prompt)
                        } else {
                            viewModel.acceptCompanyExpensePrompt(prompt)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCompanyExpensePrompt() }) {
                    Text("Skip")
                }
            }
        )
    }

    val isBackNavEnabled = SpotlightTourManager.activeStep != null || showIntroNoteDialog || showUserWalkthroughDialog || currentSubScreen != SubScreen.NONE || currentTab != MainTab.DASHBOARD || showAddSheet || selectedTransactionForDetail != null

    BackHandler(enabled = isBackNavEnabled) {
        com.example.utils.HapticHelper.vibrateClick(context)
        when {
            SpotlightTourManager.activeStep != null -> {
                currentTab = MainTab.DASHBOARD
                currentSubScreen = SubScreen.NONE
                appPreferences.setProductTourCompletedForUser(userProfile?.email, true)
                SpotlightTourManager.dismissTour()
            }
            showIntroNoteDialog -> {
                appPreferences.setIntroNoteDismissedForUser(userProfile?.email, true)
                showIntroNoteDialog = false
            }
            showUserWalkthroughDialog -> showUserWalkthroughDialog = false
            selectedTransactionForDetail != null -> selectedTransactionForDetail = null
            showAddSheet -> {
                showAddSheet = false
                preselectedLoanForRepay = null
            }
            currentSubScreen != SubScreen.NONE -> currentSubScreen = SubScreen.NONE
            currentTab != MainTab.DASHBOARD -> currentTab = MainTab.DASHBOARD
        }
    }

    CompositionLocalProvider(com.example.ui.components.LocalFloatingEffectEnabled provides isFloatingEffectEnabled) {
        Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {

            if (currentSubScreen != SubScreen.NONE) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = when (currentSubScreen) {
                                    SubScreen.RULES -> "Categorization"
                                    SubScreen.COMPANY_EXPENSES -> "Official Expenses"
                                    SubScreen.REPORTS -> "Reports"
                                    SubScreen.IMPORT_STATEMENT -> "Import Statement"
                                    SubScreen.LENDING -> "Loans & Lending"
                                    SubScreen.BORROWING -> "Money Borrowed & Debts"
                                    SubScreen.PERSONAL_EXPENSES -> "Personal Expenses"
                                    SubScreen.INVESTMENTS -> "Investments & Assets"
                                    SubScreen.TRANSFERS -> "Rotational / Transfers"
                                    SubScreen.EXCEL_SYNC -> "Excel Two-Way Sync"
                                    SubScreen.BUDGET_PLANNER -> "Budget Planner"
                                    SubScreen.NONE -> ""
                                },
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            val subtitle = when (currentSubScreen) {
                                SubScreen.RULES -> "Automatically categorize transactions using your rules."
                                SubScreen.COMPANY_EXPENSES -> "Track official expenses, pending & applied claims, reimbursements, and advance sets."
                                SubScreen.REPORTS -> "Monthly trends & category spending breakdowns"
                                SubScreen.IMPORT_STATEMENT -> "Upload bank statements in Excel (.xlsx, .xls) or PDF (.pdf) formats"
                                SubScreen.LENDING -> "Track money you have lent and repayments received"
                                SubScreen.BORROWING -> "Track borrowed funds from contacts or lenders and settlements"
                                SubScreen.PERSONAL_EXPENSES -> "Track day-to-day personal living expenses and inflows"
                                SubScreen.INVESTMENTS -> "Track capital allocations, stocks, mutual funds, and assets"
                                SubScreen.TRANSFERS -> "Track self transfers and inter-account movements"
                                SubScreen.EXCEL_SYNC -> "Bidirectional synchronization with Microsoft Excel (.xlsx)"
                                SubScreen.BUDGET_PLANNER -> "Set monthly and category spending limits and monitor progress"
                                SubScreen.NONE -> ""
                            }
                            if (subtitle.isNotEmpty()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            com.example.utils.HapticHelper.vibrateClick(context)
                            currentSubScreen = SubScreen.NONE
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            if (currentSubScreen == SubScreen.NONE) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, bottom = 12.dp, top = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                        ),
                        shadowElevation = 8.dp,
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MainTab.values().forEach { tab ->
                                val isSelected = currentTab == tab
                                val activeColor = MaterialTheme.colorScheme.primary
                                val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                val itemColor = if (isSelected) activeColor else inactiveColor

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            com.example.utils.HapticHelper.vibrateTick(context)
                                            currentTab = tab
                                        }
                                        .testTag(tab.testTag),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) tab.selectedIcon else tab.icon,
                                            contentDescription = tab.title,
                                            tint = itemColor,
                                            modifier = Modifier.size(23.dp)
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = tab.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                            fontSize = 11.sp,
                                            color = itemColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentSubScreen,
                transitionSpec = {
                    if (targetState != SubScreen.NONE && initialState == SubScreen.NONE) {
                        (slideInHorizontally(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)) { (it * 0.35f).toInt() } +
                                scaleIn(initialScale = 0.95f, animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                                fadeIn(tween(220, easing = LinearOutSlowInEasing))).togetherWith(
                            slideOutHorizontally(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)) { (-it * 0.15f).toInt() } +
                                    scaleOut(targetScale = 0.96f, animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                                    fadeOut(tween(180, easing = FastOutLinearInEasing))
                        )
                    } else if (targetState == SubScreen.NONE && initialState != SubScreen.NONE) {
                        (slideInHorizontally(animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)) { (-it * 0.15f).toInt() } +
                                scaleIn(initialScale = 0.96f, animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                                fadeIn(tween(220, easing = LinearOutSlowInEasing))).togetherWith(
                            slideOutHorizontally(animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)) { (it * 0.35f).toInt() } +
                                    scaleOut(targetScale = 0.95f, animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                                    fadeOut(tween(180, easing = FastOutLinearInEasing))
                        )
                    } else {
                        (slideInHorizontally(animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)) { (it * 0.25f).toInt() } +
                                fadeIn(tween(220, easing = LinearOutSlowInEasing))).togetherWith(
                            slideOutHorizontally(animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)) { (-it * 0.25f).toInt() } +
                                    fadeOut(tween(180, easing = FastOutLinearInEasing))
                        )
                    }.using(SizeTransform(clip = false))
                },
                label = "SubScreenAnimatedContent"
            ) { subScreen ->
                if (subScreen != SubScreen.NONE) {
                    when (subScreen) {
                        SubScreen.RULES -> RulesScreen(viewModel = viewModel)
                        SubScreen.COMPANY_EXPENSES -> OfficialExpensesScreen(
                            viewModel = viewModel,
                            onAddExpenseClick = {
                                addSheetTab = AddTab.COMPANY_EXPENSE
                                showAddSheet = true
                            }
                        )
                        SubScreen.REPORTS -> ReportsScreen(viewModel = viewModel)
                        SubScreen.IMPORT_STATEMENT -> ImportStatementScreen(
                            viewModel = viewModel,
                            onImportSuccess = {
                                currentSubScreen = SubScreen.NONE
                                currentTab = MainTab.TRANSACTIONS
                            },
                            initialTab = if (SpotlightTourManager.activeStep == SpotlightStep.IMPORT_HISTORY) 1 else 0
                        )
                        SubScreen.LENDING -> LendingScreen(
                            viewModel = viewModel,
                            onAddLoanClick = {
                                addSheetTab = AddTab.LEND_MONEY
                                preselectedLoanForRepay = null
                                showAddSheet = true
                            },
                            onRepayLoanClick = { loan ->
                                preselectedLoanForRepay = loan
                                addSheetTab = AddTab.LEND_MONEY
                                showAddSheet = true
                            }
                        )
                        SubScreen.BORROWING -> BorrowingScreen(
                            viewModel = viewModel,
                            onAddBorrowClick = {
                                addSheetTab = AddTab.BORROW_MONEY
                                showAddSheet = true
                            },
                            onTransactionClick = { tx -> selectedTransactionForDetail = tx }
                        )
                        SubScreen.PERSONAL_EXPENSES -> PersonalExpensesScreen(
                            viewModel = viewModel,
                            onAddExpenseClick = {
                                addSheetTab = AddTab.EXPENSE
                                showAddSheet = true
                            },
                            onTransactionClick = { tx -> selectedTransactionForDetail = tx }
                        )
                        SubScreen.INVESTMENTS -> InvestmentsScreen(
                            viewModel = viewModel,
                            onAddInvestmentClick = {
                                addSheetTab = AddTab.INVESTMENT
                                showAddSheet = true
                            },
                            onTransactionClick = { tx -> selectedTransactionForDetail = tx }
                        )
                        SubScreen.TRANSFERS -> TransfersScreen(
                            viewModel = viewModel,
                            onAddTransferClick = {
                                addSheetTab = AddTab.TRANSFER
                                showAddSheet = true
                            },
                            onTransactionClick = { tx -> selectedTransactionForDetail = tx }
                        )
                        SubScreen.EXCEL_SYNC -> ExcelSyncScreen(viewModel = viewModel)
                        SubScreen.BUDGET_PLANNER -> BudgetPlannerScreen(
                            viewModel = viewModel,
                            onBackClick = { currentSubScreen = SubScreen.NONE }
                        )
                        SubScreen.NONE -> {}
                    }
                } else {
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            val initialIndex = initialState.ordinal
                            val targetIndex = targetState.ordinal
                            if (targetIndex > initialIndex) {
                                (slideInHorizontally(animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)) { (it * 0.2f).toInt() } +
                                        scaleIn(initialScale = 0.98f, animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                                        fadeIn(tween(220, easing = LinearOutSlowInEasing))).togetherWith(
                                    slideOutHorizontally(animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)) { (-it * 0.2f).toInt() } +
                                            scaleOut(targetScale = 0.98f, animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                                            fadeOut(tween(180, easing = FastOutLinearInEasing))
                                )
                            } else {
                                (slideInHorizontally(animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)) { (-it * 0.2f).toInt() } +
                                        scaleIn(initialScale = 0.98f, animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                                        fadeIn(tween(220, easing = LinearOutSlowInEasing))).togetherWith(
                                    slideOutHorizontally(animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)) { (it * 0.2f).toInt() } +
                                            scaleOut(targetScale = 0.98f, animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                                            fadeOut(tween(180, easing = FastOutLinearInEasing))
                                )
                            }.using(
                                SizeTransform(clip = false)
                            )
                        },
                        label = "TabTransition"
                    ) { tab ->
                        when (tab) {
                            MainTab.DASHBOARD -> DashboardScreen(
                                viewModel = viewModel,
                                onNavigateToTransactions = { currentTab = MainTab.TRANSACTIONS },
                                onNavigateToImport = { currentSubScreen = SubScreen.IMPORT_STATEMENT },
                                onOpenAddSheet = {
                                    addSheetTab = AddTab.EXPENSE
                                    preselectedLoanForRepay = null
                                    showAddSheet = true
                                },
                                onTransactionClick = { tx -> selectedTransactionForDetail = tx },
                                onNavigateToCompanyExpenses = { currentSubScreen = SubScreen.COMPANY_EXPENSES },
                                onNavigateToLending = { currentSubScreen = SubScreen.LENDING },
                                onNavigateToBorrowing = { currentSubScreen = SubScreen.BORROWING },
                                onNavigateToPersonalExpenses = { currentSubScreen = SubScreen.PERSONAL_EXPENSES },
                                onNavigateToInvestments = { currentSubScreen = SubScreen.INVESTMENTS },
                                onNavigateToTransfers = { currentSubScreen = SubScreen.TRANSFERS },
                                onNavigateToBudget = { currentSubScreen = SubScreen.BUDGET_PLANNER },
                                onOpenAiChat = {
                                    val currentEmail = authViewModel?.user?.value?.email ?: appPreferences.lastUserEmail
                                    val currentUser = authViewModel?.user?.value
                                    val isGoogleAccount = currentUser != null && currentUser.googleSub.isNotBlank() &&
                                        !currentUser.googleSub.startsWith("local_") &&
                                        !currentUser.googleSub.startsWith("email_") &&
                                        !currentUser.googleSub.startsWith("otp_") &&
                                        !currentUser.googleSub.startsWith("user_")
                                    val isGoogleDriveConnected = authViewModel?.driveStatus?.value?.isConnected == true
                                    val geminiKey = appPreferences.getGeminiApiKeyForUser(currentEmail).ifBlank { appPreferences.geminiApiKey }
                                    val isGoogleOptedIn = isGoogleAccount || isGoogleDriveConnected || geminiKey.isNotBlank()

                                    if (isGoogleOptedIn) {
                                        if (geminiKey.isNotBlank()) {
                                            showAiChatSheet = true
                                        } else {
                                            currentTab = MainTab.SETTINGS
                                            autoOpenGeminiConfig = true
                                        }
                                    } else {
                                        showGoogleAiOptInDialog = true
                                    }
                                }
                            )

                            MainTab.TRANSACTIONS -> TransactionsScreen(
                                viewModel = viewModel,
                                onTransactionClick = { tx -> selectedTransactionForDetail = tx },
                                onNavigateToImport = { currentSubScreen = SubScreen.IMPORT_STATEMENT },
                                onOpenAddSheet = {
                                    addSheetTab = AddTab.EXPENSE
                                    preselectedLoanForRepay = null
                                    showAddSheet = true
                                }
                            )

                            MainTab.SETTINGS -> SettingsScreen(
                                viewModel = viewModel,
                                authViewModel = authViewModel,
                                initialTab = if (autoOpenGeminiConfig) com.example.features.settings.SettingsTab.FINANCE else if (SpotlightTourManager.activeStep == SpotlightStep.SETTINGS_SECURITY) com.example.features.settings.SettingsTab.SECURITY else com.example.features.settings.SettingsTab.FINANCE,
                                openGeminiConfig = autoOpenGeminiConfig,
                                onGeminiConfigDismissed = { autoOpenGeminiConfig = false },
                                onNavigateToRules = { currentSubScreen = SubScreen.RULES },
                                onNavigateToCompanyExpenses = { currentSubScreen = SubScreen.COMPANY_EXPENSES },
                                onNavigateToReports = { currentTab = MainTab.REPORTS },
                                onNavigateToImport = { currentSubScreen = SubScreen.IMPORT_STATEMENT },
                                onNavigateToLending = { currentSubScreen = SubScreen.LENDING },
                                onNavigateToExcelSync = { currentSubScreen = SubScreen.EXCEL_SYNC },
                                onNavigateToBudget = { currentSubScreen = SubScreen.BUDGET_PLANNER },
                                onOpenProductTour = {
                                    currentSubScreen = SubScreen.IMPORT_STATEMENT
                                    SpotlightTourManager.startTour()
                                },
                                onOpenWalkthrough = { showUserWalkthroughDialog = true },
                                onOpenIntroNote = { showIntroNoteDialog = true },
                                onAccountSwitched = {
                                    currentTab = MainTab.DASHBOARD
                                    currentSubScreen = SubScreen.NONE
                                }
                            )

                            MainTab.REPORTS -> ReportsScreen(viewModel = viewModel)
                        }
                    }
                }
            }

            // Sheets & Dialogs
            if (showGoogleAiOptInDialog) {
                AlertDialog(
                    onDismissRequest = { showGoogleAiOptInDialog = false },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    title = {
                        Text("Google AI Opt-In Required", fontWeight = FontWeight.Bold)
                    },
                    text = {
                        Text(
                            "AI Financial Assistant features are powered by Google Gemini and require opting into Google services (such as signing in with Google or providing your Google Gemini API key). Offline and unlinked accounts cannot use AI features without opting in.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showGoogleAiOptInDialog = false
                                currentTab = MainTab.SETTINGS
                                autoOpenGeminiConfig = true
                            }
                        ) {
                            Text("Set Gemini API Key")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showGoogleAiOptInDialog = false }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showAiChatSheet) {
                FinancialChatSheet(
                    viewModel = viewModel,
                    onDismiss = { showAiChatSheet = false }
                )
            }

            if (showAddSheet) {
                AddTransactionSheet(
                    viewModel = viewModel,
                    initialTab = addSheetTab,
                    preselectedLoan = preselectedLoanForRepay,
                    onDismiss = {
                        showAddSheet = false
                        preselectedLoanForRepay = null
                    }
                )
            }

            val allTransactionsList by viewModel.allTransactions.collectAsState()
            val activeDetailTx = remember(allTransactionsList, selectedTransactionForDetail) {
                allTransactionsList.find { it.id == selectedTransactionForDetail?.id } ?: selectedTransactionForDetail
            }

            if (activeDetailTx != null) {
                TransactionDetailSheet(
                    transaction = activeDetailTx,
                    viewModel = viewModel,
                    onDismiss = { selectedTransactionForDetail = null }
                )
            }

            smartRulePrompt?.let { prompt ->
                SmartRulePromptDialog(
                    prompt = prompt,
                    onAccept = { editedKeyword ->
                        viewModel.acceptSmartRule(prompt.copy(keyword = editedKeyword))
                    },
                    onDismiss = { viewModel.dismissSmartRule() }
                )
            }

            // Introduction & Welcome Note Dialog (shown on first launch, dismissable)
            if (showIntroNoteDialog) {
                IntroductionNoteDialog(
                    onGetStarted = {
                        appPreferences.setIntroNoteDismissedForUser(userProfile?.email, true)
                        showIntroNoteDialog = false
                    },
                    onOpenWalkthrough = {
                        appPreferences.setIntroNoteDismissedForUser(userProfile?.email, true)
                        showIntroNoteDialog = false
                        showUserWalkthroughDialog = true
                    },
                    onSkip = {
                        appPreferences.setIntroNoteDismissedForUser(userProfile?.email, true)
                        showIntroNoteDialog = false
                    }
                )
            }

            // In-App User Walkthrough Dialog (12 modules + Settings guide, reopenable)
            if (showUserWalkthroughDialog) {
                UserWalkthroughDialog(
                    onDismissRequest = { showUserWalkthroughDialog = false },
                    onCompleted = { dontShowAgain ->
                        if (dontShowAgain) {
                            appPreferences.setWalkthroughCompletedForUser(userProfile?.email, true)
                        }
                        showUserWalkthroughDialog = false
                    }
                )
            }
        }
    }

        // Interactive In-App Spotlight Product Tour (OnRent style - rendered at true window root level)
        SpotlightTourManager.activeStep?.let { tourStep ->
            val targetBounds = SpotlightTourManager.targetBoundsMap[tourStep]
            SpotlightTourOverlay(
                step = tourStep,
                targetBounds = targetBounds,
                onNext = {
                    when (tourStep) {
                        SpotlightStep.IMPORT_STATEMENT -> {
                            currentSubScreen = SubScreen.NONE
                            currentTab = MainTab.TRANSACTIONS
                            SpotlightTourManager.nextStep()
                        }
                        SpotlightStep.STATEMENTS_LEDGER -> {
                            currentSubScreen = SubScreen.NONE
                            currentTab = MainTab.TRANSACTIONS
                            SpotlightTourManager.nextStep()
                        }
                        SpotlightStep.STATEMENTS_FILTER -> {
                            currentSubScreen = SubScreen.IMPORT_STATEMENT
                            SpotlightTourManager.nextStep()
                        }
                        SpotlightStep.IMPORT_HISTORY -> {
                            currentSubScreen = SubScreen.COMPANY_EXPENSES
                            SpotlightTourManager.nextStep()
                        }
                        SpotlightStep.OFFICIAL_EXPENSES -> {
                            currentSubScreen = SubScreen.NONE
                            currentTab = MainTab.DASHBOARD
                            SpotlightTourManager.nextStep()
                        }
                        SpotlightStep.DASHBOARD_CASHFLOW -> {
                            currentSubScreen = SubScreen.NONE
                            currentTab = MainTab.REPORTS
                            SpotlightTourManager.nextStep()
                        }
                        SpotlightStep.REPORTS_PDF -> {
                            currentSubScreen = SubScreen.NONE
                            currentTab = MainTab.SETTINGS
                            SpotlightTourManager.nextStep()
                        }
                        SpotlightStep.SETTINGS_SECURITY -> {
                            currentSubScreen = SubScreen.NONE
                            currentTab = MainTab.DASHBOARD
                            appPreferences.setProductTourCompletedForUser(userProfile?.email, true)
                            SpotlightTourManager.dismissTour()
                        }
                    }
                },
                onBack = {
                    when (tourStep) {
                        SpotlightStep.SETTINGS_SECURITY -> {
                            currentSubScreen = SubScreen.NONE
                            currentTab = MainTab.REPORTS
                            SpotlightTourManager.previousStep()
                        }
                        SpotlightStep.REPORTS_PDF -> {
                            currentSubScreen = SubScreen.NONE
                            currentTab = MainTab.DASHBOARD
                            SpotlightTourManager.previousStep()
                        }
                        SpotlightStep.DASHBOARD_CASHFLOW -> {
                            currentSubScreen = SubScreen.COMPANY_EXPENSES
                            SpotlightTourManager.previousStep()
                        }
                        SpotlightStep.OFFICIAL_EXPENSES -> {
                            currentSubScreen = SubScreen.IMPORT_STATEMENT
                            SpotlightTourManager.previousStep()
                        }
                        SpotlightStep.IMPORT_HISTORY -> {
                            currentSubScreen = SubScreen.NONE
                            currentTab = MainTab.TRANSACTIONS
                            SpotlightTourManager.previousStep()
                        }
                        SpotlightStep.STATEMENTS_FILTER -> {
                            currentSubScreen = SubScreen.NONE
                            currentTab = MainTab.TRANSACTIONS
                            SpotlightTourManager.previousStep()
                        }
                        SpotlightStep.STATEMENTS_LEDGER -> {
                            currentSubScreen = SubScreen.IMPORT_STATEMENT
                            SpotlightTourManager.previousStep()
                        }
                        SpotlightStep.IMPORT_STATEMENT -> {
                            SpotlightTourManager.previousStep()
                        }
                    }
                },
                onDismiss = {
                    currentSubScreen = SubScreen.NONE
                    currentTab = MainTab.DASHBOARD
                    appPreferences.setProductTourCompletedForUser(userProfile?.email, true)
                    SpotlightTourManager.dismissTour()
                }
            )
        }
    }
}
}
