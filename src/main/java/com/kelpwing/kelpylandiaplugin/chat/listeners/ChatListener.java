package com.kelpwing.kelpylandiaplugin.chat.listeners;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.chat.Channel;
import com.kelpwing.kelpylandiaplugin.chat.ChatFormatUtils;
import com.kelpwing.kelpylandiaplugin.chat.modules.CommandsDisplay;
import com.kelpwing.kelpylandiaplugin.chat.ItemDisplayData;
import com.kelpwing.kelpylandiaplugin.chat.ItemDisplayManager;
import com.kelpwing.kelpylandiaplugin.commands.MsgCommand;
import com.kelpwing.kelpylandiaplugin.integrations.DiscordIntegration;
import com.kelpwing.kelpylandiaplugin.utils.LevelManager;
import com.kelpwing.kelpylandiaplugin.utils.SpyManager;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.ChatColor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ChatListener implements Listener {

    private final KelpylandiaPlugin plugin;

    public ChatListener(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * HIGHEST priority - runs after most other plugins.
     *
     * Handles: whisper redirect, mute check, permission check, channel recipients,
     * chat formatting, [item]/[inv]/[enderchest] replacement, SocialSpy, and
     * Discord relay.  When item-display keywords are present the vanilla chat
     * event is cancelled and JSON components are sent directly to recipients.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // Whisper redirect 
        MsgCommand msgCmd = plugin.getMsgCommand();
        if (msgCmd != null) {
            UUID targetUUID = msgCmd.getWhisperTarget(player.getUniqueId());
            if (targetUUID != null) {
                if (targetUUID.equals(MsgCommand.CONSOLE_UUID)) {
                    event.setCancelled(true);
                    Bukkit.getScheduler().runTask(plugin, () ->
                            msgCmd.sendPrivateMessage(player, Bukkit.getConsoleSender(), message));
                    return;
                }
                Player target = Bukkit.getPlayer(targetUUID);
                if (target != null && target.isOnline()) {
                    event.setCancelled(true);
                    Bukkit.getScheduler().runTask(plugin, () ->
                            msgCmd.sendPrivateMessage(player, target, message));
                    return;
                } else {
                    msgCmd.clearWhisperTarget(player.getUniqueId());
                    player.sendMessage(ChatColor.RED + "Your whisper target is no longer online. Target cleared.");
                }
            }
        }

        // Mute check 
        if (ChatFormatUtils.isPlayerMuted(player)) {
            player.sendMessage(ChatColor.RED + "You are currently muted and cannot speak.");
            event.setCancelled(true);
            return;
        }

        // Channel resolution 
        Channel playerChannel = plugin.getChannelManager().getPlayerChannel(player);
        if (playerChannel == null) {
            playerChannel = plugin.getChannelManager().getDefaultChannel();
        }

        // Permission check 
        if (!ChatFormatUtils.hasPermission(player, playerChannel.getPermission())) {
            player.sendMessage(ChatColor.RED + "You don't have permission to speak in this channel.");
            event.setCancelled(true);
            return;
        }

        // Determine recipients 
        Set<Player> recipients;
        if (!playerChannel.isGlobal()) {
            recipients = new HashSet<>();
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                if (shouldReceiveMessage(online, player, playerChannel)) {
                    recipients.add(online);
                }
            }
        } else {
            recipients = new HashSet<>(event.getRecipients());
        }

        // SocialSpy for non-global channels 
        if (!playerChannel.isGlobal()) {
            SpyManager spyManager = plugin.getSpyManager();
            if (spyManager != null) {
                String spyMsg = ChatColor.DARK_GRAY + "[SS] " + ChatColor.GRAY + "["
                        + playerChannel.getDisplayName() + "] "
                        + player.getName() + ": " + ChatColor.WHITE + message;
                for (UUID spyUUID : spyManager.getSocialSpies()) {
                    if (spyUUID.equals(player.getUniqueId())) continue;
                    Player spy = Bukkit.getPlayer(spyUUID);
                    if (spy != null && spy.isOnline() && !recipients.contains(spy)) {
                        if (!LevelManager.canObserve(spy, player, "socialspy")) continue;
                        spy.sendMessage(spyMsg);
                    }
                }
            }
        }

        // Check for item-display keywords 
        ItemDisplayManager idm = plugin.getItemDisplayManager();
        boolean hasKeywords = idm != null && idm.containsKeyword(message);

        if (hasKeywords) {
            // Cancel the vanilla event - we'll send JSON components ourselves
            event.setCancelled(true);

            // Must build components on the main thread because we access player inventory.
            // AsyncPlayerChatEvent is async, so schedule synchronously.
            final Set<Player> finalRecipients = recipients;
            final Channel finalChannel = playerChannel;
            // idm is non-null here - guarded by hasKeywords = idm != null && ...
            final ItemDisplayManager safeIdm = idm;

            Bukkit.getScheduler().runTask(plugin, () -> {
                // Build the FULL formatted line first (prefix + message), then pass it
                // to buildChatLine so the entire string is parsed in one fromLegacyText()
                // call - this preserves colour inheritance across the whole line (IC approach).
                String fullLine = ChatFormatUtils.formatMessage(plugin, player, finalChannel, message);
                BaseComponent[] components = safeIdm.buildChatLine(player, fullLine);

                // Send to all recipients
                for (Player recipient : finalRecipients) {
                    recipient.spigot().sendMessage(components);
                }

                // Console gets plain text
                plugin.getLogger().info("[" + finalChannel.getName() + "] " + player.getName() + ": " + message);

                // Discord relay
                relayToDiscord(player, message, finalChannel, true);
            });
        } else {
            // No item keywords - check for clickable [/command] patterns
            String fullLine = ChatFormatUtils.formatMessage(plugin, player, playerChannel, message);

            if (CommandsDisplay.isEnabled()) {
                // Deserialize legacy string to Adventure Component, process, re-serialize to Bungee
                net.kyori.adventure.text.Component lineComponent =
                        LegacyComponentSerializer.legacySection().deserialize(fullLine);
                net.kyori.adventure.text.Component processed = CommandsDisplay.process(lineComponent);
                // Compare serialized legacy strings - object equality is unreliable because
                // ComponentReplacing always flattens/compacts the tree even when nothing matched.
                String serializedBefore = LegacyComponentSerializer.legacySection().serialize(lineComponent);
                String serializedAfter  = LegacyComponentSerializer.legacySection().serialize(processed);
                boolean changed = !serializedAfter.equals(serializedBefore);
                if (changed) {
                    event.setCancelled(true);
                    final Set<Player> cmdRecipients = recipients;
                    final Channel cmdChannel = playerChannel;
                    // Convert via JSON round-trip (Adventure → Minecraft JSON ↔ BungeeCord JSON)
                    // so that click/hover events on the command box are preserved.
                    final String processedJson =
                            net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
                                    .gson().serialize(processed);
                    final net.md_5.bungee.api.chat.BaseComponent[] cmdComponents =
                            net.md_5.bungee.chat.ComponentSerializer.parse(processedJson);

                    for (Player recipient : cmdRecipients) {
                        recipient.spigot().sendMessage(cmdComponents);
                    }

                    plugin.getLogger().info("[" + cmdChannel.getName() + "] " + player.getName() + ": " + message);
                    relayToDiscord(player, message, cmdChannel, false);
                } else {
                    // Plain text - let Bukkit dispatch normally
                    event.setFormat(fullLine.replace("%", "%%"));

                    if (!playerChannel.isGlobal()) {
                        event.getRecipients().clear();
                        event.getRecipients().addAll(recipients);
                    }

                    plugin.getLogger().info("[" + playerChannel.getName() + "] " + player.getName() + ": " + message);
                    relayToDiscord(player, message, playerChannel, false);
                }
            } else {
                // Commands module disabled - plain text
                event.setFormat(fullLine.replace("%", "%%"));

                if (!playerChannel.isGlobal()) {
                    event.getRecipients().clear();
                    event.getRecipients().addAll(recipients);
                }

                plugin.getLogger().info("[" + playerChannel.getName() + "] " + player.getName() + ": " + message);
                relayToDiscord(player, message, playerChannel, false);
            }
        }
    }

    // Discord relay helper 

    private void relayToDiscord(Player player, String rawMessage, Channel channel, boolean hasKeywords) {
        if (!channel.isDiscordEnabled()) return;

        DiscordIntegration discord = plugin.getDiscordIntegration();
        if (discord == null || !discord.isEnabled()) return;

        if (hasKeywords) {
            ItemDisplayManager idm = plugin.getItemDisplayManager();
            if (idm != null) {
                java.util.List<ItemDisplayData> embedData = idm.buildDiscordData(player, rawMessage);
                if (!embedData.isEmpty()) {
                    String surrounding = embedData.get(0).getSurroundingText();
                    discord.sendChatItemEmbed(player, surrounding, embedData, channel.getDiscordChannel());
                    return;
                }
            }
        }

        // Plain text fallback
        discord.sendChatMessage(player, rawMessage, channel.getDiscordChannel());
    }

    // Recipient filter 

    private boolean shouldReceiveMessage(Player recipient, Player sender, Channel channel) {
        if (!ChatFormatUtils.hasPermission(recipient, channel.getPermission())) {
            return false;
        }
        if (!channel.isWorldAllowed(recipient.getWorld().getName())) {
            return false;
        }
        if (channel.isRangeEnabled()) {
            if (!recipient.getWorld().equals(sender.getWorld())) {
                return false;
            }
            double distance = recipient.getLocation().distance(sender.getLocation());
            return distance <= channel.getRange();
        }
        return true;
    }
}
