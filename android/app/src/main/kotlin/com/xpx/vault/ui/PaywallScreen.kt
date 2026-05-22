package com.xpx.vault.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xpx.vault.R
import com.xpx.vault.billing.RevenueCatSubscriptionRepository
import com.xpx.vault.domain.billing.PaywallOfferingsState
import com.xpx.vault.domain.billing.PaywallPackageOffer
import com.xpx.vault.domain.billing.PaywallPlanKind
import com.xpx.vault.domain.billing.PurchaseActivityHost
import com.xpx.vault.domain.quota.PaywallTrigger
import com.xpx.vault.ui.components.AppButton
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiRadius
import com.xpx.vault.ui.theme.UiSize
import com.xpx.vault.ui.theme.UiTextSize
import kotlinx.coroutines.delay

/**
 * 会员购买页。视觉对齐 Pixso `item-id=4:449`：深色渐变、金色皇冠、蓝强调选中态与 **BEST VALUE** 徽章。
 *
 * **套餐价签、商品标题/说明**来自 RevenueCat → Play 的 [PaywallPackageOffer]；本页仅结构文案走 `strings.xml`。
 *
 * @param dismissable 为 false 时隐藏关闭按钮（硬墙模式），用户必须购买或按系统返回。
 * @param source 触发来源标识，用于埋点。
 */
@Composable
fun PaywallScreen(
    onBack: () -> Unit,
    dismissable: Boolean = true,
    source: String = "manual",
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() as? FragmentActivity }
    val offerings by viewModel.offeringsState.collectAsStateWithLifecycle()
    val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
    val purchasing by viewModel.purchasing.collectAsStateWithLifecycle()
    val surfaceError by viewModel.surfaceError.collectAsStateWithLifecycle()

    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectionTouched by rememberSaveable { mutableStateOf(false) }

    // 观察购买结果事件：成功自动关闭，恢复给 Toast 反馈
    LaunchedEffect(Unit) {
        viewModel.purchaseResult.collect { result ->
            when (result) {
                is PurchaseResult.Success -> {
                    Toast.makeText(context, context.getString(R.string.paywall_purchase_success), Toast.LENGTH_SHORT).show()
                    delay(600) // 短暂延迟让用户看到成功提示
                    onBack()
                }
                is PurchaseResult.RestoreSuccess -> {
                    if (result.isPremium) {
                        Toast.makeText(context, context.getString(R.string.paywall_restore_success), Toast.LENGTH_SHORT).show()
                        delay(600)
                        onBack()
                    } else {
                        Toast.makeText(context, context.getString(R.string.paywall_restore_no_purchase), Toast.LENGTH_SHORT).show()
                    }
                }
                is PurchaseResult.RestoreFailed -> {
                    Toast.makeText(context, context.getString(R.string.paywall_restore_failed), Toast.LENGTH_SHORT).show()
                }
                is PurchaseResult.Cancelled,
                is PurchaseResult.Failed -> { /* surfaceError 已处理 */ }
            }
        }
    }

    LaunchedEffect(offerings) {
        val ready = offerings as? PaywallOfferingsState.Ready ?: return@LaunchedEffect
        if (!selectionTouched) {
            selectedIndex = ready.defaultSelectedIndex.coerceIn(0, ready.packages.lastIndex.coerceAtLeast(0))
        }
    }

    val scroll = rememberScrollState()
    val purchaseHost = remember(activity) {
        PurchaseActivityHost { activity }
    }

    // 埋点：记录支付墙展示
    val paywallAnalytics = remember {
        activity?.let { com.xpx.vault.billing.PaywallAnalyticsProvider.get(context) }
    }
    LaunchedEffect(Unit) {
        val trigger = if (dismissable) PaywallTrigger.ONBOARDING_SOFT else PaywallTrigger.QUOTA_HARD
        paywallAnalytics?.trackPaywallShown(source, trigger)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(UiColors.Paywall.bgTop, UiColors.Paywall.bgBottom),
                ),
            )
            .safeDrawingPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 22.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (dismissable) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(UiColors.Paywall.closeFill)
                            .border(1.dp, UiColors.Paywall.closeStroke, CircleShape)
                            .clickable(onClick = {
                                paywallAnalytics?.trackPaywallDismissed(source)
                                onBack()
                            }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "×",
                            color = UiColors.Paywall.subtitle,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            PaywallHero(isPremium = isPremium)
            Spacer(Modifier.height(22.dp))

            when (val s = offerings) {
                PaywallOfferingsState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = UiColors.Paywall.priceSelected)
                    }
                }

                is PaywallOfferingsState.Error -> {
                    val keyMissing = s.message == RevenueCatSubscriptionRepository.ERROR_CODE_RC_KEY_MISSING
                    val msg = if (keyMissing) {
                        stringResource(R.string.paywall_not_configured)
                    } else {
                        s.message ?: stringResource(R.string.paywall_error_generic)
                    }
                    Text(
                        text = msg,
                        color = if (keyMissing) UiColors.Paywall.subtitle else UiColors.Paywall.error,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    AppButton(
                        text = stringResource(R.string.paywall_retry),
                        onClick = { viewModel.refresh() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is PaywallOfferingsState.Ready -> {
                    if (s.isPremium) {
                        Text(
                            text = stringResource(R.string.paywall_premium_active),
                            color = UiColors.Paywall.tierMeta,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                    s.packages.forEachIndexed { index, offer ->
                        PaywallTierCard(
                            offer = offer,
                            selected = index == selectedIndex,
                            onClick = {
                                selectionTouched = true
                                selectedIndex = index
                            },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    PaywallFeatureList()
                    Spacer(Modifier.height(22.dp))
                    surfaceError?.let { err ->
                        Text(
                            text = err,
                            color = UiColors.Paywall.error,
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    val selected = s.packages.getOrNull(selectedIndex)
                    val ctaEnabled = selected != null && activity != null && !s.isPremium
                    AppButton(
                        text = stringResource(R.string.paywall_cta_continue),
                        onClick = {
                            if (selected != null && activity != null) {
                                viewModel.clearError()
                                viewModel.purchase(purchaseHost, selected.packageIdentifier)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = ctaEnabled,
                        loading = purchasing,
                    )
                    Spacer(Modifier.height(10.dp))
                    TextButton(
                        onClick = { viewModel.restorePurchases() },
                        enabled = !purchasing && activity != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.paywall_restore),
                            color = UiColors.Paywall.subtitle,
                            fontSize = 14.sp,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.paywall_footer),
                        color = UiColors.Paywall.footer,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PaywallHero(isPremium: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(UiSize.paywallCrown)
                .border(2.dp, UiColors.Paywall.crownRing, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "👑",
                fontSize = 28.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.paywall_hero_title),
            color = UiColors.Paywall.title,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isPremium) {
                stringResource(R.string.paywall_hero_subtitle_active)
            } else {
                stringResource(R.string.paywall_hero_subtitle)
            },
            color = UiColors.Paywall.subtitle,
            fontSize = UiTextSize.homeSubtitle,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

@Composable
private fun PaywallTierCard(
    offer: PaywallPackageOffer,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val stroke = if (selected) UiColors.Paywall.cardStrokeSelected else UiColors.Paywall.cardStroke
    val strokeW = if (selected) 2.dp else 1.dp
    val priceColor = if (selected) UiColors.Paywall.priceSelected else UiColors.Paywall.priceIdle
    val title = remember(offer.title) { offer.title.compactStoreText() }
    val description = remember(offer.description) { offer.description.compactStoreText() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiRadius.paywallCard))
            .background(UiColors.Paywall.cardBg)
            .border(strokeW, stroke, RoundedCornerShape(UiRadius.paywallCard))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = UiColors.Paywall.tierTitle,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 21.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (offer.showBestValueBadge || offer.savingsPercent != null) {
                    Spacer(Modifier.height(6.dp))
                    val badgeText = if (offer.savingsPercent != null) {
                        stringResource(R.string.paywall_best_value) + " -${offer.savingsPercent}%"
                    } else {
                        stringResource(R.string.paywall_best_value)
                    }
                    Text(
                        text = badgeText,
                        color = UiColors.Paywall.badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(100))
                            .background(UiColors.Paywall.badgeBg)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                if (description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = description,
                        color = UiColors.Paywall.tierMeta,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                offer.freeTrialLabel?.let { trial ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = trial,
                        color = UiColors.Paywall.priceSelected,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.widthIn(min = 82.dp, max = 112.dp),
            ) {
                Text(
                    text = offer.pricePrimary.trim(),
                    color = priceColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = periodLabel(offer.kind),
                    color = UiColors.Paywall.tierMeta,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun periodLabel(kind: PaywallPlanKind): String =
    when (kind) {
        PaywallPlanKind.MONTHLY -> stringResource(R.string.paywall_period_monthly)
        PaywallPlanKind.ANNUAL -> stringResource(R.string.paywall_period_yearly)
        PaywallPlanKind.LIFETIME -> stringResource(R.string.paywall_period_lifetime)
        PaywallPlanKind.OTHER -> stringResource(R.string.paywall_period_other)
    }

private val storeTextWhitespaceRegex = Regex("\\s+")

private fun String.compactStoreText(): String =
    trim().replace(storeTextWhitespaceRegex, " ")

@Composable
private fun PaywallFeatureList() {
    val items = listOf(
        Triple(R.string.paywall_feat_storage, UiColors.Paywall.featureCheck, "✓"),
        Triple(R.string.paywall_feat_watermark, UiColors.Paywall.featureCheck, "✓"),
        Triple(R.string.paywall_feat_ai, UiColors.Paywall.featureCheckGold, "✓"),
        Triple(R.string.paywall_feat_backup, UiColors.Paywall.featureCheckBlue, "✓"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { (str, tint, mark) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = mark, color = tint, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(str),
                    color = UiColors.Paywall.tierTitle,
                    fontSize = 14.sp,
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    // Free vs Pro 对比表
    PaywallComparisonTable()
}

@Composable
private fun PaywallComparisonTable() {
    val comparisons = listOf(
        Triple(stringResource(R.string.paywall_compare_storage), "50", "∞"),
        Triple(stringResource(R.string.paywall_compare_ai), stringResource(R.string.paywall_compare_ai_free), "∞"),
        Triple(stringResource(R.string.paywall_compare_backup), stringResource(R.string.paywall_compare_backup_free), "∞"),
        Triple(stringResource(R.string.paywall_compare_watermark), "✗", "✓"),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(UiColors.Paywall.cardBg)
            .padding(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "",
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.paywall_plan_free),
                color = UiColors.Paywall.tierMeta,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(56.dp),
            )
            Text(
                text = stringResource(R.string.paywall_hero_title),
                color = UiColors.Paywall.priceSelected,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(56.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        comparisons.forEach { (label, free, pro) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    color = UiColors.Paywall.tierTitle,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = free,
                    color = UiColors.Paywall.tierMeta,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(56.dp),
                )
                Text(
                    text = pro,
                    color = UiColors.Paywall.priceSelected,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(56.dp),
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
