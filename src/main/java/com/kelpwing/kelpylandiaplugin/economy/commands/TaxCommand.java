package com.kelpwing.kelpylandiaplugin.economy.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.economy.EconomyManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TaxCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;

    public TaxCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        EconomyManager eco = plugin.getEconomyManager();
        if (eco == null || !eco.isEnabled()) {
            sender.sendMessage(ChatColor.RED + "The economy system is disabled.");
            return true;
        }
        if (!sender.hasPermission("qol.economy.tax")) {
            sender.sendMessage(eco.getMessage("no-permission"));
            return true;
        }

        // /tax set <rate> - admin
        if (args.length >= 2 && args[0].equalsIgnoreCase("set")) {
            if (!sender.hasPermission("qol.economy.tax.edit")) {
                sender.sendMessage(eco.getMessage("no-permission"));
                return true;
            }
            try {
                BigDecimal newRate = new BigDecimal(args[1]);
                if (newRate.compareTo(BigDecimal.ZERO) < 0 || newRate.compareTo(BigDecimal.valueOf(100)) > 0) {
                    sender.sendMessage(ChatColor.RED + "Tax rate must be between 0 and 100.");
                    return true;
                }
                eco.setTaxRate(newRate);
                sender.sendMessage(ChatColor.GREEN + "Tax rate set to " + ChatColor.YELLOW + newRate.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%" + ChatColor.GREEN + ".");
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid rate: " + args[1]);
            }
            return true;
        }

        // /tax [amount] - view tax info, optionally calculate tax on an amount
        if (!eco.isTaxEnabled()) {
            sender.sendMessage(ChatColor.YELLOW + "Tax is currently " + ChatColor.RED + "disabled" + ChatColor.YELLOW + ".");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "---- Tax Info ----");
        sender.sendMessage(ChatColor.YELLOW + "Rate: " + ChatColor.WHITE + eco.getTaxRateDisplay());
        sender.sendMessage(ChatColor.YELLOW + "Applied to P2P: " + ChatColor.WHITE + (eco.isTaxOnPay() ? "Yes" : "No"));
        sender.sendMessage(ChatColor.YELLOW + "Applied to sells: " + ChatColor.WHITE + (eco.isTaxOnServerSell() ? "Yes" : "No"));

        if (args.length >= 1) {
            try {
                BigDecimal amount = new BigDecimal(args[0]);
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    sender.sendMessage(ChatColor.RED + "Amount must be positive.");
                    return true;
                }
                BigDecimal taxAmount = eco.calculateTax(amount);
                BigDecimal afterTax = eco.afterTax(amount);
                sender.sendMessage("");
                sender.sendMessage(ChatColor.YELLOW + "On " + eco.getUnit() + amount.setScale(eco.getDecimals(), RoundingMode.HALF_UP).toPlainString() + ":");
                sender.sendMessage(ChatColor.YELLOW + "  Tax: " + ChatColor.RED + eco.getUnit() + taxAmount.setScale(eco.getDecimals(), RoundingMode.HALF_UP).toPlainString());
                sender.sendMessage(ChatColor.YELLOW + "  After tax: " + ChatColor.GREEN + eco.getUnit() + afterTax.setScale(eco.getDecimals(), RoundingMode.HALF_UP).toPlainString());
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid amount: " + args[0]);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("100");
            suggestions.add("1000");
            if (sender.hasPermission("qol.economy.tax.edit")) {
                suggestions.add("set");
            }
            String prefix = args[0].toLowerCase();
            suggestions.removeIf(s -> !s.toLowerCase().startsWith(prefix));
            return suggestions;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return Arrays.asList("0", "5", "10", "15", "20");
        }
        return new ArrayList<>();
    }
}
