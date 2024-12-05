package io.github.lepitar.dynamicshop.shop

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.inventory.ItemStack
import java.time.LocalDateTime
import kotlin.math.exp

class ShopItem(var price: Double, val maxPrice: Double, val minPrice: Double, val itemStack: ItemStack) {
    // Sales tracking with cycle-based storage
    val totalSellAmount = mutableListOf<SalesCycle>()
    var sellAmount = 0
    var previousPrice = 0.0

    // Configuration for price cycle
    companion object {
        // Logistic function parameters for price adjustment
        const val PRICE_ADJUSTMENT_K = 0.24
        const val PRICE_ADJUSTMENT_MAX_OUTPUT = 20.0
    }

    // Represents sales data for a specific cycle
    data class SalesCycle(
        val cycle: Int,
        val timestamp: LocalDateTime,
        val salesAmount: Double
    )

    // Record sales for the current cycle
    fun recordSales(salesAmount: Double, currentCycle: Int) {
        totalSellAmount.add(SalesCycle(currentCycle, LocalDateTime.now(), salesAmount))
    }

    // Dynamic price change method
    fun changePrice(currentCycle: Int): Double {
        if (totalSellAmount.size < 2) return price

        val previousCycleIndex = totalSellAmount.indexOfLast { it.cycle == currentCycle - 1 }
        if (previousCycleIndex == -1) return price

        val yesterdaySales = totalSellAmount[previousCycleIndex].salesAmount
        val todaySales = totalSellAmount.last().salesAmount

        val salesRatio = when {
            yesterdaySales == 0.0 -> {
                if (todaySales > 0.0) {
                    (1 - todaySales) / 1
                } else {
                    Math.random() * 0.23
                }
            }
            yesterdaySales > 0 -> (yesterdaySales - todaySales) / yesterdaySales
            else -> 0.0
        }

        val scaledX = (PRICE_ADJUSTMENT_MAX_OUTPUT / (1 + exp(-PRICE_ADJUSTMENT_K * salesRatio))) - 10

        val newPrice = price + price * (scaledX / 100)

        return newPrice.coerceIn(minPrice, maxPrice)
    }

    private fun toItemStack(): ItemStack {
        var valueTag =
            Component.text("가격: ", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("${price.toInt()}", NamedTextColor.YELLOW))

        if (previousPrice > price) {
            valueTag = valueTag.append(
                Component.text("(", NamedTextColor.GRAY)
                    .append(Component.text("▼", NamedTextColor.BLUE)
                    .append(Component.text(")", NamedTextColor.GRAY)
                )))
        } else if (previousPrice == price) {
            valueTag = valueTag.append(Component.text("(-)", NamedTextColor.GRAY))
        } else if (previousPrice < price) {
            valueTag = valueTag.append(
                Component.text("(", NamedTextColor.GRAY)
                    .append(Component.text("▲", NamedTextColor.RED)
                    .append(Component.text(")", NamedTextColor.GRAY)
                )))
        }

        val lore = mutableListOf<Component>(
            Component.text(""),
            valueTag
        )
        return itemStack.clone().apply { lore(lore) }
    }

    fun getItem(): GuiItem {
        return GuiItem(toItemStack())
    }
}