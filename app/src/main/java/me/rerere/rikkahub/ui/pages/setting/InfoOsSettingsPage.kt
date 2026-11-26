package me.rerere.rikkahub.ui.pages.setting

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.AutomaticUpdates
import me.rerere.rikkahub.data.datastore.DockMaterial
import me.rerere.rikkahub.data.datastore.FolderCoverSize
import me.rerere.rikkahub.data.datastore.FolderGrid
import me.rerere.rikkahub.data.datastore.HomeGrid
import me.rerere.rikkahub.data.datastore.InfoOsNotificationPreference
import me.rerere.rikkahub.data.datastore.InfoOsDesktopFolder
import me.rerere.rikkahub.data.datastore.InfoOsWallpaper
import me.rerere.rikkahub.data.datastore.NewAppPlacement
import me.rerere.rikkahub.data.datastore.RequirePasscode
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupValue
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.rememberColorMode
import me.rerere.rikkahub.ui.theme.ColorMode
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.utils.UpdateChecker
import me.rerere.rikkahub.utils.Version
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlin.math.roundToInt

enum class InfoOsSettingSection {
    ACCOUNT,
    PERSONAL_INFO,
    LOGIN_SECURITY,
    GENERAL,
    SOFTWARE_UPDATE,
    AIRE_INTELLIGENCE,
    APPS_SERVICES,
    DISPLAY_BRIGHTNESS,
    HOME_DOCK,
    DESKTOP_FOLDERS,
    WALLPAPER,
    NOTIFICATIONS,
    APP_NOTIFICATION,
}

private enum class WallpaperTarget { LOCK, HOME, BOTH }

@Composable
fun InfoOsSettingsPage(
    sectionName: String,
    detail: String = "",
    vm: SettingVM = koinViewModel(),
) {
    val section = remember(sectionName) {
        runCatching { InfoOsSettingSection.valueOf(sectionName) }.getOrDefault(InfoOsSettingSection.GENERAL)
    }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val info = settings.infoOsSettings
    val navController = LocalNavController.current
    val context = LocalContext.current
    val updateChecker: UpdateChecker = koinInject()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val fingerprintAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.USE_BIOMETRIC) ==
        PackageManager.PERMISSION_GRANTED

    fun navigate(target: InfoOsSettingSection, targetDetail: String = "") {
        navController.navigate(Screen.InfoOsSetting(target.name, targetDetail))
    }

    fun updateInfo(transform: (me.rerere.rikkahub.data.datastore.InfoOsSettings) -> me.rerere.rikkahub.data.datastore.InfoOsSettings) {
        vm.updateSettings { latest -> latest.copy(infoOsSettings = transform(latest.infoOsSettings)) }
    }

    var wallpaperTarget by remember { mutableStateOf<WallpaperTarget?>(null) }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = wallpaperTarget
        wallpaperTarget = null
        if (uri != null && target != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val current = info.wallpapers.find { it.id == info.activeWallpaperId }
            val id = UUID.randomUUID().toString()
            val value = uri.toString()
            val wallpaper = when (target) {
                WallpaperTarget.LOCK -> InfoOsWallpaper(id = id, lockUri = value, homeUri = current?.homeUri.orEmpty())
                WallpaperTarget.HOME -> InfoOsWallpaper(id = id, lockUri = current?.lockUri.orEmpty(), homeUri = value)
                WallpaperTarget.BOTH -> InfoOsWallpaper(id = id, lockUri = value, homeUri = value)
            }
            updateInfo { it.copy(wallpapers = it.wallpapers + wallpaper) }
        }
    }

    var showPasscodeDialog by remember { mutableStateOf(false) }
    var passcode by remember { mutableStateOf("") }
    var passcodeError by remember { mutableStateOf(false) }
    var showGridDialog by remember { mutableStateOf(false) }
    var showPlacementDialog by remember { mutableStateOf(false) }
    var showMaterialDialog by remember { mutableStateOf(false) }
    var showUpdateModeDialog by remember { mutableStateOf(false) }
    var showRequireDialog by remember { mutableStateOf(false) }
    var showFolderCoverDialog by remember { mutableStateOf(false) }
    var showFolderGridDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var editingFolderId by remember { mutableStateOf<String?>(null) }
    var showAppearanceScheduleDialog by remember { mutableStateOf(false) }
    var customMinutes by remember(info.requirePasscodeAfterMinutes) {
        mutableStateOf(info.requirePasscodeAfterMinutes.toString())
    }
    var customizingWallpaperId by remember { mutableStateOf<String?>(null) }
    var lightTime by remember(info.lightAppearanceMinutes) {
        mutableStateOf(formatMinutes(info.lightAppearanceMinutes))
    }
    var darkTime by remember(info.darkAppearanceMinutes) {
        mutableStateOf(formatMinutes(info.darkAppearanceMinutes))
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(section.titleRes(detail))) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (section) {
                InfoOsSettingSection.ACCOUNT -> item {
                    CardGroup {
                        item(
                            onClick = { navigate(InfoOsSettingSection.PERSONAL_INFO) },
                            headlineContent = { Text(stringResource(R.string.infoos_settings_personal_info)) },
                            supportingContent = { Text(stringResource(R.string.infoos_settings_local_profile)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                        item(
                            onClick = { navigate(InfoOsSettingSection.LOGIN_SECURITY) },
                            headlineContent = { Text(stringResource(R.string.infoos_settings_login_security)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                        item(
                            onClick = { navController.navigate(Screen.Backup) },
                            headlineContent = { Text(stringResource(R.string.infoos_settings_backup)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                        item(
                            headlineContent = { Text(stringResource(R.string.infoos_settings_sign_out)) },
                            supportingContent = { Text(stringResource(R.string.infoos_settings_no_cloud_account)) },
                        )
                    }
                }

                InfoOsSettingSection.PERSONAL_INFO -> item {
                    val displayName = settings.displaySetting.userNickname.takeIf(String::isNotBlank)
                        ?: stringResource(R.string.infoos_settings_local_profile)
                    CardGroup {
                        item(
                            headlineContent = { Text(stringResource(R.string.infoos_settings_display_name)) },
                            trailingContent = { CardGroupValue(displayName) },
                        )
                        formItem(headlineContent = { Text(stringResource(R.string.infoos_settings_decorative_avatar)) }) {
                            UIAvatar(
                                name = displayName,
                                value = settings.displaySetting.userAvatar,
                                modifier = Modifier.size(128.dp),
                                onUpdate = { avatar ->
                                    vm.updateSettings { latest ->
                                        latest.copy(
                                            displaySetting = latest.displaySetting.copy(userAvatar = avatar)
                                        )
                                    }
                                },
                            )
                            if (settings.displaySetting.userAvatar !is Avatar.Dummy) {
                                OutlinedButton(onClick = {
                                    vm.updateSettings { latest ->
                                        latest.copy(
                                            displaySetting = latest.displaySetting.copy(userAvatar = Avatar.Dummy)
                                        )
                                    }
                                }) {
                                    Text(stringResource(R.string.infoos_settings_restore_initial))
                                }
                            }
                        }
                    }
                }

                InfoOsSettingSection.LOGIN_SECURITY -> item {
                    CardGroup {
                        item(
                            onClick = { showPasscodeDialog = true },
                            headlineContent = {
                                Text(
                                    stringResource(
                                        if (info.passcodeHash.isBlank()) R.string.infoos_settings_set_password
                                        else R.string.infoos_settings_change_password
                                    )
                                )
                            },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                        if (info.passcodeHash.isNotBlank()) {
                            switchItem(
                                checked = info.fingerprintEnabled,
                                enabled = fingerprintAvailable,
                                onCheckedChange = { enabled -> updateInfo { it.copy(fingerprintEnabled = enabled) } },
                                headlineContent = { Text(stringResource(R.string.infoos_settings_fingerprint)) },
                                supportingContent = if (!fingerprintAvailable) {
                                    { Text(stringResource(R.string.infoos_settings_fingerprint_unavailable)) }
                                } else null,
                            )
                            item(
                                onClick = { showRequireDialog = true },
                                headlineContent = { Text(stringResource(R.string.infoos_settings_require_password)) },
                                trailingContent = {
                                    CardGroupValue(
                                        if (info.requirePasscode == RequirePasscode.IMMEDIATELY) {
                                            stringResource(R.string.infoos_settings_immediately)
                                        } else {
                                            stringResource(R.string.infoos_settings_after_minutes, info.requirePasscodeAfterMinutes)
                                        }
                                    )
                                },
                            )
                            item(
                                onClick = {
                                    updateInfo {
                                        it.copy(passcodeSalt = "", passcodeHash = "", fingerprintEnabled = false)
                                    }
                                },
                                headlineContent = { Text(stringResource(R.string.infoos_settings_remove_password)) },
                            )
                        }
                    }
                }

                InfoOsSettingSection.GENERAL -> item {
                    CardGroup {
                        item(
                            onClick = { navigate(InfoOsSettingSection.SOFTWARE_UPDATE) },
                            headlineContent = { Text(stringResource(R.string.infoos_settings_software_update)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                        item(
                            onClick = { navController.navigate(Screen.SettingPreferencesGeneral) },
                            headlineContent = { Text(stringResource(R.string.setting_page_preferences_general)) },
                            supportingContent = { Text(stringResource(R.string.setting_page_preferences_general_desc)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                        item(
                            onClick = { navController.navigate(Screen.SettingPreferencesNetwork) },
                            headlineContent = { Text(stringResource(R.string.setting_page_preferences_network)) },
                            supportingContent = { Text(stringResource(R.string.setting_page_preferences_network_desc)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                        item(
                            onClick = { navController.navigate(Screen.Backup) },
                            headlineContent = { Text(stringResource(R.string.setting_page_data_backup)) },
                            supportingContent = { Text(stringResource(R.string.setting_page_data_backup_desc)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                        item(
                            onClick = { navController.navigate(Screen.SettingFiles) },
                            headlineContent = { Text(stringResource(R.string.setting_page_chat_storage)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                        item(
                            onClick = { navController.navigate(Screen.QuickStart) },
                            headlineContent = { Text(stringResource(R.string.setting_page_quick_start)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                        item(
                            onClick = { navController.navigate(Screen.Log) },
                            headlineContent = { Text(stringResource(R.string.setting_page_request_logs)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                        item(
                            onClick = { navController.navigate(Screen.SettingAbout) },
                            headlineContent = { Text(stringResource(R.string.setting_page_about)) },
                            supportingContent = { Text(stringResource(R.string.setting_page_about_desc)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                        item(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, context.getString(R.string.setting_page_share_text))
                                }
                                runCatching {
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, context.getString(R.string.setting_page_share))
                                    )
                                }.onFailure {
                                    Toast.makeText(context, R.string.setting_page_no_share_app, Toast.LENGTH_SHORT).show()
                                }
                            },
                            headlineContent = { Text(stringResource(R.string.setting_page_share)) },
                            supportingContent = { Text(stringResource(R.string.setting_page_share_desc)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                    }
                }

                InfoOsSettingSection.AIRE_INTELLIGENCE -> item {
                    CardGroup {
                        item(
                            onClick = { navController.navigate(Screen.Assistant) },
                            headlineContent = { Text(stringResource(R.string.setting_page_assistant)) },
                            supportingContent = { Text(stringResource(R.string.setting_page_assistant_desc)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                        item(
                            onClick = { navController.navigate(Screen.SettingModels) },
                            headlineContent = { Text(stringResource(R.string.setting_page_default_model)) },
                            supportingContent = { Text(stringResource(R.string.setting_page_default_model_desc)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                        item(
                            onClick = { navController.navigate(Screen.SettingProvider) },
                            headlineContent = { Text(stringResource(R.string.setting_page_providers)) },
                            supportingContent = { Text(stringResource(R.string.setting_page_providers_desc)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                        item(
                            onClick = { navController.navigate(Screen.SettingSearch) },
                            headlineContent = { Text(stringResource(R.string.setting_page_search_service)) },
                            supportingContent = { Text(stringResource(R.string.setting_page_search_service_desc)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                        item(
                            onClick = { navController.navigate(Screen.SettingSpeech) },
                            headlineContent = { Text(stringResource(R.string.setting_page_tts_service)) },
                            supportingContent = { Text(stringResource(R.string.setting_page_tts_service_desc)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                        item(
                            onClick = { navController.navigate(Screen.SettingMedia) },
                            headlineContent = { Text(stringResource(R.string.setting_page_media)) },
                            supportingContent = { Text(stringResource(R.string.setting_page_media_desc)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                    }
                }

                InfoOsSettingSection.APPS_SERVICES -> item {
                    CardGroup {
                        item(
                            onClick = { navController.navigate(Screen.Extensions) },
                            headlineContent = { Text(stringResource(R.string.setting_page_extensions)) },
                            supportingContent = { Text(stringResource(R.string.setting_page_extensions_desc)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                        item(
                            onClick = { navController.navigate(Screen.SettingMcp) },
                            headlineContent = { Text(stringResource(R.string.setting_page_mcp)) },
                            supportingContent = { Text(stringResource(R.string.setting_page_mcp_desc)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                        item(
                            onClick = { navController.navigate(Screen.SettingWeb) },
                            headlineContent = { Text(stringResource(R.string.setting_page_web_server)) },
                            supportingContent = { Text(stringResource(R.string.setting_page_web_server_desc)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                    }
                }

                InfoOsSettingSection.SOFTWARE_UPDATE -> {
                    item {
                        CardGroup {
                            item(
                                headlineContent = { Text(stringResource(R.string.infoos_settings_current_version)) },
                                trailingContent = { CardGroupValue(BuildConfig.VERSION_NAME) },
                            )
                            item(
                                onClick = { showUpdateModeDialog = true },
                                headlineContent = { Text(stringResource(R.string.infoos_settings_automatic_updates)) },
                                supportingContent = {
                                    Text(
                                        stringResource(
                                            if (info.automaticUpdates == AutomaticUpdates.WIFI_ONLY) {
                                                R.string.infoos_settings_wifi_auto
                                            } else {
                                                R.string.infoos_settings_manual_update
                                            }
                                        )
                                    )
                                },
                                trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                            )
                        }
                    }
                    item {
                        val updateState by updateChecker.updateState.collectAsStateWithLifecycle()
                        when (val state = updateState) {
                            UiState.Loading, UiState.Idle -> CircularProgressIndicator()
                            is UiState.Error -> Text(stringResource(R.string.infoos_settings_update_check_failed))
                            is UiState.Success -> {
                                val latest = state.data
                                val isNewer = Version(latest.version) > Version(BuildConfig.VERSION_NAME)
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        if (isNewer) latest.version else stringResource(R.string.infoos_settings_up_to_date),
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                    if (latest.changelog.isNotBlank()) {
                                        MarkdownBlock(latest.changelog, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    if (isNewer && latest.downloads.isNotEmpty()) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Button(onClick = {
                                                updateChecker.downloadUpdate(context, latest.downloads.first())
                                            }) { Text(stringResource(R.string.infoos_settings_update_now)) }
                                            OutlinedButton(onClick = {
                                                updateInfo { it.copy(dismissedUpdateVersion = latest.version) }
                                            }) { Text(stringResource(R.string.infoos_settings_later)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                InfoOsSettingSection.DISPLAY_BRIGHTNESS -> item {
                    var colorMode by rememberColorMode()
                    CardGroup {
                        formItem(headlineContent = { Text(stringResource(R.string.infoos_settings_appearance)) }) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ColorMode.entries.forEach { mode ->
                                    OutlinedButton(onClick = { colorMode = mode }) {
                                        Text(
                                            stringResource(
                                                when (mode) {
                                                    ColorMode.SYSTEM -> R.string.setting_page_color_mode_system
                                                    ColorMode.LIGHT -> R.string.setting_page_color_mode_light
                                                    ColorMode.DARK -> R.string.setting_page_color_mode_dark
                                                }
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        switchItem(
                            checked = info.automaticAppearance,
                            onCheckedChange = { value -> updateInfo { it.copy(automaticAppearance = value) } },
                            headlineContent = { Text(stringResource(R.string.infoos_settings_appearance_auto)) },
                            supportingContent = {
                                Text("${formatMinutes(info.lightAppearanceMinutes)} · ${formatMinutes(info.darkAppearanceMinutes)}")
                            },
                        )
                        if (info.automaticAppearance) {
                            item(
                                onClick = { showAppearanceScheduleDialog = true },
                                headlineContent = { Text(stringResource(R.string.infoos_settings_appearance_schedule)) },
                                trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                            )
                        }
                        formItem(
                            headlineContent = { Text(stringResource(R.string.infoos_settings_text_size)) },
                            trailingContent = { CardGroupValue("${(settings.displaySetting.fontSizeRatio * 100).roundToInt()}%") },
                        ) {
                            Slider(
                                value = settings.displaySetting.fontSizeRatio,
                                onValueChange = { value ->
                                    vm.updateSettings { latest ->
                                        latest.copy(displaySetting = latest.displaySetting.copy(fontSizeRatio = value))
                                    }
                                },
                                valueRange = 0.8f..1.4f,
                            )
                        }
                        switchItem(
                            checked = info.boldText,
                            onCheckedChange = { value -> updateInfo { it.copy(boldText = value) } },
                            headlineContent = { Text(stringResource(R.string.infoos_settings_bold_text)) },
                        )
                        item(
                            onClick = { navController.navigate(Screen.SettingPreferencesUI) },
                            headlineContent = { Text(stringResource(R.string.infoos_settings_custom_font)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                        item(
                            onClick = { navController.navigate(Screen.SettingPreferencesTheme) },
                            headlineContent = { Text(stringResource(R.string.setting_page_preferences_theme)) },
                            supportingContent = { Text(stringResource(R.string.setting_page_preferences_theme_desc)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                        item(
                            onClick = { navController.navigate(Screen.SettingPreferencesUI) },
                            headlineContent = { Text(stringResource(R.string.setting_page_preferences_ui)) },
                            supportingContent = { Text(stringResource(R.string.setting_page_preferences_ui_desc)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                    }
                }

                InfoOsSettingSection.HOME_DOCK -> item {
                    CardGroup {
                        item(
                            onClick = { showGridDialog = true },
                            headlineContent = { Text(stringResource(R.string.infoos_settings_desktop_layout)) },
                            supportingContent = {
                                Text(
                                    if (info.dockVisible) {
                                        "${info.homeGrid.columns} × ${info.homeGrid.dockRows}"
                                    } else {
                                        "${info.homeGrid.columns} × ${info.homeGrid.dockRows + 1}"
                                    }
                                )
                            },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                        switchItem(
                            checked = info.layoutLocked,
                            onCheckedChange = { value -> updateInfo { it.copy(layoutLocked = value) } },
                            headlineContent = { Text(stringResource(R.string.infoos_settings_lock_layout)) },
                        )
                        item(
                            onClick = { navigate(InfoOsSettingSection.DESKTOP_FOLDERS) },
                            headlineContent = { Text(stringResource(R.string.infoos_settings_desktop_folders)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                        switchItem(
                            checked = info.largeAppIcons,
                            onCheckedChange = { value -> updateInfo { it.copy(largeAppIcons = value) } },
                            headlineContent = { Text(stringResource(R.string.infoos_settings_large_icons)) },
                        )
                        item(
                            onClick = { showPlacementDialog = true },
                            headlineContent = { Text(stringResource(R.string.infoos_settings_new_apps)) },
                            trailingContent = {
                                CardGroupValue(
                                    stringResource(
                                        if (info.newAppPlacement == NewAppPlacement.DRAWER_ONLY) {
                                            R.string.infoos_settings_drawer_only
                                        } else {
                                            R.string.infoos_settings_home_and_drawer
                                        }
                                    )
                                )
                            },
                        )
                        switchItem(
                            checked = info.dockVisible,
                            onCheckedChange = { value -> updateInfo { it.copy(dockVisible = value) } },
                            headlineContent = { Text(stringResource(R.string.infoos_settings_show_dock)) },
                        )
                        if (info.dockVisible) {
                            switchItem(
                                checked = info.dockShowsAppDrawer,
                                onCheckedChange = { value -> updateInfo { it.copy(dockShowsAppDrawer = value) } },
                                headlineContent = { Text(stringResource(R.string.infoos_settings_drawer_entry)) },
                            )
                            switchItem(
                                checked = info.dockShowsSuggestions,
                                onCheckedChange = { value -> updateInfo { it.copy(dockShowsSuggestions = value) } },
                                headlineContent = { Text(stringResource(R.string.infoos_settings_suggestions)) },
                            )
                            formItem(
                                headlineContent = { Text(stringResource(R.string.infoos_settings_dock_scale)) },
                                trailingContent = { CardGroupValue("${(info.dockScale * 100).roundToInt()}%") },
                            ) {
                                Slider(
                                    value = info.dockScale,
                                    onValueChange = { value -> updateInfo { it.copy(dockScale = value) } },
                                    valueRange = 0.75f..1.25f,
                                )
                            }
                            item(
                                onClick = { showMaterialDialog = true },
                                headlineContent = { Text(stringResource(R.string.infoos_settings_material)) },
                                trailingContent = {
                                    CardGroupValue(
                                        stringResource(
                                            if (info.dockMaterial == DockMaterial.FROSTED_GLASS) {
                                                R.string.infoos_settings_frosted_glass
                                            } else {
                                                R.string.infoos_settings_liquid_glass
                                            }
                                        )
                                    )
                                },
                            )
                            formItem(
                                headlineContent = { Text(stringResource(R.string.infoos_settings_transparency)) },
                                trailingContent = { CardGroupValue("${info.dockTransparencyPercent}%") },
                            ) {
                                Slider(
                                    value = info.dockTransparencyPercent.toFloat(),
                                    onValueChange = { value ->
                                        updateInfo { it.copy(dockTransparencyPercent = value.roundToInt()) }
                                    },
                                    valueRange = 0f..100f,
                                )
                            }
                            formItem(
                                headlineContent = { Text(stringResource(R.string.infoos_settings_effect_strength)) },
                                trailingContent = { CardGroupValue("${info.dockEffectStrengthPercent}%") },
                            ) {
                                Slider(
                                    value = info.dockEffectStrengthPercent.toFloat(),
                                    onValueChange = { value ->
                                        updateInfo { it.copy(dockEffectStrengthPercent = value.roundToInt()) }
                                    },
                                    valueRange = 0f..100f,
                                )
                            }
                        }
                        switchItem(
                            checked = info.homeBadges,
                            onCheckedChange = { value -> updateInfo { it.copy(homeBadges = value) } },
                            headlineContent = { Text(stringResource(R.string.infoos_settings_home_badges)) },
                        )
                        switchItem(
                            checked = info.dockBadges,
                            onCheckedChange = { value -> updateInfo { it.copy(dockBadges = value) } },
                            headlineContent = { Text(stringResource(R.string.infoos_settings_dock_badges)) },
                        )
                    }
                }

                InfoOsSettingSection.DESKTOP_FOLDERS -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = { showNewFolderDialog = true }) {
                            Text(stringResource(R.string.infoos_settings_new_folder))
                        }
                        if (info.desktopFolders.isEmpty()) {
                            Text(stringResource(R.string.infoos_settings_no_folders))
                        }
                        info.desktopFolders.forEach { folder ->
                            CardGroup(title = { Text(folder.name) }) {
                                item(
                                    onClick = {
                                        editingFolderId = folder.id
                                        showFolderCoverDialog = true
                                    },
                                    headlineContent = { Text(stringResource(R.string.infoos_settings_folder_cover_size)) },
                                    trailingContent = {
                                        CardGroupValue("${folder.coverSize.columns} × ${folder.coverSize.rows}")
                                    },
                                )
                                item(
                                    onClick = {
                                        editingFolderId = folder.id
                                        showFolderGridDialog = true
                                    },
                                    headlineContent = { Text(stringResource(R.string.infoos_settings_folder_expanded_grid)) },
                                    trailingContent = {
                                        CardGroupValue("${folder.expandedGrid.columns} × ${folder.expandedGrid.rows}")
                                    },
                                )
                                listOf("chat", "mailbox", "settings").forEach { appId ->
                                    switchItem(
                                        checked = appId in folder.appIds,
                                        onCheckedChange = { checked ->
                                            updateInfo { current ->
                                                current.copy(desktopFolders = current.desktopFolders.map { candidate ->
                                                    if (candidate.id != folder.id) {
                                                        candidate.copy(appIds = candidate.appIds - appId)
                                                    } else {
                                                        candidate.copy(
                                                            appIds = if (checked) {
                                                                (candidate.appIds + appId).distinct()
                                                            } else {
                                                                candidate.appIds - appId
                                                            }
                                                        )
                                                    }
                                                })
                                            }
                                        },
                                        headlineContent = { Text(stringResource(appId.notificationTitleRes())) },
                                    )
                                }
                                item(
                                    onClick = {
                                        updateInfo { current ->
                                            current.copy(desktopFolders = current.desktopFolders.filterNot { it.id == folder.id })
                                        }
                                    },
                                    headlineContent = { Text(stringResource(R.string.delete)) },
                                )
                            }
                        }
                    }
                }

                InfoOsSettingSection.WALLPAPER -> {
                    item {
                        CardGroup(title = { Text(stringResource(R.string.infoos_settings_add_wallpaper)) }) {
                            item(
                                onClick = {
                                    wallpaperTarget = WallpaperTarget.LOCK
                                    wallpaperPicker.launch(arrayOf("image/*"))
                                },
                                headlineContent = { Text(stringResource(R.string.infoos_settings_lock_wallpaper)) },
                            )
                            item(
                                onClick = {
                                    wallpaperTarget = WallpaperTarget.HOME
                                    wallpaperPicker.launch(arrayOf("image/*"))
                                },
                                headlineContent = { Text(stringResource(R.string.infoos_settings_home_wallpaper)) },
                            )
                            item(
                                onClick = {
                                    wallpaperTarget = WallpaperTarget.BOTH
                                    wallpaperPicker.launch(arrayOf("image/*"))
                                },
                                headlineContent = { Text(stringResource(R.string.infoos_settings_both_wallpapers)) },
                            )
                        }
                    }
                    items(info.wallpapers, key = { it.id }) { wallpaper ->
                        CardGroup {
                            formItem(
                                headlineContent = {
                                    Text(
                                        if (wallpaper.id == info.activeWallpaperId) {
                                            stringResource(R.string.infoos_settings_current)
                                        } else {
                                            stringResource(R.string.infoos_settings_wallpaper)
                                        }
                                    )
                                }
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(wallpaper.lockUri, wallpaper.homeUri).forEachIndexed { index, uri ->
                                        AsyncImage(
                                            model = uri.ifBlank { null },
                                            contentDescription = null,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(180.dp)
                                                .clip(MaterialTheme.shapes.large)
                                                .graphicsLayer {
                                                    val lockPreview = index == 0
                                                    scaleX = if (lockPreview) wallpaper.lockScale else wallpaper.homeScale
                                                    scaleY = scaleX
                                                    translationX = if (lockPreview) wallpaper.lockOffsetX else wallpaper.homeOffsetX
                                                    translationY = if (lockPreview) wallpaper.lockOffsetY else wallpaper.homeOffsetY
                                                },
                                            contentScale = ContentScale.Crop,
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (wallpaper.id != info.activeWallpaperId) {
                                        Button(onClick = { updateInfo { it.copy(activeWallpaperId = wallpaper.id) } }) {
                                            Text(stringResource(R.string.infoos_settings_apply))
                                        }
                                    }
                                    OutlinedButton(onClick = {
                                        customizingWallpaperId = if (customizingWallpaperId == wallpaper.id) null else wallpaper.id
                                    }) { Text(stringResource(R.string.infoos_settings_customize)) }
                                    OutlinedButton(onClick = {
                                        updateInfo { current ->
                                            current.copy(
                                                wallpapers = current.wallpapers.filterNot { it.id == wallpaper.id },
                                                activeWallpaperId = current.activeWallpaperId.takeUnless { it == wallpaper.id }.orEmpty(),
                                            )
                                        }
                                    }) { Text(stringResource(R.string.delete)) }
                                }
                                if (customizingWallpaperId == wallpaper.id) {
                                    WallpaperTransformControls(
                                        wallpaper = wallpaper,
                                        onChange = { changed ->
                                            updateInfo { current ->
                                                current.copy(wallpapers = current.wallpapers.map {
                                                    if (it.id == wallpaper.id) changed else it
                                                })
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                InfoOsSettingSection.NOTIFICATIONS -> item {
                    CardGroup {
                        item(
                            onClick = { navController.navigate(Screen.SettingPreferencesNotification) },
                            headlineContent = { Text(stringResource(R.string.setting_page_preferences_notification)) },
                            supportingContent = { Text(stringResource(R.string.setting_page_preferences_notification_desc)) },
                            trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                        )
                        listOf("chat", "mailbox", "settings").forEach { appId ->
                            item(
                                onClick = { navigate(InfoOsSettingSection.APP_NOTIFICATION, appId) },
                                headlineContent = { Text(stringResource(appId.notificationTitleRes())) },
                                trailingContent = { Icon(HugeIcons.ArrowRight01, null) },
                            )
                        }
                    }
                }

                InfoOsSettingSection.APP_NOTIFICATION -> item {
                    val preference = info.notifications[detail] ?: InfoOsNotificationPreference()
                    fun updatePreference(transform: (InfoOsNotificationPreference) -> InfoOsNotificationPreference) {
                        updateInfo { value ->
                            value.copy(notifications = value.notifications + (detail to transform(preference)))
                        }
                    }
                    CardGroup {
                        switchItem(
                            checked = preference.enabled,
                            onCheckedChange = { value ->
                                if (detail == "chat") {
                                    vm.updateSettings { latest ->
                                        val current = latest.infoOsSettings.notifications[detail]
                                            ?: InfoOsNotificationPreference()
                                        latest.copy(
                                            displaySetting = latest.displaySetting.copy(
                                                enableNotificationOnMessageGeneration = value,
                                            ),
                                            infoOsSettings = latest.infoOsSettings.copy(
                                                notifications = latest.infoOsSettings.notifications +
                                                    (detail to current.copy(enabled = value)),
                                            ),
                                        )
                                    }
                                } else {
                                    updatePreference { it.copy(enabled = value) }
                                }
                            },
                            headlineContent = { Text(stringResource(R.string.infoos_settings_allow_notifications)) },
                        )
                        switchItem(
                            checked = preference.lockScreen,
                            enabled = preference.enabled,
                            onCheckedChange = { value -> updatePreference { it.copy(lockScreen = value) } },
                            headlineContent = { Text(stringResource(R.string.infoos_settings_lock_screen)) },
                        )
                        switchItem(
                            checked = preference.floating,
                            enabled = preference.enabled,
                            onCheckedChange = { value -> updatePreference { it.copy(floating = value) } },
                            headlineContent = { Text(stringResource(R.string.infoos_settings_floating)) },
                        )
                        switchItem(
                            checked = preference.badge,
                            enabled = preference.enabled,
                            onCheckedChange = { value -> updatePreference { it.copy(badge = value) } },
                            headlineContent = { Text(stringResource(R.string.infoos_settings_badge)) },
                        )
                        switchItem(
                            checked = preference.showDetails,
                            enabled = preference.enabled,
                            onCheckedChange = { value -> updatePreference { it.copy(showDetails = value) } },
                            headlineContent = { Text(stringResource(R.string.infoos_settings_show_details)) },
                        )
                    }
                }
            }
        }
    }

    if (showPasscodeDialog) {
        AlertDialog(
            onDismissRequest = { showPasscodeDialog = false },
            title = { Text(stringResource(R.string.infoos_settings_enter_password)) },
            text = {
                OutlinedTextField(
                    value = passcode,
                    onValueChange = { value ->
                        passcode = value.filter(Char::isDigit).take(6)
                        passcodeError = false
                    },
                    isError = passcodeError,
                    supportingText = if (passcodeError) {
                        { Text(stringResource(R.string.infoos_settings_password_invalid)) }
                    } else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (passcode.length == 6) {
                        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
                        updateInfo {
                            it.copy(
                                passcodeSalt = Base64.getEncoder().encodeToString(salt),
                                passcodeHash = hashPasscode(passcode, salt),
                                requirePasscode = RequirePasscode.IMMEDIATELY,
                            )
                        }
                        passcode = ""
                        showPasscodeDialog = false
                    } else {
                        passcodeError = true
                    }
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showPasscodeDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showGridDialog) {
        ChoiceDialog(
            title = stringResource(R.string.infoos_settings_desktop_layout),
            options = HomeGrid.entries,
            selected = info.homeGrid,
            label = { "${it.columns} × ${it.dockRows}" },
            onSelect = { updateInfo { current -> current.copy(homeGrid = it) } },
            onDismiss = { showGridDialog = false },
        )
    }
    if (showPlacementDialog) {
        ChoiceDialog(
            title = stringResource(R.string.infoos_settings_new_apps),
            options = NewAppPlacement.entries,
            selected = info.newAppPlacement,
            label = {
                context.getString(
                    if (it == NewAppPlacement.DRAWER_ONLY) R.string.infoos_settings_drawer_only
                    else R.string.infoos_settings_home_and_drawer
                )
            },
            onSelect = { updateInfo { current -> current.copy(newAppPlacement = it) } },
            onDismiss = { showPlacementDialog = false },
        )
    }
    if (showMaterialDialog) {
        ChoiceDialog(
            title = stringResource(R.string.infoos_settings_material),
            options = DockMaterial.entries,
            selected = info.dockMaterial,
            label = {
                context.getString(
                    if (it == DockMaterial.FROSTED_GLASS) R.string.infoos_settings_frosted_glass
                    else R.string.infoos_settings_liquid_glass
                )
            },
            onSelect = { updateInfo { current -> current.copy(dockMaterial = it) } },
            onDismiss = { showMaterialDialog = false },
        )
    }
    if (showUpdateModeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.infoos_settings_automatic_updates),
            options = AutomaticUpdates.entries,
            selected = info.automaticUpdates,
            label = {
                context.getString(
                    if (it == AutomaticUpdates.WIFI_ONLY) R.string.infoos_settings_wifi_auto
                    else R.string.infoos_settings_manual_update
                )
            },
            onSelect = { updateInfo { current -> current.copy(automaticUpdates = it) } },
            onDismiss = { showUpdateModeDialog = false },
        )
    }
    if (showRequireDialog) {
        AlertDialog(
            onDismissRequest = { showRequireDialog = false },
            title = { Text(stringResource(R.string.infoos_settings_require_password)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = info.requirePasscode == RequirePasscode.IMMEDIATELY,
                            onClick = { updateInfo { it.copy(requirePasscode = RequirePasscode.IMMEDIATELY) } },
                        )
                        Text(stringResource(R.string.infoos_settings_immediately))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = info.requirePasscode == RequirePasscode.AFTER_MINUTES,
                            onClick = { updateInfo { it.copy(requirePasscode = RequirePasscode.AFTER_MINUTES) } },
                        )
                        OutlinedTextField(
                            value = customMinutes,
                            onValueChange = { customMinutes = it.filter(Char::isDigit).take(4) },
                            label = { Text(stringResource(R.string.infoos_settings_minutes)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    updateInfo {
                        it.copy(requirePasscodeAfterMinutes = customMinutes.toIntOrNull()?.coerceAtLeast(1) ?: 5)
                    }
                    showRequireDialog = false
                }) { Text(stringResource(R.string.confirm)) }
            },
        )
    }
    if (showFolderCoverDialog) {
        val folder = info.desktopFolders.find { it.id == editingFolderId }
        ChoiceDialog(
            title = stringResource(R.string.infoos_settings_folder_cover_size),
            options = FolderCoverSize.entries,
            selected = folder?.coverSize ?: FolderCoverSize.ONE_BY_ONE,
            label = { "${it.columns} × ${it.rows}" },
            onSelect = { selected ->
                updateInfo { current ->
                    current.copy(desktopFolders = current.desktopFolders.map {
                        if (it.id == editingFolderId) it.copy(coverSize = selected) else it
                    })
                }
            },
            onDismiss = { showFolderCoverDialog = false },
        )
    }
    if (showFolderGridDialog) {
        val folder = info.desktopFolders.find { it.id == editingFolderId }
        ChoiceDialog(
            title = stringResource(R.string.infoos_settings_folder_expanded_grid),
            options = FolderGrid.entries,
            selected = folder?.expandedGrid ?: FolderGrid.THREE_BY_THREE,
            label = { "${it.columns} × ${it.rows}" },
            onSelect = { selected ->
                updateInfo { current ->
                    current.copy(desktopFolders = current.desktopFolders.map {
                        if (it.id == editingFolderId) it.copy(expandedGrid = selected) else it
                    })
                }
            },
            onDismiss = { showFolderGridDialog = false },
        )
    }
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text(stringResource(R.string.infoos_settings_new_folder)) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it.take(24) },
                    label = { Text(stringResource(R.string.infoos_settings_folder_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = folderName.isNotBlank(),
                    onClick = {
                        updateInfo {
                            it.copy(
                                desktopFolders = it.desktopFolders + InfoOsDesktopFolder(
                                    id = UUID.randomUUID().toString(),
                                    name = folderName.trim(),
                                )
                            )
                        }
                        folderName = ""
                        showNewFolderDialog = false
                    },
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    if (showAppearanceScheduleDialog) {
        AlertDialog(
            onDismissRequest = { showAppearanceScheduleDialog = false },
            title = { Text(stringResource(R.string.infoos_settings_appearance_schedule)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = lightTime,
                        onValueChange = { lightTime = it.take(5) },
                        label = { Text(stringResource(R.string.infoos_settings_light_time)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = darkTime,
                        onValueChange = { darkTime = it.take(5) },
                        label = { Text(stringResource(R.string.infoos_settings_dark_time)) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val light = parseMinutes(lightTime)
                    val dark = parseMinutes(darkTime)
                    if (light != null && dark != null) {
                        updateInfo { it.copy(lightAppearanceMinutes = light, darkAppearanceMinutes = dark) }
                        showAppearanceScheduleDialog = false
                    }
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showAppearanceScheduleDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun WallpaperTransformControls(
    wallpaper: InfoOsWallpaper,
    onChange: (InfoOsWallpaper) -> Unit,
) {
    Text(stringResource(R.string.infoos_settings_lock_screen), style = MaterialTheme.typography.titleSmall)
    TransformSlider(stringResource(R.string.infoos_settings_scale), wallpaper.lockScale, 1f..2.5f) {
        onChange(wallpaper.copy(lockScale = it))
    }
    TransformSlider(stringResource(R.string.infoos_settings_horizontal_position), wallpaper.lockOffsetX, -300f..300f) {
        onChange(wallpaper.copy(lockOffsetX = it))
    }
    TransformSlider(stringResource(R.string.infoos_settings_vertical_position), wallpaper.lockOffsetY, -300f..300f) {
        onChange(wallpaper.copy(lockOffsetY = it))
    }
    Text(stringResource(R.string.infoos_settings_home_screen), style = MaterialTheme.typography.titleSmall)
    TransformSlider(stringResource(R.string.infoos_settings_scale), wallpaper.homeScale, 1f..2.5f) {
        onChange(wallpaper.copy(homeScale = it))
    }
    TransformSlider(stringResource(R.string.infoos_settings_horizontal_position), wallpaper.homeOffsetX, -300f..300f) {
        onChange(wallpaper.copy(homeOffsetX = it))
    }
    TransformSlider(stringResource(R.string.infoos_settings_vertical_position), wallpaper.homeOffsetY, -300f..300f) {
        onChange(wallpaper.copy(homeOffsetY = it))
    }
}

@Composable
private fun TransformSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.bodySmall)
    Slider(value = value, onValueChange = onChange, valueRange = range)
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = {
                                onSelect(option)
                                onDismiss()
                            },
                        )
                        Text(label(option))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private fun InfoOsSettingSection.titleRes(detail: String): Int = when (this) {
    InfoOsSettingSection.ACCOUNT -> R.string.infoos_settings_account
    InfoOsSettingSection.PERSONAL_INFO -> R.string.infoos_settings_personal_info
    InfoOsSettingSection.LOGIN_SECURITY -> R.string.infoos_settings_login_security
    InfoOsSettingSection.GENERAL -> R.string.infoos_settings_general
    InfoOsSettingSection.SOFTWARE_UPDATE -> R.string.infoos_settings_software_update
    InfoOsSettingSection.AIRE_INTELLIGENCE -> R.string.infoos_settings_aire_intelligence
    InfoOsSettingSection.APPS_SERVICES -> R.string.infoos_settings_apps_services
    InfoOsSettingSection.DISPLAY_BRIGHTNESS -> R.string.infoos_settings_display_brightness
    InfoOsSettingSection.HOME_DOCK -> R.string.infoos_settings_home_dock
    InfoOsSettingSection.DESKTOP_FOLDERS -> R.string.infoos_settings_desktop_folders
    InfoOsSettingSection.WALLPAPER -> R.string.infoos_settings_wallpaper
    InfoOsSettingSection.NOTIFICATIONS -> R.string.infoos_settings_notifications
    InfoOsSettingSection.APP_NOTIFICATION -> detail.notificationTitleRes()
}

private fun String.notificationTitleRes(): Int = when (this) {
    "mailbox" -> R.string.infoos_settings_mailbox
    "settings" -> R.string.infoos_settings_settings_app
    else -> R.string.infoos_settings_chat
}

internal fun hashPasscode(passcode: String, salt: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(salt)
    return Base64.getEncoder().encodeToString(digest.digest(passcode.toByteArray()))
}

internal fun formatMinutes(value: Int): String = "%02d:%02d".format(value / 60, value % 60)

internal fun parseMinutes(value: String): Int? {
    val parts = value.split(':')
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
    if (parts.size != 2 || hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}
