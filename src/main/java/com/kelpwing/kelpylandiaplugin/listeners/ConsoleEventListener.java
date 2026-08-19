package com.kelpwing.kelpylandiaplugin.listeners;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.integrations.DiscordIntegration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

public class ConsoleEventListener implements Listener {

    private final KelpylandiaPlugin plugin;

    public ConsoleEventListener(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Fetch lazily - DiscordIntegration is initialized after listeners are registered. */
    private DiscordIntegration getDiscord() {
        return plugin.getDiscordIntegration();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerLoad(ServerLoadEvent event) {
        DiscordIntegration discord = getDiscord();
        if (discord != null && discord.isEnabled() && 
            plugin.getConfig().getBoolean("discord.events.console-logging", true)) {
            
            String message = String.format("Server has finished loading (%s)", event.getType().name());
            discord.sendToConsole(message);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        Plugin enabledPlugin = event.getPlugin();
        
        // Only log non-core plugins and avoid logging our own plugin to prevent spam
        DiscordIntegration discord = getDiscord();
        if (discord != null && discord.isEnabled() && 
            plugin.getConfig().getBoolean("discord.events.console-logging", true) &&
            !enabledPlugin.getName().equals(plugin.getName()) &&
            !isSystemPlugin(enabledPlugin.getName())) {
            
            String message = String.format("Plugin `%s` v%s has been enabled", 
                enabledPlugin.getName(), enabledPlugin.getDescription().getVersion());
            discord.sendToConsole(message);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        Plugin disabledPlugin = event.getPlugin();
        
        // Only log non-core plugins and avoid logging our own plugin
        DiscordIntegration discord = getDiscord();
        if (discord != null && discord.isEnabled() && 
            plugin.getConfig().getBoolean("discord.events.console-logging", true) &&
            !disabledPlugin.getName().equals(plugin.getName()) &&
            !isSystemPlugin(disabledPlugin.getName())) {
            
            String message = String.format("Plugin `%s` has been disabled", disabledPlugin.getName());
            discord.sendToConsole(message);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        // Log important admin commands to console channel
        DiscordIntegration discord = getDiscord();
        if (discord != null && discord.isEnabled() && 
            plugin.getConfig().getBoolean("discord.events.console-logging", true) &&
            !event.isCancelled()) {
            
            String command = event.getMessage().toLowerCase();
            
            // Only log certain administrative commands
            if (isImportantCommand(command)) {
                String message = String.format("Player `%s` executed command: %s", 
                    event.getPlayer().getName(), event.getMessage());
                discord.sendToConsole(message);
            }
        }
    }

    private boolean isSystemPlugin(String pluginName) {
        // System/core plugins that we don't want to spam the console with
        return pluginName.equals("WorldEdit") || 
               pluginName.equals("WorldGuard") ||
               pluginName.equals("LuckPerms") ||
               pluginName.equals("PlaceholderAPI") ||
               pluginName.equals("Vault") ||
               pluginName.startsWith("Paper") ||
               pluginName.startsWith("Spigot");
    }

    private boolean isImportantCommand(String command) {
        // Commands we want to log to the console channel
        return command.startsWith("/op ") ||
               command.startsWith("/deop ") ||
               command.startsWith("/gamemode ") ||
               command.startsWith("/give ") ||
               command.startsWith("/tp ") ||
               command.startsWith("/teleport ") ||
               command.startsWith("/whitelist ") ||
               command.startsWith("/reload") ||
               command.startsWith("/restart") ||
               command.startsWith("/stop") ||
               command.startsWith("/save-") ||
               command.startsWith("/world") ||
               command.startsWith("/perm") ||
               command.startsWith("/lp ") ||
               command.contains("ban") ||
               command.contains("kick") ||
               command.contains("mute") ||
               command.contains("warn");
    }
}
