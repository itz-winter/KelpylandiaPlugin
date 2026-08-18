package com.kelpwing.kelpylandiaplugin.listeners;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.integrations.DiscordIntegration;
import com.kelpwing.kelpylandiaplugin.chat.ChannelManager;
import com.kelpwing.kelpylandiaplugin.chat.Channel;
import com.kelpwing.kelpylandiaplugin.economy.EconomyManager;
import com.kelpwing.kelpylandiaplugin.kits.KitManager;
import com.kelpwing.kelpylandiaplugin.moderation.commands.VanishCommand;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.Statistic;

public class PlayerEventListener implements Listener {

    private final KelpylandiaPlugin plugin;
    private final ChannelManager channelManager;

    public PlayerEventListener(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
        this.channelManager = plugin.getChannelManager();
    }

    /** Fetch lazily — DiscordIntegration is initialized after listeners are registered. */
    private DiscordIntegration getDiscord() {
        return plugin.getDiscordIntegration();
    }

    //  DiscordSRV silent-event metadata 
    // DiscordSRV checks for player metadata "DiscordSRV:silentjoin" and
    // "DiscordSRV:silentquit" to decide whether to skip its own join/leave
    // messages.  We tag vanished players before DiscordSRV's MONITOR listener
    // fires, and explicitly remove the tag for non-vanished players to avoid
    // stale metadata from persisting.
    //
    // NOTE: silentjoin metadata is set in chat.listeners.PlayerListener (LOW)
    // because that is where restoreState() runs and vanish status is resolved.
    // silentquit is set here at LOWEST because the player is already vanished
    // when they quit.

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuitEarly(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        VanishCommand vc = plugin.getVanishCommand();
        boolean vanished = vc != null && vc.isVanished(player);

        if (vanished) {
            player.setMetadata("DiscordSRV:silentquit",
                    new FixedMetadataValue(plugin, true));
        } else {
            player.removeMetadata("DiscordSRV:silentquit", plugin);
        }
    }

    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Skip vanished players — VanishCommand fires fake join events for DiscordSRV;
        // VanishCommand handles the in-game broadcast itself, so we must not duplicate it.
        VanishCommand vc = plugin.getVanishCommand();
        if (vc != null && vc.isVanished(player)) {
            // Still suppress default join message
            if (plugin.getConfig().getBoolean("join-leave.hide-default", true)) {
                event.setJoinMessage(null);
            }
            return;
        }
        
        // Handle custom join messages
        if (plugin.getConfig().getBoolean("join-leave.enabled", true)) {
            // Hide default message
            if (plugin.getConfig().getBoolean("join-leave.hide-default", true)) {
                event.setJoinMessage(null);
            }
            
            // Send custom join message to global channel
            String joinMessage = plugin.getConfig().getString("join-leave.join-message", "&e{player} &ajoined the server!");
            joinMessage = joinMessage.replace("{player}", player.getName());
            joinMessage = joinMessage.replace("{displayname}", player.getDisplayName());
            
            // Route through global chat channel
            sendToGlobalChannel(org.bukkit.ChatColor.translateAlternateColorCodes('&', joinMessage));
        }
        
        // Handle Discord broadcasting (skip if DiscordSRV handles it)
        DiscordIntegration discord = getDiscord();
        if (discord != null && discord.isEnabled() && !isDiscordSRVHandlingEvents()) {
            // Broadcast to global Discord chat if enabled
            if (plugin.getConfig().getBoolean("discord.events.broadcast-joins", true)) {
                if (plugin.getConfig().getBoolean("discord.events.use-embeds", true)) {
                    discord.sendPlayerJoinEmbed(player);
                } else {
                    String discordMessage = plugin.getConfig().getString("discord.formats.join", "**{player}** joined Kelpy Land!");
                    discordMessage = discordMessage.replace("{player}", player.getName());
                    discord.sendToGlobalChat(discordMessage);
                }
            }
            
            // Send to console channel if enabled
            if (plugin.getConfig().getBoolean("discord.events.console-logging", true)) {
                String consoleMessage = String.format("Player `%s` joined the server (IP: %s)", 
                    player.getName(), 
                    player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "Unknown");
                discord.sendToConsole(consoleMessage);
            }
        }

        // First-ever join: create economy account
        if (!player.hasPlayedBefore()) {
            EconomyManager eco = plugin.getEconomyManager();
            if (eco != null && eco.isEnabled()) {
                eco.createAccount(player.getUniqueId());
            }
        }

        // Give any first-join kits the player hasn't claimed yet (runs on every join
        // so existing players who missed a kit when it was added still receive it).
        KitManager km = plugin.getKitManager();
        if (km != null) {
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    km.giveFirstJoinKits(player);
                }
            }, 5L);  // 5-tick delay to let the player fully load
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Skip vanished players — VanishCommand fires fake quit events for DiscordSRV,
        // and VanishListener handles suppressing the real quit when a vanished player logs out.
        VanishCommand vc = plugin.getVanishCommand();
        if (vc != null && vc.isVanished(player)) {
            // Suppress default quit message
            if (plugin.getConfig().getBoolean("join-leave.hide-default", true)) {
                event.setQuitMessage(null);
            }
            return;
        }
        
        // Handle custom leave messages
        if (plugin.getConfig().getBoolean("join-leave.enabled", true)) {
            // Hide default message
            if (plugin.getConfig().getBoolean("join-leave.hide-default", true)) {
                event.setQuitMessage(null);
            }
            
            // Send custom leave message to global channel
            String leaveMessage = plugin.getConfig().getString("join-leave.leave-message", "&e{player} &cleft the server!");
            leaveMessage = leaveMessage.replace("{player}", player.getName());
            leaveMessage = leaveMessage.replace("{displayname}", player.getDisplayName());
            
            // Route through global chat channel
            sendToGlobalChannel(org.bukkit.ChatColor.translateAlternateColorCodes('&', leaveMessage));
        }
        
        // Handle Discord broadcasting (skip if DiscordSRV handles it)
        DiscordIntegration discordQuit = getDiscord();
        if (discordQuit != null && discordQuit.isEnabled() && !isDiscordSRVHandlingEvents()) {
            // Broadcast to global Discord chat if enabled
            if (plugin.getConfig().getBoolean("discord.events.broadcast-leaves", true)) {
                if (plugin.getConfig().getBoolean("discord.events.use-embeds", true)) {
                    discordQuit.sendPlayerLeaveEmbed(player);
                } else {
                    String discordMessage = plugin.getConfig().getString("discord.formats.leave", "**{player}** left Kelpy Land!");
                    discordMessage = discordMessage.replace("{player}", player.getName());
                    discordQuit.sendToGlobalChat(discordMessage);
                }
            }
            
            // Send to console channel if enabled
            if (plugin.getConfig().getBoolean("discord.events.console-logging", true)) {
                String consoleMessage = String.format("Player `%s` left the server", player.getName());
                discordQuit.sendToConsole(consoleMessage);
            }
        }
    }

    // Advancement handling is in AdvancementListener (vanish suppression + Discord).

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStatisticIncrement(PlayerStatisticIncrementEvent event) {
        Player player = event.getPlayer();
        Statistic statistic = event.getStatistic();
        
        // Skip vanished players
        VanishCommand vc2 = plugin.getVanishCommand();
        if (vc2 != null && vc2.isVanished(player)) {
            return;
        }
        
        // Handle certain milestone achievements
        DiscordIntegration discord = getDiscord();
        if (discord != null && discord.isEnabled() && 
            plugin.getConfig().getBoolean("discord.events.broadcast-achievements", true)) {
            
            String achievementMessage = null;
            
            // Check for specific milestone achievements
            switch (statistic) {
                case PLAYER_KILLS:
                    if (event.getNewValue() == 1) {
                        achievementMessage = "First Player Kill";
                    } else if (event.getNewValue() % 10 == 0) {
                        achievementMessage = event.getNewValue() + " Player Kills";
                    }
                    break;
                case MOB_KILLS:
                    if (event.getNewValue() == 100) {
                        achievementMessage = "Monster Hunter (100 kills)";
                    } else if (event.getNewValue() == 1000) {
                        achievementMessage = "Monster Slayer (1000 kills)";
                    }
                    break;
                case DEATHS:
                    if (event.getNewValue() == 1) {
                        achievementMessage = "First Death";
                    }
                    break;
                case PLAY_ONE_MINUTE:
                    // Convert ticks to hours (20 ticks per second, 3600 seconds per hour)
                    long hours = event.getNewValue() / (20 * 3600);
                    if (hours > 0 && hours % 100 == 0) {
                        achievementMessage = hours + " Hours Played";
                    }
                    break;
                default:
                    break;
            }
            
            if (achievementMessage != null) {
                if (plugin.getConfig().getBoolean("discord.events.use-embeds", true)) {
                    discord.sendAchievementEmbed(player, achievementMessage, 
                        "Milestone achievement completed!");
                } else {
                    // Fallback to text message
                    String discordAchievementMessage = plugin.getConfig().getString("discord.format.achievement-message", 
                        ":trophy: **{player}** has completed the achievement **{achievement}**");
                    discordAchievementMessage = PlaceholderAPI.setPlaceholders(player, discordAchievementMessage);
                    discordAchievementMessage = discordAchievementMessage.replace("{player}", player.getName());
                    discordAchievementMessage = discordAchievementMessage.replace("{displayname}", player.getDisplayName());
                    discordAchievementMessage = discordAchievementMessage.replace("{achievement}", achievementMessage);
                    discord.sendToGlobalChat(discordAchievementMessage);
                }
                
                // Also send to console if enabled
                if (plugin.getConfig().getBoolean("discord.events.console-logging", true)) {
                    String consoleMessage = String.format("Player `%s` achieved: %s", 
                        player.getName(), achievementMessage);
                    discord.sendToConsole(consoleMessage);
                }
            }
        }
    }
    
    private void sendToGlobalChannel(String message) {
        Channel globalChannel = channelManager.getChannel("global");
        if (globalChannel != null) {
            for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
                Channel playerChannel = channelManager.getPlayerChannel(onlinePlayer);
                if (playerChannel != null && (playerChannel.getName().equals("global") || 
                    plugin.getConfig().getBoolean("broadcast-join-leave-to-all-channels", true))) {
                    onlinePlayer.sendMessage(message);
                }
            }
        } else {
            // Fallback to regular broadcast if global channel doesn't exist
            plugin.getServer().broadcastMessage(message);
        }
    }

    /**
     * Check if DiscordSRV is present and the config says to let it handle events.
     * When true, this plugin skips its own Discord join/leave/advancement messages
     * to avoid duplicates.
     */
    private boolean isDiscordSRVHandlingEvents() {
        if (!plugin.getConfig().getBoolean("discord.events.skip-if-discordsrv", true)) {
            return false; // admin explicitly wants this plugin to send even with DiscordSRV
        }
        return plugin.getServer().getPluginManager().getPlugin("DiscordSRV") != null;
    }
}
