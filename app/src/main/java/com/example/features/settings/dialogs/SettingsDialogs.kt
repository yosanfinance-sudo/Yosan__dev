package com.example.features.settings.dialogs

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.UndoHistoryEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.features.dashboard.DashboardCardConfigManager
import com.example.features.dashboard.DashboardCardType
import com.example.features.transactions.FinanceViewModel
import androidx.compose.ui.draw.scale
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import com.example.data.local.entity.CategoryType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeDashboardDialog(
    userProfile: UserProfileEntity?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var cards by remember(userProfile?.dashboardCardsConfig) {
        mutableStateOf(DashboardCardConfigManager.parse(userProfile?.dashboardCardsConfig))
    }

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragAccumulator by remember { mutableStateOf(0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.DashboardCustomize,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Customize Dashboard",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Drag the handle to reorder cards, or use switches to show/hide sections on your Dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                cards.forEachIndexed { index, item ->
                    val isDragging = draggingIndex == index
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .scale(if (isDragging) 1.02f else 1.0f),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isDragging -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                item.isEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                            }
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isDragging) 1.5.dp else 1.dp,
                            color = if (isDragging) MaterialTheme.colorScheme.primary
                            else if (item.isEnabled) MaterialTheme.colorScheme.outlineVariant
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Drag Handle to reorder directly
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .pointerInput(cards) {
                                        detectDragGestures(
                                            onDragStart = {
                                                draggingIndex = index
                                                dragAccumulator = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragAccumulator += dragAmount.y
                                                val threshold = 65f
                                                val curIdx = draggingIndex ?: return@detectDragGestures
                                                if (dragAccumulator > threshold && curIdx < cards.size - 1) {
                                                    val mutable = cards.toMutableList()
                                                    val target = curIdx + 1
                                                    val temp = mutable[curIdx]
                                                    mutable[curIdx] = mutable[target]
                                                    mutable[target] = temp
                                                    cards = mutable
                                                    draggingIndex = target
                                                    dragAccumulator = 0f
                                                    onSave(DashboardCardConfigManager.serialize(mutable))
                                                } else if (dragAccumulator < -threshold && curIdx > 0) {
                                                    val mutable = cards.toMutableList()
                                                    val target = curIdx - 1
                                                    val temp = mutable[curIdx]
                                                    mutable[curIdx] = mutable[target]
                                                    mutable[target] = temp
                                                    cards = mutable
                                                    draggingIndex = target
                                                    dragAccumulator = 0f
                                                    onSave(DashboardCardConfigManager.serialize(mutable))
                                                }
                                            },
                                            onDragEnd = {
                                                draggingIndex = null
                                                dragAccumulator = 0f
                                            },
                                            onDragCancel = {
                                                draggingIndex = null
                                                dragAccumulator = 0f
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "Drag to reorder",
                                    tint = if (isDragging) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Card Title & Icon
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    color = if (item.isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = CircleShape,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = item.type.icon,
                                            contentDescription = null,
                                            tint = if (item.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.type.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (item.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.type.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Toggle Switch
                            Switch(
                                checked = item.isEnabled,
                                onCheckedChange = { checked ->
                                    val newList = cards.toMutableList()
                                    newList[index] = item.copy(isEnabled = checked)
                                    cards = newList
                                    onSave(DashboardCardConfigManager.serialize(newList))
                                },
                                modifier = Modifier.scale(0.8f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = {
                        cards = DashboardCardConfigManager.DEFAULT_CONFIG
                        onSave(DashboardCardConfigManager.serialize(DashboardCardConfigManager.DEFAULT_CONFIG))
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset to Default Layout")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Done")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UndoHistoryDialog(
    undoHistory: List<UndoHistoryEntity>,
    onRevert: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Undo History?", fontWeight = FontWeight.Bold) },
            text = { Text("This will remove all recorded action history. Transactions and rules will remain unchanged.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAll()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = LendingIndigo,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Undo & Recovery", fontWeight = FontWeight.Bold)
                }
                if (undoHistory.isNotEmpty()) {
                    TextButton(
                        onClick = { showClearConfirm = true },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Clear", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            }
        },
        text = {
            if (undoHistory.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = IncomeGreen,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Recent Actions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Edits, deletions, and rule applications will appear here for safe recovery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(undoHistory.size, key = { undoHistory[it].actionId }) { index ->
                        val action = undoHistory[index]
                        val icon = when (action.actionType) {
                            "CHANGE_CATEGORY", "BULK_CATEGORIZE" -> Icons.Default.Category
                            "DELETE_TRANSACTION", "BULK_DELETE" -> Icons.Default.DeleteOutline
                            "ADD_RULE", "EDIT_RULE", "DELETE_RULE", "APPLY_RULE" -> Icons.Default.AutoAwesome
                            "CHANGE_TYPE" -> Icons.Default.SyncAlt
                            else -> Icons.Default.Edit
                        }
                        val iconTint = when (action.actionType) {
                            "DELETE_TRANSACTION", "BULK_DELETE" -> ExpenseRed
                            "ADD_RULE", "EDIT_RULE", "DELETE_RULE", "APPLY_RULE" -> LendingIndigo
                            "CHANGE_CATEGORY", "BULK_CATEGORIZE" -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.secondary
                        }
                        val timeStr = remember(action.timestamp) {
                            val diff = System.currentTimeMillis() - action.timestamp
                            when {
                                diff < 60_000L -> "Just now"
                                diff < 3600_000L -> "${diff / 60_000L}m ago"
                                diff < 86400_000L -> "${diff / 3600_000L}h ago"
                                else -> SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(action.timestamp))
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = iconTint.copy(alpha = 0.12f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = iconTint,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = action.description,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = timeStr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                OutlinedButton(
                                    onClick = { onRevert(action.actionId) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Revert", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Close")
            }
        }
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementDialog(
    categories: List<CategoryEntity>,
    allTransactions: List<com.example.data.local.entity.TransactionEntity>,
    onAddCategory: (String, CategoryType, String, Long?) -> Unit,
    onUpdateCategory: (CategoryEntity) -> Unit,
    onDeleteCategory: (Long) -> Unit,
    onReassignAndDelete: (Long, Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf<CategoryType?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var categoryToDeleteWithReassign by remember { mutableStateOf<CategoryEntity?>(null) }

    val filteredCategories = remember(categories, selectedFilter) {
        if (selectedFilter == null) categories else categories.filter { it.type == selectedFilter }
    }

    var initialParentIdForNew by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Categories & Subcategories",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Category Type Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val rootCount = categories.count { it.parentCategoryId == null }
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { selectedFilter = null },
                        label = { Text("All ($rootCount)") }
                    )
                    CategoryType.values().forEach { cType ->
                        val count = categories.count { it.type == cType && it.parentCategoryId == null }
                        FilterChip(
                            selected = selectedFilter == cType,
                            onClick = { selectedFilter = cType },
                            label = { Text("${cType.name.lowercase().replaceFirstChar { it.uppercase() }} ($count)") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val rootCategories = remember(filteredCategories) {
                    filteredCategories.filter { it.parentCategoryId == null }
                }

                if (rootCategories.isEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No Categories Found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Add categories and subcategories to organize your financial statements.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(rootCategories.size) { idx ->
                            val cat = rootCategories[idx]
                            val subcategories = remember(cat.id, categories) {
                                categories.filter { it.parentCategoryId == cat.id }
                            }
                            val txCount = remember(cat, allTransactions, subcategories) {
                                val subIds = subcategories.map { it.id }.toSet()
                                allTransactions.count {
                                    it.categoryId == cat.id || it.categoryName.equals(cat.name, ignoreCase = true) ||
                                            (it.subcategoryId != null && subIds.contains(it.subcategoryId))
                                }
                            }
                            val colorParsed = remember(cat.colorHex) {
                                try {
                                    Color(android.graphics.Color.parseColor(cat.colorHex))
                                } catch (_: Exception) {
                                    Color(0xFF10B981)
                                }
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    // Parent Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                color = colorParsed.copy(alpha = 0.16f),
                                                shape = CircleShape,
                                                modifier = Modifier.size(44.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.Category,
                                                        contentDescription = cat.name,
                                                        tint = colorParsed,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = cat.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val typeStr = cat.type.name.lowercase().replaceFirstChar { it.uppercase() }
                                                Text(
                                                    text = "$typeStr • $txCount txns",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { editingCategory = cat },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Edit Category",
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    if (txCount > 0) {
                                                        categoryToDeleteWithReassign = cat
                                                    } else {
                                                        onDeleteCategory(cat.id)
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete Category",
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }

                                    // Subcategories Section
                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    androidx.compose.foundation.layout.FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        subcategories.forEach { sub ->
                                            Surface(
                                                color = MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(7.dp)
                                                            .clip(CircleShape)
                                                            .background(colorParsed.copy(alpha = 0.9f))
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = sub.name,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    IconButton(
                                                        onClick = { editingCategory = sub },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Edit,
                                                            contentDescription = "Edit",
                                                            modifier = Modifier.size(12.dp),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = { onDeleteCategory(sub.id) },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Close,
                                                            contentDescription = "Delete",
                                                            modifier = Modifier.size(12.dp),
                                                            tint = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        AssistChip(
                                            onClick = {
                                                initialParentIdForNew = cat.id
                                                showAddCategoryDialog = true
                                            },
                                            label = { Text("+ Subcategory", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                                            },
                                            modifier = Modifier.height(30.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        initialParentIdForNew = null
                        editingCategory = null
                        showAddCategoryDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Category")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )

    // Add / Edit Category Dialog
    if (showAddCategoryDialog || editingCategory != null) {
        val isEditing = editingCategory != null
        var catName by remember { mutableStateOf(editingCategory?.name ?: "") }
        var catType by remember { mutableStateOf(editingCategory?.type ?: CategoryType.EXPENSE) }
        var catColor by remember { mutableStateOf(editingCategory?.colorHex ?: "#3B82F6") }
        var isSubcategory by remember { mutableStateOf(editingCategory?.parentCategoryId != null || initialParentIdForNew != null) }
        var selectedParentId by remember { mutableStateOf(editingCategory?.parentCategoryId ?: initialParentIdForNew) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        val potentialParents = remember(categories, catType, editingCategory) {
            categories.filter { it.parentCategoryId == null && it.type == catType && (!isEditing || it.id != editingCategory?.id) }
        }

        val palette = listOf(
            "#EF4444", "#10B981", "#3B82F6", "#8B5CF6",
            "#F59E0B", "#EC4899", "#06B6D4", "#64748B", "#14B8A6"
        )

        AlertDialog(
            onDismissRequest = {
                showAddCategoryDialog = false
                editingCategory = null
            },
            title = {
                Text(if (isEditing) "Edit Category" else "Add New Category", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = catName,
                        onValueChange = {
                            catName = it
                            errorMessage = null
                        },
                        label = { Text("Category Name") },
                        placeholder = { Text("e.g. Subscriptions") },
                        singleLine = true,
                        isError = errorMessage != null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    errorMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }

                    Text("Classification Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(CategoryType.EXPENSE, CategoryType.INCOME, CategoryType.INVESTMENT, CategoryType.OTHER).forEach { t ->
                            FilterChip(
                                selected = catType == t,
                                onClick = { catType = t },
                                label = { Text(t.name) }
                            )
                        }
                    }

                    Text("Category Color", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        palette.forEach { hex ->
                            val c = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Gray }
                            val isSel = catColor.equals(hex, ignoreCase = true)
                            Surface(
                                color = c,
                                shape = CircleShape,
                                border = if (isSel) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable { catColor = hex }
                            ) {}
                        }
                    }

                    if (potentialParents.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { isSubcategory = !isSubcategory }
                        ) {
                            Checkbox(
                                checked = isSubcategory,
                                onCheckedChange = { isSubcategory = it }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Nest as subcategory under another category", style = MaterialTheme.typography.bodySmall)
                        }

                        if (isSubcategory) {
                            Text("Select Parent Category", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                potentialParents.forEach { p ->
                                    val isPSelected = selectedParentId == p.id
                                    FilterChip(
                                        selected = isPSelected,
                                        onClick = { selectedParentId = if (isPSelected) null else p.id },
                                        label = { Text(p.name) }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = catName.trim()
                        if (trimmed.isBlank()) {
                            errorMessage = "Category name cannot be empty"
                            return@Button
                        }
                        val duplicate = categories.any {
                            it.name.trim().equals(trimmed, ignoreCase = true) && (!isEditing || it.id != editingCategory?.id)
                        }
                        if (duplicate) {
                            errorMessage = "Category '$trimmed' already exists"
                            return@Button
                        }
                        val finalParentId = if (isSubcategory) selectedParentId else null
                        if (isEditing) {
                            editingCategory?.let { current ->
                                onUpdateCategory(
                                    current.copy(
                                        name = trimmed,
                                        type = catType,
                                        colorHex = catColor,
                                        parentCategoryId = finalParentId,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        } else {
                            onAddCategory(trimmed, catType, catColor, finalParentId)
                        }
                        showAddCategoryDialog = false
                        editingCategory = null
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isEditing) "Update" else "Create")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddCategoryDialog = false
                    editingCategory = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Category Deletion with Reassignment Dialog
    categoryToDeleteWithReassign?.let { targetCat ->
        val candidateFallbacks = remember(categories, targetCat) {
            categories.filter { it.id != targetCat.id }
        }
        var selectedFallbackId by remember(targetCat) {
            mutableStateOf(candidateFallbacks.firstOrNull()?.id ?: 1L)
        }
        val count = allTransactions.count { it.categoryId == targetCat.id || it.categoryName.equals(targetCat.name, ignoreCase = true) }

        AlertDialog(
            onDismissRequest = { categoryToDeleteWithReassign = null },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = {
                Text("Reassign Category Records", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "Category '${targetCat.name}' has $count linked transactions.\n\nTo prevent orphan transactions or calculation distortion, choose the category to reassign these records to:"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Target Category:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        candidateFallbacks.forEach { fallback ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedFallbackId = fallback.id }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = selectedFallbackId == fallback.id,
                                    onClick = { selectedFallbackId = fallback.id }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(fallback.name, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val fallbackName = candidateFallbacks.firstOrNull { it.id == selectedFallbackId }?.name ?: "General"
                        onReassignAndDelete(targetCat.id, selectedFallbackId, fallbackName)
                        categoryToDeleteWithReassign = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reassign & Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDeleteWithReassign = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DeveloperDetailsDialog(
    onOpenWalkthrough: () -> Unit,
    onOpenIntroNote: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Details About the Developer", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Application", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("Yosan Finance Manager", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Version v${BuildConfig.VERSION_NAME} • Release", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Engineering Team", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("Rooparaj & Gokulraj", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }


            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("Close")
            }
        }
    )
}




