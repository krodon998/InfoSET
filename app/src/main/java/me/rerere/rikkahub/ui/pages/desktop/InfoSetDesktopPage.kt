package me.rerere.rikkahub.ui.pages.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.InfoOsSettings
import me.rerere.rikkahub.data.datastore.InfoOsDesktopFolder
import coil3.compose.AsyncImage

private val DesktopInk = Color(0xFF327F9E)

private enum class InfoSetSurface(val id: String) {
    Chat("chat"),
    Memory("memory"),
    Mailbox("mailbox"),
    Settings("settings"),
}

@Composable
fun InfoSetDesktopPage(
    infoOsSettings: InfoOsSettings,
    onOpenChat: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenMailbox: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeWallpaper = infoOsSettings.wallpapers.find { it.id == infoOsSettings.activeWallpaperId }
    val assignedApps = infoOsSettings.desktopFolders.flatMap { it.appIds }.toSet()
    var openFolderId by remember { mutableStateOf<String?>(null) }
    val openFolder = infoOsSettings.desktopFolders.find { it.id == openFolderId }

    fun actionFor(surface: InfoSetSurface): () -> Unit = when (surface) {
        InfoSetSurface.Chat -> onOpenChat
        InfoSetSurface.Memory -> onOpenMemory
        InfoSetSurface.Mailbox -> onOpenMailbox
        InfoSetSurface.Settings -> onOpenSettings
    }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        if (!activeWallpaper?.homeUri.isNullOrBlank()) {
            AsyncImage(
                model = activeWallpaper?.homeUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = activeWallpaper?.homeScale ?: 1f
                        scaleY = scaleX
                        translationX = activeWallpaper?.homeOffsetX ?: 0f
                        translationY = activeWallpaper?.homeOffsetY ?: 0f
                    },
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFFCBEAF7),
                        0.46f to Color(0xFFF9FDFF),
                        1f to Color(0xFFBDE9FB),
                    )
                        )
                    )
            )
        }
        Canvas(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(260.dp)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.7f), Color.Transparent)
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.app_name),
                color = DesktopInk.copy(alpha = 0.82f),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(infoOsSettings.homeGrid.columns),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 44.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                items(InfoSetSurface.entries.filterNot { it.id in assignedApps }, key = { it.name }) { surface ->
                    val label = when (surface) {
                        InfoSetSurface.Chat -> stringResource(R.string.infoset_desktop_chat)
                        InfoSetSurface.Memory -> stringResource(R.string.infoset_desktop_memory)
                        InfoSetSurface.Mailbox -> stringResource(R.string.infoset_desktop_mailbox)
                        InfoSetSurface.Settings -> stringResource(R.string.infoset_desktop_settings)
                    }
                    DesktopSurfaceIcon(
                        surface = surface,
                        label = label,
                        large = infoOsSettings.largeAppIcons,
                        onClick = actionFor(surface),
                    )
                }
                items(infoOsSettings.desktopFolders, key = { it.id }) { folder ->
                    DesktopFolderIcon(
                        folder = folder,
                        large = infoOsSettings.largeAppIcons,
                        onClick = { openFolderId = folder.id },
                    )
                }
            }

            if (infoOsSettings.dockVisible) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White.copy(
                        alpha = (1f - infoOsSettings.dockTransparencyPercent / 100f).coerceIn(0.12f, 0.92f)
                    ),
                    tonalElevation = if (infoOsSettings.dockEffectStrengthPercent > 50) 8.dp else 2.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (infoOsSettings.dockShowsAppDrawer) {
                            Text(
                                text = "•••",
                                color = DesktopInk,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(onClick = onOpenSettings)
                                    .padding(12.dp),
                            )
                        }
                        if (infoOsSettings.dockShowsSuggestions) {
                            InfoSetSurface.entries.forEach { surface ->
                                val action = actionFor(surface)
                                Box(
                                    modifier = Modifier
                                        .size((54 * infoOsSettings.dockScale).dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White.copy(alpha = 0.58f))
                                        .clickable(onClick = action),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    when (surface) {
                                        InfoSetSurface.Chat -> Image(
                                            painter = painterResource(R.drawable.infoset_launcher_foreground),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                        InfoSetSurface.Memory -> Icon(HugeIcons.Brain02, null, tint = DesktopInk)
                                        InfoSetSurface.Mailbox -> MailboxGlyph(Modifier.size(25.dp), DesktopInk)
                                        InfoSetSurface.Settings -> Icon(HugeIcons.Settings03, null, tint = DesktopInk)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (openFolder != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(24.dp),
                shape = RoundedCornerShape(32.dp),
                color = Color.White.copy(alpha = 0.92f),
                tonalElevation = 12.dp,
                onClick = { },
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(openFolder.name, color = DesktopInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "×",
                            color = DesktopInk,
                            fontSize = 24.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { openFolderId = null }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(openFolder.expandedGrid.columns),
                        modifier = Modifier.height((openFolder.expandedGrid.rows * 98).dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(InfoSetSurface.entries.filter { it.id in openFolder.appIds }) { surface ->
                            DesktopSurfaceIcon(
                                surface = surface,
                                label = when (surface) {
                                    InfoSetSurface.Chat -> stringResource(R.string.infoset_desktop_chat)
                                    InfoSetSurface.Memory -> stringResource(R.string.infoset_desktop_memory)
                                    InfoSetSurface.Mailbox -> stringResource(R.string.infoset_desktop_mailbox)
                                    InfoSetSurface.Settings -> stringResource(R.string.infoset_desktop_settings)
                                },
                                large = false,
                                onClick = actionFor(surface),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopFolderIcon(
    folder: InfoOsDesktopFolder,
    large: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(if (large || folder.coverSize.columns == 2) 78.dp else 70.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.72f))
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = folder.appIds.take(4).joinToString(" ") {
                    when (it) {
                        "chat" -> "●"
                        "memory" -> "◉"
                        "mailbox" -> "✉"
                        else -> "⚙"
                    }
                }.ifBlank { "□" },
                color = DesktopInk,
                fontSize = 18.sp,
            )
        }
        Text(
            text = folder.name,
            modifier = Modifier.padding(top = 7.dp),
            color = DesktopInk.copy(alpha = 0.92f),
            fontSize = 13.sp,
            maxLines = 1,
        )
    }
}
@Composable
private fun DesktopSurfaceIcon(
    surface: InfoSetSurface,
    label: String,
    large: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(if (large) 78.dp else 70.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.72f))
                .testTag("infoset-desktop-${surface.name.lowercase()}")
                .semantics { contentDescription = label }
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            when (surface) {
                InfoSetSurface.Chat -> Image(
                    painter = painterResource(R.drawable.infoset_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )

                InfoSetSurface.Memory -> Icon(
                    imageVector = HugeIcons.Brain02,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    tint = DesktopInk,
                )

                InfoSetSurface.Mailbox -> MailboxGlyph(
                    modifier = Modifier.size(35.dp),
                    color = DesktopInk,
                )

                InfoSetSurface.Settings -> Icon(
                    imageVector = HugeIcons.Settings03,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    tint = DesktopInk,
                )
            }
        }

        Text(
            text = label,
            modifier = Modifier.padding(top = 7.dp),
            color = DesktopInk.copy(alpha = 0.92f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
@Composable
private fun MailboxGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.065f
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.2f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.84f, size.height * 0.62f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.08f),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.27f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.58f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.88f, size.height * 0.27f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.58f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
