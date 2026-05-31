package com.xpx.vault.ui.ai

import com.xpx.vault.ai.core.SensitiveKind

internal val MetadataRiskKinds: Set<String> = setOf(
    SensitiveKind.LOCATION_METADATA.name,
    SensitiveKind.METADATA_RICH.name,
    SensitiveKind.CAMERA_INFO.name,
    SensitiveKind.CAPTURE_TIME.name,
)

internal fun isMetadataRiskKind(kind: String): Boolean = kind in MetadataRiskKinds

internal fun isLocationRiskKind(kind: String): Boolean = kind == SensitiveKind.LOCATION_METADATA.name
