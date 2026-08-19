package com.kelpwing.kelpylandiaplugin.economy.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.economy.EconomyManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /shop - Opens the server shop GUI for buying items.
 */
public class ShopCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;

    public ShopCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        EconomyManager eco = plugin.getEconomyManager();
        if (eco == null || !eco.isEnabled()) {
            sender.sendMessage(ChatColor.RED + "The economy system is disabled.");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use the shop.");
            return true;
        }
        if (!sender.hasPermission("qol.economy.shop")) {
            sender.sendMessage(eco.getMessage("no-permission"));
            return true;
        }
        if (!eco.isBuyingEnabled()) {
            sender.sendMessage(eco.getMessage("shop-buy-disabled"));
            return true;
        }

        Player player = (Player) sender;
        plugin.getShopGUI().openGUI(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }
}
