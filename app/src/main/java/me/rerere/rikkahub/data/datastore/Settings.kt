package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.ProviderSetting
import me.rerere.asr.ASRProviderSetting
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_OCR_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_SUGGESTION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TITLE_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TRANSLATION_PROMPT
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.ui.theme.CustomTheme
import me.rerere.rikkahub.ui.theme.PresetThemes
import me.rerere.search.SearchCommonOptions
import me.rerere.search.SearchServiceOptions
import me.rerere.tts.provider.TTSProviderSetting
import kotlin.uuid.Uuid

@Serializable
data class Settings(
    @Transient
    val init: Boolean = false,
    val dynamicColor: Boolean = true,
    val themeId: String = PresetThemes[0].id,
    val customThemes: List<CustomTheme> = emptyList(),
    val developerMode: Boolean = false,
    val displaySetting: DisplaySetting = DisplaySetting(),
    val networkSetting: NetworkSetting = NetworkSetting(),
    val favoriteModels: List<Uuid> = emptyList(),
    val chatModelId: Uuid = Uuid.random(),
    val fastModelId: Uuid = Uuid.random(),
    val fastModelReasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    // 记忆整理用的模型，没设置时用快速模型
    val memoryModelId: Uuid? = null,
    val memoryModelReasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    val imageGenerationModelId: Uuid = Uuid.random(),
    val enableAutoTitle: Boolean = true,
    val titlePrompt: String = DEFAULT_TITLE_PROMPT,
    val translateModeId: Uuid = Uuid.random(),
    val translatePrompt: String = DEFAULT_TRANSLATION_PROMPT,
    val translateThinkingBudget: Int = 0,
    // 翻译页的目标语言，存 BCP 47 语言标签
    val translateTargetLanguage: String = "zh-CN",
    val enableSuggestion: Boolean = true,
    val suggestionPrompt: String = DEFAULT_SUGGESTION_PROMPT,
    // 回复结束后上下文占用达到聊天模型上下文窗口的 autoCompressThreshold 时自动压缩历史
    val enableAutoCompress: Boolean = true,
    val autoCompressThreshold: Float = 0.8f,
    val ocrModelId: Uuid = Uuid.random(),
    val ocrPrompt: String = DEFAULT_OCR_PROMPT,
    val assistantId: Uuid = DEFAULT_ASSISTANT_ID,
    val providers: List<ProviderSetting> = DEFAULT_PROVIDERS,
    val assistants: List<Assistant> = DEFAULT_ASSISTANTS,
    val assistantTags: List<Tag> = emptyList(),
    val searchServices: List<SearchServiceOptions> = listOf(SearchServiceOptions.DEFAULT),
    val searchCommonOptions: SearchCommonOptions = SearchCommonOptions(),
    val searchServiceSelected: Int = 0,
    val mcpServers: List<McpServerConfig> = emptyList(),
    val webDavConfig: WebDavConfig = WebDavConfig(),
    val s3Config: S3Config = S3Config(),
    // RemoteFileStore 上传临时素材用的桶，和备份的 s3Config 互不影响；items 字段在这里不使用
    val uploadS3Config: S3Config = S3Config(),
    val mediaGenerationProviders: List<MediaGenerationProviderSetting> = emptyList(),
    val ttsProviders: List<TTSProviderSetting> = DEFAULT_TTS_PROVIDERS,
    val selectedTTSProviderId: Uuid = DEFAULT_SYSTEM_TTS_ID,
    val defaultTTSPlaybackSpeed: Float = 1.0f,
    val asrProviders: List<ASRProviderSetting> = emptyList(),
    val selectedASRProviderId: Uuid? = null,
    val modeInjections: List<PromptInjection.ModeInjection> = DEFAULT_MODE_INJECTIONS,
    val lorebooks: List<Lorebook> = emptyList(),
    val quickMessages: List<QuickMessage> = emptyList(),
    val webServerEnabled: Boolean = false,
    val webServerPort: Int = 8080,
    val webServerJwtEnabled: Boolean = false,
    val webServerAccessPassword: String = "",
    val webServerLocalhostOnly: Boolean = false,
    val backupReminderConfig: BackupReminderConfig = BackupReminderConfig(),
    val infoOsSettings: InfoOsSettings = InfoOsSettings(),
    val sponsorAlertDismissedAt: Int = 0,
) {
    companion object {
        // 构造一个用于初始化的settings, 但它不能用于保存，防止使用初始值存储
        fun dummy() = Settings(init = true)
    }
}

@Serializable
data class InfoOsSettings(
    val decorativeAvatarUri: String = "",
    val automaticUpdates: AutomaticUpdates = AutomaticUpdates.WIFI_ONLY,
    val automaticAppearance: Boolean = false,
    val lightAppearanceMinutes: Int = 7 * 60,
    val darkAppearanceMinutes: Int = 22 * 60,
    val boldText: Boolean = false,
    val homeGrid: HomeGrid = HomeGrid.FOUR_BY_SEVEN,
    val layoutLocked: Boolean = false,
    val desktopFolders: List<InfoOsDesktopFolder> = emptyList(),
    val largeAppIcons: Boolean = false,
    val newAppPlacement: NewAppPlacement = NewAppPlacement.HOME_AND_DRAWER,
    val dockVisible: Boolean = false,
    val dockShowsAppDrawer: Boolean = true,
    val dockShowsSuggestions: Boolean = true,
    val dockScale: Float = 1f,
    val homeBadges: Boolean = true,
    val dockBadges: Boolean = true,
    val dockMaterial: DockMaterial = DockMaterial.LIQUID_GLASS,
    val dockTransparencyPercent: Int = 50,
    val dockEffectStrengthPercent: Int = 50,
    val wallpapers: List<InfoOsWallpaper> = emptyList(),
    val activeWallpaperId: String = "",
    val notifications: Map<String, InfoOsNotificationPreference> = defaultInfoOsNotifications(),
    val passcodeSalt: String = "",
    val passcodeHash: String = "",
    val fingerprintEnabled: Boolean = false,
    val requirePasscode: RequirePasscode = RequirePasscode.IMMEDIATELY,
    val requirePasscodeAfterMinutes: Int = 5,
    val lastLeftAtEpochMillis: Long = 0L,
    val dismissedUpdateVersion: String = "",
)

@Serializable
enum class AutomaticUpdates { WIFI_ONLY, MANUAL }

@Serializable
enum class HomeGrid(val columns: Int, val dockRows: Int) {
    FOUR_BY_SIX(4, 6),
    FOUR_BY_SEVEN(4, 7),
    FIVE_BY_NINE(5, 9),
}

@Serializable
enum class NewAppPlacement { DRAWER_ONLY, HOME_AND_DRAWER }

@Serializable
enum class FolderCoverSize(val columns: Int, val rows: Int) {
    ONE_BY_ONE(1, 1),
    TWO_BY_TWO(2, 2),
}

@Serializable
enum class FolderGrid(val columns: Int, val rows: Int) {
    THREE_BY_THREE(3, 3),
    THREE_BY_FOUR(3, 4),
    FOUR_BY_FOUR(4, 4),
}

@Serializable
data class InfoOsDesktopFolder(
    val id: String,
    val name: String,
    val appIds: List<String> = emptyList(),
    val coverSize: FolderCoverSize = FolderCoverSize.ONE_BY_ONE,
    val expandedGrid: FolderGrid = FolderGrid.THREE_BY_THREE,
)

@Serializable
enum class DockMaterial { FROSTED_GLASS, LIQUID_GLASS }

@Serializable
enum class RequirePasscode { IMMEDIATELY, AFTER_MINUTES }

@Serializable
data class InfoOsWallpaper(
    val id: String,
    val lockUri: String = "",
    val homeUri: String = "",
    val lockScale: Float = 1f,
    val lockOffsetX: Float = 0f,
    val lockOffsetY: Float = 0f,
    val homeScale: Float = 1f,
    val homeOffsetX: Float = 0f,
    val homeOffsetY: Float = 0f,
)

@Serializable
data class InfoOsNotificationPreference(
    val enabled: Boolean = true,
    val lockScreen: Boolean = true,
    val floating: Boolean = true,
    val badge: Boolean = true,
    val showDetails: Boolean = true,
)

private fun defaultInfoOsNotifications() = mapOf(
    "chat" to InfoOsNotificationPreference(),
    "mailbox" to InfoOsNotificationPreference(),
    "settings" to InfoOsNotificationPreference(),
)

@Serializable
data class NetworkSetting(
    val userAgent: String = "",
    val proxyUrl: String = "",
    val proxyUsername: String = "",
    val proxyPassword: String = "",
    val enableAutoRetry: Boolean = true,
)

@Serializable
enum class ChatFontFamily {
    @SerialName("default")
    DEFAULT,

    @SerialName("serif")
    SERIF,

    @SerialName("monospace")
    MONOSPACE,

    @SerialName("custom")
    CUSTOM,
}

@Serializable
enum class BackgroundEffectType {
    @SerialName("blur")
    BLUR,

    @SerialName("glass")
    GLASS,
}

@Serializable
enum class ConversationSortOrder {
    @SerialName("update_time")
    UPDATE_TIME,

    @SerialName("create_time")
    CREATE_TIME,
}

@Serializable
data class DisplaySetting(
    val userAvatar: Avatar = Avatar.Dummy,
    val userNickname: String = "",
    val showUserAvatar: Boolean = true,
    val showAssistantBubble: Boolean = false,
    val splitAssistantBubbleByParagraph: Boolean = false,
    val bubbleOpacity: Float = 1.0f,
    val showModelIcon: Boolean = true,
    val showModelName: Boolean = true,
    val showDateTimeInMessage: Boolean = false,
    val showTokenUsage: Boolean = true,
    val showThinkingContent: Boolean = true,
    val autoCloseThinking: Boolean = true,
    val updateCheckDisabledUntilEpochMillis: Long = 0L,
    val showMessageJumper: Boolean = true,
    val messageJumperOnLeft: Boolean = false,
    val fontSizeRatio: Float = 1.0f,
    val enableMessageGenerationHapticEffect: Boolean = false,
    val skipCropImage: Boolean = true,
    val enableNotificationOnMessageGeneration: Boolean = false,
    val enableLiveUpdateNotification: Boolean = false,
    val enableFloatingChatWindow: Boolean = false,
    val codeBlockAutoWrap: Boolean = false,
    val codeBlockAutoCollapse: Boolean = false,
    val showLineNumbers: Boolean = false,
    val ttsOnlyReadQuoted: Boolean = false,
    val ttsOnlyReadOutsideBrackets: Boolean = false,
    val autoPlayTTSAfterGeneration: Boolean = false,
    val voiceModeListenWhileReplying: Boolean = true,
    val pasteLongTextAsFile: Boolean = false,
    val pasteLongTextThreshold: Int = 1000,
    val sendOnEnter: Boolean = false,
    val enableAutoScroll: Boolean = true,
    val enableLatexRendering: Boolean = true,
    val enableQuoteHighlight: Boolean = false,
    val enableBlurEffect: Boolean = false,
    val backgroundEffectType: BackgroundEffectType = BackgroundEffectType.BLUR,
    val chatFontFamily: ChatFontFamily = ChatFontFamily.DEFAULT,
    val chatCustomFontPath: String = "",
    val chatCustomFontName: String = "",
    val enableVolumeKeyScroll: Boolean = false,
    val volumeKeyScrollRatio: Float = 1.0f,
    val conversationSortOrder: ConversationSortOrder = ConversationSortOrder.UPDATE_TIME,
)

@Serializable
data class WebDavConfig(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val path: String = "rikkahub_backups",
    val items: List<BackupItem> = listOf(
        BackupItem.DATABASE,
        BackupItem.FILES
    ),
) {
    @Serializable
    enum class BackupItem {
        DATABASE,
        FILES,
    }
}

@Serializable
data class BackupReminderConfig(
    val enabled: Boolean = false,
    val intervalDays: Int = 7,
    val lastBackupTime: Long = 0L,
)
