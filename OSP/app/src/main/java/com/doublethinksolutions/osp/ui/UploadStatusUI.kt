package com.doublethinksolutions.osp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun UploadStatusIndicator(
    modifier: Modifier = Modifier,
    uploadQueue: List<UploadItem>,
    uploadHistory: List<UploadItem>
) {
    var expanded by remember { mutableStateOf(false) }
    val activeUploads = uploadQueue.filter { it.status == UploadStatus.UPLOADING || it.status == UploadStatus.QUEUED }
    val currentUploading = uploadQueue.firstOrNull { it.status == UploadStatus.UPLOADING }

    // The indicator is visible if there's anything in the queue or history.
    AnimatedVisibility(
        visible = uploadQueue.isNotEmpty() || uploadHistory.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .animateContentSize(animationSpec = spring())
                    .clickable { expanded = !expanded }
                    .padding(12.dp)
            ) {
                // Collapsed / Header View
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (currentUploading != null) {
                            val animatedProgress by animateFloatAsState(targetValue = currentUploading.progress, label = "progress", animationSpec = spring())
                            val color = lerp(MaterialTheme.colorScheme.primary, Color(0xFF66BB6A), animatedProgress)
                            CircularProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.size(32.dp),
                                color = color,
                                strokeWidth = 3.dp
                            )
                        } else {
                            // Show status of the most recent event if not actively uploading
                            val mostRecentItem = uploadQueue.firstOrNull() ?: uploadHistory.firstOrNull()
                            val icon = when (mostRecentItem?.status) {
                                UploadStatus.SUCCESS -> Icons.Default.CheckCircle
                                UploadStatus.FAILED -> Icons.Default.Error
                                else -> Icons.Default.CloudUpload
                            }
                            val tint = when (mostRecentItem?.status) {
                                UploadStatus.SUCCESS -> Color(0xFF66BB6A)
                                UploadStatus.FAILED -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = "Upload Status",
                                modifier = Modifier.size(32.dp),
                                tint = tint
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        val titleText = if (activeUploads.isNotEmpty()) "Uploading..." else "Uploads"
                        val subtitleText = if (activeUploads.isNotEmpty()) {
                            "${activeUploads.size} in queue"
                        } else if (uploadHistory.isNotEmpty()) {
                            "View history"
                        } else {
                            // For lingering items
                            "Finished"
                        }

                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Expanded View
                AnimatedVisibility(visible = expanded) {
                    Column {
                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        if (uploadQueue.isNotEmpty()) {
                            Text("Current Activity", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                uploadQueue.forEach { item ->
                                    if (item.status == UploadStatus.SUCCESS || item.status == UploadStatus.FAILED) {
                                        CompletedItemDetails(item = item)
                                    } else {
                                        UploadItemRow(item = item)
                                    }
                                }
                            }
                        }

                        if (uploadHistory.isNotEmpty()) {
                            if (uploadQueue.isNotEmpty()) {
                                Divider(modifier = Modifier.padding(vertical = 12.dp))
                            }
                            Text("Recent History", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                uploadHistory.forEach { item ->
                                    CompletedItemDetails(item = item)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadItemRow(item: UploadItem) {
    // This composable remains unchanged, it's perfect for active items.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val (icon, color) = when (item.status) {
            UploadStatus.QUEUED -> Icons.Default.HourglassTop to MaterialTheme.colorScheme.onSurfaceVariant
            UploadStatus.UPLOADING -> Icons.Default.CloudUpload to MaterialTheme.colorScheme.primary
            UploadStatus.SUCCESS -> Icons.Default.CheckCircle to Color(0xFF66BB6A)
            UploadStatus.FAILED -> Icons.Default.Error to MaterialTheme.colorScheme.error
            UploadStatus.SIGNING -> Icons.Default.Lock to MaterialTheme.colorScheme.primary
        }
        Icon(imageVector = icon, contentDescription = item.status.name, tint = color, modifier = Modifier.size(20.dp))
        Text(
            text = item.fileName,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (item.status == UploadStatus.UPLOADING) {
            val animatedProgress by animateFloatAsState(targetValue = item.progress, label = "row_progress", animationSpec = spring())
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.width(50.dp).clip(CircleShape),
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun CompletedItemDetails(item: UploadItem) {
    // This composable also remains unchanged, it's perfect for showing results.
    val result = item.result
    val trustScoreText = if (result != null && result.trustScore >= 0) {
        "Trust Score: ${"%.2f".format(result.trustScore)}"
    } else {
        "Trust Score: N/A"
    }

    val uploadTimeText = if (result != null && result.uploadTimeMs >= 0) {
        "Time: ${"%.2f".format(result.uploadTimeMs / 1000.0)}s"
    } else {
        "Time: N/A"
    }

    val fileSizeText = if (result != null && result.fileSizeBytes >= 0) {
        "Size: ${"%.2f".format(result.fileSizeBytes / 1024.0)} KB"
    } else {
        "Size: N/A"
    }

    val (backgroundColor, icon, iconColor) = when(item.status) {
        UploadStatus.SUCCESS -> Triple(
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
            Icons.Default.CheckCircle,
            Color(0xFF66BB6A)
        )
        else -> Triple(
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
            Icons.Default.Error,
            MaterialTheme.colorScheme.error
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(imageVector = icon, contentDescription = "Status", tint = iconColor, modifier = Modifier.size(18.dp))
            Text(item.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        if (item.status == UploadStatus.SUCCESS && item.errorMessage == null) {
            Text(trustScoreText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 26.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(start = 26.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(uploadTimeText, style = MaterialTheme.typography.bodySmall)
                Text(fileSizeText, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (item.errorMessage != null) {
            Text(
                text = item.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 26.dp)
            )
        }
    }
}
