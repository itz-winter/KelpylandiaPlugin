package com.kelpwing.kelpylandiaplugin.kits;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /kit <name>                  — Claim a kit
 * /kit list                    — List available kits
 * /kit preview <name>          — Preview a kit's contents (opens GUI)
 * /kit give <player> <name>    — Give a kit to another player (admin)
 * /kit create <name> [cooldown] — Create a kit from your inventory (admin)
 * /kit edit <name>             — Edit a kit's items in a GUI (admin)
 * /kit delete <name>           — Delete a kit (admin)
 * /kit reload                  — Reload kits from file (admin)
 * /kit info <name>             — Show kit metadata (admin)
 * /kit setcooldown <name> <seconds> — Set cooldown (admin)
 * /kit seticon <name> [material]    — Set display icon (admin, defaults to held item type)
 * /kit setdesc <name> <description> — Set description (admin)
 * /kit setperm <name> <permission>  — Set required permission (admin)
 */
public class KitCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;

    private static final List<String> ADMIN_SUBS = Arrays.asList(
        "create", "edit", "delete", "reload", "give", "info", "setcooldown", "seticon", "setdesc", "setperm",
        "setfirstjoin", "setonetime"
    );
    private static final List<String> PLAYER_SUBS = Arrays.asList("list", "preview");

    public KitCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        KitManager km = plugin.getKitManager();
        if (km == null) {
            sender.sendMessage(ChatColor.RED + "Kits system is not enabled.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "list":
                return handleList(sender);
            case "preview":
                return handlePreview(sender, args);
            case "give":
                return handleGive(sender, args);
            case "create":
                return handleCreate(sender, args);
            case "edit":
                return handleEdit(sender, args);
            case "delete":
                return handleDelete(sender, args);
            case "reload":
                return handleReload(sender);
            case "info":
                return handleInfo(sender, args);
            case "setcooldown":
                return handleSetCooldown(sender, args);
            case "seticon":
                return handleSetIcon(sender, args);
            case "setdesc":
                return handleSetDesc(sender, args);
            case "setperm":
                return handleSetPerm(sender, args);
            case "setfirstjoin":
                return handleSetFirstJoin(sender, args);
            case "setonetime":
                return handleSetOneTime(sender, args);
            default:
                // Treat as kit name: /kit <name>
                return handleClaim(sender, args);
        }
    }

    //  Claim 

    private boolean handleClaim(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can claim kits.");
            return true;
        }
        Player player = (Player) sender;
        KitManager km = plugin.getKitManager();

        Kit kit = km.getKit(args[0]);
        if (kit == null) {
            sender.sendMessage(ChatColor.RED + "Kit '" + args[0] + "' not found. Use /kit list to see available kits.");
            return true;
        }

        KitManager.GiveResult result = km.giveKit(player, kit);
        sender.sendMessage(result.isSuccess() ? ChatColor.GREEN + result.getMessage() : ChatColor.RED + result.getMessage());
        return true;
    }

    //  List 

    private boolean handleList(CommandSender sender) {
        KitManager km = plugin.getKitManager();
        List<Kit> available;

        if (sender instanceof Player) {
            available = km.getAvailableKits((Player) sender);
        } else {
            available = new ArrayList<>(km.getAllKits());
        }

        if (available.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No kits available.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + " Available Kits ");
        for (Kit kit : available) {
            StringBuilder line = new StringBuilder();
            line.append(ChatColor.GREEN).append("• ").append(ChatColor.WHITE).append(kit.getName());

            if (!kit.getDescription().isEmpty()) {
                line.append(ChatColor.GRAY).append(" - ").append(kit.getDescription());
            }

            // Cooldown info
            line.append(ChatColor.DARK_GRAY).append(" [").append(kit.getCooldownDisplay()).append("]");

            if (kit.isGiveOnFirstJoin()) {
                line.append(ChatColor.AQUA).append(" [First Join]");
            }

            // Show remaining cooldown for players
            if (sender instanceof Player) {
                long remaining = km.getRemainingCooldown(((Player) sender).getUniqueId(), kit);
                if (remaining == -1) {
                    line.append(ChatColor.RED).append(" (claimed)");
                } else if (remaining > 0) {
                    line.append(ChatColor.YELLOW).append(" (").append(km.formatCooldown(remaining)).append(")");
                } else {
                    line.append(ChatColor.GREEN).append(" (ready)");
                }
            }

            sender.sendMessage(line.toString());
        }
        sender.sendMessage(ChatColor.GOLD + "");
        return true;
    }

    //  Preview 

    private boolean handlePreview(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can preview kits.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /kit preview <name>");
            return true;
        }

        Kit kit = plugin.getKitManager().getKit(args[1]);
        if (kit == null) {
            sender.sendMessage(ChatColor.RED + "Kit '" + args[1] + "' not found.");
            return true;
        }

        plugin.getKitGUI().openPreview((Player) sender, kit);
        return true;
    }

    //  Give 

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("qol.kit.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /kit give <player> <kit>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player '" + args[1] + "' is not online.");
            return true;
        }

        Kit kit = plugin.getKitManager().getKit(args[2]);
        if (kit == null) {
            sender.sendMessage(ChatColor.RED + "Kit '" + args[2] + "' not found.");
            return true;
        }

        // Admin give bypasses cooldowns and permissions
        for (org.bukkit.inventory.ItemStack item : kit.getItems()) {
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                java.util.HashMap<Integer, org.bukkit.inventory.ItemStack> overflow =
                    target.getInventory().addItem(item.clone());
                for (org.bukkit.inventory.ItemStack leftover : overflow.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), leftover);
                }
            }
        }

        sender.sendMessage(ChatColor.GREEN + "Gave kit '" + kit.getName() + "' to " + target.getName() + ".");
        target.sendMessage(ChatColor.GREEN + "You received kit '" + kit.getName() + "' from " + sender.getName() + ".");
        return true;
    }

    //  Create 

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("qol.kit.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can create kits (items come from your inventory).");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /kit create <name> [cooldown-seconds]");
            return true;
        }

        String kitName = args[1];
        KitManager km = plugin.getKitManager();

        if (km.getKit(kitName) != null) {
            sender.sendMessage(ChatColor.RED + "A kit named '" + kitName + "' already exists. Use /kit edit " + kitName + " to modify it.");
            return true;
        }

        long cooldown = 0;
        if (args.length >= 3) {
            try {
                cooldown = Long.parseLong(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid cooldown. Use seconds (0 = one-time, -1 = no cooldown).");
                return true;
            }
        }

        Player player = (Player) sender;
        List<org.bukkit.inventory.ItemStack> items = new ArrayList<>();
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                items.add(item.clone());
            }
        }

        if (items.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Your inventory is empty — put items in your inventory first.");
            return true;
        }

        Kit kit = new Kit(kitName, items, cooldown, "qol.kit." + kitName.toLowerCase(),
                org.bukkit.Material.CHEST, "", false, false);
        km.saveKit(kit);
        sender.sendMessage(ChatColor.GREEN + "Kit '" + kitName + "' created with " + items.size() + " item(s)!");
        sender.sendMessage(ChatColor.GRAY + "Cooldown: " + kit.getCooldownDisplay() + " | Permission: " + kit.getPermission());
        return true;
    }

    //  Edit 

    private boolean handleEdit(CommandSender sender, String[] args) {
        if (!sender.hasPermission("qol.kit.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can edit kits in-game.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /kit edit <name>");
            return true;
        }

        Kit kit = plugin.getKitManager().getKit(args[1]);
        if (kit == null) {
            sender.sendMessage(ChatColor.RED + "Kit '" + args[1] + "' not found.");
            return true;
        }

        plugin.getKitGUI().openEditor((Player) sender, kit);
        return true;
    }

    //  Delete 

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("qol.kit.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /kit delete <name>");
            return true;
        }

        KitManager km = plugin.getKitManager();
        Kit kit = km.getKit(args[1]);
        if (kit == null) {
            sender.sendMessage(ChatColor.RED + "Kit '" + args[1] + "' not found.");
            return true;
        }

        km.deleteKit(kit.getName());
        sender.sendMessage(ChatColor.GREEN + "Kit '" + kit.getName() + "' deleted.");
        return true;
    }

    //  Reload 

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("qol.kit.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        plugin.getKitManager().reload();
        sender.sendMessage(ChatColor.GREEN + "Kits reloaded from kits.yml.");
        return true;
    }

    //  Info 

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("qol.kit.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /kit info <name>");
            return true;
        }

        Kit kit = plugin.getKitManager().getKit(args[1]);
        if (kit == null) {
            sender.sendMessage(ChatColor.RED + "Kit '" + args[1] + "' not found.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + " Kit: " + kit.getName() + " ");
        sender.sendMessage(ChatColor.GRAY + "Description: " + ChatColor.WHITE + (kit.getDescription().isEmpty() ? "(none)" : kit.getDescription()));
        sender.sendMessage(ChatColor.GRAY + "Items: " + ChatColor.WHITE + kit.getItems().size());
        sender.sendMessage(ChatColor.GRAY + "Cooldown: " + ChatColor.WHITE + kit.getCooldownDisplay());
        sender.sendMessage(ChatColor.GRAY + "One-time: " + ChatColor.WHITE + (kit.isOneTime() ? "Yes" : "No"));
        sender.sendMessage(ChatColor.GRAY + "First-join: " + ChatColor.WHITE + (kit.isGiveOnFirstJoin() ? "Yes" : "No"));
        sender.sendMessage(ChatColor.GRAY + "Permission: " + ChatColor.WHITE + (kit.getPermission() != null ? kit.getPermission() : "(none)"));
        sender.sendMessage(ChatColor.GRAY + "Icon: " + ChatColor.WHITE + kit.getDisplayIcon().name());
        return true;
    }

    //  Set Cooldown 

    private boolean handleSetCooldown(CommandSender sender, String[] args) {
        if (!sender.hasPermission("qol.kit.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /kit setcooldown <name> <seconds>");
            sender.sendMessage(ChatColor.GRAY + "0 = one-time, -1 = no cooldown, positive = seconds");
            return true;
        }

        Kit kit = plugin.getKitManager().getKit(args[1]);
        if (kit == null) {
            sender.sendMessage(ChatColor.RED + "Kit '" + args[1] + "' not found.");
            return true;
        }

        try {
            long cd = Long.parseLong(args[2]);
            kit.setCooldownSeconds(cd);
            plugin.getKitManager().saveKit(kit);
            sender.sendMessage(ChatColor.GREEN + "Kit '" + kit.getName() + "' cooldown set to: " + kit.getCooldownDisplay());
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid number. Use seconds (0, -1, or positive).");
        }
        return true;
    }

    //  Set Icon 

    private boolean handleSetIcon(CommandSender sender, String[] args) {
        if (!sender.hasPermission("qol.kit.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /kit seticon <name> [material]");
            return true;
        }

        Kit kit = plugin.getKitManager().getKit(args[1]);
        if (kit == null) {
            sender.sendMessage(ChatColor.RED + "Kit '" + args[1] + "' not found.");
            return true;
        }

        org.bukkit.Material mat;
        if (args.length >= 3) {
            mat = org.bukkit.Material.matchMaterial(args[2]);
            if (mat == null) {
                sender.sendMessage(ChatColor.RED + "Unknown material: " + args[2]);
                return true;
            }
        } else if (sender instanceof Player) {
            org.bukkit.inventory.ItemStack hand = ((Player) sender).getInventory().getItemInMainHand();
            mat = hand.getType();
            if (mat == org.bukkit.Material.AIR) {
                sender.sendMessage(ChatColor.RED + "Hold an item or specify a material name.");
                return true;
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Specify a material name from console.");
            return true;
        }

        kit.setDisplayIcon(mat);
        plugin.getKitManager().saveKit(kit);
        sender.sendMessage(ChatColor.GREEN + "Kit '" + kit.getName() + "' icon set to " + mat.name() + ".");
        return true;
    }

    //  Set Description 

    private boolean handleSetDesc(CommandSender sender, String[] args) {
        if (!sender.hasPermission("qol.kit.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /kit setdesc <name> <description...>");
            return true;
        }

        Kit kit = plugin.getKitManager().getKit(args[1]);
        if (kit == null) {
            sender.sendMessage(ChatColor.RED + "Kit '" + args[1] + "' not found.");
            return true;
        }

        String desc = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        kit.setDescription(desc);
        plugin.getKitManager().saveKit(kit);
        sender.sendMessage(ChatColor.GREEN + "Kit '" + kit.getName() + "' description set.");
        return true;
    }

    //  Set Permission 

    private boolean handleSetPerm(CommandSender sender, String[] args) {
        if (!sender.hasPermission("qol.kit.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /kit setperm <name> <permission>");
            sender.sendMessage(ChatColor.GRAY + "Use 'none' to remove the permission requirement.");
            return true;
        }

        Kit kit = plugin.getKitManager().getKit(args[1]);
        if (kit == null) {
            sender.sendMessage(ChatColor.RED + "Kit '" + args[1] + "' not found.");
            return true;
        }

        String perm = args[2].equalsIgnoreCase("none") ? null : args[2];
        kit.setPermission(perm);
        plugin.getKitManager().saveKit(kit);
        sender.sendMessage(ChatColor.GREEN + "Kit '" + kit.getName() + "' permission set to: " + (perm != null ? perm : "(none)"));
        return true;
    }

    //  Set First Join 

    private boolean handleSetFirstJoin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("qol.kit.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /kit setfirstjoin <name> <true|false>");
            return true;
        }

        Kit kit = plugin.getKitManager().getKit(args[1]);
        if (kit == null) {
            sender.sendMessage(ChatColor.RED + "Kit '" + args[1] + "' not found.");
            return true;
        }

        boolean value = args[2].equalsIgnoreCase("true") || args[2].equalsIgnoreCase("yes");
        kit.setGiveOnFirstJoin(value);
        plugin.getKitManager().saveKit(kit);
        sender.sendMessage(ChatColor.GREEN + "Kit '" + kit.getName() + "' give-on-first-join set to " + value + ".");
        return true;
    }

    //  Set One-Time 

    private boolean handleSetOneTime(CommandSender sender, String[] args) {
        if (!sender.hasPermission("qol.kit.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /kit setonetime <name> <true|false>");
            return true;
        }

        Kit kit = plugin.getKitManager().getKit(args[1]);
        if (kit == null) {
            sender.sendMessage(ChatColor.RED + "Kit '" + args[1] + "' not found.");
            return true;
        }

        boolean value = args[2].equalsIgnoreCase("true") || args[2].equalsIgnoreCase("yes");
        kit.setOneTime(value);
        plugin.getKitManager().saveKit(kit);
        sender.sendMessage(ChatColor.GREEN + "Kit '" + kit.getName() + "' one-time set to " + value + ".");
        return true;
    }

    //  Usage 

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + " Kit Commands ");
        sender.sendMessage(ChatColor.YELLOW + "/kit <name>" + ChatColor.GRAY + " — Claim a kit");
        sender.sendMessage(ChatColor.YELLOW + "/kit list" + ChatColor.GRAY + " — List available kits");
        sender.sendMessage(ChatColor.YELLOW + "/kit preview <name>" + ChatColor.GRAY + " — Preview a kit");
        if (sender.hasPermission("qol.kit.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/kit give <player> <kit>" + ChatColor.GRAY + " — Give kit to player");
            sender.sendMessage(ChatColor.YELLOW + "/kit create <name> [cooldown]" + ChatColor.GRAY + " — Create from inventory");
            sender.sendMessage(ChatColor.YELLOW + "/kit edit <name>" + ChatColor.GRAY + " — Edit kit items in GUI");
            sender.sendMessage(ChatColor.YELLOW + "/kit delete <name>" + ChatColor.GRAY + " — Delete a kit");
            sender.sendMessage(ChatColor.YELLOW + "/kit info <name>" + ChatColor.GRAY + " — Show kit details");
            sender.sendMessage(ChatColor.YELLOW + "/kit setcooldown <name> <seconds>" + ChatColor.GRAY + " — Set cooldown");
            sender.sendMessage(ChatColor.YELLOW + "/kit seticon <name> [material]" + ChatColor.GRAY + " — Set display icon");
            sender.sendMessage(ChatColor.YELLOW + "/kit setdesc <name> <desc>" + ChatColor.GRAY + " — Set description");
            sender.sendMessage(ChatColor.YELLOW + "/kit setperm <name> <perm>" + ChatColor.GRAY + " — Set permission");
            sender.sendMessage(ChatColor.YELLOW + "/kit setfirstjoin <name> <true|false>" + ChatColor.GRAY + " — Give on first join");
            sender.sendMessage(ChatColor.YELLOW + "/kit setonetime <name> <true|false>" + ChatColor.GRAY + " — Claim once only");
            sender.sendMessage(ChatColor.YELLOW + "/kit reload" + ChatColor.GRAY + " — Reload from file");
        }
    }

    //  Tab completion 

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        KitManager km = plugin.getKitManager();
        if (km == null) return Collections.emptyList();

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Sub-commands + kit names
            completions.addAll(PLAYER_SUBS);
            if (sender.hasPermission("qol.kit.admin")) {
                completions.addAll(ADMIN_SUBS);
            }
            // Also suggest kit names directly for /kit <name>
            completions.addAll(km.getKitNames());
            return filter(completions, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "preview":
                case "delete":
                case "edit":
                case "info":
                case "setcooldown":
                case "seticon":
                case "setdesc":
                case "setperm":
                case "setfirstjoin":
                case "setonetime":
                    return filter(new ArrayList<>(km.getKitNames()), args[1]);
                case "give":
                    return null; // default player name completion
                default:
                    return Collections.emptyList();
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if ("give".equals(sub)) {
                return filter(new ArrayList<>(km.getKitNames()), args[2]);
            }
            if ("seticon".equals(sub)) {
                // Suggest material names
                List<String> mats = new ArrayList<>();
                for (org.bukkit.Material m : org.bukkit.Material.values()) {
                    if (!m.name().startsWith("LEGACY_")) mats.add(m.name());
                }
                return filter(mats, args[2]);
            }
            if ("setfirstjoin".equals(sub) || "setonetime".equals(sub)) {
                return filter(Arrays.asList("true", "false"), args[2]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}
