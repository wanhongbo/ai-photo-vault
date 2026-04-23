package com.photovault.app.ui.theme

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
}

object UiSize {
    val buttonHeight: Dp = 56.dp
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
}

object UiTextSize {
    val button: TextUnit = 18.sp
    val dialogTitle: TextUnit = 22.sp
    val dialogBody: TextUnit = 15.sp
    val homeTitle: TextUnit = 24.sp
    val homeSubtitle: TextUnit = 14.sp
    val homeEmptyTitle: TextUnit = 20.sp
    val homeEmptyBody: TextUnit = 14.sp
    val homeNavLabel: TextUnit = 12.sp
    val vaultEmptyTitle: TextUnit = 22.sp
    val vaultEmptyBody: TextUnit = 15.sp
    val permissionTitle: TextUnit = 22.sp
    val permissionBody: TextUnit = 15.sp
}
