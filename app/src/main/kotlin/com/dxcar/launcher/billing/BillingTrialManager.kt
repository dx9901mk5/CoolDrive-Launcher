package com.dxcar.launcher.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.dxcar.launcher.i18n.DxCarI18n
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BillingTrialState(
    val isProUnlocked: Boolean = false,
    val isInTrial: Boolean = true,
    val trialStartedAtMs: Long = 0L,
    val remainingTrialMs: Long = BillingTrialManager.TRIAL_DURATION_MS,
    val formattedPrice: String = "NT$500",
    val isBillingReady: Boolean = false,
    val statusMessage: String = ""
) {
    val hasFullAccess: Boolean
        get() = isProUnlocked || isInTrial

    val remainingDays: Int
        get() = (remainingTrialMs / (24L * 60L * 60L * 1000L)).toInt().coerceAtLeast(0)

    val remainingHoursRemainder: Int
        get() = ((remainingTrialMs % (24L * 60L * 60L * 1000L)) / (60L * 60L * 1000L)).toInt().coerceAtLeast(0)
}

/**
 * Google Play Billing + 7-Day Full-Feature Free Trial Manager:
 * - Full features unlocked for 7 days from first install.
 * - One-time non-consumable lifetime purchase (`dxcar_pro_lifetime`, NT$500).
 */
class BillingTrialManager(context: Context) : PurchasesUpdatedListener {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(buildInitialState())
    val state: StateFlow<BillingTrialState> = _state.asStateFlow()

    private var productDetails: ProductDetails? = null

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    private fun buildInitialState(): BillingTrialState {
        val now = System.currentTimeMillis()
        var startedAt = prefs.getLong(KEY_TRIAL_STARTED_AT, 0L)
        if (startedAt <= 0L) {
            startedAt = now
            prefs.edit().putLong(KEY_TRIAL_STARTED_AT, startedAt).apply()
        }
        val proOwned = prefs.getBoolean(KEY_PRO_OWNED_CACHE, false)
        val elapsed = (now - startedAt).coerceAtLeast(0L)
        val remaining = (TRIAL_DURATION_MS - elapsed).coerceAtLeast(0L)
        return BillingTrialState(
            isProUnlocked = proOwned,
            isInTrial = remaining > 0L,
            trialStartedAtMs = startedAt,
            remainingTrialMs = remaining
        )
    }

    fun start() {
        refreshTrialClock()
        connectBilling()
    }

    fun refreshTrialClock() {
        val now = System.currentTimeMillis()
        val cur = _state.value
        val elapsed = (now - cur.trialStartedAtMs).coerceAtLeast(0L)
        val remaining = (TRIAL_DURATION_MS - elapsed).coerceAtLeast(0L)
        _state.value = cur.copy(
            isInTrial = remaining > 0L,
            remainingTrialMs = remaining
        )
    }

    private fun connectBilling() {
        if (billingClient.isReady) {
            queryProductAndPurchases()
            return
        }
        runCatching {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        _state.value = _state.value.copy(isBillingReady = true)
                        queryProductAndPurchases()
                    }
                }

                override fun onBillingServiceDisconnected() {
                    _state.value = _state.value.copy(isBillingReady = false)
                }
            })
        }
    }

    private fun queryProductAndPurchases() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_LIFETIME_PRO)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { result, detailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val item = detailsList.firstOrNull { it.productId == PRODUCT_LIFETIME_PRO }
                if (item != null) {
                    productDetails = item
                    val price = item.oneTimePurchaseOfferDetails?.formattedPrice
                    if (!price.isNullOrBlank()) {
                        _state.value = _state.value.copy(formattedPrice = price)
                    }
                }
            }
        }

        restorePurchases(silent = true)
    }

    fun restorePurchases(silent: Boolean = false) {
        if (!billingClient.isReady) {
            if (!silent) {
                _state.value = _state.value.copy(
                    statusMessage = DxCarI18n.tr(
                        "正在連線 Google Play 商店…",
                        "正在连接 Google Play 商店…",
                        "Connecting to Google Play…",
                        "Google Play に接続中…"
                    )
                )
            }
            connectBilling()
            return
        }
        val queryParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(queryParams) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val owned = handlePurchases(purchases)
                if (!silent) {
                    _state.value = _state.value.copy(
                        statusMessage = if (owned) {
                            DxCarI18n.tr("✓ 已恢復永久買斷授權！", "✓ 已恢复永久买断授权！", "✓ Lifetime Pro restored!", "✓ 買い切りライセンスを復元しました！")
                        } else {
                            DxCarI18n.tr("尚未偵測到買斷紀錄", "尚未检测到买断记录", "No prior purchase found", "購入履歴が見つかりません")
                        }
                    )
                }
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity) {
        if (!billingClient.isReady || productDetails == null) {
            connectBilling()
            _state.value = _state.value.copy(
                statusMessage = DxCarI18n.tr(
                    "正在載入 Google Play 付款資訊，請稍後再試…",
                    "正在加载 Google Play 付款信息，请稍后再试…",
                    "Loading Google Play product info, please try again…",
                    "Google Play の商品情報を読み込み中です…"
                )
            )
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails!!)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        billingClient.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null) {
                    handlePurchases(purchases)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _state.value = _state.value.copy(
                    statusMessage = DxCarI18n.tr("已取消購買", "已取消购买", "Purchase canceled", "購入がキャンセルされました")
                )
            }
            else -> {
                _state.value = _state.value.copy(
                    statusMessage = DxCarI18n.tr(
                        "付款失敗 (${result.responseCode})",
                        "付款失败 (${result.responseCode})",
                        "Billing error (${result.responseCode})",
                        "決済エラー (${result.responseCode})"
                    )
                )
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>): Boolean {
        var unlocked = false
        for (purchase in purchases) {
            if (purchase.products.contains(PRODUCT_LIFETIME_PRO) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            ) {
                unlocked = true
                if (!purchase.isAcknowledged) {
                    val ackParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.acknowledgePurchase(ackParams) { }
                }
            }
        }
        if (unlocked) {
            prefs.edit().putBoolean(KEY_PRO_OWNED_CACHE, true).apply()
            _state.value = _state.value.copy(
                isProUnlocked = true,
                statusMessage = DxCarI18n.tr("👑 感謝支持！已永久解鎖 DX-Car Pro", "👑 感谢支持！已永久解锁 DX-Car Pro", "👑 Thank you! DX-Car Pro unlocked", "👑 ご購入ありがとうございます！永久解除されました")
            )
        }
        return unlocked
    }

    companion object {
        const val PRODUCT_LIFETIME_PRO = "dxcar_pro_lifetime"
        const val TRIAL_DURATION_MS = 7L * 24L * 60L * 60L * 1000L
        private const val PREFS_NAME = "dxcar_billing_trial"
        private const val KEY_TRIAL_STARTED_AT = "trial_started_at_ms"
        private const val KEY_PRO_OWNED_CACHE = "pro_owned_cache"
    }
}
