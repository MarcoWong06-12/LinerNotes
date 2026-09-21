package com.linernotes.app.core.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/**
 * 支持的应用界面语言
 */
enum class AppLanguage(val code: String, val displayName: String) {
    SYSTEM("system", "跟随系统"),
    ZH_CN("zh-CN", "简体中文"),
    ZH_TW("zh-TW", "繁體中文"),
    EN("en", "English"),
    JA("ja", "日本語");

    companion object {
        fun fromCode(code: String): AppLanguage =
            entries.find { it.code.equals(code, ignoreCase = true) } ?: SYSTEM
    }
}

/**
 * 歌词翻译目标语言
 */
enum class TranslationTargetLanguage(
    val code: String,
    val displayName: String,
    val promptName: String,
    val fallbackIso: String
) {
    ZH_CN("zh-CN", "简体中文", "中文（简体）", "zh-CN"),
    ZH_TW("zh-TW", "繁體中文", "中文（繁體）", "zh-TW"),
    EN("en", "English", "English", "en"),
    JA("ja", "日本語", "日本語", "ja"),
    KO("ko", "한국어", "한국어", "ko"),
    FR("fr", "Français", "Français", "fr"),
    ES("es", "Español", "Español", "es"),
    DE("de", "Deutsch", "Deutsch", "de");

    companion object {
        fun fromCode(code: String): TranslationTargetLanguage =
            entries.find { it.code.equals(code, ignoreCase = true) } ?: ZH_CN
    }
}

/**
 * 应用全量多语言文本字典
 */
data class AppStrings(
    // 顶部与通用导航
    val appName: String,
    val shelfTitle: String,
    val searchPlaceholder: String,
    val closeSearch: String,
    val searchCd: String,
    val settingsTitle: String,
    val addAlbumTooltip: String,
    val back: String,

    // 空唱片架
    val emptyShelfTitle: String,
    val emptyShelfSubtitle: String,

    // 唱片卡片
    val viewBooklet: String,
    val removeFromShelf: String,

    // 歌词内页
    val modeBilingual: String,
    val modeOriginal: String,
    val modeTranslated: String,
    val aiTranslateAction: String,
    val translatingStatus: String,
    val editLyricsAction: String,
    val prevTrack: String,
    val nextTrack: String,
    val noLyrics: String,
    val noTranslationYet: String,
    val clickToTranslate: String,

    // 校对与编辑内页
    val editSheetTitle: String,
    val editTrackTranslatedTitleLabel: String,
    val editTrackOriginalLyricsLabel: String,
    val editTrackTranslatedLyricsLabel: String,
    val cancel: String,
    val save: String,
    val saveChanges: String,

    // 入库唱片
    val addAlbumTitle: String,
    val tabOnlineSearch: String,
    val tabManualEntry: String,
    val searchAlbumLabel: String,
    val searchAlbumPlaceholder: String,
    val searchingOnline: String,
    val searchNotFound: String,
    val albumCover: String,
    val pickFromGallery: String,
    val basicInfoSection: String,
    val albumTitleLabel: String,
    val albumTranslatedTitleLabel: String,
    val artistLabel: String,
    val releaseYearLabel: String,
    val trackListSection: String,
    val addTrackBtn: String,
    val saveAlbumBtn: String,
    val trackNumberPlaceholder: String,
    val trackTitlePlaceholder: String,

    // 设置与 AI 配置弹窗
    val settingsDialogTitle: String,
    val sectionLocalization: String,
    val appLanguageLabel: String,
    val targetLanguageLabel: String,
    val sectionAiEngine: String,
    val aiEngineDesc: String,
    val presetsTitle: String,
    val presetKuaiai: String,
    val presetGemini36: String,
    val presetGemini38: String,
    val presetDeepSeek: String,
    val presetOpenAi: String,
    val apiKeyLabel: String,
    val apiKeyPlaceholder: String,
    val baseUrlLabel: String,
    val baseUrlPlaceholder: String,
    val modelLabel: String,
    val modelPlaceholder: String,
    val testConnectionBtn: String,
    val testingStatus: String,
    val diagnosticLogsBtn: String,
    val hideLogsBtn: String,
    val logsTitle: String,
    val copyAll: String,
    val clearLogs: String,
    val noLogsYet: String,
    val saveConfigBtn: String
)

val ZhHansStrings = AppStrings(
    appName = "LinerNotes",
    shelfTitle = "LinerNotes 唱片架",
    searchPlaceholder = "搜索唱片名、译名、艺术家...",
    closeSearch = "关闭搜索",
    searchCd = "搜索唱片",
    settingsTitle = "偏好设置与 AI 引擎",
    addAlbumTooltip = "入库新唱片",
    back = "返回",

    emptyShelfTitle = "唱片架空空如也",
    emptyShelfSubtitle = "点击右上角「+」收纳你的第一张实体 CD\n翻开属于你的数字化双语内页",

    viewBooklet = "翻阅歌词内页 (Booklet)",
    removeFromShelf = "从唱片架移出",

    modeBilingual = "双语",
    modeOriginal = "原文",
    modeTranslated = "译文",
    aiTranslateAction = "AI 逐行推敲翻译",
    translatingStatus = "AI 正在推敲中...",
    editLyricsAction = "校对内页",
    prevTrack = "上一首",
    nextTrack = "下一首",
    noLyrics = "暂无歌词内容",
    noTranslationYet = "暂无译文",
    clickToTranslate = "点击右上角 ✨ 由 AI 逐行优美翻译",

    editSheetTitle = "校对曲目内页数据",
    editTrackTranslatedTitleLabel = "曲名译名",
    editTrackOriginalLyricsLabel = "原语种歌词 (逐行)",
    editTrackTranslatedLyricsLabel = "翻译歌词 (逐行对应)",
    cancel = "取消",
    save = "保存",
    saveChanges = "保存本地修改",

    addAlbumTitle = "收纳新唱片入库",
    tabOnlineSearch = "联网精准抓取",
    tabManualEntry = "手动录入档案",
    searchAlbumLabel = "搜索唱片 (如: Abbey Road / 周杰伦 / 1989)",
    searchAlbumPlaceholder = "输入专辑名或艺术家搜索",
    searchingOnline = "正在联网抓取高清封面、曲目与年份...",
    searchNotFound = "未搜索到对应专辑，请尝试更精确的名称",
    albumCover = "唱片封面",
    pickFromGallery = "选择相册封面",
    basicInfoSection = "基本档案",
    albumTitleLabel = "专辑原名 *",
    albumTranslatedTitleLabel = "专辑中文译名 (可选)",
    artistLabel = "艺术家 / 乐队 *",
    releaseYearLabel = "发行年份 (如: 1969)",
    trackListSection = "曲目列表",
    addTrackBtn = "添加单曲",
    saveAlbumBtn = "确认收纳至唱片架",
    trackNumberPlaceholder = "序号",
    trackTitlePlaceholder = "单曲英文原名",

    settingsDialogTitle = "偏好设置与 AI 引擎",
    sectionLocalization = "🌐 语言与本地化",
    appLanguageLabel = "应用界面语言",
    targetLanguageLabel = "歌词翻译目标语言",
    sectionAiEngine = "⚡ AI 翻译引擎配置",
    aiEngineDesc = "支持中转站 API、Google Gemini、DeepSeek、OpenAI 等所有兼容格式。",
    presetsTitle = "常用引擎快速预设：",
    presetKuaiai = "快爱 API (中转推荐)",
    presetGemini36 = "Gemini 3.6 (官方推荐)",
    presetGemini38 = "Gemini 3.8 (尝鲜)",
    presetDeepSeek = "DeepSeek (免翻)",
    presetOpenAi = "OpenAI",
    apiKeyLabel = "API Key",
    apiKeyPlaceholder = "粘贴 API Key (如 sk-... 或 AQ...)",
    baseUrlLabel = "API 接口地址 (Base URL)",
    baseUrlPlaceholder = "https://www.kuaiaiapi.com/v1",
    modelLabel = "模型名称 (Model)",
    modelPlaceholder = "gpt-5.6-terra / gemini-3.6-flash",
    testConnectionBtn = "测试连通性",
    testingStatus = "正在连接测试...",
    diagnosticLogsBtn = "诊断日志",
    hideLogsBtn = "隐藏日志",
    logsTitle = "实时网络与调用诊断日志",
    copyAll = "复制全部",
    clearLogs = "清空",
    noLogsYet = "暂无日志记录，可点击上方「测试连通性」发起一次网络检测",
    saveConfigBtn = "保存设置"
)

val ZhHantStrings = AppStrings(
    appName = "LinerNotes",
    shelfTitle = "LinerNotes 唱片架",
    searchPlaceholder = "搜尋唱片名、譯名、藝人...",
    closeSearch = "關閉搜尋",
    searchCd = "搜尋唱片",
    settingsTitle = "偏好設定與 AI 引擎",
    addAlbumTooltip = "入庫新唱片",
    back = "返回",

    emptyShelfTitle = "唱片架空空如也",
    emptyShelfSubtitle = "點擊右上角「+」收納你的第一張實體 CD\n翻開屬於你的數位化雙語內頁",

    viewBooklet = "翻閱歌詞內頁 (Booklet)",
    removeFromShelf = "從唱片架移出",

    modeBilingual = "雙語",
    modeOriginal = "原文",
    modeTranslated = "譯文",
    aiTranslateAction = "AI 逐行推敲翻譯",
    translatingStatus = "AI 正在推敲中...",
    editLyricsAction = "校對內頁",
    prevTrack = "上一首",
    nextTrack = "下一首",
    noLyrics = "暫無歌詞內容",
    noTranslationYet = "暫無譯文",
    clickToTranslate = "點擊右上角 ✨ 由 AI 逐行優美翻譯",

    editSheetTitle = "校對曲目內頁資料",
    editTrackTranslatedTitleLabel = "曲名譯名",
    editTrackOriginalLyricsLabel = "原語種歌詞 (逐行)",
    editTrackTranslatedLyricsLabel = "翻譯歌詞 (逐行對應)",
    cancel = "取消",
    save = "儲存",
    saveChanges = "儲存本地修改",

    addAlbumTitle = "收納新唱片入庫",
    tabOnlineSearch = "聯網精準抓取",
    tabManualEntry = "手動錄入檔案",
    searchAlbumLabel = "搜尋唱片 (如: Abbey Road / 周杰倫 / 1989)",
    searchAlbumPlaceholder = "輸入專輯名或藝人搜尋",
    searchingOnline = "正在聯網抓取高畫質封面、曲目與年份...",
    searchNotFound = "未搜尋到對應專輯，請嘗試更精確的名稱",
    albumCover = "唱片封面",
    pickFromGallery = "選擇相簿封面",
    basicInfoSection = "基本檔案",
    albumTitleLabel = "專輯原名 *",
    albumTranslatedTitleLabel = "專輯繁體譯名 (可選)",
    artistLabel = "藝人 / 樂團 *",
    releaseYearLabel = "發行年份 (如: 1969)",
    trackListSection = "曲目列表",
    addTrackBtn = "新增單曲",
    saveAlbumBtn = "確認收納至唱片架",
    trackNumberPlaceholder = "序號",
    trackTitlePlaceholder = "單曲英文原名",

    settingsDialogTitle = "偏好設定與 AI 引擎",
    sectionLocalization = "🌐 語言與在地化",
    appLanguageLabel = "應用程式介面語言",
    targetLanguageLabel = "歌詞翻譯目標語言",
    sectionAiEngine = "⚡ AI 翻譯引擎設定",
    aiEngineDesc = "支援中轉站 API、Google Gemini、DeepSeek、OpenAI 等所有相容格式。",
    presetsTitle = "常用引擎快速預設：",
    presetKuaiai = "快愛 API (中轉推薦)",
    presetGemini36 = "Gemini 3.6 (官方推薦)",
    presetGemini38 = "Gemini 3.8 (嘗鮮)",
    presetDeepSeek = "DeepSeek (免翻)",
    presetOpenAi = "OpenAI",
    apiKeyLabel = "API Key",
    apiKeyPlaceholder = "貼上 API Key (如 sk-... 或 AQ...)",
    baseUrlLabel = "API 介面位址 (Base URL)",
    baseUrlPlaceholder = "https://www.kuaiaiapi.com/v1",
    modelLabel = "模型名稱 (Model)",
    modelPlaceholder = "gpt-5.6-terra / gemini-3.6-flash",
    testConnectionBtn = "測試連通性",
    testingStatus = "正在連線測試...",
    diagnosticLogsBtn = "診斷日誌",
    hideLogsBtn = "隱藏日誌",
    logsTitle = "即時網路與調用診斷日誌",
    copyAll = "複製全部",
    clearLogs = "清空",
    noLogsYet = "暫無日誌記錄，可點擊上方「測試連通性」發起一次網路檢測",
    saveConfigBtn = "儲存設定"
)

val EnStrings = AppStrings(
    appName = "LinerNotes",
    shelfTitle = "LinerNotes Shelf",
    searchPlaceholder = "Search albums, translations, artists...",
    closeSearch = "Close Search",
    searchCd = "Search CDs",
    settingsTitle = "Preferences & AI Engine",
    addAlbumTooltip = "Add CD Album",
    back = "Back",

    emptyShelfTitle = "Your Shelf is Empty",
    emptyShelfSubtitle = "Tap \"+\" in the top right to add your first physical CD\nand explore your digital bilingual liner notes",

    viewBooklet = "Open Liner Notes Booklet",
    removeFromShelf = "Remove from Shelf",

    modeBilingual = "Bilingual",
    modeOriginal = "Original",
    modeTranslated = "Translated",
    aiTranslateAction = "AI Line-by-Line Translation",
    translatingStatus = "AI is translating...",
    editLyricsAction = "Edit Lyrics",
    prevTrack = "Previous",
    nextTrack = "Next",
    noLyrics = "No lyrics available",
    noTranslationYet = "No translation yet",
    clickToTranslate = "Tap ✨ in the top bar to translate line by line with AI",

    editSheetTitle = "Edit Track & Liner Notes",
    editTrackTranslatedTitleLabel = "Translated Title",
    editTrackOriginalLyricsLabel = "Original Lyrics (line-by-line)",
    editTrackTranslatedLyricsLabel = "Translated Lyrics (line-by-line)",
    cancel = "Cancel",
    save = "Save",
    saveChanges = "Save Changes",

    addAlbumTitle = "Add CD to Collection",
    tabOnlineSearch = "Online Search",
    tabManualEntry = "Manual Entry",
    searchAlbumLabel = "Search Album (e.g. Abbey Road / Taylor Swift)",
    searchAlbumPlaceholder = "Enter album or artist name",
    searchingOnline = "Fetching HD cover, tracklist & metadata...",
    searchNotFound = "Album not found. Please try a more specific search.",
    albumCover = "Album Cover",
    pickFromGallery = "Choose from Photos",
    basicInfoSection = "Album Details",
    albumTitleLabel = "Album Title *",
    albumTranslatedTitleLabel = "Translated Title (Optional)",
    artistLabel = "Artist / Band *",
    releaseYearLabel = "Release Year (e.g. 1969)",
    trackListSection = "Tracklist",
    addTrackBtn = "Add Track",
    saveAlbumBtn = "Save to CD Shelf",
    trackNumberPlaceholder = "No.",
    trackTitlePlaceholder = "Track Title",

    settingsDialogTitle = "Preferences & AI Engine",
    sectionLocalization = "🌐 Language & Localization",
    appLanguageLabel = "App Interface Language",
    targetLanguageLabel = "Lyric Translation Target Language",
    sectionAiEngine = "⚡ AI Translation Engine",
    aiEngineDesc = "Supports Relay API endpoints, Google Gemini, DeepSeek, OpenAI & compatible formats.",
    presetsTitle = "Quick Presets:",
    presetKuaiai = "KuaiAi Relay (Recommended)",
    presetGemini36 = "Gemini 3.6 (Official)",
    presetGemini38 = "Gemini 3.8 (Preview)",
    presetDeepSeek = "DeepSeek",
    presetOpenAi = "OpenAI",
    apiKeyLabel = "API Key",
    apiKeyPlaceholder = "Paste API Key (sk-... or AQ...)",
    baseUrlLabel = "API Base URL",
    baseUrlPlaceholder = "https://www.kuaiaiapi.com/v1",
    modelLabel = "Model Name",
    modelPlaceholder = "gpt-5.6-terra / gemini-3.6-flash",
    testConnectionBtn = "Test Connection",
    testingStatus = "Connecting & testing...",
    diagnosticLogsBtn = "Diagnostics",
    hideLogsBtn = "Hide Logs",
    logsTitle = "Real-time Network Diagnostics",
    copyAll = "Copy All",
    clearLogs = "Clear",
    noLogsYet = "No logs yet. Tap \"Test Connection\" to diagnose.",
    saveConfigBtn = "Save Settings"
)

val JaStrings = AppStrings(
    appName = "LinerNotes",
    shelfTitle = "LinerNotes CDシェルフ",
    searchPlaceholder = "アルバム名、訳題、アーティストを検索...",
    closeSearch = "検索を閉じる",
    searchCd = "CDを検索",
    settingsTitle = "設定 & AIエンジン",
    addAlbumTooltip = "新しいCDを追加",
    back = "戻る",

    emptyShelfTitle = "CDシェルフは空です",
    emptyShelfSubtitle = "右上の「+」をタップして最初のCDを追加し、\n美しいデジタル歌詞ライナーノーツを開きましょう",

    viewBooklet = "ライナーノーツを開く (Booklet)",
    removeFromShelf = "シェルフから削除",

    modeBilingual = "対訳",
    modeOriginal = "原語",
    modeTranslated = "訳詞",
    aiTranslateAction = "AI一行対訳",
    translatingStatus = "AIが翻訳中...",
    editLyricsAction = "歌詞を編集",
    prevTrack = "前の曲",
    nextTrack = "次の曲",
    noLyrics = "歌詞がありません",
    noTranslationYet = "訳詞がありません",
    clickToTranslate = "右上の ✨ をタップしてAIで翻訳",

    editSheetTitle = "トラックと歌詞の編集",
    editTrackTranslatedTitleLabel = "邦題・訳題",
    editTrackOriginalLyricsLabel = "原語歌詞 (一行ずつ)",
    editTrackTranslatedLyricsLabel = "翻訳歌詞 (一行ずつ対応)",
    cancel = "キャンセル",
    save = "保存",
    saveChanges = "変更を保存",

    addAlbumTitle = "CDをアーカイブ",
    tabOnlineSearch = "オンライン検索",
    tabManualEntry = "手動入力",
    searchAlbumLabel = "アルバム検索 (例: Abbey Road / 宇多田ヒカル)",
    searchAlbumPlaceholder = "アルバム名またはアーティストを入力",
    searchingOnline = "ジャケット写真・曲目・リリース年を取得中...",
    searchNotFound = "アルバムが見つかりませんでした。別のキーワードをお試しください。",
    albumCover = "ジャケット写真",
    pickFromGallery = "写真から選択",
    basicInfoSection = "アルバム基本情報",
    albumTitleLabel = "アルバム原題 *",
    albumTranslatedTitleLabel = "アルバム邦題・訳題 (任意)",
    artistLabel = "アーティスト / バンド *",
    releaseYearLabel = "リリース年 (例: 1969)",
    trackListSection = "収録曲リスト",
    addTrackBtn = "曲を追加",
    saveAlbumBtn = "シェルフに保存",
    trackNumberPlaceholder = "曲番",
    trackTitlePlaceholder = "曲名",

    settingsDialogTitle = "設定 & AIエンジン",
    sectionLocalization = "🌐 言語とローカライゼーション",
    appLanguageLabel = "アプリの表示言語",
    targetLanguageLabel = "歌詞翻訳のターゲット言語",
    sectionAiEngine = "⚡ AI翻訳エンジン設定",
    aiEngineDesc = "リレーAPI、Google Gemini、DeepSeek、OpenAIなどの各形式に対応。",
    presetsTitle = "クイックプリセット：",
    presetKuaiai = "KuaiAi Relay (推奨)",
    presetGemini36 = "Gemini 3.6 (公式推奨)",
    presetGemini38 = "Gemini 3.8 (プレビュー)",
    presetDeepSeek = "DeepSeek",
    presetOpenAi = "OpenAI",
    apiKeyLabel = "API Key",
    apiKeyPlaceholder = "APIキーを入力 (sk-... または AQ...)",
    baseUrlLabel = "API Base URL",
    baseUrlPlaceholder = "https://www.kuaiaiapi.com/v1",
    modelLabel = "モデル名 (Model)",
    modelPlaceholder = "gpt-5.6-terra / gemini-3.6-flash",
    testConnectionBtn = "接続テスト",
    testingStatus = "接続確認中...",
    diagnosticLogsBtn = "診断ログ",
    hideLogsBtn = "ログを非表示",
    logsTitle = "リアルタイム通信診断ログ",
    copyAll = "すべてコピー",
    clearLogs = "クリア",
    noLogsYet = "ログはありません。「接続テスト」をタップして診断を開始してください。",
    saveConfigBtn = "設定を保存"
)

val LocalStrings = staticCompositionLocalOf { ZhHansStrings }

/**
 * 根据语言设置与系统 Locale 解析 AppStrings
 */
fun resolveAppStrings(languageCode: String, systemLocale: Locale = Locale.getDefault()): AppStrings {
    val lang = if (languageCode == AppLanguage.SYSTEM.code || languageCode.isBlank()) {
        val langTag = systemLocale.language.lowercase()
        val scriptOrCountry = systemLocale.country.uppercase()
        when {
            langTag == "zh" && (scriptOrCountry == "TW" || scriptOrCountry == "HK" || scriptOrCountry == "MO") -> AppLanguage.ZH_TW
            langTag == "zh" -> AppLanguage.ZH_CN
            langTag == "ja" -> AppLanguage.JA
            langTag == "en" -> AppLanguage.EN
            else -> AppLanguage.EN
        }
    } else {
        AppLanguage.fromCode(languageCode)
    }

    return when (lang) {
        AppLanguage.ZH_CN -> ZhHansStrings
        AppLanguage.ZH_TW -> ZhHantStrings
        AppLanguage.EN -> EnStrings
        AppLanguage.JA -> JaStrings
        AppLanguage.SYSTEM -> ZhHansStrings
    }
}
