package me.rerere.rikkahub.ui.pages.setting

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.ChatFontFamily
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.FileUtils
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupValue
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.rememberChatFontFamily
import me.rerere.ui.toast.ToastType
import java.io.File

@Composable
fun FontSettingsContent(
    displaySetting: DisplaySetting,
    boldText: Boolean,
    onDisplaySettingChange: (DisplaySetting) -> Unit,
    onBoldTextChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val chatFontFamily = rememberChatFontFamily(displaySetting)
    val importSuccess = stringResource(R.string.setting_display_page_custom_font_import_success)
    val importFailed = stringResource(R.string.setting_display_page_custom_font_import_failed)
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { importCustomChatFont(context, uri) } }
                .onSuccess { imported ->
                    onDisplaySettingChange(
                        displaySetting.copy(
                            chatFontFamily = ChatFontFamily.CUSTOM,
                            chatCustomFontPath = imported.relativePath,
                            chatCustomFontName = imported.displayName,
                        )
                    )
                    toaster.show(importSuccess, type = ToastType.Success)
                }
                .onFailure { toaster.show(importFailed.format(it.message.orEmpty()), type = ToastType.Error) }
        }
    }

    CardGroup {
        formItem(
            headlineContent = { Text(stringResource(R.string.setting_display_page_font_size_title)) },
            trailingContent = { CardGroupValue("${(displaySetting.fontSizeRatio * 100).toInt()}%") },
        ) {
            Slider(
                value = displaySetting.fontSizeRatio,
                onValueChange = { onDisplaySettingChange(displaySetting.copy(fontSizeRatio = it)) },
                valueRange = 0.5f..2f,
                steps = 11,
                modifier = Modifier.fillMaxWidth(),
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                MarkdownBlock(
                    content = stringResource(R.string.setting_display_page_font_size_preview),
                    modifier = Modifier.padding(12.dp),
                    style = LocalTextStyle.current.copy(
                        fontSize = LocalTextStyle.current.fontSize * displaySetting.fontSizeRatio,
                        lineHeight = LocalTextStyle.current.lineHeight * displaySetting.fontSizeRatio,
                        fontFamily = chatFontFamily,
                    ),
                )
            }
        }
        switchItem(
            checked = boldText,
            onCheckedChange = onBoldTextChange,
            headlineContent = { Text(stringResource(R.string.infoos_settings_bold_text)) },
        )
        formItem(headlineContent = { Text(stringResource(R.string.setting_display_page_chat_font_family_title)) }) {
            ConnectedChoiceButtons(
                options = ChatFontFamily.entries,
                selected = displaySetting.chatFontFamily,
                onSelect = { family ->
                    if (family == ChatFontFamily.CUSTOM && displaySetting.chatCustomFontPath.isBlank()) {
                        fontPicker.launch(CustomFontMimeTypes)
                    } else {
                        onDisplaySettingChange(displaySetting.copy(chatFontFamily = family))
                    }
                },
                minHeight = 64.dp,
            ) { family, _ ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Aa",
                        fontFamily = family.toFontFamily(chatFontFamily),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = family.label(),
                        autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 11.sp),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        item(
            onClick = { fontPicker.launch(CustomFontMimeTypes) },
            headlineContent = { Text(stringResource(R.string.setting_display_page_custom_font_title)) },
            supportingContent = {
                Text(
                    displaySetting.chatCustomFontName.ifBlank {
                        stringResource(R.string.setting_display_page_custom_font_not_imported)
                    }
                )
            },
            trailingContent = {
                Row {
                    IconButton(onClick = { fontPicker.launch(CustomFontMimeTypes) }) {
                        Icon(HugeIcons.FileImport, stringResource(R.string.setting_display_page_custom_font_import))
                    }
                    if (displaySetting.chatCustomFontPath.isNotBlank()) {
                        IconButton(onClick = {
                            deleteCustomChatFont(context, displaySetting.chatCustomFontPath)
                            onDisplaySettingChange(
                                displaySetting.copy(
                                    chatFontFamily = ChatFontFamily.DEFAULT,
                                    chatCustomFontPath = "",
                                    chatCustomFontName = "",
                                )
                            )
                        }) {
                            Icon(HugeIcons.Delete02, stringResource(R.string.setting_display_page_custom_font_remove))
                        }
                    }
                }
            },
        )
    }
}

private val CustomFontMimeTypes = arrayOf(
    "font/*", "application/x-font-ttf", "application/x-font-otf",
    "application/vnd.ms-opentype", "application/octet-stream", "*/*",
)
private val CustomFontExtensions = setOf("ttf", "otf", "ttc")
private data class ImportedChatFont(val relativePath: String, val displayName: String)

@Composable
private fun ChatFontFamily.label(): String = when (this) {
    ChatFontFamily.DEFAULT -> stringResource(R.string.setting_display_page_chat_font_family_default)
    ChatFontFamily.SERIF -> stringResource(R.string.setting_display_page_chat_font_family_serif)
    ChatFontFamily.MONOSPACE -> stringResource(R.string.setting_display_page_chat_font_family_monospace)
    ChatFontFamily.CUSTOM -> stringResource(R.string.setting_display_page_chat_font_family_custom)
}

private fun ChatFontFamily.toFontFamily(custom: FontFamily): FontFamily = when (this) {
    ChatFontFamily.DEFAULT -> FontFamily.Default
    ChatFontFamily.SERIF -> FontFamily.Serif
    ChatFontFamily.MONOSPACE -> FontFamily.Monospace
    ChatFontFamily.CUSTOM -> custom
}

private fun importCustomChatFont(context: Context, uri: Uri): ImportedChatFont {
    val displayName = FileUtils.getFileNameFromUri(context, uri)?.takeIf(String::isNotBlank) ?: "custom_font"
    val extension = displayName.substringAfterLast('.', "").lowercase()
        .takeIf { it in CustomFontExtensions } ?: "ttf"
    val fontDir = File(context.filesDir, FileFolders.FONTS).apply { mkdirs() }
    val target = File(fontDir, "chat_font.${System.currentTimeMillis()}.$extension")
    val temp = File(fontDir, "chat_font_import.tmp")
    try {
        temp.delete()
        context.contentResolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to open selected font")
        Typeface.createFromFile(temp)
        replaceCustomChatFont(fontDir, temp, target)
    } catch (error: Throwable) {
        temp.delete()
        throw error
    }
    return ImportedChatFont(
        relativePath = FileUtils.getRelativePathInFilesDir(context.filesDir, target)
            ?: "${FileFolders.FONTS}/${target.name}",
        displayName = displayName,
    )
}

private fun replaceCustomChatFont(fontDir: File, temp: File, target: File) {
    val existing = fontDir.listFiles { file ->
        file.isFile && file.name.startsWith("chat_font.") && file != temp
    }?.toList().orEmpty()
    val backups = existing.map { file ->
        file to File(fontDir, "previous_${file.name}").also { it.delete() }
    }
    try {
        backups.forEach { (file, backup) -> check(file.renameTo(backup)) }
        check(temp.renameTo(target))
        backups.forEach { (_, backup) -> backup.delete() }
    } catch (error: Throwable) {
        temp.delete()
        backups.forEach { (file, backup) -> if (!file.exists() && backup.exists()) backup.renameTo(file) }
        throw error
    }
}

private fun deleteCustomChatFont(context: Context, relativePath: String) {
    val root = runCatching { context.filesDir.canonicalFile }.getOrNull() ?: return
    val file = runCatching { File(root, relativePath).canonicalFile }.getOrNull() ?: return
    if (file.path.startsWith("${root.path}${File.separator}")) file.delete()
}
