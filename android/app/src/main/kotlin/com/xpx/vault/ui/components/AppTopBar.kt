package com.xpx.vault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.xpx.vault.R
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiTextSize

@Composable
fun AppTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .width(38.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(UiColors.Home.navBarBg)
                .throttledClickable(onClick = onBack),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_topbar_back),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = title,
            color = UiColors.Home.title,
            fontSize = UiTextSize.homeTitle,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.width(38.dp).height(36.dp))
    }
}
