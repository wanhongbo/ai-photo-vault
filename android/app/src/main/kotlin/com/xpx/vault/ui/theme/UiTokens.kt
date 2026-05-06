package com.xpx.vault.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object UiColors {
    object Splash {
        val bgLeft = Color(0xFF0D1A2E)
        val bgRight = Color(0xFF0D0D0D)
        val glowInner = Color(0x451A3A6B)
        val glowOuter = Color(0x114A9EFF)
        val iconSurface = Color(0xFF111418)
        val iconStroke = Color(0xFF2A3848)
        val title = Color(0xFFF0F4FF)
        val tagline = Color(0xFF8A9BB0)
        val progressTrack = Color(0xFF1E2A38)
        val progressFill = Color(0xFF4A9EFF)
        val footer = Color(0xFF3A4555)
    }

    object Lock {
        val bg = Color(0xFF05080D)
        val keypadSurface = Color(0xFF131C29)
        val keypadStroke = Color(0xFF243348)
        val brandBlue = Color(0xFF4A9EFF)
        val textMain = Color(0xFFEAF1FF)
        val textSub = Color(0xFF7E90AB)
        val error = Color(0xFFFF4372)
        val success = Color(0xFF21C277)
        val errorBg = Color(0x33FF4372)
        val successHalo = Color(0x3321C277)
        val hintPanel = Color(0xFF0E1622)
    }

    object Button {
        val primaryContainer = Color(0xFF4A9EFF)
        val primaryContent = Color.White
        val secondaryContainer = Color(0xFF1A202C)
        val secondaryContent = Color(0xFFEAF1FF)
        val dangerContainer = Color(0xFF2A1820)
        val dangerContent = Color(0xFFFF6B8F)
        val disabledContainer = Color(0xFF2A3240)
        val disabledContent = Color(0xFF7788A1)
    }

    object Dialog {
        val bg = Color(0xFF101722)
        val title = Color(0xFFEAF1FF)
        val body = Color(0xFF97A8C0)
    }

    object Home {
        val bgTop = Color(0xFF0B1324)
        val bgBottom = Color(0xFF05080D)
        val sectionBg = Color(0xFF0C1523)
        val title = Color(0xFFEAF1FF)
        val subtitle = Color(0xFF8EA2C0)
        val emptyCardBg = Color(0xFF0E1624)
        val emptyCardStroke = Color(0xFF223247)
        val emptyIconBg = Color(0x1F4A9EFF)
        val emptyTitle = Color(0xFFEAF1FF)
        val emptyBody = Color(0xFF8EA2C0)
        val navBarBg = Color(0xCC0E1726)
        val navBarStroke = Color(0xFF243348)
        val navItemActiveBg = Color(0x1F4A9EFF)
        val navItemActiveStroke = Color(0xFF4A9EFF)
        val navItemIdle = Color(0xFF7E90AB)
        val navItemActive = Color(0xFFB7D7FF)
    }

    object Ai {
        val suggestCardGradientStart = Color(0xFF1A3A6E)
        val suggestCardGradientEnd = Color(0xFF0D1B2E)
        val featureCardBg = Color(0xFF16161A)
        val featureCardStroke = Color(0xFF2A2A2E)
        val featureTitle = Color(0xFFF0F4FF)
        val featureDesc = Color(0xFF6B6B70)
        val headerBtnBg = Color(0xFF1E2530)
        val headerBtnStroke = Color(0xFF2A2A2E)
        val badgeBg = Color(0x254A9EFF)
        val badgeText = Color(0xFF4A9EFF)
        val suggestTitle = Color(0xFFF0F4FF)
        val suggestDesc = Color(0xCCFFFFFF)
        val execBtnBg = Color(0xFF4A9EFF)
        val execBtnText = Color(0xFF0D0D0D)
        val skipBtnBg = Color(0x10FFFFFF)
        val skipBtnText = Color(0x99FFFFFF)
        val skipBtnStroke = Color(0x30FFFFFF)
        val iconBgWhite = Color(0x20FFFFFF)

        // —— Scanning 状态（蓝）：用默认渐变 + 进度条色 ——
        val scanningProgressFill = Color(0xFF4A9EFF)
        val scanningProgressTrack = Color(0x334A9EFF)

        // —— Sensitive 状态（红调）——
        val sensitiveGradStart = Color(0xFF6E1A3A)
        val sensitiveGradEnd = Color(0xFF0D1B2E)
        val sensitiveIconBg = Color(0x33E85A4F)
        val sensitiveBadgeBg = Color(0x33E85A4F)
        val sensitiveBadgeText = Color(0xFFFF7A6F)
        val sensitiveExecBtnBg = Color(0xFFE85A4F)
        val sensitiveExecBtnText = Color(0xFFFFFFFF)

        // —— Cleanup 状态（橙调）——
        val cleanupGradStart = Color(0xFF6E4A1A)
        val cleanupGradEnd = Color(0xFF0D1B2E)
        val cleanupIconBg = Color(0x33FFB547)
        val cleanupBadgeBg = Color(0x33FFB547)
        val cleanupBadgeText = Color(0xFFFFC87A)
        val cleanupExecBtnBg = Color(0xFFFFB547)
        val cleanupExecBtnText = Color(0xFF0D0D0D)

        // —— AllClear 状态（绿调）——
        val allClearGradStart = Color(0xFF1A6E3A)
        val allClearGradEnd = Color(0xFF0D2B1E)
        val allClearIconBg = Color(0x3332D583)
        val allClearBadgeBg = Color(0x3332D583)
        val allClearBadgeText = Color(0xFF5BE59D)
        val allClearExecBtnBg = Color(0x2232D583)
        val allClearExecBtnText = Color(0xFF5BE59D)
        val allClearExecBtnStroke = Color(0x5532D583)

        val classifyBar = Color(0xFF4A9EFF)
        val searchBar = Color(0xFF32D583)
        val blurBar = Color(0xFFE85A4F)
        val compressBar = Color(0xFFFFB547)
        val encryptBar = Color(0xFF6366F1)
        val dedupBar = Color(0xFFC850C0)

        val classifyIconBg = Color(0x204A9EFF)
        val searchIconBg = Color(0x2032D583)
        val blurIconBg = Color(0x20E85A4F)
        val compressIconBg = Color(0x20FFB547)
        val encryptIconBg = Color(0x206366F1)
        val dedupIconBg = Color(0x20C850C0)
    }
}

object UiRadius {
    val splashIcon: Dp = 22.dp
    val errorBanner: Dp = 12.dp
    val hintCard: Dp = 16.dp
    val dialog: Dp = 20.dp
    val homeCard: Dp = 20.dp
    val homeThumb: Dp = 12.dp
    val homeAlbumCard: Dp = 14.dp
    val homeNavBar: Dp = 24.dp
    val homeNavItem: Dp = 16.dp
    val vaultEmptyIconWrap: Dp = 28.dp
    val settingsRow: Dp = 12.dp
    val settingsAvatar: Dp = 14.dp
    val backupResultBadge: Dp = 28.dp
    val backupMetaCard: Dp = 14.dp
    val trashThumb: Dp = 10.dp
}

object UiSize {
    val buttonHeight: Dp = 54.dp
    val buttonHeightSecondary: Dp = 48.dp
    val loadingIndicator: Dp = 20.dp
    val homeNavBarHeight: Dp = 88.dp
    val homeNavIcon: Dp = 22.dp
    val homeEmptyIconWrap: Dp = 72.dp
    val homeEmptyIcon: Dp = 32.dp
    val homeSectionGap: Dp = 16.dp
    val homeCardPadding: Dp = 16.dp
    val homeGridGap: Dp = 8.dp
    val homeAlbumCardWidth: Dp = 124.dp
    val homeAlbumCoverHeight: Dp = 92.dp
    val homeThumbSize: Dp = 108.dp
    val vaultEmptyCardTopPad: Dp = 40.dp
    val vaultEmptyCardBottomPad: Dp = 28.dp
    val vaultEmptyIconWrap: Dp = 96.dp
    val vaultEmptyIcon: Dp = 42.dp
    val vaultEmptyTitleTopGap: Dp = 20.dp
    val vaultEmptyBodyTopGap: Dp = 12.dp
    val vaultEmptyPrimaryTopGap: Dp = 24.dp
    val vaultEmptySecondaryTopGap: Dp = 12.dp
    val vaultEmptyButtonHeight: Dp = 54.dp
    val permissionCardTopPad: Dp = 36.dp
    val permissionCardBottomPad: Dp = 28.dp
    val permissionIconWrap: Dp = 92.dp
    val permissionIcon: Dp = 42.dp
    val permissionTitleTopGap: Dp = 18.dp
    val permissionBodyTopGap: Dp = 12.dp
    val permissionPrimaryTopGap: Dp = 24.dp
    val permissionSecondaryTopGap: Dp = 10.dp
    val permissionButtonHeight: Dp = 54.dp
    val settingsScreenHorizontalPad: Dp = 16.dp
    val settingsScreenVerticalPad: Dp = 16.dp
    val settingsSubtitleTopGap: Dp = 6.dp
    val settingsListTopGap: Dp = 14.dp
    val settingsSectionGap: Dp = 12.dp
    val settingsCardPadding: Dp = 16.dp
    val settingsAvatarSize: Dp = 48.dp
    val settingsAvatarGap: Dp = 12.dp
    val settingsProfileDescTopGap: Dp = 2.dp
    val settingsGroupTitleToRowsGap: Dp = 10.dp
    val settingsRowGap: Dp = 8.dp
    val settingsRowPaddingHorizontal: Dp = 12.dp
    val settingsRowPaddingVertical: Dp = 10.dp
    val settingsDangerDescTopGap: Dp = 2.dp
    val backupScreenPadding: Dp = 16.dp
    val backupSectionGap: Dp = 12.dp
    val backupCardPadding: Dp = 16.dp
    val backupCardInnerGap: Dp = 10.dp
    val backupHeaderGap: Dp = 10.dp
    val backupSubtitleTopGap: Dp = 6.dp
    val backupCardTopGap: Dp = 14.dp
    val backupActionTopGap: Dp = 14.dp
    val backupCardBadgeWrap: Dp = 72.dp
    val backupCardBadgeGlyph: Dp = 24.dp
    val backupResultBadgeSize: Dp = 72.dp
    val backupResultBadgeGlyph: Dp = 28.dp
    val backupResultBadgeTopGap: Dp = 8.dp
    val backupResultInfoTopGap: Dp = 10.dp
    val backupResultMetaTopGap: Dp = 4.dp
    val backupMetaRowPadHorizontal: Dp = 12.dp
    val backupMetaRowPadVertical: Dp = 10.dp
    val trashThumbSize: Dp = 56.dp
    val trashThumbGlyph: Dp = 24.dp
    val trashInfoGap: Dp = 12.dp
    val trashMetaTopGap: Dp = 2.dp
    val trashRowGap: Dp = 8.dp
    val trashItemPadding: Dp = 12.dp
    val trashActionGap: Dp = 8.dp
}

object UiTextSize {
    val button: TextUnit = 16.sp
    val buttonSecondary: TextUnit = 14.sp
    val dialogTitle: TextUnit = 18.sp
    val dialogBody: TextUnit = 14.sp
    val homeTitle: TextUnit = 24.sp
    val homeSubtitle: TextUnit = 14.sp
    val homeEmptyTitle: TextUnit = 20.sp
    val homeEmptyBody: TextUnit = 14.sp
    val homeNavLabel: TextUnit = 12.sp
    val vaultEmptyTitle: TextUnit = 22.sp
    val vaultEmptyBody: TextUnit = 15.sp
    val permissionTitle: TextUnit = 22.sp
    val permissionBody: TextUnit = 15.sp
    val settingsProfileDesc: TextUnit = 12.sp
    val settingsRowDesc: TextUnit = 12.sp
    val settingsDangerDesc: TextUnit = 12.sp
    val settingsAvatar: TextUnit = 16.sp
    val backupMetaLabel: TextUnit = 12.sp
    val backupMetaValue: TextUnit = 14.sp
    val trashFileName: TextUnit = 15.sp
}
