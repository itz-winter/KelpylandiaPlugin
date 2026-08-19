package com.kelpwing.kelpylandiaplugin.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;

/**
 * /rules - Display the server rules to a player.
 * Rules are configured in config.yml under rules.lines.
 */
public class RulesCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;

    public RulesCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        List<String> header = plugin.getConfig().getStringList("rules.header");
        List<String> lines = plugin.getConfig().getStringList("rules.lines");
        List<String> footer = plugin.getConfig().getStringList("rules.footer");

        // Header
        for (String line : header) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
        }

        // Rules
        if (lines.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No rules have been configured yet.");
        } else {
            for (int i = 0; i < lines.size(); i++) {
                String formatted = lines.get(i)
                        .replace("{number}", String.valueOf(i + 1));
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', formatted));
            }
        }

        // Footer
        for (String line : footer) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
