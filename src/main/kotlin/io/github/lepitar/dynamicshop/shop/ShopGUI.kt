package io.github.lepitar.dynamicshop.shop

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.Orientable.Orientation
import com.github.stefvanschie.inventoryframework.pane.OutlinePane
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane
import com.github.stefvanschie.inventoryframework.pane.Pane
import com.github.stefvanschie.inventoryframework.pane.Pane.Priority
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.lepitar.dynamicshop.plugin.DynamicShopPlugin
import io.github.lepitar.dynamicshop.plugin.DynamicShopPlugin.Companion.plugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import java.io.File
import java.time.LocalDateTime
import java.util.*
import java.util.function.BiPredicate
import java.util.function.Consumer
import java.util.function.IntUnaryOperator
import kotlin.math.round


class ShopGUI private constructor(builder: Builder) : ChestGui(6, DynamicShopPlugin.shopSellName) {
    private val sellItemList: Deque<ShopItem> = builder.sellItemList
    private val buyItemList: Deque<ShopItem> = builder.buyItemList
    private val player: Player = builder.player
    private var sellPane: PaginatedPane? = null
    private var buyPane: PaginatedPane? = null
    private var controlPane: Pane? = null
    private var mode = ShopMode.SELL
    private val econ = DynamicShopPlugin.econ

    init {
        setOnTopClick { it.isCancelled = true }
        initializePanes()
        update()
    }

    private fun initializePanes() {
        sellPane = createSellItemPage()
        buyPane = createBuyItemPage()
        controlPane = createControlPane()
        addPane(sellPane!!)
        addPane(controlPane!!)
    }

    private fun createSellItemPage(): PaginatedPane {
        val pane = PaginatedPane(0, 0, 9, 6, Priority.LOWEST)

        val pagesAmount = (sellItemList.size + ITEMS_PER_PAGE -1) / ITEMS_PER_PAGE

        repeat(pagesAmount) {
            pane.addPane(it, createPage(sellItemList))
        }
        if (sellItemList.size != 0) pane.page = 0
        return pane
    }

    private fun createBuyItemPage(): PaginatedPane {
        val pane = PaginatedPane(0, 0, 9, 6, Priority.LOWEST)

        val pagesAmount = (buyItemList.size + ITEMS_PER_PAGE -1) / ITEMS_PER_PAGE

        repeat(pagesAmount) {
            pane.addPane(it, createPage(buyItemList))
        }
        if (buyItemList.size != 0) pane.page = 0
        return pane
    }

    private fun createPage(items: Deque<ShopItem>): Pane {
        val page = OutlinePane(0, 2, 9, 4, Priority.LOWEST)
        page.orientation = Orientation.HORIZONTAL

        items.take(ITEMS_PER_PAGE).forEach { item ->
            page.addItem(item.getItem().apply {
                setAction { e -> setShopItemAction(e, item) }
            })
        }
        return page
    }

    private fun setShopItemAction(e: InventoryClickEvent, item: ShopItem) {
        val quantity = when (e.click) {
            ClickType.LEFT -> 1
            ClickType.RIGHT -> 16
            ClickType.SHIFT_LEFT -> 64
            ClickType.SHIFT_RIGHT -> calculateMaxQuantity(item)
            else -> return
        }

        if (quantity <= 0) {
            player.sendMessage("처리할 수 있는 수량이 없습니다.")
            return
        }

        when (mode) {
            ShopMode.SELL -> processSellAction(item, quantity)
            else -> processBuyAction(item, quantity)
        }
    }

    private fun calculateMaxQuantity(item: ShopItem): Int = when (mode) {
        ShopMode.SELL -> {
            val contents = player.inventory.contents
            var total = 0
            contents.forEach { stack ->
                if (stack != null && stack.isSimilar(item.itemStack)) {
                    total += stack.amount
                }
            }
            total
        }
        else -> (econ.getBalance(player) / item.price).toInt()
    }

    private fun processSellAction(item: ShopItem, quantity: Int) {
        if (!player.inventory.containsAtLeast(item.itemStack, quantity)) {
            player.sendMessage("판매할 아이템이 부족합니다.")
            return
        }

        item.sellAmount += quantity
        player.inventory.removeItem(item.itemStack.asQuantity(quantity))
        val totalPrice = item.price * quantity
        econ.depositPlayer(player, totalPrice)
        player.sendMessage("아이템 ${quantity}개를 판매하여 ${totalPrice}를 획득했습니다.")
    }

    private fun processBuyAction(item: ShopItem, quantity: Int) {
        val totalPrice = item.price * quantity
        if (econ.getBalance(player) < totalPrice) {
            player.sendMessage("잔액이 부족합니다.")
            return
        }

        econ.withdrawPlayer(player, totalPrice)
        player.inventory.addItem(item.itemStack.asQuantity(quantity))
        player.sendMessage("아이템 ${quantity}개를 구매하였습니다.")
    }

    private fun createControlPane(): Pane {
        val pane = StaticPane(0, 0, 9, 2, Priority.LOW)

        val closeButton = GuiItem(ItemStack(Material.PAPER).apply {
            editMeta {
                it.displayName(Component.text("X").decoration(TextDecoration.ITALIC, false))
                it.setCustomModelData(17741)
            }
        }) { e ->
            e.inventory.close()
        }
        pane.addItem(closeButton, 0, 0)

        val sellModeButton = GuiItem(ItemStack(Material.PAPER).apply {
            editMeta {
                it.displayName(Component.text("판매").decoration(TextDecoration.ITALIC, false))
                it.setCustomModelData(17741)
            }
        }) {
            title = DynamicShopPlugin.shopSellName
            mode = ShopMode.SELL
            panes.remove(this.buyPane)
            addPane(sellPane!!)
            update()
        }
        pane.addItem(sellModeButton, 2, 0)
        pane.addItem(sellModeButton, 3, 0)

        val buyModeButton = GuiItem(ItemStack(Material.PAPER).apply {
            editMeta {
                it.displayName(Component.text("구매").decoration(TextDecoration.ITALIC, false))
                it.setCustomModelData(17741)
            }
        }) {
            title = DynamicShopPlugin.shopBuyName
            mode = ShopMode.BUY
            panes.remove(this.sellPane)
            addPane(buyPane!!)
            update()
        }
        pane.addItem(buyModeButton, 5, 0)
        pane.addItem(buyModeButton, 6, 0)

        val previousButton = PageController.PREVIOUS.toItemStack(this, "§f<<", if (mode == ShopMode.BUY) this.buyPane else this.sellPane)
        pane.addItem(previousButton, 6, 1)

        val nextButton = PageController.NEXT.toItemStack(this, "§f>>", if (mode == ShopMode.BUY) this.buyPane else this.sellPane)
        pane.addItem(nextButton, 7, 1)

        return pane
    }

    private enum class PageController
        (
        private val shouldContinue: BiPredicate<Int, PaginatedPane?>,
        private val nextPageSupplier: IntUnaryOperator
    ) {
        PREVIOUS(
            BiPredicate { page: Int, _: PaginatedPane? -> page > 0 },
            IntUnaryOperator { page: Int ->
                var currentPage = page
                --currentPage
            }),
        NEXT(
            BiPredicate { page: Int, itemsPane: PaginatedPane? -> page < (itemsPane!!.pages - 1) },
            IntUnaryOperator { page: Int ->
                var currentPage = page
                ++currentPage
            });

        fun toItemStack(gui: ChestGui, itemName: String?, itemsPane: PaginatedPane?): GuiItem {
            val item = ItemStack(Material.PAPER)
            val meta = item.itemMeta
            meta.setCustomModelData(17741)
            meta.displayName(Component.text(itemName.toString()))
            item.setItemMeta(meta)

            return GuiItem(item, Consumer { _: InventoryClickEvent? ->
                val currentPage = itemsPane!!.page
                if (!shouldContinue.test(currentPage, itemsPane)) return@Consumer

                itemsPane.page = nextPageSupplier.applyAsInt(currentPage)
                gui.update()
            })
        }
    }


    private enum class ShopMode() {
        SELL,
        BUY
    }


    class Builder() {
        lateinit var player: Player
        var sellItemList: Deque<ShopItem> = LinkedList()
        var buyItemList: Deque<ShopItem> = LinkedList()

        fun setSellList(queue: Deque<ShopItem>) = apply { this.sellItemList = queue }
        fun setBuyList(queue: Deque<ShopItem>) = apply { this.buyItemList = queue }
        fun forPlayer(player: Player) = apply { this.player = player }
        fun build() = ShopGUI(this)
    }


    companion object {
        private const val ITEMS_PER_PAGE = 9 * 4
    }
}


object ShopManager {
    // 각 상점별 판매 목록을 저장
    private val shopSellLists = mutableMapOf<String, LinkedList<ShopItem>>()
    private val shopGUIs = mutableMapOf<String, ShopGUI.Builder>()

    // 각 상점별 기본 아이템 목록
    val defaultShopItems = mapOf(
        "OreShop" to listOf(
            ShopItem(10000.0, 1112000.0, 900.0, ItemStack(Material.FLINT)),
            ShopItem(10000.0, 1112000.0, 900.0, ItemStack(Material.QUARTZ)),
            ShopItem(10000.0, 112000.0, 900.0, ItemStack(Material.AMETHYST_SHARD)),
            ShopItem(10000.0, 112000.0, 900.0, ItemStack(Material.COPPER_INGOT)),
            ShopItem(10000.0, 112000.0, 900.0, ItemStack(Material.IRON_INGOT)),
            ShopItem(10000.0, 112000.0, 900.0, ItemStack(Material.GOLD_INGOT)),
            ShopItem(10000.0, 112000.0, 900.0, ItemStack(Material.DIAMOND)),
            ShopItem(10000.0, 112000.0, 900.0, ItemStack(Material.EMERALD)),
            ShopItem(10000.0, 112000.0, 900.0, ItemStack(Material.NETHERITE_INGOT)),
            ShopItem(10000.0, 112000.0, 900.0, ItemStack(Material.COAL_BLOCK)),
            ShopItem(10000.0, 112000.0, 900.0, ItemStack(Material.LAPIS_BLOCK)),
            ShopItem(10000.0, 112000.0, 900.0, ItemStack(Material.REDSTONE_BLOCK)),
            ShopItem(10000.0, 112000.0, 900.0, ItemStack(Material.OBSIDIAN)),
            ShopItem(10000.0, 112000.0, 900.0, ItemStack(Material.CRYING_OBSIDIAN)),
            ShopItem(10000.0, 112000.0, 900.0, ItemStack(Material.GLOWSTONE))
        ),
        "FishShop" to listOf(
            ShopItem(5000.0, 16000.0, 400.0, ItemStack(Material.COD)),
            ShopItem(5000.0, 16000.0, 400.0, ItemStack(Material.SALMON)),
            ShopItem(5000.0, 16000.0, 400.0, ItemStack(Material.TROPICAL_FISH)),
            ShopItem(5000.0, 16000.0, 400.0, ItemStack(Material.PUFFERFISH))
        )
        // 추가 상점들은 여기에 추가
    )

    init {
        // 각 상점 초기화
        defaultShopItems.keys.forEach { shopName ->
            shopSellLists[shopName] = LinkedList()
            shopGUIs[shopName] = ShopGUI.Builder().setSellList(shopSellLists[shopName] as Deque<ShopItem>)
        }
    }

    fun updateShopPrices() {
        // Increment cycle
        DynamicShopPlugin.cycle++

        // Update prices for all shops and their items
        shopSellLists.forEach { (shopName, items) ->
            items.forEach { shopItem ->
                // Record sales and adjust price
                val salesAmount = shopItem.sellAmount.toDouble()
                shopItem.sellAmount = 0
                shopItem.previousPrice = shopItem.price
                shopItem.recordSales(salesAmount, DynamicShopPlugin.cycle)
                shopItem.price = round(shopItem.changePrice(DynamicShopPlugin.cycle))
            }
        }

        // Save updated configuration
        saveShopConfig()
        Bukkit.broadcastMessage("가격 변동됨")
    }

    private fun getMaterialName(material: Material): String {
        return material.name.toLowerCase().replace("_", " ")
    }

    fun loadShopConfig() {
        val configFile = File(plugin.dataFolder, "shops.yml")

        if (!configFile.exists()) {
            // 파일이 없을 경우 모든 상점의 기본 설정으로 저장
            defaultShopItems.forEach { (shopName, defaultItems) ->
                shopSellLists[shopName]?.clear()
                shopSellLists[shopName]?.addAll(defaultItems)
            }
            saveShopConfig()
            return
        }

        val config = YamlConfiguration.loadConfiguration(configFile)

        // 각 상점별로 설정 로드
        defaultShopItems.forEach { (shopName, defaultItems) ->
            val shopSection = config.getConfigurationSection(shopName) ?: return@forEach
            val sellList = shopSellLists[shopName] ?: return@forEach
            sellList.clear()

            // defaultItems를 Map으로 변환하여 Material을 키로 사용
            val materialToItem = defaultItems.associateBy { it.itemStack.type }

            for (key in shopSection.getKeys(false)) {
                val section = shopSection.getConfigurationSection(key) ?: continue
                val price = section.getDouble("price")
                val maxPrice = section.getDouble("maxPrice")
                val minPrice = section.getDouble("minPrice")

                val material = Material.values().find { getMaterialName(it) == key } ?: continue
                val defaultItem = materialToItem[material] ?: continue

                val shopItem = ShopItem(price, maxPrice, minPrice, defaultItem.itemStack)

                // 판매 기록 복원
                val salesHistory = section.getList("salesHistory")
                salesHistory?.forEach { salesData ->
                    if (salesData is Map<*, *>) {
                        val cycle = (salesData["cycle"] as? Number)?.toInt() ?: return@forEach
                        val timestampStr = salesData["timestamp"] as? String ?: return@forEach
                        val salesAmount = (salesData["salesAmount"] as? Number)?.toDouble() ?: return@forEach

                        // LocalDateTime으로 복원
                        val timestamp = LocalDateTime.parse(timestampStr)

                        // SalesCycle 객체 생성 및 추가
                        shopItem.totalSellAmount.add(
                            ShopItem.SalesCycle(cycle, timestamp, salesAmount)
                        )
                        println(shopItem.totalSellAmount.size)
                    }
                }

                sellList.add(shopItem)
            }

            // ShopGUI 업데이트
            shopGUIs[shopName]?.setSellList(sellList)
        }
    }

    fun saveShopConfig() {
        val configFile = File(plugin.dataFolder, "shops.yml")
        val config = YamlConfiguration()
        // 각 상점별로 설정 저장
        shopSellLists.forEach { (shopName, items) ->
            items.forEach { item ->
                val materialName = getMaterialName(item.itemStack.type)
                val path = "$shopName.$materialName"
                config.set("$path.price", item.price)
                config.set("$path.maxPrice", item.maxPrice)
                config.set("$path.minPrice", item.minPrice)

                val salesHistory = item.totalSellAmount.map {
                    mapOf(
                        "cycle" to it.cycle,
                        "timestamp" to it.timestamp.toString(),
                        "salesAmount" to it.salesAmount
                    )
                }
                config.set("$path.salesHistory", salesHistory)
            }
        }

        try {
            config.save(configFile)
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save shop configuration: ${e.message}")
        }
    }

    // 상점 GUI 가져오기
    fun getShopGUI(shopName: String): ShopGUI.Builder? {
        return shopGUIs[shopName]
    }
}


fun Player.openShop(shopName: String) {
    val gui = ShopManager.getShopGUI(shopName) ?: return
    gui.forPlayer(this).build().show(this)
}