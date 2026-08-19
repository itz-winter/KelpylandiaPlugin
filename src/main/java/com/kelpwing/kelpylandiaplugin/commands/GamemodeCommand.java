package com.kelpwing.kelpylandiaplugin.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gamemode shortcut commands:
 *   /gmc [player]  - Creative
 *   /gms [player]  - Survival
 *   /gma [player]  - Adventure
 *   /gmsp [player] - Spectator
 *   /gm <mode> [player] - Any mode by name/number
 */
public class GamemodeCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;

    private static final Map<String, GameMode> MODE_ALIASES = new HashMap<>();
    static {
        MODE_ALIASES.put("survival", GameMode.SURVIVAL);
        MODE_ALIASES.put("s", GameMode.SURVIVAL);
        MODE_ALIASES.put("0", GameMode.SURVIVAL);
        MODE_ALIASES.put("creative", GameMode.CREATIVE);
        MODE_ALIASES.put("c", GameMode.CREATIVE);
        MODE_ALIASES.put("1", GameMode.CREATIVE);
        MODE_ALIASES.put("adventure", GameMode.ADVENTURE);
        MODE_ALIASES.put("a", GameMode.ADVENTURE);
        MODE_ALIASES.put("2", GameMode.ADVENTURE);
        MODE_ALIASES.put("spectator", GameMode.SPECTATOR);
        MODE_ALIASES.put("sp", GameMode.SPECTATOR);
        MODE_ALIASES.put("3", GameMode.SPECTATOR);
    }

    public GamemodeCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmdName = label.toLowerCase();

        // Determine the target gamemode based on the command used
        GameMode targetMode = getGameModeFromLabel(cmdName);

        // /gm <mode> [player] - requires an argument for the mode
        if (targetMode == null && cmdName.equals("gm")) {
            if (args.length == 0) {
                sender.sendMessage(ChatColor.RED + "Usage: /gm <survival|creative|adventure|spectator> [player]");
                return true;
            }
            targetMode = MODE_ALIASES.get(args[0].toLowerCase());
            if (targetMode == null) {
                sender.sendMessage(ChatColor.RED + "Unknown gamemode: " + ChatColor.GOLD + args[0]);
                sender.sendMessage(ChatColor.GRAY + "Valid modes: survival, creative, adventure, spectator (or s, c, a, sp, 0-3)");
                return true;
            }
            // Shift args so args[0] is now the optional player
            String[] newArgs = new String[args.length - 1];
            System.arraycopy(args, 1, newArgs, 0, newArgs.length);
            args = newArgs;
        }

        if (targetMode == null) {
            sender.sendMessage(ChatColor.RED + "Unknown gamemode command.");
            return true;
        }

        // Check base permission
        String modePerm = getPermissionForMode(targetMode);
        if (!sender.hasPermission(modePerm)) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to switch to " + formatModeName(targetMode) + " mode.");
            return true;
        }

        // Determine target player
        Player target;
        if (args.length >= 1) {
            // Changing another player's gamemode
            if (!sender.hasPermission("qol.gamemode.others")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to change other players' gamemode.");
                return true;
            }

            target = plugin.getServer().getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player " + ChatColor.GOLD + args[0] + ChatColor.RED + " is not online.");
                return true;
            }
        } else {
            // Changing own gamemode
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Usage from console: " + label + " [mode] <player>");
                return true;
            }
            target = (Player) sender;
        }

        // Apply the gamemode
        target.setGameMode(targetMode);

        String modeName = formatModeName(targetMode);
        target.sendMessage(ChatColor.GREEN + "Gamemode set to " + ChatColor.GOLD + modeName + ChatColor.GREEN + ".");
        if (sender != target) {
            sender.sendMessage(ChatColor.GREEN + "Set " + ChatColor.GOLD + target.getName() +
                ChatColor.GREEN + "'s gamemode to " + ChatColor.GOLD + modeName + ChatColor.GREEN + ".");
        }

        return true;
    }

    /**
     * Determines the GameMode from the command label (gmc, gms, gma, gmsp).
     * Returns null for /gm (mode must be parsed from args).
     */
    private GameMode getGameModeFromLabel(String label) {
        switch (label) {
            case "gmc": return GameMode.CREATIVE;
            case "gms": return GameMode.SURVIVAL;
            case "gma": return GameMode.ADVENTURE;
            case "gmsp": return GameMode.SPECTATOR;
            default: return null;
        }
    }

    private String getPermissionForMode(GameMode mode) {
        switch (mode) {
            case CREATIVE: return "qol.gamemode.creative";
            case SURVIVAL: return "qol.gamemode.survival";
            case ADVENTURE: return "qol.gamemode.adventure";
            case SPECTATOR: return "qol.gamemode.spectator";
            default: return "qol.gamemode";
        }
    }

    private String formatModeName(GameMode mode) {
        String name = mode.name().toLowerCase();
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmdName = alias.toLowerCase();

        if (cmdName.equals("gm")) {
            // /gm <mode> [player]
            if (args.length == 1) {
                List<String> modes = new ArrayList<>();
                String partial = args[0].toLowerCase();
                for (String m : new String[]{"survival", "creative", "adventure", "spectator"}) {
                    if (m.startsWith(partial)) {
                        modes.add(m);
                    }
                }
                return modes;
            }
            if (args.length == 2) {
                return getOnlinePlayerNames(args[1], sender);
            }
        } else {
            // /gmc, /gms, /gma, /gmsp [player]
            if (args.length == 1) {
                return getOnlinePlayerNames(args[0], sender);
            }
        }

        return Collections.emptyList();
    }

    private List<String> getOnlinePlayerNames(String partial, CommandSender sender) {
        if (!sender.hasPermission("qol.gamemode.others")) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        String lower = partial.toLowerCase();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(lower)) {
                names.add(p.getName());
            }
        }
        return names;
    }
}
