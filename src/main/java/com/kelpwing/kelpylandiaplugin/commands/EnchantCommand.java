package com.kelpwing.kelpylandiaplugin.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * /enchant <enchantment> [level] [player] - Apply an enchantment to the item in hand.
 * Console may specify a player: /enchant <enchantment> [level] <player>
 * Supports unsafe enchantments (any level, any item) with the admin permission.
 */
public class EnchantCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;

    // Friendly name -> Enchantment lookup
    private static final Map<String, Enchantment> ENCHANT_MAP = new LinkedHashMap<>();

    static {
        for (Enchantment ench : Enchantment.values()) {
            // Use the key name (e.g. "sharpness", "fire_aspect")
            ENCHANT_MAP.put(ench.getKey().getKey().toLowerCase(), ench);
        }
    }

    public EnchantCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /enchant <enchantment> [level] [player]");
            return true;
        }

        // Determine the target player
        Player target = null;

        // Check if last argument is an online player name
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
        }
        // If no player specified, sender must be a player
        if (target == null && args.length >= 3) {
            // Third arg was given but not a valid player
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[2]);
            return true;
        }
        if (target == null) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Console must specify a player: /enchant <enchantment> [level] <player>");
                return true;
            }
            target = (Player) sender;
        }

        ItemStack item = target.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            sender.sendMessage(ChatColor.RED + (sender == target ? "You must" : target.getName() + " must") + " hold an item to enchant.");
            return true;
        }

        String enchantName = args[0].toLowerCase().replace("-", "_");
        Enchantment enchantment = ENCHANT_MAP.get(enchantName);
        if (enchantment == null) {
            // Try NamespacedKey directly
            enchantment = Enchantment.getByKey(NamespacedKey.minecraft(enchantName));
        }
        if (enchantment == null) {
            sender.sendMessage(ChatColor.RED + "Unknown enchantment: " + args[0]);
            sender.sendMessage(ChatColor.GRAY + "Use tab-complete to see available enchantments.");
            return true;
        }

        int level = 1;
        if (args.length >= 2) {
            try {
                level = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                // Might be a player name as the 2nd arg (no level specified)
                Player maybePlayer = Bukkit.getPlayerExact(args[1]);
                if (maybePlayer != null) {
                    target = maybePlayer;
                    item = target.getInventory().getItemInMainHand();
                    if (item.getType().isAir()) {
                        sender.sendMessage(ChatColor.RED + target.getName() + " must hold an item to enchant.");
                        return true;
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Invalid level or player: " + args[1]);
                    return true;
                }
            }
            if (level < 0) {
                sender.sendMessage(ChatColor.RED + "Level must be 0 or higher.");
                return true;
            }
        }

        // Level 0 = remove the enchantment
        if (level == 0) {
            item.removeEnchantment(enchantment);
            String msg = ChatColor.GREEN + "Removed " + ChatColor.GOLD + formatName(enchantment) + ChatColor.GREEN + " from "
                    + (sender == target ? "your item." : target.getName() + "'s item.");
            sender.sendMessage(msg);
            if (sender != target) {
                target.sendMessage(ChatColor.GREEN + formatName(enchantment) + " was removed from your item.");
            }
            return true;
        }

        boolean unsafe = sender.hasPermission("qol.enchant.unsafe");
        if (unsafe) {
            // Bypass normal limits
            item.addUnsafeEnchantment(enchantment, level);
        } else {
            // Check if enchantment can apply to this item
            if (!enchantment.canEnchantItem(item)) {
                sender.sendMessage(ChatColor.RED + "That enchantment cannot be applied to this item.");
                return true;
            }
            if (level > enchantment.getMaxLevel()) {
                sender.sendMessage(ChatColor.RED + "Maximum level for " + formatName(enchantment) + " is " + enchantment.getMaxLevel() + ".");
                return true;
            }
            // Check for conflicts with enchantments already on the item.
            // Bukkit's addEnchantment() calls addUnsafeEnchantment() internally, which can
            // silently replace/drop a conflicting enchantment - we catch it here first.
            for (Map.Entry<Enchantment, Integer> existing : item.getEnchantments().entrySet()) {
                if (enchantment.conflictsWith(existing.getKey())) {
                    sender.sendMessage(ChatColor.RED + formatName(enchantment)
                            + " conflicts with " + formatName(existing.getKey())
                            + " already on this item. Use a higher-permission level to force.");
                    return true;
                }
            }
            item.addEnchantment(enchantment, level);
        }

        String msg = ChatColor.GREEN + "Applied " + ChatColor.GOLD + formatName(enchantment) + " " + level + ChatColor.GREEN + " to "
                + (sender == target ? "your item." : target.getName() + "'s item.");
        sender.sendMessage(msg);
        if (sender != target) {
            target.sendMessage(ChatColor.GREEN + formatName(enchantment) + " " + level + " was applied to your item.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> results = new ArrayList<>();
            for (String name : ENCHANT_MAP.keySet()) {
                if (name.startsWith(prefix)) {
                    results.add(name);
                }
            }
            return results;
        }
        if (args.length == 2) {
            // Suggest common levels and online player names
            String prefix = args[1].toLowerCase();
            List<String> results = new ArrayList<>(Arrays.asList("1", "2", "3", "4", "5"));
            for (Player online : Bukkit.getOnlinePlayers()) {
                results.add(online.getName());
            }
            results.removeIf(s -> !s.toLowerCase().startsWith(prefix));
            return results;
        }
        if (args.length == 3) {
            // Suggest online player names
            String prefix = args[2].toLowerCase();
            List<String> results = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(prefix)) {
                    results.add(online.getName());
                }
            }
            return results;
        }
        return Collections.emptyList();
    }

    private String formatName(Enchantment enchantment) {
        String key = enchantment.getKey().getKey();
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
