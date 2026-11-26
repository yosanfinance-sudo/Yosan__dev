package com.example.features.messages

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CategoryType
import com.example.data.local.entity.PendingTransactionEntity
import com.example.data.local.entity.TransactionType
import com.example.features.transactions.FinanceViewModel
import com.example.ui.theme.*
import com.example.utils.CurrencyFormatter
import com.example.utils.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingTransactionsReviewSheet(
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pendingList by viewModel.pendingTransactions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState()

    var showQuickCategoryDialog by remember { mutableStateOf(false) }
    var quickCategoryParentId by remember { mutableStateOf<Long?>(null) }
    var quickCategoryName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Detected Transactions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Two-Step Verification: Select Bank & Category before adding",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (pendingList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = IncomeGreen,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "All Caught Up!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "No pending statements awaiting your review.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${pendingList.size} Pending Review",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(
                        onClick = {
                            viewModel.approveAllPendingTransactions(pendingList)
                            onDismiss()
                        }
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Approve All", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                ) {
                    items(pendingList, key = { it.id }) { pending ->
                        PendingItemCard(
                            pending = pending,
                            categories = categories,
                            accounts = accounts,
                            allTransactions = allTransactions,
                            onAddCategoryClick = { parentId ->
                                quickCategoryParentId = parentId
                                quickCategoryName = ""
                                showQuickCategoryDialog = true
                            },
                            onApprove = { accId, catId, catName, subcatId, subcatName ->
                                viewModel.approvePendingTransaction(
                                    pending = pending,
                                    accountId = accId,
                                    categoryId = catId,
                                    categoryName = catName,
                                    subcategoryId = subcatId,
                                    subcategoryName = subcatName
                                )
                            },
                            onDismiss = {
                                viewModel.dismissPendingTransaction(pending.id)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showQuickCategoryDialog) {
        val parent = categories.firstOrNull { it.id == quickCategoryParentId }
        AlertDialog(
            onDismissRequest = { showQuickCategoryDialog = false },
            title = {
                Text(
                    text = if (parent != null) "Add Subcategory under ${parent.name}" else "Add New Category",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = quickCategoryName,
                        onValueChange = { quickCategoryName = it },
                        label = { Text("Name") },
                        placeholder = { Text(if (parent != null) "e.g. Dining Out" else "e.g. Groceries") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = quickCategoryName.trim()
                        if (name.isNotBlank()) {
                            viewModel.addCategory(
                                name = name,
                                type = parent?.type ?: CategoryType.EXPENSE,
                                colorHex = parent?.colorHex ?: "#3B82F6",
                                parentCategoryId = quickCategoryParentId
                            )
                            showQuickCategoryDialog = false
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickCategoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PendingItemCard(
    pending: PendingTransactionEntity,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    allTransactions: List<com.example.data.local.entity.TransactionEntity>,
    onAddCategoryClick: (Long?) -> Unit,
    onApprove: (Long, Long?, String?, Long?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var selectedCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var selectedSubcategory by remember { mutableStateOf<CategoryEntity?>(null) }

    var showRawMessage by remember { mutableStateOf(false) }

    val matchingStatementTx = remember(pending, allTransactions) {
        allTransactions.firstOrNull { existing ->
            val refMatch = !pending.referenceNumber.isNullOrBlank() && existing.referenceNumber.isNotBlank() &&
                    existing.referenceNumber.equals(pending.referenceNumber, ignoreCase = true)
            val dateAmountMatch = existing.transactionDate == pending.transactionDate &&
                    Math.abs(existing.amount - pending.amount) < 0.01 &&
                    existing.transactionType == pending.transactionType
            refMatch || dateAmountMatch
        }
    }

    val isExpense = pending.transactionType == TransactionType.EXPENSE
    val amountColor = if (isExpense) ExpenseRed else IncomeGreen
    val amountPrefix = if (isExpense) "-" else "+"

    val isSmsSource = pending.source.startsWith("SMS", ignoreCase = true)
    val sourceIcon = if (isSmsSource) Icons.Default.Sms else Icons.Default.NotificationsActive
    val sourceBadgeColor = if (isSmsSource) IncomeGreen else LendingIndigo

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Source Badge & Timestamp Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = sourceBadgeColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = sourceIcon,
                            contentDescription = null,
                            tint = sourceBadgeColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = pending.source,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = sourceBadgeColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = DateUtils.formatForDisplay(pending.transactionDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            if (matchingStatementTx != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Already recorded in statement (#${matchingStatementTx.id} on ${matchingStatementTx.transactionDate})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Amount & Description Hero
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pending.description,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    val metadataParts = listOfNotNull(
                        pending.accountOrCardMask?.let { "A/c **$it" },
                        pending.referenceNumber?.let { "Ref: $it" }
                    )
                    if (metadataParts.isNotEmpty()) {
                        Text(
                            text = metadataParts.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "$amountPrefix${CurrencyFormatter.formatInr(pending.amount)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
            }

            // Raw SMS / Notification text accordion
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { showRawMessage = !showRawMessage }
                    .padding(vertical = 2.dp)
            ) {
                Text(
                    text = if (showRawMessage) "Hide message text" else "View original message text",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (showRawMessage) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }

            AnimatedVisibility(visible = showRawMessage) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text(
                        text = pending.rawMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(10.dp))

            // 1. Bank Account Selection Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Bank Account *",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedAccount == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (selectedAccount == null) {
                    Text(
                        text = "Required",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                accounts.forEach { acc ->
                    val isAccSelected = selectedAccount?.id == acc.id
                    FilterChip(
                        selected = isAccSelected,
                        onClick = { selectedAccount = if (isAccSelected) null else acc },
                        label = { Text(acc.bankName.ifBlank { acc.accountName }, fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isAccSelected) Icons.Default.Check else Icons.Default.AccountBalance,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Category Selection Chips
            Text(
                text = "Assign Category & Subcategory:",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            val rootCats = remember(categories) { categories.filter { it.parentCategoryId == null } }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = {
                        selectedCategory = null
                        selectedSubcategory = null
                    },
                    label = { Text("None", fontSize = 12.sp) }
                )
                rootCats.forEach { cat ->
                    val isSelected = selectedCategory?.id == cat.id
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedCategory = if (isSelected) null else cat
                            selectedSubcategory = null
                        },
                        label = { Text(cat.name, fontSize = 12.sp) }
                    )
                }
                AssistChip(
                    onClick = { onAddCategoryClick(null) },
                    label = { Text("+ Category", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp)) },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp)
                )
            }

            // Subcategories Row (if chosen category has subcategories)
            if (selectedCategory != null) {
                val availableSubcats = remember(selectedCategory, categories) {
                    categories.filter { it.parentCategoryId == selectedCategory?.id }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sub:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp
                    )
                    FilterChip(
                        selected = selectedSubcategory == null,
                        onClick = { selectedSubcategory = null },
                        label = { Text("None", fontSize = 11.sp) }
                    )
                    availableSubcats.forEach { subcat ->
                        val isSubSelected = selectedSubcategory?.id == subcat.id
                        FilterChip(
                            selected = isSubSelected,
                            onClick = { selectedSubcategory = if (isSubSelected) null else subcat },
                            label = { Text(subcat.name, fontSize = 11.sp) }
                        )
                    }
                    AssistChip(
                        onClick = { onAddCategoryClick(selectedCategory?.id) },
                        label = { Text("+ Subcategory", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp)) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Actions: Approve vs Dismiss
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (matchingStatementTx != null) "Dismiss Duplicate" else "Dismiss",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Button(
                    onClick = {
                        val acc = selectedAccount
                        if (acc == null) {
                            Toast.makeText(context, "Please select a Bank Account first", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        onApprove(
                            acc.id,
                            selectedCategory?.id,
                            selectedCategory?.name ?: pending.categoryName,
                            selectedSubcategory?.id,
                            selectedSubcategory?.name
                        )
                    },
                    enabled = selectedAccount != null,
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(12.dp),
                    colors = if (matchingStatementTx != null) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ) else ButtonDefaults.buttonColors()
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (matchingStatementTx != null) "Approve Anyway" else "Approve & Add")
                }
            }
        }
    }
}
