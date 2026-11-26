package me.rerere.rikkahub.ui.components.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.DragDropHorizontal
import me.rerere.rikkahub.data.db.entity.ManagedFileEntity
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog
import org.koin.compose.koinInject
import kotlin.math.abs

@Composable
fun StickerPickerSheet(
    onStickerSelected: (ManagedFileEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val filesManager: FilesManager = koinInject()
    val scope = rememberCoroutineScope()
    val stickerFiles by filesManager.observe(FileFolders.STICKERS).collectAsState(initial = emptyList())
    val stickers = stickerFiles.filter { it.mimeType.startsWith("image/") }
    var showLibrary by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { filesManager.syncFolder(FileFolders.STICKERS) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch {
            uris.forEach { uri ->
                filesManager.saveManagedFromUri(
                    folder = FileFolders.STICKERS,
                    uri = uri,
                    displayName = filesManager.getFileNameFromUri(uri),
                    mimeType = filesManager.getFileMimeType(uri),
                )
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "表情包",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(72.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(min = 88.dp, max = 360.dp),
            ) {
                item(key = "manage") {
                    AddStickerTile(onClick = { showLibrary = true })
                }
                items(stickers, key = ManagedFileEntity::id) { sticker ->
                    StickerImage(
                        sticker = sticker,
                        filesManager = filesManager,
                        modifier = Modifier.clickable { onStickerSelected(sticker) },
                    )
                }
            }
        }
    }

    if (showLibrary) {
        StickerLibraryDialog(
            stickers = stickers,
            filesManager = filesManager,
            onImport = { importer.launch(arrayOf("image/*")) },
            onDismiss = { showLibrary = false },
        )
    }
}

@Composable
private fun StickerLibraryDialog(
    stickers: List<ManagedFileEntity>,
    filesManager: FilesManager,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var organizing by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var ordered by remember { mutableStateOf(stickers) }
    var preview by remember { mutableStateOf<ManagedFileEntity?>(null) }

    LaunchedEffect(stickers) {
        if (!moving) ordered = stickers
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("返回") }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (organizing) "已选择${selectedIds.size}个表情" else "添加的单个表情 (${ordered.size})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            organizing = !organizing
                            moving = false
                            selectedIds = emptySet()
                        },
                    ) { Text(if (organizing) "取消" else "整理") }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    contentPadding = PaddingValues(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    item(key = "import") { AddStickerTile(onClick = onImport) }
                    items(ordered, key = ManagedFileEntity::id) { sticker ->
                        val selected = sticker.id in selectedIds
                        var dragX = 0f
                        var dragY = 0f
                        StickerImage(
                            sticker = sticker,
                            filesManager = filesManager,
                            selected = selected,
                            selectionIndex = selectedIds.indexOf(sticker.id).takeIf { it >= 0 }?.plus(1),
                            modifier = Modifier
                                .clickable {
                                    if (organizing) {
                                        selectedIds = if (selected) selectedIds - sticker.id else selectedIds + sticker.id
                                    } else {
                                        preview = sticker
                                    }
                                }
                                .pointerInput(moving, selected, ordered) {
                                    if (moving && selected) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { dragX = 0f; dragY = 0f },
                                            onDragEnd = {
                                                scope.launch { filesManager.reorderStickers(ordered.map { it.id }) }
                                            },
                                            onDragCancel = {
                                                scope.launch { filesManager.reorderStickers(ordered.map { it.id }) }
                                            },
                                        ) { change, amount ->
                                            change.consume()
                                            dragX += amount.x
                                            dragY += amount.y
                                            val from = ordered.indexOfFirst { it.id == sticker.id }
                                            val step = when {
                                                abs(dragY) >= 54f -> if (dragY > 0) 5 else -5
                                                abs(dragX) >= 54f -> if (dragX > 0) 1 else -1
                                                else -> 0
                                            }
                                            if (from >= 0 && step != 0) {
                                                val to = (from + step).coerceIn(0, ordered.lastIndex)
                                                if (to != from) {
                                                    ordered = ordered.toMutableList().apply { add(to, removeAt(from)) }
                                                    dragX = 0f
                                                    dragY = 0f
                                                }
                                            }
                                        }
                                    }
                                },
                        )
                    }
                }
                if (organizing) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(
                            onClick = { moving = !moving },
                            enabled = selectedIds.isNotEmpty(),
                        ) {
                            Icon(HugeIcons.DragDropHorizontal, contentDescription = null)
                            Text(if (moving) "完成移动" else "移动", Modifier.padding(start = 6.dp))
                        }
                        TextButton(
                            onClick = {
                                val targets = selectedIds
                                scope.launch {
                                    targets.forEach { filesManager.delete(it) }
                                    selectedIds = emptySet()
                                }
                            },
                            enabled = selectedIds.isNotEmpty(),
                        ) {
                            Icon(HugeIcons.Delete01, contentDescription = null)
                            Text("删除 (${selectedIds.size})", Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }

    preview?.let { sticker ->
        ImagePreviewDialog(images = listOf(filesManager.getFile(sticker).toUri().toString())) {
            preview = null
        }
    }
}

@Composable
private fun AddStickerTile(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.aspectRatio(1f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(HugeIcons.Add01, contentDescription = "导入或整理表情", modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun StickerImage(
    sticker: ManagedFileEntity,
    filesManager: FilesManager,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    selectionIndex: Int? = null,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(modifier),
    ) {
        AsyncImage(
            model = filesManager.getFile(sticker).toUri().toString(),
            contentDescription = sticker.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        if (selected) {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp),
            ) {
                Box(contentAlignment = Alignment.Center) { Text(selectionIndex?.toString().orEmpty()) }
            }
        }
    }
}
