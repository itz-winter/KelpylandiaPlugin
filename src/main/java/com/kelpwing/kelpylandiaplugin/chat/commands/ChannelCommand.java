package com.kelpwing.kelpylandiaplugin.chat.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.chat.Channel;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChannelCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;

    public ChannelCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("qol.channel.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to manage channels.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                return handleCreateCommand(sender, args);
            case "delete":
                return handleDeleteCommand(sender, args);
            case "list":
                return handleListCommand(sender);
            case "info":
                return handleInfoCommand(sender, args);
            case "set":
                return handleSetCommand(sender, args);
            case "reload":
                return handleReloadCommand(sender);
            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "=== Channel Management ===");
        sender.sendMessage(ChatColor.WHITE + "/channel create <name> <displayName> - Create a new channel");
        sender.sendMessage(ChatColor.WHITE + "/channel delete <name> - Delete a channel");
        sender.sendMessage(ChatColor.WHITE + "/channel list - List all channels");
        sender.sendMessage(ChatColor.WHITE + "/channel info <name> - Show channel information");
        sender.sendMessage(ChatColor.WHITE + "/channel set <name> <property> <value> - Set channel property");
        sender.sendMessage(ChatColor.WHITE + "/channel reload - Reload channel configuration");
    }

    private boolean handleCreateCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /channel create <name> <displayName>");
            return true;
        }

        String name = args[1].toLowerCase();
        String displayName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        if (plugin.getChannelManager().getChannel(name) != null) {
            sender.sendMessage(ChatColor.RED + "Channel '" + name + "' already exists.");
            return true;
        }

        Channel channel = new Channel(name, displayName);
        plugin.getChannelManager().addChannel(channel);
        sender.sendMessage(ChatColor.GREEN + "Created channel: " + channel.getFormattedDisplayName());
        return true;
    }

    private boolean handleDeleteCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /channel delete <name>");
            return true;
        }

        String name = args[1];
        Channel channel = plugin.getChannelManager().getChannel(name);

        if (channel == null) {
            sender.sendMessage(ChatColor.RED + "Channel '" + name + "' does not exist.");
            return true;
        }

        if (channel.isDefaultChannel()) {
            sender.sendMessage(ChatColor.RED + "Cannot delete the default channel.");
            return true;
        }

        plugin.getChannelManager().removeChannel(channel.getName());
        sender.sendMessage(ChatColor.GREEN + "Deleted channel: " + channel.getFormattedDisplayName());
        return true;
    }

    private boolean handleListCommand(CommandSender sender) {
        List<Channel> channels = plugin.getChannelManager().getAllChannels();

        if (channels.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No channels configured.");
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + "Configured Channels:");
        for (Channel channel : channels) {
            String defaultMarker = channel.isDefaultChannel() ? ChatColor.YELLOW + " (default)" : "";
            String globalMarker = channel.isGlobal() ? "" : ChatColor.GRAY + " (local)";
            sender.sendMessage(
                    ChatColor.WHITE + "- " + channel.getFormattedDisplayName() + defaultMarker + globalMarker);
        }
        return true;
    }

    private boolean handleInfoCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /channel info <name>");
            return true;
        }

        String name = args[1];
        Channel channel = plugin.getChannelManager().getChannel(name);

        if (channel == null) {
            sender.sendMessage(ChatColor.RED + "Channel '" + name + "' does not exist.");
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + "Channel Information: " + channel.getFormattedDisplayName());
        sender.sendMessage(ChatColor.WHITE + "Name: " + channel.getName());
        sender.sendMessage(ChatColor.WHITE + "Display Name: " + channel.getDisplayName());
        sender.sendMessage(ChatColor.WHITE + "Permission: " + channel.getPermission());
        sender.sendMessage(
                ChatColor.WHITE + "Prefix: " + ChatColor.translateAlternateColorCodes('&', channel.getPrefix()));
        sender.sendMessage(
                ChatColor.WHITE + "Suffix: " + ChatColor.translateAlternateColorCodes('&', channel.getSuffix()));
        sender.sendMessage(ChatColor.WHITE + "Color: " + channel.getColor());
        sender.sendMessage(ChatColor.WHITE + "Format: " + channel.getFormat());
        sender.sendMessage(ChatColor.WHITE + "Global: " + (channel.isGlobal() ? "Yes" : "No"));
        sender.sendMessage(ChatColor.WHITE + "Default: " + (channel.isDefaultChannel() ? "Yes" : "No"));
        sender.sendMessage(ChatColor.WHITE + "Discord: " + (channel.isDiscordEnabled() ? "Enabled" : "Disabled"));
        if (channel.isRangeEnabled()) {
            sender.sendMessage(ChatColor.WHITE + "Range: " + channel.getRange() + " blocks");
        }
        if (!channel.getAllowedWorlds().isEmpty()) {
            sender.sendMessage(ChatColor.WHITE + "Allowed Worlds: " + String.join(", ", channel.getAllowedWorlds()));
        }
        return true;
    }

    private boolean handleSetCommand(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /channel set <name> <property> <value>");
            sender.sendMessage(ChatColor.WHITE
                    + "Properties: displayname, prefix, suffix, color, permission, format, global, default, discord, range");
            return true;
        }

        String name = args[1];
        String property = args[2].toLowerCase();
        String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

        Channel channel = plugin.getChannelManager().getChannel(name);

        if (channel == null) {
            sender.sendMessage(ChatColor.RED + "Channel '" + name + "' does not exist.");
            return true;
        }

        switch (property) {
            case "displayname":
                channel.setDisplayName(value);
                sender.sendMessage(ChatColor.GREEN + "Set display name to: " + value);
                break;
            case "prefix":
                channel.setPrefix(value);
                sender.sendMessage(
                        ChatColor.GREEN + "Set prefix to: " + ChatColor.translateAlternateColorCodes('&', value));
                break;
            case "suffix":
                channel.setSuffix(value);
                sender.sendMessage(
                        ChatColor.GREEN + "Set suffix to: " + ChatColor.translateAlternateColorCodes('&', value));
                break;
            case "color":
                try {
                    ChatColor color = ChatColor.valueOf(value.toUpperCase());
                    channel.setColor(color);
                    sender.sendMessage(ChatColor.GREEN + "Set color to: " + color + color.name());
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid color: " + value);
                }
                break;
            case "permission":
                channel.setPermission(value);
                sender.sendMessage(ChatColor.GREEN + "Set permission to: " + value);
                break;
            case "format":
                channel.setFormat(value);
                sender.sendMessage(ChatColor.GREEN + "Set format to: " + value);
                break;
            case "global":
                boolean global = Boolean.parseBoolean(value);
                channel.setGlobal(global);
                sender.sendMessage(ChatColor.GREEN + "Set global to: " + global);
                break;
            case "default":
                boolean defaultChannel = Boolean.parseBoolean(value);
                if (defaultChannel) {
                    // remove default from other channels
                    for (Channel c : plugin.getChannelManager().getAllChannels()) {
                        c.setDefaultChannel(false);
                    }
                }
                channel.setDefaultChannel(defaultChannel);
                sender.sendMessage(ChatColor.GREEN + "Set default to: " + defaultChannel);
                break;
            case "discord":
                boolean discord = Boolean.parseBoolean(value);
                channel.setDiscordEnabled(discord);
                sender.sendMessage(ChatColor.GREEN + "Set discord to: " + discord);
                break;
            case "range":
                try {
                    double range = Double.parseDouble(value);
                    channel.setRange(range);
                    sender.sendMessage(ChatColor.GREEN + "Set range to: " + range);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid range: " + value);
                }
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown property: " + property);
                return true;
        }

        plugin.getChannelManager().saveChannel(channel);
        return true;
    }

    private boolean handleReloadCommand(CommandSender sender) {
        plugin.getChannelManager().reloadChannels();
        sender.sendMessage(ChatColor.GREEN + "Channel configuration reloaded.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("create", "delete", "list", "info", "set", "reload"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("delete") ||
                args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("set"))) {
            for (Channel channel : plugin.getChannelManager().getAllChannels()) {
                completions.add(channel.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            completions.addAll(Arrays.asList("displayname", "prefix", "suffix", "color", "permission",
                    "format", "global", "default", "discord", "range"));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("set")) {
            String property = args[2].toLowerCase();
            if (property.equals("color")) {
                for (ChatColor color : ChatColor.values()) {
                    if (color.isColor()) {
                        completions.add(color.name().toLowerCase());
                    }
                }
            } else if (property.equals("global") || property.equals("default") || property.equals("discord")) {
                completions.addAll(Arrays.asList("true", "false"));
            }
        }

        return completions;
    }
}
