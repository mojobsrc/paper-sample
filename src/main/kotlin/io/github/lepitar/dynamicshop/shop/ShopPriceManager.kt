package io.github.lepitar.dynamicshop.shop

import io.github.lepitar.dynamicshop.plugin.DynamicShopPlugin
import io.github.lepitar.dynamicshop.plugin.DynamicShopPlugin.Companion.plugin
import io.github.lepitar.dynamicshop.shop.ShopManager.updateShopPrices
import org.bukkit.Bukkit

class ShopPriceManager {
    private val priceUpdateInterval = 10 * 20L // 시간을 틱 단위로 (1초 = 20틱)

    fun startPriceUpdateScheduler() {
        // 서버 시작 후 24시간마다 가격 업데이트 스케줄링
        Bukkit.getScheduler().runTaskTimer(
            plugin,
            Runnable { updateShopPrices() },
            priceUpdateInterval,
            priceUpdateInterval
        )
    }
}