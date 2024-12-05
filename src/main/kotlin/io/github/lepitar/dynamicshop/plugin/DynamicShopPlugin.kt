package io.github.lepitar.dynamicshop.plugin

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.Argument
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import dev.jorel.commandapi.arguments.PlayerArgument
import dev.jorel.commandapi.arguments.StringArgument
import dev.jorel.commandapi.executors.CommandExecutor
import io.github.lepitar.dynamicshop.shop.ShopGUI
import io.github.lepitar.dynamicshop.shop.ShopManager
import io.github.lepitar.dynamicshop.shop.ShopManager.loadShopConfig
import io.github.lepitar.dynamicshop.shop.ShopManager.saveShopConfig
import io.github.lepitar.dynamicshop.shop.ShopPriceManager
import io.github.lepitar.dynamicshop.shop.openShop
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import kotlin.math.exp
import kotlin.properties.Delegates

class DynamicShopPlugin : JavaPlugin() {
    companion object {
        lateinit var econ: Economy
        lateinit var plugin: DynamicShopPlugin
        lateinit var shopSellName: String
        lateinit var shopBuyName: String
        var CYCLE_DURATION_HOURS = 8
        var cycle = 0
        val log_head = Component.text("[ 상점 ] ", NamedTextColor.BLUE)
            .decoration(TextDecoration.ITALIC, false)
    }

    override fun onEnable() {
        if (!setupEconomy()) {
            server.pluginManager.disablePlugin(this)
            return
        }
        plugin = this
        loadConfig()
        loadShopConfig()
        val arguments = listOf<Argument<*>>(
            StringArgument("command").replaceSuggestions(ArgumentSuggestions.strings(
                "change", "reload", *ShopManager.defaultShopItems.keys.toTypedArray()
            ))
        )
        CommandAPICommand("상점")
            .withArguments(arguments)
            .withOptionalArguments(PlayerArgument("player"))
            .executes(CommandExecutor { sender, args ->
                val cmd = args.get("command")
                if (cmd == "reload") {
                    loadConfig()
                    loadShopConfig()
                    sender.sendMessage("Reload Complete")
                } else if (cmd == "change") {
                    loadShopConfig()
                    Bukkit.broadcast(log_head.append(Component.text(": 물품 가격이 변동되었습니다!", NamedTextColor.WHITE)))
                } else {
                    val player = args.get("player") as Player
                    player.openShop(cmd.toString())
                }
            })
            .register()
        ShopPriceManager().startPriceUpdateScheduler()
    }

    private fun loadConfig() {
        val configFile = File(dataFolder, "config.yml")
        if (!configFile.exists()) {
            saveResource("config.yml", false)
        }
        val config = YamlConfiguration.loadConfiguration(configFile)
        shopSellName = config.getString("shop-sell") ?: "Set Shop Name"
        shopBuyName = config.getString("shop-buy") ?: "Set Shop Name"
        cycle = config.getInt("cycle")
    }

    private fun setupEconomy(): Boolean {
        if (server.pluginManager.getPlugin("Vault") == null) {
            return false
        }
        val rsp = server.servicesManager.getRegistration(
            Economy::class.java
        ) ?: return false
        econ = rsp.provider
        return true
    }

    override fun onDisable() {
        val configFile = File(dataFolder, "config.yml")
        val config = YamlConfiguration.loadConfiguration(configFile)
        config.set("cycle", cycle)
        config.save(configFile)
        saveShopConfig()
    }
}