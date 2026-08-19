package com.kelpwing.kelpylandiaplugin.integrations;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.economy.DynamicPricingEngine;
import com.kelpwing.kelpylandiaplugin.economy.EconomyManager;
import com.kelpwing.kelpylandiaplugin.economy.commands.SellCommand;
import com.kelpwing.kelpylandiaplugin.moderation.models.Punishment;
import com.kelpwing.kelpylandiaplugin.utils.DurationParser;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import com.kelpwing.kelpylandiaplugin.utils.VersionHelper;

import net.dv8tion.jda.api.entities.Message;
import javax.annotation.Nonnull;
import java.awt.Color;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class DiscordIntegration extends ListenerAdapter {
    private final KelpylandiaPlugin plugin;
    private JDA jda;
    private boolean enabled;
    private String punishmentChannelId;
    private String chatChannelId;
    private String serverChannelId;
    private String consoleChannelId;
    private final ConcurrentHashMap<String, String> webhookUrls = new ConcurrentHashMap<>();

    // Rolling console log (DiscordSRV-style queue + drain thread) 
    /**
     * Non-blocking queue of formatted lines waiting to be flushed to Discord.
     * The log4j appender / JUL handler offer() lines here without ever blocking
     * the logging thread.  A single background thread drains this queue every
     * ~1 second and batches all pending lines into a single message edit.
     */
    private final java.util.concurrent.LinkedBlockingQueue<String> pendingConsoleLines
            = new java.util.concurrent.LinkedBlockingQueue<>();
    /** The Discord message that is currently being edited with new lines. */
    private final AtomicReference<Message> consoleCurrentMessage = new AtomicReference<>(null);
    /** Lines accumulated in the current rolling message (used by the drain thread only). */
    private final Deque<String> consoleLineBuffer = new ArrayDeque<>();
    /** Current character length of the rolling message body. */
    private int consoleBufferLen = 0;
    /** Max characters kept in the rolling message before starting a new one. */
    private static final int CONSOLE_MAX_CHARS = 1900;
    /** Background drain thread - null when Discord is disabled. */
    private volatile Thread consoleFlushThread = null;

    // Per-command output capture (for c! prefix replies) 
    /** Lines captured during the active command window, to be replied to the invoker. */
    private final java.util.concurrent.ConcurrentLinkedQueue<String> cmdCaptureLines
            = new java.util.concurrent.ConcurrentLinkedQueue<>();
    /** True while we are collecting output for a command reply. */
    private volatile boolean cmdCaptureActive = false;
    /** The Discord message that issued the command (reply target). */
    private volatile Message cmdCaptureSource = null;
    
    public DiscordIntegration(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
        this.enabled = false;
        
        String token = plugin.getConfig().getString("discord.bot-token");
        this.punishmentChannelId = plugin.getConfig().getString("discord.moderation-channel-id");
        this.chatChannelId = plugin.getConfig().getString("channels.global.discord-channel");
        this.consoleChannelId = plugin.getConfig().getString("discord.console.console-channel-id");
        this.serverChannelId = resolveServerChannelId();
        
        plugin.getLogger().info("Discord Integration Initialization:");
        plugin.getLogger().info("- Bot Token: " + (token != null && !token.equals("your-bot-token-here") ? "✓ Configured" : "✗ Missing/Default"));
        plugin.getLogger().info("- Chat Channel ID: " + (chatChannelId != null && !chatChannelId.isEmpty() ? chatChannelId : "✗ Missing/Default"));
        plugin.getLogger().info("- Server Events Channel ID: " + serverChannelId);
        plugin.getLogger().info("- Console Channel ID: " + (consoleChannelId != null && !consoleChannelId.equals("your-console-channel-id") ? consoleChannelId : "✗ Missing/Default"));
        plugin.getLogger().info("- Moderation Channel ID: " + (punishmentChannelId != null && !punishmentChannelId.equals("your-moderation-channel-id") ? punishmentChannelId : "✗ Missing/Default"));
        
        if (token != null && !token.equals("your-bot-token-here")) {
            try {
                plugin.getLogger().info("Connecting to Discord...");
                jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
                    .addEventListeners(this)
                    .build();
                jda.awaitReady();
                
                // Register slash commands
                registerSlashCommands();
                
                // Start channel topic updater
                startChannelTopicUpdater();

                // Start the console flush thread (DiscordSRV-style: one thread drains
                // the queue every ~1s, so the logging thread never blocks on REST calls)
                startConsoleFlushThread();
                
                this.enabled = true;
                plugin.getLogger().info("Discord bot connected successfully!");
                plugin.getLogger().info("Discord Integration Status: ✓ ENABLED");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to initialize Discord bot: " + e.getMessage());
                plugin.getLogger().warning("Discord Integration Status: ✗ DISABLED");
                e.printStackTrace();
            }
        } else {
            plugin.getLogger().info("Discord integration disabled - no bot token configured");
            plugin.getLogger().info("Discord Integration Status: ✗ DISABLED (No Token)");
        }
    }
    
    public void sendChatMessage(Player player, String message, String channelId) {
        if (!enabled || channelId == null || channelId.isEmpty()) return;

        // Use webhook for chat messages to show player avatar and name
        sendWebhookMessage(player, message, channelId);
    }

    public void sendWebhookMessage(Player player, String message, String channelId) {
        if (!enabled || channelId == null || channelId.isEmpty()) return;
        if (message == null || message.isBlank()) return;

        // Capture all values on the calling thread before going async
        final String playerName = player.getName();
        final String displayName = player.getDisplayName();
        final String uuidStr = player.getUniqueId().toString();

        final String content = stripSectionCodes(
                plugin.getConfig().getString("discord.formats.minecraft-to-discord", "{message}")
                        .replace("{message}", message)
                        .replace("{player}", playerName)
                        .replace("{displayname}", displayName));

        final String username = stripSectionCodes(
                plugin.getConfig().getString("discord.formats.username-format", "{displayname}")
                        .replace("{player}", playerName)
                        .replace("{displayname}", displayName));

        final String avatarUrl = plugin.getConfig()
                .getString("discord.formats.avatar-url", "https://mc-heads.net/avatar/{uuid}/64")
                .replace("{uuid}", uuidStr)
                .replace("{player}", playerName);

        // Use Bukkit's async scheduler - avoids ForkJoin pool deadlock with JDA's .complete()
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String webhookUrl = getWebhookUrl(channelId);
                if (webhookUrl == null) {
                    // Fallback: plain message via JDA
                    TextChannel ch = jda.getTextChannelById(channelId);
                    if (ch != null) {
                        ch.sendMessage("[" + username + "] " + content).queue(
                                null,
                                e -> plugin.getLogger().warning("Fallback chat send failed: " + e.getMessage()));
                    }
                    return;
                }

                String payload = String.format(
                        "{\"content\":\"%s\",\"username\":\"%s\",\"avatar_url\":\"%s\"}",
                        escapeJson(content),
                        escapeJson(username),
                        avatarUrl);

                sendWebhookRequest(webhookUrl, payload);

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send webhook message: " + e.getMessage());
            }
        });
    }

    /**
     * Sends a chat message via webhook with rich embeds for [item]/[inv]/[enderchest].
     * Must be called from the main thread (player data is captured here).
     */
    public void sendChatItemEmbed(Player player, String surroundingText,
                                   java.util.List<com.kelpwing.kelpylandiaplugin.chat.ItemDisplayData> items,
                                   String channelId) {
        if (!enabled || channelId == null || channelId.isEmpty()) return;

        // --- capture everything on the main thread ---
        final String playerName = player.getName();
        final String displayName = player.getDisplayName();
        final String uuidStr = player.getUniqueId().toString();

        final String username = stripSectionCodes(
                plugin.getConfig().getString("discord.formats.username-format", "{displayname}")
                        .replace("{player}", playerName)
                        .replace("{displayname}", displayName));

        final String avatarUrl = plugin.getConfig()
                .getString("discord.formats.avatar-url", "https://mc-heads.net/avatar/{uuid}/64")
                .replace("{uuid}", uuidStr)
                .replace("{player}", playerName);

        final String contentText = stripSectionCodes(
                plugin.getConfig().getString("discord.formats.minecraft-to-discord", "{message}")
                        .replace("{message}", surroundingText)
                        .replace("{player}", playerName)
                        .replace("{displayname}", displayName));

        // Deep-copy the list so it's safe off-thread
        final java.util.List<com.kelpwing.kelpylandiaplugin.chat.ItemDisplayData> safeItems =
                new java.util.ArrayList<>(items);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Render tooltip images for each item 
                java.util.List<java.io.InputStream> tooltipImages = new java.util.ArrayList<>();
                java.util.List<String> tooltipFilenames = new java.util.ArrayList<>();
                int imgIdx = 0;
                for (com.kelpwing.kelpylandiaplugin.chat.ItemDisplayData data : safeItems) {
                    java.io.InputStream img = null;
                    if (data.getType() == com.kelpwing.kelpylandiaplugin.chat.ItemDisplayData.DisplayType.ITEM) {
                        img = com.kelpwing.kelpylandiaplugin.chat.TooltipImageRenderer.renderTooltip(data);
                    } else {
                        img = com.kelpwing.kelpylandiaplugin.chat.TooltipImageRenderer.renderInventoryTooltip(data);
                    }
                    if (img != null) {
                        String filename = "tooltip_" + imgIdx + ".png";
                        tooltipImages.add(img);
                        tooltipFilenames.add(filename);
                    }
                    imgIdx++;
                }

                // Try JDA-based send with tooltip image attachments 
                if (!tooltipImages.isEmpty()) {
                    TextChannel ch = jda.getTextChannelById(channelId);
                    if (ch != null) {
                        // IC dsrv embed structure:
                        //  author  = "PlayerName's Item" / "…'s Inventory" / "…'s Ender Chest"
                        //  title   = item name / inventory label
                        //  color   = rarity colour (ITEM) or fixed (INV/EC)
                        //  thumbnail (small, top-right) = item icon URL  [ITEM only]
                        //  image   (large, bottom)      = rendered tooltip PNG
                        java.util.List<net.dv8tion.jda.api.entities.MessageEmbed> embeds = new java.util.ArrayList<>();
                        int embedIdx = 0;
                        for (com.kelpwing.kelpylandiaplugin.chat.ItemDisplayData data : safeItems) {
                            EmbedBuilder eb = new EmbedBuilder();
                            switch (data.getType()) {
                                case ITEM:
                                    // Author: "PlayerName's Item" with avatar
                                    eb.setAuthor(username + "'s Item", null, avatarUrl);
                                    eb.setTitle(stripSectionCodes(data.getItemName()));
                                    // Embed colour = rarity (custom name always gold)
                                    eb.setColor(rarityEmbedColor(data));
                                    // Thumbnail = small item icon beside the tooltip image
                                    String iconUrl = data.getItemIconUrl();
                                    if (iconUrl != null) eb.setThumbnail(iconUrl);
                                    break;
                                case INVENTORY:
                                    eb.setAuthor(username + "'s Inventory", null, avatarUrl);
                                    eb.setTitle(playerName + "'s Inventory");
                                    eb.setColor(new java.awt.Color(0xAAAAAA));
                                    break;
                                case ENDER_CHEST:
                                    eb.setAuthor(username + "'s Ender Chest", null, avatarUrl);
                                    eb.setTitle(playerName + "'s Ender Chest");
                                    eb.setColor(new java.awt.Color(0x4D0080));
                                    break;
                            }
                            // Large image = rendered tooltip/inventory PNG
                            if (embedIdx < tooltipFilenames.size()) {
                                eb.setImage("attachment://" + tooltipFilenames.get(embedIdx));
                            }
                            embeds.add(eb.build());
                            embedIdx++;
                        }

                        // Send embeds + file attachments - no separate message content (IC sends embeds only)
                        net.dv8tion.jda.api.requests.restaction.MessageCreateAction action =
                                ch.sendMessageEmbeds(embeds);
                        for (int i = 0; i < tooltipImages.size(); i++) {
                            action = action.addFiles(net.dv8tion.jda.api.utils.FileUpload.fromData(
                                    tooltipImages.get(i), tooltipFilenames.get(i)));
                        }
                        action.queue();
                        return;
                    }
                }

                // Fallback: webhook without tooltip images 
                String webhookUrl = getWebhookUrl(channelId);
                if (webhookUrl == null) {
                    TextChannel ch = jda.getTextChannelById(channelId);
                    if (ch != null) ch.sendMessage("[" + username + "] " + contentText).queue();
                    return;
                }

                StringBuilder embedsJson = new StringBuilder("[");
                boolean first = true;

                for (com.kelpwing.kelpylandiaplugin.chat.ItemDisplayData data : safeItems) {
                    if (!first) embedsJson.append(",");
                    first = false;
                    embedsJson.append(buildItemEmbedJson(data));
                }
                embedsJson.append("]");

                String payload = String.format(
                        "{\"content\":\"%s\",\"username\":\"%s\",\"avatar_url\":\"%s\",\"embeds\":%s}",
                        escapeJson(contentText),
                        escapeJson(username),
                        avatarUrl,
                        embedsJson);

                sendWebhookRequest(webhookUrl, payload);

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send chat item embed: " + e.getMessage());
            }
        });
    }

    /**
     * Builds a single Discord embed JSON object for an {@link com.kelpwing.kelpylandiaplugin.chat.ItemDisplayData}.
     */
    private String buildItemEmbedJson(com.kelpwing.kelpylandiaplugin.chat.ItemDisplayData data) {
        StringBuilder sb = new StringBuilder("{");

        switch (data.getType()) {
            case ITEM:
                sb.append("\"title\":\"").append(escapeJson(data.getItemName())).append("\"");
                sb.append(",\"color\":").append(data.hasCustomName() ? 16750848 : 5635925); // orange for custom, teal for normal

                // Thumbnail - item icon from Minecraft Wiki
                String iconUrl = data.getItemIconUrl();
                if (iconUrl != null) {
                    sb.append(",\"thumbnail\":{\"url\":\"").append(escapeJson(iconUrl)).append("\"}");
                }

                // Fields
                sb.append(",\"fields\":[");
                boolean fieldFirst = true;

                if (data.getAmount() > 1) {
                    fieldFirst = false;
                    sb.append("{\"name\":\"Amount\",\"value\":\"")
                      .append(data.getAmount())
                      .append("\",\"inline\":true}");
                }

                if (!data.getEnchantments().isEmpty()) {
                    if (!fieldFirst) sb.append(",");
                    fieldFirst = false;
                    StringBuilder enchLines = new StringBuilder();
                    for (java.util.Map.Entry<String, Integer> e : data.getEnchantments().entrySet()) {
                        if (enchLines.length() > 0) enchLines.append("\n");
                        enchLines.append(e.getKey());
                        if (e.getValue() > 1) enchLines.append(" ").append(e.getValue());
                    }
                    sb.append("{\"name\":\"Enchantments\",\"value\":\"")
                      .append(escapeJson(enchLines.toString()))
                      .append("\",\"inline\":true}");
                }

                if (!data.getLore().isEmpty()) {
                    if (!fieldFirst) sb.append(",");
                    StringBuilder loreLines = new StringBuilder();
                    for (String line : data.getLore()) {
                        if (loreLines.length() > 0) loreLines.append("\n");
                        loreLines.append(line);
                    }
                    sb.append("{\"name\":\"Lore\",\"value\":\"")
                      .append(escapeJson(loreLines.toString()))
                      .append("\",\"inline\":false}");
                }

                sb.append("]");
                break;

            case INVENTORY:
            case ENDER_CHEST:
                boolean isEC = data.getType() == com.kelpwing.kelpylandiaplugin.chat.ItemDisplayData.DisplayType.ENDER_CHEST;
                String title = isEC ? "\uD83D\uDD2E Ender Chest" : "\uD83C\uDF92 Inventory";
                sb.append("\"title\":\"").append(escapeJson(title)).append("\"");
                sb.append(",\"color\":").append(isEC ? 5046016 : 11184810); // dark purple / grey

                // Slots field
                sb.append(",\"fields\":[");
                sb.append("{\"name\":\"Slots Used\",\"value\":\"")
                  .append(data.getUsedSlots()).append("/").append(data.getTotalSlots())
                  .append("\",\"inline\":true}");

                if (!data.getTopItems().isEmpty()) {
                    sb.append(",{\"name\":\"Top Items\",\"value\":\"");
                    StringBuilder topLines = new StringBuilder();
                    for (String item : data.getTopItems()) {
                        if (topLines.length() > 0) topLines.append("\n");
                        topLines.append(item);
                    }
                    sb.append(escapeJson(topLines.toString()));
                    sb.append("\",\"inline\":false}");
                }

                sb.append("]");

                // Thumbnail - chest icon
                if (isEC) {
                    sb.append(",\"thumbnail\":{\"url\":\"https://minecraft.wiki/images/Invicon_Ender_Chest.png\"}");
                } else {
                    sb.append(",\"thumbnail\":{\"url\":\"https://minecraft.wiki/images/Invicon_Chest.png\"}");
                }
                break;
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * Escapes Discord Markdown special characters.
     */
    private static String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("*", "\\*")
                   .replace("_", "\\_")
                   .replace("~", "\\~")
                   .replace("`", "\\`")
                   .replace("|", "\\|")
                   .replace(">", "\\>");
    }

    public void sendPunishmentMessage(Object punishment) {
        plugin.getLogger().info("[DISCORD DEBUG] sendPunishmentMessage called");
        plugin.getLogger().info("[DISCORD DEBUG] enabled: " + enabled);
        plugin.getLogger().info("[DISCORD DEBUG] punishmentChannelId: " + punishmentChannelId);
        
        if (!enabled) {
            plugin.getLogger().warning("[DISCORD DEBUG] Discord integration not enabled");
            return;
        }
        
        if (punishmentChannelId == null) {
            plugin.getLogger().warning("[DISCORD DEBUG] Punishment channel ID is null");
            return;
        }
        
        if (punishmentChannelId.equals("your-moderation-channel-id")) {
            plugin.getLogger().warning("[DISCORD DEBUG] Punishment channel ID is still default placeholder! Please configure discord.moderation-channel-id in config.yml");
            return;
        }
        
        plugin.getLogger().info("[DISCORD DEBUG] Attempting to get Discord channel...");
        TextChannel channel = jda.getTextChannelById(punishmentChannelId);
        if (channel == null) {
            plugin.getLogger().warning("[DISCORD DEBUG] Discord punishment channel not found with ID: " + punishmentChannelId);
            plugin.getLogger().warning("[DISCORD DEBUG] Available channels: " + (jda != null && jda.getTextChannels() != null ? 
                jda.getTextChannels().stream().map(c -> c.getId() + ":" + c.getName()).collect(java.util.stream.Collectors.joining(", ")) : "None"));
            return;
        }
        
        plugin.getLogger().info("[DISCORD DEBUG] Found Discord channel: " + channel.getName());

        if (punishment instanceof Punishment) {
            Punishment p = (Punishment) punishment;
            plugin.getLogger().info("[DISCORD DEBUG] Sending punishment embed for: " + p.getPlayer());
            sendPunishmentEmbed(p);
        } else {
            plugin.getLogger().info("[DISCORD DEBUG] Sending fallback punishment embed");
            // Fallback for unknown punishment objects
            EmbedBuilder embed = new EmbedBuilder();
            embed.setColor(getColorFromConfig("discord.embeds.punishment-color", Color.RED));
            embed.setTitle("Moderation Action");
            embed.setDescription("A punishment action has been taken on the server.");
            embed.setTimestamp(Instant.now());
            embed.setFooter("KelpylandiaPlugin", null);
            
            channel.sendMessageEmbeds(embed.build()).queue(
                success -> plugin.getLogger().info("[DISCORD DEBUG] Fallback punishment embed sent successfully!"),
                error -> plugin.getLogger().warning("[DISCORD DEBUG] Failed to send punishment to Discord: " + error.getMessage())
            );
        }
    }
    
    private void sendPunishmentEmbed(Punishment punishment) {
        plugin.getLogger().info("[DISCORD DEBUG] sendPunishmentEmbed called");
        
        if (!enabled || punishmentChannelId == null) {
            plugin.getLogger().warning("[DISCORD DEBUG] Cannot send punishment embed - enabled: " + enabled + ", channelId: " + punishmentChannelId);
            return;
        }
        
        TextChannel channel = jda.getTextChannelById(punishmentChannelId);
        if (channel == null) {
            plugin.getLogger().warning("[DISCORD DEBUG] Discord punishment channel not found: " + punishmentChannelId);
            return;
        }
        
        plugin.getLogger().info("[DISCORD DEBUG] Creating punishment embed for action: " + punishment.getAction());

        // Create embed with consistent formatting style
        EmbedBuilder embed = new EmbedBuilder();
        
        // Set color based on punishment type
        Color embedColor = getColorForAction(punishment.getAction());
        embed.setColor(embedColor);
        
        // Set title and description in the same style as other embeds
        String title = formatActionTitle(punishment.getAction());
        embed.setTitle(stripSectionCodes(title));
        embed.setAuthor(null, null, null);
        
        // Add fields for details
        embed.addField("Player", stripSectionCodes(punishment.getPlayer()), true);
        embed.addField("Staff", stripSectionCodes(punishment.getPunisher()), true);
        embed.addField("Reason", stripSectionCodes(punishment.getReason() != null ? punishment.getReason() : "No reason specified"), false);
        
        // Only show duration for punishment actions, not unpunishment actions
        String action = punishment.getAction().toUpperCase();
        if (!action.startsWith("UN")) {
            if (punishment.getDuration() > 0) {
                // Special handling for warnings - always show "30 days"
                if (punishment.getAction() != null && punishment.getAction().equalsIgnoreCase("WARN")) {
                    embed.addField("Duration", "30 days", true);
                } else {
                    embed.addField("Duration", stripSectionCodes(formatDuration(punishment.getDuration())), true);
                }
            } else if (action.equals("BAN") || action.equals("IPBAN")) {
                embed.addField("Duration", "Permanent", true);
            }
        }
        
        embed.setTimestamp(Instant.now());
        embed.setFooter("KelpylandiaPlugin", null);
        
        plugin.getLogger().info("[DISCORD DEBUG] Sending punishment embed to channel: " + channel.getName());
        channel.sendMessageEmbeds(embed.build()).queue(
            success -> plugin.getLogger().info("[DISCORD DEBUG] Punishment embed sent successfully!"),
            error -> plugin.getLogger().warning("[DISCORD DEBUG] Failed to send punishment to Discord: " + error.getMessage())
        );
    }
    
    public void sendPunishmentMessage(String action, String staffName, String playerName, String reason, long duration) {
        // Create a Punishment object from the parameters for consistency
        Punishment punishment = new Punishment(playerName, staffName, reason, action, duration);
        sendPunishmentMessage(punishment);
    }
    
    private Color getColorForAction(String action) {
        // Use configured punishment color from config
        Color configuredColor = getColorFromConfig("discord.embeds.punishment-color", null);
        if (configuredColor != null) {
            return configuredColor;
        }
        
        // Fallback to action-specific colors
        switch (action.toUpperCase()) {
            case "BAN":
            case "IPBAN":
                return Color.RED;
            case "KICK":
                return Color.ORANGE;
            case "MUTE":
                return Color.YELLOW;
            case "WARN":
                return Color.CYAN;
            case "UNBAN":
            case "UNMUTE":
            case "UNWARN":
                return Color.GREEN;
            default:
                return Color.GRAY;
        }
    }
    
    private String formatActionTitle(String action) {
        return action.substring(0, 1).toUpperCase() + action.substring(1).toLowerCase() + " Issued";
    }
    
    private String formatDuration(long duration) {
        if (duration <= 0) return "Permanent";
        
        // Convert milliseconds to seconds
        long totalSeconds = duration / 1000;
        
        // Handle very large durations (likely data corruption or invalid parsing)
        if (totalSeconds > 31536000000L) { // More than 1000 years
            plugin.getLogger().warning("Duration value seems corrupted: " + duration + " ms (" + totalSeconds + " seconds). Defaulting to permanent.");
            return "Permanent";
        }
        
        // Calculate time units
        long years = totalSeconds / (365 * 24 * 60 * 60);
        totalSeconds %= (365 * 24 * 60 * 60);
        
        long weeks = totalSeconds / (7 * 24 * 60 * 60);
        totalSeconds %= (7 * 24 * 60 * 60);
        
        long days = totalSeconds / (24 * 60 * 60);
        totalSeconds %= (24 * 60 * 60);
        
        long hours = totalSeconds / (60 * 60);
        totalSeconds %= (60 * 60);
        
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        
        // Build duration string with proper priority (largest unit first)
        if (years > 0) {
            return years + " year" + (years > 1 ? "s" : "");
        } else if (weeks > 0) {
            return weeks + " week" + (weeks > 1 ? "s" : "");
        } else if (days > 0) {
            return days + " day" + (days > 1 ? "s" : "");
        } else if (hours > 0) {
            return hours + " hour" + (hours > 1 ? "s" : "");
        } else if (minutes > 0) {
            return minutes + " minute" + (minutes > 1 ? "s" : "");
        } else {
            return seconds + " second" + (seconds > 1 ? "s" : "");
        }
    }
    
    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        boolean debugRelay = plugin.getConfig().getBoolean("discord.formats.debug-relay", false);

        if (!enabled || event.getAuthor().isBot() || event.getMessage().isWebhookMessage()) return;

        String channelId = event.getChannel().getId();
        String content = event.getMessage().getContentDisplay().trim();
        String username = event.getAuthor().getEffectiveName();

        // c! prefix command (any channel) 
        String cmdPrefix = plugin.getConfig().getString("discord.console.commands.prefix", "c!");
        boolean prefixEnabled = plugin.getConfig().getBoolean("discord.console.commands.prefix-command-enabled", true);

        if (prefixEnabled && content.startsWith(cmdPrefix)) {
            if (!hasConsoleRole(resolveMember(event))) {
                event.getMessage().addReaction(Emoji.fromUnicode("❌")).queue(null, e -> {});
                return;
            }
            String command = content.substring(cmdPrefix.length()).trim();
            if (!command.isEmpty()) {
                dispatchConsoleCommand(command, event, username);
            }
            return;
        }

        // Console channel: full message = command 
        if (channelId.equals(consoleChannelId)) {
            boolean consoleCommandsEnabled = plugin.getConfig().getBoolean("discord.console.commands.enabled", true);
            if (consoleCommandsEnabled) {
                if (!hasConsoleRole(resolveMember(event))) {
                    event.getMessage().addReaction(Emoji.fromUnicode("❌")).queue(null, e -> {});
                    return;
                }
                if (!content.isEmpty()) {
                    dispatchConsoleCommand(content, event, username);
                }
            }
            return;
        }

        // Chat channel: relay to Minecraft 
        if (channelId.equals(chatChannelId)) {
            if (debugRelay) {
                plugin.getLogger().info("Relaying Discord message to Minecraft: " + username + ": " + content);
            }

            // Collect attachments (images, videos, gifs, files) from the Discord message
            List<net.dv8tion.jda.api.entities.Message.Attachment> attachments =
                    event.getMessage().getAttachments();

            String format = plugin.getConfig().getString("discord.formats.discord-to-minecraft",
                    "&9[Discord] &r{user}&r: {message}");
            // For pure-attachment messages (no text), use a blank {message} placeholder -
            // the attachment components are appended separately below.
            String effectiveContent = content.isEmpty() && !attachments.isEmpty() ? "" : content;
            String minecraftMessage = format
                    .replace("{user}", username)
                    .replace("{message}", effectiveContent);

            // Snapshot attachments for use inside the lambda
            final List<net.dv8tion.jda.api.entities.Message.Attachment> finalAttachments =
                    java.util.Collections.unmodifiableList(new java.util.ArrayList<>(attachments));
            final String jumpUrl = event.getMessage().getJumpUrl();

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    com.kelpwing.kelpylandiaplugin.chat.Channel targetChannel = null;
                    for (com.kelpwing.kelpylandiaplugin.chat.Channel ch : plugin.getChannelManager().getChannels()) {
                        if (ch.isDiscordEnabled() && channelId.equals(ch.getDiscordChannel())) {
                            targetChannel = ch;
                            break;
                        }
                    }
                    if (targetChannel == null) {
                        targetChannel = plugin.getChannelManager().getChannel("global");
                        if (targetChannel == null) targetChannel = plugin.getChannelManager().getDefaultChannel();
                    }

                    // Build the text portion of the message
                    String coloredMessage = org.bukkit.ChatColor.translateAlternateColorCodes('&', minecraftMessage);

                    // Build clickable attachment components (one per attachment)
                    // Each looks like: §9[📷 image] (Ctrl+Click to open)
                    java.util.List<net.md_5.bungee.api.chat.TextComponent> attachmentComponents =
                            new java.util.ArrayList<>();
                    for (net.dv8tion.jda.api.entities.Message.Attachment att : finalAttachments) {
                        String label = getAttachmentLabel(att);
                        String url   = att.getUrl();

                        net.md_5.bungee.api.chat.TextComponent link =
                                new net.md_5.bungee.api.chat.TextComponent(" " + label);
                        link.setColor(net.md_5.bungee.api.ChatColor.AQUA);
                        link.setUnderlined(true);
                        link.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                                net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL, url));
                        link.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                                new net.md_5.bungee.api.chat.BaseComponent[]{
                                    new net.md_5.bungee.api.chat.TextComponent(
                                            net.md_5.bungee.api.ChatColor.YELLOW + "Ctrl+Click to open\n"
                                            + net.md_5.bungee.api.ChatColor.GRAY + url)
                                }));
                        attachmentComponents.add(link);
                    }

                    // Deliver to all players in the target channel
                    final com.kelpwing.kelpylandiaplugin.chat.Channel deliverTo = targetChannel;
                    java.util.function.Consumer<Player> deliver = player -> {
                        if (deliverTo != null) {
                            String playerChannelName = plugin.getChannelManager().getPlayerChannel(player.getUniqueId());
                            if (!deliverTo.getName().equalsIgnoreCase(playerChannelName)
                                    || !plugin.getChannelManager().hasPermission(player, deliverTo.getName())) {
                                return;
                            }
                        }

                        if (attachmentComponents.isEmpty()) {
                            // No attachments - plain text send (original behaviour)
                            player.sendMessage(coloredMessage);
                        } else {
                            // Build a single BaseComponent array: text prefix + attachment links
                            net.md_5.bungee.api.chat.TextComponent base =
                                    new net.md_5.bungee.api.chat.TextComponent(
                                            net.md_5.bungee.api.chat.TextComponent.fromLegacyText(coloredMessage));
                            for (net.md_5.bungee.api.chat.TextComponent link : attachmentComponents) {
                                base.addExtra(link);
                            }
                            player.spigot().sendMessage(base);
                        }
                    };

                    if (deliverTo != null) {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            deliver.accept(player);
                        }
                        if (debugRelay) {
                            plugin.getLogger().info("Relayed Discord message to channel '"
                                    + deliverTo.getName() + "': " + coloredMessage
                                    + (finalAttachments.isEmpty() ? "" : " [+" + finalAttachments.size() + " attachment(s)]"));
                        }
                    } else {
                        // No channel found - broadcast to everyone
                        if (attachmentComponents.isEmpty()) {
                            Bukkit.broadcastMessage(coloredMessage);
                        } else {
                            net.md_5.bungee.api.chat.TextComponent base =
                                    new net.md_5.bungee.api.chat.TextComponent(
                                            net.md_5.bungee.api.chat.TextComponent.fromLegacyText(coloredMessage));
                            for (net.md_5.bungee.api.chat.TextComponent link : attachmentComponents) {
                                base.addExtra(link);
                            }
                            for (Player player : Bukkit.getOnlinePlayers()) {
                                player.spigot().sendMessage(base);
                            }
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to relay Discord message to Minecraft: " + e.getMessage());
                }
            });
        } else if (debugRelay) {
            plugin.getLogger().info("Discord message in non-chat channel (" + channelId + "), not relaying.");
        }
    }

    /**
     * Returns a labelled emoji string for a Discord attachment based on its content type.
     * Used to build clickable links in the Minecraft chat relay.
     */
    private String getAttachmentLabel(net.dv8tion.jda.api.entities.Message.Attachment att) {
        if (att.isImage()) {
            // GIFs are technically images in JDA but we can check the file extension
            String ext = att.getFileExtension() == null ? "" : att.getFileExtension().toLowerCase();
            if ("gif".equals(ext)) return "🎞 [gif]";
            return "🖼 [image]";
        }
        if (att.isVideo()) return "🎬 [video]";
        // Fallback: classify by extension
        String ext = att.getFileExtension() == null ? "" : att.getFileExtension().toLowerCase();
        return switch (ext) {
            case "mp3", "ogg", "wav", "flac", "m4a" -> "🎵 [audio]";
            case "pdf"                               -> "📄 [pdf]";
            case "zip", "rar", "7z", "tar", "gz"    -> "🗜 [archive]";
            case "txt", "log", "md"                 -> "📝 [text]";
            default                                  -> "📎 [file]";
        };
    }

    /** Check whether the Discord member has the configured console role. */
    private boolean hasConsoleRole(Member member) {
        if (member == null) {
            plugin.getLogger().warning("[Discord] Console command denied: could not resolve member (null).");
            return false;
        }
        String consoleRoleId = plugin.getConfig().getString("discord.console.commands.console-role-id", "");
        if (consoleRoleId.isEmpty() || consoleRoleId.equals("your-console-role-id")) {
            plugin.getLogger().warning("[Discord] Console command denied: discord.console.commands.console-role-id is not configured.");
            return false;
        }
        boolean hasRole = member.getRoles().stream().anyMatch(r -> r.getId().equals(consoleRoleId));
        if (!hasRole) {
            plugin.getLogger().warning("[Discord] Console command denied for " + member.getUser().getName()
                    + ": required role " + consoleRoleId + " not found in [" 
                    + member.getRoles().stream().map(r -> r.getId() + "(" + r.getName() + ")")
                            .collect(java.util.stream.Collectors.joining(", ")) + "]");
        }
        return hasRole;
    }

    /** Resolve a Member from a MessageReceivedEvent, even if getMember() is null. */
    private Member resolveMember(MessageReceivedEvent event) {
        Member member = event.getMember();
        if (member != null) return member;
        // Fallback: look up from guild (may be null in DMs, which shouldn't reach console anyway)
        if (event.isFromGuild()) {
            try {
                return event.getGuild().retrieveMember(event.getAuthor()).complete();
            } catch (Exception ignored) {}
        }
        return null;
    }

    /** Dispatch a raw command string on the server console and react/reply on Discord. */
    private void dispatchConsoleCommand(String command, MessageReceivedEvent event, String username) {
        plugin.getLogger().info("[Console-Discord] " + username + " issued: " + command);
        event.getMessage().addReaction(Emoji.fromUnicode("✅")).queue(null, e -> {});

        // Start capturing console output lines for a private reply to the invoker
        startCmdCapture(event.getMessage());

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // Use CraftServer.dispatchServerCommand so vanilla brigadier commands (clear, give, tp, etc.)
                // resolve offline player names exactly as the real server console would.
                // Falls back to Bukkit.dispatchCommand if the method is unavailable on this build.
                boolean dispatched = false;
                try {
                    Object craftServer = Bukkit.getServer();
                    Method dispatchServerMethod = craftServer.getClass()
                            .getMethod("dispatchServerCommand", org.bukkit.command.CommandSender.class, String.class);
                    dispatchServerMethod.invoke(craftServer, Bukkit.getConsoleSender(), command);
                    dispatched = true;
                } catch (NoSuchMethodException ignored) {
                    // Older/non-CraftBukkit build - fall through
                }
                if (!dispatched) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }
            } catch (Exception e) {
                sendToConsole("Error executing command '" + command + "': " + e.getMessage());
                event.getMessage().addReaction(Emoji.fromUnicode("❌")).queue(null, err -> {});
            }

            // Schedule capture flush 10 seconds after the command was dispatched.
            // Some commands (e.g. lp editor) generate output asynchronously and may
            // take several seconds to print their result - 3 s was too short.
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::flushCmdCapture, 200L);
        });
    }

    /** Begin capturing console lines for a single command invocation. */
    private void startCmdCapture(Message source) {
        cmdCaptureLines.clear();
        cmdCaptureSource = source;
        cmdCaptureActive = true;
    }

    /**
     * Stop capturing, collect lines, and reply to the invoker with a code-block.
     * The reply auto-deletes after 30 seconds.
     */
    private void flushCmdCapture() {
        cmdCaptureActive = false;
        Message source = cmdCaptureSource;
        cmdCaptureSource = null;

        java.util.List<String> lines = new java.util.ArrayList<>(cmdCaptureLines);
        cmdCaptureLines.clear();

        if (source == null) return;

        String body;
        if (lines.isEmpty()) {
            body = "```\n(no output)\n```";
        } else {
            StringBuilder sb = new StringBuilder("```\n");
            for (String line : lines) {
                if (sb.length() + line.length() + 1 > 1950) {
                    sb.append("… (truncated)\n");
                    break;
                }
                sb.append(line).append("\n");
            }
            sb.append("```");
            body = sb.toString();
        }

        try {
            source.reply(body).mentionRepliedUser(false).queue(
                    reply -> Bukkit.getScheduler().runTaskLaterAsynchronously(plugin,
                            () -> reply.delete().queue(null, e -> {}), 600L), // auto-delete after 30 s
                    err -> plugin.getLogger().warning("Failed to send command reply: " + err.getMessage())
            );
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to reply to c! command: " + e.getMessage());
        }
    }

    /**
     * Send a message to the global chat Discord channel
     */
    public void sendToGlobalChat(String message) {
        if (!enabled || serverChannelId == null || serverChannelId.isEmpty()) return;
        
        try {
            TextChannel channel = jda.getTextChannelById(serverChannelId);
            if (channel != null) {
                channel.sendMessage(stripSectionCodes(message)).queue();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send message to Discord global chat: " + e.getMessage());
        }
    }
    
    /**
     * Send a player join embed to Discord
     */
    public void sendPlayerJoinEmbed(Player player) {
        if (!enabled || serverChannelId == null || serverChannelId.isEmpty()) return;
        
        try {
            TextChannel channel = jda.getTextChannelById(serverChannelId);
            if (channel != null) {
                EmbedBuilder embed = new EmbedBuilder();
                embed.setColor(getColorFromConfig("discord.embeds.join-color", Color.GREEN));
                embed.setAuthor(stripSectionCodes(player.getName()) + " joined the server", null, 
                    "https://mc-heads.net/avatar/" + player.getUniqueId() + "/64");
                
                channel.sendMessageEmbeds(embed.build()).queue();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send join embed to Discord: " + e.getMessage());
        }
    }
    
    /**
     * Send a player death embed/message to Discord.
     * @param player      the player who died
     * @param deathMessage the final (color-stripped) death message shown in-game
     */
    public void sendDeathMessage(Player player, String deathMessage) {
        if (!enabled || serverChannelId == null || serverChannelId.isEmpty()) return;

        try {
            TextChannel channel = jda.getTextChannelById(serverChannelId);
            if (channel == null) return;

            if (plugin.getConfig().getBoolean("discord.events.use-embeds", true)) {
                EmbedBuilder embed = new EmbedBuilder();
                embed.setColor(getColorFromConfig("discord.embeds.death-color", new java.awt.Color(0x8B0000)));
                embed.setAuthor(deathMessage, null,
                        "https://mc-heads.net/avatar/" + player.getUniqueId() + "/64");
                channel.sendMessageEmbeds(embed.build()).queue();
            } else {
                String format = plugin.getConfig().getString("discord.formats.death", "{death_message}");
                String msg = format.replace("{death_message}", deathMessage)
                                   .replace("{player}", player.getName());
                channel.sendMessage(msg).queue();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send death message to Discord: " + e.getMessage());
        }
    }

    /**
     * Send a player leave embed to Discord
     */
    public void sendPlayerLeaveEmbed(Player player) {
        if (!enabled || serverChannelId == null || serverChannelId.isEmpty()) return;
        
        try {
            TextChannel channel = jda.getTextChannelById(serverChannelId);
            if (channel != null) {
                EmbedBuilder embed = new EmbedBuilder();
                embed.setColor(getColorFromConfig("discord.embeds.leave-color", Color.RED));
                embed.setAuthor(stripSectionCodes(player.getName()) + " left the server", null, 
                    "https://mc-heads.net/avatar/" + player.getUniqueId() + "/64");
                
                channel.sendMessageEmbeds(embed.build()).queue();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send leave embed to Discord: " + e.getMessage());
        }
    }
    
    /**
     * Send an advancement embed to Discord
     */
    public void sendAdvancementEmbed(Player player, String advancementTitle, String advancementDescription) {
        if (!enabled || serverChannelId == null || serverChannelId.isEmpty()) return;
        
        try {
            TextChannel channel = jda.getTextChannelById(serverChannelId);
            if (channel != null) {
                EmbedBuilder embed = new EmbedBuilder();
                embed.setColor(getColorFromConfig("discord.embeds.advancement-color", Color.YELLOW));
                embed.setAuthor(stripSectionCodes(player.getName()) + " has made an advancement!", null, 
                    "https://mc-heads.net/avatar/" + player.getUniqueId() + "/64");
                embed.setTitle(stripSectionCodes(advancementTitle));
                
                channel.sendMessageEmbeds(embed.build()).queue();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send advancement embed to Discord: " + e.getMessage());
        }
    }
    
    /**
     * Send an achievement embed to Discord (for legacy achievements or custom ones)
     */
    public void sendAchievementEmbed(Player player, String achievementTitle, String achievementDescription) {
        if (!enabled || serverChannelId == null || serverChannelId.isEmpty()) return;
        
        try {
            TextChannel channel = jda.getTextChannelById(serverChannelId);
            if (channel != null) {
                EmbedBuilder embed = new EmbedBuilder();
                embed.setColor(getColorFromConfig("discord.embeds.achievement-color", Color.ORANGE));
                embed.setAuthor(stripSectionCodes(player.getName()) + " has earned an achievement!", null, 
                    "https://mc-heads.net/avatar/" + player.getUniqueId() + "/64");
                embed.setTitle(stripSectionCodes(achievementTitle));
                
                channel.sendMessageEmbeds(embed.build()).queue();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send achievement embed to Discord: " + e.getMessage());
        }
    }
    
    /**
     * Get a color from config or return default
     */
    private Color getColorFromConfig(String configPath, Color defaultColor) {
        String colorHex = plugin.getConfig().getString(configPath);
        if (colorHex != null && colorHex.startsWith("#") && colorHex.length() == 7) {
            try {
                return Color.decode(colorHex);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid color format in config: " + colorHex + ", using default");
            }
        }
        return defaultColor;
    }
    
    /**
     * Send a plain informational message to the console Discord channel.
     * Formatted the same way as sendConsoleMessage at INFO level.
     */
    public void sendToConsole(String message) {
        sendConsoleMessage(message, "INFO");
    }
    
    public void disable() {
        // Stop the console flush thread first so it doesn't race with shutdown
        stopConsoleFlushThread();

        if (enabled && jda != null) {
            jda.shutdown();
        }
        // Clear webhook cache but don't delete actual webhooks 
        // (they can be reused on restart)
        webhookUrls.clear();
        // Reset the rolling console buffer so the next session starts fresh
        consoleLineBuffer.clear();
        consoleBufferLen = 0;
        consoleCurrentMessage.set(null);
        pendingConsoleLines.clear();
        enabled = false;
    }

    /**
     * Starts the single background thread that drains {@link #pendingConsoleLines}
     * and batches them into Discord edits/sends at most once per second.
     * This is DiscordSRV's approach: never block the logging thread, never spawn
     * a new task per line - one steady thread does all the REST work.
     */
    private void startConsoleFlushThread() {
        if (consoleChannelId == null || consoleChannelId.isEmpty()
                || consoleChannelId.equals("your-console-channel-id")) return;

        consoleFlushThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Block until at least one line is available (avoids spin-waiting)
                    String first = pendingConsoleLines.poll(2, java.util.concurrent.TimeUnit.SECONDS);
                    if (first == null) continue; // timeout - nothing to flush

                    // Drain ALL currently queued lines so we batch as many as possible
                    java.util.List<String> batch = new java.util.ArrayList<>();
                    batch.add(first);
                    pendingConsoleLines.drainTo(batch);

                    flushBatchToDiscord(batch);

                    // Sleep 1 second between flushes to stay well under Discord's
                    // 5 edits/sec per-message rate limit.
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    // Never let an uncaught exception kill the flush thread
                    plugin.getLogger().warning("[Console] Flush thread error: " + e.getMessage());
                }
            }
            // Final drain on shutdown so no lines are lost
            if (!pendingConsoleLines.isEmpty()) {
                java.util.List<String> remaining = new java.util.ArrayList<>();
                pendingConsoleLines.drainTo(remaining);
                if (!remaining.isEmpty()) flushBatchToDiscord(remaining);
            }
        }, "KelpylandiaConsoleFlush");
        consoleFlushThread.setDaemon(true);
        consoleFlushThread.start();
    }

    private void stopConsoleFlushThread() {
        Thread t = consoleFlushThread;
        if (t != null) {
            t.interrupt();
            consoleFlushThread = null;
        }
    }

    /**
     * Takes a batch of already-formatted lines and appends them to the rolling
     * Discord message (editing it), or starts a new message when the 1 900-char
     * limit would be exceeded.  Called exclusively from the flush thread.
     */
    private void flushBatchToDiscord(java.util.List<String> batch) {
        TextChannel channel = jda.getTextChannelById(consoleChannelId);
        if (channel == null) return;

        for (String line : batch) {
            int addedLen = line.length() + 1; // +1 for '\n'

            if (consoleBufferLen + addedLen > CONSOLE_MAX_CHARS
                    || consoleCurrentMessage.get() == null) {
                // Start a fresh message
                consoleLineBuffer.clear();
                consoleBufferLen = 0;
                consoleLineBuffer.add(line);
                consoleBufferLen = line.length();

                String block = "```\n" + line + "\n```";
                try {
                    Message sent = channel.sendMessage(block).complete();
                    consoleCurrentMessage.set(sent);
                } catch (Exception e) {
                    plugin.getLogger().warning("[Console] Failed to send message: " + e.getMessage());
                }
            } else {
                consoleLineBuffer.add(line);
                consoleBufferLen += addedLen;
            }
        }

        // One edit for the entire batch
        Message current = consoleCurrentMessage.get();
        if (current != null && !consoleLineBuffer.isEmpty()) {
            StringBuilder sb = new StringBuilder("```\n");
            for (String l : consoleLineBuffer) sb.append(l).append("\n");
            sb.append("```");
            try {
                Message updated = current.editMessage(sb.toString()).complete();
                consoleCurrentMessage.set(updated);
            } catch (Exception e) {
                plugin.getLogger().warning("[Console] Failed to edit message: " + e.getMessage());
            }
        }
    }
    
    public void initialize() {
        // Re-read configuration
        String token = plugin.getConfig().getString("discord.bot-token");
        this.punishmentChannelId = plugin.getConfig().getString("discord.moderation-channel-id");
        this.chatChannelId = plugin.getConfig().getString("channels.global.discord-channel");
        this.consoleChannelId = plugin.getConfig().getString("discord.console.console-channel-id");
        this.serverChannelId = resolveServerChannelId();
        
        // Validate configuration
        validateConfiguration();
        
        if (token == null || token.trim().isEmpty() || token.equals("your-bot-token-here")) {
            plugin.getLogger().warning("Discord bot token not configured! Please set 'discord.bot-token' in config.yml");
            return;
        }

        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES)
                    .addEventListeners(this)
                    .build();

            jda.awaitReady();
            enabled = true;
            
            // Initialize webhooks for configured channels to reuse existing ones
            initializeExistingWebhooks();

            // Start the console flush thread for the re-initialized session
            startConsoleFlushThread();
            
            plugin.getLogger().info("Discord integration enabled successfully!");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize Discord integration: " + e.getMessage());
            enabled = false;
        }
    }

    /**
     * Resolves the channel ID to use for server events (join/leave/death/server start/stop).
     * Uses discord.server-channel-id if configured; falls back to the default channel's
     * discord-channel (channels.global.discord-channel).
     */
    private String resolveServerChannelId() {
        String explicit = plugin.getConfig().getString("discord.server-channel-id", "");
        if (explicit != null && !explicit.isEmpty() && !explicit.equals("your-server-channel-id")) {
            return explicit;
        }
        // Fall back to the default channel's Discord channel ID
        return plugin.getConfig().getString("channels.global.discord-channel", "");
    }
    
    private void validateConfiguration() {
        plugin.getLogger().info("=== Discord Configuration Validation ===");
        
        // Check bot token
        String token = plugin.getConfig().getString("discord.bot-token");
        plugin.getLogger().info("Bot Token: " + (token != null && !token.equals("your-bot-token-here") ? "✓ Configured" : "✗ Missing/Default"));
        
        // Check channel IDs
        String chatId = plugin.getConfig().getString("channels.global.discord-channel");
        String modId = plugin.getConfig().getString("discord.moderation-channel-id");
        String consoleId = plugin.getConfig().getString("discord.console.console-channel-id");
        
        plugin.getLogger().info("Chat Channel ID: " + (chatId != null && !chatId.isEmpty() ? "✓ " + chatId : "✗ Missing/Default"));
        plugin.getLogger().info("Server Events Channel ID: " + (serverChannelId != null && !serverChannelId.isEmpty() ? "✓ " + serverChannelId : "✗ Not set (will use chat channel)"));
        plugin.getLogger().info("Moderation Channel ID: " + (modId != null && !modId.equals("your-moderation-channel-id") ? "✓ " + modId : "✗ Missing/Default"));
        plugin.getLogger().info("Console Channel ID: " + (consoleId != null && !consoleId.equals("your-console-channel-id") ? "✓ " + consoleId : "✗ Missing/Default"));
        
        // Check event settings
        boolean joinEvents = plugin.getConfig().getBoolean("discord.events.broadcast-joins", true);
        boolean leaveEvents = plugin.getConfig().getBoolean("discord.events.broadcast-leaves", true);
        plugin.getLogger().info("Join/Leave Events: " + (joinEvents ? "✓ Enabled" : "✗ Disabled") + " / " + (leaveEvents ? "✓ Enabled" : "✗ Disabled"));
        
        plugin.getLogger().info("=======================================");
    }
    
    private void initializeExistingWebhooks() {
        try {
            // Check chat channel for existing webhook
            if (chatChannelId != null) {
                getWebhookUrl(chatChannelId); // This will find or create webhook
            }
            
            // Check punishment channel for existing webhook
            if (punishmentChannelId != null) {
                getWebhookUrl(punishmentChannelId); // This will find or create webhook
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to initialize existing webhooks: " + e.getMessage());
        }
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    // Webhook helper methods
    private String getWebhookUrl(String channelId) {
        if (webhookUrls.containsKey(channelId)) {
            return webhookUrls.get(channelId);
        }
        
        // Create webhook for this channel
        return createWebhookForChannel(channelId);
    }
    
    private String createWebhookForChannel(String channelId) {
        try {
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel == null) return null;
            
            // First, check if we already have a webhook for this channel
            List<Webhook> existingWebhooks = channel.retrieveWebhooks().complete();
            for (Webhook webhook : existingWebhooks) {
                if ("KelpylandiaChat".equals(webhook.getName())) {
                    String webhookUrl = webhook.getUrl();
                    webhookUrls.put(channelId, webhookUrl);
                    plugin.getLogger().info("Found existing webhook for channel: " + channelId);
                    return webhookUrl;
                }
            }
            
            // Only create new webhook if none exists
            try {
                var webhook = channel.createWebhook("KelpylandiaChat")
                    .setAvatar(null)
                    .complete(); // Wait for completion
                    
                String webhookUrl = webhook.getUrl();
                webhookUrls.put(channelId, webhookUrl);
                plugin.getLogger().info("Created new webhook for channel: " + channelId);
                return webhookUrl;
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to create webhook for channel " + channelId + ": " + e.getMessage());
                return null;
            }
                
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create webhook: " + e.getMessage());
            return null;
        }
    }
    
    private void sendWebhookRequest(String webhookUrl, String payload) {
        try {
            URL url = new URL(webhookUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = payload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = connection.getResponseCode();
            if (responseCode != 200 && responseCode != 204) {
                plugin.getLogger().warning("Webhook request failed with code: " + responseCode);
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send webhook request: " + e.getMessage());
        }
    }
    
    /**
     * Removes Minecraft section codes (color codes) from text before sending to Discord
     */
    /**
     * Maps an item's rarity (and whether it has a custom name) to a Discord embed accent colour.
     * Matches the Minecraft tooltip name colours used by IC-dsrv.
     */
    private static java.awt.Color rarityEmbedColor(com.kelpwing.kelpylandiaplugin.chat.ItemDisplayData data) {
        if (data.hasCustomName()) return new java.awt.Color(0xFFAA00); // gold - custom name
        switch (data.getRarity()) {
            case UNCOMMON: return new java.awt.Color(0xFFFF55); // yellow
            case RARE:     return new java.awt.Color(0x55FFFF); // aqua
            case EPIC:     return new java.awt.Color(0xFF55FF); // light purple
            default:       return new java.awt.Color(0xAAAAAA); // gray - common
        }
    }

    private String stripSectionCodes(String text) {
        if (text == null) return "";
        // Remove all section codes (§ followed by any character)
        return text.replaceAll("§[0-9a-fk-or]", "");
    }
    
    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    /**
     * Register Discord slash commands for moderation
     */
    private void registerSlashCommands() {
        if (jda == null) return;
        
        try {
            jda.updateCommands().addCommands(
                // Moderation commands
                Commands.slash("ban", "Ban a player from the server")
                    .addOption(OptionType.STRING, "player", "The player to ban", true)
                    .addOption(OptionType.STRING, "duration", "Duration of the ban (e.g., 1d, 2h, permanent)", true)
                    .addOption(OptionType.STRING, "reason", "Reason for the ban", false),
                
                Commands.slash("kick", "Kick a player from the server")
                    .addOption(OptionType.STRING, "player", "The player to kick", true)
                    .addOption(OptionType.STRING, "reason", "Reason for the kick", false),
                
                Commands.slash("mute", "Mute a player")
                    .addOption(OptionType.STRING, "player", "The player to mute", true)
                    .addOption(OptionType.STRING, "duration", "Duration of the mute (e.g., 1h, 30m)", true)
                    .addOption(OptionType.STRING, "reason", "Reason for the mute", false),
                
                Commands.slash("warn", "Warn a player")
                    .addOption(OptionType.STRING, "player", "The player to warn", true)
                    .addOption(OptionType.STRING, "reason", "Reason for the warning", false),
                
                Commands.slash("ipban", "Ban a player's IP address")
                    .addOption(OptionType.STRING, "player", "The player to IP ban", true)
                    .addOption(OptionType.STRING, "duration", "Duration of the IP ban (e.g., 1d, permanent)", true)
                    .addOption(OptionType.STRING, "reason", "Reason for the IP ban", false),
                
                Commands.slash("unban", "Unban a player")
                    .addOption(OptionType.STRING, "player", "The player to unban", true)
                    .addOption(OptionType.STRING, "reason", "Reason for unbanning", false),
                
                Commands.slash("unmute", "Unmute a player")
                    .addOption(OptionType.STRING, "player", "The player to unmute", true)
                    .addOption(OptionType.STRING, "reason", "Reason for unmuting", false),
                
                Commands.slash("unwarn", "Remove a warning from a player")
                    .addOption(OptionType.STRING, "player", "The player to remove warning from", true)
                    .addOption(OptionType.STRING, "reason", "Reason for removing warning", false),
                
                // Admin commands
                Commands.slash("rules", "Display the server rules"),
                
                Commands.slash("echo", "Echo a message in the Discord channel")
                    .addOption(OptionType.STRING, "message", "The message to echo", true),
                
                Commands.slash("restart", "Restart the server"),
                
                Commands.slash("stop", "Stop the server"),
                
                Commands.slash("reload", "Reload a plugin")
                    .addOption(OptionType.STRING, "plugin", "The plugin name to reload", true),
                
                Commands.slash("list", "List all online players"),
                
                // Test commands
                Commands.slash("testban", "Send a test ban punishment message"),
                Commands.slash("testwarn", "Send a test warn punishment message"),
                Commands.slash("testkick", "Send a test kick punishment message"),
                Commands.slash("testmute", "Send a test mute punishment message"),
                Commands.slash("testunban", "Send a test unban punishment message"),
                Commands.slash("testunwarn", "Send a test unwarn punishment message"),
                Commands.slash("testunmute", "Send a test unmute punishment message"),
                
                // Economy commands
                Commands.slash("price", "Check an item's price and view a price history graph")
                    .addOption(OptionType.STRING, "item", "The item to check (e.g., diamond, iron_ingot)", true)
            ).queue(
                success -> plugin.getLogger().info("Successfully registered Discord slash commands!"),
                error -> plugin.getLogger().warning("Failed to register Discord slash commands: " + error.getMessage())
            );
        } catch (Exception e) {
            plugin.getLogger().warning("Error registering Discord slash commands: " + e.getMessage());
        }
    }
    
    /**
     * Handle Discord slash command interactions
     */
    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        String staffName = event.getUser().getName();
        
        // Check permissions based on command type
        String requiredRoleId;
        if (isAdminCommand(commandName)) {
            requiredRoleId = plugin.getConfig().getString("discord.admin-role-id");
            if (requiredRoleId == null || !hasRequiredRole(event.getMember(), requiredRoleId)) {
                event.reply("❌ You don't have permission to use admin commands!").setEphemeral(true).queue();
                return;
            }
        } else if (isModerationCommand(commandName)) {
            requiredRoleId = plugin.getConfig().getString("discord.moderator-role-id");
            if (requiredRoleId == null || !hasRequiredRole(event.getMember(), requiredRoleId)) {
                event.reply("❌ You don't have permission to use moderation commands!").setEphemeral(true).queue();
                return;
            }
        }
        
        switch (commandName) {
            // Moderation commands
            case "ban":
                handleBanCommand(event, staffName);
                break;
            case "kick":
                handleKickCommand(event, staffName);
                break;
            case "mute":
                handleMuteCommand(event, staffName);
                break;
            case "warn":
                handleWarnCommand(event, staffName);
                break;
            case "ipban":
                handleIPBanCommand(event, staffName);
                break;
            case "unban":
                handleUnbanCommand(event, staffName);
                break;
            case "unmute":
                handleUnmuteCommand(event, staffName);
                break;
            case "unwarn":
                handleUnwarnCommand(event, staffName);
                break;
                
            // Admin commands
            case "rules":
                handleRulesCommand(event);
                break;
            case "echo":
                handleEchoCommand(event);
                break;
            case "restart":
                handleRestartCommand(event);
                break;
            case "stop":
                handleStopCommand(event);
                break;
            case "reload":
                handleReloadCommand(event);
                break;
            case "list":
                handleListCommand(event);
                break;
                
            // Test commands
            case "testban":
                handleTestBanCommand(event, staffName);
                break;
            case "testwarn":
                handleTestWarnCommand(event, staffName);
                break;
            case "testkick":
                handleTestKickCommand(event, staffName);
                break;
            case "testmute":
                handleTestMuteCommand(event, staffName);
                break;
            case "testunban":
                handleTestUnbanCommand(event, staffName);
                break;
            case "testunwarn":
                handleTestUnwarnCommand(event, staffName);
                break;
            case "testunmute":
                handleTestUnmuteCommand(event, staffName);
                break;
                
            case "price":
                handlePriceCommand(event);
                break;
                
            default:
                event.reply("❌ Unknown command!").setEphemeral(true).queue();
        }
    }
    
    /**
     * Check if a Discord member has the required role for moderation commands
     */
    private boolean hasRequiredRole(Member member, String requiredRoleId) {
        if (member == null) return false;
        return member.getRoles().stream()
            .anyMatch(role -> role.getId().equals(requiredRoleId));
    }
    
    /**
     * Handle Discord ban command
     */
    private void handleBanCommand(SlashCommandInteractionEvent event, String staffName) {
        String playerName = event.getOption("player").getAsString();
        String durationStr = event.getOption("duration").getAsString();
        String reason = event.getOption("reason") != null ? 
            event.getOption("reason").getAsString() : "No reason provided";
        
        // Defer the reply to give us more time to process
        event.deferReply().queue();
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // Parse duration
                long duration = DurationParser.parseDuration(durationStr);
                
                // Create punishment object
                Punishment punishment = new Punishment(playerName, staffName, reason, "BAN", duration);
                
                // Execute ban using Bukkit's ban system
                Date expiry = duration > 0 ? new Date(System.currentTimeMillis() + duration) : null;
                Bukkit.getBanList(VersionHelper.getNameBanListType()).addBan(playerName, reason, expiry, staffName);
                
                // Kick if online
                Player target = Bukkit.getPlayer(playerName);
                if (target != null) {
                    target.kickPlayer("§cYou have been banned\nReason: " + reason + 
                        (duration > 0 ? "\nDuration: " + formatDuration(duration) : "\nThis ban is permanent"));
                }
                
                // Save to database
                plugin.getFileManager().savePunishment(punishment);
                
                // Send Discord punishment embed
                sendPunishmentMessage(punishment);
                
                // Broadcast to server
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("staff_name", staffName);
                placeholders.put("player_name", playerName);
                placeholders.put("reason", reason);
                placeholders.put("duration", duration > 0 ? formatDuration(duration) : "Permanent");
                
                String broadcastMsg = plugin.getConfigManager().formatBroadcastMessage("ban", placeholders);
                if (broadcastMsg != null) {
                    Bukkit.broadcastMessage(broadcastMsg);
                }
                
                event.getHook().sendMessage("✅ Successfully banned " + playerName + " for: " + reason).queue();
                
            } catch (Exception e) {
                event.getHook().sendMessage("❌ Error executing ban: " + e.getMessage()).queue();
                plugin.getLogger().warning("Discord ban command error: " + e.getMessage());
            }
        });
    }
    
    /**
     * Handle Discord kick command
     */
    private void handleKickCommand(SlashCommandInteractionEvent event, String staffName) {
        String playerName = event.getOption("player").getAsString();
        String reason = event.getOption("reason") != null ? 
            event.getOption("reason").getAsString() : "No reason provided";
        
        event.deferReply().queue();
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Player target = Bukkit.getPlayer(playerName);
                if (target == null) {
                    event.getHook().sendMessage("❌ Player " + playerName + " is not online!").queue();
                    return;
                }
                
                // Create punishment object
                Punishment punishment = new Punishment(playerName, staffName, reason, "KICK", 0);
                
                // Kick the player
                target.kickPlayer("§cYou have been kicked\nReason: " + reason);
                
                // Save to database
                plugin.getFileManager().savePunishment(punishment);
                
                // Send Discord punishment embed
                sendPunishmentMessage(punishment);
                
                // Broadcast to server
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("staff_name", staffName);
                placeholders.put("player_name", playerName);
                placeholders.put("reason", reason);
                placeholders.put("duration", "N/A");
                
                String broadcastMsg = plugin.getConfigManager().formatBroadcastMessage("kick", placeholders);
                if (broadcastMsg != null) {
                    Bukkit.broadcastMessage(broadcastMsg);
                }
                
                event.getHook().sendMessage("✅ Successfully kicked " + playerName + " for: " + reason).queue();
                
            } catch (Exception e) {
                event.getHook().sendMessage("❌ Error executing kick: " + e.getMessage()).queue();
                plugin.getLogger().warning("Discord kick command error: " + e.getMessage());
            }
        });
    }
    
    /**
     * Handle Discord mute command
     */
    private void handleMuteCommand(SlashCommandInteractionEvent event, String staffName) {
        String playerName = event.getOption("player").getAsString();
        String durationStr = event.getOption("duration").getAsString();
        String reason = event.getOption("reason") != null ? 
            event.getOption("reason").getAsString() : "No reason provided";
        
        event.deferReply().queue();
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // Parse duration
                long duration = DurationParser.parseDuration(durationStr);
                if (duration <= 0) {
                    event.getHook().sendMessage("❌ Invalid duration! Use format like: 1h, 30m, 2d").queue();
                    return;
                }
                
                // Create punishment object
                Punishment punishment = new Punishment(playerName, staffName, reason, "MUTE", duration);
                
                // Save to database
                plugin.getFileManager().savePunishment(punishment);
                
                // Send Discord punishment embed
                sendPunishmentMessage(punishment);
                
                // Broadcast to server
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("staff_name", staffName);
                placeholders.put("player_name", playerName);
                placeholders.put("reason", reason);
                placeholders.put("duration", formatDuration(duration));
                
                String broadcastMsg = plugin.getConfigManager().formatBroadcastMessage("mute", placeholders);
                if (broadcastMsg != null) {
                    Bukkit.broadcastMessage(broadcastMsg);
                }
                
                event.getHook().sendMessage("✅ Successfully muted " + playerName + " for: " + reason + 
                    " (Duration: " + formatDuration(duration) + ")").queue();
                
            } catch (Exception e) {
                event.getHook().sendMessage("❌ Error executing mute: " + e.getMessage()).queue();
                plugin.getLogger().warning("Discord mute command error: " + e.getMessage());
            }
        });
    }
    
    /**
     * Handle Discord warn command
     */
    private void handleWarnCommand(SlashCommandInteractionEvent event, String staffName) {
        String playerName = event.getOption("player").getAsString();
        String reason = event.getOption("reason") != null ? 
            event.getOption("reason").getAsString() : "No reason provided";
        
        event.deferReply().queue();
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // Create punishment object
                Punishment punishment = new Punishment(playerName, staffName, reason, "WARN", 0);
                
                // Save warning
                long warnExpiration = System.currentTimeMillis() + (30L * 24L * 60L * 60L * 1000L); // 30 days
                plugin.getFileManager().saveWarning(playerName, reason, staffName, warnExpiration);
                
                // Send Discord punishment embed
                sendPunishmentMessage(punishment);
                
                // Broadcast to server
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("staff_name", staffName);
                placeholders.put("player_name", playerName);
                placeholders.put("reason", reason);
                placeholders.put("duration", "30 days");
                
                String broadcastMsg = plugin.getConfigManager().formatBroadcastMessage("warn", placeholders);
                if (broadcastMsg != null) {
                    Bukkit.broadcastMessage(broadcastMsg);
                }
                
                // Notify target player if online
                Player target = Bukkit.getPlayer(playerName);
                if (target != null) {
                    target.sendMessage("§cYou have been warned for: " + reason);
                    target.sendMessage("§eThis warning will expire in 30 days.");
                }
                
                event.getHook().sendMessage("✅ Successfully warned " + playerName + " for: " + reason).queue();
                
            } catch (Exception e) {
                event.getHook().sendMessage("❌ Error executing warn: " + e.getMessage()).queue();
                plugin.getLogger().warning("Discord warn command error: " + e.getMessage());
            }
        });
    }
    
    /**
     * Handle Discord ipban command
     */
    private void handleIPBanCommand(SlashCommandInteractionEvent event, String staffName) {
        String playerName = event.getOption("player").getAsString();
        String durationStr = event.getOption("duration").getAsString();
        String reason = event.getOption("reason") != null ? 
            event.getOption("reason").getAsString() : "No reason provided";
        
        event.deferReply().queue();
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Player target = Bukkit.getPlayer(playerName);
                if (target == null) {
                    event.getHook().sendMessage("❌ Player " + playerName + " is not online for IP ban!").queue();
                    return;
                }
                
                // Parse duration
                long duration = DurationParser.parseDuration(durationStr);
                String ip = target.getAddress().getAddress().getHostAddress();
                
                // Create punishment object
                Punishment punishment = new Punishment(playerName, staffName, reason, "IPBAN", duration);
                
                // Execute IP ban using Bukkit's ban system
                Date expiry = duration > 0 ? new Date(System.currentTimeMillis() + duration) : null;
                Bukkit.getBanList(BanList.Type.IP).addBan(ip, reason, expiry, staffName);
                
                // Kick the player
                target.kickPlayer("§cYou have been IP banned\nReason: " + reason + 
                    (duration > 0 ? "\nDuration: " + formatDuration(duration) : "\nThis ban is permanent"));
                
                // Save to database
                plugin.getFileManager().saveIPBan(ip, playerName, reason, staffName, duration);
                
                // Send Discord punishment embed
                sendPunishmentMessage(punishment);
                
                // Broadcast to server
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("staff_name", staffName);
                placeholders.put("player_name", playerName);
                placeholders.put("reason", reason);
                placeholders.put("duration", duration > 0 ? formatDuration(duration) : "Permanent");
                
                String broadcastMsg = plugin.getConfigManager().formatBroadcastMessage("ipban", placeholders);
                if (broadcastMsg != null) {
                    Bukkit.broadcastMessage(broadcastMsg);
                }
                
                event.getHook().sendMessage("✅ Successfully IP banned " + playerName + " for: " + reason).queue();
                
            } catch (Exception e) {
                event.getHook().sendMessage("❌ Error executing IP ban: " + e.getMessage()).queue();
                plugin.getLogger().warning("Discord IP ban command error: " + e.getMessage());
            }
        });
    }
    
    /**
     * Handle Discord unban command
     */
    private void handleUnbanCommand(SlashCommandInteractionEvent event, String staffName) {
        String playerName = event.getOption("player").getAsString();
        
        event.deferReply().queue();
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // Check if player is banned
                if (!Bukkit.getBanList(VersionHelper.getNameBanListType()).isBanned(playerName)) {
                    event.getHook().sendMessage("❌ Player " + playerName + " is not banned!").queue();
                    return;
                }
                
                // Remove from Bukkit ban list
                Bukkit.getBanList(VersionHelper.getNameBanListType()).pardon(playerName);
                
                // Try to remove from database
                plugin.getFileManager().removeBan(playerName);
                
                // Create punishment object
                Punishment punishment = new Punishment(playerName, staffName, "Unbanned by staff", "UNBAN", 0);
                
                // Send Discord punishment embed
                sendPunishmentMessage(punishment);
                
                // Broadcast to server
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("staff_name", staffName);
                placeholders.put("player_name", playerName);
                placeholders.put("reason", "Unbanned by staff");
                placeholders.put("duration", "N/A");
                
                String broadcastMsg = plugin.getConfigManager().formatBroadcastMessage("unban", placeholders);
                if (broadcastMsg != null) {
                    Bukkit.broadcastMessage(broadcastMsg);
                }
                
                event.getHook().sendMessage("✅ Successfully unbanned " + playerName).queue();
                
            } catch (Exception e) {
                event.getHook().sendMessage("❌ Error executing unban: " + e.getMessage()).queue();
                plugin.getLogger().warning("Discord unban command error: " + e.getMessage());
            }
        });
    }
    
    /**
     * Handle Discord unmute command
     */
    private void handleUnmuteCommand(SlashCommandInteractionEvent event, String staffName) {
        String playerName = event.getOption("player").getAsString();
        
        event.deferReply().queue();
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // For now, we'll create the unmute punishment without checking if player is actually muted
                // This is because the mute system may not have a centralized tracking system
                
                // Create punishment object
                Punishment punishment = new Punishment(playerName, staffName, "Unmuted by staff", "UNMUTE", 0);
                
                // Send Discord punishment embed
                sendPunishmentMessage(punishment);
                
                // Broadcast to server
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("staff_name", staffName);
                placeholders.put("player_name", playerName);
                placeholders.put("reason", "Unmuted by staff");
                placeholders.put("duration", "N/A");
                
                String broadcastMsg = plugin.getConfigManager().formatBroadcastMessage("unmute", placeholders);
                if (broadcastMsg != null) {
                    Bukkit.broadcastMessage(broadcastMsg);
                }
                
                event.getHook().sendMessage("✅ Unmute command executed for " + playerName).queue();
                
            } catch (Exception e) {
                event.getHook().sendMessage("❌ Error executing unmute: " + e.getMessage()).queue();
                plugin.getLogger().warning("Discord unmute command error: " + e.getMessage());
            }
        });
    }
    
    /**
     * Handle Discord unwarn command
     */
    private void handleUnwarnCommand(SlashCommandInteractionEvent event, String staffName) {
        String playerName = event.getOption("player").getAsString();
        int warningId = event.getOption("warning_id").getAsInt();
        
        event.deferReply().queue();
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // Remove warning from database using String ID
                plugin.getFileManager().removeWarning(String.valueOf(warningId));
                
                // Create punishment object
                Punishment punishment = new Punishment(playerName, staffName, "Warning removed by staff", "UNWARN", 0);
                
                // Send Discord punishment embed
                sendPunishmentMessage(punishment);
                
                // Broadcast to server
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("staff_name", staffName);
                placeholders.put("player_name", playerName);
                placeholders.put("reason", "Warning #" + warningId + " removed by staff");
                placeholders.put("duration", "N/A");
                
                String broadcastMsg = plugin.getConfigManager().formatBroadcastMessage("unwarn", placeholders);
                if (broadcastMsg != null) {
                    Bukkit.broadcastMessage(broadcastMsg);
                }
                
                event.getHook().sendMessage("✅ Successfully removed warning #" + warningId + " from " + playerName).queue();
                
            } catch (Exception e) {
                event.getHook().sendMessage("❌ Error executing unwarn: " + e.getMessage()).queue();
                plugin.getLogger().warning("Discord unwarn command error: " + e.getMessage());
            }
        });
    }
    
    // Helper methods for command categorization
    private boolean isAdminCommand(String command) {
        return command.equals("rules") || command.equals("echo") || command.equals("restart") || 
               command.equals("stop") || command.equals("reload") || command.equals("list") ||
               command.startsWith("test");
    }
    
    private boolean isModerationCommand(String command) {
        return command.equals("ban") || command.equals("kick") || command.equals("mute") || 
               command.equals("warn") || command.equals("ipban") || command.equals("unban") || 
               command.equals("unmute") || command.equals("unwarn");
    }
    
    // Admin command handlers
    private void handleRulesCommand(SlashCommandInteractionEvent event) {
        String title = plugin.getConfig().getString("rules.title", "Server Rules");
        String description = plugin.getConfig().getString("rules.description", "Please follow these rules:");
        List<String> rulesList = plugin.getConfig().getStringList("rules.rules-list");
        String footer = plugin.getConfig().getString("rules.footer", "Thank you for following the rules!");
        
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(title);
        embed.setDescription(description);
        embed.setColor(Color.BLUE);
        
        StringBuilder rulesText = new StringBuilder();
        for (String rule : rulesList) {
            rulesText.append(rule).append("\n");
        }
        
        embed.addField("Rules", rulesText.toString(), false);
        embed.setFooter(footer);
        embed.setTimestamp(Instant.now());
        
        event.replyEmbeds(embed.build()).queue();
    }
    
    private void handleEchoCommand(SlashCommandInteractionEvent event) {
        String message = event.getOption("message").getAsString();
        event.reply("✅ Message sent!").setEphemeral(true).queue();
        
        // Send the message to the channel where the command was used
        event.getChannel().sendMessage(message).queue();
    }
    
    private void handleRestartCommand(SlashCommandInteractionEvent event) {
        event.reply("✅ Server restart initiated!").setEphemeral(true).queue();
        
        // Send server restart message
        sendServerRestartEmbed();
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "restart");
        });
    }
    
    private void handleStopCommand(SlashCommandInteractionEvent event) {
        event.reply("✅ Server stop initiated!").setEphemeral(true).queue();
        
        // Send server stop message
        sendServerStopEmbed();
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.getServer().shutdown();
        });
    }
    
    private void handleReloadCommand(SlashCommandInteractionEvent event) {
        String pluginName = event.getOption("plugin").getAsString();
        
        event.deferReply().queue();
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "plugman reload " + pluginName);
                event.getHook().sendMessage("✅ Attempted to reload plugin: " + pluginName).queue();
            } catch (Exception e) {
                event.getHook().sendMessage("❌ Error reloading plugin " + pluginName + ": " + e.getMessage()).queue();
            }
        });
    }
    
    private void handleListCommand(SlashCommandInteractionEvent event) {
        StringBuilder playerList = new StringBuilder();
        int onlineCount = 0;
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            onlineCount++;
            playerList.append(player.getName());
            
            // Check if player is AFK (you can implement this based on your AFK system)
            if (isPlayerAFK(player)) {
                playerList.append(" [AFK]");
            }
            
            playerList.append("\n");
        }
        
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Online Players (" + onlineCount + "/" + Bukkit.getMaxPlayers() + ")");
        embed.setColor(Color.GREEN);
        
        if (onlineCount == 0) {
            embed.setDescription("No players are currently online.");
        } else {
            embed.setDescription(playerList.toString());
        }
        
        event.replyEmbeds(embed.build()).queue();
    }
    
    // Test command handlers
    private void handleTestBanCommand(SlashCommandInteractionEvent event, String staffName) {
        Punishment punishment = new Punishment("TestPlayer", staffName, "This is a test ban", "BAN", 86400000); // 1 day
        sendPunishmentMessage(punishment);
        
        // Broadcast to Minecraft
        String message = "§c[TEST] TestPlayer has been banned by " + staffName + " for: This is a test ban (Duration: 1 day)";
        Bukkit.broadcastMessage(message);
        
        event.reply("✅ Test ban message sent!").setEphemeral(true).queue();
    }
    
    private void handleTestWarnCommand(SlashCommandInteractionEvent event, String staffName) {
        Punishment punishment = new Punishment("TestPlayer", staffName, "This is a test warning", "WARN", 0);
        sendPunishmentMessage(punishment);
        
        // Broadcast to Minecraft
        String message = "§c[TEST] TestPlayer has been warned by " + staffName + " for: This is a test warning";
        Bukkit.broadcastMessage(message);
        
        event.reply("✅ Test warn message sent!").setEphemeral(true).queue();
    }
    
    private void handleTestKickCommand(SlashCommandInteractionEvent event, String staffName) {
        Punishment punishment = new Punishment("TestPlayer", staffName, "This is a test kick", "KICK", 0);
        sendPunishmentMessage(punishment);
        
        // Broadcast to Minecraft
        String message = "§c[TEST] TestPlayer has been kicked by " + staffName + " for: This is a test kick";
        Bukkit.broadcastMessage(message);
        
        event.reply("✅ Test kick message sent!").setEphemeral(true).queue();
    }
    
    private void handleTestMuteCommand(SlashCommandInteractionEvent event, String staffName) {
        Punishment punishment = new Punishment("TestPlayer", staffName, "This is a test mute", "MUTE", 3600000); // 1 hour
        sendPunishmentMessage(punishment);
        
        // Broadcast to Minecraft
        String message = "§c[TEST] TestPlayer has been muted by " + staffName + " for: This is a test mute (Duration: 1 hour)";
        Bukkit.broadcastMessage(message);
        
        event.reply("✅ Test mute message sent!").setEphemeral(true).queue();
    }
    
    private void handleTestUnbanCommand(SlashCommandInteractionEvent event, String staffName) {
        Punishment punishment = new Punishment("TestPlayer", staffName, "Test unban reason", "UNBAN", 0);
        sendPunishmentMessage(punishment);
        
        // Broadcast to Minecraft
        String message = "§a[TEST] TestPlayer has been unbanned by " + staffName + " for: Test unban reason";
        Bukkit.broadcastMessage(message);
        
        event.reply("✅ Test unban message sent!").setEphemeral(true).queue();
    }
    
    private void handleTestUnwarnCommand(SlashCommandInteractionEvent event, String staffName) {
        Punishment punishment = new Punishment("TestPlayer", staffName, "Test unwarn reason", "UNWARN", 0);
        sendPunishmentMessage(punishment);
        
        // Broadcast to Minecraft
        String message = "§a[TEST] TestPlayer has been unwarned by " + staffName + " for: Test unwarn reason";
        Bukkit.broadcastMessage(message);
        
        event.reply("✅ Test unwarn message sent!").setEphemeral(true).queue();
    }
    
    private void handleTestUnmuteCommand(SlashCommandInteractionEvent event, String staffName) {
        Punishment punishment = new Punishment("TestPlayer", staffName, "Test unmute reason", "UNMUTE", 0);
        sendPunishmentMessage(punishment);
        
        // Broadcast to Minecraft
        String message = "§a[TEST] TestPlayer has been unmuted by " + staffName + " for: Test unmute reason";
        Bukkit.broadcastMessage(message);
        
        event.reply("✅ Test unmute message sent!").setEphemeral(true).queue();
    }
    
    // Economy: /price command 
    
    private void handlePriceCommand(SlashCommandInteractionEvent event) {
        EconomyManager eco = plugin.getEconomyManager();
        if (eco == null || !eco.isEnabled()) {
            event.reply("❌ The economy system is disabled.").setEphemeral(true).queue();
            return;
        }
        
        String itemArg = event.getOption("item").getAsString().trim();
        Material material = EconomyManager.parseMaterial(itemArg);
        if (material == null) {
            event.reply("❌ Unknown item: `" + itemArg + "`").setEphemeral(true).queue();
            return;
        }
        
        // Defer because we may do HTTP call for chart
        event.deferReply().queue();
        
        // Run on async thread so chart URL generation doesn't block
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String itemName = SellCommand.formatMaterial(material);
                int decimals = eco.getDecimals();
                String unit = eco.getUnit();
                
                EconomyManager.PriceResult sellResult = eco.getPrice(material);
                EconomyManager.BuyPriceResult buyResult = eco.getBuyPrice(material);
                boolean canSell = sellResult.sellable;
                boolean canBuy = buyResult.buyable && eco.isBuyingEnabled();
                
                if (!canSell && !canBuy) {
                    event.getHook().editOriginal("❌ **" + itemName + "** is not sellable or buyable.").queue();
                    return;
                }
                
                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("💰 " + itemName + " - Price Info");
                embed.setColor(new Color(0x2ECC71));
                
                // Sell price field
                if (canSell) {
                    String sellStr = unit + sellResult.price.setScale(decimals, RoundingMode.HALF_UP).toPlainString();
                    String sellField = "**" + sellStr + "** each";
                    if (sellResult.categoryName != null) {
                        sellField += "\n*(Category: " + sellResult.categoryName + ")*";
                    }
                    embed.addField("📤 Sell Price", sellField, true);
                }
                
                // Buy price field
                if (canBuy) {
                    String buyStr = unit + buyResult.price.setScale(decimals, RoundingMode.HALF_UP).toPlainString();
                    String buyField = "**" + buyStr + "** each";
                    if (buyResult.categoryName != null) {
                        buyField += "\n*(Category: " + buyResult.categoryName + ")*";
                    }
                    embed.addField("📥 Buy Price", buyField, true);
                } else if (!eco.isBuyingEnabled()) {
                    embed.addField("📥 Buy Price", "*Buying disabled*", true);
                } else {
                    embed.addField("📥 Buy Price", "*Not available*", true);
                }
                
                // Circulation
                DynamicPricingEngine engine = eco.getDynamicPricing();
                if (engine != null) {
                    long circ = engine.getCirculation(material);
                    embed.addField("📊 Circulation", String.valueOf(circ) + " items", true);
                }
                
                // Dynamic pricing status
                if (engine != null && engine.isEnabled()) {
                    double changePercent = engine.getChangePercent(material, null);
                    String status = engine.getStatus(material, null);
                    String emoji = changePercent > 1.0 ? "🔴" : (changePercent < -1.0 ? "🟢" : "🟡");
                    String changeStr = String.format("%+.1f%%", changePercent);
                    embed.addField("📈 Market Status", emoji + " " + status + " (" + changeStr + ")", false);
                    
                    // Build QuickChart price history graph (use smoothed data for curvy lines)
                    java.util.List<DynamicPricingEngine.PriceSnapshot> history = engine.getSmoothedHistory(material);
                    if (history.isEmpty()) {
                        history = engine.getHistory(material);
                    }
                    if (!history.isEmpty()) {
                        String chartUrl = buildPriceChartUrl(history, itemName, unit, decimals, canSell ? sellResult : null);
                        if (chartUrl != null) {
                            embed.setImage(chartUrl);
                        }
                    }
                }
                
                embed.setFooter("Kelpylandia Economy");
                embed.setTimestamp(Instant.now());
                
                event.getHook().editOriginalEmbeds(embed.build()).queue();
            } catch (Exception e) {
                plugin.getLogger().warning("Error handling Discord /price command: " + e.getMessage());
                event.getHook().editOriginal("❌ An error occurred while fetching price info.").queue();
            }
        });
    }
    
    /**
     * Build a QuickChart URL for the price history graph.
     * Uses the QuickChart.io API to render a Chart.js line chart as a PNG.
     */
    private String buildPriceChartUrl(java.util.List<DynamicPricingEngine.PriceSnapshot> history,
                                       String itemName, String unit, int decimals,
                                       EconomyManager.PriceResult baseSellResult) {
        try {
            // Limit to last 30 data points for a clean graph
            int maxPoints = 30;
            int start = Math.max(0, history.size() - maxPoints);
            
            StringBuilder labels = new StringBuilder("[");
            StringBuilder data = new StringBuilder("[");
            
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM/dd HH:mm");
            
            for (int i = start; i < history.size(); i++) {
                DynamicPricingEngine.PriceSnapshot snap = history.get(i);
                if (i > start) {
                    labels.append(",");
                    data.append(",");
                }
                labels.append("\"").append(sdf.format(new java.util.Date(snap.timestamp))).append("\"");
                data.append(snap.price.setScale(decimals, RoundingMode.HALF_UP).toPlainString());
            }
            labels.append("]");
            data.append("]");
            
            // Build base price annotation line if we have a base sell price
            String annotationPlugin = "";
            if (baseSellResult != null) {
                String baseVal = baseSellResult.price.setScale(decimals, RoundingMode.HALF_UP).toPlainString();
                annotationPlugin = ",annotation:{annotations:{line1:{type:'line',yMin:" + baseVal 
                    + ",yMax:" + baseVal + ",borderColor:'rgba(255,99,132,0.6)',borderWidth:2,borderDash:[6,4],"
                    + "label:{display:true,content:'Base: " + unit + baseVal + "',position:'start',backgroundColor:'rgba(255,99,132,0.7)'}}}}";
            }
            
            String chartConfig = "{type:'line',data:{labels:" + labels + ",datasets:[{label:'Sell Price (" + unit + ")',"
                + "data:" + data + ",borderColor:'#2ecc71',backgroundColor:'rgba(46,204,113,0.1)',fill:true,"
                + "tension:0.4,pointRadius:2}]},options:{plugins:{title:{display:true,text:'" + itemName 
                + " Price History',color:'#fff',font:{size:16}},legend:{labels:{color:'#ccc'}}" + annotationPlugin 
                + "},scales:{x:{ticks:{color:'#aaa',maxTicksLimit:8},grid:{color:'rgba(255,255,255,0.1)'}},"
                + "y:{ticks:{color:'#aaa',callback:function(v){return '" + unit + "'+v}},grid:{color:'rgba(255,255,255,0.1)'}}},"
                + "layout:{padding:10}},plugins:['chartjs-plugin-annotation']}";
            
            String encoded = URLEncoder.encode(chartConfig, "UTF-8");
            String url = "https://quickchart.io/chart?c=" + encoded + "&w=600&h=300&bkg=%232f3136&f=png";
            
            // QuickChart URLs have a ~16k char limit; fall back if too long
            if (url.length() > 16000) {
                // Reduce points and retry
                return buildPriceChartUrlSimple(history, itemName, unit, decimals);
            }
            return url;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to build price chart URL: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Simplified chart with fewer data points as a fallback.
     */
    private String buildPriceChartUrlSimple(java.util.List<DynamicPricingEngine.PriceSnapshot> history,
                                             String itemName, String unit, int decimals) {
        try {
            int maxPoints = 10;
            int step = Math.max(1, history.size() / maxPoints);
            
            StringBuilder labels = new StringBuilder("[");
            StringBuilder data = new StringBuilder("[");
            
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM/dd");
            boolean first = true;
            
            for (int i = 0; i < history.size(); i += step) {
                DynamicPricingEngine.PriceSnapshot snap = history.get(i);
                if (!first) {
                    labels.append(",");
                    data.append(",");
                }
                first = false;
                labels.append("\"").append(sdf.format(new java.util.Date(snap.timestamp))).append("\"");
                data.append(snap.price.setScale(decimals, RoundingMode.HALF_UP).toPlainString());
            }
            labels.append("]");
            data.append("]");
            
            String chartConfig = "{type:'line',data:{labels:" + labels + ",datasets:[{label:'Price (" + unit + ")',"
                + "data:" + data + ",borderColor:'#2ecc71',fill:false,tension:0.4}]},"
                + "options:{plugins:{title:{display:true,text:'" + itemName + " Price History',color:'#fff'}},"
                + "scales:{x:{ticks:{color:'#aaa'}},y:{ticks:{color:'#aaa'}}}}}";
            
            String encoded = URLEncoder.encode(chartConfig, "UTF-8");
            return "https://quickchart.io/chart?c=" + encoded + "&w=600&h=300&bkg=%232f3136&f=png";
        } catch (Exception e) {
            return null;
        }
    }
    
    // Server lifecycle event methods
    public void sendServerStartEmbed() {
        if (!enabled || serverChannelId == null || serverChannelId.isEmpty()) return;
        
        try {
            TextChannel channel = jda.getTextChannelById(serverChannelId);
            if (channel != null) {
                EmbedBuilder embed = new EmbedBuilder();
                embed.setColor(getColorFromConfig("discord.embeds.server-start-color", Color.GREEN));
                
                String serverName = plugin.getConfig().getString("settings.server-name", "Minecraft Server");
                String title = plugin.getConfig().getString("discord.formats.server-start", "🟢 **{server_name}** server has started!")
                    .replace("{server_name}", serverName);
                String description = plugin.getConfig().getString("discord.formats.server-start-description", "The server is now online and ready for players!")
                    .replace("{server_name}", serverName);
                
                embed.setTitle(stripSectionCodes(title));
                embed.setDescription(stripSectionCodes(description));
                
                // Mention the server start role if configured
                String pingRoleId = plugin.getConfig().getString("discord.server-start-ping-role-id");
                String messageContent = "";
                if (pingRoleId != null && !pingRoleId.equals("your-server-start-role-id")) {
                    messageContent = "<@&" + pingRoleId + ">";
                }
                
                if (messageContent.isEmpty()) {
                    channel.sendMessageEmbeds(embed.build()).queue();
                } else {
                    channel.sendMessage(messageContent).setEmbeds(embed.build()).queue();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send server start embed to Discord: " + e.getMessage());
        }
    }
    
    public void sendServerStopEmbed() {
        if (!enabled || serverChannelId == null || serverChannelId.isEmpty()) return;
        
        try {
            TextChannel channel = jda.getTextChannelById(serverChannelId);
            if (channel != null) {
                EmbedBuilder embed = new EmbedBuilder();
                embed.setColor(getColorFromConfig("discord.embeds.server-stop-color", Color.RED));
                
                String serverName = plugin.getConfig().getString("settings.server-name", "Minecraft Server");
                String title = plugin.getConfig().getString("discord.formats.server-stop", "🔴 **{server_name}** server is stopping...")
                    .replace("{server_name}", serverName);
                String description = plugin.getConfig().getString("discord.formats.server-stop-description", "The server is shutting down. Thank you for playing!")
                    .replace("{server_name}", serverName);
                
                embed.setTitle(stripSectionCodes(title));
                embed.setDescription(stripSectionCodes(description));
                
                channel.sendMessageEmbeds(embed.build()).queue();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send server stop embed to Discord: " + e.getMessage());
        }
    }
    
    public void sendServerRestartEmbed() {
        if (!enabled || serverChannelId == null || serverChannelId.isEmpty()) return;
        
        try {
            TextChannel channel = jda.getTextChannelById(serverChannelId);
            if (channel != null) {
                EmbedBuilder embed = new EmbedBuilder();
                embed.setColor(getColorFromConfig("discord.embeds.server-restart-color", Color.ORANGE));
                
                String serverName = plugin.getConfig().getString("settings.server-name", "Minecraft Server");
                String title = plugin.getConfig().getString("discord.formats.server-restart", "🟠 **{server_name}** server is restarting...")
                    .replace("{server_name}", serverName);
                String description = plugin.getConfig().getString("discord.formats.server-restart-description", "The server is restarting. Please wait a moment to reconnect.")
                    .replace("{server_name}", serverName);
                
                embed.setTitle(stripSectionCodes(title));
                embed.setDescription(stripSectionCodes(description));
                
                channel.sendMessageEmbeds(embed.build()).queue();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send server restart embed to Discord: " + e.getMessage());
        }
    }
    
    // Helper method to check if player is AFK (implement based on your AFK system)
    private boolean isPlayerAFK(Player player) {
        // This should be implemented based on your AFK detection system
        // For now, return false. You can integrate with plugins like EssentialsX
        return false;
    }
    
    // Channel topic updater functionality
    private void startChannelTopicUpdater() {
        if (!plugin.getConfig().getBoolean("discord.channel-topics.enabled", false)) {
            return;
        }
        
        long updateIntervalSeconds = plugin.getConfig().getLong("discord.channel-topics.update-interval", 600); // Default 10 minutes
        
        // Schedule repeating task to update channel topics
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            updateChannelTopics();
        }, 20L * updateIntervalSeconds, 20L * updateIntervalSeconds);
        
        plugin.getLogger().info("Channel topic updater started with " + (updateIntervalSeconds/60) + " minute intervals");
    }
    
    private void updateChannelTopics() {
        if (!enabled || jda == null) return;
        
        try {
            // Update console channel topic if configured
            String consoleTopicFormat = plugin.getConfig().getString("discord.channel-topics.console-channel-topic");
            if (consoleTopicFormat != null && consoleChannelId != null) {
                updateChannelTopic(consoleChannelId, consoleTopicFormat);
            }
            
            // Update other configured channel topics
            if (plugin.getConfig().contains("discord.channel-topics.channels")) {
                var channelTopics = plugin.getConfig().getConfigurationSection("discord.channel-topics.channels");
                if (channelTopics != null) {
                    for (String channelId : channelTopics.getKeys(false)) {
                        String topicFormat = channelTopics.getString(channelId);
                        if (topicFormat != null) {
                            updateChannelTopic(channelId, topicFormat);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to update channel topics: " + e.getMessage());
        }
    }
    
    private void updateChannelTopic(String channelId, String topicFormat) {
        try {
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel == null) return;
            
            // Get server statistics
            int onlinePlayers = Bukkit.getOnlinePlayers().size();
            int maxPlayers = Bukkit.getMaxPlayers();
            
            // Get TPS (simplified - using a placeholder since getTPS() is not always available)
            String tps = "20.0"; // You can implement TPS monitoring using a separate plugin or Paper API
            
            // Get memory info
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory() / (1024 * 1024); // MB
            long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024); // MB
            
            // Format the topic
            String newTopic = topicFormat
                .replace("{online_players}", String.valueOf(onlinePlayers))
                .replace("{max_players}", String.valueOf(maxPlayers))
                .replace("{tps}", tps)
                .replace("{memory_used}", String.valueOf(usedMemory))
                .replace("{memory_max}", String.valueOf(maxMemory))
                .replace("{server_name}", plugin.getConfig().getString("settings.server-name", "Minecraft Server"));
            
            // Only update if the topic has changed
            if (!newTopic.equals(channel.getTopic())) {
                channel.getManager().setTopic(newTopic).queue(
                    success -> {}, // Success callback
                    error -> plugin.getLogger().warning("Failed to update channel topic: " + error.getMessage())
                );
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to update topic for channel " + channelId + ": " + e.getMessage());
        }
    }
    
    // Advancement announcement functionality
    public void sendAdvancementMessage(Player player, String advancementTitle, String advancementDescription) {
        if (!enabled || chatChannelId == null) return;
        
        if (!plugin.getConfig().getBoolean("discord.events.broadcast-advancements", true)) {
            return;
        }
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                TextChannel channel = jda.getTextChannelById(chatChannelId);
                if (channel != null) {
                    EmbedBuilder embed = new EmbedBuilder();
                    embed.setColor(getColorFromConfig("discord.embeds.advancement-color", Color.YELLOW));
                    
                    String title = plugin.getConfig().getString("discord.formats.advancement", "🏆 **{player}** has made the advancement **{advancement}**!")
                        .replace("{player}", player.getName())
                        .replace("{advancement}", advancementTitle);
                    
                    embed.setTitle(stripSectionCodes(title));
                    
                    if (advancementDescription != null && !advancementDescription.isEmpty()) {
                        embed.setDescription(stripSectionCodes(advancementDescription));
                    }
                    
                    // Add player avatar as thumbnail
                    String avatarUrl = plugin.getConfig().getString("discord.formats.avatar-url", "https://mc-heads.net/avatar/{uuid}/64")
                        .replace("{uuid}", player.getUniqueId().toString())
                        .replace("{player}", player.getName());
                    embed.setThumbnail(avatarUrl);
                    
                    embed.setTimestamp(Instant.now());

                    channel.sendMessageEmbeds(embed.build()).queue(
                        success -> plugin.getLogger().info("[DISCORD DEBUG] Advancement message sent successfully!"),
                        error -> plugin.getLogger().severe("[DISCORD DEBUG] Failed to send advancement message: " + error.getMessage())
                    );
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send advancement message to Discord: " + e.getMessage());
            }
        });
    }
    
    // Console message sending for server monitoring
    public void sendConsoleMessage(String message, String level) {
        if (!enabled || consoleChannelId == null) return;
        if (!plugin.getConfig().getBoolean("discord.events.console-logging", true)) return;

        // Filter blocked levels
        List<String> blockedLevels = plugin.getConfig().getStringList("discord.console-logging.blocked-levels");
        if (blockedLevels.contains(level.toUpperCase())) return;

        // Format the line - no blocking I/O here.  The logging thread must never block.
        String timestamp = DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalTime.now());
        String normalizedLevel = level.toUpperCase();
        if (normalizedLevel.equals("SEVERE")) normalizedLevel = "ERROR";
        if (normalizedLevel.equals("WARNING")) normalizedLevel = "WARN";

        String cleanMsg = stripSectionCodes(message);
        if (cleanMsg.length() > 400) cleanMsg = cleanMsg.substring(0, 397) + "...";
        String newLine = "[" + timestamp + " " + normalizedLevel + "]: " + cleanMsg;

        // Feed the per-command capture buffer immediately (before the async flush)
        // so command output is never lost even at high log rates.
        if (cmdCaptureActive) {
            cmdCaptureLines.add(newLine);
        }

        // Enqueue for the background flush thread - offer() never blocks the caller.
        pendingConsoleLines.offer(newLine);
    }
}
