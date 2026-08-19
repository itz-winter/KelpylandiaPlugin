package com.kelpwing.kelpylandiaplugin.listeners;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.integrations.DiscordIntegration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.command.ConsoleCommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class ConsoleListener extends Handler implements Listener {

    private final KelpylandiaPlugin plugin;

    /**
     * Records that arrived before DiscordIntegration was ready.
     * Flushed and cleared the first time Discord becomes available.
     */
    private final List<LogRecord> pendingRecords = new ArrayList<>();
    private boolean discordReady = false;

    /** log4j2 appender that captures NMS/vanilla output (say, [Not Secure], etc.) */
    private Log4jAppender log4jAppender;

    public ConsoleListener(KelpylandiaPlugin plugin) {
        this.plugin = plugin;

        // Attach to the root JUL logger so plugin messages are captured.
        Logger.getLogger("").addHandler(this);

        // Also attach a log4j2 appender to capture vanilla/NMS server output
        // (e.g. [Not Secure] [Server] messages from the `say` command)
        // that only goes through log4j2 and never reaches the JUL root logger.
        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration config = ctx.getConfiguration();
            log4jAppender = new Log4jAppender();
            log4jAppender.start();
            config.getRootLogger().addAppender(log4jAppender, null, null);
            ctx.updateLoggers();
        } catch (Exception e) {
            plugin.getLogger().warning("[ConsoleListener] Could not attach log4j2 appender: " + e.getMessage());
        }
    }

    // log4j2 appender 

    private class Log4jAppender extends AbstractAppender {
        Log4jAppender() {
            super("KelpylandiaDiscordAppender", null,
                    PatternLayout.createDefaultLayout(), true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            if (event == null) return;
            String message = event.getMessage().getFormattedMessage();
            if (message == null) return;

            String loggerName = event.getLoggerName();
            if (loggerName == null) loggerName = "";

            // Only relay events from loggers that do NOT already go through our JUL handler.
            //
            // In Spigot/Paper, the JUL LogManager is bridged to log4j2, so JUL messages show up
            // in log4j2 too.  Bukkit plugin JUL loggers use simple names without dots (e.g.
            // "LuckPerms", "EssentialsX") - our JUL Handler already captures those, so skip them
            // here to avoid double-posting.
            //
            // Loggers with dotted names (e.g. "me.lucko.luckperms.*", "net.minecraft.*") come
            // from SLF4J→log4j2 or NMS and are NOT captured by the JUL handler - relay those.
            //
            // Explicitly excluded (handled by JUL or feedback-loop risk):
            //  ""               - root logger (JUL bridge)
            //  no dots in name  - Bukkit plugin JUL logger (simple plugin name)
            //  com.kelpwing.*   - our own plugin (prevents Discord feedback loops)
            //  org.bukkit.*     - Bukkit internals (JUL)
            //  java.* / sun.* / javax.* / jdk.*  - JDK internals
            boolean isJulHandled = loggerName.isEmpty()
                    || !loggerName.contains(".")          // simple name = Bukkit JUL plugin logger
                    || loggerName.startsWith("com.kelpwing")
                    || loggerName.startsWith("org.bukkit")
                    || loggerName.startsWith("java.")
                    || loggerName.startsWith("sun.")
                    || loggerName.startsWith("javax.")
                    || loggerName.startsWith("jdk.");
            if (isJulHandled) return;

            String level = event.getLevel().name();
            relayLog4j(message, level);
        }
    }

    private void relayLog4j(String message, String level) {
        synchronized (pendingRecords) {
            DiscordIntegration discord = plugin.getDiscordIntegration();
            if (discord == null || !discord.isEnabled()) return;
        }
        DiscordIntegration discord = plugin.getDiscordIntegration();
        if (discord != null && discord.isEnabled() && shouldLogMessage(message)) {
            discord.sendConsoleMessage(message, level);
        }
    }

    // JUL handler 

    @Override
    public void publish(LogRecord record) {
        if (record == null || record.getMessage() == null) return;

        synchronized (pendingRecords) {
            if (!discordReady) {
                // Discord not up yet - check if it is now
                DiscordIntegration discord = plugin.getDiscordIntegration();
                if (discord == null || !discord.isEnabled()) {
                    // Still not ready; buffer this record (cap at 500 to avoid unbounded growth)
                    if (pendingRecords.size() < 500) {
                        pendingRecords.add(record);
                    }
                    return;
                }

                // Discord just became ready - flush buffered records first
                discordReady = true;
                for (LogRecord pending : pendingRecords) {
                    relay(discord, pending.getMessage(), pending.getLevel().getName());
                }
                pendingRecords.clear();
            }
        }

        DiscordIntegration discord = plugin.getDiscordIntegration();
        if (discord != null && discord.isEnabled()) {
            relay(discord, record.getMessage(), record.getLevel().getName());
        }
    }

    private void relay(DiscordIntegration discord, String message, String level) {
        if (shouldLogMessage(message)) {
            discord.sendConsoleMessage(message, level);
        }
    }

    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        // Only relay commands run by the actual console, not command blocks or other non-human senders.
        if (!(event.getSender() instanceof ConsoleCommandSender)) return;
        DiscordIntegration discord = plugin.getDiscordIntegration();
        if (discord != null && discord.isEnabled()) {
            discord.sendConsoleMessage("Executed command: /" + event.getCommand(), "INFO");
        }
    }

    private boolean shouldLogMessage(String message) {
        // Don't log our own Discord-relay messages to prevent feedback loops
        if (message.contains("[Discord]") || message.contains("Discord Integration")
                || message.contains("Failed to send console message")
                || message.contains("Failed to edit console message")) {
            return false;
        }

        // Filter out common high-frequency spam
        String[] spamKeywords = {
            "Can't keep up!", "Is the server overloaded?", "KeepAlive", "ping"
        };
        for (String keyword : spamKeywords) {
            if (message.contains(keyword)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void flush() {}

    @Override
    public void close() throws SecurityException {
        Logger.getLogger("").removeHandler(this);
        // Detach log4j2 appender on shutdown
        try {
            if (log4jAppender != null) {
                LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
                Configuration config = ctx.getConfiguration();
                config.getRootLogger().removeAppender("KelpylandiaDiscordAppender");
                log4jAppender.stop();
                ctx.updateLoggers();
            }
        } catch (Exception ignored) {}
    }
}
