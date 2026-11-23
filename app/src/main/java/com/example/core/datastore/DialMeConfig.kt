package com.example.core.datastore

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED
}

@Serializable
enum class DialpadButtonShape {
    CIRCLE,
    SQUIRCLE,
    ROUNDED_RECT,
    PILL
}

@Serializable
enum class DialpadLayoutStyle {
    COMPACT,
    COMFORTABLE,
    ONE_HANDED_RIGHT,
    ONE_HANDED_LEFT
}

@Serializable
enum class ContactViewMode {
    LIST,
    GRID
}

@Serializable
enum class FontFamilyOption {
    PLUS_JAKARTA,
    SPACE_GROTESK,
    JETBRAINS_MONO,
    SYSTEM
}

@Serializable
enum class IconStyleOption {
    ROUNDED,
    FILLED,
    OUTLINED
}

@Serializable
enum class CallerScreenThemeStyle {
    AURORA_GRADIENT,
    FULL_PHOTO,
    MINIMAL_GLASS,
    CLASSIC_SOLID
}

@Serializable
enum class CallerAnswerStyle {
    SWIPE_PILL,
    DUAL_BUTTONS
}

@Serializable
enum class CallerNamePosition {
    TOP,
    CENTER
}

@Serializable
enum class SimSelectionMode {
    ASK_EVERY_TIME,
    SIM_1,
    SIM_2
}

@Serializable
enum class MainTabId {
    FAVORITES,
    CONTACTS,
    RECENTS,
    DIALPAD,
    SETTINGS
}

@Serializable
data class DialMeConfig(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val useDynamicColor: Boolean = false,
    val accentHex: String = "#14B8A6",
    val secondaryAccentHex: String = "#3B82F6",
    val useGradientBackground: Boolean = true,
    val customBackgroundPath: String? = null,
    val cardTransparencyAlpha: Float = 0.88f,

    val navTabOrder: List<MainTabId> = listOf(
        MainTabId.FAVORITES,
        MainTabId.RECENTS,
        MainTabId.DIALPAD,
        MainTabId.CONTACTS,
        MainTabId.SETTINGS
    ),
    val startTab: MainTabId = MainTabId.DIALPAD,

    val dialpadLayoutStyle: DialpadLayoutStyle = DialpadLayoutStyle.COMFORTABLE,
    val dialpadButtonShape: DialpadButtonShape = DialpadButtonShape.SQUIRCLE,
    val dialpadButtonScale: Float = 1.0f,
    val dialpadCornerRadiusDp: Int = 22,
    val dialpadSpacingDp: Int = 10,
    val dialpadKeySounds: Boolean = true,
    val dialpadHaptics: Boolean = true,
    val clipboardAutoDetect: Boolean = true,

    val contactViewMode: ContactViewMode = ContactViewMode.LIST,
    val fontFamily: FontFamilyOption = FontFamilyOption.PLUS_JAKARTA,
    val fontScale: Float = 1.0f,
    val iconStyle: IconStyleOption = IconStyleOption.ROUNDED,
    val animationSpeed: Float = 1.0f,

    val callerScreenTheme: CallerScreenThemeStyle = CallerScreenThemeStyle.AURORA_GRADIENT,
    val callerAnswerStyle: CallerAnswerStyle = CallerAnswerStyle.SWIPE_PILL,
    val callerNamePosition: CallerNamePosition = CallerNamePosition.TOP,
    val callerButtonRadiusDp: Int = 36,

    val defaultSimMode: SimSelectionMode = SimSelectionMode.ASK_EVERY_TIME,
    val preferredSimAccountId: String? = null,
    val countryCodeOverride: String = "",

    val privacyModeEnabled: Boolean = false,
    val appLockEnabled: Boolean = false,
    val languageTag: String = "system"
)
