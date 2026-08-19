package com.kelpwing.kelpylandiaplugin.listeners;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import com.kelpwing.kelpylandiaplugin.utils.UpdateChecker;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Sends a clickable update notification to players with
 * kelpylandia.update.notify permission on join, if an update is available.
 */
public class UpdateNotifyListener implements Listener {

    private static final String MODRINTH_PAGE = "https://modrinth.com/plugin/kelpyylandia-plugin";
    private static final String PREFIX =
            ChatColor.GRAY + "[" + ChatColor.AQUA + "Update" + ChatColor.GRAY + "] " + ChatColor.RESET;

    private final KelpylandiaPlugin plugin;

    public UpdateNotifyListener(KelpylandiaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!player.hasPermission("qol.update.notify")) return;

        UpdateChecker checker = plugin.getUpdateChecker();
        if (checker == null) return;

        if (checker.hasChecked()) {
            // Check already completed - notify immediately if an update is available
            if (checker.isUpdateAvailable()) {
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> sendNotification(player, checker), 20L);
            }
        } else {
            // Check hasn't finished yet - run it now and notify in the callback
            checker.checkAsync(() -> {
                if (player.isOnline() && checker.isUpdateAvailable()) {
                    sendNotification(player, checker);
                }
            });
        }
    }

    private void sendNotification(Player player, UpdateChecker checker) {
        if (!player.isOnline()) return;

        String current = checker.getCurrentVersion();
        String latest  = checker.getLatestVersion();

        // Line 1: plain update notice
        player.sendMessage(PREFIX
                + ChatColor.YELLOW + "QoLPlugin update available: "
                + ChatColor.RED + "v" + current
                + ChatColor.GRAY + " → "
                + ChatColor.GREEN + "v" + latest);

        // Line 2: clickable links
        TextComponent checkBtn = new TextComponent(
                ChatColor.GRAY + "[" + ChatColor.WHITE + "/kpupdate check" + ChatColor.GRAY + "]");
        checkBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/kpupdate check"));
        checkBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ChatColor.GRAY + "Run /kpupdate check").create()));

        TextComponent space1 = new TextComponent("  ");

        TextComponent dlBtn = new TextComponent(
                ChatColor.GRAY + "[" + ChatColor.AQUA + "Download" + ChatColor.GRAY + "]");
        dlBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/kpupdate download"));
        dlBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ChatColor.GRAY + "Download the update to the plugins folder").create()));

        TextComponent space2 = new TextComponent("  ");

        TextComponent modrinthBtn = new TextComponent(
                ChatColor.GRAY + "[" + ChatColor.GREEN + "Modrinth" + ChatColor.GRAY + "]");
        modrinthBtn.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, MODRINTH_PAGE));
        modrinthBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ChatColor.GRAY + "Open Modrinth page").create()));

        TextComponent root = new TextComponent(PREFIX);
        root.addExtra(checkBtn);
        root.addExtra(space1);
        root.addExtra(dlBtn);
        root.addExtra(space2);
        root.addExtra(modrinthBtn);

        player.spigot().sendMessage(root);
    }
}
