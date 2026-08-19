package com.kelpwing.kelpylandiaplugin.commands;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.utils.LevelManager;
import com.kelpwing.kelpylandiaplugin.utils.SpyManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /w <player> <message>  - Send a private message.
 * /w <message>           - Send to whisper target (set via /wt).
 * Also handles /msg, /tell, /whisper aliases.
 *
 * Console can send messages as "Console" and players can message Console by name.
 *
 * Message formats are read from config:
 *   messaging.format-sender:   "&c[Me -> {receiver}]:&7 {message}"
 *   messaging.format-receiver: "&c[{sender} -> Me]:&7 {message}"
 */
public class MsgCommand implements CommandExecutor, TabCompleter {

    private final KelpylandiaPlugin plugin;

    /** A fixed UUID used to represent the console in conversation tracking. */
    public static final UUID CONSOLE_UUID = new UUID(0L, 0L);

    /** Tracks the last player each player messaged/received a message from, for /r. */
    private final Map<UUID, UUID> lastConversation = new ConcurrentHashMap<>();

    /** Whisper target lock: UUID -> target UUID. When set, /w <msg> goes to this player. */
    private final Map<UUID, UUID> whisperTarget = new ConcurrentHashMap<>();

    public MsgCommand(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    // /w <player> <message>  OR  /w <message> (when target locked) 
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Console: /w <player> <message>
        if (sender instanceof ConsoleCommandSender) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /w <player> <message>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
                return true;
            }
            String message = joinArgs(args, 1);
            sendPrivateMessage(sender, target, message);
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /w <player> <message>  or  /w <message> (when whisper target set)");
            return true;
        }

        // Check if sender has a whisper target locked
        UUID lockedTarget = whisperTarget.get(player.getUniqueId());

        // Check if targeting "Console"
        if (args[0].equalsIgnoreCase("console")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /w Console <message>");
                return true;
            }
            String message = joinArgs(args, 1);
            sendPrivateMessage(player, Bukkit.getConsoleSender(), message);
            return true;
        }

        // Try to resolve first arg as a player name
        Player target = Bukkit.getPlayerExact(args[0]);

        if (target != null && !target.equals(player)) {
            // First arg is a valid online player - treat as /w <player> <message>
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /w <player> <message>");
                return true;
            }
            String message = joinArgs(args, 1);
            sendPrivateMessage(player, target, message);
            return true;
        }

        // First arg is NOT a player - check for a locked whisper target
        if (lockedTarget != null) {
            // Locked to console?
            if (lockedTarget.equals(CONSOLE_UUID)) {
                String message = joinArgs(args, 0);
                sendPrivateMessage(player, Bukkit.getConsoleSender(), message);
                return true;
            }
            Player lockedPlayer = Bukkit.getPlayer(lockedTarget);
            if (lockedPlayer == null || !lockedPlayer.isOnline()) {
                player.sendMessage(ChatColor.RED + "Your whisper target is no longer online. Use /wt to clear or set a new target.");
                return true;
            }
            String message = joinArgs(args, 0);
            sendPrivateMessage(player, lockedPlayer, message);
            return true;
        }

        // No locked target and first arg isn't a player
        if (target != null && target.equals(player)) {
            player.sendMessage(ChatColor.RED + "You can't message yourself.");
        } else {
            player.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
        }
        return true;
    }

    /**
     * Core messaging logic - accepts any CommandSender (Player or Console).
     * Also used by ReplyCommand and ChatListener.
     */
    public void sendPrivateMessage(CommandSender sender, CommandSender receiver, String message) {
        String senderFormat = plugin.getConfig().getString("messaging.format-sender", "&c[Me -> {receiver}]:&7 {message}");
        String receiverFormat = plugin.getConfig().getString("messaging.format-receiver", "&c[{sender} -> Me]:&7 {message}");

        String senderDisplay = (sender instanceof Player p) ? p.getDisplayName() : "Console";
        String receiverDisplay = (receiver instanceof Player p) ? p.getDisplayName() : "Console";

        String toSender = senderFormat
                .replace("{sender}", senderDisplay)
                .replace("{receiver}", receiverDisplay)
                .replace("{message}", message);

        String toReceiver = receiverFormat
                .replace("{sender}", senderDisplay)
                .replace("{receiver}", receiverDisplay)
                .replace("{message}", message);

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', toSender));
        receiver.sendMessage(ChatColor.translateAlternateColorCodes('&', toReceiver));

        // Track conversation for /r
        UUID senderUUID = (sender instanceof Player p) ? p.getUniqueId() : CONSOLE_UUID;
        UUID receiverUUID = (receiver instanceof Player p) ? p.getUniqueId() : CONSOLE_UUID;
        lastConversation.put(senderUUID, receiverUUID);
        lastConversation.put(receiverUUID, senderUUID);

        // SocialSpy: notify staff who have socialspy enabled
        SpyManager spyManager = plugin.getSpyManager();
        if (spyManager != null) {
            String senderName = (sender instanceof Player p) ? p.getName() : "Console";
            String receiverName = (receiver instanceof Player p) ? p.getName() : "Console";
            String spyMsg = ChatColor.DARK_GRAY + "[SS] " + ChatColor.GRAY
                    + senderName + " -> " + receiverName + ": "
                    + ChatColor.WHITE + message;
            for (UUID spyUUID : spyManager.getSocialSpies()) {
                if (spyUUID.equals(senderUUID) || spyUUID.equals(receiverUUID)) continue;
                Player spy = Bukkit.getPlayer(spyUUID);
                if (spy != null && spy.isOnline()) {
                    // Level check: spy must have >= level than both sender and receiver (if they are players)
                    if (sender instanceof Player sp && !LevelManager.canObserve(spy, sp, "socialspy")) continue;
                    if (receiver instanceof Player rp && !LevelManager.canObserve(spy, rp, "socialspy")) continue;
                    spy.sendMessage(spyMsg);
                }
            }
        }
    }

    // Whisper target 

    /** Set a whisper target for a player. Returns the target. */
    public void setWhisperTarget(UUID player, UUID target) {
        whisperTarget.put(player, target);
    }

    /** Clear the whisper target for a player. */
    public void clearWhisperTarget(UUID player) {
        whisperTarget.remove(player);
    }

    /** Get the current whisper target, or null. */
    public UUID getWhisperTarget(UUID player) {
        return whisperTarget.get(player);
    }

    // Reply helper 
    public UUID getLastConversation(UUID uuid) {
        return lastConversation.get(uuid);
    }

    // Cleanup on quit 
    public void removePlayer(UUID uuid) {
        lastConversation.remove(uuid);
        whisperTarget.remove(uuid);
    }

    // Tab complete 
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> names = new ArrayList<>();
            if ("console".startsWith(prefix)) {
                names.add("Console");
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.equals(sender)) continue;
                if (p.getName().toLowerCase().startsWith(prefix)) {
                    names.add(p.getName());
                }
            }
            Collections.sort(names);
            return names;
        }
        return Collections.emptyList();
    }

    private String joinArgs(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }
}
