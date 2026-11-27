package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.AyeshaRepository
import com.example.data.db.CallHistoryEntity
import com.example.ui.components.AyeshaBackground
import com.example.ui.components.AyeshaCard
import com.example.ui.components.AyeshaIcons
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatDurationBn
import com.example.ui.theme.NavySurfaceHigh
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.TealSuccess
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    historyItems: List<CallHistoryEntity>,
    onDeleteOne: (Long) -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmClearAll by remember { mutableStateOf(false) }

    val grouped = remember(historyItems) {
        historyItems.groupBy { formatDateGroupLabel(it.timestamp) }
    }

    AyeshaBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 600.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "কল ইতিহাস",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    if (historyItems.isNotEmpty()) {
                        TextButton(
                            onClick = { confirmClearAll = true },
                            modifier = Modifier.testTag("history_clear_all")
                        ) {
                            Icon(AyeshaIcons.DeleteSweep, contentDescription = null, tint = RoseDanger)
                            Spacer(Modifier.width(4.dp))
                            Text("সব মুছুন", color = RoseDanger)
                        }
                    }
                }
                Text(
                    text = "কোনো অডিও বা ভিডিও রেকর্ডিং সংরক্ষণ করা হয় না।",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                if (historyItems.isEmpty()) {
                    AyeshaCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = AyeshaIcons.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "এখনো কোনো কলের তথ্য নেই",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "হোম ট্যাব থেকে অডিও বা ভিডিও কল করলে এখানে তারিখ অনুযায়ী তালিকা দেখা যাবে।",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        grouped.forEach { (dateLabel, itemsForDate) ->
                            item(key = "header_$dateLabel") {
                                Text(
                                    text = dateLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                                )
                            }
                            items(itemsForDate, key = { it.id }) { entry ->
                                HistoryItemCard(
                                    entry = entry,
                                    onDelete = { onDeleteOne(entry.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            containerColor = NavySurfaceHigh,
            title = { Text("সব ইতিহাস মুছবেন?") },
            text = { Text("সংরক্ষিত সব কলের তালিকা স্থায়ীভাবে মুছে ফেলা হবে।") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmClearAll = false
                        onDeleteAll()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseDanger)
                ) {
                    Text("মুছে ফেলুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
                    Text("বাতিল")
                }
            }
        )
    }
}

@Composable
private fun HistoryItemCard(
    entry: CallHistoryEntity,
    onDelete: () -> Unit
) {
    val isVideo = entry.callType == "VIDEO"
    val actions = remember(entry.actionsJson) {
        AyeshaRepository.parseActionsJson(entry.actionsJson)
    }
    val timeText = remember(entry.timestamp) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(entry.timestamp))
    }

    AyeshaCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isVideo) AyeshaIcons.Videocam else Icons.Default.Call,
                contentDescription = null,
                tint = if (isVideo) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${if (isVideo) "ভিডিও কল" else "অডিও কল"} • ${entry.characterName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$timeText • স্থায়িত্ব: ${formatDurationBn(entry.durationSeconds)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusBadge(label = entry.modeName, color = MaterialTheme.colorScheme.primary)
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "মুছুন",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (actions.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "ফোন নিয়ন্ত্রণের কাজসমূহ:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                actions.forEach { act ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (act.success) Icons.Default.CheckCircle else AyeshaIcons.Error,
                            contentDescription = null,
                            tint = if (act.success) TealSuccess else RoseDanger,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${act.action}: ${act.detail}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (act.success) MaterialTheme.colorScheme.onSurface else RoseDanger
                        )
                    }
                }
            }
        }
    }
}

private fun formatDateGroupLabel(timestampMs: Long): String {
    val target = Calendar.getInstance().apply { timeInMillis = timestampMs }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    return when {
        target.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            target.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "আজ"
        target.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
            target.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "গতকাল"
        else -> SimpleDateFormat("dd MMM yyyy", Locale("bn", "BD")).format(Date(timestampMs))
    }
}
