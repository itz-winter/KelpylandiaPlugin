package com.kelpwing.kelpylandiaplugin.moderation.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.integrations.DiscordIntegration;
import com.kelpwing.kelpylandiaplugin.chat.Channel;
import com.kelpwing.kelpylandiaplugin.chat.ChannelManager;
import com.kelpwing.kelpylandiaplugin.utils.LevelManager;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class VanishCommand implements CommandExecutor {

    private final KelpylandiaPlugin plugin;
    private final Set<UUID> vanishedPlayers;
    
    public VanishCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
        this.vanishedPlayers = new HashSet<>();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        
        if (!player.hasPermission("qol.vanish")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            // Toggle vanish for self
            toggleVanish(player);
        } else if (args.length == 1) {
            // Toggle vanish for target player
            if (!player.hasPermission("qol.vanish.others")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to vanish other players.");
                return true;
            }
            
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
                return true;
            }
            
            toggleVanish(target);
            if (target != player) {
                player.sendMessage(ChatColor.GREEN + "Toggled vanish for " + target.getName());
            }
        } else {
            player.sendMessage(ChatColor.RED + "Usage: /vanish [player]");
        }

        return true;
    }

    private void toggleVanish(Player player) {
        if (isVanished(player)) {
            unvanish(player);
        } else {
            vanish(player);
        }
    }

    private void vanish(Player player) {
        plugin.getLogger().info("Vanishing player: " + player.getName());
        vanishedPlayers.add(player.getUniqueId());
        
        // Persist immediately to disk
        if (plugin.getPlayerStateManager() != null) {
            plugin.getPlayerStateManager().saveToggle(player.getUniqueId(), "vanish", true);
        }
        
        // Hide player from all online players (respecting vanish levels)
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.equals(player)) continue;
            if (!onlinePlayer.hasPermission("qol.vanish.see")
                    || !LevelManager.canObserve(onlinePlayer, player, "vanish")) {
                onlinePlayer.hidePlayer(plugin, player);
            }
        }
        
        // Prevent item pickup
        plugin.getVanishManager().setCanPickupItems(player, false);
        
        // Send fake leave message
        sendFakeLeaveMessage(player);
        
        // Show vanish scoreboard
        plugin.getVanishManager().showVanishScoreboard(player);
        
        player.sendMessage(ChatColor.GREEN + "You are now vanished!");
        
        // Log vanish action
        plugin.getLogger().info("Player " + player.getName() + " has vanished");
    }

    private void unvanish(Player player) {
        plugin.getLogger().info("Unvanishing player: " + player.getName());
        vanishedPlayers.remove(player.getUniqueId());
        
        // Persist immediately to disk
        if (plugin.getPlayerStateManager() != null) {
            plugin.getPlayerStateManager().saveToggle(player.getUniqueId(), "vanish", false);
        }
        
        // Show player to all online players
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.showPlayer(plugin, player);
        }
        
        // Restore item pickup
        plugin.getVanishManager().setCanPickupItems(player, true);
        
        // Send fake join message
        sendFakeJoinMessage(player);
        
        // Hide vanish scoreboard
        plugin.getVanishManager().hideVanishScoreboard(player);
        
        player.sendMessage(ChatColor.RED + "You are no longer vanished!");
        
        // Log unvanish action
        plugin.getLogger().info("Player " + player.getName() + " has unvanished");
    }

    private void sendFakeLeaveMessage(Player player) {
        plugin.getLogger().info("Sending fake leave for " + player.getName());
        
        // Broadcast the in-game leave message through the channel system
        String leaveText = null;
        if (plugin.getConfig().getBoolean("join-leave.enabled", true)) {
            String leaveMessage = plugin.getConfig().getString("join-leave.leave-message", "&e{player} left the game");
            leaveMessage = PlaceholderAPI.setPlaceholders(player, leaveMessage);
            leaveMessage = leaveMessage.replace("{player}", player.getName());
            leaveMessage = leaveMessage.replace("{displayname}", player.getDisplayName());
            leaveText = leaveMessage;
            broadcastToGlobalChannel(ChatColor.translateAlternateColorCodes('&', leaveMessage));
        }
        
        // Forward to DiscordSRV if present (let it handle formatting natively)
        if (isDiscordSRVAvailable()) {
            try {
                github.scarsz.discordsrv.DiscordSRV.getPlugin()
                    .sendLeaveMessage(player, leaveText != null ? leaveText : player.getName() + " left the game");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to forward fake leave to DiscordSRV: " + e.getMessage());
            }
            return; // DiscordSRV handles it - don't double-send through our own integration
        }
        
        // Fallback: send through our own Discord integration
        DiscordIntegration discord = plugin.getDiscordIntegration();
        if (discord != null && discord.isEnabled() &&
            plugin.getConfig().getBoolean("discord.events.broadcast-leaves", true)) {
            
            if (plugin.getConfig().getBoolean("discord.events.use-embeds", true)) {
                discord.sendPlayerLeaveEmbed(player);
            } else {
                String discordMessage = plugin.getConfig().getString("discord.formats.leave", "{player} left the game");
                discordMessage = discordMessage.replace("{player}", player.getName());
                discord.sendToGlobalChat(discordMessage);
            }
        }
    }

    private void sendFakeJoinMessage(Player player) {
        plugin.getLogger().info("Sending fake join for " + player.getName());
        
        // Broadcast the in-game join message through the channel system
        String joinText = null;
        if (plugin.getConfig().getBoolean("join-leave.enabled", true)) {
            String joinMessage = plugin.getConfig().getString("join-leave.join-message", "&e{player} joined the game");
            joinMessage = PlaceholderAPI.setPlaceholders(player, joinMessage);
            joinMessage = joinMessage.replace("{player}", player.getName());
            joinMessage = joinMessage.replace("{displayname}", player.getDisplayName());
            joinText = joinMessage;
            broadcastToGlobalChannel(ChatColor.translateAlternateColorCodes('&', joinMessage));
        }
        
        // Forward to DiscordSRV if present (let it handle formatting natively)
        if (isDiscordSRVAvailable()) {
            try {
                github.scarsz.discordsrv.DiscordSRV.getPlugin()
                    .sendJoinMessage(player, joinText != null ? joinText : player.getName() + " joined the game");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to forward fake join to DiscordSRV: " + e.getMessage());
            }
            return; // DiscordSRV handles it - don't double-send through our own integration
        }
        
        // Fallback: send through our own Discord integration
        DiscordIntegration discord2 = plugin.getDiscordIntegration();
        if (discord2 != null && discord2.isEnabled() &&
            plugin.getConfig().getBoolean("discord.events.broadcast-joins", true)) {
            
            if (plugin.getConfig().getBoolean("discord.events.use-embeds", true)) {
                discord2.sendPlayerJoinEmbed(player);
            } else {
                String discordMessage = plugin.getConfig().getString("discord.formats.join", "{player} joined the game");
                discordMessage = discordMessage.replace("{player}", player.getName());
                discord2.sendToGlobalChat(discordMessage);
            }
        }
    }

    public boolean isVanished(Player player) {
        return vanishedPlayers.contains(player.getUniqueId());
    }

    /**
     * Silently vanish a player without broadcasting fake leave messages.
     * Used by state persistence to restore vanish on login.
     */
    public void silentVanish(Player player) {
        vanishedPlayers.add(player.getUniqueId());
        
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.equals(player)) continue;
            if (!onlinePlayer.hasPermission("qol.vanish.see")
                    || !LevelManager.canObserve(onlinePlayer, player, "vanish")) {
                onlinePlayer.hidePlayer(plugin, player);
            }
        }
        
        plugin.getVanishManager().setCanPickupItems(player, false);
        plugin.getVanishManager().showVanishScoreboard(player);
        
        plugin.getLogger().info("Restored vanish for " + player.getName());
    }

    public Set<UUID> getVanishedPlayers() {
        return new HashSet<>(vanishedPlayers);
    }
    
    public void removeVanishedPlayer(Player player) {
        vanishedPlayers.remove(player.getUniqueId());
    }

    /**
     * Route a message through the channel system, matching how PlayerEventListener
     * sends join/leave messages so they appear consistently in-game.
     */
    private void broadcastToGlobalChannel(String message) {
        ChannelManager channelManager = plugin.getChannelManager();
        if (channelManager == null) {
            plugin.getServer().broadcastMessage(message);
            return;
        }
        Channel globalChannel = channelManager.getChannel("global");
        if (globalChannel != null) {
            for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
                Channel playerChannel = channelManager.getPlayerChannel(onlinePlayer);
                if (playerChannel != null && (playerChannel.getName().equals("global")
                        || plugin.getConfig().getBoolean("broadcast-join-leave-to-all-channels", true))) {
                    onlinePlayer.sendMessage(message);
                }
            }
        } else {
            plugin.getServer().broadcastMessage(message);
        }
    }

    /**
     * Check if DiscordSRV is present and enabled on this server.
     */
    private boolean isDiscordSRVAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("DiscordSRV") != null;
    }

    /**
     * Check if DiscordSRV is present and the config says to let it handle events.
     */
    private boolean isDiscordSRVHandlingEvents() {
        if (!plugin.getConfig().getBoolean("discord.events.skip-if-discordsrv", true)) {
            return false;
        }
        return plugin.getServer().getPluginManager().getPlugin("DiscordSRV") != null;
    }
}
