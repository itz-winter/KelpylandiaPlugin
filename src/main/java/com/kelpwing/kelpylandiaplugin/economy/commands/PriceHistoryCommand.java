package com.kelpwing.kelpylandiaplugin.economy.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.economy.DynamicPricingEngine;
import com.kelpwing.kelpylandiaplugin.economy.EconomyManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * /pricehistory [item] [length=10]
 * Shows the price history of an item, including base price, current dynamic price,
 * status label, and a list of historical snapshots with change indicators.
 */
public class PriceHistoryCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;
    private static final int DEFAULT_LENGTH = 10;

    public PriceHistoryCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        EconomyManager eco = plugin.getEconomyManager();
        if (eco == null || !eco.isEnabled()) {
            sender.sendMessage(ChatColor.RED + "The economy system is disabled.");
            return true;
        }
        if (!sender.hasPermission("qol.economy.pricehistory")) {
            sender.sendMessage(eco.getMessage("no-permission"));
            return true;
        }

        // Resolve material
        Material material;
        int length = DEFAULT_LENGTH;
        int offset = 0;

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Console must specify an item: /pricehistory <item> [length]");
                return true;
            }
            Player player = (Player) sender;
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) {
                sender.sendMessage(ChatColor.RED + "You're not holding anything! Specify an item: /pricehistory <item>");
                return true;
            }
            material = hand.getType();
        } else {
            material = EconomyManager.parseMaterial(args[0]);
            if (material == null) {
                sender.sendMessage(ChatColor.RED + "Unknown item: " + args[0]);
                return true;
            }
        }

        // Parse optional length (and offset via length:offset format)
        if (args.length >= 2) {
            String lenArg = args[1];
            if (lenArg.contains(":")) {
                String[] parts = lenArg.split(":");
                try {
                    length = Integer.parseInt(parts[0]);
                    offset = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid length: " + lenArg);
                    return true;
                }
            } else {
                try {
                    length = Integer.parseInt(lenArg);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid length: " + lenArg);
                    return true;
                }
            }
            if (length < 1) length = 1;
            if (length > 50) length = 50;
        }

        // Get base price
        EconomyManager.PriceResult baseResult = eco.getBasePrice(material);
        if (!baseResult.sellable) {
            String reason = eco.getUnsellableReason(material);
            sender.sendMessage(eco.getMessage("price-unsellable")
                    .replace("{item}", SellCommand.formatMaterial(material))
                    .replace("{reason}", reason != null ? reason : "."));
            return true;
        }

        DynamicPricingEngine engine = eco.getDynamicPricing();
        String itemName = SellCommand.formatMaterial(material);
        String basePriceStr = eco.getUnit() + baseResult.price.setScale(eco.getDecimals(), RoundingMode.HALF_UP).toPlainString();

        java.util.UUID playerUuid = (sender instanceof Player) ? ((Player) sender).getUniqueId() : null;

        // Current effective sell price
        EconomyManager.PriceResult currentResult = eco.getPrice(material, playerUuid);
        String currentPriceStr = eco.getUnit() + currentResult.price.setScale(eco.getDecimals(), RoundingMode.HALF_UP).toPlainString();

        // Current effective buy price
        EconomyManager.BuyPriceResult buyResult = eco.getBuyPrice(material, playerUuid);
        boolean hasBuy = buyResult.buyable && eco.isBuyingEnabled();

        // Header
        sender.sendMessage(ChatColor.GREEN + "Price history for " + ChatColor.AQUA + itemName
                + ChatColor.GREEN + " (last " + length + " entries):");
        sender.sendMessage(ChatColor.GREEN + "Base sell price: " + ChatColor.AQUA + basePriceStr);
        sender.sendMessage(ChatColor.GREEN + "Current sell price: " + ChatColor.AQUA + currentPriceStr);
        if (hasBuy) {
            EconomyManager.BuyPriceResult baseBuyResult = eco.getBaseBuyPrice(material);
            String baseBuyStr = eco.getUnit() + baseBuyResult.price.setScale(eco.getDecimals(), RoundingMode.HALF_UP).toPlainString();
            String currentBuyStr = eco.getUnit() + buyResult.price.setScale(eco.getDecimals(), RoundingMode.HALF_UP).toPlainString();
            sender.sendMessage(ChatColor.GREEN + "Base buy price: " + ChatColor.AQUA + baseBuyStr);
            sender.sendMessage(ChatColor.GREEN + "Current buy price: " + ChatColor.AQUA + currentBuyStr);
        }
        if (engine != null) {
            long circ = engine.getCirculation(material);
            sender.sendMessage(ChatColor.GREEN + "Circulation: " + ChatColor.AQUA + circ + " items");
        }

        if (engine == null || !engine.isEnabled()) {
            // Dynamic pricing disabled - show stable message
            sender.sendMessage(ChatColor.GREEN + "Status: " + ChatColor.YELLOW + "STABLE"
                    + ChatColor.WHITE + " (" + ChatColor.YELLOW + "0.00%" + ChatColor.WHITE + ")");
            sender.sendMessage(ChatColor.YELLOW + "[~] " + ChatColor.YELLOW + currentPriceStr + ChatColor.RESET + " (current)");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.RED + "Dynamic pricing is currently DISABLED");
            sender.sendMessage(ChatColor.RED + "Enable dynamic pricing in the config to enable price history tracking");
            return true;
        }

        // Dynamic pricing enabled
        double changePercent = engine.getChangePercent(material, playerUuid);
        String status = engine.getStatus(material, playerUuid);

        // Colour the status & change
        ChatColor statusColor;
        if (changePercent > 1.0) statusColor = ChatColor.RED;
        else if (changePercent < -1.0) statusColor = ChatColor.GREEN;
        else statusColor = ChatColor.YELLOW;

        String changeStr = String.format("%+.1f%%", changePercent);
        sender.sendMessage(ChatColor.GREEN + "Status: " + statusColor + status
                + ChatColor.WHITE + " (" + statusColor + changeStr + ChatColor.WHITE + ")");

        // Price history entries
        List<DynamicPricingEngine.PriceSnapshot> history = engine.getHistory(material);
        if (history.isEmpty()) {
            // No history yet - just show current
            sender.sendMessage(ChatColor.YELLOW + "[~] " + ChatColor.YELLOW + currentPriceStr
                    + ChatColor.RESET + " (current)");
        } else {
            // Show entries (most recent first), starting at offset
            int start = Math.max(0, history.size() - 1 - offset);
            int end = Math.max(0, start - length);
            long now = System.currentTimeMillis();

            // Current price first
            BigDecimal prevPrice = history.size() > 1 ? history.get(history.size() - 2).price : baseResult.price;
            String indicator = getIndicator(currentResult.price, prevPrice);
            sender.sendMessage(indicator + " " + formatEntryPrice(currentResult.price, prevPrice, eco)
                    + ChatColor.RESET + " (current)");

            // Historical entries
            for (int i = start; i >= end && i >= 0; i--) {
                DynamicPricingEngine.PriceSnapshot snap = history.get(i);
                BigDecimal prev = (i > 0) ? history.get(i - 1).price : baseResult.price;
                String ind = getIndicator(snap.price, prev);
                String timeAgo = formatTimeAgo(now - snap.timestamp);
                sender.sendMessage(ind + " " + formatEntryPrice(snap.price, prev, eco)
                        + ChatColor.DARK_GRAY + " (" + timeAgo + " ago)");
            }

            // Pagination hint
            int nextOffset = offset + length;
            if (nextOffset < history.size()) {
                sender.sendMessage(ChatColor.GREEN + "Use " + ChatColor.AQUA + "/pricehistory "
                        + material.name().toLowerCase() + " " + length + ":" + nextOffset
                        + ChatColor.GREEN + " to see the next " + length + " entries.");
            }
        }

        return true;
    }

    private String getIndicator(BigDecimal current, BigDecimal previous) {
        int cmp = current.compareTo(previous);
        if (cmp > 0) return ChatColor.RED + "[+]";
        if (cmp < 0) return ChatColor.GREEN + "[-]";
        return ChatColor.YELLOW + "[~]";
    }

    private String formatEntryPrice(BigDecimal price, BigDecimal previous, EconomyManager eco) {
        int cmp = price.compareTo(previous);
        ChatColor color;
        if (cmp > 0) color = ChatColor.RED;
        else if (cmp < 0) color = ChatColor.GREEN;
        else color = ChatColor.YELLOW;
        return color + eco.getUnit() + price.setScale(eco.getDecimals(), RoundingMode.HALF_UP).toPlainString();
    }

    private String formatTimeAgo(long millis) {
        if (millis < 60_000) return "just now";
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        if (minutes < 60) return minutes + " minute" + (minutes == 1 ? "" : "s");
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        if (hours < 24) return hours + " hour" + (hours == 1 ? "" : "s");
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        return days + " day" + (days == 1 ? "" : "s");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toUpperCase();
            return Stream.of(Material.values())
                    .filter(m -> m.isItem() && !m.isAir())
                    .map(m -> m.name().toLowerCase())
                    .filter(n -> n.startsWith(prefix.toLowerCase()))
                    .limit(30)
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return List.of("5", "10", "20", "50");
        }
        return new ArrayList<>();
    }
}
