package me.rerere.rikkahub.ui.components.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.rikkahub.data.db.entity.ManagedFileEntity
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.ui.components.richtext.ZoomableAsyncImage
import org.koin.compose.koinInject

@Composable
fun StickerPickerSheet(
    onStickerSelected: (ManagedFileEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val filesManager: FilesManager = koinInject()
    val scope = rememberCoroutineScope()
    val stickerFiles by filesManager.observe(FileFolders.STICKERS).collectAsState(initial = emptyList())
    val stickers = stickerFiles.filter { it.mimeType.startsWith("image/") }
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
            FilledTonalButton(
                onClick = { importer.launch(arrayOf("image/*")) },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Icon(HugeIcons.Add01, contentDescription = null)
                Text("导入表情", modifier = Modifier.padding(start = 8.dp))
            }
            if (stickers.isEmpty()) {
                Text(
                    text = "还没有表情包",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 28.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(88.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 420.dp),
                ) {
                    items(stickers, key = ManagedFileEntity::id) { sticker ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(MaterialTheme.shapes.large)
                                .clickable { onStickerSelected(sticker) },
                        ) {
                            ZoomableAsyncImage(
                                model = filesManager.getFile(sticker).toUri().toString(),
                                contentDescription = sticker.displayName,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                            ) {
                                IconButton(
                                    onClick = { scope.launch { filesManager.delete(sticker.id) } },
                                    modifier = Modifier.size(30.dp),
                                ) {
                                    Icon(HugeIcons.Delete01, contentDescription = "删除表情", modifier = Modifier.size(17.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
