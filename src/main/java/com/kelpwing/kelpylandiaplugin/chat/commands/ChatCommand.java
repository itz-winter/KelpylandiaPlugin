package com.kelpwing.kelpylandiaplugin.chat.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.chat.Channel;
import com.kelpwing.kelpylandiaplugin.chat.ChatFormatUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ChatCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;

    public ChatCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // show current channel
            Channel currentChannel = plugin.getChannelManager().getPlayerChannel(player);
            if (currentChannel != null) {
                player.sendMessage(
                        ChatColor.GREEN + "You are currently in channel: " + currentChannel.getFormattedDisplayName());
            } else {
                player.sendMessage(ChatColor.RED + "You are not in any channel.");
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();

        // check if the first arg is channel name
        Channel directChannel = plugin.getChannelManager().getChannel(subCommand);
        if (directChannel != null) {
            return handleDirectChannelSwitch(player, directChannel);
        }

        // handle subcommands
        switch (subCommand) {
            case "join":
                return handleJoinCommand(player, args);
            case "leave":
                return handleLeaveCommand(player);
            case "list":
                return handleListCommand(player);
            case "info":
                return handleInfoCommand(player, args);
            case "who":
                return handleWhoCommand(player, args);
            case "msg":
            case "message":
                return handleMessageCommand(player, args);
            case "help":
                return handleHelpCommand(player);
            default:
                player.sendMessage(ChatColor.RED + "Unknown channel or subcommand: " + subCommand);
                player.sendMessage(
                        ChatColor.YELLOW + "Use '/chat help' for available commands or '/chat list' to see channels.");
                return true;
        }
    }

    private boolean handleDirectChannelSwitch(Player player, Channel channel) {
        if (!ChatFormatUtils.hasPermission(player, channel.getPermission())) {
            player.sendMessage(ChatColor.RED + "You don't have permission to join this channel.");
            return true;
        }

        plugin.getChannelManager().setPlayerChannel(player.getUniqueId(), channel.getName());
        player.sendMessage(ChatColor.GREEN + "Switched to channel: " + channel.getFormattedDisplayName());
        return true;
    }

    private boolean handleHelpCommand(Player player) {
        player.sendMessage(ChatColor.GREEN + "=== Chat Commands ===");
        player.sendMessage(ChatColor.WHITE + "/chat - Show current channel");
        player.sendMessage(ChatColor.WHITE + "/chat <channel> - Switch to channel");
        player.sendMessage(ChatColor.WHITE + "/chat join <channel> - Join a channel");
        player.sendMessage(ChatColor.WHITE + "/chat leave - Leave current channel");
        player.sendMessage(ChatColor.WHITE + "/chat list - List available channels");
        player.sendMessage(ChatColor.WHITE + "/chat info [channel] - Show channel information");
        player.sendMessage(ChatColor.WHITE + "/chat who [channel] - Show players in channel");
        player.sendMessage(ChatColor.WHITE + "/chat msg <channel> <message> - Send message to channel");
        return true;
    }

    private boolean handleJoinCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /chat join <channel>");
            return true;
        }

        String channelName = args[1];
        Channel channel = plugin.getChannelManager().getChannel(channelName);

        if (channel == null) {
            player.sendMessage(ChatColor.RED + "Channel '" + channelName + "' does not exist.");
            return true;
        }

        if (!ChatFormatUtils.hasPermission(player, channel.getPermission())) {
            player.sendMessage(ChatColor.RED + "You don't have permission to join this channel.");
            return true;
        }

        plugin.getChannelManager().setPlayerChannel(player.getUniqueId(), channel.getName());
        player.sendMessage(ChatColor.GREEN + "Joined channel: " + channel.getFormattedDisplayName());
        return true;
    }

    private boolean handleLeaveCommand(Player player) {
        Channel currentChannel = plugin.getChannelManager().getPlayerChannel(player);

        if (currentChannel == null) {
            player.sendMessage(ChatColor.RED + "You are not in any channel.");
            return true;
        }

        plugin.getChannelManager().removePlayerFromChannel(player);
        player.sendMessage(ChatColor.GREEN + "Left channel: " + currentChannel.getFormattedDisplayName());
        return true;
    }

    private boolean handleListCommand(Player player) {
        List<Channel> channels = plugin.getChannelManager().getAvailableChannels(player);

        if (channels.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No channels available.");
            return true;
        }

        player.sendMessage(ChatColor.GREEN + "Available channels:");
        for (Channel channel : channels) {
            String status = plugin.getChannelManager().getPlayerChannel(player) == channel
                    ? ChatColor.YELLOW + " (current)"
                    : "";
            player.sendMessage(ChatColor.WHITE + "- " + channel.getFormattedDisplayName() + status);
        }
        return true;
    }

    private boolean handleInfoCommand(Player player, String[] args) {
        Channel channel;

        if (args.length < 2) {
            channel = plugin.getChannelManager().getPlayerChannel(player);
            if (channel == null) {
                player.sendMessage(ChatColor.RED + "You are not in any channel. Specify a channel name.");
                return true;
            }
        } else {
            channel = plugin.getChannelManager().getChannel(args[1]);
            if (channel == null) {
                player.sendMessage(ChatColor.RED + "Channel '" + args[1] + "' does not exist.");
                return true;
            }
        }

        player.sendMessage(ChatColor.GREEN + "Channel Info: " + channel.getFormattedDisplayName());
        player.sendMessage(ChatColor.WHITE + "Name: " + channel.getName());
        player.sendMessage(ChatColor.WHITE + "Permission: " + channel.getPermission());
        player.sendMessage(ChatColor.WHITE + "Global: " + (channel.isGlobal() ? "Yes" : "No"));
        player.sendMessage(ChatColor.WHITE + "Discord: " + (channel.isDiscordEnabled() ? "Enabled" : "Disabled"));
        if (channel.isRangeEnabled()) {
            player.sendMessage(ChatColor.WHITE + "Range: " + channel.getRange() + " blocks");
        }
        return true;
    }

    private boolean handleWhoCommand(Player player, String[] args) {
        Channel channel;

        if (args.length < 2) {
            channel = plugin.getChannelManager().getPlayerChannel(player);
            if (channel == null) {
                player.sendMessage(ChatColor.RED + "You are not in any channel. Specify a channel name.");
                return true;
            }
        } else {
            channel = plugin.getChannelManager().getChannel(args[1]);
            if (channel == null) {
                player.sendMessage(ChatColor.RED + "Channel '" + args[1] + "' does not exist.");
                return true;
            }
        }

        List<Player> playersInChannel = plugin.getChannelManager().getPlayersInChannel(channel);

        if (playersInChannel.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No players in channel: " + channel.getFormattedDisplayName());
            return true;
        }

        StringBuilder playerList = new StringBuilder();
        for (int i = 0; i < playersInChannel.size(); i++) {
            if (i > 0)
                playerList.append(", ");
            playerList.append(playersInChannel.get(i).getName());
        }

        player.sendMessage(ChatColor.GREEN + "Players in " + channel.getFormattedDisplayName() +
                ChatColor.WHITE + " (" + playersInChannel.size() + "): " + playerList.toString());
        return true;
    }

    private boolean handleMessageCommand(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /chat msg <channel> <message>");
            return true;
        }

        String channelName = args[1];
        Channel channel = plugin.getChannelManager().getChannel(channelName);

        if (channel == null) {
            player.sendMessage(ChatColor.RED + "Channel '" + channelName + "' does not exist.");
            return true;
        }

        if (!ChatFormatUtils.hasPermission(player, channel.getPermission())) {
            player.sendMessage(ChatColor.RED + "You don't have permission to send messages to this channel.");
            return true;
        }

        // build the message from remaining args
        StringBuilder messageBuilder = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (i > 2)
                messageBuilder.append(" ");
            messageBuilder.append(args[i]);
        }

        String message = messageBuilder.toString();
        String formattedMessage = ChatFormatUtils.formatMessage(plugin, player, channel, message);

        // send message to channel recipients
        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            if (shouldReceiveChannelMessage(onlinePlayer, player, channel)) {
                onlinePlayer.sendMessage(formattedMessage);
            }
        }

        // send to Discord (if enabled)
        if (channel.isDiscordEnabled()) {
            plugin.getDiscordIntegration().sendChatMessage(player, message, channel.getDiscordChannel());
        }

        return true;
    }

    private boolean shouldReceiveChannelMessage(Player recipient, Player sender, Channel channel) {
        if (!ChatFormatUtils.hasPermission(recipient, channel.getPermission())) {
            return false;
        }

        if (!channel.isWorldAllowed(recipient.getWorld().getName())) {
            return false;
        }

        if (channel.isRangeEnabled()) {
            double distance = recipient.getLocation().distance(sender.getLocation());
            return distance <= channel.getRange();
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // add subcommands (yay how fun)
            completions.add("join");
            completions.add("leave");
            completions.add("list");
            completions.add("info");
            completions.add("who");
            completions.add("msg");
            completions.add("help");

            // add channel names for direct switching...cause some ppl just wanna type
            // /channel local instead of /l
            if (sender instanceof Player) {
                Player player = (Player) sender;
                for (Channel channel : plugin.getChannelManager().getAvailableChannels(player)) {
                    completions.add(channel.getName());
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("join") ||
                args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("who") ||
                args[0].equalsIgnoreCase("msg"))) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                for (Channel channel : plugin.getChannelManager().getAvailableChannels(player)) {
                    completions.add(channel.getName());
                }
            }
        }

        return completions;
    }
}
