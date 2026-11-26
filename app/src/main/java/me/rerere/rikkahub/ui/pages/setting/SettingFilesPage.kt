package me.rerere.rikkahub.ui.pages.setting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Clean
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.File01
import me.rerere.hugeicons.stroke.FileAudio
import me.rerere.hugeicons.stroke.FileVideo
import me.rerere.hugeicons.stroke.FolderOpen
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.db.entity.ManagedFileEntity
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.ui.components.ai.ExtensionEmptyState
import me.rerere.rikkahub.ui.components.ai.StickerLibraryDialog
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.fileSizeToString
import org.koin.compose.koinInject
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingFilesPage(
    filesManager: FilesManager = koinInject(),
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val gridState = rememberLazyStaggeredGridState()
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val sources = remember { FileSource.entries }

    // 预先获取字符串资源
    val deletedToast = stringResource(R.string.setting_files_page_deleted_toast)
    val deleteFailedToast = stringResource(R.string.setting_files_page_delete_failed_toast)
    val cleanedToast = stringResource(R.string.setting_files_page_cleaned_toast)
    val cleanFailedToast = stringResource(R.string.setting_files_page_clean_failed_toast)

    var selectedSource by remember { mutableStateOf(FileSource.AVATARS) }
    var pendingDelete by remember { mutableStateOf<ManagedFileEntity?>(null) }
    var showCleanSheet by remember { mutableStateOf(false) }
    var selectedCleanRange by remember { mutableStateOf(CleanRange.DAYS_7) }
    var showStickerLibrary by remember { mutableStateOf(false) }
    val files by filesManager.observe(selectedSource.folder).collectAsState(initial = emptyList())
    val stickerImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
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

    LaunchedEffect(selectedSource) {
        filesManager.syncFolder(
            folder = selectedSource.folder,
            recursive = selectedSource == FileSource.MACHINE_OUTPUTS,
        )
    }

    if (pendingDelete != null) {
        val target = pendingDelete!!
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.setting_files_page_delete_file_title)) },
            text = { Text(target.displayName) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val ok = filesManager.delete(target.id, deleteFromDisk = true)
                            if (ok) {
                                toaster.show(deletedToast)
                            } else {
                                toaster.show(deleteFailedToast)
                            }
                            pendingDelete = null
                        }
                    }
                ) {
                    Text(stringResource(R.string.setting_files_page_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.setting_files_page_cancel_action))
                }
            }
        )
    }

    if (showCleanSheet) {
        val sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
        ModalBottomSheet(
            onDismissRequest = { showCleanSheet = false },
            sheetState = sheetState,
        ) {
            CleanFilesSheet(
                selectedRange = selectedCleanRange,
                onRangeSelected = { selectedCleanRange = it },
                onClean = {
                    showCleanSheet = false
                    scope.launch {
                        val ok = selectedCleanRange.days?.let { days ->
                            filesManager.deleteOlderThan(
                                folder = selectedSource.folder,
                                cutoffMillis = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong()),
                            )
                        } ?: filesManager.deleteAll(selectedSource.folder)
                        toaster.show(if (ok) cleanedToast else cleanFailedToast)
                    }
                },
            )
        }
    }

    if (showStickerLibrary) {
        StickerLibraryDialog(
            stickers = files.filter { it.mimeType.startsWith("image/") },
            filesManager = filesManager,
            onImport = { stickerImporter.launch(arrayOf("image/*")) },
            onDismiss = { showStickerLibrary = false },
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_files_page_title)) },
                navigationIcon = { BackButton() },
                actions = {
                    if (selectedSource == FileSource.STICKERS) {
                        TextButton(onClick = { showStickerLibrary = true }) {
                            Text("表情库")
                        }
                    }
                    IconButton(
                        onClick = { showCleanSheet = true },
                        enabled = files.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = HugeIcons.Clean,
                            contentDescription = stringResource(R.string.setting_files_page_clean_content_description),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                )
        ) {
            SourceRow(
                sources = sources,
                selectedSource = selectedSource,
                onSourceSelected = { selectedSource = it }
            )

            if (files.isEmpty()) {
                ExtensionEmptyState(
                    message = stringResource(R.string.setting_files_page_no_files),
                    icon = HugeIcons.FolderOpen,
                )
            } else {
                LazyVerticalStaggeredGrid(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 8.dp,
                        end = 16.dp,
                        bottom = innerPadding.calculateBottomPadding() + 16.dp,
                    ),
                    verticalItemSpacing = 8.dp,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    state = gridState,
                    columns = StaggeredGridCells.Fixed(2)
                ) {
                    items(files, key = { it.id }) { file ->
                        FileItem(
                            file = file,
                            fileOnDisk = filesManager.getFile(file),
                            onDelete = { pendingDelete = file }
                        )
                    }
                }
            }
        }
    }
}

private enum class CleanRange(val days: Int?) {
    DAYS_7(7),
    DAYS_14(14),
    DAYS_30(30),
    ALL(null),
}

@Composable
private fun CleanFilesSheet(
    selectedRange: CleanRange,
    onRangeSelected: (CleanRange) -> Unit,
    onClean: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.setting_files_page_clean_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.setting_files_page_clean_range_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        CardGroup(modifier = Modifier.selectableGroup()) {
            CleanRange.entries.forEach { range ->
                val selected = selectedRange == range
                item(
                    onClick = { onRangeSelected(range) },
                    modifier = Modifier.semantics {
                        role = Role.RadioButton
                        this.selected = selected
                    },
                    headlineContent = {
                        Text(
                            range.days?.let {
                                stringResource(R.string.setting_files_page_clean_older_than_days, it)
                            } ?: stringResource(R.string.setting_files_page_clean_all)
                        )
                    },
                    // 点整行选中，单选圈自己不接收点击
                    leadingContent = { RadioButton(selected = selected, onClick = null) },
                )
            }
        }

        Button(
            onClick = onClean,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Icon(
                imageVector = HugeIcons.Clean,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.setting_files_page_clean_action))
        }
    }
}

@Composable
private fun SourceRow(
    sources: List<FileSource>,
    selectedSource: FileSource,
    onSourceSelected: (FileSource) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        sources.forEach { source ->
            val selected = selectedSource == source
            ToggleButton(
                checked = selected,
                onCheckedChange = { if (!selected) onSourceSelected(source) },
                modifier = Modifier.semantics { role = Role.Tab },
                colors = ToggleButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Text(
                    text = stringResource(source.titleRes),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

private enum class FileSource(
    val folder: String,
    @androidx.annotation.StringRes val titleRes: Int,
) {
    AVATARS(FileFolders.AVATARS, R.string.setting_files_page_source_avatars),
    WALLPAPERS(FileFolders.WALLPAPERS, R.string.setting_files_page_source_wallpapers),
    UPLOADS(FileFolders.UPLOAD, R.string.setting_files_page_source_uploads),
    MACHINE_OUTPUTS(FileFolders.MEDIA_CREATION, R.string.setting_files_page_source_machine_outputs),
    STICKERS(FileFolders.STICKERS, R.string.setting_files_page_source_stickers),
}

private val FileItemCorner = 20.dp
private val FileItemPreviewInset = 4.dp

@Composable
private fun FileItem(
    file: ManagedFileEntity,
    fileOnDisk: File,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FileItemCorner),
        color = CustomColors.listItemColors.containerColor,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .padding(FileItemPreviewInset)
                    // 预览图内缩一圈，圆角跟着减小，和卡片外沿保持同心
                    .clip(RoundedCornerShape(FileItemCorner - FileItemPreviewInset))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                if (file.mimeType.startsWith("image/")) {
                    AsyncImage(
                        model = fileOnDisk,
                        contentDescription = file.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(64.dp)
                            .background(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialShapes.Cookie9Sided.toShape(),
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                file.mimeType.startsWith("video/") -> HugeIcons.FileVideo
                                file.mimeType.startsWith("audio/") -> HugeIcons.FileAudio
                                else -> HugeIcons.File01
                            },
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // 带底色的按钮，叠在图片上也看得清
                FilledTonalIconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = HugeIcons.Delete01,
                        contentDescription = stringResource(R.string.setting_files_page_delete_content_description),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${file.mimeType} · ${file.sizeBytes.fileSizeToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
